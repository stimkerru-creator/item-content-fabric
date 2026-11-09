param([switch]$Check, [string]$Screenshot)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaCandidates = @()
if ($env:JAVA_HOME) { $javaCandidates += Join-Path $env:JAVA_HOME 'bin\java.exe' }
$onPath = Get-Command java.exe -ErrorAction SilentlyContinue
if ($onPath) { $javaCandidates += $onPath.Source }
# Check standard installation folders as PATH may still point to Java 8.
foreach ($folder in @('Java', 'Eclipse Adoptium', 'Android\openjdk')) {
    $base = Join-Path $env:ProgramFiles $folder
    if (Test-Path -LiteralPath $base) {
        $javaCandidates += Get-ChildItem -LiteralPath $base -Directory | ForEach-Object {
            Join-Path $_.FullName 'bin\java.exe'
        }
    }
}
$selectedJava = $null
foreach ($candidate in ($javaCandidates | Select-Object -Unique)) {
    if (!(Test-Path -LiteralPath $candidate)) { continue }
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $candidate
    $info.Arguments = '-version'
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardError = $true
    $info.RedirectStandardOutput = $true
    $probe = [System.Diagnostics.Process]::Start($info)
    $versionText = $probe.StandardError.ReadToEnd() + $probe.StandardOutput.ReadToEnd()
    $probe.WaitForExit()
    $probe.Dispose()
    if ($versionText -match 'version "(\d+)(?:\.(\d+))?') {
        $major = [int]$Matches[1]
        if ($major -ge 17) { $selectedJava = $candidate; break }
    }
}
if (!$selectedJava) { throw 'Install Java 17 or newer, then run this launcher again.' }
$versionLine = Get-Content (Join-Path $projectRoot 'gradle.properties') | Where-Object { $_ -match '^version=' }
$version = ($versionLine -split '=', 2)[1].Trim()
$jar = Join-Path $projectRoot "editor\build\libs\item-content-editor-$version.jar"
if (!(Test-Path -LiteralPath $jar)) {
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $selectedJava)
    & (Join-Path $projectRoot 'gradlew.bat') -p (Join-Path $projectRoot 'editor') build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Editor build failed. See the messages above.' }
}
if ($Check) { Write-Output "Java: $selectedJava"; Write-Output "Editor: $jar"; exit 0 }
if ($Screenshot) { & $selectedJava -jar $jar --screenshot $Screenshot }
else { & $selectedJava -jar $jar }
exit $LASTEXITCODE
