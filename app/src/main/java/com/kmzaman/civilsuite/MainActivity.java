package com.kmzaman.civilsuite;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int STORAGE_PERMISSION_CODE = 101;

    // Declared BEFORE the launchers to avoid illegal forward reference
    private String pendingFileContent = null;

    // ── Activity Result Launchers ────────────────────────
    private final ActivityResultLauncher<Intent> filePickerLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    readFileAndPassToJS(uri);
                }
            }
            // Also handle WebChromeClient file chooser (for <input type="file">)
            if (filePathCallback != null) {
                Uri[] uris = null;
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    uris = new Uri[]{result.getData().getData()};
                }
                filePathCallback.onReceiveValue(uris);
                filePathCallback = null;
            }
        });

    private final ActivityResultLauncher<Intent> fileSaveLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null && pendingFileContent != null) {
                    writeFileToUri(uri, pendingFileContent);
                    pendingFileContent = null;
                }
            }
        });

    // ═══════════════════════════════════════════════════
    // onCreate — entry point
    // ═══════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── STEP 1: Enable edge-to-edge BEFORE setContentView ──
        enableEdgeToEdge();

        setContentView(R.layout.activity_main);

        // ── STEP 2: Set true immersive full-screen ──
        setImmersiveMode();

        // ── STEP 3: Configure WebView ──
        webView = findViewById(R.id.webView);
        setupWebView();

        // ── STEP 4: Load the app ──
        webView.loadUrl("file:///android_asset/index.html");
    }

    // ═══════════════════════════════════════════════════
    // EDGE-TO-EDGE: Draw behind status & nav bars
    // ═══════════════════════════════════════════════════
    private void enableEdgeToEdge() {
        // Tell Android our window will handle its own insets
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        Window window = getWindow();
        // Transparent system bars — our HTML draws behind them
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        // Handle display cutouts (notch / punch-hole)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    // ═══════════════════════════════════════════════════
    // IMMERSIVE MODE: Hide status bar & navigation bar
    // ═══════════════════════════════════════════════════
    private void setImmersiveMode() {
        WindowInsetsControllerCompat controller =
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        // Hide both the status bar and the navigation bar
        controller.hide(WindowInsetsCompat.Type.systemBars());

        // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE:
        // User can swipe in from edge to temporarily reveal bars,
        // then they auto-hide again — true immersive feel
        controller.setSystemBarsBehavior(
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );

        // Light status bar icons = false → white icons on dark background
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    // ═══════════════════════════════════════════════════
    // RE-APPLY on window focus (bars may reappear after dialogs/toasts)
    // ═══════════════════════════════════════════════════
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersiveMode();
        }
    }

    // ═══════════════════════════════════════════════════
    // WEBVIEW SETUP
    // ═══════════════════════════════════════════════════
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // Required — enables the JS engine
        settings.setJavaScriptEnabled(true);

        // Allow file:// to load other file:// resources (our local HTML)
        settings.setAllowFileAccess(true);

        // DOM Storage = localStorage support
        settings.setDomStorageEnabled(true);

        // Smooth scrolling
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);

        // Viewport meta tag support
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // No zoom controls
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Allow mixed content (https page loading http resources — for Google Fonts CDN)
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Cache — use cache when available for faster loads
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Dark mode: force dark if supported and system is in dark mode
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false);
        }

        // Inject our NativeBridge Java object as window.NativeBridge in JS
        webView.addJavascriptInterface(new NativeBridge(this, webView), "NativeBridge");

        // WebViewClient — handle navigation & errors
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Open external links in browser, not in our WebView
                if (!url.startsWith("file://")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    try { startActivity(intent); } catch (ActivityNotFoundException ignored) {}
                    return true;
                }
                return false;
            }
        });

        // WebChromeClient — handles <input type="file"> picker for restore
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("application/json");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    filePickerLauncher.launch(intent);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        // Scroll the WebView itself, not just its content
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        // Remove default WebView background (prevents white flash on load)
        webView.setBackgroundColor(0xFF0A0A14);
    }

    // ═══════════════════════════════════════════════════
    // BACK BUTTON — go back in WebView history
    // ═══════════════════════════════════════════════════
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // ═══════════════════════════════════════════════════
    // FILE HELPERS
    // ═══════════════════════════════════════════════════
    private void readFileAndPassToJS(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            reader.close();
            String content = sb.toString()
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
            // Call the one-time callback registered by JS openFilePicker()
            webView.post(() ->
                webView.evaluateJavascript(
                    "if(typeof window._nativeBridgeFileCb==='function')" +
                    "{window._nativeBridgeFileCb('" + content + "');}",
                    null
                )
            );
        } catch (Exception e) {
            showToast("Could not read file");
        }
    }

    private void writeFileToUri(Uri uri, String content) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) return;
            os.write(content.getBytes("UTF-8"));
            os.flush();
            os.close();
            showToast("Backup saved ✓");
        } catch (Exception e) {
            showToast("Could not save file");
        }
    }

    private void showToast(String msg) {
        runOnUiThread(() ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        );
    }

    // ═══════════════════════════════════════════════════
    // NATIVE BRIDGE — @JavascriptInterface methods
    // Called by JavaScript as window.NativeBridge.methodName(...)
    // ═══════════════════════════════════════════════════
    public class NativeBridge {

        private final Activity activity;
        private final WebView wv;
        private static final String PREFS_KEY = "csu3_records";
        private static final String PREFS_NAME = "CivilSuitePrefs";

        NativeBridge(Activity activity, WebView wv) {
            this.activity = activity;
            this.wv = wv;
        }

        // ── DATA STORAGE ──────────────────────────────────
        @JavascriptInterface
        public void saveData(String jsonString) {
            SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(PREFS_KEY, jsonString).apply();
        }

        @JavascriptInterface
        public String loadData() {
            SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getString(PREFS_KEY, "[]");
        }

        // ── PDF PRINT ─────────────────────────────────────
        // Called by JS as: NativeBridge.printHTML(htmlString, filename)
        // Opens Android Print Manager → user can Save as PDF or print
        @JavascriptInterface
        public void printHTML(String htmlContent, String filename) {
            activity.runOnUiThread(() -> {
                WebView printWebView = new WebView(activity);
                printWebView.getSettings().setJavaScriptEnabled(true);
                printWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        PrintManager printManager =
                            (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
                        PrintDocumentAdapter adapter =
                            printWebView.createPrintDocumentAdapter(
                                filename != null ? filename : "CivilSuite_Report"
                            );
                        PrintAttributes attrs = new PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .build();
                        if (printManager != null) {
                            printManager.print(
                                filename != null ? filename : "CivilSuite_Report",
                                adapter,
                                attrs
                            );
                        }
                    }
                });
                printWebView.loadDataWithBaseURL(
                    null, htmlContent, "text/html", "UTF-8", null
                );
            });
        }

        // ── FILE SAVE (Backup export) ─────────────────────
        // Called by JS as: NativeBridge.saveFile(filename, mimeType, content)
        // Opens Android Storage Access Framework so user picks save location
        @JavascriptInterface
        public void saveFile(String filename, String mimeType, String content) {
            pendingFileContent = content;
            activity.runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(mimeType != null ? mimeType : "application/json");
                intent.putExtra(Intent.EXTRA_TITLE, filename != null ? filename : "backup.json");
                try {
                    fileSaveLauncher.launch(intent);
                } catch (ActivityNotFoundException e) {
                    showToast("File save not supported on this device");
                    pendingFileContent = null;
                }
            });
        }

        // ── FILE PICKER (Backup import) ───────────────────
        // Called by JS as: NativeBridge.pickFile()
        // After user picks a file, Java calls window._nativeBridgeFileCb(content)
        @JavascriptInterface
        public void pickFile() {
            activity.runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("application/json");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    filePickerLauncher.launch(intent);
                } catch (ActivityNotFoundException e) {
                    showToast("File picker not available");
                }
            });
        }

        // ── HAPTIC FEEDBACK ───────────────────────────────
        @JavascriptInterface
        public void vibrate(int ms) {
            android.os.Vibrator vibrator =
                (android.os.Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        android.os.VibrationEffect.createOneShot(ms,
                            android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                    );
                } else {
                    vibrator.vibrate(ms);
                }
            }
        }

        // ── THEME COLOR (update status bar tint from JS) ──
        @JavascriptInterface
        public void setThemeColor(String hexColor) {
            try {
                int color = android.graphics.Color.parseColor(hexColor);
                activity.runOnUiThread(() ->
                    activity.getWindow().setStatusBarColor(color)
                );
            } catch (Exception ignored) {}
        }
    }
}
