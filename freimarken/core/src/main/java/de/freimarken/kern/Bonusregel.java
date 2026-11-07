package de.freimarken.kern;

/**
 * Was ein Serientag an Mark bringt - die EINE Rechnung des Tagesbonus.
 *
 * <p>Getrennt von {@link Serienregel}, obwohl beide zum Tagesbonus gehoeren: Die eine
 * zaehlt TAGE und liest ein Datum, die andere rechnet MARK und liest Betraege. Zusammen in
 * einer Klasse waere die Zahl der Tage und die Zahl der Mark dieselbe Entscheidung, und
 * eine Aenderung an der Belohnung faellt dann in die Stelle, die ueber die Serie urteilt.
 */
public final class Bonusregel {

    private Bonusregel() {
    }

    /**
     * Der Betrag fuer diesen Serientag, in ganzen Mark.
     *
     * <p>{@code grundbetrag + (serie - 1) * jeSerientag}, geklemmt auf den Hoechstbetrag.
     * Der erste Tag bringt also den Grundbetrag und nicht den Grundbetrag plus einen
     * Zuschlag - sonst waere der Grundbetrag eine Zahl, die nie allein auftritt.
     *
     * <p><b>Gerechnet wird mit der GEKLEMMTEN Serie</b>, nicht mit der rohen. Eine Serie
     * von dreitausend Tagen mal hundert Mark laeuft als {@code long} nicht ueber, aber in
     * einem Jahr steht dort eine Zahl, die der Betreiber nie eingestellt hat. Darum wird
     * die Rechnung an der Stelle abgeschnitten, an der sie den Hoechstbetrag erreicht -
     * und die Multiplikation kann danach nicht mehr wachsen.
     *
     * @param serie der laufende Serientag, mindestens 1
     * @throws IllegalArgumentException bei einer Serie unter 1 - das ist ein Fehler im
     *         Programm und keine Eingabe. {@link Serienregel} gibt nie eine
     */
    public static long betrag(int serie, Bonuswerte werte) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Werte gibt es keinen Betrag");
        }
        if (serie < 1) {
            throw new IllegalArgumentException("Ein Serientag ist mindestens 1, nicht "
                    + serie);
        }
        if (werte.jeSerientag() == 0L) {
            // Ohne Zuschlag bringt jeder Tag dasselbe. Eigener Zweig, damit die Division
            // darunter nicht durch 0 teilt.
            return werte.grundbetrag();
        }
        // Wie viele Serientage ueberhaupt noch etwas aendern: Ab hier ist der
        // Hoechstbetrag erreicht, und weiter zu rechnen gibt dieselbe Zahl - nur mit der
        // Moeglichkeit, unterwegs ueberzulaufen.
        long spanne = werte.hoechstbetrag() - werte.grundbetrag();
        long wirksameTage = spanne / werte.jeSerientag();
        long zuschlagstage = Math.min(serie - 1L, wirksameTage + 1L);
        long betrag = werte.grundbetrag() + zuschlagstage * werte.jeSerientag();
        return Math.min(betrag, werte.hoechstbetrag());
    }

    /**
     * Ab welchem Serientag der Hoechstbetrag erreicht ist - fuer die Startmeldung.
     *
     * <p>Eine eigene Frage und keine Rechnung am Sendeort: Der Betreiber soll beim Start
     * lesen, wie lange eine Serie ueberhaupt waechst. Eine Zahl, die er selbst aus drei
     * anderen ausrechnen muesste, liest niemand nach.
     *
     * <p>Ohne Zuschlag je Serientag ist die Antwort 1: Der erste Tag bringt schon alles,
     * was es gibt.
     */
    public static int tagDesHoechstbetrags(Bonuswerte werte) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Werte gibt es keine Antwort");
        }
        if (werte.jeSerientag() == 0L) {
            return 1;
        }
        long spanne = werte.hoechstbetrag() - werte.grundbetrag();
        // Aufgerundet: Der Tag, an dem der Hoechstbetrag ZUERST gilt. Bei einer Spanne,
        // die nicht aufgeht, ist das der Tag, an dem geklemmt wird.
        long tage = (spanne + werte.jeSerientag() - 1L) / werte.jeSerientag();
        long tag = tage + 1L;
        return (int) Math.min(tag, Integer.MAX_VALUE);
    }
}
