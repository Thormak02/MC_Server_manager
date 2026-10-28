"""Tests fuer den Markdown-Darsteller der KI-Antworten.

Der Darsteller laeuft im Browser, deshalb wird er hier mit Node und einem kleinen
DOM-Ersatz ausgefuehrt. Ohne Node werden die Tests uebersprungen - sie pruefen dann
nichts, statt falsche Sicherheit zu geben.

Zwei Zusagen stehen auf dem Spiel:

* **Aus Modellausgabe darf kein HTML werden.** Der Darsteller baut nur DOM-Knoten und
  setzt jeden Text ueber textContent; es gibt keine Stelle mit innerHTML.
* **Er darf die Seite nicht einfrieren.** Die erste Fassung lief je Auszeichnung ein
  eigenes Muster in einer Schleife, die den Rest jedes Mal von vorn durchsuchte - bei
  einer langen Zeile quadratisch, bis zu 500-mal. Eine Antwort im normalen Rahmen
  konnte den Tab mehrere Sekunden blockieren, synchron im Haupt-Thread.
"""
import json
import shutil
import subprocess
import textwrap
from pathlib import Path

import pytest

DARSTELLER = Path("app/web/static/js/markdown.js").resolve()

# Ein DOM-Ersatz, der zusaetzlich HTML erzeugt - damit sichtbar wird, was aus dem Text
# tatsaechlich geworden ist.
TREIBER = r"""
class N {
  constructor(tag){this.tag=tag;this.children=[];this.attrs={};this.dataset={};this._t="";this.className="";}
  appendChild(c){this.children.push(c);return c;}
  removeChild(c){this.children=this.children.filter(x=>x!==c);}
  get firstChild(){return this.children[0];}
  get lastChild(){return this.children[this.children.length-1];}
  set textContent(v){this._t=String(v);this.children=[];}
  get textContent(){return this._t + this.children.map(c=>c.textContent).join("");}
  set href(v){this.attrs.href=v;} set target(v){this.attrs.target=v;} set rel(v){this.attrs.rel=v;}
  get html(){
    if(this.tag==="#text") return esc(this._t);
    const a=Object.entries(this.attrs).map(([k,v])=>` ${k}="${esc(v)}"`).join("");
    const inner=this._t?esc(this._t):this.children.map(c=>c.html).join("");
    return `<${this.tag}${a}>${inner}</${this.tag}>`;
  }
}
function esc(s){return String(s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");}
class F extends N { constructor(){super("#frag");} get html(){return this.children.map(c=>c.html).join("");} }
global.document={createElement:t=>new N(t),createTextNode:t=>{const n=new N("#text");n._t=t;return n;},createDocumentFragment:()=>new F()};
global.window={};
await import(process.argv[2]);
const md = global.window.MdDarsteller;
import { readFileSync } from "node:fs";
// Die Eingabe kommt aus einer DATEI, nicht von der Kommandozeile: eine tief
// verschachtelte Liste hat schnell 40 KB, und Windows nimmt nicht beliebig lange
// Befehlszeilen (CreateProcess bricht mit FileNotFoundError ab).
const eingabe = JSON.parse(readFileSync(process.argv[3], "utf-8"));
const ergebnis = [];
for (const text of eingabe) {
  const t0 = process.hrtime.bigint();
  const frag = md.rendern(text);
  const ms = Number(process.hrtime.bigint() - t0) / 1e6;
  ergebnis.push({ ms, html: frag.html });
}
console.log(JSON.stringify(ergebnis));
"""


def _node_oder_skip():
    pfad = shutil.which("node")
    if not pfad:
        pytest.skip("Node ist nicht vorhanden - der Darsteller laeuft nur im Browser.")
    return pfad


def _rendern(tmp_path, texte: list[str]) -> list[dict]:
    node = _node_oder_skip()
    treiber = tmp_path / "treiber.mjs"
    treiber.write_text(textwrap.dedent(TREIBER), encoding="utf-8")
    kopie = tmp_path / "markdown.js"
    kopie.write_bytes(DARSTELLER.read_bytes())

    eingabe = tmp_path / "eingabe.json"
    eingabe.write_text(json.dumps(texte), encoding="utf-8")

    lauf = subprocess.run(
        [node, str(treiber), kopie.as_uri(), str(eingabe)],
        capture_output=True, text=True, timeout=120)
    if lauf.returncode != 0:
        pytest.fail(f"Node ist gescheitert:\n{lauf.stderr[:2000]}")
    return json.loads(lauf.stdout.strip().splitlines()[-1])


# --------------------------------------------------- Kein HTML aus der Antwort

# Elementanfaenge, die im Ergebnis nicht stehen duerfen. Entscheidend ist das UNescapte
# "<": steht im Ergebnis "&lt;script&gt;", ist der Text als Text dargestellt - genau so
# soll es sein. Mein erster Versuch suchte nach Zeichenfolgen wie "onerror=alert" und
# schlug deshalb bei korrekt escaptem Text fehl.
VERBOTENE_ELEMENTE = ("<script", "<img", "<iframe", "<object", "<embed", "<svg",
                      "<b>", "<style", "<link", "<meta")

# Die EINZIGEN Attribute, die der Darsteller je setzt. Alles andere waere ein Zeichen,
# dass Text zu Markup geworden ist.
#
# Nicht nach "onerror=" suchen: im escapten Text darf das vorkommen (aus
# "<img src=x onerror=alert(1)>" wird genau dieser Text) - das war mein Denkfehler beim
# ersten Versuch. Weil der Serialisierer jedes " im Text zu &quot; macht, stammt jedes
# vorkommende =" aus einem echten Attribut; darauf laesst sich pruefen.
ERLAUBTE_ATTRIBUTE = {"href", "target", "rel"}
VERBOTENE_ATTRIBUTE = ('href="javascript:', 'href="data:', 'href="vbscript:')


