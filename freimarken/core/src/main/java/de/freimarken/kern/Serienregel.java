package de.freimarken.kern;

import java.time.LocalDate;

/**
 * Die EINE Stelle, die entscheidet, wie lang die Serie eines Spielers ist.
 *
 * <p>Die Serie ist der Teil des Tagesbonus, der REGELMAESSIGES Spielen belohnt und nicht
 * nur das Einloggen: Wer jeden Tag kommt, bekommt mit jedem Tag mehr. Was daraus an Mark
 * wird, rechnet {@link Bonusregel}; hier wird nur gezaehlt.
 *
 * <p><b>Gerechnet wird mit der LUECKE und nicht mit einer Kette von Tagen.</b> Gespeichert
 * ist je Spieler ein Datum und eine Zahl (Plan 8.8: "gespeichert wird je Spieler ein Datum
 * und kein Zeitpunkt"), nicht eine Liste seiner Tage. Die Luecke ist der Abstand zwischen
 * dem letzten bezahlten Tag und heute - und mit ihr ist die ganze Entscheidung zu treffen,
 * ohne die Vergangenheit zu lesen.
 *
 * <p><b>Ein Tag in der Vergangenheit haelt die Serie an, statt sie zu verlaengern.</b> Das
 * ist nicht konstruiert: Die Tagesgrenze ist eine eingestellte ZONE, und wer sie von
 * {@code Europe/Berlin} auf {@code Pacific/Auckland} stellt, verschiebt den Tageswechsel um
 * einen halben Tag - der naechste Spieler kann dann "gestern" erleben. Dieselbe Richtung
 * wie bei der zurueckgestellten Uhr in {@link Spielzeitregel}: Es wird nichts bezahlt, und
 * verloren ist nur ein Tag.
 */
public final class Serienregel {

    /** Was aus dem letzten bezahlten Tag und heute folgt. */
    public enum Ausgang {

        /**
         * Heute ist schon bezahlt - oder der gespeicherte Tag liegt in der Zukunft.
         *
         * <p>Beides fuehrt zu derselben Antwort, und beides mit Absicht: Eine Zahlung in
         * dieser Lage waere entweder eine zweite am selben Tag oder eine, die aus einer
         * verstellten Zone kommt. Wer unterscheiden will, liest das Datum selbst.
         */
        SCHON_BEZAHLT,

        /** Die Luecke ist klein genug - die Serie waechst um einen Tag. */
        FORTGESETZT,

        /** Die Luecke war zu gross oder es ist der erste Tag - die Serie beginnt bei 1. */
        NEU_BEGONNEN
    }

    /**
     * Der Ausgang samt der Serie, die danach gilt.
     *
     * @param ausgang was geschehen ist
     * @param serie   die Serie NACH diesem Tag; bei {@link Ausgang#SCHON_BEZAHLT} die
     *                unveraenderte alte Zahl, damit eine Anzeige sie zeigen kann, ohne zu
     *                wissen, welcher Ausgang es war
     */
    public record Urteil(Ausgang ausgang, int serie) {

        public Urteil {
            if (ausgang == null) {
                throw new IllegalArgumentException("Ohne Ausgang gibt es kein Urteil");
            }
            if (serie < 0) {
                throw new IllegalArgumentException("Eine Serie kann nicht negativ sein: "
                        + serie);
            }
        }

        /** Ob heute etwas zu zahlen ist. */
        public boolean zahlbar() {
            return ausgang != Ausgang.SCHON_BEZAHLT;
        }
    }

    private Serienregel() {
    }

    /**
     * Wie lang die Serie heute ist.
     *
     * @param letzterTag     der letzte Tag, an dem ein Bonus gezahlt wurde, oder
     *                       {@code null} fuer einen Spieler, der noch keinen hatte
     * @param heute          der heutige Tag in der eingestellten Zone - von aussen, wie
     *                       ueberall: {@link Tagesgrenze} entscheidet, was heute ist
     * @param alteSerie      die gespeicherte Serie; ohne {@code letzterTag} ohne Bedeutung
     * @param reisstAbLuecke ab welcher Luecke in Tagen die Serie reisst - 2 heisst "wer
     *                       einen Tag verpasst, faengt neu an"
     */
    public static Urteil urteile(LocalDate letzterTag, LocalDate heute, int alteSerie,
                                 int reisstAbLuecke) {
        if (heute == null) {
            throw new IllegalArgumentException("Ohne heute gibt es kein Urteil");
        }
        if (!brauchbareLuecke(reisstAbLuecke)) {
            throw new IllegalArgumentException("Die Serie kann nicht ab einer Luecke von "
                    + reisstAbLuecke + " reissen - erlaubt ist 2 bis "
                    + HOECHSTE_LUECKE + ". Bei 1 riss sie auch fuer den, der gestern da"
                    + " war, also fuer jeden.");
        }
        if (letzterTag == null) {
            // Der erste Bonus ueberhaupt. Die alte Serie wird NICHT gelesen: Ohne einen
            // letzten Tag gehoert sie zu nichts, und eine stehengebliebene Zahl aus einer
            // frueheren Fassung waere ein geschenkter Vorlauf.
            return new Urteil(Ausgang.NEU_BEGONNEN, 1);
        }
        long luecke = heute.toEpochDay() - letzterTag.toEpochDay();
        if (luecke <= 0L) {
            return new Urteil(Ausgang.SCHON_BEZAHLT, Math.max(alteSerie, 0));
        }
        if (luecke >= reisstAbLuecke) {
            return new Urteil(Ausgang.NEU_BEGONNEN, 1);
        }
        // Eine Serie von 0 bei vorhandenem letzten Tag kann nur aus einer kaputten Zeile
        // kommen. Dann ist heute der erste Tag und nicht der nullte.
        return new Urteil(Ausgang.FORTGESETZT, Math.max(alteSerie, 0) + 1);
    }

    /**
     * Die groesste Luecke, die noch eingestellt werden darf - ein Jahr.
     *
     * <p>Darueber waere die Serie keine mehr: Wer einmal im Jahr kommt, haette eine
     * ununterbrochene Serie, und die Zahl daneben verspricht Regelmaessigkeit.
     */
    public static final int HOECHSTE_LUECKE = 366;

    /**
     * Ob aus dieser Zahl eine Luecke werden kann.
     *
     * <p>Die 1 ist ausgeschlossen, und das ist die eine Schranke, die wirklich etwas
     * faengt: Bei 1 ist {@code luecke >= 1} fuer JEDEN wahr, der nicht heute schon bezahlt
     * wurde - auch fuer den, der gestern da war. Die Serie stuende dann immer auf 1, und
     * der Betreiber suchte den Fehler in der Belohnung statt in der Zahl.
     */
    public static boolean brauchbareLuecke(int tage) {
        return tage >= 2 && tage <= HOECHSTE_LUECKE;
    }
}
