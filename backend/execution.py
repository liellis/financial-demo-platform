"""Durable per-batch execution, scheduled backoff and isolated quarantine."""
import copy
from datetime import datetime, timedelta, timezone

from .contracts import DomainError, digest, identity
from .extraction import align_exact_quotes, partial_methods, validate_batch_evidence
from .store import now

DEFAULT_POLICY = {"version": 1, "model_timeout_seconds": 120, "transient_retries": 2, "retry_backoff_seconds": 5}
ISOLATED = {"MODEL_OUTPUT_INVALID", "MODEL_OUTPUT_EMPTY", "MODEL_OUTPUT_TRUNCATED", "MODEL_RESPONSE_TOO_LARGE",
            "MODEL_IDENTITY_MISMATCH", "UNKNOWN_CODE_ENTITY", "MAPPING_EVIDENCE_REQUIRED",
            "EVIDENCE_NOT_IN_SNAPSHOT", "EVIDENCE_RANGE_INVALID", "EVIDENCE_QUOTE_INVALID", "EVIDENCE_OUTSIDE_BATCH"}
TRANSIENT = {"MODEL_CONNECT_TIMEOUT", "MODEL_CONNECTION_FAILED", "MODEL_RATE_LIMITED", "MODEL_TEMPORARILY_UNAVAILABLE"}
UNKNOWN = {"MODEL_TIMEOUT", "MODEL_RESPONSE_INTERRUPTED", "WORKER_LEASE_EXPIRED"}


def states_for(job):
    completed = (job.get("checkpoint") or {}).get("completed_batches", 0)
    states = copy.deepcopy(job.get("batch_states")) or [
        {"index": i + 1, "status": "SUCCEEDED" if i < completed else "PENDING", "attempts": 0}
        for i in range(job["extraction_plan"]["total_batches"])]
    for state in states:
        state["id"] = identity("subtask", [job["id"], state["index"], job["extraction_plan"]["batches"][state["index"] - 1]["digest"]])
        state.setdefault("revision", 1)
    return states


def report(job):
    states = job["batch_states"]
    counts = {key: sum(b["status"] == key for b in states)
              for key in ("SUCCEEDED", "INVALID", "FAILED", "UNKNOWN", "PENDING", "RETRY_WAIT", "RUNNING", "HELD", "SKIPPED")}
    job["batch_summary"] = counts
    job["extraction_plan"]["completed_batches"] = counts["SUCCEEDED"]
    job["extraction_plan"]["processed_batches"] = sum(counts[k] for k in ("SUCCEEDED", "INVALID", "FAILED", "UNKNOWN", "SKIPPED"))
    job["next_retry_at"] = min((b["next_retry_at"] for b in states if b["status"] == "RETRY_WAIT"), default=None)
    attempts_per_batch = job.get("execution_policy", DEFAULT_POLICY)["transient_retries"] + 1
    job["remaining_attempts_upper_bound"] = sum(max(0, attempts_per_batch - b["attempts"]) for b in states if b["status"] in ("PENDING", "RETRY_WAIT", "RUNNING") and (job.get("dispatch_indices") is None or b["index"] in job["dispatch_indices"]))


def select_batch(states, dispatch_indices=None):
    return next((b["index"] - 1 for b in states if b["status"] == "PENDING" or
                 b["status"] == "RETRY_WAIT" and b["next_retry_at"] <= now()
                 if dispatch_indices is None or b["index"] in dispatch_indices), None)


def settle(studio, job, conn):
    scope = job.get("dispatch_indices")
    scoped_wait = any(b["status"] == "RETRY_WAIT" and (scope is None or b["index"] in scope) for b in job["batch_states"])
    if select_batch(job["batch_states"], scope) is not None:
        job["status"] = "QUEUED"
    elif scoped_wait:
        job["status"] = "WAITING_RETRY"
    elif any(b["status"] in ("PENDING", "HELD", "RETRY_WAIT") for b in job["batch_states"]):
        job["status"], job["stage"] = "WAITING_SUBTASKS", "WAITING_SUBTASKS"
    else:
        finish(studio, job, conn)


def event(studio, job, conn, action):
    job["events"].append({"at": now(), "status": job["status"], "error": job.get("error"), "batch": job.get("active_batch")})
    studio.store.update("job", job, job["revision"], conn)
    studio.audit("worker", action, job["id"], conn)


