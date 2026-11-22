package de.freimarken.kern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * Die fluechtige Anmeldeablage - sie haelt denselben Vertrag wie die MariaDB-Fassung.
 *
 * <p>Nichts ausser dem geerbten Vertrag, wie bei {@code SpeicherRaengeTest}: Jede Zusage
 * steht EINMAL in {@link AnmeldeVertrag} und gilt damit fuer beide Umsetzungen. Eine
 * eigene Probe hier waere eine Zusage, die nur fuer den fluechtigen Betrieb gilt.
 */
@DisplayName("Die Anmeldungen im Speicher")
class SpeicherAnmeldungenTest {

    @Nested
    @DisplayName("halten den Vertrag")
    class HaltenDenVertrag extends AnmeldeVertrag {

        @Override
        protected Anmeldeablage ablage() {
            return new SpeicherAnmeldungen();
        }
    }
}
