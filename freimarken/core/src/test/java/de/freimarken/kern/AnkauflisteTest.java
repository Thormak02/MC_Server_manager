package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Das Sortiment des Bundesmarktes - Preise, Reihenfolge und die Schranken. */
@DisplayName("Die Ankaufliste")
class AnkauflisteTest {

    private static Ankaufliste.Ankauf ankauf(String material, long pfennige) {
        return new Ankaufliste.Ankauf(material, pfennige);
    }

    @Test
    @DisplayName("Ein Preis kommt heraus, wie er hineinging")
    void preisKommtAn() {
        var liste = Ankaufliste.ausAnkaeufen(List.of(ankauf("stone", 20L),
                ankauf("diamond", 1_100L)));

        assertEquals(20L, liste.pfennige("stone"));
        assertEquals(1_100L, liste.pfennige("diamond"));
        assertEquals(2, liste.anzahl());
        assertFalse(liste.leer());
    }

    @Test
    @DisplayName("Ein Name wird kleingeschrieben und getrimmt - beim Bauen UND beim Fragen")
    void nameWirdNormalisiert() {
        var liste = Ankaufliste.ausAnkaeufen(List.of(ankauf("  STONE  ", 20L)));

        assertEquals(20L, liste.pfennige("stone"));
        assertEquals(20L, liste.pfennige(" Stone "));
        assertEquals(List.of("stone"), liste.materialien());
    }

    @Test
    @DisplayName("Was nicht drinsteht, bringt 0 - und wirft nicht")
    void unbekanntesIstNull() {
        var liste = Ankaufliste.ausAnkaeufen(List.of(ankauf("stone", 20L)));

        assertEquals(0L, liste.pfennige("dirt"));
        assertEquals(0L, liste.pfennige(null));
        assertEquals(0L, liste.pfennige(""));
    }

    @Test
    @DisplayName("Die REIHENFOLGE der Datei bleibt - sie ist die Reihenfolge in /markt")
    void reihenfolgeBleibt() {
        // Map.copyOf verliert sie, und das faellt genau dort auf, wo jemand sie liest:
        // /markt nennt das Sortiment. Ohne diese Zusage stuende es in einer Ordnung, die
        // sich von Lauf zu Lauf aendert - dieselbe Falle wie bei der Lohntafel.
        var liste = Ankaufliste.ausAnkaeufen(List.of(ankauf("zombie_head", 50L),
                ankauf("apple", 60L), ankauf("melon", 70L)));

        assertEquals(List.of("zombie_head", "apple", "melon"), liste.materialien());
    }

    @Test
    @DisplayName("Die erste Zeile zu einem Material gewinnt")
    void dieErsteGewinnt() {
        var liste = Ankaufliste.ausAnkaeufen(List.of(ankauf("stone", 20L),
                ankauf("stone", 99L)));

        assertEquals(20L, liste.pfennige("stone"));
        assertEquals(1, liste.anzahl());
    }

    @Test
    @DisplayName("Eine leere Liste ist erlaubt und heisst: der Bund kauft nichts")
    void leereListe() {
        var liste = Ankaufliste.ausAnkaeufen(List.of());

        assertTrue(liste.leer());
        assertEquals(0, liste.anzahl());
        assertEquals(List.of(), liste.materialien());
        assertEquals(0L, liste.pfennige("stone"));
    }

    @Test
    @DisplayName("Ohne Material, ohne Preis und unter einem Pfennig gibt es keinen Ankauf")
    void pflichtenGelten() {
        assertThrows(IllegalArgumentException.class, () -> ankauf(null, 20L));
        assertThrows(IllegalArgumentException.class, () -> ankauf("  ", 20L));
        // Unter 1 ist kein Ankauf, sondern eine Annahme - und was der Bund nicht kaufen
        // soll, steht gar nicht in der Liste.
        assertThrows(IllegalArgumentException.class, () -> ankauf("stone", 0L));
        assertThrows(IllegalArgumentException.class, () -> ankauf("stone", -1L));
        assertThrows(IllegalArgumentException.class,
                () -> Ankaufliste.ausAnkaeufen(null));
    }

    @Test
    @DisplayName("Zwei Listen mit demselben Inhalt sind gleich - eine mit anderem Preis nicht")
    void gleichheitHaengtAmInhalt() {
        // Daran haengt die Meldung von /freimarken neuladen: Sie vergleicht die alte
        // Liste mit der neuen und nennt den Unterschied. Eine Gleichheit, die nur die
        // ZAHL vergleicht, liesse einen geaenderten Preis stumm durch - und genau das
        // ist die haeufigste Aenderung beim Einstellen.
        var eine = Ankaufliste.ausAnkaeufen(List.of(ankauf("stone", 20L)));
        var gleiche = Ankaufliste.ausAnkaeufen(List.of(ankauf("stone", 20L)));
        var andererPreis = Ankaufliste.ausAnkaeufen(List.of(ankauf("stone", 21L)));

        assertEquals(eine, gleiche);
        assertEquals(eine.hashCode(), gleiche.hashCode());
        // Der EINE Unterschied ist der geprueefte: dasselbe Material, dieselbe Zahl von
        // Zeilen, nur ein anderer Preis.
        assertNotEquals(eine, andererPreis);
    }
}
