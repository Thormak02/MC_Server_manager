"""Tests fuer die KI-Loganalyse.

Der Schwerpunkt liegt auf der AUSWAHL: ein Absturzlog hat zehntausende Zeilen, von denen
ein paar hundert zaehlen. Wird falsch ausgewaehlt, kostet die Anfrage viel und sagt wenig -
oder sie kostet wenig und laesst genau die Zeile weg, in der die Ursache steht.

Konkret wird geprueft:
* Der Kopf (Software, Version, Mods) kommt mit, auch wenn er weit vor dem Absturz liegt.
* Ein Stacktrace wird NICHT in der Mitte abgeschnitten - abgeschnitten ist er wertlos.
* Die Vorschau kostet nichts: der Anbieter wird dabei nicht angefasst.

Kein Test spricht mit der echten API; der Anbieter wird durch eine Attrappe ersetzt.
"""
from pathlib import Path

import pytest


# ------------------------------------------------------------------- Hilfsmittel

class _Attrappe:
    """Anbieter-Attrappe: antwortet, ohne das Netz zu beruehren."""

    def __init__(self, text="Antwort", tokens=(100, 50)):
        self.text, self.tokens = text, tokens
        self.gesehen: list = []

    def frage(self, anfrage, schluessel, **_):
        from app.services.ai_provider_service import KiAntwort

        self.gesehen.append(anfrage)
        return KiAntwort(text=self.text, modell=anfrage.modell,
                         tokens_ein=self.tokens[0], tokens_aus=self.tokens[1],
                         werkzeug_aufrufe=[], abbruchgrund="ende")


def _bereite_vor(db, monkeypatch, attrappe, **einstellungen):
    """Schluessel hinterlegen, KI einschalten, Anbieter ersetzen."""
    from app.services import ai_provider_service, ai_settings_service as ais

    ais.setze_schluessel(db, "sk-ant-api03-TEST-1234567890")
    ais.speichere(db, {"aktiv": True, **einstellungen})
    monkeypatch.setitem(ai_provider_service.ANBIETER, "anthropic", attrappe)


def _nutzer(db, rolle="admin", name="log-tester"):
    from app.core.security import hash_password
    from app.models.user import User

    person = User(username=name, password_hash=hash_password("Egal12345!"),
                  role=rolle, is_active=True)
    db.add(person)
    db.commit()
    db.refresh(person)
    return person


def _testserver(db, *, slug: str, port: int, basis: str = "C:/nicht/vorhanden"):
    from app.models.server import Server

    server = Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                    base_path=basis, port=port, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


KOPFZEILE = "[10:00:00] [main/INFO]: Starting minecraft server version 26.3"
VERSIONSZEILE = "[10:00:01] [main/INFO]: This server is running Paper version 26.3"
MODZEILE = "[10:00:02] [main/INFO]: Loading 42 mods"


def _langes_log(fuellzeilen: int = 4000) -> list[str]:
    """Ein Log mit Kopf, einem Absturz in der MITTE und viel Rauschen dazwischen.

    Der Absturz liegt absichtlich in der Mitte: nur so zeigt sich, ob die Auswahl ihn
    findet, statt nur Anfang und Ende mitzunehmen.
    """
    zeilen = [KOPFZEILE, VERSIONSZEILE, MODZEILE]
    zeilen += [f"[10:01:{i % 60:02d}] [Server/INFO]: Rauschen {i}" for i in range(fuellzeilen)]
    zeilen += [
        "[11:00:00] [Server/ERROR]: Exception ticking entity",
        "java.lang.NullPointerException: Cannot invoke getOwner()",
    ]
    zeilen += [f"\tat net.example.haustier.Besitzer.pruefe(Besitzer.java:{100 + i})"
               for i in range(30)]
    zeilen += ["\t... 12 more"]
    zeilen += [f"[11:30:{i % 60:02d}] [Server/INFO]: Weiteres Rauschen {i}"
               for i in range(fuellzeilen)]
    zeilen += ["[12:00:00] [Server/INFO]: Stopping server"]
    return zeilen


