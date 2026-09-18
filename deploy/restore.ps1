[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BackupDirectory,
    [switch]$ConfirmRestore
)

$ErrorActionPreference = 'Stop'
if (-not $ConfirmRestore) {
    throw '恢复会覆盖当前生产数据库和上传媒体。确认无误后，请追加 -ConfirmRestore。'
}

$composeFile = Join-Path $PSScriptRoot 'docker-compose.yml'
$environmentFile = Join-Path $PSScriptRoot '.env'
$resolvedBackup = [System.IO.Path]::GetFullPath($BackupDirectory)
$databaseArchive = Join-Path $resolvedBackup 'database.dump'
$mediaArchive = Join-Path $resolvedBackup 'uploads.tar.gz'
$servicesStopped = $false
$restored = $false

foreach ($requiredFile in @($environmentFile, $databaseArchive, $mediaArchive)) {
    if (-not (Test-Path -LiteralPath $requiredFile)) {
        throw "缺少恢复所需文件：$requiredFile"
    }
}

$composeArguments = @('compose', '--project-directory', $PSScriptRoot, '--env-file', $environmentFile, '-f', $composeFile)
$databaseContainer = (& docker @composeArguments ps -q postgres).Trim()
$serverContainer = (& docker @composeArguments ps -q server).Trim()
if ([string]::IsNullOrWhiteSpace($databaseContainer) -or [string]::IsNullOrWhiteSpace($serverContainer)) {
    throw 'postgres 或 server 容器没有运行。请先完成 docker compose up -d。'
}

try {
    & docker @composeArguments stop web server
    if ($LASTEXITCODE -ne 0) { throw '无法停止 web/server 容器，已取消恢复以避免半恢复状态对外服务。' }
    $servicesStopped = $true

    & docker cp $databaseArchive "${databaseContainer}:/tmp/digital-nest.restore"
    if ($LASTEXITCODE -ne 0) { throw '无法复制 PostgreSQL 恢复文件。' }

    & docker @composeArguments exec -T postgres sh -c 'pg_restore --clean --if-exists --no-owner -U "$POSTGRES_USER" -d "$POSTGRES_DB" /tmp/digital-nest.restore'
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL 恢复失败。当前数据可能处于部分恢复状态，请停止服务并检查日志。' }

    & docker run --rm --volumes-from $serverContainer -v "${resolvedBackup}:/backup:ro" alpine:3.22 `
        sh -c 'find /data/uploads -mindepth 1 -maxdepth 1 -exec rm -rf {} + && tar -xzf /backup/uploads.tar.gz -C /data'
    if ($LASTEXITCODE -ne 0) { throw '上传媒体恢复失败。请停止服务并检查日志。' }

    $restored = $true
    Write-Host "恢复完成：$resolvedBackup"
} finally {
    if (-not [string]::IsNullOrWhiteSpace($databaseContainer)) {
        & docker @composeArguments exec -T postgres sh -c 'rm -f /tmp/digital-nest.restore' | Out-Null
    }
    if ($servicesStopped -and $restored) {
        & docker @composeArguments start server web | Out-Null
        Write-Host '服务已重新启动；请执行 health 检查并随机验证一份纪念页及其图片后，再恢复对外流量。'
    }
}
