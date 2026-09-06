# 架构说明

## 边界

Baby Backend 负责家庭、宝宝、动态、互动、成长记录和里程碑等业务；Immich 是唯一媒体基础设施。后端只引用 `immichAssetId`，不复制媒体文件，也不重新实现缩略图、EXIF、视频转码和播放能力。为避免把服务器级 Immich API Key 下发到手机，Backend 提供鉴权流式网关：请求体和响应体直接在 Android 与 Immich 间转发，不写入本地文件。

## Android 分层

```text
ui/settings
  └─ ServerSettingsViewModel
       └─ domain/settings/ServerSettingsRepository
            ├─ data/settings/ServerConfigStore (DataStore)
            └─ data/network/HealthApi (Retrofit/OkHttp)
```

Phase 2 已在相同边界内增加认证、宝宝和首页；Room 最近内容缓存仍将在后续阶段补齐。UI 不直接拼接 Immich API URL。

Phase 3 的 Android `data/immich` 包包含 `ImmichApi`、`ImmichRepository`、`ImmichAuthManager` 与 `ImmichAssetService`。UI 只接收已封装的缩略图 URL，不持有 Immich API Key。

Phase 4 增加 `TimelineRepository` 与 `TimelineViewModel`，页面只持有 cursor 和 UI 状态。照片浏览继续使用带 Baby JWT 的 Coil 全局加载器；视频播放使用 Media3/ExoPlayer，通过鉴权请求和 HTTP Range 从 Backend 流式网关读取 Immich 转码视频。

## 后端模块

```text
AppModule
  ├─ PrismaModule
  └─ HealthModule
       ├─ PostgreSQL SELECT 1
       └─ Immich /api/server/ping
```

所有数据库日期时间使用 PostgreSQL `TIMESTAMPTZ` 保存 UTC；生日和仅日期事件使用 `DATE`。Moment 分页索引按 `(babyId, eventDate DESC, id)` 设计，为后续 cursor pagination 预留。

## 数据模型

核心模型为 User、Baby、FamilyMember、Moment、MomentAsset、Comment、Like、GrowthRecord、Milestone。关键约束包括：

- `User.username` 唯一
- `FamilyMember(babyId, userId)` 唯一
- `Like(momentId, userId)` 唯一
- `MomentAsset(momentId, immichAssetId)` 唯一
- 媒体只存 Immich Asset ID

## 网络与安全

Android 在家中默认通过同一局域网直连 Windows，当前地址为 `192.168.0.40`；App 设置允许用户修改。Tailscale 仅作为可选的外出访问方式。PostgreSQL 没有映射宿主机端口，只在 Compose 内部网络中供后端访问。
