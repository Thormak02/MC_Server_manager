package de.freimarken.paper;

import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopweg;
import de.freimarken.kern.Shopschild;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

/**
 * Der Schutz und das Anlegen der Kistenshops - elf Bukkit-Ereignisse.
 *
 * <p><b>Was hier entschieden wird, ist nichts.</b> Jeder Zweig stellt eine Frage an
 * {@link #betroffen}, holt seinen Wortlaut bei {@link Shoptext} und laesst
 * {@link Shopbuch} handeln. Das ist derselbe Schnitt wie bei {@link Mietwache} und aus
 * demselben Grund: Diese Klasse ist ohne laufenden Server nicht zu starten, und was in
 * ihr steht, ist nicht pruefbar. Also steht so wenig wie moeglich darin.
 *
 * <p><b>Der Schutz gilt UEBERALL</b>, auch in der Wildnis. Das ist der Unterschied zur
 * {@link Schutzwache}: Dort entscheidet der ORT, hier der Shop. Ein Shop auf einer
 * eigenen Parzelle ist durch den Bauschutz gedeckt, einer in der Markthalle durch den
 * {@link Ladenschutz} - einer am Wegesrand durch nichts, und der waere von jedem
 * abbaubar.
 *
 * <h2>Die drei Luecken der Vorbilder, und wo sie hier geschlossen sind</h2>
 *
 * <p><b>Erstens: jeder Weg zur Kiste, der nicht durch den Shopcode laeuft.</b> Das ist
 * die teuerste der drei. ChestShop #538: Mit Papers {@code disable-move-event: true}
 * feuerte das Trichterereignis nie, und jeder Shop der Welt war leerzuziehen.
 * QuickShop #2165: Ein Zwischenspeicher hielt die veraltete Antwort bis zu drei Minuten.
 * Hier ist die Antwort eine Map im Arbeitsspeicher, die nie veraltet
 * ({@code ZwischenspeicherShops} ist eine VOLLSTAENDIGE Kopie und kein Puffer mit
 * Verfallszeit), und die Regel ist unsymmetrisch: {@link #beimGegenstandWandern} prueft
 * nur die QUELLE. Kein Trichter zieht aus einer Shopkiste - nie, auch nicht der des
 * Besitzers. Hineinlegen bleibt erlaubt, denn das ist, wie ein Laden beliefert wird.
 *
 * <p><b>Zweitens: das Schild faellt und der Shop bleibt.</b> ChestShop fasst das mit einem
 * eigenen {@code handlePhysicsBreak} NACH dem Fall nach. Hier faellt nichts: Der Block,
 * der ein Shopschild TRAEGT, ist mitgeschuetzt ({@link Shoptext.Teil#TRAEGER}). Das ist
 * weniger Code und hat kein Zeitfenster.
 *
 * <p><b>Drittens: das Schild laesst sich umschreiben.</b> Seit Minecraft 1.20 darf jeder
 * ein Schild bearbeiten, das nicht gewachst ist - ein Kunde koennte an einem fremden Shop
 * den Preis aendern. Zwei Riegel: Das Schild wird beim Anlegen GEWACHST
 * ({@link #wachse}), und {@link #beimSchildSchreiben} weist jedes Beschreiben einer
 * Stelle ab, an der schon ein Shop haengt - gleich von wem. Die Wahrheit steht in der
 * Datenbank, nicht auf dem Holz. Das ist auch QuickShops eigene Warnung zu
 * {@code allow-owner-break-shop-sign}.
 *
 * <h2>Was NICHT abgedeckt ist, und warum</h2>
 *
 * <p>{@code BlockFromToEvent} (fliessendes Wasser, Lava) steht nicht dabei. Weder Schild
 * noch Kiste noch ein gewoehnlicher Traegerblock sind von einer Fluessigkeit zu
 * entfernen - ein Schild wird ueberflutet und bleibt. Das Ereignis feuert dagegen bei
 * jedem Flusstakt jedes fliessenden Blocks, und der Preis stuende in keinem Verhaeltnis
 * zu einem Fall, den es nicht gibt. Sollte sich das als falsch erweisen, ist die
 * Ergaenzung ein Zweig wie {@link #beimVerbrennen}.
 *
 * <p>{@code BlockPhysicsEvent} ebenso, und aus demselben Grund: Es ist das allgemeine
 * Ereignis fuer "ein Block hat sich geaendert" und feuert vielhundertfach je Sekunde. Die
 * acht Zerstoerungszweige hier nennen jeden Weg einzeln, auf dem ein Block
 * verschwinden kann, und das ist der billigere Weg zum selben Schutz.
 */
final class Shopwache implements Listener {

    /**
     * Wer Bundshops anlegen und abbauen darf.
     *
     * <p>Ein eigenes Recht und nicht {@link Schutzwache#UEBERALL}: Das Team baut
     * ueberall, aber ein Bundshop ist eine wirtschaftliche Einrichtung mit unbegrenztem
     * Vorrat. Wer einen anlegt, kann jeden Preis des Servers festlegen - das ist eine
     * andere Vollmacht als die, einen Block auf fremdem Grund zu setzen.
     */
    static final String RECHT_BUNDSHOP = "freimarken.shop.bund";

