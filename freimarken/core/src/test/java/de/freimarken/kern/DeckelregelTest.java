package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die eine Deckelrechnung fuer beide Tagesdeckel.
 *
 * <p>Eigene Proben, obwohl {@link VerdienstregelTest} dieselbe Rechnung schon durchlaeuft:
 * Seit Teil F haengen ZWEI Quellen daran, und diese Datei ist die Stelle, an der die
 * Rechnung selbst festgeschrieben ist. Ohne sie waere sie nur mittelbar gedeckt - und bei
 * einer Aenderung sagte die rote Probe "Verdienst", obwohl der Markt genauso betroffen
 * ist.
 */
@DisplayName("Die Deckelregel")
class DeckelregelTest {

    @Test
    @DisplayName("Unter dem Deckel gilt der ganze Wunsch")
    void unterDemDeckel() {
        assertEquals(100L, Deckelregel.erlaubt(5_000L, 0L, 100L));
        assertEquals(100L, Deckelregel.erlaubt(5_000L, 4_900L, 100L));
    }

    @Test
    @DisplayName("Ueber dem Deckel bleibt nur das Freie")
    void ueberDemDeckel() {
        assertEquals(50L, Deckelregel.erlaubt(5_000L, 4_950L, 100L));
        assertEquals(1L, Deckelregel.erlaubt(5_000L, 4_999L, 100L));
    }

    @Test
    @DisplayName("Am Deckel und darueber ist es nichts")
    void amDeckel() {
        assertEquals(0L, Deckelregel.erlaubt(5_000L, 5_000L, 100L));
        // DARUEBER kann es wirklich kommen: Der Deckel kann im Betrieb gesenkt werden,
        // und dann steht im Buch mehr, als er heute zulaesst. Eine negative Antwort waere
        // dort eine Abbuchung auf dem Weg einer Einnahme.
        assertEquals(0L, Deckelregel.erlaubt(5_000L, 9_999L, 100L));
    }

    @Test
    @DisplayName("Ein Wunsch von 0 ist 0 - und keine Ausnahme")
    void wunschNull() {
        assertEquals(0L, Deckelregel.erlaubt(5_000L, 0L, 0L));
    }

    @Test
    @DisplayName("Ein Deckel unter 1 ist keine Grenze, sondern eine Abschaltung")
    void deckelUnterEins() {
        // Wer eine Quelle abschalten will, laesst ihre Liste leer - die Lohnliste oder
        // das Sortiment. Ein Deckel von 0 sieht dagegen aus wie ein Vertipper.
        assertThrows(IllegalArgumentException.class,
                () -> Deckelregel.erlaubt(0L, 0L, 100L));
        assertThrows(IllegalArgumentException.class,
                () -> Deckelregel.erlaubt(-1L, 0L, 100L));
    }

    @Test
    @DisplayName("Negative Betraege sind ein Fehler")
    void negativeBetraege() {
        assertThrows(IllegalArgumentException.class,
                () -> Deckelregel.erlaubt(5_000L, -1L, 100L));
        assertThrows(IllegalArgumentException.class,
                () -> Deckelregel.erlaubt(5_000L, 0L, -1L));
    }

    @Test
    @DisplayName("Die Verdienstregel rechnet UEBER diese Stelle und nicht daneben")
    void verdienstregelNutztSie() {
        // Die Zusage, die diese Klasse ueberhaupt rechtfertigt: EINE Rechnung fuer beide
        // Deckel. Geprueft an einem Fall, der beides zugleich beantwortet - waere in der
        // Verdienstregel eine zweite Rechnung, waere eine der beiden Zeilen hier falsch.
        Verdienstwerte verdienst = Verdienstwerte.VORGABE.mitMinderung(5_000L, 200, 200,
                50, 10);

        assertEquals(Deckelregel.erlaubt(5_000L, 4_950L, 100L),
                Verdienstregel.unterDemDeckel(verdienst, 4_950L, 100L));
        assertEquals(Deckelregel.erlaubt(5_000L, 5_000L, 100L),
                Verdienstregel.unterDemDeckel(verdienst, 5_000L, 100L));
    }
}
