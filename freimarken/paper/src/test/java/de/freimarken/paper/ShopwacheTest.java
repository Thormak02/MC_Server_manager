package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.SpeicherShops;
import de.freimarken.kern.Warenprobe;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die HAND am Deckel einer fremden Shopkiste - der Zweig wird wirklich durchlaufen.
 *
 * <p><b>Warum hier ein echtes Ereignis gebaut wird und keine Tafel genuegt.</b>
 * {@link ShopwacheTafel} prueft TEXT: dass {@code setCancelled(true)} dasteht, dass
 * {@code shopsAn} gefragt wird, dass {@code MONITOR} fehlt. Keine dieser Zeilen sagt, ob
 * der Deckel am Ende zubleibt. Genau das ist hier die Frage - und es ist die Stelle, an
 * der ein Fehler Ware kostet, die niemand zurueckbekommt.
 *
 * <p><b>Es geht ohne Server, und das ist gemessen, nicht gehofft.</b>
 * {@code InventoryView} ist eine Schnittstelle, und der Konstruktor von
 * {@link InventoryOpenEvent} nimmt nur sie - er fragt sie nicht einmal. {@link Shopwache}
 * selbst laedt ohne Server (ihre statischen Felder sind eine Dauer, eine Liste von
 * Richtungen und zwei Zeichenketten), und {@link Shopware} ebenso - das tut
 * {@code ShopwareTest} schon heute. Gebraucht wird {@code Serverprobe.einmalSetzen}
 * trotzdem: {@code Spielername.von} fragt {@code Bukkit.getOfflinePlayer} nach dem Namen,
 * den die Absage nennt.
 *
 * <p><b>Jeder Stellvertreter beantwortet NUR das Gefragte</b> und klagt sonst mit dem
 * Methodennamen - derselbe Grundsatz wie in {@link Serverprobe}. Ein stilles
 * {@code null} liesse eine Probe durch den falschen Zweig laufen und gruen melden.
 *
 * <p><b>Die Kistenhaelfte traegt ZWEI Schnittstellen</b>, {@code BlockState} und
 * {@code InventoryHolder}, und das ist kein Beiwerk: {@code Inventory.getHolder} und
 * {@code DoubleChest.getLeftSide} geben beide einen {@code InventoryHolder} zurueck,
 * {@code Shopware.bloeckeVon} fragt danach aber {@code instanceof BlockState}. In echtem
 * Bukkit ist eine Kiste beides. Ein Stellvertreter mit nur einer der beiden wirft beim
 * Umwandeln, und der Fehler sieht nach einem Fehler im Zweig aus.
 */
@DisplayName("Shopwache: die Hand am Kistendeckel")
class ShopwacheTest {

    private static final String WELT = "freimarken:hochwald";

    private static final UUID ANNA = UUID.fromString("0000a44a-0000-0000-0000-00000000a1");
    private static final UUID BERT = UUID.fromString("0000b55b-0000-0000-0000-00000000b2");

    /** Die Stelle der Shopkiste - das Schild haengt einen Block daneben. */
    private static final int X = 61;
    private static final int Y = 71;
    private static final int Z = 6;

    private World welt;
    private Shopablage ablage;
    private Shopwache wache;
    private Spielerprobe anna;
    private Spielerprobe bert;

    @BeforeAll
    static void serverSetzen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void bauen() {
        Serverprobe.keineWeltenMehr();
        Serverprobe.niemandMehrDa();
        welt = Serverprobe.welt(WELT);
        // Die Absage nennt den Besitzer, und der Name kommt aus Bukkit.getOfflinePlayer.
        Serverprobe.nenne(ANNA, "Anna");
        Serverprobe.nenne(BERT, "Bert");
        ablage = new SpeicherShops();
        anna = new Spielerprobe("Anna", ANNA);
        bert = new Spielerprobe("Bert", BERT);
        wache = wacheMit(ablage);
    }

