import gzip
import zlib
from pathlib import Path

from app.models.server import Server
from app.services.console_service import console_service
from app.services.process_service import get_log_directory_for_server


_LOG_LEVELS = {"all", "warning", "error"}

# Obergrenze beim Einsammeln, bevor nach Alter sortiert wird.
_SAMMEL_GRENZE = 2000


def _is_inside(base: Path, target: Path) -> bool:
    return target == base or base in target.parents


def _server_logs_dir(server: Server) -> Path:
    return (Path(server.base_path).expanduser().resolve() / "logs").resolve()


def _runtime_logs_dir(server: Server) -> Path:
    return get_log_directory_for_server(server.id).resolve()


def _crash_reports_dir(server: Server) -> Path:
    """Minecraft legt Crash-Reports NICHT in logs/ ab, sondern daneben.

    Fuer eine Crash-Analyse ist das die aussagekraeftigste Datei ueberhaupt: sie enthaelt
    den Stacktrace, die geladenen Mods und den Zustand der Welt zum Zeitpunkt des
    Absturzes. Ohne diese Quelle kann die Analyse nur das Serverlog sehen, in dem oft
    bloss "Server crashed" steht.
    """
    return (Path(server.base_path).expanduser().resolve() / "crash-reports").resolve()


def list_log_files(server: Server, *, max_files: int = 200) -> list[dict[str, object]]:
    entries: list[dict[str, object]] = []

    runtime_dir = _runtime_logs_dir(server)
    for file in runtime_dir.glob("*.log"):
        if not file.is_file():
            continue
        stats = file.stat()
        entries.append(
            {
                "key": f"runtime:{file.name}",
                "label": f"Runtime: {file.name}",
                "source": "runtime",
                "name": file.name,
                "size_bytes": stats.st_size,
                "mtime": stats.st_mtime,
            }
        )

    server_log_dir = _server_logs_dir(server)
    if server_log_dir.exists() and server_log_dir.is_dir():
        for file in server_log_dir.rglob("*"):
            if not file.is_file():
                continue
            relative = file.relative_to(server_log_dir).as_posix()
            stats = file.stat()
            entries.append(
                {
                    "key": f"server:{relative}",
                    "label": f"Server: logs/{relative}",
                    "source": "server",
                    "name": relative,
                    "size_bytes": stats.st_size,
                    "mtime": stats.st_mtime,
                }
            )
            # Eine Obergrenze gegen Ordner mit Tausenden Dateien. Sie liegt deutlich ueber
            # max_files, weil gleich nach ALTER sortiert wird: ein frueher Abbruch bei
            # genau max_files wuerde nach Ordnerreihenfolge abschneiden und koennte so
            # gerade die neueste Datei weglassen - die meistgebrauchte.
            if len(entries) >= _SAMMEL_GRENZE:
                break

    crash_dir = _crash_reports_dir(server)
    if crash_dir.exists() and crash_dir.is_dir():
        for file in sorted(crash_dir.iterdir(), reverse=True):
            if not file.is_file() or file.suffix.lower() not in {".txt", ".log"}:
                continue
            stats = file.stat()
            entries.append(
                {
                    "key": f"crash:{file.name}",
                    "label": f"Crash-Report: {file.name}",
                    "source": "crash",
                    "name": file.name,
                    "size_bytes": stats.st_size,
                    "mtime": stats.st_mtime,
                }
            )
            if len(entries) >= _SAMMEL_GRENZE:
                break

    entries.sort(key=lambda item: float(item.get("mtime", 0.0)), reverse=True)
    return entries[:max_files]


