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

    nur_admin: bool = False
    """Zusaetzlich zu ``recht``: nur Admin und Super-Admin.

    Fuer Werkzeuge, die mehr zeigen oder aendern, als die Oberflaeche einem Moderator
    gibt - in ein Archiv zu sehen heisst auch, eine Konfiguration mit Zugangsdaten zu
    sehen, die der Dateieditor nicht anzeigt (der kennt nur Textdateien). Geprueft wird
    das an denselben drei Toren wie ``recht``, damit der Vorbehalt nicht neben einem
    Werkzeug stehen kann, ohne zu wirken."""

    zusatzpruefung: Callable[..., None] | None = None
    """Eine Pruefung, die von den PARAMETERN abhaengt und nicht vom Werkzeug allein.

    Beispiel Konsolenbefehl: das Werkzeug braucht Steuerrechte, aber 'op' und 'whitelist
    off' verlangen Admin. Ohne diesen Haken erfaehrt der Nutzer das erst NACH dem
    Bestaetigen - der Vorschlag soll aber schon sagen, dass es nicht geht."""

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


# Wie gross eine Datei sein darf, die der Agent ganz einliest. Darueber wird nur der
# Anfang gelesen: ``read_text_file`` holt die VOLLSTAENDIGE Datei in den Speicher, um
# daraus 12.000 Zeichen zurueckzugeben - bei einer 8-MB-Datei schon eine Speicherspitze
# von 26 MB, bei einem gewachsenen Log entsprechend mehr. Fuer Logs ist ohnehin
# ``log_ansehen`` das richtige Werkzeug, das Kopf und Ende liest.
MAX_DATEI_BYTES = 1_000_000


def _lies_datei(db: Session, user: User, server: Server, p: dict) -> dict:
    from app.services.file_service import validate_server_relative_path

    pfad = str(p.get("pfad") or "").strip()
    if not pfad:
        raise AgentFehler("Es wurde kein Dateipfad angegeben.")
    try:
        # Prueft den Pfad gegen den Serverordner (kein Ausbruch, kein absoluter Pfad,
        # keine Binaerdatei) - und liefert den aufgeloesten Pfad, damit die GROESSE
        # geprueft werden kann, bevor etwas gelesen wird.
        ziel = validate_server_relative_path(server, pfad)
    except ValueError as exc:
        raise AgentFehler(str(exc)) from exc

    try:
        groesse = ziel.stat().st_size
    except OSError as exc:
        raise AgentFehler(f"Die Datei konnte nicht gelesen werden: {exc}") from exc

    # IMMER begrenzt lesen, nicht nur oberhalb der Groessengrenze.
    #
    # Vorher wurde unterhalb der Grenze ueber read_text_file gelesen, und das holt die
    # ganze Datei in den Speicher. Eine Datei, die ZWISCHEN stat() und open() waechst -
    # ein Log, in das der Server gerade schreibt -, kam damit trotzdem vollstaendig in
    # den Speicher; gemessen wurde das Achtfache der Grenze. Ein einziger begrenzter
    # Lesevorgang kennt dieses Fenster nicht.
    try:
        with ziel.open("r", encoding="utf-8", errors="replace") as f:
            inhalt = f.read(MAX_ERGEBNIS + 1)
    except OSError as exc:
        raise AgentFehler(f"Die Datei konnte nicht gelesen werden: {exc}") from exc

    gekuerzt = len(inhalt) > MAX_ERGEBNIS or groesse > MAX_DATEI_BYTES
    ergebnis: dict = {"pfad": pfad.replace("\\", "/"),
                      "inhalt": inhalt[:MAX_ERGEBNIS], "gekuerzt": gekuerzt}
    if groesse > MAX_DATEI_BYTES:
        ergebnis["groesse_bytes"] = groesse
        ergebnis["hinweis"] = (
            f"Die Datei ist {groesse / 1024 / 1024:.1f} MB gross - nur der Anfang wurde "
            f"gelesen. Fuer Logs ist 'log_ansehen' das richtige Werkzeug, es liest Kopf "
            f"UND Ende.")
    elif gekuerzt:
        ergebnis["hinweis"] = f"Nur die ersten {MAX_ERGEBNIS} Zeichen der Datei."
    return ergebnis


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


def _pruefe_admin(db: Session, user: User, server: Server | None, p: dict) -> None:
    """Fuer Werkzeuge, die mehr zeigen oder aendern, als die Oberflaeche einem Moderator gibt.

    In einem Archiv kann alles stehen - auch eine Konfiguration mit Zugangsdaten, die
    ueber den Dateieditor nicht sichtbar waere (der zeigt nur Textdateien). Und wer die
    Teilpakete eines Resource Packs aendert, aendert, was JEDER Spieler beim Beitritt
    geladen bekommt.
    """
    if (getattr(user, "role", "") or "") in {UserRole.SUPER_ADMIN.value,
                                             UserRole.ADMIN.value}:
        return
    raise AgentFehler("Dafuer braucht es Admin-Rechte.")


def _pruefe_konsolenbefehl(db: Session, user: User, server: Server, p: dict) -> None:
    """Befehle, die Rechte vergeben oder Sperren aufheben, sind Admin-Sache.

    Die Regel selbst steht in process_service (``braucht_admin``) und greift dort fuer
    JEDEN Weg - auch fuer die Konsolenseite. Hier wird sie nur vorgezogen, damit der
    Vorschlag gleich sagt, dass es nicht geht, statt erst bei der Ausfuehrung.
    """
    from app.core.constants import UserRole
    from app.services.process_service import braucht_admin

    grund = braucht_admin(str(p.get("befehl") or ""))
    if not grund:
        return
    if (getattr(user, "role", "") or "") in {UserRole.SUPER_ADMIN.value,
                                             UserRole.ADMIN.value}:
        return
    raise AgentFehler(f"Dafuer braucht es Admin-Rechte: {grund}")


def _pruefe_einstellung_rechte(db: Session, user: User, server: Server, p: dict) -> None:
    """``white-list=false`` ist dasselbe wie ``whitelist off`` - also Admin-Sache.

    Vierter Weg zur selben Tat: Konsole, Zeitplan, Zugriffsseite, Dateieditor waren schon
    gesichert. Das Werkzeug ``einstellung_setzen`` schreibt aber ebenfalls in
    server.properties und verlangte bisher nur das Dateirecht.
    """
    from app.core.constants import UserRole
    from app.services.process_service import whitelist_wird_abgeschaltet

    schluessel = str(p.get("schluessel") or "").strip().lower()
    wert = str(p.get("wert") if p.get("wert") is not None else "").strip()
    grund = whitelist_wird_abgeschaltet("server.properties",
                                        f"{schluessel}=true", f"{schluessel}={wert}")
    if not grund:
        return
    if (getattr(user, "role", "") or "") in {UserRole.SUPER_ADMIN.value,
                                             UserRole.ADMIN.value}:
        return
    raise AgentFehler(f"Dafuer braucht es Admin-Rechte: {grund}")


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


