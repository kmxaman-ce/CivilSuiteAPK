@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
setlocal
set DIRNAME=%~dp0
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1 || echo ERROR: JAVA_HOME is not set && exit /B 1
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
%JAVA_EXE% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
