"""Client-Passung vor dem Serverwechsel (join_match_service).

Schwerpunkt ist die FAIL-OPEN-Matrix: der Brand ist ein freier, faelschbarer String
vom Client - jede unsichere Lage muss deshalb nachweislich bei "ok" landen. Ein
"block" darf es hier ueberhaupt nicht geben, nur "ok" oder "confirm".

Laeuft ohne DB und ohne Netz: Server sind SimpleNamespace, die DB ist ein beliebiges
Objekt != None, und die beiden Modpack-Zugriffe werden gemonkeypatcht.
"""
from types import SimpleNamespace

import pytest

from app.services import join_match_service as jm
from app.services import modpack_service

# Fuer evaluate_fit ist die DB nur ein Wahrheitswert ("ist ueberhaupt eine da?").
_DB = object()

# 200 Zeichen Muell ohne jeden bekannten Brand-Teilstring.
_JUNK_BRAND = "zq7-" * 50


def _server(**kw):
    base = {
        "id": 7,
        "name": "Davids Welt",
        "server_type": "neoforge",
        "mc_version": "1.21.1",
        "slug": "davids-welt",
        "base_path": "x",
    }
    base.update(kw)
    return SimpleNamespace(**base)


def _client(brand="", mods=(), source="bukkit"):
    return jm.ClientInfo(brand=brand, mods=frozenset(mods), source=source)


@pytest.fixture()
def _pack(monkeypatch):
    """Default: Ziel faehrt ein importiertes Modpack, aber ohne Pack-Hinweis.

    Gepatcht wird auf der Ebene von modpack_service - so laeuft der echte Weg durch
    _has_modpack_state/_pack_hint samt int(server.id) mit.
    """
    monkeypatch.setattr(modpack_service, "get_server_modpack_state",
                        lambda db, sid: SimpleNamespace(id=1, server_id=sid))
    monkeypatch.setattr(modpack_service, "client_pack_hint", lambda db, sid: ("", ""))
    return monkeypatch


@pytest.fixture()
def _no_pack(monkeypatch):
    """Ziel ohne importiertes Modpack (Lithium-Fall)."""
    monkeypatch.setattr(modpack_service, "get_server_modpack_state", lambda db, sid: None)
    monkeypatch.setattr(modpack_service, "client_pack_hint", lambda db, sid: ("", ""))
    return monkeypatch


@pytest.fixture()
def _mods(monkeypatch):
    """Die beiden Mod-Quellen von shared_required_mods setzen (mods-Ordner + Replay)."""
    def _install(installed, pack):
        from app.services import hub_replay_service, modpack_router_service

        monkeypatch.setattr(modpack_router_service, "server_mod_ids_cached",
                            lambda s: frozenset(installed))
        monkeypatch.setattr(hub_replay_service, "replay_path_for",
                            lambda slug: "X:/replays/%s.bin" % slug)
        monkeypatch.setattr(hub_replay_service, "replay_mod_namespaces",
                            lambda path: frozenset(pack))
    return _install


# --------------------------------------------------------------------------- #
# Es gibt kein "block"
# --------------------------------------------------------------------------- #
def test_modul_kennt_kein_block():
    assert jm.LEVEL_OK == "ok"
    assert jm.LEVEL_CONFIRM == "confirm"
    assert not hasattr(jm, "LEVEL_BLOCK")


def test_default_fit_ist_ok():
    fit = jm.Fit()
    assert fit.level == jm.LEVEL_OK
    assert fit.text == "" and fit.short == "" and fit.note == "" and fit.code == "ok"


# --------------------------------------------------------------------------- #
# FAIL-OPEN: Ziel nimmt ohnehin jeden Client
# --------------------------------------------------------------------------- #
@pytest.mark.parametrize("server_type",
                         ["paper", "spigot", "purpur", "folia", "bukkit",
                          "craftbukkit", "vanilla"])
def test_plugin_ziel_meldet_nie_etwas(_pack, server_type):
    """Paper & Co. nehmen auch modded Clients - und zwar SELBST mit importiertem Pack."""
    fit = jm.evaluate_fit(_DB, _server(server_type=server_type), _client(brand="fabric"))
    assert fit.level == jm.LEVEL_OK


def test_plugin_ziel_unabhaengig_von_gross_klein(_pack):
    fit = jm.evaluate_fit(_DB, _server(server_type="PaPeR"), _client(brand="neoforge"))
    assert fit.level == jm.LEVEL_OK