def _wert_ist_harmlos(value: str) -> bool:
    """Darf dieser Wert zu einem Pfad gemacht werden?

    Geprueft wird VOR jedem Dateisystemzugriff, denn schon das Aufloesen eines Pfades
    kann eine Netzverbindung aufbauen. Erlaubt sind nur relative Namen mit normalen
    Zeichen - alles andere wird abgewiesen, nicht zurechtgebogen.
    """
    roh = (value or "").strip()
    if not roh or len(roh) > 400:
        return False
    if "\x00" in roh:
        return False
    normalisiert = roh.replace("\\", "/")
    if normalisiert.startswith("/") or normalisiert.startswith("//"):
        return False          # absolut oder UNC
    if ":" in roh:
        return False          # Laufwerksbuchstabe oder NTFS-Datenstrom
    teile = [p for p in normalisiert.split("/") if p]
    if any(p in {"..", "."} for p in teile):
        return False
    # Windows-Geraetenamen: ein Zugriff darauf haengt oder oeffnet ein Geraet.
    geraete = {"con", "prn", "aux", "nul", "clock$"}
    geraete |= {f"com{i}" for i in range(1, 10)} | {f"lpt{i}" for i in range(1, 10)}
    return not any(p.split(".")[0].lower() in geraete for p in teile)


def _resolve_log_file(server: Server, key: str) -> tuple[Path, str]:
    if not key or ":" not in key:
        raise ValueError("Ungueltiger Logdatei-Schluessel.")
    source, value = key.split(":", 1)
    if source not in {"runtime", "server", "crash"}:
        raise ValueError("Unbekannte Logquelle.")

    # Der Wert kommt aus dem Browser und wird erst GEPRUEFT, dann zu einem Pfad gemacht.
    # Die Reihenfolge ist wichtig: schon `(base / value).resolve()` greift auf das
    # Dateisystem zu. Bei einem UNC-Wert wie "//angreifer/freigabe/x" baut Windows dabei
    # eine SMB-Verbindung zu diesem Host auf und meldet sich automatisch mit dem Konto
    # des Dienstes an - der Manager laeuft als SYSTEM. Die Containment-Pruefung weiter
    # unten kommt dafuer zu spaet, sie laeuft erst nach resolve(). Ein Laufwerksbuchstabe
    # ("C:datei") wuerde ausserdem den Basispfad verwerfen.
    if not _wert_ist_harmlos(value):
        raise ValueError("Ungueltiger Logdatei-Pfad.")

    if source == "runtime":
        base = _runtime_logs_dir(server)
        path = (base / value).resolve()
        label = f"Runtime: {value}"
    elif source == "crash":
        base = _crash_reports_dir(server)
        path = (base / value).resolve()
        label = f"Crash-Report: {value}"
    else:
        base = _server_logs_dir(server)
        path = (base / value).resolve()
        label = f"Server: logs/{value}"

    if not _is_inside(base, path):
        raise ValueError("Ungueltiger Logdatei-Pfad.")
    if not path.exists() or not path.is_file():
        raise ValueError("Logdatei nicht gefunden.")
    return path, label


def _oeffne_text(file_path: Path):
    """Eine Logdatei als Text oeffnen, auch wenn sie gepackt ist.

    Minecraft packt alte Logs beim Rotieren zu .log.gz. Ungepackt gelesen kommt
    Binaermuell heraus - und eine KI-Analyse wuerde fuer diesen Muell bezahlen.
    """
    if file_path.suffix.lower() == ".gz":
        return gzip.open(file_path, "rt", encoding="utf-8", errors="ignore")
    return open(file_path, "r", encoding="utf-8", errors="ignore")


