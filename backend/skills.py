"""Local SDLC Skill file storage used by the demo portal."""
from pathlib import Path
import os
import re
import shutil
from .config import ROOT

CATEGORIES = (
    {"id": "requirement-analysis", "name": "需求分析", "folder": "需求分析"},
    {"id": "existing-system-understanding", "name": "存量理解", "folder": "存量理解"},
    {"id": "impact-assessment", "name": "影响评估", "folder": "影响评估"},
    {"id": "controlled-development", "name": "受控开发", "folder": "受控开发"},
    {"id": "test-validation", "name": "测试验证", "folder": "测试验证"},
    {"id": "delivery-traceability", "name": "交付追溯", "folder": "交付追溯"},
)
MAX_UPLOAD = 50 * 1024 * 1024
_INVALID_NAME = re.compile(r'[<>:"/\\|?*\x00-\x1f]')
_SEED_DIRS = (ROOT / "seed-skills", ROOT / "frontend" / "src" / "skills")


class SkillError(Exception):
    def __init__(self, code: str, status: int):
        super().__init__(code)
        self.code = code
        self.status = status


def _category(category_id: str) -> dict:
    for category in CATEGORIES:
        if category["id"] == category_id:
            return category
    raise SkillError("INVALID_CATEGORY", 400)


def _safe_name(filename: str) -> str:
    name = Path(filename).name
    if not name or name != filename or _INVALID_NAME.search(name):
        raise SkillError("INVALID_FILENAME", 400)
    return name


def _seed(directory: Path) -> None:
    try:
        if directory.is_dir() and any(not entry.name.startswith(".") for entry in directory.iterdir()):
            return
    except OSError:
        return
    for seed in _SEED_DIRS:
        if not seed.is_dir():
            continue
        try:
            directory.mkdir(parents=True, exist_ok=True, mode=0o700)
            for category in CATEGORIES:
                source = seed / category["folder"]
                if not source.is_dir():
                    continue
                target = directory / category["folder"]
                target.mkdir(parents=True, exist_ok=True, mode=0o700)
                for entry in source.iterdir():
                    if not entry.is_file() or entry.name.startswith("."):
                        continue
                    destination = target / entry.name
                    if not destination.exists():
                        shutil.copy2(entry, destination)
        except OSError:
            continue
        return


def _root(settings) -> Path:
    directory = settings.data_dir / "skills"
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    _seed(directory)
    return directory


def list_categories(settings) -> list[dict]:
    directory = _root(settings)
    result = []
    for category in CATEGORIES:
        files = []
        try:
            for entry in (directory / category["folder"]).iterdir():
                if not entry.is_file() or entry.name.startswith("."):
                    continue
                try:
                    stat = entry.stat()
                    files.append({"name": entry.name, "size": stat.st_size, "updatedAt": round(stat.st_mtime * 1000)})
                except OSError:
                    continue
        except OSError:
            pass
        files.sort(key=lambda item: item["updatedAt"], reverse=True)
        result.append({"id": category["id"], "name": category["name"], "folder": category["folder"], "files": files})
    return result


def save(settings, category_id: str, filename: str, payload: bytes) -> dict:
    category = _category(category_id)
    name = _safe_name(filename)
    if not payload:
        raise SkillError("EMPTY_FILE", 400)
    if len(payload) > MAX_UPLOAD:
        raise SkillError("FILE_TOO_LARGE", 413)
    directory = _root(settings) / category["folder"]
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    target = directory / name
    try:
        fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        raise SkillError("FILE_EXISTS", 409)
    except OSError:
        raise SkillError("WRITE_FAILED", 500)
    try:
        with os.fdopen(fd, "wb") as out:
            out.write(payload)
    except OSError:
        try:
            target.unlink()
        except OSError:
            pass
        raise SkillError("WRITE_FAILED", 500)
    return {"ok": True, "path": f"skills/{category['folder']}/{name}", "size": len(payload)}


def download(settings, category_id: str, filename: str) -> Path:
    category = _category(category_id)
    name = _safe_name(filename)
    target = _root(settings) / category["folder"] / name
    try:
        if not target.is_file():
            raise OSError
    except OSError:
        raise SkillError("FILE_NOT_FOUND", 404)
    return target
