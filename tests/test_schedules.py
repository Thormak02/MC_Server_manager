import re


def _login_admin(client):
    response = client.post(
        "/login",
        data={"username": "admin", "password": "admin123!"},
        follow_redirects=False,
    )
    assert response.status_code == 303


def _import_server(client, server_dir, *, name="Schedule Server"):
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
    match = re.search(r"/servers/(\d+)", response.headers["location"])
    assert match
    return int(match.group(1))


def test_create_schedule_job(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "schedule_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\ntimeout /t 10 >nul\n", encoding="utf-8")
    server_id = _import_server(client, server_dir)

    create_response = client.post(
        f"/servers/{server_id}/schedules",
        data={
            "job_type": "restart",
            "schedule_expression": "interval:60",
            "delay_seconds": "5",
            "warning_message": "Restart in {seconds}",
        },
        follow_redirects=True,
    )
    assert create_response.status_code == 200
    assert "interval:60" in create_response.text
    assert "restart" in create_response.text


def test_calendar_shows_recurring_job_on_every_day(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "calendar_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\n", encoding="utf-8")
    server_id = _import_server(client, server_dir)

    # Taeglicher Restart um 04:00 -> muss an jedem Tag des Monats erscheinen,
    # nicht nur an einem einzelnen (next_run_at).
    client.post(
        f"/servers/{server_id}/schedules",
        data={
            "job_type": "restart",
            "schedule_mode": "daily",
            "planner_time": "04:00",
            "planner_date": "2026-07-15",
        },
        follow_redirects=True,
    )

    page = client.get(
        f"/servers/{server_id}/schedules?year=2026&month=7"
    ).text
    pill_count = page.count('class="calendar-event"')
    # Juli hat 31 Tage; ein einzelner next_run_at wuerde nur 1 Pill rendern.
    assert pill_count >= 28, f"nur {pill_count} Kalender-Events gerendert"
    assert 'data-type="restart"' in page


def test_manual_restart_with_delay_and_warning(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "restart_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\ntimeout /t 30 >nul\n", encoding="utf-8")
    server_id = _import_server(client, server_dir)

    client.post(f"/servers/{server_id}/start", follow_redirects=False)
    restart_response = client.post(
        f"/servers/{server_id}/restart",
        data={"delay_seconds": "1", "warning_message": "Restart in {seconds} sec"},
        follow_redirects=True,
    )
    assert restart_response.status_code == 200
    assert "Neustart geplant in 1 Sekunden." in restart_response.text


def test_restart_via_console_command_is_supported(client, tmp_path):
    _login_admin(client)
    server_dir = tmp_path / "console_restart_srv"
    server_dir.mkdir()
    (server_dir / "start.bat").write_text("@echo off\ntimeout /t 10 >nul\n", encoding="utf-8")
    server_id = _import_server(client, server_dir)

    client.post(f"/servers/{server_id}/start", follow_redirects=False)
    response = client.post(
        f"/servers/{server_id}/console/command",
        data={"command": "/restart"},
        follow_redirects=True,
    )
    assert response.status_code == 200
    assert "Neustart geplant in 1 Sekunden." in response.text


def test_scheduled_restart_skips_a_stopped_server(client, tmp_path, monkeypatch):
    """Ein geplanter Restart darf einen stillgelegten Server nicht hochfahren.

    Genau das passierte: ein Server stand jeden Morgen wieder da, obwohl Autostart aus
    und das Gateway-Routing entfernt war - der taegliche Restart-Job hat beides
    ueberfahren. Fuer einen geplanten START gibt es den eigenen Job-Typ "start".
    """
    from types import SimpleNamespace

    from app.services import schedule_service

    gerufen = []
    monkeypatch.setattr(schedule_service, "is_running", lambda sid: False)
    monkeypatch.setattr(schedule_service, "queue_restart",
                        lambda *a, **kw: gerufen.append("restart") or (True, "gestartet"))
    monkeypatch.setattr(schedule_service, "_record_job_history_start", lambda db, job: None)
    monkeypatch.setattr(schedule_service, "_record_job_history_finish",
                        lambda db, row, **kw: None)
    monkeypatch.setattr(schedule_service.audit_service, "log_action", lambda *a, **kw: None)

    class _Db:
        def get(self, _model, _pk):
            return SimpleNamespace(id=4, name="David", base_path="x")

        def add(self, _row):
            pass

        def commit(self):
            pass

    job = SimpleNamespace(id=4, server_id=4, job_type="restart", command_payload="{}")
    ok, message = schedule_service._execute_job(_Db(), job)

    assert gerufen == []                       # queue_restart wurde NICHT gerufen
    assert ok is True                          # ein Uebersprung ist kein Fehler
    assert "uebersprungen" in message.lower()


def test_scheduled_restart_still_restarts_a_running_server(client, tmp_path, monkeypatch):
    from types import SimpleNamespace

    from app.services import schedule_service

    gerufen = []
    monkeypatch.setattr(schedule_service, "is_running", lambda sid: True)
    monkeypatch.setattr(schedule_service, "queue_restart",
                        lambda *a, **kw: gerufen.append("restart") or (True, "neu gestartet"))
    monkeypatch.setattr(schedule_service, "_record_job_history_start", lambda db, job: None)
    monkeypatch.setattr(schedule_service, "_record_job_history_finish",
                        lambda db, row, **kw: None)
    monkeypatch.setattr(schedule_service.audit_service, "log_action", lambda *a, **kw: None)

    class _Db:
        def get(self, _model, _pk):
            return SimpleNamespace(id=2, name="ATM10SKY", base_path="x")

        def add(self, _row):
            pass

        def commit(self):
            pass

    job = SimpleNamespace(id=1, server_id=2, job_type="restart",
                          command_payload='{"delay_seconds": 5}')
    ok, message = schedule_service._execute_job(_Db(), job)

    assert gerufen == ["restart"]
    assert ok is True and "neu gestartet" in message
