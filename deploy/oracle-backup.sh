#!/usr/bin/env bash
# 为 Oracle 单机 Compose 部署创建可配对恢复的 PostgreSQL + uploads 备份。
# 备份会短暂停止 Java 容器写入。请将输出目录同步到实例外部后再视为有效备份。
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="$project_root/deploy/docker-compose.oracle.yml"
environment_file="$project_root/deploy/.env"
backup_root="${BACKUP_ROOT:-$HOME/backups/digital-nest}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="$backup_root/$timestamp"

if [[ ! -f "$environment_file" ]]; then
  echo "缺少 $environment_file。" >&2
  exit 1
fi

mkdir -p "$backup_dir"
cd "$project_root"

server_id="$(docker compose --env-file "$environment_file" -f "$compose_file" ps -q server)"
postgres_id="$(docker compose --env-file "$environment_file" -f "$compose_file" ps -q postgres)"
if [[ -z "$server_id" || -z "$postgres_id" ]]; then
  echo "server 或 postgres 容器没有运行，拒绝创建不完整备份。" >&2
  exit 1
fi

resume_server() {
  docker compose --env-file "$environment_file" -f "$compose_file" unpause server >/dev/null 2>&1 || true
}
trap resume_server EXIT

docker compose --env-file "$environment_file" -f "$compose_file" pause server
docker compose --env-file "$environment_file" -f "$compose_file" exec -T postgres \
  pg_dump -U "$(awk -F= '$1 == "POSTGRES_USER" { print substr($0, index($0, "=") + 1); exit }' "$environment_file")" \
  -Fc -f /tmp/digital-nest.dump \
  "$(awk -F= '$1 == "POSTGRES_DB" { print substr($0, index($0, "=") + 1); exit }' "$environment_file")"
docker cp "$postgres_id:/tmp/digital-nest.dump" "$backup_dir/database.dump"
docker run --rm --volumes-from "$server_id" -v "$backup_dir:/backup" alpine:3.22 \
  tar -czf /backup/uploads.tar.gz -C /data uploads
docker compose --env-file "$environment_file" -f "$compose_file" exec -T postgres rm -f /tmp/digital-nest.dump

cat > "$backup_dir/manifest.json" <<EOF
{"createdAt":"$timestamp","database":"database.dump","media":"uploads.tar.gz"}
EOF

echo "备份已创建：$backup_dir"
echo "请立即将整个目录同步至实例外部；本机备份不构成灾难恢复。"
