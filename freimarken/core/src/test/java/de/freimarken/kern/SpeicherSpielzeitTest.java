package de.freimarken.kern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * Die fluechtige Fassung haelt nichts anderes als den Vertrag - darum steht hier nichts
 * anderes.
 *
 * <p>Zwei Proben standen einmal hier und sind in {@link SpielzeitVertrag} gewandert, weil sie
 * umsetzungsfrei formuliert waren und die Datenbankfassung ihre eigene Kopie daneben hatte:
 * "ein null-Eintrag hinterlaesst nichts" ({@link SpielzeitVertrag#allesOderNichts()}) und
 * "ein Ueberlauf bricht ab" ({@link SpielzeitVertrag#ueberlaufBrichtAb()}). Eine Zusage, die
 * jede Fassung fuer sich prueft, erbt die naechste Fassung nicht.
 */
@DisplayName("Spielzeit im Arbeitsspeicher")
class SpeicherSpielzeitTest {

    @Nested
    @DisplayName("Haelt den Spielzeitvertrag ein")
    class Vertrag extends SpielzeitVertrag {
        @Override
        protected Spielzeitablage ablage() {
            return new SpeicherSpielzeit();
        }
    }
}
