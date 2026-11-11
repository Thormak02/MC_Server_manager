package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Geometrie der Freistaedte - Stadtfeld, Barriere, Leere.
 *
 * <p>Diese Rechnung traegt drei Dinge, die sonst auseinanderlaufen: den Weltgenerator,
 * die Frage nach dem Besitzer eines Blockes und den Ankunftspunkt eines Warps. Eine
 * Probe, die nur den Generator prueft, laesst die beiden anderen offen - darum steht hier
 * die Rechnung und nicht das Bild.
 */
@DisplayName("Die Geometrie der Freistaedte")
class StadtfeldplanTest {

    private final Stadtfeldplan plan = Stadtfeldplan.freistaedte();

    @Test
    @DisplayName("Ein Stadtfeld ist volle 256 Bloecke breit - die Barriere steht daneben")
    void feldBleibtVoll() {
        // DIESELBE ENTSCHEIDUNG WIE BEI DER MAUER DER MARKTSTADT: Sie steht auf dem Weg
        // und nicht auf der Parzelle. Wer 256 verspricht, liefert 256 - eine Barriere auf
        // dem Bauland naehme der Stadt einen Ring von 1.020 Bloecken, und zwar genau den,
        // an dem jeder bauen will.
        assertEquals(Stadtfeldplan.Feld.STADTFELD, plan.feldBei(0, 0));
        assertEquals(Stadtfeldplan.Feld.STADTFELD, plan.feldBei(255, 255));
        assertEquals(Stadtfeldplan.Feld.BARRIERE, plan.feldBei(256, 0),
                "der erste Block hinter dem Feld ist schon Rand");
        assertEquals(256, plan.feldKante());
    }

    @Test
    @DisplayName("Die Barriere steht an BEIDEN Raendern der Leere")
    void barriereAnBeidenRaendern() {
        // Die Leere ist 32 breit: 256 ist der Rand DIESES Feldes, 287 der Rand des
        // NAECHSTEN. Stuende nur eine da, faende jeder Spieler die offene Seite - und
        // zwar die, an der er gerade steht.
        assertEquals(Stadtfeldplan.Feld.BARRIERE, plan.feldBei(256, 100));
        assertEquals(Stadtfeldplan.Feld.BARRIERE, plan.feldBei(287, 100));
        assertEquals(Stadtfeldplan.Feld.LEERE, plan.feldBei(257, 100));
        assertEquals(Stadtfeldplan.Feld.LEERE, plan.feldBei(286, 100));
        assertEquals(Stadtfeldplan.Feld.STADTFELD, plan.feldBei(288, 100),
                "und dahinter faengt das naechste Feld an");
    }

    @Test
    @DisplayName("Ueber Eck steht ein Pfosten - sonst geht man an der Ecke hinaus")
    void eckePfosten() {
        // Eine Ecke ohne Barriere waere genau die Luecke, durch die jemand diagonal
        // hinauslaeuft. Beide Achsen sind dort Rand, also ist es Rand.
        assertEquals(Stadtfeldplan.Feld.BARRIERE, plan.feldBei(256, 256));
        assertEquals(Stadtfeldplan.Feld.BARRIERE, plan.feldBei(287, 287));
        assertEquals(Stadtfeldplan.Feld.BARRIERE, plan.feldBei(256, 287));
    }

    @Test
    @DisplayName("Die LEERE gewinnt gegen die Barriere - keine Wand im Himmel")
    void leereGewinnt() {
        // Eine Barriere mitten in der Leere begrenzte nichts: Sie stuende quer zwischen
        // vier Staedten, wo niemand steht und niemand hinwill - und waere unsichtbar.
        // Wer dort gegenlaeuft, haelt den Server fuer kaputt.
        assertEquals(Stadtfeldplan.Feld.LEERE, plan.feldBei(256, 270),
                "x ist Rand, z ist Leere - dann ist es Leere");
        assertEquals(Stadtfeldplan.Feld.LEERE, plan.feldBei(270, 256));
        assertEquals(Stadtfeldplan.Feld.LEERE, plan.feldBei(270, 270));
    }

