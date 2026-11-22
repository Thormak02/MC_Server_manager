package de.freimarken.kern;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Der Angelwettbewerb im Arbeitsspeicher - fuer Tests und fuer einen Server ohne
 * Datenbank.
 *
 * <p>Vergisst beim Neustart alles. Das ist kein Mangel, sondern der Zweck: Wer sie im
 * Betrieb einsetzt, soll das merken, statt es nach dem ersten Neustart zu entdecken.
 *
 * <p><b>Beim Wettbewerb kostet das Vergessen mehr als bei den anderen fluechtigen
 * Fassungen</b>, und darum steht es hier: Ein Neustart MITTEN IM FENSTER loescht die
 * Punkte und die Anmeldungen - die Startgelder sind dann abgebucht und der Topf ist weg.
 * Darum ist der Wettbewerb ein Teil, bei dem die MariaDB-Fassung nicht nur besser,
 * sondern die einzig richtige ist; die Karte, die das traegt, haelt der Schemaschritt.
 *
 * <p>{@code synchronized} auf jeder Methode, wie {@link SpeicherKasse}: Die Zuhoerer und
 * Zeitgeber des Plugins laufen im Hauptfaden, die Vervollstaendigung von Befehlen aber
 * ausdruecklich nebenlaeufig.
 */
public final class SpeicherWettbewerb implements Wettbewerbsablage {

    private final Map<String, Punktzeile> zeilen = new HashMap<>();
    private final Set<LocalDate> abgeschlossen = new HashSet<>();

    @Override
    public synchronized boolean meldeAn(UUID spieler, LocalDate lauf, long startgeld) {
        pruefe(spieler, lauf);
        String schluessel = schluessel(spieler, lauf);
        if (zeilen.containsKey(schluessel)) {
            // DER RIEGEL: in EINEM Zug gefragt und gesetzt. Zwei Klicks auf
            // /wettbewerb anmelden kosten nur einmal Startgeld.
            return false;
        }
        zeilen.put(schluessel, Punktzeile.angemeldet(spieler, lauf, startgeld));
        return true;
    }

    @Override
    public synchronized Optional<Integer> zaehlePunkte(UUID spieler, LocalDate lauf,
                                                       int punkte) {
        pruefe(spieler, lauf);
        Punktzeile vorher = zeilen.get(schluessel(spieler, lauf));
        if (vorher == null) {
            // Kein Fehler: Wer nicht angemeldet ist, sammelt nicht.
            return Optional.empty();
        }
        Punktzeile nachher = vorher.mitMehr(punkte);
        zeilen.put(schluessel(spieler, lauf), nachher);
        return Optional.of(nachher.punkte());
    }

    @Override
    public synchronized List<Punktzeile> zeilen(LocalDate lauf) {
        if (lauf == null) {
            throw new IllegalArgumentException("Ohne Lauf gibt es keine Zeilen");
        }
        List<Punktzeile> gefunden = new ArrayList<>();
        for (Punktzeile zeile : zeilen.values()) {
            if (zeile.lauf().equals(lauf)) {
                gefunden.add(zeile);
            }
        }
        // UNSORTIERT zurueck, mit Absicht: Sortiert wird in Wettbewerbsregel.rangliste,
        // und zwar dort allein. Die Reihenfolge einer HashMap ist ohnehin keine.
        return List.copyOf(gefunden);
    }

    @Override
    public synchronized Optional<Punktzeile> zeile(UUID spieler, LocalDate lauf) {
        pruefe(spieler, lauf);
        return Optional.ofNullable(zeilen.get(schluessel(spieler, lauf)));
    }

    @Override
    public synchronized boolean merkeAbgeschlossen(LocalDate lauf,
                                                   java.time.Instant jetzt) {
        if (lauf == null || jetzt == null) {
            throw new IllegalArgumentException("Ohne Lauf und Zeitpunkt gibt es nichts"
                    + " abzuschliessen");
        }
        // Der Zeitpunkt wird hier NICHT gemerkt: Diese Fassung vergisst beim Neustart
        // ohnehin alles, und eine Spur fuer spaeter waere eine Zusage, die sie nicht
        // halten kann. Sie verlangt ihn trotzdem, damit der Vertrag fuer BEIDE
        // Fassungen derselbe ist.
        // DER ZWEITE RIEGEL, und der wichtigere: add gibt false, wenn schon drin. Ohne
        // ihn zahlte ein zweiter Takt denselben Topf ein zweites Mal aus.
        return abgeschlossen.add(lauf);
    }

    @Override
    public synchronized boolean istAbgeschlossen(LocalDate lauf) {
        if (lauf == null) {
            throw new IllegalArgumentException("Ohne Lauf gibt es keine Antwort");
        }
        return abgeschlossen.contains(lauf);
    }

    private static void pruefe(UUID spieler, LocalDate lauf) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Punkte");
        }
        if (lauf == null) {
            throw new IllegalArgumentException("Ohne Lauf gibt es keine Punkte");
        }
    }

    /**
     * Spieler und Lauf als ein Schluessel.
     *
     * <p>Ein Text und kein zusammengesetztes Objekt, wie bei {@link SpeicherLose}: Der
     * Trenner ist das Leerzeichen, und es kann in keinem der beiden Teile vorkommen -
     * eine UUID und ein ISO-Datum haben keines.
     */
    private static String schluessel(UUID spieler, LocalDate lauf) {
        return spieler + " " + lauf;
    }
}
