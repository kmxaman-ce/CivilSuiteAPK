package com.kmzaman.civilsuite;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    // MUST be declared BEFORE the launchers — Java forward reference rule
    private String pendingFileContent = null;
    private ValueCallback<Uri[]> filePathCallback = null;

    // ── FIX #2: File Save Launcher (ACTION_CREATE_DOCUMENT) ──────────────
    private final ActivityResultLauncher<Intent> fileSaveLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK
                    && result.getData() != null
                    && result.getData().getData() != null
                    && pendingFileContent != null) {
                writeFileToUri(result.getData().getData(), pendingFileContent);
            } else {
                if (result.getResultCode() != Activity.RESULT_OK) {
                    showToast("Save cancelled");
                }
            }
            pendingFileContent = null;
        });

    // ── File Picker Launcher (ACTION_GET_CONTENT) ─────────────────────────
    private final ActivityResultLauncher<Intent> filePickerLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            // Handle WebChromeClient <input type="file">
            if (filePathCallback != null) {
                Uri[] uris = null;
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    uris = new Uri[]{ result.getData().getData() };
                }
                filePathCallback.onReceiveValue(uris);
                filePathCallback = null;
                return;
            }
            // Handle NativeBridge.pickFile()
            if (result.getResultCode() == Activity.RESULT_OK
                    && result.getData() != null
                    && result.getData().getData() != null) {
                readFileAndPassToJS(result.getData().getData());
            }
        });

    // ═══════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableEdgeToEdge();
        setContentView(R.layout.activity_main);
        setImmersiveMode();
        webView = findViewById(R.id.webView);
        setupWebView();
        webView.loadUrl("file:///android_asset/index.html");
    }

    // ── EDGE-TO-EDGE ──────────────────────────────────────────────────────
    private void enableEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        Window window = getWindow();
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    // ── IMMERSIVE MODE ────────────────────────────────────────────────────
    private void setImmersiveMode() {
        WindowInsetsControllerCompat ctrl =
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
        ctrl.setSystemBarsBehavior(
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        ctrl.setAppearanceLightStatusBars(false);
        ctrl.setAppearanceLightNavigationBars(false);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setImmersiveMode();
    }

    // ── WEBVIEW SETUP ─────────────────────────────────────────────────────
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, false);
        }

        webView.addJavascriptInterface(new NativeBridgeImpl(this, webView), "NativeBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (!url.startsWith("file://")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (ActivityNotFoundException ignored) {}
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView wv,
                                             ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                filePathCallback = cb;
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

        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setBackgroundColor(0xFF0A0A14);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    // ── FILE I/O HELPERS ──────────────────────────────────────────────────
    private void readFileAndPassToJS(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            reader.close();
            // Escape for JS string literal
            String content = sb.toString()
                .replace("\\", "\\\\")
                .replace("'",  "\\'")
                .replace("\r", "")
                .replace("\n", "\\n");
            webView.post(() ->
                webView.evaluateJavascript(
                    "if(typeof window._nativeBridgeFileCb==='function')" +
                    "{window._nativeBridgeFileCb('" + content + "');}",
                    null));
        } catch (Exception e) {
            showToast("Could not read file");
        }
    }

    private void writeFileToUri(Uri uri, String content) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) { showToast("Could not open file"); return; }
            os.write(content.getBytes("UTF-8"));
            os.flush();
            os.close();
            showToast("Backup saved ✓");
        } catch (Exception e) {
            showToast("Could not save file: " + e.getMessage());
        }
    }

    void showToast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    // ═══════════════════════════════════════════════════
    // NATIVE BRIDGE  — @JavascriptInterface
    // window.NativeBridge.xxx() calls from JavaScript
    // ═══════════════════════════════════════════════════
    public class NativeBridgeImpl {

        private final Activity activity;
        private final WebView wv;
        private static final String PREFS_KEY  = "csu3_records";
        private static final String PREFS_NAME = "CivilSuitePrefs";

        NativeBridgeImpl(Activity a, WebView w) { activity = a; wv = w; }

        // ── STORAGE ───────────────────────────────────
        @JavascriptInterface
        public void saveData(String json) {
            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREFS_KEY, json).apply();
        }

        @JavascriptInterface
        public String loadData() {
            return activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREFS_KEY, "[]");
        }

        // ── FIX #3: PDF PRINT ─────────────────────────
        // Loads HTML into an off-screen WebView then calls Android PrintManager.
        // printWebView MUST be kept alive until onPageFinished fires — we keep
        // a reference on the outer class to prevent GC.
        @JavascriptInterface
        public void printHTML(final String html, final String jobName) {
            activity.runOnUiThread(() -> {
                // Use a fresh WebView dedicated to printing
                final WebView printWV = new WebView(activity);
                printWV.getSettings().setJavaScriptEnabled(true);

                printWV.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        PrintManager pm = (PrintManager)
                            activity.getSystemService(Context.PRINT_SERVICE);
                        if (pm == null) {
                            showToast("Print not available on this device");
                            return;
                        }
                        String name = (jobName != null && !jobName.isEmpty())
                            ? jobName : "CivilSuite_Report";
                        PrintDocumentAdapter adapter =
                            printWV.createPrintDocumentAdapter(name);
                        PrintAttributes attrs = new PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setResolution(new PrintAttributes.Resolution(
                                "pdf", "PDF", 300, 300))
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build();
                        pm.print(name, adapter, attrs);
                    }
                });

                // loadDataWithBaseURL with https base allows the print WebView
                // to load Google Fonts (CDN resources) correctly
                printWV.loadDataWithBaseURL(
                    "https://civil-suite.local/",
                    html,
                    "text/html",
                    "UTF-8",
                    null);
            });
        }

        // ── FIX #2: FILE SAVE (Backup export) ─────────
        // ACTION_CREATE_DOCUMENT opens the Android file picker so the user
        // can choose where to save the backup JSON file.
        @JavascriptInterface
        public void saveFile(final String filename,
                             final String mimeType,
                             final String content) {
            activity.runOnUiThread(() -> {
                pendingFileContent = content;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(mimeType != null && !mimeType.isEmpty()
                    ? mimeType : "application/json");
                intent.putExtra(Intent.EXTRA_TITLE,
                    filename != null && !filename.isEmpty()
                        ? filename : "CivilSuite_Backup.json");
                try {
                    fileSaveLauncher.launch(intent);
                } catch (ActivityNotFoundException e) {
                    pendingFileContent = null;
                    showToast("File manager not found on this device");
                }
            });
        }

        // ── FILE PICKER (Backup import) ────────────────
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

        // ── HAPTIC FEEDBACK ───────────────────────────
        @SuppressWarnings("deprecation")
        @JavascriptInterface
        public void vibrate(int ms) {
            android.os.Vibrator v =
                (android.os.Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(android.os.VibrationEffect.createOneShot(
                    ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        }

        // ── THEME COLOR ───────────────────────────────
        @JavascriptInterface
        public void setThemeColor(String hex) {
            try {
                int color = android.graphics.Color.parseColor(hex);
                activity.runOnUiThread(() ->
                    activity.getWindow().setStatusBarColor(color));
            } catch (Exception ignored) {}
        }
    }
}
