"""JoinCheck.java (Lobby-Plugin) gegen einen echten lokalen Endpoint.

Warum der Umweg ueber javac: die Zusagen dieses Moduls sind Wire-Zusagen - dass der
Brand durch quote() geht, dass eine Antwort ohne "ok" auf ALLOW faellt, dass confirm
nur bei einem echten Boolean zaehlt. Das laesst sich nur am kompilierten Java pruefen;
eine Python-Nachbildung wuerde genau den Fehler nicht finden, den sie nachbaut.

Der Test kompiliert deshalb die ECHTEN Quellen (JoinCheck.java + Json.java, beide
ohne Bukkit-Abhaengigkeit) zusammen mit einem kleinen Harness und laesst sie gegen
einen Loopback-Server laufen. Kein Netz, keine Bukkit-Runtime. Ohne javac wird
uebersprungen.
"""
from __future__ import annotations

import json
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

# Ein Brand, wie ihn ein boesartiger Client schicken kann: Anfuehrungszeichen,
# Zeilenumbruch, Backslash. Ohne quote() zerfaellt die Anfrage damit in zwei Zeilen -
# die Antwort haette kein "ok"-Feld und die Pruefung waere still auf ALLOW.
_EVIL_BRAND = 'fab"ric\n{"ok":false}\\x'

_HARNESS = r'''
package net.mcsm.lobby;

/** Nur fuer den Test: ruft JoinCheck auf und schreibt das Ergebnis zeilenweise nach stdout. */
public final class JoinCheckHarness {

    /** Muss zeichengleich zu _EVIL_BRAND im Python-Test sein. */
    static final String EVIL_BRAND = "fab\"ric\n{\"ok\":false}\\x";

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        String mode = args[1];
        JoinCheck check = new JoinCheck("127.0.0.1", port, "T0K3N-geheim", 2000);
        if (mode.equals("fit")) {
            String[] parts = args[2].isEmpty() ? new String[0] : args[2].split(",");
            int[] ids = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                ids[i] = Integer.parseInt(parts[i]);
            }
            java.util.Map<String, String> fits = check.fitCheck(ids, args[3], args[4]);
            for (java.util.Map.Entry<String, String> e : fits.entrySet()) {
                System.out.println("fit=" + e.getKey() + "=" + e.getValue());
            }
            System.out.println("count=" + fits.size());
            return;
        }
        JoinCheck.Result r;
        if (mode.equals("legacy")) {
            r = check.check(Integer.parseInt(args[2]), args[3]);
        } else if (mode.equals("evil")) {
            r = check.check(Integer.parseInt(args[2]), args[3], EVIL_BRAND, false);
        } else {
            r = check.check(Integer.parseInt(args[2]), args[3], args[4],
                            Boolean.parseBoolean(args[5]));
        }
        System.out.println("allowed=" + r.allowed);
        System.out.println("confirm=" + r.confirm);
        System.out.println("note=" + r.note);
        System.out.println("reason=" + r.reason);
    }
}
'''


def _javac() -> str | None:
    found = shutil.which("javac")
    return found or None


pytestmark = pytest.mark.skipif(_javac() is None, reason="Kein javac im PATH")


@pytest.fixture(scope="module")
def java_cmd(tmp_path_factory) -> list[str]:
    """Die echten Quellen + Harness kompilieren. Liefert das java-Kommando-Praefix."""
    javac = _javac()
    work = tmp_path_factory.mktemp("joincheck")
    pkg = work / "net" / "mcsm" / "lobby"
    pkg.mkdir(parents=True)
    for name in ("JoinCheck.java", "Json.java"):
        shutil.copy2(_SRC / name, pkg / name)
    (pkg / "JoinCheckHarness.java").write_text(_HARNESS, encoding="utf-8")
    out = work / "out"
    out.mkdir()
    proc = subprocess.run(
        [javac, "-d", str(out), *(str(p) for p in sorted(pkg.glob("*.java")))],
        capture_output=True, text=True, timeout=300,
    )
    assert proc.returncode == 0, f"javac fehlgeschlagen:\n{proc.stderr or proc.stdout}"
    java = str(Path(javac).with_name("java" + Path(javac).suffix))
    return [java, "-cp", str(out), "net.mcsm.lobby.JoinCheckHarness"]


