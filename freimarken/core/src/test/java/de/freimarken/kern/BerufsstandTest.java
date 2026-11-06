package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was ein Berufsstand aushaelt - und was er nicht durchlaesst.
 *
 * <p>Gleiche Bauart und gleicher Grund wie {@link SpielzeitstandTest}: Ein Stand, der eine
 * unmoegliche Zusammenstellung durchliesse, traegt sie bis in die Platzzaehlung der Regel,
 * und dort sieht sie aus wie eine Einstellung.
 */
class BerufsstandTest {

    private static final UUID ANNA = UUID.randomUUID();
    private static final Instant MITTAGS = Instant.parse("2026-11-06T12:00:00Z");

    @Test
    @DisplayName("Ein leerer Stand nennt den Spieler und sonst nichts")
    void leererStand() {
        Berufsstand stand = Berufsstand.leer(ANNA);

        assertEquals(ANNA, stand.spieler());
        assertEquals(List.of(), stand.berufe());
        assertNull(stand.letztesAblegen());
        assertTrue(stand.nochNieGewaehlt());
    }

    @Test
    @DisplayName("Die Reihenfolge der Wahl bleibt, sie wird nicht sortiert")
    void reihenfolgeBleibt() {
        Berufsstand stand = new Berufsstand(ANNA, List.of("steinmetz", "bergmann"), null);

        assertEquals(List.of("steinmetz", "bergmann"), stand.berufe());
    }

    @Test
    @DisplayName("Eine leere Liste mit Frist ist nicht dasselbe wie ein Neuling")
    void leerMitFristIstKeinNeuling() {
        // Der Unterschied entscheidet, was /beruf stand schreibt: "du hast noch keinen
        // Beruf gewaehlt" oder "du kannst in 14 Stunden wieder waehlen".
        Berufsstand abgelegt = new Berufsstand(ANNA, List.of(), MITTAGS);

        assertTrue(abgelegt.berufe().isEmpty());
        assertFalse(abgelegt.nochNieGewaehlt());
    }

    @Test
    @DisplayName("Ein gehaltener Beruf wird genau so beantwortet, wie er dasteht")
    void haelt() {
        Berufsstand stand = new Berufsstand(ANNA, List.of("bergmann"), null);

        assertTrue(stand.haelt("bergmann"));
        assertFalse(stand.haelt("steinmetz"));
        // Kleingeschrieben kommt der Name aus dem Antrag und aus der Angabe - hier wird
        // NICHT ein zweites Mal normalisiert. Zwei Stellen, die denselben Namen
        // zurechtbiegen, biegen ihn eines Tages verschieden.
        assertFalse(stand.haelt("BERGMANN"));
    }

    @Test
    @DisplayName("Derselbe Beruf zweimal kommt nicht in einen Stand")
    void keineDoppelten() {
        // Sonst zaehlte die Platzzaehlung ihn zweimal: Ein Spieler mit einem Beruf koennte
        // keinen zweiten mehr waehlen, und die Absage saehe wie eine Einstellung aus.
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsstand(ANNA, List.of("bergmann", "bergmann"), null));
    }

    @Test
    @DisplayName("Ein leerer Beruf ist eine Zeile, die niemand lesen kann")
    void keineLeerenNamen() {
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsstand(ANNA, Arrays.asList("bergmann", null), null));
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsstand(ANNA, List.of(""), null));
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsstand(ANNA, List.of("   "), null));
    }

    @Test
    @DisplayName("Ohne Spieler und ohne Liste gibt es keinen Stand")
    void pflichtenGelten() {
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsstand(null, List.of(), null));
        // Eine leere Liste ist der Normalfall, null waere "nicht gelesen" - und das
        // unterschiede die Ablage nicht mehr von einem Neuling.
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsstand(ANNA, null, null));
    }

    @Test
    @DisplayName("Die Liste des Standes laesst sich nicht nachtraeglich aendern")
    void listeIstFest() {
        Berufsstand stand = new Berufsstand(ANNA, List.of("bergmann"), null);

        // Sie geht an die Regel und an den Befehl. Waere sie veraenderbar, koennte eine
        // Zaehlung sie unterwegs umbauen, und der geschriebene Stand waere nicht der
        // gepruefte.
        assertThrows(UnsupportedOperationException.class,
                () -> stand.berufe().add("steinmetz"));
    }
}
