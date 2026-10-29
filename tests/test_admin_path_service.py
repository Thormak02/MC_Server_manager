"""Tests fuer den Pfadzugriff der Super-Admins.

Das Versprechen dieses Dienstes ist schmal und soll schmal bleiben: er verhindert den
Unfall und die Rechteausweitung, nicht die bewusste Entscheidung eines Super-Admins.
Entsprechend pruefen diese Tests vor allem die Raender:

* Geheimnisse bleiben zu - auch wenn der Weg dorthin ueber eine Abzweigung (Junction)
  fuehrt, die auf dem Papier harmlos aussieht.
* In den Programmcode des Managers fuehrt kein Schreibweg. Wer app/ schreiben darf,
  schreibt die Pruefung um, die ihn gerade begrenzt.
* Geloescht wird nichts dicht unter einer Wurzel und nichts, was Geheimnisse ENTHAELT.
* Eine bestehende Datei behaelt ihre Kodierung, und ein nicht kodierbares Zeichen laesst
  sie unveraendert - nicht leer.
"""
import os
import subprocess
from pathlib import Path

import pytest


def _junction(verknuepfung: Path, ziel: Path) -> bool:
    """Eine Windows-Abzweigung anlegen. False, wenn das System das nicht hergibt."""
    verknuepfung.parent.mkdir(parents=True, exist_ok=True)
    fertig = subprocess.run(["cmd", "/c", "mklink", "/J", str(verknuepfung), str(ziel)],
                            capture_output=True, text=True)
    return fertig.returncode == 0 and verknuepfung.exists()


# ------------------------------------------------------------------- Geheimnisse

def test_geheimnisse_bleiben_zu():
    """Diese Stellen trennen 'den Manager bedienen' von 'den Manager uebernehmen'."""
    from app.services import admin_path_service as aps

    for pfad in (".env", ".env.example", "data/mc_server_manager.db",
                 "data/mc_server_manager.db-wal", "data/db-snapshots/x.sql",
                 ".git/config", "~/.ssh/id_ed25519", "~/.aws/credentials",
                 "~/.claude/settings.json", "C:/zeug/server.pem", "C:/zeug/tresor.kdbx",
                 "C:/zeug/.netrc"):
        with pytest.raises(aps.PfadFehler, match="bleibt auch fuer Super-Admins zu"):
            aps.pruefe(pfad, muss_existieren=False)


def test_abzweigung_auf_ein_geheimnis_wird_erkannt(tmp_path):
    """Der wichtigste Test des Moduls.

    Eine Abzweigung unter data/ sieht wie ein harmloser Ordner aus. Wer die Sperrliste
    gegen den EINGEGEBENEN Pfad prueft statt gegen das aufgeloeste Ziel, hat damit einen
    Weg in jeden Ordner des Systems geoeffnet.
    """
    from app.services import admin_path_service as aps

    echtes_geheimnis = tmp_path / "heim" / ".ssh"
    echtes_geheimnis.mkdir(parents=True)
    (echtes_geheimnis / "id_rsa").write_text("PRIVAT", encoding="utf-8")
    brueckenkopf = tmp_path / "harmlos" / "ordner"
    if not _junction(brueckenkopf, echtes_geheimnis):
        pytest.skip("Abzweigungen sind auf diesem System nicht anlegbar")

    with pytest.raises(aps.PfadFehler, match="bleibt auch fuer Super-Admins zu"):
        aps.pruefe(str(brueckenkopf / "id_rsa"))


def test_abzweigung_in_den_programmordner_darf_nicht_beschrieben_werden(tmp_path,
                                                                         monkeypatch):
    """Dieselbe Falle beim Schreiben. Der Programmordner wird hier nachgestellt, weil
    eine Abzweigung kein UNC-Ziel haben kann - der echte Manager liegt auf einer
    Freigabe, die Pruefung selbst ist davon unabhaengig."""
    from app.services import admin_path_service as aps

    alsob = tmp_path / "programm"
    (alsob / "app" / "services").mkdir(parents=True)
    (alsob / "app" / "services" / "ai_agent_service.py").write_text("x", encoding="utf-8")
    monkeypatch.setattr(aps, "_wurzel", lambda: alsob)

    brueckenkopf = tmp_path / "umweg"
    if not _junction(brueckenkopf, alsob / "app"):
        pytest.skip("Abzweigungen sind auf diesem System nicht anlegbar")

    with pytest.raises(aps.PfadFehler, match="Programmordner"):
        aps.pruefe(str(brueckenkopf / "services" / "ai_agent_service.py"),
                   zweck="schreiben")


