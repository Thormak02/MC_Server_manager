package de.freimarken.kern;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Die Lose im Arbeitsspeicher - fuer Tests und fuer einen Server ohne Datenbank.
 *
 * <p>Vergisst beim Neustart alles. Das ist kein Mangel, sondern der Zweck: Wer sie im
 * Betrieb einsetzt, soll das merken, statt es nach dem ersten Neustart zu entdecken.
 * Derselbe Rueckfall, den {@link SpeicherKasse}, {@link SpeicherLager},
 * {@link SpeicherSpielzeit} und {@link SpeicherBonus} schon haben.
 *
 * <p><b>Bei der Lotterie kostet das Vergessen Geld</b> - anders als bei
 * {@link SpeicherBonus}, wo das Buchungsbuch die Wahrheit haelt. Nach einem Neustart hat
 * jeder Spieler wieder sein gratis Los. Das steht hier, damit es niemand erst im Betrieb
 * herausfindet; fuer einen Server, auf dem Geld zaehlt, ist die MariaDB-Fassung die
 * einzige richtige.
 *
 * <p>Alte Tage bleiben liegen und werden nie geraeumt: Diese Fassung lebt nur, solange
 * der Server laeuft, und in der Zeit sind es hoechstens so viele Zeilen wie Spieler mal
 * Tage. Die MariaDB-Fassung raeumt sie NICHT - dort bleiben alte Tage dauerhaft stehen
 * (eine Zeile je Spieler und Tag, in der sie ein Los gezogen hat). Hier stand bis zum
 * 23.11.2026 "hat dafuer ihren eigenen Raeumlauf"; einen solchen Lauf gibt es nicht, und
 * wer ihn sucht, sucht umsonst.
 *
 * <p>{@code synchronized} auf jeder Methode, wie {@link SpeicherKasse}: Die Zuhoerer und
 * Zeitgeber des Plugins laufen im Hauptfaden, die Vervollstaendigung von Befehlen aber
 * ausdruecklich nebenlaeufig. Eine nackte {@link HashMap} aus zwei Faeden ist nicht nur
 * ein verlorener Schreibzug, sondern im schlechten Fall eine zerstoerte Karte.
 */
public final class SpeicherLose implements Losablage {

    private final Map<String, Integer> zahlen = new HashMap<>();

    @Override
    public synchronized Losstand lies(UUID spieler, LocalDate tag) {
        pruefe(spieler, tag);
        return new Losstand(spieler, tag, zahlen.getOrDefault(schluessel(spieler, tag), 0));
    }

    @Override
    public synchronized int zaehleEins(UUID spieler, LocalDate tag) {
        pruefe(spieler, tag);
        // AUFSCHLAGEND und nicht setzend - siehe das erste Versprechen in Losablage.
        // merge und nicht lesen-rechnen-schreiben: In EINEM Zug, damit auch hier gilt,
        // was die MariaDB-Fassung mit ihrem ON DUPLICATE KEY UPDATE zusagt.
        return zahlen.merge(schluessel(spieler, tag), 1,
                (alt, eins) -> alt == Integer.MAX_VALUE ? alt : alt + eins);
    }

    /**
     * Setzt die Zahl unmittelbar - NUR fuer die Saettigungsprobe des Vertrags.
     *
     * <p>Paketsichtbar und darum nur aus dem Kern selbst erreichbar, nicht aus dem Plugin.
     * Es gibt keinen anderen Weg, den Zustand "zwei Milliarden Lose" herzustellen, und
     * ohne ihn liesse sich die Zusage der Schnittstelle fuer diese Fassung nicht pruefen -
     * eine Zusage, die nur eine von zwei Umsetzungen haelt, ist genau das, was am
     * 23.11.2026 auffiel.
     */
    synchronized void setzeFuerProbe(UUID spieler, LocalDate tag, int anzahl) {
        pruefe(spieler, tag);
        zahlen.put(schluessel(spieler, tag), anzahl);
    }

    private static void pruefe(UUID spieler, LocalDate tag) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Lose");
        }
        if (tag == null) {
            throw new IllegalArgumentException("Ohne Tag gibt es keine Lose");
        }
    }

    /**
     * Spieler und Tag als ein Schluessel.
     *
     * <p>Ein Text und kein zusammengesetztes Objekt: Die Fassung soll so klein bleiben,
     * dass an ihr nichts zu pruefen ist. Der Trenner ist das Leerzeichen, und es kann in
     * keinem der beiden Teile vorkommen - eine UUID und ein ISO-Datum haben keines.
     */
    private static String schluessel(UUID spieler, LocalDate tag) {
        return spieler + " " + tag;
    }
}
