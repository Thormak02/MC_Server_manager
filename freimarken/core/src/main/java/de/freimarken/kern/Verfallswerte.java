package de.freimarken.kern;

/**
 * Wann eine Freistadt verfaellt - und wann sie gewarnt wird [Plan 16, Teil E5].
 *
 * <p>Der Zweck steht im Plan: Eine Stadt, die zwoelf Monate unbesucht ist UND keinen
 * Stadtbuerger hat, ist eine Geisterstadt. Sie blockiert ein Feld, einen Namen und einen
 * Platz in der Hoechstzahl ihres Besitzers, und niemand hat etwas davon.
 *
 * <p><b>Gerechnet wird in TAGEN und nicht in Monaten</b>, wie jede Frist dieses
 * Projekts. Zwoelf Monate sind je nach Anfangstag 365 oder 366 Tage, und mit
 * {@code plusMonths} haengt die Frist am Datum der Gruendung: Eine Stadt vom 31. Januar
 * verfiele am 28. Februar, eine vom 1. Februar am 1. Maerz - dreissig Tage spaeter. Eine
 * Tageszahl ist dieselbe fuer alle und steht ausserdem in der {@code config.yml}.
 *
 * <p><b>Eigenes Satzstueck und kein weiteres Feld an {@link Freistadtwerte}</b>: Jene
 * haetten damit elf Bestandteile, und die vier hier gehoeren zusammen - wer eine Frist
 * verstellt, sieht die anderen drei daneben. Teil E6 haengt den Erstattungsanteil an
 * dieselbe Stelle.
 *
 * @param an            ob der Verfall ueberhaupt laeuft. Auf {@code false} verfaellt
 *                      keine Stadt und es wird nicht gewarnt - der Startbericht sagt es
 * @param unbesuchtTage nach wie vielen Tagen ohne Besuch eine Stadt faellig ist. Gezaehlt
 *                      ab dem letzten Besuch oder, wenn es nie einen gab, ab der
 *                      GRUENDUNG - nicht ab 1970
 * @param warnungFruehTage wie viele Tage VOR der Faelligkeit die erste Warnung gilt
 * @param warnungSpaetTage wie viele Tage vorher die zweite, dringendere gilt. Muss
 *                      kleiner sein als die erste, sonst gibt es die erste nie
 */
public record Verfallswerte(boolean an, int unbesuchtTage, int warnungFruehTage,
                            int warnungSpaetTage) {

    /** Ob der Verfall laeuft. */
    public static final String PFAD_AN = "freistaedte.verfall.an";

    /** Nach wie vielen Tagen ohne Besuch eine Stadt faellig ist. */
    public static final String PFAD_UNBESUCHT_TAGE =
            "freistaedte.verfall.unbesucht-tage";

    /** Wie viele Tage vorher die erste Warnung gilt. */
    public static final String PFAD_WARNUNG_FRUEH =
            "freistaedte.verfall.warnung-frueh-tage";

    /** Wie viele Tage vorher die zweite Warnung gilt. */
    public static final String PFAD_WARNUNG_SPAET =
            "freistaedte.verfall.warnung-spaet-tage";

    /**
     * Der Verfall laeuft ab Werk.
     *
     * <p>Wie bei der Auflage und aus demselben Grund: Eine Vorgabe von {@code false}
     * haette die ganze Regel gebaut und ausgeschaltet geliefert - gruen geprueft und im
     * Spiel wirkungslos. Und die Regel ist mild: Sie trifft nur, was zwoelf Monate
     * niemand betreten hat und wo niemand wohnt.
     */
    public static final boolean VORGABE_AN = true;

    /**
     * Zwoelf Monate als 365 Tage - die Zahl aus Plan 16.
     *
     * <p>Nicht 366: Wer 365 Tage nicht da war, war ein Jahr nicht da. Ein Tag Streit
     * darueber kostet niemanden etwas, und der Betreiber stellt es ein.
     */
    public static final int VORGABE_UNBESUCHT_TAGE = 365;

    /** Zwei Monate vorher die erste Warnung - Zeit genug, einmal vorbeizugehen. */
    public static final int VORGABE_WARNUNG_FRUEH = 60;

    /** Und zwei Wochen vorher die dringende. */
    public static final int VORGABE_WARNUNG_SPAET = 14;

    /**
     * Die groesste sinnvolle Frist - zehn Jahre.
     *
     * <p>Gegen den Vertipper und nicht gegen den Betreiber: Wer 3650 eintraegt, hat den
     * Verfall praktisch abgeschaltet, und dafuer gibt es {@link #an}. Eine Frist von
     * einer Million Tagen waere dasselbe, nur ohne dass es jemand merkt.
     */
    public static final int LAENGSTE_FRIST_TAGE = 3650;

    public static final Verfallswerte VORGABE = new Verfallswerte(VORGABE_AN,
            VORGABE_UNBESUCHT_TAGE, VORGABE_WARNUNG_FRUEH, VORGABE_WARNUNG_SPAET);

    public Verfallswerte {
        if (unbesuchtTage < 1 || unbesuchtTage > LAENGSTE_FRIST_TAGE) {
            throw new IllegalArgumentException("Die Verfallsfrist ist " + unbesuchtTage
                    + " Tage und liegt nicht zwischen 1 und " + LAENGSTE_FRIST_TAGE
                    + ". Eine 0 ist NICHT erlaubt: Sie liesse jede Stadt am Tag ihrer"
                    + " Gruendung verfallen. Wer den Verfall nicht will, setzt an auf"
                    + " false.");
        }
        if (warnungFruehTage < 0 || warnungFruehTage >= unbesuchtTage) {
            throw new IllegalArgumentException("Die erste Warnung soll "
                    + warnungFruehTage + " Tage vor dem Verfall gelten, und das liegt"
                    + " nicht zwischen 0 und " + (unbesuchtTage - 1) + ". Eine Warnung"
                    + " vor der Gruendung gibt es nicht; eine 0 heisst: keine erste"
                    + " Warnung.");
        }
        if (warnungSpaetTage < 0 || warnungSpaetTage > warnungFruehTage) {
            throw new IllegalArgumentException("Die zweite Warnung soll "
                    + warnungSpaetTage + " Tage vor dem Verfall gelten und damit"
                    + " FRUEHER als die erste (" + warnungFruehTage + "). Sie ist die"
                    + " dringendere und muss naeher am Verfall liegen - sonst liest ein"
                    + " Stadtbesitzer nie die erste.");
        }
    }

    /** Dieselben Werte mit anderem Schalter. */
    public Verfallswerte mitAn(boolean anderer) {
        return new Verfallswerte(anderer, unbesuchtTage, warnungFruehTage,
                warnungSpaetTage);
    }

    /** Dieselben Werte mit anderer Frist. */
    public Verfallswerte mitUnbesuchtTage(int andere) {
        return new Verfallswerte(an, andere, warnungFruehTage, warnungSpaetTage);
    }

    /** Dieselben Werte mit anderen Warnfristen - beide zusammen, weil sie es sind. */
    public Verfallswerte mitWarnungen(int frueh, int spaet) {
        return new Verfallswerte(an, unbesuchtTage, frueh, spaet);
    }
}
