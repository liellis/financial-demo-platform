import copy
import uuid
from datetime import datetime, timedelta, timezone
from .contracts import DomainError, RULE_SCHEMA, SCHEMA_VERSION, PROMPT_VERSION, digest, identity
from .graph import GraphProjection
from . import ontology
from .models import ModelService
from .sources import SourceService
from .store import now
from .extraction import plan_extraction, validate_batch_evidence, align_exact_quotes, partial_methods
from .execution import DEFAULT_POLICY, states_for, report, process_resilient


class Studio:
    def __init__(self, settings, store):
        self.settings, self.store = settings, store
        self.sources = SourceService(settings, store)
        self.models = ModelService(settings, store, self.sources)
        self.graph = GraphProjection(settings)

    def get(self, kind, key, conn=None):
        result = self.store.get(kind, key, conn)
        if not result:
            raise DomainError("NOT_FOUND", 404)
        return result

    def audit(self, actor, action, target, conn, detail=None):
        return self.store.insert("audit", {"id": "audit_" + uuid.uuid4().hex, "actor": actor, "action": action, "target": target, "detail": detail or {}, "at": now()}, conn)

    def extraction_defaults(self):
        return {**DEFAULT_POLICY, "id": "extraction", "revision": 0, "max_source_lines": 100,
                **(self.store.get("settings", "extraction") or {})}

    def update_extraction_defaults(self, data, actor):
        with self.store.transaction() as conn:
            current = self.store.get("settings", "extraction", conn)
            if (current["revision"] if current else 0) != data["expected_revision"]:
                raise DomainError("REVISION_CONFLICT", 409)
            value = {**self.extraction_defaults(), **{k: data[k] for k in ("max_source_lines", "model_timeout_seconds", "transient_retries", "retry_backoff_seconds") if k in data}}
            if current is None:
                value.pop("revision", None)
            result = self.store.update("settings", value, current["revision"], conn) if current else self.store.insert("settings", value, conn)
            self.audit(actor, "EXTRACTION_DEFAULTS_UPDATE", "extraction", conn, {k: value[k] for k in ("max_source_lines", "model_timeout_seconds", "transient_retries", "retry_backoff_seconds")})
            return result

    def extraction_plan(self, snapshot, projection, config, options=None):
        texts = {f["path"]: self.sources.read(snapshot, f["path"]) for f in snapshot["files"] if f["path"].endswith((".java", ".md", ".txt", ".sql"))}
        plan, batches = plan_extraction(texts, projection["entities"], config["max_input_chars"], config["max_output_tokens"], options["max_source_lines"] if options else None)
        return texts, plan, batches

    def job(self, data, actor, retry_of=None, include_unknown=False):
        snapshot = self.get("snapshot", data["snapshot_id"])
        config = None
        validation = None
        if data["type"] == "EXTRACT":
            config = self.get("model", data["model_id"])
            validation = self.models.require(config)
            config = {**config, "fingerprint": self.models.fingerprint(config)}
            projection = self.get("projection", data["projection_id"])
            if projection["snapshot_id"] != snapshot["id"]:
                raise DomainError("PROJECTION_SNAPSHOT_MISMATCH")
        job = {**data, "id": "job_" + uuid.uuid4().hex, "attempt_id": uuid.uuid4().hex, "status": "CREATED", "stage": "AWAITING_START", "actor": actor, "schema_version": SCHEMA_VERSION, "prompt_version": PROMPT_VERSION, "model_config": config, "validation_id": validation["id"] if validation else None, "candidate_ids": [], "events": [{"at": now(), "status": "CREATED"}], "error": None}
        if data["type"] == "ANALYZE":
            job["analyzer_engine"] = (self.store.get("settings","analysis") or {}).get("engine", "joern" if self.settings.codegraph_url else "javac")
        if config:
            job["extraction_settings"] = self.extraction_defaults()
            _, plan, _ = self.extraction_plan(snapshot, projection, config, job["extraction_settings"])
            job["extraction_plan"] = plan
            job["execution_policy"] = {k: job["extraction_settings"][k] for k in DEFAULT_POLICY}
        if retry_of:
            job["retry_of"] = retry_of["id"]
            job["unknown_replay_authorized"] = include_unknown
            checkpoint = retry_of.get("checkpoint")
            unknowns = any(s["status"] == "UNKNOWN" for s in states_for(retry_of))
            if unknowns and not include_unknown and (not checkpoint or checkpoint.get("plan_digest") != job["extraction_plan"]["digest"]):
                raise DomainError("UNKNOWN_REPLAY_CONFIRMATION_REQUIRED", 409)
            if checkpoint and checkpoint.get("plan_digest") == job.get("extraction_plan", {}).get("digest") and retry_of["model_config"]["fingerprint"] == config["fingerprint"]:
                job["checkpoint"] = copy.deepcopy(checkpoint)
                job["extraction_plan"]["completed_batches"] = checkpoint["completed_batches"]
                job["batch_states"] = states_for(retry_of)
                for state in job["batch_states"]:
                    if state["status"] == "SUCCEEDED":
                        state["reused_from"] = retry_of["id"]
                    if state["status"] not in ("SUCCEEDED", "HELD", "SKIPPED") and (state["status"] != "UNKNOWN" or include_unknown):
                        state["previous_attempt_history"] = state.get("previous_attempt_history", []) + state.get("attempt_history", [])
                        state["previous_error"] = state.pop("error", None)
                        state["previous_error_params"] = state.pop("error_params", {})
                        state.update(status="PENDING", attempts=0, attempt_history=[])
                        state.pop("next_retry_at", None)
                job["unknown_replay_authorized"] = include_unknown
        if config:
            job["batch_states"] = states_for(job)
            report(job)
        with self.store.transaction() as conn:
            result = self.store.insert("job", job, conn)
            self.audit(actor, "JOB_CREATE", result["id"], conn)
            return result

    def retry_job(self, key, expected, actor, include_unknown=False):
        original = self.get("job", key)
        if original["revision"] != expected:
            raise DomainError("REVISION_CONFLICT", 409)
        if original["status"] not in ("FAILED", "PARTIAL_SUCCEEDED", "NEEDS_ATTENTION", "EXECUTION_UNKNOWN") or original["type"] != "EXTRACT":
            raise DomainError("INVALID_STATE_TRANSITION", 409)
        if original["status"] == "EXECUTION_UNKNOWN" and not include_unknown:
            raise DomainError("UNKNOWN_REPLAY_CONFIRMATION_REQUIRED", 409)
        if original["status"] == "EXECUTION_UNKNOWN":
            original["batch_states"] = states_for(original)
            unknown_index = (original.get("checkpoint") or {}).get("completed_batches", 0)
            if unknown_index < len(original["batch_states"]):
                original["batch_states"][unknown_index]["status"] = "UNKNOWN"
        return self.job({k: original[k] for k in ("type", "snapshot_id", "model_id", "projection_id")}, actor, original, include_unknown)

    def command(self, key, command, expected, actor):
        with self.store.transaction() as conn:
            job = self.get("job", key, conn)
            status = job["status"]
            if job["revision"] != expected:
                raise DomainError("REVISION_CONFLICT", 409)
            if command == "START" and status == "CREATED" or command == "RESUME" and status in ("PAUSED", "WAITING_SUBTASKS"):
                if job["model_config"]:
                    self.models.require(self.get("model", job["model_id"], conn))
                target = "QUEUED"
                job.pop("dispatch_indices", None)
            elif command == "PAUSE" and status in ("CREATED", "QUEUED", "WAITING_RETRY", "WAITING_SUBTASKS", "RUNNING"):
                target = "PAUSE_REQUESTED" if status == "RUNNING" else "PAUSED"
            elif command == "CANCEL" and status in ("CREATED", "QUEUED", "WAITING_RETRY", "WAITING_SUBTASKS", "PAUSED", "RUNNING", "PAUSE_REQUESTED"):
                target = "CANCEL_REQUESTED" if status in ("RUNNING", "PAUSE_REQUESTED") else "CANCELLED"
            else:
                raise DomainError("INVALID_STATE_TRANSITION", 409, {"state": status, "command": command})
            job["status"] = target
            job["events"].append({"at": now(), "status": target, "actor": actor})
            result = self.store.update("job", job, expected, conn)
            self.audit(actor, "JOB_" + command, key, conn)
            return result

    def process_one(self):
        # A standalone single worker holds an OS lock. CAS additionally protects duplicate claims.
        for original in self.store.all("job"):
            if original.get("execution_policy"):
                handled = process_resilient(self, original)
                if handled:
                    return True
                continue
            if original["status"] in ("RUNNING", "PAUSE_REQUESTED", "CANCEL_REQUESTED") and original.get("lease_until", "") < now():
                with self.store.transaction() as conn:
                    job = self.get("job", original["id"], conn)
                    if job["status"] in ("RUNNING", "PAUSE_REQUESTED", "CANCEL_REQUESTED") and job.get("lease_until", "") < now():
                        job["pending_intent"] = job["status"]
                        job["status"] = "EXECUTION_UNKNOWN"
                        job["error"] = "WORKER_LEASE_EXPIRED"
                        job["events"].append({"at": now(), "status": job["status"]})
                        self.store.update("job", job, job["revision"], conn)
                continue
            if original["status"] != "QUEUED":
                continue
            with self.store.transaction() as conn:
                job = self.get("job", original["id"], conn)
                if job["status"] != "QUEUED":
                    continue
                job["status"], job["stage"] = "RUNNING", job["type"]
                job["lease_until"] = (datetime.now(timezone.utc) + timedelta(seconds=self.settings.codegraph_timeout + 120 if job["type"] == "ANALYZE" and self.settings.codegraph_url else 180)).isoformat()
                job["events"].append({"at": now(), "status": "RUNNING"})
                job = self.store.update("job", job, job["revision"], conn)
            result = None
            error = None
            error_params = {}
            progress = None
            index = None
            try:
                snapshot = self.get("snapshot", job["snapshot_id"])
                if job.get("checkpoint") is not None and job["type"] != "EXTRACT":
                    result = job["checkpoint"]
                elif job["type"] == "ANALYZE":
                    result = self.sources.analyze(snapshot, job.get("analyzer_engine", "javac"))
                else:
                    config = self.get("model", job["model_id"])
                    validation = self.models.require(config)
                    if self.models.fingerprint(config) != job["model_config"]["fingerprint"]:
                        raise DomainError("MODEL_VALIDATION_STALE", 409)
                    projection = self.get("projection", job["projection_id"])
                    texts, plan, batches = self.extraction_plan(snapshot, projection, config, job.get("extraction_settings"))
                    expected_plan = job.get("extraction_plan", plan)
                    if expected_plan["digest"] != plan["digest"]:
                        raise DomainError("EXTRACTION_PLAN_CHANGED", 409)
                    result = copy.deepcopy(job.get("checkpoint")) or {
                        "output": {"rules": []}, "usage": {}, "input_digest": digest(texts),
                        "validation_id": validation["id"], "plan_digest": plan["digest"], "completed_batches": 0}
                    if result.get("plan_digest") != plan["digest"]:
                        raise DomainError("EXTRACTION_PLAN_CHANGED", 409)
                    index = result["completed_batches"]
                    if index < len(batches):
                        batch = batches[index]
                        args = [config, batch["source"], batch["entities"], batch["ranges"]]
                        if job.get("extraction_settings"):
                            args.append(True)
                        output, usage = self.models.call(*args)
                        # Validate every citation against both the sent range and immutable full source.
                        repairs = []
                        for rule in output["rules"]:
                            if job.get("extraction_settings"):
                                repairs.extend(align_exact_quotes(rule, texts, batch))
                            validate_batch_evidence(rule, batch)
                            self.models.validate_evidence(rule, texts, batch["entities"])
                            if partial_methods(batch):
                                marker = "PARTIAL_METHOD_CONTEXT: This batch contains an incomplete method; verify full conditions, branches and exceptions before approval."
                                if marker not in rule["uncertainties"]:
                                    rule["uncertainties"] = rule["uncertainties"][:29] + [marker]
                        if repairs:
                            result.setdefault("evidence_line_corrections", []).append({"batch": index + 1, "corrections": repairs})
                        result["output"]["rules"].extend(output["rules"])
                        for k, v in usage.items():
                            if isinstance(v, (int, float)):
                                result["usage"][k] = result["usage"].get(k, 0) + v
                        result["completed_batches"] += 1
                    progress = {**plan, "completed_batches": result["completed_batches"]}
                if job["type"] == "EXTRACT":
                    self.models.require(self.get("model", job["model_id"]))
            except DomainError as exc:
                error = exc.code
                error_params = exc.params
                if index is not None:
                    error_params = {**error_params, "batch": index + 1}
            except Exception:
                error = "WORKER_INTERNAL_ERROR"
            with self.store.transaction() as conn:
                current = self.get("job", job["id"], conn)
                if progress is not None:
                    current["extraction_plan"] = progress
                # Only successfully validated batches become durable checkpoints.
                if result is not None and not error and job["type"] == "EXTRACT":
                    current["checkpoint"] = result
                if current["status"] == "CANCEL_REQUESTED":
                    current["status"] = "CANCELLED"
                    current["result_discarded"] = True
                elif current["status"] == "PAUSE_REQUESTED" and not error:
                    # Paused after current safe boundary. On resume do not silently reuse changed config.
                    current["status"] = "PAUSED"
                    current["checkpoint"] = result
                elif error:
                    current["status"] = "EXECUTION_UNKNOWN" if error == "MODEL_TIMEOUT" else "FAILED"
                    current["error"] = error
                    current["error_params"] = error_params
                elif progress is not None and progress["completed_batches"] < progress["total_batches"]:
                    current["status"] = "QUEUED"
                    current["stage"] = "EXTRACT"
                else:
                    if job["type"] == "ANALYZE":
                        current["projection_id"] = result["id"]
                    else:
                        # Re-check revocation before accepting returned model data.
                        projection = self.get("projection", job["projection_id"], conn)
                        ids = []
                        for rule in result["output"]["rules"]:
                            rid = identity("rule", [job["snapshot_id"], job["projection_id"], rule])
                            if not self.store.get("candidate", rid, conn):
                                candidate = {**rule, "id": rid, "status": "CANDIDATE", "job_id": job["id"], "snapshot_id": job["snapshot_id"], "projection_id": projection["id"], "origin_kind": "MODEL_CANDIDATE", "content_revision": 1}
                                self.store.insert("candidate", candidate, conn)
                            if rid not in ids:
                                ids.append(rid)
                        current["candidate_ids"] = ids
                        current["usage"] = result["usage"]
                        current["evidence_line_corrections"] = result.get("evidence_line_corrections", [])
                        current["input_digest"] = result["input_digest"]
                    current["status"] = "SUCCEEDED"
                    current["stage"] = "CANDIDATES_READY" if job["type"] == "EXTRACT" else "FACTS_READY"
                current["events"].append({"at": now(), "status": current["status"], "error": current["error"]})
                self.store.update("job", current, current["revision"], conn)
                self.audit("worker", "JOB_" + current["status"], current["id"], conn)
            return True
        return False

    def review(self, key, data, actor):
        with self.store.transaction() as conn:
            candidate = self.get("candidate", key, conn)
            if candidate["revision"] != data["expected_revision"]:
                raise DomainError("REVISION_CONFLICT", 409)
            if candidate["status"] == "PUBLISHED":
                raise DomainError("PUBLISHED_IMMUTABLE", 409)
            changes = data.get("changes")
            if changes:
                draft = {k: candidate[k] for k in RULE_SCHEMA["properties"]["rules"]["items"]["properties"]}
                draft.update(changes)
                from jsonschema import validate
                validate({"rules": [draft]}, RULE_SCHEMA)
                snapshot = self.get("snapshot", candidate["snapshot_id"], conn)
                projection = self.get("projection", candidate["projection_id"], conn)
                self.models.validate_evidence(draft, {f["path"]: self.sources.read(snapshot, f["path"]) for f in snapshot["files"]}, projection["entities"])
                candidate.update(draft)
                candidate["content_revision"] += 1
                candidate["status"] = "CANDIDATE"
                candidate.pop("approval_id", None)
                if data["decision"] != "EDIT":
                    raise DomainError("EDIT_REQUIRES_SEPARATE_REVIEW", 422)
            elif data["decision"] in ("APPROVE", "REJECT"):
                if data["decision"] == "APPROVE":
                    if candidate["uncertainties"]:
                        raise DomainError("UNRESOLVED_UNCERTAINTIES", 409)
                    snapshot = self.get("snapshot", candidate["snapshot_id"], conn)
                    projection = self.get("projection", candidate["projection_id"], conn)
                    self.models.validate_evidence(candidate, {f["path"]: self.sources.read(snapshot, f["path"]) for f in snapshot["files"]}, projection["entities"])
                approval = self.store.insert("review", {"id": "review_" + uuid.uuid4().hex, "candidate_id": key, "content_revision": candidate["content_revision"], "content_digest": self.candidate_digest(candidate), "decision": data["decision"], "reason": data["reason"], "actor": actor}, conn)
                candidate["status"] = "APPROVED" if data["decision"] == "APPROVE" else "REJECTED"
                candidate["approval_id"] = approval["id"]
            else:
                raise DomainError("INVALID_REVIEW_DECISION")
            result = self.store.update("candidate", candidate, candidate["revision"], conn)
            self.audit(actor, "REVIEW_" + data["decision"], key, conn)
            return result

    def candidate_digest(self, candidate):
        return digest({k: candidate[k] for k in [*RULE_SCHEMA["properties"]["rules"]["items"]["properties"], "content_revision", "snapshot_id", "projection_id"]})

    def publish(self, data, actor):
        candidates, snapshots, projections, nodes, edges = [], {}, {}, {}, {}
        for key in data["candidate_ids"]:
            item = self.get("candidate", key)
            if item["status"] not in ("APPROVED", "PUBLISHED"):
                raise DomainError("UNAPPROVED_KNOWLEDGE", 409, {"id": key})
            review = self.get("review", item.get("approval_id", ""))
            if review["decision"] != "APPROVE" or review["content_digest"] != self.candidate_digest(item):
                raise DomainError("REVIEW_STALE", 409)
            snapshot = self.get("snapshot", item["snapshot_id"])
            projection = self.get("projection", item["projection_id"])
            texts = {f["path"]: self.sources.read(snapshot, f["path"]) for f in snapshot["files"]}
            self.models.validate_evidence(item, texts, projection["entities"])
            if any(s["repository_id"] == snapshot["repository_id"] and s["id"] != snapshot["id"] for s in snapshots.values()):
                raise DomainError("MIXED_REPOSITORY_BASELINES", 409)
            snapshots[snapshot["id"]] = snapshot
            projections[projection["id"]] = projection
            candidates.append(copy.deepcopy(item))
            for ent in projection["entities"]:
                nodes[ent["id"]] = ent
            for edge in projection["edges"]:
                edges[edge["id"]] = edge
            nodes[item["id"]] = {"id": item["id"], "kind": "BusinessRule", "name": item["name"], "status": "PUBLISHED", "snapshot_id": item["snapshot_id"]}
            mapping = {"id": identity("mapping", [item["id"], item["content_revision"], item["entity_version_id"]]), "source": item["id"], "target": item["entity_version_id"], "type": "IMPLEMENTED_BY", "review_id": review["id"]}
            edges[mapping["id"]] = mapping
        if not candidates:
            raise DomainError("EMPTY_RELEASE")
        frozen_rules = [{k: v for k, v in item.items() if k not in ("revision", "status")} | {"status": "PUBLISHED"} for item in candidates]
        ontology_nodes, ontology_edges, frozen_ontology = ontology.graph_nodes(self,list(projections),[c['id'] for c in candidates],list(nodes),True)
        nodes.update({n['id']:n for n in ontology_nodes});edges.update({e['id']:e for e in ontology_edges})
        core = {"schema_version": SCHEMA_VERSION, "ontology": frozen_ontology, "rules": frozen_rules, "source_snapshots": list(snapshots.values()), "projection_ids": list(projections), "nodes": list(nodes.values()), "edges": list(edges.values())}
        release = {**core, "id": identity("release", core), "digest": digest(core)}
        # Durable preparation ledger survives a crash between graph construction and pointer commit.
        with self.store.transaction() as conn:
            staged = self.store.get("graph_sync", release["id"], conn)
            if not staged:
                self.store.insert("graph_sync", {"id": release["id"], "status": "PENDING", "manifest": release, "actor": actor}, conn)
        try:
            projection_status = self.graph.prepare(release)
        except DomainError as exc:
            with self.store.transaction() as conn:
                staged = self.get("graph_sync", release["id"], conn)
                self.store.update("graph_sync", {**staged, "status": "FAILED", "error": exc.code}, staged["revision"], conn)
            raise
        with self.store.transaction() as conn:
            staged = self.get("graph_sync", release["id"], conn)
            self.store.update("graph_sync", {**staged, "status": "READY", "error": None}, staged["revision"], conn)
        # Graph ready first. One relational transaction validates revisions and switches pointer last.
        with self.store.transaction() as conn:
            pointer = self.store.get("pointer", "current", conn)
            revision = pointer["revision"] if pointer else 0
            if revision != data["expected_revision"]:
                raise DomainError("REVISION_CONFLICT", 409)
            for item in candidates:
                current = self.get("candidate", item["id"], conn)
                if current["revision"] != item["revision"]:
                    raise DomainError("REVIEW_STALE", 409)
                current["status"] = "PUBLISHED"
                self.store.update("candidate", current, current["revision"], conn)
            existing = self.store.get("release", release["id"], conn)
            if not existing:
                release = self.store.insert("release", {**release, "projection_status": projection_status, "actor": actor}, conn)
            else:
                release = existing
            new_pointer = {"id": "current", "release_id": release["id"], "reason": data["reason"], "actor": actor}
            if pointer:
                self.store.update("pointer", new_pointer, revision, conn)
            else:
                self.store.insert("pointer", new_pointer, conn)
            staged = self.get("graph_sync", release["id"], conn)
            self.store.update("graph_sync", {**staged, "status": "COMMITTED"}, staged["revision"], conn)
            self.audit(actor, "RELEASE_PUBLISH", release["id"], conn, {"reason": data["reason"]})
        return release
