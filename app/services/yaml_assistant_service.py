"""Assistent fuer YAML-Dateien (spigot.yml, bukkit.yml, paper-*.yml, Plugin-Configs).

Der entscheidende Unterschied zu den anderen Assistenten: hier wird die Datei NICHT neu
erzeugt. YAML-Konfigurationen von Minecraft-Servern bestehen zur Haelfte aus Kommentaren -
``yaml.safe_load`` + ``yaml.dump`` wuerfe die alle weg, zusammen mit der Reihenfolge, den
Leerzeilen und der Anfuehrungszeichen-Wahl. Stattdessen wird nur der Textabschnitt des
geaenderten Wertes ersetzt; alles andere bleibt Zeichen fuer Zeichen stehen. Wer nichts
aendert und speichert, bekommt eine byteweise identische Datei zurueck.

Dafuer liefert ``yaml.compose`` genau das Richtige: einen Baum aus Knoten MIT Marken, also
den Anfangs- und Endpositionen im Originaltext. Gebaut wird daraus kein Python-Objekt (das
waere die unsichere Seite von PyYAML), sondern nur die Liste der Felder.
"""

from __future__ import annotations

import json
from typing import Any

import yaml

# Mehr Felder als das zeigt keine Oberflaeche sinnvoll an - paper-world-defaults.yml kommt
# auf einige hundert. Der Rest bleibt ueber "Freitext" erreichbar.
MAX_FELDER = 400

# Groesser als das wird nicht mehr zerlegt. Die groesste echte Konfiguration im Manager
# (paper-world-defaults.yml) liegt bei rund 9 KB; alles jenseits davon ist entweder eine
# Datenhalde oder ein Versuch, den Webserver zu beschaeftigen.
MAX_ZEICHEN = 512_000

# Nur diese Standardtypen werden angeboten. Alles andere (!!binary, eigene Tags, Zeitstempel)
# laesst sich nicht verlaesslich aus einem Formularfeld zurueckschreiben.
_TAGS = {
    "tag:yaml.org,2002:str": "text",
    "tag:yaml.org,2002:int": "int",
    "tag:yaml.org,2002:float": "float",
    "tag:yaml.org,2002:bool": "bool",
    "tag:yaml.org,2002:null": "frei",
}

# Zeichen, mit denen ein einfacher (unquotierter) YAML-Wert nicht anfangen darf.
_HEIKEL_ANFANG = set("-?:,[]{}#&*!|>'\"%@`")


class YamlAssistentFehler(ValueError):
    """Sagt dem Aufrufer, warum nichts geschrieben wurde - der Text geht an den Nutzer."""


# --------------------------------------------------------------------- Lesen


def _pfad_label(pfad: list[Any]) -> str:
    teile: list[str] = []
    for token in pfad:
        if isinstance(token, int):
            teile.append(f"[{token}]")
        elif teile:
            teile.append(f".{token}")
        else:
            teile.append(str(token))
    return "".join(teile)


