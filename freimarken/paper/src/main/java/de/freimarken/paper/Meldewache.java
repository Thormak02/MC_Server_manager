package de.freimarken.paper;

import de.freimarken.kern.Meldebuch;
import de.freimarken.kern.Meldung;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Die Bruecke zwischen einer erledigten Meldung und dem Melder, der sie abgegeben hat
 * (Schritt 16a Teil C).
 *
 * <p><b>Ohne diese Klasse ist der ganze Rueckmeldeweg unsichtbar wirkungslos.</b> Der
 * Kern merkt sich an jeder Meldung, ob der Melder die Antwort gelesen hat
 * ({@code Meldung.gelesen}); die Ablage halt sie; das Buch gibt sie heraus - und bis
 * hierher hat kein Spieler davon etwas gemerkt. Genau diese Art Luecke hat dieses Projekt
 * schon dreimal getroffen: eine gebaute, gruen gepruefte Regel, die niemand verdrahtet
 * hat.
 *
 * <p><b>Und sie ist der Grund, aus dem der ganze Teil gebaut ist.</b> Plan 12c.3:
 * <i>"Der Melder bekommt eine Rueckmeldung - sonst meldet beim zweiten Mal niemand
 * mehr."</i> Ein Moderator erledigt eine Meldung um drei Uhr nachts; eine Chatzeile ginge
 * an niemanden. Hier wird sie beim naechsten Beitritt nachgeliefert.
 *
 * <p><b>{@link EventPriority#MONITOR}</b>, wie bei {@link Bonuswache} und
 * {@link Rangwache}: Das Ausliefern ist eine FOLGE des Beitritts und keine Bedingung
 * dafuer. Ein Fehler hier darf keinen Beitritt abbrechen.
 *
 * <p><b>Ein Lesezugriff je Beitritt</b>, und zwar einer auf einen Index
 * ({@code melder, stand}). Dieselbe Linie wie bei {@link Rangwache}: Bei zwanzig Spielern
 * an einer Datenbank im selben Netz ist das keine Zahl, fuer die sich ein zweiter Faden
 * lohnte - und eine Antwort, die erst einen Takt spaeter kommt, ist eine, die der Spieler
 * im Beitrittsgetuemmel uebersieht.
 */
final class Meldewache implements Listener {

    private final Meldebuch buch;
    private final Sprache sprache;
    private final Meldetext.Namensgeber namen;
    private final Logger protokoll;

    Meldewache(Meldebuch buch, Sprache sprache, Meldetext.Namensgeber namen,
               Logger protokoll) {
        if (buch == null || sprache == null || namen == null || protokoll == null) {
            throw new IllegalArgumentException("Die Meldewache braucht das Meldebuch,"
                    + " Sprache, den Namensgeber und das Protokoll");
        }
        this.buch = buch;
        this.sprache = sprache;
        this.namen = namen;
        this.protokoll = protokoll;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void beimBeitritt(PlayerJoinEvent ereignis) {
        liefere(ereignis.getPlayer());
    }

    /**
     * Liefert die wartenden Antworten - und vermerkt sie als gelesen.
     *
     * <p>Beides besorgt {@code Meldebuch.holeAntworten}, und zwar in dieser Reihenfolge:
     * lesen, senden, vermerken. Bricht das Vermerken ab, liest der Spieler die Antwort
     * beim naechsten Beitritt noch einmal - das ist die harmlose Richtung.
     *
     * <p><b>Gefangen und nicht durchgereicht</b>, obwohl MONITOR einen Wurf ohnehin nur
     * ins Protokoll schreiben wuerde: Bukkits eigene Zeile waere ein Stapelabbild ohne
     * Zusammenhang. Hier steht, WER keine Antwort bekommen hat - und das ist die
     * Auskunft, mit der ein Betreiber etwas anfangen kann.
     */
    void liefere(Player spieler) {
        if (spieler == null) {
            return;
        }
        List<Meldung> warten;
        try {
            warten = buch.holeAntworten(spieler.getUniqueId());
        } catch (IllegalStateException gestoert) {
            protokoll.warning("Die Antworten auf die Meldungen von " + spieler.getName()
                    + " sind nicht lesbar: " + gestoert.getMessage() + ". Er bekommt sie"
                    + " beim naechsten Beitritt - sie sind nicht verloren, denn als"
                    + " gelesen vermerkt wird erst nach dem Senden.");
            return;
        }
        if (warten.isEmpty()) {
            return;
        }
        for (Component zeile : Meldetext.antworten(sprache, warten, namen)) {
            spieler.sendMessage(zeile);
        }
        protokoll.info(spieler.getName() + " hat " + warten.size()
                + " Antwort(en) auf seine Meldungen gelesen.");
    }
}
