"""Tests fuer das KI-Fundament: Schluessel-Ablage, Budget, Rate Limit, Themen-Sperre,
Saeuberung vor dem Versand.

Der Schwerpunkt liegt darauf, dass die Schranken greifen BEVOR ein Aufruf Geld kostet -
das ist der Grund, warum es sie gibt ("sonst frisst eine Crash-Analyse-Schleife still das
Guthaben"). Kein Test spricht mit der echten API; der Anbieter wird ersetzt.
"""
from datetime import datetime, timedelta, timezone

import pytest


# --------------------------------------------------------------- Schluessel-Ablage

def test_schluessel_wird_verschluesselt_abgelegt(client):
    """Der Klartext darf im abgelegten Wert nicht vorkommen - sonst waere es nur
    Verschleierung wie die Base64-Ablage, die wir ersetzen wollten."""
    from app.services import ai_secret_service as ass

    geheim = "sk-ant-api03-TESTSCHLUESSEL-abcdefghijklmnop"
    abgelegt = ass.verschluessele(geheim)

    assert geheim not in abgelegt
    assert abgelegt.startswith("dpapi:v1:")
    assert ass.entschluessele(abgelegt) == geheim
    assert ass.ist_verschluesselt(abgelegt) is True


def test_klartext_wird_nicht_durchgereicht(client):
    """Ein versehentlich im Klartext abgelegter Schluessel muss auffallen, nicht
    funktionieren."""
    from app.services import ai_secret_service as ass

    with pytest.raises(ass.GeheimnisFehler, match="nicht verschluesselt"):
        ass.entschluessele("sk-ant-im-klartext")


def test_leerer_wert_ist_kein_fehler(client):
    from app.services import ai_secret_service as ass

    assert ass.entschluessele("") == ""
    assert ass.entschluessele(None) == ""
    with pytest.raises(ass.GeheimnisFehler):
        ass.verschluessele("")


def test_maskierung_zeigt_nur_die_enden(client):
    from app.services import ai_secret_service as ass

    maske = ass.maskiere("sk-ant-api03-GEHEIMERTEIL-1234")
    assert maske.startswith("sk-ant-a")
    assert maske.endswith("1234")
    assert "GEHEIMERTEIL" not in maske
    assert ass.maskiere("") == ""


def test_einstellungen_geben_den_schluessel_nie_im_klartext(client):
    from app.db.session import SessionLocal
    from app.services import ai_settings_service as ais

    geheim = "sk-ant-api03-NIEMALSSICHTBAR-9876"
    with SessionLocal() as db:
        maske = ais.setze_schluessel(db, geheim)
        werte = ais.hole_einstellungen(db)

        assert werte["schluessel_hinterlegt"] is True
        assert werte["schluessel_verschluesselt"] is True
        assert geheim not in str(werte)
        assert werte["schluessel_maskiert"] == maske
        # Nur hole_schluessel entschluesselt.
        assert ais.hole_schluessel(db) == geheim

        ais.loesche_schluessel(db)
        assert ais.hole_einstellungen(db)["schluessel_hinterlegt"] is False


# ------------------------------------------------------------ Saeuberung vor dem Versand

def test_zeitstempel_bleiben_erhalten(client):
    """Ein grobes IPv6-Muster haelt "12:00:01" fuer eine Adresse und loescht damit die
    Uhrzeit aus jeder Logzeile - bei einer Crash-Analyse die halbe Information."""
    from app.services import ai_privacy_service as aps

    log = "[12:00:01] [Server thread/INFO]: Start\n[23:59:59] [Server thread/WARN]: Lag"
    gesaeubert, bericht = aps.saeubere(log)

    assert "[12:00:01]" in gesaeubert
    assert "[23:59:59]" in gesaeubert
    assert bericht["ips"] == 0


def test_versionsnummern_bleiben_erhalten(client):
    """"19.21.0.247" in einem Mod-Dateinamen IST eine gueltige IPv4 - wer sie entfernt,
    nimmt der Analyse die Antwort auf "welche Mod?"."""
    from app.services import ai_privacy_service as aps

    text = ("Mod geladen: jei-1.21.1-19.21.0.247\n"
            "Loader-Version 47.2.0.1\n"
            "Position (12.5, 64.0, -8.0)")
    gesaeubert, bericht = aps.saeubere(text)

    assert "jei-1.21.1-19.21.0.247" in gesaeubert
    assert "47.2.0.1" in gesaeubert
    assert "(12.5, 64.0, -8.0)" in gesaeubert
    assert bericht["ips"] == 0


