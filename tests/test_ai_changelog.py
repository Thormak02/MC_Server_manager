"""Tests fuer die KI-Changelog-Zusammenfassung vor einem Update.

Die Frage ist nicht "gibt es ein Update", sondern "darf ich es einspielen". Der Satz, der
darueber entscheidet, steht selten im Changelog der Zielfassung - er steht in einer
Fassung DAZWISCHEN, die man mit einem Sprung ueberspringt. Deshalb pruefen diese Tests
vor allem:

* dass die ganze Spanne geholt wird, nicht nur die Zielfassung,
* dass bei fehlendem Changelog die KI GAR NICHT gefragt wird (sie koennte nur erfinden,
  bezahlt waere es trotzdem),
* dass eine Kuerzung gemeldet und nicht verschwiegen wird.

Kein Test spricht mit einer echten API; sowohl der KI-Anbieter als auch die HTTP-Abrufe
der Inhalte-Dienste werden ersetzt.
"""
import pytest


class _WerkzeugAttrappe:
    def __init__(self, eingabe=None):
        self.eingabe = eingabe if eingabe is not None else {
            "zusammenfassung": "Fehlerbehebungen.", "breaking": [], "empfehlung": "jetzt"}
        self.gesehen: list = []

    def frage(self, anfrage, schluessel, **_):
        from app.services.ai_provider_service import KiAntwort

        self.gesehen.append(anfrage)
        return KiAntwort(text="", modell=anfrage.modell, tokens_ein=600, tokens_aus=300,
                         werkzeug_aufrufe=[{"name": "changelog_bericht",
                                            "eingabe": self.eingabe, "id": "tu_1"}],
                         abbruchgrund="werkzeug")


def _bereite_vor(db, monkeypatch, attrappe, **einstellungen):
    from app.services import ai_provider_service, ai_settings_service as ais

    ais.setze_schluessel(db, "sk-ant-api03-TEST-1234567890")
    ais.speichere(db, {"aktiv": True, **einstellungen})
    monkeypatch.setitem(ai_provider_service.ANBIETER, "anthropic", attrappe)


def _nutzer(db, rolle="admin", name="cl-tester"):
    from app.core.security import hash_password
    from app.models.user import User

    person = User(username=name, password_hash=hash_password("Egal12345!"),
                  role=rolle, is_active=True)
    db.add(person)
    db.commit()
    db.refresh(person)
    return person


def _server(db, tmp_path, *, slug: str, port: int):
    from app.models.server import Server

    basis = tmp_path / slug
    basis.mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text(
        f"level-name=world\nserver-port={port}\n", encoding="utf-8")
    server = Server(name=slug, slug=slug, server_type="paper", mc_version="1.21.1",
                    base_path=str(basis), port=port, status="stopped",
                    start_mode="command", start_command="java -jar server.jar")
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


def _eintrag(db, server, *, anbieter="modrinth", version_id="v2", label="1.2.0",
             projekt="prj1", typ="plugin"):
    from app.models.installed_content import InstalledContent

    zeile = InstalledContent(
        server_id=server.id, provider_name=anbieter, content_type=typ,
        external_project_id=projekt, external_version_id=version_id,
        name="Haustiere", version_label=label, file_name="haustiere.jar")
    db.add(zeile)
    db.commit()
    db.refresh(zeile)
    return zeile


def _modrinth_fassungen(anzahl=5):
    """Fassungen v1..vN, aufsteigend nach Datum. Die MITTE traegt den wichtigen Satz."""
    eintraege = []
    for i in range(1, anzahl + 1):
        changelog = f"- Fehler {i} behoben"
        if i == 3:
            changelog = ("- ACHTUNG: config format changed, delete your old config\n"
                         "- Neue Einstellung 'pets.max'")
        eintraege.append({
            "id": f"v{i}", "version_number": f"1.{i}.0", "name": f"Release 1.{i}.0",
            "date_published": f"2026-0{i}-01T10:00:00Z", "version_type": "release",
            "changelog": changelog,
        })
    return eintraege


# ----------------------------------------------------------------- HTML zu Text

def test_html_wird_zu_lesbarem_text():
    """CurseForge liefert HTML. Ungewandelt waeren die Tags ein Drittel der Token - und
    bezahlt wird pro Token."""
    from app.services import ai_changelog_service as acl

    text = acl.html_zu_text(
        "<h2>1.3.0</h2><p>Fixed a <b>crash</b> &amp; more</p>"
        "<ul><li>Config format changed</li><li>New option</li></ul>")

    assert "<" not in text
    assert "&amp;" not in text and "& more" in text
    assert "- Config format changed" in text
    assert "- New option" in text
    # Die Listenpunkte duerfen nicht zu einem Satz zusammenlaufen.
    assert text.count("\n- ") == 2


