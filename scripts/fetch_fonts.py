"""Schriften von Google Fonts holen und LOKAL ablegen.

Warum lokal: bindet die Seite fonts.googleapis.com direkt ein, wird die IP-Adresse jedes
Besuchers an Google uebertragen, bevor er zustimmen konnte. Das LG Muenchen I hat das am
20.01.2022 (Az. 3 O 17493/20) als Verstoss gegen das allgemeine Persoenlichkeitsrecht
gewertet; in der Folge gab es eine Abmahnwelle. Lokal ausgeliefert entfaellt der Drittabruf
vollstaendig.

Dieses Skript ist der nachvollziehbare Weg, wie die Dateien unter
``app/web/static/fonts/`` entstanden sind - und wie man sie erneuert, wenn eine
Schriftversion aktualisiert werden soll:

    python scripts/fetch_fonts.py

Es schreibt die .woff2-Dateien, das Stylesheet ``app/web/static/css/fonts.css`` und
``app/web/static/fonts/LICENSE.txt``. Ein Aufruf ist idempotent: gleiche Eingabe,
gleiches Ergebnis.
"""

from __future__ import annotations

import re
import urllib.request
from pathlib import Path

# Ohne modernen User-Agent liefert Google noch TTF statt des viel kleineren woff2.
_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
)

# Genau die Familien und Schnitte, die base.html/app.css benutzen.
_CSS_URL = (
    "https://fonts.googleapis.com/css2?"
    "family=Manrope:wght@400;500;700;800&family=IBM+Plex+Mono:wght@400;500&display=swap"
)

# Nur was die Oberflaeche braucht. Die Oberflaeche ist deutsch und Minecraft-Namen sind
# ASCII; fehlt doch einmal ein Zeichen, faellt der Browser auf eine Systemschrift zurueck
# und die Seite bleibt benutzbar. Alle sechs Subsets waeren rund dreimal so viele Dateien.
_SUBSETS = {"latin", "latin-ext"}

_LICENCES = [("Manrope", "manrope"), ("IBM Plex Mono", "ibmplexmono")]

_ROOT = Path(__file__).resolve().parents[1]
_FONTS_DIR = _ROOT / "app" / "web" / "static" / "fonts"
_CSS_PATH = _ROOT / "app" / "web" / "static" / "css" / "fonts.css"

_HEADER = """/* Lokal ausgelieferte Schriften - KEIN Abruf bei Google.
 *
 * Warum: eine Einbindung ueber fonts.googleapis.com uebertraegt die IP-Adresse jedes
 * Besuchers an Google, bevor er zugestimmt hat. Das LG Muenchen I hat das am 20.01.2022
 * (Az. 3 O 17493/20) als Verstoss gegen das allgemeine Persoenlichkeitsrecht gewertet
 * und war Anlass einer Abmahnwelle. Lokal ausgeliefert entfaellt der Drittabruf.
 *
 * Umfang: nur die Subsets latin und latin-ext. Fuer die deutschsprachige Oberflaeche
 * und ASCII-Spielernamen reicht das; fehlt ein Zeichen, faellt der Browser auf eine
 * Systemschrift zurueck - die Seite bleibt benutzbar.
 *
 * NICHT von Hand bearbeiten - erzeugt von scripts/fetch_fonts.py.
 * Manrope und IBM Plex Mono stehen unter der SIL Open Font License 1.1,
 * siehe fonts/LICENSE.txt.
 */

"""


def _get(url: str) -> bytes:
    return urllib.request.urlopen(
        urllib.request.Request(url, headers={"User-Agent": _UA}), timeout=30
    ).read()


def fetch_fonts() -> tuple[int, int]:
    """Schriften laden und Stylesheet schreiben -> (Anzahl Dateien, Bytes)."""
    _FONTS_DIR.mkdir(parents=True, exist_ok=True)
    _CSS_PATH.parent.mkdir(parents=True, exist_ok=True)
    css = _get(_CSS_URL).decode("utf-8")

    # Google stellt jedem @font-face einen Subset-Kommentar voran ("/* latin */").
    parts = re.split(r"/\*\s*([a-z-]+)\s*\*/\s*", css)
    blocks: list[str] = []
    count = total = 0
    for i in range(1, len(parts) - 1, 2):
        subset, block = parts[i], parts[i + 1]
        if subset not in _SUBSETS or "@font-face" not in block:
            continue
        # Aendert Google das CSS-Format, soll das hier sichtbar scheitern statt still
        # eine halbe Schriftfamilie zu schreiben.
        family_m = re.search(r"font-family: '([^']+)'", block)
        weight_m = re.search(r"font-weight: (\d+)", block)
        url_m = re.search(r"url\((https://[^)]+)\)", block)
        if not (family_m and weight_m and url_m):
            raise RuntimeError(
                f"Unerwartetes @font-face-Format im Subset {subset} - "
                "Google hat das CSS geaendert, dieses Skript muss angepasst werden."
            )
        family, weight, url = family_m.group(1), weight_m.group(1), url_m.group(1)
        name = f"{family.replace(' ', '')}-{weight}-{subset}.woff2"
        data = _get(url)
        (_FONTS_DIR / name).write_bytes(data)
        count += 1
        total += len(data)
        blocks.append(
            f"/* {subset} */\n"
            + block.strip().replace(f"url({url})", f"url('/static/fonts/{name}')")
        )

    _CSS_PATH.write_text(_HEADER + "\n\n".join(blocks) + "\n", encoding="utf-8")
    return count, total


def fetch_licences() -> None:
    """OFL-Texte mitliefern - die Lizenz verlangt das ausdruecklich."""
    chunks = []
    for family, slug in _LICENCES:
        text = _get(
            f"https://raw.githubusercontent.com/google/fonts/main/ofl/{slug}/OFL.txt"
        ).decode("utf-8")
        chunks.append(f"{'=' * 78}\n{family}\n{'=' * 78}\n\n{text.strip()}\n")
    header = (
        "Die in diesem Ordner ausgelieferten Schriftdateien stehen unter der\n"
        "SIL Open Font License 1.1. Die Lizenz verlangt, dass sie den Dateien\n"
        "beiliegt - daher dieser Text.\n\n"
        "Bezogen von Google Fonts, Subsets latin und latin-ext.\n"
        "Erneuern: python scripts/fetch_fonts.py\n\n"
    )
    (_FONTS_DIR / "LICENSE.txt").write_text(header + "\n".join(chunks), encoding="utf-8")


def main() -> None:
    count, total = fetch_fonts()
    fetch_licences()
    print(f"{count} Schriftdateien ({total / 1024:.0f} KB) -> {_FONTS_DIR}")
    print(f"Stylesheet -> {_CSS_PATH}")


if __name__ == "__main__":
    main()
