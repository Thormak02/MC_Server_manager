package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Rechnung, die sich die Freistaedte und Hafenwerder teilen.
 *
 * <p>Die Zahlen stehen hier als ZAHLEN und nicht als Formel: Eine Probe, die
 * {@code feldAnfang(3)} gegen {@code 3 * rasterWeite()} prueft, schreibt die gepruefte
 * Zeile ab und ist auch dann gruen, wenn beide falsch sind.
 */
@DisplayName("Die Rechnung eines Feldrasters")
class FeldrechnungTest {

    /** Das Raster der Freistaedte: Feld 256, Leere 32, Rasterweite 288. */
    private final Feldrechnung staedte = new Feldrechnung(256, 32);

    /** Das Raster von Hafenwerder: Feld 128, Meer 128, Rasterweite 256. */
    private final Feldrechnung inseln = new Feldrechnung(128, 128);

    @Test
    @DisplayName("Die Rasterweite ist Feld plus Zwischenraum")
    void rasterweite() {
        assertEquals(288, staedte.rasterWeite());
        assertEquals(256, inseln.rasterWeite());
    }

    @Test
    @DisplayName("Im Negativen zaehlt das Raster weiter - floorMod und nicht Rest")
    void imNegativenZaehltEsWeiter() {
        // Der Block -1 liegt am ENDE der Zelle davor und nicht nirgends. Mit dem
        // Rest-Operator kaeme -1 heraus, und damit laege er in keinem Feld.
        assertEquals(255, inseln.imRaster(-1));
        assertEquals(128, inseln.imRaster(-128));
        assertEquals(0, inseln.imRaster(-256));
        assertEquals(-1, inseln.feldNummer(-1));
        assertEquals(-1, inseln.feldNummer(-256));
        assertEquals(-2, inseln.feldNummer(-257));
    }

    @Test
    @DisplayName("Der Zwischenraum gehoert zum Feld DAVOR")
    void derZwischenraumGehoertNachVorn() {
        // Dieselbe Zusage wie bei Rasterplan.parzelleX auf dem Weg: Die Feldnummer ist
        // auch dort beantwortbar, wo gar kein Feld ist. Wer wissen will, ob ueberhaupt
        // eines gemeint ist, fragt imFeld.
        assertTrue(inseln.imFeld(0));
        assertTrue(inseln.imFeld(127));
        assertFalse(inseln.imFeld(128), "128 ist der erste Block Meer");
        assertFalse(inseln.imFeld(255));
        assertEquals(0, inseln.feldNummer(200), "mitten im Meer, und doch Feld 0");
        assertEquals(1, inseln.feldNummer(256));
    }

    @Test
    @DisplayName("Der Feldanfang ist ein Vielfaches der Rasterweite")
    void feldanfang() {
        assertEquals(0, inseln.feldAnfang(0));
        assertEquals(256, inseln.feldAnfang(1));
        assertEquals(-2304, inseln.feldAnfang(-9));
        assertEquals(2592, staedte.feldAnfang(9));
    }

    @Test
    @DisplayName("Bei gerader Kante ist die Mitte der KLEINERE der beiden Bloecke")
    void dieMitteIstImmerDieselbe() {
        // Welcher von beiden, ist beliebig; dass es immer derselbe ist, nicht. Ein Warp,
        // der mal hierhin und mal dorthin setzt, ist keiner.
        assertEquals(63, inseln.feldMitte(0), "128er Feld: 63, nicht 64");
        assertEquals(319, inseln.feldMitte(1));
        assertEquals(127, staedte.feldMitte(0), "256er Feld: 127");
        assertEquals(-2241, inseln.feldMitte(-9), "auch im Negativen der kleinere");
        assertEquals(50, new Feldrechnung(101, 10).feldMitte(0),
                "bei ungerader Kante gibt es eine echte Mitte");
    }

    @Test
    @DisplayName("Die Feldmitte liegt immer IM Feld - auf beiden Seiten der Null")
    void dieMitteLiegtImFeld() {
        for (int nummer = -20; nummer <= 20; nummer++) {
            int mitte = inseln.feldMitte(nummer);
            assertTrue(inseln.imFeld(mitte),
                    "die Mitte von Feld " + nummer + " liegt bei " + mitte);
            assertEquals(nummer, inseln.feldNummer(mitte));
        }
    }

    @Test
    @DisplayName("Das Mindestmass sind zwei Felder und ein Zwischenraum")
    void mindestmass() {
        assertEquals(608, staedte.mindestKante());
        assertEquals(640, inseln.mindestKante());
    }

