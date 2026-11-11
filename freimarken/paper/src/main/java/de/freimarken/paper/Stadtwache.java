package de.freimarken.paper;

import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Parzelle;
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

    @Override
    public Urteil pruefe(Player spieler, Location ort) {
        if (!zustaendig(ort)) {
            return Urteil.NICHT_ZUSTAENDIG;
        }
        UUID wer = spieler == null ? null : spieler.getUniqueId();
        try {
            return buch.darfBauen(weltschluessel, wer, ort.getBlockX(), ort.getBlockZ())
                    == Freistadtbuch.Bauurteil.ERLAUBT
                    ? Urteil.ERLAUBT
                    : Urteil.VERBOTEN;
        } catch (RuntimeException fehler) {
            klage(fehler);
            return Urteil.VERBOTEN;
        }
    }

    @Override
    public Component grund(Location ort) {
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
            urteil = buch.darfBauen(weltschluessel, null,
                    ort.getBlockX(), ort.getBlockZ());
        } catch (RuntimeException fehler) {
            klage(fehler);
            return Schutztext.stadtGestoertBalken(sprache);
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
}
