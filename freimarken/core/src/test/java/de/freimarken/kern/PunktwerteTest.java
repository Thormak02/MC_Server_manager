package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Was eine Wolkeninsel wert ist - die Punkte je Block. */
@DisplayName("Die Punktwerte von Wolkenwerder")
class PunktwerteTest {

    @Test
    @DisplayName("Die Punkte eines Blocks kommen zurueck - egal wie geschrieben")
    void punkteFuer() {
        Punktwerte werte = new Punktwerte(Map.of("diamond_block", 900L), 1, 60L);

        assertEquals(900L, werte.punkteFuer("diamond_block"));
        assertEquals(900L, werte.punkteFuer("DIAMOND_BLOCK"),
                "so kommt es aus Material.name()");
        assertEquals(900L, werte.punkteFuer("  diamond_block  "));
        assertEquals(0L, werte.punkteFuer("cobblestone"),
                "was nicht in der Tabelle steht, zaehlt nicht");
        assertEquals(0L, werte.punkteFuer(null));
    }

    @Test
    @DisplayName("Eine leere Tabelle greift nicht - dann ist jede Insel 0 wert")
    void greiftNicht() {
        assertTrue(Punktwerte.VORGABE.greift());
        assertFalse(Punktwerte.VORGABE.mitTabelle(Map.of()).greift(),
                "erlaubt und heisst: keine Rangliste nach Punkten");
    }

    @Test
    @DisplayName("Punkte 0, ein Happen von 0 und eine negative Pause sind Fehler")
    void riegel() {
        assertThrows(IllegalArgumentException.class,
                () -> new Punktwerte(Map.of("stone", 0L), 1, 60L),
                "wer einen Block nicht zaehlen will, nimmt ihn heraus");
        assertThrows(IllegalArgumentException.class,
                () -> new Punktwerte(Map.of("stone", Punktwerte.HOECHSTE_PUNKTE + 1L), 1,
                        60L));
        assertThrows(IllegalArgumentException.class,
                () -> new Punktwerte(Map.of(" ", 1L), 1, 60L));
        assertThrows(IllegalArgumentException.class, () -> new Punktwerte(null, 1, 60L));
        assertThrows(IllegalArgumentException.class,
                () -> new Punktwerte(Map.of("stone", 1L), 0, 60L),
                "ein Happen von 0 Chunks kommt nie weiter");
        assertThrows(IllegalArgumentException.class,
                () -> new Punktwerte(Map.of("stone", 1L),
                        Punktwerte.HOECHSTENS_JE_TAKT + 1, 60L));
        assertThrows(IllegalArgumentException.class,
                () -> new Punktwerte(Map.of("stone", 1L), 1, -1L));
    }

    @Test
    @DisplayName("Die ausgelieferte Tabelle zaehlt NICHTS, was der Generator schenkt")
    void vorgabeOhneGeschenke() {
        // Die tragende Entscheidung dieser Tabelle: Eine Insel aus einer Million
        // Kobbelsteinen ist keine Leistung - der Generator gibt sie umsonst. Stuende er
        // darin, waere die Rangliste eine Liste der Geduldigsten.
        for (String ausgang : Erzwerte.AUSGANG) {
            assertEquals(0L, Punktwerte.VORGABE.punkteFuer(ausgang),
                    ausgang + " steht in der Punktetabelle - das schenkt der Generator");
        }
        assertTrue(Punktwerte.VORGABE.punkteFuer("netherite_block")
                        > Punktwerte.VORGABE.punkteFuer("diamond_block"),
                "Netherit kommt nur aus der letzten Erzstufe und steht darum darueber");
        assertEquals(9L * Punktwerte.VORGABE.punkteFuer("coal_block") / 9L,
                Punktwerte.VORGABE.punkteFuer("copper_block"),
                "Kohle und Kupfer kommen beide ab Stufe 0 und wiegen gleich");
    }

    @Test
    @DisplayName("Jeder Wither laesst seine Nachbarn stehen")
    void wither() {
        Punktwerte werte = new Punktwerte(Map.of("stone", 1L), 2, 60L);

        assertEquals(2, werte.mitTabelle(Map.of("dirt", 1L)).chunksJeTakt());
        assertEquals(60L, werte.mitTabelle(Map.of("dirt", 1L)).abklingSekunden());
        assertEquals(Map.of("stone", 1L), werte.mitChunksJeTakt(5).tabelle());
        assertEquals(5, werte.mitChunksJeTakt(5).chunksJeTakt());
        assertEquals(2, werte.mitAbklingzeit(0L).chunksJeTakt());
        assertEquals(0L, werte.mitAbklingzeit(0L).abklingSekunden());
    }

    @Test
    @DisplayName("Die Punktwerte liegen im EINEN Wirtschaftssatz")
    void imWirtschaftssatz() {
        assertEquals(Punktwerte.VORGABE, Wirtschaftswerte.VORGABE.wolken().punkte());

        Punktwerte eigene = Punktwerte.VORGABE.mitChunksJeTakt(4);
        Wirtschaftswerte geaendert = Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitPunkten(eigene));

        assertEquals(eigene, geaendert.wolken().punkte());
        assertEquals(Aufgabenwerte.VORGABE, geaendert.wolken().aufgaben(),
                "und die Aufgabenliste daneben bleibt stehen");
    }
}
