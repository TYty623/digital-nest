# 数字小窝

> 为宠物建立私密、可分享、可导出的数字纪念空间。

本仓库采用 Vue 3 前端与 Java/Spring Boot 后端的前后端分离架构。产品和执行范围见：

- [项目确认文档](./项目一-宠物数字纪念网站-项目确认文档.md)
- [全面设计与执行方案](./项目一-全面设计与执行方案.md)
- [当前执行状态与上线边界](./docs/EXECUTION-STATUS.md)

## 目录

```text
web/       Vue 3 + TypeScript 前端
server/    Spring Boot 后端
deploy/    本地与生产部署配置
docs/      API、架构决策和运行手册
```

## 当前进度

当前可用闭环包括：账户注册/登录、错误密码限流、受控图片与短视频上传（大小、类型与文件签名均由服务端校验；短视频限 MP4／WebM、30MB，且支持进度拖动但不自动播放）、私密草稿与离开未保存内容提醒、可选的陪伴日期、关于 TA 的性格与日常、可关联相册图片并手动排序的时间线、写给 TA 的信、可加说明并排序的照片/短视频相册、暖阳／星夜／花园三套可保存的页面主题、三个明确标注为演示内容的完整样板页、四档分享范围（仅自己／持链接／口令／公开）、已发布页面的一键复制分享链接与下载 PNG 分享卡片、由 Java 动态生成的分享标题和 Open Graph 元数据、亲友留言与主人隐藏、每日一次的访客点亮、ZIP 资料导出，以及可撤销的账号删除请求。已发布页面切换为仅自己可见时会立即下线；相册媒体被移除后会立刻从页面和受控读取接口中撤下，文件则在 24 小时后清理。导出包含结构化 JSON、可阅读的 Markdown、相册顺序/媒体说明、含状态的全部亲友留言、原始媒体清单、图片与短视频文件，并通过哈希保存的短时一次性下载令牌交付。删除请求提交后，公开页面和媒体会立刻下线；用户可在 7 天内取消，逾期后由后台清理账号与内容。套餐有服务端锁价的订单、重复回调只发放一次权益的本地模拟支付；运营角色可在本地环境填写原因后撤销模拟订单，订单与权益会同步更新并保留审计记录。¥699 起的人工定制服务已具备不扣款申请、运营工单、两轮修改范围提示和用户确认交付的流程；真实支付开放前不会在站内扣款。套餐权益会由后端校验图片、短视频和时间线容量，公开页的留言、点亮、举报和口令尝试均有访问频率限制，媒体不会因知道地址而越权读取。除了 PostgreSQL Compose 路径，仓库还提供显式 `local` H2 开发配置，便于没有 Docker 的本机联通验收。

管理台同时展示当日与近 7 日的匿名访问、近 7 日新建和发布小窝、主人主动分享次数、近 30 日已支付订单。访问只按“纪念页 + HMAC-SHA-256 匿名指纹 + 当日”去重，不保存原始 IP；分享只记录主人成功复制链接或下载卡片的事件类型与时间；生产环境需要设置 `VISITOR_FINGERPRINT_SECRET`。

真实微信支付、短信验证码、对象存储迁移、平台级审核与备案仍需按执行方案接入相应的合规账号和生产环境配置。

## 调整前端设计

当前正式站采用已确认的 B「星夜来信」，不再是静态提案。详细改版入口、验证记录及剩余上线条件见 [B 版设计与验收](./docs/B-星夜来信-设计与验收.md)。

- 改配色、字体、焦点色和阴影：编辑 `web/src/assets/design-tokens.css`；
- 改 B 版布局、组件表面和三套纪念主题适配：编辑 `web/src/assets/night-theme.css`；
- 改站名、导航、页脚和定制服务状态文案：编辑 `web/src/config/site.ts`；
- 改套餐卡的主文案与排序：编辑 `web/src/config/pricing.ts`；价格、容量和托管期限由 `server/src/main/java/com/digitalnest/petmemorial/billing/PlanCatalog.java` 统一锁定，避免显示价与实际结算不一致。

页面组件保留交互和布局，避免把常改内容与业务流程耦合在一起。

## 本地运行目标

