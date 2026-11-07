package de.freimarken.kern;

/**
 * Wie viel ein Tagesdeckel noch zulaesst - die eine Rechnung fuer beide Deckel.
 *
 * <p><b>Warum sie eine eigene Klasse ist:</b> Seit Teil F gibt es ZWEI gedeckelte
 * Quellen - die Arbeit ({@link Verdienstwerte#tagesdeckel}) und den Verkauf an den
 * Bundesmarkt ({@link Marktwerte#tagesdeckel}). Beide rechnen dasselbe, und zwei
 * Abschriften derselben Rechnung laufen auseinander: Die eine bekommt eine Schranke
 * nachgezogen, die andere nicht, und dann deckelt der Server zwei Quellen nach zwei
 * verschiedenen Regeln. Der Plan verlangt "alle gedeckelt" (8.2) und nennt keine
 * Unterschiede.
 *
 * <p>Gerechnet wird in MARK und nicht in Pfennigen: Der Deckel haengt am GEBUCHTEN, und
 * gebucht werden nur ganze Mark. Ein Deckel auf Pfennige braeuchte eine zweite Wahrheit
 * neben dem Buchungsbuch.
 */
public final class Deckelregel {

    private Deckelregel() {
    }

    /**
     * Wie viel von einem Wunsch der Deckel noch zulaesst.
     *
     * @param deckel     die Tagesgrenze - mindestens 1
     * @param heuteSchon was heute schon aus DIESEM Grund gebucht ist
     * @param wunsch     was jetzt gebucht werden soll
     * @return was davon gebucht werden darf - 0, wenn der Deckel erreicht ist
     */
    public static long erlaubt(long deckel, long heuteSchon, long wunsch) {
        if (deckel < 1L) {
            throw new IllegalArgumentException("Ein Deckel von " + deckel + " waere keine"
                    + " Grenze, sondern eine Abschaltung - und die gehoert an die Liste,"
                    + " nicht an den Deckel");
        }
        if (heuteSchon < 0L || wunsch < 0L) {
            throw new IllegalArgumentException("Weder " + heuteSchon + " noch " + wunsch
                    + " kann negativ sein - ein negativer Betrag waere eine Abbuchung auf"
                    + " dem Weg einer Einnahme");
        }
        long frei = deckel - heuteSchon;
        if (frei <= 0L) {
            return 0L;
        }
        return Math.min(wunsch, frei);
    }
}
