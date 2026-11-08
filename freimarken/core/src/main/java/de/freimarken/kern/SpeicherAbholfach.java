package de.freimarken.kern;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Das Abholfach im Arbeitsspeicher - fuer Tests und fuer einen Server ohne Datenbank.
 *
 * <p>Vergisst beim Neustart alles. Bei dieser Ablage ist das schwerer zu verkraften als
 * bei den anderen: Ein vergessenes Abholfach ist verlorener Besitz, und das Versprechen
 * des Fachs lautet "wird nie geloescht". Der Rueckfall bleibt trotzdem, aus demselben
 * Grund wie bei {@link SpeicherLager} - ein Server, der ohne Datenbank startet, soll
 * laufen und es merken, statt gar nicht zu starten.
 *
 * <p>Eine {@link LinkedHashMap} und keine {@link java.util.TreeMap}: Die Reihenfolge ist
 * die des Einlegens, und die Nummern steigen ohnehin. Eine Sortierung nach Nummer waere
 * dieselbe Reihenfolge mit mehr Arbeit.
 *
 * <p>{@code synchronized} auf jeder Methode, wie {@link SpeicherMiete}: Die Zeitgeber des
 * Plugins laufen im Hauptfaden, die Vervollstaendigung von Befehlen aber ausdruecklich
 * nebenlaeufig.
 */
public final class SpeicherAbholfach implements Abholablage {

    private final Map<Long, Abholstueck> stuecke = new LinkedHashMap<>();

    /**
     * Die naechste Nummer.
     *
     * <p>Steigt und wird nie wiederverwendet, auch nicht, wenn das Fach leer ist. Eine
     * wiederverwendete Nummer ist genau der Fall, in dem ein Spieler mit einem alten
     * Fenster ein Stueck holt, das inzwischen einem anderen gehoert.
     */
    private long naechste = 1L;

    @Override
    public synchronized List<Abholstueck> fuerSpieler(UUID empfaenger) {
        if (empfaenger == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        List<Abholstueck> seine = new ArrayList<>();
        for (Abholstueck stueck : stuecke.values()) {
            if (stueck.gehoert(empfaenger)) {
                seine.add(stueck);
            }
        }
        return seine;
    }

    @Override
    public synchronized int anzahlFuer(UUID empfaenger) {
        return fuerSpieler(empfaenger).size();
    }

    @Override
    public synchronized void lege(UUID empfaenger, String herkunft, Instant wann,
            List<Abholgut> stapel) {
        if (stapel == null) {
            throw new IllegalArgumentException("Nichts ist keine Liste von Stapeln -"
                    + " eine LEERE Liste ist erlaubt und tut nichts");
        }
        // ZUERST alle bauen, dann alle einhaengen. Das ist das zweite Versprechen: Wirft
        // der dritte Stapel, weil seine Beschriftung zu lang ist, darf der erste nicht
        // schon im Fach liegen - sonst stuende er beim zweiten Versuch doppelt da.
        List<Abholstueck> fertige = new ArrayList<>();
        long nummer = naechste;
        for (Abholgut gut : stapel) {
            fertige.add(new Abholstueck(nummer, empfaenger, herkunft, wann, gut));
            nummer++;
        }
        for (Abholstueck stueck : fertige) {
            stuecke.put(stueck.id(), stueck);
        }
        naechste = nummer;
    }

    @Override
    public synchronized int nimm(UUID empfaenger, List<Long> ids) {
        if (empfaenger == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu nehmen");
        }
        if (ids == null) {
            throw new IllegalArgumentException("Nichts ist keine Liste von Nummern");
        }
        int weg = 0;
        for (Long id : ids) {
            if (id == null || id < 1L) {
                throw new IllegalArgumentException("\"" + id + "\" ist keine Nummer eines"
                        + " Abholstuecks - sie beginnen bei 1");
            }
            // Erst nachsehen, WEM es gehoert. Ein blosses remove(id) holte die Ware eines
            // fremden Spielers, sobald eine Nummer ein zweites Mal vergeben wird - und in
            // der Datenbankfassung passiert das nach einem Neustart.
            Abholstueck stueck = stuecke.get(id);
            if (stueck != null && stueck.gehoert(empfaenger)) {
                stuecke.remove(id);
                weg++;
            }
        }
        return weg;
    }
}
