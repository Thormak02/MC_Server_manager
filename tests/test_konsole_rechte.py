"""Befehle, die Rechte vergeben oder Zugangssperren aufheben, duerfen nur Admins senden.

`op`, `deop`, `pardon` und `whitelist off` sind keine Steuerung eines Servers, sondern
eine Entscheidung darueber, WER auf ihm etwas darf. Ein Moderator darf den Server starten
und stoppen, aber nicht bestimmen, wer Operator wird oder wer nach einem Bann wieder
hereinkommt.

Geprueft wird in ``process_service.send_console_command``, also an der Stelle, durch die
JEDER Weg laeuft: die Konsolenseite, der Zeitplan und der KI-Agent. Haette ich die Regel
nur im Agenten eingebaut, waere die Konsolenseite der Weg daran vorbei gewesen.
"""
import pytest


@pytest.mark.parametrize("befehl", [
    "op Thormak02",
    "/op Thormak02",
    "minecraft:op Thormak02",          # Namensraum-Praefix wirkt auf Paper
    "OP Thormak02",                    # Gross-/Kleinschreibung
    "deop Thormak02",
    "pardon Thormak02",
    "pardon-ip 203.0.113.7",           # dieselbe Tat fuer IP-Sperren
    "whitelist off",
    "/whitelist off",
    "execute as @a at @s run op Thormak02",   # ueber execute versteckt
])
def test_rechtevergebende_befehle_brauchen_admin(befehl):
    from app.services.process_service import braucht_admin

    assert braucht_admin(befehl), f"{befehl!r} muesste Admin verlangen"


@pytest.mark.parametrize("befehl", [
    "save-all",
    "stop",
    "whitelist add Thormak02",
    "whitelist on",
    "whitelist list",
    "whitelist reload",
    "kick Thormak02 Pause",
    "ban Thormak02 Grund",
    "time set day",
    "say hallo op leute",              # das Wort in einer Nachricht ist kein Befehl
    "tell Thormak02 pardon mir",
    "",
])
def test_normale_befehle_bleiben_moderatorenarbeit(befehl):
    """Ueberzusperren waere auch ein Fehler: ein Moderator muss arbeiten koennen."""
    from app.services.process_service import braucht_admin

    assert not braucht_admin(befehl), f"{befehl!r} wurde faelschlich gesperrt"


def _nutzer(db, rolle, name):
    from app.core.security import hash_password
    from app.models.user import User

    person = User(username=name, password_hash=hash_password("Egal12345!"),
                  role=rolle, is_active=True)
    db.add(person)
    db.commit()
    db.refresh(person)
    return person


def _server(db, tmp_path, *, slug, port):
    from app.models.server import Server

    basis = tmp_path / slug
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text(
        f"level-name=world\nserver-port={port}\n", encoding="utf-8")
    server = Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                    base_path=str(basis), port=port, status="running",
                    start_mode="command", start_command="java -jar server.jar")
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


def test_moderator_wird_abgewiesen_bevor_etwas_gesendet_wird(client, tmp_path):
    """Der Befehl darf den Serverprozess nicht erreichen. Der Test laeuft ohne laufenden
    Prozess: kaeme die Absage ERST danach, waere die Meldung "Server ist nicht aktiv" -
    die Pruefung muss also vorher greifen."""
    from app.db.session import SessionLocal
    from app.services.process_service import send_console_command

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "mod-konsole")
        server = _server(db, tmp_path, slug="konsole-mod", port=25801)

        ok, meldung = send_console_command(db, server, "op Thormak02", person.id)

    assert ok is False
    assert "Admin-Rechte" in meldung
    assert "nicht aktiv" not in meldung, "Die Absage kam erst beim Senden"


def test_abweisung_wird_protokolliert(client, tmp_path):
    """Ein Versuch, Rechte zu vergeben, gehoert ins Protokoll - auch der gescheiterte."""
    from sqlalchemy import select

    from app.db.session import SessionLocal
    from app.models.audit_log import AuditLog
    from app.services.process_service import send_console_command

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "mod-protokoll")
        server = _server(db, tmp_path, slug="konsole-prot", port=25802)
        send_console_command(db, server, "whitelist off", person.id)

        eintraege = list(db.scalars(select(AuditLog).where(
            AuditLog.action == "server.console_command_denied")))

    assert len(eintraege) == 1
    assert "whitelist off" in (eintraege[0].details or "")


