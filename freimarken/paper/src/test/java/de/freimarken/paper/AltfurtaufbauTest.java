package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Bauplan;
import de.freimarken.kern.Baustil;
import de.freimarken.kern.Dachart;
import de.freimarken.kern.Platzhalterbau;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Richtung;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Setzen eines Hansehauses - ohne Server, ueber den {@link Weltgriff}.
 *
 * <p>Dieselbe Bauart wie {@code WolkenaufbauTest}: Die Welt ist eine Karte, und jeder
 * gesetzte Block bleibt nachzaehlbar. Die Zahlen stehen als RECHNUNG und nicht als
 * Ergebnis.
 *
 * <p>Das Haus der Proben ist das Buergeramt der ausgelieferten Datei: elf tief, sieben
 * breit, vierzehn hoch, Tuer nach OST, Backstein mit Ziegeldach.
 */
@DisplayName("Das Setzen eines Hansehauses in Altfurt")
class AltfurtaufbauTest {

    private static String schluessel(int x, int y, int z) {
        return x + "/" + y + "/" + z;
    }

    /** Eine Welt als Karte - jeder gesetzte Block bleibt nachzaehlbar. */
    private static final class Kartengriff implements Weltgriff {

        private final Map<String, Material> gesetzt = new HashMap<>();
        private int rufe;

        @Override
        public int unten() {
            return -64;
        }

        @Override
        public int oben(int x, int z) {
            return -61;
        }

        @Override
        public Material bei(int x, int y, int z) {
            return gesetzt.getOrDefault(schluessel(x, y, z), Material.AIR);
        }

        @Override
        public void setze(int x, int y, int z, Material block) {
            rufe++;
            gesetzt.put(schluessel(x, y, z), block);
        }
    }

    /**
     * Die zweite Naht als Mitschrift - wer sie nie ruft, faellt damit auf.
     *
     * <p>Mitgeschrieben wird VOR jeder Pruefung: Eine Attrappe, die erst prueft und dann
     * schreibt, ist bei einem werfenden Aufruf stumm.
     */
    private static final class Feinmitschrift implements Altfurtaufbau.Feingriff {

        private final List<String> rufe = new ArrayList<>();

        @Override
        public void setzeTuer(int x, int y, int z, Richtung zeigt, Material art) {
            rufe.add("tuer " + schluessel(x, y, z) + " " + zeigt.wort() + " "
                    + art.name());
        }

        @Override
        public void setzeSchild(int x, int y, int z, Richtung zeigt, Material art,
                List<String> zeilen) {
            rufe.add("schild " + schluessel(x, y, z) + " " + zeigt.wort() + " "
                    + art.name() + " " + zeilen);
        }
    }

    /** Was auf dem Schild steht - eine Zeile, wie der Stationsname sie liefert. */
    private static final List<String> NAME = List.of("Buergeramt");

    /** Das Buergeramt der ausgelieferten Datei. */
    private static Platzhalterbau haus() {
        return haus(Richtung.OST, Baustil.BACKSTEIN, Dachart.ZIEGEL);
    }

    private static Platzhalterbau haus(Richtung tuer, Baustil stil, Dachart dach) {
        return new Platzhalterbau("buergeramt", new Quader(48, -61, 3, 58, -48, 9),
                tuer, stil, dach);
    }

    private static Altfurtaufbau hand() {
        return new Altfurtaufbau(Baustoffe.abWerk());
    }

    /** Der Block, den ein Stueck in diesem Stil bekommt - aus derselben Tabelle. */
    private static Material block(Platzhalterbau bau, Bauplan.Stueck stueck) {
        return Baustoffe.abWerk().block(bau.stil(), bau.dach(), stueck);
    }

    @Test
    @DisplayName("Der GANZE Quader geht in die Welt, Luft eingerechnet")
    void derGanzeQuader() {
        Kartengriff welt = new Kartengriff();
        Feinmitschrift fein = new Feinmitschrift();

        int gesetzt = hand().setze(haus(), welt, fein, NAME);

        // Der Quader ist 11x14x7; dazu zaehlen zwei Tuerhaelften und ein Schild, die
        // NICHT ueber den Weltgriff gehen, sondern ueber die zweite Naht.
        assertEquals(11 * 14 * 7 + 2 + 1, gesetzt, "Quader plus Tuer plus Schild");
        assertEquals(11 * 14 * 7, welt.gesetzt.size(),
                "ueber den Weltgriff geht der Quader - keine Stelle zweimal");
        assertEquals(11 * 14 * 7, welt.rufe, "und kein Ruf ohne Wirkung");
        assertEquals(2, fein.rufe.size(), fein.rufe.toString());
    }

