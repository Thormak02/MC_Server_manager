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
    LEER("markt.leer", Art.SATZ);

    // SIEBEN SAETZE SIND AM 10.11.2026 WEGGEFALLEN, und zwar mit ihrem Befehl:
    // markt.verkauft und die sechs markt.absage.* gehoerten zu /markt verkaufen.
    // Verkauft wird seit diesem Schritt am SCHILD (Plan 7.1, entschieden 08.11.2026),
    // und was dort schiefgehen kann, sagen die Saetze unter shop.bund - an derselben
    // Stelle wie jede andere Antwort eines Kistenshops.
    //
    // Zwei davon haben dort einen Nachfolger, und das ist kein Umbenennen: Der Kunde
    // steht jetzt VOR einem Stand, also heisst "der Bund kauft das nicht" etwas anderes
    // als vorher mit einem Stein in der Hand. Die anderen fuenf haben keinen, weil ihre
    // Lage am Schild nicht mehr vorkommt:
    //
    //   falsche-welt   Der Markt ist jetzt ein ORT und keine Weltpruefung - das Schild
    //                  steht, wo es steht. Genau das wollte der Plan.
    //   leere-hand     Wer nichts hat, bekommt shop.keine-ware.
    //   zu-wenig       Kann es nicht mehr geben: Die Menge eines Standes MUSS einen
    //                  ganzen Mark-Betrag ergeben, sonst entsteht er nicht.
    //   nicht-buchbar  Wird shop.handel-gestoert.
    //
    // Die Zeilen in sprache-vorlage.yml sind mit ihnen gegangen. Ein Satz ohne Sender
    // ist eine Zeile, die ein Betreiber uebersetzt und die niemand liest - und
    // GrenzeTest macht genau das rot.

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
