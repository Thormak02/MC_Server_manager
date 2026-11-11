package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.freimarken.kern.Stadtfeldplan;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Blocktabelle der Freistaedte - was auf, ueber und unter einem Feld liegt.
 *
 * <p>Ohne Server pruefbar, und das ist der Grund, warum die Tabelle eine eigene Klasse
 * ist: Im Generator steckend waere sie nur im Spiel zu sehen - also erst, wenn eine Welt
 * von 140.000 Chunks schon erzeugt ist und niemand sie noch einmal erzeugen will.
 */
@DisplayName("Die Blocktabelle der Freistaedte")
class FeldbodenTest {

    /** Eine Weltunterkante wie die echte - tief genug, dass Erde und Stein Platz haben. */
    private static final int UNTEN = -64;

    @Test
    @DisplayName("Die Spalte eines Stadtfeldes: Grundgestein, Stein, Erde, Gras")
    void spalteEinesFeldes() {
        var feld = Stadtfeldplan.Feld.STADTFELD;

        assertEquals(Material.BEDROCK, Feldboden.darunter(feld, UNTEN, UNTEN),
                "auf der Weltunterkante Grundgestein - sonst faellt man aus der Welt");
        assertEquals(Material.STONE, Feldboden.darunter(feld, UNTEN + 1, UNTEN));
        assertEquals(Material.STONE, Feldboden.darunter(feld, Feldboden.ERDE_AB - 1, UNTEN));
        assertEquals(Material.DIRT, Feldboden.darunter(feld, Feldboden.ERDE_AB, UNTEN),
                "ab hier Erde");
        assertEquals(Material.DIRT, Feldboden.darunter(feld, Feldboden.BODEN - 1, UNTEN));
        assertEquals(Material.GRASS_BLOCK, Feldboden.oberflaeche(feld));
    }

    @Test
    @DisplayName("Die Barriere reicht bis zur Weltunterkante - sonst graebt man darunter")
    void dieWandHatKeinLochDarunter() {
        // Vorher war hier Luft, mit der Begruendung, eine Saeule unter jeder Wand waere
        // ein Rundweg um die Stadt. Nur ist die Barriere kein Weg: Auf ihr steht
        // niemand. Die Luft dagegen WAR ein Loch - wer am Feldrand einen Block tief
        // graebt und einen Schritt zur Seite geht, stand unter der Wand in der Leere.
        var barriere = Stadtfeldplan.Feld.BARRIERE;

        assertEquals(Material.BARRIER, Feldboden.darunter(barriere, UNTEN, UNTEN),
                "schon auf der Weltunterkante - und dort KEIN Grundgestein, sonst"
                        + " liefe ein begehbarer Steg um jede Stadt");
        assertEquals(Material.BARRIER, Feldboden.darunter(barriere, Feldboden.BODEN - 1, UNTEN));
        assertEquals(Material.BARRIER, Feldboden.oberflaeche(barriere));
    }

    @Test
    @DisplayName("In der Leere ist NICHTS - auf keiner Hoehe")
    void leereIstLeer() {
        var leere = Stadtfeldplan.Feld.LEERE;

        assertEquals(Material.AIR, Feldboden.darunter(leere, UNTEN, UNTEN));
        assertEquals(Material.AIR, Feldboden.darunter(leere, Feldboden.ERDE_AB, UNTEN));
        assertEquals(Material.AIR, Feldboden.oberflaeche(leere));
    }

    @Test
    @DisplayName("Vier Bloecke ueber der LAUFFLAECHE - nicht ueber dem Bodenblock")
    void wandhoehe() {
        // Gegen Laufen genuegt einer, gegen Springen zwei, gegen einen Sprung mit Trank
        // und Schwung vier. Gezaehlt wird ab dem Block, in dem der Spieler STEHT - und
        // der liegt einen ueber dem Gras. Vorher stand hier assertEquals(4,
        // BARRIERE_HOCH): Das schrieb die Deklaration ab und war darum auch dann gruen,
        // als die Wand in Wahrheit nur drei Bloecke ueber dem Kopf hatte.
        int laufflaeche = Feldboden.BODEN + 1;

        assertEquals(4, Feldboden.wandBis() - laufflaeche,
                "so viele Bloecke ueber dem Fuss des Spielers sind zu");
    }

    @Test
    @DisplayName("Drei Schichten Erde unter dem Gras - gezaehlt, nicht abgeschrieben")
    void dreiSchichtenErde() {
        // Gezaehlt und nicht ERDE_AB == BODEN - 3 gefragt: Jene Form haette dieselbe
        // Rechnung zweimal hingeschrieben und sich selbst bestaetigt.
        int erde = 0;
        for (int y = UNTEN; y < Feldboden.BODEN; y++) {
            if (Feldboden.darunter(Stadtfeldplan.Feld.STADTFELD, y, UNTEN) == Material.DIRT) {
                erde++;
            }
        }

        assertEquals(3, erde, "wie in der Marktstadt");
    }
}
