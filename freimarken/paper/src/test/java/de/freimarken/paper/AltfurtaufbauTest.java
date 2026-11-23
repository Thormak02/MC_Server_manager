package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Platzhalterbau;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Richtung;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Setzen eines Platzhalterbaus - ohne Server, ueber den {@link Weltgriff}.
 *
 * <p>Dieselbe Bauart wie {@code WolkenaufbauTest}: Die Welt ist eine Karte, und jeder
 * gesetzte Block bleibt nachzaehlbar. Die Zahlen stehen als RECHNUNG und nicht als
 * Ergebnis - eine Erwartung, die aus der geprueften Funktion kommt, faengt kein
 * Vertauschen.
 */
@DisplayName("Das Setzen eines Platzhalterbaus in Altfurt")
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
     * <p>Mitgeschrieben wird VOR jeder Pruefung und nicht danach: Eine Attrappe, die erst
     * prueft und dann schreibt, ist bei einem werfenden Aufruf stumm.
     */
    private static final class Feinmitschrift implements Altfurtaufbau.Feingriff {

        private final List<String> rufe = new java.util.ArrayList<>();

        @Override
        public void setzeTuer(int x, int y, int z, Richtung zeigt, Material art) {
            rufe.add("tuer " + schluessel(x, y, z) + " " + zeigt.wort() + " " + art.name());
        }

        @Override
        public void setzeSchild(int x, int y, int z, Richtung zeigt, Material art,
                List<String> zeilen) {
            rufe.add("schild " + schluessel(x, y, z) + " " + zeigt.wort() + " "
                    + art.name() + " " + zeilen);
        }
    }

    /** Ein Haus von neun Bloecken Kante auf dem Boden der Ebene. */
    private static Platzhalterbau haus(Richtung tuer) {
        return new Platzhalterbau("buergeramt",
                new Quader(50, -61, 2, 58, -53, 10), tuer);
    }

    /** Was auf dem Schild steht - eine Zeile, wie der Stationsname sie liefert. */
    private static final List<String> NAME = List.of("Buergeramt");

    private static Altfurtaufbau hand() {
        return new Altfurtaufbau(Baustoffe.abWerk());
    }

    @Test
    @DisplayName("Der GANZE Quader geht in die Welt, Luft eingerechnet")
    void derGanzeQuader() {
        Kartengriff welt = new Kartengriff();

        var fein = new Feinmitschrift();
        int gesetzt = hand().setze(haus(Richtung.NORD), welt, fein, NAME);

        // Die Schale sind 9x9x9; dazu zaehlen zwei Tuerhaelften und ein Schild, die
        // NICHT ueber den Weltgriff gehen, sondern ueber die zweite Naht.
        assertEquals(9 * 9 * 9 + 2 + 1, gesetzt, "Schale plus Tuer plus Schild");
        assertEquals(9 * 9 * 9, welt.gesetzt.size(),
                "ueber den Weltgriff geht nur die Schale - keine Stelle zweimal");
        assertEquals(9 * 9 * 9, welt.rufe, "und kein Ruf ohne Wirkung");
        assertEquals(2, fein.rufe.size(), fein.rufe.toString());
    }

    @Test
    @DisplayName("Boden, Wand, Dach und Luft stehen an den richtigen Stellen")
    void dieStuecke() {
        Kartengriff welt = new Kartengriff();
        hand().setze(haus(Richtung.NORD), welt, new Feinmitschrift(), NAME);

        assertEquals(Baustoffe.BODEN_AB_WERK, welt.bei(54, -61, 6), "Boden in der Mitte");
        assertEquals(Baustoffe.BODEN_AB_WERK, welt.bei(50, -61, 2), "Boden in der Ecke");
        assertEquals(Baustoffe.DACH_AB_WERK, welt.bei(54, -53, 6), "Dach");
        assertEquals(Baustoffe.WAND_AB_WERK, welt.bei(50, -57, 6), "Westwand");
        assertEquals(Baustoffe.WAND_AB_WERK, welt.bei(58, -57, 6), "Ostwand");
        assertEquals(Material.AIR, welt.bei(54, -57, 6), "das Innere ist Luft");
        // Die Tuer: zwei Bloecke Luft in der Nordwand, mittig.
        assertEquals(Material.AIR, welt.bei(54, -60, 2));
        assertEquals(Material.AIR, welt.bei(54, -59, 2));
        assertEquals(Baustoffe.WAND_AB_WERK, welt.bei(54, -58, 2),
                "darueber ist die Wand wieder zu");
        assertEquals(Baustoffe.WAND_AB_WERK, welt.bei(53, -60, 2), "und daneben auch");
    }

    @Test
    @DisplayName("Die Luft wird MITGESETZT - sonst waere Zuruecksetzen etwas anderes")
    void luftWirdMitgesetzt() {
        // DIE TRAGENDE ZUSICHERUNG: Wer nur die Schale setzte, hinterliesse beim zweiten
        // Lauf, was vorher im Haus stand.
        Kartengriff welt = new Kartengriff();
        welt.setze(54, -57, 6, Material.DIAMOND_BLOCK);
        welt.setze(53, -56, 5, Material.DIAMOND_BLOCK);
        int vorher = welt.rufe;

        hand().setze(haus(Richtung.NORD), welt, new Feinmitschrift(), NAME);

        assertEquals(Material.AIR, welt.bei(54, -57, 6), "der Diamant ist weg");
        assertEquals(Material.AIR, welt.bei(53, -56, 5));
        assertEquals(vorher + 9 * 9 * 9, welt.rufe);
    }

    @Test
    @DisplayName("Ausserhalb des Quaders wird kein einziger Block angefasst")
    void nichtsDaneben() {
        Kartengriff welt = new Kartengriff();

        hand().setze(haus(Richtung.NORD), welt, new Feinmitschrift(), NAME);

        Set<String> daneben = new HashSet<>();
        for (int x = 48; x <= 60; x++) {
            for (int y = -63; y <= -51; y++) {
                for (int z = 0; z <= 12; z++) {
                    boolean drin = x >= 50 && x <= 58 && y >= -61 && y <= -53
                            && z >= 2 && z <= 10;
                    if (!drin && welt.gesetzt.containsKey(schluessel(x, y, z))) {
                        daneben.add(schluessel(x, y, z));
                    }
                }
            }
        }
        assertTrue(daneben.isEmpty(), "angefasst, obwohl ausserhalb: " + daneben);
    }

    @Test
    @DisplayName("Steht der Bau schon, sagt die Hand es - und zwar an der WAND")
    void stehtSchon() {
        // Gefragt wird die Wandecke ueber dem Boden und nicht der Boden: Altfurt ist eine
        // Ebene, dort liegt auf Bodenhoehe ueberall schon etwas.
        Kartengriff welt = new Kartengriff();
        Platzhalterbau bau = haus(Richtung.NORD);
        Altfurtaufbau hand = hand();

        assertFalse(hand.stehtSchon(bau, welt), "auf unberuehrter Ebene steht nichts");

        // Nur den Boden hinlegen - wie die Ebene ihn ohnehin hat. Das darf NICHT als
        // "steht schon" gelten, sonst baut der Start nie.
        for (int x = 50; x <= 58; x++) {
            for (int z = 2; z <= 10; z++) {
                welt.setze(x, -61, z, Baustoffe.BODEN_AB_WERK);
            }
        }
        assertFalse(hand.stehtSchon(bau, welt),
                "ein Boden allein ist kein Haus - in einer Ebene liegt er ueberall");

        hand.setze(bau, welt, new Feinmitschrift(), NAME);
        assertTrue(hand.stehtSchon(bau, welt), "nach dem Setzen steht er");
    }

    @Test
    @DisplayName("Ein anderer Baustoff aendert, was gesetzt wird")
    void andereBaustoffe() {
        // Ohne diese Probe waere die Einstellung gebaut und ohne Wirkung.
        Kartengriff welt = new Kartengriff();
        Baustoffe eigen = new Baustoffe(Material.OAK_PLANKS, Material.BRICKS,
                Material.DARK_OAK_PLANKS, Material.SPRUCE_DOOR,
                Material.SPRUCE_WALL_SIGN);

        new Altfurtaufbau(eigen).setze(haus(Richtung.SUED), welt,
                new Feinmitschrift(), NAME);

        assertEquals(Material.OAK_PLANKS, welt.bei(54, -61, 6));
        assertEquals(Material.BRICKS, welt.bei(50, -57, 6));
        assertEquals(Material.DARK_OAK_PLANKS, welt.bei(54, -53, 6));
        // Und die Frage "steht schon" haengt am EIGENEN Wandstoff, nicht am Werkswert.
        assertTrue(new Altfurtaufbau(eigen).stehtSchon(haus(Richtung.SUED), welt));
        assertFalse(hand().stehtSchon(haus(Richtung.SUED), welt),
                "mit anderen Stoffen gefragt ist es ein anderes Haus");
    }

    @Test
    @DisplayName("Die Tuerseite wirkt - vier Richtungen, vier Oeffnungen")
    void tuerseiteWirkt() {
        Map<Richtung, String> stellen = new HashMap<>();
        for (Richtung richtung : Richtung.values()) {
            Kartengriff welt = new Kartengriff();
            hand().setze(haus(richtung), welt, new Feinmitschrift(), NAME);
            String gefunden = null;
            for (int x = 50; x <= 58; x++) {
                for (int z = 2; z <= 10; z++) {
                    boolean rand = x == 50 || x == 58 || z == 2 || z == 10;
                    if (rand && welt.bei(x, -60, z) == Material.AIR) {
                        gefunden = schluessel(x, -60, z);
                    }
                }
            }
            stellen.put(richtung, gefunden);
        }
        assertEquals("54/-60/2", stellen.get(Richtung.NORD));
        assertEquals("54/-60/10", stellen.get(Richtung.SUED));
        assertEquals("50/-60/6", stellen.get(Richtung.WEST));
        assertEquals("58/-60/6", stellen.get(Richtung.OST));
        assertEquals(4, new HashSet<>(stellen.values()).size(),
                "vier Seiten, vier verschiedene Stellen");
    }

    @Test
    @DisplayName("Tuer und Schild gehen ueber die zweite Naht - mit Stelle und Richtung")
    void tuerUndSchild() {
        // DIE NAHT MUSS GERUFEN WERDEN, und zwar mit den Stellen, die der Bauplan nennt.
        // Ohne diese Probe waere die zweite Naht gebaut und nie benutzt - der Bau haette
        // ein Loch in der Wand und kein Schild, und alles waere gruen.
        var welt = new Kartengriff();
        var fein = new Feinmitschrift();

        hand().setze(haus(Richtung.NORD), welt, fein, NAME);

        assertEquals(List.of(
                        "tuer 54/-60/2 nord OAK_DOOR",
                        "schild 54/-58/3 sued OAK_WALL_SIGN [Buergeramt]"),
                fein.rufe,
                "die Tuer in der Nordwand, das Schild INNEN darueber und ins Zimmer"
                        + " zeigend");
    }

    @Test
    @DisplayName("Ohne Zeilen gibt es kein Schild - aber die Tuer bleibt")
    void ohneZeilenKeinSchild() {
        var fein = new Feinmitschrift();

        hand().setze(haus(Richtung.NORD), new Kartengriff(), fein, List.of());

        assertEquals(1, fein.rufe.size(), fein.rufe.toString());
        assertTrue(fein.rufe.get(0).startsWith("tuer "), fein.rufe.get(0));
    }

    @Test
    @DisplayName("Ein Bau von drei Bloecken Hoehe bekommt KEINE Tuer und kein Schild")
    void niedrigerBauOhneTuer() {
        // Eine Tuer braucht ZWEI Ebenen, und die zweite waere dort das Dach. Die
        // Oeffnung bleibt ein Loch in der Wand - das ist richtig und kein Mangel.
        var fein = new Feinmitschrift();
        var huette = new de.freimarken.kern.Platzhalterbau("huette",
                new de.freimarken.kern.Quader(0, -61, 0, 4, -59, 4), Richtung.SUED);

        int gesetzt = hand().setze(huette, new Kartengriff(), fein, NAME);

        assertEquals(5 * 3 * 5, gesetzt, "nur die Schale, kein Zuschlag");
        assertEquals(List.of(), fein.rufe, "weder Tuer noch Schild");
    }

    @Test
    @DisplayName("Ohne Baustoffe, ohne Bau oder ohne Welt gibt es nichts zu setzen")
    void ohneAllesWirft() {
        assertThrows(IllegalArgumentException.class, () -> new Altfurtaufbau(null));
        assertThrows(IllegalArgumentException.class,
                () -> hand().setze(null, new Kartengriff(), new Feinmitschrift(), NAME));
        assertThrows(IllegalArgumentException.class,
                () -> hand().setze(haus(Richtung.NORD), null, new Feinmitschrift(),
                        NAME));
        assertThrows(IllegalArgumentException.class,
                () -> hand().setze(haus(Richtung.NORD), new Kartengriff(), null, NAME));
        assertThrows(IllegalArgumentException.class,
                () -> hand().stehtSchon(null, new Kartengriff()));
    }
}
