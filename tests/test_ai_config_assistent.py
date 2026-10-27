"""Tests fuer den KI-Konfigurationsassistenten.

Der Assistent schreibt nichts: er fuellt das Formular, das es im Datei-Assistenten schon
gibt, und gespeichert wird mit dessen normalem Knopf. Entsprechend liegt der Schwerpunkt
dieser Tests nicht auf der Qualitaet der Vorschlaege (die kommt vom Modell), sondern
darauf, dass kein Vorschlag durchkommt, der Schaden anrichtet:

* ``level-name``, ``level-seed``, Ports und ``online-mode`` darf er nicht aendern - ein
  falscher Wert dort entwertet eine bestehende Welt oder trennt Spieler von ihren
  Spielstaenden, und zwar unsichtbar.
* Ein erfundener Schluessel darf nicht ins Formular wandern.
* Ein Wert, der nicht zur Art des bisherigen passt (Zahl statt true/false), kostet den
  Serverstart - also vorher abfangen.
* Nichts wird still verworfen: was nicht uebernommen wird, steht mit Grund da.

Kein Test spricht mit der echten API; der Anbieter wird ersetzt.
"""
import pytest


PROPS = """motd=Testserver
max-players=20
view-distance=10
simulation-distance=10
difficulty=normal
pvp=true
online-mode=true
level-name=world
server-port=25565
"""


class _WerkzeugAttrappe:
    """Anbieter-Attrappe, die wie das echte Modell ein Werkzeug aufruft."""

    def __init__(self, eingabe=None, text=""):
        self.eingabe = eingabe if eingabe is not None else {"aenderungen": []}
        self.text = text
        self.gesehen: list = []

    def frage(self, anfrage, schluessel, **_):
        from app.services.ai_provider_service import KiAntwort

        self.gesehen.append(anfrage)
        return KiAntwort(
            text=self.text, modell=anfrage.modell, tokens_ein=400, tokens_aus=200,
            werkzeug_aufrufe=[{"name": "konfigurationsvorschlag",
                               "eingabe": self.eingabe, "id": "tu_1"}],
            abbruchgrund="werkzeug")


def _bereite_vor(db, monkeypatch, attrappe, **einstellungen):
    from app.services import ai_provider_service, ai_settings_service as ais

    ais.setze_schluessel(db, "sk-ant-api03-TEST-1234567890")
    ais.speichere(db, {"aktiv": True, **einstellungen})
    monkeypatch.setitem(ai_provider_service.ANBIETER, "anthropic", attrappe)


def _nutzer(db, rolle="admin", name="config-tester"):
    from app.core.security import hash_password
    from app.models.user import User

    person = User(username=name, password_hash=hash_password("Egal12345!"),
                  role=rolle, is_active=True)
    db.add(person)
    db.commit()
    db.refresh(person)
    return person


def _server_mit_props(db, tmp_path, *, slug: str, port: int, inhalt: str = PROPS,
                      speicher_max: int | None = 4096):
    from app.models.server import Server

    basis = tmp_path / slug
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text(inhalt, encoding="utf-8")
    server = Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                    base_path=str(basis), port=port, status="stopped",
                    start_mode="command", start_command="java -jar server.jar",
                    memory_min_mb=1024, memory_max_mb=speicher_max)
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


def _login_super(client):
    antwort = client.post("/login", data={"username": "admin", "password": "admin123!"},
                          follow_redirects=False)
    assert antwort.status_code == 303


# ------------------------------------------------------------ Gesperrte Schluessel

def test_gesperrte_schluessel_kommen_nicht_durch():
    """Das Wichtigste ueberhaupt. Jeder dieser Werte richtet unsichtbaren Schaden an:
    eine neue leere Welt, eine Abbruchkante im Gelaende, ein unerreichbarer Server oder
    Spieler ohne Zugriff auf ihre Spielstaende."""
    from app.services import ai_config_service as acs

    vorhanden = {"level-name": "world", "level-seed": "123", "level-type": "normal",
                 "server-port": "25565", "server-ip": "", "online-mode": "true",
                 "rcon.password": "geheim", "enable-rcon": "false",
                 "query.port": "25565", "rcon.port": "25575"}
    vorschlaege = [{"schluessel": k, "wert": "anders", "begruendung": "x"}
                   for k in vorhanden]

    ergebnis = acs.pruefe_vorschlaege(vorschlaege, vorhanden)

    assert ergebnis["aenderungen"] == []
    assert len(ergebnis["abgelehnt"]) == len(vorhanden)
    # Jede Ablehnung muss einen Grund nennen, nicht bloss "nicht erlaubt".
    for eintrag in ergebnis["abgelehnt"]:
        assert len(eintrag["grund"]) > 15, eintrag