def _lies_inhalt_suchen(db: Session, user: User, server: Server, p: dict) -> dict:
    """Ein Plugin/eine Mod bei den Anbietern suchen - passend zu DIESEM Server.

    Ohne dieses Werkzeug konnte der Agent einen Auftrag wie "installiere Plugin X" gar
    nicht angehen: er kannte die Projekt-ID nicht und hatte keinen Weg, sie zu finden.
    """
    from app.services import content_service as cs

    begriff = str(p.get("begriff") or "").strip()
    if not begriff:
        raise AgentFehler("Es wurde kein Suchbegriff angegeben.")
    art = str(p.get("typ") or "plugin").strip().lower()
    if art not in {"mod", "plugin", "datapack", "modpack", "resourcepack"}:
        art = "plugin"

    mc_version = cs._expected_server_mc_version(server)
    loader = cs._expected_server_loader(server, art)
    treffer: list[dict] = []
    fehler: list[str] = []
    for anbieter, suche in (("modrinth", cs.search_modrinth),
                            ("curseforge", cs.search_curseforge)):
        try:
            for eintrag in suche(begriff, mc_version, loader, art)[:8]:
                treffer.append({
                    "anbieter": anbieter,
                    "projekt_id": eintrag.get("id"),
                    "name": eintrag.get("title"),
                    "beschreibung": (str(eintrag.get("description") or "")[:200]),
                    "downloads": eintrag.get("downloads"),
                    "url": eintrag.get("project_url"),
                })
        except Exception as exc:  # noqa: BLE001 - ein Anbieter darf den anderen nicht toeten
            fehler.append(f"{anbieter}: {exc}")

    ergebnis: dict = {"treffer": treffer, "mc_version": mc_version, "loader": loader,
                      "typ": art}
    if fehler:
        ergebnis["anbieter_fehler"] = fehler
    if not treffer:
        ergebnis["hinweis"] = (f"Zu '{begriff}' gab es keinen Treffer fuer "
                               f"{mc_version} / {loader or 'ohne Loader'}. Vielleicht "
                               f"heisst das Projekt anders, oder es gibt es fuer diese "
                               f"Version nicht.")
    return ergebnis


def _lies_versionen(db: Session, user: User, server: Server, p: dict) -> dict:
    """Welche Fassungen eines Projekts passen zu diesem Server?"""
    from app.services import content_service as cs

    anbieter = str(p.get("anbieter") or "").strip().lower()
    projekt_id = str(p.get("projekt_id") or "").strip()
    art = str(p.get("typ") or "plugin").strip().lower()
    if not projekt_id:
        raise AgentFehler("Es wurde keine Projekt-ID angegeben.")

    mc_version = cs._expected_server_mc_version(server)
    loader = cs._expected_server_loader(server, art)
    try:
        if anbieter == "modrinth":
            fassungen = cs.list_modrinth_versions(projekt_id, mc_version, loader)
        elif anbieter == "curseforge":
            # CurseForge arbeitet mit Zahlen-IDs, Modrinth mit Zeichenketten.
            fassungen = cs.list_curseforge_versions(int(projekt_id), mc_version, loader,
                                                    art)
        else:
            raise AgentFehler(f"Unbekannter Anbieter '{anbieter}'.")
    except AgentFehler:
        raise
    except Exception as exc:  # noqa: BLE001
        raise AgentFehler(f"Die Versionsliste konnte nicht geladen werden: {exc}") from exc
    return {"fassungen": fassungen[:12], "mc_version": mc_version, "loader": loader}


def _pruefe_inhaltstyp(server: Server, art: str) -> None:
    """Passt dieser Inhaltstyp ueberhaupt zu diesem Server?

    Ohne diese Pruefung legte der Agent ein Plugin in den plugins-Ordner eines
    Fabric-Servers - der liest den Ordner nie, und die Meldung "installiert, wirksam
    beim naechsten Start" war trotzdem da. Der Nutzer sucht den Fehler dann im Plugin.
    """
    from app.services import content_service as cs

    if art in {"datapack", "resourcepack"}:
        return   # loaderunabhaengig
    if cs._expected_server_loader(server, art) is None:
        passend = {"mod": "Mods", "plugin": "Plugins",
                   "modpack": "Modpacks"}.get(art, art)
        raise AgentFehler(
            f"'{server.name}' ist ein {server.server_type}-Server und laedt keine "
            f"{passend}. Pruefe mit server_details, welche Art hier passt.")


def _tue_inhalt_installieren(db: Session, user: User, server: Server, p: dict) -> dict:
    from app.services import content_service as cs

    anbieter = str(p.get("anbieter") or "").strip().lower()
    projekt_id = str(p.get("projekt_id") or "").strip()
    version_id = str(p.get("version_id") or "").strip()
    art = str(p.get("typ") or "plugin").strip().lower()
    if not projekt_id or not version_id:
        raise AgentFehler("Projekt-ID und Version-ID sind beide noetig.")
    _pruefe_inhaltstyp(server, art)

    if art == "resourcepack":
        # Resource Packs werden NICHT als Datei abgelegt, sondern ueber
        # server.properties gesetzt (resource-pack + resource-pack-sha1) - genau wie in
        # der Oberflaeche. Ohne diesen Zweig landete die Datei in einem Ordner, den der
        # Server nicht liest, und die Erfolgsmeldung war eine Falschmeldung.
        try:
            eintrag = cs.set_server_resource_pack(db, server, anbieter, projekt_id,
                                                  version_id, user.id)
        except ValueError as exc:
            raise AgentFehler(str(exc)) from exc
        return {"meldung": f"Resource Pack '{eintrag.name or projekt_id}' in "
                           f"server.properties von '{server.name}' gesetzt. Wirksam beim "
                           f"naechsten Start.",
                "inhalt_id": eintrag.id}

    try:
        if anbieter == "modrinth":
            eintrag = cs.install_modrinth(db, server, projekt_id, version_id, art, user.id)
        elif anbieter == "curseforge":
            # CurseForge erwartet Zahlen-IDs. Eine nicht-numerische ID ist ein Versehen
            # des Modells und keine Stoerung - also als Meldung, nicht als Abbruch.
            try:
                eintrag = cs.install_curseforge(db, server, int(projekt_id),
                                                int(version_id), art, user.id)
            except (TypeError, ValueError) as exc:
                raise AgentFehler(
                    f"CurseForge braucht Zahlen als Projekt- und Version-ID "
                    f"(bekommen: '{projekt_id}' / '{version_id}')." ) from exc
        else:
            raise AgentFehler(f"Unbekannter Anbieter '{anbieter}'.")
    except AgentFehler:
        raise
    except ValueError as exc:
        raise AgentFehler(str(exc)) from exc

    return {"meldung": f"'{eintrag.name or projekt_id}' {eintrag.version_label or ''} "
                       f"nach '{server.name}' installiert. Wirksam wird es beim naechsten "
                       f"Start.",
            "inhalt_id": eintrag.id, "datei": eintrag.file_name}


def _tue_inhalt_entfernen(db: Session, user: User, server: Server, p: dict) -> dict:
    from app.models.installed_content import InstalledContent
    from app.services import content_service as cs

    try:
        inhalt_id = int(p.get("inhalt_id") or 0)
    except (TypeError, ValueError):
        inhalt_id = 0
    if not inhalt_id:
        raise AgentFehler("Es wurde keine Inhalts-ID angegeben (siehe "
                          "inhalte_auflisten).")
    eintrag = db.get(InstalledContent, inhalt_id)
    if eintrag is None or eintrag.server_id != server.id:
        raise AgentFehler("Diesen Eintrag gibt es auf diesem Server nicht.")

    name = eintrag.name or eintrag.file_name or str(inhalt_id)
    try:
        cs.delete_installed_content(db, server, eintrag, user.id)
    except ValueError as exc:
        raise AgentFehler(str(exc)) from exc
    if (eintrag.content_type or "").strip().lower() == "resourcepack":
        return {"meldung": f"Resource Pack '{name}' aus server.properties von "
                           f"'{server.name}' entfernt. Wirksam beim naechsten Start."}
    return {"meldung": f"'{name}' wurde von '{server.name}' entfernt."}


def _b_inhalt_installieren(db: Session, server: Server, p: dict) -> str:
    return (f"{str(p.get('typ') or 'Plugin')} '{str(p.get('projekt_id') or '?')}' "
            f"(Version {str(p.get('version_id') or '?')}, {p.get('anbieter')}) auf "
            f"'{server.name}' installieren.")


