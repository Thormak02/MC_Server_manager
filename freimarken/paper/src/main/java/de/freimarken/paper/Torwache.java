package de.freimarken.paper;

import de.freimarken.kern.Quader;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Die Tore am Bundesmarkt - der erste der beiden Reisewege aus Plan 4.3.
 *
 * <p>Ein Tor ist das, was ein Neuer findet, ohne einen Befehl zu kennen: Er sieht, wohin
 * es geht, und laeuft hin. Die Quader stehen am Eintrag der Zielwelt in der
 * {@code config.yml} und sind beim Lesen gegeneinander geprueft.
 *
 * <p>Aus Mauerwerk und nicht aus Portalbloecken. Ein gesetzter {@code NETHER_PORTAL}- oder
 * {@code END_GATEWAY}-Block erzeugt von sich aus Portale, Gateways und Chunks in anderen
 * Welten und loescht sich selbst wieder; ein falscher Quader ist dagegen eine Zeile in der
 * Einstellungsdatei.
 */
final class Torwache implements Listener {

    private final World ankunftswelt;
    private final Map<String, World> welten;
    private final List<Weltangabe> angaben;
    private final Reise reise;

    /**
     * @param ankunftswelt die Welt, in der die Tore stehen - meist Altfurt
     * @param angaben      alle Weltangaben; nur die mit Tor werden befragt
     */
    Torwache(World ankunftswelt, Map<String, World> welten, List<Weltangabe> angaben,
             Reise reise) {
        this.ankunftswelt = ankunftswelt;
        this.welten = welten;
        this.angaben = angaben.stream().filter(Weltangabe::hatTor).toList();
        this.reise = reise;
    }

    /** Ob es ueberhaupt ein Tor gibt - sonst braucht sich der Zuhoerer nicht anzumelden. */
    boolean hatTore() {
        return !angaben.isEmpty();
    }

    /**
     * Ein Schritt in ein Tor.
     *
     * <p>Die erste Zeile ist die wichtigste: CraftBukkit feuert
     * {@code PlayerMoveEvent} auch dann, wenn sich nur die Blickrichtung um zehn Grad
     * aendert - bei voellig unveraenderten Koordinaten. {@code hasExplicitlyChangedBlock}
     * sind drei Vergleiche und sortiert die ueberwaeltigende Mehrheit der Ereignisse aus,
     * bevor irgendetwas gerechnet wird. Advanced Portals filtert an dieser Stelle
     * nachweislich nicht, und Multiverse-Portals hat dasselbe Problem seit 2011.
     *
     * <p>Danach die Welt: Die Tore stehen alle in einer Welt, und ein Spieler in Kornmark
     * braucht die Quader nicht durchsucht zu bekommen.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimSchritt(PlayerMoveEvent ereignis) {
        if (!ereignis.hasExplicitlyChangedBlock()) {
            return;
        }
        Location zu = ereignis.getTo();
        if (!ankunftswelt.equals(zu.getWorld())) {
            return;
        }
        int x = zu.getBlockX();
        int y = zu.getBlockY();
        int z = zu.getBlockZ();
        for (Weltangabe angabe : angaben) {
            Quader tor = angabe.tor();
            if (!tor.enthaelt(x, y, z)) {
                continue;
            }
            World ziel = welten.get(angabe.name());
            if (ziel != null) {
                reise.fuehre(ereignis.getPlayer(), ziel, angabe.name());
            }
            return;
        }
    }
}
