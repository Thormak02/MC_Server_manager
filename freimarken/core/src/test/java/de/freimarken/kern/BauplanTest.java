package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Regel, welche Stelle eines Platzhalterbaus was wird.
 *
 * <p>Die Erwartungen sind GERECHNET und nicht aus {@link Bauplan} geholt: Eine Probe, die
 * ihre Zahl aus der geprueften Funktion nimmt, faengt kein Vertauschen.
 */
@DisplayName("Der Bauplan eines Platzhalterbaus")
class BauplanTest {

    /** Ein Haus von neun Bloecken Kante, auf dem Boden der Ebene (y -61). */
    private static Platzhalterbau haus(Richtung tuer) {
        return new Platzhalterbau("buergeramt",
                new Quader(50, -61, 2, 58, -53, 10), tuer);
    }

    @Test
    @DisplayName("Jede Stelle des Quaders hat genau ein Stueck - und keine daneben")
    void jedeStelleHatEinStueck() {
        Platzhalterbau bau = haus(Richtung.NORD);
        Map<Bauplan.Stueck, Integer> gezaehlt = new EnumMap<>(Bauplan.Stueck.class);
        int gefragt = 0;
        for (int x = 50; x <= 58; x++) {
            for (int y = -61; y <= -53; y++) {
                for (int z = 2; z <= 10; z++) {
                    Bauplan.Stueck stueck = Bauplan.stueckBei(bau, x, y, z);
                    gezaehlt.merge(stueck, 1, Integer::sum);
                    gefragt++;
                }
            }
        }
        // GEZAEHLT: Ein Vorfilter oder eine Grenze um eins daneben machte die Zahlen
        // unten sonst zu einer Rechnung ueber einen kleineren Quader.
        assertEquals(9 * 9 * 9, gefragt, "neun mal neun mal neun Stellen");
        assertEquals(9 * 9 * 9, gezaehlt.values().stream().mapToInt(Integer::intValue).sum());

        // Die Rechnung, Ebene fuer Ebene: Boden 9x9, Dach 9x9, dazwischen sieben Ebenen
        // mit je 9*9 - 7*7 Wandbloecken, und aus der Wand faellt die Tuer heraus.
        assertEquals(9 * 9, gezaehlt.get(Bauplan.Stueck.BODEN), "der Boden ist die Flaeche");
        assertEquals(9 * 9, gezaehlt.get(Bauplan.Stueck.DACH), "das Dach auch");
        assertEquals(2, gezaehlt.get(Bauplan.Stueck.TUER), "zwei Bloecke hoch");
        assertEquals(7 * (9 * 9 - 7 * 7) - 2, gezaehlt.get(Bauplan.Stueck.WAND),
                "sieben Ebenen Wandring, abzueglich der zwei Tuerbloecke");
        assertEquals(7 * 7 * 7, gezaehlt.get(Bauplan.Stueck.LUFT),
                "das Innere - es wird MITGESETZT, damit Bauen und Zuruecksetzen dasselbe"
                        + " sind");
    }

    @Test
    @DisplayName("Die Tuer liegt auf der genannten Seite, mittig, ueber dem Boden")
    void dieTuerLiegtRichtig() {
        // NORD ist negatives Z (Richtung.NORD), die Tuer liegt also in der Wand bei z 2.
        Platzhalterbau nord = haus(Richtung.NORD);
        assertEquals(Bauplan.Stueck.TUER, Bauplan.stueckBei(nord, 54, -60, 2));
        assertEquals(Bauplan.Stueck.TUER, Bauplan.stueckBei(nord, 54, -59, 2));
        // Nicht auf dem Boden und nicht eine Ebene hoeher als zwei.
        assertEquals(Bauplan.Stueck.BODEN, Bauplan.stueckBei(nord, 54, -61, 2));
        assertEquals(Bauplan.Stueck.WAND, Bauplan.stueckBei(nord, 54, -58, 2));
        // Nicht daneben.
        assertEquals(Bauplan.Stueck.WAND, Bauplan.stueckBei(nord, 53, -60, 2));
        assertEquals(Bauplan.Stueck.WAND, Bauplan.stueckBei(nord, 55, -60, 2));
        // Und nicht in der gegenueberliegenden Wand - sonst waere die Seite ohne Wirkung.
        assertEquals(Bauplan.Stueck.WAND, Bauplan.stueckBei(nord, 54, -60, 10));

        // DIE VIER SEITEN SIND VERSCHIEDEN, und das ist die tragende Zusicherung: Eine
        // Richtung, die nichts aendert, ist eine Einstellung ohne Wirkung.
        Map<Richtung, String> stellen = new EnumMap<>(Richtung.class);
        for (Richtung richtung : Richtung.values()) {
            Platzhalterbau bau = haus(richtung);
            String gefunden = null;
            for (int x = 50; x <= 58; x++) {
                for (int z = 2; z <= 10; z++) {
                    if (Bauplan.stueckBei(bau, x, -60, z) == Bauplan.Stueck.TUER) {
                        gefunden = x + "/" + z;
                    }
                }
            }
            stellen.put(richtung, gefunden);
        }
        assertEquals("54/2", stellen.get(Richtung.NORD), "Norden ist negatives Z");
        assertEquals("54/10", stellen.get(Richtung.SUED));
        assertEquals("50/6", stellen.get(Richtung.WEST), "Westen ist negatives X");
        assertEquals("58/6", stellen.get(Richtung.OST));
        assertEquals(4, stellen.values().stream().distinct().count(),
                "vier Seiten, vier verschiedene Stellen");
    }

