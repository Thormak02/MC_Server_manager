"""Dispatcher-Statemachine end-to-end mit einem In-Memory-Fake-Socket."""

import zipfile

from app.services import mc_dispatch as mcd
from app.services.mc_protocol import Handshake, _wrap_packet, encode_string, encode_varint


class FakeSock:
    """Minimaler Socket-Ersatz: liefert vorskriptete Client-Bytes, merkt Gesendetes."""

    def __init__(self, script: bytes):
        self.inbuf = bytearray(script)
        self.sent = bytearray()
        self.closed = False

    def settimeout(self, _t):
        pass

    def recv(self, n):
        if not self.inbuf:
            return b""
        chunk = bytes(self.inbuf[:n])
        del self.inbuf[:n]
        return chunk

    def sendall(self, data):
        self.sent.extend(data)

    def close(self):
        self.closed = True


# --- Client-Pakete bauen ---
def _login_start(name="Steve"):
    return _wrap_packet(encode_varint(0x00) + encode_string(name) + b"\x01" * 16)


def _login_ack():
    return _wrap_packet(encode_varint(0x03))


def _client_info():
    return _wrap_packet(encode_varint(0x00) + b"\x00")


def _brand(brand):
    return _wrap_packet(encode_varint(0x02) + encode_string("minecraft:brand") + encode_string(brand))


def _register(channels):
    data = b"\x00".join(c.encode() for c in channels)
    return _wrap_packet(encode_varint(0x02) + encode_string("minecraft:register") + data)


def _manifest(channel_ids):
    blob = b"".join(encode_string(cid) + b"\x00\x00" for cid in channel_ids)
    return _wrap_packet(encode_varint(0x02) + encode_string("neoforge:register") + blob)


def _sent_packets(sent: bytes):
    out = []
    buf = bytes(sent)
    while True:
        got = mcd.try_read_packet(buf)
        if got is None:
            break
        pid, fields, consumed = got
        out.append((pid, fields))
        buf = buf[consumed:]
    return out


def _find_transfer(sent: bytes):
    from app.services.mc_protocol import _read_string, read_varint

    for pid, fields in _sent_packets(sent):
        if pid == mcd.CFG_CB_TRANSFER:
            host, off = _read_string(fields, 0)
            port, _ = read_varint(fields, off)
            return host, port
    return None


def _make_neoforge_server(mods_dir, mod_ids):
    mods_dir.mkdir(parents=True, exist_ok=True)
    toml = "\n".join(f'[[mods]]\nmodId="{m}"\n' for m in mod_ids)
    with zipfile.ZipFile(mods_dir / "pack.jar", "w") as zf:
        zf.writestr("META-INF/neoforge.mods.toml", toml)


def _hs(proto=767):
    return Handshake(protocol_version=proto, server_address="mc.example.de",
                     server_port=25565, next_state=2, consumed=0)


def test_dispatch_vanilla_transfers_to_lobby(client, tmp_path):
    import app.services.app_setting_service as svc
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import dispatcher_service

    with SessionLocal() as db:
        db.add(Server(name="Lobby", slug="lob", server_type="paper", mc_version="1.21.1",
                      base_path=str(tmp_path / "lob"), gateway_enabled=True,
                      gateway_hostname="lobby", gateway_is_default=True, port=25569))
        db.commit()
        svc.set_network_domain(db, "mc.example.de")
        svc.set_network_port(db, 25565)

    script = _login_start() + _login_ack() + _client_info() + _brand("vanilla") + _register(["minecraft:register"])
    sock = FakeSock(script)
    dispatcher_service.dispatch(sock, _hs(), b"")

    assert _find_transfer(sock.sent) == ("lobby.mc.example.de", 25565)
    assert sock.closed


def test_dispatch_modded_routes_to_matching_pack(client, tmp_path):
    import app.services.app_setting_service as svc
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import dispatcher_service

    _make_neoforge_server(tmp_path / "atm" / "mods", ["create", "mekanism", "ae2"])
    with SessionLocal() as db:
        db.add_all([
            Server(name="Lobby", slug="lob", server_type="paper", mc_version="1.21.1",
                   base_path=str(tmp_path / "lob"), gateway_enabled=True,
                   gateway_hostname="lobby", gateway_is_default=True, port=25569),
            Server(name="ATM10", slug="atm", server_type="neoforge", mc_version="1.21.1",
                   base_path=str(tmp_path / "atm"), gateway_enabled=True,
                   gateway_hostname="atm10", port=25601),
        ])
        db.commit()
        svc.set_network_domain(db, "mc.example.de")
        svc.set_network_port(db, 25565)

    script = (
        _login_start() + _login_ack() + _client_info()
        + _brand("neoforge")
        + _register(["neoforge:register", "minecraft:register"])
        + _manifest(["create:network", "mekanism:tile", "ae2:main", "sodium:client"])
    )
    sock = FakeSock(script)
    dispatcher_service.dispatch(sock, _hs(), b"")

    # -> Transfer zum ATM10-Pack (nicht zur Lobby).
    assert _find_transfer(sock.sent) == ("atm10.mc.example.de", 25565)
    # Der Dispatcher hat die neoforge:register-Query gesendet.
    assert any(pid == mcd.CFG_CB_CUSTOM for pid, _ in _sent_packets(sock.sent))


