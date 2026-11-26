"""Die Plugin-Datenbank in der Sicherung - und der Nachweis, dass sie zurueckkommt.

WAS BIS ZUM 26.11.2026 FEHLTE: Der Sicherungsdienst zippte Dateien aus dem Serverordner.
Die Plugin-Datenbank laeuft als MariaDB-Dienst und liegt nicht dort - Parzellen, Guthaben,
Laeden, Freistaedte, Inseln, Strafen, Meldungen, Teamrollen, Mitgliedschaften und das
Blockbuch lagen damit in KEINER Sicherung.

ZWEI SORTEN PROBEN STEHEN HIER, und die Teilung ist wichtig:

  * Die meisten brauchen keine Datenbank. Sie halten die Entscheidungen fest - dass der
    Abzug kein ``CREATE DATABASE`` enthaelt, dass das Kennwort nicht auf die
    Kommandozeile kommt, dass ein Fehlschlag die Dateisicherung GELINGEN laesst und
    trotzdem sichtbar ist.
  * Eine braucht eine echte Anlage: ``test_ein_abzug_kommt_wirklich_zurueck``. Sie ist
    die einzige, die beweist, worauf es ankommt - **dass der Abzug sich EINSPIELEN
    laesst**. Ein Abzug, der nicht zurueckkommt, ist keine Sicherung, sondern eine Datei.
    Ohne Anlage wird sie uebersprungen; der Rest laeuft immer.

DIE ECHTE PROBE BERUEHRT DIE BETRIEBSDATEN NIE. Sie legt ein eigenes Schema mit eigenem
Benutzer und eigenem Kennwort an und zeigt dem Dienst eine EIGENE Properties-Datei. Das
Kennwort der Anlage des Betreibers kommt in dieser Datei nicht vor.
"""
from __future__ import annotations

import socket
import subprocess
import zipfile
from pathlib import Path

import pytest

# Ein eigener Namensraum fuer die Probeschemata. Das Suffix _test ist Pflicht: Der
# Dienst weigert sich, in ein Schema einzuspielen, dessen Name nicht darauf endet.
PROBE_SCHEMA = "mcm_abzugprobe"
PROBE_SCHEMA_TEST = "mcm_abzugprobe_test"
PROBE_BENUTZER = "mcm_abzugprobe"
PROBE_KENNWORT = "nur-fuer-die-probe-28-zeichen"
PROBE_TABELLE = "probezeilen"


# --------------------------------------------------------------- ohne Datenbank

def test_der_abzug_enthaelt_kein_create_database():
    """DIE TRAGENDE ENTSCHEIDUNG DIESES MODULS, als Quelltextzusage.

    Mit ``--databases`` traegt ein Abzug den Namen seines Schemas in sich, und ein
    Einspielen landet IMMER dort. Die Wiederherstellungsprobe haette damit das
    BETRIEBSSCHEMA ueberschrieben - also genau den Schaden angerichtet, gegen den die
    Sicherung gebaut ist.

    Geprueft wird der Quelltext und nicht ein Abzug: Ein Abzug braucht eine Anlage, und
    diese Zusage soll in jedem Lauf gehalten werden - auch auf einem Rechner ohne
    Datenbank.
    """
    quelle = Path("app/services/datenbanksicherung_service.py").read_text(
        encoding="utf-8")

    # NUR DER CODE, OHNE DIE DOKUMENTATION - und das hat diese Probe selbst
    # gelernt: In ihrer ersten Fassung las sie den ganzen Rumpf und fand
    # "--databases" im Javadoc, das die Abwesenheit der Option BEGRUENDET. Eine
    # Tafel, die ihre eigene Erklaerung liest, ist rot, solange sie erklaert wird.
    #
    # Geschaerft wird das MUSTER und nicht die Datei ausgenommen: Der Docstring
    # faellt heraus, der Code bleibt ganz drin.
    anfang = quelle.index("def schreibe_abzug")
    ende = quelle.index("def einspielbefehl")
    ganz = quelle[anfang:ende]
    # chr(34) * 3 und nicht drei Anfuehrungszeichen: Die stuenden in dieser
    # Datei selbst und beendeten den Docstring dieser Probe.
    dreifach = chr(34) * 3
    erstes = ganz.index(dreifach)
    zweites = ganz.index(dreifach, erstes + 3)
    rumpf = ganz[:erstes] + ganz[zweites + 3:]

    assert "--single-transaction" in rumpf, (
        "ohne diesen Satz prueft die Probe nichts mehr - dann hat sie den Rumpf"
        " nicht gefunden")

    assert "--databases" not in rumpf, (
        "Mit --databases traegt der Abzug seinen Schemanamen in sich, und ein"
        " Einspielen landet IMMER dort - auch wenn ein Testschema gemeint war.")
    assert "--add-drop-table" in rumpf, (
        "ohne sie scheitert das Einspielen an der ersten vorhandenen Tabelle - also"
        " genau dann, wenn man es braucht")


