package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpielzeitstandTest {

    private static final UUID ANNA = UUID.randomUUID();
    private static final LocalDate TAG = LocalDate.of(2026, 11, 3);

    @Test
    @DisplayName("Ein leerer Stand nennt den Tag und zweimal null")
    void leererStand() {
        Spielzeitstand stand = Spielzeitstand.leer(ANNA, TAG);

        assertEquals(0L, stand.sekundenGesamt());
        assertEquals(0L, stand.sekundenHeute());
        assertEquals(TAG, stand.tag());
    }

    @Test
    @DisplayName("Die Dauern sind dieselben Zahlen, nur anders geschrieben")
    void dauern() {
        Spielzeitstand stand = new Spielzeitstand(ANNA, TAG, 7_200L, 1_800L);

        assertEquals(Duration.ofHours(2), stand.gesamt());
        assertEquals(Duration.ofMinutes(30), stand.heute());
    }

    @Test
    @DisplayName("Heute kann nicht mehr sein als insgesamt - das waere ein Lesefehler")
    void heuteNieMehrAlsGesamt() {
        // Faellt hier auf und nicht in der Anzeige: Eine Tageszahl ueber der Gesamtzahl
        // heisst, dass zwei verschiedene Zeilen gelesen wurden.
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(ANNA, TAG, 100L, 101L));
    }

    @Test
    @DisplayName("Negative Zeit und fehlende Angaben kommen nicht in einen Stand")
    void schrankenGelten() {
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(ANNA, TAG, -1L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(ANNA, TAG, 10L, -1L));
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(null, TAG, 0L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new Spielzeitstand(ANNA, null, 0L, 0L));
    }
}
