package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.freimarken.kern.Bonusablage;
import de.freimarken.kern.Bonusstand;
import de.freimarken.kern.BonusVertrag;
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
 * Derselbe Bonusvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaSpielzeitTest}: Eine
 * gruene Suite, die nie eine Datenbank gesehen hat, waere das Schlimmste.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann: dass
 * {@code ON DUPLICATE KEY UPDATE} wirklich SETZT statt zu addieren, dass dabei EINE Zeile
 * entsteht, und dass ein NULL in {@code letzter_tag} ein NULL bleibt.
 */
@DisplayName("Tagesbonus in MariaDB")
class MariaBonusTest extends BonusVertrag {

    private static Datenbank datenbank;
    private static MariaBonus bonus;

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
        bonus = new MariaBonus(datenbank);
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
    protected Bonusablage ablage() {
        return bonus;
    }

    @Test
    @DisplayName("Zwei Schreibzuege ergeben EINE Zeile, nicht zwei")
    void zweiZuegeEineZeile() throws SQLException {
        UUID anna = UUID.randomUUID();

        bonus.schreibe(new Bonusstand(anna, GESTERN, 4, 4));
        bonus.schreibe(new Bonusstand(anna, HEUTE, 5, 5));

        assertEquals(1, zeilen(anna),
                "der Primaerschluessel ist der Spieler allein - zwei Zeilen hiessen zwei"
                        + " Serien, und das Lesen nimmt dann eine davon");
    }

    @Test
    @DisplayName("Das NULL in letzter_tag steht wirklich als NULL in der Spalte")
    void nullIstNull() throws SQLException {
        // Geprueft in der SPALTE und nicht nur am zurueckgelesenen Stand: Ein Treiber,
        // der daraus ein Datum von 1970 macht, faellt beim Lesen auf - aber ein
        // Schreibzug, der 1970 in die Spalte setzt, nicht. Dann waere die Luecke zum
        // heutigen Tag ueber zwanzigtausend Tage, und die Serie riss fuer immer.
        UUID anna = UUID.randomUUID();

        bonus.schreibe(new Bonusstand(anna, null, 0, 9));

        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT letzter_tag FROM tagesbonus WHERE spieler = '"
                             + anna + "'")) {
            assertEquals(true, ergebnis.next(), "die Zeile muss da sein");
            ergebnis.getObject("letzter_tag");
            assertEquals(true, ergebnis.wasNull(),
                    "in der Spalte muss NULL stehen und kein Datum");
        }
        assertNull(bonus.lies(anna).letzterTag());
    }

    @Test
    @DisplayName("Die Serie haengt an KEINEM Fremdschluessel - sie darf ohne Konto bestehen")
    void keinFremdschluessel() {
        // Eine Serie kann entstehen, bevor ein Konto eroeffnet ist: Der Bonus wird beim
        // Beitritt gezahlt, und das Eroeffnen besorgt die Gutschrift selbst. Ein
        // Fremdschluessel machte aus der Reihenfolge zweier Schreibzuege eine Zusage.
        UUID ohneKonto = UUID.randomUUID();

        bonus.schreibe(new Bonusstand(ohneKonto, HEUTE, 1, 1));

        assertEquals(1, bonus.lies(ohneKonto).serie());
    }

    @Test
    @DisplayName("leereAlles raeumt die Serien mit weg")
    void leereAllesRaeumtAuf() throws SQLException {
        // Ohne diese Zeile in leereAlles wanderten Serien von einem Test in den
        // naechsten, und eine Probe ueber den ERSTEN Bonus eines Spielers faende dort
        // schon eine Serie. Die UUIDs sind zufaellig, also faellt es nur hier auf.
        UUID anna = UUID.randomUUID();
        bonus.schreibe(new Bonusstand(anna, HEUTE, 5, 5));

        datenbank.leereAlles();

        assertEquals(0, zeilen(anna));
        assertEquals(0, bonus.lies(anna).serie());
    }

    private int zeilen(UUID spieler) throws SQLException {
        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT COUNT(*) FROM tagesbonus WHERE spieler = '"
                             + spieler + "'")) {
            ergebnis.next();
            return ergebnis.getInt(1);
        }
    }
}
