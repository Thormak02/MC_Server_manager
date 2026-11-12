package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Flaeche eines Grundstuecks - ein Rechteck aus ganzen Zellen.
 *
 * <p>Die Rechnung, auf der der ganze Zuschnitt steht: Ueberschneidung, Groesse und die
 * Frage des heissen Pfades, ob eine Zelle dazugehoert. Alles ohne Server, ohne Ablage und
 * ohne Welt.
 */
@DisplayName("Der Zellquader")
class ZellquaderTest {

    @Test
    @DisplayName("Zwei Ecken in JEDER Reihenfolge ergeben denselben Quader")
    void zweiEckenInJederReihenfolge() {
        // Der Spieler steckt zwei Ecken ab, und in welcher Reihenfolge er das tut, darf
        // die Flaeche nicht aendern. Alle vier Kombinationen - sonst bliebe genau die
        // ungeprueft, in der beide Achsen gedreht sind.
        Zellquader erwartet = new Zellquader(3, 5, 7, 9);

        assertEquals(erwartet, Zellquader.zwischen(3, 5, 7, 9));
        assertEquals(erwartet, Zellquader.zwischen(7, 9, 3, 5));
        assertEquals(erwartet, Zellquader.zwischen(3, 9, 7, 5));
        assertEquals(erwartet, Zellquader.zwischen(7, 5, 3, 9));
    }

    @Test
    @DisplayName("Ein verdrehter Quader laesst sich nicht BAUEN")
    void verdrehtWirftSofort() {
        // Der Riegel hinter zwischen(...): Wer den Konstruktor von Hand ruft und die
        // Ecken vertauscht, bekommt keinen leeren Quader, sondern einen Wurf. Ein leerer
        // Quader waere ein Grundstueck ohne Flaeche, das die Ueberschneidungspruefung
        // jedes Mal passiert.
        assertThrows(IllegalArgumentException.class, () -> new Zellquader(7, 5, 3, 9));
        assertThrows(IllegalArgumentException.class, () -> new Zellquader(3, 9, 7, 5));
    }

    @Test
    @DisplayName("Enthalten ist einschliesslich - beide Raender gehoeren dazu")
    void raenderGehoerenDazu() {
        Zellquader quader = new Zellquader(3, 5, 7, 9);

        assertTrue(quader.enthaelt(3, 5), "die kleine Ecke");
        assertTrue(quader.enthaelt(7, 9), "die grosse Ecke");
        assertTrue(quader.enthaelt(5, 7), "die Mitte");
        // Und die vier Nachbarn aussen - jede Achse einzeln, sonst bliebe eine Grenze
        // ungeprueft und ein Grundstueck waere eine Zelle zu gross.
        assertFalse(quader.enthaelt(2, 7), "eine Zelle vor der X-Kante");
        assertFalse(quader.enthaelt(8, 7), "eine dahinter");
        assertFalse(quader.enthaelt(5, 4), "eine vor der Z-Kante");
        assertFalse(quader.enthaelt(5, 10), "eine dahinter");
    }

    @Test
    @DisplayName("Eine einzelne Zelle ist der kleinste Quader")
    void eineZelleIstDerKleinste() {
        Zellquader eine = Zellquader.eineZelle(4, 4);

        assertEquals(1, eine.zellen());
        assertEquals(1, eine.breite());
        assertEquals(1, eine.tiefe());
        assertTrue(eine.enthaelt(4, 4));
        assertFalse(eine.enthaelt(5, 4));
        assertEquals(256L, eine.bloecke(), "16 x 16 Bloecke - genau ein Chunk");
    }

