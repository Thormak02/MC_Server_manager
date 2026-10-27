"""Changelog-Zusammenfassung vor einem Update: was aendert sich, was bricht.

Die Frage, die diese Funktion beantwortet, ist nicht "gibt es ein Update", sondern
"darf ich es einspielen". Dafuer reicht die Versionsnummer nicht: zwischen der
installierten und der gewuenschten Fassung liegen oft mehrere Veroeffentlichungen, und
der Satz, der zaehlt, steht meist in der Mitte - "config format changed, delete your old
config".

Deshalb werden ALLE Changelogs zwischen der installierten und der Zielversion geholt, nicht
nur der des Ziels. Modrinth liefert den Text im Versionsobjekt mit; CurseForge hat einen
eigenen Endpunkt pro Datei und liefert HTML, das hier zu Text wird.

Zwei Dinge, die Geld sparen und ohne die die Antwort schlechter waere:

* Ist bei allen Fassungen kein Changelog hinterlegt - bei Plugins der Normalfall - wird
  die KI GAR NICHT gefragt. Sie koennte aus nichts nur etwas erfinden, und bezahlt waere
  es trotzdem.
* Bei sehr vielen Zwischenfassungen wird gekuerzt, aber nie stillschweigend: was
  weggelassen wurde, steht in der Antwort.
"""

from __future__ import annotations

import html
import re

from sqlalchemy.orm import Session

from app.models.installed_content import InstalledContent
from app.models.server import Server
from app.models.user import User
from app.services import ai_service

MAX_FASSUNGEN = 12
MAX_ZEICHEN = 24_000

WERKZEUG_NAME = "changelog_bericht"

WERKZEUG = {
    "name": WERKZEUG_NAME,
    "description": ("Bericht zu den vorgelegten Changelogs eines Minecraft-Mods oder "
                    "-Plugins: was sich aendert, was bestehende Installationen bricht, "
                    "und ob das Update jetzt gefahrlos ist."),
    "input_schema": {
        "type": "object",
        "properties": {
            "zusammenfassung": {
                "type": "string",
                "description": "Zwei bis fuenf Saetze: was aendert sich fuer den Betreiber "
                               "dieses Servers. Keine Aufzaehlung der Commits.",
            },
            "breaking": {
                "type": "array",
                "description": "Aenderungen, die eine bestehende Installation brechen oder "
                               "einen Eingriff verlangen. Leer, wenn es keine gibt - "
                               "nichts erfinden.",
                "items": {
                    "type": "object",
                    "properties": {
                        "was": {"type": "string"},
                        "folge": {"type": "string",
                                  "description": "Was passiert, wenn man nichts tut"},
                        "zu_tun": {"type": "string",
                                   "description": "Der konkrete Schritt davor oder danach"},
                    },
                    "required": ["was", "folge"],
                },
            },
            "wichtig": {
                "type": "array",
                "description": "Weitere nennenswerte Aenderungen ohne Bruch "
                               "(Fehlerbehebungen, neue Einstellungen).",
                "items": {"type": "string"},
            },
            "empfehlung": {
                "type": "string",
                "enum": ["jetzt", "pruefen", "warten"],
                "description": "'jetzt' = gefahrlos; 'pruefen' = erst ein Schritt noetig "
                               "(Backup, Config); 'warten' = es gibt einen Grund, nicht "
                               "zu aktualisieren.",
            },
            "begruendung_empfehlung": {"type": "string"},
        },
        "required": ["zusammenfassung", "breaking", "empfehlung"],
    },
}


# ----------------------------------------------------------------- HTML zu Text

# li fehlt hier absichtlich: das schliessende </li> wuerde eine zweite Leerzeile
# erzeugen, denn das naechste <li> bringt seinen Umbruch schon mit. Doppelte
# Leerzeilen in einer Liste kosten nur Token.
_BLOCK_ENDE = re.compile(r"(?i)</(?:p|div|h[1-6]|tr|blockquote)>")
_ZEILENUMBRUCH = re.compile(r"(?i)<br\s*/?>")
_LISTENPUNKT = re.compile(r"(?i)<li[^>]*>")
_TAG = re.compile(r"<[^>]+>")
_LEERZEILEN = re.compile(r"\n{3,}")


