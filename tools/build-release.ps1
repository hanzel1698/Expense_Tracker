# Build a release APK into dist/ (repo-relative paths).
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

Write-Host "Building release APK..." -ForegroundColor Cyan
& "$RepoRoot\gradlew.bat" assembleRelease --stacktrace
if ($LASTEXITCODE -ne 0) {
    throw "Gradle assembleRelease failed with exit code $LASTEXITCODE"
}

$ApkSource = Join-Path $RepoRoot "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $ApkSource)) {
    throw "Expected APK not found: $ApkSource"
}

$DistDir = Join-Path $RepoRoot "dist"
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null

$VersionName = (Select-String -Path (Join-Path $RepoRoot "app\build.gradle.kts") -Pattern 'versionName\s*=\s*"([^"]+)"').Matches[0].Groups[1].Value
$DestName = "expense-tracker-v$VersionName-release.apk"
$ApkDest = Join-Path $DistDir $DestName

Copy-Item -Path $ApkSource -Destination $ApkDest -Force
Write-Host "Release APK: $ApkDest" -ForegroundColor Green
