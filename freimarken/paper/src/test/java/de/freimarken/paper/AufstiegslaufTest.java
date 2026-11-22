package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Berufsablage;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Rangablage;
import de.freimarken.kern.Rangstand;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Rangwerte;
import de.freimarken.kern.Rundgang;
import de.freimarken.kern.Rundgangsstation;
import de.freimarken.kern.Rundgangsverzeichnis;
import de.freimarken.kern.SpeicherRundgang;
import de.freimarken.kern.SpeicherBerufe;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.SpeicherRaenge;
import de.freimarken.kern.SpeicherSpielzeit;
import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Aufstieg zur Laufzeit - messen, urteilen, eintragen.
 *
 * <p>Alles ohne Server und ohne Datenbank: Die vier Messquellen stehen als
 * Speicherfassungen da, die Uhr ist eingespeist. Was hier geprueft wird, ist die
 * ENTSCHEIDUNG - wer steigt auf, wer nicht, und was wird dabei geschrieben.
 *
 * <p><b>Die Leiter dieser Proben ist eine EIGENE</b> und nicht die ausgelieferte. Das ist
 * kein Zufall: Eine Probe, die an der Vorgabe haengt, bricht bei jeder Aenderung der
 * Rangtafel ein halbes Dutzend Mal - und dann wird sie angepasst, statt gelesen.
 */
