"""Vorab-Pruefung fuer den Serverwechsel aus der Lobby (Graceful Rejection).

Ein nativer Transfer trennt die Lobby-Verbindung - deshalb wird VOR dem Wechsel geprueft,
ob der Spieler auf dem Ziel-Server ueberhaupt landen darf.
"""
from types import SimpleNamespace

import pytest

from app.services import lobby_service


def _server(**kw):
    base = {"id": 7, "name": "David", "sleep_enabled": False, "base_path": "x"}
    base.update(kw)
    return SimpleNamespace(**base)


@pytest.fixture()
def _env(monkeypatch):
    """Default: Server laeuft, keine Whitelist, keine Bans, nicht voll."""
    monkeypatch.setattr("app.services.process_service.is_running", lambda sid: True)
    monkeypatch.setattr("app.services.process_service.get_player_counts", lambda s: (1, 20))
    monkeypatch.setattr("app.services.file_service.get_whitelist_enabled", lambda s: False)
    monkeypatch.setattr("app.services.file_service.list_access_entries", lambda s, k: [])
    # Ziel antwortet auf den Status-Ping, nennt aber keine Spielerzahlen -> Fallback greift.
    monkeypatch.setattr(lobby_service, "_ping_local", lambda s: {})
    return monkeypatch


def test_allowed_by_default(_env):
    ok, reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is True and reason == ""


def test_offline_without_sleep_is_rejected(_env):
    _env.setattr("app.services.process_service.is_running", lambda sid: False)
    ok, reason = lobby_service.check_join_allowed(None, _server(sleep_enabled=False), "Thormak")
    assert ok is False and "offline" in reason.lower()


def test_offline_with_sleep_is_allowed(_env):
    """Sleep-Server werden beim Beitritt geweckt -> kein Grund abzulehnen."""
    _env.setattr("app.services.process_service.is_running", lambda sid: False)
    ok, _reason = lobby_service.check_join_allowed(None, _server(sleep_enabled=True), "Thormak")
    assert ok is True


def test_banned_player_is_rejected(_env):
    _env.setattr("app.services.file_service.list_access_entries",
                 lambda s, k: [{"name": "Thormak", "reason": "Griefing"}] if k == "banned_players" else [])
    ok, reason = lobby_service.check_join_allowed(None, _server(), "thormak")   # Case egal
    assert ok is False and "gebannt" in reason.lower() and "Griefing" in reason


def test_whitelist_blocks_unlisted_player(_env):
    _env.setattr("app.services.file_service.get_whitelist_enabled", lambda s: True)
    _env.setattr("app.services.file_service.list_access_entries",
                 lambda s, k: [{"name": "Jemand"}] if k == "whitelist" else [])
    ok, reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is False and "whitelist" in reason.lower()


def test_whitelist_allows_listed_player(_env):
    _env.setattr("app.services.file_service.get_whitelist_enabled", lambda s: True)
    _env.setattr("app.services.file_service.list_access_entries",
                 lambda s, k: [{"name": "Thormak"}] if k == "whitelist" else [])
    ok, _reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is True


def test_empty_whitelist_does_not_lock_out(_env):
    """Fail-open: Whitelist an, aber Datei leer/unlesbar -> nicht aussperren."""
    _env.setattr("app.services.file_service.get_whitelist_enabled", lambda s: True)
    _env.setattr("app.services.file_service.list_access_entries", lambda s, k: [])
    ok, _reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is True


def test_full_server_is_rejected(_env):
    _env.setattr("app.services.process_service.get_player_counts", lambda s: (20, 20))
    ok, reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is False and "voll" in reason.lower()


def test_unreadable_files_fail_open(_env):
    def boom(*a, **k):
        raise OSError("Ordner weg")

    _env.setattr("app.services.file_service.get_whitelist_enabled", boom)
    _env.setattr("app.services.file_service.list_access_entries", boom)
    ok, _reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is True


def test_missing_server_is_rejected(_env):
    ok, reason = lobby_service.check_join_allowed(None, None, "Thormak")
    assert ok is False and "nicht gefunden" in reason.lower()