@pytest.mark.parametrize("zeile, weg", [
    ("Thormak2002[/203.0.113.7:51234] logged in", "203.0.113.7"),
    ("Connection from 198.51.100.22", "198.51.100.22"),
    ("server-ip=192.168.1.50", "192.168.1.50"),
    ("Verbindung von 2001:db8::ff00:42:8329 angenommen", "2001:db8"),
])
def test_echte_adressen_werden_entfernt(client, zeile, weg):
    from app.services import ai_privacy_service as aps

    gesaeubert, bericht = aps.saeubere(zeile)
    assert weg not in gesaeubert
    assert bericht["ips"] >= 1


@pytest.mark.parametrize("zeile", [
    "rcon.password=supergeheim",
    "velocity.secret: abcdef123456",
    "api_key: sk-ant-api03-ECHT-xyz",
    "token: geheim123",
])
def test_geheimnisse_gehen_nie_hinaus(client, zeile):
    """Unabhaengig von allen Einstellungen: Zugangsdaten verlassen das Haus nicht."""
    from app.services import ai_privacy_service as aps

    gesaeubert, bericht = aps.saeubere(zeile)
    assert "<entfernt>" in gesaeubert
    assert bericht["geheimnisse"] >= 1
    # Der Schluesselname bleibt, damit die KI die Config noch versteht.
    assert zeile.split(":")[0].split("=")[0] in gesaeubert


def test_namen_bleiben_standardmaessig_stehen(client):
    """Entscheidung des Nutzers: Spielernamen im Klartext, weil sie zur Diagnose
    gehoeren."""
    from app.services import ai_privacy_service as aps

    log = "[12:00:06] [Server thread/INFO]: Thormak2002 joined the game"
    gesaeubert, _ = aps.saeubere(log)
    assert "Thormak2002" in gesaeubert


def test_pseudonymisierung_ist_stabil_und_umkehrbar(client):
    """Derselbe Name immer derselbe Platzhalter - sonst kann die KI nicht erkennen, dass
    alle Abstuerze denselben Spieler betreffen."""
    from app.services import ai_privacy_service as aps

    log = ("[12:00:06] [Server thread/INFO]: Thormak2002 joined the game\n"
           "[12:00:09] [Server thread/INFO]: <Thormak2002> hallo\n"
           "[12:01:02] [Server thread/INFO]: Sir_Fangar2004 joined the game")
    gesaeubert, bericht = aps.saeubere(log, namen_ersetzen=True)

    assert "Thormak2002" not in gesaeubert
    assert gesaeubert.count("Spieler1") == 2       # stabil: zweimal derselbe Platzhalter
    assert bericht["namen"] == {"Spieler1": "Thormak2002", "Spieler2": "Sir_Fangar2004"}

    zurueck = aps.uebersetze_zurueck("Spieler1 verursacht den Absturz.", bericht["namen"])
    assert zurueck == "Thormak2002 verursacht den Absturz."


def test_kuerzen_behaelt_anfang_und_ende(client):
    """Der Anfang hat die geladenen Mods, das Ende den Absturz - die Mitte ist entbehrlich
    und kostet Token."""
    from app.services import ai_privacy_service as aps

    text = "START" + ("x" * 5000) + "ENDE"
    gekuerzt, wurde_gekuerzt = aps.kuerze_fuer_anfrage(text, max_zeichen=1000)

    assert wurde_gekuerzt is True
    assert gekuerzt.startswith("START")
    assert gekuerzt.endswith("ENDE")
    assert len(gekuerzt) < len(text)
    # Kurze Texte bleiben unberuehrt.
    assert aps.kuerze_fuer_anfrage("kurz", max_zeichen=1000) == ("kurz", False)


def test_vorschau_zeigt_was_hinausgeht(client):
    from app.services import ai_privacy_service as aps

    log = "Connection from 203.0.113.7\nrcon.password=geheim\nThormak2002 joined the game"
    v = aps.vorschau(log)

    assert "203.0.113.7" not in v["text"]
    assert v["entfernte_ips"] >= 1
    assert v["entfernte_geheimnisse"] >= 1
    assert v["zeilen_gesamt"] == 3
    assert v["namen_ersetzt"] is False


# ------------------------------------------------------------------- Budget und Limits

