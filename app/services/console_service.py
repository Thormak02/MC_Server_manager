import asyncio
from collections import defaultdict, deque
from dataclasses import dataclass
from datetime import datetime
from threading import RLock

from fastapi import WebSocket


@dataclass(frozen=True)
class WebSocketSubscription:
    websocket: WebSocket
    loop: asyncio.AbstractEventLoop


class ConsoleService:
    def __init__(self) -> None:
        self._lines: dict[int, deque[str]] = defaultdict(lambda: deque(maxlen=1000))
        self._sockets: dict[int, set[WebSocketSubscription]] = defaultdict(set)
        self._lock = RLock()

    def _normalize_line(self, line: str) -> str:
        clean = line.rstrip("\r\n")
        timestamp = datetime.now().strftime("%H:%M:%S")
        return f"[{timestamp}] {clean}"

    def append_output(self, server_id: int, line: str) -> None:
        if not line:
            return
        normalized = self._normalize_line(line)
        with self._lock:
            self._lines[server_id].append(normalized)
            subscriptions = list(self._sockets.get(server_id, set()))

        for subscription in subscriptions:
            future = asyncio.run_coroutine_threadsafe(
                subscription.websocket.send_text(normalized),
                subscription.loop,
            )
            try:
                future.result(timeout=0.4)
            except Exception:
                self.unregister_websocket(server_id, subscription.websocket)

    def get_recent_lines(self, server_id: int, limit: int = 500) -> list[str]:
        with self._lock:
            lines = list(self._lines.get(server_id, []))
        if limit <= 0:
            return lines
        return lines[-limit:]

    def register_websocket(self, server_id: int, websocket: WebSocket) -> None:
        loop = asyncio.get_running_loop()
        subscription = WebSocketSubscription(websocket=websocket, loop=loop)
        with self._lock:
            self._sockets[server_id].add(subscription)

    def unregister_websocket(self, server_id: int, websocket: WebSocket) -> None:
        with self._lock:
            current = self._sockets.get(server_id)
            if not current:
                return
            to_remove = [entry for entry in current if entry.websocket is websocket]
            for entry in to_remove:
                current.discard(entry)
            if not current:
                self._sockets.pop(server_id, None)


console_service = ConsoleService()
