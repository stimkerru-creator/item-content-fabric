param([switch]$Check, [string]$Screenshot, [string]$Open)
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
$jar = Join-Path $projectRoot "lib\item-content-editor-$version.jar"
if (!(Test-Path -LiteralPath $jar)) { $jar = Join-Path $projectRoot "editor\build\libs\item-content-editor-$version.jar" }
if (!(Test-Path -LiteralPath $jar)) {
    if (!(Test-Path -LiteralPath (Join-Path $projectRoot 'gradlew.bat'))) {
        throw 'Editor JAR is missing. Extract the complete release ZIP before launching.'
    }
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-home'
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $selectedJava)
    & (Join-Path $projectRoot 'gradlew.bat') -p (Join-Path $projectRoot 'editor') build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Editor build failed. See the messages above.' }
}
if ($Check) { Write-Output "Java: $selectedJava"; Write-Output "Editor: $jar"; exit 0 }
function Get-EditorDigest([string]$path) {
    $stream = [System.IO.File]::OpenRead($path)
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try { return ([System.BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
    finally { $algorithm.Dispose(); $stream.Dispose() }
}
# A running JVM must never read from a JAR that the next build can overwrite.
# One immutable snapshot is reused for each exact editor build.
$editorDigest = (Get-EditorDigest $jar)
$runtimeFolder = Join-Path $projectRoot (".editor-runtime\" + $editorDigest)
$runtimeJar = Join-Path $runtimeFolder (Split-Path -Leaf $jar)
New-Item -ItemType Directory -Path $runtimeFolder -Force | Out-Null
if (!(Test-Path -LiteralPath $runtimeJar)) {
    $pendingJar = Join-Path $runtimeFolder ([Guid]::NewGuid().ToString('N') + '.tmp')
    Copy-Item -LiteralPath $jar -Destination $pendingJar
    if ((Get-EditorDigest $pendingJar) -ne $editorDigest) {
        throw 'Editor changed during launch. Run the launcher again after the build finishes.'
    }
    try { Move-Item -LiteralPath $pendingJar -Destination $runtimeJar -ErrorAction Stop }
    catch { if (!(Test-Path -LiteralPath $runtimeJar)) { throw } }
}
if ((Get-EditorDigest $runtimeJar) -ne $editorDigest) {
    throw 'The editor runtime copy is damaged. Do not overwrite it while an editor is running.'
}
$editorArguments = @('-jar', $runtimeJar)
if ($Screenshot) { $editorArguments += @('--screenshot', $Screenshot) }
if ($Open) { $editorArguments += @('--open', $Open) }
& $selectedJava @editorArguments
exit $LASTEXITCODE
