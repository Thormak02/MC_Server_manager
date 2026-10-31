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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
 * <p>Gueltig nur in der eigenen Welt. Andere Welten des Servers gehen diese Wache nichts an.
 */
public final class Schutzwache implements Listener {

    /** Wer das hat, baut ueberall. Fuer das Team - die Regel selbst bleibt unangetastet. */
    public static final String UEBERALL = "freimarken.bauen.ueberall";

    /** Eine Absage je Spieler und Pause. Ohne das wird aus Abbauhalten ein Chatregen. */
    private static final long MELDEPAUSE_MS = 2_000L;

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
            Material.TURTLE_EGG, Material.SNIFFER_EGG);

    /** Ganze Blockgruppen, damit nicht jede Topfpflanze einzeln aufgezaehlt werden muss. */
    private static final List<Tag<Material>> HEIKLE_GRUPPEN = List.of(
            Tag.FLOWER_POTS, Tag.CANDLES, Tag.CANDLE_CAKES, Tag.CAULDRONS, Tag.BEDS);

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
     */
    public boolean darfHandeln(Player spieler, Location ort) {
        World gewaehlt = ort.getWorld();
        if (gewaehlt == null || !welt.equals(gewaehlt.key().asString())) {
            return true;
        }
        if (spieler.hasPermission(UEBERALL)) {
            return true;
        }
        return buch.darfBauen(welt, spieler.getUniqueId(), ort.getBlockX(), ort.getBlockZ());
    }

    /** Sagt dem Spieler, warum nicht - hoechstens einmal je Pause. */
    public void verweigere(Player spieler, Location ort) {
        long jetzt = System.currentTimeMillis();
        Long zuletzt = letzteMeldung.get(spieler.getUniqueId());
        if (zuletzt != null && jetzt - zuletzt < MELDEPAUSE_MS) {
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
        Entity ziel = ereignis.getRightClicked();
        if (!(ziel instanceof Hanging) && !(ziel instanceof ArmorStand)) {
            return;
        }
        if (unterbinden(ereignis.getPlayer(), ziel.getLocation())) {
            ereignis.setCancelled(true);
        }
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
