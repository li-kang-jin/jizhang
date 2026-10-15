<#
.SYNOPSIS
    一键启动记账系统的本地开发环境（Docker + 后端 + 前端）。

.DESCRIPTION
    依次完成三件事，每步都会等到服务真正就绪，而不是盲等固定秒数：
      1. 拉起 Docker 并启动 PostgreSQL / Redis 容器
      2. 构建后端（可跳过）并后台启动 Spring Boot 服务
      3. 启动管理后台 Vite 开发服务器

.PARAMETER SkipBuild
    跳过 Maven 构建，直接用已有的 jar 启动后端（改完后端代码后想快速重启时用）。

.PARAMETER SkipWeb
    只启动后端，不启动前端。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy/start-all.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy/start-all.ps1 -SkipBuild
#>
param(
    [switch]$SkipBuild,
    [switch]$SkipWeb
)

# 刻意不用 'Stop'：PowerShell 5.1 下原生命令（docker / mvn / npm）把进度信息写进
# stderr 时，一旦被重定向就会被包装成 ErrorRecord，在 Stop 模式下会直接中断整个脚本。
# 这里保持 Continue，改为在每个关键步骤后显式检查退出码。
$ErrorActionPreference = 'Continue'

$deployDir = $PSScriptRoot
$root = Split-Path -Parent $deployDir
$compose = Join-Path $deployDir 'docker-compose.yml'
$settings = Join-Path $deployDir 'maven-settings.xml'
$jar = Join-Path $root 'server\jizhang-api\target\jizhang-api.jar'
$mvn = 'D:\code2\tools\apache-maven-3.9.9\bin\mvn.cmd'

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function Write-Ok($msg) { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Hint($msg) { Write-Host "  [!]  $msg" -ForegroundColor Yellow }

function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Stop-PortProcess([int]$Port) {
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        Stop-Process -Id $conn[0].OwningProcess -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 3
    }
}

# ---------------- 1. 依赖服务 ----------------
Write-Step '1/3 依赖服务（PostgreSQL / Redis）'

$dockerReady = $false
docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -eq 0) { $dockerReady = $true } else { $global:LASTEXITCODE = 0 }

if (-not $dockerReady) {
    $dd = @(
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
        'D:\Program Files\Docker\Docker\Docker Desktop.exe'
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $dd) { throw '未找到 Docker Desktop，请先安装或手动启动。' }

    Write-Host '  正在启动 Docker Desktop，首次启动可能需要 1 分钟 ...'
    Start-Process $dd
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 5
        docker info --format '{{.ServerVersion}}' *> $null
        if ($LASTEXITCODE -eq 0) { $dockerReady = $true; break }
        $global:LASTEXITCODE = 0
    }
}
if (-not $dockerReady) { throw 'Docker daemon 启动超时，请手动打开 Docker Desktop 后重试。' }
Write-Ok 'Docker daemon 就绪'

docker compose -f $compose up -d *> $null
$global:LASTEXITCODE = 0

$pgReady = $false
for ($i = 0; $i -lt 20; $i++) {
    docker exec jizhang-postgres pg_isready -U jizhang *> $null
    if ($LASTEXITCODE -eq 0) { $pgReady = $true; break }
    $global:LASTEXITCODE = 0
    Start-Sleep -Seconds 3
}
if (-not $pgReady) { throw 'PostgreSQL 未就绪，请检查容器状态：docker ps -a' }
Write-Ok 'PostgreSQL 就绪'

# 容器被重建时会丢掉业务库，这里兜底补建（建表由应用启动脚本负责）
$dbExists = docker exec jizhang-postgres psql -U jizhang -d postgres -t -A -c "SELECT 1 FROM pg_database WHERE datname='jizhang';" 2>$null
$global:LASTEXITCODE = 0
if ($dbExists -notmatch '1') {
    docker exec jizhang-postgres psql -U jizhang -d postgres -c 'CREATE DATABASE jizhang OWNER jizhang;' *> $null
    Write-Hint '业务库 jizhang 不存在，已重新创建'
}
Write-Ok 'Redis / 数据库检查完成'

# ---------------- 2. 后端 ----------------
Write-Step '2/3 后端服务（Spring Boot）'

if (-not $SkipBuild) {
    Write-Host '  正在构建后端（首次较慢，之后走增量）...'
    & $mvn -s $settings -f (Join-Path $root 'server\pom.xml') clean package -DskipTests -B -q
    if ($LASTEXITCODE -ne 0) { throw '后端构建失败，请查看上方 Maven 输出。' }
    Write-Ok '构建完成'
}
if (-not (Test-Path $jar)) { throw "未找到 $jar，请去掉 -SkipBuild 先构建一次。" }

if (Test-Port 8080) {
    Write-Hint '端口 8080 被占用，停止旧进程后重启'
    Stop-PortProcess 8080
}

$api = Start-Process -FilePath 'java' `
    -ArgumentList '-jar', $jar `
    -RedirectStandardOutput (Join-Path $deployDir 'app.log') `
    -RedirectStandardError (Join-Path $deployDir 'app.err.log') `
    -WindowStyle Hidden -PassThru
Write-Host "  后端 PID = $($api.Id)，等待就绪 ..."

$apiReady = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 3
    try {
        $resp = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 5
        if ($resp.StatusCode -eq 200) { $apiReady = $true; break }
    } catch { }
}
if ($apiReady) { Write-Ok '后端就绪：http://localhost:8080' }
else { Write-Hint '后端未在预期时间内就绪，查看 deploy\app.log' }

# ---------------- 3. 前端 ----------------
if (-not $SkipWeb) {
    Write-Step '3/3 管理后台（Vite）'

    $webDir = Join-Path $root 'admin-web'
    if (-not (Test-Path (Join-Path $webDir 'node_modules'))) {
        Write-Host '  首次运行，安装前端依赖 ...'
        Push-Location $webDir
        npm install --registry=https://registry.npmmirror.com
        Pop-Location
    }

    if (Test-Port 5173) {
        Write-Hint '端口 5173 被占用，停止旧进程后重启'
        Stop-PortProcess 5173
    }

    $web = Start-Process -FilePath 'npm.cmd' -ArgumentList 'run', 'dev' `
        -WorkingDirectory $webDir `
        -RedirectStandardOutput (Join-Path $deployDir 'web.log') `
        -RedirectStandardError (Join-Path $deployDir 'web.err.log') `
        -WindowStyle Hidden -PassThru
    Write-Host "  前端 PID = $($web.Id)，等待就绪 ..."

    $webReady = $false
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 3
        if (Test-Port 5173) { $webReady = $true; break }
    }
    if ($webReady) { Write-Ok '管理后台就绪：http://localhost:5173' }
    else { Write-Hint '前端未在预期时间内就绪，查看 deploy\web.log' }
}

Write-Host ''
Write-Host '启动完成' -ForegroundColor Cyan
Write-Host '  管理后台    http://localhost:5173        (admin / admin123456)'
Write-Host '  后端接口    http://localhost:8080'
Write-Host '  接口文档    http://localhost:8080/swagger-ui.html'
Write-Host '  日志        deploy\app.log  deploy\web.log'
