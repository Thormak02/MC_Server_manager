package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wie weit Hafenwerder reicht.
 *
 * <p>Dieselbe Bauart wie {@link FeldgrenzeTest}: Die Proben rechnen ihre Erwartungen
 * NICHT mit denselben Formeln nach, die sie pruefen. Sie fragen stattdessen nach
 * Eigenschaften, die ein Spieler merkt: Komme ich auf jede Insel, die es laut Grenze
 * gibt? Liegt hinter der Grenze noch Eigentum? Wird meine Insel kleiner, wenn jemand eine
 * Zahl in einer Datei aendert?
 */
@DisplayName("Die Grenze von Hafenwerder")
class InselgrenzeTest {

    private final Inselplan plan = Inselplan.hafenwerder();

    @Test
    @DisplayName("Aus dem Wunsch 5000 werden 18 Felder je Achse und die Kante 4736")
    void ausDemWunschWirdDasRaster() {
        Inselgrenze grenze = Inselgrenze.hafenwerder();

        assertEquals(18, grenze.felderJeAchse());
        assertEquals(-9, grenze.von());
        assertEquals(8, grenze.bis());
        assertEquals(4736.0, grenze.kante(),
                "5000 ist kein Vielfaches von 256 - abgerundet auf ganze Felder");
        assertEquals(-64.0, grenze.mitte(), "immer das halbe Meer nach Westen");
        assertEquals(324, grenze.felderZahl());
        assertEquals(323, grenze.inselnZahl(), "die Ankunftsinsel gehoert niemandem");
        assertEquals(-2432, grenze.kleinsterBlock());
        assertEquals(2303, grenze.groessterBlock());
    }

    @Test
    @DisplayName("Eine Grenze von 5000 um 0/0 schnitte ein Inselfeld an")
    void eineEinfacheGrenzeSchneidetAn() {
        // Der ganze Grund fuer die Ableitung, und bei den Freistaedten am 11.11.2026
        // gefunden: -2500 liegt mitten im Feld -10, das von -2560 bis -2433 reicht.
        // Dessen Insel waere zur Haelfte draussen, und wer sie gekauft hat, stuende vor
        // seiner eigenen Weltgrenze.
        assertEquals(-10, plan.feldNummer(-2500));
        assertEquals(Inselplan.Feld.INSELFELD, plan.feldBei(-2500, 0));

        Inselgrenze grenze = Inselgrenze.hafenwerder();

        assertEquals(-2432, grenze.kleinsterBlock(),
                "die abgeleitete Grenze endet am Rand des Meerrings um Feld -9");
        assertFalse(grenze.enthaelt(-2500, 0), "Feld -10 gibt es in dieser Welt nicht");
    }

    @Test
    @DisplayName("Jede Inselmitte liegt INNERHALB der Grenze")
    void jedeInselmitteLiegtDrin() {
        Inselgrenze grenze = Inselgrenze.hafenwerder();

        for (int nummer = grenze.von(); nummer <= grenze.bis(); nummer++) {
            int mitte = plan.feldMitte(nummer);
            assertTrue(mitte >= grenze.kleinsterBlock() && mitte <= grenze.groessterBlock(),
                    "die Mitte von Feld " + nummer + " liegt bei " + mitte
                            + ", die Grenze geht von " + grenze.kleinsterBlock() + " bis "
                            + grenze.groessterBlock());
        }
    }

    @Test
    @DisplayName("Hinter der Grenze liegt kein Eigentum mehr - auf keiner Seite")
    void hinterDerGrenzeKeinEigentum() {
        Inselgrenze grenze = Inselgrenze.hafenwerder();

        // Die eigentliche Zusage: Die Grenze faellt ins offene Meer und nicht in ein
        // Inselfeld. Der Block DAHINTER gehoert schon zum ersten verbotenen Feld.
        assertEquals(Inselplan.Feld.OFFENES_MEER,
                plan.feldBei(grenze.groessterBlock(), plan.feldMitte(0)),
                "der letzte Block innen ist offenes Meer");
        assertEquals(Inselplan.Feld.INSELFELD,
                plan.feldBei(grenze.groessterBlock() + 1, plan.feldMitte(0)),
                "und gleich dahinter faengt das verbotene Feld 9 an - darum endet die"
                        + " Grenze genau hier");

        assertEquals(Inselplan.Feld.OFFENES_MEER,
                plan.feldBei(grenze.kleinsterBlock(), plan.feldMitte(0)),
                "im Westen ebenso");
        assertEquals(Inselplan.Feld.INSELFELD,
                plan.feldBei(grenze.kleinsterBlock() - 1, plan.feldMitte(0)),
                "davor das Eigentum des verbotenen Feldes -10");
    }

