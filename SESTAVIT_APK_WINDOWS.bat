@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo === HQS Wi-Fi Navi - sestaveni Android APK ===
echo Nutne: Android Studio, Android SDK 34, JDK 17+ a pripojeni k internetu pri prvnim sestaveni.
echo.
call gradlew.bat --no-daemon assembleDebug
if errorlevel 1 (
  echo.
  echo SESTAVENI NEUSPESNE. Otevrete projekt v Android Studiu a zkontrolujte chybu v logu.
  pause
  exit /b 1
)
echo.
echo HOTOVO: app\build\outputs\apk\debug\app-debug.apk
if exist "app\build\outputs\apk\debug\app-debug.apk" (
  explorer /select,"%cd%\app\build\outputs\apk\debug\app-debug.apk"
)
pause
