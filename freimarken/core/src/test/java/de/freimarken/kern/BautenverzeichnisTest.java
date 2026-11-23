package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Das Verzeichnis der Platzhalterbauten")
class BautenverzeichnisTest {

    private static Platzhalterbau bau(String name, int x) {
        return new Platzhalterbau(name, new Quader(x, -61, 2, x + 8, -53, 10),
                Richtung.NORD,
                Baustil.BACKSTEIN, Dachart.ZIEGEL);
    }

    @Test
    @DisplayName("Die Reihenfolge der Datei bleibt die Reihenfolge des Verzeichnisses")
    void reihenfolgeBleibt() {
        // Ein Bericht, dessen Zeilen bei jedem Start anders stehen, ist einer, den
        // niemand vergleicht.
        Bautenverzeichnis verzeichnis = Bautenverzeichnis.ausBauten(List.of(
                bau("zunfthaus", 0), bau("buergeramt", 20), bau("muenzhaus", 40)));

        assertEquals(List.of("zunfthaus", "buergeramt", "muenzhaus"), verzeichnis.namen(),
                "nicht alphabetisch - die Datei gibt die Folge vor");
    }

    @Test
    @DisplayName("Bei zwei Eintraegen zum selben Namen gewinnt der ERSTE")
    void ersterGewinnt() {
        Bautenverzeichnis verzeichnis = Bautenverzeichnis.ausBauten(List.of(
                bau("haus", 0), bau("haus", 40)));

        assertEquals(1, verzeichnis.anzahl());
        assertEquals(0, verzeichnis.mitNamen("haus").bereich().vonX(),
                "der erste Eintrag, nicht der letzte");
    }

    @Test
    @DisplayName("Das leere Verzeichnis ist erlaubt")
    void leeresVerzeichnis() {
        Bautenverzeichnis leer = Bautenverzeichnis.ohneBauten();

        assertTrue(leer.leer());
        assertEquals(0, leer.anzahl());
        assertEquals(0, leer.bloecke());
        assertEquals(List.of(), leer.alle());
    }

    @Test
    @DisplayName("Mehr als zwanzig Bauten werden abgewiesen")
    void zuVieleBauten() {
        List<Platzhalterbau> viele = new ArrayList<>();
        for (int i = 0; i <= Bautenverzeichnis.HOECHSTENS; i++) {
            viele.add(bau("haus" + i, i * 20));
        }

        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> Bautenverzeichnis.ausBauten(viele));

        assertTrue(wurf.getMessage().contains("21"), wurf.getMessage());
        // Und GENAU zwanzig gehen - sonst waere die Grenze um eins daneben, und das
        // faellt an einer Verbotsprobe allein nie auf.
        assertEquals(Bautenverzeichnis.HOECHSTENS,
                Bautenverzeichnis.ausBauten(viele.subList(0, Bautenverzeichnis.HOECHSTENS))
                        .anzahl());
    }

    @Test
    @DisplayName("Ein unbekannter Name gibt null und wirft nicht")
    void unbekannterName() {
        // Der Name kann aus einer Station kommen, und dort steht womoeglich ein Bau, den
        // der Betreiber inzwischen aus der Datei genommen hat.
        Bautenverzeichnis verzeichnis = Bautenverzeichnis.ausBauten(List.of(bau("haus", 0)));

        assertNull(verzeichnis.mitNamen("zunfthaus"));
        assertNull(verzeichnis.mitNamen(null));
        assertFalse(verzeichnis.leer());
        // Gross- und Kleinschreibung trennen nicht.
        assertEquals("haus", verzeichnis.mitNamen("  HAUS ").schluessel());
    }

    @Test
    @DisplayName("Die Blockzahl ist die Summe und geht in den Startbericht")
    void blockzahl() {
        Bautenverzeichnis verzeichnis = Bautenverzeichnis.ausBauten(List.of(
                bau("eins", 0), bau("zwei", 20)));

        assertEquals(2 * 9 * 9 * 9, verzeichnis.bloecke());
    }

    @Test
    @DisplayName("Nichts ist kein Verzeichnis")
    void nullListe() {
        assertThrows(IllegalArgumentException.class,
                () -> Bautenverzeichnis.ausBauten(null));
        assertThrows(IllegalArgumentException.class,
                () -> Bautenverzeichnis.ausBauten(java.util.Collections.singletonList(null)));
    }
}
