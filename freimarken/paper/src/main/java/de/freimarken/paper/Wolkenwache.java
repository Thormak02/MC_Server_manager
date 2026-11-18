package de.freimarken.paper;

import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Tat;
import de.freimarken.kern.Wolkenbuch;
import de.freimarken.kern.Wolkenplan;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Der Schutz von Wolkenwerder - wer auf einer Wolkeninsel bauen darf.
 *
 * <p>Zeile fuer Zeile die {@link Inselwache}, und aus denselben Gruenden: eine
 * {@link Schutzwache.Ortsregel} und keine eigene Wache (die fuenfzehn Bukkit-Zugaenge
 * stehen sonst zweimal da), die WELT entscheidet ueber die Zustaendigkeit, und eine stumme
 * Ablage heisst VERBOTEN plus eine gedrosselte Zeile im Protokoll.
 *
 * <p><b>Ohne diese Regel waere Wolkenwerder eine Welt ohne Eigentum</b>: Die Welt laeuft
 * im Ueberlebensmodus, {@code darfHandeln} steigt bei fremder Welt aus, und keine andere
 * Zuhoererklasse liest den Wolkenplan. Jeder koennte auf jeder fremden Insel abbauen -
 * und weil hier niemand bezahlt hat, waere nicht einmal ein Kaufpreis der Hinweis darauf,
 * dass etwas fehlt.
 *
 * <p><b>Die LEERE ist fuer jeden verboten</b>, auch fuer den Besitzer des Feldes daneben,
 * und das ist der eine Unterschied zu Hafenwerder: Dort trennt Wasser, und darin zu
 * schwimmen ist erlaubt. Hier trennt Nichts, und ein Block darin ist der erste Block
 * einer Bruecke - der zweite stuende schon im Blick des Nachbarn, dessen Insel 384
 * Bloecke weit weg sein soll.
 */
final class Wolkenwache implements Schutzwache.Ortsregel {

    /** So selten wird ein Ausfall der Ablage ins Protokoll geschrieben. */
    private static final long KLAGEPAUSE_MS = 30_000L;

    private final Wolkenbuch buch;
    private final String wolkenschluessel;
    private final Sprache sprache;
    private final Logger protokoll;

    /** Wann zuletzt ueber eine stumme Ablage geklagt wurde - 0 heisst: noch nie. */
    private long letzteKlage;

    /**
     * @param buch             die Regel samt Geometrie - dieselbe Instanz, die auch
     *                         vergibt. Zwei waeren zwei Rechnungen darueber, wo eine
     *                         Insel liegt
     * @param wolkenschluessel die Wolkenwelt, aus {@code wolken.welt} und schon
     *                         umgewandelt. DERSELBE Text wie bei {@code WolkeBefehle}
     */
    Wolkenwache(Wolkenbuch buch, String wolkenschluessel, Sprache sprache,
                Logger protokoll) {
        if (buch == null) {
            throw new IllegalArgumentException("Ohne Wolkenbuch gibt es keine Geometrie,"
                    + " nach der diese Regel urteilen koennte");
        }
        if (wolkenschluessel == null || wolkenschluessel.isBlank()) {
            throw new IllegalArgumentException("Ohne Welt ist die Wolkenwache eine Regel,"
                    + " die in JEDER Welt nach Wolkenfeldern sucht");
        }
        this.buch = buch;
        this.wolkenschluessel = wolkenschluessel;
        this.sprache = sprache;
        this.protokoll = protokoll;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Die {@link Tat} geht NICHT an das Buch weiter, wie bei der {@link Inselwache}:
     * Es gibt Eigentuemer, Mitbauer und sonst niemanden. Wer eine Abstufung einfuehrt,
     * nimmt sie von hier an mit - der Parameter ist da.
     */
    @Override
    public Urteil pruefe(Player spieler, Tat tat, Location ort) {
        if (!zustaendig(ort)) {
            return Urteil.NICHT_ZUSTAENDIG;
        }
        UUID wer = spieler == null ? null : spieler.getUniqueId();
        try {
            return buch.darfBauen(wolkenschluessel, wer, ort.getBlockX(), ort.getBlockZ())
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
     * Geometrie, dann die Ablage. Wer sie umdreht, bekommt mitten in der Leere den Balken
     * einer fremden Insel - denn {@code feldNummer} nennt dort das Feld DAVOR.
     */
    @Override
    public Component grund(Player spieler, Tat tat, Location ort) {
        if (!zustaendig(ort)) {
            // Zwischen dem Urteil und dieser Frage kann der Spieler die Welt gewechselt
            // haben. Dann ist der allgemeine Satz die ehrlichere Auskunft.
            return Schutztext.inselGestoertBalken(sprache);
        }
        Wolkenplan plan = buch.plan();
        if (plan.feldBei(ort.getBlockX(), ort.getBlockZ())
                != Wolkenplan.Feld.WOLKENFELD) {
            return Schutztext.inselBalken(sprache, WacheSatz.SCHUTZ_WOLKE_LEERE);
        }
        if (plan.istAnkunftsfeld(plan.feldNummer(ort.getBlockX()),
                plan.feldNummer(ort.getBlockZ()))) {
            return Schutztext.inselBalken(sprache, WacheSatz.SCHUTZ_WOLKE_ANKUNFT);
        }
        Optional<Parzelle> insel;
        try {
            insel = buch.inselAn(wolkenschluessel, ort.getBlockX(), ort.getBlockZ());
        } catch (RuntimeException fehler) {
            klage(fehler);
            return Schutztext.inselGestoertBalken(sprache);
        }
        return insel
                // DERSELBE Satz wie in Hafenwerder, und das ist Absicht: "Diese Insel
                // gehoert X" sagt in beiden Welten genau dasselbe. Ein zweiter Satz
                // waere ein zweites Wort fuer eine Sache.
                .map(gefunden -> Schutztext.fremdeInselBalken(sprache,
                        Spielername.von(gefunden.eigentuemer())))
                // Niemandem vergeben - oder zwischen zwei Fragen aufgegeben.
                .orElseGet(() -> Schutztext.inselBalken(sprache,
                        WacheSatz.SCHUTZ_WOLKE_FREI));
    }

    /**
     * Ob diese Stelle ueberhaupt in der Wolkenwelt liegt.
     *
     * <p>Die WELT entscheidet und nicht das Feld: In dieser Welt gibt es nichts anderes.
     * Jede Stelle von Wolkenwerder geht diese Regel etwas an, auch die Leere - gerade die.
     */
    @Override
    public boolean zustaendig(Location ort) {
        return ort != null && ort.getWorld() != null
                && wolkenschluessel.equals(ort.getWorld().key().asString());
    }

    /**
     * Meldet einen Ausfall der Ablage - hoechstens einmal je Pause.
     *
     * <p>Gedrosselt wie bei der {@link Inselwache}: Ohne Drossel schreibt ein
     * ausgefallener Datenbankrechner in Sekunden ein Protokoll voll und verdeckt jede
     * andere Meldung.
     */
    private void klage(RuntimeException fehler) {
        long jetzt = System.nanoTime();
        if (letzteKlage != 0L && (jetzt - letzteKlage) / 1_000_000L < KLAGEPAUSE_MS) {
            return;
        }
        letzteKlage = jetzt;
        protokoll.log(Level.WARNING, "Die Wolkeninseln sind nicht nachsehbar - in"
                + " Wolkenwerder wird bis auf Weiteres JEDES Bauen abgelehnt. Das ist die"
                + " sichere Richtung: Ein stilles Ja liesse jeden auf jeder fremden Insel"
                + " abbauen.", fehler);
    }
}