@pytest.mark.parametrize("server_type", ["irgendwas", "", "   ", "modded-2025"])
def test_unbekannter_servertyp_schweigt(_pack, server_type):
    fit = jm.evaluate_fit(_DB, _server(server_type=server_type), _client(brand="vanilla"))
    assert fit.level == jm.LEVEL_OK


def test_fehlendes_servertyp_attribut_schweigt(_pack):
    srv = SimpleNamespace(id=7, name="X", slug="x", base_path="x")
    assert jm.server_loader(srv) is None
    assert jm.evaluate_fit(_DB, srv, _client(brand="vanilla")).level == jm.LEVEL_OK


# --------------------------------------------------------------------------- #
# FAIL-OPEN: kein Pack, kein Urteil
# --------------------------------------------------------------------------- #
def test_neoforge_ohne_importiertes_modpack_schweigt(_no_pack):
    """Ein Loader-Server ohne Pack nimmt Vanilla-Clients oft trotzdem - nichts melden."""
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"), _client(brand="vanilla"))
    assert fit.level == jm.LEVEL_OK


def test_modpack_state_wirft_gilt_als_kein_pack(monkeypatch):
    def _boom(db, sid):
        raise RuntimeError("DB weg")
    monkeypatch.setattr(modpack_service, "get_server_modpack_state", _boom)
    assert jm._has_modpack_state(_DB, _server()) is False
    assert jm.evaluate_fit(_DB, _server(), _client(brand="vanilla")).level == jm.LEVEL_OK


def test_pack_hint_wirft_ergibt_leeres_paar(monkeypatch):
    def _boom(db, sid):
        raise RuntimeError("DB weg")
    monkeypatch.setattr(modpack_service, "client_pack_hint", _boom)
    assert jm._pack_hint(_DB, _server()) == ("", "")


# --------------------------------------------------------------------------- #
# FAIL-OPEN: unbrauchbarer Brand
# --------------------------------------------------------------------------- #
@pytest.mark.parametrize("brand", ["", None, "lunarclient", "badlion", "optifine",
                                   "feather", _JUNK_BRAND, 42, 0, 3.5, b"vanilla",
                                   ("vanilla",)])
def test_unbrauchbarer_brand_schweigt(_pack, brand):
    """Nur Brands, die wir wirklich kennen, duerfen eine Rueckfrage ausloesen."""
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"), _client(brand=brand))
    assert fit.level == jm.LEVEL_OK


@pytest.mark.parametrize("brand", ["", None, "lunarclient", _JUNK_BRAND, 42, b"x"])
def test_normalize_brand_liefert_leer(brand):
    assert jm.normalize_brand(brand) == ""


# --------------------------------------------------------------------------- #
# FAIL-OPEN: fehlende Bausteine
# --------------------------------------------------------------------------- #
def test_db_none_schweigt(_pack):
    assert jm.evaluate_fit(None, _server(), _client(brand="vanilla")).level == jm.LEVEL_OK


def test_server_none_schweigt(_pack):
    assert jm.evaluate_fit(_DB, None, _client(brand="vanilla")).level == jm.LEVEL_OK


def test_client_none_schweigt(_pack):
    assert jm.evaluate_fit(_DB, _server(), None).level == jm.LEVEL_OK


def test_alles_none_schweigt():
    assert jm.evaluate_fit(None, None, None).level == jm.LEVEL_OK


def test_notausgang_schaltet_alles_ab(_pack, monkeypatch):
    """JOIN_MATCH_ENABLED = False muss auch den sicheren Treffer verstummen lassen."""
    laut = jm.evaluate_fit(_DB, _server(server_type="neoforge"), _client(brand="vanilla"))
    assert laut.level == jm.LEVEL_CONFIRM      # ohne Notausgang waere es eine Rueckfrage

    monkeypatch.setattr(jm, "JOIN_MATCH_ENABLED", False)
    still = jm.evaluate_fit(_DB, _server(server_type="neoforge"), _client(brand="vanilla"))
    assert still.level == jm.LEVEL_OK


def test_exception_im_inneren_bleibt_folgenlos(_pack, monkeypatch):
    """Egal wo es knallt - evaluate_fit faellt auf ok zurueck."""
    def _boom(*_a, **_kw):
        raise RuntimeError("kaputt")
    monkeypatch.setattr(jm, "_evaluate_fit_inner", _boom)
    assert jm.evaluate_fit(_DB, _server(), _client(brand="vanilla")).level == jm.LEVEL_OK


