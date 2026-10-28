"""Der YAML-Assistent - und der Zustand der Dateiliste, der bisher verloren ging.

Der wichtigste Test in dieser Datei ist der langweiligste: wer nichts aendert und
speichert, muss eine byteweise identische Datei zurueckbekommen. Daran haengt alles
andere, denn eine spigot.yml besteht zur Haelfte aus Kommentaren.
"""

import re

import pytest

from app.services import yaml_assistant_service as ya
from app.services.file_service import (
    assistent_hinweis,
    build_content_from_assistant,
    get_assistant_payload,
)


SPIGOT = """# This is the main configuration file for Spigot.
settings:
  bungeecord: false
  sample-count: 12
  save-user-cache-on-stop-only: false
  netty-threads: 4   # nicht erhoehen ohne Grund
  attribute-maps: {}
  restart-script: ./start.sh
  restart-message: 'Server is restarting'
  timeout-time: 60
messages:
  whitelist: You are not whitelisted on this server!
  unknown-command: Unknown command. Type "/help" for help.
world-settings:
  default:
    verbose: false
    view-distance: default
    merge-radius:
      item: 2.5
      exp: 3.0
    entity-activation-range:
      animals: 32
      monsters: 32
    hopper-amount: 1
    growth:
      cactus-modifier: 100
"""


def _formular(payload, **aenderungen):
    """Baut die Formulardaten so, wie der Browser sie schicken wuerde."""
    daten = {feld["key"]: feld["value"] for feld in payload["fields"]}
    daten["__assistant_yaml_meta"] = payload["assistant_yaml_meta"]
    for titel, wert in aenderungen.items():
        schluessel = _schluessel(payload, titel)
        daten[schluessel] = wert
    return daten


def _schluessel(payload, titel):
    for feld in payload["fields"]:
        if feld["titel"] == titel:
            return feld["key"]
    raise AssertionError(f"Feld '{titel}' gibt es nicht: "
                         f"{[f['titel'] for f in payload['fields']][:20]}")


def _speichern(text, **aenderungen):
    payload, grund = ya.baue(text)
    assert payload is not None, grund
    return ya.schreibe(text, _formular(payload, **aenderungen),
                       payload["assistant_yaml_meta"])


# ----------------------------------------------------------------- Unveraendert


def test_ohne_aenderung_bleibt_die_datei_zeichengleich():
    assert _speichern(SPIGOT) == SPIGOT


def test_kommentare_und_reihenfolge_ueberleben_eine_aenderung():
    neu = _speichern(SPIGOT, **{"settings.sample-count": "20"})

    assert "# This is the main configuration file for Spigot." in neu
    assert "# nicht erhoehen ohne Grund" in neu
    assert "sample-count: 20" in neu
    # Alles ausser der einen Zahl ist unangetastet.
    assert neu.replace("sample-count: 20", "sample-count: 12") == SPIGOT


def test_nur_der_gemeinte_wert_wird_getroffen():
    """32 steht zweimal in der Datei - es darf nur eines davon wandern."""
    neu = _speichern(SPIGOT, **{"world-settings.default.entity-activation-range.animals": "48"})

    assert "animals: 48" in neu
    assert "monsters: 32" in neu


# ----------------------------------------------------------------- Werte und Typen


@pytest.mark.parametrize("titel,eingabe,erwartet", [
    ("settings.bungeecord", "true", "bungeecord: true"),
    ("settings.sample-count", "-3", "sample-count: -3"),
    ("world-settings.default.merge-radius.item", "4.5", "item: 4.5"),
    ("settings.restart-script", "./neu.sh", "restart-script: ./neu.sh"),
])
def test_einfache_aenderungen(titel, eingabe, erwartet):
    assert erwartet in _speichern(SPIGOT, **{titel: eingabe})


def test_hochkommata_bleiben_in_ihrer_schreibweise():
    neu = _speichern(SPIGOT, **{"settings.restart-message": "Gleich geht's weiter"})

    assert "restart-message: 'Gleich geht''s weiter'" in neu
    import yaml
    assert yaml.safe_load(neu)["settings"]["restart-message"] == "Gleich geht's weiter"


