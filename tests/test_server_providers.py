

# --------------------------------------------------------------------------- #
# NeoForge: Loader-Nummer -> Minecraft-Version (Schemawechsel 1.x -> 26.x)
# --------------------------------------------------------------------------- #

def test_neoforge_year_scheme_does_not_get_a_1_prefix():
    """Der gemeldete Fehler: aus Loader 26.3.0.99 wurde die Phantomversion "1.26.3".

    Die sah plausibel aus, stand im Dropdown GANZ OBEN und liess sich anlegen - und
    danach waehlte der Manager Java 21 statt 25 (der Zweig `first != 1` in
    required_java_major_for_mc war unerreichbar), lehnte jede Mod als "Inkompatibel
    fuer Minecraft 1.26.3" ab und fand keine VanillaTweaks-Gruppe. Eine echte 26.x
    liess sich ueber die Oberflaeche gar nicht waehlen.
    """
    from app.providers.server.neoforge_provider import NeoForgeProvider as P

    assert P._mc_version_from_loader("26.3.0.99") == "26.3"
    assert P._mc_version_from_loader("26.1.2.124") == "26.1"
    assert P._mc_version_from_loader("26.2.5") == "26.2"


def test_neoforge_old_scheme_keeps_the_1_prefix():
    """Gegenprobe - das alte Schema darf sich nicht aendern."""
    from app.providers.server.neoforge_provider import NeoForgeProvider as P

    assert P._mc_version_from_loader("21.1.265") == "1.21.1"
    assert P._mc_version_from_loader("20.4.0") == "1.20.4"
    assert P._mc_version_from_loader("21.0.0") == "1.21"      # ".0" faellt weg


def test_neoforge_derived_version_feeds_java_and_compatibility_correctly():
    """Die Ableitung ist nur der Anfang der Kette - hier bricht sie oder haelt."""
    from app.providers.server.neoforge_provider import NeoForgeProvider as P
    from app.services.content_service import _is_mc_version_compatible
    from app.services.java_runtime_service import required_java_major_for_mc

    mc = P._mc_version_from_loader("26.3.0.99")
    assert required_java_major_for_mc(mc) == 25, "26.x braucht Java 25, nicht 21"
    assert _is_mc_version_compatible(mc, {"26.3"}) is True, "Mods duerfen nicht abgelehnt werden"


def test_neoforge_rejects_unparsable_loader_versions():
    from app.providers.server.neoforge_provider import NeoForgeProvider as P

    assert P._mc_version_from_loader("") is None
    assert P._mc_version_from_loader("beta") is None
    assert P._mc_version_from_loader("26") is None            # zu wenige Teile
