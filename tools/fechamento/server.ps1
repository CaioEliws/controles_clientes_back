param([ValidateSet('Start','Stop')][string]$Mode='Start',[int]$Port=18083,[string]$Database)
$ErrorActionPreference='Stop'
$closeRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$closeOut=Join-Path $closeRoot '.auditoria/fechamento'
$closeJar=Join-Path $closeOut "app-$Port.jar"
$closePidFile=Join-Path $closeOut "pid-$Port.txt"
if($Port -lt 18082 -or $Port -gt 18120){throw 'Porta fora da faixa isolada.'}
if($Mode -eq 'Stop') {
    if(Test-Path -LiteralPath $closePidFile) {
        $closeProcessId=[int](Get-Content -LiteralPath $closePidFile)
        $closeProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$closeProcessId"
        if($closeProcess -and $closeProcess.Name -eq 'java.exe' -and $closeProcess.CommandLine.Contains($closeJar) -and $closeProcess.CommandLine.Contains("--server.port=$Port")){Stop-Process -Id $closeProcessId}
        elseif($closeProcess){throw 'Processo não pertence ao fechamento.'}
    }
    exit
}
if($Database -notmatch '^controle_clientes_(auditoria|fechamento)[a-zA-Z0-9_]*$'){throw 'Banco não pertence aos testes isolados.'}
if(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue){throw 'Porta ocupada.'}
Copy-Item -LiteralPath (Join-Path $closeRoot 'target/controle_clientes-0.0.1-SNAPSHOT.jar') -Destination $closeJar
$closeArgs=@('-jar',('"'+$closeJar+'"'),"--server.port=$Port",'--server.address=127.0.0.1',
    "--spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/$Database",'--spring.datasource.username=auditor','--spring.datasource.password=',
    '--spring.datasource.driver-class-name=org.postgresql.Driver','--spring.jpa.hibernate.ddl-auto=validate',
    '--spring.jpa.show-sql=false','--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect')
$closeProc=Start-Process -FilePath 'C:/Program Files/Java/jdk-17/bin/java.exe' -ArgumentList $closeArgs -WorkingDirectory $closeRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $closeOut "api-$Port.log") -RedirectStandardError (Join-Path $closeOut "api-$Port-error.log")
$closeProc.Id | Set-Content -LiteralPath $closePidFile
for($closeAttempt=0;$closeAttempt -lt 50;$closeAttempt++) {
    try { $closePing=Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/ping" -TimeoutSec 1; if($closePing.StatusCode -eq 200){break} } catch {}
    if($closeProc.HasExited){throw 'API de teste encerrou durante inicialização.'}
    Start-Sleep -Milliseconds 300
}
if($closeAttempt -eq 50){throw 'Readiness da API isolada não concluída.'}
Write-Output "API isolada: $Port / $Database / PID $($closeProc.Id)"
