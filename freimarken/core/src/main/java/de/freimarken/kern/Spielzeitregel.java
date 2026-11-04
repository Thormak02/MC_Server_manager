package de.freimarken.kern;

import java.time.Duration;
import java.time.Instant;

/**
 * Was von einer verstrichenen Spanne als Spielzeit zaehlt - die ganze Rechnung als reine
 * Funktion.
 *
 * <p>Gezaehlt wird die Differenz zweier Ablesungen einer eingespeisten Wanduhr, nie
 * {@code System.nanoTime()}: nanoTime hat ueber einen Neustart hinweg keinen Bezugspunkt, und
 * eine Sitzung ueber einen Neustart waere damit Unsinn. Die Wanduhr laeuft dafuer nicht
 * monoton - gegen eine zurueckgestellte und gegen eine vorgestellte Uhr steht je eine Klemme
 * in dieser Regel.
 *
 * <p>ALLE Groessen stehen in der Signatur, keine im Rechenweg. Stuenden Taktlaenge oder
 * Untaetigkeitsgrenze als Konstante hier, waeren {@link Zeitwerte#PFAD_TAKT} und
 * {@link Zeitwerte#PFAD_UNTAETIG} in der Einstellungsdatei wirkungslos - zwei Zeilen, die ein
 * Betreiber aendert, ohne dass etwas geschieht.
 *
 * <p>Die Antwort ist ein {@link Urteil} und nicht eine Zahl. Eine Zahl kann nicht sagen, WARUM
 * sie kleiner ist als die verstrichene Zeit, und genau das braucht der Aufrufer: ein
 * verworfener Takt ist Alltag und darf nichts ins Protokoll schreiben, eine geklemmte Spanne
 * muss es. Das Urteil nennt ausserdem die Spanne, die zaehlt, und die neue Marke - beides
 * braucht der Aufrufer, und beides getrennt zu rechnen waere die Stelle, an der zwei
 * Rechnungen auseinanderlaufen.
 */
public final class Spielzeitregel {

    /** Warum ein Zuschlag so ausgefallen ist, wie er ausgefallen ist. */
    public enum Ausgang {

        /** Die verstrichene Zeit zaehlt, bis auf den Rest unter einer Sekunde. */
        GEZAEHLT,

        /** Der Spieler war zu lange untaetig - der Takt zaehlt nicht. */
        UNTAETIG,

        /** Die Uhr steht vor der Marke; es gibt keine verstrichene Zeit. */
        UHR_ZURUECKGESTELLT,

        /** Die Spanne war unplausibel lang und wurde auf das Erlaubte gekuerzt. */
        GEKLEMMT
    }

    /**
     * Das Ergebnis eines Taktes.
     *
     * <p>{@code von} und {@code bis} sind die Spanne, die ZAEHLT - sie ist bei einem
     * verworfenen Takt leer und bei einem geklemmten kuerzer als die verstrichene. Genau
     * diese Spanne gehoert in {@link Tagesgrenze#teile}, damit ein Takt ueber Mitternacht auf
     * beide Tage faellt.
     *
     * <p>{@code neueMarke} ist NICHT immer {@code bis}, und darin liegt die Entscheidung:
     * <ul>
     *   <li>Zaehlt der Takt, bleibt der Rest unter einer Sekunde an der Marke stehen
     *       ({@code neueMarke == bis}). Wuerde die Marke auf jetzt springen, fiele je Takt
     *       durchschnittlich eine halbe Sekunde weg - bei einem Takt von 60 s sind das ueber
     *       40 Stunden Spielzeit rund 20 Minuten, also ein Vielfaches dessen, was ein Absturz
     *       kostet. Ein stiller, immer gleich gerichteter Verlust ist schlimmer als ein
     *       lauter.</li>
     *   <li>Wird der Takt verworfen oder geklemmt, rueckt die Marke auf JETZT. Ein verworfener
     *       Takt wird nicht angespart und spaeter ausgezahlt - sonst sammelte ein abwesender
     *       Spieler seine Stunden und kassierte sie mit einem Schritt.</li>
     * </ul>
     */
    public record Urteil(Instant von, Instant bis, Instant neueMarke, Ausgang ausgang) {

        /** Die volle Sekundenzahl, die zaehlt - genau die Summe aus {@link Tagesgrenze#teile}. */
        public long sekunden() {
            return Duration.between(von, bis).getSeconds();
        }

        /**
         * Ob dieser Ausgang eine Protokollzeile verdient.
         *
         * <p>Untaetigkeit ist Alltag und wuerde das Protokoll zumuellen. Eine verstellte Uhr
         * ist es nicht: Sie laesst sich nicht verhindern, aber sie muss nachlesbar sein, denn
         * anders als beim Geld hat die Spielzeit keinen Zeugen - es gibt keine Summenprobe, an
         * der eine verlorene Minute auffaellt (Plan 8.7, Erkennung statt Verhinderung).
         */
        public boolean meldepflichtig() {
            return ausgang == Ausgang.GEKLEMMT || ausgang == Ausgang.UHR_ZURUECKGESTELLT;
        }
    }

