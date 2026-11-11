package de.freimarken.paper;

import de.freimarken.kern.Blockort;
import de.freimarken.kern.Truhenverzeichnis;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;

/**
 * Die Einrichtung der Stadt - wer nichts bauen darf, sieht auch in keine Kiste.
 *
 * <p>Die Antwort auf eine Luecke, die seit Schritt 3 offen stand und die der Plan
 * vorhergesehen hat: <b>Der Abenteuermodus verhindert das BAUEN, nicht das OEFFNEN.</b>
 * Altfurt steht in {@code spielmodus: abenteuer}, und genau deshalb hielt dort niemand
 * eine Kiste zu. Jedes Fass in der Halle, jeder Ofen im Bundesratshaus und jede Truhe im
 * Lager stand jedem offen; was der Betreiber hineinlegt, waere am ersten Abend weg.
 *
 * <p><b>Am {@link InventoryOpenEvent} und nicht an der {@link Schutzwache}</b>, und das
 * ist die tragende Entscheidung:
 * <ul>
 *   <li>Die Schutzwache entscheidet ueber fuenfzehn Ereignisarten an einem ORT. Hier geht
 *       es um genau eine Tat an einem BEHAELTER - Tueren, Hebel und Knoepfe einer Stadt
 *       soll jeder benutzen duerfen, und eine Ortsregel koennte das nicht trennen: Sie
 *       sieht nur Orte.</li>
 *   <li>Das OEFFNEN ganz zu verweigern ist dieselbe Entscheidung wie beim Kistenshop
 *       [08.11.2026]: Das Nehmen hat zehn Wege, und jede vergessene Art waere ein Loch.
 *       Die Shopwache sperrt darum am selben Ereignis.</li>
 * </ul>
 *
 * <p><b>Drei Ausnahmen, und keine davon steht hier zweimal:</b>
 * <ul>
 *   <li><b>Die gemieteten Staende.</b> Sie kommen hier nie an: Den Klick auf eine
 *       Ladentruhe bricht schon {@link Ladenschutz} ueber die Schutzwache ab, und ein
 *       abgebrochener Klick oeffnet kein Inventar. Darum fragt diese Wache nicht nach
 *       Einheiten - eine zweite Mietpruefung daneben liefe eines Tages anders.</li>
 *   <li><b>Die Freie Truhe.</b> Sie ist der eine Behaelter, der jedem offen steht; was
 *       dort gilt, entscheidet {@link Truhenwache}.</li>
 *   <li><b>Kistenshops.</b> Dort entscheidet {@code Shopwache.beimOeffnen} nach
 *       {@code Kistenshop.offenFuer} - also auch, wer freigegeben ist. Diese Wache laesst
 *       sie durch und sagt nichts dazu.</li>
 * </ul>
 *
 * <p><b>Das Teamrecht {@link Schutzwache#UEBERALL} geht vor</b>, und zwar dasselbe wie
 * beim Bauen: Wer die Stadt einrichtet, muss in ihre Kisten sehen. Ein eigenes Recht
 * daneben waere eine zweite Zeile in der plugin.yml, die niemand pflegt - und beim
 * ersten Umsortieren haette ein Baumeister Baurecht ohne Kistenrecht.
 *
 * <p><b>Nur die LADENWELT.</b> Sie ist die Welt, die von Hand gebaut wird und in der
 * nichts einem Spieler gehoert. Die Marktstadt deckt der Besitzschutz, die Wildnis deckt
 * niemand - dort ist eine Kiste die Kiste dessen, der sie hingestellt hat, und das regelt
 * der Kistenshop oder gar nichts.
 */
final class Einrichtungswache implements Listener {

    /**
     * Die EINE Frage an die Shops: Haengt an dieser Kiste ein Laden?
     *
     * <p>Eine Naht und nicht das ganze {@link Shopbuch}: Gebraucht wird genau diese
     * Antwort, und mit ihr laeuft die ganze Entscheidung dieser Wache ohne Server durch
     * eine Probe. Gesetzt wird sie mit {@code shopbuch::istShopkiste} - dessen Antwort
     * bei stummer Ablage ({@code true}) ist hier die richtige: Dann entscheidet die
     * Shopwache, und die sperrt in genau diesem Fall selbst.
     */
    interface Shopfrage {

