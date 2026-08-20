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
