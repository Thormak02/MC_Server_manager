package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Quader;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Standprobe - ohne Server.
 *
 * <p>Moeglich, weil {@link Ankunftspunkt.Weltgriff} nur drei Fragen stellt. Mit einem
 * {@code World} waere die Probe nur auf einem laufenden Server pruefbar, und dann nicht am
 * Wasserloch, nicht am Hang und nicht auf einer Wiese.
 */
class AnkunftspunktTest {

    /**
     * Eine Welt aus einer Karte: was nicht eingetragen ist, ist Luft.
     *
     * <p>Die Attrappe fuehrt eine eigene, winzige Liste fester Bloecke. Im Spiel antwortet
     * das Bukkits Blockregistrierung; hier stehen nur die, die in diesen Proben vorkommen -
     * {@code Material#isSolid} gibt es im nackten Testlauf nicht.
     */
    private static final class Probewelt implements Ankunftspunkt.Weltgriff {

        private static final Set<Material> FEST = EnumSet.of(Material.STONE,
                Material.RED_SAND, Material.ORANGE_TERRACOTTA, Material.TERRACOTTA,
                Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE);

        private final Map<String, Material> bloecke = new HashMap<>();

        Probewelt setze(int x, int y, int z, Material art) {
            bloecke.put(x + "/" + y + "/" + z, art);
            return this;
        }

        /** Eine Saeule fester Bloecke bis zu dieser Hoehe. */
        Probewelt boden(int x, int z, int bis, Material art) {
            for (int y = bis - 3; y <= bis; y++) {
                setze(x, y, z, art);
            }
            return this;
        }

        @Override
        public Material bei(int x, int y, int z) {
            return bloecke.getOrDefault(x + "/" + y + "/" + z, Material.AIR);
        }

        @Override
        public boolean fest(int x, int y, int z) {
            return FEST.contains(bei(x, y, z));
        }

        @Override
        public int bodenHoehe(int x, int z) {
            // Wie OCEAN_FLOOR: der hoechste feste Block, Fluessigkeit uebergangen.
            for (int y = 200; y > -64; y--) {
                if (fest(x, y, z)) {
                    return y;
                }
            }
            return -64;
        }
    }

    @Test
    @DisplayName("Fester Boden mit zwei freien Bloecken darueber taugt")
    void festerBodenTaugt() {
        var welt = new Probewelt().boden(10, 20, 70, Material.STONE);

        var stand = Ankunftspunkt.suche(welt, 10, 20).orElseThrow();

        assertEquals(10, stand.x());
        assertEquals(71, stand.y(), "die Fuesse stehen auf dem Boden, nicht in ihm");
        assertEquals(20, stand.z());
    }

    @Test
    @DisplayName("Ein Grasbueschel ueber dem Boden ist kein Hindernis")
    void bewuchsIstKeinHindernis() {
        // Der Fehler, den diese Probe gefunden hat: Zuerst verlangte die Standprobe LUFT
        // ueber dem Boden. Damit waere jede Wiese abgelehnt worden und die Suche bis in
        // den achten Ring gelaufen - in Altfurts nacktem Sand faellt das nicht auf, in
        // Hochwald und Kornmark ueberall.
        var welt = new Probewelt()
                .boden(0, 0, 70, Material.STONE)
                .setze(0, 71, 0, Material.SHORT_GRASS);

        var stand = Ankunftspunkt.suche(welt, 0, 0).orElseThrow();

        assertEquals(0, stand.x(), "genau hier, nicht nebenan");
        assertEquals(71, stand.y());
    }

    @Test
    @DisplayName("Unter Wasser kommt niemand an - der gemessene Fall von Altfurt 0/0")
    void unterWasserNicht() {
        // Gemessen am 02.11.2026: Altfurt 0/0 hat Boden auf y 58 und Wasser bis y 62.
        // Der Boden ist fest, aber darueber steht Wasser - und genau dorthin setzt
        // Weltenbau bei einer frischen Welt den Erscheinungspunkt.
        var welt = new Probewelt().boden(0, 0, 58, Material.ORANGE_TERRACOTTA);
        for (int y = 59; y <= 62; y++) {
            welt.setze(0, y, 0, Material.WATER);
        }
        // Daneben, in Ring 1, liegt trockener Boden.
        welt.boden(1, 0, 58, Material.RED_SAND);

        var stand = Ankunftspunkt.suche(welt, 0, 0).orElseThrow();

        assertEquals(1, stand.x(), "ein Block weiter, wo es trocken ist");
        assertEquals(59, stand.y());
    }

