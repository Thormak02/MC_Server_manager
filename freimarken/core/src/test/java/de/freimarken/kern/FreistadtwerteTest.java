package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was eine Freistadt kostet und wie viele einer halten darf - die Schranken.
 *
 * <p>Dieselbe Bauart wie {@link MietwerteTest}: Jede Grenze wird von BEIDEN Seiten
 * angefasst. Ein Waechter, der nur nach unten prueft, laesst oben alles durch, und das
 * faellt erst auf, wenn eine Rechnung ueberlaeuft.
 */
@DisplayName("Die Zahlen der Freistaedte")
class FreistadtwerteTest {

    @Test
    @DisplayName("Die Vorgabe ist das, was Plan 6.2 nennt")
    void vorgabe() {
        assertEquals(400_000L, Freistadtwerte.VORGABE.gruendungspreis());
        assertEquals(1, Freistadtwerte.VORGABE.hoechstzahl());
    }

    @Test
    @DisplayName("Ein Gruendungspreis von 0 ist erlaubt - und heisst gratis")
    void nullIstErlaubt() {
        // Eine 0 ist keine Falle, sondern eine Ansage: Wer das Recht hat, gruendet
        // umsonst. Dieselbe Entscheidung wie bei der Kaution der Ladenmiete.
        Freistadtwerte gratis = Freistadtwerte.VORGABE.mitGruendungspreis(0L);

        assertEquals(0L, gratis.gruendungspreis());
        assertEquals(1, gratis.hoechstzahl(), "und die andere Zahl bleibt, wie sie war");
    }

    @Test
    @DisplayName("Eine Hoechstzahl von 0 ist erlaubt - dann wird gar nicht gegruendet")
    void keineStadtIstErlaubt() {
        // Brauchbar, solange eine Welt noch vorbereitet wird: Der Befehl steht, die
        // Absage kommt, und niemand belegt ein Feld, das spaeter anders liegen soll.
        Freistadtwerte aus = Freistadtwerte.VORGABE.mitHoechstzahl(0);

        assertEquals(0, aus.hoechstzahl());
        assertEquals(400_000L, aus.gruendungspreis());
    }

    @Test
    @DisplayName("Ein negativer Preis ist keiner")
    void negativerPreisWirft() {
        assertThrows(IllegalArgumentException.class,
                () -> new Freistadtwerte(-1L, 1));
    }

    @Test
    @DisplayName("Ueber der Geldgrenze laeuft die Rechnung ueber - darum ein Riegel")
    void zuGrosserPreisWirft() {
        assertEquals(Verdienstwerte.HOECHSTER_TAGESDECKEL,
                Freistadtwerte.HOECHSTER_BETRAG,
                "dieselbe Obergrenze wie bei allen Geldzahlen - dieselbe Rechnung");

        // Die Grenze selbst geht noch durch, eine mehr nicht. Beide Seiten angefasst:
        // Ein Waechter mit > statt >= liesse genau diesen einen Wert durch.
        assertEquals(Freistadtwerte.HOECHSTER_BETRAG,
                new Freistadtwerte(Freistadtwerte.HOECHSTER_BETRAG, 1).gruendungspreis());
        assertThrows(IllegalArgumentException.class,
                () -> new Freistadtwerte(Freistadtwerte.HOECHSTER_BETRAG + 1L, 1));
    }

    @Test
    @DisplayName("Mehr Staedte als Felder gibt es nicht")
    void zuVieleStaedteWerfen() {
        assertEquals(399, Freistadtwerte.HOECHSTE_ZAHL);
        assertEquals(Freistadtwerte.HOECHSTE_ZAHL,
                Feldgrenze.freistaedte().staedteZahl(),
                "die Schranke ist die Zahl der Felder und keine gegriffene Zahl -"
                        + " laufen beide auseinander, erlaubt die Datei eine Stadt, die"
                        + " es nicht geben kann");

        assertEquals(399, new Freistadtwerte(1L, 399).hoechstzahl());
        assertThrows(IllegalArgumentException.class, () -> new Freistadtwerte(1L, 400));
        assertThrows(IllegalArgumentException.class, () -> new Freistadtwerte(1L, -1));
    }

    @Test
    @DisplayName("Jeder Abwandler laesst die andere Zahl in Ruhe")
    void abwandlerSindEng() {
        Freistadtwerte eigen = new Freistadtwerte(12_345L, 7);

        assertEquals(7, eigen.mitGruendungspreis(1L).hoechstzahl());
        assertEquals(12_345L, eigen.mitHoechstzahl(3).gruendungspreis());
    }

    @Test
    @DisplayName("Die Pfade stehen hier und nicht an jeder Stelle, die klagt")
    void pfade() {
        assertEquals("freistaedte.gruendungspreis", Freistadtwerte.PFAD_GRUENDUNGSPREIS);
        assertEquals("freistaedte.hoechstzahl", Freistadtwerte.PFAD_HOECHSTZAHL);
    }
}
