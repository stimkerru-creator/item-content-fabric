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
    $version=((Get-Content (Join-Path $probeRoot 'gradle.properties') | Where-Object { $_ -match '^version=' }) -split '=',2)[1].Trim()
    $generated=Join-Path $probeRoot 'ready-example.jar'
    & (Join-Path $probeRoot 'runtime\bin\java.exe') -jar (Join-Path $probeRoot "lib\item-content-editor-$version.jar") --build-mod (Join-Path $probeRoot 'examples\native-models.icproject') release_example 'Release example' 1.0.0 $generated
    if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $generated)) { throw 'Packaged editor cannot build standalone mod without a compiler' }
    $document=Get-Content -LiteralPath (Join-Path $probeRoot 'examples\native-models.icproject') -Raw -Encoding UTF8 | ConvertFrom-Json
    $document | Add-Member -NotePropertyName modItems -NotePropertyValue @($document.items[0].id) -Force
    $selectedProject=Join-Path $probeRoot 'selected.icproject'
    $document | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $selectedProject -Encoding UTF8
    $selectedJar=Join-Path $probeRoot 'selected.jar'
    & (Join-Path $probeRoot 'runtime\bin\java.exe') -jar (Join-Path $probeRoot "lib\item-content-editor-$version.jar") --build-mod $selectedProject release_subset 'Only selected' 1.0.0 $selectedJar
    if ($LASTEXITCODE -ne 0) { throw 'Saved subset export failed' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jar=[System.IO.Compression.ZipFile]::OpenRead($selectedJar)
    try {
        $reader=New-Object System.IO.StreamReader($jar.GetEntry('itemcontent/content.json').Open())
        try { $selectedContent=$reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if (@($selectedContent.items).Count -ne 1) { throw 'Packaged editor exported unselected items' }
        if (!$jar.GetEntry('META-INF/jars/itemcontent-runtime.jar')) { throw 'Standalone runtime missing from export' }
    } finally { $jar.Dispose() }
    Write-Output "PASS: extracted ZIP, spaces in path, no installed Java, immutable launcher, actual editor rendering, standalone JAR and saved subset export. $screenshot"
} finally { $env:JAVA_HOME=$oldJavaHome; $env:PATH=$oldPath }
