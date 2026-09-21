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


# --------------------------------------------------------------------------- #
# Client-Abgleich ueber die Leitung: optionaler client-Block, Rueckfragen, fit_check.
# Das Vorhandensein des Blocks IST die Versionsverhandlung - ein altes Jar schickt
# keinen, bekommt genau die alte Antwort und merkt von alldem nichts.
# --------------------------------------------------------------------------- #
from app.services import join_match_service, lobby_service  # noqa: E402

_CLIENT = {"brand": "vanilla", "source": "bukkit"}

_CONFIRM = lobby_service.JoinVerdict(
    ok=False,
    reason="David laeuft mit NeoForge 1.21.1 - dein Client meldet sich als Vanilla.",
    confirm=True,
    code="loader",
)


def _verdict(monkeypatch, verdict):
    """evaluate_join_by_id festnageln und die gesehenen Argumente zurueckgeben."""
    seen = {}

    def fake(server_id, player, *, client=None, override=False):
        seen.update(server_id=server_id, player=player, client=client, override=override)
        return verdict

    monkeypatch.setattr("app.services.lobby_service.evaluate_join_by_id", fake)
    return seen


def _never_called(*_a, **_kw):
    raise AssertionError("Dieser Pfad haette nicht laufen duerfen")


def test_client_block_reaches_the_evaluation(endpoint, monkeypatch):
    seen = _verdict(monkeypatch, lobby_service.JoinVerdict(True))
    answer = _ask(endpoint, {"token": _TOKEN, "op": "join_check", "server_id": 7,
                             "player": "David", "client": _CLIENT})
    assert answer == {"ok": True}
    assert seen["server_id"] == 7 and seen["player"] == "David"
    assert seen["client"] == join_match_service.ClientInfo(
        brand="vanilla", mods=frozenset(), source="bukkit"
    )
    assert seen["override"] is False


def test_confirm_answer_carries_the_flag(endpoint, monkeypatch):
    _verdict(monkeypatch, _CONFIRM)
    answer = _ask(endpoint, {"token": _TOKEN, "op": "join_check", "server_id": 7,
                             "player": "David", "client": _CLIENT})
    assert answer == {"ok": False, "reason": _CONFIRM.reason, "confirm": True}


def test_confirm_leaves_a_trace_in_the_log(endpoint, monkeypatch, capsys):
    """Eine zu Unrecht verhinderte Verbindung muss nachvollziehbar bleiben."""
    _verdict(monkeypatch, _CONFIRM)
    _ask(endpoint, {"token": _TOKEN, "op": "join_check", "server_id": 7,
                    "player": "David", "client": _CLIENT})
    ausgabe = capsys.readouterr().out
    assert "[lobby-api]" in ausgabe and "David" in ausgabe and "loader" in ausgabe


def test_hard_rejection_has_no_confirm_flag(endpoint, monkeypatch):
    """Hart ist hart: ohne confirm darf das Plugin keine Rueckfrage anbieten."""
    _verdict(monkeypatch, lobby_service.JoinVerdict(False, "Du bist auf David gebannt."))
    answer = _ask(endpoint, {"token": _TOKEN, "op": "join_check", "server_id": 7,
                             "player": "David", "client": _CLIENT})
    assert answer == {"ok": False, "reason": "Du bist auf David gebannt."}
    assert "confirm" not in answer


def test_note_rides_along_without_blocking(endpoint, monkeypatch):
    _verdict(monkeypatch, lobby_service.JoinVerdict(True, note="Andere Pack-Version?"))
    answer = _ask(endpoint, {"token": _TOKEN, "op": "join_check", "server_id": 7,
                             "player": "David", "client": _CLIENT})
    assert answer == {"ok": True, "note": "Andere Pack-Version?"}


def test_override_is_passed_through(endpoint, monkeypatch):
    seen = _verdict(monkeypatch, lobby_service.JoinVerdict(True))
    _ask(endpoint, {"token": _TOKEN, "op": "join_check", "server_id": 7,
                    "player": "David", "client": _CLIENT, "override": True})
    assert seen["override"] is True