def test_erfundener_schluessel_wird_abgelehnt():
    """Ein Schluessel, den es in der Datei nicht gibt, ist entweder ein Tippfehler oder
    gehoert in eine andere Datei - im Formular gibt es dafuer kein Feld."""
    from app.services import ai_config_service as acs

    ergebnis = acs.pruefe_vorschlaege(
        [{"schluessel": "max-chunk-threads", "wert": "4", "begruendung": "x"}],
        {"view-distance": "10"})

    assert ergebnis["aenderungen"] == []
    assert "steht nicht in dieser Datei" in ergebnis["abgelehnt"][0]["grund"]


def test_falsche_wertart_wird_abgefangen():
    """Eine Zahl, wo true/false hingehoert, kostet den naechsten Serverstart."""
    from app.services import ai_config_service as acs

    ergebnis = acs.pruefe_vorschlaege(
        [{"schluessel": "pvp", "wert": "7", "begruendung": "x"},
         {"schluessel": "view-distance", "wert": "weit", "begruendung": "x"}],
        {"pvp": "true", "view-distance": "10"})

    assert ergebnis["aenderungen"] == []
    gruende = " ".join(e["grund"] for e in ergebnis["abgelehnt"])
    assert "true oder false" in gruende
    assert "eine Zahl" in gruende


def test_unveraenderter_wert_ist_kein_vorschlag():
    """Sonst steht im Formular ein Haken, der nichts tut - und der Nutzer sucht den
    Unterschied."""
    from app.services import ai_config_service as acs

    ergebnis = acs.pruefe_vorschlaege(
        [{"schluessel": "view-distance", "wert": "10", "begruendung": "x"}],
        {"view-distance": "10"})

    assert ergebnis["aenderungen"] == []
    assert "schon so" in ergebnis["abgelehnt"][0]["grund"]


def test_doppelter_schluessel_wird_gemeldet():
    from app.services import ai_config_service as acs

    ergebnis = acs.pruefe_vorschlaege(
        [{"schluessel": "view-distance", "wert": "8", "begruendung": "x"},
         {"schluessel": "view-distance", "wert": "6", "begruendung": "y"}],
        {"view-distance": "10"})

    assert len(ergebnis["aenderungen"]) == 1
    assert ergebnis["aenderungen"][0]["wert"] == "8"
    assert "doppelt" in ergebnis["abgelehnt"][0]["grund"]


def test_nichts_wird_still_verworfen():
    """Der entscheidende Punkt: zu jedem eingereichten Vorschlag gibt es eine Spur -
    entweder in aenderungen oder in abgelehnt. Ein stiller Wegfall waere das Schlimmste,
    denn dann glaubt der Nutzer, etwas sei gesetzt, was nie ankam."""
    from app.services import ai_config_service as acs

    vorschlaege = [
        {"schluessel": "view-distance", "wert": "8", "begruendung": "x"},
        {"schluessel": "level-name", "wert": "neu", "begruendung": "x"},
        {"schluessel": "gibtsnicht", "wert": "1", "begruendung": "x"},
        {"schluessel": "pvp", "wert": "false", "begruendung": "x"},
        {"schluessel": "pvp", "wert": "true", "begruendung": "x"},
        {"schluessel": "difficulty", "wert": "hard", "begruendung": "x"},
    ]
    ergebnis = acs.pruefe_vorschlaege(
        vorschlaege, {"view-distance": "10", "level-name": "world", "pvp": "true",
                      "difficulty": "normal"})

    assert len(ergebnis["aenderungen"]) + len(ergebnis["abgelehnt"]) == len(vorschlaege)


