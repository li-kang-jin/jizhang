<#
.SYNOPSIS
    记账系统 —— 本地一键构建 + 部署到云服务器。

.DESCRIPTION
    在本地（Windows）完成：
      1. 构建后端 jar（Maven）
      2. 构建管理后台 dist（Vite）
      3. 打成单个 tar.gz 上传（比 scp 一堆小文件快得多）
      4. 在服务器上解包并执行 provision.sh（幂等，可反复跑）

    服务器侧形态见 provision.sh：PG/Redis 走 Docker，后端 systemd 直跑 jar，Nginx 做入口。

.PARAMETER SshTarget
    ~/.ssh/config 里的主机别名，例如 jz-prod。默认 jz-prod。
    请先配置好别名与密钥，不要把密码写在这里。

.PARAMETER SkipBuild
    跳过构建，直接用上次的 jar / dist 上传（只改配置时很快）。

.PARAMETER MavenCmd
    mvn 可执行文件路径。不传则先在 PATH 里找，找不到就用本机已知路径。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy/prod/deploy.ps1

.EXAMPLE
    # 只改了 nginx / compose / service 配置，不重新构建
    powershell -ExecutionPolicy Bypass -File deploy/prod/deploy.ps1 -SkipBuild
#>
[CmdletBinding()]
param(
    [string]$SshTarget = 'jz-prod',
    [switch]$SkipBuild,
    [string]$MavenCmd
)

$ErrorActionPreference = 'Stop'

# 仓库根目录（本脚本在 deploy/prod/ 下）
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ProdDir  = $PSScriptRoot
$Staging  = Join-Path $ProdDir '.staging'
$Payload  = Join-Path $ProdDir 'payload.tgz'
$JarPath  = Join-Path $RepoRoot 'server\jizhang-api\target\jizhang-api.jar'
$DistDir  = Join-Path $RepoRoot 'admin-web\dist'

function Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Green }
function Warn($msg) { Write-Host "[!] $msg" -ForegroundColor Yellow }
function Die($msg)  { Write-Host "[x] $msg" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------------------
# 0. 连通性 & 构建前检查
# ---------------------------------------------------------------------------
Step "检查 SSH 连接（$SshTarget）"
$sshProbe = & ssh -o BatchMode=yes -o ConnectTimeout=10 $SshTarget 'echo OK' 2>&1
if ($LASTEXITCODE -ne 0 -or ($sshProbe -join '') -notmatch 'OK') {
    Warn ($sshProbe -join "`n")
    Die @"
连不上 $SshTarget。请先在 ~/.ssh/config 里配好别名，例如：

    Host jz-prod
        HostName <服务器公网IP>
        User root
        Port 22
        IdentityFile ~/.ssh/jz-prod

并把公钥装到服务器（首次需要输一次服务器密码）：
    type `$env:USERPROFILE\.ssh\jz-prod.pub | ssh root@<IP> "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
"@
}
Write-Host "SSH 正常"

if (-not $SkipBuild) {
    # 本机常驻着一个 dev 后端，会锁住 target 下的 jar，导致 mvn clean 失败
    $lockers = Get-Process java -ErrorAction SilentlyContinue
    if ($lockers -and (Test-Path $JarPath)) {
        try {
            $fs = [System.IO.File]::Open($JarPath, 'Open', 'ReadWrite', 'None')
            $fs.Close()
        } catch {
            Die "本机有 java 进程正占用 $JarPath，请先停掉本地后端再部署。"
        }
    }
}

# ---------------------------------------------------------------------------
# 1. 构建后端
# ---------------------------------------------------------------------------
if (-not $SkipBuild) {
    Step '构建后端（Maven）'

    if (-not $MavenCmd) {
        $found = Get-Command mvn -ErrorAction SilentlyContinue
        if ($found) {
            $MavenCmd = $found.Source
        } else {
            $guess = 'D:\code2\tools\apache-maven-3.9.9\bin\mvn.cmd'
            if (Test-Path $guess) { $MavenCmd = $guess }
        }
    }
    if (-not $MavenCmd) { Die "找不到 mvn，请用 -MavenCmd 指定路径。" }
    Write-Host "使用 Maven: $MavenCmd"

    & $MavenCmd -s (Join-Path $RepoRoot 'deploy\maven-settings.xml') `
                -f (Join-Path $RepoRoot 'server\pom.xml') `
                clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { Die '后端构建失败' }

    Step '构建管理后台（Vite）'
    Push-Location (Join-Path $RepoRoot 'admin-web')
    try {
        if (-not (Test-Path 'node_modules')) {
            Write-Host 'node_modules 不存在，先 npm install'
            npm install
            if ($LASTEXITCODE -ne 0) { Die 'npm install 失败' }
        }
        npm run build
        if ($LASTEXITCODE -ne 0) { Die '管理后台构建失败' }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $JarPath)) { Die "jar 不存在：$JarPath" }
if (-not (Test-Path (Join-Path $DistDir 'index.html'))) { Die "dist 不存在：$DistDir" }

# ---------------------------------------------------------------------------
# 2. 组装上传包
# ---------------------------------------------------------------------------
Step '组装上传包'
if (Test-Path $Staging) { Remove-Item $Staging -Recurse -Force }
if (Test-Path $Payload) { Remove-Item $Payload -Force }

New-Item -ItemType Directory -Path (Join-Path $Staging 'bin')        -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $Staging 'admin-web')  -Force | Out-Null

Copy-Item $JarPath (Join-Path $Staging 'bin\jizhang-api.jar')
Copy-Item (Join-Path $DistDir '*') (Join-Path $Staging 'admin-web') -Recurse -Force
Copy-Item (Join-Path $ProdDir 'docker-compose.prod.yml') $Staging
Copy-Item (Join-Path $ProdDir 'nginx-jizhang.conf')       $Staging
Copy-Item (Join-Path $ProdDir 'jizhang-api.service')     $Staging
Copy-Item (Join-Path $ProdDir 'provision.sh')            $Staging
Copy-Item (Join-Path $ProdDir 'wait-for-db.sh')          $Staging

# 从暂存目录打包，保证 tar 内是相对路径（bin/、admin-web/、*.conf ...）
& tar -czf $Payload -C $Staging .
if ($LASTEXITCODE -ne 0) { Die 'tar 打包失败' }

$sizeMB = [math]::Round((Get-Item $Payload).Length / 1MB, 2)
Write-Host "上传包大小：$sizeMB MB"

# ---------------------------------------------------------------------------
# 3. 上传
# ---------------------------------------------------------------------------
Step '上传到服务器 /tmp/jizhang-payload.tgz'
& scp $Payload "${SshTarget}:/tmp/jizhang-payload.tgz"
if ($LASTEXITCODE -ne 0) { Die 'scp 上传失败' }

# ---------------------------------------------------------------------------
# 4. 解包 + 初始化
# ---------------------------------------------------------------------------
Step '解包并执行 provision.sh'

# 先 bash -n 做语法检查，脚本本身有问题时立刻失败，而不是跑到一半把服务器改坏。
#
# 全部命令加 sudo -n：/opt/jizhang 属于 root，而 ssh 登录用户通常不是 root
# （见 ~/.ssh/config 的 User），不加会报 “Cannot open: Permission denied”；
# 对直接以 root 登录的场景 sudo -n 同样可用。
#
# ⚠️ 这里刻意不写任何引号与 $：Windows 的 ssh.exe 会按 CRT 规则重解析参数，
# 双引号会被吃掉，远端 shell 就会报 unexpected EOF（单引号同理易出错）。
$remote = 'set -e; ' +
          'sudo -n mkdir -p /opt/jizhang; ' +
          'sudo -n tar -xzf /tmp/jizhang-payload.tgz -C /opt/jizhang; ' +
          'sudo -n bash -n /opt/jizhang/provision.sh && sudo -n bash -n /opt/jizhang/wait-for-db.sh; ' +
          'sudo -n bash /opt/jizhang/provision.sh; ' +
          'rm -f /tmp/jizhang-payload.tgz'

& ssh $SshTarget $remote
if ($LASTEXITCODE -ne 0) { Die '服务器初始化失败，按上面的提示看日志' }

# ---------------------------------------------------------------------------
# 5. 收尾
# ---------------------------------------------------------------------------
if (Test-Path $Staging) { Remove-Item $Staging -Recurse -Force }
if (Test-Path $Payload) { Remove-Item $Payload -Force }

Write-Host "`n部署完成。" -ForegroundColor Green
Write-Host "Android 重新打包时指定后端地址："
Write-Host ("  cd android; .\gradlew.bat assembleDebug -PapiBaseUrl=http://<公网IP>/api/v1/") -ForegroundColor Cyan