def _b_inhalt_entfernen(db: Session, server: Server, p: dict) -> str:
    """Beschreibung der Tat - robust, denn hier darf nichts scheitern.

    ``int(...)`` auf einer nicht-numerischen ID warf vorher eine ValueError, und die
    riss die ganze Anfrage mit: HTTP 500, obwohl die Antwort des Modells schon bezahlt
    war. Zusaetzlich wurde der Server des Eintrags nicht geprueft, weshalb der
    Bestaetigungstext Namen von Inhalten FREMDER Server nennen konnte.
    """
    from app.models.installed_content import InstalledContent

    try:
        kennung = int(p.get("inhalt_id") or 0)
    except (TypeError, ValueError):
        kennung = 0
    eintrag = db.get(InstalledContent, kennung) if kennung else None
    if eintrag is None or eintrag.server_id != server.id:
        return (f"Einen Inhalt mit der ID {p.get('inhalt_id')!r} gibt es auf "
                f"'{server.name}' nicht.")
    name = eintrag.name or eintrag.file_name or str(kennung)
    if (eintrag.content_type or "").strip().lower() == "resourcepack":
        return (f"Resource Pack '{name}' aus server.properties von '{server.name}' "
                f"entfernen.")
    return f"'{name}' von '{server.name}' entfernen - die Datei wird geloescht."


def _pruefe_inhalt_rechte(db: Session, user: User, server: Server, p: dict) -> None:
    """Resource Packs brauchen Dateirechte, denn sie werden in server.properties gesetzt.

    Das Werkzeug verlangt sonst nur Steuerrechte - damit haette ein Nutzer ohne
    Dateirecht drei Schluessel in server.properties ueberschrieben und ein vorhandenes
    Resource Pack zerstoert.
    """
    art = str(p.get("typ") or "").strip().lower()
    if art != "resourcepack":
        return
    _recht_pruefen(db, user, server, "dateien")


def _pruefe_entfernen_rechte(db: Session, user: User, server: Server, p: dict) -> None:
    """Wie oben, nur dass die Art hier am Eintrag haengt und nicht am Parameter."""
    from app.models.installed_content import InstalledContent

    try:
        kennung = int(p.get("inhalt_id") or 0)
    except (TypeError, ValueError):
        return
    eintrag = db.get(InstalledContent, kennung) if kennung else None
    if eintrag is None or eintrag.server_id != server.id:
        return
    if (eintrag.content_type or "").strip().lower() == "resourcepack":
        _recht_pruefen(db, user, server, "dateien")


def _ja(wert) -> bool:
    """Das Modell schickt Wahrheitswerte als bool, als "true" oder als "ja"."""
    if isinstance(wert, bool):
        return wert
    return str(wert or "").strip().lower() in {"true", "1", "ja", "yes", "y"}


def _pfad_ziel(db: Session, p: dict, feld: str = "pfad", *, zweck: str,
               muss_existieren: bool = True, erwarte: str | None = None) -> Path:
    """Einen Pfad durch die Regel schicken und als Ausnahme des Agenten weitergeben."""
    from app.services import admin_path_service

    try:
        return admin_path_service.pruefe(str(p.get(feld) or ""), db=db, zweck=zweck,
                                         muss_existieren=muss_existieren,
                                         erwarte=erwarte)
    except admin_path_service.PfadFehler as exc:
        raise AgentFehler(str(exc)) from exc


def _protokolliere_lesen(db: Session, user: User, name: str, ziel: Path) -> None:
    """Auch das LESEN eines freien Pfades wird protokolliert.

    Veraendernde Werkzeuge protokolliert fuehre_aus von selbst. Lesende nicht - bei
    Serverdateien genuegt das auch, denn die sieht der Nutzer ohnehin im Dateieditor.
    Ein freier Pfad ist etwas anderes: dort kann alles liegen, und hinterher soll
    nachvollziehbar sein, was angesehen wurde.
    """
    from app.services import audit_service

    audit_service.log_action(db, action=f"ai.agent.{name}", user_id=user.id,
                             details=str(ziel)[:400])


def _lies_pfad_liste(db: Session, user: User, _server, p: dict) -> dict:
    from app.services import admin_path_service

    ziel = _pfad_ziel(db, p, zweck="lesen", erwarte="ordner")
    _protokolliere_lesen(db, user, "pfad_auflisten", ziel)
    try:
        return admin_path_service.auflisten(ziel)
    except admin_path_service.PfadFehler as exc:
        raise AgentFehler(str(exc)) from exc


def _lies_pfad_datei(db: Session, user: User, _server, p: dict) -> dict:
    from app.services import admin_path_service

    ziel = _pfad_ziel(db, p, zweck="lesen", erwarte="datei")
    _protokolliere_lesen(db, user, "pfad_lesen", ziel)
    try:
        return admin_path_service.lesen(ziel)
    except admin_path_service.PfadFehler as exc:
        raise AgentFehler(str(exc)) from exc


def _tue_pfad_schreiben(db: Session, user: User, _server, p: dict) -> dict:
    from app.services import admin_path_service

    ziel = _pfad_ziel(db, p, zweck="schreiben", muss_existieren=False, erwarte="datei")
    try:
        return admin_path_service.schreiben(ziel, p.get("inhalt") or "")
    except admin_path_service.PfadFehler as exc:
        raise AgentFehler(str(exc)) from exc


def _tue_pfad_loeschen(db: Session, user: User, _server, p: dict) -> dict:
    from app.services import admin_path_service

    ziel = _pfad_ziel(db, p, zweck="loeschen")
    try:
        return admin_path_service.loeschen(ziel, rekursiv=_ja(p.get("rekursiv")))
    except admin_path_service.PfadFehler as exc:
        raise AgentFehler(str(exc)) from exc


def _tue_pfad_verschieben(db: Session, user: User, _server, p: dict) -> dict:
    from app.services import admin_path_service

    # Die QUELLE wird mit dem Zweck "loeschen" geprueft: verschieben nimmt sie von ihrem
    # Platz weg, und das ist fuer alles, was danach dort sucht, dasselbe wie loeschen.
    quelle = _pfad_ziel(db, p, "quelle", zweck="loeschen")
    ziel = _pfad_ziel(db, p, "ziel", zweck="schreiben", muss_existieren=False)
    try:
        return admin_path_service.verschieben(quelle, ziel)
    except admin_path_service.PfadFehler as exc:
        raise AgentFehler(str(exc)) from exc


def _pruefe_pfad_schreiben(db: Session, user: User, server, p: dict) -> None:
    """Die Pfadregel schon bei der VORLAGE anwenden.

    Sonst liest der Nutzer eine Bestaetigungsfrage zu einer Tat, die danach an der Regel
    scheitert - dieselbe Ueberlegung wie bei einem Konsolenbefehl, der Admin braucht.
    """
    _pfad_ziel(db, p, zweck="schreiben", muss_existieren=False, erwarte="datei")


def _pruefe_pfad_loeschen(db: Session, user: User, server, p: dict) -> None:
    _pfad_ziel(db, p, zweck="loeschen")


def _pruefe_pfad_verschieben(db: Session, user: User, server, p: dict) -> None:
    _pfad_ziel(db, p, "quelle", zweck="loeschen")
    _pfad_ziel(db, p, "ziel", zweck="schreiben", muss_existieren=False)


# Hoechstens so viele Eintraege eines Archivs auf einmal: ein Modpack-Jar hat
# zehntausende, und das Inhaltsverzeichnis allein waere teurer als jede Antwort darauf.
ARCHIV_MAX_EINTRAEGE = 200
# Und hoechstens so viel aus einer Datei darin. In Bytes gelesen, nicht in Zeichen:
# bei einem Zip-Bomb-Eintrag entscheidet, wie viel ENTPACKT wird, nicht wie viel ankommt.
ARCHIV_MAX_BYTES = 32_000
ARCHIV_MAX_ZEICHEN = 8_000

