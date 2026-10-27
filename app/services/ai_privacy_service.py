"""Logs und Configs vor dem Versand an einen externen Dienst saeubern.

Entscheidung des Nutzers: **IP-Adressen werden immer entfernt, Spielernamen bleiben im
Klartext.** Begruendung dafuer ist die Diagnose - bei einem Crash, der an einem bestimmten
Spieler haengt, ist der Name die halbe Information.

Zwei Dinge gibt es trotzdem:

* Einen Schalter fuer Pseudonymisierung (``namen_ersetzen``). Dann wird jeder Name durch
  "Spieler1", "Spieler2" ersetzt - derselbe Name immer durch denselben Platzhalter, damit
  Zusammenhaenge erkennbar bleiben, und die Zuordnung kommt zurueck, damit die Antwort
  wieder lesbar gemacht werden kann. Gedacht fuer ein Log mit vielen Mitspielern.
* Eine Vorschau (``vorschau``), die zeigt, was tatsaechlich hinausgeht, bevor es hinausgeht.

Immer entfernt werden ausserdem Dinge, die niemals in einen externen Dienst gehoeren und
die in Serverlogs und Configs regelmaessig auftauchen: Zugangsschluessel, Passwortzeilen,
das Velocity-Forwarding-Geheimnis und RCON-Passwoerter.
"""

from __future__ import annotations

import re

# IP-Erkennung. Zwei Fallen, die ein grobes Muster beide tappt:
#
# 1. Ein Log-Zeitstempel "12:00:01" sieht wie eine IPv6 aus - Doppelpunkte, und Ziffern
#    sind Hex-Zeichen. Ein grobes IPv6-Muster loescht damit die Uhrzeit aus JEDER Zeile,
#    und bei einer Crash-Analyse ist die Uhrzeit die halbe Information.
# 2. Eine Mod-Version wie "jei-1.21.1-19.21.0.247" enthaelt "19.21.0.247" - und das IST
#    eine gueltige IPv4. Wer sie entfernt, nimmt der Analyse die Antwort auf "welche Mod?".
#
# Deshalb: Kandidaten per Muster finden, dann pruefen - sowohl ob es wirklich eine Adresse
# ist (ipaddress) als auch ob sie im Text allein steht und nicht Teil eines Namens.
_IPV4_KANDIDAT = re.compile(r"(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?")
# Breit gefasst (auch "::" mitten drin); ob es eine Adresse ist, entscheidet ipaddress.
_IPV6_KANDIDAT = re.compile(r"(?:[0-9a-fA-F]{0,4}:){2,}[0-9a-fA-F]{0,4}")

# Woran eine IPv4 OHNE Port als Adresse erkennbar ist. Ohne diese Einschraenkung wird jede
# vierstellige Versionsnummer zur Adresse: "Loader-Version 47.2.0.1" ist eine gueltige
# IPv4, und wer sie entfernt, nimmt der Crash-Analyse die Antwort auf "welche Version?".
# Minecraft schreibt echte Adressen praktisch immer mit Port und nach einem Schraegstrich
# ("Spieler[/203.0.113.7:51234]"), deshalb traegt der Kontext hier mehr als die Zahl.
_IP_KONTEXT = re.compile(
    r"(?i)\b(?:ip|address|adresse|from|von|connection|verbindung|logged in|host|"
    r"remote|client|server-ip|bind)\b")

# Zeichen, die direkt an einer Zahlenfolge stehen und zeigen, dass sie zu einem Namen
# gehoert (Mod-Dateien, Versionen): "jei-1.21.1-19.21.0.247", "forge_1.2.3.4".
_NAMENSZEICHEN = set("-_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")

# Spielernamen aus den ueblichen Logzeilen. Bewusst an die Form der Zeile gebunden statt
# "jedes Wort, das ein Name sein koennte" - sonst wuerde die Haelfte des Logs ersetzt.
_NAME_MUSTER = [
    re.compile(r"]:\s*(?P<name>[A-Za-z0-9_]{3,16})\s+(?:joined|left) the game"),
    re.compile(r"]:\s*(?P<name>[A-Za-z0-9_]{3,16})\[/"),
    re.compile(r"]:\s*<(?P<name>[A-Za-z0-9_]{3,16})>"),
    re.compile(r"]:\s*(?P<name>[A-Za-z0-9_]{3,16}) lost connection"),
    re.compile(r"UUID of player (?P<name>[A-Za-z0-9_]{3,16})"),
]

