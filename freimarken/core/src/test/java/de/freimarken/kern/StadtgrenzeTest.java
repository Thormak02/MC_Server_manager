package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Eine Weltgrenze laesst sich nach aussen schieben, nach innen nicht. Ein Fehler hier
 * faellt beim ersten Mal nicht auf - er faellt auf, wenn jemand fuer eine Parzelle bezahlt
 * hat, die nachher vor der Tuer liegt. Deshalb wird hier jede Zahl einzeln nachgerechnet.
 */
class StadtgrenzeTest {

    private final Rasterplan plan = Rasterplan.marktstadt();
    private final Stadtgrenze grenze = Stadtgrenze.marktstadt();

    @Test
    @DisplayName("Aus dem Wunsch 3.000 werden 42 Parzellen je Achse, Nummern -21 bis 20")
    void ausDemWunschWirdDasRaster() {
        assertEquals(42, grenze.zellenJeAchse());
        assertEquals(-21, grenze.von());
        assertEquals(20, grenze.bis());
        // Plan Abschnitt 4 nennt "~1.760 Parzellen".
        assertEquals(1764L, grenze.parzellenZahl());
    }

    @Test
    @DisplayName("Die Kante kommt aus dem Raster, nicht aus dem Wunsch: 2.989 statt 3.000")
    void kanteKommtAusDemRaster() {
        assertEquals(2989.0, grenze.kante());
        assertEquals(-3.5, grenze.mitte());
    }

    @Test
    @DisplayName("Die Kante ist ein Durchmesser, kein Radius")
    void kanteIstDurchmesser() {
        double halbe = grenze.kante() / 2;
        assertEquals(1494.5, halbe);
        // Mitte minus halbe Kante trifft genau den kleinsten Block, der noch drin ist.
        assertEquals(grenze.kleinsterBlock(), grenze.mitte() - halbe);
        // Mitte plus halbe Kante trifft den ersten Block, der schon draussen ist.
        assertEquals(grenze.groessterBlock() + 1, grenze.mitte() + halbe);
        assertEquals(-1498, grenze.kleinsterBlock());
        assertEquals(1490, grenze.groessterBlock());
    }

    @Test
    @DisplayName("Die Mitte bleibt -3,5, egal wie gross die Stadt ist")
    void mitteBleibtGleich() {
        assertEquals(-3.5, Stadtgrenze.ausKante(plan, 149).mitte());
        assertEquals(-3.5, Stadtgrenze.ausKante(plan, 3131).mitte());
        assertEquals(-3.5, Stadtgrenze.ausKante(plan, 60_000).mitte());
    }

    @Test
    @DisplayName("Jede erlaubte Parzelle liegt mit allen 71 Bloecken innerhalb der Grenze")
    void erlaubteParzellenLiegenGanzInnen() {
        for (int zelle = grenze.von(); zelle <= grenze.bis(); zelle++) {
            int anfang = plan.ursprungX(zelle);
            for (int versatz = 0; versatz < plan.rasterWeite(); versatz++) {
                int block = anfang + versatz;
                assertTrue(block >= grenze.kleinsterBlock() && block <= grenze.groessterBlock(),
                        "Block " + block + " der Zelle " + zelle + " liegt ausserhalb");
            }
        }
    }

    @Test
    @DisplayName("Innerhalb der Grenze gehoert kein Bauland zu einer verbotenen Parzelle")
    void keinVerbotenesBaulandInnerhalb() {
        for (int x = grenze.kleinsterBlock(); x <= grenze.groessterBlock(); x++) {
            if (plan.feldBei(x, 0) == Rasterplan.Feld.PARZELLE) {
                assertTrue(grenze.enthaelt(x, 0),
                        "Bauland bei " + x + " liegt innerhalb der Grenze, ist aber verboten");
            }
        }
    }

    @Test
    @DisplayName("Die erste verbotene Parzelle beginnt genau einen Block hinter der Grenze")
    void ersteVerboteneParzelle() {
        // Osten: Zelle 21 beginnt bei 1491, die Grenze endet bei 1490.
        assertEquals(1491, plan.ursprungX(21));
        assertFalse(grenze.enthaeltParzelle(21, 0));
        assertTrue(grenze.enthaeltParzelle(20, 0));
        // Westen: Das Bauland der Zelle -22 endet bei -1499, die Grenze beginnt bei -1498.
        assertEquals(-1499, plan.ursprungX(-22) + plan.parzellenKante() - 1);
        assertFalse(grenze.enthaeltParzelle(-22, 0));
        assertTrue(grenze.enthaeltParzelle(-21, 0));
    }

    @Test
    @DisplayName("Der aeussere Mauer-Weg-Ring liegt auf allen vier Seiten innen")
    void ringLiegtInnen() {
        assertEquals(-1498, grenze.kleinsterBlock());
        assertEquals(1490, grenze.groessterBlock());
        // Der Westring gehoert zur verbotenen Zelle -22, ist aber die Randmauer der Stadt.
        assertEquals(Rasterplan.Feld.MAUER, plan.feldBei(-1498, 0));
        assertEquals(Rasterplan.Feld.WEG, plan.feldBei(-1495, 0));
        assertEquals(Rasterplan.Feld.MAUER, plan.feldBei(1490, 0));
        // Einen Block weiter beginnt Bauland, das niemand bekommen darf.
        assertEquals(Rasterplan.Feld.PARZELLE, plan.feldBei(-1499, 0));
        assertEquals(Rasterplan.Feld.PARZELLE, plan.feldBei(1491, 0));
    }

