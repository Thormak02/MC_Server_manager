"""Tests fuer die Presence-Bridge (gespiegelte Avatare zwischen Instanzen)."""

from __future__ import annotations

import threading


# --- Bus-Logik -----------------------------------------------------------------
def test_bus_add_update_stale_remove():
    from app.services.presence_bridge_service import (
        EVENT_ADD, EVENT_CHAT, EVENT_REMOVE, EVENT_UPDATE, Presence, PresenceBus,
    )

    bus = PresenceBus()
    seen = []
    bus.subscribe(lambda e, p: seen.append((e, getattr(p, "uuid", p))))

    bus.upsert(Presence(uuid="u1", name="A", origin="vanilla", x=1, seq=1))
    bus.upsert(Presence(uuid="u1", name="A", origin="vanilla", x=2, seq=2))   # -> update
    bus.upsert(Presence(uuid="u1", name="A", origin="vanilla", x=9, seq=1))   # stale -> ignored
    bus.chat("A", "vanilla", "hi")
    assert [p for p in bus.snapshot(exclude_origin="hub")][0].x == 2          # neuester Wert
    assert bus.snapshot(exclude_origin="vanilla") == []                        # self-filter
    bus.remove("u1")

    assert [e for e, _ in seen] == [EVENT_ADD, EVENT_UPDATE, EVENT_CHAT, EVENT_REMOVE]


def test_bus_sweep_removes_stale(monkeypatch):
    from app.services import presence_bridge_service as pb

    bus = pb.PresenceBus()
    removed = []
    bus.subscribe(lambda e, p: removed.append(getattr(p, "uuid", None)) if e == pb.EVENT_REMOVE else None)
    bus.upsert(pb.Presence(uuid="old", name="X", origin="vanilla"))
    # Praesenz kuenstlich veralten lassen + Sweep-Drossel zuruecksetzen
    bus._presences["old"].updated -= (pb._TTL_SECONDS + 1)
    bus._last_sweep = 0.0
    bus.sweep()
    assert "old" in removed
    assert bus.snapshot() == []


def test_uuid16_from():
    from app.services.presence_bridge_service import uuid16_from

    assert len(uuid16_from("069a79f4-44e9-4726-a5be-fca90e38aaf5")) == 16   # echte UUID
    assert len(uuid16_from("synthetic-vanilla")) == 16                       # Fallback SHA1
    # stabil (deterministisch)
    assert uuid16_from("abc") == uuid16_from("abc")


# --- Hub-Consumer (Bridge-Events -> Avatar-Sessions) ---------------------------
class _FakeHub:
    """Leichtes Double: borgt die echten Hub-Bridge-Methoden, ohne ein Replay zu brauchen.
    Die Broadcast-Methoden werden aufgezeichnet statt an Sockets zu senden."""

    def __init__(self):
        self.lock = threading.Lock()
        self.players: dict = {}
        self.bridge: dict = {}
        self._eid_ctr = 1000
        self._bridge_attached = True
        self.sent: list = []

    def _broadcast(self, data, exclude=None):
        self.sent.append(("one", data))

    def _broadcast_many(self, pkts, exclude=None):
        self.sent.append(("many", len(pkts)))


def _bind_hub_methods():
    from app.services import hub_service

    # _spawn_packets/_despawn_packets sind @staticmethod -> als solche uebernehmen,
    # sonst wuerde self faelschlich als erstes Argument uebergeben.
    for name in ("_spawn_packets", "_despawn_packets"):
        setattr(_FakeHub, name, staticmethod(getattr(hub_service.Hub, name)))
    for name in ("_on_bridge_event", "_bridge_add", "_bridge_update",
                 "_bridge_remove", "_hub_bus_uuid"):
        setattr(_FakeHub, name, getattr(hub_service.Hub, name))


