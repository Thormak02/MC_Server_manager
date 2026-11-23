package de.freimarken.kern;

import java.util.Locale;

/**
 * Woraus ein Platzhalterbau gemauert ist - das WORT, nicht der Block.
 *
 * <p>Im Kern und ohne Bukkit: Welcher {@code Material}-Wert zu einem Stil gehoert, sagt
 * die Tabelle im Plugin. Hier steht nur, welche Stile es gibt - damit die Geometrie sie
 * kennt, ohne einen Block zu kennen.
 *
 * <p><b>Die Farben kommen aus Abschnitt 2.2 des Plans</b>, der ausdruecklich auch fuer
 * "offizielle Gebaeude" gilt: Bundesrot, Markgold, Forstgruen, Schiefer. Ein fuenfter
 * Stil ohne Farbe waere eine Stadt, die nach einem Monat auseinanderdriftet - genau das,
 * was jener Abschnitt verhindern will.
 */
public enum Baustil {

    /** Bundesrot: Backstein mit dunklen Balken - der Blick des alten Lumia. */
    BACKSTEIN("backstein"),

    /** Helles Gefach zwischen dunklen Balken - das Fachwerk der Handwerkergasse. */
    FACHWERK("fachwerk"),

    /** Markgold: warmer Sandstein mit Goldakzent, fuer das Muenzhaus. */
    GOLD("gold"),

    /** Forstgruen: Schlammziegel und Gruenspan, fuer die Tore in die Natur. */
    FORST("forst"),

    /** Schiefer: Tiefenschiefer und Tuff - der Grundton der Stadt. */
    SCHIEFER("schiefer");

    private final String wort;

    Baustil(String wort) {
        this.wort = wort;
    }

    /** Wie der Stil in der {@code config.yml} geschrieben wird - umlautfrei. */
    public String wort() {
        return wort;
    }

    /**
     * Der Stil zu diesem Wort - oder {@code null}, wenn es keinen gibt.
     *
     * <p>{@code null} und kein Wurf: Der Wert kommt aus der Datei, und ein Vertipper des
     * Betreibers ist eine Klage und keine Ausnahme mitten im Start.
     */
    public static Baustil ausWort(String wort) {
        if (wort == null) {
            return null;
        }
        String gesucht = wort.trim().toLowerCase(Locale.ROOT);
        for (Baustil stil : values()) {
            if (stil.wort.equals(gesucht)) {
                return stil;
            }
        }
        return null;
    }

    /** Alle Worte, aus der Aufzaehlung und nicht abgeschrieben - fuer die Klage. */
    public static String worte() {
        StringBuilder raus = new StringBuilder();
        for (Baustil stil : values()) {
            if (raus.length() > 0) {
                raus.append(", ");
            }
            raus.append(stil.wort);
        }
        return raus.toString();
    }
}
