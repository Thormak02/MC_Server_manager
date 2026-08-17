"""Neues Minecraft-Versionsschema ab 2026 (YY.D.H, z.B. 26.2) wird korrekt
behandelt: rein numerischer Vergleich + Java-21 fuer alle Nicht-1.x-Versionen."""


def test_new_year_based_version_scheme_is_handled():
    from app.providers.server.common import is_version_at_least
    from app.services.java_runtime_service import required_java_major_for_mc
    from app.services.vanillatweaks_service import map_vt_version

    # YY.D.H (year-based, 2026er) -> Java 25 (PaperMC-API java.minimum=25).
    assert required_java_major_for_mc("26.2") == 25
    assert required_java_major_for_mc("26.4.1") == 25
    # Alte 1.x-Regeln bleiben erhalten.
    assert required_java_major_for_mc("1.21.11") == 21
    assert required_java_major_for_mc("1.16.5") == 8

    # 2026er Versionen sind neuer als 1.21.x (26 > 1, numerischer Vergleich).
    assert is_version_at_least("26.2", "1.21.11") is True
    assert is_version_at_least("1.21.11", "26.2") is False
    assert is_version_at_least("26.3", "26.2") is True

    # Vanilla-Tweaks-Gruppe = "Jahr.Drop" (Hotfix wird abgeschnitten).
    assert map_vt_version("26.2.1") == "26.2"
    assert map_vt_version("1.21.11") == "1.21"
