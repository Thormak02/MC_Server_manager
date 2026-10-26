"""Keine Drittabrufe im Browser, keine offene API-Doku.

Hintergrund Schriften: bindet eine Seite fonts.googleapis.com direkt ein, laedt der
Browser des Besuchers dort - und uebertraegt dabei dessen IP-Adresse an Google, bevor
er zustimmen konnte. Das LG Muenchen I hat das am 20.01.2022 (Az. 3 O 17493/20) als
Verstoss gewertet; danach kam eine Abmahnwelle. Die Schriften liegen deshalb lokal
unter app/web/static/fonts (erzeugt von scripts/fetch_fonts.py).

Hintergrund API-Doku: FastAPI registriert /docs, /redoc und /openapi.json selbst, und
vor ihnen liegen nur CSRF- und Session-Middleware - also KEINE Anmeldepflicht. Bei einer
aus dem Internet erreichbaren Verwaltungsoberflaeche kann damit jeder die komplette
API-Struktur auslesen. Sie sind deshalb ausserhalb des Debug-Betriebs abgeschaltet.
"""
from pathlib import Path

import pytest

_STATIC = Path(__file__).resolve().parents[1] / "app" / "web" / "static"
_TEMPLATES = Path(__file__).resolve().parents[1] / "app" / "web" / "templates"

# Hosts, die der Browser eines Besuchers NICHT von sich aus kontaktieren soll.
_FREMDHOSTS = ("fonts.googleapis.com", "fonts.gstatic.com", "googleapis.com")


# --------------------------------------------------------------------------- #
# Schriften
# --------------------------------------------------------------------------- #

def test_login_page_contacts_no_google(client):
    """Die oeffentlich erreichbare Seite - hier wiegt der Drittabruf am schwersten."""
    html = client.get("/login").text
    for host in _FREMDHOSTS:
        assert host not in html, f"{host} wird von der Login-Seite geladen"


def test_no_template_pulls_fonts_from_google():
    """Der Einbau stand in base.html und galt damit fuer JEDE Seite, nicht nur Login."""
    treffer = [
        f"{p.name}:{nr}"
        for p in _TEMPLATES.rglob("*.html")
        for nr, zeile in enumerate(p.read_text(encoding="utf-8").splitlines(), 1)
        if any(h in zeile for h in ("fonts.googleapis.com", "fonts.gstatic.com"))
        and "{#" not in zeile          # Kommentare duerfen den Grund nennen
    ]
    assert treffer == [], f"Google-Fonts-Einbindung noch vorhanden: {treffer}"


def test_local_font_stylesheet_is_served(client):
    antwort = client.get("/static/css/fonts.css")
    assert antwort.status_code == 200
    assert "@font-face" in antwort.text


def test_font_stylesheet_points_only_at_local_files(client):
    css = client.get("/static/css/fonts.css").text
    assert "https://" not in css.split("*/", 1)[-1], "Stylesheet verweist nach aussen"
    assert "/static/fonts/" in css


@pytest.mark.parametrize(
    "datei",
    [
        "Manrope-400-latin.woff2",
        "Manrope-500-latin.woff2",
        "Manrope-700-latin.woff2",
        "Manrope-800-latin.woff2",
        "IBMPlexMono-400-latin.woff2",
        "IBMPlexMono-500-latin.woff2",
    ],
)
def test_every_declared_weight_exists_locally(datei):
    """Fehlt ein Schnitt, faellt die Seite still auf eine Systemschrift zurueck."""
    pfad = _STATIC / "fonts" / datei
    assert pfad.is_file(), f"{datei} fehlt - scripts/fetch_fonts.py ausfuehren"
    assert pfad.stat().st_size > 1000


def test_font_licence_is_shipped():
    """Die SIL Open Font License verlangt, dass sie den Schriftdateien beiliegt."""
    lizenz = (_STATIC / "fonts" / "LICENSE.txt").read_text(encoding="utf-8")
    assert "SIL OPEN FONT LICENSE" in lizenz.upper()
    assert "Manrope" in lizenz and "IBM Plex Mono" in lizenz


def test_served_fonts_are_really_woff2(client):
    """Ein kaputter Download waere im Browser nur als falsche Schrift sichtbar."""
    antwort = client.get("/static/fonts/Manrope-400-latin.woff2")
    assert antwort.status_code == 200
    assert antwort.content[:4] == b"wOF2"


# --------------------------------------------------------------------------- #
# API-Doku
# --------------------------------------------------------------------------- #

@pytest.mark.parametrize("pfad", ["/docs", "/redoc", "/openapi.json"])
def test_api_docs_are_off_without_debug(client, pfad):
    """Ohne Anmeldung erreichbar waere die vollstaendige API-Beschreibung."""
    antwort = client.get(pfad)
    assert antwort.status_code == 404, f"{pfad} ist erreichbar (Status {antwort.status_code})"


def test_api_docs_can_be_turned_on_for_development():
    """Abschalten darf die Entwicklung nicht behindern - mit debug=True sind sie da."""
    import os

    from app.core.config import get_settings
    from app.main import create_app

    try:
        os.environ["MCSM_DEBUG"] = "true"
        get_settings.cache_clear()
        app = create_app()
        assert app.docs_url == "/docs"
        assert app.openapi_url == "/openapi.json"
    finally:
        os.environ.pop("MCSM_DEBUG", None)
        get_settings.cache_clear()
