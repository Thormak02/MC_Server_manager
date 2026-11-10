package de.freimarken.kern;

import java.util.Locale;

/**
 * Die vier Zeilen eines Shopschildes - gelesen, nicht geraten.
 *
 * <p>Das Format kommt von Lumia und damit von ChestShop (Plan 7.4, belegt aus
 * {@code w09_chestshops.html} vom 11.01.2016):
 *
 * <pre>
 *   Zeile 1   nichts oder der eigene Name, oder das Wort fuer einen Bundshop
 *   Zeile 2   die Anzahl je Handel
 *   Zeile 3   B[Preis]:S[Preis]
 *   Zeile 4   der Materialname - oder ?, um ihn aus der Kiste zu lesen
 * </pre>
 *
 * <p><b>Eine Preisform und nicht fuenf.</b> ChestShop erlaubt {@code B 10}, {@code 10 B},
 * {@code B 10 : S 5}, {@code 10 B : 5 S}, dazu {@code free}, die Suffixe {@code K} und
 * {@code M} und Zahlen mit Punkt und {@code e}. Das ist die Ursache seiner eigenen
 * Doku-Schwaeche - und das {@code e} war ein belegter Dupe (#30, 2014: "This prevents
 * duplications by using the 'E' in a price of a sign"). Hier gilt: Buchstabe, dann Zahl,
 * Leerzeichen beliebig, Gross- und Kleinschreibung gleich. {@code B100:S80} und
 * {@code b 100 : s 80} sind dasselbe; {@code 100B} ist es nicht, und die Absage nennt die
 * richtige Form.
 *
 * <p><b>Ganze Mark.</b> Kein Punkt, kein Komma, kein {@code e}, kein Suffix. Ein Preis
 * ist eine Folge von Ziffern - alles andere wird abgewiesen, bevor daraus eine Zahl wird.
 * QuickShop #1902 ist der belegte Gegenfall: {@code 1E99} legte das Schild, aber keinen
 * Shop dahinter, und sehr hohe Preise fielen stumm auf 10 zurueck.
 *
 * <p><b>Was hier NICHT geschieht: die Ware aufloesen.</b> Ob {@code STONE} ein Material
 * ist und was in der Kiste liegt, weiss nur das Spiel. Diese Klasse sagt, WELCHE Frage zu
 * stellen ist - ein Name oder ein Blick in die Kiste -, und die Schicht am Spiel stellt
 * sie. Damit bleibt das ganze Format ohne Server pruefbar.
 */
public final class Shopschild {

    /** Das Zeichen, das "lies die Ware aus der Kiste" bedeutet - wie bei Lumia. */
    public static final String AUS_DER_KISTE = "?";

    /** Wie viele Zeilen ein Schild hat. */
    public static final int ZEILEN = 4;

    /**
     * Der Buchstabe vor dem Preis, den ein Kunde ZAHLT.
     *
     * <p>{@code B} wie "buy" und {@code S} wie "sell", und zwar aus der Sicht des KUNDEN:
     * Was er kauft, steht hinter B. Das ist ChestShops Form und damit Lumias - belegt in
     * {@code w09_chestshops.html} vom 11.01.2016 - und sie wird nicht uebersetzt. Ein
     * deutsches {@code K}/{@code V} waere lesbarer und broche jedes Schild, das ein
     * Spieler aus einem Wiki abschreibt, das es seit zehn Jahren gibt.
     *
     * <p><b>Als Konstante und nicht als Buchstabe im Quelltext</b>, und das ist ein Befund
     * vom 08.11.2026: Dieselben zwei Zeichen standen VIERMAL da - zweimal in
     * {@code liesPreise}, zweimal in {@link #preistext}. Mit der Vorfrage
     * {@link #istGemeint} waere es ein fuenftes und sechstes Mal geworden, und dann
     * entscheidet eine Stelle, ob ein Schild gemeint ist, waehrend eine andere es liest.
     */
    public static final char KAUF = 'B';

    /** Der Buchstabe vor dem Preis, den ein Kunde BEKOMMT - siehe {@link #KAUF}. */
    public static final char VERKAUF = 'S';

    private Shopschild() {
    }

    /** Was in der vierten Zeile stand. */
    public enum Warenangabe {
        /** Ein Materialname wurde getippt - {@link Gelesen#material} traegt ihn. */
        NAME,
        /** Ein {@code ?} - die Ware kommt aus der Kiste. */
        AUS_KISTE
    }

