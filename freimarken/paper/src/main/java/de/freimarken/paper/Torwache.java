package de.freimarken.paper;

import de.freimarken.kern.Quader;
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
    /**
     * Das Torbuch selbst und keine Abschrift seiner Tore [25.11.2026].
     *
     * <p>Bis dahin bekam diese Wache eine fertige Liste in den Konstruktor.
     * Mit
     * {@code /portal setzen} ist das falsch: Die Liste waere die vom Start, und ein
     * im Spiel gesetztes Tor wirkte erst nach einem Neustart. Das Buch wird darum
     * bei JEDEM Schritt gefragt - es ist eine Karte im Speicher, kein Dateizugriff.
     */
    private final Torbuch torbuch;
    private final Reise reise;

    /**
     * @param ankunftswelt die Welt, in der die Tore stehen - meist Altfurt
     * @param torbuch      die Tore, lebend gefragt
     */
    Torwache(World ankunftswelt, Map<String, World> welten, Torbuch torbuch,
             Reise reise) {
        this.ankunftswelt = ankunftswelt;
        this.welten = welten;
        this.torbuch = torbuch;
        this.reise = reise;
    }

    /**
     * Ob es ueberhaupt ein Tor gibt.
     *
     * <p>Nur noch fuer die Startmeldung. Angemeldet wird der Zuhoerer seit dem
     * 25.11.2026 IMMER: Haengt die Anmeldung daran, dass beim Start schon ein Tor
     * stand, dann wirkt das erste mit {@code /portal setzen} gesetzte Tor nicht -
     * und niemand kaeme auf den Gedanken, dass die Wache fehlt.
     */
    boolean hatTore() {
        return torbuch.hatTore();
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
        for (Map.Entry<String, Torbuch.Tor> eintrag : torbuch.alle().entrySet()) {
            Quader tor = eintrag.getValue().quader();
            if (!tor.enthaelt(x, y, z)) {
                continue;
            }
            World ziel = welten.get(eintrag.getKey());
            if (ziel != null) {
                // Der Torquader als Aufwaermbereich: Man laeuft in ein Tor hinein, und
                // der naechste Schritt liegt noch darin. Mit „ein Block" braeche die
                // Wartezeit genau bei dem Schritt ab, mit dem man angekommen ist.
                reise.fuehre(ereignis.getPlayer(), ziel, eintrag.getKey(), tor);
            }
            return;
        }
    }
}
