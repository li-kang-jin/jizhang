# 记账系统 - 数据库设计

> 版本：v1.1（新增家庭多人记账：邀请码、成员称呼、记账人）
> 数据库：PostgreSQL 16
> 更新日期：2026-10-15

## 1. 设计约定

| 约定 | 说明 |
| --- | --- |
| 主键 | `id`，`bigint`，由**客户端生成**的雪花 ID（预留离线创建能力）。服务端自增为后备 |
| 金额 | `bigint`，单位**分**。禁止浮点类型 |
| 时间 | `timestamptz`，统一 UTC 存储 |
| 软删除 | `deleted` `smallint`，0=正常，1=已删除 |
| 审计字段 | `created_by` / `created_at` / `updated_by` / `updated_at` |
| 乐观锁 | `version` `int`，用于多端同步冲突检测 |
| 同步字段 | `updated_at` + `version`，客户端按 `last_sync_at` 增量拉取 |
| 命名 | 表名/字段小写下划线；表名不用复数 |

## 2. ER 关系

```
user 1 ──< book_member >── 1 book 1 ──< book_invite (邀请码，拉人入口)
                            │
                            ├──< account        (账户：现金/银行卡/支付宝…)
                            ├──< category       (分类：餐饮/交通…，树形)
                            └──< transaction    (交易流水，核心表)
                                    ├─ account_id
                                    ├─ category_id
                                    ├─ created_by   (记账人，家庭账本按人统计)
                                    └─ 支持转账(transfer) / 收入(income) / 支出(expense)
```

### 家庭多人记账的模型（重要）

**不引入独立的「家庭 / 团队」实体，账本本身就是团队**：

- `个人账本`（`t_book.type = 0`）：成员表里只有所有者自己，数据**仅自己可见**；
- `家庭 / 共享账本`（`t_book.type = 1`）：所有成员都能看到账本内的每一笔收支；
- 「家庭共同财产」= 家庭账本里的账户；「私房钱」= 个人账本里的账户。**账户不跨账本**，隐私边界天然清晰；
- 用户之间通过**邀请码**组队：所有者在账本里生成一个 code，家人输入 code 即成为 `t_book_member` 的一员；
- 一笔交易记录 `created_by`（记账人），用于「谁记的」展示与按成员统计。

「个人用」与「多人用」不是两套模式，而是同一套账本模型下 `type` 的差异。

## 3. 表结构

### 3.1 用户表 `t_user`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 主键 |
| username | varchar(64) | 登录名，唯一 |
| password | varchar(128) | BCrypt 加密 |
| nickname | varchar(64) | 昵称 |
| avatar | varchar(512) | 头像 URL |
| phone | varchar(32) | 手机号，可空 |
| email | varchar(128) | 邮箱，可空 |
| status | smallint | 0=禁用 1=正常 |
| last_login_at | timestamptz | 最后登录时间 |
| created_by / created_at / updated_by / updated_at / deleted | - | 审计字段 |

唯一索引：`uk_user_username (username)`（仅 `deleted = 0`）

### 3.2 账本表 `t_book`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 主键 |
| name | varchar(64) | 账本名 |
| icon | varchar(64) | 图标 |
| currency | varchar(8) | 本位币，默认 CNY |
| type | smallint | **0=个人（仅自己可见） 1=家庭/共享（成员互相可见）** |
| owner_id | bigint | 创建者 |
| remark | varchar(255) | 备注 |
| version | int | 乐观锁 |
| 审计字段 | - | 同上 |

### 3.3 账本成员表 `t_book_member`（多账本共享）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 主键 |
| book_id | bigint | 账本 |
| user_id | bigint | 成员 |
| role | smallint | 0=只读 1=可写 2=管理员 |
| alias | varchar(32) | 家庭内称呼（如「爸爸」），为空时展示昵称 |
| joined_at | timestamptz | 加入时间 |
| 审计字段 | - | 同上 |

唯一索引：`uk_book_user (book_id, user_id, deleted)`

> 所有者不出现在本表里（`t_book.owner_id` 已表达），成员数 = 1 + 本表记录数。

### 3.3.1 账本邀请码表 `t_book_invite`（家庭拉人入口）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 主键 |
| book_id | bigint | 所属账本 |
| code | varchar(16) | 邀请码，全局唯一，8 位大写字母数字（去除 0/O/1/I） |
| role | smallint | 加入后角色 0=只读 1=可写 2=管理员 |
| expire_at | timestamptz | 过期时间，NULL 表示永不过期 |
| max_uses | int | 最大使用次数，0 表示不限 |
| used_count | int | 已使用次数 |
| status | smallint | 0=已作废 1=有效 |
| 审计字段 | - | 同上 |

