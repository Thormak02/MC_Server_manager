from __future__ import annotations

import json
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path


@dataclass(slots=True)
class ManagerUpdateStatus:
    ok: bool
    message: str
    branch: str = "-"
    local_commit_short: str = "-"
    remote_commit_short: str = "-"
    ahead_count: int = 0
    behind_count: int = 0
    dirty: bool = False

    @property
    def has_update(self) -> bool:
        return self.behind_count > 0

    @property
    def can_apply(self) -> bool:
        return self.ok and self.has_update and not self.dirty


def _repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def _run_git(args: list[str], *, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=str(cwd),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )


def _read_service_name_from_meta(repo_path: Path) -> str:
    meta_path = repo_path / "data" / "service_meta.json"
    if not meta_path.exists():
        return "mc-server-manager"
    try:
        payload = json.loads(meta_path.read_text(encoding="utf-8"))
    except Exception:
        return "mc-server-manager"
    if not isinstance(payload, dict):
        return "mc-server-manager"
    raw = str(payload.get("service_name") or "").strip()
    return raw or "mc-server-manager"


def get_manager_update_status(*, fetch_remote: bool) -> ManagerUpdateStatus:
    repo_path = _repo_root()
    if not (repo_path / ".git").exists():
        return ManagerUpdateStatus(ok=False, message="Kein Git-Repository gefunden.")
    if shutil.which("git") is None:
        return ManagerUpdateStatus(ok=False, message="Git ist nicht installiert oder nicht im PATH.")

    branch_result = _run_git(["rev-parse", "--abbrev-ref", "HEAD"], cwd=repo_path)
    if branch_result.returncode != 0:
        return ManagerUpdateStatus(ok=False, message="Branch konnte nicht ermittelt werden.")
    branch = (branch_result.stdout or "").strip() or "main"

    if fetch_remote:
        fetch_result = _run_git(["fetch", "origin", branch], cwd=repo_path)
        if fetch_result.returncode != 0:
            return ManagerUpdateStatus(
                ok=False,
                message=f"Fetch fehlgeschlagen: {(fetch_result.stdout or '').strip()}",
                branch=branch,
            )

    local_result = _run_git(["rev-parse", "--short", "HEAD"], cwd=repo_path)
    if local_result.returncode != 0:
        return ManagerUpdateStatus(ok=False, message="Lokaler Commit konnte nicht ermittelt werden.", branch=branch)
    local_commit_short = (local_result.stdout or "").strip() or "-"

    remote_ref = f"origin/{branch}"
    remote_result = _run_git(["rev-parse", "--short", remote_ref], cwd=repo_path)
    if remote_result.returncode != 0:
        return ManagerUpdateStatus(
            ok=False,
            message=f"Remote-Branch '{remote_ref}' nicht verfuegbar.",
            branch=branch,
            local_commit_short=local_commit_short,
        )
    remote_commit_short = (remote_result.stdout or "").strip() or "-"

    count_result = _run_git(["rev-list", "--left-right", "--count", f"HEAD...{remote_ref}"], cwd=repo_path)
    if count_result.returncode != 0:
        return ManagerUpdateStatus(
            ok=False,
            message="Commit-Differenz konnte nicht berechnet werden.",
            branch=branch,
            local_commit_short=local_commit_short,
            remote_commit_short=remote_commit_short,
        )
    count_parts = (count_result.stdout or "").strip().split()
    ahead_count = int(count_parts[0]) if len(count_parts) > 0 and count_parts[0].isdigit() else 0
    behind_count = int(count_parts[1]) if len(count_parts) > 1 and count_parts[1].isdigit() else 0

    dirty_result = _run_git(["status", "--porcelain"], cwd=repo_path)
    dirty = bool((dirty_result.stdout or "").strip())

    if behind_count > 0:
        if dirty:
            message = (
                f"Update verfuegbar ({behind_count} Commit(s) hinter {remote_ref}), "
                "aber Working Tree ist nicht sauber."
            )
        else:
            message = f"Update verfuegbar ({behind_count} Commit(s) hinter {remote_ref})."
    elif ahead_count > 0:
        message = f"Kein Remote-Update. Lokaler Stand ist {ahead_count} Commit(s) vor {remote_ref}."
    else:
        message = "Bereits auf aktuellem Stand."

    return ManagerUpdateStatus(
        ok=True,
        message=message,
        branch=branch,
        local_commit_short=local_commit_short,
        remote_commit_short=remote_commit_short,
        ahead_count=ahead_count,
        behind_count=behind_count,
        dirty=dirty,
    )


def trigger_manager_update() -> tuple[bool, str]:
    status = get_manager_update_status(fetch_remote=True)
    if not status.ok:
        return False, status.message
    if not status.has_update:
        return False, "Kein Update verfuegbar."
    if status.dirty:
        return False, "Update blockiert: Working Tree ist nicht sauber."

    repo_path = _repo_root()
    deploy_script = repo_path / "scripts" / "deploy_from_github.ps1"
    if not deploy_script.exists():
        return False, f"Deploy-Skript fehlt: {deploy_script}"

    service_name = _read_service_name_from_meta(repo_path)
    startup_task_name = "mc-server-manager-startup"
    update_log_path = repo_path / "data" / "logs" / "manager-update.log"
    update_log_path.parent.mkdir(parents=True, exist_ok=True)
    command = [
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        str(deploy_script),
        "-RepoPath",
        str(repo_path),
        "-Branch",
        status.branch,
        "-ServiceName",
        service_name,
        "-StartupTaskName",
        startup_task_name,
        "-PythonExe",
        "python",
    ]

    creationflags = 0
    for name in ("CREATE_NEW_PROCESS_GROUP", "DETACHED_PROCESS", "CREATE_NO_WINDOW"):
        creationflags |= int(getattr(subprocess, name, 0))

    try:
        with update_log_path.open("ab") as log_handle:
            subprocess.Popen(
                command,
                cwd=str(repo_path),
                stdin=subprocess.DEVNULL,
                stdout=log_handle,
                stderr=subprocess.STDOUT,
                close_fds=True,
                creationflags=creationflags,
            )
    except Exception as exc:
        return False, f"Update-Prozess konnte nicht gestartet werden: {exc}"

    return (
        True,
        (
            "Update wurde gestartet. Details unter "
            f"'{update_log_path.as_posix()}'. "
            "Im Startup-Task-Modus ist ggf. ein Task-/Server-Neustart noetig, "
            "damit der neue Code aktiv wird."
        ),
    )
