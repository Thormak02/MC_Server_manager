package de.freimarken.kern;

/**
 * Die Zahlen der Wechselstube - zwei Kurse und der Wechseldeckel (Plan 8.1).
 *
 * <p>Dieselbe Bauart wie {@link Marktwerte} und {@link Verdienstwerte}: ein Satz, eine
 * Vorgabe im PROGRAMM, und zu jeder Zahl eine Frage, die der Leser stellen kann, ohne zu
 * werfen.
 *
 * <p><b>Gewechselt wird in GOLDMARK, in beiden Richtungen.</b> Das ist die erste
 * Entscheidung dieses Teils, und sie macht die ganze Rechnung ganzzahlig: Wer nach dem
 * Hinweg in MARK fragte, muesste mit 2.500 Mark zwei Goldmark bekommen und 500 Mark
 * behalten - ein Rest, den irgendeine Stelle eines Tages verschluckt. Mit der Goldmark als
 * Einheit gibt es keinen Rest, und die Goldmark ist ohnehin die grobe Einheit des Plans
 * ("Preise werden in Zehnern genannt, nicht in Hunderten").
 *
 * <p><b>Der Rueckweg ist 20 Prozent schlechter, und das ist der Zweck.</b> Plan 8.1: 1.000
 * Mark geben eine Goldmark, eine Goldmark gibt 800 Mark zurueck. Der Verlust macht den
 * Wechsel zu einer Entscheidung und nicht zu einem Verwahrort - und er ist der Grund,
 * warum der Hinweg keinen Deckel braucht: Er VERNICHTET Mark.
 *
 * @param markJeGoldmark was eine Goldmark KOSTET - der Hinweg, unbegrenzt
 * @param markZurueck    was eine Goldmark BRINGT - der Rueckweg, gedeckelt
 * @param tagesdeckel    wie viele MARK ein Spieler an einem Tag hoechstens aus Goldmark
 *                       zurueckholen darf. <b>0 heisst: der Rueckweg ist zu</b>
 */
public record Wechselwerte(long markJeGoldmark, long markZurueck, long tagesdeckel) {

    public static final String PFAD_HIN = "wechsel.mark-je-goldmark";

    public static final String PFAD_ZURUECK = "wechsel.mark-zurueck";

    public static final String PFAD_DECKEL = "wechsel.tagesdeckel";

    /** 1.000 Mark fuer eine Goldmark - Plan 8.1, "entschieden". */
    public static final long VORGABE_HIN = 1_000L;

    /** 800 Mark zurueck - 20 Prozent schlechter, Plan 8.1. */
    public static final long VORGABE_ZURUECK = 800L;

    /**
     * 10.000 Mark am Tag auf dem Rueckweg - Plan 8.1, "entschieden".
     *
     * <p>Diese Zahl traegt die tragende Zusage des ganzen Geldwegs: 400.000 Mark fuer eine
     * Freistadt brauchen damit <b>mindestens 40 Tage</b>, auch wenn jemand hundert Euro
     * ausgibt. "Pay to fast", nicht "pay to win". Wer den Geldweg enger will, senkt diese
     * Zahl - Plan 8.1 nennt 5.000 als das naechstliegende.
     */
    public static final long VORGABE_DECKEL = 10_000L;

    /**
     * Die Zahl, die den Rueckweg SCHLIESST.
     *
     * <p><b>Hier heisst die 0 etwas anderes als bei {@link Marktwerte#KEIN_DECKEL}</b>,
     * und das ist eine Entscheidung mit Begruendung, kein Versehen. Bei der Arbeit und am
     * Bundesmarkt hiesse ein Deckel von 0 "niemand darf verdienen" - das ist in einer
     * Einstellungsdatei nicht von einem Tippfehler zu unterscheiden, also heisst die 0
     * dort "kein Deckel". Hier ist es umgekehrt: Ein geschlossener Rueckweg ist eine
     * sinnvolle Einstellung (die Goldmark ist dann eine Einbahnstrasse, so wie es der
     * Rueckweg in ECHTGELD ohnehin ist), ein Rueckweg OHNE Deckel dagegen bricht die
     * Zusage aus {@link #VORGABE_DECKEL}. Wer wirklich keinen Deckel will, traegt eine
     * grosse Zahl ein und sieht dabei, was er tut.
     *
     * <p>Eine 0 und kein {@code true}/{@code false} - dieselbe Hausregel wie ueberall:
     * Das Projekt kennt kein {@code getBoolean}, denn Bukkit liest jedes Wort ausser
     * {@code true} stumm als {@code false}.
     */
    public static final long RUECKWEG_ZU = 0L;

