@echo off
setlocal

echo ===================================================
echo   BMS IR Plugin Client Spoofing CLI Tool
echo ===================================================
echo.

if "%~1"=="" (
    echo [Error] No JAR file provided!
    echo Please drag and drop a .jar file onto this batch file.
    echo.
    pause
    exit /b 1
)

:: Check if Python is installed
python --version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [Error] Python is not installed or not in the PATH.
    echo Please install Python to run this utility.
    echo.
    pause
    exit /b 1
)

echo [Info] File: "%~1"
python "%~dp0spoof_jar.py" "%~1"

endlocal
