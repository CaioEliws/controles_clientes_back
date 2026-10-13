$ErrorActionPreference='Stop'
$auditRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$auditEvidence=Join-Path $auditRoot '.auditoria/evidencias'
& (Join-Path $PSScriptRoot 'servidor.ps1') -Mode Stop
$auditResults=@{api='encerrada';frontendPreview='ausente';frontendDev='ausente';postgres='ausente'}
foreach($auditTarget in @(@{file='frontend-pid.txt';port=5180;key='frontendPreview'},@{file='frontend-dev-pid.txt';port=5181;key='frontendDev'})) {
    $auditPidPath=Join-Path $auditEvidence $auditTarget.file
    if(!(Test-Path -LiteralPath $auditPidPath)){continue}
    $auditProcessId=[int](Get-Content -LiteralPath $auditPidPath)
    $auditProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$auditProcessId"
    if(!$auditProcess){continue}
    $auditCommand=$auditProcess.CommandLine.Replace('\','/').ToLowerInvariant()
    $auditVitePath=((Join-Path (Split-Path $auditRoot -Parent) 'frontend/node_modules/vite/bin/vite.js').Replace('\','/')).ToLowerInvariant()
    if($auditProcess.Name -ne 'node.exe' -or !$auditCommand.Contains($auditVitePath) -or $auditCommand -notmatch ('--port(?:=|\s+)'+$auditTarget.port+'(?:\s|$)')) {
        throw 'PID não corresponde ao Vite da auditoria; processo preservado.'
    }
    Stop-Process -Id $auditProcessId
    $auditResults[$auditTarget.key]='encerrado'
}
$auditData=[IO.Path]::GetFullPath((Join-Path $auditRoot '.auditoria/postgres-data'))
$auditExpected=[IO.Path]::GetFullPath($auditRoot)+[IO.Path]::DirectorySeparatorChar
if(!$auditData.StartsWith($auditExpected,[StringComparison]::OrdinalIgnoreCase)){throw 'Diretório PostgreSQL fora da auditoria.'}
$auditPostmaster=Join-Path $auditData 'postmaster.pid'
if(Test-Path -LiteralPath $auditPostmaster) {
    $auditPidLines=Get-Content -LiteralPath $auditPostmaster
    if([int]$auditPidLines[3] -ne 15432 -or [IO.Path]::GetFullPath($auditPidLines[1]) -ne $auditData){throw 'Cluster não corresponde ao PostgreSQL isolado; preservado.'}
    & 'C:/Program Files/PostgreSQL/18/bin/pg_ctl.exe' -D $auditData -m fast -w stop
    if($LASTEXITCODE -ne 0){throw 'Não foi possível encerrar o cluster isolado.'}
    $auditResults.postgres='encerrado; dados preservados'
}
$auditResults | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $auditEvidence 'encerramento.json') -Encoding utf8
$auditResults
