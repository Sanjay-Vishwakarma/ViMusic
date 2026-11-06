<#
.SYNOPSIS
  Builds the release APK (version auto-increments) and copies it to dist\ViMusic-<version>.apk

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\build-apk.ps1
  powershell -ExecutionPolicy Bypass -File tools\build-apk.ps1 -SkipTests
#>
param(
    # Skip the live YouTube stream test that runs before the build.
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# Gradle 7.5.1 runs only on JDK 11-18; use the JDK 17 in %USERPROFILE%\.jdks
$jdk = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.jdks') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $jdk) {
    throw "JDK 17 not found in $env:USERPROFILE\.jdks. See README -> 'Zaroori cheezein'."
}
$env:JAVA_HOME = $jdk.FullName
Write-Host "Using JDK: $($jdk.FullName)"

Push-Location $root
try {
    if (-not $SkipTests) {
        Write-Host "`n== Live stream test (YouTube) ==" -ForegroundColor Cyan
        & .\gradlew.bat --no-daemon :innertube:test --tests PlayerStreamTest
        if ($LASTEXITCODE -ne 0) { throw "Stream test failed - songs would not play. Not building. See README -> 'Jab YouTube kuch tod de'." }
    }

    Write-Host "`n== Building release APK ==" -ForegroundColor Cyan
    & .\gradlew.bat --no-daemon :app:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Build failed." }

    $code = (Select-String -Path 'app\version.properties' -Pattern '^VERSION_CODE=(\d+)').Matches[0].Groups[1].Value
    $name = "0.6.$([int]$code - 20)"
    New-Item -ItemType Directory -Force -Path 'dist' | Out-Null
    $out = "dist\ViMusic-$name.apk"
    Copy-Item 'app\build\outputs\apk\release\app-release.apk' $out -Force

    Write-Host "`nDONE: version $name (code $code)" -ForegroundColor Green
    Write-Host "APK : $(Join-Path $root $out)"
}
finally {
    Pop-Location
}
