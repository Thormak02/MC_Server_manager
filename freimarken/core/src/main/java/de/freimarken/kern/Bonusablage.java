package de.freimarken.kern;

import java.util.UUID;

/**
 * Die Serie je Spieler - eine Zeile, ein Spieler.
 *
 * <p>Vier Versprechen, auf die sich der Tagesbonus verlaesst und die der
 * {@code BonusVertrag} fuer JEDE Umsetzung prueft:
 * <ul>
 *   <li><b>Geschrieben wird SETZEND und nicht aufschlagend</b> - anders als bei
 *       {@link Spielzeitablage}. Dort ist die Zahl eine Summe, die zwei Server gemeinsam
 *       fuellen; hier ist sie ein Zustand, und zwei Server, die beide "plus eins" sagen,
 *       machten aus einem Tag zwei. Der Preis ist, dass ein Schreibzug den anderen
 *       ueberschreiben kann - und der ist tragbar, weil der Bonus HOECHSTENS EINMAL AM TAG
 *       faellt und das Buchungsbuch darueber wacht.</li>
 *   <li><b>Es liegt kein Geld darin.</b> Ob heute schon gezahlt wurde, entscheidet allein
 *       {@link Kasse#summeSeit}. Darum kann kein Verlust dieser Zeile Geld erzeugen - nur
 *       eine Serie verkuerzen.</li>
 *   <li><b>Ein unbekannter Spieler hat {@link Bonusstand#leer}</b> und keinen Fehler:
 *       Jeder faengt ohne Serie an, und der erste Bonus legt die Zeile an.</li>
 *   <li><b>Die Ablage entscheidet nie, was "heute" ist.</b> Der Tag kommt mit dem Stand
 *       herein, aus {@link Tagesgrenze#tagVon}. Sonst braeuchte sie eine zweite Zone oder
 *       ein {@code CURDATE()} - und dann waere die eingestellte Tagesgrenze stumm
 *       wirkungslos, weil die Datenbanksitzung auf UTC steht.</li>
 * </ul>
 */
public interface Bonusablage {

    /**
     * Die Serie dieses Spielers.
     *
     * @return der Stand; fuer einen unbekannten Spieler {@link Bonusstand#leer}
     * @throws IllegalStateException wenn nicht gelesen werden konnte - derselbe Typ fuer
     *         jede Umsetzung, damit der Aufrufer nicht wissen muss, welche Fassung hinter
     *         der Schnittstelle steht
     */
    Bonusstand lies(UUID spieler);

    /**
     * Schreibt die Serie - setzend.
     *
     * @throws IllegalArgumentException wenn der Stand {@code null} ist
     * @throws IllegalStateException wenn nicht geschrieben werden konnte. Der Aufrufer
     *         faengt das, protokolliert es und zahlt NICHT erneut: Gezahlt ist gezahlt,
     *         und die verlorene Zeile kostet hoechstens eine Serie
     */
    void schreibe(Bonusstand stand);
}
