"""Lobby-API: der TCP/JSON-Endpoint, ueber den das Plugin vor dem Transfer nachfragt."""
import json
import socket

import pytest

from app.services import lobby_api_service

_TOKEN = "geheim-token-1234"


@pytest.fixture()
def endpoint():
    """Endpoint auf einem freien Port starten und danach sicher wieder abbauen."""
    probe = socket.socket()
    probe.bind(("127.0.0.1", 0))
    port = probe.getsockname()[1]
    probe.close()
    assert lobby_api_service.start_server(port, _TOKEN) is True
    try:
        yield port
    finally:
        lobby_api_service.stop_server()


def _ask(port: int, payload: dict, *, timeout: float = 5.0) -> dict | None:
    with socket.create_connection(("127.0.0.1", port), timeout=timeout) as sock:
        sock.settimeout(timeout)
        sock.sendall((json.dumps(payload) + "\n").encode("utf-8"))
        buffer = bytearray()
        while b"\n" not in buffer:
            chunk = sock.recv(4096)
            if not chunk:
                return None
            buffer.extend(chunk)
    return json.loads(buffer.split(b"\n", 1)[0].decode("utf-8"))


def test_join_check_allows(endpoint, monkeypatch):
    monkeypatch.setattr("app.services.lobby_service.check_join_allowed_by_id",
                        lambda sid, name: (True, ""))
    assert _ask(endpoint, {"token": _TOKEN, "op": "join_check",
                           "server_id": 7, "player": "Thormak"}) == {"ok": True}


def test_join_check_rejects_with_reason(endpoint, monkeypatch):
    monkeypatch.setattr("app.services.lobby_service.check_join_allowed_by_id",
                        lambda sid, name: (False, "David ist voll (20/20)."))
    answer = _ask(endpoint, {"token": _TOKEN, "op": "join_check",
                             "server_id": 7, "player": "Thormak"})
    assert answer == {"ok": False, "reason": "David ist voll (20/20)."}


def test_join_check_passes_player_and_server_through(endpoint, monkeypatch):
    seen = {}

    def check(server_id, player):
        seen.update(server_id=server_id, player=player)
        return True, ""

    monkeypatch.setattr("app.services.lobby_service.check_join_allowed_by_id", check)
    _ask(endpoint, {"token": _TOKEN, "op": "join_check", "server_id": "42", "player": "David"})
    assert seen == {"server_id": 42, "player": "David"}


def test_reason_keeps_umlauts(endpoint, monkeypatch):
    monkeypatch.setattr("app.services.lobby_service.check_join_allowed_by_id",
                        lambda sid, name: (False, "Du bist gebannt: Grießbrei"))
    answer = _ask(endpoint, {"token": _TOKEN, "op": "join_check",
                             "server_id": 1, "player": "x"})
    assert answer["reason"] == "Du bist gebannt: Grießbrei"


def test_wrong_token_is_not_a_rejection(endpoint, monkeypatch):
    """Falscher Token -> KEIN ok:false. Das Plugin soll dann durchlassen, nicht aussperren."""
    monkeypatch.setattr("app.services.lobby_service.check_join_allowed_by_id",
                        lambda sid, name: (False, "gebannt"))
    answer = _ask(endpoint, {"token": "falsch", "op": "join_check",
                             "server_id": 7, "player": "Thormak"})
    assert answer == {"error": "auth"}
    assert "ok" not in answer


def test_missing_token_is_rejected(endpoint):
    assert _ask(endpoint, {"op": "join_check", "server_id": 7}) == {"error": "auth"}


def test_garbage_line_answers_bad_request(endpoint):
    with socket.create_connection(("127.0.0.1", endpoint), timeout=5) as sock:
        sock.sendall(b"das ist kein json\n")
        assert b"bad_request" in sock.recv(4096)


def test_unknown_op_has_no_ok_field(endpoint):
    answer = _ask(endpoint, {"token": _TOKEN, "op": "was_auch_immer"})
    assert answer == {"error": "unknown_op"}


def test_bad_server_id_has_no_ok_field(endpoint):
    answer = _ask(endpoint, {"token": _TOKEN, "op": "join_check", "server_id": "abc"})
    assert answer == {"error": "bad_server_id"}


def test_internal_error_is_not_a_rejection(endpoint, monkeypatch):
    def boom(sid, name):
        raise RuntimeError("DB weg")

    monkeypatch.setattr("app.services.lobby_service.check_join_allowed_by_id", boom)
    answer = _ask(endpoint, {"token": _TOKEN, "op": "join_check",
                             "server_id": 7, "player": "x"})
    assert answer == {"error": "internal"} and "ok" not in answer


def test_ping_op(endpoint):
    assert _ask(endpoint, {"token": _TOKEN, "op": "ping"}) == {"ok": True, "pong": True}


def test_oversized_line_is_dropped(endpoint):
    with socket.create_connection(("127.0.0.1", endpoint), timeout=5) as sock:
        try:
            sock.sendall(b"x" * (32 * 1024) + b"\n")
        except OSError:
            pass  # Gegenseite hat schon zugemacht - genau das ist erwuenscht
        sock.settimeout(5)
        try:
            assert sock.recv(4096) == b""
        except OSError:
            pass


def test_endpoint_survives_many_sequential_requests(endpoint, monkeypatch):
    monkeypatch.setattr("app.services.lobby_service.check_join_allowed_by_id",
                        lambda sid, name: (True, ""))
    for _ in range(25):
        assert _ask(endpoint, {"token": _TOKEN, "op": "join_check",
                               "server_id": 1, "player": "x"}) == {"ok": True}


def test_start_server_is_idempotent(endpoint):
    assert lobby_api_service.start_server(endpoint, _TOKEN) is True
    assert lobby_api_service.server_running() is True
    assert lobby_api_service.active_port() == endpoint


def test_stop_server_closes_endpoint(endpoint):
    lobby_api_service.stop_server()
    assert lobby_api_service.server_running() is False
    with pytest.raises(OSError):
        socket.create_connection(("127.0.0.1", endpoint), timeout=2).close()
