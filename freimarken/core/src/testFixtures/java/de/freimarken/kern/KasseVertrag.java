package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was jede Kasse einhalten muss - egal ob im Speicher oder in MariaDB.
 *
 * <p>Gleiche Bauart wie {@link BesitzLagerVertrag}: Die Umsetzung bringt ihre eigene
 * Testklasse mit und erbt diese Pruefungen. Dadurch wird eine Umsetzung, die sich anders
 * verhaelt als die andere, in einem Testlauf sichtbar und nicht im Betrieb.
 */
public abstract class KasseVertrag {

    /** Das Startguthaben, mit dem die geprueften Kassen aufgesetzt werden. */
    protected static final long START = 50_000L;

    /** Eine frische, leere Kasse mit {@link #START} als Startguthaben. */
    protected abstract Kasse kasse();

    @Test
    @DisplayName("Ein unbekannter Spieler hat das Startguthaben")
    void unbekannterHatStart() {
        assertEquals(START, kasse().guthaben(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Abbuchen senkt das Guthaben um genau den Betrag")
    void abbuchenSenkt() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        assertTrue(kasse.abbuchen(anna, 20_000L));

        assertEquals(START - 20_000L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Ein ungedeckter Betrag wird nicht halb abgebucht")
    void ungedecktAendertNichts() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        assertFalse(kasse.abbuchen(anna, START + 1));

        assertEquals(START, kasse.guthaben(anna),
                "nach einem abgelehnten Abbuchen muss der Stand unveraendert sein");
    }

    @Test
    @DisplayName("Genau das Guthaben laesst sich abbuchen, bis auf null")
    void genauAufgebraucht() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        assertTrue(kasse.abbuchen(anna, START));

        assertEquals(0L, kasse.guthaben(anna));
        assertFalse(kasse.abbuchen(anna, 1L), "von null geht nichts mehr ab");
    }

    @Test
    @DisplayName("Null abbuchen geht immer - auch von einem leeren Konto")
    void nullGehtImmer() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();
        assertTrue(kasse.abbuchen(anna, START));

        assertTrue(kasse.abbuchen(anna, 0L),
                "sonst muesste jede Gratis-Regel den Sonderfall selbst kennen");
        assertEquals(0L, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Gutschreiben hebt das Guthaben, auch fuer einen unbekannten Spieler")
    void gutschreibenHebt() {
        Kasse kasse = kasse();
        UUID neu = UUID.randomUUID();

        kasse.gutschreiben(neu, 1_000L);

        assertEquals(START + 1_000L, kasse.guthaben(neu));
    }

    @Test
    @DisplayName("Jeder Spieler hat sein eigenes Konto")
    void kontenSindGetrennt() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();

        assertTrue(kasse.abbuchen(anna, 30_000L));

        assertEquals(START - 30_000L, kasse.guthaben(anna));
        assertEquals(START, kasse.guthaben(bert));
    }

    @Test
    @DisplayName("Ein negativer Betrag ist ein Fehler, keine verkappte Gegenbuchung")
    void negativFliegtAuf() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> kasse.abbuchen(anna, -1L));
        assertThrows(IllegalArgumentException.class, () -> kasse.gutschreiben(anna, -1L));

        assertEquals(START, kasse.guthaben(anna));
    }

    @Test
    @DisplayName("Viele Buchungen hintereinander ergeben die Summe")
    void vieleBuchungen() {
        Kasse kasse = kasse();
        UUID anna = UUID.randomUUID();

        for (int i = 0; i < 10; i++) {
            assertTrue(kasse.abbuchen(anna, 1_000L));
            kasse.gutschreiben(anna, 400L);
        }

        assertEquals(START - 6_000L, kasse.guthaben(anna));
    }
}
