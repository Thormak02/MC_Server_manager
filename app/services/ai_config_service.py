"""Konfigurationsassistent: Einstellungen vorschlagen, nicht setzen.

Der Assistent bekommt die Datei, die tatsaechlichen Eckdaten des Servers (Typ, Version,
zugewiesener Speicher, Spielerzahl, Zahl der Mods, RAM des Rechners und was die anderen
Server davon schon belegen) und liefert einzelne Vorschlaege zurueck: Schluessel, neuer
Wert, Begruendung.

Zwei Dinge sind hier wichtiger als der Vorschlag selbst:

**Geschrieben wird nichts.** Die Vorschlaege landen in dem Formular, das es im
Datei-Assistenten schon gibt; gespeichert wird mit demselben Knopf wie bei einer
Handeingabe, ueber denselben Pfad mit derselben Pruefung. Der Mensch sieht die Werte im
Formular, bevor etwas auf der Platte landet.

**Manche Schluessel darf er nicht anfassen.** Nicht weil sie heikel klingen, sondern weil
ein falscher Wert dort echten Schaden anrichtet, den man nicht sieht:

* ``level-name`` zeigt auf den Weltordner. Ein anderer Name heisst: der Server erzeugt
  eine neue, leere Welt und die alte ist nur noch ein Ordner, den niemand mehr laedt.
* ``level-seed`` und ``level-type`` aendern die Weltgenerierung. Bei einer bestehenden
  Welt passen neu erzeugte Gebiete dann nicht mehr an die alten - sichtbar als Abbruchkante
  mitten in der Landschaft, nicht rueckgaengig zu machen.
* ``server-port``, ``server-ip``, ``query.port``, ``rcon.port`` verwaltet der Manager
  selbst (Gateway-Routing, Sleep-Proxy). Ein geaenderter Port nimmt dem Server die
  Erreichbarkeit oder kollidiert mit einem anderen Server.
* ``online-mode`` entscheidet, welche UUID ein Spieler bekommt. Ein Wechsel laesst jeden
  Spieler mit leerem Inventar an einem fremden Ort erscheinen, weil seine Spielerdatei
  unter der anderen UUID liegt.
* ``rcon.password`` ist ein Geheimnis und gehoert nicht in einen Vorschlag.

Alles andere ist erlaubt, aber was einen laufenden Betrieb merkbar veraendert, wird als
solches gekennzeichnet und im Formular NICHT vorgehakt.
"""

from __future__ import annotations

from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.server import Server
from app.models.user import User
from app.services import ai_service

# Schluessel, die der Assistent nie vorschlagen darf (Begruendung im Modul-Text oben).
GESPERRT: dict[str, str] = {
    "level-name": "zeigt auf den Weltordner - ein anderer Wert erzeugt eine neue, leere "
                  "Welt und laesst die bisherige liegen",
    "level-seed": "aendert die Weltgenerierung - neu erzeugte Gebiete passen dann nicht "
                  "mehr an die bestehende Welt",
    "level-type": "aendert die Weltgenerierung - siehe level-seed",
    "server-port": "verwaltet der Manager (Gateway-Routing und Sleep-Proxy haengen daran)",
    "server-ip": "verwaltet der Manager",
    "query.port": "verwaltet der Manager",
    "rcon.port": "verwaltet der Manager",
    "rcon.password": "ist ein Geheimnis und gehoert nicht in einen Vorschlag",
    "online-mode": "entscheidet ueber die UUID jedes Spielers - ein Wechsel trennt alle "
                   "Spieler von ihren Spielstaenden",
    "enable-rcon": "haengt an der Fernsteuerung des Managers",
}