    @Test
    @DisplayName("Ein Deckel ueber dem Boden macht die Spalte unbrauchbar")
    void deckelUeberDemBoden() {
        // Nicht aus Stein - der waere selbst der hoechste feste Block und damit der neue
        // Boden. Gemeint ist etwas, was Platz einnimmt, ohne Boden zu sein: hier Lava,
        // die ueber dem Stein steht.
        var welt = new Probewelt()
                .boden(5, 5, 70, Material.STONE)
                .setze(5, 71, 5, Material.LAVA);
        welt.boden(6, 5, 70, Material.STONE);

        var stand = Ankunftspunkt.suche(welt, 5, 5).orElseThrow();

        assertEquals(6, stand.x(), "nebenan ist Platz");
    }

    @Test
    @DisplayName("Auf Lava, Kaktus und Pulverschnee kommt niemand an")
    void schlechterBoden() {
        for (Material schlecht : new Material[] {Material.MAGMA_BLOCK, Material.CACTUS,
                Material.CAMPFIRE}) {
            var welt = new Probewelt().boden(0, 0, 70, schlecht);

            assertTrue(Ankunftspunkt.suche(welt, 0, 0).isEmpty(),
                    "auf " + schlecht + " soll niemand erscheinen");
        }
        assertTrue(Ankunftspunkt.schaedlich(Material.LAVA));
        assertTrue(Ankunftspunkt.schaedlich(Material.POWDER_SNOW));
        assertTrue(Ankunftspunkt.schaedlich(Material.WATER),
                "Wasser ist nicht fest - ohne diesen Eintrag waere Altfurt 0/0 gueltig");
        assertTrue(!Ankunftspunkt.schaedlich(Material.SHORT_GRASS));
        assertTrue(!Ankunftspunkt.schaedlich(null));
    }

    @Test
    @DisplayName("Wer nichts findet, gibt nichts zurueck")
    void nichtsGefunden() {
        // Eine Welt aus Leere - Wolkenwerder vor der Startinsel. Eine geratene Stelle
        // waere schlimmer als eine Absage, weil sie wie ein Erfolg aussieht.
        var leere = new Probewelt();

        assertTrue(Ankunftspunkt.suche(leere, 0, 0).isEmpty());
    }

    @Test
    @DisplayName("Die naechstgelegene Stelle gewinnt, nicht die erste gefundene")
    void naechstgelegeneGewinnt() {
        // Ring fuer Ring nach aussen: Ein Platz in Ring 1 schlaegt einen in Ring 2, egal
        // in welcher Himmelsrichtung er liegt.
        var welt = new Probewelt();
        welt.boden(0, 0, 70, Material.MAGMA_BLOCK);    // Wunschpunkt taugt nicht
        welt.boden(2, 0, 70, Material.STONE);          // Ring 2
        welt.boden(0, 1, 70, Material.STONE);          // Ring 1

        var stand = Ankunftspunkt.suche(welt, 0, 0).orElseThrow();

        assertEquals(0, stand.x());
        assertEquals(1, stand.z(), "Ring 1 vor Ring 2");
    }

    @Test
    @DisplayName("Die Ortsprobe prueft GENAU den Ort - sie sucht nicht")
    void ortsprobePruefTGenau() {
        // Der Unterschied, auf den es ankommt: /zurueck und /tpa kennen ihr Ziel. Wuerden
        // sie die ringweise Suche benutzen, sprang sie auf bodenHoehe(x,z) - und ein
        // Rueckweg aus einer Hoehle endete an der Oberflaeche. Hier wird der Ort
        // genommen, wie er ist.
        var welt = new Probewelt()
                .boden(0, 0, 40, Material.STONE)        // Hoehlenboden
                .setze(0, 60, 0, Material.STONE)        // Decke der Hoehle
                .setze(0, 61, 0, Material.STONE);       // Oberflaeche darueber

        assertTrue(Ankunftspunkt.taugt(welt, 0, 41, 0),
                "in der Hoehle ist Platz, und genau dort ist das Ziel");
        assertEquals(62, Ankunftspunkt.suche(welt, 0, 0).orElseThrow().y(),
                "die Suche wuerde dagegen an die Oberflaeche springen");
    }

