package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Die Mietvertraege im Arbeitsspeicher - fuer Tests und fuer einen Server ohne Datenbank.
 *
 * <p>Vergisst beim Neustart alles. Das ist kein Mangel, sondern der Zweck: Wer sie im
 * Betrieb einsetzt, soll das merken, statt es nach dem ersten Neustart zu entdecken.
 * Derselbe Rueckfall, den {@link SpeicherKasse}, {@link SpeicherLager},
 * {@link SpeicherSpielzeit} und {@link SpeicherBonus} schon haben.
 *
 * <p>Eine {@link TreeMap} und keine {@link java.util.HashMap}: {@link #alle} muss zweimal
 * hintereinander dieselbe Reihenfolge liefern, und das ist kein Geschmack, sondern das
 * dritte Versprechen aus {@link Mietablage#alle}.
 *
 * <p>{@code synchronized} auf jeder Methode, wie {@link SpeicherBonus}: Die Zuhoerer und
 * Zeitgeber des Plugins laufen im Hauptfaden, die Vervollstaendigung von Befehlen aber
 * ausdruecklich nebenlaeufig. Eine nackte Karte aus zwei Faeden ist nicht nur ein
 * verlorener Schreibzug, sondern im schlechten Fall eine zerstoerte Karte.
 */
public final class SpeicherMiete implements Mietablage {

    private final Map<String, Mietvertrag> vertraege = new TreeMap<>();

    @Override
    public synchronized Mietvertrag fuerEinheit(String einheit) {
        return vertraege.get(schluessel(einheit));
    }

    @Override
    public synchronized Mietvertrag fuerMieter(UUID mieter) {
        if (mieter == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        for (Mietvertrag vertrag : vertraege.values()) {
            if (vertrag.gehoert(mieter)) {
                return vertrag;
            }
        }
        return null;
    }

    @Override
    public synchronized List<Mietvertrag> alle() {
        return new ArrayList<>(vertraege.values());
    }

    @Override
    public synchronized void schreibe(Mietvertrag vertrag) {
        if (vertrag == null) {
            throw new IllegalArgumentException("Nichts zu schreiben ist kein Vertrag");
        }
        // Das zweite Versprechen: ein Laden je Spieler, gewacht von der Ablage. Die
        // Abfrage muss im SELBEN synchronized stehen wie das Schreiben - genau das ist in
        // der Datenbankfassung der eindeutige Schluessel auf dem Mieter.
        Mietvertrag schon = fuerMieter(vertrag.mieter());
        if (schon != null && !schon.einheit().equals(vertrag.einheit())) {
            throw new IllegalStateException("Dem Mieter gehoert schon \""
                    + schon.einheit() + "\" - ein Laden je Spieler (Plan 7.1).");
        }
        vertraege.put(vertrag.einheit(), vertrag);
    }

    @Override
    public synchronized boolean loesche(String einheit) {
        return vertraege.remove(schluessel(einheit)) != null;
    }

    private static String schluessel(String einheit) {
        if (einheit == null || einheit.isBlank()) {
            throw new IllegalArgumentException("Ohne Ladeneinheit gibt es keinen Vertrag");
        }
        return einheit.trim().toLowerCase(Locale.ROOT);
    }
}
