from __future__ import annotations

import json
import os
import sys
import threading
import traceback
from pathlib import Path
from typing import Any

import servicemanager
import uvicorn
import win32event
import win32service
import win32serviceutil

REPO_ROOT = Path(__file__).resolve().parents[1]
SERVICE_META_PATH = REPO_ROOT / "data" / "service_meta.json"
SERVICE_CONFIG_PATH = REPO_ROOT / "data" / "service_config.json"
SERVICE_LOG_PATH = REPO_ROOT / "data" / "service_runtime.log"


def _load_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}


def _load_service_meta() -> dict[str, str]:
    data = _load_json(SERVICE_META_PATH)
    return {
        "service_name": str(data.get("service_name", "mc-server-manager")),
        "display_name": str(data.get("display_name", "MC Server Manager")),
        "description": str(data.get("description", "Minecraft Server Manager (FastAPI/uvicorn)")),
    }


def _append_runtime_log(message: str) -> None:
    try:
        SERVICE_LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
        with SERVICE_LOG_PATH.open("a", encoding="utf-8") as handle:
            handle.write(message.rstrip() + "\n")
    except Exception:
        pass


def _load_runtime_config() -> dict[str, Any]:
    data = _load_json(SERVICE_CONFIG_PATH)
    listen_host = str(data.get("listen_host", "0.0.0.0"))
    port_raw = data.get("port", 8000)
    try:
        port = int(port_raw)
    except (TypeError, ValueError):
        port = 8000
    return {"listen_host": listen_host, "port": port}


SERVICE_META = _load_service_meta()


class McServerManagerService(win32serviceutil.ServiceFramework):
    _svc_name_ = SERVICE_META["service_name"]
    _svc_display_name_ = SERVICE_META["display_name"]
    _svc_description_ = SERVICE_META["description"]

    def __init__(self, args):
        super().__init__(args)
        self.stop_event = win32event.CreateEvent(None, 0, 0, None)
        self.server: uvicorn.Server | None = None
        self.server_thread: threading.Thread | None = None

    def SvcStop(self):
        self.ReportServiceStatus(win32service.SERVICE_STOP_PENDING)
        if self.server is not None:
            self.server.should_exit = True
        win32event.SetEvent(self.stop_event)

    def SvcDoRun(self):
        _append_runtime_log(f"{self._svc_name_}: service is starting")
        try:
            servicemanager.LogInfoMsg(f"{self._svc_name_} service is starting")
        except Exception:
            pass
        try:
            self._run()
        except Exception as exc:
            _append_runtime_log(f"{self._svc_name_}: crashed: {exc}")
            _append_runtime_log(traceback.format_exc())
            try:
                servicemanager.LogErrorMsg(f"{self._svc_name_} crashed: {exc}")
            except Exception:
                pass
            raise
        finally:
            _append_runtime_log(f"{self._svc_name_}: service has stopped")
            try:
                servicemanager.LogInfoMsg(f"{self._svc_name_} service has stopped")
            except Exception:
                pass

    def _run(self):
        os.chdir(REPO_ROOT)
        repo_root_text = str(REPO_ROOT)
        if repo_root_text not in sys.path:
            sys.path.insert(0, repo_root_text)
        runtime_config = _load_runtime_config()
        _append_runtime_log(
            f"{self._svc_name_}: runtime config host={runtime_config['listen_host']} port={runtime_config['port']}"
        )

        from app.main import app

        uvicorn_config = uvicorn.Config(
            app,
            host=runtime_config["listen_host"],
            port=runtime_config["port"],
            log_level="info",
        )
        self.server = uvicorn.Server(uvicorn_config)
        self.server_thread = threading.Thread(target=self.server.run, daemon=True)
        self.server_thread.start()

        while self.server_thread.is_alive():
            result = win32event.WaitForSingleObject(self.stop_event, 1000)
            if result == win32event.WAIT_OBJECT_0:
                break

        if self.server is not None:
            self.server.should_exit = True
        if self.server_thread is not None:
            self.server_thread.join(timeout=30)


if __name__ == "__main__":
    win32serviceutil.HandleCommandLine(McServerManagerService)
