package de.freimarken.kern;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eine laufende Blockzaehlung - der Zustand zwischen zwei Takten (Teil E).
 *
 * <p><b>Warum es diese Klasse gibt:</b> Ein Wolkenfeld ist 128 Bloecke breit und reicht
 * ueber die ganze Welthoehe. Das sind 64 Chunks mit mehr als sechs Millionen Bloecken - in
 * einem Zug gezaehlt stuende der Server eine Sekunde oder laenger. Gezaehlt wird darum in
 * HAPPEN, und was zwischen zwei Takten bleibt, steht hier.
 *
 * <p><b>Ohne Bukkit und ohne Zeitgeber</b>, wie jede Regel dieses Kerns: Welche Chunks
 * noch offen sind, rechnet diese Klasse; WAS in einem Chunk liegt, kommt als Karte
 * herein. Dadurch laeuft der ganze Ablauf - Reihenfolge, Teilsummen, Abbruch - ohne
 * laufenden Server durch eine Probe.
 *
 * <p><b>Die Reihenfolge der Chunks ist festgelegt</b> und nicht beliebig: Zeile fuer
 * Zeile, von der kleinsten Ecke aus. Eine Zaehlung, die ihre Chunks in wechselnder Folge
 * nimmt, laesst sich nicht wiederholen - und zwei Laeufe ueber dieselbe Insel haetten
 * verschiedene Teilsummen, solange dort gebaut wird.
 */
public final class Zaehlauftrag {

    private final long inselId;

    /**
     * Welche Chunks noch offen sind - die GETEILTE Happenrechnung.
     *
     * <p>Sie steht in {@link Chunkfolge} und nicht hier, weil das Umsetzen der Biomzellen
     * dieselbe Rechnung braucht. Zwei Abschriften waeren zwei Orte fuer denselben Fehler.
     */
    private final Chunkfolge folge;

    /** Je Blockname die Zahl der gefundenen Stuecke. */
    private final Map<String, Long> gefunden = new LinkedHashMap<>();

    /**
     * Ein Auftrag fuer das Feld dieser Insel.
     *
     * @param inselId  die Nummer der Insel - sie steht in der Ablage
     * @param plan     die Geometrie; aus ihr kommen die Blockgrenzen des Feldes
     * @param feldX    die Feldnummer in x
     * @param feldZ    die Feldnummer in z
     */
    public Zaehlauftrag(long inselId, Wolkenplan plan, int feldX, int feldZ) {
        if (inselId <= 0L) {
            throw new IllegalArgumentException("Eine Insel ohne Nummer gibt es nicht,"
                    + " nicht " + inselId);
        }
        if (plan == null) {
            throw new IllegalArgumentException("Ohne Plan gibt es kein Feld zu zaehlen");
        }
        this.inselId = inselId;
        this.folge = new Chunkfolge(plan, feldX, feldZ);
    }

    /** Die Insel, um die es geht. */
    public long inselId() {
        return inselId;
    }

    /** Wie viele Chunks das Feld hat. */
    public int chunkzahl() {
        return folge.chunkzahl();
    }

    /** Ob alles gezaehlt ist. */
    public boolean fertig() {
        return folge.fertig();
    }

    /** Wie weit es ist, in Prozent - fuer eine Zeile im Chat oder im Protokoll. */
    public int fortschrittProzent() {
        return folge.fortschrittProzent();
    }

    /** Die naechsten Chunks - und der Zeiger geht WEITER. Siehe {@link Chunkfolge}. */
    public List<int[]> naechsterHappen(int chunksJeTakt) {
        return folge.naechsterHappen(chunksJeTakt);
    }

    /** Was in einem Happen lag, kommt dazu. */
    public void nimm(Map<String, Long> bloecke) {
        if (bloecke == null) {
            return;
        }
        for (var zeile : bloecke.entrySet()) {
            if (zeile.getKey() != null && zeile.getValue() != null
                    && zeile.getValue() > 0L) {
                gefunden.merge(zeile.getKey(), zeile.getValue(), Long::sum);
            }
        }
    }

    /** Was bisher gefunden wurde - je Blockname die Zahl der Stuecke. */
    public Map<String, Long> gefunden() {
        return Map.copyOf(gefunden);
    }

    /**
     * Die Punktzahl des bisher Gezaehlten.
     *
     * <p>Gerechnet und nicht mitgezaehlt, und das ist der Grund: Die Tabelle ist
     * neuladbar. Eine Summe, die beim Finden entsteht, waere nach einer Aenderung der
     * Punktetabelle eine Zahl aus zwei Tabellen - und niemand koennte sie nachrechnen.
     */
    public long punkte(Punktwerte werte) {
        if (werte == null) {
            throw new IllegalArgumentException("Ohne Punktwerte gibt es keine Punkte");
        }
        long summe = 0L;
        for (var zeile : gefunden.entrySet()) {
            long je = werte.punkteFuer(zeile.getKey());
            if (je > 0L) {
                summe += je * zeile.getValue();
            }
        }
        return summe;
    }
}
