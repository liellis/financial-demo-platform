"""Subtask projections and atomic commands over the authoritative job ledger."""
import copy
from contextlib import nullcontext
from .contracts import DomainError
from .execution import ISOLATED, UNKNOWN, report, states_for
from .store import now

STATUSES = {"PENDING", "RUNNING", "RETRY_WAIT", "SUCCEEDED", "INVALID", "FAILED", "UNKNOWN", "HELD", "SKIPPED"}
FILTERS = {"ERRORS": {"FAILED", "INVALID"}}
TRANSITIONS = {
    "RETRY": {"INVALID", "FAILED", "UNKNOWN"},
    "HOLD": {"PENDING", "RETRY_WAIT"},
    "RESUME": {"HELD"},
    "SKIP": {"PENDING", "RETRY_WAIT", "HELD", "INVALID", "FAILED"},
    "RESTORE": {"SKIPPED"},
}


def projected_states(job):
    states = states_for(job)
    if not job.get("execution_policy"):
        failed_index = job.get("error_params", {}).get("batch")
        if failed_index is None and job.get("error"):
            failed_index = min((job.get("checkpoint") or {}).get("completed_batches", 0) + 1, len(states))
        if failed_index and 1 <= failed_index <= len(states):
            state = states[failed_index - 1]
            if state["status"] != "SUCCEEDED":
                state.update(status="UNKNOWN" if job.get("error") in UNKNOWN else "INVALID" if job.get("error") in ISOLATED else "FAILED",
                             error=job.get("error"), error_params=job.get("error_params", {}))
    return states


def project(job, state, detail=False):
    batch = job["extraction_plan"]["batches"][state["index"] - 1]
    histories = state.get("previous_attempt_history", []) + state.get("attempt_history", [])
    row = {k: copy.deepcopy(state[k]) for k in ("id", "index", "revision", "status", "attempts", "retry_cycles", "rule_count", "reused_from", "error", "error_params", "next_retry_at", "previous_error", "previous_error_params") if k in state}
    row.update(job_id=job["id"], parent_status=job["status"], snapshot_id=job["snapshot_id"],
               model_id=job["model_config"]["model_id"], ranges=batch["ranges"], input_chars=batch["input_chars"],
               source_lines=batch.get("source_lines", sum(r["end_line"] - r["start_line"] + 1 for r in batch["ranges"].values())),
               can_manage=bool(job.get("execution_policy")) and job["status"] not in ("CANCELLED", "CANCEL_REQUESTED"),
               total_attempts=None if not histories and (not job.get("execution_policy") or state.get("reused_from")) else max(state.get("attempts", 0) + len(state.get("previous_attempt_history", [])), len(histories)),
               last_started_at=histories[-1].get("at") if histories else None,
               last_finished_at=histories[-1].get("finished_at") if histories else None)
    if detail:
        row.update(attempt_history=histories, control_history=state.get("control_history", []), recovery_guidance=state.get("recovery_guidance"), recovery_advice_id=state.get("recovery_advice_id"),
                   policy=job.get("execution_policy"), source_digest=batch["digest"],
                   plan_version=job["extraction_plan"]["version"],
                   dispatch_indices=job.get("dispatch_indices"), coverage=job.get("batch_summary"))
    return row


def list_subtasks(studio, job_id=None, status=None, query="", offset=0, limit=25):
    if status and status not in STATUSES | FILTERS.keys():
        raise DomainError("SUBTASK_STATUS_INVALID", 422)
    jobs = [studio.get("job", job_id)] if job_id else studio.store.all("job")
    rows = []
    for job in sorted(jobs, key=lambda j: j.get("created_at", ""), reverse=True):
        if job["type"] != "EXTRACT" or not job.get("extraction_plan"):
            continue
        rows.extend(project(job, state) for state in projected_states(job))
    summary = {key: sum(r["status"] == key for r in rows) for key in sorted(STATUSES)}
    needle = query.casefold().strip()
    filtered = [r for r in rows if (not status or r["status"] in FILTERS.get(status, {status})) and
                (not needle or needle in " ".join([r["id"], r["job_id"], str(r["index"]), r.get("error") or "", *r["ranges"]]).casefold())]
    return {"items": filtered[offset:offset + limit], "total": len(filtered), "all_total": len(rows),
            "summary": summary, "offset": offset, "limit": limit}


def subtask_detail(studio, key, index):
    job = studio.get("job", key)
    if job["type"] != "EXTRACT" or not job.get("extraction_plan") or not 1 <= index <= job["extraction_plan"]["total_batches"]:
        raise DomainError("NOT_FOUND", 404)
    return project(job, projected_states(job)[index - 1], True)


