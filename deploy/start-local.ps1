param([string]$BootstrapAdminEmail = '')
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$logDirectory = Join-Path $projectRoot 'deploy/data/logs'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
Get-Command java, mvn.cmd, npm.cmd -ErrorAction Stop | Out-Null
function Test-LocalPort([int]$Port) {
    $connection = [System.Net.Sockets.TcpClient]::new()
    try { return $connection.ConnectAsync('127.0.0.1', $Port).Wait(700) -and $connection.Connected }
    catch { return $false }
    finally { $connection.Dispose() }
}
if (-not (Test-Path (Join-Path $projectRoot 'web/node_modules'))) {
    throw 'Install frontend dependencies first: cd web; npm ci'
}
$backend = Test-LocalPort 8080
if (-not $backend) {
    $previousAdmin = $env:BOOTSTRAP_ADMIN_EMAILS
    try {
        $env:BOOTSTRAP_ADMIN_EMAILS = $BootstrapAdminEmail
        Start-Process -FilePath 'mvn.cmd' -ArgumentList @(
            ('"-Dmaven.repo.local=' + (Join-Path $projectRoot '.m2') + '"'),
            '-Dspring-boot.run.useTestClasspath=true',
            '-Dspring-boot.run.profiles=local',
            '-Dspring-boot.run.arguments=--server.address=127.0.0.1',
            'spring-boot:run'
        ) -WorkingDirectory (Join-Path $projectRoot 'server') -WindowStyle Hidden `
          -RedirectStandardOutput (Join-Path $logDirectory 'server.log') `
          -RedirectStandardError (Join-Path $logDirectory 'server-error.log') | Out-Null
    } finally { $env:BOOTSTRAP_ADMIN_EMAILS = $previousAdmin }
} else { Write-Host 'Port 8080 is already in use; existing process left untouched.' }
if (-not (Test-LocalPort 5173)) {
    Start-Process -FilePath 'npm.cmd' -ArgumentList 'run','dev','--','--host','127.0.0.1','--strictPort' `
      -WorkingDirectory (Join-Path $projectRoot 'web') -WindowStyle Hidden `
      -RedirectStandardOutput (Join-Path $logDirectory 'web.log') `
      -RedirectStandardError (Join-Path $logDirectory 'web-error.log') | Out-Null
} else { Write-Host 'Port 5173 is already in use; existing process left untouched.' }
Write-Host 'Startup requested. Open http://127.0.0.1:5173/health after Java finishes starting.'
Write-Host 'Local database and uploads are persisted in server/data. Local mock payment is not real payment.'
