package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Zahlen des Bundesmarktes - und die 0, die kein Deckel heisst.
 *
 * <p>Diese Probendatei ist am 10.11.2026 kleiner geworden, weil der Satz es ist: Das
 * SORTIMENT ({@code Ankaufliste}) ist gefallen, denn "es kann ja nur ge- und verkauft
 * werden, wofuer es ein Schild gibt". Was bleibt, ist eine Zahl - und die hat jetzt einen
 * Wert mehr als vorher.
 */
@DisplayName("Die Zahlen des Bundesmarktes")
class MarktwerteTest {

    @Test
    @DisplayName("Die VORGABE ist ein Viertel des Verdienstdeckels")
    void vorgabeIstEinViertel() {
        // Gerechnet gegen die Rolle: Der Markt ist die Verwertung und keine zweite
        // Arbeit. Die Zahl steht hier und nicht nur in der Datei, denn eine Datei kann
        // fehlen - dann gilt diese.
        assertEquals(5_000L, Marktwerte.VORGABE.tagesdeckel());
        assertEquals(Verdienstwerte.VORGABE_TAGESDECKEL / 4L,
                Marktwerte.VORGABE.tagesdeckel(),
                "ein Viertel des Verdienstdeckels - wer die eine Zahl aendert, soll"
                        + " diese Probe lesen und nicht raten");
        assertTrue(Marktwerte.VORGABE.gedeckelt());
    }

    @Test
    @DisplayName("Eine 0 heisst KEIN Deckel - und ist erlaubt")
    void nullHeisstKeinDeckel() {
        // ENTSCHIEDEN AM 10.11.2026, und bis dahin war es umgekehrt: Eine 0 wurde
        // abgewiesen, mit der Begruendung "eine 0 hiesse, niemand kann verkaufen - wer
        // den Ankauf abschalten will, laesst die Liste leer". Die Liste gibt es nicht
        // mehr, und damit ist die 0 frei fuer die Bedeutung, die der Betreiber braucht.
        //
        // Abgeschaltet wird der Ankauf jetzt dadurch, dass kein Schild dafuer steht -
        // und das ist dieselbe Entscheidung, die auch das Sortiment ersetzt hat.
        Marktwerte ohne = Marktwerte.VORGABE.mitDeckel(Marktwerte.KEIN_DECKEL);

        assertEquals(0L, ohne.tagesdeckel());
        assertFalse(ohne.gedeckelt(), "0 heisst: kein Deckel");
        assertTrue(Marktwerte.brauchbarerDeckel(0L));
    }

    @Test
    @DisplayName("Ein Deckel ab 1 Mark gilt, ein negativer ist keiner")
    void grenzenDesDeckels() {
        assertTrue(Marktwerte.VORGABE.mitDeckel(1L).gedeckelt(),
                "eine Mark ist ein Deckel - ein strenger, aber einer");
        assertTrue(Marktwerte.brauchbarerDeckel(Marktwerte.HOECHSTER_TAGESDECKEL));
        assertFalse(Marktwerte.brauchbarerDeckel(-1L));
        assertFalse(Marktwerte.brauchbarerDeckel(Marktwerte.HOECHSTER_TAGESDECKEL + 1L));

        var fehler = assertThrows(IllegalArgumentException.class,
                () -> Marktwerte.VORGABE.mitDeckel(-5L));
        assertTrue(fehler.getMessage().contains("kein"), fehler.getMessage());
    }

    @Test
    @DisplayName("Der Hoechstwert ist derselbe wie beim Verdienen")
    void hoechstwertIstDerselbe() {
        // Zwei Zahlen fuer dieselbe Schranke liefen auseinander: Die eine bekaeme eine
        // Erhoehung mit, die andere nicht - und dann deckelt der Server zwei Quellen
        // nach zwei verschiedenen Grenzen.
        assertEquals(Verdienstwerte.HOECHSTER_TAGESDECKEL,
                Marktwerte.HOECHSTER_TAGESDECKEL);
    }
}