# Zeilen, die unabhaengig von allem anderen nie hinausgehen. Der Wert wird ersetzt, der
# Schluesselname bleibt - sonst versteht die KI die Config nicht mehr.
_GEHEIM_ZEILEN = [
    (re.compile(r"(?i)^(\s*rcon\.password\s*=).*$", re.M), r"\1<entfernt>"),
    (re.compile(r"(?i)^(\s*[a-z0-9_.-]*secret\s*[:=]).*$", re.M), r"\1 <entfernt>"),
    (re.compile(r"(?i)^(\s*[a-z0-9_.-]*password\s*[:=]).*$", re.M), r"\1 <entfernt>"),
    (re.compile(r"(?i)^(\s*[a-z0-9_.-]*api[_-]?key\s*[:=]).*$", re.M), r"\1 <entfernt>"),
    (re.compile(r"(?i)^(\s*[a-z0-9_.-]*token\s*[:=]).*$", re.M), r"\1 <entfernt>"),
    # Freistehende Schluessel im Text (etwa in einem Stacktrace).
    (re.compile(r"\bsk-ant-[A-Za-z0-9_-]{10,}"), "<entfernter Schluessel>"),
    (re.compile(r"\b\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}\b"), "<entfernter Hash>"),
]


def saeubere(text: str, *, namen_ersetzen: bool = False) -> tuple[str, dict]:
    """Text versandfertig machen.

    Rueckgabe: (gesaeuberter Text, Bericht). Der Bericht nennt, was entfernt wurde, und
    enthaelt bei ``namen_ersetzen`` die Zuordnung Platzhalter -> echter Name, damit die
    Antwort der KI hinterher wieder lesbar gemacht werden kann.
    """
    roh = text or ""
    bericht: dict = {"ips": 0, "geheimnisse": 0, "namen": {}}

    for muster, ersatz in _GEHEIM_ZEILEN:
        roh, anzahl = muster.subn(ersatz, roh)
        bericht["geheimnisse"] += anzahl

    # IPv6 vor IPv4, sonst zerlegt das IPv4-Muster eingebettete Adressen.
    for muster, pruefer, mit_kontext in ((_IPV6_KANDIDAT, _ist_ipv6, False),
                                         (_IPV4_KANDIDAT, _ist_ipv4, True)):
        def _ersetze(treffer: "re.Match", pruefer=pruefer,
                     mit_kontext=mit_kontext) -> str:
            gefunden = treffer.group(0)
            passt = pruefer(gefunden, treffer) if mit_kontext else pruefer(gefunden)
            if not passt or _teil_eines_namens(treffer):
                return gefunden
            bericht["ips"] += 1
            return "<IP entfernt>"

        roh = muster.sub(_ersetze, roh)

    if namen_ersetzen:
        roh, zuordnung = _ersetze_namen(roh)
        bericht["namen"] = zuordnung

    return roh, bericht


def _teil_eines_namens(treffer: "re.Match") -> bool:
    """Steht die Zahlenfolge mitten in einem Namen?

    "jei-1.21.1-19.21.0.247" ist ein Mod-Dateiname, keine Adresse - erkennbar daran, dass
    direkt davor oder dahinter ein Buchstabe, Bindestrich oder Unterstrich steht. Eine
    echte Adresse im Log steht dagegen nach einem Schraegstrich, in Klammern, nach einem
    Leerzeichen oder am Zeilenanfang.
    """
    text = treffer.string
    vorher = text[treffer.start() - 1] if treffer.start() > 0 else ""
    nachher = text[treffer.end()] if treffer.end() < len(text) else ""
    return vorher in _NAMENSZEICHEN or nachher in _NAMENSZEICHEN