    // --- Die fremde Kiste bleibt zu --------------------------------------------

    @Test
    @DisplayName("Die Shopkiste eines anderen geht nicht auf - und sagt, wessen sie ist")
    void fremdeShopkisteBleibtZu() {
        // DIE LUECKE, die dieser Zweig schliesst: Schutzwache.darfHandeln gibt true,
        // sobald die Welt nicht die Parzellenwelt ist - in hochwald war jede Shopkiste
        // von jedem zu oeffnen. Wer die Kiste oeffnen kann, braucht nicht zu kaufen.
        ablage.schreibe(shopVon(ANNA));

        InventoryOpenEvent ereignis = oeffnet(bert, einzelkiste(X, Y, Z));
        wache.beimOeffnen(ereignis);

        assertTrue(ereignis.isCancelled(), "der Deckel MUSS zubleiben");
        assertEquals(List.of("&cDiese Kiste ist der Shop von &fAnna"),
                bert.handbalkenBild(),
                "und er soll lesen, wessen sie ist - sonst haelt er den Server fuer"
                        + " kaputt");
    }

    @Test
    @DisplayName("Die eigene Shopkiste geht auf, und zwar schweigend")
    void eigeneShopkisteGehtAuf() {
        ablage.schreibe(shopVon(ANNA));

        InventoryOpenEvent ereignis = oeffnet(anna, einzelkiste(X, Y, Z));
        wache.beimOeffnen(ereignis);

        assertFalse(ereignis.isCancelled(), "ihr eigener Shop - sie fuellt ihn nach");
        assertEquals(List.of(), anna.handbalkenBild(),
                "und KEIN Handbalken: Wer seine eigene Kiste aufmacht, bekommt keine"
                        + " Meldung darueber, dass sie ihm gehoert");
    }

    @Test
    @DisplayName("Ein FREIGEGEBENER macht die fremde Shopkiste auf")
    void freigegebenerOeffnetDieFremdeKiste() {
        // Der Zweck der Freigabe: Drei Freunde fuehren einen Laden gemeinsam, und zwei
        // davon sollen nachfuellen koennen. Welchen WEG dieser Zweig an den Kern
        // weitergibt, sieht man nur hier - der Kern kennt alle drei Antworten, und
        // zwei von ihnen wuerden Bert hier abweisen.
        ablage.schreibe(shopVon(ANNA).mitFreigaben(Set.of(BERT)));

        InventoryOpenEvent ereignis = oeffnet(bert, einzelkiste(X, Y, Z));
        wache.beimOeffnen(ereignis);

        assertFalse(ereignis.isCancelled(),
                "Anna hat ihm den Schluessel gegeben - der Deckel geht auf");
        assertEquals(List.of(), bert.handbalkenBild(),
                "und schweigend: Eine Meldung waere hier die Absage, die gerade NICHT"
                        + " stattfindet");
    }

    @Test
    @DisplayName("Eine gewoehnliche Kiste geht auf - es haengt kein Shop daran")
    void gewoehnlicheKisteGehtAuf() {
        // Die Ablage ist leer. Haette der Zweig hier eine Absage, waere jede Kiste der
        // Welt zu - und das faellt beim Spielen in der ersten Minute auf. Die Probe
        // steht trotzdem hier: Sie haelt, dass die Absage an den SHOPS haengt und nicht
        // daran, dass ueberhaupt ein Inventar aufgeht.
        InventoryOpenEvent ereignis = oeffnet(bert, einzelkiste(X, Y, Z));
        wache.beimOeffnen(ereignis);

        assertFalse(ereignis.isCancelled());
    }

