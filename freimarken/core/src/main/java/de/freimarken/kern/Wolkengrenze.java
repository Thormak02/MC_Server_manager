package de.freimarken.kern;

/**
 * Wie weit Wolkenwerder reicht.
 *
 * <p>Das Geschwister von {@link Feldgrenze} und {@link Inselgrenze}, und wie dort wird die
 * Kante nie uebernommen, wie sie in der Einstellungsdatei dasteht, sondern <b>aus dem
 * Raster abgeleitet</b>: Aus dem Wunsch 20.000 werden 38 Wolkenfelder je Achse (Nummern
 * -19 bis 18, zusammen 1.444 Felder) und daraus die tatsaechliche Kante 19.840 um
 * -192/-192.
 *
 * <p>Gerechnet wird hier nicht - Feldzahl, Rand und Mitte stehen in {@link Feldfenster},
 * das Raster darunter in {@link Feldrechnung}. Diese Klasse ist der NAME dieser Welt: Sie
 * kennt ihren Plan, ihre ausgelieferte Kante und das Wort fuer das, was sie zaehlt.
 *
 * <pre>
 *   Feld n belegt      512n .. 512n+511
 *   davon Wolkenfeld   512n .. 512n+127   (das Eigentum)
 *   davon Leere        512n+128 .. 512n+511
 * </pre>
 *
 * <p><b>Warum die Welt so viel groesser ist als Hafenwerder</b> (19.840 gegen 4.736): Die
 * Skyblockinsel ist der zweite gratis Einstieg des Spiels (Plan 6.2), und von etwas
 * Gratisem werden mehr Stuecke genommen als von einer Insel zu 75.000 Mark. 1.443
 * Wolkeninseln sind rund viermal so viele wie Meerinseln - und sie kosten nichts: Eine
 * leere Welt hat keine Chunks, solange niemand eine Insel nimmt. Mit Hafenwerders Kante
 * waeren es 323 gewesen, und die waeren eines Tages vergeben.
 *
 * <p>Die Grenze faellt auf die Aussenflaeche des umlaufenden Leere-Rings: im Westen auf
 * -10.112, im Osten auf 9.727. Jedes erlaubte Feld liegt damit vollstaendig innen, samt
 * seiner Leere auf allen vier Seiten - und kein einziger Block eines verbotenen Feldes
 * liegt drin.
 *
 * <p>Die Mitte ist immer {@code -leereBreite/2}, also -192 - unabhaengig davon, wie viele
 * Felder die Grenze umfasst. Deshalb bleibt sie bei jeder spaeteren Erweiterung gleich,
 * und Erweitern ist rein additiv: Welche Insel heute drin liegt, liegt auch danach drin.
 */
public final class Wolkengrenze {

    /** Die Kante aus der ausgelieferten Einstellung. Rueckfall, wenn sie unbrauchbar ist. */
    public static final int WOLKENWERDER_KANTE = 20_000;

    private final Wolkenplan plan;
    private final Feldfenster fenster;

    private Wolkengrenze(Wolkenplan plan, Feldfenster fenster) {
        this.plan = plan;
        this.fenster = fenster;
    }

    /**
     * Die Grenze zu einer gewuenschten Kante - abgerundet auf ganze Felder und auf eine
     * gerade Feldzahl ({@link Feldrechnung#felderFuer}).
     *
     * @throws IllegalArgumentException wenn die Kante keine zwei Felder traegt oder
     *                                  oberhalb dessen liegt, was eine Weltgrenze annimmt
     */
    public static Wolkengrenze ausKante(Wolkenplan plan, int gewuenschteKante) {
        return new Wolkengrenze(plan, Feldfenster.ausKante(plan.rechnung(),
                gewuenschteKante, "Wolkenfelder"));
    }

    /** Die Grenze von Wolkenwerder nach der ausgelieferten Einstellung. */
    public static Wolkengrenze wolkenwerder() {
        return ausKante(Wolkenplan.wolkenwerder(), WOLKENWERDER_KANTE);
    }

