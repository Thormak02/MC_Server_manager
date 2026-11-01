package de.freimarken.paper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wer zuletzt wann etwas Teures ausgeloest hat.
 *
 * <p>Gebaut fuer die Abschaetzung vor dem Auftrennen. Die liest jede Spalte ueber der
 * Naht - bei vier Feldern, hoch bebaut und tief ausgehoben, ein paar hunderttausend
 * Bloecke - und sie kostet nichts: kein Geld, keine Bestaetigung, keine Veraenderung.
 * Alles andere am Plugin bremst sich von selbst, weil es etwas abbucht oder schreibt;
 * ausgerechnet der teuerste Lesevorgang tat das nicht.
 *
 * <p>Eine eigene Klasse und keine zwei Zeilen im Befehl: So laesst sich die Regel ohne
 * Server pruefen, und die Zeit kommt von aussen statt aus einem Aufruf von
 * {@code System.nanoTime()} mitten in der Rechnung - sonst waere jede Probe ein
 * Gluecksspiel mit der Uhr.
 *
 * <p>Nicht nebenlaeufig: Befehle laufen im Hauptfaden des Servers, und zwei davon
 * gleichzeitig gibt es nicht.
 */
final class Abklingzeit {

    private final long dauerNs;
    private final Map<UUID, Long> zuletzt = new HashMap<>();

    Abklingzeit(Duration dauer) {
        this.dauerNs = dauer.toNanos();
    }

    /**
     * Wie lange dieser Spieler noch warten muss.
     *
     * <p>Null heisst frei - und dann ist der Zeitpunkt vermerkt. Die Antwort und das
     * Vermerken gehoeren zusammen: Zwei Methoden daraus zu machen hiesse, dass ein
     * Aufrufer das Vermerken vergessen kann, und dann greift die Sperre nie.
     *
     * @param wer     der Spieler
     * @param jetztNs die Zeit, gemessen mit {@link System#nanoTime()}
     * @return 0, wenn er darf; sonst die Restzeit in Nanosekunden
     */
    long restNs(UUID wer, long jetztNs) {
        // Abgelaufenes faellt bei jeder Anfrage weg. Ohne das waechst die Karte mit jedem
        // Spieler, der den Befehl je benutzt hat, und ein Serverlauf dauert Wochen.
        zuletzt.values().removeIf(zeit -> jetztNs - zeit >= dauerNs);
        Long vorher = zuletzt.putIfAbsent(wer, jetztNs);
        return vorher == null ? 0L : dauerNs - (jetztNs - vorher);
    }

    /** Wie viele Spieler gerade vermerkt sind - damit das Aufraeumen pruefbar ist. */
    int vermerkt() {
        return zuletzt.size();
    }
}