    @Test
    @DisplayName("Ein Inventar ohne Block geht auf - Rucksack, Pferd, Trichterwagen")
    void inventarOhneBlockGehtAuf() {
        // Shopware.bloeckeVon gibt fuer diese drei eine LEERE Liste: Sie haben keinen
        // Block und koennen keine Shopkiste sein. Wer die leere Liste als "nichts
        // gefunden, also absagen" liest, sperrt den Blick in den eigenen Rucksack.
        ablage.schreibe(shopVon(ANNA));

        InventoryOpenEvent ereignis = oeffnet(bert, inventarMit(null));
        wache.beimOeffnen(ereignis);

        assertFalse(ereignis.isCancelled());
        assertEquals(List.of(), bert.handbalkenBild());
    }

    // --- Die drei Rechte -------------------------------------------------------

    @Test
    @DisplayName("Das Teamrecht oeffnet die fremde Kiste")
    void teamrechtOeffnet() {
        // Ohne das kaeme niemand mehr an den Shop eines Spielers, der nicht mehr spielt -
        // und ein verlassener Shop stuende fuer immer in der Welt.
        ablage.schreibe(shopVon(ANNA));
        Spielerprobe team = new Spielerprobe("Chef", BERT, Schutzwache.UEBERALL);

        InventoryOpenEvent ereignis = oeffnet(team, einzelkiste(X, Y, Z));
        wache.beimOeffnen(ereignis);

        assertFalse(ereignis.isCancelled());
    }

    @Test
    @DisplayName("Ein Bundshop braucht das Bundrecht - und NICHT den Besitz")
    void bundshopBrauchtDasBundrecht() {
        // Ein Bundshop hat besitzer == null, gehoert() gibt dort fuer JEDEN false. Wer
        // den Zutritt am Besitz allein prueft, schliesst jeden Bundshop fuer alle zu -
        // auch fuer das Team, das ihn nachfuellen muss.
        ablage.schreibe(shopVon(null));
        Spielerprobe bund = new Spielerprobe("Bund", BERT, Shopwache.RECHT_BUNDSHOP);

        InventoryOpenEvent ohne = oeffnet(bert, einzelkiste(X, Y, Z));
        wache.beimOeffnen(ohne);
        InventoryOpenEvent mit = oeffnet(bund, einzelkiste(X, Y, Z));
        wache.beimOeffnen(mit);

        assertTrue(ohne.isCancelled(), "ohne Bundrecht zu");
        assertEquals(List.of("&cDas ist ein Shop des Bundes"), bert.handbalkenBild(),
                "und ein eigener Satz, denn ein Bundshop hat keinen Menschen zu nennen");
        assertFalse(mit.isCancelled(), "mit Bundrecht offen");
    }

    @Test
    @DisplayName("Das Bundrecht oeffnet keinen Spielershop")
    void bundrechtOeffnetKeinenSpielershop() {
        // Sonst waere freimarken.shop.bund ein Generalschluessel. Wer Preise des Servers
        // festlegen darf, darf damit nicht in jede Spielerkiste greifen.
        ablage.schreibe(shopVon(ANNA));
        Spielerprobe bund = new Spielerprobe("Bund", BERT, Shopwache.RECHT_BUNDSHOP);

        InventoryOpenEvent ereignis = oeffnet(bund, einzelkiste(X, Y, Z));
        wache.beimOeffnen(ereignis);

        assertTrue(ereignis.isCancelled());
    }

    // --- Die Doppelkiste -------------------------------------------------------

    @Test
    @DisplayName("Bei einer Doppelkiste wird auch die ZWEITE Haelfte gefragt")
    void doppelkisteBeideHaelften() {
        // DIE FALLE: Die beiden Haelften teilen EIN Inventar. Wer seine eigene Haelfte
        // aufmacht, greift in dieselbe Ware - und der Shop haengt nur an einer von
        // beiden. Ein Zweig, der bloss die erste Haelfte fragt, laesst die zweite offen.
        //
        // Hier haengt Annas Shop an der ZWEITEN Haelfte, und Bert oeffnet die Kiste.
        ablage.schreibe(shopVon(ANNA, X + 1));

        InventoryOpenEvent ereignis = oeffnet(bert,
                inventarMit(doppelkiste(kistenhalter(X, Y, Z),
                        kistenhalter(X + 1, Y, Z))));
        wache.beimOeffnen(ereignis);

        assertTrue(ereignis.isCancelled(),
                "die zweite Haelfte traegt den Shop, und das Inventar ist dasselbe");
    }