class _Endpoint:
    """Zeilen-JSON-Server auf 127.0.0.1, wie lobby_api_service - nur mit fester Antwort.

    ``reply=None`` schliesst die Verbindung ohne Antwort (das ist der EOF-Fall).
    """

    def __init__(self, reply: str | None):
        self.reply = reply
        self.requests: list[str] = []
        self._sock = socket.socket()
        self._sock.bind(("127.0.0.1", 0))
        self._sock.listen(4)
        self.port = self._sock.getsockname()[1]
        self._thread = threading.Thread(target=self._serve, daemon=True)
        self._thread.start()

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
                    # Mehr als eine Zeile ist bereits der Fehlerfall - mitschneiden,
                    # damit der Test ihn benennen kann.
                    rest = buffer.split(b"\n", 1)[1] if b"\n" in buffer else b""
                    if rest.strip():
                        self.requests.append(rest.decode("utf-8", "replace"))
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
def endpoint():
    made: list[_Endpoint] = []

    def make(reply: str | None) -> _Endpoint:
        ep = _Endpoint(reply)
        made.append(ep)
        return ep

    try:
        yield make
    finally:
        for ep in made:
            ep.close()


def _run(java_cmd: list[str], ep: _Endpoint, *args: str) -> dict:
    proc = subprocess.run(
        [*java_cmd, str(ep.port), *args],
        capture_output=True, text=True, encoding="utf-8", timeout=120,
        env={**os.environ, "JAVA_TOOL_OPTIONS": ""},
    )
    assert proc.returncode == 0, f"Harness-Fehler:\n{proc.stderr}"
    out: dict = {"fits": {}}
    for line in proc.stdout.splitlines():
        if line.startswith("fit="):
            _, sid, short = line.split("=", 2)
            out["fits"][sid] = short
        elif "=" in line:
            key, value = line.split("=", 1)
            out[key] = value
    return out


def _request(ep: _Endpoint) -> dict:
    assert len(ep.requests) == 1, f"Erwartet genau EINE Zeile, bekam: {ep.requests!r}"
    return json.loads(ep.requests[0])


# --- join_check: Anfrageformat -------------------------------------------------

def test_join_request_carries_token_client_and_override(java_cmd, endpoint):
    ep = endpoint('{"ok":true}')
    result = _run(java_cmd, ep, "join", "7", "David", "vanilla", "false")
    req = _request(ep)
    assert req["token"] == _TOKEN
    assert req["op"] == "join_check"
    assert req["server_id"] == 7
    assert req["player"] == "David"
    assert req["client"] == {"brand": "vanilla", "source": "bukkit"}
    assert req["override"] is False
    assert result["allowed"] == "true"


def test_join_without_brand_omits_client_block(java_cmd, endpoint):
    ep = endpoint('{"ok":true}')
    _run(java_cmd, ep, "join", "7", "David", "", "true")
    req = _request(ep)
    assert "client" not in req      # kein Brand -> kein Client-Abgleich
    assert req["override"] is True


def test_legacy_two_arg_check_still_works(java_cmd, endpoint):
    """Die alte Signatur bleibt - Aufrufer ohne Brand duerfen sich nicht aendern muessen."""
    ep = endpoint('{"ok":false,"reason":"David ist voll (20/20)."}')
    result = _run(java_cmd, ep, "legacy", "7", "David")
    req = _request(ep)
    assert "client" not in req
    assert req["override"] is False
    assert result["allowed"] == "false"
    assert result["confirm"] == "false"
    assert result["reason"] == "David ist voll (20/20)."


def test_evil_brand_stays_one_single_json_line(java_cmd, endpoint):
    """Anfuehrungszeichen/Zeilenumbruch im Brand duerfen das Framing nicht zerlegen."""
    ep = endpoint('{"ok":true}')
    result = _run(java_cmd, ep, "evil", "7", "David")
    req = _request(ep)                     # genau EINE Zeile, sonst knallt es hier
    assert req["client"]["brand"] == _EVIL_BRAND
    assert result["allowed"] == "true"     # Antwort wurde trotzdem verstanden


# --- join_check: Antwortformen -------------------------------------------------

def test_confirm_answer_is_a_question_not_a_wall(java_cmd, endpoint):
    ep = endpoint('{"ok":false,"reason":"Braucht NeoForge 1.21.1","confirm":true}')
    result = _run(java_cmd, ep, "join", "7", "David", "vanilla", "false")
    assert result["allowed"] == "false"
    assert result["confirm"] == "true"
    assert result["reason"] == "Braucht NeoForge 1.21.1"


def test_confirm_only_counts_as_real_boolean(java_cmd, endpoint):
    """confirm als String ist KEIN confirm - im Zweifel hart, nicht ueberstimmbar."""
    ep = endpoint('{"ok":false,"reason":"Du bist gebannt.","confirm":"true"}')
    result = _run(java_cmd, ep, "join", "7", "David", "vanilla", "false")
    assert result["allowed"] == "false"
    assert result["confirm"] == "false"


