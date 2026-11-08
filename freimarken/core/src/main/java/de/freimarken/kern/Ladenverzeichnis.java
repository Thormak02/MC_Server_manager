package de.freimarken.kern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Welche Ladeneinheiten es gibt - die Liste aus der {@code config.yml}.
 *
 * <p>Dieselbe Bauart wie {@link Ankaufliste}: Die REIHENFOLGE der Datei bleibt, denn sie
 * ist die Reihenfolge in {@code /laden liste}; dort ordnet der Betreiber, und das ist
 * seine Entscheidung. Bei zwei Eintraegen zum selben Namen gewinnt der ERSTE, und der
 * zweite wird beim Lesen beklagt - nicht hier, denn diese Klasse kennt keinen Betreiber.
 *
 * <p><b>Dieses Verzeichnis wird NUR BEIM START gelesen.</b> Es ist kein Wert, sondern ein
 * Ort: Verschiebt ein Neuladen den Bereich einer Einheit, haette ein Mieter von einem
 * Augenblick auf den anderen einen anderen Raum - und seine Truhe stuende bei einem
 * Nachbarn. Dieselbe Entscheidung wie bei den Welten und ihren Toren.
 */
public final class Ladenverzeichnis {

    private final Map<String, Ladeneinheit> einheiten;

    private Ladenverzeichnis(Map<String, Ladeneinheit> einheiten) {
        this.einheiten = einheiten;
    }

    /**
     * Baut das Verzeichnis - der ERSTE Eintrag zu einem Namen gewinnt.
     *
     * <p>Die Reihenfolge der Liste bleibt die Reihenfolge des Verzeichnisses
     * ({@link LinkedHashMap}). Ein {@code null}-Eintrag ist ein Fehler im Programm und
     * keine Fehleingabe - der Leser wirft kaputte Zeilen weg, bevor sie hierher kommen.
     */
    public static Ladenverzeichnis ausEinheiten(List<Ladeneinheit> einheiten) {
        if (einheiten == null) {
            throw new IllegalArgumentException("Nichts ist kein Verzeichnis, sondern eine"
                    + " leere Liste");
        }
        Map<String, Ladeneinheit> karte = new LinkedHashMap<>();
        for (Ladeneinheit einheit : einheiten) {
            if (einheit == null) {
                throw new IllegalArgumentException("Eine Einheit in der Liste ist null");
            }
            karte.putIfAbsent(einheit.name(), einheit);
        }
        return new Ladenverzeichnis(java.util.Collections.unmodifiableMap(karte));
    }

    /**
     * Das leere Verzeichnis - solange die Markthalle nicht gebaut ist.
     *
     * <p>Heisst {@code ohneEinheiten} und nicht {@code leer}: Daneben steht die Frage
     * {@link #leer()}, und zwei Namen fuer zwei verschiedene Dinge sind besser als einer,
     * den der Uebersetzer trennt und ein Leser nicht.
     */
    public static Ladenverzeichnis ohneEinheiten() {
        return ausEinheiten(List.of());
    }

    public int anzahl() {
        return einheiten.size();
    }

    public boolean leer() {
        return einheiten.isEmpty();
    }

    /** Die Namen in der Reihenfolge der Datei. */
    public List<String> namen() {
        return List.copyOf(einheiten.keySet());
    }

    /** Die Einheiten in der Reihenfolge der Datei. */
    public List<Ladeneinheit> alle() {
        return List.copyOf(einheiten.values());
    }

    /**
     * Die Einheit mit diesem Namen - oder {@code null}.
     *
     * <p>Gibt {@code null} und wirft nicht: Der Name kommt aus einem Befehl, den ein
     * Spieler tippt, und ein Tippfehler ist keine Ausnahme, sondern eine Absage.
     */
    public Ladeneinheit mitNamen(String name) {
        if (name == null) {
            return null;
        }
        return einheiten.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Welche Einheit diesen Punkt umfasst - oder {@code null}.
     *
     * <p>Die Frage des Truhenschutzes, und sie laeuft bei jedem Oeffnen einer Truhe in
     * Altfurt. Eine REIHENSUCHE und kein Index: Die Halle hat eine Handvoll Einheiten,
     * und ein Index ueber Quader waere ein Baum, den niemand bezahlt sehen will. Wer
     * eines Tages hunderte Einheiten fuehrt, ordnet sie hier nach Chunk - und findet die
     * Stelle, weil sie die einzige ist.
     *
     * <p>Dass zwei Einheiten sich nicht ueberschneiden, prueft der Leser beim Start.
     * Darum darf diese Suche die erste Antwort nehmen; welche es waere, wenn sie sich doch
     * schnitten, entschiede sonst die Reihenfolge der Datei.
     */
    public Ladeneinheit anDerStelle(int x, int y, int z) {
        for (Ladeneinheit einheit : einheiten.values()) {
            if (einheit.enthaelt(x, y, z)) {
                return einheit;
            }
        }
        return null;
    }

    /**
     * Wie viele Bloecke alle Einheiten zusammen umfassen - fuer den Bericht.
     *
     * <p>Eine Zahl, an der ein Betreiber eine vertippte Ecke erkennt: Zehn Staende mit je
     * hundert Bloecken sind tausend; stehen dort hunderttausend, hat eine Einheit eine
     * Null zu viel.
     */
    public long bloecke() {
        long summe = 0L;
        for (Ladeneinheit einheit : einheiten.values()) {
            summe += einheit.bereich().bloecke();
        }
        return summe;
    }

    /**
     * Gleich ist, was denselben INHALT hat - nicht dieselbe Laenge.
     *
     * <p>Dieselbe Begruendung wie bei {@link Ankaufliste}: Eine Zaehlung saehe nicht, dass
     * eine Einheit verschoben wurde. Daran haengt jede kuenftige Zeile, die eine Aenderung
     * meldet.
     */
    @Override
    public boolean equals(Object andere) {
        return andere instanceof Ladenverzeichnis jenes
                && einheiten.equals(jenes.einheiten);
    }

    @Override
    public int hashCode() {
        return einheiten.hashCode();
    }

    @Override
    public String toString() {
        List<String> stuecke = new ArrayList<>();
        for (Ladeneinheit einheit : einheiten.values()) {
            stuecke.add(einheit.name() + " " + einheit.bereich().alsText());
        }
        return "Ladenverzeichnis[" + String.join(", ", stuecke) + "]";
    }
}
