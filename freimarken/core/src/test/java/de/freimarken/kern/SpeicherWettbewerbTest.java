package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die fluechtige Fassung haelt den Wettbewerbsvertrag - und zwei Zusagen darueber hinaus.
 */
@DisplayName("Angelwettbewerb im Arbeitsspeicher")
class SpeicherWettbewerbTest {

    @Nested
    @DisplayName("Haelt den Wettbewerbsvertrag ein")
    class Vertrag extends WettbewerbVertrag {
        @Override
        protected Wettbewerbsablage ablage() {
            return new SpeicherWettbewerb();
        }
    }

    @Test
    @DisplayName("Zwei Ablagen teilen nichts")
    void zweiAblagenTeilenNichts() {
        UUID anna = UUID.randomUUID();
        SpeicherWettbewerb erste = new SpeicherWettbewerb();
        erste.meldeAn(anna, WettbewerbVertrag.LAUF, 250L);
        erste.merkeAbgeschlossen(WettbewerbVertrag.LAUF, WettbewerbVertrag.JETZT);

        SpeicherWettbewerb zweite = new SpeicherWettbewerb();
        assertTrue(zweite.zeile(anna, WettbewerbVertrag.LAUF).isEmpty());
        assertFalse(zweite.istAbgeschlossen(WettbewerbVertrag.LAUF),
                "ein statischer Merker waere hier der naheliegende Fehler - und er"
                        + " faellt in einer Probenreihe nie auf");
    }

    @Test
    @DisplayName("Die Zeilen kommen UNSORTIERT - sortiert wird in der Regel")
    void unsortiert() {
        // Eine Ablage, die ihre Reihenfolge zusagt, waere eine zweite Stelle, an der
        // ueber den Sieger entschieden wird. Geprueft wird darum nur, dass alle da sind -
        // und dass die Regel daraus eine Rangliste macht.
        SpeicherWettbewerb ablage = new SpeicherWettbewerb();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        ablage.meldeAn(anna, WettbewerbVertrag.LAUF, 250L);
        ablage.meldeAn(bert, WettbewerbVertrag.LAUF, 250L);
        ablage.zaehlePunkte(anna, WettbewerbVertrag.LAUF, 5);
        ablage.zaehlePunkte(bert, WettbewerbVertrag.LAUF, 50);

        assertEquals(2, ablage.zeilen(WettbewerbVertrag.LAUF).size());
        assertEquals(bert, Wettbewerbsregel
                .rangliste(ablage.zeilen(WettbewerbVertrag.LAUF)).get(0).spieler(),
                "bert hat mehr Punkte - und das entscheidet die REGEL");
    }
}