def html_zu_text(roh: str) -> str:
    """CurseForge liefert HTML. Ungewandelt waeren Tags ein Drittel der Token.

    Die Struktur bleibt erhalten, soweit sie Bedeutung traegt: Listenpunkte und
    Absatzgrenzen, denn ein Changelog IST eine Liste und ohne die Grenzen laufen die
    Punkte zu einem Satz zusammen.
    """
    if not roh:
        return ""
    text = _ZEILENUMBRUCH.sub("\n", roh)
    text = _LISTENPUNKT.sub("\n- ", text)
    text = _BLOCK_ENDE.sub("\n", text)
    text = _TAG.sub("", text)
    text = html.unescape(text)
    zeilen = [z.rstrip() for z in text.splitlines()]
    return _LEERZEILEN.sub("\n\n", "\n".join(zeilen)).strip()


# ------------------------------------------------------------------- Changelogs

def _modrinth_fassungen(projekt_id: str, mc_version: str | None,
                        loader: str | None) -> list[dict]:
    import json as _json
    import urllib.parse

    from app.services import content_service as cs

    params: dict[str, str] = {}
    mc_liste = cs._normalize_mc_version_list(mc_version)
    loader_liste = cs._normalize_loader_list(loader)
    if mc_liste:
        params["game_versions"] = _json.dumps(mc_liste)
    if loader_liste:
        params["loaders"] = _json.dumps(loader_liste)
    url = f"{cs.MODRINTH_BASE}/project/{projekt_id}/version"
    if params:
        url = f"{url}?{urllib.parse.urlencode(params)}"

    nutzlast = cs._request_json(url, headers=cs._modrinth_headers())
    eintraege = nutzlast if isinstance(nutzlast, list) else []
    fassungen = [
        {
            "id": str(e.get("id") or ""),
            "version": str(e.get("version_number") or e.get("name") or ""),
            "datum": str(e.get("date_published") or ""),
            "kanal": str(e.get("version_type") or "release"),
            "changelog": str(e.get("changelog") or "").strip(),
        }
        for e in eintraege if isinstance(e, dict)
    ]
    fassungen.sort(key=lambda f: f["datum"])
    return fassungen


def _curseforge_fassungen(projekt_id: str) -> list[dict]:
    from app.services import content_service as cs

    nutzlast = cs._request_json(f"{cs.CURSEFORGE_BASE}/v1/mods/{projekt_id}/files",
                                headers=cs._curseforge_headers())
    eintraege = (nutzlast or {}).get("data") if isinstance(nutzlast, dict) else []
    fassungen = [
        {
            "id": str(e.get("id") or ""),
            "version": str(e.get("displayName") or e.get("fileName") or ""),
            "datum": str(e.get("fileDate") or ""),
            "kanal": cs._curseforge_release_channel(e.get("releaseType")),
            "changelog": "",   # kommt erst auf Nachfrage, ein Aufruf je Datei
        }
        for e in (eintraege or []) if isinstance(e, dict)
    ]
    fassungen.sort(key=lambda f: f["datum"])
    return fassungen


def _curseforge_changelog(projekt_id: str, datei_id: str) -> str:
    from app.services import content_service as cs

    try:
        nutzlast = cs._request_json(
            f"{cs.CURSEFORGE_BASE}/v1/mods/{projekt_id}/files/{datei_id}/changelog",
            headers=cs._curseforge_headers())
    except ValueError:
        return ""
    roh = (nutzlast or {}).get("data") if isinstance(nutzlast, dict) else ""
    return html_zu_text(str(roh or ""))


def _spanne(fassungen: list[dict], von_id: str, bis_id: str) -> tuple[list[dict], dict]:
    """Die Fassungen ZWISCHEN installiert und Ziel - die installierte selbst nicht mehr.

    Ohne diese Spanne wuerde nur der Changelog der Zielversion gelesen. Der Satz, der
    zaehlt ("config format changed"), steht aber oft in einer Fassung dazwischen, die man
    mit einem Sprung ueberspringt - und genau dann merkt man ihn nicht.
    """
    kennungen = [f["id"] for f in fassungen]
    bis = kennungen.index(bis_id) if bis_id in kennungen else len(fassungen) - 1
    von = kennungen.index(von_id) if von_id in kennungen else -1

    if von >= bis:
        # Rueckwaerts (Downgrade) oder gleich: dann interessiert der Changelog der
        # Zielfassung, nicht eine leere Spanne.
        ausschnitt = fassungen[bis:bis + 1]
        return ausschnitt, {"richtung": "zurueck" if von > bis else "gleich",
                            "gesamt": len(ausschnitt), "weggelassen": 0,
                            "installierte_bekannt": von >= 0}

    ausschnitt = fassungen[von + 1:bis + 1]
    gesamt = len(ausschnitt)
    weggelassen = 0
    if gesamt > MAX_FASSUNGEN:
        # Die NEUESTEN behalten: dort steht der aktuelle Stand, und das Ziel ist die
        # letzte. Was wegfaellt, wird gemeldet, nicht verschwiegen.
        ausschnitt = ausschnitt[-MAX_FASSUNGEN:]
        weggelassen = gesamt - MAX_FASSUNGEN
    return ausschnitt, {"richtung": "vor", "gesamt": gesamt, "weggelassen": weggelassen,
                        "installierte_bekannt": von >= 0}


