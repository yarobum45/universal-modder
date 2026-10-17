# Invoked by repository-root BUILD.bat. All settings are process-local.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$bridgeRoot = Split-Path $PSScriptRoot -Parent
$repoRoot = Split-Path (Split-Path $bridgeRoot -Parent) -Parent
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$logDir = Join-Path $repoRoot 'BUILD-LOGS'
$ready = Join-Path $repoRoot 'READY'
$staging = Join-Path $repoRoot ('.ready-stage-' + $stamp)
$logPath = Join-Path $logDir ('build-' + $stamp + '.log')
$transcribing = $false
$result = 1
try {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    Start-Transcript -LiteralPath $logPath -Force | Out-Null
    $transcribing = $true
    Write-Host "Build log: $logPath"
    Write-Host "JAVA_HOME: $env:JAVA_HOME"
    Set-Location -LiteralPath $bridgeRoot
    # Preserve old output, but never leave an old READY looking like this run succeeded.
    if (Test-Path -LiteralPath $ready) {
        $previous = Join-Path $repoRoot ('READY.previous-' + $stamp)
        Move-Item -LiteralPath $ready -Destination $previous
        Write-Host "Previous output preserved: $previous"
    }
    & (Join-Path $PSScriptRoot 'Build-Forge.ps1')
    $jar = Join-Path $bridgeRoot 'mc/build/libs/um-sa-bridge-0.1.0-diagnostic.jar'
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw 'Build returned without the expected jar. READY will not be created.'
    }
    if ((Get-Item -LiteralPath $jar).Length -eq 0) { throw 'The built jar is empty.' }
    New-Item -ItemType Directory -Path $staging | Out-Null
    Copy-Item -LiteralPath $jar -Destination $staging
    Copy-Item -LiteralPath (Join-Path $bridgeRoot 'cleo/umsa-bridge[fs].js') -Destination $staging
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'INSTALL-RU.txt') -Destination (Join-Path $staging 'INSTALL-RU.txt')
    $hash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText((Join-Path $staging 'SHA256SUMS.txt'),
        "$hash  um-sa-bridge-0.1.0-diagnostic.jar`r`n", [Text.Encoding]::ASCII)
    Move-Item -LiteralPath $staging -Destination $ready
    Write-Host "BUILD SUCCEEDED. Diagnostic jar prepared: $ready"
    Write-Host 'In-game compatibility is still unverified. Nothing was installed into games.'
    $result = 0
    try { Start-Process -FilePath 'explorer.exe' -ArgumentList ('"' + $ready + '"') | Out-Null }
    catch { Write-Host "Could not open Explorer. Open READY manually: $($_.Exception.Message)" }
} catch {
    Write-Host ''
    Write-Host ('BUILD FAILED: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host $_.ScriptStackTrace
    Write-Host "Log: $logPath"
    Write-Host 'No new READY package was declared successful. Nothing was installed into games.'
} finally {
    if ($transcribing) {
        Stop-Transcript | Out-Null
        # Copy the complete transcript after it is closed, including the final outcome.
        if ($result -eq 0) {
            try { Copy-Item -LiteralPath $logPath -Destination (Join-Path $ready 'build.log') -Force }
            catch { Write-Host "Could not copy final log to READY; original log remains at $logPath" }
        }
    }
}
exit $result