# Erlaubt, aber mit sichtbarer Folge fuer den laufenden Betrieb. Wird angezeigt, aber im
# Formular nicht vorgehakt - diese Entscheidung soll der Mensch bewusst treffen.
BEDENKLICH: dict[str, str] = {
    "difficulty": "aendert den Schwierigkeitsgrad fuer alle",
    "gamemode": "aendert den Spielmodus neuer Spieler",
    "force-gamemode": "setzt den Spielmodus bei jedem Betreten zurueck",
    "hardcore": "im Hardcore-Modus ist der Tod endgueltig",
    "pvp": "erlaubt oder verbietet Kampf zwischen Spielern",
    "spawn-protection": "aendert, wo in der Naehe des Spawns gebaut werden darf",
    "allow-nether": "false schliesst den Nether - Gebautes bleibt, ist aber unerreichbar",
    "allow-flight": "wirkt auf die Flugerkennung gegen Cheats",
    "white-list": "schliesst alle aus, die nicht auf der Liste stehen",
    "enforce-whitelist": "wirft bereits verbundene Spieler hinaus, die nicht gelistet sind",
    "enable-command-block": "erlaubt oder verbietet Befehlsbloecke",
    "max-players": "aendert, wie viele gleichzeitig spielen koennen",
    "generate-structures": "aendert, ob in neuem Gelaende Bauwerke entstehen",
    "spawn-monsters": "schaltet feindliche Mobs ab oder an",
    "pvp-mode": "siehe pvp",
}

WERKZEUG_NAME = "konfigurationsvorschlag"

WERKZEUG = {
    "name": WERKZEUG_NAME,
    "description": ("Konkrete Aenderungsvorschlaege fuer die vorgelegte Konfigurationsdatei "
                    "und, falls sinnvoll, fuer den Arbeitsspeicher des Servers. Nur "
                    "Schluessel vorschlagen, die in der Datei vorkommen oder zu dieser "
                    "Serversoftware gehoeren."),
    "input_schema": {
        "type": "object",
        "properties": {
            "aenderungen": {
                "type": "array",
                "description": "Je Eintrag ein Schluessel mit neuem Wert.",
                "items": {
                    "type": "object",
                    "properties": {
                        "schluessel": {"type": "string",
                                       "description": "Der Schluessel genau wie in der Datei"},
                        "wert": {"type": "string", "description": "Der vorgeschlagene Wert"},
                        "begruendung": {"type": "string",
                                        "description": "Ein Satz: warum, bezogen auf die "
                                                       "genannten Eckdaten dieses Servers"},
                    },
                    "required": ["schluessel", "wert", "begruendung"],
                },
            },
            "speicher": {
                "type": "object",
                "description": "Nur ausfuellen, wenn der zugewiesene Speicher nicht passt.",
                "properties": {
                    "min_mb": {"type": "integer"},
                    "max_mb": {"type": "integer"},
                    "begruendung": {"type": "string"},
                },
            },
            "hinweise": {
                "type": "array",
                "description": "Was auffaellt, aber kein Einstellungswert ist (z.B. zu "
                               "wenig RAM im Rechner fuer die Summe aller Server).",
                "items": {"type": "string"},
            },
        },
        "required": ["aenderungen"],
    },
}


# --------------------------------------------------------------------- Eckdaten

def _properties(server: Server) -> dict[str, str]:
    pfad = Path(server.base_path) / "server.properties"
    werte: dict[str, str] = {}
    try:
        for zeile in pfad.read_text(encoding="utf-8", errors="replace").splitlines():
            zeile = zeile.strip()
            if not zeile or zeile.startswith("#") or "=" not in zeile:
                continue
            schluessel, wert = zeile.split("=", 1)
            werte[schluessel.strip()] = wert.strip()
    except OSError:
        pass
    return werte


def _inhalte_zaehlen(db: Session, server: Server) -> dict[str, int]:
    from app.models.installed_content import InstalledContent

    zahlen: dict[str, int] = {}
    try:
        for zeile in db.scalars(select(InstalledContent)
                                .where(InstalledContent.server_id == server.id)):
            art = (zeile.content_type or "sonstiges").lower()
            zahlen[art] = zahlen.get(art, 0) + 1
    except Exception:  # noqa: BLE001 - die Zahl ist eine Zusatzangabe, kein Muss
        return {}
    return zahlen


