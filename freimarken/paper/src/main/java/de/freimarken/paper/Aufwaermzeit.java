package de.freimarken.paper;

import de.freimarken.kern.Quader;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Die Wartezeit vor jedem Teleport, in der man stillstehen muss
 * [entschieden 02.11.2026: drei Sekunden, Bewegung und Schaden brechen ab].
 *
 * <p>Der Grund ist nicht die Platte, sondern das Spiel: Ohne sie ist jeder Teleport ein
 * Fluchtweg. Wer verfolgt wird, tippt {@code /warp altfurt} und ist weg; ein Kampf hat
 * dann keinen Ausgang mehr, sondern nur noch einen Abbruch. Die Abklingzeit in
 * {@link Reise} hilft dagegen nicht - sie begrenzt, wie oft man reist, nicht ob man
 * mitten im Kampf verschwindet.
 *
 * <p><b>Bleiben, nicht stillstehen.</b> Abgebrochen wird, wer den Bereich verlaesst, in
 * dem er angefangen hat - nicht, wer sich darin bewegt. Bei einem Befehl ist dieser
 * Bereich der eine Block, auf dem er steht; bei einem Tor der ganze Torquader. Der
 * Unterschied ist nicht Feinschliff: Man laeuft in ein Tor hinein, und der naechste
 * Schritt liegt noch darin. Eine Regel „ein Block" braeche genau bei dem Schritt ab, mit
 * dem man angekommen ist - und niemand kaeme je durch.
 *
 * <p>Damit verhaelt sich ein Tor wie ein Portal, und das ist die Absicht: Ein
 * Netherportal verlangt in Minecraft vier Sekunden Aufenthalt, und niemand hat das je
 * erklaert bekommen muessen.
 */
final class Aufwaermzeit implements Listener {

    /** Wie lange gewartet wird. */
    static final Duration DAUER = Duration.ofSeconds(3);

    /** Wer sofort reist - Betreiber und ihre Werkzeuge. */
    static final String RECHT_SOFORT = "freimarken.sofort";

    /**
     * Ein laufendes Aufwaermen.
     *
     * @param aufgabe  die geplante Tat, zum Abbrechen
     * @param bleibeIn der Bereich, den der Spieler nicht verlassen darf
     * @param wohin    der Name des Ziels, fuer die Meldung
     */
    private record Lauf(BukkitTask aufgabe, Quader bleibeIn, String wohin) {
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Lauf> laufende = new HashMap<>();

    /**
     * Die Saetze dieses Plugins - verdrahtet, bevor der erste Satz umzieht.
     *
     * <p>Warum alle elf Sender dieses Feld in EINEM Zug bekommen und nicht je
     * Bauschritt, steht dort, wo sie gebaut werden: {@code FreimarkenPlugin.onEnable}.
     */
    private final Sprache sprache;

    Aufwaermzeit(JavaPlugin plugin, Sprache sprache) {
        this.sprache = sprache;
        this.plugin = plugin;
    }

    /** Ob fuer diesen Spieler schon eine Wartezeit laeuft. */
    boolean laeuft(UUID wer) {
        return laufende.containsKey(wer);
    }

    /**
     * Beginnt die Wartezeit - oder laeuft sofort los.
     *
     * <p>Sofort fuer wen {@link #RECHT_SOFORT} hat: Ein Betreiber, der sich mit
     * {@code /freimarken parzelle} etwas ansehen will, soll nicht drei Sekunden
     * stillstehen, und sein Werkzeug soll nicht abbrechen, weil er sich dabei dreht.
     *
     * @param bleibeIn der Bereich, den der Spieler nicht verlassen darf
     * @param tat      was nach der Wartezeit geschieht
     */
    void beginne(Player spieler, String wohin, Quader bleibeIn, Runnable tat) {
        if (spieler.hasPermission(RECHT_SOFORT)) {
            tat.run();
            return;
        }
        UUID wer = spieler.getUniqueId();
        long takte = DAUER.toMillis() / 50L;
        BukkitTask aufgabe = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            laufende.remove(wer);
            tat.run();
        }, takte);
        laufende.put(wer, new Lauf(aufgabe, bleibeIn, wohin));
        spieler.sendMessage(Component.text("Nach " + DAUER.toSeconds()
                + " Sekunden geht es nach " + wohin + " - bis dahin stehen bleiben.",
                NamedTextColor.GRAY));
    }

    /**
     * Der Bereich, in dem ein Spieler bei einem Befehl bleiben muss: sein eigener Block.
     *
     * <p>Nicht seine genaue Stelle: Wer sich dreht, bewegt sich in Minecraft messbar, und
     * eine Regel auf Kommastellen braeche bei jedem Mausruck ab.
     */
    static Quader seinBlock(Player spieler) {
        var ort = spieler.getLocation();
        return new Quader(ort.getBlockX(), ort.getBlockY(), ort.getBlockZ(),
                ort.getBlockX(), ort.getBlockY(), ort.getBlockZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void beimSchritt(PlayerMoveEvent ereignis) {
        // Dieselbe erste Zeile wie in der Torwache, aus demselben Grund: Das Ereignis
        // feuert schon bei zehn Grad Kopfdrehung.
        if (!ereignis.hasExplicitlyChangedBlock()) {
            return;
        }
        Lauf lauf = laufende.get(ereignis.getPlayer().getUniqueId());
        if (lauf == null) {
            return;
        }
        var zu = ereignis.getTo();
        if (lauf.bleibeIn().enthaelt(zu.getBlockX(), zu.getBlockY(), zu.getBlockZ())) {
            return;
        }
        brichAb(ereignis.getPlayer(), "Bewegt - die Reise nach " + lauf.wohin()
                + " ist abgebrochen.");
    }

    /**
     * Schaden bricht ab.
     *
     * <p>Bewegung allein liesse eine Luecke: Wer eingekeilt in einer Ecke steht und
     * geschlagen wird, bewegt sich durch Rueckstoss nicht immer einen ganzen Block weit -
     * und genau dann waere der Teleport doch ein Fluchtweg.
     */
    @EventHandler(ignoreCancelled = true)
    public void beiSchaden(EntityDamageEvent ereignis) {
        if (!(ereignis.getEntity() instanceof Player spieler)) {
            return;
        }
        Lauf lauf = laufende.get(spieler.getUniqueId());
        if (lauf == null) {
            return;
        }
        brichAb(spieler, "Schaden genommen - die Reise nach " + lauf.wohin()
                + " ist abgebrochen.");
    }

    /** Beim Abmelden ohne Meldung - es liest sie niemand mehr. */
    @EventHandler
    public void beimAbmelden(PlayerQuitEvent ereignis) {
        Lauf lauf = laufende.remove(ereignis.getPlayer().getUniqueId());
        if (lauf != null) {
            lauf.aufgabe().cancel();
        }
    }

    private void brichAb(Player spieler, String grund) {
        Lauf lauf = laufende.remove(spieler.getUniqueId());
        if (lauf != null) {
            lauf.aufgabe().cancel();
            spieler.sendMessage(Component.text(grund, NamedTextColor.RED));
        }
    }

    /** Alles abbrechen - beim Abschalten des Plugins. */
    void brichAllesAb() {
        laufende.values().forEach(lauf -> lauf.aufgabe().cancel());
        laufende.clear();
    }
}
