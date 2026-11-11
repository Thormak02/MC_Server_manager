package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockort;
import de.freimarken.kern.Truhenverzeichnis;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Einrichtung der Stadt - wer nichts bauen darf, sieht auch in keine Kiste.
 *
 * <p>Der Abenteuermodus verhindert das Bauen, nicht das OEFFNEN. Diese Probe haelt die
 * vier Antworten, aus denen die Wache besteht: die WELT (nur die Ladenwelt), die Freie
 * Truhe (dort entscheidet die Truhenwache), die Kistenshops (dort die Shopwache) und
 * alles andere (zu).
 *
 * <p><b>Zwei Ebenen, und beide werden gebraucht:</b> {@code gehoertZurStadt} ist die
 * Entscheidung und laeuft ohne Server; am echten {@link InventoryOpenEvent} haengt, was
 * nur Bukkit beantwortet - das Wesen ohne Spieler, das Teamrecht und die Absage samt
 * Handbalken. Dieselbe Teilung wie bei der Truhenwache.
 */
@DisplayName("Die Einrichtung der Stadt")
class EinrichtungswacheTest {

    private static final String WELT = "freimarken:altfurt";
    private static final String ANDERE_WELT = "freimarken:marktstadt";

    /** Die Freie Truhe steht in der Halle - dieselbe Stelle wie im Probeserver. */
    private static final int TRUHE_X = 72;
    private static final int TRUHE_Y = -60;
    private static final int TRUHE_Z = 20;

    /** Eine gewoehnliche Kiste der Stadt, ein Stueck daneben. */
    private static final int STADT_X = 75;

    /** Eine Kiste, an der ein Kistenshop haengt. */
    private static final int SHOP_X = 78;

    private static final UUID ANNA =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private World welt;
    private Spielerprobe anna;
    private Einrichtungswache wache;

    @BeforeAll
    static void serverSetzen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void bauen() {
        Serverprobe.keineWeltenMehr();
        Serverprobe.niemandMehrDa();
        welt = Serverprobe.welt(WELT);
        anna = new Spielerprobe("Anna", ANNA);
        wache = wache(WELT);
    }

    // --- Die Entscheidung, ohne Server ----------------------------------------

    @Test
    @DisplayName("Eine gewoehnliche Kiste in der Ladenwelt gehoert zur Stadt")
    void gewoehnlicheKisteGehoertDerStadt() {
        // Der Fall, den es vor dem 11.11.2026 gar nicht gab: Jedes Fass in der Halle,
        // jeder Ofen im Bundesratshaus stand jedem offen, weil der Abenteuermodus nur
        // das Bauen verhindert.
        assertTrue(wache.gehoertZurStadt(WELT, STADT_X, TRUHE_Y, TRUHE_Z));
    }

    @Test
    @DisplayName("Eine Kiste in einer ANDEREN Welt geht diese Wache nichts an")
    void andereWeltGehtSieNichtsAn() {
        // Die Marktstadt deckt der Besitzschutz, die Wildnis deckt niemand - dort ist
        // eine Kiste die Kiste dessen, der sie hingestellt hat. Eine Wache, die
        // ueberall sperrt, naehme jedem Spieler seine eigene Truhe.
        assertFalse(wache.gehoertZurStadt(ANDERE_WELT, STADT_X, TRUHE_Y, TRUHE_Z));
    }

    @Test
    @DisplayName("Die Freie Truhe gehoert NICHT zur Stadt - dort entscheidet die Truhenwache")
    void freieTruheIstAusgenommen() {
        // Sie ist der eine Behaelter, der jedem offen steht. Waere sie hier mitgesperrt,
        // waere der ganze Teil D tot - und zwar lautlos, denn die Truhenwache wuerde
        // nie gefragt.
        assertFalse(wache.gehoertZurStadt(WELT, TRUHE_X, TRUHE_Y, TRUHE_Z));
    }

    @Test
    @DisplayName("Eine Shopkiste gehoert NICHT zur Stadt - dort entscheidet die Shopwache")
    void shopkisteIstAusgenommen() {
        // Sonst koennte der Besitzer seinen eigenen Laden nicht mehr fuellen: Der Klick
        // auf die Kiste ist dann abgesagt, bevor Kistenshop.offenFuer gefragt wird.
        assertFalse(wache.gehoertZurStadt(WELT, SHOP_X, TRUHE_Y, TRUHE_Z));
    }

    @Test
    @DisplayName("Ohne Welt, Truhen, Shopfrage oder Sprache gibt es keine Wache")
    void ohneTeileKeineWache() {
        Truhenverzeichnis truhen = Truhenverzeichnis.ausStellen(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> new Einrichtungswache("", truhen, this::shopAn, sprache()));
        assertThrows(IllegalArgumentException.class,
                () -> new Einrichtungswache(WELT, null, this::shopAn, sprache()));
        assertThrows(IllegalArgumentException.class,
                () -> new Einrichtungswache(WELT, truhen, null, sprache()));
        assertThrows(IllegalArgumentException.class,
                () -> new Einrichtungswache(WELT, truhen, this::shopAn, null));
    }

