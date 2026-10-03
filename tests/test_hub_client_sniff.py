"""Der Python-Hub liest den Client-Fingerabdruck aus der Config-Phase mit.

Brand und NeoForge-Mod-Manifest laufen ohnehin durch die Warteschleife der Config-Phase -
frueher wurde der Rueckgabewert von ``read_packet`` dort weggeworfen. Getestet wird gegen
den ECHTEN Mitschnitt ``atm10_capture.replay`` (Record 4 = Brand, Record 10 = 66 KB
Manifest), nicht gegen nachgebaute Bytes.

Der wichtigste Test ist ``test_read_packet_count_matches_old_loop``: die umgebaute Schleife
darf GENAU so viele read_packet-Aufrufe machen wie die alte. Liest sie ein Paket mehr oder
bricht sie frueher ab, geraet die Config-Phase bei Clients, deren Paketfolge vom Capture
abweicht, aus dem Tritt - und jeder Join haengt sekundenlang im _CONFIG_WAIT_TIMEOUT.
"""
import threading
import time
from pathlib import Path
from types import SimpleNamespace

import pytest

from app.services import hub_service, mc_dispatch as mcd, mc_play as pl, replay_service as rp

_CAPTURE = Path(__file__).resolve().parents[1] / "atm10_capture.replay"
# Die Datei ist 22 MB: EINMAL auf Modulebene lesen, alle Tests teilen sich das Ergebnis.
_RECORDS = rp.load_replay_file(str(_CAPTURE)) if _CAPTURE.exists() else []
_needs_capture = pytest.mark.skipif(not _RECORDS, reason="atm10_capture.replay nicht vorhanden")

if _RECORDS:
    _CFG_START = rp.find_config_start(_RECORDS)
    _CFG_END = rp.find_play_login(_RECORDS)
    _STEPS = rp.build_steps(_RECORDS[:_CFG_END], _CFG_START)
else:  # pragma: no cover - nur ohne Capture-Datei
    _CFG_START = _CFG_END = 0
    _STEPS = []


def _fields_of(record) -> bytes:
    """Paket-Rumpf eines Records (aeussere Laenge + Paket-ID ab) - genau das, was
    ``_Reader.read_packet`` dem Hub als zweites Element liefert."""
    _pid, fields, _consumed = mcd.try_read_packet(record.raw)
    return fields


def _client_packets() -> list:
    """Die C->S-Pakete der aufgezeichneten Config-Phase in Originalreihenfolge."""
    return [(r.packet_id, _fields_of(r)) for r in _RECORDS[_CFG_START:_CFG_END] if not r.to_client]


class _FakeSock:
    def __init__(self):
        self.sent: list[bytes] = []

    def sendall(self, data: bytes) -> None:
        self.sent.append(data)


class _CountingReader:
    """Liefert vorgegebene Pakete und zaehlt dabei die read_packet-Aufrufe.

    ``fail_after`` simuliert den abgebrochenen Client (read_packet wirft dann).
    """

    def __init__(self, packets=(), fail_after=None):
        self.queue = list(packets)
        self.calls = 0
        self.fail_after = fail_after

    def read_packet(self, timeout: float = 10.0):
        self.calls += 1
        if self.fail_after is not None and self.calls > self.fail_after:
            raise ConnectionError("closed")
        if self.queue:
            return self.queue.pop(0)
        return 0x00, b""          # Client schweigt: leeres, nichtssagendes Paket


def _old_config_loop(sock, reader, steps) -> None:
    """Die Warteschleife WORTGLEICH wie vor dem Umbau (Stand 4e196a3) - Referenzmass
    fuer die Anzahl und Reihenfolge der read_packet-Aufrufe."""
    for step in steps:
        if step.send:
            for raw in step.send:
                sock.sendall(raw)
        else:
            for _ in range(step.wait):
                try:
                    reader.read_packet(hub_service._CONFIG_WAIT_TIMEOUT)
                except (OSError, ConnectionError):
                    break


