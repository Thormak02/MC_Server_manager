"""API-Schluessel so ablegen, dass er nicht offen einsehbar ist.

Anforderung des Nutzers: der Schluessel darf nirgends im Klartext stehen. Die vorhandene
Ablage fuer Anbieter-Schluessel (``platform_settings_service``) kodiert nur mit Base64 -
das ist Verschleierung, keine Verschluesselung, und aus der Datenbank jederzeit
zurueckrechenbar.

Hier wird deshalb **DPAPI** benutzt, die Verschluesselung von Windows selbst
(``crypt32.dll``). Vorteil gegenueber einem eigenen Schluessel: es gibt kein Hauptpasswort,
das man wieder irgendwo ablegen muesste - Windows haelt das Material selbst.

**Gebunden an den RECHNER** (``CRYPTPROTECT_LOCAL_MACHINE``), nicht an das Windows-Konto.
Der Manager laeuft live als SYSTEM (siehe ``scripts/install_startup_task.ps1``), wird aber
zum Testen auch von Hand unter einem Benutzerkonto gestartet. Kontogebunden waere der
Schluessel nach jedem solchen Wechsel unlesbar. Der Sicherheitsunterschied ist gering: wer
auf diesem PC Administrator ist, kommt an SYSTEM-Daten ohnehin heran.

Was das trotzdem leistet: ein Datenbank- oder Ordner-Backup auf der NAS enthaelt keinen
nutzbaren Schluessel - auf einem anderen Rechner laesst sich der Wert nicht entschluesseln.
Genau das ist der Fall, der hier zaehlt, denn die Sicherungen liegen auf einer Freigabe.

Auf Nicht-Windows-Systemen gibt es DPAPI nicht. Dort verweigert dieses Modul das Speichern
mit klarer Meldung, statt stillschweigend auf etwas Schwaecheres auszuweichen.
"""

from __future__ import annotations

import base64
import ctypes
import ctypes.wintypes as wintypes
import sys

# Der Wert bindet das Geheimnis an den Rechner statt an das Konto.
CRYPTPROTECT_LOCAL_MACHINE = 0x4

# Kennzeichen vor dem verschluesselten Wert: daran ist erkennbar, dass es ein
# DPAPI-Geheimnis ist und nicht etwa ein versehentlich im Klartext abgelegter Schluessel.
PRAEFIX = "dpapi:v1:"


class GeheimnisFehler(Exception):
    """Das Geheimnis liess sich nicht ver- oder entschluesseln."""


class _BLOB(ctypes.Structure):
    _fields_ = [("cbData", wintypes.DWORD),
                ("pbData", ctypes.POINTER(ctypes.c_char))]


def verfuegbar() -> bool:
    """Steht DPAPI auf diesem System zur Verfuegung?"""
    return sys.platform == "win32"


def _blob(roh: bytes) -> _BLOB:
    puffer = ctypes.create_string_buffer(roh, len(roh))
    return _BLOB(len(roh), ctypes.cast(puffer, ctypes.POINTER(ctypes.c_char)))


def _inhalt(blob: _BLOB) -> bytes:
    try:
        return ctypes.string_at(blob.pbData, blob.cbData)
    finally:
        # Windows hat den Puffer belegt; ohne LocalFree waechst der Speicher mit jedem
        # Aufruf. Bei einem Dienst, der monatelang laeuft, faellt das irgendwann auf.
        ctypes.windll.kernel32.LocalFree(blob.pbData)


def verschluessele(wert: str) -> str:
    """Klartext -> abzulegender Wert (mit Kennzeichen, base64-verpackt).

    Base64 dient hier nur dazu, die Bytes in einer Textspalte ablegen zu koennen - die
    Sicherheit kommt von DPAPI, nicht von der Kodierung.
    """
    if not verfuegbar():
        raise GeheimnisFehler(
            "Verschluesselte Ablage braucht Windows (DPAPI). Auf diesem System kann der "
            "Schluessel nicht sicher gespeichert werden.")
    roh = (wert or "").encode("utf-8")
    if not roh:
        raise GeheimnisFehler("Es wurde kein Wert angegeben.")
    ein, aus = _blob(roh), _BLOB()
    ok = ctypes.windll.crypt32.CryptProtectData(
        ctypes.byref(ein), None, None, None, None,
        CRYPTPROTECT_LOCAL_MACHINE, ctypes.byref(aus))
    if not ok:
        raise GeheimnisFehler(
            f"Windows konnte den Schluessel nicht verschluesseln "
            f"(Fehler {ctypes.get_last_error()}).")
    return PRAEFIX + base64.b64encode(_inhalt(aus)).decode("ascii")


def entschluessele(abgelegt: str) -> str:
    """Abgelegter Wert -> Klartext. Leerer Rueckgabewert heisst "nicht vorhanden"."""
    text = (abgelegt or "").strip()
    if not text:
        return ""
    if not text.startswith(PRAEFIX):
        # Kein DPAPI-Wert. Nicht einfach durchreichen: ein Klartext-Schluessel in der
        # Datenbank ist genau der Zustand, den diese Ablage verhindern soll.
        raise GeheimnisFehler(
            "Der abgelegte Wert ist nicht verschluesselt. Bitte den Schluessel in den "
            "Einstellungen neu eintragen.")
    if not verfuegbar():
        raise GeheimnisFehler("Entschluesseln braucht Windows (DPAPI).")
    try:
        roh = base64.b64decode(text[len(PRAEFIX):].encode("ascii"))
    except Exception as exc:  # noqa: BLE001
        raise GeheimnisFehler("Der abgelegte Wert ist beschaedigt.") from exc

    ein, aus = _blob(roh), _BLOB()
    ok = ctypes.windll.crypt32.CryptUnprotectData(
        ctypes.byref(ein), None, None, None, None,
        CRYPTPROTECT_LOCAL_MACHINE, ctypes.byref(aus))
    if not ok:
        raise GeheimnisFehler(
            "Windows konnte den Schluessel nicht entschluesseln. Das passiert, wenn er auf "
            "einem ANDEREN Rechner eingetragen wurde - etwa weil eine Datenbank-Sicherung "
            "hierher kopiert wurde. Bitte neu eintragen.")
    return _inhalt(aus).decode("utf-8", "replace")


def ist_verschluesselt(abgelegt: str) -> bool:
    return (abgelegt or "").strip().startswith(PRAEFIX)


def maskiere(wert: str) -> str:
    """Schluessel so darstellen, dass er wiedererkennbar, aber nicht nutzbar ist.

    Nach dem Speichern zeigt die Oberflaeche nur noch diese Form - auch dem Super-Admin.
    Zum Pruefen, ob der richtige Schluessel hinterlegt ist, genuegt der Anfang; den
    vollstaendigen Wert braucht niemand mehr zu sehen.
    """
    text = (wert or "").strip()
    if not text:
        return ""
    if len(text) <= 12:
        return "*" * len(text)
    return f"{text[:8]}{'*' * 12}{text[-4:]}"
