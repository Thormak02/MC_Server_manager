package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Geometrie von Wolkenwerder.
 *
 * <p>Dieselbe Bauart wie {@link InselplanTest}: Die Proben rechnen ihre Erwartungen nicht
 * mit denselben Formeln nach, die sie pruefen, sondern fragen nach Eigenschaften, die ein
 * Spieler merkt - steht die Plattform unter mir, ist das Feld des Nachbarn weit genug weg,
 * und steht der Baum nicht auf der Truhe.
 */
@DisplayName("Der Plan von Wolkenwerder")
class WolkenplanTest {

    private final Wolkenplan plan = Wolkenplan.wolkenwerder();

    @Test
    @DisplayName("Ein Feld ist 128 Bloecke breit, dazwischen liegen 384 Bloecke Leere")
    void dasRaster() {
        assertEquals(128, plan.feldKante());
        assertEquals(384, plan.leereBreite());
        assertEquals(512, plan.rasterWeite(),
                "eine Rasterzelle ist genau eine Regionsdatei - 32 mal 32 Chunks");

        assertEquals(Wolkenplan.Feld.WOLKENFELD, plan.feldBei(0, 0));
        assertEquals(Wolkenplan.Feld.WOLKENFELD, plan.feldBei(127, 127),
                "der letzte Block des Feldes");
        assertEquals(Wolkenplan.Feld.LEERE, plan.feldBei(128, 0),
                "einen Block weiter beginnt die Leere");
        assertEquals(Wolkenplan.Feld.LEERE, plan.feldBei(0, 128));
        assertEquals(Wolkenplan.Feld.LEERE, plan.feldBei(200, 200),
                "mitten in der Leere");
        assertEquals(Wolkenplan.Feld.WOLKENFELD, plan.feldBei(512, 512),
                "das naechste Feld faengt bei 512 an");
    }

    @Test
    @DisplayName("Die Leere gewinnt - ein Feld braucht BEIDE Achsen")
    void dieLeereGewinnt() {
        // Ueber Eck gibt es kein halbes Eigentum. Ohne diese Regel waere der Streifen
        // zwischen zwei Feldern auf einer Achse Eigentum und auf der anderen nicht.
        assertEquals(Wolkenplan.Feld.LEERE, plan.feldBei(0, 300));
        assertEquals(Wolkenplan.Feld.LEERE, plan.feldBei(300, 0));
        assertEquals(Wolkenplan.Feld.LEERE, plan.feldBei(300, 300));
    }

    @Test
    @DisplayName("Die Welt geht in beide Richtungen - auch im Negativen")
    void auchImNegativen() {
        assertEquals(-1, plan.feldNummer(-1), "der Block -1 liegt im Feld -1");
        assertEquals(-512, plan.feldAnfang(-1));
        assertEquals(Wolkenplan.Feld.LEERE, plan.feldBei(-1, -1),
                "das Feld -1 endet bei -385 - der Block -1 ist Leere");
        assertEquals(Wolkenplan.Feld.WOLKENFELD, plan.feldBei(-512, -512),
                "der erste Block des Feldes -1");
        assertEquals(Wolkenplan.Feld.WOLKENFELD, plan.feldBei(-385, -385),
                "und sein letzter");
    }

    @Test
    @DisplayName("Das Ankunftsfeld ist 0/0, und seine Mitte ist der Erscheinungspunkt")
    void dasAnkunftsfeld() {
        assertTrue(plan.istAnkunftsfeld(0, 0));
        assertFalse(plan.istAnkunftsfeld(0, 1));
        assertFalse(plan.istAnkunftsfeld(-1, 0));
        assertEquals(63, plan.ankunftsMitte(),
                "bei gerader Feldkante der kleinere der beiden mittleren Bloecke");
        assertEquals(plan.feldMitte(Wolkenplan.ANKUNFTSFELD), plan.ankunftsMitte(),
                "eine Stelle und nicht zwei");
    }