def test_das_kennwort_kommt_nie_auf_die_kommandozeile():
    """Ein ``-p<kennwort>`` ist fuer jeden sichtbar, der die Prozessliste liest.

    Und bei einem Dienst, der laufend sichert, steht es dort laufend. Gereicht wird es
    ueber eine ``--defaults-extra-file``, die unmittelbar danach geloescht wird.

    Geprueft wird die GESTALT im Quelltext: Eine Verhaltensprobe koennte nur nachsehen,
    was ankommt - nicht, was daneben in der Prozessliste stand.
    """
    quelle = Path("app/services/datenbanksicherung_service.py").read_text(
        encoding="utf-8")

    for verboten in ('"-p" +', "'-p' +", '"--password=" +', '"-p{', 'f"-p'):
        assert verboten not in quelle, (
            "Das Kennwort darf nicht in ein Befehlswort wandern: " + verboten)
    assert "--defaults-extra-file=" in quelle
    # Und die Datei muss WEG, auch wenn der Aufruf wirft.
    anfang = quelle.index("def _lauf(")
    rumpf = quelle[anfang:quelle.index("class _nichts")]
    assert "finally:" in rumpf and "datei.unlink()" in rumpf, (
        "eine liegengebliebene Kennwortdatei ist genau das, was dieser Umweg"
        " vermeiden soll")


def test_die_einspielzeile_nennt_kein_kennwort(monkeypatch, tmp_path):
    """``-p`` ohne Wert: Das Kennwort wird abgefragt.

    Eine Zeile mit Kennwort landet in der Zwischenablage und in der
    Befehlsgeschichte - und von dort bekommt man sie nicht mehr heraus.
    """
    _zeige_auf_eigene_datei(monkeypatch, tmp_path)
    from app.services import datenbanksicherung_service as d

    zeile = d.einspielbefehl("_manager/freimarken-datenbank.sql")

    assert PROBE_SCHEMA in zeile
    assert PROBE_KENNWORT not in zeile, "das Kennwort gehoert NICHT in die Zeile"
    assert " -p " in zeile or zeile.endswith(" -p"), (
        "-p OHNE Wert - dann fragt das Werkzeug danach: " + zeile)


def test_ohne_zugangsdaten_gibt_es_nichts_zu_sichern(monkeypatch, tmp_path):
    """Kein Wurf, sondern ein Satz: Ein Server ohne Plugin-Datenbank ist normal."""
    from app.services import datenbanksicherung_service as d
    from app.services import freimarken_einrichtung_service as e

    monkeypatch.setattr(e, "PFAD_ZUGANG", tmp_path / "gibtesnicht.properties")

    gelungen, meldung = d.schreibe_abzug(tmp_path / "abzug.sql")

    assert gelungen is False
    assert "nichts zu sichern" in meldung
    assert not (tmp_path / "abzug.sql").exists(), (
        "eine halbe Datei ist schlimmer als keine - sie sieht wie eine Sicherung aus")


def test_das_testschema_kommt_aus_einem_EIGENEN_weg(monkeypatch, tmp_path):
    """``lies_zugangsdaten`` gibt ``url.test`` mit Absicht NICHT heraus.

    Der Riegel steht dort mit Begruendung: "Das Testschema leeren die Java-Proben vor
    jedem Lauf. Wer dort hineinzeigt, laesst den Betrieb auf einer Anlage laufen, die
    beim naechsten Probelauf leer ist." Diese Probe haelt beide Richtungen - den Riegel
    UND den benannten Weg daran vorbei.
    """
    _zeige_auf_eigene_datei(monkeypatch, tmp_path)
    from app.services import freimarken_einrichtung_service as e

    betrieb = e.lies_zugangsdaten()
    test = e.lies_testadresse()

    assert PROBE_SCHEMA_TEST not in betrieb.get("url", ""), (
        "lies_zugangsdaten darf die Testadresse nicht herausgeben")
    assert "url.test" not in betrieb, "und ihren Schluessel auch nicht"
    assert PROBE_SCHEMA_TEST in test["url"], (
        "lies_testadresse nennt im Namen, was sie liefert - und liefert es")


