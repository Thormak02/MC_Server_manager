import json
import re


def _login_admin(client):
    response = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert response.status_code == 303


def _import_server(client, server_dir, *, name="Files Server"):
    response = client.post(
        "/servers/import/confirm",
        data={
            "name": name,
            "base_path": str(server_dir),
            "server_type": "paper",
            "mc_version": "1.20.1",
            "start_mode": "bat",
            "start_bat_path": str(server_dir / "start.bat"),
            "start_command": "",
        },
        follow_redirects=False,
    )
    assert response.status_code == 303
    return response.headers["location"]


def test_java_profile_create_and_default(client):
    _login_admin(client)

    create_response = client.post(
        "/settings/java-profiles",
        data={
            "name": "Java17",
            "java_path": r"C:\Java\jdk-17\bin\java.exe",
            "version_label": "17",
            "description": "Standard Java 17",
            "is_default": "true",
        },
        follow_redirects=True,
    )
    assert create_response.status_code == 200
    assert "Java17" in create_response.text


def test_file_edit_roundtrip(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "files_srv"
    (server_dir / "config").mkdir(parents=True)
    (server_dir / "start.bat").write_text("@echo off\n", encoding="utf-8")
    target_file = server_dir / "config" / "test.cfg"
    target_file.write_text("key=old\n", encoding="utf-8")

    server_location = _import_server(client, server_dir)
    match = re.search(r"/servers/(\d+)", server_location)
    assert match
    server_id = int(match.group(1))

    read_response = client.get(f"/servers/{server_id}/files", params={"file": "config/test.cfg"})
    assert read_response.status_code == 200
    assert "key=old" in read_response.text

    write_response = client.post(
        f"/servers/{server_id}/files/save",
        data={"relative_path": "config/test.cfg", "content": "key=new\n"},
        follow_redirects=True,
    )
    assert write_response.status_code == 200
    assert "key=new" in write_response.text
    assert target_file.read_text(encoding="utf-8") == "key=new\n"


def test_file_assistant_for_server_properties(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "assistant_srv"
    server_dir.mkdir(parents=True)
    (server_dir / "start.bat").write_text("@echo off\n", encoding="utf-8")
    props = server_dir / "server.properties"
    props.write_text("motd=Hello\npvp=true\nmax-players=20\n", encoding="utf-8")

    server_location = _import_server(client, server_dir, name="Assistant Server")
    match = re.search(r"/servers/(\d+)", server_location)
    assert match
    server_id = int(match.group(1))

    read_response = client.get(
        f"/servers/{server_id}/files",
        params={"file": "server.properties", "mode": "assistant"},
    )
    assert read_response.status_code == 200
    assert "Server Properties Assistent" in read_response.text

    save_response = client.post(
        f"/servers/{server_id}/files/assistant-save",
        data={
            "relative_path": "server.properties",
            "motd": "New MOTD",
            "pvp": "false",
            "max-players": "42",
            "extras_text": "allow-flight=true",
        },
        follow_redirects=True,
    )
    assert save_response.status_code == 200
    content = props.read_text(encoding="utf-8")
    assert "motd=New MOTD" in content
    assert "pvp=false" in content
    assert "max-players=42" in content
    assert "allow-flight=true" in content


def test_access_lists_support_empty_files_and_whitelist_toggle(client, tmp_path, monkeypatch):
    _login_admin(client)
    server_dir = tmp_path / "access_srv"
    server_dir.mkdir(parents=True)
    (server_dir / "start.bat").write_text("@echo off\n", encoding="utf-8")
    (server_dir / "whitelist.json").write_text("", encoding="utf-8")
    (server_dir / "ops.json").write_text("", encoding="utf-8")
    (server_dir / "banned-players.json").write_text("", encoding="utf-8")
    (server_dir / "banned-ips.json").write_text("", encoding="utf-8")

    from app.services import file_service

    monkeypatch.setattr(file_service, "_lookup_mojang_uuid", lambda _name: None)

    server_location = _import_server(client, server_dir, name="Access Server")
    match = re.search(r"/servers/(\d+)", server_location)
    assert match
    server_id = int(match.group(1))

    page_response = client.get(f"/servers/{server_id}/access?tab=whitelist")
    assert page_response.status_code == 200
    assert "Whitelist" in page_response.text

    add_whitelist = client.post(
        f"/servers/{server_id}/access/entry-add",
        data={"list_key": "whitelist", "identity": "PlayerOne"},
        follow_redirects=True,
    )
    assert add_whitelist.status_code == 200
    whitelist_data = json.loads((server_dir / "whitelist.json").read_text(encoding="utf-8"))
    assert len(whitelist_data) == 1
    assert whitelist_data[0]["name"] == "PlayerOne"
    assert "uuid" in whitelist_data[0]

    add_op = client.post(
        f"/servers/{server_id}/access/entry-add",
        data={"list_key": "ops", "identity": "AdminOne", "op_level": "2"},
        follow_redirects=True,
    )
    assert add_op.status_code == 200
    ops_data = json.loads((server_dir / "ops.json").read_text(encoding="utf-8"))
    assert len(ops_data) == 1
    assert ops_data[0]["name"] == "AdminOne"
    assert ops_data[0]["level"] == 2
    assert ops_data[0]["bypassesPlayerLimit"] is True

    update_op_level_response = client.post(
        f"/servers/{server_id}/access/op-level-update",
        data={"identity": "AdminOne", "op_level": "4"},
        follow_redirects=True,
    )
    assert update_op_level_response.status_code == 200
    ops_data_after_update = json.loads((server_dir / "ops.json").read_text(encoding="utf-8"))
    assert ops_data_after_update[0]["name"] == "AdminOne"
    assert ops_data_after_update[0]["level"] == 4

    add_ban = client.post(
        f"/servers/{server_id}/access/entry-add",
        data={"list_key": "banned_players", "identity": "BadOne"},
        follow_redirects=True,
    )
    assert add_ban.status_code == 200
    bans_data = json.loads((server_dir / "banned-players.json").read_text(encoding="utf-8"))
    assert len(bans_data) == 1
    assert bans_data[0]["name"] == "BadOne"
    assert bans_data[0]["reason"] == "Banned by an operator."

    toggle_response = client.post(
        f"/servers/{server_id}/access/whitelist-toggle",
        data={"enabled": "true"},
        follow_redirects=True,
    )
    assert toggle_response.status_code == 200
    props_content = (server_dir / "server.properties").read_text(encoding="utf-8")
    assert "white-list=true" in props_content


def test_access_list_actions_send_runtime_commands(client, tmp_path, monkeypatch):
    _login_admin(client)
    server_dir = tmp_path / "access_runtime_srv"
    server_dir.mkdir(parents=True)
    (server_dir / "start.bat").write_text("@echo off\n", encoding="utf-8")
    (server_dir / "whitelist.json").write_text("[]\n", encoding="utf-8")
    (server_dir / "ops.json").write_text("[]\n", encoding="utf-8")
    (server_dir / "banned-players.json").write_text("[]\n", encoding="utf-8")
    (server_dir / "banned-ips.json").write_text("[]\n", encoding="utf-8")

    from app.api.routers import files as files_router
    from app.services import file_service

    sent_commands: list[str] = []

    def _fake_send_console_command(_db, _server, command, _initiated_by_user_id):
        sent_commands.append(command)
        return True, "Befehl gesendet."

    monkeypatch.setattr(file_service, "_lookup_mojang_uuid", lambda _name: None)
    monkeypatch.setattr(files_router, "send_console_command", _fake_send_console_command)

    server_location = _import_server(client, server_dir, name="Access Runtime Server")
    match = re.search(r"/servers/(\d+)", server_location)
    assert match
    server_id = int(match.group(1))

    add_payloads = [
        {"list_key": "whitelist", "identity": "PlayerOne"},
        {"list_key": "ops", "identity": "AdminOne", "op_level": "2"},
        {"list_key": "banned_players", "identity": "BadOne"},
        {"list_key": "banned_ips", "identity": "10.0.0.8"},
    ]
    for payload in add_payloads:
        response = client.post(
            f"/servers/{server_id}/access/entry-add",
            data=payload,
            follow_redirects=True,
        )
        assert response.status_code == 200

    remove_payloads = [
        {"list_key": "whitelist", "identity": "PlayerOne"},
        {"list_key": "ops", "identity": "AdminOne"},
        {"list_key": "banned_players", "identity": "BadOne"},
        {"list_key": "banned_ips", "identity": "10.0.0.8"},
    ]
    for payload in remove_payloads:
        response = client.post(
            f"/servers/{server_id}/access/entry-remove",
            data=payload,
            follow_redirects=True,
        )
        assert response.status_code == 200

    enable_response = client.post(
        f"/servers/{server_id}/access/whitelist-toggle",
        data={"enabled": "true"},
        follow_redirects=True,
    )
    assert enable_response.status_code == 200
    disable_response = client.post(
        f"/servers/{server_id}/access/whitelist-toggle",
        data={"enabled": "false"},
        follow_redirects=True,
    )
    assert disable_response.status_code == 200

    assert sent_commands == [
        "whitelist add PlayerOne",
        "op AdminOne",
        "ban BadOne",
        "ban-ip 10.0.0.8",
        "whitelist remove PlayerOne",
        "deop AdminOne",
        "pardon BadOne",
        "pardon-ip 10.0.0.8",
        "whitelist on",
        "whitelist off",
    ]


def test_assistant_detects_dynamic_server_properties_and_json_fields():
    from app.services.file_service import build_content_from_assistant, get_assistant_payload

    props_payload = get_assistant_payload(
        "server.properties",
        "motd=Hello\nallow-flight=true\ncustom-setting=abc\n",
    )
    assert props_payload is not None
    assert props_payload["mode"] == "server_properties"
    keys = [field["key"] for field in props_payload["fields"]]
    assert "allow-flight" in keys
    assert "custom-setting" in keys

    props_form = {
        "__assistant_field_keys": props_payload["field_keys_json"],
        "__assistant_existing_keys": props_payload["existing_keys_json"],
        "motd": "Updated",
        "allow-flight": "false",
        "custom-setting": "xyz",
        "extras_text": "",
    }
    props_content = build_content_from_assistant("server.properties", props_form)
    assert "motd=Updated" in props_content
    assert "allow-flight=false" in props_content
    assert "custom-setting=xyz" in props_content

    json_payload = get_assistant_payload(
        "config/settings.json",
        json.dumps(
            {
                "graphics": {"fancy": True, "distance": 12},
                "mods": [{"enabled": False}],
                "note": "hello",
            }
        ),
    )
    assert json_payload is not None
    assert json_payload["mode"] == "json_fields"
    labels = {field["label"]: field["key"] for field in json_payload["fields"]}
    assert "graphics.fancy" in labels
    assert "graphics.distance" in labels
    assert "mods[0].enabled" in labels
    assert "note" in labels

    json_form = {
        "__assistant_json_meta": json_payload["assistant_json_meta"],
        "__assistant_json_base": json_payload["assistant_json_base"],
    }
    json_form[labels["graphics.fancy"]] = "false"
    json_form[labels["graphics.distance"]] = "24"
    json_form[labels["mods[0].enabled"]] = "true"
    json_form[labels["note"]] = "updated"

    json_content = build_content_from_assistant("config/settings.json", json_form)
    parsed = json.loads(json_content)
    assert parsed["graphics"]["fancy"] is False
    assert parsed["graphics"]["distance"] == 24
    assert parsed["mods"][0]["enabled"] is True
    assert parsed["note"] == "updated"


def test_dateimanager_zeigt_spigot_und_bukkit_yml(tmp_path, client):
    """Darin stehen die Server-Einstellungen von Spigot und Paper. Eine feste Namensliste
    hatte sie verschluckt - jetzt wird jede Textdatei in der Wurzel gelistet."""
    from app.services.file_service import list_files

    class _Server:
        base_path = str(tmp_path)

    for name in ("server.properties", "spigot.yml", "bukkit.yml", "commands.yml",
                 "permissions.yml", "eula.txt", "paper.yml", "purpur.yml"):
        (tmp_path / name).write_text("x: 1", encoding="utf-8")
    # Nicht-Textdateien bleiben aussen vor.
    (tmp_path / "server.jar").write_bytes(b"\x00binaer")
    (tmp_path / "icon.png").write_bytes(b"\x89PNG")

    dateien = list_files(_Server())

    assert "spigot.yml" in dateien
    assert "bukkit.yml" in dateien
    assert "paper.yml" in dateien
    assert "purpur.yml" in dateien
    assert "commands.yml" in dateien
    assert "server.jar" not in dateien
    assert "icon.png" not in dateien
    # server.properties zuerst, spigot.yml direkt danach.
    assert dateien[0] == "server.properties"
    assert dateien[1] == "spigot.yml"


def test_dateimanager_findet_papers_config_ordner(tmp_path, client):
    """Ab 26.1 liegen Papers Einstellungen in config/ - die wird rekursiv durchsucht."""
    from app.services.file_service import list_files

    class _Server:
        base_path = str(tmp_path)

    (tmp_path / "config").mkdir()
    (tmp_path / "config" / "paper-global.yml").write_text("proxies: {}", encoding="utf-8")
    (tmp_path / "config" / "paper-world-defaults.yml").write_text("x: 1", encoding="utf-8")

    dateien = list_files(_Server())

    assert "config/paper-global.yml" in dateien
    assert "config/paper-world-defaults.yml" in dateien


def test_dateimanager_sortiert_wurzel_vor_unterordnern(tmp_path, client):
    from app.services.file_service import list_files

    class _Server:
        base_path = str(tmp_path)

    (tmp_path / "spigot.yml").write_text("x: 1", encoding="utf-8")
    (tmp_path / "plugins" / "Irgendwas").mkdir(parents=True)
    (tmp_path / "plugins" / "Irgendwas" / "config.yml").write_text("y: 2", encoding="utf-8")
    (tmp_path / "zzz-eigene.yml").write_text("z: 3", encoding="utf-8")

    dateien = list_files(_Server())

    # Bekannte zuerst, dann unbekannte aus der Wurzel, dann Unterordner.
    assert dateien.index("spigot.yml") < dateien.index("zzz-eigene.yml")
    assert dateien.index("zzz-eigene.yml") < dateien.index("plugins/Irgendwas/config.yml")


# =========================================================================================
# Sortierung und Suche im Dateimanager.
# =========================================================================================

def _login_admin_files(client):
    antwort = client.post("/login", data={"username": "admin", "password": "admin123!"},
                          follow_redirects=False)
    assert antwort.status_code == 303


def _dateiserver(db, basis, *, slug="sortserver", port=25777):
    from app.models.server import Server

    basis.mkdir(parents=True, exist_ok=True)
    srv = Server(name=f"Server-{slug}", slug=slug, server_type="spigot",
                 mc_version="1.21", base_path=str(basis), port=port, status="stopped",
                 start_mode="command", start_command="java -jar server.jar")
    db.add(srv)
    db.commit()
    db.refresh(srv)
    return srv


def test_sortierung_nach_name(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import file_service as fs

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s")
        for n in ("zebra.yml", "alpha.yml", "mitte.yml"):
            (tmp_path / "s" / n).write_text("x: 1", encoding="utf-8")

        auf = fs.liste_mit_metadaten(srv, db, sortierung="name", richtung="auf")
        ab = fs.liste_mit_metadaten(srv, db, sortierung="name", richtung="ab")

    namen_auf = [d["pfad"] for d in auf["dateien"]]
    namen_ab = [d["pfad"] for d in ab["dateien"]]
    assert namen_auf == sorted(namen_auf)
    assert namen_ab == list(reversed(namen_auf))


def test_sortierung_nach_groesse(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import file_service as fs

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s", slug="groesse", port=25778)
        (tmp_path / "s" / "klein.yml").write_text("x", encoding="utf-8")
        (tmp_path / "s" / "gross.yml").write_text("y" * 5000, encoding="utf-8")

        ab = fs.liste_mit_metadaten(srv, db, sortierung="groesse", richtung="ab")

    assert [d["pfad"] for d in ab["dateien"]][:2] == ["gross.yml", "klein.yml"]
    assert ab["dateien"][0]["groesse"] > ab["dateien"][1]["groesse"]


def test_sortierung_nach_haeufigkeit_zaehlt_oeffnungen(tmp_path, client):
    """Nutzungshaeufigkeit misst, was der NUTZER im Manager oeffnet - nicht, was der
    Server schreibt (latest.log aendert sich dauernd, ohne dass es jemand ansieht)."""
    from app.db.session import SessionLocal
    from app.services import file_service as fs

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s", slug="haeufig", port=25779)
        for n in ("selten.yml", "oft.yml", "nie.yml"):
            (tmp_path / "s" / n).write_text("x: 1", encoding="utf-8")

        for _ in range(5):
            fs.zaehle_oeffnung(db, srv, "oft.yml")
        fs.zaehle_oeffnung(db, srv, "selten.yml")

        ab = fs.liste_mit_metadaten(srv, db, sortierung="haeufigkeit", richtung="ab")
        eintraege = {d["pfad"]: d["anzahl"] for d in ab["dateien"]}

    assert eintraege["oft.yml"] == 5
    assert eintraege["selten.yml"] == 1
    assert eintraege["nie.yml"] == 0
    assert [d["pfad"] for d in ab["dateien"]][:2] == ["oft.yml", "selten.yml"]


def test_nie_geoeffnete_stehen_bei_zuletzt_hinten(tmp_path, client):
    """Sonst stehen sie mitten in der Liste und verdecken, was wirklich zuletzt dran war."""
    from app.db.session import SessionLocal
    from app.services import file_service as fs

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s", slug="zuletzt", port=25780)
        for n in ("a.yml", "b.yml", "c.yml"):
            (tmp_path / "s" / n).write_text("x: 1", encoding="utf-8")
        fs.zaehle_oeffnung(db, srv, "b.yml")

        ab = fs.liste_mit_metadaten(srv, db, sortierung="zuletzt", richtung="ab")

    assert [d["pfad"] for d in ab["dateien"]][0] == "b.yml"


def test_gleicher_wert_behaelt_die_empfohlene_reihenfolge(tmp_path, client):
    """Bei gleichem Sortierwert (hier: alle nie geoeffnet) gilt die empfohlene Folge -
    bekannte Dateien vor unbekannten, Wurzel vor Unterordnern. Sonst steht eine
    zufaellige Plugin-Config vor server.properties."""
    from app.db.session import SessionLocal
    from app.services import file_service as fs

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s", slug="stabil", port=25781)
        (tmp_path / "s" / "server.properties").write_text("x=1", encoding="utf-8")
        (tmp_path / "s" / "aaa-eigene.yml").write_text("x: 1", encoding="utf-8")
        (tmp_path / "s" / "plugins" / "X").mkdir(parents=True)
        (tmp_path / "s" / "plugins" / "X" / "config.yml").write_text("y: 2",
                                                                    encoding="utf-8")

        namen = [d["pfad"] for d in fs.liste_mit_metadaten(
            srv, db, sortierung="haeufigkeit", richtung="auf")["dateien"]]

    # Alle haben 0 Oeffnungen - entscheidend ist der Zweitschluessel.
    assert namen.index("server.properties") < namen.index("aaa-eigene.yml")
    assert namen.index("aaa-eigene.yml") < namen.index("plugins/X/config.yml")
    # Und zweimal aufgerufen kommt dasselbe heraus.
    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s2", slug="stabil2", port=25791)
        (tmp_path / "s2" / "a.yml").write_text("x", encoding="utf-8")
        (tmp_path / "s2" / "b.yml").write_text("x", encoding="utf-8")
        erst = [d["pfad"] for d in fs.liste_mit_metadaten(srv, db)["dateien"]]
        zweit = [d["pfad"] for d in fs.liste_mit_metadaten(srv, db)["dateien"]]
    assert erst == zweit


def test_suche_filtert_ueber_alle_dateien(tmp_path, client):
    """Die Suche muss auch finden, was jenseits der Anzeigegrenze liegt - bei einem
    Modpack mit tausenden Configs ist das der Unterschied zwischen "gefunden" und
    "existiert angeblich nicht"."""
    from app.db.session import SessionLocal
    from app.services import file_service as fs

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s", slug="suche", port=25782)
        (tmp_path / "s" / "config").mkdir()
        for i in range(30):
            (tmp_path / "s" / "config" / f"fuellmaterial{i}.yml").write_text(
                "x: 1", encoding="utf-8")
        (tmp_path / "s" / "config" / "gesuchtes-ding.yml").write_text(
            "x: 1", encoding="utf-8")

        ergebnis = fs.liste_mit_metadaten(srv, db, suche="gesuchtes", max_files=5)

    assert ergebnis["gesamt"] == 1
    assert ergebnis["dateien"][0]["pfad"] == "config/gesuchtes-ding.yml"


def test_suche_ist_gross_klein_unabhaengig(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import file_service as fs

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s", slug="gross", port=25783)
        (tmp_path / "s" / "SpigotEinstellung.yml").write_text("x: 1", encoding="utf-8")

        assert fs.liste_mit_metadaten(srv, db, suche="spigot")["gesamt"] == 1
        assert fs.liste_mit_metadaten(srv, db, suche="SPIGOT")["gesamt"] == 1


def test_gekuerzte_liste_wird_gemeldet(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import file_service as fs

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s", slug="kurz", port=25784)
        (tmp_path / "s" / "config").mkdir()
        for i in range(12):
            (tmp_path / "s" / "config" / f"d{i}.yml").write_text("x", encoding="utf-8")

        ergebnis = fs.liste_mit_metadaten(srv, db, max_files=5)

    assert ergebnis["gekuerzt"] is True
    assert len(ergebnis["dateien"]) == 5
    assert ergebnis["gesamt"] == 12


def test_unbekannte_sortierung_faellt_auf_empfohlen_zurueck(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import file_service as fs

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s", slug="unbekannt", port=25785)
        (tmp_path / "s" / "server.properties").write_text("x=1", encoding="utf-8")

        ergebnis = fs.liste_mit_metadaten(srv, db, sortierung="quatsch",
                                          richtung="seitwaerts")

    assert ergebnis["sortierung"] == "empfohlen"
    assert ergebnis["richtung"] == "auf"


def test_standardrichtung_je_sortierung(client):
    """Namen aufsteigend, Zeiten und Zahlen absteigend - das Neueste zuerst."""
    from app.services import file_service as fs

    assert fs.normalisiere_richtung(None, "name") == "auf"
    assert fs.normalisiere_richtung(None, "zuletzt") == "ab"
    assert fs.normalisiere_richtung(None, "haeufigkeit") == "ab"
    assert fs.normalisiere_richtung(None, "geaendert") == "ab"
    assert fs.normalisiere_richtung(None, "groesse") == "ab"
    # Eine ausdrueckliche Angabe gewinnt.
    assert fs.normalisiere_richtung("auf", "zuletzt") == "auf"


def test_alle_sortierungen_laufen_durch(tmp_path, client):
    from app.db.session import SessionLocal
    from app.services import file_service as fs

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s", slug="alle", port=25790)
        (tmp_path / "s" / "spigot.yml").write_text("x: 1", encoding="utf-8")
        (tmp_path / "s" / "a.yml").write_text("y: 2", encoding="utf-8")

        for art in fs.SORTIERUNGEN:
            for richtung in ("auf", "ab"):
                ergebnis = fs.liste_mit_metadaten(srv, db, sortierung=art,
                                                  richtung=richtung)
                assert len(ergebnis["dateien"]) == 2, f"{art}/{richtung}"
                assert ergebnis["sortierung"] == art
                assert ergebnis["richtung"] == richtung


def test_zaehlung_vertraegt_unsinnige_pfade(tmp_path, client):
    """Eine fehlende Statistik darf das Anzeigen einer Datei nie verhindern."""
    from app.db.session import SessionLocal
    from app.services import file_service as fs

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s", slug="robust", port=25786)
        fs.zaehle_oeffnung(db, srv, "")
        fs.zaehle_oeffnung(db, srv, "../ausserhalb.yml")
        assert fs.liste_mit_metadaten(srv, db)["gesamt"] == 0


def test_dateiseite_zeigt_suche_und_sortierung(tmp_path, client):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s", slug="seite", port=25787)
        (tmp_path / "s" / "spigot.yml").write_text("x: 1", encoding="utf-8")
        srv_id = srv.id

    _login_admin_files(client)
    seite = client.get(f"/servers/{srv_id}/files")
    assert seite.status_code == 200
    assert 'id="file-search"' in seite.text
    assert 'id="file-sort-select"' in seite.text
    assert "Nutzungshaeufigkeit" in seite.text
    assert "spigot.yml" in seite.text


def test_dateiseite_nimmt_sortierung_und_suche_an(tmp_path, client):
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s", slug="param", port=25788)
        (tmp_path / "s" / "spigot.yml").write_text("x: 1", encoding="utf-8")
        (tmp_path / "s" / "bukkit.yml").write_text("y: 2", encoding="utf-8")
        srv_id = srv.id

    _login_admin_files(client)
    seite = client.get(f"/servers/{srv_id}/files?sort=name&dir=ab&q=spig")
    assert seite.status_code == 200
    assert "spigot.yml" in seite.text
    assert "Treffer" in seite.text


def test_oeffnen_wird_gezaehlt(tmp_path, client):
    """Nach dem Oeffnen im Dateimanager muss die Haeufigkeit steigen."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import file_service as fs

    with SessionLocal() as db:
        srv = _dateiserver(db, tmp_path / "s", slug="zaehlen", port=25789)
        (tmp_path / "s" / "spigot.yml").write_text("x: 1", encoding="utf-8")
        srv_id = srv.id

    _login_admin_files(client)
    client.get(f"/servers/{srv_id}/files?file=spigot.yml&mode=raw")
    client.get(f"/servers/{srv_id}/files?file=spigot.yml&mode=raw")

    with SessionLocal() as db:
        srv = db.get(Server, srv_id)
        eintraege = {d["pfad"]: d["anzahl"]
                     for d in fs.liste_mit_metadaten(srv, db)["dateien"]}
    assert eintraege["spigot.yml"] == 2