def test_running_but_not_yet_accepting_is_rejected(_env):
    """Prozess laeuft, Port noch zu (Welt/Mods laden) -> Transfer wuerde ins Leere laufen."""
    _env.setattr(lobby_service, "_ping_local", lambda s: None)
    ok, reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is False and "startet noch" in reason.lower()


def test_sleeping_server_without_ping_is_still_allowed(_env):
    """Schlafender Server: der Transfer WECKT ihn - kein Ping noetig, kein Ablehnen."""
    _env.setattr("app.services.process_service.is_running", lambda sid: False)
    _env.setattr(lobby_service, "_ping_local", lambda s: None)
    ok, reason = lobby_service.check_join_allowed(None, _server(sleep_enabled=True), "Thormak")
    assert ok is True and reason == ""


def test_ping_player_counts_beat_process_estimate(_env):
    """Der Server selbst sagt 'voll' - auch wenn die Prozess-Schaetzung Platz sieht."""
    _env.setattr("app.services.process_service.get_player_counts", lambda s: (1, 20))
    _env.setattr(lobby_service, "_ping_local",
                 lambda s: {"players": {"online": 20, "max": 20}})
    ok, reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is False and "voll (20/20)" in reason


def test_ping_reports_free_slots_despite_stale_process_counts(_env):
    _env.setattr("app.services.process_service.get_player_counts", lambda s: (20, 20))
    _env.setattr(lobby_service, "_ping_local",
                 lambda s: {"players": {"online": 3, "max": 20}})
    ok, reason = lobby_service.check_join_allowed(None, _server(), "Thormak")
    assert ok is True and reason == ""


# --------------------------------------------------------------------------- #
# Client-Abgleich: er kommt IMMER nach den harten Gruenden und erzeugt hoechstens
# eine Rueckfrage. Ein Brand ist ein freier String vom Client - faelschbar.
# --------------------------------------------------------------------------- #
from app.services import join_match_service  # noqa: E402

_VANILLA = join_match_service.ClientInfo(brand="vanilla", source="bukkit")

_CONFIRM_FIT = join_match_service.Fit(
    level=join_match_service.LEVEL_CONFIRM,
    text="David laeuft mit NeoForge 1.21.1 - dein Client meldet sich als Vanilla.",
    short="Braucht NeoForge 1.21.1",
    code="loader",
)


def _confirming(_env):
    """Client-Abgleich so verbiegen, dass er IMMER eine Rueckfrage liefert."""
    _env.setattr(join_match_service, "evaluate_fit", lambda db, srv, cl: _CONFIRM_FIT)


def _exploding(_env):
    """Client-Abgleich, der knallt - er darf das harte Urteil nie veraendern."""
    def boom(db, srv, cl):
        raise RuntimeError("Mod-Ernte kaputt")

    _env.setattr(join_match_service, "evaluate_fit", boom)


class _FakeSession:
    """Session-Ersatz fuer die *_by_id-Funktionen - ohne echte DB, ohne Netz."""

    def __init__(self, server):
        self._server = server

    def get(self, _model, _pk):
        return self._server

    def __enter__(self):
        return self

    def __exit__(self, *_exc):
        return False


def test_evaluate_join_ok_is_quiet(_env):
    verdict = lobby_service.evaluate_join(None, _server(), "Thormak")
    assert verdict.ok is True
    assert verdict.reason == "" and verdict.confirm is False and verdict.note == ""


def test_check_join_allowed_stays_a_tuple(_env):
    """Der alte Aufruf bleibt Wort fuer Wort derselbe - nur eben als Wrapper."""
    assert lobby_service.check_join_allowed(None, _server(), "Thormak") == (True, "")


_HARTE_GRUENDE = [
    (lambda e: e.setattr("app.services.process_service.is_running", lambda sid: False),
     "David ist offline."),
    (lambda e: e.setattr("app.services.file_service.list_access_entries",
                         lambda s, k: [{"name": "Thormak"}] if k == "banned_players" else []),
     "Du bist auf David gebannt."),
    (lambda e: e.setattr("app.services.process_service.get_player_counts",
                         lambda s: (20, 20)),
     "David ist voll (20/20)."),
    (lambda e: e.setattr(lobby_service, "_ping_local", lambda s: None),
     "David startet noch - gleich nochmal versuchen."),
]