def test_eingespielt_wird_nur_in_ein_schema_auf_test(monkeypatch, tmp_path):
    """DER RIEGEL AM NAMEN. Diese Funktion kann den Betrieb nicht ueberschreiben.

    Eine allgemeine Einspielfunktion waere ein Weg, mit einem falschen Argument die
    Spielerdaten aller Server zu loeschen - und sie wuerde genau einmal falsch
    aufgerufen. Hier kommt das Ziel aus ``url.test``, und wenn DAS nicht auf ``_test``
    endet, wird abgesagt.
    """
    datei = tmp_path / "datenbank.properties"
    # Die Testadresse zeigt auf das BETRIEBSSCHEMA - der Fall, den der Riegel faengt.
    datei.write_text(
        "url=jdbc:mariadb://127.0.0.1:3306/" + PROBE_SCHEMA + "\n"
        "url.test=jdbc:mariadb://127.0.0.1:3306/" + PROBE_SCHEMA + "\n"
        "benutzer=" + PROBE_BENUTZER + "\n"
        "passwort=" + PROBE_KENNWORT + "\n", encoding="utf-8", newline="\n")
    from app.services import datenbanksicherung_service as d
    from app.services import freimarken_einrichtung_service as e

    monkeypatch.setattr(e, "PFAD_ZUGANG", datei)
    abzug = tmp_path / "abzug.sql"
    abzug.write_text("SELECT 1;\n", encoding="utf-8", newline="\n")

    gelungen, meldung = d.spiele_ein_ins_testschema(abzug)

    assert gelungen is False
    assert "nicht auf _test" in meldung, meldung
    assert PROBE_SCHEMA in meldung, "der Satz muss das Schema NENNEN"


def test_eine_gesetzte_aber_falsche_umgebungsvariable_gibt_nichts(monkeypatch):
    """Wer sie setzt, soll den Fehler sehen - nicht ein anderes Werkzeug bekommen.

    Ohne diese Richtung fiele der Dienst auf die PATH-Suche zurueck und zoege den Abzug
    mit einem Werkzeug, das der Betreiber nicht gemeint hat.
    """
    from app.services import datenbanksicherung_service as d

    monkeypatch.setenv(d.UMGEBUNGSVARIABLE, r"C:\gibtesnicht\mariadb-dump.exe")

    assert d.werkzeug() is None


# ------------------------------------------------- die Sicherung als Ganzes

def test_eine_vollsicherung_nimmt_den_abzug_mit(monkeypatch, tmp_path, client):
    """Der Abzug liegt im Archiv - unter einem Namen, der nicht nach Minecraft aussieht."""
    from app.services import backup_service, datenbanksicherung_service

    monkeypatch.setattr(datenbanksicherung_service, "schreibe_abzug",
                        lambda ziel: _gestellter_abzug(ziel))
    server, db = _server_mit_dateien(tmp_path, "abzug-voll", 25851)
    try:
        sicherung = backup_service.create_backup(
            db, server=server, initiated_by_user_id=None, backup_scope="full")
        with zipfile.ZipFile(sicherung.storage_path) as archiv:
            namen = archiv.namelist()
            inhalt = archiv.read(
                datenbanksicherung_service.NAME_IM_ARCHIV).decode("utf-8")
        hinweis = sicherung.datenbank_hinweis
    finally:
        db.close()

    assert datenbanksicherung_service.NAME_IM_ARCHIV in namen, namen
    assert "CREATE TABLE" in inhalt
    assert hinweis and "gesichert" in hinweis, (
        "der Satz in der Spalte sagt, dass die Datenbank dabei war: " + str(hinweis))


def test_eine_weltsicherung_nimmt_ihn_NICHT_mit(monkeypatch, tmp_path, client):
    """Die Gegenrichtung, und sie ist eine Entscheidung.

    Eine Weltsicherung meint das GELAENDE - Chunks, Gebautes, uid.dat. Wer sie waehlt,
    hat sich fuer eine schnelle, kleine Sicherung entschieden. Die Spalte bleibt NULL:
    "die Frage stellte sich nicht". Ohne diesen Satz waere ein "immer mitnehmen" gruen,
    und jede Weltsicherung truege alle Spielerdaten.
    """
    from app.services import backup_service, datenbanksicherung_service

    gerufen = []
    monkeypatch.setattr(datenbanksicherung_service, "schreibe_abzug",
                        lambda ziel: (gerufen.append(ziel), _gestellter_abzug(ziel))[1])
    server, db = _server_mit_dateien(tmp_path, "abzug-welt", 25852)
    try:
        sicherung = backup_service.create_backup(
            db, server=server, initiated_by_user_id=None, backup_scope="world")
        with zipfile.ZipFile(sicherung.storage_path) as archiv:
            namen = archiv.namelist()
        hinweis = sicherung.datenbank_hinweis
    finally:
        db.close()

    assert gerufen == [], "bei einer Weltsicherung wird gar nicht erst gezogen"
    assert datenbanksicherung_service.NAME_IM_ARCHIV not in namen
    assert hinweis is None, "NULL heisst: die Frage stellte sich nicht"


