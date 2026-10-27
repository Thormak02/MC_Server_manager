"""Freier Agent: fragen, nachsehen, Aktionen vorschlagen - ausfuehren tut der Mensch.

Der Agent darf im Manager nachsehen (welche Server gibt es, was steht in der Config, was
sagt der Log) und daraufhin eine Aktion VORSCHLAGEN. Ausgefuehrt wird sie erst, wenn der
Nutzer sie bestaetigt - und dann ueber dieselben Dienstfunktionen mit derselben
Rechtepruefung wie bei einem Klick in der Oberflaeche.

Die wichtigste Zeile dieses Moduls steht in ``fuehre_aus``: der Vorschlag aus dem Browser
wird NICHT geglaubt. Werkzeugname, Server und jeder Parameter werden neu geprueft, und das
Recht wird neu gegen die Datenbank gestellt. Ein manipulierter Vorschlag kann deshalb
nichts tun, was der angemeldete Nutzer nicht auch per Klick koennte. Das war die
Bedingung: "rechte duerfen dadurch nicht umgehbar sein".

Aufbau:

* **Lesende Werkzeuge** laufen ohne Rueckfrage, aber nur auf Servern, die der Nutzer
  ohnehin sehen darf. Ohne sie waere der Agent blind und muesste raten; mit ihnen kostet
  eine Frage mehrere Anfragen, deshalb ist die Zahl der Schritte begrenzt.
* **Handelnde Werkzeuge** beenden den Lauf. Der Agent liefert dann einen Vorschlag mit
  einem Satz, der in der Oberflaeche steht, bevor der Nutzer bestaetigt - formuliert vom
  Manager, nicht vom Modell, damit die Beschreibung nicht von der Tat abweichen kann.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Callable

from sqlalchemy.orm import Session

from app.core.constants import UserRole
from app.models.server import Server
from app.models.user import User
from app.services import ai_service

# Wie viele KI-Anfragen ein einzelner Auftrag hoechstens kosten darf. Jede Schleifenrunde
# ist eine eigene Anfrage und damit echtes Geld; ohne Deckel koennte ein Agent sich im
# Nachsehen verlieren.
MAX_SCHRITTE = 5

# Zeichen, die ein Werkzeugergebnis hoechstens zurueckgibt. Eine 2-MB-Config waere sonst
# eine Anfrage, die mehr kostet als die Antwort wert ist.
MAX_ERGEBNIS = 12_000


@dataclass
class Werkzeug:
    """Ein Werkzeug des Agenten samt allem, was der Manager dazu wissen muss."""

    name: str
    beschreibung: str
    felder: dict
    pflicht: list[str]
    lesend: bool
    recht: str
    """"ansehen", "steuern", "dateien" oder "super_admin" - bestimmt die Pruefung."""
    gefahr: str = "niedrig"
    braucht_server: bool = True
    ausfuehren: Callable[..., dict] | None = None
    beschreibe: Callable[..., str] | None = None
    nachwirkung: str = ""
    """Ein Satz fuer die Bestaetigung: was ist danach anders und was laesst sich nicht
    zurueckdrehen."""

    def schema(self) -> dict:
        felder = dict(self.felder)
        pflicht = list(self.pflicht)
        if self.braucht_server:
            felder = {"server_id": {"type": "integer",
                                    "description": "ID des Servers aus der Lage-Uebersicht"},
                      **felder}
            pflicht = ["server_id", *pflicht]
        return {"name": self.name, "description": self.beschreibung,
                "input_schema": {"type": "object", "properties": felder,
                                 "required": pflicht}}


# Platzhalter fuer einen Werkzeugnamen, den es nicht gibt: lesend=False, damit ein
# erfundener Name im Zweifel vorgelegt und nicht ausgefuehrt wird.
_UNBEKANNT = Werkzeug(name="", beschreibung="", felder={}, pflicht=[], lesend=False,
                      recht="super_admin")


class AgentFehler(Exception):
    """Der Auftrag konnte nicht ausgefuehrt werden. Die Meldung ist fuer den Nutzer."""


# ------------------------------------------------------------ Rechte und Server

def _server_holen(db: Session, user: User, server_id: Any) -> Server:
    from app.services.server_service import can_view_server, get_server_by_id

    try:
        kennung = int(server_id)
    except (TypeError, ValueError):
        raise AgentFehler("Es wurde keine gueltige Server-ID angegeben.") from None
    server = get_server_by_id(db, kennung)
    if server is None:
        raise AgentFehler(f"Es gibt keinen Server mit der ID {kennung}.")
    if not can_view_server(db, user, server):
        # Dieselbe Meldung wie fuer "gibt es nicht": sonst verraet der Agent, dass es
        # Server gibt, die dieser Nutzer nicht sehen darf.
        raise AgentFehler(f"Es gibt keinen Server mit der ID {kennung}.")
    return server


def _recht_pruefen(db: Session, user: User, server: Server | None, recht: str) -> None:
    from app.services.server_service import (
        can_control_server,
        can_edit_server_files,
        can_view_server,
    )

    if recht == "super_admin":
        if (getattr(user, "role", "") or "") != UserRole.SUPER_ADMIN.value:
            raise AgentFehler("Dafuer braucht es Super-Admin-Rechte.")
        return
    if server is None:
        return
    if recht == "ansehen" and not can_view_server(db, user, server):
        raise AgentFehler(f"Du darfst '{server.name}' nicht ansehen.")
    if recht == "steuern" and not can_control_server(db, user, server):
        raise AgentFehler(f"Du darfst '{server.name}' nicht steuern.")
    if recht == "dateien" and not can_edit_server_files(db, user, server):
        raise AgentFehler(f"Du darfst die Dateien von '{server.name}' nicht aendern.")


# ------------------------------------------------------------------------- Lage

def lage(db: Session, user: User) -> str:
    """Eine kurze Uebersicht, die bei JEDER Anfrage mitgeht.

    Ohne sie muesste der Agent als ersten Schritt immer erst die Serverliste abfragen -
    eine zusaetzliche Anfrage fuer eine Information, die zwanzig Zeilen lang ist. So ist
    die haeufigste Frage ("laeuft alles?") in einem Zug beantwortet.
    """
    from app.services.process_service import get_player_counts
    from app.services.resource_service import get_host_resources
    from app.services.server_service import list_servers_for_user

    zeilen: list[str] = []
    rechner = get_host_resources()
    if rechner.get("memory_total_mb"):
        teile_rechner = [f"{int(rechner['memory_total_mb'])} MB RAM"]
        if rechner.get("cpu_logical"):
            teile_rechner.append(f"{rechner['cpu_logical']} logische Kerne")
        if rechner.get("memory_percent") is not None:
            teile_rechner.append(f"aktuell {int(rechner['memory_percent'])}% belegt")
        zeilen.append("Rechner: " + ", ".join(teile_rechner))
    for server in list_servers_for_user(db, user):
        try:
            jetzt, maximal = get_player_counts(server)
        except Exception:  # noqa: BLE001
            jetzt, maximal = None, None
        teile = [f"ID {server.id}: '{server.name}'",
                 f"{server.server_type} {server.mc_version}",
                 f"Status {server.status}"]
        if server.memory_max_mb:
            teile.append(f"{server.memory_max_mb} MB")
        if jetzt is not None:
            teile.append(f"{jetzt}/{maximal or '?'} Spieler")
        if server.sleep_enabled:
            teile.append("Sleep-Modus")
        zeilen.append(" | ".join(teile))

    if not zeilen:
        return "Es gibt keine Server, die du sehen darfst."
    return "\n".join(zeilen)


# ------------------------------------------------------------ Lesende Werkzeuge

def _lies_server_details(db: Session, user: User, server: Server, _p: dict) -> dict:
    from app.services.content_service import list_installed_content
    from app.services.process_service import get_player_counts, get_process_resource_usage

    try:
        jetzt, maximal = get_player_counts(server)
    except Exception:  # noqa: BLE001
        jetzt, maximal = None, None
    inhalte: dict[str, int] = {}
    try:
        for zeile in list_installed_content(db, server):
            art = (zeile.content_type or "sonstiges").lower()
            inhalte[art] = inhalte.get(art, 0) + 1
    except Exception:  # noqa: BLE001
        pass
    return {
        "id": server.id, "name": server.name, "typ": server.server_type,
        "version": server.mc_version, "loader_version": server.loader_version,
        "status": server.status, "port": server.port,
        "speicher_min_mb": server.memory_min_mb, "speicher_max_mb": server.memory_max_mb,
        "spieler": jetzt, "spieler_maximal": maximal,
        "auto_restart": server.auto_restart,
        "sleep_an": server.sleep_enabled, "gateway_an": server.gateway_enabled,
        "verbrauch": get_process_resource_usage(server.id),
        "inhalte": inhalte,
    }


def _lies_datei(db: Session, user: User, server: Server, p: dict) -> dict:
    from app.services.file_service import read_text_file

    pfad = str(p.get("pfad") or "").strip()
    if not pfad:
        raise AgentFehler("Es wurde kein Dateipfad angegeben.")
    try:
        # read_text_file prueft den Pfad gegen den Serverordner - der Agent kann damit
        # nicht aus dem Serverordner herauslesen.
        datei = read_text_file(server, pfad)
    except ValueError as exc:
        raise AgentFehler(str(exc)) from exc
    inhalt = datei.content or ""
    gekuerzt = len(inhalt) > MAX_ERGEBNIS
    return {"pfad": datei.relative_path, "inhalt": inhalt[:MAX_ERGEBNIS],
            "gekuerzt": gekuerzt}


def _lies_dateiliste(db: Session, user: User, server: Server, _p: dict) -> dict:
    from app.services.file_service import list_files

    try:
        dateien = list_files(server)
    except ValueError as exc:
        raise AgentFehler(str(exc)) from exc
    return {"dateien": list(dateien)[:200]}


def _lies_logfehler(db: Session, user: User, server: Server, p: dict) -> dict:
    from app.services.ai_log_service import waehle_ausschnitt
    from app.services.log_service import (
        get_console_lines,
        lies_fuer_analyse,
        list_log_files,
    )

    schluessel = str(p.get("logdatei") or "").strip()
    if schluessel:
        vorhanden = {str(e.get("key")) for e in list_log_files(server)}
        if schluessel not in vorhanden:
            raise AgentFehler("Diese Logdatei gibt es nicht.")
        # lies_fuer_analyse statt read_log_file: letzteres liefert nur den Schwanz, und
        # ohne den Kopf (Software, Version, geladene Mods) kann der Agent nicht sagen,
        # WELCHE Mod abgestuerzt ist.
        zeilen, quelle, _gesamt = lies_fuer_analyse(server, schluessel,
                                                    max_zeilen=20_000)
    else:
        zeilen = get_console_lines(server.id, limit_lines=20_000)
        quelle = "Aktuelle Sitzung"
    if not zeilen:
        return {"quelle": quelle, "leer": True,
                "hinweis": "Dieser Log ist leer. Wenn der Server nie gestartet wurde, "
                           "gibt es noch nichts zu sehen."}
    ausschnitt, angaben = waehle_ausschnitt(zeilen, max_zeilen=300)
    return {"quelle": quelle, "auswahl": angaben, "ausschnitt": ausschnitt[:MAX_ERGEBNIS]}


def _lies_backups(db: Session, user: User, server: Server, _p: dict) -> dict:
    from app.services.backup_service import list_backups_for_server

    eintraege = list_backups_for_server(db, server.id)[:25]
    return {"backups": [
        {"id": b.id, "name": getattr(b, "name", None) or getattr(b, "file_name", ""),
         "umfang": getattr(b, "backup_scope", None),
         "groesse_bytes": getattr(b, "size_bytes", None),
         "erstellt": str(getattr(b, "created_at", "") or "")[:19],
         "art": getattr(b, "backup_type", None)}
        for b in eintraege]}


def _lies_inhalte(db: Session, user: User, server: Server, _p: dict) -> dict:
    from app.services.content_service import list_installed_content

    return {"inhalte": [
        {"id": z.id, "name": z.name, "typ": z.content_type, "version": z.version_label,
         "anbieter": z.provider_name, "datei": z.file_name,
         "erklaerte_mc_version": z.declared_mc_version}
        for z in list_installed_content(db, server)][:200]}


# ----------------------------------------------------------- Handelnde Werkzeuge

def _tue_backup(db: Session, user: User, server: Server, p: dict) -> dict:
    from app.services.backup_service import create_backup

    umfang = str(p.get("umfang") or "full").strip().lower()
    if umfang not in {"full", "world", "config"}:
        raise AgentFehler("Umfang muss 'full', 'world' oder 'config' sein.")
    sicherung = create_backup(db, server=server, initiated_by_user_id=user.id,
                              backup_scope=umfang, backup_type="manual")
    return {"backup_id": sicherung.id,
            "datei": getattr(sicherung, "file_name", None) or getattr(sicherung, "name", ""),
            "groesse_bytes": getattr(sicherung, "size_bytes", None),
            "meldung": f"Sicherung ({umfang}) von '{server.name}' angelegt."}


def _tue_start(db: Session, user: User, server: Server, _p: dict) -> dict:
    from app.services.process_service import start_server

    ok, meldung = start_server(db, server, user.id)
    if not ok:
        raise AgentFehler(meldung)
    return {"meldung": meldung}


def _tue_stop(db: Session, user: User, server: Server, _p: dict) -> dict:
    from app.services.process_service import stop_server

    ok, meldung = stop_server(db, server, user.id)
    if not ok:
        raise AgentFehler(meldung)
    return {"meldung": meldung}


def _tue_neustart(db: Session, user: User, server: Server, _p: dict) -> dict:
    from app.services.process_service import restart_server

    ok, meldung = restart_server(db, server, user.id)
    if not ok:
        raise AgentFehler(meldung)
    return {"meldung": meldung}


def _tue_befehl(db: Session, user: User, server: Server, p: dict) -> dict:
    from app.services.process_service import send_console_command

    befehl = str(p.get("befehl") or "").strip()
    if not befehl:
        raise AgentFehler("Es wurde kein Befehl angegeben.")
    ok, meldung = send_console_command(db, server, befehl, user.id)
    if not ok:
        raise AgentFehler(meldung)
    return {"meldung": meldung, "befehl": befehl}


def _tue_einstellung(db: Session, user: User, server: Server, p: dict) -> dict:
    from app.services import ai_config_service
    from app.services.file_service import read_text_file, write_text_file

    schluessel = str(p.get("schluessel") or "").strip()
    wert = str(p.get("wert") if p.get("wert") is not None else "").strip()
    if not schluessel:
        raise AgentFehler("Es wurde kein Schluessel angegeben.")
    if schluessel in ai_config_service.GESPERRT:
        raise AgentFehler(f"'{schluessel}' darf der Agent nicht aendern: "
                          f"{ai_config_service.GESPERRT[schluessel]}.")

    datei = read_text_file(server, "server.properties")
    vorhanden = ai_config_service._parse_inhalt("server.properties", datei.content)
    geprueft = ai_config_service.pruefe_vorschlaege(
        [{"schluessel": schluessel, "wert": wert, "begruendung": ""}], vorhanden)
    if not geprueft["aenderungen"]:
        grund = (geprueft["abgelehnt"][0]["grund"] if geprueft["abgelehnt"]
                 else "nicht uebernehmbar")
        raise AgentFehler(f"'{schluessel}={wert}' wurde nicht gesetzt: {grund}.")

    zeilen = []
    gesetzt = False
    for roh in (datei.content or "").splitlines():
        if roh.strip() and not roh.lstrip().startswith("#") and "=" in roh:
            k = roh.split("=", 1)[0].strip()
            if k == schluessel:
                zeilen.append(f"{schluessel}={wert}")
                gesetzt = True
                continue
        zeilen.append(roh)
    if not gesetzt:   # kann nicht passieren, pruefe_vorschlaege haette abgelehnt
        raise AgentFehler(f"'{schluessel}' steht nicht in server.properties.")

    write_text_file(server, "server.properties", "\n".join(zeilen) + "\n")
    alt = geprueft["aenderungen"][0]["alt"]
    return {"meldung": f"server.properties: {schluessel} von '{alt}' auf '{wert}' "
                       f"gesetzt. Wirksam wird das beim naechsten Start.",
            "alt": alt, "neu": wert}


def _tue_transfer(db: Session, user: User, server: Server, p: dict) -> dict:
    from app.services import transfer_service
    from app.services.server_service import can_view_server, get_server_by_id

    quelle = get_server_by_id(db, int(p.get("quelle_id") or 0))
    if quelle is None or not can_view_server(db, user, quelle):
        raise AgentFehler("Den Quellserver gibt es nicht.")
    if quelle.id == server.id:
        raise AgentFehler("Quelle und Ziel sind derselbe Server.")

    try:
        transfer_service.uebertrage(
            db, quelle=quelle, ziel=server,
            welt=bool(p.get("welt", True)),
            spielerdaten=bool(p.get("spielerdaten", True)),
            configs=bool(p.get("configs", False)),
            erweiterungen=bool(p.get("erweiterungen", False)),
            zusatzwelten=bool(p.get("zusatzwelten", False)),
            zugangslisten=bool(p.get("zugangslisten", False)),
            user_id=user.id)
    except ValueError as exc:
        raise AgentFehler(str(exc)) from exc
    return {"meldung": f"Die Uebernahme von '{quelle.name}' nach '{server.name}' laeuft. "
                       f"Der Fortschritt steht auf der Detailseite des Zielservers."}


def _tue_inhalte_update(db: Session, user: User, server: Server, p: dict) -> dict:
    from app.services.content_service import bulk_update_installed_content

    art = str(p.get("typ") or "").strip().lower()
    arten = {art} if art in {"mod", "plugin", "datapack"} else {"mod", "plugin"}
    hinweise, warnungen = bulk_update_installed_content(
        db, server, user.id, content_types=arten)
    return {"aktualisiert": len(hinweise), "hinweise": hinweise[:20],
            "warnungen": warnungen[:20]}


# ------------------------------------------------------------- Beschreibungen

def _b_backup(db: Session, server: Server, p: dict) -> str:
    umfang = {"full": "den ganzen Serverordner", "world": "die Welt",
              "config": "die Konfigurationsdateien"}.get(
                  str(p.get("umfang") or "full").lower(), "den ganzen Serverordner")
    return f"Eine Sicherung von '{server.name}' anlegen ({umfang})."


def _b_start(db: Session, server: Server, _p: dict) -> str:
    return f"'{server.name}' starten."


def _b_stop(db: Session, server: Server, _p: dict) -> str:
    return f"'{server.name}' stoppen. Alle verbundenen Spieler werden getrennt."


def _b_neustart(db: Session, server: Server, _p: dict) -> str:
    return f"'{server.name}' neu starten. Alle verbundenen Spieler werden getrennt."


def _b_befehl(db: Session, server: Server, p: dict) -> str:
    return (f"Diesen Befehl auf der Konsole von '{server.name}' ausfuehren:\n"
            f"{str(p.get('befehl') or '').strip()}")


def _b_einstellung(db: Session, server: Server, p: dict) -> str:
    return (f"In server.properties von '{server.name}' "
            f"'{str(p.get('schluessel') or '').strip()}' auf "
            f"'{str(p.get('wert') or '').strip()}' setzen.")


def _b_transfer(db: Session, server: Server, p: dict) -> str:
    from app.services.server_service import get_server_by_id

    try:
        quelle = get_server_by_id(db, int(p.get("quelle_id") or 0))
    except (TypeError, ValueError):
        quelle = None
    teile = [k for k, v in (("Welt", p.get("welt", True)),
                            ("Spielerdaten", p.get("spielerdaten", True)),
                            ("Configs", p.get("configs", False)),
                            ("Plugins/Mods", p.get("erweiterungen", False)),
                            ("weitere Welten", p.get("zusatzwelten", False)),
                            ("Zugangslisten", p.get("zugangslisten", False))) if v]
    return (f"{', '.join(teile) or 'Nichts'} von '{quelle.name if quelle else '?'}' "
            f"nach '{server.name}' uebernehmen.")


def _b_inhalte_update(db: Session, server: Server, p: dict) -> str:
    art = str(p.get("typ") or "").strip().lower()
    was = {"mod": "Mods", "plugin": "Plugins", "datapack": "Datapacks"}.get(
        art, "Mods und Plugins")
    return f"Alle {was} von '{server.name}' auf die neueste passende Version bringen."


# ------------------------------------------------------------------- Katalog

WERKZEUGE: dict[str, Werkzeug] = {}


def _eintragen(w: Werkzeug) -> None:
    WERKZEUGE[w.name] = w


_eintragen(Werkzeug(
    name="server_details", lesend=True, recht="ansehen",
    beschreibung="Alles zu einem Server: Status, Speicher, Spieler, Verbrauch, Zahl der "
                 "installierten Mods/Plugins.",
    felder={}, pflicht=[], ausfuehren=_lies_server_details))

_eintragen(Werkzeug(
    name="datei_lesen", lesend=True, recht="ansehen",
    beschreibung="Eine Textdatei des Servers lesen, z.B. server.properties, spigot.yml "
                 "oder config/paper-global.yml. Pfad relativ zum Serverordner.",
    felder={"pfad": {"type": "string",
                     "description": "Pfad relativ zum Serverordner, z.B. "
                                    "'server.properties'"}},
    pflicht=["pfad"], ausfuehren=_lies_datei))

_eintragen(Werkzeug(
    name="dateien_auflisten", lesend=True, recht="ansehen",
    beschreibung="Welche Textdateien des Servers lassen sich lesen oder bearbeiten?",
    felder={}, pflicht=[], ausfuehren=_lies_dateiliste))

_eintragen(Werkzeug(
    name="log_ansehen", lesend=True, recht="ansehen",
    beschreibung="Den Log eines Servers ansehen. Es kommen nicht alle Zeilen zurueck, "
                 "sondern Kopf, Fehlerstellen und Ende - das genuegt fuer eine Diagnose.",
    felder={"logdatei": {"type": "string",
                         "description": "Schluessel einer Logdatei; leer lassen fuer die "
                                        "laufende Sitzung"}},
    pflicht=[], ausfuehren=_lies_logfehler))

_eintragen(Werkzeug(
    name="backups_auflisten", lesend=True, recht="ansehen",
    beschreibung="Welche Sicherungen gibt es fuer diesen Server?",
    felder={}, pflicht=[], ausfuehren=_lies_backups))

_eintragen(Werkzeug(
    name="inhalte_auflisten", lesend=True, recht="ansehen",
    beschreibung="Welche Mods, Plugins und Datapacks sind installiert, in welcher Version?",
    felder={}, pflicht=[], ausfuehren=_lies_inhalte))

_eintragen(Werkzeug(
    name="backup_anlegen", lesend=False, recht="steuern", gefahr="niedrig",
    beschreibung="Eine Sicherung des Servers anlegen.",
    felder={"umfang": {"type": "string", "enum": ["full", "world", "config"],
                       "description": "full = ganzer Ordner, world = nur die Welten, "
                                      "config = nur Konfigurationsdateien"}},
    pflicht=[], ausfuehren=_tue_backup, beschreibe=_b_backup,
    nachwirkung="Legt eine neue Datei an und veraendert den Server nicht."))

_eintragen(Werkzeug(
    name="server_starten", lesend=False, recht="steuern", gefahr="niedrig",
    beschreibung="Den Server starten.", felder={}, pflicht=[],
    ausfuehren=_tue_start, beschreibe=_b_start,
    nachwirkung="Der Server belegt danach Arbeitsspeicher und seinen Port."))

_eintragen(Werkzeug(
    name="server_stoppen", lesend=False, recht="steuern", gefahr="mittel",
    beschreibung="Den Server stoppen.", felder={}, pflicht=[],
    ausfuehren=_tue_stop, beschreibe=_b_stop,
    nachwirkung="Verbundene Spieler werden getrennt. Nicht gespeicherter Fortschritt "
                "der letzten Minuten kann verloren gehen."))

_eintragen(Werkzeug(
    name="server_neustarten", lesend=False, recht="steuern", gefahr="mittel",
    beschreibung="Den Server neu starten.", felder={}, pflicht=[],
    ausfuehren=_tue_neustart, beschreibe=_b_neustart,
    nachwirkung="Verbundene Spieler werden getrennt und muessen sich neu verbinden."))

_eintragen(Werkzeug(
    name="konsolenbefehl", lesend=False, recht="steuern", gefahr="hoch",
    beschreibung="Einen Befehl auf der Serverkonsole ausfuehren, z.B. 'save-all' oder "
                 "'whitelist reload'.",
    felder={"befehl": {"type": "string",
                       "description": "Der Befehl ohne fuehrenden Schraegstrich"}},
    pflicht=["befehl"], ausfuehren=_tue_befehl, beschreibe=_b_befehl,
    nachwirkung="Ein Konsolenbefehl wirkt sofort und laesst sich nicht zurueckdrehen. "
                "Lies ihn Wort fuer Wort, bevor du bestaetigst."))

_eintragen(Werkzeug(
    name="einstellung_setzen", lesend=False, recht="dateien", gefahr="mittel",
    beschreibung="Einen Wert in server.properties aendern. Strukturelle Schluessel "
                 "(level-name, Ports, online-mode, level-seed) sind gesperrt.",
    felder={"schluessel": {"type": "string"}, "wert": {"type": "string"}},
    pflicht=["schluessel", "wert"], ausfuehren=_tue_einstellung,
    beschreibe=_b_einstellung,
    nachwirkung="Wirkt beim naechsten Start des Servers."))

_eintragen(Werkzeug(
    name="inhalte_aktualisieren", lesend=False, recht="steuern", gefahr="mittel",
    beschreibung="Alle installierten Mods/Plugins auf die neueste zur Serverversion "
                 "passende Fassung bringen.",
    felder={"typ": {"type": "string", "enum": ["mod", "plugin", "datapack"],
                    "description": "Leer lassen fuer Mods und Plugins zusammen"}},
    pflicht=[], ausfuehren=_tue_inhalte_update, beschreibe=_b_inhalte_update,
    nachwirkung="Ersetzt Dateien im plugins-/mods-Ordner. Ein Plugin kann nach einem "
                "Update eine neue Config verlangen."))

_eintragen(Werkzeug(
    name="welt_uebernehmen", lesend=False, recht="super_admin", gefahr="hoch",
    beschreibung="Welt und/oder Daten von einem anderen Server uebernehmen. server_id ist "
                 "das ZIEL, quelle_id die Quelle.",
    felder={
        "quelle_id": {"type": "integer", "description": "ID des Quellservers"},
        "welt": {"type": "boolean"}, "spielerdaten": {"type": "boolean"},
        "configs": {"type": "boolean"}, "erweiterungen": {"type": "boolean"},
        "zusatzwelten": {"type": "boolean"}, "zugangslisten": {"type": "boolean"},
    },
    pflicht=["quelle_id"], ausfuehren=_tue_transfer, beschreibe=_b_transfer,
    nachwirkung="Die bisherige Welt des Zielservers wird ersetzt (vorher wird automatisch "
                "gesichert). Die Welt-Identitaet wandert mit, die Quelle wird dadurch "
                "entwertet - ein Transfer ist ein Umzug, keine Kopie."))


def werkzeug_schemas() -> list[dict]:
    return [w.schema() for w in WERKZEUGE.values()]


# ---------------------------------------------------------------------- Auftrag

AUFTRAG_ZUSATZ = (
    "Du arbeitest IM Manager und kannst nachsehen, bevor du antwortest. Nutze dafuer die "
    "lesenden Werkzeuge, statt zu raten oder den Nutzer nach Dingen zu fragen, die du "
    "selbst nachsehen kannst.\n"
    "Fuer alles, was etwas VERAENDERT, rufe das passende Werkzeug auf - es wird nicht "
    "sofort ausgefuehrt, sondern dem Nutzer zur Bestaetigung vorgelegt. Rufe hoechstens "
    "ein veraenderndes Werkzeug auf einmal auf und erklaere im Text davor, warum.\n"
    "Du kannst nichts tun, was der angemeldete Nutzer nicht auch selbst darf; eine Absage "
    "wegen fehlender Rechte ist keine Fehlfunktion.\n"
    "Antworte knapp und auf Deutsch. Nenne Server mit Namen, nicht mit ID."
)


def _ergebnis_text(daten: dict) -> str:
    text = json.dumps(daten, ensure_ascii=False, default=str)
    if len(text) > MAX_ERGEBNIS:
        return text[:MAX_ERGEBNIS] + ' ... (gekuerzt)"'
    return text


def _fuehre_lesend_aus(db: Session, user: User, name: str, eingabe: dict) -> dict:
    """Ein lesendes Werkzeug ausfuehren. Fehler werden zur Antwort, nicht zur Ausnahme.

    Ein abgewiesener Werkzeugaufruf soll den Agenten nicht beenden, sondern ihn merken
    lassen, dass er anders weitermachen muss - sonst bricht eine Unterhaltung ab, weil
    das Modell einen Pfad geraten hat.
    """
    werkzeug = WERKZEUGE.get(name)
    if werkzeug is None or not werkzeug.lesend:
        return {"fehler": f"Das Werkzeug '{name}' gibt es nicht."}
    try:
        server = (_server_holen(db, user, eingabe.get("server_id"))
                  if werkzeug.braucht_server else None)
        _recht_pruefen(db, user, server, werkzeug.recht)
        if werkzeug.ausfuehren is None:
            return {"fehler": "Dieses Werkzeug kann nichts tun."}
        return werkzeug.ausfuehren(db, user, server, eingabe)
    except AgentFehler as exc:
        return {"fehler": str(exc)}
    except Exception as exc:  # noqa: BLE001 - ein Lesefehler darf das Gespraech nicht toeten
        return {"fehler": f"Unerwarteter Fehler: {exc}"}


def beauftrage(db: Session, *, user: User, nachricht: str,
               verlauf: list[dict] | None = None) -> dict:
    """Einen Auftrag bearbeiten: nachsehen und antworten oder eine Aktion vorschlagen.

    Rueckgabe enthaelt ``text`` (die Antwort), ``vorschlag`` (None oder eine Aktion zur
    Bestaetigung), ``schritte`` (was nachgesehen wurde) und ``verlauf`` (zum Mitgeben beim
    naechsten Zug). Wirft KiAbgelehnt oder KiFehler.
    """
    # Einmal pruefen, an der echten Frage - danach gilt das Thema fuer alle Runden
    # dieses Auftrags. Die Pruefung der ANTWORT bleibt in jeder Runde bestehen.
    ai_service.pruefe_thema(db, user=user, nachricht=nachricht, verlauf=verlauf)

    lauf: list[dict] = list(verlauf or [])
    schritte: list[dict] = []
    kosten_cent = 0.0
    hinweise: list[str] = []
    ergebnis: dict = {}

    for runde in range(MAX_SCHRITTE):
        ergebnis = ai_service.frage(
            db, user=user, funktion="agent",
            nachricht=nachricht if runde == 0 else
                      "Mach weiter mit dem, was du jetzt weisst.",
            kontext=f"Lage im Manager:\n{lage(db, user)}" if runde == 0 else "",
            zusatzauftrag=AUFTRAG_ZUSATZ, werkzeuge=werkzeug_schemas(),
            verlauf=lauf, thema_geprueft=True, max_tokens=3072)
        kosten_cent += float(ergebnis.get("kosten_cent") or 0.0)
        hinweise += [h for h in (ergebnis.get("hinweise") or []) if h not in hinweise]

        aufrufe = ergebnis.get("werkzeug_aufrufe") or []
        if not aufrufe:
            break

        # Ein unbekannter Werkzeugname gilt als handelnd und wird damit nie von selbst
        # ausgefuehrt - im Zweifel vorlegen statt tun.
        handelnde = [a for a in aufrufe
                     if not (WERKZEUGE.get(str(a.get("name"))) or _UNBEKANNT).lesend]
        if handelnde:
            # Ein veraenderndes Werkzeug beendet den Lauf: ausgefuehrt wird erst nach
            # Bestaetigung. Mehrere auf einmal werden nicht gesammelt - jede Aktion soll
            # einzeln entschieden werden.
            vorschlag = _baue_vorschlag(db, user, handelnde[0])
            if len(handelnde) > 1:
                hinweise.append(
                    f"Die KI hat {len(handelnde)} Aktionen auf einmal vorgeschlagen. "
                    f"Vorgelegt wird nur die erste - jede Aktion soll einzeln "
                    f"entschieden werden.")
            return _antwort(ergebnis, lauf, schritte, vorschlag, kosten_cent,
                            hinweise)

        # Nur lesende Aufrufe: ausfuehren und zurueckspielen.
        lauf.append({"rolle": "assistent",
                     "inhalt": _bloecke_fuer_aufrufe(ergebnis.get("text") or "", aufrufe)})
        ergebnisse = []
        for aufruf in aufrufe:
            daten = _fuehre_lesend_aus(db, user, str(aufruf.get("name")),
                                       aufruf.get("eingabe") or {})
            schritte.append({"werkzeug": str(aufruf.get("name")),
                             "eingabe": aufruf.get("eingabe") or {},
                             "fehler": daten.get("fehler")})
            ergebnisse.append({"type": "tool_result", "tool_use_id": aufruf.get("id"),
                               "content": _ergebnis_text(daten)})
        lauf.append({"rolle": "nutzer", "inhalt": ergebnisse})
    else:
        hinweise.append(
            f"Der Agent hat die Grenze von {MAX_SCHRITTE} Schritten erreicht und hier "
            f"aufgehoert. Das schuetzt das Guthaben; frag gezielter weiter.")

    return _antwort(ergebnis, lauf, schritte, None, kosten_cent, hinweise)


def _bloecke_fuer_aufrufe(text: str, aufrufe: list[dict]) -> list[dict]:
    bloecke: list[dict] = []
    if (text or "").strip():
        bloecke.append({"type": "text", "text": text})
    for aufruf in aufrufe:
        bloecke.append({"type": "tool_use", "id": aufruf.get("id"),
                        "name": aufruf.get("name"), "input": aufruf.get("eingabe") or {}})
    return bloecke


def _antwort(ergebnis: dict, lauf: list[dict], schritte: list[dict],
             vorschlag: dict | None, kosten_cent: float, hinweise: list[str]) -> dict:
    text = (ergebnis.get("text") or "").strip()
    if not text and vorschlag:
        text = "Ich wuerde Folgendes tun:"
    if not text:
        text = "Ich habe nichts zu melden."
    # Der eigene Zug gehoert in den Verlauf, sonst wiederholt sich das Modell. Ein
    # offener Werkzeugaufruf darf NICHT mit hinein: zu ihm gibt es (noch) kein Ergebnis,
    # und der Anbieter weist einen tool_use ohne tool_result ab.
    neuer_lauf = list(lauf)
    if text:
        neuer_lauf.append({"rolle": "assistent", "inhalt": text})
    return {
        "text": text, "vorschlag": vorschlag, "schritte": schritte,
        "verlauf": neuer_lauf, "modell": ergebnis.get("modell"),
        "kosten_cent": kosten_cent, "budget": ergebnis.get("budget"),
        "hinweise": hinweise,
    }


def _baue_vorschlag(db: Session, user: User, aufruf: dict) -> dict:
    """Aus einem Werkzeugaufruf einen Vorschlag machen, den ein Mensch beurteilen kann.

    Der Satz wird vom MANAGER formuliert, nicht vom Modell: sonst koennte die Beschreibung
    von der Tat abweichen ("ich lege nur eine Sicherung an", waehrend der Aufruf den
    Server stoppt).
    """
    name = str(aufruf.get("name") or "")
    eingabe = aufruf.get("eingabe") or {}
    werkzeug = WERKZEUGE.get(name)
    if werkzeug is None:
        return {"werkzeug": name, "parameter": eingabe, "moeglich": False,
                "beschreibung": f"Das Werkzeug '{name}' gibt es nicht.",
                "gefahr": "hoch", "nachwirkung": "", "hindernis":
                    "Unbekanntes Werkzeug - hier wird nichts ausgefuehrt."}

    vorschlag: dict = {"werkzeug": name, "parameter": eingabe, "moeglich": True,
                       "gefahr": werkzeug.gefahr, "nachwirkung": werkzeug.nachwirkung,
                       "hindernis": ""}
    try:
        server = (_server_holen(db, user, eingabe.get("server_id"))
                  if werkzeug.braucht_server else None)
        _recht_pruefen(db, user, server, werkzeug.recht)
        vorschlag["server_name"] = server.name if server else None
        vorschlag["beschreibung"] = (werkzeug.beschreibe(db, server, eingabe)
                                     if werkzeug.beschreibe else werkzeug.beschreibung)
    except AgentFehler as exc:
        vorschlag["moeglich"] = False
        vorschlag["hindernis"] = str(exc)
        vorschlag["beschreibung"] = werkzeug.beschreibung
    return vorschlag


# ----------------------------------------------------------------- Ausfuehrung

def fuehre_aus(db: Session, *, user: User, werkzeug_name: str, parameter: dict) -> dict:
    """Eine bestaetigte Aktion ausfuehren.

    HIER wird nichts geglaubt, was aus dem Browser kommt. Der Werkzeugname muss im Katalog
    stehen und darf nicht lesend sein; der Server wird neu geholt; das Recht wird neu
    gegen die Datenbank gestellt; die Parameter werden von der Dienstfunktion geprueft wie
    bei einem Klick. Ein manipulierter Vorschlag kann deshalb nichts tun, was dieser
    Nutzer nicht auch selbst duerfte.
    """
    from app.services import audit_service

    werkzeug = WERKZEUGE.get(str(werkzeug_name or ""))
    if werkzeug is None:
        raise AgentFehler(f"Das Werkzeug '{werkzeug_name}' gibt es nicht.")
    if werkzeug.lesend:
        raise AgentFehler(f"'{werkzeug.name}' veraendert nichts und braucht keine "
                          f"Bestaetigung.")
    if werkzeug.ausfuehren is None:
        raise AgentFehler(f"'{werkzeug.name}' kann nichts ausfuehren.")

    server = (_server_holen(db, user, (parameter or {}).get("server_id"))
              if werkzeug.braucht_server else None)
    _recht_pruefen(db, user, server, werkzeug.recht)

    audit_service.log_action(
        db, action=f"ai.agent.{werkzeug.name}", user_id=user.id,
        server_id=server.id if server else None,
        details=json.dumps(parameter or {}, ensure_ascii=False, default=str)[:400])

    daten = werkzeug.ausfuehren(db, user, server, parameter or {})
    daten.setdefault("meldung", f"'{werkzeug.name}' ausgefuehrt.")
    daten["werkzeug"] = werkzeug.name
    return daten