    @Test
    @DisplayName("Die Stuecke des Hansehauses stehen an den richtigen Stellen")
    void dieStuecke() {
        Platzhalterbau bau = haus();
        Kartengriff welt = new Kartengriff();
        hand().setze(bau, welt, new Feinmitschrift(), NAME);

        assertEquals(block(bau, Bauplan.Stueck.BODEN), welt.bei(53, -61, 6),
                "Fussboden");
        assertEquals(block(bau, Bauplan.Stueck.SOCKEL), welt.bei(48, -60, 3),
                "Sockelecke");
        assertEquals(block(bau, Bauplan.Stueck.BALKEN), welt.bei(48, -57, 3),
                "Ecksaeule");
        assertEquals(block(bau, Bauplan.Stueck.GEFACH), welt.bei(52, -59, 3),
                "Gefach unter dem Fenster");
        assertEquals(block(bau, Bauplan.Stueck.FENSTER), welt.bei(52, -58, 3),
                "Fenster");
        assertEquals(Material.LANTERN, welt.bei(58, -58, 4), "Laterne neben der Tuer");
        assertEquals(Material.AIR, welt.bei(58, -59, 6),
                "die Tuerstelle bleibt Luft - die echte Tuer setzt die zweite Naht");
        assertEquals(block(bau, Bauplan.Stueck.GIEBELSTUFE), welt.bei(58, -50, 4),
                "Treppengiebel an der FRONT");
        assertEquals(Material.DARK_OAK_STAIRS, welt.bei(53, -50, 4),
                "Dachflaeche an der Laengsseite - aus der Dachart");
        assertEquals(block(bau, Bauplan.Stueck.FIRST), welt.bei(53, -48, 6), "First");
        assertEquals(Material.AIR, welt.bei(53, -57, 6), "das Innere ist Luft");
        assertEquals(Material.AIR, welt.bei(53, -50, 6), "und der Dachboden auch");
    }

    @Test
    @DisplayName("Die Luft wird MITGESETZT - sonst waere Zuruecksetzen etwas anderes")
    void luftWirdMitgesetzt() {
        Kartengriff welt = new Kartengriff();
        welt.setze(53, -57, 6, Material.DIAMOND_BLOCK);
        welt.setze(53, -50, 6, Material.DIAMOND_BLOCK);
        int vorher = welt.rufe;

        hand().setze(haus(), welt, new Feinmitschrift(), NAME);

        assertEquals(Material.AIR, welt.bei(53, -57, 6), "der Diamant ist weg");
        assertEquals(Material.AIR, welt.bei(53, -50, 6), "auch der im Dachboden");
        assertEquals(vorher + 11 * 14 * 7, welt.rufe);
    }

    @Test
    @DisplayName("Ausserhalb des Quaders wird kein einziger Block angefasst")
    void nichtsDaneben() {
        Kartengriff welt = new Kartengriff();

        hand().setze(haus(), welt, new Feinmitschrift(), NAME);

        Set<String> daneben = new HashSet<>();
        for (int x = 46; x <= 60; x++) {
            for (int y = -63; y <= -46; y++) {
                for (int z = 1; z <= 11; z++) {
                    boolean drin = x >= 48 && x <= 58 && y >= -61 && y <= -48
                            && z >= 3 && z <= 9;
                    if (!drin && welt.gesetzt.containsKey(schluessel(x, y, z))) {
                        daneben.add(schluessel(x, y, z));
                    }
                }
            }
        }
        assertTrue(daneben.isEmpty(), "angefasst, obwohl ausserhalb: " + daneben);
    }

    @Test
    @DisplayName("Steht der Bau schon, sagt die Hand es - und zwar am SOCKEL")
    void stehtSchon() {
        // Gefragt wird die Sockelecke ueber dem Boden und nicht der Boden: Altfurt ist
        // eine Ebene, dort liegt auf Bodenhoehe ueberall schon etwas.
        Platzhalterbau bau = haus();
        Kartengriff welt = new Kartengriff();
        Altfurtaufbau hand = hand();

        assertFalse(hand.stehtSchon(bau, welt), "auf unberuehrter Ebene steht nichts");

        for (int x = 48; x <= 58; x++) {
            for (int z = 3; z <= 9; z++) {
                welt.setze(x, -61, z, block(bau, Bauplan.Stueck.BODEN));
            }
        }
        assertFalse(hand.stehtSchon(bau, welt),
                "ein Fussboden allein ist kein Haus - in einer Ebene liegt er ueberall");

        hand.setze(bau, welt, new Feinmitschrift(), NAME);
        assertTrue(hand.stehtSchon(bau, welt), "nach dem Setzen steht er");
    }

    @Test
    @DisplayName("Der Stil waehlt die Bloecke - fuenf Paletten, fuenf Waende")
    void derStilWirkt() {
        // Ohne diese Probe waere die Einstellung gebaut und ohne Wirkung.
        Map<Baustil, Material> gefach = new HashMap<>();
        for (Baustil stil : Baustil.values()) {
            Kartengriff welt = new Kartengriff();
            Platzhalterbau bau = haus(Richtung.OST, stil, Dachart.ZIEGEL);
            hand().setze(bau, welt, new Feinmitschrift(), NAME);
            gefach.put(stil, welt.bei(52, -59, 3));
        }
        assertEquals(Material.BRICKS, gefach.get(Baustil.BACKSTEIN), "Bundesrot");
        assertEquals(Material.CALCITE, gefach.get(Baustil.FACHWERK));
        assertEquals(Material.SANDSTONE, gefach.get(Baustil.GOLD), "Markgold");
        assertEquals(Material.MUD_BRICKS, gefach.get(Baustil.FORST), "Forstgruen");
        assertEquals(Material.POLISHED_DEEPSLATE, gefach.get(Baustil.SCHIEFER),
                "Schiefer");
        assertEquals(Baustil.values().length,
                gefach.values().stream().distinct().count(),
                "jeder Stil ein eigenes Gefach: " + gefach);
    }