def test_shared_required_mods_wirft_bleibt_folgenlos(_pack, monkeypatch):
    """Die Mod-Ernte darf den Wechsel nie aufhalten, auch nicht mit Exception."""
    def _boom(_server_obj):
        raise RuntimeError("Jar kaputt")
    monkeypatch.setattr(jm, "shared_required_mods", _boom)
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"),
                          _client(brand="neoforge", mods={"sodium"}))
    assert fit.level == jm.LEVEL_OK


def test_shared_required_mods_faengt_defekte_quelle_selbst_ab(monkeypatch):
    from app.services import modpack_router_service

    def _boom(_s):
        raise OSError("mods-Ordner weg")
    monkeypatch.setattr(modpack_router_service, "server_mod_ids_cached", _boom)
    assert jm.shared_required_mods(_server()) == frozenset()


# --------------------------------------------------------------------------- #
# FAIL-OPEN: Mod-Regel schweigt bei duenner Datenlage
# --------------------------------------------------------------------------- #
def test_ziel_ohne_replay_meldet_nichts(_pack, _mods):
    """Leere Pflichtmenge (kein Replay aufgenommen) -> die Mod-Regel schweigt."""
    _mods(installed={"create", "jei", "botania", "mekanism"}, pack=set())
    assert jm.shared_required_mods(_server()) == frozenset()
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"),
                          _client(brand="neoforge", mods={"sodium"}))
    assert fit.level == jm.LEVEL_OK


def test_ziel_ohne_mods_ordner_meldet_nichts(_pack, _mods):
    _mods(installed=set(), pack={"create", "jei", "botania"})
    assert jm.shared_required_mods(_server()) == frozenset()
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"),
                          _client(brand="neoforge", mods={"sodium"}))
    assert fit.level == jm.LEVEL_OK


def test_weniger_als_drei_fehlende_mods_sind_rauschen(_pack, _mods):
    _mods(installed={"create", "jei", "botania"}, pack={"create", "jei", "botania"})
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"),
                          _client(brand="neoforge", mods={"botania"}))
    assert fit.level == jm.LEVEL_OK      # nur create + jei fehlen -> 2 < Schwelle


def test_client_ohne_mod_liste_loest_mod_regel_nicht_aus(_pack, _mods):
    """Bukkit liefert nie eine Mod-Liste - dann darf die Mod-Regel nicht greifen."""
    _mods(installed={"create", "jei", "botania"}, pack={"create", "jei", "botania"})
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"),
                          _client(brand="neoforge", mods=()))
    assert fit.level == jm.LEVEL_OK


# --------------------------------------------------------------------------- #
# Positiv: Loader-Familie passt nicht -> Rueckfrage
# --------------------------------------------------------------------------- #
@pytest.mark.parametrize("target,brand", [
    ("neoforge", "vanilla"),
    ("fabric", "vanilla"),
    ("neoforge", "fabric"),
    ("forge", "quilt"),
    ("fabric", "neoforge"),
    ("quilt", "forge"),
    ("forge", "vanilla"),
])
def test_fremde_loader_familie_fragt_nach(_pack, target, brand):
    fit = jm.evaluate_fit(_DB, _server(server_type=target), _client(brand=brand))
    assert fit.level == jm.LEVEL_CONFIRM
    assert fit.level != "block"
    assert fit.code == "loader"


def test_rueckfrage_text_nennt_server_und_beide_loader(_pack):
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge", name="Davids Welt",
                                       mc_version="1.21.1"),
                          _client(brand="vanilla"))
    assert fit.level == jm.LEVEL_CONFIRM
    assert "Davids Welt" in fit.text
    assert "NeoForge" in fit.text        # Ziel-Loader
    assert "Vanilla" in fit.text         # Client-Loader
    assert "1.21.1" in fit.text
    assert fit.short.strip() != ""
    assert fit.short == "Braucht NeoForge 1.21.1"


def test_short_bleibt_ohne_version_brauchbar(_pack):
    fit = jm.evaluate_fit(_DB, _server(server_type="fabric", mc_version=""),
                          _client(brand="vanilla"))
    assert fit.level == jm.LEVEL_CONFIRM
    assert fit.short == "Braucht Fabric"
    assert fit.short.strip() != ""


def test_namenloser_server_bekommt_platzhalter(_pack):
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge", name=""),
                          _client(brand="vanilla"))
    assert fit.level == jm.LEVEL_CONFIRM
    assert "Der Server" in fit.text


