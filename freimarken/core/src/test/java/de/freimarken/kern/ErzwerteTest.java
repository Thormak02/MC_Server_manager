package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die Tabelle des Kobbelsteingenerators - Rechnerei ohne Bukkit. */
@DisplayName("Die Erzwerte von Wolkenwerder")
class ErzwerteTest {

    private static final Erzwerte.Erzzeile KOHLE = new Erzwerte.Erzzeile(50, 0);
    private static final Erzwerte.Erzzeile EISEN = new Erzwerte.Erzzeile(25, 1);
    private static final Erzwerte.Erzzeile DIAMANT = new Erzwerte.Erzzeile(3, 5);

    private static Erzwerte werte(int grund, int jeStufe, int hoechst) {
        return new Erzwerte(true, grund, jeStufe, hoechst, true,
                Map.of("coal_ore", KOHLE, "iron_ore", EISEN, "diamond_ore", DIAMANT));
    }

    @Test
    @DisplayName("Die Ausbeute steigt mit der Stufe - bis zum DECKEL")
    void ausbeuteSteigtBisZumDeckel() {
        // Der Deckel ist die tragende Zahl: Bei Stufe 50 und zwei Punkten je Stufe waeren
        // es 115 Prozent, und das hiesse "immer" - der Kobbelstein, aus dem alles Weitere
        // gebaut wird, kaeme nie mehr.
        Erzwerte werte = werte(15, 2, 40);

        assertEquals(15, werte.prozentFuer(0));
        assertEquals(17, werte.prozentFuer(1));
        assertEquals(39, werte.prozentFuer(12));
        assertEquals(40, werte.prozentFuer(13), "ab hier greift der Deckel");
        assertEquals(40, werte.prozentFuer(1_000), "und er bleibt bindend");
    }

    @Test
    @DisplayName("Ohne Steigerung bleibt es beim Grundwert - auch bei hoher Stufe")
    void ohneSteigerung() {
        // Erlaubt und nicht sinnlos: So bekommt eine Welt Erz, in der die Stufe nur
        // ueber die TABELLE wirkt - erst Kohle, ab Stufe 5 Diamant, aber immer gleich oft.
        Erzwerte werte = werte(20, 0, 40);

        assertEquals(20, werte.prozentFuer(0));
        assertEquals(20, werte.prozentFuer(99));
    }

    @Test
    @DisplayName("Eine negative Stufe gibt 0 und keinen Wurf")
    void negativeStufe() {
        // Kommen kann sie nur aus einem Programmfehler. Ein Wurf hier waere ein
        // Generator, der bei jedem Block eine Ausnahme wirft - die unerfreulichste Art,
        // einen Zahlendreher zu erfahren.
        assertEquals(0, werte(15, 2, 40).prozentFuer(-1));
    }

    @Test
    @DisplayName("Abgeschaltet, leer oder mit Deckel 0 greift die Tabelle nicht")
    void greiftNicht() {
        assertFalse(werte(15, 2, 40).mitAn(false).greift());
        assertFalse(werte(15, 2, 40).mitTabelle(Map.of()).greift(),
                "eine leere Tabelle ist erlaubt und heisst: nur Kobbelstein");
        assertFalse(new Erzwerte(true, 0, 0, 0, true, Map.of("coal_ore", KOHLE)).greift(),
                "ein Deckel von 0 ist dasselbe wie aus");
        assertEquals(0, werte(15, 2, 40).mitAn(false).prozentFuer(9),
                "und dann ist auch die Haeufigkeit 0, nicht 19");
    }

    @Test
    @DisplayName("Die Gewichtssumme zaehlt NUR die Zeilen, die die Stufe erreichen")
    void gewichtsummeNachStufe() {
        // Daran haengt die ganze Fortschrittsidee: Diamant ab Stufe 5 heisst, dass er in
        // der Summe der Stufe 4 nicht vorkommen darf - sonst wuerde er gezogen und waere
        // doch da.
        Erzwerte werte = werte(15, 2, 40);

        assertEquals(50L, werte.gewichtsumme(0));
        assertEquals(75L, werte.gewichtsumme(1));
        assertEquals(75L, werte.gewichtsumme(4));
        assertEquals(78L, werte.gewichtsumme(5));
    }

    @Test
    @DisplayName("Eine Tabelle, deren niedrigste Zeile erst spaeter greift, gibt 0")
    void keineZeileErreichtDieStufe() {
        // Der Fall, den die Regel abfangen muss: Der Prozentwurf trifft, und es gibt
        // nichts zu ziehen. Ohne diese 0 zeigte ein Wurf auf eine Zeile, die es nicht
        // gibt.
        Erzwerte werte = new Erzwerte(true, 15, 2, 40, true,
                Map.of("diamond_ore", DIAMANT));

        assertEquals(0L, werte.gewichtsumme(0));
        assertEquals(3L, werte.gewichtsumme(5));
    }

