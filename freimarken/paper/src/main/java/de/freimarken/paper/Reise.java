package de.freimarken.paper;

import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Die eine Stelle, an der ein Spieler die Welt wechselt.
 *
 * <p>Beide Wege aus Plan 4.3 laufen hier durch - {@code /warp} und das Tor am
 * Bundesmarkt. Das ist kein Schoenheitsfehler, sondern die Projektregel „alle Wege zur
 * selben Tat": Zwei Fassungen derselben Pruefungen heissen, dass eine davon ohne
 * Abklingzeit oder ohne Standprobe reist, und gemerkt wird es an dem Tag, an dem jemand
 * unter Wasser erscheint.
 *
 * <p>Die Reihenfolge der Pruefungen ist Absicht. Die Abklingzeit kommt <b>zuletzt</b>:
 * {@link Abklingzeit#restNs} antwortet und vermerkt in einem Zug, und wer sie vor den
 * uebrigen Pruefungen fragt, verbrennt die Sperre fuer eine Reise, die danach ohnehin
 * abgelehnt wird.
 */
final class Reise {

    /**
     * Abstand zwischen zwei Reisen.
     *
     * <p>Nicht gegen Missbrauch, sondern gegen die Platte: Jede Reise liest den
     * Zielchunk, und ein Spieler, der zwischen zwei Toren hin und her laeuft, laesst den
     * Server Chunks in zwei Welten nachladen.
     */
    private static final Duration ZWISCHEN_ZWEI_REISEN = Duration.ofSeconds(3);

    private final Abklingzeit abklingzeit = new Abklingzeit(ZWISCHEN_ZWEI_REISEN);
    private final Logger protokoll;

    Reise(Logger protokoll) {
        this.protokoll = protokoll;
    }

    /**
     * Bringt den Spieler in diese Welt - oder sagt ihm, warum nicht.
     *
     * @param ziel die Zielwelt
     * @param name der Name, unter dem der Spieler sie kennt (ohne Namensraum)
     */
    void fuehre(Player spieler, World ziel, String name) {
        if (ziel.equals(spieler.getWorld())) {
            spieler.sendMessage(Component.text("Du bist schon in " + name + ".",
                    NamedTextColor.GRAY));
            return;
        }
        // Zu Pferd geht es nicht, und das sagt man besser vorher als nachher:
        // teleportAsync gibt ueber Weltgrenzen hinweg false zurueck, und RETAIN_VEHICLE
        // ist seit 1.21.10 abgekuendigt und wirkt nicht.
        if (spieler.getVehicle() != null) {
            spieler.sendMessage(Component.text("Nicht zu Pferd - absteigen, dann"
                    + " reisen. Das Reittier bleibt zurueck.", NamedTextColor.RED));
            return;
        }
        Optional<Location> ankunft = ankunftIn(ziel);
        if (ankunft.isEmpty()) {
            spieler.sendMessage(Component.text("In " + name + " ist am Erscheinungspunkt"
                    + " kein Platz zum Stehen. Das ist nichts, was du beheben kannst -"
                    + " melde es.", NamedTextColor.RED));
            protokoll.warning("Keine Ankunft in " + ziel.key().asString()
                    + ": am Erscheinungspunkt " + ziel.getSpawnLocation().getBlockX() + "/"
                    + ziel.getSpawnLocation().getBlockZ() + " steht in " + Ankunftspunkt.RINGE
                    + " Ringen kein tragfaehiger Boden mit Platz darueber."
                    + " /setworldspawn in " + ziel.key().asString() + " setzen.");
            return;
        }
        // Gegen PaperMC/Paper#13186: Ein Teleport jenseits der Weltkoordinaten hat Paper
        // noch 2025 zum Absturz gebracht, und ein Erscheinungspunkt aus einer Sicherung
        // reicht dafuer.
        if (!ziel.getWorldBorder().isInside(ankunft.get())) {
            spieler.sendMessage(Component.text("Der Erscheinungspunkt von " + name
                    + " liegt ausserhalb der Weltgrenze. Melde es.", NamedTextColor.RED));
            protokoll.warning("Erscheinungspunkt von " + ziel.key().asString()
                    + " liegt ausserhalb der eigenen Weltgrenze - keine Reise dorthin.");
            return;
        }
        if (zuSchnellHintereinander(spieler)) {
            return;
        }
        if (fuehrtEinTier(spieler)) {
            spieler.sendMessage(Component.text("Angeleinte Tiere bleiben zurueck - eine"
                    + " Leine reisst beim Weltwechsel.", NamedTextColor.YELLOW));
        }
        spieler.sendMessage(Component.text("Unterwegs nach " + name + " ...",
                NamedTextColor.GRAY));
        spieler.teleportAsync(ankunft.get(), PlayerTeleportEvent.TeleportCause.COMMAND)
                .thenAccept(geklappt -> {
                    if (!Boolean.TRUE.equals(geklappt)) {
                        // Ohne diese Auswertung bliebe der Spieler stehen und haette
                        // „Unterwegs" gelesen.
                        spieler.sendMessage(Component.text("Die Reise nach " + name
                                + " hat nicht geklappt.", NamedTextColor.RED));
                        protokoll.warning("Teleport von " + spieler.getName() + " nach "
                                + ziel.key().asString() + " abgelehnt.");
                    }
                });
    }

    /**
     * Wo man in dieser Welt ankommt.
     *
     * <p>Der Erscheinungspunkt der Welt und nichts anderes - Plan 4.3 verbietet eine
     * zweite Liste von Ankunftspunkten ausdruecklich: „Eine zweite Liste von
     * Ankunftspunkten waere eine zweite Wahrheit." Nur der Stand wird geprueft, denn der
     * Punkt verspricht keinen Boden.
     */
    private Optional<Location> ankunftIn(World ziel) {
        Location spawn = ziel.getSpawnLocation();
        return Ankunftspunkt.fuer(ziel, spawn.getBlockX(), spawn.getBlockZ());
    }

    /** Ob der Spieler ein Tier an der Leine fuehrt. */
    private boolean fuehrtEinTier(Player spieler) {
        return spieler.getNearbyEntities(12, 12, 12).stream()
                .anyMatch(wesen -> wesen instanceof LivingEntity tier
                        && tier.isLeashed()
                        && spieler.equals(tier.getLeashHolder()));
    }

    /**
     * Zu schnell hintereinander?
     *
     * <p>Genau ein Aufruf je Reise, und zwar hier - erst wenn feststeht, dass die Reise
     * sonst durchgeht. {@code restNs} antwortet und vermerkt zugleich.
     */
    private boolean zuSchnellHintereinander(Player spieler) {
        long rest = abklingzeit.restNs(spieler.getUniqueId(), System.nanoTime());
        if (rest <= 0L) {
            return false;
        }
        long sekunden = (rest + 999_999_999L) / 1_000_000_000L;
        spieler.sendMessage(Component.text("Einen Augenblick - noch " + sekunden
                + (sekunden == 1 ? " Sekunde." : " Sekunden."), NamedTextColor.GRAY));
        return true;
    }
}