# --------------------------------------------------------------------------- #
# Sniffer gegen den echten ATM10-Mitschnitt
# --------------------------------------------------------------------------- #
@_needs_capture
def test_capture_config_phase_yields_brand_and_mods():
    sock, reader = _FakeSock(), _CountingReader(_client_packets())
    brand, mods = hub_service._play_config_phase(sock, reader, _STEPS)
    assert brand == "neoforge"
    assert len(mods) >= 150                       # Capture: 172 Mod-Namespaces
    assert "create" in mods and "jei" in mods     # Stichproben aus dem ATM10-Manifest
    # Das Abspielen selbst bleibt vollstaendig: jedes S->C-Paket der Config-Phase geht raus.
    to_client = sum(1 for r in _RECORDS[_CFG_START:_CFG_END] if r.to_client)
    assert len(sock.sent) == to_client


@_needs_capture
def test_sniff_single_records_from_capture():
    # Record 4 = minecraft:brand, Record 10 = 66 KB neoforge:register-Manifest
    brand, mods = hub_service._sniff_client(_fields_of(_RECORDS[4]))
    assert brand == "neoforge" and mods == frozenset()
    brand2, mods2 = hub_service._sniff_client(_fields_of(_RECORDS[10]))
    assert brand2 == "" and len(mods2) >= 150


def test_sniff_ignores_foreign_and_broken_payloads():
    # Fremder Kanal -> keine Aussage (es wird nach Kanal gesucht, nicht nach Position)
    foreign = mcd.encode_string("minecraft:register") + b"foo\x00bar"
    assert hub_service._sniff_client(foreign) == ("", frozenset())
    # Leeres/kaputtes Paket darf nichts liefern und nie werfen
    assert hub_service._sniff_client(b"") == ("", frozenset())
    assert hub_service._sniff_client(b"\xff\xff\xff\xff\xff") == ("", frozenset())


# --------------------------------------------------------------------------- #
# Die Zusicherung: gleich viele read_packet-Aufrufe wie vor dem Umbau
# --------------------------------------------------------------------------- #
@_needs_capture
def test_read_packet_count_matches_old_loop():
    packets = _client_packets()
    old_sock, old_reader = _FakeSock(), _CountingReader(list(packets))
    _old_config_loop(old_sock, old_reader, _STEPS)
    new_sock, new_reader = _FakeSock(), _CountingReader(list(packets))
    hub_service._play_config_phase(new_sock, new_reader, _STEPS)
    assert new_reader.calls == old_reader.calls
    assert new_reader.calls == sum(step.wait for step in _STEPS)   # kein Paket zu viel/zu wenig
    assert new_sock.sent == old_sock.sent                          # gleiche Sende-Reihenfolge


@_needs_capture
def test_read_packet_count_matches_when_client_drops():
    """Bricht der Client mitten in der Config-Phase ab, muss die neue Schleife an
    derselben Stelle aussteigen wie die alte - auch ueber mehrere Warte-Schritte."""
    for fail_after in (0, 1, 3):
        old_reader = _CountingReader(_client_packets(), fail_after=fail_after)
        _old_config_loop(_FakeSock(), old_reader, _STEPS)
        new_reader = _CountingReader(_client_packets(), fail_after=fail_after)
        hub_service._play_config_phase(_FakeSock(), new_reader, _STEPS)
        assert new_reader.calls == old_reader.calls, fail_after


def test_config_phase_without_capture_keeps_step_order():
    """Synthetische Steps: Senden/Warten bleiben in Reihenfolge, Wartezahl wird eingehalten."""
    steps = [rp.Step(send=[b"a", b"b"], wait=0), rp.Step(send=[], wait=2),
             rp.Step(send=[b"c"], wait=0), rp.Step(send=[], wait=1)]
    brand_packet = mcd.encode_string(mcd.MINECRAFT_BRAND) + mcd.encode_string("fabric")
    sock, reader = _FakeSock(), _CountingReader([(0x02, brand_packet)])
    brand, mods = hub_service._play_config_phase(sock, reader, steps)
    assert sock.sent == [b"a", b"b", b"c"]
    assert reader.calls == 3 and brand == "fabric" and mods == frozenset()


def test_session_has_client_fields():
    session = hub_service._Session(1, None, 2, b"x" * 16, "David", 0.0, 64.0, 0.0)
    assert session.brand == "" and session.mods == frozenset()
    assert session.pending_confirm == {}


