package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Art;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Rasterplan;
import de.freimarken.kern.Zustand;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fuellen, Zaehlen und Zurueckbauen an einer Stadt in einer Map.
 *
 * <p>Die Zahlen hier sind vorgerechnet, nicht abgelesen. Fuer die Gruppe 0/0 + 1/0 liegt
 * die Fuge bei x 64..70 und z 0..63, also 448 Stellen: 2 x 64 auf den Mauerreihen
 * (x = 64 und x = 70) und 5 x 64 auf den Wegreihen (x = 65..69). Dazu kommen die
 * Mauerstuecke an den Bandenden, x = 65..69 bei z = -1 und z = 64, also 10 Stellen.
 *
 * <p>Das Fuellen aendert je Wegreihen-Stelle einen Block (Oberflaeche wird Gras, darueber
 * war und bleibt Luft), je Mauerreihen-Stelle zwei (Oberflaeche und die Krone verschwindet)
 * und je Mauerstueck zwei: 320 + 256 + 20 = 596.
 */
class WegbauTest {

    private static final String WELT = "freimarken:marktstadt";
    private static final int UNTEN = -64;

    private final Rasterplan plan = Rasterplan.marktstadt();
    private final Probewelt welt = new Probewelt(plan, UNTEN);
    private final Wegbau wegbau = new Wegbau(plan, welt);
    private final UUID anna = UUID.randomUUID();

    private Parzelle feld(int x, int z) {
        return new Parzelle(1L, WELT, x, z, anna, Set.of(), 0L, Instant.EPOCH,
                Art.PARZELLE, 7L, Zustand.AKTIV);
    }

    private List<Parzelle> gruppe(int... xz) {
        List<Parzelle> felder = new ArrayList<>();
        for (int i = 0; i < xz.length; i += 2) {
            felder.add(feld(xz[i], xz[i + 1]));
        }
        return felder;
    }

    /** Die Warnzahlen beim ganzen Auftrennen - nichts bleibt Gruppe. */
    private Wegbau.Aufwand aufwand(List<Parzelle> gruppe) {
        return wegbau.aufwandBeimAuftrennen(gruppe, List.of());
    }

    /** Der ganze Weg zurueck. */
    private int baueZurueck(List<Parzelle> gruppe) {
        return wegbau.baueZurueck(gruppe, List.of());
    }

    /** Die Gruppe 0/0 + 1/0, gefuellt. */
    private List<Parzelle> zweiFelderGefuellt() {
        List<Parzelle> gruppe = gruppe(0, 0, 1, 0);
        assertEquals(596, wegbau.fuelle(gruppe, List.of()), "das Fuellen der ersten Fuge");
        return gruppe;
    }

    @Test
    @DisplayName("Fuellen und Zurueckbauen lassen die Stadt genau so zurueck, wie sie war")
    void dieRundreiseAendertNichts() {
        // Die staerkste Probe: nicht zwei Zahlen vergleichen, sondern die Welt. Eine Stelle
        // zu viel oder zu wenig faellt mit Koordinate auf - Blockzahlen koennen sich
        // ausgleichen, ein Abbild nicht.
        assertTrue(welt.abweichungen().isEmpty(), "die Stadt beginnt unberuehrt");
        List<Parzelle> gruppe = zweiFelderGefuellt();
        assertEquals(596, welt.abweichungen().size(),
                "so viele Bloecke, wie das Fuellen gemeldet hat - keine Stelle zweimal, "
                        + "und keine wieder auf den Generatorwert zurueck");

        assertEquals(596, baueZurueck(gruppe), "der Weg kommt zurueck");
        assertEquals(Set.of(), welt.abweichungen().keySet(),
                "danach ist nichts mehr anders als im Generator");
    }

    @Test
    @DisplayName("Auf einer frisch gefuellten Flaeche geht nichts verloren")
    void frischGefuelltKostetNichts() {
        // Die Zusage aus dem Javadoc, jetzt wirklich geprueft: direkt nach dem
        // Zusammenlegen ist beide Zahlen null. Das haengt am Hoehenbereich der Spalte, und
        // den fuehrt nur dieser Weg aus.
        List<Parzelle> gruppe = zweiFelderGefuellt();
        assertEquals(new Wegbau.Aufwand(0, 0), aufwand(gruppe));
        assertTrue(aufwand(gruppe).istNichts());
    }

    @Test
    @DisplayName("Die Mauerkrone wird gesetzt, obwohl der hoechste Block tiefer liegt")
    void dieKroneFaelltNichtAus() {
        // Auf einer unberuehrten Fuge ist der hoechste Block die Oberflaeche auf 62. Ohne
        // das Mindestmass in der Spalte faellt die Schicht 63 ganz aus - und dann fehlen
        // beim Zurueckbau 128 Kronenbloecke je Band, ohne dass eine Zahl auffaellig wird.
        List<Parzelle> gruppe = zweiFelderGefuellt();
        baueZurueck(gruppe);
        for (int z = 0; z <= 63; z += 21) {
            assertEquals(Material.STONE_BRICK_SLAB, welt.bei(64, Stadtboden.BODEN + 1, z),
                    "Krone auf der Mauerreihe x=64, z=" + z);
            assertEquals(Material.STONE_BRICK_SLAB, welt.bei(70, Stadtboden.BODEN + 1, z),
                    "Krone auf der Mauerreihe x=70, z=" + z);
        }
    }

