"""Der Feldkatalog darf nicht still veralten.

Der Freimarken-Bereich zeigt fachlich geschnittene Seiten - Wirtschaft, Waehrung, Raenge
- und nicht die config.yml von oben nach unten. Das ist die schoenere Oberflaeche und die
gefaehrlichere: Baut das Plugin einen Abschnitt dazu, erscheint er nirgends, und niemand
merkt es. Die Einstellung ist dann da, wirkt im Spiel, und das Dashboard behauptet durch
Schweigen, es gaebe sie nicht.

Dagegen stehen die Proben hier. Sie lesen die AUSGELIEFERTE config.yml des Plugins - die,
die mit dem Plugin in jeden neuen Serverordner wandert - und halten den Katalog dagegen.
Ein neuer Abschnitt macht sie rot, mit seinem Namen in der Meldung.

Geprueft wird gegen die Datei und nicht gegen den Java-Quelltext: Die Datei ist, was das
Plugin ausliefert und was der Manager liest. Was in einer Klasse steht, aber nicht in der
ausgelieferten Datei, erreicht keinen Server.
"""
from __future__ import annotations

from pathlib import Path

import pytest

from app.services import freimarken_service as fs
from app.services import yaml_assistant_service as ya


# Die ausgelieferte Einstellungsdatei des Plugins, von dieser Datei aus gefunden.
VORLAGE = (Path(__file__).resolve().parents[1]
           / "freimarken" / "paper" / "src" / "main" / "resources" / "config.yml")


@pytest.fixture(scope="module")
def felder() -> dict:
    if not VORLAGE.is_file():
        pytest.skip("Das Plugin liegt nicht in diesem Baum: " + str(VORLAGE))
    with open(VORLAGE, "r", encoding="utf-8", newline="") as offen:
        return ya.felder_nach_pfad(offen.read())


@pytest.fixture(scope="module")
def abschnitte(felder: dict) -> set[str]:
    return {eintrag["pfad"][0] for eintrag in felder.values()}


def test_jeder_abschnitt_ist_verortet_oder_begruendet_ausgenommen(abschnitte):
    """Kein Abschnitt faellt stumm durch - er steht auf einer Seite oder mit Grund daneben."""
    verortet = {str(block) for seite in fs.seiten()
                for block in (seite.get("bloecke") or [])}
    ausgenommen = set((fs.katalog().get("nicht-im-dashboard") or {}).keys())
    vergessen = sorted(abschnitte - verortet - ausgenommen)
    assert not vergessen, (
        "Diese Abschnitte der config.yml stehen in keiner Seite des Feldkatalogs und "
        "auch nicht unter nicht-im-dashboard: " + ", ".join(vergessen)
        + ". Trage sie in app/assets/freimarken_felder.yml ein - entweder bei einer Seite "
        "oder, mit Begruendung, unter nicht-im-dashboard.")


def test_kein_abschnitt_im_katalog_den_es_nicht_gibt(abschnitte):
    """Die Gegenrichtung: ein Tippfehler im Katalog ist eine Seite, die leer bleibt."""
    verortet = {str(block) for seite in fs.seiten()
                for block in (seite.get("bloecke") or [])}
    ausgenommen = set((fs.katalog().get("nicht-im-dashboard") or {}).keys())
    erfunden = sorted((verortet | ausgenommen) - abschnitte)
    assert not erfunden, (
        "Der Feldkatalog nennt Abschnitte, die es in der ausgelieferten config.yml nicht "
        "gibt: " + ", ".join(erfunden))


def test_jeder_sprungschluessel_ist_einem_wert_zugeordnet(felder):
    """Sonst schreibt das Dashboard eine Bestaetigung, die niemand liest.

    Das Plugin verwirft einen Geldwert ueber Faktor 2, solange
    wirtschaft.sprung-bestaetigt.<name> nicht "alt -> neu" traegt. Welcher Name zu welchem
    Wert gehoert, steht im Plugin am Aufruf und ist aus dem Pfad nicht ableitbar
    (wechsel.mark-je-goldmark heisst dort wechsel-hin). Kommt im Plugin ein Sprungschutz
    dazu, erscheint hier ein Name ohne Zuordnung - und diese Probe sagt es.
    """
    vorsatz = fs.PFAD_BESTAETIGT + "."
    in_der_datei = {pfad[len(vorsatz):] for pfad in felder if pfad.startswith(vorsatz)}
    zugeordnet = set((fs.katalog().get("sprung") or {}).values())
    ohne = sorted(in_der_datei - zugeordnet)
    assert not ohne, (
        "Diese Schluessel unter " + fs.PFAD_BESTAETIGT + " sind im Feldkatalog keinem "
        "Wertpfad zugeordnet: " + ", ".join(ohne)
        + ". Ohne Zuordnung kann das Dashboard eine Aenderung ueber Faktor 2 nicht "
        "bestaetigen, und das Plugin verwirft sie beim naechsten Start.")


