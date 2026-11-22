package de.freimarken.kern;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Die Annahmen im Arbeitsspeicher - fuer den Betrieb ohne Datenbank und fuer die Proben.
 *
 * <p><b>Vergisst beim Neustart, und das ist hier teurer als beim Rang.</b> Ohne
 * eingetragene Datenbank laeuft der Server fluechtig und sagt das laut; dann muss jeder
 * nach jedem Neustart erneut annehmen. Verloren ist nichts, was ein Spieler gekauft hat -
 * aber ein Nachweis, dass er den Regeln zugestimmt hat, ist weg. Wer die Annahme als
 * Nachweis braucht, braucht eine Datenbank, und genau das sagt die Startwarnung.
 *
 * <p><b>Keine Uhr.</b> Der Zeitpunkt kommt im {@link Anmeldeablage.Antrag} herein, so wie
 * es die vierte Zusage des Vertrags verlangt.
 *
 * <p><b>{@code synchronized} auf jeder Methode</b>, und nicht aus Vorsicht: Lesen und
 * Schreiben muessen EINE Tat sein, weil {@link #nimmAn} seine Antwort aus dem Vergleich
 * mit dem Vorherigen bildet. Liefen zwei Annahmen dazwischen, meldeten beide eine
 * Aenderung, und der Spieler las die Begruessung zweimal.
 */
public final class SpeicherAnmeldungen implements Anmeldeablage {

    /** Je Spieler seine Annahme - wer hier fehlt, hat nie angenommen. */
    private final Map<UUID, Anmeldestand> staende = new HashMap<>();

    @Override
    public synchronized Anmeldestand lies(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        return staende.getOrDefault(spieler, Anmeldestand.leer(spieler));
    }

    @Override
    public synchronized boolean nimmAn(Antrag antrag) {
        Anmeldeablage.pruefePflichten(antrag);
        Anmeldestand vorher = lies(antrag.spieler());
        if (vorher.hatAngenommen(antrag.fassung())) {
            // Genau diese Fassung stand schon da. Der Zeitpunkt bleibt der ERSTE: Eine
            // zweite Annahme derselben Regeln ist keine neue Zustimmung, und ihr Datum
            // zu ueberschreiben machte den Nachweis juenger, als er ist.
            return false;
        }
        staende.put(antrag.spieler(), new Anmeldestand(antrag.spieler(),
                antrag.fassung(), antrag.jetzt()));
        return true;
    }
}
