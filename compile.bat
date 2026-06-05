@echo off
setlocal

set "JAVA_HOME=C:\Program Files\Java\jdk-25.0.2"
set "JAVAC=%JAVA_HOME%\bin\javac.exe"
set "JAR=%JAVA_HOME%\bin\jar.exe"

set "DEPENDENCY_JAR="

rem 1. Check in game engine's dist directory
for %%f in ("..\lr2oraja-endlessdream-main(lr2oraja 구동기)\dist\lr2oraja-*.jar") do (
    set "DEPENDENCY_JAR=%%f"
)

rem 2. Fallback to lr2oraja(lr2oraja 구동기).jar in parent directory
if not defined DEPENDENCY_JAR (
    if exist "..\lr2oraja(lr2oraja 구동기).jar" (
        set "DEPENDENCY_JAR=..\lr2oraja(lr2oraja 구동기).jar"
    )
)

rem 3. Fallback to generic lr2oraja.jar
if not defined DEPENDENCY_JAR (
    if exist "..\lr2oraja.jar" (
        set "DEPENDENCY_JAR=..\lr2oraja.jar"
    )
)

if not defined DEPENDENCY_JAR (
    echo [Build] Error: Could not find dependency jar.
    exit /b 1
)

echo [Build] Using dependency jar: %DEPENDENCY_JAR%

echo [Build] Cleaning previous builds...
if exist bin rd /s /q bin
if exist BMS-IR.jar del BMS-IR.jar

echo [Build] Creating directories...
mkdir bin

echo [Build] Compiling Java source...
"%JAVAC%" -source 17 -target 17 -cp "%DEPENDENCY_JAR%" -d bin src\bms\player\beatoraja\ir\LR2IRConnectionCustom.java
if %ERRORLEVEL% neq 0 (
    echo [Build] Error: Compilation failed.
    exit /b %ERRORLEVEL%
)

echo [Build] Packaging JAR file...
"%JAR%" cvf BMS-IR.jar -C bin bms
if %ERRORLEVEL% neq 0 (
    echo [Build] Error: Packaging failed.
    exit /b %ERRORLEVEL%
)

echo [Build] Cleaning up temporary files...
rd /s /q bin

echo [Build] Success! Generated BMS-IR.jar
endlocal