@pytest.mark.parametrize("text", [
    "<script>boese()</script>",
    "<img src=x onerror=alert(1)>",
    "<b>fett?</b>",
    "```\n<script>x</script>\n```",
    "[Klick](javascript:alert(1))",
    "[Klick](data:text/html,<script>x</script>)",
    "[Klick](vbscript:msgbox)",
    "[Klick](JAVASCRIPT:alert(1))",
    "[Klick](  javascript:alert(1))",
    "| <script>x</script> | b |\n|---|---|\n| c | d |",
    "# <script>x</script>",
    "> <script>x</script>",
    "- <img src=x onerror=alert(1)>",
    "<a href=\"javascript:alert(1)\">x</a>",
])
def test_aus_modellausgabe_wird_kein_html(tmp_path, text):
    """Der Text kommt von einem Sprachmodell und ist damit nicht vertrauenswuerdig.

    Geprueft wird, dass daraus kein ELEMENT und kein gefaehrliches ATTRIBUT entsteht -
    als escaptes Zeichen im Text darf alles vorkommen.
    """
    html = _rendern(tmp_path, [text])[0]["html"]

    for verboten in VERBOTENE_ELEMENTE:
        assert verboten not in html, f"{verboten} ist ein Element geworden: {html[:300]}"
    for verboten in VERBOTENE_ATTRIBUTE:
        assert verboten not in html, f"{verboten} steht als Attribut drin: {html[:300]}"

    import re as _re

    gesetzt = set(_re.findall(r'\s([A-Za-z][\w-]*)="', html))
    assert gesetzt <= ERLAUBTE_ATTRIBUTE, (
        f"unerwartete Attribute {gesetzt - ERLAUBTE_ATTRIBUTE}: {html[:300]}")


def test_gefaehrliche_zeichen_bleiben_als_text_sichtbar(tmp_path):
    """Nicht nur unschaedlich machen - der Nutzer soll sehen, was die KI geschrieben hat."""
    html = _rendern(tmp_path, ["Vorsicht: <script>boese()</script> im Text"])[0]["html"]

    assert "&lt;script&gt;" in html
    assert "boese()" in html


def test_link_bekommt_noopener(tmp_path):
    """target=_blank ohne rel=noopener gibt der Zielseite Zugriff auf das Fenster."""
    ergebnis = _rendern(tmp_path, ["Ein [Link](https://example.com/x) dazu."])[0]

    assert 'href="https://example.com/x"' in ergebnis["html"]
    assert 'rel="noopener noreferrer"' in ergebnis["html"]


def test_struktur_bleibt_erhalten(tmp_path):
    """Die Darstellung soll ja etwas leisten, nicht nur sicher sein."""
    quelle = ("# Titel\n\nText mit **fett**, *kursiv* und `code`.\n\n"
              "- Punkt eins\n- Punkt zwei\n  - eingerueckt\n\n"
              "1. erstens\n2. zweitens\n\n> Zitat\n\n"
              "| Name | Wert |\n|---|---|\n| a | 1 |\n")
    html = _rendern(tmp_path, [quelle])[0]["html"]

    for teil in ("<h3", "<strong>fett</strong>", "<em>kursiv</em>", "<code>code</code>",
                 "<ul", "<ol", "<blockquote", "<table", "<th", "<td"):
        assert teil in html, f"{teil} fehlt in der Darstellung"


# ------------------------------------------------------- Die Seite darf nicht stehen

@pytest.mark.parametrize("name,text", [
    ("viele offene Klammern", "[" * 4000),
    ("Klammern mit Text", "[abc" * 1000),
    ("viele Sternchen", "*" * 6000),
    ("fett ohne Ende", "**abc" * 1000),
    ("Backticks ohne Ende", "`abc" * 1000),
    ("nur Leerraum", " " * 5000),
    ("Leerraum mit Strich", " " * 2500 + "-" + " " * 2500),
    ("Pipes und Leerraum", "| " * 1200 + "\n" + " |" * 1200),
    ("gemischt lang", "[a](http://x) **b** `c` *d* " * 400),
])
def test_boesartige_eingabe_friert_nichts_ein(tmp_path, name, text):
    """Die erste Fassung brauchte fuer solche Eingaben Sekunden - im Haupt-Thread des
    Browsers, also ohne Eingabe und ohne Abbruch. Jetzt laeuft ein einziger Durchgang."""
    ergebnis = _rendern(tmp_path, [text])[0]

    assert ergebnis["ms"] < 500, (
        f"{name}: {ergebnis['ms']:.0f} ms fuer {len(text)} Zeichen")


def test_antwort_in_normaler_groesse_ist_sofort_da(tmp_path):
    """Eine Antwort darf hoechstens 12.000 Zeichen haben (MAX_ERGEBNIS)."""
    quelle = ("## Abschnitt\nText mit **fett** und `code`.\n- Punkt\n- Punkt\n") * 180
    ergebnis = _rendern(tmp_path, [quelle])[0]

    assert len(quelle) > 10_000
    assert ergebnis["ms"] < 200, f"{ergebnis['ms']:.0f} ms"


def test_tief_verschachtelte_liste_bleibt_beherrschbar(tmp_path):
    """Verschachtelung laeuft rekursiv - eine sehr tiefe Liste darf den Stapel nicht
    sprengen."""
    quelle = "\n".join("  " * i + "- Ebene " + str(i) for i in range(200))
    ergebnis = _rendern(tmp_path, [quelle])[0]

    assert ergebnis["ms"] < 500
    assert "Ebene 0" in ergebnis["html"]
