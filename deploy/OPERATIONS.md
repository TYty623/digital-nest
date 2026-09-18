# 生产运行手册

本手册仅适用于 `docker-compose.yml` 的 PostgreSQL + 本地上传卷部署。它不是备案、支付商户审核、对象存储或 TLS 证书的替代方案；这些外部条件完成前，不得把站点标记为正式商业上线。

## 启动前

1. 复制 `.env.example` 为 `.env`，使用至少 20 位的随机数据库密码与至少 32 位的访客指纹 HMAC 密钥，并将 `.env` 保留在服务器，不提交仓库。生产启动门禁会拒绝示例密码、非安全 Cookie、模拟支付和弱指纹密钥。
2. 在 HTTPS 反向代理之后运行，设置 `SESSION_COOKIE_SECURE=true`。反向代理必须把 `X-Forwarded-Proto` 传给 web 容器。
3. 真实支付未验签、验收前保持 `MOCK_PAYMENTS_ENABLED=false`；不要将本地 profile 或测试管理员邮箱带到生产。
4. 首次管理员初始化完成后，清空 `BOOTSTRAP_ADMIN_EMAILS`，仅通过数据库角色管理运营人员。

## 健康检查与发布

```powershell
docker compose --env-file .env -f docker-compose.yml up -d --build
Invoke-WebRequest http://127.0.0.1:8081/api/v1/health
docker compose --env-file .env -f docker-compose.yml ps
```

`postgres`、`server` 与 `web` 均会显示 Docker health 状态；`web` 会等待 `server` 的 Actuator 健康检查通过后才启动。部署后至少抽查：匿名访问公开页、口令页不能泄露标题或图片、登录后上传一张测试图片、管理员审核入口、数据导出。生产域名应使用 HTTPS，而非示例中的 `http://127.0.0.1:8081`。

## 备份

`backup.ps1` 会短暂停止应用容器的写入，将 PostgreSQL 自定义格式备份和 `uploads` 媒体压缩包写入同一个带时间戳目录；两份文件必须作为同一组保留。

```powershell
cd deploy
.\backup.ps1 -OutputDirectory D:\backups\digital-nest
```

把备份目录复制到与生产主机隔离的加密存储中，并记录保留期限。脚本会使用临时 `alpine:3.22` 容器归档上传卷；首次执行可能拉取该镜像。

## 恢复演练

恢复会先停止 web/server、覆盖数据库，再清空并还原上传媒体；成功后脚本会重新启动应用容器。因此必须只在隔离环境或经批准的维护窗口执行。若恢复中途失败，脚本会让 web/server 保持停止，避免半恢复状态对外服务。

```powershell
cd deploy
.\restore.ps1 -BackupDirectory D:\backups\digital-nest\digital-nest-YYYYMMDD-HHMMSS -ConfirmRestore
```

恢复完成后检查 `/api/v1/health`，随机打开已发布纪念页并确认图片、时间线和导出可用。然后记录恢复时长、发现的问题和最终结论。

## 仍需外部完成的上线条件

- 已备案的域名、HTTPS 证书与反向代理；
- 生产 PostgreSQL/上传卷的主机级快照与异地备份策略；
- 对象存储/CDN 迁移（若媒体规模或多实例部署需要）；
- 支付商户主体、回调验签、退款与客服渠道；
- 隐私政策、用户协议和退款规则的实际运营主体与法务复核；
- 种子用户测试、监控告警和回滚演练记录。
