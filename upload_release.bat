@echo off
setlocal
echo [Release] Starting build and upload process for BMS-IR plugin...
python build_and_upload.py
if %ERRORLEVEL% neq 0 (
    echo [Release] Error: Build or upload failed.
    exit /b %ERRORLEVEL%
)
echo [Release] Done!
endlocal