def _nutzer(db, rolle="admin", name="ki-tester"):
    from app.core.security import hash_password
    from app.models.user import User

    person = User(username=name, password_hash=hash_password("Egal12345!"),
                  role=rolle, is_active=True)
    db.add(person)
    db.commit()
    db.refresh(person)
    return person


def test_budget_stand_rechnet_den_monat(client):
    from app.db.session import SessionLocal
    from app.services import ai_budget_service as abs_

    with SessionLocal() as db:
        person = _nutzer(db, name="budget1")
        abs_.protokolliere(db, user_id=person.id, server_id=None,
                           funktion="crash_analyse", modell="claude-sonnet-5-5",
                           tokens_ein=1000, tokens_aus=500, kosten_cent=150.0)
        stand = abs_.budget_stand(db, {"monatsbudget_cent": 20000})

    assert stand["verbraucht_cent"] == 150.0
    assert stand["rest_cent"] == 19850.0
    assert stand["gesperrt"] is False
    assert any(z["funktion"] == "crash_analyse" for z in stand["je_funktion"])


def test_budget_sperrt_bei_erreichtem_deckel(client):
    """Der Deckel muss VOR dem Aufruf greifen - danach ist das Geld weg."""
    from app.db.session import SessionLocal
    from app.services import ai_budget_service as abs_

    with SessionLocal() as db:
        person = _nutzer(db, name="budget2")
        abs_.protokolliere(db, user_id=person.id, server_id=None, funktion="agent",
                           modell="claude-opus-5-5", kosten_cent=20000.0)

        with pytest.raises(abs_.BudgetGesperrt, match="Monatsbudget"):
            abs_.pruefe_erlaubt(db, user=person, funktion="agent",
                                einstellungen={"aktiv": True, "monatsbudget_cent": 20000})


def test_warnschwelle_meldet_sich_vor_der_sperre(client):
    from app.db.session import SessionLocal
    from app.services import ai_budget_service as abs_

    with SessionLocal() as db:
        person = _nutzer(db, name="budget3")
        abs_.protokolliere(db, user_id=person.id, server_id=None, funktion="changelog",
                           modell="claude-haiku-5-5", kosten_cent=17000.0)
        stand = abs_.budget_stand(db, {"monatsbudget_cent": 20000, "warnschwelle": 0.8})

    assert stand["warnt"] is True
    assert stand["gesperrt"] is False


def test_rate_limit_faengt_die_schleife_ab(client):
    """Genau der Fall aus der TODO-Notiz: eine Schleife soll nicht das Guthaben fressen."""
    from app.db.session import SessionLocal
    from app.services import ai_budget_service as abs_

    with SessionLocal() as db:
        person = _nutzer(db, name="limit1")
        for _ in range(5):
            abs_.protokolliere(db, user_id=person.id, server_id=None,
                               funktion="crash_analyse", modell="x", kosten_cent=1.0)

        with pytest.raises(abs_.BudgetGesperrt, match="Stundenkontingent"):
            abs_.pruefe_erlaubt(db, user=person, funktion="crash_analyse",
                                einstellungen={"aktiv": True, "limit_stunde": 5})


def test_abgelehnte_aufrufe_zaehlen_ans_limit(client):
    """Sonst liesse sich das Limit mit Anfragen umgehen, die an der Themen-Sperre haengen."""
    from app.db.session import SessionLocal
    from app.services import ai_budget_service as abs_

    with SessionLocal() as db:
        person = _nutzer(db, name="limit2")
        for _ in range(3):
            abs_.protokolliere(db, user_id=person.id, server_id=None, funktion="agent",
                               modell="-", status="abgelehnt", hinweis="Thema")

        assert abs_.aufrufe_seit(
            db, person.id, datetime.now(timezone.utc) - timedelta(hours=1)) == 3


def test_moderator_bekommt_das_halbe_kontingent(client):
    from app.db.session import SessionLocal
    from app.services import ai_budget_service as abs_

    with SessionLocal() as db:
        moderator = _nutzer(db, rolle="moderator", name="mod1")
        for _ in range(5):
            abs_.protokolliere(db, user_id=moderator.id, server_id=None,
                               funktion="changelog", modell="x", kosten_cent=1.0)

        # Admin-Grenze 10 -> Moderator 5 -> schon erreicht.
        with pytest.raises(abs_.BudgetGesperrt, match="Stundenkontingent"):
            abs_.pruefe_erlaubt(db, user=moderator, funktion="changelog",
                                einstellungen={"aktiv": True, "limit_stunde": 10})


