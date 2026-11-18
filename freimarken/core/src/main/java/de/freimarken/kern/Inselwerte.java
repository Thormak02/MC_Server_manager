package de.freimarken.kern;

/**
 * Was eine Meerinsel kostet und wie viele einer halten darf.
 *
 * <p>Das Geschwister von {@link Freistadtwerte}, nur viel kleiner: Eine Insel hat keinen
 * Namen, keine Kasse, keine Buerger und keine Auflage. Sie hat einen Preis und eine
 * Obergrenze, und beides steht in der {@code config.yml}.
 *
 * <p><b>Ein FESTPREIS und keine Staffel</b>, wie bei der Freistadt: Die Preisstaffel der
 * Marktstadt verdoppelt mit jeder Parzelle, weil dort das Flaechenhorten gebremst werden
 * soll. Hier bremst die Obergrenze, und 75.000 Mark sind der Preis fuer DIE Insel - nicht
 * fuer die erste von vielen.
 *
 * @param preis       was eine Insel kostet, in Mark
 * @param hoechstzahl wie viele Inseln ein Spieler ohne besonderes Recht haelt
 */
public record Inselwerte(long preis, int hoechstzahl) {

    /** Der Schluessel des Preises in der Einstellungsdatei. */
    public static final String PFAD_PREIS = "inseln.preis";

    /** Der Schluessel der Obergrenze in der Einstellungsdatei. */
    public static final String PFAD_HOECHSTZAHL = "inseln.hoechstzahl";

    /**
     * Was eine Insel ab Werk kostet - Plan 6.2: "Meerinsel | Hafenwerder | 75.000".
     *
     * <p>Die Zahl steht ABSICHTLICH hier und nicht an der Preisstaffel: Plan 6.2 nennt
     * sie neben dem Gruendungspreis einer Freistadt (400.000) und neben der
     * Hochwald-Sicherung (5.000). Drei Festpreise fuer drei verschiedene Dinge, und keiner
     * von ihnen folgt aus einem anderen - wer die Staffel aendert, soll nicht aus
     * Versehen den Inselpreis mitaendern.
     *
     * <p>Fuenfundsiebzigtausend sind rund drei Parzellen in der Staffel (25.000, 50.000,
     * 100.000 - die vierte kostet schon mehr) und ein Fuenftel einer Freistadt. Damit ist
     * die Insel das, was Plan 4.1 von ihr sagt: ein "ruhiger Rueckzug mit Status" und kein
     * Lebenswerk.
     */
    public static final long VORGABE_PREIS = 75_000L;

    /**
     * Wie viele Inseln ein Spieler ab Werk haelt - eine.
     *
     * <p>Wie bei der Freistadt, und aus einem Grund, der hier noch schaerfer ist:
     * Inselflaeche gibt es in einem Archipel nur einmal, und eine Insel ist unteilbar -
     * wer zwei haelt, haelt die zweite leer. Mehr gibt es ueber das RECHT
     * {@code freimarken.werder.anzahl.<n>}, also je Rang; dieselbe Bauart wie bei den
     * Parzellen, damit die Rangzahlen in der Datei bleiben und nicht im Programm.
     */
    public static final int VORGABE_HOECHSTZAHL = 1;

    /**
     * Die Obergrenze fuer den Preis - dieselbe wie bei der Freistadt.
     *
     * <p>Gegen den Vertipper und nicht gegen den Betreiber: Wer 75.000 meint und
     * 75.000.000 tippt, soll es beim Start lesen und nicht beim ersten Spieler, der sein
     * ganzes Vermoegen ausgibt.
     */
    public static final long HOECHSTER_BETRAG = Freistadtwerte.HOECHSTER_BETRAG;

    /**
     * Die Obergrenze fuer die Zahl - so viele Inseln hat die Welt.
     *
     * <p>Dreihundertdreiundzwanzig, und das ist {@code Inselgrenze.hafenwerder()
     * .inselnZahl()}: 18 Felder je Achse, die Ankunftsinsel abgezogen. Eine Zahl darueber
     * waere eine Obergrenze, die nie greift - und eine Einstellung, die nie wirkt, liest
     * der Betreiber als eine, die kaputt ist.
     *
     * <p>Als Zahl und nicht als Aufruf: {@code Inselgrenze} haengt an der Weltkante aus
     * der Datei, und die kann der Betreiber vergroessern. Eine Obergrenze, die mit der
     * Weltkante waechst, waere keine.
     */
    public static final int HOECHSTE_ZAHL = 323;

    /** Die Werte, die das Programm mitbringt. */
    public static final Inselwerte VORGABE =
            new Inselwerte(VORGABE_PREIS, VORGABE_HOECHSTZAHL);

    public Inselwerte {
        if (preis < 0L || preis > HOECHSTER_BETRAG) {
            throw new IllegalArgumentException("Der Inselpreis ist " + preis
                    + " und liegt nicht zwischen 0 und " + HOECHSTER_BETRAG
                    + ". Eine 0 ist erlaubt und heisst: Wer das Recht hat, nimmt eine"
                    + " Insel umsonst.");
        }
        if (hoechstzahl < 0 || hoechstzahl > HOECHSTE_ZAHL) {
            throw new IllegalArgumentException("Die Hoechstzahl der Inseln ist "
                    + hoechstzahl + " und liegt nicht zwischen 0 und " + HOECHSTE_ZAHL
                    + ". Eine 0 ist erlaubt und heisst: Es wird keine Insel vergeben.");
        }
    }

    /** Dieselben Werte mit einem anderen Preis. */
    public Inselwerte mitPreis(long anderer) {
        return new Inselwerte(anderer, hoechstzahl);
    }

    /** Dieselben Werte mit einer anderen Obergrenze. */
    public Inselwerte mitHoechstzahl(int andere) {
        return new Inselwerte(preis, andere);
    }
}
