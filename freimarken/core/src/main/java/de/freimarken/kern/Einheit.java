package de.freimarken.kern;

import java.util.Locale;

/**
 * Die Besitzeinheit, die einen Block regelt - Art UND Nummer.
 *
 * <p><b>Warum nicht einfach die Nummer</b>, und das ist gemessen und keine Vorsicht auf
 * Verdacht: Die Nummern kommen aus VERSCHIEDENEN Tabellen. {@link Parzelle#id()} zaehlt
 * im Besitzlager, {@link Grundstueck#id()} in der Grundstuecksablage - beide beginnen bei
 * 1 und beide zaehlen aufwaerts. In der Freistadtwelt liegen genau diese zwei Arten
 * nebeneinander: ein Block auf einem vergebenen Grundstueck und der Nachbarblock auf
 * blossem Stadtfeld.
 *
 * <p>Waere die Einheit nur eine Zahl, hiesse Grundstueck 5 dasselbe wie Stadtfeld 5. Eine
 * Wache, die zwei Stellen auf "dieselbe Einheit" vergleicht, liesse den Uebergriff dann
 * durch - nicht immer, sondern genau dann, wenn die zwei Zaehler zufaellig gleich stehen.
 * Ein Fehler, der von der Fuellung der Datenbank abhaengt, ist der schlimmste: Er tritt
 * auf einem Probeserver nie auf.
 *
 * <p>Die Art ist ein kurzes Wort und keine Aufzaehlung, und auch das mit Grund: Eine
 * neue Bauwelt bringt eine neue Art mit, und sie soll sich an EINER Stelle anmelden
 * koennen - dort, wo ihre Zustaendigkeit gebaut wird - und nicht zusaetzlich eine
 * Aufzaehlung im Kern aendern muessen.
 *
 * @param art     woher die Nummer kommt, etwa {@code parzelle}, {@code grundstueck},
 *                {@code stadtfeld}, {@code insel}, {@code wolke}
 * @param nummer  die Nummer innerhalb dieser Art
 */
public record Einheit(String art, long nummer) {

    public Einheit {
        if (art == null || art.isBlank()) {
            throw new IllegalArgumentException("Jede Einheit nennt ihre Art - ohne sie"
                    + " waeren zwei Nummern aus verschiedenen Tabellen nicht"
                    + " unterscheidbar, und genau das soll dieser Satz verhindern");
        }
        art = art.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return art + "#" + nummer;
    }
}
