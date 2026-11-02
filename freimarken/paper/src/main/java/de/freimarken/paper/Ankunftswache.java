package de.freimarken.paper;

import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

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
 */
final class Ankunftswache implements Listener {

    /** Wer nie umgestellt wird - Baumeister und Betreiber. */
    static final String RECHT_SPIELMODUS_FREI = "freimarken.spielmodus.frei";

    private final World ankunftswelt;
    private final Map<String, Weltangabe> nachSchluessel;
    private final Logger protokoll;

    /**
     * @param nachSchluessel die Weltangaben unter dem SCHLUESSELTEXT ihrer Welt, etwa
     *                       {@code freimarken:altfurt} - nicht unter dem Namen aus der
     *                       Datei. Sonst traegt eine Hauptwelt, die zufaellig
     *                       {@code altfurt} heisst, Altfurts Spielmodus.
     */
    Ankunftswache(World ankunftswelt, Map<String, Weltangabe> nachSchluessel,
                  Logger protokoll) {
        this.ankunftswelt = ankunftswelt;
        this.nachSchluessel = nachSchluessel;
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

    @EventHandler
    public void beimBeitritt(PlayerJoinEvent ereignis) {
        richteSpielmodus(ereignis.getPlayer());
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