@pytest.mark.parametrize("prepare,erwartet", _HARTE_GRUENDE)
def test_hard_reason_wins_over_client_mismatch(_env, prepare, erwartet):
    """Reihenfolge: der harte Grund steht zuerst - woertlich, und ohne confirm.

    Andersherum wuerde ein gefaelschter Brand aus einem Ban eine ueberstimmbare
    Rueckfrage machen.
    """
    _confirming(_env)
    prepare(_env)
    verdict = lobby_service.evaluate_join(object(), _server(), "Thormak", client=_VANILLA)
    assert verdict.ok is False
    assert verdict.reason == erwartet
    assert verdict.confirm is False


@pytest.mark.parametrize("prepare,erwartet", _HARTE_GRUENDE[:3])
def test_override_does_not_unlock_hard_reasons(_env, prepare, erwartet):
    """override ueberspringt NUR den Client-Teil - nie Ban, Whitelist, offline, voll."""
    _confirming(_env)
    prepare(_env)
    verdict = lobby_service.evaluate_join(
        object(), _server(), "Thormak", client=_VANILLA, override=True
    )
    assert verdict.ok is False and verdict.reason == erwartet and verdict.confirm is False


def test_override_does_not_unlock_whitelist(_env):
    _confirming(_env)
    _env.setattr("app.services.file_service.get_whitelist_enabled", lambda s: True)
    _env.setattr("app.services.file_service.list_access_entries",
                 lambda s, k: [{"name": "Jemand"}] if k == "whitelist" else [])
    verdict = lobby_service.evaluate_join(
        object(), _server(), "Thormak", client=_VANILLA, override=True
    )
    assert verdict.ok is False and "Whitelist" in verdict.reason and verdict.confirm is False


def test_client_mismatch_becomes_a_confirm(_env):
    _confirming(_env)
    verdict = lobby_service.evaluate_join(object(), _server(), "Thormak", client=_VANILLA)
    assert verdict.ok is False and verdict.confirm is True
    assert verdict.reason == _CONFIRM_FIT.text and verdict.code == "loader"


def test_override_skips_the_client_part(_env):
    _confirming(_env)
    verdict = lobby_service.evaluate_join(
        object(), _server(), "Thormak", client=_VANILLA, override=True
    )
    assert verdict.ok is True and verdict.confirm is False and verdict.reason == ""


def test_without_client_nothing_changes(_env):
    """Regressionsschutz fuer alte Jars: kein Client -> kein Abgleich."""
    _exploding(_env)   # wuerde knallen, wenn der Client-Teil doch liefe
    verdict = lobby_service.evaluate_join(object(), _server(), "Thormak")
    assert verdict.ok is True and verdict.confirm is False


def test_db_none_disables_the_client_part(_env):
    """Ohne Session gibt es nichts nachzuschlagen - der Abgleich bleibt aus."""
    _exploding(_env)
    verdict = lobby_service.evaluate_join(None, _server(), "Thormak", client=_VANILLA)
    assert verdict.ok is True and verdict.confirm is False


def test_note_never_blocks(_env):
    _env.setattr(join_match_service, "evaluate_fit",
                 lambda db, srv, cl: join_match_service.Fit(note="Andere Mod-Version?"))
    verdict = lobby_service.evaluate_join(object(), _server(), "Thormak", client=_VANILLA)
    assert verdict.ok is True and verdict.note == "Andere Mod-Version?"
    assert verdict.confirm is False and verdict.reason == ""


def test_broken_client_check_keeps_the_hard_verdict(_env):
    """Faellt der Abgleich um, bleibt das Urteil aus Schritt 1 stehen - fail-open."""
    _exploding(_env)
    verdict = lobby_service.evaluate_join(object(), _server(), "Thormak", client=_VANILLA)
    assert verdict.ok is True and verdict.confirm is False


