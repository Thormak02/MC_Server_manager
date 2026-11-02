package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
