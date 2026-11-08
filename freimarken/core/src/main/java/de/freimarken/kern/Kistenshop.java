package de.freimarken.kern;

import java.util.UUID;

/**
 * Ein Kistenshop - ein Schild an einer Kiste, das handelt.
 *
 * <p>Die Standardform des Verkaufs (Plan 7.4), dem alten Lumia nachgebaut: Rechtsklick
 * kauft, Linksklick verkauft, Sneak handelt einen ganzen Stapel. Kein Befehl, kein
 * Menue - man geht hin und klickt.
 *
 * <p><b>Das Schild ist die Bedienung, DIESE Zeile ist die Wahrheit.</b> Was auf dem Schild
 * steht, kann ein Spieler lesen; was gehandelt wird, steht hier. Der Grund ist die
 * {@link Warenprobe}: Verzauberte Ausruestung passt nicht auf ein Schild, und ChestShop
 * fuehrt dafuer vier offene Fehler.
 *
 * <p><b>Zwei Preise auf einem Shop</b>, wie bei Lumia ({@code B 100 : S 80}). QuickShop
 * verlangt fuer Kauf und Ankauf zwei getrennte Shops; das waere eine Doppelkiste je Ware
 * und in einer Markthalle mit wenigen Staenden nicht tragbar.
 *
 * <p><b>Der Verkaufspreis darf NIE ueber dem Kaufpreis liegen.</b> Das ist keine
 * Vorsicht, sondern eine Schranke gegen eine Gelddruckmaschine aus Versehen: Waere S
 * ueber B, kauft man im selben Shop billig und verkauft teuer zurueck. ChestShop fuehrt
 * dieselbe Sperre und begruendet sie in seiner eigenen Config - "This prevents newbies
 * from creating shops that would be exploited".
 *
 * @param schildWelt  die Welt des Schildes, als Schluesseltext. Shop und Kiste liegen
 *                    immer in DERSELBEN Welt - ein Schild, das in eine andere zeigt,
 *                    waere ein Shop ohne Kiste
 * @param schildX     wo das Schild haengt. Es ist der SCHLUESSEL des Shops: Ein Schild
 *                    traegt genau einen Shop, und eine Kiste kann mehrere Schilder haben
 *                    (eine Doppelkiste mit zwei Waren)
 * @param schildY     "
 * @param schildZ     "
 * @param kisteX      wo die Kiste steht. Eigene Angabe und nicht gerechnet: Das Schild
 *                    darf an, ueber, unter oder neben der Kiste haengen, und welche von
 *                    sechs Richtungen gilt, entscheidet die Schicht darueber einmal beim
 *                    Anlegen - nicht bei jedem Klick neu
 * @param kisteY      "
 * @param kisteZ      "
 * @param besitzer    wem der Shop gehoert, oder {@code null} fuer einen BUNDSHOP. Der
 *                    Bund hat unbegrenzten Bestand und unbegrenzten Platz; sein Geld
 *                    kommt aus dem Nichts und verschwindet dorthin. Dieselbe Loesung wie
 *                    ChestShops Adminshop, nur ueber ein fehlendes Feld statt ueber einen
 *                    Namen in der ersten Schildzeile - ein Spieler, der sich "Admin Shop"
 *                    nennt, kann damit nichts anfangen
 * @param menge       wie viele Stueck ein Handel umfasst
 * @param kaufpreis   was ein Kunde zahlt, um die {@code menge} zu KAUFEN. 0 heisst: Es
 *                    wird nicht verkauft
 * @param verkaufpreis was der Shop einem Kunden zahlt, der die {@code menge} ANBIETET.
 *                    0 heisst: Es wird nicht angekauft
 * @param ware        was gehandelt wird - EIN Exemplar, siehe {@link Warenprobe}
 */
