# ============================================================================
# 记账系统 后端接口冒烟测试
# 前置：应用已启动（默认 http://localhost:8080），数据库可用
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File deploy/smoke-test.ps1
# ============================================================================
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$base = 'http://localhost:8080/api/v1'
$username = "demo$([DateTime]::Now.ToString('HHmmss'))"
$password = '123456'

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

Step '1. 注册（自动创建默认账本/账户/分类）'
$reg = Invoke-Api POST '/auth/register' @{ username = $username; password = $password; nickname = '演示账号' }
"code=$($reg.code) userId=$($reg.data.id) username=$($reg.data.username)"

Step '2. 登录'
$login = Invoke-Api POST '/auth/login' @{ username = $username; password = $password }
$token = $login.data.accessToken
$refresh = $login.data.refreshToken
"code=$($login.code) accessToken长度=$($token.Length) expiresIn=$($login.data.expiresIn)"

Step '3. 当前用户 /auth/me'
$me = Invoke-Api GET '/auth/me' $null $token
"code=$($me.code) nickname=$($me.data.nickname)"

Step '4. 默认账本列表'
$books = Invoke-Api GET '/app/books' $null $token
$bookId = $books.data[0].id
"账本数=$($books.data.Count) 首个=$($books.data[0].name) id=$bookId myRole=$($books.data[0].myRole)"

Step '5. 默认账户'
$accounts = Invoke-Api GET "/app/accounts?bookId=$bookId" $null $token
$accountId = $accounts.data[0].id
foreach ($a in $accounts.data) { "  $($a.name) type=$($a.type) 余额=$($a.balance)分" }

Step '6. 默认支出分类树'
$categories = Invoke-Api GET "/app/categories?bookId=$bookId&type=1" $null $token
$categoryId = $categories.data[0].id
"支出分类数=$($categories.data.Count) 首个=$($categories.data[0].name)"

Step '7. 新增一笔支出 35.00 元'
$tradeTime = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$tx = Invoke-Api POST '/app/transactions' @{
    bookId = $bookId; type = 1; amount = 3500; accountId = $accountId
    categoryId = $categoryId; tradeTime = $tradeTime; remark = '午餐'
} $token
$txId = $tx.data.id
"code=$($tx.code) id=$txId amount=$($tx.data.amount) tradeTime=$($tx.data.tradeTime)"

Step '8. 校验账户余额联动（期望 -3500 分）'
$accounts2 = Invoke-Api GET "/app/accounts?bookId=$bookId" $null $token
"实际余额 = $(($accounts2.data | Where-Object { $_.id -eq $accountId }).balance)"

Step '9. 分页查询交易'
$page = Invoke-Api GET "/app/transactions?bookId=$bookId&page=1&size=10" $null $token
"code=$($page.code) total=$($page.data.total) 首条备注=$($page.data.records[0].remark) 金额=$($page.data.records[0].amount)"

Step '10. 修改交易为 50.00 元（期望余额 -5000 分）'
$null = Invoke-Api PUT "/app/transactions/$txId" @{
    bookId = $bookId; type = 1; amount = 5000; accountId = $accountId
    categoryId = $categoryId; tradeTime = $tradeTime; remark = '午餐改'
} $token
$accounts3 = Invoke-Api GET "/app/accounts?bookId=$bookId" $null $token
"实际余额 = $(($accounts3.data | Where-Object { $_.id -eq $accountId }).balance)"

Step '11. 删除交易（期望余额回到 0）'
$null = Invoke-Api DELETE "/app/transactions/$txId" $null $token
$accounts4 = Invoke-Api GET "/app/accounts?bookId=$bookId" $null $token
"实际余额 = $(($accounts4.data | Where-Object { $_.id -eq $accountId }).balance)"

Step '12. 登出后旧 token 应失效（后端返回标准 HTTP 401）'
$null = Invoke-Api POST '/auth/logout' $null $token
try {
    $after = Invoke-Api GET '/auth/me' $null $token
    "未按预期返回 401: code=$($after.code) message=$($after.message)"
} catch {
    "旧 token 已被拒绝（HTTP $([int]$_.Exception.Response.StatusCode)）"
}

Step '13. 用 refreshToken 换取新令牌'
$refreshed = Invoke-Api POST '/auth/refresh' @{ refreshToken = $refresh }
"code=$($refreshed.code) 新accessToken长度=$($refreshed.data.accessToken.Length)"

Write-Host "`n全部接口验证完成" -ForegroundColor Green
