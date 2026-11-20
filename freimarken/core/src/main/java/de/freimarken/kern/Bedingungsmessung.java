package de.freimarken.kern;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Woher die gemessenen Werte eines Spielers kommen - die EINE Naht dafuer.
 *
 * <p><b>Warum sie eine Naht ist und keine Abhaengigkeit:</b> Gemessen wird an genau einer
 * Stelle, im Aufstiegslauf des Spielmoduls - dort steht der Schalter ueber die
 * {@link Bedingungsart}en, und dort allein wird gezaehlt, gefragt und gerechnet. Haette
 * jeder Leser seinen eigenen Messer, urteilte ein Kauf im Muenzhaus eines Tages anders
 * als eine Befoerderung, und {@code /rang} zeigte eine dritte Zahl.
 *
 * <p><b>Hiess bis zum 20.11.2026 {@code Muenzhaus.Bedingungsmessung}.</b> Mit
 * {@code /rang} bekam sie den zweiten Leser, und eine Naht, die zwei Klassen brauchen,
 * gehoert keiner von beiden.
 *
 * <p>Die Liste der ARTEN gibt der Leser vor: Jede Messung ausser dem Beruf ist eine
 * Datenbankabfrage im Hauptfaden, und was niemand braucht, soll niemand fragen.
 */
public interface Bedingungsmessung {

    /**
     * Die gemessenen Werte zu diesen Arten, in der MESSEINHEIT (Sekunden fuer die
     * Zeitarten, Stueck sonst).
     *
     * <p>Eine Art, die nicht in der Karte steht, zaehlt bei der Beurteilung als 0 -
     * {@link Rangregel} entscheidet das und nicht diese Naht.
     */
    Map<Bedingungsart, Long> fuer(UUID wer, List<Bedingungsart> gebraucht);
}
