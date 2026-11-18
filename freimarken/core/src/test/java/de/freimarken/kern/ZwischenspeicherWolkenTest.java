package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Der Zwischenspeicher haelt denselben Vertrag - und drei Zusagen darueber hinaus.
 *
 * <p>Dass der Vertrag auch hier laeuft, ist der Zweck der Uebung: Ein Zwischenspeicher,
 * der sich anders verhaelt als die Ablage dahinter, ist kein Zwischenspeicher, sondern ein
 * zweiter Stand.
 */
@DisplayName("Der Zwischenspeicher der Inselstaende")
class ZwischenspeicherWolkenTest {

    private static final Instant GEZAEHLT = Instant.parse("2026-11-18T12:00:00Z");

    @Nested
    @DisplayName("Haelt den Wolkenvertrag ein")
    class Vertrag extends WolkenVertrag {

        private final AtomicLong naechste = new AtomicLong(1L);

        @Override
        protected Wolkenablage ablage() {
            return new ZwischenspeicherWolken(new SpeicherWolken());
        }

        @Override
        protected long frischeInsel() {
            return naechste.getAndIncrement();
        }
    }

    @Test
    @DisplayName("fuellen() holt, was schon in der Ablage steht")
    void fuellenHolt() {
        // Der Startfall: Die Staende sind da, bevor der Zwischenspeicher gebaut wird.
        // Ohne diesen Zug saehe der Generator nach jedem Neustart lauter Stufe 0.
        SpeicherWolken wahrheit = new SpeicherWolken();
        wahrheit.erfuellen(7L, "erster_stein");
        wahrheit.punkteSetzen(7L, 99L, GEZAEHLT);

        ZwischenspeicherWolken speicher = new ZwischenspeicherWolken(wahrheit);
        assertEquals(0, speicher.groesse(), "vor dem Fuellen ist er leer");
        speicher.fuellen();

        assertEquals(1, speicher.groesse());
        assertEquals(1, speicher.stand(7L).stufe());
        assertEquals(99L, speicher.stand(7L).punkte());
    }

    @Test
    @DisplayName("Jeder Schreibzug geht WIRKLICH in die Ablage")
    void schreibzugGehtDurch() {
        // Die Richtung, in der ein Zwischenspeicher lautlos falsch wird: Er antwortet
        // richtig, und nach dem Neustart ist alles weg. Geprueft wird daher nicht an ihm,
        // sondern an der Ablage dahinter.
        SpeicherWolken wahrheit = new SpeicherWolken();
        ZwischenspeicherWolken speicher = new ZwischenspeicherWolken(wahrheit);

        speicher.biomSetzen(3L, "swamp");
        speicher.punkteSetzen(3L, 12L, GEZAEHLT);
        speicher.erfuellen(3L, "erster_stein");

        assertEquals("swamp", wahrheit.stand(3L).biom());
        assertEquals(12L, wahrheit.stand(3L).punkte());
        assertEquals(1, wahrheit.stand(3L).stufe());

        speicher.entfernen(3L);
        assertEquals(0, wahrheit.alle().size(), "und das Loeschen geht auch durch");
    }

    @Test
    @DisplayName("Die ANTWORT auf erfuellen kommt aus der Ablage, nicht aus der Karte")
    void antwortKommtAusDerAblage() {
        // An dieser Antwort haengt die Belohnung. Steht die Zeile in der Ablage schon,
        // muss auch ein frisch gebauter Zwischenspeicher nein sagen - und zwar OHNE
        // vorheriges fuellen(), denn genau dann ist seine Karte leer und eine eigene
        // Pruefung sagte faelschlich ja.
        SpeicherWolken wahrheit = new SpeicherWolken();
        wahrheit.erfuellen(4L, "erster_stein");

        ZwischenspeicherWolken speicher = new ZwischenspeicherWolken(wahrheit);

        assertFalse(speicher.erfuellen(4L, "erster_stein"),
                "sonst zahlte jeder Neustart die Belohnung noch einmal");
        assertTrue(speicher.erfuellen(4L, "erstes_brot"));
        assertEquals(2, speicher.stand(4L).stufe(),
                "und der nachgezogene Stand kennt BEIDE Zeilen");
    }
}
