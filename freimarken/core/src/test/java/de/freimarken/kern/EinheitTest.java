package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Besitzeinheit - Art UND Nummer.
 *
 * <p>Die eine Zusage, auf die es ankommt, steht in
 * {@link #gleicheNummerAndereArtIstNichtGleich}: Nummern kommen aus VERSCHIEDENEN
 * Tabellen und beginnen alle bei 1. Ein Vergleich, der nur die Zahl ansieht, haelt
 * Grundstueck 5 fuer Stadtfeld 5 - und laesst damit einen Uebergriff durch. Nicht
 * immer, sondern genau dann, wenn die zwei Zaehler zufaellig gleich stehen; also auf
 * einem Probeserver nie.
 */
@DisplayName("Die Besitzeinheit")
class EinheitTest {

    @Test
    @DisplayName("Gleiche Nummer, andere ART: NICHT dieselbe Einheit")
    void gleicheNummerAndereArtIstNichtGleich() {
        Einheit stadtfeld = new Einheit("stadtfeld", 5L);
        Einheit grundstueck = new Einheit("grundstueck", 5L);

        assertEquals(stadtfeld.nummer(), grundstueck.nummer(),
                "dieselbe Zahl - sonst prueft dieser Satz nichts");
        assertNotEquals(stadtfeld, grundstueck,
                "zwei Tabellen, zwei Zaehler: Grundstueck 5 ist nicht Stadtfeld 5. Ein"
                        + " Vergleich der Nummern allein liesse einen Kolben von einem"
                        + " ins andere schieben.");
    }

    @Test
    @DisplayName("Dieselbe Art und dieselbe Nummer: dieselbe Einheit")
    void dieselbeArtUndNummerIstGleich() {
        // DIE GEGENRICHTUNG, und ohne sie waere der Satz darueber auch gruen, wenn
        // ueberhaupt nichts gleich waere - dann gaebe es auf der eigenen Parzelle
        // keinen Kolben mehr.
        assertEquals(new Einheit("parzelle", 7L), new Einheit("parzelle", 7L));
        assertEquals(new Einheit("parzelle", 7L).hashCode(),
                new Einheit("parzelle", 7L).hashCode());
    }

    @Test
    @DisplayName("Die Art wird vereinheitlicht - Grossschreibung und Rand fallen weg")
    void artWirdVereinheitlicht() {
        // Die Arten kommen aus zwei Quellen: aus Art.schluessel() (immer klein) und als
        // Konstante aus einem Buch. Ein " Grundstueck " aus der zweiten Quelle waere
        // sonst eine dritte Art, und zwei Stellen derselben Einheit gaelten als zwei.
        assertEquals(new Einheit("grundstueck", 1L), new Einheit("  GRUNDSTUECK  ", 1L));
        assertEquals("grundstueck", new Einheit("  GRUNDSTUECK  ", 1L).art());
    }

    @Test
    @DisplayName("Ohne Art wirft es - und die Meldung sagt, warum")
    void ohneArtWirft() {
        for (String leer : new String[] {null, "", "   "}) {
            var fehler = assertThrows(IllegalArgumentException.class,
                    () -> new Einheit(leer, 1L));
            assertTrue(fehler.getMessage().contains("Art"), fehler.getMessage());
        }
    }

    @Test
    @DisplayName("Die Beschreibung nennt beides - fuer das Protokoll")
    void beschreibungNenntBeides() {
        // Eine Klage, die nur "5" nennt, hilft niemandem: Die Frage ist immer, 5 WAS.
        assertEquals("parzelle#5", new Einheit("parzelle", 5L).toString());
    }
}
