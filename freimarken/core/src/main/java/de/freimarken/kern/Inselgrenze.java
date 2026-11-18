package de.freimarken.kern;

/**
 * Wie weit Hafenwerder reicht.
 *
 * <p>Das Geschwister von {@link Feldgrenze}, und wie dort wird die Kante nie uebernommen,
 * wie sie in der Einstellungsdatei dasteht, sondern <b>aus dem Raster abgeleitet</b>: Aus
 * dem Wunsch 5.000 werden 18 Inselfelder je Achse (Nummern -9 bis 8, zusammen 324 Felder)
 * und daraus die tatsaechliche Kante 4.736 um -64/-64.
 *
 * <p><b>Warum das noetig ist</b> [gefunden 11.11.2026 bei den Freistaedten, gilt hier
 * wortgleich]: 5.000 ist kein Vielfaches von 256. Eine Grenze von 5.000 um 0/0 schnitte
 * Inselfelder an - und schlimmer: Sie liefe mitten durch Feld 9, dessen Mitte bei 2.367
 * liegt. Wer dort eine Insel kauft und hinfaehrt, stuende VOR der eigenen Weltgrenze, in
 * einer Welt, aus der ihn der Rand zurueckdraengt. Die Zahl in der Datei ist ein Wunsch,
 * kein Mass.
 *
 * <pre>
 *   Feld n belegt     256n .. 256n+255
 *   davon Inselfeld   256n .. 256n+127   (Eigentum: Land UND eigenes Wasser)
 *   davon Meer        256n+128 .. 256n+255
 * </pre>
 *
 * <p>Die Grenze faellt auf die Aussenflaeche des umlaufenden Meerrings: im Westen auf
 * -2.432, im Osten auf 2.303. Jedes erlaubte Inselfeld liegt damit vollstaendig innen,
 * samt seinem Wasser auf allen vier Seiten - und kein einziger Block eines verbotenen
 * Feldes liegt drin.
 *
 * <p>Die Mitte ist immer {@code -meerBreite/2}, also -64 - unabhaengig davon, wie viele
 * Felder die Grenze umfasst. Deshalb bleibt sie bei jeder spaeteren Erweiterung gleich,
 * und Erweitern ist rein additiv: Welche Insel heute drin liegt, liegt auch danach drin.
 */
public final class Inselgrenze {

    /** Die Kante aus der ausgelieferten Einstellung. Rueckfall, wenn sie unbrauchbar ist. */
    public static final int HAFENWERDER_KANTE = 5000;

    private final Inselplan plan;
    private final int felderJeAchse;

    private Inselgrenze(Inselplan plan, int felderJeAchse) {
        this.plan = plan;
        this.felderJeAchse = felderJeAchse;
    }

    /**
     * Die Grenze zu einer gewuenschten Kante - abgerundet auf ganze Felder und auf eine
     * gerade Feldzahl ({@link Feldrechnung#felderFuer}).
     *
     * @throws IllegalArgumentException wenn die Kante keine zwei Felder traegt oder
     *                                  oberhalb dessen liegt, was eine Weltgrenze annimmt
     */
    public static Inselgrenze ausKante(Inselplan plan, int gewuenschteKante) {
        int mindestens = mindestKante(plan);
        if (gewuenschteKante < mindestens) {
            throw new IllegalArgumentException("Die Kante muss mindestens " + mindestens
                    + " sein, damit zwei Inselfelder hineinpassen, nicht "
                    + gewuenschteKante);
        }
        if (gewuenschteKante > Grenzregel.HOECHSTMASS) {
            throw new IllegalArgumentException("Eine Weltgrenze nimmt hoechstens "
                    + (long) Grenzregel.HOECHSTMASS + " an, nicht " + gewuenschteKante);
        }
        return new Inselgrenze(plan, plan.rechnung().felderFuer(gewuenschteKante));
    }

    /** Die Grenze von Hafenwerder nach der ausgelieferten Einstellung. */
    public static Inselgrenze hafenwerder() {
        return ausKante(Inselplan.hafenwerder(), HAFENWERDER_KANTE);
    }

    /** Die kleinste Kante, die noch eine Welt ergibt: zwei Felder und ein Meer. */
    public static int mindestKante(Inselplan plan) {
        return plan.rechnung().mindestKante();
    }

