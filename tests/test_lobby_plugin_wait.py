"""Warteschleife der Lobby: retry-Auswertung (JoinCheck) und die Zahlen (WaitLoop).

Warum das Problem ueberhaupt existiert: der Client haengt einen ReadTimeoutHandler(30)
als ERSTEN Handler in seine Netty-Pipeline und bricht eine stumme Verbindung nach 30 s
ab. Ein geweckter Modpack-Server braucht gemessen 35-56 s. Stilles Warten im Handshake
ist damit arithmetisch unmoeglich - also haelt die LOBBY den Spieler (PLAY-Zustand,
Keep-Alive laeuft) und transferiert erst, wenn das Ziel Logins annimmt.

Geprueft wird das ECHTE Java, nicht eine Python-Nachbildung: die Zusagen hier sind
Wire-Zusagen (retry zaehlt nur als echtes Boolean, ein Array vergiftet die ganze
Antwort). Eine Nachbildung wuerde genau den Fehler nicht finden, den sie nachbaut.
JoinCheck, Json und WaitLoop haengen bewusst an keiner Bukkit-API und lassen sich
deshalb allein uebersetzen. Ohne javac wird uebersprungen.
"""
from __future__ import annotations

import os
import shutil
import socket
import subprocess
import threading
from pathlib import Path

import pytest

_SRC = (Path(__file__).resolve().parents[1]
        / "app" / "assets" / "lobby_plugin" / "src" / "net" / "mcsm" / "lobby")

_TOKEN = "T0K3N-geheim"

_HARNESS = r'''
package net.mcsm.lobby;

/** Nur fuer den Test: retry-Auswertung von JoinCheck + die Entscheidungen von WaitLoop. */
public final class WaitHarness {

    public static void main(String[] args) throws Exception {
        if (args[0].equals("loop")) {
            System.out.println("interval=" + WaitLoop.INTERVAL_TICKS);
            System.out.println("max=" + WaitLoop.MAX_RUNS);
            StringBuilder progress = new StringBuilder();
            StringBuilder exhausted = new StringBuilder();
            for (int run = 0; run <= WaitLoop.MAX_RUNS + 2; run++) {
                if (WaitLoop.isProgressRun(run)) {
                    progress.append(run).append(',');
                }
                if (WaitLoop.exhausted(run)) {
                    exhausted.append(run).append(',');
                }
            }
            System.out.println("progress=" + progress);
            System.out.println("exhausted=" + exhausted);
            return;
        }
        int port = Integer.parseInt(args[0]);
        JoinCheck check = new JoinCheck("127.0.0.1", port, "T0K3N-geheim", 2000);
        JoinCheck.Result r = check.check(7, "David", "vanilla", false);
        System.out.println("allowed=" + r.allowed);
        System.out.println("retry=" + r.retry);
        System.out.println("confirm=" + r.confirm);
        System.out.println("reason=" + r.reason);
        // Die alten Konstruktoren und ALLOW duerfen niemanden warten lassen.
        System.out.println("allowConst=" + JoinCheck.ALLOW.retry);
        System.out.println("twoArg=" + new JoinCheck.Result(false, "hart").retry);
        System.out.println("fourArg=" + new JoinCheck.Result(false, "hart", true, "").retry);
    }
}
'''


def _javac() -> str | None:
    """javac aus dem Manager-JDK (gleiche Kette wie der Plugin-Build) oder dem PATH."""
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
def java_cmd(tmp_path_factory) -> list[str]:
    """Die ECHTEN Quellen + Harness uebersetzen. Liefert das java-Kommando-Praefix."""
    javac = _javac()
    if not javac:
        pytest.skip("kein javac verfuegbar")
    work = tmp_path_factory.mktemp("wait")
    pkg = work / "net" / "mcsm" / "lobby"
    pkg.mkdir(parents=True)
    for name in ("JoinCheck.java", "Json.java", "WaitLoop.java"):
        shutil.copy2(_SRC / name, pkg / name)
    (pkg / "WaitHarness.java").write_text(_HARNESS, encoding="utf-8")
    out = work / "out"
    out.mkdir()
    proc = subprocess.run(
        [javac, "-d", str(out), *(str(p) for p in sorted(pkg.glob("*.java")))],
        capture_output=True, text=True, timeout=300)
    assert proc.returncode == 0, f"javac fehlgeschlagen:\n{proc.stderr or proc.stdout}"
    java = str(Path(javac).with_name("java" + Path(javac).suffix))
    return [java, "-cp", str(out), "net.mcsm.lobby.WaitHarness"]


