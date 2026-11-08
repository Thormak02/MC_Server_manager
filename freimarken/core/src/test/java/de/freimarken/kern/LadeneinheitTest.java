package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Eine Ladeneinheit - ein Raum und seine Truhe. */
@DisplayName("Die Ladeneinheit")
class LadeneinheitTest {

    /** Ein Stand von 5 x 5 x 5 mit der Truhe in der Mitte. */
    private static Ladeneinheit stand() {
        return new Ladeneinheit("halle-1", new Quader(60, 71, 4, 64, 75, 8), 62, 71, 6);
    }

    @Test
    @DisplayName("Ein Stand haelt seinen Namen, seinen Raum und seine Truhe")
    void einStand() {
        Ladeneinheit einheit = stand();

        assertEquals("halle-1", einheit.name());
        assertEquals(125L, einheit.bereich().bloecke(), "fuenf mal fuenf mal fuenf");
        assertEquals("62/71/6", einheit.truheAlsText());
        assertTrue(einheit.istTruhe(62, 71, 6));
        // DREI Gegenproben, je eine Achse - und das ist ein Befund aus einer
        // Einspritzung: Mit nur einem Fall, der sich in z unterschied, blieb eine
        // Fassung gruen, die die HOEHE gar nicht vergleicht. In einer Halle mit zwei
        // Staenden uebereinander waere das die Truhe des Nachbarn.
        assertFalse(einheit.istTruhe(63, 71, 6), "eine andere x-Stelle");
        assertFalse(einheit.istTruhe(62, 72, 6), "eine andere HOEHE");
        assertFalse(einheit.istTruhe(62, 71, 7), "eine andere z-Stelle");
    }

    @Test
    @DisplayName("Der Name wird kleingeschrieben und von Leerzeichen befreit")
    void nameWirdGeglaettet() {
        // Der Name steht in der config.yml UND im Befehl. Ein Betreiber, der "Halle-1"
        // tippt, soll /laden mieten halle-1 benutzen koennen - und nicht raten.
        Ladeneinheit einheit = new Ladeneinheit("  Halle-1  ",
                new Quader(0, 0, 0, 2, 2, 2), 1, 1, 1);

        assertEquals("halle-1", einheit.name());
    }

    @Test
    @DisplayName("Ein Name mit Leerzeichen oder Umlaut kommt nicht zustande")
    void nameMitLeerzeichen() {
        // Ein Leerzeichen macht /laden mieten halle 1 zu ZWEI Argumenten, und ein Umlaut
        // verschwindet unter einer anderen Kodierung.
        assertThrows(IllegalArgumentException.class,
                () -> new Ladeneinheit("halle 1", new Quader(0, 0, 0, 2, 2, 2), 1, 1, 1));
        assertFalse(Ladeneinheit.brauchbarerName("halle 1"));
        assertFalse(Ladeneinheit.brauchbarerName("hallé"));
        assertFalse(Ladeneinheit.brauchbarerName("Halle"));
        assertFalse(Ladeneinheit.brauchbarerName(""));
        assertFalse(Ladeneinheit.brauchbarerName(null));
        assertTrue(Ladeneinheit.brauchbarerName("halle-1"));
        assertTrue(Ladeneinheit.brauchbarerName("stand7"));
    }

    @Test
    @DisplayName("Ein Name ohne Ende kommt nicht zustande")
    void nameZuLang() {
        // Der Name steht in einer Chatzeile und in einem Befehl. Zweiunddreissig Zeichen
        // sind reichlich; zweihundert waeren eine Zeile fuer sich.
        assertFalse(Ladeneinheit.brauchbarerName("a".repeat(33)));
        assertTrue(Ladeneinheit.brauchbarerName("a".repeat(32)));
    }

    @Test
    @DisplayName("Eine Einheit ohne Namen und ohne Raum kommt nicht zustande")
    void pflichtangaben() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ladeneinheit(null, new Quader(0, 0, 0, 2, 2, 2), 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Ladeneinheit("  ", new Quader(0, 0, 0, 2, 2, 2), 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Ladeneinheit("halle-1", null, 1, 1, 1));
    }

    @Test
    @DisplayName("Eine Truhe AUSSERHALB ihrer Einheit kommt nicht zustande")
    void truheMussDrinStehen() {
        // Die tragende Zusage dieses Teils: Eine Truhe daneben waere eine, die der
        // Mieter nicht geschuetzt bekommt - und die der Nachbar leerraeumt.
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Ladeneinheit("halle-1", new Quader(60, 71, 4, 64, 75, 8),
                        70, 71, 6));

        assertTrue(wurf.getMessage().contains("70/71/6"),
                "der Wurf muss den ORT der Truhe nennen: " + wurf.getMessage());
        assertTrue(wurf.getMessage().contains("ungeschuetzt"), wurf.getMessage());
    }

    @Test
    @DisplayName("Die Truhe darf genau auf der KANTE stehen - beide Ecken sind drin")
    void truheAufDerKante() {
        // Der Quader schliesst beide Ecken ein. Eine Truhe in der Ecke ist genau das,
        // was ein Stand von 5 x 5 oft braucht - und eine Pruefung, die sie abweist,
        // waere eine um eins daneben.
        Ladeneinheit einheit = new Ladeneinheit("halle-1",
                new Quader(60, 71, 4, 64, 75, 8), 60, 71, 4);

        assertTrue(einheit.istTruhe(60, 71, 4));
    }

    @Test
    @DisplayName("Eine Einheit ueber 4.096 Bloecken kommt nicht zustande")
    void zuGross() {
        // Wer eine Null zu viel tippt, vermietet sonst halb Altfurt - und bemerkt es
        // erst, wenn ein Mieter dort Truhen leerraeumt, die ihm nicht gehoeren.
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> new Ladeneinheit("halle-1", new Quader(0, 0, 0, 16, 16, 16),
                        1, 1, 1));

        assertTrue(wurf.getMessage().contains("Stadtteil"), wurf.getMessage());
        // Genau an der Grenze geht es noch: 16 Kantenlaenge sind 0 bis 15.
        assertEquals(Ladeneinheit.HOECHSTE_BLOECKE,
                new Ladeneinheit("halle-1", new Quader(0, 0, 0, 15, 15, 15), 1, 1, 1)
                        .bereich().bloecke());
    }

    @Test
    @DisplayName("Ein Stand von einem Block ist erlaubt - er traegt seine Truhe")
    void einBlockGenuegt() {
        // Die untere Grenze ergibt sich von selbst: Die Truhe muss drin stehen, also ist
        // ein Block das Kleinste, was zustande kommt. Eine eigene Schranke dafuer waere
        // dieselbe Regel ein zweites Mal.
        Ladeneinheit einheit = new Ladeneinheit("winzig",
                new Quader(5, 71, 5, 5, 71, 5), 5, 71, 5);

        assertEquals(1L, einheit.bereich().bloecke());
        assertTrue(einheit.enthaelt(5, 71, 5));
    }

    @Test
    @DisplayName("enthaelt fragt den Raum und nicht die Truhe")
    void enthaeltFragtDenRaum() {
        Ladeneinheit einheit = stand();

        assertTrue(einheit.enthaelt(60, 71, 4), "die Ecke gehoert dazu");
        assertTrue(einheit.enthaelt(64, 75, 8), "die andere auch");
        assertTrue(einheit.enthaelt(62, 73, 6), "die Mitte erst recht");
        assertFalse(einheit.enthaelt(59, 71, 4), "einen Block davor nicht mehr");
        assertFalse(einheit.enthaelt(65, 75, 8), "und einen danach auch nicht");
    }
}
