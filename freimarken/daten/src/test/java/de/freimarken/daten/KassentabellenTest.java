package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Welche Tabellen eine Waehrung benutzt - und dass sich zwei Waehrungen nie treffen. */
@DisplayName("Die Tabellen einer Kasse")
class KassentabellenTest {

    @Test
    @DisplayName("Die Mark steht auf den Tabellen, die es seit Schritt 3 und 7 gibt")
    void markIstUnveraendert() {
        // Diese drei Namen stehen in den Schemaschritten 3 und 7, und die sind
        // UNVERAENDERLICH (SchemaTest haelt eine Pruefsumme je Schritt). Weicht hier
        // einer ab, liest die Kasse eine Tabelle, die es nicht gibt - und zwar erst beim
        // ersten Guthaben, mitten im Spiel.
        assertEquals("kassen", Kassentabellen.MARK.konten());
        assertEquals("buchungen", Kassentabellen.MARK.buch());
        assertEquals("mark", Kassentabellen.MARK.betragsspalte());
    }

    @Test
    @DisplayName("Die Goldmark steht auf EIGENEN Tabellen - keine davon ist die der Mark")
    void goldmarkTeiltNichts() {
        var mark = Kassentabellen.MARK;
        var gold = Kassentabellen.GOLDMARK;

        // DIE PROBE, DIE DEN GANZEN BAUSCHRITT TRAEGT. Teilten sich die beiden eine
        // Tabelle, addierten sich zwei Waehrungen zu einem Stand - und die tragende
        // Zusage des Buchungsbuchs ("die Summe aller Buchungen ist der Stand") wuerde
        // diesen Stand BESTAETIGEN. Die Summenprobe waere gruen, und das Geld falsch.
        assertNotEquals(mark.konten(), gold.konten());
        assertNotEquals(mark.buch(), gold.buch());
        // Auch die SPALTE muss sich unterscheiden, und das ist keine Schoenheit: Wer eine
        // Spalte mark liest, haelt den Wert fuer Mark. Dieselbe Begruendung, aus der die
        // Buchungsgruende ASCII-Schluessel tragen und nicht deutsche Woerter.
        assertNotEquals(mark.betragsspalte(), gold.betragsspalte());
        assertEquals(Set.of("kassen", "buchungen", "goldkassen", "goldbuchungen"),
                Set.of(mark.konten(), mark.buch(), gold.konten(), gold.buch()),
                "vier verschiedene Tabellen fuer zwei Waehrungen");
    }

    @Test
    @DisplayName("Jede Tabelle, die eine Waehrung nennt, legt das Schema auch AN")
    void jedeTabelleWirdAngelegt() {
        // DIE NAHT ZWISCHEN DIESER KLASSE UND DEM SCHEMA, und sie ist die Stelle, an der
        // ein Vertipper landet: Die Namen hier gehen ungeschuetzt ins SQL, und ein
        // "goldkasse" statt "goldkassen" waere erst bei der ersten Buchung ein Fehler -
        // also nach dem Start, im Spiel, bei einem Spieler.
        //
        // Gefragt wird der ECHTE Schritttext und nicht der Quelltext: Datenbank.SCHRITTE
        // ist paketprivat, und was dort steht, bekommt die Datenbank.
        String alle = String.join("\n", Datenbank.SCHRITTE);
        for (Kassentabellen satz : List.of(Kassentabellen.MARK,
                Kassentabellen.GOLDMARK)) {
            assertTrue(alle.contains("CREATE TABLE " + satz.konten() + " (")
                            || alle.contains("CREATE TABLE IF NOT EXISTS "
                                    + satz.konten() + " ("),
                    "Das Schema legt " + satz.konten() + " nicht an");
            assertTrue(alle.contains("CREATE TABLE " + satz.buch() + " (")
                            || alle.contains("CREATE TABLE IF NOT EXISTS "
                                    + satz.buch() + " ("),
                    "Das Schema legt " + satz.buch() + " nicht an");
            assertTrue(alle.contains(satz.betragsspalte() + " BIGINT")
                            || alle.contains(satz.betragsspalte() + "    BIGINT")
                            || alle.contains(satz.betragsspalte() + " BIGINT   NOT NULL"),
                    "Das Schema legt die Spalte " + satz.betragsspalte() + " nicht an");
        }
    }

    @Test
    @DisplayName("Ein Name, der kein Tabellenname sein kann, wird abgewiesen")
    void unmoeglicheNamen() {
        // Diese Namen gehen UNGESCHUETZT ins SQL. Sie kommen heute nur aus den
        // Konstanten dieser Klasse - der Riegel faengt den Vertipper eines kuenftigen
        // Bauschrittes, und zwar beim Start und nicht bei der ersten Buchung.
        assertThrows(IllegalArgumentException.class,
                () -> new Kassentabellen(null, "buch", "betrag"));
        assertThrows(IllegalArgumentException.class,
                () -> new Kassentabellen("Kassen", "buch", "betrag"));
        assertThrows(IllegalArgumentException.class,
                () -> new Kassentabellen("kassen; DROP TABLE x", "buch", "betrag"));
        assertThrows(IllegalArgumentException.class,
                () -> new Kassentabellen("kassen", "", "betrag"));
        assertThrows(IllegalArgumentException.class,
                () -> new Kassentabellen("kassen", "buch", "3betrag"));
        assertThrows(IllegalArgumentException.class,
                () -> new Kassentabellen("a".repeat(33), "buch", "betrag"));
    }

    @Test
    @DisplayName("Konten und Buch duerfen nicht dieselbe Tabelle sein")
    void kontenUndBuchGetrennt() {
        // Ein Stand, der seine eigene Geschichte ueberschreibt - und die Summenprobe
        // liefe dann gegen sich selbst.
        assertThrows(IllegalArgumentException.class,
                () -> new Kassentabellen("eins", "eins", "betrag"));
    }
}
