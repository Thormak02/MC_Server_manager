package de.freimarken.kern;

/**
 * Die Zahlen des Kistenhandels - zwei Gebuehrensaetze.
 *
 * <p>Dieselbe Bauart wie {@link Mietwerte} und {@link Bonuswerte}: ein Satz, eine Vorgabe
 * im Programm, zu jedem Wert eine Frage, die der Leser stellen kann, ohne zu werfen.
 *
 * <p><b>Beide Saetze sind ab Werk 0</b> [entschieden 08.11.2026], und sie stehen trotzdem
 * in der Datei. Plan 8.2 fuehrt die Marktgebuehr als laufende Senke mit 5 %; ob sie
 * gebraucht wird, zeigt erst der Betrieb. Eine Zahl, die erst eingebaut wird, wenn man
 * sie braucht, wird unter Druck eingebaut - diese hier ist da, geprueft und auf 0.
 *
 * <p><b>Zwei Saetze und nicht einer</b>, und das ist der Grund: Der Spielerhandel und der
 * Ankauf durch den Bund sind zwei verschiedene Senken. Der Bundesmarkt ist die
 * Verwertung des Erarbeiteten - eine Gebuehr dort trifft jeden, der etwas abgibt. Der
 * Spielerhandel ist ein Umlauf zwischen Spielern - eine Gebuehr dort nimmt Geld aus dem
 * Kreis. Wer beides mit einer Zahl stellen muesste, koennte das eine nicht ohne das
 * andere aendern.
 *
 * @param spielerProzent was der Staat bei einem Handel zwischen Spielern abzieht - vom
 *                       VERKAEUFER, nie als Aufschlag (siehe {@link Shopregel})
 * @param bundProzent    was der Staat abzieht, wenn ein Spieler an einen BUNDSHOP
 *                       verkauft. Das ist eine Gebuehr auf die eigene Quelle und darum
 *                       getrennt: Sie senkt den Ankaufpreis, ohne die Tafel in
 *                       {@code bundesmarkt.ankauf} anzufassen
 */
