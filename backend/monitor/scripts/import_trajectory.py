"""轨迹分析导入：`icode trajectory export` → trajectory_fact。

一个会话一行。数值全部由客户端 TrajectoryAnalyzer 算好，服务端只做投影
（方案 §2.3：服务端不重算，只导入结果）。

由 `api/main.py` 的 lifespan 起后台任务，按 TRAJECTORY_IMPORT_INTERVAL 循环；
单个会话导出失败只记日志，不影响其他会话。
"""
from __future__ import annotations

import asyncio
import json
import logging
import tempfile
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from sqlalchemy import text

from ..config import (
    ICODE_CLI,
    TRAJECTORY_IMPORT_INTERVAL,
    TRAJECTORY_IMPORT_LIMIT,
    TRAJECTORY_IMPORT_TIMEOUT,
)
from ..db.connection import get_session_factory

logger = logging.getLogger("trace_server.trajectory")

_cli_missing_logged = False

_UPSERT_SQL = text(
    """
    INSERT INTO trajectory_fact (
        session_id, project_id, exported_at, availability, turn_count,
        elapsed_ns, exclusive_work_ns, response_cp_ns, compute_cp_ns,
        parallelism, overlap_gain_ns, cache_hit_rate,
        wall_time, utilization, tokens, operations, skills, mcp,
        validation, findings, raw
    ) VALUES (
        :session_id, :project_id, :exported_at, :availability, :turn_count,
        :elapsed_ns, :exclusive_work_ns, :response_cp_ns, :compute_cp_ns,
        :parallelism, :overlap_gain_ns, :cache_hit_rate,
        (:wall_time)::jsonb, (:utilization)::jsonb, (:tokens)::jsonb,
        (:operations)::jsonb, (:skills)::jsonb, (:mcp)::jsonb,
        (:validation)::jsonb, (:findings)::jsonb, (:raw)::jsonb
    )
    ON CONFLICT (session_id) DO UPDATE SET
        project_id = EXCLUDED.project_id,
        exported_at = EXCLUDED.exported_at,
        availability = EXCLUDED.availability,
        turn_count = EXCLUDED.turn_count,
        elapsed_ns = EXCLUDED.elapsed_ns,
        exclusive_work_ns = EXCLUDED.exclusive_work_ns,
        response_cp_ns = EXCLUDED.response_cp_ns,
        compute_cp_ns = EXCLUDED.compute_cp_ns,
        parallelism = EXCLUDED.parallelism,
        overlap_gain_ns = EXCLUDED.overlap_gain_ns,
        cache_hit_rate = EXCLUDED.cache_hit_rate,
        wall_time = EXCLUDED.wall_time,
        utilization = EXCLUDED.utilization,
        tokens = EXCLUDED.tokens,
        operations = EXCLUDED.operations,
        skills = EXCLUDED.skills,
        mcp = EXCLUDED.mcp,
        validation = EXCLUDED.validation,
        findings = EXCLUDED.findings,
        raw = EXCLUDED.raw
    """
)


def _json(value: Any) -> str | None:
    if value is None:
        return None
    return json.dumps(value, ensure_ascii=False, default=str)


def _metric_value(container: dict[str, Any] | None, key: str) -> Any:
    """export 里每个指标是 {value, precision, reason}，只取 value。"""
    if not isinstance(container, dict):
        return None
    metric = container.get(key)
    if isinstance(metric, dict):
        return metric.get("value")
    return metric