def test_dispatch_modded_without_register(client, tmp_path):
    """NeoForge-Client sendet nur brand (kein minecraft:register) -> trotzdem als
    modded erkannt und korrekt geroutet (break-on-brand, kein 4s-Haenger)."""
    import app.services.app_setting_service as svc
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import dispatcher_service

    _make_neoforge_server(tmp_path / "atm" / "mods", ["create", "mekanism"])
    with SessionLocal() as db:
        db.add_all([
            Server(name="Lobby", slug="lob", server_type="paper", mc_version="1.21.1",
                   base_path=str(tmp_path / "lob"), gateway_enabled=True,
                   gateway_hostname="lobby", gateway_is_default=True, port=25569),
            Server(name="ATM10", slug="atm", server_type="neoforge", mc_version="1.21.1",
                   base_path=str(tmp_path / "atm"), gateway_enabled=True,
                   gateway_hostname="atm10", port=25601),
        ])
        db.commit()
        svc.set_network_domain(db, "mc.example.de")
        svc.set_network_port(db, 25565)

    # Kein _register()! Nur client_info + brand, dann (auf die Query hin) das Manifest.
    script = (
        _login_start() + _login_ack() + _client_info()
        + _brand("neoforge")
        + _manifest(["create:network", "mekanism:tile"])
    )
    sock = FakeSock(script)
    dispatcher_service.dispatch(sock, _hs(), b"")
    assert _find_transfer(sock.sent) == ("atm10.mc.example.de", 25565)


# --------------------------- Phase 2: Path-A (Pack-Tag) --------------------- #
def _neoforge_lobby_and_pack(db_add, tmp_path, svc):
    from app.models.server import Server

    db_add([
        Server(name="Lobby", slug="lob", server_type="paper", mc_version="1.21.1",
               base_path=str(tmp_path / "lob"), gateway_enabled=True,
               gateway_hostname="lobby", gateway_is_default=True, port=25569),
        Server(name="ATM10", slug="atm", server_type="neoforge", mc_version="1.21.1",
               base_path=str(tmp_path / "atm"), gateway_enabled=True,
               gateway_hostname="atm10", port=25601),
    ])


def test_dispatch_modded_hub_tags_pack_with_replay(client, tmp_path, monkeypatch):
    from pathlib import Path

    from sqlalchemy import select

    import app.services.app_setting_service as svc
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import dispatcher_service, hub_replay_service

    monkeypatch.setattr(hub_replay_service, "REPLAY_DIR", str(tmp_path / "replays"))
    _make_neoforge_server(tmp_path / "atm" / "mods", ["create", "mekanism", "ae2"])
    with SessionLocal() as db:
        _neoforge_lobby_and_pack(db.add_all, tmp_path, svc)
        db.commit()
        svc.set_network_domain(db, "mc.example.de")
        svc.set_network_port(db, 25565)
        svc.set_hub_lobby_enabled(db, True)
        sid = db.scalar(select(Server).where(Server.slug == "atm")).id

    # LADBARES Replay fuer ATM anlegen (mit S->C PLAY-Login 0x2B) -> Pack-Tag modlobby-<id>.
    # (Eine blosse MCRP-Magic zaehlt nicht mehr - der Hub koennte daraus kein Profil bauen.)
    replay = Path(hub_replay_service.replay_path_for("atm"))
    replay.parent.mkdir(parents=True, exist_ok=True)
    from app.services import hub_capture_service
    hub_capture_service._write_replay(str(replay), [(0, b"\x05\x2b\x00\x00\x00\x01")])

    script = (_login_start() + _login_ack() + _client_info()
              + _brand("neoforge")
              + _register(["neoforge:register", "minecraft:register"])
              + _manifest(["create:network", "mekanism:tile", "ae2:main"]))
    sock = FakeSock(script)
    dispatcher_service.dispatch(sock, _hs(), b"")
    assert _find_transfer(sock.sent) == (f"modlobby-{sid}.mc.example.de", 25565)