def _lies_zeilen(file_path: Path, *, kopf: int, schwanz: int) -> tuple[list[str], list[str], int]:
    """Kopf und Schwanz einer Datei lesen, ohne sie ganz im Speicher zu halten.

    Vorher wurde die ganze Datei in eine Liste gelesen. Bei einem rotierten .log.gz sind
    das leicht ueber ein Gigabyte entpackt - ein Klick konnte den Dienst an die
    Speichergrenze bringen und damit alle verwalteten Serverprozesse mitnehmen, denn die
    haengen an ihm.

    Rueckgabe: (Kopfzeilen, Schwanzzeilen, Gesamtzahl der Zeilen).
    """
    from collections import deque

    kopf_zeilen: list[str] = []
    schwanz_zeilen: deque = deque(maxlen=max(0, schwanz))
    gesamt = 0
    try:
        with _oeffne_text(file_path) as handle:
            for roh in handle:
                zeile = roh.rstrip("\r\n")
                if len(kopf_zeilen) < max(0, kopf):
                    kopf_zeilen.append(zeile)
                if schwanz > 0:
                    schwanz_zeilen.append(zeile)
                gesamt += 1
    except (OSError, EOFError, gzip.BadGzipFile, zlib.error) as exc:
        # Ein abgebrochenes oder defektes Archiv ist keine Stoerung des Managers, sondern
        # eine kaputte Datei. Ohne diesen Fang kaeme eine 500-Seite - und zwar genau
        # dann, wenn man die Logseite am dringendsten braucht.
        raise ValueError(f"Die Logdatei konnte nicht gelesen werden: {exc}") from exc
    return kopf_zeilen, list(schwanz_zeilen), gesamt


def read_log_file(server: Server, key: str, limit_lines: int = 800) -> tuple[list[str], str]:
    file_path, label = _resolve_log_file(server, key)
    schwanz = limit_lines if limit_lines > 0 else 0
    if schwanz == 0:
        # Ohne Grenze: alles lesen, aber mit demselben Fehlerschutz.
        _, zeilen, _ = _lies_zeilen(file_path, kopf=0, schwanz=10_000_000)
        return zeilen, label
    _, zeilen, _ = _lies_zeilen(file_path, kopf=0, schwanz=schwanz)
    return zeilen, label


def lies_fuer_analyse(server: Server, key: str, *, max_zeilen: int = 20_000,
                      kopf_zeilen: int = 400) -> tuple[list[str], str, int]:
    """Kopf UND Ende einer Logdatei lesen - fuer die Diagnose.

    ``read_log_file`` liefert nur den Schwanz. Bei einem Log mit 200.000 Zeilen fehlt
    damit der Kopf, und genau dort stehen Serversoftware, Version und die geladenen Mods -
    ohne die kann keine Antwort sagen, WELCHE Mod abgestuerzt ist. Das ist die Frage, fuer
    die die Funktion da ist.

    Rueckgabe: (Zeilen, Bezeichnung, Gesamtzahl im Log). Die Gesamtzahl ist die ECHTE,
    damit die Oberflaeche nicht "von 20000 Zeilen" behauptet, wenn es 200000 sind.
    """
    file_path, label = _resolve_log_file(server, key)
    schwanz = max(1, max_zeilen - max(0, kopf_zeilen))
    kopf, ende, gesamt = _lies_zeilen(file_path, kopf=max(0, kopf_zeilen),
                                      schwanz=schwanz)
    if gesamt <= max_zeilen:
        return ende[-gesamt:] if gesamt else [], label, gesamt
    fehlt = gesamt - len(kopf) - len(ende)
    if fehlt > 0:
        return ([*kopf, f"[... {fehlt} Zeilen ausgelassen ...]", *ende], label, gesamt)
    return ([*kopf, *ende], label, gesamt)


def get_download_log_file(server: Server, key: str) -> tuple[Path, str]:
    path, label = _resolve_log_file(server, key)
    return path, path.name if path.name else label.replace(":", "-")


def get_console_lines(server_id: int, limit_lines: int = 800) -> list[str]:
    return console_service.get_recent_lines(server_id, limit=limit_lines)


def _matches_log_level(line: str, level: str) -> bool:
    normalized = level if level in _LOG_LEVELS else "all"
    if normalized == "all":
        return True
    lower = line.lower()
    if normalized == "error":
        return any(token in lower for token in ["error", "exception", "fatal", "traceback"])
    if normalized == "warning":
        return any(token in lower for token in ["warn", "warning"])
    return True


def filter_lines(
    lines: list[str],
    query: str | None,
    *,
    level: str = "all",
) -> list[str]:
    pattern = (query or "").lower().strip()
    filtered = [
        line
        for line in lines
        if _matches_log_level(line, level)
        and (not pattern or pattern in line.lower())
    ]
    return filtered
