package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die fluechtige Fassung haelt den Truhenvertrag - und eine Zusage darueber hinaus.
 *
 * <p>Jede Zusage, die umsetzungsfrei zu formulieren ist, steht im {@link TruhenVertrag}
 * und nicht hier: Sonst erbt die naechste Fassung sie nicht. Was hier steht, gilt nur fuer
 * die Karte im Arbeitsspeicher.
 */
@DisplayName("Entnahmen der Freien Truhe im Arbeitsspeicher")
class SpeicherTruheTest {

    @Nested
    @DisplayName("Haelt den Truhenvertrag ein")
    class Vertrag extends TruhenVertrag {
        @Override
        protected Truhenablage ablage() {
            return new SpeicherTruhe();
        }
    }

    @Test
    @DisplayName("Zwei Ablagen teilen nichts")
    void zweiAblagenTeilenNichts() {
        // Eine statische Karte waere hier der naheliegende Fehler, und er faellt in einer
        // Probenreihe nie auf, weil jede Probe ihre eigene UUID nimmt. Im Betrieb waere
        // er ein Deckel, der ein Wiedereinschalten des Plugins ueberlebt - und das ist
        // genau die Zusage, die diese Fassung NICHT hat.
        UUID anna = UUID.randomUUID();
        SpeicherTruhe erste = new SpeicherTruhe();
        erste.merke(anna, TruhenVertrag.HEUTE, 16);

        assertEquals(0, new SpeicherTruhe().behalten(anna, TruhenVertrag.HEUTE));
    }
}