def finish(studio, job, conn):
    """Only validated outputs are candidates. Incomplete coverage remains explicit."""
    report(job)
    incomplete = job["batch_summary"]["SUCCEEDED"] != len(job["batch_states"])
    result = job.get("checkpoint") or {"output": {"rules": []}, "usage": {}}
    ids = []
    for rule in result["output"]["rules"]:
        rid = identity("rule", [job["snapshot_id"], job["projection_id"], rule])
        if not studio.store.get("candidate", rid, conn):
            candidate = {**copy.deepcopy(rule), "id": rid, "status": "CANDIDATE", "job_id": job["id"],
                         "snapshot_id": job["snapshot_id"], "projection_id": job["projection_id"],
                         "origin_kind": "MODEL_CANDIDATE", "content_revision": 1,
                         "extraction_coverage": "PARTIAL" if incomplete else "COMPLETE"}
            if incomplete:
                required = [u for u in candidate["uncertainties"] if u.startswith("PARTIAL_METHOD_CONTEXT:")][:1]
                optional = [u for u in candidate["uncertainties"] if not u.startswith("PARTIAL_METHOD_CONTEXT:")]
                candidate["uncertainties"] = optional[:29 - len(required)] + required + [
                    "INCOMPLETE_EXTRACTION_COVERAGE: Some batches are invalid, failed, unknown or skipped. Verify missing scope and dependencies before approval."]
            studio.store.insert("candidate", candidate, conn)
        if rid not in ids:
            ids.append(rid)
    job["candidate_ids"], job["usage"] = ids, result.get("usage", {})
    job["input_digest"] = result.get("input_digest")
    job["evidence_line_corrections"] = result.get("evidence_line_corrections", [])
    job["active_batch"] = None
    if not incomplete:
        job["status"], job["stage"], job["error"] = "SUCCEEDED", "CANDIDATES_READY", None
        job["error_params"] = {}
    else:
        job["status"] = "PARTIAL_SUCCEEDED" if job["batch_summary"]["SUCCEEDED"] else (
            "NEEDS_ATTENTION" if job["batch_summary"]["UNKNOWN"] else "FAILED")
        job["stage"] = "BATCHES_REQUIRE_ATTENTION"
        # Preserve a single-batch actionable error, otherwise show the batch table.
        job["error"] = (job.get("last_batch_error") or "BATCHES_REQUIRE_ATTENTION") if len(job["batch_states"]) == 1 else "BATCHES_REQUIRE_ATTENTION"
        job["error_params"] = {"counts": job["batch_summary"]}


