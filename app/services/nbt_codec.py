"""NBT lesen UND schreiben, typerhaltend - fuer gezielte Eingriffe in Weltdateien.

Warum ein eigener Codec, obwohl ``world_bake_service`` schon einen Leser hat: dieser Leser
gibt reine Python-Werte zurueck, und damit ist die Tag-Art verloren. Ein ``int`` kann ein
TAG_Byte, TAG_Short, TAG_Int oder TAG_Long gewesen sein. Beim Zurueckschreiben waere die
Entscheidung geraten - und eine Spielregel, die als Byte gelesen und als Int geschrieben
wird, macht die Datei fuer Minecraft unlesbar.

Hier traegt deshalb jeder Wert seinen Typ mit (``Tag``), und ``schreibe(lies(x)) == x`` gilt
Byte fuer Byte. Genau das pruefen die Tests gegen die echte level.dat und echte Chunks:
scheitert der Round-Trip, wird gar nichts geschrieben.

Gebraucht wird das fuer Eingriffe, bei denen sich die Datenlaenge aendert und ein
Byte-Tausch (siehe ``world_identity_service``) nicht ausreicht:

* ``Data.DragonFight`` aus der End-Welt in die Hauptwelt uebernehmen, damit der
  Enderdrache nicht wieder auftaucht.
* ``Data.LevelName`` auf den neuen Weltnamen setzen.
* Einzelne Felder der Nether-/End-level.dat uebernehmen (Weltgrenze, Spielregeln), die beim
  Zusammenlegen der Dimensionen sonst verloren gehen.
"""

from __future__ import annotations

import gzip
import struct
import zlib
from pathlib import Path

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12

_NAMEN = {
    TAG_BYTE: "byte", TAG_SHORT: "short", TAG_INT: "int", TAG_LONG: "long",
    TAG_FLOAT: "float", TAG_DOUBLE: "double", TAG_BYTE_ARRAY: "byte[]",
    TAG_STRING: "string", TAG_LIST: "list", TAG_COMPOUND: "compound",
    TAG_INT_ARRAY: "int[]", TAG_LONG_ARRAY: "long[]",
}


class NbtFehler(Exception):
    """Die Daten sind kein gueltiges NBT (oder eine Form, die dieser Codec nicht kennt)."""


class Tag:
    """Ein NBT-Wert samt seiner Tag-Art.

    ``wert`` ist je nach ``typ``:
      * Zahl/Text: int, float, str
      * TAG_BYTE_ARRAY: bytes
      * TAG_INT_ARRAY / TAG_LONG_ARRAY: list[int]
      * TAG_LIST: list[Tag] - alle mit demselben Typ, der in ``elementtyp`` steht
      * TAG_COMPOUND: dict[str, Tag], Reihenfolge wie in der Datei (wichtig fuer den
        Byte-genauen Round-Trip)
    """

    __slots__ = ("typ", "wert", "elementtyp")

    def __init__(self, typ: int, wert, elementtyp: int = TAG_END) -> None:
        self.typ = typ
        self.wert = wert
        self.elementtyp = elementtyp

    def __repr__(self) -> str:
        art = _NAMEN.get(self.typ, f"typ{self.typ}")
        if self.typ == TAG_COMPOUND:
            return f"Tag({art}, {len(self.wert)} Felder)"
        if self.typ == TAG_LIST:
            return f"Tag({art}<{_NAMEN.get(self.elementtyp, '?')}>, {len(self.wert)})"
        return f"Tag({art}, {self.wert!r})"

    def __eq__(self, andere) -> bool:
        if not isinstance(andere, Tag):
            return NotImplemented
        return (self.typ == andere.typ and self.elementtyp == andere.elementtyp
                and self.wert == andere.wert)

    # --- bequemer Zugriff auf Compounds ---------------------------------------
    def hole(self, *pfad: str):
        """Verschachteltes Feld holen, oder ``None``."""
        knoten = self
        for teil in pfad:
            if knoten is None or knoten.typ != TAG_COMPOUND:
                return None
            knoten = knoten.wert.get(teil)
        return knoten

    def setze(self, name: str, wert: "Tag") -> None:
        if self.typ != TAG_COMPOUND:
            raise NbtFehler("setze() geht nur auf einem Compound.")
        self.wert[name] = wert

    def entferne(self, name: str) -> bool:
        if self.typ != TAG_COMPOUND:
            return False
        return self.wert.pop(name, None) is not None