def command_subtasks(studio, key, command, targets, actor, include_unknown=False, *, conn=None, guidance=None, advice_id=None):
    if command not in TRANSITIONS:
        raise DomainError("SUBTASK_INVALID_TRANSITION", 422)
    with (nullcontext(conn) if conn is not None else studio.store.transaction()) as conn:
        job = studio.get("job", key, conn)
        if not job.get("execution_policy"):
            raise DomainError("SUBTASK_LEGACY_UPGRADE_REQUIRED", 409)
        if job["status"] in ("CANCELLED", "CANCEL_REQUESTED", "PAUSE_REQUESTED"):
            raise DomainError("SUBTASK_PARENT_NOT_AVAILABLE", 409)
        job["batch_states"] = states_for(job)
        if len({t["index"] for t in targets}) != len(targets):
            raise DomainError("DUPLICATE_SUBTASKS", 422)
        selected = []
        for target in targets:
            if not 1 <= target["index"] <= len(job["batch_states"]):
                raise DomainError("NOT_FOUND", 404)
            state = job["batch_states"][target["index"] - 1]
            if target["expected_revision"] != state["revision"]:
                raise DomainError("SUBTASK_REVISION_CONFLICT", 409, {"index": state["index"]})
            if state["status"] not in TRANSITIONS[command]:
                raise DomainError("SUBTASK_INVALID_TRANSITION", 409, {"index": state["index"], "status": state["status"], "command": command})
            if state["status"] == "UNKNOWN" and not include_unknown:
                raise DomainError("UNKNOWN_REPLAY_CONFIRMATION_REQUIRED", 409)
            selected.append(state)
        if command in ("RETRY", "RESUME"):
            config = studio.get("model", job["model_id"], conn)
            studio.models.require(config)
            if studio.models.fingerprint(config) != job["model_config"]["fingerprint"]:
                raise DomainError("MODEL_VALIDATION_STALE", 409)
        before_parent = job["status"]
        for state in selected:
            before = state["status"]
            if command == "RETRY":
                state.pop("recovery_guidance", None)
                state.pop("recovery_advice_id", None)
                if guidance and state["index"] in guidance:
                    state["recovery_guidance"] = guidance[state["index"]]
                    state["recovery_advice_id"] = advice_id
                state["previous_error"] = state.pop("error", None)
                state["previous_error_params"] = state.pop("error_params", {})
                state.update(status="PENDING", attempts=0, retry_cycles=state.get("retry_cycles", 0) + 1)
                state.pop("next_retry_at", None)
                state.pop("reused_from", None)
            elif command == "HOLD":
                state.update(status="HELD", held_from=before)
            elif command == "RESUME":
                state["status"] = state.pop("held_from", "PENDING")
            elif command == "SKIP":
                state.update(status="SKIPPED", skipped_from=before)
            else:
                previous = state.pop("skipped_from", "PENDING")
                if previous in ("FAILED", "INVALID"):
                    state["status"] = previous
                else:
                    state["status"] = "HELD"
                    if previous != "HELD":
                        state["held_from"] = previous
            state["revision"] += 1
            state.setdefault("control_history", []).append({"at": now(), "actor": actor, "command": command,
                "from_status": before, "to_status": state["status"], "unknown_replay_authorized": before == "UNKNOWN" and include_unknown, "advice_id": advice_id})
        if command in ("RETRY", "RESUME"):
            if before_parent in ("PARTIAL_SUCCEEDED", "NEEDS_ATTENTION", "FAILED", "WAITING_SUBTASKS"):
                job["dispatch_indices"] = [state["index"] for state in selected]
                job["status"], job["stage"] = "QUEUED", "EXTRACT"
                job["error"], job["error_params"] = None, {}
            elif job.get("dispatch_indices") is not None:
                job["dispatch_indices"] = sorted(set(job["dispatch_indices"]) | {s["index"] for s in selected})
        elif command == "SKIP" and before_parent == "WAITING_SUBTASKS":
            job["status"] = "QUEUED"  # settle coverage at the next worker boundary; no skipped request is sent
        elif command == "RESTORE" and before_parent in ("PARTIAL_SUCCEEDED", "FAILED", "NEEDS_ATTENTION") and any(s["status"] == "HELD" for s in selected):
            job["status"] = "WAITING_SUBTASKS"
        report(job)
        job["events"].append({"at": now(), "status": job["status"], "action": "SUBTASK_" + command,
                              "indices": [s["index"] for s in selected], "actor": actor})
        result = studio.store.update("job", job, job["revision"], conn)
        studio.audit(actor, "SUBTASK_" + command, key, conn,
                     {"indices": [s["index"] for s in selected], "include_unknown": include_unknown, "parent_before": before_parent})
        return {"job_id": key, "parent_status": result["status"], "parent_revision": result["revision"],
                "items": [project(result, state, True) for state in selected]}