def test_admin_darf_und_scheitert_erst_am_prozess(client, tmp_path):
    """Fuer einen Admin greift die Sperre nicht - er landet an der normalen Pruefung,
    ob der Server ueberhaupt laeuft."""
    from app.db.session import SessionLocal
    from app.services.process_service import send_console_command

    with SessionLocal() as db:
        for rolle, name, port in (("admin", "admin-konsole", 25803),
                                  ("super_admin", "super-konsole", 25804)):
            person = _nutzer(db, rolle, name)
            server = _server(db, tmp_path, slug=f"konsole-{rolle}", port=port)

            ok, meldung = send_console_command(db, server, "op Thormak02", person.id)

            assert ok is False
            assert "Admin-Rechte" not in meldung, f"{rolle} wurde gesperrt"
            assert "nicht aktiv" in meldung


def test_manager_selbst_wird_nicht_gesperrt(client, tmp_path):
    """Zeitplan und Automatik senden ohne Nutzer (user_id None) - die duerfen."""
    from app.db.session import SessionLocal
    from app.services.process_service import send_console_command

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="konsole-system", port=25805)

        ok, meldung = send_console_command(db, server, "whitelist off", None)

    assert ok is False
    assert "Admin-Rechte" not in meldung
    assert "nicht aktiv" in meldung


# ------------------------------------------------- Der Agent sagt es vorher

def test_agent_legt_moderator_den_befehl_nicht_zur_bestaetigung_vor(client, tmp_path):
    """Ein Vorschlag, der bei der Bestaetigung scheitert, ist eine Zumutung - der
    Assistent soll gleich sagen, dass es nicht geht, und warum."""
    from app.db.session import SessionLocal
    from app.models.server_permission import ServerPermission
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "mod-agent")
        server = _server(db, tmp_path, slug="agent-mod", port=25806)
        db.add(ServerPermission(user_id=person.id, server_id=server.id,
                                can_view=True, can_manage=True))
        db.commit()

        vorschlag = aas._baue_vorschlag(db, person, {
            "name": "konsolenbefehl", "id": "tu_1",
            "eingabe": {"server_id": server.id, "befehl": "op Thormak02"}})

    assert vorschlag["moeglich"] is False
    assert "Admin-Rechte" in vorschlag["hindernis"]


def test_agent_legt_admin_den_befehl_vor(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        person = _nutzer(db, "super_admin", "super-agent")
        server = _server(db, tmp_path, slug="agent-super", port=25807)

        vorschlag = aas._baue_vorschlag(db, person, {
            "name": "konsolenbefehl", "id": "tu_1",
            "eingabe": {"server_id": server.id, "befehl": "op Thormak02"}})

    assert vorschlag["moeglich"] is True
    assert "op Thormak02" in vorschlag["beschreibung"]


def test_agent_fuehrt_fuer_moderator_nicht_aus(client, tmp_path):
    """Auch wenn der Vorschlag im Browser manipuliert wird."""
    from app.db.session import SessionLocal
    from app.models.server_permission import ServerPermission
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "mod-agent-exec")
        server = _server(db, tmp_path, slug="agent-mod-exec", port=25808)
        db.add(ServerPermission(user_id=person.id, server_id=server.id,
                                can_view=True, can_manage=True))
        db.commit()

        with pytest.raises(aas.AgentFehler, match="Admin-Rechte"):
            aas.fuehre_aus(db, user=person, werkzeug_name="konsolenbefehl",
                           parameter={"server_id": server.id, "befehl": "deop X"})


