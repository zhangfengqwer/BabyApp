[CmdletBinding()]
param(
    [string]$SourceDirectory = 'E:\迅雷下载\张之意',
    [string]$ServerUrl = 'http://127.0.0.1:8080',
    # 未传入时仅预览，不会上传、移动或删除任何文件。
    [switch]$Import,
    # 仅在某一天的动态成功创建后，将源文件移至归档目录。
    [switch]$MoveSourceAfterSuccess,
    [string]$ArchiveDirectory = 'E:\迅雷下载\张之意_已导入'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$extensions = @('.jpg', '.jpeg', '.png', '.webp', '.heic', '.mp4', '.mov', '.m4v')
$datePattern = '_(?<date>\d{4}-\d{2}-\d{2})_'
$stateDirectory = Join-Path $PSScriptRoot '.state'
$journalPath = Join-Path $stateDirectory 'zhang-zhiyi-import-journal.csv'

function Get-HomeAccessKey {
    $envPath = Join-Path $PSScriptRoot '..\.env'
    if (-not (Test-Path -LiteralPath $envPath)) { throw "找不到配置文件：$envPath" }
    $line = Get-Content -LiteralPath $envPath | Where-Object { $_ -match '^HOME_ACCESS_KEY=' } | Select-Object -First 1
    if (-not $line) { throw '配置文件中缺少 HOME_ACCESS_KEY。' }
    $key = ($line -split '=', 2)[1].Trim()
    if ($key.Length -lt 32) { throw 'HOME_ACCESS_KEY 配置无效。' }
    $key
}

function Get-MimeType([IO.FileInfo]$file) {
    switch ($file.Extension.ToLowerInvariant()) {
        '.jpg' { 'image/jpeg'; break }; '.jpeg' { 'image/jpeg'; break }
        '.png' { 'image/png'; break }; '.webp' { 'image/webp'; break }
        '.heic' { 'image/heic'; break }; '.mp4' { 'video/mp4'; break }
        '.mov' { 'video/quicktime'; break }; '.m4v' { 'video/x-m4v'; break }
        default { 'application/octet-stream' }
    }
}

function Get-AssetType([IO.FileInfo]$file) {
    if ($file.Extension.ToLowerInvariant() -in @('.mp4', '.mov', '.m4v')) { 'VIDEO' } else { 'IMAGE' }
}

function Add-Journal([string]$kind, [string]$date, [string]$path, [string]$assetId, [string]$momentId) {
    [pscustomobject]@{
        Kind = $kind; Date = $date; SourcePath = $path; AssetId = $assetId
        MomentId = $momentId; RecordedAtUtc = [DateTime]::UtcNow.ToString('o')
    } | Export-Csv -LiteralPath $journalPath -NoTypeInformation -Append -Encoding UTF8
}

function Archive-Files([object[]]$files, [string]$date) {
    $destinationDirectory = Join-Path $ArchiveDirectory $date
    New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
    foreach ($file in $files) {
        if (-not (Test-Path -LiteralPath $file.FullName)) { continue }
        $destination = Join-Path $destinationDirectory $file.Name
        if (Test-Path -LiteralPath $destination) {
            Write-Warning "归档目录已有同名文件，未移动：$($file.Name)"
        } else {
            Move-Item -LiteralPath $file.FullName -Destination $destination
        }
    }
}

if (-not (Test-Path -LiteralPath $SourceDirectory -PathType Container)) { throw "找不到照片目录：$SourceDirectory" }
$groups = @{}
$unmatched = 0
Get-ChildItem -LiteralPath $SourceDirectory -Recurse -File | ForEach-Object {
    if ($_.Extension.ToLowerInvariant() -notin $extensions) { return }
    $match = [regex]::Match($_.Name, $datePattern)
    if (-not $match.Success) { $script:unmatched++; return }
    $date = $match.Groups['date'].Value
    if (-not $groups.ContainsKey($date)) { $groups[$date] = [Collections.Generic.List[object]]::new() }
    $groups[$date].Add($_)
}
if ($groups.Count -eq 0) { throw '没有发现文件名中包含 yyyy-MM-dd 的图片或视频。' }
$count = ($groups.Values | ForEach-Object { $_.Count } | Measure-Object -Sum).Sum
Write-Host "发现 $($groups.Count) 天、$count 个可导入媒体。" -ForegroundColor Cyan
$groups.GetEnumerator() | Sort-Object Key | ForEach-Object {
    if (-not $Import) { Write-Host ("{0}  {1,4} 个文件" -f $_.Key, $_.Value.Count) }
}
if ($unmatched) { Write-Warning "$unmatched 个支持媒体没有日期名，已跳过。" }
if (-not $Import) {
    Write-Host "`n这是预览，没有改动文件。确认后运行：.\scripts\import-legacy-album.ps1 -Import" -ForegroundColor Yellow
    Write-Host '如要在成功后移动原文件，再加 -MoveSourceAfterSuccess'
    exit 0
}

New-Item -ItemType Directory -Force -Path $stateDirectory | Out-Null

$baseUrl = $ServerUrl.TrimEnd('/')
$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/auth/home" -Headers @{ 'X-Home-Access-Key' = (Get-HomeAccessKey) }
$token = $login.data.accessToken
if (-not $token) { throw '家庭服务器未返回访问令牌。' }
$headers = @{ Authorization = "Bearer $token" }
$babies = (Invoke-RestMethod -Method Get -Uri "$baseUrl/babies" -Headers $headers).data
if ($babies.Count -ne 1) { throw '当前需要恰好一个宝宝资料，避免把旧相册导给错误的宝宝。' }
$babyId = $babies[0].id

# 日志按文件记录 Asset ID，按日期记录 Moment ID；网络中断后重跑可续传。
$uploaded = @{}; $createdMoments = @{}
if (Test-Path -LiteralPath $journalPath) {
    Import-Csv -LiteralPath $journalPath | ForEach-Object {
        if ($_.Kind -eq 'asset' -and $_.AssetId) { $uploaded[$_.SourcePath] = $_.AssetId }
        if ($_.Kind -eq 'moment' -and $_.MomentId) { $createdMoments[$_.Date] = $_.MomentId }
    }
    Write-Host '已读取上次迁移记录，将从未完成处继续。' -ForegroundColor DarkCyan
}

$http = [System.Net.Http.HttpClient]::new()
$http.Timeout = [TimeSpan]::FromMinutes(30)
$http.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
try {
    foreach ($entry in ($groups.GetEnumerator() | Sort-Object Key)) {
        $date = $entry.Key; $files = @($entry.Value | Sort-Object Name)
        if ($createdMoments.ContainsKey($date)) {
            Write-Host "$date 已导入，跳过。" -ForegroundColor DarkGray
            if ($MoveSourceAfterSuccess) { Archive-Files $files $date }
            continue
        }
        Write-Host "`n[$date] 正在上传 $($files.Count) 个文件…" -ForegroundColor Cyan
        $assets = [Collections.Generic.List[object]]::new()
        foreach ($file in $files) {
            $assetId = $uploaded[$file.FullName]
            if (-not $assetId) {
                $stream = [IO.File]::OpenRead($file.FullName)
                $form = [System.Net.Http.MultipartFormDataContent]::new()
                try {
                    $content = [System.Net.Http.StreamContent]::new($stream)
                    $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse((Get-MimeType $file))
                    $form.Add($content, 'assetData', $file.Name)
                    $timestamp = "${date}T12:00:00+08:00"
                    $form.Add([System.Net.Http.StringContent]::new($timestamp), 'fileCreatedAt')
                    $form.Add([System.Net.Http.StringContent]::new($timestamp), 'fileModifiedAt')
                    $form.Add([System.Net.Http.StringContent]::new('false'), 'isFavorite')
                    $result = $http.PostAsync("$baseUrl/media/assets", $form).GetAwaiter().GetResult()
                    $body = $result.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                    if (-not $result.IsSuccessStatusCode) { throw "上传 $($file.Name) 失败 ($($result.StatusCode))：$body" }
                    $assetId = ($body | ConvertFrom-Json).id
                    if (-not $assetId) { throw "Immich 未返回 Asset ID：$body" }
                    Add-Journal 'asset' $date $file.FullName $assetId ''
                    $uploaded[$file.FullName] = $assetId
                } finally { $form.Dispose(); $stream.Dispose() }
            }
            $assets.Add([ordered]@{ immichAssetId = $assetId; assetType = (Get-AssetType $file); sortOrder = $assets.Count })
        }
        # 中国时区的中午，保证 App 中显示为文件名对应的日期。
        $payload = @{ content = $null; eventDate = "${date}T12:00:00+08:00"; location = $null; assets = @($assets) } | ConvertTo-Json -Depth 5
        $moment = Invoke-RestMethod -Method Post -Uri "$baseUrl/babies/$babyId/moments" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $payload
        if (-not $moment.data.id) { throw "$date 动态创建失败。" }
        Add-Journal 'moment' $date '' '' $moment.data.id
        $createdMoments[$date] = $moment.data.id
        Write-Host "[$date] 完成：$($files.Count) 个媒体。" -ForegroundColor Green
        if ($MoveSourceAfterSuccess) { Archive-Files $files $date }
    }
} finally { $http.Dispose() }
Write-Host "`n全部导入完成。迁移记录：$journalPath" -ForegroundColor Green
