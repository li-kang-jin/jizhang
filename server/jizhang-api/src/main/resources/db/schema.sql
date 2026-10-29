-- ============================================================================
-- 记账系统 数据库初始化脚本（PostgreSQL 16）
-- 幂等设计：可重复执行，不会破坏已有数据
-- 约定：主键为客户端/雪花 ID；金额单位为「分」，使用 BIGINT；时间为 UTC
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 用户
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_user
(
    id            BIGINT       PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password      VARCHAR(128) NOT NULL,
    nickname      VARCHAR(64),
    avatar        VARCHAR(512),
    phone         VARCHAR(32),
    email         VARCHAR(128),
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    status        SMALLINT     NOT NULL DEFAULT 1,
    last_login_at TIMESTAMPTZ,
    created_by    BIGINT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by    BIGINT,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted       SMALLINT     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_username ON t_user (username) WHERE deleted = 0;
-- 管理员重置密码后置 1：登录仍然放行（否则用户拿不到改密码的机会），由客户端提示尽快改密
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS must_change_password SMALLINT NOT NULL DEFAULT 0;
-- 令牌版本：改密 / 重置密码 / 禁用时 +1，旧 accessToken 与 refreshToken 立即失效
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;
COMMENT ON TABLE t_user IS '用户表';
COMMENT ON COLUMN t_user.role IS 'USER=普通用户 ADMIN=管理员';
COMMENT ON COLUMN t_user.status IS '0=禁用 1=正常';
COMMENT ON COLUMN t_user.must_change_password IS '1=密码已被管理员重置，提示用户登录后自行修改';
COMMENT ON COLUMN t_user.token_version IS '令牌版本，签发 JWT 时写入，校验时不匹配即视为失效';

-- ---------------------------------------------------------------------------
-- 账本
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_book
(
    id         BIGINT      PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    icon       VARCHAR(64),
    currency   VARCHAR(8)  NOT NULL DEFAULT 'CNY',
    type       SMALLINT    NOT NULL DEFAULT 0,
    owner_id   BIGINT      NOT NULL,
    remark     VARCHAR(255),
    version    INTEGER     NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted    SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_book_owner ON t_book (owner_id) WHERE deleted = 0;
COMMENT ON TABLE t_book IS '账本表（账本即团队：个人账本成员只有自己，家庭账本成员为全家）';
COMMENT ON COLUMN t_book.type IS '0=个人（仅自己可见） 1=家庭/共享（成员互相可见） 2=共享';

-- ---------------------------------------------------------------------------
-- 账本成员
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_book_member
(
    id         BIGINT      PRIMARY KEY,
    book_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role       SMALLINT    NOT NULL DEFAULT 1,
    alias      VARCHAR(32),
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted    SMALLINT    NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_book_member ON t_book_member (book_id, user_id) WHERE deleted = 0;
-- 兼容已存在的库：补列（IF NOT EXISTS 保证脚本可重复执行）
ALTER TABLE t_book_member ADD COLUMN IF NOT EXISTS alias VARCHAR(32);
ALTER TABLE t_book_member ADD COLUMN IF NOT EXISTS joined_at TIMESTAMPTZ NOT NULL DEFAULT now();
COMMENT ON TABLE t_book_member IS '账本成员表（共享账本）';
COMMENT ON COLUMN t_book_member.role IS '0=只读 1=可写 2=管理员';
COMMENT ON COLUMN t_book_member.alias IS '家庭内称呼（如「爸爸」），为空时展示昵称';

-- ---------------------------------------------------------------------------
-- 账本邀请码（家庭 / 共享账本的拉人入口）
-- 不建独立的「家庭/团队」实体：账本本身就是成员容器，邀请码把成员直接拉进账本
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_book_invite
(
    id         BIGINT      PRIMARY KEY,
    book_id    BIGINT      NOT NULL,
    code       VARCHAR(16) NOT NULL,
    role       SMALLINT    NOT NULL DEFAULT 1,
    expire_at  TIMESTAMPTZ,
    max_uses   INTEGER     NOT NULL DEFAULT 1,
    used_count INTEGER     NOT NULL DEFAULT 0,
    status     SMALLINT    NOT NULL DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted    SMALLINT    NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_book_invite_code ON t_book_invite (code) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_book_invite_book ON t_book_invite (book_id) WHERE deleted = 0;
COMMENT ON TABLE t_book_invite IS '账本邀请码表';
COMMENT ON COLUMN t_book_invite.code IS '邀请码，全局唯一，8 位大写字母数字';
COMMENT ON COLUMN t_book_invite.role IS '加入后角色 0=只读 1=可写 2=管理员';
COMMENT ON COLUMN t_book_invite.max_uses IS '最大使用次数，0 表示不限';
COMMENT ON COLUMN t_book_invite.status IS '0=已作废 1=有效';

-- ---------------------------------------------------------------------------
-- 账户
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_account
(
    id               BIGINT      PRIMARY KEY,
    book_id          BIGINT      NOT NULL,
    name             VARCHAR(64) NOT NULL,
    type             SMALLINT    NOT NULL,
    balance          BIGINT      NOT NULL DEFAULT 0,
    initial_balance  BIGINT      NOT NULL DEFAULT 0,
    currency         VARCHAR(8)  NOT NULL DEFAULT 'CNY',
    icon             VARCHAR(64),
    sort             INTEGER     NOT NULL DEFAULT 0,
    include_in_total SMALLINT    NOT NULL DEFAULT 1,
    version          INTEGER     NOT NULL DEFAULT 0,
    created_by       BIGINT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by       BIGINT,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted          SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_account_book ON t_account (book_id) WHERE deleted = 0;
COMMENT ON TABLE t_account IS '账户表';
COMMENT ON COLUMN t_account.type IS '1=现金 2=储蓄卡 3=信用卡 4=支付宝 5=微信 6=投资 7=其他';
COMMENT ON COLUMN t_account.balance IS '当前余额（分），由交易流水驱动';
COMMENT ON COLUMN t_account.include_in_total IS '是否计入总资产 0=否 1=是';

-- ---------------------------------------------------------------------------
-- 分类
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_category
(
    id         BIGINT      PRIMARY KEY,
    book_id    BIGINT      NOT NULL DEFAULT 0,
    parent_id  BIGINT      NOT NULL DEFAULT 0,
    name       VARCHAR(64) NOT NULL,
    type       SMALLINT    NOT NULL,
    icon       VARCHAR(64),
    color      VARCHAR(16),
    sort       INTEGER     NOT NULL DEFAULT 0,
    version    INTEGER     NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted    SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_category_book_type ON t_category (book_id, type) WHERE deleted = 0;
COMMENT ON TABLE t_category IS '收支分类表（树形）';
COMMENT ON COLUMN t_category.book_id IS '0 表示系统内置分类';
COMMENT ON COLUMN t_category.type IS '1=支出 2=收入';

-- ---------------------------------------------------------------------------
-- 交易流水（核心表）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_transaction
(
    id            BIGINT      PRIMARY KEY,
    book_id       BIGINT      NOT NULL,
    type          SMALLINT    NOT NULL,
    amount        BIGINT      NOT NULL,
    account_id    BIGINT      NOT NULL,
    to_account_id BIGINT,
    category_id   BIGINT,
    trade_time    TIMESTAMPTZ NOT NULL,
    remark        VARCHAR(255),
    images        JSONB,
    tags          JSONB,
    longitude     NUMERIC(10, 7),
    latitude      NUMERIC(10, 7),
    source        SMALLINT    NOT NULL DEFAULT 1,
    version       INTEGER     NOT NULL DEFAULT 0,
    created_by    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    BIGINT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted       SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_tx_book_time ON t_transaction (book_id, trade_time DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_tx_account ON t_transaction (account_id, trade_time DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_tx_category ON t_transaction (category_id, trade_time DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_tx_updated_at ON t_transaction (updated_at);
COMMENT ON TABLE t_transaction IS '交易流水表';
COMMENT ON COLUMN t_transaction.type IS '1=支出 2=收入 3=转账';
COMMENT ON COLUMN t_transaction.amount IS '金额（分），恒为正数，方向由 type 决定';
COMMENT ON COLUMN t_transaction.source IS '1=手动 2=导入 3=周期自动';

-- ---------------------------------------------------------------------------
-- 多端同步游标
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_sync_log
(
    id           BIGINT      PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    device_id    VARCHAR(64) NOT NULL,
    last_sync_at TIMESTAMPTZ,
    sync_cursor  BIGINT,
    created_by   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by   BIGINT,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted      SMALLINT    NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sync_user_device ON t_sync_log (user_id, device_id) WHERE deleted = 0;
COMMENT ON TABLE t_sync_log IS '多端同步游标表';

-- ---------------------------------------------------------------------------
-- 预算
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_budget
(
    id           BIGINT      PRIMARY KEY,
    book_id      BIGINT      NOT NULL,
    category_id  BIGINT      NOT NULL DEFAULT 0,
    -- 周期用「类型 + 首日」表达，结束日由 cycle 推导（首日 + 1 月 / 1 周 / 1 年），
    -- 不存字符串周期：yyyy-MM / 2026-W42 / 2026 需要分别解析，ISO 周还有跨年归属歧义
    cycle        SMALLINT    NOT NULL DEFAULT 1,
    period_start DATE        NOT NULL,
    amount       BIGINT      NOT NULL,
    remark       VARCHAR(255),
    version      INTEGER     NOT NULL DEFAULT 0,
    created_by   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by   BIGINT,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted      SMALLINT    NOT NULL DEFAULT 0
);
-- 用 category_id = 0 表示「账本总预算」而不是 NULL，
-- 否则 PostgreSQL 的唯一索引不约束 NULL，同一周期会插出多条总预算
CREATE UNIQUE INDEX IF NOT EXISTS uk_budget_book_cat_cycle_start
    ON t_budget (book_id, category_id, cycle, period_start) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_budget_book_period_start
    ON t_budget (book_id, period_start) WHERE deleted = 0;
COMMENT ON TABLE t_budget IS '预算表（每月 / 每周 / 每年）';
COMMENT ON COLUMN t_budget.category_id IS '0=账本总预算，其余为对应分类的预算';
COMMENT ON COLUMN t_budget.cycle IS '周期类型：1=每月 2=每周（ISO 周）3=每年';
COMMENT ON COLUMN t_budget.period_start IS '周期首日（含）；结束日为右开边界，由 cycle 推导';
COMMENT ON COLUMN t_budget.amount IS '预算金额（分）';

-- ---------------------------------------------------------------------------
-- 固定支出（房租 / 房贷 / 会员费这类按期必须支出的钱）
-- ---------------------------------------------------------------------------
-- 只登记规则，不生成交易：它不参与账单数据，只参与「本期还没到期的部分先从预算里预留」。
-- 主键由客户端生成（毫秒时间戳左移 20 位 + 20 位随机数），服务端按 id 幂等写入，
-- 与 t_transaction 同一套约定，离线创建的多端记录不会互相覆盖。
CREATE TABLE IF NOT EXISTS t_recurring_expense
(
    id            BIGINT      PRIMARY KEY,
    book_id       BIGINT      NOT NULL,
    name          VARCHAR(64) NOT NULL,
    -- 归类到的分类；0 表示未指定（与 t_budget 一样用 0 而不是 NULL，避免唯一约束失效）
    category_id   BIGINT      NOT NULL DEFAULT 0,
    -- 频率与「哪天发生」的规则，与客户端 RecurringExpenses.occurrences 一一对应
    freq          SMALLINT    NOT NULL DEFAULT 2,
    day_of_week   SMALLINT    NOT NULL DEFAULT 1,
    day_of_month  SMALLINT    NOT NULL DEFAULT 1,
    month_of_year SMALLINT    NOT NULL DEFAULT 1,
    amount        BIGINT      NOT NULL,
    -- 生效首日 / 失效日（含）；结束日为空表示长期有效
    start_date    DATE        NOT NULL,
    end_date      DATE,
    enabled       SMALLINT    NOT NULL DEFAULT 1,
    remark        VARCHAR(255),
    version       INTEGER     NOT NULL DEFAULT 0,
    created_by    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    BIGINT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted       SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_recurring_expense_book
    ON t_recurring_expense (book_id) WHERE deleted = 0;
COMMENT ON TABLE t_recurring_expense IS '固定支出规则（只做预算预留，不自动生成账单）';
COMMENT ON COLUMN t_recurring_expense.category_id IS '0=未指定分类';
COMMENT ON COLUMN t_recurring_expense.freq IS '频率：1=每周 2=每月 3=每年';
COMMENT ON COLUMN t_recurring_expense.day_of_week IS '每周用：1=周一 … 7=周日';
COMMENT ON COLUMN t_recurring_expense.day_of_month IS '每月用：1-31，当月天数不足时取当月最后一天';
COMMENT ON COLUMN t_recurring_expense.month_of_year IS '每年用：1-12';
COMMENT ON COLUMN t_recurring_expense.amount IS '金额（分）';
COMMENT ON COLUMN t_recurring_expense.start_date IS '生效首日（含）';
COMMENT ON COLUMN t_recurring_expense.end_date IS '失效日（含），空表示长期有效';
COMMENT ON COLUMN t_recurring_expense.enabled IS '1=启用 0=停用，停用的不参与预算预留';

-- ---------------------------------------------------------------------------
-- 投资持仓（基金 / 股票 / 理财）
-- ---------------------------------------------------------------------------
-- 买入卖出**不在这里记**：仍然是普通的转账账单（储蓄卡 → 投资账户），
-- 所以买股票不会让支出统计暴涨，预算与宠物建议都不受影响。
-- 这三张表只补两个维度：钱落在哪个品种上、它现在值多少。
--
-- 主键由客户端生成，服务端按 id 幂等写入 —— 与 t_recurring_expense 同一套约定。
CREATE TABLE IF NOT EXISTS t_invest_position
(
    id             BIGINT      PRIMARY KEY,
    book_id        BIGINT      NOT NULL,
    -- 挂在哪个投资账户下（t_account.type = 6）
    account_id     BIGINT      NOT NULL,
    name           VARCHAR(64) NOT NULL,
    code           VARCHAR(32),
    -- 1=股票 2=基金 3=债券 4=理财 5=其他
    kind           SMALLINT    NOT NULL DEFAULT 2,
    -- **期初成本**（分）：转账自动累计的部分由 t_invest_flow 表达，两者相加才是总成本
    cost_fen       BIGINT      NOT NULL DEFAULT 0,
    -- 份额 × 10000，可空
    shares_scaled  BIGINT,
    target_percent SMALLINT    NOT NULL DEFAULT 0,
    sort           INTEGER     NOT NULL DEFAULT 0,
    note           VARCHAR(255),
    archived       SMALLINT    NOT NULL DEFAULT 0,
    created_by     BIGINT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     BIGINT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted        SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_invest_position_book
    ON t_invest_position (book_id) WHERE deleted = 0;

-- 估值快照：只增不改。存历史而不是只存当前值，净值曲线才能回填。
-- valued_at 用 epoch millis：它是客户端本地产生的瞬间，转成带时区的字符串再解析会漂。
CREATE TABLE IF NOT EXISTS t_invest_valuation
(
    id          BIGINT      PRIMARY KEY,
    book_id     BIGINT      NOT NULL,
    position_id BIGINT      NOT NULL,
    value_fen   BIGINT      NOT NULL,
    valued_at   BIGINT      NOT NULL,
    created_by  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  BIGINT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted     SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_invest_valuation_position
    ON t_invest_valuation (position_id);

-- 成本流水：一笔转账记到了哪个品种上。
-- **按持仓全量覆盖写入**（同步时先删后插），所以不需要删除标记。
-- transaction_id 唯一：一笔转账最多算到一个品种头上，重复入库会让成本凭空翻倍。
CREATE TABLE IF NOT EXISTS t_invest_flow
(
    id             BIGINT   PRIMARY KEY,
    book_id        BIGINT   NOT NULL,
    position_id    BIGINT   NOT NULL,
    transaction_id BIGINT   NOT NULL,
    -- 1=买入（钱转入投资账户） 2=卖出（钱从投资账户转出）
    side           SMALLINT NOT NULL DEFAULT 1,
    amount_fen     BIGINT   NOT NULL,
    occurred_at    BIGINT   NOT NULL,
    created_by     BIGINT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     BIGINT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted        SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_invest_flow_position ON t_invest_flow (position_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_invest_flow_transaction ON t_invest_flow (transaction_id);
