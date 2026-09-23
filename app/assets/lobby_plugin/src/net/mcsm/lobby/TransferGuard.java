package net.mcsm.lobby;

/**
 * Reine Entscheidungslogik fuer den Transfer-Trichter - bewusst OHNE Bukkit, damit sie
 * ohne laufenden Server testbar ist.
 *
 * <p>Hier sass ein Fehler, der die ganze Rueckfrage aushebelte: {@code onMove} ruft beim
 * Durchlaufen einer Portal-Region bei JEDEM Blockwechsel einen Transfer an. Wurde jede
 * Wiederholung als "der Spieler hat bejaht" gewertet, lieferte der naechste Schritt den
 * zweiten Klick selbst - der Spieler war transferiert, bevor er die Frage lesen konnte,
 * und landete genau in dem Disconnect-Screen, den die Pruefung verhindern soll.
 *
 * <p>Deshalb zwei Bedingungen: der Ausloeser muss eine BEWUSSTE Handlung sein
 * (Menue-Klick, /server, Schild - kein Schritt), und die Frage muss lange genug offen
 * stehen, dass man sie lesen konnte.
 */
final class TransferGuard {

    private TransferGuard() {
    }

    /**
     * Darf dieser Aufruf eine offene Rueckfrage ueberstimmen?
     *
     * @param askedAt    Zeitpunkt der Rueckfrage, oder {@code null}, wenn keine offen ist
     * @param now        aktuelle Zeit (gleiche Zeitbasis wie {@code askedAt})
     * @param deliberate {@code true} nur bei einer bewussten Handlung des Spielers
     * @param minAgeMs   so lange muss die Frage mindestens offen stehen
     * @param windowMs   danach ist sie verfallen
     */
    static boolean mayOverride(Long askedAt, long now, boolean deliberate,
                               long minAgeMs, long windowMs) {
        if (!deliberate || askedAt == null) {
            return false;
        }
        long age = now - askedAt;
        // Negatives Alter (Uhr zurueckgestellt) zaehlt nicht als Zustimmung.
        return age >= minAgeMs && age < windowMs;
    }
}
