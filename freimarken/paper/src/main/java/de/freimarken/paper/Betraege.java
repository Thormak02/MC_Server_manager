package de.freimarken.paper;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Geldbetraege in Worte - mit deutschen Tausenderpunkten.
 *
 * <p>Eigene Klasse, damit es nur eine Fassung davon gibt: Sonst steht in einem Befehl
 * "400.000 Mark" und in einem anderen "400000", und beides ist derselbe Betrag.
 */
final class Betraege {

    /**
     * Nicht nebenlaeufig benutzbar - {@link NumberFormat} ist das nie. Alles hier laeuft im
     * Serverhauptfaden, und wenn das einmal nicht mehr stimmt, gehoert hier ein neues
     * Exemplar je Aufruf her.
     */
    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.GERMANY);

    private Betraege() {
    }

    /** Zum Beispiel {@code "400.000 Mark"}. */
    static String mark(long betrag) {
        return FORMAT.format(betrag) + " Mark";
    }

    /** Nur die Zahl, ohne Waehrung. */
    static String zahl(long wert) {
        return FORMAT.format(wert);
    }
}
