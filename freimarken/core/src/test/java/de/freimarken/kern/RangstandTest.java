package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Der Stand eines Spielers auf der Leiter - und der EINE Rueckfall nach unten. */
@DisplayName("Der Rangstand")
class RangstandTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-06T12:00:00Z");

    @Test
    @DisplayName("Ein leerer Stand faellt auf die UNTERSTE Stufe zurueck")
    void leerFaelltAufUnterste() {
        Rangstand stand = Rangstand.leer(UUID.randomUUID());

        assertFalse(stand.aufgestiegen());
        // DIE EINE STELLE, an der aus "keine Zeile" eine Stufe wird. Haette jeder Leser
        // seinen eigenen Rueckfall, waere der erste, der ihn vergisst, ein Spieler ohne
        // jedes Rangrecht - und das sieht im Spiel aus wie die Regel.
        assertEquals("reisender",
                stand.stufeIn(Rangwerte.VORGABE).schluessel());
        assertTrue(stand.bekanntIn(Rangwerte.VORGABE),
                "wer keinen Rang hat, hat auch keinen unbekannten");
    }

    @Test
    @DisplayName("Ein Rang, den die Leiter nicht mehr kennt, faellt ebenfalls zurueck")
    void unbekannterRangFaelltZurueck() {
        UUID anna = UUID.randomUUID();
        Rangstand stand = new Rangstand(anna, "fuerst", MITTAGS);

        // Wer eine Stufe aus der Datei nimmt, laesst Zeilen stehen, die auf sie zeigen.
        // Ohne Rueckfall bekaemen gerade die Aufgestiegenen gar nichts - und still
        // bliebe es auch noch.
        assertEquals("reisender", stand.stufeIn(Rangwerte.VORGABE).schluessel());
        assertFalse(stand.bekanntIn(Rangwerte.VORGABE),
                "und DAS muss der Aufrufer melden koennen");
    }

    @Test
    @DisplayName("Ohne jede Leiter gibt es keine Stufe - und keinen Wurf")
    void ohneLeiterKeineStufe() {
        Rangstand stand = Rangstand.leer(UUID.randomUUID());

        // Eine leere Leiter heisst "keine Raenge", und das ist erlaubt. Ein Wurf hier
        // waere ein Absturz beim Beitritt auf einem Server, der gar keine Raenge will.
        assertNull(stand.stufeIn(Rangwerte.VORGABE.mitLeiter(List.of())));
    }

    @Test
    @DisplayName("Rang und Zeitpunkt gehoeren zusammen - beide oder keiner")
    void halberStandWirft() {
        UUID anna = UUID.randomUUID();

        // Ein halber Stand kaeme aus einer Zeile, die es in der Tabelle nicht geben kann
        // (beide Spalten sind NOT NULL). Er liefe erst beim Schreiben auf, als
        // Datenbankfehler mitten im Spiel.
        assertThrows(IllegalArgumentException.class,
                () -> new Rangstand(anna, "siedler", null));
        assertThrows(IllegalArgumentException.class,
                () -> new Rangstand(anna, null, MITTAGS));
    }

    @Test
    @DisplayName("Ein Rang wird kleingeschrieben gehalten")
    void kleingeschrieben() {
        Rangstand stand = new Rangstand(UUID.randomUUID(), "  Siedler  ", MITTAGS);

        assertEquals("siedler", stand.rang());
        assertEquals("siedler", stand.stufeIn(Rangwerte.VORGABE).schluessel());
    }

    @Test
    @DisplayName("Ein Rang, der laenger ist als die Spalte, wird abgewiesen")
    void zuLangerRang() {
        UUID anna = UUID.randomUUID();
        String zuLang = "a".repeat(Rangwerte.HOECHSTE_SCHLUESSELLAENGE + 1);

        // Die Pruefung steht hier noch einmal und nicht nur in Rangwerte.Stufe: Ein
        // Stand kommt auch aus der ABLAGE, und eine von Hand gefuellte Datenbank ist
        // keine Leiter.
        assertThrows(IllegalArgumentException.class,
                () -> new Rangstand(anna, zuLang, MITTAGS));
    }

    @Test
    @DisplayName("Ein Rang mit Zeichen, die es nicht gibt, wird abgewiesen")
    void unmoeglicherRang() {
        UUID anna = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> new Rangstand(anna, "Sie dler", MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> new Rangstand(anna, "1siedler", MITTAGS));
    }

    @Test
    @DisplayName("Ohne Spieler gibt es keinen Stand")
    void ohneSpieler() {
        assertThrows(IllegalArgumentException.class,
                () -> new Rangstand(null, "siedler", MITTAGS));
    }

    @Test
    @DisplayName("Die Stufe kommt aus der Leiter, die man hereingibt - nicht aus einer festen")
    void stufeAusDerUebergebenenLeiter() {
        UUID anna = UUID.randomUUID();
        var eigene = Rangwerte.VORGABE.mitLeiter(List.of(
                new Rangwerte.Stufe("gast", Map.of(), Map.of("parzellen", 1), List.of()),
                new Rangwerte.Stufe("siedler", Map.of(Bedingungsart.PARZELLEN, 1L),
                        Map.of("parzellen", 9), List.of())));

        // Der Rueckfall nimmt die unterste Stufe DIESER Leiter und nicht "reisender":
        // Wer die Vorgabe fest einbaute, gaebe auf einem Server mit eigenen Raengen
        // einen Rang aus, den es dort nicht gibt.
        assertEquals("gast", Rangstand.leer(anna).stufeIn(eigene).schluessel());
        assertEquals(9, new Rangstand(anna, "siedler", MITTAGS).stufeIn(eigene)
                .zahl("parzellen").orElseThrow());
    }
}