public record Kistenshop(String schildWelt, int schildX, int schildY, int schildZ,
                         int kisteX, int kisteY, int kisteZ, UUID besitzer, int menge,
                         long kaufpreis, long verkaufpreis, Warenprobe ware) {

    /**
     * Die groesste Menge je Handel: eine volle Doppelkiste.
     *
     * <p>54 Plaetze mal 64 Stueck - dieselbe Rechnung wie ChestShops
     * {@code MAX_SHOP_AMOUNT: 3456}, dessen Config-Kommentar sie nennt ("Default 3456 is
     * a double chest of 64 stacks"). Mehr kann kein Handel bewegen, weil mehr nicht in
     * die Kiste passt.
     */
    public static final int GROESSTE_MENGE = 54 * 64;

    /**
     * Der hoechste Preis - dieselbe Obergrenze wie bei allen Geldzahlen des Projekts.
     *
     * <p>Die Zahl ist die Schranke gegen den Vertipper, nicht gegen den Reichen: Ein
     * Preis von einer Milliarde ist in einer Wirtschaft mit 5.000 Mark Tagesdeckel kein
     * Angebot, sondern ein verrutschtes Komma.
     */
    public static final long HOECHSTER_PREIS = Verkaufsregel.HOECHSTER_PREIS;

    public Kistenshop {
        if (schildWelt == null || schildWelt.isBlank()) {
            throw new IllegalArgumentException("Ein Kistenshop ohne Welt ist keiner -"
                    + " Schild und Kiste liegen immer in derselben");
        }
        schildWelt = schildWelt.trim();
        if (ware == null) {
            throw new IllegalArgumentException("Ein Kistenshop ohne Ware ist keiner");
        }
        if (menge < 1 || menge > GROESSTE_MENGE) {
            throw new IllegalArgumentException("Eine Handelsmenge von " + menge
                    + " ist keine - erlaubt ist 1 bis " + GROESSTE_MENGE
                    + " (eine volle Doppelkiste). Mehr kann kein Handel bewegen, weil"
                    + " mehr nicht in die Kiste passt.");
        }
        pruefePreis("Der Kaufpreis", kaufpreis);
        pruefePreis("Der Verkaufspreis", verkaufpreis);
        // Ein Shop, der weder kauft noch verkauft, ist ein Schild an einer Kiste. Er
        // faellt hier auf und nicht beim ersten Klick, bei dem ein Kunde nichts
        // geschieht und nichts erfaehrt.
        if (kaufpreis == 0L && verkaufpreis == 0L) {
            throw new IllegalArgumentException("Ein Kistenshop ohne Kauf- UND ohne"
                    + " Verkaufspreis handelt nichts. Mindestens eine der zwei Zahlen"
                    + " muss ueber 0 liegen.");
        }
        // Die Schranke gegen die Gelddruckmaschine aus Versehen - siehe den Klassenkopf.
        // NUR wenn beide Preise gesetzt sind: Ein Shop, der bloss ankauft (Kaufpreis 0),
        // ist kein Rundlauf, sondern ein Ankaufstand.
        if (kaufpreis > 0L && verkaufpreis > kaufpreis) {
            throw new IllegalArgumentException("Der Verkaufspreis " + verkaufpreis
                    + " liegt ueber dem Kaufpreis " + kaufpreis + ". Dann kauft man im"
                    + " selben Shop billig und verkauft teuer zurueck - das ist eine"
                    + " Gelddruckmaschine aus Versehen. Erlaubt ist hoechstens "
                    + kaufpreis + ".");
        }
        // Die Kiste MUSS neben dem Schild liegen. Geprueft wird der Abstand und nicht die
        // Richtung: Lumia erlaubte an, ueber, unter und neben - also alle sechs Seiten -
        // und ausdruecklich nicht diagonal.
        if (!istNachbar(schildX, schildY, schildZ, kisteX, kisteY, kisteZ)) {
            throw new IllegalArgumentException("Das Schild bei " + schildX + "/" + schildY
                    + "/" + schildZ + " liegt nicht neben der Kiste bei " + kisteX + "/"
                    + kisteY + "/" + kisteZ + ". Erlaubt ist genau ein Block Abstand auf"
                    + " einer Achse - an, ueber, unter oder neben der Kiste, nicht"
                    + " diagonal.");
        }
    }

    /** Ob dieser Shop dem Bund gehoert - unbegrenzt in Bestand, Platz und Geld. */
    public boolean demBund() {
        return besitzer == null;
    }

    /** Ob dieser Shop diesem Spieler gehoert. Ein Bundshop gehoert niemandem. */
    public boolean gehoert(UUID wer) {
        return besitzer != null && besitzer.equals(wer);
    }

    /** Ob hier gekauft werden kann - also ob der Shop verkauft. */
    public boolean verkauftWare() {
        return kaufpreis > 0L;
    }

    /** Ob hier verkauft werden kann - also ob der Shop ankauft. */
    public boolean kauftWare() {
        return verkaufpreis > 0L;
    }

    /**
     * Der Schluessel dieses Shops: die Schildstelle als Text.
     *
     * <p>Das SCHILD und nicht die Kiste, und das ist eine Entscheidung: An einer
     * Doppelkiste koennen zwei Schilder haengen, die zwei verschiedene Waren handeln -
     * eines auf jeder Haelfte. Mit der Kiste als Schluessel waere das unmoeglich, und
     * ein Laden mit einer Ware je Kiste braeuchte doppelt so viel Platz.
     */
    public String schluessel() {
        return schluesselVon(schildWelt, schildX, schildY, schildZ);
    }

    /** Derselbe Schluessel, ohne einen Shop in der Hand zu haben. */
    public static String schluesselVon(String welt, int x, int y, int z) {
        if (welt == null || welt.isBlank()) {
            throw new IllegalArgumentException("Ohne Welt gibt es keine Stelle");
        }
        return welt.trim() + " " + x + " " + y + " " + z;
    }

    /**
     * Ob zwei Stellen genau einen Block voneinander entfernt sind - nicht diagonal.
     *
     * <p>Die Summe der drei Abstaende ist genau 1. Das trifft die sechs Nachbarn und
     * schliesst die Diagonalen aus, ohne sie einzeln aufzuzaehlen - und es schliesst auch
     * aus, dass Schild und Kiste dieselbe Stelle sind.
     */
    public static boolean istNachbar(int ax, int ay, int az, int bx, int by, int bz) {
        return Math.abs(ax - bx) + Math.abs(ay - by) + Math.abs(az - bz) == 1;
    }

    private static void pruefePreis(String was, long preis) {
        if (preis < 0L || preis > HOECHSTER_PREIS) {
            throw new IllegalArgumentException(was + " ist " + preis + " und liegt nicht"
                    + " zwischen 0 und " + HOECHSTER_PREIS + ". Eine 0 heisst: in dieser"
                    + " Richtung wird nicht gehandelt.");
        }
    }
}
