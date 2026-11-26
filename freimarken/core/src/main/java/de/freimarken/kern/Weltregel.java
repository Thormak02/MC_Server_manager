package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Was eine Welt an Zerstoerung OHNE Spieler zulaesst - Explosionen, Feuer, Kolben.
 *
 * <p><b>Warum das nicht in die Schutzwache gehoert</b>, und zwar genau so, wie deren
 * Klassenkopf es seit Monaten ankuendigt: Jene Wache beantwortet "darf DIESER SPIELER
 * hier handeln". Eine TNT-Ladung hat keinen Spieler - jedenfalls keinen, der im
 * Augenblick der Explosion noch dabei waere -, ein Lavastrom hat keinen, und ein Enderman
 * schon gar nicht. Die Frage ist eine andere: "gehoert dieser Block jemandem". Sie kommt
 * aus derselben Quelle ({@code Besitzbuch.zustaendigerBesitz}), und genau darum ist es
 * keine zweite Regel, sondern ein zweiter Zugang zu derselben.
 *
 * <p><b>ZWEI SORTEN, und die Teilung ist die Entscheidung dieses Teils:</b>
 *
 * <ul>
 *   <li><b>{@link #EXPLOSION}, {@link #FEUER}, {@link #WESEN} wirken WELTWEIT.</b> Sie
 *       sind reine Zerstoerung, und wer sie fuer eine Welt eintraegt, will dort keine
 *       Krater - auch nicht auf einer freien Parzelle und nicht auf dem Weg. Nur den
 *       Besitz zu schuetzen waere die halbe Einstellung: Ein Spieler kratert dann die
 *       Nachbarschaft, und der naechste bekommt eine Parzelle mit einem Loch.</li>
 *   <li><b>{@link #KOLBEN} und {@link #FLUESSIGKEIT} wirken nur ueber BESITZGRENZEN.</b>
 *       Sie sind Spielinhalt - eine Farm braucht Wasser, eine Maschine braucht Kolben -,
 *       und sie ganz zu verbieten naehme dem Spiel mehr, als es dem Nachbarn gibt.
 *       Abgebrochen wird darum nur der UEBERGRIFF: wenn das Ziel jemandem gehoert und
 *       die Quelle nicht demselben.</li>
 * </ul>
 *
 * <p><b>Namen und keine Wahrheitswerte</b>, dieselbe Begruendung wie bei
 * {@link Blocktat}: Bukkit liest jedes Wort ausser {@code true} stumm als {@code false}.
 * Aus einem {@code explosion: ja} wuerde eine Welt ohne Schutz, ohne eine Zeile Klage -
 * und der Betreiber suchte Monate spaeter den Krater, den er verhindert zu haben glaubte.
 * Eine Weissliste beklagt dagegen jeden Namen, den sie nicht kennt, und nennt die
 * erlaubten.
 */
public enum Weltregel {

    /**
     * TNT, Creeper, Feuerkugeln, Betten im Nether, Endkristalle.
     *
     * <p>Der Knall bleibt, der Schaden faellt weg - {@code blockList()} wird geleert. Die
     * andere Richtung (das Ereignis abbrechen) ist NICHT dasselbe: Ein abgebrochenes
     * {@code EntityExplodeEvent} laesst in Paper je nach Quelle das TNT stehen oder
     * erneut zuenden, und ein Creeper, der nicht explodiert, bleibt am Spieler haengen.
     */
    EXPLOSION("explosion"),

    /**
     * Feuer, das sich AUSBREITET oder einen Block abbrennt.
     *
     * <p>Nicht das Anzuenden: Das ist eine Spielerhandlung und steht in der Schutzwache
     * ({@code beimAnzuenden}). Hier geht es um das, was danach von selbst geschieht -
     * und das ist der Teil, der ueber eine Parzellengrenze laeuft, waehrend der
     * Anzuender schon weg ist.
     */
    FEUER("feuer"),

    /**
     * Wesen, die Bloecke veraendern - Enderman, Ziege, Schaf, Kaninchen.
     *
     * <p>Ein Enderman nimmt einen Block mit und setzt ihn irgendwo ab; eine Ziege rammt
     * Bloecke; ein Schaf frisst Gras. Nichts davon ist boese gemeint, und alles davon
     * hinterlaesst Loecher in einem gebauten Stadtbild.
     */
    WESEN("wesen"),

    /**
     * Kolben, die einen Block ueber eine Besitzgrenze schieben oder ziehen.
     *
     * <p>NUR ueber die Grenze: Ein Kolben auf der eigenen Parzelle bleibt unangetastet.
     * Der Fall, den es gibt, ist der Spieler, der an der Grenze einen Kolben baut und
     * damit den Zaun des Nachbarn verschiebt - ohne je einen Block auf dessen Grund
     * angefasst zu haben.
     */
    KOLBEN("kolben"),

    /**
     * Wasser und Lava, die in fremden Besitz fliessen.
     *
     * <p>NUR hinein: Vom eigenen Grund auf den Weg darf es laufen, sonst funktioniert
     * keine Farm am Rand einer Parzelle. Was abgebrochen wird, ist der Strom, der in
     * fremdes Gebautes laeuft - und Lava, die ein Haus anzuendet, ist genau der Fall,
     * fuer den es diese Regel gibt.
     */
    FLUESSIGKEIT("fluessigkeit");

    private final String schluessel;

    Weltregel(String schluessel) {
        this.schluessel = schluessel;
    }

    /** Wie die Regel in der {@code config.yml} heisst. */
    public String schluessel() {
        return schluessel;
    }

    /**
     * Ob diese Regel WELTWEIT wirkt oder nur ueber Besitzgrenzen.
     *
     * <p>Die Frage steht HIER und nicht in der Wache, und das ist der Grund: Kommt eine
     * sechste Regel dazu, muss ihre Art an genau einer Stelle entschieden werden. Stuende
     * die Unterscheidung als {@code switch} in der Wache, waere die neue Regel dort
     * stumm im falschen Zweig - und ein stumm nicht wirkender Schutz ist schlimmer als
     * keiner, weil der Betreiber ihn eingetragen hat.
     */
    public boolean giltWeltweit() {
        return switch (this) {
            case EXPLOSION, FEUER, WESEN -> true;
            case KOLBEN, FLUESSIGKEIT -> false;
        };
    }

    /** Die Regel zu einem Namen aus der Datei - oder {@code null}. */
    public static Weltregel ausSchluessel(String name) {
        if (name == null) {
            return null;
        }
        String sauber = name.trim().toLowerCase(Locale.ROOT);
        for (Weltregel regel : values()) {
            if (regel.schluessel.equals(sauber)) {
                return regel;
            }
        }
        return null;
    }

    /** Alle Namen, die die Datei kennt - fuer die Klage ueber einen Tippfehler. */
    public static List<String> erlaubteNamen() {
        List<String> namen = new ArrayList<>();
        for (Weltregel regel : values()) {
            namen.add(regel.schluessel);
        }
        return List.copyOf(namen);
    }
}
