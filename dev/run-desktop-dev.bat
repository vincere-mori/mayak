@echo off
setlocal

cd /d "%~dp0.."

echo Checking sing-box...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ensure-sing-box.ps1"
if errorlevel 1 (
    echo.
    echo sing-box setup failed.
    pause
    exit /b 1
)

echo Starting Mayak desktop dev run...
echo (running as current user. For TUN mode the app will ask to elevate.)
call ".\gradlew.bat" :desktop:run --no-daemon

if errorlevel 1 (
    echo.
    echo Mayak dev run failed.
    pause
    exit /b 1
)