    @Test
    @DisplayName("Eine abgetragene Oberflaeche wird gezaehlt und wieder gefuellt")
    void abgetrageneOberflaecheWirdGefuellt() {
        // Der zweite Fall des Mindestmasses: Hier liegt der hoechste Block UNTER der
        // Oberflaeche. Ohne das Mindestmass laeuft keine der beiden Schleifen ueber die
        // Stelle, die Warnung meldet 0, und im wiederhergestellten Weg bleibt ein Loch.
        List<Parzelle> gruppe = zweiFelderGefuellt();
        welt.setze(67, Stadtboden.BODEN, 30, Material.AIR);

        assertEquals(new Wegbau.Aufwand(0, 1), aufwand(gruppe),
                "ein Loch ist kein Verlust, sondern eine Fuellung");
        baueZurueck(gruppe);
        assertEquals(Material.SMOOTH_STONE, welt.bei(67, Stadtboden.BODEN, 30),
                "der Weg hat kein Loch");
    }

    @Test
    @DisplayName("Ein Haus auf der Fuge wird gewarnt und geraeumt - mit derselben Zahl")
    void hausWirdGewarntUndGeraeumt() {
        // 3 x 3 x 3 Bretter auf den Wegreihen, von 63 bis 65. Gewarnt werden 27 Bloecke;
        // der Zurueckbau aendert die 596 des Weges plus genau diese 27.
        List<Parzelle> gruppe = zweiFelderGefuellt();
        for (int x = 66; x <= 68; x++) {
            for (int y = Stadtboden.BODEN + 1; y <= Stadtboden.BODEN + 3; y++) {
                for (int z = 30; z <= 32; z++) {
                    welt.setze(x, y, z, Material.OAK_PLANKS);
                }
            }
        }

        assertEquals(new Wegbau.Aufwand(27, 0), aufwand(gruppe));
        assertEquals(596 + 27, baueZurueck(gruppe),
                "der Weg und das Haus, kein Block doppelt");
        assertEquals(Set.of(), welt.abweichungen().keySet(), "auch das Haus ist weg");
    }

    @Test
    @DisplayName("Pflaster aus dem Strassenblock wird nicht gewarnt - es bleibt ja liegen")
    void pflasterAusStrassenblockBleibtLiegen() {
        // Wer die Fuge mit dem Strassenblock der Stadt pflastert, verliert beim Auftrennen
        // keinen Block: Dort liegt schon das Richtige. Es zu melden hiesse, vor einer Tat
        // zu warnen, die nicht stattfindet - und die Schlusszahl ist um dieselben drei
        // Stellen kleiner, weil nichts zu tun war.
        List<Parzelle> gruppe = zweiFelderGefuellt();
        for (int x = 66; x <= 68; x++) {
            welt.setze(x, Stadtboden.BODEN, 10, Material.SMOOTH_STONE);
        }

        assertEquals(new Wegbau.Aufwand(0, 0), aufwand(gruppe));
        assertEquals(596 - 3, baueZurueck(gruppe));
    }

    @Test
    @DisplayName("Ueber einem Mauerstueck wird nicht geraeumt - das ist fremdes Werk")
    void ueberDemMauerstueckWirdNichtGeraeumt() {
        // Das Bandende war oeffentlicher Weg. Was dort steht - eine Laterne der Stadt etwa -
        // gehoert nicht dem Eigentuemer, und die Zaehlung laesst diese Stellen aus. Wuerde
        // der Zurueckbau sie trotzdem raeumen, riss er fremdes Werk ab, ungewarnt.
        List<Parzelle> gruppe = zweiFelderGefuellt();
        welt.setze(67, Stadtboden.BODEN + 2, -1, Material.OAK_PLANKS);

        assertEquals(new Wegbau.Aufwand(0, 0), aufwand(gruppe));
        assertEquals(596, baueZurueck(gruppe), "nur der Weg, nicht die Laterne");
        assertEquals(Material.OAK_PLANKS, welt.bei(67, Stadtboden.BODEN + 2, -1),
                "die Laterne steht noch");
    }

    @Test
    @DisplayName("Ein angehaengtes Feld laesst den Bau auf der alten Fuge stehen")
    void angehaengtesFeldLaesstDenBauStehen() {
        // Die Probe in der Welt zu dem, was der Kern geometrisch sichert: Das zweite
        // Zusammenlegen fuellt nur die neue Fuge. Ohne das Auslassen wuerde die Schicht
        // ueber der alten Fuge geloescht - 448 Stellen, ohne Warnung und ohne Drops.
        List<Parzelle> zwei = zweiFelderGefuellt();
        welt.setze(67, Stadtboden.BODEN + 1, 30, Material.OAK_PLANKS);

        List<Parzelle> drei = gruppe(0, 0, 1, 0, 1, 1);
        assertEquals(596, wegbau.fuelle(drei, List.of(zwei)),
                "nur das neue Band und seine zwei Enden");
        assertEquals(Material.OAK_PLANKS, welt.bei(67, Stadtboden.BODEN + 1, 30),
                "der Bau auf der alten Fuge steht noch");
    }

