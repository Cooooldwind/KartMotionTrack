@echo off
echo ================================================
echo   KartMotionTrack - Generate Debug Keystore
echo ================================================
echo.

REM Find Java keytool
set KEYTOOL=
for /f "tokens=*" %%i in ('where java') do (
    set JAVA_PATH=%%i
    set KEYTOOL=%%~dpi\..\bin\keytool.exe
    if exist "!KEYTOOL!" goto :found
)

REM Try common locations
if exist "%JAVA_HOME%\bin\keytool.exe" set KEYTOOL=%JAVA_HOME%\bin\keytool.exe
if exist "C:\Program Files\Java\jdk*\bin\keytool.exe" (
    for /d %%d in ("C:\Program Files\Java\jdk*") do set KEYTOOL=%%d\bin\keytool.exe
)

:found
if "!KEYTOOL!"=="" (
    echo ERROR: keytool not found. Please ensure Java JDK is installed.
    echo You can download from: https://adoptium.net/
    pause
    exit /b 1
)

echo Using: !KEYTOOL!
echo.

REM Create .android directory if not exists
if not exist "%USERPROFILE%\.android" mkdir "%USERPROFILE%\.android"

REM Generate keystore
echo Generating debug.keystore...
"!KEYTOOL!" -genkeypair -v -keystore "%USERPROFILE%\.android\debug.keystore" ^
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 ^
    -storepass android -keypass android ^
    -dname "CN=Android Debug,O=Android,C=US"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo SUCCESS: debug.keystore generated!
    echo Location: %USERPROFILE%\.android\debug.keystore
    echo.
    echo You can now build the release APK.
) else (
    echo.
    echo ERROR: Failed to generate keystore
)

pause
