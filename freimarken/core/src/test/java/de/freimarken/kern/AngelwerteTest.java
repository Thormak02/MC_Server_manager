package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Zahlen der Angeltabelle.
 *
 * <p>Zwei Dinge haengen hier dran, und beide sind nicht offensichtlich: Die LEERE Tabelle
 * ist erlaubt und bedeutet etwas (wieder Vanilla), und die hereingegebene Abbildung wird
 * KOPIERT - sonst waere eine veraenderliche Tabelle in einem unveraenderlichen Satz eine
 * Hintertuer in den Stand, der bei jedem Fang aus dem Hauptfaden gelesen wird.
 */
@DisplayName("Die Zahlen der Angeltabelle")
class AngelwerteTest {

    @Test
    @DisplayName("Die Vorgabe greift, ersetzt jeden fuenften Fang und hat neun Zeilen")
    void vorgabe() {
        assertTrue(Angelwerte.VORGABE.an());
        assertEquals(20, Angelwerte.VORGABE.haeufigkeitProzent());
        assertEquals(9, Angelwerte.VORGABE.gewichte().size());
        assertEquals(83L, Angelwerte.VORGABE.gewichtsumme(),
                "die Summe ist der Nenner jeder Wahl - sie steht hier als Zahl, damit"
                        + " eine neue Zeile in der Vorgabe auffaellt");
        assertTrue(Angelwerte.VORGABE.greift());
    }

    @Test
    @DisplayName("greift() fragt alle DREI Bedingungen")
    void greiftFragtAlleDrei() {
        // Drei Wege zu Vanilla, und jeder muss einzeln wirken: der Schalter (fuer ein
        // Wochenende), die Haeufigkeit (fuer eine Probe) und die leere Tabelle (fuer
        // den, der sie herausnimmt, ohne den Schalter zu finden).
        assertFalse(Angelwerte.VORGABE.mitAn(false).greift());
        assertFalse(Angelwerte.VORGABE.mitHaeufigkeit(0).greift());
        assertFalse(Angelwerte.VORGABE.mitFaengen(Map.of()).greift());
        assertTrue(Angelwerte.VORGABE.mitHaeufigkeit(1).greift(), "ein Prozent genuegt");
    }

    @Test
    @DisplayName("Die LEERE Tabelle ist erlaubt - sie ist eine Entscheidung")
    void leereTabelleIstErlaubt() {
        Angelwerte leer = new Angelwerte(true, 20, Map.of());

        assertEquals(0L, leer.gewichtsumme());
        assertFalse(leer.greift());
    }

    @Test
    @DisplayName("Die Tabelle wird KOPIERT - von aussen laesst sie sich nicht aendern")
    void tabelleWirdKopiert() {
        Map<String, Integer> meine = new HashMap<>();
        meine.put("aal", 1);
        Angelwerte werte = new Angelwerte(true, 20, meine);

        meine.put("zander", 99);

        assertEquals(1, werte.gewichte().size(),
                "sonst waere der Stand, der bei jedem Fang gelesen wird, von aussen"
                        + " veraenderlich - und zwar aus einem anderen Faden");
        assertThrows(UnsupportedOperationException.class,
                () -> werte.gewichte().put("butt", 1),
                "und die Abbildung selbst ist unveraenderlich");
    }

    @Test
    @DisplayName("Was nicht in den Bereich passt, wird abgewiesen")
    void riegel() {
        assertThrows(IllegalArgumentException.class,
                () -> new Angelwerte(true, -1, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Angelwerte(true, 101, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Angelwerte(true, 20, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Angelwerte(true, 20, Map.of("aal", 0)),
                "Gewicht 0 ist eine Zeile, die nie greift - die gehoert heraus");
        assertThrows(IllegalArgumentException.class,
                () -> new Angelwerte(true, 20, Map.of("aal", -1)));
        assertThrows(IllegalArgumentException.class,
                () -> new Angelwerte(true, 20,
                        Map.of("aal", Angelwerte.HOECHSTES_GEWICHT + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new Angelwerte(true, 20, Map.of("  ", 1)),
                "ein Fang ohne Namen - der Name IST der Gegenstand");
    }

    @Test
    @DisplayName("Die Angelwerte liegen im EINEN Wirtschaftssatz")
    void imWirtschaftssatz() {
        // Derselbe Grund wie bei allen anderen Bloecken: Ein Neuladen tauscht den
        // ganzen Satz als EIN Stueck. Hier ist er am schaerfsten - die Tabelle wird bei
        // JEDEM Fang gelesen, und zwar im Hauptfaden.
        assertEquals(Angelwerte.VORGABE, Wirtschaftswerte.VORGABE.angeln());

        Wirtschaftswerte anders = Wirtschaftswerte.VORGABE.mitAngeln(
                Angelwerte.VORGABE.mitAn(false));

        assertFalse(anders.angeln().an());
        assertEquals(Wirtschaftswerte.VORGABE.inseln(), anders.inseln(),
                "und der Nachbarblock bleibt stehen");
    }
}
