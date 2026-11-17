package de.freimarken.paper;

import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Der Besuchsstempel: Welche Freistadt wurde heute betreten? [Plan 16, Teil E4]
 *
 * <p>Daran haengt die Verfallsregel. Eine Stadt, die zwoelf Monate unbesucht ist und
 * keinen Stadtbuerger hat, ist eine Geisterstadt - sie blockiert ein Feld, das jemand
 * anderes bebauen wuerde. Ohne diesen Stempel waere der Verfall nicht entscheidbar: Die
 * Zeile kennt nur ihr {@code erworben_am}, und das steht ab der Gruendung fest.
 *
 * <p><b>Kein eigener Zeitgeber</b>, wie bei {@link Bonuswache} und aus demselben Grund:
 * {@link Spielzeitwache} stellt den Minutentakt schon, und ein zweiter Zeitgeber ist in
 * diesem Projekt ausdruecklich ausgeschlossen.
 *
 * <p><b>Ein Besuch hat seit Teil E5 ZWEI Folgen</b>, und beide gehoeren zum selben
 * Gegenstand: Die Stadt bekommt ihren Tag, und wenn es ihr BESITZER ist, wacht eine
 * verfallene Stadt wieder auf. Eine zweite Klasse mit demselben Blick waere eine zweite
 * Rechnung darueber, in welcher Stadt ein Spieler steht.
 *
 * <p><b>JEDER Spieler zaehlt, auch der Besitzer.</b> Das ist die Lesart von "unbesucht":
 * Eine Stadt, deren Besitzer monatlich durchgeht, ist in Gebrauch - und wer 399 Staedte
 * so am Leben halten will, muss jede einzeln betreten. Die zweite Haelfte der
 * Verfallsregel faengt den Rest: Ohne Stadtbuerger hilft auch ein Besuch nur ein Jahr.
 *
 * <p><b>ZWEI Waechter gegen dasselbe Schreiben, und beide braucht es</b> - dieselbe
 * Teilung wie bei {@link Mietlauf} und {@link Truhenlauf}:
 * <ul>
 *   <li>Der BILLIGE VORTEST hier: eine Menge der heute schon gestempelten Staedte, nur
 *       im Arbeitsspeicher. Der Takt schlaegt jede Minute, und eine bewohnte Stadt
 *       bekaeme sonst 1.440 Schreibvorgaenge am Tag statt einem.</li>
 *   <li>Die WAHRHEIT in der Zeile: {@code Freistadtbuch.stempleBesuch} schreibt nur,
 *       wenn der gespeicherte Tag sich aendert. Die Menge hier ueberlebt keinen
 *       Neustart, jene Zeile schon - nach einem Neustart mitten am Tag wird nicht ein
 *       zweites Mal geschrieben.</li>
 * </ul>
 * Das ist kein doppelter Riegel fuer denselben Fall: Der eine spart die Abfrage, der
 * andere ist ueber einen Neustart hinweg richtig.
 *
 * <p><b>Der WELTSCHLUESSEL und nicht der Weltname</b> - die Falle, die diese Klasse
 * umgeht. Die vorhandene {@code Messstelle.Ortsquelle} liefert
 * {@code getWorld().getName()}, also {@code freistaedte}; das Freistadtbuch rechnet mit
 * {@code getWorld().key().asString()}, also {@code freimarken:freistaedte}. Die zwei
 * sind nie gleich, und der Stempel waere stumm leer geblieben - kein Wurf, keine Klage,
 * nur eine Verfallsregel, die nach einem Jahr jede Stadt abraeumt. Darum fragt diese
 * Klasse den {@link Location} selbst.
 */
final class Besuchslauf {

    private final Freistadtbuch buch;
    private final Clock uhr;
    private final Wirtschaftsstand wirtschaft;

    /**
     * Fuer die EINE Zeile, die ein Besuch senden kann: die Wiederbelebung [Teil E5].
     *
     * <p>Das Stempeln selbst sendet nie - es faellt 1.440 Mal am Tag und waere eine
     * Durchsage ohne Anlass. Dass eine verfallene Stadt aufwacht, faellt dagegen
     * hoechstens einmal und ist ohne Satz unsichtbar: Der Besitzer steht dort, darf
     * ploetzlich bauen und weiss nicht, warum.
     */
    private final Sprache sprache;

    private final Logger protokoll;

    /**
     * Zu welchem Tag die Menge darunter gehoert.
     *
     * <p>Ein eigenes Feld und nicht ein Vergleich beim Lesen: Ohne es muesste die Menge
     * um Mitternacht von jemandem geleert werden, und "jemand" waere ein zweiter
     * Zeitgeber. So leert sie der erste Schlag des neuen Tages.
     */
    private LocalDate tag;

