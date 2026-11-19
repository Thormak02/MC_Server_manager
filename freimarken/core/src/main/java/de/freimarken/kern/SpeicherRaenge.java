package de.freimarken.kern;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Die Raenge im Arbeitsspeicher - fuer den Betrieb ohne Datenbank und fuer die Proben.
 *
 * <p><b>Vergisst beim Neustart, und das ist der Zweck.</b> Ohne eingetragene Datenbank
 * laeuft der Server fluechtig und sagt das laut; diese Fassung ist dann die Ablage. Und
 * sie ist die zweite Umsetzung desselben Vertrags - damit {@link RangVertrag} beide
 * prueft und keine Zusage nur fuer eine gilt.
 *
 * <p>Dass ein Rang hier beim Neustart verfaellt, ist leichter zu verkraften als ein
 * vergessenes Abholfach: Verloren ist nichts, was ein Spieler gekauft hat - er steigt
 * wieder auf, sobald der Takt seine Bedingungen misst, denn die liegen in der Spielzeit
 * und in seinem Besitz und nicht hier.
 *
 * <p><b>Keine Uhr.</b> Der Zeitpunkt kommt im {@link Rangablage.Antrag} herein, so wie es
 * die fuenfte Zusage des Vertrags verlangt.
 *
 * <p><b>{@code synchronized} auf jeder Methode</b>, und nicht aus Vorsicht: Der Vergleich
 * mit dem erwarteten Stand und das Schreiben muessen EINE Tat sein. Liefen der Tageslauf
 * und ein Befehl dazwischen, bestaetigten beide denselben Stand und beide schrieben.
 * Dasselbe Zusammenspiel wie bei {@link SpeicherBerufe}.
 */
public final class SpeicherRaenge implements Rangablage {

    /** Je Spieler seine Stufe - wer hier fehlt, ist nie aufgestiegen. */
    private final Map<UUID, Rangstand> staende = new HashMap<>();

    @Override
    public synchronized Rangstand lies(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        return staende.getOrDefault(spieler, Rangstand.leer(spieler));
    }

    @Override
    public synchronized boolean setze(Antrag antrag, Rangstand erwartet) {
        Rangablage.pruefePflichten(antrag, erwartet);
        if (!lies(antrag.spieler()).equals(erwartet)) {
            return false;
        }
        staende.put(antrag.spieler(),
                new Rangstand(antrag.spieler(), antrag.rang(), antrag.jetzt()));
        return true;
    }
}
