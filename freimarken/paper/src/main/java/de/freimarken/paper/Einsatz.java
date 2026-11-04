package de.freimarken.paper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Die benannten Werte, die in einen Satz eingesetzt werden.
 *
 * <p>Benannt und nicht nummeriert, und das aus zwei gemessenen Gruenden: Mehrere Saetze
 * tragen denselben Wert zweimal ({@code von {von}/{von} bis {bis}/{bis}}), und eine
 * Umstellung durch den Betreiber wuerde nummerierte Werte verwechseln. Mehrfaches
 * Vorkommen ist erlaubt und Absicht.
 *
 * <p>Unveraenderlich, {@link #mit} gibt ein neues Exemplar. So kann ein Einsatz nicht
 * zwischen zwei Saetzen hin und her wandern und dabei einen Wert mitnehmen, den der zweite
 * gar nicht kennt.
 */
record Einsatz(Map<String, String> werte) {

    private static final Einsatz KEINE = new Einsatz(Map.of());

    Einsatz {
        werte = Map.copyOf(werte);
    }

    /** Fuer einen Satz ohne Platzhalter. */
    static Einsatz keine() {
        return KEINE;
    }

    static Einsatz von(String name, String wert) {
        return KEINE.mit(name, wert);
    }

    static Einsatz von(String name, long zahl) {
        return KEINE.mit(name, zahl);
    }

    Einsatz mit(String name, String wert) {
        if (name == null || wert == null) {
            throw new IllegalArgumentException("Ein Wert ohne Namen oder ein Name ohne"
                    + " Wert waere eine leere Stelle im Satz, und die faellt erst dem"
                    + " Spieler auf: " + name + " = " + wert);
        }
        Map<String, String> alle = new LinkedHashMap<>(werte);
        alle.put(name, wert);
        return new Einsatz(alle);
    }

    /**
     * Eine Zahl - ueber {@link Sprache#deutscheZahl}, damit es EINE Zahlform gibt.
     *
     * <p>Sonst steht in einem Befehl {@code 400.000} und in einem anderen {@code 400000},
     * und beides ist dieselbe Zahl. Genau dagegen gab es {@code Betraege} schon einmal.
     */
    Einsatz mit(String name, long zahl) {
        return mit(name, Sprache.deutscheZahl(zahl));
    }
}