# --------------------------------------------------------------------- Auswahl

def test_kurzes_log_geht_vollstaendig_raus():
    """Kuerzen, wo nichts zu kuerzen ist, wuerde nur Luecken in eine vollstaendige
    Information reissen."""
    from app.services import ai_log_service as als

    zeilen = [KOPFZEILE, "[10:00:05] [Server/INFO]: Done", "[10:00:06] ERROR: kaputt"]
    text, angaben = als.waehle_ausschnitt(zeilen)

    assert angaben["vollstaendig"] is True
    assert angaben["gesendet"] == 3
    assert text.splitlines() == zeilen
    assert "ausgelassen" not in text


def test_kopf_kommt_mit_auch_wenn_der_absturz_weit_dahinter_liegt():
    """Ohne Software, Version und Modanzahl kann niemand sagen, WELCHE Mod schuld ist -
    und genau das ist die Frage, die diese Funktion beantworten soll."""
    from app.services import ai_log_service as als

    text, angaben = als.waehle_ausschnitt(_langes_log())

    assert angaben["vollstaendig"] is False
    assert VERSIONSZEILE in text
    assert MODZEILE in text


def test_stacktrace_wird_nicht_in_der_mitte_abgeschnitten():
    """Ein halber Stacktrace ist wertlos: die ausloesende Klasse steht ganz unten."""
    from app.services import ai_log_service as als

    text, _ = als.waehle_ausschnitt(_langes_log())

    fehlend = [i for i in range(30)
               if f"Besitzer.java:{100 + i}" not in text]
    assert fehlend == [], f"Stacktrace-Zeilen fehlen: {fehlend}"
    assert "... 12 more" in text


def test_absturz_in_der_mitte_wird_gefunden():
    """Eine Auswahl, die nur Anfang und Ende nimmt, wuerde genau diesen Fall verpassen."""
    from app.services import ai_log_service as als

    text, angaben = als.waehle_ausschnitt(_langes_log())

    assert "Exception ticking entity" in text
    assert "NullPointerException" in text
    assert angaben["fehlerstellen"] >= 1


def test_rauschen_bleibt_draussen():
    """Wenn das Rauschen mitgeht, ist nichts gespart - der Sinn der Auswahl waere weg."""
    from app.services import ai_log_service as als

    zeilen = _langes_log()
    text, angaben = als.waehle_ausschnitt(zeilen)

    assert angaben["gesendet"] < angaben["gesamt"] / 4
    assert "Rauschen 2000" not in text
    assert "ausgelassen" in text   # die Luecke wird benannt, nicht verschwiegen


def test_zeilengrenze_wird_eingehalten():
    """Die Grenze ist der Kostendeckel der Funktion. Haelt sie nicht, haelt der Deckel
    nicht."""
    from app.services import ai_log_service as als

    # Ein Log, das fast nur aus Fehlerzeilen besteht: hier will die Auswahl viel zu viel.
    zeilen = [KOPFZEILE] + [f"[10:00:00] ERROR: Fehler Nummer {i}" for i in range(3000)]
    _text, angaben = als.waehle_ausschnitt(zeilen, max_zeilen=400)

    assert angaben["gesendet"] <= 400


def test_das_ende_kommt_immer_mit():
    """Dort steht, woran der Server gestorben ist."""
    from app.services import ai_log_service as als

    text, _ = als.waehle_ausschnitt(_langes_log())

    assert "Stopping server" in text


