package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockmarken;
import de.freimarken.kern.BlockmarkenVertrag;
import de.freimarken.kern.Blockort;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die Marken IM CHUNK - derselbe Vertrag wie im Arbeitsspeicher, nur mit Bitmustern.
 *
 * <p><b>Das ist die tragende Probe dieses Schrittes.</b> Was hier gerechnet wird, steht
 * danach in der Weltdatei: Welches Bit welchen Block bedeutet. Eine verrutschte
 * Verschiebung traegt die Marke eine Ebene hoeher ein, und im Spiel ist das NICHT zu
 * sehen - beide Antworten sind plausibel. Der geteilte {@link BlockmarkenVertrag} prueft
 * die Nachbarschaft in jeder Achse, die acht Ecken eines Abschnitts und die negativen
 * Koordinaten; die Proben darunter pruefen, was nur die echte Fassung hat: den
 * Schluesselnamen, das genaue Byte und die Zahl der Schreibzuege.
 *
 * <p><b>Der Datenbehaelter besteht ueber Aufrufe hinweg</b> ({@link Serverprobe}), und er
 * KOPIERT bei jedem Lesen und Schreiben - genau wie der echte. Ohne diese Kopie schriebe
 * eine Umsetzung, die das Feld aendert und das {@code set} vergisst, trotzdem richtig, und
 * im Spiel waere die Marke nach dem Neustart weg.
 */
@DisplayName("Blockmarken im Chunk")
class ChunkmarkenTest {

    private static final String WELT = "freimarken:marktstadt";

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    /** Legt die zwei Welten an, die der Vertrag und die Proben hier brauchen. */
    private static Blockmarken frisch() {
        Serverprobe.keineWeltenMehr();
        Serverprobe.welt(WELT);
        Serverprobe.welt("freimarken:hochwald");
        return Chunkmarken.ueberBukkit();
    }

    @Nested
    @DisplayName("Haelt den Blockmarkenvertrag ein")
    class Vertrag extends BlockmarkenVertrag {
        @Override
        protected Blockmarken marken() {
            return frisch();
        }
    }

    private Blockmarken marken;

    @BeforeEach
    void vorher() {
        marken = frisch();
    }

    private static Blockort ort(int x, int y, int z) {
        return new Blockort(WELT, x, y, z);
    }

    /** Das Bitfeld eines Abschnitts, roh aus dem Chunk gelesen. */
    private static byte[] feldVon(int chunkX, int chunkZ, int abschnitt) {
        return Bukkit.getWorld(NamespacedKey.fromString(WELT))
                .getChunkAt(chunkX, chunkZ)
                .getPersistentDataContainer()
                .get(new NamespacedKey(Chunkmarken.BEREICH,
                        Chunkmarken.VORSILBE + abschnitt), PersistentDataType.BYTE_ARRAY);
    }

    @Test
    @DisplayName("Der Schluessel heisst freimarken:marken_<abschnitt> - und das fuer immer")
    void derSchluesselname() {
        // Er steht in JEDER Weltdatei. Wird er umbenannt, sind alle alten Marken
        // unlesbar - und zwar stumm: Der Server findet den Schluessel nicht und haelt
        // jeden gesetzten Block fuer gewachsenen Boden.
        marken.markiere(ort(7, 71, 13));

        assertNotNull(feldVon(0, 0, 4), "y=71 liegt im Abschnitt 4");
        assertEquals("freimarken", Chunkmarken.BEREICH);
        assertEquals("marken_", Chunkmarken.VORSILBE);
        assertEquals(512, Chunkmarken.FELDGROESSE, "4096 Bloecke, ein Bit je Block");
    }

    @Test
    @DisplayName("Das Bit steht im richtigen Byte - gerechnet und nachgezaehlt")
    void dasRichtigeBit() {
        // x=7, y=71, z=13: Stelle (71 mod 16) * 256 + (13) * 16 + 7 = 7 * 256 + 215 = 2007.
        // Byte 250, Bit 7.
        marken.markiere(ort(7, 71, 13));
        byte[] feld = feldVon(0, 0, 4);

        assertEquals(512, feld.length);
        assertEquals(2007, ort(7, 71, 13).stelleImAbschnitt());
        assertEquals((byte) 0b1000_0000, feld[250], "Byte 250, Bit 7");
        // Und sonst NICHTS: Ein Feld, das sich mit Einsen fuellt, antwortete auf jede
        // Frage ja - und jeder Block waere gesetzt.
        int gesetzte = 0;
        for (byte b : feld) {
            gesetzte += Integer.bitCount(b & 0xFF);
        }
        assertEquals(1, gesetzte, "genau ein Bit im ganzen Abschnitt");
    }

    @Test
    @DisplayName("Ein unberuehrter Abschnitt wird gar nicht angelegt")
    void unberuehrterAbschnittBleibtLeer() {
        // 512 Byte je Abschnitt und 24 Abschnitte je Chunk waeren 12 KB je Chunk, auch
        // dort, wo nie jemand gebaut hat. Angelegt wird er erst mit der ersten Marke.
        assertFalse(marken.istMarkiert(ort(7, 71, 13)));

        assertNull(feldVon(0, 0, 4), "nur Lesen legt nichts an");
    }

