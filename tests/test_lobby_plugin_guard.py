"""TransferGuard.java: wer darf eine Rueckfrage beantworten?

Regressionsschutz fuer einen Fehler, der die Rueckfrage vollstaendig aushebelte:
``onMove`` loest beim Durchlaufen einer Portal-Region bei JEDEM Blockwechsel einen
Transfer aus. Solange jede Wiederholung als Zustimmung zaehlte, lieferte der naechste
Schritt den "zweiten Klick" selbst - der Spieler wurde transferiert, bevor er die Frage
lesen konnte, und landete genau im Disconnect-Screen, den die Pruefung verhindern soll.

Geprueft wird das ECHTE Java, nicht eine Python-Nachbildung - eine Nachbildung wuerde
genau den Fehler nicht finden, den sie nachbaut. TransferGuard haengt bewusst an keiner
Bukkit-API und laesst sich deshalb allein uebersetzen. Ohne javac wird uebersprungen.
"""
from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

import pytest

_SRC = (Path(__file__).resolve().parents[1]
        / "app" / "assets" / "lobby_plugin" / "src" / "net" / "mcsm" / "lobby")

_HARNESS = r'''
package net.mcsm.lobby;

/** Nur fuer den Test: ruft TransferGuard auf und schreibt je Zeile true/false. */
public final class GuardHarness {
    public static void main(String[] args) {
        for (String spec : args) {
            String[] p = spec.split(":");
            Long asked = p[0].equals("null") ? null : Long.valueOf(Long.parseLong(p[0]));
            long now = Long.parseLong(p[1]);
            boolean deliberate = Boolean.parseBoolean(p[2]);
            System.out.println(TransferGuard.mayOverride(asked, now, deliberate, 1000L, 60000L));
        }
    }
}
'''


def _javac() -> str | None:
    """javac aus dem Manager-JDK oder dem PATH."""
    try:
        from app.db.session import SessionLocal
        from app.services import java_runtime_service, plugin_build_service

        with SessionLocal() as db:
            java_bin = java_runtime_service.resolve_java_binary(db, 17)
        found = plugin_build_service._tool_from_java(java_bin, "javac")
        if found:
            return found
    except Exception:  # noqa: BLE001 - kein JDK ueber den Manager -> PATH versuchen
        pass
    return shutil.which("javac")


@pytest.fixture(scope="module")
def guard(tmp_path_factory):
    """TransferGuard + Harness uebersetzen; liefert eine Funktion spec -> bool."""
    javac = _javac()
    if not javac:
        pytest.skip("kein javac verfuegbar")
    java = str(Path(javac).with_name("java" + Path(javac).suffix))

    work = tmp_path_factory.mktemp("guard")
    pkg = work / "net" / "mcsm" / "lobby"
    pkg.mkdir(parents=True)
    shutil.copy2(_SRC / "TransferGuard.java", pkg / "TransferGuard.java")
    (pkg / "GuardHarness.java").write_text(_HARNESS, encoding="utf-8")

    build = subprocess.run(
        [javac, "--release", "17", "-d", str(work),
         str(pkg / "TransferGuard.java"), str(pkg / "GuardHarness.java")],
        capture_output=True, text=True, cwd=str(work))
    if build.returncode != 0:
        pytest.fail(f"TransferGuard uebersetzt nicht:\n{build.stdout}\n{build.stderr}")

    def run(*specs: str) -> list[bool]:
        out = subprocess.run([java, "-cp", ".", "net.mcsm.lobby.GuardHarness", *specs],
                             capture_output=True, text=True, cwd=str(work))
        assert out.returncode == 0, out.stderr
        return [line.strip() == "true" for line in out.stdout.strip().splitlines()]

    return run


def test_a_step_never_answers_the_question(guard):
    """DER Fehler: ein Schritt in einer Portal-Region darf nie zustimmen.

    onMove uebergibt deliberate=false. Selbst mit perfekt passendem Zeitstempel muss
    die Antwort false sein - sonst beantwortet die Bewegung die eigene Rueckfrage.
    """
    assert guard("1000:5000:false") == [False]      # Alter 4 s, aber unbewusst
    assert guard("1000:1000:false") == [False]      # im selben Augenblick
    assert guard("1000:59000:false") == [False]     # spaet im Fenster


def test_a_deliberate_click_answers_it(guard):
    """Menue-Klick, /server, Schild: deliberate=true und die Frage stand lange genug."""
    assert guard("1000:2500:true") == [True]        # 1,5 s spaeter
    assert guard("1000:2000:true") == [True]        # exakt Mindestalter erreicht
    assert guard("1000:60000:true") == [True]       # kurz vor Fensterende


def test_too_fast_does_not_count(guard):
    """Doppelklick oder zwei Events im selben Tick sind keine gelesene Antwort."""
    assert guard("1000:1000:true") == [False]       # 0 ms
    assert guard("1000:1500:true") == [False]       # 0,5 s - zu schnell gelesen
    assert guard("1000:1999:true") == [False]       # eine Millisekunde zu frueh


def test_expired_question_does_not_count(guard):
    assert guard("1000:61000:true") == [False]      # exakt am Fensterende
    assert guard("1000:120000:true") == [False]     # lange darueber


def test_without_an_open_question_nothing_is_overridden(guard):
    assert guard("null:5000:true", "null:5000:false") == [False, False]


def test_clock_going_backwards_is_not_consent(guard):
    """Negatives Alter (Uhr zurueckgestellt) darf nicht als Zustimmung durchgehen."""
    assert guard("5000:1000:true") == [False]
