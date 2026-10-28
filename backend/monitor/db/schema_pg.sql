-- ============================================================
-- Trace Monitor PostgreSQL 增量建表脚本
-- 目标库：trace_db（复用本地 langfuse-postgres-1，127.0.0.1:5432）
-- 执行：psql -U postgres -d trace_db -f backend/db/schema_pg.sql
--
-- 只补充指标与轨迹分析所需的表/列，trace 主表（sessions/traces/spans/
-- observations/llm_requests）已存在，不做重建。
-- ============================================================

-- span 原始属性：缓存命中率、tool 维度等指标从这里取
ALTER TABLE spans ADD COLUMN IF NOT EXISTS attributes JSONB;

-- ------------------------------------------------------------
-- OTel 指标数据点（一行 = 一个 series 在一个时间窗的聚合结果）
-- 唯一键保证重放不重复；attributes 已剔除 call.id / response.id 等易变属性
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS metric_point (
    id              BIGSERIAL PRIMARY KEY,
    project_id      VARCHAR(64)  NOT NULL,
    service_name    VARCHAR(128),
    metric_name     VARCHAR(128) NOT NULL,
    metric_type     VARCHAR(16)  NOT NULL,
    temporality     VARCHAR(16)  NOT NULL DEFAULT 'delta',
    unit            VARCHAR(32),
    ts_start        TIMESTAMPTZ  NOT NULL,
    ts_end          TIMESTAMPTZ  NOT NULL,
    value           DOUBLE PRECISION,
    sample_count    BIGINT,
    sample_sum      DOUBLE PRECISION,
    bucket_counts   BIGINT[],
    explicit_bounds DOUBLE PRECISION[],
    operation       VARCHAR(64),
    provider        VARCHAR(64),
    request_model   VARCHAR(128),
    response_model  VARCHAR(128),
    token_type      VARCHAR(16),
    tool_name       VARCHAR(128),
    error_type      VARCHAR(128),
    attributes      JSONB NOT NULL DEFAULT '{}'::jsonb,
    series_hash     CHAR(32) NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, metric_name, series_hash, ts_start, ts_end)
);

CREATE INDEX IF NOT EXISTS idx_metric_point_project_time
    ON metric_point (project_id, ts_end DESC);
CREATE INDEX IF NOT EXISTS idx_metric_point_name_time
    ON metric_point (metric_name, ts_end DESC);
CREATE INDEX IF NOT EXISTS idx_metric_point_model_time
    ON metric_point (project_id, metric_name, request_model, ts_end DESC);
CREATE INDEX IF NOT EXISTS idx_metric_point_tool_time
    ON metric_point (project_id, metric_name, tool_name, ts_end DESC);

-- ------------------------------------------------------------
-- 轨迹分析事实（一个会话一行，由 icode trajectory export 导入）
-- 数值来自客户端 TrajectoryAnalyzer，服务端只做存储与聚合
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trajectory_fact (
    session_id        VARCHAR(64) PRIMARY KEY,
    project_id        VARCHAR(64) NOT NULL,
    exported_at       TIMESTAMPTZ NOT NULL,
    availability      VARCHAR(32),
    turn_count        INTEGER,
    elapsed_ns        BIGINT,
    exclusive_work_ns BIGINT,
    response_cp_ns    BIGINT,
    compute_cp_ns     BIGINT,
    parallelism       DOUBLE PRECISION,
    overlap_gain_ns   BIGINT,
    cache_hit_rate    DOUBLE PRECISION,
    wall_time         JSONB,
    utilization       JSONB,
    tokens            JSONB,
    operations        JSONB,
    skills            JSONB,
    mcp               JSONB,
    validation        JSONB,
    findings          JSONB,
    raw               JSONB
);

CREATE INDEX IF NOT EXISTS idx_trajectory_fact_project_time
    ON trajectory_fact (project_id, exported_at DESC);