def hole_changelogs(eintrag: InstalledContent, server: Server, ziel_version_id: str) -> dict:
    """Changelogs zwischen installierter und Zielfassung holen.

    Wirft ValueError, wenn das Projekt nicht abgefragt werden kann - der Aufrufer macht
    daraus eine verstaendliche Meldung.
    """
    from app.services import content_service as cs

    anbieter = (eintrag.provider_name or "").strip().lower()
    projekt_id = str(eintrag.external_project_id or "").strip()
    installiert_id = str(eintrag.external_version_id or "").strip()
    ziel_id = str(ziel_version_id or "").strip()

    if not projekt_id:
        raise ValueError("Zu diesem Eintrag ist kein Projekt hinterlegt - er wurde manuell "
                         "hochgeladen. Ohne Projekt gibt es keine Changelogs.")
    if anbieter == "modrinth":
        inhalt_typ = (eintrag.content_type or "").strip().lower()
        fassungen = _modrinth_fassungen(
            projekt_id, cs._expected_server_mc_version(server),
            cs._expected_server_loader(server, inhalt_typ))
    elif anbieter == "curseforge":
        fassungen = _curseforge_fassungen(projekt_id)
    else:
        raise ValueError(f"Fuer den Anbieter '{eintrag.provider_name or 'unbekannt'}' gibt "
                         f"es keine Changelogs.")

    if not fassungen:
        raise ValueError("Der Anbieter hat keine Versionsliste geliefert.")

    ausschnitt, angaben = _spanne(fassungen, installiert_id, ziel_id)

    if anbieter == "curseforge":
        for fassung in ausschnitt:
            fassung["changelog"] = _curseforge_changelog(projekt_id, fassung["id"])
    else:
        for fassung in ausschnitt:
            fassung["changelog"] = html_zu_text(fassung["changelog"]) \
                if "<" in fassung["changelog"] else fassung["changelog"]

    mit_text = [f for f in ausschnitt if f["changelog"].strip()]
    angaben["mit_changelog"] = len(mit_text)
    angaben["ohne_changelog"] = len(ausschnitt) - len(mit_text)
    return {"fassungen": ausschnitt, "angaben": angaben,
            "installiert": eintrag.version_label or installiert_id or "unbekannt"}


def baue_text(daten: dict) -> tuple[str, bool]:
    """Die Changelogs zu einem Text zusammensetzen. Rueckgabe: (Text, gekuerzt)."""
    teile: list[str] = []
    zeichen = 0
    gekuerzt = False
    # Von NEU nach ALT: wenn gekuerzt werden muss, soll das Aelteste wegfallen.
    for fassung in reversed(daten["fassungen"]):
        if not fassung["changelog"].strip():
            continue
        kopf = (f"=== Version {fassung['version']}"
                f"{f' ({fassung['kanal']})' if fassung['kanal'] != 'release' else ''}"
                f"{f', {fassung['datum'][:10]}' if fassung['datum'] else ''} ===")
        block = f"{kopf}\n{fassung['changelog'].strip()}"
        if zeichen + len(block) > MAX_ZEICHEN and teile:
            gekuerzt = True
            break
        teile.append(block)
        zeichen += len(block)
    return "\n\n".join(teile), gekuerzt


