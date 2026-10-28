"""ORM 模型：与 db/schema.sql 的 trace 数据表一一对应（Repository Pattern 的领域模型层）。

字段类型采用跨方言通用类型：
- UUID 语义列用 UUID36（PG 下为原生 UUID 列，绑定参数自动转 uuid；
  MySQL 下为 VARCHAR(36) 存带连字符 UUID 字符串，与 schema_mysql.sql 的 CHAR(36) 一致）
- JSON 列用 JSON（PG 下为 JSONB，MySQL 下为 JSON）

注：项目管理功能已移除，project_id 仅作为集成方自带的数据隔离维度（无外键、
无 projects 表），故此处不映射 Project 模型；它为任意字符串（如 l0/l1/l2），
SQL 侧为 VARCHAR(64)（PG/MySQL 一致），故映射为 String(64) 而非 UUID36。

注：与 db/schema.sql / schema_mysql.sql 一致，**不声明任何表级外键**——按日分区
改造要求子表不 REFERENCES 分区父表，且外键检查会对父行加共享锁带来写争用。
session_id / trace_id / span_id 仅为普通列，引用完整性由 consumer 的
ensure_* 占位与查询过滤保证（本模块不参与建表，仅供 ORM 查询映射）。
"""
from __future__ import annotations

from datetime import datetime

