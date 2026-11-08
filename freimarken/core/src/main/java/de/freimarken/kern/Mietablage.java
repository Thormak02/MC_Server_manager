package de.freimarken.kern;

import java.util.List;
import java.util.UUID;

/**
 * Die Mietvertraege - eine Zeile, eine Ladeneinheit.
 *
 * <p>Fuenf Versprechen, auf die sich die Ladenmiete verlaesst und die der
 * {@code MietVertrag} fuer JEDE Umsetzung prueft:
 * <ul>
 *   <li><b>Geschlossen wird je EINHEIT</b>, und der Name der Einheit ist der Schluessel.
 *       Zwei Vertraege ueber denselben Laden kann es darum nicht geben - nicht, weil
 *       irgendwo eine Pruefung steht, sondern weil kein Platz dafuer ist.</li>
 *   <li><b>Ein Laden je Spieler</b> (Plan 7.1: "damit nicht drei Leute die Halle
 *       besitzen") - und das wacht die ABLAGE und nicht nur der Befehl. Ein Befehl, der
 *       erst fragt und dann schreibt, hat zwischen beidem eine Luecke; zwei Fenster,
 *       gleichzeitig abgeschickt, bekaemen beide ein Ja. Darum wirft {@link #schreibe},
 *       wenn dem Spieler schon eine ANDERE Einheit gehoert.</li>
 *   <li><b>Es liegt kein Geld darin.</b> Was gezahlt wurde - Lizenz, Kaution, Miete -,
 *       steht im Buchungsbuch und nur dort. Darum kann kein Verlust dieser Zeile Geld
 *       erzeugen. Die zwei Zahlen, die doch hier stehen - vereinbarte Miete und
 *       hinterlegte Kaution -, sind keine Zahlungen, sondern Abreden; warum sie hier
 *       stehen muessen, erklaert {@link Mietvertrag}.</li>
 *   <li><b>Eine freie Einheit und ein Spieler ohne Laden sind {@code null}</b> und kein
 *       Fehler: Der Normalfall am ersten Tag ist, dass niemand etwas gemietet hat.</li>
 *   <li><b>Die Ablage entscheidet nie, was "heute" ist.</b> Beginn und Ende kommen mit dem
 *       Vertrag herein, aus {@link Tagesgrenze#tagVon}. Sonst braeuchte sie eine zweite
 *       Zone oder ein {@code CURDATE()} - und dann waere die eingestellte Tagesgrenze
 *       stumm wirkungslos, weil die Datenbanksitzung auf UTC steht.</li>
 * </ul>
 */
public interface Mietablage {

    /**
     * Der Vertrag ueber diese Einheit.
     *
     * @param einheit der Name der Ladeneinheit; Gross- und Kleinschreibung gleich
     * @return der Vertrag, oder {@code null}, wenn die Einheit frei ist
     * @throws IllegalArgumentException wenn der Name {@code null} oder leer ist
     * @throws IllegalStateException wenn nicht gelesen werden konnte - derselbe Typ fuer
     *         jede Umsetzung, damit der Aufrufer nicht wissen muss, welche Fassung hinter
     *         der Schnittstelle steht
     */
    Mietvertrag fuerEinheit(String einheit);

    /**
     * Der Vertrag dieses Spielers.
     *
     * @return der Vertrag, oder {@code null}, wenn der Spieler keinen Laden hat
     * @throws IllegalArgumentException wenn der Spieler {@code null} ist
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    Mietvertrag fuerMieter(UUID mieter);

    /**
     * Alle Vertraege - fuer den taeglichen Lauf aus Teil C.
     *
     * <p>Die Reihenfolge ist die der Einheitennamen und nicht die des Abschlusses. Der
     * taegliche Lauf bucht Geld ab, und er soll zweimal hintereinander dieselbe
     * Reihenfolge haben: Sonst ist nach einem Absturz mitten im Lauf nicht zu sagen, wer
     * schon gezahlt hat.
     *
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    List<Mietvertrag> alle();

    /**
     * Schreibt den Vertrag - setzend, mit dem Namen der Einheit als Schluessel.
     *
     * @throws IllegalArgumentException wenn der Vertrag {@code null} ist
     * @throws IllegalStateException wenn dem Mieter schon eine ANDERE Einheit gehoert
     *         (siehe das zweite Versprechen) oder wenn nicht geschrieben werden konnte
     */
    void schreibe(Mietvertrag vertrag);

    /**
     * Loescht den Vertrag ueber diese Einheit - das Kuendigen und das Raeumen.
     *
     * @return {@code true}, wenn es einen gab. {@code false} ist kein Fehler: Ein
     *         Kuendigen, das zweimal ankommt, soll nicht das zweite Mal werfen
     * @throws IllegalArgumentException wenn der Name {@code null} oder leer ist
     * @throws IllegalStateException wenn nicht geloescht werden konnte
     */
    boolean loesche(String einheit);
}
