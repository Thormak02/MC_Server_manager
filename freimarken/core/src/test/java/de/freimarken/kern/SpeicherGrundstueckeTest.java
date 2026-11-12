package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Vertrag aus {@link GrundstuecksablageVertrag}, geprueft an der Fassung im
 * Arbeitsspeicher.
 */
@DisplayName("Grundstuecke im Arbeitsspeicher")
class SpeicherGrundstueckeTest extends GrundstuecksablageVertrag {

    /** Die Nummern steigen, damit zwei Staedte nie dieselbe bekommen. */
    private long naechste = 100L;

    @Override
    protected Grundstuecksablage ablage() {
        return new SpeicherGrundstuecke();
    }

    /**
     * Eine blosse Zahl - diese Fassung kennt die Parzellen nicht.
     *
     * <p>Genau darin liegt der Unterschied zu {@code MariaGrundstueckeTest}, und der
     * Vertrag macht ihn sichtbar statt ihn zu verstecken: Dort legt dieselbe Methode
     * eine echte Freistadt an, weil {@code fk_grundstueck_stadt} nichts anderes
     * zulaesst.
     */
    @Override
    protected long stadtAnlegen(String welt, int feldX, int feldZ) {
        return naechste++;
    }

    /** Eine Uhr, die sich stellen laesst - wie in {@code ZwischenspeicherLagerTest}. */
    private static final class StellUhr extends Clock {
        private Instant jetzt = Instant.parse("2026-11-12T12:00:00Z");

        void weiter(Duration wie) {
            jetzt = jetzt.plus(wie);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return jetzt;
        }
    }

    /**
     * Der Erwerbszeitpunkt wandert beim Verkauf MIT - scharf gemessen.
     *
     * <p>Die Vertragsprobe kann nur sagen "nicht vorher": Sie bekommt die Ablage
     * fertig und kennt ihre Uhr nicht. Hier ist die Uhr verstellbar, und damit ist der
     * Unterschied messbar - ein JAHR zwischen Zuschnitt und Verkauf.
     *
     * <p><b>Ein Jahr und keine Sekunde</b>, weil die Verfallsregel aus Plan 16 in
     * Monaten rechnet: Bliebe der alte Zeitpunkt stehen, waere das gekaufte Grundstueck
     * im selben Augenblick ueberaltert, in dem es den Besitzer wechselt.
     */
    @Test
    @DisplayName("Der Erwerbszeitpunkt ist der des KAUFS und nicht der des Zuschnitts")
    void erwerbszeitpunktIstDerDesKaufs() {
        StellUhr uhr = new StellUhr();
        SpeicherGrundstuecke ablage = new SpeicherGrundstuecke(uhr);
        long stadt = stadtAnlegen(WELT, 0, 0);
        java.util.UUID anna = java.util.UUID.randomUUID();
        java.util.UUID bert = java.util.UUID.randomUUID();
        Grundstueck zugeschnitten = ablage.anlegen(WELT, stadt,
                Zellquader.eineZelle(1, 1), anna, 0L);
        Instant beimZuschnitt = zugeschnitten.erworbenAm();
        ablage.anbieten(zugeschnitten.id(), 500L);

        uhr.weiter(Duration.ofDays(365L));
        Grundstueck gekauft = ablage.eigentuemerSetzen(zugeschnitten.id(), bert, 500L);

        assertEquals(uhr.instant(), gekauft.erworbenAm(),
                "erworben hat es der, dem es jetzt gehoert - heute");
        assertTrue(gekauft.erworbenAm().isAfter(beimZuschnitt),
                "ein Jahr spaeter, nicht gleichzeitig");
        assertEquals(gekauft, ablage.anZelle(WELT, 1, 1).orElseThrow(),
                "und dasselbe steht in der Ablage");
    }
}
