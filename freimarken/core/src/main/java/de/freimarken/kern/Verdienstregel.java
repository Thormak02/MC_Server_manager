package de.freimarken.kern;

import java.util.Set;

/**
 * Was eine Taetigkeit einbringt - die Regel, ohne Spieler und ohne Uhr.
 *
 * <p>Dieselbe Bauart wie {@link Berufsregel} und {@link Spielzeitregel}: Alle Groessen
 * stehen in der Signatur. Eine Regel, die sich ihre Zahlen selbst holt, laesst sich nur
 * mit allem drumherum pruefen - und diese hier ist die wichtigste Zahl dieser Wirtschaft,
 * denn an ihr haengt die Kalibrierung auf 2.000 bis 5.000 Mark je Spielstunde.
 *
 * <p><b>Gerechnet wird in PFENNIGEN</b>, in ganzen Zahlen und ohne Gleitkomma. Geld in
 * {@code double} ist der Fehler, den man erst bemerkt, wenn die Summe aller Buchungen
 * nicht mehr dem Kontostand entspricht. Die Umrechnung in Mark macht der Sammler, und
 * zwar erst beim Buchen.
 *
 * <p><b>Die drei Fragen sind getrennt</b>, und das ist Absicht: {@link #anteilProzent}
 * weiss nichts von Loehnen, {@link #pfennige} nichts von Deckeln, und
 * {@link #unterDemDeckel} nichts von Bloecken. So laesst sich jede fuer sich pruefen -
 * und die Minderung, an der niemand eine Zahl ablesen kann, ist genau die, die eine Probe
 * Stufe fuer Stufe nachrechnen kann.
 */
public final class Verdienstregel {

    private Verdienstregel() {
    }

    /**
     * Welchen ANTEIL der n-te Block einer Art noch bringt, in Prozent.
     *
     * <p>Die ersten {@code voll-bis} zahlen voll. Danach senkt jede Stufe auf
     * {@code faktor-prozent} des vorherigen Satzes, und unter {@code boden-prozent} geht
     * es nie. Mit den Vorgaben 200/200/50/10 heisst das: 1-200 voll, 201-400 die Haelfte,
     * 401-600 ein Viertel, 601-800 ein Achtel, ab 801 der Boden von einem Zehntel.
     *
     * <p>Die Schleife endet spaetestens am Boden und nicht erst nach der Zahl der Stufen:
     * Bei 99 Prozent Faktor und einem Boden von 1 Prozent sind das rund 460 Durchgaenge,
     * und danach aendert sich nichts mehr. Ohne diese Bedingung liefe sie bei einer
     * Millionenzahl abgebauter Bloecke eine Million Mal - in einem Blockereignis.
     *
     * @param schonGezaehlt wie viele Bloecke dieser Art in der gleitenden Stunde schon
     *                      gezaehlt wurden, DIESEN nicht mitgerechnet
     */
    public static int anteilProzent(Verdienstwerte werte, int schonGezaehlt) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Werte gibt es keine Minderung");
        }
        if (schonGezaehlt < 0) {
            throw new IllegalArgumentException("Es koennen nicht " + schonGezaehlt
                    + " Bloecke gezaehlt worden sein");
        }
        if (schonGezaehlt < werte.vollBis()) {
            return 100;
        }
        int stufen = (schonGezaehlt - werte.vollBis()) / werte.stufe() + 1;
        int anteil = 100;
        for (int schritt = 0; schritt < stufen && anteil > werte.bodenProzent();
                schritt++) {
            anteil = anteil * werte.faktorProzent() / 100;
        }
        return Math.max(anteil, werte.bodenProzent());
    }

    /**
     * Was diese Taetigkeit an diesem Material JETZT einbringt, in Pfennigen.
     *
     * <p>Der hoechste Satz unter den gehaltenen Berufen, gemindert um den Anteil. Null
     * heisst: Diese Taetigkeit an diesem Material bringt diesem Spieler nichts - entweder
     * steht es nicht in der Lohnliste, oder er hat den passenden Beruf nicht.
     *
     * <p><b>Ein kleiner Lohn kann durch die Minderung auf 0 fallen</b>, und das ist
     * richtig gerechnet: Vier Pfennige mal ein Zehntel sind null. Wer das nicht will,
     * setzt den Lohn hoeher oder den Boden; die Rechnung erfindet keinen Pfennig.
     */
    public static long pfennige(Verdienstwerte werte, Set<String> berufe,
                                Taetigkeit taetigkeit, String material,
                                int schonGezaehlt) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Werte gibt es keinen Lohn");
        }
        // Die Tafel kommt AUS den Werten und nicht daneben herein: Sonst koennte ein
        // Aufrufer eine neue Tafel mit einer alten Minderung paaren, und ein Spieler
        // verdiente einen Augenblick lang etwas, das niemand eingestellt hat.
        long voll = werte.loehne().hoechsterLohn(berufe, taetigkeit, material);
        if (voll <= 0L) {
            return 0L;
        }
        return voll * anteilProzent(werte, schonGezaehlt) / 100L;
    }

    /**
     * Wie viel von einem Betrag der Tagesdeckel noch zulaesst - in MARK.
     *
     * <p>In Mark und nicht in Pfennigen, weil der Deckel am Gebuchten haengt: Gebucht
     * werden nur ganze Mark, und der Zeuge fuer die Tagessumme ist das Buchungsbuch. Ein
     * Deckel auf Pfennige braeuchte eine zweite Wahrheit daneben.
     *
     * @param heuteSchon was heute schon aus Arbeit gebucht ist
     * @param wunsch     was jetzt gebucht werden soll
     * @return was davon gebucht werden darf - 0, wenn der Deckel erreicht ist
     */
    public static long unterDemDeckel(Verdienstwerte werte, long heuteSchon,
                                      long wunsch) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Werte gibt es keinen Deckel");
        }
        // Gerechnet wird in Deckelregel und nicht hier: Seit Teil F gibt es ZWEI
        // gedeckelte Quellen, und zwei Abschriften derselben Rechnung liefen auseinander.
        return Deckelregel.erlaubt(werte.tagesdeckel(), heuteSchon, wunsch);
    }

    /** Wie viele ganze Mark in einer Pfennigsumme stecken. */
    public static long mark(long pfennige) {
        if (pfennige < 0L) {
            throw new IllegalArgumentException("Negative Pfennige: " + pfennige);
        }
        return pfennige / 100L;
    }

    /**
     * Was nach dem Buchen als Rest stehenbleibt.
     *
     * <p>Der Rest ist der ganze Grund fuer die Pfennige: Ohne ihn muesste gewoehnlicher
     * Stein 0 zahlen, und die Spanne von 2.000 bis 5.000 Mark je Stunde haenge an der
     * Erzdichte statt an der Arbeit.
     */
    public static long rest(long pfennige) {
        if (pfennige < 0L) {
            throw new IllegalArgumentException("Negative Pfennige: " + pfennige);
        }
        return pfennige % 100L;
    }
}
