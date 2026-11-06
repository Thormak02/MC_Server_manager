package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Berufswerte;
import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Lohntafel;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.Taetigkeit;
import de.freimarken.kern.Verdienstwerte;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Sammler des Verdienstes - Pfennige, Minderung, Deckel, Buchen.
 *
 * <p>Ohne Server und ohne Datenbank: Diese Klasse kennt kein Bukkit, und die Kasse ist
 * hier die {@code SpeicherKasse}. Die Uhr wird gestellt - {@link SpielzeitbuchTest.StellUhr}
 * ist dieselbe wie bei der Spielzeit, damit die Entscheidung "fest UTC, kein withZone"
 * nicht an zwei Stellen gepflegt werden muss.
 *
 * <p><b>Die drei tragenden Proben</b>, weil sie Geld betreffen:
 *
 * <ul>
 *   <li>{@link #nachEinemFehlschlagWirdNichtWiederholt()} - ein zweiter Versuch buchte
 *       doppelt, und die Zusage "Summe aller Buchungen gleich Kontostand" waere mit Geld
 *       gebrochen, das niemand erarbeitet hat.</li>
 *   <li>{@link #amDeckelVerfaelltDerRest()} - wandert er in den naechsten Tag, ist der
 *       Deckel nur eine Verzoegerung.</li>
 *   <li>{@link #dieTagessummeKommtAusDemBuch()} - der Deckel haengt am Buchungsbuch und
 *       nicht an einem Zaehler im Arbeitsspeicher, der einen Neustart nicht ueberlebt.</li>
 * </ul>
 */
@DisplayName("Der Sammler des Verdienstes")
class VerdienstbuchTest {

    private static final UUID ANNA = UUID.fromString("0000a11a-0000-0000-0000-000000000001");
    private static final UUID BERT = UUID.fromString("0000be27-0000-0000-0000-000000000002");

    /** Mittags, damit kein Tageswechsel in eine Probe hineinredet. */
    private static final Instant START = Instant.parse("2026-11-06T12:00:00Z");

    private static final Set<String> BERGMANN = Set.of("bergmann");

    /** Gewoehnlicher Stein: 40 Pfennige, also 0,40 Mark - der Fall mit Rest. */
    static final String STEIN = "stone";

    /** Ein teurer Block: 1000 Pfennige - an ihm ist jede Minderungsstufe zu sehen. */
    static final String ERZ = "diamond_ore";

    /** Eine Erntepflanze MIT Alter - reif erst am Hoechstalter. */
    static final String WEIZEN = "wheat";

    /** Eine Hoehlenranke - bei ihr entscheiden die BEEREN und nicht das Alter. */
    static final String RANKE = "cave_vines";

    /** Eine Wesensart zum Zuechten. */
    static final String KALB = "cow";

    /** Eine Wesensart zum Toeten. */
    static final String MONSTER = "zombie";

    /** Ein Fang. */
    static final String FISCH = "cod";

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private SpielzeitbuchTest.StellUhr uhr;
    private SpeicherKasse kasse;
    private Logger protokoll;

    @BeforeEach
    void vorher() {
        uhr = new SpielzeitbuchTest.StellUhr(START);
        kasse = new SpeicherKasse(0L, uhr);
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
    }

    private Logger eigenesProtokoll() {
        // Eigener Name je Probe - dieselbe Begruendung wie bei der Spielzeit: Logger leben
        // im LogManager weiter, und ein geteilter Name liesse zwei Proben durcheinander.
        Logger eigener = Logger.getLogger("VerdienstbuchProbe-" + UUID.randomUUID());
        eigener.setUseParentHandlers(false);
        eigener.addHandler(new Handler() {
            @Override
            public void publish(LogRecord satz) {
                protokollzeilen.add(satz);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return eigener;
    }

    /**
     * Werte mit kleinen Stufen - damit die Minderung ohne zweihundert Bloecke zu sehen ist.
     *
     * <p>voll-bis 2 und Stufe 2 heisst: Die ersten zwei zahlen voll, dann je zwei eine
     * Stufe tiefer. Die Zahlen des Betriebs sind 200/200; geprueft wird die RECHNUNG, und
     * die ist dieselbe.
     *
     * <p>Nicht {@code private}, damit {@link VerdienstwacheTest} dieselben Saetze nimmt:
     * Zwei Lohntafeln fuer dieselbe Sache waeren zwei Stellen, an denen eine Zahl zu
     * pflegen ist - und die Probe der Wache prueefte dann eine andere Wirtschaft als die
     * des Sammlers.
     */
    static Verdienstwerte werte(long tagesdeckel) {
        return Verdienstwerte.VORGABE.mitMinderung(tagesdeckel, 2, 2, 50, 10)
                .mitLoehnen(Lohntafel.ausLoehnen(List.of(
                        new Lohntafel.Lohn("bergmann", Taetigkeit.ABBAUEN, STEIN, 40L),
                        new Lohntafel.Lohn("bergmann", Taetigkeit.ABBAUEN, ERZ, 1000L),
                        new Lohntafel.Lohn("bergmann", Taetigkeit.SETZEN, STEIN, 10L),
                        new Lohntafel.Lohn("steinmetz", Taetigkeit.ABBAUEN, ERZ, 300L),
                        // Je neue Taetigkeit eine Zeile - sonst hat die Probe der Wache
                        // nichts zu zahlen, und ihr Zweig liefe am Lohn vorbei ins
                        // Leere.
                        new Lohntafel.Lohn("bauer", Taetigkeit.ERNTEN, WEIZEN, 150L),
                        new Lohntafel.Lohn("bauer", Taetigkeit.ERNTEN, RANKE, 180L),
                        new Lohntafel.Lohn("bauer", Taetigkeit.ZUECHTEN, KALB, 3500L),
                        new Lohntafel.Lohn("fischer", Taetigkeit.FISCHEN, FISCH, 800L),
                        new Lohntafel.Lohn("jaeger", Taetigkeit.TOETEN, MONSTER,
                                2000L))));
    }

    /**
     * Ein Stand mit UTC als Zone - die Tagesgrenze liegt dann um Mitternacht UTC.
     *
     * <p>Nicht Europe/Berlin wie im Betrieb: Eine Probe, die ihre eigene Zeitzone
     * umrechnen muss, prueft am Ende die Umrechnung und nicht den Deckel.
     */
    static Wirtschaftsstand stand(Verdienstwerte verdienst) {
        return new Wirtschaftsstand(new Wirtschaftswerte(Preisstaffel.VORGABE,
                Wirtschaftswerte.VORGABE_STARTGUTHABEN,
                Wirtschaftswerte.VORGABE_HOECHSTZAHL,
                Wirtschaftswerte.VORGABE_GRUPPEN_FELDER,
                Wirtschaftswerte.VORGABE_RANG_WERBUNG,
                new Zeitwerte(ZoneOffset.UTC, Duration.ofSeconds(60L),
                        Duration.ofMinutes(10L)),
                Berufswerte.VORGABE, verdienst));
    }

    private Verdienstbuch buch(long tagesdeckel) {
        return new Verdienstbuch(kasse, stand(werte(tagesdeckel)), uhr, protokoll);
    }

    private Verdienstbuch buch() {
        return buch(Verdienstwerte.VORGABE_TAGESDECKEL);
    }

    private Verdienstbuch buch(Kasse andere) {
        return new Verdienstbuch(andere,
                stand(werte(Verdienstwerte.VORGABE_TAGESDECKEL)), uhr, protokoll);
    }

    /** Wie viel aus Arbeit auf dem Konto steht - nur die Verdienstbuchungen. */
    private long verdient(UUID wer) {
        long summe = 0L;
        for (Buchung zeile : kasse.verlauf(wer, 100)) {
            if (zeile.grund() == Buchungsgrund.BERUF_VERDIENST) {
                summe += zeile.betrag();
            }
        }
        return summe;
    }

    private boolean protokollNennt(String stueck) {
        return protokollzeilen.stream().anyMatch(satz ->
                satz.getMessage() != null && satz.getMessage().contains(stueck));
    }

    @Test
    @DisplayName("Der Pfennigrest bleibt stehen, bis eine ganze Mark zusammen ist")
    void pfennigrestBleibtStehen() {
        // Das ist der ganze Grund fuer die Pfennige: Ohne Rest muesste gewoehnlicher
        // Stein 0 zahlen, und zwei von drei Bloecken brachten gar nichts.
        var buch = buch();

        assertEquals(40L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, STEIN));
        assertEquals(40L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, STEIN));
        assertEquals(80L, buch.rest(ANNA));
        assertEquals(0L, buch.wartende(), "achtzig Pfennige sind keine Mark");
        assertEquals(0L, buch.rechneAb(10));
        assertEquals(80L, buch.rest(ANNA), "nicht gebucht heisst nicht verloren");

        // Der dritte Block ist der erste gemindert: 40 mal die Haelfte sind 20.
        assertEquals(20L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, STEIN));
        assertEquals(1L, buch.wartende());
        assertEquals(1L, buch.rechneAb(10));
        assertEquals(1L, verdient(ANNA));
        assertEquals(0L, buch.rest(ANNA));
    }

    @Test
    @DisplayName("Die Minderung geht in Stufen und bleibt am Boden stehen")
    void minderungInStufenMitBoden() {
        // 1000 Pfennige, voll-bis 2, Stufe 2, Faktor 50, Boden 10: zwei voll, zwei halb,
        // zwei ein Viertel, zwei ein Achtel - und dann der Boden. Die Zahl 120 statt 125
        // ist der ganzzahlige Anteil (12 Prozent), nicht ein Fehler: Gerechnet wird in
        // Prozent und nicht in Bruechen, damit der Betreiber die Stufen eintippen kann.
        var buch = buch();
        List<Long> gesehen = new ArrayList<>();
        for (int mal = 0; mal < 12; mal++) {
            gesehen.add(buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ));
        }

        assertEquals(List.of(1000L, 1000L, 500L, 500L, 250L, 250L, 120L, 120L,
                100L, 100L, 100L, 100L), gesehen);
    }

    @Test
    @DisplayName("Jede Blockart hat ihren eigenen Zaehler")
    void jedeArtZaehltFuerSich() {
        // Sonst senkte das Abbauen von Erde den Lohn fuers Erz - und ein Spieler, der
        // einen Tunnel graebt, verdiente am Erz darin nichts mehr.
        var buch = buch();
        for (int mal = 0; mal < 6; mal++) {
            buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, STEIN);
        }

        assertEquals(1000L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ),
                "der erste Erzblock zahlt voll");
        assertEquals(2, buch.gezaehlteArten(ANNA).size());
    }

    @Test
    @DisplayName("Nach einer Stunde zahlt dieselbe Art wieder voll - vorher nicht")
    void fensterSpringtNachEinerStunde() {
        var buch = buch();
        for (int mal = 0; mal < 6; mal++) {
            buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        }

        uhr.weiter(Verdienstbuch.FENSTER.minusSeconds(1L));
        assertEquals(120L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ),
                "eine Sekunde vor der Stunde gilt das alte Fenster noch");

        uhr.weiter(Duration.ofSeconds(2L));
        assertEquals(1000L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ),
                "nach der Stunde beginnt die Minderung neu");
    }

    @Test
    @DisplayName("Eine zurueckgestellte Uhr beginnt das Fenster NICHT neu")
    void zurueckgestellteUhrBeginntNichtNeu() {
        // Die sichere Richtung: Ein Neubeginn waere wieder der volle Satz, und eine
        // Sommerzeitumstellung oder ein Zeitabgleich des Rechners waere damit eine
        // Stunde voller Saetze - geschenkt, ohne dass jemand es merkt.
        var buch = buch();
        for (int mal = 0; mal < 6; mal++) {
            buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        }

        uhr.weiter(Duration.ofHours(-2L));

        assertEquals(120L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ));
    }

    @Test
    @DisplayName("Ohne Lohnsatz entsteht kein Zaehler im Arbeitsspeicher")
    void ohneSatzKeinZaehler() {
        // Sonst bekaeme jede Blockart, die ein Spieler anfasst, einen Eintrag: Erde, Gras,
        // Laub, Sand - und keiner davon zahlt je etwas. Auf einem Server mit dreissig
        // Spielern waeren das Tausende Eintraege, die nie wieder angesehen werden.
        var buch = buch();

        assertEquals(0L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, "dirt"));
        assertEquals(0L, buch.verdiene(ANNA, Set.of("bauer"), Taetigkeit.ABBAUEN, STEIN),
                "der falsche Beruf zahlt an diesem Block nichts");
        assertEquals(List.of(), buch.gezaehlteArten(ANNA));
        assertEquals(0L, buch.rest(ANNA));
    }

    @Test
    @DisplayName("Der hoechste Satz unter den gehaltenen Berufen gilt - nicht die Summe")
    void hoechsterSatzGilt() {
        // Zwei Berufe an einem Block duerfen nicht addieren: Sonst waere die
        // Spezialisierung eine Verdopplung, und wer beides haelt, verdient doppelt am
        // selben Stein. Der Bergmann bekommt 1000, der Steinmetz 300 - addiert waeren es
        // 1300, und genau diese Zahl darf hier nicht herauskommen.
        var buch = buch();

        assertEquals(1000L, buch.verdiene(ANNA, Set.of("bergmann", "steinmetz"),
                Taetigkeit.ABBAUEN, ERZ));
        assertEquals(300L, buch.verdiene(BERT, Set.of("steinmetz"),
                Taetigkeit.ABBAUEN, ERZ), "allein zahlt der kleinere Satz");
    }

    @Test
    @DisplayName("Abbauen und Setzen sind zwei Taetigkeiten mit eigenen Saetzen")
    void abbauenUndSetzenSindGetrennt() {
        // Der gesetzte Stein zahlt 10 und der abgebaute 40: Sonst waere ein Block, der
        // beides kann, eine Schleife - setzen, abbauen, setzen - und sie zahlte zweimal
        // denselben Satz. Dass ein gesetzter Block beim Abbauen gar nichts mehr bringt,
        // besorgt die Marke; hier geht es nur um die zwei Saetze.
        var buch = buch();

        assertEquals(10L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.SETZEN, STEIN));
        assertEquals(40L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, STEIN));
        assertEquals(0L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.SETZEN, ERZ),
                "fuers Setzen von Erz steht kein Satz in der Liste");
    }

    @Test
    @DisplayName("Der Minderungszaehler haengt an TAETIGKEIT UND Name")
    void zaehlerHaengtAnTaetigkeitUndName() {
        // Nicht am Namen allein, und das ist eine Entscheidung mit Grund: Sonst
        // entschiede ein Namenszufall darueber, was zusammen zaehlt. "fischen cod" und
        // "abbauen cod" teilten ein Fenster, "zuechten cow" und "toeten cow" ebenso - und
        // bei "ernten wheat" gegen "abbauen wheat" fiele derselbe Zufall zugunsten des
        // Spielers aus.
        //
        // Dass eine Schleife aus Setzen und Abbauen damit zwei Fenster hat, ist kein Loch:
        // Ein gesetzter Block traegt eine Marke und zahlt beim Abbauen gar nichts.
        var buch = buch();
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.SETZEN, STEIN);
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.SETZEN, STEIN);

        assertEquals(40L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, STEIN),
                "das Abbauen hat sein eigenes Fenster - voller Satz");
        assertEquals(2, buch.gezaehlteArten(ANNA).size());
        assertTrue(buch.gezaehlteArten(ANNA).contains("setzen/" + STEIN),
                buch.gezaehlteArten(ANNA).toString());
        assertTrue(buch.gezaehlteArten(ANNA).contains("abbauen/" + STEIN),
                buch.gezaehlteArten(ANNA).toString());
    }

    @Test
    @DisplayName("Die harte Stundenmenge haelt eine Farm an - ueber ALLE Namen")
    void stundenmengeHaeltAn() {
        // Die einzige der drei Bremsen, die wirklich anhaelt. Die Stufen DAEMPFEN nur:
        // Ihre Pauschale vervielfacht sich mit jeder weiteren Art, die eine Farm
        // ausspuckt - fuenf Monsterarten waeren fuenf Pauschalen. Darum zaehlt diese
        // Grenze ueber alle Namen EINER Taetigkeit zusammen.
        var buch = new Verdienstbuch(kasse, stand(werte(
                Verdienstwerte.VORGABE_TAGESDECKEL)
                .mitStundenmenge(Taetigkeit.ABBAUEN, 3)), uhr, protokoll);

        // Drei zahlen - und zwar auf ZWEI verschiedene Namen verteilt.
        assertTrue(buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ) > 0L);
        assertTrue(buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, STEIN) > 0L);
        assertTrue(buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ) > 0L);

        assertEquals(0L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, STEIN),
                "der vierte bringt nichts, obwohl es erst der zweite Stein ist");
        assertEquals(Taetigkeit.ABBAUEN, buch.anDerGrenze(ANNA),
                "und der Spieler erfaehrt, WELCHE Arbeit zu ist");

        // Eine ANDERE Taetigkeit zahlt weiter - die Grenze gilt je Taetigkeit.
        assertTrue(buch.verdiene(ANNA, BERGMANN, Taetigkeit.SETZEN, STEIN) > 0L);
        assertEquals(null, buch.anDerGrenze(ANNA),
                "nach einer zahlenden Tat steht er nicht mehr an der Grenze");
    }

    @Test
    @DisplayName("Nach einer Stunde zahlt die Taetigkeit wieder - und beginnt an der Grenze")
    void stundenmengeSpringtNachEinerStunde() {
        var buch = new Verdienstbuch(kasse, stand(werte(
                Verdienstwerte.VORGABE_TAGESDECKEL)
                .mitStundenmenge(Taetigkeit.ABBAUEN, 2)), uhr, protokoll);
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        assertEquals(0L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ));

        uhr.weiter(Verdienstbuch.FENSTER);

        assertTrue(buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ) > 0L,
                "die neue Stunde zahlt wieder");
    }

    @Test
    @DisplayName("Die Grenze gilt in JEDER neuen Stunde wieder - und zwar ab eins")
    void grenzeGiltJedeStundeNeu() {
        // Wer eine Stunde lang gegen die Grenze haut, hat in der naechsten wieder genau
        // sein Soll frei - nicht weniger und nicht mehr. Fuenfzig Schlaege ins Leere
        // aendern daran nichts.
        //
        // Der frueheren Fassung dieser Probe lag eine falsche Begruendung bei ("sonst
        // begaenne die naechste Stunde bei einer gewachsenen Zahl"). Das ist nicht so:
        // imFenster setzt den Zaehler beim Fensterwechsel ohnehin auf 0. Gezeigt hat es
        // eine Einspritzung, die den Zaehler an der Grenze weiterzaehlen liess und von
        // keiner Probe bemerkt wurde.
        var buch = new Verdienstbuch(kasse, stand(werte(
                Verdienstwerte.VORGABE_TAGESDECKEL)
                .mitStundenmenge(Taetigkeit.ABBAUEN, 1)), uhr, protokoll);
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        for (int mal = 0; mal < 50; mal++) {
            assertEquals(0L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ));
        }

        uhr.weiter(Verdienstbuch.FENSTER);

        assertTrue(buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ) > 0L);
        assertEquals(0L, buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ),
                "und dann ist wieder zu - genau nach einem");
    }

    @Test
    @DisplayName("Gebucht wird je Spieler, und nur so viele, wie der Aufruf zulaesst")
    void nurSoVieleWieErlaubt() {
        // Fuenfzig Buchungen in einem Tick waeren dreihundert Netzrunden hintereinander -
        // genau die Massenabmeldung, gegen die das Spielzeitbuch seine Sperre haelt.
        var buch = buch();
        List<UUID> fuenf = new ArrayList<>();
        for (int wer = 0; wer < 5; wer++) {
            UUID einer = UUID.randomUUID();
            fuenf.add(einer);
            buch.verdiene(einer, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        }
        assertEquals(5, buch.wartende());

        assertEquals(30L, buch.rechneAb(3), "drei Spieler mit je zehn Mark");
        assertEquals(2, buch.wartende(), "was dieser Schlag nicht bucht, steht noch da");

        assertEquals(20L, buch.rechneAb(3));
        assertEquals(0, buch.wartende());
        long summe = 0L;
        for (UUID einer : fuenf) {
            summe += verdient(einer);
        }
        assertEquals(50L, summe);
    }

    @Test
    @DisplayName("Ein Schlag ohne Spieler ist kein Schlag")
    void mindestensEinSpielerJeSchlag() {
        assertThrows(IllegalArgumentException.class, () -> buch().rechneAb(0));
        assertThrows(IllegalArgumentException.class, () -> buch().rechneAb(-1));
    }

    @Test
    @DisplayName("Am Deckel VERFAELLT der Rest - er wandert nicht in den naechsten Tag")
    void amDeckelVerfaelltDerRest() {
        // Sonst waere der Deckel nur eine Verzoegerung: Wer einen Tag durchfarmt, bekaeme
        // am naechsten Morgen alles - und die Grenze, die den Geldzufluss bremsen soll,
        // bremste nur die Auszahlung.
        var buch = buch(3L);
        for (int mal = 0; mal < 2; mal++) {
            buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        }
        assertEquals(2000L, buch.rest(ANNA), "zwanzig Mark wollen gebucht werden");

        assertEquals(3L, buch.rechneAb(10), "der Deckel laesst drei zu");
        assertEquals(3L, verdient(ANNA));
        assertTrue(buch.amDeckel(ANNA));
        assertEquals(0L, buch.rest(ANNA), "die siebzehn Mark darueber sind weg");

        // Und am naechsten Takt kommt nichts nach.
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        assertEquals(0L, buch.rechneAb(10));
        assertEquals(3L, verdient(ANNA));
    }

    @Test
    @DisplayName("Was am Deckel anfaellt, wandert NICHT in den naechsten Tag")
    void amDeckelWandertNichtInDenNaechstenTag() {
        // Diese Probe fehlte, und eine Einspritzung hat es gezeigt: Die Probe darueber
        // trifft nur den Fall, in dem der Deckel einen TEIL zulaesst. Ist er ganz
        // ausgeschoepft, laeuft ein anderer Zweig - und ohne diese Probe durfte der den
        // Rest stehenlassen, ohne dass jemand es merkt. Dann waere der Deckel genau das,
        // was er nicht sein soll: eine Verzoegerung. Wer einen Tag durchfarmt, bekaeme
        // am naechsten Morgen alles.
        var buch = buch(10L);
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        assertEquals(10L, buch.rechneAb(10), "der Deckel ist mit einem Erzblock voll");
        assertFalse(buch.amDeckel(ANNA), "genau aufgegangen ist nicht am Deckel");

        // Jetzt ist der Tag zu - und es wird weitergearbeitet.
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        assertEquals(1500L, buch.rest(ANNA), "fuenfzehn Mark wollen gebucht werden");

        assertEquals(0L, buch.rechneAb(10), "der Deckel laesst nichts mehr durch");
        assertTrue(buch.amDeckel(ANNA));
        assertEquals(0L, buch.rest(ANNA), "und die fuenfzehn Mark sind WEG");

        // Mitternacht UTC: Der neue Tag zahlt wieder - aber nicht das Gestrige nach.
        uhr.setze(Instant.parse("2026-11-07T00:00:00Z"));
        assertEquals(0L, buch.rechneAb(10));
        assertEquals(10L, verdient(ANNA),
                "sonst waere der Deckel nur eine Verzoegerung gewesen");
    }

    @Test
    @DisplayName("Die Tagessumme kommt aus dem BUCH und nicht aus dem Arbeitsspeicher")
    void dieTagessummeKommtAusDemBuch() {
        // Der Zeuge fuer "heute schon verdient" ist das Buchungsbuch. Ein Zaehler im
        // Arbeitsspeicher ueberlebte keinen Neustart: Ein Spieler am Deckel brauchte nur
        // auf den naechsten Neustart zu warten, und der Tag begaenne von vorn.
        kasse.gutschreiben(ANNA, 20L, Buchungsgrund.BERUF_VERDIENST);
        var buch = buch(20L);

        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        assertEquals(0L, buch.rechneAb(10),
                "zwanzig Mark stehen schon im Buch - der Deckel ist erreicht");
        assertTrue(buch.amDeckel(ANNA));
        assertEquals(20L, verdient(ANNA));
    }

    @Test
    @DisplayName("Nur VERDIENST zaehlt gegen den Deckel - ein Geschenk nicht")
    void nurVerdienstZaehltGegenDenDeckel() {
        // Sonst sperrte eine Auszahlung aus dem Grundstuecksverkauf die Arbeit des Tages,
        // und der Deckel maesse nicht mehr, was er messen soll.
        kasse.gutschreiben(ANNA, 1_000L, Buchungsgrund.BESTAND);
        var buch = buch(20L);

        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);

        assertEquals(10L, buch.rechneAb(10));
        assertFalse(buch.amDeckel(ANNA));
    }

    @Test
    @DisplayName("Der Deckel gilt je Spieler und nicht je Server")
    void derDeckelGiltJeSpieler() {
        var buch = buch(10L);
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        buch.verdiene(BERT, BERGMANN, Taetigkeit.ABBAUEN, ERZ);

        assertEquals(20L, buch.rechneAb(10));
        assertEquals(10L, verdient(ANNA));
        assertEquals(10L, verdient(BERT));
    }

    @Test
    @DisplayName("Ein neuer Tag hebt den Deckel wieder")
    void einNeuerTagHebtDenDeckel() {
        var buch = buch(10L);
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        assertEquals(10L, buch.rechneAb(10));

        // Mitternacht UTC - die Zone des Standes dieser Probe.
        uhr.setze(Instant.parse("2026-11-07T00:00:00Z"));
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);

        assertEquals(10L, buch.rechneAb(10), "der neue Tag zahlt wieder");
        assertEquals(20L, verdient(ANNA));
    }

    @Test
    @DisplayName("Nach einem Fehlschlag wird NICHT wiederholt")
    void nachEinemFehlschlagWirdNichtWiederholt() {
        // Die wichtigste Zusage dieser Datei. Anders als bei der Spielzeit ist das hier
        // zwingend: Ein zweiter Versuch mit derselben Summe buchte doppelt, und die
        // Zusage "Summe aller Buchungen gleich Kontostand" waere mit Geld gebrochen, das
        // niemand erarbeitet hat.
        var kaputte = new KaputteKasse();
        var buch = buch(kaputte);
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);

        assertEquals(0L, buch.rechneAb(10));
        assertEquals(1, kaputte.versuche, "einmal versucht");
        assertEquals(0L, buch.rest(ANNA), "der verlorene Betrag wird verworfen");
        assertTrue(protokollNennt("NICHT wiederholt"),
                protokollzeilen.stream().map(LogRecord::getMessage).toList().toString());
        assertEquals(Level.SEVERE, protokollzeilen.get(0).getLevel());

        assertEquals(0L, buch.rechneAb(10));
        assertEquals(1, kaputte.versuche, "derselbe Betrag wird NIE ein zweites Mal"
                + " versucht");
    }

    @Test
    @DisplayName("Nach einem Fehlschlag wird die Tagessumme neu gelesen")
    void nachEinemFehlschlagWirdNeuGelesen() {
        // Sonst rechnete der Sammler mit einer Tagessumme weiter, von der er nicht weiss,
        // ob die gescheiterte Buchung darin steht - und der Deckel waere um genau diesen
        // Betrag verschoben, in die falsche Richtung.
        var wackelige = new WackeligeKasse();
        var buch = buch(wackelige);
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        assertEquals(0L, buch.rechneAb(10));
        assertEquals(1, wackelige.gelesen);

        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        assertEquals(10L, buch.rechneAb(10), "der naechste Versuch gelingt");

        assertEquals(2, wackelige.gelesen, "die Tagessumme wird neu gelesen");
    }

    @Test
    @DisplayName("Die Klage ueber die Kasse kommt hoechstens einmal je Pause")
    void klageWirdGedrosselt() {
        // Ohne Drossel schriebe ein Ausfall der Datenbank je Spieler und je Takt eine
        // Stapelspur - das Protokoll waere in Minuten unbrauchbar, und zwar genau dann,
        // wenn man es braucht.
        var kaputte = new KaputteKasse();
        var buch = buch(kaputte);
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        buch.verdiene(BERT, BERGMANN, Taetigkeit.ABBAUEN, ERZ);

        buch.rechneAb(10);

        assertEquals(2, kaputte.versuche, "beide Spieler werden versucht");
        assertEquals(1, protokollzeilen.size(),
                "aber nur eine Zeile: " + protokollzeilen.size());
    }

    @Test
    @DisplayName("Ein Abgang rechnet sofort ab und vergisst den Zaehler")
    void abgangRechnetSofortAb() {
        var buch = buch();
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);

        buch.abgang(ANNA);

        assertEquals(10L, verdient(ANNA), "sofort und nicht im naechsten Takt");
        assertEquals(0L, buch.rest(ANNA));
        assertEquals(0, buch.wartende());
        // Vergessen, weil der Minderungszaehler die Sitzung nicht ueberlebt - eine Stunde
        // voller Saetze nach dem Wiederkommen ist der Preis dafuer, dass dieser Zustand
        // nirgends geschrieben wird.
        assertEquals(List.of(), buch.gezaehlteArten(ANNA));
    }

    @Test
    @DisplayName("Ein Abgang ohne ganze Mark verliert nichts - er verliert nur den Rest")
    void abgangUnterEinerMark() {
        // Benannt statt stillschweigend: Achtzig Pfennige sind beim Abgang weg. Sie zu
        // behalten hiesse, den Stand eines abgemeldeten Spielers zu speichern, und dafuer
        // waere eine Spalte in der Datenbank fuer weniger als eine Mark da.
        var buch = buch();
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, STEIN);
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, STEIN);

        buch.abgang(ANNA);

        assertEquals(0L, verdient(ANNA));
        assertEquals(0L, buch.rest(ANNA));
    }

    @Test
    @DisplayName("Beim Abschalten wird ALLES gebucht und geleert")
    void beimAbschaltenAllesGebucht() {
        // Ohne diesen Weg waere der letzte Verdienst jedes anwesenden Spielers verloren -
        // bei einem Neustart zur vollen Stunde jeden Tag.
        var buch = buch();
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        buch.verdiene(BERT, BERGMANN, Taetigkeit.ABBAUEN, ERZ);

        assertEquals(20L, buch.rechneAllesAb());

        assertEquals(10L, verdient(ANNA));
        assertEquals(10L, verdient(BERT));
        assertEquals(0, buch.wartende());
        assertEquals(List.of(), buch.gezaehlteArten(ANNA));
    }

    @Test
    @DisplayName("Der Handbalken kommt hoechstens einmal je Sekunde")
    void handbalkenWirdGedrosselt() {
        // Zwanzig Nachrichten je Sekunde waeren ein flackernder Streifen ueber dem
        // Gegenstandsgurt. Die -1 unterscheidet "noch nicht wieder" von "nichts
        // verdient": Beide senden nichts, aber eine Probe muss sie trennen koennen.
        var buch = buch();
        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);

        assertEquals(1000L, buch.fuerDenHandbalken(ANNA));
        assertEquals(0L, buch.fuerDenHandbalken(ANNA), "nichts Neues verdient");

        buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, ERZ);
        assertEquals(-1L, buch.fuerDenHandbalken(ANNA), "die Pause laeuft noch");
        assertEquals(-1L, buch.fuerDenHandbalken(ANNA));

        uhr.weiter(Verdienstbuch.ANZEIGEPAUSE);
        assertEquals(1000L, buch.fuerDenHandbalken(ANNA),
                "und dann die ganze Summe seit der letzten Anzeige");
    }

    @Test
    @DisplayName("Ein unbekannter Spieler hat nichts - und wirft nicht")
    void unbekannterSpielerHatNichts() {
        var buch = buch();

        assertEquals(0L, buch.rest(ANNA));
        assertEquals(0L, buch.fuerDenHandbalken(ANNA));
        assertFalse(buch.amDeckel(ANNA));
        assertEquals(List.of(), buch.gezaehlteArten(ANNA));
        assertEquals(0L, buch.rechneAb(10));
        buch.abgang(ANNA);
    }

    @Test
    @DisplayName("Kasse, Wirtschaftsstand, Uhr und Protokoll sind Pflicht")
    void pflichtenGelten() {
        var echterStand = stand(werte(Verdienstwerte.VORGABE_TAGESDECKEL));
        assertThrows(IllegalArgumentException.class,
                () -> new Verdienstbuch(null, echterStand, uhr, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Verdienstbuch(kasse, null, uhr, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Verdienstbuch(kasse, echterStand, null, protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Verdienstbuch(kasse, echterStand, uhr, null));

        var buch = buch();
        assertThrows(IllegalArgumentException.class,
                () -> buch.verdiene(null, BERGMANN, Taetigkeit.ABBAUEN, STEIN));
        assertThrows(IllegalArgumentException.class,
                () -> buch.verdiene(ANNA, null, Taetigkeit.ABBAUEN, STEIN));
        assertThrows(IllegalArgumentException.class,
                () -> buch.verdiene(ANNA, BERGMANN, null, STEIN));
        assertThrows(IllegalArgumentException.class,
                () -> buch.verdiene(ANNA, BERGMANN, Taetigkeit.ABBAUEN, null));
    }

    /** Eine Kasse, die beim Gutschreiben immer wirft - und mitzaehlt, wie oft. */
    private static class KaputteKasse implements Kasse {

        private int versuche;

        @Override
        public long guthaben(UUID spieler) {
            return 0L;
        }

        @Override
        public boolean eroeffne(UUID spieler) {
            return false;
        }

        @Override
        public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
            return false;
        }

        @Override
        public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
            versuche++;
            throw new IllegalStateException("die Datenbank ist weg");
        }

        @Override
        public List<Buchung> verlauf(UUID spieler, int hoechstens) {
            return List.of();
        }

        @Override
        public long verdientSeit(UUID spieler, Instant ab) {
            return 0L;
        }
    }

    /** Eine Kasse, die beim ERSTEN Gutschreiben wirft und danach gutgeht. */
    private static final class WackeligeKasse extends KaputteKasse {

        private int gelesen;
        private boolean schonGeworfen;

        @Override
        public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
            if (!schonGeworfen) {
                schonGeworfen = true;
                super.gutschreiben(spieler, betrag, grund);
            }
        }

        @Override
        public long verdientSeit(UUID spieler, Instant ab) {
            gelesen++;
            return 0L;
        }
    }
}
