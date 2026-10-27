"""Tests zur Verbrauchsmessung eines Servers.

Der Manager startet JEDEN Server ueber ``cmd /d /c ...`` (process_service.
_command_for_server). Gemessen wurde aber nur der Prozess, den er selbst gestartet hat -
also die Huelle cmd.exe, nicht Java. Angezeigt wurden deshalb konstant rund 7 MB und 0 %
CPU, unabhaengig davon, was der Server tatsaechlich belegte. Aufgefallen ist es, weil der
KI-Assistent es gemeldet hat: "Laut Manager braucht der Server 4,75 MB Speicher. Das kann
nicht stimmen."

Der Testaufbau benutzt ``ping -n 30 127.0.0.1`` als Kind: lokal, langlaufend, ohne
verschachtelte Anfuehrungszeichen. Ein Kind, dessen Befehlszeile Quotes braucht, laesst
cmd unter Umstaenden mit Fehler aussteigen - daran ist mein erster Testaufbau
gescheitert, nicht der Code.
"""
import sys
import time

import pytest


@pytest.fixture
def cmd_baum():
    """Ein cmd.exe mit einem langlaufenden Kind - genau die Form eines Serverstarts."""
    import subprocess

    if not sys.platform.startswith("win"):
        pytest.skip("Der Manager startet Server nur unter Windows ueber cmd.")
    psutil = pytest.importorskip("psutil")

    prozess = subprocess.Popen(
        ["cmd", "/d", "/c", "ping -n 30 127.0.0.1"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, encoding="utf-8", errors="replace")
    eigene = [prozess.pid]
    try:
        # Warten, bis das Kind wirklich da ist.
        kinder: list = []
        frist = time.monotonic() + 20
        while time.monotonic() < frist:
            if prozess.poll() is not None:
                pytest.skip(f"cmd endete sofort (code {prozess.poll()}).")
            try:
                kinder = psutil.Process(prozess.pid).children(recursive=True)
            except psutil.NoSuchProcess:
                pytest.skip("cmd war schon weg.")
            if kinder:
                break
            time.sleep(0.2)
        if not kinder:
            pytest.skip("Das Kind ist nicht erschienen.")
        eigene.extend(k.pid for k in kinder)
        yield prozess
    finally:
        for kennung in reversed(eigene):
            try:
                psutil.Process(kennung).kill()
            except Exception:  # noqa: BLE001
                continue
        try:
            prozess.wait(timeout=10)
        except Exception:  # noqa: BLE001
            pass


def test_messung_zaehlt_den_ganzen_baum(cmd_baum):
    """Der Kern: der Verbrauch des KINDES muss in der Messung auftauchen.

    Gemessen wurde vorher nur die Wurzel - und die Wurzel ist bei jedem Server cmd.exe
    mit konstant rund 7 MB.
    """
    import psutil

    from app.services.process_service import _baum_verbrauch

    wurzel = psutil.Process(cmd_baum.pid)
    nur_wurzel = wurzel.memory_info().rss / (1024 * 1024)
    kinder_summe = 0.0
    for kind in wurzel.children(recursive=True):
        try:
            kinder_summe += kind.memory_info().rss / (1024 * 1024)
        except Exception:  # noqa: BLE001
            pass

    _cpu, baum = _baum_verbrauch(cmd_baum.pid)

    assert kinder_summe > 0, "Der Testaufbau hat kein messbares Kind"
    assert baum > nur_wurzel, (
        f"Gemessen wurden {baum:.1f} MB, die Huelle allein belegt {nur_wurzel:.1f} MB - "
        f"das Kind zaehlt nicht mit")
    # Summe aus Wurzel und Kindern, mit Spielraum fuer Schwankungen zwischen den Abfragen.
    assert abs(baum - (nur_wurzel + kinder_summe)) < max(5.0,
                                                         0.3 * (nur_wurzel + kinder_summe))


def test_prozessobjekt_wird_gemerkt(cmd_baum):
    """``cpu_percent(interval=None)`` vergleicht mit dem letzten Aufruf AM SELBEN OBJEKT.

    Wurde das Objekt jedes Mal neu gebaut, war die Antwort immer 0,0 - die CPU-Anzeige
    stand dauerhaft auf null, ohne dass es auffiel.
    """
    from app.services.process_service import _psutil_prozess

    erst = _psutil_prozess(cmd_baum.pid)
    zweit = _psutil_prozess(cmd_baum.pid)

    assert erst is not None
    assert erst is zweit, "Das Prozessobjekt wird nicht gemerkt - cpu_percent bleibt 0"


def test_beendeter_prozess_meldet_null(client):
    """Fuer eine PID, die es nicht gibt, darf nichts geraten werden."""
    from app.services.process_service import _baum_verbrauch

    cpu, mb = _baum_verbrauch(2_000_000_000)

    assert cpu == 0.0
    assert mb == 0.0


def test_eigener_prozess_wird_realistisch_gemessen(client):
    """Ein Python-Prozess belegt deutlich mehr als die wenigen MB einer cmd-Huelle."""
    import os

    from app.services.process_service import _baum_verbrauch

    _cpu, mb = _baum_verbrauch(os.getpid())

    assert mb > 20, f"Gemessen wurden {mb:.1f} MB - das ist keine echte Messung."


def test_messung_ohne_laufenden_server(client):
    """Ein nicht laufender Server meldet sauber Nullen, nicht None."""
    from app.services.process_service import get_process_resource_usage

    werte = get_process_resource_usage(999_999)

    assert werte["running"] is False
    assert werte["memory_mb"] == 0.0
    assert werte["cpu_percent"] == 0.0
    assert werte["uptime_seconds"] is None
