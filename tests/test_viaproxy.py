"""ViaProxy-Uebersetzungsstufe (Option A: VOR Dispatcher/Hub, Loopback ins Gateway)."""

from __future__ import annotations

from app.services import viaproxy_service


def test_render_config_option_a():
    yml = viaproxy_service.render_config(
        {"port": 25601, "target_port": 25565, "target_version": "1.21.1"}
    )
    assert "bind-address: 127.0.0.1:25601" in yml       # intern, nicht oeffentlich
    assert "target-address: 127.0.0.1:25565" in yml     # ZURUECK ins Gateway (Loopback)
    assert "target-version: 1.21.1" in yml
    assert "rewrite-handshake-packet: false" in yml     # Original-Host durchreichen -> Routing bleibt
    assert "rewrite-transfer-packets: true" in yml      # Transfer-Emulation (auch <1.20.5)
    assert "proxy-online-mode: false" in yml


def test_viaproxy_config_targets_gateway_port(client):
    """target_port muss der GATEWAY-Port sein (Loopback), NICHT der Hub-Vanilla-Port."""
    import app.db.session as dbs
    from app.services import app_setting_service as A

    with dbs.SessionLocal() as db:
        A.set_network_port(db, 25599)
    cfg = A.get_viaproxy_config_runtime()
    assert cfg["target_port"] == 25599


def test_needs_viaproxy_translation():
    """Gateway-Weiche: nur nicht-767-JOINS bei aktivem ViaProxy werden uebersetzt."""
    from types import SimpleNamespace

    import app.services.gateway_service as gw
    import app.services.mc_protocol as mp

    on = SimpleNamespace(viaproxy_enabled=True, viaproxy_port=25601)
    off = SimpleNamespace(viaproxy_enabled=False, viaproxy_port=0)

    def hs(proto, ns):
        return SimpleNamespace(protocol_version=proto, next_state=ns)

    login = mp.NEXT_STATE_LOGIN
    status = mp.NEXT_STATE_STATUS

    assert gw._needs_viaproxy_translation(on, hs(770, login)) is True   # neuer Client -> uebersetzen
    assert gw._needs_viaproxy_translation(on, hs(47, login)) is True    # 1.8 -> uebersetzen
    assert gw._needs_viaproxy_translation(on, hs(767, login)) is False  # 1.21.1 -> nativer Pfad
    assert gw._needs_viaproxy_translation(on, hs(770, status)) is False # Status-Ping -> nicht
    assert gw._needs_viaproxy_translation(off, hs(770, login)) is False # ViaProxy aus -> nicht
    assert gw._needs_viaproxy_translation(on, hs(None, login)) is False # unbekannt -> nicht