    @Test
    @DisplayName("Negative Koordinaten rechnen genauso - die Welt geht nach beiden Seiten")
    void negativeSeite() {
        // Math.floorMod und nicht der Rest-Operator: Jener gibt bei -1 die -1, und dann
        // laege westlich des Nullpunktes alles um ein Feld verschoben. Derselbe Fund wie
        // beim Raster der Marktstadt.
        assertEquals(Stadtfeldplan.Feld.BARRIERE, plan.feldBei(-1, 0),
                "unmittelbar westlich von Feld 0 steht dessen eigener Rand");
        assertEquals(Stadtfeldplan.Feld.LEERE, plan.feldBei(-16, 0),
                "dazwischen die Leere");
        assertEquals(Stadtfeldplan.Feld.BARRIERE, plan.feldBei(-32, 0),
                "und bei -32 der Rand des Feldes -1 - DIESE Zeile hat die Probe beim"
                        + " ersten Lauf berichtigt: Ich hatte dort schon das Bauland"
                        + " erwartet, und das faengt erst einen Block weiter an");
        assertEquals(Stadtfeldplan.Feld.STADTFELD, plan.feldBei(-33, 0),
                "das Feld -1 selbst");
        assertEquals(Stadtfeldplan.Feld.STADTFELD, plan.feldBei(-288, 0),
                "und sein Anfang");
        assertEquals(-1, plan.feldNummer(-1));
        assertEquals(-1, plan.feldNummer(-288));
        assertEquals(-2, plan.feldNummer(-289));
    }

    @Test
    @DisplayName("Die Feldnummer und ihr Anfang passen zusammen")
    void nummerUndAnfang() {
        assertEquals(0, plan.feldNummer(0));
        assertEquals(0, plan.feldNummer(255));
        assertEquals(0, plan.feldNummer(287), "die Leere gehoert zum Feld DAVOR");
        assertEquals(1, plan.feldNummer(288));
        assertEquals(288, plan.feldAnfang(1));
        assertEquals(-288, plan.feldAnfang(-1));
        assertEquals(Stadtfeldplan.Feld.STADTFELD,
                plan.feldBei(plan.feldAnfang(3), plan.feldAnfang(-2)),
                "der Anfang eines Feldes ist Bauland");
    }

    @Test
    @DisplayName("Die Mitte ist immer dieselbe - sonst ist der Warp keiner")
    void mitteBleibtGleich() {
        // Bei gerader Kante gibt es keine echte Mitte. Welcher der beiden mittleren
        // Bloecke genommen wird, ist gleichgueltig - dass es immer derselbe ist, nicht:
        // Ein Warp, der mal hierhin und mal dorthin setzt, ist keiner.
        assertEquals(127, plan.feldMitte(0));
        assertEquals(415, plan.feldMitte(1), "127 + 288");
        assertEquals(-161, plan.feldMitte(-1), "-288 + 127");
        assertEquals(Stadtfeldplan.Feld.STADTFELD,
                plan.feldBei(plan.feldMitte(-1), plan.feldMitte(4)),
                "die Mitte liegt im Bauland und nicht in der Leere");
    }

    @Test
    @DisplayName("Feld 0/0 ist das Ankunftsfeld und gehoert niemandem")
    void ankunftsfeld() {
        // Dort landet das Tor aus Altfurt und steht der Verteiler zu den oeffentlichen
        // Staedten. Die Frage steht an EINER Stelle, damit nicht der Befehl das eine und
        // der Schutz das andere meint.
        assertTrue(plan.istAnkunftsfeld(0, 0));
        assertFalse(plan.istAnkunftsfeld(1, 0));
        assertFalse(plan.istAnkunftsfeld(0, -1));
    }

    @Test
    @DisplayName("Ein Plan ohne Feld oder ohne Platz fuer zwei Barrieren ist keiner")
    void unmoeglicherPlan() {
        assertThrows(IllegalArgumentException.class, () -> new Stadtfeldplan(0, 32));
        assertThrows(IllegalArgumentException.class, () -> new Stadtfeldplan(256, 1),
                "eine Leere von 1 traegt nur EINE Barriere - dann hat ein Feld eine"
                        + " offene Seite");
        assertEquals(258, new Stadtfeldplan(256, 2).rasterWeite(),
                "zwei ist die kleinste Leere, die zwei Raender traegt");
    }

    @Test
    @DisplayName("Bei der kleinsten Leere ist jeder Block der Leere eine Barriere")
    void kleinsteLeere() {
        // Der Grenzfall der Rechnung: Bei leereBreite 2 sind 256 und 257 BEIDE Rand -
        // einmal als "erster hinter dem Feld" und einmal als "letzter vor dem naechsten".
        // Die Reihenfolge der beiden Fragen entscheidet das nicht, und das ist gut so.
        Stadtfeldplan eng = new Stadtfeldplan(256, 2);

        assertEquals(Stadtfeldplan.Feld.BARRIERE, eng.feldBei(256, 0));
        assertEquals(Stadtfeldplan.Feld.BARRIERE, eng.feldBei(257, 0));
        assertEquals(Stadtfeldplan.Feld.STADTFELD, eng.feldBei(258, 0));
    }
}