def test_kopf_ueberlebt_auch_bei_knapper_zeilengrenze():
    """Beim Kuerzen darf nicht der Kopf wegfallen.

    Vorher wurden die LETZTEN Zeilen der Auswahl behalten - bei einem Log voller Fehler
    war das die Mitte, und Software, Version und Modanzahl fielen weg. Die Antwort kann
    dann nicht mehr sagen, welche Mod schuld ist, und das ist die eigentliche Frage.
    """
    from app.services import ai_log_service as als

    zeilen = [KOPFZEILE, VERSIONSZEILE, MODZEILE]
    zeilen += [f"[10:00:00] ERROR: Fehler Nummer {i}" for i in range(3000)]
    zeilen += ["[12:00:00] [Server/INFO]: Stopping server"]

    text, angaben = als.waehle_ausschnitt(zeilen, max_zeilen=400)

    assert angaben["gesendet"] <= 400
    assert VERSIONSZEILE in text
    assert MODZEILE in text
    assert "Stopping server" in text


def test_leerzeilenstrecke_frisst_die_grenze_nicht():
    """Eine lange Leerstrecke hinter einem Fehler gehoert nicht zum Stacktrace.

    Zaehlt sie mit, ist die Zeilengrenze mit Leerzeilen aufgebraucht - bezahlt wird fuer
    nichts, und die aussagekraeftigen Zeilen bleiben draussen.
    """
    from app.services import ai_log_service as als

    zeilen = [KOPFZEILE, VERSIONSZEILE, MODZEILE]
    # Abstand zum Kopfbereich, damit dieser Test nur die Leerstrecke prueft und nicht
    # nebenbei den Kopf-Notfall.
    zeilen += [f"[10:00:{i % 60:02d}] [Server/INFO]: Start {i}" for i in range(200)]
    zeilen += ["[10:30:00] [Server/ERROR]: Exception ticking entity"]
    zeilen += [""] * 2000
    zeilen += [f"[11:00:00] [Server/INFO]: Rauschen {i}" for i in range(2000)]
    zeilen += ["[12:00:00] [Server/INFO]: Stopping server"]

    text, _angaben = als.waehle_ausschnitt(zeilen)

    assert VERSIONSZEILE in text
    assert "Stopping server" in text
    # Hoechstens ein paar Leerzeilen als Umfeld, nicht zweitausend.
    assert sum(1 for z in text.splitlines() if not z.strip()) < 20


def test_einzelne_leerzeile_beendet_den_stacktrace_nicht():
    """Manche Logger setzen eine Leerzeile zwischen Meldung und Stacktrace."""
    from app.services import ai_log_service as als

    zeilen = [KOPFZEILE, VERSIONSZEILE, MODZEILE]
    zeilen += [f"[10:00:{i % 60:02d}] [Server/INFO]: Rauschen {i}" for i in range(2000)]
    zeilen += ["[10:30:00] [Server/ERROR]: Exception ticking entity", ""]
    zeilen += [f"\tat net.example.Tier.pruefe(Tier.java:{200 + i})" for i in range(40)]
    zeilen += [f"[11:00:00] [Server/INFO]: Rauschen {i}" for i in range(2000)]
    zeilen += ["[12:00:00] [Server/INFO]: Stopping server"]

    text, _ = als.waehle_ausschnitt(zeilen)

    fehlend = [i for i in range(40) if f"Tier.java:{200 + i}" not in text]
    assert fehlend == [], f"Stacktrace hinter der Leerzeile fehlt: {fehlend}"


def test_harmlose_woerter_sind_keine_fehlerstellen():
    """"mirror", "terror" und ein Plugin namens "ErrorHandler" enthalten zwar Buchstaben
    von "error", sind aber keine Fehler. Zaehlen sie als Fehlerstelle, wird fuer Rauschen
    bezahlt."""
    from app.services import ai_log_service as als

    for harmlos in ("[10:00:00] [Server/INFO]: Mirror world geladen",
                    "[10:00:00] [Server/INFO]: ErrorHandler v1.2 aktiviert"):
        assert als._ist_fehler(harmlos) is False, harmlos

    for echt in ("[10:00:00] [Server/ERROR]: kaputt",
                 "java.lang.NullPointerException: Cannot invoke getOwner()",
                 "java.lang.OutOfMemoryError: Java heap space",
                 "Caused by: java.io.IOException"):
        assert als._ist_fehler(echt) is True, echt