    /**
     * Die Nummern der Staedte, die an {@link #tag} schon gestempelt sind.
     *
     * <p>Nur im Arbeitsspeicher und absichtlich keine Spalte: Eine waere eine zweite
     * Wahrheit ueber "heute erledigt" - die erste steht in {@code parzellen.besucht}.
     */
    private final Set<Long> gestempelt = new HashSet<>();

    Besuchslauf(Freistadtbuch buch, Clock uhr, Wirtschaftsstand wirtschaft,
            Sprache sprache, Logger protokoll) {
        if (buch == null) {
            throw new IllegalArgumentException("Ohne Freistadtbuch gibt es keine Stadt,"
                    + " die man besuchen koennte - diese Klasse entsteht nur, wenn es"
                    + " eines gibt");
        }
        this.buch = buch;
        this.uhr = uhr;
        this.wirtschaft = wirtschaft;
        this.sprache = sprache;
        this.protokoll = protokoll;
    }

    /**
     * Stempelt die Stadt, in der dieser Spieler steht - gerufen je Takt und Spieler.
     *
     * <p><b>Klagt nur und wirft nicht.</b> Der Aufrufer ist der Minutentakt, und seine
     * Ausnahme kostet die Spielzeit aller Spieler bis zum naechsten Neustart. Ein
     * verlorener Stempel kostet dagegen nichts: Der naechste Takt holt ihn nach, und
     * zwar in derselben Minute.
     *
     * <p><b>Bei einer Stoerung wird der Tag NICHT gemerkt</b> - die Stadt kommt nicht in
     * die Menge. Sonst waere ein einzelner Datenbankausfall ein Tag ohne Stempel,
     * obwohl jemand dort stand.
     */
    void imTakt(Player spieler) {
        try {
            LocalDate heute = heute();
            if (!heute.equals(tag)) {
                // Der erste Schlag eines neuen Tages: Die Menge gehoert zum alten.
                tag = heute;
                gestempelt.clear();
            }
            Location ort = spieler.getLocation();
            if (ort == null || ort.getWorld() == null) {
                return;
            }
            // Der SCHLUESSEL und nicht der Name - siehe den Klassenkopf.
            Optional<Parzelle> stadt = buch.stadtAn(ort.getWorld().key().asString(),
                    ort.getBlockX(), ort.getBlockZ());
            if (stadt.isEmpty()) {
                return;
            }
            // DIE WIEDERBELEBUNG ZUERST, und VOR dem Merker [Teil E5]: Sie faellt
            // hoechstens einmal, und sie darf nicht daran haengen, ob die Stadt heute
            // schon gestempelt ist. Sonst waere ein Besitzer, der nach einem Fremden
            // kommt, bis Mitternacht ausgesperrt.
            //
            // Nur der BESITZER weckt - die Regel steht im Kern (Freistadtbuch.beleb),
            // nicht hier: Ein Fremder, der durch eine verfallene Stadt laeuft, belebte
            // sie sonst fuer ihren abwesenden Besitzer mit.
            if (buch.beleb(stadt.get(), spieler.getUniqueId())) {
                Freistadttext.aufgewachtZeilen(sprache, stadt.get().name())
                        .forEach(spieler::sendMessage);
                protokoll.info("Freistadt " + stadt.get().name() + " ist wieder aktiv -"
                        + " ihr Besitzer steht darin.");
            }
            if (gestempelt.contains(stadt.get().id())) {
                return;
            }
            if (buch.stempleBesuch(stadt.get(), heute)) {
                protokoll.fine(() -> "Besuch gestempelt: " + stadt.get().name()
                        + " am " + heute);
            }
            // NACH dem Schreiben gemerkt, nicht davor: Bei einer Stoerung soll der
            // naechste Takt es erneut versuchen.
            gestempelt.add(stadt.get().id());
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Der Besuchsstempel ist in einen Fehler"
                    + " gelaufen. Der naechste Takt versucht es erneut; faellt er"
                    + " laenger aus, altert eine besuchte Stadt wie eine unbesuchte.",
                    fehler);
        }
    }

    /**
     * Der heutige Tag unter der eingestellten Zone.
     *
     * <p>Die Zone wird beim GEBRAUCH geholt und nie in ein Feld kopiert - dieselbe
     * Zeile wie in {@code Tagesbonus} und {@code Blockraeumung}. Ein Feld haette den
     * Stand des Serverstarts, und ein Neuladen von {@code zeit.zone} wirkte nie.
     */
    private LocalDate heute() {
        return Tagesgrenze.tagVon(uhr.instant(), wirtschaft.werte().zeit().zone());
    }
}