    @Test
    @DisplayName("Ohne Boden unter den Fuessen taugt der Ort nicht")
    void keinBodenDarunter() {
        // Die Todesstelle eines Spielers, der ins Leere gefallen ist.
        var welt = new Probewelt();

        assertTrue(!Ankunftspunkt.taugt(welt, 0, 64, 0));
    }

    @Test
    @DisplayName("Lava unter den Fuessen taugt nicht - der belegte /back-Fehler")
    void lavaUnterDenFuessen() {
        // Wer neben einem Lavasee stirbt, bekommt mit /zurueck sonst denselben Tod
        // zweimal.
        var welt = new Probewelt().boden(0, 0, 70, Material.MAGMA_BLOCK);

        assertTrue(!Ankunftspunkt.taugt(welt, 0, 71, 0));
    }

    @Test
    @DisplayName("Wasser im Kopfraum taugt nicht")
    void wasserImKopfraum() {
        var welt = new Probewelt()
                .boden(0, 0, 70, Material.STONE)
                .setze(0, 72, 0, Material.WATER);

        assertTrue(!Ankunftspunkt.taugt(welt, 0, 71, 0), "der Kopf waere unter Wasser");
    }

    @Test
    @DisplayName("Ein Block in Fusshoehe taugt nicht, ein Grasbueschel schon")
    void fusshoehe() {
        var zugebaut = new Probewelt()
                .boden(0, 0, 70, Material.STONE)
                .setze(0, 71, 0, Material.STONE);
        var bewachsen = new Probewelt()
                .boden(0, 0, 70, Material.STONE)
                .setze(0, 71, 0, Material.SHORT_GRASS);

        assertTrue(!Ankunftspunkt.taugt(zugebaut, 0, 71, 0));
        assertTrue(Ankunftspunkt.taugt(bewachsen, 0, 71, 0));
    }

    @Test
    @DisplayName("Ein Block ueber dem Kopf taugt nicht - zwei Bloecke braucht ein Spieler")
    void kopfraumZuNiedrig() {
        var welt = new Probewelt()
                .boden(0, 0, 70, Material.STONE)
                .setze(0, 72, 0, Material.STONE);

        assertTrue(!Ankunftspunkt.taugt(welt, 0, 71, 0));
    }

    @Test
    @DisplayName("Die Spaltensuche benutzt dieselbe Ortsprobe")
    void spaltensucheBenutztOrtsprobe() {
        // Damit es nicht zwei Tabellen gibt, die auseinanderlaufen: Was die Suche
        // zurueckgibt, muss die Ortsprobe bestaetigen.
        var welt = new Probewelt().boden(3, 4, 70, Material.RED_SAND);

        var stand = Ankunftspunkt.suche(welt, 3, 4).orElseThrow();

        assertTrue(Ankunftspunkt.taugt(welt, stand.x(), stand.y(), stand.z()));
    }

    @Test
    @DisplayName("Die Suche hoert nach acht Ringen auf")
    void suchfeldIstBegrenzt() {
        // 17x17 Bloecke. Ein Teleport, der hundert Bloecke weit sucht, bringt den Spieler
        // woanders hin als an den Ort, der gemeint war.
        var welt = new Probewelt().boden(20, 0, 70, Material.STONE);

        assertTrue(Ankunftspunkt.suche(welt, 0, 0).isEmpty(),
                "20 Bloecke weit wird nicht gesucht");
        assertTrue(Ankunftspunkt.suche(welt, 13, 0).isPresent(),
                "sieben Bloecke weit schon");
    }

    // ================================================ Die Torprobe

    /**
     * Eine gebaute Wand mit einer Oeffnung - der Fall vom 02.11.2026.
     *
     * <p>Boden auf {@code boden}, Wand von {@code boden+1} bis {@code boden+3}, und in
     * der Mitte drei Bloecke Loch. Darueber ein Sturz. Genau so sieht die Reihe am
     * Bundesmarkt aus, und genau an ihr hat die ringweise Suche damals alle sieben Tore
     * fuer unbegehbar erklaert: Sie fragt {@code bodenHoehe} und findet den Sturz.
     */
    private static Probewelt wandMitLoch(int boden, int z) {
        var welt = new Probewelt();
        for (int x = 9; x <= 13; x++) {
            welt.boden(x, z, boden, Material.STONE);
            welt.setze(x, boden + 4, z, Material.STONE);
        }
        for (int hoehe = 1; hoehe <= 3; hoehe++) {
            welt.setze(9, boden + hoehe, z, Material.STONE);
            welt.setze(13, boden + hoehe, z, Material.STONE);
        }
        return welt;
    }

