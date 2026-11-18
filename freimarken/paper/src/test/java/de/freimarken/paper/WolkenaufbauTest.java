package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Wolkenplan;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Aufsetzen einer Startinsel - die Hand, die in Wolkenwerder das Gelaende macht.
 *
 * <p>Ohne Server pruefbar, und das ist der ganze Zweck der beiden Naehte: Die Bloecke
 * gehen durch einen {@link Weltgriff}, die Truhe durch einen
 * {@link Wolkenaufbau.Truhengriff}. Hier stehen beide als Karte in der Probe, und damit
 * ist jeder gesetzte Block nachzaehlbar - in jeder anderen Welt des Projekts geht das
 * nicht, weil dort ein Weltgenerator setzt.
 *
 * <p><b>Eine eigene Probewelt und nicht {@code Probewelt}</b>: Jene holt ihre Bloecke aus
 * {@code Stadtboden} und ist damit die Marktstadt.
 */
@DisplayName("Das Aufsetzen einer Wolkeninsel")
class WolkenaufbauTest {

    private final Wolkenplan plan = Wolkenplan.wolkenwerder();
    private Wolkenaufbau aufbau;
    private Kartengriff welt;
    private Truhenmitschrift truhe;

    @BeforeEach
    void vorher() {
        aufbau = new Wolkenaufbau(plan);
        welt = new Kartengriff();
        truhe = new Truhenmitschrift();
    }

    @Test
    @DisplayName("Eine Insel sind 810 Bloecke - der GANZE Quader, Luft eingerechnet")
    void derGanzeQuader() {
        // Die tragende Entscheidung dieser Klasse: Geschrieben wird auch die Luft. Nur
        // so ist Aufsetzen und Zuruecksetzen dieselbe Hand - wer die Luft ausliesse,
        // hinterliesse beim Zuruecksetzen die Reste des alten Baumes.
        int gesetzt = aufbau.setze(3, -1, Wolkenplan.Insel.START, Map.of(), welt, truhe);

        assertEquals(9 * 9 * 10, gesetzt, "9x9 Grundflaeche, 10 Hoehen (62 bis 71)");
        assertEquals(gesetzt, welt.gesetzt.size(),
                "und jeder davon ging wirklich in die Welt");
    }

    @Test
    @DisplayName("Die Insel steht in der MITTE ihres Feldes")
    void inDerMitte() {
        aufbau.setze(3, -1, Wolkenplan.Insel.START, Map.of(), welt, truhe);
        int mitteX = plan.feldMitte(3);
        int mitteZ = plan.feldMitte(-1);

        assertEquals(Material.GRASS_BLOCK, welt.bei(mitteX, Wolkenboden.BODEN, mitteZ),
                "der Mittelblock der Wiese");
        assertEquals(mitteX, aufbau.mitteX(3), "und der Befehl rechnet dieselbe Mitte");
        assertEquals(mitteZ, aufbau.mitteZ(-1));
        assertEquals(Wolkenboden.BODEN + 1, Wolkenaufbau.stehhoehe(),
                "stehen tut man einen Block darueber");
    }

    @Test
    @DisplayName("Die Spalte stimmt: Wiese, zwei Erde, darunter nichts")
    void dieSpalte() {
        aufbau.setze(0, 0, Wolkenplan.Insel.START, Map.of(), welt, truhe);
        int m = plan.feldMitte(0);

        assertEquals(Material.GRASS_BLOCK, welt.bei(m, 64, m));
        assertEquals(Material.DIRT, welt.bei(m, 63, m));
        assertEquals(Material.DIRT, welt.bei(m, 62, m));
        assertFalse(welt.gesetzt.containsKey(schluessel(m, 61, m)),
                "tiefer greift das Aufsetzen nicht - dort ist die Leere, und sie gehoert"
                        + " nicht zur Insel");
    }

    @Test
    @DisplayName("Der Baum steht, und seine Krone haengt um den Stamm")
    void derBaum() {
        aufbau.setze(0, 0, Wolkenplan.Insel.START, Map.of(), welt, truhe);
        int baumX = plan.feldMitte(0) + Wolkenplan.BAUM_X;
        int baumZ = plan.feldMitte(0) + Wolkenplan.BAUM_Z;

        for (int dy = 1; dy <= Wolkenplan.STAMM_HOCH; dy++) {
            assertEquals(Material.OAK_LOG, welt.bei(baumX, Wolkenboden.BODEN + dy, baumZ),
                    "Stamm auf Hoehe " + dy);
        }
        assertEquals(Material.OAK_LEAVES, welt.bei(baumX, Wolkenboden.BODEN + 7, baumZ),
                "die Spitze");
        assertEquals(Material.OAK_LEAVES,
                welt.bei(baumX + 2, Wolkenboden.BODEN + 4, baumZ), "die Kante der Krone");
        assertEquals(Material.AIR, welt.bei(baumX + 2, Wolkenboden.BODEN + 4, baumZ + 2),
                "und die Ecken sind offen");
    }

