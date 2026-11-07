package de.freimarken.paper;

import de.freimarken.kern.Lebenszeichen;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Die Bruecke zwischen dem Spiel und der {@link Messstelle} - sechs Ereigniszweige.
 *
 * <p>Eine EIGENE Wache und kein Zweig in {@link Verdienstwache} oder {@link Setzwache},
 * obwohl zwei Ereignisse dieselben sind. Der Grund ist, dass hier etwas anderes gezaehlt
 * wird: Die Verdienstwache zahlt nur fuer einen Block, der noch keine Marke hat, in einer
 * Welt mit Loehnen, fuer einen Spieler mit dem passenden Beruf, unter dem Tagesdeckel und
 * unter der Stundenmenge. Jede dieser Bedingungen ist richtig fuer GELD und falsch fuer
 * eine MESSUNG: Wer seinen eigenen Keller abraeumt, verdient nichts und tut trotzdem
 * etwas. Haenge die Messung an der Lohnzahlung, miede sie genau die Spieler, die bezahlt
 * schon genug bekommen haben.
 *
 * <p><b>Das Bewegen fehlt hier.</b> Es wird nicht gemeldet, sondern aus dem Abstand
 * gemessen - siehe {@link Messstelle}. Ein Zuhoerer auf {@code PlayerMoveEvent} waere der
 * heisseste Zweig des ganzen Plugins (er feuert bei jeder Kopfdrehung) und wuerde dabei
 * eine gewackelte Maus als Bewegung zaehlen.
 *
 * <p><b>{@code ignoreCancelled = true} auf allen Blockzweigen.</b> Was der Besitzschutz
 * abgelehnt hat, ist nicht geschehen. Haette ein abgelehnter Versuch gezaehlt, waere das
 * Dauerschlagen auf eine fremde Mauer ein Lebenszeichen - und zwar das einzige, das keine
 * Wirkung hat und darum beliebig oft geht.
 */
final class Messwache implements Listener {

    private final JavaPlugin plugin;
    private final Messstelle messstelle;

