package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Das Warpverzeichnis - die Ableitung aus der Weltenliste. */
class WarpverzeichnisTest {

    private static final List<String> ACHT = List.of("marktstadt", "altfurt", "freistaedte",
            "hafenwerder", "hochwald", "kornmark", "nether", "end");

    @Test
    @DisplayName("Die Reihenfolge der Datei bleibt die Reihenfolge der Vorschlaege")
    void reihenfolgeBleibt() {
        // Die Marktstadt steht oben. Wer /warp m tippt, meint fast immer sie.
        var verzeichnis = Warpverzeichnis.ausWelten(ACHT);

        assertEquals(ACHT, verzeichnis.welten());
        assertEquals(List.of("marktstadt"), verzeichnis.vorschlaege("m"));
        assertEquals(List.of("hafenwerder", "hochwald"), verzeichnis.vorschlaege("h"));
        assertEquals(ACHT, verzeichnis.vorschlaege(""), "ein leerer Anfang schlaegt alles vor");
    }

    @Test
    @DisplayName("Gross und klein ist egal - der Name steht auf einem Schild")
    void grossKleinEgal() {
        var verzeichnis = Warpverzeichnis.ausWelten(ACHT);

        assertEquals("kornmark", verzeichnis.aufgeloest("Kornmark").orElseThrow());
        assertEquals("kornmark", verzeichnis.aufgeloest("KORNMARK").orElseThrow());
        assertEquals("kornmark", verzeichnis.aufgeloest("  kornmark  ").orElseThrow());
        assertEquals(List.of("kornmark"), verzeichnis.vorschlaege("KORN"));
    }

    @Test
    @DisplayName("Was nicht in der Liste steht, ist kein Ziel")
    void unbekanntesZiel() {
        var verzeichnis = Warpverzeichnis.ausWelten(ACHT);

        assertTrue(verzeichnis.aufgeloest("wolkenwerder").isEmpty(),
                "steht nicht in der ausgelieferten Liste - die Startinsel kommt mit Schritt 11");
        assertTrue(verzeichnis.aufgeloest("").isEmpty());
        assertTrue(verzeichnis.aufgeloest(null).isEmpty());
        assertTrue(verzeichnis.aufgeloest("kornmarkt").isEmpty(), "ein Buchstabe daneben");
    }

    @Test
    @DisplayName("Ein Weltname ist reserviert - die Regel aus Plan 4.3")
    void weltnamenSindReserviert() {
        // Heute hat diese Frage noch keinen Gegenpart: Spielereigene Warps gehoeren zur
        // Rangleiter und sind nicht Teil C. Die Regel steht aber hier und nicht im
        // spaeteren Befehl, damit sie eine Stelle hat.
        var verzeichnis = Warpverzeichnis.ausWelten(ACHT);

        assertTrue(verzeichnis.reserviert("kornmark"));
        assertTrue(verzeichnis.reserviert("Kornmark"));
        assertFalse(verzeichnis.reserviert("meinladen"));
    }

    @Test
    @DisplayName("Doppelte Namen fallen weg, der erste bleibt stehen")
    void doppelteFallenWeg() {
        var verzeichnis = Warpverzeichnis.ausWelten(
                List.of("altfurt", "kornmark", "Altfurt", "altfurt"));

        assertEquals(List.of("altfurt", "kornmark"), verzeichnis.welten());
    }

    @Test
    @DisplayName("Leere Eintraege werden uebersprungen, nicht geworfen")
    void leereEintraege() {
        // Das Lesen der Weltenliste hat einen solchen Eintrag schon beklagt. Ein
        // Verzeichnis, das deswegen den Start abbricht, waere die zweite Strafe fuer
        // denselben Tippfehler.
        var verzeichnis = Warpverzeichnis.ausWelten(
                java.util.Arrays.asList("altfurt", "", "   ", null, "kornmark"));

        assertEquals(List.of("altfurt", "kornmark"), verzeichnis.welten());
    }

    @Test
    @DisplayName("Eine leere Liste ergibt ein leeres Verzeichnis")
    void leereListe() {
        var verzeichnis = Warpverzeichnis.ausWelten(List.of());

        assertTrue(verzeichnis.welten().isEmpty());
        assertTrue(verzeichnis.vorschlaege("").isEmpty());
        assertTrue(verzeichnis.aufgeloest("altfurt").isEmpty());
    }
}
