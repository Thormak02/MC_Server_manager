package de.freimarken.paper;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import de.freimarken.kern.Kasse;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Wer wo ankommt - und in welchem Spielmodus.
 *
 * <p>Zwei kleine Aufgaben, die derselben Frage gehoeren: Was gilt fuer einen Spieler, der
 * gerade irgendwo auftaucht?
 *
 * <p><b>Ankommen.</b> Ein neuer Spieler landet im Erscheinungspunkt der Respawn-Welt des
 * Servers, und die setzt das Plugin beim Start auf Altfurt - dafuer braucht es keinen
 * Zuhoerer. Was es braucht, ist der Fall daneben: Wer in einer Welt ausgeloggt war, die es
 * nicht mehr gibt, bekommt von Paper hart {@code minecraft:overworld} eingesetzt - mit den
 * ALTEN Koordinaten. Dann steht er irgendwo in der Hauptwelt des Servers.
 * {@code PlayerJoinEvent} kaeme dafuer zu spaet (er ist dann schon dort), und
 * {@code PlayerLoginEvent} hat keinen Ortssetzer. Bleibt dieses eine Ereignis.
 *
 * <p><b>Spielmodus.</b> Minecraft kennt keinen Spielmodus je Welt. Die Tabelle in Plan
 * Abschnitt 4 fuehrt Altfurt als Abenteuer und alle uebrigen als Ueberleben; gesetzt wird
 * er deshalb beim Betreten - und nur dann, wenn er abweicht, denn jedes Setzen raeumt das
 * Flugrecht ab.
 *
 * <p><b>Sterben.</b> Kein eigener Spawnpunkt im Bett [entschieden 02.11.2026] - wer
 * stirbt, erscheint immer in Altfurt. Dafuer braucht es zwei Haken, nicht einen:
 * {@code setRespawnWorld} wirkt nur im Standardzweig, und ein Spawnpunkt aus Bett, Anker
 * oder {@code /spawnpoint} gewinnt davor. Der erste Haken verhindert, dass einer gesetzt
 * wird; der zweite biegt um, was trotzdem gesetzt wurde. Der zweite allein waere auch
 * genug - aber dann sagte das Spiel beim Schlafen weiterhin „Respawn point set", und das
 * waere eine Luege.
 */
final class Ankunftswache implements Listener {

    /** Wer nie umgestellt wird - Baumeister und Betreiber. */
    static final String RECHT_SPIELMODUS_FREI = "freimarken.spielmodus.frei";

    /**
     * Wer trotz der Sperre einen Spawnpunkt setzen darf.
     *
     * <p>Oeffnet allein den Zweig {@code COMMAND}, also {@code /spawnpoint}. Bett und
     * Anker bleiben auch damit gesperrt: Sie sind kein Werkzeug des Betreibers, sondern
     * eine Spielmechanik, und die ist hier abgeschaltet.
     */
    static final String RECHT_SPAWNPUNKT_SETZEN = "freimarken.spawnpunkt.setzen";

    private final World ankunftswelt;
    private final Map<String, Weltangabe> nachSchluessel;
    private final Kasse kasse;
    private final Logger protokoll;

    /**
     * @param nachSchluessel die Weltangaben unter dem SCHLUESSELTEXT ihrer Welt, etwa
     *                       {@code freimarken:altfurt} - nicht unter dem Namen aus der
     *                       Datei. Sonst traegt eine Hauptwelt, die zufaellig
     *                       {@code altfurt} heisst, Altfurts Spielmodus.
     * @param kasse          um das Konto beim ersten Beitritt zu eroeffnen
     */
    Ankunftswache(World ankunftswelt, Map<String, Weltangabe> nachSchluessel, Kasse kasse,
                  Logger protokoll) {
        this.ankunftswelt = ankunftswelt;
        this.nachSchluessel = nachSchluessel;
        this.kasse = kasse;
        this.protokoll = protokoll;
    }

    /**
     * Holt zurueck, wen Paper in eine fremde Welt gesetzt hat.
     *
     * <p>Laeuft in einem anderen Faden, noch vor dem Beitritt. Deshalb steht hier
     * <b>keine</b> Standprobe: Sie liest Bloecke, und das heisst Chunks laden - von einem
     * fremden Faden aus ist das nicht erlaubt. Der Erscheinungspunkt der Ankunftswelt
     * genuegt; er ist derselbe Ort, an dem auch jeder Neue landet, und wenn der nicht
     * taugt, sagt das die Startmeldung.
     */
    @EventHandler
    public void beimErscheinen(AsyncPlayerSpawnLocationEvent ereignis) {
        World war = ereignis.getSpawnLocation().getWorld();
        if (war != null && nachSchluessel.containsKey(war.key().asString())) {
            return;
        }
        ereignis.setSpawnLocation(ankunftswelt.getSpawnLocation());
        protokoll.info("Ein Spieler sollte in "
                + (war == null ? "einer unbekannten Welt" : war.key().asString())
                + " erscheinen - das gehoert nicht zum Freimarkenbund. Umgeleitet nach "
                + ankunftswelt.key().asString() + ".");
    }

