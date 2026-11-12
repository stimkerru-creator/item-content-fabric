param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$java = Join-Path $JavaHome 'bin/java.exe'
$javac = Join-Path $JavaHome 'bin/javac.exe'
$release = (Get-Content (Join-Path $projectRoot 'gradle.properties') | Where-Object { $_ -match '^version=' }) -split '=', 2
$jarName = 'item-content-editor-' + $release[1].Trim() + '.jar'
$sourceJar = Join-Path $projectRoot ('editor/build/libs/' + $jarName)
$fixture = Join-Path $projectRoot ('build/tmp/launcher-close-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $fixture 'tools') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $fixture 'lib') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'run-editor.ps1') -Destination (Join-Path $fixture 'tools/run-editor.ps1')
Copy-Item -LiteralPath (Join-Path $projectRoot 'gradle.properties') -Destination $fixture
$fixtureJar = Join-Path $fixture ('lib/' + $jarName)
Copy-Item -LiteralPath $sourceJar -Destination $fixtureJar
& $javac -encoding UTF-8 -cp $sourceJar -d (Join-Path $fixture 'classes') (Join-Path $projectRoot 'editor/src/test/java/dev/itemcontent/editor/CloseDialogNativeProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed.' }
& (Join-Path $fixture 'tools/run-editor.ps1') -Screenshot (Join-Path $fixture 'launch.png')
if ($LASTEXITCODE -ne 0) { throw 'Launcher failed.' }
$snapshot = (Get-ChildItem -LiteralPath (Join-Path $fixture '.editor-runtime') -Filter $jarName -Recurse | Select-Object -First 1).FullName
if (!$snapshot) { throw 'No immutable editor snapshot was created.' }
foreach ($action in @('discard','cancel','x')) {
    $case = Join-Path $fixture $action
    New-Item -ItemType Directory -Path $case | Out-Null
    $stdout = Join-Path $case 'stdout.log'; $stderr = Join-Path $case 'stderr.log'
    $classPath = (Join-Path $fixture 'classes') + ';' + $snapshot
    $process = Start-Process -FilePath $java -ArgumentList @('-cp',('"' + $classPath + '"'),'dev.itemcontent.editor.CloseDialogNativeProbe',('"' + $case + '"'),$action) -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds(12)
    while (!(Test-Path -LiteralPath (Join-Path $case 'ready')) -and [DateTime]::UtcNow -lt $deadline -and !$process.HasExited) { Start-Sleep -Milliseconds 100 }
    # Deliberately replace only the fixture's source JAR while the snapshot process is alive.
    [System.IO.File]::WriteAllText($fixtureJar, 'Simulated replacement of build output')
    [System.IO.File]::WriteAllText((Join-Path $case 'go'), 'go')
    if (!$process.WaitForExit(7000)) { Stop-Process -Id $process.Id -Force; throw ('Timed out: ' + $action) }
    $output = [System.IO.File]::ReadAllText($stdout)
    $errors = [System.IO.File]::ReadAllText($stderr)
    $expected = if ($action -eq 'discard') { 'RESULT true' } else { 'RESULT false' }
    if ($output -notmatch 'DIALOG ([3-9][0-9]{2}|[1-9][0-9]{3})x[1-9][0-9]{2}' -or !$output.Contains($expected) -or $errors.Length -gt 0) {
        throw ('Native close probe failed: ' + $action + ' See ' + $case)
    }
    Write-Output ('PASS ' + $action + ': ' + ($output -split "`r?`n" | Where-Object { $_ -match '^DIALOG|^RESULT' } | ForEach-Object { $_.Trim() }))
}
Write-Output ('PASS immutable launcher and native close dialog. Fixture: ' + $fixture)
