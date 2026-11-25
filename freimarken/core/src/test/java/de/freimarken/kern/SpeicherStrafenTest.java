package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Strafen im Arbeitsspeicher - gegen den {@link StrafVertrag}.
 *
 * <p>Dazu die zwei Zusagen, die nur diese Fassung hat: Die Nummern zaehlen von 1 hoch,
 * und sie werden nie wiederverwendet.
 */
class SpeicherStrafenTest extends StrafVertrag {

    @Override
    protected Strafablage ablage() {
        return new SpeicherStrafen();
    }

    @Test
    @DisplayName("Die erste Strafe hat die Nummer 1")
    void ersteNummerIstEins() {
        UUID bert = UUID.randomUUID();

        assertEquals(1L, new SpeicherStrafen()
                .lege(bert, Strafart.RAUSWURF, "Spam", null, MITTAGS, null).nummer());
    }

    @Test
    @DisplayName("Die Nummern zaehlen fortlaufend hoch")
    void nummernZaehlenHoch() {
        SpeicherStrafen ablage = new SpeicherStrafen();
        UUID bert = UUID.randomUUID();

        for (long erwartet = 1L; erwartet <= 5L; erwartet++) {
            assertEquals(erwartet, ablage.lege(bert, Strafart.RAUSWURF,
                    "Vorfall " + erwartet, null, MITTAGS, null).nummer());
        }
    }

    @Test
    @DisplayName("Eine zweite Ablage faengt wieder bei 1 an - sie vergisst alles")
    void zweiteAblageFaengtNeuAn() {
        UUID bert = UUID.randomUUID();
        new SpeicherStrafen().lege(bert, Strafart.BANN, "erste Ablage", null, MITTAGS,
                EINE_STUNDE);

        // Das ist der Zweck dieser Fassung und ihr Mangel in einem: Ein Neustart
        // entsperrt jeden gebannten Spieler. Wer sie im Betrieb einsetzt, soll das
        // hier lesen und nicht am naechsten Morgen entdecken.
        assertEquals(1L, new SpeicherStrafen()
                .lege(bert, Strafart.BANN, "zweite Ablage", null, MITTAGS, EINE_STUNDE)
                .nummer());
    }

    @Test
    @DisplayName("Eine Strafe ueber die Hoechstdauer hinaus nimmt die Ablage an")
    void ueberlangeWirdAngenommen() {
        SpeicherStrafen ablage = new SpeicherStrafen();
        UUID bert = UUID.randomUUID();

        // Die Ablage urteilt nicht - der Riegel gegen die zu lange Dauer steht im
        // Strafbuch. Diese Probe haelt die TRENNUNG fest: Wer sie hier einbaute,
        // haette zwei Riegel fuer denselben Fall, und keiner liesse sich pruefen.
        Strafe lang = ablage.lege(bert, Strafart.BANN, "neun Jahre", null, MITTAGS,
                new Strafdauer(Duration.ofDays(3285L)));

        assertTrue(lang.giltNoch(MITTAGS.plus(Duration.ofDays(3000L))));
    }
}
