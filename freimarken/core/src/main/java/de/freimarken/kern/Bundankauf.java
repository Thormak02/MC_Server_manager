package de.freimarken.kern;

/**
 * Der Ankauf des Bundes AM SCHILD - die Bruecke zwischen Kistenshop und Bundesmarkt.
 *
 * <p><b>Verkauft wird am SCHILD und nicht per Befehl</b> [entschieden 08.11.2026, gebaut
 * 10.11.2026]. Ein Bundshop, der ANKAUFT, ist damit der Bundesmarkt: ein Schild je
 * Gegenstand, an einer Kiste, wie ein gewoehnlicher Laden. {@code /markt verkaufen} ist
 * dafuer weggefallen, {@code /markt liste} geblieben.
 *
 * <p>Der Grund steht im Plan (7.1) und ist keiner der Technik: Ein Markt, den man von
 * ueberall bedienen kann, ist ein Menue. Wer einmal weiss, dass der Befehl geht, geht nie
 * wieder hin - und damit ist das gebaute Gebaeude Zierde, und niemand trifft dort
 * jemanden. <b>Der Ort ist seit diesem Schritt das Gebaeude selbst</b> und keine
 * Weltpruefung mehr: Das Schild steht, wo es steht.
 *
 * <h2>Welche Zahl gilt - und warum nicht die auf dem Schild</h2>
 *
 * <p><b>Der Preis kommt aus {@code bundesmarkt.ankauf} und nicht aus der Shopzeile.</b>
 * Das ist die tragende Entscheidung dieser Klasse, und sie folgt aus der Zusage des
 * Plans: "Der Markt soll beliebig erweiterbar und anpassbar sein - ein neues Schild
 * hinstellen genuegt, das Sortiment steht weiter in {@code bundesmarkt.ankauf}."
 * Anpassbar heisst: Eine Preisaenderung wirkt an jedem Stand, ohne dass einer neu
 * beschrieben wird. Stuende der Preis in der Zeile, waere die Tafel in der
 * {@code config.yml} nach dem ersten Schild eine zweite Wahrheit - und
 * {@code /markt liste} nennte Preise, die kein Stand zahlt.
 *
 * <p>Die ZAHL AUF DEM SCHILD ist darum eine Anzeige. Sie wird beim Anlegen vom Programm
 * geschrieben ({@code Shopschild.schreibe} setzt sie aus der Shopzeile), und was der
 * Betreiber in die Preiszeile getippt hat, gilt an einem Ankaufstand nicht. Aendert
 * jemand spaeter die Tafel, nennt das Schild die alte Zahl, bis es neu beschrieben wird -
 * gehandelt wird trotzdem zur neuen, und das Protokoll sagt es bei jedem solchen Klick.
 *
 * <h2>Ganze Mark, oder gar nicht</h2>
 *
 * <p>{@link Verkaufsregel} nimmt nur Stuecke, die eine ganze Mark ergeben, und begruendet
 * das ausfuehrlich: Von sieben Stein zu 40 Pfennigen nimmt der Bund fuenf und zahlt zwei
 * Mark, zwei bleiben liegen. Am Schild ist das nicht moeglich - dort ist die Menge je
 * Handel FEST, und was nicht aufgeht, verschwindet. Darum muss die Menge des Standes die
 * Rechnung glatt machen: {@code menge * pfennige} muss durch 100 teilbar sein. Geht sie
 * nicht auf, entsteht der Stand nicht, und die Absage nennt die kleinste Menge, mit der
 * er entstehen wuerde ({@link #kleinsteMenge}).
 *
 * <h2>Der Tagesdeckel - die benannte Luecke vom 08.11.2026</h2>
 *
 * <p>Plan 7.4 fuehrte sie woertlich: "Ein Bundshop mit Verkaufspreis ist eine Geldquelle
 * ohne Tagesdeckel - der Deckel des Bundesmarkts rechnet ueber
 * {@code bundesmarkt_verkauf}, der Shop bucht {@code shop_verkauft}." Genau das schliesst
 * dieser Schritt: Ein Ankaufstand bucht {@link Buchungsgrund#BUNDESMARKT_VERKAUF}, zaehlt
 * sein Heute aus demselben Grund und rechnet ueber dieselbe {@link Deckelregel} wie der
 * Befehl vorher. Es gibt damit nur noch EINEN Weg vom Spieler zum Geld des Bundes, und
 * der ist gedeckelt.
 *
 * <p>Die MARKTGEBUEHR bleibt, wo sie war: {@code Shopwerte.bundshopVerkauf}, ab Werk 0.
 * Sie ist eine Ausgabe des Kunden und darum keine zweite Quelle - der Deckel haengt am
 * Gutgeschriebenen.
 */
public final class Bundankauf {

