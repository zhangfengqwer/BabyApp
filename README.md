# 之之成长手册（Baby Home）

Baby Home 是一个完全自托管的家庭成长相册。Android App 和 iPhone PWA 可通过家庭局域网或 Tailscale 访问 Windows 电脑；业务数据保存在 PostgreSQL，照片和视频由现有 Immich 实例管理。

## 当前版本与功能

截至 2026-09-25，Android 已发布 **0.4.37（versionCode 38）**；iPhone 网页版界面版本为 **0.4.36**，由 Baby Backend 提供并通过 Service Worker 更新。两端启动加载页为白底、底部小屋图标与“时光小屋”字样；App 安装名称仍为“之之成长手册”。

| 功能 | Android App | iPhone PWA |
| --- | --- | --- |
| 家庭身份与宝宝 | 首次选择并记住身份；查看/管理家庭成员、编辑宝宝名片与固定头像 | 同步身份、成员和名片功能 |
| 浏览 | 时光轴、日历、年龄索引、详情、点赞与留言、整组照片/视频浏览和照片缩放 | 同步主要浏览与编辑功能，包括更早留言、封面设置和媒体缩放 |
| 发布 | 点击发布先进入应用内相册；按文件夹与拍摄日期查看，轻点或长按滑动多选，可按日期全选 | 点击发布打开 iOS 系统相册；选完后可在网页预览网格中勾选、按日期全选 |
| 日期归档 | 默认按拍摄日期分组，同一天追加到已有记录；无法识别时使用手选日期 | 同步归档逻辑；JPEG EXIF 和 MOV/MP4 时间可识别，其他未知时间使用手选日期 |
| 删除成长记录媒体 | 详情长按进入多选，可全选和批量删除；删除整条记录也处理其媒体 | 同步多选、全选和删除确认，调用相同后端接口 |
| 发布后删除本地文件 | 所有日期发布成功后请求 Android 系统确认删除；“我的”可重试清理之前未删的文件 | 浏览器无法删除 iOS“照片”图库原件，需在“照片”中手动删除 |
| 连接与更新 | 服务器地址设置、连接测试；登录连接时检查 `versionCode`，下载并校验新 APK 后交由系统安装 | 显示/切换网页服务器地址并测试连接；新版页面与图标通过 Service Worker 缓存版本更新 |

“未上传”筛选只依据本机/当前浏览器记录的发布历史，不代表对 Immich 全库的核对。Android 使用自己的相册网格时，可以请求删除手机本地媒体；系统 Photo Picker 给出的只读 URI 仍可能需要手动删除。iPhone 网页不能遍历完整设备相册，系统选片界面的滑选手势由 iOS 决定。

删除详情中的照片、视频或整条记录时，后端先检查原件是否仍被其他记录、头像或里程碑使用；有引用就拒绝删除。没有共用引用时，将 Immich 原件移入回收站，再删除 Baby Home 的关联或记录。**回收站保留期由 Immich 设置决定**；这与 Android 发布成功后删除手机本地副本是两件事。

Android 构建与 13 项单元测试、后端 29 项测试、网页日期逻辑 4 项测试通过；服务端 `/health` 和自动更新 APK 的 SHA-256 已验证。Android 滑动多选与本地删除、iPhone Safari/VoiceOver 仍需实机验收。发布与宝宝名片的较早设计说明见[发布与名片编辑](docs/publish-profile.md)。

家庭 Wi-Fi 模式使用 App 内置的私有访问密钥自动换取 JWT。`HOME_ACCESS_KEY` 仅保存在未提交的根目录 `.env` 和 `apps/android/local.properties`，并会编译进私人 APK；不要把 APK 发送给家庭之外的人。若 APK 泄露，应更换两处密钥并重新部署、构建。

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
  web/           iPhone Safari/PWA 页面与 Service Worker
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

当前版本化安装包为 `releases/zhizhi-growth-0.4.37-debug.apk`。0.4.37 将选中的照片和视频转换为 Android 删除接口要求的媒体 URI；在 0.4.36 发布成功但未删掉本地文件的用户，可更新后从“我的 → 重试删除已发布的本地文件”重新请求系统删除。该入口仅处理仍能在本机媒体库定位的文件，删除前会展示文件名并再次要求确认。Android 系统拒绝或用户取消时不会删除本地原件。

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

iPhone 版无需 Mac、IPA 或 Apple 开发者账号。网页随 Baby Backend 一起部署，包含时光轴、日历、年龄索引、宝宝资料、动态详情、整组媒体浏览、照片缩放、点赞、留言、编辑/选择封面、批量选择与删除，以及按拍摄日期归档的照片/视频发布。网页启动页与 Android 一样使用白底“时光小屋”字样。选片必须通过 iOS 系统相册；网页只能预览和调整已选文件，不能遍历手机相册或删除 iOS“照片”图库中的原件。

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

本项目不把 Tailscale 强制装进容器；需要公网访问时按[公网访问说明](docs/public-access.md)通过 Nginx 代理，不直接开放后端和 Immich 端口。

## 数据与备份

PostgreSQL 只保存用户、宝宝、成长动态和 Immich Asset ID，不保存图片二进制。原图、视频、缩略图、EXIF 和转码由 Immich 管理。删除成长记录或其中的媒体时，后端会在权限与共用引用检查通过后先把原件移入 Immich 回收站，再删除 Baby Home 数据；Immich 按回收站设置完成后续清理。发布成功后删除 Android 手机本地副本是独立流程，不会影响服务器原件。

> **Immich 存储不等于备份。** 建议至少使用“主照片盘 + 第二块独立备份盘”，并同时备份 PostgreSQL。Phase 1 不包含自动备份功能。

## 安全说明

- 敏感配置只放在未提交的 `.env`。
- App 支持家庭 Tailscale 网络上的 HTTP；不要把 8080 或 2283 暴露到公网。
- Immich API Key 只保存在 Backend `.env`，目前仅授予媒体上传、读取、浏览、下载与删除权限；后端在授权用户明确确认删除且媒体无共用引用时才使用删除权限。Android 经 Baby Backend 流式网关上传和读取媒体，Backend 不落盘、不复制原文件。
- Release 构建不输出网络请求日志；认证头和 API Key 请求头会脱敏。
- JWT Access/Refresh Token 已实现；Android Token 使用 EncryptedSharedPreferences。细粒度角色权限继续在 Phase 6 完成。
- Android 遇到 Access Token 过期时会自动使用单次轮换 Refresh Token 换新并重放原请求；Refresh 失效后才要求重新登录。
