package de.freimarken.kern;

import java.util.List;

/**
 * Wo die Freien Truhen stehen - von Hand eingemessenes Gelaende, Plan 7.2.
 *
 * <p>Derselbe Schnitt wie zwischen {@code Mietwerte} und {@link Ladenverzeichnis} im
 * Abschnitt {@code laeden:}: Die Zahlen der Truhe sind neuladbar und stehen in
 * {@link Truhenwerte}, die Stellen werden nur beim Start gelesen und stehen hier. Der
 * Grund ist nicht Ordnung, sondern ein Fehler, den der Schnitt unmoeglich macht: Wuerde
 * eine Stelle beim Neuladen mitgetauscht, rechnete ein offenes Truhenfenster seine
 * Entnahme gegen den Inhalt einer anderen Kiste.
 *
 * <p><b>Die Stellen haben mit Absicht keine Vorgabe.</b> Eine Truhe steht dort, wo jemand
 * sie hingestellt hat - eine geratene Koordinate zeigte in unerschlossenes Gelaende, und
 * dort stuende dann eine Kiste, aus der jeder nehmen darf. Dieselbe Entscheidung wie bei
 * den Ladeneinheiten (Plan: "von HAND vorgebaut") und bei den Torquadern: Ohne Eintrag
 * gibt es keine Freie Truhe.
 *
 * <p><b>Mehrere Truhen sind erlaubt.</b> Der Plan nennt eine; wer zwei aufstellt, soll
 * damit aber nicht zwei Tagesmengen verschenken - der Deckel zaehlt darum je SPIELER und
 * steht bei den {@link Truhenwerte}, nicht hier.
 *
 * <p>Die Y-Achse gehoert zum Schluessel, wie bei jedem {@link Blockort}: Eine Kiste eine
 * Ebene hoeher ist eine andere Kiste. Fuer eine DOPPELKISTE heisst das, dass beide
 * Haelften eingetragen sein muessen - die Spielschicht sieht den angeklickten Block, und
 * welche Haelfte das ist, entscheidet der Spieler mit seinem Klick.
 */
public final class Truhenverzeichnis {

    /** Der Abschnitt mit den Stellen - je Truhe drei ganze Zahlen. */
    public static final String PFAD_TRUHEN = "freie-truhe.truhen";

    private static final Truhenverzeichnis LEER =
            new Truhenverzeichnis(List.of());

    private final List<Blockort> truhen;

    private Truhenverzeichnis(List<Blockort> truhen) {
        this.truhen = truhen;
    }

    /**
     * Ein Verzeichnis aus diesen Stellen.
     *
     * <p>Doppelte Stellen werden NICHT abgewiesen, sondern zusammengelegt: Zweimal
     * dieselbe Zeile in der Datei ist ein Tippfehler des Betreibers, aber keiner, der
     * etwas kaputt macht - gefragt wird nur, OB an einer Stelle eine Truhe steht. Der
     * Leser beklagt die doppelte Zeile; das Verzeichnis arbeitet damit weiter.
     */
    public static Truhenverzeichnis ausStellen(List<Blockort> stellen) {
        if (stellen == null) {
            throw new IllegalArgumentException("Nichts ist keine Stellenliste - eine"
                    + " LEERE Liste heisst: es gibt keine Freie Truhe");
        }
        for (Blockort stelle : stellen) {
            if (stelle == null) {
                throw new IllegalArgumentException("Eine Stelle in der Liste ist null");
            }
        }
        return new Truhenverzeichnis(stellen.stream().distinct().toList());
    }

    /** Das Verzeichnis ohne jede Truhe - es gibt dann keine Freie Truhe. */
    public static Truhenverzeichnis ohneTruhen() {
        return LEER;
    }

    /** Ob es ueberhaupt eine Freie Truhe gibt. */
    public boolean gibtTruhen() {
        return !truhen.isEmpty();
    }

    public int anzahl() {
        return truhen.size();
    }

    /** Alle Stellen - unveraenderlich, in der Reihenfolge der Datei. */
    public List<Blockort> alle() {
        return truhen;
    }

    /**
     * Ob an dieser Stelle eine Freie Truhe steht - die EINE Frage dieses Verzeichnisses.
     *
     * <p>{@code null} ist keine Truhe und kein Fehler: Die Spielschicht fragt mit dem
     * angeklickten Block, und der kann in einer Welt liegen, die es nicht mehr gibt.
     */
    public boolean istFreieTruhe(Blockort ort) {
        return ort != null && truhen.contains(ort);
    }

    @Override
    public boolean equals(Object andere) {
        return andere instanceof Truhenverzeichnis anderes
                && truhen.equals(anderes.truhen);
    }

    @Override
    public int hashCode() {
        return truhen.hashCode();
    }

    @Override
    public String toString() {
        return "Truhenverzeichnis" + truhen;
    }
}
