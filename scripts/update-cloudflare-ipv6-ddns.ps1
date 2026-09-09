param(
    [string]$TokenFile = 'E:\pem\之之成长手册 IPv6 DDNS.txt',
    [string]$ZoneName = 'zfzyy.top',
    [string]$RecordName = 'zhizhi.zfzyy.top',
    [string]$InterfaceAlias = '以太网'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $TokenFile)) {
    throw "Cloudflare token file was not found: $TokenFile"
}

$token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
if ($token.Length -lt 20) {
    throw 'Cloudflare token file is empty or invalid.'
}

# Windows labels privacy addresses as Random. Only publish the stable Link address.
$address = Get-NetIPAddress -InterfaceAlias $InterfaceAlias -AddressFamily IPv6 |
    Where-Object {
        $_.AddressState -eq 'Preferred' -and
        $_.SuffixOrigin -eq 'Link' -and
        $_.IPAddress -notlike 'fe80:*'
    } |
    Select-Object -First 1 -ExpandProperty IPAddress

if (-not $address) {
    throw "No stable public IPv6 address was found on interface '$InterfaceAlias'."
}

$headers = @{
    Authorization = "Bearer $token"
    'Content-Type' = 'application/json'
}
$api = 'https://api.cloudflare.com/client/v4'
$encodedZone = [Uri]::EscapeDataString($ZoneName)
$zoneResponse = Invoke-RestMethod -Method Get -Uri "$api/zones?name=$encodedZone" -Headers $headers
if (-not $zoneResponse.success -or $zoneResponse.result.Count -ne 1) {
    throw "Cloudflare zone '$ZoneName' was not found or is ambiguous."
}
$zoneId = $zoneResponse.result[0].id

$encodedName = [Uri]::EscapeDataString($RecordName)
$recordResponse = Invoke-RestMethod -Method Get -Uri "$api/zones/$zoneId/dns_records?type=AAAA&name=$encodedName" -Headers $headers
$record = $recordResponse.result | Select-Object -First 1

$body = @{
    type = 'AAAA'
    name = $RecordName
    content = $address
    ttl = 1
    proxied = $false
} | ConvertTo-Json

if ($record) {
    if ($record.content -eq $address -and -not $record.proxied) {
        Write-Output "[$(Get-Date -Format s)] IPv6 unchanged: $address"
        exit 0
    }
    $result = Invoke-RestMethod -Method Patch -Uri "$api/zones/$zoneId/dns_records/$($record.id)" -Headers $headers -Body $body
} else {
    $result = Invoke-RestMethod -Method Post -Uri "$api/zones/$zoneId/dns_records" -Headers $headers -Body $body
}

if (-not $result.success) {
    throw 'Cloudflare rejected the DNS update.'
}
Write-Output "[$(Get-Date -Format s)] Updated $RecordName to $address"
