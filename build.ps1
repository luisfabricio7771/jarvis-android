param([switch]$TestOnly)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$privateJava = Join-Path $PSScriptRoot '.tools\java'
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath $privateJava)) {
    $env:JAVA_HOME = Get-ChildItem -LiteralPath $privateJava -Directory | Select-Object -First 1 -ExpandProperty FullName
}
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH }
$digest = [System.Security.Cryptography.SHA256]::Create()
try { $rootHash = [BitConverter]::ToString($digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($PSScriptRoot))).Replace('-', '').Substring(0, 12) } finally { $digest.Dispose() }
$env:JARVIS_ANDROID_BUILD_ROOT = Join-Path ([IO.Path]::GetTempPath()) "jarvis-android-$rootHash"
$env:GRADLE_USER_HOME = Join-Path $env:JARVIS_ANDROID_BUILD_ROOT 'gradle-home'
New-Item -ItemType Directory -Path '.tools' -Force | Out-Null
Set-Content -LiteralPath '.tools\build-root.txt' -Encoding UTF8 -Value $env:JARVIS_ANDROID_BUILD_ROOT
$tasks = @('testDebugUnitTest', 'lintDebug')
if (-not $TestOnly) { $tasks += 'assembleDebug' }
& .\gradlew.bat --no-daemon --project-cache-dir (Join-Path $env:JARVIS_ANDROID_BUILD_ROOT 'project-cache') @tasks
if ($LASTEXITCODE -ne 0) { throw 'La compilación o las pruebas no pasaron.' }
if (-not $TestOnly) {
    New-Item -ItemType Directory -Path 'artifacts' -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $env:JARVIS_ANDROID_BUILD_ROOT 'app\outputs\apk\debug\app-debug.apk') -Destination 'artifacts\JARVIS-Android-0.2.0.apk' -Force
    Get-FileHash -LiteralPath 'artifacts\JARVIS-Android-0.2.0.apk' -Algorithm SHA256
}
