"""Proben fuer skripte/torreihe_messen.py - das Messwerkzeug fuer die Torreihe.

Bis zum 23.11.2026 stand ``palettenname`` in keiner einzigen Probe (repoweite Suche nach
"torreihe_messen" in tests/: kein Treffer). Seit dem Teilen ist sie ein Einzeiler auf
``world_bake_service.palette_entry`` - und ein unbewachter Einzeiler ist genau die Stelle,
an der beim naechsten Umbau still der falsche Name herauskommt.

Das Skript liegt nicht in einem Paket (kein ``skripte/__init__.py``), darum wird es ueber
seinen Pfad geladen.
"""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

_PFAD = Path(__file__).resolve().parents[1] / "skripte" / "torreihe_messen.py"


def _torreihe():
    if "torreihe_messen" in sys.modules:
        return sys.modules["torreihe_messen"]
    spec = importlib.util.spec_from_file_location("torreihe_messen", _PFAD)
    modul = importlib.util.module_from_spec(spec)
    sys.modules["torreihe_messen"] = modul
    spec.loader.exec_module(modul)
    return modul


def test_palettenname_kennt_alle_vier_formen():
    """Die Erwartungen sind Namensliterale - nicht aus palette_entry geholt."""
    tm = _torreihe()
    assert tm.palettenname("minecraft:bedrock") == "minecraft:bedrock"
    assert tm.palettenname({"": "minecraft:stone"}) == "minecraft:stone"
    assert tm.palettenname({"Name": "minecraft:stone"}) == "minecraft:stone"
    assert tm.palettenname({"id": "minecraft:kelp"}) == "minecraft:kelp"


def test_palettenname_faellt_auf_luft_zurueck():
    """Eine unlesbare Gestalt wird Luft - beim MESSEN ist das richtig: eine Spalte, in der
    etwas Unbekanntes steht, soll nicht als tragender Boden durchgehen."""
    tm = _torreihe()
    assert tm.palettenname({"foo": 1}) == "minecraft:air"
    assert tm.palettenname(7) == "minecraft:air"


def test_palettenname_nimmt_die_geteilte_regel():
    """Derselbe Leser wie im Bake-Dienst - sonst laufen sie wieder auseinander.

    GEPRUEFT WIRD DER ANSCHLUSS, nicht der Wert [berichtigt 23.11.2026]: Ein Vergleich
    ``palettenname(x) == palette_entry(x)[0]`` holt seine Erwartung aus dem Gepruefen.
    Nimmt jemand das Teilen zurueck und schreibt den alten vierzweigigen Rumpf wieder
    hierher, liefern beide Fassungen fuer alle fuenf Gestalten dasselbe - und die Probe
    bliebe gruen, waehrend die eine Leseregel wieder zwei sind. Genau das ist der Zustand,
    den diese Datei verhindern soll.
    """
    import ast

    quelle = _PFAD.read_text(encoding="utf-8")
    assert "from app.services.world_bake_service import" in quelle, \
        "das Skript muss den Leser aus dem Dienst holen"

    # DER RUMPF MUSS DER EINZEILER SEIN, und nicht die Zeile irgendwo ENTHALTEN
    # [berichtigt 23.11.2026]: Wer dem Dienst nicht mehr traut, schreibt den alten
    # vierzweigigen Rumpf DAVOR und laesst den Aufruf als Rueckfall darunter stehen. Die
    # Zeile steht dann noch da, ist aber unerreichbar - und genau dann laufen wieder zwei
    # Leseregeln im Haus, waehrend die Probe gruen bleibt.
    baum = ast.parse(quelle)
    funktionen = [k for k in ast.walk(baum)
                  if isinstance(k, ast.FunctionDef) and k.name == "palettenname"]
    assert len(funktionen) == 1, "palettenname gibt es nicht genau einmal"
    saetze = [s for s in funktionen[0].body
              if not (isinstance(s, ast.Expr) and isinstance(s.value, ast.Constant)
                      and isinstance(s.value.value, str))]
    assert len(saetze) == 1, ("palettenname muss aus EINEM Satz bestehen, gefunden: "
                              + str([ast.unparse(s) for s in saetze]))
    assert ast.unparse(saetze[0]) == "return palette_entry(eintrag)[0]", \
        ast.unparse(saetze[0])
    # Dass die geteilte Regel auch das Richtige liefert, haelt
    # tests/test_world_bake.py::test_palette_entry_vier_formen mit gemessenen Zahlen.
    tm = _torreihe()
    assert tm.palettenname({"id": "minecraft:kelp"}) == "minecraft:kelp"
