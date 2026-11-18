#!/usr/bin/env python3
"""Die Torreihe am Bundesmarkt einmessen - offline, an den Regionsdateien einer Welt.

WARUM es dieses Skript gibt
---------------------------
Die Torquader stehen als Zahlen in ``freimarken/paper/src/main/resources/config.yml``
(``welten.<name>.tor``), das Gelaende steht in der Welt. Beide muessen zusammenpassen,
sonst liegt ein Tor im Berg oder in der Luft, und ``FreimarkenPlugin.torklage`` klagt beim
Start - oder, schlimmer, niemand merkt es, bis jemand durchgehen will.

Die Zahlen in der config.yml sind am 02.11.2026 an EINEM Altfurt eingemessen worden, mit
Wegwerfskripten in einem Scratchpad, die nie eingecheckt wurden. Die damalige Welt gibt es
nicht mehr; mit jedem neuen Startwert faengt das Messen von vorn an. Darum liegt das
Werkzeug jetzt IM Repo und laeuft OHNE Server: ein Server bauen, hochfahren, hineingehen
und ablesen kostet eine halbe Stunde, die Regionsdateien liegen sofort da.

Und darum ist es ein reines LESEwerkzeug: Es schreibt keine Datei und veraendert keine
Welt. Was zu tun ist, gibt es als Zeilen zum Abtippen aus - die Hand am Hebel bleibt die
des Betreibers, so wie ``FreimarkenPlugin`` den Erscheinungspunkt auch nur nennt und nicht
verschiebt.

WARUM es die Palette-Namen selbst entpackt
------------------------------------------
``app.services.world_bake_service`` kann Regionsdateien lesen, und dessen NBT- und
Anvil-Leser wird hier benutzt (``read_region``); einen zweiten NBT-Leser braucht niemand.
Was dort NICHT taugt, ist ``chunk_section_blocks``: Die Funktion liefert
Block-State-Nummern der 1.21.1-Registry und faellt bei einem Namen, den diese Registry
nicht kennt, stumm auf eine Ersatznummer (stone) zurueck. Beim Backen ist das richtig -
der Client bekommt irgendeinen Block statt eines Fehlers. Beim MESSEN waere es fatal:
"stone" sieht nach festem Boden aus, auch wenn dort in Wahrheit ein Busch steht. Beim
Messen zaehlt der NAME, also entpackt dieses Skript die Bitpackung der Sektion selbst
(gleiche Rechnung wie dort, nur mit der Palette statt der Registry).

ZWEI FORMEN: eine Linie messen, eine Flaeche durchsuchen
--------------------------------------------------------
Ohne ``--von-z``/``--bis-z`` misst das Skript EINE z-Linie und druckt die Tabelle Spalte
fuer Spalte - das ist die Form, mit der ein Betreiber eine EINGETRAGENE Reihe nachprueft:
Er kennt das z und will die Zahlen daneben sehen.

Sind ``--von-z`` und ``--bis-z`` verschieden, wird jede Linie im Bereich gemessen und die
beste Stelle gesucht. WARUM es das braucht: Am 18.11.2026 hat sich an einer frischen
Altfurt-Welt (Startwert 110926001) gezeigt, dass die in der config.yml eingetragene Reihe
bei z=0, x 56..88 komplett UNTER WASSER liegt. Eine Messung sagt dann nur "hier nicht";
wo es denn geht, muss man suchen. Die Einzelspalten-Tabelle wird dabei nicht gedruckt -
bei 500 Linien waere sie unlesbar -, sondern eine Zeile je Linie und am Ende eine
Bestenliste.

Aufruf: siehe ``--help``.
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

# Repo-Wurzel in den Suchpfad, BEVOR etwas aus app/ importiert wird - damit das Skript aus
# jedem Verzeichnis laeuft und nicht nur aus der Wurzel.
_WURZEL = Path(__file__).resolve().parents[1]
if str(_WURZEL) not in sys.path:
    sys.path.insert(0, str(_WURZEL))

# read_nbt wird hier nicht gebraucht: read_region wendet es selbst auf jeden Chunk an.
from app.services.world_bake_service import read_region  # noqa: E402  (nach sys.path)


# --- Blockkunde ----------------------------------------------------------------
# WARUM eigene Listen und nicht Material.isSolid(): Ohne Server gibt es kein Bukkit. Die
# Listen sind bewusst kurz und bewusst unvollstaendig - genau wie Ankunftspunkt.SCHAEDLICH
# im Plugin. Alles, was hier nicht steht, gilt als TRAGEND; das ist die haeufigere und die
# harmlosere Annahme (Gelaende besteht aus Erde und Stein). Damit ein Irrtum nicht
# unsichtbar bleibt, nennt die Tabelle immer den NAMEN des Deckblocks: Wer dort
# "dead_bush" liest, sieht den Fehler selbst. Wer eine Art nachtragen will, ohne diese
# Datei zu aendern, nutzt --nicht-tragend.

BLOCKE_LUFT = frozenset({"air", "cave_air", "void_air"})

BLOCKE_FLUESSIG = frozenset({
    "water", "lava", "flowing_water", "flowing_lava", "bubble_column",
})

# Eis ist fest und traegt - aber ueber Wasser, und es schmilzt. Ein Tor auf Eis ist in
# einer Woche ein Tor ueber einem Loch, darum gilt eine Eisspalte als nass.
BLOCKE_EIS = frozenset({"ice", "packed_ice", "blue_ice", "frosted_ice"})

# Steht herum, traegt aber niemanden (und ist in Bukkit nicht solid).
BLOCKE_NICHT_TRAGEND = frozenset({
    "allium", "azure_bluet", "bamboo", "bamboo_sapling", "beetroots", "blue_orchid",
    "brown_mushroom", "carrots", "closed_eyeblossom", "cobweb", "cocoa", "cornflower",
    "crimson_fungus", "dandelion", "dead_bush", "fern", "fire", "firefly_bush",
    "glow_lichen", "grass", "hanging_roots", "kelp", "kelp_plant", "large_fern",
    "leaf_litter", "lever", "lilac", "lily_of_the_valley", "lily_pad", "melon_stem",
    "nether_sprouts", "nether_wart", "open_eyeblossom", "oxeye_daisy", "peony",
    "pink_petals", "pitcher_crop", "pitcher_plant", "poppy", "pumpkin_stem", "rail",
    "red_mushroom", "redstone_torch", "rose_bush", "seagrass", "short_grass",
    "small_dripleaf", "snow", "soul_fire", "soul_torch", "spore_blossom", "sugar_cane",
    "sunflower", "sweet_berry_bush", "tall_grass", "tall_seagrass", "torch",
    "torchflower", "torchflower_crop", "vine", "wheat", "wildflowers", "wither_rose",
})

# Endungen statt Einzelnamen, wo die Farbe oder die Holzart vorne steht - sonst stuenden
# hier sechzehn Teppiche und vierzehn Schilder.
ENDUNGEN_NICHT_TRAGEND = (
    "_banner", "_bush", "_button", "_carpet", "_coral", "_coral_fan", "_coral_wall_fan",
    "_fan", "_flower", "_grass", "_mushroom", "_pressure_plate", "_rail", "_roots",
    "_sapling", "_sign", "_sprouts", "_torch", "_tulip", "_vines",
)

# Spiegel von Ankunftspunkt.SCHAEDLICH (Java). WARUM gespiegelt und nicht abgeleitet: Die
# Java-Liste steht in einem EnumSet und ist ohne Server nicht lesbar. Wer sie dort
# erweitert, muss sie hier nachziehen - sonst empfiehlt dieses Skript eine Stelle, die das
# Plugin beim Start wieder verwirft.
BLOCKE_SCHAEDLICH = frozenset({
    "water", "lava", "fire", "soul_fire", "magma_block", "cactus", "powder_snow",
    "sweet_berry_bush", "wither_rose", "campfire", "soul_campfire", "pointed_dripstone",
})


def kurzname(name: str) -> str:
    """``minecraft:grass_block`` -> ``grass_block``; der Namensraum sagt beim Messen nichts."""
    return str(name).split(":")[-1].strip().lower()


def ist_luft(name: str) -> bool:
    return kurzname(name) in BLOCKE_LUFT


def ist_fluessig(name: str) -> bool:
    return kurzname(name) in BLOCKE_FLUESSIG


def ist_eis(name: str) -> bool:
    return kurzname(name) in BLOCKE_EIS


def ist_schaedlich(name: str) -> bool:
    return kurzname(name) in BLOCKE_SCHAEDLICH


def ist_tragend(name: str, zusatz: frozenset[str] = frozenset()) -> bool:
    """Ob auf diesem Block ein Tor stehen kann - die Frage, die ``torklage`` stellt."""
    kurz = kurzname(name)
    if kurz in BLOCKE_LUFT or kurz in BLOCKE_FLUESSIG:
        return False
    if kurz in BLOCKE_NICHT_TRAGEND or kurz in zusatz:
        return False
    return not kurz.endswith(ENDUNGEN_NICHT_TRAGEND)


# --- Sektion entpacken ---------------------------------------------------------
def palettenname(eintrag: object) -> str:
    """Der Blockname eines Paletteneintrags - in ALLEN vier Formen, die vorkommen.

    GEMESSEN am 18.11.2026 an einer frischen Welt aus Paper 26.3 (Chunk 5/0 von
    freimarken:altfurt). Eine einzige Sektion traegt dort drei verschiedene Formen
    nebeneinander, und das ist der Grund, warum diese Funktion ueberhaupt existiert:

    * ``"minecraft:bedrock"``                              - ein nackter String
    * ``{"": "minecraft:stone"}``                           - ein Compound, dessen Name
      unter dem LEEREN Schluessel steht (ein unbenannter NBT-String)
    * ``{"id": "minecraft:kelp", "properties": {...}}``     - das Format ab 26.x:
      KLEIN geschrieben, ``id`` statt ``Name``
    * ``{"Name": "minecraft:stone", "Properties": {...}}``  - das Format bis 1.21.x

    <b>Wer nur ``Name`` liest, bekommt bei einer 26.x-Welt fuer JEDEN Block denselben
    Ersatzwert.</b> Genau daran ist der erste Lauf dieses Skriptes gescheitert, und genau
    das tut ``world_bake_service.block_state_id`` heute noch (es liest ``Name`` und faellt
    stumm auf eine Ersatz-ID zurueck) - dort steht es im TODO.
    """
    if isinstance(eintrag, str):
        return eintrag
    if isinstance(eintrag, dict):
        for schluessel in ("id", "Name", ""):
            wert = eintrag.get(schluessel)
            if isinstance(wert, str) and wert:
                return wert
    return "minecraft:air"


def sektion_namen(sektion: dict) -> list[str] | None:
    """Die 4096 Blocknamen einer Sektion in YZX-Reihenfolge, oder ``None``.

    Dieselbe Bitpackung wie ``world_bake_service.chunk_section_blocks``, nur dass hier der
    Palettenname stehen bleibt statt zu einer 1.21.1-Nummer zu werden (siehe Modulkopf).

    Eine Sektion ohne ``data`` hat genau einen Block in der Palette und besteht ganz aus
    ihm - das ist der haeufige Fall (reine Luft, reiner Stein) und spart Minecraft die
    Bitpackung.
    """
    zustaende = sektion.get("block_states")
    if not isinstance(zustaende, dict):
        # Altes Format (vor 1.13) hat hier "Blocks"; read_region siebt solche Chunks
        # schon aus, aber ein halb umgezogener Chunk soll nicht als Luft durchgehen.
        return None
    palette = zustaende.get("palette") or []
    if not palette:
        return None
    namen = [palettenname(eintrag) for eintrag in palette]
    daten = zustaende.get("data")
    if not daten:
        return [namen[0]] * 4096

    bits = max(4, (len(palette) - 1).bit_length())
    je_long = 64 // bits
    maske = (1 << bits) - 1
    aus: list[str] = []
    for long_wert in daten:
        wert = long_wert & 0xFFFFFFFFFFFFFFFF   # signiert -> unsigniert fuers Schieben
        for _ in range(je_long):
            index = wert & maske
            aus.append(namen[index] if index < len(namen) else namen[0])
            wert >>= bits
            if len(aus) >= 4096:
                break
        if len(aus) >= 4096:
            break
    if len(aus) < 4096:
        # Zu kurze Daten sind ein Schaden, kein Stein: der Rest gilt als Luft, damit die
        # Messung nicht einen Boden erfindet, den es nicht gibt.
        aus += ["minecraft:air"] * (4096 - len(aus))
    return aus[:4096]


# --- Lage einer Spalte ---------------------------------------------------------
LAGE_GEMESSEN = "gemessen"
LAGE_KEINE_DATEI = "keine_regionsdatei"
LAGE_NICHT_ERZEUGT = "nicht_erzeugt"
LAGE_UNFERTIG = "unfertig"
LAGE_ALTES_FORMAT = "altes_format"
LAGE_LEER = "leer"

# Chunk-Zustaende, in denen das Gelaende schon steht. WARUM das geprueft wird: Ein Chunk
# im Zustand "biomes" oder "noise" hat einen Eintrag in der Regionsdatei, aber keine
# Oberflaeche - gemessen gaebe er einen Boden an, der sich beim naechsten Laden verschiebt.
ZUSTAND_MESSBAR = frozenset({"full", "spawn", "light", "initialize_light"})

# Die Zustaende in der Reihenfolge, in der Minecraft sie durchlaeuft - die alten Namen
# (1.21.x) und die kurzen von 26.x in EINER Reihe. WARUM als Liste und nicht als Menge:
# --auch-unfertig heisst "ab dem Zustand, in dem der Boden steht", und was "ab" bedeutet,
# sagt nur eine Reihenfolge. Ein Name, der hier fehlt, gilt weiter als nicht messbar.
#
# GEMESSEN am 18.11.2026 an freimarken:altfurt (Paper 26.3): Dort kommen genau fuenf
# Zustaende vor - structure_starts (592 Chunks), biomes (38), terrain (30),
# initialize_light (23), full (6). "features" gibt es in dieser Version NICHT mehr;
# noise, surface und carvers sind zu "terrain" zusammengelegt. Wer hier nur nach
# "features" sucht, sucht in einer 26.x-Welt nach einem Namen, den es nicht gibt.
ZUSTAND_REIHE = (
    "empty", "structure_starts", "structure_references", "biomes", "noise", "surface",
    "carvers", "terrain", "features", "initialize_light", "light", "spawn", "full",
)

# Was --auch-unfertig zusaetzlich freigibt. Ab "terrain" steht der Boden MIT Oberflaeche,
# aber Baeume, Seegras und Strukturen koennen noch fehlen; "features" (1.21.x) ist der
# gleiche Fall, denn auch danach legen die Nachbarchunks noch Gewaechs herein.
#
# GEMESSEN (gleicher Lauf): Der oberste Block der Mittelspalte ist in terrain-Chunks
# grass_block, snow_block, stone oder water auf plausibler Hoehe - die Oberflaeche ist
# also fertig. In biomes- und structure_starts-Chunks sind alle Sektionen reine Luft;
# sie zu messen wuerde einen Boden erfinden, darum bleiben sie auch mit --auch-unfertig
# draussen (sie fallen dann als LAGE_LEER auf, nicht als Boden).
#
# Jede so gemessene Spalte wird als unsicher gekennzeichnet (Spalte.unsicher) und nicht
# stillschweigend mitgezaehlt.
ZUSTAND_UNSICHER = frozenset(ZUSTAND_REIHE[ZUSTAND_REIHE.index("terrain"):]) - ZUSTAND_MESSBAR


class Spalte:
    """Was an einer Stelle (x, z) gemessen wurde - oder warum nicht."""

    def __init__(self, x: int, z: int, lage: str, grund: str = "", unsicher: bool = False):
        self.x = x
        self.z = z
        self.lage = lage
        self.grund = grund
        # Gemessen, aber aus einem Chunk im Zustand "features": Boden verlaesslich,
        # Hindernisse nicht. Nur mit --auch-unfertig ueberhaupt moeglich.
        self.unsicher = unsicher
        self.boden: int | None = None
        self.deck: str | None = None
        self.nass = False
        self.nass_grund = ""
        self.hindernisse: list[str] = []

    @property
    def taugt(self) -> bool:
        """Ob diese Spalte fuer ein Tor in Frage kommt - gemessen, trocken, mit Boden."""
        return self.lage == LAGE_GEMESSEN and self.boden is not None and not self.nass


class Weltleser:
    """Liest Blocknamen aus einem Regionsordner - mit Gedaechtnis, ohne Server.

    WARUM mit Gedaechtnis: ``read_region`` entpackt eine ganze Datei (bis 1024 Chunks) auf
    einmal. Eine Torreihe fragt sechzig Spalten im selben Dutzend Chunks; ohne Zwischenlage
    waere das sechzig Mal dieselbe Datei.
    """

    # Wie hoch ueber dem Boden nachgesehen wird. Drei, weil ein Tor drei hoch ist; die
    # tragenden zwei (Fuss und Kopf) prueft torklage, der dritte Block ist der Sturz.
    HOEHE_UEBER_BODEN = 3

    def __init__(self, ordner: Path, zusatz_nicht_tragend: frozenset[str] = frozenset(),
                 auch_unfertig: bool = False):
        self.ordner = Path(ordner)
        self.zusatz = zusatz_nicht_tragend
        self.auch_unfertig = bool(auch_unfertig)
        # Zaehler ueber den GANZEN Lauf - er wird von vergessen() nicht geleert, sonst
        # koennte die Zusammenfassung nicht sagen, wie viel Arbeit es war.
        self.gemessen = 0
        self.unsichere = 0
        self._regionen: dict[tuple[int, int], dict] = {}
        self._sektionen: dict[tuple[int, int, int], list[str] | None] = {}
        self._chunks: dict[tuple[int, int], tuple[dict | None, str, str, bool]] = {}
        self._spalten: dict[tuple[int, int], Spalte] = {}

    def vergessen(self) -> None:
        """Die feinen Zwischenlagen leeren - Sektionen und Spalten, nicht die Regionen.

        WARUM: Eine Flaechensuche laeuft Linie fuer Linie. Jede entpackte Sektion ist eine
        Liste mit 4096 Namen; bei 500x500 Bloecken kommen tausende zusammen, und der
        Arbeitsspeicher ist voll, bevor die Suche fertig ist. Hat die Suche eine
        Chunk-Reihe verlassen, kommt sie nie wieder - dann darf das weg. Die eingelesenen
        Regionen bleiben: sie noch einmal zu entpacken waere teuer und sie liegen ohnehin
        schon im Speicher.
        """
        self._sektionen.clear()
        self._spalten.clear()

    # -- Dateien und Chunks --
    def regionsdatei(self, chunk_x: int, chunk_z: int) -> Path:
        return self.ordner / f"r.{chunk_x >> 5}.{chunk_z >> 5}.mca"

    def _im_kopf_belegt(self, datei: Path, chunk_x: int, chunk_z: int) -> bool:
        """Ob die Regionsdatei fuer diesen Chunk ueberhaupt einen Eintrag hat.

        WARUM das eigens geprueft wird: ``read_region`` gibt einen Chunk nicht zurueck,
        wenn er fehlt UND wenn es ihn gibt, er aber nicht lesbar ist (altes Format vor
        1.13, kaputte Kompression). Beides als "nicht erzeugt" zu melden waere dieselbe
        stumme Luege, die am 02.11.2026 Arbeit gekostet hat. Der 8-KiB-Kopf einer
        Regionsdatei sagt, welcher Fall vorliegt - und das ist Byte-Arithmetik, kein
        zweiter NBT-Leser.
        """
        try:
            with open(datei, "rb") as strom:
                kopf = strom.read(4096)
        except OSError:
            return False
        if len(kopf) < 4096:
            return False
        platz = ((chunk_x & 31) + (chunk_z & 31) * 32) * 4
        eintrag = kopf[platz:platz + 4]
        versatz = (eintrag[0] << 16) | (eintrag[1] << 8) | eintrag[2]
        return versatz != 0 and eintrag[3] != 0

    def chunk(self, chunk_x: int, chunk_z: int) -> tuple[dict | None, str, str, bool]:
        """(Chunk-NBT, Lage, Grund, unsicher) - Lage sagt, warum nichts da ist."""
        schluessel = (chunk_x, chunk_z)
        if schluessel in self._chunks:
            return self._chunks[schluessel]

        datei = self.regionsdatei(chunk_x, chunk_z)
        if not datei.is_file() or datei.stat().st_size < 8192:
            antwort = (None, LAGE_KEINE_DATEI, datei.name, False)
        else:
            region = (chunk_x >> 5, chunk_z >> 5)
            if region not in self._regionen:
                self._regionen[region] = read_region(datei)
            nbt = self._regionen[region].get(schluessel)
            if nbt is None:
                if self._im_kopf_belegt(datei, chunk_x, chunk_z):
                    antwort = (None, LAGE_ALTES_FORMAT, datei.name, False)
                else:
                    antwort = (None, LAGE_NICHT_ERZEUGT, datei.name, False)
            else:
                zustand = kurzname(str(nbt.get("Status", "full")))
                if zustand in ZUSTAND_MESSBAR:
                    antwort = (nbt, LAGE_GEMESSEN, "", False)
                elif self.auch_unfertig and zustand in ZUSTAND_UNSICHER:
                    # Gemessen wird er, aber der Grund bleibt stehen: die Ausgabe nennt ihn.
                    antwort = (nbt, LAGE_GEMESSEN, zustand, True)
                else:
                    antwort = (nbt, LAGE_UNFERTIG, zustand, False)
        self._chunks[schluessel] = antwort
        return antwort

    def _sektion(self, nbt: dict, chunk_x: int, chunk_z: int, sektion_y: int) -> list[str] | None:
        schluessel = (chunk_x, chunk_z, sektion_y)
        if schluessel not in self._sektionen:
            treffer = None
            for sektion in nbt.get("sections", []):
                if sektion.get("Y") is not None and int(sektion["Y"]) == sektion_y:
                    treffer = sektion_namen(sektion)
                    break
            self._sektionen[schluessel] = treffer
        return self._sektionen[schluessel]

    @staticmethod
    def _sektion_hoehen(nbt: dict) -> list[int]:
        """Die vorhandenen Sektionen von oben nach unten - mehr Welt gibt es nicht."""
        hoehen = [int(s["Y"]) for s in nbt.get("sections", []) if s.get("Y") is not None]
        return sorted(hoehen, reverse=True)

    # -- Bloecke und Spalten --
    def block(self, x: int, y: int, z: int) -> str | None:
        """Der Blockname an dieser Stelle; ``None``, wenn der Chunk nicht lesbar ist.

        Eine Hoehe ohne Sektion ist Luft: Minecraft legt fuer reine Luft keine Sektion an.
        """
        nbt, _, _, _ = self.chunk(x >> 4, z >> 4)
        if nbt is None:
            return None
        namen = self._sektion(nbt, x >> 4, z >> 4, y >> 4)
        if namen is None:
            return "minecraft:air"
        return namen[(y & 15) * 256 + (z & 15) * 16 + (x & 15)]

    def spalte(self, x: int, z: int) -> Spalte:
        """Die Spalte an (x, z) - jede Stelle wird nur EINMAL wirklich gemessen.

        WARUM mit Gedaechtnis: Eine Flaechensuche fragt dieselbe Stelle zwar nur einmal,
        aber ein zweiter Blick (Nachrechnen, ein zweites Fenster) soll nicht noch einmal
        eine Sektion entpacken. Der Zaehler ``gemessen`` zaehlt nur die echten Messungen -
        damit die Zusammenfassung nicht luegt.
        """
        schluessel = (x, z)
        treffer = self._spalten.get(schluessel)
        if treffer is None:
            treffer = self._spalte_messen(x, z)
            self._spalten[schluessel] = treffer
            self.gemessen += 1
            if treffer.unsicher:
                self.unsichere += 1
        return treffer

    def _spalte_messen(self, x: int, z: int) -> Spalte:
        """Boden, Deckblock, Nass und Hindernis an einer Stelle - in einem Durchgang."""
        nbt, lage, grund, unsicher = self.chunk(x >> 4, z >> 4)
        if nbt is None:
            return Spalte(x, z, lage, grund)
        if lage == LAGE_UNFERTIG:
            return Spalte(x, z, LAGE_UNFERTIG, grund)

        boden: int | None = None
        deck = ""
        for sektion_y in self._sektion_hoehen(nbt):
            namen = self._sektion(nbt, x >> 4, z >> 4, sektion_y)
            if namen is None:
                continue
            for lokal_y in range(15, -1, -1):
                name = namen[lokal_y * 256 + (z & 15) * 16 + (x & 15)]
                if ist_tragend(name, self.zusatz):
                    boden = sektion_y * 16 + lokal_y
                    deck = name
                    break
            if boden is not None:
                break

        if boden is None:
            # Chunk da, aber keine tragende Spalte: ueber der Leere (Freistaedte-Felder)
            # oder im Wasser bis zum Weltboden. Keine Messung, aber auch kein Datenfehler.
            return Spalte(x, z, LAGE_LEER, "kein tragender Block in der Spalte", unsicher)

        ergebnis = Spalte(x, z, LAGE_GEMESSEN, grund, unsicher)
        ergebnis.boden = boden
        ergebnis.deck = kurzname(deck)
        if ist_eis(deck):
            ergebnis.nass = True
            ergebnis.nass_grund = ergebnis.deck
        for hoch in range(1, self.HOEHE_UEBER_BODEN + 1):
            name = self.block(x, boden + hoch, z)
            if name is None:
                continue
            kurz = kurzname(name)
            if ist_luft(name):
                continue
            if ist_fluessig(name) or ist_eis(name):
                if not ergebnis.nass:
                    ergebnis.nass = True
                    ergebnis.nass_grund = kurz
                continue
            marke = "!" if ist_schaedlich(name) else ""
            ergebnis.hindernisse.append(f"+{hoch} {kurz}{marke}")
        return ergebnis


# --- Fenster fuer die Wand -----------------------------------------------------
def fensterbreite(tore: int) -> int:
    """1 + 4*n: links eine Endsaeule, dann je Tor drei Oeffnung und eine Saeule.

    Acht Tore brauchen also 33 Bloecke. Das am 02.11.2026 gemessene flache Feld war 32
    breit - genau ein Block zu wenig, und genau darum rechnet das hier ein Programm.
    """
    return 1 + 4 * tore


def toranfaenge(anfang_x: int, tore: int) -> list[int]:
    """Die x-Anfaenge der Oeffnungen; jede ist drei breit, dazwischen eine Saeule."""
    return [anfang_x + 1 + 4 * nummer for nummer in range(tore)]


def fenster_suchen(spalten: list[Spalte], tore: int,
                   x_naehe: bool = False) -> tuple[list[Spalte], int] | None:
    """Das flachste trockene Fenster - oder ``None``, wenn keines ganz messbar ist.

    Reihenfolge der Guete: erst kleine Spanne (sie entscheidet, ob ueberhaupt gebaut
    werden kann), dann wenige Hindernisse (sie sind nur Arbeit), dann kleines x (damit
    zwei gleich gute Stellen nicht bei jedem Lauf anders ausfallen).

    ``x_naehe`` aendert nur den letzten, den entscheidungsfreien Teil: Dann gewinnt bei
    gleicher Spanne und gleicher Arbeit das Fenster, dessen MITTE am naechsten an x=0
    liegt, statt dem mit dem kleinsten x. WARUM einstellbar und nicht immer so: Die
    Einzellinien-Messung soll Zeile fuer Zeile dasselbe sagen wie bisher; die
    Flaechensuche dagegen sucht eine Stelle beim Erscheinungspunkt, und dort ist das
    kleinste x der am weitesten entfernte Rand des Suchbereichs.
    """
    breite = fensterbreite(tore)
    if len(spalten) < breite:
        return None
    beste: tuple[tuple, list[Spalte]] | None = None
    for start in range(len(spalten) - breite + 1):
        teil = spalten[start:start + breite]
        if not all(s.taugt for s in teil):
            continue
        hoehen = [s.boden for s in teil]
        schluessel = ((abs((teil[0].x + teil[-1].x) // 2), teil[0].x) if x_naehe
                      else (teil[0].x,))
        guete = (max(hoehen) - min(hoehen), sum(len(s.hindernisse) for s in teil), schluessel)
        if beste is None or guete < beste[0]:
            beste = (guete, teil)
    if beste is None:
        return None
    return beste[1], beste[0][0]


# --- Flaechensuche -------------------------------------------------------------
class Stelle:
    """Eine gefundene Torstelle: das beste Fenster EINER z-Linie, mit ihren Zahlen."""

    def __init__(self, z: int, fenster: list[Spalte], spanne: int):
        self.z = z
        self.fenster = fenster
        self.spanne = spanne
        self.anfang = fenster[0].x
        self.ende = fenster[-1].x
        self.hindernisse = sum(len(s.hindernisse) for s in fenster)
        self.unsichere = sum(1 for s in fenster if s.unsicher)
        self.arten = sorted({s.deck for s in fenster if s.deck})

    @property
    def mitte(self) -> int:
        return (self.anfang + self.ende) // 2

    @property
    def guete(self) -> tuple[int, int, int, int, int]:
        """Klein ist gut - in dieser Reihenfolge.

        Spanne zuerst (sie entscheidet, ob ueberhaupt gebaut werden kann), dann die
        Hindernisse (nur Arbeit), dann |z| und |x| der Mitte: Der Bundesmarkt IST der
        Hauptspawn, eine Reihe weit draussen hilft niemandem. Das letzte Glied (z) macht
        die Reihenfolge eindeutig, damit zwei Laeufe dieselbe Liste geben.
        """
        return (self.spanne, self.hindernisse, abs(self.z), abs(self.mitte), self.z)


def kein_fenster_grund(spalten: list[Spalte], tore: int) -> str:
    """Warum auf dieser Linie kein Fenster liegt - in Zahlen, nicht als Vermutung."""
    breite = fensterbreite(tore)
    taugliche = sum(1 for s in spalten if s.taugt)
    teile = [f"{taugliche} von {breite} noetigen Spalten taugen"]
    nasse = sum(1 for s in spalten if s.lage == LAGE_GEMESSEN and s.nass)
    if nasse:
        teile.append(f"{nasse} nass")
    # WARUM eigene kurze Worte und nicht die Lage-Namen: "unfertig" steht in der uebrigen
    # Ausgabe fuer eine GEMESSENE, aber unsichere Spalte. Hier geht es um Spalten, die
    # ueberhaupt nicht gemessen sind - dasselbe Wort fuer zwei Dinge waere eine Falle.
    kurz = {
        LAGE_KEINE_DATEI: "ohne Regionsdatei",
        LAGE_NICHT_ERZEUGT: "Chunk nicht erzeugt",
        LAGE_ALTES_FORMAT: "Chunk nicht lesbar",
        LAGE_UNFERTIG: "Chunk zu frueh erzeugt",
        LAGE_LEER: "ohne tragenden Block",
    }
    andere: dict[str, int] = {}
    for spalte in spalten:
        if spalte.lage != LAGE_GEMESSEN:
            andere[spalte.lage] = andere.get(spalte.lage, 0) + 1
    teile += [f"{anzahl} {kurz.get(lage, lage)}" for lage, anzahl in sorted(andere.items())]
    return "kein Fenster (" + ", ".join(teile) + ")"


def flaeche_suchen(leser: Weltleser, von_x: int, bis_x: int, von_z: int, bis_z: int,
                   tore: int) -> list[Stelle]:
    """Jede z-Linie des Bereichs messen und die Stellen nach Guete sortiert geben.

    Gedruckt wird WAEHREND der Suche eine Zeile je Linie - bei 500 Linien will niemand
    minutenlang auf eine stumme Konsole sehen, und die Einzelspalten-Tabelle waere hier
    vollkommen unlesbar.
    """
    stellen: list[Stelle] = []
    for z in range(von_z, bis_z + 1):
        spalten = [leser.spalte(x, z) for x in range(von_x, bis_x + 1)]
        gefunden = fenster_suchen(spalten, tore, x_naehe=True)
        if gefunden is None:
            print(f"  z={z:>6} | {kein_fenster_grund(spalten, tore)}")
        else:
            stelle = Stelle(z, gefunden[0], gefunden[1])
            stellen.append(stelle)
            zusatz = f", {stelle.unsichere} unfertig" if stelle.unsichere else ""
            print(f"  z={z:>6} | bestes Fenster x {stelle.anfang}..{stelle.ende},"
                  f" Spanne {stelle.spanne}, Hindernisse {stelle.hindernisse}{zusatz}")
        # Chunk-Reihe verlassen: Diese Sektionen braucht die Suche nie wieder (siehe
        # Weltleser.vergessen). Ohne das waechst der Speicher mit der Flaeche.
        if (z + 1) >> 4 != z >> 4:
            leser.vergessen()
    return sorted(stellen, key=lambda stelle: stelle.guete)


def bestenliste_zeigen(stellen: list[Stelle], grenze: int = 10) -> None:
    print()
    print(f"BESTENLISTE - die {min(grenze, len(stellen))} besten von {len(stellen)}"
          f" Linien mit Fenster:")
    print(f"{'Platz':>5} | {'z':>6} | {'x von..bis':>13} | {'Spanne':>6} | {'Hind.':>5}"
          f" | {'unfertig':>8} | Deckblockarten")
    print(f"{'-' * 5}-+-{'-' * 6}-+-{'-' * 13}-+-{'-' * 6}-+-{'-' * 5}-+-{'-' * 8}-+-{'-' * 30}")
    for platz, stelle in enumerate(stellen[:grenze], start=1):
        bereich = f"{stelle.anfang}..{stelle.ende}"
        print(f"{platz:>5} | {stelle.z:>6} | {bereich:>13} | {stelle.spanne:>6}"
              f" | {stelle.hindernisse:>5} | {stelle.unsichere:>8} |"
              f" {', '.join(stelle.arten)}")


# --- Ausgabe -------------------------------------------------------------------
def dimension_aus_pfad(regionen: Path) -> str:
    """``.../dimensions/freimarken/altfurt/region`` -> ``freimarken:altfurt``.

    WARUM geraten und nicht gefragt: Ohne Dimension sind die Baubefehle falsch (die
    Konsole steht in der Overworld), und der Pfad weiss es ohnehin. Wer eine andere
    braucht, setzt --dimension.
    """
    teile = [t.lower() for t in Path(regionen).resolve().parts]
    if "dimensions" in teile:
        stelle = len(teile) - 1 - teile[::-1].index("dimensions")
        if stelle + 2 < len(teile):
            return f"{teile[stelle + 1]}:{teile[stelle + 2]}"
    return ""


def tabelle_zeigen(spalten: list[Spalte]) -> None:
    print(f"{'x':>6} | {'Boden':>5} | {'Deckblock':<22} | {'nass':<12} | Hindernis (3 darueber)")
    print(f"{'-' * 6}-+-{'-' * 5}-+-{'-' * 22}-+-{'-' * 12}-+-{'-' * 26}")
    klartext = {
        LAGE_KEINE_DATEI: "KEIN CHUNK - keine Regionsdatei",
        LAGE_NICHT_ERZEUGT: "KEIN CHUNK - in der Regionsdatei nicht erzeugt",
        LAGE_ALTES_FORMAT: "NICHT LESBAR - Chunk im alten Format (vor 1.13)",
        LAGE_UNFERTIG: "KEIN CHUNK - erst halb erzeugt, Status",
        LAGE_LEER: "KEIN BODEN",
    }
    for spalte in spalten:
        if spalte.lage != LAGE_GEMESSEN:
            hinweis = klartext.get(spalte.lage, spalte.lage)
            zusatz = f" ({spalte.grund})" if spalte.grund else ""
            print(f"{spalte.x:>6} | {'?':>5} | {hinweis}{zusatz}")
            continue
        nass = spalte.nass_grund if spalte.nass else "-"
        hindernis = ", ".join(spalte.hindernisse) if spalte.hindernisse else "-"
        # "(unfertig)" steht beim Deckblock, weil genau der dann noch wackelt: Der Boden
        # ist da, aber Baeume und Seegras darueber fehlen vielleicht noch.
        deck = f"{spalte.deck} (unfertig)" if spalte.unsicher else str(spalte.deck)
        print(f"{spalte.x:>6} | {spalte.boden:>5} | {deck:<22} | {nass:<12} | {hindernis}")


def befehl(dimension: str, rest: str) -> str:
    """Einen Konsolenbefehl in die richtige Dimension stellen.

    Die Serverkonsole steht in der Overworld; ein nacktes ``fill`` baut die Wand dort und
    nicht in Altfurt. Dieser Fehler sieht in der Konsole wie ein Erfolg aus.
    """
    return f"execute in {dimension} run {rest}" if dimension else rest


def ergebnis_zeigen(fenster: list[Spalte], spanne: int, tore: int, z: int,
                    dimension: str, wandblock: str, bodenblock: str) -> None:
    anfang = fenster[0].x
    ende = fenster[-1].x
    hoehen = [s.boden for s in fenster]
    # WARUM der HOECHSTE Boden +1: torklage verlangt fuer JEDE Saeule der Oeffnung einen
    # festen Block unter vonY. Nimmt man den tiefsten, steht das Tor an der hoechsten
    # Saeule im Boden; nimmt man den hoechsten, traegt es ueberall (sofern das Feld flach
    # ist - siehe die Klage bei Spanne > 0).
    y = max(hoehen) + 1
    arten = sorted({s.deck for s in fenster if s.deck})

    print()
    print(f"FENSTER fuer {tore} Tore ({fensterbreite(tore)} Bloecke breit)")
    print(f"  Anfang x = {anfang}, Ende x = {ende}, z = {z}")
    print(f"  Boden {min(hoehen)} bis {max(hoehen)}, Spanne {spanne}")
    print(f"  Deckblockarten: {', '.join(arten)}")
    unsichere = [s.x for s in fenster if s.unsicher]
    if unsichere:
        print(f"  UNSICHER: {len(unsichere)} der {len(fenster)} Spalten stehen in Chunks, die"
              " erst halb")
        print("  erzeugt sind (x " + ", ".join(str(x) for x in unsichere) + ").")
        print("  Der Boden gilt, die Hindernisse nicht: Baeume, Seegras und Strukturen koennen")
        print("  dort noch dazukommen. Diese Stelle erst laden lassen und OHNE --auch-unfertig")
        print("  nachmessen, bevor gebaut wird.")
    hindernisse = [f"x={s.x} {h}" for s in fenster for h in s.hindernisse]
    if hindernisse:
        print(f"  Hindernisse im Fenster ({len(hindernisse)}): {'; '.join(hindernisse)}")
        print("  (die Oeffnungs-fills raeumen sie mit weg; ein '!' ist schaedlich im Sinne"
              " von Ankunftspunkt.SCHAEDLICH)")
    if spanne > 0:
        print()
        print(f"  ACHTUNG: Das Feld ist nicht flach (Spanne {spanne}). Mit vonY = {y} steht")
        print("  unter den tieferen Saeulen Luft, und torklage klagt ueber genau die. Vor dem")
        print("  Bau den Boden angleichen (Befehl unten) oder einen anderen Bereich messen.")

    print()
    print("CONFIG.YML - je Tor unter den Eintrag seiner ZIELwelt (welten.<name>.tor):")
    for nummer, tor_x in enumerate(toranfaenge(anfang, tore), start=1):
        print(f"    # Tor {nummer}")
        print("    tor:")
        print(f"      von: [{tor_x}, {y}, {z}]")
        print(f"      bis: [{tor_x + 2}, {y + 2}, {z}]")

    print()
    print("BAUBEFEHLE fuer die Konsole (in dieser Reihenfolge):")
    print(f"  {befehl(dimension, f'forceload add {anfang} {z} {ende} {z}')}")
    if spanne > 0:
        print(f"  {befehl(dimension, f'fill {anfang} {y - 1} {z} {ende} {y - 1} {z} {bodenblock}')}"
              "   # nur bei Spanne > 0: Boden angleichen")
    print(f"  {befehl(dimension, f'fill {anfang} {y} {z} {ende} {y + 2} {z} {wandblock}')}")
    for nummer, tor_x in enumerate(toranfaenge(anfang, tore), start=1):
        print(f"  {befehl(dimension, f'fill {tor_x} {y} {z} {tor_x + 2} {y + 2} {z} air')}"
              f"   # Tor {nummer}")
    print(f"  {befehl(dimension, 'forceload remove all')}")


# --- Aufruf --------------------------------------------------------------------
def argumente(wortliste: list[str] | None = None) -> argparse.Namespace:
    leser = argparse.ArgumentParser(
        description="Die Torreihe am Bundesmarkt an den Regionsdateien einer Welt"
                    " einmessen - ohne Server. Schreibt nichts; gibt die config.yml-Zeilen"
                    " und die Baubefehle zum Abtippen aus.",
        epilog="Beispiel: torreihe_messen.py --regionen"
               " .../leerwelt/dimensions/freimarken/altfurt/region --von-x 40 --bis-x 120",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    leser.add_argument("--regionen", required=True, metavar="PFAD",
                       help="Regionsordner EINER Welt, also der Ordner mit den"
                            " r.<x>.<z>.mca-Dateien")
    leser.add_argument("--von-x", required=True, type=int, metavar="X",
                       help="erste x-Spalte, die gemessen wird (eingeschlossen)")
    leser.add_argument("--bis-x", required=True, type=int, metavar="X",
                       help="letzte x-Spalte, die gemessen wird (eingeschlossen)")
    leser.add_argument("--z", type=int, default=0, metavar="Z",
                       help="die Reihe, auf der die Wand steht")
    leser.add_argument("--von-z", type=int, default=None, metavar="Z",
                       help="erste z-Linie einer FLAECHENSUCHE (eingeschlossen); ohne"
                            " Angabe gilt --z, dann wird wie bisher nur diese eine Linie"
                            " gemessen")
    leser.add_argument("--bis-z", type=int, default=None, metavar="Z",
                       help="letzte z-Linie der Flaechensuche (eingeschlossen); ohne"
                            " Angabe gilt --z. Ist sie von --von-z verschieden, wird jede"
                            " Linie gemessen und eine Bestenliste gedruckt")
    leser.add_argument("--auch-unfertig", action="store_true",
                       help="auch halb erzeugte Chunks messen, naemlich ab dem Zustand"
                            " 'terrain' (26.x) bzw. 'features' (1.21.x): Dort steht der"
                            " Boden mit Oberflaeche, aber Baeume, Seegras und Strukturen"
                            " koennen fehlen. Solche Spalten werden als unfertig"
                            " gekennzeichnet und muessen nachgemessen werden")
    leser.add_argument("--tore", type=int, default=8, metavar="N",
                       help="wie viele Tore in die Wand sollen; die Wand ist dann"
                            " 1+4*N Bloecke breit")
    leser.add_argument("--dimension", default=None, metavar="NS:NAME",
                       help="Dimension fuer die Baubefehle; Vorgabe: aus dem Regionspfad"
                            " geraten, leer lassen mit --dimension ''")
    leser.add_argument("--wandblock", default="stone_bricks", metavar="BLOCK",
                       help="woraus die Wand gebaut wird")
    leser.add_argument("--bodenblock", default="stone", metavar="BLOCK",
                       help="womit der Boden angeglichen wird, falls das Feld nicht flach ist")
    leser.add_argument("--nicht-tragend", default="", metavar="A,B,C",
                       help="weitere Blocknamen, die NICHT als Boden gelten sollen"
                            " (Komma getrennt, ohne minecraft:)")
    return leser.parse_args(wortliste)


def kopf_zeigen(regionen: Path, wahl: argparse.Namespace, von_z: int, bis_z: int,
                dimension: str) -> None:
    anzahl_mca = len(list(regionen.glob("*.mca")))
    print(f"Regionsordner: {regionen}  ({anzahl_mca} .mca-Dateien)")
    spalten_x = wahl.bis_x - wahl.von_x + 1
    if von_z == bis_z:
        print(f"Bereich: x {wahl.von_x}..{wahl.bis_x} bei z = {von_z}"
              f"  ({spalten_x} Spalten)")
    else:
        linien = bis_z - von_z + 1
        print(f"Bereich: x {wahl.von_x}..{wahl.bis_x}, z {von_z}..{bis_z}"
              f"  ({linien} Linien zu {spalten_x} Spalten = {linien * spalten_x} Stellen)")
    if wahl.auch_unfertig:
        print("--auch-unfertig: Chunks ab dem Zustand 'terrain' (26.x) bzw. 'features'"
              " (1.21.x) werden mitgemessen und als unfertig gekennzeichnet.")
    print(f"Dimension fuer die Befehle: {dimension or '(keine - Befehle ohne execute in)'}")
    print()


def linie_messen(leser: Weltleser, wahl: argparse.Namespace, z: int, dimension: str) -> int:
    """Der alte Weg: EINE z-Linie mit Tabelle - die Form zum Nachpruefen einer
    eingetragenen Reihe. Hier aendert sich am Verhalten nichts."""
    spalten = [leser.spalte(x, z) for x in range(wahl.von_x, wahl.bis_x + 1)]
    tabelle_zeigen(spalten)

    fehlend = [s for s in spalten if s.lage != LAGE_GEMESSEN]
    if fehlend:
        print()
        print(f"{len(fehlend)} von {len(spalten)} Spalten sind NICHT gemessen (siehe Tabelle).")
        print("Ein nicht erzeugter Chunk ist keine leere Welt: Erst die Welt an dieser"
              " Stelle laden (forceload add / hingehen), dann neu messen.")
        if not wahl.auch_unfertig and any(s.lage == LAGE_UNFERTIG for s in fehlend):
            print("Halb erzeugte Chunks messen --auch-unfertig trotzdem - dann steht"
                  " '(unfertig)' dabei.")
    nasse = [s for s in spalten if s.lage == LAGE_GEMESSEN and s.nass]
    if nasse:
        print(f"{len(nasse)} Spalten sind nass oder eisig und fallen fuer ein Tor weg: "
              + ", ".join(f"x={s.x} ({s.nass_grund})" for s in nasse))
    if leser.unsichere:
        print(f"{leser.unsichere} der {leser.gemessen} gemessenen Spalten stehen in unfertigen"
              " Chunks und sind als (unfertig) gekennzeichnet - dort fehlen moeglicherweise"
              " noch Hindernisse.")

    breite = fensterbreite(wahl.tore)
    if len(spalten) < breite:
        print()
        print(f"FEHLER: {wahl.tore} Tore brauchen {breite} Bloecke, gemessen wurden nur"
              f" {len(spalten)} Spalten. Bereich erweitern.", file=sys.stderr)
        return 1

    gefunden = fenster_suchen(spalten, wahl.tore)
    if gefunden is None:
        print()
        print(f"FEHLER: In x {wahl.von_x}..{wahl.bis_x} liegt kein durchgehend gemessenes,"
              f" trockenes Fenster von {breite} Bloecken. Einen anderen oder groesseren"
              f" Bereich messen.", file=sys.stderr)
        return 1

    fenster, spanne = gefunden
    ergebnis_zeigen(fenster, spanne, wahl.tore, z, dimension,
                    wahl.wandblock, wahl.bodenblock)
    return 0


def flaeche_messen(leser: Weltleser, wahl: argparse.Namespace, von_z: int, bis_z: int,
                   dimension: str) -> int:
    """Der neue Weg: jede z-Linie messen und die besten Stellen nennen."""
    breite = fensterbreite(wahl.tore)
    if wahl.bis_x - wahl.von_x + 1 < breite:
        print(f"FEHLER: {wahl.tore} Tore brauchen {breite} Bloecke, der x-Bereich ist nur"
              f" {wahl.bis_x - wahl.von_x + 1} breit. Bereich erweitern.", file=sys.stderr)
        return 1

    print(f"FLAECHENSUCHE - je z-Linie das flachste trockene Fenster fuer {wahl.tore} Tore"
          f" ({breite} Bloecke breit):")
    beginn = time.monotonic()
    stellen = flaeche_suchen(leser, wahl.von_x, wahl.bis_x, von_z, bis_z, wahl.tore)
    dauer = time.monotonic() - beginn

    print()
    print(f"Gemessen: {leser.gemessen} Spalten in {dauer:.1f} s"
          f" ({bis_z - von_z + 1} Linien, {len(stellen)} davon mit Fenster).")
    if leser.unsichere:
        print(f"Davon beruhen {leser.unsichere} Spalten auf unfertigen Chunks (Zustand"
              " 'terrain' bzw. 'features'): Der Boden steht, Baeume, Seegras und"
              " Strukturen koennen noch dazukommen.")
        print("Jede Stelle mit einer Zahl in der Spalte 'unfertig' muss nachgemessen werden,"
              " bevor dort gebaut wird.")

    if not stellen:
        print()
        print(f"FEHLER: In x {wahl.von_x}..{wahl.bis_x}, z {von_z}..{bis_z} liegt auf keiner"
              f" Linie ein durchgehend gemessenes, trockenes Fenster von {breite} Bloecken."
              f" Einen anderen oder groesseren Bereich messen (und die Welt dort erzeugen"
              f" lassen).", file=sys.stderr)
        return 1

    bestenliste_zeigen(stellen)
    beste = stellen[0]
    print()
    print(f"BESTE STELLE: z = {beste.z}, x {beste.anfang}..{beste.ende}"
          f" - dafuer die Zeilen und Befehle:")
    ergebnis_zeigen(beste.fenster, beste.spanne, wahl.tore, beste.z, dimension,
                    wahl.wandblock, wahl.bodenblock)
    return 0


def main(wortliste: list[str] | None = None) -> int:
    wahl = argumente(wortliste)
    regionen = Path(wahl.regionen)
    if not regionen.is_dir():
        print(f"FEHLER: {regionen} ist kein Ordner.", file=sys.stderr)
        return 2
    if wahl.bis_x < wahl.von_x:
        print("FEHLER: --bis-x liegt vor --von-x.", file=sys.stderr)
        return 2
    if wahl.tore < 1:
        print("FEHLER: --tore muss mindestens 1 sein.", file=sys.stderr)
        return 2
    if wahl.von_z is None and wahl.bis_z is None:
        von_z = bis_z = wahl.z
    else:
        # Nur EINE Grenze genannt: Dann ist sie beide. Wer "--von-z 50" tippt, meint die
        # Linie 50 - eine Klage ueber das fehlende --bis-z waere nur im Weg.
        von_z = wahl.von_z if wahl.von_z is not None else wahl.bis_z
        bis_z = wahl.bis_z if wahl.bis_z is not None else wahl.von_z
    if bis_z < von_z:
        print("FEHLER: --bis-z liegt vor --von-z.", file=sys.stderr)
        return 2

    dimension = wahl.dimension if wahl.dimension is not None else dimension_aus_pfad(regionen)
    zusatz = frozenset(kurzname(t) for t in wahl.nicht_tragend.split(",") if t.strip())
    leser = Weltleser(regionen, zusatz, wahl.auch_unfertig)

    kopf_zeigen(regionen, wahl, von_z, bis_z, dimension)
    if von_z == bis_z:
        return linie_messen(leser, wahl, von_z, dimension)
    return flaeche_messen(leser, wahl, von_z, bis_z, dimension)


if __name__ == "__main__":
    raise SystemExit(main())