    /**
     * Der hoechste Kurs - eine Milliarde Mark je Goldmark.
     *
     * <p>Keine Balancegrenze, sondern Platz: Zusammen mit {@link #HOECHSTE_ANZAHL} bleibt
     * jedes Produkt {@code anzahl * kurs} weit unter dem Zahlenraum. Ein uebergelaufenes
     * Produkt waere NEGATIV - also eine Gutschrift beim Kaufen.
     */
    public static final long HOECHSTER_KURS = 1_000_000_000L;

    /** Wie viele Goldmark ein Tausch hoechstens umfasst - siehe {@link #HOECHSTER_KURS}. */
    public static final long HOECHSTE_ANZAHL = 1_000_000_000L;

    /** Derselbe Hoechstwert wie bei den anderen zwei Tagesdeckeln. */
    public static final long HOECHSTER_DECKEL = Verdienstwerte.HOECHSTER_TAGESDECKEL;

    public static final Wechselwerte VORGABE =
            new Wechselwerte(VORGABE_HIN, VORGABE_ZURUECK, VORGABE_DECKEL);

    public Wechselwerte {
        if (!brauchbarerKurs(markJeGoldmark)) {
            throw new IllegalArgumentException("Der Hinweg ist " + markJeGoldmark
                    + " Mark je Goldmark und liegt nicht zwischen 1 und "
                    + HOECHSTER_KURS + ". Eine 0 hiesse: Goldmark fuer nichts.");
        }
        if (!brauchbarerKurs(markZurueck)) {
            throw new IllegalArgumentException("Der Rueckweg ist " + markZurueck
                    + " Mark je Goldmark und liegt nicht zwischen 1 und "
                    + HOECHSTER_KURS + ". Wer den Rueckweg schliessen will, setzt den"
                    + " Tagesdeckel auf " + RUECKWEG_ZU + " - ein Kurs von 0 waere ein"
                    + " Tausch, der nichts einbringt, und das liest niemand als Absage.");
        }
        if (markZurueck > markJeGoldmark) {
            // DER RIEGEL DIESES BAUSCHRITTES, und er faengt das einzige, was hier Geld
            // aus nichts macht: Bringt der Rueckweg MEHR, als der Hinweg kostet, ist
            // jeder Rundlauf ein Gewinn - 1.000 Mark hinein, eine Goldmark, 1.200 Mark
            // heraus. Das laeuft beliebig oft, jede einzelne Buchung ist richtig, und die
            // tragende Zusage des Buchungsbuchs BESTAETIGT jeden Stand: Sie prueft, dass
            // die Summe der Zeilen der Stand ist, und das stimmt dabei immer.
            //
            // Der Tagesdeckel hilft hier NICHT. Er begrenzt, wie viel an einem Tag
            // zurueckkommt, nicht das Verhaeltnis - und 10.000 Mark Gewinn am Tag, je
            // Spieler, ohne jede Taetigkeit, ist das Ende dieser Wirtschaft.
            throw new IllegalArgumentException("Der Rueckweg bringt " + markZurueck
                    + " Mark, der Hinweg kostet " + markJeGoldmark + " - damit ist jeder"
                    + " Rundlauf ein Gewinn von " + (markZurueck - markJeGoldmark)
                    + " Mark je Goldmark, beliebig oft wiederholbar. Der Rueckweg darf"
                    + " nie mehr bringen, als der Hinweg kostet.");
        }
        if (!brauchbarerDeckel(tagesdeckel)) {
            throw new IllegalArgumentException("Der Wechseldeckel ist " + tagesdeckel
                    + " und liegt nicht zwischen " + RUECKWEG_ZU + " und "
                    + HOECHSTER_DECKEL + ". Eine " + RUECKWEG_ZU + " heisst: der"
                    + " Rueckweg ist zu.");
        }
    }