    @Test
    @DisplayName("Bei drei Bloecken Hoehe ist die Tuer EINEN Block hoch - kein Dachloch")
    void niedrigerBauHatEineNiedrigeTuer() {
        // Boden, eine Reihe Wand, Dach. Eine zwei Bloecke hohe Oeffnung frasse die
        // Dachkante - ein Haus mit einem Loch im Dach ist schlimmer als eines, das man
        // geduckt betritt.
        Platzhalterbau flach = new Platzhalterbau("huette",
                new Quader(0, -61, 0, 4, -59, 4), Richtung.SUED);

        assertEquals(-60, Bauplan.tuerObersteEbene(flach),
                "die oberste Tuerebene liegt unter dem Dach");
        assertEquals(Bauplan.Stueck.TUER, Bauplan.stueckBei(flach, 2, -60, 4));
        assertEquals(Bauplan.Stueck.DACH, Bauplan.stueckBei(flach, 2, -59, 4),
                "das Dach bleibt zu");

        int tuerbloecke = 0;
        for (int y = -61; y <= -59; y++) {
            if (Bauplan.stueckBei(flach, 2, y, 4) == Bauplan.Stueck.TUER) {
                tuerbloecke++;
            }
        }
        assertEquals(1, tuerbloecke);
    }

    @Test
    @DisplayName("Eine Stelle ausserhalb ist ein Programmfehler und keine Luft")
    void ausserhalbWirft() {
        // Ein LUFT als Antwort waere die bequeme Luege: Sie machte einen Zaehler um eins
        // daneben unsichtbar.
        Platzhalterbau bau = haus(Richtung.NORD);

        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> Bauplan.stueckBei(bau, 59, -61, 2));

        assertTrue(wurf.getMessage().contains("59/-61/2"), wurf.getMessage());
        assertTrue(wurf.getMessage().contains("buergeramt"), wurf.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> Bauplan.stueckBei(bau, 54, -62, 5), "eine Ebene unter dem Boden");
        assertThrows(IllegalArgumentException.class,
                () -> Bauplan.stueckBei(bau, 54, -52, 5), "eine Ebene ueber dem Dach");
        assertThrows(IllegalArgumentException.class,
                () -> Bauplan.stueckBei(null, 0, 0, 0));
    }

    @Test
    @DisplayName("Ein Bau von genau drei Bloecken Kante hat ein Inneres - und einen Block davon")
    void kleinsterBau() {
        Platzhalterbau klein = new Platzhalterbau("kiosk",
                new Quader(0, -61, 0, 2, -59, 2), Richtung.OST);

        int luft = 0;
        for (int x = 0; x <= 2; x++) {
            for (int y = -61; y <= -59; y++) {
                for (int z = 0; z <= 2; z++) {
                    if (Bauplan.stueckBei(klein, x, y, z) == Bauplan.Stueck.LUFT) {
                        luft++;
                    }
                }
            }
        }
        assertEquals(1, luft, "genau der Mittelblock");
        assertEquals(Bauplan.Stueck.LUFT, Bauplan.stueckBei(klein, 1, -60, 1));
    }
}
