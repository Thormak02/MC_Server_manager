"""Tests fuer die Admin-Werkzeuge: in Archive sehen, VT-Teilpakete lesen und setzen.

Der Anlass war eine Luecke im Blickfeld: welche Teilpakete in einem VanillaTweaks-Paket
stecken, stand nur in einer Datei INNERHALB des Archivs - und beim Resource Pack liegt
das Archiv ausserhalb des Serverordners. Gegengift ist NICHT ein weiterer Dateizugriff,
sondern ein Werkzeug mit scharfem Rand. Also prueft dieser Test vor allem den Rand:

* Der Serverordner bleibt die Grenze. Ein Archiv daneben ist nicht zu oeffnen.
* Nur Admins - und das wird fuer JEDES Werkzeug mit diesem Vorbehalt geprueft, nicht nur
  fuer das erste.
* Ein Eintrag gehoert zu einem Server. Eine fremde Inhalts-ID zaehlt nicht.
* Groesse ist begrenzt, und zwar beim Entpacken. Ein Archiv darf nicht bestimmen, wie
  viel Speicher der Manager belegt.
* Was im Bestaetigungsdialog steht, muss das sein, was danach passiert - und ein Fehler
  gehoert ins Hindernis, nicht in die Tatbeschreibung.
* Zugangsdaten aus einer Datei verlassen das Haus nicht, auch nicht als Werkzeugergebnis.

Kein Test spricht mit VanillaTweaks; Katalog und Erzeugung werden ersetzt.
"""
import zipfile
from pathlib import Path

import pytest


def _nutzer(db, server=None, rolle="admin", name="archiv-tester"):
    """Ein Nutzer MIT allen Serverrechten - dann liegt ein Fehlschlag nur an der Rolle.

    Auch ein Admin braucht den Rechte-Eintrag: ohne ihn sieht er den Server gar nicht,
    und der Test wuerde das Richtige aus dem falschen Grund beweisen.
    """
    from app.core.security import hash_password
    from app.models.server_permission import ServerPermission
    from app.models.user import User

    person = User(username=name, password_hash=hash_password("Egal12345!"),
                  role=rolle, is_active=True)
    db.add(person)
    db.commit()
    db.refresh(person)
    if server is not None:
        db.add(ServerPermission(user_id=person.id, server_id=server.id, can_view=True,
                                can_console=True, can_restart=True, can_edit_files=True,
                                can_manage=True))
        db.commit()
    return person


def _server(db, tmp_path, *, slug: str, port: int):
    from app.models.server import Server

    basis = tmp_path / slug
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text(
        f"level-name=world\nserver-port={port}\n", encoding="utf-8")
    server = Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                    base_path=str(basis), port=port, status="stopped",
                    start_mode="command", start_command="java -jar server.jar",
                    memory_min_mb=1024, memory_max_mb=4096)
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


def _zip(pfad: Path, dateien: dict, verfahren=zipfile.ZIP_DEFLATED) -> Path:
    pfad.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(pfad, "w", verfahren) as z:
        for name, inhalt in dateien.items():
            z.writestr(name, inhalt)
    return pfad


def _manifest(*packs: str) -> str:
    return ("VanillaTweaks Resource Packs\nVersion: 26.3\nPacks:\n"
            + "\n".join(packs) + "\n")


def _paketname(server) -> str:
    """So heissen die selbst gehosteten Pakete: vt_<server>_<hash>.zip.

    Die Server-ID gehoert hinein, nicht eine feste 1: der Dienst prueft inzwischen, dass
    die Datei zu DIESEM Server gehoert - sonst liesse sich ueber die resource-pack-URL
    ein fremdes Paket benennen.
    """
    return f"vt_{server.id}_abc.zip"


def _eintrag(db, server, *, typ="vt:resourcepacks", datei="",
             name="VanillaTweaks Resource Pack"):
    from app.models.installed_content import InstalledContent

    zeile = InstalledContent(
        server_id=server.id, provider_name="vanillatweaks",
        content_type="resourcepack" if typ == "vt:resourcepacks" else "datapack",
        external_project_id=typ, external_version_id="26.3", name=name,
        version_label="26.3", file_name=datei or _paketname(server))
    db.add(zeile)
    db.commit()
    db.refresh(zeile)
    return zeile


def _katalog(monkeypatch, *namen: str, unterkategorie: dict | None = None):
    """Einen VT-Katalog vortaeuschen, ohne das Netz anzufassen.

    ``unterkategorie`` legt eine zweite Ebene an - die gibt es beim echten
    Resource-Pack-Katalog, und sie wurde lange uebersehen.
    """
    from app.services import vanillatweaks_service as vt

    def _kategorien(pack_type, version):
        oben = {"category": "Aesthetic",
                "packs": [{"name": n, "display": n} for n in namen]}
        if unterkategorie:
            oben["categories"] = [
                {"category": name,
                 "packs": [{"name": n, "display": d} for n, d in inhalt.items()]}
                for name, inhalt in unterkategorie.items()]
        return [oben]

    monkeypatch.setattr(vt, "list_categories", _kategorien)
    monkeypatch.setattr(vt, "resolve_vt_version", lambda mc_version: "26.3")


# --------------------------------------------------------------- archiv_ansehen

def test_archiv_inhaltsverzeichnis_und_eintrag(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-ok", port=25851)
        person = _nutzer(db, server, name="arch-ok")
        _zip(Path(server.base_path) / "plugins" / "Dings.jar",
             {"plugin.yml": "name: Dings\nmain: net.dings.Plugin\napi-version: '1.21'\n",
              "net/dings/Plugin.class": b"\xca\xfe\xba\xbe"})

        liste = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                       {"server_id": server.id,
                                        "pfad": "plugins/Dings.jar"})
        assert "fehler" not in liste, liste
        assert "plugin.yml" in liste["eintraege"]
        assert liste["eintraege_gesamt"] == 2

        datei = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                       {"server_id": server.id,
                                        "pfad": "plugins/Dings.jar",
                                        "eintrag": "plugin.yml"})
        assert "fehler" not in datei, datei
        assert "api-version" in datei["inhalt"]
        assert datei["gekuerzt"] is False


