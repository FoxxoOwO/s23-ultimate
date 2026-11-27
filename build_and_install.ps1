param([switch]$NoInstall)
$ErrorActionPreference = "Stop"

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projDir = Join-Path $rootDir "S23_LSPosed_Module"
$outApk = Join-Path $rootDir "S23_LSPosed_Enhancer.apk"

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not (Test-Path $sdk)) { Write-Error "Android SDK not found (set ANDROID_HOME)"; exit 1 }
$env:ANDROID_HOME = $sdk
$adb = "$sdk\platform-tools\adb.exe"

# SESL9 AARs use class file version 68.0 (JDK 24+), so prefer JDK 25 / 24 for Gradle & javac
$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium\jdk-2*" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
if (-not $jdk) {
    $jdk = Get-ChildItem "C:\Program Files\Java\jdk-2*" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
}
if (-not $jdk) {
    $jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium\jdk-17*" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
}
if ($jdk) {
    $env:JAVA_HOME = $jdk.FullName
    $env:PATH = "$($jdk.FullName)\bin;$env:PATH"
}

# Locate Gradle (9.9.1 preferred, else any cached wrapper dist or PATH)
$gradleCmd = (Get-Command gradle -ErrorAction SilentlyContinue).Source
if (-not $gradleCmd) {
    $gradleBat = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists" -Recurse -Filter "gradle.bat" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($gradleBat) { $gradleCmd = $gradleBat.FullName }
}
if (-not $gradleCmd) { Write-Error "Gradle binary not found on PATH or ~/.gradle/wrapper/dists"; exit 1 }

# Populate GitHub Packages credentials for tribalfs SESL9 if not already set
if (-not $env:GPR_TOKEN) {
    if ($env:GITHUB_TOKEN) {
        $env:GPR_TOKEN = $env:GITHUB_TOKEN
    } elseif (Get-Command gh -ErrorAction SilentlyContinue) {
        $env:GPR_TOKEN = (gh auth token 2>$null).Trim()
    }
}
if (-not $env:GPR_USER) {
    if ($env:GITHUB_ACTOR) {
        $env:GPR_USER = $env:GITHUB_ACTOR
    } elseif (Get-Command gh -ErrorAction SilentlyContinue) {
        $env:GPR_USER = (gh api user --jq ".login" 2>$null).Trim()
    }
}

Write-Host "--- 1. Building Release APK via Gradle (SESL9) ---"
& $gradleCmd -p $projDir :app:assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed"
    exit $LASTEXITCODE
}

$builtApk = Join-Path $projDir "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $builtApk)) {
    Write-Error "Release APK missing at $builtApk"
    exit 1
}
Copy-Item -Path $builtApk -Destination $outApk -Force
Write-Host "APK Built successfully: $outApk"

Write-Host "--- 2. Installing APK to Device ---"
if ($NoInstall) {
    Write-Host "-NoInstall specified, skipping install."
    exit 0
}

$devId = $env:ANDROID_SERIAL
if (-not $devId) {
    $deviceLine = & "$adb" devices | Select-String -Pattern "`tdevice$" | Select-Object -First 1
    if ($deviceLine) { $devId = ($deviceLine -split "`t")[0].Trim() }
}
if ($devId) {
    Write-Host "Found device: $devId"
    & "$adb" -s $devId install -r "$outApk"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "ADB install failed with exit code $LASTEXITCODE"
        exit $LASTEXITCODE
    }
    & "$adb" -s $devId shell "pm grant com.s23.enhancements android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; appops set com.s23.enhancements WRITE_SETTINGS allow 2>/dev/null"
    Write-Host "Permissions granted to com.s23.enhancements"
} else {
    Write-Warning "No connected ADB device found. Please install $outApk manually."
}

Write-Host "Done!"
