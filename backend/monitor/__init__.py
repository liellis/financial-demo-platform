"""Trace 监控子包：把原独立 trace-server 的查询/接收接口挂到主应用上。

使用方式（见 `backend/api.py::create_app`）：

    from .monitor import register
    register(app)

只注册路由，不做生命周期管理；启动/关闭钩子在 `lifecycle.py`。
"""
from __future__ import annotations

from fastapi import FastAPI

from .config import METRICS_ENABLED, OTLP_ENABLED, STORAGE_BACKEND

__all__ = ["register"]


def register(app: FastAPI) -> None:
    """把监控相关路由挂到传入的 FastAPI 应用上。

    - `/api/v1/projects/...`（traces / metrics）
    - `/v1/traces`、`/v1/metrics`（OTLP 接收，挂根路径不带前缀）
    - `/api/v1/config`
    """
    # 延迟导入：依赖缺失时由调用方（api.py 的 try/except）兜底，不影响主应用启动
    from .api.routes import metrics, otlp, traces
    from .api.schemas import ConfigOut

    api_prefix = "/api/v1"

    @app.get(f"{api_prefix}/config", response_model=ConfigOut, tags=["monitor"])
    async def get_monitor_config():
        """返回当前存储后端模式（file/database），供前端据此显示/隐藏功能。"""
        return ConfigOut(storageBackend=STORAGE_BACKEND)

    app.include_router(traces.router, prefix=api_prefix, tags=["traces"])
    app.include_router(metrics.router, prefix=api_prefix, tags=["metrics"])

    if OTLP_ENABLED or METRICS_ENABLED:
        app.include_router(otlp.router, tags=["otlp"])