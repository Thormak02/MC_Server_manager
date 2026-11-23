package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Bauplan;
import de.freimarken.kern.Bautenverzeichnis;
import de.freimarken.kern.Baustil;
import de.freimarken.kern.Dachart;
import de.freimarken.kern.Platzhalterbau;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Richtung;
import de.freimarken.kern.Stadtplan;
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

        @Override
        public void setzeDachtreppe(int x, int y, int z, Richtung bergauf, Material art) {
            rufe.add("dach " + schluessel(x, y, z) + " " + bergauf.wort() + " "
                    + art.name());
        }

        @Override
        public void setzeFensterscheibe(int x, int y, int z, Richtung achse,
                Material art) {
            rufe.add("scheibe " + schluessel(x, y, z) + " " + achse.wort() + " "
                    + art.name());
        }

        @Override
        public void setzeBalkenachse(int x, int y, int z, Richtung achse, Material art) {
            rufe.add("balken " + schluessel(x, y, z) + " " + achse.wort() + " "
                    + art.name());
        }
    }

    /**
     * Die Rufe der zweiten Naht OHNE die Dachtreppen.
     *
     * <p>Seit das Dach seine Richtung bekommt, laufen auch die Treppen durch diese Naht -
     * ueber fuenfzig Rufe je Haus. Proben, die Tuer und Schild pruefen, wuerden darin
     * untergehen; die Dachtreppen haelt ihre eigene Probe.
     */
    private static List<String> ohneDach(Feinmitschrift fein) {
        return fein.rufe.stream()
                .filter(zeile -> !zeile.startsWith("dach ")
                        && !zeile.startsWith("scheibe ")
                        && !zeile.startsWith("balken "))
                .toList();
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

    /** Dasselbe Haus aus einer VORLAGE - nur die steht in der Normlage und dreht sich. */
    private static Platzhalterbau vorlagenhaus() {
        return new Platzhalterbau("buergeramt", new Quader(48, -61, 3, 58, -48, 9),
                Richtung.OST, Baustil.BACKSTEIN, Dachart.ZIEGEL, "hansehaus-mittel");
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
        assertEquals(2, ohneDach(fein).size(), ohneDach(fein).toString());
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
        assertEquals(block(bau, Bauplan.Stueck.GEFACH), welt.bei(51, -57, 3),
                "Gefach im Fachwerk");
        assertEquals(block(bau, Bauplan.Stueck.FENSTER), welt.bei(50, -56, 3),
                "Fenster zwischen zwei Balken");
        assertEquals(block(bau, Bauplan.Stueck.FENSTER), welt.bei(50, -59, 3),
                "und eines im steinernen Erdgeschoss, auf Augenhoehe");
        // AUF DER OBERSTEN TUEREBENE -59: Die Tuer beginnt auf der Lauf-Ebene, seit
        // am Probeserver eine Schwelle in jeder Oeffnung gemessen wurde.
        assertEquals(Material.LANTERN, welt.bei(58, -59, 4), "Laterne neben der Tuer");
        assertEquals(Material.AIR, welt.bei(58, -59, 6),
                "die Tuerstelle bleibt Luft - die echte Tuer setzt die zweite Naht");
        assertEquals(block(bau, Bauplan.Stueck.GIEBELSTUFE), welt.bei(58, -52, 4),
                "Treppengiebel an der FRONT");
        assertEquals(Material.DARK_OAK_PLANKS, welt.bei(53, -54, 3),
                "der volle Dachfuss - aus der Dachart");
        assertEquals(Material.DARK_OAK_STAIRS, welt.bei(53, -53, 3),
                "die Dachtreppe darueber - auch aus der Dachart");
        assertEquals(block(bau, Bauplan.Stueck.FIRST), welt.bei(53, -48, 6), "First");
        assertEquals(Material.AIR, welt.bei(53, -57, 6), "das Innere ist Luft");
        assertEquals(Material.AIR, welt.bei(53, -52, 6), "und der Dachboden auch");
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
            // Die Fachwerkzone liegt ueber dem steinernen Erdgeschoss: y -57.
            gefach.put(stil, welt.bei(51, -57, 3));
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

        // Die TREPPE der Dachhaut liegt auf -53 (die obere Ebene der ersten Stufe),
        // der volle DACHFUSS darunter auf -54.
        assertEquals(Material.DARK_OAK_STAIRS, ziegel.bei(53, -53, 3));
        assertEquals(Material.OXIDIZED_CUT_COPPER_STAIRS, kupfer.bei(53, -53, 3));
        assertEquals(Material.DARK_OAK_PLANKS, ziegel.bei(53, -54, 3), "der Dachfuss");
        assertEquals(Material.OXIDIZED_COPPER, kupfer.bei(53, -54, 3));
        // UND NUR DAS DACH: Die Wand bleibt dieselbe. Ohne das waere eine Dachart, die
        // die halbe Palette austauscht, genauso gruen.
        assertEquals(ziegel.bei(51, -57, 3), kupfer.bei(51, -57, 3), "das Gefach bleibt");
        assertEquals(ziegel.bei(58, -52, 4), kupfer.bei(58, -52, 4),
                "und der Treppengiebel auch");
        assertNotEquals(ziegel.bei(53, -53, 3), kupfer.bei(53, -53, 3));
    }

    @Test
    @DisplayName("Die Dachart wirkt bei JEDEM Bau - auch beim schmalsten")
    void dieDachartWirktAuchImTurm() {
        // AM LAUFENDEN SERVER GEFUNDEN [23.11.2026]: Ein Bau mit einer Front von DREI
        // Bloecken hat genau eine Giebelstufe, und die ist sofort der First - er hat also
        // gar keine Dachflaeche. Solange der First aus der Palette kam, war dach: bei
        // allen vier Tortuermen Altfurts eine Einstellung OHNE WIRKUNG. Keine Probe sah
        // es, weil alle an einem Haus mit sieben Bloecken Front prueften.
        //
        // Darum laeuft diese Probe ueber JEDE Front, die es in der ausgelieferten Datei
        // gibt - drei, sieben und siebzehn.
        int geprueft = 0;
        for (int front : new int[]{3, 7, 17}) {
            Quader quader = new Quader(0, -61, 0, 10, -61 + 15, front - 1);
            Platzhalterbau ziegelbau = new Platzhalterbau("probe" + front, quader,
                    Richtung.OST, Baustil.SCHIEFER, Dachart.ZIEGEL);
            Platzhalterbau kupferbau = new Platzhalterbau("probe" + front, quader,
                    Richtung.OST, Baustil.SCHIEFER, Dachart.KUPFER);
            assertEquals(front, ziegelbau.front(), "die Probe misst, was sie meint");

            Kartengriff ziegel = new Kartengriff();
            Kartengriff kupfer = new Kartengriff();
            hand().setze(ziegelbau, ziegel, new Feinmitschrift(), NAME);
            hand().setze(kupferbau, kupfer, new Feinmitschrift(), NAME);

            Set<String> anders = new HashSet<>();
            for (String stelle : ziegel.gesetzt.keySet()) {
                if (!ziegel.gesetzt.get(stelle).equals(kupfer.gesetzt.get(stelle))) {
                    anders.add(stelle);
                }
            }
            assertFalse(anders.isEmpty(),
                    "bei Front " + front + " aendert die Dachart KEINEN einzigen Block -"
                            + " dann ist dach: dort eine Einstellung ohne Wirkung");
            geprueft++;
        }
        assertEquals(3, geprueft, "drei Fronten geprueft");
    }

    @Test
    @DisplayName("Tuer und Schild gehen ueber die zweite Naht - mit Stelle und Richtung")
    void tuerUndSchild() {
        // DIE NAHT MUSS GERUFEN WERDEN, und zwar mit den Stellen, die der Bauplan nennt.
        // Ohne diese Probe waere die zweite Naht gebaut und nie benutzt.
        Feinmitschrift fein = new Feinmitschrift();

        hand().setze(haus(), new Kartengriff(), fein, NAME);

        assertEquals(List.of(
                        "tuer 58/-60/6 ost OAK_DOOR",
                        "schild 57/-58/6 west OAK_WALL_SIGN [Buergeramt]"),
                ohneDach(fein),
                "die Tuer in der Front, das Schild INNEN darueber und ins Zimmer"
                        + " zeigend");
    }

    @Test
    @DisplayName("Ohne Zeilen gibt es kein Schild - aber die Tuer bleibt")
    void ohneZeilenKeinSchild() {
        Feinmitschrift fein = new Feinmitschrift();

        hand().setze(haus(), new Kartengriff(), fein, List.of());

        assertEquals(1, ohneDach(fein).size(), ohneDach(fein).toString());
        assertTrue(ohneDach(fein).get(0).startsWith("tuer "), ohneDach(fein).get(0));
    }

    @Test
    @DisplayName("Die Tuerseite wirkt - vier Richtungen, vier Oeffnungen")
    void tuerseiteWirkt() {
        Map<Richtung, String> stellen = new HashMap<>();
        for (Richtung richtung : Richtung.values()) {
            Feinmitschrift fein = new Feinmitschrift();
            hand().setze(haus(richtung, Baustil.BACKSTEIN, Dachart.ZIEGEL),
                    new Kartengriff(), fein, NAME);
            stellen.put(richtung, ohneDach(fein).get(0));
        }
        assertEquals(4, new HashSet<>(stellen.values()).size(),
                "vier Seiten, vier Tueren: " + stellen);
        assertTrue(stellen.get(Richtung.OST).startsWith("tuer 58/-60/6 ost"),
                stellen.get(Richtung.OST));
        assertTrue(stellen.get(Richtung.NORD).startsWith("tuer 53/-60/3 nord"),
                stellen.get(Richtung.NORD));
    }

    /** Eine Mitschrift der Strukturnaht - mit einstellbarer Groesse. */
    private static final class Strukturmitschrift implements Strukturgriff {

        private final List<String> rufe = new ArrayList<>();
        private int[] masse;
        private int liefert = 1;

        /** Was {@code sichere} zurueckgibt - eine Klage oder {@code null}. */
        private String klagt;

        @Override
        public int[] groesse(String name) {
            rufe.add("groesse " + name);
            return masse;
        }

        @Override
        public int setze(String name, Quader bereich, int grad) {
            rufe.add("setze " + name + " " + bereich.alsText() + " " + grad + " Grad");
            return liefert;
        }

        @Override
        public String sichere(String name, Quader bereich) {
            // VOR dem Rueckgabewert mitgeschrieben: Sonst waere ein Ruf, der klagt, in
            // der Mitschrift unsichtbar.
            rufe.add("sichere " + name);
            return klagt;
        }
    }

    @Test
    @DisplayName("Gibt es eine Datei, GEWINNT sie - und wird gedreht gesetzt")
    void dateiGewinnt() {
        // DER GANZE SINN DIESER FASSUNG [23.11.2026]: Der gerechnete Bauplan erzeugt neun
        // gleichfoermige Haeuser; ein von Hand gebautes Haus sieht aus wie ein Haus. Der
        // Rechenweg ist seitdem der RUECKFALL.
        Kartengriff welt = new Kartengriff();
        Strukturmitschrift strukturen = new Strukturmitschrift();
        // Eine VORLAGE steht in der Normlage: ungedreht 7 breit, 14 hoch, 11 tief - nach
        // 90 Grad passt das genau in den Quader 11x14x7.
        strukturen.masse = new int[]{7, 14, 11};
        strukturen.liefert = 7 * 14 * 11;

        Altfurtaufbau.Ergebnis ergebnis = hand().baue(vorlagenhaus(), welt,
                new Feinmitschrift(), NAME, strukturen);

        assertEquals(Altfurtaufbau.Weg.STRUKTUR, ergebnis.weg());
        assertEquals(7 * 14 * 11, ergebnis.bloecke());
        assertNull(ergebnis.klage());
        assertEquals(List.of("groesse hansehaus-mittel",
                        "setze hansehaus-mittel 48/-61/3 bis 58/-48/9 90 Grad"),
                strukturen.rufe,
                "der Name der VORLAGE, nicht der des Baus - und gedreht");
        assertEquals(0, welt.rufe,
                "der Rechenweg darf dann KEINEN Block gesetzt haben");
    }

    @Test
    @DisplayName("Die EIGENE Datei gewinnt auch - aber UNGEDREHT")
    void eigeneDateiUngedreht() {
        // AM LAUFENDEN SERVER GESEHEN [23.11.2026]: Eine mit /freimarken altfurt sichern
        // geschriebene Datei ist der Quader selbst, in der Lage, in der er in der Welt
        // steht. Wurde sie trotzdem gedreht, war sie 7x14x11 statt 11x14x7 und passte
        // nicht mehr in ihren EIGENEN Quader - vier von neun Haeusern wurden gerechnet,
        // obwohl ihre Datei dalag.
        Kartengriff welt = new Kartengriff();
        Strukturmitschrift strukturen = new Strukturmitschrift();
        strukturen.masse = new int[]{11, 14, 7};   // genau der Quader
        strukturen.liefert = 11 * 14 * 7;

        Altfurtaufbau.Ergebnis ergebnis = hand().baue(haus(), welt,
                new Feinmitschrift(), NAME, strukturen);

        assertEquals(Altfurtaufbau.Weg.STRUKTUR, ergebnis.weg());
        assertNull(ergebnis.klage());
        assertEquals(List.of("groesse buergeramt",
                        "setze buergeramt 48/-61/3 bis 58/-48/9 0 Grad"),
                strukturen.rufe,
                "der Name des Baus - und NULL Grad, obwohl die Tuer nach OST zeigt");
        assertEquals(0, welt.rufe);
    }

    @Test
    @DisplayName("Ohne Datei rechnet der Bauplan - und sagt es")
    void ohneDateiRechnen() {
        Kartengriff welt = new Kartengriff();
        Strukturmitschrift strukturen = new Strukturmitschrift();
        strukturen.masse = null;

        Altfurtaufbau.Ergebnis ergebnis = hand().baue(haus(), welt,
                new Feinmitschrift(), NAME, strukturen);

        assertEquals(Altfurtaufbau.Weg.GERECHNET, ergebnis.weg());
        assertEquals(11 * 14 * 7 + 2 + 1, ergebnis.bloecke());
        assertNull(ergebnis.klage(), "keine Datei zu haben ist kein Fehler");
        assertEquals(11 * 14 * 7, welt.rufe);
    }

    @Test
    @DisplayName("Eine Datei, die nicht passt, wird NICHT gesetzt - und beklagt")
    void dateiPasstNicht() {
        // Ein Haus, das ueber seinen Quader hinausragt, steht in der Nachbarwand oder im
        // Ankunftsweg - und genau dagegen ist jeder Quader geprueft.
        Kartengriff welt = new Kartengriff();
        Strukturmitschrift strukturen = new Strukturmitschrift();
        strukturen.masse = new int[]{11, 14, 7};   // nach 90 Grad 7x14x11 - zu tief

        Altfurtaufbau.Ergebnis ergebnis = hand().baue(vorlagenhaus(), welt,
                new Feinmitschrift(), NAME, strukturen);

        assertEquals(Altfurtaufbau.Weg.GERECHNET, ergebnis.weg());
        assertNotNull(ergebnis.klage());
        assertTrue(ergebnis.klage().contains("90 Grad"), ergebnis.klage());
        assertTrue(ergebnis.klage().contains("gerechnet statt gesetzt"),
                ergebnis.klage());
        assertEquals(List.of("groesse hansehaus-mittel"), strukturen.rufe,
                "setze darf gar nicht erst gerufen werden");
        assertEquals(11 * 14 * 7, welt.rufe, "stattdessen hat der Rechenweg gebaut");
    }

    @Test
    @DisplayName("Laesst sich die Datei nicht setzen, rechnet der Bauplan doch")
    void dateiLaesstSichNichtSetzen() {
        Kartengriff welt = new Kartengriff();
        Strukturmitschrift strukturen = new Strukturmitschrift();
        strukturen.masse = new int[]{7, 14, 11};
        strukturen.liefert = 0;                    // die Naht meldet: nichts gesetzt

        Altfurtaufbau.Ergebnis ergebnis = hand().baue(haus(), welt,
                new Feinmitschrift(), NAME, strukturen);

        assertEquals(Altfurtaufbau.Weg.GERECHNET, ergebnis.weg());
        assertNotNull(ergebnis.klage());
        assertEquals(11 * 14 * 7, welt.rufe, "ein halbes Altfurt waere schlimmer");
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

    /** Zwei Bauten - das Buergeramt und das Zunfthaus daneben. */
    private static Bautenverzeichnis zweiBauten() {
        return Bautenverzeichnis.ausBauten(List.of(haus(),
                new Platzhalterbau("zunfthaus", new Quader(48, -61, 13, 58, -48, 19),
                        Richtung.OST, Baustil.FACHWERK, Dachart.ZIEGEL)));
    }

    @Test
    @DisplayName("Sichern schreibt jeden Quader, der noch keine Datei hat")
    void sichernSchreibtAlle() {
        Strukturmitschrift strukturen = new Strukturmitschrift();
        strukturen.masse = null;

        Altfurtaufbau.Sicherung raus = hand().sichere(zweiBauten(), strukturen, null,
                false);

        assertEquals(List.of("buergeramt", "zunfthaus"), raus.gesichert());
        assertEquals(List.of(), raus.schonDa());
        assertEquals(List.of(), raus.klagen());
        assertNull(raus.unbekannt());
        assertEquals(List.of("groesse buergeramt", "sichere buergeramt",
                        "groesse zunfthaus", "sichere zunfthaus"),
                strukturen.rufe,
                "erst fragen, ob schon eine Datei da ist, dann schreiben");
    }

    @Test
    @DisplayName("Eine vorhandene Datei wird OHNE Bestaetigung nicht ueberschrieben")
    void vorhandeneDateiBleibt() {
        // DER EINZIGE SCHRITT, DEN NICHTS HEILT: Ein gerechnetes Haus kann jederzeit neu
        // gerechnet werden; eine von Hand gebaute Datei ist nach dem Ueberschreiben weg.
        Strukturmitschrift strukturen = new Strukturmitschrift();
        strukturen.masse = new int[]{7, 14, 11};

        Altfurtaufbau.Sicherung raus = hand().sichere(zweiBauten(), strukturen, null,
                false);

        assertEquals(List.of(), raus.gesichert());
        assertEquals(List.of("buergeramt", "zunfthaus"), raus.schonDa());
        assertFalse(strukturen.rufe.contains("sichere buergeramt"),
                "geschrieben werden darf nichts: " + strukturen.rufe);
        assertEquals(List.of("groesse buergeramt", "groesse zunfthaus"), strukturen.rufe);
    }

    @Test
    @DisplayName("Mit Bestaetigung wird ueberschrieben - und nicht erst gefragt")
    void bestaetigtUeberschreibt() {
        Strukturmitschrift strukturen = new Strukturmitschrift();
        strukturen.masse = new int[]{7, 14, 11};

        Altfurtaufbau.Sicherung raus = hand().sichere(zweiBauten(), strukturen,
                "buergeramt", true);

        assertEquals(List.of("buergeramt"), raus.gesichert(), "nur der genannte Bau");
        assertEquals(List.of(), raus.schonDa());
        assertEquals(List.of("sichere buergeramt"), strukturen.rufe,
                "wer bestaetigt hat, braucht die Frage nach der Datei nicht mehr");
    }

    @Test
    @DisplayName("Ein unbekannter Name tut NICHTS und sagt es")
    void unbekannterNameSagtEs() {
        // Ein Tippfehler, der nichts tut und nichts sagt, sieht aus wie ein stummer
        // Fehlschlag - und der Betreiber sucht den Fehler bei seiner Welt.
        Strukturmitschrift strukturen = new Strukturmitschrift();

        Altfurtaufbau.Sicherung raus = hand().sichere(zweiBauten(), strukturen,
                "zunfhaus", false);

        assertEquals("zunfhaus", raus.unbekannt());
        assertEquals(List.of(), raus.gesichert());
        assertEquals(List.of(), strukturen.rufe, "angefasst wird nichts");
    }

    @Test
    @DisplayName("Ein Name mit Grossbuchstaben und Leerzeichen findet seinen Bau")
    void nameWirdGeputzt() {
        Strukturmitschrift strukturen = new Strukturmitschrift();
        strukturen.masse = null;

        Altfurtaufbau.Sicherung raus = hand().sichere(zweiBauten(), strukturen,
                "  Zunfthaus ", false);

        assertEquals(List.of("zunfthaus"), raus.gesichert());
        assertNull(raus.unbekannt());
    }

    @Test
    @DisplayName("Was sich nicht schreiben laesst, kommt als Klage zurueck")
    void klageKommtDurch() {
        Strukturmitschrift strukturen = new Strukturmitschrift();
        strukturen.masse = null;
        strukturen.klagt = "kein Platz auf der Platte";

        Altfurtaufbau.Sicherung raus = hand().sichere(zweiBauten(), strukturen, null,
                false);

        assertEquals(List.of(), raus.gesichert(), "gesichert ist nur, was auch ankam");
        assertEquals(List.of("kein Platz auf der Platte", "kein Platz auf der Platte"),
                raus.klagen());
    }

    @Test
    @DisplayName("Sichern ohne Verzeichnis oder ohne Naht ist ein Programmfehler")
    void sichernWirft() {
        assertThrows(IllegalArgumentException.class,
                () -> hand().sichere(null, new Strukturmitschrift(), null, false));
        assertThrows(IllegalArgumentException.class,
                () -> hand().sichere(zweiBauten(), null, null, false));
    }

    @Test
    @DisplayName("Jede Dachtreppe bekommt ihre Richtung - sonst zeigen alle nach Norden")
    void dachtreppenBekommenRichtung() {
        // DER GROESSTE MANGEL DER ERSTEN FASSUNG [23.11.2026]: Der Weltgriff setzt einen
        // Block OHNE Blockdaten, und eine Treppe bekommt dann die Vorgabe facing=north.
        // Auf den Bildern zeigten ALLE Dachtreppen nach Norden, und eine der zwei
        // Dachseiten lag damit falsch herum.
        Platzhalterbau bau = haus();
        Feinmitschrift fein = new Feinmitschrift();

        hand().setze(bau, new Kartengriff(), fein, NAME);

        List<String> dach = fein.rufe.stream().filter(z -> z.startsWith("dach ")).toList();
        // GEZAEHLT und nicht "nicht leer": Die Zahl folgt aus der Form - je Dachebene
        // mit Treppe zwei Reihen ueber die ganze Tiefe.
        int treppenebenen = 0;
        for (int y = Bauplan.dachansatz(bau) + 1; y <= bau.bereich().bisY(); y++) {
            if (Bauplan.dachzeigt(bau, 53, y, bau.bereich().vonZ()
                    + Bauplan.dacheinzug(bau, y - Bauplan.dachansatz(bau))) != null) {
                treppenebenen++;
            }
        }
        // Die Reihe laeuft NICHT ueber die ganze Tiefe: An beiden Enden steht die
        // Giebelwand, und die ragt beim Staffelgiebel ueber das Dach. Darum zwei Stellen
        // weniger je Reihe.
        assertEquals(treppenebenen * 2 * (bau.tiefe() - 2), dach.size(),
                "je Treppenebene zwei Reihen zwischen den Giebelwaenden: " + dach);
        assertTrue(treppenebenen >= 3, "ein Dach mit unter drei Treppenebenen ist flach");

        // BEIDE Richtungen kommen vor - das ist der Kern der Sache.
        assertTrue(dach.stream().anyMatch(z -> z.contains(" sued ")), dach.toString());
        assertTrue(dach.stream().anyMatch(z -> z.contains(" nord ")), dach.toString());
        assertEquals(2, dach.stream()
                        .map(z -> z.split(" ")[2]).distinct().count(),
                "genau zwei Richtungen, eine je Dachseite: " + dach);
        // Und der Block ist die TREPPE der Dachart, nicht der volle.
        assertTrue(dach.stream().allMatch(z -> z.endsWith("DARK_OAK_STAIRS")),
                dach.toString());
    }


    @Test
    @DisplayName("Jede Glasscheibe wird verbunden gesetzt - sonst steht sie als Pfosten")
    void scheibenWerdenVerbunden() {
        // DER LETZTE BLOCKFEHLER DER ERSTEN FASSUNG: Der Weltgriff setzt einen Block ohne
        // Blockdaten, und eine Glasscheibe hat dann KEINE Seite verbunden. Sie steht als
        // Pfosten mitten im Wandfeld, statt die Fuge zu fuellen.
        Platzhalterbau bau = haus();
        Kartengriff welt = new Kartengriff();
        Feinmitschrift fein = new Feinmitschrift();

        hand().setze(bau, welt, fein, NAME);

        List<String> scheiben = fein.rufe.stream()
                .filter(zeile -> zeile.startsWith("scheibe ")).toList();
        // GEZAEHLT gegen den Kern: So viele Scheiben, wie der Bauplan Fenster hat.
        int fenster = 0;
        for (int x = 48; x <= 58; x++) {
            for (int y = -61; y <= -48; y++) {
                for (int z = 3; z <= 9; z++) {
                    if (Bauplan.stueckBei(bau, x, y, z) == Bauplan.Stueck.FENSTER) {
                        fenster++;
                    }
                }
            }
        }
        assertEquals(fenster, scheiben.size(),
                "jedes Fenster braucht seine Verbindung: " + scheiben);
        assertTrue(fenster >= 8, "und es gibt welche: " + fenster);

        // BEIDE Achsen kommen vor - die Laengs- und die Giebelwand.
        assertTrue(scheiben.stream().anyMatch(z -> z.contains(" ost ")), scheiben.toString());
        assertTrue(scheiben.stream().anyMatch(z -> z.contains(" sued ")), scheiben.toString());
        // Und der Block ist das Fenster der Palette.
        assertTrue(scheiben.stream().allMatch(z -> z.endsWith("GLASS_PANE")),
                scheiben.toString());
        // Die Stelle ist dieselbe, die der Weltgriff gesetzt hat - erst der Block macht
        // die Scheibe zur Scheibe.
        assertEquals(Material.GLASS_PANE, welt.bei(50, -56, 3));
        assertTrue(scheiben.contains("scheibe 50/-56/3 ost GLASS_PANE"),
                scheiben.toString());
    }


    @Test
    @DisplayName("Jeder liegende Balken wird gelegt - sonst stehen die Fasern senkrecht")
    void balkenWerdenGelegt() {
        Platzhalterbau bau = haus();
        Feinmitschrift fein = new Feinmitschrift();

        hand().setze(bau, new Kartengriff(), fein, NAME);

        List<String> gelegt = fein.rufe.stream()
                .filter(zeile -> zeile.startsWith("balken ")).toList();
        // GEZAEHLT gegen den Kern und nicht gegen "nicht leer".
        int liegende = 0;
        for (int x = 48; x <= 58; x++) {
            for (int y = -61; y <= -48; y++) {
                for (int z = 3; z <= 9; z++) {
                    if (Bauplan.balkenachse(bau, x, y, z) != null) {
                        liegende++;
                    }
                }
            }
        }
        assertEquals(liegende, gelegt.size(), "jeder liegende Balken: " + gelegt.size());
        assertTrue(liegende >= 20, "und es gibt welche: " + liegende);

        // BEIDE Achsen, und der Block ist der Balken der Palette.
        assertTrue(gelegt.stream().anyMatch(z -> z.contains(" ost ")), gelegt.toString());
        assertTrue(gelegt.stream().anyMatch(z -> z.contains(" sued ")), gelegt.toString());
        assertTrue(gelegt.stream()
                        .allMatch(z -> z.endsWith("STRIPPED_DARK_OAK_WOOD")),
                gelegt.toString());
        // Die Ecke ist NICHT dabei - dort laeuft der Eckpfosten durch.
        assertFalse(gelegt.stream().anyMatch(z -> z.startsWith("balken 48/-55/3 ")),
                "die Ecke der Wandkrone darf nicht gelegt werden");
    }


    @Test
    @DisplayName("Das Pflaster legt je Lage seinen Boden - und die Mauer in die Hoehe")
    void dasPflasterLiegt() {
        Stadtplan plan = new Stadtplan(1);
        Pflasterstoffe stoffe = Pflasterstoffe.abWerk();
        Kartengriff welt = new Kartengriff();

        Altfurtaufbau.Pflasterung raus =
                hand().pflastere(plan, stoffe, welt, List.of());

        // GEZAEHLT MIT EIGENER RECHNUNG und nicht mit plan.mauerhoehe: Eine Probe,
        // die ihre Erwartung aus der gepruefen Funktion holt, haelt nur, dass eine
        // Zahl sich selbst gleicht. Die Bodenebene ueberall in der Stadt, dazu auf
        // der Mauer MAUER_HOEHE - 1 Ebenen und auf dem aeusseren Ring eine mehr,
        // wo ein Zinnenzahn steht.
        long erwartet = 0L;
        Quader umriss = plan.umriss();
        for (int x = umriss.vonX(); x <= umriss.bisX(); x++) {
            for (int z = umriss.vonZ(); z <= umriss.bisZ(); z++) {
                Stadtplan.Lage lage = plan.lageBei(x, z);
                if (stoffe.block(lage) == null) {
                    continue;
                }
                if (lage != Stadtplan.Lage.MAUER) {
                    erwartet += 1;
                    continue;
                }
                erwartet += Stadtplan.MAUER_HOEHE - 1;
                if (Stadtplan.ring(x, z) == plan.aussenRing()
                        && Stadtplan.zinne(x, z)) {
                    erwartet += 1;
                }
            }
        }
        assertEquals(erwartet, raus.bloecke(), "jeder Block des Plans");
        assertTrue(erwartet > 14000L, "und es sind viele: " + erwartet);
        assertEquals(0L, raus.uebersprungen(), "ohne Handarbeit wird nichts uebersprungen");

        // JE LAGE DER RICHTIGE BODEN - an einer Stelle, die NEBEN der Achse liegt,
        // sonst gewinnt die Radialgasse.
        assertEquals(stoffe.block(Stadtplan.Lage.MARKT),
                welt.bei(Stadtplan.MITTE_X, Stadtplan.BODEN_Y, Stadtplan.MITTE_Z),
                "die Mitte des Marktes");
        assertEquals(stoffe.block(Stadtplan.Lage.RINGGASSE),
                welt.bei(Stadtplan.MITTE_X + 28, Stadtplan.BODEN_Y,
                        Stadtplan.MITTE_Z + 8), "die erste Ringgasse");
        assertEquals(stoffe.block(Stadtplan.Lage.ZEILE),
                welt.bei(Stadtplan.MITTE_X + 35, Stadtplan.BODEN_Y,
                        Stadtplan.MITTE_Z + 8), "das Bauland der Zeile");
        assertEquals(stoffe.block(Stadtplan.Lage.KERN),
                welt.bei(Stadtplan.MITTE_X + 20, Stadtplan.BODEN_Y,
                        Stadtplan.MITTE_Z + 8), "der Kern wird gepflastert");

        // DIE MAUER STEHT, und zwar genau fuenf Ebenen hoch plus Zinne.
        int mauer = plan.mauerVon();
        assertEquals(stoffe.block(Stadtplan.Lage.MAUER),
                welt.bei(Stadtplan.MITTE_X + mauer, Stadtplan.BODEN_Y,
                        Stadtplan.MITTE_Z + 8), "der Fuss der Mauer");
        assertEquals(stoffe.block(Stadtplan.Lage.MAUER),
                welt.bei(Stadtplan.MITTE_X + mauer,
                        Stadtplan.BODEN_Y + Stadtplan.MAUER_HOEHE - 2,
                        Stadtplan.MITTE_Z + 8), "ihre oberste geschlossene Ebene");
        assertEquals(Material.AIR, welt.bei(Stadtplan.MITTE_X + mauer,
                        Stadtplan.BODEN_Y + Stadtplan.MAUER_HOEHE - 1,
                        Stadtplan.MITTE_Z + 8),
                "der innere Mauerring traegt KEINE Zinne - sie sitzt aussen");

        // JENSEITS DER MAUER WIRD NICHTS ANGEFASST: Luft waere dort ein geloeschter
        // Boden, und wer darauf laeuft, faellt ins Leere.
        assertEquals(Material.AIR,
                welt.bei(Stadtplan.MITTE_X + plan.aussenRing() + 1,
                        Stadtplan.BODEN_Y, Stadtplan.MITTE_Z + 8),
                "ein Block jenseits der Stadt bleibt unberuehrt");
    }

    @Test
    @DisplayName("Das Pflaster laeuft um die Handarbeit herum")
    void pflasterUmHandarbeit() {
        Stadtplan plan = new Stadtplan(1);
        Pflasterstoffe stoffe = Pflasterstoffe.abWerk();
        Kartengriff welt = new Kartengriff();
        // Ein Quader mitten auf dem Markt - etwa eine Ladeneinheit.
        Quader laden = new Quader(Stadtplan.MITTE_X - 2, Stadtplan.BODEN_Y,
                Stadtplan.MITTE_Z - 2, Stadtplan.MITTE_X + 2, Stadtplan.BODEN_Y + 4,
                Stadtplan.MITTE_Z + 2);

        Altfurtaufbau.Pflasterung raus =
                hand().pflastere(plan, stoffe, welt, List.of(laden));

        // FUENF MAL FUENF Bloecke der Bodenebene bleiben unberuehrt - die oberen Ebenen
        // des Quaders zaehlen nicht, denn dort pflastert niemand.
        assertEquals(25L, raus.uebersprungen(), "die Grundflaeche des Ladens");
        assertEquals(Material.AIR,
                welt.bei(Stadtplan.MITTE_X, Stadtplan.BODEN_Y, Stadtplan.MITTE_Z),
                "der Boden des Ladens ist unberuehrt");
        assertEquals(stoffe.block(Stadtplan.Lage.MARKT),
                welt.bei(Stadtplan.MITTE_X + 3, Stadtplan.BODEN_Y, Stadtplan.MITTE_Z),
                "einen Block weiter liegt Pflaster");
    }

    @Test
    @DisplayName("Ob schon gepflastert ist, haengt an EINEM Block in der Mitte")
    void istGepflastert() {
        Stadtplan plan = new Stadtplan(1);
        Pflasterstoffe stoffe = Pflasterstoffe.abWerk();
        Kartengriff leer = new Kartengriff();

        assertFalse(hand().istGepflastert(plan, stoffe, leer),
                "eine leere Welt ist nicht gepflastert");

        hand().pflastere(plan, stoffe, leer, List.of());
        assertTrue(hand().istGepflastert(plan, stoffe, leer), "danach schon");

        // WER NEU PFLASTERN WILL, nimmt diesen einen Block weg - und das wirkt.
        leer.setze(Stadtplan.MITTE_X, Stadtplan.BODEN_Y, Stadtplan.MITTE_Z, Material.AIR);
        assertFalse(hand().istGepflastert(plan, stoffe, leer),
                "ohne ihn wird wieder gepflastert");
    }

    @Test
    @DisplayName("Ohne Plan, Pflaster, Welt oder Liste wirft das Pflastern")
    void pflasternOhneAlles() {
        Stadtplan plan = new Stadtplan(1);
        Pflasterstoffe stoffe = Pflasterstoffe.abWerk();
        Kartengriff welt = new Kartengriff();

        assertThrows(IllegalArgumentException.class,
                () -> hand().pflastere(null, stoffe, welt, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> hand().pflastere(plan, null, welt, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> hand().pflastere(plan, stoffe, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> hand().pflastere(plan, stoffe, welt, null));
        assertThrows(IllegalArgumentException.class,
                () -> hand().istGepflastert(null, stoffe, welt));
    }


    @Test
    @DisplayName("Eine Lage ohne Stoff wird uebersprungen - nicht mit Luft geloescht")
    void lageOhneStoff() {
        Stadtplan plan = new Stadtplan(1);
        Kartengriff welt = new Kartengriff();
        // EINE TAFEL OHNE MARKT - so etwas kann die config.yml nicht erzeugen (ein
        // fehlender Schluessel gibt den Werkstoff), aber der Zweig im Pflasterlauf muss
        // halten: Wer dort Material.AIR setzte, LOESCHTE den Boden des Marktes.
        Map<Stadtplan.Lage, Material> ohneMarkt =
                new java.util.EnumMap<>(Stadtplan.Lage.class);
        Pflasterstoffe.abWerk().boden().forEach((lage, block) -> {
            if (lage != Stadtplan.Lage.MARKT) {
                ohneMarkt.put(lage, block);
            }
        });
        Pflasterstoffe loecherig = new Pflasterstoffe(ohneMarkt);

        Altfurtaufbau.Pflasterung raus =
                hand().pflastere(plan, loecherig, welt, List.of());

        assertEquals(Material.AIR,
                welt.bei(Stadtplan.MITTE_X, Stadtplan.BODEN_Y, Stadtplan.MITTE_Z),
                "der Markt bleibt unberuehrt und wird NICHT geloescht");
        assertEquals(loecherig.block(Stadtplan.Lage.RINGGASSE),
                welt.bei(Stadtplan.MITTE_X + 28, Stadtplan.BODEN_Y,
                        Stadtplan.MITTE_Z + 8), "die Gasse liegt trotzdem");
        // Und die Zahl ist um die Marktflaeche kleiner.
        long mitMarkt = hand().pflastere(plan, Pflasterstoffe.abWerk(),
                new Kartengriff(), List.of()).bloecke();
        long marktflaeche = (2L * Stadtplan.MARKT_RING + 1) * (2L * Stadtplan.MARKT_RING + 1);
        assertEquals(mitMarkt - marktflaeche, raus.bloecke(),
                "genau die Marktflaeche fehlt");
    }

}