# ------------------------------------------------------------------- Vorschau

def test_vorschau_kostet_nichts(client, monkeypatch):
    """Die Vorschau soll zeigen, was hinausgeht - sie darf dabei nichts hinausschicken."""
    from app.db.session import SessionLocal
    from app.services import ai_log_service as als


    attrappe = _Attrappe()
    with SessionLocal() as db:
        _bereite_vor(db, monkeypatch, attrappe)
        sicht = als.vorschau(_langes_log(), db)

    assert attrappe.gesehen == []
    assert sicht["auswahl"]["vollstaendig"] is False
    assert VERSIONSZEILE in sicht["text"]


def test_vorschau_meldet_entfernte_ips(client, monkeypatch):
    """Was die Saeuberung entfernt hat, muss vor dem Senden sichtbar sein."""
    from app.db.session import SessionLocal
    from app.services import ai_log_service as als


    with SessionLocal() as db:
        _bereite_vor(db, monkeypatch, _Attrappe())
        sicht = als.vorschau(
            [KOPFZEILE, "[10:00:03] [Server/INFO]: Spieler[/203.0.113.7:51234] logged in",
             "[10:00:04] ERROR: kaputt"], db)

    assert sicht["entfernte_ips"] >= 1
    assert "203.0.113.7" not in sicht["text"]


# -------------------------------------------------------------------- Analyse

def test_analyse_schickt_kopf_und_absturz(client, monkeypatch):
    """Der Test, auf den es ankommt: was der Anbieter SIEHT, muss beides enthalten."""
    from app.db.session import SessionLocal
    from app.services import ai_log_service as als


    attrappe = _Attrappe(text="Die Mod 'haustier' ruft getOwner() auf einem leeren Tier auf.")
    with SessionLocal() as db:
        person = _nutzer(db, name="loganalyse1")
        _bereite_vor(db, monkeypatch, attrappe)
        server = _testserver(db, slug="log-1", port=25611)

        ergebnis = als.analysiere(db, user=person, server=server, zeilen=_langes_log())

    gesendet = attrappe.gesehen[0].nachrichten[0]["inhalt"]
    assert VERSIONSZEILE in gesendet
    assert "Exception ticking entity" in gesendet
    assert "Besitzer.java:129" in gesendet
    assert "Rauschen 2000" not in gesendet
    # Der Serverbezug steht im Auftrag, damit die Antwort zur richtigen Version passt.
    assert "26.3" in attrappe.gesehen[0].auftrag
    assert ergebnis["text"].startswith("Die Mod")


def test_analyse_sagt_dass_gekuerzt_wurde(client, monkeypatch):
    """Eine Antwort auf einen gekuerzten Log kann die Ursache verfehlen. Wer das nicht
    weiss, haelt ein 'kein Fehler erkennbar' fuer eine Aussage."""
    from app.db.session import SessionLocal
    from app.services import ai_log_service as als


    attrappe = _Attrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="loganalyse2")
        _bereite_vor(db, monkeypatch, attrappe)
        server = _testserver(db, slug="log-2", port=25612)

        ergebnis = als.analysiere(db, user=person, server=server, zeilen=_langes_log())

    assert any("von" in h and "Zeilen" in h for h in ergebnis["hinweise"])
    # Auch die KI selbst wird darauf hingewiesen - sonst behauptet sie Vollstaendigkeit.
    assert "gekuerzt" in attrappe.gesehen[0].auftrag


def test_leeres_log_wird_ohne_aufruf_abgelehnt(client, monkeypatch):
    """Ein Aufruf, der nichts zu analysieren hat, kostet trotzdem Geld. Also gar nicht
    erst stellen."""
    from app.db.session import SessionLocal
    from app.services import ai_log_service as als, ai_service


    attrappe = _Attrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="loganalyse3")
        _bereite_vor(db, monkeypatch, attrappe)
        server = _testserver(db, slug="log-3", port=25613)

        with pytest.raises(ai_service.KiAbgelehnt, match="leer"):
            als.analysiere(db, user=person, server=server, zeilen=["", "   ", ""])

    assert attrappe.gesehen == []