def test_ein_fehlgeschlagener_abzug_laesst_die_sicherung_GELINGEN(monkeypatch, tmp_path,
                                                                  client):
    """Eine Dateisicherung ohne Datenbank ist viel besser als keine.

    Sie darf nur nicht AUSSEHEN wie eine vollstaendige - und genau das ist der
    Unterschied, auf den es hier ankommt: Der Status bleibt ``success``, und der Satz in
    der Spalte nennt den Grund. Beides als Fehler zu melden liesse eine brauchbare
    Sicherung aussehen wie keine; beides als Erfolg waere die stille Luege.
    """
    from app.services import backup_service, datenbanksicherung_service

    monkeypatch.setattr(
        datenbanksicherung_service, "schreibe_abzug",
        lambda ziel: (False, "MariaDB antwortet nicht auf 127.0.0.1"))
    server, db = _server_mit_dateien(tmp_path, "abzug-kaputt", 25853)
    try:
        sicherung = backup_service.create_backup(
            db, server=server, initiated_by_user_id=None, backup_scope="full")
        with zipfile.ZipFile(sicherung.storage_path) as archiv:
            namen = archiv.namelist()
        status = sicherung.status
        hinweis = sicherung.datenbank_hinweis
    finally:
        db.close()

    assert status == "success", "die Dateien sind gesichert - das ist etwas wert"
    assert datenbanksicherung_service.NAME_IM_ARCHIV not in namen
    assert hinweis and "antwortet nicht" in hinweis, (
        "der GRUND muss in der Spalte stehen - wer \"nein\" liest, fragt \"warum\": "
        + str(hinweis))


def test_der_abzug_der_vorigen_sicherung_wandert_nicht_mit(monkeypatch, tmp_path,
                                                           client):
    """Sonst wuechse jede Sicherung um den Abzug der vorigen.

    Die Wiederherstellung entpackt ``_manager/freimarken-datenbank.sql`` in den
    Serverordner. Die naechste Vollsicherung nimmt ihn als Datei des Servers mit - neben
    dem frischen Abzug. Nach zehn Laeufen lagen zehn alte Abzuege im Archiv, jeder mit
    allen Spielerdaten.
    """
    from app.services import backup_service, datenbanksicherung_service

    monkeypatch.setattr(datenbanksicherung_service, "schreibe_abzug",
                        lambda ziel: _gestellter_abzug(ziel))
    server, db = _server_mit_dateien(tmp_path, "abzug-doppelt", 25854)
    # So sieht der Serverordner NACH einer Wiederherstellung aus.
    alt = Path(server.base_path) / datenbanksicherung_service.NAME_IM_ARCHIV
    alt.parent.mkdir(parents=True, exist_ok=True)
    alt.write_text("-- der ALTE Abzug von vorgestern\n", encoding="utf-8",
                   newline="\n")
    try:
        sicherung = backup_service.create_backup(
            db, server=server, initiated_by_user_id=None, backup_scope="full")
        with zipfile.ZipFile(sicherung.storage_path) as archiv:
            namen = archiv.namelist()
            inhalt = archiv.read(
                datenbanksicherung_service.NAME_IM_ARCHIV).decode("utf-8")
    finally:
        db.close()

    assert namen.count(datenbanksicherung_service.NAME_IM_ARCHIV) == 1, (
        "genau EINMAL - der frische Abzug: " + str(namen))
    assert "vorgestern" not in inhalt, (
        "und es muss der FRISCHE sein, nicht der alte aus dem Serverordner")


def test_die_wiederherstellung_nennt_die_einspielzeile(monkeypatch, tmp_path, client):
    """Eingespielt wird NICHT - aber genannt.

    Das Schema gehoert ALLEN Servern gemeinsam; ein Wiederherstellen, das es
    stillschweigend ueberschreibt, nimmt dem zweiten Server seinen Stand. Eine Datei, die
    niemand erwaehnt, findet aber auch niemand - und dann glaubt der Betreiber, die
    Datenbank sei mit zurueckgekommen.
    """
    from app.services import backup_service, datenbanksicherung_service

    _zeige_auf_eigene_datei(monkeypatch, tmp_path)
    monkeypatch.setattr(datenbanksicherung_service, "schreibe_abzug",
                        lambda ziel: _gestellter_abzug(ziel))
    server, db = _server_mit_dateien(tmp_path, "abzug-zurueck", 25855)
    try:
        sicherung = backup_service.create_backup(
            db, server=server, initiated_by_user_id=None, backup_scope="full")
        zeile = backup_service.restore_backup(
            db, server=server, backup=sicherung, initiated_by_user_id=None)
        notizen = zeile.notes or ""
        liegt = Path(server.base_path) / datenbanksicherung_service.NAME_IM_ARCHIV
        da = liegt.is_file()
    finally:
        db.close()

    assert da, "der Abzug muss als DATEI zurueckkommen"
    assert "NICHT eingespielt" in notizen, notizen
    assert PROBE_SCHEMA in notizen, "und die fertige Zeile nennen: " + notizen


# ------------------------------------------------- mit echter Datenbank

