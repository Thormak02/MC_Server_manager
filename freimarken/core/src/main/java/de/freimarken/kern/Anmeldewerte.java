package de.freimarken.kern;

import java.util.Locale;

/**
 * Die Anmeldung im Buergeramt - ob es sie gibt und WOZU zugestimmt wird (Plan 10.1).
 *
 * <p><b>Die FASSUNG ist der tragende Wert dieses Blocks.</b> Plan 10.1 verlangt die
 * Annahme von "Regeln und Nutzungsbedingungen". Aendert der Betreiber die Regeln
 * ({@code regeln.text} in der Sprachdatei), ist eine Zustimmung von vorher keine
 * Zustimmung zu den neuen - sie waere eine stille Halbheit, die aussieht wie ein
 * Nachweis. Darum steht hier eine Bezeichnung, und wer sie aendert, verlangt von allen
 * eine neue Annahme. Was angenommen WURDE, liegt in {@link Anmeldeablage}; was heute
 * GILT, steht hier.
 *
 * <p><b>Die Fassung ist keine Zahl, die das Programm hochzaehlt.</b> Ein automatischer
 * Zaehler muesste merken, dass sich die Regeln geaendert haben - und das heisst, den Text
 * zu vergleichen. Dann waere jede Verbesserung eines Tippfehlers eine neue
 * Zustimmungspflicht fuer alle. Die Entscheidung "das ist eine neue Fassung" gehoert dem
 * Betreiber, und zwar ausdruecklich.
 *
 * <p><b>{@code an: false} ist eine ernstzunehmende Lage</b> und kein Schalter fuer
 * Faulheit: Ein Server, dessen Regeln noch nicht geschrieben sind, soll niemanden nach
 * einer Zustimmung zu einem leeren Text fragen. Aus heisst: Das Formular kommt nicht, der
 * Bundesmarkt ist offen, und die Ablage bleibt leer. Wer die Anmeldung spaeter
 * einschaltet, fragt dann alle - auch die, die schon lange da sind.
 *
 * @param an      ob die Anmeldung verlangt wird. Aus heisst: kein Formular, kein Riegel
 * @param fassung die Bezeichnung der heute geltenden Regeln, kleingeschrieben. Was darin
 *                steht, ist Sache des Betreibers - ein Datum, eine Nummer oder beides
 */
public record Anmeldewerte(boolean an, String fassung) {

    /** Der Schluessel in der Einstellungsdatei - ob die Anmeldung verlangt wird. */
    public static final String PFAD_AN = "anmeldung.an";

    /** Der Schluessel in der Einstellungsdatei - die geltende Fassung. */
    public static final String PFAD_FASSUNG = "anmeldung.fassung";

    /**
     * Ab Werk ist die Anmeldung AN.
     *
     * <p>Plan 10.1 nennt sie als Entwurfsregel des Einstiegs ("Erst danach oeffnet sich
     * der Bundesmarkt"), und die ausgelieferte Sprachdatei bringt fuenf Regeln mit. Ein
     * Server, der sie ab Werk nicht verlangte, haette den Schritt gebaut und nicht
     * angeschlossen.
     */
    public static final boolean VORGABE_AN = true;

    /**
     * Die Fassung der ausgelieferten Regeln - das Datum, an dem sie geschrieben wurden.
     *
     * <p>Wer die ausgelieferten Regeln uebernimmt, uebernimmt diese Fassung. Wer sie
     * aendert, setzt eine eigene - und erst damit wird die Zustimmung zu SEINEN Regeln
     * gezaehlt.
     */
    public static final String VORGABE_FASSUNG = "2026-11-21";

    public static final Anmeldewerte VORGABE = new Anmeldewerte(VORGABE_AN,
            VORGABE_FASSUNG);

    public Anmeldewerte {
        if (fassung == null || fassung.isBlank()) {
            throw new IllegalArgumentException("Ohne Fassung gibt es keine Anmeldung:"
                    + " Eine Zustimmung muss sagen, WOZU sie gilt (" + PFAD_FASSUNG
                    + ").");
        }
        // Kleingeschrieben HIER, an der einen Stelle, an der die geltende Fassung
        // entsteht. Der Antrag normalisiert ebenso, und Anmeldestand.hatAngenommen auch -
        // dreimal dasselbe, aber jede der drei Stellen bekommt ihren Wert von aussen und
        // darf sich nicht darauf verlassen, dass eine andere schon aufgeraeumt hat.
        fassung = fassung.trim().toLowerCase(Locale.ROOT);
        if (fassung.length() > Anmeldestand.HOECHSTE_FASSUNGSLAENGE) {
            throw new IllegalArgumentException("Die Fassung \"" + fassung + "\" ist"
                    + " laenger als " + Anmeldestand.HOECHSTE_FASSUNGSLAENGE
                    + " Zeichen - so lang ist die Spalte in der Ablage. Eine laengere"
                    + " liesse sich nicht ablegen, und die Annahme scheiterte als"
                    + " Datenbankfehler mitten im Spiel.");
        }
        if (!fassung.matches(Anmeldestand.MUSTER)) {
            throw new IllegalArgumentException("Die Fassung \"" + fassung + "\" taugt"
                    + " nicht: erlaubt sind Kleinbuchstaben, Ziffern, Punkt,"
                    + " Unterstrich und Bindestrich. Ein Leerzeichen oder ein Umlaut"
                    + " waere in der Datei nicht wiederzuerkennen.");
        }
    }
}
