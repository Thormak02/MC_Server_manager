package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Stadtplan;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Womit der Boden Altfurts belegt wird - je Lage eine Mischung. */
@DisplayName("Das Pflaster Altfurts")
class PflasterstoffeTest {

    @Test
    @DisplayName("Jede Lage hat eine Mischung - nur AUSSEN nicht, und das mit Absicht")
    void jedeLage() {
        Pflasterstoffe stoffe = Pflasterstoffe.abWerk();

        for (Stadtplan.Lage lage : Stadtplan.Lage.values()) {
            if (lage == Stadtplan.Lage.AUSSEN) {
                // LUFT WAERE FALSCH: Sie ist ein Block und wuerde den Boden LOESCHEN.
                assertEquals(List.of(), stoffe.mischung(lage),
                        "jenseits der Mauer wird nicht gepflastert");
                assertNull(stoffe.block(lage, 0, 0), "und kein Block kommt heraus");
                continue;
            }
            assertFalse(stoffe.mischung(lage).isEmpty(), "Lage " + lage + " ohne Stoff -"
                    + " der Boden bliebe, wie der Generator ihn gemacht hat");
            assertNotNull(stoffe.block(lage, 0, 0), "Lage " + lage);
        }
    }

    @Test
    @DisplayName("Keine Mischung enthaelt Luft - sie wuerde den Boden loeschen")
    void keineLuft() {
        for (Stadtplan.Lage lage : Stadtplan.Lage.values()) {
            for (Material block : Pflasterstoffe.abWerk().mischung(lage)) {
                assertFalse(block.name().equals("AIR"), lage + " enthaelt Luft");
            }
        }
        // UND DER RIEGEL GREIFT: Eine Tafel mit Luft darin laesst sich nicht bauen.
        Map<Stadtplan.Lage, List<Material>> mitLuft =
                new EnumMap<>(Stadtplan.Lage.class);
        mitLuft.put(Stadtplan.Lage.MARKT, List.of(Material.AIR));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new Pflasterstoffe(mitLuft)).getMessage().contains("LOESCHT"));
    }

    @Test
    @DisplayName("Die Mischung wechselt den Block von Stelle zu Stelle")
    void dieMischungWechselt() {
        Pflasterstoffe stoffe = Pflasterstoffe.abWerk();
        List<Material> gasse = stoffe.mischung(Stadtplan.Lage.RINGGASSE);
        assertTrue(gasse.size() >= 4, "die Gasse ist gemischt: " + gasse);

        // JEDER Block der Mischung kommt auf einer Flaeche von 40x40 wirklich vor -
        // sonst waere ein Eintrag in der Datei ohne Wirkung.
        Set<Material> gesehen = new HashSet<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                gesehen.add(stoffe.block(Stadtplan.Lage.RINGGASSE, x, z));
            }
        }
        assertEquals(new HashSet<>(gasse), gesehen, "jeder Block der Mischung kommt vor");

        // UND DIESELBE STELLE GIBT IMMER DASSELBE - sonst waere die Stadt nach jedem
        // Neustart anders gepflastert.
        for (int i = 0; i < 20; i++) {
            assertEquals(stoffe.block(Stadtplan.Lage.RINGGASSE, 7, 13),
                    stoffe.block(Stadtplan.Lage.RINGGASSE, 7, 13), "fest");
        }

        // DIE GEWICHTUNG WIRKT: Kopfstein steht viermal in der Werkmischung der Gasse
        // und muss darum deutlich oefter liegen als Kies, der einmal darin steht.
        int kopfstein = 0;
        int kies = 0;
        for (int x = 0; x < 60; x++) {
            for (int z = 0; z < 60; z++) {
                Material block = stoffe.block(Stadtplan.Lage.RINGGASSE, x, z);
                if (block == Material.COBBLESTONE) {
                    kopfstein++;
                } else if (block == Material.GRAVEL) {
                    kies++;
                }
            }
        }
        assertTrue(kopfstein > 2 * kies, "Gewichtung durch Wiederholung: " + kopfstein
                + " Kopfstein gegen " + kies + " Kies");
    }

    @Test
    @DisplayName("Ohne Lage gibt es keine Antwort")
    void ohneLage() {
        assertThrows(IllegalArgumentException.class,
                () -> Pflasterstoffe.abWerk().mischung(null));
        assertThrows(IllegalArgumentException.class,
                () -> Pflasterstoffe.abWerk().block(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Pflasterstoffe(null));
    }

    @Test
    @DisplayName("Jeder Schluessel der Datei kommt an - einzeln und als Liste")
    void jederSchluesselKommtAn() {
        // JEDE Lage wird eingetragen, jede mit einem ANDEREN Block - sonst saehe eine
        // Zuordnung, die zwei Lagen vertauscht, aus wie eine richtige.
        Map<Stadtplan.Lage, Material> gewuenscht = new EnumMap<>(Stadtplan.Lage.class);
        gewuenscht.put(Stadtplan.Lage.MARKT, Material.GOLD_BLOCK);
        gewuenscht.put(Stadtplan.Lage.KIRCHPLATZ, Material.NETHERITE_BLOCK);
        gewuenscht.put(Stadtplan.Lage.RATHAUSPLATZ, Material.COPPER_BLOCK);
        gewuenscht.put(Stadtplan.Lage.PLATZKANTE, Material.AMETHYST_BLOCK);
        gewuenscht.put(Stadtplan.Lage.KERN, Material.IRON_BLOCK);
        gewuenscht.put(Stadtplan.Lage.RINGGASSE, Material.DIAMOND_BLOCK);
        gewuenscht.put(Stadtplan.Lage.RADIALGASSE, Material.EMERALD_BLOCK);
        // DER BORDSTEIN ist die Kante des Kreuzarms - ungemischt, siehe Pflasterstoffe.
        gewuenscht.put(Stadtplan.Lage.BORDSTEIN, Material.GOLD_BLOCK);
        gewuenscht.put(Stadtplan.Lage.ECKPLATZ, Material.LAPIS_BLOCK);
        gewuenscht.put(Stadtplan.Lage.ZEILE, Material.COAL_BLOCK);
        gewuenscht.put(Stadtplan.Lage.MAUER, Material.REDSTONE_BLOCK);

        YamlConfiguration datei = new YamlConfiguration();
        gewuenscht.forEach((lage, block) -> datei.set(Pflasterstoffe.PFAD + "."
                + Pflasterstoffe.schluesselVon(lage),
                block.name().toLowerCase(Locale.ROOT)));
        datei.set(Pflasterstoffe.PFAD + ".aussen", "bedrock");

        List<String> klagen = new ArrayList<>();
        Pflasterstoffe stoffe = Pflasterstoffe.ausKonfiguration(datei, klagen);

        for (Stadtplan.Lage lage : Stadtplan.Lage.values()) {
            if (lage == Stadtplan.Lage.AUSSEN) {
                continue;
            }
            // EIN einzelner Wert ist eine Mischung aus einem - und dann liegt er
            // ueberall.
            assertEquals(List.of(gewuenscht.get(lage)), stoffe.mischung(lage),
                    "Lage " + lage);
            assertEquals(gewuenscht.get(lage), stoffe.block(lage, 5, 9), "Lage " + lage);
        }
        // AUSSEN LAESST SICH NICHT AUFSCHLIESSEN, und das wird GESAGT: Das Pflaster
        // geht nur den Umriss der Stadt ab, und dort kommt die Lage nie vor. Ein
        // Schluessel, der gelesen wird und nichts tut, ist schlimmer als keiner.
        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("aussen"), klagen.get(0));
        assertTrue(klagen.get(0).contains("keine Wirkung"), klagen.get(0));
        assertEquals(List.of(), stoffe.mischung(Stadtplan.Lage.AUSSEN),
                "und der Wert kommt nicht an");
    }

    @Test
    @DisplayName("Eine Liste in der Datei wird eine Mischung - samt Gewichtung")
    void eineListeWirdMischung() {
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Pflasterstoffe.PFAD + ".ringgasse",
                List.of("gold_block", "gold_block", "iron_block"));

        List<String> klagen = new ArrayList<>();
        Pflasterstoffe stoffe = Pflasterstoffe.ausKonfiguration(datei, klagen);

        assertEquals(List.of(), klagen, klagen.toString());
        assertEquals(List.of(Material.GOLD_BLOCK, Material.GOLD_BLOCK,
                        Material.IRON_BLOCK),
                stoffe.mischung(Stadtplan.Lage.RINGGASSE),
                "die Wiederholung bleibt - sie IST die Gewichtung");

        int gold = 0;
        int eisen = 0;
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                if (stoffe.block(Stadtplan.Lage.RINGGASSE, x, z) == Material.GOLD_BLOCK) {
                    gold++;
                } else {
                    eisen++;
                }
            }
        }
        assertTrue(gold > eisen, "zweimal eingetragen heisst oefter: " + gold + "/"
                + eisen);
        // Und die anderen Lagen bleiben beim Werk.
        assertEquals(Pflasterstoffe.abWerk().mischung(Stadtplan.Lage.MARKT),
                stoffe.mischung(Stadtplan.Lage.MARKT), "der Markt bleibt");
    }

    @Test
    @DisplayName("Ein unbekannter Block fehlt in der Mischung und wird geklagt")
    void unbekannterBlock() {
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Pflasterstoffe.PFAD + ".ringgasse",
                List.of("cobblestone", "kopfsteinpflaster", "gravel"));
        datei.set(Pflasterstoffe.PFAD + ".markt", List.of("hansestein"));

        List<String> klagen = new ArrayList<>();
        Pflasterstoffe stoffe = Pflasterstoffe.ausKonfiguration(datei, klagen);

        // DREI Klagen: zwei unbekannte Bloecke, und dass vom Markt nichts uebrig blieb.
        // REIHENFOLGEFREI geprueft: Sie haengt an der Reihenfolge der Lagen, und die ist
        // nicht die Sache dieser Probe.
        assertEquals(3, klagen.size(), klagen.toString());
        String alle = String.join(" | ", klagen);
        assertTrue(alle.contains("kopfsteinpflaster"), alle);
        assertTrue(alle.contains("hansestein"), alle);
        // Die Gasse behaelt, was brauchbar war.
        assertEquals(List.of(Material.COBBLESTONE, Material.GRAVEL),
                stoffe.mischung(Stadtplan.Lage.RINGGASSE), "der Rest bleibt");
        // Der Markt faellt auf das Werk zurueck - eine leere Mischung waere eine Lage,
        // die stillschweigend nicht gepflastert wird.
        assertEquals(Pflasterstoffe.abWerk().mischung(Stadtplan.Lage.MARKT),
                stoffe.mischung(Stadtplan.Lage.MARKT), "der Markt faellt zurueck");
        assertTrue(alle.contains("blieb nichts"), alle);
    }

    @Test
    @DisplayName("Luft in der Datei wird geklagt und nicht gesetzt")
    void luftInDerDatei() {
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Pflasterstoffe.PFAD + ".zeile", List.of("coarse_dirt", "air"));

        List<String> klagen = new ArrayList<>();
        Pflasterstoffe stoffe = Pflasterstoffe.ausKonfiguration(datei, klagen);

        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("LOESCHT"), klagen.get(0));
        assertEquals(List.of(Material.COARSE_DIRT),
                stoffe.mischung(Stadtplan.Lage.ZEILE), "Luft fehlt in der Mischung");
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
    @DisplayName("Ohne Abschnitt und mit leerem Wert gilt das Werk")
    void ohneAbschnitt() {
        List<String> klagen = new ArrayList<>();
        assertEquals(Pflasterstoffe.abWerk(),
                Pflasterstoffe.ausKonfiguration(new YamlConfiguration(), klagen),
                "ohne Abschnitt");
        assertEquals(Pflasterstoffe.abWerk(), Pflasterstoffe.ausKonfiguration(null, klagen),
                "ohne Datei");

        // EIN SCHLUESSEL OHNE WERT kommt als null an - das darf nicht wie eine leere
        // Mischung wirken, sonst waere der Markt ploetzlich unberuehrt.
        YamlConfiguration leer = new YamlConfiguration();
        leer.set(Pflasterstoffe.PFAD + ".markt", "");
        leer.set(Pflasterstoffe.PFAD + ".mauer", "   ");
        Pflasterstoffe stoffe = Pflasterstoffe.ausKonfiguration(leer, klagen);
        assertEquals(Pflasterstoffe.abWerk().mischung(Stadtplan.Lage.MARKT),
                stoffe.mischung(Stadtplan.Lage.MARKT), "leerer Wert");
        assertEquals(Pflasterstoffe.abWerk().mischung(Stadtplan.Lage.MAUER),
                stoffe.mischung(Stadtplan.Lage.MAUER), "nur Leerzeichen");
        assertEquals(List.of(), klagen, "ein fehlender Wert ist keine Klage");
    }

    @Test
    @DisplayName("Der Bericht nennt jede Lage und jeden Block ihrer Mischung")
    void derBericht() {
        Pflasterstoffe stoffe = Pflasterstoffe.abWerk();
        String bericht = stoffe.bericht();
        for (Stadtplan.Lage lage : Stadtplan.Lage.values()) {
            if (lage == Stadtplan.Lage.AUSSEN) {
                continue;
            }
            assertTrue(bericht.contains(Pflasterstoffe.schluesselVon(lage)),
                    lage + " fehlt im Bericht: " + bericht);
            for (Material block : stoffe.mischung(lage)) {
                assertTrue(bericht.contains(block.name().toLowerCase(Locale.ROOT)),
                        block + " fehlt im Bericht: " + bericht);
            }
        }
        // AUSSEN steht NICHT drin - dort wird nicht gepflastert.
        assertFalse(bericht.contains("aussen"), bericht);
    }
}
