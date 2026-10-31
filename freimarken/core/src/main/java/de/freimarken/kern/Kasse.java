package de.freimarken.kern;

import java.util.UUID;

/**
 * Das Guthaben der Spieler in Mark.
 *
 * <p>Absichtlich klein: Hier liegt nur die Waehrung des Alltags. Goldmark, Wechselkurs und
 * Tagesdeckel gehoeren zur Wirtschaft und kommen mit ihr - sie wuerden hier nur eine halbe
 * Umsetzung ergeben, die spaeter wieder auseinandergenommen werden muesste.
 *
 * <p>Zwei Versprechen, auf die sich die Regeln verlassen:
 * <ul>
 *   <li>{@link #abbuchen} bucht entweder vollstaendig ab oder gar nicht und sagt, was war.
 *       Ein halb abgebuchter Betrag darf es nicht geben.</li>
 *   <li>Ein Betrag von {@code 0} laesst sich immer abbuchen. Sonst muesste jede Regel, die
 *       etwas gratis vergibt, den Sonderfall selbst kennen - und eine von ihnen wuerde ihn
 *       vergessen.</li>
 * </ul>
 */
public interface Kasse {

    /** Was dieser Spieler hat. Fuer einen unbekannten Spieler das Startguthaben. */
    long guthaben(UUID spieler);

    /**
     * Abbuchen, wenn gedeckt.
     *
     * @return {@code true}, wenn abgebucht wurde; {@code false}, wenn das Guthaben nicht
     *         reicht - dann ist nichts passiert
     * @throws IllegalArgumentException bei negativem Betrag; das waere eine verkappte
     *         Gutschrift und soll auffallen, nicht durchrutschen
     */
    boolean abbuchen(UUID spieler, long betrag);

    /**
     * Gutschreiben.
     *
     * @throws IllegalArgumentException bei negativem Betrag
     */
    void gutschreiben(UUID spieler, long betrag);

    /**
     * Die Betragspruefung, die beide Buchungswege schulden.
     *
     * <p>Steht hier und nicht in den Umsetzungen: Sonst haette jede ihre eigene Fassung,
     * und eine davon wuerde die Pruefung irgendwann anders oder gar nicht machen.
     */
    static void pruefeBetrag(long betrag) {
        if (betrag < 0) {
            throw new IllegalArgumentException("Betrag darf nicht negativ sein: " + betrag);
        }
    }
}