@pytest.mark.parametrize("eingabe", [
    "true", "false", "yes", "no", "on", "off", "null", "~", "012", "0x1F", "1e5",
    ".inf", "2024-01-01", "12", "3.5", "y", "n",
])
def test_text_bleibt_text(eingabe):
    """Ein Text, der wie ein Wahrheitswert oder eine Zahl aussieht, darf nicht kippen.

    PyYAML liest YAML 1.1 - dort ist auch "yes", "no", "on" und "off" ein Wahrheitswert.
    Ohne Anfuehrungszeichen waere aus der Whitelist-Meldung "no" ein False geworden.
    """
    import yaml

    neu = _speichern(SPIGOT, **{"messages.whitelist": eingabe})
    wert = yaml.safe_load(neu)["messages"]["whitelist"]

    assert wert == eingabe, f"{eingabe!r} wurde zu {wert!r}"
    assert isinstance(wert, str)


@pytest.mark.parametrize("eingabe", [
    "Nicht auf dem Server! # kein Kommentar",
    "a: b",
    "  fuehrendes Leerzeichen",
    "- wie eine Liste",
    "{geschweift}",
    "[eckig]",
    "*stern",
    "&anker",
    "schluss:",
    "Umlaute ae oe ue und Emoji \U0001f9f1",
    '"schon in Anfuehrungszeichen"',
])
def test_heikle_texte_kommen_heil_an(eingabe):
    import yaml

    neu = _speichern(SPIGOT, **{"messages.whitelist": eingabe})

    assert yaml.safe_load(neu)["messages"]["whitelist"] == eingabe.strip()


def test_zahlenfeld_nimmt_keinen_text():
    with pytest.raises(ya.YamlAssistentFehler) as fehler:
        _speichern(SPIGOT, **{"settings.sample-count": "viele"})

    assert "keine Zahl" in str(fehler.value)


def test_zahlenfeld_darf_nicht_leer_bleiben():
    """Leer waere in YAML null - und ein null bei netty-threads kippt den Serverstart."""
    with pytest.raises(ya.YamlAssistentFehler):
        _speichern(SPIGOT, **{"settings.netty-threads": ""})


def test_wahrheitswert_nimmt_nur_true_oder_false():
    with pytest.raises(ya.YamlAssistentFehler):
        _speichern(SPIGOT, **{"settings.bungeecord": "vielleicht"})


def test_text_leeren_ergibt_leeren_text_nicht_null():
    import yaml

    neu = _speichern(SPIGOT, **{"messages.whitelist": ""})

    assert yaml.safe_load(neu)["messages"]["whitelist"] == ""


def test_mehrzeilige_eingabe_wird_abgelehnt():
    payload, _ = ya.baue(SPIGOT)
    daten = _formular(payload)
    daten[_schluessel(payload, "messages.whitelist")] = "erste\nzweite"

    with pytest.raises(ya.YamlAssistentFehler):
        ya.schreibe(SPIGOT, daten, payload["assistant_yaml_meta"])


# ----------------------------------------------------------------- Was NICHT angeboten wird


def test_blockskalar_und_listen_bleiben_draussen():
    text = ("motd: |\n  erste Zeile\n  zweite Zeile\nliste:\n  - eins\n  - zwei\n"
            "zahl: 5\n")
    payload, _ = ya.baue(text)
    titel = [feld["titel"] for feld in payload["fields"]]

    assert "motd" not in titel
    assert "zahl" in titel
    # Einzelne Listeneintraege sind ersetzbar und duerfen deshalb mit.
    assert "liste[0]" in titel


def test_anker_und_alias_werden_ausgelassen():
    """Der Textabschnitt eines Ankers enthaelt '&name' - ersetzen wuerde ihn zerstoeren."""
    text = "a: &ref 7\nb: *ref\nc: 1\n"
    payload, _ = ya.baue(text)
    titel = [feld["titel"] for feld in payload["fields"]]

    assert titel == ["c"]


def test_doppelte_schluessel_werden_ausgelassen():
    text = "a: 1\na: 2\nb: 3\n"
    payload, _ = ya.baue(text)

    assert [feld["titel"] for feld in payload["fields"]] == ["b"]


def test_kaputtes_yaml_erklaert_sich():
    payload, grund = ya.baue("a: [unvollstaendig\n")

    assert payload is None
    assert "YAML" in grund


