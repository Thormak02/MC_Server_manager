package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was als Berufsschluessel durchgeht - und warum die Schranke hier steht und nicht erst
 * beim Lesen der Einstellungsdatei.
 *
 * <p>Derselbe Schluessel steht an drei Orten: in der Ablage, in der Sprachdatei als
 * {@code beruf.name.<schluessel>} und im getippten Befehl. Ein Name, der an einem dieser
 * Orte anders aussieht als an den anderen, ergibt keinen Absturz, sondern einen Beruf, den
 * niemand waehlen kann.
 */
class BerufsangabeTest {

    @Test
    @DisplayName("Ein Grundberuf laesst das Feld leer und weiss das von sich")
    void grundberuf() {
        Berufsangabe angabe = new Berufsangabe("bergmann", null);

        assertEquals("bergmann", angabe.schluessel());
        assertNull(angabe.grundberuf());
        assertFalse(angabe.istSpezialisierung());
    }

    @Test
    @DisplayName("Eine Spezialisierung nennt ihren Grundberuf")
    void spezialisierung() {
        Berufsangabe angabe = new Berufsangabe("steinmetz", "bergmann");

        assertTrue(angabe.istSpezialisierung());
        assertEquals("bergmann", angabe.grundberuf());
    }

    @Test
    @DisplayName("Beide Namen werden kleingeschrieben und von Leerraum befreit")
    void namenWerdenNormalisiert() {
        // Und zwar HIER, an einer Stelle. Biegt jeder Leser den Namen selbst zurecht,
        // biegt ihn eines Tages einer anders - und der Befehl findet den Beruf nicht
        // mehr, den die Datei nennt.
        Berufsangabe angabe = new Berufsangabe("  Steinmetz ", " BERGMANN ");

        assertEquals("steinmetz", angabe.schluessel());
        assertEquals("bergmann", angabe.grundberuf());
    }

    @Test
    @DisplayName("32 Zeichen gehen, 33 nicht - die Ablage schnitte stumm ab")
    void laengeGilt() {
        String grenze = "a".repeat(Berufsangabe.HOECHSTENS_ZEICHEN);

        assertEquals(grenze, new Berufsangabe(grenze, null).schluessel());

        assertThrows(IllegalArgumentException.class,
                () -> new Berufsangabe(grenze + "a", null));
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsangabe("steinmetz", grenze + "a"));
    }

    @Test
    @DisplayName("Nur Kleinbuchstaben, Ziffern und Bindestrich, beginnend mit einem Buchstaben")
    void nurBrauchbareZeichen() {
        assertEquals("berg-mann2", new Berufsangabe("berg-mann2", null).schluessel());

        assertThrows(IllegalArgumentException.class,
                () -> new Berufsangabe("berg mann", null));
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsangabe("2bergmann", null));
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsangabe("-bergmann", null));
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsangabe("berg_mann", null));
        // Der Punkt waere besonders boese: Er ist der Trenner in der Sprachdatei, und
        // beruf.name.berg.mann waere dort eine Ebene tiefer statt ein Name.
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsangabe("berg.mann", null));
    }

    @Test
    @DisplayName("Ohne Namen gibt es keine Angabe")
    void nameIstPflicht() {
        assertThrows(IllegalArgumentException.class, () -> new Berufsangabe(null, null));
        assertThrows(IllegalArgumentException.class, () -> new Berufsangabe("", null));
        assertThrows(IllegalArgumentException.class, () -> new Berufsangabe("   ", null));
        // Ein leeres Feld ist nicht dasselbe wie ein fehlendes: Wer in der Datei
        // grundberuf: "" schreibt, meint vermutlich einen Grundberuf - aber geraten wird
        // hier nichts.
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsangabe("steinmetz", "  "));
    }

    @Test
    @DisplayName("Ein Beruf ist nicht sein eigener Grundberuf")
    void keinKreisAufSichSelbst() {
        // Sonst zaehlte die Platzzaehlung der Regel ihn zweimal, und die Absage waere
        // "Spezialisierungen voll" bei einem Spieler, der keine hat.
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsangabe("bergmann", "bergmann"));
        assertThrows(IllegalArgumentException.class,
                () -> new Berufsangabe("Bergmann", " bergmann "));
    }
}
