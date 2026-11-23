package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Losablage;
import de.freimarken.kern.LosVertrag;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Losvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaBonusTest}: Eine
 * gruene Suite, die nie eine Datenbank gesehen hat, waere das Schlimmste.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann: dass
 * {@code ON DUPLICATE KEY UPDATE anzahl = anzahl + 1} wirklich ADDIERT statt zu setzen,
 * dass dabei je Spieler und Tag EINE Zeile entsteht, und dass in der {@code tag}-Spalte
 * wirklich der hereingegebene Tag steht und nicht der einer Zone.
 */
@DisplayName("Lose in MariaDB")
class MariaLoseTest extends LosVertrag {

    private static Datenbank datenbank;
    private static MariaLose lose;

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
        lose = new MariaLose(datenbank);
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
    protected Losablage ablage() {
        return lose;
    }

    @Override
    protected void saettige(Losablage ablage, UUID spieler, LocalDate tag) {
        // Die Zeile wird mit der Grenze ANGELEGT. Hochzaehlen ist kein Weg: Es waere
        // genau der Aufruf, den der Vertrag danach prueft.
        String sql = "INSERT INTO lotterie_lose (spieler, tag, anzahl) VALUES ('"
                + spieler + "', '" + tag + "', " + Integer.MAX_VALUE + ")";
        try (Statement anweisung = datenbank.verbindung().createStatement()) {
            anweisung.executeUpdate(sql);
        } catch (SQLException fehler) {
            throw new IllegalStateException("Saettigung nicht vorbereitbar", fehler);
        }
    }

    @Test
    @DisplayName("Drei Zuege am selben Tag ergeben EINE Zeile mit anzahl 3")
    void dreiZuegeEineZeile() throws SQLException {
        UUID anna = UUID.randomUUID();

        lose.zaehleEins(anna, HEUTE);
        lose.zaehleEins(anna, HEUTE);
        lose.zaehleEins(anna, HEUTE);

        assertEquals(1, zeilen(anna),
                "der Hauptschluessel ist Spieler UND Tag - drei Zeilen hiessen drei"
                        + " Zaehlungen, und das Lesen nimmt dann eine davon");
        assertEquals(3, anzahlInDerSpalte(anna, HEUTE),
                "in der Spalte muss 3 stehen - ON DUPLICATE KEY UPDATE addiert hier und"
                        + " setzt nicht");
    }

    @Test
    @DisplayName("Der Tag steht als DIESER Tag in der Spalte, nicht als der einer Zone")
    void tagStehtRichtigInDerSpalte() throws SQLException {
        // Geprueft in der SPALTE und nicht nur am zurueckgelesenen Stand: Ein Treiber,
        // der beim Schreiben um eine Zone verschiebt und beim Lesen zurueck, ist in
        // sich stimmig - hin und zurueck kommt derselbe Tag. Auffallen wuerde es erst,
        // wenn jemand anderes die Spalte liest, etwa das Dashboard des Managers. Der
        // Monatsanfang ist dabei der harte Fall: Ein Versatz um einen Tag dreht dort
        // auch den Monat.
        UUID anna = UUID.randomUUID();
        LocalDate monatsanfang = LocalDate.of(2026, 3, 1);

        lose.zaehleEins(anna, monatsanfang);

        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT CAST(tag AS CHAR) AS alsText FROM lotterie_lose"
                             + " WHERE spieler = '" + anna + "'")) {
            assertTrue(ergebnis.next(), "die Zeile muss da sein");
            assertEquals("2026-03-01", ergebnis.getString("alsText"),
                    "als TEXT aus der Datenbank gelesen, damit kein Treiber die Zahl"
                            + " noch einmal durch eine Zone schiebt");
        }
    }

    @Test
    @DisplayName("Zwei Tage desselben Spielers sind zwei Zeilen")
    void zweiTageZweiZeilen() throws SQLException {
        UUID anna = UUID.randomUUID();

        lose.zaehleEins(anna, GESTERN);
        lose.zaehleEins(anna, HEUTE);

        assertEquals(2, zeilen(anna),
                "der Tag gehoert in den Hauptschluessel - ohne ihn waere am naechsten Tag"
                        + " kein Los mehr gratis");
    }

    private static int zeilen(UUID spieler) throws SQLException {
        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT COUNT(*) FROM lotterie_lose WHERE spieler = '"
                             + spieler + "'")) {
            ergebnis.next();
            return ergebnis.getInt(1);
        }
    }

    private static int anzahlInDerSpalte(UUID spieler, LocalDate tag)
            throws SQLException {
        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT anzahl FROM lotterie_lose WHERE spieler = '" + spieler
                             + "' AND tag = '" + tag + "'")) {
            ergebnis.next();
            return ergebnis.getInt(1);
        }
    }
}
