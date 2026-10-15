<#
.SYNOPSIS
    构建 Android 安装包并上传到腾讯云 COS。

.DESCRIPTION
    为什么走 COS：服务器只有 3Mbps 带宽（≈375KB/s），一个 15MB 的 APK
    用户要下 40 秒以上，而且会把带宽占满导致接口超时。放 COS 走对象存储
    的独立带宽，不占用服务器。

    依赖腾讯云官方 CLI `coscli`（单文件二进制）：
        https://github.com/tencentyun/coscli/releases
    下载 coscli_windows_amd64.exe，改名 coscli.exe 放进 PATH 即可。

    首次使用需要配置密钥（存在 ~/.cos.yaml，不要提交到仓库）：
        coscli config set --secret_id <SecretId> --secret_key <SecretKey>
      或直接用环境变量：
        $env:COS_SECRET_ID  /  $env:COS_SECRET_KEY

.PARAMETER Bucket
    COS 存储桶全名，必须带 APPID 后缀，例如 jizhang-release-1250000000

.PARAMETER Region
    地域，与服务器同地域下载更快。上海填 ap-shanghai

.PARAMETER Build
    先重新构建 APK 再上传

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy/prod/upload-apk-cos.ps1 `
        -Bucket jizhang-release-1250000000 -Region ap-shanghai -Build
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Bucket,
    [string]$Region = 'ap-shanghai',
    [string]$ApkPath,
    [string]$Key = 'jizhang/jizhang-latest.apk',
    [switch]$Build,
    [string]$ApiBaseUrl
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Green }
function Die($msg)  { Write-Host "[x] $msg" -ForegroundColor Red; exit 1 }

if (-not $ApkPath) {
    $ApkPath = Join-Path $RepoRoot 'android\app\build\outputs\apk\debug\app-debug.apk'
}

# ---------------------------------------------------------------------------
# 1. 构建 APK
# ---------------------------------------------------------------------------
if ($Build) {
    if (-not $ApiBaseUrl) {
        Die @'
-Build 时必须同时给 -ApiBaseUrl，否则打出来的包还是指向模拟器的 10.0.2.2。
例如：-ApiBaseUrl http://123.45.67.89/api/v1/
'@
    }
    Step "构建 APK（后端地址 $ApiBaseUrl）"
    Push-Location (Join-Path $RepoRoot 'android')
    try {
        & .\gradlew.bat assembleDebug "-PapiBaseUrl=$ApiBaseUrl"
        if ($LASTEXITCODE -ne 0) { Die 'APK 构建失败' }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $ApkPath)) { Die "找不到 APK：$ApkPath`n先加 -Build 重新构建。" }

$sizeMB = [math]::Round((Get-Item $ApkPath).Length / 1MB, 2)
Write-Host "APK：$ApkPath（$sizeMB MB）"

# ---------------------------------------------------------------------------
# 2. 检查 coscli
# ---------------------------------------------------------------------------
if (-not (Get-Command coscli -ErrorAction SilentlyContinue)) {
    Die @'
没找到 coscli。安装方式：

  1. 打开 https://github.com/tencentyun/coscli/releases
  2. 下载 coscli_windows_amd64.exe
  3. 改名为 coscli.exe，放到一个在 PATH 里的目录（例如 C:\Windows\System32 或自建 tools 目录）
  4. 配置密钥： coscli config set --secret_id <SecretId> --secret_key <SecretKey>
'@
}

# ---------------------------------------------------------------------------
# 3. 上传
# ---------------------------------------------------------------------------
$target = "cos://$Bucket/$Key"
Step "上传到 $target"

& coscli cp $ApkPath $target --region $Region
if ($LASTEXITCODE -ne 0) { Die 'coscli 上传失败' }

# ---------------------------------------------------------------------------
# 4. 输出下载地址
# ---------------------------------------------------------------------------
$url = "https://$Bucket.cos.$Region.myqcloud.com/$Key"
Write-Host "`n上传完成：" -ForegroundColor Green
Write-Host "  $url" -ForegroundColor Cyan
Write-Host @"

下载地址要能用，需要满足其一：
  - 存储桶开启「公有读」（对象存储控制台 → 权限管理 → 公有读私有写）
  - 或给该对象单独设置公有读 ACL：
      coscli cp $ApkPath $target --region $Region --acl public-read
  - 或绑 CDN 域名（生产推荐，顺便省流量费）

发版建议：同时传一份带版本号的，方便回滚
  -Key jizhang/jizhang-1.0.0.apk
随时覆盖 -Key jizhang/jizhang-latest.apk 作为固定下载入口。
"@
