"""Tests fuer die Pfadwerkzeuge des Assistenten.

Der Pfadzugriff ist die weiteste Befugnis, die der Assistent hat - entsprechend liegt das
Gewicht hier auf der Grenze, nicht auf dem Nutzen:

* Nur Super-Admin. Ein Admin mit allen Serverrechten kommt nicht durch, und zwar an
  JEDEM der drei Tore: lesen, vorlegen, ausfuehren.
* Geheimnisse bleiben zu, auch fuer Super-Admins (die Regel selbst wird in
  test_admin_path_service.py geprueft, hier nur, dass sie am Werkzeug ankommt).
* Ein verbotener Pfad erscheint schon im VORSCHLAG als Hindernis - sonst bestaetigt der
  Nutzer etwas, das danach abprallt.
* Der Satz vor dem Bestaetigen kommt vom Manager und nennt, was wirklich passiert
  (Anzahl der Eintraege, Ueberschreiben).
* Auch das Lesen eines freien Pfades wird protokolliert.
"""
from pathlib import Path

import pytest


def _nutzer(db, rolle="super_admin", name="pfad-tester", server=None):
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
    (basis / "server.properties").write_text(f"server-port={port}\n", encoding="utf-8")
    server = Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                    base_path=str(basis), port=port, status="stopped",
                    start_mode="command", start_command="java -jar server.jar",
                    memory_min_mb=1024, memory_max_mb=4096)
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


def _protokoll(db, aktion: str) -> int:
    from app.models.audit_log import AuditLog

    return db.query(AuditLog).filter(AuditLog.action == aktion).count()


# ------------------------------------------------------------------ Nur Super-Admin

LESENDE = ("pfad_auflisten", "pfad_lesen")
AENDERNDE = ("pfad_schreiben", "pfad_loeschen", "pfad_verschieben")


def test_lesende_pfadwerkzeuge_nur_fuer_super_admins(client, tmp_path):
    """Ein Admin mit allen Serverrechten reicht nicht. Ein freier Pfad ist keine
    Serverarbeit mehr - dort kann alles liegen."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    datei = tmp_path / "beute.txt"
    datei.write_text("GEHEIM", encoding="utf-8")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="pfad-rechte", port=25911)
        for rolle in ("admin", "moderator", "view_only"):
            person = _nutzer(db, rolle=rolle, name=f"pfad-{rolle}", server=server)
            for name, p in (("pfad_lesen", {"pfad": str(datei)}),
                            ("pfad_auflisten", {"pfad": str(tmp_path)})):
                ergebnis = aas._fuehre_lesend_aus(db, person, name, p)
                assert "fehler" in ergebnis, (rolle, name)
                assert "Super-Admin" in ergebnis["fehler"]
                assert "GEHEIM" not in str(ergebnis)

        chef = _nutzer(db, name="pfad-chef")
        ok = aas._fuehre_lesend_aus(db, chef, "pfad_lesen", {"pfad": str(datei)})
        assert ok.get("inhalt") == "GEHEIM"


def test_aendernde_pfadwerkzeuge_nur_fuer_super_admins(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    datei = tmp_path / "weg.txt"
    datei.write_text("bleibt", encoding="utf-8")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="pfad-rechte2", port=25912)
        person = _nutzer(db, rolle="admin", name="pfad-admin2", server=server)
        parameter = {"pfad": str(datei), "inhalt": "neu", "rekursiv": True,
                     "quelle": str(datei), "ziel": str(tmp_path / "anders.txt")}

        for name in AENDERNDE:
            # Tor 2: der Vorschlag sagt es schon.
            vorschlag = aas._baue_vorschlag(db, person,
                                            {"name": name, "id": "t", "eingabe": parameter})
            assert vorschlag["moeglich"] is False, name
            assert "Super-Admin" in vorschlag["hindernis"], name
            # Tor 3: und die Ausfuehrung tut es trotzdem nicht.
            with pytest.raises(aas.AgentFehler, match="Super-Admin"):
                aas.fuehre_aus(db, user=person, werkzeug_name=name, parameter=parameter)

    assert datei.read_text(encoding="utf-8") == "bleibt"


# ------------------------------------------------------------------- Geheimnisse

def test_geheimnisse_bleiben_auch_dem_assistenten_zu(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        chef = _nutzer(db, name="pfad-geheim")
        for pfad in (".env", "data/mc_server_manager.db", ".git/config"):
            ergebnis = aas._fuehre_lesend_aus(db, chef, "pfad_lesen", {"pfad": pfad})
            assert "fehler" in ergebnis, pfad
            assert "bleibt auch fuer Super-Admins zu" in ergebnis["fehler"]


def test_kein_schreibweg_in_den_programmcode(client, tmp_path):
    """Wer app/ schreiben darf, schreibt die Rechtepruefung um, die ihn gerade
    begrenzt - und zwar ueber die Sitzung hinaus. Das ist die eine Grenze, die auch
    fuer einen Super-Admin gilt."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        chef = _nutzer(db, name="pfad-code")
        parameter = {"pfad": "app/services/ai_agent_service.py", "inhalt": "# uebernommen"}

        vorschlag = aas._baue_vorschlag(db, chef, {"name": "pfad_schreiben", "id": "t",
                                                   "eingabe": parameter})
        assert vorschlag["moeglich"] is False
        assert "Programmordner" in vorschlag["hindernis"]
        with pytest.raises(aas.AgentFehler, match="Programmordner"):
            aas.fuehre_aus(db, user=chef, werkzeug_name="pfad_schreiben",
                           parameter=parameter)

    # Lesen bleibt erlaubt: der Quelltext liegt ohnehin oeffentlich im Repository.
    with SessionLocal() as db:
        chef2 = _nutzer(db, name="pfad-code2")
        ergebnis = aas._fuehre_lesend_aus(db, chef2, "pfad_lesen",
                                          {"pfad": "app/main.py"})
    assert "fehler" not in ergebnis


