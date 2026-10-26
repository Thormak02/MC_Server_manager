"""Tests fuer die Spielerkoepfe.

Zwei Dinge stehen hier im Mittelpunkt:
1. Der Browser darf NIE einen Fremddienst kontaktieren - die Skin-Datei kommt vom
   Manager. Das ist derselbe Grund, aus dem die Schriften lokal liegen.
2. Die Online-Namen stammen aus dem Konsolen-Log und koennen Unfug enthalten
   (Chat-Echo, Plugin-Praefixe, Farbcodes). Solche Eintraege duerfen weder ein
   Icon bekommen noch eine Anfrage nach draussen ausloesen.
"""
import struct
import zlib

import pytest


def _login_admin(client):
    antwort = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert antwort.status_code == 303


def _png_bloecke(daten: bytes) -> dict:
    """PNG wirklich zerlegen statt nur die Signatur zu pruefen - ein kaputtes
    Bild faellt sonst erst im Browser auf."""
    assert daten[:8] == b"\x89PNG\r\n\x1a\n"
    bloecke, pos = {}, 8
    while pos < len(daten):
        laenge = struct.unpack(">I", daten[pos:pos + 4])[0]
        typ = daten[pos + 4:pos + 8]
        inhalt = daten[pos + 8:pos + 8 + laenge]
        pruef = struct.unpack(">I", daten[pos + 8 + laenge:pos + 12 + laenge])[0]
        assert zlib.crc32(typ + inhalt) & 0xFFFFFFFF == pruef, f"CRC falsch in {typ!r}"
        bloecke[typ] = inhalt
        pos += 12 + laenge
    return bloecke


def test_default_skin_is_a_valid_png():
    """Der Ersatzkopf wird ohne Bildbibliothek von Hand gebaut - also wirklich pruefen."""
    from app.services.avatar_service import STANDARD_SKIN_PNG

    bloecke = _png_bloecke(STANDARD_SKIN_PNG)
    breite, hoehe, tiefe, farbtyp = struct.unpack(">IIBB", bloecke[b"IHDR"][:10])
    assert (breite, hoehe) == (64, 64)      # Skin-Format, aus dem das CSS schneidet
    assert (tiefe, farbtyp) == (8, 6)       # 8 Bit RGBA

    roh = zlib.decompress(bloecke[b"IDAT"])
    assert len(roh) == hoehe * (1 + breite * 4)

    def pixel(x, y):
        start = y * (1 + breite * 4) + 1 + x * 4
        return tuple(roh[start:start + 4])

    assert pixel(12, 12)[3] == 255, "Kopfbereich muss deckend sein"
    assert pixel(0, 0)[3] == 0, "ausserhalb des Kopfes muss durchsichtig sein"


@pytest.mark.parametrize("eingabe,erwartet", [
    ("Thormak02", "thormak02"),
    ("  Notch  ", "notch"),
    ("69f230e1-af2c-4d0e-9885-93d66595a855", "69f230e1af2c4d0e988593d66595a855"),
    ("69f230e1af2c4d0e988593d66595a855", "69f230e1af2c4d0e988593d66595a855"),
])
def test_normalisiere_akzeptiert_namen_und_uuids(eingabe, erwartet):
    from app.services.avatar_service import normalisiere

    assert normalisiere(eingabe) == erwartet


@pytest.mark.parametrize("muell", [
    "",
    "   ",
    "192.168.1.5",                    # IP-Bann, kein Spieler
    "<Thormak02> xy",                 # Chat-Echo durch die gierige Join-Regex
    "[Essentials] Spieler",           # Plugin-Praefix
    "§eBunt",                    # Farbcode
    ".BedrockSpieler",                # Floodgate-Name
    "Viel_zu_langer_Spielername",     # ueber 16 Zeichen
    "../../etc/passwd",               # Pfad-Ausbruch
    "a/b",
])
def test_normalisiere_lehnt_nichtspieler_ab(muell):
    """Kein Schluessel heisst: kein Icon, kein Dateiname, keine Anfrage nach draussen."""
    from app.services.avatar_service import normalisiere

    assert normalisiere(muell) is None


