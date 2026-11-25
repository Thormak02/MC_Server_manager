package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.TeamVertrag;
import de.freimarken.kern.Teamablage;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Teamvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaRaengeTest}: Eine
 * gruene Suite, die nie eine Datenbank gesehen hat, waere das Schlimmste.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann - und jede
 * einzelne haengt an einer Entscheidung dieses Schrittes: <b>eine Zeile je Spieler UND
 * Rolle, und ein zweites Geben fasst die bestehende nicht an.</b> Der Vertrag prueft die
 * WIRKUNG; dass dabei wirklich nur eine Zeile entsteht und dass {@code INSERT IGNORE}
 * und nicht {@code ON DUPLICATE KEY UPDATE} darunter steht, sieht man nur hier.
 */
@DisplayName("Teamrollen in MariaDB")
class MariaTeamTest extends TeamVertrag {

    private static Datenbank datenbank;
    private static MariaTeam team;

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
        team = new MariaTeam(datenbank);
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
    protected Teamablage ablage() {
        // Immer dieselbe Ablage, aber vor jedem Test eine leere Tabelle - leereAlles()
        // laeuft in @BeforeEach. Dasselbe Vorgehen wie bei MariaRaengeTest.
        return team;
    }

    @Test
    @DisplayName("Wer keine Rolle hat, hat KEINE Zeile")
    void keineRolleKeineZeile() throws SQLException {
        UUID anna = UUID.randomUUID();

        team.lies(anna);

        // Ein Lesezugriff darf nichts anlegen. Eine Fassung, die beim Lesen eine leere
        // Zeile schriebe, legte bei jedem Beitritt eine an - und die Tabelle waere nach
        // einem Monat eine Spielerliste und keine Teamliste.
        assertEquals(0, zeilen(anna));
    }

    @Test
    @DisplayName("Zwei Rollen sind ZWEI Zeilen")
    void zweiRollenZweiZeilen() throws SQLException {
        UUID anna = UUID.randomUUID();

        assertTrue(team.gib(anna, MODERATOR, MITTAGS, null));
        assertTrue(team.gib(anna, BAUMEISTER, MITTAGS, null));

        // Der Hauptschluessel ist das PAAR aus Spieler und Rolle. Stuende dort der
        // Spieler allein, verdraengte die zweite Rolle die erste - und das ist genau der
        // Unterschied zur Tabelle raenge, wo es so gewollt ist.
        assertEquals(2, zeilen(anna));
    }

    @Test
    @DisplayName("Ein zweites Geben legt keine zweite Zeile an und aendert die erste nicht")
    void zweitesGebenLaesstDieZeileStehen() throws SQLException {
        UUID anna = UUID.randomUUID();
        UUID erster = UUID.randomUUID();
        UUID zweiter = UUID.randomUUID();
        assertTrue(team.gib(anna, MODERATOR, MITTAGS, erster), "die Vorbereitung");

        assertEquals(false, team.gib(anna, MODERATOR, SPAETER, zweiter));

        // DIE PROBE AUF INSERT IGNORE. Mit ON DUPLICATE KEY UPDATE stuende hier auch
        // eine Zeile, und der Rueckgabewert waere derselbe - aber vergeben_am und
        // vergeber waeren die des zweiten Versuchs. Gelesen wird mit rohem SQL, weil die
        // Spalten selbst die Zusage sind.
        assertEquals(1, zeilen(anna));
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "SELECT vergeben_am, vergeber FROM team WHERE spieler = ?"
                        + " AND rolle = ?")) {
            anweisung.setString(1, anna.toString());
            anweisung.setString(2, MODERATOR);
            try (var ergebnis = anweisung.executeQuery()) {
                assertTrue(ergebnis.next(), "die Zeile muss da sein");
                assertEquals(MITTAGS, ergebnis.getTimestamp("vergeben_am",
                        Datenbank.utcKalender()).toInstant(),
                        "die ERSTE Vergabezeit steht noch in der Spalte");
                assertEquals(erster.toString(), ergebnis.getString("vergeber"),
                        "und der erste Vergeber auch");
            }
        }
    }

    @Test
    @DisplayName("Ein Entzug loescht die Zeile und nicht nur ihren Inhalt")
    void entzugLoeschtDieZeile() throws SQLException {
        UUID anna = UUID.randomUUID();
        assertTrue(team.gib(anna, MODERATOR, MITTAGS, null), "die Vorbereitung");

        assertTrue(team.nimm(anna, MODERATOR));

        // Eine Fassung, die stattdessen eine Spalte auf NULL setzte, liesse die Zeile
        // stehen - und alle() nennte den Spieler weiter im Team.
        assertEquals(0, zeilen(anna));
    }

    @Test
    @DisplayName("Die Konsole schreibt ein echtes SQL NULL in die Vergeberspalte")
    void konsoleSchreibtNull() throws SQLException {
        UUID anna = UUID.randomUUID();

        assertTrue(team.gib(anna, MODERATOR, MITTAGS, null));

        // Und keinen Text "null". Dieselbe Zeile kommt beim Lesen durch
        // UUID.fromString, und ein Text "null" waere dort ein Wurf mitten im Beitritt.
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "SELECT vergeber FROM team WHERE spieler = ?")) {
            anweisung.setString(1, anna.toString());
            try (var ergebnis = anweisung.executeQuery()) {
                assertTrue(ergebnis.next(), "die Zeile muss da sein");
                assertNull(ergebnis.getString("vergeber"));
            }
        }
        assertTrue(team.lies(anna).zeilen().get(0).vergeber().isEmpty(),
                "und gelesen wird daraus wieder: die Konsole");
    }

    @Test
    @DisplayName("Eine grossgeschriebene Rolle landet kleingeschrieben in der Spalte")
    void grossgeschriebeneRolleWirdKlein() throws SQLException {
        UUID anna = UUID.randomUUID();

        assertTrue(team.gib(anna, "Moderator", MITTAGS, null));

        // Die Kollation dieser Spalte vergleicht ohne Ruecksicht auf Gross und Klein,
        // SPEICHERT aber die Schreibweise des ersten Schreibzugs. Ohne das Kleinschreiben
        // in der Ablage bekaeme der Kern beim Lesen ein "Moderator" zurueck - und
        // Teamstand.Zeile klagt darueber, zu Recht: Teamwerte.rolle faende die Rolle
        // nicht wieder, der Spieler haette sie also ohne ihre Rechte.
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "SELECT rolle FROM team WHERE spieler = ?")) {
            anweisung.setString(1, anna.toString());
            try (var ergebnis = anweisung.executeQuery()) {
                assertTrue(ergebnis.next(), "die Zeile muss da sein");
                assertEquals(MODERATOR, ergebnis.getString("rolle"));
            }
        }
    }

    /** Wie viele Zeilen dieser Spieler hat - mit rohem SQL, nicht ueber die Ablage. */
    private static int zeilen(UUID spieler) throws SQLException {
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "SELECT COUNT(*) FROM team WHERE spieler = ?")) {
            anweisung.setString(1, spieler.toString());
            try (var ergebnis = anweisung.executeQuery()) {
                assertTrue(ergebnis.next(), "COUNT gibt immer eine Zeile");
                return ergebnis.getInt(1);
            }
        }
    }
}
