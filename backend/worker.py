import time
from .config import Settings
from .filelock import file_lock
from .service import Studio
from .store import Conflict, Store


def main():
    settings = Settings()
    with (settings.data_dir / "worker.lock").open("w") as lock:
        try:
            with file_lock(lock, blocking=False):
                run(settings)
        except BlockingIOError:
            raise SystemExit("同一数据目录只允许运行一个 M1 Worker")


def run(settings):
    service = Studio(settings, Store(settings.database_url))
    print("M1 Worker 已启动；运行状态持久化，未知执行不自动重跑。", flush=True)
    while True:
        try:
            if not service.process_one():
                time.sleep(1)
        except Conflict:
            time.sleep(0.2)
        except Exception as exc:
            # Do not log model payloads/credentials/source text.
            print("worker recovery:", type(exc).__name__, flush=True)
            time.sleep(1)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