    /**
     * Verhindert, dass ein Bett oder ein Anker einen Spawnpunkt setzt.
     *
     * <p>Geschlafen werden darf weiter - gesperrt ist nur das Setzen des Punktes. Die
     * Vanilla-Meldung wird ausdruecklich abgeschaltet und durch eine eigene ersetzt: Ohne
     * das stuende „Respawn point set" da, waehrend keiner gesetzt wurde, und beim
     * naechsten Tod waere der Spieler verwundert statt vorbereitet.
     *
     * <p>{@code UNKNOWN} gilt als gesperrt, nicht als erlaubt. Was sich nicht zuordnen
     * laesst, soll keinen Spawnpunkt setzen - und wenn eine kuenftige
     * Minecraft-Fassung einen neuen Weg bringt, faellt er hierher und nicht durch.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimSpawnpunktSetzen(PlayerSetSpawnEvent ereignis) {
        if (!gesperrt(ereignis)) {
            return;
        }
        ereignis.setCancelled(true);
        ereignis.setNotifyPlayer(false);
        if (ereignis.getCause() == PlayerSetSpawnEvent.Cause.BED
                || ereignis.getCause() == PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR) {
            ereignis.getPlayer().sendMessage(Component.text("Hier wird kein Spawnpunkt"
                    + " gesetzt - gestorben wird immer nach Altfurt. Schlafen geht"
                    + " weiter.", NamedTextColor.GRAY));
        }
    }

    private boolean gesperrt(PlayerSetSpawnEvent ereignis) {
        return switch (ereignis.getCause()) {
            case BED, RESPAWN_ANCHOR, UNKNOWN -> true;
            case COMMAND -> !ereignis.getPlayer().hasPermission(RECHT_SPAWNPUNKT_SETZEN);
            // PLAYER_RESPAWN raeumt beim Erscheinen auf, PLUGIN ist unser eigener Weg.
            case PLAYER_RESPAWN, PLUGIN -> false;
        };
    }

    /**
     * Wer stirbt, erscheint in Altfurt - unabhaengig davon, wo er gestorben ist.
     *
     * <p>Nur beim Tod. {@code END_PORTAL} ist ebenfalls ein Erscheinen, gehoert aber dem
     * Ende: Wer durch das Rueckportal geht, will aus dem Ende heraus und nicht nach
     * Altfurt. Ein Filter, der das verwechselt, ist der belegte Fehler solcher Zuhoerer.
     *
     * <p>Hier laeuft die Standprobe, anders als in {@link #beimErscheinen}: Dieses
     * Ereignis kommt im Hauptfaden, Bloecke zu lesen ist also erlaubt.
     */
    @EventHandler
    public void beimErscheinenNachTod(PlayerRespawnEvent ereignis) {
        if (ereignis.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) {
            return;
        }
        Location spawn = ankunftswelt.getSpawnLocation();
        var platz = Ankunftspunkt.fuer(ankunftswelt, spawn.getBlockX(), spawn.getBlockZ());
        if (platz.isEmpty()) {
            protokoll.warning("Am Erscheinungspunkt von " + ankunftswelt.key().asString()
                    + " ist kein Platz zum Stehen - ein Toter erscheint trotzdem dort,"
                    + " weil es keinen besseren Ort gibt. /setworldspawn setzen.");
            ereignis.setRespawnLocation(spawn);
            return;
        }
        ereignis.setRespawnLocation(platz.get());
    }

    @EventHandler
    public void beimBeitritt(PlayerJoinEvent ereignis) {
        eroeffneKonto(ereignis.getPlayer());
        richteSpielmodus(ereignis.getPlayer());
    }

    /**
     * Eroeffnet das Konto beim ersten Beitritt.
     *
     * <p>Bei jedem Beitritt gerufen und nur beim ersten wirksam. Der Grund steht in
     * {@link Kasse#eroeffne}: Ohne einen festen Zeitpunkt kaeme das Startguthaben bei
     * jedem Blick neu aus der Einstellungsdatei, und ein geaenderter Wert dort aenderte
     * rueckwirkend das Guthaben jedes Spielers, der noch nichts gekauft hat.
     */
    private void eroeffneKonto(Player spieler) {
        if (kasse.eroeffne(spieler.getUniqueId())) {
            protokoll.info("Konto eroeffnet fuer " + spieler.getName() + " ("
                    + spieler.getUniqueId() + ").");
        }
    }

    @EventHandler
    public void beimWeltwechsel(PlayerChangedWorldEvent ereignis) {
        richteSpielmodus(ereignis.getPlayer());
    }

    /**
     * Setzt den Spielmodus der Welt - wenn er abweicht und der Spieler nicht frei ist.
     *
     * <p>Die Ausnahme per Recht ist Pflicht, nicht Hoeflichkeit: Ohne sie wuerde ein
     * Baumeister im Kreativmodus beim Betreten Altfurts mitten im Bauen auf Abenteuer
     * heruntergestuft.
     */
    private void richteSpielmodus(Player spieler) {
        if (spieler.hasPermission(RECHT_SPIELMODUS_FREI)) {
            return;
        }
        Weltangabe angabe = nachSchluessel.get(spieler.getWorld().key().asString());
        if (angabe == null || spieler.getGameMode() == angabe.spielmodus()) {
            return;
        }
        spieler.setGameMode(angabe.spielmodus());
        if (angabe.spielmodus() == GameMode.ADVENTURE) {
            spieler.sendMessage(Component.text("Hier ist Abenteuer: ansehen und benutzen,"
                    + " aber nicht abbauen. Gebaut wird in der Marktstadt.",
                    NamedTextColor.GRAY));
        }
    }

}
