"""Tests zu den Fehlern, die eine gegnerische Pruefung der KI-Funktionen gefunden hat.

Jeder Test hier haelt einen Fehler fest, der tatsaechlich im Code war und reproduziert
wurde. Sie stehen beieinander, weil sie eine gemeinsame Ursache haben: die Sparsamkeit und
die Saeuberung galten nur fuer den WEG, an den ich gedacht hatte (den Logauszug), nicht
fuer die anderen (die Frage des Nutzers, den Gespraechsverlauf, Logzeilen statt
Configzeilen).

Die drei schwersten:

* Das Fragefeld war unbegrenzt und ungesaeubert. Eine 12-MB-Frage ging vollstaendig an
  den Anbieter - statt 3 Cent kostete ein Aufruf 2 Dollar.
* Die Geldschranken zaehlten erst NACH der Antwort. Zwanzig gleichzeitige Aufrufe kamen
  alle durch, obwohl fuenf erlaubt waren.
* Geheimnisse mitten in einer Logzeile blieben stehen, weil die Muster am Zeilenanfang
  hingen - Logzeilen beginnen aber mit einem Zeitstempel.
"""
import pytest


class _Attrappe:
    def __init__(self, text="Antwort", verzoegerung=0.0):
        self.text = text
        self.verzoegerung = verzoegerung
        self.gesehen: list = []

    def frage(self, anfrage, schluessel, **_):
        import time

        from app.services.ai_provider_service import KiAntwort

        if self.verzoegerung:
            time.sleep(self.verzoegerung)
        self.gesehen.append(anfrage)
        return KiAntwort(text=self.text, modell=anfrage.modell, tokens_ein=1000,
                         tokens_aus=200, werkzeug_aufrufe=[], abbruchgrund="ende")


def _bereite_vor(db, monkeypatch, attrappe, **einstellungen):
    from app.services import ai_provider_service, ai_settings_service as ais

    ais.setze_schluessel(db, "sk-ant-api03-TEST-1234567890")
    ais.speichere(db, {"aktiv": True, **einstellungen})
    monkeypatch.setitem(ai_provider_service.ANBIETER, "anthropic", attrappe)


def _nutzer(db, rolle="admin", name="haertung"):
    from app.core.security import hash_password
    from app.models.user import User

    person = User(username=name, password_hash=hash_password("Egal12345!"),
                  role=rolle, is_active=True)
    db.add(person)
    db.commit()
    db.refresh(person)
    return person


# ------------------------------------------------- Die Frage kostet auch Geld

def test_lange_frage_wird_gekappt(client, monkeypatch):
    """400 KB Frage sind rund 100.000 Token. Die ganze Sparsamkeit galt nur fuer den Log -
    die Frage ging unveraendert hinaus, und ein Aufruf kostete statt 3 Cent zwei Dollar."""
    from app.db.session import SessionLocal
    from app.services import ai_service

    attrappe = _Attrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="lang1")
        _bereite_vor(db, monkeypatch, attrappe)
        ergebnis = ai_service.frage(db, user=person, funktion="crash_analyse",
                                    nachricht="Server " + ("x" * 400_000))

    gesendet = attrappe.gesehen[0].nachrichten[0]["inhalt"]
    assert len(gesendet) < ai_service.MAX_FRAGE_ZEICHEN + 2000, \
        f"Die Frage ging mit {len(gesendet)} Zeichen hinaus"
    assert any("Frage" in h and "gekuerzt" in h.lower() or "weggelassen" in h
               for h in ergebnis["hinweise"])


