"""Modpack-Erkennung: Mod-IDs harvesten + passenden Backend finden."""

import zipfile


def _make_neoforge_jar(mods_dir, name, mod_ids):
    mods_dir.mkdir(parents=True, exist_ok=True)
    toml = "\n".join(f'[[mods]]\nmodId="{m}"\n' for m in mod_ids)
    with zipfile.ZipFile(mods_dir / f"{name}.jar", "w") as zf:
        zf.writestr("META-INF/neoforge.mods.toml", toml)


def _make_fabric_jar(mods_dir, name, mod_id):
    mods_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(mods_dir / f"{name}.jar", "w") as zf:
        zf.writestr("fabric.mod.json", f'{{"id": "{mod_id}"}}')


def test_harvest_reads_mod_ids_all_loaders(tmp_path):
    from app.services import modpack_router_service as mr

    srv = tmp_path / "srv"
    mods = srv / "mods"
    _make_neoforge_jar(mods, "atm-core", ["atmcore", "mekanism"])
    _make_fabric_jar(mods, "sodium", "sodium")

    ids = mr.harvest_server_mod_ids(str(srv))
    assert {"atmcore", "mekanism", "sodium"} <= set(ids)


def test_harvest_empty_without_mods_dir(tmp_path):
    from app.services import modpack_router_service as mr

    srv = tmp_path / "vanilla"
    srv.mkdir()
    assert mr.harvest_server_mod_ids(str(srv)) == frozenset()


def test_match_backend_picks_most_specific_pack(client, tmp_path):
    from app.services import modpack_router_service as mr
    from app.db.session import SessionLocal
    from app.models.server import Server

    atm_dir = tmp_path / "atm"
    tts_dir = tmp_path / "tts"
    # ATM10: viele Mods (spezifisch). Through the Seasons: kleinere Schnittmenge.
    _make_neoforge_jar(atm_dir / "mods", "atm", ["atmcore", "mekanism", "create", "ae2"])
    _make_neoforge_jar(tts_dir / "mods", "tts", ["seasons", "create"])

    with SessionLocal() as db:
        db.add_all([
            Server(name="ATM10", slug="atm10", server_type="neoforge", mc_version="1.21.1",
                   base_path=str(atm_dir), gateway_enabled=True, gateway_hostname="atm10", port=25601),
            Server(name="TTS", slug="tts", server_type="neoforge", mc_version="1.21.1",
                   base_path=str(tts_dir), gateway_enabled=True, gateway_hostname="tts", port=25602),
        ])
        db.commit()

        # Client hat ATM10s Server-Mods (plus Clientside-Extra "sodium", das ignoriert wird).
        atm_id = db.scalar(__import__("sqlalchemy").select(Server.id).where(Server.slug == "atm10"))
        sid, reason = mr.match_backend_for_client(
            db, {"atmcore", "mekanism", "create", "ae2", "sodium"})
        assert sid == atm_id, reason

        # Client hat nur die TTS-Mods -> TTS (ATM10 waere nicht vollstaendig abgedeckt).
        tts_id = db.scalar(__import__("sqlalchemy").select(Server.id).where(Server.slug == "tts"))
        sid2, _ = mr.match_backend_for_client(db, {"seasons", "create"})
        assert sid2 == tts_id


def test_match_backend_none_for_vanilla(client, tmp_path):
    from app.services import modpack_router_service as mr
    from app.db.session import SessionLocal

    with SessionLocal() as db:
        sid, reason = mr.match_backend_for_client(db, set())
    assert sid is None
    assert "vanilla" in reason.lower() or "client-only" in reason.lower()


def test_match_backend_none_when_no_pack_covers(client, tmp_path):
    from app.services import modpack_router_service as mr
    from app.db.session import SessionLocal
    from app.models.server import Server

    atm_dir = tmp_path / "atm2"
    _make_neoforge_jar(atm_dir / "mods", "atm", ["atmcore", "mekanism"])
    with SessionLocal() as db:
        db.add(Server(name="ATM10", slug="atm10b", server_type="neoforge", mc_version="1.21.1",
                      base_path=str(atm_dir), gateway_enabled=True, gateway_hostname="atm10", port=25603))
        db.commit()
        # Client verlangt einen Mod, den kein Server hat -> kein Match.
        sid, _ = mr.match_backend_for_client(db, {"someunknownmod"})
    assert sid is None
