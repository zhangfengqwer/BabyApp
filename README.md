# 之之成长手册（Baby Home）

Baby Home 是一个完全自托管的家庭成长相册。Android App 和 iPhone PWA 可通过家庭局域网或 Tailscale 访问 Windows 电脑；业务数据保存在 PostgreSQL，照片和视频由现有 Immich 实例管理。

## 当前进度

Android 0.4.32 / versionCode 33 已构建测试通过并发布：详情页媒体长按后在右上角显示红色圆形减号，点击仍需确认；普通点击浏览不受影响，只移除业务关联，不删除 Immich 原文件。

Android 0.4.30 / versionCode 31 已发布：首次选择家庭身份，之后设备记住用户名并免密码进入；我的 → 家庭成员可查看关系，管理员可添加家人和编辑关系。时光轴/详情显示当天参与发布人的关系，评论显示真实评论账号的关系。按用户要求，现有管理员关系已设置为爸爸，用户名和 ADMIN 权限保留。此免密码选择不防冒充，只适合可信家庭设备。Android 构建/测试通过，后端 24 项测试通过，详见 [家庭成员](docs/family-members.md)。

2026-09-16：Android 0.4.29 / versionCode 30 已成功构建并发布，13 项 Android 单元测试通过。发布页增加本地视频帧预览；1904 年 QuickTime 零时间和其他无效媒体时间按“拍摄时间不明”处理，不覆盖手选备用日期。详情页支持独立移除照片/视频，后端限制管理权限且只删除 MomentAsset 关联，不删除 Immich 原文件。配套后端编译及 21 项测试通过。

Android 0.4.28 加入重新设计的发布页和宝宝名片编辑页：媒体默认按各自拍摄日期分组发布，同一天继续追加到同一个 Moment；可关闭自动分组统一使用手选日期。未知拍摄时间的媒体使用手选日期，发布前可查看分组，部分日期成功后重试只继续剩余日期。宝宝姓名、昵称、生日、简介和固定头像可由管理员编辑。2026-09-16 已通过 Android APK 编译及 10 项单元测试，0.4.28 / versionCode 29 已发布至自动更新目录；后端编译与 19 项测试通过。详见 [发布与名片编辑](docs/publish-profile.md)。

0.4.10 将包含：安装名称“之之成长手册”、局域网自动认证、原创启动图标、日历直达详情、详情媒体整组左右滑动、发布/删除后主页强制刷新、单次选择超过 10 个媒体、滚到底自动分页，以及重新设计的时光轴界面。顶部标题和页签会随内容滚动收起，宝宝名片使用干净的中性色卡片，底部导航使用新的图标与选中样式。宝宝头像只读取宝宝资料中的固定头像，不再使用最新动态照片；刚上传媒体的缩略图未就绪时会自动重试。

已加入原创“黄色小屋＋宝宝笑脸＋相册页”启动图标，设计源文件位于 `apps/android/design/app-icon-source.png`，APK 使用各 Android 屏幕密度对应的 mipmap 图标。

家庭 Wi-Fi 模式使用 App 内置的本机私有访问密钥自动换取 JWT，启动后不显示地址、用户名或密码输入页。`HOME_ACCESS_KEY` 仅保存在未提交的根目录 `.env` 和 `apps/android/local.properties`；它会被编译进私人 APK，因此不要把 APK 发送给家庭之外的人。若 APK 泄露，应同时更换两处密钥并重新部署、构建。

Windows 防火墙当前向家庭局域网放行 TCP 8080（Baby API）和 2283（Immich）。公网部署时只通过 Nginx 开放 TCP 80/443，完整步骤见[公网访问](docs/public-access.md)。

UI 0.4.6：时光轴 / 日历双视图、年龄索引、动态详情和整组媒体浏览，见 [页面跳转说明](docs/navigation.md)。配套后端已部署，14 项后端测试通过，健康检查确认 API、数据库与 Immich 正常。手机交互验收待完成。完整范围与未完成项目见 [家庭相册实施范围](docs/family-album-scope.md)。

Phase 1 已包含：

- Android Kotlin + Jetpack Compose + Material 3 工程
- 可修改 Baby Server URL / Immich URL 的服务器设置页
- `/health` 连接测试和面向普通用户的中文错误提示
- NestJS 后端和统一健康检查响应
- PostgreSQL + Prisma 核心业务 Schema 与初始 migration
- Windows Docker Desktop 可用的 Docker Compose

已验证：NestJS 编译与测试、Prisma migration、Docker 镜像、临时 PostgreSQL `/health` 端到端检查，以及 Android `assembleDebug` / `testDebugUnitTest` 均通过。

