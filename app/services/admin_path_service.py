"""Pfadzugriff fuer Super-Admins - und die Regel, die ihn eingrenzt.

Warum ueberhaupt: der Assistent arbeitet sonst nur im Serverordner. Das genuegt fuer
Serverarbeit, aber nicht fuer den Manager selbst - die gehosteten Resource Packs liegen
unter ``data/resourcepacks``, die Logs unter ``data/logs``, Sicherungen oft auf einem
anderen Laufwerk. Statt den Sandkasten jedes Mal ein Stueck weiter aufzuziehen, gibt es
hier EINEN Pfadzugriff, und der steht nur Super-Admins offen.

Die Regel steht an einer Stelle, nicht an jedem Werkzeug: wer sie fuenfmal schreibt, hat
sie viermal falsch. ``pruefe()`` ist der einzige Weg zu einem Pfad; alles andere in
diesem Modul baut darauf auf.

Was die Regel kann - und was sie nicht kann:

* **Lesen ist weit, aber nicht vollstaendig.** Ein Super-Admin darf jede Konfiguration
  sehen. Zu bleiben haben die Stellen, aus denen sich der Manager *uebernehmen* laesst
  statt bedienen: ``.env``, die Datenbank, ``.git`` (im Remote kann ein Token stehen) und
  die Schluesselspeicher des Betriebssystems. Wer sie liest, braucht den Manager danach
  nicht mehr.
* **Schreiben ist eng.** Vor allem fuehrt kein Weg in den Programmcode des Managers. Wer
  ``app/`` schreiben darf, schreibt die Rechtepruefung um, die ihn gerade begrenzt - und
  zwar dauerhaft, ueber jede Sitzung hinaus. Das ist kein Pfadzugriff mehr, das ist eine
  andere Software.
* **Geloescht wird nichts dicht unter einer Laufwerkswurzel** und nichts, was ein
  Geheimnis ENTHAELT. ``C:\\Users`` ist zwei Zeichen von einem Unfall entfernt, und ein
  rekursives Loeschen fragt die Sperrliste sonst nur fuer sich selbst ab, nicht fuer das,
  was darunter liegt.
* **Ein laufender Server wird nicht unter sich weggeloescht.** Dafuer gibt es den
  Serverloeschweg, der den Prozess vorher beendet.

Was die Regel ausdruecklich NICHT leistet: sie macht den Zugriff nicht harmlos. Ein
Super-Admin kann damit Dateien zerstoeren, die niemand gesichert hat. Sie verhindert den
Unfall und die Rechteausweitung, nicht die bewusste Entscheidung.
"""
from __future__ import annotations

import os
from pathlib import Path

from sqlalchemy.orm import Session


class PfadFehler(Exception):
    """Der Pfad ist nicht erlaubt oder nicht brauchbar. Die Meldung ist fuer den Nutzer."""


# Hoechstens so viele Eintraege kommen aus einem Verzeichnis zurueck.
MAX_EINTRAEGE = 500
# Hoechstens so viele Bytes werden aus einer Datei gelesen. In BYTES, weil beim Lesen
# entschieden wird, wie viel Speicher belegt wird - nicht beim Kuerzen danach.
MAX_LESE_BYTES = 200_000
# Und so viele Zeichen kommen davon zurueck (der Rest ist fuer das Modell ohnehin wertlos).
MAX_LESE_ZEICHEN = 20_000
# Hoechstens so viel darf geschrieben werden. Mehr als das schickt kein Modell absichtlich.
MAX_SCHREIB_ZEICHEN = 1_000_000
# Oberhalb dieser Zahl wird ein Baum nicht geloescht. Keine Sicherheitsgrenze, eine
# Unfallgrenze: wer 20.000 Dateien loeschen will, nimmt nicht den Assistenten.
MAX_LOESCH_EINTRAEGE = 20_000
# So tief muss ein Pfad unter der Laufwerks- oder Freigabewurzel liegen, um geloescht
# werden zu duerfen. "C:/Users" hat eine Stufe - das ist der klassische Totalschaden.
MIN_TIEFE_LOESCHEN = 2

