# Windows + Docker Desktop 部署

## 前置条件

- Windows 10/11
- Docker Desktop 已启动，使用 Linux containers / WSL 2
- Immich 已独立运行；本 Compose 不创建第二套 Immich
- Windows Tailscale 已登录并启用 MagicDNS

## 启动

在仓库根目录复制配置：

```powershell
Copy-Item .env.example .env
```

编辑 `.env`，至少更换数据库密码和 Immich API Key。然后执行：

```powershell
docker compose --env-file .env -f docker/docker-compose.yml up -d --build
```

后端容器启动时会执行 `prisma migrate deploy`，之后监听 `0.0.0.0:8080`。PostgreSQL 数据存放在 Docker 命名卷 `baby_postgres_data`，PostgreSQL 端口不暴露给宿主机或 tailnet。

## Immich 连接

Docker Desktop 容器访问 Windows 宿主机上的 Immich 通常使用：

```env
IMMICH_BASE_URL=http://host.docker.internal:2283
```

如果 Immich 运行在另一套 Compose 网络或另一台 Tailscale 设备上，应按实际可达地址调整。不要创建第二套 Immich，也不要把 2283 转发到公网。

当前项目的唯一 Immich 实例使用官方 v3 Compose：

```powershell
docker compose -f docker/immich/docker-compose.yml up -d
```

原始媒体保存在 `E:\ImmichLibrary`，Immich PostgreSQL 文件保存在 `E:\ImmichLibrary\postgres`。`docker/immich/.env` 包含数据库密码和本地管理员凭据，已被 Git 忽略，必须纳入离线安全备份。

## Tailscale 与防火墙

Tailscale 安装在 Windows 主机，不必进入容器。确认 Windows 上：

```powershell
tailscale status
tailscale ip -4
```

在 Android Tailscale 内测试能够解析当前服务器短名称 `desktop-rcsbtks`。Windows 防火墙规则应限制为 Tailscale 网络配置文件/接口，不配置路由器端口转发。

## 升级与备份

升级前备份：

- Immich 的原始媒体目录及其自身数据库
- Baby Home PostgreSQL 数据库
- 根目录 `.env`（离线安全保存）

Immich 数据盘本身不是备份，建议使用第二块独立磁盘保存副本。