    private Bundankauf() {
    }

    /**
     * Ob dieser Klick ein Ankauf des Bundes ist - die EINE Stelle dieser Frage.
     *
     * <p>Zwei Tatsachen, und beide muessen stimmen: Der Shop gehoert dem Bund, und der
     * Kunde VERKAUFT. Die andere Richtung - ein Bundshop, bei dem ein Kunde kauft - ist
     * eine Senke und hat mit dem Bundesmarkt nichts zu tun; sie laeuft weiter ueber den
     * Preis in der Shopzeile, denn was der Bund VERKAUFT, steht in keiner Tafel.
     *
     * <p>Als benannte Frage und nicht als Vergleich an den drei Stellen, die sie
     * brauchen: Drei Abschriften von {@code demBund() && art == KUNDE_VERKAUFT} liefen
     * auseinander, und dann gilt der Deckel beim Rechnen und nicht beim Buchen.
     */
    public static boolean istAnkaufstand(Kistenshop shop, Handelsart art) {
        if (shop == null || art == null) {
            throw new IllegalArgumentException("Ohne Shop und Handelsart gibt es keine"
                    + " Frage nach dem Ankauf des Bundes");
        }
        return shop.demBund() && art == Handelsart.KUNDE_VERKAUFT;
    }

    /**
     * Ob die Rechnung an diesem Stand glatt aufgeht.
     *
     * <p>{@code menge * pfennige} muss durch 100 teilbar sein - sonst verschwindet bei
     * JEDEM Handel derselbe Pfennigrest, und zwar unsichtbar. Das ist genau der Fall, den
     * {@link Verkaufsregel} fuer den Befehl ausdruecklich vermieden hat, nur ohne den
     * Ausweg: Dort konnte sie Stuecke liegenlassen, hier ist die Menge fest.
     */
    public static boolean gehtGlattAuf(long pfennige, int menge) {
        if (pfennige < 1L) {
            throw new IllegalArgumentException("Ein Ankaufpreis von " + pfennige
                    + " Pfennigen ist keiner - was der Bund nicht kauft, steht gar nicht"
                    + " in der Liste, und dafuer gibt es kauftAn");
        }
        if (menge < 1) {
            throw new IllegalArgumentException("Eine Handelsmenge von " + menge
                    + " ist keine");
        }
        return (long) menge * pfennige % 100L == 0L;
    }

    /**
     * Die kleinste Menge, mit der die Rechnung glatt aufgeht - fuer die Absage.
     *
     * <p>Hundert geteilt durch den groessten gemeinsamen Teiler von Preis und Hundert.
     * Bei 40 Pfennigen sind es 5 Stueck (200 Pfennige, also 2 Mark), bei 250 Pfennigen 2
     * Stueck, bei einem Pfennig 100 Stueck, bei einem glatten Markpreis 1.
     *
     * <p><b>Die Zahl steht in der Absage</b>, und das ist mehr als Hoeflichkeit: Ein
     * Betreiber, der "diese Menge geht nicht" liest, probiert sonst Zahlen durch. Jedes
     * Vielfache dieser Menge geht ebenfalls - wer 16 Stein verkaufen will, nimmt 20.
     */
    public static int kleinsteMenge(long pfennige) {
        if (pfennige < 1L) {
            throw new IllegalArgumentException("Ein Ankaufpreis von " + pfennige
                    + " Pfennigen ist keiner");
        }
        return (int) (100L / groessterTeiler(pfennige % 100L, 100L));
    }

    /**
     * Was EINE Stufe dieses Standes einbringt, in ganzen Mark.
     *
     * <p>Wirft, wenn die Rechnung nicht aufgeht oder das Ergebnis kein brauchbarer Preis
     * ist. Beides ist hier kein Fall des Spielers, sondern einer, der VORHER geprueft
     * gehoert - {@link #gehtGlattAuf} und {@link #brauchbarerStufenpreis} sind genau
     * dafuer da, und die zwei Stellen, die diese Methode rufen, fragen beide zuerst. Ein
     * stiller Rueckfall auf eine abgerundete Zahl waere der verschwundene Pfennigrest aus
     * {@link Verkaufsregel}, und eine 0 waere ein Handel ohne Geld.
     */
    public static long stufenpreis(long pfennige, int menge) {
        if (!gehtGlattAuf(pfennige, menge)) {
            throw new IllegalArgumentException(menge + " Stueck zu " + pfennige
                    + " Pfennigen ergeben keinen ganzen Mark-Betrag. Bei einer FESTEN"
                    + " Handelsmenge verschwaende das jeden Handel denselben Rest -"
                    + " darum entsteht ein solcher Stand nicht. Die kleinste passende"
                    + " Menge ist " + kleinsteMenge(pfennige) + ".");
        }
        long preis = (long) menge * pfennige / 100L;
        if (!brauchbarerStufenpreis(pfennige, menge)) {
            throw new IllegalArgumentException(menge + " Stueck zu " + pfennige
                    + " Pfennigen sind " + preis + " Mark je Handel und damit ueber "
                    + Kistenshop.HOECHSTER_PREIS + ". So viel traegt keine Shopzeile -"
                    + " eine kleinere Menge traegt es.");
        }
        return preis;
    }

