package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wie weit Wolkenwerder reicht.
 *
 * <p>Dieselbe Bauart wie {@link InselgrenzeTest}: Gefragt wird nach Eigenschaften, die ein
 * Spieler merkt - komme ich auf jede Insel, die es laut Grenze gibt, und liegt hinter der
 * Grenze noch Eigentum. Der gemeinsame Rumpf hat seine eigene Probe
 * ({@link FeldfensterTest}); hier stehen die ZAHLEN dieser Welt.
 */
@DisplayName("Die Grenze von Wolkenwerder")
class WolkengrenzeTest {

    private final Wolkenplan plan = Wolkenplan.wolkenwerder();

    @Test
    @DisplayName("Aus dem Wunsch 20000 werden 38 Felder je Achse und die Kante 19840")
    void ausDemWunschWirdDasRaster() {
        Wolkengrenze grenze = Wolkengrenze.wolkenwerder();

        assertEquals(38, grenze.felderJeAchse());
        assertEquals(-19, grenze.von());
        assertEquals(18, grenze.bis());
        assertEquals(19840.0, grenze.kante(),
                "20000 ist kein Vielfaches von 512 - abgerundet auf ganze Felder");
        assertEquals(-192.0, grenze.mitte(), "immer die halbe Leere nach Westen");
        assertEquals(1444, grenze.felderZahl());
        assertEquals(1443, grenze.inselnZahl(), "das Ankunftsfeld gehoert niemandem");
        assertEquals(-10112, grenze.kleinsterBlock());
        assertEquals(9727, grenze.groessterBlock());
    }

    @Test
    @DisplayName("Viermal so viele Inseln wie Hafenwerder - und sie kosten nichts")
    void vielMehrAlsHafenwerder() {
        // Die Entscheidung hinter der Zahl 20000: Die Wolkeninsel ist gratis (Plan 6.2),
        // und von etwas Gratisem werden mehr Stuecke genommen. Kosten tut die groessere
        // Welt nichts, denn eine leere Welt hat keine Chunks, solange niemand eine Insel
        // nimmt.
        assertTrue(Wolkengrenze.wolkenwerder().inselnZahl()
                        > 4 * Inselgrenze.hafenwerder().inselnZahl(),
                "1443 gegen 323");
    }

    @Test
    @DisplayName("Jedes Feld der Welt liegt vollstaendig innerhalb der Grenze")
    void jedesFeldLiegtGanzDrin() {
        Wolkengrenze grenze = Wolkengrenze.wolkenwerder();

        assertTrue(grenze.enthaeltFeld(grenze.von(), grenze.von()));
        assertTrue(grenze.enthaeltFeld(grenze.bis(), grenze.bis()));
        assertFalse(grenze.enthaeltFeld(grenze.von() - 1, 0));
        assertFalse(grenze.enthaeltFeld(0, grenze.bis() + 1));

        // Die Westkante des westlichsten und die Ostkante des oestlichsten Feldes - beide
        // muessen drin liegen, sonst stuende eine Insel halb vor der Tuer.
        assertTrue(plan.feldAnfang(grenze.von()) >= grenze.kleinsterBlock());
        assertTrue(plan.feldAnfang(grenze.bis()) + plan.feldKante() - 1
                <= grenze.groessterBlock());
    }

    @Test
    @DisplayName("Die Leere am Ostrand liegt drin, ist aber kein Eigentum")
    void leereAmRandIstKeinEigentum() {
        Wolkengrenze grenze = Wolkengrenze.wolkenwerder();

        assertTrue(grenze.enthaelt(grenze.groessterBlock(), 0),
                "noch innerhalb der Weltgrenze");
        assertEquals(Wolkenplan.Feld.LEERE,
                plan.feldBei(grenze.groessterBlock(), 0),
                "aber Leere - wer eine Meldung baut, fragt erst den Plan");
    }

    @Test
    @DisplayName("Unter zwei Feldern gibt es keine Welt")
    void zuKleinWirdGeworfen() {
        assertEquals(1408, Wolkengrenze.mindestKante(plan),
                "zwei Rasterzellen und eine Leere");
        assertEquals(2, Wolkengrenze.ausKante(plan, Wolkengrenze.mindestKante(plan))
                .felderJeAchse());
        var zuKlein = assertThrows(IllegalArgumentException.class,
                () -> Wolkengrenze.ausKante(plan, Wolkengrenze.mindestKante(plan) - 1));
        assertTrue(zuKlein.getMessage().contains("Wolkenfelder"),
                "die Meldung nennt die Felder DIESER Welt: " + zuKlein.getMessage());
    }

    @Test
    @DisplayName("Die Grenze waechst nur nach aussen - eine kleinere Zahl schiebt nicht")
    void nurNachAussen() {
        // Eine Wolkeninsel hat niemand bezahlt, aber jemand hat Monate auf ihr gebaut.
        assertEquals(19840, Wolkengrenze.wirksameKante(plan, 5000, 19840.0, false),
                "die Welt ist weiter als die Datei - das bleibt sie");
        assertEquals(30000, Wolkengrenze.wirksameKante(plan, 30000, 19840.0, false),
                "nach aussen ohne Rueckfrage");
        assertEquals(19840, Wolkengrenze.wirksameKante(plan, 50000, 19840.0, false),
                "ueber das Doppelte nicht ohne Bestaetigung");
        assertEquals(50000, Wolkengrenze.wirksameKante(plan, 50000, 19840.0, true));
    }

    @Test
    @DisplayName("Eine sehr kleine Welt zieht kein Ankunftsfeld ab, das sie nicht hat")
    void sehrKleineWelt() {
        // Bei zwei Feldern je Achse liegen die Nummern -1 und 0 - das Ankunftsfeld ist
        // also dabei. Die Probe haelt die andere Richtung offen: Wer die Felder eines
        // Tages anders legt, bekommt hier die Rechnung dazu.
        Wolkengrenze klein = Wolkengrenze.ausKante(plan, Wolkengrenze.mindestKante(plan));

        assertEquals(4, klein.felderZahl());
        assertTrue(klein.enthaeltFeld(Wolkenplan.ANKUNFTSFELD, Wolkenplan.ANKUNFTSFELD));
        assertEquals(3, klein.inselnZahl());
    }
}