def berichte(db: Session, *, user: User, server: Server, eintrag: InstalledContent,
             ziel_version_id: str, ziel_version_label: str = "") -> dict:
    """Changelogs holen und zusammenfassen lassen. Wirft KiAbgelehnt, KiFehler, ValueError."""
    daten = hole_changelogs(eintrag, server, ziel_version_id)
    text, gekuerzt = baue_text(daten)
    angaben = daten["angaben"]

    name = eintrag.name or str(eintrag.external_project_id or "")
    ziel = ziel_version_label or next(
        (f["version"] for f in daten["fassungen"] if f["id"] == str(ziel_version_id)),
        str(ziel_version_id))

    if not text.strip():
        # Kein Changelog hinterlegt - bei Plugins der Normalfall. Die KI zu fragen waere
        # eine Aufforderung zum Erfinden, und bezahlt waere es trotzdem.
        return {
            "ohne_ki": True,
            "name": name, "installiert": daten["installiert"], "ziel": ziel,
            "angaben": angaben,
            "meldung": (f"Zu den {angaben['gesamt']} Fassung(en) zwischen "
                        f"{daten['installiert']} und {ziel} ist kein Changelog hinterlegt. "
                        f"Es gibt also nichts zusammenzufassen - die KI wurde nicht "
                        f"gefragt, das haette nur Guthaben gekostet."),
            "fassungen": [{"version": f["version"], "datum": f["datum"][:10],
                           "kanal": f["kanal"], "hat_changelog": False}
                          for f in daten["fassungen"]],
        }

    ergebnis = ai_service.frage(
        db, user=user, funktion="changelog",
        nachricht=(f"Fasse zusammen, was sich beim Update von {name} "
                   f"{daten['installiert']} auf {ziel} aendert."),
        kontext=text, server_id=server.id,
        zusatzauftrag=(
            f"Benutze das Werkzeug '{WERKZEUG_NAME}'. Der Server ist "
            f"{server.server_type} {server.mc_version}. Es geht um "
            f"{eintrag.content_type or 'einen Inhalt'} '{name}', installiert ist "
            f"{daten['installiert']}, gewuenscht ist {ziel}; dazwischen liegen "
            f"{angaben['gesamt']} Fassung(en). Beurteile aus der Sicht eines Betreibers, "
            f"der den Server laufen hat: was muss er TUN, bevor oder nachdem er "
            f"aktualisiert. Erfinde keine Breaking Changes - steht nichts davon in den "
            f"Changelogs, bleibt die Liste leer."),
        werkzeuge=[WERKZEUG], werkzeugzwang=WERKZEUG_NAME, max_tokens=2048)

    bericht: dict = {}
    for aufruf in ergebnis.get("werkzeug_aufrufe") or []:
        if aufruf.get("name") == WERKZEUG_NAME:
            bericht = aufruf.get("eingabe") or {}
            break

    ergebnis.update({
        "ohne_ki": False,
        "name": name, "installiert": daten["installiert"], "ziel": ziel,
        "angaben": angaben,
        "zusammenfassung": str(bericht.get("zusammenfassung") or "").strip(),
        "breaking": [b for b in (bericht.get("breaking") or []) if isinstance(b, dict)],
        "wichtig": [str(w).strip() for w in (bericht.get("wichtig") or []) if str(w).strip()],
        "empfehlung": str(bericht.get("empfehlung") or "pruefen"),
        "begruendung_empfehlung": str(bericht.get("begruendung_empfehlung") or "").strip(),
        "fassungen": [{"version": f["version"], "datum": f["datum"][:10],
                       "kanal": f["kanal"], "hat_changelog": bool(f["changelog"].strip())}
                      for f in daten["fassungen"]],
    })

    if angaben["weggelassen"]:
        ergebnis.setdefault("hinweise", []).append(
            f"Zwischen den beiden Fassungen liegen {angaben['gesamt']} "
            f"Veroeffentlichungen. Zusammengefasst wurden die {MAX_FASSUNGEN} neuesten; "
            f"{angaben['weggelassen']} aeltere wurden weggelassen.")
    if gekuerzt:
        ergebnis.setdefault("hinweise", []).append(
            "Die Changelogs waren zusammen sehr lang - die aeltesten wurden weggelassen, "
            "die neuesten sind vollstaendig drin.")
    if angaben["ohne_changelog"]:
        ergebnis.setdefault("hinweise", []).append(
            f"{angaben['ohne_changelog']} der Fassungen haben keinen Changelog hinterlegt - "
            f"was dort geaendert wurde, steht in dieser Zusammenfassung nicht.")
    if not angaben["installierte_bekannt"]:
        ergebnis.setdefault("hinweise", []).append(
            "Die installierte Fassung steht nicht in der Versionsliste des Anbieters "
            "(zurueckgezogen oder fuer eine andere Serverversion). Zusammengefasst wurde "
            "deshalb nur die Zielfassung, nicht die Spanne.")
    return ergebnis