def test_archive_werden_gefunden_und_falsche_pfade_korrigiert(client, tmp_path):
    """Der Entdeckungsschritt. dateien_auflisten zeigt nur Textdateien - ohne diese Liste
    muesste der Assistent Pfade raten, und jeder Fehlgriff kostet eine Runde."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-finden", port=25857)
        person = _nutzer(db, server, name="arch-finden")
        basis = Path(server.base_path)
        _zip(basis / "plugins" / "EssentialsX.jar", {"plugin.yml": "name: EssentialsX"})
        _zip(basis / "plugins" / "update" / "EssentialsX.jar", {"plugin.yml": "name: X"})
        _zip(basis / "mods" / "Create.jar", {"fabric.mod.json": "{}"})
        _zip(basis / "world" / "datapacks" / "VT.zip", {"pack.mcmeta": "{}"})
        _zip(basis / "server.jar", {"version.json": "{}"})
        # Weltdaten gehoeren nicht dazu - und werden auch nicht durchsucht.
        (basis / "world" / "region").mkdir(parents=True, exist_ok=True)
        (basis / "world" / "region" / "r.0.0.mca").write_bytes(bytes(64))

        liste = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                       {"server_id": server.id})
        assert "fehler" not in liste, liste
        assert sorted(liste["archive"]) == [
            "mods/Create.jar", "plugins/EssentialsX.jar",
            "plugins/update/EssentialsX.jar", "server.jar", "world/datapacks/VT.zip"]

        # Ein geratener Pfad soll den richtigen gleich mitnennen. Der falsche ORDNER,
        # nicht die falsche Schreibweise: Windows findet "mods/create.jar" selbst.
        daneben = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                         {"server_id": server.id,
                                          "pfad": "plugins/Create.jar"})
    assert "fehler" in daneben
    assert "mods/Create.jar" in daneben["fehler"]


def test_abzweigung_bringt_keine_fremden_archive_in_die_liste(client, tmp_path):
    """glob folgt Abzweigungen. Ein "plugins/update", das auf einen anderen Ordner zeigt,
    haette die NAMEN fremder Archive in die Liste gebracht - lesen liessen sie sich nicht,
    aber genannt waeren sie, und ein Dateiname verraet oft genug."""
    import subprocess

    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-abzweig", port=25884)
        person = _nutzer(db, server, name="arch-abzweig")
        basis = Path(server.base_path)
        _zip(basis / "plugins" / "Eigen.jar", {"plugin.yml": "name: Eigen"})
        draussen = tmp_path / "fremd"
        _zip(draussen / "Verraeterisch.jar", {"plugin.yml": "name: X"})

        brueckenkopf = basis / "plugins" / "update"
        fertig = subprocess.run(["cmd", "/c", "mklink", "/J", str(brueckenkopf),
                                 str(draussen)], capture_output=True, text=True)
        if fertig.returncode != 0 or not brueckenkopf.exists():
            pytest.skip("Abzweigungen sind auf diesem System nicht anlegbar")

        liste = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                       {"server_id": server.id})
    assert "fehler" not in liste, liste
    assert liste["archive"] == ["plugins/Eigen.jar"]
    assert not any("Verraeterisch" in a for a in liste["archive"])


def test_archiv_nur_fuer_admins(client, tmp_path):
    """Ein Moderator MIT allen Serverrechten darf trotzdem nicht hineinsehen: in einem
    Jar kann eine config mit Zugangsdaten liegen, und die zeigt ihm auch der Dateieditor
    nicht (der kennt nur Textdateien)."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-mod", port=25852)
        _zip(Path(server.base_path) / "plugins" / "Geheim.jar",
             {"config.yml": "mysql-passwort: SUPERGEHEIM\n"})

        for rolle in ("moderator", "view_only"):
            person = _nutzer(db, server, rolle=rolle, name=f"arch-{rolle}")
            ergebnis = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                              {"server_id": server.id,
                                               "pfad": "plugins/Geheim.jar",
                                               "eintrag": "config.yml"})
            assert "fehler" in ergebnis, rolle
            assert "Admin" in ergebnis["fehler"]
            assert "SUPERGEHEIM" not in str(ergebnis)

        for rolle in ("admin", "super_admin"):
            person = _nutzer(db, server, rolle=rolle, name=f"arch-{rolle}")
            ok = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                        {"server_id": server.id,
                                         "pfad": "plugins/Geheim.jar"})
            assert "fehler" not in ok, (rolle, ok)


def test_jeder_admin_vorbehalt_wird_auch_durchgesetzt(client, monkeypatch, tmp_path):
    """Der Vorbehalt steht als Feld am Werkzeug - geprueft wird hier, dass er fuer JEDES
    Werkzeug mit diesem Feld auch greift.

    Vorher deckte kein Test den Moderator-Fall von vt_auswahl_zeigen ab: alle Tests dort
    liefen als Admin. Ein Vorbehalt, der nur beim ersten Werkzeug geprueft wird, ist beim
    zweiten eine Behauptung.
    """
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)
    _katalog(monkeypatch, "WavyWater")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-alle", port=25858)
        person = _nutzer(db, server, rolle="moderator", name="arch-alle-mod")
        _zip(tmp_path / "resourcepacks" / _paketname(server),
             {"Selected Packs.txt": _manifest("WavyWater")})
        zeile = _eintrag(db, server)
        _zip(Path(server.base_path) / "plugins" / "X.jar", {"plugin.yml": "name: X"})

        betroffen = [w for w in aas.WERKZEUGE.values() if w.nur_admin]
        assert betroffen, "kein Werkzeug mit Admin-Vorbehalt - Feld umbenannt?"
        parameter = {"server_id": server.id, "inhalt_id": zeile.id,
                     "pfad": "plugins/X.jar", "hinzufuegen": ["WavyWater"]}
        for werkzeug in betroffen:
            # Der Vorbehalt muss in der Beschreibung stehen (damit das Modell es weiss)
            # UND greifen (damit es nicht nur dasteht).
            assert "Admin" in werkzeug.beschreibung, werkzeug.name
            if werkzeug.lesend:
                ergebnis = aas._fuehre_lesend_aus(db, person, werkzeug.name, parameter)
                assert "fehler" in ergebnis, werkzeug.name
                assert "Admin" in ergebnis["fehler"], werkzeug.name
            else:
                with pytest.raises(aas.AgentFehler, match="Admin"):
                    aas.fuehre_aus(db, user=person, werkzeug_name=werkzeug.name,
                                   parameter=parameter)