# --------------------------------------------------------------------------- #
# Rueckfrage statt Absage: _try_transfer mit dem Urteil des Managers
# --------------------------------------------------------------------------- #
_SRV = {"id": 7, "key": "atm10", "display": "&aATM10 &7(neoforge 1.21.1)",
        "host": "atm10.mc.example.de", "port": 25590, "material": "ANVIL", "sleep": False}
_TRANSFER = pl.build_transfer(_SRV["host"], _SRV["port"])


class _StubHub:
    """Minimaler Hub-Ersatz: nur _try_transfer/_tell + aufgezeichnete Ausgaben."""

    _tell = hub_service.Hub._tell
    _try_transfer = hub_service.Hub._try_transfer
    _start_wait = hub_service.Hub._start_wait
    _wait_loop = hub_service.Hub._wait_loop

    def __init__(self):
        self.sent: list[bytes] = []

    def _send(self, session, data: bytes) -> None:
        self.sent.append(data)

    def chat(self) -> bytes:
        return b"".join(self.sent)


def _session(brand: str = "vanilla", mods=frozenset()):
    return SimpleNamespace(name="David", brand=brand, mods=mods, pending_confirm={},
                           alive=True, waiting_for=None)


def _verdict(**kw):
    base = {"ok": True, "reason": "", "confirm": False, "note": "", "code": "ok",
            "retry": False}
    base.update(kw)
    return SimpleNamespace(**base)


def _patch_join(monkeypatch, fn):
    from app.services import lobby_service

    monkeypatch.setattr(lobby_service, "evaluate_join_by_id", fn, raising=False)


def test_confirm_asks_once_and_second_click_overrides(monkeypatch):
    seen: list[bool] = []

    def fake(server_id, player, *, client=None, override=False):
        seen.append(override)
        assert client is not None and client.source == "hub" and client.brand == "vanilla"
        if override:
            return _verdict()
        return _verdict(ok=False, confirm=True, code="loader",
                        reason="ATM10 laeuft mit NeoForge 1.21.1 - dein Client meldet Vanilla.")

    _patch_join(monkeypatch, fake)
    hub, sess = _StubHub(), _session()

    assert hub._try_transfer(sess, _SRV) is False          # 1. Klick: nur Rueckfrage
    assert _TRANSFER not in hub.sent
    assert b"dein Client meldet Vanilla." in hub.chat()
    assert b"Klick nochmal, um es trotzdem zu versuchen." in hub.chat()
    assert 7 in sess.pending_confirm

    assert hub._try_transfer(sess, _SRV) is True           # 2. Klick: trotzdem
    assert _TRANSFER in hub.sent
    assert seen == [False, True]
    assert sess.pending_confirm == {}                      # Rueckfrage ist verbraucht


def test_confirm_expires_after_ttl(monkeypatch):
    seen: list[bool] = []

    def fake(server_id, player, *, client=None, override=False):
        seen.append(override)
        return _verdict(ok=False, confirm=True, reason="Passt vermutlich nicht.")

    _patch_join(monkeypatch, fake)
    hub, sess = _StubHub(), _session()
    # Klick von vor ueber einer Minute -> zaehlt nicht mehr als Bestaetigung.
    sess.pending_confirm[7] = time.monotonic() - hub_service._CONFIRM_TTL - 1.0
    assert hub._try_transfer(sess, _SRV) is False
    assert seen == [False] and _TRANSFER not in hub.sent


def test_hard_reason_is_not_overridable(monkeypatch):
    """Ban/Whitelist/offline bleiben hart - auch nach einer vorherigen Rueckfrage."""
    _patch_join(monkeypatch, lambda sid, player, *, client=None, override=False:
                _verdict(ok=False, reason="Du bist auf ATM10 gebannt.", code="ban"))
    hub, sess = _StubHub(), _session()
    sess.pending_confirm[7] = time.monotonic()             # offene Rueckfrage von eben
    assert hub._try_transfer(sess, _SRV) is False
    assert _TRANSFER not in hub.sent
    assert b"gebannt" in hub.chat()
    assert b"Klick nochmal" not in hub.chat()


def test_note_is_shown_but_never_stops(monkeypatch):
    _patch_join(monkeypatch, lambda sid, player, *, client=None, override=False:
                _verdict(note="ATM10 startet gerade - das kann kurz dauern."))
    hub, sess = _StubHub(), _session()
    assert hub._try_transfer(sess, _SRV) is True
    assert _TRANSFER in hub.sent
    assert b"das kann kurz dauern." in hub.chat()