def _anlage_antwortet() -> bool:
    """Horcht eine MariaDB auf 127.0.0.1:3306, und kommt root ohne Kennwort hinein?"""
    try:
        with socket.create_connection(("127.0.0.1", 3306), timeout=2.0):
            pass
    except OSError:
        return False
    werkzeug = _mysql_werkzeug()
    if werkzeug is None:
        return False
    try:
        ergebnis = subprocess.run([str(werkzeug), "-u", "root", "--batch",
                                   "--execute", "SELECT 1"],
                                  stdin=subprocess.DEVNULL, capture_output=True,
                                  timeout=20)
    except (OSError, subprocess.SubprocessError):
        return False
    return ergebnis.returncode == 0


def _mysql_werkzeug():
    from app.services import datenbanksicherung_service as d

    return d.werkzeug_einspielen()


def _als_root(sql: str) -> bool:
    werkzeug = _mysql_werkzeug()
    if werkzeug is None:
        return False
    ergebnis = subprocess.run([str(werkzeug), "-u", "root", "--batch",
                               "--execute", sql],
                              stdin=subprocess.DEVNULL, capture_output=True,
                              timeout=60)
    return ergebnis.returncode == 0


@pytest.mark.skipif(not _anlage_antwortet(),
                    reason="keine MariaDB auf 127.0.0.1:3306 mit root ohne Kennwort")
def test_ein_abzug_kommt_wirklich_zurueck(monkeypatch, tmp_path):
    """DIE PROBE, AUF DIE ES ANKOMMT: Der Abzug laesst sich EINSPIELEN.

    Ein Abzug, der nicht zurueckkommt, ist keine Sicherung, sondern eine Datei. Alles
    andere in dieser Datei prueft Entscheidungen; dieser Satz prueft den Gegenstand.

    SIE BERUEHRT DIE BETRIEBSDATEN NIE: eigenes Schema, eigener Benutzer, eigenes
    Kennwort, eigene Properties-Datei. Das Kennwort der Anlage des Betreibers kommt hier
    nicht vor.

    Der Weg ist der echte: mysqldump zieht, das Testschema wird GELEERT (sonst beweist
    ein Treffer nichts - die Zeilen koennten die alten sein), der Abzug wird eingespielt,
    und danach wird GEZAEHLT.
    """
    from app.services import datenbanksicherung_service as d

    # 1. Die Buehne: zwei Schemata, ein Benutzer, drei Zeilen.
    for sql in (
        "DROP DATABASE IF EXISTS " + PROBE_SCHEMA,
        "DROP DATABASE IF EXISTS " + PROBE_SCHEMA_TEST,
        "CREATE DATABASE " + PROBE_SCHEMA + " CHARACTER SET utf8mb4",
        "CREATE DATABASE " + PROBE_SCHEMA_TEST + " CHARACTER SET utf8mb4",
        "CREATE USER IF NOT EXISTS '" + PROBE_BENUTZER + "'@'%'"
        " IDENTIFIED BY '" + PROBE_KENNWORT + "'",
        "GRANT ALL ON " + PROBE_SCHEMA + ".* TO '" + PROBE_BENUTZER + "'@'%'",
        "GRANT ALL ON " + PROBE_SCHEMA_TEST + ".* TO '" + PROBE_BENUTZER + "'@'%'",
        "FLUSH PRIVILEGES",
    ):
        assert _als_root(sql), "Buehne: " + sql
    assert _als_root(
        "CREATE TABLE " + PROBE_SCHEMA + "." + PROBE_TABELLE
        + " (id INT PRIMARY KEY, wert VARCHAR(64))")
    assert _als_root(
        "INSERT INTO " + PROBE_SCHEMA + "." + PROBE_TABELLE + " VALUES"
        " (1,'Parzelle'),(2,'Guthaben'),(3,'Blockbuch')")

    try:
        _zeige_auf_eigene_datei(monkeypatch, tmp_path)

        # 2. Der Abzug - mit dem echten Werkzeug.
        abzug = tmp_path / "abzug.sql"
        gelungen, meldung = d.schreibe_abzug(abzug)
        assert gelungen, meldung
        text = abzug.read_text(encoding="utf-8", errors="replace")
        assert "CREATE DATABASE" not in text.upper(), (
            "ohne --databases - sonst landete das Einspielen IMMER im Betriebsschema")
        assert PROBE_TABELLE in text
        assert "Guthaben" in text, "die Daten gehoeren dazu, nicht nur das Geruest"

        # 3. Das Testschema LEEREN. Ohne diesen Schritt beweist ein Treffer nichts -
        #    die Zeilen koennten die alten sein.
        assert _als_root("DROP DATABASE " + PROBE_SCHEMA_TEST)
        assert _als_root("CREATE DATABASE " + PROBE_SCHEMA_TEST
                         + " CHARACTER SET utf8mb4")
        assert _als_root("GRANT ALL ON " + PROBE_SCHEMA_TEST + ".* TO '"
                         + PROBE_BENUTZER + "'@'%'")
        assert d.zaehle_im_testschema(PROBE_TABELLE) is None, (
            "nach dem Leeren gibt es die Tabelle nicht - sonst zaehlt die Probe"
            " hinterher die alten Zeilen")

        # 4. Einspielen und ZAEHLEN.
        gelungen, meldung = d.spiele_ein_ins_testschema(abzug)
        assert gelungen, meldung

        assert d.zaehle_im_testschema(PROBE_TABELLE) == 3, (
            "drei Zeilen hinein, drei Zeilen heraus - das ist der ganze Nachweis")

        # 5. Und ein zweites Einspielen muss auch gehen: --add-drop-table.
        gelungen, meldung = d.spiele_ein_ins_testschema(abzug)
        assert gelungen, (
            "ein zweites Einspielen in ein NICHT leeres Schema - genau der Fall, in dem"
            " man es braucht: " + meldung)
        assert d.zaehle_im_testschema(PROBE_TABELLE) == 3, (
            "und es verdoppelt nicht")
    finally:
        _als_root("DROP DATABASE IF EXISTS " + PROBE_SCHEMA)
        _als_root("DROP DATABASE IF EXISTS " + PROBE_SCHEMA_TEST)
        _als_root("DROP USER IF EXISTS '" + PROBE_BENUTZER + "'@'%'")