def test_archiv_kein_ausbruch_aus_dem_serverordner(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    draussen = _zip(tmp_path / "draussen.zip", {"beute.txt": "DAS DARF NIE AN DIE KI"})
    assert draussen.exists()

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-ausbruch", port=25853)
        person = _nutzer(db, server, name="arch-ausbruch")

        for pfad in ("../draussen.zip", "..\\draussen.zip",
                     "plugins/../../draussen.zip", "C:/Windows/win.ini",
                     "/etc/passwd", "//friedrichnas/FriedrichNAS/geheim.zip"):
            ergebnis = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                              {"server_id": server.id, "pfad": pfad,
                                               "eintrag": "beute.txt"})
            assert "fehler" in ergebnis, f"{pfad} wurde geoeffnet!"
            assert "DARF NIE" not in str(ergebnis)


def test_archiv_das_keins_ist_meldet_sich_sauber(client, tmp_path):
    """server.properties als Archiv zu oeffnen ist ein Irrtum des Modells, kein Absturz
    des Managers - sonst endet das Gespraech statt der Fehlversuch."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-kaputt", port=25854)
        person = _nutzer(db, server, name="arch-kaputt")

        ergebnis = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                          {"server_id": server.id,
                                           "pfad": "server.properties"})
    assert "fehler" in ergebnis
    assert "server.properties" in ergebnis["fehler"]


def test_verfaelschtes_archiv_bleibt_eine_saubere_absage(client, tmp_path):
    """Ein Fuzz ueber verfaelschte Zips lieferte neben BadZipFile und OSError auch
    zlib.error, ValueError und UnicodeDecodeError - Ausnahmen ohne gemeinsame
    Oberklasse. Fuer den Nutzer bedeuten sie alle dasselbe: die Datei ist nicht lesbar.
    """
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-fuzz", port=25859)
        person = _nutzer(db, server, name="arch-fuzz")
        pfad = _zip(Path(server.base_path) / "plugins" / "Kaputt.jar",
                    {"config.yml": "wert: " + "x" * 4000})
        rohdaten = bytearray(pfad.read_bytes())
        # Mitten in den Deflate-Strom greifen; das zentrale Verzeichnis bleibt heil.
        for stelle in range(60, 90):
            rohdaten[stelle] ^= 0xFF
        pfad.write_bytes(bytes(rohdaten))

        ergebnis = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                          {"server_id": server.id,
                                           "pfad": "plugins/Kaputt.jar",
                                           "eintrag": "config.yml"})
    assert "fehler" in ergebnis
    assert "Kaputt.jar konnte nicht gelesen werden" in ergebnis["fehler"]
    assert "Unerwarteter Fehler" not in ergebnis["fehler"]


def test_unbekannter_eintrag_nennt_einen_treffer(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-eintrag", port=25855)
        person = _nutzer(db, server, name="arch-eintrag")
        _zip(Path(server.base_path) / "datapacks" / "Pack.zip",
             {"data/minecraft/tags/blocks/x.json": "{}", "pack.mcmeta": "{}"})

        ergebnis = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                          {"server_id": server.id,
                                           "pfad": "datapacks/Pack.zip",
                                           "eintrag": "x.json"})
    assert "fehler" in ergebnis
    # Der Hinweis soll den Umweg ueber das Inhaltsverzeichnis ersparen.
    assert "data/minecraft/tags/blocks/x.json" in ergebnis["fehler"]


def test_grosser_eintrag_wird_begrenzt_entpackt(client, tmp_path):
    """Die Grenze gilt in BYTES beim Entpacken, nicht in Zeichen am Ende: ein Eintrag aus
    gleichen Zeichen packt sich auf nahezu nichts zusammen, und wer erst ganz entpackt
    und dann kuerzt, laesst das Archiv bestimmen, wie viel Speicher belegt wird.

    Gemessen wird deshalb der Speicher, nicht nur die Laenge der Antwort - sonst besteht
    den Test auch eine Fassung, die 20 MB entpackt und davon 8000 Zeichen zurueckgibt.
    """
    import tracemalloc

    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-bombe", port=25856)
        person = _nutzer(db, server, name="arch-bombe")
        pfad = _zip(Path(server.base_path) / "plugins" / "Bombe.jar",
                    {"gross.txt": "A" * 20_000_000})
        assert pfad.stat().st_size < 100_000  # gepackt winzig, entpackt 20 MB

        tracemalloc.start()
        try:
            ergebnis = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                              {"server_id": server.id,
                                               "pfad": "plugins/Bombe.jar",
                                               "eintrag": "gross.txt"})
            _jetzt, spitze = tracemalloc.get_traced_memory()
        finally:
            tracemalloc.stop()

    assert "fehler" not in ergebnis, ergebnis
    assert ergebnis["gekuerzt"] is True
    assert len(ergebnis["inhalt"]) <= aas.ARCHIV_MAX_ZEICHEN
    # Grosszuegig, aber weit unter den 20 MB des Eintrags.
    assert spitze < 4_000_000, f"Spitze {spitze} Bytes - die Grenze wirkt nicht"


def test_eintrag_mit_fremdem_verfahren_wird_abgewiesen(client, tmp_path):
    """Die Lesegrenze wirkt nur bei STORED und DEFLATE.

    Nachgelesen in zipfile.ZipExtFile._read1: fuer Deflate laeuft decompress(data, n),
    fuer jedes andere Verfahren decompress(data) OHNE Grenze - gefuettert mit 32 KB
    komprimiert. Gemessen ergeben 32 KB bzip2 rund 43 GB in EINEM bytes-Objekt, bevor
    irgendeine Kappung greift. Da die Route im Event-Loop laeuft, friert das nicht die
    Anfrage ein, sondern den ganzen Manager. 7-Zip erzeugt solche Zips auf Knopfdruck,
    und ein Datapack kommt von aussen.
    """
    import tracemalloc

    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-bzip", port=25860)
        person = _nutzer(db, server, name="arch-bzip")
        pfad = _zip(Path(server.base_path) / "plugins" / "Bzip.jar",
                    {"gross.txt": "A" * 50_000_000}, verfahren=zipfile.ZIP_BZIP2)
        assert pfad.stat().st_size < 10_000

        tracemalloc.start()
        try:
            ergebnis = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                              {"server_id": server.id,
                                               "pfad": "plugins/Bzip.jar",
                                               "eintrag": "gross.txt"})
            _jetzt, spitze = tracemalloc.get_traced_memory()
        finally:
            tracemalloc.stop()

        assert "fehler" in ergebnis
        assert "Verfahren" in ergebnis["fehler"]
        assert spitze < 4_000_000, f"Spitze {spitze} Bytes - es wurde doch entpackt"

        # Das Inhaltsverzeichnis geht weiter: nur das LESEN eines solchen Eintrags nicht.
        liste = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                       {"server_id": server.id,
                                        "pfad": "plugins/Bzip.jar"})
    assert "fehler" not in liste, liste
    assert liste["eintraege"] == ["gross.txt"]


def test_binaerer_eintrag_kommt_nicht_als_text(client, tmp_path):
    """Sonst gehen tausende Ersatzzeichen an das Modell - bezahlt pro Zeichen."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-binaer", port=25863)
        person = _nutzer(db, server, name="arch-binaer")
        _zip(Path(server.base_path) / "plugins" / "Binaer.jar",
             {"daten.bin": bytes(range(256)) * 50})

        ergebnis = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                          {"server_id": server.id,
                                           "pfad": "plugins/Binaer.jar",
                                           "eintrag": "daten.bin"})
    assert "fehler" in ergebnis
    assert "keine Textdatei" in ergebnis["fehler"]