def test_frage_wird_gesaeubert(client, monkeypatch):
    """Wer eine Fehlermeldung in das Fragefeld kopiert - der naheliegendste Gebrauch -,
    haette damit IP-Adressen und Geheimnisse ungesaeubert hinausgeschickt, obwohl die
    Zusage lautet, dass sie entfernt werden."""
    from app.db.session import SessionLocal
    from app.services import ai_service

    attrappe = _Attrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="saeub1")
        _bereite_vor(db, monkeypatch, attrappe)
        ergebnis = ai_service.frage(
            db, user=person, funktion="crash_analyse",
            nachricht="Der Server meldet: Connection from 203.0.113.9:25565 und "
                      "rcon.password=meinGeheimnis")

    gesendet = attrappe.gesehen[0].nachrichten[0]["inhalt"]
    assert "203.0.113.9" not in gesendet
    assert "meinGeheimnis" not in gesendet
    assert any("IP-Adresse" in h for h in ergebnis["hinweise"])


def test_verlauf_wird_gesaeubert(client, monkeypatch):
    """Der Verlauf kommt aus dem Browser und kann dieselben Daten tragen wie ein Log."""
    from app.db.session import SessionLocal
    from app.services import ai_service

    attrappe = _Attrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="saeub2")
        _bereite_vor(db, monkeypatch, attrappe)
        ai_service.frage(
            db, user=person, funktion="agent", nachricht="Was ist mit dem Server?",
            verlauf=[{"rolle": "nutzer",
                      "inhalt": "Vorhin stand da 198.51.100.4 und secret=abc123xyz"},
                     {"rolle": "assistent", "inhalt": "Verstanden."}])

    alles = " ".join(str(n.get("inhalt")) for n in attrappe.gesehen[0].nachrichten)
    assert "198.51.100.4" not in alles
    assert "abc123xyz" not in alles


# ------------------------------------------- Geheimnisse mitten in der Zeile

@pytest.mark.parametrize("zeile,geheimnis", [
    ("[12:00:00] [Server thread/INFO]: rcon.password=meinRconPasswort",
     "meinRconPasswort"),
    ("[12:00:00] [main/WARN]: forwarding-secret=abc123geheim ist zu kurz",
     "abc123geheim"),
    ("JVM Flags: 3 total; -Xmx8G -Dapi_key=AbCdEf1234567890 -XX:+UseG1GC",
     "AbCdEf1234567890"),
    ("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NX0.abcdefghijk",
     "eyJhbGciOiJIUzI1NiJ9"),
    ("[12:00:00] Webhook: https://discord.com/api/webhooks/123456789/AbCdEfGhIjKlMnOp",
     "AbCdEfGhIjKlMnOp"),
    ("[12:00:00] GET https://example.com/api?token=geheim123xyz&x=1", "geheim123xyz"),
    ("[12:00:00] rcon.password=1234567890", "1234567890"),
])
def test_geheimnis_in_logzeile_wird_entfernt(zeile, geheimnis):
    """Alle Muster hingen mit ^ am Zeilenanfang. Das passt zu Configdateien - die
    Log-/Crash-Analyse schickt aber ausschliesslich Logzeilen hinaus, und die beginnen
    mit Zeitstempel und Thread-Praefix. Die Zeile mit den JVM-Flags steht in JEDEM
    Crash-Report."""
    from app.services.ai_privacy_service import saeubere

    gesaeubert, bericht = saeubere(zeile)

    assert geheimnis not in gesaeubert, gesaeubert
    assert bericht["geheimnisse"] >= 1


@pytest.mark.parametrize("zeile", [
    "[10:00:00] [main/INFO]: Starting minecraft server version 26.3",
    "[12:00:00] [Server/INFO]: Spieler Thormak02 hat gejoint",
    "  - loaded 42 mods, max_tokens: 2048",
    "[12:00:00] Token-Verbrauch: 500 ein, 200 aus",
])
def test_harmlose_zeile_bleibt_stehen(zeile):
    """Ueberredigieren ist auch ein Fehler: eine Zahl zu verstecken, die bei der Diagnose
    hilft, macht die Antwort schlechter."""
    from app.services.ai_privacy_service import saeubere

    gesaeubert, bericht = saeubere(zeile)

    assert gesaeubert == zeile
    assert bericht["geheimnisse"] == 0