        boolean istShopkiste(String welt, int x, int y, int z);
    }

    private final String weltschluessel;
    private final Truhenverzeichnis truhen;
    private final Shopfrage shops;
    private final Sprache sprache;

    /**
     * @param weltschluessel die Ladenwelt, fertig als {@code freimarken:altfurt}
     * @param truhen         die Stellen der Freien Truhen - darf leer sein
     * @param shops          die Frage nach einem Laden an dieser Kiste
     */
    Einrichtungswache(String weltschluessel, Truhenverzeichnis truhen, Shopfrage shops,
            Sprache sprache) {
        if (weltschluessel == null || weltschluessel.isBlank() || truhen == null
                || shops == null || sprache == null) {
            throw new IllegalArgumentException("Eine Einrichtungswache ohne Welt, Truhen,"
                    + " Shopbuch oder Sprache waere eine, die nichts entscheiden kann");
        }
        this.weltschluessel = weltschluessel;
        this.truhen = truhen;
        this.shops = shops;
        this.sprache = sprache;
    }

    /**
     * Ob dieser Behaelter zur Einrichtung gehoert - ohne Bukkit pruefbar.
     *
     * <p>Paketprivat und getrennt vom Ereignis, weil hier die ganze Entscheidung liegt:
     * Welt, Freie Truhe, Kistenshop. Das Ereignis darueber holt nur die Bloecke.
     */
    boolean gehoertZurStadt(String welt, int x, int y, int z) {
        if (!weltschluessel.equals(welt)) {
            return false;
        }
        if (truhen.istFreieTruhe(new Blockort(welt, x, y, z))) {
            return false;
        }
        return !shops.istShopkiste(welt, x, y, z);
    }

    /**
     * Beim Oeffnen: zu, wenn der Behaelter zur Stadt gehoert.
     *
     * <p>{@code ignoreCancelled}: Hat die Schutzwache den Klick schon abgebrochen, gibt
     * es dieses Ereignis gar nicht - und hat eine andere Wache das Oeffnen abgesagt, ist
     * eine zweite Absage nur eine zweite Zeile fuer denselben Spieler.
     *
     * <p>{@code getPlayer()} gibt eine {@code HumanEntity}: Ein Inventar kann auch fuer
     * ein anderes Wesen offen sein. Ohne Spieler gibt es keine Rechte, also keine
     * Entscheidung - dieselbe Pruefung und derselbe Grund wie in
     * {@code Shopwache.beimOeffnen}.
     *
     * <p><b>Ueber {@link Shopware#bloeckeVon} und nicht ueber
     * {@code Inventory.getLocation()}</b>: Jenes liefert bei einer Doppelkiste die MITTE
     * zwischen den Haelften, also eine Stelle, an der kein Block steht. Dieselbe Falle
     * und dieselbe Antwort wie bei der Truhenwache und beim Trichterschutz.
     *
     * <p>EINE Haelfte genuegt zum Sperren: Beide teilen dasselbe Inventar. Umgekehrt
     * genuegt eine Haelfte auch zum Durchlassen - stuende ein Shopschild an der einen
     * Haelfte, waere die andere ohne diese Regel zu.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimOeffnen(InventoryOpenEvent ereignis) {
        if (!(ereignis.getPlayer() instanceof Player spieler)) {
            return;
        }
        if (spieler.hasPermission(Schutzwache.UEBERALL)) {
            return;
        }
        boolean stadt = false;
        for (Block block : Shopware.bloeckeVon(ereignis.getInventory())) {
            String welt = Shopware.weltVon(block);
            if (welt == null) {
                continue;
            }
            if (!gehoertZurStadt(welt, block.getX(), block.getY(), block.getZ())) {
                return;
            }
            stadt = true;
        }
        if (!stadt) {
            return;
        }
        ereignis.setCancelled(true);
        spieler.sendActionBar(sprache.eineZeile(WacheSatz.SCHUTZ_EINRICHTUNG,
                Einsatz.keine()));
    }
}