def test_hub_consumer_spawns_moves_removes():
    from app.services import presence_bridge_service as pb

    _bind_hub_methods()
    h = _FakeHub()

    # ADD (fremde Vanilla-Praesenz) -> Bridge-Avatar entsteht + Spawn wird gebroadcastet.
    # Bus-Koordinaten sind SPAWN-RELATIV -> der Avatar rendert bei _SPAWN + Offset.
    from app.services.hub_service import _SPAWN

    h._on_bridge_event(pb.EVENT_ADD, pb.Presence(
        uuid="u-van", name="Steve26", origin=pb.ORIGIN_VANILLA, x=1, y=0, z=2, yaw=10, seq=1))
    assert "u-van" in h.bridge
    sess = h.bridge["u-van"]
    assert sess.conn_id == "br:u-van"
    assert sess.eid == 1001                          # aus dem reservierten eid-Zaehler
    assert sess.conn_id in h.players                 # im Roster -> spaetere Joiner sehen ihn
    assert ("many", 3) in h.sent                     # _spawn_packets = 3 Pakete
    assert (sess.x, sess.y, sess.z) == (_SPAWN[0] + 1, _SPAWN[1] + 0, _SPAWN[2] + 2)

    # UPDATE -> Position wandert (weiterhin spawn-relativ auf lokal gemappt)
    h._on_bridge_event(pb.EVENT_UPDATE, pb.Presence(
        uuid="u-van", name="Steve26", origin=pb.ORIGIN_VANILLA, x=5, y=0, z=6, yaw=20, seq=2))
    assert (h.bridge["u-van"].x, h.bridge["u-van"].z) == (_SPAWN[0] + 5, _SPAWN[2] + 6)

    # Self-Filter: eigene (hub) Praesenz wird NICHT gespiegelt
    h._on_bridge_event(pb.EVENT_ADD, pb.Presence(
        uuid="hub-x", name="Selbst", origin=pb.ORIGIN_HUB, x=0, y=0, z=0))
    assert "hub-x" not in h.bridge

    # CHAT (fremd) -> System-Broadcast; eigener Chat gefiltert
    before = len(h.sent)
    h._on_bridge_event(pb.EVENT_CHAT, {"name": "Steve26", "origin": pb.ORIGIN_VANILLA, "text": "hallo"})
    assert len(h.sent) == before + 1
    h._on_bridge_event(pb.EVENT_CHAT, {"name": "Selbst", "origin": pb.ORIGIN_HUB, "text": "x"})
    assert len(h.sent) == before + 1                 # eigener Chat -> kein Broadcast

    # REMOVE -> Avatar verschwindet
    h._on_bridge_event(pb.EVENT_REMOVE, pb.Presence(
        uuid="u-van", name="Steve26", origin=pb.ORIGIN_VANILLA))
    assert "u-van" not in h.bridge and "br:u-van" not in h.players


# --- TCP/JSON-Endpoint (Paper-Plugin <-> Bus) ----------------------------------
def test_plugin_server_roundtrip():
    """Ein simuliertes Paper-Plugin: Hub-Praesenz -> Plugin; Plugin-Spieler -> BUS."""
    import json
    import socket
    import time

    from app.services import presence_bridge_service as pb

    with pb.BUS._lock:                 # Testisolation: BUS leeren
        pb.BUS._presences.clear()
    port = 25617
    assert pb.start_plugin_server(port, "tok") is True
    conn = None
    try:
        conn = socket.create_connection(("127.0.0.1", port), timeout=5)
        conn.settimeout(5)
        rf = conn.makefile("rb")
        conn.sendall(b'{"t":"hello","token":"tok"}\n')
        assert b"welcome" in rf.readline()

        time.sleep(0.25)               # Server abonniert den BUS
        # Hub-Praesenz -> das Plugin muss ein "up" bekommen
        pb.BUS.upsert(pb.Presence(uuid="hub-x", name="Modder", origin=pb.ORIGIN_HUB,
                                  x=1, y=64, z=2, seq=1))
        msg = json.loads(rf.readline())
        assert msg["t"] == "up" and msg["uuid"] == "hub-x" and msg["name"] == "Modder"

        # Plugin publiziert einen Vanilla-Spieler -> landet als origin=vanilla im BUS
        conn.sendall(b'{"t":"up","uuid":"van-y","name":"Steve26","x":3,"y":64,"z":4,"seq":1}\n')
        deadline = time.monotonic() + 3
        got = None
        while time.monotonic() < deadline:
            snap = {p.uuid: p for p in pb.BUS.snapshot()}
            if "van-y" in snap:
                got = snap["van-y"]
                break
            time.sleep(0.05)
        assert got is not None and got.origin == pb.ORIGIN_VANILLA and got.name == "Steve26"
    finally:
        if conn is not None:
            conn.close()
        pb.stop_plugin_server()
        pb.BUS.remove("hub-x")
        pb.BUS.remove("van-y")


