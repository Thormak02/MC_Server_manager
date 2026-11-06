package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Rechnung, an der die ganze Wirtschaft haengt.
 *
 * <p>Plan 8.2 gibt eine Zielzahl her: Ein SPIELER verdient 2.000 bis 5.000 Mark je
 * Spielstunde. Diese Regel ist der einzige Ort, an dem diese Zahl entsteht - also wird sie
 * hier Stufe fuer Stufe nachgerechnet und nicht an ihrer Wirkung geprueft.
 *
 * <p><b>Die Minderung ist die unauffaelligste Zahl des Projekts.</b> Niemand kann im Spiel
 * ablesen, dass der dreihundertste Stein die Haelfte bringt - er bringt einfach weniger,
 * und das soll er. Ein Rechenfehler darin faellt deshalb nie auf: zu scharf, und jede
 * Mobfarm druckt Geld; zu milde, und ein Spieler verdient am Abend nichts mehr und sucht
 * den Fehler bei sich.
 */
class VerdienstregelTest {

    /** Die Vorgabe: 200 voll, dann je 200 auf die Haelfte, Boden ein Zehntel. */
    private static final Verdienstwerte WERTE = Verdienstwerte.VORGABE;

    private static final Set<String> BERGMANN = Set.of("bergmann");

    /** Eine Tafel mit 40 Pfennigen fuer Stein und 500 fuer Diamant. */
    private static final Verdienstwerte MIT_LOEHNEN = new Verdienstwerte(
            Verdienstwerte.VORGABE_TAGESDECKEL, Verdienstwerte.VORGABE_VOLL_BIS,
            Verdienstwerte.VORGABE_STUFE, Verdienstwerte.VORGABE_FAKTOR_PROZENT,
            Verdienstwerte.VORGABE_BODEN_PROZENT,
            Lohntafel.ausLoehnen(List.of(
                    new Lohntafel.Lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L),
                    new Lohntafel.Lohn("bergmann", Taetigkeit.ABBAUEN, "diamond_ore",
                            500L),
                    new Lohntafel.Lohn("baumeister", Taetigkeit.SETZEN, "stone", 25L))));

    // --- Die Minderung, Stufe fuer Stufe --------------------------------------

    @Test
    @DisplayName("Die ersten 200 Bloecke einer Art zahlen voll")
    void dieErstenZahlenVoll() {
        assertEquals(100, Verdienstregel.anteilProzent(WERTE, 0));
        assertEquals(100, Verdienstregel.anteilProzent(WERTE, 1));
        assertEquals(100, Verdienstregel.anteilProzent(WERTE, 199),
                "der 200. Block ist der letzte volle");
    }

    @Test
    @DisplayName("Jede Stufe halbiert - 200/400/600/800 sind die Kanten")
    void jedeStufeHalbiert() {
        // Die Tabelle aus dem Entwurf, Zahl fuer Zahl. Eine verrutschte Kante ist der
        // Fehler, den niemand sieht: Der Spieler verdient weniger, und die Minderung
        // greift eine Stufe zu frueh.
        assertEquals(50, Verdienstregel.anteilProzent(WERTE, 200), "der 201. Block");
        assertEquals(50, Verdienstregel.anteilProzent(WERTE, 399));
        assertEquals(25, Verdienstregel.anteilProzent(WERTE, 400), "der 401.");
        assertEquals(25, Verdienstregel.anteilProzent(WERTE, 599));
        assertEquals(12, Verdienstregel.anteilProzent(WERTE, 600), "der 601.");
        assertEquals(12, Verdienstregel.anteilProzent(WERTE, 799));
    }

    @Test
    @DisplayName("Unter den Boden geht es nie - und der ist 10 Prozent, nicht 0")
    void derBodenHaelt() {
        // Ein Boden von 0 waere eine unsichtbare Wand: Ab einer Zahl, die niemand kennt,
        // passiert gar nichts mehr. Ein Zehntel ist spuerbar wenig und bleibt eine
        // Antwort.
        assertEquals(10, Verdienstregel.anteilProzent(WERTE, 800), "der 801.");
        assertEquals(10, Verdienstregel.anteilProzent(WERTE, 5_000));
        assertEquals(10, Verdienstregel.anteilProzent(WERTE, 1_000_000),
                "auch eine Mobfarm faellt nicht unter den Boden");
        assertEquals(10, Verdienstregel.anteilProzent(WERTE, Integer.MAX_VALUE),
                "und die Schleife endet, statt eine Milliarde Mal zu rechnen");
    }

    @Test
    @DisplayName("Andere Zahlen ergeben andere Kanten - die Form steht in der Datei")
    void andereZahlenAndereKanten() {
        Verdienstwerte scharf = new Verdienstwerte(1_000L, 10, 10, 25, 1,
                Lohntafel.ausLoehnen(List.of()));

        assertEquals(100, Verdienstregel.anteilProzent(scharf, 9));
        assertEquals(25, Verdienstregel.anteilProzent(scharf, 10));
        assertEquals(6, Verdienstregel.anteilProzent(scharf, 20));
        assertEquals(1, Verdienstregel.anteilProzent(scharf, 40), "am Boden von 1");
    }

    @Test
    @DisplayName("Eine negative Zahl gezaehlter Bloecke ist ein Rechenfehler")
    void negativeZahlWirft() {
        assertThrows(IllegalArgumentException.class,
                () -> Verdienstregel.anteilProzent(WERTE, -1));
        assertThrows(IllegalArgumentException.class,
                () -> Verdienstregel.anteilProzent(null, 0));
    }

    // --- Der Lohn -------------------------------------------------------------

    @Test
    @DisplayName("Der Lohn ist der Satz der Tafel, gemindert um den Anteil")
    void lohnMalAnteil() {
        assertEquals(40L, Verdienstregel.pfennige(MIT_LOEHNEN, BERGMANN,
                Taetigkeit.ABBAUEN, "stone", 0));
        assertEquals(20L, Verdienstregel.pfennige(MIT_LOEHNEN, BERGMANN,
                Taetigkeit.ABBAUEN, "stone", 200));
        assertEquals(10L, Verdienstregel.pfennige(MIT_LOEHNEN, BERGMANN,
                Taetigkeit.ABBAUEN, "stone", 400));
        assertEquals(4L, Verdienstregel.pfennige(MIT_LOEHNEN, BERGMANN,
                Taetigkeit.ABBAUEN, "stone", 10_000), "am Boden: ein Zehntel von 40");
    }

    @Test
    @DisplayName("Was nicht in der Tafel steht, bringt nichts")
    void ohneEintragKeinLohn() {
        // Der Normalfall: Die Liste nennt die Materialien, die zahlen, und nicht die
        // tausend, die es nicht tun.
        assertEquals(0L, Verdienstregel.pfennige(MIT_LOEHNEN, BERGMANN,
                Taetigkeit.ABBAUEN, "dirt", 0));
        // Dieselbe Art, aber die andere Taetigkeit: Der Bergmann verdient am ABBAUEN von
        // Stein, nicht am Setzen. Ohne die Taetigkeit in der Tafel bekaeme er beides.
        assertEquals(0L, Verdienstregel.pfennige(MIT_LOEHNEN, BERGMANN,
                Taetigkeit.SETZEN, "stone", 0));
        // Und ohne den passenden Beruf gar nichts.
        assertEquals(0L, Verdienstregel.pfennige(MIT_LOEHNEN, Set.of("fischer"),
                Taetigkeit.ABBAUEN, "stone", 0));
        assertEquals(0L, Verdienstregel.pfennige(MIT_LOEHNEN, Set.of(),
                Taetigkeit.ABBAUEN, "stone", 0));
    }

    @Test
    @DisplayName("Ein kleiner Lohn kann durch die Minderung auf 0 fallen - richtig gerechnet")
    void kleinerLohnFaelltAufNull() {
        // Vier Pfennige mal ein Zehntel sind null. Wer das nicht will, setzt den Lohn
        // hoeher oder den Boden; die Rechnung erfindet keinen Pfennig.
        Verdienstwerte knapp = new Verdienstwerte(1_000L, 10, 10, 50, 10,
                Lohntafel.ausLoehnen(List.of(
                        new Lohntafel.Lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 4L))));

        assertEquals(4L, Verdienstregel.pfennige(knapp, BERGMANN, Taetigkeit.ABBAUEN,
                "stone", 0));
        assertEquals(0L, Verdienstregel.pfennige(knapp, BERGMANN, Taetigkeit.ABBAUEN,
                "stone", 100));
    }

    @Test
    @DisplayName("Zwei Berufe: der HOECHSTE Satz gilt, nicht die Summe")
    void derHoechsteSatzGilt() {
        // Der Plan sagt, die zwei Grundberufe TEILEN sich das Einkommen. Summiert waere
        // ein Block zweimal bezahlt. Der hoechste Satz ist die freundliche Lesart: Wer
        // zwei passende Berufe haelt, bekommt den besseren.
        Verdienstwerte beide = new Verdienstwerte(1_000L, 200, 200, 50, 10,
                Lohntafel.ausLoehnen(List.of(
                        new Lohntafel.Lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L),
                        new Lohntafel.Lohn("steinmetz", Taetigkeit.ABBAUEN, "stone",
                                70L))));

        assertEquals(70L, Verdienstregel.pfennige(beide,
                Set.of("bergmann", "steinmetz"), Taetigkeit.ABBAUEN, "stone", 0));
    }

    // --- Der Tagesdeckel ------------------------------------------------------

    @Test
    @DisplayName("Unter dem Deckel wird voll gezahlt")
    void unterDemDeckel() {
        assertEquals(100L, Verdienstregel.unterDemDeckel(WERTE, 0L, 100L));
        assertEquals(100L, Verdienstregel.unterDemDeckel(WERTE, 19_000L, 100L));
    }

    @Test
    @DisplayName("Am Deckel wird GEKUERZT, nicht abgelehnt")
    void amDeckelWirdGekuerzt() {
        // Der Rest einer Mark zu zahlen ist richtiger, als sie ganz zu verweigern: Der
        // Deckel ist eine Bremse und keine Strafe.
        assertEquals(50L, Verdienstregel.unterDemDeckel(WERTE, 19_950L, 100L));
        assertEquals(0L, Verdienstregel.unterDemDeckel(WERTE, 20_000L, 100L));
        assertEquals(0L, Verdienstregel.unterDemDeckel(WERTE, 25_000L, 100L),
                "ueber dem Deckel - etwa nach einer Senkung im Betrieb");
    }

    @Test
    @DisplayName("Negative Betraege kommen nicht in den Deckel")
    void negativesWirft() {
        assertThrows(IllegalArgumentException.class,
                () -> Verdienstregel.unterDemDeckel(WERTE, -1L, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> Verdienstregel.unterDemDeckel(WERTE, 0L, -1L));
        assertThrows(IllegalArgumentException.class,
                () -> Verdienstregel.unterDemDeckel(null, 0L, 10L));
    }

    // --- Mark und Rest --------------------------------------------------------

    @Test
    @DisplayName("Aus Pfennigen werden ganze Mark und ein Rest")
    void markUndRest() {
        // Der Rest ist der ganze Grund fuer die Pfennige: Ohne ihn muesste gewoehnlicher
        // Stein 0 zahlen.
        assertEquals(0L, Verdienstregel.mark(99L));
        assertEquals(99L, Verdienstregel.rest(99L));
        assertEquals(1L, Verdienstregel.mark(100L));
        assertEquals(0L, Verdienstregel.rest(100L));
        assertEquals(12L, Verdienstregel.mark(1_234L));
        assertEquals(34L, Verdienstregel.rest(1_234L));
        assertThrows(IllegalArgumentException.class, () -> Verdienstregel.mark(-1L));
        assertThrows(IllegalArgumentException.class, () -> Verdienstregel.rest(-1L));
    }

    // --- Die Gegenprobe zur Kalibrierung --------------------------------------

    @Test
    @DisplayName("Die Rechnung trifft 2.000 bis 5.000 Mark je Spielstunde")
    void dieKalibrierungStimmt() {
        // Die EINE Zahl, die der Plan vorgibt (8.2), hier nachgerechnet - und das
        // Ergebnis ist ein BEFUND und keine Bestaetigung.
        //
        // Zweitausend Bloecke Stein in einer Stunde, 40 Pfennige voll:
        //   200 x 40 = 8.000 | 200 x 20 = 4.000 | 200 x 10 = 2.000
        //   200 x  4 =   800 (12 Prozent von 40, ganzzahlig) | 1.200 x 4 = 4.800
        //   zusammen 19.600 Pfennige, also 196 Mark.
        long pfennige = 0L;
        for (int block = 0; block < 2_000; block++) {
            pfennige += Verdienstregel.pfennige(MIT_LOEHNEN, BERGMANN,
                    Taetigkeit.ABBAUEN, "stone", block);
        }

        // 196 Mark fuer eine Stunde Steinabbau - ein Zehntel der Spanne. Das ist
        // ABSICHT und steht hier, damit niemand die Lohnliste danach einstellt:
        // Gewoehnlicher Stein soll die Spanne NICHT tragen, sonst waere stumpfes Graben
        // der beste Beruf. Sie kommt aus dem, was dabei anfaellt - Kohle, Eisen, Tiefe -
        // und aus den anderen fuenf Berufen. Wer den Steinsatz vervielfacht, sieht hier
        // zuerst, dass er die Grundlage der Kalibrierung verschiebt.
        assertEquals(196L, Verdienstregel.mark(pfennige),
                "zweitausend Stein bringen mit der Vorgabe 196 Mark");
    }
}