# ------------------------------------------------------------------------- Lesen

class _Leser:
    __slots__ = ("d", "i")

    def __init__(self, daten: bytes) -> None:
        self.d = daten
        self.i = 0

    def _nimm(self, n: int) -> bytes:
        if self.i + n > len(self.d):
            raise NbtFehler("NBT endet mitten im Wert.")
        stueck = self.d[self.i:self.i + n]
        self.i += n
        return stueck

    def _entpacke(self, form: str, n: int):
        return struct.unpack(form, self._nimm(n))[0]

    def _text(self) -> str:
        laenge = self._entpacke(">H", 2)
        # Minecraft schreibt modifiziertes UTF-8; fuer die Felder, um die es hier geht,
        # ist das deckungsgleich mit UTF-8. "surrogatepass" haelt exotische Faelle
        # durch den Round-Trip, statt sie stillschweigend zu veraendern.
        return self._nimm(laenge).decode("utf-8", "surrogatepass")

    def nutzlast(self, typ: int) -> Tag:
        if typ == TAG_BYTE:
            return Tag(typ, self._entpacke(">b", 1))
        if typ == TAG_SHORT:
            return Tag(typ, self._entpacke(">h", 2))
        if typ == TAG_INT:
            return Tag(typ, self._entpacke(">i", 4))
        if typ == TAG_LONG:
            return Tag(typ, self._entpacke(">q", 8))
        if typ == TAG_FLOAT:
            return Tag(typ, self._nimm(4))     # Rohbytes: haelt NaN und -0.0 exakt
        if typ == TAG_DOUBLE:
            return Tag(typ, self._nimm(8))
        if typ == TAG_BYTE_ARRAY:
            return Tag(typ, self._nimm(self._entpacke(">i", 4)))
        if typ == TAG_STRING:
            return Tag(typ, self._text())
        if typ == TAG_LIST:
            elementtyp = self._entpacke(">b", 1)
            anzahl = self._entpacke(">i", 4)
            if anzahl < 0:
                anzahl = 0
            eintraege = [self.nutzlast(elementtyp) for _ in range(anzahl)] \
                if elementtyp != TAG_END else []
            return Tag(typ, eintraege, elementtyp)
        if typ == TAG_COMPOUND:
            felder: dict[str, Tag] = {}
            while True:
                art = self._entpacke(">b", 1)
                if art == TAG_END:
                    break
                name = self._text()
                felder[name] = self.nutzlast(art)
            return Tag(typ, felder)
        if typ == TAG_INT_ARRAY:
            anzahl = self._entpacke(">i", 4)
            return Tag(typ, list(struct.unpack(f">{anzahl}i", self._nimm(4 * anzahl))))
        if typ == TAG_LONG_ARRAY:
            anzahl = self._entpacke(">i", 4)
            return Tag(typ, list(struct.unpack(f">{anzahl}q", self._nimm(8 * anzahl))))
        raise NbtFehler(f"Unbekannte NBT-Art {typ}.")


def lies(daten: bytes) -> tuple[str, Tag]:
    """Rohes (dekomprimiertes) NBT lesen. Rueckgabe: (Name der Wurzel, Wurzel-Tag)."""
    leser = _Leser(daten)
    typ = leser._entpacke(">b", 1)
    if typ != TAG_COMPOUND:
        raise NbtFehler("NBT-Wurzel ist kein Compound.")
    name = leser._text()
    return name, leser.nutzlast(TAG_COMPOUND)


# ----------------------------------------------------------------------- Schreiben

def _schreibe_text(aus: bytearray, wert: str) -> None:
    roh = wert.encode("utf-8", "surrogatepass")
    aus += struct.pack(">H", len(roh))
    aus += roh