def _sammle(knoten: Any, pfad: list[Any], text: str,
            raus: list[dict], gesehen: set[int], doppelt: set[str]) -> None:
    """Geht den Knotenbaum durch und merkt sich jeden einzeilig ersetzbaren Skalar."""
    # Was yaml.compose liefert, ist kein Baum, sondern ein Geflecht: ein Alias (*name)
    # gibt DENSELBEN Knoten zurueck, auf den der Anker zeigt. Diese Sperre stand erst beim
    # Skalar - die Rekursion stieg also in jede Liste, auf die mehrere Aliase zeigen,
    # immer wieder neu ab. Neun Zeilen mit je neun Aliasen ergaben 9^8 Knotenbesuche:
    # gemessene 137 Sekunden fuer eine 486 Byte grosse Datei, bei jedem Oeffnen der
    # Dateiseite, ausloesbar mit reinem Leserecht. Die Sperre gehoert deshalb nach ganz
    # oben und gilt fuer JEDE Knotenart.
    if id(knoten) in gesehen:
        return
    gesehen.add(id(knoten))
    if len(raus) > MAX_FELDER:
        return   # mehr zeigt die Oberflaeche ohnehin nicht an

    if isinstance(knoten, yaml.MappingNode):
        # Doppelte Schluessel in derselben Ebene zuerst einsammeln: YAML nimmt beim Laden
        # den LETZTEN, der Assistent wuerde den ersten anzeigen und aendern. Dann steht im
        # Feld ein anderer Wert als der, der gilt. Also fliegen beide raus - nicht nur der
        # zweite, denn der erste ist genauso irrefuehrend.
        zaehler: dict[str, int] = {}
        for schluessel_knoten, _ in knoten.value:
            if isinstance(schluessel_knoten, yaml.ScalarNode):
                name = str(schluessel_knoten.value)
                zaehler[name] = zaehler.get(name, 0) + 1
        for schluessel_knoten, wert_knoten in knoten.value:
            if not isinstance(schluessel_knoten, yaml.ScalarNode):
                continue   # zusammengesetzte Schluessel ("? [a, b]") lassen wir in Ruhe
            name = str(schluessel_knoten.value)
            if zaehler.get(name, 0) > 1:
                doppelt.add(_pfad_label(pfad + [name]))
                continue
            _sammle(wert_knoten, pfad + [name], text, raus, gesehen, doppelt)
        return

    if isinstance(knoten, yaml.SequenceNode):
        for index, eintrag in enumerate(knoten.value):
            _sammle(eintrag, pfad + [index], text, raus, gesehen, doppelt)
        return

    if not isinstance(knoten, yaml.ScalarNode) or not pfad:
        return

    art = _TAGS.get(knoten.tag)
    if art is None:
        return
    if knoten.style in {"|", ">"}:
        return   # Blockskalar ueber mehrere Zeilen - Einrueckung waere nicht sicher zu treffen
    if knoten.start_mark.line != knoten.end_mark.line:
        return   # mehrzeiliger Wert

    roh = text[knoten.start_mark.index:knoten.end_mark.index]
    if roh.startswith("&") or roh.startswith("*"):
        return   # der Abschnitt enthaelt den Anker, den wuerden wir beim Ersetzen zerstoeren

    wert = roh if art == "frei" else str(knoten.value)
    if art == "text" and knoten.style in {"'", '"'}:
        wert = str(knoten.value)
    if art == "frei":
        wert = ""   # leerer Schluessel ("key:") - das Feld startet leer

    raus.append({
        "pfad": list(pfad),
        "label": _pfad_label(pfad),
        "gruppe": _pfad_label(pfad[:-1]),
        "name": str(pfad[-1]) if not isinstance(pfad[-1], int) else f"[{pfad[-1]}]",
        "art": art,
        "stil": knoten.style or "",
        "wert": wert,
        "roh": roh,
    })


def _felder(text: str) -> tuple[list[dict], set[str]]:
    wurzel = yaml.compose(text)
    raus: list[dict] = []
    doppelt: set[str] = set()
    if wurzel is not None:
        _sammle(wurzel, [], text, raus, set(), doppelt)
    return raus, doppelt


def baue(text: str) -> tuple[dict | None, str]:
    """``(payload, grund)``. Ist payload None, erklaert grund dem Nutzer warum.

    Diese Funktion wirft nichts. Sie laeuft beim blossen OEFFNEN einer Datei, auch im
    Freitext-Modus - eine Ausnahme von hier waere ein HTTP 500 und die Datei liesse sich
    nicht einmal mehr reparieren.
    """
    if len(text) > MAX_ZEICHEN:
        return None, (f"Die Datei ist groesser als {MAX_ZEICHEN // 1024} KB - dafuer gibt "
                      f"es keinen Assistenten. Aenderungen gehen ueber den Freitext.")
    try:
        gefunden, doppelt = _felder(text)
    except (yaml.YAMLError, RecursionError) as exc:
        # RecursionError ist KEIN YAMLError: eine Datei mit 500 offenen Klammern liess die
        # Seite vorher mit HTTP 500 abbrechen statt mit einer Meldung.
        kurz = str(exc).splitlines()[0] if str(exc) else "zu tief verschachtelt"
        return None, (f"Die Datei ist kein gueltiges oder zu tief verschachteltes YAML "
                      f"({kurz}). Bitte im Freitext berichtigen - danach greift der "
                      f"Assistent wieder.")
    try:
        # Nur zur Probe: laesst sich die Datei ueberhaupt laden? Wenn nicht, koennen wir
        # spaeter nicht pruefen, ob unsere Aenderung angekommen ist.
        yaml.safe_load(text)
    except (yaml.YAMLError, RecursionError):
        return None, ("Die Datei enthaelt YAML, das der Manager nicht sicher laden kann "
                      "(eigene Tags, zu tief verschachtelt?). Bitte den Freitext "
                      "benutzen.")

    if not gefunden:
        return None, ("In dieser Datei steht kein einzelner Wert, den der Assistent sicher "
                      "aendern koennte - nur Listen, mehrzeilige Texte oder Kommentare.")

    gekuerzt = len(gefunden) > MAX_FELDER
    gefunden = gefunden[:MAX_FELDER]

    felder: list[dict] = []
    meta: list[dict] = []
    for index, eintrag in enumerate(gefunden):
        schluessel = f"yaml_field_{index}"
        art = eintrag["art"]
        if art == "bool":
            feld = {"key": schluessel, "kind": "select", "options": ["true", "false"],
                    "value": eintrag["wert"]}
        elif art in {"int", "float"}:
            feld = {"key": schluessel, "kind": "number", "value": eintrag["wert"],
                    "placeholder": ""}
        else:
            feld = {"key": schluessel, "kind": "text", "value": eintrag["wert"],
                    "placeholder": "leer" if art == "frei" else ""}
        feld["label"] = eintrag["name"]
        feld["gruppe"] = eintrag["gruppe"]
        feld["titel"] = eintrag["label"]
        felder.append(feld)
        meta.append({"key": schluessel, "pfad": eintrag["pfad"], "art": art,
                     "stil": eintrag["stil"], "wert": eintrag["wert"],
                     "roh": eintrag["roh"]})

    hinweise: list[str] = []
    if gekuerzt:
        hinweise.append(f"Nur die ersten {MAX_FELDER} Werte werden angezeigt; der Rest ist "
                        f"ueber den Freitext erreichbar.")
    if doppelt:
        hinweise.append("Doppelt vergebene Schluessel sind ausgelassen: "
                        + ", ".join(sorted(doppelt)[:5]) + ".")
    hinweise.append("Kommentare, Reihenfolge und Formatierung bleiben erhalten - geaendert "
                    "wird nur der Wert selbst. Listen und mehrzeilige Texte stehen nicht im "
                    "Assistenten.")

    return {
        "mode": "yaml_fields",
        "title": "YAML Assistent",
        "fields": felder,
        "extras_text": "",
        "assistant_yaml_meta": json.dumps(meta, ensure_ascii=False),
        "hinweis": " ".join(hinweise),
    }, ""


