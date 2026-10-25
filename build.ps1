param([switch]$TestOnly)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$privateJava = Join-Path $PSScriptRoot '.tools\java'
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath $privateJava)) {
    $env:JAVA_HOME = Get-ChildItem -LiteralPath $privateJava -Directory | Select-Object -First 1 -ExpandProperty FullName
}
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH }
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.local-gradle'
$tasks = @('testDebugUnitTest', 'lintDebug')
if (-not $TestOnly) { $tasks += 'assembleDebug' }
& .\gradlew.bat --no-daemon @tasks
if ($LASTEXITCODE -ne 0) { throw 'La compilación o las pruebas no pasaron.' }
if (-not $TestOnly) {
    New-Item -ItemType Directory -Path 'artifacts' -Force | Out-Null
    Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination 'artifacts\JARVIS-Android-0.1.0.apk' -Force
    Get-FileHash -LiteralPath 'artifacts\JARVIS-Android-0.1.0.apk' -Algorithm SHA256
}