    /**
     * Ein gelesenes Schild.
     *
     * @param besitzername was in Zeile 1 stand, getrimmt. LEER heisst: der Ersteller.
     *                     Wer daraus einen Spieler macht, ist die Schicht am Spiel - sie
     *                     allein weiss, wer gerade schreibt
     * @param bundshop     ob Zeile 1 das Wort fuer einen Bundshop trug
     * @param menge        die Anzahl je Handel
     * @param kaufpreis    was ein Kunde zahlt; 0 heisst: wird nicht verkauft
     * @param verkaufpreis was der Shop zahlt; 0 heisst: wird nicht angekauft
     * @param warenangabe  welche Frage die vierte Zeile stellt
     * @param material     der getippte Materialname bei {@link Warenangabe#NAME}, sonst
     *                     leer
     */
    public record Gelesen(String besitzername, boolean bundshop, int menge,
                          long kaufpreis, long verkaufpreis, Warenangabe warenangabe,
                          String material) {
    }

    /**
     * Was beim Lesen schiefgehen kann - je Fall eine eigene Antwort.
     *
     * <p>Eine Aufzaehlung und keine Textmeldung: Der Wortlaut gehoert in die Sprachdatei,
     * und ein Spieler, der ein Schild falsch beschriftet, soll lesen, WELCHE Zeile
     * gemeint ist. Eine Sammelabsage "das Schild ist falsch" liesse ihn alle vier
     * durchprobieren.
     */
    public enum Fehler {
        /** Das Schild hat nicht vier Zeilen - kommt nur aus einem Programmierfehler. */
        KEIN_SCHILD,
        /** Zeile 2 ist keine Anzahl. */
        MENGE_KEINE_ZAHL,
        /** Zeile 2 liegt ausserhalb von 1 bis zur vollen Doppelkiste. */
        MENGE_AUSSERHALB,
        /** Zeile 3 hat nicht die Form {@code B100:S80}. */
        PREIS_FORM,
        /** Eine Preiszahl ist keine Ziffernfolge - Punkt, Komma, e oder ein Suffix. */
        PREIS_KEINE_ZAHL,
        /** Eine Preiszahl liegt ueber der Obergrenze. */
        PREIS_AUSSERHALB,
        /** Beide Preise sind 0 - das Schild handelt nichts. */
        PREIS_BEIDE_NULL,
        /** Der Verkaufspreis liegt ueber dem Kaufpreis - die Gelddruckmaschine. */
        PREIS_VERDREHT,
        /** Zeile 4 ist leer. */
        WARE_FEHLT
    }

    /**
     * Das Ergebnis des Lesens - entweder ein Schild oder ein Fehler, nie beides.
     *
     * @param schild bei Erfolg das gelesene Schild, sonst {@code null}
     * @param fehler bei Misserfolg der Grund, sonst {@code null}
     * @param zeile  bei Misserfolg die Zeilennummer 1 bis 4, sonst 0. Der Spieler soll
     *               wissen, WO er nachsehen muss
     */
    public record Ergebnis(Gelesen schild, Fehler fehler, int zeile) {

        public boolean gelungen() {
            return schild != null;
        }
    }