def test_nur_lesen_darf_keine_ki_nutzen(client):
    from app.db.session import SessionLocal
    from app.services import ai_budget_service as abs_

    with SessionLocal() as db:
        gast = _nutzer(db, rolle="view_only", name="gast1")
        with pytest.raises(abs_.BudgetGesperrt, match="Moderator-Rechte"):
            abs_.pruefe_erlaubt(db, user=gast, funktion="crash_analyse",
                                einstellungen={"aktiv": True})


def test_abschaltung_wirkt_global_und_je_funktion(client):
    from app.db.session import SessionLocal
    from app.services import ai_budget_service as abs_

    with SessionLocal() as db:
        person = _nutzer(db, name="aus1")
        with pytest.raises(abs_.BudgetGesperrt, match="abgeschaltet"):
            abs_.pruefe_erlaubt(db, user=person, funktion="agent",
                                einstellungen={"aktiv": False})
        with pytest.raises(abs_.BudgetGesperrt, match="'agent' ist"):
            abs_.pruefe_erlaubt(db, user=person, funktion="agent",
                                einstellungen={"aktiv": True,
                                               "funktionen": {"agent": False}})


# ------------------------------------------------------------------- Kostenrechnung

def test_kosten_werden_aus_tokens_gerechnet(client):
    from app.services.ai_provider_service import kosten_cent

    # Sonnet 5.5: 200 Cent je 1M ein, 1000 Cent je 1M aus.
    cent = kosten_cent("claude-sonnet-5-5", 1_000_000, 1_000_000)
    assert cent == pytest.approx(1200.0)
    # Haiku ist deutlich guenstiger - das ist der Sinn der Modellwahl je Funktion.
    assert kosten_cent("claude-haiku-5-5", 1_000_000, 1_000_000) < cent


def test_unbekanntes_modell_rechnet_nicht_falsch(client):
    """Lieber 0 als eine erfundene Zahl - der Aufruf steht trotzdem im Protokoll."""
    from app.services.ai_provider_service import kosten_cent

    assert kosten_cent("gibt-es-nicht", 1_000_000, 1_000_000) == 0.0


def test_eigene_preise_schlagen_die_vorgabe(client):
    """Die Vorgaben stammen aus Drittquellen - wer sie korrigiert, muss gewinnen."""
    from app.services.ai_provider_service import kosten_cent

    eigene = {"claude-sonnet-5-5": {"cent_ein": 100.0, "cent_aus": 100.0}}
    assert kosten_cent("claude-sonnet-5-5", 1_000_000, 0, eigene) == pytest.approx(100.0)


# ------------------------------------------------------------------- Themen-Sperre

@pytest.mark.parametrize("frage_text", [
    "Warum stuerzt mein Server ab?",
    "Welche Mod verursacht den Lag?",
    "Wie viel RAM soll ich der JVM geben?",
    "Ist das Backup vollstaendig?",
])
def test_serverfragen_kommen_durch(client, frage_text):
    from app.services.ai_service import _thema_passt

    assert _thema_passt(frage_text) is True


@pytest.mark.parametrize("frage_text", [
    "Schreibe ein Gedicht ueber den Herbst.",
    "Wie lautet die Hauptstadt von Peru?",
    "Erklaere mir die Relativitaetstheorie.",
])
def test_fremde_fragen_werden_vorher_abgefangen(client, frage_text):
    """Vor dem Aufruf, damit sie nichts kosten."""
    from app.services.ai_service import _thema_passt

    assert _thema_passt(frage_text) is False


def test_grundauftrag_legt_die_rolle_und_die_ablehnung_fest(client):
    from app.services import ai_service

    auftrag = ai_service._baue_auftrag("crash_analyse")
    assert "Minecraft-Server-Managers" in auftrag
    assert ai_service.ABLEHNUNG in auftrag
    assert "Absturzursachen" in auftrag
    # Je Funktion kommt ein eigener Teil dazu.
    assert ai_service._baue_auftrag("changelog") != auftrag
    assert "Breaking Changes" in ai_service._baue_auftrag("changelog")


# -------------------------------------------------- Zusammenspiel in ai_service.frage