# Namen, die Windows nicht als Dateien behandelt, sondern als Geraete. Ein open() darauf
# liest nicht, es haengt oder schreibt in die Konsole.
_GERAETE = {"CON", "PRN", "AUX", "NUL", "CLOCK$",
            *(f"COM{n}" for n in range(1, 10)),
            *(f"LPT{n}" for n in range(1, 10))}

# Zeichen, die in einem Windows-Pfad nicht vorkommen koennen. Kommen sie doch, ist der
# Pfad geraten - und ein geratener Pfad soll scheitern, nicht irgendetwas treffen.
_VERBOTENE_ZEICHEN = '*?"<>|'

# Ordnernamen, die ueberall ein Geheimnis bedeuten - unabhaengig vom Nutzerprofil, vom
# Laufwerk und davon, ob der Manager lokal oder auf einem NAS liegt.
_GEHEIME_ORDNER = {
    ".ssh": "Dort liegen private SSH-Schluessel.",
    ".gnupg": "Dort liegen private GPG-Schluessel.",
    ".aws": "Dort liegen AWS-Zugangsdaten.",
    ".azure": "Dort liegen Azure-Zugangsdaten.",
    ".kube": "Dort liegen Cluster-Zugangsdaten.",
    ".docker": "Dort liegen Registry-Zugangsdaten.",
    ".claude": "Dort liegen Sitzungsdaten und Zugangsdaten des Assistenten.",
    ".git": "In der Git-Konfiguration kann ein Zugangstoken stehen.",
    "db-snapshots": "Das sind Kopien der Manager-Datenbank.",
}

# Dateinamen und Endungen, die fuer sich genommen ein Geheimnis sind.
_GEHEIME_NAMEN = {
    ".netrc": "Darin stehen Zugangsdaten im Klartext.",
    "_netrc": "Darin stehen Zugangsdaten im Klartext.",
    ".pgpass": "Darin stehen Datenbank-Passwoerter im Klartext.",
    "credentials": "Der Name sagt es.",
    "credentials.json": "Der Name sagt es.",
    ".credentials.json": "Der Name sagt es.",
    "id_rsa": "Das ist ein privater Schluessel.",
    "id_dsa": "Das ist ein privater Schluessel.",
    "id_ecdsa": "Das ist ein privater Schluessel.",
    "id_ed25519": "Das ist ein privater Schluessel.",
}
_GEHEIME_ENDUNGEN = {
    ".pem": "Das ist ein privater Schluessel.",
    ".key": "Das ist ein privater Schluessel.",
    ".pfx": "Das ist ein Zertifikat samt privatem Schluessel.",
    ".p12": "Das ist ein Zertifikat samt privatem Schluessel.",
    ".ppk": "Das ist ein privater Schluessel.",
    ".kdbx": "Das ist ein Passwortspeicher.",
    ".db": "Darin kann die Manager-Datenbank stehen (Passwort-Hashes, Schluessel).",
    ".db-wal": "Das gehoert zur Datenbank.",
    ".db-shm": "Das gehoert zur Datenbank.",
    ".sqlite": "Darin kann die Manager-Datenbank stehen.",
    ".sqlite3": "Darin kann die Manager-Datenbank stehen.",
}


def _wurzel() -> Path:
    """Der Programmordner des Managers. Aus dem Modulpfad, nicht aus dem
    Arbeitsverzeichnis: welches das ist, haengt davon ab, wie der Dienst gestartet wurde."""
    return Path(__file__).resolve().parents[2]


def _umgebungspfad(name: str) -> Path | None:
    wert = (os.environ.get(name) or "").strip()
    if not wert:
        return None
    try:
        return Path(wert).resolve()
    except OSError:
        return None


def _ist_unter(ziel: Path, ordner: Path) -> bool:
    return ziel == ordner or ordner in ziel.parents


def _tiefe_unter_wurzel(ziel: Path) -> int:
    """Stufen unterhalb der Laufwerks- oder Freigabewurzel.

    ``Path.parts`` zaehlt den Anker als ein Teil, bei einer UNC-Freigabe aber ebenfalls
    nur einen (``\\\\host\\share`` ist EIN Anker) - deshalb genuegt parts[1:].
    """
    return len(ziel.parts) - 1