def test_pack_hinweis_taucht_im_text_auf(_pack):
    _pack.setattr(modpack_service, "client_pack_hint",
                  lambda db, sid: ("All the Mods 10 (2.0.1)", "https://example.invalid/atm10"))
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"), _client(brand="vanilla"))
    assert fit.level == jm.LEVEL_CONFIRM
    assert "All the Mods 10 (2.0.1)" in fit.text        # Bezeichnung
    assert "https://example.invalid/atm10" in fit.text  # Bezugsweg


def test_text_traegt_keine_farbcodes(_pack):
    """Jede Lobby faerbt selbst - der Text muss roh bleiben."""
    _pack.setattr(modpack_service, "client_pack_hint",
                  lambda db, sid: ("ATM10", "https://example.invalid/atm10"))
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"), _client(brand="vanilla"))
    assert "\u00a7" not in fit.text and "\u00a7" not in fit.short
    assert "&c" not in fit.text


# --------------------------------------------------------------------------- #
# Niemals Rueckfrage: Forge und NeoForge sind EINE Familie
# --------------------------------------------------------------------------- #
@pytest.mark.parametrize("target,brand", [
    ("neoforge", "forge"),
    ("forge", "neoforge"),
    ("neoforge", "neoforge"),
    ("forge", "forge"),
    ("fabric", "quilt"),
    ("quilt", "fabric"),
])
def test_gleiche_familie_fragt_nie_nach(_pack, target, brand):
    """Die Brand-Lage zwischen Forge und NeoForge ist versionsabhaengig uneindeutig -
    ein Fehlalarm kostet hier mehr als ein verpasster Treffer."""
    fit = jm.evaluate_fit(_DB, _server(server_type=target), _client(brand=brand))
    assert fit.level == jm.LEVEL_OK


def test_forge_brand_mit_fml_prefix_fragt_nicht_nach(_pack):
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"), _client(brand="fml,forge"))
    assert fit.level == jm.LEVEL_OK


# --------------------------------------------------------------------------- #
# Normalisierung
# --------------------------------------------------------------------------- #
@pytest.mark.parametrize("raw,erwartet", [
    ("fml,forge", "forge"),
    ("vanilla,fabric", "fabric"),
    ("vanilla (Velocity)", "vanilla"),
    ("Vanilla", "vanilla"),
    ("quilt", "quilt"),
    ("neoforge", "neoforge"),
    ("NeoForge", "neoforge"),
    ("  fabric  ", "fabric"),
    ("vanilla (BungeeCord)", "vanilla"),
    ("vanilla (Waterfall)", "vanilla"),
    ("vanilla (Proxy)", "vanilla"),
])
def test_normalize_brand(raw, erwartet):
    assert jm.normalize_brand(raw) == erwartet


def test_quilt_gehoert_zur_fabric_familie():
    assert jm.normalize_brand("quilt") == "quilt"
    assert jm.brand_family(jm.normalize_brand("quilt")) == "fabric"


def test_neoforge_gewinnt_vor_forge():
    """Reihenfolge in _KNOWN_BRANDS: sonst schluckt der Teilstring forge jeden
    NeoForge-Brand."""
    assert jm.normalize_brand("neoforge") == "neoforge"
    assert jm.normalize_brand("fml,neoforge") == "neoforge"


@pytest.mark.parametrize("loader,familie", [
    ("forge", "forge"),
    ("neoforge", "forge"),
    ("fabric", "fabric"),
    ("quilt", "fabric"),
    ("vanilla", "vanilla"),
    ("", ""),
])
def test_brand_family(loader, familie):
    assert jm.brand_family(loader) == familie


@pytest.mark.parametrize("server_type,erwartet", [
    ("paper", "plugin"), ("spigot", "plugin"), ("purpur", "plugin"),
    ("folia", "plugin"), ("bukkit", "plugin"), ("craftbukkit", "plugin"),
    ("vanilla", "plugin"), ("  Paper  ", "plugin"),
    ("forge", "forge"), ("neoforge", "neoforge"),
    ("fabric", "fabric"), ("quilt", "quilt"),
    ("irgendwas", None), ("", None), (None, None),
])
def test_server_loader(server_type, erwartet):
    assert jm.server_loader(_server(server_type=server_type)) == erwartet


