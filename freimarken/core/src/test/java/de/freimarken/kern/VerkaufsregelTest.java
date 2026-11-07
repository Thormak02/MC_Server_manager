package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Rechnung des Bundesmarktes - was er nimmt und was er zahlt.
 *
 * <p><b>Die tragende Probe ist {@link #nurGanzeMarkWerdenGenommen()}:</b> Der Bund zahlt
 * in ganzen Mark, und was er nicht bezahlen kann, nimmt er nicht. Haette er alles
 * genommen und abgerundet, waere die Ware weg und der Pfennigrest verschwunden - der
 * Spieler sieht nur, dass seine Rechnung nicht aufgeht.
 */
@DisplayName("Die Rechnung des Bundesmarktes")
class VerkaufsregelTest {

    /** Stein zu 20 Pfennigen - fuenf Stueck sind genau eine Mark. */
    private static final String STEIN = "stone";

    /** Diamant zu 1100 Pfennigen - ein Stueck ist mehr als eine Mark. */
    private static final String DIAMANT = "diamond";

    private static Marktwerte werte(long deckel) {
        return Marktwerte.VORGABE.mitDeckel(deckel)
                .mitAnkauf(Ankaufliste.ausAnkaeufen(List.of(
                        new Ankaufliste.Ankauf(STEIN, 20L),
                        new Ankaufliste.Ankauf(DIAMANT, 1_100L))));
    }

    @Test
    @DisplayName("Fuenf Stein zu 20 Pfennigen sind eine Mark")
    void fuenfSteinEineMark() {
        var verkauf = Verkaufsregel.rechne(werte(5_000L), STEIN, 5, 0L);

        assertEquals(5, verkauf.stuecke());
        assertEquals(1L, verkauf.mark());
        assertFalse(verkauf.nichts());
    }

    @Test
    @DisplayName("Nur STUECKE, die eine ganze Mark ergeben - der Rest bleibt liegen")
    void nurGanzeMarkWerdenGenommen() {
        // Die Entscheidung dieser Klasse. Sieben Stein sind 1,40 Mark; der Bund nimmt
        // fuenf und zahlt eine, zwei bleiben im Inventar. Haette er alle sieben genommen
        // und abgerundet, waeren 40 Pfennige verschwunden - der Spieler sieht nur, dass
        // seine Ware weg ist, und rechnet nach.
        var verkauf = Verkaufsregel.rechne(werte(5_000L), STEIN, 7, 0L);

        assertEquals(5, verkauf.stuecke(), "fuenf, nicht sieben");
        assertEquals(1L, verkauf.mark());

        // Und bei neun: zwei Mark braucht zehn Stein, also bleiben es fuenf.
        assertEquals(5, Verkaufsregel.rechne(werte(5_000L), STEIN, 9, 0L).stuecke());
        assertEquals(10, Verkaufsregel.rechne(werte(5_000L), STEIN, 10, 0L).stuecke());
        assertEquals(2L, Verkaufsregel.rechne(werte(5_000L), STEIN, 10, 0L).mark());
    }

    @Test
    @DisplayName("Zu wenig fuer eine Mark heisst: nichts - und die Ware bleibt")
    void zuWenigFuerEineMark() {
        // Vier Stein sind 80 Pfennige. Nicht nehmen und nicht zahlen: Sonst ist die Ware
        // weg und der Spieler hat nichts.
        var verkauf = Verkaufsregel.rechne(werte(5_000L), STEIN, 4, 0L);

        assertTrue(verkauf.nichts());
        assertEquals(0, verkauf.stuecke());
        assertEquals(0L, verkauf.mark());
    }

    @Test
    @DisplayName("Was der Bund nicht kauft, bringt nichts - und wirft nicht")
    void nichtImSortiment() {
        for (String material : new String[] {"dirt", "", "STONE_BRICKS", null}) {
            var verkauf = Verkaufsregel.rechne(werte(5_000L), material, 64, 0L);

            assertTrue(verkauf.nichts(), String.valueOf(material));
        }
    }

    @Test
    @DisplayName("Der Name wird gross und mit Leerraum erkannt")
    void nameWirdNormalisiert() {
        assertEquals(1L, Verkaufsregel.rechne(werte(5_000L), " STONE ", 5, 0L).mark());
    }

    @Test
    @DisplayName("Der Tagesdeckel kuerzt die STUECKZAHL, nicht nur den Betrag")
    void deckelKuerztDieStueckzahl() {
        // Der Kern der Fairness: Wer 100 Diamanten anbietet und nur noch 33 Mark frei
        // hat, soll DREI loswerden und 97 behalten - nicht alle 100 fuer 33 Mark.
        var verkauf = Verkaufsregel.rechne(werte(33L), DIAMANT, 100, 0L);

        assertEquals(3, verkauf.stuecke(), "ein Diamant ist 11 Mark, also passen drei");
        assertEquals(33L, verkauf.mark());

        // Und wenn nicht einmal EINER ganz hineinpasst, wird nichts genommen: Bei drei
        // Mark Deckel bleibt der ganze Stapel liegen.
        assertTrue(Verkaufsregel.rechne(werte(3L), DIAMANT, 100, 0L).nichts(),
                "11 Mark je Stueck - bei 3 Mark Deckel passt kein einziger");
    }

    @Test
    @DisplayName("Am Deckel wird nichts genommen")
    void amDeckelNichts() {
        var verkauf = Verkaufsregel.rechne(werte(100L), STEIN, 64, 100L);

        assertTrue(verkauf.nichts());
    }

    @Test
    @DisplayName("Kurz vor dem Deckel wird nur das Freie genommen")
    void kurzVorDemDeckel() {
        // Noch 2 Mark frei, angeboten sind 64 Stein (12,80 Mark). Genommen werden die
        // zehn Stein, die zwei Mark ergeben.
        var verkauf = Verkaufsregel.rechne(werte(100L), STEIN, 64, 98L);

        assertEquals(10, verkauf.stuecke());
        assertEquals(2L, verkauf.mark());
    }

    @Test
    @DisplayName("Null Stueck sind nichts, und eine negative Zahl ist ein Fehler")
    void nullUndNegativ() {
        assertTrue(Verkaufsregel.rechne(werte(5_000L), STEIN, 0, 0L).nichts());
        assertThrows(IllegalArgumentException.class,
                () -> Verkaufsregel.rechne(werte(5_000L), STEIN, -1, 0L));
    }

    @Test
    @DisplayName("Ohne Marktwerte gibt es keinen Verkauf")
    void ohneWerte() {
        assertThrows(IllegalArgumentException.class,
                () -> Verkaufsregel.rechne(null, STEIN, 5, 0L));
    }

    @Test
    @DisplayName("Ein ganzes Inventar zum Hoechstpreis laeuft NICHT ueber")
    void keinUeberlauf() {
        // Die Schranke ist gerechnet und nicht gewaehlt: Ein uebergelaufener Betrag ist
        // NEGATIV, und ein negativer Betrag auf dem Weg einer Einnahme ist eine
        // Abbuchung. Geprueft wird mit der groessten Last, die ein Inventar tragen kann -
        // 36 Plaetze plus Hand und Ruestung, mal 64, aufgerundet auf 2_816.
        Marktwerte teuer = Marktwerte.VORGABE
                .mitDeckel(Marktwerte.HOECHSTER_TAGESDECKEL)
                .mitAnkauf(Ankaufliste.ausAnkaeufen(List.of(new Ankaufliste.Ankauf(
                        STEIN, Verkaufsregel.HOECHSTER_PREIS))));

        var verkauf = Verkaufsregel.rechne(teuer, STEIN, 2_816, 0L);

        assertEquals(2_816, verkauf.stuecke());
        assertTrue(verkauf.mark() > 0L, "positiv und nicht uebergelaufen: "
                + verkauf.mark());
        assertEquals(2_816L * Verkaufsregel.HOECHSTER_PREIS / 100L, verkauf.mark());
    }

    @Test
    @DisplayName("Ein Preis ueber der Schranke ist ein Fehler und kein stiller Ueberlauf")
    void preisUeberDerSchranke() {
        // Die Liste lehnt so einen Preis schon ab; diese Probe sichert den Fall, dass
        // jemand den Satz von Hand baut. Zwei Schranken fuer dieselbe Zahl, aber nur
        // EINE Zahl - sie steht in Verkaufsregel.
        assertThrows(IllegalArgumentException.class,
                () -> Ankaufliste.ausAnkaeufen(List.of(new Ankaufliste.Ankauf(STEIN,
                        Verkaufsregel.HOECHSTER_PREIS + 1L))));
    }
}