# --------------------------------------------------- Geldschranken und Nebenlauf

def test_gleichzeitige_aufrufe_sprengen_das_stundenlimit_nicht(client, monkeypatch):
    """Der Kern des Fundes: protokolliert wurde erst NACH der Antwort. Waehrend ein Aufruf
    lief - beim echten Anbieter Sekunden -, sah ihn keine andere Pruefung, und zwanzig
    gleichzeitige Aufrufe kamen alle durch."""
    from threading import Barrier, Thread

    from app.db.session import SessionLocal
    from app.services import ai_service

    attrappe = _Attrappe(verzoegerung=0.25)
    grenze = 3
    anzahl = 8

    with SessionLocal() as db:
        person = _nutzer(db, name="race1")
        _bereite_vor(db, monkeypatch, attrappe, limit_stunde=grenze, limit_tag=100,
                     monatsbudget_cent=100_000)
        person_id = person.id

    schranke = Barrier(anzahl)
    durch: list[int] = []
    abgelehnt: list[int] = []

    def _lauf(nummer: int) -> None:
        from app.models.user import User

        with SessionLocal() as db:
            wer = db.get(User, person_id)
            schranke.wait()
            try:
                ai_service.frage(db, user=wer, funktion="crash_analyse",
                                 nachricht=f"Was ist mit dem Server {nummer}?")
                durch.append(nummer)
            except ai_service.KiAbgelehnt:
                abgelehnt.append(nummer)
            except Exception:  # noqa: BLE001 - z.B. gesperrte SQLite zaehlt nicht als durch
                pass

    faeden = [Thread(target=_lauf, args=(i,)) for i in range(anzahl)]
    for f in faeden:
        f.start()
    for f in faeden:
        f.join(timeout=30)

    assert len(attrappe.gesehen) <= grenze, (
        f"{len(attrappe.gesehen)} Aufrufe erreichten den Anbieter, erlaubt waren {grenze}")
    assert abgelehnt, "Es haette Ablehnungen geben muessen"


def test_laufender_aufruf_zaehlt_schon_mit(client, monkeypatch):
    """Die Vorbuchung ist der Mechanismus dahinter: sie muss im Verbrauch auftauchen,
    bevor die Antwort da ist."""
    from app.db.session import SessionLocal
    from app.services import ai_budget_service as abs_

    with SessionLocal() as db:
        person = _nutzer(db, name="vorb1")
        zeile_id = abs_.reserviere(db, user_id=person.id, server_id=None,
                                   funktion="crash_analyse", modell="claude-sonnet-5-5",
                                   geschaetzt_cent=42.0)
        stand = abs_.budget_stand(db, {"monatsbudget_cent": 20000})
        anzahl = abs_.aufrufe_seit(
            db, person.id, __import__("datetime").datetime.now(
                __import__("datetime").timezone.utc)
            - __import__("datetime").timedelta(hours=1))

    assert zeile_id > 0
    assert stand["verbraucht_cent"] >= 42.0, "Die Vorbuchung zaehlt nicht mit"
    assert anzahl == 1


def test_abbuchen_ersetzt_die_schaetzung(client, monkeypatch):
    from app.db.session import SessionLocal
    from app.services import ai_budget_service as abs_

    with SessionLocal() as db:
        person = _nutzer(db, name="vorb2")
        zeile_id = abs_.reserviere(db, user_id=person.id, server_id=None,
                                   funktion="changelog", modell="claude-haiku-5-5",
                                   geschaetzt_cent=99.0)
        abs_.buche_ab(db, zeile_id, modell="claude-haiku-5-5", tokens_ein=100,
                      tokens_aus=50, kosten_cent=0.5, status="ok")
        stand = abs_.budget_stand(db, {"monatsbudget_cent": 20000})
        eintraege = abs_.letzte_aufrufe(db, grenze=5)

    assert stand["verbraucht_cent"] == pytest.approx(0.5)
    assert len(eintraege) == 1
    assert eintraege[0].status == "ok"
    assert eintraege[0].kosten_cent == pytest.approx(0.5)