async def _export_session(session_id: str, out_path: Path) -> dict[str, Any]:
    """调用 iCode CLI 导出单个会话的轨迹分析 JSON。"""
    proc = await asyncio.create_subprocess_exec(
        ICODE_CLI,
        "trajectory",
        "export",
        "--session",
        session_id,
        "--format",
        "json",
        "--out",
        str(out_path),
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout, stderr = await asyncio.wait_for(
            proc.communicate(), timeout=TRAJECTORY_IMPORT_TIMEOUT
        )
    except asyncio.TimeoutError:
        proc.kill()
        await proc.wait()
        raise RuntimeError(f"icode trajectory export 超时（{session_id}）")

    if proc.returncode != 0:
        detail = (stderr or b"").decode("utf-8", "replace").strip()
        raise RuntimeError(f"icode trajectory export 失败: {detail}")

    # 正常走 --out 文件；个别版本直接打印到 stdout，做一次兜底
    if out_path.exists() and out_path.stat().st_size > 0:
        return json.loads(out_path.read_text(encoding="utf-8"))
    text_output = (stdout or b"").decode("utf-8", "replace").strip()
    if text_output:
        return json.loads(text_output)
    raise RuntimeError(f"icode trajectory export 未产出数据（{session_id}）")


def build_fact(
    session_id: str, project_id: str, payload: dict[str, Any]
) -> dict[str, Any]:
    """export JSON → trajectory_fact 一行（字段口径见方案 §2.1 / §2.2）。"""
    overview = payload.get("overview") or {}
    tokens = payload.get("token_usage") or {}
    turns = payload.get("turns") or []

    input_tokens = _metric_value(tokens, "input") or 0
    cache_read = _metric_value(tokens, "cache_read") or 0
    cache_hit_rate = (cache_read / input_tokens) if input_tokens else None

    operations: Counter[str] = Counter()
    for turn in turns:
        for operation in turn.get("operations") or []:
            family = operation.get("family")
            if family:
                operations[str(family)] += 1

    session = payload.get("session") or {}
    return {
        "session_id": session_id,
        "project_id": project_id,
        "exported_at": datetime.now(timezone.utc),
        "availability": session.get("availability"),
        "turn_count": len(turns),
        "elapsed_ns": _metric_value(overview, "elapsed_ns"),
        "exclusive_work_ns": _metric_value(overview, "exclusive_work_ns"),
        "response_cp_ns": _metric_value(overview, "response_cp_ns"),
        "compute_cp_ns": _metric_value(overview, "compute_cp_ns"),
        "parallelism": _metric_value(overview, "parallelism"),
        "overlap_gain_ns": _metric_value(overview, "overlap_gain_ns"),
        "cache_hit_rate": cache_hit_rate,
        "wall_time": _json(overview.get("wall_time_ns")),
        "utilization": _json(overview.get("utilization")),
        "tokens": _json(tokens),
        "operations": _json(dict(operations)),
        # export 里没有 skill / mcp 维度，留空由后续版本补齐
        "skills": None,
        "mcp": None,
        "validation": _json(payload.get("validation")),
        "findings": _json(payload.get("findings")),
        "raw": _json(payload),
    }


async def import_once() -> int:
    """一轮导入：取最近 N 个会话，逐个导出并按 session_id UPSERT。"""
    global _cli_missing_logged

    factory = get_session_factory()
    async with factory() as session:
        rows = (
            (
                await session.execute(
                    text(
                        """
                        SELECT s.id, s.project_id
                        FROM sessions s
                        LEFT JOIN trajectory_fact f ON f.session_id = s.id
                        -- 已导入且足够新的会话跳过，避免每轮重复拉起 CLI
                        WHERE f.session_id IS NULL
                           OR f.exported_at < now() - make_interval(secs => :refresh)
                        ORDER BY s.created_at DESC
                        LIMIT :limit
                        """
                    ),
                    {
                        "limit": max(1, TRAJECTORY_IMPORT_LIMIT),
                        "refresh": max(300.0, TRAJECTORY_IMPORT_INTERVAL * 10),
                    },
                )
            )
            .mappings()
            .all()
        )

    imported = 0
    for row in rows:
        session_id = str(row["id"])
        project_id = str(row["project_id"])
        with tempfile.TemporaryDirectory(prefix="traj-") as tmp:
            out_path = Path(tmp) / "trajectory.json"
            try:
                payload = await _export_session(session_id, out_path)
            except FileNotFoundError:
                if not _cli_missing_logged:
                    logger.warning(
                        "未找到 iCode CLI（ICODE_CLI=%s），轨迹导入跳过；"
                        "安装后重启服务即可自动开始导入",
                        ICODE_CLI,
                    )
                    _cli_missing_logged = True
                return imported
            except Exception:
                logger.warning("会话 %s 轨迹导出失败", session_id, exc_info=True)
                continue

        fact = build_fact(session_id, project_id, payload)
        async with factory() as session:
            async with session.begin():
                await session.execute(_UPSERT_SQL, fact)
        imported += 1

    return imported


async def import_loop() -> None:
    """后台循环：每 TRAJECTORY_IMPORT_INTERVAL 秒跑一轮。"""
    interval = max(5.0, TRAJECTORY_IMPORT_INTERVAL)
    while True:
        try:
            count = await import_once()
            if count:
                logger.info("轨迹分析导入完成：%d 个会话", count)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.warning("轨迹分析导入轮次失败", exc_info=True)
        await asyncio.sleep(interval)
