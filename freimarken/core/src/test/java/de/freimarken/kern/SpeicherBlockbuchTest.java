package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die fluechtige Fassung haelt das Blockbuch - und drei Zusagen darueber hinaus.
 *
 * <p>Jede Zusage, die umsetzungsfrei zu formulieren ist, steht im
 * {@link BlockbuchVertrag} und nicht hier: Sonst erbt die naechste Fassung sie nicht.
 */
@DisplayName("Blockbuch im Arbeitsspeicher")
class SpeicherBlockbuchTest {

    @Nested
    @DisplayName("Haelt den Blockbuchvertrag ein")
    class Vertrag extends BlockbuchVertrag {
        @Override
        protected Blockbuchablage ablage() {
            return new SpeicherBlockbuch();
        }
    }

    @Test
    @DisplayName("Zwei Buecher teilen nichts")
    void zweiBuecherTeilenNichts() {
        // Eine statische Liste waere hier der naheliegende Fehler, und er faellt in einer
        // Probenreihe nie auf, weil jede Probe ihre eigene UUID nimmt - beim Blockbuch
        // aber fragen zwei Proben denselben ORT, und dann schieben fremde Zeilen die
        // eigenen aus einer Antwort mit Grenze heraus.
        SpeicherBlockbuch erstes = new SpeicherBlockbuch();
        erstes.schreibe(List.of(BlockbuchVertrag.tat(BlockbuchVertrag.WANN,
                UUID.randomUUID(), BlockbuchVertrag.ORT, Blocktat.ABBAUEN, true,
                "oak_log")));

        SpeicherBlockbuch zweites = new SpeicherBlockbuch();

        assertEquals(1, erstes.anzahl());
        assertEquals(0, zweites.anzahl());
        assertTrue(zweites.amOrt(BlockbuchVertrag.ORT, 20).isEmpty());
    }

    @Test
    @DisplayName("Jede Zeile bekommt eine EIGENE Nummer, und sie steigt")
    void nummernSteigenUndSindEigen() {
        // Diese Zusage steht NICHT im geteilten Vertrag: AUTO_INCREMENT in MariaDB
        // ueberlebt einen Neustart nicht zuverlaessig - sein Zaehler beginnt dann bei
        // MAX(id) + 1, also kann eine Nummer wiederkommen. Fuer das Blockbuch ist das
        // harmlos, weil niemand eine Zeile nach ihrer Nummer holt; hier, wo es haltbar
        // ist, wird es geprueft.
        SpeicherBlockbuch buch = new SpeicherBlockbuch();
        UUID anna = UUID.randomUUID();

        buch.schreibe(List.of(
                BlockbuchVertrag.tat(BlockbuchVertrag.WANN, anna, BlockbuchVertrag.ORT,
                        Blocktat.SETZEN, true, "erst"),
                BlockbuchVertrag.tat(BlockbuchVertrag.WANN, anna, BlockbuchVertrag.ORT,
                        Blocktat.ABBAUEN, true, "dann")));

        List<Blockzeile> gelesen = buch.amOrt(BlockbuchVertrag.ORT, 20);
        assertEquals(2, gelesen.size());
        assertNotEquals(gelesen.get(0).id(), gelesen.get(1).id(),
                "zwei Zeilen am selben Ort, zur selben Zeit, vom selben Spieler -"
                        + " sie unterscheiden sich NUR in der Nummer, und genau die"
                        + " entscheidet ihre Reihenfolge");
        assertTrue(gelesen.get(0).id() > gelesen.get(1).id(),
                "die neuere Zeile traegt die hoehere Nummer");
    }

    @Test
    @DisplayName("Eine geloeschte Nummer kommt nie wieder")
    void geloeschteNummerKommtNichtWieder() {
        // Der Zaehler laeuft weiter, auch wenn das Buch leer ist. Sonst traegt die
        // naechste Zeile die Nummer einer weggeraeumten - im Arbeitsspeicher
        // vermeidbar, und wo es vermeidbar ist, wird es zugesagt.
        SpeicherBlockbuch buch = new SpeicherBlockbuch();
        UUID anna = UUID.randomUUID();

        buch.schreibe(List.of(BlockbuchVertrag.tat(BlockbuchVertrag.WANN, anna,
                BlockbuchVertrag.ORT, Blocktat.SETZEN, true, "alt")));
        long ersteNummer = buch.amOrt(BlockbuchVertrag.ORT, 20).get(0).id();
        assertEquals(1, buch.raeumeAuf(BlockbuchVertrag.WANN.plusSeconds(60), 100));

        buch.schreibe(List.of(BlockbuchVertrag.tat(BlockbuchVertrag.WANN.plusSeconds(120),
                anna, BlockbuchVertrag.ORT, Blocktat.SETZEN, true, "neu")));

        assertNotEquals(ersteNummer, buch.amOrt(BlockbuchVertrag.ORT, 20).get(0).id(),
                "die weggeraeumte Nummer darf nicht erneut vergeben werden");
    }
}
