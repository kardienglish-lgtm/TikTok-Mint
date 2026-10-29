@echo off
setlocal
pushd "%~dp0"

if "%~1"=="" (
    echo Drag the original TikTok 47.4.42 APK onto this file,
    echo or launch it from a terminal with the APK path as an argument.
    echo.
    set /p APK_PATH=Enter full path to TikTok APK: 
) else (
    set "APK_PATH=%~1"
)

if not exist "%APK_PATH%" (
    echo ERROR: APK file not found: "%APK_PATH%"
    goto :failed
)

where py >nul 2>&1
if not errorlevel 1 (
    set "PY=py -3"
) else (
    set "PY=python"
)

%PY% --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python 3 is not installed or not on PATH.
    echo Install Python 3, then run this file again.
    goto :failed
)

where java >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java is not installed or not on PATH.
    echo Install JDK 17 or newer, then run this file again.
    goto :failed
)

echo.
echo === Checking input APK ===
%PY% check_apk.py "%APK_PATH%"
if errorlevel 1 goto :failed

echo.
echo === Building TikTok Mint (appearance-only compatibility profile) ===
%PY% -m kardinebi "%APK_PATH%" --profile appearance --accent BFE8AE -o "%~dp0build\kardinebi.apk"
if errorlevel 1 goto :failed

echo.
echo BUILD COMPLETE: "%~dp0build\kardinebi.apk"
echo The APK is signed with the project's debug key unless a keystore is supplied.
echo It cannot update the officially signed TikTok; uninstall TikTok first if Android rejects the signature.
pause
popd
exit /b 0

:failed
echo.
echo BUILD NOT COMPLETED. Read the error above; no working APK is promised.
pause
popd
exit /b 1
