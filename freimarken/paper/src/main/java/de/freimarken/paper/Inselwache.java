package de.freimarken.paper;

import de.freimarken.kern.Inselbuch;
import de.freimarken.kern.Inselplan;
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
 * Der Schutz von Hafenwerder - wer auf einer Meerinsel bauen darf.
 *
 * <p><b>Eine {@link Schutzwache.Ortsregel} und keine eigene Wache</b>, aus demselben
 * Grund wie bei {@link Stadtwache} und {@link Ladenschutz}: Die fuenfzehn Bukkit-Zugaenge
 * der Schutzwache - Bloecke, Eimer, Feuer, Bilderrahmen, Tuerklinken, Fahrzeuge - muessten
 * sonst ein zweites Mal dastehen, und einer von ihnen waere irgendwann anders.
 *
 * <p><b>Diese Regel ist der Grund, aus dem eine Insel 75.000 Mark wert ist</b>: Ohne sie
 * griffe in Hafenwerder GAR KEINE Wache - {@code darfHandeln} steigt bei fremder Welt aus,
 * keine andere Zuhoererklasse liest den Inselplan, und die Welt laeuft im
 * Ueberlebensmodus. Jeder koennte auf jeder fremden Insel bauen und abbauen, und der Kauf
 * machte keinen Unterschied, weil der Kaeufer dort vorher schon durfte. Dasselbe Loch
 * stand bei den Stadtfeldern bis zum 11.11.2026 offen.
 *
 * <p><b>Das offene Meer ist fuer jeden verboten</b>, auch fuer den Besitzer der Insel
 * daneben. Erst damit stimmt, was {@link Inselplan} ueber die 128 Bloecke Meer sagt: Es
 * ist das oeffentliche Wasser des Archipels. Hinueberschwimmen darf jeder - das
 * unterscheidet das Meer von der Leere zwischen den Stadtfeldern -, nur bauen nicht.
 *
 * <p>Wie die Stadtwache antwortet sie bei stummer Ablage mit {@code VERBOTEN} und einer
 * gedrosselten Zeile im Protokoll. Ein stilles Ja waere eine Welt voller bezahlter Inseln,
 * die bei jeder Netzstoerung offensteht.
 */
final class Inselwache implements Schutzwache.Ortsregel {

    /** So selten wird ein Ausfall der Ablage ins Protokoll geschrieben. */
    private static final long KLAGEPAUSE_MS = 30_000L;

    private final Inselbuch buch;
    private final String weltschluessel;
    private final Sprache sprache;
    private final Logger protokoll;

    /** Wann zuletzt ueber eine stumme Ablage geklagt wurde - 0 heisst: noch nie. */
    private long letzteKlage;