    @Test
    @DisplayName("Ein Tor in einer gebauten Wand ist begehbar - der Fall vom 02.11.2026")
    void torInEinerWand() {
        // Die Probe, die es am 02.11.2026 nicht gab: Damals lief die ringweise Suche auf
        // die Torquader, fand den STURZ ueber der Oeffnung und erklaerte eine gerade
        // gebaute Wand fuer unbegehbar - sieben Klagen fuer sieben richtige Tore.
        var welt = wandMitLoch(70, 0);

        assertNull(Ankunftspunkt.torklage(welt, new Quader(10, 71, 0, 12, 73, 0)),
                "drei Bloecke Loch ueber festem Boden sind ein Tor");
        // Und die Gegenrichtung in derselben Welt: Die ringweise Suche, die hier NICHT
        // benutzt wird, findet oben auf dem Sturz einen Platz - haette torklage sie
        // genommen, waere die Hoehe um vier zu hoch.
        assertEquals(welt.bodenHoehe(11, 0) + 1,
                Ankunftspunkt.suche(welt, 11, 0).orElseThrow().y(),
                "die Suche landet auf dem Sturz - darum fragt die Torprobe sie nicht");
        assertEquals(74, welt.bodenHoehe(11, 0),
                "der hoechste feste Block ueber dem Tor IST der Sturz");
    }

    @Test
    @DisplayName("Ein Tor in der Luft nennt die Hoehe, auf der es stehen muesste")
    void torInDerLuft() {
        // KEINE Wand mit Sturz, sondern flacher Boden: Auf dem Sturz einer Wand kann man
        // stehen, und ein Vorschlag, der ihn nennt, waere richtig. Hier soll der BODEN
        // gefunden werden, und darum gibt es hier nur Boden.
        var welt = new Probewelt();
        for (int x = 10; x <= 12; x++) {
            welt.boden(x, 0, 70, Material.STONE);
        }

        String klage = Ankunftspunkt.torklage(welt, new Quader(10, 75, 0, 12, 77, 0));

        assertTrue(klage != null && klage.contains("ist kein Boden"), "" + klage);
        assertTrue(klage.contains("Passend waere: von [10, 71, 0] bis [12, 73, 0]"),
                "der Vorschlag muss die Zahlen zum Abtippen nennen: " + klage);
    }

    @Test
    @DisplayName("Ueber einem Meeresboden gibt es KEINE Hoehe - und das sagt der Satz")
    void torUeberWasser() {
        // DER FUND VOM 18.11.2026, gemessen am heutigen Altfurt: Die ganze Torreihe am
        // Bundesmarkt liegt im Meer. Der alte Vorschlag suchte nur den ersten festen
        // Block unter der Fusshoehe - und riet "Passend waere: von [86, 51, 0] ...",
        // also mitten ins Wasser. Wer das abtippte, bekam beim naechsten Start die
        // naechste Klage ("steht WATER im Tor"). Ein Vorschlag, der in die naechste
        // Klage fuehrt, ist schlimmer als keiner: Er sieht aus wie eine Antwort.
        var welt = new Probewelt();
        for (int x = 86; x <= 88; x++) {
            welt.boden(x, 0, 51, Material.STONE);
            for (int y = 52; y <= 62; y++) {
                welt.setze(x, y, 0, Material.WATER);
            }
        }

        String klage = Ankunftspunkt.torklage(welt, new Quader(86, 71, 0, 88, 73, 0));

        assertTrue(klage != null && klage.contains("ist kein Boden"), "" + klage);
        assertFalse(klage.contains("Passend waere"),
                "auf einem Meeresboden ist keine Hoehe passend: " + klage);
        assertTrue(klage.contains("hier muss gebaut werden"), klage);
    }