@pytest.fixture()
def _kein_netz(monkeypatch):
    """Sicherheitsnetz: kein Test darf wirklich bei Mojang anklopfen."""
    def verboten(*args, **kwargs):
        raise AssertionError("Test hat eine echte Netzwerkanfrage versucht")

    monkeypatch.setattr("app.services.avatar_service._hole", verboten)
    from app.services import avatar_service

    avatar_service.leere_caches()


def test_skin_png_falls_back_without_network(tmp_path, client, monkeypatch, _kein_netz):
    """Netz weg, Name unbekannt, Offline-Mode: Ersatzkopf statt Absturz."""
    from app.services import avatar_service

    monkeypatch.setattr(avatar_service.get_settings(), "data_dir", tmp_path, raising=False)
    png, echt = avatar_service.skin_png("Niemand")
    assert echt is False
    assert png == avatar_service.STANDARD_SKIN_PNG


def test_skin_png_never_asks_for_non_players(client, _kein_netz):
    """Ein IP-Bann oder ein Log-Fehltreffer darf keine Anfrage ausloesen -
    _hole wuerde in diesem Test sonst knallen."""
    from app.services import avatar_service

    for muell in ("192.168.1.5", "<Thormak02> xy", "[Essentials] Spieler", ""):
        png, echt = avatar_service.skin_png(muell)
        assert echt is False
        assert png == avatar_service.STANDARD_SKIN_PNG


def test_skin_png_uses_and_caches_the_real_skin(tmp_path, client, monkeypatch):
    """Erfolgsfall: aufloesen, holen, auf Platte legen - und beim zweiten Mal
    NICHT erneut ins Netz."""
    import base64
    import json

    from app.services import avatar_service

    avatar_service.leere_caches()
    monkeypatch.setattr(avatar_service.get_settings(), "data_dir", tmp_path, raising=False)

    echtes_png = avatar_service._png(64, 64, bytes(64 * 64 * 4))
    texturen = base64.b64encode(json.dumps({
        "textures": {"SKIN": {"url": "http://textures.minecraft.net/texture/abc"}}
    }).encode()).decode()

    aufrufe = []

    def gefaelscht(url, zeitlimit):
        aufrufe.append(url)
        if "api.mojang.com" in url:
            return json.dumps({"id": "69f230e1af2c4d0e988593d66595a855"}).encode()
        if "sessionserver" in url:
            return json.dumps({"properties": [
                {"name": "textures", "value": texturen}
            ]}).encode()
        return echtes_png

    monkeypatch.setattr(avatar_service, "_hole", gefaelscht)

    png, echt = avatar_service.skin_png("Thormak02")
    assert echt is True
    assert png == echtes_png
    # http-Adresse muss auf https angehoben worden sein.
    assert any(u.startswith("https://textures.minecraft.net") for u in aufrufe)

    vorher = len(aufrufe)
    png2, echt2 = avatar_service.skin_png("Thormak02")
    assert (png2, echt2) == (echtes_png, True)
    assert len(aufrufe) == vorher, "zweiter Aufruf muss aus dem Plattencache kommen"


def test_avatar_endpoint_is_behind_login(client):
    """Alles ausser der Anmeldeseite ist intern - der Endpunkt auch."""
    antwort = client.get("/api/avatar/Thormak02", follow_redirects=False)
    assert antwort.status_code == 303
    assert "/login" in antwort.headers.get("location", "")


def test_avatar_endpoint_serves_png(client, monkeypatch, _kein_netz):
    from app.services import avatar_service

    _login_admin(client)
    antwort = client.get("/api/avatar/Niemand")
    assert antwort.status_code == 200
    assert antwort.headers["content-type"] == "image/png"
    assert antwort.content == avatar_service.STANDARD_SKIN_PNG
    # Ersatzkopf nur kurz halten, damit der echte Skin bald nachrueckt.
    assert "max-age=300" in antwort.headers.get("cache-control", "")
    assert "private" in antwort.headers.get("cache-control", "")


