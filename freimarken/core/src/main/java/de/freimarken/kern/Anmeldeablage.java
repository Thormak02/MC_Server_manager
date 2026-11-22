package de.freimarken.kern;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Wo die Annahme der Regeln liegt - der Vertrag, den beide Umsetzungen halten.
 *
 * <p>Plan 10.1: "Regeln und Nutzungsbedingungen werden als Formular angenommen, nicht als
 * Textwand weggeklickt. Erst danach oeffnet sich der Bundesmarkt. Die Anmeldung im
 * Buergeramt ist gleichzeitig die Aufnahme in den Bund." Dieser Vertrag ist das
 * Gedaechtnis dazu.
 *
 * <p>Vier Zusagen, und jede steht hier, weil ihr Gegenteil einen Fehler ergaebe:
 *
 * <ol>
 *   <li><b>Die Ablage weiss nicht, welche Fassung GILT.</b> Sie legt ab, was angenommen
 *       wurde; was heute verlangt ist, steht in der Einstellungsdatei. Haette die Ablage
 *       eine eigene Vorstellung davon, gaebe es die geltende Fassung zweimal, und nach
 *       einer Aenderung in der Datei waere die in der Datenbank die falsche. Dieselbe
 *       Begruendung, aus der {@link Rangablage} die Leiter nicht kennt.</li>
 *   <li><b>KEINE Zeile heisst: hat nicht angenommen.</b> Geschrieben wird erst bei der
 *       Annahme. Eine Zeile je Besucher waere eine Tabelle, die nur Platz braucht - und
 *       der Rueckfall steht an EINER Stelle, in {@link Anmeldestand#leer}.</li>
 *   <li><b>Die Annahme ist FORTSCHREIBEND und nicht einmalig.</b> Nimmt jemand eine neue
 *       Fassung an, tritt sie an die Stelle der alten; es gibt eine Zeile je Spieler und
 *       keinen Verlauf. Wer den Verlauf der Zustimmungen braucht - und bei
 *       Nutzungsbedingungen kann das sein -, bekommt eine zweite Tabelle wie das
 *       Blockbuch, nicht eine zweite Bedeutung fuer diese.</li>
 *   <li><b>Die Ablage entscheidet nie, was "jetzt" ist.</b> Der Zeitpunkt kommt im
 *       {@link Antrag} von aussen, wie bei {@link Rangablage} und
 *       {@link Spielzeitablage}. Eine Ablage mit eigener Uhr liesse sich nicht pruefen,
 *       ohne zu warten.</li>
 * </ol>
 *
 * <p><b>Kein erwarteter Stand, anders als beim Rang.</b> Dort schuetzt der Vergleich
 * gegen zwei gleichzeitige Aufstiege, die verschiedene Stufen schreiben wollen. Hier gibt
 * es diesen Streit nicht: Zwei gleichzeitige Annahmen DERSELBEN Fassung sind dieselbe
 * Tat, und zwei verschiedene Fassungen gleichzeitig kann ein Spieler nicht annehmen - es
 * gilt immer die aus der Datei. Ein erwarteter Stand waere hier ein Riegel ohne Fall.
 */
public interface Anmeldeablage {

    /**
     * Eine Annahme - geprueft beim Bauen, nicht beim Schreiben.
     *
     * @param spieler wer
     * @param fassung welche Fassung er annimmt, kleingeschrieben wie in der Datei
     * @param jetzt   der Zeitpunkt, den die Ablage eintragen soll
     */
    record Antrag(UUID spieler, String fassung, Instant jetzt) {

        public Antrag {
            if (spieler == null || jetzt == null) {
                throw new IllegalArgumentException("Spieler und Zeitpunkt sind Pflicht;"
                        + " die Ablage hat keine eigene Uhr");
            }
            if (fassung == null || fassung.isBlank()) {
                // Eine Annahme ohne Fassung waere eine Zustimmung zu nichts. Wer die
                // Zeile wegnehmen will, hat eine Verwaltungsaufgabe und keine Annahme -
                // die geht nicht durch diesen Weg.
                throw new IllegalArgumentException("Ein Antrag ohne Fassung. Eine"
                        + " Zustimmung muss sagen, WOZU sie gilt.");
            }
            // Kleingeschrieben hier, damit die Ablage nicht ein zweites Mal
            // normalisiert. Zwei Stellen, die denselben Namen zurechtbiegen, biegen ihn
            // eines Tages verschieden.
            fassung = fassung.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Was dieser Spieler angenommen hat.
     *
     * <p>Fuer einen Spieler, von dem die Ablage nichts weiss, {@link Anmeldestand#leer} -
     * kein {@code null} und kein Wurf. Wer nie angenommen hat, ist der Normalfall.
     */
    Anmeldestand lies(UUID spieler);

    /**
     * Traegt die Annahme ein - alles oder nichts.
     *
     * <p>Schreibt auch dann, wenn schon eine aeltere Fassung dasteht: Dann tritt die neue
     * an ihre Stelle.
     *
     * @return {@code true}, wenn sich dadurch etwas geaendert hat; {@code false}, wenn
     *         genau diese Fassung schon angenommen war - dann ist nichts passiert. Der
     *         Aufrufer braucht die Antwort, um nicht zweimal zu melden.
     */
    boolean nimmAn(Antrag antrag);

    /**
     * Die eine Pruefung, die beide Umsetzungen von {@link #nimmAn} machen.
     *
     * <p>Hier und nicht zweimal: Zwei Fassungen derselben Pruefung weichen eines Tages
     * ab, und dann verhaelt sich der fluechtige Betrieb anders als der echte - gefunden
     * wird das nicht im Testlauf, sondern von einem Spieler.
     */
    static void pruefePflichten(Antrag antrag) {
        if (antrag == null) {
            throw new IllegalArgumentException("Ohne Antrag gibt es nichts zu schreiben");
        }
        if (antrag.fassung().length() > Anmeldestand.HOECHSTE_FASSUNGSLAENGE) {
            // Hier und nicht erst in der Datenbank: Die Speicherfassung kennt keine
            // Spaltenbreite, und ohne diese Zeile nahm sie an, was MariaDB abweist -
            // der fluechtige Betrieb verhielte sich anders als der echte.
            throw new IllegalArgumentException("Die Fassung \"" + antrag.fassung()
                    + "\" ist laenger als " + Anmeldestand.HOECHSTE_FASSUNGSLAENGE
                    + " Zeichen - so lang ist die Spalte in der Ablage.");
        }
        if (!antrag.fassung().matches(Anmeldestand.MUSTER)) {
            throw new IllegalArgumentException("Die Fassung \"" + antrag.fassung()
                    + "\" taugt nicht: erlaubt sind Kleinbuchstaben, Ziffern, Punkt,"
                    + " Unterstrich und Bindestrich.");
        }
    }
}
