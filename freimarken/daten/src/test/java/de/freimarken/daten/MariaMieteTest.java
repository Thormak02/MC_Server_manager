package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.MietVertrag;
import de.freimarken.kern.Mietablage;
import de.freimarken.kern.Mietvertrag;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Mietvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaBonusTest}: Eine
 * gruene Suite, die nie eine Datenbank gesehen hat, waere das Schlimmste.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann: dass der
 * zweite eindeutige Schluessel wirklich steht, dass ein abgewiesener Schreibzug NICHTS
 * zurueckgelassen hat, und dass die beiden Datumsspalten keine Zone sehen.
 */
@DisplayName("Ladenmiete in MariaDB")
class MariaMieteTest extends MietVertrag {

    private static Datenbank datenbank;
    private static MariaMiete miete;

    @BeforeAll
    static void verbinden() throws SQLException {
        String url = System.getProperty("freimarken.db.url", "");
        Assumptions.assumeFalse(url.isBlank(),
                "Keine Zugangsdaten - data/freimarken/datenbank.properties fehlt. "
                        + "Erst skripte/mariadb_einrichten.bat ausfuehren.");

        datenbank = new Datenbank(url,
                System.getProperty("freimarken.db.benutzer"),
                System.getProperty("freimarken.db.passwort"));
        datenbank.migriere();
        miete = new MariaMiete(datenbank);
    }

    @AfterAll
    static void trennen() {
        if (datenbank != null) {
            datenbank.close();
        }
    }

    @BeforeEach
    void leeren() throws SQLException {
        datenbank.leereAlles();
    }

    @Override
    protected Mietablage ablage() {
        return miete;
    }

    @Test
    @DisplayName("Der eindeutige Schluessel auf dem Mieter STEHT wirklich")
    void schluesselAufDemMieterStehtWirklich() throws SQLException {
        // Die Zusage "ein Laden je Spieler" am rohen SQL geprueft, nicht am Java davor.
        // Faellt der UNIQUE KEY aus dem Schemaschritt, ist diese Probe rot und jede
        // andere gruen - denn jede andere geht durch MariaMiete.schreibe, und das fragt
        // vorher nach.
        //
        // ZWEIMAL dasselbe INSERT, einmal mit fremdem und einmal mit demselben Mieter,
        // und das ist am 08.11.2026 beim Bauen von Teil C nachgebessert worden: Vorher
        // nannte der Satz die Spalte kaution nicht, und die ist NOT NULL ohne DEFAULT.
        // Die Probe war gruen, weil MariaDB die fehlende Spalte verweigerte - nicht, weil
        // der eindeutige Schluessel stand. Wer ihn aus dem Schemaschritt nahm, haette es
        // nie gemerkt. Jetzt unterscheiden sich die zwei Saetze NUR im Mieter, also ist
        // er der einzige moegliche Grund fuer den Unterschied.
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        miete.schreibe(new Mietvertrag("stand-1", anna, HEUTE, IN_DREISSIG, 500L, 5_000L));

        try (Statement anweisung = datenbank.verbindung().createStatement()) {
            assertEquals(1, anweisung.executeUpdate(rohesEinfuegen("stand-2", bert)),
                    "mit einem FREMDEN Mieter muss derselbe Satz durchgehen - sonst"
                            + " scheitert die zweite Haelfte an etwas anderem");
            assertThrows(SQLException.class,
                    () -> anweisung.executeUpdate(rohesEinfuegen("stand-3", anna)),
                    "die Datenbank selbst muss den zweiten Laden desselben Mieters"
                            + " verweigern");
        }
    }

    /** Ein vollstaendiges INSERT von Hand - alle Pflichtspalten, nur Name und Mieter frei. */
    private static String rohesEinfuegen(String einheit, UUID mieter) {
        return "INSERT INTO ladenmiete (einheit, mieter, beginn, laeuft_bis, miete,"
                + " kaution) VALUES ('" + einheit + "', '" + mieter + "',"
                + " '2026-11-07', '2026-12-06', 500, 5000)";
    }