# --------------------------------------------------------------------- Operationen

def test_schreiben_anlegen_und_ersetzen(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    ziel = tmp_path / "neu" / "notiz.txt"
    with SessionLocal() as db:
        chef = _nutzer(db, name="pfad-schreib")

        satz = aas.WERKZEUGE["pfad_schreiben"].beschreibe(
            db, None, {"pfad": str(ziel), "inhalt": "Hallo"})
        assert "5 Zeichen" in satz and "notiz.txt" in satz
        assert "gibt es schon" not in satz

        ergebnis = aas.fuehre_aus(db, user=chef, werkzeug_name="pfad_schreiben",
                                  parameter={"pfad": str(ziel), "inhalt": "Hallo"})
        assert ergebnis["kodierung"] == "utf-8"
        assert ziel.read_text(encoding="utf-8") == "Hallo"

        # Beim zweiten Mal muss der Satz sagen, dass etwas ersetzt wird.
        satz = aas.WERKZEUGE["pfad_schreiben"].beschreibe(
            db, None, {"pfad": str(ziel), "inhalt": "Neu"})
        assert "gibt es schon" in satz and "5 Bytes" in satz


def test_loeschen_nennt_die_zahl_vorher(client, tmp_path):
    """Der Satz vor dem Bestaetigen muss die Groesse der Tat nennen. '12 Eintraege' ist
    der Unterschied zwischen einem Klick und einem Unfall."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    ordner = tmp_path / "haufen"
    (ordner / "tief").mkdir(parents=True)
    for nummer in range(10):
        (ordner / f"d{nummer}.txt").write_text("x", encoding="utf-8")

    with SessionLocal() as db:
        chef = _nutzer(db, name="pfad-loesch")
        satz = aas.WERKZEUGE["pfad_loeschen"].beschreibe(db, None, {"pfad": str(ordner)})
        assert "11 Eintraegen" in satz

        with pytest.raises(aas.AgentFehler, match="rekursiv=true"):
            aas.fuehre_aus(db, user=chef, werkzeug_name="pfad_loeschen",
                           parameter={"pfad": str(ordner)})
        assert ordner.exists()

        ergebnis = aas.fuehre_aus(db, user=chef, werkzeug_name="pfad_loeschen",
                                  parameter={"pfad": str(ordner), "rekursiv": True})
    assert ergebnis["eintraege"] == 11
    assert not ordner.exists()


def test_verschieben_prueft_die_quelle_wie_ein_loeschen(client, tmp_path):
    """Verschieben nimmt die Quelle von ihrem Platz weg. Fuer alles, was danach dort
    sucht, ist das dasselbe wie loeschen - also gilt dieselbe Pruefung."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import ai_agent_service as aas

    basis = tmp_path / "srv" / "paper"
    basis.mkdir(parents=True)
    with SessionLocal() as db:
        db.add(Server(name="pfad-verschieb", slug="pfad-verschieb", server_type="paper",
                      mc_version="26.3", base_path=str(basis), port=25913))
        db.commit()
        chef = _nutzer(db, name="pfad-versch")

        with pytest.raises(aas.AgentFehler, match="Ordner des Servers"):
            aas.fuehre_aus(db, user=chef, werkzeug_name="pfad_verschieben",
                           parameter={"quelle": str(basis),
                                      "ziel": str(tmp_path / "anderswo")})
        assert basis.exists()


def test_verschieben_ueberschreibt_nichts(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    quelle = tmp_path / "a.txt"
    quelle.write_text("neu", encoding="utf-8")
    ziel = tmp_path / "b.txt"
    ziel.write_text("alt", encoding="utf-8")

    with SessionLocal() as db:
        chef = _nutzer(db, name="pfad-ueber")
        with pytest.raises(aas.AgentFehler, match="gibt es schon"):
            aas.fuehre_aus(db, user=chef, werkzeug_name="pfad_verschieben",
                           parameter={"quelle": str(quelle), "ziel": str(ziel)})
    assert ziel.read_text(encoding="utf-8") == "alt"


def test_auflisten_zeigt_art_und_groesse(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    (tmp_path / "unterordner").mkdir()
    (tmp_path / "x.txt").write_text("abc", encoding="utf-8")

    with SessionLocal() as db:
        chef = _nutzer(db, name="pfad-liste")
        ergebnis = aas._fuehre_lesend_aus(db, chef, "pfad_auflisten",
                                          {"pfad": str(tmp_path)})
    assert "fehler" not in ergebnis
    nach_name = {e["name"]: e for e in ergebnis["eintraege"]}
    assert nach_name["unterordner"]["art"] == "ordner"
    assert nach_name["x.txt"]["bytes"] == 3


# ------------------------------------------------------------------- Protokoll

def test_freies_lesen_wird_protokolliert(client, tmp_path):
    """Veraendernde Werkzeuge protokolliert fuehre_aus von selbst, lesende nicht. Bei
    Serverdateien genuegt das - die sieht der Nutzer ohnehin im Dateieditor. Ein freier
    Pfad ist etwas anderes: hinterher soll nachvollziehbar sein, was angesehen wurde."""
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    datei = tmp_path / "spur.txt"
    datei.write_text("inhalt", encoding="utf-8")

    with SessionLocal() as db:
        chef = _nutzer(db, name="pfad-spur")
        vorher = _protokoll(db, "ai.agent.pfad_lesen")
        aas._fuehre_lesend_aus(db, chef, "pfad_lesen", {"pfad": str(datei)})
        assert _protokoll(db, "ai.agent.pfad_lesen") == vorher + 1

        vorher = _protokoll(db, "ai.agent.pfad_auflisten")
        aas._fuehre_lesend_aus(db, chef, "pfad_auflisten", {"pfad": str(tmp_path)})
        assert _protokoll(db, "ai.agent.pfad_auflisten") == vorher + 1


# -------------------------------------------------------- Archivblick am freien Pfad

def test_archiv_am_freien_pfad_nur_fuer_super_admins(client, tmp_path):
    """Der eigentliche Anlass: das gehostete Resource Pack liegt unter
    data/resourcepacks, also ausserhalb jedes Serverordners."""
    import zipfile

    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    archiv = tmp_path / "resourcepacks" / "vt_1_abc.zip"
    archiv.parent.mkdir(parents=True)
    with zipfile.ZipFile(archiv, "w") as z:
        z.writestr("Selected Packs.txt", "Packs:\nWavyWater\n")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="pfad-archiv", port=25914)
        admin = _nutzer(db, rolle="admin", name="pfad-archiv-admin", server=server)

        abgelehnt = aas._fuehre_lesend_aus(db, admin, "archiv_ansehen",
                                          {"pfad": str(archiv)})
        assert "fehler" in abgelehnt
        assert "Super-Admin" in abgelehnt["fehler"]

        chef = _nutzer(db, name="pfad-archiv-chef")
        liste = aas._fuehre_lesend_aus(db, chef, "archiv_ansehen", {"pfad": str(archiv)})
        assert "fehler" not in liste, liste
        assert "Selected Packs.txt" in liste["eintraege"]

        inhalt = aas._fuehre_lesend_aus(db, chef, "archiv_ansehen",
                                        {"pfad": str(archiv),
                                         "eintrag": "Selected Packs.txt"})
        assert "WavyWater" in inhalt["inhalt"]
    assert get_settings() is not None  # Einstellungen bleiben unberuehrt


def test_archiv_im_serverordner_geht_weiter_ohne_pfadrecht(client, tmp_path):
    """Regression: der freie Pfad ist dazugekommen, der alte Weg muss bleiben - mit
    server_id, serverrelativ, fuer Admins."""
    import zipfile

    from app.db.session import SessionLocal
    from app.services import ai_agent_service as aas

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="pfad-arch-alt", port=25915)
        admin = _nutzer(db, rolle="admin", name="pfad-arch-alt-a", server=server)
        jar = Path(server.base_path) / "plugins" / "Dings.jar"
        jar.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(jar, "w") as z:
            z.writestr("plugin.yml", "name: Dings\n")

        liste = aas._fuehre_lesend_aus(db, admin, "archiv_ansehen",
                                       {"server_id": server.id,
                                        "pfad": "plugins/Dings.jar"})
        assert "fehler" not in liste, liste
        assert liste["eintraege"] == ["plugin.yml"]

        # Und ohne server_id bei einem serverrelativen Pfad: klare Absage, kein Absturz.
        ohne = aas._fuehre_lesend_aus(db, admin, "archiv_ansehen",
                                      {"pfad": "plugins/Dings.jar"})
        assert "fehler" in ohne
        assert "server_id" in ohne["fehler"]
