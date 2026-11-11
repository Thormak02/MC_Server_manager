package de.freimarken.kern;

/**
 * Wie weit die Welt der Freistaedte reicht.
 *
 * <p>Das Geschwister von {@link Stadtgrenze}, und aus demselben Grund gebaut: Die Kante
 * wird nie uebernommen, wie sie in der Einstellungsdatei dasteht, sondern <b>aus dem
 * Raster abgeleitet</b>. Aus dem Wunsch 6.000 werden 20 Felder je Achse (Nummern -10 bis
 * 9, zusammen 400 Stadtfelder) und daraus die tatsaechliche Kante 5.792.
 *
 * <p><b>Warum das noetig ist</b> [gefunden 11.11.2026, Lesesuche ueber Teil A]: 6.000 ist
 * kein Vielfaches von 288. Eine Grenze von 6.000 um 0/0 schnitt 84 Felder an - und
 * schlimmer: Sie lief mitten durch Feld 10, dessen Mitte bei 3.007 liegt. Ein
 * {@code /warp freistadt} auf dieses Feld haette den Spieler VOR die eigene Weltgrenze
 * gesetzt, in eine Welt, aus der ihn der Rand zurueckdraengt. Die Zahl in der Datei ist
 * ein Wunsch, kein Mass.
 *
 * <pre>
 *   Feld n belegt     288n .. 288n+287
 *   davon Bauland     288n .. 288n+255
 *   davon Ostwand     288n+256
 *   davon Leere       288n+257 .. 288n+286
 *   davon Westwand    288n+287   (die des naechsten Feldes)
 * </pre>
 *
 * <p>Die Grenze faellt auf die Aussenflaeche des umlaufenden Leere-Rings: im Westen auf
 * -2.912 (der Ring des verbotenen Feldes -11, zugleich die Westwand von Feld -10), im
 * Osten auf 2.879 (der Ring des erlaubten Feldes 9, zugleich die Westwand des verbotenen
 * Feldes 10). Jedes erlaubte Stadtfeld liegt damit vollstaendig innen, samt seiner Wand
 * auf allen vier Seiten - und kein einziger Baulandblock eines verbotenen Feldes liegt
 * drin.
 *
 * <p>Die Mitte ist immer {@code -leereBreite/2}, also -16 - unabhaengig davon, wie viele
 * Felder die Grenze umfasst. Deshalb bleibt sie bei jeder spaeteren Erweiterung gleich,
 * und Erweitern ist rein additiv: Welche Stadt heute drin liegt, liegt auch danach drin.
 */
public final class Feldgrenze {

    /** Bukkits Hoechstmass fuer die Weltgrenze - und die Vorgabe einer frischen Welt. */
    public static final double WELTGRENZE_HOECHSTMASS = Grenzregel.HOECHSTMASS;

    /** Die Kante aus Plan 6.4. Rueckfall, wenn die Einstellung unbrauchbar ist. */
    public static final int FREISTAEDTE_KANTE = 6000;

    private final Stadtfeldplan plan;
    private final int felderJeAchse;

    private Feldgrenze(Stadtfeldplan plan, int felderJeAchse) {
        this.plan = plan;
        this.felderJeAchse = felderJeAchse;
    }

    /**
     * Die Grenze zu einer gewuenschten Kante - abgerundet auf ganze Felder und auf eine
     * gerade Feldzahl.
     *
     * <p>Gerade muss die Zahl sein, damit die Felder symmetrisch um den Ursprung liegen
     * und die Mitte wirklich {@code -leereBreite/2} ist. Bei ungerader Zahl laege das
     * Fenster um ein ganzes Feld schief, und das Ankunftsfeld 0/0 saesse nicht mehr in
     * der Mitte der Welt.
     *
     * @throws IllegalArgumentException wenn die Kante keine zwei Felder traegt oder
     *                                  oberhalb dessen liegt, was eine Weltgrenze annimmt
     */
    public static Feldgrenze ausKante(Stadtfeldplan plan, int gewuenschteKante) {
        int mindestens = mindestKante(plan);
        if (gewuenschteKante < mindestens) {
            throw new IllegalArgumentException("Die Kante muss mindestens " + mindestens
                    + " sein, damit zwei Stadtfelder hineinpassen, nicht "
                    + gewuenschteKante);
        }
        if (gewuenschteKante > WELTGRENZE_HOECHSTMASS) {
            throw new IllegalArgumentException("Eine Weltgrenze nimmt hoechstens "
                    + (long) WELTGRENZE_HOECHSTMASS + " an, nicht " + gewuenschteKante);
        }
        int felder = 2 * ((gewuenschteKante - plan.leereBreite())
                / (2 * plan.rasterWeite()));
        return new Feldgrenze(plan, felder);
    }

