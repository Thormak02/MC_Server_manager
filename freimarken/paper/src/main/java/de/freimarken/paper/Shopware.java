package de.freimarken.paper;

import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Warenprobe;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
 */
final class Shopware implements Shopbuch.Warenleser {

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
        Block block = blockAn(welt, x, y, z);
        if (block == null || !(block.getState() instanceof Container behaelter)) {
            return null;
        }
        ItemStack erste = null;
        for (ItemStack stapel : behaelter.getInventory().getContents()) {
            if (stapel == null || stapel.getType().isAir()) {
                continue;
            }
            if (erste == null) {
                erste = stapel;
            } else if (!erste.isSimilar(stapel)) {
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