@DisplayName("Der Aufstiegslauf")
class AufstiegslaufTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-19T12:00:00Z");

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 19);

    private static final String WELT = "freimarken:marktstadt";

    /** Fuenf Stunden taetige Zeit in Sekunden - die Schwelle der Probenleiter. */
    private static final long FUENF_STUNDEN = 5L * 3_600L;

    private SpeicherRaenge raenge;
    private SpeicherSpielzeit zeit;
    private SpeicherLager lager;
    private SpeicherBerufe berufe;
    private SpeicherKasse kasse;

    @BeforeEach
    void frisch() {
        raenge = new SpeicherRaenge();
        zeit = new SpeicherSpielzeit();
        lager = new SpeicherLager();
        berufe = new SpeicherBerufe();
        kasse = new SpeicherKasse(0L);
    }

    /**
     * Die Leiter dieser Proben: gast -> siedler (5 Stunden taetig) -> buerger (+Parzelle).
     *
     * <p>Zwei Bedingungen auf verschiedenen Stufen und zwei verschiedene ARTEN - so
     * faellt auf, wenn eine Art nicht gemessen oder die falsche Stufe genommen wird.
     */
    private static Rangwerte leiter() {
        return new Rangwerte(true, List.of(
                new Rangwerte.Stufe("gast", Map.of(), Map.of("parzellen", 1), List.of()),
                new Rangwerte.Stufe("siedler",
                        Map.of(Bedingungsart.TAETIGE_STUNDEN, 5L),
                        Map.of("parzellen", 2), List.of("laden.mieten")),
                new Rangwerte.Stufe("buerger",
                        Map.of(Bedingungsart.TAETIGE_STUNDEN, 5L,
                                Bedingungsart.PARZELLEN, 1L),
                        Map.of("parzellen", 4), List.of("freistadt.gruenden"))));
    }

    @Test
    @DisplayName("Wer die Zeit hat, steigt von SELBST auf - das Abnahmekriterium")
    void steigtVonSelbst() {
        UUID anna = UUID.randomUUID();
        taetig(anna, FUENF_STUNDEN);

        var gehoben = lauf(leiter()).pruefe(anna);

        // DAS ABNAHMEKRITERIUM VON SCHRITT 15: "Reisender -> Siedler steigt von selbst".
        // Ohne Antrag, ohne Team, ohne Befehl - nur gemessene Zeit.
        assertTrue(gehoben.isPresent(), "er hat die Zeit, also steht ihm die Stufe zu");
        assertEquals("gast", gehoben.get().vorher());
        assertEquals("siedler", gehoben.get().nachher());
        assertEquals("siedler", raenge.lies(anna).rang(), "und es steht in der Ablage");
        assertEquals(MITTAGS, raenge.lies(anna).erreichtAm(),
                "mit dem Zeitpunkt der eingespeisten Uhr, nicht der Systemuhr");
    }

    @Test
    @DisplayName("Wem die Zeit fehlt, bleibt stehen - und es wird nichts geschrieben")
    void ohneZeitKeinAufstieg() {
        UUID anna = UUID.randomUUID();
        taetig(anna, FUENF_STUNDEN - 1L);

        assertTrue(lauf(leiter()).pruefe(anna).isEmpty(), "eine Sekunde fehlt");
        assertFalse(raenge.lies(anna).aufgestiegen(),
                "und es darf KEINE Zeile entstehen - sonst waere die Tabelle voll von"
                        + " Spielern, die auf der untersten Stufe stehen");
    }

    @Test
    @DisplayName("ANWESENHEIT zaehlt nicht als taetige Zeit")
    void anwesenheitIstKeineTaetigkeit() {
        UUID anna = UUID.randomUUID();
        // Zehn Stunden da, keine Sekunde taetig. Das ist die Entscheidung vom 18.11.2026:
        // "man soll nicht fuer die reine gespielte zeit belohnt werden". Haengte der
        // Aufstieg an der Anwesenheit, waere er mit einem beschwerten Tastenbrett zu
        // haben - und der Plan sagt selbst, dass die Messung das nicht ausschliesst.
        zeit.schreibeFort(List.of(
                Spielzeitablage.Zuschlag.nurAnwesend(anna, HEUTE, 10L * 3_600L)));

        assertTrue(lauf(leiter()).pruefe(anna).isEmpty(),
                "zehn Stunden Anwesenheit ohne Taetigkeit heben nichts");
    }

    @Test
    @DisplayName("Kein UEBERSPRINGEN: ohne Parzelle bleibt er Siedler")
    void keinUeberspringen() {
        UUID anna = UUID.randomUUID();
        taetig(anna, 100L * 3_600L);

        var gehoben = lauf(leiter()).pruefe(anna);

        // Die Zeit reicht fuer buerger, die Parzelle fehlt. Ohne die Regel "es gilt der
        // hoechste Rang, fuer den DIESE Stufe UND jede darunter erfuellt ist" waere er
        // jetzt Buerger - und die Parzellenbedingung waere eine Zeile ohne Wirkung.
        assertEquals("siedler", gehoben.orElseThrow().nachher());
    }

    @Test
    @DisplayName("Mit Parzelle geht es durch bis oben - in EINEM Schritt")
    void zweiStufenAufEinmal() {
        UUID anna = UUID.randomUUID();
        taetig(anna, 100L * 3_600L);
        parzelle(anna);

        var gehoben = lauf(leiter()).pruefe(anna);

        // Von gast direkt zu buerger. Ein Aufstieg je Taktschlag waere zwei Minuten fuer
        // zwei Stufen - und zwei Nachrichten, von denen die erste schon veraltet ist.
        assertEquals("gast", gehoben.orElseThrow().vorher());
        assertEquals("buerger", gehoben.get().nachher());
    }

    @Test
    @DisplayName("KEIN ABSTIEG: die verkaufte Parzelle nimmt den Rang nicht weg")
    void keinAbstieg() {
        UUID anna = UUID.randomUUID();
        taetig(anna, 100L * 3_600L);
        parzelle(anna);
        assertEquals("buerger", lauf(leiter()).pruefe(anna).orElseThrow().nachher());

        lager.entfernen(lager.finde(WELT, 0, 0).orElseThrow().id());

        // Er erfuellt die Buergerbedingung nicht mehr. Ohne diese Regel verlore er den
        // Rang, weil er seine Parzelle verkauft hat - und mit ihm die Rechte, die daran
        // haengen. Das ist der Fall, der niemandem erklaerbar waere.
        assertTrue(lauf(leiter()).pruefe(anna).isEmpty(), "es geschieht NICHTS");
        assertEquals("buerger", raenge.lies(anna).rang(), "und er bleibt Buerger");
    }

    @Test
    @DisplayName("Wer schon oben steht, wird GAR NICHT gemessen")
    void obenWirdNichtGemessen() {
        UUID anna = UUID.randomUUID();
        setzeRang(anna, "buerger");
        var zaehlende = new ZaehlendeZeit();

        assertTrue(lauf(leiter(), zaehlende).pruefe(anna).isEmpty());

        // DIE SPARENDE ZEILE, und sie spart nach einiger Zeit die ganze Mannschaft: Jede
        // Messung ausser dem Beruf ist eine Datenbankabfrage im Hauptfaden, und der Takt
        // schlaegt jede Minute je anwesendem Spieler.
        assertEquals(0, zaehlende.gelesen,
                "fuer wen es nichts zu holen gibt, wird nicht gemessen");
    }

    @Test
    @DisplayName("Eine Art, die in KEINER Stufe steht, wird nicht gefragt")
    void ungebrauchteArtWirdNichtGefragt() {
        UUID anna = UUID.randomUUID();
        taetig(anna, FUENF_STUNDEN);
        // Eine Kasse, die auf JEDE Frage wirft. Die Probenleiter nennt kein guthaben,
        // also darf sie nie gefragt werden - eine Abfrage, deren Ergebnis niemand liest,
        // ist eine Fehlerquelle ohne Nutzen.
        Kasse werfende = new Kasse() {
            @Override
            public long guthaben(UUID spieler) {
                throw new IllegalStateException("darf nicht gefragt werden");
            }

            @Override
            public boolean eroeffne(UUID spieler) {
                throw new IllegalStateException("darf nicht gefragt werden");
            }

            @Override
            public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
                throw new IllegalStateException("darf nicht gefragt werden");
            }

            @Override
            public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
                throw new IllegalStateException("darf nicht gefragt werden");
            }

            @Override
            public List<de.freimarken.kern.Buchung> verlauf(UUID spieler, int hoechstens) {
                throw new IllegalStateException("darf nicht gefragt werden");
            }

            @Override
            public long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund) {
                throw new IllegalStateException("darf nicht gefragt werden");
            }
        };

        var lauf = new Aufstiegslauf(stand(leiter()), raenge, zeit, lager, berufe,
                werfende, rundgangLeer(), WELT, uhr(), stumm());

        assertEquals("siedler", lauf.pruefe(anna).orElseThrow().nachher());
    }

    @Test
    @DisplayName("Ein GUTHABEN in der Leiter wird gemessen")
    void guthabenWirdGemessen() {
        UUID anna = UUID.randomUUID();
        var mitGeld = new Rangwerte(true, List.of(
                new Rangwerte.Stufe("gast", Map.of(), Map.of(), List.of()),
                new Rangwerte.Stufe("haendler", Map.of(Bedingungsart.GUTHABEN, 500L),
                        Map.of(), List.of())));
        // Die Zeit steht auf null - gemessen wird hier nur das Geld, und das ist der
        // Beweis, dass der Zweig fuer GUTHABEN wirklich die Kasse fragt.
        kasse.gutschreiben(anna, 500L, Buchungsgrund.BESTAND);

        assertEquals("haendler", lauf(mitGeld).pruefe(anna).orElseThrow().nachher());
    }

    @Test
    @DisplayName("Ein BERUF in der Leiter wird gemessen")
    void berufWirdGemessen() {
        UUID anna = UUID.randomUUID();
        var mitBeruf = new Rangwerte(true, List.of(
                new Rangwerte.Stufe("gast", Map.of(), Map.of(), List.of()),
                new Rangwerte.Stufe("zuenftig", Map.of(Bedingungsart.BERUF, 1L),
                        Map.of(), List.of())));

        assertTrue(lauf(mitBeruf).pruefe(anna).isEmpty(), "ohne Beruf nicht");
        berufe.waehle(new Berufsablage.Antrag(anna, "bergmann", MITTAGS),
                berufe.lies(anna));

        assertEquals("zuenftig", lauf(mitBeruf).pruefe(anna).orElseThrow().nachher());
    }

    @Test
    @DisplayName("Eine Insel in einer ANDEREN Welt zaehlt nicht als Parzelle")
    void fremdeWeltZaehltNicht() {
        UUID anna = UUID.randomUUID();
        taetig(anna, 100L * 3_600L);
        // Dieselbe Bauart, nur in einer anderen Welt. Der Weltschluessel IST der Riegel -
        // eine Zaehlung ohne ihn machte aus einer Meerinsel eine Parzelle, und ein
        // Spieler stiege auf, ohne in der Marktstadt je etwas zu besitzen.
        lager.anlegen("freimarken:hafenwerder", 0, 0, anna, 0L);

        assertEquals("siedler", lauf(leiter()).pruefe(anna).orElseThrow().nachher(),
                "die Insel hebt ihn NICHT zum Buerger");
    }

    @Test
    @DisplayName("Ist die Leiter aus, geschieht nichts")
    void leiterAus() {
        UUID anna = UUID.randomUUID();
        taetig(anna, 100L * 3_600L);
        parzelle(anna);

        assertTrue(lauf(leiter().mitAn(false)).pruefe(anna).isEmpty());
        assertFalse(raenge.lies(anna).aufgestiegen());
    }

    // --- Die Nachstufung beim Start -------------------------------------------

    @Test
    @DisplayName("Die Nachstufung hebt JEDEN, dem mehr zusteht")
    void nachstufungHebtAlle() {
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        UUID claus = UUID.randomUUID();
        taetig(anna, FUENF_STUNDEN);
        taetig(bert, 1L);
        taetig(claus, FUENF_STUNDEN);

        var ergebnis = lauf(leiter()).nachstufung();

        // DAS IST DER LAUF, FUER DEN ES alleStaende GIBT: Ohne ihn gaebe es keine
        // Spielerliste - jede andere Methode der Spielzeitablage nimmt eine UUID.
        assertEquals(3, ergebnis.geprueft(), "alle drei wurden angesehen");
        assertEquals(2, ergebnis.aufstiege().size(), ergebnis.aufstiege().toString());
        assertEquals("siedler", raenge.lies(anna).rang());
        assertEquals("siedler", raenge.lies(claus).rang());
        assertFalse(raenge.lies(bert).aufgestiegen(), "bert hat eine Sekunde");
    }

    @Test
    @DisplayName("Ein zweiter Lauf ist FOLGENLOS - darum braucht es keinen Merker")
    void zweiterLaufIstFolgenlos() {
        UUID anna = UUID.randomUUID();
        taetig(anna, FUENF_STUNDEN);
        var lauf = lauf(leiter());
        assertEquals(1, lauf.nachstufung().aufstiege().size());
        Instant zuerst = raenge.lies(anna).erreichtAm();

        var zweiter = lauf.nachstufung();

        // DIE BEGRUENDUNG DAFUER, DASS DER LAUF BEI JEDEM START LAEUFT. Ein Merker "schon
        // erledigt" waere Aufwand ohne Gegenwert - und es gibt in diesem Projekt keinen
        // fachlichen Merker, der einen Neustart ueberlebt.
        assertEquals(1, zweiter.geprueft(), "angesehen wird er wieder");
        assertEquals(List.of(), zweiter.aufstiege(), "gehoben wird niemand");
        assertEquals(zuerst, raenge.lies(anna).erreichtAm(),
                "und der Zeitpunkt des Aufstiegs verschiebt sich NICHT");
    }

    @Test
    @DisplayName("Wer nie gespielt hat, kommt in der Nachstufung nicht vor")
    void nachstufungOhneSpieler() {
        var ergebnis = lauf(leiter()).nachstufung();

        // Der erste Start eines neuen Servers. Die Zahl 0 ist die Antwort, nicht ein
        // Fehler - und der Startbericht muss sie von "gar nicht gelaufen" unterscheiden
        // koennen, darum traegt das Ergebnis BEIDE Zahlen.
        assertEquals(0, ergebnis.geprueft());
        assertEquals(List.of(), ergebnis.aufstiege());
    }

    @Test
    @DisplayName("Ein Fehler bei EINEM Spieler beendet den Lauf nicht")
    void fehlerBeiEinemGehtWeiter() {
        UUID anna = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bert = UUID.fromString("00000000-0000-0000-0000-000000000002");
        taetig(anna, FUENF_STUNDEN);
        taetig(bert, FUENF_STUNDEN);
        // Eine Rangablage, die bei anna wirft. Die Reihenfolge von alleStaende ist
        // zugesagt (nach der Kennung), anna kommt also ZUERST - sonst waere diese Probe
        // auch mit einem abbrechenden Lauf gruen.
        Rangablage launisch = new Rangablage() {
            @Override
            public Rangstand lies(UUID spieler) {
                if (spieler.equals(anna)) {
                    throw new IllegalStateException("Rang nicht lesbar");
                }
                return raenge.lies(spieler);
            }

            @Override
            public boolean setze(Antrag antrag, Rangstand erwartet) {
                return raenge.setze(antrag, erwartet);
            }
        };
        var lauf = new Aufstiegslauf(stand(leiter()), launisch, zeit, lager, berufe,
                kasse, rundgangLeer(), WELT, uhr(), stumm());

        var ergebnis = lauf.nachstufung();

        // Ein Startlauf, der am ersten von zwanzig stirbt, liesse neunzehn ungestuft -
        // und niemand saehe, dass es neunzehn waren.
        assertEquals(2, ergebnis.geprueft(), "beide wurden angesehen");
        assertEquals(1, ergebnis.aufstiege().size(), "und bert wurde gehoben");
        assertEquals(bert, ergebnis.aufstiege().get(0).spieler());
    }

    @Test
    @DisplayName("Eine unlesbare Spielzeit beendet den Start nicht")
    void unlesbareSpielzeitBrichtNichtAb() {
        Spielzeitablage tot = new Spielzeitablage() {
            @Override
            public void schreibeFort(List<Zuschlag> zuschlaege) {
                throw new IllegalStateException("Spielzeit nicht fortschreibbar");
            }

            @Override
            public de.freimarken.kern.Spielzeitstand lies(UUID spieler,
                                                          LocalDate heute) {
                return de.freimarken.kern.Spielzeitstand.leer(spieler, heute);
            }

            @Override
            public List<de.freimarken.kern.Spielzeitstand> alleStaende(
                    LocalDate heute) {
                throw new IllegalStateException("Spielzeit nicht lesbar: alle Staende");
            }
        };
        var lauf = new Aufstiegslauf(stand(leiter()), raenge, tot, lager, berufe, kasse,
                rundgangLeer(), WELT, uhr(), stumm());

        // Der Start darf daran nicht scheitern: Die Nachstufung ist eine Bequemlichkeit,
        // der Takt holt sie fuer jeden Anwesenden nach. Ein Wurf hier liesse den ganzen
        // Server nicht hochkommen.
        var ergebnis = lauf.nachstufung();

        assertEquals(0, ergebnis.geprueft());
        assertEquals(List.of(), ergebnis.aufstiege());
    }

    @Test
    @DisplayName("Ohne eine der Angaben gibt es keinen Lauf")
    void riegel() {
        var werte = stand(leiter());
        assertThrows(IllegalArgumentException.class, () -> new Aufstiegslauf(
                null, raenge, zeit, lager, berufe, kasse, rundgangLeer(), WELT, uhr(),
                stumm()));
        assertThrows(IllegalArgumentException.class, () -> new Aufstiegslauf(
                werte, null, zeit, lager, berufe, kasse, rundgangLeer(), WELT, uhr(),
                stumm()));
        assertThrows(IllegalArgumentException.class, () -> new Aufstiegslauf(
                werte, raenge, null, lager, berufe, kasse, rundgangLeer(), WELT, uhr(),
                stumm()));
        assertThrows(IllegalArgumentException.class, () -> new Aufstiegslauf(
                werte, raenge, zeit, null, berufe, kasse, rundgangLeer(), WELT, uhr(),
                stumm()));
        assertThrows(IllegalArgumentException.class, () -> new Aufstiegslauf(
                werte, raenge, zeit, lager, null, kasse, rundgangLeer(), WELT, uhr(),
                stumm()));
        assertThrows(IllegalArgumentException.class, () -> new Aufstiegslauf(
                werte, raenge, zeit, lager, berufe, null, rundgangLeer(), WELT, uhr(),
                stumm()));
        // Ohne Rundgang koennte der Lauf die Bedingung RUNDGANG nie messen - und der
        // erschoepfende Schalter in messe() verlangt eine Messquelle fuer JEDE Art.
        assertThrows(IllegalArgumentException.class, () -> new Aufstiegslauf(
                werte, raenge, zeit, lager, berufe, kasse, null, WELT, uhr(), stumm()));
        assertThrows(IllegalArgumentException.class, () -> new Aufstiegslauf(
                werte, raenge, zeit, lager, berufe, kasse, rundgangLeer(), null, uhr(),
                stumm()));
        assertThrows(IllegalArgumentException.class, () -> new Aufstiegslauf(
                werte, raenge, zeit, lager, berufe, kasse, rundgangLeer(), "  ", uhr(),
                stumm()));
        assertThrows(IllegalArgumentException.class, () -> new Aufstiegslauf(
                werte, raenge, zeit, lager, berufe, kasse, rundgangLeer(), WELT, null,
                stumm()));
        assertThrows(IllegalArgumentException.class, () -> new Aufstiegslauf(
                werte, raenge, zeit, lager, berufe, kasse, rundgangLeer(), WELT, uhr(),
                null));
    }

    @Test
    @DisplayName("Die Bedingung RUNDGANG haelt auf, wer ihn nicht abgeschlossen hat")
    void rundgangHaeltAuf() {
        // Die sechste Bedingungsart [21.11.2026]. Geprueft wird in BEIDE Richtungen -
        // ohne die zweite Haelfte waere auch eine Messung gruen, die immer 0 gibt (und
        // damit jeden Aufstieg verhindert), oder eine, die immer 1 gibt.
        var leiter = new Rangwerte(true, List.of(
                new Rangwerte.Stufe("reisender", Map.of(), Map.of("parzellen", 1),
                        List.of()),
                new Rangwerte.Stufe("siedler", Map.of(Bedingungsart.RUNDGANG, 1L),
                        Map.of("parzellen", 2), List.of())));
        UUID anna = UUID.randomUUID();
        var ablage = new SpeicherRundgang();
        var station = new Rundgangsstation("buergeramt",
                new Quader(10, -60, 10, 12, -58, 12));
        var verzeichnis = Rundgangsverzeichnis.ausStationen(List.of(station));
        var rundgang = new Rundgang(ablage, verzeichnis, uhr());

        var lauf = new Aufstiegslauf(stand(leiter), raenge, zeit, lager, berufe, kasse,
                rundgang, WELT, uhr(), stumm());

        assertTrue(lauf.pruefe(anna).isEmpty(),
                "ohne abgeschlossenen Rundgang steigt er nicht auf");

        rundgang.betreten(anna, "buergeramt");

        assertEquals("siedler", lauf.pruefe(anna).orElseThrow().nachher(),
                "mit abgeschlossenem Rundgang steigt er auf");
    }

    @Test
    @DisplayName("Ohne Stationen ist die Bedingung RUNDGANG nicht erfuellbar")
    void ohneStationenNichtErfuellbar() {
        // DER AUSGELIEFERTE ZUSTAND, und darum steht die Bedingung ab Werk nicht in der
        // Leiter. Waere sie mit null Stationen erfuellt, verschenkte ein Betreiber die
        // Stufe, ohne eine Zeile zu schreiben.
        var leiter = new Rangwerte(true, List.of(
                new Rangwerte.Stufe("reisender", Map.of(), Map.of("parzellen", 1),
                        List.of()),
                new Rangwerte.Stufe("siedler", Map.of(Bedingungsart.RUNDGANG, 1L),
                        Map.of("parzellen", 2), List.of())));

        UUID anna = UUID.randomUUID();
        var lauf = new Aufstiegslauf(stand(leiter), raenge, zeit, lager, berufe, kasse,
                rundgangLeer(), WELT, uhr(), stumm());

        assertTrue(lauf.pruefe(anna).isEmpty());
    }

    // --- Hilfen ---------------------------------------------------------------

    /**
     * Ein Rundgang OHNE Stationen - der ausgelieferte Zustand.
     *
     * <p>Die meisten Proben hier messen etwas anderes; fuer sie ist der Rundgang nur ein
     * Mitspieler, der nichts verlangt. Dass er ohne Stationen NICHT abgeschlossen ist,
     * prueft {@code ohneStationenNichtErfuellbar} ausdruecklich.
     */
    private Rundgang rundgangLeer() {
        return new Rundgang(new SpeicherRundgang(),
                Rundgangsverzeichnis.ohneStationen(), uhr());
    }

    private Aufstiegslauf lauf(Rangwerte werte) {
        return new Aufstiegslauf(stand(werte), raenge, zeit, lager, berufe, kasse, rundgangLeer(), WELT,
                uhr(), stumm());
    }

    private Aufstiegslauf lauf(Rangwerte werte, Spielzeitablage andere) {
        return new Aufstiegslauf(stand(werte), raenge, andere, lager, berufe, kasse, rundgangLeer(), WELT,
                uhr(), stumm());
    }

    private static Wirtschaftsstand stand(Rangwerte werte) {
        return new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitRaengen(werte));
    }

    /** Eine feste Uhr - ein Aufstieg traegt einen nachpruefbaren Zeitpunkt. */
    private static Clock uhr() {
        return Clock.fixed(MITTAGS, ZoneOffset.UTC);
    }

    private static Logger stumm() {
        Logger eigener = Logger.getLogger("AufstiegProbe-" + UUID.randomUUID());
        eigener.setUseParentHandlers(false);
        return eigener;
    }

    private void taetig(UUID wer, long sekunden) {
        zeit.schreibeFort(List.of(
                Spielzeitablage.Zuschlag.ganzTaetig(wer, HEUTE, sekunden)));
    }

    private void parzelle(UUID wer) {
        lager.anlegen(WELT, 0, 0, wer, 0L);
    }

    private void setzeRang(UUID wer, String rang) {
        assertTrue(raenge.setze(new Rangablage.Antrag(wer, rang, MITTAGS),
                Rangstand.leer(wer)), "die Vorbereitung muss durchgehen");
    }

    /**
      * Eine Spielzeitablage, die ZAEHLT, wie oft sie gelesen wurde.
      *
      * <p>Durchgereicht und nicht geerbt: Die Speicherfassungen dieses Projekts sind
      * {@code final}, und das ist richtig - eine ueberschriebene Methode in einer
      * Umsetzung waere eine zweite Wahrheit, die der geteilte Vertrag nicht sieht.
      */
    private static final class ZaehlendeZeit implements Spielzeitablage {

        private final SpeicherSpielzeit echt = new SpeicherSpielzeit();

        private int gelesen;

        @Override
        public void schreibeFort(List<Zuschlag> zuschlaege) {
            echt.schreibeFort(zuschlaege);
        }

        @Override
        public de.freimarken.kern.Spielzeitstand lies(UUID spieler, LocalDate heute) {
            gelesen++;
            return echt.lies(spieler, heute);
        }

        @Override
        public List<de.freimarken.kern.Spielzeitstand> alleStaende(LocalDate heute) {
            gelesen++;
            return echt.alleStaende(heute);
        }
    }
}
