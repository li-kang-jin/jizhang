# 记账系统 - 接口契约

> 版本：v1.0
> Base URL：`http://localhost:8080/api/v1`
> 更新日期：2026-10-14

## 1. 通用约定

### 1.1 请求头

| Header | 说明 |
| --- | --- |
| `Authorization` | `Bearer <accessToken>`，除登录/注册外必填 |
| `Content-Type` | `application/json;charset=UTF-8` |
| `X-Device-Id` | 客户端设备唯一标识（Android 必填，用于同步） |

### 1.2 数据类型约定

| 类型 | 约定 |
| --- | --- |
| 金额 | 整数，单位**分**（`amount: 12345` = 123.45 元） |
| 时间 | ISO 8601 UTC，如 `2026-10-14T08:00:00Z` |
| ID | 字符串传输（避免 JS 精度丢失），如 `"id": "1836745123456789"` |
| 枚举 | 整数 |

### 1.3 统一响应体

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "a1b2c3d4e5f6"
}
```

### 1.4 分页响应体

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "total": 100,
    "page": 1,
    "size": 20
  },
  "traceId": "a1b2c3d4e5f6"
}
```

### 1.5 错误码

| code | 说明 |
| --- | --- |
| 0 | 成功 |
| 400 | 参数校验失败 |
| 401 | 未登录 / Token 失效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 数据冲突（同步冲突） |
| 500 | 服务器内部错误 |
| 1001 | 用户名已存在 |
| 1002 | 用户名或密码错误 |
| 1003 | 账号已被禁用 |
| 1004 | Token 已失效，请重新登录 |
| 1006 | 管理员仍在使用初始密码，需先修改（除 `/admin/auth/**` 外的管理接口均返回此码） |
| 2001 | 账本不存在 |
| 2002 | 无该账本访问权限 |
| 2003 | 邀请码无效或已过期 |
| 2004 | 你已经是该账本成员 |
| 2005 | 该成员不存在 |
| 3001 | 账户不存在 |
| 3002 | 账户余额不足 |
| 4001 | 分类不存在 |
| 5001 | 交易记录不存在 |

---

## 2. 认证模块 `/auth`

### 2.1 注册

`POST /auth/register`

```json
{ "username": "zhangsan", "password": "123456", "nickname": "张三" }
```

响应 `data`：

```json
{ "userId": "1836745123456789", "username": "zhangsan", "nickname": "张三" }
```

### 2.2 登录

`POST /auth/login`

```json
{ "username": "zhangsan", "password": "123456" }
```

响应 `data`：

```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "expiresIn": 7200,
  "user": { "id": "1836745123456789", "username": "zhangsan", "nickname": "张三", "avatar": null }
}
```

### 2.3 刷新 Token

`POST /auth/refresh`

```json
{ "refreshToken": "eyJhbGciOi..." }
```

### 2.4 登出

`POST /auth/logout` —— 将当前 accessToken 加入 Redis 黑名单。

### 2.5 当前用户

`GET /auth/me` → 返回当前登录用户信息。

---

## 3. 账本模块 `/app/books`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/app/books` | 我的账本列表（含 `type` / `myRole` / `memberCount` / `personal`） |
| POST | `/app/books` | 创建账本 |
| GET | `/app/books/{id}` | 账本详情 |
| PUT | `/app/books/{id}` | 修改账本 |
| DELETE | `/app/books/{id}` | 删除账本（软删，仅所有者） |
| GET | `/app/books/{id}/members` | 成员列表 |
| POST | `/app/books/{id}/members` | 直接按 userId 添加成员（仅所有者） |
| PUT | `/app/books/{id}/members/{userId}` | 修改成员角色 / 家庭内称呼（仅所有者） |
| DELETE | `/app/books/{id}/members/{userId}` | 移除成员（仅所有者） |
| GET | `/app/books/{id}/invites` | 有效邀请码列表（仅所有者） |
| POST | `/app/books/{id}/invites` | 生成邀请码（仅所有者） |
| DELETE | `/app/books/{id}/invites/{code}` | 作废邀请码（仅所有者） |
| POST | `/app/books/join` | **凭邀请码加入账本** |

创建请求：

```json
{ "name": "家庭账本", "currency": "CNY", "type": 1, "icon": "home", "remark": "" }
```

> `type`：`0=个人`（仅自己可见）、`1=家庭/共享`（成员互相可见）。
> **个人账本不能生成邀请码**，服务端返回 `400`：`个人账本不能邀请成员，请先创建家庭账本`。

账本列表响应 `data`：

```json
[
  {
    "id": "1836745123456789",
    "name": "我的账本",
    "type": 0,
    "ownerId": "1836745100000001",
    "myRole": 2,
    "memberCount": 1,
    "personal": true
  }
]
```