class _Endpoint:
    """Zeilen-JSON-Server auf 127.0.0.1 mit fester Antwort (wie lobby_api_service)."""

    def __init__(self, reply: str | None):
        self.reply = reply
        self.requests: list[str] = []
        self._sock = socket.socket()
        self._sock.bind(("127.0.0.1", 0))
        self._sock.listen(4)
        self.port = self._sock.getsockname()[1]
        threading.Thread(target=self._serve, daemon=True).start()

    def _serve(self) -> None:
        while True:
            try:
                conn, _ = self._sock.accept()
            except OSError:
                return
            with conn:
                conn.settimeout(10.0)
                buffer = bytearray()
                try:
                    while b"\n" not in buffer:
                        chunk = conn.recv(4096)
                        if not chunk:
                            break
                        buffer.extend(chunk)
                    self.requests.append(
                        buffer.split(b"\n", 1)[0].decode("utf-8", "replace"))
                    if self.reply is not None:
                        conn.sendall((self.reply + "\n").encode("utf-8"))
                except OSError:
                    pass

    def close(self) -> None:
        try:
            self._sock.close()
        except OSError:
            pass


@pytest.fixture()
def answer(java_cmd):
    """Liefert eine Funktion: Antwortzeile des Managers -> ausgewertetes Result."""
    made: list[_Endpoint] = []

    def ask(reply: str | None) -> dict:
        ep = _Endpoint(reply)
        made.append(ep)
        proc = subprocess.run(
            [*java_cmd, str(ep.port)], capture_output=True, text=True,
            encoding="utf-8", timeout=120,
            env={**os.environ, "JAVA_TOOL_OPTIONS": ""})
        assert proc.returncode == 0, f"Harness-Fehler:\n{proc.stderr}"
        out: dict = {}
        for line in proc.stdout.splitlines():
            if "=" in line:
                key, value = line.split("=", 1)
                out[key] = value
        return out

    try:
        yield ask
    finally:
        for ep in made:
            ep.close()


# --- retry auf dem Draht --------------------------------------------------------

def test_retry_true_keeps_the_player_in_the_lobby(answer):
    out = answer('{"ok":false,"reason":"ATM10SKY wird gestartet - bleib in der Lobby,'
                 ' du wirst automatisch verbunden.","retry":true}')
    assert out["allowed"] == "false"      # noch nicht transferieren
    assert out["retry"] == "true"         # ... aber warten, nicht absagen
    assert out["confirm"] == "false"
    assert out["reason"].startswith("ATM10SKY wird gestartet")


@pytest.mark.parametrize("field", ['"true"', "1", '"1"', "0.0", "null", '{"x":1}'])
def test_retry_only_counts_as_a_real_boolean(answer, field):
    """Exakt dieselbe Regel wie bei confirm: alles andere heisst "kein Warten"."""
    out = answer('{"ok":false,"reason":"Du bist gebannt.","retry":' + field + "}")
    assert out["allowed"] == "false"
    assert out["retry"] == "false"
    assert out["reason"] == "Du bist gebannt."


def test_retry_false_is_a_hard_no(answer):
    out = answer('{"ok":false,"reason":"Du bist gebannt.","retry":false}')
    assert out["retry"] == "false"


def test_a_backend_without_retry_behaves_exactly_as_today(answer):
    """Altes Backend, kein retry-Feld -> harte Absage, kein Warten."""
    out = answer('{"ok":false,"reason":"Du stehst nicht auf der Whitelist."}')
    assert out["allowed"] == "false"
    assert out["retry"] == "false"
    assert out["confirm"] == "false"