# --------------------------------------------------------------------- Routen

def _login_super(client):
    antwort = client.post("/login", data={"username": "admin", "password": "admin123!"},
                          follow_redirects=False)
    assert antwort.status_code == 303


def _server_mit_log(tmp_path: Path, zeilen: list[str], *, slug: str, port: int):
    from app.db.session import SessionLocal

    basis = tmp_path / slug
    (basis / "logs").mkdir(parents=True, exist_ok=True)
    (basis / "logs" / "latest.log").write_text("\n".join(zeilen), encoding="utf-8")
    (basis / "server.properties").write_text(
        f"level-name=world\nserver-port={port}\n", encoding="utf-8")

    with SessionLocal() as db:
        return _testserver(db, slug=slug, port=port, basis=str(basis)).id


def test_logseite_zeigt_die_analyse(client, monkeypatch, tmp_path):
    _login_super(client)
    server_id = _server_mit_log(tmp_path, [KOPFZEILE], slug="route-a", port=25621)

    seite = client.get(f"/servers/{server_id}/logs")
    assert seite.status_code == 200
    assert "KI-Analyse" in seite.text
    assert "ki-vorschau-knopf" in seite.text
    assert "Was wird gesendet?" in seite.text


def test_vorschau_route_ruft_den_anbieter_nicht(client, monkeypatch, tmp_path):
    """Ein Klick auf 'Was wird gesendet?' darf kein Guthaben verbrauchen."""
    from app.db.session import SessionLocal


    attrappe = _Attrappe()
    with SessionLocal() as db:
        _bereite_vor(db, monkeypatch, attrappe)

    _login_super(client)
    server_id = _server_mit_log(tmp_path, _langes_log(500), slug="route-b", port=25622)

    antwort = client.get(f"/api/servers/{server_id}/logs/ki-vorschau?file=server:latest.log")
    assert antwort.status_code == 200
    daten = antwort.json()
    assert attrappe.gesehen == []
    assert daten["auswahl"]["gesamt"] > daten["auswahl"]["gesendet"]
    assert VERSIONSZEILE in daten["text"]


def test_analyse_route_liest_mehr_als_die_seite_anzeigt(client, monkeypatch, tmp_path):
    """Die Seite zeigt standardmaessig die letzten 800 Zeilen. Wuerde die Analyse dieselbe
    Grenze benutzen, fehlte bei jedem laengeren Log der Kopf - und damit die Antwort auf
    'welche Mod?'."""
    from app.db.session import SessionLocal


    attrappe = _Attrappe(text="Ursache gefunden.")
    with SessionLocal() as db:
        _bereite_vor(db, monkeypatch, attrappe)

    _login_super(client)
    server_id = _server_mit_log(tmp_path, _langes_log(), slug="route-c", port=25623)

    antwort = client.post(f"/api/servers/{server_id}/logs/ki-analyse",
                          data={"file": "server:latest.log", "frage": ""})
    assert antwort.status_code == 200, antwort.text
    gesendet = attrappe.gesehen[0].nachrichten[0]["inhalt"]
    assert VERSIONSZEILE in gesendet, "Der Kopf des Logs hat die Analyse nicht erreicht"
    assert antwort.json()["text"] == "Ursache gefunden."