# Wo Archive liegen. Keine Suche ueber den ganzen Serverordner: dort stehen die
# Weltdaten, und ein rglob darueber kostet bei grossen Welten Sekunden fuer nichts.
ARCHIV_MUSTER = ("*.jar", "*.zip", "plugins/*.jar", "plugins/*/*.jar", "mods/*.jar",
                 "datapacks/*.zip", "*/datapacks/*.zip", "*/datapacks/*/*.zip")


def _archive_finden(server: Server) -> list[str]:
    """Die Archive des Servers - der Entdeckungsschritt zu 'pfad'.

    Noetig, weil dateien_auflisten nur TEXT-Dateien zeigt: ohne das hier muesste der
    Assistent Pfade raten, und ein geratener Pfad kostet eine Runde fuer nichts.
    """
    from app.services.file_service import _server_base_path

    try:
        basis = _server_base_path(server)
    except Exception:  # noqa: BLE001
        return []
    try:
        wurzel = basis.resolve()
    except OSError:
        return []
    gefunden: set[str] = set()
    for muster in ARCHIV_MUSTER:
        try:
            for datei in basis.glob(muster):
                if not datei.is_file():
                    continue
                # Aufgeloest pruefen: glob folgt Abzweigungen. Ein "plugins/update",
                # das auf ein anderes Laufwerk zeigt, haette sonst die Namen fremder
                # Archive in die Liste gebracht - lesen liesse sie sich nicht, aber
                # genannt waeren sie.
                try:
                    echt = datei.resolve()
                except OSError:
                    continue
                if echt != wurzel and wurzel not in echt.parents:
                    continue
                gefunden.add(datei.relative_to(basis).as_posix())
                if len(gefunden) >= ARCHIV_MAX_EINTRAEGE:
                    return sorted(gefunden)
        except OSError:
            continue
    return sorted(gefunden)


def _tue_archiv_ansehen(db: Session, user: User, server: Server | None, p: dict) -> dict:
    """In ein Archiv sehen: Inhaltsverzeichnis oder eine Datei daraus.

    Zwei Wege, eine Funktion: serverrelativ (Admin, wie bisher) und an einem freien Pfad
    (Super-Admin). Der Katalog fuehrt dieses Werkzeug ohne Serverpflicht, weil es fuer den
    zweiten Weg keinen Server gibt - deshalb wird er hier geholt und geprueft, mit
    derselben Pruefung, die der Katalog sonst uebernimmt.
    """
    from app.services.file_service import resolve_server_path

    if server is None and p.get("server_id") is not None:
        server = _server_holen(db, user, p.get("server_id"))
        _recht_pruefen(db, user, server, "ansehen")

    pfad = str(p.get("pfad") or "").strip()
    eintrag = str(p.get("eintrag") or "").strip().replace(chr(92), "/")
    if not pfad:
        if server is None:
            raise AgentFehler("Ohne server_id braucht es den vollen Pfad des Archivs.")
        archive = _archive_finden(server)
        return {"archive": archive,
                "hinweis": ("Mit 'pfad' eines dieser Archive oeffnen."
                            if archive else
                            "In diesem Serverordner liegt kein Archiv.")}

    if _ist_freier_pfad(pfad):
        # Ein vollstaendiger Pfad verlaesst den Serverordner - das ist Super-Admin-Sache
        # und laeuft durch dieselbe Regel wie die uebrigen Pfadwerkzeuge. Genau dafuer
        # gibt es den Fall: das gehostete Resource Pack liegt unter data/resourcepacks,
        # also ausserhalb jedes Serverordners.
        _recht_pruefen(db, user, None, "super_admin")
        ziel = _pfad_ziel(db, p, zweck="lesen", erwarte="datei")
        anzeige = str(ziel)
        _protokolliere_lesen(db, user, "archiv_ansehen", ziel)
        return _archiv_oeffnen(ziel, anzeige, eintrag)

    if server is None:
        raise AgentFehler("Ohne server_id muss der Pfad vollstaendig sein, z.B. "
                          "'data/resourcepacks/vt_1_abc.zip'.")
    try:
        # Derselbe Haken wie beim Dateilesen: kein Ausbruch aus dem Serverordner, kein
        # absoluter Pfad. Nur die Textdatei-Pruefung entfaellt - ein Jar ist keine.
        ziel = resolve_server_path(server, pfad, must_exist=True, expect_file=True)
    except ValueError as exc:
        # Bei einem geratenen Pfad gleich die echten nennen, statt eine Runde zu kosten.
        gesucht = pfad.replace(chr(92), "/").rsplit("/", 1)[-1].lower()
        passend = [a for a in _archive_finden(server)
                   if a.rsplit("/", 1)[-1].lower() == gesucht]
        raise AgentFehler(str(exc) + (f" Gemeint war vielleicht: {', '.join(passend[:5])}."
                                      if passend else "")) from exc

    anzeige = pfad.replace(chr(92), "/")
    return _archiv_oeffnen(ziel, anzeige, eintrag)


def _ist_freier_pfad(pfad: str) -> bool:
    """Ob das ein Pfad AUSSERHALB des Serverordners sein will.

    'data/...' relativ zum Programmordner zaehlt dazu: ein Serverordner hat kein data/,
    und wer so etwas angibt, meint den Manager. Alles andere bleibt serverrelativ,
    damit 'plugins/X.jar' weiter das Naheliegende tut.
    """
    gerade = pfad.replace(chr(92), "/")
    if gerade.startswith("/") or gerade.startswith("~"):
        return True
    if len(gerade) > 2 and gerade[1] == ":":
        return True
    return gerade.split("/", 1)[0].lower() in {"data", "managed_servers"}


# Hoechstens so lang darf ein Eintragsname im Inhaltsverzeichnis sein. Ein Zip darf
# Namen bis 64 KB fuehren; 200 davon waeren 12 MB JSON, gebaut um auf 12 KB gekuerzt zu
# werden.
ARCHIV_MAX_NAME = 300

# Nur diese beiden Verfahren werden gelesen. Begruendung steht in _archiv_oeffnen.
_LESBARE_VERFAHREN = (0, 8)  # ZIP_STORED, ZIP_DEFLATED


