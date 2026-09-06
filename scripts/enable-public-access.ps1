param(
    [Parameter(Mandatory = $true)]
    [string]$Email
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$docker = 'C:\Users\Administration\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
$composeArgs = @('--env-file', '.env', '-f', 'docker/docker-compose.yml', '-f', 'docker/docker-compose.public.yml')
$domain = 'zhizhi.zfzyy.top'
$nginxDir = Join-Path $projectRoot 'docker/nginx'

$envFile = Join-Path -Path $projectRoot -ChildPath '.env'
if (-not (Test-Path -LiteralPath $docker)) { throw 'Docker Desktop was not found. Start Docker Desktop first.' }
if (-not (Test-Path -LiteralPath $envFile)) { throw 'The project root .env file was not found.' }

# 先只开放 ACME 验证页面，证书成功后才切换到 HTTPS 代理。
Copy-Item (Join-Path $nginxDir 'bootstrap.conf') (Join-Path $nginxDir 'active.conf') -Force
Push-Location $projectRoot
try {
    & $docker compose @composeArgs up -d --build backend nginx
    if ($LASTEXITCODE -ne 0) { throw 'Nginx startup failed.' }

    & $docker compose @composeArgs run --rm certbot certonly --webroot -w /var/www/certbot `
        -d $domain --email $Email --agree-tos --no-eff-email --non-interactive
    if ($LASTEXITCODE -ne 0) {
        throw 'Certificate request failed over TCP 80. Check the upstream gateway, ISP port blocking, or use DNS validation.'
    }

    Copy-Item (Join-Path $nginxDir 'https.conf') (Join-Path $nginxDir 'active.conf') -Force
    & $docker compose @composeArgs up -d nginx
    if ($LASTEXITCODE -ne 0) { throw 'HTTPS Nginx startup failed.' }

    Write-Host "Public HTTPS is ready: https://$domain/health"
    Write-Host 'Test the address from mobile data.'
} finally {
    Pop-Location
}