def test_broken_check_fails_open(monkeypatch):
    def boom(server_id, player, *, client=None, override=False):
        raise RuntimeError("DB weg")

    _patch_join(monkeypatch, boom)
    hub, sess = _StubHub(), _session()
    assert hub._try_transfer(sess, _SRV) is True
    assert _TRANSFER in hub.sent


def test_unusable_verdict_fails_open(monkeypatch):
    _patch_join(monkeypatch, lambda sid, player, *, client=None, override=False: None)
    hub, sess = _StubHub(), _session()
    assert hub._try_transfer(sess, _SRV) is True
    assert _TRANSFER in hub.sent


# --------------------------------------------------------------------------- #
# Menue-Marker: unpassende Server grau + Kurzbegruendung, aber klickbar
# --------------------------------------------------------------------------- #
def test_menu_slots_mark_unfitting_server():
    fit = SimpleNamespace(level="confirm", short="Braucht NeoForge 1.21.1")
    plain = hub_service.Hub._menu_slots([_SRV])[0]
    marked = hub_service.Hub._menu_slots([_SRV], {7: fit})[0]
    assert marked != plain
    # Kurzbegruendung als zusaetzliche Lore-Zeile ...
    assert b"Braucht NeoForge 1.21.1" in marked
    assert b"Braucht NeoForge 1.21.1" not in plain
    # ... und der Name wird grau statt bunt (bleibt aber dasselbe Item = klickbar).
    assert pl._text_component_from_runs([("ATM10 (neoforge 1.21.1)", "gray")]) in marked
    assert pl._text_component_from_runs(hub_service._legacy_runs(_SRV["display"])) in plain
    assert pl._text_component_from_runs(hub_service._legacy_runs(_SRV["display"])) not in marked
    assert b"Klick zum Verbinden" in marked


def test_menu_slots_unmarked_when_fit_is_for_another_server():
    fit = SimpleNamespace(level="confirm", short="Braucht NeoForge 1.21.1")
    other = hub_service.Hub._menu_slots([_SRV], {99: fit})[0]
    assert other == hub_service.Hub._menu_slots([_SRV])[0]


def test_menu_fits_collects_only_confirm_levels(monkeypatch):
    from app.services import lobby_service

    servers = [dict(_SRV), {"id": 8, "display": "&aLobby", "host": "h", "port": 1}]
    calls: list[int] = []

    def fake(server_ids, client):
        calls.extend(int(i) for i in server_ids)
        assert client.brand == "vanilla" and client.source == "hub"
        # Die echte Funktion liefert NUR Treffer zurueck - passende Ziele fehlen schlicht.
        return {7: SimpleNamespace(level="confirm", short="Braucht NeoForge 1.21.1")}

    monkeypatch.setattr(lobby_service, "evaluate_fits_by_ids", fake, raising=False)
    fits = hub_service._menu_fits(servers, _session())
    assert calls == [7, 8]
    assert list(fits) == [7] and fits[7].short == "Braucht NeoForge 1.21.1"


def test_menu_fits_skips_lookup_without_client_hints(monkeypatch):
    """Ohne Brand und ohne Mods kann die Pruefung nur "passt" sagen - keine DB-Abfrage."""
    from app.services import lobby_service

    def fail(server_ids, client):
        raise AssertionError("darf nicht abgefragt werden")

    monkeypatch.setattr(lobby_service, "evaluate_fits_by_ids", fail, raising=False)
    assert hub_service._menu_fits([dict(_SRV)], _session(brand="")) == {}


def test_menu_fits_survives_broken_check(monkeypatch):
    from app.services import lobby_service

    def boom(server_ids, client):
        raise RuntimeError("DB weg")

    monkeypatch.setattr(lobby_service, "evaluate_fits_by_ids", boom, raising=False)
    assert hub_service._menu_fits([dict(_SRV)], _session()) == {}