    @Test
    @DisplayName("Eine Hoehe taugt nur, wenn JEDE Saeule der Oeffnung sie traegt")
    void vorschlagFragtDieGanzeOeffnung() {
        // Der Unterschied zwischen "die eine aufgefallene Saeule" und "die Oeffnung":
        // Links und rechts steht der Boden auf 70, in der Mitte liegt eine Pfuetze -
        // Boden 67, Wasser bis 70. Auf Hoehe 71 traegt also nur zwei Drittel der
        // Oeffnung. Wer nur die erste Saeule fragt, nennt 71 und irrt.
        var welt = new Probewelt();
        welt.boden(10, 0, 70, Material.STONE);
        welt.boden(12, 0, 70, Material.STONE);
        welt.boden(11, 0, 67, Material.STONE);
        for (int y = 68; y <= 70; y++) {
            welt.setze(11, y, 0, Material.WATER);
        }

        String klage = Ankunftspunkt.torklage(welt, new Quader(10, 80, 0, 12, 82, 0));

        assertTrue(klage != null && klage.contains("ist kein Boden"), "" + klage);
        assertFalse(klage.contains("Passend waere"),
                "keine Hoehe traegt alle drei Saeulen: " + klage);
    }

    @Test
    @DisplayName("Ein zugemauertes Tor nennt den Block und KEINE Zahlen")
    void torZugemauert() {
        var welt = wandMitLoch(70, 0);
        welt.setze(11, 72, 0, Material.STONE);

        String klage = Ankunftspunkt.torklage(welt, new Quader(10, 71, 0, 12, 73, 0));

        assertTrue(klage != null && klage.contains("steht STONE im Tor"), "" + klage);
        assertFalse(klage.contains("Passend waere"),
                "der Boden traegt ja - ein Vorschlag nennte dieselbe Hoehe wieder: "
                        + klage);
    }

    @Test
    @DisplayName("Ein Tor auf einem Magmablock ist eine Klage, nicht ein Durchgang")
    void torAufMagma() {
        // Der Boden traegt, und darum war dieser Fall vor dem 18.11.2026 klaglos: Die
        // alte Pruefung fragte nur, ob unter dem Tor etwas FEST ist. Die Standprobe
        // fragt beides, und seitdem tun es beide.
        var welt = wandMitLoch(70, 0);
        welt.setze(11, 70, 0, Material.MAGMA_BLOCK);

        String klage = Ankunftspunkt.torklage(welt, new Quader(10, 71, 0, 12, 73, 0));

        assertTrue(klage != null && klage.contains("MAGMA_BLOCK"), "" + klage);
        assertTrue(klage.contains("nimmt Schaden"), klage);
    }

    @Test
    @DisplayName("Die Torprobe fragt JEDE Saeule und JEDE Hoehe der Oeffnung")
    void torprobeFragtAlles() {
        // GEGEN DIE STILL GRUENE SCHLEIFE: Eine Pruefung, die nur die erste Saeule oder
        // nur die Fusshoehe ansieht, ist bei einem 3x3-Tor zu acht Neunteln blind. Hier
        // wird jede Stelle der Oeffnung EINZELN kaputtgemacht, und jede muss auffallen.
        int gepruefteStellen = 0;
        for (int x = 10; x <= 12; x++) {
            for (int hoehe = 0; hoehe <= 1; hoehe++) {
                var welt = wandMitLoch(70, 0);
                welt.setze(x, 71 + hoehe, 0, Material.STONE);
                gepruefteStellen++;
                assertTrue(Ankunftspunkt.torklage(welt,
                                new Quader(10, 71, 0, 12, 73, 0)) != null,
                        "ein Block bei " + x + "/" + (71 + hoehe) + "/0 bleibt"
                                + " unbemerkt - die Pruefung sieht nicht die ganze"
                                + " Oeffnung an");
            }
            var ohneBoden = wandMitLoch(70, 0);
            ohneBoden.setze(x, 70, 0, Material.AIR);
            gepruefteStellen++;
            assertTrue(Ankunftspunkt.torklage(ohneBoden,
                            new Quader(10, 71, 0, 12, 73, 0)) != null,
                    "ein Loch im Boden bei " + x + "/70/0 bleibt unbemerkt");
        }
        // Der Sturz (die dritte Hoehe) wird ABSICHTLICH nicht verlangt: Ein Spieler ist
        // zwei Bloecke hoch, und ein Tor mit einem Block im Sturz ist begehbar. Darum
        // sind es neun Stellen und nicht zwoelf.
        assertEquals(9, gepruefteStellen, "drei Saeulen zu je Boden, Fuss und Kopf");
    }
}