def _geheimnisgrund(ziel: Path) -> str | None:
    """Warum dieser Pfad auch einem Super-Admin verschlossen bleibt - oder None."""
    name = ziel.name.lower()

    # .env traegt den Sitzungsschluessel, den API-Schluessel und das Startpasswort des
    # ersten Super-Admins. Auch .env.example bleibt zu: die Unterscheidung lohnt den
    # Sonderfall nicht, und im Repository steht die Datei ohnehin oeffentlich.
    if name == ".env" or name.startswith(".env."):
        return "In .env stehen Sitzungsschluessel und Zugangsdaten des Managers."

    if name in _GEHEIME_NAMEN:
        return _GEHEIME_NAMEN[name]
    for endung, grund in _GEHEIME_ENDUNGEN.items():
        if name.endswith(endung):
            return grund

    # Ordnernamen gelten fuer den Ordner selbst UND fuer alles darunter.
    for teil in ziel.parts:
        grund = _GEHEIME_ORDNER.get(teil.lower())
        if grund:
            return grund

    heim = Path.home()
    windows = _umgebungspfad("SystemRoot") or Path("C:/Windows")
    for ordner, grund in (
        (heim / "AppData/Roaming/Microsoft/Protect",
         "Das sind die DPAPI-Hauptschluessel - damit liesse sich jedes verschluesselte "
         "Geheimnis des Managers oeffnen."),
        (heim / "AppData/Local/Microsoft/Protect", "Das sind DPAPI-Hauptschluessel."),
        (heim / "AppData/Local/Microsoft/Credentials", "Das ist der Windows-Anmeldespeicher."),
        (heim / "AppData/Roaming/Microsoft/Credentials", "Das ist der Windows-Anmeldespeicher."),
        (heim / "AppData/Local/Microsoft/Vault", "Das ist der Windows-Anmeldespeicher."),
        (windows / "System32/config",
         "Dort liegen die Registry-Stoecke samt Kontodaten."),
        (windows / "System32/Microsoft/Protect", "Das sind DPAPI-Hauptschluessel."),
        (windows / "repair", "Dort liegen Kopien der Kontodatenbank."),
    ):
        if _ist_unter(ziel, ordner):
            return grund
    return None


def _schreibbare_bereiche(db: Session | None) -> list[Path]:
    """Die Stellen INNERHALB des Programmordners, die trotzdem Daten und nicht Code sind.

    Serverordner und Sicherungen koennen per Einstellung ueberall liegen - auch mitten im
    Programmordner. Wer sie nicht ausnimmt, sperrt den Assistenten aus genau den Ordnern
    aus, um die es bei dieser Aufgabe ueberhaupt geht.
    """
    from app.core.config import get_settings

    bereiche: list[Path] = []
    wurzel = _wurzel()
    for kandidat in (Path(get_settings().data_dir), wurzel / "managed_servers",
                     wurzel / "logs"):
        try:
            bereiche.append(kandidat.resolve())
        except OSError:
            continue
    if db is not None:
        from app.services import app_setting_service

        for holen in (app_setting_service.get_server_storage_root,
                      app_setting_service.get_backup_storage_root):
            try:
                bereiche.append(Path(holen(db)).resolve())
            except Exception:  # noqa: BLE001 - eine fehlende Einstellung ist kein Fehler hier
                continue
    return bereiche