class _Attrappe:
    """Anbieter-Attrappe: antwortet, ohne das Netz zu beruehren."""

    def __init__(self, text="Antwort", tokens=(100, 50), werkzeuge=None, grund="ende"):
        self.text, self.tokens = text, tokens
        self.werkzeuge = werkzeuge or []
        self.grund = grund
        self.gesehen: list = []

    def frage(self, anfrage, schluessel, **_):
        from app.services.ai_provider_service import KiAntwort

        self.gesehen.append(anfrage)
        return KiAntwort(text=self.text, modell=anfrage.modell,
                         tokens_ein=self.tokens[0], tokens_aus=self.tokens[1],
                         werkzeug_aufrufe=self.werkzeuge, abbruchgrund=self.grund)


def _bereite_vor(db, monkeypatch, attrappe, **einstellungen):
    from app.services import ai_provider_service, ai_settings_service as ais

    ais.setze_schluessel(db, "sk-ant-api03-TEST-1234567890")
    ais.speichere(db, {"aktiv": True, **einstellungen})
    monkeypatch.setitem(ai_provider_service.ANBIETER, "anthropic", attrappe)


def test_frage_saeubert_vor_dem_senden(client, monkeypatch):
    """Der entscheidende Test: was der Anbieter SIEHT, darf keine IP enthalten."""
    from app.db.session import SessionLocal
    from app.services import ai_service

    attrappe = _Attrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="frage1")
        _bereite_vor(db, monkeypatch, attrappe)

        ergebnis = ai_service.frage(
            db, user=person, funktion="crash_analyse",
            nachricht="Warum stuerzt der Server ab?",
            kontext="Connection from 203.0.113.7\nrcon.password=geheim\n"
                    "[12:00:01] Exception ticking entity")

    gesendet = attrappe.gesehen[0].nachrichten[0]["inhalt"]
    assert "203.0.113.7" not in gesendet
    assert "geheim" not in gesendet
    # Diagnose-Material bleibt drin.
    assert "[12:00:01]" in gesendet
    assert "Exception ticking entity" in gesendet
    assert ergebnis["text"] == "Antwort"
    assert any("IP-Adresse" in h for h in ergebnis["hinweise"])


def test_frage_protokolliert_mit_echten_tokens(client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import ai_budget_service as abs_, ai_service

    attrappe = _Attrappe(tokens=(2000, 1000))
    with SessionLocal() as db:
        person = _nutzer(db, name="frage2")
        _bereite_vor(db, monkeypatch, attrappe)
        ai_service.frage(db, user=person, funktion="crash_analyse",
                         nachricht="Warum stuerzt der Server ab?")

        letzte = abs_.letzte_aufrufe(db, grenze=1)[0]

    assert letzte.tokens_ein == 2000
    assert letzte.tokens_aus == 1000
    assert letzte.status == "ok"
    assert letzte.kosten_cent > 0


def test_frage_lehnt_fremdes_thema_ohne_aufruf_ab(client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import ai_service

    attrappe = _Attrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="frage3")
        _bereite_vor(db, monkeypatch, attrappe)

        # Der Wortfilter gilt fuer den freien Agenten - dort wird beliebig gefragt.
        with pytest.raises(ai_service.KiAbgelehnt, match="Bezug zur Serververwaltung"):
            ai_service.frage(db, user=person, funktion="agent",
                             nachricht="Schreibe ein Gedicht ueber den Herbst.")

    # Kein Aufruf beim Anbieter -> keine Kosten.
    assert attrappe.gesehen == []


def test_gebundene_funktionen_brauchen_kein_schluesselwort(client, monkeypatch):
    """"Was aendert sich?" ist bei der Changelog-Funktion eine normale Frage. Der
    Wortfilter darf sie nicht abweisen - der Zweck steht durch den Aufruf fest."""
    from app.db.session import SessionLocal
    from app.services import ai_service

    attrappe = _Attrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="frage4b")
        _bereite_vor(db, monkeypatch, attrappe)
        ergebnis = ai_service.frage(db, user=person, funktion="changelog",
                                    nachricht="Was aendert sich?")

    assert ergebnis["text"] == "Antwort"
    assert len(attrappe.gesehen) == 1


def test_mitgelieferte_daten_heben_die_wortsperre_auf(client, monkeypatch):
    """Wer einen Log mitschickt, hat Serverbezug - auch beim Agenten ohne Schluesselwort."""
    from app.db.session import SessionLocal
    from app.services import ai_service

    attrappe = _Attrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="frage4")
        _bereite_vor(db, monkeypatch, attrappe)
        ergebnis = ai_service.frage(db, user=person, funktion="agent",
                                    nachricht="Was ist hier los?",
                                    kontext="[12:00:01] Exception ticking entity")

    assert ergebnis["text"] == "Antwort"
    assert len(attrappe.gesehen) == 1


