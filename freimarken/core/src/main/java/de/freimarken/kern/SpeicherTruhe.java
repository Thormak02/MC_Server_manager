package de.freimarken.kern;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Die Entnahmen im Arbeitsspeicher - fuer Proben und fuer einen Server ohne Datenbank.
 *
 * <p>Dieselbe Rolle wie {@link SpeicherBonus} und {@link SpeicherKasse}: Sie haelt die
 * Zusagen der {@link Truhenablage} vollstaendig, nur nicht ueber einen Neustart. Was sie
 * damit kostet, ist genau die Zusage, um die es beim Deckel geht - nach einem Neustart
 * darf jeder noch einmal sechzehn Stueck nehmen. Fuer einen Probeserver ist das tragbar,
 * fuer den Betrieb nicht; darum nimmt das Plugin sie nur, wenn keine Datenbank
 * eingetragen ist, und die Startmeldung sagt es.
 *
 * <p>{@code synchronized}, wie die uebrigen Speicherablagen: Gelesen und geschrieben wird
 * aus dem Hauptfaden, aber eine Probe darf mehrere Faeden benutzen, und eine Ablage, die
 * dabei zerbricht, waere eine Falle fuer die Probe und nicht fuer das Programm.
 */
public final class SpeicherTruhe implements Truhenablage {

    /**
     * Der Schluessel einer Zeile: Spieler UND Tag.
     *
     * <p>Ein record und kein zusammengesetzter Text: Zwei Werte, die zusammen ein
     * Schluessel sind, gehoeren in einen Typ - ein {@code spieler + "|" + tag} waere
     * derselbe Schluessel mit einer Stelle, an der jemand das Trennzeichen aendert.
     */
    private record Zeile(UUID spieler, LocalDate tag) {
    }

    private final Map<Zeile, Integer> behalten = new HashMap<>();

    @Override
    public synchronized int behalten(UUID spieler, LocalDate tag) {
        pruefe(spieler, tag);
        return behalten.getOrDefault(new Zeile(spieler, tag), 0);
    }

    @Override
    public synchronized void merke(UUID spieler, LocalDate tag, int stueck) {
        pruefe(spieler, tag);
        if (stueck < 1) {
            throw new IllegalArgumentException("Ein Aufschlag von " + stueck
                    + " Stueck ist keiner - angerechnet wird nur, was er BEHAELT, und das"
                    + " ist mindestens eines");
        }
        behalten.merge(new Zeile(spieler, tag), stueck, Integer::sum);
    }

    @Override
    public synchronized int raeume(LocalDate vorTag) {
        if (vorTag == null) {
            throw new IllegalArgumentException("Ohne Tag gibt es keine Grenze");
        }
        // AUSSCHLIESSEND: Der uebergebene Tag bleibt stehen. Einschliessend geraeumt
        // waere der laufende Tag weg, und jeder duerfte noch einmal nehmen.
        int vorher = behalten.size();
        behalten.keySet().removeIf(zeile -> zeile.tag().isBefore(vorTag));
        return vorher - behalten.size();
    }

    private static void pruefe(UUID spieler, LocalDate tag) {
        if (spieler == null || tag == null) {
            throw new IllegalArgumentException("Ohne Spieler und Tag gibt es keine Zeile");
        }
    }
}