def _archiv_oeffnen(ziel, anzeige: str, eintrag: str) -> dict:
    """Inhaltsverzeichnis oder einen Eintrag - begrenzt, und ohne das Archiv zu entpacken."""
    import zipfile

    try:
        with zipfile.ZipFile(ziel) as z:
            namen = z.namelist()
            if not eintrag:
                return {
                    "pfad": anzeige,
                    "eintraege_gesamt": len(namen),
                    "eintraege": [n[:ARCHIV_MAX_NAME]
                                  for n in namen[:ARCHIV_MAX_EINTRAEGE]],
                    "gekuerzt": len(namen) > ARCHIV_MAX_EINTRAEGE,
                    "hinweis": ("Mit 'eintrag' eine einzelne Datei daraus lesen, z.B. "
                                "plugin.yml, pack.mcmeta oder 'Selected Packs.txt'."),
                }
            if eintrag not in namen:
                treffer = [n for n in namen if n.lower().endswith(eintrag.lower())][:5]
                raise AgentFehler(
                    f"'{eintrag}' steht nicht in {anzeige}."
                    + (f" Gemeint war vielleicht: {', '.join(treffer)}." if treffer
                       else " Ohne 'eintrag' kommt das Inhaltsverzeichnis."))
            angaben = z.getinfo(eintrag)
            if eintrag.endswith("/") or angaben.is_dir():
                raise AgentFehler(f"'{eintrag}' ist ein Ordner im Archiv, keine Datei.")
            # Die Grenze beim Lesen wirkt nur bei STORED und DEFLATE. Nachgelesen in
            # zipfile.ZipExtFile._read1 (3.12): fuer Deflate laeuft
            # decompress(data, n) - begrenzt -, fuer JEDES andere Verfahren
            # decompress(data) OHNE Grenze, und gefuettert wird mit 32 KB komprimiert.
            # Gemessen ergeben 32 KB bzip2 rund 43 GB, 32 KB lzma 224 MB - in EINEM
            # bytes-Objekt, bevor irgendeine Kappung greift. Da die Route im Event-Loop
            # laeuft, friert das nicht die Anfrage ein, sondern den ganzen Manager.
            if angaben.compress_type not in _LESBARE_VERFAHREN:
                raise AgentFehler(
                    f"'{eintrag}' ist mit Verfahren {angaben.compress_type} gepackt "
                    f"(nicht Deflate oder unkomprimiert). Solche Eintraege liest der "
                    f"Manager nicht: dabei liesse sich die Groessengrenze umgehen.")
            with z.open(eintrag) as f:
                rohdaten = f.read(ARCHIV_MAX_BYTES + 1)
    except AgentFehler:
        raise
    except Exception as exc:  # noqa: BLE001
        # Absichtlich breit. Das Archiv kommt von aussen, und ein Fuzz ueber verfaelschte
        # Zips lieferte neben BadZipFile/OSError auch zlib.error (46 von 60 Laeufen),
        # ValueError ("negative seek value") und UnicodeDecodeError aus dem Oeffnen -
        # alles Ausnahmen, die keine gemeinsame Oberklasse haben. Fuer den Nutzer
        # bedeuten sie dasselbe: die Datei ist nicht lesbar.
        raise AgentFehler(f"{anzeige} konnte nicht gelesen werden: "
                          f"{type(exc).__name__}: {exc}") from exc

    nutzdaten = rohdaten[:ARCHIV_MAX_BYTES]
    if b"\x00" in nutzdaten[:8192]:
        raise AgentFehler(
            f"'{eintrag}' ist keine Textdatei. Als Text gelesen ergibt das Muell, und "
            f"an das Modell gingen tausende Ersatzzeichen.")
    text = nutzdaten.decode("utf-8", errors="replace")
    gekuerzt = len(rohdaten) > ARCHIV_MAX_BYTES or len(text) > ARCHIV_MAX_ZEICHEN
    ergebnis = {"pfad": anzeige, "eintrag": eintrag,
                "inhalt": text[:ARCHIV_MAX_ZEICHEN], "gekuerzt": gekuerzt}
    if gekuerzt:
        ergebnis["hinweis"] = "Nur der Anfang der Datei."
    return ergebnis


def _vt_eintrag(db: Session, server: Server, p: dict):
    """Den Inhaltseintrag zu einer ID holen - und zwar nur einen mit Teilpaketen."""
    from app.models.installed_content import InstalledContent
    from app.services import vanillatweaks_service as vt

    try:
        kennung = int(p.get("inhalt_id") or 0)
    except (TypeError, ValueError):
        kennung = 0
    eintrag = db.get(InstalledContent, kennung) if kennung else None
    # Die Zugehoerigkeit zum Server pruefen, nicht nur die ID: sonst liesse sich ueber
    # einen Server, den man steuern darf, ein Eintrag eines anderen anfassen.
    if eintrag is None or eintrag.server_id != server.id:
        raise AgentFehler(f"Einen Inhalt mit der ID {p.get('inhalt_id')!r} gibt es auf "
                          f"'{server.name}' nicht.")
    if not vt.auswahl_typ(eintrag):
        raise AgentFehler(
            f"'{eintrag.name}' buendelt keine VanillaTweaks-Teilpakete. Das gibt es nur "
            f"beim Resource Pack und bei den Crafting Tweaks - einzelne Datapacks fuehrt "
            f"der Manager als je eigenen Eintrag, die lassen sich direkt entfernen.")
    return eintrag


def _tue_vt_auswahl_zeigen(db: Session, user: User, server: Server, p: dict) -> dict:
    from app.services import vanillatweaks_service as vt

    eintrag = _vt_eintrag(db, server, p)
    stand = vt.auswahl_lesen(server, eintrag) or {}
    raus = {"name": eintrag.name, "inhalt_id": eintrag.id, "typ": stand.get("typ", ""),
            "version": stand.get("version", ""),
            "zielversion": stand.get("zielversion", ""),
            "gewaehlt": stand.get("gewaehlt", []),
            "unbekannt": stand.get("unbekannt", []),
            "datei": stand.get("datei", ""),
            "lesbar": bool(stand.get("lesbar"))}
    if stand.get("hinweis"):
        raus["hinweis"] = stand["hinweis"]
    try:
        # Gegen die ZIELGRUPPE: mit der wird neu erzeugt, also muessen die waehlbaren
        # Namen aus ihr kommen. Sonst bestaetigt der Nutzer einen Namen aus dem alten
        # Katalog, und die Ausfuehrung weist ihn als unbekannt ab.
        katalog, hinweis = vt.verfuegbare_packs(
            stand.get("typ") or "resourcepacks",
            stand.get("zielversion") or stand.get("version") or "",
            str(p.get("suche") or ""))
        raus["verfuegbar"] = katalog
        if hinweis:
            raus["verfuegbar_hinweis"] = hinweis
    except Exception as exc:  # noqa: BLE001 - ohne Katalog bleibt die Auswahl lesbar
        raus["verfuegbar_fehler"] = f"Der Katalog war nicht erreichbar: {exc}"
    return raus


def _vt_namen(p: dict, feld: str) -> list[str]:
    """Eine Namensliste aus den Parametern - das Modell schickt Liste ODER Fliesstext."""
    wert = p.get(feld)
    if isinstance(wert, str):
        wert = wert.replace(";", ",").split(",")
    return [str(x).strip() for x in (wert or []) if str(x).strip()]


def _vt_neue_auswahl(db: Session, server: Server, p: dict):
    """Die kuenftige Auswahl aus dem Istzustand plus 'hinzufuegen'/'entfernen'.

    Zweimal gebraucht: fuer die Beschreibung im Bestaetigungsdialog und fuer die
    Ausfuehrung - beide muessen dieselbe Liste nennen.
    """
    from app.services import vanillatweaks_service as vt

    eintrag = _vt_eintrag(db, server, p)
    stand = vt.auswahl_lesen(server, eintrag) or {}
    namen = list(stand.get("gewaehlt") or [])
    dazu = _vt_namen(p, "hinzufuegen")

    # "Leere Auswahl" und "Auswahl nicht lesbar" sehen gleich aus und bedeuten das
    # Gegenteil: auf einer unlesbaren Grundlage ERSETZT ein Hinzufuegen die ganze
    # Zusammenstellung, statt sie zu ergaenzen. Aus 21 Teilpaketen wird dann eines -
    # und die Nachwirkung des Werkzeugs sagt selbst, dass das nur aus einer Sicherung
    # zurueckzuholen ist.
    if dazu and not stand.get("lesbar"):
        raise AgentFehler(
            "Die bisherige Auswahl laesst sich nicht lesen"
            + (f" ({stand['hinweis']})" if stand.get("hinweis") else "")
            + ". Etwas hinzuzufuegen wuerde sie nicht ergaenzen, sondern durch die "
              "genannten Teilpakete ersetzen. Erst mit vt_auswahl_zeigen klaeren, was "
              "wirklich drin ist.")

    # Verglichen wird ueber den Namensschluessel, nicht per .lower(): das Manifest fuehrt
    # interne Namen ("WavyWater"), Nutzer und Katalog-Anzeige den Namen mit Leerzeichen
    # ("Wavy Water"). Bei Resource Packs weichen name und display in 670 von 672 Faellen
    # ab - mit .lower() entfernte "weg: Wavy Water" nichts, und der Satz im Dialog sagte
    # trotzdem, es sei entfernt.
    weg = {vt.namensschluessel(n) for n in _vt_namen(p, "entfernen")}
    namen = [n for n in namen if vt.namensschluessel(n) not in weg]
    for neu in dazu:
        if vt.namensschluessel(neu) not in {vt.namensschluessel(n) for n in namen}:
            namen.append(neu)
    return eintrag, namen


