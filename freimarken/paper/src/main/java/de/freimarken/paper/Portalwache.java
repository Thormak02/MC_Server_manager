package de.freimarken.paper;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;

/**
 * Selbstgebaute Nether- und Endportale - gesperrt [entschieden 02.11.2026].
 *
 * <p>Plan 4.3 entscheidet zwei Reisewege, keinen dritten. Ein funktionierendes
 * Vanilla-Portal waere der dritte, und zwar der schlechteste: Im Quelltext von CraftBukkit
 * ist das Ziel eines Netherportals der feste Vanilla-Schluessel, nicht
 * {@code freimarken:nether}. Ein Spieler, der in Altfurt ein Portal anzuendet, landet also
 * in {@code minecraft:the_nether} - einer Welt ohne Weltgrenze, ohne Schutzwache und ohne
 * Vorgenerierung, in der er endlos Gelaende erzeugt und aus der ihn nichts zurueckholt.
 *
 * <p>Das Ende liesse sich auch nicht sauber umlenken: Der Rueckweg feuert fuer Spieler kein
 * {@code PlayerPortalEvent}, und die Obsidianplattform entsteht im Haupt-Ende noch vor dem
 * Ereignis - sie blieb als Narbe in einer fremden Welt stehen.
 *
 * <p>Gesperrt wird an drei Stellen, weil es drei Wege sind: das Anzuenden, das Betreten und
 * die Reise selbst. Jede Sperre kommt mit einer Meldung - ohne sie ist das Fehlerbild ein
 * Rahmen, der sich nicht anzuenden laesst, und das sieht wie ein Serverfehler aus.
 */
final class Portalwache implements Listener {

    /**
     * Abstand zwischen zwei Meldungen an denselben Spieler.
     *
     * <p>{@code EntityPortalEnterEvent} feuert, solange der Spieler im Portalblock steht -
     * ohne Sperre waere die Erklaerung eine Wand aus Text.
     */
    private static final Duration ZWISCHEN_ZWEI_MELDUNGEN = Duration.ofSeconds(8);

    private final Set<String> eigeneWelten;
    private final Abklingzeit meldungen = new Abklingzeit(ZWISCHEN_ZWEI_MELDUNGEN);

    /**
     * Die Saetze dieses Plugins - verdrahtet, bevor der erste Satz umzieht.
     *
     * <p>Warum alle elf Sender dieses Feld in EINEM Zug bekommen und nicht je
     * Bauschritt, steht dort, wo sie gebaut werden: {@code FreimarkenPlugin.onEnable}.
     */
    private final Sprache sprache;

    /**
     * @param eigeneWelten die Schluesseltexte der Freimarken-Welten; nur dort wird gesperrt
     */
    Portalwache(Set<String> eigeneWelten, Sprache sprache) {
        this.sprache = sprache;
        this.eigeneWelten = eigeneWelten;
    }

    /** Der Rahmen laesst sich nicht anzuenden. */
    @EventHandler(ignoreCancelled = true)
    public void beimAnzuenden(PortalCreateEvent ereignis) {
        if (!gehoertUns(ereignis.getWorld())) {
            return;
        }
        if (ereignis.getReason() != PortalCreateEvent.CreateReason.FIRE) {
            // NETHER_PAIR entsteht als Gegenstueck zu einem schon bestehenden Portal, END
            // beim Erwecken des Endes. Beides faellt hier nicht an, solange das Anzuenden
            // gesperrt ist - und was nicht anfaellt, wird nicht gesperrt.
            return;
        }
        ereignis.setCancelled(true);
        if (ereignis.getEntity() instanceof Player spieler) {
            erklaere(spieler);
        }
    }

    /** Ein bestehendes Portal - etwa aus einer Sicherung - laesst niemanden durch. */
    @EventHandler(ignoreCancelled = true)
    public void beimBetreten(EntityPortalEnterEvent ereignis) {
        if (!(ereignis.getEntity() instanceof Player spieler)
                || !gehoertUns(spieler.getWorld())) {
            return;
        }
        ereignis.setCancelled(true);
        erklaere(spieler);
    }

    /**
     * Und die Reise selbst.
     *
     * <p>Zwei Riegel fuer einen Weg: Welches der beiden Ereignisse eine Portalreise
     * wirklich verhindert, ist nicht dokumentiert, und ein Spieler in der
     * Haupt-Nether ist nicht zurueckzuholen. Der zweite Riegel kostet nichts.
     *
     * <p>Dass wirklich BEIDE abbrechen, hielt bis zum 06.11.2026 keine Probe fest:
     * Beide Zweige rufen dieselbe private Methode, also zaehlt
     * {@code GrenzeTest.SENDESTELLEN} eine Sendestelle und sagt ueber den zweiten
     * Riegel nichts. Jetzt sagt es {@code PortalwacheTest} 38c - belegt mit einem
     * Eingriff, der das {@code setCancelled} hier wegnahm und nur jene Probe rot
     * machte.
     */
    @EventHandler(ignoreCancelled = true)
    public void beiDerPortalreise(PlayerPortalEvent ereignis) {
        if (!gehoertUns(ereignis.getPlayer().getWorld())) {
            return;
        }
        ereignis.setCancelled(true);
        erklaere(ereignis.getPlayer());
    }

    private boolean gehoertUns(World welt) {
        return welt != null && eigeneWelten.contains(welt.key().asString());
    }

    private void erklaere(Player spieler) {
        if (meldungen.restNs(spieler.getUniqueId(), System.nanoTime()) > 0L) {
            return;
        }
        keinAusgangZeilen(sprache).forEach(spieler::sendMessage);
    }

    // --- Die Naht: eine Absage, ohne Player und ohne Bukkit --------------------

    /**
     * Die Absage am Portal - ZWEI Chatzeilen aus EINEM Griff.
     *
     * <p>Rot die Absage, grau der Weg darunter, und sie gehen immer zusammen hinaus:
     * Zwischen den zwei Sendungen stand kein {@code if}, kein {@code return} und keine
     * Schleife - die Abklingzeit wird EINMAL davor gefragt, und danach gibt es keinen
     * Zweig mehr, der nur eine der beiden Zeilen schickt. Darum ist der Zeilenbau hier
     * ein Stueck des Satzes und nicht eine Folge von zwei Sendeaufrufen, genau wie bei
     * {@code warp.kein-ziel}.
     *
     * <p>Gemessen zwei Chatzeilen und nicht drei: Die zweite stand im Quelltext ueber
     * zwei Zeilen, mit {@code +} zusammengesetzt - die Verkettung zerreisst den Satz
     * nicht. Jetzt steht der Umbruch als Marke in der Datei und damit beim Betreiber;
     * {@code SendewegTest} 33al haelt ihn fest.
     */
    static List<Component> keinAusgangZeilen(Sprache sprache) {
        return sprache.bau(WacheSatz.PORTAL_KEIN_AUSGANG, 1L, Einsatz.keine());
    }
}