已实现登录、JWT Access/Refresh Token、宝宝信息、Android Photo Picker、图片/视频上传进度、失败重试和 MomentAsset 关联，并已通过真实 Immich 上传联调。当前范围收敛为家庭相册：底部仅“时光轴 / 我的”，发布使用悬浮按钮；支持手动加载更早动态、照片缩放和 Media3 视频播放，不再保留独立身高体重 Tab。

## 架构

```text
Android Baby App
  │  http://192.168.0.40:8080
  │  家庭有线网络 / Wi-Fi
  ▼
Windows 家庭服务器
  ├─ Baby Backend (Docker, :8080)
  ├─ PostgreSQL (Docker，仅内部网络)
  └─ Immich (已有实例, :2283)
       └─ 本地照片硬盘
```

目录结构：

```text
apps/
  android/       Android 原生应用
  backend/       NestJS + Prisma 后端
docker/          Docker Compose
docs/            架构、API、部署文档
```

详细说明见 [架构文档](docs/architecture.md) 和 [API 文档](docs/api.md)。

## Windows 开发环境

需要安装：

- Git
- Node.js 22 LTS 或更高版本
- JDK 17 LTS（推荐 Eclipse Temurin 17 或 Microsoft Build of OpenJDK 17）
- Android SDK 35（建议安装最新版稳定 Android Studio）
- Docker Desktop for Windows，启用 WSL 2 后端和 Docker Compose
- Windows 与 Android 上的 Tailscale

首次拉取后安装后端依赖：

```powershell
npm install
```

后端编译和测试：

```powershell
npm run backend:build
npm run backend:test
```

## 配置与 Docker 启动

1. 复制根目录 `.env.example` 为 `.env`。
2. 修改 `POSTGRES_PASSWORD` 与 `DATABASE_URL`，两处密码必须一致。
3. 将 `IMMICH_BASE_URL` 指向 Windows 上已有的 Immich，Docker Desktop 通常使用 `http://host.docker.internal:2283`。
4. 填写 Immich API Key。不要提交 `.env`。
5. 启动服务：

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up -d --build
```

### Immich（独立 Compose）

本仓库保留 Immich 官方 release Compose 于 `docker/immich/docker-compose.yml`，不会在 Baby Backend Compose 中创建第二套。当前媒体目录配置在被忽略的 `docker/immich/.env` 中：

```text
UPLOAD_LOCATION=E:/ImmichLibrary
DB_DATA_LOCATION=E:/ImmichLibrary/postgres
IMMICH_VERSION=v3
```

启动或升级：

```powershell
docker compose -f docker/immich/docker-compose.yml pull
docker compose -f docker/immich/docker-compose.yml up -d
```

访问地址为 `http://localhost:2283`；当前 Tailscale 手机端使用 `http://desktop-rcsbtks:2283`。Immich 管理员凭据保存在未提交的 `docker/immich/.env`，初始化入口已通过 `IMMICH_ALLOW_SETUP=false` 关闭。

首次启动后创建管理员、宝宝资料和一条测试动态：

```powershell
docker compose --env-file .env -f docker/docker-compose.yml exec backend npm --workspace apps/backend run prisma:seed
```

执行前必须在 `.env` 修改 `INITIAL_ADMIN_PASSWORD`，至少 10 位且不能使用示例值。`INITIAL_BABY_NAME` 和 `INITIAL_BABY_BIRTHDAY` 可按实际情况修改。

检查状态：

```powershell
docker compose --env-file .env -f docker/docker-compose.yml ps
curl.exe http://localhost:8080/health
```

停止服务（保留数据库）：

```powershell
docker compose --env-file .env -f docker/docker-compose.yml down
```

不要执行 `down -v`，除非明确要删除 PostgreSQL 数据卷。完整部署细节见 [部署文档](docs/deployment.md)。

## Android 构建与安装

项目使用 Gradle Wrapper，不需要全局安装 Gradle。在 Windows PowerShell 中：

```powershell
cd apps/android
.\gradlew.bat testDebugUnitTest assembleDebug
```

