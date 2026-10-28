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
