package de.freimarken.kern;

/**
 * Die Zahlen des Kistenhandels - drei Gebuehrensaetze und das Bundwort.
 *
 * <p>Dieselbe Bauart wie {@link Mietwerte} und {@link Bonuswerte}: ein Satz, eine Vorgabe
 * im Programm, zu jedem Wert eine Frage, die der Leser stellen kann, ohne zu werfen.
 *
 * <p><b>Alle Saetze sind ab Werk 0</b> [entschieden 08.11.2026], und sie stehen trotzdem
 * in der Datei. Plan 8.2 fuehrt die Marktgebuehr als laufende Senke mit 5 %; ob sie
 * gebraucht wird, zeigt erst der Betrieb. Eine Zahl, die erst eingebaut wird, wenn man
 * sie braucht, wird unter Druck eingebaut - diese hier sind da, geprueft und auf 0.
 *
 * <p><b>Je LAGE ein Satz und nicht einer fuer alles</b> [entschieden 08.11.2026]. Eine
 * Lage ist Shopart mal {@link Handelsart}, und die Lagen sind wirtschaftlich verschieden:
 *
 * <ul>
 *   <li>Der Spielerhandel ist ein UMLAUF zwischen Spielern - eine Gebuehr dort nimmt
 *       Geld aus dem Kreis. Und sie trifft je Richtung einen anderen: Wer am Shop kauft,
 *       laesst den BESITZER zahlen; wer an den Shop verkauft, zahlt selbst.</li>
 *   <li>Der Ankauf durch den Bund ist die VERWERTUNG des Erarbeiteten - eine Gebuehr
 *       dort trifft jeden, der etwas abgibt, und sie senkt den Ankaufpreis, ohne die
 *       Tafel in {@code bundesmarkt.ankauf} anzufassen.</li>
 * </ul>
 *
 * <p>Wer das mit einer Zahl stellen muesste, koennte das eine nicht ohne das andere
 * aendern - und WANN eine Gebuehr faellt, waere eine Entscheidung des Programms und
 * nicht des Betreibers.
 *
 * <p><b>Fuer den KAUF an einem Bundshop gibt es keinen Satz, und das ist kein
 * Versehen.</b> Die Gebuehr wird dem abgezogen, der das Geld BEKOMMT ({@link Shopregel}:
 * "vom VERKAEUFER, nie als Aufschlag"), und am Bundshop bekommt es niemand - der Bund
 * hat kein Konto. Ein vierter Schluessel waere eine Einstellung, die nie wirkt; dass es
 * ihn nicht gibt, steht als Satz in der {@code config.yml}.
 *
 * @param spielershopKauf    was der Staat dem SHOPBESITZER abzieht, wenn ein Kunde bei
 *                           ihm kauft
 * @param spielershopVerkauf was der Staat dem KUNDEN abzieht, wenn er an einen
 *                           Spielershop verkauft
 * @param bundshopVerkauf    was der Staat dem KUNDEN abzieht, wenn er an einen BUNDSHOP
 *                           verkauft - eine Gebuehr auf die eigene Quelle und darum
 *                           getrennt
 * @param bundWort           das Wort in der ersten Schildzeile, das einen Bundshop
 *                           anzeigt
 * @param zeilenJeSeite      wie viele Shops EINE Seite von {@code /shop liste} zeigt
 */
