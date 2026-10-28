"""Trace 监控子包配置：从项目根 .env / 环境变量读取。

移植说明：本文件原属独立 trace-server 的 `backend/config.py`，现作为
`backend/monitor/` 内部子包使用，因此根目录指向本项目根，而不是原服务端目录。

所有可配置项：
- STORAGE_BACKEND : database | file
- DATABASE_URL    : SQLAlchemy async URL（database 后端）
- DATA_FILE       : SDK 直写 JSONL 文件目录（file 后端，只读）
- HOST / PORT     : 服务监听地址（仅独立运行时使用，本子包不参与监听）
"""
from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

# 项目根目录：backend/monitor/config.py → parents[2] = 项目根
SERVER_ROOT = Path(__file__).resolve().parents[2]
# 加载项目根 .env（存在时），环境变量优先（load_dotenv 默认不覆盖已存在变量）
_ENV_FILE = SERVER_ROOT / ".env"
if _ENV_FILE.exists():
    load_dotenv(_ENV_FILE, override=False)

DEFAULT_STORAGE_BACKEND = "database"
STORAGE_BACKENDS = ("database", "file")

# 存储后端
STORAGE_BACKEND = os.getenv("STORAGE_BACKEND", DEFAULT_STORAGE_BACKEND)
if STORAGE_BACKEND not in STORAGE_BACKENDS:
    raise RuntimeError(
        f"STORAGE_BACKEND 无效: {STORAGE_BACKEND!r}，可选 {STORAGE_BACKENDS}"
    )

# 数据库 URL：优先 TRACE_DATABASE_URL，其次 DATABASE_URL，最后回落默认值。
# 注意：这里**不能**因为缺配置就 raise —— 本子包是主应用的一部分，
# 配置缺失只应导致监控接口不可用（返回 503），不能让整个应用起不来。
_DEFAULT_DATABASE_URL = "postgresql+asyncpg://postgres:postgres@127.0.0.1:5432/trace_db"
DATABASE_URL = (
    os.getenv("TRACE_DATABASE_URL", "").strip()
    or os.getenv("DATABASE_URL", "").strip()
    or _DEFAULT_DATABASE_URL
)

# SDK 直写 JSONL 文件目录（file 后端使用，只读）
DATA_FILE = os.getenv("DATA_FILE", "trace_data")

# 服务监听（本子包不监听端口，保留以兼容原脚本）
HOST = os.getenv("HOST", "127.0.0.1")
PORT = int(os.getenv("PORT", "8000"))

# 若 DATA_FILE 为相对路径，则相对项目根目录解析
if not os.path.isabs(DATA_FILE):
    DATA_FILE = str(SERVER_ROOT / DATA_FILE)


# ============================================================
# OTLP 端点接收
#
# 注意：OTLP_SINK 与 STORAGE_BACKEND 是**两个独立开关**：
#   OTLP_SINK       —— 接收端把事件写到哪里
#   STORAGE_BACKEND —— 查询端从哪里读
# 二者互不牵连，允许「写 Kafka 读 Python 文件」这类组合。
# ============================================================

OTLP_ENABLED = os.getenv("OTLP_ENABLED", "1") == "1"

# 接收路径（挂在根路径下，不在 /api/v1 前缀内）
OTLP_PATH = os.getenv("OTLP_PATH", "/v1/traces")

# 写入通道：database（默认，直写 PostgreSQL）| kafka | redis | file | none
OTLP_SINK = os.getenv("OTLP_SINK", "database").strip().lower()
OTLP_SINK_CHOICES = ("kafka", "redis", "database", "file", "none")

# 客户端未带 project 属性时的兜底项目
OTLP_DEFAULT_PROJECT_ID = os.getenv("OTLP_DEFAULT_PROJECT_ID", "otlp")

# 非空则校验 Authorization: Bearer <token>
OTLP_AUTH_TOKEN = os.getenv("OTLP_AUTH_TOKEN", "").strip()

# 请求体上限（字节），默认 16MB
OTLP_MAX_BODY_BYTES = int(os.getenv("OTLP_MAX_BODY_BYTES", str(16 * 1024 * 1024)))

# 批处理参数
OTLP_QUEUE_SIZE = int(os.getenv("OTLP_QUEUE_SIZE", "10000"))
OTLP_FLUSH_INTERVAL = float(os.getenv("OTLP_FLUSH_INTERVAL", "1.0"))
OTLP_BATCH_SIZE = int(os.getenv("OTLP_BATCH_SIZE", "200"))

