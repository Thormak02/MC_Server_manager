def _login_admin(client):
    response = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert response.status_code == 303


def _import_server(client, server_dir, *, name="Console Server"):
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


def test_console_and_logs_pages_are_available(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "console_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text(
        "@echo off\necho Booting server\nping -n 5 127.0.0.1 >nul\n",
        encoding="utf-8",
    )
    server_location = _import_server(client, server_dir)

    console_response = client.get(f"{server_location}/console")
    assert console_response.status_code == 200
    assert "Konsole" in console_response.text

    logs_response = client.get(f"{server_location}/logs")
    assert logs_response.status_code == 200
    assert "Logs:" in logs_response.text

    command_response = client.post(
        f"{server_location}/console/command",
        data={"command": "say hello"},
        follow_redirects=False,
    )
    assert command_response.status_code == 303


def test_super_admin_can_open_audit_logs(client):
    _login_admin(client)
    response = client.get("/audit-logs")
    assert response.status_code == 200
    assert "Audit Logs" in response.text


# ------------------------------------------------- Die ANTWORT auf einen Befehl
#
# DAS LOCH, DAS DIESE PROBEN FESTNAGELN [geschlossen 26.11.2026]:
# ``send_console_command`` meldete "Befehl gesendet.", sobald die Zeile in stdin
# stand. Was der Server darauf sagte, sah niemand - ein
# "/freimarken geld Ana 5000" mit einem Tippfehler im Namen meldete also ERFOLG,
# und der Betreiber glaubte, die Korrektur sei gebucht. Genau bei Geld und Besitz
# ist eine falsche Erfolgsmeldung am teuersten.


def test_die_antwort_des_servers_steht_in_der_meldung():
    from app.services import console_service as modul
    from app.services.process_service import _antwort_abwarten

    dienst = modul.console_service
    marke = dienst.gezaehlt(4242)
    dienst.append_output(4242, "> freimarken geld Ana 5000")
    dienst.append_output(4242, "Den Spieler Ana kennt dieser Server nicht.")

    meldung = _antwort_abwarten(4242, marke)

    assert "kennt dieser Server nicht" in meldung, (
        "die Antwort des Servers gehoert in die Meldung - sonst liest der"
        f" Betreiber Erfolg, wo keiner war: {meldung}")
    assert "freimarken geld Ana" not in meldung, (
        "die eigene Zeile ist die FRAGE und nicht die Antwort")


def test_ein_schweigen_wird_nicht_als_erfolg_verkauft():
    """Viele Befehle antworten nichts - und das darf nicht wie Erfolg klingen."""
    from app.services import console_service as modul
    from app.services.process_service import _antwort_abwarten

    dienst = modul.console_service
    marke = dienst.gezaehlt(4243)
    dienst.append_output(4243, "> say hallo")

    meldung = _antwort_abwarten(4243, marke)

    assert "KEIN Beweis" in meldung, (
        "ein Schweigen ist kein Erfolg, und die Meldung muss das sagen:"
        f" {meldung}")


def test_die_marke_zaehlt_auch_ueber_den_rand_des_puffers():
    """Der Zaehler ist eine MARKE und keine Laenge.

    Der Ringpuffer haelt tausend Zeilen. Eine Probe, die mit seiner LAENGE
    rechnete, waere auf einem stillen Server gruen und auf einem belebten falsch -
    genau dort, wo ein Betreiber eine Geldkorrektur sendet.
    """
    from app.services import console_service as modul

    dienst = modul.console_service
    marke = dienst.gezaehlt(4244)
    for i in range(1100):
        dienst.append_output(4244, f"Zeile {i}")

    assert dienst.gezaehlt(4244) - marke == 1100, (
        "der Zaehler muss ALLE zaehlen, auch die, die der Puffer vorne verloren"
        " hat")
    letzte = [z.split("] ", 1)[-1]
              for z in dienst.seit(4244, marke, hoechstens=3)]
    assert letzte == ["Zeile 1097", "Zeile 1098", "Zeile 1099"], (
        "und seit() darf nie mehr Zeilen zurueckgeben, als noch da sind: "
        + str(letzte))