def process_resilient(studio, original):
    if original["status"] in ("RUNNING", "PAUSE_REQUESTED", "CANCEL_REQUESTED"):
        if original.get("lease_until", "") >= now():
            return None
        with studio.store.transaction() as conn:
            job = studio.get("job", original["id"], conn)
            if job["status"] not in ("RUNNING", "PAUSE_REQUESTED", "CANCEL_REQUESTED") or job.get("lease_until", "") >= now():
                return None
            intent = job["status"]
            job["batch_states"] = states_for(job)
            if job.get("active_batch"):
                batch = job["batch_states"][job["active_batch"] - 1]
                batch.update(status="UNKNOWN", error="WORKER_LEASE_EXPIRED", error_params={"delivery": "unknown"})
                batch["revision"] += 1
                if batch.get("attempt_history"):
                    batch["attempt_history"][-1].update(status="WORKER_LEASE_EXPIRED", finished_at=now())
            job["status"] = "CANCELLED" if intent == "CANCEL_REQUESTED" else "PAUSED" if intent == "PAUSE_REQUESTED" else "QUEUED"
            job["active_batch"] = None
            report(job)
            event(studio, job, conn, "JOB_BATCH_RECOVER_UNKNOWN")
        return True
    if original["status"] not in ("QUEUED", "WAITING_RETRY"):
        return None
    if original.get("provider_retry_at") and original["provider_retry_at"] > now():
        return None
    with studio.store.transaction() as conn:
        job = studio.get("job", original["id"], conn)
        if job["status"] not in ("QUEUED", "WAITING_RETRY"):
            return None
        if job.get("provider_retry_at") and job["provider_retry_at"] > now():
            return None
        job["batch_states"] = states_for(job)
        index = select_batch(job["batch_states"], job.get("dispatch_indices"))
        if index is None:
            before = job["status"]
            settle(studio, job, conn)
            if job["status"] == before == "WAITING_RETRY":
                return None
            report(job)
            event(studio, job, conn, "JOB_" + job["status"])
            return True
        job["status"], job["stage"], job["active_batch"] = "RUNNING", "EXTRACT", index + 1
        policy = job["execution_policy"]
        job["lease_until"] = (datetime.now(timezone.utc) + timedelta(seconds=max(180, policy["model_timeout_seconds"] * 4 + 60))).isoformat()
        batch_state = job["batch_states"][index]
        batch_state["status"] = "RUNNING"
        batch_state["revision"] += 1
        batch_state["attempts"] += 1
        batch_state.setdefault("attempt_history", []).append({"at": now(), "status": "STARTED", "cycle": batch_state.get("retry_cycles", 0), "attempt": batch_state["attempts"]})
        job = studio.store.update("job", job, job["revision"], conn)
    result = copy.deepcopy(job.get("checkpoint")) or {"output": {"rules": []}, "usage": {},
               "completed_batches": 0, "plan_digest": job["extraction_plan"]["digest"]}
    error = None
    params = {}
    output = None
    request_started = False
    try:
        config = studio.get("model", job["model_id"])
        validation = studio.models.require(config)
        if studio.models.fingerprint(config) != job["model_config"]["fingerprint"]:
            raise DomainError("MODEL_VALIDATION_STALE", 409)
        snapshot = studio.get("snapshot", job["snapshot_id"])
        projection = studio.get("projection", job["projection_id"])
        texts, plan, batches = studio.extraction_plan(snapshot, projection, config, job.get("extraction_settings"))
        if plan["digest"] != job["extraction_plan"]["digest"] or result["plan_digest"] != plan["digest"]:
            raise DomainError("EXTRACTION_PLAN_CHANGED", 409)
        result["input_digest"], result["validation_id"] = digest(texts), validation["id"]
        batch = batches[index]
        args = [{**config, "request_timeout_seconds": policy["model_timeout_seconds"], "recovery_guidance": batch_state.get("recovery_guidance")}, batch["source"], batch["entities"], batch["ranges"]]
        if job.get("extraction_settings"):
            args.append(True)
        request_started = True
        output, usage = studio.models.call(*args)
        # Usage of rejected model output is still usage, not accepted knowledge.
        for key, value in usage.items():
            if isinstance(value, (int, float)):
                result["usage"][key] = result["usage"].get(key, 0) + value
        repairs = []
        for rule_index, rule in enumerate(output["rules"]):
            try:
                if job.get("extraction_settings"):
                    repairs.extend(align_exact_quotes(rule, texts, batch))
                validate_batch_evidence(rule, batch)
                studio.models.validate_evidence(rule, texts, batch["entities"])
            except DomainError as exc:
                raise DomainError(exc.code, exc.status, {**exc.params, "rule_index": rule_index + 1})
            mapped_entity=next((e for e in batch['entities'] if e['id']==rule['entity_version_id']),{})
            if mapped_entity.get('range_kind')=='AST_COVERED_LINES':
                rule['uncertainties']=rule['uncertainties'][:29]+['PARTIAL_TYPE_CONTEXT: Joern exposes AST-covered type lines; verify the full declaration and business scope before approval.']
            if partial_methods(batch):
                marker = "PARTIAL_METHOD_CONTEXT: This batch contains an incomplete method; verify full conditions, branches and exceptions before approval."
                if marker not in rule["uncertainties"]:
                    rule["uncertainties"] = rule["uncertainties"][:29] + [marker]
        studio.models.require(studio.get("model", job["model_id"]))
        result["output"]["rules"].extend(output["rules"])
        if repairs:
            result.setdefault("evidence_line_corrections", []).append({"batch": index + 1, "corrections": repairs})
    except DomainError as exc:
        error, params = exc.code, exc.params
    except Exception:
        error = "WORKER_INTERNAL_ERROR"
        params = {"delivery": "unknown" if request_started else "not_sent"}
    with studio.store.transaction() as conn:
        current = studio.get("job", job["id"], conn)
        state = current["batch_states"][index]
        state["revision"] += 1
        state["attempt_history"][-1].update(status=error or "SUCCEEDED", finished_at=now())
        if not error:
            state.update(status="SUCCEEDED", rule_count=len(output["rules"]))
            state.pop("error", None)
            state.pop("error_params", None)
        else:
            state.update(error=error, error_params={**params, "batch": index + 1})
            current["last_batch_error"] = error
            if output is not None:
                state["quarantined_output_digest"] = digest(output)
            if error in TRANSIENT and params.get("delivery") != "unknown" and state["attempts"] <= policy["transient_retries"]:
                delay = max(policy["retry_backoff_seconds"] * 2 ** (state["attempts"] - 1), params.get("retry_after_seconds", 0))
                state.update(status="RETRY_WAIT", next_retry_at=(datetime.now(timezone.utc) + timedelta(seconds=delay)).isoformat())
                current["provider_retry_at"] = state["next_retry_at"]
            else:
                state["status"] = "UNKNOWN" if error in UNKNOWN or params.get("delivery") == "unknown" else "INVALID" if error in ISOLATED else "FAILED"
        report(current)
        result["completed_batches"] = current["batch_summary"]["SUCCEEDED"]
        current["checkpoint"] = result
        current["active_batch"] = None
        current["error"], current["error_params"] = None, {}
        if current["status"] == "CANCEL_REQUESTED":
            current["status"], current["result_discarded"] = "CANCELLED", True
        elif current["status"] == "PAUSE_REQUESTED":
            current["status"] = "PAUSED"
        elif error and error not in ISOLATED | TRANSIENT | UNKNOWN:
            current.update(status="FAILED", error=error, error_params=state["error_params"])
        elif current.get("provider_retry_at") and current["provider_retry_at"] > now():
            current["status"] = "WAITING_RETRY"
        else:
            settle(studio, current, conn)
        event(studio, current, conn, "JOB_BATCH_" + state["status"])
    return True
