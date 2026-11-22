package de.freimarken.kern;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Der Rundgangsfortschritt im Arbeitsspeicher - ohne Datenbank und fuer die Proben.
 *
 * <p><b>Vergisst beim Neustart</b>, und das ist hier leichter zu verkraften als bei der
 * Anmeldung: Verloren ist kein Nachweis, sondern ein Weg, den man noch einmal gehen kann.
 * Wer den Rundgang als Rangbedingung gesetzt hat, sollte es trotzdem wissen - ohne
 * Datenbank steigt dann niemand dauerhaft auf, und die Startwarnung sagt es.
 *
 * <p><b>Keine Uhr.</b> Der Zeitpunkt kommt herein, so wie es die vierte Zusage des
 * Vertrags verlangt.
 *
 * <p><b>{@code synchronized} auf jeder Methode</b>, und nicht aus Vorsicht: {@link #merke}
 * bildet seine Antwort aus dem Vergleich mit dem Vorherigen. Liefen zwei Schritte in
 * dieselbe Station dazwischen, meldeten beide eine neue Station, und der Spieler lase
 * denselben Stationstext zweimal.
 *
 * <p><b>Der ZEITPUNKT wird hier nicht abgelegt</b>, obwohl der Vertrag ihn verlangt: Die
 * fluechtige Fassung beantwortet keine Frage, fuer die er gebraucht wird (es gibt keinen
 * Leser von "wann war er dort"). In der Datenbank steht er, weil eine Zeile ohne
 * Zeitpunkt dort nichts kostet und ein Betreiber eines Tages nachsehen will. Dass der
 * Vertrag ihn fordert, ist richtig: Er ist der Riegel gegen eine Umsetzung, die sich
 * eine eigene Uhr nimmt.
 */
public final class SpeicherRundgang implements Rundgangsablage {

    /** Je Spieler die Namen seiner gesehenen Stationen - wer fehlt, war nirgends. */
    private final Map<UUID, Set<String>> gesehen = new HashMap<>();

    @Override
    public synchronized Set<String> gesehen(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        // EINE Kopie heraus: Wer die Menge veraenderte, aenderte den Stand, ohne durch
        // merke zu gehen - und damit ohne die Antwort "war es neu".
        return Set.copyOf(gesehen.getOrDefault(spieler, Set.of()));
    }

    @Override
    public synchronized boolean merke(UUID spieler, String station, Instant jetzt) {
        String name = Rundgangsablage.pruefeUndNormalisiere(spieler, station, jetzt);
        // LinkedHashSet und nicht HashSet: Die Reihenfolge, in der ein Spieler die
        // Stationen gesehen hat, kostet hier nichts - und eine Probe, die sie liest,
        // bekommt dieselbe Folge wie die Datenbank mit ihrem Zeitpunkt.
        return gesehen.computeIfAbsent(spieler, wer -> new LinkedHashSet<>()).add(name);
    }
}
