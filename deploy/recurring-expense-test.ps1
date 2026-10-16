# ============================================================================
# 固定支出（/app/recurring-expenses）接口验证脚本
# 覆盖：保存 -> 列表 -> 同 ID 幂等更新 -> 参数兜底与校验 -> 同步拉取 ->
#       停用 -> 删除随同步下发 -> 跨账本越权
# 前置：应用已启动（默认 http://localhost:8080）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File deploy/recurring-expense-test.ps1
# ============================================================================
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$base = 'http://localhost:8080/api/v1'
$deviceId = 'android-recurring-test'

function Step([string]$msg) { Write-Host "`n$msg" -ForegroundColor Cyan }

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{
        Uri         = "$base$Path"
        Method      = $Method
        Headers     = $headers
        ContentType = 'application/json; charset=utf-8'
        TimeoutSec  = 15
    }
    if ($null -ne $Body) {
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress))
    }
    return Invoke-RestMethod @params
}

function Register-And-Login([string]$Name) {
    $null = Invoke-Api POST '/auth/register' @{ username = $Name; password = '123456' }
    $login = Invoke-Api POST '/auth/login' @{ username = $Name; password = '123456' }
    if (-not $login.data.accessToken) { throw "登录未返回 accessToken" }
    return $login.data.accessToken
}

$suffix = Get-Random -Minimum 100000 -Maximum 999999
$username = "recurring$suffix"
# 客户端 ID 生成规则：毫秒时间戳左移 20 位 + 20 位随机数（与 newClientId() 一致）
$rid = ([DateTimeOffset]::Now.ToUnixTimeMilliseconds()) * 1048576 + (Get-Random -Maximum 1048576)
$today = (Get-Date).ToString('yyyy-MM-dd')

Step '1. 注册并登录'
$token = Register-And-Login $username
"用户 $username 登录成功"

Step '2. 取默认账本与支出分类'
$books = Invoke-Api GET '/app/books' $null $token
$bookId = $books.data[0].id
$cats = Invoke-Api GET "/app/categories?bookId=$bookId" $null $token
$expense = @($cats.data | Where-Object { $_.type -eq 1 })
$catId = $expense[0].id
"账本=$($books.data[0].name) bookId=$bookId, 支出分类=$($expense[0].name) id=$catId"

Step '3. 保存固定支出：房租，每月 5 日，2500.00 元'
$saveBody = @{
    id          = $rid
    bookId      = $bookId
    name        = '房租'
    categoryId  = $catId
    freq        = 2
    dayOfMonth  = 5
    amount      = 250000
    startDate   = $today
    enabled     = $true
}
$saved = Invoke-Api POST '/app/recurring-expenses' $saveBody $token
"code=$($saved.code) 返回 id=$($saved.data)（应等于客户端传入的 $rid）"

Step '4. 列表校验'
$list = Invoke-Api GET "/app/recurring-expenses?bookId=$bookId" $null $token
$item = $list.data | Where-Object { [string]$_.id -eq [string]$rid }
"条目数=$(@($list.data).Count) 名称=$($item.name) 金额=$($item.amount) 频率=$($item.freq) 每月 $($item.dayOfMonth) 日 启用=$($item.enabled)"
"分类 id 回读=$($item.categoryId), 生效首日=$($item.startDate)"

Step '5. 同 ID 重复提交应视为更新，而不是新增'
$saveBody.name = '房租（已涨价）'
$saveBody.amount = 280000
$null = Invoke-Api POST '/app/recurring-expenses' $saveBody $token
$list2 = Invoke-Api GET "/app/recurring-expenses?bookId=$bookId" $null $token
$item2 = $list2.data | Where-Object { [string]$_.id -eq [string]$rid }
"条目数=$(@($list2.data).Count)（期望 1） 名称=$($item2.name) 金额=$($item2.amount)（期望 280000）"

Step '6. 参数兜底：freq=99 -> 每月，dayOfMonth=40 -> 31'
$saveBody.freq = 99
$saveBody.dayOfMonth = 40
$null = Invoke-Api POST '/app/recurring-expenses' $saveBody $token
$item3 = (Invoke-Api GET "/app/recurring-expenses?bookId=$bookId" $null $token).data |
    Where-Object { [string]$_.id -eq [string]$rid }
"freq=$($item3.freq)（期望 2） dayOfMonth=$($item3.dayOfMonth)（期望 31）"

Step '7. 参数校验：金额 0 与空名称应被拦截'
# 注意：全局异常处理统一返回 HTTP 200 + 业务 code，不能靠捕获异常判断
$badAmount = Invoke-Api POST '/app/recurring-expenses' @{
    id = $rid; bookId = $bookId; name = '测试'; amount = 0; startDate = $today
} $token
"金额 0: code=$($badAmount.code) message=$($badAmount.message)（期望 code=400）"
$badName = Invoke-Api POST '/app/recurring-expenses' @{
    id = $rid; bookId = $bookId; name = '   '; amount = 100; startDate = $today
} $token
"空名称: code=$($badName.code) message=$($badName.message)（期望 code=400）"

Step '8. 同步全量拉取应带回固定支出规则'
$pull1 = Invoke-Api POST '/app/sync/pull' @{ deviceId = $deviceId; bookId = $bookId } $token
$pulled = @($pull1.data.recurringExpenses)
$pulledItem = $pulled | Where-Object { [string]$_.id -eq [string]$rid }
"拉取到固定支出 $($pulled.Count) 条, 命中目标=$([bool]$pulledItem) 金额=$($pulledItem.amount)"
"nextCursor=$($pull1.data.nextCursor)"

Step '9. 停用后列表仍可见，且 enabled=false'
$saveBody.enabled = $false
$null = Invoke-Api POST '/app/recurring-expenses' $saveBody $token
$item4 = (Invoke-Api GET "/app/recurring-expenses?bookId=$bookId" $null $token).data |
    Where-Object { [string]$_.id -eq [string]$rid }
"enabled=$($item4.enabled)（期望 False）—— 停用只影响客户端的预算预留计算"

Step '10. 删除固定支出'
$null = Invoke-Api DELETE "/app/recurring-expenses/$rid?bookId=$bookId" $null $token
$afterDelete = @((Invoke-Api GET "/app/recurring-expenses?bookId=$bookId" $null $token).data |
    Where-Object { [string]$_.id -eq [string]$rid })
"删除后剩余命中条目=$($afterDelete.Count)（期望 0）"

Step '11. 增量拉取应下发删除标记'
$pull2 = Invoke-Api POST '/app/sync/pull' @{
    deviceId   = $deviceId
    bookId     = $bookId
    lastSyncAt = $pull1.data.nextCursor
} $token
$deletedIds = @($pull2.data.deletedRecurringExpenseIds | ForEach-Object { [string]$_ })
"deletedRecurringExpenseIds=$($deletedIds -join ',')"
"包含被删规则: $($deletedIds -contains [string]$rid)"

Step '12. 跨账本越权：他人向该账本保存应被拒绝'
$otherToken = Register-And-Login "other$suffix"
$denied = Invoke-Api POST '/app/recurring-expenses' @{
    id = ([DateTimeOffset]::Now.ToUnixTimeMilliseconds()) * 1048576
    bookId = $bookId; name = '越权测试'; amount = 100; startDate = $today
} $otherToken
"code=$($denied.code) message=$($denied.message)（期望 code=2002）"

Write-Host "`n固定支出模块验证完成" -ForegroundColor Green