# -------------------------------------------------------------------- Schreiben


def _knoten_an_pfad(wurzel: Any, pfad: list[Any]) -> Any:
    knoten = wurzel
    for token in pfad:
        if isinstance(token, int):
            if not isinstance(knoten, yaml.SequenceNode) or token >= len(knoten.value):
                return None
            knoten = knoten.value[token]
            continue
        if not isinstance(knoten, yaml.MappingNode):
            return None
        treffer = None
        for schluessel_knoten, wert_knoten in knoten.value:
            if isinstance(schluessel_knoten, yaml.ScalarNode) and \
                    str(schluessel_knoten.value) == str(token):
                treffer = wert_knoten
                break
        if treffer is None:
            return None
        knoten = treffer
    return knoten


def _wert_an_pfad(geladen: Any, pfad: list[Any]) -> Any:
    kursor = geladen
    for token in pfad:
        if isinstance(token, int):
            if not isinstance(kursor, list) or token >= len(kursor):
                raise KeyError(token)
            kursor = kursor[token]
            continue
        if not isinstance(kursor, dict) or token not in kursor:
            raise KeyError(token)
        kursor = kursor[token]
    return kursor


def _einfach_moeglich(wert: str) -> bool:
    """Darf dieser Text ohne Anfuehrungszeichen stehen?"""
    if not wert or wert != wert.strip():
        return False
    if any(zeichen in wert for zeichen in ("\n", "\r", "\t", "#", ": ")):
        return False
    if wert.endswith(":") or wert[0] in _HEIKEL_ANFANG:
        return False
    return True


def _skalar_text(wert: str, art: str, stil: str, label: str) -> tuple[str, Any]:
    """Baut den Text, der in die Datei geht, und den Wert, der danach herauskommen muss."""
    if "\n" in wert or "\r" in wert:
        raise YamlAssistentFehler(f"'{label}': mehrzeilige Werte gehen nur ueber den Freitext.")

    if art == "bool":
        klein = wert.strip().lower()
        if klein not in {"true", "false"}:
            raise YamlAssistentFehler(f"'{label}': erlaubt sind nur true oder false.")
        return klein, klein == "true"

    if art in {"int", "float"}:
        roh = wert.strip()
        if not roh:
            raise YamlAssistentFehler(f"'{label}': hier muss eine Zahl stehen.")
        try:
            zahl: Any = int(roh) if art == "int" else float(roh)
        except ValueError as exc:
            raise YamlAssistentFehler(f"'{label}': '{roh}' ist keine Zahl.") from exc
        return roh, zahl

    if art == "frei":
        # Leerer Schluessel ohne festgelegten Typ: der eingegebene Text darf sich selbst
        # aussuchen, was er ist (12 wird eine Zahl, true ein Wahrheitswert).
        roh = wert.strip()
        if not roh:
            return "", None
        if _einfach_moeglich(roh):
            try:
                return roh, yaml.safe_load(roh)
            except yaml.YAMLError:
                pass
        return json.dumps(roh, ensure_ascii=False), roh

    # Text. Die urspruengliche Schreibweise wird beibehalten, damit sich die Datei optisch
    # nicht veraendert.
    if stil == "'":
        return "'" + wert.replace("'", "''") + "'", wert
    if stil == '"':
        return json.dumps(wert, ensure_ascii=False), wert
    if _einfach_moeglich(wert):
        try:
            # "true", "12" oder "~" duerfen nicht unquotiert stehen - sonst wird aus einem
            # Text stillschweigend ein Wahrheitswert, eine Zahl oder nichts.
            if yaml.safe_load(wert) == wert:
                return wert, wert
        except yaml.YAMLError:
            pass
    # Doppelte Anfuehrungszeichen von YAML sind mit denen von JSON identisch.
    return json.dumps(wert, ensure_ascii=False), wert