    // --- Die stumme Ablage -----------------------------------------------------

    @Test
    @DisplayName("Schweigt die Ablage, bleibt die Kiste zu - ohne einen Namen zu nennen")
    void stummeAblageBleibtZu() {
        // DER EINE FALL, in dem die zwei Richtungen der Ablage auseinandergehen:
        // Shopbuch.istShopkiste gibt bei einer Stoerung true ("schuetze sie"),
        // Shopbuch.anDerKiste eine leere Liste ("erfinde keinen Besitzer"). Beides ist
        // richtig, und zusammen heisst es: zu, aber ohne Namen.
        //
        // Nur die zweite Frage zu stellen ist die naheliegende Vereinfachung - und sie
        // schliesst bei einer schweigenden Datenbank jede Shopkiste der Welt AUF, denn
        // eine leere Liste heisst "niemand fremd". Diese Probe ist der Grund, warum
        // shopsAn zwei Fragen stellt.
        wache = wacheMit(new StummeAblage());

        InventoryOpenEvent ereignis = oeffnet(bert, einzelkiste(X, Y, Z));
        wache.beimOeffnen(ereignis);

        assertTrue(ereignis.isCancelled(), "bei Zweifel bleibt die Kiste zu");
        assertEquals(List.of("&cDiese Kiste ist geschuetzt - versuch es noch einmal"),
                bert.handbalkenBild(),
                "und ein eigener Satz: \"der Shop von -\" sieht aus wie ein"
                        + " Programmfehler");
    }

    // --- Der Trichter am ZIEL [09.11.2026] ------------------------------------

    @Test
    @DisplayName("Kein Trichter zieht aus einer Shopkiste - das bleibt ohne Bedingung")
    void trichterZiehtNichtAusDerShopkiste() {
        // Die aeltere Haelfte der Regel, und sie gilt auch dem BESITZER: Wer unter seine
        // eigene Shopkiste einen Trichter setzt, hat einen Shop gebaut, der sich selbst
        // leert - und der naechste Kunde kauft Ware, die schon weg ist.
        //
        // WAS DIESE PROBE NICHT HAELT, und das ist gemessen: Werden Quelle und Ziel
        // im Zweig VERTAUSCHT, bleibt sie gruen. Solange keine Warenprobe aufloesbar
        // ist, sagt der Zweig in jeder Lage mit einer Shopkiste ab - gleich auf
        // welcher Seite sie steht, und der Dreher ist verhaltensgleich. Erst eine
        // PASSENDE Ware wuerde die Seiten unterscheiden, und isSimilar gibt es ohne
        // Server nicht. Den Dreher haelt darum allein die Quelltexttafel
        // (ShopwacheTafel: "Der Trichter sperrt die QUELLE immer") - und der
        // Spieltest.
        ablage.schreibe(shopVon(ANNA));

        InventoryMoveItemEvent ereignis = new InventoryMoveItemEvent(
                einzelkiste(X, Y, Z), new Wesenprobe.Probestapel(Material.STONE),
                inventarMit(null), true);
        wache.beimGegenstandWandern(ereignis);

        assertTrue(ereignis.isCancelled());
    }