public record Shopwerte(int spielerProzent, int bundProzent,
                        String bundWort) {

    public static final String PFAD_SPIELER = "handel.marktgebuehr-spieler-prozent";

    public static final String PFAD_BUND = "handel.marktgebuehr-bund-prozent";

    /**
     * 0 Prozent beim Spielerhandel [entschieden 08.11.2026].
     *
     * <p>Der Spielermarkt soll zuerst laufen. Eine Gebuehr auf den ersten Handel des
     * ersten Tages bremst genau das, was sie besteuern soll - und die Ladenmiete ist
     * ohnehin die staerkere Senke (Plan 7.1: "laufend, sichtbar, freiwillig").
     */
    public static final int VORGABE_SPIELER = 0;

    /**
     * 0 Prozent am Bundesmarkt.
     *
     * <p>Der Ankaufpreis IST hier schon der Abzug: Plan 8.2 setzt ihn bewusst niedrig,
     * damit Verkaufen die Verwertung bleibt und keine zweite Arbeit wird. Eine Gebuehr
     * darauf waere ein zweiter Abzug an derselben Stelle, und ein Betreiber, der den
     * Ankauf senken will, hat dafuer die Tafel.
     */
    public static final int VORGABE_BUND = 0;

    /**
     * Das Wort in der ersten Schildzeile, das einen Bundshop anzeigt.
     *
     * <p><b>Aus der Datei und nicht aus dem Programm</b>, wie jedes Wort, das ein Spieler
     * tippt - derselbe Nutzergrundsatz, nach dem Preise, Rangvorteile und Berufsloehne in
     * Dateien stehen. Ein Betreiber, der seinen Staat anders nennt, soll das Wort
     * aendern koennen, ohne das Plugin neu zu bauen.
     *
     * <p>Leer heisst: Bundshops lassen sich UEBER DAS SCHILD nicht anlegen. Das ist eine
     * zulaessige Einstellung und keine Stoerung - {@code Shopschild.istBundwort} faengt
     * sie selbst ab. Ein Server ohne Bundshops braucht kein Wort dafuer.
     */
    public static final String PFAD_WORT = "handel.bundshop-wort";

    /**
     * Das vorgegebene Wort.
     *
     * <p>Grossgeschrieben, aber der Vergleich ist es nicht: Wer "bund" tippt, bekommt
     * denselben Shop. Das steht in {@code Shopschild.istBundwort} und ist dort gepruefte
     * Sache - ein Spieler, der am Schild steht, soll nicht an der Grossschreibung
     * scheitern.
     */
    public static final String VORGABE_WORT = "Bund";

    /** So lang darf das Wort werden - eine Schildzeile. */
    public static final int LAENGSTES_WORT = Warenprobe.LAENGSTER_NAME;

    public static final Shopwerte VORGABE =
            new Shopwerte(VORGABE_SPIELER, VORGABE_BUND, VORGABE_WORT);

    public Shopwerte {
        if (!Shopregel.brauchbareGebuehr(spielerProzent)) {
            throw new IllegalArgumentException("Die Marktgebuehr fuer Spielerhandel ist "
                    + spielerProzent + " Prozent - erlaubt ist 0 bis "
                    + Shopregel.HOECHSTE_GEBUEHR_PROZENT + ".");
        }
        if (!Shopregel.brauchbareGebuehr(bundProzent)) {
            throw new IllegalArgumentException("Die Marktgebuehr am Bundesmarkt ist "
                    + bundProzent + " Prozent - erlaubt ist 0 bis "
                    + Shopregel.HOECHSTE_GEBUEHR_PROZENT + ".");
        }
        // Das Wort wird HIER normalisiert und nicht in jedem Aufrufer: null und ein
        // leeres Wort bedeuten dasselbe - Bundshops sind ueber ein Schild nicht
        // anzulegen -, und zwei Schreibweisen fuer dieselbe Einstellung sind zwei
        // Vergleiche, die irgendwann auseinandergehen.
        bundWort = bundWort == null ? "" : bundWort.trim();
        if (bundWort.length() > LAENGSTES_WORT) {
            throw new IllegalArgumentException("Das Bundwort \"" + bundWort + "\" ist "
                    + bundWort.length() + " Zeichen lang - auf eine Schildzeile passen "
                    + LAENGSTES_WORT + ". Ein laengeres Wort liesse sich nie eintippen,"
                    + " und die Einstellung wirkte nie.");
        }
    }

    /**
     * Dieselben Werte mit einem anderen Bundwort.
     *
     * <p>Ohne eigene Pruefung: {@code null} und die Laenge besorgt der Konstruktor, und
     * zwar fuer jeden Weg hierher. Das ist der Unterschied zu einer Pruefung in dieser
     * Methode - jene gaelte nur fuer den, der sie ruft.
     */
    public Shopwerte mitWort(String andere) {
        return new Shopwerte(spielerProzent, bundProzent, andere);
    }

    /** Dieselben Werte mit anderer Spielergebuehr - fuer die Proben und den Leser. */
    public Shopwerte mitSpieler(int andere) {
        return new Shopwerte(andere, bundProzent, bundWort);
    }

    /** Dieselben Werte mit anderer Bundgebuehr. */
    public Shopwerte mitBund(int andere) {
        return new Shopwerte(spielerProzent, andere, bundWort);
    }

    /**
     * Welcher Satz fuer diesen Shop gilt.
     *
     * <p>Die EINE Stelle, an der zwischen den zwei Saetzen gewaehlt wird. Zwei Stellen
     * waeren zwei Gelegenheiten, die Saetze zu vertauschen - und das faellt niemandem
     * auf, solange beide 0 sind.
     */
    public int prozentFuer(Kistenshop shop) {
        if (shop == null) {
            throw new IllegalArgumentException("Ohne Shop gibt es keine Gebuehr");
        }
        return shop.demBund() ? bundProzent : spielerProzent;
    }

    /** Ob ueberhaupt eine Gebuehr erhoben wird - fuer die Startzeile. */
    public boolean erhebtGebuehr() {
        return spielerProzent > 0 || bundProzent > 0;
    }
}
