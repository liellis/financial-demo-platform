"""监控子包生命周期：数据库 engine、OTLP sink、指标 sink、轨迹导入任务。

移植自原 `api/main.py` 的 lifespan，改为可被主应用显式调用的 startup/shutdown。

设计原则：任何一步失败**只记日志**，绝不让主应用启动/关闭失败。
数据库不可达时 sink 自身会降级为「拒收」，接收接口返回 503。
"""
from __future__ import annotations

import asyncio
import logging

from .config import (
    METRICS_ENABLED,
    METRIC_SINK,
    OTLP_BATCH_SIZE,
    OTLP_ENABLED,
    OTLP_FILE_DIR,
    OTLP_FLUSH_INTERVAL,
    OTLP_QUEUE_SIZE,
    OTLP_RECONNECT_INTERVAL,
    OTLP_SINK,
    STORAGE_BACKEND,
    TRAJECTORY_IMPORT_ENABLED,
)

# 任一链路写库都需要 engine（查询端 / trace 写入 / 指标写入）
DB_REQUIRED = (
    STORAGE_BACKEND == "database"
    or OTLP_SINK == "database"
    or METRIC_SINK == "database"
)

logger = logging.getLogger("trace_server")


async def startup(app) -> None:
    """初始化 engine、启动两个 sink、按需起轨迹导入后台任务。

    `init_engine()` 只创建连接池（惰性建连），不是阻塞 I/O。
    """
    if DB_REQUIRED:
        try:
            from .db.connection import init_engine

            init_engine()
        except Exception:
            logger.warning(
                "监控子包数据库引擎初始化失败，监控查询接口将不可用", exc_info=True
            )

    # OTLP 接收通道（与 STORAGE_BACKEND 解耦，由 OTLP_SINK 独立决定）
    app.state.otlp_sink = None
    if OTLP_ENABLED:
        try:
            from .otlp import build_sink

            sink = build_sink(
                OTLP_SINK,
                root=OTLP_FILE_DIR,
                maxsize=OTLP_QUEUE_SIZE,
                batch_size=OTLP_BATCH_SIZE,
                flush_interval=OTLP_FLUSH_INTERVAL,
                reconnect_interval=OTLP_RECONNECT_INTERVAL,
            )
            await sink.start()
            app.state.otlp_sink = sink
            logger.info("OTLP 接收已启用：sink=%s", OTLP_SINK)
        except Exception:
            logger.warning(
                "OTLP 接收通道启动失败，上报接口将返回 503", exc_info=True
            )

    # OTel 指标接收通道（METRIC_SINK 独立决定落点）
    app.state.metrics_sink = None
    if METRICS_ENABLED:
        try:
            from .otlp import build_metrics_sink

            sink = build_metrics_sink(
                METRIC_SINK,
                maxsize=OTLP_QUEUE_SIZE,
                batch_size=OTLP_BATCH_SIZE,
                flush_interval=OTLP_FLUSH_INTERVAL,
                reconnect_interval=OTLP_RECONNECT_INTERVAL,
            )
            await sink.start()
            app.state.metrics_sink = sink
            logger.info("OTel 指标接收已启用：sink=%s", METRIC_SINK)
        except Exception:
            logger.warning(
                "OTel 指标接收通道启动失败，上报接口将返回 503", exc_info=True
            )

    # 轨迹分析导入（icode trajectory export → trajectory_fact）
    app.state.trajectory_task = None
    if TRAJECTORY_IMPORT_ENABLED and DB_REQUIRED:
        try:
            from .scripts.import_trajectory import import_loop

            app.state.trajectory_task = asyncio.create_task(
                import_loop(), name="trajectory-import"
            )
            logger.info("轨迹分析导入任务已启动")
        except Exception:
            logger.warning("轨迹分析导入任务启动失败", exc_info=True)


async def shutdown(app) -> None:
    """取消后台任务、停止两个 sink、释放连接池；异常只记日志。"""
    task = getattr(app.state, "trajectory_task", None)
    if task is not None:
        task.cancel()
        try:
            await task
        except (asyncio.CancelledError, Exception):
            pass
        app.state.trajectory_task = None

    sink = getattr(app.state, "metrics_sink", None)
    if sink is not None:
        try:
            await sink.stop()
        except Exception:
            logger.warning("OTel 指标 sink 关闭失败", exc_info=True)
        app.state.metrics_sink = None

    sink = getattr(app.state, "otlp_sink", None)
    if sink is not None:
        try:
            await sink.stop()
        except Exception:
            logger.warning("OTLP sink 关闭失败", exc_info=True)
        app.state.otlp_sink = None

    if DB_REQUIRED:
        try:
            from .db.connection import close_engine

            await close_engine()
        except Exception:
            logger.warning("监控子包数据库连接释放失败", exc_info=True)