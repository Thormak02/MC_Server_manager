package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze von {@code /markt}.
 *
 * <p>Neun Dateien statt einer, eine je Bereich: So teilen zwei gleichzeitig arbeitende
 * Haende nie dieselbe Datei. Die Dateien dieses Schrittes sind {@link Markttext} und
 * {@link MarktBefehle}.
 *
 * <p><b>Was hier steht, ist der Vertrag und kein Wort.</b> Pfad, Art und die Namen der
 * Werte - der Wortlaut steht NUR in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich: Wer beim Abschreiben einem Satz seinen
 * {@code {betrag}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Die Eigenheit dieses Bereichs: Die Materialnamen stehen NICHT in der
 * Sprachdatei.</b> Ein Sortiment aus vierzig Materialien braeuchte dort vierzig Worte -
 * eine zweite Abschrift der Liste aus der {@code config.yml}, und zwei Abschriften
 * derselben Tabelle laufen auseinander. Genau dieselbe Entscheidung wie bei der
 * Lohnliste. Gezeigt wird darum der technische Name ({@code cobblestone}), und das ist
 * derselbe, den der Betreiber eintraegt und den der Spieler im Spiel im Tooltip liest.
 */
enum MarktSatz implements Satz {

    /** Die Ueberschrift von {@code /markt}. */
    KOPF("markt.kopf", Art.SATZ),

    /**
     * Eine Zeile des Sortiments - Material und Preis je Stueck.
     *
     * <p>{@code preis} kommt aus {@code waehrung.pfennige}: Die Ankaufpreise sind
     * meistens Pfennige, und "0 Mark" waere die falsche Antwort fuer einen Preis, der
     * 40 Pfennige ist.
     */
    ZEILE("markt.zeile", Art.WORT, "material", "preis"),

    /**
     * Das ganze Sortiment in EINER Zeile.
     *
     * <p>Eine Zeile und nicht eine je Material: Vierzig Chatzeilen waeren eine Wand, und
     * der Spieler sucht darin seinen Block. Die Reihenfolge ist die der
     * {@code config.yml} - dort ordnet der Betreiber, und das ist seine Entscheidung.
     */
    SORTIMENT("markt.sortiment", Art.SATZ, "liste"),

    /** Der Trenner zwischen zwei Sortimentsstuecken. */
    ZEILEN_TRENNER("markt.zeilen-trenner", Art.WORT, Satz.LEER_ERLAUBT),

    /** Was heute noch aus Verkaeufen zu holen ist. */
    FREI("markt.frei", Art.SATZ, "betrag"),

    /** Der Bund kauft derzeit gar nichts - die Liste ist leer. */
    LEER("markt.leer", Art.SATZ),

    /**
     * Ein Verkauf ist durch.
     *
     * <p>Mit der Zahl der STUECKE, und das ist mehr als Hoeflichkeit: Der Bund nimmt nur
     * so viele, wie eine ganze Mark ergeben, und wer sieben Stein anbietet und fuenf
     * losgeworden ist, soll das lesen, statt nachzuzaehlen.
     */
    VERKAUFT("markt.verkauft", Art.GEZAEHLT, "anzahl", "material", "betrag"),

    /** Der Bundesmarkt ist ein ORT - wer nicht dort ist, kann nicht verkaufen. */
    ABSAGE_FALSCHE_WELT("markt.absage.falsche-welt", Art.SATZ),

    /** Mit leerer Hand gibt es nichts zu verkaufen. */
    ABSAGE_LEERE_HAND("markt.absage.leere-hand", Art.SATZ),

    /** Das kauft der Bund nicht - mit dem Hinweis auf die Liste. */
    ABSAGE_KAUFT_NICHT("markt.absage.kauft-nicht", Art.SATZ, "material"),

    /**
     * Zu wenig fuer eine ganze Mark.
     *
     * <p>Diese Zeile MUSS es geben: Sonst tippt ein Spieler {@code /markt verkaufen} mit
     * einem Stein in der Hand, es passiert nichts, und er haelt es fuer einen Fehler.
     */
    ABSAGE_ZU_WENIG("markt.absage.zu-wenig", Art.SATZ, "material"),

    /** Der Tagesdeckel des Marktes ist erreicht. */
    ABSAGE_DECKEL("markt.absage.deckel", Art.SATZ),

    /**
     * Die Kasse antwortet nicht.
     *
     * <p>Wie {@code beruf.absage.nicht-lesbar} und aus demselben Grund: Die Ware ist noch
     * da, der Server laeuft weiter, und der Spieler soll nicht raten, warum nichts
     * geschieht. Im Protokoll steht daneben die Ausnahme.
     */
    ABSAGE_NICHT_BUCHBAR("markt.absage.nicht-buchbar", Art.SATZ);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;
    private final boolean leerErlaubt;

    MarktSatz(String pfad, Art art, String... platzhalter) {
        this(pfad, art, false, platzhalter);
    }

    MarktSatz(String pfad, Art art, boolean leerErlaubt, String... platzhalter) {
        this.pfad = pfad;
        this.art = art;
        this.platzhalter = Set.of(platzhalter);
        this.leerErlaubt = leerErlaubt;
    }

    @Override
    public String pfad() {
        return pfad;
    }

    @Override
    public Art art() {
        return art;
    }

    @Override
    public Set<String> platzhalter() {
        return platzhalter;
    }

    @Override
    public boolean darfLeerSein() {
        return leerErlaubt;
    }
}