def eckdaten(db: Session, server: Server) -> dict:
    """Die Zahlen, ohne die jeder Vorschlag geraten waere.

    Ein Ratschlag wie "gib dem Server 8 GB" ist ohne den RAM des Rechners und ohne die
    Summe dessen, was die anderen Server schon belegen, keine Empfehlung, sondern ein
    Wuerfelwurf. Deshalb geht das alles mit.
    """
    from app.services.process_service import get_player_counts
    from app.services.resource_service import get_host_resources

    rechner = get_host_resources()
    props = _properties(server)

    zugewiesen_gesamt = 0
    andere: list[str] = []
    try:
        for s in db.scalars(select(Server)):
            if not s.memory_max_mb:
                continue
            zugewiesen_gesamt += int(s.memory_max_mb)
            if s.id != server.id:
                andere.append(f"{s.name}: {int(s.memory_max_mb)} MB")
    except Exception:  # noqa: BLE001
        pass

    try:
        jetzt, maximal = get_player_counts(server)
    except Exception:  # noqa: BLE001
        jetzt, maximal = None, None

    return {
        "typ": server.server_type,
        "version": server.mc_version,
        "loader_version": server.loader_version,
        "speicher_min_mb": server.memory_min_mb,
        "speicher_max_mb": server.memory_max_mb,
        "rechner_ram_mb": int(rechner["memory_total_mb"]) if rechner.get("memory_total_mb")
                          else None,
        "rechner_kerne": rechner.get("cpu_logical"),
        "allen_servern_zugewiesen_mb": zugewiesen_gesamt or None,
        "andere_server": andere,
        "spieler_jetzt": jetzt,
        "spieler_maximal": maximal or props.get("max-players"),
        "inhalte": _inhalte_zaehlen(db, server),
        "sleep_an": bool(server.sleep_enabled),
        "gateway_an": bool(server.gateway_enabled),
    }


def _eckdaten_text(daten: dict) -> str:
    teile = [
        f"Serversoftware: {daten['typ']} {daten['version']}"
        + (f" (Loader {daten['loader_version']})" if daten.get("loader_version") else ""),
    ]
    if daten.get("speicher_max_mb"):
        teile.append(f"Zugewiesener Arbeitsspeicher: {daten.get('speicher_min_mb') or '?'}"
                     f"-{daten['speicher_max_mb']} MB")
    else:
        teile.append("Zugewiesener Arbeitsspeicher: nicht gesetzt (der Startbefehl "
                     "entscheidet)")
    if daten.get("rechner_ram_mb"):
        teile.append(f"RAM im Rechner: {daten['rechner_ram_mb']} MB"
                     + (f", {daten['rechner_kerne']} logische Kerne"
                        if daten.get("rechner_kerne") else ""))
    if daten.get("allen_servern_zugewiesen_mb"):
        teile.append(f"Allen Servern zusammen zugewiesen: "
                     f"{daten['allen_servern_zugewiesen_mb']} MB"
                     + (f" (davon andere: {', '.join(daten['andere_server'][:6])})"
                        if daten.get("andere_server") else ""))
    if daten.get("spieler_maximal"):
        teile.append(f"Spielerzahl: {daten.get('spieler_jetzt') if daten.get('spieler_jetzt') is not None else '?'}"
                     f" von {daten['spieler_maximal']}")
    if daten.get("inhalte"):
        teile.append("Installiert: " + ", ".join(f"{anzahl} {art}"
                                                 for art, anzahl in sorted(daten["inhalte"].items())))
    if daten.get("sleep_an"):
        teile.append("Der Server nutzt den Sleep-Modus (schlaeft bei 0 Spielern).")
    if daten.get("gateway_an"):
        teile.append("Der Server ist ueber das Gateway per Hostname erreichbar.")
    return "\n".join(f"- {t}" for t in teile)


# ------------------------------------------------------------------ Vorschlaege

def _art_passt(alt: str, neu: str) -> tuple[bool, str]:
    """Passt der neue Wert zur Art des bisherigen?

    Eine Zahl, wo vorher true/false stand, oder Text, wo eine Zahl stand, ist kein
    Vorschlag, sondern ein Fehler - und wuerde den Serverstart kosten. Besser hier
    abfangen als beim naechsten Start suchen.
    """
    a, n = (alt or "").strip().lower(), (neu or "").strip()
    if a in {"true", "false"}:
        if n.lower() not in {"true", "false"}:
            return False, f"erwartet true oder false, vorgeschlagen war '{neu}'"
        return True, ""
    if a and _ist_zahl(a):
        if not _ist_zahl(n):
            return False, f"erwartet eine Zahl, vorgeschlagen war '{neu}'"
        return True, ""
    return True, ""


def _ist_zahl(wert: str) -> bool:
    roh = (wert or "").strip()
    if roh.startswith("-"):
        roh = roh[1:]
    return roh.isdigit()


