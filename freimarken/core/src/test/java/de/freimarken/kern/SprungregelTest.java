package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Rueckfrage bei einer zu grossen Aenderung - die eine Stelle, die "Faktor 2"
 * entscheidet.
 */
class SprungregelTest {

    @Test
    @DisplayName("Genau das Doppelte geht noch durch, mehr nicht")
    void nachOben() {
        assertFalse(Sprungregel.grosserSprungNachOben(25_000L, 50_000L),
                "genau das Doppelte ist die Grenze und noch erlaubt");
        assertTrue(Sprungregel.grosserSprungNachOben(25_000L, 50_001L));
        assertFalse(Sprungregel.grosserSprungNachOben(25_000L, 25_000L));
        assertFalse(Sprungregel.grosserSprungNachOben(25_000L, 1L),
                "nach unten ist das kein Sprung nach oben");
    }

    @Test
    @DisplayName("Genau die Haelfte geht noch durch, weniger nicht")
    void nachUnten() {
        assertFalse(Sprungregel.grosserSprungNachUnten(25_000L, 12_500L),
                "genau die Haelfte ist die Grenze und noch erlaubt");
        assertTrue(Sprungregel.grosserSprungNachUnten(25_000L, 12_499L));
        assertFalse(Sprungregel.grosserSprungNachUnten(25_000L, 1_000_000L));
    }

    @Test
    @DisplayName("Die Haelfte wird nicht abgerundet gerechnet")
    void keineGanzzahlfalle() {
        // Bei alt = 25 waere alt / 2 = 12, und 12 waere damit erlaubt - obwohl es weniger
        // als die Haelfte ist. Gerechnet wird deshalb 2 * neu < alt.
        assertTrue(Sprungregel.grosserSprungNachUnten(25L, 12L));
        assertFalse(Sprungregel.grosserSprungNachUnten(24L, 12L));
    }

    @Test
    @DisplayName("Null ist bei einem Geldwert immer ein grosser Sprung")
    void nullIstEinSprung() {
        // Gratis ist eine denkbare Serverform, aber keine, die beilaeufig entsteht:
        // Jede Parzelle ohne Preis ist die unumkehrbare Richtung.
        assertTrue(Sprungregel.grosserSprung(25_000L, 0L));
        assertTrue(Sprungregel.grosserSprungNachUnten(1L, 0L));
    }

    @Test
    @DisplayName("Der Bestaetigungstext nennt beide Zahlen in der Reihenfolge alt, neu")
    void text() {
        assertEquals("25000 -> 100000", Sprungregel.sprungText(25_000L, 100_000L));
        assertTrue(Sprungregel.bestaetigt("25000 -> 100000", 25_000L, 100_000L));
        assertTrue(Sprungregel.bestaetigt("  25000 -> 100000  ", 25_000L, 100_000L),
                "Leerzeichen am Rand sind kein Unterschied");
    }

    @Test
    @DisplayName("Eine Bestaetigung gilt nur fuer genau diesen Sprung")
    void bestaetigungGiltNichtWeiter() {
        // Sonst waere eine einmal eingetragene Zeile ein Dauerfreibrief fuer jede
        // kuenftige Aenderung desselben Wertes.
        assertFalse(Sprungregel.bestaetigt("25000 -> 100000", 25_000L, 400_000L));
        assertFalse(Sprungregel.bestaetigt("100000", 25_000L, 100_000L));
        assertFalse(Sprungregel.bestaetigt("", 25_000L, 100_000L));
        assertFalse(Sprungregel.bestaetigt(null, 25_000L, 100_000L));
    }

    @Test
    @DisplayName("Grenzregel entscheidet und formuliert ab jetzt ueber dieselbe Regel")
    void grenzregelReichtDurch() {
        // Die Weltgrenze nennt alt und neu in der anderen Reihenfolge - der TEXT muss
        // trotzdem derselbe sein, sonst passt eine eingetragene Bestaetigung an einer
        // der beiden Stellen nicht.
        assertEquals(Sprungregel.sprungText(2989L, 8000L),
                Grenzregel.sprungText(8000L, 2989.0));
        assertEquals(2989L, Grenzregel.wirksam(8000L, 2989.0, 32L, 3000L, false),
                "ohne Bestaetigung bleibt die Grenze, die in der Welt steht");
        assertEquals(8000L, Grenzregel.wirksam(8000L, 2989.0, 32L, 3000L, true));
    }
}
