"""跨平台文件锁：POSIX 使用 fcntl.flock，Windows 使用 msvcrt.locking。

非阻塞模式下无法获得锁时统一抛出 BlockingIOError。
"""
import os
import time
from contextlib import contextmanager

if os.name == "nt":
    import msvcrt
else:
    import fcntl


@contextmanager
def file_lock(handle, blocking=True):
    """对已打开的文件句柄加排他锁，退出时释放。"""
    if os.name == "nt":
        handle.seek(0)
        while True:
            try:
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
                break
            except OSError:
                if not blocking:
                    raise BlockingIOError("文件已被其他进程锁定")
                time.sleep(0.1)
        try:
            yield
        finally:
            handle.seek(0)
            msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
    else:
        flags = fcntl.LOCK_EX if blocking else fcntl.LOCK_EX | fcntl.LOCK_NB
        try:
            fcntl.flock(handle, flags)
        except BlockingIOError:
            raise BlockingIOError("文件已被其他进程锁定")
        try:
            yield
        finally:
            fcntl.flock(handle, fcntl.LOCK_UN)
