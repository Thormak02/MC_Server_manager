package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Das Verzeichnis der Ladeneinheiten. */
@DisplayName("Das Ladenverzeichnis")
class LadenverzeichnisTest {

    private static Ladeneinheit einheit(String name, int x) {
        return new Ladeneinheit(name, new Quader(x, 71, 0, x + 4, 75, 4), x + 2, 71, 2);
    }

    @Test
    @DisplayName("Ein leeres Verzeichnis ist leer und findet nichts")
    void leer() {
        Ladenverzeichnis verzeichnis = Ladenverzeichnis.ohneEinheiten();

        assertEquals(0, verzeichnis.anzahl());
        assertTrue(verzeichnis.leer());
        assertEquals(0L, verzeichnis.bloecke());
        assertEquals(List.of(), verzeichnis.namen());
        assertNull(verzeichnis.mitNamen("halle-1"));
        assertNull(verzeichnis.anDerStelle(0, 0, 0));
    }

    @Test
    @DisplayName("Die REIHENFOLGE der Datei bleibt - sie ist die Reihenfolge in /laden")
    void reihenfolgeBleibt() {
        // Dort ordnet der Betreiber, und das ist seine Entscheidung. Eine sortierte
        // Fassung wuerde ihm die Ordnung wegnehmen, die er sich ueberlegt hat.
        Ladenverzeichnis verzeichnis = Ladenverzeichnis.ausEinheiten(List.of(
                einheit("zwiebel", 0), einheit("apfel", 10), einheit("mohn", 20)));

        assertEquals(List.of("zwiebel", "apfel", "mohn"), verzeichnis.namen());
    }

    @Test
    @DisplayName("Der ERSTE Eintrag zu einem Namen gewinnt")
    void ersterGewinnt() {
        Ladeneinheit erste = einheit("halle-1", 0);
        Ladeneinheit zweite = einheit("halle-1", 100);

        Ladenverzeichnis verzeichnis =
                Ladenverzeichnis.ausEinheiten(List.of(erste, zweite));

        assertEquals(1, verzeichnis.anzahl());
        assertSame(erste, verzeichnis.mitNamen("halle-1"),
                "der zweite faellt weg - und der Leser beklagt ihn, damit der Betreiber"
                        + " nicht einen Stand sucht, den er eingetragen hat");
    }

    @Test
    @DisplayName("Gesucht wird nachsichtig - gross und mit Leerzeichen")
    void nachsichtigGesucht() {
        // Der Name kommt aus einem Befehl, den ein Spieler tippt.
        Ladenverzeichnis verzeichnis =
                Ladenverzeichnis.ausEinheiten(List.of(einheit("halle-1", 0)));

        assertNotNull(verzeichnis.mitNamen("HALLE-1"));
        assertNotNull(verzeichnis.mitNamen("  halle-1  "));
        assertNull(verzeichnis.mitNamen("halle-2"));
        assertNull(verzeichnis.mitNamen(null),
                "ein Tippfehler ist keine Ausnahme, sondern eine Absage");
    }

    @Test
    @DisplayName("anDerStelle findet die Einheit, in der ein Punkt liegt")
    void anDerStelle() {
        // Die Frage des Truhenschutzes - sie laeuft bei jedem Oeffnen einer Truhe.
        Ladenverzeichnis verzeichnis = Ladenverzeichnis.ausEinheiten(List.of(
                einheit("halle-1", 0), einheit("halle-2", 10)));

        assertEquals("halle-1", verzeichnis.anDerStelle(2, 71, 2).name());
        assertEquals("halle-2", verzeichnis.anDerStelle(12, 71, 2).name());
        // Zwischen den Staenden liegt der Gang - er gehoert niemandem.
        assertNull(verzeichnis.anDerStelle(7, 71, 2));
        assertNull(verzeichnis.anDerStelle(2, 80, 2), "ueber dem Stand auch nicht");
    }

    @Test
    @DisplayName("Die Bloecke aller Einheiten werden addiert")
    void bloeckeAddiert() {
        // Eine Zahl fuer den Bericht: Zehn Staende mit je hundert Bloecken sind tausend;
        // stehen dort hunderttausend, hat eine Einheit eine Null zu viel.
        Ladenverzeichnis verzeichnis = Ladenverzeichnis.ausEinheiten(List.of(
                einheit("halle-1", 0), einheit("halle-2", 10)));

        assertEquals(2L * 125L, verzeichnis.bloecke());
    }

    @Test
    @DisplayName("Zwei Verzeichnisse mit demselben INHALT sind gleich")
    void gleichheitHaengtAmInhalt() {
        Ladenverzeichnis a = Ladenverzeichnis.ausEinheiten(List.of(einheit("halle-1", 0)));
        Ladenverzeichnis b = Ladenverzeichnis.ausEinheiten(List.of(einheit("halle-1", 0)));

        assertEquals(a, b);
        // Der Unterschied ist NUR der Ort: gleicher Name, gleiche Zahl, gleiche Groesse -
        // und doch ein anderer Stand. Eine Zaehlung saehe das nicht.
        assertNotEquals(a, Ladenverzeichnis.ausEinheiten(List.of(einheit("halle-1", 50))));
    }

    @Test
    @DisplayName("Die Listen sind eigene Kopien - ein Aufrufer kann nichts aendern")
    void eigeneKopien() {
        Ladenverzeichnis verzeichnis =
                Ladenverzeichnis.ausEinheiten(List.of(einheit("halle-1", 0)));

        assertThrows(UnsupportedOperationException.class,
                () -> verzeichnis.namen().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> verzeichnis.alle().clear());
        // Und die hereingegebene Liste darf danach wachsen, ohne das Verzeichnis zu
        // aendern - sonst haelt es seine eigene Pruefung nicht mehr.
        List<Ladeneinheit> aussen = new ArrayList<>(List.of(einheit("halle-1", 0)));
        Ladenverzeichnis eigenes = Ladenverzeichnis.ausEinheiten(aussen);
        aussen.add(einheit("halle-2", 10));

        assertEquals(1, eigenes.anzahl());
    }

    @Test
    @DisplayName("Ohne Liste und mit einem leeren Eintrag geht nichts")
    void pflichtangaben() {
        assertThrows(IllegalArgumentException.class,
                () -> Ladenverzeichnis.ausEinheiten(null));
        assertThrows(IllegalArgumentException.class,
                () -> Ladenverzeichnis.ausEinheiten(
                        Arrays.asList(einheit("halle-1", 0), null)));
    }

    @Test
    @DisplayName("Zwanzig Staende werden alle gefunden")
    void zwanzigStaende() {
        // Die Reihensuche von anDerStelle ist eine Reihensuche - diese Probe sagt, dass
        // sie bei einer vollen Halle noch jeden findet und nicht beim ersten aufhoert.
        List<Ladeneinheit> staende = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            staende.add(einheit("stand-" + i, i * 10));
        }
        Ladenverzeichnis verzeichnis = Ladenverzeichnis.ausEinheiten(staende);

        assertEquals(20, verzeichnis.anzahl());
        for (int i = 0; i < 20; i++) {
            assertEquals("stand-" + i,
                    verzeichnis.anDerStelle(i * 10 + 2, 71, 2).name(),
                    "Stand " + i);
        }
    }

}