def _tue_vt_auswahl_setzen(db: Session, user: User, server: Server, p: dict) -> dict:
    from app.services import vanillatweaks_service as vt

    eintrag, namen = _vt_neue_auswahl(db, server, p)
    try:
        ok, meldung = vt.auswahl_setzen(db, server, eintrag, namen, user.id)
    except ValueError as exc:
        raise AgentFehler(str(exc)) from exc
    if not ok:
        raise AgentFehler(meldung)
    return {"meldung": meldung, "gewaehlt": namen}


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


def _pfad_fuer_satz(db: Session, p: dict, feld: str = "pfad") -> str:
    """Der aufgeloeste Pfad fuer den Bestaetigungssatz - oder der eingegebene.

    Ein Pfad, der die Regel nicht besteht, erscheint im Vorschlag schon als Hindernis
    (siehe _pruefe_pfad_*). Hier soll derselbe Fehler den Satz nicht sprengen.
    """
    from app.services import admin_path_service

    roh = str(p.get(feld) or "").strip()
    try:
        return str(admin_path_service.pruefe(roh, db=db, zweck="lesen",
                                             muss_existieren=False))
    except Exception:  # noqa: BLE001
        return roh or "(kein Pfad)"


def _b_pfad_schreiben(db: Session, server: Server | None, p: dict) -> str:
    from pathlib import Path as _Pfad

    pfad = _pfad_fuer_satz(db, p)
    inhalt = str(p.get("inhalt") or "")
    zusatz = ""
    try:
        vorhanden = _Pfad(pfad)
        if vorhanden.is_file():
            zusatz = (f" Die Datei gibt es schon ({vorhanden.stat().st_size} Bytes) und "
                      f"wird vollstaendig ersetzt.")
    except OSError:
        pass
    return f"'{pfad}' schreiben, {len(inhalt)} Zeichen.{zusatz}"


def _b_pfad_loeschen(db: Session, server: Server | None, p: dict) -> str:
    from pathlib import Path as _Pfad

    from app.services import admin_path_service

    pfad = _pfad_fuer_satz(db, p)
    try:
        ziel = _Pfad(pfad)
        if ziel.is_dir():
            anzahl = admin_path_service.zaehle(ziel)
            return (f"Den Ordner '{pfad}' loeschen - mit {anzahl} Eintraegen darunter."
                    if anzahl else f"Den leeren Ordner '{pfad}' loeschen.")
    except OSError:
        pass
    return f"Die Datei '{pfad}' loeschen."


def _b_pfad_verschieben(db: Session, server: Server | None, p: dict) -> str:
    return (f"'{_pfad_fuer_satz(db, p, 'quelle')}' nach "
            f"'{_pfad_fuer_satz(db, p, 'ziel')}' verschieben.")


def _pruefe_vt_auswahl(db: Session, user: User, server: Server, p: dict) -> None:
    """Die Parameter schon bei der VORLAGE pruefen.

    Vorher fing die Beschreibung den Fehler ab und gab ihn als TATBESCHREIBUNG zurueck.
    Der Dialog titelte dann "Vorschlag", setzte den Fehlersatz als das ein, was passieren
    soll, und bot "Ausfuehren" aktiv an - ausgefuehrt wurde nichts, aber die Zusage "der
    Bestaetigungssatz kommt vom Manager und beschreibt die Tat" galt nicht mehr.
    """
    _vt_neue_auswahl(db, server, p)


def _b_vt_auswahl_setzen(db: Session, server: Server, p: dict) -> str:
    # Hier wird NICHT mehr gefangen: _baue_vorschlag ruft die Beschreibung innerhalb
    # seines try auf, eine Ausnahme wird dort also zum Hindernis - genau richtig.
    eintrag, namen = _vt_neue_auswahl(db, server, p)
    weg = _vt_namen(p, "entfernen")
    dazu = _vt_namen(p, "hinzufuegen")
    teile = []
    if dazu:
        teile.append("dazu: " + ", ".join(dazu))
    if weg:
        teile.append("weg: " + ", ".join(weg))
    return (f"'{eintrag.name}' neu erzeugen mit {len(namen)} Teilpaket(en)"
            + (" (" + "; ".join(teile) + ")" if teile else "") + ".")


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
                 "'whitelist reload'. 'op', 'deop', 'pardon' und 'whitelist off' "
                 "verlangen Admin-Rechte - ein Moderator kann sie nicht ausfuehren.",
    felder={"befehl": {"type": "string",
                       "description": "Der Befehl ohne fuehrenden Schraegstrich"}},
    pflicht=["befehl"], ausfuehren=_tue_befehl, beschreibe=_b_befehl,
    zusatzpruefung=_pruefe_konsolenbefehl,
    nachwirkung="Ein Konsolenbefehl wirkt sofort und laesst sich nicht zurueckdrehen. "
                "Lies ihn Wort fuer Wort, bevor du bestaetigst."))

