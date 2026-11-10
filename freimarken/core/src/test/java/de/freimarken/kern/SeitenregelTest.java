package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Seitenrechnung - die vier Zeilen, an denen es schiefgeht.
 *
 * <p>Die Einspritzungsrunde zu {@code /shop liste} (10.11.2026) holte VIER ihrer zwoelf
 * Faelle aus dieser Rechnung: abgerundete Seitenzahl, verschobener Seitenanfang,
 * abgewiesene letzte Seite, Schnitt ueber das Ende. Alle vier wurden gefangen - aber je
 * Liste muessten sie neu gefangen werden. Seit es eine ZWEITE blaetternde Liste gibt
 * ({@code /markt liste}), steht die Rechnung an einer Stelle und diese Proben halten sie
 * dort.
 */
@DisplayName("Die Seitenrechnung")
class SeitenregelTest {

    @Test
    @DisplayName("Die Seitenzahl wird AUFGERUNDET - sonst fehlt die letzte Zeile")
    void seitenzahlWirdAufgerundet() {
        // Bei drei Laeden auf zwei je Seite gibt es ZWEI Seiten. Abgerundet waere es
        // eine, und der dritte Laden waere ueber den Befehl nie zu sehen - genau das,
        // wogegen die Liste gebaut ist.
        assertEquals(2, Seitenregel.seiten(3, 2));
        assertEquals(1, Seitenregel.seiten(2, 2), "voll ist nicht zu wenig");
        assertEquals(2, Seitenregel.seiten(4, 3));
        assertEquals(1, Seitenregel.seiten(1, 10));
        assertEquals(10, Seitenregel.seiten(100, 10));
        assertEquals(11, Seitenregel.seiten(101, 10));
    }

    @Test
    @DisplayName("Eine LEERE Liste hat eine Seite und nicht keine")
    void leereListeHatEineSeite() {
        // "Seite 1 von 0" ist eine Auskunft, die niemand lesen kann. Den leeren Fall
        // faengt der Aufrufer vorher ab - und wenn er es vergisst, liest ein Spieler
        // "Seite 1 von 1" und keine Zeile, was wahr ist.
        assertEquals(1, Seitenregel.seiten(0, 10));
        assertTrue(Seitenregel.gibtEs(1, 0, 10));
        assertEquals(0, Seitenregel.ersteZeile(1, 0, 10));
        assertEquals(0, Seitenregel.letzteZeile(1, 0, 10));
    }

    @Test
    @DisplayName("Die LETZTE Seite gibt es - und die danach nicht")
    void letzteSeiteGibtEs() {
        // Der Unterschied zwischen > und >= an dieser Stelle: Das zweite weist die
        // letzte Seite ab, und bei drei Laeden auf zwei je Seite waere der dritte
        // unerreichbar.
        assertTrue(Seitenregel.gibtEs(2, 3, 2), "die letzte Seite gibt es");
        assertFalse(Seitenregel.gibtEs(3, 3, 2));
        assertFalse(Seitenregel.gibtEs(0, 3, 2), "Seiten zaehlen ab 1");
        assertFalse(Seitenregel.gibtEs(-1, 3, 2));
    }

    @Test
    @DisplayName("Seite 1 beginnt bei 0 - Seiten zaehlen ab eins, Listen ab null")
    void ersteSeiteBeginntBeiNull() {
        assertEquals(0, Seitenregel.ersteZeile(1, 30, 10));
        assertEquals(10, Seitenregel.ersteZeile(2, 30, 10));
        assertEquals(20, Seitenregel.ersteZeile(3, 30, 10));
    }

    @Test
    @DisplayName("Der Schnitt laeuft nie ueber das Ende hinaus")
    void schnittBleibtInDerListe() {
        // Die letzte Seite ist meistens nicht voll, und ein Schnitt darueber hinaus ist
        // ein Wurf mitten in der Antwort.
        assertEquals(3, Seitenregel.letzteZeile(2, 3, 2), "einer auf der zweiten Seite");
        assertEquals(2, Seitenregel.letzteZeile(1, 3, 2));
        assertEquals(30, Seitenregel.letzteZeile(3, 30, 10));
    }

    @Test
    @DisplayName("Der Weiter-Hinweis steht auf jeder Seite ausser der letzten")
    void weiterNurVorDerLetzten() {
        assertTrue(Seitenregel.gehtWeiter(1, 3, 2));
        assertFalse(Seitenregel.gehtWeiter(2, 3, 2),
                "auf der letzten Seite zeigt kein Hinweis auf eine Seite, die es nicht"
                        + " gibt");
        assertFalse(Seitenregel.gehtWeiter(1, 2, 2));
    }

    @Test
    @DisplayName("Jede Zeile landet auf genau EINER Seite - fuer jede Groesse")
    void jedeZeileGenauEinmal() {
        // DIE PROBE, DIE DIE RECHNUNG PRUEFT UND NICHT IHRE ABSCHRIFT. Die Tafeln oben
        // halten die Faelle, die jemand aufgeschrieben hat; dieser Durchlauf haelt die
        // ZUSAGE: Blaettert man eine Liste von vorn bis hinten durch, hat man jede Zeile
        // genau einmal gesehen. Jede Verschiebung um eins faellt hier auf - auch die,
        // die in keiner Tafel steht.
        for (int anzahl = 0; anzahl <= 40; anzahl++) {
            for (int jeSeite = 1; jeSeite <= 7; jeSeite++) {
                List<Integer> gesehen = new ArrayList<>();
                for (int seite = 1; Seitenregel.gibtEs(seite, anzahl, jeSeite); seite++) {
                    int von = Seitenregel.ersteZeile(seite, anzahl, jeSeite);
                    int bis = Seitenregel.letzteZeile(seite, anzahl, jeSeite);
                    assertTrue(bis - von <= jeSeite,
                            "keine Seite traegt mehr als " + jeSeite + ": " + anzahl
                                    + " Zeilen, Seite " + seite);
                    for (int zeile = von; zeile < bis; zeile++) {
                        gesehen.add(zeile);
                    }
                }
                List<Integer> erwartet = new ArrayList<>();
                for (int zeile = 0; zeile < anzahl; zeile++) {
                    erwartet.add(zeile);
                }
                assertEquals(erwartet, gesehen,
                        anzahl + " Zeilen zu " + jeSeite + " je Seite - jede genau"
                                + " einmal und in der Reihenfolge");
            }
        }
    }

    @Test
    @DisplayName("Ohne Zeilen je Seite gibt es keine Rechnung")
    void ohneSeitengroesseKeineRechnung() {
        assertThrows(IllegalArgumentException.class, () -> Seitenregel.seiten(10, 0));
        assertThrows(IllegalArgumentException.class, () -> Seitenregel.seiten(10, -1));
        assertThrows(IllegalArgumentException.class, () -> Seitenregel.seiten(-1, 10));
        // Und wer eine Seite aufschlaegt, die es nicht gibt, bekommt keinen geratenen
        // Schnitt: Die Frage danach heisst gibtEs, und sie steht vor der Rechnung.
        assertThrows(IllegalArgumentException.class,
                () -> Seitenregel.ersteZeile(4, 3, 2));
        assertThrows(IllegalArgumentException.class,
                () -> Seitenregel.letzteZeile(0, 3, 2));
    }
}