    /**
     * Ob diese vier Zeilen ueberhaupt ein Shopschild sein SOLLEN.
     *
     * <p><b>Die Frage, ohne die jedes Hausnummernschild eine Absage bekaeme.</b>
     * {@link #lies} antwortet auf ein gewoehnliches Schild mit einem Fehler - es hat ja
     * keine Menge in der zweiten Zeile -, und ein Spieler, der ein Wegweiserschild
     * aufstellt, laese "die Menge ist keine Zahl". Darum wird VOR dem Lesen gefragt, ob
     * er ueberhaupt einen Shop gemeint hat.
     *
     * <p><b>Die PREISZEILE entscheidet und nicht die Kiste daneben</b>, und das ist die
     * eigentliche Entscheidung dieser Methode. Ein Schild an einer Kiste ist das
     * Normalste der Welt - "Werkzeug", "Vorrat Nord" -, und wer danach ginge, machte aus
     * jeder beschrifteten Vorratskiste einen Shopversuch. Ein {@code B} oder {@code S}
     * mit etwas dahinter in der dritten Zeile schreibt dagegen niemand versehentlich.
     * Denselben Weg geht ChestShop.
     *
     * <p><b>Nur der BUCHSTABE, nicht die Zahl dahinter.</b> Das ist der Sinn der
     * Trennung: Wer {@code B abc} tippt, hat einen Shop gemeint und sich vertippt - er
     * soll die Absage mit der Hilfe lesen und nicht ein stumm stehengelassenes Schild
     * bekommen. Waere die Zahl Teil dieser Frage, verschwaende genau der Fall, fuer den
     * es die acht Fehlermeldungen gibt.
     *
     * <p>Alles andere - Menge, Ware, Bundwort - wird hier NICHT angesehen. Ein Schild mit
     * Preiszeile und leerer Ware ist ein gemeinter Shop mit einem Fehler, und das ist
     * genau der Unterschied, den diese Methode treffen muss.
     */
    public static boolean istGemeint(String[] zeilen) {
        if (zeilen == null || zeilen.length < ZEILEN) {
            return false;
        }
        String preiszeile = sauber(zeilen[2]);
        if (preiszeile.length() < 2) {
            // Ein einzelner Buchstabe ist kein Preis und auch keine Absicht: "B" allein
            // steht auf genug Schildern. Zwei Zeichen heisst: Buchstabe UND etwas
            // dahinter, und was das ist, entscheidet lies.
            return false;
        }
        char art = Character.toUpperCase(preiszeile.charAt(0));
        return art == KAUF || art == VERKAUF;
    }

    /**
     * Liest die vier Zeilen.
     *
     * <p><b>Wirft nicht.</b> Ein falsch beschriftetes Schild ist der Normalfall beim
     * ersten Versuch, nicht ein Fehler des Programms - und ein Wurf mitten im
     * Schildschreiben liesse den Spieler mit einem halben Schild stehen.
     *
     * @param zeilen     die vier Zeilen, wie sie auf dem Schild stehen. {@code null}
     *                   Eintraege gelten als leer
     * @param bundWort   das Wort, das einen Bundshop anzeigt - aus der
     *                   {@code config.yml}, damit ein Betreiber es aendern kann. Leer
     *                   oder {@code null} heisst: Bundshops werden ueber das Schild nicht
     *                   angelegt
     */
    public static Ergebnis lies(String[] zeilen, String bundWort) {
        if (zeilen == null || zeilen.length < ZEILEN) {
            return new Ergebnis(null, Fehler.KEIN_SCHILD, 0);
        }
        String erste = sauber(zeilen[0]);
        // istBundwort faengt ein fehlendes Wort selbst - der Aufrufer darf null
        // uebergeben, und "es gibt kein Bundwort" ist eine zulaessige Einstellung.
        boolean bund = istBundwort(erste, bundWort);

        // --- Zeile 2: die Menge
        String mengentext = sauber(zeilen[1]);
        if (!nurZiffern(mengentext)) {
            return new Ergebnis(null, Fehler.MENGE_KEINE_ZAHL, 2);
        }
        long menge = alsZahl(mengentext);
        if (menge < 1L || menge > Kistenshop.GROESSTE_MENGE) {
            return new Ergebnis(null, Fehler.MENGE_AUSSERHALB, 2);
        }

        // --- Zeile 3: die Preise
        Preise preise = liesPreise(sauber(zeilen[2]));
        if (preise.fehler() != null) {
            return new Ergebnis(null, preise.fehler(), 3);
        }
        if (preise.kauf() == 0L && preise.verkauf() == 0L) {
            return new Ergebnis(null, Fehler.PREIS_BEIDE_NULL, 3);
        }
        // Dieselbe Schranke wie im Satz Kistenshop, und hier noch einmal: Ein Spieler
        // soll sie beim Schreiben lesen und nicht erst, wenn das Schild schon haengt.
        if (preise.kauf() > 0L && preise.verkauf() > preise.kauf()) {
            return new Ergebnis(null, Fehler.PREIS_VERDREHT, 3);
        }

        // --- Zeile 4: die Ware
        String warentext = sauber(zeilen[3]);
        if (warentext.isEmpty()) {
            return new Ergebnis(null, Fehler.WARE_FEHLT, 4);
        }
        boolean ausKiste = AUS_DER_KISTE.equals(warentext);
        return new Ergebnis(new Gelesen(erste, bund, (int) menge, preise.kauf(),
                preise.verkauf(),
                ausKiste ? Warenangabe.AUS_KISTE : Warenangabe.NAME,
                ausKiste ? "" : warentext), null, 0);
    }