    @Test
    @DisplayName("Das INSERT ist ein NACKTES - kein ON DUPLICATE KEY UPDATE")
    void einfuegenOhneOnDuplicate() {
        // Eine Zusage ueber die BAUART, und sie braucht eine Begruendung, weil eine
        // Verhaltensprobe sie nicht halten kann: ON DUPLICATE KEY UPDATE feuert bei JEDEM
        // eindeutigen Schluessel, also auch bei uq_ladenmiete_mieter. Ein Versuch, dem
        // Mieter eine zweite Einheit zu geben, waere damit kein Fehler mehr, sondern eine
        // stille Aenderung an der Zeile seiner ERSTEN Einheit - der Spieler behielte den
        // alten Laden mit dem neuen Ablauftag, und niemand bekaeme eine Meldung.
        //
        // Im Normalfall faengt das die Abfrage vor dem Einfuegen; gefaehrlich wird es nur
        // im Rennen zweier Faeden, und das ist in einer Probe nicht herstellbar. Die
        // GESTALT der Anweisung ist es - dieselbe Antwort wie bei VorlageTest 10e.
        //
        // Gelesen wird das Feld und nicht ein Literal: So wird die Probe rot, wenn jemand
        // die Anweisung aendert, und nicht erst, wenn er sie umbenennt.
        String einfuegen = sqlFeld("EINFUEGEN");

        assertTrue(einfuegen.startsWith("INSERT INTO ladenmiete"), einfuegen);
        assertFalse(einfuegen.toUpperCase(java.util.Locale.ROOT).contains("DUPLICATE"),
                "ein ON DUPLICATE KEY UPDATE wuerde bei einem zweiten Laden die fremde"
                        + " Zeile still aendern statt zu scheitern: " + einfuegen);
        assertFalse(einfuegen.toUpperCase(java.util.Locale.ROOT).startsWith("REPLACE"),
                "REPLACE INTO hat dasselbe Problem und loescht die fremde Zeile sogar: "
                        + einfuegen);
    }

    @Test
    @DisplayName("Die Absage NENNT den Laden, der dem Mieter schon gehoert")
    void absageNenntDenFremdenLaden() {
        // Sonst sucht der Spieler, welchen Laden er eigentlich hat. Die Abfrage vor dem
        // Einfuegen ist genau dafuer da - die Sicherung ist der eindeutige Schluessel,
        // und der kann keinen Namen nennen. Eine Einspritzung, die die Abfrage durch
        // null ersetzt, blieb ohne diese Probe gruen (08.11.2026): Der Schluessel fing
        // den Fall, aber die Meldung war die allgemeine.
        UUID anna = UUID.randomUUID();
        miete.schreibe(new Mietvertrag("stand-1", anna, HEUTE, IN_DREISSIG, 500L,
                5_000L));

        IllegalStateException wurf = assertThrows(IllegalStateException.class,
                () -> miete.schreibe(new Mietvertrag("stand-2", anna, HEUTE, IN_DREISSIG,
                        500L, 5_000L)));

        assertTrue(wurf.getMessage().contains("stand-1"),
                "die Meldung muss den vorhandenen Laden nennen: " + wurf.getMessage());
    }

    @Test
    @DisplayName("Ein abgewiesener Schreibzug laesst die Verbindung SAUBER zurueck")
    void abgewiesenLaesstDieVerbindungSauber() throws SQLException {
        // Der Pfad "Fehlschlag zwischen raeumen und einfuegen" ist von aussen nicht zu
        // erzeugen - beide Anweisungen scheitern nur an Zusagen, die die Abfrage vorher
        // prueft. Was sehr wohl zu pruefen ist: dass die Transaktion danach wirklich
        // beendet ist. Bliebe autoCommit auf false, haengte JEDE weitere Buchung in einer
        // Transaktion, die niemand committet - und sie verschwaende beim naechsten
        // Verbindungsabbruch lautlos.
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        miete.schreibe(new Mietvertrag("stand-1", anna, HEUTE, IN_DREISSIG, 500L,
                5_000L));

        assertThrows(IllegalStateException.class, () -> miete.schreibe(
                new Mietvertrag("stand-2", anna, HEUTE, IN_DREISSIG, 500L, 5_000L)));

        assertTrue(datenbank.verbindung().getAutoCommit(),
                "autoCommit muss zurueckstehen, auch wenn der Schreibzug geworfen hat");

        // Und der naechste Schreibzug ist wirklich committet: verwerfen() wirft die
        // Verbindung weg, und eine offene Transaktion mit ihr.
        miete.schreibe(new Mietvertrag("stand-2", bert, HEUTE, IN_DREISSIG, 500L,
                5_000L));
        datenbank.verwerfen();

        assertEquals(2, zeilen(), "beide Zeilen muessen den Verbindungsabbruch"
                + " ueberleben - sonst war nichts committet");
    }

