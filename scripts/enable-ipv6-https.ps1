param(
    [string]$Email = '984992820@qq.com',
    [string]$TokenFile = 'E:\pem\之之成长手册 IPv6 DDNS.txt'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$docker = 'C:\Users\Administration\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
$composeArgs = @('--env-file', '.env', '-f', 'docker/docker-compose.yml', '-f', 'docker/docker-compose.public.yml')
$domain = 'zhizhi.zfzyy.top'
$nginxDir = Join-Path $projectRoot 'docker/nginx'

if (-not (Test-Path -LiteralPath $docker)) { throw 'Docker Desktop was not found.' }
if (-not (Test-Path -LiteralPath $TokenFile)) { throw "Token file not found: $TokenFile" }
$token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
if ($token.Length -lt 20) { throw 'Cloudflare token file is empty or invalid.' }

$credentialPath = Join-Path ([IO.Path]::GetTempPath()) ("zhizhi-cloudflare-{0}.ini" -f [guid]::NewGuid().ToString('N'))
[IO.File]::WriteAllText($credentialPath, "dns_cloudflare_api_token = $token`n", [Text.UTF8Encoding]::new($false))

Push-Location $projectRoot
try {
    & $docker compose @composeArgs run --rm `
        --volume "${credentialPath}:/run/secrets/cloudflare.ini:ro" `
        certbot certonly --dns-cloudflare `
        --dns-cloudflare-credentials /run/secrets/cloudflare.ini `
        --dns-cloudflare-propagation-seconds 30 `
        -d $domain --email $Email --agree-tos --no-eff-email --non-interactive
    if ($LASTEXITCODE -ne 0) { throw 'Certificate request using Cloudflare DNS validation failed.' }

    Copy-Item (Join-Path $nginxDir 'https.conf') (Join-Path $nginxDir 'active.conf') -Force
    & $docker compose @composeArgs up -d backend nginx
    if ($LASTEXITCODE -ne 0) { throw 'HTTPS Nginx startup failed.' }
    Write-Output "HTTPS certificate installed for https://$domain"
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $credentialPath) {
        Remove-Item -LiteralPath $credentialPath -Force
    }
}
