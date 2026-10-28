"""FastAPI 应用入口：路由注册 + CORS + 生命周期。

注意：本文件是原独立 trace-server 的入口，移植进 `backend/monitor/` 后
**不参与挂载**——路由注册由 `backend/monitor/__init__.py::register(app)` 完成，
生命周期由 `backend/monitor/lifecycle.py` 完成。保留此文件仅供对照/独立调试，
不要 import 它（会额外创建一套 FastAPI 实例与 lifespan）。
"""
from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from ..config import (
    METRICS_ENABLED,
    METRIC_SINK,
    OTLP_BATCH_SIZE,
    OTLP_ENABLED,
    OTLP_FILE_DIR,
    OTLP_FLUSH_INTERVAL,
    OTLP_PATH,
    OTLP_QUEUE_SIZE,
    OTLP_RECONNECT_INTERVAL,
    OTLP_SINK,
    STORAGE_BACKEND,
    TRAJECTORY_IMPORT_ENABLED,
)
from ..db.connection import close_engine, init_engine
from .routes import metrics, otlp, traces
from .schemas import ConfigOut

# 任一链路写库都需要 engine（查询端 / trace 写入 / 指标写入）
DB_REQUIRED = (
    STORAGE_BACKEND == "database"
    or OTLP_SINK == "database"
    or METRIC_SINK == "database"
)

logger = logging.getLogger("trace_server")


@asynccontextmanager
async def lifespan(app: FastAPI):
    if DB_REQUIRED:
        init_engine()

    # OTLP 接收通道（与 STORAGE_BACKEND 解耦，由 OTLP_SINK 独立决定）
    if OTLP_ENABLED:
        from ..otlp import build_sink

        app.state.otlp_sink = build_sink(
            OTLP_SINK,
            root=OTLP_FILE_DIR,
            maxsize=OTLP_QUEUE_SIZE,
            batch_size=OTLP_BATCH_SIZE,
            flush_interval=OTLP_FLUSH_INTERVAL,
            reconnect_interval=OTLP_RECONNECT_INTERVAL,
        )
        await app.state.otlp_sink.start()
        logger.info("OTLP 接收已启用：path=%s sink=%s", OTLP_PATH, OTLP_SINK)
        if OTLP_SINK in ("kafka", "redis") and STORAGE_BACKEND == "file":
            logger.warning(
                "OTLP_SINK=%s 但 STORAGE_BACKEND=file："
                "数据会写入数据库而查询读取文件，前端将看不到这些 trace",
                OTLP_SINK,
            )

    # OTel 指标接收通道（METRIC_SINK 独立决定落点）
    if METRICS_ENABLED:
        from ..otlp import build_metrics_sink

        app.state.metrics_sink = build_metrics_sink(
            METRIC_SINK,
            maxsize=OTLP_QUEUE_SIZE,
            batch_size=OTLP_BATCH_SIZE,
            flush_interval=OTLP_FLUSH_INTERVAL,
            reconnect_interval=OTLP_RECONNECT_INTERVAL,
        )
        await app.state.metrics_sink.start()
        logger.info("OTel 指标接收已启用：sink=%s", METRIC_SINK)
    else:
        app.state.metrics_sink = None

    # 轨迹分析导入（icode trajectory export → trajectory_fact）
    trajectory_task: asyncio.Task | None = None
    if TRAJECTORY_IMPORT_ENABLED and DB_REQUIRED:
        from ..scripts.import_trajectory import import_loop

        trajectory_task = asyncio.create_task(
            import_loop(), name="trajectory-import"
        )
        logger.info("轨迹分析导入任务已启动")
    elif TRAJECTORY_IMPORT_ENABLED:
        logger.warning("TRAJECTORY_IMPORT_ENABLED=1 但未启用数据库，导入任务未启动")

    yield

    if trajectory_task is not None:
        trajectory_task.cancel()
        try:
            await trajectory_task
        except (asyncio.CancelledError, Exception):
            pass

    if METRICS_ENABLED:
        sink = getattr(app.state, "metrics_sink", None)
        if sink is not None:
            await sink.stop()
            app.state.metrics_sink = None

    if OTLP_ENABLED:
        sink = getattr(app.state, "otlp_sink", None)
        if sink is not None:
            await sink.stop()
            app.state.otlp_sink = None
    if DB_REQUIRED:
        await close_engine()


app = FastAPI(title="Trace Monitor API", version="0.1.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

api_prefix = "/api/v1"


@app.get("/api/v1/config", response_model=ConfigOut)
async def get_config():
    """返回当前存储后端模式（file/database），供前端据此显示/隐藏功能。"""
    return ConfigOut(storageBackend=STORAGE_BACKEND)


app.include_router(traces.router, prefix=api_prefix, tags=["traces"])
app.include_router(metrics.router, prefix=api_prefix, tags=["metrics"])

if OTLP_ENABLED or METRICS_ENABLED:
    app.include_router(otlp.router, tags=["otlp"])


@app.get("/health")
async def health():
    return {"status": "ok"}
