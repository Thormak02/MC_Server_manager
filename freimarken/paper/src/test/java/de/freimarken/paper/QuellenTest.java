package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Werkzeug unter den Toren: Was ein Kommentar ist, zaehlt nicht als Benutzung.
 *
 * <p>Drei Proben lesen den Quelltext selbst - "jeder Schluessel wird benutzt", "Farbe nur
 * noch hier", "so oft wird gesendet". Alle drei nahmen bis zum 04.11.2026 eine Nennung im
 * Javadoc als Benutzung, weil sie den rohen Dateitext durchsuchten. Gemessener Fehlerfall:
 * Die einzige Benutzung von {@code ParzellenSatz.AUFWAND_NICHTS} wurde durch
 * handgeschriebenen Text ersetzt und die Konstante blieb als Kommentarzeile stehen - Probe
 * 30 blieb gruen. Wer so vorgeht und dabei zeichengleich schreibt, nimmt dem Betreiber
 * einen Satz aus der Datei, ohne dass ein Lauf rot wird.
 *
 * <p><b>Diese Probe ist das Gegenstueck zu {@code GrenzeTest.quellenWurdenGefunden}:</b>
 * Der Abtaster darf nicht zu VIEL wegnehmen. Ein {@code //} in einer Zeichenkette ist kein
 * Kommentaranfang, und wer es dafuer haelt, frisst den Rest der Zeile - dann verschwindet
 * ein echter Aufruf, und die Tafel der Sendestellen wird ohne Grund rot.
 */
class QuellenTest {

    /** Der Backslash, ohne ihn zu schreiben - diese Datei soll ASCII bleiben. */
    private static final String BACKSLASH = String.valueOf((char) 92);

    @Test
    @DisplayName("35 - ein Zeilenkommentar zaehlt nicht")
    void zeilenkommentarFaelltWeg() {
        String quelle = "int a = eins();\n"
                + "// hier steht zwei() und niemand ruft es\n"
                + "int b = drei(); // und vier() auch nicht\n";
        String rein = Quellen.ohneKommentare(quelle);

        assertTrue(rein.contains("eins()"), rein);
        assertTrue(rein.contains("drei()"), rein);
        assertFalse(rein.contains("zwei()"), "der Kommentar steht noch da: " + rein);
        assertFalse(rein.contains("vier()"), "der Rest der Zeile steht noch da: " + rein);
        // Die Zeilen bleiben: Eine Zeilennummer in einer Meldung soll weiter stimmen.
        assertEquals(3, rein.lines().count(), rein);
    }

    @Test
    @DisplayName("35b - ein Blockkommentar faellt weg, seine Zeilen bleiben")
    void blockkommentarFaelltWeg() {
        String quelle = "int a = eins();\n"
                + "/**\n"
                + " * Javadoc, das zwei() nennt.\n"
                + " */\n"
                + "int b = drei();\n";
        String rein = Quellen.ohneKommentare(quelle);

        assertTrue(rein.contains("eins()"), rein);
        assertTrue(rein.contains("drei()"), rein);
        assertFalse(rein.contains("zwei()"), "das Javadoc steht noch da: " + rein);
        assertEquals(5, rein.lines().count(), rein);
    }

    @Test
    @DisplayName("35c - was in einer Zeichenkette steht, BLEIBT")
    void zeichenkettenBleibenGanz() {
        // Der Fehlerfall in die andere Richtung: Wer das // in dieser Zeichenkette fuer
        // einen Kommentaranfang haelt, frisst den echten Aufruf dahinter.
        String quelle = "sende(\"/parzelle // nicht gemeint\"); echt();\n"
                + "sende(\"ein /* kein Block */ Stern\"); zweitens();\n"
                + "char c = '\"'; drittens();\n";
        String rein = Quellen.ohneKommentare(quelle);

        assertTrue(rein.contains("echt()"), "der Aufruf hinter dem // ist weg: " + rein);
        assertTrue(rein.contains("zweitens()"), "der Aufruf hinter dem Stern ist weg: "
                + rein);
        assertTrue(rein.contains("drittens()"), "das Anfuehrungszeichen als Zeichen hat"
                + " die Suche verwirrt: " + rein);
        assertTrue(rein.contains("/parzelle // nicht gemeint"),
                "die Zeichenkette ist angeknabbert: " + rein);
    }

    @Test
    @DisplayName("35d - ein maskiertes Anfuehrungszeichen beendet die Zeichenkette nicht")
    void maskiertesAnfuehrungszeichenBeendetNichts() {
        // Genau der Fall, der in der ausgelieferten Vorlage steht: ohne "alles".
        String quelle = "sende(\"ohne " + BACKSLASH + "\"alles" + BACKSLASH + "\". //\");"
                + " echt();\n";
        String rein = Quellen.ohneKommentare(quelle);

        assertTrue(rein.contains("echt()"), "das maskierte Anfuehrungszeichen hat die"
                + " Zeichenkette beendet, und der Rest wurde Kommentar: " + rein);
    }

    @Test
    @DisplayName("35e - ein Textblock bleibt ganz, mit allen Schraegstrichen")
    void textblockBleibtGanz() {
        // Die Tafeln von SendewegTest sind Textbloecke, und in ihnen stehen Befehle mit
        // Schraegstrichen. Ein einzelnes Anfuehrungszeichen darin beendet nichts.
        String quelle = "String tafel = \"\"\"\n"
                + "        /parzelle auftrennen ohne \"alles\"\n"
                + "        \"\"\"; echt();\n";
        String rein = Quellen.ohneKommentare(quelle);

        assertTrue(rein.contains("echt()"), "der Textblock hat die Suche verwirrt: " + rein);
        assertTrue(rein.contains("/parzelle auftrennen"), rein);
    }

    @Test
    @DisplayName("35f - zwei Bezeichner kleben nach einem Kommentar nicht aneinander")
    void bezeichnerKlebenNicht() {
        String rein = Quellen.ohneKommentare("eins/* dazwischen */zwei");

        assertFalse(rein.contains("einszwei"), "aus zwei Namen ist einer geworden: "
                + rein);
    }
}
