"""Die Protokollseite - und dass die Zeile lesbar bleibt, die geschrieben wurde.

ZWEI FUNKTIONEN, EIN TEXT
protokoll_text schreibt die Zeile, zerlege_protokoll liest sie wieder. Beide muessen
denselben Trenner benutzen, sonst steht auf der Seite bei jeder Zeile "Grund: -", obwohl
der Grund im Protokoll steht. Darum gibt es die Konstante TRENNER_GRUND und die Proben
hier.

EIN RUNDLAUF ALLEIN GENUEGT NICHT
Eine Probe, die nur zerlege(text(x)) == x prueft, haelt auch dann, wenn beide Funktionen
alt und neu VERTAUSCHEN - konsistent falsch ist konsistent. Darum liest eine zweite Probe
eine von Hand geschriebene Zeile, in der alt und neu unterscheidbar sind.

WARUM AB MODERATOR
Das allgemeine Protokoll des Managers ist Super-Admin-Sache, weil darin alles steht:
Anmeldungen, Dateizugriffe, Sicherheitsereignisse. Diese Ansicht ist auf einen Server UND
auf freimarken.* eingeschraenkt - sie zeigt, welche Einstellung wer wann warum geaendert
hat, und das ist genau, was Plan 12a.4 ab Moderator lesbar nennt. Die Probe
test_fremder_server_erscheint_nicht_im_protokoll haelt die Einschraenkung fest.
"""
from __future__ import annotations

import pytest

from app.services import freimarken_service as fs


CONFIG = """wirtschaft:
  parzelle:
    grundpreis: 25000
    zusammenlegung-je-feld: 10000
  sprung-bestaetigt:
    grundpreis: ""
    zusammenlegung-je-feld: ""

handel:
  bundshop-wort: Bund
  zeilen-je-seite: 10
"""


def _nutzer(db, rolle, name):
    from app.core.security import hash_password
    from app.models.user import User

    person = User(username=name, password_hash=hash_password("Egal12345!"),
                  role=rolle, is_active=True)
    db.add(person)
    db.commit()
    db.refresh(person)
    return person


def _server(db, tmp_path, *, slug, port):
    from app.models.server import Server

    basis = tmp_path / slug
    (basis / "plugins" / "Freimarken").mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text("level-name=world\n", encoding="utf-8")
    with open(basis / "plugins" / "Freimarken" / "config.yml", "w",
              encoding="utf-8", newline="") as offen:
        offen.write(CONFIG)
    server = Server(name=slug, slug=slug, server_type="paper", mc_version="26.3",
                    base_path=str(basis), port=port, status="stopped",
                    start_mode="command", start_command="java -jar server.jar",
                    freimarken_enabled=True)
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


def _aufbau(client, tmp_path, *, name, port, rolle="admin", zweiter=False):
    from app.db.session import SessionLocal
    from app.models.server_permission import ServerPermission

    with SessionLocal() as db:
        person = _nutzer(db, rolle, name)
        server = _server(db, tmp_path, slug=name, port=port)
        db.add(ServerPermission(user_id=person.id, server_id=server.id, can_view=True,
                               can_console=False, can_restart=False,
                               can_edit_files=True, can_manage=False))
        zweite_id = None
        if zweiter:
            andere = _server(db, tmp_path, slug=name + "-zwei", port=port + 100)
            db.add(ServerPermission(user_id=person.id, server_id=andere.id,
                                   can_view=True, can_console=False, can_restart=False,
                                   can_edit_files=True, can_manage=False))
            zweite_id = andere.id
        db.commit()
        server_id = server.id

    assert client.post("/login", data={"username": name, "password": "Egal12345!"},
                       follow_redirects=False).status_code == 303
    return (server_id, zweite_id) if zweiter else server_id


# ------------------------------------------------------- Schreiben und Lesen

def test_der_geschriebene_grund_steht_auf_der_seite(client, tmp_path):
    """Der ganze Zweck dieser Seite: der Grund darf nicht unterwegs verloren gehen."""
    server_id = _aufbau(client, tmp_path, name="buch", port=25701)

    client.post("/freimarken/" + str(server_id) + "/wirtschaft",
                data={"feld:wirtschaft.parzelle.grundpreis": "27000",
                      "grund": "Weil mehr Spieler da sind"},
                follow_redirects=False)

    antwort = client.get("/freimarken/" + str(server_id) + "/protokoll")
    assert antwort.status_code == 200
    assert "wirtschaft.parzelle.grundpreis" in antwort.text
    assert "25000" in antwort.text and "27000" in antwort.text
    assert "Weil mehr Spieler da sind" in antwort.text
    assert "buch" in antwort.text, "der Nutzername gehoert dazu"