from sqlalchemy import (
    JSON,
    ARRAY,
    BigInteger,
    Column,
    DateTime,
    Float,
    Index,
    Integer,
    String,
    Text,
    Uuid,
    func,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

# 跨方言 UUID 列：PG 存原生 uuid 列（Uuid 绑定自动转 uuid，避免 uuid=varchar 报错），
# MySQL 存 VARCHAR(36) 带连字符字符串（与 schema_mysql.sql 的 CHAR(36) 语义一致）。
UUID36 = String(36).with_variant(Uuid, "postgresql")


class Base(DeclarativeBase):
    pass


class Session(Base):
    __tablename__ = "sessions"

    # id 用 VARCHAR：session_id 语义与 langfuse 一致（可为任意字符串，如 sess-xxx）
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    project_id: Mapped[str] = mapped_column(String(64))
    name: Mapped[str | None] = mapped_column(String(255))
    user_id: Mapped[str | None] = mapped_column(String(255))
    metadata_json: Mapped[dict | None] = Column("metadata", JSON)
    trace_count: Mapped[int] = mapped_column(Integer, server_default="0")
    total_tokens: Mapped[int] = mapped_column(BigInteger, server_default="0")
    total_duration_ms: Mapped[int] = mapped_column(BigInteger, server_default="0")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    __table_args__ = (Index("idx_sessions_project", "project_id", "created_at"),)


class Trace(Base):
    __tablename__ = "traces"

    id: Mapped[str] = mapped_column(UUID36, primary_key=True)
    event_id: Mapped[str] = mapped_column(UUID36, unique=True)
    project_id: Mapped[str] = mapped_column(String(64))
    session_id: Mapped[str | None] = mapped_column(String(64))
    name: Mapped[str | None] = mapped_column(String(255))
    agent_name: Mapped[str | None] = mapped_column(String(255))
    input: Mapped[dict | None] = mapped_column(JSON)
    output: Mapped[dict | None] = mapped_column(JSON)
    duration_ms: Mapped[int | None] = mapped_column(BigInteger)
    react_step_count: Mapped[int] = mapped_column(Integer, server_default="0")
    tool_count: Mapped[int] = mapped_column(Integer, server_default="0")
    span_count: Mapped[int] = mapped_column(Integer, server_default="0")
    status: Mapped[str] = mapped_column(String(20), server_default="success")
    error_info: Mapped[dict | None] = mapped_column(JSON)
    started_at: Mapped[datetime | None] = mapped_column(DateTime())
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    __table_args__ = (
        Index("idx_traces_project_time", "project_id", "created_at"),
        Index("idx_traces_session", "session_id"),
    )


class Span(Base):
    __tablename__ = "spans"

    id: Mapped[str] = mapped_column(UUID36, primary_key=True)
    event_id: Mapped[str] = mapped_column(UUID36, unique=True)
    project_id: Mapped[str] = mapped_column(String(64))
    trace_id: Mapped[str] = mapped_column(UUID36)
    parent_span_id: Mapped[str | None] = mapped_column(UUID36)
    name: Mapped[str] = mapped_column(String(255))
    span_type: Mapped[str] = mapped_column(String(20))
    input: Mapped[dict | None] = mapped_column(JSON)
    output: Mapped[dict | None] = mapped_column(JSON)
    duration_ms: Mapped[int | None] = mapped_column(BigInteger)
    model: Mapped[str | None] = mapped_column(String(255))
    tool_name: Mapped[str | None] = mapped_column(String(255))
    status: Mapped[str] = mapped_column(String(20), server_default="success")
    error_info: Mapped[dict | None] = mapped_column(JSON)
    # OTel 原始属性（剔除 input/output/model/tool_name 等已消费字段），
    # 缓存命中率等指标从 attributes->>'gen_ai.usage.*' 取值
    attributes: Mapped[dict | None] = mapped_column("attributes", JSON)
    started_at: Mapped[datetime | None] = mapped_column(DateTime())
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    __table_args__ = (
        Index("idx_spans_trace", "trace_id", "created_at"),
        Index("idx_spans_parent", "parent_span_id"),
        Index("idx_spans_project_time", "project_id", "created_at"),
    )


class Observation(Base):
    __tablename__ = "observations"

    id: Mapped[str] = mapped_column(UUID36, primary_key=True)
    event_id: Mapped[str] = mapped_column(UUID36, unique=True)
    project_id: Mapped[str] = mapped_column(String(64))
    trace_id: Mapped[str] = mapped_column(UUID36)
    span_id: Mapped[str | None] = mapped_column(UUID36)
    model: Mapped[str | None] = mapped_column(String(255))
    prompt_tokens: Mapped[int] = mapped_column(Integer, server_default="0")
    completion_tokens: Mapped[int] = mapped_column(Integer, server_default="0")
    total_tokens: Mapped[int] = mapped_column(Integer, server_default="0")
    input: Mapped[dict | None] = mapped_column(JSON)
    output: Mapped[dict | None] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    __table_args__ = (
        Index("idx_observations_span", "span_id", "created_at"),
        Index("idx_observations_trace", "trace_id", "created_at"),
    )


class LlmRequest(Base):
    """真实 LLM 请求（中间件拦截，event_id 关联 llm span）。

    与 db/schema.sql 的 llm_requests 表对应：event_id 主键（幂等），
    input/output 为真实请求/响应 payload。
    """

    __tablename__ = "llm_requests"

    event_id: Mapped[str] = mapped_column(UUID36, primary_key=True)
    id: Mapped[str] = mapped_column(UUID36)
    project_id: Mapped[str] = mapped_column(String(64))
    trace_id: Mapped[str] = mapped_column(UUID36)
    span_id: Mapped[str | None] = mapped_column(UUID36)
    model: Mapped[str | None] = mapped_column(String(256))
    input: Mapped[dict] = mapped_column(JSON)
    output: Mapped[dict | None] = mapped_column(JSON)
    status: Mapped[str] = mapped_column(String(16), server_default="success")
    error_info: Mapped[dict | None] = mapped_column(JSON)
    started_at: Mapped[datetime | None] = mapped_column(DateTime())
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    __table_args__ = (
        Index("idx_llm_requests_trace", "trace_id"),
        Index("idx_llm_requests_span", "span_id"),
    )


class MetricPoint(Base):
    """OTel 指标数据点（一个 series 在一个时间窗的聚合结果）。

    与 db/schema_pg.sql 的 metric_point 对应：Histogram 存 count/sum/桶计数，
    Gauge/Sum 存 value；唯一键 (project_id, metric_name, series_hash, ts_start,
    ts_end) 保证 SDK 重放不重复。
    """

    __tablename__ = "metric_point"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    project_id: Mapped[str] = mapped_column(String(64))
    service_name: Mapped[str | None] = mapped_column(String(128))
    metric_name: Mapped[str] = mapped_column(String(128))
    metric_type: Mapped[str] = mapped_column(String(16))
    temporality: Mapped[str] = mapped_column(String(16), server_default="delta")
    unit: Mapped[str | None] = mapped_column(String(32))
    ts_start: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    ts_end: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    value: Mapped[float | None] = mapped_column(Float)
    sample_count: Mapped[int | None] = mapped_column(BigInteger)
    sample_sum: Mapped[float | None] = mapped_column(Float)
    bucket_counts: Mapped[list[int] | None] = mapped_column(ARRAY(BigInteger))
    explicit_bounds: Mapped[list[float] | None] = mapped_column(ARRAY(Float))
    operation: Mapped[str | None] = mapped_column(String(64))
    provider: Mapped[str | None] = mapped_column(String(64))
    request_model: Mapped[str | None] = mapped_column(String(128))
    response_model: Mapped[str | None] = mapped_column(String(128))
    token_type: Mapped[str | None] = mapped_column(String(16))
    tool_name: Mapped[str | None] = mapped_column(String(128))
    error_type: Mapped[str | None] = mapped_column(String(128))
    attributes: Mapped[dict | None] = mapped_column(JSON)
    series_hash: Mapped[str] = mapped_column(String(32))
    received_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    __table_args__ = (
        Index("idx_metric_point_project_time", "project_id", "ts_end"),
        Index("idx_metric_point_name_time", "metric_name", "ts_end"),
    )


class TrajectoryFact(Base):
    """轨迹分析事实：一个会话一行，来自 `icode trajectory export --format json`。

    数值由客户端 TrajectoryAnalyzer 计算，服务端不重算；比率类指标由查询侧
    用分子分母分别求和后再除。
    """

    __tablename__ = "trajectory_fact"

    session_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    project_id: Mapped[str] = mapped_column(String(64))
    exported_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    availability: Mapped[str | None] = mapped_column(String(32))
    turn_count: Mapped[int | None] = mapped_column(Integer)
    elapsed_ns: Mapped[int | None] = mapped_column(BigInteger)
    exclusive_work_ns: Mapped[int | None] = mapped_column(BigInteger)
    response_cp_ns: Mapped[int | None] = mapped_column(BigInteger)
    compute_cp_ns: Mapped[int | None] = mapped_column(BigInteger)
    parallelism: Mapped[float | None] = mapped_column(Float)
    overlap_gain_ns: Mapped[int | None] = mapped_column(BigInteger)
    cache_hit_rate: Mapped[float | None] = mapped_column(Float)
    wall_time: Mapped[dict | None] = mapped_column(JSON)
    utilization: Mapped[dict | None] = mapped_column(JSON)
    tokens: Mapped[dict | None] = mapped_column(JSON)
    operations: Mapped[dict | None] = mapped_column(JSON)
    skills: Mapped[dict | None] = mapped_column(JSON)
    mcp: Mapped[dict | None] = mapped_column(JSON)
    validation: Mapped[dict | None] = mapped_column(JSON)
    findings: Mapped[dict | None] = mapped_column(JSON)
    raw: Mapped[dict | None] = mapped_column(JSON)

    __table_args__ = (
        Index("idx_trajectory_fact_project_time", "project_id", "exported_at"),
    )

