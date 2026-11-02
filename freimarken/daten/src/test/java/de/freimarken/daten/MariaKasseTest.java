package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Buchungsgrund;
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
        assertTrue(kasse.abbuchen(anna, 12_345L, Buchungsgrund.PARZELLE_GEKAUFT));

        datenbank.close();

        assertEquals(START - 12_345L, kasse.guthaben(anna),
                "Die Datenbank ist die Wahrheit, nicht der Arbeitsspeicher");
    }

    @Test
    @DisplayName("Zwei Kassen auf derselben Datenbank sehen denselben Stand")
    void keinEigenesGedaechtnis() {
        UUID anna = UUID.randomUUID();
        MariaKasse zweite = new MariaKasse(datenbank, START);

        assertTrue(kasse.abbuchen(anna, 5_000L, Buchungsgrund.PARZELLE_GEKAUFT));

        assertEquals(START - 5_000L, zweite.guthaben(anna));
    }

    @Test
    @DisplayName("Lesen legt keine Zeile an - erst eine Buchung tut das")
    void lesenLegtNichtsAn() throws SQLException {
        UUID anna = UUID.randomUUID();

        assertEquals(START, kasse.guthaben(anna));
        assertEquals(0, zeilen(), "eine Abfrage darf keine Daten erzeugen");
        assertEquals(0, buchungen(), "und auch keine Buchung");

        kasse.gutschreiben(anna, 1L, Buchungsgrund.PARZELLE_ERSTATTET);
        assertEquals(1, zeilen());
        assertEquals(2, buchungen(), "Eroeffnung und Gutschrift");
    }

    @Test
    @DisplayName("Der Zeitpunkt einer Buchung kommt in UTC zurueck, nicht in Ortszeit")
    void zeitpunktIstUtc() {
        // Die Spalte haelt UTC. Ohne Kalender in UTC legte der Treiber die Zonenzeit der
        // JVM darueber - auf diesem Rechner waere jede Buchung dann eine Stunde verschoben,
        // im Sommer zwei.
        //
        // Das Fenster ist fuenf MINUTEN weit, und das ist Absicht: Die Uhr des
        // Datenbankrechners laeuft nicht synchron mit dieser (am 02.11.2026 gemessen:
        // einige Sekunden Versatz). Ein engeres Fenster wuerde diesen Versatz als Fehler
        // melden; ein weiteres wuerde eine Zonenverschiebung durchlassen. Fuenf Minuten
        // trennen beides sauber, weil die kleinste Zone eine Viertelstunde ist.
        UUID anna = UUID.randomUUID();
        java.time.Instant vorher = java.time.Instant.now().minusSeconds(300);

        kasse.eroeffne(anna);

        var zeile = kasse.verlauf(anna, 1).get(0);
        java.time.Instant nachher = java.time.Instant.now().plusSeconds(300);
        assertTrue(zeile.zeitpunkt().isAfter(vorher) && zeile.zeitpunkt().isBefore(nachher),
                "Zeitpunkt " + zeile.zeitpunkt() + " liegt nicht zwischen " + vorher
                        + " und " + nachher + " - das ist eine Zonenverschiebung, kein"
                        + " Uhrversatz");
    }

    @Test
    @DisplayName("Ein abgelehntes Abbuchen rollt zurueck - keine Zeile, kein Stand")
    void abgelehntRolltZurueck() throws SQLException {
        // Der Punkt, an dem die Transaktion sich beweist: Das UPDATE scheitert an der
        // Deckung, und danach darf weder eine Buchungszeile noch ein veraenderter Stand
        // dastehen.
        UUID anna = UUID.randomUUID();
        kasse.eroeffne(anna);
        int vorher = buchungen();

        assertFalse(kasse.abbuchen(anna, START + 1, Buchungsgrund.PARZELLE_GEKAUFT));

        assertEquals(vorher, buchungen(), "keine Zeile nach einer abgelehnten Buchung");
        assertEquals(START, markLaut(anna));
    }

    @Test
    @DisplayName("Der Altbestand kommt als bestand-Buchung ins Buch")
    void altbestandWirdUebernommen() throws SQLException {
        // Schemaschritt 8. Eine Kassenzeile von Hand, ohne Buchung - so sahen beim
        // Einfuehren des Buchs alle vorhandenen Konten aus.
        //
        // Der Schritt muss dafuer zurueckgesetzt werden: migriere() fuehrt nur aus, was
        // ueber dem vermerkten Stand liegt, und der steht hier schon auf 8. Genau das war
        // mein erster Fehlschluss - der zweite Aufruf tat nichts, und das ist richtig.
        UUID alt = UUID.randomUUID();
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "INSERT INTO kassen (spieler, mark) VALUES (?, ?)")) {
            anweisung.setString(1, alt.toString());
            anweisung.setLong(2, 77_000L);
            anweisung.executeUpdate();
        }

        setzeSchrittZurueck(8);
        datenbank.migriere();

        var verlauf = kasse.verlauf(alt, 10);
        assertEquals(1, verlauf.size(), "der Altbestand muss im Buch stehen");
        assertEquals(Buchungsgrund.BESTAND, verlauf.get(0).grund());
        assertEquals(77_000L, verlauf.get(0).betrag());
        assertEquals(77_000L, verlauf.get(0).standNachher());

        // Und ein zweiter Lauf desselben Schritts darf nichts hinzufuegen: migriere()
        // legt keine Transaktion um Anweisung und Standfortschreibung, also muss jeder
        // Satz einen Abbruch dazwischen folgenlos ueberstehen.
        setzeSchrittZurueck(8);
        datenbank.migriere();
        assertEquals(1, kasse.verlauf(alt, 10).size(),
                "ein zweiter Lauf darf nichts hinzufuegen");
    }

    private void setzeSchrittZurueck(int schritt) throws SQLException {
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "DELETE FROM schema_stand WHERE schritt = ?")) {
            anweisung.setInt(1, schritt);
            anweisung.executeUpdate();
        }
    }

    private int buchungen() throws SQLException {
        try (var anweisung = datenbank.verbindung().createStatement();
                var ergebnis = anweisung.executeQuery("SELECT COUNT(*) FROM buchungen")) {
            ergebnis.next();
            return ergebnis.getInt(1);
        }
    }

    @Test
    @DisplayName("Die Deckungspruefung liegt in der Abfrage, nicht im Programm")
    void deckungStehtInSql() throws SQLException {
        UUID anna = UUID.randomUUID();
        assertTrue(kasse.abbuchen(anna, START, Buchungsgrund.PARZELLE_GEKAUFT));

        assertFalse(kasse.abbuchen(anna, 1L, Buchungsgrund.PARZELLE_GEKAUFT));

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