def _schreibe_nutzlast(aus: bytearray, tag: Tag) -> None:
    typ = tag.typ
    if typ == TAG_BYTE:
        aus += struct.pack(">b", tag.wert)
    elif typ == TAG_SHORT:
        aus += struct.pack(">h", tag.wert)
    elif typ == TAG_INT:
        aus += struct.pack(">i", tag.wert)
    elif typ == TAG_LONG:
        aus += struct.pack(">q", tag.wert)
    elif typ in (TAG_FLOAT, TAG_DOUBLE):
        roh = tag.wert
        if isinstance(roh, float):
            roh = struct.pack(">f" if typ == TAG_FLOAT else ">d", roh)
        erwartet = 4 if typ == TAG_FLOAT else 8
        if len(roh) != erwartet:
            raise NbtFehler(f"Gleitkommawert hat {len(roh)} statt {erwartet} Bytes.")
        aus += roh
    elif typ == TAG_BYTE_ARRAY:
        aus += struct.pack(">i", len(tag.wert))
        aus += tag.wert
    elif typ == TAG_STRING:
        _schreibe_text(aus, tag.wert)
    elif typ == TAG_LIST:
        elementtyp = tag.elementtyp if tag.wert or tag.elementtyp else TAG_END
        if tag.wert:
            elementtyp = tag.wert[0].typ
            if any(e.typ != elementtyp for e in tag.wert):
                raise NbtFehler("NBT-Liste mit gemischten Typen.")
        aus += struct.pack(">b", elementtyp)
        aus += struct.pack(">i", len(tag.wert))
        for eintrag in tag.wert:
            _schreibe_nutzlast(aus, eintrag)
    elif typ == TAG_COMPOUND:
        for name, feld in tag.wert.items():
            aus += struct.pack(">b", feld.typ)
            _schreibe_text(aus, name)
            _schreibe_nutzlast(aus, feld)
        aus += b"\x00"
    elif typ == TAG_INT_ARRAY:
        aus += struct.pack(">i", len(tag.wert))
        aus += struct.pack(f">{len(tag.wert)}i", *tag.wert)
    elif typ == TAG_LONG_ARRAY:
        aus += struct.pack(">i", len(tag.wert))
        aus += struct.pack(f">{len(tag.wert)}q", *tag.wert)
    else:
        raise NbtFehler(f"Unbekannte NBT-Art {typ}.")


def schreibe(name: str, wurzel: Tag) -> bytes:
    """Wurzel-Compound zu rohem NBT. Gegenstueck zu ``lies``."""
    if wurzel.typ != TAG_COMPOUND:
        raise NbtFehler("NBT-Wurzel muss ein Compound sein.")
    aus = bytearray()
    aus += struct.pack(">b", TAG_COMPOUND)
    _schreibe_text(aus, name)
    _schreibe_nutzlast(aus, wurzel)
    return bytes(aus)


# -------------------------------------------------------------- Dateien bearbeiten

def lies_datei(pfad: Path) -> tuple[str, Tag, str]:
    """NBT-Datei lesen. Rueckgabe: (Wurzelname, Wurzel, Packart "gzip"/"zlib"/"roh")."""
    gepackt = Path(pfad).read_bytes()
    for art, entpacken in (("gzip", gzip.decompress), ("zlib", zlib.decompress)):
        try:
            name, wurzel = lies(entpacken(gepackt))
            return name, wurzel, art
        except NbtFehler:
            raise
        except Exception:  # noqa: BLE001 - falsche Packart, naechste probieren
            continue
    name, wurzel = lies(gepackt)
    return name, wurzel, "roh"


def schreibe_datei(pfad: Path, name: str, wurzel: Tag, packart: str) -> None:
    """NBT-Datei schreiben - nur, wenn sie sich danach wieder einlesen laesst.

    Die Probe ist der Sinn dieser Funktion: eine level.dat, die Minecraft nicht lesen kann,
    kostet die ganze Welt. Geschrieben wird erst, wenn das Ergebnis denselben Baum ergibt.
    """
    roh = schreibe(name, wurzel)
    probe_name, probe = lies(roh)
    if probe_name != name or probe != wurzel:
        raise NbtFehler("Round-Trip-Probe fehlgeschlagen - es wurde nichts geschrieben.")
    if packart == "gzip":
        daten = gzip.compress(roh)
    elif packart == "zlib":
        daten = zlib.compress(roh)
    else:
        daten = roh
    Path(pfad).write_bytes(daten)


def daten_wurzel(wurzel: Tag) -> Tag:
    """Der ``Data``-Zweig einer level.dat, oder die Wurzel selbst."""
    innen = wurzel.hole("Data")
    return innen if innen is not None and innen.typ == TAG_COMPOUND else wurzel
