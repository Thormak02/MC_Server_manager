package de.freimarken.kern;

import static de.freimarken.kern.Rasterplan.Feld.MAUER;
import static de.freimarken.kern.Rasterplan.Feld.PARZELLE;
import static de.freimarken.kern.Rasterplan.Feld.WEG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Raster ist die Grundlage von allem - Weltgenerator, Besitzpruefung und
 * Zuruecksetzen fragen denselben Plan. Ein Fehler hier faellt nicht auf, er wird gebaut.
 */
class RasterplanTest {

    private final Rasterplan plan = Rasterplan.marktstadt();

    @Test
    @DisplayName("Die Marktstadt hat 64er Parzellen und 7 Bloecke Weg")
    void masze() {
        assertEquals(64, plan.parzellenKante());
        assertEquals(7, plan.wegBreite());
        assertEquals(71, plan.rasterWeite());
        assertEquals(4096L, plan.flaeche());
    }

    @Test
    @DisplayName("Die Parzelle ist volle 64x64 - die Mauer steht auf dem Weg")
    void parzelleIstVoll() {
        assertEquals(PARZELLE, plan.feldBei(0, 0));
        assertEquals(PARZELLE, plan.feldBei(63, 63));
        assertEquals(PARZELLE, plan.feldBei(0, 63));
        assertEquals(PARZELLE, plan.feldBei(63, 0));
        // Erst dahinter beginnt die Umrandung.
        assertEquals(MAUER, plan.feldBei(64, 0));
        assertEquals(MAUER, plan.feldBei(0, 64));
    }

    @Test
    @DisplayName("Zwischen zwei Parzellen stehen zwei Mauern und fuenf Felder Weg")
    void wegAufbau() {
        assertEquals(MAUER, plan.feldBei(64, 10));
        for (int x = 65; x <= 69; x++) {
            assertEquals(WEG, plan.feldBei(x, 10), "x=" + x + " sollte Weg sein");
        }
        assertEquals(MAUER, plan.feldBei(70, 10));
        assertEquals(PARZELLE, plan.feldBei(71, 10), "Dahinter faengt die naechste an");
    }

    @Test
    @DisplayName("Wege kreuzen sich - an der Kreuzung steht keine Mauer im Weg")
    void wegeKreuzen() {
        assertEquals(WEG, plan.feldBei(67, 67));
        // Wo ein Weg eine Mauerlinie quert, gewinnt der Weg. Sonst stuende man an jeder
        // Kreuzung vor einem Pfosten.
        assertEquals(WEG, plan.feldBei(67, 64));
        assertEquals(WEG, plan.feldBei(64, 67));
        // Die Ecke zweier Mauerlinien bleibt Mauer.
        assertEquals(MAUER, plan.feldBei(64, 64));
        assertEquals(MAUER, plan.feldBei(70, 70));
    }

    @Test
    @DisplayName("Negative Koordinaten sind kein Sonderfall")
    void negativeKoordinaten() {
        // -1 liegt im Raster bei 70, also auf der Mauer der Parzelle (-1,-1).
        assertEquals(MAUER, plan.feldBei(-1, -1));
        assertEquals(PARZELLE, plan.feldBei(-71, -71));
        assertEquals(PARZELLE, plan.feldBei(-8, -8));
        assertEquals(-1, plan.parzelleX(-1));
        assertEquals(-1, plan.parzelleX(-71));
        assertEquals(-2, plan.parzelleX(-72));
    }

    @Test
    @DisplayName("Parzellennummer und Ursprung passen zusammen")
    void nummerierung() {
        assertEquals(0, plan.parzelleX(0));
        assertEquals(0, plan.parzelleX(70));
        assertEquals(1, plan.parzelleX(71));
        assertEquals(0, plan.ursprungX(0));
        assertEquals(71, plan.ursprungX(1));
        assertEquals(-71, plan.ursprungX(-1));
        // Die Mitte liegt in der Parzelle, nicht auf der Mauer.
        assertEquals(PARZELLE, plan.feldBei(plan.mitteX(0), plan.mitteZ(0)));
        assertEquals(PARZELLE, plan.feldBei(plan.mitteX(3), plan.mitteZ(-2)));
    }

    @Test
    @DisplayName("Jede Koordinate der Parzelle 0 gehoert auch zu Parzelle 0")
    void parzelleIstGeschlossen() {
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                assertEquals(PARZELLE, plan.feldBei(x, z), x + "/" + z);
                assertEquals(0, plan.parzelleX(x));
                assertEquals(0, plan.parzelleZ(z));
            }
        }
    }

    @Test
    @DisplayName("Unsinnige Masze werden abgewiesen, nicht stillschweigend gebogen")
    void unsinnigeMasze() {
        assertThrows(IllegalArgumentException.class, () -> new Rasterplan(0, 7));
        assertThrows(IllegalArgumentException.class, () -> new Rasterplan(64, 1));
    }
}
