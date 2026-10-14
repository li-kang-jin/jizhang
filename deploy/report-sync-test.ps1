# ============================================================================
# 报表（/app/reports/*）与同步（/app/sync/*）接口验证脚本
# 前置：应用已启动（默认 http://localhost:8080）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File deploy/report-sync-test.ps1
# ============================================================================
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$base = 'http://localhost:8080/api/v1'
$username = "synctest$([DateTime]::Now.ToString('HHmmss'))"
$password = '123456'
$deviceId = 'android-test-001'

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{
        Uri         = "$base$Path"
        Method      = $Method
        Headers     = $headers
        ContentType = 'application/json; charset=utf-8'
    }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 8 -Compress }
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    Invoke-RestMethod @params
}

function Step($text) { Write-Host "`n== $text ==" -ForegroundColor Cyan }

function Iso([datetime]$d) { $d.ToString('yyyy-MM-ddTHH:mm:ssZ') }

Step '1. 注册并登录测试账号'
$null = Invoke-Api POST '/auth/register' @{ username = $username; password = $password; nickname = '报表同步测试' }
$login = Invoke-Api POST '/auth/login' @{ username = $username; password = $password }
$token = $login.data.accessToken
"账号=$username 登录code=$($login.code)"

Step '2. 准备基础数据'
$bookId = (Invoke-Api GET '/app/books' $null $token).data[0].id
$accounts = (Invoke-Api GET "/app/accounts?bookId=$bookId" $null $token).data
$accountId = $accounts[0].id
$expenseCategories = (Invoke-Api GET "/app/categories?bookId=$bookId&type=1" $null $token).data
$incomeCategories = (Invoke-Api GET "/app/categories?bookId=$bookId&type=2" $null $token).data
"账本=$bookId 账户=$($accounts.Count) 支出分类=$($expenseCategories.Count) 收入分类=$($incomeCategories.Count)"

Step '3. 造数据：本月 2 笔支出 + 1 笔收入，上月 1 笔支出'
$now = (Get-Date).ToUniversalTime()
$batch = @(
    @{ bookId = $bookId; type = 1; amount = 3000; accountId = $accountId; categoryId = $expenseCategories[0].id; tradeTime = (Iso $now); remark = '报表-餐饮' },
    @{ bookId = $bookId; type = 1; amount = 5000; accountId = $accountId; categoryId = $expenseCategories[1].id; tradeTime = (Iso $now.AddDays(-1)); remark = '报表-交通' },
    @{ bookId = $bookId; type = 2; amount = 200000; accountId = $accountId; categoryId = $incomeCategories[0].id; tradeTime = (Iso $now); remark = '报表-工资' },
    @{ bookId = $bookId; type = 1; amount = 8000; accountId = $accountId; categoryId = $expenseCategories[0].id; tradeTime = (Iso $now.AddMonths(-1)); remark = '上月餐饮' }
)
$created = Invoke-Api POST '/app/transactions/batch' $batch $token
"写入 $($created.data.Count) 笔交易"

Step '4. 报表 - 收支总览'
$overview = Invoke-Api GET "/app/reports/overview?bookId=$bookId" $null $token
"month=$($overview.data.month) 收入=$($overview.data.income) 支出=$($overview.data.expense) 结余=$($overview.data.balance) 支出环比=$($overview.data.expenseRatio)"

Step '5. 报表 - 分类占比（支出）'
$category = Invoke-Api GET "/app/reports/category?bookId=$bookId&type=1" $null $token
"合计=$($category.data.total) 分, 分类数=$($category.data.items.Count)"
foreach ($item in $category.data.items) {
    "  {0,-6} {1,7} 分  占比 {2}%" -f $item.categoryName, $item.amount, [math]::Round($item.ratio * 100, 1)
}

Step '6. 报表 - 收支趋势（日 / 月）'
$trendDay = Invoke-Api GET "/app/reports/trend?bookId=$bookId&granularity=day" $null $token
$lastDay = $trendDay.data[-1]
"日粒度: 点数=$($trendDay.data.Count) 末点=$($lastDay.period) 支出=$($lastDay.expense)"
$trendMonth = Invoke-Api GET "/app/reports/trend?bookId=$bookId&granularity=month" $null $token
$lastMonth = $trendMonth.data[-1]
"月粒度: 点数=$($trendMonth.data.Count) 末点=$($lastMonth.period) 收入=$($lastMonth.income) 支出=$($lastMonth.expense)"

