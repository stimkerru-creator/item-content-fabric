param([Parameter(Mandatory=$true)][string]$Archive)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$archivePath = (Resolve-Path -LiteralPath $Archive).Path
$probeRoot = Join-Path $root ('build\windows-release-check\' + [Guid]::NewGuid().ToString('N') + '\folder with spaces')
New-Item -ItemType Directory -Path $probeRoot -Force | Out-Null
Expand-Archive -LiteralPath $archivePath -DestinationPath $probeRoot
if (Test-Path -LiteralPath (Join-Path $probeRoot 'gradlew.bat')) { throw 'Source build files leaked into the ready-to-run ZIP' }
$oldJavaHome=$env:JAVA_HOME; $oldPath=$env:PATH
try {
    # Simulate a computer without Java on PATH and with an invalid JAVA_HOME.
    $env:JAVA_HOME=Join-Path $probeRoot 'no-installed-java'
    $env:PATH=Join-Path $env:SystemRoot 'System32'
    $launcher=Join-Path $probeRoot 'tools\run-editor.ps1'
    $output=& "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File $launcher -Check 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Launcher failed: $output" }
    if (($output -join "`n") -notlike '*runtime\bin\java.exe*') { throw 'Launcher did not choose the bundled Java' }
    $screenshot=Join-Path $probeRoot 'editor.png'
    & "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File $launcher -Screenshot $screenshot -Open (Join-Path $probeRoot 'examples\native-models.icproject')
    if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $screenshot)) { throw 'Packaged editor failed to render using bundled Java' }
    Write-Output "PASS: extracted ZIP, spaces in path, no installed Java, immutable launcher, actual editor rendering. $screenshot"
} finally { $env:JAVA_HOME=$oldJavaHome; $env:PATH=$oldPath }
