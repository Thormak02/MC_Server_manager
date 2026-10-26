"""Spieler-Koepfe: Skin-Dateien ueber den Manager ausliefern, nicht ueber einen Fremddienst.

WARUM nicht einfach Crafatar/mc-heads im <img src>: dann holt der BROWSER jedes
Betrachters das Bild bei einem Dritten ab und liefert dabei seine IP mit. Das ist
derselbe Sachverhalt, der 2022 die Google-Fonts-Abmahnungen ausgeloest hat - und der
Grund, warum die Schriften in diesem Projekt lokal liegen. Hier holt der Manager die
Datei einmal, legt sie auf Platte und liefert sie selbst aus.

WARUM kein serverseitiger Zuschnitt: der Kopf ist der 8x8-Ausschnitt (8,8)-(16,16)
einer 64x64-Skin-Datei. Ihn hier auszuschneiden hiesse, ohne Pillow eine eigene
PNG-Dekodierung zu schreiben. Stattdessen liefern wir die Skin-Datei unveraendert
aus und schneiden per CSS zu (background-position + image-rendering: pixelated).
Damit kommt das Projekt ohne neue Abhaengigkeit aus.
"""
from __future__ import annotations

import base64
import json
import re
import struct
import threading
import time
import urllib.request
import zlib
from pathlib import Path

from app.core.config import get_settings

# Minecraft-Namen: 3-16 Zeichen aus [A-Za-z0-9_]. UUIDs mit oder ohne Bindestriche.
_NAME_MUSTER = re.compile(r"^[A-Za-z0-9_]{1,16}$")
_UUID_MUSTER = re.compile(r"^[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?"
                          r"[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}$")

# Skins aendern sich selten -> lange Gueltigkeit. Fehlschlaege kurz merken, damit ein
# Offline-Mode-Name nicht bei jedem Seitenaufruf erneut bei Mojang anklopft (deren
# Namens-Endpunkt hat ein Ratenlimit).
_TTL_TREFFER = 7 * 24 * 3600.0
_TTL_FEHLSCHLAG = 6 * 3600.0

_ZEITLIMIT = 5.0

_FEHL_CACHE: dict[str, float] = {}
_FEHL_LOCK = threading.Lock()


