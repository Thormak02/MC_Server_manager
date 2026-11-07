package de.freimarken.kern;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Die Serien im Arbeitsspeicher - fuer Tests und fuer einen Server ohne Datenbank.
 *
 * <p>Vergisst beim Neustart alles. Das ist kein Mangel, sondern der Zweck: Wer sie im
 * Betrieb einsetzt, soll das merken, statt es nach dem ersten Neustart zu entdecken.
 * Derselbe Rueckfall, den {@link SpeicherKasse}, {@link SpeicherLager} und
 * {@link SpeicherSpielzeit} schon haben.
 *
 * <p>Braucht KEINE Uhr: Der Tag kommt mit dem Stand herein. Eine eigene Uhr hier waere
 * eine zweite Quelle fuer die Tagesgrenze und damit genau der Fehler, gegen den
 * {@link Tagesgrenze} gebaut ist.
 *
 * <p>{@code synchronized} auf jeder Methode, wie {@link SpeicherKasse}: Die Zuhoerer und
 * Zeitgeber des Plugins laufen im Hauptfaden, die Vervollstaendigung von Befehlen aber
 * ausdruecklich nebenlaeufig. Eine nackte {@link HashMap} aus zwei Faeden ist nicht nur
 * ein verlorener Schreibzug, sondern im schlechten Fall eine zerstoerte Karte.
 */
public final class SpeicherBonus implements Bonusablage {

    private final Map<UUID, Bonusstand> staende = new HashMap<>();

    @Override
    public synchronized Bonusstand lies(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        return staende.getOrDefault(spieler, Bonusstand.leer(spieler));
    }

    @Override
    public synchronized void schreibe(Bonusstand stand) {
        if (stand == null) {
            throw new IllegalArgumentException("Nichts zu schreiben ist kein Stand");
        }
        // SETZEND und nicht zusammengerechnet - siehe das erste Versprechen in
        // Bonusablage. Ein merge waere hier falsch: Die Serie ist ein Zustand und keine
        // Summe.
        staende.put(stand.spieler(), stand);
    }
}
