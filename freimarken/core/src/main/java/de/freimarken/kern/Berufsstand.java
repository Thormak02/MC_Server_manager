package de.freimarken.kern;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Was ein Spieler an Berufen haelt - und wann er zuletzt einen abgelegt hat.
 *
 * <p><b>KEIN Verdienst darin</b>, und das ist Absicht: Was ein Beruf einbringt, kommt mit
 * Teil E. Dieser Satz beantwortet nur die Frage, die Teil D stellt - wer darf was waehlen.
 * Dieselbe Trennung wie bei {@link Spielzeitstand}, der ausdruecklich keinen Rang traegt.
 *
 * <p><b>Nur die AKTIVEN Berufe</b>, in der Reihenfolge, in der sie gewaehlt wurden. Ein
 * abgelegter Beruf steht in der Ablage weiter als Zeile, aber nicht hier: Der Spieler
 * haelt ihn nicht mehr, und eine Liste, die Gehaltenes und Abgelegtes mischt, liesse die
 * Platzzaehlung der Regel zweimal zuschlagen.
 *
 * <p><b>Der Zeitpunkt des Ablegens ist die Sperrfrist selbst.</b> Er kommt nicht aus dem
 * jungsten WAEHLEN - sonst sperrte die erste Wahl einen neuen Spieler einen Tag lang aus
 * seinem zweiten Grundberuf, und niemand wuerde das als Fehler melden, weil die Absage
 * dieselbe ist.
 *
 * @param spieler        wessen Stand das ist
 * @param berufe         die aktiven Berufe, in der Reihenfolge der Wahl, ohne Doppelte
 * @param letztesAblegen wann zuletzt ein Beruf abgelegt wurde, oder {@code null}, wenn
 *                       noch nie
 */
public record Berufsstand(UUID spieler, List<String> berufe, Instant letztesAblegen) {

    public Berufsstand {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keinen Berufsstand");
        }
        if (berufe == null) {
            throw new IllegalArgumentException("Die Berufe sind nie null; eine leere"
                    + " Liste ist der Stand eines Spielers, der noch keinen gewaehlt hat");
        }
        // Keine Doppelten, und darum hier und nicht erst in der Regel: Ein Beruf zweimal
        // in der Liste liesse den Platzzaehler zweimal zuschlagen, und der Spieler koennte
        // mit einem Beruf keinen zweiten mehr waehlen. Ein LinkedHashSet, damit die
        // Reihenfolge der Wahl erhalten bleibt - sie ist das, was /beruf stand zeigt.
        LinkedHashSet<String> ohneDoppelte = new LinkedHashSet<>();
        for (String beruf : berufe) {
            if (beruf == null || beruf.isBlank()) {
                throw new IllegalArgumentException("Ein leerer Beruf steht in der Liste"
                        + " von " + spieler + " - das ist eine Zeile der Ablage, die"
                        + " niemand lesen kann");
            }
            if (!ohneDoppelte.add(beruf)) {
                throw new IllegalArgumentException("Der Beruf " + beruf + " steht zweimal"
                        + " im Stand von " + spieler + ". Die Ablage hat einen"
                        + " Primaerschluessel aus Spieler und Beruf - zweimal kann er"
                        + " nur durch einen Rechenfehler hierher kommen");
            }
        }
        berufe = List.copyOf(ohneDoppelte);
    }

    /** Der Stand eines Spielers, von dem die Ablage nichts weiss. */
    public static Berufsstand leer(UUID spieler) {
        return new Berufsstand(spieler, List.of(), null);
    }

    /** Ob er diesen Beruf gerade haelt. */
    public boolean haelt(String beruf) {
        return berufe.contains(beruf);
    }

    /**
     * Ob er noch nie etwas mit Berufen zu tun hatte.
     *
     * <p>Nicht dasselbe wie {@code berufe().isEmpty()}: Wer alles abgelegt hat, hat auch
     * eine leere Liste - aber eine laufende Sperrfrist.
     */
    public boolean nochNieGewaehlt() {
        return berufe.isEmpty() && letztesAblegen == null;
    }
}