def test_html_zu_text_vertraegt_leere_eingabe():
    from app.services import ai_changelog_service as acl

    assert acl.html_zu_text("") == ""
    assert acl.html_zu_text(None) == ""


# --------------------------------------------------------------------- Spanne

def test_spanne_nimmt_alles_zwischen_installiert_und_ziel():
    """Der entscheidende Punkt. Nur den Changelog der Zielfassung zu lesen heisst, den
    Satz "config format changed" aus einer Zwischenfassung zu uebersehen."""
    from app.services import ai_changelog_service as acl

    fassungen = [{"id": f"v{i}", "datum": f"2026-0{i}-01"} for i in range(1, 6)]
    ausschnitt, angaben = acl._spanne(fassungen, "v2", "v5")

    assert [f["id"] for f in ausschnitt] == ["v3", "v4", "v5"]
    assert angaben["richtung"] == "vor"
    assert angaben["gesamt"] == 3
    assert angaben["weggelassen"] == 0
    assert angaben["installierte_bekannt"] is True


def test_spanne_bei_rueckschritt_nimmt_nur_das_ziel():
    from app.services import ai_changelog_service as acl

    fassungen = [{"id": f"v{i}", "datum": f"2026-0{i}-01"} for i in range(1, 6)]
    ausschnitt, angaben = acl._spanne(fassungen, "v4", "v2")

    assert [f["id"] for f in ausschnitt] == ["v2"]
    assert angaben["richtung"] == "zurueck"


def test_spanne_bei_unbekannter_installierter_fassung():
    """Eine zurueckgezogene Fassung steht nicht mehr in der Liste. Dann gibt es keine
    Spanne - und das muss gesagt werden, sonst haelt man die Zusammenfassung fuer
    vollstaendig."""
    from app.services import ai_changelog_service as acl

    fassungen = [{"id": f"v{i}", "datum": f"2026-0{i}-01"} for i in range(1, 6)]
    ausschnitt, angaben = acl._spanne(fassungen, "weg", "v3")

    assert [f["id"] for f in ausschnitt] == ["v1", "v2", "v3"]
    assert angaben["installierte_bekannt"] is False


def test_spanne_kuerzt_auf_die_neuesten_und_meldet_es():
    """Bei 40 Zwischenfassungen wird gekuerzt - aber nicht stillschweigend, sonst haelt
    man die Zusammenfassung fuer vollstaendig."""
    from app.services import ai_changelog_service as acl

    fassungen = [{"id": f"v{i}", "datum": f"2026-01-{i:02d}"} for i in range(1, 41)]
    ausschnitt, angaben = acl._spanne(fassungen, "v1", "v40")

    assert len(ausschnitt) == acl.MAX_FASSUNGEN
    assert ausschnitt[-1]["id"] == "v40", "Die Zielfassung muss drin sein"
    assert angaben["gesamt"] == 39
    assert angaben["weggelassen"] == 39 - acl.MAX_FASSUNGEN


# ---------------------------------------------------------------- Changelogs holen

def test_modrinth_changelogs_werden_mitgenommen(client, monkeypatch, tmp_path):
    """list_modrinth_versions wirft den Changelog weg - fuer diese Funktion ist er das
    Einzige, was zaehlt."""
    from app.db.session import SessionLocal
    from app.services import ai_changelog_service as acl, content_service

    monkeypatch.setattr(content_service, "_request_json",
                        lambda url, headers=None: _modrinth_fassungen())

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="cl-a", port=25661)
        eintrag = _eintrag(db, server, version_id="v2")
        daten = acl.hole_changelogs(eintrag, server, "v5")

    assert [f["version"] for f in daten["fassungen"]] == ["1.3.0", "1.4.0", "1.5.0"]
    assert "config format changed" in daten["fassungen"][0]["changelog"]
    assert daten["angaben"]["mit_changelog"] == 3
    assert daten["installiert"] == "1.2.0"