    @Test
    @DisplayName("In eine Shopkiste mit UNLESBARER Warenprobe kommt nichts hinein")
    void trichterLegtNichtsInUnlesbareWare() {
        // Die Warenprobe dieser Proben ist absichtlich kein gueltiges BASE64, also gibt
        // vorbildVon null - und was nicht zu vergleichen ist, kommt nicht hinein. Das ist
        // hier die sichere Richtung: Ein stockender Trichter ist zu beheben, eine
        // verstopfte Kiste muss jemand ausraeumen.
        //
        // Dass PASSENDE Ware hineinkommt, ist hier NICHT zu pruefen: isSimilar ist eine
        // echte ItemStack-Methode und ohne Server nicht zu haben (Probestapel
        // ueberschreibt nur getType). Das steht als Spieltestpunkt in TODO.md, und die
        // Verdrahtung haelt ShopwacheTafel.
        ablage.schreibe(shopVon(ANNA));

        InventoryMoveItemEvent ereignis = new InventoryMoveItemEvent(
                inventarMit(null), new Wesenprobe.Probestapel(Material.DIRT),
                einzelkiste(X, Y, Z), true);
        wache.beimGegenstandWandern(ereignis);

        assertTrue(ereignis.isCancelled(),
                "unbekannte Ware heisst NEIN - sonst war der Muell wieder drin");
    }

    @Test
    @DisplayName("In eine gewoehnliche Kiste legt der Trichter weiter alles")
    void trichterLegtInGewoehnlicheKiste() {
        // Die Ablage ist leer, es haengt kein Shop daran. Ohne diese Probe waere nicht
        // gehalten, dass die neue Zielpruefung nur SHOPkisten betrifft - und eine
        // Zielpruefung fuer jede Kiste der Welt waere das Ende jeder Sortieranlage.
        InventoryMoveItemEvent ereignis = new InventoryMoveItemEvent(
                inventarMit(null), new Wesenprobe.Probestapel(Material.DIRT),
                einzelkiste(X, Y, Z), true);
        wache.beimGegenstandWandern(ereignis);

        assertFalse(ereignis.isCancelled());
    }

    @Test
    @DisplayName("Schweigt die Ablage, kommt auch per Trichter nichts hinein")
    void stummeAblageNimmtNichtsAn() {
        // Dasselbe Auseinandergehen wie beim Oeffnen: istShopkiste sagt "schuetze sie",
        // anDerKiste nennt niemanden - und ohne Shop ist die Ware unbekannt.
        wache = wacheMit(new StummeAblage());

        InventoryMoveItemEvent ereignis = new InventoryMoveItemEvent(
                inventarMit(null), new Wesenprobe.Probestapel(Material.DIRT),
                einzelkiste(X, Y, Z), true);
        wache.beimGegenstandWandern(ereignis);

        assertTrue(ereignis.isCancelled());
    }

    // --- Kein Spieler ----------------------------------------------------------

    @Test
    @DisplayName("Ohne Spieler wird durchgelassen - ohne Rechte gibt es keine"
            + " Entscheidung")
    void ohneSpielerDurchgelassen() {
        // getPlayer gibt eine HumanEntity: Ein Inventar kann auch fuer ein anderes Wesen
        // offen sein. Ohne Spieler gibt es kein hasPermission und keine UUID, also keine
        // Entscheidung - dieselbe Pruefung und derselbe Grund wie in
        // Messwache.beimOeffnen. Wuerde hier abgesagt, waere es eine Absage an niemanden.
        ablage.schreibe(shopVon(ANNA));

        InventoryOpenEvent ereignis = new InventoryOpenEvent(
                sichtAuf(keinSpieler(), einzelkiste(X, Y, Z)));
        wache.beimOeffnen(ereignis);

        assertFalse(ereignis.isCancelled());
    }

    // --- Werkzeug -------------------------------------------------------------

    private Shopwache wacheMit(Shopablage welche) {
        // waren darf null sein: Der Oeffnen-Zweig geht ueber Shopware.bloeckeVon, und das
        // ist STATISCH. Was der gepruefte Zweig nicht anfasst, braucht keinen
        // Stellvertreter - dasselbe Muster wie in MesswacheTest.
        return new Shopwache(
                new Shopbuch(welche, null, null,
                        new Wirtschaftsstand(Wirtschaftswerte.VORGABE),
                        Logger.getAnonymousLogger()), null,
                Sprachprobe.spracheAusDemJar(), tat -> tat.run(),
                Logger.getAnonymousLogger());
    }