def test_pages_never_reference_a_foreign_avatar_service():
    """Der Kern der Sache: in keinem Template darf eine fremde Avatar-Adresse stehen.

    Genau dieser Fehler war bei den Google Fonts der Anlass der Abmahnwelle 2022 -
    der Browser des Betrachters liefert dabei seine IP an einen Dritten.
    """
    from pathlib import Path

    fremd = ("crafatar.com", "mc-heads.net", "minotar.net", "visage.surgeplay.com",
             "cravatar.eu", "api.mineatar.io", "textures.minecraft.net")
    wurzel = Path("app/web")
    for datei in list(wurzel.rglob("*.html")) + list(wurzel.rglob("*.js")) + list(wurzel.rglob("*.css")):
        inhalt = datei.read_text(encoding="utf-8", errors="ignore").lower()
        for dienst in fremd:
            assert dienst not in inhalt, f"{datei} laedt Avatare von {dienst}"


def test_access_lists_show_heads_but_ip_bans_do_not(client, tmp_path, monkeypatch, _kein_netz):
    """OP-/Whitelist-/Bann-Listen bekommen Koepfe - IP-Banns nicht, eine IP ist
    kein Spieler."""
    from app.db.session import SessionLocal
    from app.models.server import Server

    basis = tmp_path / "srv"
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text("max-players=20\n", encoding="utf-8")
    (basis / "ops.json").write_text(
        '[{"uuid":"69f230e1-af2c-4d0e-9885-93d66595a855","name":"Thormak02","level":4}]',
        encoding="utf-8")
    (basis / "banned-ips.json").write_text(
        '[{"ip":"192.168.1.5","reason":"Test"}]', encoding="utf-8")

    with SessionLocal() as db:
        srv = Server(name="S", slug="s", server_type="paper", mc_version="1.21.11",
                     base_path=str(basis), port=25599, status="stopped", start_mode="bat")
        db.add(srv)
        db.commit()
        sid = srv.id

    _login_admin(client)

    ops = client.get(f"/servers/{sid}/access?tab=ops")
    assert ops.status_code == 200
    assert "player-head" in ops.text
    assert "/api/avatar/69f230e1af2c4d0e988593d66595a855" in ops.text

    ips = client.get(f"/servers/{sid}/access?tab=banned_ips")
    assert ips.status_code == 200
    assert "192.168.1.5" in ips.text
    assert "player-head" not in ips.text, "eine IP ist kein Spieler"


def test_online_list_shows_head_but_skips_log_garbage(client, tmp_path, monkeypatch, _kein_netz):
    """Die Online-Namen kommen aus dem Konsolen-Log. Ein echter Name bekommt einen
    Kopf, ein verunglueckter Eintrag (Chat-Echo durch die gierige Join-Regex) nicht."""
    from app.db.session import SessionLocal
    from app.models.server import Server

    basis = tmp_path / "srv"
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text("max-players=20\n", encoding="utf-8")

    with SessionLocal() as db:
        srv = Server(name="S", slug="s", server_type="paper", mc_version="1.21.11",
                     base_path=str(basis), port=25599, status="running", start_mode="bat")
        db.add(srv)
        db.commit()
        sid = srv.id

    monkeypatch.setattr(
        "app.api.routers.servers.get_online_player_names",
        lambda server_id: ["Thormak02", "<Thormak02> xy"],
    )

    _login_admin(client)
    seite = client.get(f"/servers/{sid}")
    assert seite.status_code == 200

    # Gezielt im Listen-Container zaehlen: die Zeichenkette "player-head" steht
    # auch in der JavaScript-Funktion weiter unten im selben Dokument.
    start = seite.text.index('id="server-players-list"')
    liste = seite.text[start:seite.text.index("</div>", start)]
    assert "/api/avatar/thormak02" in liste
    assert liste.count("player-head") == 1, "nur der echte Name bekommt einen Kopf"
    assert "&lt;Thormak02&gt; xy" in liste, "der Eintrag selbst bleibt sichtbar"


