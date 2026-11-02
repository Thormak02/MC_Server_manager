package de.freimarken.paper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Zwei Dinge, die {@link Reise} nicht mitbekommt: der Tod und das Abmelden.
 *
 * <p><b>Der Tod.</b> Jeder Teleport merkt sich den Rueckweg selbst, in {@code Reise} -
 * nur das Sterben nicht, denn das ist kein Teleport. Und der Weg zurueck muss hier
 * entstehen und nicht beim Erscheinen: Das Erscheinen darf <b>nicht</b> durch
 * {@code Reise} laufen, sonst merkte es sich den Ankunftsort und ueberschriebe damit
 * genau die Todesstelle, zu der {@code /zurueck} fuehren soll.
 *
 * <p>Ohne Ausnahme fuer einen Tod durch einen anderen Spieler [entschieden 02.11.2026].
 * Die Gegenfassung ist verbreitet, braucht aber eine Abfrage des Toeters, ein
 * Umgehungsrecht und eine Erklaerung - und solange es keinen Kampfmodus und keine Arena
 * gibt, waere das Vorbauen. Falls PvP kommt, ist die Stelle eine Zeile hier:
 * {@code ereignis.getEntity().getKiller() == null}.
 *
 * <p><b>Das Abmelden.</b> Rueckweg und offene Anfragen werden vergessen. Beim Rueckweg
 * haelt das die Karte so gross wie die Zahl der anwesenden Spieler; bei den Anfragen
 * verhindert es, dass jemand die Anfrage eines Spielers annimmt, der nicht mehr da ist.
 */
final class Rueckwegwache implements Listener {

    private final Rueckweg rueckweg;
    private final Anfragen anfragen;

    Rueckwegwache(Rueckweg rueckweg, Anfragen anfragen) {
        this.rueckweg = rueckweg;
        this.anfragen = anfragen;
    }

    /**
     * Merkt die Todesstelle.
     *
     * <p>Ohne {@code ignoreCancelled}: Ein Tod ist nicht abbrechbar, und das Ereignis
     * ist es auch nicht.
     */
    @EventHandler
    public void beimTod(PlayerDeathEvent ereignis) {
        rueckweg.merken(ereignis.getEntity().getUniqueId(),
                ereignis.getEntity().getLocation());
    }

    @EventHandler
    public void beimAbmelden(PlayerQuitEvent ereignis) {
        rueckweg.vergessen(ereignis.getPlayer().getUniqueId());
        anfragen.vergessen(ereignis.getPlayer().getUniqueId());
    }
}