    /** Die Grenze der Freistaedte nach Plan 6.4. */
    public static Feldgrenze freistaedte() {
        return ausKante(Stadtfeldplan.freistaedte(), FREISTAEDTE_KANTE);
    }

    /** Die kleinste Kante, die noch eine Welt ergibt: zwei Felder und eine Leere. */
    public static int mindestKante(Stadtfeldplan plan) {
        return 2 * plan.rasterWeite() + plan.leereBreite();
    }

    /**
     * Welche Kante wirklich gilt - die eine Stelle, an der ueber das Verkleinern
     * entschieden wird.
     *
     * <p>Wortgleich zu {@link Stadtgrenze#wirksameKante}, und das ist kein Versehen: Eine
     * Weltgrenze wird nie nach innen geschoben. Eine bezahlte Freistadt darf nicht vor die
     * Tuer geraten, nur weil jemand eine Zahl in einer Datei kleiner macht. Deshalb gilt
     * der groessere Wert aus Einstellung und dem, was in der Welt steht.
     *
     * <p>Der Riegel nach unten ist wichtiger, als er aussieht: Ohne ihn wuerde
     * {@link #ausKante} weiter unten werfen - mitten im Start, zwischen Welt und Wache.
     *
     * @param ausEinstellung   was in der Einstellungsdatei steht
     * @param inDerWelt        was die Welt heute als Kante fuehrt
     * @param sprungBestaetigt ob ein Sprung ueber das Doppelte ausdruecklich bestaetigt ist
     */
    public static int wirksameKante(Stadtfeldplan plan, int ausEinstellung, double inDerWelt,
                                    boolean sprungBestaetigt) {
        return (int) Grenzregel.wirksam(ausEinstellung, inDerWelt, mindestKante(plan),
                FREISTAEDTE_KANTE, sprungBestaetigt);
    }

    /** Der Text, mit dem ein Sprung ueber das Doppelte bestaetigt wird, etwa "5792 -> 20000". */
    public static String sprungText(int ausEinstellung, double inDerWelt) {
        return Grenzregel.sprungText(ausEinstellung, inDerWelt);
    }

    public Stadtfeldplan plan() {
        return plan;
    }

    /** Wie viele Stadtfelder auf einer Achse liegen - immer gerade. */
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

    /** Wie viele Stadtfelder die Welt insgesamt hat - das Ankunftsfeld eingerechnet. */
    public long felderZahl() {
        return (long) felderJeAchse * felderJeAchse;
    }

    /**
     * Wie viele Freistaedte es hoechstens geben kann.
     *
     * <p>Ein Feld weniger als {@link #felderZahl}: Das Ankunftsfeld gehoert niemandem.
     * Gefragt wird dabei, ob es ueberhaupt innerhalb der Grenze liegt - sonst zoege eine
     * sehr kleine Welt eine Stadt ab, die sie gar nicht hat.
     */
    public long staedteZahl() {
        return felderZahl()
                - (enthaeltFeld(Stadtfeldplan.ANKUNFTSFELD, Stadtfeldplan.ANKUNFTSFELD)
                        ? 1 : 0);
    }

    /** Liegt dieses Feld in der Welt? */
    public boolean enthaeltFeld(int feldX, int feldZ) {
        return feldX >= von() && feldX <= bis() && feldZ >= von() && feldZ <= bis();
    }

    /**
     * Liegt das Feld an dieser Weltkoordinate in der Welt?
     *
     * <p>Antwortet ueber die Feldnummer und nicht ueber den Block: Ein Stadtfeld, das zur
     * Haelfte draussen laege, ist damit von vornherein unmoeglich. Achtung - die Wand und
     * die Leere am Ostrand sind innerhalb der Grenze, gehoeren aber zum Feld davor. Wer
     * eine Meldung fuer den Spieler baut, fragt deshalb erst
     * {@link Stadtfeldplan#feldBei} und erst danach hier.
     */
    public boolean enthaelt(int weltX, int weltZ) {
        return enthaeltFeld(plan.feldNummer(weltX), plan.feldNummer(weltZ));
    }

    /** Die Kantenlaenge der Weltgrenze - ein Durchmesser, kein Radius. */
    public double kante() {
        return (double) plan.rasterWeite() * felderJeAchse + plan.leereBreite();
    }

    /** Die Mitte der Weltgrenze auf beiden Achsen. Immer -leereBreite/2. */
    public double mitte() {
        return -plan.leereBreite() / 2.0;
    }

    /** Kleinste Weltkoordinate innerhalb der Grenze - der aeussere Rand der Leere. */
    public int kleinsterBlock() {
        return plan.feldAnfang(von()) - plan.leereBreite();
    }

    /** Groesste Weltkoordinate innerhalb der Grenze - der aeussere Rand der Leere. */
    public int groessterBlock() {
        return plan.feldAnfang(bis()) + plan.rasterWeite() - 1;
    }
}
