package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Was ein Serientag an Mark bringt. */
@DisplayName("Die Bonusregel")
class BonusregelTest {

    /** Die ausgelieferten Zahlen: 500, je Tag 100 dazu, hoechstens 2.000. */
    private static final Bonuswerte WERTE = Bonuswerte.VORGABE;

    @Test
    @DisplayName("Der erste Tag bringt den Grundbetrag - ohne Zuschlag")
    void ersterTag() {
        // Nicht Grundbetrag plus Zuschlag: Sonst waere der Grundbetrag eine Zahl, die
        // nie allein auftritt, und der Betreiber traegt sie ein und sieht sie nie.
        assertEquals(500L, Bonusregel.betrag(1, WERTE));
    }

    @Test
    @DisplayName("Jeder weitere Tag legt den Zuschlag dazu")
    void weitereTage() {
        assertEquals(600L, Bonusregel.betrag(2, WERTE));
        assertEquals(700L, Bonusregel.betrag(3, WERTE));
        assertEquals(1_400L, Bonusregel.betrag(10, WERTE));
    }

    @Test
    @DisplayName("Am Hoechstbetrag hoert das Wachsen auf")
    void hoechstbetragKlemmt() {
        assertEquals(2_000L, Bonusregel.betrag(16, WERTE));
        assertEquals(2_000L, Bonusregel.betrag(17, WERTE));
        assertEquals(2_000L, Bonusregel.betrag(1_000, WERTE));
        // Und auch eine Serie, die ein Jahr laeuft, ueberlaeuft nicht und wird nicht
        // negativ: Gerechnet wird mit der geklemmten Zahl der Serientage.
        assertEquals(2_000L, Bonusregel.betrag(Integer.MAX_VALUE, WERTE));
    }

    @Test
    @DisplayName("Ab Tag 16 ist der Hoechstbetrag erreicht - und das sagt die Regel auch")
    void tagDesHoechstbetrags() {
        assertEquals(16, Bonusregel.tagDesHoechstbetrags(WERTE));
        // Die Gegenprobe: Der Tag davor ist noch darunter.
        assertTrue(Bonusregel.betrag(15, WERTE) < WERTE.hoechstbetrag());
        assertEquals(WERTE.hoechstbetrag(), Bonusregel.betrag(16, WERTE));
    }

    @Test
    @DisplayName("Eine Spanne, die nicht aufgeht, klemmt am ERSTEN Tag darueber")
    void spanneGehtNichtAuf() {
        // 500 + 400 je Tag bis 2.000: Tag 4 gibt 1.700, Tag 5 waere 2.100 und wird auf
        // 2.000 geklemmt. Der Tag des Hoechstbetrags ist darum 5 und nicht 4 - und eine
        // Rechnung, die abrundet statt aufzurunden, sagte hier 4.
        Bonuswerte werte = WERTE.mitJeSerientag(400L);

        assertEquals(1_700L, Bonusregel.betrag(4, werte));
        assertEquals(2_000L, Bonusregel.betrag(5, werte));
        assertEquals(5, Bonusregel.tagDesHoechstbetrags(werte));
    }

    @Test
    @DisplayName("Ohne Zuschlag bringt jeder Tag dasselbe")
    void ohneZuschlag() {
        // Eine 0 bei je-serientag ist erlaubt und heisst genau das. Sie ist ausserdem der
        // Fall, in dem die Rechnung durch 0 teilen wuerde - darum hat sie einen eigenen
        // Zweig.
        Bonuswerte flach = WERTE.mitJeSerientag(0L);

        assertEquals(500L, Bonusregel.betrag(1, flach));
        assertEquals(500L, Bonusregel.betrag(50, flach));
        assertEquals(1, Bonusregel.tagDesHoechstbetrags(flach),
                "ohne Wachstum bringt schon der erste Tag alles, was es gibt");
    }

    @Test
    @DisplayName("Ein Serientag unter 1 ist ein Programmfehler")
    void serieUnterEins() {
        // Die Serienregel gibt nie eine, also ist ein solcher Aufruf ein Fehler im
        // Programm und keine Eingabe - und er soll auffallen, nicht 500 Mark zahlen.
        assertThrows(IllegalArgumentException.class, () -> Bonusregel.betrag(0, WERTE));
        assertThrows(IllegalArgumentException.class, () -> Bonusregel.betrag(-1, WERTE));
    }

    @Test
    @DisplayName("Ohne Werte gibt es keinen Betrag")
    void ohneWerte() {
        assertThrows(IllegalArgumentException.class, () -> Bonusregel.betrag(1, null));
        assertThrows(IllegalArgumentException.class,
                () -> Bonusregel.tagDesHoechstbetrags(null));
    }

    @Test
    @DisplayName("Der Betrag waechst nie ueber den Hoechstbetrag, egal welche Zahlen")
    void nieUeberDemHoechstbetrag() {
        // Eine Rundumprobe ueber grosse Zahlen: Daran faellt ein Ueberlauf auf, der sich
        // in einer einzelnen Probe verstecken kann. Negativ darf nie etwas werden - das
        // waere eine Abbuchung auf dem Weg einer Gutschrift.
        Bonuswerte gross = new Bonuswerte(1L, Bonuswerte.HOECHSTER_BETRAG,
                Bonuswerte.HOECHSTER_BETRAG, 2, 0L);

        for (int serie : new int[] {1, 2, 3, 1_000, Integer.MAX_VALUE}) {
            long betrag = Bonusregel.betrag(serie, gross);
            assertTrue(betrag >= 1L && betrag <= Bonuswerte.HOECHSTER_BETRAG,
                    "Serie " + serie + " gab " + betrag);
        }
    }
}
