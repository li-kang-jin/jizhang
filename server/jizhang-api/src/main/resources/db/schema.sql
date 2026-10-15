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
COMMENT ON TABLE t_user IS '用户表';
COMMENT ON COLUMN t_user.role IS 'USER=普通用户 ADMIN=管理员';
COMMENT ON COLUMN t_user.status IS '0=禁用 1=正常';

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
COMMENT ON TABLE t_book IS '账本表';
COMMENT ON COLUMN t_book.type IS '0=个人 1=家庭 2=共享';

-- ---------------------------------------------------------------------------
-- 账本成员
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_book_member
(
    id         BIGINT      PRIMARY KEY,
    book_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role       SMALLINT    NOT NULL DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted    SMALLINT    NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_book_member ON t_book_member (book_id, user_id) WHERE deleted = 0;
COMMENT ON TABLE t_book_member IS '账本成员表（共享账本）';
COMMENT ON COLUMN t_book_member.role IS '0=只读 1=可写 2=管理员';

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
    id          BIGINT      PRIMARY KEY,
    book_id     BIGINT      NOT NULL,
    category_id BIGINT      NOT NULL DEFAULT 0,
    period      VARCHAR(7)  NOT NULL,
    amount      BIGINT      NOT NULL,
    remark      VARCHAR(255),
    version     INTEGER     NOT NULL DEFAULT 0,
    created_by  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  BIGINT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted     SMALLINT    NOT NULL DEFAULT 0
);
-- 用 category_id = 0 表示「账本总预算」而不是 NULL，
-- 否则 PostgreSQL 的唯一索引不约束 NULL，同一周期会插出多条总预算
CREATE UNIQUE INDEX IF NOT EXISTS uk_budget_book_cat_period
    ON t_budget (book_id, category_id, period) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_budget_book_period
    ON t_budget (book_id, period) WHERE deleted = 0;
COMMENT ON TABLE t_budget IS '预算表（按月度周期）';
COMMENT ON COLUMN t_budget.category_id IS '0=账本总预算，其余为对应分类的预算';
COMMENT ON COLUMN t_budget.period IS '预算周期，格式 yyyy-MM（UTC）';
COMMENT ON COLUMN t_budget.amount IS '预算金额（分）';
