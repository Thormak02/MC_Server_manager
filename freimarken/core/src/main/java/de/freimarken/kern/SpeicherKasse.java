package de.freimarken.kern;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kasse im Arbeitsspeicher - fuer Tests und fuer einen Server ohne Datenbank.
 *
 * <p>Vergisst beim Neustart alles. Das ist kein Mangel, sondern der Zweck: Wer sie im
 * Betrieb einsetzt, soll das merken, statt es nach dem ersten Neustart zu entdecken.
 */
public final class SpeicherKasse implements Kasse {

    private final Map<UUID, Long> stand = new HashMap<>();
    private final long startguthaben;

    public SpeicherKasse(long startguthaben) {
        if (startguthaben < 0) {
            throw new IllegalArgumentException("Startguthaben darf nicht negativ sein");
        }
        this.startguthaben = startguthaben;
    }

    @Override
    public synchronized long guthaben(UUID spieler) {
        return stand.getOrDefault(spieler, startguthaben);
    }

    @Override
    public synchronized boolean abbuchen(UUID spieler, long betrag) {
        Kasse.pruefeBetrag(betrag);
        long vorhanden = guthaben(spieler);
        if (vorhanden < betrag) {
            return false;
        }
        stand.put(spieler, vorhanden - betrag);
        return true;
    }

    @Override
    public synchronized void gutschreiben(UUID spieler, long betrag) {
        Kasse.pruefeBetrag(betrag);
        // addExact und nicht +: Ein Ueberlauf wuerde aus viel Geld lautlos Schulden machen.
        // MariaDB bricht an derselben Stelle mit einem Fehler ab - beide Kassen sollen sich
        // gleich verhalten, nicht nur solange die Betraege klein bleiben.
        stand.put(spieler, Math.addExact(guthaben(spieler), betrag));
    }
}
