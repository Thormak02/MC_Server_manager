package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Quader;
import de.freimarken.kern.Richtung;
import de.freimarken.kern.Stadtplan;
import de.freimarken.kern.Torhausplan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Setzen eines Torhauses - ohne Server, ueber den {@link Weltgriff}.
 *
 * <p>Dieselbe Bauart wie {@code AltfurtaufbauTest}: Die Welt ist eine Karte, und jeder
 * gesetzte Block bleibt nachzaehlbar.
 */
@DisplayName("Das Setzen eines Torhauses")
class TorhausaufbauTest {

    /** Die Welt als Karte - jeder gesetzte Block bleibt nachzaehlbar. */
    private static final class Kartengriff implements Weltgriff {

        private final Map<String, Material> gesetzt = new HashMap<>();

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
            return gesetzt.getOrDefault(x + "/" + y + "/" + z, Material.AIR);
        }

        @Override
        public void setze(int x, int y, int z, Material block) {
            gesetzt.put(x + "/" + y + "/" + z, block);
        }
    }

    /** Eine Mitschrift der Feinnaht - wer sie nie ruft, faellt damit auf. */
    private static final class Feinmitschrift implements Altfurtaufbau.Feingriff {

        private final List<String> rufe = new ArrayList<>();

        @Override
        public void setzeTuer(int x, int y, int z, Richtung zeigt, Material art) {
            rufe.add("tuer " + x + "/" + y + "/" + z);
        }

        @Override
        public void setzeSchild(int x, int y, int z, Richtung zeigt, Material art,
                List<String> zeilen) {
            rufe.add("schild " + x + "/" + y + "/" + z);
        }

        @Override
        public void setzeDachtreppe(int x, int y, int z, Richtung bergauf, Material art) {
            rufe.add("dach " + x + "/" + y + "/" + z + " " + bergauf.wort() + " "
                    + art.name());
        }

        @Override
        public void setzeFensterscheibe(int x, int y, int z, Richtung achse,
                Material art) {
            rufe.add("scheibe " + x + "/" + y + "/" + z);
        }

        @Override
        public void setzeBalkenachse(int x, int y, int z, Richtung achse, Material art) {
            rufe.add("balken " + x + "/" + y + "/" + z);
        }

        @Override
        public void setzeLeiter(int x, int y, int z, Richtung zeigt, Material art) {
            rufe.add("leiter " + x + "/" + y + "/" + z);
        }
    }

    private static Torhausaufbau hand() {
        return new Torhausaufbau(Pflasterstoffe.abWerk(), Baustoffe.abWerk());
    }

    private static Torhausplan nord() {
        return Torhausplan.inDerMauer(Stadtplan.altfurt(), Richtung.NORD);
    }

    @Test
    @DisplayName("Jeder Block des Torhauses wird gesetzt - gezaehlt, nicht geschaetzt")
    void jederBlock() {
        Torhausplan plan = nord();
        Kartengriff welt = new Kartengriff();

        Torhausaufbau.Ergebnis raus = hand().setze(plan, welt, new Feinmitschrift(),
                List.of(Material.NETHERRACK), List.of(Material.END_STONE));

        assertEquals(Torhausplan.BREITE * Torhausplan.TIEFE * Torhausplan.HOEHE,
                raus.bloecke(), "15 mal 9 mal 11 Bloecke");
        assertEquals(2 * Torhausplan.NISCHE_BREITE * Torhausplan.NISCHE_HOEHE,
                raus.nischen(), "zwei Nischen von je drei mal drei");
        assertEquals(2, raus.dioramen(), "zwei Dioramen");
    }

    @Test
    @DisplayName("Das Diorama der linken Nische gehoert zur linken Welt")
    void dioramaSeiten() {
        Torhausplan plan = nord();
        Kartengriff welt = new Kartengriff();
        Quader links = plan.nische(true);
        Quader rechts = plan.nische(false);

        hand().setze(plan, welt, new Feinmitschrift(),
                List.of(Material.NETHERRACK), List.of(Material.END_STONE));

        // DIE SEITEN DUERFEN NICHT VERTAUSCHT SEIN - sonst sieht man beim Nether den
        // Hochwald. Das Diorama liegt einen Block hinter der Scheibe.
        assertEquals(Material.NETHERRACK,
                welt.bei(links.vonX() - 2, links.vonY(), links.vonZ()),
                "links der Nether");
        assertEquals(Material.END_STONE,
                welt.bei(rechts.vonX() + 2, rechts.vonY(), rechts.vonZ()),
                "rechts das End");
        // UND DIE SCHEIBE STEHT DAVOR - sie ist der Riegel gegen die Lava.
        assertEquals(Torhausaufbau.SCHEIBE,
                welt.bei(links.vonX() - 1, links.vonY(), links.vonZ()), "Scheibe links");
        assertEquals(Torhausaufbau.SCHEIBE,
                welt.bei(rechts.vonX() + 1, rechts.vonY(), rechts.vonZ()),
                "Scheibe rechts");
        // DAHINTER MAUERWERK: Das Diorama ist nach aussen dicht.
        assertNotEquals(Material.NETHERRACK,
                welt.bei(links.vonX() - 3, links.vonY(), links.vonZ()),
                "aussen kein Diorama mehr");
        assertNotEquals(Material.AIR,
                welt.bei(links.vonX() - 3, links.vonY(), links.vonZ()),
                "und auch keine Luft");
    }

    @Test
    @DisplayName("Die Nische ist LUFT und hat Boden - sonst klagt die Torpruefung")
    void dieNischeIstBegehbar() {
        Torhausplan plan = nord();
        Kartengriff welt = new Kartengriff();

        hand().setze(plan, welt, new Feinmitschrift(), List.of(), List.of());

        for (boolean links : new boolean[] {true, false}) {
            Quader nische = plan.nische(links);
            for (int x = nische.vonX(); x <= nische.bisX(); x++) {
                for (int y = nische.vonY(); y <= nische.bisY(); y++) {
                    for (int z = nische.vonZ(); z <= nische.bisZ(); z++) {
                        assertEquals(Material.AIR, welt.bei(x, y, z),
                                "die Nische muss frei sein: " + x + "/" + y + "/" + z);
                    }
                }
                assertNotEquals(Material.AIR,
                        welt.bei(x, Stadtplan.BODEN_Y, nische.vonZ()),
                        "und darunter muss Boden sein");
            }
        }
        // UND DER DURCHGANG IST BEGEHBAR, von der Stadtseite her.
        Quader b = plan.bereich();
        assertEquals(Material.AIR, welt.bei(Stadtplan.MITTE_X, Stadtplan.BODEN_Y + 1,
                b.bisZ()), "der Durchgang ist offen");
        assertNotEquals(Material.AIR, welt.bei(Stadtplan.MITTE_X, Stadtplan.BODEN_Y,
                b.bisZ()), "und hat Boden");
    }

    @Test
    @DisplayName("Ohne Diorama steht Mauerwerk hinter dem Portal - kein Loch")
    void ohneDiorama() {
        Torhausplan plan = nord();
        Kartengriff welt = new Kartengriff();
        Quader links = plan.nische(true);

        Torhausaufbau.Ergebnis raus =
                hand().setze(plan, welt, new Feinmitschrift(), List.of(), List.of());

        assertEquals(0, raus.dioramen(), "keine Dioramen");
        assertEquals(Torhausaufbau.HINTERGRUND,
                welt.bei(links.vonX() - 2, links.vonY(), links.vonZ()),
                "dahinter Mauerwerk");
        assertNotEquals(Material.AIR,
                welt.bei(links.vonX() - 2, links.vonY(), links.vonZ()),
                "und kein Loch");
    }

    @Test
    @DisplayName("Das Mauerwerk ist dieselbe Mischung wie die Stadtmauer")
    void dasselbeMauerwerk() {
        Torhausplan plan = nord();
        Kartengriff welt = new Kartengriff();
        Pflasterstoffe stoffe = Pflasterstoffe.abWerk();

        hand().setze(plan, welt, new Feinmitschrift(), List.of(), List.of());

        // GEMISCHT und aus DERSELBEN Tafel: Das Torhaus steht in der Mauer, und zwei
        // verschiedene Steinsorten nebeneinander sieht man.
        Quader b = plan.bereich();
        Set<Material> gesehen = new HashSet<>();
        for (int x = b.vonX(); x < b.vonX() + Torhausplan.TURM_BREITE; x++) {
            for (int y = Stadtplan.BODEN_Y + 4; y <= Stadtplan.BODEN_Y + 6; y++) {
                gesehen.add(welt.bei(x, y, b.bisZ() - 4));
            }
        }
        assertTrue(gesehen.size() >= 2, "das Mauerwerk ist gemischt: " + gesehen);
        for (Material block : gesehen) {
            assertTrue(stoffe.mischung(Stadtplan.Lage.MAUER).contains(block)
                            || block == Torhausaufbau.PFEILER,
                    block + " gehoert nicht zur Mauermischung");
        }
    }

    @Test
    @DisplayName("Jede Dachtreppe geht ueber die Naht und zeigt bergauf")
    void dachtreppen() {
        Torhausplan plan = nord();
        Feinmitschrift fein = new Feinmitschrift();

        hand().setze(plan, new Kartengriff(), fein, List.of(), List.of());

        List<String> daecher = fein.rufe.stream()
                .filter(zeile -> zeile.startsWith("dach ")).toList();
        // GEZAEHLT gegen den Plan: so viele Treppen, wie der Plan DACH nennt.
        Quader b = plan.bereich();
        int ausDemPlan = 0;
        for (int x = b.vonX(); x <= b.bisX(); x++) {
            for (int y = b.vonY(); y <= b.bisY(); y++) {
                for (int z = b.vonZ(); z <= b.bisZ(); z++) {
                    if (plan.dachzeigt(x, y, z) != null) {
                        ausDemPlan++;
                    }
                }
            }
        }
        assertEquals(ausDemPlan, daecher.size(), "jede Treppe geht ueber die Naht");
        assertTrue(ausDemPlan > 20, "und es sind viele: " + ausDemPlan);
        // BEIDE RICHTUNGEN - ein Dach aus lauter gleich gerichteten Treppen ist von
        // einer Seite falsch.
        assertTrue(daecher.stream().anyMatch(zeile -> zeile.contains(" ost ")),
                daecher.toString());
        assertTrue(daecher.stream().anyMatch(zeile -> zeile.contains(" west ")),
                daecher.toString());
    }

    @Test
    @DisplayName("Ob das Torhaus steht, haengt an EINEM Block")
    void stehtSchon() {
        Torhausplan plan = nord();
        Kartengriff welt = new Kartengriff();

        assertFalse(hand().stehtSchon(plan, welt), "eine leere Welt hat kein Torhaus");
        hand().setze(plan, welt, new Feinmitschrift(), List.of(), List.of());
        assertTrue(hand().stehtSchon(plan, welt), "danach steht es");

        Quader b = plan.bereich();
        welt.setze(b.vonX(), Stadtplan.BODEN_Y + 1, b.vonZ(), Material.AIR);
        assertFalse(hand().stehtSchon(plan, welt), "ohne den Block wird neu gesetzt");
    }

    @Test
    @DisplayName("Ohne Plan, Welt, Naht oder Diorama wirft das Setzen")
    void ohneAlles() {
        Torhausplan plan = nord();
        Kartengriff welt = new Kartengriff();
        Feinmitschrift fein = new Feinmitschrift();

        assertThrows(IllegalArgumentException.class,
                () -> hand().setze(null, welt, fein, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> hand().setze(plan, null, fein, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> hand().setze(plan, welt, null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> hand().setze(plan, welt, fein, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> hand().setze(plan, welt, fein, List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new Torhausaufbau(null, Baustoffe.abWerk()));
        assertThrows(IllegalArgumentException.class,
                () -> hand().block(null, 0, 0, List.of()));
    }

    @Test
    @DisplayName("Die Liste liest Seiten und Dioramen - und klagt ueber Unsinn")
    void dieListe() {
        YamlConfiguration datei = new YamlConfiguration();
        datei.set(Torhausliste.PFAD + ".nord.links", "hochwald");
        datei.set(Torhausliste.PFAD + ".nord.rechts", "kornmark");
        datei.set(Torhausliste.PFAD + ".oben.links", "nether");
        datei.set(Torhausliste.PFAD_DIORAMEN + ".hochwald",
                List.of("spruce_log", "podzol", "hansemoos"));
        datei.set(Torhausliste.PFAD_DIORAMEN + ".kornmark", List.of("hay_block", "air"));

        List<String> klagen = new ArrayList<>();
        Torhausliste liste = Torhausliste.ausKonfiguration(datei, klagen);

        assertEquals("hochwald", liste.tor(Richtung.NORD).links());
        assertEquals("kornmark", liste.tor(Richtung.NORD).rechts());
        assertEquals(null, liste.tor(Richtung.SUED), "ohne Eintrag kein Torhaus");
        assertFalse(liste.leer(), "eines steht da");

        assertEquals(List.of(Material.SPRUCE_LOG, Material.PODZOL),
                liste.diorama("hochwald"), "der unbekannte Block fehlt");
        assertEquals(List.of(Material.HAY_BLOCK), liste.diorama("kornmark"),
                "und die Luft auch");
        assertEquals(List.of(), liste.diorama("nether"), "ohne Eintrag kein Diorama");
        assertEquals(List.of(), liste.diorama(null), "und ohne Welt auch nicht");

        String alle = String.join(" | ", klagen);
        assertEquals(3, klagen.size(), alle);
        assertTrue(alle.contains("oben"), alle);
        assertTrue(alle.contains("hansemoos"), alle);
        assertTrue(alle.contains("air"), alle);

        // OHNE ABSCHNITT gibt es kein Torhaus - und keine Klage.
        List<String> stille = new ArrayList<>();
        assertTrue(Torhausliste.ausKonfiguration(new YamlConfiguration(), stille).leer());
        assertTrue(Torhausliste.ausKonfiguration(null, stille).leer());
        assertEquals(List.of(), stille, "ohne Abschnitt keine Klage");
    }

    @Test
    @DisplayName("Die Seitenwoerter sind die der Richtungen")
    void dieSeitenwoerter() {
        for (Richtung richtung : Richtung.values()) {
            assertEquals(richtung, Torhausliste.seiteVon(richtung.wort()),
                    richtung.wort());
            assertEquals(richtung, Torhausliste.seiteVon(
                    richtung.wort().toUpperCase(java.util.Locale.ROOT)),
                    "auch gross geschrieben");
        }
        assertEquals(null, Torhausliste.seiteVon("oben"));
        assertEquals(null, Torhausliste.seiteVon(null));
    }
}