# --------------------------------------------------------------------------- #
# Mod-Regel: Mengen-Asymmetrie (Anti-Regression)
# --------------------------------------------------------------------------- #
def test_shared_required_mods_kuerzt_beide_seiten_weg(_mods):
    """Server-only-Mods (nur im mods-Ordner) UND Client-only-Mods (nur im Replay)
    muessen aus der Pflichtmenge fallen - keine Menge ist Teilmenge der anderen."""
    _mods(
        installed={"create", "jei", "ftbbackups2", "spark", "servercore"},
        pack={"create", "jei", "sodium", "xaerominimap", "iris"},
    )
    required = jm.shared_required_mods(_server())

    assert required == frozenset({"create", "jei"})
    for server_only in ("ftbbackups2", "spark", "servercore"):
        assert server_only not in required
    for client_only in ("sodium", "xaerominimap", "iris"):
        assert client_only not in required


def test_shared_required_mods_ist_case_unabhaengig(_mods):
    _mods(installed={"Create", "JEI"}, pack={"create", "jei"})
    assert jm.shared_required_mods(_server()) == frozenset({"create", "jei"})


def test_builtin_namespaces_bleiben_aus_der_pflichtmenge(_mods):
    """minecraft/neoforge sind keine Mods - landen sie in der Pflichtmenge, zaehlt
    der Hinweis Phantom-Mods auf. Der Filter sitzt in der Namespace-Ernte."""
    from app.services import mc_dispatch as mcd

    payload = (b"\x00\x0fminecraft:stone\x00\x12neoforge:attachment"
               b"\x00\x0ecreate:cogwheel\x00\x08jei:info\x00\x0csodium:opts")
    geerntet = mcd.extract_mod_namespaces(payload)
    assert "minecraft" not in geerntet and "neoforge" not in geerntet
    assert "forge" not in geerntet and "fml" not in geerntet

    _mods(installed={"create", "jei", "minecraft", "neoforge", "forge"}, pack=geerntet)
    required = jm.shared_required_mods(_server())
    assert "minecraft" not in required and "neoforge" not in required
    assert required == frozenset({"create", "jei"})


def test_kein_builtin_namespace_im_hinweistext(_pack, _mods):
    from app.services import mc_dispatch as mcd

    payload = (b"\x00\x0fminecraft:stone\x00\x12neoforge:attachment"
               b"\x00\x0ecreate:cogwheel\x00\x08jei:info\x00\x0csodium:opts"
               b"\x00\x0bbotania:rod\x00\x0emekanism:cable")
    _mods(installed={"create", "jei", "sodium", "botania", "mekanism", "minecraft",
                     "neoforge", "ftbbackups2"},
          pack=mcd.extract_mod_namespaces(payload))
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"),
                          _client(brand="neoforge", mods={"xaerominimap"}))
    assert fit.level == jm.LEVEL_CONFIRM
    assert "minecraft" not in fit.text
    assert "neoforge" not in fit.text.lower()
    assert "ftbbackups2" not in fit.text          # Server-only, nie verlangt


# --------------------------------------------------------------------------- #
# Mod-Regel: Positivfall
# --------------------------------------------------------------------------- #
def test_viele_fehlende_mods_fragen_nach(_pack, _mods):
    pflicht = {"create", "jei", "botania", "mekanism", "thermal"}
    _mods(installed=pflicht | {"ftbbackups2"}, pack=pflicht | {"sodium"})
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge", name="Davids Welt"),
                          _client(brand="neoforge", mods={"sodium"}))
    assert fit.level == jm.LEVEL_CONFIRM
    assert fit.code == "mods"
    assert "5" in fit.text and "Davids Welt" in fit.text
    assert "(+2 weitere)" in fit.text             # 5 fehlen, 3 werden genannt
    assert fit.short == "5 Mods fehlen vermutlich"
    assert "Verdacht" in fit.text                 # nie als Tatsache formulieren


def test_mod_hinweis_nennt_das_pack(_pack, _mods):
    _pack.setattr(modpack_service, "client_pack_hint", lambda db, sid: ("ATM10 2.0.1", ""))
    pflicht = {"create", "jei", "botania", "mekanism"}
    _mods(installed=pflicht, pack=pflicht)
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"),
                          _client(brand="neoforge", mods={"sodium"}))
    assert fit.level == jm.LEVEL_CONFIRM
    assert "ATM10 2.0.1" in fit.text


def test_genau_drei_fehlende_mods_erreichen_die_schwelle(_pack, _mods):
    pflicht = {"create", "jei", "botania"}
    _mods(installed=pflicht, pack=pflicht)
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"),
                          _client(brand="neoforge", mods={"sodium"}))
    assert fit.level == jm.LEVEL_CONFIRM
    assert "weitere" not in fit.text              # genau 3 -> kein Rest-Zusatz
    assert fit.short == "3 Mods fehlen vermutlich"