def test_bedenkliche_aenderungen_werden_benannt():
    """difficulty und pvp sind erlaubt, aber sie aendern das Spiel fuer alle. Die
    Oberflaeche hakt sie deshalb nicht vor - dazu braucht sie diese Angabe."""
    from app.services import ai_config_service as acs

    ergebnis = acs.pruefe_vorschlaege(
        [{"schluessel": "difficulty", "wert": "hard", "begruendung": "x"},
         {"schluessel": "view-distance", "wert": "8", "begruendung": "x"}],
        {"difficulty": "normal", "view-distance": "10"})

    nach_schluessel = {a["schluessel"]: a for a in ergebnis["aenderungen"]}
    assert nach_schluessel["difficulty"]["bedenklich"]
    assert not nach_schluessel["view-distance"]["bedenklich"]


# -------------------------------------------------------------------- Eckdaten

def test_eckdaten_nennen_rechner_und_andere_server(client, tmp_path):
    """Ein Ratschlag zum Arbeitsspeicher ohne den RAM des Rechners und ohne das, was die
    anderen Server schon belegen, ist geraten."""
    from app.db.session import SessionLocal
    from app.services import ai_config_service as acs

    with SessionLocal() as db:
        ziel = _server_mit_props(db, tmp_path, slug="eck-a", port=25641,
                                 speicher_max=4096)
        _server_mit_props(db, tmp_path, slug="eck-b", port=25642, speicher_max=8192)

        daten = acs.eckdaten(db, ziel)

    assert daten["typ"] == "paper"
    assert daten["speicher_max_mb"] == 4096
    assert daten["allen_servern_zugewiesen_mb"] == 4096 + 8192
    assert any("eck-b" in eintrag for eintrag in daten["andere_server"])
    # Der RAM des Rechners kommt von psutil; ohne psutil ist None richtig, aber nicht 0.
    assert daten["rechner_ram_mb"] is None or daten["rechner_ram_mb"] > 0


def test_speichervorschlag_warnt_vor_ueberbuchung(client, tmp_path):
    """Mehr zuweisen als vorhanden fuehrt nicht zu einem langsamen Server, sondern zu
    einem, der nicht startet - oder zu einem lahmgelegten Rechner."""
    from app.services import ai_config_service as acs

    daten = {"rechner_ram_mb": 16384, "allen_servern_zugewiesen_mb": 12288,
             "speicher_max_mb": 4096, "speicher_min_mb": 1024}
    geprueft = acs._speicher_pruefen({"min_mb": 2048, "max_mb": 12288,
                                      "begruendung": "mehr ist besser"}, daten)

    assert geprueft["max_mb"] == 12288
    assert geprueft["warnungen"], "Die Ueberbuchung wurde nicht erkannt"
    assert "2 GB" in " ".join(geprueft["warnungen"])


def test_unsinniger_speichervorschlag_wird_verworfen():
    from app.services import ai_config_service as acs

    assert acs._speicher_pruefen({"min_mb": 8192, "max_mb": 2048}, {}) is None
    assert acs._speicher_pruefen({"max_mb": 0}, {}) is None
    assert acs._speicher_pruefen(None, {}) is None
    assert acs._speicher_pruefen({"max_mb": "viel"}, {}) is None


# ----------------------------------------------------------------- Zusammenspiel

def test_werkzeug_wird_erzwungen(client, monkeypatch, tmp_path):
    """Ohne Zwang antwortet das Modell in Prosa, und die Vorschlaege muessten geraten
    werden. Der Zwang muss also tatsaechlich bis zur Anfrage durchkommen."""
    from app.db.session import SessionLocal
    from app.services import ai_config_service as acs

    attrappe = _WerkzeugAttrappe({"aenderungen": [
        {"schluessel": "view-distance", "wert": "8", "begruendung": "40 Spieler"}]})
    with SessionLocal() as db:
        person = _nutzer(db, name="zwang1")
        _bereite_vor(db, monkeypatch, attrappe)
        server = _server_mit_props(db, tmp_path, slug="zwang", port=25643)

        ergebnis = acs.schlage_vor(db, user=person, server=server,
                                   pfad="server.properties", inhalt=PROPS)

    anfrage = attrappe.gesehen[0]
    assert anfrage.werkzeugzwang == "konfigurationsvorschlag"
    assert anfrage.werkzeuge and anfrage.werkzeuge[0]["name"] == "konfigurationsvorschlag"
    assert len(ergebnis["aenderungen"]) == 1
    assert ergebnis["aenderungen"][0]["alt"] == "10"