def test_dispatch_modded_no_match_disconnects(client, tmp_path):
    """NeoForge-Client ohne passenden Server UND ohne Voll-Abdeckung des Default-Replays ->
    klarer Hinweis-Disconnect (KEIN fremdes Replay servieren -> kein Registry-Kick)."""
    import app.services.app_setting_service as svc
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import dispatcher_service

    with SessionLocal() as db:
        db.add(Server(name="Lobby", slug="lob", server_type="paper", mc_version="1.21.1",
                      base_path=str(tmp_path / "lob"), gateway_enabled=True,
                      gateway_hostname="lobby", gateway_is_default=True, port=25569))
        db.commit()
        svc.set_network_domain(db, "mc.example.de")
        svc.set_network_port(db, 25565)
        svc.set_hub_lobby_enabled(db, True)

    # Mods passen zu keinem Server, Default-Replay fehlt/leer -> Disconnect mit Hinweis.
    script = (_login_start() + _login_ack() + _client_info()
              + _brand("neoforge")
              + _register(["neoforge:register", "minecraft:register"])
              + _manifest(["unknownmod:x", "anotherunknown:y"]))
    sock = FakeSock(script)
    dispatcher_service.dispatch(sock, _hs(), b"")
    assert _find_transfer(sock.sent) is None
    assert b"nicht eingerichtet" in bytes(sock.sent)


def test_dispatch_fabric_routes_to_vanilla_profile(client, tmp_path):
    """Fabric ist lenient -> Vanilla-Profil (vanlobby), NICHT der NeoForge-Spoof."""
    import app.services.app_setting_service as svc
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import dispatcher_service

    with SessionLocal() as db:
        db.add(Server(name="Lobby", slug="lob", server_type="paper", mc_version="1.21.1",
                      base_path=str(tmp_path / "lob"), gateway_enabled=True,
                      gateway_hostname="lobby", gateway_is_default=True, port=25569))
        db.commit()
        svc.set_network_domain(db, "mc.example.de")
        svc.set_network_port(db, 25565)
        svc.set_hub_lobby_enabled(db, True)
        svc.set_hub_lobby_vanilla_replay(db, "vanilla_capture.replay")

    script = (_login_start() + _login_ack() + _client_info()
              + _brand("fabric") + _register(["minecraft:register"]))
    sock = FakeSock(script)
    dispatcher_service.dispatch(sock, _hs(), b"")
    assert _find_transfer(sock.sent) == ("vanlobby.mc.example.de", 25565)


# --------------------------- Auto-Capture (2.3) ----------------------------- #
def _write_manifest_replay(path, mod_channels):
    from app.services import hub_capture_service
    from app.services.mc_protocol import _wrap_packet, encode_string, encode_varint

    blob = b"".join(encode_string(c) + b"\x00\x00" for c in mod_channels)
    frame = _wrap_packet(encode_varint(0x02) + encode_string("neoforge:register") + blob)
    hub_capture_service._write_replay(str(path), [(1, frame)])   # dir 1 = C->S


def test_replay_mod_namespaces_and_match(tmp_path):
    from app.services import hub_replay_service as R

    p = tmp_path / "atm.replay"
    _write_manifest_replay(p, ["create:network", "mekanism:tile", "ae2:main"])
    assert R.replay_mod_namespaces(str(p)) == frozenset({"create", "mekanism", "ae2"})
    # Voll-Abdeckung (Pack-Mods ⊆ Client, plus Client-only sodium) -> darf serviert werden.
    assert R.client_matches_replay({"create", "mekanism", "ae2", "sodium"}, str(p)) is True
    # Fehlt auch nur EINE Pack-Mod (ae2) -> NICHT servieren (sonst Registry-Kick).
    assert R.client_matches_replay({"create", "mekanism"}, str(p)) is False
    assert R.client_matches_replay({"twilightforest", "ironchests"}, str(p)) is False