    @Test
    @DisplayName("Die Dachart waehlt das Dach - und nur das Dach")
    void dieDachartWirkt() {
        Kartengriff ziegel = new Kartengriff();
        Kartengriff kupfer = new Kartengriff();
        hand().setze(haus(Richtung.OST, Baustil.BACKSTEIN, Dachart.ZIEGEL), ziegel,
                new Feinmitschrift(), NAME);
        hand().setze(haus(Richtung.OST, Baustil.BACKSTEIN, Dachart.KUPFER), kupfer,
                new Feinmitschrift(), NAME);

        assertEquals(Material.DARK_OAK_STAIRS, ziegel.bei(53, -50, 4));
        assertEquals(Material.OXIDIZED_COPPER, kupfer.bei(53, -50, 4));
        // UND NUR DAS DACH: Die Wand bleibt dieselbe. Ohne das waere eine Dachart, die
        // die halbe Palette austauscht, genauso gruen.
        assertEquals(ziegel.bei(52, -59, 3), kupfer.bei(52, -59, 3), "das Gefach bleibt");
        assertEquals(ziegel.bei(58, -50, 4), kupfer.bei(58, -50, 4),
                "und der Treppengiebel auch");
        assertNotEquals(ziegel.bei(53, -50, 4), kupfer.bei(53, -50, 4));
    }

    @Test
    @DisplayName("Tuer und Schild gehen ueber die zweite Naht - mit Stelle und Richtung")
    void tuerUndSchild() {
        // DIE NAHT MUSS GERUFEN WERDEN, und zwar mit den Stellen, die der Bauplan nennt.
        // Ohne diese Probe waere die zweite Naht gebaut und nie benutzt.
        Feinmitschrift fein = new Feinmitschrift();

        hand().setze(haus(), new Kartengriff(), fein, NAME);

        assertEquals(List.of(
                        "tuer 58/-59/6 ost OAK_DOOR",
                        "schild 57/-57/6 west OAK_WALL_SIGN [Buergeramt]"),
                fein.rufe,
                "die Tuer in der Front, das Schild INNEN darueber und ins Zimmer zeigend");
    }

    @Test
    @DisplayName("Ohne Zeilen gibt es kein Schild - aber die Tuer bleibt")
    void ohneZeilenKeinSchild() {
        Feinmitschrift fein = new Feinmitschrift();

        hand().setze(haus(), new Kartengriff(), fein, List.of());

        assertEquals(1, fein.rufe.size(), fein.rufe.toString());
        assertTrue(fein.rufe.get(0).startsWith("tuer "), fein.rufe.get(0));
    }

    @Test
    @DisplayName("Die Tuerseite wirkt - vier Richtungen, vier Oeffnungen")
    void tuerseiteWirkt() {
        Map<Richtung, String> stellen = new HashMap<>();
        for (Richtung richtung : Richtung.values()) {
            Feinmitschrift fein = new Feinmitschrift();
            hand().setze(haus(richtung, Baustil.BACKSTEIN, Dachart.ZIEGEL),
                    new Kartengriff(), fein, NAME);
            stellen.put(richtung, fein.rufe.get(0));
        }
        assertEquals(4, new HashSet<>(stellen.values()).size(),
                "vier Seiten, vier Tueren: " + stellen);
        assertTrue(stellen.get(Richtung.OST).startsWith("tuer 58/-59/6 ost"),
                stellen.get(Richtung.OST));
        assertTrue(stellen.get(Richtung.NORD).startsWith("tuer 53/-59/3 nord"),
                stellen.get(Richtung.NORD));
    }

    @Test
    @DisplayName("Ohne Baustoffe, ohne Bau oder ohne Welt gibt es nichts zu setzen")
    void ohneAllesWirft() {
        assertThrows(IllegalArgumentException.class, () -> new Altfurtaufbau(null));
        assertThrows(IllegalArgumentException.class,
                () -> hand().setze(null, new Kartengriff(), new Feinmitschrift(), NAME));
        assertThrows(IllegalArgumentException.class,
                () -> hand().setze(haus(), null, new Feinmitschrift(), NAME));
        assertThrows(IllegalArgumentException.class,
                () -> hand().setze(haus(), new Kartengriff(), null, NAME));
        assertThrows(IllegalArgumentException.class,
                () -> hand().stehtSchon(null, new Kartengriff()));
    }
}