    /**
     * Ob aus Preis und Menge ein Stufenpreis wird, den eine Shopzeile traegt.
     *
     * <p>Erreichbar nur mit grossen Zahlen auf beiden Seiten: Erst ab etwa 2.900 Mark je
     * Stueck UND einer Menge in der Groessenordnung einer Doppelkiste kommt die Rechnung
     * ueber die Preisgrenze. Die Pruefung steht trotzdem hier und nicht als Zufall im
     * Satz {@link Kistenshop}: Dort entstuende aus demselben Fall ein Wurf mit einer
     * Meldung, die von Pfennigen nichts weiss.
     *
     * <p><b>Nur die OBERE Kante, und das ist ein Befund der Einspritzung vom
     * 10.11.2026.</b> Hier stand zusaetzlich {@code preis >= 1}, und diese Haelfte ist
     * unerreichbar: Beide Aufrufstellen fragen zuerst {@link #gehtGlattAuf}, und wenn
     * das ja sagt, ist {@code menge * pfennige} durch 100 teilbar und mindestens 1 -
     * also mindestens 100, und der Stufenpreis damit mindestens 1. Eine Einspritzung,
     * die den Riegel entfernte, lief vollstaendig gruen durch, und genau das ist das
     * Zeichen: Zwei Pruefungen fuer denselben Fall machen sich gegenseitig unpruefbar.
     * Die untere Kante haelt {@code gehtGlattAuf}, an EINER Stelle.
     *
     * <p>Die Pruefung auf {@code pfennige} und {@code menge} selbst bleibt: Sie ist die
     * Antwort auf eine Frage, die ein Aufrufer auch ohne gehtGlattAuf stellen darf, und
     * sie ist erreichbar - eine Probe stellt sie.
     */
    public static boolean brauchbarerStufenpreis(long pfennige, int menge) {
        if (pfennige < 1L || menge < 1) {
            return false;
        }
        return (long) menge * pfennige / 100L <= Kistenshop.HOECHSTER_PREIS;
    }

    /**
     * Wie viele Stufen der Tagesdeckel noch zulaesst - gedeckelt auf das Gewuenschte.
     *
     * <p>Gerechnet ueber {@link Deckelregel}, also durch dieselbe Rechnung wie der
     * Verdienstdeckel und wie {@code /markt verkaufen} vorher. Der WUNSCH ist, was dieser
     * Klick sonst buchen wuerde ({@code stufen * stufenpreis}) - so sieht der Deckel
     * genau die Summe, die auf dem Konto landet, und nicht eine gerundete davon.
     *
     * <p>Gibt 0, wenn heute nichts mehr geht. Der Aufrufer macht daraus die Absage; diese
     * Rechnung sagt nur, wie viel geht - dieselbe Arbeitsteilung wie bei
     * {@code Shopregel.passendeStufen}.
     *
     * @param tagesdeckel wie viele Mark ein Spieler am Tag aus Verkaeufen bekommt
     * @param heuteSchon  was er heute schon aus {@link Buchungsgrund#BUNDESMARKT_VERKAUF}
     *                    gutgeschrieben bekommen hat
     * @param stufenpreis was eine Stufe einbringt
     * @param gewuenscht  wie viele Stufen ohne den Deckel moeglich waeren
     */
    public static int stufenImDeckel(long tagesdeckel, long heuteSchon, long stufenpreis,
            int gewuenscht) {
        if (stufenpreis < 1L) {
            throw new IllegalArgumentException("Ohne Stufenpreis gibt es keine Stufen");
        }
        if (gewuenscht < 0) {
            throw new IllegalArgumentException("Es koennen nicht " + gewuenscht
                    + " Stufen gewuenscht sein");
        }
        if (gewuenscht == 0) {
            return 0;
        }
        long wunsch = (long) gewuenscht * stufenpreis;
        long erlaubt = Deckelregel.erlaubt(tagesdeckel, heuteSchon, wunsch);
        return (int) Math.min(gewuenscht, erlaubt / stufenpreis);
    }

    /** Der groesste gemeinsame Teiler - fuer {@link #kleinsteMenge}. */
    private static long groessterTeiler(long eines, long anderes) {
        long a = eines;
        long b = anderes;
        while (a != 0L) {
            long rest = b % a;
            b = a;
            a = rest;
        }
        return b;
    }
}