public record Shopwerte(int spielershopKauf, int spielershopVerkauf,
                        int bundshopVerkauf, String bundWort, int zeilenJeSeite) {

    public static final String PFAD_SPIELER_KAUF =
            "handel.marktgebuehr-spielershop-kauf-prozent";

    public static final String PFAD_SPIELER_VERKAUF =
            "handel.marktgebuehr-spielershop-verkauf-prozent";

    public static final String PFAD_BUND_VERKAUF =
            "handel.marktgebuehr-bundshop-verkauf-prozent";

    /**
     * 0 Prozent, wenn ein Kunde bei einem Spielershop kauft.
     *
     * <p>Der Spielermarkt soll zuerst laufen. Eine Gebuehr auf den ersten Handel des
     * ersten Tages bremst genau das, was sie besteuern soll - und die Ladenmiete ist
     * ohnehin die staerkere Senke (Plan 7.1: "laufend, sichtbar, freiwillig").
     */
    public static final int VORGABE_SPIELER_KAUF = 0;

    /** 0 Prozent, wenn ein Kunde an einen Spielershop verkauft - derselbe Grund. */
    public static final int VORGABE_SPIELER_VERKAUF = 0;

    /**
     * 0 Prozent am Bundesmarkt.
     *
     * <p>Der Ankaufpreis IST hier schon der Abzug: Plan 8.2 setzt ihn bewusst niedrig,
     * damit Verkaufen die Verwertung bleibt und keine zweite Arbeit wird. Eine Gebuehr
     * darauf waere ein zweiter Abzug an derselben Stelle, und ein Betreiber, der den
     * Ankauf senken will, hat dafuer die Tafel.
     */
    public static final int VORGABE_BUND_VERKAUF = 0;

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

    /**
     * Wie viele Shops EINE Seite von {@code /shop liste} zeigt.
     *
     * <p>Eine ZAHL und kein {@code true}/{@code false}: Das Projekt kennt kein
     * {@code getBoolean}, denn Bukkit liest jedes Wort ausser {@code true} stumm als
     * {@code false} - aus einem {@code liste-begrenzen: ja} waere eine unbegrenzte
     * Liste geworden, ohne eine Zeile Klage.
     *
     * <p>Und SEITEN statt eines Schnitts: Ein Schnitt bei zehn Zeilen verfehlte genau
     * den Zweck dieser Liste. Wer dreissig Laeden hat, findet seine Laeden nicht
     * wieder - und von denen sind einundzwanzig dann gar nicht erreichbar. "Unbegrenzt"
     * gibt es darum nicht: Der Chat ist kein Anzeigemittel fuer hundert Zeilen, und
     * wer mehr sehen will, blaettert.
     */
    public static final String PFAD_ZEILEN = "handel.zeilen-je-seite";

    /**
     * Zehn Zeilen je Seite.
     *
     * <p>Dieselbe Zahl wie {@code blockbuch.zeilen-je-abfrage}, und aus demselben
     * Grund: Der Chat zeigt etwa zehn Zeilen, ohne dass das Vorige weggescrollt ist.
     * Wer den Kopf nicht mehr sieht, weiss nicht, welche Seite er liest.
     */
    public static final int VORGABE_ZEILEN = 10;

    /**
     * Hundert Zeilen, und keine mehr.
     *
     * <p>Die Grenze faengt den Vertipper: Wer sich bei der Zehn um eine Null vertut,
     * bekaeme eine Seite, die den ganzen Chat fuellt - und zwar fuer jeden, der den
     * Befehl tippt. Hundert ist schon unbrauchbar, aber nicht schaedlich.
     */
    public static final int HOECHSTE_ZEILEN = 100;

    public static final Shopwerte VORGABE = new Shopwerte(VORGABE_SPIELER_KAUF,
            VORGABE_SPIELER_VERKAUF, VORGABE_BUND_VERKAUF, VORGABE_WORT,
            VORGABE_ZEILEN);

    public Shopwerte {
        pruefe("Die Marktgebuehr beim Kauf an einem Spielershop", spielershopKauf);
        pruefe("Die Marktgebuehr beim Verkauf an einen Spielershop", spielershopVerkauf);
        pruefe("Die Marktgebuehr beim Verkauf an einen Bundshop", bundshopVerkauf);
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
        if (!brauchbareZeilen(zeilenJeSeite)) {
            throw new IllegalArgumentException("Eine Seite der Shopliste soll "
                    + zeilenJeSeite + " Zeilen zeigen - erlaubt ist 1 bis "
                    + HOECHSTE_ZEILEN + ". Eine 0 hiesse: Die Liste zeigt nie etwas,"
                    + " und der Befehl waere stumm abgeschaltet.");
        }
    }

    /** Ob so viele Zeilen je Seite brauchbar sind - 1 bis {@link #HOECHSTE_ZEILEN}. */
    public static boolean brauchbareZeilen(long zeilen) {
        return zeilen >= 1 && zeilen <= HOECHSTE_ZEILEN;
    }

    private static void pruefe(String was, int prozent) {
        if (!Shopregel.brauchbareGebuehr(prozent)) {
            throw new IllegalArgumentException(was + " ist " + prozent
                    + " Prozent - erlaubt ist 0 bis "
                    + Shopregel.HOECHSTE_GEBUEHR_PROZENT + ".");
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
        return new Shopwerte(spielershopKauf, spielershopVerkauf, bundshopVerkauf,
                andere, zeilenJeSeite);
    }

    /** Dieselben Werte mit anderer Gebuehr beim Kauf am Spielershop. */
    public Shopwerte mitSpielerKauf(int andere) {
        return new Shopwerte(andere, spielershopVerkauf, bundshopVerkauf, bundWort,
                zeilenJeSeite);
    }

    /** Dieselben Werte mit anderer Gebuehr beim Verkauf an einen Spielershop. */
    public Shopwerte mitSpielerVerkauf(int andere) {
        return new Shopwerte(spielershopKauf, andere, bundshopVerkauf, bundWort,
                zeilenJeSeite);
    }

    /** Dieselben Werte mit anderer Gebuehr beim Verkauf an einen Bundshop. */
    public Shopwerte mitBundVerkauf(int andere) {
        return new Shopwerte(spielershopKauf, spielershopVerkauf, andere, bundWort,
                zeilenJeSeite);
    }

    /**
     * Dieselben Werte mit anderer Seitengroesse.
     *
     * <p>Die fuenfte {@code mitX}-Methode, und sie fehlte bis zum 10.11.2026: Solange
     * nur {@code /shop liste} blaetterte, setzte jede Probe ihre Seitengroesse ueber den
     * ganzen Satz. Seit {@code /markt liste} dieselbe Zahl benutzt, braucht es sie - und
     * eine Probe, die dafuer den ganzen Satz neu baut, verliert die Vorgaben, die sie
     * nicht nennt.
     */
    public Shopwerte mitZeilen(int andere) {
        return new Shopwerte(spielershopKauf, spielershopVerkauf, bundshopVerkauf,
                bundWort, andere);
    }

    /**
     * Welcher Satz fuer diese Lage gilt.
     *
     * <p>Die EINE Stelle, an der zwischen den Saetzen gewaehlt wird. Zwei Stellen waeren
     * zwei Gelegenheiten, die Saetze zu vertauschen - und das faellt niemandem auf,
     * solange alle 0 sind.
     *
     * <p><b>Der Kauf am Bundshop gibt 0</b>, und das ist keine vierte Einstellung,
     * sondern eine Tatsache: Die Gebuehr geht dem ab, der das Geld bekommt, und das ist
     * dort der Bund. Siehe den Klassenkopf.
     */
    public int prozentFuer(Kistenshop shop, Handelsart art) {
        if (shop == null || art == null) {
            throw new IllegalArgumentException("Ohne Shop und Handelsart gibt es keine"
                    + " Gebuehr");
        }
        if (shop.demBund()) {
            return art == Handelsart.KUNDE_KAUFT ? 0 : bundshopVerkauf;
        }
        return art == Handelsart.KUNDE_KAUFT ? spielershopKauf : spielershopVerkauf;
    }

    /** Ob ueberhaupt eine Gebuehr erhoben wird - fuer die Startzeile. */
    public boolean erhebtGebuehr() {
        return spielershopKauf > 0 || spielershopVerkauf > 0 || bundshopVerkauf > 0;
    }
}
