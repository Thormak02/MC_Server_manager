package de.freimarken.kern;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Wie weit eine Freistadt auf dem Weg zum Verfall ist - die EINE Rechnung [Teil E5].
 *
 * <p>Gefragt wird sie an drei Stellen: vom taeglichen Lauf, der den Zustand setzt; vom
 * Beitritt, der den Stadtbesitzer warnt; und von der Liste {@code /freistadt}. Stuende
 * die Rechnung dort dreimal, bekaeme ein Besitzer eine Warnung, die zu einem anderen Tag
 * gehoert als der Verfall.
 *
 * <p><b>Statisch und ohne Zustand</b>, wie {@link Shopregel} und {@link Mietregel}: Alles
 * kommt herein, nichts wird gemerkt. Damit ist jeder der fuenf Staende ohne Datenbank und
 * ohne Uhr pruefbar.
 *
 * <p><b>Keine Warnung wird GEZAEHLT</b>, und das ist die tragende Entscheidung dieser
 * Klasse. Zwei Warnungen heissen nicht "zwei verschickte Nachrichten", sondern ZWEI
 * STUFEN: Wer sich anmeldet, waehrend seine Stadt in der ersten Stufe steht, liest die
 * erste; wer sich in der zweiten anmeldet, die zweite. Ein Zaehler an der Stadtzeile
 * waere eine zweite Wahrheit ueber "schon gewarnt" - er stuende falsch, sobald eine
 * Nachricht verloren geht, und er machte aus einem Besuch keine Entwarnung. So dagegen
 * ist die Zeile {@code besucht} die ganze Wahrheit: Ein Besuch setzt den Stand sofort
 * auf {@link Stand#AKTIV} zurueck, und wer nichts tut, liest die Warnung bei JEDER
 * Anmeldung wieder.
 */
public final class Verfallsregel {

    /** Wie weit eine Stadt ist. */
    public enum Stand {

        /** Nichts zu tun: besucht, bewohnt, oder der Verfall ist abgeschaltet. */
        AKTIV,

        /** Die erste Warnung gilt. */
        WARNUNG_FRUEH,

        /** Die zweite, dringendere Warnung gilt. */
        WARNUNG_SPAET,

        /** Die Frist ist um - der taegliche Lauf setzt diese Stadt auf VERFALLEN. */
        FAELLIG,

        /**
         * Steht schon auf {@link Zustand#VERFALLEN}.
         *
         * <p>Ein eigener Stand und nicht {@code FAELLIG}: Jener ist eine ANWEISUNG an
         * den Lauf, dieser eine Feststellung. Ohne die Trennung setzte der Lauf jeden
         * Tag denselben Zustand neu - und jeder Schreibvorgang leert den
         * Zwischenspeicher aller Parzellen.
         */
        VERFALLEN;
    }

    private Verfallsregel() {
    }

    /**
     * Der Stand dieser Stadt am Tag {@code heute}.
     *
     * <p><b>Die Reihenfolge der Zweige ist die Aussage</b>:
     * <ol>
     *   <li>Abgeschalteter Verfall gewinnt ueber alles - auch ueber eine Stadt, die
     *       schon verfallen ist. Wer ihn abschaltet, soll keine Warnungen mehr lesen.</li>
     *   <li>Ein BUERGER haelt die Stadt am Leben, auch ohne Besuch. Das ist die zweite
     *       Haelfte der Regel aus Plan 16 ("unbesucht UND ohne Buerger"), und sie steht
     *       VOR der Fristrechnung: Wo jemand wohnt, ist keine Geisterstadt, und ein
     *       Besitzer soll dafuer nicht gewarnt werden.</li>
     *   <li>Erst dann die Frist.</li>
     * </ol>
     *
     * @param hatBuerger ob die Stadt mindestens einen Stadtbuerger hat - hereingegeben,
     *                   weil das die Grundstuecke fragt und diese Klasse keine kennt
     * @param zone       nur, um die GRUENDUNG in einen Tag zu verwandeln: Hat eine Stadt
     *                   noch nie Besuch gehabt, laeuft ihre Frist ab dem Tag, an dem sie
     *                   entstand
     */
    public static Stand stand(Parzelle stadt, boolean hatBuerger, Verfallswerte werte,
            LocalDate heute, ZoneId zone) {
        if (stadt == null || !stadt.istFreistadt()) {
            throw new IllegalArgumentException("Verfallen koennen nur Freistaedte");
        }
        if (heute == null || zone == null || werte == null) {
            throw new IllegalArgumentException("Ohne Tag, Zone und Werte gibt es keine"
                    + " Frist");
        }
        if (!werte.an()) {
            return Stand.AKTIV;
        }
        if (stadt.zustand() == Zustand.VERFALLEN) {
            return Stand.VERFALLEN;
        }
        if (hatBuerger) {
            return Stand.AKTIV;
        }
        long ohneBesuch = tageOhneBesuch(stadt, heute, zone);
        if (ohneBesuch >= werte.unbesuchtTage()) {
            return Stand.FAELLIG;
        }
        long bisZumVerfall = werte.unbesuchtTage() - ohneBesuch;
        if (bisZumVerfall <= werte.warnungSpaetTage()) {
            return Stand.WARNUNG_SPAET;
        }
        if (bisZumVerfall <= werte.warnungFruehTage()) {
            return Stand.WARNUNG_FRUEH;
        }
        return Stand.AKTIV;
    }

    /**
     * Wie viele Tage es noch bis zum Verfall sind - fuer den Satz an den Besitzer.
     *
     * <p>Nie negativ: Ist die Frist um, steht hier 0. Eine negative Zahl im Satz liest
     * sich wie ein Fehler des Programms, und was danach geschieht, entscheidet ohnehin
     * der Lauf und nicht diese Zahl.
     */
    public static long resttage(Parzelle stadt, Verfallswerte werte, LocalDate heute,
            ZoneId zone) {
        return Math.max(0L, werte.unbesuchtTage() - tageOhneBesuch(stadt, heute, zone));
    }

    /**
     * Wie lange niemand da war.
     *
     * <p><b>Ab dem letzten Besuch, und ohne einen ab der GRUENDUNG.</b> Das ist der
     * Grund, warum {@link Parzelle#NIE_BESUCHT} ein {@code null} ist und nicht der
     * 1.1.1970: Mit jenem waere jede frisch gegruendete Stadt am Tag ihrer Gruendung
     * faellig, und 400.000 Mark waeren weg, bevor der Besitzer das erste Grundstueck
     * zugeschnitten hat.
     *
     * <p>Gerechnet ueber {@code toEpochDay}, nie ueber eine {@code Duration}: Ein Tag
     * hat bei der Zeitumstellung 23 oder 25 Stunden, und nach einem halben Jahr
     * verschiebt eine Sekundenrechnung die Frist um einen ganzen Tag.
     *
     * <p>Ein Besuch in der ZUKUNFT gibt eine negative Zahl, und das ist hier harmlos:
     * Die Stadt gilt dann als besucht. {@code Freistadtbuch.stempleBesuch} heilt den
     * Wert beim naechsten Besuch.
     */
    private static long tageOhneBesuch(Parzelle stadt, LocalDate heute, ZoneId zone) {
        LocalDate letzter = stadt.besucht() != null
                ? stadt.besucht()
                : Tagesgrenze.tagVon(stadt.erworbenAm(), zone);
        return heute.toEpochDay() - letzter.toEpochDay();
    }
}
