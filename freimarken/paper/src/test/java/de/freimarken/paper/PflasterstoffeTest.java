package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Stadtplan;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Womit der Boden Altfurts belegt wird. */
@DisplayName("Das Pflaster Altfurts")
class PflasterstoffeTest {

    @Test
    @DisplayName("Jede Lage hat einen Stoff - nur AUSSEN nicht, und das mit Absicht")
    void jedeLage() {
        Pflasterstoffe stoffe = Pflasterstoffe.abWerk();

        for (Stadtplan.Lage lage : Stadtplan.Lage.values()) {
            if (lage == Stadtplan.Lage.AUSSEN) {
                // LUFT WAERE FALSCH: Sie ist ein Block und wuerde den Boden LOESCHEN.
                assertNull(stoffe.block(lage), "jenseits der Mauer wird nicht gepflastert");
                continue;
            }
            assertNotNull(stoffe.block(lage), "Lage " + lage + " ohne Stoff - der Boden"
                    + " bliebe dort, wie der Generator ihn gemacht hat");
        }
        // Und keiner davon ist Luft.
        for (Stadtplan.Lage lage : Stadtplan.Lage.values()) {
            Material block = stoffe.block(lage);
            assertTrue(block == null || !block.name().equals("AIR"),
                    "Lage " + lage + " ist Luft");
        }
    }

    @Test
    @DisplayName("Ohne Lage gibt es keine Antwort")
    void ohneLage() {
        assertThrows(IllegalArgumentException.class,
                () -> Pflasterstoffe.abWerk().block(null));
        assertThrows(IllegalArgumentException.class, () -> new Pflasterstoffe(null));
    }

