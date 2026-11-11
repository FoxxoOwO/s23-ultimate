param([switch]$NoInstall)
$ErrorActionPreference = "Stop"


$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "C:\Users\Teresa\AppData\Local\Android\Sdk" }
if (-not (Test-Path $sdk)) { Write-Error "Android SDK not found (set ANDROID_HOME)"; exit 1 }

$buildTools = (Get-ChildItem "$sdk\build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$platform = if (Test-Path "$sdk\platforms\android-36\android.jar") { "$sdk\platforms\android-36\android.jar" } else { (Get-ChildItem "$sdk\platforms" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName + "\android.jar" }
$libxposed = "D:\repositories\s23ultimate\libxposed-api-102.jar"
$adb = "$sdk\platform-tools\adb.exe"

$projDir = "D:\repositories\s23ultimate\S23_LSPosed_Module"
$srcDir = "$projDir\app\src\main\java"
$resDir = "$projDir\app\src\main\res"
$resourcesDir = "$projDir\app\src\main\resources"
$outDir = "$projDir\build_output"
$sevenZip = "C:\Program Files\7-Zip\7z.exe"
$outApk = "D:\repositories\s23ultimate\S23_LSPosed_Enhancer.apk"
$keystore = "D:\repositories\s23ultimate\debug.keystore"

Write-Host "--- 1. Cleaning build directory ---"
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force -Path "$outDir\classes" | Out-Null
New-Item -ItemType Directory -Force -Path "$outDir\dex" | Out-Null
New-Item -ItemType Directory -Force -Path "$outDir\java_r" | Out-Null

Write-Host "--- 2. Compiling resources with AAPT2 ---"
& "$buildTools\aapt2.exe" compile --dir "$resDir" -o "$outDir\compiled_res.zip"
& "$buildTools\aapt2.exe" link -I "$platform" --manifest "$projDir\app\src\main\AndroidManifest.xml" -o "$outDir\base_res.apk" --java "$outDir\java_r" "$outDir\compiled_res.zip"

Write-Host "--- 3. Compiling Java sources with javac (--release 17) ---"
$javaFiles = Get-ChildItem -Path "$srcDir", "$outDir\java_r" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
& javac --release 17 -encoding UTF-8 -cp "$platform;$libxposed" -d "$outDir\classes" $javaFiles

if ($LASTEXITCODE -ne 0) {
    Write-Error "javac failed"
    exit 1
}

Write-Host "--- 4. Dexing with D8 (excluding LibXposed from DEX) ---"
$classFiles = Get-ChildItem -Path "$outDir\classes" -Filter "*.class" -Recurse | ForEach-Object { $_.FullName }
& "$buildTools\d8.bat" --release --min-api 31 --lib "$platform" --classpath "$libxposed" --output "$outDir\dex" $classFiles

if ($LASTEXITCODE -ne 0) {
    Write-Error "d8 failed"
    exit 1
}

Write-Host "--- 5. Packaging APK with 7-Zip ---"
Copy-Item "$outDir\base_res.apk" "$outDir\unaligned.apk" -Force
& "$sevenZip" a -tzip "$outDir\unaligned.apk" "$outDir\dex\classes.dex"

Push-Location "$resourcesDir"
& "$sevenZip" a -tzip "$outDir\unaligned.apk" "META-INF\xposed\*"
Pop-Location

Write-Host "--- 6. Zipalign & Apksigner ---"
if (Test-Path "$outDir\aligned.apk") { Remove-Item -Force "$outDir\aligned.apk" }
& "$buildTools\zipalign.exe" -p -f -v 4 "$outDir\unaligned.apk" "$outDir\aligned.apk" | Out-Null

& "$buildTools\apksigner.bat" sign --ks "$keystore" --ks-pass "pass:android" --key-pass "pass:android" --ks-key-alias "debug" --out "$outApk" "$outDir\aligned.apk"

if ($LASTEXITCODE -ne 0) {
    Write-Error "apksigner failed"
    exit 1
}

Write-Host "APK Built successfully: $outApk"

Write-Host "--- 7. Installing APK to Device ---"
Write-Host "--- 7. Installing APK to Device ---"
if ($NoInstall) {
    Write-Host "-NoInstall specified, skipping install."
    exit 0
}
$deviceLine = & "$adb" devices | Select-String -Pattern "`tdevice$" | Select-Object -First 1
if ($deviceLine) {
    $devId = ($deviceLine -split "`t")[0].Trim()
    Write-Host "Found device: $devId"
    & "$adb" -s $devId install -r "$outApk"
    & "$adb" -s $devId shell "pm grant com.s23.enhancements android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; appops set com.s23.enhancements WRITE_SETTINGS allow 2>/dev/null"
    Write-Host "Permissions granted to com.s23.enhancements"
} else {
    Write-Warning "No connected ADB device found. Please install $outApk manually."
}

Write-Host "Done!"