def test_mehrere_aenderungen_einer_zeile_stehen_einzeln(client, tmp_path):
    server_id = _aufbau(client, tmp_path, name="zweifach", port=25702)

    client.post("/freimarken/" + str(server_id) + "/wirtschaft",
                data={"feld:wirtschaft.parzelle.grundpreis": "27000",
                      "feld:wirtschaft.parzelle.zusammenlegung-je-feld": "11000",
                      "grund": "Beides zusammen"},
                follow_redirects=False)

    zeilen = _zeilen(server_id)
    assert len(zeilen) == 1, "eine Speicherung ist EINE Protokollzeile"
    assert len(zeilen[0]["aenderungen"]) == 2
    pfade = {e["pfad"] for e in zeilen[0]["aenderungen"]}
    assert pfade == {"wirtschaft.parzelle.grundpreis",
                     "wirtschaft.parzelle.zusammenlegung-je-feld"}


def _zeilen(server_id, grenze=200):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        return fs.protokollzeilen(db, server_id, grenze=grenze)


def test_ohne_grund_steht_es_auch_so_da(client, tmp_path):
    """Ein fehlender Grund wird benannt und nicht als leere Zelle verschwiegen."""
    server_id = _aufbau(client, tmp_path, name="grundlos", port=25703)
    client.post("/freimarken/" + str(server_id) + "/wirtschaft",
                data={"feld:wirtschaft.parzelle.grundpreis": "27000", "grund": ""},
                follow_redirects=False)
    zeilen = _zeilen(server_id)
    assert zeilen[0]["grund"] == fs.OHNE_GRUND


def test_neueste_zuerst(client, tmp_path):
    server_id = _aufbau(client, tmp_path, name="reihenfolge", port=25704)
    for wert in ("26000", "27000", "28000"):
        client.post("/freimarken/" + str(server_id) + "/wirtschaft",
                    data={"feld:wirtschaft.parzelle.grundpreis": wert,
                          "grund": "Schritt " + wert},
                    follow_redirects=False)
    zeilen = _zeilen(server_id)
    assert len(zeilen) == 3
    assert zeilen[0]["grund"] == "Schritt 28000", "die neueste Zeile steht oben"
    assert zeilen[2]["grund"] == "Schritt 26000"


def test_fremder_server_erscheint_nicht_im_protokoll(client, tmp_path):
    """Die Ansicht ist auf EINEN Server eingeschraenkt - daran haengt, dass sie ab
    Moderator lesbar sein darf."""
    erster, zweiter = _aufbau(client, tmp_path, name="getrennt", port=25705,
                              zweiter=True)

    client.post("/freimarken/" + str(erster) + "/wirtschaft",
                data={"feld:wirtschaft.parzelle.grundpreis": "27000",
                      "grund": "Nur beim ersten"},
                follow_redirects=False)

    assert len(_zeilen(erster)) == 1
    assert _zeilen(zweiter) == [], "die Zeile des anderen Servers gehoert nicht hierher"
    antwort = client.get("/freimarken/" + str(zweiter) + "/protokoll")
    assert "Nur beim ersten" not in antwort.text
    assert "Noch nichts geaendert" in antwort.text


def test_fremde_aktionen_desselben_servers_erscheinen_nicht(client, tmp_path):
    """Nur freimarken.* - und daran haengt, dass diese Seite ab Moderator offen sein darf.

    Das allgemeine Protokoll ist Super-Admin-Sache, weil darin Anmeldungen,
    Dateizugriffe und Sicherheitsereignisse stehen. Faellt der Aktionsfilter, zeigte
    diese Seite sie einem Moderator - eine Rechteausweitung durch eine fehlende
    where-Bedingung, die niemand sucht.
    """
    from app.db.session import SessionLocal
    from app.services import audit_service

    server_id = _aufbau(client, tmp_path, name="fremdeaktion", port=25709)

    with SessionLocal() as db:
        audit_service.log_action(db, action="server.file_edit", server_id=server_id,
                                 details="ops.json geaendert - GEHEIMNIS 4711")
        audit_service.log_action(db, action="auth.login", server_id=server_id,
                                 details="jemand hat sich angemeldet")

    zeilen = _zeilen(server_id)
    assert zeilen == [], "fremde Aktionen gehoeren nicht in diese Ansicht"
    antwort = client.get("/freimarken/" + str(server_id) + "/protokoll")
    assert antwort.status_code == 200
    assert "GEHEIMNIS 4711" not in antwort.text
    assert "angemeldet" not in antwort.text


def test_moderator_darf_das_protokoll_lesen(client, tmp_path):
    server_id = _aufbau(client, tmp_path, name="lesemoder", port=25706,
                        rolle="moderator")
    antwort = client.get("/freimarken/" + str(server_id) + "/protokoll")
    assert antwort.status_code == 200