def test_eckdaten_gehen_mit(client, monkeypatch, tmp_path):
    """Ohne die Eckdaten waere jede Begruendung allgemein statt auf diesen Server bezogen."""
    from app.db.session import SessionLocal
    from app.services import ai_config_service as acs

    attrappe = _WerkzeugAttrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="eck1")
        _bereite_vor(db, monkeypatch, attrappe)
        server = _server_mit_props(db, tmp_path, slug="eck-mit", port=25644)
        acs.schlage_vor(db, user=person, server=server, pfad="server.properties",
                        inhalt=PROPS)

    gesendet = attrappe.gesehen[0].nachrichten[0]["inhalt"]
    assert "paper 26.3" in gesendet
    assert "4096 MB" in gesendet
    assert "view-distance=10" in gesendet
    # Die gesperrten Schluessel stehen im Auftrag, damit das Modell sie gar nicht erst
    # vorschlaegt - die Pruefung danach ist nur das Netz darunter.
    assert "level-name" in attrappe.gesehen[0].auftrag
    assert "online-mode" in attrappe.gesehen[0].auftrag


def test_gesperrter_vorschlag_des_modells_erreicht_das_formular_nicht(
        client, monkeypatch, tmp_path):
    """Der Auftrag sagt dem Modell, es soll level-name nicht anfassen. Falls es das
    trotzdem tut, muss die Pruefung greifen - darauf verlassen darf man sich nicht."""
    from app.db.session import SessionLocal
    from app.services import ai_config_service as acs

    attrappe = _WerkzeugAttrappe({"aenderungen": [
        {"schluessel": "level-name", "wert": "lobby", "begruendung": "besserer Name"},
        {"schluessel": "view-distance", "wert": "8", "begruendung": "Last"}]})
    with SessionLocal() as db:
        person = _nutzer(db, name="gesperrt1")
        _bereite_vor(db, monkeypatch, attrappe)
        server = _server_mit_props(db, tmp_path, slug="gesperrt", port=25645)

        ergebnis = acs.schlage_vor(db, user=person, server=server,
                                   pfad="server.properties", inhalt=PROPS)

    schluessel = [a["schluessel"] for a in ergebnis["aenderungen"]]
    assert "level-name" not in schluessel
    assert "view-distance" in schluessel
    assert any(a["schluessel"] == "level-name" for a in ergebnis["abgelehnt"])
    assert any("nicht uebernommen" in h for h in ergebnis["hinweise"])


def test_leere_datei_wird_ohne_aufruf_abgelehnt(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_config_service as acs, ai_service

    attrappe = _WerkzeugAttrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="leer1")
        _bereite_vor(db, monkeypatch, attrappe)
        server = _server_mit_props(db, tmp_path, slug="leer", port=25646)

        with pytest.raises(ai_service.KiAbgelehnt, match="leer"):
            acs.schlage_vor(db, user=person, server=server, pfad="server.properties",
                            inhalt="   \n")

    assert attrappe.gesehen == []


# --------------------------------------------------------------------- Routen

def test_vorschlag_route_schreibt_nichts(client, monkeypatch, tmp_path):
    """Der Kern der Entscheidung "vorschlagen, du bestaetigst": die Datei auf der Platte
    muss nach dem Aufruf unveraendert sein."""
    from app.db.session import SessionLocal

    attrappe = _WerkzeugAttrappe({"aenderungen": [
        {"schluessel": "view-distance", "wert": "8", "begruendung": "Last"}]})
    with SessionLocal() as db:
        _bereite_vor(db, monkeypatch, attrappe)
        server = _server_mit_props(db, tmp_path, slug="route-w", port=25647)
        server_id = server.id
        datei = tmp_path / "route-w" / "server.properties"

    vorher = datei.read_text(encoding="utf-8")
    _login_super(client)
    antwort = client.post(f"/api/servers/{server_id}/files/ki-vorschlag",
                          data={"file": "server.properties"})
    assert antwort.status_code == 200, antwort.text
    assert antwort.json()["aenderungen"][0]["schluessel"] == "view-distance"
    assert datei.read_text(encoding="utf-8") == vorher


