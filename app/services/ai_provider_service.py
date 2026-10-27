"""Die Schnittstelle zum KI-Anbieter - schmal gehalten, damit ein Wechsel leicht bleibt.

Anforderung des Nutzers: "API-Anbieter einfach aenderbar". Umgesetzt als schmale
Schnittstelle (Anfrage rein, Antwort samt Verbrauch raus) mit Claude als erster Umsetzung.
Ein weiterer Anbieter ist spaeter eine neue Klasse in dieser Datei plus ein Eintrag in
ANBIETER - nichts anderes im Manager muss sich aendern.

Bewusst NICHT das offizielle ``anthropic``-Paket: der Manager soll ohne neue Abhaengigkeit
laufen, und gebraucht wird nur ein HTTP-Aufruf. ``urllib`` liegt bei und wird im Projekt
schon an mehreren Stellen so benutzt (avatar_service, providers/server/common).

**Preise** stehen in MODELLE. Sie sind in US-Cent je Million Token angegeben und stammen aus
oeffentlichen Preisuebersichten vom Oktober 2026 - NICHT von Anthropic selbst bestaetigt.
Deshalb sind sie in den Einstellungen aenderbar: wer mit falschen Preisen rechnet, bekommt
einen falschen Budgetstand, und darauf soll sich niemand blind verlassen.
"""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from dataclasses import dataclass, field


class KiFehler(Exception):
    """Der Anbieter hat nicht geantwortet oder einen Fehler gemeldet."""


class KiUeberlastFehler(KiFehler):
    """Der Anbieter ist ueberlastet oder das eigene Kontingent dort ist erschoepft -
    ein spaeterer Versuch kann klappen."""


@dataclass
class KiAnfrage:
    auftrag: str
    """Systemauftrag: legt die Rolle fest und damit die Themen-Sperre."""

    nachrichten: list[dict]
    """[{"rolle": "nutzer"|"assistent", "inhalt": "..."}], aelteste zuerst."""

    modell: str
    max_tokens: int = 2048
    temperatur: float | None = None
    werkzeuge: list[dict] = field(default_factory=list)
    """Werkzeugbeschreibungen fuer den Agenten. Leer = reine Textantwort."""


@dataclass
class KiAntwort:
    text: str
    modell: str
    tokens_ein: int
    tokens_aus: int
    werkzeug_aufrufe: list[dict] = field(default_factory=list)
    """[{"name": ..., "eingabe": {...}, "id": ...}] - was die KI TUN will. Ausgefuehrt
    wird erst nach Bestaetigung durch den Nutzer; siehe ai_service."""

    abbruchgrund: str = ""
    """Warum die Antwort endete: "ende", "laenge", "werkzeug" - "laenge" heisst
    abgeschnitten, das muss der Nutzer erfahren."""


# Name -> Angaben. cent_ein/cent_aus sind US-Cent je 1 Million Token.
MODELLE: dict[str, dict] = {
    "claude-opus-5-5": {
        "anbieter": "anthropic", "anzeige": "Opus 5.5 (stark, teuer)",
        "cent_ein": 400.0, "cent_aus": 2000.0,
    },
    "claude-sonnet-5-5": {
        "anbieter": "anthropic", "anzeige": "Sonnet 5.5 (ausgewogen)",
        "cent_ein": 200.0, "cent_aus": 1000.0,
    },
    "claude-haiku-5-5": {
        "anbieter": "anthropic", "anzeige": "Haiku 5.5 (schnell, guenstig)",
        "cent_ein": 10.0, "cent_aus": 50.0,
    },
}

# Welches Modell welche Funktion standardmaessig benutzt. Begruendung je Zeile: eine
# Changelog-Zusammenfassung ist Textarbeit und braucht kein starkes Modell, eine
# Crash-Analyse schon, und der Agent trifft Entscheidungen mit Folgen.
STANDARD_MODELLE: dict[str, str] = {
    "crash_analyse": "claude-sonnet-5-5",
    "config_assistent": "claude-sonnet-5-5",
    "changelog": "claude-haiku-5-5",
    "agent": "claude-opus-5-5",
}


def kosten_cent(modell: str, tokens_ein: int, tokens_aus: int,
                preise: dict | None = None) -> float:
    """Kosten eines Aufrufs in US-Cent. Unbekanntes Modell -> 0.0 (lieber nicht rechnen
    als falsch rechnen; der Aufruf steht trotzdem im Protokoll)."""
    angaben = (preise or {}).get(modell) or MODELLE.get(modell)
    if not angaben:
        return 0.0
    ein = float(angaben.get("cent_ein") or 0.0) * max(0, tokens_ein) / 1_000_000
    aus = float(angaben.get("cent_aus") or 0.0) * max(0, tokens_aus) / 1_000_000
    return ein + aus