    @Test
    @DisplayName("Geschrieben wird nur, wenn sich etwas aendert")
    void keinSchreibzugOhneAenderung() {
        // Jedes set markiert den Chunk als ungespeichert. Ohne diese Schranke liesse ein
        // Spieler, der eine Mauer nachbessert, den Server dauernd schreiben - und im
        // Ergebnis stuende dasselbe Bit.
        marken.markiere(ort(7, 71, 13));
        assertEquals(1, Serverprobe.schreibzuege());

        marken.markiere(ort(7, 71, 13));
        assertEquals(1, Serverprobe.schreibzuege(), "dieselbe Stelle, kein zweiter Zug");

        marken.istMarkiert(ort(7, 71, 13));
        assertEquals(1, Serverprobe.schreibzuege(), "und Lesen schreibt nie");

        marken.markiere(ort(8, 71, 13));
        assertEquals(2, Serverprobe.schreibzuege(), "eine neue Stelle schon");
    }

    @Test
    @DisplayName("Mehrere Positionen EINES Abschnitts kosten einen Schreibzug")
    void einBuendelEinSchreibzug() {
        // Das ist der ganze Grund, aus dem der Vertrag markiereAlle fuehrt: Eine Tuer
        // liegt in einem Abschnitt, und der Standardweg schriebe das 512-Byte-Feld
        // zweimal.
        int neue = marken.markiereAlle(List.of(ort(7, 71, 13), ort(7, 72, 13),
                ort(8, 71, 13)));

        assertEquals(3, neue);
        assertEquals(1, Serverprobe.schreibzuege(), "ein Feld, ein Zug");
        assertTrue(marken.istMarkiert(ort(7, 72, 13)));
    }

    @Test
    @DisplayName("Zwei Abschnitte sind zwei Schluessel und zwei Schreibzuege")
    void zweiAbschnitteZweiSchluessel() {
        marken.markiereAlle(List.of(ort(7, 71, 13), ort(7, 87, 13)));

        assertEquals(2, Serverprobe.schreibzuege());
        assertNotNull(feldVon(0, 0, 4), "Abschnitt 4 fuer y=71");
        assertNotNull(feldVon(0, 0, 5), "Abschnitt 5 fuer y=87");
        assertTrue(marken.istMarkiert(ort(7, 71, 13)));
        assertTrue(marken.istMarkiert(ort(7, 87, 13)));
    }

    @Test
    @DisplayName("Die tiefste Blockschicht liegt im Abschnitt -4")
    void negativerAbschnittsschluessel() {
        // marken_-4 - ein NamespacedKey nimmt den Bindestrich. Waere das nicht so, waere
        // jede Marke unter Null ein Wurf, und zwar mitten im Blockereignis.
        marken.markiere(ort(7, -64, 13));

        assertNotNull(feldVon(0, 0, -4));
        assertTrue(marken.istMarkiert(ort(7, -64, 13)));
    }

    @Test
    @DisplayName("Zwei Chunks halten getrennte Felder")
    void chunksSindGetrennt() {
        marken.markiere(ort(7, 71, 13));
        marken.markiere(ort(23, 71, 13));

        // x=23 liegt im Chunk 1, und die Stelle im Abschnitt ist dieselbe wie bei x=7:
        // 23 mod 16 ist 7. Ohne getrennte Chunks waere die zweite Marke die erste.
        assertEquals(ort(7, 71, 13).stelleImAbschnitt(),
                ort(23, 71, 13).stelleImAbschnitt(), "dieselbe Stelle, anderer Chunk");
        assertNotNull(feldVon(0, 0, 4));
        assertNotNull(feldVon(1, 0, 4));
        assertTrue(marken.istMarkiert(ort(7, 71, 13)));
        assertTrue(marken.istMarkiert(ort(23, 71, 13)));
        assertFalse(marken.istMarkiert(ort(39, 71, 13)), "Chunk 2 ist leer");
    }

    @Test
    @DisplayName("Eine Welt, die es nicht gibt, ist ein Rechenfehler und kein stilles Nein")
    void fehlendeWeltWirft() {
        // Ein stilles false hiesse \"schon markiert\": Teil E zahlte dann nie, und niemand
        // wuesste warum. Aus einem Blockereignis ist der Fall unerreichbar - dort kommt
        // der Ort aus einem Block, der in dieser Welt liegt.
        Blockort nirgendwo = new Blockort("freimarken:gibtesnicht", 0, 0, 0);

        assertThrows(IllegalStateException.class, () -> marken.markiere(nirgendwo));
        assertThrows(IllegalStateException.class, () -> marken.istMarkiert(nirgendwo));
    }

    @Test
    @DisplayName("Ohne Weltsucher gibt es keine Marken")
    void ohneSucher() {
        assertThrows(IllegalArgumentException.class, () -> new Chunkmarken(null));
    }
}