    // --- Am echten Ereignis ---------------------------------------------------

    @Test
    @DisplayName("Eine Kiste der Stadt bleibt ZU - und sagt es im Handbalken")
    void stadtkisteBleibtZu() {
        InventoryOpenEvent ereignis = oeffnet(anna.spieler(), STADT_X);

        wache.beimOeffnen(ereignis);

        assertTrue(ereignis.isCancelled(), "die Kiste der Stadt muss zubleiben");
        assertTrue(anna.handbalkenBild().toString().contains("gehoert zur Stadt"),
                anna.handbalkenBild().toString());
        assertEquals(List.of(), anna.chatBild(),
                "und zwar im HANDBALKEN - eine Absage, die man wegklicken muss, steht"
                        + " bei jedem zweiten Klick im Chat");
    }

    @Test
    @DisplayName("Die Freie Truhe geht am Ereignis auf, ohne ein Wort")
    void freieTruheGehtAuf() {
        InventoryOpenEvent ereignis = oeffnet(anna.spieler(), TRUHE_X);

        wache.beimOeffnen(ereignis);

        assertFalse(ereignis.isCancelled());
        assertEquals(List.of(), anna.handbalkenBild(), "und ohne Handbalken");
    }

    @Test
    @DisplayName("Das Teamrecht oeffnet alles - wer einrichtet, muss hineinsehen")
    void teamrechtOeffnetAlles() {
        // Dasselbe Recht wie beim Bauen und kein eigenes daneben: Beim ersten
        // Umsortieren haette ein Baumeister sonst Baurecht ohne Kistenrecht.
        Spielerprobe team = new Spielerprobe("Team",
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                Schutzwache.UEBERALL);
        InventoryOpenEvent ereignis = oeffnet(team.spieler(), STADT_X);

        wache.beimOeffnen(ereignis);

        assertFalse(ereignis.isCancelled());
        assertEquals(List.of(), team.handbalkenBild());
    }

    @Test
    @DisplayName("Ein Inventar OHNE Kiste - der eigene Rucksack - wird nicht gesperrt")
    void inventarOhneKisteBleibtOffen() {
        // Shopware.bloeckeVon gibt bei einem Inventar ohne Kiste eine LEERE Liste: der
        // Rucksack, eine Werkbank, ein Braustand, das Endlager. Ohne den Riegel darauf
        // waere in Altfurt jedes Inventar zu - auch das eigene -, und ein Spieler koennte
        // dort nicht einmal seinen Rucksack oeffnen.
        InventoryOpenEvent ereignis = new InventoryOpenEvent(
                Kistenprobe.sichtAuf(anna.spieler(), Kistenprobe.ohneBlock()));

        wache.beimOeffnen(ereignis);

        assertFalse(ereignis.isCancelled(), "ein Inventar ohne Kiste gehoert niemandem"
                + " und damit nicht der Stadt");
        assertEquals(List.of(), anna.handbalkenBild());
    }

    @Test
    @DisplayName("Ohne Spieler wird nichts gesperrt - ein Wesen hat keine Rechte")
    void ohneSpielerKeineSperre() {
        // getPlayer gibt eine HumanEntity: Ein Inventar kann auch fuer ein anderes Wesen
        // offen sein. Ohne Spieler gibt es keine Rechtefrage, also keine Entscheidung -
        // dieselbe Pruefung und derselbe Grund wie in Shopwache.beimOeffnen.
        HumanEntity wesen = Kistenprobe.keinSpieler();
        InventoryOpenEvent ereignis = new InventoryOpenEvent(
                Kistenprobe.sichtAuf(wesen, kiste(STADT_X)));

        wache.beimOeffnen(ereignis);

        assertFalse(ereignis.isCancelled(), "eine Absage an niemanden waere keine");
    }

    // --- Werkzeug -------------------------------------------------------------

    private Einrichtungswache wache(String weltschluessel) {
        return new Einrichtungswache(weltschluessel,
                Truhenverzeichnis.ausStellen(
                        List.of(new Blockort(WELT, TRUHE_X, TRUHE_Y, TRUHE_Z))),
                this::shopAn, sprache());
    }

    /**
     * Die Shopfrage der Probe: an SHOP_X haengt ein Laden, sonst nirgends.
     *
     * <p>Eine eigene Antwort und nicht das echte Shopbuch: Jenes braucht Ablage,
     * Shopware und Zugriff, und alle drei haetten mit dieser Entscheidung nichts zu tun.
     */
    private boolean shopAn(String welt, int x, int y, int z) {
        return WELT.equals(welt) && x == SHOP_X;
    }

    private static Sprache sprache() {
        return Sprachprobe.spracheAusDemJar();
    }

    /** Ein Oeffnen dieser Kiste durch dieses Wesen. */
    private InventoryOpenEvent oeffnet(HumanEntity wer, int x) {
        return new InventoryOpenEvent(Kistenprobe.sichtAuf(wer, kiste(x)));
    }

    private Inventory kiste(int x) {
        return Kistenprobe.kiste(welt, x, TRUHE_Y, TRUHE_Z, new ItemStack[27],
                new ArrayList<>());
    }
}
