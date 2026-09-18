# Oracle Always Free 上线执行手册

本项目采用单机生产部署：Vue、Spring Boot、PostgreSQL、私密媒体与 HTTPS 反向代理运行在同一台 Oracle Always Free Ubuntu ARM 虚拟机中。GitHub Actions 只负责验证和触发部署，不保存生产数据库密码或媒体。

## 0. 已完成的仓库准备

- `deploy/docker-compose.oracle.yml`：生产容器编排；Caddy 负责自动 HTTPS，应用保持同域访问。
- `deploy/Caddyfile`：TLS、压缩和最低限度安全响应头。
- `deploy/oracle-bootstrap.sh`：全新 Ubuntu 24.04 VM 的 Docker/Git 安装脚本。
- `deploy/oracle-deploy.sh`：只接受快进合并的远程部署脚本；失败时不会自动覆盖为旧版本。
- `.github/workflows/oracle-deploy.yml`：推送 `main` 后依次执行 Vue 构建/单测、Spring Boot 测试、SSH 部署和健康检查。

生产密钥只存在 Oracle 的 `deploy/.env` 和 GitHub Actions Secrets 中，绝不提交仓库。

## 1. 必须由账户持有人完成的 Oracle 注册

Oracle Cloud Free Tier 通常要求手机验证和信用卡/借记卡验证。不要点击升级到 Pay As You Go，也不要创建没有 `Always Free Eligible` 标签的资源。注册时选择今后长期使用的 Home Region；Always Free 计算和卷资源受 Home Region 限制。

建议创建：

- 映像：Ubuntu 24.04（aarch64/Arm）；
- 规格：`VM.Standard.A1.Flex`；总资源不超过 2 OCPU / 12GB RAM；
- 启动卷：200GB（会占用 Always Free 的总块存储额度，换取最简单的 Docker 数据持久化）；
- 公网 IPv4：启用；
- SSH：只上传自己的公开密钥，私钥不上传、不发给他人。

如果 Arm 规格提示无容量，先不要创建收费实例；等待、换可用区或在控制台重试。

## 2. 网络与域名

在 OCI Security List 或 Network Security Group 中只开放：

| 端口 | 来源 | 用途 |
| --- | --- | --- |
| 22/TCP | 仅团队固定公网 IP | SSH 维护与 GitHub Actions 部署。 |
| 80/TCP | 全网 | Caddy 首次 HTTPS 验证及 HTTP 跳转。 |
| 443/TCP、443/UDP | 全网 | 网站 HTTPS 与 HTTP/3。 |

不要暴露 5432、8080、8081。PostgreSQL 和 Spring Boot 只保留 Docker 内网访问。

为域名添加一条 A 记录，指向 Oracle 公网 IPv4。DNS 生效后才启动 Caddy；证书申请期间域名必须能从公网访问 80/443。

## 3. 初始化服务器

登录 Ubuntu 后运行：

```bash
git clone redacted-contact-1@example.com:OWNER/REPOSITORY.git ~/apps/digital-nest
cd ~/apps/digital-nest
bash ./deploy/oracle-bootstrap.sh
# 重新 SSH 登录后再继续
```

私有仓库需要为 Oracle 创建一把只读 GitHub Deploy Key，并将其公钥添加到仓库的 Deploy keys。不要将 GitHub 个人密码或 Personal Access Token 写入服务器。

创建生产环境变量：

```bash
cd ~/apps/digital-nest
cp deploy/.env.example deploy/.env
openssl rand -base64 36
openssl rand -base64 48
chmod 600 deploy/.env
```

将两个随机结果分别填入 `POSTGRES_PASSWORD` 和 `VISITOR_FINGERPRINT_SECRET`，填写真实 `SITE_DOMAIN`。首次管理员邮箱可暂时填在 `BOOTSTRAP_ADMIN_EMAILS`，管理员首次登录完成后清空该项并重新部署。

首次部署：

```bash
bash ./deploy/oracle-deploy.sh
docker compose --env-file deploy/.env -f deploy/docker-compose.oracle.yml ps
curl --fail https://YOUR_DOMAIN/api/v1/health
```

## 4. GitHub 自动部署

在 GitHub 仓库 Settings → Secrets and variables → Actions 中添加：

| Secret | 值 |
| --- | --- |
| `ORACLE_DEPLOY_SSH_KEY` | 专供 GitHub Actions 登录 Oracle 的私钥。 |
| `ORACLE_KNOWN_HOSTS` | 通过 `ssh-keyscan -H YOUR_ORACLE_IP` 获取并人工核对的主机指纹。 |
| `ORACLE_HOST` | Oracle 的公网 IP 或已生效域名。 |
| `ORACLE_USER` | Ubuntu SSH 用户名，通常为 `ubuntu`。 |
| `ORACLE_DEPLOY_PATH` | 例如 `/home/ubuntu/apps/digital-nest`。 |

将仓库的 `production` Environment 配置为需要人工批准，可阻止误推送立刻发布。确认后，`main` 的每次推送都会先测试再发布。

## 5. 备份、告警与恢复

Oracle 环境使用 `deploy/oracle-backup.sh` 创建 PostgreSQL dump 与 uploads 归档：

```bash
cd ~/apps/digital-nest
bash ./deploy/oracle-backup.sh
```

可先用 `crontab -e` 添加每日 03:20（UTC）的备份任务：

```cron
20 3 * * * cd /home/ubuntu/apps/digital-nest && BACKUP_ROOT=/home/ubuntu/backups bash ./deploy/oracle-backup.sh >> /home/ubuntu/backups/oracle-backup.log 2>&1
```

必须把生成目录中的 PostgreSQL dump 与 uploads 归档作为一组同步到实例外部。Oracle 同时提供有限的卷备份额度，但卷快照不是异地恢复演练的替代品；不要在没有外部副本的前提下自动删除旧备份。

上线后至少验证：公开页、口令页、上传图片、上传短视频、数据导出、删除后媒体下线、管理员审核、`/api/v1/health`。

## 6. 操作边界

- 域名购买、Oracle 注册、身份/手机/银行卡验证、SSH 私钥保管以及创建 GitHub Secret 必须由账户持有人完成。
- 不把真实密码、银行卡、身份证件、OTP、生产 `.env` 或用户上传媒体提交到 Git 仓库。
- 网站包含账户、私密媒体与社区能力；正式公开运营前仍须完成隐私、内容审核、支付、备案/地域和数据合规复核。