def test_mod_vergleich_ignoriert_gross_klein(_pack, _mods):
    pflicht = {"create", "jei", "botania"}
    _mods(installed=pflicht, pack=pflicht)
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"),
                          _client(brand="neoforge", mods={"Create", "JEI", "Botania"}))
    assert fit.level == jm.LEVEL_OK


# --------------------------------------------------------------------------- #
# Gesamtmatrix: nie etwas anderes als ok/confirm
# --------------------------------------------------------------------------- #
def test_niemals_ein_anderes_level_als_ok_oder_confirm(_pack, _mods):
    _mods(installed={"create", "jei", "botania", "mekanism"},
          pack={"create", "jei", "botania", "sodium"})
    ziele = ["paper", "spigot", "purpur", "folia", "bukkit", "vanilla",
             "forge", "neoforge", "fabric", "quilt", "irgendwas", ""]
    brands = ["", None, "vanilla", "forge", "neoforge", "fabric", "quilt",
              "fml,forge", "vanilla (Velocity)", "lunarclient", _JUNK_BRAND, 42]
    gesehen = set()
    for ziel in ziele:
        for brand in brands:
            for mods in ((), ("sodium",)):
                fit = jm.evaluate_fit(_DB, _server(server_type=ziel),
                                      _client(brand=brand, mods=mods))
                gesehen.add(fit.level)
    assert gesehen <= {jm.LEVEL_OK, jm.LEVEL_CONFIRM}
    assert "block" not in gesehen


# --------------------------------------------------------------------------- #
# profile_from_payload: alles Unerwartete schaltet den Abgleich ab
# --------------------------------------------------------------------------- #
@pytest.mark.parametrize("raw", [None, "vanilla", 5, 3.5, True, [], ["vanilla"],
                                 (), b"{}", {}, {"brand": ""}, {"mods": []},
                                 {"brand": "   ", "mods": []},
                                 {"brand": 42, "mods": "create"},
                                 {"brand": None, "mods": None},
                                 {"quatsch": 1}])
def test_profile_from_payload_liefert_none(raw):
    assert jm.profile_from_payload(raw) is None


def test_profile_from_payload_liest_brand():
    info = jm.profile_from_payload({"brand": "  NeoForge  ", "source": "hub"})
    assert isinstance(info, jm.ClientInfo)
    assert info.brand == "NeoForge"        # roh, nur getrimmt
    assert jm.normalize_brand(info.brand) == "neoforge"
    assert info.mods == frozenset()
    assert info.source == "hub"


def test_profile_from_payload_saeubert_mod_liste():
    info = jm.profile_from_payload({"brand": "neoforge",
                                    "mods": ["Create", "  JEI  ", "", "   ",
                                             None, 5, {"id": "x"}, "create"]})
    assert info.mods == frozenset({"create", "jei"})
    assert isinstance(info.mods, frozenset)


def test_profile_from_payload_ohne_brand_aber_mit_mods():
    info = jm.profile_from_payload({"mods": ["create"]})
    assert info is not None
    assert info.brand == "" and info.mods == frozenset({"create"})


def test_profile_from_payload_kuerzt_ueberlange_felder():
    info = jm.profile_from_payload({"brand": "n" * 200, "source": "s" * 200})
    assert len(info.brand) == 64
    assert len(info.source) == 16


def test_profile_from_payload_akzeptiert_tupel_als_mod_liste():
    info = jm.profile_from_payload({"mods": ("create", "jei")})
    assert info.mods == frozenset({"create", "jei"})


def test_profile_aus_payload_fuehrt_zum_selben_urteil(_pack):
    """Der Weg Endpoint-Payload -> ClientInfo -> Fit muss durchgaengig sein."""
    info = jm.profile_from_payload({"brand": "vanilla", "source": "bukkit"})
    fit = jm.evaluate_fit(_DB, _server(server_type="neoforge"), info)
    assert fit.level == jm.LEVEL_CONFIRM


def test_client_info_ist_unveraenderlich():
    info = jm.profile_from_payload({"brand": "vanilla"})
    with pytest.raises(Exception):
        info.brand = "neoforge"


def test_fit_ist_unveraenderlich():
    fit = jm.Fit(level=jm.LEVEL_CONFIRM, text="x")
    with pytest.raises(Exception):
        fit.level = jm.LEVEL_OK
