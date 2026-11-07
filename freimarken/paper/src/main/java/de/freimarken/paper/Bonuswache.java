package de.freimarken.paper;

import de.freimarken.kern.Wirtschaftsstand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Die Bruecke zwischen dem Spiel und dem {@link Tagesbonus}: Beitritt und Takt.
 *
 * <p>Zwei Wege zur selben Tat, und beide braucht es:
 * <ul>
 *   <li><b>Der Beitritt</b> - ohne eingestellte Mindestzeit faellt der Bonus genau hier,
 *       und das ist der Normalfall. Ein Spieler soll ihn lesen, wenn er ankommt.</li>
 *   <li><b>Der Spielzeittakt</b> - ist eine Mindestzeit eingestellt, kann sie beim
 *       Beitritt noch nicht erreicht sein. Der Takt fragt erneut, bis sie es ist.</li>
 * </ul>
 * Beide gehen durch {@link #zahleWennFaellig}, und die Entscheidung liegt dahinter in
 * {@link Tagesbonus#pruefe} - ein zweiter Aufruf am selben Tag zahlt nicht.
 *
 * <p><b>Kein eigener Zeitgeber.</b> Der Takt kommt von {@link Spielzeitwache}, die ihn
 * schon stellt. Ein zweiter Zeitgeber ist in Plan 8.8 ausdruecklich ausgeschlossen - und
 * er waere hier auch nicht noetig, denn der Bonus braucht kein eigenes Fenster.
 *
 * <p><b>{@link EventPriority#MONITOR} beim Beitritt</b>, obwohl die Reihenfolge nicht
 * entscheidet: {@code Ankunftswache} eroeffnet dort das Konto, und wer zuerst kommt, ist
 * gleichgueltig, weil {@code Kasse.gutschreiben} ein fehlendes Konto selbst eroeffnet -
 * mit dem Startguthaben und der Eroeffnungsbuchung ({@code MariaKasse.stelleKontoSicher},
 * gelesen am 07.11.2026). MONITOR steht hier, weil der Bonus eine FOLGE des Beitritts ist
 * und keine Bedingung dafuer.
 */
final class Bonuswache implements Listener {

    private final Tagesbonus bonus;
    private final Wirtschaftsstand wirtschaft;
    private final Sprache sprache;

    Bonuswache(Tagesbonus bonus, Wirtschaftsstand wirtschaft, Sprache sprache) {
        this.bonus = bonus;
        this.wirtschaft = wirtschaft;
        this.sprache = sprache;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void beimBeitritt(PlayerJoinEvent ereignis) {
        zahleWennFaellig(ereignis.getPlayer());
    }

    /**
     * Derselbe Griff fuer den Spielzeittakt - gerufen von {@link Spielzeitwache}.
     *
     * <p>Je anwesendem Spieler und NACH dem Abrechnen der Spielzeit: Ist eine Mindestzeit
     * eingestellt, zaehlt sie taetige Minuten, und die entstehen genau in diesem
     * Abrechnen. Davor gefragt, fehlte dem Bonus immer der letzte Taktschlag.
     */
    void imTakt(Player spieler) {
        zahleWennFaellig(spieler);
    }

    /**
     * Prueft und sagt es dem Spieler.
     *
     * <p>Die Nachricht kommt aus {@link Bonustext} und damit aus der Sprachdatei - auch
     * die Wahl, WELCHER der drei Zahlungssaetze zutrifft, liegt dort und nicht hier. Was
     * diese Stelle tut, ist senden.
     */
    private void zahleWennFaellig(Player spieler) {
        Tagesbonus.Zahlung zahlung = bonus.pruefe(spieler.getUniqueId());
        if (zahlung == null) {
            return;
        }
        spieler.sendMessage(Bonustext.zahlungZeile(sprache, zahlung,
                wirtschaft.werte().bonus()));
    }
}
