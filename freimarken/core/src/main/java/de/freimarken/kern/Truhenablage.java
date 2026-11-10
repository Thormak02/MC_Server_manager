package de.freimarken.kern;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Was ein Spieler an einem Tag aus der Freien Truhe behalten hat - eine Zeile je Tag.
 *
 * <p>Vier Versprechen, auf die sich die Truhe verlaesst und die der
 * {@code TruhenVertrag} fuer JEDE Umsetzung prueft:
 *
 * <ul>
 *   <li><b>Geschrieben wird AUFSCHLAGEND und nicht setzend</b> - anders als bei
 *       {@link Bonusablage} und wie bei {@link Spielzeitablage}. Dort ist die Zahl ein
 *       Zustand, hier eine SUMME: Wer dreimal am Tag an die Truhe geht, soll dreimal
 *       zaehlen. Ein setzendes Schreiben verloere die vorigen Griffe, und der Deckel
 *       waere so oft offen, wie man die Truhe schliesst.</li>
 *   <li><b>Es liegt kein Geld darin</b>, sondern eine Zaehlung von STUECK. Darum ist eine
 *       verlorene Zeile kein Geld aus nichts - sie oeffnet hoechstens den Deckel eines
 *       Tages. Dass die Zahl trotzdem in der Datenbank steht und nicht im
 *       Arbeitsspeicher, hat denselben Grund wie beim Verdienstdeckel: Ein Zaehler im
 *       Speicher ueberlebte keinen Neustart, und dann waere der Deckel nach jedem
 *       Neustart offen.</li>
 *   <li><b>Ein unbekannter Spieler hat 0</b> und keinen Fehler: Jeder faengt mit leerem
 *       Tag an, und der erste Griff legt die Zeile an.</li>
 *   <li><b>Die Ablage entscheidet nie, was "heute" ist.</b> Der Tag kommt herein, aus
 *       {@link Tagesgrenze#tagVon}. Sonst braeuchte sie eine zweite Zone oder ein
 *       {@code CURDATE()} - und dann waere die eingestellte Tagesgrenze stumm
 *       wirkungslos, weil die Datenbanksitzung auf UTC steht.</li>
 * </ul>
 */
public interface Truhenablage {

    /**
     * Wie viele Stueck dieser Spieler an diesem Tag schon behalten hat.
     *
     * @return 0 fuer einen Spieler ohne Zeile an diesem Tag
     * @throws IllegalStateException wenn nicht gelesen werden konnte - derselbe Typ fuer
     *         jede Umsetzung, damit der Aufrufer nicht wissen muss, welche Fassung hinter
     *         der Schnittstelle steht
     */
    int behalten(UUID spieler, LocalDate tag);

    /**
     * Schlaegt Stueck auf den Tag dieses Spielers auf.
     *
     * @param stueck wie viele dazukommen - mindestens 1. Eine 0 waere eine Zeile ohne
     *               Anlass, und ein negativer Wert eine Rueckgabe; Rueckgaben gibt es
     *               nicht, denn angerechnet wird erst, was er BEHAELT
     * @throws IllegalArgumentException bei {@code null} oder {@code stueck < 1}
     * @throws IllegalStateException wenn nicht geschrieben werden konnte. Der Aufrufer
     *         faengt das und protokolliert es; die Ware ist dann schon beim Spieler, und
     *         der verlorene Zaehler kostet hoechstens einen offenen Deckel
     */
    void merke(UUID spieler, LocalDate tag, int stueck);

    /**
     * Loescht alle Zeilen von VOR diesem Tag.
     *
     * <p>Gerufen vom taeglichen Lauf, zusammen mit dem Leeren der Truhe. Ohne das waechst
     * die Tabelle mit jedem Spieler und jedem Tag weiter, und gefragt wird immer nur nach
     * HEUTE - die Zeilen von gestern beantworten keine Frage mehr.
     *
     * <p>Die Grenze ist AUSSCHLIESSEND: {@code raeume(heute)} laesst den heutigen Tag
     * stehen. Ein einschliessendes Loeschen raeumte den laufenden Tag weg und oeffnete
     * jedem den Deckel noch einmal.
     *
     * @return wie viele Zeilen verschwunden sind
     * @throws IllegalStateException wenn nicht geloescht werden konnte
     */
    int raeume(LocalDate vorTag);
}
