package de.freimarken.paper;

import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopregel;
import de.freimarken.kern.Warenprobe;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Die Gegenstaende der Kistenshops - die EINZIGE Stelle, die Bukkit und einen Shop
 * zusammenbringt.
 *
 * <p>Dieselbe Bauart und derselbe Grund wie bei {@link Ladenware}: Alles hier haengt am
 * laufenden Server und ist ohne ihn nicht pruefbar, also ist es so duenn wie moeglich.
 * Jede ENTSCHEIDUNG - welcher Bescheid, welcher Schutz, welcher Preis - steht in
 * {@link Shopbuch}, {@link de.freimarken.kern.Shopregel} und
 * {@link de.freimarken.kern.Shopschild}, und die sind ohne Server pruefbar.
 *
 * <p><b>Die Umwandlung Stapel in Text kommt aus {@link Ladenware} und wird nicht zweimal
 * gebaut.</b> Beide Bereiche schreiben Base64 der Paper-Bytefolge in eine
 * {@code MEDIUMTEXT}-Spalte, und eine zweite Umwandlung daneben liefe auseinander: Dann
 * laege im Abholfach eine Form und im Shop eine andere, und ein Shop, dessen Laden
 * geraeumt wird, koennte seine Ware nicht wiederfinden.
 *
 * <p><b>Drei Dinge sind hier Bukkit-Wissen und nirgends sonst:</b> welche Bloecke eine
 * Doppelkiste sind, welcher Block ein Schild TRAEGT, und welche Behaelter ein Shop sein
 * duerfen. Alle drei sind Loecher der Vorbilder, und alle drei sind nur hier zu schliessen,
 * weil nur hier die Welt sichtbar ist.
 *
 * <p><b>ZWEI Schnittstellen und eine Klasse</b> [08.11.2026]: {@link Shopbuch.Warenleser}
 * fuer das Anlegen, {@link Shophandel.Warenhand} fuer den Handel. Eine zweite Klasse
 * daneben haette {@code isSimilar} ein zweites Mal gebraucht - die Frage "ist dieser
 * Stapel DIESE Ware" -, und zwei Fassungen davon laufen auseinander. Dann naehme der
 * Ausgabegang anderes an als der Zaehlgang hergibt, und das ist die Form, in der aus einem
 * Zaehlfehler eine Buchung ohne Lieferung wird.
 */
final class Shopware implements Shopbuch.Warenleser, Shophandel.Warenhand {

    /**
     * Was ein Shop sein darf - eine ERLAUBNISLISTE und keine Verbotsliste.
     *
     * <p>Eine Verbotsliste ist bei jedem Spielupdate falsch: Minecraft bekommt neue
     * Behaelter, und ein neuer stuende dann als Shop offen, ohne dass jemand darueber
     * nachgedacht haette.
     *
     * <p><b>Was ausdruecklich NICHT darin steht, und warum:</b>
     * <ul>
     *   <li>{@code SHULKER_BOX} - QuickShop #1889: Mit einer Shulkerkiste als Shopblock
     *       liess sich Shulker in Shulker legen, gegen das Vanilla-Verhalten. Eine
     *       Shulkerkiste ist ausserdem selbst ein Gegenstand, also ein Behaelter, der in
     *       einen Behaelter passt - der Shop koennte in seiner eigenen Ware liegen.</li>
     *   <li>{@code FURNACE}, {@code DISPENSER}, {@code DROPPER}, {@code BREWING_STAND} -
     *       QuickShops eigene Warnliste nennt sie: Sie haben mehrere Faecher mit
     *       verschiedener Bedeutung (Brennstoff, Ergebnis), und Redstone bedient sie. Ein
     *       Werfer mit Redstone ist ein Weg, Ware aus dem Shop zu holen, der nicht durch
     *       den Shopcode laeuft - genau die zweite der drei Luecken aus Plan 7.4.</li>
     *   <li>{@code HOPPER} - zieht selbst. Ein Shop, der sich leersaugt.</li>
     * </ul>
     */
    private static final Set<Material> ERLAUBTE_KISTEN =
            Set.of(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL);

    /**
     * Das Zeichen vor einem Namen, wenn die Ware mehr ist als ihr Material.
     *
     * <p>Das ist die halbe Antwort auf ChestShop #305 - "die Verzauberung steht am Ende
     * gar nicht darauf". Ganz zu schliessen ist die Luecke auf fuenfzehn Zeichen nicht;
     * ein Kunde soll aber am Schild SEHEN, dass hier nicht die gewoehnliche Ware liegt,
     * und nicht erst beim Kauf. Die andere Haelfte ist die Kaufmeldung, die die Ware
     * vollstaendig nennt.
     */
    static final String BESONDERS = "+";