# ----------------------------------------------------------------- Windows-Fallen

def test_geraetenamen_und_datenstroeme_werden_abgewiesen():
    from app.services import admin_path_service as aps

    for pfad, muster in (("NUL", "Geraet"), ("C:/zeug/CON", "Geraet"),
                         ("C:/zeug/nul.txt", "Geraet"),
                         ("data/logs/x.txt:versteckt", "Doppelpunkt"),
                         ("data/*.log", "Suchmuster"), ("data/?.log", "Suchmuster")):
        with pytest.raises(aps.PfadFehler, match=muster):
            aps.pruefe(pfad, muss_existieren=False)


def test_nullzeichen_und_leerer_pfad():
    from app.services import admin_path_service as aps

    with pytest.raises(aps.PfadFehler, match="kein Pfad"):
        aps.pruefe("   ")
    with pytest.raises(aps.PfadFehler, match="Nullzeichen"):
        aps.pruefe("data/x\x00.txt", muss_existieren=False)


def test_relativer_pfad_zaehlt_vom_programmordner(tmp_path, monkeypatch):
    """Nicht vom Arbeitsverzeichnis: welches das ist, haengt davon ab, wie der Dienst
    gestartet wurde - und ein Pfad, dessen Bedeutung davon abhaengt, ist geraten."""
    from app.services import admin_path_service as aps

    monkeypatch.chdir(tmp_path)
    ziel = aps.pruefe("data/logs", muss_existieren=False)
    assert ziel == (aps._wurzel() / "data" / "logs").resolve()


# -------------------------------------------------------------------- Schreiben

def test_kein_schreibweg_in_den_programmcode():
    """Der Kern der Zusage: Rechte duerfen durch den Assistenten nicht umgehbar sein.
    Wer app/ schreiben kann, schreibt die Rechtepruefung um - dauerhaft."""
    from app.services import admin_path_service as aps

    for pfad in ("app/services/ai_agent_service.py", "app/main.py", "tests/conftest.py",
                 "requirements.txt", ".venv/Lib/site-packages/sitecustomize.py",
                 "scripts/Manager starten.bat"):
        with pytest.raises(aps.PfadFehler, match="Programmordner"):
            aps.pruefe(pfad, zweck="schreiben", muss_existieren=False)

    # Lesen bleibt erlaubt - der Quelltext liegt ohnehin oeffentlich im Repository.
    assert aps.pruefe("app/main.py", zweck="lesen").name == "main.py"


def test_datenordner_im_programmordner_bleibt_schreibbar():
    from app.services import admin_path_service as aps

    for pfad in ("data/temp/x.txt", "data/resourcepacks/y.zip", "managed_servers/s/z.txt"):
        assert aps.pruefe(pfad, zweck="schreiben", muss_existieren=False)


def test_systemordner_werden_nicht_beschrieben():
    from app.services import admin_path_service as aps

    windows = Path(os.environ.get("SystemRoot") or "C:/Windows")
    for pfad in (windows / "System32/drivers/etc/hosts",
                 Path(os.environ.get("ProgramFiles") or "C:/Program Files") / "x/y.txt",
                 Path(os.environ.get("ProgramData") or "C:/ProgramData") / "x/y.txt"):
        with pytest.raises(aps.PfadFehler, match="aendert der Manager nichts"):
            aps.pruefe(str(pfad), zweck="schreiben", muss_existieren=False)