def test_analyse_route_haelt_den_aufruf_fest(client, monkeypatch, tmp_path):
    """Wer wann welchen Log an die KI gegeben hat, muss nachlesbar sein - dort gehen
    Serverdaten nach draussen."""
    from sqlalchemy import select

    from app.db.session import SessionLocal
    from app.models.audit_log import AuditLog


    with SessionLocal() as db:
        _bereite_vor(db, monkeypatch, _Attrappe())

    _login_super(client)
    server_id = _server_mit_log(tmp_path, _langes_log(100), slug="route-d", port=25624)

    antwort = client.post(f"/api/servers/{server_id}/logs/ki-analyse",
                          data={"file": "server:latest.log"})
    assert antwort.status_code == 200

    with SessionLocal() as db:
        eintraege = list(db.scalars(
            select(AuditLog).where(AuditLog.action == "ai.log_analysis")))
    assert len(eintraege) == 1
    assert eintraege[0].server_id == server_id


def test_analyse_route_meldet_abgeschaltete_ki_verstaendlich(client, monkeypatch, tmp_path):
    """Ohne Schluessel oder mit abgeschalteter KI soll dort stehen, was zu tun ist - nicht
    'Fehler 500'."""
    _login_super(client)
    server_id = _server_mit_log(tmp_path, _langes_log(100), slug="route-e", port=25625)

    antwort = client.post(f"/api/servers/{server_id}/logs/ki-analyse",
                          data={"file": "server:latest.log"})
    assert antwort.status_code == 409
    daten = antwort.json()
    assert daten["abgelehnt"] is True
    assert "Einstellungen" in daten["detail"] or "abgeschaltet" in daten["detail"]


def test_unbekannte_logdatei_wird_abgewiesen(client, monkeypatch, tmp_path):
    """Der Schluessel kommt aus dem Browser. Ohne Pruefung waere das ein Weg, beliebige
    Dateien an die KI zu schicken."""
    _login_super(client)
    server_id = _server_mit_log(tmp_path, [KOPFZEILE], slug="route-f", port=25626)

    antwort = client.get(f"/api/servers/{server_id}/logs/ki-vorschau"
                         "?file=server:../../../server.properties")
    assert antwort.status_code == 400
    assert "nicht vorhanden" in antwort.json()["detail"]


def test_analyse_route_braucht_anmeldung(client, tmp_path):
    server_id = _server_mit_log(tmp_path, [KOPFZEILE], slug="route-g", port=25627)

    antwort = client.post(f"/api/servers/{server_id}/logs/ki-analyse",
                          data={"file": "server:latest.log"})
    assert antwort.status_code == 401


def test_view_only_sieht_die_analyse_nicht(client, tmp_path):
    """Ein Knopf, der fuer diese Rolle nur eine Absage liefern kann, soll nicht da sein."""
    from app.db.session import SessionLocal
    from app.models.server_permission import ServerPermission

    server_id = _server_mit_log(tmp_path, [KOPFZEILE], slug="route-h", port=25628)
    with SessionLocal() as db:
        person = _nutzer(db, rolle="view_only", name="nur-lesen")
        db.add(ServerPermission(user_id=person.id, server_id=server_id, can_view=True))
        db.commit()

    antwort = client.post("/login", data={"username": "nur-lesen",
                                          "password": "Egal12345!"},
                          follow_redirects=False)
    assert antwort.status_code == 303

    seite = client.get(f"/servers/{server_id}/logs")
    assert seite.status_code == 200
    assert "KI-Analyse" not in seite.text
    assert "ki-vorschau-knopf" not in seite.text


# ------------------------------------------------------- Crash-Reports und .gz