# 事件时间戳时区（IANA 名），需与 SDK 侧 TRACE_TIMEZONE 口径一致
OTLP_TIMEZONE = os.getenv("OTLP_TIMEZONE", "Asia/Shanghai")

# file 通道输出目录；留空则沿用 DATA_FILE
_OTLP_FILE_DIR_RAW = os.getenv("OTLP_FILE_DIR", "").strip()
OTLP_FILE_DIR = _OTLP_FILE_DIR_RAW or DATA_FILE
if _OTLP_FILE_DIR_RAW and not os.path.isabs(_OTLP_FILE_DIR_RAW):
    OTLP_FILE_DIR = str(SERVER_ROOT / _OTLP_FILE_DIR_RAW)

# kafka 通道（OTLP_SINK=kafka 时生效）：接收端自己的连接串，
# 与查询侧、consumer 侧配置互不影响
OTLP_KAFKA_BOOTSTRAP = os.getenv("OTLP_KAFKA_BOOTSTRAP", "127.0.0.1:9092").strip()
OTLP_KAFKA_TOPIC = os.getenv("OTLP_KAFKA_TOPIC", "trace_topic").strip()
OTLP_KAFKA_ACKS = os.getenv("OTLP_KAFKA_ACKS", "all").strip()

# redis 通道（OTLP_SINK=redis 时生效）
OTLP_REDIS_URL = os.getenv(
    "OTLP_REDIS_URL", "redis://:myredissecret@127.0.0.1:6379/0"
).strip()
OTLP_REDIS_TOPIC = os.getenv("OTLP_REDIS_TOPIC", "trace_topic").strip()

# 下游不可达时的重连间隔（秒）
OTLP_RECONNECT_INTERVAL = float(os.getenv("OTLP_RECONNECT_INTERVAL", "5.0"))

if OTLP_ENABLED and OTLP_SINK not in OTLP_SINK_CHOICES:
    raise RuntimeError(
        f"OTLP_SINK 无效: {OTLP_SINK!r}，可选 {OTLP_SINK_CHOICES}"
    )


# ============================================================
# OTel 指标接收（POST /v1/metrics）
#
# 与 traces 一样，接收与查询解耦：METRIC_SINK 决定 data point 写到哪里。
# 当前只支持 database（写 metric_point 表）与 none（只回 200 不落盘）。
# ============================================================

METRICS_ENABLED = os.getenv("METRICS_ENABLED", "1") == "1"

METRIC_PATH = os.getenv("METRIC_PATH", "/v1/metrics")

METRIC_SINK = os.getenv("METRIC_SINK", "database").strip().lower()
METRIC_SINK_CHOICES = ("database", "none")

if METRICS_ENABLED and METRIC_SINK not in METRIC_SINK_CHOICES:
    raise RuntimeError(
        f"METRIC_SINK 无效: {METRIC_SINK!r}，可选 {METRIC_SINK_CHOICES}"
    )


# ============================================================
# 轨迹分析导入（icode trajectory export → trajectory_fact）
#
# 定时从 sessions 表取最近会话，调用 iCode CLI 导出 JSON，再由服务端落表。
# 未安装 iCode CLI 时保持关闭，不影响主链路。
# ============================================================

TRAJECTORY_IMPORT_ENABLED = os.getenv("TRAJECTORY_IMPORT_ENABLED", "0") == "1"

# 导入轮询间隔（秒）
TRAJECTORY_IMPORT_INTERVAL = float(os.getenv("TRAJECTORY_IMPORT_INTERVAL", "60"))

# 每轮最多导入多少个会话（按 sessions.created_at 倒序）
TRAJECTORY_IMPORT_LIMIT = int(os.getenv("TRAJECTORY_IMPORT_LIMIT", "20"))

# iCode CLI 可执行文件（PATH 中可直接写 icode）
ICODE_CLI = os.getenv("ICODE_CLI", "icode").strip()

# 单次导出超时（秒）
TRAJECTORY_IMPORT_TIMEOUT = float(os.getenv("TRAJECTORY_IMPORT_TIMEOUT", "120"))

# 导入数据归属的项目 ID（demo 内嵌采集默认 icode）
TRAJECTORY_IMPORT_PROJECT = os.getenv("TRAJECTORY_IMPORT_PROJECT", "icode").strip()