def test_request_without_client_block_behaves_like_before(endpoint, monkeypatch):
    """Regressionsschutz fuer alte Jars: kein client-Block -> alter Pfad, alte Antwort."""
    monkeypatch.setattr("app.services.lobby_service.evaluate_join_by_id", _never_called)
    monkeypatch.setattr("app.services.lobby_service.check_join_allowed_by_id",
                        lambda sid, name: (False, "David ist voll (20/20)."))
    answer = _ask(endpoint, {"token": _TOKEN, "op": "join_check",
                             "server_id": 7, "player": "David"})
    assert answer == {"ok": False, "reason": "David ist voll (20/20)."}
    assert "confirm" not in answer and "note" not in answer


@pytest.mark.parametrize("kaputt", ["bloedsinn", ["neoforge", "fabric"], 7, 1.5, True, None])
def test_broken_client_block_arrives_as_none(endpoint, monkeypatch, kaputt):
    """Unsinn im client-Block darf nie zu einem Client-Urteil werden."""
    seen = _verdict(monkeypatch, lobby_service.JoinVerdict(True))
    answer = _ask(endpoint, {"token": _TOKEN, "op": "join_check", "server_id": 7,
                             "player": "David", "client": kaputt, "override": True})
    assert answer == {"ok": True}
    assert seen["client"] is None


@pytest.mark.parametrize("kaputt", ["bloedsinn", ["neoforge"], 7, {}, {"brand": 5}])
def test_broken_client_block_keeps_the_old_path(endpoint, monkeypatch, kaputt):
    """Ohne verwertbaren Client bleibt es beim alten Urteil - kein confirm, kein note."""
    monkeypatch.setattr("app.services.lobby_service.evaluate_join_by_id", _never_called)
    monkeypatch.setattr("app.services.lobby_service.check_join_allowed_by_id",
                        lambda sid, name: (True, ""))
    answer = _ask(endpoint, {"token": _TOKEN, "op": "join_check", "server_id": 7,
                             "player": "David", "client": kaputt})
    assert answer == {"ok": True}


def _fits(monkeypatch, mapping):
    """evaluate_fits_by_ids aus einer {id: Fit}-Tabelle bedienen.

    Bildet den Vertrag der echten Funktion nach: kaputte IDs fallen raus, und nur
    Treffer (level != "ok") landen im Ergebnis - ein fehlender Eintrag heisst "passt".
    """
    def batched(server_ids, client):
        out = {}
        for raw in server_ids or ():
            try:
                sid = int(raw)
            except (TypeError, ValueError):
                continue
            fit = mapping.get(sid)
            if fit is not None and fit.level != join_match_service.LEVEL_OK:
                out[sid] = fit
        return out

    monkeypatch.setattr("app.services.lobby_service.evaluate_fits_by_ids", batched)


def test_fit_check_reports_only_the_misfits(endpoint, monkeypatch):
    _fits(monkeypatch, {5: join_match_service.Fit(
        level=join_match_service.LEVEL_CONFIRM,
        text="lang", short="Braucht NeoForge 1.21.1", code="loader")})
    answer = _ask(endpoint, {"token": _TOKEN, "op": "fit_check", "server_ids": [2, 5, 7],
                             "player": "David", "client": _CLIENT})
    assert answer == {"ok": True, "fits": {
        "5": {"level": "confirm", "short": "Braucht NeoForge 1.21.1"}}}


def test_fit_check_returns_an_object_not_an_array(endpoint, monkeypatch):
    """fits muss ein OBJEKT sein - Json.java im Plugin kann keine Arrays lesen."""
    _fits(monkeypatch, {})
    answer = _ask(endpoint, {"token": _TOKEN, "op": "fit_check",
                             "server_ids": [1, 2], "client": _CLIENT})
    assert answer == {"ok": True, "fits": {}}
    assert isinstance(answer["fits"], dict)


def test_fit_check_survives_unknown_and_broken_ids(endpoint, monkeypatch):
    """Unbekannte IDs liefern schlicht keinen Marker - fehlender Eintrag heisst 'passt'."""
    _fits(monkeypatch, {5: join_match_service.Fit(
        level=join_match_service.LEVEL_CONFIRM, short="Braucht Fabric")})
    answer = _ask(endpoint, {"token": _TOKEN, "op": "fit_check", "client": _CLIENT,
                             "server_ids": [4711, "bloedsinn", None, 5, {"a": 1}, "5"]})
    assert answer == {"ok": True, "fits": {"5": {"level": "confirm", "short": "Braucht Fabric"}}}