    /**
     * Schreibt die vier Zeilen fuer einen fertigen Shop - die Gegenrichtung.
     *
     * <p>Gebraucht, nachdem ein Shop angelegt ist: Dann steht in Zeile 1 der wirkliche
     * Besitzer und in Zeile 4 der aufgeloeste Warenname, auch wenn der Spieler ein
     * {@code ?} getippt hat. <b>Das ist die Rueckmeldung des Programms auf dem Schild</b>,
     * und sie ist kein Schmuck: Ein Kunde liest dort, was er bekommt, und der Besitzer
     * sieht, dass sein Shop steht.
     *
     * <p><b>WELCHER der zwei Namen in Zeile 1 kommt, entscheidet diese Methode</b> - und
     * zwar seit dem 10.11.2026, als ein Fund zeigte, dass es niemand entschied: Die
     * einzige Aufrufstelle gab IMMER den Spielernamen, auch bei einem Bundshop. Ein
     * frisch aufgestellter Bundshop trug also in der ersten Zeile den Namen dessen, der
     * ihn hingestellt hat - er sah aus wie der Laden eines Spielers, waehrend er dem Bund
     * gehoert. Gehandelt wurde richtig (die Zeile in der Datenbank ist die Wahrheit, nicht
     * das Holz), aber jeder Kunde las etwas Falsches.
     *
     * <p>Die Entscheidung steht HIER und nicht in der Wache, und das hat zwei Gruende.
     * Erstens ist sie ohne Server pruefbar. Zweitens fragt die Wache damit nirgends
     * selbst nach {@link Kistenshop#demBund()} - eine Quelltexttafel verlangt genau das,
     * denn ein Bundshop hat keinen Besitzer, und wer ihn am Besitz prueft, schliesst ihn
     * fuer alle zu.
     *
     * @param spielername wer den Shop angelegt hat - fuer jeden Shop ausser einem Bundshop
     * @param bundWort    das Wort aus der {@code config.yml}, das einen Bundshop anzeigt.
     *                    Pflicht nur bei einem Bundshop; bei jedem anderen darf es leer
     *                    sein, denn ein Server ohne Bundshops braucht kein Wort dafuer
     */
    public static String[] schreibe(String spielername, String bundWort,
            Kistenshop shop) {
        if (shop == null) {
            throw new IllegalArgumentException("Ohne Shop gibt es keine Schildzeilen");
        }
        String erste = shop.demBund() ? bundWort : spielername;
        if (erste == null || erste.isBlank()) {
            throw new IllegalArgumentException("Ohne Namen in der ersten Zeile ist das"
                    + " Schild eines fertigen Shops unvollstaendig. Gebraucht wird hier "
                    + (shop.demBund() ? "das Bundwort" : "der Spielername") + ".");
        }
        return new String[] {
            erste.trim(),
            String.valueOf(shop.menge()),
            preistext(shop.kaufpreis(), shop.verkaufpreis()),
            shop.ware().name(),
        };
    }

    /**
     * Die Preiszeile eines Shops - in der EINEN Form, die {@link #lies} versteht.
     *
     * <p>Paketprivat waere zu eng: Eine Probe muss zeigen koennen, dass geschrieben und
     * gelesen zusammenpassen, und die Absage an einen Spieler nennt diese Form als
     * Beispiel.
     */
    public static String preistext(long kaufpreis, long verkaufpreis) {
        if (kaufpreis > 0L && verkaufpreis > 0L) {
            return KAUF + " " + kaufpreis + " : " + VERKAUF + " " + verkaufpreis;
        }
        if (kaufpreis > 0L) {
            return KAUF + " " + kaufpreis;
        }
        return VERKAUF + " " + verkaufpreis;
    }

    /** Die zwei Preise und der Fehler - nur fuer den Weg durch {@link #liesPreise}. */
    private record Preise(long kauf, long verkauf, Fehler fehler) {
    }

