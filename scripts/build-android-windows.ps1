$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repositoryRoot 'apps\android'
$logPath = Join-Path $repositoryRoot 'android-build.log'
$exitPath = Join-Path $repositoryRoot 'android-build-exit.txt'
[System.IO.File]::Delete($exitPath)
$jdkRoot = 'C:\Program Files\Eclipse Adoptium'
$jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction Stop |
    Where-Object Name -Like 'jdk-17*' |
    Sort-Object Name -Descending |
    Select-Object -First 1

if ($null -eq $jdk) {
    throw "JDK 17 was not found under $jdkRoot"
}

$env:JAVA_HOME = $jdk.FullName
$env:Path = "$($env:JAVA_HOME)\bin;$($env:Path)"
$env:TEMP = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Temp'
$env:TMP = $env:TEMP
$env:GRADLE_USER_HOME = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle'

Set-Location -LiteralPath $androidRoot
$gradleWrapper = Join-Path $androidRoot 'gradlew.bat'
$commandLine = "`"$gradleWrapper`" assembleDebug testDebugUnitTest --stacktrace > `"$logPath`" 2>&1"
& $env:ComSpec /d /c $commandLine
$buildExitCode = $LASTEXITCODE
[System.IO.File]::WriteAllText($exitPath, [string]$buildExitCode)
if ($buildExitCode -eq 0) {
    $apkPath = Join-Path $androidRoot 'app\build\outputs\apk\debug\app-debug.apk'
    $releaseRoot = Join-Path $repositoryRoot 'releases'
    $releaseApk = Join-Path $releaseRoot 'zhizhi-growth-latest.apk'
    $gradleFile = Join-Path $androidRoot 'app\build.gradle.kts'
    $gradleText = [System.IO.File]::ReadAllText($gradleFile)
    $versionCode = [regex]::Match($gradleText, 'versionCode\s*=\s*(\d+)').Groups[1].Value
    $versionName = [regex]::Match($gradleText, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    New-Item -ItemType Directory -Force -Path $releaseRoot | Out-Null
    Copy-Item -LiteralPath $apkPath -Destination $releaseApk -Force
    $sha256 = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
    $manifest = [ordered]@{
        versionCode = [int]$versionCode
        versionName = $versionName
        sha256 = $sha256
        publishedAt = [DateTime]::UtcNow.ToString('o')
    } | ConvertTo-Json
    [System.IO.File]::WriteAllText(
        (Join-Path $releaseRoot 'version.json'),
        $manifest,
        [System.Text.UTF8Encoding]::new($false)
    )
}
exit $buildExitCode
