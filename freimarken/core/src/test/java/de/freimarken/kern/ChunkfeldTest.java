package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Chunkfeld - und vor allem die Zahlen, die Plan Abschnitt 4 nennt.
 *
 * <p>Die Probe hat einen zweiten Zweck: Der Plan behauptet vier Zahlen zur Marktstadt
 * (Radius 1.495, 35.344 Chunks, -94..93 je Achse, 36 Regionsdateien). Sie standen bisher
 * nur im Text. Hier rechnet sie der Rechner nach, und wenn jemand die Rasterweite oder die
 * Kante aendert, fallen sie zusammen auf.
 */
class ChunkfeldTest {

    @Test
    @DisplayName("Die Marktstadt ergibt die Chunks, die Plan 4 nennt")
    void marktstadtWieImPlan() {
        Chunkfeld feld = Chunkfeld.ausStadtgrenze(Stadtgrenze.marktstadt());

        // Grenze 2989 um -3,5: Bloecke -1498 bis 1490 - die Aussenflaechen des
        // umlaufenden Mauer-Weg-Rings.
        assertEquals(-1498, feld.blockVonX());
        assertEquals(1490, feld.blockBisX());

        assertEquals(-94, feld.chunkVonX(), "Plan 4: -94..93 je Achse");
        assertEquals(93, feld.chunkBisX(), "Plan 4: -94..93 je Achse");
        assertEquals(35_344, feld.chunks(), "Plan 4: 35.344 Chunks");
        assertEquals(36, feld.regionenImFeld(), "Plan 4: 36 Regionsdateien - gemeint sind die Regionen IM FELD");
    }

    @Test
    @DisplayName("Ein spawnzentriertes Werkzeug braucht um 32/32 den Radius 1.530")
    void spawnzentriertKostetDenWestring() {
        // Die Zeile aus Plan Abschnitt 4, als Rechnung. Sie ist der Grund, warum der
        // Befehl die Zahlen ausgibt, statt sie dem Betreiber zu ueberlassen: Wer Mitte und
        // Radius vom Spawn nimmt, laesst im Westen 72 Bloecke aus - und das faellt erst
        // auf, wenn ein Spieler hinlaeuft.
        Chunkfeld feld = Chunkfeld.ausStadtgrenze(Stadtgrenze.marktstadt());

        assertEquals(1530, feld.radiusUm(32, 32), "Plan 4 nennt 1.530");
        assertEquals(1494, feld.empfohlenerRadius(), "um -4/-4 liegt es symmetrisch");
        assertEquals(-4, feld.empfohleneMitteX());
        assertEquals(-4, feld.empfohleneMitteZ());
    }

    @Test
    @DisplayName("Kaufmaennisch gerundet waere die Mitte schlechter")
    void gerundeteGrenzmitteIstNichtDieBesteMitte() {
        // Math.round(-3.5) ist in Java -3, nicht -4. Mit -3 kostet der Westen zwei
        // Bloecke Radius mehr - deshalb nimmt empfohleneMitte die Mitte des
        // BLOCKBEREICHS und nicht die gerundete Grenzmitte.
        Chunkfeld feld = Chunkfeld.ausStadtgrenze(Stadtgrenze.marktstadt());

        assertEquals(-3, Math.round(-3.5), "so rundet Java");
        assertEquals(1495, feld.radiusUm(-3, -3));
        assertTrue(feld.empfohlenerRadius() < feld.radiusUm(-3, -3));
    }

    @Test
    @DisplayName("Eine gewoehnliche Welt um 0/0 rechnet glatt")
    void gewoehnlicheWelt() {
        // kornmark, nether und end: Kante 2000 um 0/0.
        Chunkfeld feld = Chunkfeld.ausGrenze(0, 0, 2000);

        assertEquals(-1000, feld.blockVonX());
        assertEquals(999, feld.blockBisX(), "der Block bei +1000 liegt schon draussen");
        assertEquals(-63, feld.chunkVonX());
        assertEquals(62, feld.chunkBisX());
        assertEquals(126L * 126L, feld.chunks());
        assertEquals(1000, feld.empfohlenerRadius());
    }

    @Test
    @DisplayName("Die Kante der Freistaedte ergibt die groesste Welt")
    void freistaedte() {
        Chunkfeld feld = Chunkfeld.ausGrenze(0, 0, 6000);

        assertEquals(376L * 376L, feld.chunks());
        assertEquals(144, feld.regionenImFeld());
    }

    @Test
    @DisplayName("Nach aussen gerundet, nie nach innen")
    void immerNachAussen() {
        // Eine ungerade Kante um einen ganzzahligen Mittelpunkt geht nicht glatt auf. Dann
        // ist ein Block zu viel richtig: Er kostet Platte, waehrend ein Block zu wenig den
        // Rand kostet - und der Rand ist das, was ein Spieler als Erstes sieht.
        Chunkfeld feld = Chunkfeld.ausGrenze(0, 0, 801);

        assertEquals(-401, feld.blockVonX());
        assertEquals(400, feld.blockBisX());
        assertEquals(802, feld.blockBisX() - feld.blockVonX() + 1, "ein Block Zugabe");
    }

    @Test
    @DisplayName("Beide Achsen werden getrennt gerechnet")
    void achsenGetrennt() {
        // WorldBorder.setCenter nimmt zwei Werte. Eine Klasse, die eine gemeinsame Mitte
        // voraussetzt, gibt irgendwann eine falsche Antwort, ohne dass es auffaellt.
        Chunkfeld feld = Chunkfeld.ausGrenze(1000, -500, 320);

        assertEquals(840, feld.blockVonX());
        assertEquals(1159, feld.blockBisX());
        assertEquals(-660, feld.blockVonZ());
        assertEquals(-341, feld.blockBisZ());
        assertEquals(1000, feld.empfohleneMitteX() + 1, "Mitte des Blockbereichs: 999");
        assertEquals(-501, feld.empfohleneMitteZ());
    }

    @Test
    @DisplayName("Die Stichprobe hat neun Punkte, und die Ecken sind dabei")
    void stichprobeMitEcken() {
        // Die Ecken sind der Zweck: Ein Werkzeug, das einen KREIS erzeugt statt eines
        // Quadrats, sieht in der Mitte und an den Kantenmitten fertig aus. Bei Chunky ist
        // das eine Einstellung und damit ein Vertipper weit.
        Chunkfeld feld = Chunkfeld.ausStadtgrenze(Stadtgrenze.marktstadt());
        var punkte = feld.stichprobe();

        assertEquals(9, punkte.size());
        assertEquals(4, punkte.stream().filter(p -> p.name().startsWith("Ecke")).count());
        assertTrue(punkte.stream().anyMatch(p -> p.chunkX() == -94 && p.chunkZ() == -94),
                "die Nordwestecke ist der aeusserste Chunk");
        assertTrue(punkte.stream().allMatch(p -> p.chunkX() >= feld.chunkVonX()
                        && p.chunkX() <= feld.chunkBisX()
                        && p.chunkZ() >= feld.chunkVonZ()
                        && p.chunkZ() <= feld.chunkBisZ()),
                "kein Punkt liegt ausserhalb des Feldes");
    }

    @Test
    @DisplayName("Ein Feld ohne Ausdehnung ist keins")
    void keinFeldOhneAusdehnung() {
        assertThrows(IllegalArgumentException.class, () -> new Chunkfeld(10, 9, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Chunkfeld.ausGrenze(0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Chunkfeld.ausGrenze(0, 0, Double.NaN));
    }
}
