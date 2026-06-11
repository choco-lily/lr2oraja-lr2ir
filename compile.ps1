$JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"
$JAVAC = "$JAVA_HOME\bin\javac.exe"
$JAR = "$JAVA_HOME\bin\jar.exe"

$DEPENDENCY_JAR = "..\lr2oraja-endlessdream-main(lr2oraja 구동기)\dist\lr2oraja-0.8.8-endlessdream-pre0.3.2.jar"

if (-not (Test-Path $DEPENDENCY_JAR)) {
    $DEPENDENCY_JAR = "..\lr2oraja.jar"
}

Write-Output "[Build] Using dependency jar: $DEPENDENCY_JAR"

# --- Build Eunga-IR.jar ---
Write-Output "[Build] Cleaning previous Eunga-IR build..."
if (Test-Path bin) { Remove-Item -Recurse -Force bin }
if (Test-Path Eunga-IR.jar) { Remove-Item Eunga-IR.jar }

Write-Output "[Build] Creating directories..."
New-Item -ItemType Directory -Path bin | Out-Null

Write-Output "[Build] Compiling EungaIRConnection..."
& $JAVAC -source 17 -target 17 -cp $DEPENDENCY_JAR -d bin src\bms\player\beatoraja\ir\EungaIRConnection.java

if ($LASTEXITCODE -ne 0) {
    Write-Error "[Build] Error: EungaIR Connection compilation failed."
    exit $LASTEXITCODE
}

Write-Output "[Build] Packaging Eunga-IR.jar..."
& $JAR cvf Eunga-IR.jar -C bin bms

if ($LASTEXITCODE -ne 0) {
    Write-Error "[Build] Error: Eunga-IR Packaging failed."
    exit $LASTEXITCODE
}

Write-Output "[Build] Cleaning up temporary files..."
if (Test-Path bin) { Remove-Item -Recurse -Force bin }

Write-Output "[Build] Success! Generated Eunga-IR.jar"