# ----------------------------------------------------------------- Werkzeug

def _zeige_auf_eigene_datei(monkeypatch, tmp_path: Path) -> Path:
    """Eine Properties-Datei NUR fuer die Probe - die echte bleibt unberuehrt."""
    from app.services import freimarken_einrichtung_service as e

    datei = tmp_path / "datenbank.properties"
    datei.write_text(
        "# Nur fuer die Probe.\n"
        "url=jdbc:mariadb://127.0.0.1:3306/" + PROBE_SCHEMA + "\n"
        "url.test=jdbc:mariadb://127.0.0.1:3306/" + PROBE_SCHEMA_TEST + "\n"
        "benutzer=" + PROBE_BENUTZER + "\n"
        "passwort=" + PROBE_KENNWORT + "\n", encoding="utf-8", newline="\n")
    monkeypatch.setattr(e, "PFAD_ZUGANG", datei)
    return datei


def _gestellter_abzug(ziel: Path) -> tuple[bool, str]:
    """Ein Abzug, wie mysqldump ihn schreibt - ohne eine Datenbank zu brauchen."""
    ziel.parent.mkdir(parents=True, exist_ok=True)
    ziel.write_text(
        "-- gestellter Abzug\n"
        "DROP TABLE IF EXISTS `parzellen`;\n"
        "CREATE TABLE `parzellen` (`id` int NOT NULL);\n"
        "INSERT INTO `parzellen` VALUES (1);\n", encoding="utf-8", newline="\n")
    return True, "Plugin-Datenbank gesichert: Schema probe, 120 Bytes."


def _server_mit_dateien(tmp_path: Path, slug: str, port: int):
    """Ein Server mit etwas Inhalt - und eine offene Sitzung dazu."""
    from app.db.session import SessionLocal
    from app.models.server import Server

    basis = tmp_path / slug
    (basis / "world").mkdir(parents=True, exist_ok=True)
    (basis / "world" / "level.dat").write_bytes(b"x" * 32)
    (basis / "server.properties").write_text("level-name=world\n", encoding="utf-8",
                                             newline="\n")
    (basis / "plugins").mkdir(exist_ok=True)
    (basis / "plugins" / "Freimarken.jar").write_bytes(b"y" * 16)

    db = SessionLocal()
    server = Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                    base_path=str(basis), port=port, status="stopped",
                    start_mode="command",
                    start_command="java -jar server.jar")
    db.add(server)
    db.commit()
    db.refresh(server)
    return server, db


def test_der_wegwerfordner_bleibt_nicht_liegen(monkeypatch, tmp_path, client):
    """Ein vergessener Abzug im Temp traegt alle Spielerdaten im Klartext.

    Und zwar dort, wo niemand ihn sucht: im Temp-Verzeichnis, das kein Mensch
    aufraeumt und das keine Sicherung erfasst. Nach dem Schreiben des Archivs darf
    er weg sein - und zwar auch dann, wenn das Schreiben wirft.
    """
    import tempfile

    from app.services import backup_service, datenbanksicherung_service

    monkeypatch.setattr(datenbanksicherung_service, "schreibe_abzug",
                        lambda ziel: _gestellter_abzug(ziel))
    vorher = set(Path(tempfile.gettempdir()).glob("mcm-dbabzug-*"))
    server, db = _server_mit_dateien(tmp_path, "abzug-temp", 25856)
    try:
        backup_service.create_backup(
            db, server=server, initiated_by_user_id=None, backup_scope="full")
    finally:
        db.close()
    nachher = set(Path(tempfile.gettempdir()).glob("mcm-dbabzug-*"))

    assert nachher <= vorher, (
        "Der Wegwerfordner liegt noch da: " + str(sorted(nachher - vorher))
        + " - er traegt alle Spielerdaten im Klartext, und zwar dort, wo niemand"
        " ihn sucht.")