Step '7. 报表 - 资产分布'
$assets = Invoke-Api GET "/app/reports/assets?bookId=$bookId" $null $token
"总资产=$($assets.data.totalAssets) 分, 账户数=$($assets.data.accounts.Count)"
foreach ($account in $assets.data.accounts) { "  $($account.name) 余额=$($account.balance) 分" }

Step '8. 同步 - 首次全量拉取'
$pull1 = Invoke-Api POST '/app/sync/pull' @{ deviceId = $deviceId; bookId = $bookId } $token
"交易=$($pull1.data.transactions.Count) 账户=$($pull1.data.accounts.Count) 分类=$($pull1.data.categories.Count) 账本=$($pull1.data.books.Count) hasMore=$($pull1.data.hasMore)"
$serverTime = $pull1.data.serverTime
"serverTime=$serverTime"

Step '9. 同步 - 推送一条离线记账'
# 用时间戳派生唯一客户端 ID：服务端按该 ID 幂等，若固定不变会命中历史遗留记录
# （那条记录属于上一次运行的用户，服务端会因跨账本而拒绝更新）
$clientTxId = ([DateTimeOffset]::Now.ToUnixTimeMilliseconds()) * 1000 + (Get-Random -Maximum 999)
$pushBody = @{
    deviceId     = $deviceId
    bookId       = $bookId
    transactions = @(
        @{
            id = $clientTxId; bookId = $bookId; type = 1; amount = 1234
            accountId = $accountId; categoryId = $expenseCategories[0].id
            tradeTime = (Iso $now); remark = '离线记账'; updatedAt = (Iso $now)
        }
    )
}
$push = Invoke-Api POST '/app/sync/push' $pushBody $token
"成功=$($push.data.successIds.Count) 冲突=$($push.data.conflicts.Count) successIds=$(($push.data.successIds) -join ',')"

Step '10. 同步 - 增量拉取（应包含刚推送的记录）'
$pull2 = Invoke-Api POST '/app/sync/pull' @{ deviceId = $deviceId; bookId = $bookId; lastSyncAt = $serverTime } $token
"增量交易数=$($pull2.data.transactions.Count)"
foreach ($tx in $pull2.data.transactions) { "  id=$($tx.id) 金额=$($tx.amount) 备注=$($tx.remark)" }

Step '11. 同步 - 冲突验证（用更早的时间戳推送同一条）'
$pushBody.transactions[0].amount = 9999
$pushBody.transactions[0].updatedAt = (Iso $now.AddHours(-2))
$conflictResult = Invoke-Api POST '/app/sync/push' $pushBody $token
"成功=$($conflictResult.data.successIds.Count) 冲突=$($conflictResult.data.conflicts.Count)"
if ($conflictResult.data.conflicts.Count -gt 0) {
    "服务端版本金额=$($conflictResult.data.conflicts[0].amount)（客户端推的是 9999，未被覆盖）"
}

Step '12. 同步 - 删除记录应出现在 deletedTransactionIds'
$deleteId = $created.data[0].id
$null = Invoke-Api DELETE "/app/transactions/$deleteId" $null $token
$pull3 = Invoke-Api POST '/app/sync/pull' @{ deviceId = $deviceId; bookId = $bookId } $token
"已删除交易数=$($pull3.data.deletedTransactionIds.Count)"
"删除的 ID 包含目标记录: $(($pull3.data.deletedTransactionIds -contains [long]$deleteId))"

Step '13. 同步 - 推送删除动作（模拟客户端离线删除）'
$deleteTargetId = $push.data.successIds[0]
"待删除 id=$deleteTargetId"
$deleteBody = @{
    deviceId              = $deviceId
    bookId                = $bookId
    transactions          = @()
    deletedTransactionIds = @($deleteTargetId)
}
$deletePush = Invoke-Api POST '/app/sync/push' $deleteBody $token
"推送删除结果: code=$($deletePush.code)"
$pull4 = Invoke-Api POST '/app/sync/pull' @{ deviceId = $deviceId; bookId = $bookId } $token
"已删除列表包含目标: $(($pull4.data.deletedTransactionIds -contains [long]$deleteTargetId))"
"nextCursor=$($pull4.data.nextCursor)"

Write-Host "`n报表与同步接口验证完成" -ForegroundColor Green