# --------------------------------------------------------------------------- #
# NOCH NICHT statt NEIN: der Spieler wartet im PLAY-Zustand auf den Serverstart
#
# Ein schlafendes Ziel braucht gemessen bis 56 s - mehr als die 30 s, nach denen der
# Client eine stille Verbindung kappt. In der Lobby laeuft dagegen Keep-Alive, also
# wird hier gewartet und erst transferiert, wenn das Ziel Logins annimmt.
# --------------------------------------------------------------------------- #
_RETRY = {"ok": False, "retry": True,
          "reason": "ATM10 wird gestartet - bleib in der Lobby, du wirst automatisch verbunden."}
_LABEL = "ATM10 (neoforge 1.21.1)"      # == _plain(_SRV["display"])


class _NoWaitHub(_StubHub):
    """Wie _StubHub, aber die Warteschleife wird nur protokolliert statt gestartet -
    so laesst sich _try_transfer ohne Thread pruefen."""

    def __init__(self):
        super().__init__()
        self.waits: list[tuple] = []

    def _start_wait(self, session, sid, label, host, port, override) -> None:
        self.waits.append((sid, label, host, port, override))


def _patch_verdicts(monkeypatch, verdicts, interval=0.0, rounds=None):
    """_join_verdict durch eine Liste von Urteilen ersetzen (das letzte gilt weiter).

    Zurueck kommt die Liste der uebergebenen override-Flags - daran haengt die Zusicherung,
    dass die Schleife mit DEMSELBEN override weiterfragt wie der erste Klick.
    """
    seen: list[bool] = []

    def fake(server_id, session, *, override: bool):
        seen.append(override)
        return verdicts[min(len(seen) - 1, len(verdicts) - 1)]

    monkeypatch.setattr(hub_service, "_join_verdict", fake)
    monkeypatch.setattr(hub_service, "_WAIT_POLL_INTERVAL", interval)
    if rounds is not None:
        monkeypatch.setattr(hub_service, "_WAIT_MAX_ROUNDS", rounds)
    return seen


