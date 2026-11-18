package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Wahl aus der Erztabelle - zu zwei Wuerfen, die als Zahl hereinkommen.
 *
 * <p>Dass der Zufall draussen bleibt, ist der Grund, warum diese Probe jede Grenze genau
 * treffen kann: Bei einem eigenen Wuerfel waere sie eine Haeufigkeitsschaetzung mit
 * tausend Durchlaeufen - und die ist mal gruen und mal rot.
 */
@DisplayName("Die Erzregel")
class ErzregelTest {

    /** Kohle 50 ab 0, Kupfer 30 ab 0, Eisen 25 ab 1, Diamant 3 ab 5. */
    private static final Erzwerte WERTE = new Erzwerte(true, 20, 2, 40, true,
            Map.of("coal_ore", new Erzwerte.Erzzeile(50, 0),
                    "copper_ore", new Erzwerte.Erzzeile(30, 0),
                    "iron_ore", new Erzwerte.Erzzeile(25, 1),
                    "diamond_ore", new Erzwerte.Erzzeile(3, 5)));

    @Test
    @DisplayName("Unter der Haeufigkeit kommt Erz, darueber Kobbelstein")
    void grenzeDerHaeufigkeit() {
        // Bei Stufe 0 sind es 20 Prozent, also treffen die Wuerfe 0 bis 19. Die 20 ist
        // die Grenze, und sie gehoert zum Kobbelstein: Sonst waeren es 21 von 100 - ein
        // Fehler, den keine Haeufigkeitsschaetzung findet.
        assertTrue(Erzregel.erz(WERTE, 0, 0, 0L).isPresent());
        assertTrue(Erzregel.erz(WERTE, 0, 19, 0L).isPresent());
        assertEquals(Optional.empty(), Erzregel.erz(WERTE, 0, 20, 0L));
        assertEquals(Optional.empty(), Erzregel.erz(WERTE, 0, 99, 0L));
    }

    @Test
    @DisplayName("Die Stufe hebt die Haeufigkeit - dieselben Wuerfe, mehr Treffer")
    void stufeHebtDieHaeufigkeit() {
        assertEquals(Optional.empty(), Erzregel.erz(WERTE, 0, 20, 0L));
        assertTrue(Erzregel.erz(WERTE, 1, 20, 0L).isPresent(),
                "bei Stufe 1 sind es 22 Prozent - derselbe Wurf trifft jetzt");
    }

    @Test
    @DisplayName("Der Gewichtswurf trifft die Zeilen in ALPHABETISCHER Reihenfolge")
    void gewichtswurfTrifftZeilen() {
        // Bei Stufe 0 spielen Kohle (50) und Kupfer (30) mit, Summe 80. Sortiert nach
        // Namen: coal_ore 0 bis 49, copper_ore 50 bis 79.
        assertEquals(Optional.of("coal_ore"), Erzregel.erz(WERTE, 0, 0, 0L));
        assertEquals(Optional.of("coal_ore"), Erzregel.erz(WERTE, 0, 0, 49L));
        assertEquals(Optional.of("copper_ore"), Erzregel.erz(WERTE, 0, 0, 50L));
        assertEquals(Optional.of("copper_ore"), Erzregel.erz(WERTE, 0, 0, 79L));
        assertEquals(Optional.empty(), Erzregel.erz(WERTE, 0, 0, 80L),
                "ein Wurf ausserhalb der Summe ist ein Aufrufsfehler - und es bleibt bei"
                        + " Kobbelstein, statt im Hauptfaden zu werfen");
        assertEquals(Optional.empty(), Erzregel.erz(WERTE, 0, 0, -1L));
    }

    @Test
    @DisplayName("Eine Zeile mit Mindeststufe ist vorher NICHT zu treffen")
    void mindeststufeFiltert() {
        // Diamant ab Stufe 5: Bei Stufe 4 ist die Summe 105 (Kohle 50, Kupfer 30, Eisen
        // 25), und kein Wurf darf ihn nennen. Bei Stufe 5 ist sie 108 - und weil sortiert
        // wird, SCHIEBT sich der Diamant zwischen Kupfer und Eisen: coal_ore 0 bis 49,
        // copper_ore 50 bis 79, diamond_ore 80 bis 82, iron_ore 83 bis 107. Genau das
        // meint "alphabetisch" - die Zeilen hinter einer neuen verschieben sich, und
        // deshalb muss die Summe aus DERSELBEN gefilterten Liste kommen wie die Schleife.
        assertEquals(105L, WERTE.gewichtsumme(4));
        assertEquals(Optional.of("iron_ore"), Erzregel.erz(WERTE, 4, 0, 104L));
        assertEquals(Optional.empty(), Erzregel.erz(WERTE, 4, 0, 105L));

        assertEquals(108L, WERTE.gewichtsumme(5));
        assertEquals(Optional.of("copper_ore"), Erzregel.erz(WERTE, 5, 0, 79L));
        assertEquals(Optional.of("diamond_ore"), Erzregel.erz(WERTE, 5, 0, 80L));
        assertEquals(Optional.of("diamond_ore"), Erzregel.erz(WERTE, 5, 0, 82L));
        assertEquals(Optional.of("iron_ore"), Erzregel.erz(WERTE, 5, 0, 83L));
        assertEquals(Optional.of("iron_ore"), Erzregel.erz(WERTE, 5, 0, 107L));
        assertEquals(Optional.empty(), Erzregel.erz(WERTE, 5, 0, 108L));
    }

