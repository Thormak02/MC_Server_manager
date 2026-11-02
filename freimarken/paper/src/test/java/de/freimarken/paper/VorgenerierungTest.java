package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Chunkfeld;
import de.freimarken.kern.Stadtgrenze;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Chunky-Befehlszeile - ohne Server.
 *
 * <p>Pruefbar, weil die Zeile aus einer Zahl und einem Namen entsteht und nicht aus einer
 * Welt. Das ist der Grund, warum {@code chunkyBefehl} den Namen und den Mittelpunkt
 * uebergeben bekommt, statt sie sich selbst zu holen: Eine Zeile, die der Betreiber
 * kopiert, soll vorher einmal nachgerechnet worden sein.
 */
class VorgenerierungTest {

    @Test
    @DisplayName("Die Marktstadt ergibt genau die Zahlen aus Plan Abschnitt 4")
    void marktstadtWieImPlan() {
        // Plan 4: "Fuer die Marktstadt: Mitte -3,5/-3,5, Radius 1.495, 35.344 Chunks
        // (-94...93 je Achse), 36 Regionsdateien."
        Stadtgrenze grenze = Stadtgrenze.marktstadt();
        Chunkfeld feld = Chunkfeld.ausStadtgrenze(grenze);

        String befehl = Vorgenerierung.chunkyBefehl("marktstadt", feld,
                grenze.mitte(), grenze.mitte());

        assertEquals("/chunky start marktstadt square -3.5 -3.5 1495", befehl);
    }

    @Test
    @DisplayName("Eine ganze Zahl wird ohne Komma geschrieben")
    void ganzeZahlOhneKomma() {
        // kornmark, nether und end liegen um 0/0. "0.0" waere nicht falsch, aber die Zeile
        // soll lesbar sein - sie wird von Hand kopiert.
        Chunkfeld feld = Chunkfeld.ausGrenze(0, 0, 2000);

        assertEquals("/chunky start kornmark square 0 0 1000",
                Vorgenerierung.chunkyBefehl("kornmark", feld, 0, 0));
    }

    @Test
    @DisplayName("Der Punkt bleibt ein Punkt, auch auf einem deutschen System")
    void punktStattKomma() {
        // Chunkys Zahlenleser nimmt keine Dezimalkommas. Eine Formatierung mit
        // Vorgabesprache machte auf diesem Rechner aus -3.5 ein "-3,5", und der Befehl
        // scheiterte - mit einer Fehlermeldung ueber den Radius, nicht ueber das Komma.
        Chunkfeld feld = Chunkfeld.ausGrenze(-3.5, -3.5, 2989);

        String befehl = Vorgenerierung.chunkyBefehl("marktstadt", feld, -3.5, -3.5);

        assertTrue(befehl.contains("-3.5 -3.5"), befehl);
        assertTrue(!befehl.contains(","), "kein Komma in der Zeile: " + befehl);
    }

    @Test
    @DisplayName("Der Radius folgt dem genannten Mittelpunkt, nicht dem Spawn")
    void radiusFolgtDemMittelpunkt() {
        // Dieselbe Welt, zwei Mittelpunkte, zwei Radien. Das ist der ganze Grund, warum
        // der Befehl gerechnet und nicht getippt wird: Mit dem Spawn als Mittelpunkt und
        // dem Radius der Grenzmitte fehlt der Westring, und das sieht man erst, wenn ein
        // Spieler hinlaeuft.
        Chunkfeld feld = Chunkfeld.ausStadtgrenze(Stadtgrenze.marktstadt());

        assertTrue(Vorgenerierung.chunkyBefehl("marktstadt", feld, -3.5, -3.5)
                .endsWith(" 1495"));
        assertTrue(Vorgenerierung.chunkyBefehl("marktstadt", feld, 32, 32)
                .endsWith(" 1530"));
    }

    @Test
    @DisplayName("Die Form steht in der Zeile - ein Kreis liesse die Ecken aus")
    void formIstQuadrat() {
        Chunkfeld feld = Chunkfeld.ausGrenze(0, 0, 800);

        assertTrue(Vorgenerierung.chunkyBefehl("altfurt", feld, 0, 0).contains(" square "),
                "ohne square erzeugt Chunky die Vorgabeform");
    }
}