也可以从仓库根目录运行带日志记录的辅助脚本：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-android-windows.ps1
```

日志写入根目录 `android-build.log`，退出码写入 `android-build-exit.txt`，两者均不会提交到版本控制。构建成功后，脚本还会把最新版复制为 `releases/zhizhi-growth-latest.apk` 并生成带 SHA-256 的 `releases/version.json`。App 登录家庭服务器后会自动检查更高版本，下载校验后调用 Android 系统安装器。

若 Windows JDK 报 `Unable to establish loopback connection` 且栈包含 `UnixDomainSockets.connect`，优先使用上述辅助脚本。脚本为所有 Java 子进程配置项目专用 `.gradle-sockets` 目录（`jdk.net.unixdomain.tmpdir`），避免系统 Temp 下的 Unix-domain socket 兼容性故障。2026-09-16 已通过 TCP/NIO Pipe/Selector 最小测试及完整 Gradle 构建验证，不需要关闭防火墙、重装 Docker 或清空 Gradle 缓存。

Debug APK 输出位置：

```text
apps/android/app/build/outputs/apk/debug/app-debug.apk
```

## 按日期导入旧相册

对于文件名含日期的旧照片，例如 `10个月_2023-07-01_1788581786105.jpeg`，可以让脚本按日期分组上传到 Immich，并为当天创建一条成长动态。同一天不限 10 张照片或视频。

```powershell
# 先预览分组，不会上传、移动或删除文件
.\scripts\import-legacy-album.ps1

# 导入；原始下载目录仍会保留
.\scripts\import-legacy-album.ps1 -Import

# 确认迁移完成后，才在每一天成功导入时移走该天的源文件
.\scripts\import-legacy-album.ps1 -Import -MoveSourceAfterSuccess
```

脚本在 `scripts/.state/zhang-zhiyi-import-journal.csv` 写入断点记录。网络中断后，重复执行 `-Import` 会继续未完成的文件，不会重复上传已记录的文件。

把 APK 发送到 Android 手机，允许当前文件管理器安装未知来源应用，然后打开 APK 安装即可，不需要 Google Play。

App 首次打开服务器设置页，推荐填写：

```text
Baby Server URL: http://192.168.0.40:8080
Immich URL:      http://192.168.0.40:2283
```

地址保存在 App 本地，可随时修改，不需要重新编译。App 在 `192.168.0.*` 家庭 Wi-Fi 中自动使用局域网地址；其他网络自动使用 Tailscale MagicDNS，不需要手动切换。

## iPhone PWA

iPhone 版无需 Mac、IPA 或 Apple 开发者账号。网页随 Baby Backend 一起部署，包含时光轴、日历、动态详情、整组媒体浏览、点赞、留言、删除和照片/视频发布。

部署最新版：

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up -d --build backend
```

在家连接家庭 Wi-Fi 后，用 iPhone Safari 打开：

```text
http://192.168.0.40:8080/web/
```

点击 Safari 底部“分享”→“添加到主屏幕”。外出时先连接 Tailscale，再打开：

```text
http://desktop-rcsbtks:8080/web/
```

PWA 使用服务器端家庭网络认证，不在网页源码中保存 `HOME_ACCESS_KEY`。该入口仅应在家庭局域网和 Tailscale 内使用，禁止把 8080 暴露到公网。

## Tailscale

1. 在 Windows 主机和每部 Android 手机上安装 Tailscale，并加入同一 tailnet。
2. 当前 Windows 主机的 MagicDNS 短名称为 `desktop-rcsbtks`；如果以后在 Tailscale 中改名，需要更新 App 中的外出访问配置后重新构建。
3. 在 Tailscale 管理页启用 MagicDNS。
4. 在家连接家庭 Wi-Fi 时不需要打开 Tailscale；App 会直连 `192.168.0.40`，上传和浏览速度更快。
5. 外出时先打开 Tailscale，再打开 Baby App；App 自动改用 `desktop-rcsbtks`，无需改地址或重新登录。
6. Windows 防火墙只需允许 Tailscale 网络上的 8080/2283 入站访问；不要做路由器公网端口转发。

本项目不把 Tailscale 强制装进容器，不使用 Cloudflare Tunnel、Caddy 或公网反向代理。

## 数据与备份

PostgreSQL 只保存用户、宝宝、成长动态和 Immich Asset ID，不保存图片二进制。原图、视频、缩略图、EXIF 和转码由 Immich 管理；删除 Moment 默认不删除 Immich 原文件。

> **Immich 存储不等于备份。** 建议至少使用“主照片盘 + 第二块独立备份盘”，并同时备份 PostgreSQL。Phase 1 不包含自动备份功能。

## 安全说明

- 敏感配置只放在未提交的 `.env`。
- App 支持家庭 Tailscale 网络上的 HTTP；不要把 8080 或 2283 暴露到公网。
- Immich API Key 只保存在 Backend `.env`。Android 经 Baby Backend 流式网关上传和读取媒体，Backend 不落盘、不复制原文件。
- Release 构建不输出网络请求日志；认证头和 API Key 请求头会脱敏。
- JWT Access/Refresh Token 已实现；Android Token 使用 EncryptedSharedPreferences。细粒度角色权限继续在 Phase 6 完成。
- Android 遇到 Access Token 过期时会自动使用单次轮换 Refresh Token 换新并重放原请求；Refresh 失效后才要求重新登录。