def test_agent_erlaubt_moderatoren_normale_befehle(client, tmp_path):
    """Die Sperre darf die uebrige Arbeit nicht behindern."""
    from app.db.session import SessionLocal
    from app.models.server_permission import ServerPermission
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "mod-normal")
        server = _server(db, tmp_path, slug="agent-normal", port=25809)
        db.add(ServerPermission(user_id=person.id, server_id=server.id,
                                can_view=True, can_manage=True))
        db.commit()

        vorschlag = aas._baue_vorschlag(db, person, {
            "name": "konsolenbefehl", "id": "tu_1",
            "eingabe": {"server_id": server.id, "befehl": "save-all"}})

    assert vorschlag["moeglich"] is True


# ------------------------------------------ Geld und Besitz: auch kein Admin

@pytest.mark.parametrize("befehl", [
    "freimarken geld Thormak02 50000",
    "/freimarken geld Thormak02 -50000",
    "FREIMARKEN GELD Thormak02 1",              # Gross-/Kleinschreibung
    "minecraft:freimarken geld Thormak02 1",    # Namensraum-Praefix
    "fm geld Thormak02 1",                      # DAS KUERZEL - sonst ist die Regel offen
    "/fm geld Thormak02 1",
    # GOLDGELD [23.11.2026]: derselbe Zweig in der groben Waehrung. Eine Goldmark ist
    # achthundert Mark - ein Loch hier ist kein kleineres als bei "geld", sondern ein
    # achthundertmal groesseres. Darum alle drei Wege: Befehl, Kuerzel, execute.
    "freimarken goldgeld Thormak02 1",
    "fm goldgeld Thormak02 1",
    "execute as @a run freimarken goldgeld Thormak02 1",
    "freimarken besitz uebertragen 3 4 Thormak02",
    "freimarken besitz freigeben 3 4 bestaetigen",
    "fm besitz uebertragen 3 4 Thormak02",
    "execute as @a run freimarken geld Thormak02 1",   # ueber execute versteckt
])
def test_geld_und_besitz_brauchen_super_admin(befehl):
    """Plan 12a.4 stellt Geld- und Besitzkorrekturen auf Super-Admin.

    Gefragt wird nach der STUFE und nicht nur danach, ob eine Sperre greift: Stuende hier
    nur ``braucht_admin(befehl)``, waere die Probe auch gruen, wenn der Befehl in
    RECHTE_BEFEHLE gelandet waere - und dann duerfte ein Admin ihn senden.
    """
    from app.services.process_service import STUFE_SUPER_ADMIN, stufe_fuer_befehl

    stufe, grund = stufe_fuer_befehl(befehl)
    assert stufe == STUFE_SUPER_ADMIN, f"{befehl!r} muesste Super-Admin verlangen"
    assert "Super-Admin" in grund, "der Grund muss die Stufe nennen"


@pytest.mark.parametrize("befehl", [
    "freimarken welt",
    "freimarken neuladen",
    "freimarken preis 3",
    "freimarken parzelle 3 4",
    "freimarken spielzeit Thormak02",
    "fm welt",
    "geld Thormak02 1",            # ohne den Befehlsbaum davor ist es kein Zweig
    "goldgeld Thormak02 1",        # dasselbe fuer die Goldmark
    "freimarken goldpreis 3",      # faengt ein "sperre alles mit gold darin"
    "besitz uebertragen 3 4 Anna",
    "say freimarken geld ist toll",
])
def test_die_anderen_freimarken_zweige_bleiben_frei(befehl):
    """Ueberzusperren waere auch ein Fehler.

    ``/freimarken welt`` und ``neuladen`` sind Verwaltung und gehen jeden an, der den
    Server steuern darf. Ohne diese Richtung waere ein "sperre alles, was mit freimarken
    anfaengt" ebenfalls gruen.
    """
    from app.services.process_service import stufe_fuer_befehl

    stufe, _ = stufe_fuer_befehl(befehl)
    assert stufe == "", f"{befehl!r} wurde faelschlich gesperrt"


def test_op_bleibt_bei_der_schwaecheren_stufe():
    """Die Gegenrichtung: op wird NICHT auf Super-Admin gehoben.

    Ohne diesen Satz waere ein "alles ist Super-Admin-Sache" gruen - und dann koennte
    kein Admin mehr jemanden oppen, obwohl er es darf.
    """
    from app.services.process_service import (STUFE_ADMIN, STUFE_SUPER_ADMIN,
                                              stufe_fuer_befehl)

    assert stufe_fuer_befehl("op Thormak02")[0] == STUFE_ADMIN
    assert stufe_fuer_befehl("whitelist off")[0] == STUFE_ADMIN
    assert STUFE_ADMIN != STUFE_SUPER_ADMIN


