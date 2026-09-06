# 公网访问：zhizhi.zfzyy.top

此部署只公开 Nginx 的 80/443 端口。Baby Backend 的 8080、Immich 的 2283 和 PostgreSQL 的 5432 不应在路由器中做端口转发。

## TP-Link 路由器

截图中的“内网映射”是商云中转服务，不是本项目使用的端口转发。请回到路由器本地管理页，找到以下任一入口：

- 高级 → NAT 转发 → 虚拟服务器
- 高级 → NAT 转发 → 端口转发
- 转发规则 → 虚拟服务器

先为 Windows 家庭服务器保留局域网地址 `192.168.0.40`，再添加两条 TCP 规则：

| 名称 | 外部端口 | 内部 IP | 内部端口 |
| --- | --- | --- | --- |
| Baby Home HTTP | 80 | 192.168.0.40 | 80 |
| Baby Home HTTPS | 443 | 192.168.0.40 | 443 |

不要开启路由器“远程管理”，也不要转发 8080、2283、5432。

## Windows 和 Docker

以管理员身份打开 PowerShell，在项目根目录执行：

```powershell
New-NetFirewallRule -DisplayName "Baby Home Public HTTP" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 80 -Profile Any
New-NetFirewallRule -DisplayName "Baby Home Public HTTPS" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 443 -Profile Any
.\scripts\enable-public-access.ps1 -Email "你的真实邮箱"
```

脚本会先申请 `zhizhi.zfzyy.top` 的 Let’s Encrypt 证书，成功后再启动 HTTPS 入口。完成后关闭手机 Wi-Fi、使用移动数据打开：

```text
https://zhizhi.zfzyy.top/health
```

能看到 JSON 状态即代表公网入口正常。

## App 设置

在 App 的“我的 → 服务器设置”填写：

```text
Baby Server URL: https://zhizhi.zfzyy.top
Immich URL:      https://zhizhi.zfzyy.top
```

然后点击“测试连接”。App 的媒体请求经过 Baby Backend 代理，不会直接公开 Immich。

APK 内置家庭访问密钥，拿到 APK 的人可以访问该家庭相册。只把 APK 提供给家人；若 APK 外泄，请更换 `.env` 与 `apps/android/local.properties` 中的 `HOME_ACCESS_KEY` 后重新部署后端并重新构建 APK。
