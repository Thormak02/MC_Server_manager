package de.freimarken.paper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Wo ein Spieler zuletzt war - fuer {@code /back}.
 *
 * <p>Gemerkt wird an genau einer Stelle: in {@link Reise}, vor jedem Teleport - und
 * ausserdem beim Tod, denn der laeuft nicht durch {@code Reise}. Dass zweimal
 * {@code /zurueck} wieder am Ausgangspunkt endet, folgt daraus von selbst: Der Befehl
 * liest den gemerkten Ort, und die Reise dorthin merkt den jetzigen. Eine eigene
 * Tauschmethode waere eine zweite Stelle, die dasselbe tut.
 *
 * <p>Diese Klasse <b>prueft nichts</b> - sie merkt und tauscht. Ob der gemerkte Ort noch
 * taugt, entscheidet der Aufrufer unmittelbar vor dem Teleport: Eine Welt kann zwischen
 * dem Merken und dem Zurueckkehren entladen worden sein, und ein Boden kann abgebaut
 * worden sein. Eine Pruefung beim Merken waere zu diesem Zeitpunkt richtig und beim
 * Zurueckkehren falsch.
 *
 * <p>Nur im Speicher, nicht in der Datenbank [entschieden]. Ein Rueckweg, der einen
 * Neustart ueberlebt, fuehrt Tage spaeter an eine Stelle, von der niemand mehr weiss,
 * warum sie gemerkt wurde - und die Datenbank haette eine Tabelle mehr fuer etwas, das
 * nach einer Stunde niemand braucht. Beim Abmelden wird vergessen; das haelt die Karte so
 * gross wie die Zahl der anwesenden Spieler.
 *
 * <p>Nicht nebenlaeufig: Befehle und Ereignisse laufen im Hauptfaden des Servers.
 */
final class Rueckweg {

    private final Map<UUID, Location> letzte = new HashMap<>();

    /**
     * Merkt sich, wo der Spieler war.
     *
     * <p>Eine Abschrift und nicht der Ort selbst: Ein {@code Location} ist veraenderlich,
     * und Bukkit gibt bei {@code getLocation()} je nach Aufruf dasselbe Objekt zurueck.
     * Ohne {@code clone()} wanderte der gemerkte Ort mit dem Spieler mit, und
     * {@code /back} fuehrte dorthin, wo er gerade steht.
     */
    void merken(UUID wer, Location wo) {
        if (wo != null) {
            letzte.put(wer, wo.clone());
        }
    }

    /** Der gemerkte Ort, ohne ihn anzufassen - zum Anzeigen. */
    Optional<Location> letzter(UUID wer) {
        return Optional.ofNullable(letzte.get(wer)).map(Location::clone);
    }

    /** Vergisst den Rueckweg dieses Spielers - beim Abmelden. */
    void vergessen(UUID wer) {
        letzte.remove(wer);
    }

    /** Wie viele Rueckwege gemerkt sind - damit das Aufraeumen pruefbar ist. */
    int gemerkt() {
        return letzte.size();
    }
}
