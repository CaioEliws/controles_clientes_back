param([ValidateSet('Start','Stop')][string]$Mode='Start')
$ErrorActionPreference='Stop'
$auditRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$auditJar=Join-Path $auditRoot '.auditoria/app.jar'
$auditPidFile=Join-Path $auditRoot '.auditoria/evidencias/backend-pid.txt'
if($Mode -eq 'Stop') {
    if(Test-Path -LiteralPath $auditPidFile) {
        $auditProcessId=[int](Get-Content -LiteralPath $auditPidFile)
        $auditProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$auditProcessId"
        if($auditProcess -and $auditProcess.Name -eq 'java.exe' -and $auditProcess.CommandLine.Contains($auditJar) -and $auditProcess.CommandLine.Contains('--server.port=18082')) {
            Stop-Process -Id $auditProcessId
        } elseif($auditProcess) {throw 'PID não pertence ao servidor de auditoria; processo preservado.'}
    }
    exit
}
if(Get-NetTCPConnection -LocalPort 18082 -State Listen -ErrorAction SilentlyContinue) {throw 'Porta de auditoria ocupada.'}
$auditArtifact=Get-ChildItem -LiteralPath (Join-Path $auditRoot 'target') -Filter '*.jar' | Select-Object -First 1
if(!$auditArtifact){throw 'Execute o package primeiro.'}
Copy-Item -LiteralPath $auditArtifact.FullName -Destination $auditJar
$auditArgs=@('-jar',('"'+$auditJar+'"'),'--server.port=18082','--server.address=127.0.0.1',
    '--spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/controle_clientes_auditoria',
    '--spring.datasource.username=auditor','--spring.datasource.password=',
    '--spring.datasource.driver-class-name=org.postgresql.Driver','--spring.jpa.hibernate.ddl-auto=update',
    '--spring.jpa.show-sql=false','--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect')
$auditStart=@{FilePath='C:/Program Files/Java/jdk-17/bin/java.exe';ArgumentList=$auditArgs;
    WorkingDirectory=$auditRoot;WindowStyle='Hidden';PassThru=$true;
    RedirectStandardOutput=(Join-Path $auditRoot '.auditoria/evidencias/backend-stdout.log');
    RedirectStandardError=(Join-Path $auditRoot '.auditoria/evidencias/backend-stderr.log')}
$auditProcess=Start-Process @auditStart
$auditProcess.Id | Set-Content -LiteralPath $auditPidFile
Write-Output "Servidor de auditoria iniciado: PID $($auditProcess.Id), porta 18082, PostgreSQL 15432."
