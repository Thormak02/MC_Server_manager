package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Zahlen des Bundesmarktes und ihre Schranken. */
@DisplayName("Die Marktwerte")
class MarktwerteTest {

    @Test
    @DisplayName("Die Vorgabe traegt ein LEERES Sortiment - mit Absicht")
    void vorgabeIstLeer() {
        // Dieselbe Entscheidung wie bei der Lohnliste und mit demselben Grund: Eine
        // Tabelle im Programm waere eine zweite Abschrift der ausgelieferten Datei, und
        // zwei Abschriften laufen auseinander. Fehlt die Liste, kauft der Bund nichts -
        // die sichere Richtung, denn ein geratener Ankaufpreis macht Geld.
        assertTrue(Marktwerte.VORGABE.ankauf().leer());
        assertEquals(5_000L, Marktwerte.VORGABE.tagesdeckel());
    }

    @Test
    @DisplayName("Der Marktdeckel ist ein VIERTEL des Verdienstdeckels")
    void einViertelDesVerdienstdeckels() {
        // Die Rolle in einer Zahl: Der Markt ist die Verwertung des Erarbeiteten und
        // keine zweite Arbeit. Waeren beide gleich, verdiente ein Spieler am selben Block
        // zweimal den vollen Tagessatz - einmal beim Abbauen, einmal beim Verkaufen.
        assertEquals(Verdienstwerte.VORGABE_TAGESDECKEL / 4L,
                Marktwerte.VORGABE_TAGESDECKEL);
    }

    @Test
    @DisplayName("Ein Deckel von 0 gilt nicht - abschalten geht ueber das Sortiment")
    void deckelNullGiltNicht() {
        for (long getippt : new long[] {0L, -1L, Marktwerte.HOECHSTER_TAGESDECKEL + 1L}) {
            assertThrows(IllegalArgumentException.class,
                    () -> Marktwerte.VORGABE.mitDeckel(getippt),
                    String.valueOf(getippt));
            assertFalse(Marktwerte.brauchbarerDeckel(getippt), String.valueOf(getippt));
        }
        assertTrue(Marktwerte.brauchbarerDeckel(1L));
        assertTrue(Marktwerte.brauchbarerDeckel(Marktwerte.HOECHSTER_TAGESDECKEL));
    }

    @Test
    @DisplayName("Ohne Ankaufliste gibt es keinen Markt")
    void ohneListe() {
        assertThrows(IllegalArgumentException.class,
                () -> Marktwerte.VORGABE.mitAnkauf(null));
        assertThrows(IllegalArgumentException.class,
                () -> new Marktwerte(5_000L, null));
    }

    @Test
    @DisplayName("Die Abwandler lassen alles andere stehen")
    void abwandlerLassenDenRestStehen() {
        // Dieselbe Lehre wie bei den Verdienstwerten: Dieser Satz waechst, und eine
        // Probe, die ihn ganz baut, bricht beim naechsten Teil - nicht weil ihre Frage
        // falsch waere, sondern weil ein Teil dazugekommen ist.
        Ankaufliste liste = Ankaufliste.ausAnkaeufen(List.of(
                new Ankaufliste.Ankauf("stone", 20L)));

        var mitDeckel = Marktwerte.VORGABE.mitDeckel(123L);
        assertEquals(123L, mitDeckel.tagesdeckel());
        assertEquals(Marktwerte.VORGABE.ankauf(), mitDeckel.ankauf());

        var mitListe = Marktwerte.VORGABE.mitAnkauf(liste);
        assertEquals(liste, mitListe.ankauf());
        assertEquals(Marktwerte.VORGABE_TAGESDECKEL, mitListe.tagesdeckel());
    }

    @Test
    @DisplayName("Zwei Saetze mit gleichem Inhalt sind gleich - einer mit anderem Preis nicht")
    void gleichheitHaengtAmInhalt() {
        // Daran haengt die Meldung von /freimarken neuladen.
        var eine = Marktwerte.VORGABE.mitAnkauf(Ankaufliste.ausAnkaeufen(List.of(
                new Ankaufliste.Ankauf("stone", 20L))));
        var gleiche = Marktwerte.VORGABE.mitAnkauf(Ankaufliste.ausAnkaeufen(List.of(
                new Ankaufliste.Ankauf("stone", 20L))));
        var andere = Marktwerte.VORGABE.mitAnkauf(Ankaufliste.ausAnkaeufen(List.of(
                new Ankaufliste.Ankauf("stone", 21L))));

        assertEquals(eine, gleiche);
        assertNotEquals(eine, andere);
    }
}
