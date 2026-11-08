package de.freimarken.paper;

import de.freimarken.kern.Wirtschaftsstand;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Die Bruecke zwischen dem Spiel und dem {@link Mietlauf}: Beitritt und Takt.
 *
 * <p>Dieselbe Bauart wie {@link Bonuswache}, und aus demselben Grund zwei Wege:
 * <ul>
 *   <li><b>Der Spielzeittakt</b> rechnet ab. Er laeuft, ob der Mieter da ist oder nicht -
 *       das ist der ganze Zweck der Ladenmiete. Wer gerade online ist, liest sofort, was
 *       mit seinem Laden geschah.</li>
 *   <li><b>Der Beitritt</b> sagt es dem, der es verschlafen hat. Der Lauf findet statt,
 *       waehrend ein Spieler offline ist; ohne diese Zeile kaeme er zurueck und faende
 *       seinen Laden weg, ohne je gewarnt worden zu sein.</li>
 * </ul>
 *
 * <p><b>Kein eigener Zeitgeber.</b> Der Takt kommt von {@link Spielzeitwache}, die ihn
 * schon stellt. Plan 8.8 schliesst einen zweiten ausdruecklich aus.
 *
 * <p><b>{@link #imTakt} wird EINMAL je Taktschlag gerufen und nicht je Spieler</b> -
 * anders als bei der Bonuswache, und das ist der wesentliche Unterschied. Der Bonus
 * gehoert einem Spieler; die Ladenmiete gehoert allen Vertraegen, und die sind nicht an
 * Anwesenheit gebunden. Ein Aufruf je anwesendem Spieler rechnete den ganzen Tageslauf so
 * oft ab, wie Leute online sind - der Merker in {@link Mietlauf} faengt das, aber er ist
 * eine Schranke und keine Einladung.
 *
 * <p><b>{@link EventPriority#MONITOR} beim Beitritt</b>, wie bei der Bonuswache: Diese
 * Zeilen sind eine FOLGE des Beitritts und keine Bedingung dafuer.
 */
final class Mietwache implements Listener {

    private final Server server;
    private final Mietlauf lauf;
    private final Mietbuch mietbuch;
    private final Abholbuch abholbuch;
    private final Wirtschaftsstand wirtschaft;
    private final Sprache sprache;

    Mietwache(Server server, Mietlauf lauf, Mietbuch mietbuch, Abholbuch abholbuch,
              Wirtschaftsstand wirtschaft, Sprache sprache) {
        this.server = server;
        this.lauf = lauf;
        this.mietbuch = mietbuch;
        this.abholbuch = abholbuch;
        this.wirtschaft = wirtschaft;
        this.sprache = sprache;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void beimBeitritt(PlayerJoinEvent ereignis) {
        Player spieler = ereignis.getPlayer();
        // Der Verzug ZUERST: Er ist die Nachricht, bei der etwas zu tun ist. Das
        // Abholfach ist eine Auskunft und steht darunter.
        sende(spieler, Miettext.verzugZeilen(sprache,
                mietbuch.vertragVon(spieler.getUniqueId()),
                wirtschaft.werte().laeden().ausfaelleBisRaeumung()));
        // Auch fuer Spieler OHNE Laden, und das ist Absicht: Wer geraeumt wurde, hat
        // keinen Vertrag mehr - und gerade er muss erfahren, wo seine Ware liegt. Eine
        // Zeile nur fuer Mieter traefe jeden ausser den Betroffenen.
        sende(spieler, Miettext.fachstandZeilen(sprache,
                abholbuch.stand(spieler.getUniqueId())));
    }

    /**
     * Der Taktschlag - gerufen von {@link Spielzeitwache}, einmal je Schlag.
     *
     * <p>Jeder Posten geht an SEINEN Mieter, wenn der gerade da ist. Wer nicht da ist,
     * liest die Lage beim naechsten Beitritt; das Geld ist unabhaengig davon gebucht.
     */
    void imTakt() {
        for (Mietlauf.Posten posten : lauf.imTakt()) {
            if (posten.vertrag() == null) {
                continue;
            }
            Player mieter = server.getPlayer(posten.vertrag().mieter());
            if (mieter == null) {
                continue;
            }
            sende(mieter, Miettext.laufZeilen(sprache, posten));
        }
    }

    private static void sende(Player wer, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            wer.sendMessage(zeile);
        }
    }
}