    @Test
    @DisplayName("Verrottetes Gras kostet nichts, wird aber ersetzt")
    void verrottetesGrasKostetNichts() {
        // Gras wird zu Erde, sobald etwas Lichtdichtes darauf steht oder ein Schaf
        // vorbeikommt. Keine der beiden Zahlen darf das nennen - sonst meldet eine
        // unberuehrte Gruppe einen Verlust. Ersetzt wird die Stelle trotzdem, sie gehoert
        // zu den 596.
        List<Parzelle> gruppe = zweiFelderGefuellt();
        welt.setze(67, Stadtboden.BODEN, 30, Material.DIRT);

        assertEquals(new Wegbau.Aufwand(0, 0), aufwand(gruppe));
        assertEquals(596, baueZurueck(gruppe));
    }

    @Test
    @DisplayName("Ein einzelnes Feld herausloesen laesst die anderen Wege liegen")
    void einFeldHerausloesenLaesstDieAnderenWegeLiegen() {
        // Die Reihe 0/0, 1/0, 2/0 hat zwei Fugen. Loest sich 2/0 heraus, kommt nur der Weg
        // zwischen 1/0 und 2/0 zurueck - die Fuge zwischen 0/0 und 1/0 bleibt Bauland.
        // Ohne diese Auslassung muesste der Spieler sie neu bezahlen, nur weil er ein Feld
        // abgegeben hat.
        List<Parzelle> drei = gruppe(0, 0, 1, 0, 2, 0);
        assertEquals(2 * 596, wegbau.fuelle(drei, List.of()), "zwei Fugen, zwei Bandenden");
        List<Parzelle> bleibend = gruppe(0, 0, 1, 0);

        assertEquals(596, wegbau.baueZurueck(drei, List.of(bleibend)),
                "nur der Weg zum geloesten Feld");
        // Die erste Fuge ist noch Bauland, die zweite wieder Weg.
        assertEquals(Material.GRASS_BLOCK, welt.bei(67, Stadtboden.BODEN, 30),
                "die Fuge zwischen 0/0 und 1/0 bleibt Bauland");
        assertEquals(Material.SMOOTH_STONE, welt.bei(138, Stadtboden.BODEN, 30),
                "die Fuge zwischen 1/0 und 2/0 ist wieder Weg");
    }

    @Test
    @DisplayName("Der Bau auf den bleibenden Wegen wird beim Herausloesen nicht gewarnt")
    void bauAufBleibendenWegenKostetNichts() {
        // Gewarnt wird nur, was der Zurueckbau wirklich anfasst. Ein Haus auf der ersten
        // Fuge bleibt stehen, also darf es in keiner der beiden Zahlen vorkommen.
        List<Parzelle> drei = gruppe(0, 0, 1, 0, 2, 0);
        wegbau.fuelle(drei, List.of());
        welt.setze(67, Stadtboden.BODEN + 1, 30, Material.OAK_PLANKS);
        welt.setze(138, Stadtboden.BODEN + 1, 30, Material.OAK_PLANKS);
        List<Parzelle> bleibend = gruppe(0, 0, 1, 0);

        assertEquals(new Wegbau.Aufwand(1, 0),
                wegbau.aufwandBeimAuftrennen(drei, List.of(bleibend)),
                "nur der Block auf der Fuge, die zurueckgebaut wird");
        wegbau.baueZurueck(drei, List.of(bleibend));
        assertEquals(Material.OAK_PLANKS, welt.bei(67, Stadtboden.BODEN + 1, 30),
                "der Bau auf der bleibenden Fuge steht noch");
        assertEquals(Material.AIR, welt.bei(138, Stadtboden.BODEN + 1, 30),
                "der Bau auf der zurueckgebauten Fuge ist weg");
    }

    @Test
    @DisplayName("Ein Fremdblock im Schacht unter der Fuge wird gewarnt und ersetzt")
    void fremdblockImSchacht() {
        // Die Abwaertsschleife bricht erst ab, wenn sie findet, was dort hingehoert - nicht
        // beim ersten festen Block. Sonst blieb unter einer wieder oeffentlichen Strasse
        // ein Hohlraum hinter einer Glasplatte zurueck.
        List<Parzelle> gruppe = zweiFelderGefuellt();
        for (int y = Stadtboden.BODEN - 1; y >= Stadtboden.BODEN - 6; y--) {
            welt.setze(67, y, 30, Material.AIR);
        }
        welt.setze(67, Stadtboden.BODEN - 4, 30, Material.GLASS);

        // Fuenf Luftstellen werden gefuellt, das Glas verschwindet.
        assertEquals(new Wegbau.Aufwand(1, 5), aufwand(gruppe));
        baueZurueck(gruppe);
        assertEquals(Set.of(), welt.abweichungen().keySet(),
                "die Spalte ist wieder, wie der Generator sie legt");
    }
}