def test_curseforge_changelog_wird_je_datei_geholt(client, monkeypatch, tmp_path):
    """CurseForge liefert den Changelog nicht in der Dateiliste, sondern erst auf
    Nachfrage - und als HTML."""
    from app.db.session import SessionLocal
    from app.services import ai_changelog_service as acl, content_service

    aufrufe: list[str] = []

    def _fake(url, headers=None):
        aufrufe.append(url)
        if url.endswith("/files"):
            return {"data": [
                {"id": 11, "displayName": "1.1.0", "fileDate": "2026-01-01T00:00:00Z",
                 "releaseType": 1},
                {"id": 12, "displayName": "1.2.0", "fileDate": "2026-02-01T00:00:00Z",
                 "releaseType": 1},
                {"id": 13, "displayName": "1.3.0", "fileDate": "2026-03-01T00:00:00Z",
                 "releaseType": 1},
            ]}
        return {"data": "<ul><li>Config format changed</li></ul>"}

    monkeypatch.setattr(content_service, "_request_json", _fake)

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="cl-b", port=25662)
        eintrag = _eintrag(db, server, anbieter="curseforge", version_id="11",
                           label="1.1.0")
        daten = acl.hole_changelogs(eintrag, server, "13")

    assert [f["version"] for f in daten["fassungen"]] == ["1.2.0", "1.3.0"]
    assert all("<" not in f["changelog"] for f in daten["fassungen"])
    assert "- Config format changed" in daten["fassungen"][0]["changelog"]
    # Je Fassung der Spanne ein Changelog-Aufruf, nicht fuer alle drei Dateien.
    assert sum(1 for u in aufrufe if u.endswith("/changelog")) == 2


def test_manueller_eintrag_wird_verstaendlich_abgelehnt(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_changelog_service as acl

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="cl-c", port=25663)
        eintrag = _eintrag(db, server, anbieter="local", projekt="")

        with pytest.raises(ValueError, match="manuell"):
            acl.hole_changelogs(eintrag, server, "v1")


def test_unbekannter_anbieter_wird_benannt(client, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_changelog_service as acl

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="cl-d", port=25664)
        eintrag = _eintrag(db, server, anbieter="vanillatweaks")

        with pytest.raises(ValueError, match="vanillatweaks"):
            acl.hole_changelogs(eintrag, server, "v1")


# ------------------------------------------------------------------- Textaufbau

def test_text_behaelt_die_neuesten_und_meldet_die_kuerzung():
    from app.services import ai_changelog_service as acl

    fassungen = [{"version": f"1.{i}.0", "datum": "2026-01-01T00:00:00Z",
                  "kanal": "release", "changelog": f"Fassung {i}: " + ("x" * 5000)}
                 for i in range(1, 11)]
    text, gekuerzt = acl.baue_text({"fassungen": fassungen})

    assert gekuerzt is True
    assert "Fassung 10" in text, "Die neueste Fassung muss drin sein"
    assert "Fassung 1:" not in text, "Die aelteste darf wegfallen"
    assert len(text) <= acl.MAX_ZEICHEN + 6000


def test_fassungen_ohne_changelog_tauchen_im_text_nicht_auf():
    from app.services import ai_changelog_service as acl

    text, _ = acl.baue_text({"fassungen": [
        {"version": "1.1.0", "datum": "", "kanal": "release", "changelog": ""},
        {"version": "1.2.0", "datum": "", "kanal": "release", "changelog": "Etwas"},
    ]})

    assert "1.1.0" not in text
    assert "1.2.0" in text


# ----------------------------------------------------------------- Zusammenspiel

def test_ohne_changelog_wird_die_ki_nicht_gefragt(client, monkeypatch, tmp_path):
    """Bei Plugins der Normalfall. Die KI koennte aus nichts nur etwas erfinden - und
    bezahlt waere es trotzdem."""
    from app.db.session import SessionLocal
    from app.services import ai_changelog_service as acl, content_service

    leer = [{"id": f"v{i}", "version_number": f"1.{i}.0",
             "date_published": f"2026-0{i}-01T10:00:00Z", "version_type": "release",
             "changelog": ""} for i in range(1, 4)]
    monkeypatch.setattr(content_service, "_request_json", lambda url, headers=None: leer)

    attrappe = _WerkzeugAttrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="ohne-cl")
        _bereite_vor(db, monkeypatch, attrappe)
        server = _server(db, tmp_path, slug="cl-e", port=25665)
        eintrag = _eintrag(db, server, version_id="v1", label="1.1.0")

        ergebnis = acl.berichte(db, user=person, server=server, eintrag=eintrag,
                                ziel_version_id="v3")

    assert attrappe.gesehen == [], "Die KI wurde fuer nichts gefragt"
    assert ergebnis["ohne_ki"] is True
    assert "kein Changelog" in ergebnis["meldung"]
    assert "Guthaben" in ergebnis["meldung"]


