"""FastAPI 依赖注入：按 STORAGE_BACKEND 提供存储后端。"""
from __future__ import annotations

from typing import AsyncIterator

from fastapi import Depends
from sqlalchemy.ext.asyncio import AsyncSession

from ..config import STORAGE_BACKEND
from ..db.backends.base import StorageBackend
from ..db.backends.database_backend import DatabaseBackend
from ..db.backends.file_backend import FileBackend
from ..db.connection import get_session_factory


async def get_session() -> AsyncIterator[AsyncSession | None]:
    """database 后端：每个请求一个 session（FastAPI 自动关闭）。

    file 后端：无需数据库连接，直接 yield None（不初始化 engine）。
    """
    if STORAGE_BACKEND != "database":
        yield None
        return
    factory = get_session_factory()
    async with factory() as session:
        yield session


def get_storage_backend(
    session: AsyncSession | None = Depends(get_session),
) -> StorageBackend:
    """按配置返回存储后端实例（file 后端做进程级单例）。"""
    if STORAGE_BACKEND == "database":
        if session is None:  # 防御：database 模式必须有 session
            raise RuntimeError("database 存储后端缺少数据库会话")
        return DatabaseBackend(session)
    if _file_backend is None:
        raise RuntimeError("file 存储后端未初始化")
    return _file_backend


# 模块级 file 后端单例（懒加载初始化，仅 file 模式创建）
_file_backend: FileBackend | None = (
    FileBackend() if STORAGE_BACKEND == "file" else None
)