def test_ein_tabellenname_wird_geprueft_bevor_er_in_SQL_wandert(monkeypatch,
                                                               tmp_path):
    """Der Name kommt aus dem Programm - und wird trotzdem geprueft.

    Er wandert in ein ``SELECT COUNT(*) FROM``, und eine Zeichenkette, die in SQL
    wandert, wird in diesem Haus geprueft. Dass heute nur das Programm sie setzt,
    ist kein Schutz fuer morgen: Der naechste Aufrufer kommt aus einem Formular,
    und dann ist der Riegel schon da.
    """
    _zeige_auf_eigene_datei(monkeypatch, tmp_path)
    from app.services import datenbanksicherung_service as d

    for boese in ("x; DROP TABLE y", "`x`", "x y", "", "x'"):
        assert d.zaehle_im_testschema(boese) is None, (
            "ein Name mit Sonderzeichen darf nicht in das SQL: " + repr(boese))


def test_eine_halbe_datei_bleibt_nicht_stehen(monkeypatch, tmp_path):
    """Ein Abbruch MITTEN im Abzug darf keine Datei hinterlassen.

    Sie sieht wie eine Sicherung aus und ist keine - und das ist schlimmer als gar
    keine Datei: Wer sie findet, spielt sie ein und hat ein halbes Schema.

    DER FALL BRAUCHT EINE EIGENE PROBE: Der frueher Rueckweg ("keine
    Zugangsdaten") legt nie eine Datei an und beweist darum nichts darueber. Hier
    schreibt das gestellte Werkzeug erst und scheitert dann - genau wie ein
    abgerissener Netzzugang mitten im Abzug.
    """
    _zeige_auf_eigene_datei(monkeypatch, tmp_path)
    from app.services import datenbanksicherung_service as d

    def halb(werkzeugpfad, zugang, weitere, *, hinein=None, heraus=None):
        if heraus is not None:
            Path(heraus).write_text(
                "-- halb geschrieben, dann abgebrochen" + chr(10),
                encoding="utf-8", newline=chr(10))
        return False, "Verbindung mitten im Abzug verloren"

    monkeypatch.setattr(d, "_lauf", halb)
    ziel = tmp_path / "abzug.sql"

    gelungen, meldung = d.schreibe_abzug(ziel)

    assert gelungen is False
    assert "mitten im Abzug" in meldung, meldung
    assert not ziel.exists(), (
        "Die halbe Datei liegt noch da. Sie sieht wie eine Sicherung aus und ist"
        " keine - wer sie findet, spielt sie ein und hat ein halbes Schema.")


def test_ein_boeser_tabellenname_ERREICHT_das_werkzeug_nicht(monkeypatch, tmp_path):
    """DER RIEGEL, nicht sein Ergebnis.

    Die erste Fassung dieser Probe fragte nur, ob ``None`` zurueckkommt - und das
    kommt es auch mit ausgeschaltetem Riegel: MariaDB lehnt den kaputten Namen
    selbst ab, und der Rueckgabewert ist derselbe. Eine Einspritzung (Fall H) hat es
    gezeigt, und sie hatte recht.

    Was der Riegel zusagt, ist etwas anderes: Der Name ERREICHT das Werkzeug nicht.
    Darum wird jetzt der Aufruf beobachtet - und die Gegenrichtung steht daneben,
    denn eine Probe, die NIE einen Aufruf erwartet, waere auch gruen, wenn die
    Funktion gar nichts mehr tut.
    """
    import subprocess as echtes

    _zeige_auf_eigene_datei(monkeypatch, tmp_path)
    from app.services import datenbanksicherung_service as d

    gerufen = []

    def beobachtet(befehl, **rest):
        gerufen.append(befehl)

        class Ergebnis:
            returncode = 1
            stdout = b""
            stderr = b""

        return Ergebnis()

    monkeypatch.setattr(d, "werkzeug_einspielen",
                        lambda: Path("C:/gestellt/mariadb.exe"))
    monkeypatch.setattr(echtes, "run", beobachtet)

    for boese in ("x; DROP TABLE y", "`x`", "x y", "", "x'"):
        assert d.zaehle_im_testschema(boese) is None, repr(boese)
    assert gerufen == [], (
        "Ein Name mit Sonderzeichen darf das Werkzeug NICHT erreichen - und genau"
        " das war die Luecke: Mit ausgeschaltetem Riegel kam weiterhin None"
        " zurueck, weil MariaDB den Namen selbst ablehnt. Gerufen wurde: "
        + str(gerufen))

    # DIE GEGENRICHTUNG: Ein guter Name muss durchkommen - sonst waere diese Probe
    # auch gruen, wenn die Funktion gar nichts mehr tut.
    assert d.zaehle_im_testschema(PROBE_TABELLE) is None, (
        "das gestellte Werkzeug gibt Code 1 - also None")
    assert len(gerufen) == 1, (
        "der GUTE Name muss das Werkzeug erreichen: " + str(gerufen))
    assert PROBE_TABELLE in str(gerufen[0]), gerufen[0]


