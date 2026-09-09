param(
    [string]$TokenFile = 'E:\pem\之之成长手册 IPv6 DDNS.txt'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$updater = Join-Path $PSScriptRoot 'update-cloudflare-ipv6-ddns.ps1'
$taskName = 'Zhizhi IPv6 DDNS'
$logFile = Join-Path $projectRoot 'ipv6-ddns.log'

if (-not (Test-Path -LiteralPath $updater)) { throw "Updater script not found: $updater" }
if (-not (Test-Path -LiteralPath $TokenFile)) { throw "Token file not found: $TokenFile" }

$arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$updater`" -TokenFile `"$TokenFile`" >> `"$logFile`" 2>&1"
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
$startup = New-ScheduledTaskTrigger -AtStartup
$repeat = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) `
    -RepetitionInterval (New-TimeSpan -Minutes 30) `
    -RepetitionDuration (New-TimeSpan -Days 3650)
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 2)
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger @($startup, $repeat) `
    -Settings $settings -Principal $principal -Description 'Update zhizhi.zfzyy.top AAAA record from the stable home IPv6 address.' -Force -ErrorAction Stop | Out-Null
Start-ScheduledTask -TaskName $taskName -ErrorAction Stop
Write-Output "Installed and started scheduled task: $taskName"