def test_admin_darf_kein_geld_buchen(client, tmp_path):
    """Der Fund, um den es geht: Vorher fiel dieser Befehl durch jede Sperre.

    Geprueft wird am DURCHSETZEN und nicht nur an der Liste: Eine Liste, die niemand
    liest, ist eine Absichtserklaerung - genau das war der Abschnitt
    nicht-im-dashboard im Freimarken-Katalog.
    """
    from app.db.session import SessionLocal
    from app.services.process_service import send_console_command

    with SessionLocal() as db:
        person = _nutzer(db, "admin", "admin-geld")
        server = _server(db, tmp_path, slug="konsole-geld", port=25811)

        ok, meldung = send_console_command(db, server,
                                           "freimarken geld Thormak02 50000",
                                           person.id)

    assert ok is False
    assert "Super-Admin-Rechte" in meldung
    assert "nicht aktiv" not in meldung, "Die Absage kam erst beim Senden"


def test_super_admin_darf_und_scheitert_erst_am_prozess(client, tmp_path):
    """Fuer den Super-Admin greift die Sperre nicht - er landet an der normalen Frage,
    ob der Server ueberhaupt laeuft. Ohne diesen Satz waere ein "immer verweigern"
    gruen."""
    from app.db.session import SessionLocal
    from app.services.process_service import send_console_command

    with SessionLocal() as db:
        person = _nutzer(db, "super_admin", "super-geld")
        server = _server(db, tmp_path, slug="konsole-geld-super", port=25812)

        ok, meldung = send_console_command(db, server,
                                           "freimarken geld Thormak02 50000",
                                           person.id)

    assert ok is False
    assert "Super-Admin-Rechte" not in meldung, "der Super-Admin wurde gesperrt"


def test_abgewiesene_geldbuchung_steht_im_protokoll(client, tmp_path):
    """Ein Versuch, Geld zu buchen, gehoert ins Protokoll - auch der gescheiterte."""
    from sqlalchemy import select

    from app.db.session import SessionLocal
    from app.models.audit_log import AuditLog
    from app.services.process_service import send_console_command

    with SessionLocal() as db:
        person = _nutzer(db, "admin", "admin-geld-buch")
        server = _server(db, tmp_path, slug="konsole-geld-buch", port=25813)
        send_console_command(db, server, "fm geld Thormak02 50000", person.id)

        eintraege = list(db.scalars(select(AuditLog).where(
            AuditLog.action == "server.console_command_denied",
            AuditLog.server_id == server.id)))

    assert len(eintraege) == 1
    assert "geld" in (eintraege[0].details or "")


# ---------------------------------------- Ein Befehl ist eine Zeile

