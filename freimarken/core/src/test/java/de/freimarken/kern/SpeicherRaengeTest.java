package de.freimarken.kern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

/**
 * Die fluechtige Fassung haelt nichts anderes als den Vertrag - darum steht hier nichts
 * anderes.
 *
 * <p>Gleiche Bauart wie {@link SpeicherBerufeTest}: Jede Zusage, die umsetzungsfrei
 * formuliert werden kann, steht in {@link RangVertrag} und gilt damit auch fuer
 * {@code MariaRaenge}. Eine Probe, die hier stuende, erbte die Datenbankfassung nicht -
 * und genau dort faellt ein Unterschied im Betrieb auf und nicht im Testlauf.
 */
@DisplayName("Raenge im Arbeitsspeicher")
class SpeicherRaengeTest {

    @Nested
    @DisplayName("Haelt den Rangvertrag ein")
    class Vertrag extends RangVertrag {
        @Override
        protected Rangablage ablage() {
            return new SpeicherRaenge();
        }
    }
}
