-- ============================================================================
-- 存量库升级：预算周期由「字符串 period(YYYY-MM)」改为「cycle + period_start」
-- ============================================================================
-- 背景：原方案 period 是 VARCHAR(7) 且带 @Pattern(yyyy-MM)，只支持月周期；
--       客户端已支持每周 / 每年，两端口径对不上，预算也无法进同步。
-- 新方案：cycle(1=月 2=周 3=年) + period_start(DATE，左闭区间起点)，
--       结束日由 cycle 推导，避免解析 2026-W42 这类格式与 ISO 周跨年歧义。
--
-- 执行方式（在业务低峰期执行，脚本幂等，可重复跑）：
--   psql -h <host> -U <user> -d jizhang -f upgrade_budget_period.sql
--
-- 注意：老数据全部按「月」迁移（旧 period 只可能是 yyyy-MM）。
--       迁移后请确认 t_budget 行数与迁移前一致，再确认 period 列已删除。
-- ============================================================================

BEGIN;

-- 1. 新增两列（period_start 先允许为空，回填后再置 NOT NULL）
ALTER TABLE t_budget ADD COLUMN IF NOT EXISTS cycle        SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE t_budget ADD COLUMN IF NOT EXISTS period_start DATE;

-- 2. 回填：period 'yyyy-MM' -> 当月 1 日
--    用正则先过滤掉脏数据，避免 to_date 直接报错中断整个脚本
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 't_budget' AND column_name = 'period'
    ) THEN
        UPDATE t_budget
           SET period_start = to_date(period || '-01', 'YYYY-MM-DD')
         WHERE period_start IS NULL
           AND period ~ '^\d{4}-\d{2}$';
    END IF;
END $$;

-- 3. 兜底：仍然为空的（脏数据 / period 列已被删过）按创建时间所在月处理
UPDATE t_budget
   SET period_start = date_trunc('month', created_at)::date
 WHERE period_start IS NULL;

ALTER TABLE t_budget ALTER COLUMN period_start SET NOT NULL;

-- 4. 唯一索引改成带 cycle + period_start；
--    必须先建新索引再删旧索引，避免中间出现无唯一约束的窗口
CREATE UNIQUE INDEX IF NOT EXISTS uk_budget_book_cat_cycle_start
    ON t_budget (book_id, category_id, cycle, period_start) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_budget_book_period_start
    ON t_budget (book_id, period_start) WHERE deleted = 0;

DROP INDEX IF EXISTS uk_budget_book_cat_period;
DROP INDEX IF EXISTS idx_budget_book_period;

-- 5. 删除旧的 period 列
ALTER TABLE t_budget DROP COLUMN IF EXISTS period;

COMMENT ON COLUMN t_budget.cycle IS '周期类型：1=每月 2=每周（ISO 周）3=每年';
COMMENT ON COLUMN t_budget.period_start IS '周期首日（含）；结束日为右开边界，由 cycle 推导';

COMMIT;
