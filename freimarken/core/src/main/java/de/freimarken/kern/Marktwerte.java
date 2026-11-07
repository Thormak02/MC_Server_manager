package de.freimarken.kern;

import java.util.List;

/**
 * Die Zahlen des Bundesmarktes - der Tagesdeckel und das Sortiment.
 *
 * <p>Dieselbe Bauart wie {@link Verdienstwerte}: ein Satz, eine Vorgabe im PROGRAMM fuer
 * die Zahlen und KEINE fuer die Tabelle, und zu jeder Zahl eine Frage, die der Leser
 * stellen kann, ohne zu werfen.
 *
 * <p><b>Der Bundesmarkt ist die zweite gedeckelte Quelle</b> (Plan 8.2: "Verkauf an den
 * Bundesmarkt - kleines Sortiment, niedrige Preise"). Er ist mit Absicht KEINE zweite
 * Arbeit, sondern die Verwertung des Erarbeiteten: Wer nur verkauft, soll nicht so viel
 * bekommen wie wer arbeitet.
 *
 * <p><b>Ein EIGENER Tagesdeckel und nicht derselbe wie beim Verdienen</b>, und das ist die
 * tragende Entscheidung: Mit einem gemeinsamen Deckel waere jede der zwei Quellen durch
 * die andere zu verschliessen - wer vormittags am Markt verkauft, koennte nachmittags
 * nicht mehr arbeiten. Beide rechnen ueber {@link Deckelregel} und beide zaehlen aus dem
 * Buchungsbuch, jede nach ihrem eigenen {@link Buchungsgrund}.
 *
 * @param tagesdeckel wie viele Mark ein Spieler an einem Tag hoechstens aus VERKAEUFEN an
 *                    den Bund bekommt. Je Spieler, wie beim Verdienen
 * @param ankauf      was der Bund kauft und zu welchem Preis. Die TABELLE gehoert in
 *                    denselben Satz wie die Zahl, nicht daneben: Ein Neuladen tauscht
 *                    beides als EIN Stueck
 */
public record Marktwerte(long tagesdeckel, Ankaufliste ankauf) {

    public static final String PFAD_TAGESDECKEL = "bundesmarkt.tagesdeckel";

    /** Der Abschnitt mit dem Sortiment - je Material ein Preis in Pfennigen. */
    public static final String PFAD_ANKAUF = "bundesmarkt.ankauf";

    /**
     * 5.000 Mark am Tag - ein Viertel des Verdienstdeckels.
     *
     * <p>Gerechnet gegen die Rolle: Der Markt soll die Verwertung sein und keine zweite
     * Arbeit. Bei 2.000 bis 5.000 Mark je Spielstunde aus Arbeit (Plan 8.2) ist das
     * ungefaehr eine Stunde - genug, damit das Verkaufen sich lohnt, und zu wenig, damit
     * es die Arbeit ersetzt.
     *
     * <p>Ein Viertel und nicht die Haelfte: Die Ware, die hier verkauft wird, ist beim
     * Erarbeiten schon einmal bezahlt worden. Wer sie auch noch zum halben Tagessatz
     * verwertet, verdient am selben Block zweimal.
     */
    public static final long VORGABE_TAGESDECKEL = 5_000L;

    /** Derselbe Hoechstwert wie beim Verdienstdeckel - dieselbe Rechnung dahinter. */
    public static final long HOECHSTER_TAGESDECKEL = Verdienstwerte.HOECHSTER_TAGESDECKEL;

    /**
     * Die Vorgabe traegt eine LEERE Ankaufliste - und das ist Absicht.
     *
     * <p>Dieselbe Entscheidung wie bei der Lohnliste und mit demselben Grund: Eine
     * Tabelle im Programm waere eine zweite Abschrift der ausgelieferten Datei, und zwei
     * Abschriften laufen auseinander. Fehlt die Liste, kauft der Bund nichts - das ist die
     * sichere Richtung, denn ein geratener Ankaufpreis macht Geld, das keine
     * Ruecksicherung zurueckholt.
     */
    public static final Marktwerte VORGABE = new Marktwerte(VORGABE_TAGESDECKEL,
            Ankaufliste.ausAnkaeufen(List.of()));

    public Marktwerte {
        if (ankauf == null) {
            throw new IllegalArgumentException("Ohne Ankaufliste gibt es keinen Markt;"
                    + " eine leere Liste ist erlaubt und heisst: der Bund kauft nichts");
        }
        if (!brauchbarerDeckel(tagesdeckel)) {
            throw new IllegalArgumentException("Der Markt-Tagesdeckel ist " + tagesdeckel
                    + " und liegt nicht zwischen 1 und " + HOECHSTER_TAGESDECKEL
                    + ". Eine 0 hiesse, niemand kann verkaufen - wer den Ankauf"
                    + " abschalten will, laesst die Liste leer.");
        }
    }

    /** Dieselben Werte mit anderem Deckel - fuer die Proben und den Leser. */
    public Marktwerte mitDeckel(long anderer) {
        return new Marktwerte(anderer, ankauf);
    }

    /** Dieselben Werte mit einer anderen Ankaufliste. */
    public Marktwerte mitAnkauf(Ankaufliste andere) {
        return new Marktwerte(tagesdeckel, andere);
    }

    public static boolean brauchbarerDeckel(long mark) {
        return mark >= 1L && mark <= HOECHSTER_TAGESDECKEL;
    }
}