def test_ohne_vorbuchung_findet_kein_aufruf_statt(client, monkeypatch):
    """Ein Aufruf, der Geld kostet und nicht gezaehlt wird, hoehlt den Deckel aus - und
    zwar genau dann, wenn die Datenbank Probleme macht."""
    from app.db.session import SessionLocal
    from app.services import ai_budget_service as abs_, ai_service

    attrappe = _Attrappe()
    with SessionLocal() as db:
        person = _nutzer(db, name="keinebuchung")
        _bereite_vor(db, monkeypatch, attrappe)

        def _kaputt(*_a, **_k):
            raise abs_.BudgetGesperrt("Buchung nicht moeglich (Test).")

        monkeypatch.setattr(abs_, "pruefe_und_reserviere", _kaputt)

        with pytest.raises(ai_service.KiAbgelehnt, match="Buchung"):
            ai_service.frage(db, user=person, funktion="crash_analyse",
                             nachricht="Was ist mit dem Server?")

    assert attrappe.gesehen == [], "Ohne Buchung darf kein Aufruf hinausgehen"


def test_schaetzung_liegt_nicht_zu_niedrig(client):
    """Eine Schaetzung, die zu niedrig liegt, waere nutzlos - sie soll den Deckel halten."""
    from app.services import ai_budget_service as abs_

    klein = abs_.schaetze_cent("claude-sonnet-5-5", 4_000, 2048)
    gross = abs_.schaetze_cent("claude-sonnet-5-5", 400_000, 2048)

    assert gross > klein * 5, "Eine 100-fach groessere Eingabe muss deutlich mehr kosten"
    assert klein > 0


def test_haengende_vorbuchung_blaeht_den_monat_nicht_auf(client):
    """Stuerzt der Dienst mitten im Aufruf ab, bleibt die Vorbuchung stehen. Wuerde sie
    weiter mitzaehlen, waere der Monatsdeckel irgendwann erreicht, obwohl nie Geld
    geflossen ist - eine selbstgebaute Sperre."""
    from datetime import datetime, timedelta, timezone

    from app.db.session import SessionLocal
    from app.models.ai_usage import AiUsage
    from app.services import ai_budget_service as abs_

    with SessionLocal() as db:
        person = _nutzer(db, name="haengt1")
        frisch = abs_.reserviere(db, user_id=person.id, server_id=None,
                                 funktion="agent", modell="claude-sonnet-5-5",
                                 geschaetzt_cent=10.0)
        alt = abs_.reserviere(db, user_id=person.id, server_id=None,
                              funktion="agent", modell="claude-sonnet-5-5",
                              geschaetzt_cent=5000.0)
        # Die zweite Buchung kuenstlich altern lassen.
        zeile = db.get(AiUsage, alt)
        zeile.created_at = (datetime.now(timezone.utc)
                            - timedelta(minutes=abs_.LAEUFT_MAX_MINUTEN + 5))
        db.add(zeile)
        db.commit()

        stand = abs_.budget_stand(db, {"monatsbudget_cent": 20000})
        aufgeraeumt = abs_.raeume_haengende_auf(db)
        nachher = abs_.budget_stand(db, {"monatsbudget_cent": 20000})

    assert frisch and alt
    assert stand["verbraucht_cent"] == pytest.approx(10.0), \
        "Die haengende Buchung darf nicht mitzaehlen"
    assert aufgeraeumt == 1
    assert nachher["verbraucht_cent"] == pytest.approx(10.0)