def test_antwort_mit_ablehnung_wird_erkannt(client, monkeypatch):
    """Lehnt das Modell selbst ab, soll das nicht als Antwort durchgehen."""
    from app.db.session import SessionLocal
    from app.services import ai_budget_service as abs_, ai_service

    attrappe = _Attrappe(text="THEMA_ABGELEHNT")
    with SessionLocal() as db:
        person = _nutzer(db, name="frage5")
        _bereite_vor(db, monkeypatch, attrappe)

        with pytest.raises(ai_service.KiAbgelehnt, match="themenfremd"):
            ai_service.frage(db, user=person, funktion="crash_analyse",
                             nachricht="Erzaehl mir vom Server und auch von Peru")

        # Trotzdem protokolliert - der Aufruf hat ja Geld gekostet.
        letzte = abs_.letzte_aufrufe(db, grenze=1)[0]
    assert letzte.status == "abgelehnt"
    assert letzte.kosten_cent > 0


def test_frage_ohne_schluessel_wird_abgelehnt(client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import ai_service, ai_settings_service as ais

    with SessionLocal() as db:
        person = _nutzer(db, name="frage6")
        ais.speichere(db, {"aktiv": True})
        ais.loesche_schluessel(db)

        with pytest.raises(ai_service.KiAbgelehnt, match="kein API-Schluessel"):
            ai_service.frage(db, user=person, funktion="crash_analyse",
                             nachricht="Warum stuerzt der Server ab?")


def test_abgeschnittene_antwort_wird_gemeldet(client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import ai_service

    attrappe = _Attrappe(grund="laenge")
    with SessionLocal() as db:
        person = _nutzer(db, name="frage7")
        _bereite_vor(db, monkeypatch, attrappe)
        ergebnis = ai_service.frage(db, user=person, funktion="crash_analyse",
                                    nachricht="Warum stuerzt der Server ab?")

    assert any("abgeschnitten" in h for h in ergebnis["hinweise"])


def test_werkzeuge_werden_nur_vorgeschlagen(client, monkeypatch):
    """Entscheidung des Nutzers: Vorschlagen, der Mensch bestaetigt. frage() darf nichts
    ausfuehren."""
    from app.db.session import SessionLocal
    from app.services import ai_service

    attrappe = _Attrappe(text="Ich wuerde ein Backup anlegen.", werkzeuge=[
        {"name": "backup_anlegen", "eingabe": {"server_id": 1}, "id": "abc"}])
    with SessionLocal() as db:
        person = _nutzer(db, name="frage8")
        _bereite_vor(db, monkeypatch, attrappe)
        ergebnis = ai_service.frage(db, user=person, funktion="agent",
                                    nachricht="Mache ein Backup von Server 1")

    assert ergebnis["werkzeug_aufrufe"] == [
        {"name": "backup_anlegen", "eingabe": {"server_id": 1}, "id": "abc"}]


def test_modell_haengt_an_der_funktion(client, monkeypatch):
    """Changelog auf ein kleines Modell, Agent auf ein grosses - das entscheidet, wie weit
    das Budget reicht."""
    from app.db.session import SessionLocal
    from app.services import ai_service

    attrappe = _Attrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="frage9")
        _bereite_vor(db, monkeypatch, attrappe)
        ai_service.frage(db, user=person, funktion="changelog",
                         nachricht="Was aendert sich beim Mod-Update?")
        ai_service.frage(db, user=person, funktion="agent",
                         nachricht="Sichere den Server")

    assert attrappe.gesehen[0].modell == "claude-haiku-5-5"
    assert attrappe.gesehen[1].modell == "claude-opus-5-5"


def test_eigene_modellwahl_schlaegt_die_vorgabe(client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import ai_service

    attrappe = _Attrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="frage10")
        _bereite_vor(db, monkeypatch, attrappe,
                     modell_changelog="claude-opus-5-5")
        ai_service.frage(db, user=person, funktion="changelog",
                         nachricht="Was aendert sich?")

    assert attrappe.gesehen[0].modell == "claude-opus-5-5"


def test_pseudonymisierung_wirkt_in_beide_richtungen(client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import ai_service

    attrappe = _Attrappe(text="Spieler1 verursacht den Absturz.")
    with SessionLocal() as db:
        person = _nutzer(db, name="frage11")
        _bereite_vor(db, monkeypatch, attrappe, namen_ersetzen=True)
        ergebnis = ai_service.frage(
            db, user=person, funktion="crash_analyse", nachricht="Wer stuerzt ab?",
            kontext="[12:00:06] [Server thread/INFO]: Thormak2002 joined the game")

    gesendet = attrappe.gesehen[0].nachrichten[0]["inhalt"]
    assert "Thormak2002" not in gesendet      # geht pseudonymisiert hinaus ...
    assert "Spieler1" in gesendet
    assert "Thormak2002" in ergebnis["text"]  # ... kommt lesbar zurueck


# ------------------------------------------------------------- Oberflaeche und Routen

def _login_super(client):
    antwort = client.post("/login", data={"username": "admin", "password": "admin123!"},
                          follow_redirects=False)
    assert antwort.status_code == 303


def test_einstellungsseite_zeigt_den_ki_reiter(client):
    _login_super(client)
    seite = client.get("/settings")
    assert seite.status_code == 200
    assert 'data-settings-tab="ki"' in seite.text
    assert 'data-settings-panel="ki"' in seite.text
    assert "Monatsbudget" in seite.text
    assert "Themen-Sperre" in seite.text


def test_schluessel_wird_ueber_die_route_verschluesselt(client):
    """Der Klartext darf weder in der Antwort noch in der Datenbank auftauchen."""
    from app.db.session import SessionLocal
    from app.models.app_setting import AppSetting
    from sqlalchemy import select

    _login_super(client)
    geheim = "sk-ant-api03-ROUTENTEST-abcdefghij"
    antwort = client.post("/settings/ki/schluessel", data={"api_key": geheim},
                          follow_redirects=False)
    assert antwort.status_code == 303

    seite = client.get("/settings")
    assert geheim not in seite.text

    with SessionLocal() as db:
        zeilen = db.scalars(select(AppSetting)).all()
        alle_werte = " ".join(z.value or "" for z in zeilen)
    assert geheim not in alle_werte


def test_einstellungen_werden_gespeichert(client):
    from app.db.session import SessionLocal
    from app.services import ai_settings_service as ais

    _login_super(client)
    antwort = client.post("/settings/ki", data={
        "aktiv": "true", "themensperre": "true",
        "monatsbudget_dollar": "150", "warnschwelle_prozent": "70",
        "limit_stunde": "10", "limit_tag": "30",
        "funktion_crash_analyse": "true",
        "modell_changelog": "claude-haiku-5-5",
    }, follow_redirects=False)
    assert antwort.status_code == 303

    with SessionLocal() as db:
        werte = ais.hole_einstellungen(db)
    assert werte["aktiv"] is True
    assert werte["monatsbudget_cent"] == 15000
    assert werte["warnschwelle"] == pytest.approx(0.7)
    assert werte["limit_stunde"] == 10
    assert werte["funktionen"]["crash_analyse"] is True
    # Nicht angehakte Funktionen werden abgeschaltet.
    assert werte["funktionen"]["agent"] is False


def test_budget_null_wird_als_risiko_gemeldet(client):
    """Kein Deckel ist genau der Fall, den die TODO-Notiz als Gefahr nennt."""
    from app.db.session import SessionLocal
    from app.services import ai_settings_service as ais

    with SessionLocal() as db:
        meldungen = ais.speichere(db, {"monatsbudget_cent": 0})
    assert any("KEIN Deckel" in m for m in meldungen)


def test_schluessel_entfernen_funktioniert(client):
    from app.db.session import SessionLocal
    from app.services import ai_settings_service as ais

    _login_super(client)
    client.post("/settings/ki/schluessel", data={"api_key": "sk-ant-weg-123456789"},
                follow_redirects=False)
    client.post("/settings/ki/schluessel", data={"loeschen": "true"},
                follow_redirects=False)

    with SessionLocal() as db:
        assert ais.hole_einstellungen(db)["schluessel_hinterlegt"] is False


def test_leerer_schluessel_wird_abgewiesen(client):
    _login_super(client)
    antwort = client.post("/settings/ki/schluessel", data={"api_key": "  "},
                          follow_redirects=False)
    assert antwort.status_code == 303
    assert "kein Schluessel eingegeben" in client.get("/settings").text