    /**
     * Ab wie vielen Taktlaengen eine Spanne nicht mehr verstrichene Zeit sein kann.
     *
     * <p>Der Zeitgeber laeuft im Taktabstand. Sind zwischen zwei Ablesungen mehr als zehn
     * Taktlaengen vergangen, hat nicht der Spieler so lange gespielt - dann wurde die Uhr
     * vorgestellt oder der Server stand. Zehn und nicht zwei: Ein lahmer Server soll nichts
     * verlieren, und ein Taktausfall von einigen Minuten kommt vor. Der Ueberschuss wird
     * weggeworfen und gemeldet, nicht stillschweigend gutgeschrieben - zu viel Spielzeit gibt
     * einen Rang zu frueh, zu wenig nur zu spaet.
     */
    public static final int KLEMME_IN_TAKTEN = 10;

    private Spielzeitregel() {
    }

    /**
     * Rechnet einen Takt ab.
     *
     * @param marke        bis wohin die Spielzeit dieses Spielers schon fortgeschrieben ist
     * @param jetzt        was die Wanduhr sagt
     * @param untaetig     wie lange der Spieler schon untaetig ist; eine negative Dauer ist ein
     *                     Messfehler und wird als "aktiv" gelesen - ein Fehler der Messung darf
     *                     den Spieler nicht seine Minute kosten
     * @param untaetigNach ab welcher Untaetigkeit nicht mehr gezaehlt wird;
     *                     {@link Duration#ZERO} heisst: Abwesenheit zaehlt mit
     * @param takt         der Taktabstand - nur Grundlage der Klemme, nicht der Rechnung
     * @throws IllegalArgumentException wenn etwas fehlt, der Takt nicht positiv ist oder die
     *         Untaetigkeitsgrenze negativ ist
     */
    public static Urteil pruefe(Instant marke, Instant jetzt, Duration untaetig,
                                Duration untaetigNach, Duration takt) {
        if (marke == null || jetzt == null) {
            throw new IllegalArgumentException("Marke und Jetzt sind Pflicht");
        }
        if (untaetig == null || untaetigNach == null || takt == null) {
            throw new IllegalArgumentException("Untaetigkeit, Grenze und Takt sind Pflicht");
        }
        if (takt.isZero() || takt.isNegative()) {
            throw new IllegalArgumentException("Ein Takt ohne Laenge ergibt keine Klemme: "
                    + takt);
        }
        if (untaetigNach.isNegative()) {
            throw new IllegalArgumentException("Eine Untaetigkeitsgrenze unter null hiesse"
                    + " nichts zaehlt; dafuer gibt es keinen Wert: " + untaetigNach);
        }

        // Die Uhr zuerst: Eine zurueckgestellte Uhr soll auch dann gemeldet werden, wenn der
        // Spieler gerade untaetig ist - sonst verdeckt der Alltagsfall den Fehlerfall.
        if (jetzt.isBefore(marke)) {
            return new Urteil(jetzt, jetzt, jetzt, Ausgang.UHR_ZURUECKGESTELLT);
        }
        Duration gemessen = untaetig.isNegative() ? Duration.ZERO : untaetig;
        // Die Grenze ist eine STUFE und kein Schnitt: Reisst sie, faellt der GANZE Takt und
        // nicht nur der untaetige Teil davon. Das ist nur dann zugunsten des Spielers, wenn
        // die Grenze MINDESTENS eine Taktlaenge ist - sonst wirft dieser Zweig aktiv
        // gespielte Zeit weg (600 s Takt, 1 min Grenze: 540 Sekunden, stumm, denn
        // meldepflichtig() ist hier false). Dass dieses Paar nicht entstehen kann, ist nicht
        // hier geregelt, sondern an EINER Stelle im Kompaktkonstruktor von Zeitwerte und in
        // Zeitwerte.brauchbareGrenzeZumTakt - diese Regel bekommt die beiden Dauern einzeln
        // und kann sie nicht gegeneinander pruefen, ohne eine zweite Regel zu werden.
        //
        // Nur den untaetigen TEIL abzuziehen waere keine Abhilfe: getIdleDuration() liefert
        // allein den augenblicklichen Stand der Untaetigkeit, nicht ihren Verlauf innerhalb
        // des Takts. Der Abzug gaebe eine Genauigkeit vor, die die Messgroesse nicht hat.
        if (!untaetigNach.isZero() && gemessen.compareTo(untaetigNach) >= 0) {
            return new Urteil(jetzt, jetzt, jetzt, Ausgang.UNTAETIG);
        }

        Duration verstrichen = Duration.between(marke, jetzt);
        Duration hoechstens = takt.multipliedBy(KLEMME_IN_TAKTEN);
        if (verstrichen.compareTo(hoechstens) > 0) {
            return new Urteil(marke, marke.plus(hoechstens), jetzt, Ausgang.GEKLEMMT);
        }
        // Volle Sekunden, und die Marke genau dorthin: Der Rest zaehlt beim naechsten Takt mit.
        Instant bis = marke.plusSeconds(verstrichen.getSeconds());
        return new Urteil(marke, bis, bis, Ausgang.GEZAEHLT);
    }
}