def test_erfolgreicher_aufruf_laesst_keine_laufende_buchung_zurueck(client, monkeypatch):
    """Nach einem Aufruf darf keine Zeile auf "laeuft" stehen bleiben."""
    from app.db.session import SessionLocal
    from app.models.ai_usage import AiUsage
    from app.services import ai_budget_service as abs_, ai_service
    from sqlalchemy import select

    with SessionLocal() as db:
        person = _nutzer(db, name="keinrest")
        _bereite_vor(db, monkeypatch, _Attrappe())
        ai_service.frage(db, user=person, funktion="crash_analyse",
                         nachricht="Was ist mit dem Server?")
        offen = list(db.scalars(select(AiUsage).where(
            AiUsage.status == abs_.STATUS_LAEUFT)))
        alle = list(db.scalars(select(AiUsage)))

    assert offen == []
    assert len(alle) == 1, "Es soll EINE Zeile geben, nicht eine Buchung plus ein Protokoll"
    assert alle[0].status == "ok"
    assert alle[0].tokens_ein == 1000


def test_gescheiterter_aufruf_wird_auf_null_gesetzt(client, monkeypatch):
    """Der Anbieter hat nichts geliefert - dann darf die Schaetzung nicht stehen bleiben."""
    from app.db.session import SessionLocal
    from app.models.ai_usage import AiUsage
    from app.services import ai_provider_service, ai_service
    from sqlalchemy import select

    class _Kaputt:
        def frage(self, anfrage, schluessel, **_):
            raise ai_provider_service.KiFehler("Keine Verbindung zum Anbieter.")

    with SessionLocal() as db:
        person = _nutzer(db, name="kaputt1")
        _bereite_vor(db, monkeypatch, _Kaputt())

        with pytest.raises(ai_provider_service.KiFehler):
            ai_service.frage(db, user=person, funktion="crash_analyse",
                             nachricht="Was ist mit dem Server?")

        zeilen = list(db.scalars(select(AiUsage)))

    assert len(zeilen) == 1
    assert zeilen[0].status == "fehler"
    assert zeilen[0].kosten_cent == 0.0


# ------------------------------------------------------------ Auswahl im Log

def _langes_log(fuell=3000):
    kopf = ["[10:00:00] [main/INFO]: Starting minecraft server version 26.3",
            "[10:00:01] [main/INFO]: This server is running Paper version 26.3",
            "[10:00:02] [main/INFO]: Loading 42 mods"]
    mitte = [f"[10:01:00] [Server/INFO]: Rauschen {i}" for i in range(fuell)]
    fehler = ["[11:00:00] [Server/ERROR]: Exception ticking entity",
              "java.lang.NullPointerException: Cannot invoke getOwner()"]
    fehler += [f"\tat net.example.haustier.Besitzer.pruefe(Besitzer.java:{100 + i})"
               for i in range(20)]
    return kopf + mitte + fehler + mitte + ["[12:00:00] [Server/INFO]: Stopping server"]


@pytest.mark.parametrize("grenze", [20, 50, 100, 300, 370, 600])
def test_zeilengrenze_haelt_bei_jeder_groesse(grenze):
    """Unterhalb von etwa 370 Zeilen wurde die Grenze ignoriert: Kopf und Ende waren fest
    gesetzt und zusammen schon groesser. Wer kuenftig mit einem kleineren Wert analysiert -
    fuer ein billigeres Modell oder ein knappes Kontingent -, zahlte bis zum Sechsfachen."""
    from app.services.ai_log_service import waehle_ausschnitt

    _text, angaben = waehle_ausschnitt(_langes_log(), max_zeilen=grenze)

    assert angaben["gesendet"] <= grenze


def test_zeichengrenze_haelt():
    """Die Zeichengrenze liegt unter der von kuerze_fuer_anfrage - sonst schneidet die
    danach blind die Mitte heraus und wirft genau die gesuchten Fehlerstellen weg."""
    from app.services import ai_log_service as als

    lange_zeilen = [f"[10:00:00] ERROR Fehler {i}: " + "x" * 400 for i in range(2000)]
    text, angaben = als.waehle_ausschnitt(lange_zeilen)

    assert angaben["zeichen"] <= als.MAX_ZEICHEN
    assert len(text) <= als.MAX_ZEICHEN