def test_vorschau_route_kostet_nichts(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal

    attrappe = _WerkzeugAttrappe()
    with SessionLocal() as db:
        _bereite_vor(db, monkeypatch, attrappe)
        server = _server_mit_props(db, tmp_path, slug="route-v", port=25648)
        server_id = server.id

    _login_super(client)
    antwort = client.get(f"/api/servers/{server_id}/files/ki-vorschau"
                         "?file=server.properties")
    assert antwort.status_code == 200
    assert attrappe.gesehen == []
    assert "view-distance=10" in antwort.json()["text"]


def test_nicht_unterstuetzte_datei_wird_erklaert(client, tmp_path):
    """Die Absage soll sagen, WARUM - nicht bloss "geht nicht"."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        server = _server_mit_props(db, tmp_path, slug="route-u", port=25649)
        server_id = server.id
        (tmp_path / "route-u" / "spigot.yml").write_text("settings:\n  x: 1\n",
                                                         encoding="utf-8")

    _login_super(client)
    antwort = client.post(f"/api/servers/{server_id}/files/ki-vorschlag",
                          data={"file": "spigot.yml"})
    assert antwort.status_code == 400
    assert "Istzustand" in antwort.json()["detail"]


def test_vorschlag_route_haelt_den_aufruf_fest(client, monkeypatch, tmp_path):
    from sqlalchemy import select

    from app.db.session import SessionLocal
    from app.models.audit_log import AuditLog

    with SessionLocal() as db:
        _bereite_vor(db, monkeypatch, _WerkzeugAttrappe({"aenderungen": [
            {"schluessel": "view-distance", "wert": "8", "begruendung": "x"}]}))
        server = _server_mit_props(db, tmp_path, slug="route-p", port=25650)
        server_id = server.id

    _login_super(client)
    assert client.post(f"/api/servers/{server_id}/files/ki-vorschlag",
                       data={"file": "server.properties"}).status_code == 200

    with SessionLocal() as db:
        eintraege = list(db.scalars(
            select(AuditLog).where(AuditLog.action == "ai.config_suggest")))
    assert len(eintraege) == 1
    assert eintraege[0].server_id == server_id


def test_vorschlag_route_braucht_anmeldung(client, tmp_path):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        server = _server_mit_props(db, tmp_path, slug="route-anon", port=25651)
        server_id = server.id

    antwort = client.post(f"/api/servers/{server_id}/files/ki-vorschlag",
                          data={"file": "server.properties"})
    assert antwort.status_code == 401


# ---------------------------------------------------------------- Oberflaeche

def test_dateiseite_zeigt_den_assistenten(client, tmp_path):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        server = _server_mit_props(db, tmp_path, slug="seite-a", port=25652)
        server_id = server.id

    _login_super(client)
    seite = client.get(f"/servers/{server_id}/files?file=server.properties&mode=assistant")
    assert seite.status_code == 200
    assert "KI-Vorschlag fuer diese Einstellungen" in seite.text
    assert "kic-senden-knopf" in seite.text
    # Der Hinweis, dass nichts geschrieben wird, muss auf der Seite stehen.
    assert "Geschrieben wird nichts" in seite.text


def test_assistent_fehlt_ohne_strukturierte_datei(client, tmp_path):
    """Fuer eine Datei, deren Schluessel der Manager nicht lesen kann, gibt es keinen
    Istzustand zum Vergleichen - und damit keinen Schutz vor erfundenen Schluesseln."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        server = _server_mit_props(db, tmp_path, slug="seite-b", port=25653)
        server_id = server.id
        (tmp_path / "seite-b" / "notizen.txt").write_text("nur Text\n", encoding="utf-8")

    _login_super(client)
    seite = client.get(f"/servers/{server_id}/files?file=notizen.txt&mode=assistant")
    assert seite.status_code == 200
    assert "kic-senden-knopf" not in seite.text