def test_crash_report_ist_waehlbar(client, tmp_path):
    """Eine Crash-Analyse ohne Zugriff auf den Crash-Report waere nur halb eine.

    Minecraft legt ihn neben logs/ ab (crash-reports/), nicht darin - er war deshalb in
    der Dateiliste gar nicht aufgefuehrt.
    """
    from app.db.session import SessionLocal
    from app.services.log_service import list_log_files, read_log_file

    basis = tmp_path / "crash-srv"
    (basis / "logs").mkdir(parents=True)
    (basis / "logs" / "latest.log").write_text("[10:00:00] Server crashed\n", encoding="utf-8")
    (basis / "crash-reports").mkdir()
    (basis / "crash-reports" / "crash-2026-10-27_11.00.00-server.txt").write_text(
        "---- Minecraft Crash Report ----\n"
        "java.lang.NullPointerException: Cannot invoke getOwner()\n"
        "\tat net.example.haustier.Besitzer.pruefe(Besitzer.java:120)\n",
        encoding="utf-8")

    with SessionLocal() as db:
        server = _testserver(db, slug="crash-srv", port=25631, basis=str(basis))
        eintraege = list_log_files(server)
        schluessel = [e["key"] for e in eintraege]
        assert "crash:crash-2026-10-27_11.00.00-server.txt" in schluessel

        zeilen, bezeichnung = read_log_file(
            server, "crash:crash-2026-10-27_11.00.00-server.txt")

    assert "Crash-Report" in bezeichnung
    assert any("NullPointerException" in z for z in zeilen)


def test_gepacktes_log_wird_ausgepackt(client, tmp_path):
    """Minecraft packt alte Logs beim Rotieren zu .log.gz. Ungepackt gelesen kommt
    Binaermuell heraus - und eine KI-Analyse wuerde fuer diesen Muell bezahlen."""
    import gzip

    from app.db.session import SessionLocal
    from app.services.log_service import read_log_file

    basis = tmp_path / "gz-srv"
    (basis / "logs").mkdir(parents=True)
    inhalt = (KOPFZEILE + "\n" + VERSIONSZEILE + "\n"
              "[10:30:00] [Server/ERROR]: Exception ticking entity\n")
    with gzip.open(basis / "logs" / "2026-10-26-1.log.gz", "wt", encoding="utf-8") as f:
        f.write(inhalt)

    with SessionLocal() as db:
        server = _testserver(db, slug="gz-srv", port=25632, basis=str(basis))
        zeilen, _ = read_log_file(server, "server:2026-10-26-1.log.gz")

    assert VERSIONSZEILE in zeilen
    assert any("Exception ticking entity" in z for z in zeilen)


def test_crash_report_geht_durch_die_analyse(client, monkeypatch, tmp_path):
    """Der Weg von Anfang bis Ende: Crash-Report auswaehlen, analysieren lassen, und was
    beim Anbieter ankommt, enthaelt den Stacktrace."""
    from app.db.session import SessionLocal

    basis = tmp_path / "crash-route"
    (basis / "logs").mkdir(parents=True)
    (basis / "logs" / "latest.log").write_text("[10:00:00] Server crashed\n",
                                               encoding="utf-8")
    (basis / "crash-reports").mkdir()
    bericht = ["---- Minecraft Crash Report ----",
               "// Shall we play a game?",
               "Time: 2026-10-27 11:00:00",
               "Description: Exception ticking entity",
               "java.lang.NullPointerException: Cannot invoke getOwner()"]
    bericht += [f"\tat net.example.haustier.Besitzer.pruefe(Besitzer.java:{120 + i})"
                for i in range(25)]
    bericht += ["-- System Details --", "\tOperating System: Windows 11",
                "\tMemory: 4096 MB", "\tLoading 42 mods"]
    (basis / "crash-reports" / "crash-neu.txt").write_text("\n".join(bericht),
                                                           encoding="utf-8")

    attrappe = _Attrappe(text="Das Plugin 'haustier' ruft getOwner() auf null auf.")
    with SessionLocal() as db:
        _bereite_vor(db, monkeypatch, attrappe)
        server = _testserver(db, slug="crash-route", port=25633, basis=str(basis))
        server_id = server.id

    _login_super(client)
    antwort = client.post(f"/api/servers/{server_id}/logs/ki-analyse",
                          data={"file": "crash:crash-neu.txt"})
    assert antwort.status_code == 200, antwort.text

    gesendet = attrappe.gesehen[0].nachrichten[0]["inhalt"]
    assert "NullPointerException" in gesendet
    assert "Besitzer.java:144" in gesendet
    assert antwort.json()["text"].startswith("Das Plugin")
