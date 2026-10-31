package de.freimarken.paper;

import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Rasterplan;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
// Liegt in .entity, nicht in .player - anders als sein Gegenstueck PlayerUnleashEntityEvent.
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Der Schutz der Parzellen.
 *
 * <p>Die Regel steht genau einmal da: {@link #darfHandeln}. Alles andere in dieser Klasse
 * sind Zugaenge zu dieser einen Regel - Bloecke setzen, Bloecke abbauen, Eimer, Feuer,
 * Bilderrahmen, Tuerklinken. Es gibt viele Wege, fremden Grund zu veraendern, und jeder
 * einzelne davon muss dieselbe Antwort bekommen. Haette jeder Weg seine eigene Pruefung,
 * waere einer davon irgendwann anders - und das waere die Luecke.
 *
 * <p>Noch NICHT abgedeckt, bewusst benannt statt stillschweigend offen gelassen: TNT und
 * andere Explosionen, Feuerausbreitung, Kolben, die ueber eine Grenze schieben, Wasser- und
 * Lavafluss von aussen, Enderman und Ziegen, Trichter, die in fremde Kisten greifen. Das
 * sind Weltregeln, keine Spielerhandlungen, und sie gehoeren zu einem eigenen Schritt.
 *
 * <p>Diese Liste hat einmal zu viel versprochen: Sie fuehrte nur Weltregeln auf und liess
 * damit den Eindruck entstehen, Spielerhandlungen seien vollstaendig gedeckt. Es fehlte
 * jedes Schadensereignis - ein Ruestungsstaender erbt von LivingEntity und stirbt durch
 * Schlagen, ein bestueckter Bilderrahmen gibt beim ersten Treffer seinen Inhalt her, ohne
 * dass der Rahmen selbst entfernt wird. Beides war mit der Faust zu holen. Daher jetzt
 * {@link #beimWesenSchaden} und {@link #beimFahrzeugSchaden}.
 *
 * <p>Gueltig nur in der eigenen Welt. Andere Welten des Servers gehen diese Wache nichts an.
 */
public final class Schutzwache implements Listener {

    /** Wer das hat, baut ueberall. Fuer das Team - die Regel selbst bleibt unangetastet. */
    public static final String UEBERALL = "freimarken.bauen.ueberall";

    /** Eine Absage je Spieler und Pause. Ohne das wird aus Abbauhalten ein Chatregen. */
    private static final long MELDEPAUSE_MS = 2_000L;

    /** So selten wird ein Ausfall der Ablage ins Protokoll geschrieben. */
    private static final long KLAGEPAUSE_MS = 30_000L;

    /**
     * Bloecke, deren Anklicken etwas veraendert, die aber weder Behaelter noch
     * {@link Openable} noch {@link Powerable} sind.
     *
     * <p>Das ist die Stelle, an der nachgetragen wird, wenn doch noch etwas durchrutscht.
     */
    private static final Set<Material> HEIKEL = Set.of(
            Material.COMPOSTER, Material.RESPAWN_ANCHOR, Material.DRAGON_EGG,
            Material.BEEHIVE, Material.BEE_NEST, Material.SWEET_BERRY_BUSH,
            Material.BELL, Material.CAKE, Material.NOTE_BLOCK, Material.JUKEBOX,
            Material.LECTERN, Material.COMPARATOR, Material.REPEATER,
            Material.DAYLIGHT_DETECTOR, Material.TNT, Material.FARMLAND,
            Material.TURTLE_EGG, Material.SNIFFER_EGG,
            // Nachgetragen: Keines davon ist Behaelter, Openable oder Powerable. Das
            // Leuchtfeuer liess seine Wirkung umstellen, auf dem Lagerfeuer liess sich
            // fremdes Fleisch braten - und ein nicht gewachstes Schild auf einer
            // Ladenparzelle liess jeder Besucher umschreiben, samt der Preise darauf.
            Material.BEACON, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.DECORATED_POT, Material.CHISELED_BOOKSHELF);

    /** Ganze Blockgruppen, damit nicht jede Topfpflanze einzeln aufgezaehlt werden muss. */
    private static final List<Tag<Material>> HEIKLE_GRUPPEN = List.of(
            Tag.FLOWER_POTS, Tag.CANDLES, Tag.CANDLE_CAKES, Tag.CAULDRONS, Tag.BEDS,
            // ALL_SIGNS deckt Steh- und Wandschilder samt Haengeschildern in allen
            // Holzarten ab; CAVE_VINES die Leuchtbeeren, die sonst jeder abernten konnte.
            Tag.ALL_SIGNS, Tag.CAVE_VINES);

    /**
     * Gegenstaende, die einen Block veraendern, ohne dass ein Setz- oder Abbauereignis
     * ausgeloest wird - Hacke, Schaufel, Axt, Feuerzeug, Knochenmehl und Verwandte.
     */
    private static final Set<Material> WERKZEUG = Set.of(
            Material.FLINT_AND_STEEL, Material.FIRE_CHARGE, Material.BONE_MEAL,
            Material.ARMOR_STAND, Material.END_CRYSTAL, Material.ITEM_FRAME,
            Material.GLOW_ITEM_FRAME, Material.PAINTING, Material.LEAD,
            Material.GLASS_BOTTLE, Material.SHEARS, Material.HONEYCOMB,
            Material.INK_SAC, Material.GLOW_INK_SAC);

    private final Besitzbuch buch;

    /**
     * Die Welt als Schluesseltext, etwa {@code freimarken:marktstadt}.
     *
     * <p>Bewusst als Text und nicht als {@code NamespacedKey}: {@code World.key()} liefert
     * ein {@code Key}, und {@code NamespacedKey.equals(Key)} ist immer falsch. Dieser
     * Vergleich wuerde uebersetzen und jede Welt fuer fremd halten - der Schutz waere
     * lautlos aus. Derselbe Text steht im Besitzbuch und in der Datenbank, also gibt es nur
     * eine Darstellung und nichts umzurechnen.
     */
    private final String welt;

    private final Map<UUID, Long> letzteMeldung = new HashMap<>();
    private long letzteKlage;

    public Schutzwache(Besitzbuch buch, String welt) {
        this.buch = buch;
        this.welt = welt;
    }

    // --- Die eine Regel ---------------------------------------------------------

    /**
     * Darf dieser Spieler an dieser Stelle etwas veraendern?
     *
     * <p>Antwortet {@code true} fuer alles ausserhalb der eigenen Welt: Dort ist diese Wache
     * nicht zustaendig, und "nicht zustaendig" heisst nicht "verboten".
     *
     * <p>Fehlt die Antwort, lautet sie nein. Wer nicht nachsehen kann, wem der Grund
     * gehoert, darf dort nicht bauen. Ohne diesen Fang flog eine Ausnahme aus der
     * Datenschicht - Netz weg, Zeitgrenze erreicht - aus dem Ereignishandler heraus; Bukkit
     * schreibt sie ins Protokoll und laesst das Ereignis UNABGEBROCHEN. Ein Aussetzer der
     * Datenbank waere damit eine Luecke im Besitzschutz gewesen, und zwar die unauffaellige
     * Sorte: Gebaut wird, der Eintrag im Protokoll liest sich wie ein technisches Problem.
     */
    public boolean darfHandeln(Player spieler, Location ort) {
        World gewaehlt = ort.getWorld();
        if (gewaehlt == null || !welt.equals(gewaehlt.key().asString())) {
            return true;
        }
        if (spieler.hasPermission(UEBERALL)) {
            return true;
        }
        try {
            return buch.darfBauen(welt, spieler.getUniqueId(), ort.getBlockX(), ort.getBlockZ());
        } catch (RuntimeException fehler) {
            klageUeberAblage(fehler);
            return false;
        }
    }

    /**
     * Meldet einen Ausfall der Ablage - hoechstens einmal je Pause.
     *
     * <p>Gedrosselt, weil der Fall bei jedem Blockereignis eintreten wuerde: Ohne Drossel
     * schreibt ein ausgefallener Datenbankrechner in Sekunden ein Protokoll voll und
     * verdeckt damit jede andere Meldung.
     */
    private void klageUeberAblage(RuntimeException fehler) {
        long jetzt = System.nanoTime();
        if (letzteKlage != 0L && (jetzt - letzteKlage) / 1_000_000L < KLAGEPAUSE_MS) {
            return;
        }
        letzteKlage = jetzt;
        Bukkit.getLogger().warning("[Freimarken] Besitz nicht nachsehbar, es wird abgelehnt: "
                + fehler.getMessage());
    }

    /**
     * Sagt dem Spieler, warum nicht - hoechstens einmal je Pause.
     *
     * <p>nanoTime und nicht currentTimeMillis: Die Wanduhr laeuft nicht monoton. Wird sie
     * zurueckgestellt - Zeitumstellung, Zeitabgleich - ist die Differenz negativ und damit
     * immer kleiner als die Pause. Die Begruendung bliebe dann dauerhaft stumm, und der
     * Spieler saehe nur, dass nichts passiert.
     */
    public void verweigere(Player spieler, Location ort) {
        long jetzt = System.nanoTime();
        Long zuletzt = letzteMeldung.get(spieler.getUniqueId());
        if (zuletzt != null && (jetzt - zuletzt) / 1_000_000L < MELDEPAUSE_MS) {
            return;
        }
        letzteMeldung.put(spieler.getUniqueId(), jetzt);
        spieler.sendActionBar(grund(ort));
    }

    private Component grund(Location ort) {
        Optional<Parzelle> parzelle = buch.parzelleAn(welt, ort.getBlockX(), ort.getBlockZ());
        if (parzelle.isEmpty()) {
            boolean bauland = buch.plan().feldBei(ort.getBlockX(), ort.getBlockZ())
                    == Rasterplan.Feld.PARZELLE;
            return bauland
                    ? Component.text("Diese Parzelle ist frei - /parzelle nehmen",
                            NamedTextColor.YELLOW)
                    : Component.text("Weg und Mauer gehoeren allen", NamedTextColor.YELLOW);
        }
        return Component.text("Diese Parzelle gehoert ", NamedTextColor.RED)
                .append(Component.text(namen(parzelle.get().eigentuemer()), NamedTextColor.WHITE));
    }

    private static String namen(UUID spieler) {
        String name = Bukkit.getOfflinePlayer(spieler).getName();
        return name != null ? name : spieler.toString().substring(0, 8);
    }

    /** Pruefen und bei Bedarf absagen - was jeder Zugang unten tut. */
    private boolean unterbinden(Player spieler, Location ort) {
        if (darfHandeln(spieler, ort)) {
            return false;
        }
        verweigere(spieler, ort);
        return true;
    }

    /** Wie {@link #unterbinden}, nur ohne Meldung - fuer alles, was der Spieler nicht wollte. */
    private boolean unterbindenLeise(Player spieler, Location ort) {
        return !darfHandeln(spieler, ort);
    }

    /**
     * Raeumt die Meldepause eines Spielers weg, der gegangen ist.
     *
     * <p>Sonst waechst die Tabelle mit jedem Spieler, der je da war - langsam, aber
     * unbegrenzt, und niemand sieht es, weil es ja funktioniert.
     */
    @EventHandler
    public void beimVerlassen(PlayerQuitEvent ereignis) {
        letzteMeldung.remove(ereignis.getPlayer().getUniqueId());
    }

    // --- Die Zugaenge ----------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void beimAbbauen(BlockBreakEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), ereignis.getBlock().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /** Faengt auch {@code BlockMultiPlaceEvent} - Betten, Tueren, grosse Blumen. */
    @EventHandler(ignoreCancelled = true)
    public void beimSetzen(BlockPlaceEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), ereignis.getBlock().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void beimAusgiessen(PlayerBucketEmptyEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), ereignis.getBlock().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void beimAufschoepfen(PlayerBucketFillEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), ereignis.getBlock().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /** Zweiter Riegel gegen Feuerzeug und Feuerkugel, falls der Klickweg etwas durchlaesst. */
    @EventHandler(ignoreCancelled = true)
    public void beimAnzuenden(BlockIgniteEvent ereignis) {
        Player spieler = ereignis.getPlayer();
        if (spieler != null && unterbinden(spieler, ereignis.getBlock().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void beimBildAbnehmen(HangingBreakByEntityEvent ereignis) {
        if (ereignis.getRemover() instanceof Player spieler
                && unterbinden(spieler, ereignis.getEntity().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void beimBildAufhaengen(HangingPlaceEvent ereignis) {
        Player spieler = ereignis.getPlayer();
        if (spieler != null && unterbinden(spieler, ereignis.getEntity().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Ruestungsstaender und Bilderrahmen.
     *
     * <p>Beides sind Wesen, keine Bloecke - sie stehen aber auf fremdem Grund und gehoeren
     * zu dem, was dort gebaut wurde. {@code PlayerArmorStandManipulateEvent} kommt hier mit
     * durch, weil es von diesem Ereignis erbt.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimWesenAnklicken(PlayerInteractEntityEvent ereignis) {
        if (gehoertZumGrundstueck(ereignis.getRightClicked())
                && unterbinden(ereignis.getPlayer(), ereignis.getRightClicked().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Ist dieses Wesen Teil dessen, was auf der Parzelle steht?
     *
     * <p>{@link InventoryHolder} ist der Grund, warum hier ueberhaupt etwas zu holen ist:
     * Eine Kisten-Lore und ein Truhenboot tragen genau dasselbe Merkmal, an dem
     * {@link #istHeikel} einen Behaelterblock erkennt - nur sind sie Wesen und fielen
     * deshalb durch einen Filter, der nur Hanging und ArmorStand kannte. Wer sie als Lager
     * auf seine Parzelle stellt, stellte sie jedem offen.
     *
     * <p>Tiere sind mit dabei: Sie gehoeren zwar niemandem im Sinne des Besitzbuchs, stehen
     * aber im Gehege des Eigentuemers. Ein Besucher, der sie fortleint oder erschlaegt,
     * nimmt ihm etwas weg.
     */
    private static boolean gehoertZumGrundstueck(Entity wesen) {
        return wesen instanceof Hanging
                || wesen instanceof ArmorStand
                || wesen instanceof Vehicle
                || wesen instanceof InventoryHolder
                || wesen instanceof Animals;
    }

    /**
     * Schaden an dem, was auf der Parzelle steht.
     *
     * <p>Das war die groesste Luecke: Ein Ruestungsstaender erbt von LivingEntity, ist also
     * KEIN Hanging - er stirbt durch Schlagen und nicht durch einen Hanging-Break. Und ein
     * bestueckter Bilderrahmen gibt beim ersten Treffer seinen Inhalt her, ohne dass der
     * Rahmen selbst entfernt wird; auch dabei faellt kein HangingBreakByEntityEvent. Beides
     * war mit der Faust zu holen, ohne Meldung und ohne Protokolleintrag.
     *
     * <p>Der Schlaeger kann ein Pfeil oder ein Schneeball sein - dann steckt der Spieler im
     * Schuetzen, nicht im Angreifer.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimWesenSchaden(EntityDamageByEntityEvent ereignis) {
        if (!gehoertZumGrundstueck(ereignis.getEntity())) {
            return;
        }
        Player taeter = urheber(ereignis.getDamager());
        if (taeter != null && unterbinden(taeter, ereignis.getEntity().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /** Loren und Boote; faengt auch {@link VehicleDestroyEvent}, weil es davon erbt. */
    @EventHandler(ignoreCancelled = true)
    public void beimFahrzeugSchaden(VehicleDamageEvent ereignis) {
        Player taeter = urheber(ereignis.getAttacker());
        if (taeter != null && unterbinden(taeter, ereignis.getVehicle().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /** Tiere mit dem Eimer aus dem Teich schoepfen - Axolotl, Kaulquappe, Fisch. */
    @EventHandler(ignoreCancelled = true)
    public void beimWesenSchoepfen(PlayerBucketEntityEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), ereignis.getEntity().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /** Fremde Tiere fortleinen. */
    @EventHandler(ignoreCancelled = true)
    public void beimLeinen(PlayerLeashEntityEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), ereignis.getEntity().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /** Und fremde Tiere losmachen, damit sie weglaufen. */
    @EventHandler(ignoreCancelled = true)
    public void beimEntleinen(PlayerUnleashEntityEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), ereignis.getEntity().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Der Spieler hinter einem Schaden - direkt oder als Schuetze hinter einem Geschoss.
     *
     * @return {@code null}, wenn kein Spieler dahintersteht; dann ist es eine Weltregel und
     *         nicht Sache dieser Wache
     */
    private static Player urheber(Entity schlaeger) {
        if (schlaeger instanceof Player spieler) {
            return spieler;
        }
        if (schlaeger instanceof Projectile geschoss
                && geschoss.getShooter() instanceof Player schuetze) {
            return schuetze;
        }
        return null;
    }

    /**
     * Klicken auf Bloecke: Kisten, Tueren, Hebel - und Werkzeug, das den Block veraendert.
     *
     * <p>Absichtlich nicht jeder Rechtsklick: Wer auf fremdem Grund steht und Brot isst,
     * klickt auch auf einen Block. Unterbunden wird, was den Block oder seinen Inhalt
     * angeht, nicht was der Spieler mit sich selbst tut.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimKlicken(PlayerInteractEvent ereignis) {
        Block block = ereignis.getClickedBlock();
        if (block == null) {
            return;
        }
        if (ereignis.getAction() == Action.PHYSICAL) {
            // Draufgetreten, nicht angeklickt - unterbinden, aber ohne Meldung. Sonst
            // bekommt ein Spieler, der ueber fremdes Ackerland laeuft, bei jedem Schritt
            // eine Absage fuer etwas, das er nicht vorhatte.
            if (unterbindenLeise(ereignis.getPlayer(), block.getLocation())) {
                ereignis.setCancelled(true);
            }
            return;
        }
        if (ereignis.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Der Linksklick war gar nicht geprueft. Fuer das Abbauen genuegt
            // BlockBreakEvent, aber nicht alles am Linksklick ist ein Abbau: Ein Drachenei
            // springt weg, ein Feuer wird ausgeschlagen, ein Gemaelde faellt. Nur bei
            // heiklen Bloecken eingreifen, sonst bliebe kein normales Abbauen moeglich.
            if (istHeikel(block) && unterbinden(ereignis.getPlayer(), block.getLocation())) {
                ereignis.setCancelled(true);
            }
            return;
        }
        if (ereignis.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        boolean blockHeikel = istHeikel(block);
        boolean werkzeugHeikel = aendertBlock(ereignis.getItem());
        if (!blockHeikel && !werkzeugHeikel) {
            return;
        }
        if (darfHandeln(ereignis.getPlayer(), block.getLocation())) {
            return;
        }
        verweigere(ereignis.getPlayer(), block.getLocation());
        // Getrennt abgesagt: Der Block bleibt in jedem Fall unberuehrt, die Hand nur dann,
        // wenn sie etwas veraendert haette.
        ereignis.setUseInteractedBlock(Event.Result.DENY);
        if (werkzeugHeikel) {
            ereignis.setUseItemInHand(Event.Result.DENY);
        }
    }

    private static boolean istHeikel(Block block) {
        // getState(false): ohne Abbild des Blockzustands. Diese Pruefung laeuft bei jedem
        // Rechtsklick, da ist eine Kopie des Kisteninhalts zu viel Aufwand fuer eine Frage,
        // die nur "ist das ein Behaelter" lautet.
        if (block.getState(false) instanceof InventoryHolder) {
            return true;
        }
        BlockData daten = block.getBlockData();
        if (daten instanceof Openable || daten instanceof Powerable) {
            return true;
        }
        if (HEIKEL.contains(block.getType())) {
            return true;
        }
        for (Tag<Material> gruppe : HEIKLE_GRUPPEN) {
            if (gruppe.isTagged(block.getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean aendertBlock(ItemStack gegenstand) {
        if (gegenstand == null) {
            return false;
        }
        Material art = gegenstand.getType();
        // Der einzige Eimer, der keinen Block anfasst. Ohne diese Zeile liess sich auf
        // fremdem Grund keine Milch trinken - eine Absage fuer etwas, das niemandem schadet.
        if (art == Material.MILK_BUCKET) {
            return false;
        }
        if (art.isBlock() && !art.isAir()) {
            return true;
        }
        if (WERKZEUG.contains(art)) {
            return true;
        }
        String name = art.name();
        return name.endsWith("_HOE") || name.endsWith("_SHOVEL") || name.endsWith("_AXE")
                || name.endsWith("_BUCKET") || name.endsWith("_SPAWN_EGG")
                || name.endsWith("_DYE");
    }
}