def test_fit_check_without_client_stays_quiet(endpoint, monkeypatch):
    monkeypatch.setattr("app.services.lobby_service.evaluate_fits_by_ids", _never_called)
    answer = _ask(endpoint, {"token": _TOKEN, "op": "fit_check", "server_ids": [1, 2]})
    assert answer == {"ok": True, "fits": {}}


def test_fit_check_without_ids_is_no_verdict(endpoint):
    answer = _ask(endpoint, {"token": _TOKEN, "op": "fit_check", "client": _CLIENT})
    assert answer == {"error": "bad_server_ids"} and "ok" not in answer


def test_no_answer_ever_contains_a_json_array(monkeypatch):
    """Regressionsschutz gegen den Json.java-Fallstrick.

    Der Mini-Parser des Plugins kennt kein '['. Er faellt bis zur Zahl durch, wirft und
    liefert null - und null liest JoinCheck als ALLOW. Ein Array in der Antwort wuerde
    auf jeder noch nicht neu gestarteten Lobby also auch Ban und Whitelist still
    abschalten. Deshalb: keine eckige Klammer, nirgends.
    """
    _fits(monkeypatch, {
        5: join_match_service.Fit(level=join_match_service.LEVEL_CONFIRM,
                                  short="Braucht NeoForge 1.21.1", code="loader"),
        9: join_match_service.Fit(level=join_match_service.LEVEL_CONFIRM,
                                  short="4 Mods fehlen vermutlich", code="mods"),
    })
    verdicts = [
        lobby_service.JoinVerdict(True),
        lobby_service.JoinVerdict(True, note="Hinweis eins | Hinweis zwei"),
        _CONFIRM,
        lobby_service.JoinVerdict(False, "David ist voll (20/20)."),
    ]
    anfragen = [
        {"op": "ping"},
        {"op": "was_auch_immer"},
        {"op": "join_check", "server_id": "abc"},
        {"op": "fit_check"},
        {"op": "fit_check", "server_ids": [2, 5, 9], "client": _CLIENT},
        {"op": "fit_check", "server_ids": [], "client": _CLIENT},
    ]
    antworten = [lobby_api_service._handle_request(dict(a)) for a in anfragen]
    for verdict in verdicts:
        _verdict(monkeypatch, verdict)
        antworten.append(lobby_api_service._handle_request(
            {"op": "join_check", "server_id": 7, "player": "David", "client": _CLIENT}))
    monkeypatch.setattr("app.services.lobby_service.check_join_allowed_by_id",
                        lambda sid, name: (False, "Du bist auf David gebannt."))
    antworten.append(lobby_api_service._handle_request(
        {"op": "join_check", "server_id": 7, "player": "David"}))

    # Strukturell pruefen, nicht per Zeichen: eine eckige Klammer IM TEXT (Ban-Grund
    # "Griefing [Basis]") ist fuer Json.java harmlos, weil sie in einem String steht.
    def _ohne_liste(wert, pfad="antwort"):
        assert not isinstance(wert, (list, tuple)), f"Array bei {pfad}: {wert!r}"
        if isinstance(wert, dict):
            for schluessel, inhalt in wert.items():
                _ohne_liste(inhalt, f"{pfad}.{schluessel}")

    for antwort in antworten:
        _ohne_liste(antwort)


def test_bracket_in_reason_survives(monkeypatch):
    """Eckige Klammern im Grund sind erlaubt - sie stehen in einem String.

    Wichtig, weil Ban-Gruende frei getippt werden ("Griefing [Basis von X]"). Ein
    Regressionsschutz, der das Zeichen verbietet, wuerde hier falsch anschlagen.
    """
    _verdict(monkeypatch, lobby_service.JoinVerdict(False, "Gebannt: Griefing [Basis von Tim]"))
    antwort = lobby_api_service._handle_request(
        {"op": "join_check", "server_id": 7, "player": "David", "client": _CLIENT})
    assert antwort["reason"] == "Gebannt: Griefing [Basis von Tim]"
    assert not isinstance(antwort.get("reason"), (list, tuple))
