package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Wolkenplan;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Blocktabelle von Wolkenwerder - aus welchem Stoff die beiden Inseln sind.
 *
 * <p>Ohne Server pruefbar, und das ist hier noch wichtiger als bei {@link InselbodenTest}:
 * Diese Tabelle hat von Anfang an ZWEI Schreiber - der Weltgenerator backt das
 * Ankunftsfeld, und das Plugin setzt jede Startinsel beim Nehmen und beim Zuruecksetzen.
 * Liefen sie auseinander, saehe eine zurueckgesetzte Insel anders aus als eine neue.
 */
@DisplayName("Die Blocktabelle von Wolkenwerder")
class WolkenbodenTest {

    private final Wolkenplan plan = Wolkenplan.wolkenwerder();

    @Test
    @DisplayName("Die Wiese liegt auf 64, die Insel reicht von 62 bis 71")
    void dieHoehen() {
        // 64 ist die einzige Zahl dieser Welt ohne Zwang: Es gibt weder Wasserlinie noch
        // Gelaende, an die sie gebunden waere. Gewaehlt ist die Mitte - Platz fuer einen
        // Turm nach oben und fuer etwas Haengendes nach unten.
        assertEquals(64, Wolkenboden.BODEN);
        assertEquals(62, Wolkenboden.untersteHoehe(plan), "zwei Schichten Erde darunter");
        assertEquals(71, Wolkenboden.obersteHoehe(plan), "die Spitze des Baumes");

        // Die Spanne ist abgeleitet und keine eigene Zahl: Wer den Baum hoeher macht,
        // bekommt den Generator von selbst mit.
        assertEquals(Wolkenboden.BODEN + plan.hoeheUeberWiese(),
                Wolkenboden.obersteHoehe(plan));
        assertEquals(Wolkenboden.BODEN - plan.tiefeUnterWiese(),
                Wolkenboden.untersteHoehe(plan));
    }

    @Test
    @DisplayName("Die Startinsel ist Gras, Erde, Eiche und eine Truhe")
    void dieStartinsel() {
        assertEquals(Material.GRASS_BLOCK, block(Wolkenplan.Insel.START,
                Wolkenplan.Stueck.WIESE));
        assertEquals(Material.DIRT, block(Wolkenplan.Insel.START,
                Wolkenplan.Stueck.ERDE));
        assertEquals(Material.OAK_LOG, block(Wolkenplan.Insel.START,
                Wolkenplan.Stueck.STAMM));
        assertEquals(Material.OAK_LEAVES, block(Wolkenplan.Insel.START,
                Wolkenplan.Stueck.LAUB));
        assertEquals(Material.CHEST, block(Wolkenplan.Insel.START,
                Wolkenplan.Stueck.TRUHE));
        assertEquals(Material.AIR, block(Wolkenplan.Insel.START,
                Wolkenplan.Stueck.NICHTS));
    }

    @Test
    @DisplayName("Eiche und kein anderes Holz - ihr Laub gibt Setzlinge UND Aepfel")
    void eicheHatEinenGrund() {
        // Der Setzling ist der zweite Baum, der Apfel das erste Essen. Mit Birke oder
        // Fichte begaenne das Spiel mit Hunger und ohne Weg hinaus.
        assertEquals(Material.OAK_LEAVES, block(Wolkenplan.Insel.START,
                Wolkenplan.Stueck.LAUB));
        assertNotEquals(Material.BIRCH_LEAVES, block(Wolkenplan.Insel.START,
                Wolkenplan.Stueck.LAUB));
        assertNotEquals(Material.SPRUCE_LEAVES, block(Wolkenplan.Insel.START,
                Wolkenplan.Stueck.LAUB));
    }

    @Test
    @DisplayName("Das Ankunftsfeld ist Stein - und sagt damit: das ist keine Insel")
    void dasAnkunftsfeld() {
        assertEquals(Material.STONE_BRICKS, block(Wolkenplan.Insel.ANKUNFT,
                Wolkenplan.Stueck.WIESE));
        assertEquals(Material.STONE, block(Wolkenplan.Insel.ANKUNFT,
                Wolkenplan.Stueck.ERDE));
        assertEquals(Material.STONE_BRICK_WALL, block(Wolkenplan.Insel.ANKUNFT,
                Wolkenplan.Stueck.GELAENDER));
        assertEquals(Stadtboden.oberflaeche(de.freimarken.kern.Rasterplan.Feld.MAUER),
                block(Wolkenplan.Insel.ANKUNFT, Wolkenplan.Stueck.WIESE),
                "dasselbe Material wie die Mauern der Marktstadt - was in Freimarken"
                        + " gebaut und nicht gewachsen ist, sieht gleich aus");
    }

    @Test
    @DisplayName("Was es auf einer der beiden Inseln nicht gibt, ist LUFT und kein Wurf")
    void unmoeglichesIstLuft() {
        // Der Plan fragt diese Paare nie - er gibt auf dem Ankunftsfeld keine Truhe und
        // auf der Startinsel kein Gelaender. Ein Wurf dafuer waere ein Riegel, den nichts
        // ausloest, und der ist gruen mit UND ohne.
        assertEquals(Material.AIR, block(Wolkenplan.Insel.ANKUNFT,
                Wolkenplan.Stueck.TRUHE));
        assertEquals(Material.AIR, block(Wolkenplan.Insel.ANKUNFT,
                Wolkenplan.Stueck.STAMM));
        assertEquals(Material.AIR, block(Wolkenplan.Insel.ANKUNFT,
                Wolkenplan.Stueck.LAUB));
        assertEquals(Material.AIR, block(Wolkenplan.Insel.START,
                Wolkenplan.Stueck.GELAENDER));
    }

    @Test
    @DisplayName("Jedes Stueck hat auf beiden Inseln eine Antwort")
    void jedesStueckHatEineAntwort() {
        // Die Tafel gegen die Luecke: Ein neues Stueck im Plan ohne Zeile hier waere ein
        // Block, den der Verzweig nicht kennt - und der uebersetzt sich dann nicht. Diese
        // Probe sagt dasselbe noch einmal fuer den Fall, dass jemand ein default
        // hinzufuegt.
        for (Wolkenplan.Insel insel : Wolkenplan.Insel.values()) {
            for (Wolkenplan.Stueck stueck : Wolkenplan.Stueck.values()) {
                assertTrue(block(insel, stueck) != null,
                        insel + "/" + stueck + " hat keine Antwort");
            }
        }
    }

    private static Material block(Wolkenplan.Insel insel, Wolkenplan.Stueck stueck) {
        return Wolkenboden.block(insel, stueck);
    }
}