    /**
     * Welche Kante wirklich gilt - die eine Stelle, an der ueber das Verkleinern
     * entschieden wird.
     *
     * <p>Wortgleich zu {@link Feldgrenze#wirksameKante}, und das ist kein Versehen: Eine
     * Weltgrenze wird nie nach innen geschoben. Eine bezahlte Meerinsel darf nicht vor die
     * Tuer geraten, nur weil jemand eine Zahl in einer Datei kleiner macht. Deshalb gilt
     * der groessere Wert aus Einstellung und dem, was in der Welt steht.
     *
     * @param ausEinstellung   was in der Einstellungsdatei steht
     * @param inDerWelt        was die Welt heute als Kante fuehrt
     * @param sprungBestaetigt ob ein Sprung ueber das Doppelte ausdruecklich bestaetigt ist
     */
    public static int wirksameKante(Inselplan plan, int ausEinstellung, double inDerWelt,
                                    boolean sprungBestaetigt) {
        return (int) Grenzregel.wirksam(ausEinstellung, inDerWelt, mindestKante(plan),
                HAFENWERDER_KANTE, sprungBestaetigt);
    }

    public Inselplan plan() {
        return plan;
    }

    /** Wie viele Inselfelder auf einer Achse liegen - immer gerade. */
    public int felderJeAchse() {
        return felderJeAchse;
    }

    /** Kleinste Feldnummer auf beiden Achsen. */
    public int von() {
        return -felderJeAchse / 2;
    }

    /** Groesste Feldnummer auf beiden Achsen. */
    public int bis() {
        return felderJeAchse / 2 - 1;
    }

    /** Wie viele Inselfelder die Welt insgesamt hat - die Ankunftsinsel eingerechnet. */
    public long felderZahl() {
        return (long) felderJeAchse * felderJeAchse;
    }

    /**
     * Wie viele Meerinseln es hoechstens geben kann.
     *
     * <p>Ein Feld weniger als {@link #felderZahl}: Die Ankunftsinsel gehoert niemandem.
     * Gefragt wird dabei, ob sie ueberhaupt innerhalb der Grenze liegt - sonst zoege eine
     * sehr kleine Welt eine Insel ab, die sie gar nicht hat.
     */
    public long inselnZahl() {
        return felderZahl()
                - (enthaeltFeld(Inselplan.ANKUNFTSINSEL, Inselplan.ANKUNFTSINSEL) ? 1 : 0);
    }

    /** Liegt dieses Feld in der Welt? */
    public boolean enthaeltFeld(int feldX, int feldZ) {
        return feldX >= von() && feldX <= bis() && feldZ >= von() && feldZ <= bis();
    }

    /**
     * Liegt das Feld an dieser Weltkoordinate in der Welt?
     *
     * <p>Antwortet ueber die Feldnummer und nicht ueber den Block: Ein Inselfeld, das zur
     * Haelfte draussen laege, ist damit von vornherein unmoeglich. Achtung - das Meer am
     * Ostrand liegt innerhalb der Grenze, gehoert aber zum Feld davor. Wer eine Meldung
     * fuer den Spieler baut, fragt deshalb erst {@link Inselplan#feldBei} und erst danach
     * hier.
     */
    public boolean enthaelt(int weltX, int weltZ) {
        return enthaeltFeld(plan.feldNummer(weltX), plan.feldNummer(weltZ));
    }

    /** Die Kantenlaenge der Weltgrenze - ein Durchmesser, kein Radius. */
    public double kante() {
        return plan.rechnung().weltkante(felderJeAchse);
    }

    /** Die Mitte der Weltgrenze auf beiden Achsen. Immer -meerBreite/2. */
    public double mitte() {
        return plan.rechnung().weltmitte();
    }

    /** Kleinste Weltkoordinate innerhalb der Grenze - der aeussere Rand des Meerrings. */
    public int kleinsterBlock() {
        return plan.rechnung().kleinsterBlock(von());
    }

    /** Groesste Weltkoordinate innerhalb der Grenze - der aeussere Rand des Meerrings. */
    public int groessterBlock() {
        return plan.rechnung().groessterBlock(bis());
    }
}