def _nurlesegrund(ziel: Path, db: Session | None) -> str | None:
    """Warum hier nicht geschrieben werden darf - oder None."""
    wurzel = _wurzel()
    if _ist_unter(ziel, wurzel) and not any(
            _ist_unter(ziel, bereich) for bereich in _schreibbare_bereiche(db)):
        return ("Das ist der Programmordner des Managers. Wer hier schreibt, aendert die "
                "Software selbst - einschliesslich der Rechtepruefung, die diesen Zugriff "
                "gerade begrenzt. Daten liegen unter data/ und in den Serverordnern.")

    windows = _umgebungspfad("SystemRoot") or Path("C:/Windows")
    for ordner, grund in (
        (windows, "Das ist der Windows-Ordner."),
        (_umgebungspfad("ProgramFiles") or Path("C:/Program Files"),
         "Dort liegen installierte Programme."),
        (_umgebungspfad("ProgramFiles(x86)") or Path("C:/Program Files (x86)"),
         "Dort liegen installierte Programme."),
        (_umgebungspfad("ProgramData") or Path("C:/ProgramData"),
         "Dort liegen Daten installierter Programme."),
        (Path.home() / "AppData/Roaming/Microsoft/Windows/Start Menu/Programs/Startup",
         "Was dort liegt, startet beim Anmelden mit."),
        (windows / "System32/Tasks", "Das sind die geplanten Aufgaben des Systems."),
    ):
        if _ist_unter(ziel, ordner):
            return grund + " Dort aendert der Manager nichts."
    return None


def _serverordner(db: Session, ziel: Path) -> str | None:
    """Ob der Pfad ein Serverordner ist oder einen enthaelt.

    Einen Serverordner unter dem laufenden Prozess wegzuloeschen hinterlaesst einen
    Server, der laut Datenbank existiert, laut Festplatte nicht und laut Prozessliste
    noch laeuft. Dafuer gibt es den Loeschweg, der vorher stoppt.
    """
    from app.models.server import Server

    for server in db.query(Server).all():
        basis = (server.base_path or "").strip()
        if not basis:
            continue
        try:
            aufgeloest = Path(basis).resolve()
        except OSError:
            continue
        # Nur der Ordner SELBST ist gesperrt, nicht alles darin: eine Datei im
        # Serverordner zu loeschen ist gewoehnliche Arbeit, und der Dateieditor kann es
        # seit immer. Gesperrt ist das, was den Server als Ganzes verschwinden laesst.
        if ziel == aufgeloest:
            return (f"Das ist der Ordner des Servers '{server.name}'. Einen Server "
                    f"loescht man ueber den Server selbst - der beendet vorher den "
                    f"Prozess.")
        if ziel in aufgeloest.parents:
            return (f"Darunter liegt der Ordner des Servers '{server.name}'. Loeschen "
                    f"wuerde ihn mitnehmen.")
    return None


def _enthaelt_geheimes(ziel: Path) -> str | None:
    """Ob unter diesem Ordner etwas Gesperrtes liegt.

    Ein rekursives Loeschen fragt die Sperrliste sonst nur fuer den Ordner selbst ab.
    ``C:/Users/fridi`` steht auf keiner Liste - ``.ssh`` darunter schon.
    """
    if not ziel.is_dir():
        return None
    gezaehlt = 0
    for pfad in ziel.rglob("*"):
        gezaehlt += 1
        if gezaehlt > MAX_LOESCH_EINTRAEGE:
            return (f"Darunter liegen mehr als {MAX_LOESCH_EINTRAEGE} Eintraege - zu "
                    f"viele, um sie hier zu pruefen und zu loeschen.")
        grund = _geheimnisgrund(pfad)
        if grund:
            return f"Darunter liegt {pfad.name}: {grund}"
    return None