def test_datei_ohne_einzelwerte_erklaert_sich():
    payload, grund = ya.baue("# nur ein Kommentar\n")

    assert payload is None
    assert grund


# ----------------------------------------------------------------- Zwischenzeitlich geaendert


def test_fremde_aenderung_wird_bemerkt_und_nichts_geschrieben():
    payload, _ = ya.baue(SPIGOT)
    daten = _formular(payload, **{"settings.sample-count": "20"})
    # Jemand anderes hat die Datei inzwischen angefasst.
    inzwischen = SPIGOT.replace("sample-count: 12", "sample-count: 99")

    with pytest.raises(ya.YamlAssistentFehler) as fehler:
        ya.schreibe(inzwischen, daten, payload["assistant_yaml_meta"])

    assert "geaendert" in str(fehler.value)


def test_verschwundener_schluessel_wird_bemerkt():
    payload, _ = ya.baue(SPIGOT)
    daten = _formular(payload, **{"settings.sample-count": "20"})
    inzwischen = SPIGOT.replace("  sample-count: 12\n", "")

    with pytest.raises(ya.YamlAssistentFehler):
        ya.schreibe(inzwischen, daten, payload["assistant_yaml_meta"])


def test_ohne_metadaten_wird_nichts_geschrieben():
    with pytest.raises(ya.YamlAssistentFehler):
        ya.schreibe(SPIGOT, {"yaml_field_0": "x"}, "kein json")


# ----------------------------------------------------------------- Leerer Schluessel


def test_leerer_schluessel_bekommt_ein_leerzeichen():
    text = "server:\n  passwort:\n  port: 25565\n"
    neu = _speichern(text, **{"server.passwort": "geheim"})

    assert neu == "server:\n  passwort: geheim\n  port: 25565\n"


def test_leerer_schluessel_am_dateiende():
    text = "a: 1\nb:\n"
    neu = _speichern(text, **{"b": "zwei"})

    assert neu == "a: 1\nb: zwei\n"


def test_leerer_schluessel_darf_leer_bleiben():
    text = "a: 1\nb:\n"

    assert _speichern(text) == text


# ----------------------------------------------------------------- Anbindung


def test_yml_bekommt_jetzt_einen_assistenten():
    payload = get_assistant_payload("spigot.yml", SPIGOT)

    assert payload is not None
    assert payload["mode"] == "yaml_fields"
    assert assistent_hinweis("spigot.yml", SPIGOT) == ""


def test_yaml_endung_zaehlt_auch():
    assert get_assistant_payload("config/paper-global.yaml", "a: 1\n") is not None


def test_ohne_assistent_gibt_es_eine_erklaerung():
    hinweis = assistent_hinweis("mein.cfg", "irgendwas\n")

    assert "Freitext" in hinweis


def test_kaputtes_yaml_erklaert_sich_auch_auf_der_seite():
    hinweis = assistent_hinweis("spigot.yml", "a: [kaputt\n")

    assert "YAML" in hinweis


def test_schreiben_laeuft_ueber_den_gemeinsamen_einstieg():
    payload = get_assistant_payload("spigot.yml", SPIGOT)
    daten = _formular(payload, **{"settings.timeout-time": "90"})

    neu = build_content_from_assistant("spigot.yml", daten, SPIGOT)

    assert "timeout-time: 90" in neu
    assert "# This is the main configuration file for Spigot." in neu


def test_ohne_aktuellen_inhalt_wird_nicht_geschrieben():
    """Fehlt der Dateistand, darf auf keinen Fall geraten werden."""
    payload = get_assistant_payload("spigot.yml", SPIGOT)

    with pytest.raises(ValueError):
        build_content_from_assistant("spigot.yml", _formular(payload), None)


def test_die_liste_der_felder_hat_eine_obergrenze():
    gross = "".join(f"k{i}: {i}\n" for i in range(ya.MAX_FELDER + 50))
    payload, _ = ya.baue(gross)

    assert len(payload["fields"]) == ya.MAX_FELDER
    assert str(ya.MAX_FELDER) in payload["hinweis"]


# ----------------------------------------------------------------- Ueber die Oberflaeche


def _login(client):
    antwort = client.post("/login", data={"username": "admin", "password": "admin123!"},
                          follow_redirects=False)
    assert antwort.status_code == 303


