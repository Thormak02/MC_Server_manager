package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wie eine Freistadt heissen darf.
 *
 * <p>Die Regel steht an EINER Stelle, weil sie an dreien gebraucht wird - beim Gruenden,
 * beim Warp, beim Nachschlagen. Diese Proben pruefen sie dort, und nicht in jedem der
 * drei Aufrufer noch einmal.
 */
@DisplayName("Die Namensregel der Freistaedte")
class StadtnameTest {

    @Test
    @DisplayName("Brauchbare Namen kommen durch - beide Fragen sagen ja")
    void brauchbareNamen() {
        for (String name : new String[] {"aue", "neustadt", "hafen-am-see", "stadt42",
                "a".repeat(32)}) {
            assertTrue(Stadtname.brauchbareLaenge(name), name);
            assertTrue(Stadtname.brauchbareZeichen(name), name);
        }
    }

    @Test
    @DisplayName("Die Laenge ist eine EIGENE Frage - sie nennt zwei Zahlen")
    void laengeIstEigeneFrage() {
        // Zwei Fragen und nicht eine, damit die Absage sagen kann, WAS nicht stimmt.
        // Darum traegt ein zu kurzer Name, der sonst richtig geschrieben ist, nur die
        // Laengenklage: Haette er beide, wuesste der Spieler nicht, was er aendern soll.
        assertFalse(Stadtname.brauchbareLaenge("ab"));
        assertTrue(Stadtname.brauchbareZeichen("ab"), "die Zeichen von ab sind in"
                + " Ordnung - nur die Laenge nicht");

        assertFalse(Stadtname.brauchbareLaenge("a".repeat(33)));
        assertTrue(Stadtname.brauchbareZeichen("a".repeat(33)));

        assertFalse(Stadtname.brauchbareLaenge(null));
        assertFalse(Stadtname.brauchbareLaenge(""));
        assertFalse(Stadtname.brauchbareLaenge("   "));
    }

    @Test
    @DisplayName("Die Zeichen sind die zweite Frage - und der Punkt ist dabei")
    void zeichenSindDieZweiteFrage() {
        // Kein PUNKT: Er ist in der Sprachdatei der Trenner, und freistadt.neu.stadt
        // waere dort eine Ebene tiefer statt ein Name. Der Unterstrich faellt mit.
        assertFalse(Stadtname.brauchbareZeichen("neu.stadt"));
        assertFalse(Stadtname.brauchbareZeichen("neu_stadt"));
        assertFalse(Stadtname.brauchbareZeichen("neu stadt"));
        assertFalse(Stadtname.brauchbareZeichen("neu+stadt"));
        assertFalse(Stadtname.brauchbareZeichen("neustadt!"));
        assertFalse(Stadtname.brauchbareZeichen(null));

        // Der erste Buchstabe MUSS ein Buchstabe sein: Sonst gaebe es eine Stadt namens
        // 10, und in "Freistadt 10" waere nicht mehr zu sehen, ob das ein Name oder
        // eine Feldnummer ist.
        assertFalse(Stadtname.brauchbareZeichen("1stadt"));
        assertFalse(Stadtname.brauchbareZeichen("-stadt"));
        assertTrue(Stadtname.brauchbareZeichen("s1"), "eine Ziffer DANACH ist erlaubt");
    }

    @Test
    @DisplayName("Grossschreibung ist erlaubt - und wird eingeebnet")
    void grossWirdKlein() {
        assertTrue(Stadtname.brauchbareZeichen("NEUSTADT"));
        assertEquals("neustadt", Stadtname.genormt("NEUSTADT"));
        assertEquals("neustadt", Stadtname.genormt("  NeuStadt  "));
        assertEquals("neustadt", Stadtname.genormt("neustadt"));
    }

    @Test
    @DisplayName("Dieselbe Stadt in jeder Schreibweise - sonst ist der Schluessel kein Riegel")
    void jedeSchreibweiseIstDieselbeStadt() {
        // Der eindeutige Schluessel in der Datenbank vergleicht Zeichen fuer Zeichen.
        // Wuerde nicht genormt, bekaeme ein zweiter Spieler denselben Namen in anderer
        // Schreibweise - und zwei Staedte hiessen gleich.
        String eine = Stadtname.genormt("Neustadt");
        for (String schreibweise : new String[] {"neustadt", "NEUSTADT", "NeUsTaDt",
                " neustadt ", "neustadt\t"}) {
            assertEquals(eine, Stadtname.genormt(schreibweise), schreibweise);
        }
    }

    @Test
    @DisplayName("Ein tuerkisches Gebietsschema aendert den Namen NICHT")
    void keinTuerkischesI() {
        // Ohne Locale.ROOT macht ein tuerkisches Gebietsschema aus ISTADT ein 'istadt'
        // mit punktlosem i - und der Name waere auf genau einem Rechner der Welt ein
        // anderer. Die Probe stellt das Gebietsschema wirklich um und danach zurueck.
        Locale vorher = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals("istadt", Stadtname.genormt("ISTADT"));
            assertTrue(Stadtname.brauchbareZeichen("ISTADT"));
        } finally {
            Locale.setDefault(vorher);
        }
    }

    @Test
    @DisplayName("Wer normiert, hat vorher gefragt - sonst wirft es")
    void normenOhneFragenWirft() {
        assertThrows(IllegalArgumentException.class, () -> Stadtname.genormt("ab"));
        assertThrows(IllegalArgumentException.class, () -> Stadtname.genormt("neu.stadt"));
        assertThrows(IllegalArgumentException.class, () -> Stadtname.genormt(null));
    }

    @Test
    @DisplayName("Die Hoechstlaenge passt zur Spaltenbreite")
    void laengePasstZurSpalte() {
        // VARCHAR(32). Eine Regel, die mehr zulaesst als die Spalte traegt, verschiebt
        // die Absage von einer lesbaren Zeile in eine abgeschnittene Zeichenkette.
        assertEquals(32, Stadtname.HOECHSTENS_ZEICHEN);
        assertEquals(Stadtname.HOECHSTENS_ZEICHEN,
                Stadtname.genormt("a".repeat(Stadtname.HOECHSTENS_ZEICHEN)).length());
    }
}
