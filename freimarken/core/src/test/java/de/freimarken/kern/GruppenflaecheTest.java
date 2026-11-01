package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Welche Bloecke eine Gruppe zu fuellen hat - und welche nicht.
 *
 * <p>Die Zahlen hier sind vorgerechnet und nicht abgelesen. Bei Raster 64 + 7 ist das Band
 * zwischen zwei Zellen 7 Bloecke breit und 64 lang, also 448 Bloecke; davon sind 2 x 64
 * Mauer und 5 x 64 Weg. Eine Kreuzung ist 7 x 7 = 49.
 */
class GruppenflaecheTest {

    private static final String WELT = "freimarken:marktstadt";

    private final Rasterplan plan = Rasterplan.marktstadt();
    private final UUID anna = UUID.randomUUID();

    private Parzelle feld(int x, int z) {
        return new Parzelle(1L, WELT, x, z, anna, Set.of(), 0L, Instant.EPOCH,
                Art.PARZELLE, 7L, Zustand.AKTIV);
    }

    private Gruppenflaeche flaeche(int... xz) {
        List<Parzelle> felder = new ArrayList<>();
        for (int i = 0; i < xz.length; i += 2) {
            felder.add(feld(xz[i], xz[i + 1]));
        }
        return Gruppenflaeche.aus(felder);
    }

    /** Alle Fugen der Flaeche, als Liste von "x/z/Feld". */
    private List<String> fugen(Gruppenflaeche flaeche) {
        List<String> gefunden = new ArrayList<>();
        flaeche.ueberDieFugen(plan, (x, z, feld) -> gefunden.add(x + "/" + z + "/" + feld));
        return gefunden;
    }

    private long davon(List<String> fugen, Rasterplan.Feld feld) {
        return fugen.stream().filter(eintrag -> eintrag.endsWith("/" + feld)).count();
    }

    @Test
    @DisplayName("Ein einzelnes Feld hat keine Fuge")
    void einFeldHatKeineFuge() {
        // Nicht etwa den eigenen Rand: Jenseits des Bandes liegt eine Zelle, die nicht
        // dazugehoert. Ein Feld allein aendert in der Welt also gar nichts - und genau
        // deshalb ist es keine Gruppe.
        assertEquals(List.of(), fugen(flaeche(0, 0)));
        assertEquals(1, flaeche(0, 0).felder());
    }

    @Test
    @DisplayName("Zwei Felder nebeneinander ergeben ein Band aus 448 Bloecken")
    void zweiFelderEinBand() {
        List<String> fugen = fugen(flaeche(0, 0, 1, 0));

        assertEquals(448, fugen.size(), "7 breit, 64 lang");
        assertEquals(128, davon(fugen, Rasterplan.Feld.MAUER), "zwei Mauerreihen");
        assertEquals(320, davon(fugen, Rasterplan.Feld.WEG), "fuenf Wegreihen");
        assertEquals(0, davon(fugen, Rasterplan.Feld.PARZELLE), "Bauland ist keine Fuge");
    }

    @Test
    @DisplayName("Das Band liegt genau zwischen den beiden Feldern")
    void bandLiegtDazwischen() {
        for (String fuge : fugen(flaeche(0, 0, 1, 0))) {
            int x = Integer.parseInt(fuge.split("/")[0]);
            int z = Integer.parseInt(fuge.split("/")[1]);
            assertTrue(x >= 64 && x <= 70, "x gehoert ins Band, nicht " + x);
            assertTrue(z >= 0 && z <= 63, "z bleibt auf Baulandhoehe, nicht " + z);
        }
    }

    @Test
    @DisplayName("Bei einer L-Form bleibt die Kreuzung aussen")
    void lFormLaesstDieKreuzungAus() {
        // 0/0, 1/0 und 1/1: An der Kreuzung bei 64..70 / 64..70 liegt auch die Zelle 0/1,
        // und die gehoert jemand anderem. Zwei Baender, keine Kreuzung.
        List<String> fugen = fugen(flaeche(0, 0, 1, 0, 1, 1));

        assertEquals(2 * 448, fugen.size());
        assertFalse(fugen.contains("67/67/WEG"), "die Kreuzung bleibt Weg");
    }

    @Test
    @DisplayName("Vier Felder im Quadrat nehmen die Kreuzung mit: 1841 Bloecke")
    void quadratNimmtDieKreuzungMit() {
        List<String> fugen = fugen(flaeche(0, 0, 1, 0, 0, 1, 1, 1));

        assertEquals(4 * 448 + 49, fugen.size(), "vier Baender und eine Kreuzung");
        assertTrue(fugen.contains("67/67/WEG"));
        // In der Kreuzung ist nur Mauer, wo BEIDE Achsen Mauer sind - sonst gewinnt der
        // Weg, damit sich Wege kreuzen koennen.
        assertEquals(4 * 128 + 4, davon(fugen, Rasterplan.Feld.MAUER));
    }

    @Test
    @DisplayName("Auf der negativen Seite stimmt die Flaeche auch")
    void negativeSeite() {
        // Zelle -21 beginnt bei -1491 und hat ihr Band bei -1427 bis -1421. Ohne die
        // Vorzeichenmaske im Zellschluessel waere -21/-21 dieselbe Zelle wie -20/-21,
        // und die Flaeche haette nur ein Feld.
        Gruppenflaeche zwei = flaeche(-21, -21, -20, -21);
        assertEquals(2, zwei.felder());

        List<String> fugen = fugen(zwei);
        assertEquals(448, fugen.size());
        for (String fuge : fugen) {
            int x = Integer.parseInt(fuge.split("/")[0]);
            assertTrue(x >= -1427 && x <= -1421, "x gehoert ins Band, nicht " + x);
        }
    }

    @Test
    @DisplayName("Gehoert sagt nur bei den eigenen Zellen ja")
    void gehoert() {
        Gruppenflaeche zwei = flaeche(0, 0, 1, 0);
        assertTrue(zwei.gehoert(0, 0));
        assertTrue(zwei.gehoert(1, 0));
        assertFalse(zwei.gehoert(0, 1));
        assertFalse(zwei.gehoert(-1, 0));
        assertFalse(zwei.gehoert(0, -1), "nicht dieselbe Zelle wie 0/0");
    }

    @Test
    @DisplayName("Eine Flaeche ohne Felder gibt es nicht")
    void ohneFelderLaut() {
        // Lieber ein Abbruch als eine leere Flaeche: Beim Zurueckbau heisst "keine Fuge"
        // sonst "nichts zu tun", und der Weg bliebe fuer immer Bauland.
        assertThrows(IllegalArgumentException.class, () -> Gruppenflaeche.aus(List.of()));
    }
}
