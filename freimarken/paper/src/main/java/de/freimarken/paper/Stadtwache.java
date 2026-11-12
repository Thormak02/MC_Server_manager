package de.freimarken.paper;

import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Tat;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Der Schutz der Freistaedte - wer in einem Stadtfeld bauen darf.
 *
 * <p><b>Eine {@link Schutzwache.Ortsregel} und keine eigene Wache</b>, aus demselben
 * Grund wie beim {@link Ladenschutz}: Die fuenfzehn Bukkit-Zugaenge der Schutzwache -
 * Bloecke, Eimer, Feuer, Bilderrahmen, Tuerklinken, Fahrzeuge - muessten sonst ein
 * zweites Mal dastehen, und einer von ihnen waere irgendwann anders. Genau davor warnt
 * der erste Absatz von {@link Schutzwache}.
 *
 * <p><b>Diese Regel schliesst ein Loch, das seit Teil A offenstand</b> [11.11.2026]: In
 * der Welt der Stadtfelder griff bis heute GAR KEINE Wache. {@code darfHandeln} stieg
 * bei fremder Welt aus, keine der siebzehn Zuhoererklassen las den Stadtfeldplan, und
 * die Welt laeuft im Ueberlebensmodus. Jeder konnte in jedem Feld jeder fremden Stadt
 * bauen und abbauen - und der Kauf eines Grundstuecks haette gar keinen Unterschied
 * machen koennen, weil der Kaeufer dort vorher schon durfte.
 *
 * <p><b>Die Leere ist fuer jeden verboten</b>, auch fuer den Besitzer des Feldes
 * nebenan. Erst damit stimmt, was {@link de.freimarken.kern.Stadtfeldplan} ueber die
 * zweiunddreissig Bloecke Leere sagt: Die Barriere ist vier Bloecke hoch, und wer auf
 * ihre Oberkante steigt, baut von dort aus weiter - die Wand allein bruecken zu
 * verhindern, reicht nicht.
 *
 * <p>Wie der Ladenschutz antwortet sie bei stummer Ablage mit {@code VERBOTEN} und einer
 * gedrosselten Zeile im Protokoll. Ein stilles Ja waere eine Welt voller Staedte, die
 * bei jeder Netzstoerung offensteht.
 */
final class Stadtwache implements Schutzwache.Ortsregel {

    /** So selten wird ein Ausfall der Ablage ins Protokoll geschrieben. */
    private static final long KLAGEPAUSE_MS = 30_000L;

    private final Freistadtbuch buch;
    private final String weltschluessel;
    private final Sprache sprache;
    private final Logger protokoll;

    /** Wann zuletzt ueber eine stumme Ablage geklagt wurde - 0 heisst: noch nie. */
    private long letzteKlage;