def pruefe(pfad: str, *, db: Session | None = None, zweck: str = "lesen",
           muss_existieren: bool = True, erwarte: str | None = None) -> Path:
    """Einen Pfad pruefen und aufgeloest zurueckgeben. Bei jedem Zweifel: PfadFehler.

    ``zweck`` ist "lesen", "schreiben" oder "loeschen" - in dieser Reihenfolge wird die
    Pruefung strenger, und jede Stufe enthaelt die vorige.
    """
    roh = str(pfad or "").strip().strip('"')
    if not roh:
        raise PfadFehler("Es wurde kein Pfad angegeben.")
    if "\x00" in roh:
        raise PfadFehler("Der Pfad enthaelt ein Nullzeichen.")
    if len(roh) > 4096:
        raise PfadFehler("Der Pfad ist zu lang.")
    for zeichen in _VERBOTENE_ZEICHEN:
        if zeichen in roh:
            raise PfadFehler(
                f"'{zeichen}' kommt in einem Windows-Pfad nicht vor. Dieses Werkzeug "
                f"loest keine Suchmuster auf - bitte einen vollstaendigen Pfad angeben.")

    kandidat = Path(os.path.expanduser(roh))
    if not kandidat.is_absolute():
        # Relativ zum PROGRAMMORDNER, nicht zum Arbeitsverzeichnis: "data/logs" soll
        # dasselbe treffen, egal wie der Dienst gestartet wurde.
        kandidat = _wurzel() / kandidat

    for teil in kandidat.parts[1:]:
        if teil.split(".")[0].upper() in _GERAETE:
            raise PfadFehler(f"'{teil}' ist unter Windows ein Geraet, keine Datei.")
        if ":" in teil:
            raise PfadFehler(
                "Ein Doppelpunkt im Pfad bezeichnet einen Datenstrom in der Datei, "
                "nicht die Datei. Das macht dieses Werkzeug nicht.")

    try:
        # Aufloesen folgt Verknuepfungen UND Abzweigungen (Junctions). Alles, was danach
        # kommt, prueft deshalb das ECHTE Ziel - sonst waere eine Abzweigung unter data/
        # ein Weg in jeden Ordner des Systems.
        ziel = kandidat.resolve()
    except (OSError, RuntimeError) as exc:
        raise PfadFehler(f"Der Pfad liess sich nicht aufloesen: {exc}") from exc

    if muss_existieren and not ziel.exists():
        raise PfadFehler(f"'{ziel}' gibt es nicht.")
    if erwarte == "datei" and ziel.exists() and not ziel.is_file():
        raise PfadFehler(f"'{ziel}' ist keine Datei.")
    if erwarte == "ordner" and ziel.exists() and not ziel.is_dir():
        raise PfadFehler(f"'{ziel}' ist kein Ordner.")

    grund = _geheimnisgrund(ziel)
    if grund:
        raise PfadFehler(f"Dieser Pfad bleibt auch fuer Super-Admins zu. {grund}")

    if zweck == "lesen":
        return ziel

    grund = _nurlesegrund(ziel, db)
    if grund:
        raise PfadFehler(grund)

    if zweck != "loeschen":
        return ziel

    if _tiefe_unter_wurzel(ziel) < MIN_TIEFE_LOESCHEN:
        raise PfadFehler(
            f"'{ziel}' liegt direkt unter der Laufwerks- oder Freigabewurzel. So dicht "
            f"am Anfang wird hier nichts geloescht.")
    if db is not None:
        grund = _serverordner(db, ziel)
        if grund:
            raise PfadFehler(grund)
    grund = _enthaelt_geheimes(ziel)
    if grund:
        raise PfadFehler(f"Dieser Ordner wird nicht geloescht. {grund}")
    return ziel


# ------------------------------------------------------------------- Operationen

def auflisten(ziel: Path) -> dict:
    """Ein Verzeichnis auflisten - Name, Art, Groesse, Datum."""
    eintraege: list[dict] = []
    try:
        for kind in sorted(ziel.iterdir(), key=lambda p: (p.is_file(), p.name.lower())):
            try:
                angaben = kind.stat()
                eintraege.append({
                    "name": kind.name,
                    "art": "ordner" if kind.is_dir() else "datei",
                    "bytes": None if kind.is_dir() else angaben.st_size,
                    "geaendert": int(angaben.st_mtime),
                })
            except OSError:
                # Eine Datei, die zwischen iterdir und stat verschwindet, ist kein Grund,
                # die ganze Liste fallen zu lassen.
                eintraege.append({"name": kind.name, "art": "unbekannt"})
            if len(eintraege) >= MAX_EINTRAEGE:
                break
    except OSError as exc:
        raise PfadFehler(f"Der Ordner liess sich nicht lesen: {exc}") from exc
    return {"pfad": str(ziel), "eintraege": eintraege,
            "gekuerzt": len(eintraege) >= MAX_EINTRAEGE}