### 3.1 生成邀请码

`POST /app/books/{id}/invites`

```json
{ "role": 1, "expireDays": 7, "maxUses": 1 }
```

`expireDays = 0` 表示永不过期，`maxUses = 0` 表示不限次数。响应 `data`：

```json
{ "code": "FAM8K3QA", "role": 1, "expireAt": "2026-10-22T08:00:00Z", "maxUses": 1, "usedCount": 0 }
```

### 3.2 凭邀请码加入

`POST /app/books/join`

```json
{ "code": "fam8k3qa" }
```

响应 `data` 为加入后的账本（同账本对象）。失败时 `code = 2003 邀请码无效或已过期`。
已是成员时幂等返回，不消耗邀请码次数。

### 3.3 成员

成员列表响应 `data`：

```json
[
  {
    "userId": "1836745100000001",
    "username": "zhangsan",
    "nickname": "张三",
    "alias": "爸爸",
    "displayName": "爸爸",
    "role": 2,
    "owner": true,
    "joinedAt": "2026-10-14T08:00:00Z"
  }
]
```

> `displayName` 由服务端算好：优先 `alias`（家庭内称呼），其次 `nickname`，最后 `username`。
> **家庭账本内 `role >= 1` 的成员可以增删改任意交易**（家庭内部互信），操作留痕在 `updated_by`；
> `role = 0` 为只读。

修改成员：`PUT /app/books/{id}/members/{userId}`

```json
{ "role": 2, "alias": "妈妈" }
```

两字段均可为空表示不修改；`alias` 传空串表示清除称呼。不能修改所有者的角色。

---

## 4. 账户模块 `/app/accounts`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/app/accounts?bookId=xxx` | 账户列表（含余额、总资产） |
| POST | `/app/accounts` | 创建账户 |
| PUT | `/app/accounts/{id}` | 修改账户 |
| DELETE | `/app/accounts/{id}` | 删除账户 |

创建请求：

```json
{
  "bookId": "1836745123456789",
  "name": "招商银行储蓄卡",
  "type": 2,
  "initialBalance": 1000000,
  "currency": "CNY",
  "icon": "bank",
  "includeInTotal": 1
}
```

---

## 5. 分类模块 `/app/categories`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/app/categories?bookId=xxx&type=1` | 分类列表（树形，type 可空） |
| POST | `/app/categories` | 新增分类 |
| PUT | `/app/categories/{id}` | 修改分类 |
| DELETE | `/app/categories/{id}` | 删除分类（有交易引用时拒绝） |

---

## 6. 交易模块 `/app/transactions`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/app/transactions` | 分页查询 |
| POST | `/app/transactions` | 新增交易 |
| GET | `/app/transactions/{id}` | 交易详情 |
| PUT | `/app/transactions/{id}` | 修改交易 |
| DELETE | `/app/transactions/{id}` | 删除交易 |
| POST | `/app/transactions/batch` | 批量新增（导入用） |

查询参数：

| 参数 | 说明 |
| --- | --- |
| `bookId` | 账本 ID，必填 |
| `page` / `size` | 分页，默认 1 / 20 |
| `type` | 1=支出 2=收入 3=转账，可空 |
| `accountId` / `categoryId` | 可空 |
| `startTime` / `endTime` | 时间区间 |
| `keyword` | 备注模糊搜索 |
| `minAmount` / `maxAmount` | 金额区间（分） |

新增请求（支出示例）：

```json
{
  "id": "1836745123456790",
  "bookId": "1836745123456789",
  "type": 1,
  "amount": 3500,
  "accountId": "1836745100000001",
  "categoryId": "1836745100000002",
  "tradeTime": "2026-10-14T04:30:00Z",
  "remark": "午餐",
  "images": [],
  "tags": ["工作餐"]
}
```

转账（`type=3`）时 `amount` 为转出金额，`toAccountId` 必填，`categoryId` 忽略。

响应中的 `id` 均以字符串返回，并额外带回 **`createdBy`（记账人用户 ID）**，
家庭账本用它展示「谁记的」以及做按成员统计。

---

## 7. 统计报表模块 `/app/reports`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/app/reports/overview?bookId=&month=2026-10` | 总览：本月收入/支出/结余、环比 |
| GET | `/app/reports/category?bookId=&type=1&startTime=&endTime=` | 分类占比（饼图） |
| GET | `/app/reports/trend?bookId=&granularity=day&startTime=&endTime=` | 收支趋势（折线图） |
| GET | `/app/reports/assets?bookId=` | 账户资产分布（柱状/饼图） |
| GET | `/app/reports/member?bookId=&startTime=&endTime=` | **按成员统计收支**（家庭账本「谁花了多少」） |