    /** So lang darf der Name werden, wenn das Zeichen davor noch Platz braucht. */
    private static final int LAENGE_MIT_ZEICHEN =
            Warenprobe.LAENGSTER_NAME - BESONDERS.length();

    private final Server server;
    private final Logger protokoll;

    Shopware(Server server, Logger protokoll) {
        this.server = server;
        this.protokoll = protokoll;
    }

    @Override
    public Warenprobe ausNamen(String getippt) {
        Material material = Material.matchMaterial(getippt);
        // Kein Material, oder eines, das es nur als Block gibt: WATER, FIRE, das
        // Kopfstueck einer Tuer. Beides ist dieselbe Absage - was man nicht in die Hand
        // nehmen kann, kann man nicht verkaufen.
        if (material == null || !material.isItem() || material.isAir()) {
            return null;
        }
        try {
            return probeVon(new ItemStack(material));
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Der Gegenstand " + material
                    + " liess sich nicht als Warenprobe fassen", fehler);
            return null;
        }
    }

    @Override
    public Warenprobe ausKiste(String welt, int x, int y, int z) {
        Inventory inventar = inventarAn(welt, x, y, z);
        if (inventar == null) {
            return null;
        }
        ItemStack erste = null;
        for (ItemStack stapel : inventar.getStorageContents()) {
            if (stapel == null || stapel.getType().isAir()) {
                continue;
            }
            if (erste == null) {
                erste = stapel;
            } else if (!passt(erste, stapel)) {
                // Lumias Regel, woertlich: "funktioniert jedoch nur, wenn von diesem Item
                // nur ein Gegenstand in der Kiste enthalten ist". Mit zwei Arten waere
                // nicht zu entscheiden, welche gemeint ist - und ein geratener Shop
                // verkauft die falsche Ware.
                //
                // isSimilar und nicht gleiches Material: Eine verzauberte und eine
                // gewoehnliche Spitzhacke sind ZWEI Arten, und das ist die Folge der
                // Entscheidung vom 08.11.2026. Wer beides in die Kiste legt, bekommt
                // keinen geratenen Shop.
                return null;
            }
        }
        if (erste == null) {
            return null;
        }
        try {
            return probeVon(erste);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Der Gegenstand in der Kiste bei " + welt + " "
                    + x + "/" + y + "/" + z + " liess sich nicht als Warenprobe fassen",
                    fehler);
            return null;
        }
    }

    @Override
    public boolean taugtAlsShopkiste(String welt, int x, int y, int z) {
        Block block = blockAn(welt, x, y, z);
        return block != null && istErlaubteKiste(block);
    }

    // --- Der Handel: zaehlen und bewegen, nichts entscheiden -----------------

    @Override
    public boolean lesbar(Kistenshop shop) {
        return vorbildVon(shop) != null;
    }

    /**
     * Wie viele Stueck dieser Ware auf einen Platz gehen.
     *
     * <p>Die Groesse des GEGENSTANDS, gemessen und nicht geraten. Dass die Rechnung in
     * {@link #bestand} zusaetzlich die Grenze des Inventars einbezieht, ist keine zweite
     * Wahrheit, sondern eine andere Frage: Hier steht, was ein Stapel dieser Ware IST
     * (das begrenzt den Klick), dort, was auf einen Platz DIESES Behaelters passt (das
     * begrenzt den Platz). Die zweite kann nur kleiner sein, und kleiner ist hier die
     * sichere Richtung.
     *
     * <p>0 bei unlesbarer Ware. Das faengt {@code Shophandel} ueber {@link #lesbar} ab,
     * bevor hier gefragt wird; kommt es doch hierher, wirft {@code Shopregel.Stufung}
     * darauf - und das ist richtig, denn eine geratene Stapelgroesse macht aus einem
     * unbekannten Gegenstand den groesstmoeglichen Handel.
     */
    @Override
    public int stapelgroesse(Kistenshop shop) {
        ItemStack vorbild = vorbildVon(shop);
        return vorbild == null ? 0 : vorbild.getMaxStackSize();
    }

    /**
     * Was auf dieser Seite liegt und was noch hineinpasst - in STUECK.
     *
     * <p><b>Hier wird nur ABGELESEN, gerechnet wird in {@link Shopregel}.</b> Dass ein
     * angefangener Stapel mitzaehlt, ist eine Regel, die falsch sein kann - also gehoert
     * sie dorthin, wo eine Probe sie lesen kann. Dieselbe Teilung wie bei {@code kurzname}.
     *
     * <p><b>{@code getStorageContents} und nicht {@code getContents}.</b> Beim Spieler
     * enthaelt jenes auch vier Ruestungsplaetze und die zweite Hand - fuenf Faecher, die
     * kein Platz fuer Ware sind. Mit {@code getContents} gezaehlt waere Ware in der
     * zweiten Hand VORHANDEN und, wenn {@code removeItem} sie nicht anfasst, nicht
     * ENTNEHMBAR - und das ist Geld aus nichts. An einer Kiste sind beide dasselbe;
     * der Unterschied zaehlt nur beim Spieler, und genau darum wird er verwechselt.
     * Dieselbe Lehre steht schon in {@code Ladenware.freiePlaetze}.
     *
     * <p><b>Was {@code removeItem} wirklich anfasst, ist hier NICHT nachpruefbar</b>
     * und gehoert in den Spieltest. Die Zaehlung traegt trotzdem, und zwar aus einem
     * anderen Grund als der Annahme: Sie verspricht NIE mehr, als die Lagerplaetze
     * hergeben. Griffe {@code removeItem} auch in Ruestung und zweite Hand, fande es
     * seine Menge also schon in den Faechern, die gezaehlt wurden - die uebrigen werden
     * nie gebraucht. Die Richtung ist damit in jedem Fall die sichere; was offen
     * bleibt, ist nur, ob der Shop manchmal weniger annimmt, als ein Spieler traegt.
     */
    @Override
    public Shophandel.Bestand bestand(Shophandel.Seite seite, UUID kunde,
            Kistenshop shop) {
        Inventory inventar = inventarFuer(seite, kunde, shop);
        ItemStack vorbild = vorbildVon(shop);
        if (inventar == null || vorbild == null) {
            return new Shophandel.Bestand(0, 0);
        }
        int frei = 0;
        List<Integer> gleiche = new ArrayList<>();
        for (ItemStack platz : inventar.getStorageContents()) {
            if (platz == null || platz.getType().isAir()) {
                frei++;
            } else if (passt(vorbild, platz)) {
                gleiche.add(platz.getAmount());
            }
        }
        int[] mengen = new int[gleiche.size()];
        for (int i = 0; i < mengen.length; i++) {
            mengen[i] = gleiche.get(i);
        }
        return new Shophandel.Bestand(Shopregel.vorratInStueck(mengen),
                Shopregel.platzInStueck(frei, aufEinenPlatz(vorbild, inventar),
                        mengen));
    }

    /**
     * Nimmt Stueck dieser Ware weg und meldet, wie viele WIRKLICH wegkamen.
     *
     * <p>{@code removeItem} ist NICHT unteilbar: Es entnimmt, was es findet, und meldet
     * nur den Rest - anders als {@code Kasse.abbuchen}, auf dessen Ganz-oder-gar-nicht
     * sich die Regeln verlassen duerfen. Darum wird die Zahl gemeldet und nicht ein
     * {@code boolean}: Was damit geschieht, entscheidet {@code Shophandel}, und dort ist
     * es pruefbar.
     *
     * <p>Je Portion ein eigenes Exemplar - siehe {@link #portionen}.
     */
    @Override
    public int nehme(Shophandel.Seite seite, UUID kunde, Kistenshop shop,
            int stueck) {
        return bewege(true, seite, kunde, shop, stueck);
    }

    /**
     * Legt Stueck dieser Ware hin und meldet, wie viele WIRKLICH ankamen.
     *
     * <p>{@code addItem} fuegt bei zu wenig Platz TEILWEISE ein und gibt den Rest zurueck.
     * Dieselbe Zusage wie in {@code Ladenware}, nur hier als Zahl statt als {@code boolean}:
     * Dort hat ein Rest keinen Ort, hier hat er einen - die Seite, von der er kam.
     */
    @Override
    public int lege(Shophandel.Seite seite, UUID kunde, Kistenshop shop,
            int stueck) {
        return bewege(false, seite, kunde, shop, stueck);
    }

    private int bewege(boolean nehmen, Shophandel.Seite seite, UUID kunde,
            Kistenshop shop, int stueck) {
        Inventory inventar = inventarFuer(seite, kunde, shop);
        ItemStack vorbild = vorbildVon(shop);
        if (inventar == null || vorbild == null || stueck < 1) {
            return 0;
        }
        int rest = 0;
        for (ItemStack portion : portionen(vorbild, stueck,
                aufEinenPlatz(vorbild, inventar))) {
            // NUR die Map lesen, nie den uebergebenen Stapel: Beide Methoden setzen die
            // Menge des Exemplars herunter, waehrend sie arbeiten, und "alles gepasst"
            // sowie "nichts gepasst" lassen sie beide unveraendert.
            var uebrig = nehmen
                    ? inventar.removeItem(portion)
                    : inventar.addItem(portion);
            for (ItemStack liegengeblieben : uebrig.values()) {
                rest += liegengeblieben.getAmount();
            }
        }
        return stueck - rest;
    }

    /**
     * Die Ware in Stapelgroesse zerlegt - je Portion ein eigenes Exemplar.
     *
     * <p>In Portionen und nicht als ein Stapel mit 3.456 Stueck: Ein Exemplar ueber seiner
     * Stapelgroesse ist kein Zustand, den Minecraft kennt, und was die Inventarmethoden
     * damit tun, ist nicht zugesagt.
     *
     * <p>{@code asQuantity} liefert eine KOPIE. Das ist die Zusage, auf der der Rest der
     * Rechnung steht: Was {@code removeItem} und {@code addItem} an ihrem Argument
     * veraendern, trifft nie die Warenprobe des Shops.
     */
    private static List<ItemStack> portionen(ItemStack vorbild, int stueck,
            int stapelgroesse) {
        List<ItemStack> teile = new ArrayList<>();
        int offen = stueck;
        while (offen > 0) {
            int diesmal = Math.min(offen, stapelgroesse);
            teile.add(vorbild.asQuantity(diesmal));
            offen -= diesmal;
        }
        return teile;
    }

    /**
     * Ob dieser Stapel DIESE Ware ist - die EINE Stelle, die das entscheidet.
     *
     * <p>{@code isSimilar} und nicht {@code equals}: Jenes vergleicht die MENGE mit, und
     * die Warenprobe haelt genau ein Stueck - ein voller Stapel waere dann nie dieselbe
     * Ware. Und nicht das Material allein: Eine gewoehnliche und eine verzauberte
     * Spitzhacke sind zwei Arten, sonst verkauft man die eine zum Preis der anderen.
     *
     * <p>Und erst recht nicht der gespeicherte TEXT der Warenprobe. Dessen Bytes gehen
     * durch den Datenfixer, wenn ein Update die Gegenstandsform aendert - der gespeicherte
     * Text traegt dann die alte Fassung, ein heute aus der Kiste gelesener Gegenstand die
     * neue. Die Texte sind verschieden, obwohl die Ware dieselbe ist, und jeder Shop der
     * Welt meldete nach dem Update "ausverkauft".
     *
     * <p><b>Dieselbe Frage im Zaehlgang und im Ausgabegang.</b> Das ist der Grund, warum
     * es diese Methode gibt und nicht drei Abschriften: Sonst nimmt der eine Gang mehr an,
     * als der andere hergibt, und aus dem Unterschied wird eine Buchung ohne Lieferung.
     *
     * <p>Bewusst mitgenommen: {@code isSimilar} vergleicht auch die Haltbarkeit. Eine
     * angeschlagene Spitzhacke ist NICHT dieselbe Ware wie eine frische, und der Shop
     * weist sie ab. Das ist die sichere Richtung und dieselbe Strenge, nach der ein Shop
     * aus einer gemischten Kiste gar nicht entsteht.
     */
    private static boolean passt(ItemStack vorbild, ItemStack stapel) {
        return vorbild.isSimilar(stapel);
    }

    /**
     * Ob dieser Stapel die Ware EINES dieser Shops ist - fuer den Trichter am ZIEL.
     *
     * <p><b>Warum es diese Frage gibt.</b> Die Trichterregel laesst Ware in eine
     * Shopkiste HINEIN, denn so wird ein Laden beliefert - auch ein fremder, und das
     * ist gewollt. Damit liess sich eine fremde Shopkiste aber mit MUELL fuellen, bis
     * sie keinen Platz mehr fuer einen Ankauf hatte. Seit dem 09.11.2026 kommt nur
     * noch die Ware des Shops hinein: Nachfuellen und der automatische Shop (Farm,
     * Trichter, Shopkiste) bleiben, das Verstopfen ist weg.
     *
     * <p><b>Was bleibt, und es ist benannt:</b> Wer die ECHTE Ware verschenkt, kann
     * die Kiste trotzdem fuellen. Das kostet ihn aber mehr als den Besitzer, der sie
     * nur ausraeumen muss - der Angriff bestraft sich selbst.
     *
     * <p><b>EINES dieser Shops, nicht des ersten.</b> An einer Doppelkiste koennen
     * zwei Schilder mit zwei Waren haengen, und beide Haelften teilen ein Inventar.
     * Wer nur den ersten Shop fragt, sperrt dem zweiten die Belieferung.
     *
     * <p><b>Der Preis.</b> Je Shop eine Aufloesung der Warenprobe - also hoechstens
     * eine je Ereignis, denn es wird abgebrochen, sobald eine passt. Gerufen wird das
     * erst, wenn das Ziel ueberhaupt eine Shopkiste ist (eine Map im
     * Arbeitsspeicher), und ein Trichterereignis feuert nur, wenn wirklich ein
     * Gegenstand wandert. Die Aufloesung je FACH, vor der
     * {@link #vorbildVon} warnt, entsteht hier nicht.
     *
     * <p><b>Ein {@code null}-Stapel heisst NEIN, eine unlesbare Warenprobe auch -
     * und eine LEERE Shopliste ebenso.</b> Was nicht zu vergleichen ist, kommt nicht
     * hinein; das ist hier die sichere Richtung, denn ein abgewiesener Trichter
     * stockt, eine verstopfte Kiste muss jemand ausraeumen.
     *
     * <p>Die leere Liste ist dabei nicht nebenbei, sondern TRAGEND: Sie ist der
     * Stoerfall. Schweigt die Ablage, sagt {@code Shopbuch.istShopkiste} "hier steht
     * eine Shopkiste" und {@code anDerKiste} gibt nichts - der Aufrufer weiss dann,
     * dass eine zu schuetzende Kiste da ist, aber nicht, was hineindarf. Im
     * Trichterzweig stand dafuer erst ein eigener Riegel; eine Einspritzung hat
     * gezeigt, dass die zwei sich gegenseitig deckten und darum keiner mehr geprueft
     * war. Jetzt steht die Zusage hier, einmal, und
     * {@code ShopwacheTest.stummeAblageNimmtNichtsAn} haelt sie.
     */
    static boolean istWareVon(List<Kistenshop> shops, ItemStack stapel) {
        if (stapel == null) {
            return false;
        }
        for (Kistenshop shop : shops) {
            ItemStack vorbild = vorbildVon(shop);
            if (vorbild != null && passt(vorbild, stapel)) {
                return true;
            }
        }
        return false;
    }

    /** Wie viele Stueck dieser Ware auf einen Platz DIESES Behaelters gehen. */
    private static int aufEinenPlatz(ItemStack vorbild, Inventory inventar) {
        return Math.min(vorbild.getMaxStackSize(), inventar.getMaxStackSize());
    }

    /**
     * Ein Exemplar der Ware dieses Shops - oder {@code null}.
     *
     * <p>Je Aufruf neu aufgeloest, und das ist ein Preis: Der Zaehlgang laeuft ueber bis
     * zu 54 Faecher, aber nur EINMAL je Gang. Was nicht geschehen darf, ist eine
     * Aufloesung je FACH - darum nimmt {@link #passt} das Vorbild als Argument und loest
     * nicht selbst auf.
     */
    private static ItemStack vorbildVon(Kistenshop shop) {
        return Ladenware.ausText(shop.ware().inhalt());
    }

    /** Das Inventar dieser Seite - oder {@code null}. */
    private Inventory inventarFuer(Shophandel.Seite seite, UUID kunde,
            Kistenshop shop) {
        if (seite == Shophandel.Seite.KUNDE) {
            Player spieler = server.getPlayer(kunde);
            return spieler == null ? null : spieler.getInventory();
        }
        return inventarAn(shop.schildWelt(), shop.kisteX(), shop.kisteY(),
                shop.kisteZ());
    }

    /**
     * Das Inventar an dieser Stelle - oder {@code null}.
     *
     * <p><b>{@code getState(false)} und dann {@code getInventory()}</b>, und dieser Weg
     * ist der ganze Grund, warum es die Methode gibt. {@code getState()} ohne Argument
     * baut einen SCHNAPPSCHUSS des Behaelters samt Inhalt; {@code getInventory()} darauf
     * liefert trotzdem das lebende Inventar, aber {@code getSnapshotInventory()} waere das
     * kopierte - und ein {@code update()} danach schriebe einen veralteten Inhalt
     * zurueck. Genau so verdoppelt sich Ware, wenn der Besitzer die Kiste offen hat,
     * waehrend ein Kunde kauft.
     *
     * <p>Bei einer Doppelkiste liefert {@code getInventory()} das GEMEINSAME Inventar
     * beider Haelften - darauf steht schon {@link #kistenbloecke}. Fuer das Zaehlen und
     * Entnehmen ist das richtig: Die Ware liegt wirklich in beiden.
     */
    Inventory inventarAn(String welt, int x, int y, int z) {
        Block block = blockAn(welt, x, y, z);
        if (block == null || !(block.getState(false) instanceof Container behaelter)) {
            return null;
        }
        return behaelter.getInventory();
    }

    /**
     * Ob an diesem Block ein Shop haengen DARF - siehe {@link #ERLAUBTE_KISTEN}.
     *
     * <p>Die Blockart UND der Zustand: Die Liste allein genuegte nicht, denn ein
     * {@code CHEST}-Block ohne Behaelterzustand ist ein Fehler der Welt, und
     * {@code getInventory} wuerfe darauf. Die Liste steht aber VORNE, und das ist hier
     * nicht Geschmack: Diese Frage laeuft bei jeder Explosion fuer jeden einzelnen
     * Block, und ein Mengenvergleich ist billig, wo ein Blockzustand es nicht ist.
     *
     * <p><b>{@code getState(false)} und nicht {@code getState()}</b>: Jenes baut einen
     * SCHNAPPSCHUSS des ganzen Behaelters samt Inhalt, und zwar auch dann, wenn die
     * Antwort "nein" lautet. Hier wird nur gefragt, WAS dort steht, nicht was darin
     * liegt - und ein Schnappschuss je Block einer Explosion ist eine Kopie des halben
     * Kistenbestands der Welt.
     */
    static boolean istErlaubteKiste(Block block) {
        return ERLAUBTE_KISTEN.contains(block.getType())
                && block.getState(false) instanceof Container;
    }

    /**
     * EIN Exemplar dieser Ware als Probe - die Menge bleibt aussen.
     *
     * <p>{@code asOne} und nicht der Stapel selbst: Die Anzahl je Handel steht in der
     * Shopzeile, und zwei Angaben derselben Menge liefen auseinander, sobald jemand die
     * eine aendert. Genau das sagt der Kopf von {@link Warenprobe}.
     */
    private static Warenprobe probeVon(ItemStack stapel) {
        ItemStack eines = stapel.asOne();
        return new Warenprobe(kurzname(eines), Ladenware.alsText(eines));
    }

    /**
     * Der Name fuer die vierte Schildzeile - hoechstens fuenfzehn Zeichen.
     *
     * <p><b>Gekuerzt wird HIER und nicht in {@link Warenprobe}</b>, und zwar genau so, wie
     * es dort gefordert ist: Die Zeile weist einen zu langen Namen ab, damit kein Name
     * stillschweigend zu zwei Namen wird. Die Kuerzung gehoert also an die EINE Stelle,
     * die sie bewusst vornimmt.
     *
     * <p>Zwei Stufen, und die erste ist wichtiger als sie aussieht:
     * {@code NETHERITE_PICKAXE} hat siebzehn Zeichen, und hart gekuerzt stuende
     * "NETHERITE_PICKA" auf dem Schild. Das letzte Wortstueck - "PICKAXE" - ist fuer einen
     * Menschen lesbar und fuer das Programm genauso genau, naemlich gar nicht: Welche Ware
     * ein Shop handelt, entscheidet {@code Warenprobe.inhalt} und nie dieser Name.
     *
     * <p>Dass dabei zwei Materialien denselben Kurznamen bekommen koennen, ist darum
     * harmlos - aber nur, solange niemand den Namen zum Vergleichen benutzt. Diese Zusage
     * haelt {@code ShopwareTafel}: Nirgends im Programm wird {@code ware().name()}
     * verglichen.
     */
    static String kurzname(ItemStack stapel) {
        return kurzname(stapel.getType().name(), stapel.hasItemMeta());
    }

    /**
     * Dieselbe Kuerzung, aber OHNE Bukkit - und darum pruefbar.
     *
     * <p>Zwei Methoden und nicht eine, und der Grund ist die Zusage selbst: Die Regel
     * oben haengt an {@code ItemStack}, also an einem laufenden Server, und waere ohne
     * ihn nicht zu pruefen. Was von ihr WIRKLICH eine Entscheidung ist - welche zwei
     * Stufen in welcher Reihenfolge, wo das Zeichen bleibt -, ist reine
     * Zeichenkettenarbeit. Die steht hier und wird von {@code ShopwareTest} Zeichen fuer
     * Zeichen gelesen; droben bleibt nur das Ablesen zweier Eigenschaften.
     *
     * @param materialname der Name, wie Bukkit ihn schreibt - GROSS mit Unterstrichen
     * @param besonders    ob die Ware mehr ist als ihr Material
     */
    static String kurzname(String materialname, boolean besonders) {
        String name = materialname;
        int platz = besonders ? LAENGE_MIT_ZEICHEN : Warenprobe.LAENGSTER_NAME;
        if (name.length() > platz) {
            int strich = name.lastIndexOf('_');
            if (strich >= 0 && name.length() - strich - 1 <= platz
                    && strich + 1 < name.length()) {
                name = name.substring(strich + 1);
            } else {
                name = name.substring(0, platz);
            }
        }
        return besonders ? BESONDERS + name : name;
    }

    /**
     * Alle Bloecke, die zu diesem Inventar gehoeren - eins oder zwei.
     *
     * <p><b>Das ist die Doppelkistenfalle, und sie ist gemessen eine.</b>
     * {@code Inventory.getLocation()} liefert bei einer Doppelkiste die MITTE zwischen
     * den beiden Haelften, also eine Stelle mit halben Koordinaten, an der kein Block
     * steht. Wer damit nach einem Shop fragt, fragt an einer Stelle, an der nie einer
     * haengt - und der Trichterschutz greift nicht.
     *
     * <p>Darum geht der Weg ueber den {@link InventoryHolder}: Ein {@link DoubleChest}
     * nennt seine beiden Seiten einzeln, und die haben echte Blockstellen.
     *
     * <p><b>Beide Haelften, nicht nur die mit dem Schild.</b> Ein Shop zeigt auf EINE
     * Haelfte - {@code Kistenshop.istNachbar} verlangt Abstand eins vom Schild, und das
     * kann nur eine sein. Die Ware liegt aber im gemeinsamen Inventar: Ein Trichter unter
     * der anderen Haelfte zieht dieselben Gegenstaende, und wer die andere Haelfte
     * abbaut, bekommt deren Haelfte der Ware in die Hand. Beides war in den Vorbildern
     * offen.
     */
    static List<Block> bloeckeVon(Inventory inventar) {
        // getHolder(false) statt getHolder(): Jenes baut einen Schnappschuss des
        // Behaelters samt Inhalt, und diese Frage wird bei JEDEM Trichtertakt gestellt -
        // achtmal je Sekunde je Trichter, in einer Markthalle also dauernd. Gebraucht
        // wird davon nur, WO die Kiste steht.
        InventoryHolder haelt = inventar.getHolder(false);
        if (haelt instanceof DoubleChest doppel) {
            List<Block> beide = new ArrayList<>(2);
            if (doppel.getLeftSide() instanceof BlockState links) {
                beide.add(links.getBlock());
            }
            if (doppel.getRightSide() instanceof BlockState rechts) {
                beide.add(rechts.getBlock());
            }
            return beide;
        }
        if (haelt instanceof BlockState zustand) {
            return List.of(zustand.getBlock());
        }
        // Kein Blockinventar: der Rucksack eines Spielers, ein Pferd, ein Trichterwagen.
        // Die Stelle allein genuegt dann nicht, und der Aufrufer behandelt das: Was keinen
        // Block hat, kann keine Shopkiste sein.
        return List.of();
    }

    /**
     * Alle Bloecke, die sich mit DIESEM ein Inventar teilen - eins oder zwei.
     *
     * <p>Die Gegenrichtung zu {@link #bloeckeVon} und dieselbe Frage: Welche Bloecke
     * halten dieselbe Ware? Deshalb geht sie auch durch dieselbe Methode und nicht an
     * ihr vorbei - zwei Wege zu dieser Antwort liefen auseinander, und dann waere eine
     * Haelfte der Doppelkiste bei einem Ereignis geschuetzt und beim anderen nicht.
     *
     * <p><b>Das ist der zweite Teil der Doppelkistenfalle.</b> Wer die freie Haelfte
     * einer Doppelkiste abbaut, bekommt deren Haelfte der Ware in die Hand - die Kiste
     * zerfaellt in zwei Einzelkisten, und das Inventar wird geteilt. Der Shop haengt dann
     * noch an der anderen Haelfte und verkauft, was nicht mehr darin liegt. In den
     * Vorbildern war dieser Weg offen.
     *
     * <p>Eine leere Liste bei einem Block, der keine erlaubte Kiste ist: Dann gibt es
     * nichts zu schuetzen, und der Aufrufer erspart sich den Zustand.
     */
    static List<Block> kistenbloecke(Block kiste) {
        if (!istErlaubteKiste(kiste)) {
            return List.of();
        }
        if (kiste.getState(false) instanceof Container behaelter) {
            return bloeckeVon(behaelter.getInventory());
        }
        return List.of();
    }

    /**
     * Der Block, der dieses Schild TRAEGT - oder {@code null}, wenn es keines ist.
     *
     * <p>Ein Schild faellt, wenn sein Traeger verschwindet, und dann steht ein Shop in
     * der Datenbank, dessen Schild es nicht mehr gibt. ChestShop hat dafuer eigens ein
     * {@code handlePhysicsBreak}; hier wird der Traeger stattdessen MITGESCHUETZT. Das ist
     * weniger Code und hat kein Zeitfenster: Was nicht wegkann, laesst nichts fallen.
     *
     * <p>Drei Formen, und jede haengt woanders: Ein Wandschild an dem Block, von dem es
     * wegzeigt; ein Haengeschild an dem darueber; ein Stehschild auf dem darunter.
     */
    static Block trageblock(Block schild) {
        if (schild.getBlockData() instanceof WallSign wand) {
            return schild.getRelative(wand.getFacing().getOppositeFace());
        }
        if (schild.getType().name().endsWith("HANGING_SIGN")) {
            return schild.getRelative(BlockFace.UP);
        }
        if (org.bukkit.Tag.ALL_SIGNS.isTagged(schild.getType())) {
            return schild.getRelative(BlockFace.DOWN);
        }
        return null;
    }

    /**
     * Der Weltschluessel dieses Blocks - {@code null}, wenn er keine Welt hat.
     *
     * <p>Hier und nicht in jeder Wache: Der Schluesseltext ist die Form, in der eine
     * Welt in der Datenbank steht, und zwei Fassungen davon waeren zwei Antworten auf
     * die Frage, in welcher Welt eine Stelle liegt. Die Gegenrichtung steht in
     * {@link #blockAn}.
     */
    static String weltVon(Block block) {
        return block.getWorld() == null ? null : block.getWorld().key().asString();
    }

    /**
     * Der Schildblock dieses Shops - oder {@code null}.
     *
     * <p>Gebraucht, wenn ein Shop von der KISTE her aufgeloest wird: Dann bleibt das
     * Schild an der Wand haengen, obwohl die Kiste darunter verschwindet, und muss selbst
     * abgebaut werden. Die Stelle steht im Shop, also ist hier nichts zu suchen - nur die
     * Welt aufzuloesen.
     */
    Block schildblockVon(Kistenshop shop) {
        return blockAn(shop.schildWelt(), shop.schildX(), shop.schildY(),
                shop.schildZ());
    }

    /**
     * Der Block an dieser Stelle - oder {@code null}, wenn die Welt nicht geladen ist.
     *
     * <p>Kein heisser Pfad: Gefragt wird beim Anlegen eines Shops, einmal. Der Schutz und
     * der Handel kommen mit den Bloecken aus dem Ereignis und brauchen diesen Weg nicht.
     *
     * <p>Paketprivat und nicht privat: {@link Shopzugriff} braucht DIESELBE Aufloesung,
     * um die Rechtefrage an einer Stelle zu stellen. Zwei Aufloesungen daneben waeren
     * zwei Antworten auf die Frage, in welcher Welt eine Stelle liegt.
     */
    Block blockAn(String welt, int x, int y, int z) {
        for (World eine : server.getWorlds()) {
            if (eine.key().asString().equals(welt)) {
                return eine.getBlockAt(x, y, z);
            }
        }
        protokoll.warning("Die Welt " + welt + " ist nicht geladen - die Stelle " + x + "/"
                + y + "/" + z + " ist nicht zu erreichen.");
        return null;
    }
}
