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
 * @param spieler        wessen Zeit
 * @param tag            der Tag, auf den sich {@code sekundenHeute} bezieht
 * @param sekundenGesamt alle gemessenen Sekunden, ueber alle Tage
 * @param sekundenHeute  die Sekunden des genannten Tages
 */
public record Spielzeitstand(UUID spieler, LocalDate tag, long sekundenGesamt,
                             long sekundenHeute) {

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
        if (sekundenHeute > sekundenGesamt) {
            throw new IllegalArgumentException("Heute kann nicht mehr sein als insgesamt: "
                    + sekundenHeute + " > " + sekundenGesamt);
        }
    }

    /** Ein Spieler, von dem noch nichts gemessen wurde - jeder faengt bei 0 an. */
    public static Spielzeitstand leer(UUID spieler, LocalDate tag) {
        return new Spielzeitstand(spieler, tag, 0L, 0L);
    }

    /** Die Gesamtzeit als {@link Duration} - fuer jede Anzeige, die Stunden rechnet. */
    public Duration gesamt() {
        return Duration.ofSeconds(sekundenGesamt);
    }

    /** Die Zeit des genannten Tages als {@link Duration}. */
    public Duration heute() {
        return Duration.ofSeconds(sekundenHeute);
    }
}