    Messwache(JavaPlugin plugin, Messstelle messstelle) {
        this.plugin = plugin;
        this.messstelle = messstelle;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimAbbauen(BlockBreakEvent ereignis) {
        messstelle.melde(ereignis.getPlayer().getUniqueId(), Lebenszeichen.ABBAUEN);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimSetzen(BlockPlaceEvent ereignis) {
        messstelle.melde(ereignis.getPlayer().getUniqueId(), Lebenszeichen.SETZEN);
    }

    /**
     * Eine geoeffnete Oberflaeche - das dritte Signal aus Plan 8.8.
     *
     * <p>{@code getPlayer()} gibt eine {@code HumanEntity} und nicht einen
     * {@code Player}: Ein Inventar kann auch fuer ein anderes Wesen geoeffnet sein. Darum
     * die Pruefung - sie ist keine Vorsicht, sondern die Bedingung dafuer, dass hier eine
     * UUID eines SPIELERS herauskommt.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimOeffnen(InventoryOpenEvent ereignis) {
        if (ereignis.getPlayer() instanceof Player spieler) {
            messstelle.melde(spieler.getUniqueId(), Lebenszeichen.OEFFNEN);
        }
    }

    /**
     * Ein benutzter Block oder Gegenstand - und zwar NUR mit dem rechten Knopf.
     *
     * <p>Der linke Knopf ist hier ausgeschlossen, und daran haengt die halbe Messung:
     * {@code PlayerInteractEvent} feuert bei jedem Schlag mit
     * {@code LEFT_CLICK_BLOCK} oder {@code LEFT_CLICK_AIR}. Zaehlte das als Benutzen,
     * bekaeme ein Makro, das an einem Pflastersteingenerator die linke Taste haelt, zwei
     * Lebenszeichen - Graben UND Benutzen - und waere taetig. Es gaebe damit genau das
     * Muster, das diese Messstelle erkennen soll, und zwar stumm.
     *
     * <p>{@code PHYSICAL} - also eine Druckplatte oder ein Stolperdraht - zaehlt ebenso
     * nicht: Darauf tritt man beim Gehen, und das Gehen hat sein eigenes Zeichen.
     *
     * <p>Diese Art steht ab Werk NICHT in der Liste
     * ({@link de.freimarken.kern.Messwerte#VORGABE_ARTEN}), weil sie die halbe Art zum
     * Setzen ist. Gemeldet wird sie trotzdem: Gefiltert wird beim Urteil, damit ein
     * Neuladen die Liste erreicht und der Bericht auch die abgeschalteten Arten nennt.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimBenutzen(PlayerInteractEvent ereignis) {
        if (!ereignis.getAction().isRightClick()) {
            return;
        }
        messstelle.melde(ereignis.getPlayer().getUniqueId(), Lebenszeichen.BENUTZEN);
    }

    /**
     * Ein Angriff auf ein Wesen.
     *
     * <p>Gezaehlt wird der ANGREIFER und nur, wenn er ein Spieler ist. Nicht der
     * Geschlagene: Wer in einer Mobfarm steht und Schaden nimmt, tut nichts - und genau
     * das ist der Fall, den die Messung nicht als Taetigkeit lesen darf.
     *
     * <p>Kein {@code ignoreCancelled}: Ein abgewehrter Schlag - Schild, Unverwundbarkeit,
     * ein anderes Plugin - ist trotzdem ein Schlag des Spielers. Anders als beim Block
     * gibt es hier keinen Weg, folgenlos und beliebig oft zu schlagen; die Reichweite und
     * die Schlagpause des Spiels begrenzen es, und ein Wesen muss ueberhaupt da sein.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void beimSchlagen(EntityDamageByEntityEvent ereignis) {
        messstelle.melde(angreifer(ereignis.getDamager()), Lebenszeichen.KAEMPFEN);
    }

    /**
     * Wer geschlagen HAT, wenn es ein Spieler war - sonst {@code null}.
     *
     * <p>Eine eigene Stelle und kein {@code instanceof} im Zweig, weil der Zweig ohne
     * Server nicht zu durchlaufen ist: {@code EntityDamageByEntityEvent} bricht schon in
     * seiner Klassenvorbereitung ab - gemessen am 07.11.2026 mit
     * {@code ExceptionInInitializerError}, weil ein statisches Feld dort das Verzeichnis
     * des Servers braucht. Dieselbe Entscheidung wie bei
     * {@code Spielzeitwache.untaetigkeitVon}: Was hinter einer Bukkit-Wand liegt, bekommt
     * einen Griff, den eine Probe anfassen kann.
     *
     * <p>Gefragt wird nach dem SCHLAEGER und nicht nach dem Geschlagenen. Wer in einer
     * Mobfarm steht und Schaden nimmt, tut nichts - und genau das ist der Fall, den die
     * Messung nicht als Taetigkeit lesen darf.
     */
    static UUID angreifer(org.bukkit.entity.Entity schlaeger) {
        return schlaeger instanceof Player spieler ? spieler.getUniqueId() : null;
    }

    /** Ein Befehl - der sichere Teil des Sprechens, denn er kommt im Hauptfaden. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimBefehl(PlayerCommandPreprocessEvent ereignis) {
        messstelle.melde(ereignis.getPlayer().getUniqueId(), Lebenszeichen.SPRECHEN);
    }

    /**
     * Eine Chatzeile - und der EINZIGE Umweg ueber den Zeitgeber im ganzen Plugin.
     *
     * <p>Paper liefert den Chat eines Spielers ausserhalb des Hauptfadens aus; dieser
     * Zweig laeuft also nicht dort, wo die Messstelle lebt. Ein direkter Aufruf waere
     * genau der zweite Faden auf einer nackten {@code HashMap}, den {@link Spielzeitbuch}
     * im Klassenkopf ausschliesst - im schlechten Fall keine verlorene Meldung, sondern
     * eine zerstoerte Karte.
     *
     * <p>{@code runTask} und nicht die synchrone Fassung {@code ChatEvent}: Welche der
     * beiden Paper fuer eine gesprochene Zeile wirklich auslaest, haengt am Faden, auf dem
     * die Zeile verarbeitet wird, und ist ohne Server und ohne Spieler nicht zu messen.
     * Dieser Weg gilt fuer beide.
     *
     * <p>Der Umweg kostet einen Tick, und das ist bedeutungslos: Das Fenster, in das die
     * Meldung fallen soll, ist eine Taktlaenge - zehn Sekunden im kuerzesten Fall, also
     * zweihundert Ticks. Nur eine Zeile, die im LETZTEN Tick vor der Abrechnung gesprochen
     * wird, landet im naechsten Fenster.
     *
     * <p>Die Pruefung auf {@code isEnabled} muss sein: Ein Zeitgeber, der nach dem
     * Abschalten gestellt wird, wirft - und das waere eine Ausnahme im Chatzweig beim
     * Herunterfahren des Servers.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void beimSprechen(AsyncChatEvent ereignis) {
        UUID wer = ereignis.getPlayer().getUniqueId();
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin,
                () -> messstelle.melde(wer, Lebenszeichen.SPRECHEN));
    }
}