def _dekodiere_angeschnitten(rohdaten: bytes) -> tuple[str, str]:
    """Bytes dekodieren, die mitten in einem Zeichen enden koennen.

    Ohne das hier waere ein abgeschnittenes UTF-8-Zeichen am Ende ein
    UnicodeDecodeError - und der Rueckfall wuerde die GANZE Datei als cp1252 lesen,
    also jedes Sonderzeichen darin verfaelschen.

    Der Schnitt laesst sich vom echten Kodierungsfehler sauber unterscheiden, und zwar
    nicht an der Fehlerstelle, sondern am Fehler-ENDE: bei einem angeschnittenen Zeichen
    sucht der Dekodierer bis zum Datenende nach Folgebytes (``end == len``), bei einem
    cp1252-Byte mitten im Text endet der Fehlerbereich davor. Diese Unterscheidung ist
    gemessen, nicht geraten - "bis zu drei Bytes abwerfen, bis etwas durchgeht" machte
    aus b"Gr\xfc\xdfe" ein utf-8-taugliches b"Gr".
    """
    from app.services import properties_service

    try:
        return rohdaten.decode("utf-8"), "utf-8"
    except UnicodeDecodeError as fehler:
        schnitt = (fehler.end >= len(rohdaten)
                   and fehler.start >= len(rohdaten) - 3
                   and fehler.start > 0)
        if schnitt:
            try:
                return rohdaten[:fehler.start].decode("utf-8"), "utf-8"
            except UnicodeDecodeError:
                pass
    return properties_service.dekodiere(rohdaten)


def lesen(ziel: Path) -> dict:
    """Eine Textdatei lesen - begrenzt, und ohne die Kodierung zu erraten."""
    try:
        groesse = ziel.stat().st_size
        with ziel.open("rb") as datei:
            rohdaten = datei.read(MAX_LESE_BYTES + 1)
    except OSError as exc:
        raise PfadFehler(f"Die Datei liess sich nicht lesen: {exc}") from exc

    if b"\x00" in rohdaten[:8192]:
        raise PfadFehler(
            "Das ist keine Textdatei. Ein Archiv laesst sich mit 'archiv_ansehen' "
            "oeffnen; alles andere ergibt als Text nur Muell.")

    text, kodierung = _dekodiere_angeschnitten(rohdaten[:MAX_LESE_BYTES])
    gekuerzt = groesse > MAX_LESE_BYTES or len(text) > MAX_LESE_ZEICHEN
    ergebnis = {"pfad": str(ziel), "inhalt": text[:MAX_LESE_ZEICHEN],
                "kodierung": kodierung, "bytes": groesse, "gekuerzt": gekuerzt}
    if gekuerzt:
        ergebnis["hinweis"] = (f"Nur der Anfang: die Datei hat {groesse} Bytes, gelesen "
                               f"werden hoechstens {MAX_LESE_BYTES}.")
    return ergebnis