def _server_anlegen(client, ordner, name="YAML Server"):
    (ordner / "start.bat").write_text("@echo off\n", encoding="utf-8")
    antwort = client.post("/servers/import/confirm", data={
        "name": name, "base_path": str(ordner), "server_type": "paper",
        "mc_version": "1.20.1", "start_mode": "bat",
        "start_bat_path": str(ordner / "start.bat"), "start_command": "",
    }, follow_redirects=False)
    assert antwort.status_code == 303
    return int(re.search(r"/servers/(\d+)", antwort.headers["location"]).group(1))


def test_seite_zeigt_den_yaml_assistenten(client, tmp_path):
    _login(client)
    ordner = tmp_path / "srv_yaml"
    ordner.mkdir()
    (ordner / "spigot.yml").write_text(SPIGOT, encoding="utf-8")
    server_id = _server_anlegen(client, ordner)

    antwort = client.get(f"/servers/{server_id}/files?file=spigot.yml&mode=assistant")

    assert antwort.status_code == 200
    assert "YAML Assistent" in antwort.text
    assert "bungeecord" in antwort.text


def test_umschalten_behaelt_die_sortierung(client, tmp_path):
    """Der gemeldete Fehler: beide Umschaltknoepfe muessen den Listenzustand mitnehmen."""
    _login(client)
    ordner = tmp_path / "srv_sort"
    ordner.mkdir()
    (ordner / "spigot.yml").write_text(SPIGOT, encoding="utf-8")
    server_id = _server_anlegen(client, ordner)

    antwort = client.get(
        f"/servers/{server_id}/files?file=spigot.yml&mode=assistant&sort=groesse&dir=ab")

    assert "mode=raw&amp;sort=groesse&amp;dir=ab" in antwort.text
    assert "mode=assistant&amp;sort=groesse&amp;dir=ab" in antwort.text
    assert '<input type="hidden" name="__liste_sort" value="groesse">' in antwort.text


def test_speichern_fuehrt_in_die_gleiche_sortierung_zurueck(client, tmp_path):
    _login(client)
    ordner = tmp_path / "srv_save"
    ordner.mkdir()
    ziel = ordner / "spigot.yml"
    ziel.write_text(SPIGOT, encoding="utf-8")
    server_id = _server_anlegen(client, ordner)

    seite = client.get(f"/servers/{server_id}/files?file=spigot.yml&mode=assistant"
                       f"&sort=geaendert&dir=ab")
    meta = re.search(r'name="__assistant_yaml_meta" value="([^"]*)"', seite.text).group(1)
    import html as html_modul
    payload, _ = ya.baue(SPIGOT)
    daten = _formular(payload, **{"settings.timeout-time": "90"})
    daten["__assistant_yaml_meta"] = html_modul.unescape(meta)
    daten["relative_path"] = "spigot.yml"
    daten["__liste_sort"] = "geaendert"
    daten["__liste_dir"] = "ab"

    antwort = client.post(f"/servers/{server_id}/files/assistant-save", data=daten,
                          follow_redirects=False)

    assert antwort.status_code == 303
    ort = antwort.headers["location"]
    assert "sort=geaendert" in ort and "dir=ab" in ort
    geschrieben = ziel.read_text(encoding="utf-8")
    assert "timeout-time: 90" in geschrieben
    assert "# This is the main configuration file for Spigot." in geschrieben


def test_listenfelder_landen_nicht_in_der_datei(client, tmp_path):
    """__liste_sort darf niemals als Schluessel in einer .properties auftauchen."""
    _login(client)
    ordner = tmp_path / "srv_props"
    ordner.mkdir()
    ziel = ordner / "server.properties"
    ziel.write_text("motd=Hallo\nmax-players=20\n", encoding="utf-8")
    server_id = _server_anlegen(client, ordner)

    antwort = client.post(f"/servers/{server_id}/files/assistant-save", data={
        "relative_path": "server.properties",
        "motd": "Neu", "max-players": "30",
        "__liste_sort": "groesse", "__liste_dir": "ab", "__liste_q": "yml",
    }, follow_redirects=False)

    assert antwort.status_code == 303
    inhalt = ziel.read_text(encoding="utf-8")
    assert "__liste_sort" not in inhalt
    assert "groesse" not in inhalt
    assert "motd=Neu" in inhalt