    @Test
    @DisplayName("Aus dem Wunsch werden GERADE Felder, und nie aufgerundet")
    void felderWerdenAbgerundet() {
        assertEquals(20, staedte.felderFuer(6000), "6000 ist kein Vielfaches von 288");
        assertEquals(18, inseln.felderFuer(5000), "5000 ist kein Vielfaches von 256");
        assertEquals(2, inseln.felderFuer(640), "genau das Mindestmass");
        assertEquals(0, inseln.felderFuer(639), "darunter traegt es kein Feld mehr");

        for (int wunsch = 640; wunsch <= 12000; wunsch += 37) {
            int felder = inseln.felderFuer(wunsch);
            assertEquals(0, felder % 2, "immer gerade, Wunsch " + wunsch);
            assertTrue(inseln.weltkante(felder) <= wunsch,
                    "aus dem Wunsch " + wunsch + " wurde " + inseln.weltkante(felder));
        }
    }

    @Test
    @DisplayName("Kante, Mitte und Raender beschreiben DASSELBE Fenster")
    void kanteUndRaenderPassenZusammen() {
        // Die vier Zahlen gehen an zwei verschiedene Stellen: Kante und Mitte an die
        // Weltgrenze, die Raender in die Besitzpruefung. Stimmen sie nicht ueberein,
        // zeigt der Spieler ein anderes Rot, als das Programm rechnet.
        int felder = inseln.felderFuer(5000);
        int kleinster = inseln.kleinsterBlock(-felder / 2);
        int groesster = inseln.groessterBlock(felder / 2 - 1);

        assertEquals(-2432, kleinster);
        assertEquals(2303, groesster);
        assertEquals(4736.0, inseln.weltkante(felder));
        assertEquals(-64.0, inseln.weltmitte());
        assertEquals(groesster - kleinster + 1, (int) inseln.weltkante(felder));
        assertEquals((kleinster + groesster + 1) / 2.0, inseln.weltmitte());
    }

    @Test
    @DisplayName("Die Mitte haengt nur am Zwischenraum - darum ist Erweitern additiv")
    void dieMitteHaengtNurAmZwischenraum() {
        // Die Feldzahl geht hier gar nicht ein, und darauf beruht, dass eine Erweiterung
        // rein additiv ist: Wessen Feld heute drin liegt, liegt auch danach drin. Ein
        // Mittelpunkt, der mit der Feldzahl wanderte, verschoebe die ganze Welt.
        assertEquals(-16.0, staedte.weltmitte());
        assertEquals(-64.0, inseln.weltmitte());
        assertEquals(inseln.kleinsterBlock(-1) + inseln.groessterBlock(0) + 1,
                2 * (int) inseln.weltmitte(),
                "zwei Felder um die Null liegen symmetrisch um diese Mitte");
        assertEquals(inseln.kleinsterBlock(-9) + inseln.groessterBlock(8) + 1,
                2 * (int) inseln.weltmitte(),
                "und achtzehn genauso");
    }

    @Test
    @DisplayName("Ohne Feld und ohne Zwischenraum ist es kein Raster")
    void riegel() {
        assertThrows(IllegalArgumentException.class, () -> new Feldrechnung(0, 32));
        assertThrows(IllegalArgumentException.class, () -> new Feldrechnung(-1, 32));
        assertThrows(IllegalArgumentException.class, () -> new Feldrechnung(256, 0));
        assertThrows(IllegalArgumentException.class, () -> new Feldrechnung(256, -1));
    }

    @Test
    @DisplayName("Beide Plaene rechnen mit DIESER Rechnung und nicht mit einer eigenen")
    void beidePlaeneTeilenDieRechnung() {
        // Der Sinn der ganzen Klasse: Stadtfeldplan und Inselplan geben ihre Rechnung
        // heraus, und sie ist dieselbe, die sie selbst benutzen. Zwei Abschriften waeren
        // zwei Orte, an denen die Feldmitte auseinanderlaufen kann.
        Stadtfeldplan stadt = Stadtfeldplan.freistaedte();
        Inselplan insel = Inselplan.hafenwerder();

        assertEquals(staedte, stadt.rechnung());
        assertEquals(inseln, insel.rechnung());
        assertEquals(stadt.feldMitte(7), stadt.rechnung().feldMitte(7));
        assertEquals(insel.feldMitte(7), insel.rechnung().feldMitte(7));
        assertEquals(stadt.rasterWeite(), stadt.rechnung().rasterWeite());
        assertEquals(insel.rasterWeite(), insel.rechnung().rasterWeite());
    }
}