def schreibe(aktuell: str, form_data: dict[str, str], meta_roh: str) -> str:
    """Setzt die geaenderten Werte in den vorhandenen Text ein und gibt ihn zurueck."""
    try:
        meta = json.loads(meta_roh or "[]")
    except Exception as exc:  # noqa: BLE001
        raise YamlAssistentFehler("Die Formulardaten sind unvollstaendig - bitte die Seite "
                                  "neu laden.") from exc
    if not isinstance(meta, list):
        raise YamlAssistentFehler("Die Formulardaten sind unvollstaendig - bitte die Seite "
                                  "neu laden.")

    if len(aktuell) > MAX_ZEICHEN:
        raise YamlAssistentFehler("Die Datei ist zu gross fuer den Assistenten.")
    try:
        wurzel = yaml.compose(aktuell)
    except (yaml.YAMLError, RecursionError) as exc:
        raise YamlAssistentFehler(
            f"Die Datei ist zurzeit kein gueltiges YAML: {exc}") from exc

    ersetzungen: list[tuple[int, int, str]] = []
    erwartet: list[tuple[list[Any], Any]] = []

    for eintrag in meta:
        if not isinstance(eintrag, dict):
            continue
        schluessel = str(eintrag.get("key") or "")
        if schluessel not in form_data:
            continue
        pfad = eintrag.get("pfad")
        if not isinstance(pfad, list) or not pfad:
            continue
        label = _pfad_label(pfad)
        alt = str(eintrag.get("wert") or "")
        neu = str(form_data.get(schluessel) or "").strip()
        if neu == alt:
            continue

        knoten = _knoten_an_pfad(wurzel, pfad)
        if not isinstance(knoten, yaml.ScalarNode):
            raise YamlAssistentFehler(
                f"'{label}' steht nicht mehr dort, wo es beim Oeffnen stand. Die Datei "
                f"wurde zwischenzeitlich geaendert - bitte neu laden.")
        von, bis = knoten.start_mark.index, knoten.end_mark.index
        if aktuell[von:bis] != str(eintrag.get("roh") or ""):
            raise YamlAssistentFehler(
                f"'{label}' hat sich seit dem Oeffnen geaendert. Bitte die Seite neu laden, "
                f"damit nichts ueberschrieben wird.")

        text, wert = _skalar_text(neu, str(eintrag.get("art") or "text"),
                                  str(eintrag.get("stil") or ""), label)
        if text and von == bis and von > 0 and aktuell[von - 1] not in " \t\n":
            text = " " + text   # leerer Schluessel: "key:" braucht das Leerzeichen
        ersetzungen.append((von, bis, text))
        erwartet.append((list(pfad), wert))

    if not ersetzungen:
        return aktuell

    # Von hinten nach vorne einsetzen, damit die vorderen Positionen gueltig bleiben.
    neu_text = aktuell
    for von, bis, text in sorted(ersetzungen, key=lambda eintrag: eintrag[0], reverse=True):
        neu_text = neu_text[:von] + text + neu_text[bis:]

    # Gegenprobe: Ergibt das Ergebnis noch gueltiges YAML, und steht ueberall das, was der
    # Nutzer eingetragen hat? Wenn nicht, wird nichts geschrieben.
    try:
        geladen = yaml.safe_load(neu_text)
    except (yaml.YAMLError, RecursionError) as exc:
        raise YamlAssistentFehler(
            f"Die Aenderung haette die Datei unlesbar gemacht, es wurde nichts "
            f"gespeichert ({str(exc).splitlines()[0]}).") from exc
    for pfad, wert in erwartet:
        try:
            tatsaechlich = _wert_an_pfad(geladen, pfad)
        except (KeyError, TypeError) as exc:
            raise YamlAssistentFehler(
                f"'{_pfad_label(pfad)}' kam nach dem Speichern nicht mehr vor - es wurde "
                f"nichts geschrieben.") from exc
        if tatsaechlich != wert and not (tatsaechlich is None and wert is None):
            raise YamlAssistentFehler(
                f"'{_pfad_label(pfad)}' haette nach dem Schreiben einen anderen Wert gehabt "
                f"({tatsaechlich!r} statt {wert!r}) - es wurde nichts geschrieben.")

    return neu_text
