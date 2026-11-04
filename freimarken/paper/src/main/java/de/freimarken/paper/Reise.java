package de.freimarken.paper;

import de.freimarken.kern.Quader;
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
 * <p>Jeder Teleport laeuft hier durch: {@code /warp}, das Tor am Bundesmarkt,
 * {@code /zurueck}, {@code /tpaccept} und {@code /freimarken parzelle}. Das ist kein
 * Schoenheitsfehler, sondern die Projektregel „alle Wege zur selben Tat": Zwei Fassungen
 * derselben Pruefungen heissen, dass eine davon ohne Abklingzeit oder ohne Standprobe
 * reist, und gemerkt wird es an dem Tag, an dem jemand unter Wasser erscheint.
 *
 * <p>Zwei Eingaenge, weil es zwei Fragen sind. {@link #fuehre(Player, World, String)}
 * fuehrt in eine WELT und sucht sich den Ankunftsplatz an ihrem Erscheinungspunkt;
 * {@link #fuehre(Player, Location, String)} fuehrt an einen ORT, der schon feststeht, und
 * sucht <b>nichts</b> - er prueft und sagt ab. Dieser Unterschied ist nicht Feinschliff:
 * Eine Suche am Zielort schickte einen Rueckweg aus einer Hoehle an die Oberflaeche und
 * eine Todesstelle neben einem Lavasee aufs Netherdach.
 *
 * <p>Hier wird auch der Rueckweg gemerkt - vor jedem Teleport, an einer Stelle. Das
 * erspart einen Zuhoerer auf {@code PlayerTeleportEvent} und damit die Frage, welche
 * Teleportursachen zaehlen; ein Anti-Cheat, das einen Spieler zuruecksetzt, meldet
 * {@code PLUGIN} und haette den Rueckweg sonst ueberschrieben.
 *
 * <p>Die Reihenfolge der Pruefungen ist Absicht. Die Abklingzeit kommt <b>zuletzt</b>:
 * {@link Abklingzeit#restNs} antwortet und vermerkt in einem Zug, und wer sie vor den
 * uebrigen Pruefungen fragt, verbrennt die Sperre fuer eine Reise, die danach ohnehin
 * abgelehnt wird.
 *
 * <p>Danach kommt die {@link Aufwaermzeit} - drei Sekunden, in denen der Spieler bleiben
 * muss, wo er ist. Deshalb wird <b>zweimal</b> geprueft: einmal, bevor gewartet wird, und
 * einmal, wenn es losgeht. In drei Sekunden kann der Boden am Ziel abgebaut, Lava
 * geflossen oder die Welt entladen worden sein, und die erste Pruefung sagt darueber
 * nichts mehr aus.
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
    private final Rueckweg rueckweg;
    private final Aufwaermzeit aufwaermzeit;

    /**
     * Die Saetze dieses Plugins - verdrahtet, bevor der erste Satz umzieht.
     *
     * <p>Warum alle elf Sender dieses Feld in EINEM Zug bekommen und nicht je
     * Bauschritt, steht dort, wo sie gebaut werden: {@code FreimarkenPlugin.onEnable}.
     */
    private final Sprache sprache;

    Reise(Logger protokoll, Rueckweg rueckweg, Aufwaermzeit aufwaermzeit,
          Sprache sprache) {
        this.sprache = sprache;
        this.protokoll = protokoll;
        this.rueckweg = rueckweg;
        this.aufwaermzeit = aufwaermzeit;
    }

    /**
     * Bringt den Spieler in diese Welt - oder sagt ihm, warum nicht.
     *
     * <p>Der Ankunftsplatz wird am Erscheinungspunkt der Welt GESUCHT, ringweise. Das ist
     * hier richtig, weil kein bestimmter Ort gemeint ist: Gewollt ist „nach Kornmark",
     * nicht „auf diesen Block".
     *
     * @param ziel die Zielwelt
     * @param name der Name, unter dem der Spieler sie kennt (ohne Namensraum)
     */
    void fuehre(Player spieler, World ziel, String name) {
        fuehre(spieler, ziel, name, null);
    }

    /** Wie {@link #fuehre(Player, World, String)}, mit einem eigenen Aufwaermbereich. */
    void fuehre(Player spieler, World ziel, String name, Quader bleibeIn) {
        if (ziel.equals(spieler.getWorld())) {
            spieler.sendMessage(Component.text("Du bist schon in " + name + ".",
                    NamedTextColor.GRAY));
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
        fuehre(spieler, ankunft.get(), name, bleibeIn);
    }

    /** Wie {@link #fuehre(Player, Location, String, Quader)}, mit dem eigenen Block. */
    void fuehre(Player spieler, Location ziel, String name) {
        fuehre(spieler, ziel, name, null);
    }

    /**
     * Bringt den Spieler an genau diesen Ort - oder sagt ihm, warum nicht.
     *
     * <p>Fuer {@code /zurueck}, {@code /tpaccept} und {@code /freimarken parzelle}: Der
     * Ort steht fest, und es wird <b>nicht</b> gesucht. Wer hier suchte, verschoebe das
     * Ziel - und zwar am liebsten dorthin, wo es am wenigsten gewollt ist: aus einer
     * Hoehle an die Oberflaeche, von der Todesstelle neben dem Lavasee aufs Netherdach.
     *
     * @param name wie das Ziel in einer Meldung heisst, etwa „Kornmark" oder „Anna"
     */
    void fuehre(Player spieler, Location ziel, String name, Quader bleibeIn) {
        // Laeuft schon eine Wartezeit, ist nichts zu tun und nichts zu melden: Der
        // Spieler hat „stehen bleiben" gelesen. Ohne diese Zeile wuerde jeder Schritt im
        // Tor eine zweite Reise anstossen, die dann an der Abklingzeit scheitert - zwei
        // Meldungen fuer einen Schritt.
        if (aufwaermzeit.laeuft(spieler.getUniqueId())) {
            return;
        }
        Optional<Component> klage = pruefe(spieler, ziel);
        if (klage.isPresent()) {
            spieler.sendMessage(klage.get());
            return;
        }
        if (zuSchnellHintereinander(spieler)) {
            return;
        }
        World welt = ziel.getWorld();
        if (!welt.equals(spieler.getWorld()) && fuehrtEinTier(spieler)) {
            spieler.sendMessage(Component.text("Angeleinte Tiere bleiben zurueck - eine"
                    + " Leine reisst beim Weltwechsel.", NamedTextColor.YELLOW));
        }
        Quader bereich = bleibeIn != null ? bleibeIn : Aufwaermzeit.seinBlock(spieler);
        aufwaermzeit.beginne(spieler, name, bereich, () -> springe(spieler, ziel, name));
    }

    /**
     * Alles, was gegen diesen Teleport spricht - oder nichts.
     *
     * <p>Eine eigene Methode, weil sie <b>zweimal</b> laeuft: vor der Wartezeit und
     * wieder, wenn es losgeht. In drei Sekunden kann der Boden am Ziel abgebaut, Lava
     * geflossen oder die Welt entladen worden sein.
     */
    private Optional<Component> pruefe(Player spieler, Location ziel) {
        if (ziel == null || !ziel.isWorldLoaded()) {
            return Optional.of(Component.text("Dorthin geht es nicht mehr - die Welt ist"
                    + " nicht geladen.", NamedTextColor.RED));
        }
        World welt = ziel.getWorld();
        // Zu Pferd geht es nicht, und das sagt man besser vorher als nachher:
        // teleportAsync gibt ueber Weltgrenzen hinweg false zurueck, und RETAIN_VEHICLE
        // ist seit 1.21.10 abgekuendigt und wirkt nicht.
        if (spieler.getVehicle() != null) {
            return Optional.of(Component.text("Nicht zu Pferd - absteigen, dann reisen."
                    + " Das Reittier bleibt zurueck.", NamedTextColor.RED));
        }
        // Zwei Pruefungen gegen PaperMC/Paper#13186: Ein Teleport jenseits der
        // Weltkoordinaten hat Paper noch 2025 zum Absturz gebracht, und ein gemerkter Ort
        // aus einer Sicherung oder ein vertippter Quader reicht dafuer.
        if (ziel.getBlockY() < welt.getMinHeight()
                || ziel.getBlockY() > welt.getMaxHeight() - 2) {
            return Optional.of(Component.text("Dorthin geht es nicht - der Ort liegt"
                    + " ausserhalb der Welthoehe.", NamedTextColor.RED));
        }
        if (!welt.getWorldBorder().isInside(ziel)) {
            return Optional.of(Component.text("Dorthin geht es nicht - der Ort liegt"
                    + " ausserhalb der Weltgrenze.", NamedTextColor.RED));
        }
        if (!Ankunftspunkt.taugt(Ankunftspunkt.griffAuf(welt), ziel.getBlockX(),
                ziel.getBlockY(), ziel.getBlockZ())) {
            // Abgesagt und nicht verschoben: Der Ort war gemeint. Hat sich dort etwas
            // geaendert - Boden abgebaut, Wasser gelaufen, Lava geflossen -, ist das eine
            // Nachricht und kein Grund, woanders hinzuspringen.
            return Optional.of(Component.text("Dort ist kein Platz zum Stehen mehr.",
                    NamedTextColor.RED));
        }
        return Optional.empty();
    }

    /** Der Sprung selbst - nach der Wartezeit, und erst nach einer zweiten Pruefung. */
    private void springe(Player spieler, Location ziel, String name) {
        Optional<Component> klage = pruefe(spieler, ziel);
        if (klage.isPresent()) {
            spieler.sendMessage(klage.get());
            return;
        }
        // Der Rueckweg, an der einen Stelle, an der jeder Teleport vorbeikommt - und erst
        // hier: Ein abgebrochenes Aufwaermen soll keinen Rueckweg hinterlassen.
        rueckweg.merken(spieler.getUniqueId(), spieler.getLocation());
        spieler.teleportAsync(ziel, PlayerTeleportEvent.TeleportCause.COMMAND)
                .thenAccept(geklappt -> {
                    if (!Boolean.TRUE.equals(geklappt)) {
                        // Ohne diese Auswertung bliebe der Spieler stehen und haette
                        // „stehen bleiben" gelesen.
                        spieler.sendMessage(Component.text("Die Reise nach " + name
                                + " hat nicht geklappt.", NamedTextColor.RED));
                        protokoll.warning("Teleport von " + spieler.getName() + " nach "
                                + ziel.getWorld().key().asString() + " abgelehnt.");
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
