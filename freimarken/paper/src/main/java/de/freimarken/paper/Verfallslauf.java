package de.freimarken.paper;

import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Verfallsregel;
import de.freimarken.kern.Wirtschaftsstand;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Der Verfall der Geisterstaedte - und die Warnungen davor [Plan 16, Teil E5].
 *
 * <p>Zwei Wege zur selben Rechnung, und beide braucht es:
 * <ul>
 *   <li><b>Der taegliche Lauf</b> setzt faellige Staedte auf {@code VERFALLEN}. Er muss
 *       laufen, ob jemand angemeldet ist oder nicht - eine Geisterstadt wird gerade
 *       dadurch zu einer, dass niemand kommt.</li>
 *   <li><b>Der Beitritt</b> warnt den Stadtbesitzer. Er muss am Beitritt haengen, weil
 *       ein abwesender Besitzer sonst nie erfaehrt, dass seine Stadt abzulaufen
 *       droht.</li>
 * </ul>
 *
 * <p><b>Die Warnung wird NICHT gezaehlt und nicht gespeichert</b>, und das ist die
 * tragende Entscheidung dieser Klasse - die Begruendung steht ausfuehrlich bei
 * {@link Verfallsregel}. Kurz: Zwei Warnungen sind zwei STUFEN und nicht zwei
 * verschickte Nachrichten. Wer sich anmeldet, waehrend seine Stadt in einer Stufe steht,
 * liest sie; wer nichts tut, liest sie beim naechsten Mal wieder. Ein Zaehler an der
 * Stadtzeile waere eine zweite Wahrheit ueber "schon gewarnt", er stuende falsch, sobald
 * eine Nachricht verloren geht, und er machte aus einem Besuch keine Entwarnung.
 *
 * <p><b>Kein eigener Zeitgeber</b>, wie bei {@link Mietwache} und {@link Truhenlauf}:
 * {@link Spielzeitwache} stellt den Minutentakt schon. Davor sitzt der BILLIGE VORTEST
 * {@link #zuletzt} - der Takt schlaegt 1.440 Mal am Tag, und der Lauf geht ueber alle
 * Staedte einer Welt.
 */
final class Verfallslauf implements Listener {

    private final Freistadtbuch buch;
    private final String weltschluessel;
    private final Clock uhr;
    private final Wirtschaftsstand wirtschaft;
    private final Sprache sprache;
    private final Logger protokoll;

    /**
     * An welchem Tag der Lauf zuletzt DURCH war.
     *
     * <p>Nur im Arbeitsspeicher und absichtlich keine Spalte - dieselbe Bauart und
     * dieselbe Begruendung wie bei {@code Mietlauf.zuletzt}: Eine Spalte waere eine
     * zweite Wahrheit ueber "heute erledigt", und die erste steht ohnehin in
     * {@code parzellen.zustand}. Ein Lauf, der nach einem Neustart ein zweites Mal
     * laeuft, setzt denselben Zustand noch einmal - und {@code lasseVerfallen} schreibt
     * dann gar nicht.
     *
     * <p>Gesetzt erst nach einem STOERUNGSFREIEN Lauf: Sonst waere ein einzelner
     * Datenbankausfall ein ganzer Tag ohne Verfall.
     */
    private LocalDate zuletzt;

    Verfallslauf(Freistadtbuch buch, String weltschluessel, Clock uhr,
            Wirtschaftsstand wirtschaft, Sprache sprache, Logger protokoll) {
        if (buch == null) {
            throw new IllegalArgumentException("Ohne Freistadtbuch gibt es keine Stadt,"
                    + " die verfallen koennte - diese Klasse entsteht nur mit einem");
        }
        if (weltschluessel == null || weltschluessel.isBlank()) {
            throw new IllegalArgumentException("Ohne Weltschluessel findet der Lauf"
                    + " keine Staedte - und faende er keine, verfiele lautlos nie eine");
        }
        this.buch = buch;
        this.weltschluessel = weltschluessel;
        this.uhr = uhr;
        this.wirtschaft = wirtschaft;
        this.sprache = sprache;
        this.protokoll = protokoll;
    }

    /**
     * Der taegliche Durchgang - gerufen von {@link Spielzeitwache} je Taktschlag.
     *
     * <p><b>Klagt nur und wirft nicht</b>, wie jeder Mitfahrer des Taktes: Eine
     * Ausnahme von hier kostete die Spielzeit aller Spieler bis zum naechsten Neustart.
     *
     * @return wie viele Staedte in diesem Durchgang verfallen sind - fuer die Probe und
     *         fuer das Protokoll
     */
    int imTakt() {
        LocalDate heute = heute();
        if (heute.equals(zuletzt)) {
            return 0;
        }
        try {
            ZoneId zone = zone();
            int verfallen = 0;
            for (Parzelle stadt : buch.staedte(weltschluessel)) {
                if (buch.verfallsstand(stadt, heute, zone) != Verfallsregel.Stand.FAELLIG) {
                    continue;
                }
                if (buch.lasseVerfallen(stadt)) {
                    verfallen++;
                    protokoll.info("Freistadt " + stadt.name() + " ist VERFALLEN -"
                            + " seit " + wirtschaft.werte().freistaedte().verfall()
                                    .unbesuchtTage()
                            + " Tagen unbesucht und ohne Stadtbuerger. Sie behaelt Feld,"
                            + " Name und Guthaben; ihr Besitzer weckt sie durch"
                            + " Betreten.");
                }
            }
            // ERST JETZT gemerkt: Bei einer Stoerung soll der naechste Takt es erneut
            // versuchen, und nicht erst morgen.
            zuletzt = heute;
            return verfallen;
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Der Verfallslauf ist in einen Fehler gelaufen."
                    + " Der naechste Takt versucht es erneut; faellt er laenger aus,"
                    + " bleiben Geisterstaedte stehen - verloren geht dabei nichts.",
                    fehler);
            return 0;
        }
    }

    /**
     * Warnt den Stadtbesitzer beim Beitritt - oder sagt ihm, dass es zu spaet ist.
     *
     * <p>{@link EventPriority#MONITOR}, wie bei {@link Bonuswache} und aus demselben
     * Grund: Die Warnung ist eine FOLGE des Beitritts und keine Bedingung dafuer.
     *
     * <p><b>Nur seine EIGENEN Staedte</b>, und zwar alle: Wer drei hat, liest drei
     * Zeilen. Ein Mitbauer bekommt nichts - er wurde zum Bauen eingetragen, nicht zum
     * Erhalten, und er kann die Stadt auch nicht wecken.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void beimBeitritt(PlayerJoinEvent ereignis) {
        Player spieler = ereignis.getPlayer();
        try {
            LocalDate heute = heute();
            ZoneId zone = zone();
            for (Parzelle stadt : buch.staedte(weltschluessel)) {
                if (!stadt.gehoert(spieler.getUniqueId())) {
                    continue;
                }
                Verfallsregel.Stand stand = buch.verfallsstand(stadt, heute, zone);
                if (stand == Verfallsregel.Stand.AKTIV) {
                    continue;
                }
                Freistadttext.verfallszeilen(sprache, stadt.name(), stand,
                                buch.resttage(stadt, heute, zone))
                        .forEach(spieler::sendMessage);
            }
        } catch (RuntimeException fehler) {
            // GEFANGEN: Ein Wurf aus einem Beitrittszweig liesse den Spieler ohne
            // Begruendung in einer halb eingerichteten Sitzung zurueck. Eine verpasste
            // Warnung kostet ihn dagegen nichts - er bekommt sie beim naechsten Mal.
            protokoll.log(Level.WARNING, "Die Verfallswarnung an " + spieler.getName()
                    + " liess sich nicht bauen. Er bekommt sie beim naechsten"
                    + " Beitritt.", fehler);
        }
    }

    /**
     * Der heutige Tag unter der eingestellten Zone.
     *
     * <p>Die Zone wird beim GEBRAUCH geholt und nie in ein Feld kopiert - dieselbe Zeile
     * wie in {@code Tagesbonus} und {@code Besuchslauf}. Ein Feld haette den Stand des
     * Serverstarts, und ein Neuladen von {@code zeit.zone} wirkte nie.
     */
    private LocalDate heute() {
        return Tagesgrenze.tagVon(uhr.instant(), zone());
    }

    private ZoneId zone() {
        return wirtschaft.werte().zeit().zone();
    }
}