唯一索引：`uk_book_invite_code (code, deleted)`
索引：`idx_book_invite_book (book_id, deleted)`

> **个人账本不允许生成邀请码**（`type = 0` 时服务端直接拒绝），否则「只有自己」的语义会与成员数自相矛盾。

### 3.4 账户表 `t_account`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 主键 |
| book_id | bigint | 所属账本 |
| name | varchar(64) | 账户名 |
| type | smallint | 1=现金 2=储蓄卡 3=信用卡 4=支付宝 5=微信 6=投资 7=其他 |
| balance | bigint | 当前余额（分） |
| initial_balance | bigint | 初始余额（分） |
| currency | varchar(8) | 币种 |
| icon | varchar(64) | 图标 |
| sort | int | 排序 |
| include_in_total | smallint | 是否计入总资产 |
| 审计字段 + version | - | 同上 |

索引：`idx_account_book (book_id, deleted)`

### 3.5 分类表 `t_category`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 主键 |
| book_id | bigint | 所属账本（0 或 NULL 表示系统内置） |
| parent_id | bigint | 父分类，0=顶级 |
| name | varchar(64) | 分类名 |
| type | smallint | 1=支出 2=收入 |
| icon | varchar(64) | 图标 |
| color | varchar(16) | 颜色 |
| sort | int | 排序 |
| 审计字段 + version | - | 同上 |

索引：`idx_category_book_type (book_id, type, deleted)`

### 3.6 交易表 `t_transaction`（核心）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 客户端生成，幂等同步 |
| book_id | bigint | 所属账本 |
| type | smallint | 1=支出 2=收入 3=转账 |
| amount | bigint | 金额（分），恒为正数，方向由 type 决定 |
| account_id | bigint | 账户（转账时为转出账户） |
| to_account_id | bigint | 转入账户，仅 type=3 使用 |
| category_id | bigint | 分类，转账时为空 |
| trade_time | timestamptz | 交易发生时间（用户可改） |
| remark | varchar(255) | 备注 |
| images | jsonb | 票据图片 URL 数组 |
| tags | jsonb | 标签数组 |
| longitude / latitude | numeric(10,7) | 位置（预留） |
| source | smallint | 来源：1=手动 2=导入 3=周期自动 |
| created_by | bigint | **记账人**，家庭账本用于「谁记的」与按成员统计 |
| 审计字段 + version | - | 同上 |

索引：
- `idx_tx_book_time (book_id, trade_time DESC, deleted)`
- `idx_tx_account (account_id, trade_time DESC)`
- `idx_tx_category (category_id, trade_time DESC)`

### 3.7 周期账单表 `t_periodic_bill`（预留）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 主键 |
| book_id | bigint | 账本 |
| name | varchar(64) | 名称（如「房租」） |
| type | smallint | 同交易 type |
| amount | bigint | 金额（分） |
| account_id / to_account_id / category_id | bigint | 同交易 |
| cron_expr | varchar(64) | 触发规则 |
| next_run_at | timestamptz | 下次执行时间 |
| enabled | smallint | 是否启用 |
| 审计字段 + version | - | 同上 |

### 3.8 同步日志表 `t_sync_log`（多端同步）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 主键 |
| user_id | bigint | 用户 |
| device_id | varchar(64) | 设备标识 |
| last_sync_at | timestamptz | 上次同步时间 |
| sync_cursor | bigint | 增量游标 |
| created_at | timestamptz | 创建时间 |

唯一索引：`uk_sync_user_device (user_id, device_id)`

## 4. 同步与并发要点

1. **增量同步**：客户端提交 `last_sync_at`，服务端返回 `updated_at > last_sync_at` 的记录（**包含已软删除的记录**，用 `deleted=1` 通知客户端删除本地数据）。
2. **幂等写入**：客户端先本地生成 `id`，服务端 `INSERT ... ON CONFLICT (id) DO UPDATE`。
3. **冲突解决 V1**：`last-write-wins` —— 比较 `updated_at`，新者覆盖，同时把用户 id 写入 `updated_by`。
4. **冲突解决 V2（预留）**：需要更强一致性时引入字段级版本或 CRDT。
5. **余额维护**：`t_account.balance` 为**冗余字段**，由交易变更同步更新；每日定时任务全量校验一次。

## 5. 初始化数据

- 系统内置分类（`book_id = 0`）：支出（餐饮、交通、购物、居住、娱乐、医疗、教育、通讯、人情、其他）、收入（工资、奖金、投资、红包、其他）。
- 默认账户：现金、支付宝、微信。
- 新用户注册时自动创建一个**个人账本**（`type = 0`，名称「XX的账本」）及上述账户/分类副本。
  本地未登录时的离线账单在首次登录时**一律迁入该个人账本**，绝不迁入家庭账本。
