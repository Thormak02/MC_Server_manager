package de.freimarken.kern;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Die gemessene Spielzeit je Spieler und Tag.
 *
 * <p>Sechs Versprechen, auf die sich die Regeln verlassen - und die der
 * {@code SpielzeitVertrag} fuer JEDE Umsetzung prueft:
 * <ul>
 *   <li><b>Fortschreiben ist ADDITIV.</b> Ein Zuschlag wird auf die vorhandene Zahl
 *       aufgeschlagen, nie gesetzt. Zwei Gruende: Nur so fuellen zwei Backend-Server dieselbe
 *       Zeile widerspruchsfrei (lesen-rechnen-schreiben verschluckt den Zuwachs des anderen),
 *       und nur so steht in der Ablage immer schon VERSTRICHENE Zeit und nie ein laufender
 *       Zeitpunkt. Darum kann kein Absturz eine offene Sitzung hinterlassen, und es gibt kein
 *       Aufraeumen beim Start.</li>
 *   <li><b>Ein Aufruf darf nicht wiederholt werden.</b> Additiv heisst: Eine Wiederholung
 *       zaehlt doppelt. Nach einem unklaren Fehler wird deshalb nicht erneut geschrieben,
 *       sondern der Verlust protokolliert - zu viel Spielzeit gibt einen Rang zu frueh, zu
 *       wenig nur zu spaet.</li>
 *   <li><b>Eine LISTE in einem Aufruf, alles oder nichts.</b> Deshalb nimmt
 *       {@link #schreibeFort} eine Liste und nicht einen Zuschlag: Die Datenschicht legt sie
 *       in EINE Transaktion. Ein Takt, der die Haelfte seiner Spieler geschrieben hat und
 *       dann abbricht, waere nicht nachvollziehbar.</li>
 *   <li><b>Ein Zuschlag mit neuem Tag legt eine neue Zeile an und laesst die alten stehen.</b>
 *       Die Vergangenheit laesst sich nicht nachtraeglich in Tage aufteilen; ein einzelner
 *       Zaehler je Spieler waere spaeter nicht nachzuholen.</li>
 *   <li><b>Es gibt kein Setzen.</b> Ein absoluter Wert passt nicht zu einer additiven
 *       Schnittstelle, waere gegen den laufenden Takt rennbar und haette keinen Zeugen: Anders
 *       als beim Geld, wo die Summe aller Buchungen den Stand ergeben muss, widerspricht einer
 *       verfaelschten Spielzeit nichts.</li>
 *   <li><b>Die Ablage entscheidet nie, was "heute" ist.</b> {@link #lies} bekommt den Tag von
 *       aussen. Sonst braeuchte sie eine zweite Zone oder ein {@code CURDATE()} - und dann
 *       waere die eingestellte Tagesgrenze stumm wirkungslos, weil die Datenbanksitzung auf
 *       UTC steht.</li>
 * </ul>
 *
 * <p>Alle Zeiten in Sekunden und nie negativ. Jeder Spieler faengt bei 0 an; aus der
 * Bukkit-Statistik wird nichts uebernommen - sie ist fuer jedes Plugin schreibbar, nicht
 * weltgebunden und von einer Weltkopie verdoppelt, kann also die Zusage "die Datenbank ist
 * die Wahrheit" nicht halten.
 */
public interface Spielzeitablage {

    /**
     * Was einem Spieler an EINEM Tag zugeschlagen wird.
     *
     * <p>Der Tag ist Pflicht und kommt aus {@link Tagesgrenze#tagVon} - nie aus der
     * Datenbank und nie aus der Systemzone.
     *
     * @param spieler  wessen Zeit
     * @param tag      der Tag in der eingestellten Zone
     * @param sekunden volle Sekunden, nie negativ; 0 ist erlaubt und aendert nichts
     */
    record Zuschlag(UUID spieler, LocalDate tag, long sekunden) {

        public Zuschlag {
            if (spieler == null) {
                throw new IllegalArgumentException("Ohne Spieler gibt es keinen Zuschlag");
            }
            if (tag == null) {
                throw new IllegalArgumentException("Ohne Tag gibt es keine Zeile");
            }
            // Geprueft beim BAUEN und nicht beim Schreiben: So kann keine Umsetzung einen
            // negativen Zuschlag anders behandeln als die andere, und ein Rechenfehler faellt
            // an seiner Quelle auf und nicht erst in der Datenbank.
            if (sekunden < 0) {
                throw new IllegalArgumentException("Ein negativer Zuschlag waere ein Abzug,"
                        + " und den gibt es nicht: " + sekunden);
            }
        }
    }

    /**
     * Schreibt die gemessene Zeit fort - additiv, in einem Zug.
     *
     * @param zuschlaege darf leer sein, dann geschieht nichts; mehrere Zuschlaege fuer
     *                   denselben Spieler und Tag addieren sich
     * @throws IllegalArgumentException wenn die Liste oder ein Eintrag {@code null} ist -
     *         und zwar bevor irgendetwas geschrieben ist
     * @throws IllegalStateException wenn nichts geschrieben werden konnte: eine
     *         unerreichbare Datenbank ebenso wie ein Ueberlauf der Sekundensumme. Der Typ
     *         ist fuer JEDE Umsetzung derselbe und nicht die Ausnahme, die der Rechenweg
     *         gerade hergibt - sonst muesste der Aufrufer wissen, welche Fassung hinter der
     *         Schnittstelle steht. Geprueft in {@code SpielzeitVertrag.ueberlaufBrichtAb}.
     *         Der Aufrufer faengt den Fehlschlag, protokolliert die verlorenen Sekunden und
     *         wiederholt NICHT - siehe die zweite Zusage oben
     */
    void schreibeFort(List<Zuschlag> zuschlaege);

    /**
     * Was von diesem Spieler gemessen wurde.
     *
     * @param heute der Tag, dessen Zeit getrennt genannt werden soll - von aussen, weil die
     *              Ablage die Tagesgrenze nicht kennen darf
     * @return der Stand; fuer einen unbekannten Spieler
     *         {@link Spielzeitstand#leer(UUID, LocalDate)}
     */
    Spielzeitstand lies(UUID spieler, LocalDate heute);
}
