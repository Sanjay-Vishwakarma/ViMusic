<#
.SYNOPSIS
  Updates NewPipeExtractor (the library that resolves YouTube stream URLs) to
  its latest release, verifies streaming still works, then builds the APK.
  If the new version fails the live test, the old version is restored.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\update-newpipe.ps1
  powershell -ExecutionPolicy Bypass -File tools\update-newpipe.ps1 -Version v0.26.6
  powershell -ExecutionPolicy Bypass -File tools\update-newpipe.ps1 -NoBuild
#>
param(
    # A specific tag instead of the latest release.
    [string]$Version,
    # Update + test only, don't build the APK.
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$root = Split-Path -Parent $PSScriptRoot
$settings = Join-Path $root 'settings.gradle.kts'
$pattern = '(library\("newpipe-extractor", "com\.github\.TeamNewPipe", "NewPipeExtractor"\)\.version\(")([^"]+)("\))'

$content = Get-Content -Raw -Path $settings
$m = [regex]::Match($content, $pattern)
if (-not $m.Success) { throw "NewPipeExtractor line not found in settings.gradle.kts" }
$current = $m.Groups[2].Value

if (-not $Version) {
    $Version = (Invoke-RestMethod -Uri 'https://api.github.com/repos/TeamNewPipe/NewPipeExtractor/releases/latest' -UseBasicParsing).tag_name
}
Write-Host "Current: $current   Target: $Version"

if ($current -eq $Version) {
    Write-Host "Already on $Version - nothing to update." -ForegroundColor Green
    Write-Host "If songs still don't play, YouTube may have broken it before a fix was released. See README."
    exit 0
}

# JitPack builds each tag on demand; make sure this one actually built.
try {
    $jp = Invoke-RestMethod -Uri "https://jitpack.io/api/builds/com.github.TeamNewPipe/NewPipeExtractor/$Version" -UseBasicParsing
    Write-Host "JitPack status for ${Version}: $($jp.status)"
    if ($jp.status -ne 'ok') { throw "JitPack hasn't built $Version yet (status: $($jp.status)). Try again later." }
} catch {
    throw "Could not confirm JitPack build for ${Version}: $($_.Exception.Message)"
}

$updated = [regex]::Replace($content, $pattern, "`${1}$Version`${3}")
[IO.File]::WriteAllText($settings, $updated)
Write-Host "settings.gradle.kts -> $Version"

$jdk = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.jdks') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $jdk) { throw "JDK 17 not found in $env:USERPROFILE\.jdks" }
$env:JAVA_HOME = $jdk.FullName

Push-Location $root
try {
    Write-Host "`n== Live stream test with $Version ==" -ForegroundColor Cyan
    & .\gradlew.bat --no-daemon :innertube:test --tests PlayerStreamTest
    if ($LASTEXITCODE -ne 0) {
        [IO.File]::WriteAllText($settings, $content)
        throw "Stream test FAILED with $Version - restored $current. If it is a compile error, the library API changed: fix innertube\...\newpipe\NewPipeStreams.kt (see README)."
    }
    Write-Host "Stream test passed with $Version" -ForegroundColor Green
}
finally {
    Pop-Location
}

if (-not $NoBuild) {
    & (Join-Path $PSScriptRoot 'build-apk.ps1') -SkipTests
}