def schreiben(ziel: Path, inhalt: str) -> dict:
    """Eine Textdatei schreiben. Eine bestehende behaelt ihre Kodierung.

    Warum nicht einfach UTF-8: eine cp1252-Datei stillschweigend umzukodieren aendert
    jedes Sonderzeichen darin - auch in Zeilen, die niemand angefasst hat. Dieser Fehler
    ist im Manager schon einmal passiert (server.properties), er muss nicht noch einmal
    passieren.
    """
    text = inhalt if isinstance(inhalt, str) else str(inhalt or "")
    if len(text) > MAX_SCHREIB_ZEICHEN:
        raise PfadFehler(f"Mehr als {MAX_SCHREIB_ZEICHEN} Zeichen werden hier nicht "
                         f"geschrieben.")

    kodierung = "utf-8"
    vorher = None
    if ziel.exists():
        if not ziel.is_file():
            raise PfadFehler(f"'{ziel}' ist keine Datei.")
        try:
            vorher = ziel.stat().st_size
            # Zum Erkennen genuegt der Anfang: der Rest der Datei wird ersetzt, von ihr
            # bleibt nur die Kodierung. Eine 2-GB-Datei muss dafuer nicht in den Speicher.
            with ziel.open("rb") as datei:
                _text, kodierung = _dekodiere_angeschnitten(datei.read(MAX_LESE_BYTES))
        except OSError as exc:
            raise PfadFehler(f"Die bestehende Datei liess sich nicht lesen: {exc}") from exc

    # ZUERST kodieren, DANN schreiben. write_text kuerzt die Datei beim Oeffnen und
    # scheitert erst danach am Kodieren - gemessen blieben von 18 Bytes null uebrig.
    try:
        rohdaten = text.encode(kodierung, errors="strict")
    except UnicodeEncodeError as exc:
        raise PfadFehler(
            f"Der Text enthaelt Zeichen, die in der bisherigen Kodierung dieser Datei "
            f"({kodierung}) nicht darstellbar sind: "
            f"{str(exc.object)[exc.start:exc.end]!r}. Die Datei bleibt unveraendert. "
            f"Wenn sie UTF-8 werden soll, muss sie vorher weg.") from exc
    try:
        ziel.parent.mkdir(parents=True, exist_ok=True)
        ziel.write_bytes(rohdaten)
    except OSError as exc:
        raise PfadFehler(f"Die Datei liess sich nicht schreiben: {exc}") from exc

    return {"pfad": str(ziel), "zeichen": len(text), "bytes": len(rohdaten),
            "kodierung": kodierung, "bytes_vorher": vorher,
            "meldung": ("Datei angelegt." if vorher is None else "Datei ueberschrieben.")}


def zaehle(ziel: Path) -> int:
    """Wie viele Eintraege ein Loeschen mitnehmen wuerde. Fuer den Bestaetigungssatz."""
    if ziel.is_file():
        return 1
    gezaehlt = 0
    try:
        for _ in ziel.rglob("*"):
            gezaehlt += 1
            if gezaehlt > MAX_LOESCH_EINTRAEGE:
                break
    except OSError:
        pass
    return gezaehlt


def loeschen(ziel: Path, *, rekursiv: bool = False) -> dict:
    """Eine Datei oder einen Ordner loeschen. Ordner nur, wenn es ausdruecklich dasteht."""
    import shutil

    if ziel.is_file():
        try:
            ziel.unlink()
        except OSError as exc:
            raise PfadFehler(f"Die Datei liess sich nicht loeschen: {exc}") from exc
        return {"pfad": str(ziel), "art": "datei", "meldung": "Datei geloescht."}

    if not ziel.is_dir():
        raise PfadFehler(f"'{ziel}' ist weder Datei noch Ordner.")

    anzahl = zaehle(ziel)
    if anzahl > MAX_LOESCH_EINTRAEGE:
        raise PfadFehler(f"Darunter liegen mehr als {MAX_LOESCH_EINTRAEGE} Eintraege. So "
                         f"viel loescht der Assistent nicht.")
    if anzahl and not rekursiv:
        raise PfadFehler(
            f"'{ziel}' ist nicht leer ({anzahl} Eintraege). Mit rekursiv=true loeschen, "
            f"wenn das so gewollt ist.")
    try:
        shutil.rmtree(ziel) if anzahl else ziel.rmdir()
    except OSError as exc:
        raise PfadFehler(f"Der Ordner liess sich nicht loeschen: {exc}") from exc
    return {"pfad": str(ziel), "art": "ordner", "eintraege": anzahl,
            "meldung": f"Ordner geloescht ({anzahl} Eintraege)."}


def verschieben(quelle: Path, ziel: Path) -> dict:
    """Verschieben oder umbenennen. Ein bestehendes Ziel wird NICHT ueberschrieben."""
    import shutil

    if ziel.exists():
        raise PfadFehler(f"'{ziel}' gibt es schon. Verschieben ueberschreibt hier nichts - "
                         f"das Ziel muss frei sein oder vorher weg.")
    try:
        ziel.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(quelle), str(ziel))
    except OSError as exc:
        raise PfadFehler(f"Verschieben ging nicht: {exc}") from exc
    return {"quelle": str(quelle), "ziel": str(ziel), "meldung": "Verschoben."}