    @Test
    @DisplayName("Jeder Schluessel der Datei kommt an")
    void jederSchluesselKommtAn() {
        // JEDE Lage wird eingetragen, jede mit einem ANDEREN Block - sonst saehe eine
        // Zuordnung, die zwei Lagen vertauscht, aus wie eine richtige.
        Map<Stadtplan.Lage, Material> gewuenscht = new EnumMap<>(Stadtplan.Lage.class);
        gewuenscht.put(Stadtplan.Lage.MARKT, Material.GOLD_BLOCK);
        gewuenscht.put(Stadtplan.Lage.KERN, Material.IRON_BLOCK);
        gewuenscht.put(Stadtplan.Lage.RINGGASSE, Material.DIAMOND_BLOCK);
        gewuenscht.put(Stadtplan.Lage.RADIALGASSE, Material.EMERALD_BLOCK);
        gewuenscht.put(Stadtplan.Lage.ECKPLATZ, Material.LAPIS_BLOCK);
        gewuenscht.put(Stadtplan.Lage.ZEILE, Material.COAL_BLOCK);
        gewuenscht.put(Stadtplan.Lage.MAUER, Material.REDSTONE_BLOCK);
        gewuenscht.put(Stadtplan.Lage.AUSSEN, Material.NETHERITE_BLOCK);

        YamlConfiguration datei = new YamlConfiguration();
        gewuenscht.forEach((lage, block) -> datei.set(Pflasterstoffe.PFAD + "."
                + Pflasterstoffe.schluesselVon(lage),
                block.name().toLowerCase(java.util.Locale.ROOT)));

        List<String> klagen = new ArrayList<>();
        Pflasterstoffe stoffe = Pflasterstoffe.ausKonfiguration(datei, klagen);

        for (Stadtplan.Lage lage : Stadtplan.Lage.values()) {
            if (lage == Stadtplan.Lage.AUSSEN) {
                continue;
            }
            assertEquals(gewuenscht.get(lage), stoffe.block(lage), "Lage " + lage);
        }
        // AUSSEN LAESST SICH NICHT AUFSCHLIESSEN, und das wird GESAGT: Das Pflaster
        // geht nur den Umriss der Stadt ab, und dort kommt die Lage nie vor. Ein
        // Schluessel, der gelesen wird und nichts tut, ist schlimmer als keiner.
        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("aussen"), klagen.get(0));
        assertTrue(klagen.get(0).contains("keine Wirkung"), klagen.get(0));
        assertNull(stoffe.block(Stadtplan.Lage.AUSSEN),
                "und der Wert kommt nicht an");
    }

    @Test
    @DisplayName("Die Schluessel sind die Namen in Kleinbuchstaben")
    void dieSchluessel() {
        assertEquals("markt", Pflasterstoffe.schluesselVon(Stadtplan.Lage.MARKT));
        assertEquals("ringgasse", Pflasterstoffe.schluesselVon(Stadtplan.Lage.RINGGASSE));
        assertEquals("radialgasse",
                Pflasterstoffe.schluesselVon(Stadtplan.Lage.RADIALGASSE));
        assertEquals("eckplatz", Pflasterstoffe.schluesselVon(Stadtplan.Lage.ECKPLATZ));
        // UND JEDER ist ein brauchbarer Schluessel: keine Unterstriche, keine Punkte.
        for (Stadtplan.Lage lage : Stadtplan.Lage.values()) {
            assertTrue(Pflasterstoffe.schluesselVon(lage).matches("[a-z]+"),
                    lage + " gibt den Schluessel \""
                            + Pflasterstoffe.schluesselVon(lage) + "\"");
        }
    }

    @Test
    @DisplayName("Ein unbekannter Block wird geklagt, und es bleibt beim Werk")
    void unbekannterBlock() {
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Pflasterstoffe.PFAD + ".markt", "kopfsteinpflaster");
        datei.set(Pflasterstoffe.PFAD + ".zeile", "gravel");

        List<String> klagen = new ArrayList<>();
        Pflasterstoffe stoffe = Pflasterstoffe.ausKonfiguration(datei, klagen);

        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("markt"), klagen.get(0));
        assertTrue(klagen.get(0).contains("kopfsteinpflaster"), klagen.get(0));
        // Der Werkstoff bleibt - und der NACHBARSCHLUESSEL wirkt trotzdem.
        assertEquals(Pflasterstoffe.abWerk().block(Stadtplan.Lage.MARKT),
                stoffe.block(Stadtplan.Lage.MARKT), "Markt bleibt beim Werk");
        assertEquals(Material.GRAVEL, stoffe.block(Stadtplan.Lage.ZEILE), "Zeile wirkt");
    }

    @Test
    @DisplayName("Ohne Abschnitt und mit leerem Wert gilt das Werk")
    void ohneAbschnitt() {
        List<String> klagen = new ArrayList<>();
        assertEquals(Pflasterstoffe.abWerk(),
                Pflasterstoffe.ausKonfiguration(new YamlConfiguration(), klagen),
                "ohne Abschnitt");
        assertEquals(Pflasterstoffe.abWerk(), Pflasterstoffe.ausKonfiguration(null, klagen),
                "ohne Datei");

        // EIN SCHLUESSEL OHNE WERT kommt als null an - das darf nicht wie ein leerer
        // Stoff wirken, sonst waere der Markt ploetzlich unberuehrt.
        YamlConfiguration leer = new YamlConfiguration();
        leer.set(Pflasterstoffe.PFAD + ".markt", "");
        leer.set(Pflasterstoffe.PFAD + ".mauer", "   ");
        Pflasterstoffe stoffe = Pflasterstoffe.ausKonfiguration(leer, klagen);
        assertEquals(Pflasterstoffe.abWerk().block(Stadtplan.Lage.MARKT),
                stoffe.block(Stadtplan.Lage.MARKT), "leerer Wert");
        assertEquals(Pflasterstoffe.abWerk().block(Stadtplan.Lage.MAUER),
                stoffe.block(Stadtplan.Lage.MAUER), "nur Leerzeichen");
        assertEquals(List.of(), klagen, "ein fehlender Wert ist keine Klage");
    }

    @Test
    @DisplayName("Der Bericht nennt jede Lage, die gepflastert wird")
    void derBericht() {
        String bericht = Pflasterstoffe.abWerk().bericht();
        for (Stadtplan.Lage lage : Stadtplan.Lage.values()) {
            if (lage == Stadtplan.Lage.AUSSEN) {
                continue;
            }
            assertTrue(bericht.contains(Pflasterstoffe.schluesselVon(lage)),
                    lage + " fehlt im Bericht: " + bericht);
        }
        // AUSSEN steht NICHT drin - dort wird nicht gepflastert.
        assertTrue(!bericht.contains("aussen"), bericht);
    }
}
