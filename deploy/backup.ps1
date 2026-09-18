[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'backups')
)

$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot 'docker-compose.yml'
$environmentFile = Join-Path $PSScriptRoot '.env'

if (-not (Test-Path -LiteralPath $environmentFile)) {
    throw "未找到 $environmentFile。请先从 .env.example 创建生产环境配置。"
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDirectory = Join-Path $resolvedOutput "digital-nest-$timestamp"
New-Item -ItemType Directory -Force -Path $backupDirectory | Out-Null

$composeArguments = @('compose', '--project-directory', $PSScriptRoot, '--env-file', $environmentFile, '-f', $composeFile)
$databaseContainer = (& docker @composeArguments ps -q postgres).Trim()
$serverContainer = (& docker @composeArguments ps -q server).Trim()
if ([string]::IsNullOrWhiteSpace($databaseContainer) -or [string]::IsNullOrWhiteSpace($serverContainer)) {
    throw 'postgres 或 server 容器没有运行。请先完成 docker compose up -d。'
}

$databaseArchive = Join-Path $backupDirectory 'database.dump'
$mediaArchive = Join-Path $backupDirectory 'uploads.tar.gz'
$serverPaused = $false

try {
    & docker @composeArguments pause server
    if ($LASTEXITCODE -ne 0) { throw '无法暂停 server 容器，已取消备份以避免数据库与媒体不一致。' }
    $serverPaused = $true

    & docker @composeArguments exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc -f /tmp/digital-nest.dump "$POSTGRES_DB"'
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL 备份失败。' }

    & docker cp "${databaseContainer}:/tmp/digital-nest.dump" $databaseArchive
    if ($LASTEXITCODE -ne 0) { throw '无法复制 PostgreSQL 备份文件。' }

    & docker run --rm --volumes-from $serverContainer -v "${backupDirectory}:/backup" alpine:3.22 `
        tar -czf /backup/uploads.tar.gz -C /data uploads
    if ($LASTEXITCODE -ne 0) { throw '上传媒体备份失败。' }

    [ordered]@{
        createdAt = (Get-Date).ToUniversalTime().ToString('o')
        database = 'database.dump'
        media = 'uploads.tar.gz'
        note = '数据库与媒体必须作为同一组备份保留和恢复。'
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backupDirectory 'manifest.json') -Encoding utf8

    Write-Host "备份完成：$backupDirectory"
} finally {
    if (-not [string]::IsNullOrWhiteSpace($databaseContainer)) {
        & docker @composeArguments exec -T postgres sh -c 'rm -f /tmp/digital-nest.dump' | Out-Null
    }
    if ($serverPaused) {
        & docker @composeArguments unpause server | Out-Null
    }
}
