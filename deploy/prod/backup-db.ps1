<#
.SYNOPSIS
    把线上 PostgreSQL 导出并拉回本地（在 Windows 侧执行）。

.DESCRIPTION
    流程：ssh 到服务器 → 容器内 pg_dump -Fc（自带压缩）→ 落到服务器 /tmp →
    scp 拉回 backup/db/ → 校验 dump 结构 → 删远程临时文件 → 按份数清理本地旧备份。

    -IncludeEnv 额外把 /opt/jizhang/.env 一起拉回。这个文件里有数据库密码和 JWT 密钥，
    换机器/服务器到期重建时是必需项，丢了等于所有已登录用户 token 失效、库密码未知。

    安全约定：backup/ 已在 .gitignore 里；导出的 dump 含真实账单，不要提交、不要传公共网盘。
    建议再留一份到移动硬盘或加密网盘，只放本地等于没防「电脑坏掉」。

.PARAMETER SshTarget
    ~/.ssh/config 里的主机别名，默认 jz-prod。

.PARAMETER OutDir
    本地输出目录，默认仓库下的 backup/db。

.PARAMETER Keep
    本地保留最近 N 份 dump，默认 14。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy/prod/backup-db.ps1

.EXAMPLE
    # 连 .env 一起备份，保留 30 份
    powershell -ExecutionPolicy Bypass -File deploy/prod/backup-db.ps1 -IncludeEnv -Keep 30
#>
[CmdletBinding()]
param(
    [string]$SshTarget = 'jz-prod',
    [string]$OutDir,
    [int]$Keep = 14,
    [switch]$IncludeEnv
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $OutDir) { $OutDir = Join-Path $RepoRoot 'backup\db' }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$stamp      = Get-Date -Format 'yyyyMMdd-HHmmss'
$dumpName   = "jizhang-$stamp.dump"
$remoteDump = "/tmp/$dumpName"
$localDump  = Join-Path $OutDir $dumpName

function Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Green }
function Warn($msg) { Write-Host "[!] $msg" -ForegroundColor Yellow }
function Die($msg)  { Write-Host "[x] $msg" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------------------
# 0. 连通性
# ---------------------------------------------------------------------------
Step "检查 SSH 连接（$SshTarget）"
$probe = & ssh -o BatchMode=yes -o ConnectTimeout=10 $SshTarget 'echo OK' 2>&1
if ($LASTEXITCODE -ne 0 -or ($probe -join '') -notmatch 'OK') {
    Warn ($probe -join "`n")
    Die @"
连不上 $SshTarget。
若提示「无法将 ssh 项识别为 cmdlet」，说明用的是 32 位（x86）PowerShell：
OpenSSH 只装在 64 位的 C:\Windows\System32\OpenSSH，x86 进程看不到。
改用 Win+R 输入 powershell 打开 64 位窗口，或：
    & `$env:SystemRoot\Sysnative\OpenSSH\ssh.exe ...
"@
}
Write-Host 'SSH 正常'

# ---------------------------------------------------------------------------
# 1. 服务器上导出（-Fc 是自定义格式，自带压缩，恢复时可用 pg_restore 选择性还原）
# ---------------------------------------------------------------------------
Step '导出数据库'
& ssh $SshTarget "sudo -n docker exec jizhang-postgres pg_dump -U jizhang -d jizhang -Fc -Z 9 | sudo -n tee $remoteDump >/dev/null && sudo -n chmod 644 $remoteDump"
if ($LASTEXITCODE -ne 0) { Die 'pg_dump 失败，看上面的报错' }

$sizeText = ((& ssh $SshTarget "sudo -n stat -c %s $remoteDump" 2>&1) -join '').Trim()
$size = [int64]0
[void][int64]::TryParse($sizeText, [ref]$size)
if ($size -lt 4096) { Die "导出文件异常（$size 字节），放弃本次备份" }
Write-Host ("远程 dump 大小: {0:N1} KB" -f ($size / 1KB))

# ---------------------------------------------------------------------------
# 2. 校验：能被 pg_restore 读出目录才算有效备份，只看文件大小不够
# ---------------------------------------------------------------------------
Step '校验 dump 结构'
$verify = ((& ssh $SshTarget "sudo -n docker cp $remoteDump jizhang-postgres:/tmp/verify.dump >/dev/null && sudo -n docker exec jizhang-postgres pg_restore -l /tmp/verify.dump | wc -l; sudo -n docker exec jizhang-postgres rm -f /tmp/verify.dump" 2>&1) -join '').Trim()
$entries = 0
[void][int]::TryParse($verify, [ref]$entries)
if ($entries -lt 10) { Die "pg_restore 读不出 dump 目录（返回 $verify），备份不可用" }
Write-Host "dump 目录条目: $entries"

# ---------------------------------------------------------------------------
# 3. 拉回本地
# ---------------------------------------------------------------------------
Step '拉回本地'
& scp -o BatchMode=yes "${SshTarget}:$remoteDump" $localDump
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $localDump)) { Die 'scp 失败' }
Write-Host "本地文件: $localDump"

# ---------------------------------------------------------------------------
# 4. 可选：.env（服务器重建的必需品）
# ---------------------------------------------------------------------------
if ($IncludeEnv) {
    Step '拉回 .env'
    & ssh $SshTarget 'sudo -n install -m 644 -o ubuntu /opt/jizhang/.env /tmp/jizhang-env.txt'
    if ($LASTEXITCODE -eq 0) {
        $envPath = Join-Path $OutDir "jizhang-env-$stamp.txt"
        & scp -o BatchMode=yes "${SshTarget}:/tmp/jizhang-env.txt" $envPath
        Write-Host "本地文件: $envPath"
        Warn '.env 含数据库密码与 JWT 密钥，别外传；丢了这个文件，重建后所有用户需重新登录'
    } else {
        Warn '读取 /opt/jizhang/.env 失败，跳过'
    }
}

# ---------------------------------------------------------------------------
# 5. 清理远程临时文件
# ---------------------------------------------------------------------------
Step '清理远程临时文件'
& ssh $SshTarget "sudo -n rm -f $remoteDump /tmp/jizhang-env.txt" | Out-Null

# ---------------------------------------------------------------------------
# 6. 保留策略：dump 留 $Keep 份，.env 只留最近 2 份（内容几乎不变，多留没意义）
# ---------------------------------------------------------------------------
Step "清理本地旧备份（保留最近 $Keep 份）"
$oldDumps = @(Get-ChildItem $OutDir -Filter 'jizhang-*.dump' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -Skip $Keep)
$oldEnvs = @(Get-ChildItem $OutDir -Filter 'jizhang-env-*.txt' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -Skip 2)
foreach ($f in ($oldDumps + $oldEnvs)) {
    if ($f -and $f.FullName -ne $localDump) {
        Remove-Item $f.FullName -Force
        Warn "已删除旧备份 $($f.Name)"
    }
}

Step '备份完成'
Write-Host "本地备份: $localDump"
Write-Host '提醒：定期把该目录复制到移动硬盘或其他机器，单机保存挡不住硬盘损坏。'