运行 PostgreSQL 版本前，请确认 Docker Desktop 已经启动。完成依赖安装后：

```powershell
docker compose -f deploy/docker-compose.local.yml up -d
cd server
mvn "-Dmaven.repo.local=..\\.m2" spring-boot:run
cd ..\web
npm run dev
```

前端开发服务器为 `http://localhost:5173`，后端健康检查为 `http://localhost:8080/api/v1/health`；在浏览器打开 `http://localhost:5173/health` 可确认前后端已连通。

没有启动 Docker Desktop 时，也可以使用仅供本机联调的文件数据库配置。账户与小窝保存在 `server/data/local-db`，上传保存在 `server/data/local-uploads`，重启后仍保留；默认启用模拟支付，不能用于生产。

在仓库根目录可一键启动前后端（后台运行，仅本机访问；已占用端口不会被强制关闭）：

```powershell
powershell -ExecutionPolicy Bypass -File deploy/start-local.ps1
```

启动后访问 `http://127.0.0.1:5173`。日志在 `deploy/data/logs`。也可以手动启动：

```powershell
cd server
mvn "-Dmaven.repo.local=..\\.m2" "-Dspring-boot.run.useTestClasspath=true" "-Dspring-boot.run.profiles=local" spring-boot:run
cd ..\\web
npm run dev
```

首次开通运营管理台时，在启动 Java 服务前为明确的运营邮箱设置 `BOOTSTRAP_ADMIN_EMAILS`。该邮箱首次注册或下次登录时会被授予 `ADMIN` 角色；完成初始化后应清空该变量，避免无意扩大管理员范围：

```powershell
$env:BOOTSTRAP_ADMIN_EMAILS = 'operator@example.com'
cd server
mvn "-Dmaven.repo.local=..\\.m2" "-Dspring-boot.run.useTestClasspath=true" "-Dspring-boot.run.profiles=local" spring-boot:run
```

管理员登录后可访问 `/admin`，巡查公开纪念页并在填写原因后下线、审核待处理/被举报留言、查看匿名访问、发布/分享增长与受控媒体总用量、订单概览、人工定制工单及带原因的处置审计记录。公开页巡查只显示必要的页面和昵称信息。生产环境必须在 HTTPS 反向代理后保持 `SESSION_COOKIE_SECURE=true`。

首次运行后可按以下路径验证：注册账户 → 创建小窝并上传图片 → 在“我的小窝”中进入整理台 → 添加时间线和信件 → 选择分享范围并发布 → 打开分享链接、留言或输入访问口令；也可从“定制服务申请”提交需求，查看运营进度，并在交付后确认完成。页面主人可随时暂时下线并恢复原分享链接。

在 Java 本地服务与 Vite 都启动后，执行以下命令可运行注册、上传、发布、访客留言的完整浏览器验收：

```powershell
    cd web
    npx playwright install chromium
    $env:E2E_FULL_STACK = 'true'
    npm run test:e2e -- --project=chromium

    # 如果 Chromium 已由企业镜像或本机安装，可跳过 Playwright 的浏览器下载：
    $env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH = 'C:\path\to\chrome.exe'
    npm run test:e2e -- --project=chromium
```

本地验证订单闭环时，在启动后端前显式设置模拟支付开关：

```powershell
$env:MOCK_PAYMENTS_ENABLED = 'true'
mvn "-Dmaven.repo.local=..\\.m2" spring-boot:run
```

然后登录后打开“我的小窝 → 订单与套餐权益”。该开关默认关闭，生产环境不得开启。

## 生产部署

1. 复制 `deploy/.env.example` 为 `deploy/.env`，将数据库密码换成高强度随机值。
2. 在仓库根目录运行 `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`。
3. 访问 `http://服务器地址:8081`。Vue 页面、Java API 与图片资源会通过同一个域名访问，避免跨域会话问题。

容器启动后，Flyway 会自动执行版本化迁移。上传文件与 PostgreSQL 数据均使用具名卷保存。

生产发布、备份和恢复演练步骤见 [`deploy/OPERATIONS.md`](./deploy/OPERATIONS.md)。真实支付、对象存储、备案、TLS 和法务主体仍需在相应外部账号与正式环境中完成，不能用本地模拟配置替代。
