package de.freimarken.kern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * Die fluechtige Fassung haelt nichts anderes als den Vertrag - darum steht hier nichts
 * anderes.
 *
 * <p>Gleiche Bauart wie {@link SpeicherSpielzeitTest}: Jede Zusage, die umsetzungsfrei
 * formuliert werden kann, steht in {@link BerufeVertrag} und gilt damit auch fuer
 * {@code MariaBerufe}. Eine Probe, die hier stuende, erbte die Datenbankfassung nicht -
 * und genau dort faellt ein Unterschied im Betrieb auf und nicht im Testlauf.
 */
@DisplayName("Berufe im Arbeitsspeicher")
class SpeicherBerufeTest {

    @Nested
    @DisplayName("Haelt den Berufsvertrag ein")
    class Vertrag extends BerufeVertrag {
        @Override
        protected Berufsablage ablage() {
            return new SpeicherBerufe();
        }
    }
}
