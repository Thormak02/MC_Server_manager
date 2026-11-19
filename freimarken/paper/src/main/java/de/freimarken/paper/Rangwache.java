package de.freimarken.paper;

import de.freimarken.kern.Rangablage;
import de.freimarken.kern.Rangstand;
import de.freimarken.kern.Rangwerte;
import de.freimarken.kern.Wirtschaftsstand;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Die Bruecke zwischen dem abgelegten Rang und dem, was ein Spieler darf.
 *
 * <p><b>Ohne diese Klasse ist die ganze Leiter unsichtbar wirkungslos.</b> Teil A rechnet
 * aus, wer wohin gehoert, Teil B liest die Leiter aus der Datei, Teil C legt den
 * erreichten Rang ab - und bis hierher hat das kein Spieler je gemerkt. Genau diese Art
 * Luecke hat dieses Projekt schon zweimal getroffen: eine gebaute, gruen gepruefte Regel,
 * die niemand verdrahtet hat.
 *
 * <p>Drei Wege, und jeden braucht es:
 * <ul>
 *   <li><b>Der Beitritt</b> - hier wird zuerst geprueft, ob ihm mehr zusteht, und dann
 *       bekommt er sein Buendel. Jedes Mal neu und nicht nur beim ersten Mal: Die Leiter
 *       kann sich zwischen zwei Sitzungen geaendert haben, und ein Anhaengsel ueberlebt
 *       keinen Neustart.</li>
 *   <li><b>{@link #imTakt} </b> - je anwesendem Spieler, jede Minute. Das ist der Weg,
 *       der "Reisender steigt von selbst" wahr macht (Abnahmekriterium von Schritt 12).
 *       </li>
 *   <li><b>{@link #vergibNeu}</b> - das Vergeben allein, fuer die beiden oberen und fuer
 *       jeden spaeteren Aufrufer. Ohne diesen Schritt wirkte ein Aufstieg erst nach dem
 *       naechsten Anmelden, und der Spieler haette die Nachricht gelesen und doch nichts
 *       gekonnt.</li>
 * </ul>
 *
 * <p><b>Die Reihenfolge beim Beitritt ist nicht beliebig: erst heben, dann vergeben.</b>
 * Andersherum bekaeme der Spieler die Rechte seines ALTEN Ranges und die Nachricht ueber
 * den neuen - und bis zum naechsten Taktschlag koennte er weniger, als dastand. Genau in
 * dieser Minute versucht er, was die Nachricht ihm gerade versprochen hat.
 *
 * <p><b>Warum der Aufstieg beim Beitritt UND im Takt geprueft wird</b>, obwohl der Takt
 * ihn ohnehin eine Minute spaeter faengt: Diese eine Minute ist die, in der ein
 * zurueckkehrender Spieler sofort {@code /parzelle nehmen} tippt. Eine Absage "dein Rang
 * erlaubt nicht mehr" fuer jemanden, dem seit Wochen mehr zusteht, ist der Fehler, den
 * niemand meldet - er haelt es fuer die Regel.
 *
 * <p><b>{@link EventPriority#MONITOR} beim Beitritt</b>, wie bei {@link Bonuswache}: Das
 * Vergeben ist eine FOLGE des Beitritts und keine Bedingung dafuer.
 *
 * <p><b>Die Ablage wird im Hauptfaden gefragt</b>, ein Lesezugriff je Beitritt. Dieselbe
 * Linie wie bei {@link Mietwache}, und bei zwanzig Spielern an einer Datenbank im selben
 * Netz ist das keine Zahl, fuer die sich ein zweiter Faden lohnte. Ein Rang, der erst
 * einen Takt spaeter gilt, waere dagegen ein Spieler, der in seiner ersten Sekunde
 * weniger darf als danach - und der baut genau dann an der Parzelle, die er noch nicht
 * nehmen kann.
 */
final class Rangwache implements Listener {

    private final Rangablage ablage;
    private final Wirtschaftsstand wirtschaft;
    private final Rechtegriff griff;

    /**
     * Der Lauf, der messen und heben kann.
     *
     * <p>Getrennt von dieser Wache, weil er auch OHNE anwesenden Spieler arbeiten muss -
     * der Startlauf stuft Leute nach, die gerade nicht da sind, und denen kann man kein
     * Anhaengsel geben.
     */
    private final Aufstiegslauf lauf;

    private final Sprache sprache;
    private final Logger protokoll;

    /**
     * Welche unbekannten Raenge schon gemeldet sind.
     *
     * <p>Ein Rang, den die Leiter nicht mehr kennt, ist eine Sache des BETREIBERS und
     * nicht des Spielers - gemeldet gehoert er, aber einmal. Ohne diese Liste stuende die
     * Zeile bei jedem Beitritt jedes Betroffenen erneut im Protokoll, und das ist der
     * sicherste Weg, dass sie niemand liest.
     */
    private final Set<String> gemeldet = new LinkedHashSet<>();

    Rangwache(Rangablage ablage, Wirtschaftsstand wirtschaft, Rechtegriff griff,
              Aufstiegslauf lauf, Sprache sprache, Logger protokoll) {
        if (ablage == null || wirtschaft == null || griff == null || lauf == null
                || sprache == null || protokoll == null) {
            throw new IllegalArgumentException("Die Rangwache braucht Ablage, Werte,"
                    + " Rechtegriff, Aufstiegslauf, Sprache und Protokoll");
        }
        this.ablage = ablage;
        this.wirtschaft = wirtschaft;
        this.griff = griff;
        this.lauf = lauf;
        this.sprache = sprache;
        this.protokoll = protokoll;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void beimBeitritt(PlayerJoinEvent ereignis) {
        hebeUndVergib(ereignis.getPlayer());
    }

    /**
     * Der Taktschlag - gerufen von {@link Spielzeitwache}, je anwesendem Spieler.
     *
     * <p>NACH dem Abrechnen der Spielzeit, wie {@link Bonuswache#imTakt}: Die taetigen
     * Sekunden dieses Taktes entstehen genau in diesem Abrechnen. Davor gefragt, fehlte
     * dem Aufstieg immer der letzte Taktschlag - und beim Erreichen einer runden Stunde
     * waere das genau der, auf den der Spieler gewartet hat.
     */
    void imTakt(Player spieler) {
        hebeUndVergib(spieler);
    }

    /**
     * Prueft den Aufstieg, vergibt die Rechte und sagt es dem Spieler - in dieser Folge.
     *
     * <p>Das Vergeben laeuft AUCH ohne Aufstieg: Beim Beitritt ist es der eigentliche
     * Zweck, und im Takt kostet es nichts, weil der Griff dieselbe Menge noch einmal
     * setzt. Ein {@code if (aufstieg.isPresent())} davor waere eine Ersparnis mit einer
     * Luecke - nach einem Neuladen der config.yml gilt eine andere Leiter, und ohne
     * Vergeben haette der Spieler bis zum naechsten Anmelden die Rechte der alten.
     */
    private void hebeUndVergib(Player spieler) {
        Optional<Aufstiegslauf.Aufstieg> gehoben = lauf.pruefe(spieler.getUniqueId());
        vergibNeu(spieler);
        if (gehoben.isEmpty()) {
            return;
        }
        protokoll.info(spieler.getName() + " ist aufgestiegen: "
                + gehoben.get().vorher() + " -> " + gehoben.get().nachher() + ".");
        for (Component zeile : Rangtext.aufstiegZeilen(sprache,
                wirtschaft.werte().raenge(), gehoben.get().nachher())) {
            spieler.sendMessage(zeile);
        }
    }

    /**
     * Beim Verlassen die Merkliste des Griffs raeumen.
     *
     * <p>Das Anhaengsel selbst geht mit dem Spieler; die Merkliste tut es nicht. Ohne
     * diese Zeile waechst sie um einen Eintrag je Beitritt, solange der Server laeuft.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void beimVerlassen(PlayerQuitEvent ereignis) {
        griff.loese(ereignis.getPlayer());
    }

    /**
     * Gibt dem Spieler das Buendel seines Ranges - neu und vollstaendig.
     *
     * <p>Gerufen beim Beitritt und nach jedem Aufstieg. Was er vorher von diesem Griff
     * hatte, wird dabei zurueckgenommen; Rechte aus der Rechteverwaltung bleiben.
     */
    void vergibNeu(Player spieler) {
        if (spieler == null) {
            return;
        }
        Rangwerte werte = wirtschaft.werte().raenge();
        if (!werte.greift()) {
            // raenge.an ist aus oder die Leiter ist leer. Dann gilt, was in der config.yml
            // steht, fuer jeden dieselbe Zahl - und der Griff darf nichts halten, sonst
            // wirkte der Schalter nur auf den AUFSTIEG und nicht auf das Gehaltene.
            griff.setze(spieler, Set.of());
            return;
        }
        Rangstand stand = ablage.lies(spieler.getUniqueId());
        if (!stand.bekanntIn(werte)) {
            melde(stand, spieler);
        }
        Rangwerte.Stufe stufe = stand.stufeIn(werte);
        // Kann nicht null sein, solange greift() gilt - die Leiter ist dann nicht leer.
        // Trotzdem gefragt: Eine spaetere Aenderung an greift() soll hier keinen Absturz
        // beim Beitritt ergeben.
        griff.setze(spieler, stufe == null ? Set.of()
                : Rangzahl.knoten(werte.buendel(stufe.schluessel())));
    }

    /**
     * Sagt EINMAL, dass in der Ablage eine Stufe steht, die es nicht mehr gibt.
     *
     * <p>Der Spieler faellt dabei auf die unterste Stufe zurueck - das ist das Beste, was
     * sich tun laesst, und zugleich das, was niemand bemerkt: Im Spiel sieht es aus, als
     * waere er nie aufgestiegen. Darum diese Zeile.
     */
    private void melde(Rangstand stand, Player spieler) {
        if (!gemeldet.add(stand.rang())) {
            return;
        }
        protokoll.warning("In der Ablage steht der Rang \"" + stand.rang() + "\" ("
                + spieler.getName() + " und moeglicherweise weitere), aber die"
                + " config.yml kennt ihn nicht mehr. Sie haben die unterste Stufe -"
                + " im Spiel sieht das aus, als waeren sie nie aufgestiegen. Entweder"
                + " den Rang wieder eintragen oder die Betroffenen umsetzen.");
    }
}
