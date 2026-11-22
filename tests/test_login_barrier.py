"""Ohne Anmeldung ist nur die Login-Seite sichtbar - und nichts darueber hinaus.

Die Verwaltungsoberflaeche ist absichtlich aus dem Internet erreichbar. Alles hinter dem
Login gilt damit als intern; die Schranke selbst ist die einzige Verteidigungslinie.

Besonderheit dieses Projekts: die Anmeldung wird NICHT als FastAPI-Abhaengigkeit geprueft,
sondern im Funktionsrumpf (`_require_logged_in`, `_require_user`, `_require_super_admin`,
...). Ein `Depends` zu vergessen faellt sofort auf - eine vergessene Zeile im Rumpf nicht.
Genau diese Luecke schliesst der Test unten: er geht JEDE registrierte Route durch und
besteht darauf, dass im erreichbaren Quelltext eine Anmeldepruefung vorkommt.

Wer hier eine Route ergaenzen muss, weil sie wirklich oeffentlich sein soll, traegt sie in
OEFFENTLICH ein - bewusst und sichtbar, statt dass sie stillschweigend durchrutscht.
"""
from __future__ import annotations

import inspect
import re

import pytest

from app.main import create_app

# Diese Namen pruefen die Anmeldung. Kommt keiner davon im erreichbaren Quelltext vor,
# ist die Route ungeschuetzt.
_AUTH_MARKER = (
    "_require_logged_in",
    "_require_user",
    "_require_super_admin",
    "_ensure_user",
    "get_current_user_from_session",
    "get_current_active_user",
    "require_roles",
)

# Absichtlich ohne Anmeldung erreichbar - jede Zeile hier ist eine bewusste Entscheidung.
_OEFFENTLICH = (
    re.compile(r"^/$"),                   # leitet auf /login bzw. /dashboard
    re.compile(r"^/login$"),
    re.compile(r"^/logout$"),
    re.compile(r"^/static(/|$)"),
    re.compile(r"^/resourcepacks(/|$)"),  # MC-Clients laden Packs ohne Anmeldung
)


def _ist_oeffentlich(pfad: str) -> bool:
    return any(p.match(pfad) for p in _OEFFENTLICH)


# Wie viele Ebenen die Helferkette verfolgt wird.
#
# EINE war zu wenig [Fund 22.11.2026]: Der Freimarken-Bereich haelt seine drei Tore in
# _fuer_schreiben bzw. _fuer_korrektur, und jene rufen _require_stufe, das erst
# get_current_user_from_session ruft. Die Tafel meldete darum sieben geschuetzte Routen
# als ungeschuetzt. Gemessen wurde vor der Aenderung: Bei transitiver Verfolgung aendert
# sich das Urteil fuer GENAU diese sieben und fuer keine andere der ueber hundert Routen -
# die Tafel wird also nirgends schwaecher.
#
# Begrenzt, und zwar aus zwei Gruenden: Ein Kreis in der Kette hielte sonst nie an (der
# Besuchsvermerk faengt ihn, die Grenze ist der zweite Riegel), und je mehr Quelltext
# hereingezogen wird, desto mehr Gelegenheit fuer einen zufaelligen Treffer. Drei Ebenen
# sind die tiefste Kette im Haus plus eine.
_HELFERTIEFE = 4


def _erreichbarer_quelltext(fn, tiefe: int = _HELFERTIEFE) -> str:
    """Quelltext der Funktion plus der Helfer, an die sie - auch mittelbar - delegiert.

    Noetig, weil manche Route nur eine Zeile ist: `return _set_hub_running(...)`. Die
    Pruefung steckt dann im Helfer, nicht in der Route selbst - und manchmal erst im
    Helfer des Helfers.
    """
    gesehen: set = set()
    teile: list[str] = []

    def gehe(ziel, rest: int) -> None:
        if rest <= 0 or ziel in gesehen:
            return
        gesehen.add(ziel)
        try:
            quelle = inspect.getsource(ziel)
        except (OSError, TypeError):
            return
        teile.append(quelle)
        modul = inspect.getmodule(ziel)
        if modul is None:
            return
        for name in re.findall(r"\b(_[a-z_]+)\s*\(", quelle):
            helfer = getattr(modul, name, None)
            if callable(helfer):
                gehe(helfer, rest - 1)

    gehe(fn, tiefe)
    return "\n".join(teile)


def _routen():
    app = create_app()
    for route in app.routes:
        pfad = getattr(route, "path", None)
        fn = getattr(route, "endpoint", None)
        methoden = getattr(route, "methods", None)
        if not pfad or fn is None or not methoden:
            continue
        for methode in sorted(methoden - {"HEAD", "OPTIONS"}):
            yield methode, pfad, fn


