"""HTTP protocol fixture for browser tests only. Not a real LLM or quality benchmark."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Fixture(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        request = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        user = json.loads(request["messages"][1]["content"])
        if "DIAGNOSTICS" in user:
            result = {"summary": "TEST ONLY · 引用未落在所选实体范围内，建议保留成功结果并带修正提示重试。", "groups": [{"group_id": group["group_id"], "action": "RETRY", "reason": "TEST ONLY · 规则证据需与代码实体的路径和行号范围一致。", "guidance": "TEST ONLY · 选择本批次实体，引用其范围内的逐字源码；不存在支持规则时返回空规则，不编造证据。"} for group in user["DIAGNOSTICS"]]}
            value = {"model": request["model"], "choices": [{"message": {"content": json.dumps(result, ensure_ascii=False)}}], "usage": {"prompt_tokens": 100, "completion_tokens": 80}}
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(value).encode())
            return
        entity = next(e for e in user["entities"] if e["kind"] == "Method" and "allowed" in e.get("signature", ""))
        data = user["SOURCE_DATA"][entity["path"]]
        rows = data if isinstance(data, list) else [{"line": i + 1, "text": line} for i, line in enumerate(data.splitlines())]
        row = next(row for row in rows if "return amount <= 100" in row["text"])
        n = row["line"]
        value = {"model": request["model"], "choices": [{"message": {"content": json.dumps({"rules": [{"name": "TEST ONLY · 额度上限规则", "condition": "amount <= 100", "outcome": "额度检查通过", "scope": "仅用于自动化测试", "exceptions": [], "uncertainties": [], "entity_version_id": entity["id"], "evidence": [{"path": entity["path"], "start_line": n, "end_line": n, "quote": row["text"].strip()}]}]}, ensure_ascii=False)}}], "usage": {"prompt_tokens": 100, "completion_tokens": 80}}
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(value).encode())


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 8099), Fixture).serve_forever()
