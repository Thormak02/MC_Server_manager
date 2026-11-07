package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die fluechtige Fassung haelt den Bonusvertrag - und eine Zusage darueber hinaus.
 *
 * <p>Jede Zusage, die umsetzungsfrei zu formulieren ist, steht im {@link BonusVertrag} und
 * nicht hier: Sonst erbt die naechste Fassung sie nicht. Was hier steht, gilt nur fuer die
 * Karte im Arbeitsspeicher.
 */
@DisplayName("Tagesbonus im Arbeitsspeicher")
class SpeicherBonusTest {

    @Nested
    @DisplayName("Haelt den Bonusvertrag ein")
    class Vertrag extends BonusVertrag {
        @Override
        protected Bonusablage ablage() {
            return new SpeicherBonus();
        }
    }

    @Test
    @DisplayName("Zwei Ablagen teilen nichts")
    void zweiAblagenTeilenNichts() {
        // Eine statische Karte waere hier der naheliegende Fehler, und er faellt in einer
        // Probenreihe nie auf, weil jede Probe ihre eigene UUID nimmt. Im Betrieb waere
        // er eine Serie, die ein Wiedereinschalten des Plugins ueberlebt.
        UUID anna = UUID.randomUUID();
        SpeicherBonus erste = new SpeicherBonus();
        erste.schreibe(new Bonusstand(anna, BonusVertrag.HEUTE, 5, 5));

        assertEquals(0, new SpeicherBonus().lies(anna).serie());
    }
}
