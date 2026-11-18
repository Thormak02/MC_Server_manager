package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die laufende Blockzaehlung - Teilsummen und Punkte, ohne Bukkit. */
@DisplayName("Der Zaehlauftrag einer Insel")
class ZaehlauftragTest {

    private static final Wolkenplan PLAN = Wolkenplan.wolkenwerder();

    private static final Punktwerte WERTE = new Punktwerte(
            Map.of("diamond_block", 900L, "glass", 2L), 1, 60L);

    private static Zaehlauftrag auftrag() {
        return new Zaehlauftrag(7L, PLAN, 1, -1);
    }

    @Test
    @DisplayName("Was in den Happen lag, wird ADDIERT")
    void happenWerdenAddiert() {
        Zaehlauftrag auftrag = auftrag();

        auftrag.nimm(Map.of("glass", 10L, "diamond_block", 1L));
        auftrag.nimm(Map.of("glass", 5L));

        assertEquals(Map.of("glass", 15L, "diamond_block", 1L), auftrag.gefunden());
    }

    @Test
    @DisplayName("Die Punkte werden GERECHNET, nicht mitgezaehlt")
    void punkteWerdenGerechnet() {
        // Der Grund: Die Tabelle ist neuladbar. Eine Summe, die beim Finden entsteht,
        // waere nach einer Aenderung eine Zahl aus zwei Tabellen - und niemand koennte
        // sie nachrechnen.
        Zaehlauftrag auftrag = auftrag();
        auftrag.nimm(Map.of("glass", 10L, "diamond_block", 2L));

        assertEquals(10L * 2L + 2L * 900L, auftrag.punkte(WERTE));
        assertEquals(10L * 5L + 2L * 900L,
                auftrag.punkte(WERTE.mitTabelle(
                        Map.of("diamond_block", 900L, "glass", 5L))),
                "dieselben Bloecke, andere Tabelle, andere Punkte");
    }

    @Test
    @DisplayName("Was nicht in der Tabelle steht, bringt NICHTS - zaehlt aber mit")
    void unbekannteBloeckeZaehlenNicht() {
        // Gezaehlt wird alles, bewertet nur die Tabelle. Das ist Absicht: Ein Betreiber,
        // der morgen Kobbelstein in die Tabelle schreibt, bekommt dieselbe Zaehlung mit
        // einer anderen Zahl - ohne neu zaehlen zu muessen.
        Zaehlauftrag auftrag = auftrag();
        auftrag.nimm(Map.of("cobblestone", 1_000_000L));

        assertEquals(0L, auftrag.punkte(WERTE));
        assertEquals(1_000_000L, auftrag.gefunden().get("cobblestone"));
    }

    @Test
    @DisplayName("Leere und unsinnige Happen aendern nichts")
    void leereHappen() {
        Zaehlauftrag auftrag = auftrag();

        auftrag.nimm(null);
        auftrag.nimm(Map.of());
        auftrag.nimm(new java.util.HashMap<>(Map.of("glass", 0L)));

        assertEquals(Map.of(), auftrag.gefunden());
        assertEquals(0L, auftrag.punkte(WERTE));
    }

    @Test
    @DisplayName("Der Auftrag kennt sein Feld - 64 Chunks und seine Insel")
    void kenntSeinFeld() {
        Zaehlauftrag auftrag = auftrag();

        assertEquals(7L, auftrag.inselId());
        assertEquals(64, auftrag.chunkzahl());
        assertEquals(1, auftrag.naechsterHappen(1).size());
        assertEquals(1, auftrag.fortschrittProzent());
    }

    @Test
    @DisplayName("Ohne Nummer, ohne Plan und ohne Werte ist es ein Programmfehler")
    void riegel() {
        assertThrows(IllegalArgumentException.class,
                () -> new Zaehlauftrag(0L, PLAN, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Zaehlauftrag(-1L, PLAN, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Zaehlauftrag(1L, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> auftrag().punkte(null));
    }
}
