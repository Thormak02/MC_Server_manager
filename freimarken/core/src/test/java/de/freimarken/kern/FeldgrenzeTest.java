package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wie weit die Welt der Freistaedte reicht.
 *
 * <p>Die Proben rechnen ihre Erwartungen NICHT mit denselben Formeln nach, die sie
 * pruefen. Sie fragen stattdessen nach Eigenschaften, die ein Spieler merkt: Komme ich
 * auf jedes Feld, das es laut Grenze gibt? Liegt hinter der Grenze noch Bauland? Wird
 * meine Stadt kleiner, wenn jemand eine Zahl in einer Datei aendert?
 */
@DisplayName("Die Grenze der Freistaedtewelt")
class FeldgrenzeTest {

    private final Stadtfeldplan plan = Stadtfeldplan.freistaedte();

    @Test
    @DisplayName("Aus dem Wunsch 6000 werden 20 Felder je Achse und die Kante 5792")
    void ausDemWunschWirdDasRaster() {
        Feldgrenze grenze = Feldgrenze.freistaedte();

        assertEquals(20, grenze.felderJeAchse());
        assertEquals(-10, grenze.von());
        assertEquals(9, grenze.bis());
        assertEquals(5792.0, grenze.kante(),
                "6000 ist kein Vielfaches von 288 - abgerundet auf ganze Felder");
        assertEquals(-16.0, grenze.mitte(), "immer die halbe Leere nach Westen");
        assertEquals(400, grenze.felderZahl());
        assertEquals(399, grenze.staedteZahl(), "das Ankunftsfeld gehoert niemandem");
    }

    @Test
    @DisplayName("Jede Feldmitte liegt INNERHALB der Grenze - sonst warpt man sich hinaus")
    void jedeFeldmitteLiegtDrin() {
        // Genau dieser Fall war der Fehler: Bei einer Grenze von 6000 um 0/0 lag die
        // Mitte von Feld 10 bei 3007 und damit 7 Bloecke VOR der eigenen Weltgrenze. Ein
        // /warp freistadt auf dieses Feld haette den Spieler dorthin gesetzt, wo ihn der
        // Rand sofort wieder zurueckschiebt.
        Feldgrenze grenze = Feldgrenze.freistaedte();

        for (int nummer = grenze.von(); nummer <= grenze.bis(); nummer++) {
            int mitte = plan.feldMitte(nummer);
            assertTrue(mitte >= grenze.kleinsterBlock() && mitte <= grenze.groessterBlock(),
                    "die Mitte von Feld " + nummer + " liegt bei " + mitte
                            + ", die Grenze geht von " + grenze.kleinsterBlock() + " bis "
                            + grenze.groessterBlock());
        }
    }

    @Test
    @DisplayName("Hinter der Grenze liegt kein Bauland mehr - auf keiner Seite")
    void hinterDerGrenzeKeinBauland() {
        Feldgrenze grenze = Feldgrenze.freistaedte();

        // Der Block NACH der Grenze gehoert schon zum ersten verbotenen Feld, und der
        // Block DAVOR noch zum letzten erlaubten. Das ist die eigentliche Zusage: Die
        // Grenze faellt in die Leere und nicht in ein Stadtfeld.
        assertEquals(Stadtfeldplan.Feld.LEERE, plan.feldBei(grenze.groessterBlock() - 1,
                plan.feldMitte(0)), "der vorletzte Block ist Leere");
        assertEquals(Stadtfeldplan.Feld.BARRIERE,
                plan.feldBei(grenze.groessterBlock(), plan.feldMitte(0)),
                "der letzte Block innen ist die Wand des ersten verbotenen Feldes");
        assertEquals(Stadtfeldplan.Feld.STADTFELD,
                plan.feldBei(grenze.groessterBlock() + 1, plan.feldMitte(0)),
                "und gleich dahinter faengt dessen Bauland an - darum endet die Grenze"
                        + " genau hier");

        assertEquals(Stadtfeldplan.Feld.BARRIERE,
                plan.feldBei(grenze.kleinsterBlock(), plan.feldMitte(0)),
                "im Westen ebenso: der erste Block innen ist eine Wand");
        assertEquals(Stadtfeldplan.Feld.STADTFELD,
                plan.feldBei(grenze.kleinsterBlock() - 1, plan.feldMitte(0)),
                "davor das Bauland des verbotenen Feldes");
    }

    @Test
    @DisplayName("Jedes erlaubte Feld hat seine Wand auf allen vier Seiten noch drinnen")
    void jedesFeldHatSeineWandDrinnen() {
        Feldgrenze grenze = Feldgrenze.freistaedte();

        for (int nummer = grenze.von(); nummer <= grenze.bis(); nummer++) {
            int westwand = plan.feldAnfang(nummer) - 1;
            int ostwand = plan.feldAnfang(nummer) + plan.feldKante();
            assertTrue(westwand >= grenze.kleinsterBlock(),
                    "die Westwand von Feld " + nummer + " liegt bei " + westwand);
            assertTrue(ostwand <= grenze.groessterBlock(),
                    "die Ostwand von Feld " + nummer + " liegt bei " + ostwand);
        }
    }

