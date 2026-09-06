# API（Phase 1）

## 统一响应

成功响应：

```json
{
  "success": true,
  "data": {}
}
```

后续业务错误会统一为 `success: false`、错误代码和中文可映射消息；不会把数据库或 Socket 异常直接显示给用户。

## GET /health

检查后端、数据库和 Immich 连通性。

响应示例：

```json
{
  "success": true,
  "data": {
    "status": "ok",
    "services": {
      "backend": "up",
      "database": "up",
      "immich": "up"
    },
    "latencyMs": 32,
    "timestamp": "2026-08-27T12:00:00.000Z"
  }
}
```

组件状态可为 `up`、`down`、`not_configured`。数据库不可用或已配置的 Immich 不可用时，总体状态为 `degraded`。

## POST /auth/login

请求：

```json
{ "username": "admin", "password": "your-password" }
```

返回 Access Token、Refresh Token、有效期和当前用户。密码或完整 Token 不写入日志。

## POST /auth/refresh

请求：

```json
{ "refreshToken": "..." }
```

Refresh Token 单次轮换，数据库只保存 SHA-256 哈希；旧 Token 在换新后立即撤销。

## GET /babies

需要 `Authorization: Bearer <accessToken>`。ADMIN 查看所有宝宝，其他角色只查看存在 FamilyMember 关系的宝宝。

## GET /babies/:id/moments

需要登录，支持 `cursor` 和 `limit`（最大 50），按事件时间倒序返回动态、作者、媒体引用、点赞/评论数和当前用户点赞状态。

Android 时间轴首次请求不传 cursor，接近列表底部时传上页的 `nextCursor`；下拉刷新会清空旧 cursor 并重新请求第一页。

## POST /babies/:id/moments

ADMIN/PARENT 创建动态并关联已经上传的 Immich Asset ID；FAMILY 返回 403。请求中的 `assets` 包含 `immichAssetId`、`assetType` 与 `sortOrder`，媒体二进制不进入 PostgreSQL。

## POST /media/assets

ADMIN/PARENT 使用 Baby JWT 上传 multipart 媒体。Backend 将请求流直接转发到 Immich `POST /api/assets`，API Key 只在服务器端添加，不缓存或落盘上传文件。

## GET /media/assets/:id/view

登录用户读取 Immich 媒体流。`size` 可为 `thumbnail`、`preview`、`fullsize`，并转发 Range 请求头以给后续视频播放使用。
