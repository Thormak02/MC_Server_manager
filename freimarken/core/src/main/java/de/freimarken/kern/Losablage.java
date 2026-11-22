package de.freimarken.kern;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Die Lose je Spieler und Tag - eine Zeile, ein Spieler an einem Tag.
 *
 * <p>Vier Versprechen, auf die sich die Lotterie verlaesst und die der
 * {@code LosVertrag} fuer JEDE Umsetzung prueft:
 * <ul>
 *   <li><b>Geschrieben wird AUFSCHLAGEND und nicht setzend</b> - anders als bei
 *       {@link Bonusablage}. Dort ist die Zahl ein Zustand, und zwei Server, die beide
 *       "plus eins" sagen, machten aus einem Tag zwei. Hier ist sie eine ZAEHLUNG: Zwei
 *       Server, die beide ein Los melden, meinen zwei Lose - und es sind zwei. Setzend
 *       verschluckte eines davon, und das verbilligt das naechste.</li>
 *   <li><b>Hier liegt Geld</b> - auch das anders als beim Bonus. Diese Zahl ist die
 *       EINZIGE Wahrheit darueber, was das naechste Los kostet; aus dem Buchungsbuch ist
 *       sie nicht zu holen, weil das gratis Los mit 0 Mark gebucht wird (siehe
 *       {@link Losstand}). Darum zaehlt {@link Lotteriebuch} ZUERST und zahlt danach.</li>
 *   <li><b>Ein unbekannter Spieler hat {@link Losstand#leer}</b> und keinen Fehler:
 *       Jeder faengt jeden Tag bei null an, und das erste Los legt die Zeile an.</li>
 *   <li><b>Die Ablage entscheidet nie, was "heute" ist.</b> Der Tag kommt herein, aus
 *       {@link Tagesgrenze#tagVon}. Sonst braeuchte sie eine zweite Zone oder ein
 *       {@code CURDATE()} - und dann waere die eingestellte Tagesgrenze stumm
 *       wirkungslos, weil die Datenbanksitzung auf UTC steht.</li>
 * </ul>
 */
public interface Losablage {

    /**
     * Wie viele Lose dieser Spieler an diesem Tag gezogen hat.
     *
     * @return der Stand; fuer einen unbekannten Spieler {@link Losstand#leer}
     * @throws IllegalStateException wenn nicht gelesen werden konnte - derselbe Typ fuer
     *         jede Umsetzung, damit der Aufrufer nicht wissen muss, welche Fassung hinter
     *         der Schnittstelle steht
     */
    Losstand lies(UUID spieler, LocalDate tag);

    /**
     * Zaehlt ein Los dazu und gibt die NEUE Zahl zurueck.
     *
     * <p>Aufschlagend, in einem Zug - siehe das erste Versprechen. Der Rueckgabewert ist
     * die Zahl NACH dem Zaehlen, damit der Aufrufer nicht zweimal lesen muss: Ein
     * Lesen-Rechnen-Schreiben aus zwei Faeden verliert ein Los, und ein verlorenes Los
     * ist ein verbilligtes naechstes.
     *
     * @return wie viele Lose der Spieler an diesem Tag nun hat, mindestens 1
     * @throws IllegalStateException wenn nicht geschrieben werden konnte. Der Aufrufer
     *         bricht dann ab und zieht NICHT: Ein ungezaehltes Los ist ein zweites
     *         gratis Los, und das ist eine Geldquelle
     */
    int zaehleEins(UUID spieler, LocalDate tag);
}