    @Test
    @DisplayName("Kante und Mitte passen zu den Bloecken, die wirklich drin liegen")
    void kanteUndMittePassenZuDenBloecken() {
        // Gemessen und nicht nachgerechnet: Die Grenze ist ein Mass in Bloecken, und
        // kante() und mitte() muessen dasselbe Fenster beschreiben wie kleinsterBlock()
        // und groessterBlock(). Stimmen die vier nicht ueberein, zeigt der Spieler ein
        // anderes Rot, als die Besitzpruefung rechnet.
        Feldgrenze grenze = Feldgrenze.freistaedte();
        int bloecke = grenze.groessterBlock() - grenze.kleinsterBlock() + 1;

        assertEquals(bloecke, (int) grenze.kante());
        assertEquals((grenze.kleinsterBlock() + grenze.groessterBlock() + 1) / 2.0,
                grenze.mitte());
    }

    @Test
    @DisplayName("enthaelt fragt das FELD - die Leere am Ostrand gehoert zum Feld davor")
    void enthaeltFragtDasFeld() {
        Feldgrenze grenze = Feldgrenze.freistaedte();

        assertTrue(grenze.enthaelt(plan.feldMitte(9), plan.feldMitte(-10)));
        assertTrue(grenze.enthaelt(grenze.groessterBlock(), 0),
                "die Wand des verbotenen Feldes 10 zaehlt noch zu Feld 9");
        assertFalse(grenze.enthaelt(grenze.groessterBlock() + 1, 0));
        assertFalse(grenze.enthaelt(plan.feldMitte(10), 0),
                "Feld 10 gibt es in dieser Welt nicht");
        assertFalse(grenze.enthaelt(0, plan.feldMitte(-11)));
    }

    @Test
    @DisplayName("Die Kante wird abgerundet, nie aufgerundet")
    void nieAufgerundet() {
        // Aufrunden hiesse, ein Feld halb einzuschliessen - und genau das soll die
        // Ableitung verhindern. Geprueft ueber eine ganze Reihe von Wuenschen, damit
        // nicht zufaellig der eine getestete Wert aufgeht.
        for (int wunsch = 608; wunsch <= 12000; wunsch += 37) {
            Feldgrenze grenze = Feldgrenze.ausKante(plan, wunsch);
            assertTrue(grenze.kante() <= wunsch,
                    "aus dem Wunsch " + wunsch + " wurde " + grenze.kante());
            assertEquals(0, grenze.felderJeAchse() % 2,
                    "immer gerade, sonst saesse das Ankunftsfeld nicht in der Mitte");
        }
    }

    @Test
    @DisplayName("Unter zwei Feldern ist es keine Welt mehr - und das wirft sofort")
    void unterZweiFeldernWirftEs() {
        assertEquals(608, Feldgrenze.mindestKante(plan));
        assertThrows(IllegalArgumentException.class, () -> Feldgrenze.ausKante(plan, 607));
        assertThrows(IllegalArgumentException.class,
                () -> Feldgrenze.ausKante(plan, Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("Erweitern ist additiv - keine bestehende Stadt faellt heraus")
    void erweiternIstAdditiv() {
        Feldgrenze heute = Feldgrenze.freistaedte();
        Feldgrenze spaeter = Feldgrenze.ausKante(plan, 20000);

        assertEquals(heute.mitte(), spaeter.mitte(), "dieselbe Mitte, sonst waere es kein"
                + " Erweitern, sondern ein Verschieben");
        for (int x = heute.von(); x <= heute.bis(); x++) {
            for (int z = heute.von(); z <= heute.bis(); z++) {
                assertTrue(spaeter.enthaeltFeld(x, z), "Feld " + x + "/" + z);
            }
        }
    }

    @Test
    @DisplayName("Eine kleiner gemachte Zahl verkleinert die Welt nicht")
    void kleinerMachenWirktNicht() {
        // Dieselbe Regel wie bei der Marktstadt: Eine bezahlte Stadt darf nicht vor die
        // Tuer geraten, weil jemand eine Zahl in einer Datei aendert.
        assertEquals(6000, Feldgrenze.wirksameKante(plan, 3000, 6000.0, false),
                "in der Welt steht 6000, die Einstellung sagt 3000 - es bleibt 6000");
        assertEquals(6000, Feldgrenze.wirksameKante(plan, 6000, 3000.0, false),
                "andersherum waechst sie");
        assertEquals(6000, Feldgrenze.wirksameKante(plan, 20000, 6000.0, false),
                "ein Sprung ueber das Doppelte braucht eine Bestaetigung");
        assertEquals(20000, Feldgrenze.wirksameKante(plan, 20000, 6000.0, true));
        assertEquals(Feldgrenze.FREISTAEDTE_KANTE, Feldgrenze.wirksameKante(plan, 12,
                Feldgrenze.WELTGRENZE_HOECHSTMASS, false),
                "eine unbrauchbare Zahl faellt auf den Planwert zurueck - und nicht auf"
                        + " etwas, bei dem ausKante mitten im Start wirft");
    }
}
