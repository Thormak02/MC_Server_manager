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

    /**
     * Der Spieler steht NICHT in der Inselwelt - also legt kein Zuschlag auf.
     *
     * <p>Ein NAME und kein nacktes {@code false} an jeder Aufrufstelle: Der
     * Inselbonus kam mit Schritt 10 dazu (Plan 9.1, "Fischer - Bonus auf
     * Hafenwerder"), und jede Probe dieser Datei rechnet ohne ihn. Ein false an
     * fuenfzig Stellen waere fuenfzigmal eine Zahl, die nichts sagt.
     */
    private static final boolean FESTLAND = false;

    /** Der Spieler steht in der Inselwelt - dort legt der Zuschlag auf. */
    private static final boolean INSELWELT = true;


    /** Die Vorgabe: 200 voll, dann je 200 auf die Haelfte, Boden ein Zehntel. */
    private static final Verdienstwerte WERTE = Verdienstwerte.VORGABE;

    private static final Set<String> BERGMANN = Set.of("bergmann");

    /**
      * Minderung UND Tafel in einem - die zwei Stellen, die beides setzen.
      *
      * <p>Ueber die Abwandler und nicht ueber den Konstruktor: Dieser Satz waechst mit
      * jedem Bauschritt um einen Teil, und eine Probe soll nur das nennen, was ihre
      * Frage betrifft.
      */
    private static Verdienstwerte werteMitTafel(long deckel, int bisVoll, int breite,
                                                int faktor, int boden, Lohntafel tafel) {
        return Verdienstwerte.VORGABE.mitMinderung(deckel, bisVoll, breite, faktor, boden)
                .mitLoehnen(tafel);
    }

    /** Eine Tafel mit 40 Pfennigen fuer Stein und 500 fuer Diamant. */
    private static final Verdienstwerte MIT_LOEHNEN = Verdienstwerte.VORGABE
            .mitLoehnen(
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
        Verdienstwerte scharf = Verdienstwerte.VORGABE
                .mitMinderung(1_000L, 10, 10, 25, 1);

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
                Taetigkeit.ABBAUEN, "stone", 0, FESTLAND));
        assertEquals(20L, Verdienstregel.pfennige(MIT_LOEHNEN, BERGMANN,
                Taetigkeit.ABBAUEN, "stone", 200, FESTLAND));
        assertEquals(10L, Verdienstregel.pfennige(MIT_LOEHNEN, BERGMANN,
                Taetigkeit.ABBAUEN, "stone", 400, FESTLAND));
        assertEquals(4L, Verdienstregel.pfennige(MIT_LOEHNEN, BERGMANN,
                Taetigkeit.ABBAUEN, "stone", 10_000, FESTLAND), "am Boden: ein Zehntel von 40");
    }

    @Test
    @DisplayName("Der Inselbonus legt auf - und NUR in der Inselwelt")
    void inselbonusLegtAuf() {
        // Plan 9.1: "Fischer - Bonus auf Hafenwerder". Fuenfzig Prozent auf 40 Pfennige
        // sind 60 - und auf dem Festland bleiben es 40. Die Probe setzt ihren Zuschlag
        // SELBST und haengt nicht an der Vorgabe: An ihr haengend pruefte sie die
        // Vorgabe statt der Regel.
        Verdienstwerte werte = MIT_LOEHNEN.mitInselbonus(java.util.Map.of(
                "bergmann", 50));

        assertEquals(60L, Verdienstregel.pfennige(werte, BERGMANN, Taetigkeit.ABBAUEN,
                "stone", 0, INSELWELT));
        assertEquals(40L, Verdienstregel.pfennige(werte, BERGMANN, Taetigkeit.ABBAUEN,
                "stone", 0, FESTLAND),
                "auf dem Festland legt nichts auf - sonst waere der Bonus kein Grund,"
                        + " irgendwohin zu fahren");
    }

    @Test
    @DisplayName("Der Zuschlag liegt VOR der Minderung - er wird mitgedaempft")
    void zuschlagVorDerMinderung() {
        // DIE ENTSCHEIDUNG DIESES TEILS: 60 Pfennige, gemindert auf die Haelfte, sind
        // 30. Danach gerechnet waeren es 20 plus 20, also 40 - und dann waere der
        // Zuschlag am Ende einer Stunde der GROESSERE Teil des Lohns. Die Bremse gegen
        // den Klickautomaten waere in Hafenwerder die schwaechste von allen.
        Verdienstwerte werte = MIT_LOEHNEN.mitInselbonus(java.util.Map.of(
                "bergmann", 50));

        assertEquals(30L, Verdienstregel.pfennige(werte, BERGMANN, Taetigkeit.ABBAUEN,
                "stone", 200, INSELWELT));
        assertEquals(6L, Verdienstregel.pfennige(werte, BERGMANN, Taetigkeit.ABBAUEN,
                "stone", 10_000, INSELWELT),
                "am Boden: ein Zehntel von 60 - und nicht ein Zehntel von 40 plus die"
                        + " halben 20");
    }

    @Test
    @DisplayName("Der Zuschlag haengt am BERUF - und der beste Satz gewinnt")
    void zuschlagHaengtAmBeruf() {
        // Der Grund, aus dem die Regel jeden Beruf einzeln rechnet: hoechsterLohn sagt
        // nicht, WELCHER Beruf den besten Satz traegt - und mit dem Zuschlag kann ein
        // schlechterer Satz der beste werden. Hier zahlt der Bauer 10 und der Bergmann
        // 40; mit 500 Prozent auf den Bauern sind es 60 gegen 40.
        Verdienstwerte zwei = Verdienstwerte.VORGABE
                .mitLoehnen(Lohntafel.ausLoehnen(List.of(
                        new Lohntafel.Lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L),
                        new Lohntafel.Lohn("bauer", Taetigkeit.ABBAUEN, "stone", 10L))))
                .mitInselbonus(java.util.Map.of("bauer", 500));
        Set<String> beide = Set.of("bergmann", "bauer");

        assertEquals(40L, Verdienstregel.pfennige(zwei, beide, Taetigkeit.ABBAUEN,
                "stone", 0, FESTLAND), "auf dem Festland gewinnt der Bergmann");
        assertEquals(60L, Verdienstregel.pfennige(zwei, beide, Taetigkeit.ABBAUEN,
                "stone", 0, INSELWELT),
                "in der Inselwelt der Bauer mit seinem Zuschlag - eine Rechnung, die"
                        + " nur den hoechsten Satz kennt, kaeme hier auf 40");
    }

    @Test
    @DisplayName("Ein Zuschlag auf einen Beruf ohne Satz erfindet keinen Pfennig")
    void zuschlagOhneSatzErfindetNichts() {
        // Der Riegel, der vor dem Zuschlag steht: Wer die Taetigkeit an diesem Material
        // nicht bezahlt bekommt, bekommt auch keinen Zuschlag darauf. Sonst verdiente
        // ein Fischer in Hafenwerder am Abbauen von Stein.
        Verdienstwerte werte = MIT_LOEHNEN.mitInselbonus(java.util.Map.of(
                "fischer", 500));

        assertEquals(0L, Verdienstregel.pfennige(werte, Set.of("fischer"),
                Taetigkeit.ABBAUEN, "stone", 0, INSELWELT));
    }

    @Test
    @DisplayName("Was nicht in der Tafel steht, bringt nichts")
    void ohneEintragKeinLohn() {
        // Der Normalfall: Die Liste nennt die Materialien, die zahlen, und nicht die
        // tausend, die es nicht tun.
        assertEquals(0L, Verdienstregel.pfennige(MIT_LOEHNEN, BERGMANN,
                Taetigkeit.ABBAUEN, "dirt", 0, FESTLAND));
        // Dieselbe Art, aber die andere Taetigkeit: Der Bergmann verdient am ABBAUEN von
        // Stein, nicht am Setzen. Ohne die Taetigkeit in der Tafel bekaeme er beides.
        assertEquals(0L, Verdienstregel.pfennige(MIT_LOEHNEN, BERGMANN,
                Taetigkeit.SETZEN, "stone", 0, FESTLAND));
        // Und ohne den passenden Beruf gar nichts.
        assertEquals(0L, Verdienstregel.pfennige(MIT_LOEHNEN, Set.of("fischer"),
                Taetigkeit.ABBAUEN, "stone", 0, FESTLAND));
        assertEquals(0L, Verdienstregel.pfennige(MIT_LOEHNEN, Set.of(),
                Taetigkeit.ABBAUEN, "stone", 0, FESTLAND));
    }

    @Test
    @DisplayName("Ein kleiner Lohn kann durch die Minderung auf 0 fallen - richtig gerechnet")
    void kleinerLohnFaelltAufNull() {
        // Vier Pfennige mal ein Zehntel sind null. Wer das nicht will, setzt den Lohn
        // hoeher oder den Boden; die Rechnung erfindet keinen Pfennig.
        Verdienstwerte knapp = werteMitTafel(1_000L, 10, 10, 50, 10,
                Lohntafel.ausLoehnen(List.of(
                        new Lohntafel.Lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 4L))));

        assertEquals(4L, Verdienstregel.pfennige(knapp, BERGMANN, Taetigkeit.ABBAUEN,
                "stone", 0, FESTLAND));
        assertEquals(0L, Verdienstregel.pfennige(knapp, BERGMANN, Taetigkeit.ABBAUEN,
                "stone", 100, FESTLAND));
    }

    @Test
    @DisplayName("Zwei Berufe: der HOECHSTE Satz gilt, nicht die Summe")
    void derHoechsteSatzGilt() {
        // Der Plan sagt, die zwei Grundberufe TEILEN sich das Einkommen. Summiert waere
        // ein Block zweimal bezahlt. Der hoechste Satz ist die freundliche Lesart: Wer
        // zwei passende Berufe haelt, bekommt den besseren.
        Verdienstwerte beide = werteMitTafel(1_000L, 200, 200, 50, 10,
                Lohntafel.ausLoehnen(List.of(
                        new Lohntafel.Lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L),
                        new Lohntafel.Lohn("steinmetz", Taetigkeit.ABBAUEN, "stone",
                                70L))));

        assertEquals(70L, Verdienstregel.pfennige(beide,
                Set.of("bergmann", "steinmetz"), Taetigkeit.ABBAUEN, "stone", 0, FESTLAND));
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
                    Taetigkeit.ABBAUEN, "stone", block, FESTLAND);
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