def test_der_wichtige_satz_aus_der_mitte_wird_gesendet(client, monkeypatch, tmp_path):
    """Das ist der ganze Zweck der Funktion: der Satz steht in Fassung 3, gesprungen wird
    von 2 auf 5."""
    from app.db.session import SessionLocal
    from app.services import ai_changelog_service as acl, content_service

    monkeypatch.setattr(content_service, "_request_json",
                        lambda url, headers=None: _modrinth_fassungen())

    attrappe = _WerkzeugAttrappe({
        "zusammenfassung": "Das Config-Format hat sich geaendert.",
        "breaking": [{"was": "config format changed",
                      "folge": "Die alte Config wird nicht gelesen",
                      "zu_tun": "Alte Config loeschen, Server neu starten"}],
        "wichtig": ["Neue Einstellung 'pets.max'"],
        "empfehlung": "pruefen",
        "begruendung_empfehlung": "Erst Backup, dann Config erneuern."})

    with SessionLocal() as db:
        person = _nutzer(db, name="mitte1")
        _bereite_vor(db, monkeypatch, attrappe)
        server = _server(db, tmp_path, slug="cl-f", port=25666)
        eintrag = _eintrag(db, server, version_id="v2", label="1.2.0")

        ergebnis = acl.berichte(db, user=person, server=server, eintrag=eintrag,
                                ziel_version_id="v5")

    gesendet = attrappe.gesehen[0].nachrichten[0]["inhalt"]
    assert "config format changed" in gesendet, \
        "Der entscheidende Satz aus der Zwischenfassung fehlt"
    assert "Version 1.5.0" in gesendet and "Version 1.3.0" in gesendet
    assert attrappe.gesehen[0].werkzeugzwang == "changelog_bericht"
    assert ergebnis["empfehlung"] == "pruefen"
    assert ergebnis["breaking"][0]["zu_tun"].startswith("Alte Config")
    assert ergebnis["installiert"] == "1.2.0"
    assert ergebnis["ziel"] == "1.5.0"


def test_fassungen_ohne_changelog_werden_im_ergebnis_benannt(client, monkeypatch, tmp_path):
    """Sonst glaubt man, die Zusammenfassung deckt die ganze Spanne ab."""
    from app.db.session import SessionLocal
    from app.services import ai_changelog_service as acl, content_service

    gemischt = _modrinth_fassungen()
    gemischt[3]["changelog"] = ""
    monkeypatch.setattr(content_service, "_request_json",
                        lambda url, headers=None: gemischt)

    with SessionLocal() as db:
        person = _nutzer(db, name="gemischt1")
        _bereite_vor(db, monkeypatch, _WerkzeugAttrappe())
        server = _server(db, tmp_path, slug="cl-g", port=25667)
        eintrag = _eintrag(db, server, version_id="v2", label="1.2.0")

        ergebnis = acl.berichte(db, user=person, server=server, eintrag=eintrag,
                                ziel_version_id="v5")

    assert ergebnis["angaben"]["ohne_changelog"] == 1
    assert any("keinen Changelog" in h for h in ergebnis["hinweise"])
    nach_version = {f["version"]: f for f in ergebnis["fassungen"]}
    assert nach_version["1.4.0"]["hat_changelog"] is False
    assert nach_version["1.3.0"]["hat_changelog"] is True


