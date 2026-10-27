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

# Woran erkennbar ist, dass eine vierteilige Zahlenfolge eine VERSION ist und keine
# Adresse. Nur dann bleibt sie stehen - im Zweifel wird entfernt, denn die Zusage lautet,
# dass IP-Adressen das Haus nicht verlassen.
_VERSION_KONTEXT = re.compile(
    r"(?i)\b(?:version|versionen|ver|build|revision|loader|fabric|forge|neoforge|"
    r"mod|mods|plugin|plugins|datapack|jar|api|protokoll|protocol|schema|"
    r"laeuft auf|running)\b")

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
# Woerter, die einen Wert als Geheimnis kennzeichnen. Getrennt in zwei Gruppen, weil
# "token" auch in harmlosen Zaehlern steckt ("max_tokens: 2048") und die uebrigen nicht.
_GEHEIM_STARK = (r"(?:secret|passwor[dt]|passwd|api[_-]?key|apikey|access[_-]?key|"
                 r"private[_-]?key|credential|auth|bearer)")
_GEHEIM_ZAEHLBAR = r"(?:token)"
_GEHEIM_WORT = r"(?:" + _GEHEIM_STARK + r"|" + _GEHEIM_ZAEHLBAR + r")"

# Der Wert eines Geheimnisses in einer Logzeile: bis zum naechsten Trennzeichen.
_WERT = r"[^\s,;\"'\)\]}]{3,}"

# Zwei Orte, zwei Muster:
#
# 1. In einer CONFIGDATEI beginnt der Schluessel die Zeile, und der Wert laeuft bis zum
#    Zeilenende (er darf Leerzeichen enthalten).
_GEHEIM_ZEILENANFANG = re.compile(
    r"(?im)^(\s*[\w.$-]*" + _GEHEIM_WORT + r"[\w.$-]*\s*[:=]).*$")

# 2. In einer LOGZEILE steht davor immer ein Zeitstempel und ein Thread-Praefix:
#       [12:00:00] [Server thread/INFO]: rcon.password=meinPasswort
#    Die an ^ gebundenen Muster griffen dort nicht - und die Log-/Crash-Analyse schickt
#    ausschliesslich Logzeilen hinaus. Besonders heikel: die Zeile
#    "JVM Flags: 3 total; -Xmx8G -Dapi_key=..." steht in JEDEM Crash-Report.
#    Entfernt wird nur der WERT, nicht der Rest der Zeile - der wird zur Diagnose
#    gebraucht.
_GEHEIM_INLINE = re.compile(
    r"(?i)(?<![\w.$-])([\w.$-]*" + _GEHEIM_STARK + r"[\w.$-]*\s*[:=]\s*)(" + _WERT + r")")

# Fuer "token" dasselbe, aber eine kurze reine Zahl bleibt stehen: "max_tokens: 2048" ist
# ein Zaehler, kein Geheimnis, und ihn zu verstecken wuerde nur die Diagnose erschweren.
# Ein langer Zahlenwert (ab 8 Stellen) gilt wieder als Geheimnis.
_GEHEIM_INLINE_TOKEN = re.compile(
    r"(?i)(?<![\w.$-])([\w.$-]*" + _GEHEIM_ZAEHLBAR + r"[\w.$-]*\s*[:=]\s*)"
    r"(?!\d{1,7}(?:[\s,;)\]}]|$))(" + _WERT + r")")

_GEHEIM_ZEILEN = [
    (_GEHEIM_ZEILENANFANG, r"\1 <entfernt>"),
    (_GEHEIM_INLINE, r"\1<entfernt>"),
    (_GEHEIM_INLINE_TOKEN, r"\1<entfernt>"),
    # "Authorization: Bearer eyJ..." - zwischen Schluessel und Wert steht ein Leerzeichen,
    # an dem das Inline-Muster endet.
    (re.compile(r"(?i)\bBearer\s+[A-Za-z0-9._~+/=-]{8,}"), "Bearer <entfernt>"),
    # Ein JWT ist auch ohne Schluesselwort als solcher erkennbar.
    (re.compile(r"\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}"),
     "<entfernter Token>"),
    # Webhook-URLs tragen das Geheimnis im Pfad: wer die URL hat, kann posten.
    (re.compile(r"(?i)https?://(?:[\w.-]*\.)?(?:discord(?:app)?\.com/api/webhooks|"
                r"hooks\.slack\.com/services)/\S+"), "<entfernte Webhook-URL>"),
    # Geheimnisse als Abfrageparameter in einer URL.
    (re.compile(r"(?i)([?&](?:" + _GEHEIM_WORT + r")=)[^&\s]+"), r"\1<entfernt>"),
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

    Mit Port ist es immer eine. Ohne Port entscheidet der Kontext - aber in der anderen
    Richtung als zuerst gebaut: im Zweifel ist es eine ADRESSE und wird entfernt.

    Vorher war ein positives Kontextwort noetig ("from", "connection", "address"). Das
    passte zu Logzeilen, liess aber jede freistehende Adresse stehen - etwa eine, die der
    Nutzer selbst in das Fragefeld schreibt ("vorhin stand da 198.51.100.4"). Die Zusage
    lautet, dass IP-Adressen entfernt werden; eine Ausnahme fuer Zeilen ohne
    Schluesselwort hoehlt sie aus.

    Stehen bleibt eine Zahlenfolge nur, wenn die Zeile erkennbar von einer VERSION
    spricht ("Loader-Version 47.2.0.1" ist eine gueltige IPv4, und sie zu entfernen nimmt
    der Crash-Analyse die Antwort auf "welche Version?"). Zahlen, die zu einem Namen
    gehoeren ("jei-1.21.1-19.21.0.247"), faengt ohnehin _teil_eines_namens ab.
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
    if _IP_KONTEXT.search(zeile):
        return True
    return not _VERSION_KONTEXT.search(zeile)


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