    @Test
    @DisplayName("Ein abgewiesener Schreibzug laesst NICHTS zurueck")
    void abgewiesenHinterlaesstNichts() throws SQLException {
        // Die Transaktion in schreibe(), an der Datenbank geprueft: Zwischen Abfrage und
        // Einfuegen steht ein DELETE auf die Zielzeile. Ohne Transaktion waere nach einem
        // abgewiesenen Versuch die Zeile der Zieleinheit geloescht - und ein fremder
        // Laden waere stumm frei geworden.
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        miete.schreibe(new Mietvertrag("stand-1", anna, HEUTE, IN_DREISSIG, 500L, 5_000L));
        miete.schreibe(new Mietvertrag("stand-2", bert, HEUTE, IN_DREISSIG, 500L, 5_000L));

        // Anna will stand-2 - sie hat schon stand-1, also abgewiesen.
        assertThrows(IllegalStateException.class, () -> miete.schreibe(
                new Mietvertrag("stand-2", anna, HEUTE, IN_DREISSIG, 500L, 5_000L)));

        assertEquals(2, zeilen(), "beide Vertraege stehen noch");
        assertEquals(bert, miete.fuerEinheit("stand-2").mieter(),
                "Berts Laden ist unberuehrt");
        assertEquals("stand-1", miete.fuerMieter(anna).einheit());
    }

    @Test
    @DisplayName("Verlaengern ergibt EINE Zeile, nicht zwei")
    void verlaengernEineZeile() throws SQLException {
        UUID anna = UUID.randomUUID();

        miete.schreibe(new Mietvertrag("stand-1", anna, HEUTE, IN_DREISSIG, 500L, 5_000L));
        miete.schreibe(new Mietvertrag("stand-1", anna, HEUTE, IN_DREISSIG.plusDays(30),
                500L, 5_000L));

        assertEquals(1, zeilen(),
                "der Hauptschluessel ist die Einheit - zwei Zeilen hiessen zwei Vertraege"
                        + " ueber denselben Laden, und das Lesen nimmt dann einen davon");
        assertEquals(IN_DREISSIG.plusDays(30),
                miete.fuerEinheit("stand-1").laeuftBis());
    }

