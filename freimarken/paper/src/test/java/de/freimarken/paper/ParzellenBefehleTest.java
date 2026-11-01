package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Rechnung hinter der Parzellengrenze - der Teil, der ohne Server auskommt.
 *
 * <p>Welche Rechte ein Spieler hat, weiss nur der Server. Was aus zwei Zahlen wird, nicht:
 * Genau deshalb steht die Addition in einer eigenen Methode und nicht mitten in der
 * Abfrage der Rechte.
 */
class ParzellenBefehleTest {

    @Test
    @DisplayName("Der Zuschlag kommt oben drauf, er ersetzt nicht")
    void zuschlagKommtDrauf() {
        // Der ganze Sinn der Sache: Eine Mitgliedschaft belohnt den, der sich schon etwas
        // erspielt hat. Wuerde sie die Zahl ersetzen, haette ein Reisender am ersten Tag
        // so viele Parzellen wie ein Buerger nach vierzig Stunden.
        assertEquals(6, ParzellenBefehle.mitZuschlag(4, 2));
        assertEquals(3, ParzellenBefehle.mitZuschlag(1, 2), "auch unten auf der Leiter");
    }

    @Test
    @DisplayName("Ohne Zuschlag bleibt es bei der Grundlage")
    void ohneZuschlagUnveraendert() {
        assertEquals(4, ParzellenBefehle.mitZuschlag(4, 0));
    }

    @Test
    @DisplayName("Ein negativer Zuschlag nimmt nichts weg")
    void negativerZuschlagNimmtNichts() {
        // Ein Recht wie freimarken.extra.parzellen.-3 ist ein Tippfehler, kein Entzug.
        // Rechte sollen geben; wegnehmen tut die Vorgabe in der Konfiguration.
        assertEquals(4, ParzellenBefehle.mitZuschlag(4, -3));
    }

    @Test
    @DisplayName("Zusammen laufen sie nicht ueber")
    void keinUeberlauf() {
        // Jede der beiden Zahlen wird bei der Rechteauswertung auf Integer.MAX_VALUE
        // gekappt. Addiert ergaebe das ohne Riegel eine NEGATIVE Obergrenze - und damit
        // haette ausgerechnet der mit den meisten Rechten gar keine Parzelle mehr.
        int ergebnis = ParzellenBefehle.mitZuschlag(Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertTrue(ergebnis > 0, "eine Obergrenze darf nie negativ werden");
        assertEquals(Integer.MAX_VALUE, ergebnis);
    }
}
