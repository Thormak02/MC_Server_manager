"""Tests zu doppelten VanillaTweaks-Datapacks.

Gemeldet hatte es der KI-Assistent: sieben Datapacks lagen zweimal im Ordner
``world/datapacks``, einmal als 26.2- und einmal als 26.3-Fassung. Die Ursache stand im
Code als ein stilles ``except ValueError: pass``:

Ein LAUFENDER Server haelt seine Datapack-ZIPs offen. Das Loeschen der alten Datei
scheitert unter Windows deshalb mit einem Fehler - und weil der verschwiegen wurde, blieb
die Datei liegen. Der Eintrag zeigte danach auf die neue Datei, die alte war fuer den
Manager unsichtbar, wurde von Minecraft aber ZUSAETZLICH geladen. Beide Packs liefen
gleichzeitig, mit doppelten Funktionen und Rezepten.

Der zweite Fehler kam aus demselben Bereich: ``declared_mc_version`` stand auf einem
26.2-Server auf "1.21.2", obwohl die Dateien korrekt "(MC 26.2)" hiessen (nachgesehen im
DB-Schnappschuss auf dem NAS). Der Vergleich "schon aktuell" griff damit nie, und jedes
Update erzeugte alle Packs neu - was die Gelegenheit fuer den ersten Fehler bei jedem
Lauf neu herstellte.
"""
import pytest


@pytest.mark.parametrize("dateiname,gruppe", [
    ("armor statues v2.8.23 (MC 26.2).zip", "26.2"),
    ("armor statues v2.8.20 (MC 1.21-1.21.10).zip", "1.21"),
    ("elevators v1.0.18 (MC 26.3).zip", "26.3"),
    ("x v1.0 (MC 1.20-1.20.4).zip", "1.20"),
    ("VanillaTweaks Crafting Tweaks.zip", ""),
    ("", ""),
])
def test_gruppe_kommt_aus_dem_dateinamen(dateiname, gruppe):
    """Den Dateinamen schreibt VanillaTweaks selbst - er ist verlaesslicher als das Feld
    declared_mc_version, das beim Duplizieren und beim Transfer mitkopiert wird und dann
    nicht mehr zum Server passt."""
    from app.services.vanillatweaks_service import _pack_gruppe_from_file

    assert _pack_gruppe_from_file(dateiname) == gruppe


def _server(db, tmp_path, *, slug="vt-srv", port=25781, mc_version="26.2"):
    from app.models.server import Server

    basis = tmp_path / slug
    (basis / "world" / "datapacks").mkdir(parents=True, exist_ok=True)
    (basis / "server.properties").write_text(
        f"level-name=world\nserver-port={port}\n", encoding="utf-8")
    server = Server(name=slug, slug=slug, server_type="paper", mc_version=mc_version,
                    base_path=str(basis), port=port, status="running",
                    start_mode="command", start_command="java -jar server.jar")
    db.add(server)
    db.commit()
    db.refresh(server)
    return server


def _eintrag(db, server, *, datei, version="2.8.23", erklaert="26.2"):
    from app.models.installed_content import InstalledContent

    zeile = InstalledContent(
        server_id=server.id, provider_name="vanillatweaks", content_type="datapack",
        external_project_id="vt:datapacks", external_version_id=version,
        name="Armor Statues", version_label=version, file_name=datei,
        declared_mc_version=erklaert)
    db.add(zeile)
    db.commit()
    db.refresh(zeile)
    return zeile


def _katalog(monkeypatch, *, version="26.2", pack_version="2.8.30"):
    """find_pack/resolve_vt_version/generate_zip ersetzen - kein Netzzugriff."""
    import io
    import zipfile

    from app.services import vanillatweaks_service as vt

    monkeypatch.setattr(vt, "resolve_vt_version", lambda _mc: version)
    monkeypatch.setattr(vt, "find_pack", lambda *_a, **_k: {
        "pack_type": "datapacks", "category": "survival", "name": "armor statues",
        "display": "Armor Statues", "version": pack_version})

    puffer = io.BytesIO()
    with zipfile.ZipFile(puffer, "w") as z:
        z.writestr(f"armor statues v{pack_version} (MC {version}).zip", b"inhalt")
    monkeypatch.setattr(vt, "generate_zip", lambda *_a, **_k: puffer.getvalue())


def test_nicht_loeschbare_alte_datei_wird_gemeldet(client, monkeypatch, tmp_path):
    """Der eigentliche Fund: der Fehlschlag darf nicht verschwiegen werden.

    Vorher stand hier ``pass``. Die alte Datei blieb liegen, der Nutzer erfuhr nichts -
    und Minecraft lud beide Packs.
    """
    from app.db.session import SessionLocal
    from app.services import content_service, vanillatweaks_service as vt

    _katalog(monkeypatch, version="26.2", pack_version="2.8.30")

    def _loeschen_scheitert(server, content_type, file_name):
        raise ValueError(f"Datei konnte nicht geloescht werden: {file_name} "
                         f"(gesperrt). Bitte Server stoppen.")

    monkeypatch.setattr(content_service, "_delete_content_file", _loeschen_scheitert)

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-gesperrt", port=25781)
        eintrag = _eintrag(db, server, datei="armor statues v2.8.23 (MC 26.2).zip")

        aktualisiert, meldung = vt.update_installed_vt(db, server, eintrag, None)

    assert aktualisiert is True, "Die neue Datei ist da, das Update gilt als erfolgt"
    assert "2.8.30" in meldung
    # Und der Nutzer erfaehrt vom Rest - mit Dateiname und was zu tun ist.
    assert "armor statues v2.8.23 (MC 26.2).zip" in meldung
    assert "ZUSAETZLICH geladen" in meldung
    assert "stoppen" in meldung


