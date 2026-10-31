package de.freimarken.kern;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * In welchem Stand ein Stueck Besitz ist.
 *
 * <p>Gebraucht wird das erst richtig bei den Mietvertraegen (Plan Schritt 8). Die Spalte
 * entsteht trotzdem jetzt: Sobald es sie gibt, muss die Frage "welcher Zustand erlaubt
 * Bauen" genau einen Wohnort haben. Steht die Antwort spaeter an acht Abfragen verteilt,
 * muss Schritt 8 sie alle finden - und eine davon wird uebersehen.
 *
 * <p>Der Wohnort ist {@link #erlaubtBauen()}. In SQL wird {@code zustand} bewusst
 * <b>nirgends</b> gefiltert: Der eindeutige Schluessel {@code ort (welt, x, z)} gilt ueber
 * alle Zeilen. Wuerde eine Suche eine verfallene Zeile ausblenden, meldete sie die Flaeche
 * als frei, das Besitzbuch buchte den Preis ab und liefe anschliessend in genau diesen
 * Schluessel. Zwei Lesewege, zwei Wahrheiten - und bezahlt hat der Spieler trotzdem.
 */
public enum Zustand {

    /** Laeuft. Der Normalfall und das einzige, was heute entsteht. */
    AKTIV("aktiv", true),

    /**
     * Gekuendigt, aber noch nicht abgelaufen.
     *
     * <p>Bauen bleibt erlaubt, und das ist keine Nachlaessigkeit: Wer raeumen soll, muss
     * seine Kisten oeffnen und abbauen duerfen. Ein Mieter, der ab der Kuendigung an seine
     * eigenen Sachen nicht mehr herankommt, verliert sie - die Kuendigung waere dann eine
     * Enteignung mit Frist.
     */
    GEKUENDIGT("gekuendigt", true),

    /** Abgelaufen und noch nicht abgeraeumt. Hier darf niemand mehr bauen. */
    VERFALLEN("verfallen", false);

    private static final Map<String, Zustand> NACH_SCHLUESSEL = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(Zustand::schluessel, Function.identity()));

    private final String schluessel;
    private final boolean erlaubtBauen;

    Zustand(String schluessel, boolean erlaubtBauen) {
        this.schluessel = schluessel;
        this.erlaubtBauen = erlaubtBauen;
    }

    /** Der Text, der in der Spalte steht - ASCII, also {@code gekuendigt} ohne Umlaut. */
    public String schluessel() {
        return schluessel;
    }

    /** Ob in diesem Zustand ueberhaupt jemand bauen darf - auch der Eigentuemer nicht. */
    public boolean erlaubtBauen() {
        return erlaubtBauen;
    }

    /**
     * Der Zustand zu einem gespeicherten Schluessel.
     *
     * @throws IllegalStateException bei einem Wert, den dieser Code nicht kennt. Siehe
     *                               {@link Art#ausSchluessel} - stilles Ersetzen durch
     *                               AKTIV hiesse, auf verfallenem Besitz weiterzubauen.
     */
    public static Zustand ausSchluessel(String schluessel) {
        Zustand gefunden = NACH_SCHLUESSEL.get(schluessel);
        if (gefunden == null) {
            throw new IllegalStateException("Unbekannter Zustand in der Ablage: " + schluessel);
        }
        return gefunden;
    }
}