    /**
     * Ob der Rueckweg ueberhaupt offen ist.
     *
     * <p>Eine benannte Frage und kein {@code > 0} an den Stellen, die sie brauchen - wie
     * {@link Marktwerte#gedeckelt()}. Drei Vergleiche daneben liefen auseinander, und
     * dann weist die Wechselstube ab, was die Auskunft daneben anbietet.
     */
    public boolean rueckwegOffen() {
        return tagesdeckel > RUECKWEG_ZU;
    }

    /** Was so viele Goldmark KOSTEN. */
    public long kaufpreis(long anzahl) {
        pruefeAnzahl(anzahl);
        return anzahl * markJeGoldmark;
    }

    /** Was so viele Goldmark BRINGEN. */
    public long erloes(long anzahl) {
        pruefeAnzahl(anzahl);
        return anzahl * markZurueck;
    }

    /**
     * Wie viele MARK der Deckel heute noch zulaesst.
     *
     * <p>Gerechnet mit {@link Deckelregel} und nicht von Hand: Es ist der dritte
     * Tagesdeckel dieses Projekts, und drei Abschriften derselben Rechnung laufen
     * auseinander.
     *
     * @param heuteSchon was heute schon zurueckgewechselt wurde, in Mark
     */
    public long freiHeute(long heuteSchon) {
        if (!rueckwegOffen()) {
            return 0L;
        }
        return Deckelregel.erlaubt(tagesdeckel, heuteSchon, tagesdeckel);
    }

    /**
     * Wie viele GOLDMARK heute noch zurueckgewechselt werden duerfen.
     *
     * <p>Abgerundet, und das ist der Grund fuer diese Methode: Bei 10.000 Mark Deckel und
     * 800 Mark je Goldmark sind das zwoelf Goldmark (9.600 Mark) und nicht zwoelf Komma
     * fuenf. Die dreizehnte waere 10.400 Mark und damit ueber dem Deckel - eine halbe
     * Goldmark gibt es im Spiel nicht, also wird abgerundet.
     */
    public long nochMoeglichHeute(long heuteSchon) {
        return freiHeute(heuteSchon) / markZurueck;
    }

    /** Dieselben Werte mit anderem Deckel - fuer die Proben und den Leser. */
    public Wechselwerte mitDeckel(long anderer) {
        return new Wechselwerte(markJeGoldmark, markZurueck, anderer);
    }

    /** Dieselben Werte mit anderen Kursen. */
    public Wechselwerte mitKursen(long hin, long zurueck) {
        return new Wechselwerte(hin, zurueck, tagesdeckel);
    }

    public static boolean brauchbarerKurs(long mark) {
        return mark >= 1L && mark <= HOECHSTER_KURS;
    }

    public static boolean brauchbarerDeckel(long mark) {
        return mark >= RUECKWEG_ZU && mark <= HOECHSTER_DECKEL;
    }

    /** Ob aus dieser Zahl ein Tausch werden kann - siehe {@link #HOECHSTE_ANZAHL}. */
    public static boolean brauchbareAnzahl(long anzahl) {
        return anzahl >= 1L && anzahl <= HOECHSTE_ANZAHL;
    }

    private static void pruefeAnzahl(long anzahl) {
        if (!brauchbareAnzahl(anzahl)) {
            throw new IllegalArgumentException("Ein Tausch ueber " + anzahl
                    + " Goldmark ist keiner - erlaubt ist 1 bis " + HOECHSTE_ANZAHL);
        }
    }
}