    /**
     * Zerlegt die Preiszeile an ihrem Doppelpunkt.
     *
     * <p>Kein Regex, sondern ein Zerlegen und zwei gleiche Pruefungen. Der Grund ist
     * ChestShops Preis-Regex: Sie traegt fuenf Alternativen und erlaubt dabei
     * stillschweigend {@code [.e]} in der Zahl - ein Muster, das mehr zulaesst, als sein
     * Autor wollte, und das niemand mehr liest. Zwei kurze Pruefungen sagen, was sie
     * pruefen.
     */
    private static Preise liesPreise(String zeile) {
        if (zeile.isEmpty()) {
            return new Preise(0L, 0L, Fehler.PREIS_FORM);
        }
        String[] teile = zeile.split(":", -1);
        if (teile.length > 2) {
            return new Preise(0L, 0L, Fehler.PREIS_FORM);
        }
        long kauf = 0L;
        long verkauf = 0L;
        for (String teil : teile) {
            String stueck = sauber(teil);
            if (stueck.isEmpty()) {
                return new Preise(0L, 0L, Fehler.PREIS_FORM);
            }
            char art = Character.toUpperCase(stueck.charAt(0));
            if (art != KAUF && art != VERKAUF) {
                return new Preise(0L, 0L, Fehler.PREIS_FORM);
            }
            String zahl = sauber(stueck.substring(1));
            if (!nurZiffern(zahl)) {
                // Punkt, Komma, e, ein Suffix oder gar nichts - alles dasselbe Problem:
                // Es ist keine ganze Mark.
                return new Preise(0L, 0L, Fehler.PREIS_KEINE_ZAHL);
            }
            long wert = alsZahl(zahl);
            if (wert < 0L || wert > Kistenshop.HOECHSTER_PREIS) {
                return new Preise(0L, 0L, Fehler.PREIS_AUSSERHALB);
            }
            // Zweimal dieselbe Art - "B 10 : B 20" - ist keine Form, die etwas bedeutet.
            if (art == KAUF) {
                if (kauf > 0L) {
                    return new Preise(0L, 0L, Fehler.PREIS_FORM);
                }
                kauf = wert;
            } else {
                if (verkauf > 0L) {
                    return new Preise(0L, 0L, Fehler.PREIS_FORM);
                }
                verkauf = wert;
            }
        }
        return new Preise(kauf, verkauf, null);
    }

    /**
     * Nur Ziffern, mindestens eine - und nicht mehr als neunzehn.
     *
     * <p>Die Laengengrenze faengt den Ueberlauf, BEVOR daraus eine Zahl wird: Eine
     * zwanzigstellige Ziffernfolge passt in keinen {@code long}, und
     * {@code Long.parseLong} wuerfe dafuer eine Ausnahme mitten im Lesen. Die
     * Preisobergrenze liegt ohnehin weit darunter; diese Grenze ist nur dafuer da, dass
     * die Pruefung VOR der Umwandlung steht.
     */
    private static boolean nurZiffern(String text) {
        if (text.isEmpty() || text.length() > 19) {
            return false;
        }
        for (int stelle = 0; stelle < text.length(); stelle++) {
            if (text.charAt(stelle) < '0' || text.charAt(stelle) > '9') {
                return false;
            }
        }
        return true;
    }

    /** Eine geprueefte Ziffernfolge als Zahl - wirft darum nicht. */
    private static long alsZahl(String ziffern) {
        return Long.parseLong(ziffern);
    }

    /**
     * Leerzeichen weg, aussen und innen gleich behandelt.
     *
     * <p>Minecraft schreibt auf ein Schild, was getippt wurde, samt fuehrender
     * Leerzeichen. Ein Spieler, der {@code " B 100"} tippt, meint {@code "B 100"}.
     */
    private static String sauber(String zeile) {
        return zeile == null ? "" : zeile.trim();
    }

    /**
     * Vergleicht zwei Worte ohne Ruecksicht auf Leerzeichen und Gross-/Kleinschreibung.
     *
     * <p>Dieselbe Form wie ChestShops {@code isAdminShop}: Das Bundwort kann aus zwei
     * Worten bestehen, und ein Spieler, der eines davon mit zwei Leerzeichen tippt, soll
     * dasselbe Schild bekommen.
     */
    private static boolean gleichOhneLeerzeichen(String eines, String anderes) {
        return eines.replace(" ", "").equalsIgnoreCase(anderes.replace(" ", ""));
    }

    /** Der Vergleich auch von aussen - der Leser der config.yml braucht ihn. */
    public static boolean istBundwort(String zeile, String bundWort) {
        if (bundWort == null || bundWort.isBlank() || zeile == null) {
            return false;
        }
        return gleichOhneLeerzeichen(sauber(zeile), bundWort);
    }

    /** Ein Materialname, wie das Spiel ihn kennt - gross und mit Unterstrichen. */
    public static String alsMaterialname(String getippt) {
        if (getippt == null) {
            return "";
        }
        return getippt.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
