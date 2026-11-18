package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Chunks eines Wolkenfeldes - geteilt zwischen Zaehlung und Biomwechsel.
 *
 * <p>Die tragende Zusage ist die VOLLSTAENDIGKEIT: Jeder Block des Feldes muss in genau
 * einem Chunk dieser Liste liegen. Fehlte einer, zaehlte die Punktzahl eine Ecke nicht und
 * der Biomwechsel liesse sie beim alten Biom - und beides sieht man erst im Spiel.
 */
@DisplayName("Die Chunkfolge eines Wolkenfeldes")
class ChunkfolgeTest {

    private static final Wolkenplan PLAN = Wolkenplan.wolkenwerder();

    @Test
    @DisplayName("Ein Feld von 128 Bloecken hat 64 Chunks")
    void vierundsechzigChunks() {
        assertEquals(64, new Chunkfolge(PLAN, 0, 0).chunkzahl());
        assertEquals(64, new Chunkfolge(PLAN, 3, -2).chunkzahl());
    }

    @Test
    @DisplayName("JEDER Block des Feldes liegt in einem Chunk der Liste")
    void jederBlockIstDabei() {
        // DIE TRAGENDE PROBE. Gerechnet wird aus den Blockgrenzen, und diese Probe
        // prueft genau das Ergebnis dieser Rechnung - nicht ihre Formel.
        Chunkfolge folge = new Chunkfolge(PLAN, 2, -3);
        Set<Long> inDerListe = new LinkedHashSet<>();
        for (int[] chunk : folge.naechsterHappen(64)) {
            inDerListe.add((long) chunk[0] << 32 | (chunk[1] & 0xffffffffL));
        }

        int vonX = PLAN.feldAnfang(2);
        int vonZ = PLAN.feldAnfang(-3);
        for (int x = vonX; x < vonX + PLAN.feldKante(); x++) {
            for (int z = vonZ; z < vonZ + PLAN.feldKante(); z++) {
                long schluessel = (long) (x >> 4) << 32 | ((z >> 4) & 0xffffffffL);
                assertTrue(inDerListe.contains(schluessel),
                        "Block " + x + "/" + z + " liegt in Chunk " + (x >> 4) + "/"
                                + (z >> 4) + ", und der steht nicht in der Liste");
            }
        }
    }

    @Test
    @DisplayName("Kein Chunk der Liste liegt AUSSERHALB des Feldes")
    void keinChunkDaneben() {
        // Die Gegenrichtung, und sie ist bei dieser Welt besonders wichtig: Zwischen zwei
        // Feldern liegen 384 Bloecke Leere. Ein Chunk zu viel hiesse, dass die Zaehlung
        // Bloecke aus dem Niemandsland mitzaehlt - oder dass ein Biomwechsel die Leere
        // umsetzt, die jeder sieht.
        Chunkfolge folge = new Chunkfolge(PLAN, 1, 1);
        int vonX = PLAN.feldAnfang(1);
        int bisX = vonX + PLAN.feldKante() - 1;

        for (int[] chunk : folge.naechsterHappen(64)) {
            int ecke = chunk[0] << 4;
            assertTrue(ecke >= vonX && ecke <= bisX,
                    "Chunk " + chunk[0] + " beginnt bei " + ecke + ", das Feld geht von "
                            + vonX + " bis " + bisX);
        }
    }

    @Test
    @DisplayName("Die Happen kommen in FESTER Folge und jeder Chunk genau einmal")
    void festeFolgeOhneDoppel() {
        // Ohne feste Folge haetten zwei Laeufe ueber dieselbe Insel verschiedene
        // Teilsummen, solange dort gebaut wird - und ein doppelter Chunk zaehlte seine
        // Bloecke zweimal.
        List<String> erste = alleChunks(new Chunkfolge(PLAN, 0, 0), 7);
        List<String> zweite = alleChunks(new Chunkfolge(PLAN, 0, 0), 1);

        assertEquals(erste, zweite,
                "die Happengroesse darf die Reihenfolge nicht aendern");
        assertEquals(64, new LinkedHashSet<>(erste).size(), "jeder Chunk genau einmal");
    }

    @Test
    @DisplayName("Der Zeiger laeuft bis zum Ende - und bleibt dort")
    void zeigerLaeuftAb() {
        Chunkfolge folge = new Chunkfolge(PLAN, 0, 0);

        assertFalse(folge.fertig());
        assertEquals(0, folge.fortschrittProzent());
        assertEquals(32, folge.naechsterHappen(32).size());
        assertEquals(50, folge.fortschrittProzent());
        assertEquals(32, folge.naechsterHappen(100).size(),
                "der letzte Happen ist kuerzer, wenn weniger offen sind");
        assertTrue(folge.fertig());
        assertEquals(100, folge.fortschrittProzent());
        assertEquals(List.of(), folge.naechsterHappen(5),
                "danach kommt nichts mehr - und kein Wurf");
    }

    @Test
    @DisplayName("Ein Happen von 0 und ein fehlender Plan sind Programmfehler")
    void riegel() {
        assertThrows(IllegalArgumentException.class,
                () -> new Chunkfolge(PLAN, 0, 0).naechsterHappen(0));
        assertThrows(IllegalArgumentException.class, () -> new Chunkfolge(null, 0, 0));
    }

    private static List<String> alleChunks(Chunkfolge folge, int jeTakt) {
        List<String> namen = new ArrayList<>();
        while (!folge.fertig()) {
            for (int[] chunk : folge.naechsterHappen(jeTakt)) {
                namen.add(chunk[0] + "/" + chunk[1]);
            }
        }
        return namen;
    }
}