def pruefe_vorschlaege(vorschlaege: list[dict], vorhanden: dict[str, str]) -> dict:
    """Vorschlaege gegen die echte Datei pruefen und einordnen.

    Nichts wird still verworfen: was nicht uebernommen werden kann, steht mit Grund in
    ``abgelehnt``. Ein stiller Wegfall waere das Schlimmste - dann glaubt der Nutzer,
    etwas sei gesetzt, was nie ankam.
    """
    uebernehmbar: list[dict] = []
    abgelehnt: list[dict] = []
    gesehen: set[str] = set()

    for eintrag in vorschlaege or []:
        schluessel = str(eintrag.get("schluessel") or "").strip()
        wert = str(eintrag.get("wert") if eintrag.get("wert") is not None else "").strip()
        grund = str(eintrag.get("begruendung") or "").strip()
        if not schluessel:
            continue
        if schluessel in gesehen:
            abgelehnt.append({"schluessel": schluessel, "wert": wert,
                              "grund": "doppelter Vorschlag fuer denselben Schluessel"})
            continue
        gesehen.add(schluessel)

        if schluessel in GESPERRT:
            abgelehnt.append({"schluessel": schluessel, "wert": wert,
                              "grund": GESPERRT[schluessel]})
            continue
        if schluessel not in vorhanden:
            abgelehnt.append({
                "schluessel": schluessel, "wert": wert,
                "grund": "steht nicht in dieser Datei - ein neuer Schluessel waere "
                         "entweder ein Tippfehler oder gehoert in eine andere Datei"})
            continue

        alt = vorhanden.get(schluessel, "")
        if alt.strip() == wert:
            abgelehnt.append({"schluessel": schluessel, "wert": wert,
                              "grund": "steht schon so in der Datei"})
            continue
        passt, warum = _art_passt(alt, wert)
        if not passt:
            abgelehnt.append({"schluessel": schluessel, "wert": wert, "grund": warum})
            continue

        uebernehmbar.append({
            "schluessel": schluessel,
            "alt": alt,
            "wert": wert,
            "begruendung": grund,
            "bedenklich": BEDENKLICH.get(schluessel, ""),
        })

    return {"aenderungen": uebernehmbar, "abgelehnt": abgelehnt}


def _speicher_pruefen(roh: dict | None, daten: dict) -> dict | None:
    """Einen Speichervorschlag auf Plausibilitaet pruefen.

    Mehr Speicher zuweisen als der Rechner hat, fuehrt nicht zu einem langsamen Server,
    sondern zu einem, der nicht startet oder den Rechner lahmlegt.
    """
    if not isinstance(roh, dict):
        return None
    try:
        max_mb = int(roh.get("max_mb") or 0)
        min_mb = int(roh.get("min_mb") or 0)
    except (TypeError, ValueError):
        return None
    if max_mb <= 0:
        return None

    warnungen: list[str] = []
    if min_mb > max_mb:
        return None
    if max_mb < 1024:
        warnungen.append("Unter 1 GB laeuft praktisch kein Server stabil.")

    ram = daten.get("rechner_ram_mb")
    if ram:
        andere = (daten.get("allen_servern_zugewiesen_mb") or 0) - (
            daten.get("speicher_max_mb") or 0)
        if max_mb + max(0, andere) > ram - 2048:
            warnungen.append(
                f"Mit {max_mb} MB fuer diesen Server und {max(0, andere)} MB fuer die "
                f"uebrigen bleiben von {ram} MB weniger als 2 GB fuer Windows selbst. "
                f"Das wird der Rechner nicht aushalten.")

    return {"min_mb": min_mb or None, "max_mb": max_mb,
            "alt_min_mb": daten.get("speicher_min_mb"),
            "alt_max_mb": daten.get("speicher_max_mb"),
            "begruendung": str(roh.get("begruendung") or "").strip(),
            "warnungen": warnungen}


def vorschau(db: Session, server: Server, pfad: str, inhalt: str) -> dict:
    """Was wuerde gesendet? Dieselbe Offenheit wie bei der Loganalyse."""
    from app.services import ai_privacy_service, ai_settings_service

    daten = eckdaten(db, server)
    einstellungen = ai_settings_service.hole_einstellungen(db)
    sicht = ai_privacy_service.vorschau(
        _baue_kontext(pfad, inhalt, daten),
        namen_ersetzen=bool(einstellungen.get("namen_ersetzen")))
    sicht["eckdaten"] = daten
    return sicht


