package de.freimarken.kern;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Die Punkte und die Anmeldungen des Angelwettbewerbs - eine Zeile je Spieler und Lauf.
 *
 * <p>Fuenf Versprechen, auf die sich der Wettbewerb verlaesst und die der
 * {@code WettbewerbVertrag} fuer JEDE Umsetzung prueft:
 * <ul>
 *   <li><b>Die Anmeldung ist der RIEGEL gegen die doppelte Zahlung.</b>
 *       {@link #meldeAn} gibt {@code false}, wenn die Zeile schon da war - und zwar in
 *       EINEM Zug, nicht nach einer Lesung davor. Zwei Klicks auf
 *       {@code /wettbewerb anmelden} wuerden sonst zweimal Startgeld kosten.</li>
 *   <li><b>Punkte werden AUFSCHLAGEND gezaehlt</b>, wie die Lose der Lotterie: Zwei
 *       Faenge sind zwei Punktgaben und nicht eine.</li>
 *   <li><b>Der ABSCHLUSS ist der zweite Riegel</b>, und er ist der wichtigere:
 *       {@link #merkeAbgeschlossen} gibt {@code false}, wenn der Lauf schon abgerechnet
 *       war. Ohne ihn zahlte ein zweiter Takt denselben Topf ein zweites Mal aus - und
 *       das ist eine Geldquelle, die mit jedem Takt waechst. Dasselbe Muster wie bei
 *       {@code wolke_herausforderung} und {@code rundgang}.</li>
 *   <li><b>Ein unbekannter Spieler hat keine Zeile</b> und keinen Fehler: Wer nicht
 *       angemeldet ist, sammelt keine Punkte, und das ist richtig - sonst wuerde einem
 *       Angler ungefragt Startgeld abgebucht.</li>
 *   <li><b>Die Ablage entscheidet nie, welcher Lauf gilt.</b> Der Tag kommt herein, aus
 *       {@link Wettbewerbswerte#laufAm}. Sonst braeuchte sie eine zweite Zone, eine
 *       zweite Uhrzeit und ein {@code CURDATE()} - und dann waere das eingestellte
 *       Fenster stumm wirkungslos, weil die Datenbanksitzung auf UTC steht.</li>
 * </ul>
 */
public interface Wettbewerbsablage {

    /**
     * Meldet einen Spieler an - mit dem Startgeld, das er WIRKLICH gezahlt hat.
     *
     * @return {@code true}, wenn die Zeile neu war; {@code false}, wenn er schon
     *         angemeldet ist. Der Aufrufer bucht nur bei {@code true} ab
     * @throws IllegalStateException wenn nicht geschrieben werden konnte
     */
    boolean meldeAn(UUID spieler, LocalDate lauf, long startgeld);

    /**
     * Zaehlt Punkte dazu und gibt die NEUE Summe zurueck.
     *
     * <p>Aufschlagend, in einem Zug - wie {@link Losablage#zaehleEins}. Ein
     * Lesen-Rechnen-Schreiben aus zwei Faeden verliert einen Fang.
     *
     * @return die Punkte NACH dem Zaehlen, oder leer, wenn der Spieler nicht angemeldet
     *         ist. Leer ist kein Fehler: Nur Angemeldete sammeln
     * @throws IllegalStateException wenn nicht geschrieben werden konnte
     */
    Optional<Integer> zaehlePunkte(UUID spieler, LocalDate lauf, int punkte);

    /**
     * Alle Zeilen eines Laufs - unsortiert.
     *
     * <p>Sortiert wird in {@link Wettbewerbsregel#rangliste}, und zwar dort allein: Eine
     * Ablage, die ihre Reihenfolge zusagt, waere eine zweite Stelle, an der ueber den
     * Sieger entschieden wird.
     */
    List<Punktzeile> zeilen(LocalDate lauf);

    /** Die Zeile eines Spielers - leer, wenn er nicht angemeldet ist. */
    Optional<Punktzeile> zeile(UUID spieler, LocalDate lauf);

    /**
     * Merkt einen Lauf als abgerechnet.
     *
     * <p>Der ZEITPUNKT kommt herein und nicht aus einer Uhr der Ablage - dieselbe
     * Linie wie bei {@code Rundgangsablage.merke}. Eine eigene Uhr hier waere die
     * zweite im Haus, und zwei Uhren in zwei Tabellen laufen auseinander.
     *
     * @return {@code true}, wenn er es noch nicht war; {@code false}, wenn schon. Der
     *         Aufrufer zahlt nur bei {@code true} aus - siehe das dritte Versprechen
     * @throws IllegalStateException wenn nicht geschrieben werden konnte
     */
    boolean merkeAbgeschlossen(LocalDate lauf, java.time.Instant jetzt);

    /** Ob dieser Lauf schon abgerechnet ist. */
    boolean istAbgeschlossen(LocalDate lauf);
}