    @Test
    @DisplayName("Die Truhe steht auf der Wiese und wird GEFUELLT")
    void dieTruhe() {
        Map<String, Integer> inhalt = new LinkedHashMap<>();
        inhalt.put("lava_bucket", 1);
        inhalt.put("ice", 2);

        aufbau.setze(2, 2, Wolkenplan.Insel.START, inhalt, welt, truhe);
        int x = plan.feldMitte(2) + Wolkenplan.TRUHE_X;
        int y = Wolkenboden.BODEN + 1;
        int z = plan.feldMitte(2) + Wolkenplan.TRUHE_Z;

        assertEquals(Material.CHEST, welt.bei(x, y, z));
        assertEquals(1, truhe.aufrufe, "genau einmal gefuellt");
        assertEquals(schluessel(x, y, z), truhe.wo, "und zwar an der Stelle der Truhe");
        assertEquals(inhalt, truhe.inhalt);
    }

    @Test
    @DisplayName("Ohne Truhe in der Gestalt wird auch nichts gefuellt")
    void ankunftsfeldOhneTruhe() {
        aufbau.setze(0, 0, Wolkenplan.Insel.ANKUNFT, Map.of("ice", 1), welt, truhe);

        assertEquals(0, truhe.aufrufe,
                "das Ankunftsfeld hat keine Truhe - ein Fuellen waere ein Griff in die"
                        + " Luft");
    }

    @Test
    @DisplayName("Das Ankunftsfeld ist Stein und traegt ein Gelaender")
    void dasAnkunftsfeld() {
        aufbau.setze(0, 0, Wolkenplan.Insel.ANKUNFT, Map.of(), welt, truhe);
        int m = plan.feldMitte(0);
        int rand = m + plan.plattformHalb();

        assertEquals(Material.STONE_BRICKS, welt.bei(m, Wolkenboden.BODEN, m));
        assertEquals(Material.STONE, welt.bei(m, Wolkenboden.BODEN - 1, m));
        assertEquals(Material.STONE_BRICK_WALL,
                welt.bei(rand, Wolkenboden.BODEN + 1, m), "das Gelaender am Rand");
        assertEquals(Material.AIR, welt.bei(m, Wolkenboden.BODEN + 1, m),
                "in der Mitte steht man, dort ist keines");
        assertEquals(Material.AIR, welt.bei(m + Wolkenplan.BAUM_X,
                Wolkenboden.BODEN + 3, m + Wolkenplan.BAUM_Z), "und kein Baum");
    }

    @Test
    @DisplayName("Zweimal aufsetzen ergibt genau dasselbe - das ist das Zuruecksetzen")
    void zweimalIstDasselbe() {
        // Die Zusage, auf der Teil F steht: Aufsetzen und Zuruecksetzen sind dieselbe
        // Hand. Dazwischen wird etwas HINEINGEBAUT - und danach muss es weg sein.
        aufbau.setze(1, 1, Wolkenplan.Insel.START, Map.of(), welt, truhe);
        Map<String, Material> erstesMal = new HashMap<>(welt.gesetzt);
        int m = plan.feldMitte(1);
        welt.setze(m + 1, Wolkenboden.BODEN + 3, m + 1, Material.COBBLESTONE);
        assertEquals(Material.COBBLESTONE, welt.bei(m + 1, Wolkenboden.BODEN + 3, m + 1));

        aufbau.setze(1, 1, Wolkenplan.Insel.START, Map.of(), welt, truhe);

        assertEquals(erstesMal, welt.gesetzt,
                "der Kobbelstein ist weg, und sonst hat sich nichts geaendert");
    }

    @Test
    @DisplayName("Zwei Felder stoeren sich nicht - 512 Bloecke liegen dazwischen")
    void zweiFelder() {
        aufbau.setze(0, 0, Wolkenplan.Insel.START, Map.of(), welt, truhe);
        int ersterRand = plan.feldMitte(0) + plan.plattformHalb();

        aufbau.setze(1, 0, Wolkenplan.Insel.START, Map.of(), welt, truhe);
        int zweiterRand = plan.feldMitte(1) - plan.plattformHalb();

        assertTrue(zweiterRand - ersterRand > 400,
                "zwischen den Plattformen liegen " + (zweiterRand - ersterRand)
                        + " Bloecke - bei zwoelf Chunks Sichtweite sieht man den"
                        + " Nachbarn nicht");
        assertEquals(2 * 9 * 9 * 10, welt.gesetzt.size(), "und beide stehen vollstaendig");
    }

    private static String schluessel(int x, int y, int z) {
        return x + "/" + y + "/" + z;
    }

    /** Eine Welt als Karte - jeder gesetzte Block bleibt nachzaehlbar. */
    private static final class Kartengriff implements Weltgriff {

        private final Map<String, Material> gesetzt = new HashMap<>();

        @Override
        public int unten() {
            return -64;
        }

        @Override
        public int oben(int x, int z) {
            return Wolkenboden.BODEN;
        }

        @Override
        public Material bei(int x, int y, int z) {
            return gesetzt.getOrDefault(schluessel(x, y, z), Material.AIR);
        }

        @Override
        public void setze(int x, int y, int z, Material block) {
            gesetzt.put(schluessel(x, y, z), block);
        }
    }

    /** Eine Hand, die sich merkt, WAS sie wohin gelegt hat. */
    private static final class Truhenmitschrift implements Wolkenaufbau.Truhengriff {

        private int aufrufe;
        private String wo;
        private Map<String, Integer> inhalt;

        @Override
        public void fuelle(int x, int y, int z, Map<String, Integer> gut) {
            aufrufe++;
            wo = schluessel(x, y, z);
            inhalt = Map.copyOf(gut);
        }
    }
}
