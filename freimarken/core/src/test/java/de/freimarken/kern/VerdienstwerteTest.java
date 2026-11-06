package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Schranken der Verdienstwerte - und die Zusage, dass die Vorgabe im Programm genau
 * das ist, was die Datei ausliefert.
 *
 * <p>Gleiche Bauart wie {@link BerufswerteTest} und {@link ZeitwerteTest}. Mit einer
 * Ausnahme, die hier festgenagelt wird: Die LOHNTAFEL hat keine Vorgabe im Programm, und
 * das ist Absicht.
 */
class VerdienstwerteTest {

    private static Verdienstwerte satz(long deckel, int vollBis, int stufe, int faktor,
                                       int boden) {
        return Verdienstwerte.VORGABE.mitMinderung(deckel, vollBis, stufe, faktor,
                boden);
    }

    @Test
    @DisplayName("Die Vorgabe im Programm ist genau das, was die Datei ausliefert")
    void vorgabeStimmt() {
        assertEquals(20_000L, Verdienstwerte.VORGABE.tagesdeckel());
        assertEquals(200, Verdienstwerte.VORGABE.vollBis());
        assertEquals(200, Verdienstwerte.VORGABE.stufe());
        assertEquals(50, Verdienstwerte.VORGABE.faktorProzent());
        assertEquals(10, Verdienstwerte.VORGABE.bodenProzent());
    }

    @Test
    @DisplayName("Die Lohntafel der Vorgabe ist LEER - und das mit Absicht")
    void dieVorgabeTraegtKeineLoehne() {
        // Jede andere Vorgabe dieses Projekts steht im Programm, damit sie nicht fehlen
        // kann. Eine Lohntafel mit vierzig Zeilen dort waere eine zweite Abschrift der
        // ausgelieferten Datei - und zwei Abschriften derselben Tabelle laufen
        // auseinander. Fehlt die Liste, verdient niemand etwas: die sichere Richtung,
        // denn ein geratener Lohn macht Geld, das keine Ruecksicherung zurueckholt.
        assertTrue(Verdienstwerte.VORGABE.loehne().leer());
    }

    @Test
    @DisplayName("Die Pfade heissen so, wie sie in der Datei stehen")
    void pfadeStimmen() {
        assertEquals("verdienst.tagesdeckel", Verdienstwerte.PFAD_TAGESDECKEL);
        assertEquals("verdienst.minderung.voll-bis", Verdienstwerte.PFAD_VOLL_BIS);
        assertEquals("verdienst.minderung.stufe", Verdienstwerte.PFAD_STUFE);
        assertEquals("verdienst.minderung.faktor-prozent", Verdienstwerte.PFAD_FAKTOR);
        assertEquals("verdienst.minderung.boden-prozent", Verdienstwerte.PFAD_BODEN);
        assertEquals("verdienst.loehne", Verdienstwerte.PFAD_LOEHNE);
    }

    @Test
    @DisplayName("Ein Tagesdeckel von 0 waere kein Deckel, sondern ein Verbot")
    void deckelgrenzen() {
        assertEquals(1L, satz(1L, 200, 200, 50, 10).tagesdeckel());
        assertThrows(IllegalArgumentException.class, () -> satz(0L, 200, 200, 50, 10));
        assertThrows(IllegalArgumentException.class, () -> satz(-1L, 200, 200, 50, 10));
        assertThrows(IllegalArgumentException.class,
                () -> satz(Verdienstwerte.HOECHSTER_TAGESDECKEL + 1L, 200, 200, 50, 10));
    }

    @Test
    @DisplayName("voll-bis und stufe sind mindestens 1 - eine 0 teilte durch null")
    void stufengrenzen() {
        assertThrows(IllegalArgumentException.class, () -> satz(100L, 0, 200, 50, 10));
        assertThrows(IllegalArgumentException.class, () -> satz(100L, 200, 0, 50, 10));
        assertThrows(IllegalArgumentException.class, () -> satz(100L, -1, 200, 50, 10));
        assertThrows(IllegalArgumentException.class,
                () -> satz(100L, 200, Verdienstwerte.HOECHSTE_STUFE + 1, 50, 10));
    }

    @Test
    @DisplayName("Der Faktor liegt zwischen 1 und 99 Prozent")
    void faktorgrenzen() {
        // Bei 100 mindert nichts - wer das will, setzt voll-bis hoch. Bei 0 faellt der
        // Satz in einem Schritt auf den Boden, und die Stufen daneben waeren Zierde.
        assertEquals(1, satz(100L, 200, 200, 1, 1).faktorProzent());
        assertEquals(99, satz(100L, 200, 200, 99, 1).faktorProzent());
        assertThrows(IllegalArgumentException.class, () -> satz(100L, 200, 200, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> satz(100L, 200, 200, 0, 10));
    }

    @Test
    @DisplayName("Der Boden liegt zwischen 1 und 100 - eine 0 waere eine unsichtbare Wand")
    void bodengrenzen() {
        assertEquals(1, satz(100L, 200, 200, 50, 1).bodenProzent());
        assertEquals(100, satz(100L, 200, 200, 50, 100).bodenProzent(),
                "100 heisst: die Minderung ist aus");
        assertThrows(IllegalArgumentException.class, () -> satz(100L, 200, 200, 50, 0));
        assertThrows(IllegalArgumentException.class, () -> satz(100L, 200, 200, 50, 101));
    }

    @Test
    @DisplayName("Ohne Lohntafel gibt es keine Verdienstwerte")
    void tafelIstPflicht() {
        assertThrows(IllegalArgumentException.class,
                () -> Verdienstwerte.VORGABE.mitLoehnen(null));
    }

    @Test
    @DisplayName("Die Fragen fuer den Leser antworten wie die Schranken")
    void fragenUndSchrankenStimmenUeberein() {
        // Zwei Wege zur selben Tat: Der Leser fragt, der Satz wirft. Antwortete die Frage
        // anders als die Schranke, meldete die Datei eine brauchbare Zahl und der Bau des
        // Satzes brachte den Start zum Stehen.
        assertTrue(Verdienstwerte.brauchbarerDeckel(1L));
        assertFalse(Verdienstwerte.brauchbarerDeckel(0L));
        assertTrue(Verdienstwerte.brauchbareAnzahl(1L));
        assertFalse(Verdienstwerte.brauchbareAnzahl(0L));
        assertTrue(Verdienstwerte.brauchbarerFaktor(99L));
        assertFalse(Verdienstwerte.brauchbarerFaktor(100L));
        assertTrue(Verdienstwerte.brauchbarerBoden(100L));
        assertFalse(Verdienstwerte.brauchbarerBoden(0L));
        // Eine Zahl jenseits von int kommt aus einer Datei, in der jemand 99999999999
        // getippt hat - sie darf nicht stillschweigend abgeschnitten werden.
        assertFalse(Verdienstwerte.brauchbareAnzahl(Integer.MAX_VALUE + 1L));
    }
}
