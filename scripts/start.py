"""Start the M1 API and single Worker together; stop both with Ctrl+C."""
import signal
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
children = []


def stop(*_):
    for child in children:
        if child.poll() is None:
            child.terminate()


def main():
    if not (ROOT / "frontend/dist/index.html").exists():
        raise SystemExit("请先运行 npm --prefix frontend run build")
    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)
    try:
        for module in ("backend.api", "backend.worker"):
            children.append(subprocess.Popen([sys.executable, "-m", module], cwd=ROOT))
        print("工作区：http://127.0.0.1:8000；初始管理员：admin / admin123", flush=True)
        while all(p.poll() is None for p in children):
            time.sleep(0.5)
    finally:
        stop()
        for child in children:
            try:
                child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                child.kill()


if __name__ == "__main__":
    main()