def test_dispatch_auto_capture_unknown_pack(client, tmp_path, monkeypatch):
    from sqlalchemy import select

    import app.services.app_setting_service as svc
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import dispatcher_service, hub_capture_service, hub_replay_service

    monkeypatch.setattr(hub_replay_service, "REPLAY_DIR", str(tmp_path / "replays"))
    default_replay = tmp_path / "default.replay"
    _write_manifest_replay(default_replay, ["foo:bar", "baz:qux"])   # anderes Pack

    _make_neoforge_server(tmp_path / "seasons" / "mods", ["seasons", "create", "mekanism"])
    with SessionLocal() as db:
        db.add_all([
            Server(name="Lobby", slug="lob", server_type="paper", mc_version="1.21.1",
                   base_path=str(tmp_path / "lob"), gateway_enabled=True,
                   gateway_hostname="lobby", gateway_is_default=True, port=25569),
            Server(name="Seasons", slug="seasons", server_type="neoforge", mc_version="1.21.1",
                   base_path=str(tmp_path / "seasons"), gateway_enabled=True,
                   gateway_hostname="seasons", port=25602),
        ])
        db.commit()
        svc.set_network_domain(db, "mc.example.de")
        svc.set_network_port(db, 25565)
        svc.set_hub_lobby_enabled(db, True)
        svc.set_hub_lobby_replay(db, str(default_replay))
        sid = db.scalar(select(Server).where(Server.slug == "seasons")).id

    calls: dict = {}

    def fake_start(server_id, user_id=None):
        calls["sid"] = server_id
        return True, "ok"

    monkeypatch.setattr(hub_capture_service, "start_capture_for_server", fake_start)
    monkeypatch.setattr(hub_capture_service, "capture_status", lambda server_id: None)

    script = (_login_start() + _login_ack() + _client_info()
              + _brand("neoforge")
              + _register(["neoforge:register", "minecraft:register"])
              + _manifest(["seasons:net", "create:network", "mekanism:tile"]))
    sock = FakeSock(script)
    dispatcher_service.dispatch(sock, _hs(), b"")

    assert _find_transfer(sock.sent) is None          # kein Transfer -> Einrichtungs-Disconnect
    assert b"erstmalig" in bytes(sock.sent)           # klare Meldung
    assert calls["sid"] == sid                        # Auto-Capture angestossen


def test_dispatch_matched_server_without_replay_auto_captures(client, tmp_path, monkeypatch):
    """Gematchter Pack-Server OHNE eigenes Replay -> Auto-Capture DIESES Servers, NICHT das
    (moeglicherweise fremde) Default-Replay servieren. Genau der atm10sky-Bug: das Default
    'atm10_capture.replay' passt ~aehnlich, wuerde aber mit fehlender Registry kicken."""
    from sqlalchemy import select

    import app.services.app_setting_service as svc
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import dispatcher_service, hub_capture_service, hub_replay_service

    monkeypatch.setattr(hub_replay_service, "REPLAY_DIR", str(tmp_path / "replays"))
    default_replay = tmp_path / "default.replay"
    _write_manifest_replay(default_replay, ["create:network", "mekanism:tile", "ae2:main"])

    _make_neoforge_server(tmp_path / "atm" / "mods", ["create", "mekanism", "ae2"])
    with SessionLocal() as db:
        db.add_all([
            Server(name="Lobby", slug="lob", server_type="paper", mc_version="1.21.1",
                   base_path=str(tmp_path / "lob"), gateway_enabled=True,
                   gateway_hostname="lobby", gateway_is_default=True, port=25569),
            Server(name="ATM10", slug="atm", server_type="neoforge", mc_version="1.21.1",
                   base_path=str(tmp_path / "atm"), gateway_enabled=True,
                   gateway_hostname="atm10", port=25601),
        ])
        db.commit()
        svc.set_network_domain(db, "mc.example.de")
        svc.set_network_port(db, 25565)
        svc.set_hub_lobby_enabled(db, True)
        svc.set_hub_lobby_replay(db, str(default_replay))
        sid = db.scalar(select(Server).where(Server.slug == "atm")).id

    calls: dict = {}

    def fake_start(server_id, user_id=None):
        calls["sid"] = server_id
        return True, "ok"

    monkeypatch.setattr(hub_capture_service, "start_capture_for_server", fake_start)
    monkeypatch.setattr(hub_capture_service, "capture_status", lambda server_id: None)

    # Client-Mods decken das Default-Replay voll ab - trotzdem: eigener Server, eigenes
    # (fehlendes) Replay -> Aufnahme, KEIN Default-Serve.
    script = (_login_start() + _login_ack() + _client_info()
              + _brand("neoforge")
              + _register(["neoforge:register", "minecraft:register"])
              + _manifest(["create:network", "mekanism:tile", "ae2:main"]))
    sock = FakeSock(script)
    dispatcher_service.dispatch(sock, _hs(), b"")

    assert _find_transfer(sock.sent) is None       # kein Transfer -> Einrichtungs-Disconnect
    assert b"erstmalig" in bytes(sock.sent)
    assert calls["sid"] == sid                     # Auto-Capture DIESES Servers angestossen


# --------------------------- is_neoforge_client ----------------------------- #
def test_is_neoforge_client():
    assert mcd.is_neoforge_client("neoforge", []) is True
    assert mcd.is_neoforge_client("forge", []) is True
    assert mcd.is_neoforge_client("vanilla", ["neoforge:handshake"]) is True
    assert mcd.is_neoforge_client("vanilla", ["fml:handshake"]) is True
    assert mcd.is_neoforge_client("fabric", []) is False   # lenient -> Vanilla-Profil
    assert mcd.is_neoforge_client("fabric", ["minecraft:register"]) is False
    assert mcd.is_neoforge_client("vanilla", []) is False
