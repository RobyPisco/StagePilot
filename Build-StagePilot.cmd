@echo off
setlocal
cd /d "%~dp0"
echo StagePilot - compilazione web e Android
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not defined ANDROID_HOME set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Serve un JDK 21. Imposta JAVA_HOME e riprova.
  pause
  exit /b 1
)
if not exist ".tmp" mkdir ".tmp"
set "JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=%CD%\.tmp"
pushd web
if not exist node_modules call npm ci
if errorlevel 1 exit /b 1
call npm test
if errorlevel 1 exit /b 1
call npm run build
if errorlevel 1 exit /b 1
popd
call gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
if errorlevel 1 exit /b 1
echo.
echo Pronto. Avvia StagePilot.cmd per lo studio web.
echo APK: app\build\outputs\apk\debug\app-debug.apk
pause
