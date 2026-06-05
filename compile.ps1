$JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"
$JAVAC = "$JAVA_HOME\bin\javac.exe"
$JAR = "$JAVA_HOME\bin\jar.exe"

$DEPENDENCY_JAR = "..\lr2oraja-endlessdream-main(lr2oraja 구동기)\dist\lr2oraja-0.8.8-endlessdream-pre0.3.2.jar"

if (-not (Test-Path $DEPENDENCY_JAR)) {
    $DEPENDENCY_JAR = "..\lr2oraja.jar"
}

Write-Output "[Build] Using dependency jar: $DEPENDENCY_JAR"

Write-Output "[Build] Cleaning previous builds..."
if (Test-Path bin) { Remove-Item -Recurse -Force bin }
if (Test-Path BMS-IR.jar) { Remove-Item BMS-IR.jar }

Write-Output "[Build] Creating directories..."
New-Item -ItemType Directory -Path bin | Out-Null

Write-Output "[Build] Compiling Java source..."
& $JAVAC -source 17 -target 17 -cp $DEPENDENCY_JAR -d bin src\bms\player\beatoraja\ir\LR2IRConnectionCustom.java

if ($LASTEXITCODE -ne 0) {
    Write-Error "[Build] Error: Compilation failed."
    exit $LASTEXITCODE
}

Write-Output "[Build] Packaging JAR file..."
& $JAR cvf BMS-IR.jar -C bin bms

if ($LASTEXITCODE -ne 0) {
    Write-Error "[Build] Error: Packaging failed."
    exit $LASTEXITCODE
}

Write-Output "[Build] Cleaning up temporary files..."
if (Test-Path bin) { Remove-Item -Recurse -Force bin }

Write-Output "[Build] Success! Generated BMS-IR.jar"
