import io
import zipfile


def test_map_vt_version():
    from app.services import vanillatweaks_service as vt

    assert vt.map_vt_version("1.21.11") == "1.21"
    assert vt.map_vt_version("1.20.1") == "1.20"
    assert vt.map_vt_version("1.21") == "1.21"
    assert vt.map_vt_version("garbage") == "1.21"


def test_list_categories_parses(monkeypatch):
    from app.services import content_service, vanillatweaks_service as vt

    monkeypatch.setattr(
        content_service,
        "_request_json",
        lambda url, headers=None: {
            "versionName": "1.21",
            "categories": [
                {
                    "category": "Deco",
                    "packs": [{"name": "armor statues", "display": "Armor Statues"}],
                }
            ],
        },
    )
    cats = vt.list_categories("datapacks", "1.21")
    assert cats and cats[0]["category"] == "Deco"
    assert cats[0]["packs"][0]["name"] == "armor statues"


def _make_container() -> bytes:
    inner = io.BytesIO()
    with zipfile.ZipFile(inner, "w") as z:
        z.writestr("data/foo/x.json", "{}")
        z.writestr("pack.mcmeta", "{}")
    container = io.BytesIO()
    with zipfile.ZipFile(container, "w") as z:
        z.writestr("armor statues.zip", inner.getvalue())
    return container.getvalue()