def test_broken_client_check_does_not_rescue_a_ban(_env):
    _exploding(_env)
    _env.setattr("app.services.file_service.list_access_entries",
                 lambda s, k: [{"name": "Thormak"}] if k == "banned_players" else [])
    verdict = lobby_service.evaluate_join(object(), _server(), "Thormak", client=_VANILLA)
    assert verdict.ok is False and verdict.reason == "Du bist auf David gebannt."


def test_real_loader_mismatch_confirms(_env):
    """Ohne Attrappe: echter join_match_service, NeoForge-Pack gegen Vanilla-Client."""
    _env.setattr("app.services.modpack_service.get_server_modpack_state",
                 lambda db, sid: object())
    _env.setattr("app.services.modpack_service.client_pack_hint",
                 lambda db, sid: ("Alles Modded 10", "https://example.invalid/atm10"))
    server = _server(server_type="neoforge", mc_version="1.21.1")
    verdict = lobby_service.evaluate_join(object(), server, "Thormak", client=_VANILLA)
    assert verdict.ok is False and verdict.confirm is True and verdict.code == "loader"
    assert "NeoForge" in verdict.reason and "Vanilla" in verdict.reason


def test_real_plugin_server_takes_every_client(_env):
    """Paper nimmt auch modded Clients - hier darf nie eine Rueckfrage entstehen."""
    server = _server(server_type="paper", mc_version="1.21.1")
    client = join_match_service.ClientInfo(brand="neoforge", source="bukkit")
    verdict = lobby_service.evaluate_join(object(), server, "Thormak", client=client)
    assert verdict.ok is True and verdict.confirm is False


# --------------------------------------------------------------------------- #
# Die *_by_id-Varianten: eigene Session, und bei JEDEM Fehler fail-open.
# --------------------------------------------------------------------------- #
def test_evaluate_join_by_id_uses_its_own_session(_env):
    _env.setattr("app.db.session.SessionLocal", lambda: _FakeSession(_server()))
    verdict = lobby_service.evaluate_join_by_id(7, "Thormak")
    assert verdict.ok is True and verdict.reason == ""


def test_evaluate_join_by_id_passes_client_and_override(_env):
    _confirming(_env)
    _env.setattr("app.db.session.SessionLocal", lambda: _FakeSession(_server()))
    assert lobby_service.evaluate_join_by_id(7, "Thormak", client=_VANILLA).confirm is True
    assert lobby_service.evaluate_join_by_id(
        7, "Thormak", client=_VANILLA, override=True
    ).ok is True


def test_evaluate_join_by_id_unknown_server_is_rejected(_env):
    _env.setattr("app.db.session.SessionLocal", lambda: _FakeSession(None))
    verdict = lobby_service.evaluate_join_by_id(4711, "Thormak")
    assert verdict.ok is False and "nicht gefunden" in verdict.reason.lower()


def test_evaluate_join_by_id_fails_open(_env):
    def boom():
        raise RuntimeError("DB weg")

    _env.setattr("app.db.session.SessionLocal", boom)
    verdict = lobby_service.evaluate_join_by_id(7, "Thormak", client=_VANILLA)
    assert verdict.ok is True and verdict.confirm is False and verdict.reason == ""


def test_check_join_allowed_by_id_stays_a_tuple(_env):
    _env.setattr("app.db.session.SessionLocal", lambda: _FakeSession(_server()))
    assert lobby_service.check_join_allowed_by_id(7, "Thormak") == (True, "")


def test_evaluate_fit_by_id_reports_the_mismatch(_env):
    _env.setattr("app.services.modpack_service.get_server_modpack_state",
                 lambda db, sid: object())
    _env.setattr("app.services.modpack_service.client_pack_hint", lambda db, sid: ("", ""))
    _env.setattr("app.db.session.SessionLocal",
                 lambda: _FakeSession(_server(server_type="neoforge", mc_version="1.21.1")))
    fit = lobby_service.evaluate_fit_by_id(7, _VANILLA)
    assert fit.level == join_match_service.LEVEL_CONFIRM
    assert fit.short == "Braucht NeoForge 1.21.1"


