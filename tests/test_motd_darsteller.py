"""Tests fuer die MOTD-Vorschau im Browser.

Der Darsteller laeuft im Browser, deshalb wird er hier mit Node und einem kleinen
DOM-Ersatz ausgefuehrt. Ohne Node werden die Tests uebersprungen - sie pruefen dann
nichts, statt falsche Sicherheit zu geben.

Zwei Zusagen stehen auf dem Spiel:

* **Die Vorschau darf nicht luegen.** Sie ahmt Minecrafts Regeln nach: eine Farbe setzt
  alle Formate zurueck, ``§r`` setzt alles zurueck, ein unbekannter Code bleibt als Text
  stehen (im Spiel wirkt er auch nicht). Wer sich hier auf etwas anderes verlaesst,
  stellt die MOTD falsch ein und sieht es erst in der Serverliste.
* **Aus dem Eingabefeld darf kein HTML werden.** Der Text kommt vom Nutzer und wird
  unmittelbar wieder angezeigt; die Vorschau entsteht deshalb nur aus DOM-Knoten.
"""
import json
import shutil
import subprocess
from pathlib import Path

import pytest

DARSTELLER = Path("app/web/static/js/motd.js").resolve()

TREIBER = r"""
class N {
  constructor(tag){this.tag=tag;this.children=[];this.style={};this.dataset={};
    this._t="";this.className="";
    this.classList={_s:new Set(),add:(c)=>this.classList._s.add(c),
      toggle:(c,on)=>{on?this.classList._s.add(c):this.classList._s.delete(c);},
      contains:(c)=>this.classList._s.has(c)};}
  appendChild(c){this.children.push(c);return c;}
  removeChild(c){this.children=this.children.filter(x=>x!==c);}
  get firstChild(){return this.children[0];}
  set textContent(v){this._t=String(v);this.children=[];}
  get textContent(){return this._t + this.children.map(c=>c.textContent).join("");}
  get html(){
    const kebab=(k)=>k.replace(/[A-Z]/g, (c)=>"-"+c.toLowerCase());
    const st=Object.entries(this.style).map(([k,v])=>`${kebab(k)}:${v}`).join(";");
    const a=st?` style="${esc(st)}"`:"";
    const inner=this._t?esc(this._t):this.children.map(c=>c.html).join("");
    return `<${this.tag}${a}>${inner}</${this.tag}>`;
  }
}
function esc(s){return String(s).replace(/&/g,"&amp;").replace(/</g,"&lt;")
  .replace(/>/g,"&gt;").replace(/"/g,"&quot;");}
global.document={getElementById:()=>null,createElement:t=>new N(t)};
global.window={matchMedia:()=>({matches:true})};
await import(process.argv[2]);
const M = global.window.MotdDarsteller;
import { readFileSync } from "node:fs";
const faelle = JSON.parse(readFileSync(process.argv[3], "utf-8"));
const raus = faelle.map((text) => {
  const ziel = new N("div");
  M.zeichneZeile(ziel, text);
  return { html: ziel.children.map(c=>c.html).join(""),
           text: ziel.textContent,
           ohne: M.ohneCodes(text),
           sichtbar: M.sichtbareLaenge(text) };
});
console.log(JSON.stringify(raus));
"""


def _node():
    return shutil.which("node")


def _zeichne(tmp_path, faelle):
    node = _node()
    if not node:
        pytest.skip("Node ist nicht installiert")
    treiber = tmp_path / "treiber.mjs"
    treiber.write_text(TREIBER, encoding="utf-8")
    eingabe = tmp_path / "faelle.json"
    eingabe.write_text(json.dumps(faelle), encoding="utf-8")
    ergebnis = subprocess.run(
        [node, str(treiber), DARSTELLER.as_uri(), str(eingabe)],
        capture_output=True, text=True, timeout=60,
        # Node gibt UTF-8 aus; ohne diese Angabe liest Python mit der Windows-Codepage
        # und aus dem Paragrafzeichen wird ein Ersatzzeichen.
        encoding="utf-8",
    )
    assert ergebnis.returncode == 0, ergebnis.stderr[-1500:]
    return json.loads(ergebnis.stdout)


PARAGRAF = "§"


# ------------------------------------------------------------ Farben und Formate

def test_farbe_wird_zur_echten_minecraft_farbe(tmp_path):
    """Die Palette ist Minecrafts eigene - eine huebschere waere eine Luege."""
    ergebnis = _zeichne(tmp_path, [PARAGRAF + "cRot", PARAGRAF + "aGruen"])[0:2]

    assert "color:#ff5555" in ergebnis[0]["html"]
    assert ergebnis[0]["text"] == "Rot"
    assert "color:#55ff55" in ergebnis[1]["html"]