def test_live_player_refresh_also_draws_heads():
    """Die Liste wird alle 5 s per JavaScript neu gezeichnet. Faellt das Icon dort
    weg, ist es nach dem ersten Aktualisieren verschwunden - ein Fehler, den man
    im Test nicht sieht, wenn man nur das gerenderte HTML prueft."""
    from pathlib import Path

    quelle = Path("app/web/templates/server_detail.html").read_text(encoding="utf-8")
    assert "function kopfHtml(" in quelle
    assert "${kopfHtml(name)}" in quelle
    # Name landet in einer URL -> muss URL-kodiert werden, nicht nur HTML-escaped.
    assert "encodeURIComponent" in quelle


@pytest.mark.parametrize("uuid,online", [
    # Echte UUIDs aus den Listen des Nutzers - die dritte Gruppe traegt die Version.
    ("79668c7e-c702-3ba1-9b9e-20219be4de43", False),   # Offline-Mode (Version 3)
    ("69f230e1-af2c-4d0e-9885-93d66595a855", True),
    ("2abd16ed-9f71-4a80-994e-71dd35b8c53e", True),
    ("db317fd4-e539-4c49-a174-e6da13e7012c", True),
    ("4eda60c7-1b3d-4bc5-93ce-42f35911befd", True),
])
def test_ist_online_uuid(uuid, online):
    from app.services.avatar_service import ist_online_uuid

    assert ist_online_uuid(uuid) is online


def test_offline_uuid_falls_back_to_name(client):
    """Ein Server im Offline-Mode vergibt die UUID selbst (Version 3, aus dem Namen
    gehasht). Mojang kennt sie nicht - dann muss der NAME als Schluessel dienen,
    sonst sieht derselbe Spieler in der Whitelist anders aus als in der OP-Liste.
    """
    from app.web.routes.pages import _avatar_schluessel

    # Offline-UUID -> Name gewinnt
    assert _avatar_schluessel("79668c7e-c702-3ba1-9b9e-20219be4de43", "Thormak2002") == "thormak2002"
    # Echte UUID -> bleibt, sie ueberlebt eine Umbenennung
    assert (_avatar_schluessel("69f230e1-af2c-4d0e-9885-93d66595a855", "Thormak2002")
            == "69f230e1af2c4d0e988593d66595a855")
    # Keine UUID -> Name
    assert _avatar_schluessel(None, "Thormak2002") == "thormak2002"
    # Weder noch -> kein Icon
    assert _avatar_schluessel("192.168.1.5", "192.168.1.5") is None
    # Offline-UUID ohne brauchbaren Namen -> lieber die UUID als gar nichts
    assert (_avatar_schluessel("79668c7e-c702-3ba1-9b9e-20219be4de43", "[Essentials] X")
            == "79668c7ec7023ba19b9e20219be4de43")


def test_head_crop_keeps_aspect_ratio_for_old_skins():
    """Alte Konten haben Skins im Format 64x32 statt 64x64. Eine feste Hoehe im
    background-size wuerde so ein Bild vertikal auf das Doppelte ziehen - der
    Ausschnitt saesse dann an der falschen Stelle und der Kopf saehe verzerrt aus.
    """
    from pathlib import Path

    css = Path("app/web/static/css/app.css").read_text(encoding="utf-8")
    block = css[css.index(".player-head {"):css.index(".player-name {")]
    assert "background-size: calc(var(--kopf) * 8) auto;" in block
    assert "image-rendering: pixelated" in block
    # Gesicht (8,8) und Huthaut (40,8), bei Massstab kopf/8 pro Quellpixel.
    assert "calc(var(--kopf) * -5) calc(var(--kopf) * -1)" in block
    assert "calc(var(--kopf) * -1) calc(var(--kopf) * -1)" in block
