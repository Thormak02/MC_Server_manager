package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die fluechtige Fassung haelt den Wolkenvertrag - und eine Zusage darueber hinaus.
 *
 * <p>Jede Zusage, die umsetzungsfrei zu formulieren ist, steht im {@link WolkenVertrag}
 * und nicht hier: Sonst erbt die naechste Fassung sie nicht.
 */
@DisplayName("Der Inselzustand im Arbeitsspeicher")
class SpeicherWolkenTest {

    @Nested
    @DisplayName("Haelt den Wolkenvertrag ein")
    class Vertrag extends WolkenVertrag {

        /**
         * Die Nummern sind frei waehlbar - diese Fassung kennt keinen Fremdschluessel.
         *
         * <p>Ein Zaehler und keine festen Zahlen: Zwei Proben derselben Reihe liefen
         * sonst auf derselben Insel, und eine davon saehe den Stand der anderen.
         */
        private final AtomicLong naechste = new AtomicLong(1L);

        @Override
        protected Wolkenablage ablage() {
            return new SpeicherWolken();
        }

        @Override
        protected long frischeInsel() {
            return naechste.getAndIncrement();
        }
    }

    @Test
    @DisplayName("Zwei Ablagen teilen nichts")
    void zweiAblagenTeilenNichts() {
        // Eine statische Karte waere hier der naheliegende Fehler, und er faellt in einer
        // Probenreihe nie auf, weil jede Probe ihre eigene Nummer nimmt. Im Betrieb waere
        // er eine Insel-Stufe, die ein Wiedereinschalten des Plugins ueberlebt - und das
        // ist genau die Zusage, die diese Fassung NICHT hat.
        SpeicherWolken erste = new SpeicherWolken();
        erste.erfuellen(7L, "erster_stein");

        assertEquals(0, new SpeicherWolken().stand(7L).stufe());
    }
}