    /**
     * @param buch           die Regel samt Geometrie - dieselbe Instanz, die auch
     *                       vergibt. Zwei waeren zwei Rechnungen darueber, wo eine Insel
     *                       liegt
     * @param weltschluessel die Inselwelt, aus {@code inseln.welt} und schon umgewandelt.
     *                       DERSELBE Text wie bei {@link WerderBefehle}
     */
    Inselwache(Inselbuch buch, String weltschluessel, Sprache sprache, Logger protokoll) {
        if (buch == null) {
            throw new IllegalArgumentException("Ohne Inselbuch gibt es keine Geometrie,"
                    + " nach der diese Regel urteilen koennte");
        }
        if (weltschluessel == null || weltschluessel.isBlank()) {
            throw new IllegalArgumentException("Ohne Welt ist die Inselwache eine Regel,"
                    + " die in JEDER Welt nach Inselfeldern sucht");
        }
        this.buch = buch;
        this.weltschluessel = weltschluessel;
        this.sprache = sprache;
        this.protokoll = protokoll;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Die {@link Tat} geht NICHT an das Buch weiter</b>, anders als bei der
     * Stadtwache: Dort gibt es Stadtbuerger mit abgestuften Rechten, hier gibt es
     * Eigentuemer, Mitbauer und sonst niemanden. Wer eine Abstufung einfuehrt, nimmt sie
     * von hier an mit - der Parameter ist da.
     */
    @Override
    public Urteil pruefe(Player spieler, Tat tat, Location ort) {
        if (!zustaendig(ort)) {
            return Urteil.NICHT_ZUSTAENDIG;
        }
        UUID wer = spieler == null ? null : spieler.getUniqueId();
        try {
            return buch.darfBauen(weltschluessel, wer, ort.getBlockX(), ort.getBlockZ())
                    ? Urteil.ERLAUBT
                    : Urteil.VERBOTEN;
        } catch (RuntimeException fehler) {
            klage(fehler);
            return Urteil.VERBOTEN;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Vier Lagen, und die Reihenfolge der Fragen ist dieselbe wie im Buch: erst die
     * Geometrie, dann die Ablage. Wer sie umdreht, bekommt mitten im Meer den Balken
     * einer fremden Insel - denn {@code feldNummer} nennt dort das Feld DAVOR.
     */
    @Override
    public Component grund(Player spieler, Tat tat, Location ort) {
        if (!zustaendig(ort)) {
            // Zwischen dem Urteil und dieser Frage kann der Spieler die Welt gewechselt
            // haben. Dann ist der allgemeine Satz die ehrlichere Auskunft.
            return Schutztext.inselGestoertBalken(sprache);
        }
        Inselplan plan = buch.plan();
        if (plan.feldBei(ort.getBlockX(), ort.getBlockZ())
                != Inselplan.Feld.INSELFELD) {
            return Schutztext.inselBalken(sprache, WacheSatz.SCHUTZ_INSEL_MEER);
        }
        if (plan.istAnkunftsinsel(plan.feldNummer(ort.getBlockX()),
                plan.feldNummer(ort.getBlockZ()))) {
            return Schutztext.inselBalken(sprache, WacheSatz.SCHUTZ_INSEL_ANKUNFT);
        }
        Optional<Parzelle> insel;
        try {
            insel = buch.inselAn(weltschluessel, ort.getBlockX(), ort.getBlockZ());
        } catch (RuntimeException fehler) {
            klage(fehler);
            return Schutztext.inselGestoertBalken(sprache);
        }
        return insel
                .map(gefunden -> Schutztext.fremdeInselBalken(sprache,
                        Spielername.von(gefunden.eigentuemer())))
                // Niemandem vergeben - oder zwischen zwei Fragen aufgegeben.
                .orElseGet(() -> Schutztext.inselBalken(sprache,
                        WacheSatz.SCHUTZ_INSEL_FREI));
    }

    /**
     * Ob diese Stelle ueberhaupt in der Inselwelt liegt.
     *
     * <p>Die WELT entscheidet und nicht das Feld - wie bei der {@link Stadtwache}: In
     * dieser Welt gibt es nichts anderes. Jede Stelle von Hafenwerder geht diese Regel
     * etwas an, auch das offene Meer - gerade das.
     */
    @Override
    public boolean zustaendig(Location ort) {
        return ort != null && ort.getWorld() != null
                && weltschluessel.equals(ort.getWorld().key().asString());
    }

    /**
     * Meldet einen Ausfall der Ablage - hoechstens einmal je Pause.
     *
     * <p>Gedrosselt wie bei der {@link Stadtwache}: Ohne Drossel schreibt ein
     * ausgefallener Datenbankrechner in Sekunden ein Protokoll voll und verdeckt jede
     * andere Meldung.
     */
    private void klage(RuntimeException fehler) {
        long jetzt = System.nanoTime();
        if (letzteKlage != 0L && (jetzt - letzteKlage) / 1_000_000L < KLAGEPAUSE_MS) {
            return;
        }
        letzteKlage = jetzt;
        protokoll.log(Level.WARNING, "Die Meerinseln sind nicht nachsehbar - in"
                + " Hafenwerder wird bis auf Weiteres JEDES Bauen abgelehnt. Das ist die"
                + " sichere Richtung: Ein stilles Ja liesse jeden auf jeder fremden Insel"
                + " bauen.", fehler);
    }
}