def test_a_confirm_rejection_does_not_become_a_wait(answer):
    """Rueckfrage bleibt Rueckfrage - sonst wartet der Spieler auf eine Antwort,
    die nur sein zweiter Klick geben kann."""
    out = answer('{"ok":false,"reason":"Braucht NeoForge 1.21.1","confirm":true}')
    assert out["confirm"] == "true"
    assert out["retry"] == "false"


def test_an_allowed_answer_never_waits(answer):
    out = answer('{"ok":true,"note":"ATM10SKY ist bereit - verbinde ..."}')
    assert out["allowed"] == "true"
    assert out["retry"] == "false"


def test_old_constructors_and_allow_stay_retry_free(answer):
    """Kein bestehender Aufrufer darf durch das neue Feld ins Warten kippen."""
    out = answer('{"ok":true}')
    assert out["allowConst"] == "false"
    assert out["twoArg"] == "false"
    assert out["fourArg"] == "false"


def test_retry_as_an_array_poisons_the_whole_answer(answer):
    """DESHALB ist retry auf dem Draht ein echtes Boolean und NIE ein Array.

    Json.java hat keinen '['-Zweig, faellt bis number() durch, wirft - und parse()
    liefert null. JoinCheck liest das als FAIL-OPEN, also ALLOW. Ein Array wuerde auf
    jeder noch nicht neu gestarteten Lobby damit auch Ban und Whitelist still
    abschalten: der Spieler wird trotz Absage transferiert.
    """
    out = answer('{"ok":false,"reason":"Du bist gebannt.","retry":[true]}')
    assert out["allowed"] == "true"       # genau der Schaden, den das Format verbietet
    assert out["retry"] == "false"
    assert out["reason"] == ""


@pytest.mark.parametrize("reply", [None, "kein json", '{"error":"auth"}', ""])
def test_unusable_answers_still_fail_open_without_waiting(answer, reply):
    """Eine kaputte Pruefung darf niemanden aussperren - und auch nicht endlos warten
    lassen (eine Warteschleife ohne Urteil wuerde nur die Lobby zumuellen)."""
    out = answer(reply)
    assert out["allowed"] == "true"
    assert out["retry"] == "false"


# --- Zahlen der Warteschleife ---------------------------------------------------

@pytest.fixture(scope="module")
def loop(java_cmd) -> dict:
    proc = subprocess.run([*java_cmd, "loop"], capture_output=True, text=True,
                          encoding="utf-8", timeout=120,
                          env={**os.environ, "JAVA_TOOL_OPTIONS": ""})
    assert proc.returncode == 0, proc.stderr
    out: dict = {}
    for line in proc.stdout.splitlines():
        key, value = line.split("=", 1)
        out[key] = value
    return out


def test_poll_interval_is_five_seconds(loop):
    assert loop["interval"] == "100"      # 100 Ticks = 5 s


def test_limit_is_48_runs_which_is_four_minutes(loop):
    """48 x 5 s = 240 s, gut 4x die gemessenen 56 s des langsamsten Modpacks."""
    assert loop["max"] == "48"
    exhausted = [int(x) for x in loop["exhausted"].split(",") if x]
    assert min(exhausted) == 48          # vorher wird nie abgebrochen
    assert 47 not in exhausted


def test_progress_line_every_three_runs(loop):
    """Alle 3 Laeufe = alle 15 s eine Zeile. Im letzten Lauf NICHT: dort kommt die
    Obergrenz-Meldung, und "startet noch ..." davor wuerde ihr widersprechen."""
    progress = [int(x) for x in loop["progress"].split(",") if x]
    assert progress[:4] == [3, 6, 9, 12]
    assert 0 not in progress             # der erste Bescheid kam schon aus guardedTransfer
    assert 1 not in progress
    assert 48 not in progress
    assert max(progress) == 45
