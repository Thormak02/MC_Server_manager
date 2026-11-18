package de.freimarken.kern;

import java.time.Instant;
import java.util.List;

/**
 * Wo der Zustand einer Wolkeninsel liegt - Biom, Punkte, erfuellte Herausforderungen.
 *
 * <p>Die Zusagen, die der {@code WolkenVertrag} fuer JEDE Umsetzung prueft:
 *
 * <ul>
 *   <li><b>Eine Insel ohne Zeile hat den LEEREN Stand</b> und ist kein Fehler
 *       ({@link Inselstand#leer}). Jede frisch genommene Insel steht so da, und darum
 *       legt das Nehmen gar keine Zeile an - sie entsteht beim ersten Biom, bei der
 *       ersten Herausforderung oder beim ersten Zaehlen.</li>
 *   <li><b>Erfuellen sagt, ob es NEU war.</b> Daran haengt die Belohnung: Wer dieselbe
 *       Herausforderung zweimal abgibt, bekommt sie nicht zweimal bezahlt - und der
 *       Riegel dagegen gehoert der Ablage und nicht dem Aufrufer. Eine Pruefung davor
 *       haette zwischen Fragen und Schreiben eine Luecke, und zwei Abgaben im selben
 *       Augenblick zahlten zweimal.</li>
 *   <li><b>Punkte und Biom werden SETZEND geschrieben</b> - anders als bei der
 *       {@link Truhenablage}, wo die Zahl eine Summe ist. Hier ist sie ein Zustand: Das
 *       Ergebnis des letzten Zaehlens ersetzt das vorige vollstaendig, denn die Insel
 *       hat nicht mehr Punkte, weil sie oefter gezaehlt wurde.</li>
 *   <li><b>Das Entfernen loescht die Zeile UND die Herausforderungen.</b> Wer seine Insel
 *       freigibt, gibt ihren Fortschritt mit auf; der naechste Spieler auf diesem Feld
 *       faengt bei Stufe 0 an. Ohne diese Zusage erbte er die Erzausbeute eines
 *       Fremden.</li>
 *   <li><b>Jeder Lesefehler ist ein {@link IllegalStateException}</b> - derselbe Typ fuer
 *       jede Umsetzung, damit der Aufrufer nicht wissen muss, welche Fassung dahinter
 *       steht.</li>
 * </ul>
 *
 * <p><b>Warum es kein {@code beste(n)} gibt</b>: Die Rangliste braucht neben der Punktzahl
 * den EIGENTUEMER, und der steht in {@code parzellen}. Eine Abfrage ueber beide Tabellen
 * waere in der Fassung im Arbeitsspeicher eine zweite, handgeschriebene Zusammenfuehrung -
 * also genau die Stelle, an der zwei Umsetzungen auseinanderlaufen. Darum gibt es
 * {@link #alle}, und die Zusammenfuehrung macht das Wolkenbuch mit {@code besitzIn} in
 * Java: Bei hoechstens 1.443 Inseln ist das eine Liste, die in eine Handvoll
 * Kilobytes passt.
 */
public interface Wolkenablage {

    /**
     * Der Stand dieser Insel.
     *
     * @return der gespeicherte Stand, oder {@link Inselstand#leer} fuer eine Insel ohne
     *         Zeile - nie {@code null}
     * @throws IllegalStateException wenn nicht gelesen werden konnte
     */
    Inselstand stand(long inselId);

    /**
     * Der Stand JEDER Insel, die eine Zeile hat.
     *
     * <p>Zwei Fragen in einer, und beide sind selten: die Rangliste (Teil E) und die
     * Stufen beim Start. Inseln ohne Zeile fehlen - sie haben den leeren Stand, und der
     * steht in keiner Tabelle.
     *
     * <p>Die Reihenfolge ist NICHT zugesagt: Wer sortiert braucht, sortiert. Eine
     * zugesagte Reihenfolge waere hier eine zweite Entscheidung darueber, was "die beste
     * Insel" ist - und die gehoert in die Rangliste und nicht in die Ablage.
     */
    List<Inselstand> alle();

    /**
     * Setzt das Biom dieser Insel.
     *
     * <p>Setzend und nicht anfuegend: Eine Insel hat EIN Biom. Die Zeile entsteht, wenn
     * es noch keine gibt.
     */
    void biomSetzen(long inselId, String biom);

    /**
     * Setzt die Punktzahl und den Zeitpunkt des Zaehlens.
     *
     * <p>Beides zusammen und in EINEM Schreibzug: Eine Punktzahl ohne ihren Zeitpunkt
     * waere eine Zahl, von der niemand weiss, wie alt sie ist - und ein Zeitpunkt ohne
     * Punktzahl ein Zaehlen, das nichts ergeben hat.
     */
    void punkteSetzen(long inselId, long punkte, Instant wann);

    /**
     * Traegt eine erfuellte Herausforderung ein.
     *
     * @return {@code true}, wenn sie NEU war - nur dann gibt es eine Belohnung
     */
    boolean erfuellen(long inselId, String schluessel);

    /** Loescht den ganzen Stand dieser Insel samt ihren Herausforderungen. */
    void entfernen(long inselId);
}