def test_ordner_im_archiv_wird_benannt(client, tmp_path):
    """Vorher kam stillschweigend leerer Inhalt zurueck - das sieht aus wie eine leere
    Datei und ist eine falsche Antwort."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-ordner", port=25864)
        person = _nutzer(db, server, name="arch-ordner")
        pfad = Path(server.base_path) / "plugins" / "MitOrdner.jar"
        pfad.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(pfad, "w") as z:
            z.writestr("verzeichnis/", "")
            z.writestr("verzeichnis/x.txt", "inhalt")

        ergebnis = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                          {"server_id": server.id,
                                           "pfad": "plugins/MitOrdner.jar",
                                           "eintrag": "verzeichnis/"})
    assert "fehler" in ergebnis
    assert "Ordner im Archiv" in ergebnis["fehler"]


def test_zugangsdaten_verlassen_das_werkzeugergebnis_nicht(client, tmp_path):
    """Die Zusage des Projekts lautet: unabhaengig von allen Einstellungen verlassen
    Zugangsdaten das Haus nicht.

    Sie galt fuer den Rohtext, nicht fuer das Werkzeugergebnis: das wird als EINE
    JSON-Zeile verpackt, und die Geheimnis-Muster haengen an (?im)^. Ein Zeilenanfang
    kommt in einem einzeiligen Blob nicht vor - die Umbrueche sind dort die zwei Zeichen
    Backslash und n. Gemessen wurde 'password: "hunter2"' im Rohtext entfernt und im
    Blob nicht. Genau das Werkzeug, dessen Admin-Vorbehalt mit "in einem Archiv kann
    eine Konfiguration mit Zugangsdaten liegen" begruendet ist, lieferte sie also im
    Klartext an den externen Anbieter.
    """
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    config = ('mysql:\n  password: "hunter2"\n'
              "forwarding-secret: 'abc12345'\n"
              "rcon.password=unquotiert\n"
              "server-ip: 203.0.113.7\n")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="arch-geheim", port=25865)
        person = _nutzer(db, server, name="arch-geheim")
        _zip(Path(server.base_path) / "plugins" / "Mit.jar", {"config.yml": config})

        ergebnis = aas._fuehre_lesend_aus(db, person, "archiv_ansehen",
                                          {"server_id": server.id,
                                           "pfad": "plugins/Mit.jar",
                                           "eintrag": "config.yml"})
        assert "fehler" not in ergebnis, ergebnis
        versandfertig = aas._ergebnis_text(ergebnis)

    for geheim in ("hunter2", "abc12345", "unquotiert", "203.0.113.7"):
        assert geheim not in versandfertig, f"{geheim} ging mit hinaus"
    assert "<entfernt>" in versandfertig
    # Und der Rest der Datei bleibt lesbar: ein zu gieriges Muster frisst sonst alles
    # bis zum naechsten Anfuehrungszeichen und damit den halben Dateiinhalt.
    assert "mysql" in versandfertig and "forwarding-secret" in versandfertig


# ------------------------------------------------------------ vt_auswahl_zeigen

def test_teilpakete_eines_gehosteten_resourcepacks_werden_sichtbar(
        client, monkeypatch, tmp_path):
    """Der eigentliche Anlass: die Liste steht im Archiv, und das Archiv liegt in
    data/resourcepacks - also ausserhalb des Serverordners."""
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)
    _katalog(monkeypatch, "WavyWater", "LowerFire", "AlternateBlockDestruction")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-zeigen", port=25861)
        person = _nutzer(db, server, name="vt-zeigen")
        _zip(tmp_path / "resourcepacks" / _paketname(server),
             {"Selected Packs.txt": _manifest("WavyWater", "LowerFire"),
              "pack.mcmeta": "{}"})
        zeile = _eintrag(db, server)

        ergebnis = aas._fuehre_lesend_aus(db, person, "vt_auswahl_zeigen",
                                          {"server_id": server.id,
                                           "inhalt_id": zeile.id})

    assert "fehler" not in ergebnis, ergebnis
    assert ergebnis["gewaehlt"] == ["WavyWater", "LowerFire"]
    assert ergebnis["unbekannt"] == []
    assert ergebnis["lesbar"] is True
    assert "AlternateBlockDestruction" in ergebnis["verfuegbar"]["Aesthetic"][2]["name"]


def test_teilpaket_aus_einer_unterkategorie_gilt_als_bekannt(client, monkeypatch,
                                                             tmp_path):
    """Der Resource-Pack-Katalog ist nicht flach: 5 der 14 Hauptkategorien fuehren eigene
    Unterkategorien. Gemessen lagen 329 Packs oben und 343 darunter - wer nur die oberste
    Ebene liest, haelt also die HAELFTE aller Teilpakete fuer unbekannt. Folge: die
    Auswahl liess sich gar nicht mehr aendern, und der naheliegende Ausweg des Modells
    ("dann nimm es raus") hat ein gueltiges Pack endgueltig verloren.
    """
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)
    _katalog(monkeypatch, "WavyWater",
             unterkategorie={"More Zombies": {"MZSteve": "Steve Zombies"}})

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-unter", port=25866)
        person = _nutzer(db, server, name="vt-unter")
        _zip(tmp_path / "resourcepacks" / _paketname(server),
             {"Selected Packs.txt": _manifest("WavyWater", "MZSteve")})
        zeile = _eintrag(db, server)

        ergebnis = aas._fuehre_lesend_aus(db, person, "vt_auswahl_zeigen",
                                          {"server_id": server.id,
                                           "inhalt_id": zeile.id})

    assert "fehler" not in ergebnis, ergebnis
    assert ergebnis["unbekannt"] == [], "Unterkategorie gilt wieder als unbekannt"
    assert "More Zombies" in ergebnis["verfuegbar"]


def test_fehlender_katalog_verdeckt_die_auswahl_nicht(client, monkeypatch, tmp_path):
    """Die Auswahl steht in der Datei; der Katalog sagt nur, was es sonst noch gibt. Ein
    Netzfehler darf also nicht die Antwort auf die gestellte Frage verschlucken - aber er
    muss gesagt werden, sonst sieht 'unbekannt: []' wie 'alles in Ordnung' aus."""
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas
    from app.services import vanillatweaks_service as vt

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)
    monkeypatch.setattr(vt, "resolve_vt_version", lambda mc_version: "26.3")

    def _kaputt(pack_type, version):
        raise RuntimeError("VanillaTweaks nicht erreichbar")

    monkeypatch.setattr(vt, "list_categories", _kaputt)

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-katalog", port=25862)
        person = _nutzer(db, server, name="vt-katalog")
        _zip(tmp_path / "resourcepacks" / _paketname(server),
             {"Selected Packs.txt": _manifest("WavyWater")})
        zeile = _eintrag(db, server)

        ergebnis = aas._fuehre_lesend_aus(db, person, "vt_auswahl_zeigen",
                                          {"server_id": server.id,
                                           "inhalt_id": zeile.id})

    assert "fehler" not in ergebnis, ergebnis
    assert ergebnis["gewaehlt"] == ["WavyWater"]
    assert ergebnis["lesbar"] is True
    assert "nicht erreichbar" in ergebnis["verfuegbar_fehler"]
    assert "nicht erreichbar" in ergebnis.get("hinweis", "")


def test_fremder_inhalt_zaehlt_nicht(client, monkeypatch, tmp_path):
    """Die ID allein genuegt nicht. Sonst liesse sich ueber einen Server, den man
    steuern darf, das Paket eines anderen anfassen."""
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)
    _katalog(monkeypatch, "WavyWater")

    with SessionLocal() as db:
        meiner = _server(db, tmp_path, slug="vt-meiner", port=25863)
        anderer = _server(db, tmp_path, slug="vt-anderer", port=25864)
        person = _nutzer(db, meiner, name="vt-fremd")
        _zip(tmp_path / "resourcepacks" / _paketname(anderer),
             {"Selected Packs.txt": _manifest("WavyWater")})
        fremd = _eintrag(db, anderer)

        ergebnis = aas._fuehre_lesend_aus(db, person, "vt_auswahl_zeigen",
                                          {"server_id": meiner.id,
                                           "inhalt_id": fremd.id})
    assert "fehler" in ergebnis
    assert "gibt es auf" in ergebnis["fehler"]


def test_gewoehnliches_plugin_hat_keine_teilpakete(client, tmp_path):
    """Und die Absage soll sagen, wo es die gibt - sonst probiert das Modell es mit jeder
    weiteren Inhalts-ID noch einmal."""
    from app.db.session import SessionLocal
    from app.models.installed_content import InstalledContent
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-plugin", port=25865)
        person = _nutzer(db, server, name="vt-plugin")
        zeile = InstalledContent(server_id=server.id, provider_name="modrinth",
                                 content_type="plugin", external_project_id="abc123",
                                 external_version_id="v1", name="EssentialsX",
                                 version_label="2.20", file_name="EssentialsX.jar")
        db.add(zeile)
        db.commit()
        db.refresh(zeile)

        ergebnis = aas._fuehre_lesend_aus(db, person, "vt_auswahl_zeigen",
                                          {"server_id": server.id,
                                           "inhalt_id": zeile.id})
    assert "fehler" in ergebnis
    assert "Crafting Tweaks" in ergebnis["fehler"]


def test_fremdes_paket_in_der_url_zaehlt_nicht_als_eigenes(client, monkeypatch, tmp_path):
    """Steht im Eintrag kein Dateiname, wurde er aus der resource-pack-URL abgeleitet -
    und die kann auf irgendetwas zeigen. 'C:fremd.zip' enthaelt ausserdem keinen Trenner
    und ist unter Windows trotzdem ein laufwerksrelativer Pfad: Path(ordner) / 'C:x.zip'
    ergibt auf einer UNC-Installation schlicht 'C:x.zip'.
    """
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import server_service
    from app.services import vanillatweaks_service as vt

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-fremdurl", port=25867)
        zeile = _eintrag(db, server)
        zeile.file_name = ""
        db.commit()

        (tmp_path / "resourcepacks").mkdir(parents=True, exist_ok=True)
        (tmp_path / "resourcepacks" / "fremd.zip").write_bytes(b"PK\x03\x04")
        for url in ("https://dropbox.example/fremd.zip", "C:fremd.zip",
                    "https://x/vt_999_abc.zip"):
            server_service._upsert_server_property(server, "resource-pack", url)
            assert vt.auswahl_datei(server, zeile) is None, url

        # Der eigene Name geht - daran haengt der normale Weg.
        eigen = _paketname(server)
        (tmp_path / "resourcepacks" / eigen).write_bytes(b"PK\x03\x04")
        server_service._upsert_server_property(server, "resource-pack",
                                               f"https://x/{eigen}")
        assert vt.auswahl_datei(server, zeile).name == eigen


# ------------------------------------------------------------ vt_auswahl_setzen

def test_auswahl_setzen_rechnet_hinzu_und_weg(client, monkeypatch, tmp_path):
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas
    from app.services import vanillatweaks_service as vt

    einstellungen = get_settings()
    monkeypatch.setattr(einstellungen, "data_dir", tmp_path)
    monkeypatch.setattr(einstellungen, "public_base_url", "http://beispiel.invalid:8000")
    _katalog(monkeypatch, "WavyWater", "LowerFire", "CleanGlass")
    gesehen: list = []
    monkeypatch.setattr(vt, "generate_zip",
                        lambda pack_type, version, selection:
                        gesehen.append(selection) or b"PACKDATEN")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-setzen", port=25871)
        person = _nutzer(db, server, name="vt-setzen")
        _zip(tmp_path / "resourcepacks" / _paketname(server),
             {"Selected Packs.txt": _manifest("WavyWater", "LowerFire")})
        zeile = _eintrag(db, server)

        parameter = {"server_id": server.id, "inhalt_id": zeile.id,
                     "hinzufuegen": ["CleanGlass"], "entfernen": ["LowerFire"]}

        # Was im Bestaetigungsdialog steht, muss die Tat beschreiben, die folgt.
        satz = aas.WERKZEUGE["vt_auswahl_setzen"].beschreibe(db, server, parameter)
        assert "2 Teilpaket" in satz
        assert "CleanGlass" in satz and "LowerFire" in satz

        ergebnis = aas.fuehre_aus(db, user=person,
                                  werkzeug_name="vt_auswahl_setzen",
                                  parameter=parameter)

    assert ergebnis["gewaehlt"] == ["WavyWater", "CleanGlass"]
    assert gesehen == [{"Aesthetic": ["WavyWater", "CleanGlass"]}]


def test_anzeigename_entfernt_das_richtige_teilpaket(client, monkeypatch, tmp_path):
    """Das Manifest fuehrt interne Namen ("WavyWater"), Nutzer und Katalog-Anzeige den
    Namen mit Leerzeichen ("Wavy Water"). Bei Resource Packs weichen beide in 670 von
    672 Faellen ab. Ein Vergleich per .lower() entfernte deshalb nichts - und der Satz
    im Dialog sagte trotzdem, es sei entfernt.
    """
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas
    from app.services import vanillatweaks_service as vt

    einstellungen = get_settings()
    monkeypatch.setattr(einstellungen, "data_dir", tmp_path)
    monkeypatch.setattr(einstellungen, "public_base_url", "http://beispiel.invalid:8000")
    monkeypatch.setattr(vt, "list_categories", lambda pack_type, version: [
        {"category": "Aesthetic",
         "packs": [{"name": "WavyWater", "display": "Wavy Water"},
                   {"name": "LowerFire", "display": "Lower Fire"}]}])
    monkeypatch.setattr(vt, "resolve_vt_version", lambda mc_version: "26.3")
    gesehen: list = []
    monkeypatch.setattr(vt, "generate_zip",
                        lambda pack_type, version, selection:
                        gesehen.append(selection) or b"PACKDATEN")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-anzeige", port=25872)
        person = _nutzer(db, server, name="vt-anzeige")
        _zip(tmp_path / "resourcepacks" / _paketname(server),
             {"Selected Packs.txt": _manifest("WavyWater", "LowerFire")})
        zeile = _eintrag(db, server)
        parameter = {"server_id": server.id, "inhalt_id": zeile.id,
                     "entfernen": ["Wavy Water"]}

        satz = aas.WERKZEUGE["vt_auswahl_setzen"].beschreibe(db, server, parameter)
        assert "1 Teilpaket" in satz, satz
        ergebnis = aas.fuehre_aus(db, user=person, werkzeug_name="vt_auswahl_setzen",
                                  parameter=parameter)

    assert ergebnis["gewaehlt"] == ["LowerFire"]
    assert gesehen == [{"Aesthetic": ["LowerFire"]}]


def test_unlesbare_grundlage_blockt_das_hinzufuegen(client, monkeypatch, tmp_path):
    """"Keine Auswahl" und "Auswahl nicht lesbar" sehen gleich aus und bedeuten das
    Gegenteil: auf einer unlesbaren Grundlage ERSETZT ein Hinzufuegen die ganze
    Zusammenstellung. Aus 21 Teilpaketen wird dann eines - und die Nachwirkung des
    Werkzeugs sagt selbst, dass das nur aus einer Sicherung zurueckzuholen ist.
    """
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas
    from app.services import vanillatweaks_service as vt

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)
    _katalog(monkeypatch, "WavyWater", "CleanGlass")
    monkeypatch.setattr(vt, "generate_zip",
                        lambda *a, **k: pytest.fail("haette nichts erzeugen duerfen"))

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-unlesbar", port=25873)
        person = _nutzer(db, server, name="vt-unlesbar")
        # Archiv ohne Auswahlliste - genau der Fall, der wie "leer" aussah.
        _zip(tmp_path / "resourcepacks" / _paketname(server), {"pack.mcmeta": "{}"})
        zeile = _eintrag(db, server)

        stand = aas._fuehre_lesend_aus(db, person, "vt_auswahl_zeigen",
                                       {"server_id": server.id, "inhalt_id": zeile.id})
        assert stand["lesbar"] is False
        assert "Selected Packs.txt" in stand["hinweis"]

        with pytest.raises(aas.AgentFehler, match="nicht lesen"):
            aas.fuehre_aus(db, user=person, werkzeug_name="vt_auswahl_setzen",
                           parameter={"server_id": server.id, "inhalt_id": zeile.id,
                                      "hinzufuegen": ["CleanGlass"]})


def test_auswahl_setzen_nur_fuer_admins(client, monkeypatch, tmp_path):
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas
    from app.services import vanillatweaks_service as vt

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)
    _katalog(monkeypatch, "WavyWater", "CleanGlass")
    monkeypatch.setattr(vt, "generate_zip",
                        lambda *a, **k: pytest.fail("haette nichts erzeugen duerfen"))

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-mod", port=25874)
        person = _nutzer(db, server, rolle="moderator", name="vt-mod")
        _zip(tmp_path / "resourcepacks" / _paketname(server),
             {"Selected Packs.txt": _manifest("WavyWater")})
        zeile = _eintrag(db, server)

        with pytest.raises(aas.AgentFehler, match="Admin"):
            aas.fuehre_aus(db, user=person, werkzeug_name="vt_auswahl_setzen",
                           parameter={"server_id": server.id, "inhalt_id": zeile.id,
                                      "hinzufuegen": ["CleanGlass"]})


def test_unbekanntes_teilpaket_wird_benannt(client, monkeypatch, tmp_path):
    """Ein erfundener Name darf nicht stillschweigend ein Paket OHNE ihn erzeugen - sonst
    verschwindet die halbe Auswahl, weil das Modell sich bei einem Namen vertippt hat."""
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas
    from app.services import vanillatweaks_service as vt

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)
    _katalog(monkeypatch, "WavyWater")
    monkeypatch.setattr(vt, "generate_zip",
                        lambda *a, **k: pytest.fail("haette nichts erzeugen duerfen"))

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-tippfehler", port=25875)
        person = _nutzer(db, server, name="vt-tippfehler")
        _zip(tmp_path / "resourcepacks" / _paketname(server),
             {"Selected Packs.txt": _manifest("WavyWater")})
        zeile = _eintrag(db, server)

        with pytest.raises(aas.AgentFehler, match="WavyWatr"):
            aas.fuehre_aus(db, user=person, werkzeug_name="vt_auswahl_setzen",
                           parameter={"server_id": server.id, "inhalt_id": zeile.id,
                                      "hinzufuegen": ["WavyWatr"]})


def test_leere_auswahl_wird_abgewiesen(client, monkeypatch, tmp_path):
    """Alles abwaehlen ist keine Auswahl, sondern ein Entfernen - und das macht
    inhalt_entfernen, mit eigener Warnung."""
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas
    from app.services import vanillatweaks_service as vt

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)
    _katalog(monkeypatch, "WavyWater")
    monkeypatch.setattr(vt, "generate_zip",
                        lambda *a, **k: pytest.fail("haette nichts erzeugen duerfen"))

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-leer", port=25876)
        person = _nutzer(db, server, name="vt-leer")
        _zip(tmp_path / "resourcepacks" / _paketname(server),
             {"Selected Packs.txt": _manifest("WavyWater")})
        zeile = _eintrag(db, server)

        with pytest.raises(aas.AgentFehler, match="entfernen"):
            aas.fuehre_aus(db, user=person, werkzeug_name="vt_auswahl_setzen",
                           parameter={"server_id": server.id, "inhalt_id": zeile.id,
                                      "entfernen": ["WavyWater"]})


def test_namen_als_fliesstext_werden_verstanden(client, monkeypatch, tmp_path):
    """Das Modell schickt manchmal eine Liste und manchmal "A, B" in einem Feld. Ein
    Formatfehler soll keine falsche Auswahl erzeugen."""
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)
    _katalog(monkeypatch, "WavyWater", "LowerFire", "CleanGlass")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-text", port=25877)
        _zip(tmp_path / "resourcepacks" / _paketname(server),
             {"Selected Packs.txt": _manifest("WavyWater")})
        zeile = _eintrag(db, server)

        _eintr, namen = aas._vt_neue_auswahl(
            db, server, {"inhalt_id": zeile.id,
                         "hinzufuegen": "CleanGlass, LowerFire"})
    assert namen == ["WavyWater", "CleanGlass", "LowerFire"]


def test_doppelter_name_bleibt_einmal(client, monkeypatch, tmp_path):
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)
    _katalog(monkeypatch, "WavyWater")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-doppelt", port=25878)
        _zip(tmp_path / "resourcepacks" / _paketname(server),
             {"Selected Packs.txt": _manifest("WavyWater")})
        zeile = _eintrag(db, server)

        # Beide Schreibweisen desselben Packs: klein geschrieben UND mit Leerzeichen.
        _eintr, namen = aas._vt_neue_auswahl(
            db, server, {"inhalt_id": zeile.id,
                         "hinzufuegen": ["wavywater", "Wavy Water"]})
    assert namen == ["WavyWater"]


def test_fehler_wird_hindernis_nicht_tatbeschreibung(client, monkeypatch, tmp_path):
    """Vorher fing die Beschreibung den Fehler ab und gab ihn als TAT zurueck. Der Dialog
    titelte dann "Vorschlag", setzte den Fehlersatz als das ein, was passieren soll, und
    bot "Ausfuehren" aktiv an.
    """
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)
    _katalog(monkeypatch, "WavyWater")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-hindernis", port=25879)
        person = _nutzer(db, server, name="vt-hindernis")

        vorschlag = aas._baue_vorschlag(db, person, {
            "name": "vt_auswahl_setzen", "id": "tu_1",
            "eingabe": {"server_id": server.id, "inhalt_id": 9999,
                        "hinzufuegen": ["WavyWater"]}})

    assert vorschlag["moeglich"] is False
    assert "9999" in vorschlag["hindernis"]
    # Und die Beschreibung bleibt die des WERKZEUGS, nicht der Fehlersatz.
    assert "Teilpakete" in vorschlag["beschreibung"]


def test_vorschlag_sagt_dem_moderator_vorher_dass_es_nicht_geht(client, monkeypatch,
                                                                tmp_path):
    """Der Vorbehalt muss am Vorschlag haengen, nicht erst an der Ausfuehrung: sonst
    liest der Moderator eine Bestaetigungsfrage, die ohnehin ins Leere fuehrt."""
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    monkeypatch.setattr(get_settings(), "data_dir", tmp_path)
    _katalog(monkeypatch, "WavyWater", "CleanGlass")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-vorschlag", port=25880)
        person = _nutzer(db, server, rolle="moderator", name="vt-vorschlag")
        _zip(tmp_path / "resourcepacks" / _paketname(server),
             {"Selected Packs.txt": _manifest("WavyWater")})
        zeile = _eintrag(db, server)

        vorschlag = aas._baue_vorschlag(db, person, {
            "name": "vt_auswahl_setzen", "id": "tu_1",
            "eingabe": {"server_id": server.id, "inhalt_id": zeile.id,
                        "hinzufuegen": ["CleanGlass"]}})

    assert vorschlag["moeglich"] is False
    assert "Admin" in vorschlag["hindernis"]


def test_dateiname_des_gehosteten_pakets_wird_gespeichert(client, monkeypatch, tmp_path):
    """Die Zuordnung Eintrag -> Datei darf nicht an der URL in server.properties haengen:
    die aendert sich, sobald die oeffentliche Adresse des Managers wechselt - und dann
    waere nicht mehr zu sagen, welche Datei zu diesem Eintrag gehoert."""
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import server_service
    from app.services import vanillatweaks_service as vt

    einstellungen = get_settings()
    monkeypatch.setattr(einstellungen, "data_dir", tmp_path)
    monkeypatch.setattr(einstellungen, "public_base_url", "http://beispiel.invalid:8000")
    _katalog(monkeypatch, "WavyWater")
    monkeypatch.setattr(vt, "generate_zip", lambda *a, **k: b"PACKDATEN")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-datei", port=25881)
        eintrag = vt.install_resourcepack(db, server, {"Aesthetic": ["WavyWater"]}, None)
        assert eintrag.file_name.startswith(f"vt_{server.id}_")

        server_service._upsert_server_property(server, "resource-pack", "")
        datei = vt.auswahl_datei(server, eintrag)
    assert datei is not None and datei.name == eintrag.file_name


# ------------------------------------------------------------- Crafting Tweaks

def test_crafting_tweaks_auswahl_lesen_und_setzen(client, monkeypatch, tmp_path):
    """Die zweite Haelfte der Funktion hatte keinen einzigen Test - und sie laeuft ganz
    anders als das Resource Pack: die Datei liegt IM Serverordner, wird ersetzt statt neu
    angelegt, und der Eintrag behaelt seine ID."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas
    from app.services import content_service
    from app.services import vanillatweaks_service as vt

    _katalog(monkeypatch, "DoubleShulker", "Coarse2Dirt", "UnpackNetherite")
    gesehen: list = []
    monkeypatch.setattr(vt, "generate_zip",
                        lambda pack_type, version, selection:
                        gesehen.append((pack_type, selection)) or b"CTDATEN")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-ct", port=25882)
        person = _nutzer(db, server, name="vt-ct")
        name = content_service._safe_file_name("VanillaTweaks Crafting Tweaks.zip")
        zeile = _eintrag(db, server, typ="vt:craftingtweaks", datei=name,
                         name="VanillaTweaks Crafting Tweaks")
        pfad = content_service._content_file_path(server, "datapack", name)
        _zip(pfad, {"Selected Packs.txt": ("VanillaTweaks Crafting Tweaks\n"
                                           "Version: 26.3\nPacks:\n"
                                           "DoubleShulker\nCoarse2Dirt\n")})

        stand = aas._fuehre_lesend_aus(db, person, "vt_auswahl_zeigen",
                                       {"server_id": server.id, "inhalt_id": zeile.id})
        assert "fehler" not in stand, stand
        assert stand["typ"] == "craftingtweaks"
        assert stand["gewaehlt"] == ["DoubleShulker", "Coarse2Dirt"]
        assert stand["lesbar"] is True

        ergebnis = aas.fuehre_aus(db, user=person, werkzeug_name="vt_auswahl_setzen",
                                  parameter={"server_id": server.id,
                                             "inhalt_id": zeile.id,
                                             "hinzufuegen": ["UnpackNetherite"]})
        assert ergebnis["gewaehlt"] == ["DoubleShulker", "Coarse2Dirt", "UnpackNetherite"]
        assert gesehen and gesehen[0][0] == "craftingtweaks"
        assert pfad.read_bytes() == b"CTDATEN"
        # Die Zeile bleibt dieselbe - anders als beim Resource Pack.
        db.refresh(zeile)
        assert zeile.file_name == name