def test_kopf_ueberlebt_auch_bei_kleiner_grenze():
    """Ohne Software, Version und Modliste kann die Antwort nicht sagen, WELCHE Mod schuld
    ist - und das ist die Frage."""
    from app.services.ai_log_service import waehle_ausschnitt

    text, _ = waehle_ausschnitt(_langes_log(), max_zeilen=60)

    assert "Paper version 26.3" in text


def test_fehlerstellen_zaehlt_den_ausschnitt_nicht_das_log():
    """Angezeigt wurde die Zahl der Fehlerstellen im GANZEN Log. Der Nutzer las "5000
    Fehlerstellen" und glaubte, die KI habe sie gesehen - sie sah 8 Prozent davon."""
    from app.services.ai_log_service import waehle_ausschnitt

    zeilen = ["[10:00:00] [main/INFO]: Starting minecraft server version 26.3"]
    zeilen += [f"[10:00:00] ERROR: Fehler {i}" for i in range(5000)]
    _text, angaben = waehle_ausschnitt(zeilen, max_zeilen=300)

    assert angaben["fehlerstellen"] <= angaben["gesendet"]
    assert angaben["fehlerstellen_gesamt"] == 5000
    assert angaben["fehlerstellen"] < 5000


def test_vollstaendiges_log_meldet_seine_fehlerstellen():
    """Vorher war die Zahl im vollstaendigen Fall fest 0 - ein Log voller Fehler meldete
    null Fehlerstellen."""
    from app.services.ai_log_service import waehle_ausschnitt

    _text, angaben = waehle_ausschnitt(
        ["[10:00:00] ERROR: eins", "[10:00:01] alles gut", "[10:00:02] SEVERE: zwei"])

    assert angaben["vollstaendig"] is True
    assert angaben["fehlerstellen"] == 2


def test_auswahl_rechnet_auch_beim_schlimmsten_log_schnell():
    """Jede Zeile ist gleichzeitig Fehler- UND Stacktrace-Zeile: der Nachlauf lief vorher
    fuer jede Zeile erneut durch denselben Abschnitt."""
    import time

    from app.services.ai_log_service import waehle_ausschnitt

    boese = ["\tat net.example.Foo$Exception.bar(Foo.java:1)"] * 20_000
    start = time.monotonic()
    waehle_ausschnitt(boese)
    dauer = time.monotonic() - start

    assert dauer < 3.0, f"Die Auswahl brauchte {dauer:.1f} s"


# --------------------------------------------------------- Logdateien lesen

@pytest.mark.parametrize("wert", [
    "//angreifer/freigabe/x", "\\\\angreifer\\freigabe\\x", "C:datei", "C:/Windows/x",
    "../../etc/passwd", "unterordner/../../x", "NUL", "con.log", "COM1", "/absolut",
    "latest.log:zone",
])
def test_gefaehrlicher_dateiwert_wird_vor_jedem_zugriff_abgewiesen(wert):
    """Schon `(base / wert).resolve()` greift auf das Dateisystem zu. Bei einem UNC-Wert
    baut Windows dabei eine SMB-Verbindung zum fremden Host auf und meldet sich mit dem
    Konto des Dienstes an - der Manager laeuft als SYSTEM. Die Containment-Pruefung lief
    erst danach."""
    from app.services.log_service import _wert_ist_harmlos

    assert _wert_ist_harmlos(wert) is False


@pytest.mark.parametrize("wert", [
    "latest.log", "2026-01-01-1.log.gz", "unterordner/x.log",
    "crash-2026-10-27_11.00.00-server.txt",
])
def test_normaler_dateiwert_bleibt_erlaubt(wert):
    from app.services.log_service import _wert_ist_harmlos

    assert _wert_ist_harmlos(wert) is True