    @Test
    @DisplayName("Die Zellenzahl wird abgerundet und ist immer gerade")
    void immerAbgerundetUndGerade() {
        assertEquals(42, Stadtgrenze.ausKante(plan, 3000).zellenJeAchse());
        assertEquals(42, Stadtgrenze.ausKante(plan, 3130).zellenJeAchse());
        assertEquals(44, Stadtgrenze.ausKante(plan, 3131).zellenJeAchse());
    }

    @Test
    @DisplayName("Kanten der Form 142n+7 gehen restlos auf")
    void restloseKanten() {
        assertEquals(2989.0, Stadtgrenze.ausKante(plan, 2989).kante());
        assertEquals(3131.0, Stadtgrenze.ausKante(plan, 3131).kante());
        assertEquals(4267.0, Stadtgrenze.ausKante(plan, 4267).kante());
    }

    @Test
    @DisplayName("Eine Kante, die keine Stadt ergibt oder keine Grenze sein kann, wird abgewiesen")
    void unmoeglicheKanten() {
        assertEquals(149, Stadtgrenze.mindestKante(plan));
        assertThrows(IllegalArgumentException.class, () -> Stadtgrenze.ausKante(plan, 148));
        assertThrows(IllegalArgumentException.class, () -> Stadtgrenze.ausKante(plan, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Stadtgrenze.ausKante(plan, 60_000_000));
    }

    @Test
    @DisplayName("Erweitern ist rein additiv - was drin war, bleibt drin")
    void erweiternIstAdditiv() {
        Stadtgrenze groesser = Stadtgrenze.ausKante(plan, 4267);
        for (int x = grenze.von(); x <= grenze.bis(); x++) {
            for (int z = grenze.von(); z <= grenze.bis(); z++) {
                assertTrue(groesser.enthaeltParzelle(x, z),
                        "Parzelle " + x + "/" + z + " faellt beim Erweitern heraus");
            }
        }
    }

    @Test
    @DisplayName("Die Vorgabe einer frischen Welt gilt als nie gesetzt")
    void frischeWeltZaehltNicht() {
        assertEquals(3000, Stadtgrenze.wirksameKante(
                plan, 3000, Stadtgrenze.WELTGRENZE_HOECHSTMASS, false));
    }

    @Test
    @DisplayName("Eine kleinere Einstellung verkleinert die Grenze nicht")
    void kleinerGehtNicht() {
        assertEquals(2989, Stadtgrenze.wirksameKante(plan, 2000, 2989.0, false));
        assertEquals(2989, Stadtgrenze.wirksameKante(plan, 149, 2989.0, false));
        // Auch mit Bestaetigung nicht - die gilt nur fuer den Sprung nach oben.
        assertEquals(2989, Stadtgrenze.wirksameKante(plan, 2000, 2989.0, true));
    }

    @Test
    @DisplayName("Eine groessere Einstellung vergroessert die Grenze")
    void groesserGehtSofort() {
        assertEquals(4000, Stadtgrenze.wirksameKante(plan, 4000, 2989.0, false));
    }

    @Test
    @DisplayName("Ein Sprung ueber das Doppelte braucht eine Bestaetigung")
    void sprungBrauchtBestaetigung() {
        assertEquals(2989, Stadtgrenze.wirksameKante(plan, 8000, 2989.0, false));
        assertEquals(8000, Stadtgrenze.wirksameKante(plan, 8000, 2989.0, true));
        assertEquals("2989 -> 8000", Stadtgrenze.sprungText(8000, 2989.0));
    }

    @Test
    @DisplayName("Eine unbrauchbare Einstellung faellt auf die 3.000 des Plans zurueck")
    void unbrauchbareEinstellung() {
        assertEquals(3000, Stadtgrenze.wirksameKante(
                plan, 0, Stadtgrenze.WELTGRENZE_HOECHSTMASS, false));
        assertEquals(3000, Stadtgrenze.wirksameKante(
                plan, -5, Stadtgrenze.WELTGRENZE_HOECHSTMASS, false));
        assertEquals(3000, Stadtgrenze.wirksameKante(
                plan, 60_000_000, Stadtgrenze.WELTGRENZE_HOECHSTMASS, false));
    }

    @Test
    @DisplayName("Eine von Hand verstellte Minigrenze blockiert den Start nicht")
    void minigrenzeInDerWelt() {
        // Unter einer Rasterzelle liegt keine Parzelle, die zu schuetzen waere. Ohne diesen
        // Riegel wuerde ausKante() werfen - mitten im Start, zwischen Welt und Schutzwache.
        assertEquals(3000, Stadtgrenze.wirksameKante(plan, 3000, 10.0, false));
        assertEquals(3000, Stadtgrenze.wirksameKante(plan, 3000, 0.0, false));
        Stadtgrenze trotzdem = Stadtgrenze.ausKante(
                plan, Stadtgrenze.wirksameKante(plan, 3000, 10.0, false));
        assertEquals(42, trotzdem.zellenJeAchse());
    }

    @Test
    @DisplayName("Der Spawn liegt in der Stadt")
    void spawnLiegtDrin() {
        assertTrue(grenze.enthaelt(plan.mitteX(0), plan.mitteZ(0)));
    }
}