def test_zweiter_ct_eintrag_wird_nicht_still_ueberschrieben(client, monkeypatch,
                                                            tmp_path):
    """Alle Crafting Tweaks eines Servers liegen in EINER Datei mit festem Namen. Gibt es
    dafuer zwei Eintraege, wuerde das Schreiben die Auswahl des anderen ueberschreiben,
    ohne dass es irgendwo auffaellt."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas
    from app.services import content_service
    from app.services import vanillatweaks_service as vt

    _katalog(monkeypatch, "DoubleShulker", "Coarse2Dirt")
    monkeypatch.setattr(vt, "generate_zip", lambda *a, **k: b"CTDATEN")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-ct2", port=25883)
        person = _nutzer(db, server, name="vt-ct2")
        name = content_service._safe_file_name("VanillaTweaks Crafting Tweaks.zip")
        erster = _eintrag(db, server, typ="vt:craftingtweaks", datei=name,
                          name="Crafting Tweaks A")
        _eintrag(db, server, typ="vt:craftingtweaks", datei=name,
                 name="Crafting Tweaks B")
        pfad = content_service._content_file_path(server, "datapack", name)
        _zip(pfad, {"Selected Packs.txt": ("VanillaTweaks Crafting Tweaks\n"
                                           "Version: 26.3\nPacks:\nDoubleShulker\n")})

        with pytest.raises(aas.AgentFehler, match="zweiten Eintrag"):
            aas.fuehre_aus(db, user=person, werkzeug_name="vt_auswahl_setzen",
                           parameter={"server_id": server.id, "inhalt_id": erster.id,
                                      "hinzufuegen": ["Coarse2Dirt"]})
