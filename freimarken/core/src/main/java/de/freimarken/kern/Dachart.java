package de.freimarken.kern;

import java.util.Locale;

/**
 * Was fuer ein Dach ein Platzhalterbau traegt - das WORT, nicht der Block.
 *
 * <p>Zwei, und mehr soll es vorerst nicht geben: Ein Ziegeldach und ein gruenspaniges
 * Kupferdach. Jenes ist das Dach der norddeutschen Hanse - Rathaeuser, Tortuerme,
 * Kirchtuerme -, und es trifft zugleich Forstgruen aus Abschnitt 2.2.
 *
 * <p>Die ART aendert nur den BLOCK und nicht die Form: Die Dachflaeche entsteht aus der
 * Breite der Front, und das gilt fuer beide gleich. Zwei Formen waeren zwei Geometrien,
 * und die Geometrie ist das, was hier geprueft wird.
 */
public enum Dachart {

    /** Dunkle Dachtreppen - das gewoehnliche Haus. */
    ZIEGEL("ziegel"),

    /** Oxidiertes Kupfer - Rathaus, Tortuerme, alles Offizielle. */
    KUPFER("kupfer");

    private final String wort;

    Dachart(String wort) {
        this.wort = wort;
    }

    /** Wie die Dachart in der {@code config.yml} geschrieben wird. */
    public String wort() {
        return wort;
    }

    /** Die Dachart zu diesem Wort - oder {@code null}. Begruendung wie bei {@link Baustil}. */
    public static Dachart ausWort(String wort) {
        if (wort == null) {
            return null;
        }
        String gesucht = wort.trim().toLowerCase(Locale.ROOT);
        for (Dachart art : values()) {
            if (art.wort.equals(gesucht)) {
                return art;
            }
        }
        return null;
    }

    /** Alle Worte, aus der Aufzaehlung und nicht abgeschrieben - fuer die Klage. */
    public static String worte() {
        StringBuilder raus = new StringBuilder();
        for (Dachart art : values()) {
            if (raus.length() > 0) {
                raus.append(", ");
            }
            raus.append(art.wort);
        }
        return raus.toString();
    }
}
