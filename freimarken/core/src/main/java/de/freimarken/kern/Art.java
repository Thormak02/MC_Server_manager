package de.freimarken.kern;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Was fuer ein Stueck Besitz ein Datensatz beschreibt.
 *
 * <p>Alle Arten liegen in derselben Tabelle. Das ist der Kniff aus Plan 6.1: Ein
 * Grundstueck in einer Freistadt ist derselbe Datensatz wie eine Parzelle in der
 * Marktstadt, nur mit einem Elternteil. Dadurch faellt der Spielermarkt - Stadtbesitzer
 * vergeben eigene Grundstuecke zu eigenen Preisen - aus dem Modell heraus, statt eine
 * Sonderlocke zu brauchen.
 *
 * <p>Die Reihenfolge der Werte bedeutet nichts. Nirgends wird nach {@code art} sortiert
 * und nirgends ihre Ordnungszahl gespeichert; in der Datenbank steht der
 * {@link #schluessel()}. Ein neuer Wert darf deshalb an jeder Stelle dazu - er kostet
 * dann allerdings einen Schemaschritt, weil die Spalte ein ENUM ist.
 *
 * <p>Vier der sechs Arten entstehen wirklich, und jede auf ihrem EIGENEN Schreibweg:
 * {@link #PARZELLE} (anlegen), {@link #FREISTADT} (freistadtAnlegen), {@link #WERDER}
 * (werderAnlegen) und {@link #WOLKE} (wolkeAnlegen). Jeder dieser Wege traegt seine
 * Art als LITERAL - ein durchgereichter Parameter liesse jeden Aufrufer jede Art
 * erzeugen, auch eine {@link #GRUPPE} ohne Kinder. Die {@link #GRUPPE} entsteht beim
 * Zusammenlegen; die {@link #LADENEINHEIT} ist fuer die Markthalle vorgesehen.
 *
 * <p>Der Satz an dieser Stelle sagte bis zum 18.11.2026 "heute entsteht ausschliesslich
 * PARZELLE" und stand damit seit Schritt 5 falsch da.
 */
public enum Art {

    /** Ein Feld im Raster der Marktstadt. Das einzige, was heute entsteht. */
    PARZELLE("parzelle"),

    /** Mehrere zusammengelegte Felder. Hat selbst keine Rasterzelle - siehe Plan 6.3. */
    GRUPPE("gruppe"),

    /** Eine gekaufte Insel. Plan 4.1. */
    WERDER("werder"),

    /** Eine Stadt, die ein Spieler gegruendet hat und in der er selbst vergibt. */
    FREISTADT("freistadt"),

    /** Ein gemieteter Laden in der Markthalle. Plan 7. */
    LADENEINHEIT("ladeneinheit"),

    /**
     * Eine Skyblockinsel in Wolkenwerder. Plan 4.1, Schritt 11.
     *
     * <p>Eine eigene Art und nicht {@link #WERDER} noch einmal, obwohl beide ein
     * Rasterfeld in einer Werderwelt sind und keine der beiden einen Namen traegt. Der
     * Grund steht in der Spalte: Nur mit einer eigenen Art laesst sich eine Zeile
     * NACH IHR fragen - und genau das braucht die Zuteilung einer Wolkeninsel. In einer
     * Welt aus Leere kann niemand auf dem Feld STEHEN, das er haben will; das Buch
     * sucht es stattdessen aus der Liste der schon vergebenen, wie das Freistadtbuch.
     * Mit art='werder' fuer beide Welten liefe diese Abfrage ueber Hafenwerder mit.
     */
    WOLKE("wolke");

    private static final Map<String, Art> NACH_SCHLUESSEL = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(Art::schluessel, Function.identity()));

    private final String schluessel;

    Art(String schluessel) {
        this.schluessel = schluessel;
    }

    /** Der Text, der in der Spalte steht - ASCII und klein, wie das Schema ihn fuehrt. */
    public String schluessel() {
        return schluessel;
    }

    /**
     * Die Art zu einem gespeicherten Schluessel.
     *
     * <p>Ueber eine Tabelle und nicht ueber {@code valueOf(name().toUpperCase())}: Ohne
     * {@code Locale.ROOT} macht ein tuerkisches Gebietsschema aus {@code ladeneinheit} ein
     * {@code LADENEİNHEİT} mit Punkt auf dem I, und der Aufruf scheitert auf genau einem
     * Rechner der Welt. Diese Falle wird hier gar nicht erst gebaut.
     *
     * @throws IllegalStateException wenn die Datenbank einen Wert fuehrt, den dieser Code
     *                               nicht kennt - etwa nach einem Rueckschritt auf ein
     *                               aelteres Jar. Lauter Abbruch ist richtig: Eine stille
     *                               Ersetzung durch PARZELLE machte aus einer Freistadt
     *                               eine Parzelle, und das faellt erst auf, wenn jemand
     *                               sie kauft.
     */
    public static Art ausSchluessel(String schluessel) {
        Art gefunden = NACH_SCHLUESSEL.get(schluessel);
        if (gefunden == null) {
            throw new IllegalStateException("Unbekannte Art in der Ablage: " + schluessel);
        }
        return gefunden;
    }
}