def test_die_seite_nennt_den_hinweis(monkeypatch, tmp_path, client):
    """DER ANSCHLUSS ZUR OBERFLAECHE.

    Ein Satz in einer Spalte, den niemand sieht, ist keine Auskunft - und bis zu
    einer Einspritzung (Fall R) hielt genau das keine Probe: Die Seite konnte den
    Hinweis verschweigen, und alle fuenfzehn Proben blieben gruen. Zum dritten Mal
    an diesem Tag dieselbe Falle.

    Geprueft werden BEIDE Lagen: der Erfolg (dabei) und der Fehlschlag (mit Grund).
    Nur die zweite ist die wichtige - sie ist der Unterschied zwischen einer
    Sicherung, die vollstaendig IST, und einer, die nur so aussieht.
    """
    from app.services import backup_service, datenbanksicherung_service

    server_id = _angemeldet(client, tmp_path, "seite-hinweis", 25857)
    _sicherung_anlegen(monkeypatch, datenbanksicherung_service, backup_service,
                       server_id, gelungen=True)

    seite = client.get("/servers/" + str(server_id) + "/backups").text

    # DIE KOPFZEILE und nicht das Wort: "Plugin-Datenbank" steht auch im
    # Hinweisabsatz ueber der Tabelle, und eine Einspritzung (R3) hat gezeigt, dass
    # die Probe damit gruen blieb, als die SPALTE verschwand. Eine fehlende Spalte
    # verschiebt aber jeden Wert unter einen falschen Kopf.
    assert "<th>Plugin-Datenbank</th>" in seite, (
        "die SPALTE muss es geben - ohne sie stehen alle Werte unter dem falschen"
        " Kopf")
    assert "dabei" in seite

    # Und der Fehlschlag - mit dem GRUND, denn wer "nein" liest, fragt "warum".
    _sicherung_anlegen(monkeypatch, datenbanksicherung_service, backup_service,
                       server_id, gelungen=False)
    seite = client.get("/servers/" + str(server_id) + "/backups").text

    assert "NICHT dabei" in seite, (
        "ein fehlgeschlagener Abzug muss auf der Seite stehen - sonst sieht eine"
        " unvollstaendige Sicherung aus wie eine vollstaendige")
    assert "antwortet nicht" in seite, (
        "und zwar mit dem GRUND: " + seite[-2000:])


def _angemeldet(client, tmp_path: Path, slug: str, port: int) -> int:
    """Ein Server mit Dateien, ein Nutzer mit Rechten darauf, angemeldet."""
    from app.core.security import hash_password
    from app.db.session import SessionLocal
    from app.models.server_permission import ServerPermission
    from app.models.user import User

    server, db = _server_mit_dateien(tmp_path, slug, port)
    try:
        server_id = server.id
        person = User(username=slug, password_hash=hash_password("Egal12345!"),
                      role="super_admin", is_active=True)
        db.add(person)
        db.commit()
        db.refresh(person)
        db.add(ServerPermission(user_id=person.id, server_id=server_id,
                                can_view=True, can_console=True, can_restart=True,
                                can_edit_files=True, can_manage=True))
        db.commit()
    finally:
        db.close()
    assert client.post("/login", data={"username": slug,
                                       "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303
    return server_id


def _sicherung_anlegen(monkeypatch, dienst, backup_service, server_id: int, *,
                       gelungen: bool) -> None:
    """Eine Sicherung mit gestelltem Abzug - gelungen oder nicht."""
    from app.db.session import SessionLocal
    from app.models.server import Server

    if gelungen:
        monkeypatch.setattr(dienst, "schreibe_abzug",
                            lambda ziel: _gestellter_abzug(ziel))
    else:
        monkeypatch.setattr(
            dienst, "schreibe_abzug",
            lambda ziel: (False, "MariaDB antwortet nicht auf 127.0.0.1"))
    with SessionLocal() as db:
        server = db.get(Server, server_id)
        backup_service.create_backup(db, server=server,
                                     initiated_by_user_id=None,
                                     backup_scope="full")
