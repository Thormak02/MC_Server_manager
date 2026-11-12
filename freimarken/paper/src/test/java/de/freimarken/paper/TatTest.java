package de.freimarken.paper;

import de.freimarken.kern.Tat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Welche {@link Tat} an einer Stelle liegt - gemessen, nicht behauptet.
 *
 * <p><b>Die Probe, die Teil D1 ueberhaupt erst zu etwas macht</b> [12.11.2026]. Vor den
 * Taten stand hier {@code istHeikel}, eine Frage mit zwei Antworten - und sie war von
 * KEINER Verhaltensprobe gedeckt; die Einschraenkung stand als Absatz in
 * {@code GrenzeTest} 31x. Das war vertretbar, solange "heikel" ueberall dasselbe Nein
 * bedeutete. Jetzt entscheidet dieselbe Methode, WELCHES Recht ein Griff braucht: Gibt
 * sie an einem Fass BENUTZEN statt BEHAELTER, oeffnet ein Stadtbuerger mit Tuerrecht
 * fremde Fasser, und keine andere Probe im Projekt saehe es.
 *
 * <p><b>Eine eigene Datei und kein Anhang an {@code SchutzwacheTest}</b>: Jene Proben
 * gehen durch {@code verweigere} und brauchen Welt, Lager und Besitzbuch. Hier wird eine
 * einzige statische Methode gefragt, und was sie braucht, ist ein Block mit drei
 * Antworten - {@code getState}, {@code getBlockData}, {@code getType}.
 *
 * <p><b>Die Blockgruppen werden alle sieben angemeldet</b>, auch die leeren: Eine Gruppe,
 * die niemand angemeldet hat, WIRFT bei {@code isTagged} (siehe {@link Serverprobe}), und
 * der Weg zu {@code null} laeuft durch jede von ihnen. Ohne die leeren Anmeldungen
 * scheiterte die Probe fuer Stein an der ersten Gruppe - und zwar mit einer Klage, nicht
 * mit einer Aussage.
 */
class TatTest {

    /** Eine eigene Welt, damit die angemeldeten Stellen keiner anderen Probe gehoeren. */
    private static final String WELT = "freimarken:tatprobe";

    /** Die Bloecke dieser Welt - nur ihre ART wird gefragt. */
    private static final Map<String, Material> ARTEN = new HashMap<>();