    @Test
    @DisplayName("Ueberschneidung sagt in BEIDEN Richtungen dasselbe")
    void ueberschneidungIstSymmetrisch() {
        // Der Aufrufer prueft ein neues Grundstueck gegen alle vorhandenen, und welches
        // von beiden er zuerst nennt, haengt an der Reihenfolge der Ablage. Saehe
        // schneidet() das verschieden, kaeme es auf diese Reihenfolge an - und
        // dieselbe Ueberschneidung wuerde manchmal erkannt und manchmal nicht.
        Zellquader gross = new Zellquader(0, 0, 5, 5);
        Zellquader ecke = new Zellquader(5, 5, 9, 9);
        Zellquader daneben = new Zellquader(6, 0, 9, 5);

        assertTrue(gross.schneidet(ecke), "sie teilen genau die Zelle 5/5");
        assertTrue(ecke.schneidet(gross));
        assertFalse(gross.schneidet(daneben), "Zelle 5 gegen Zelle 6 - kein Block gemein");
        assertFalse(daneben.schneidet(gross));
    }

    @Test
    @DisplayName("Ein Quader schneidet sich selbst - sonst liesse er sich doppelt anlegen")
    void sichSelbstSchneiden() {
        Zellquader quader = new Zellquader(2, 2, 4, 4);

        assertTrue(quader.schneidet(quader));
        assertTrue(quader.schneidet(new Zellquader(2, 2, 4, 4)),
                "und einen gleich geschnittenen genauso");
    }

    @Test
    @DisplayName("Umfassen ist mehr als Schneiden")
    void umfassenIstMehr() {
        Zellquader aussen = new Zellquader(0, 0, 9, 9);
        Zellquader innen = new Zellquader(3, 3, 5, 5);
        Zellquader halb = new Zellquader(8, 8, 12, 12);

        assertTrue(aussen.umfasst(innen));
        assertFalse(innen.umfasst(aussen));
        assertTrue(aussen.schneidet(halb), "sie ueberschneiden sich");
        assertFalse(aussen.umfasst(halb), "aber umfassen tut er ihn nicht");
        assertTrue(aussen.umfasst(aussen), "sich selbst umfasst er");
    }

    @Test
    @DisplayName("Die Groesse rechnet in Zellen UND in Bloecken")
    void groesseInZellenUndBloecken() {
        Zellquader vierMalDrei = new Zellquader(0, 0, 3, 2);

        assertEquals(4, vierMalDrei.breite());
        assertEquals(3, vierMalDrei.tiefe());
        assertEquals(12, vierMalDrei.zellen());
        // 12 Zellen x 16 x 16 = 3.072 Bloecke. Die Zahl steht hier als Rechnung und
        // nicht als Literal: Sie haengt an Stadtfeldplan.ZELLE_KANTE, und wer die
        // aendert, soll nicht diese Probe nachrechnen muessen.
        assertEquals(12L * Stadtfeldplan.ZELLE_KANTE * Stadtfeldplan.ZELLE_KANTE,
                vierMalDrei.bloecke());
    }

    @Test
    @DisplayName("Ein Feld voller Zellen bleibt in den Grenzen von long")
    void grosseFlaecheBleibtRichtig() {
        // Das groesste, was es geben kann: ein ganzes Stadtfeld als EIN Grundstueck.
        Stadtfeldplan plan = Stadtfeldplan.freistaedte();
        Zellquader ganzesFeld = new Zellquader(plan.ersteZelle(0), plan.ersteZelle(0),
                plan.letzteZelle(0), plan.letzteZelle(0));

        assertEquals(16, ganzesFeld.breite(), "256 Bloecke sind 16 Zellen");
        assertEquals(256, ganzesFeld.zellen());
        assertEquals(65_536L, ganzesFeld.bloecke(), "256 x 256 Bloecke");
    }

    @Test
    @DisplayName("Der Text nennt ZELLEN, nicht Bloecke")
    void textNenntZellen() {
        // Der Spieler hat Zellen abgesteckt. Eine Zeile mit Blockkoordinaten liesse ihn
        // rechnen, ob er wirklich das bekommen hat, was er wollte.
        assertEquals("3/5 bis 7/9", new Zellquader(3, 5, 7, 9).alsText());
        assertEquals("-2/-4 bis -1/-3", new Zellquader(-2, -4, -1, -3).alsText());
    }
}
