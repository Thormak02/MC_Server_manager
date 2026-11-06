package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Rechnung von der Weltkoordinate zum Platz im Chunkabschnitt.
 *
 * <p>Diese Rechnung steht in der WELTDATEI: Welches Bit welchen Block bedeutet, entscheidet
 * sie, und eine Aenderung daran liest alle alten Marken an falschen Stellen. Darum ist sie
 * hier Zahl fuer Zahl festgenagelt und nicht nur an ihrer Wirkung geprueft.
 */
class BlockortTest {

    private static final String WELT = "freimarken:marktstadt";

    private static Blockort ort(int x, int y, int z) {
        return new Blockort(WELT, x, y, z);
    }

    @Test
    @DisplayName("Ein Chunk ist 16 mal 16 - und floorDiv rechnet auch unter Null richtig")
    void chunkrechnung() {
        assertEquals(0, ort(0, 0, 0).chunkX());
        assertEquals(0, ort(15, 0, 15).chunkZ());
        assertEquals(1, ort(16, 0, 0).chunkX());
        // Der Fall, den eine Division mit / falsch rechnet: -1 / 16 ist 0, und damit
        // laegen der Block -1 und der Block 0 im selben Chunk.
        assertEquals(-1, ort(-1, 0, 0).chunkX());
        assertEquals(-1, ort(-16, 0, 0).chunkX());
        assertEquals(-2, ort(-17, 0, 0).chunkX());
        assertEquals(-1, ort(0, 0, -1).chunkZ());
    }

    @Test
    @DisplayName("Ein Abschnitt ist 16 hoch - die Welt beginnt bei -64, also Abschnitt -4")
    void abschnittsrechnung() {
        assertEquals(0, ort(0, 0, 0).abschnittY());
        assertEquals(0, ort(0, 15, 0).abschnittY());
        assertEquals(1, ort(0, 16, 0).abschnittY());
        assertEquals(4, ort(0, 71, 0).abschnittY(), "Bauhoehe der Marktstadt");
        // Die tiefste Blockschicht der Welt. Mit / statt floorDiv waere das Abschnitt 0,
        // also derselbe wie fuer y=0 - eine Marke entwertete dann zwei Bloecke.
        assertEquals(-4, ort(0, -64, 0).abschnittY());
        assertEquals(-1, ort(0, -1, 0).abschnittY());
        assertEquals(-1, ort(0, -16, 0).abschnittY());
        assertEquals(-2, ort(0, -17, 0).abschnittY());
        assertEquals(23, ort(0, 383, 0).abschnittY(), "oberste Bauhoehe");
    }

    @Test
    @DisplayName("Die Stelle im Abschnitt ist eine Zahl von 0 bis 4095")
    void stelleImAbschnitt() {
        assertEquals(0, ort(0, 0, 0).stelleImAbschnitt());
        assertEquals(1, ort(1, 0, 0).stelleImAbschnitt());
        assertEquals(16, ort(0, 0, 1).stelleImAbschnitt());
        assertEquals(256, ort(0, 1, 0).stelleImAbschnitt());
        assertEquals(4095, ort(15, 15, 15).stelleImAbschnitt());
        assertEquals(Blockort.BLOECKE_JE_ABSCHNITT - 1, ort(15, 15, 15).stelleImAbschnitt());
        // y oben, dann z, dann x - die Reihenfolge, die Minecraft selbst benutzt. Sie ist
        // frei waehlbar, muss aber fuer immer bleiben.
        assertEquals((7 << 8) | (13 << 4) | 5, ort(5, 7, 13).stelleImAbschnitt());
    }

    @Test
    @DisplayName("Jeder Block eines Abschnitts hat eine EIGENE Stelle - alle 4096")
    void jederBlockEineEigeneStelle() {
        // Die Probe, die eine verrutschte Verschiebung faengt: Mit (y << 4) statt (y << 8)
        // fielen Bloecke zusammen, und zwei Positionen teilten eine Marke. Gezaehlt wird
        // mit einem Bitfeld, damit die Probe keine 4096 Objekte baut.
        boolean[] gesehen = new boolean[Blockort.BLOECKE_JE_ABSCHNITT];
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int stelle = ort(x, y, z).stelleImAbschnitt();
                    assertTrue(stelle >= 0 && stelle < Blockort.BLOECKE_JE_ABSCHNITT,
                            "ausserhalb: " + stelle);
                    assertTrue(!gesehen[stelle], "zwei Bloecke auf Stelle " + stelle);
                    gesehen[stelle] = true;
                }
            }
        }
    }

    @Test
    @DisplayName("Negative Koordinaten liegen im Abschnitt an derselben Stelle wie positive")
    void stelleIstImmerPositiv() {
        // floorMod und nicht %: -1 % 16 ist -1, und ein negativer Index greift neben das
        // Bitfeld. Das waere kein Rechenfehler, sondern eine Ausnahme mitten im
        // Blockereignis.
        assertEquals(15, ort(-1, 0, 0).stelleImAbschnitt());
        assertEquals(15 << 4, ort(0, 0, -1).stelleImAbschnitt());
        assertEquals(15 << 8, ort(0, -1, 0).stelleImAbschnitt());
        assertEquals(0, ort(-16, -16, -16).stelleImAbschnitt());
        assertEquals(4095, ort(-17, -17, -17).stelleImAbschnitt());
    }

    @Test
    @DisplayName("Ein Blockort ist ein WERT - gleiche Zahlen sind derselbe Ort")
    void ortIstEinWert() {
        assertEquals(ort(7, 71, 13), ort(7, 71, 13));
        assertEquals(ort(7, 71, 13).hashCode(), ort(7, 71, 13).hashCode());
        assertNotEquals(ort(7, 71, 13), ort(7, 71, 14));
        assertNotEquals(ort(7, 71, 13), new Blockort("freimarken:hochwald", 7, 71, 13));
    }

    @Test
    @DisplayName("Ohne Welt gibt es keinen Blockort")
    void weltIstPflicht() {
        assertThrows(IllegalArgumentException.class, () -> new Blockort(null, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Blockort("", 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Blockort("  ", 0, 0, 0));
    }

    @Test
    @DisplayName("Der Weltschluessel wird NICHT zurechtgebogen")
    void weltBleibtWieSieKommt() {
        // Er kommt aus World.key().asString() und ist dort schon kleingeschrieben. Ein
        // zweiter Ort, der ihn zurechtbiegt, biegt ihn eines Tages anders - und dann
        // liegt die Marke in einer Welt, die niemand fragt.
        assertEquals(" Freimarken:Marktstadt ",
                new Blockort(" Freimarken:Marktstadt ", 0, 0, 0).welt());
    }
}
