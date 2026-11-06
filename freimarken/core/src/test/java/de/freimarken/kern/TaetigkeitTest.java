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
    @DisplayName("Sechs Taetigkeiten mit ihrem Schluessel")
    void sechsTaetigkeiten() {
        // Eine Zahl, die mitwachsen MUSS: Jede Taetigkeit braucht einen eigenen
        // Ereigniszweig in der Verdienstwache, ein Wort in der Sprachdatei, eine
        // Stundenmenge und Lohnzeilen in der ausgelieferten Datei. Wer hier eine
        // hinzufuegt, laeuft auf - und das ist der Zweck.
        assertEquals(6, Taetigkeit.values().length);
        assertEquals("abbauen", Taetigkeit.ABBAUEN.schluessel());
        assertEquals("setzen", Taetigkeit.SETZEN.schluessel());
        assertEquals("ernten", Taetigkeit.ERNTEN.schluessel());
        assertEquals("fischen", Taetigkeit.FISCHEN.schluessel());
        assertEquals("toeten", Taetigkeit.TOETEN.schluessel());
        assertEquals("zuechten", Taetigkeit.ZUECHTEN.schluessel());
    }

    @Test
    @DisplayName("Die Namensart sagt, wogegen der Leser den Namen prueft")
    void namensartStimmt() {
        // Daran haengt, ob eine Lohnzeile gegen Material oder gegen EntityType geprueft
        // wird. Eine falsche Zuordnung verwirft STILL jede Zeile der Taetigkeit: Es gibt
        // kein Material cow und keine Wesensart stone.
        assertEquals(Taetigkeit.Namensart.MATERIAL, Taetigkeit.ABBAUEN.namensart());
        assertEquals(Taetigkeit.Namensart.MATERIAL, Taetigkeit.SETZEN.namensart());
        assertEquals(Taetigkeit.Namensart.MATERIAL, Taetigkeit.ERNTEN.namensart());
        assertEquals(Taetigkeit.Namensart.MATERIAL, Taetigkeit.FISCHEN.namensart());
        assertEquals(Taetigkeit.Namensart.WESEN, Taetigkeit.TOETEN.namensart());
        assertEquals(Taetigkeit.Namensart.WESEN, Taetigkeit.ZUECHTEN.namensart());
    }

    @Test
    @DisplayName("Jede Taetigkeit hat eine Namensart - keine ist null")
    void jedeHatEineNamensart() {
        for (Taetigkeit taetigkeit : Taetigkeit.values()) {
            assertTrue(taetigkeit.namensart() != null, taetigkeit.schluessel());
        }
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
