package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die fluechtige Fassung haelt den Losvertrag - und zwei Zusagen darueber hinaus.
 *
 * <p>Jede Zusage, die umsetzungsfrei zu formulieren ist, steht im {@link LosVertrag} und
 * nicht hier: Sonst erbt die naechste Fassung sie nicht. Was hier steht, gilt nur fuer die
 * Karte im Arbeitsspeicher.
 */
@DisplayName("Lose im Arbeitsspeicher")
class SpeicherLoseTest {

    @Nested
    @DisplayName("Haelt den Losvertrag ein")
    class Vertrag extends LosVertrag {
        @Override
        protected Losablage ablage() {
            return new SpeicherLose();
        }
    }

    @Test
    @DisplayName("Zwei Ablagen teilen nichts")
    void zweiAblagenTeilenNichts() {
        // Eine statische Karte waere hier der naheliegende Fehler, und er faellt in einer
        // Probenreihe nie auf, weil jede Probe ihre eigene UUID nimmt. Im Betrieb waere
        // er eine Loszahl, die ein Wiedereinschalten des Plugins ueberlebt.
        UUID anna = UUID.randomUUID();
        SpeicherLose erste = new SpeicherLose();
        erste.zaehleEins(anna, LosVertrag.HEUTE);

        assertEquals(0, new SpeicherLose().lies(anna, LosVertrag.HEUTE).loseHeute());
    }

    @Test
    @DisplayName("Der Textschluessel verwechselt keine zwei Felder")
    void schluesselIstEindeutig() {
        // Diese Fassung baut ihren Schluessel als Text aus UUID und Datum. Ein Trenner,
        // der in einem der beiden Teile vorkommen kann, waere die Falle: Dann liesse sich
        // ein Spieler-Tag-Paar so waehlen, dass es dasselbe Textstueck ergibt wie ein
        // anderes, und zwei Spieler teilten eine Loszahl. Geprueft mit UUIDs, die sich
        // nur im letzten Zeichen unterscheiden - das ist der Fall, bei dem ein
        // abschneidender Schluessel zusammenfaellt.
        UUID eine = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID andere = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        SpeicherLose lose = new SpeicherLose();

        lose.zaehleEins(eine, LosVertrag.HEUTE);
        lose.zaehleEins(eine, LosVertrag.HEUTE);
        lose.zaehleEins(andere, LosVertrag.HEUTE);

        assertEquals(2, lose.lies(eine, LosVertrag.HEUTE).loseHeute());
        assertEquals(1, lose.lies(andere, LosVertrag.HEUTE).loseHeute());
    }
}