def test_defektes_gz_gibt_eine_meldung_keinen_absturz(client, tmp_path):
    """Statt "Logdatei nicht lesbar" kam eine 500-Seite - und zwar genau dann, wenn man
    die Logseite am dringendsten braucht."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services.log_service import read_log_file

    basis = tmp_path / "gz-kaputt"
    (basis / "logs").mkdir(parents=True)
    (basis / "logs" / "halb.log.gz").write_bytes(b"\x1f\x8b\x08\x00abgeschnitten")

    with SessionLocal() as db:
        server = Server(name="gzk", slug="gzk", server_type="paper", mc_version="26.3",
                        base_path=str(basis), port=25751, status="stopped",
                        start_mode="command", start_command="java -jar server.jar")
        db.add(server)
        db.commit()
        db.refresh(server)

        with pytest.raises(ValueError, match="nicht gelesen"):
            read_log_file(server, "server:halb.log.gz")


def test_analyse_liest_kopf_und_ende(client, tmp_path):
    """read_log_file liefert nur den Schwanz. Bei 200.000 Zeilen fehlte damit der Kopf -
    und mit ihm die Antwort auf "welche Mod?"."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.services.log_service import lies_fuer_analyse

    basis = tmp_path / "lang"
    (basis / "logs").mkdir(parents=True)
    zeilen = ["[10:00:00] [main/INFO]: This server is running Paper version 26.3"]
    zeilen += [f"[10:00:00] Mitte {i}" for i in range(50_000)]
    zeilen += ["[12:00:00] [Server/ERROR]: Exception ticking entity"]
    (basis / "logs" / "latest.log").write_text("\n".join(zeilen), encoding="utf-8")

    with SessionLocal() as db:
        server = Server(name="lang", slug="lang", server_type="paper", mc_version="26.3",
                        base_path=str(basis), port=25752, status="stopped",
                        start_mode="command", start_command="java -jar server.jar")
        db.add(server)
        db.commit()
        db.refresh(server)
        gelesen, _label, gesamt = lies_fuer_analyse(server, "server:latest.log",
                                                    max_zeilen=2000)

    assert gesamt == len(zeilen), "Die Gesamtzahl muss die ECHTE sein"
    assert len(gelesen) <= 2001
    assert any("Paper version 26.3" in z for z in gelesen), "Der Kopf fehlt"
    assert any("Exception ticking entity" in z for z in gelesen), "Das Ende fehlt"
    assert any("ausgelassen" in z for z in gelesen), "Die Luecke wird nicht benannt"


# -------------------------------------------------------------------- Routen

def test_vorschau_route_weist_view_only_ab(client, tmp_path):
    """Der Abschnitt ist im Template ausgeblendet - ein ausgeblendeter Knopf ist aber
    keine Pruefung."""
    from app.db.session import SessionLocal
    from app.models.server import Server
    from app.models.server_permission import ServerPermission

    basis = tmp_path / "vo"
    (basis / "logs").mkdir(parents=True)
    (basis / "logs" / "latest.log").write_text("[10:00:00] start\n", encoding="utf-8")

    with SessionLocal() as db:
        server = Server(name="vo", slug="vo", server_type="paper", mc_version="26.3",
                        base_path=str(basis), port=25753, status="stopped",
                        start_mode="command", start_command="java -jar server.jar")
        db.add(server)
        db.commit()
        db.refresh(server)
        person = _nutzer(db, rolle="view_only", name="nur-schauen")
        db.add(ServerPermission(user_id=person.id, server_id=server.id, can_view=True))
        db.commit()
        server_id = server.id

    antwort = client.post("/login", data={"username": "nur-schauen",
                                          "password": "Egal12345!"},
                          follow_redirects=False)
    assert antwort.status_code == 303

    for pfad in (f"/api/servers/{server_id}/logs/ki-vorschau?file=server:latest.log",):
        assert client.get(pfad).status_code == 403
    assert client.post(f"/api/servers/{server_id}/logs/ki-analyse",
                       data={"file": "server:latest.log"}).status_code == 403
