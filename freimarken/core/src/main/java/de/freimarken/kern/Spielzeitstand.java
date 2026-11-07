package de.freimarken.kern;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Was von einem Spieler gemessen wurde - was eine Spielzeitanzeige zeigt.
 *
 * <p>KEIN Rang darin. Raenge sind heute allein Rechte, und das Plugin hat keinen Weg, ein
 * Recht zu vergeben; eine Rangstufe in diesem Wert waere eine zweite Quelle fuer etwas, das
 * aus den Rechten kommt. Dieser Schritt liefert die gemessene Zeit und sonst nichts.
 *
 * <p>Der TAG steht mit drin, obwohl er von aussen kam: Sonst nennt die Anzeige "heute" und
 * niemand kann nachpruefen, welcher Tag damit gemeint war - bei einer Tagesgrenze, die nicht
 * UTC ist, ist das die Frage, um die es geht.
 *
 * <p><b>Zwei Zahlenpaare und nicht eines</b> [Teil G]: die Anwesenheit und die davon
 * TAETIGE Zeit. Der Grund steht in Plan 8.8 - die Anwesenheit allein ist "im Stehen
 * erreichbar", und wer eine Rangschwelle daran haengt, haengt sie an ein Makro. Beide
 * Paare kommen aus derselben Zeile derselben Tabelle und darum aus demselben Augenblick;
 * kaeme die taetige Zeit aus einer zweiten Lesung, koennte sie die Zusage
 * {@code taetig <= gemessen} brechen, ohne dass etwas kaputt ist.
 *
 * @param spieler              wessen Zeit
 * @param tag                  der Tag, auf den sich die beiden Tageszahlen beziehen
 * @param sekundenGesamt       alle gemessenen Sekunden, ueber alle Tage
 * @param sekundenHeute        die Sekunden des genannten Tages
 * @param taetigSekundenGesamt wie viele der gemessenen Sekunden taetig waren, ueber alle
 *                             Tage - was taetig heisst, entscheidet {@link Messregel}
 * @param taetigSekundenHeute  wie viele der heutigen Sekunden taetig waren
 */
public record Spielzeitstand(UUID spieler, LocalDate tag, long sekundenGesamt,
                             long sekundenHeute, long taetigSekundenGesamt,
                             long taetigSekundenHeute) {

    public Spielzeitstand {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keinen Stand");
        }
        if (tag == null) {
            throw new IllegalArgumentException("Ohne Tag ist 'heute' nicht nachpruefbar");
        }
        if (sekundenGesamt < 0 || sekundenHeute < 0) {
            throw new IllegalArgumentException("Gemessene Zeit kann nicht negativ sein: "
                    + sekundenGesamt + " / " + sekundenHeute);
        }
        if (taetigSekundenGesamt < 0 || taetigSekundenHeute < 0) {
            throw new IllegalArgumentException("Taetige Zeit kann nicht negativ sein: "
                    + taetigSekundenGesamt + " / " + taetigSekundenHeute);
        }
        if (sekundenHeute > sekundenGesamt) {
            throw new IllegalArgumentException("Heute kann nicht mehr sein als insgesamt: "
                    + sekundenHeute + " > " + sekundenGesamt);
        }
        // Drei Pruefungen und nicht eine: Jede faengt einen anderen Fehler. Die erste einen
        // ueber alle Tage, die zweite einen am heutigen Tag - und die dritte einen, bei dem
        // beide Paare fuer sich stimmen und doch heute mehr taetige Zeit steht als
        // insgesamt, also eine verlorene Zeile aus der Vergangenheit.
        if (taetigSekundenGesamt > sekundenGesamt) {
            throw new IllegalArgumentException("Taetig kann nicht mehr sein als gemessen: "
                    + taetigSekundenGesamt + " > " + sekundenGesamt);
        }
        if (taetigSekundenHeute > sekundenHeute) {
            throw new IllegalArgumentException("Taetig heute kann nicht mehr sein als heute"
                    + " gemessen: " + taetigSekundenHeute + " > " + sekundenHeute);
        }
        if (taetigSekundenHeute > taetigSekundenGesamt) {
            throw new IllegalArgumentException("Taetig heute kann nicht mehr sein als taetig"
                    + " insgesamt: " + taetigSekundenHeute + " > " + taetigSekundenGesamt);
        }
    }

    /** Ein Spieler, von dem noch nichts gemessen wurde - jeder faengt bei 0 an. */
    public static Spielzeitstand leer(UUID spieler, LocalDate tag) {
        return new Spielzeitstand(spieler, tag, 0L, 0L, 0L, 0L);
    }

    /** Die Gesamtzeit als {@link Duration} - fuer jede Anzeige, die Stunden rechnet. */
    public Duration gesamt() {
        return Duration.ofSeconds(sekundenGesamt);
    }

    /** Die Zeit des genannten Tages als {@link Duration}. */
    public Duration heute() {
        return Duration.ofSeconds(sekundenHeute);
    }

    /** Die taetige Zeit ueber alle Tage als {@link Duration}. */
    public Duration taetigGesamt() {
        return Duration.ofSeconds(taetigSekundenGesamt);
    }

    /** Die taetige Zeit des genannten Tages als {@link Duration}. */
    public Duration taetigHeute() {
        return Duration.ofSeconds(taetigSekundenHeute);
    }

    /**
     * Wie viel Prozent der gemessenen Zeit taetig war - 0 bis 100, abgerundet.
     *
     * <p>Hier und nicht in der Anzeige, damit es nur EINE Rundung gibt: Der Bericht an den
     * Betreiber und die Zeile fuer den Spieler sollen nicht verschiedene Zahlen fuer
     * denselben Stand nennen. Abgerundet, weil die Zahl eine Untergrenze sein soll - 99 fuer
     * 99,8 Prozent ist richtig, eine 100 dafuer waere eine Zusage, die der Stand nicht gibt.
     *
     * <p>Ohne gemessene Zeit ist das Ergebnis 0 und keine Ausnahme: Ein Spieler, von dem
     * nichts gemessen wurde, steht in jeder Liste, und eine Liste darf an ihm nicht
     * abbrechen.
     */
    public int taetigAnteilProzent() {
        if (sekundenGesamt <= 0L) {
            return 0;
        }
        return (int) (taetigSekundenGesamt * 100L / sekundenGesamt);
    }
}
