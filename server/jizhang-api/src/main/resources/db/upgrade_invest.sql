-- ---------------------------------------------------------------------------
-- 增量升级：投资持仓（适配已部署的库，全新建库请直接跑 schema.sql）
-- ---------------------------------------------------------------------------
-- 三张表全部是**新增**，不改动任何既有表与数据，可以安全地在线上执行。
-- 执行顺序无依赖，重复执行也安全（IF NOT EXISTS）。
--
-- 买入卖出仍然是普通的转账账单（储蓄卡 → 投资账户），这里只补两个维度：
-- 钱落在哪个品种上、它现在值多少。

CREATE TABLE IF NOT EXISTS t_invest_position
(
    id             BIGINT      PRIMARY KEY,
    book_id        BIGINT      NOT NULL,
    account_id     BIGINT      NOT NULL,
    name           VARCHAR(64) NOT NULL,
    code           VARCHAR(32),
    kind           SMALLINT    NOT NULL DEFAULT 2,
    cost_fen       BIGINT      NOT NULL DEFAULT 0,
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

CREATE TABLE IF NOT EXISTS t_invest_flow
(
    id             BIGINT   PRIMARY KEY,
    book_id        BIGINT   NOT NULL,
    position_id    BIGINT   NOT NULL,
    transaction_id BIGINT   NOT NULL,
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
-- 一笔转账最多算到一个品种头上：重复入库会让成本凭空翻倍
CREATE UNIQUE INDEX IF NOT EXISTS idx_invest_flow_transaction ON t_invest_flow (transaction_id);

COMMENT ON TABLE t_invest_position IS '投资持仓（期初成本，不含转账累计）';
COMMENT ON TABLE t_invest_valuation IS '投资估值快照（只增不改，epoch millis）';
COMMENT ON TABLE t_invest_flow IS '投资成本流水（按持仓全量覆盖，不含删除标记）';