class AnthropicAnbieter:
    """Claude ueber die Messages-API."""

    name = "anthropic"
    anzeige = "Anthropic (Claude)"
    schluessel_hinweis = ("Zu finden unter console.anthropic.com -> API Keys. Beginnt mit "
                          "sk-ant-. Der Schluessel wird verschluesselt abgelegt und danach "
                          "nur noch verkuerzt angezeigt.")
    url = "https://api.anthropic.com/v1/messages"
    api_version = "2023-06-01"

    def modelle(self) -> dict[str, dict]:
        return {name: angaben for name, angaben in MODELLE.items()
                if angaben.get("anbieter") == self.name}

    def pruefe_schluessel(self, schluessel: str) -> tuple[bool, str]:
        """Einen moeglichst billigen Aufruf machen, um den Schluessel zu pruefen."""
        try:
            antwort = self.frage(KiAnfrage(
                auftrag="Antworte mit genau dem Wort: bereit",
                nachrichten=[{"rolle": "nutzer", "inhalt": "bereit?"}],
                modell="claude-haiku-5-5", max_tokens=16), schluessel, zeitlimit=20.0)
        except KiFehler as exc:
            return False, str(exc)
        return True, f"Verbindung steht (Antwort: {antwort.text.strip()[:40]})."

    def frage(self, anfrage: KiAnfrage, schluessel: str, *,
              zeitlimit: float = 120.0) -> KiAntwort:
        if not (schluessel or "").strip():
            raise KiFehler("Es ist kein API-Schluessel hinterlegt.")

        inhalt: dict = {
            "model": anfrage.modell,
            "max_tokens": max(1, int(anfrage.max_tokens)),
            "system": anfrage.auftrag,
            "messages": [
                {"role": "assistant" if n.get("rolle") == "assistent" else "user",
                 "content": str(n.get("inhalt") or "")}
                for n in anfrage.nachrichten
            ],
        }
        if anfrage.temperatur is not None:
            inhalt["temperature"] = float(anfrage.temperatur)
        if anfrage.werkzeuge:
            inhalt["tools"] = anfrage.werkzeuge

        daten = json.dumps(inhalt).encode("utf-8")
        kopf = {
            "content-type": "application/json",
            "x-api-key": schluessel.strip(),
            "anthropic-version": self.api_version,
        }
        anfrage_obj = urllib.request.Request(self.url, data=daten, headers=kopf,
                                             method="POST")
        try:
            with urllib.request.urlopen(anfrage_obj, timeout=zeitlimit) as antwort:
                roh = json.loads(antwort.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raise self._http_fehler(exc) from exc
        except urllib.error.URLError as exc:
            raise KiFehler(f"Keine Verbindung zum Anbieter: {exc.reason}") from exc
        except Exception as exc:  # noqa: BLE001
            raise KiFehler(f"Unerwarteter Fehler beim Anbieter: {exc}") from exc

        return self._auswerten(roh, anfrage.modell)

    def _http_fehler(self, exc: urllib.error.HTTPError) -> KiFehler:
        try:
            inhalt = json.loads(exc.read().decode("utf-8"))
            meldung = ((inhalt.get("error") or {}).get("message")
                       or inhalt.get("message") or "")
        except Exception:  # noqa: BLE001
            meldung = ""
        if exc.code == 401:
            return KiFehler("Der API-Schluessel wurde abgelehnt. Bitte in den Einstellungen "
                            "pruefen.")
        if exc.code == 429:
            return KiUeberlastFehler(
                "Der Anbieter hat die Anfrage wegen zu vieler Zugriffe abgewiesen. In einem "
                "Moment erneut versuchen.")
        if exc.code == 400 and "credit" in meldung.lower():
            return KiUeberlastFehler(
                "Das Guthaben beim Anbieter ist erschoepft. Der Monatsdeckel des Managers "
                "ist davon unabhaengig - hier geht es um das Konto bei Anthropic.")
        if exc.code >= 500:
            return KiUeberlastFehler(f"Der Anbieter meldet eine Stoerung ({exc.code}).")
        return KiFehler(f"Anbieter-Fehler {exc.code}: {meldung or exc.reason}")

    def _auswerten(self, roh: dict, modell: str) -> KiAntwort:
        texte: list[str] = []
        werkzeuge: list[dict] = []
        for teil in roh.get("content") or []:
            art = teil.get("type")
            if art == "text":
                texte.append(str(teil.get("text") or ""))
            elif art == "tool_use":
                werkzeuge.append({"name": teil.get("name"),
                                  "eingabe": teil.get("input") or {},
                                  "id": teil.get("id")})
        verbrauch = roh.get("usage") or {}
        grund = {"end_turn": "ende", "max_tokens": "laenge",
                 "tool_use": "werkzeug", "stop_sequence": "ende"}.get(
                     str(roh.get("stop_reason") or ""), str(roh.get("stop_reason") or ""))
        return KiAntwort(
            text="\n".join(t for t in texte if t).strip(),
            modell=str(roh.get("model") or modell),
            tokens_ein=int(verbrauch.get("input_tokens") or 0),
            tokens_aus=int(verbrauch.get("output_tokens") or 0),
            werkzeug_aufrufe=werkzeuge,
            abbruchgrund=grund,
        )


ANBIETER: dict[str, AnthropicAnbieter] = {"anthropic": AnthropicAnbieter()}


def hole_anbieter(name: str = "anthropic"):
    schluessel = (name or "").strip().lower() or "anthropic"
    anbieter = ANBIETER.get(schluessel)
    if anbieter is None:
        raise KiFehler(f"Unbekannter KI-Anbieter: {name}")
    return anbieter
