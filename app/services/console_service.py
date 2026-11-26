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
        # WIE VIELE ZEILEN JE ANGEKOMMEN SIND - nicht, wie viele noch im
        # Puffer liegen. Der Unterschied ist der ganze Zweck: Der Puffer hat
        # eine Hoechstlaenge, seine Laenge waechst also nicht monoton, und
        # zwei gleiche Zeilen sind in ihm nicht auseinanderzuhalten. Mit diesem
        # Zaehler kann ein Aufrufer sagen "gib mir, was seit JETZT kam" -
        # und genau das braucht send_console_command, um die Antwort des
        # Servers auf seinen Befehl zu finden.
        self._gezaehlt: dict[int, int] = defaultdict(int)
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
            self._gezaehlt[server_id] += 1
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

    def gezaehlt(self, server_id: int) -> int:
        """Wie viele Zeilen fuer diesen Server je angekommen sind.

        Eine MARKE und keine Laenge: Wer sie vor und nach einer Handlung holt,
        weiss genau, wie viele Zeilen dazwischen kamen - auch wenn der Puffer
        inzwischen vorne etwas verloren hat.
        """
        with self._lock:
            return self._gezaehlt[server_id]

    def seit(self, server_id: int, marke: int, hoechstens: int = 20) -> list[str]:
        """Die Zeilen, die seit dieser Marke kamen - hoechstens so viele."""
        with self._lock:
            neue = self._gezaehlt[server_id] - marke
            if neue <= 0:
                return []
            zeilen = list(self._lines.get(server_id, []))
        # Hat der Ringpuffer inzwischen vorne verloren, ist weniger da als
        # gezaehlt - dann kommt eben, was noch da ist. Ein Aufrufer, der auf
        # die ZAHL baute, bekaeme hier sonst eine zu lange Liste.
        genommen = zeilen[-min(neue, len(zeilen)):]
        return genommen[-hoechstens:] if hoechstens > 0 else genommen

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