    /** Die kleinste Kante, die noch eine Welt ergibt: zwei Felder und eine Leere. */
    public static int mindestKante(Wolkenplan plan) {
        return plan.rechnung().mindestKante();
    }

    /**
     * Welche Kante wirklich gilt - siehe {@link Feldfenster#wirksameKante}.
     *
     * <p>Eine Weltgrenze wird nie nach innen geschoben, und hier gilt das so streng wie
     * bei den bezahlten Welten: Eine Wolkeninsel hat niemand bezahlt, aber jemand hat
     * Monate auf ihr gebaut.
     *
     * @param ausEinstellung   was in der Einstellungsdatei steht
     * @param inDerWelt        was die Welt heute als Kante fuehrt
     * @param sprungBestaetigt ob ein Sprung ueber das Doppelte ausdruecklich bestaetigt ist
     */
    public static int wirksameKante(Wolkenplan plan, int ausEinstellung, double inDerWelt,
                                    boolean sprungBestaetigt) {
        return Feldfenster.wirksameKante(plan.rechnung(), ausEinstellung, inDerWelt,
                WOLKENWERDER_KANTE, sprungBestaetigt);
    }

    /**
     * Das Fenster darunter - die geteilte Rechnung aus Feldzahl und Rand.
     *
     * <p>Herausgegeben und nicht verborgen, damit Fragen, die ALLE Feldrasterwelten
     * gleich beantworten, auch nur einmal dastehen - etwa die Ringsuche nach einem
     * freien Feld ({@link Feldfenster#freiesFeld}).
     */
    public Feldfenster fenster() {
        return fenster;
    }

    public Wolkenplan plan() {
        return plan;
    }

    /** Wie viele Wolkenfelder auf einer Achse liegen - immer gerade. */
    public int felderJeAchse() {
        return fenster.felderJeAchse();
    }

    /** Kleinste Feldnummer auf beiden Achsen. */
    public int von() {
        return fenster.von();
    }

    /** Groesste Feldnummer auf beiden Achsen. */
    public int bis() {
        return fenster.bis();
    }

    /** Wie viele Felder die Welt insgesamt hat - das Ankunftsfeld eingerechnet. */
    public long felderZahl() {
        return fenster.felderZahl();
    }

    /**
     * Wie viele Wolkeninseln es hoechstens geben kann.
     *
     * <p>Ein Feld weniger als {@link #felderZahl}: Das Ankunftsfeld gehoert niemandem.
     */
    public long inselnZahl() {
        return fenster.felderOhneAnkunft(Wolkenplan.ANKUNFTSFELD);
    }

    /** Liegt dieses Feld in der Welt? */
    public boolean enthaeltFeld(int feldX, int feldZ) {
        return fenster.enthaeltFeld(feldX, feldZ);
    }

    /**
     * Liegt das Feld an dieser Weltkoordinate in der Welt?
     *
     * <p>Antwortet ueber die Feldnummer und nicht ueber den Block. Achtung - die Leere am
     * Ostrand liegt innerhalb der Grenze, gehoert aber zum Feld davor. Wer eine Meldung
     * fuer den Spieler baut, fragt deshalb erst {@link Wolkenplan#feldBei} und erst danach
     * hier.
     */
    public boolean enthaelt(int weltX, int weltZ) {
        return fenster.enthaelt(weltX, weltZ);
    }

    /** Die Kantenlaenge der Weltgrenze - ein Durchmesser, kein Radius. */
    public double kante() {
        return fenster.kante();
    }

    /** Die Mitte der Weltgrenze auf beiden Achsen. Immer -leereBreite/2. */
    public double mitte() {
        return fenster.mitte();
    }

    /** Kleinste Weltkoordinate innerhalb der Grenze - der aeussere Rand des Leere-Rings. */
    public int kleinsterBlock() {
        return fenster.kleinsterBlock();
    }

    /** Groesste Weltkoordinate innerhalb der Grenze - der aeussere Rand des Leere-Rings. */
    public int groessterBlock() {
        return fenster.groessterBlock();
    }
}
