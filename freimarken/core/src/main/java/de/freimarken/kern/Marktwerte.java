package de.freimarken.kern;

/**
 * Die Zahlen des Bundesmarktes - der Tagesdeckel.
 *
 * <p>Dieselbe Bauart wie {@link Verdienstwerte}: ein Satz, eine Vorgabe im PROGRAMM, und
 * zu jeder Zahl eine Frage, die der Leser stellen kann, ohne zu werfen.
 *
 * <p><b>Das SORTIMENT stand bis zum 10.11.2026 hier</b>, als Tafel {@code Ankaufliste}
 * aus {@code bundesmarkt.ankauf}: je Material ein Ankaufpreis in Pfennigen, und was dort
 * nicht stand, kaufte der Bund nicht. Die Tafel ist weg, und das ist eine Entscheidung
 * des Betreibers mit einem Satz, der sie ganz traegt: <b>"es kann ja nur ge- und verkauft
 * werden, wofuer es ein Schild gibt"</b>.
 *
 * <p>Damit ist das SCHILD das Tor und nicht die Datei. Ein Ankaufstand des Bundes ist seit
 * diesem Tag ein gewoehnlicher {@link Kistenshop} ohne Besitzer: Der Preis steht in seiner
 * Zeile, wie bei jedem anderen Laden, und wer einen Gegenstand ankaufen will, stellt ein
 * Schild dafuer hin. Eine zweite Liste daneben waere ein Tor hinter einem Tor - und sie
 * verbot dem Betreiber genau das, was er wollte: alles freizugeben.
 *
 * <p>Was damit WEGFAELLT, steht hier, weil es sonst niemand mehr findet: die Pfennige als
 * Einheit (ein Schildpreis ist immer eine ganze Mark), die Pruefung, dass Menge mal Preis
 * glatt aufgeht, die Absage "das kauft der Bund nicht", und die Regel, dass ein Bundschild
 * nur eine Richtung tragen darf. Jene Regel war der Riegel gegen einen Rundlauf zwischen
 * Tafelpreis und Schildpreis; ohne Tafel haelt {@link Kistenshop} beide Preise selbst
 * ("der Verkaufspreis darf nie ueber dem Kaufpreis liegen"), und der Rundlauf ist damit
 * unmoeglich.
 *
 * @param tagesdeckel wie viele Mark ein Spieler an einem Tag hoechstens aus VERKAEUFEN an
 *                    den Bund bekommt - je Spieler, wie beim Verdienen. <b>0 heisst: kein
 *                    Deckel</b> [entschieden 10.11.2026]
 */
public record Marktwerte(long tagesdeckel) {

    public static final String PFAD_TAGESDECKEL = "bundesmarkt.tagesdeckel";

    /**
     * Die Zahl, die KEIN Deckel bedeutet.
     *
     * <p><b>Eine 0 und kein {@code true}/{@code false}</b> - dieselbe Hausregel wie bei
     * {@code handel.zeilen-je-seite}: Das Projekt kennt kein {@code getBoolean}, denn
     * Bukkit liest jedes Wort ausser {@code true} stumm als {@code false}.
     *
     * <p><b>Bis zum 10.11.2026 war die 0 verboten</b>, mit der Begruendung "eine 0 hiesse,
     * niemand kann verkaufen - wer den Ankauf abschalten will, laesst die Liste leer".
     * Die Liste gibt es nicht mehr, und damit ist die 0 frei fuer die Bedeutung, die der
     * Betreiber braucht: ER entscheidet, ob es einen Deckel gibt, und nicht das Programm.
     * Abgeschaltet wird der Ankauf jetzt dadurch, dass kein Schild dafuer steht.
     */
    public static final long KEIN_DECKEL = 0L;

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

    public static final Marktwerte VORGABE = new Marktwerte(VORGABE_TAGESDECKEL);

    public Marktwerte {
        if (!brauchbarerDeckel(tagesdeckel)) {
            throw new IllegalArgumentException("Der Markt-Tagesdeckel ist " + tagesdeckel
                    + " und liegt nicht zwischen " + KEIN_DECKEL + " und "
                    + HOECHSTER_TAGESDECKEL + ". Eine " + KEIN_DECKEL + " heisst: kein"
                    + " Deckel; abgeschaltet wird der Ankauf dadurch, dass kein Schild"
                    + " dafuer steht.");
        }
    }

    /**
     * Ob ueberhaupt ein Deckel gilt.
     *
     * <p>Eine benannte Frage und kein {@code > 0} an den Stellen, die sie brauchen: Die
     * Bedeutung der 0 steht damit an EINER Stelle. Drei Vergleiche daneben liefen
     * auseinander, und dann deckelt der Handel, was die Auskunft fuer unbegrenzt erklaert.
     */
    public boolean gedeckelt() {
        return tagesdeckel > KEIN_DECKEL;
    }

    /** Dieselben Werte mit anderem Deckel - fuer die Proben und den Leser. */
    public Marktwerte mitDeckel(long anderer) {
        return new Marktwerte(anderer);
    }

    public static boolean brauchbarerDeckel(long mark) {
        return mark >= KEIN_DECKEL && mark <= HOECHSTER_TAGESDECKEL;
    }
}