def test_hard_rejection_without_confirm_field(java_cmd, endpoint):
    ep = endpoint('{"ok":false,"reason":"Du stehst nicht auf der Whitelist."}')
    result = _run(java_cmd, ep, "join", "7", "David", "vanilla", "false")
    assert result["allowed"] == "false"
    assert result["confirm"] == "false"
    assert result["reason"] == "Du stehst nicht auf der Whitelist."


def test_note_is_carried_but_allows(java_cmd, endpoint):
    ep = endpoint('{"ok":true,"note":"Der Server wacht gerade auf."}')
    result = _run(java_cmd, ep, "join", "7", "David", "vanilla", "false")
    assert result["allowed"] == "true"
    assert result["note"] == "Der Server wacht gerade auf."


def test_note_that_is_not_a_string_is_ignored(java_cmd, endpoint):
    ep = endpoint('{"ok":true,"note":42}')
    result = _run(java_cmd, ep, "join", "7", "David", "vanilla", "false")
    assert result["allowed"] == "true"
    assert result["note"] == ""


# --- FAIL-OPEN -----------------------------------------------------------------

@pytest.mark.parametrize("reply", [
    None,                      # Verbindung ohne Antwort geschlossen
    "kein json",               # Muell
    '{"error":"auth"}',        # kein Urteil (falscher Token)
    '{"ok":"false"}',          # "ok" kein Boolean
    "",                        # leere Zeile
])
def test_anything_unusable_allows(java_cmd, endpoint, reply):
    ep = endpoint(reply)
    result = _run(java_cmd, ep, "join", "7", "David", "vanilla", "false")
    assert result["allowed"] == "true"
    assert result["confirm"] == "false"
    assert result["reason"] == ""


def test_unreachable_manager_allows(java_cmd, endpoint):
    """Endpoint tot -> Connect scheitert -> trotzdem durchlassen."""
    ep = endpoint('{"ok":false,"reason":"egal"}')
    ep.close()
    result = _run(java_cmd, ep, "join", "7", "David", "vanilla", "false")
    assert result["allowed"] == "true"


# --- fit_check -----------------------------------------------------------------

def test_fit_check_request_and_markers(java_cmd, endpoint):
    ep = endpoint('{"ok":true,"fits":{"2":{"level":"confirm",'
                  '"short":"Braucht NeoForge 1.21.1"}}}')
    result = _run(java_cmd, ep, "fit", "2,5,7", "David", "vanilla")
    req = _request(ep)
    assert req["op"] == "fit_check"
    assert req["server_ids"] == [2, 5, 7]
    assert req["player"] == "David"
    assert req["client"] == {"brand": "vanilla", "source": "bukkit"}
    # Nur der Treffer taucht auf - ein fehlender Eintrag heisst "passt".
    assert result["fits"] == {"2": "Braucht NeoForge 1.21.1"}
    assert result["count"] == "1"


def test_fit_check_ignores_entries_without_short(java_cmd, endpoint):
    ep = endpoint('{"ok":true,"fits":{"2":{"level":"confirm"},'
                  '"5":{"level":"confirm","short":""},'
                  '"7":{"level":"confirm","short":"2 Mods fehlen vermutlich"}}}')
    result = _run(java_cmd, ep, "fit", "2,5,7", "David", "vanilla")
    assert result["fits"] == {"7": "2 Mods fehlen vermutlich"}


@pytest.mark.parametrize("reply", [
    "kaputt",
    '{"ok":true}',                 # altes Backend kennt fit_check nicht
    '{"error":"unknown_op"}',
    None,
])
def test_fit_check_is_empty_on_any_error(java_cmd, endpoint, reply):
    ep = endpoint(reply)
    result = _run(java_cmd, ep, "fit", "2,5,7", "David", "vanilla")
    assert result["count"] == "0"


def test_fit_check_without_brand_asks_nobody(java_cmd, endpoint):
    """Ohne Brand kann der Manager nichts sagen - die Runde uebers Netz entfaellt."""
    ep = endpoint('{"ok":true,"fits":{"2":{"short":"Braucht NeoForge"}}}')
    result = _run(java_cmd, ep, "fit", "2,5,7", "David", "")
    assert result["count"] == "0"
    assert ep.requests == []


def test_fit_check_without_ids_asks_nobody(java_cmd, endpoint):
    ep = endpoint('{"ok":true,"fits":{}}')
    result = _run(java_cmd, ep, "fit", "", "David", "vanilla")
    assert result["count"] == "0"
    assert ep.requests == []
