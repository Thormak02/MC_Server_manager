package de.freimarken.kern;

/**
 * Wie weit die Marktstadt reicht.
 *
 * <p>Reine Rechnerei wie der {@link Rasterplan}, bewusst ohne Minecraft. Die Klasse haelt
 * den Rasterplan mit - sonst gaebe es zwei Plaene, aus denen zwei verschiedene Kanten
 * folgen, und damit eine Grenze, die mitten durch eine Parzelle laeuft.
 *
 * <p>Die Kante wird nie uebernommen, wie sie dasteht, sondern <b>aus dem Raster
 * abgeleitet</b>: Aus dem Wunsch 3.000 werden 42 Rasterzellen je Achse (Nummern -21 bis
 * 20, zusammen 1.764 Parzellen) und daraus die tatsaechliche Kante 2.989. So kann kein
 * Zahlenwert in einer Einstellungsdatei eine Parzelle halbieren.
 *
 * <pre>
 *   Zelle n belegt    71n .. 71n+70
 *   davon Bauland     71n .. 71n+63
 *   davon Mauer+Weg   71n+64 .. 71n+70
 * </pre>
 *
 * <p>Die Grenze faellt auf die Aussenflaeche des umlaufenden Mauer-Weg-Rings: im Westen
 * auf -1.498 (der Ring der verbotenen Zelle -22, zugleich die Randmauer der Stadt), im
 * Osten auf 1.490 (der Ring der erlaubten Zelle 20). Jede erlaubte Parzelle liegt damit
 * vollstaendig innen, samt Weg auf allen vier Seiten, und kein einziger Baulandblock
 * einer verbotenen Parzelle liegt drin.
 *
 * <p>Die Mitte ist immer {@code -wegBreite/2}, also -3,5 - unabhaengig davon, wie viele
 * Zellen die Grenze umfasst. Deshalb bleibt sie bei jeder spaeteren Erweiterung gleich,
 * und Erweitern ist rein additiv: Was heute drin liegt, liegt auch danach drin.
 */
public final class Stadtgrenze {

    /** Bukkits Hoechstmass fuer die Weltgrenze - und die Vorgabe einer frischen Welt. */
    public static final double WELTGRENZE_HOECHSTMASS = 59_999_968.0;

    /** Die Kante aus Plan Abschnitt 4. Rueckfall, wenn die Einstellung unbrauchbar ist. */
    public static final int MARKTSTADT_KANTE = 3000;

    private final Rasterplan plan;
    private final int zellenJeAchse;

    private Stadtgrenze(Rasterplan plan, int zellenJeAchse) {
        this.plan = plan;
        this.zellenJeAchse = zellenJeAchse;
    }

    /**
     * Die Grenze zu einer gewuenschten Kante - abgerundet auf ganze Rasterzellen und auf
     * eine gerade Zellenzahl.
     *
     * <p>Gerade muss die Zahl sein, damit die Zellen symmetrisch um den Ursprung liegen und
     * die Mitte wirklich {@code -wegBreite/2} ist. Bei ungerader Zahl laege das Fenster um
     * eine ganze Rasterzelle schief, und {@link #kante()} waere keine ehrliche Angabe mehr.
     *
     * @throws IllegalArgumentException wenn die Kante keine zwei Zellen traegt oder
     *                                  oberhalb dessen liegt, was eine Weltgrenze annimmt
     */
    public static Stadtgrenze ausKante(Rasterplan plan, int gewuenschteKante) {
        int mindestens = mindestKante(plan);
        if (gewuenschteKante < mindestens) {
            throw new IllegalArgumentException("Die Kante muss mindestens " + mindestens
                    + " sein, damit zwei Rasterzellen hineinpassen, nicht " + gewuenschteKante);
        }
        if (gewuenschteKante > WELTGRENZE_HOECHSTMASS) {
            throw new IllegalArgumentException("Eine Weltgrenze nimmt hoechstens "
                    + (long) WELTGRENZE_HOECHSTMASS + " an, nicht " + gewuenschteKante);
        }
        int zellen = 2 * ((gewuenschteKante - plan.wegBreite()) / (2 * plan.rasterWeite()));
        return new Stadtgrenze(plan, zellen);
    }

    /** Die Grenze der Marktstadt nach Plan Abschnitt 4. */
    public static Stadtgrenze marktstadt() {
        return ausKante(Rasterplan.marktstadt(), MARKTSTADT_KANTE);
    }

    /** Die kleinste Kante, die noch eine Stadt ergibt: zwei Rasterzellen und ein Weg. */
    public static int mindestKante(Rasterplan plan) {
        return 2 * plan.rasterWeite() + plan.wegBreite();
    }