def test_evaluate_fit_by_id_unknown_server_passes(_env):
    """Unbekannte ID -> kein Marker. Ein nicht ermittelbarer Marker darf nie warnen."""
    _env.setattr("app.db.session.SessionLocal", lambda: _FakeSession(None))
    fit = lobby_service.evaluate_fit_by_id(4711, _VANILLA)
    assert fit.level == join_match_service.LEVEL_OK and fit.short == ""


def test_evaluate_fit_by_id_fails_open(_env):
    def boom():
        raise RuntimeError("DB weg")

    _env.setattr("app.db.session.SessionLocal", boom)
    fit = lobby_service.evaluate_fit_by_id(7, _VANILLA)
    assert fit.level == join_match_service.LEVEL_OK and fit.text == ""


class _FakeMultiSession:
    """Session-Ersatz fuer evaluate_fits_by_ids - zaehlt, wie oft geoeffnet wurde."""

    opened = 0

    def __init__(self, by_id):
        self._by_id = by_id
        type(self).opened += 1

    def get(self, _model, pk):
        return self._by_id.get(int(pk))

    def __enter__(self):
        return self

    def __exit__(self, *_exc):
        return False


def test_evaluate_fits_by_ids_uses_one_session_for_the_whole_menu(_env):
    """Ein Menue-Oeffnen darf nicht 27 DB-Sessions kosten - eine reicht."""
    _env.setattr("app.services.modpack_service.get_server_modpack_state",
                 lambda db, sid: object())
    _env.setattr("app.services.modpack_service.client_pack_hint", lambda db, sid: ("", ""))
    rows = {
        2: _server(id=2, name="ATM10SKY", server_type="neoforge", mc_version="1.21.1"),
        5: _server(id=5, name="Seasons", server_type="neoforge", mc_version="1.21.1"),
        7: _server(id=7, name="Lobby", server_type="paper", mc_version="26.2"),
    }
    _FakeMultiSession.opened = 0
    _env.setattr("app.db.session.SessionLocal", lambda: _FakeMultiSession(rows))

    found = lobby_service.evaluate_fits_by_ids([2, 5, 7], _VANILLA)

    assert _FakeMultiSession.opened == 1
    # Nur die Treffer stehen drin; Paper nimmt jeden Client -> taucht gar nicht auf.
    assert sorted(found) == [2, 5]
    assert found[2].short == "Braucht NeoForge 1.21.1"
    assert 7 not in found


def test_evaluate_fits_by_ids_skips_broken_and_unknown_ids(_env):
    _env.setattr("app.services.modpack_service.get_server_modpack_state",
                 lambda db, sid: object())
    _env.setattr("app.services.modpack_service.client_pack_hint", lambda db, sid: ("", ""))
    rows = {5: _server(id=5, name="Seasons", server_type="neoforge", mc_version="1.21.1")}
    _env.setattr("app.db.session.SessionLocal", lambda: _FakeMultiSession(rows))

    found = lobby_service.evaluate_fits_by_ids(
        [4711, "bloedsinn", None, 5, {"a": 1}, "5"], _VANILLA)

    assert sorted(found) == [5]   # "5" und 5 sind dasselbe Ziel


def test_evaluate_fits_by_ids_without_ids_is_empty(_env):
    def never():
        raise AssertionError("ohne IDs darf keine Session aufgehen")

    _env.setattr("app.db.session.SessionLocal", never)
    assert lobby_service.evaluate_fits_by_ids([], _VANILLA) == {}
    assert lobby_service.evaluate_fits_by_ids(None, _VANILLA) == {}


def test_evaluate_fits_by_ids_fails_open(_env):
    """Kaputte Marker-Abfrage -> gar keine Marker. Nie ein falscher Warnhinweis."""
    def boom():
        raise RuntimeError("DB weg")

    _env.setattr("app.db.session.SessionLocal", boom)
    assert lobby_service.evaluate_fits_by_ids([2, 5], _VANILLA) == {}