`overview` 响应：

```json
{
  "month": "2026-10",
  "income": 1500000,
  "expense": 823400,
  "balance": 676600,
  "incomeRatio": 0.12,
  "expenseRatio": -0.05
}
```

`category` 响应：

```json
{
  "total": 823400,
  "items": [
    { "categoryId": "1836745100000002", "categoryName": "餐饮", "icon": "food", "color": "#FF7043", "amount": 320000, "ratio": 0.3887 }
  ]
}
```

`member` 响应（按交易的记账人 `created_by` 聚合，转账不计入）：

```json
{
  "totalIncome": 1500000,
  "totalExpense": 823400,
  "items": [
    {
      "userId": "1836745100000001",
      "nickname": "张三",
      "alias": "爸爸",
      "displayName": "爸爸",
      "income": 1500000,
      "expense": 520000,
      "balance": 980000,
      "count": 42
    }
  ]
}
```

> 客户端同样会把这套统计**在本地算一遍**（交易上的 `createdBy` + 缓存的成员名单），
> 因此断网、未登录也能看到「谁花了多少」。

---

## 8. 预算与固定支出

### 8.1 预算 `/app/budgets`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/app/budgets` | 指定周期的分类预算列表（含已用 / 剩余 / 超支） |
| GET | `/app/budgets/overview` | 总预算执行情况、每日可花与预测、超支明细 |
| POST | `/app/budgets` | 保存预算（同账本 + 分类 + 周期视为更新；`categoryId=0` 表示总预算） |
| DELETE | `/app/budgets/{id}` | 删除预算 |

周期支持两种传法：`cycle + periodStart`（推荐，月 / 周 / 年通用）与 `period=yyyy-MM`
（仅兼容旧客户端）。`cycle`：`1=每月 2=每周 3=每年`。

> 客户端的预算**读**走本地 Room 实时聚合，只有**写**走这组接口；跨设备 / 家庭成员的
> 额度变更由同步拉取带回（见第 9 章）。

### 8.2 固定支出 `/app/recurring-expenses`

房租、房贷、保险、会员费这类每期都要花的钱。**只登记规则，不生成账单**。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/app/recurring-expenses?bookId=` | 全部规则（含已停用） |
| POST | `/app/recurring-expenses` | 保存规则；`id` 由客户端生成，同 id 重复提交视为修改 |
| DELETE | `/app/recurring-expenses/{id}?bookId=` | 删除规则（软删除，删除动作随同步下发） |

请求体：

```json
{
  "id": "1836745123456790",
  "bookId": "1836745123456789",
  "name": "房租",
  "categoryId": "0",
  "freq": 2,
  "dayOfWeek": 1,
  "dayOfMonth": 5,
  "monthOfYear": 1,
  "amount": 250000,
  "startDate": "2026-10-01",
  "endDate": null,
  "enabled": true
}
```

- `freq`：`1=每周 2=每月 3=每年`；`dayOfWeek`：`1=周一 … 7=周日`；
  `monthOfYear`：`1-12`；每月发生日超出当月天数时按当月最后一天（31 号在小月按 30 号）。
- `id` 由客户端生成（毫秒时间戳左移 20 位 + 20 位随机数）。同名固定支出允许存在多条，
  没有可用的业务键，幂等只能落在客户端 ID 上，服务端**不重新分配 ID**。
- `status`：停用（`enabled=false`）的规则不参与预留。

> **预留金额不在服务端算**。它依赖客户端的本地时区与本地流水，由客户端按
> `[今天, 本周期末)` 内的发生日实时求出，从预算的「每天可花」里扣掉 ——
> 所以服务端的预算总览里没有 `fixedReserved` 字段。这条通路只保证规则本身多端一致。

---

## 9. 多端同步模块 `/app/sync`

### 8.1 增量拉取

`POST /app/sync/pull`

```json
{ "deviceId": "android-abc123", "lastSyncAt": "2026-10-14T00:00:00Z", "bookId": "1836745123456789" }
```

响应 `data`：

```json
{
  "serverTime": "2026-10-14T08:00:00Z",
  "nextCursor": "2026-10-14T08:00:00Z",
  "accounts": [],
  "categories": [],
  "transactions": [],
  "budgets": [],
  "recurringExpenses": [],
  "deletedTransactions": ["1836745123456799"],
  "deletedBudgetIds": [],
  "deletedRecurringExpenseIds": []
}
```

> 已软删除的记录会出现在对应集合中并带 `"deleted": 1`，客户端据此删除本地数据。

### 8.2 批量推送

`POST /app/sync/push`

```json
{
  "deviceId": "android-abc123",
  "transactions": [
    { "id": "1836745123456790", "bookId": "...", "type": 1, "amount": 3500, "tradeTime": "2026-10-14T04:30:00Z" }
  ]
}
```

响应 `data`：

```json
{
  "successIds": ["1836745123456790"],
  "conflicts": [],
  "serverTime": "2026-10-14T08:00:00Z"
}
```

冲突策略 V1：`last-write-wins`，以 `updated_at` 较新者为准，冲突记录在 `conflicts` 中返回服务端版本。

---

## 10. 管理后台 `/admin`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/admin/auth/login` | 管理员登录 |
| GET | `/admin/users` | 用户列表（分页、搜索） |
| PUT | `/admin/users/{id}/status` | 启用/禁用用户 |
| GET | `/admin/books` | 账本列表（带所有者名称、成员数、账户数） |
| GET | `/admin/accounts` | 账户列表（分页，可按账本 / 所有者 / 类型 / 名称筛选，带账本名与所有者） |
| POST | `/admin/accounts` | 创建账户（请求体同 `/app/accounts` 的创建） |
| PUT | `/admin/accounts/{id}` | 修改账户（不允许改动所属账本，`bookId` 被忽略） |
| DELETE | `/admin/accounts/{id}` | 删除账户（账户下仍有流水时拒绝，code=3003） |
| GET | `/admin/transactions` | 交易列表（可按 `accountId` / `bookId` / `userId` 筛选，转出/转入都算） |
| GET | `/admin/transactions/summary` | 交易合计（与列表同一套筛选条件，统计整个结果集而非当前页） |
| GET | `/admin/statistics/overview` | 平台总览（用户数、账本数、今日活跃账本、收支金额，转账不计入收支） |
| GET | `/admin/statistics/trend` | 平台增长趋势（新增用户、交易笔数、收支金额） |

