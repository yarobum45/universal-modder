# Candidate build only. Requires a full JDK 17; never installs into a game directory.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    throw 'Set JAVA_HOME to a full JDK 17 (not the Minecraft JRE), then rerun.'
}
$javac = Join-Path $env:JAVA_HOME 'bin/javac.exe'
$version = (& $javac -version | Out-String)
if ($version -notmatch 'javac 17\.') { throw "This candidate requires JDK 17. Found: $version" }
$tools = Join-Path $root 'artifacts/toolchain'
New-Item -ItemType Directory -Force -Path $tools | Out-Null
$gradle = Join-Path $tools 'gradle-8.1.1/bin/gradle.bat'
if (-not (Test-Path $gradle)) {
    $zip = Join-Path $tools 'gradle-8.1.1-bin.zip'
    $url = 'https://downloads.gradle.org/distributions/gradle-8.1.1-bin.zip'
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $zip
    # Published Gradle 8.1.1 checksum, verified against its official endpoint in cloud research.
    $expected = 'e111cb9948407e26351227dabce49822fb88c37ee72f1d1582a69c68af2e702f'
    if ((Get-FileHash -Algorithm SHA256 $zip).Hash -ine $expected) { throw 'Gradle SHA-256 mismatch; not extracting' }
    Expand-Archive -Path $zip -DestinationPath $tools -Force
}
$env:GRADLE_USER_HOME = Join-Path $root 'artifacts/gradle-home'
& $gradle --no-daemon --console=plain -p (Join-Path $root 'mc') build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Get-FileHash -Algorithm SHA256 (Join-Path $root 'mc/build/libs/um-sa-bridge-0.1.0-diagnostic.jar')
