package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die fluechtige Fassung haelt den Mietvertrag - und eine Zusage darueber hinaus.
 *
 * <p>Jede Zusage, die umsetzungsfrei zu formulieren ist, steht im {@link MietVertrag} und
 * nicht hier: Sonst erbt die naechste Fassung sie nicht.
 */
@DisplayName("Mietvertraege im Arbeitsspeicher")
class SpeicherMieteTest {

    @Nested
    @DisplayName("Haelt den Mietvertrag ein")
    class Vertrag extends MietVertrag {
        @Override
        protected Mietablage ablage() {
            return new SpeicherMiete();
        }
    }

    @Test
    @DisplayName("Zwei Ablagen teilen nichts")
    void zweiAblagenTeilenNichts() {
        // Eine statische Karte waere hier der naheliegende Fehler, und er faellt in einer
        // Probenreihe nie auf, weil jede Probe ihre eigene UUID nimmt. Im Betrieb waere er
        // ein Laden, der nach dem Wiedereinschalten des Plugins noch vermietet scheint.
        UUID anna = UUID.randomUUID();
        SpeicherMiete erste = new SpeicherMiete();
        erste.schreibe(new Mietvertrag("stand-1", anna, MietVertrag.HEUTE,
                MietVertrag.IN_DREISSIG, 500L, 5_000L));

        assertNull(new SpeicherMiete().fuerEinheit("stand-1"));
        assertNull(new SpeicherMiete().fuerMieter(anna));
    }
}