def test_zurueckgezogene_installierte_fassung_wird_gemeldet(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.services import ai_changelog_service as acl, content_service

    monkeypatch.setattr(content_service, "_request_json",
                        lambda url, headers=None: _modrinth_fassungen())

    with SessionLocal() as db:
        person = _nutzer(db, name="weg1")
        _bereite_vor(db, monkeypatch, _WerkzeugAttrappe())
        server = _server(db, tmp_path, slug="cl-h", port=25668)
        eintrag = _eintrag(db, server, version_id="zurueckgezogen", label="1.0.9")

        ergebnis = acl.berichte(db, user=person, server=server, eintrag=eintrag,
                                ziel_version_id="v3")

    assert any("nicht in der Versionsliste" in h for h in ergebnis["hinweise"])


# --------------------------------------------------------------------- Routen

def _login_super(client):
    antwort = client.post("/login", data={"username": "admin", "password": "admin123!"},
                          follow_redirects=False)
    assert antwort.status_code == 303


def test_route_liefert_den_bericht(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.services import content_service

    monkeypatch.setattr(content_service, "_request_json",
                        lambda url, headers=None: _modrinth_fassungen())

    with SessionLocal() as db:
        _bereite_vor(db, monkeypatch, _WerkzeugAttrappe({
            "zusammenfassung": "Config-Format geaendert.",
            "breaking": [{"was": "config", "folge": "wird nicht gelesen"}],
            "empfehlung": "pruefen"}))
        server = _server(db, tmp_path, slug="cl-route", port=25669)
        eintrag = _eintrag(db, server, version_id="v2", label="1.2.0")
        server_id, content_id = server.id, eintrag.id

    _login_super(client)
    antwort = client.post(f"/api/servers/{server_id}/content/ki-changelog",
                          json={"content_id": content_id, "version_id": "v5",
                                "version_label": "1.5.0"})
    assert antwort.status_code == 200, antwort.text
    daten = antwort.json()
    assert daten["empfehlung"] == "pruefen"
    assert daten["breaking"][0]["was"] == "config"


def test_route_haelt_nur_echte_aufrufe_fest(client, monkeypatch, tmp_path):
    """Ein Lauf ohne KI-Aufruf hat nichts gekostet und keine Daten hinausgeschickt - er
    gehoert nicht ins Protokoll."""
    from sqlalchemy import select

    from app.db.session import SessionLocal
    from app.models.audit_log import AuditLog
    from app.services import content_service

    leer = [{"id": f"v{i}", "version_number": f"1.{i}.0",
             "date_published": f"2026-0{i}-01T10:00:00Z", "version_type": "release",
             "changelog": ""} for i in range(1, 4)]
    monkeypatch.setattr(content_service, "_request_json", lambda url, headers=None: leer)

    with SessionLocal() as db:
        _bereite_vor(db, monkeypatch, _WerkzeugAttrappe())
        server = _server(db, tmp_path, slug="cl-audit", port=25670)
        eintrag = _eintrag(db, server, version_id="v1", label="1.1.0")
        server_id, content_id = server.id, eintrag.id

    _login_super(client)
    antwort = client.post(f"/api/servers/{server_id}/content/ki-changelog",
                          json={"content_id": content_id, "version_id": "v3"})
    assert antwort.status_code == 200
    assert antwort.json()["ohne_ki"] is True

    with SessionLocal() as db:
        eintraege = list(db.scalars(
            select(AuditLog).where(AuditLog.action == "ai.changelog")))
    assert eintraege == []


def test_route_lehnt_fremden_eintrag_ab(client, monkeypatch, tmp_path):
    """Eine content_id aus dem Browser darf nicht auf den Eintrag eines anderen Servers
    zeigen."""
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        a = _server(db, tmp_path, slug="cl-x", port=25671)
        b = _server(db, tmp_path, slug="cl-y", port=25672)
        fremd = _eintrag(db, b, version_id="v1")
        a_id, fremd_id = a.id, fremd.id

    _login_super(client)
    antwort = client.post(f"/api/servers/{a_id}/content/ki-changelog",
                          json={"content_id": fremd_id, "version_id": "v3"})
    assert antwort.status_code == 404


def test_route_braucht_angaben(client, tmp_path):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="cl-leer", port=25673)
        server_id = server.id

    _login_super(client)
    assert client.post(f"/api/servers/{server_id}/content/ki-changelog",
                       json={}).status_code == 400


def test_route_braucht_anmeldung(client, tmp_path):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="cl-anon", port=25674)
        eintrag = _eintrag(db, server, version_id="v1")
        server_id, content_id = server.id, eintrag.id

    antwort = client.post(f"/api/servers/{server_id}/content/ki-changelog",
                          json={"content_id": content_id, "version_id": "v3"})
    assert antwort.status_code == 401


def test_inhaltsseite_zeigt_den_knopf(client, tmp_path):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="cl-seite", port=25675)
        _eintrag(db, server, version_id="v1")
        server_id = server.id

    _login_super(client)
    seite = client.get(f"/servers/{server_id}/content")
    assert seite.status_code == 200
    assert "ki-changelog-btn" in seite.text
    assert "Was aendert sich?" in seite.text
    assert 'id="ki-changelog-dialog"' in seite.text