def test_formate_werden_gesetzt(tmp_path):
    proben = [PARAGRAF + "lFett", PARAGRAF + "oKursiv", PARAGRAF + "nUnter",
              PARAGRAF + "mDurch"]
    html = [e["html"] for e in _zeichne(tmp_path, proben)]

    assert "font-weight:700" in html[0]
    assert "font-style:italic" in html[1]
    assert "underline" in html[2]
    assert "line-through" in html[3]


def test_farbe_setzt_formate_zurueck(tmp_path):
    """Minecrafts Regel: eine Farbe loescht fett/kursiv. Wer das anders anzeigt,
    bastelt eine MOTD, die im Spiel anders aussieht."""
    ergebnis = _zeichne(tmp_path, [PARAGRAF + "lFett" + PARAGRAF + "cRot"])[0]

    teile = ergebnis["html"].split("</span>")
    assert "font-weight:700" in teile[0]
    assert "font-weight" not in teile[1], teile[1]
    assert "color:#ff5555" in teile[1]


def test_reset_setzt_alles_zurueck(tmp_path):
    ergebnis = _zeichne(tmp_path, [PARAGRAF + "c" + PARAGRAF + "lX" + PARAGRAF + "rY"])[0]

    letzter = ergebnis["html"].split("<span")[-1]
    assert "font-weight" not in letzter
    assert "color:#aaaaaa" in letzter


def test_eigene_farbe_aus_hex(tmp_path):
    """Paper kann §x§r§r§g§g§b§b - die Vorschau muss es genauso lesen."""
    code = PARAGRAF + "x" + "".join(PARAGRAF + c for c in "ff8800")
    ergebnis = _zeichne(tmp_path, [code + "Orange"])[0]

    assert "color:#ff8800" in ergebnis["html"]
    assert ergebnis["text"] == "Orange"


def test_unbekannter_code_bleibt_text(tmp_path):
    """Im Spiel wirkt er auch nicht - also darf ihn die Vorschau nicht verschlucken."""
    ergebnis = _zeichne(tmp_path, [PARAGRAF + "zHallo"])[0]

    assert PARAGRAF in ergebnis["text"]
    assert "zHallo" in ergebnis["text"]


def test_abgeschnittener_code_am_ende(tmp_path):
    ergebnis = _zeichne(tmp_path, ["Text" + PARAGRAF])[0]

    assert ergebnis["text"] == "Text" + PARAGRAF


# ------------------------------------------------------------ Sicherheit

@pytest.mark.parametrize("text", [
    "<script>boese()</script>",
    "<img src=x onerror=alert(1)>",
    PARAGRAF + "c<b>fett?</b>",
    '"><script>x</script>',
    PARAGRAF + "x" + "".join(PARAGRAF + c for c in "ff0000") + "<i>x</i>",
])
def test_aus_der_eingabe_wird_kein_html(tmp_path, text):
    """Der Text steht in einem Formularfeld und wird sofort wieder angezeigt."""
    ergebnis = _zeichne(tmp_path, [text])[0]
    html = ergebnis["html"]

    # Entscheidend ist, dass kein ELEMENT entsteht. Eine Zeichenfolge wie "onerror="
    # darf sehr wohl vorkommen - als escapter Text ist sie genau richtig dargestellt.
    for verboten in ("<script", "<img", "<b>", "<i>", "<svg", "<iframe"):
        assert verboten not in html, f"{verboten} ist ein Element geworden: {html[:200]}"
    # Es darf ausserdem nur span-Elemente mit style geben, nichts sonst.
    assert set(__import__("re").findall(r"<(\w+)", html)) <= {"span"}, html[:200]
    if "<" in text:
        assert "&lt;" in html, "Das Kleinerzeichen wurde nicht escapt"


def test_stil_kann_nicht_aus_dem_attribut_ausbrechen(tmp_path):
    """Die Farbe kommt aus einer festen Tabelle, nie aus der Eingabe - ausser bei §x,
    und das ist auf sechs Hex-Stellen begrenzt."""
    boese = PARAGRAF + "x" + "".join(PARAGRAF + c for c in "ff0000") + "\");}"
    html = _zeichne(tmp_path, [boese])[0]["html"]

    assert "color:#ff0000" in html
    assert "}" not in html.split("style=")[1].split('"')[1]


# ------------------------------------------------------------ Zaehlen

@pytest.mark.parametrize("text,sichtbar", [
    ("Hallo", 5),
    (PARAGRAF + "cHallo", 5),
    (PARAGRAF + "c" + PARAGRAF + "lHallo", 5),
    (PARAGRAF + "x" + "".join(PARAGRAF + c for c in "ff0000") + "Hallo", 5),
    ("", 0),
])
def test_sichtbare_zeichen_zaehlen_ohne_codes(tmp_path, text, sichtbar):
    """Der Zaehler warnt vor dem Abschneiden in der Serverliste - Codes zaehlen dort
    nicht mit, also darf er sie auch nicht mitzaehlen."""
    assert _zeichne(tmp_path, [text])[0]["sichtbar"] == sichtbar