    /**
     * @param buch           die Regel samt Geometrie - dieselbe Instanz, die auch
     *                       gruendet. Zwei waeren zwei Rechnungen darueber, wo ein Feld
     *                       liegt
     * @param weltschluessel die Welt der Stadtfelder, aus {@code freistaedte.welt} und
     *                       schon umgewandelt. DERSELBE Text wie bei
     *                       {@link FreistadtBefehle} und {@link WarpBefehle}
     */
    Stadtwache(Freistadtbuch buch, String weltschluessel, Sprache sprache,
               Logger protokoll) {
        if (buch == null) {
            throw new IllegalArgumentException("Ohne Freistadtbuch gibt es keine"
                    + " Geometrie, nach der diese Regel urteilen koennte");
        }
        if (weltschluessel == null || weltschluessel.isBlank()) {
            throw new IllegalArgumentException("Ohne Welt ist die Stadtwache eine Regel,"
                    + " die in JEDER Welt nach Stadtfeldern sucht");
        }
        this.buch = buch;
        this.weltschluessel = weltschluessel;
        this.sprache = sprache;
        this.protokoll = protokoll;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Die {@link Tat} geht an das Buch weiter</b> [12.11.2026, Teil
     * D4]: An derselben Stelle kann fuer einen Stadtbuerger eine Tuer erlaubt und ein
     * Block verboten sein, und was die Stadt hergibt, stellt ihr Besitzer ein. Ohne die
     * Tat waere diese Abstufung nicht ausdrueckbar.
     */
    @Override
    public Urteil pruefe(Player spieler, Tat tat, Location ort) {
        if (!zustaendig(ort)) {
            return Urteil.NICHT_ZUSTAENDIG;
        }
        UUID wer = spieler == null ? null : spieler.getUniqueId();
        try {
            // DAS URTEIL SAGT SELBST, ob es erlaubt [Teil D4]. Vorher stand hier eine
            // Positivliste zweier Urteile, und sie war der stille Weg: Ein drittes Ja -
            // und genau das ist ERLAUBT_STADTBUERGER - waere darin nicht vorgekommen und
            // auf VERBOTEN gefallen. Das Buergerrecht waere gebaut, in eigenen Proben
            // gruen und im Spiel wirkungslos gewesen; keine Klage haette es gesagt.
            Freistadtbuch.Bauurteil urteil = buch.darfBauen(weltschluessel, wer, tat,
                    ort.getBlockX(), ort.getBlockZ());
            return urteil.erlaubt() ? Urteil.ERLAUBT : Urteil.VERBOTEN;
        } catch (RuntimeException fehler) {
            klage(fehler);
            return Urteil.VERBOTEN;
        }
    }

    @Override
    public Component grund(Player spieler, Tat tat, Location ort) {
        if (!zustaendig(ort)) {
            // Zwischen dem Urteil und dieser Frage kann der Spieler die Welt gewechselt
            // haben. Dann ist der allgemeine Satz die ehrlichere Auskunft.
            return Schutztext.stadtGestoertBalken(sprache);
        }
        Freistadtbuch.Bauurteil urteil;
        try {
            // OHNE Spieler: Der Grund fragt nicht mehr, OB er darf - das ist entschieden
            // -, sondern WAS hier liegt. Mit Spieler kaeme bei seiner eigenen Stadt
            // ERLAUBT heraus, und dafuer gibt es keinen Handbalken.
            urteil = buch.darfBauen(weltschluessel, null, tat,
                    ort.getBlockX(), ort.getBlockZ());
        } catch (RuntimeException fehler) {
            klage(fehler);
            return Schutztext.stadtGestoertBalken(sprache);
        }
        // DER STADTBUERGER BEKOMMT SEINEN EIGENEN SATZ [Teil D4], und das ist der Grund,
        // aus dem diese Methode seit Teil D1 ueberhaupt einen Spieler hat: Wem die
        // Stadt mitgehoert - weil er ein Grundstueck darin hat -, dem sagt "Das ist die
        // Stadt von Anna" nichts. Er weiss, wessen Stadt es ist; er will wissen, was er
        // dort darf.
        //
        // NACH dem Grundstueckszweig und VOR dem Stadtzweig: Auf einem fremden
        // Grundstueck gilt dessen Besitzer, nicht die Buergerschaft - sonst waere Teil
        // C2 fuer jeden Buerger der Stadt aufgehoben. Das steht gleich unten.
        if (urteil == Freistadtbuch.Bauurteil.FREMDE_STADT && spieler != null
                && istBuerger(spieler, ort)) {
            return Schutztext.buergerDarfNichtBalken(sprache, tat);
        }
        // Das GRUNDSTUECK zuerst, in derselben Reihenfolge wie die Bauregel: Wer
        // auf einem vergebenen Grundstueck steht, soll dessen Besitzer lesen und nicht
        // den der Stadt - sonst schickte ihn der Balken zum Falschen.
        if (urteil == Freistadtbuch.Bauurteil.FREMDES_GRUNDSTUECK) {
            Optional<de.freimarken.kern.Grundstueck> darauf;
            try {
                darauf = buch.grundstueckAn(weltschluessel, ort.getBlockX(),
                        ort.getBlockZ());
            } catch (RuntimeException fehler) {
                klage(fehler);
                return Schutztext.stadtGestoertBalken(sprache);
            }
            if (darauf.isPresent()) {
                return Schutztext.fremdesGrundstueckBalken(sprache,
                        Spielername.von(darauf.get().eigentuemer()));
            }
            // Zwischen zwei Fragen aufgeloest - dann gilt wieder die Stadt.
            urteil = Freistadtbuch.Bauurteil.FREMDE_STADT;
        }
        if (urteil != Freistadtbuch.Bauurteil.FREMDE_STADT) {
            return Schutztext.stadtBalken(sprache, urteil);
        }
        Optional<Parzelle> stadt;
        try {
            stadt = buch.stadtAn(weltschluessel, ort.getBlockX(), ort.getBlockZ());
        } catch (RuntimeException fehler) {
            klage(fehler);
            return Schutztext.stadtGestoertBalken(sprache);
        }
        return stadt
                .map(gefunden -> Schutztext.fremdeStadtBalken(sprache, gefunden.name(),
                        Spielername.von(gefunden.eigentuemer())))
                // Verschwunden zwischen zwei Fragen - dann ist das Feld jetzt frei.
                .orElseGet(() -> Schutztext.stadtBalken(sprache,
                        Freistadtbuch.Bauurteil.FREIES_FELD));
    }

    /**
     * Ob diese Stelle ueberhaupt in der Stadtfelderwelt liegt.
     *
     * <p>Die WELT entscheidet und nicht das Feld - anders als beim {@link Ladenschutz},
     * wo die Einheit entscheidet: Dort kann eine Ladeneinheit auf einer Parzelle liegen,
     * hier gibt es in dieser Welt nichts anderes. Jede Stelle der Stadtfelderwelt geht
     * diese Regel etwas an, auch die Leere zwischen den Feldern - gerade die.
     */
    @Override
    public boolean zustaendig(Location ort) {
        return ort != null && ort.getWorld() != null
                && weltschluessel.equals(ort.getWorld().key().asString());
    }

    /**
     * Meldet einen Ausfall der Ablage - hoechstens einmal je Pause.
     *
     * <p>Gedrosselt wie bei {@link Schutzwache}: Ohne Drossel schreibt ein ausgefallener
     * Datenbankrechner in Sekunden ein Protokoll voll und verdeckt jede andere Meldung.
     */
    private void klage(RuntimeException fehler) {
        long jetzt = System.nanoTime();
        if (letzteKlage != 0L && (jetzt - letzteKlage) / 1_000_000L < KLAGEPAUSE_MS) {
            return;
        }
        letzteKlage = jetzt;
        protokoll.log(Level.WARNING, "Die Freistaedte sind nicht nachsehbar - in der"
                + " Stadtfelderwelt wird bis auf Weiteres JEDES Bauen abgelehnt."
                + " Das ist die sichere Richtung: Ein stilles Ja liesse jeden in jeder"
                + " fremden Stadt bauen.", fehler);
    }

    /**
     * Ob dieser Spieler Stadtbuerger der Stadt an dieser Stelle ist.
     *
     * <p>NUR fuer den Handbalken, und das ist der Grund, warum sie hier stehen darf: Sie
     * faellt hoechstens alle zwei Sekunden je Spieler (die Drossel in
     * {@code Schutzwache.verweigere}) und nicht bei jedem Block. Auf dem heissen Pfad
     * stellt {@code Freistadtbuch.darfBauen} dieselbe Frage selbst - und dort in der
     * Reihenfolge, die sie billig macht.
     *
     * <p>Fragt DIESELBE Methode wie die Bauregel: {@code Freistadtbuch.istStadtbuerger}
     * ist die eine Stelle, an der die Frage gestellt wird - und sie fragt
     * {@code Stadtbuergerschaft}, wo steht, dass der Stadtbesitzer nicht mitzaehlt. Ein
     * eigener Weg hierher waere der zweite Weg zur selben Tat.
     */
    private boolean istBuerger(Player spieler, Location ort) {
        try {
            Optional<Parzelle> stadt = buch.stadtAn(weltschluessel, ort.getBlockX(),
                    ort.getBlockZ());
            return stadt.isPresent() && buch.istStadtbuerger(weltschluessel,
                    stadt.get(), spieler.getUniqueId());
        } catch (RuntimeException fehler) {
            // Eine stumme Ablage heisst hier NICHT "kein Buerger": Der Balken faellt
            // dann auf den Satz der fremden Stadt zurueck, und das ist die mildere
            // Falschaussage - ein Buerger liest einmal den falschen Grund, statt dass
            // die Wache wirft, waehrend sie eine Absage begruenden soll.
            klage(fehler);
            return false;
        }
    }

}