def test_datei_ohne_assistent_erklaert_sich_auf_der_seite(client, tmp_path):
    _login(client)
    ordner = tmp_path / "srv_cfg"
    ordner.mkdir()
    (ordner / "mein.cfg").write_text("irgendwas\n", encoding="utf-8")
    server_id = _server_anlegen(client, ordner)

    antwort = client.get(f"/servers/{server_id}/files?file=mein.cfg&mode=assistant")

    assert antwort.status_code == 200
    assert "keinen Assistenten" in antwort.text


# ----------------------------------------------------------------- Feindliche Dateien


def test_alias_geflecht_bleibt_schnell():
    """Ein Alias liefert DENSELBEN Knoten - ohne Sperre wird daraus ein Lawinenlauf.

    Neun Zeilen mit je neun Aliasen ergaben 9^8 Knotenbesuche: gemessene 137 Sekunden
    fuer 486 Byte, ausloesbar mit reinem Leserecht bei jedem Oeffnen der Dateiseite.
    """
    import time

    zeilen = ['l0: &l0 ["x", "x", "x", "x", "x", "x", "x", "x", "x"]']
    for i in range(1, 12):
        zeilen.append(f"l{i}: &l{i} [" + ", ".join([f"*l{i - 1}"] * 9) + "]")
    bombe = "\n".join(zeilen) + "\n"

    start = time.perf_counter()
    payload, _ = ya.baue(bombe)
    gebraucht = time.perf_counter() - start

    assert gebraucht < 2.0, f"{gebraucht:.1f}s fuer {len(bombe)} Byte"
    assert payload is not None


def test_zu_tiefe_verschachtelung_gibt_eine_meldung_statt_abzustuerzen():
    """RecursionError ist KEIN YAMLError - vorher endete das in einem HTTP 500."""
    payload, grund = ya.baue("a: " + "[" * 500 + "]" * 500 + "\n")

    assert payload is None
    assert grund


def test_sehr_grosse_dateien_werden_abgelehnt():
    payload, grund = ya.baue("x: " + "y" * (ya.MAX_ZEICHEN + 10) + "\n")

    assert payload is None
    assert "Freitext" in grund


def test_feindliche_datei_macht_die_seite_nicht_kaputt(client, tmp_path):
    """Der ganze Weg: die Datei muss sich im Browser oeffnen lassen, sonst ist sie auch
    nicht mehr zu reparieren."""
    _login(client)
    ordner = tmp_path / "srv_boese"
    ordner.mkdir()
    (ordner / "boese.yml").write_text("a: " + "[" * 500 + "]" * 500 + "\n",
                                      encoding="utf-8")
    server_id = _server_anlegen(client, ordner)

    antwort = client.get(f"/servers/{server_id}/files?file=boese.yml&mode=assistant")

    assert antwort.status_code == 200
    assert "YAML" in antwort.text


def test_suche_zuruecksetzen_behaelt_die_geoeffnete_datei(client, tmp_path):
    _login(client)
    ordner = tmp_path / "srv_suche"
    ordner.mkdir()
    (ordner / "spigot.yml").write_text(SPIGOT, encoding="utf-8")
    server_id = _server_anlegen(client, ordner)

    antwort = client.get(f"/servers/{server_id}/files?file=spigot.yml&mode=raw"
                         f"&sort=name&dir=auf&q=yml")

    assert "title=\"Suche zuruecksetzen\"" in antwort.text
    treffer = [z for z in antwort.text.splitlines() if "Suche zuruecksetzen" in z
               or "ghost" in z and "files?sort=" in z]
    assert any("file=spigot.yml" in z and "mode=raw" in z for z in treffer), treffer[:3]


def test_klick_auf_die_naechste_datei_behaelt_den_modus(client, tmp_path):
    _login(client)
    ordner = tmp_path / "srv_klick"
    ordner.mkdir()
    (ordner / "spigot.yml").write_text(SPIGOT, encoding="utf-8")
    (ordner / "bukkit.yml").write_text("settings:\n  allow-end: true\n", encoding="utf-8")
    server_id = _server_anlegen(client, ordner)

    antwort = client.get(f"/servers/{server_id}/files?file=spigot.yml&mode=raw")

    assert "file=bukkit.yml&amp;mode=raw" in antwort.text
