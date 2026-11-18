package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Chunks EINES Wolkenfeldes, in fester Folge und in Happen (Teil E).
 *
 * <p><b>Warum es diese Klasse gibt:</b> Zwei Arbeiten laufen ueber ein ganzes Feld - das
 * Zaehlen der Bloecke ({@link Zaehlauftrag}) und das Umsetzen der Biomzellen. Beide
 * muessen in Happen laufen, denn ein Feld ist 128 Bloecke breit und reicht ueber die ganze
 * Welthoehe: 64 Chunks mit mehr als sechs Millionen Bloecken. Zwei Abschriften derselben
 * Happenrechnung waeren zwei Orte fuer denselben Fehler - und der zweite bekaeme die
 * naechste Verschaerfung nicht mit.
 *
 * <p><b>Die Reihenfolge ist festgelegt</b> und nicht beliebig: Zeile fuer Zeile, von der
 * kleinsten Ecke aus. Eine Arbeit, die ihre Chunks in wechselnder Folge nimmt, laesst sich
 * nicht wiederholen - und zwei Laeufe ueber dieselbe Insel haetten verschiedene
 * Teilsummen, solange dort gebaut wird.
 */
public final class Chunkfolge {

    /** Jeder Chunk des Feldes: {@code {chunkX, chunkZ}}. */
    private final List<int[]> chunks;

    /** Wie viele davon erledigt sind - der Zeiger. */
    private int erledigt;

    /**
     * Die Chunks des Feldes mit diesen Nummern.
     *
     * @param plan  die Geometrie; aus ihr kommen die Blockgrenzen des Feldes
     * @param feldX die Feldnummer in x
     * @param feldZ die Feldnummer in z
     */
    public Chunkfolge(Wolkenplan plan, int feldX, int feldZ) {
        if (plan == null) {
            throw new IllegalArgumentException("Ohne Plan gibt es kein Feld");
        }
        this.chunks = List.copyOf(chunksVon(plan, feldX, feldZ));
    }

    /**
     * Jeder Chunk, der das Feld beruehrt - Zeile fuer Zeile.
     *
     * <p>Gerechnet aus den BLOCKGRENZEN und nicht aus einer Zahl: Ein Feld ist 128 Bloecke
     * breit, also acht Chunks - aber nur, solange es auch an einer Chunkgrenze beginnt.
     * Eine festverdrahtete 8 waere eine Annahme ueber den Weltversatz, und die stimmt
     * genau so lange, wie niemand die Feldkante aendert.
     */
    private static List<int[]> chunksVon(Wolkenplan plan, int feldX, int feldZ) {
        int vonX = plan.feldAnfang(feldX) >> 4;
        int bisX = (plan.feldAnfang(feldX) + plan.feldKante() - 1) >> 4;
        int vonZ = plan.feldAnfang(feldZ) >> 4;
        int bisZ = (plan.feldAnfang(feldZ) + plan.feldKante() - 1) >> 4;
        List<int[]> alle = new ArrayList<>();
        for (int x = vonX; x <= bisX; x++) {
            for (int z = vonZ; z <= bisZ; z++) {
                alle.add(new int[] {x, z});
            }
        }
        return alle;
    }

    /** Wie viele Chunks das Feld hat. */
    public int chunkzahl() {
        return chunks.size();
    }

    /** Ob alles erledigt ist. */
    public boolean fertig() {
        return erledigt >= chunks.size();
    }

    /** Wie weit es ist, in Prozent - fuer eine Zeile im Chat oder im Protokoll. */
    public int fortschrittProzent() {
        return chunks.isEmpty() ? 100 : (int) (100L * erledigt / chunks.size());
    }

    /**
     * Die naechsten Chunks - und der Zeiger geht WEITER.
     *
     * <p>Die Liste ist kuerzer als gewuenscht, wenn weniger offen sind, und leer, wenn
     * alles erledigt ist. Dass der Zeiger hier weitergeht und nicht erst nach der Arbeit,
     * ist Absicht: Ein Chunk, der sich nicht laden laesst, soll die Arbeit nicht anhalten -
     * sonst liefe sie an einer entladenen Ecke ewig.
     */
    public List<int[]> naechsterHappen(int jeTakt) {
        if (jeTakt < 1) {
            throw new IllegalArgumentException("Ein Happen von " + jeTakt
                    + " Chunks kommt nie weiter");
        }
        int bis = Math.min(chunks.size(), erledigt + jeTakt);
        List<int[]> happen = new ArrayList<>(chunks.subList(erledigt, bis));
        erledigt = bis;
        return happen;
    }
}
