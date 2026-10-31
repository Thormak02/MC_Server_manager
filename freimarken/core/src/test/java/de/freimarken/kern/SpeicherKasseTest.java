package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

@DisplayName("Kasse im Arbeitsspeicher")
class SpeicherKasseTest {

    @Nested
    @DisplayName("Haelt den Kassenvertrag ein")
    class Vertrag extends KasseVertrag {
        @Override
        protected Kasse kasse() {
            return new SpeicherKasse(START);
        }
    }

    @Test
    @DisplayName("Ein Startguthaben von null ist erlaubt - ein negatives nicht")
    void startguthaben() {
        assertEquals(0L, new SpeicherKasse(0L).guthaben(java.util.UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new SpeicherKasse(-1L));
    }
}
