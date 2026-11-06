package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Schluessel der Taetigkeiten - sie stehen in der Einstellungsdatei.
 *
 * <p>Kurz, aber nicht ueberfluessig: Der Schluessel ist eine Ebene der {@code config.yml}.
 * Wird er umbenannt, liest der Leser die ganze Lohnliste eines Betreibers nicht mehr - und
 * zwar stumm, denn eine unbekannte Ebene ist dort eine Klage je Zeile und kein Absturz.
 */
class TaetigkeitTest {

    @Test
    @DisplayName("Zwei Taetigkeiten - und der Schnitt dieses Schrittes ist Absicht")
    void zweiTaetigkeiten() {
        // Abbauen und Setzen sind die zwei, deren Ereignis das Plugin heute hoert. Fangen,
        // Toeten, Zuechten und Ernten brauchen sieben weitere Ereignisklassen und kommen
        // mit dem naechsten Schritt. Diese Probe wird dann rot - und das ist richtig, denn
        // dann gehoert die Lohnliste der ausgelieferten Datei mitgewachsen.
        assertEquals(2, Taetigkeit.values().length);
        assertEquals("abbauen", Taetigkeit.ABBAUEN.schluessel());
        assertEquals("setzen", Taetigkeit.SETZEN.schluessel());
    }

    @Test
    @DisplayName("Jeder Schluessel kommt genau einmal vor")
    void schluesselSindEindeutig() {
        // Zwei Taetigkeiten mit demselben Schluessel wuerden in der Lohntafel einander
        // stumm ueberschreiben.
        List<String> gesehen = new ArrayList<>();
        for (Taetigkeit taetigkeit : Taetigkeit.values()) {
            assertTrue(!gesehen.contains(taetigkeit.schluessel()),
                    "zweimal: " + taetigkeit.schluessel());
            gesehen.add(taetigkeit.schluessel());
        }
    }

    @Test
    @DisplayName("Aus einem Schluessel wird die Taetigkeit, auch gross und mit Leerraum")
    void ausSchluessel() {
        assertEquals(Taetigkeit.ABBAUEN, Taetigkeit.ausSchluessel("abbauen"));
        assertEquals(Taetigkeit.ABBAUEN, Taetigkeit.ausSchluessel(" ABBAUEN "));
        assertEquals(Taetigkeit.SETZEN, Taetigkeit.ausSchluessel("Setzen"));
    }

    @Test
    @DisplayName("Ein unbekannter Schluessel ist null und kein Wurf")
    void unbekannterSchluessel() {
        // Der Leser der Einstellungsdatei fragt damit, ob eine Ebene eine Taetigkeit ist.
        // Ein Wurf waere ein Absturz beim Start wegen eines Tippfehlers in einer Zeile.
        assertNull(Taetigkeit.ausSchluessel("fangen"));
        assertNull(Taetigkeit.ausSchluessel(""));
        assertNull(Taetigkeit.ausSchluessel(null));
    }
}
