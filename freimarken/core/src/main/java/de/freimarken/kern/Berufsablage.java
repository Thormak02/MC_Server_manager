package de.freimarken.kern;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Wo die Berufe eines Spielers liegen - der Vertrag, den beide Umsetzungen halten.
 *
 * <p>Fuenf Zusagen, und jede steht hier, weil ihr Gegenteil einen Fehler ergeben hat oder
 * ergeben wuerde:
 *
 * <ol>
 *   <li><b>Die Ablage entscheidet nie, welcher Beruf welche ROLLE hat.</b> Ob
 *       {@code steinmetz} eine Spezialisierung ist, steht in der Einstellungsdatei und
 *       beantwortet {@link Berufsverzeichnis}. Haette die Ablage eine eigene Spalte
 *       dafuer, gaebe es die Rolle zweimal - und bei einer Aenderung in der Datei waere
 *       die in der Datenbank die falsche. Dieselbe Begruendung, aus der ein Buchungsgrund
 *       seine Richtung nicht in der Tabelle traegt.</li>
 *   <li><b>Geschrieben wird gegen einen ERWARTETEN Stand</b>, nie lesen-rechnen-schreiben.
 *       Wer liest, in Java rechnet und zurueckschreibt, verliert bei zwei gleichzeitigen
 *       Zuegen einen: Zwei {@code /beruf waehlen} in derselben Sekunde ergaeben drei
 *       Grundberufe. Dieselbe Zusage wie bei {@link Kasse#abbuchen} und mit derselben
 *       Antwort - {@code false} heisst "dieser Stand gilt nicht mehr", und dann ist NICHTS
 *       passiert.</li>
 *   <li><b>Ein Verlassen loescht nichts, es legt ab.</b> Die Zeile bleibt stehen. Zwei
 *       Gruende: Der Zeitpunkt des Ablegens IST die Sperrfrist, und ein {@code DELETE}
 *       liesse sie mit der Zeile verschwinden - Aufgeben und Warten waere dann der Weg um
 *       Gebuehr und Frist herum. Und Teil G will die Geschichte.</li>
 *   <li><b>Die Sperrfrist ist der juengste Zeitpunkt eines ABLEGENS</b>, nicht eines
 *       Waehlens. Sonst sperrte das erste {@code /beruf waehlen} einen neuen Spieler einen
 *       Tag lang aus seinem zweiten Grundberuf.</li>
 *   <li><b>Die Ablage entscheidet nie, was "jetzt" ist.</b> Der Zeitpunkt kommt im
 *       {@link Antrag} von aussen, wie bei {@link Spielzeitablage}. Eine Ablage mit eigener
 *       Uhr liesse sich nicht pruefen, ohne zu warten.</li>
 * </ol>
 */
public interface Berufsablage {

    /**
     * Ein Antrag auf EINEN Beruf - geprueft beim Bauen, nicht beim Schreiben.
     *
     * @param spieler wer
     * @param beruf   welcher Beruf, kleingeschrieben wie in der Datei
     * @param jetzt   der Zeitpunkt, den die Ablage eintragen soll
     */
    record Antrag(UUID spieler, String beruf, Instant jetzt) {

        public Antrag {
            if (spieler == null || jetzt == null) {
                throw new IllegalArgumentException("Spieler und Zeitpunkt sind Pflicht;"
                        + " die Ablage hat keine eigene Uhr");
            }
            if (beruf == null || beruf.isBlank()) {
                throw new IllegalArgumentException("Ein Antrag ohne Beruf");
            }
            // Kleingeschrieben hier, damit die Ablage nicht ein zweites Mal normalisiert.
            // Zwei Stellen, die denselben Namen zurechtbiegen, biegen ihn eines Tages
            // verschieden.
            beruf = beruf.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Was dieser Spieler haelt.
     *
     * <p>Fuer einen Spieler, von dem die Ablage nichts weiss, {@link Berufsstand#leer} -
     * kein {@code null} und kein Wurf. Ein Neuling ist der Normalfall.
     */
    Berufsstand lies(UUID spieler);

    /**
     * Nimmt den Beruf an - alles oder nichts.
     *
     * @param erwartet der Stand, auf dem die Regel geurteilt hat
     * @return {@code false}, wenn dieser Stand nicht mehr gilt; dann ist NICHTS passiert
     */
    boolean waehle(Antrag antrag, Berufsstand erwartet);

    /**
     * Legt den Beruf ab und setzt damit die Sperrfrist - dieselbe Zusage.
     *
     * <p>Der Beruf verschwindet aus {@link Berufsstand#berufe}, und
     * {@link Berufsstand#letztesAblegen} traegt danach den Zeitpunkt des Antrags.
     *
     * <p><b>Der erwartete Stand muss den Beruf halten.</b> Sonst ein Wurf und kein
     * {@code false}: {@code false} heisst "dieser Stand gilt nicht mehr", und der Aufrufer
     * versucht es mit dem neuen noch einmal - was hier nie gelingen koennte. Die Regel hat
     * den Fall vorher als {@code NICHT_GEHALTEN} abgewiesen; wer trotzdem hierher kommt,
     * hat einen Rechenfehler. Geprueft wird fuer beide Umsetzungen von
     * {@link #pruefeAbzulegen}, denn eine Tabelle kann den Fall gar nicht anders
     * behandeln: Ohne Zeile gibt es nichts, woran ein Zeitpunkt haengt, und eine Zeile nur
     * fuer die Frist zu schreiben erfaende einen Beruf, den der Spieler nie hatte.
     */
    boolean verlasse(Antrag antrag, Berufsstand erwartet);

    /**
     * Die eine Pruefung, die beide Umsetzungen von {@link #verlasse} machen.
     *
     * <p>Hier und nicht zweimal: Zwei Fassungen derselben Pruefung weichen eines Tages
     * ab, und dann verhaelt sich der fluechtige Betrieb anders als der echte - gefunden
     * wird das nicht im Testlauf, sondern von einem Spieler.
     */
    static void pruefeAbzulegen(Antrag antrag, Berufsstand erwartet) {
        if (!erwartet.haelt(antrag.beruf())) {
            throw new IllegalArgumentException("Abzulegen ist " + antrag.beruf()
                    + ", aber der erwartete Stand von " + erwartet.spieler() + " haelt nur "
                    + erwartet.berufe() + ". Das ist kein veralteter Stand, sondern ein"
                    + " Rechenfehler: Die Regel weist diesen Fall als NICHT_GEHALTEN ab.");
        }
    }
}