def test_bestehende_datei_behaelt_ihre_kodierung(tmp_path):
    """Stillschweigend auf UTF-8 umzukodieren aendert jedes Sonderzeichen der Datei.
    Genau dieser Fehler ist im Manager schon einmal passiert (server.properties)."""
    from app.services import admin_path_service as aps

    ziel = tmp_path / "alt.txt"
    ziel.write_bytes("motd=Gruesse aus Muenchen: aeoeue".replace("ae", "\u00e4")
                     .replace("oe", "\u00f6").replace("ue", "\u00fc").encode("cp1252"))

    ergebnis = aps.schreiben(ziel, "motd=Neue Gr\u00fc\u00dfe")
    assert ergebnis["kodierung"] == "cp1252"
    assert ziel.read_bytes().decode("cp1252") == "motd=Neue Gr\u00fc\u00dfe"


def test_nicht_kodierbares_zeichen_laesst_die_datei_unveraendert(tmp_path):
    """write_text kuerzt die Datei beim Oeffnen und scheitert erst danach am Kodieren -
    gemessen blieben von 18 Bytes null uebrig. Deshalb wird zuerst kodiert."""
    from app.services import admin_path_service as aps

    ziel = tmp_path / "alt.txt"
    vorher = "Gr\u00fc\u00dfe".encode("cp1252")
    ziel.write_bytes(vorher)

    with pytest.raises(aps.PfadFehler, match="unveraendert"):
        aps.schreiben(ziel, "Emoji: \U0001f600")
    assert ziel.read_bytes() == vorher


def test_neue_datei_wird_utf8_und_legt_den_ordner_an(tmp_path):
    from app.services import admin_path_service as aps

    ziel = tmp_path / "neu" / "tief" / "x.txt"
    ergebnis = aps.schreiben(ziel, "Gr\u00fc\u00dfe \u2713")
    assert ergebnis["kodierung"] == "utf-8"
    assert ergebnis["bytes_vorher"] is None
    assert ziel.read_text(encoding="utf-8") == "Gr\u00fc\u00dfe \u2713"


def test_uebergrosser_text_wird_abgewiesen(tmp_path):
    from app.services import admin_path_service as aps

    ziel = tmp_path / "gross.txt"
    with pytest.raises(aps.PfadFehler, match="Zeichen"):
        aps.schreiben(ziel, "x" * (aps.MAX_SCHREIB_ZEICHEN + 1))
    assert not ziel.exists()


# ----------------------------------------------------------------------- Lesen