def test_keine_zuordnung_auf_einen_schluessel_den_es_nicht_gibt(felder):
    vorsatz = fs.PFAD_BESTAETIGT + "."
    in_der_datei = {pfad[len(vorsatz):] for pfad in felder if pfad.startswith(vorsatz)}
    erfunden = sorted(name for name in (fs.katalog().get("sprung") or {}).values()
                      if name not in in_der_datei)
    assert not erfunden, (
        "Der Katalog ordnet Bestaetigungsschluessel zu, die es unter "
        + fs.PFAD_BESTAETIGT + " nicht gibt: " + ", ".join(erfunden))


def test_jeder_genannte_wertpfad_kommt_in_der_datei_vor(felder):
    """Gilt fuer die Sprung-Zuordnung; Muster mit * sind davon ausgenommen."""
    fehlend = sorted(pfad for pfad in (fs.katalog().get("sprung") or {})
                     if pfad not in felder)
    assert not fehlend, (
        "Der Katalog nennt Wertpfade, die in der ausgelieferten config.yml nicht "
        "vorkommen: " + ", ".join(fehlend))


@pytest.mark.parametrize("abteilung", ["gesperrt", "bestandsschutz"])
def test_jedes_muster_trifft_mindestens_ein_feld(felder, abteilung):
    """Ein Muster, das nichts trifft, ist eine Regel, die nie gilt.

    Es sieht aus wie Schutz und ist keiner - genau die Art Zeile, die jahrelang
    unbemerkt danebenliegt, weil ein Pfad umbenannt wurde.
    """
    leer = []
    for muster in (fs.katalog().get(abteilung) or {}):
        if not any(fs.passt_muster(str(muster), pfad) for pfad in felder):
            leer.append(str(muster))
    assert not leer, (
        "Diese Muster unter '" + abteilung + "' treffen kein einziges Feld der "
        "ausgelieferten config.yml: " + ", ".join(leer))


def test_jede_seite_hat_schluessel_name_und_hinweis():
    for seite in fs.seiten():
        for pflicht in ("schluessel", "name", "hinweis"):
            assert seite.get(pflicht), (
                "Seite " + repr(seite.get("schluessel")) + " ohne " + pflicht)


def test_seitenschluessel_sind_eindeutig():
    schluessel = [seite.get("schluessel") for seite in fs.seiten()]
    doppelt = sorted({s for s in schluessel if schluessel.count(s) > 1})
    assert not doppelt, "Doppelte Seitenschluessel: " + ", ".join(doppelt)


def test_ausgenommene_abschnitte_haben_eine_begruendung():
    """Eine gesperrte Einstellung mit Begruendung ist freundlicher als eine fehlende -
    aber nur, wenn die Begruendung auch dasteht."""
    for abschnitt, grund in (fs.katalog().get("nicht-im-dashboard") or {}).items():
        assert str(grund).strip(), (
            "Abschnitt " + str(abschnitt) + " ist ausgenommen, ohne dass dabeisteht warum.")


# ------------------------------------------------------- Der Musterabgleich selbst

@pytest.mark.parametrize("muster,pfad,erwartet", [
    ("welten.*.art", "welten.marktstadt.art", True),
    ("welten.*.art", "welten.altfurt.art", True),
    # Der Stern steht fuer GENAU EINEN Abschnitt. Liefe er ueber Punkte hinweg, traefe
    # das Muster auch tiefer liegende Felder - und spaerrte Dinge, die niemand sperren
    # wollte.
    ("welten.*.art", "welten.marktstadt.tor.art", False),
    ("welten.*.art", "welten.art", False),
    ("laeden.miete-je-tag", "laeden.miete-je-tag", True),
    ("laeden.miete-je-tag", "laeden.miete-je-tag.extra", False),
    ("muenzhaus.katalog.*.preis", "muenzhaus.katalog.kuerbishut.preis", True),
    ("muenzhaus.katalog.*.preis", "muenzhaus.katalog.kuerbishut.gegenstaende", False),
])
def test_musterabgleich(muster, pfad, erwartet):
    assert fs.passt_muster(muster, pfad) is erwartet