_eintragen(Werkzeug(
    name="einstellung_setzen", lesend=False, recht="dateien", gefahr="mittel",
    beschreibung="Einen Wert in server.properties aendern. Strukturelle Schluessel "
                 "(level-name, Ports, online-mode, level-seed) sind gesperrt.",
    felder={"schluessel": {"type": "string"}, "wert": {"type": "string"}},
    pflicht=["schluessel", "wert"], ausfuehren=_tue_einstellung,
    beschreibe=_b_einstellung,
    zusatzpruefung=_pruefe_einstellung_rechte,
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


_eintragen(Werkzeug(
    name="inhalt_suchen", lesend=True, recht="ansehen",
    beschreibung="Ein Plugin, eine Mod oder ein Datapack bei Modrinth und CurseForge "
                 "suchen - gefiltert auf Version und Loader DIESES Servers. Liefert "
                 "Anbieter und Projekt-ID, die zum Installieren gebraucht werden.",
    felder={"begriff": {"type": "string", "description": "Name oder Teil des Namens"},
            "typ": {"type": "string",
                    "enum": ["plugin", "mod", "datapack", "modpack", "resourcepack"]}},
    pflicht=["begriff"], ausfuehren=_lies_inhalt_suchen))

_eintragen(Werkzeug(
    name="versionen_auflisten", lesend=True, recht="ansehen",
    beschreibung="Welche Fassungen eines Projekts passen zu diesem Server? Liefert die "
                 "Version-ID, die zum Installieren gebraucht wird.",
    felder={"anbieter": {"type": "string", "enum": ["modrinth", "curseforge"]},
            "projekt_id": {"type": "string"},
            "typ": {"type": "string",
                    "enum": ["plugin", "mod", "datapack", "modpack", "resourcepack"]}},
    pflicht=["anbieter", "projekt_id"], ausfuehren=_lies_versionen))

_eintragen(Werkzeug(
    name="inhalt_installieren", lesend=False, recht="steuern", gefahr="mittel",
    beschreibung="Ein Plugin/eine Mod/ein Datapack installieren. Projekt- und Version-ID "
                 "vorher mit inhalt_suchen und versionen_auflisten ermitteln.",
    felder={"anbieter": {"type": "string", "enum": ["modrinth", "curseforge"]},
            "projekt_id": {"type": "string"}, "version_id": {"type": "string"},
            "typ": {"type": "string",
                    "enum": ["plugin", "mod", "datapack", "modpack", "resourcepack"]}},
    pflicht=["anbieter", "projekt_id", "version_id"],
    ausfuehren=_tue_inhalt_installieren, beschreibe=_b_inhalt_installieren,
    zusatzpruefung=_pruefe_inhalt_rechte,
    nachwirkung="Legt eine Datei im plugins-/mods-Ordner an. Wirksam beim naechsten "
                "Start; Abhaengigkeiten werden bei Mods mitinstalliert."))

_eintragen(Werkzeug(
    name="inhalt_entfernen", lesend=False, recht="steuern", gefahr="hoch",
    beschreibung="Ein installiertes Plugin/eine Mod/ein Datapack entfernen. Die ID kommt "
                 "aus inhalte_auflisten.",
    felder={"inhalt_id": {"type": "integer", "description": "ID aus inhalte_auflisten"}},
    pflicht=["inhalt_id"], ausfuehren=_tue_inhalt_entfernen,
    beschreibe=_b_inhalt_entfernen, zusatzpruefung=_pruefe_entfernen_rechte,
    nachwirkung="Die Datei wird geloescht. Was ein Plugin in der Welt angelegt hat "
                "(Konfiguration, Daten, gesetzte Bloecke) bleibt liegen und kann ohne das "
                "Plugin unbrauchbar oder fehlerhaft sein. Vorher sichern."))


_PFAD_FELD = {"type": "string",
              "description": "Vollstaendiger Pfad, z.B. 'C:/mc/data/logs/app.log'. "
                             "Ohne Laufwerk zaehlt der Pfad vom Programmordner des "
                             "Managers, also 'data/logs/app.log'."}

_eintragen(Werkzeug(
    name="pfad_auflisten", lesend=True, recht="super_admin", braucht_server=False,
    beschreibung="Einen Ordner irgendwo auf dem Managerrechner auflisten - auch "
                 "ausserhalb der Serverordner, z.B. data/resourcepacks oder "
                 "data/downloads. Nur fuer Super-Admins.",
    felder={"pfad": _PFAD_FELD}, pflicht=["pfad"], ausfuehren=_lies_pfad_liste))

_eintragen(Werkzeug(
    name="pfad_lesen", lesend=True, recht="super_admin", braucht_server=False,
    beschreibung="Eine Textdatei irgendwo auf dem Managerrechner lesen - auch ausserhalb "
                 "der Serverordner. Nur fuer Super-Admins. Geheimnisse (.env, Datenbank, "
                 "Schluesselspeicher) bleiben auch dann zu.",
    felder={"pfad": _PFAD_FELD}, pflicht=["pfad"], ausfuehren=_lies_pfad_datei))

_eintragen(Werkzeug(
    name="pfad_schreiben", lesend=False, recht="super_admin", braucht_server=False,
    gefahr="hoch", ausfuehren=_tue_pfad_schreiben, beschreibe=_b_pfad_schreiben,
    zusatzpruefung=_pruefe_pfad_schreiben,
    beschreibung="Eine Textdatei irgendwo auf dem Managerrechner anlegen oder ersetzen. "
                 "Fehlende Ordner werden angelegt. Eine bestehende Datei behaelt ihre "
                 "Kodierung. In den Programmcode des Managers fuehrt kein Weg.",
    felder={"pfad": _PFAD_FELD,
            "inhalt": {"type": "string",
                       "description": "Der vollstaendige neue Inhalt der Datei"}},
    pflicht=["pfad", "inhalt"],
    nachwirkung="Der bisherige Inhalt der Datei ist danach weg - vollstaendig, nicht "
                "ergaenzt. Der Manager legt dafuer keine Sicherung an. Fehlende Ordner "
                "im Pfad werden mit angelegt."))

_eintragen(Werkzeug(
    name="pfad_loeschen", lesend=False, recht="super_admin", braucht_server=False,
    gefahr="hoch", ausfuehren=_tue_pfad_loeschen, beschreibe=_b_pfad_loeschen,
    zusatzpruefung=_pruefe_pfad_loeschen,
    beschreibung="Eine Datei oder einen Ordner auf dem Managerrechner loeschen. Ordner "
                 "nur mit rekursiv=true. Serverordner, Geheimnisse und alles dicht unter "
                 "einer Laufwerkswurzel bleiben geschuetzt.",
    felder={"pfad": _PFAD_FELD,
            "rekursiv": {"type": "boolean",
                         "description": "Auch einen Ordner mit Inhalt loeschen"}},
    pflicht=["pfad"],
    nachwirkung="Geloescht ist geloescht - der Manager legt dafuer keine Sicherung an "
                "und der Windows-Papierkorb wird nicht benutzt. Bei einem Ordner geht "
                "alles darunter mit."))

_eintragen(Werkzeug(
    name="pfad_verschieben", lesend=False, recht="super_admin", braucht_server=False,
    gefahr="mittel", ausfuehren=_tue_pfad_verschieben, beschreibe=_b_pfad_verschieben,
    zusatzpruefung=_pruefe_pfad_verschieben,
    beschreibung="Eine Datei oder einen Ordner verschieben oder umbenennen. Das Ziel "
                 "muss frei sein - ueberschrieben wird nichts.",
    felder={"quelle": _PFAD_FELD, "ziel": _PFAD_FELD},
    pflicht=["quelle", "ziel"],
    nachwirkung="Am alten Ort liegt danach nichts mehr. Was den alten Pfad kennt - "
                "Startskripte, Konfigurationen, geplante Aufgaben - findet die Datei "
                "nicht mehr."))

_eintragen(Werkzeug(
    name="archiv_ansehen", lesend=True, recht="ansehen", nur_admin=True,
    braucht_server=False,
    beschreibung="In ein Archiv des Servers hineinsehen, ohne es zu entpacken: ein "
                 "Datapack-Zip, ein Plugin- oder Mod-Jar, ein Resource Pack. Ohne "
                 "'pfad' kommt die Liste der vorhandenen Archive (dateien_auflisten "
                 "zeigt nur Textdateien), ohne 'eintrag' das Inhaltsverzeichnis, mit "
                 "'eintrag' der Inhalt einer "
                 "Datei daraus (plugin.yml, pack.mcmeta, 'Selected Packs.txt', ...). "
                 "Nur fuer Admins; ein Pfad ausserhalb des Serverordners nur fuer "
                 "Super-Admins.",
    felder={"server_id": {"type": "integer",
                          "description": "Server, in dessen Ordner gesucht wird; weglassen "
                                         "bei einem vollstaendigen Pfad"},
            "pfad": {"type": "string",
                     "description": "Pfad des Archivs relativ zum Serverordner, z.B. "
                                    "'plugins/EssentialsX.jar'; leer lassen, um die "
                                    "Archive des Servers aufzulisten. Ein vollstaendiger "
                                    "Pfad (z.B. 'data/resourcepacks/vt_1_abc.zip') geht "
                                    "auch, dafuer braucht es Super-Admin-Rechte"},
            "eintrag": {"type": "string",
                        "description": "Datei im Archiv; leer lassen fuer das "
                                       "Inhaltsverzeichnis"}},
    pflicht=[], ausfuehren=_tue_archiv_ansehen))

_eintragen(Werkzeug(
    name="vt_auswahl_zeigen", lesend=True, recht="ansehen", nur_admin=True,
    beschreibung="Welche Teilpakete stecken in einem VanillaTweaks-Paket (Resource Pack "
                 "oder Crafting Tweaks), und welche gibt es fuer diese Version? Die ID "
                 "kommt aus inhalte_auflisten. Nur fuer Admins.",
    felder={"inhalt_id": {"type": "integer", "description": "ID aus inhalte_auflisten"},
            "suche": {"type": "string",
                      "description": "Nur Teilpakete nennen, deren Name dazu passt - "
                                     "der Katalog hat ueber 700 Eintraege"}},
    pflicht=["inhalt_id"], ausfuehren=_tue_vt_auswahl_zeigen))

_eintragen(Werkzeug(
    name="vt_auswahl_setzen", lesend=False, recht="steuern", gefahr="mittel",
    nur_admin=True, ausfuehren=_tue_vt_auswahl_setzen,
    beschreibe=_b_vt_auswahl_setzen, zusatzpruefung=_pruefe_vt_auswahl,
    beschreibung="Teilpakete eines VanillaTweaks-Pakets hinzufuegen oder entfernen. Die "
                 "bisherige Auswahl bleibt bestehen, soweit sie nicht in 'entfernen' "
                 "steht. Namen vorher mit vt_auswahl_zeigen pruefen. Nur fuer Admins.",
    felder={"inhalt_id": {"type": "integer", "description": "ID aus inhalte_auflisten"},
            "hinzufuegen": {"type": "array", "items": {"type": "string"},
                            "description": "Namen aus dem Katalog, z.B. 'WavyWater'"},
            "entfernen": {"type": "array", "items": {"type": "string"},
                          "description": "Namen, die aus der Auswahl fallen"}},
    pflicht=["inhalt_id"],
    nachwirkung="Das Paket wird bei VanillaTweaks neu erzeugt und ersetzt die alte Datei. "
                "Beim Resource Pack laedt es jeder Spieler beim naechsten Beitritt neu; "
                "bei den Crafting Tweaks wirkt es ab dem naechsten Start. Die alte "
                "Zusammenstellung ist danach nur ueber eine Sicherung zurueckzuholen."))


def werkzeug_schemas() -> list[dict]:
    return [w.schema() for w in WERKZEUGE.values()]


# ---------------------------------------------------------------------- Auftrag

AUFTRAG_ZUSATZ = (
    "Du arbeitest IM Manager eines Minecraft-Servernetzwerks und kannst nachsehen, bevor "
    "du antwortest.\n"
    "\n"
    "WICHTIGSTE REGEL: Beantworte GENAU das, was der Nutzer will - nichts daneben. Hat er "
    "einen Auftrag gegeben ('installiere X', 'starte Y'), dann arbeite diesen Auftrag ab. "
    "Liefere KEINE unaufgeforderte Bestandsaufnahme, keine Liste von Verbesserungen und "
    "keine Hinweise zu Dingen, nach denen niemand gefragt hat. Faellt dir dabei etwas "
    "Wichtiges auf, nenne es in EINEM Satz am Ende und frage, ob du darauf eingehen "
    "sollst.\n"
    "\n"
    "Nutze die lesenden Werkzeuge, statt zu raten oder nach Dingen zu fragen, die du "
    "selbst nachsehen kannst - aber nur die, die du fuer DIESEN Auftrag brauchst.\n"
    "\n"
    "Fuer alles, was etwas VERAENDERT, rufe das passende Werkzeug auf. Es wird nicht "
    "sofort ausgefuehrt, sondern dem Nutzer zur Bestaetigung vorgelegt. Rufe hoechstens "
    "ein veraenderndes Werkzeug auf einmal auf und schreibe davor in einem Satz, warum. "
    "Besteht der Auftrag aus mehreren Schritten (installieren, dann neu starten), nenne "
    "den Plan kurz und schlage den ERSTEN Schritt vor; die weiteren kommen nach der "
    "Bestaetigung.\n"
    "\n"
    "Gibt es fuer das Verlangte kein Werkzeug, sage das in einem Satz, nenne was du "
    "stattdessen tun kannst, und hoere auf. Weiche NICHT auf etwas anderes aus - eine "
    "Antwort, die am Auftrag vorbeigeht, ist schlechter als ein klares 'das kann ich "
    "nicht'. Dasselbe gilt fuer fehlende Rechte.\n"
    "\n"
    "Antworte knapp und auf Deutsch. Nenne Server mit Namen, nicht mit ID. Du darfst "
    "Markdown benutzen (Ueberschriften, Listen, **fett**, `code`)."
)


def _saeubere_werte(wert):
    """Jeden Text im Werkzeugergebnis saeubern, BEVOR er in JSON verpackt wird.

    ai_service saeubert den tool_result-Text ebenfalls - aber erst, wenn er EINE
    JSON-Zeile ist. Die Geheimnis-Muster haengen an ``(?im)^``, und ein Zeilenanfang
    kommt dort nicht mehr vor: die Umbrueche sind im Blob die zwei Zeichen Backslash
    und n. Gemessen wurde 'password: "hunter2"' im Rohtext entfernt und im Blob nicht;
    ebenso fraesst das Inline-Muster im Blob den Rest der Datei auf, weil Backslash-n
    dort kein Leerraum ist. Deshalb hier, am Rohtext, wo die Muster greifen.

    ``namen_ersetzen`` bleibt aus: Spielernamen gehen nach ausdruecklicher Entscheidung
    unveraendert mit, und die Namensersetzung erledigt ai_service anhand der
    Einstellung. Geheimnisse und Adressen dagegen gehen immer, unabhaengig von jeder
    Einstellung.
    """
    from app.services import ai_privacy_service

    if isinstance(wert, str):
        sauber, _ = ai_privacy_service.saeubere(wert, namen_ersetzen=False)
        return sauber
    if isinstance(wert, dict):
        return {schluessel: _saeubere_werte(unterwert)
                for schluessel, unterwert in wert.items()}
    if isinstance(wert, list):
        return [_saeubere_werte(eintrag) for eintrag in wert]
    return wert


def _ergebnis_text(daten: dict) -> str:
    text = json.dumps(_saeubere_werte(daten), ensure_ascii=False, default=str)
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
        if werkzeug.nur_admin:
            _pruefe_admin(db, user, server, eingabe)
        if werkzeug.zusatzpruefung is not None:
            werkzeug.zusatzpruefung(db, user, server, eingabe)
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
            # Die Folgefrage WIEDERHOLT den Auftrag. "Mach weiter mit dem, was du
            # jetzt weisst" war eine Aufforderung zum Zusammenfassen - das Modell hat
            # daraufhin eine Bestandsaufnahme geliefert statt den Auftrag abzuarbeiten.
            nachricht=nachricht if runde == 0 else
                      (f"Der Auftrag lautet weiterhin: {nachricht}\n"
                       f"Arbeite genau diesen Auftrag ab. Liefere keine "
                       f"Bestandsaufnahme zu anderen Dingen."),
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
        if werkzeug.nur_admin:
            _pruefe_admin(db, user, server, eingabe)
        if werkzeug.zusatzpruefung is not None:
            werkzeug.zusatzpruefung(db, user, server, eingabe)
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
    if werkzeug.nur_admin:
        _pruefe_admin(db, user, server, parameter or {})
    if werkzeug.zusatzpruefung is not None:
        werkzeug.zusatzpruefung(db, user, server, parameter or {})

    audit_service.log_action(
        db, action=f"ai.agent.{werkzeug.name}", user_id=user.id,
        server_id=server.id if server else None,
        details=json.dumps(parameter or {}, ensure_ascii=False, default=str)[:400])

    daten = werkzeug.ausfuehren(db, user, server, parameter or {})
    daten.setdefault("meldung", f"'{werkzeug.name}' ausgefuehrt.")
    daten["werkzeug"] = werkzeug.name
    return daten