    private static Kistenshop shopVon(UUID besitzer) {
        return shopVon(besitzer, X);
    }

    /** Ein Shop an der Kiste bei {@code kisteX} - das Schild haengt darueber. */
    private static Kistenshop shopVon(UUID besitzer, int kisteX) {
        return new Kistenshop(WELT, kisteX, Y + 1, Z, kisteX, Y, Z, besitzer, 16, 100L,
                80L, new Warenprobe("STONE", "inhalt-von-stein"), Set.of());
    }

    private InventoryOpenEvent oeffnet(Spielerprobe wer, Inventory was) {
        return new InventoryOpenEvent(sichtAuf(wer.spieler(), was));
    }

    /** Das Inventar einer einzelnen Kiste an dieser Stelle. */
    private Inventory einzelkiste(int x, int y, int z) {
        return inventarMit(kistenhalter(x, y, z));
    }

    /**
     * Eine Kistenhaelfte: {@code BlockState} UND {@code InventoryHolder}.
     *
     * <p>Der Block kommt aus {@link Serverprobe} - das ist die eine Wahrheit darueber,
     * was ein Block beantwortet. Die Huelle darum traegt die zweite Schnittstelle, die
     * echtes Bukkit mitbringt und die {@code Serverprobe.zustand} nicht hat: Ohne sie
     * wirft {@code getHolder} beim Umwandeln auf {@code InventoryHolder}.
     */
    private InventoryHolder kistenhalter(int x, int y, int z) {
        Block block = Serverprobe.zustand(welt, null, x, y, z).getBlock();
        return (InventoryHolder) Proxy.newProxyInstance(
                ShopwacheTest.class.getClassLoader(),
                new Class<?>[] {BlockState.class, InventoryHolder.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getBlock" -> block;
                    case "toString" -> "Kistenprobe " + x + "/" + y + "/" + z;
                    case "hashCode" -> (x * 31 + y) * 31 + z;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Die Kistenprobe beantwortet " + methode.getName()
                                    + " nicht - gefragt werden darf nur, WO sie steht");
                });
    }

    /** Ein Inventar, das genau einen Halter kennt. {@code null} ist ein Rucksack. */
    private static Inventory inventarMit(InventoryHolder haelt) {
        return (Inventory) Proxy.newProxyInstance(ShopwacheTest.class.getClassLoader(),
                new Class<?>[] {Inventory.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    // getHolder(false) und getHolder() haben denselben Namen - der
                    // Stellvertreter sieht den Unterschied nicht, und er braucht ihn
                    // nicht: Gefragt wird so oder so, WO die Kiste steht.
                    case "getHolder" -> haelt;
                    case "toString" -> "Inventarprobe " + haelt;
                    case "hashCode" -> 23;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Die Inventarprobe beantwortet " + methode.getName()
                                    + " nicht - gefragt werden darf nur der Halter");
                });
    }

    /**
     * Zwei Haelften, ein Inventar - eine ECHTE {@code DoubleChest}.
     *
     * <p><b>{@code DoubleChest} ist eine Klasse und keine Schnittstelle</b>, gemessen am
     * 08.11.2026: Ein {@code Proxy} darauf wirft in
     * {@code Proxy$ProxyBuilder.validateProxyInterfaces}. Darum wird hier eine wirkliche
     * gebaut - was geht, weil ihr Konstruktor nur ein {@code DoubleChestInventory}
     * entgegennimmt, und DAS ist eine Schnittstelle. Ihre beiden Seiten holt sie sich
     * daraus, und die Stellvertreter dahinter sind dieselben wie bei der Einzelkiste.
     *
     * <p>Der Umweg ist die Sache wert: Dass BEIDE Haelften gefragt werden, ist die
     * Doppelkistenfalle. Die beiden teilen EIN Inventar, der Shop haengt aber immer nur
     * an einer von ihnen.
     */
    private static InventoryHolder doppelkiste(InventoryHolder links,
            InventoryHolder rechts) {
        return new DoubleChest(doppelinventar(inventarMit(links), inventarMit(rechts)));
    }

    /** Das Inventar einer Doppelkiste - es kennt seine zwei Seiten. */
    private static DoubleChestInventory doppelinventar(Inventory links, Inventory rechts) {
        return (DoubleChestInventory) Proxy.newProxyInstance(
                ShopwacheTest.class.getClassLoader(),
                new Class<?>[] {DoubleChestInventory.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getLeftSide" -> links;
                    case "getRightSide" -> rechts;
                    case "toString" -> "Doppelinventarprobe";
                    case "hashCode" -> 29;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Die Doppelinventarprobe beantwortet " + methode.getName()
                                    + " nicht - gefragt werden die zwei Seiten");
                });
    }

    /**
     * Die Sicht auf ein Inventar - mehr fragt {@link InventoryOpenEvent} nicht.
     *
     * <p>Der Konstruktor des Ereignisses fragt sie ueberhaupt nicht: Er legt sie nur in
     * ein Feld. Die erste Frage stellt der Zweig, und das sind genau zwei - wer oeffnet
     * und was.
     */
    private static InventoryView sichtAuf(HumanEntity wer, Inventory oben) {
        return (InventoryView) Proxy.newProxyInstance(
                ShopwacheTest.class.getClassLoader(),
                new Class<?>[] {InventoryView.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getPlayer" -> wer;
                    case "getTopInventory" -> oben;
                    case "toString" -> "Sicht von " + wer;
                    case "hashCode" -> 31;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Die Sichtprobe beantwortet " + methode.getName()
                                    + " nicht - gefragt werden darf, WER oeffnet und WAS");
                });
    }

    /** Ein Wesen, das kein Spieler ist - nur dafuer da, keiner zu sein. */
    private static HumanEntity keinSpieler() {
        return (HumanEntity) Proxy.newProxyInstance(
                ShopwacheTest.class.getClassLoader(),
                new Class<?>[] {HumanEntity.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "toString" -> "Wesen ohne Spieler";
                    case "hashCode" -> 37;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Dieses Wesen soll GAR NICHTS beantworten - es ist nur dafuer"
                                    + " da, kein Spieler zu sein. Gefragt wurde "
                                    + methode.getName());
                });
    }

    /**
     * Eine Ablage, die bei der Kistenfrage wirft - die stumme Datenbank.
     *
     * <p>Geworfen wird in {@code anDerKiste}, und das ist die eine Stelle, die der
     * Oeffnen-Zweig zweimal fragt: {@code Shopbuch.istShopkiste} faengt den Wurf und
     * antwortet {@code true}, {@code Shopbuch.anDerKiste} faengt ihn und antwortet mit
     * einer leeren Liste. Genau dieses Auseinandergehen ist der gepruefte Fall.
     */
    private static final class StummeAblage implements Shopablage {

        @Override
        public Kistenshop anDemSchild(String welt, int x, int y, int z) {
            return null;
        }

        @Override
        public List<Kistenshop> anDerKiste(String welt, int x, int y, int z) {
            throw new IllegalStateException("Die Datenbank schweigt");
        }

        @Override
        public List<Kistenshop> vonSpieler(UUID besitzer) {
            return List.of();
        }

        @Override
        public List<Kistenshop> alle() {
            return List.of();
        }

        @Override
        public void schreibe(Kistenshop shop) {
            throw new UnsupportedOperationException("Diese Ablage schreibt nicht");
        }

        @Override
        public boolean loesche(String welt, int x, int y, int z) {
            return false;
        }
    }
}