def test_install_datapacks_places_files(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import content_service, vanillatweaks_service as vt

    monkeypatch.setattr(
        vt, "generate_zip", lambda pack_type, version, selection: _make_container()
    )

    base = tmp_path / "vtsrv"
    base.mkdir()
    (base / "server.properties").write_text("level-name=world\n", encoding="utf-8")

    with SessionLocal() as db:
        srv = Server(
            name="vt-server",
            slug="vt-server",
            server_type="paper",
            mc_version="1.21.1",
            base_path=str(base),
        )
        db.add(srv)
        db.commit()

        notes, warnings = vt.install_datapacks(
            db, srv, "datapacks", {"Deco": ["armor statues"]}, None
        )
        assert len(notes) == 1
        assert not warnings

        # Inneres Datapack-.zip liegt im Welt-Datapacks-Ordner.
        placed = list((base / "world" / "datapacks").glob("*.zip"))
        assert len(placed) == 1

        # Als installiertes Datapack gefuehrt (provider vanillatweaks).
        installed = content_service.list_installed_content(db, srv)
        vt_entries = [
            i
            for i in installed
            if i.content_type == "datapack" and i.provider_name == "vanillatweaks"
        ]
        assert len(vt_entries) == 1


def test_vt_resourcepack_hosts_and_sets_properties(client, monkeypatch, tmp_path):
    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import vanillatweaks_service as vt

    settings = get_settings()
    monkeypatch.setattr(settings, "public_base_url", "http://thormakmc.ddns.net:8000")
    monkeypatch.setattr(settings, "data_dir", tmp_path)
    monkeypatch.setattr(
        vt, "generate_zip", lambda pack_type, version, selection: b"PACKDATA"
    )

    base = tmp_path / "srv"
    base.mkdir()
    (base / "server.properties").write_text("level-name=world\n", encoding="utf-8")

    with SessionLocal() as db:
        srv = Server(
            name="vt-rp",
            slug="vt-rp",
            server_type="paper",
            mc_version="1.21.1",
            base_path=str(base),
        )
        db.add(srv)
        db.commit()

        entry = vt.install_resourcepack(db, srv, {"Aesthetic": ["x"]}, None)
        assert entry.content_type == "resourcepack"

        hosted = list((tmp_path / "resourcepacks").glob("vt_*.zip"))
        assert len(hosted) == 1

        props = (base / "server.properties").read_text(encoding="utf-8")
        assert (
            "resource-pack=http://thormakmc.ddns.net:8000/resourcepacks/vt_" in props
        )
        assert "resource-pack-sha1=" in props


def test_vt_resourcepack_requires_public_url(client, monkeypatch, tmp_path):
    import pytest

    from app.core.config import get_settings
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services import vanillatweaks_service as vt

    settings = get_settings()
    monkeypatch.setattr(settings, "public_base_url", None)
    monkeypatch.setattr(vt, "generate_zip", lambda *a, **k: b"X")

    base = tmp_path / "srv2"
    base.mkdir()
    (base / "server.properties").write_text("level-name=world\n", encoding="utf-8")

    with SessionLocal() as db:
        srv = Server(
            name="vt-rp2",
            slug="vt-rp2",
            server_type="paper",
            mc_version="1.21.1",
            base_path=str(base),
        )
        db.add(srv)
        db.commit()
        with pytest.raises(ValueError):
            vt.install_resourcepack(db, srv, {"A": ["x"]}, None)


# --------------------------------------------------------------------------- #
# Versionsgruppe aufloesen - die Absicherung gegen kuenftige MC-Versionen
# --------------------------------------------------------------------------- #

def _vt_mit_angebot(monkeypatch, vorhanden):
    """VT tut so, als gaebe es genau diese Gruppen. Zaehlt die Abfragen mit."""
    from app.services import vanillatweaks_service as vt

    gefragt = []

    def fake_exists(version):
        gefragt.append(version)
        return version in vorhanden

    monkeypatch.setattr(vt, "version_exists", fake_exists)
    vt._VERSION_CACHE.clear()
    return vt, gefragt


def test_resolve_uses_the_servers_own_group_when_vt_has_it(monkeypatch):
    vt, gefragt = _vt_mit_angebot(monkeypatch, {"1.20", "1.21", "26.1", "26.2", "26.3"})
    assert vt.resolve_vt_version("26.3") == "26.3"
    assert gefragt == ["26.3"], "haette nicht weitersuchen duerfen"


def test_resolve_falls_back_to_the_next_lower_minor(monkeypatch):
    """MC 26.4 erscheint, VT hinkt noch hinterher -> 26.3 ist die richtige Grundlage."""
    vt, _ = _vt_mit_angebot(monkeypatch, {"1.21", "26.1", "26.2", "26.3"})
    assert vt.resolve_vt_version("26.4.1") == "26.3"


def test_resolve_never_crosses_the_major_version(monkeypatch):
    """DIE entscheidende Regel.

    Genau hier lag der gemeldete Fehler: der alte Rueckfall lieferte fuer einen
    26.x-Server ein 1.21-Pack. Das laedt dort nicht (1.21 deckt die Formate 48-94 ab,
    26.x verlangt 101+), wurde aber klaglos als installiert verbucht. Lieber laut
    scheitern als etwas Unbrauchbares ausliefern.
    """
    import pytest

    vt, _ = _vt_mit_angebot(monkeypatch, {"1.20", "1.21"})   # nur altes Schema vorhanden
    with pytest.raises(ValueError, match="VanillaTweaks bietet"):
        vt.resolve_vt_version("26.2")


def test_resolve_reports_what_it_tried(monkeypatch):
    import pytest

    vt, _ = _vt_mit_angebot(monkeypatch, set())
    with pytest.raises(ValueError) as fehler:
        vt.resolve_vt_version("26.2")
    text = str(fehler.value)
    assert "26.2" in text and "26.1" in text, "die geprueften Kandidaten gehoeren in die Meldung"
    assert "1.21" not in text, "ueber die Hauptversion hinaus darf gar nicht gesucht werden"


def test_resolve_is_cached(monkeypatch):
    """Jede Abfrage ist eine Runde uebers Netz - zweimal dasselbe reicht einmal."""
    vt, gefragt = _vt_mit_angebot(monkeypatch, {"26.2"})
    assert vt.resolve_vt_version("26.2.1") == "26.2"
    anzahl = len(gefragt)
    assert vt.resolve_vt_version("26.2.4") == "26.2"
    assert len(gefragt) == anzahl, "zweiter Aufruf haette aus dem Zwischenspeicher kommen muessen"


def test_resolve_handles_an_unparsable_version(monkeypatch):
    """Unlesbare Serverversion -> map_vt_version liefert 1.21, das wird normal geprueft."""
    vt, _ = _vt_mit_angebot(monkeypatch, {"1.21"})
    assert vt.resolve_vt_version("irgendwas") == "1.21"


def test_generate_zip_no_longer_falls_back_silently(monkeypatch):
    """Scheitert die Generierung, soll das sichtbar sein - nicht ein fremdes Pack liefern."""
    import pytest

    from app.services import vanillatweaks_service as vt

    versuche = []

    def fake_post(url, form):
        versuche.append(form.get("version"))
        return {"status": "error"}

    monkeypatch.setattr(vt, "_post_json", fake_post)
    with pytest.raises(ValueError):
        vt.generate_zip("datapacks", "26.2", {"Informative": ["durability ping"]})
    assert versuche == ["26.2"], f"es wurde heimlich eine andere Gruppe probiert: {versuche}"


# --------------------------------------------------------------------------- #
# Versionsvergleich: haelt er dem Schemawechsel 1.x -> 26.x stand?
# --------------------------------------------------------------------------- #

def test_year_scheme_sorts_above_the_old_scheme():
    """26.2 muss groesser sein als 1.21.11 - ein String-Vergleich laege hier falsch."""
    from app.providers.server.common import is_version_at_least as mindestens

    assert mindestens("26.2", "1.21.11") is True
    assert mindestens("1.21.11", "26.2") is False
    assert mindestens("26.10", "26.2") is True       # zweistellige Minor
    assert mindestens("26.2", "26.10") is False


def test_snapshots_are_not_silently_dropped():
    """Snapshot-Kennungen haben keine Zahlenpunkte und wurden zu (0,) - sie fielen
    damit IMMER durch die Mindestversion, auch bei ausdruecklich gewaehltem Kanal.
    Ueber ihre Aufnahme entscheidet jetzt allein der Kanal-Filter."""
    from app.providers.server.common import is_version_at_least as mindestens

    assert mindestens("26w14a", "1.7.10") is True
    assert mindestens("23w31a", "1.7.10") is True


def test_real_old_versions_are_still_filtered_out():
    """Die Mindestversion muss weiter greifen - sonst waere der Fix eine Regression."""
    from app.providers.server.common import is_version_at_least as mindestens

    assert mindestens("1.6.4", "1.7.10") is False
    assert mindestens("1.2.5", "1.7.10") is False
    assert mindestens("1.7.10", "1.7.10") is True
