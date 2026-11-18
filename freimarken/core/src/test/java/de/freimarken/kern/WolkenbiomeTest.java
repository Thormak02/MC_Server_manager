package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Biomtafel von Wolkenwerder.
 *
 * <p>Sie steht im Kern und nicht im Plugin, und genau das prueft diese Probe mit: Jede
 * Frage nach einem Biomnamen muss OHNE laufenden Server zu beantworten sein. Minecrafts
 * Biome liegen in einem Verzeichnis, das ohne Server wirft - waere die Tafel dort, koennte
 * weder der Leser der Einstellungsdatei noch eine Probe einen Namen pruefen.
 */
@DisplayName("Die Biome von Wolkenwerder")
class WolkenbiomeTest {

    @Test
    @DisplayName("Zehn Biome, jedes einmal, und die Vorgabe ist darunter")
    void dieTafel() {
        assertEquals(10, Wolkenbiome.NAMEN.size());
        assertEquals(Wolkenbiome.NAMEN.size(), Set.copyOf(Wolkenbiome.NAMEN).size(),
                "kein Name zweimal");
        assertTrue(Wolkenbiome.NAMEN.contains(Wolkenbiome.VORGABE),
                "der Grundton der Welt muss selbst waehlbar sein - sonst kaeme niemand"
                        + " zu ihm zurueck");
        assertEquals("plains", Wolkenbiome.VORGABE);
    }

    @Test
    @DisplayName("Die vier Biome, die etwas KOENNEN, stehen darin")
    void dieVierMitWirkung() {
        // Ein Biom ist nicht nur eine Farbe. Diese vier sind der Grund, warum es die Wahl
        // ueberhaupt gibt - faellt eines heraus, faellt ein Stueck Spiel mit.
        assertTrue(Wolkenbiome.gibtEs("swamp"), "Schleim, und damit Kolben und Lasso");
        assertTrue(Wolkenbiome.gibtEs("snowy_plains"), "Eis, und damit Wasser");
        assertTrue(Wolkenbiome.gibtEs("mushroom_fields"), "keine Monster");
        assertTrue(Wolkenbiome.gibtEs("desert"), "kein Regen - der Kessel bleibt leer");
    }

    @Test
    @DisplayName("Gefragt wird beschnitten und kleingeschrieben")
    void wieAbgelegt() {
        assertTrue(Wolkenbiome.gibtEs("SWAMP"));
        assertTrue(Wolkenbiome.gibtEs("  Swamp  "));
        assertEquals("swamp", Wolkenbiome.wieAbgelegt("  SWAMP "));
        assertEquals("", Wolkenbiome.wieAbgelegt(null),
                "kein Wurf - gefragt wird mit Namen aus einer Datei");
        assertFalse(Wolkenbiome.gibtEs(null));
    }

    @Test
    @DisplayName("Was Minecraft nicht kennt, kennt die Tafel auch nicht")
    void unbekannteNamen() {
        assertFalse(Wolkenbiome.gibtEs("mondwueste"));
        assertFalse(Wolkenbiome.gibtEs(""));
        assertFalse(Wolkenbiome.gibtEs("the_void"),
                "die Leere ist KEIN Angebot: Sie laesst nichts erscheinen und nichts"
                        + " wachsen - eine Insel darin waere eine ohne Spiel");
        assertFalse(Wolkenbiome.gibtEs("nether_wastes"),
                "und kein Nether-Biom: Dort brennt Wasser, und die Insel waere nicht"
                        + " mehr zu bewohnen");
    }

    @Test
    @DisplayName("Jeder Name ist ein Minecraft-Schluessel - klein, ohne Leerzeichen")
    void jederNameIstEinSchluessel() {
        // Die Namen gehen in Minecrafts Verzeichnis, und dort heisst alles so. Ein Name
        // mit Grossbuchstaben oder Leerzeichen waere einer, zu dem es kein Biom gibt -
        // und das faellt erst auf einem laufenden Server auf.
        for (String name : Wolkenbiome.NAMEN) {
            assertEquals(name, Wolkenbiome.wieAbgelegt(name), name + " ist nicht"
                    + " abgelegt, wie er dasteht");
            assertTrue(name.matches("[a-z][a-z0-9_]*"),
                    name + " ist kein Minecraft-Schluessel");
        }
        assertEquals(List.copyOf(Wolkenbiome.NAMEN), Wolkenbiome.NAMEN,
                "die Liste ist unveraenderlich");
    }
}