def _baue_kontext(pfad: str, inhalt: str, daten: dict) -> str:
    return (f"Datei: {pfad}\n\nEckdaten dieses Servers:\n{_eckdaten_text(daten)}\n\n"
            f"Aktueller Inhalt der Datei:\n{inhalt}")


def schlage_vor(db: Session, *, user: User, server: Server, pfad: str, inhalt: str,
                ziel: str = "") -> dict:
    """Vorschlaege holen, pruefen und einordnen. Wirft KiAbgelehnt oder KiFehler."""
    if not (inhalt or "").strip():
        raise ai_service.KiAbgelehnt("Die Datei ist leer - es gibt nichts vorzuschlagen.")

    daten = eckdaten(db, server)
    anliegen = (ziel or "").strip() or (
        "Pruefe diese Konfiguration gegen die genannten Eckdaten und schlage vor, was "
        "besser passen wuerde.")

    gesperrt_text = ", ".join(sorted(GESPERRT))
    ergebnis = ai_service.frage(
        db, user=user, funktion="config_assistent", nachricht=anliegen,
        kontext=_baue_kontext(pfad, inhalt, daten), server_id=server.id,
        zusatzauftrag=(
            f"Benutze das Werkzeug '{WERKZEUG_NAME}'. Schlage nur Schluessel vor, die in "
            f"der vorgelegten Datei vorkommen; einen neuen Schluessel kann der Manager "
            f"nicht uebernehmen. Begruende jede Aenderung mit den genannten Eckdaten, "
            f"nicht allgemein. Schlage nichts vor, was schon so eingestellt ist. Diese "
            f"Schluessel darfst du NICHT vorschlagen, sie verwaltet der Manager oder sie "
            f"wuerden bestehende Daten entwerten: {gesperrt_text}."),
        werkzeuge=[WERKZEUG], werkzeugzwang=WERKZEUG_NAME, max_tokens=3072)

    eingabe: dict = {}
    for aufruf in ergebnis.get("werkzeug_aufrufe") or []:
        if aufruf.get("name") == WERKZEUG_NAME:
            eingabe = aufruf.get("eingabe") or {}
            break

    vorhanden = _parse_inhalt(pfad, inhalt)
    geprueft = pruefe_vorschlaege(eingabe.get("aenderungen") or [], vorhanden)
    ergebnis.update(geprueft)
    ergebnis["speicher"] = _speicher_pruefen(eingabe.get("speicher"), daten)
    ergebnis["eckdaten"] = daten

    roh_hinweise = [str(h).strip() for h in (eingabe.get("hinweise") or []) if str(h).strip()]
    ergebnis["beobachtungen"] = roh_hinweise

    if not geprueft["aenderungen"] and not geprueft["abgelehnt"] and not roh_hinweise:
        ergebnis.setdefault("hinweise", []).append(
            "Die KI hat keinen Aenderungsbedarf gesehen.")
    if geprueft["abgelehnt"]:
        ergebnis.setdefault("hinweise", []).append(
            f"{len(geprueft['abgelehnt'])} Vorschlag/Vorschlaege wurden nicht uebernommen - "
            f"die Gruende stehen darunter.")
    return ergebnis


def _parse_inhalt(pfad: str, inhalt: str) -> dict[str, str]:
    """Die Schluessel der vorgelegten Datei - gegen diese wird jeder Vorschlag geprueft."""
    if pfad.replace("\\", "/").lower().endswith((".properties", ".txt")):
        werte: dict[str, str] = {}
        for zeile in (inhalt or "").splitlines():
            roh = zeile.strip()
            if not roh or roh.startswith("#") or "=" not in roh:
                continue
            schluessel, wert = roh.split("=", 1)
            werte[schluessel.strip()] = wert.strip()
        return werte
    return {}


def unterstuetzt(pfad: str) -> bool:
    """Fuer welche Dateien gibt es den Assistenten?

    Nur fuer Dateien, deren Schluessel der Manager zuverlaessig lesen kann - sonst liesse
    sich ein Vorschlag nicht gegen den Istzustand pruefen, und genau diese Pruefung ist
    der Schutz davor, dass ein erfundener Schluessel ins Formular wandert.
    """
    normalisiert = pfad.replace("\\", "/").lower()
    return normalisiert.endswith(".properties")