def test_synthetic_modded_feeder_publishes_hub_avatar():
    """Der Modded-Feeder muss einen 'hub'-Avatar in den Bus legen (Proof Hub->Vanilla),
    damit die Vanilla-Lobby beim Solo-Test etwas Gespiegeltes zeigt."""
    import time

    from app.services import presence_bridge_service as pb

    with pb.BUS._lock:
        pb.BUS._presences.clear()
    try:
        pb.start_synthetic_modded_feeder()
        deadline = time.monotonic() + 3
        got = None
        while time.monotonic() < deadline:
            snap = {p.uuid: p for p in pb.BUS.snapshot()}
            if "synthetic-modded" in snap:
                got = snap["synthetic-modded"]
                break
            time.sleep(0.05)
        assert got is not None
        assert got.origin == pb.ORIGIN_HUB and got.name == "Modded-Test"
        status = pb.bridge_status()
        assert status["presence_count"] >= 1
        assert any(p["origin"] == pb.ORIGIN_HUB for p in status["presences"])
    finally:
        pb.stop_synthetic_modded_feeder()
        pb.BUS.remove("synthetic-modded")


def test_bridge_status_counts_connected_plugin():
    """bridge_status meldet einen verbundenen (authentifizierten) Plugin-Client."""
    import socket
    import time

    from app.services import presence_bridge_service as pb

    port = 25619
    assert pb.start_plugin_server(port, "tok") is True
    conn = None
    try:
        conn = socket.create_connection(("127.0.0.1", port), timeout=5)
        conn.settimeout(5)
        rf = conn.makefile("rb")
        conn.sendall(b'{"t":"hello","token":"tok"}\n')
        assert b"welcome" in rf.readline()
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            if pb.bridge_status()["plugins_connected"] >= 1:
                break
            time.sleep(0.05)
        st = pb.bridge_status()
        assert st["server_running"] is True
        assert st["plugins_connected"] >= 1
    finally:
        if conn is not None:
            conn.close()
        pb.stop_plugin_server()


def test_fetch_mojang_skin_uses_cache():
    """fetch_mojang_skin liefert gecachte Werte ohne Netzwerk + case-insensitiv."""
    import time

    from app.services import presence_bridge_service as pb

    pb._SKIN_CACHE["skintest"] = ("BASE64VALUE", "SIGNATURE", time.monotonic())
    v, s = pb.fetch_mojang_skin("SkinTest")
    assert v == "BASE64VALUE" and s == "SIGNATURE"
    assert pb.fetch_mojang_skin("") == ("", "")


def test_player_info_update_includes_textures():
    """build_player_info_update haengt die textures-Property an, wenn ein Skin uebergeben wird."""
    from app.services import mc_play as pl

    plain = pl.build_player_info_update(b"u" * 16, "Steve")
    signed = pl.build_player_info_update(b"u" * 16, "Steve", textures="VAL", signature="SIG")
    unsigned = pl.build_player_info_update(b"u" * 16, "Steve", textures="VAL")
    assert b"textures" not in plain            # 0 Properties -> Default-Skin
    assert b"textures" in signed and b"VAL" in signed and b"SIG" in signed
    assert b"textures" in unsigned and b"VAL" in unsigned
    assert len(signed) > len(unsigned) > len(plain)


def test_plugin_server_rejects_bad_token():
    import socket

    from app.services import presence_bridge_service as pb

    port = 25618
    assert pb.start_plugin_server(port, "secret") is True
    conn = None
    try:
        conn = socket.create_connection(("127.0.0.1", port), timeout=5)
        conn.settimeout(5)
        rf = conn.makefile("rb")
        conn.sendall(b'{"t":"hello","token":"wrong"}\n')
        assert b"error" in rf.readline()
    finally:
        if conn is not None:
            conn.close()
        pb.stop_plugin_server()