def test_lesen_ist_begrenzt_und_schneidet_kein_zeichen_entzwei(tmp_path):
    """Ein abgeschnittenes UTF-8-Zeichen am Ende darf nicht dazu fuehren, dass die GANZE
    Datei als cp1252 gelesen wird - dann waere jedes Sonderzeichen darin verfaelscht."""
    from app.services import admin_path_service as aps

    ziel = tmp_path / "lang.txt"
    # Fuellen bis genau ein Vier-Byte-Zeichen ueber die Grenze haengt.
    fueller = "\u00e4" * ((aps.MAX_LESE_BYTES - 2) // 2)
    ziel.write_text(fueller + "\U0001f600" + "Rest", encoding="utf-8")

    ergebnis = aps.lesen(ziel)
    assert ergebnis["kodierung"] == "utf-8"
    assert ergebnis["gekuerzt"] is True
    assert ergebnis["inhalt"].startswith("\u00e4\u00e4\u00e4")
    assert "\ufffd" not in ergebnis["inhalt"]


def test_binaerdatei_wird_als_text_abgewiesen(tmp_path):
    from app.services import admin_path_service as aps

    ziel = tmp_path / "zeug.bin"
    ziel.write_bytes(b"\x89PNG\r\n\x1a\n" + bytes(range(256)))
    with pytest.raises(aps.PfadFehler, match="keine Textdatei"):
        aps.lesen(ziel)


def test_auflisten_ist_begrenzt(tmp_path):
    from app.services import admin_path_service as aps

    for nummer in range(aps.MAX_EINTRAEGE + 20):
        (tmp_path / f"d{nummer:04d}.txt").write_text("x", encoding="utf-8")
    (tmp_path / "ordner").mkdir()

    ergebnis = aps.auflisten(tmp_path)
    assert len(ergebnis["eintraege"]) == aps.MAX_EINTRAEGE
    assert ergebnis["gekuerzt"] is True
    # Ordner zuerst - beim Suchen ist das die nuetzlichere Reihenfolge.
    assert ergebnis["eintraege"][0]["art"] == "ordner"


# --------------------------------------------------------------------- Loeschen

def test_nicht_dicht_unter_einer_wurzel_loeschen():
    from app.services import admin_path_service as aps

    for pfad in ("C:/Users", "C:/Windows", "C:/", "//friedrichnas/FriedrichNAS/x"):
        with pytest.raises(aps.PfadFehler):
            aps.pruefe(pfad, zweck="loeschen", muss_existieren=False)


def test_ordner_mit_geheimnis_darunter_wird_nicht_geloescht(tmp_path):
    """Die Sperrliste fuer den Ordner selbst genuegt nicht: tmp/heim steht auf keiner
    Liste, .ssh darunter schon - und rmtree nimmt es mit."""
    from app.services import admin_path_service as aps

    heim = tmp_path / "tief" / "heim"
    (heim / ".ssh").mkdir(parents=True)
    (heim / ".ssh" / "id_rsa").write_text("PRIVAT", encoding="utf-8")

    with pytest.raises(aps.PfadFehler, match="wird nicht geloescht"):
        aps.pruefe(str(heim), zweck="loeschen")


def test_ordner_wird_nur_mit_rekursiv_geloescht(tmp_path):
    from app.services import admin_path_service as aps

    ordner = tmp_path / "weg"
    (ordner / "drin").mkdir(parents=True)
    (ordner / "drin" / "x.txt").write_text("x", encoding="utf-8")

    with pytest.raises(aps.PfadFehler, match="rekursiv=true"):
        aps.loeschen(ordner)
    assert ordner.exists()

    ergebnis = aps.loeschen(ordner, rekursiv=True)
    assert ergebnis["eintraege"] == 2
    assert not ordner.exists()


def test_leerer_ordner_geht_ohne_rekursiv(tmp_path):
    from app.services import admin_path_service as aps

    ordner = tmp_path / "leer"
    ordner.mkdir()
    assert aps.loeschen(ordner)["art"] == "ordner"
    assert not ordner.exists()


def test_serverordner_wird_nicht_so_geloescht(client, tmp_path):
    """Einen Serverordner unter dem laufenden Prozess wegzuloeschen hinterlaesst einen
    Server, der laut Datenbank existiert, laut Festplatte nicht und laut Prozessliste
    noch laeuft."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import admin_path_service as aps

    basis = tmp_path / "server" / "paper1"
    (basis / "plugins").mkdir(parents=True)

    with SessionLocal() as db:
        db.add(Server(name="paper1", slug="paper1", server_type="paper",
                      mc_version="26.3", base_path=str(basis), port=25901))
        db.commit()

        with pytest.raises(aps.PfadFehler, match="Ordner des Servers"):
            aps.pruefe(str(basis), zweck="loeschen", db=db)
        # Auch der Ordner DARUEBER nicht - rmtree nimmt den Server mit.
        with pytest.raises(aps.PfadFehler, match="Darunter liegt der Ordner"):
            aps.pruefe(str(tmp_path / "server"), zweck="loeschen", db=db)
        # Eine einzelne Datei darin dagegen schon.
        assert aps.pruefe(str(basis / "plugins"), zweck="loeschen", db=db)


# ------------------------------------------------------------------ Verschieben

def test_verschieben_ueberschreibt_nichts(tmp_path):
    from app.services import admin_path_service as aps

    quelle = tmp_path / "a.txt"
    quelle.write_text("neu", encoding="utf-8")
    ziel = tmp_path / "b.txt"
    ziel.write_text("alt", encoding="utf-8")

    with pytest.raises(aps.PfadFehler, match="gibt es schon"):
        aps.verschieben(quelle, ziel)
    assert ziel.read_text(encoding="utf-8") == "alt"

    frei = tmp_path / "unter" / "c.txt"
    assert aps.verschieben(quelle, frei)["ziel"] == str(frei)
    assert frei.read_text(encoding="utf-8") == "neu"
    assert not quelle.exists()