def test_every_route_checks_the_login():
    """Keine Route ohne Anmeldepruefung - ausser den ausdruecklich oeffentlichen."""
    ungeschuetzt = [
        f"{methode} {pfad}  ({fn.__module__.split('.')[-1]}.{fn.__name__})"
        for methode, pfad, fn in _routen()
        if not _ist_oeffentlich(pfad)
        and not any(m in _erreichbarer_quelltext(fn) for m in _AUTH_MARKER)
    ]
    assert ungeschuetzt == [], (
        "Routen ohne Anmeldepruefung:\n  " + "\n  ".join(ungeschuetzt)
        + "\n\nEntweder eine Pruefung einbauen (_require_user/_require_super_admin/...) "
          "oder die Route bewusst in _OEFFENTLICH eintragen."
    )


def test_the_audit_actually_sees_routes():
    """Schutz gegen einen Test, der nichts prueft, weil die Aufzaehlung leer bleibt."""
    alle = list(_routen())
    assert len(alle) > 100, f"nur {len(alle)} Routen gefunden - Aufzaehlung kaputt?"
    assert any(p == "/dashboard" for _m, p, _f in alle)


def test_public_list_stays_short():
    """Die Liste der oeffentlichen Pfade soll auffallen, wenn sie waechst."""
    assert len(_OEFFENTLICH) == 5


@pytest.mark.parametrize(
    "pfad",
    ["/dashboard", "/settings", "/users", "/system-status", "/templates",
     "/audit-logs", "/security-events", "/resources", "/servers/create",
     "/servers/import", "/modpacks/import"],
)
def test_pages_redirect_to_login(client, pfad):
    antwort = client.get(pfad, follow_redirects=False)
    assert antwort.status_code == 303
    assert antwort.headers["location"] == "/login"


@pytest.mark.parametrize(
    "pfad,daten",
    [
        ("/servers/1/start", {}),
        ("/servers/1/stop", {}),
        ("/servers/1/console/command", {"command": "op Angreifer"}),
        ("/users", {"username": "eindringling", "password": "Geheim123!", "role": "operator"}),
        ("/settings/network", {"network_mode": "gateway"}),
    ],
)
def test_write_actions_redirect_to_login(client, pfad, daten):
    """Mit GUELTIGEM Formular - sonst stoppt schon die Validierung und der Test
    bewiese nichts ueber die Anmeldung."""
    antwort = client.post(pfad, data=daten, follow_redirects=False)
    assert antwort.status_code == 303, f"{pfad} antwortet {antwort.status_code}"
    assert antwort.headers["location"] == "/login"


def test_login_page_is_reachable(client):
    assert client.get("/login").status_code == 200


def test_root_sends_anonymous_visitors_to_login(client):
    antwort = client.get("/", follow_redirects=False)
    assert antwort.status_code == 303
    assert antwort.headers["location"] == "/login"


def test_die_suche_erkennt_eine_ungeschuetzte_kette():
    """Die Gegenprobe zur transitiven Verfolgung.

    Mehr Quelltext hereinzuziehen heisst mehr Gelegenheit fuer einen zufaelligen Treffer.
    Darum wird hier gefragt, ob eine Kette OHNE Pruefung weiterhin als ungeschuetzt
    erkannt wird - und ob eine Kette MIT Pruefung in der Tiefe erkannt wird. Ohne den
    ersten Satz waere ein "finde immer einen Marker" gruen, ohne den zweiten ein
    "finde nie einen".
    """
    from app.api.routers import freimarken as modul

    # Die echte Kette des Bereichs: Route -> _fuer_korrektur -> _require_stufe ->
    # get_current_user_from_session. Sie ist drei Ebenen tief.
    geschuetzt = _erreichbarer_quelltext(modul.freimarken_konto_korrigieren)
    assert any(m in geschuetzt for m in _AUTH_MARKER), \
        "die Kette des Freimarken-Bereichs muss gefunden werden"

    # Und eine Kette gleicher Tiefe ohne jede Pruefung darf NICHT als geschuetzt gelten.
    assert not any(m in _erreichbarer_quelltext(_ohne_pruefung) for m in _AUTH_MARKER), \
        "eine Kette ohne Pruefung darf nicht als geschuetzt gelten"

    # Und die Begrenzung muss greifen: bei Tiefe 1 ist nur die Funktion selbst zu sehen.
    assert not any(m in _erreichbarer_quelltext(modul.freimarken_konto_korrigieren,
                                                tiefe=1)
                   for m in _AUTH_MARKER), \
        "bei Tiefe 1 darf die Kette NICHT gefunden werden - sonst zaehlt die Grenze nicht"


def _zweite_ebene_ohne_pruefung() -> str:
    return "hier steht keine Anmeldepruefung"


def _erste_ebene_ohne_pruefung() -> str:
    return _zweite_ebene_ohne_pruefung()


def _ohne_pruefung() -> str:
    """Eine Kette von drei Funktionen, in der keine die Anmeldung prueft."""
    return _erste_ebene_ohne_pruefung()
