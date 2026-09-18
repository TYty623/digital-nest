#!/usr/bin/env bash
# 在 Oracle VM 的项目根目录执行。此脚本不会重置工作目录或覆盖 deploy/.env。
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
branch="${DEPLOY_BRANCH:-main}"
compose_file="$project_root/deploy/docker-compose.oracle.yml"
environment_file="$project_root/deploy/.env"

if [[ ! -f "$environment_file" ]]; then
  echo "缺少 $environment_file。请从 deploy/.env.example 创建生产环境变量文件。" >&2
  exit 1
fi

cd "$project_root"
git fetch origin "$branch"
git merge --ff-only "origin/$branch"

docker compose --env-file "$environment_file" -f "$compose_file" build
docker compose --env-file "$environment_file" -f "$compose_file" up -d --remove-orphans

for attempt in {1..30}; do
  if docker compose --env-file "$environment_file" -f "$compose_file" exec -T server \
    curl --fail --silent http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo "部署完成：Java 健康检查已通过。"
    exit 0
  fi
  sleep 2
done

echo "容器已启动但 Java 健康检查未通过；未自动回滚，请检查 docker compose logs。" >&2
exit 1