    @Test
    @DisplayName("Nur Kobbelstein und Stein sind der AUSGANG - und zwar egal geschrieben")
    void ausgang() {
        // Nicht einstellbar, und das ist der Grund: Dasselbe Bukkit-Ereignis bildet auch
        // Schnee, Eis und Beton. Ein Diamant aus Schnee waere ein Fehler, den niemand als
        // Einstellung gemeint haben kann.
        assertTrue(Erzwerte.istAusgang("cobblestone"));
        assertTrue(Erzwerte.istAusgang("COBBLESTONE"), "so kommt es aus Material.name()");
        assertTrue(Erzwerte.istAusgang("  Stone  "));
        assertFalse(Erzwerte.istAusgang("cobblestone_stairs"));
        assertFalse(Erzwerte.istAusgang("snow"));
        assertFalse(Erzwerte.istAusgang("obsidian"),
                "Obsidian entsteht aus Lava und Wasser, ist aber kein Generator - wer"
                        + " seine Quelle opfert, soll dafuer keinen Diamanten bekommen");
        assertFalse(Erzwerte.istAusgang(null));
    }

    @Test
    @DisplayName("Ein Name wird kleingeschrieben und beschnitten abgelegt")
    void nameWirdAbgelegt() {
        // Dieselbe Linie wie bei den Angelfaengen: Die Datei schreibt, was der Betreiber
        // tippt, und gefragt wird mit dem Namen aus Material.name().toLowerCase.
        Erzwerte werte = werte(15, 2, 40).mitTabelle(Map.of("  Coal_Ore  ", KOHLE));

        assertEquals(Map.of("coal_ore", KOHLE), werte.tabelle());
    }

    @Test
    @DisplayName("Ein Deckel UNTER dem Grundwert ist ein Programmfehler")
    void deckelUnterGrund() {
        // Sonst saenke die Ausbeute mit der ersten Stufe. Das ist kein Fortschritt,
        // sondern das Gegenteil - und im Spiel haette es niemand gemeint.
        IllegalArgumentException wurf = assertThrows(IllegalArgumentException.class,
                () -> werte(40, 2, 15));
        assertTrue(wurf.getMessage().contains("saenke"), wurf.getMessage());
    }

    @Test
    @DisplayName("Prozente ausserhalb von 0 bis 100, Gewicht 0 und Stufe -1 werfen")
    void riegel() {
        assertThrows(IllegalArgumentException.class, () -> werte(-1, 2, 40));
        assertThrows(IllegalArgumentException.class, () -> werte(15, 101, 40));
        assertThrows(IllegalArgumentException.class, () -> werte(15, 2, 101));
        assertThrows(IllegalArgumentException.class, () -> new Erzwerte.Erzzeile(0, 0),
                "wer eine Zeile abschalten will, nimmt sie heraus");
        assertThrows(IllegalArgumentException.class, () -> new Erzwerte.Erzzeile(1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new Erzwerte.Erzzeile(1, Erzwerte.HOECHSTE_STUFE + 1),
                "eine Zeile oberhalb der hoechsten Stufe waere eine, die nie kommt");
        assertThrows(IllegalArgumentException.class,
                () -> new Erzwerte(true, 15, 2, 40, true, null));
        assertThrows(IllegalArgumentException.class,
                () -> werte(15, 2, 40).mitTabelle(Map.of(" ", KOHLE)));
    }

    @Test
    @DisplayName("Die ausgelieferte Tabelle hat neun Erze und zwei von Anfang an")
    void vorgabe() {
        // Die Zahlen der Vorgabe stehen hier, weil sie der Schwierigkeitsgrad dieser Welt
        // SIND: Zwei Erze bei Stufe 0 heissen, dass die erste Herausforderung wirklich
        // etwas aufschliesst - naemlich das Eisen.
        assertEquals(9, Erzwerte.VORGABE_TABELLE.size());
        assertEquals(2, Erzwerte.VORGABE_TABELLE.values().stream()
                .filter(zeile -> zeile.abStufe() == 0).count());
        assertEquals(1, Erzwerte.VORGABE_TABELLE.get("iron_ore").abStufe(),
                "Eisen ab Stufe 1 - der Lohn der ersten Aufgabe");
        assertTrue(Erzwerte.VORGABE.greift());
        assertEquals(15, Erzwerte.VORGABE.prozentFuer(0));

        for (var zeile : Erzwerte.VORGABE_TABELLE.entrySet()) {
            assertFalse(Erzwerte.istAusgang(zeile.getKey()),
                    zeile.getKey() + " ist der Block, AUS dem getauscht wird - eine"
                            + " Zeile darueber taete nichts");
        }
    }

    @Test
    @DisplayName("Jeder Wither laesst alles andere stehen")
    void wither() {
        Erzwerte werte = werte(15, 2, 40);

        assertFalse(werte.mitAn(false).an());
        assertEquals(werte.tabelle(), werte.mitAn(false).tabelle());
        assertEquals(7, werte.mitProzent(7, 1, 9).grundProzent());
        assertEquals(werte.marken(), werte.mitProzent(7, 1, 9).marken());
        assertFalse(werte.mitMarken(false).marken());
        assertEquals(15, werte.mitMarken(false).grundProzent());
        assertEquals(Map.of("coal_ore", KOHLE),
                werte.mitTabelle(Map.of("coal_ore", KOHLE)).tabelle());
        assertTrue(werte.mitTabelle(Map.of("coal_ore", KOHLE)).an());
    }
}
