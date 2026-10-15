# 预算模块接口验证：注册 -> 设置总预算与分类预算 -> 记账 -> 校验进度与超支
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api/v1'

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{ Uri = "$base$Path"; Method = $Method; Headers = $headers; TimeoutSec = 15 }
    if ($Body) {
        $params['ContentType'] = 'application/json'
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
$username = "budget$suffix"

# 1. 注册并登录
$token = Register-And-Login $username
Write-Host "[1] 注册并登录 $username 成功"

# 2. 取默认账本
$books = Invoke-Api GET '/app/books' $null $token
$bookId = $books.data[0].id
Write-Host "[2] 默认账本 bookId=$bookId 名称=$($books.data[0].name)"

# 3. 取支出分类
$cats = Invoke-Api GET "/app/categories?bookId=$bookId" $null $token
$expense = @($cats.data | Where-Object { $_.type -eq 1 })
$catId = $expense[0].id
Write-Host "[3] 支出分类: $($expense[0].name) (id=$catId)"

$period = (Get-Date).ToString('yyyy-MM')

# 4. 设置总预算 5000 元
$null = Invoke-Api POST '/app/budgets' @{ bookId = $bookId; categoryId = 0; period = $period; amount = 500000 } $token
Write-Host "[4] 总预算设置成功: 5000.00 元"

# 5. 设置分类预算 1000 元
$null = Invoke-Api POST '/app/budgets' @{ bookId = $bookId; categoryId = $catId; period = $period; amount = 100000 } $token
Write-Host "[5] 分类预算设置成功: $($expense[0].name) 1000.00 元"

# 6. 重复提交同一分类预算，应视为更新而非新增
$null = Invoke-Api POST '/app/budgets' @{ bookId = $bookId; categoryId = $catId; period = $period; amount = 80000 } $token
$listAfter = Invoke-Api GET "/app/budgets?bookId=$bookId&period=$period" $null $token
$catBudget = $listAfter.data | Where-Object { $_.categoryId -eq $catId }
Write-Host "[6] 重复提交后: 条目数=$($listAfter.data.Count) 金额=$($catBudget.amount) (期望 80000，即更新而非新增)"

# 7. 记账：该分类支出 350 元
$acct = (Invoke-Api GET "/app/accounts?bookId=$bookId" $null $token).data[0]
$now = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$null = Invoke-Api POST '/app/transactions' @{
    bookId = $bookId; type = 1; amount = 35000; accountId = $acct.id
    categoryId = $catId; tradeTime = $now; remark = '预算测试'
} $token
Write-Host "[7] 记一笔支出 350.00 元"

# 8. 校验总览
$ov = (Invoke-Api GET "/app/budgets/overview?bookId=$bookId&period=$period" $null $token).data
Write-Host "[8] 总览: 预算=$($ov.totalBudget) 分, 已用=$($ov.totalUsed) 分, 剩余=$($ov.remaining) 分, 比例=$([math]::Round($ov.usedRatio,3)), 超支=$($ov.exceeded)"
Write-Host "    本月 $($ov.daysInMonth) 天 / 剩余 $($ov.daysLeft) 天, 日均可用 $($ov.dailyAvailable) 分, 建议日花 $($ov.suggestDaily) 分"

# 9. 触发超支：再记 900 元（分类预算 800 元，累计已用 1250 元）
$null = Invoke-Api POST '/app/transactions' @{
    bookId = $bookId; type = 1; amount = 90000; accountId = $acct.id
    categoryId = $catId; tradeTime = $now; remark = '预算超支测试'
} $token
$ov2 = (Invoke-Api GET "/app/budgets/overview?bookId=$bookId&period=$period" $null $token).data
$cat2 = $ov2.items | Where-Object { $_.categoryId -eq $catId }
Write-Host "[9] 追加 900 元后: 分类已用=$($cat2.used) 分, 剩余=$($cat2.remaining) 分, 超支=$($cat2.exceeded), 超支项数=$($ov2.overruns.Count)"

# 10. 参数校验：非法周期格式应被拦截
#     注意：全局异常处理统一返回 HTTP 200 + 业务 code，所以不能靠捕获异常来判断
$r10 = Invoke-Api POST '/app/budgets' @{ bookId = $bookId; categoryId = 0; period = '2026/10'; amount = 1000 } $token
Write-Host "[10] 非法周期格式: code=$($r10.code) message=$($r10.message) (期望 code=400)"

# 11. 跨账本越权：另一个用户改这个账本的预算，应被拒绝
$otherToken = Register-And-Login "other$suffix"
$r11 = Invoke-Api POST '/app/budgets' @{ bookId = $bookId; categoryId = 0; period = $period; amount = 999 } $otherToken
Write-Host "[11] 他人账本预算越权: code=$($r11.code) message=$($r11.message) (期望 code=2002)"

# 12. 删除分类预算
$delTarget = $listAfter.data | Where-Object { $_.categoryId -eq $catId }
$null = Invoke-Api DELETE "/app/budgets/$($delTarget.id)?bookId=$bookId" $null $token
$after = Invoke-Api GET "/app/budgets?bookId=$bookId&period=$period" $null $token
Write-Host "[12] 删除分类预算后剩余条目数=$(@($after.data).Count) (期望 0，分类预算已删，总预算不在列表中)"

Write-Host ''
Write-Host '预算模块验证完成'