def _wait_until(cond, timeout: float = 5.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if cond():
            return
        time.sleep(0.01)
    raise AssertionError("Bedingung ist nicht eingetreten")


def _join_wait_threads(timeout: float = 5.0) -> None:
    for t in list(threading.enumerate()):
        if t.name.startswith("hub-wait-"):
            t.join(timeout)
            assert not t.is_alive(), t.name


def test_retry_verdict_keeps_player_in_lobby(monkeypatch):
    """retry -> kein Transfer, zwei Hinweise im Chat, Warteschleife mit dem Klick-override."""
    _patch_join(monkeypatch, lambda sid, player, *, client=None, override=False:
                _verdict(**_RETRY))
    hub, sess = _NoWaitHub(), _session()

    assert hub._try_transfer(sess, _SRV) is False
    assert _TRANSFER not in hub.sent
    assert len(hub.sent) == 2
    assert b"bleib in der Lobby, du wirst automatisch verbunden." in hub.chat()
    assert b"Du bleibst in der Lobby und wirst automatisch verbunden." in hub.chat()
    assert hub.waits == [(7, _LABEL, _SRV["host"], _SRV["port"], False)]
    # Eine Rueckfrage ist das NICHT - es wird kein zweiter Klick verlangt.
    assert b"Klick nochmal" not in hub.chat()
    assert sess.pending_confirm == {}


def test_ok_verdict_transfers_without_waiting(monkeypatch):
    """Gegentest: ein gruenes Urteil geht weiterhin direkt durch - keine Warteschleife."""
    _patch_join(monkeypatch, lambda sid, player, *, client=None, override=False: _verdict())
    hub, sess = _NoWaitHub(), _session()

    assert hub._try_transfer(sess, _SRV) is True
    assert _TRANSFER in hub.sent
    assert hub.waits == []


def test_wait_loop_sends_nothing_when_player_left(monkeypatch):
    """Der Spieler hat die Lobby verlassen -> nichts senden und nicht mehr nachfragen."""
    seen = _patch_verdicts(monkeypatch, [_verdict(**_RETRY)])
    hub, sess = _StubHub(), _session()
    sess.alive = False

    hub._wait_loop(sess, 7, _LABEL, _SRV["host"], _SRV["port"], False)
    assert hub.sent == [] and seen == []


def test_wait_loop_transfers_when_target_becomes_ready(monkeypatch):
    """Viermal "noch nicht", dann gruenes Licht -> Transfer genau wie in _try_transfer."""
    seen = _patch_verdicts(monkeypatch, [_verdict(**_RETRY)] * 4 + [_verdict()])
    hub, sess = _StubHub(), _session()

    hub._wait_loop(sess, 7, _LABEL, _SRV["host"], _SRV["port"], True)
    assert seen == [True] * 5                     # immer dasselbe override wie beim Klick
    assert hub.chat().count(f"{_LABEL} startet noch ...".encode()) == 1   # nur Lauf 3
    assert f"{_LABEL} ist bereit - verbinde ...".encode() in hub.chat()
    assert hub.sent[-1] == _TRANSFER
    assert sess.waiting_for is None               # Guard wieder frei


def test_wait_loop_stops_on_hard_no(monkeypatch):
    """Wird aus dem Warten ein hartes Nein (Start gescheitert), bricht die Schleife ab."""
    seen = _patch_verdicts(monkeypatch,
                           [_verdict(**_RETRY),
                            _verdict(ok=False, reason="Serverstart nicht moeglich: kein Java.")])
    hub, sess = _StubHub(), _session()

    hub._wait_loop(sess, 7, _LABEL, _SRV["host"], _SRV["port"], False)
    assert len(seen) == 2
    assert b"Serverstart nicht moeglich: kein Java." in hub.chat()
    assert _TRANSFER not in hub.sent
    assert sess.waiting_for is None


def test_wait_loop_gives_up_after_the_limit(monkeypatch):
    """Nach _WAIT_MAX_ROUNDS Laeufen ist Schluss - mit Hinweis, ohne Transfer."""
    seen = _patch_verdicts(monkeypatch, [_verdict(**_RETRY)], rounds=7)
    hub, sess = _StubHub(), _session()

    hub._wait_loop(sess, 7, _LABEL, _SRV["host"], _SRV["port"], False)
    assert len(seen) == 7
    assert hub.chat().count(f"{_LABEL} startet noch ...".encode()) == 2   # Laeufe 3 und 6
    assert (f"{_LABEL} braucht laenger als erwartet. "
            "Bitte spaeter noch einmal versuchen.").encode() in hub.chat()
    assert _TRANSFER not in hub.sent
    assert sess.waiting_for is None


def test_wait_messages_carry_no_seconds_or_percent(monkeypatch):
    """Keine Zahlen in den Wartemeldungen: get_start_progress springt 5 -> 97 -> 100,
    eine daran gehaengte Anzeige stuende die ganze Modladezeit hindurch auf 97 %."""
    _patch_verdicts(monkeypatch, [_verdict(**_RETRY)], rounds=3)
    hub, sess = _StubHub(), _session()

    hub._wait_loop(sess, 7, "David", _SRV["host"], _SRV["port"], False)
    assert hub.sent and b"%" not in hub.chat()
    assert b"Sekunden" not in hub.chat() and b"Minute" not in hub.chat()


def test_second_click_while_waiting_starts_no_second_thread(monkeypatch):
    """Zweiter Klick waehrend des Wartens -> nur ein Hinweis, KEINE zweite Schleife."""
    gate = threading.Event()
    seen: list[bool] = []

    def fake(server_id, session, *, override: bool):
        seen.append(override)
        gate.wait(5.0)            # der erste Poll haengt, bis der Test ihn freigibt
        return _verdict(**_RETRY)

    monkeypatch.setattr(hub_service, "_join_verdict", fake)
    monkeypatch.setattr(hub_service, "_WAIT_POLL_INTERVAL", 0.0)
    monkeypatch.setattr(hub_service, "_WAIT_MAX_ROUNDS", 1)
    hub, sess = _StubHub(), _session()

    hub._start_wait(sess, 7, _LABEL, _SRV["host"], _SRV["port"], False)
    _wait_until(lambda: bool(seen))               # Schleife steht im ersten Poll
    assert sess.waiting_for == 7
    hub._start_wait(sess, 7, _LABEL, _SRV["host"], _SRV["port"], False)
    assert b"Du wartest bereits auf einen Serverstart." in hub.chat()

    gate.set()
    _join_wait_threads()
    assert len(seen) == 1                         # genau EIN Poll-Thread war unterwegs
    assert sess.waiting_for is None
