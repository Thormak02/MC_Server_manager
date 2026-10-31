package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Kasse;
import de.freimarken.kern.KasseVertrag;
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
 * Derselbe Kassenvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaLagerTest}: Eine gruene
 * Suite, die nie eine Datenbank gesehen hat, waere das Schlimmste.
 */
@DisplayName("Kasse in MariaDB")
class MariaKasseTest extends KasseVertrag {

    private static Datenbank datenbank;
    private static MariaKasse kasse;

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
        kasse = new MariaKasse(datenbank, START);
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
    protected Kasse kasse() {
        return kasse;
    }

    @Test
    @DisplayName("Der Stand uebersteht einen Verbindungsabbruch")
    void ueberstehtAbbruch() throws SQLException {
        UUID anna = UUID.randomUUID();
        assertTrue(kasse.abbuchen(anna, 12_345L));

        datenbank.close();

        assertEquals(START - 12_345L, kasse.guthaben(anna),
                "Die Datenbank ist die Wahrheit, nicht der Arbeitsspeicher");
    }

    @Test
    @DisplayName("Zwei Kassen auf derselben Datenbank sehen denselben Stand")
    void keinEigenesGedaechtnis() {
        UUID anna = UUID.randomUUID();
        MariaKasse zweite = new MariaKasse(datenbank, START);

        assertTrue(kasse.abbuchen(anna, 5_000L));

        assertEquals(START - 5_000L, zweite.guthaben(anna));
    }

    @Test
    @DisplayName("Lesen legt keine Zeile an - erst eine Buchung tut das")
    void lesenLegtNichtsAn() throws SQLException {
        UUID anna = UUID.randomUUID();

        assertEquals(START, kasse.guthaben(anna));
        assertEquals(0, zeilen(), "eine Abfrage darf keine Daten erzeugen");

        kasse.gutschreiben(anna, 1L);
        assertEquals(1, zeilen());
    }

    @Test
    @DisplayName("Die Deckungspruefung liegt in der Abfrage, nicht im Programm")
    void deckungStehtInSql() throws SQLException {
        UUID anna = UUID.randomUUID();
        assertTrue(kasse.abbuchen(anna, START));

        assertFalse(kasse.abbuchen(anna, 1L));

        assertEquals(0L, kasse.guthaben(anna));
        assertEquals(0L, markLaut(anna), "auch in der Tabelle selbst, nicht nur im Rueckgabewert");
    }

    private int zeilen() throws SQLException {
        try (Statement anweisung = datenbank.verbindung().createStatement();
             var ergebnis = anweisung.executeQuery("SELECT COUNT(*) FROM kassen")) {
            ergebnis.next();
            return ergebnis.getInt(1);
        }
    }

    private long markLaut(UUID spieler) throws SQLException {
        try (Statement anweisung = datenbank.verbindung().createStatement();
             var ergebnis = anweisung.executeQuery(
                     "SELECT mark FROM kassen WHERE spieler = '" + spieler + "'")) {
            return ergebnis.next() ? ergebnis.getLong(1) : -1L;
        }
    }
}