    @Test
    @DisplayName("Die Plattform ist 9x9 und liegt um die Feldmitte")
    void diePlattform() {
        int halb = plan.plattformHalb();

        assertEquals(9, plan.plattformKante());
        assertEquals(4, halb);
        assertEquals(Wolkenplan.Stueck.WIESE, stueck(0, 0, 0), "der Mittelblock");
        assertEquals(Wolkenplan.Stueck.WIESE, stueck(halb, 0, halb), "die Suedostecke");
        assertEquals(Wolkenplan.Stueck.WIESE, stueck(-halb, 0, -halb), "die Nordwestecke");
        assertEquals(Wolkenplan.Stueck.NICHTS, stueck(halb + 1, 0, 0),
                "einen Block weiter ist nichts");
        assertEquals(Wolkenplan.Stueck.NICHTS, stueck(0, 0, -halb - 1));
    }

    @Test
    @DisplayName("Unter der Wiese liegen genau zwei Schichten Erde, dann nichts")
    void zweiSchichtenErde() {
        // Die Zahl, an der das ganze Spiel haengt: Erde waechst nicht nach. Eine Schicht
        // mehr oder weniger ist ein Drittel des Baulands einer Insel.
        assertEquals(Wolkenplan.Stueck.ERDE, stueck(0, -1, 0));
        assertEquals(Wolkenplan.Stueck.ERDE, stueck(0, -2, 0));
        assertEquals(Wolkenplan.Stueck.NICHTS, stueck(0, -3, 0),
                "darunter ist Leere, kein Stein");
        assertEquals(2, plan.tiefeUnterWiese());

        int bloecke = 0;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int dy = -4; dy <= 0; dy++) {
                    if (stueck(dx, dy, dz) != Wolkenplan.Stueck.NICHTS) {
                        bloecke++;
                    }
                }
            }
        }
        assertEquals(243, bloecke, "81 Wiese und 162 Erde - das ganze Bauland einer"
                + " Insel, und es kommt nichts nach");
    }

    @Test
    @DisplayName("Der Baum steht nicht auf der Truhe, und beide stehen auf der Wiese")
    void baumUndTruhe() {
        assertEquals(Wolkenplan.Stueck.TRUHE, stueck(Wolkenplan.TRUHE_X, 1,
                Wolkenplan.TRUHE_Z));
        assertEquals(Wolkenplan.Stueck.STAMM, stueck(Wolkenplan.BAUM_X, 1,
                Wolkenplan.BAUM_Z));
        assertEquals(Wolkenplan.Stueck.WIESE, stueck(Wolkenplan.TRUHE_X, 0,
                Wolkenplan.TRUHE_Z), "die Truhe steht auf Gras und nicht in der Luft");
        assertEquals(Wolkenplan.Stueck.WIESE, stueck(Wolkenplan.BAUM_X, 0,
                Wolkenplan.BAUM_Z));
        assertTrue(Wolkenplan.BAUM_X != Wolkenplan.TRUHE_X
                        || Wolkenplan.BAUM_Z != Wolkenplan.TRUHE_Z,
                "ein Baum auf der Truhe liesse sie nicht oeffnen");
    }

    @Test
    @DisplayName("Der Baum hat fuenf Stammbloecke und eine Krone darueber")
    void derBaum() {
        for (int dy = 1; dy <= Wolkenplan.STAMM_HOCH; dy++) {
            assertEquals(Wolkenplan.Stueck.STAMM,
                    stueck(Wolkenplan.BAUM_X, dy, Wolkenplan.BAUM_Z),
                    "Stamm auf Hoehe " + dy);
        }
        assertEquals(Wolkenplan.Stueck.LAUB, stueck(Wolkenplan.BAUM_X, 6,
                Wolkenplan.BAUM_Z), "ueber dem Stamm sitzt Laub");
        assertEquals(Wolkenplan.Stueck.LAUB, stueck(Wolkenplan.BAUM_X, 7,
                Wolkenplan.BAUM_Z), "und die Spitze");
        assertEquals(Wolkenplan.Stueck.NICHTS, stueck(Wolkenplan.BAUM_X, 8,
                Wolkenplan.BAUM_Z), "darueber ist Himmel");
        assertEquals(7, plan.hoeheUeberWiese(), "die Spitze liegt sieben ueber der Wiese");

        // Die Krone sitzt UM den Stamm und nicht daneben: Laub ohne Stamm in Reichweite
        // verschwindet in Minecraft von selbst.
        assertEquals(Wolkenplan.Stueck.LAUB, stueck(Wolkenplan.BAUM_X + 2, 4,
                Wolkenplan.BAUM_Z), "die aeussere Kante der Krone");
        assertEquals(Wolkenplan.Stueck.NICHTS, stueck(Wolkenplan.BAUM_X + 2, 4,
                Wolkenplan.BAUM_Z + 2), "die Ecken der Krone sind offen");
        assertEquals(Wolkenplan.Stueck.NICHTS, stueck(Wolkenplan.BAUM_X + 3, 4,
                Wolkenplan.BAUM_Z), "und drei Bloecke weit reicht sie nicht");
    }

    @Test
    @DisplayName("Das Ankunftsfeld traegt ein Gelaender und KEINE Truhe")
    void dasAnkunftsfeldHatEinGelaender() {
        // Der eine Unterschied der beiden Inseln: Dies ist der einzige Platz, den niemand
        // umbauen darf - und ein Sturz in die Leere kostet die Sachen, mit denen jemand
        // gerade aus Altfurt angekommen ist.
        int halb = plan.plattformHalb();

        assertEquals(Wolkenplan.Stueck.GELAENDER,
                plan.stueckBei(Wolkenplan.Insel.ANKUNFT, halb, 1, 0), "die Ostkante");
        assertEquals(Wolkenplan.Stueck.GELAENDER,
                plan.stueckBei(Wolkenplan.Insel.ANKUNFT, halb, 1, halb), "die Ecke");
        assertEquals(Wolkenplan.Stueck.NICHTS,
                plan.stueckBei(Wolkenplan.Insel.ANKUNFT, 0, 1, 0),
                "in der Mitte steht man, dort ist kein Gelaender");
        assertEquals(Wolkenplan.Stueck.NICHTS,
                plan.stueckBei(Wolkenplan.Insel.ANKUNFT, halb, 2, 0),
                "einen Block hoch und nicht zwei");
        assertEquals(Wolkenplan.Stueck.NICHTS,
                plan.stueckBei(Wolkenplan.Insel.ANKUNFT, Wolkenplan.TRUHE_X, 1,
                        Wolkenplan.TRUHE_Z),
                "keine Truhe - sie liesse sich dort nicht oeffnen");
        assertEquals(Wolkenplan.Stueck.NICHTS,
                plan.stueckBei(Wolkenplan.Insel.ANKUNFT, Wolkenplan.BAUM_X, 1,
                        Wolkenplan.BAUM_Z),
                "und kein Baum - sein Laub darf niemand ernten");
        assertEquals(Wolkenplan.Stueck.WIESE,
                plan.stueckBei(Wolkenplan.Insel.ANKUNFT, 0, 0, 0),
                "die Plattform selbst ist dieselbe Gestalt");
    }

    @Test
    @DisplayName("Die Startinsel hat KEIN Gelaender")
    void dieStartinselHatKeines() {
        // Der Fall ins Nichts ist die Regel dieser Welt, und wer sich sichern will, baut
        // sich die Mauer selbst. Ein Ring um die Plattform stuende ausserdem genau dort,
        // wo Lava und Wasser hingehoeren.
        int halb = plan.plattformHalb();

        assertEquals(Wolkenplan.Stueck.NICHTS, stueck(halb, 1, 0));
        assertEquals(Wolkenplan.Stueck.NICHTS, stueck(halb, 1, halb));
    }

    @Test
    @DisplayName("Eine Plattform ohne Mittelblock oder groesser als das Feld wird abgewiesen")
    void riegel() {
        Feldrechnung rechnung = new Feldrechnung(128, 384);

        assertThrows(IllegalArgumentException.class, () -> new Wolkenplan(rechnung, 8),
                "ohne Mittelblock laege die Plattform um einen halben Block schief");
        assertThrows(IllegalArgumentException.class, () -> new Wolkenplan(rechnung, 1),
                "auf einem Block ist kein Platz fuer Lava und Wasser");
        assertThrows(IllegalArgumentException.class, () -> new Wolkenplan(rechnung, 129),
                "breiter als das eigene Feld");
    }

    private Wolkenplan.Stueck stueck(int dx, int dy, int dz) {
        return plan.stueckBei(Wolkenplan.Insel.START, dx, dy, dz);
    }
}