    @Test
    @DisplayName("Jedes erlaubte Feld hat sein Wasser auf allen vier Seiten noch drinnen")
    void jedesFeldHatSeinWasserDrinnen() {
        // Nicht bloss die Insel, sondern das ganze FELD: Die acht bis neun Bloecke Wasser
        // um die Insel gehoeren dem Besitzer - das ist sein Hafen. Eine Grenze, die
        // dorthin faellt, nimmt ihm den Steg.
        Inselgrenze grenze = Inselgrenze.hafenwerder();

        for (int nummer = grenze.von(); nummer <= grenze.bis(); nummer++) {
            int westen = plan.feldAnfang(nummer);
            int osten = plan.feldAnfang(nummer) + plan.feldKante() - 1;
            assertTrue(westen >= grenze.kleinsterBlock(),
                    "Feld " + nummer + " faengt bei " + westen + " an");
            assertTrue(osten <= grenze.groessterBlock(),
                    "Feld " + nummer + " endet bei " + osten);
        }
    }

    @Test
    @DisplayName("Kante und Mitte passen zu den Bloecken, die wirklich drin liegen")
    void kanteUndMittePassenZuDenBloecken() {
        Inselgrenze grenze = Inselgrenze.hafenwerder();
        int bloecke = grenze.groessterBlock() - grenze.kleinsterBlock() + 1;

        assertEquals(bloecke, (int) grenze.kante());
        assertEquals((grenze.kleinsterBlock() + grenze.groessterBlock() + 1) / 2.0,
                grenze.mitte());
    }

    @Test
    @DisplayName("enthaelt fragt das FELD - das Meer am Ostrand gehoert zum Feld davor")
    void enthaeltFragtDasFeld() {
        Inselgrenze grenze = Inselgrenze.hafenwerder();

        assertTrue(grenze.enthaelt(plan.feldMitte(8), plan.feldMitte(-9)));
        assertTrue(grenze.enthaelt(grenze.groessterBlock(), 0),
                "das Meer vor dem verbotenen Feld 9 zaehlt noch zu Feld 8");
        assertFalse(grenze.enthaelt(grenze.groessterBlock() + 1, 0));
        assertFalse(grenze.enthaelt(plan.feldMitte(9), 0),
                "Feld 9 gibt es in dieser Welt nicht");
        assertFalse(grenze.enthaelt(0, plan.feldMitte(-10)));
    }

    @Test
    @DisplayName("Die Kante wird abgerundet, nie aufgerundet")
    void nieAufgerundet() {
        for (int wunsch = 640; wunsch <= 12000; wunsch += 37) {
            Inselgrenze grenze = Inselgrenze.ausKante(plan, wunsch);
            assertTrue(grenze.kante() <= wunsch,
                    "aus dem Wunsch " + wunsch + " wurde " + grenze.kante());
            assertEquals(0, grenze.felderJeAchse() % 2,
                    "immer gerade, sonst saesse die Ankunftsinsel nicht in der Mitte");
        }
    }

    @Test
    @DisplayName("Unter zwei Feldern ist es keine Welt mehr - und das wirft sofort")
    void unterZweiFeldernWirftEs() {
        assertEquals(640, Inselgrenze.mindestKante(plan));
        assertThrows(IllegalArgumentException.class,
                () -> Inselgrenze.ausKante(plan, 639));
        assertThrows(IllegalArgumentException.class,
                () -> Inselgrenze.ausKante(plan, Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("Erweitern ist additiv - keine bezahlte Insel faellt heraus")
    void erweiternIstAdditiv() {
        Inselgrenze heute = Inselgrenze.hafenwerder();
        Inselgrenze spaeter = Inselgrenze.ausKante(plan, 20000);

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
        // Dieselbe Regel wie bei den Freistaedten und der Marktstadt: Eine bezahlte Insel
        // darf nicht vor die Tuer geraten, weil jemand eine Zahl in einer Datei aendert.
        assertEquals(5000, Inselgrenze.wirksameKante(plan, 2500, 5000.0, false),
                "in der Welt stehen 5000, die Einstellung sagt 2500 - es bleiben 5000");
        assertEquals(5000, Inselgrenze.wirksameKante(plan, 5000, 2500.0, false),
                "andersherum waechst sie");
        assertEquals(5000, Inselgrenze.wirksameKante(plan, 20000, 5000.0, false),
                "ein Sprung ueber das Doppelte braucht eine Bestaetigung");
        assertEquals(20000, Inselgrenze.wirksameKante(plan, 20000, 5000.0, true));
        assertEquals(Inselgrenze.HAFENWERDER_KANTE, Inselgrenze.wirksameKante(plan, 12,
                Grenzregel.HOECHSTMASS, false),
                "eine unbrauchbare Zahl faellt auf die ausgelieferte Kante zurueck - und"
                        + " nicht auf etwas, bei dem ausKante mitten im Start wirft");
    }
}