def _png(breite: int, hoehe: int, pixel: bytes) -> bytes:
    """Minimales RGBA-PNG aus rohen Pixeln bauen (nur zlib/struct aus der Standardbibliothek)."""
    roh = b"".join(
        b"\x00" + pixel[y * breite * 4:(y + 1) * breite * 4] for y in range(hoehe)
    )

    def block(typ: bytes, daten: bytes) -> bytes:
        inhalt = typ + daten
        return (struct.pack(">I", len(daten)) + inhalt
                + struct.pack(">I", zlib.crc32(inhalt) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + block(b"IHDR", struct.pack(">IIBBBBB", breite, hoehe, 8, 6, 0, 0, 0))
            + block(b"IDAT", zlib.compress(roh, 9))
            + block(b"IEND", b""))


def _baue_standard_skin() -> bytes:
    """Neutraler Ersatzkopf fuer Spieler ohne Mojang-Skin (Offline-Mode, Tippfehler, Netz weg).

    Bewusst grau und gesichtslos statt Steve-Nachbau: es soll erkennbar "unbekannt"
    heissen und nicht nach einem bestimmten Spieler aussehen. Nur der Kopfbereich
    (8,8)-(16,16) ist gefuellt, der Rest bleibt durchsichtig - mehr schneidet das CSS
    ohnehin nicht aus.
    """
    breite = hoehe = 64
    pixel = bytearray(breite * hoehe * 4)   # komplett durchsichtig

    def setze(x: int, y: int, farbe: tuple[int, int, int]) -> None:
        i = (y * breite + x) * 4
        pixel[i:i + 4] = bytes(farbe) + b"\xff"

    kopf = (0x6B, 0x72, 0x80)      # gedecktes Blaugrau
    schopf = (0x4A, 0x51, 0x5C)    # dunklere obere Kante
    auge = (0x2B, 0x30, 0x38)

    for y in range(8, 16):
        for x in range(8, 16):
            setze(x, y, schopf if y < 10 else kopf)
    setze(10, 12, auge)
    setze(13, 12, auge)

    return _png(breite, hoehe, bytes(pixel))


STANDARD_SKIN_PNG = _baue_standard_skin()


def _cache_ordner() -> Path:
    ordner = get_settings().data_dir / "avatars"
    ordner.mkdir(parents=True, exist_ok=True)
    return ordner


def normalisiere(spieler: str) -> str | None:
    """Eingabe auf einen sicheren Cache-/URL-Schluessel bringen.

    ``None``, wenn es weder ein Minecraft-Name noch eine UUID ist - damit kann aus
    einem Listeneintrag wie einer IP-Adresse oder ``*`` kein Dateiname und keine
    Anfrage nach draussen werden.
    """
    roh = (spieler or "").strip()
    if _UUID_MUSTER.match(roh):
        return roh.replace("-", "").lower()
    if _NAME_MUSTER.match(roh):
        return roh.lower()
    return None


def ist_online_uuid(schluessel: str) -> bool:
    """True, wenn die UUID von Mojang stammen KANN (Version 4, zufaellig erzeugt).

    Server im Offline-Mode vergeben die UUID selbst, als MD5 ueber
    "OfflinePlayer:<name>" - das ergibt Version 3. Solche UUIDs kennt der
    Sessionserver nicht; mit ihnen zu fragen liefert garantiert nichts. An der
    13. Stelle (dritte Gruppe, erstes Zeichen) steht die Version.
    """
    roh = (schluessel or "").replace("-", "")
    return len(roh) == 32 and roh[12] == "4"


def _hole(url: str, zeitlimit: float) -> bytes:
    anfrage = urllib.request.Request(url, headers={"User-Agent": "mcsm-avatar"})
    with urllib.request.urlopen(anfrage, timeout=zeitlimit) as antwort:
        return antwort.read()


def _uuid_zu_name(name: str, zeitlimit: float) -> str | None:
    daten = json.loads(_hole(
        "https://api.mojang.com/users/profiles/minecraft/" + name, zeitlimit
    ).decode("utf-8"))
    return str((daten or {}).get("id") or "") or None


def _skin_url(uuid: str, zeitlimit: float) -> str | None:
    """Skin-Adresse aus dem Sessionserver-Profil lesen.

    Das Profil liefert die Texturen base64-kodiert in einer ``properties``-Liste.
    """
    daten = json.loads(_hole(
        f"https://sessionserver.mojang.com/session/minecraft/profile/{uuid}", zeitlimit
    ).decode("utf-8"))
    for eigenschaft in (daten or {}).get("properties", []):
        if eigenschaft.get("name") != "textures":
            continue
        inhalt = json.loads(base64.b64decode(eigenschaft.get("value") or ""))
        url = (((inhalt or {}).get("textures") or {}).get("SKIN") or {}).get("url")
        if url:
            # Mojang liefert teils http:// - auf https anheben, sonst laedt der
            # Manager ungeschuetzt und manche Umgebungen blocken es.
            return str(url).replace("http://", "https://", 1)
    return None


def _frisch(pfad: Path, ttl: float) -> bool:
    try:
        return (time.time() - pfad.stat().st_mtime) < ttl
    except OSError:
        return False


def _fehlschlag_gemerkt(schluessel: str) -> bool:
    with _FEHL_LOCK:
        bis = _FEHL_CACHE.get(schluessel)
        if bis is None:
            return False
        if time.time() >= bis:
            _FEHL_CACHE.pop(schluessel, None)
            return False
        return True


def _merke_fehlschlag(schluessel: str) -> None:
    with _FEHL_LOCK:
        _FEHL_CACHE[schluessel] = time.time() + _TTL_FEHLSCHLAG


def leere_caches() -> None:
    """Nur fuer Tests - der Fehlschlag-Cache ist prozessweit."""
    with _FEHL_LOCK:
        _FEHL_CACHE.clear()


def skin_png(spieler: str, *, zeitlimit: float = _ZEITLIMIT) -> tuple[bytes, bool]:
    """Skin-Datei zu einem Namen oder einer UUID holen.

    Rueckgabe ``(png, echt)``. ``echt=False`` heisst: Ersatzkopf - der Aufrufer kann
    das nutzen, um kuerzer zwischenzuspeichern. Diese Funktion wirft NIE; ein fehlender
    Skin ist der Normalfall (Offline-Mode-Server, geloeschte Konten, Netz weg) und darf
    keine Listenseite zerlegen.
    """
    schluessel = normalisiere(spieler)
    if schluessel is None:
        return STANDARD_SKIN_PNG, False

    datei = _cache_ordner() / f"{schluessel}.png"
    if _frisch(datei, _TTL_TREFFER):
        try:
            return datei.read_bytes(), True
        except OSError:
            pass
    if _fehlschlag_gemerkt(schluessel):
        return STANDARD_SKIN_PNG, False

    try:
        uuid = schluessel if len(schluessel) == 32 else _uuid_zu_name(schluessel, zeitlimit)
        url = _skin_url(uuid, zeitlimit) if uuid else None
        png = _hole(url, zeitlimit) if url else None
    except Exception:  # noqa: BLE001 - Netz/JSON/404 -> Ersatzkopf
        png = None

    if not png or not png.startswith(b"\x89PNG"):
        # Eine veraltete Datei ist immer noch besser als der Ersatzkopf.
        if datei.is_file():
            try:
                return datei.read_bytes(), True
            except OSError:
                pass
        _merke_fehlschlag(schluessel)
        return STANDARD_SKIN_PNG, False

    try:
        datei.write_bytes(png)
    except OSError:
        pass   # ohne Plattencache geht es auch, nur langsamer
    return png, True