def test_erfolgreiches_loeschen_meldet_keinen_rest(client, monkeypatch, tmp_path):
    from app.db.session import SessionLocal
    from app.services import vanillatweaks_service as vt

    _katalog(monkeypatch, version="26.2", pack_version="2.8.30")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-ok", port=25782)
        alt = server.base_path + "/world/datapacks/armor statues v2.8.23 (MC 26.2).zip"
        from pathlib import Path

        Path(alt).write_bytes(b"alt")
        eintrag = _eintrag(db, server, datei="armor statues v2.8.23 (MC 26.2).zip")

        aktualisiert, meldung = vt.update_installed_vt(db, server, eintrag, None)
        neuer_name = eintrag.file_name

    assert aktualisiert is True
    assert "ZUSAETZLICH" not in meldung
    assert not Path(alt).exists(), "Die alte Datei haette weg sein muessen"
    assert "2.8.30" in neuer_name


def test_falsche_versionsangabe_verhindert_das_erkennen_nicht(client, monkeypatch,
                                                              tmp_path):
    """Mit declared_mc_version='1.21.2' auf einem 26.2-Server galt ein aktuelles Pack als
    veraltet und wurde bei JEDEM Update neu erzeugt. Jetzt zaehlt der Dateiname."""
    from app.db.session import SessionLocal
    from app.services import vanillatweaks_service as vt

    erzeugt: list = []
    _katalog(monkeypatch, version="26.2", pack_version="2.8.23")
    echtes_generate = vt.generate_zip
    monkeypatch.setattr(vt, "generate_zip",
                        lambda *a, **k: (erzeugt.append(1), echtes_generate(*a, **k))[1])

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-falschangabe", port=25783)
        eintrag = _eintrag(db, server, datei="armor statues v2.8.23 (MC 26.2).zip",
                           version="2.8.23", erklaert="1.21.2")

        aktualisiert, meldung = vt.update_installed_vt(db, server, eintrag, None)
        nachher = eintrag.declared_mc_version

    assert aktualisiert is False and meldung == "", "Das Pack ist aktuell"
    assert erzeugt == [], "Es haette nichts neu erzeugt werden duerfen"
    # Und die falsche Angabe wird dabei nachgezogen, sonst bleibt sie fuer immer falsch.
    assert nachher == "26.2"


def test_pack_aus_der_falschen_gruppe_wird_ersetzt(client, monkeypatch, tmp_path):
    """Ein fuer 1.21 erzeugtes Pack laedt auf einem 26.x-Server nicht (andere
    Paketformate) - das MUSS erneuert werden, auch wenn die Versionsnummer gleich ist."""
    from app.db.session import SessionLocal
    from app.services import vanillatweaks_service as vt

    _katalog(monkeypatch, version="26.2", pack_version="2.8.23")

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-gruppe", port=25784)
        eintrag = _eintrag(db, server,
                           datei="armor statues v2.8.23 (MC 1.21-1.21.11).zip",
                           version="2.8.23", erklaert="1.21")

        aktualisiert, _meldung = vt.update_installed_vt(db, server, eintrag, None)
        neuer_name = eintrag.file_name

    assert aktualisiert is True
    assert "26.2" in neuer_name, f"Es wurde nicht neu erzeugt: {neuer_name}"


def test_verwaiste_datei_wird_als_eintrag_sichtbar(client, tmp_path):
    """Bleibt eine alte Datei liegen, soll der Manager sie wenigstens SEHEN - sonst kann
    man sie auch nicht gezielt entfernen. Das erledigt die Uebernahme nicht verwalteter
    Dateien."""
    from pathlib import Path

    from app.db.session import SessionLocal
    from app.services.content_service import _sync_local_content_entries, \
        list_installed_content

    with SessionLocal() as db:
        server = _server(db, tmp_path, slug="vt-verwaist", port=25785)
        ordner = Path(server.base_path) / "world" / "datapacks"
        ordner.mkdir(parents=True, exist_ok=True)
        # Beide Dateien anlegen: die verwaiste UND die, zu der es einen Eintrag gibt.
        # Ein Eintrag ohne Datei wird von list_installed_content ausgesiebt.
        (ordner / "armor statues v2.8.23 (MC 26.2).zip").write_bytes(b"alt")
        (ordner / "armor statues v2.8.30 (MC 26.3).zip").write_bytes(b"neu")
        _eintrag(db, server, datei="armor statues v2.8.30 (MC 26.3).zip")

        _sync_local_content_entries(db, server)
        eintraege = list_installed_content(db, server)

    dateien = {e.file_name for e in eintraege}
    assert "armor statues v2.8.30 (MC 26.3).zip" in dateien
    assert "armor statues v2.8.23 (MC 26.2).zip" in dateien, \
        "Die liegengebliebene Datei ist fuer den Manager unsichtbar"