    /**
     * Welche Kante wirklich gilt - die eine Stelle, an der ueber das Verkleinern
     * entschieden wird.
     *
     * <p>Eine Weltgrenze wird nie nach innen geschoben; Plan 12a.6 fuehrt sie als nur
     * lesbar. Eine bezahlte Parzelle darf nicht vor die Tuer geraten, nur weil jemand eine
     * Zahl in einer Datei kleiner macht. Deshalb gilt der groessere Wert aus Einstellung
     * und dem, was in der Welt steht - und die Parzellennummern werden aus genau dieser
     * Zahl abgeleitet. Dadurch kann das, was der Spieler sieht, nie von dem abweichen, was
     * {@code nehmen()} zulaesst.
     *
     * <p>Nach oben gibt es die Gegenrichtung: Mehr als eine Verdopplung ist genauso
     * unumkehrbar wie eine Verkleinerung und braucht deshalb eine zweite Bestaetigung
     * (Plan 12a.6, Schranken gegen Vertipper).
     *
     * @param ausEinstellung   was in der Einstellungsdatei steht
     * @param inDerWelt        was die Welt heute als Kante fuehrt
     * @param sprungBestaetigt ob ein Sprung ueber das Doppelte ausdruecklich bestaetigt ist
     */
    public static int wirksameKante(Rasterplan plan, int ausEinstellung, double inDerWelt,
                                    boolean sprungBestaetigt) {
        int gewuenscht = brauchbar(plan, ausEinstellung) ? ausEinstellung : MARKTSTADT_KANTE;

        // Die Vorgabe einer frischen Welt heisst: Hier wurde noch nie eine Grenze gesetzt.
        // Exakt vergleichen und nicht gegen eine runde Million - sonst gaelte eine von Hand
        // auf zwei Millionen gesetzte Grenze als ungesetzt und wuerde verkleinert.
        if (inDerWelt >= WELTGRENZE_HOECHSTMASS) {
            return gewuenscht;
        }
        int gesetzt = (int) Math.min(Math.max(inDerWelt, 0.0), WELTGRENZE_HOECHSTMASS);

        // Unterhalb einer Stadt ist keine Stadt: Wer die Grenze von Hand auf 10 stellt, hat
        // keine Parzelle eingeschlossen, die zu schuetzen waere. Ohne diesen Riegel wuerde
        // ausKante() unten werfen - mitten im Start, zwischen Welt und Schutzwache.
        if (gesetzt < mindestKante(plan)) {
            return gewuenscht;
        }
        if (gewuenscht > 2 * gesetzt && !sprungBestaetigt) {
            return gesetzt;
        }
        return Math.max(gewuenscht, gesetzt);
    }

    /** Der Text, mit dem ein Sprung ueber das Doppelte bestaetigt wird, etwa "2989 -> 8000". */
    public static String sprungText(int ausEinstellung, double inDerWelt) {
        return (long) inDerWelt + " -> " + ausEinstellung;
    }

    private static boolean brauchbar(Rasterplan plan, int kante) {
        return kante >= mindestKante(plan) && kante <= WELTGRENZE_HOECHSTMASS;
    }

    public Rasterplan plan() {
        return plan;
    }

    /** Wie viele Rasterzellen auf einer Achse liegen - immer gerade. */
    public int zellenJeAchse() {
        return zellenJeAchse;
    }

    /** Kleinste Parzellennummer auf beiden Achsen. */
    public int von() {
        return -zellenJeAchse / 2;
    }

    /** Groesste Parzellennummer auf beiden Achsen. */
    public int bis() {
        return zellenJeAchse / 2 - 1;
    }

    /** Wie viele Parzellen die Stadt insgesamt hat. */
    public long parzellenZahl() {
        return (long) zellenJeAchse * zellenJeAchse;
    }

    /** Liegt diese Parzelle in der Stadt? */
    public boolean enthaeltParzelle(int parzelleX, int parzelleZ) {
        return parzelleX >= von() && parzelleX <= bis()
                && parzelleZ >= von() && parzelleZ <= bis();
    }

    /**
     * Liegt die Parzelle an dieser Weltkoordinate in der Stadt?
     *
     * <p>Antwortet ueber die Parzellennummer und nicht ueber den Block: Eine Parzelle, die
     * zur Haelfte draussen laege, ist damit von vornherein unmoeglich. Achtung - der
     * aeusserste Weg ist innerhalb der Grenze begehbar, gehoert aber zur Rasterzelle
     * davor. Wer eine Meldung fuer den Spieler baut, fragt deshalb erst
     * {@link Rasterplan#feldBei} und erst danach hier.
     */
    public boolean enthaelt(int weltX, int weltZ) {
        return enthaeltParzelle(plan.parzelleX(weltX), plan.parzelleZ(weltZ));
    }

    /** Die Kantenlaenge der Weltgrenze - ein Durchmesser, kein Radius. */
    public double kante() {
        return (double) plan.rasterWeite() * zellenJeAchse + plan.wegBreite();
    }

    /** Die Mitte der Weltgrenze auf beiden Achsen. Immer -wegBreite/2. */
    public double mitte() {
        return -plan.wegBreite() / 2.0;
    }

    /** Kleinste Weltkoordinate innerhalb der Grenze - die aeussere Randmauer. */
    public int kleinsterBlock() {
        return plan.ursprungX(von()) - plan.wegBreite();
    }

    /** Groesste Weltkoordinate innerhalb der Grenze - die aeussere Randmauer. */
    public int groessterBlock() {
        return plan.ursprungX(bis()) + plan.rasterWeite() - 1;
    }
}