    @Test
    @DisplayName("Trifft der Prozentwurf und erreicht keine Zeile die Stufe, bleibt es"
            + " Kobbelstein")
    void keineZeileErreichtDieStufe() {
        // Der Fall einer Tabelle, deren niedrigste Zeile eine Mindeststufe traegt. Ohne
        // diese Antwort zeigte ein Wurf auf eine Zeile, die es nicht gibt.
        Erzwerte spaet = WERTE.mitTabelle(
                Map.of("diamond_ore", new Erzwerte.Erzzeile(3, 5)));

        assertEquals(Optional.empty(), Erzregel.erz(spaet, 0, 0, 0L));
        assertEquals(Optional.of("diamond_ore"), Erzregel.erz(spaet, 5, 0, 0L));
    }

    @Test
    @DisplayName("DIESELBE Tabelle in anderer Reihenfolge gibt DIESELBEN Erze")
    void reihenfolgeDerKarteAendertNichts() {
        // DIE TRAGENDE PROBE DIESER KLASSE. Map.copyOf sagt keine Reihenfolge zu, und sie
        // ist je JVM-Lauf eine andere: Ohne das Sortieren in der Regel gaebe derselbe
        // Wurf heute Kohle und morgen Kupfer - und zwar auch im Spiel, nach jedem
        // Neustart. Eine Probe mit nur EINER Karte saehe das nie.
        Map<String, Erzwerte.Erzzeile> vorwaerts = new LinkedHashMap<>();
        vorwaerts.put("coal_ore", new Erzwerte.Erzzeile(50, 0));
        vorwaerts.put("copper_ore", new Erzwerte.Erzzeile(30, 0));
        Map<String, Erzwerte.Erzzeile> rueckwaerts = new LinkedHashMap<>();
        rueckwaerts.put("copper_ore", new Erzwerte.Erzzeile(30, 0));
        rueckwaerts.put("coal_ore", new Erzwerte.Erzzeile(50, 0));
        Map<String, Erzwerte.Erzzeile> sortiert = new TreeMap<>(rueckwaerts);

        for (long wurf = 0L; wurf < 80L; wurf++) {
            Optional<String> ausVorwaerts =
                    Erzregel.erz(WERTE.mitTabelle(vorwaerts), 0, 0, wurf);
            assertEquals(ausVorwaerts,
                    Erzregel.erz(WERTE.mitTabelle(rueckwaerts), 0, 0, wurf),
                    "Wurf " + wurf + " trifft je nach Einfuegereihenfolge etwas anderes");
            assertEquals(ausVorwaerts,
                    Erzregel.erz(WERTE.mitTabelle(sortiert), 0, 0, wurf));
        }
    }

    @Test
    @DisplayName("Ohne Werte ist es ein Programmfehler")
    void ohneWerte() {
        assertThrows(IllegalArgumentException.class, () -> Erzregel.erz(null, 0, 0, 0L));
    }

    @Test
    @DisplayName("Die Promillezahl rechnet die Haeufigkeit MIT ein")
    void promille() {
        // Fuer den Startbericht, wie bei den Faengen: Kohle hat bei Stufe 0 das Gewicht
        // 50 von 80, und getauscht wird in 20 von 100 Faellen. Also 1000 * 50 * 20 /
        // (80 * 100) = 125 von tausend Bloecken.
        assertEquals(125L, Erzregel.promilleBeiStufe(WERTE, "coal_ore", 0));
        assertEquals(75L, Erzregel.promilleBeiStufe(WERTE, "copper_ore", 0));
        assertEquals(0L, Erzregel.promilleBeiStufe(WERTE, "diamond_ore", 0),
                "was die Stufe nicht erreicht, kommt nie");
        assertEquals(0L, Erzregel.promilleBeiStufe(WERTE, "gibt_es_nicht", 0));
        assertEquals(0L, Erzregel.promilleBeiStufe(WERTE.mitAn(false), "coal_ore", 0),
                "abgeschaltet kommt auch nichts");
    }
}
