package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Riegel an den Verfallsfristen [Teil E5].
 *
 * <p>Jeder davon faengt eine Einstellung, die im Spiel nicht aussieht wie ein Fehler,
 * sondern wie ein kaputtes Plugin: eine Stadt, die am Gruendungstag verfaellt, oder eine
 * Warnung, die nie kommt.
 */
@DisplayName("Die Verfallsfristen aus der config.yml")
class VerfallswerteTest {

    @Test
    @DisplayName("Die Vorgabe ist genau das, was die ausgelieferte Datei nennt")
    void vorgabe() {
        // Die Zahlen stehen im PROGRAMM und nicht nur in der Datei - eine vorhandene
        // config.yml wird beim Update nie ueberschrieben, und ohne Vorgabe im Programm
        // bekaeme ein alter Server keinen Verfall.
        assertTrue(Verfallswerte.VORGABE.an());
        assertEquals(365, Verfallswerte.VORGABE.unbesuchtTage(),
                "zwoelf Monate aus Plan 16, in TAGEN");
        assertEquals(60, Verfallswerte.VORGABE.warnungFruehTage());
        assertEquals(14, Verfallswerte.VORGABE.warnungSpaetTage());
    }

    @Test
    @DisplayName("Eine Frist von 0 Tagen wird abgewiesen - sie liesse jede Stadt sterben")
    void nullTageWirft() {
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Verfallswerte(true, 0, 0, 0));
        assertTrue(fehler.getMessage().contains("an auf"), fehler.getMessage());
    }

    @Test
    @DisplayName("Eine Frist ueber zehn Jahren wird abgewiesen")
    void zuLangeFristWirft() {
        assertTrue(new Verfallswerte(true, Verfallswerte.LAENGSTE_FRIST_TAGE, 0, 0)
                        .unbesuchtTage() == Verfallswerte.LAENGSTE_FRIST_TAGE,
                "genau zehn Jahre gehen durch");
        assertThrows(IllegalArgumentException.class,
                () -> new Verfallswerte(true, Verfallswerte.LAENGSTE_FRIST_TAGE + 1,
                        0, 0));
    }

    @Test
    @DisplayName("Eine Warnung AB der Faelligkeit wird abgewiesen")
    void warnungNichtVorDerGruendung() {
        // 365 Tage vorher waere der Gruendungstag selbst - der Besitzer laese seine
        // Warnung in dem Augenblick, in dem er zahlt.
        assertThrows(IllegalArgumentException.class,
                () -> new Verfallswerte(true, 365, 365, 14));
        assertTrue(new Verfallswerte(true, 365, 364, 14).warnungFruehTage() == 364,
                "einen Tag darunter geht");
    }

    @Test
    @DisplayName("Die zweite Warnung MUSS naeher am Verfall liegen als die erste")
    void zweiteWarnungNaeher() {
        // Sonst liest ein Stadtbesitzer nie die erste: Die Rechnung prueft die spaete
        // zuerst, und waere sie weiter weg, traefe sie immer.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> new Verfallswerte(true, 365, 14, 60));
        assertTrue(fehler.getMessage().contains("dringendere"), fehler.getMessage());

        assertTrue(new Verfallswerte(true, 365, 60, 60).warnungSpaetTage() == 60,
                "gleich geht durch - dann gilt nur die dringendere, und das ist eine"
                        + " brauchbare Einstellung");
    }

    @Test
    @DisplayName("Jeder Wandler laesst die anderen drei stehen")
    void wandlerBehaltenDenRest() {
        Verfallswerte eigen = new Verfallswerte(true, 100, 30, 7);

        assertEquals(new Verfallswerte(false, 100, 30, 7), eigen.mitAn(false));
        assertEquals(new Verfallswerte(true, 200, 30, 7), eigen.mitUnbesuchtTage(200));
        assertEquals(new Verfallswerte(true, 100, 50, 20), eigen.mitWarnungen(50, 20));
    }
}