    private static World welt;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
        // Alle sieben Gruppen aus HEIKLE_GRUPPEN, in genau dieser Besetzung: Nur BEDS und
        // ALL_SIGNS tragen hier ein Mitglied, weil nur sie in dieser Probe vorkommen. Die
        // anderen fuenf sind LEER angemeldet und nicht weggelassen - weggelassen wuerde
        // jede Frage nach Stein an ihnen scheitern.
        Serverprobe.gruppe("flower_pots", Set.of());
        Serverprobe.gruppe("candles", Set.of());
        Serverprobe.gruppe("candle_cakes", Set.of());
        Serverprobe.gruppe("cauldrons", Set.of());
        Serverprobe.gruppe("beds", Set.of(Material.RED_BED));
        Serverprobe.gruppe("all_signs", Set.of(Material.OAK_SIGN));
        Serverprobe.gruppe("cave_vines", Set.of());
        welt = Serverprobe.welt(WELT, new Artengriff());
    }

    /** Ein Weltgriff, der nur Arten haelt - mehr fragt {@code tatAn} nicht. */
    private static final class Artengriff implements Weltgriff {

        @Override
        public int unten() {
            return -64;
        }

        @Override
        public int oben(int x, int z) {
            return 64;
        }

        @Override
        public Material bei(int x, int y, int z) {
            return ARTEN.getOrDefault(x + "/" + y + "/" + z, Material.AIR);
        }

        @Override
        public void setze(int x, int y, int z, Material block) {
            ARTEN.put(x + "/" + y + "/" + z, block);
        }
    }

    /** Legt einen Block dieser Art hin und gibt ihn - ohne weitere Anmeldung. */
    private static Block blockAus(int x, Material art) {
        ARTEN.put(x + "/64/0", art);
        return welt.getBlockAt(x, 64, 0);
    }

    @Test
    @DisplayName("37 - Stein ist keine Tat: wo nichts zu schuetzen ist, fragt die Wache"
            + " nicht")
    void steinIstKeineTat() {
        assertNull(Schutzwache.tatAn(blockAus(1, Material.STONE)),
                "Stein ist kein Behaelter, nicht oeffenbar, nicht schaltbar und in"
                        + " keiner der sieben Gruppen - der Klick darauf geht die Wache"
                        + " nichts an. Ein Urteil hier hiesse, dass jeder Rechtsklick in"
                        + " der Welt durch die Ortsregel laeuft.");
    }

    @Test
    @DisplayName("37b - eine Tuer ist BENUTZEN, ein Knopf auch - beide Haelften der"
            + " Bedingung")
    void tuerUndKnopfSindBenutzen() {
        Serverprobe.oeffenbar(WELT, 2, 64, 0);
        Serverprobe.schaltbar(WELT, 3, 64, 0);

        assertEquals(Tat.BENUTZEN,
                Schutzwache.tatAn(blockAus(2, Material.OAK_DOOR)),
                "Openable - eine Tuer, eine Falltuer, ein Zauntor");
        // Die zweite Haelfte eigens: tatAn fragt Openable ODER Powerable, und eine Probe
        // nur auf das erste bliebe gruen, wenn jemand das zweite wegnimmt. Dann fiele
        // jeder Knopf und jede Druckplatte auf BAUEN zurueck - lautlos, denn verboten ist
        // beides, solange niemand Rechte abstuft.
        assertEquals(Tat.BENUTZEN,
                Schutzwache.tatAn(blockAus(3, Material.STONE_BUTTON)),
                "Powerable - ein Knopf, ein Hebel, eine Druckplatte");
    }

    @Test
    @DisplayName("37c - eine Truhe ist BEHAELTER")
    void truheIstBehaelter() {
        Serverprobe.behaelter(WELT, 4, 64, 0);

        assertEquals(Tat.BEHAELTER,
                Schutzwache.tatAn(blockAus(4, Material.CHEST)),
                "der Blockzustand ist ein InventoryHolder");
    }

    @Test
    @DisplayName("37d - EIN FASS IST BEIDES, und die Reihenfolge entscheidet: BEHAELTER")
    void dasFassIstBehaelterUndNichtBenutzen() {
        // DER FALL, UM DEN ES GEHT. Material.BARREL ist ein Behaelter UND seine
        // Blockdaten sind Openable - in Bukkit erbt Barrel von Openable, weil ein Fass
        // aufklappt. Beantwortete tatAn die Fragen in der anderen Reihenfolge, waere ein
        // Fass BENUTZEN, und jeder, der in einer Freistadt Tueren bedienen darf, raeumte
        // damit fremde Fasser aus. Dasselbe gilt fuer eine Falltuer mit Trichter
        // darunter und fuer jeden kuenftigen Block, der beides ist.
        Serverprobe.behaelter(WELT, 5, 64, 0);
        Serverprobe.oeffenbar(WELT, 5, 64, 0);

        assertEquals(Tat.BEHAELTER,
                Schutzwache.tatAn(blockAus(5, Material.BARREL)),
                "Ein Fass ist beides, und der Inhalt ist das Schutzwuerdige. Wer diese"
                        + " Reihenfolge umstellt, gibt mit dem Tuerrecht die Fasser her.");
    }

    @Test
    @DisplayName("37e - was nur in den zwei Listen steht, ist BAUEN")
    void listenblockIstBauen() {
        // Drei Wege in denselben Fall, und sie laufen durch VERSCHIEDENE Zweige: das
        // Drachenei steht in HEIKEL, das Bett in Tag.BEDS, das Schild in Tag.ALL_SIGNS.
        // Die beiden Gruppen sind der Grund, warum diese Probe alle sieben anmeldet.
        assertEquals(Tat.BAUEN,
                Schutzwache.tatAn(blockAus(6, Material.DRAGON_EGG)),
                "HEIKEL - es springt weg, wenn man es anklickt");
        assertEquals(Tat.BAUEN,
                Schutzwache.tatAn(blockAus(7, Material.RED_BED)),
                "Tag.BEDS - ein Bett ist weder Behaelter noch Openable");
        assertEquals(Tat.BAUEN,
                Schutzwache.tatAn(blockAus(8, Material.OAK_SIGN)),
                "Tag.ALL_SIGNS - darauf stehen fremde Preise");
    }

    @Test
    @DisplayName("37f - am WESEN dieselbe Trennung: Truhenboot BEHAELTER, Tier BAUEN")
    void amWesenDieselbeTrennung() {
        assertEquals(Tat.BEHAELTER,
                Schutzwache.tatAmWesen(Wesenprobe.mitInventar()),
                "eine Kisten-Lore und ein Truhenboot tragen dasselbe Merkmal wie ein"
                        + " Behaelterblock");
        assertEquals(Tat.BAUEN, Schutzwache.tatAmWesen(Wesenprobe.schlicht()),
                "ein Ruestungsstaender, ein Bilderrahmen, ein Tier - sie werden"
                        + " bestueckt oder abgeraeumt, und das veraendert, was dasteht");
    }
}