> 管理后台接口需要管理员角色（`role = ADMIN`），与 C 端 Token 隔离。
> 它是**运营后台**（管用户 / 账本 / 账户 / 交易 / 平台指标），不承载预算、固定支出这类业务功能。
> 管理员登录时如果仍在用初始密码 `admin123456`，响应里的 `user.mustChangePassword` 为 1，
> 此时除 `/admin/auth/**` 外的管理接口都返回业务码 1006，前端应强制跳转改密页。
> 禁用用户、重置密码、修改密码都会自增 `t_user.token_version`，已签发的 accessToken 立即返回 401，
> 旧 refreshToken 调用 `/auth/refresh` 返回 1004。
>
> 账户列表与交易列表会额外返回 `bookName`、`ownerUsername`、`accountName`、`toAccountName`、
> `categoryName` 这些展示名（App 端接口不填，保持只返回 ID）。
> 账本列表额外返回 `ownerUsername`、`memberCount`、`accountCount`，便于后台直接从用户下钻到账本、账户。
> 平台金额口径统一为「支出 / 收入分开统计，转账不计入」，与 C 端报表保持一致。

---

## 11. 实现进度

### 已完成

1. 通用响应 / 全局异常 / 参数校验 / 链路追踪（traceId）
2. 认证模块（注册、登录、刷新令牌、登出、当前用户）
3. 账本 / 账户 / 分类 / 交易的完整 CRUD，交易写操作自动维护账户余额
4. 账本共享成员管理：成员列表、修改角色 / 家庭内称呼、移除成员
5. **家庭多人记账**：邀请码生成 / 查看 / 作废 / 凭码加入；账本 `type` 区分个人与家庭；
   交易带 `createdBy`；`/app/reports/member` 按成员统计「谁花了多少」
6. **预算与固定支出**：月 / 周 / 年总预算与分类预算、执行进度与每日预测；
   固定支出规则（房租 / 房贷 / 会员费）随同步下发给多设备与家庭成员
7. 管理后台：管理员登录、用户查询与启用/禁用、账本查询、账户增删改查与账户流水、交易查询、
   平台总览与增长趋势
8. SpringDoc 在线接口文档（`/swagger-ui.html`）

### 待实现（阶段二）

1. **周期账单（到点自动生成账单）**：固定支出规则已支持多端同步，但当前只做「预算内预留」，
   **不自动记账**；到点自动生成流水仍是待实现项。另有票据上传（对象存储）
2. 令牌黑名单切换为 Redis 实现（当前为单机内存实现）
3. 家庭多人记账的增强项（V1 刻意不做）：
   - **待审批加入**：输码后由所有者确认才进账本（当前是输码即加入）
   - **家庭实体层**：一支成员管多个家庭账本（当前账本即团队，每个家庭账本各自邀请）
   - **账户级可见性**：共享账本里放「私有账户」（当前私房钱请放个人账本）
   - 邀请链接 / 二维码分享、加入成功推送通知
