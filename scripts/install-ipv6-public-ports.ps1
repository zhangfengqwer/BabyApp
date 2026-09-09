$ErrorActionPreference = 'Stop'

$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Please run this script from an Administrator PowerShell window.'
}

Set-Service -Name iphlpsvc -StartupType Automatic
Start-Service -Name iphlpsvc

$address = Get-NetIPAddress -AddressFamily IPv6 |
    Where-Object {
        $_.AddressState -eq 'Preferred' -and
        $_.SuffixOrigin -eq 'Link' -and
        $_.IPAddress -notlike 'fe80:*'
    } |
    Select-Object -First 1 -ExpandProperty IPAddress
if (-not $address) { throw 'No stable public IPv6 address was found.' }

foreach ($port in @(80, 443)) {
    # Docker is bound to IPv4 loopback, so IP Helper can safely own the IPv6 wildcard.
    & netsh interface portproxy delete v6tov4 listenaddress=$address listenport=$port protocol=tcp 2>$null | Out-Null
    & netsh interface portproxy set v6tov4 listenaddress=:: listenport=$port connectaddress=127.0.0.1 connectport=$port protocol=tcp | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed to configure IPv6 port proxy for TCP $port." }

    $ruleName = "Zhizhi Album IPv6 HTTPS $port"
    $existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
    if ($existing) {
        $existing | Set-NetFirewallRule -Enabled True -Direction Inbound -Action Allow -Profile Any | Out-Null
    } else {
        New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow `
            -Protocol TCP -LocalPort $port -Profile Any | Out-Null
    }
}

# Portproxy does not always bind newly added IPv6 listeners until IP Helper reloads.
Restart-Service -Name iphlpsvc -Force
Start-Sleep -Seconds 2

Write-Output 'IPv6 public port forwarding is ready on :: for TCP 80 and 443.'
& netsh interface portproxy show v6tov4