    @Test
    @DisplayName("Die beiden Tage stehen als DATE in der Spalte, nicht verschoben")
    void tageStehenAlsDate() throws SQLException {
        // Geprueft in der SPALTE und nicht nur am zurueckgelesenen Vertrag: Ein
        // Schreibzug, der durch die Sitzungszone reist, und ein Lesevorgang, der
        // zurueckreist, heben sich gegenseitig auf - und die Probe waere gruen, obwohl in
        // der Datenbank der falsche Tag steht. Der taegliche Lauf aus Teil C vergleicht
        // aber gegen einen Tag aus JAVA.
        miete.schreibe(new Mietvertrag("stand-1", UUID.randomUUID(),
                java.time.LocalDate.of(2026, 3, 1), java.time.LocalDate.of(2026, 4, 1),
                500L, 5_000L));

        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT CAST(beginn AS CHAR) AS b, CAST(laeuft_bis AS CHAR) AS e"
                             + " FROM ladenmiete WHERE einheit = 'stand-1'")) {
            assertTrue(ergebnis.next(), "die Zeile muss da sein");
            assertEquals("2026-03-01", ergebnis.getString("b"));
            assertEquals("2026-04-01", ergebnis.getString("e"));
        }
    }

    @Test
    @DisplayName("Die hinterlegte Kaution steht in der ZEILE und nicht in der config.yml")
    void kautionStehtInDerZeile() throws SQLException {
        // Der Grund fuer die eigene Spalte, an der Datenbank geprueft: Bei einer Raeumung
        // verfaellt die Kaution, ohne dass eine Rueckzahlung gebucht wird. Waere die
        // gehaltene Summe nur als "eingezahlt minus zurueckgezahlt" aus dem Buchungsbuch
        // zu rechnen, bliebe die verfallene Summe dort stehen - und beim naechsten
        // ordentlichen Kuendigen bekaeme der Spieler sie mit heraus.
        miete.schreibe(new Mietvertrag("stand-1", UUID.randomUUID(), HEUTE, IN_DREISSIG,
                500L, 4_321L));

        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT miete, kaution FROM ladenmiete WHERE einheit = 'stand-1'")) {
            assertTrue(ergebnis.next(), "die Zeile muss da sein");
            // BEIDE Spalten, und mit verschiedenen Zahlen: Sie liegen nebeneinander, und
            // zwei gleiche Werte liessen ein Vertauschen offen.
            assertEquals(500L, ergebnis.getLong("miete"));
            assertEquals(4_321L, ergebnis.getLong("kaution"));
        }
    }

    @Test
    @DisplayName("Die Ausfaelle stehen in der Zeile - und ein frischer Vertrag hat 0")
    void ausfaelleStehenInDerZeile() throws SQLException {
        // Die Spalte aus Teil C, an der Datenbank geprueft. Sie steht hier und nicht im
        // Buchungsbuch, weil sie aus dem Buch nicht zu rechnen ist: Ein Server, der fuenf
        // Tage aus war, hat fuenf Tage ohne Mietzeile - und die Rechnung aus dem Buch
        // raeumte danach jeden Laden der Halle.
        //
        // Zwei Zeilen mit VERSCHIEDENEN Zahlen, 0 und 2: Eine Fassung, die die Spalte gar
        // nicht schreibt, liefert ueberall die 0 aus dem DEFAULT - und waere mit einer
        // einzigen Zeile ueber 0 gruen.
        miete.schreibe(new Mietvertrag("stand-1", UUID.randomUUID(), HEUTE, IN_DREISSIG,
                500L, 5_000L));
        miete.schreibe(new Mietvertrag("stand-2", UUID.randomUUID(), HEUTE, IN_DREISSIG,
                500L, 5_000L, 2));

        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery("SELECT einheit, ausfaelle FROM"
                     + " ladenmiete ORDER BY einheit")) {
            assertTrue(ergebnis.next());
            assertEquals("stand-1", ergebnis.getString("einheit"));
            assertEquals(0, ergebnis.getInt("ausfaelle"));
            assertTrue(ergebnis.next());
            assertEquals("stand-2", ergebnis.getString("einheit"));
            assertEquals(2, ergebnis.getInt("ausfaelle"));
        }
    }

    @Test
    @DisplayName("Der Vertrag haengt an KEINEM Fremdschluessel - er darf ohne Konto bestehen")
    void keinFremdschluessel() {
        // Dieselbe Linie wie bei tagesbonus, spielzeit und berufe: Ein Fremdschluessel
        // machte aus der Reihenfolge zweier Schreibzuege eine Zusage.
        UUID ohneKonto = UUID.randomUUID();

        miete.schreibe(new Mietvertrag("stand-1", ohneKonto, HEUTE, IN_DREISSIG, 500L, 5_000L));

        assertEquals(ohneKonto, miete.fuerEinheit("stand-1").mieter());
    }

    @Test
    @DisplayName("leereAlles raeumt die Vertraege mit weg")
    void leereAllesRaeumtAuf() throws SQLException {
        // Ohne diese Zeile in leereAlles wanderten Vertraege von einem Test in den
        // naechsten - und weil "ein Laden je Spieler" ein eindeutiger Schluessel ist,
        // waere die Folge nicht ein falscher Stand, sondern ein harter Wurf.
        miete.schreibe(new Mietvertrag("stand-1", UUID.randomUUID(), HEUTE, IN_DREISSIG,
                500L, 5_000L));

        datenbank.leereAlles();

        assertEquals(0, zeilen());
        assertNull(miete.fuerEinheit("stand-1"));
    }

    /**
     * Der SQL-Text eines Feldes von {@link MariaMiete} - fuer die Probe ueber die Bauart.
     *
     * <p>Ueber Reflexion und nicht als abgeschriebenes Literal: Eine Probe, die den Satz
     * noch einmal hinschreibt, prueft sich selbst. So liest sie, was wirklich ausgefuehrt
     * wird.
     */
    private static String sqlFeld(String name) {
        try {
            java.lang.reflect.Field feld = MariaMiete.class.getDeclaredField(name);
            feld.setAccessible(true);
            return (String) feld.get(null);
        } catch (ReflectiveOperationException fehlt) {
            throw new AssertionError("MariaMiete." + name + " gibt es nicht mehr -"
                    + " die Probe ueber die Bauart muss mitwandern.", fehlt);
        }
    }

    private int zeilen() throws SQLException {
        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT COUNT(*) FROM ladenmiete")) {
            ergebnis.next();
            return ergebnis.getInt(1);
        }
    }
}
