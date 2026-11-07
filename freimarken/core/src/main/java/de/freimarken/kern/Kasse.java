package de.freimarken.kern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Das Guthaben der Spieler in Mark.
 *
 * <p>Absichtlich klein: Hier liegt nur die Waehrung des Alltags. Goldmark, Wechselkurs und
 * Tagesdeckel gehoeren zur Wirtschaft und kommen mit ihr - sie wuerden hier nur eine halbe
 * Umsetzung ergeben, die spaeter wieder auseinandergenommen werden muesste.
 *
 * <p>Fuenf Versprechen, auf die sich die Regeln verlassen:
 * <ul>
 *   <li>{@link #abbuchen} bucht entweder vollstaendig ab oder gar nicht und sagt, was war.
 *       Ein halb abgebuchter Betrag darf es nicht geben.</li>
 *   <li>Ein Betrag von {@code 0} laesst sich immer abbuchen. Sonst muesste jede Regel, die
 *       etwas gratis vergibt, den Sonderfall selbst kennen - und eine von ihnen wuerde ihn
 *       vergessen.</li>
 *   <li><b>Keine Geldbewegung ohne Zeile im Buch.</b></li>
 *   <li><b>Keine Zeile im Buch ohne Geldbewegung.</b> Ein abgelehntes Abbuchen ist keine
 *       Bewegung und hinterlaesst nichts.</li>
 *   <li><b>Fuer ein eroeffnetes Konto ist die Summe aller Buchungen sein Stand.</b></li>
 * </ul>
 *
 * <p>Das letzte Versprechen ist das, was die beiden davor zusammenhaelt: Eine Buchung ohne
 * Zeile und eine Zeile ohne Buchung sind beide eine Luege, und beide fallen an dieser
 * Summe auf - ohne dass die Probe jede einzelne Buchungsart kennen muss. Deshalb steht sie
 * im {@code KasseVertrag} und gilt fuer jede Umsetzung.
 *
 * <p>Der {@link Buchungsgrund} ist Pflicht, ohne Ueberladung und ohne Vorgabewert. Ein
 * Vorgabe-Grund waere genau die Luecke, die man sucht, wenn im Buch eines Tages
 * „unbekannt" steht: So zwingt der Uebersetzer jeden kuenftigen Buchungsweg zur Angabe.
 */
public interface Kasse {

    /** Was dieser Spieler hat. Fuer ein nicht eroeffnetes Konto das Startguthaben. */
    long guthaben(UUID spieler);

    /**
     * Eroeffnet das Konto mit dem Startguthaben - einmalig.
     *
     * <p>Der Grund, warum es diese Methode gibt: Ohne sie entstuende die Kassenzeile erst
     * bei der ersten Buchung, und bis dahin kaeme das Guthaben bei <em>jedem Blick</em>
     * neu aus der Einstellungsdatei. Wer den Wert dort aendert, aenderte damit das
     * Guthaben jedes Spielers, der noch nichts gekauft hat - rueckwirkend und unbemerkt.
     * Mit der Eroeffnungsbuchung friert der Betrag im Augenblick der Eroeffnung ein.
     *
     * @return {@code true}, wenn das Konto jetzt eroeffnet wurde; {@code false}, wenn es
     *         schon offen war - dann ist nichts passiert
     */
    boolean eroeffne(UUID spieler);

    /**
     * Abbuchen, wenn gedeckt.
     *
     * @param betrag ohne Vorzeichen; das Vorzeichen im Buch kommt aus dem Grund
     * @return {@code true}, wenn abgebucht wurde; {@code false}, wenn das Guthaben nicht
     *         reicht - dann ist nichts passiert, auch keine Zeile im Buch
     * @throws IllegalArgumentException bei negativem Betrag; das waere eine verkappte
     *         Gutschrift und soll auffallen, nicht durchrutschen
     */
    boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund);

    /**
     * Gutschreiben.
     *
     * @param betrag ohne Vorzeichen
     * @throws IllegalArgumentException bei negativem Betrag
     */
    void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund);

    /**
     * Die letzten Buchungen dieses Spielers, die neueste zuerst.
     *
     * @param hoechstens wie viele Zeilen hoechstens - Pflicht, denn ein unbegrenzter
     *                   Verlauf ist auf einem Server, der Monate laeuft, eine Abfrage, die
     *                   niemand gewollt hat
     */
    List<Buchung> verlauf(UUID spieler, int hoechstens);

    /**
     * Was dieser Spieler seit diesem Zeitpunkt aus EINEM Grund bekommen hat.
     *
     * <p><b>Der Grund kommt von aussen</b>, und das ist die Lehre aus dem zweiten
     * Tagesdeckel: Teil E deckelt die ARBEIT ({@link Buchungsgrund#BERUF_VERDIENST}),
     * Teil F den Verkauf an den Bundesmarkt - und beide stellen dieselbe Frage. Eine
     * zweite Methode daneben waere eine zweite Abschrift derselben Abfrage, und die
     * laeuft mit der ersten auseinander, lautlos.
     *
     * <p>GENAU ein Grund und nicht alle Einnahmen: Ein Deckel auf die Arbeit darf nicht
     * durch ein Geschenk, eine Erstattung oder das Startguthaben gefuellt werden.
     *
     * <p><b>Warum das Buchungsbuch der Zeuge ist und keine eigene Tabelle:</b> Die Summe
     * steht dort schon. Eine zweite Tabelle daneben waere eine zweite Wahrheit, die nach
     * dem ersten Absturz von der ersten abweicht - und dann deckelt der Server einen
     * Spieler, dessen Konto etwas anderes sagt. Gefragt wird einmal je Sitzung, nicht je
     * Block; die Antwort haelt der Sammler im Arbeitsspeicher.
     *
     * <p>Gezaehlt werden nur GEBUCHTE Mark. Was als Pfennigrest beim Sammler liegt, ist
     * noch nicht gebucht und zaehlt darum nicht mit - das ist weniger als eine Mark je
     * Spieler, und der Deckel ist auf Tausende gestellt.
     *
     * @param ab    der Zeitpunkt, ab dem gezaehlt wird - einschliesslich
     * @param grund welche Buchungen zaehlen - genau dieser, kein anderer
     */
    long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund);

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

    /** Dieselbe Begruendung wie bei {@link #pruefeBetrag}, fuer die Laenge des Verlaufs. */
    static void pruefeAnzahl(int hoechstens) {
        if (hoechstens < 1) {
            throw new IllegalArgumentException("Es muss mindestens eine Zeile sein: "
                    + hoechstens);
        }
    }
}