    /**
     * Eine Absage je Spieler und Pause - ohne das wird aus Abbauhalten ein Regen.
     *
     * <p>Dieselbe Zahl wie {@code Schutzwache.MELDEPAUSE_MS}, aber ueber
     * {@link Abklingzeit} statt ueber eine eigene Map. Jene Wache hat ihre eigene, und
     * das bleibt in diesem Zug so: Sie umzustellen waere eine Aenderung an geprueftem
     * Code ausserhalb dieses Auftrags. Dass es zwei Formen derselben Drossel gibt, steht
     * hier aufgeschrieben und nicht verschwiegen.
     */
    private static final Duration MELDEPAUSE = Duration.ofSeconds(2);

    /**
     * Die sechs Nachbarn eines Blocks - genau die, an denen ein Schild haengen kann.
     *
     * <p>Keine Diagonalen, und das ist {@code Kistenshop.istNachbar}: Manhattan-Abstand
     * genau eins. Dasselbe Maass wie in den Vorbildern ("not diagonal, max 1 block").
     */
    private static final List<BlockFace> SECHS = List.of(BlockFace.DOWN, BlockFace.UP,
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    /**
     * Was nach dem Ereignis noch zu tun ist - die Naht zum Taktgeber.
     *
     * <p>Gebraucht fuer genau eine Sache: das Wachsen des Schildes. Am
     * {@code SignChangeEvent} ist der Text noch nicht geschrieben, und ein
     * {@code update()} mitten darin setzte den leeren Zustand zurueck. Also im naechsten
     * Takt.
     *
     * <p>Eine Schnittstelle und kein {@code Plugin} im Feld: So kann eine Probe
     * {@code tat -> tat.run()} uebergeben und das Wachsen sofort sehen, ohne einen
     * Taktgeber.
     */
    interface Nachtrag {
        void imNaechstenTakt(Runnable tat);
    }

    private final Shopbuch buch;
    private final Shopware waren;
    private final Sprache sprache;
    private final Nachtrag nachtrag;
    private final Abklingzeit drossel = new Abklingzeit(MELDEPAUSE);
    private final Logger protokoll;

    /**
     * <b>Kein Bundwort mehr im Feld</b> - FUND VOM 10.11.2026. Es stand hier als Text,
     * also als Schnappschuss des Starts, und wurde an {@code Shopbuch.anlege}
     * weitergegeben. Ein {@code /freimarken neuladen} baut absichtlich kein Objekt neu,
     * und darum wirkte ein geaendertes {@code handel.bundshop-wort} nie - waehrend der
     * Befehl "uebernommen" meldete. Das Wort haelt jetzt {@link Shopbuch}, und zwar als
     * Halter; diese Wache fragt es im Augenblick des Schreibens.
     */
    Shopwache(Shopbuch buch, Shopware waren, Sprache sprache, Nachtrag nachtrag,
            Logger protokoll) {
        if (nachtrag == null) {
            throw new IllegalArgumentException("Ohne Nachtrag bleibt jedes Shopschild"
                    + " unbewachst, und ein unbewachstes Schild darf seit Minecraft 1.20"
                    + " jeder umschreiben - auch die Preise eines fremden Shops.");
        }
        this.buch = buch;
        this.waren = waren;
        this.sprache = sprache;
        this.nachtrag = nachtrag;
        this.protokoll = protokoll;
    }

    // --- Anlegen --------------------------------------------------------------

    /**
     * Ein beschriebenes Schild - wird es ein Shop?
     *
     * <p><b>Die Reihenfolge der drei Fragen ist die Sache selbst.</b> Erst: Haengt hier
     * SCHON ein Shop? Dann wird nichts umgeschrieben, gleich von wem und gleich was da
     * steht - sonst waere "Hallo" in der Preiszeile der Weg, einen fremden Shop
     * unsichtbar zu machen, der weiter handelt. Danach erst: Ist ueberhaupt ein Shop
     * gemeint? Ohne diese Frage bekaeme jedes Wegweiserschild die Absage "die Menge ist
     * keine Zahl".
     *
     * <p><b>Nicht {@code ignoreCancelled}</b>, anders als bei allen anderen Zweigen hier:
     * Ein abgebrochenes {@code SignChangeEvent} heisst, dass der Text nicht gesetzt wird -
     * und dann gibt es nichts anzulegen. Der Zweig muss aber trotzdem laufen, falls ein
     * anderer Zuhoerer das Ereignis abbricht, nachdem dieser einen Shop geschrieben hat.
     * Darum steht die Pruefung auf {@code isCancelled} am Anfang und nicht in der
     * Anmerkung: so ist sie zu lesen.
     */
    @EventHandler
    public void beimSchildSchreiben(SignChangeEvent ereignis) {
        if (ereignis.isCancelled()) {
            return;
        }
        Block schild = ereignis.getBlock();
        String welt = weltVon(schild);
        if (welt == null) {
            return;
        }
        Player spieler = ereignis.getPlayer();
        if (buch.anDemSchild(welt, schild.getX(), schild.getY(), schild.getZ()) != null) {
            // Der zweite Riegel hinter dem Wachs. Auch fuer den Besitzer: Preise aendert
            // man durch Abbauen und neu Setzen, denn nur so geht die Aenderung durch die
            // Datenbank. Ein umgeschriebenes Schild zeigte etwas anderes an, als der Shop
            // handelt - und dann betruegt das Holz den Kunden.
            ereignis.setCancelled(true);
            sende(spieler, List.of(sprache.eineZeile(ShopSatz.SHOP_SCHON_EIN_SHOP,
                    Einsatz.keine())));
            return;
        }
        String[] zeilen = alsText(ereignis.lines());
        if (!Shopschild.istGemeint(zeilen)) {
            return;
        }
        Block kiste = kisteNeben(schild);
        Shopbuch.Anlage anlage = buch.anlege(spieler.getUniqueId(), welt, schild.getX(),
                schild.getY(), schild.getZ(), kiste.getX(), kiste.getY(), kiste.getZ(),
                zeilen, spieler.hasPermission(RECHT_BUNDSHOP),
                spieler.hasPermission(Schutzwache.UEBERALL));
        sende(spieler, Shoptext.anlageZeilen(sprache, anlage, zeilen[3]));
        if (anlage.bescheid() != Shopbuch.Bescheid.ANGELEGT) {
            // Das Schild bleibt stehen, wie er es getippt hat - und unbewachst. So sieht
            // er seinen Fehler und kann ihn an derselben Stelle berichtigen. Ein geleertes
            // Schild liesse ihn alles neu tippen, ohne zu wissen, was falsch war.
            return;
        }
        // BEIDE Namen hinein, und WELCHER genommen wird, entscheidet der Kern - FUND
        // VOM 10.11.2026. Diese Stelle gab vorher immer den Spielernamen, auch bei
        // einem Bundshop: Der trug dann in der ersten Zeile den Namen dessen, der ihn
        // aufgestellt hat und sah aus wie ein Laden eines Spielers. Die Begruendung
        // steht bei Shopschild.schreibe - samt dem Grund, warum die Wahl nicht hier
        // steht.
        String[] fertig = Shopschild.schreibe(spieler.getName(), buch.bundWort(),
                anlage.shop());
        for (int i = 0; i < fertig.length; i++) {
            ereignis.line(i, Component.text(fertig[i]));
        }
        // Erst im naechsten Takt, denn JETZT ist der Text noch nicht geschrieben.
        Block zuWachsen = schild;
        nachtrag.imNaechstenTakt(() -> wachse(zuWachsen));
    }

    /**
     * Macht das Schild unbearbeitbar.
     *
     * <p>Wachs ist Vanilla und braucht kein Plugin, um zu wirken: Ein gewachstes Schild
     * laesst niemand mehr bearbeiten, auch der Besitzer nicht, auch nicht nach einem
     * Serverwechsel und auch dann nicht, wenn dieses Plugin fehlt. Das ist der Riegel,
     * der traegt, wenn alle anderen weg sind.
     *
     * <p>Klagt nur und wirft nicht: Der Shop steht schon, und ein Wurf aus einem
     * nachgetragenen Takt liesse ihn mit einer halben Zusicherung zurueck. Der zweite
     * Riegel in {@link #beimSchildSchreiben} greift auch ohne Wachs.
     */
    private void wachse(Block schild) {
        if (schild.getState(false) instanceof Sign tafel) {
            tafel.setWaxed(true);
            tafel.update(true, false);
            return;
        }
        protokoll.warning("Das Shopschild bei " + schild.getX() + "/" + schild.getY()
                + "/" + schild.getZ() + " ist einen Takt spaeter kein Schild mehr (dort"
                + " steht " + schild.getType() + ") - es bleibt unbewachst.");
    }

    /**
     * Die Kiste neben diesem Schild - oder der Traegerblock, wenn keine da ist.
     *
     * <p><b>Nie {@code null}</b>, und das ist Absicht: Wenn hier nichts gefunden wird,
     * soll {@link Shopbuch} die Absage {@code KEINE_KISTE} geben und nicht diese Klasse.
     * Die Wache nennt eine STELLE, das Buch entscheidet, ob dort eine Kiste steht - sonst
     * stuende dieselbe Entscheidung an zwei Orten.
     *
     * <p><b>Der Traegerblock hat Vorrang</b> und wird zuerst gefragt. Das ist die
     * Zuordnung, die der Spieler mit der Platzierung selbst getroffen hat: Er hat das
     * Schild AN diese Kiste gehaengt oder DARAUF gestellt. Stehen zwei Kisten neben dem
     * Schild, waere jede andere Wahl geraten.
     */
    private Block kisteNeben(Block schild) {
        Block traeger = Shopware.trageblock(schild);
        if (traeger != null && Shopware.istErlaubteKiste(traeger)) {
            return traeger;
        }
        for (BlockFace richtung : SECHS) {
            Block nachbar = schild.getRelative(richtung);
            if (Shopware.istErlaubteKiste(nachbar)) {
                return nachbar;
            }
        }
        // Keine Kiste in Reichweite: Die Stelle des Traegers geht hinein, damit das Buch
        // dort nachsieht und KEINE_KISTE sagt. Der Traeger liegt immer im Abstand eins,
        // also haelt auch Kistenshop.istNachbar.
        return traeger != null ? traeger : schild.getRelative(BlockFace.DOWN);
    }

    // --- Schutz ---------------------------------------------------------------

    /**
     * Abbauen - der einzige Zweig, in dem jemand den Shop auch AUFLOESEN darf.
     *
     * <p><b>Erst die Datenbank, dann der Block.</b> Scheitert das Loeschen, bleibt der
     * Block stehen und der Spieler liest die Stoerung. Umgekehrt stuende ein Shop in der
     * Datenbank, dessen Schild es nicht mehr gibt - und der naechste Spieler, der dort
     * ein Schild aufstellt, bekaeme ein fremdes Geschaeft.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimAbbauen(BlockBreakEvent ereignis) {
        Betroffen was = betroffen(ereignis.getBlock());
        if (was == null) {
            return;
        }
        Player spieler = ereignis.getPlayer();
        Kistenshop fremder = ersterFremder(spieler, was.shops(), Shopweg.ABBAUEN);
        if (fremder != null) {
            ereignis.setCancelled(true);
            melde(spieler, was.teil(), fremder);
            return;
        }
        if (!loeseAuf(spieler, was)) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Loescht die Shops, die dieser Abbau beendet - und nimmt ihre Schilder mit.
     *
     * <p>Die Schilder werden selbst abgebaut und nicht der Physik ueberlassen. Bei
     * {@link Shoptext.Teil#KISTE} MUSS das sein - ein Schild an einer Wand bleibt haengen,
     * wenn die Kiste darunter verschwindet -, bei {@link Shoptext.Teil#TRAEGER} faellt es
     * ohnehin, und zweimal abgebaut ist kein Schaden. Dass es an EINER Stelle steht, ist
     * mehr wert als die eingesparte Zeile.
     *
     * <p>{@code breakNaturally} und nicht {@code setType(AIR)}: Das Schild soll
     * herausfallen. Es loest kein weiteres {@code BlockBreakEvent} aus, also auch keine
     * Rekursion in diesen Zweig.
     *
     * @return {@code false}, wenn die Datenbank geschwiegen hat - dann nichts anruehren
     */
    private boolean loeseAuf(Player spieler, Betroffen was) {
        for (Kistenshop shop : was.shops()) {
            Shopbuch.Bescheid bescheid = buch.baueAb(shop.schildWelt(), shop.schildX(),
                    shop.schildY(), shop.schildZ());
            if (bescheid == Shopbuch.Bescheid.GESTOERT) {
                sende(spieler, Shoptext.abbauZeilen(sprache, bescheid, shop));
                return false;
            }
            sende(spieler, Shoptext.abbauZeilen(sprache, bescheid, shop));
        }
        for (Block schild : was.schilder()) {
            schild.breakNaturally();
        }
        return true;
    }

    /**
     * Explosionen - die Shopbloecke fallen aus der Liste, die Explosion bleibt.
     *
     * <p><b>Nicht das Ereignis abbrechen.</b> Ein abgebrochenes Explosionsereignis liesse
     * die ganze Explosion ausfallen: Kein Block der Umgebung ginge kaputt, und eine
     * Kreatur neben einer Markthalle waere ein Schutzzauber fuer das halbe Viertel. Die
     * Liste zu kuerzen ist der genaue Eingriff - alles andere bricht wie immer.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimWesenSprengen(EntityExplodeEvent ereignis) {
        schuetzeVorExplosion(ereignis.blockList());
    }

    /** Dasselbe fuer einen Block, der sprengt - Bett und Respawnanker im Nether. */
    @EventHandler(ignoreCancelled = true)
    public void beimBlockSprengen(BlockExplodeEvent ereignis) {
        schuetzeVorExplosion(ereignis.blockList());
    }

    private void schuetzeVorExplosion(List<Block> bloecke) {
        bloecke.removeIf(block -> betroffen(block) != null);
    }

    /**
     * Feuer - Schilder und Kisten sind aus Holz.
     *
     * <p>Der Zweig, der am leichtesten vergessen wird und am meisten kostet: Ein Schild
     * brennt, und eine Kiste auch. Ohne ihn genuegte ein Feuerzeug neben einer
     * Markthalle, und der Schaden waere kein Diebstahl, sondern ein Totalverlust.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimVerbrennen(BlockBurnEvent ereignis) {
        if (betroffen(ereignis.getBlock()) != null) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Kolben, ausfahrend.
     *
     * <p><b>Die geschobenen Bloecke UND der Block eins weiter in Schubrichtung.</b> Das
     * ist der Grund fuer {@link #richtungsNachbarn}: Ein Schild, das im Weg steht, wird
     * zerstoert und nicht geschoben - es steht darum NICHT in {@code getBlocks()}. Wer
     * nur die Liste prueft, schuetzt die Traeger und verliert das Schild.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimKolbenAus(BlockPistonExtendEvent ereignis) {
        if (trifftShop(richtungsNachbarn(ereignis.getBlocks(), ereignis.getDirection()))) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Kolben, einfahrend - hier genuegt die Liste.
     *
     * <p>Beim Zurueckziehen wird nichts zerstoert; was zaehlt, ist, dass ein gezogener
     * Block seinen Platz VERLAESST. Ist er der Traeger eines Shopschildes, faellt das
     * Schild - und genau das faengt {@link #betroffen} als
     * {@link Shoptext.Teil#TRAEGER}.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimKolbenEin(BlockPistonRetractEvent ereignis) {
        if (trifftShop(ereignis.getBlocks())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Wesen, die Bloecke nehmen oder aendern - Enderman, Ziege, Zombie, Sandfall.
     *
     * <p>Eine Kiste kann ein Enderman nicht nehmen, einen Traegerblock schon. Das genuegt:
     * Mit dem Traeger faellt das Schild.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimWesenBlock(EntityChangeBlockEvent ereignis) {
        if (betroffen(ereignis.getBlock()) != null) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Verrottendes Laub - der billigste Zweig und ein echter Fall.
     *
     * <p>Wer sein Schild an einen Baum haengt und den Stamm abbaut, verliert es, sobald
     * das Laub verrottet. Das Ereignis feuert nur fuer Laub, kostet also so gut wie
     * nichts - und ohne es faellt ein Shop still und ohne Spur.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimLaubfall(LeavesDecayEvent ereignis) {
        if (betroffen(ereignis.getBlock()) != null) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Der Trichter - und die Regel ist UNSYMMETRISCH.
     *
     * <p><b>Nur {@link InventoryMoveItemEvent#getSource()} wird geprueft, nie das Ziel.</b>
     * Kein Trichter zieht aus einer Shopkiste - nie, auch nicht der des Besitzers.
     * Hineinlegen bleibt erlaubt, denn so wird ein Laden beliefert.
     *
     * <p>Warum nicht auch dem Besitzer? Weil ein Trichter nicht fragt, wem er gehoert.
     * Wer unter seine eigene Shopkiste einen Trichter setzt und daneben eine Kiste, hat
     * einen Shop gebaut, der sich selbst leert - und der naechste Kunde kauft Ware, die
     * schon weg ist. QuickShop #2539 beschreibt den Weg von der anderen Seite: "they can
     * place a hopper underneath it to obtain the items". Eine Ausnahme fuer den Besitzer
     * waere genau diese Luecke mit einer Besitzerpruefung davor, und die Besitzerpruefung
     * laeuft ins Leere, sobald jemand die Kiste des Nachbarn beliefert.
     *
     * <p><b>Am ZIEL ist die Regel seit dem 09.11.2026 feiner als ein Ja oder Nein:</b>
     * Hineinlegen bleibt erlaubt, aber nur die WARE des Shops
     * ({@link Shopware#istWareVon}). Vorher war das Ziel ganz ungeprueft, und damit
     * liess sich eine fremde Shopkiste mit Muell fuellen, bis sie keinen Platz mehr
     * fuer einen Ankauf hatte. Der automatische Shop - Farm, Trichter, Shopkiste -
     * bleibt damit moeglich, und auch das Nachfuellen aus einer Lagerkiste.
     *
     * <p>Eine pauschale Sperre des Ziels waere die naheliegende Loesung gewesen und
     * haette den automatischen Shop mitgenommen - auch den eigenen, denn ein Trichter
     * fragt nicht, wem er gehoert. Entschieden am 09.11.2026 so.
     *
     * <p>Das Ereignis deckt auch den Trichterwagen: Bukkit meldet ihn mit dem Inventar des
     * Wagens als Ziel und der Kiste als Quelle. Das war ChestShop #538 - dort feuerte das
     * Ereignis mit Papers {@code disable-move-event: true} ueberhaupt nicht mehr, und
     * jeder Shop der Welt war leerzuziehen. Diese Einstellung darf auf diesem Server nicht
     * gesetzt werden; dass sie es nicht ist, steht in {@code docs/citybuild_plan.md}.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimGegenstandWandern(InventoryMoveItemEvent ereignis) {
        if (istShopinventar(ereignis.getSource())) {
            ereignis.setCancelled(true);
            return;
        }
        AnDerKiste ziel = shopsAn(ereignis.getDestination());
        if (!ziel.shopkiste()) {
            return;
        }
        // Schweigt die Ablage, ist die Ware unbekannt - dann kommt nichts hinein.
        // Ein stockender Trichter ist zu beheben, eine verstopfte Kiste muss jemand
        // ausraeumen. Getragen wird das von istWareVon SELBST: Eine leere Shopliste
        // heisst dort "nichts passt". Hier stand erst ein zweiter Riegel
        // (ziel.gestoert()), und eine Einspritzung hat gezeigt, dass die beiden sich
        // GEGENSEITIG decken - jeder allein entfernt aenderte nichts, also hielt
        // keine Probe mehr, welcher von beiden traegt. Ein Riegel mit Probe ist mehr
        // wert als zwei ohne.
        if (!Shopware.istWareVon(ziel.shops(), ereignis.getItem())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Die HAND an einer fremden Shopkiste - und dieser Zweig ist der EINZIGE Riegel
     * auf diesem Weg.
     *
     * <p><b>Warum er gebraucht wird:</b> Eine Shopkiste war gegen Trichter, Abbau,
     * Feuer und Kolben geschuetzt, aber nicht gegen eine Hand.
     * {@code Schutzwache.darfHandeln} gibt {@code true}, sobald die Welt nicht die
     * Parzellenwelt ist - in hochwald, kornmark, nether und end war also jede
     * Shopkiste von jedem zu oeffnen. Wer die Kiste oeffnen kann, braucht nicht zu
     * kaufen. In der Marktstadt und in der Markthalle greift schon der Klickzweig der
     * Schutzwache (eine Kiste ist dort heikel, weil ihr Blockzustand ein
     * {@code InventoryHolder} ist), und dann kommt dieses Ereignis gar nicht an -
     * darum gibt es auch keine zwei Handbalken aus zwei Drosseln.
     *
     * <p><b>Das Oeffnen kennt keine Richtung, und das ist eine Entscheidung.</b> Die
     * Trichterregel ist unsymmetrisch (siehe {@link #beimGegenstandWandern}:
     * Hineinlegen bleibt erlaubt, denn so wird ein Laden beliefert) - eine
     * Oeffnungsabsage schliesst dagegen BEIDE Richtungen. Ein Fremder kann einem Shop
     * danach auch nichts schenken. Entschieden am 08.11.2026 so: Das Nehmen hat nicht
     * einen Weg, sondern zehn (Linksklick, Rechtsklick, Umschaltklick, Ziehen ueber
     * mehrere Faecher, Zahlentasten, Tausch mit der zweiten Hand, Doppelklick zum
     * Einsammeln, Wurf) - und jede Art, die ein Zweig vergisst, ist ein Loch. EIN
     * Ereignis ohne Ausnahme ist dicht; zehn Klickarten sind es irgendwann. Wer einen
     * fremden Shop beliefern will, nimmt bis zum geplanten {@code /shop freigeben}
     * den Trichter.
     *
     * <p><b>Nicht {@link #betroffen}, sondern die Kistenfrage.</b> Jene Methode faellt
     * bis zum TRAEGER-Zweig durch, und dann waere eine Kiste gesperrt, die bloss ein
     * fremdes Shopschild TRAEGT, obwohl in ihr keine Shopware liegt. Beim Abbauen ist
     * der Traeger richtig (wer ihn bricht, nimmt das Schild mit), beim Oeffnen zaehlt
     * nur, wessen Ware drin liegt.
     *
     * <p>{@code getPlayer()} gibt eine {@code HumanEntity} und keinen
     * {@code Player} - ein Inventar kann auch fuer ein anderes Wesen offen sein.
     * Ohne Spieler gibt es keine Rechte, also keine Entscheidung, also wird
     * durchgelassen: Dieselbe Pruefung und derselbe Grund wie in
     * {@code Messwache.beimOeffnen}.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimOeffnen(InventoryOpenEvent ereignis) {
        if (!(ereignis.getPlayer() instanceof Player spieler)) {
            return;
        }
        AnDerKiste kiste = shopsAn(ereignis.getInventory());
        if (!kiste.shopkiste()) {
            return;
        }
        if (kiste.gestoert()) {
            // Geschuetzt, aber niemand nennbar. Die Absage steht trotzdem - siehe
            // AnDerKiste.gestoert - und melde() faengt den fehlenden Shop ab.
            ereignis.setCancelled(true);
            melde(spieler, Shoptext.Teil.KISTE, null);
            return;
        }
        Kistenshop fremd = ersterFremder(spieler, kiste.shops(), Shopweg.OEFFNEN);
        if (fremd == null) {
            return;
        }
        ereignis.setCancelled(true);
        melde(spieler, Shoptext.Teil.KISTE, fremd);
    }

    // --- Die eine Frage -------------------------------------------------------

    /**
     * Was ein Shop an diesem Block haengt - oder {@code null}.
     *
     * <p><b>Die Reihenfolge der drei Fragen ist die ihrer Kosten</b>, und das ist hier
     * nicht Feinschliff: Diese Methode laeuft bei jeder Explosion fuer jeden einzelnen
     * Block. Jede Frage hat darum ein billiges Tor davor - eine Blockart -, und die
     * Datenbank wird nie gefragt: Dahinter liegt {@code ZwischenspeicherShops}, also eine
     * Map im Arbeitsspeicher.
     */
    private Betroffen betroffen(Block block) {
        String welt = weltVon(block);
        if (welt == null) {
            return null;
        }
        if (Tag.ALL_SIGNS.isTagged(block.getType())) {
            Kistenshop shop = buch.anDemSchild(welt, block.getX(), block.getY(),
                    block.getZ());
            if (shop != null) {
                // Das Schild baut das Ereignis selbst ab - hier ist nichts nachzuholen.
                return new Betroffen(Shoptext.Teil.SCHILD, List.of(shop), List.of());
            }
        }
        if (Shopware.istErlaubteKiste(block)) {
            // BEIDE Haelften einer Doppelkiste, nicht nur die mit dem Schild: Sie teilen
            // ein Inventar, und wer die freie Haelfte abbaut, bekommt deren Haelfte der
            // Ware. Siehe Shopware.kistenbloecke.
            List<Kistenshop> shops = new ArrayList<>();
            for (Block teil : Shopware.kistenbloecke(block)) {
                shops.addAll(buch.anDerKiste(welt, teil.getX(), teil.getY(),
                        teil.getZ()));
            }
            if (!shops.isEmpty()) {
                return new Betroffen(Shoptext.Teil.KISTE, shops, schilderVon(shops));
            }
        }
        List<Kistenshop> getragen = new ArrayList<>();
        for (BlockFace richtung : SECHS) {
            Block nachbar = block.getRelative(richtung);
            if (!Tag.ALL_SIGNS.isTagged(nachbar.getType())) {
                continue;
            }
            Block traeger = Shopware.trageblock(nachbar);
            if (traeger == null || !traeger.equals(block)) {
                continue;
            }
            Kistenshop shop = buch.anDemSchild(welt, nachbar.getX(), nachbar.getY(),
                    nachbar.getZ());
            if (shop != null) {
                getragen.add(shop);
            }
        }
        return getragen.isEmpty() ? null
                : new Betroffen(Shoptext.Teil.TRAEGER, getragen, schilderVon(getragen));
    }

    /**
     * Ob dieses Inventar zu einer Shopkiste gehoert - der heisseste Pfad des Bereichs.
     *
     * <p>Gefragt bei jedem Trichtertakt, also achtmal je Sekunde je Trichter. Beides
     * dahinter ist billig: {@code getHolder(false)} baut keinen Schnappschuss, und die
     * Shopfrage ist eine Map im Arbeitsspeicher.
     */
    private boolean istShopinventar(Inventory inventar) {
        for (Block block : Shopware.bloeckeVon(inventar)) {
            String welt = weltVon(block);
            if (welt != null && buch.istShopkiste(welt, block.getX(), block.getY(),
                    block.getZ())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Was an diesem Inventar haengt - und ob es ueberhaupt eine Shopkiste ist.
     *
     * <p><b>Zwei Fragen und nicht eine, weil die Ablage zwei RICHTUNGEN hat.</b>
     * {@code Shopbuch.istShopkiste} gibt bei stummer Ablage {@code true} ("schuetze
     * sie"), {@code Shopbuch.anDerKiste} gibt eine LEERE Liste ("erfinde keinen
     * Besitzer"). Beim Oeffnen sind beide gebraucht: die erste entscheidet, OB
     * abgesagt wird, die zweite, WER genannt wird. Nur die zweite zu fragen waere ein
     * Schutz, der bei einer schweigenden Datenbank jede Kiste der Welt aufschliesst.
     *
     * <p><b>{@link Shopware#bloeckeVon} und nicht {@code inventar.getLocation()}.</b>
     * Jenes gibt bei einer Doppelkiste die MITTE mit halben Koordinaten, an der kein
     * Block steht - der Zweig waere dort stumm offen. Darum geht diese Methode
     * denselben Weg wie {@link #istShopinventar}: Der WEG zum Block ist einer, die
     * FRAGE daran ist eine andere.
     *
     * <p>Warum {@link #istShopinventar} trotzdem bleibt: Jenes ist der heisse Pfad
     * (achtmal je Sekunde je Trichter) und braucht nur eine der beiden Fragen. Diese
     * hier laeuft, wenn ein Mensch eine Kiste aufmacht.
     *
     * <p>Eine LEERE Blockliste ist kein Fehler: Rucksack, Pferd und Trichterwagen
     * haben keinen Block und koennen keine Shopkiste sein.
     */
    private AnDerKiste shopsAn(Inventory inventar) {
        boolean shopkiste = false;
        List<Kistenshop> shops = new ArrayList<>();
        for (Block block : Shopware.bloeckeVon(inventar)) {
            String welt = weltVon(block);
            if (welt == null) {
                continue;
            }
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();
            if (buch.istShopkiste(welt, x, y, z)) {
                shopkiste = true;
            }
            shops.addAll(buch.anDerKiste(welt, x, y, z));
        }
        return new AnDerKiste(shopkiste, shops);
    }

    /** Ob unter diesen Bloecken ein Shopblock ist - fuer die Zweige, die ganz absagen. */
    private boolean trifftShop(List<Block> bloecke) {
        for (Block block : bloecke) {
            if (betroffen(block) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Die Bloecke samt ihren Nachbarn in Schubrichtung - siehe {@link #beimKolbenAus}.
     *
     * <p>Der Block VOR dem letzten geschobenen ist der, der zerstoert wird, und er steht
     * in keiner Liste von Bukkit. Mitgenommen werden die Nachbarn ALLER geschobenen
     * Bloecke und nicht nur des letzten: Welcher der letzte ist, haengt an der Form der
     * Kette, und eine Rechnung darueber waere eine Annahme mehr als noetig.
     */
    private static List<Block> richtungsNachbarn(List<Block> bloecke,
            BlockFace richtung) {
        List<Block> alle = new ArrayList<>(bloecke.size() * 2);
        alle.addAll(bloecke);
        for (Block block : bloecke) {
            alle.add(block.getRelative(richtung));
        }
        return alle;
    }

    /** Die Schildbloecke dieser Shops - die mit abgebaut werden muessen. */
    private List<Block> schilderVon(List<Kistenshop> shops) {
        List<Block> schilder = new ArrayList<>(shops.size());
        for (Kistenshop shop : shops) {
            Block schild = waren.schildblockVon(shop);
            if (schild != null) {
                schilder.add(schild);
            }
        }
        return schilder;
    }

    /**
     * Der erste dieser Shops, an den dieser Spieler nicht darf - oder {@code null}.
     *
     * <p><b>Hier stehen nur die zwei Rechtefragen, die Regel steht im Kern</b>
     * ({@link Kistenshop#ersterFremder}). Das ist nicht Ordnungsliebe: Dieselbe Frage
     * wird beim Abbauen, beim Oeffnen der Kiste UND beim Anlegen eines zweiten
     * Schildes gestellt, und die dritte Stelle sitzt in {@link Shopbuch}, das keinen
     * {@code Player} sieht. Eine zweite Fassung dort liefe auseinander.
     *
     * <p>{@code hasPermission} ist der einzige Grund, warum diese Methode ueberhaupt
     * noch hier steht - nur eine Klasse mit Server kann ein Recht erfragen.
     *
     * <p><b>Der WEG kommt seit dem 09.11.2026 als Parameter herein</b>, denn die
     * Freigabe traegt nicht jeden: Ein Freigegebener darf oeffnen und abbauen, aber kein
     * zweites Schild anhaengen. Diese Huelle wird von zwei Zweigen gerufen und reicht
     * darum weiter, welcher fragt - entschieden wird es im Kern.
     */
    private static Kistenshop ersterFremder(Player spieler, List<Kistenshop> shops,
            Shopweg weg) {
        return Kistenshop.ersterFremder(shops, spieler.getUniqueId(), weg,
                spieler.hasPermission(Schutzwache.UEBERALL),
                spieler.hasPermission(RECHT_BUNDSHOP));
    }

    /**
     * Der Handbalken, hoechstens einmal je Pause - siehe {@link #MELDEPAUSE}.
     *
     * <p><b>Der Name wird HIER geholt und nicht in {@link Shoptext}.</b> Diese Klasse
     * laeuft ohnehin nur mit Server; jene soll ohne einen pruefbar bleiben, und ein
     * {@code Spielername.von} dort griff nach {@code Bukkit.server}. Dieselbe Teilung wie
     * zwischen {@link Schutzwache} und {@link Schutztext}.
     *
     * <p>Geholt wird er auch dann, wenn der Satz ihn nicht braucht - beim Bundshop und
     * beim Traeger. Das ist ein Nachschlag im Benutzerzwischenspeicher und keine Abfrage,
     * und die Alternative waere eine zweite Verzweigung ueber dieselben drei Faelle hier
     * oben, die in {@link Shoptext#schutzBalken} schon steht.
     */
    private void melde(Player spieler, Shoptext.Teil teil, Kistenshop shop) {
        if (drossel.restNs(spieler.getUniqueId(), System.nanoTime()) == 0L) {
            spieler.sendActionBar(Shoptext.schutzBalken(sprache, teil, shop,
                    Spielername.von(shop == null ? null : shop.besitzer())));
        }
    }

    /** Die EINE Sendestelle fuer Chatzeilen dieses Bereichs. */
    private static void sende(Player an, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            an.sendMessage(zeile);
        }
    }

    /** Der Weltschluessel dieses Blocks - {@code null}, wenn er keine Welt hat. */
    private static String weltVon(Block block) {
        return block.getWorld() == null ? null : block.getWorld().key().asString();
    }

    /**
     * Die vier Schildzeilen als Klartext.
     *
     * <p>Klartext und nicht die Components: {@code Shopschild.lies} ist im Kern und kennt
     * kein Adventure. Ein Spieler kann in eine Schildzeile ohnehin keine Farbe tippen,
     * also geht hier nichts verloren - und was er tippt, ist genau das, was er lesen
     * soll, wenn die Absage ihm seine Eingabe zurueckgibt.
     */
    private static String[] alsText(List<Component> zeilen) {
        String[] text = new String[Shopschild.ZEILEN];
        PlainTextComponentSerializer klartext = PlainTextComponentSerializer.plainText();
        for (int i = 0; i < text.length; i++) {
            text[i] = i < zeilen.size() ? klartext.serialize(zeilen.get(i)) : "";
        }
        return text;
    }

    /**
     * Was an einem Block haengt - das Ergebnis von {@link #betroffen}.
     *
     * @param shops    alle Shops, die dieser Block traegt. Mehrere bei einer Doppelkiste
     *                 mit zwei Schildern und bei einem Block, der zwei Schilder haelt
     * @param schilder die Schildbloecke, die beim Abbauen MIT weg muessen - leer, wenn das
     *                 Ereignis selbst das Schild abbaut
     */
    private record Betroffen(Shoptext.Teil teil, List<Kistenshop> shops,
                             List<Block> schilder) {
    }

    /**
     * Das Ergebnis von {@link #shopsAn} - siehe dort, warum es zwei Felder sind.
     *
     * @param shopkiste ob hier eine Shopkiste steht; bei stummer Ablage ja
     * @param shops     die Shops daran; leer, wenn die Ablage geschwiegen hat
     */
    private record AnDerKiste(boolean shopkiste, List<Kistenshop> shops) {
        /**
         * Geschuetzt, aber niemand nennbar - die Ablage hat geschwiegen.
         *
         * <p>Dieser Zustand ist kein Programmfehler, sondern die einzige Lage, in der
         * die zwei Richtungen der Ablage auseinandergehen. Abgesagt wird trotzdem:
         * Eine leergeraeumte Kiste bekommt niemand zurueck, ein nicht geoeffneter
         * Deckel kostet einen zweiten Versuch.
         */
        boolean gestoert() {
            return shopkiste && shops.isEmpty();
        }
    }
}