def test_view_only_nicht(client, tmp_path):
    server_id = _aufbau(client, tmp_path, name="nixlesen", port=25707,
                        rolle="view_only")
    assert client.get("/freimarken/" + str(server_id) + "/protokoll",
                      follow_redirects=False).status_code == 403


def test_leere_seite_bricht_nicht(client, tmp_path):
    """Ohne eine einzige Zeile muss die Seite stehen - nicht mit einem Serverfehler."""
    server_id = _aufbau(client, tmp_path, name="leerbuch", port=25708)
    antwort = client.get("/freimarken/" + str(server_id) + "/protokoll")
    assert antwort.status_code == 200
    assert "Noch nichts geaendert" in antwort.text


# ------------------------------------------------- Die Zerlegung fuer sich

def test_rundlauf_schreiben_und_lesen():
    """Was protokoll_text schreibt, muss zerlege_protokoll wieder hergeben."""
    aenderungen = [{"pfad": "wechsel.mark-je-goldmark", "alt": "1000", "neu": "1100"},
                   {"pfad": "laeden.kaution", "alt": "5000", "neu": "4000"}]
    text = fs.protokoll_text(aenderungen, "Marktlage")
    zerlegt = fs.zerlege_protokoll(text)
    assert zerlegt["grund"] == "Marktlage"
    assert zerlegt["aenderungen"] == aenderungen


def test_handgeschriebene_zeile_wird_richtig_herum_gelesen():
    """Die Gegenprobe zum Rundlauf.

    Ein Rundlauf allein haelt auch dann, wenn BEIDE Funktionen alt und neu vertauschen -
    konsistent falsch ist konsistent. Diese Zeile ist von Hand geschrieben, und 7 ist
    nicht 9: Eine Verwechslung faellt hier auf.
    """
    zerlegt = fs.zerlege_protokoll("kasse.startguthaben: 7 -> 9 | Grund: Probe")
    assert zerlegt["aenderungen"] == [{"pfad": "kasse.startguthaben",
                                       "alt": "7", "neu": "9"}]
    assert zerlegt["grund"] == "Probe"


@pytest.mark.parametrize("roh", [
    "",
    None,
    "irgendwas ganz anderes",
    "ohne pfeil: nur ein doppelpunkt | Grund: x",
    "kasse.startguthaben: 7 -> 9",            # ohne Grund-Teil
])
def test_unerwartete_zeile_wird_nicht_verschluckt(roh):
    """Was sich nicht zerlegen laesst, muss GANZ erhalten bleiben.

    Eine Zeile aus einem aelteren Stand ist kein Grund, sie zu verschweigen: Bei Geld und
    Besitz ist eine unleserliche Zeile besser als eine fehlende.
    """
    zerlegt = fs.zerlege_protokoll(roh)
    # Jeder Zweig liefert dieselben Schluessel - sonst laeuft die Vorlage gegen ein
    # Undefined und eine einzige krumme Zeile zerlegt die ganze Seite.
    for pflicht in ("aenderungen", "grund", "unlesbar", "roh"):
        assert pflicht in zerlegt, "Schluessel " + pflicht + " fehlt"
    if roh:
        teile = (zerlegt["roh"] or "")
        assert teile == roh.strip()


def test_grund_mit_dem_trenner_darin():
    """Ein Grund, der selbst " | Grund: " enthaelt, darf die Zerlegung nicht aufteilen.

    partition trennt am ERSTEN Vorkommen - der Rest bleibt beim Grund, wo er hingehoert.
    """
    text = fs.protokoll_text([{"pfad": "a.b", "alt": "1", "neu": "2"}],
                             "siehe oben | Grund: Verwechslung")
    zerlegt = fs.zerlege_protokoll(text)
    assert zerlegt["grund"] == "siehe oben | Grund: Verwechslung"
    assert zerlegt["aenderungen"] == [{"pfad": "a.b", "alt": "1", "neu": "2"}]


def test_der_trenner_steht_nur_an_einer_stelle():
    """Quelltext-Zusage: Schreiben und Lesen benutzen dieselbe Konstante.

    Stuende der Text zweimal da, liesse eine Aenderung die Protokollseite stumm bei
    "kein Grund" stehen - und zwar fuer alle alten Zeilen zugleich.
    """
    import inspect

    quelle = inspect.getsource(fs)
    # Das Literal darf nur in der Konstante selbst vorkommen.
    assert quelle.count('" | Grund: "') == 1, (
        "der Trenner steht mehr als einmal als Literal im Quelltext")
    assert "TRENNER_GRUND" in inspect.getsource(fs.protokoll_text)
    assert "TRENNER_GRUND" in inspect.getsource(fs.zerlege_protokoll)