def _ist_ipv4(wert: str, treffer: "re.Match | None" = None) -> bool:
    """Echte IPv4-ADRESSE? Nicht jede gueltige IPv4 ist auch eine Adresse im Text.

    Mit Port ist es immer eine. Ohne Port zaehlt der Kontext: direkt nach einem
    Schraegstrich, oder die Zeile spricht von einer Adresse/Verbindung. Sonst ist es eine
    Versionsnummer und bleibt stehen - die Diagnose braucht sie.
    """
    import ipaddress

    ohne_port = wert.split(":")[0]
    try:
        ipaddress.IPv4Address(ohne_port)
    except ValueError:
        return False
    if ":" in wert:
        return True          # mit Port -> Adresse
    if treffer is None:
        return True
    text = treffer.string
    if treffer.start() > 0 and text[treffer.start() - 1] == "/":
        return True          # "[/1.2.3.4]" -> Adresse
    anfang = text.rfind("\n", 0, treffer.start()) + 1
    ende = text.find("\n", treffer.end())
    zeile = text[anfang:ende if ende != -1 else len(text)]
    return bool(_IP_KONTEXT.search(zeile))


def _ist_ipv6(wert: str) -> bool:
    """Echte IPv6? Ein Zeitstempel "12:00:01" ist keine - ipaddress weist ihn ab."""
    import ipaddress

    try:
        ipaddress.IPv6Address(wert.strip())
    except ValueError:
        return False
    return True


def _ersetze_namen(text: str) -> tuple[str, dict]:
    """Spielernamen durch stabile Platzhalter ersetzen.

    Derselbe Name bekommt immer denselben Platzhalter - sonst kann die KI nicht erkennen,
    dass es bei allen Abstuerzen um denselben Spieler geht, und genau das ist oft die
    Diagnose.
    """
    gefunden: list[str] = []
    for muster in _NAME_MUSTER:
        for treffer in muster.finditer(text):
            name = treffer.group("name")
            if name and name not in gefunden:
                gefunden.append(name)

    zuordnung: dict[str, str] = {}
    for i, name in enumerate(gefunden, start=1):
        zuordnung[f"Spieler{i}"] = name

    # Laengste Namen zuerst, damit "Thor" nicht in "Thormak2002" ersetzt wird.
    for platzhalter, name in sorted(zuordnung.items(), key=lambda p: -len(p[1])):
        text = re.sub(r"(?<![A-Za-z0-9_])" + re.escape(name) + r"(?![A-Za-z0-9_])",
                      platzhalter, text)
    return text, zuordnung


def uebersetze_zurueck(antwort: str, zuordnung: dict) -> str:
    """Platzhalter in der KI-Antwort wieder durch die echten Namen ersetzen."""
    for platzhalter, name in (zuordnung or {}).items():
        antwort = antwort.replace(platzhalter, name)
    return antwort


def vorschau(text: str, *, namen_ersetzen: bool = False,
             zeilen: int = 40) -> dict:
    """Was geht hinaus? Fuer die Anzeige vor dem Senden.

    Zeigt den gesaeuberten Anfang, die Groesse und was entfernt wurde. Der Nutzer soll das
    sehen koennen, BEVOR Daten das Haus verlassen - auch wenn er sich entschieden hat,
    Namen im Klartext zu senden.
    """
    gesaeubert, bericht = saeubere(text, namen_ersetzen=namen_ersetzen)
    alle = gesaeubert.splitlines()
    return {
        "text": gesaeubert,
        "auszug": "\n".join(alle[:max(1, zeilen)]),
        "zeilen_gesamt": len(alle),
        "zeichen": len(gesaeubert),
        "entfernte_ips": bericht["ips"],
        "entfernte_geheimnisse": bericht["geheimnisse"],
        "namen": bericht["namen"],
        "namen_ersetzt": bool(namen_ersetzen),
    }


def kuerze_fuer_anfrage(text: str, *, max_zeichen: int = 60_000) -> tuple[str, bool]:
    """Sehr lange Logs beschneiden - Anfang UND Ende behalten.

    Der Anfang enthaelt den Start des Servers (Versionen, geladene Mods), das Ende den
    eigentlichen Absturz. Die Mitte ist bei einem Crash fast immer das Uninteressante, und
    Token kosten Geld.
    """
    roh = text or ""
    if len(roh) <= max_zeichen:
        return roh, False
    haelfte = max_zeichen // 2
    return (roh[:haelfte] + "\n\n[... Mitte gekuerzt, um Token zu sparen ...]\n\n"
            + roh[-haelfte:]), True