@pytest.mark.parametrize("befehl", [
    "list\nfreimarken geld Thormak02 1000000",
    "list\nop Thormak02",
    "say hallo\r\nop Thormak02",
    "time set day\nwhitelist off",
    "list\n\nop Thormak02",
    # EIN WAGENRUECKLAUF ALLEIN, ohne Zeilenvorschub [Fund 22.11.2026].
    #
    # Jeder Fall darueber traegt ein \n, also blieb eine Pruefung auf
    # \n allein gruen - die Einspritzung hat es gefangen. Javas
    # BufferedReader.readLine beendet eine Zeile aber auch bei \r
    # allein, und der Server liest seine Konsole damit: Hier stehen also
    # ebenfalls zwei Befehle.
    "list\rop Thormak02",
])
def test_ein_zweiter_befehl_hinter_einem_umbruch_wird_abgewiesen(client, tmp_path,
                                                                 befehl):
    """Der Server liest seine Konsole ZEILENWEISE.

    ``_befehl_wortfolge`` zerlegt mit ``split()`` ueber jeden Leerraum, also auch ueber
    einen Umbruch, und ``stufe_fuer_befehl`` beurteilt davon nur die ersten zwei Woerter.
    Ein harmloses erstes Wort trug damit einen beliebigen zweiten Befehl an den Server -
    das traf die Super-Admin-Stufe UND die aeltere Admin-Sperre.

    Geprueft wird am Text der Absage und nicht nur daran, dass sie kommt: Ein
    ``return False, "Server ist nicht aktiv."`` wuerde sonst als Riegel gelten.
    """
    from app.db.session import SessionLocal
    from app.services.process_service import send_console_command

    with SessionLocal() as db:
        person = _nutzer(db, "super_admin", "umbruch-" + str(abs(hash(befehl)) % 10000))
        server = _server(db, tmp_path, slug="umbruch-" + str(abs(hash(befehl)) % 10000),
                         port=25900 + abs(hash(befehl)) % 90)

        ok, meldung = send_console_command(db, server, befehl, person.id)

    # SUPER-ADMIN und trotzdem abgewiesen: Es geht nicht um die Rolle, sondern darum,
    # dass eine Eingabe fuer EINEN Befehl keine zwei senden darf.
    assert ok is False
    assert "eine Zeile" in meldung
    assert "nicht aktiv" not in meldung, "Die Absage kam erst beim Senden"


def test_der_abgewiesene_umbruch_steht_im_protokoll(client, tmp_path):
    """Wer das probiert, probiert es nicht aus Versehen."""
    from sqlalchemy import select

    from app.db.session import SessionLocal
    from app.models.audit_log import AuditLog
    from app.services.process_service import send_console_command

    with SessionLocal() as db:
        person = _nutzer(db, "moderator", "mod-umbruch")
        server = _server(db, tmp_path, slug="umbruch-buch", port=25991)
        send_console_command(db, server, "list\nop Thormak02", person.id)

        eintraege = list(db.scalars(select(AuditLog).where(
            AuditLog.action == "server.console_command_denied",
            AuditLog.server_id == server.id)))

    assert len(eintraege) == 1
    assert "op Thormak02" in (eintraege[0].details or "")


def test_ein_einzeiliger_befehl_geht_weiter(client, tmp_path):
    """Die Gegenrichtung: Ohne Umbruch laeuft alles wie zuvor.

    Ohne diesen Satz waere ein "verweigere alles" gruen - und dann koennte niemand mehr
    einen Konsolenbefehl senden.
    """
    from app.db.session import SessionLocal
    from app.services.process_service import send_console_command

    with SessionLocal() as db:
        person = _nutzer(db, "super_admin", "super-einzeilig")
        server = _server(db, tmp_path, slug="einzeilig", port=25992)

        ok, meldung = send_console_command(db, server, "list", person.id)

    assert ok is False
    assert "eine Zeile" not in meldung, "ein einzeiliger Befehl darf nicht hier scheitern"
    assert "nicht aktiv" in meldung, "er scheitert erst am fehlenden Prozess"


def test_die_neustartwarnung_wird_geglaettet(monkeypatch, client, tmp_path):
    """Die Warnung laeuft mit user_id=None und system_fallback=True - ohne Rollenpruefung.

    Ein Umbruch darin waere ein Befehl mit Konsolenrechten, den niemand geprueft hat, und
    bei 300 Sekunden Vorlauf zehnmal je Neustart. Geglaettet und nicht abgewiesen: Eine
    Warnung ist eine Chatzeile und soll ankommen.
    """
    from app.db.session import SessionLocal
    from app.services import process_service as ps

    gesendet: list[str] = []

    def gefaelscht(db, server, command, initiated_by_user_id, **rest):
        gesendet.append(command)
        return True, "Befehl gesendet."

    monkeypatch.setattr(ps, "send_console_command", gefaelscht)

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="warnung", port=25993)

    ps._send_server_message(server, "Neustart\nfm geld Thormak02 1000000")

    assert len(gesendet) == 1
    assert "\n" not in gesendet[0], "die Warnung darf keinen Umbruch tragen"
    assert gesendet[0] == "say Neustart fm geld Thormak02 1000000", \
        "geglaettet zu EINER Zeile - der Text bleibt sichtbar, wirkt aber als Chat"
