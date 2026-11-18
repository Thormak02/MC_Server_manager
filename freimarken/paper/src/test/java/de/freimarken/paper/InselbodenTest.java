package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.freimarken.kern.Inselplan;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Blocktabelle von Hafenwerder - was in einer Spalte uebereinander liegt.
 *
 * <p>Ohne Server pruefbar, und das ist der Grund, warum die Tabelle eine eigene Klasse
 * ist: Im Generator steckend waere sie nur im Spiel zu sehen - also erst, wenn eine Welt
 * von 90.000 Chunks schon erzeugt ist und niemand sie noch einmal erzeugen will.
 */
@DisplayName("Die Blocktabelle von Hafenwerder")
class InselbodenTest {

    /** Eine Weltunterkante wie die echte - tief genug, dass Erde und Stein Platz haben. */
    private static final int UNTEN = -64;

    private final Inselplan plan = Inselplan.hafenwerder();

    @Test
    @DisplayName("Die Wasserlinie liegt auf 62, die Wiese einen Block darueber")
    void dieWasserlinie() {
        // Die einzige Hoehe, die aus der Reihe der anderen Welten faellt, und das ist der
        // Grund: Eine Insel, deren Gras auf der Wasserlinie liegt, ist keine Insel,
        // sondern eine Untiefe.
        assertEquals(63, Inselboden.BODEN);
        assertEquals(62, Inselboden.MEERESSPIEGEL);
        assertNotEquals(Feldboden.BODEN, Inselboden.BODEN,
                "hier liegt der Boden hoeher als auf einem Stadtfeld - wegen des Meeres");
    }

    @Test
    @DisplayName("Der Meeresgrund folgt aus der Insel und ist keine eigene Zahl")
    void derMeeresgrundIstAbgeleitet() {
        // Der Hang faellt um einen Block, sooft er einen Block nach aussen geht; wo er
        // den Rand des eigenen Feldes erreicht, ist der Grund. Eine zweite Zahl daneben
        // waere entweder eine senkrechte Riffwand oder ein Fuss, der ins fremde Wasser
        // laeuft.
        assertEquals(55, Inselboden.meeresgrund(plan));
        assertEquals(7, Inselboden.MEERESSPIEGEL - Inselboden.meeresgrund(plan),
                "sieben Bloecke Wasser ueber dem Grund");
    }

    @Test
    @DisplayName("Die Spalte einer Insel: Grundgestein, Stein, Erde, Gras")
    void spalteDerInsel() {
        int gipfel = Inselboden.BODEN;

        assertEquals(Material.BEDROCK, Inselboden.darunter(UNTEN, gipfel, UNTEN),
                "auf der Weltunterkante Grundgestein - sonst faellt man aus der Welt");
        assertEquals(Material.STONE, Inselboden.darunter(UNTEN + 1, gipfel, UNTEN));
        assertEquals(Material.STONE, Inselboden.darunter(gipfel - 4, gipfel, UNTEN));
        assertEquals(Material.DIRT, Inselboden.darunter(gipfel - 3, gipfel, UNTEN),
                "ab hier Erde");
        assertEquals(Material.DIRT, Inselboden.darunter(gipfel - 1, gipfel, UNTEN));
        assertEquals(Material.GRASS_BLOCK,
                Inselboden.oberflaeche(Inselplan.Gestalt.LAND, 0));
    }

    @Test
    @DisplayName("Drei Schichten Erde unter dem Gras - gezaehlt, nicht abgeschrieben")
    void dreiSchichtenErde() {
        // Gezaehlt und nicht ERDE_TIEF == 3 gefragt: Jene Form haette dieselbe Rechnung
        // zweimal hingeschrieben und sich selbst bestaetigt.
        int erde = 0;
        for (int y = UNTEN; y < Inselboden.BODEN; y++) {
            if (Inselboden.darunter(y, Inselboden.BODEN, UNTEN) == Material.DIRT) {
                erde++;
            }
        }

        assertEquals(3, erde, "wie in der Marktstadt und auf einem Stadtfeld");
    }

    @Test
    @DisplayName("Die Erde wandert MIT dem Gipfel - auch am Hang unter Wasser")
    void dieErdeWandertMitDemGipfel() {
        // Der Hang ist keine Ausnahme, sondern dieselbe Spalte tiefer gesetzt. Haengte
        // die Erdschicht an einer festen Hoehe, saesse sie am Meeresgrund mitten im
        // Stein und der Grund selbst waere nackter Fels.
        int gipfel = Inselboden.meeresgrund(plan);

        assertEquals(Material.DIRT, Inselboden.darunter(gipfel - 1, gipfel, UNTEN));
        assertEquals(Material.DIRT, Inselboden.darunter(gipfel - 3, gipfel, UNTEN));
        assertEquals(Material.STONE, Inselboden.darunter(gipfel - 4, gipfel, UNTEN));
    }

    @Test
    @DisplayName("Gras nur ueber Wasser - Strand, Hang und Grund sind Sand")
    void grasNurUeberWasser() {
        // Ein Sandstrand, der unter Wasser ploetzlich Stein waere, saehe aus wie eine
        // Baugrube. Darum ist alles, was nicht Wiese ist, Sand - der Strand, der Hang
        // darunter und der Meeresgrund.
        assertEquals(Material.SAND, Inselboden.oberflaeche(Inselplan.Gestalt.STRAND, 0));
        assertEquals(Material.SAND, Inselboden.oberflaeche(Inselplan.Gestalt.WASSER, 3));
        assertEquals(Material.SAND,
                Inselboden.oberflaeche(Inselplan.Gestalt.WASSER, plan.meerTiefe()));
        assertEquals(Material.SAND, Inselboden.oberflaeche(Inselplan.Gestalt.LAND, 1),
                "sogar eine Wiese unter Wasser waere Sand - die Tiefe entscheidet"
                        + " zuerst, und so kann keine gruene Flaeche im Meer entstehen");
    }
}
