package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Rundgangsablage;
import de.freimarken.kern.RundgangVertrag;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Rundgangsvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaRaengeTest}.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann: dass eine
 * Zeile je Spieler UND Station steht, dass ein zweiter Schritt keine zweite anlegt und
 * dass der Zeitpunkt ohne Zonenversatz in der Spalte liegt.
 */
@DisplayName("Rundgang in MariaDB")
class MariaRundgangTest extends RundgangVertrag {

    private static Datenbank datenbank;
    private static MariaRundgang rundgang;

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
        rundgang = new MariaRundgang(datenbank);
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
    protected Rundgangsablage ablage() {
        return rundgang;
    }

    @Test
    @DisplayName("Wer nirgends war, hat KEINE Zeile")
    void keinSchrittKeineZeile() throws SQLException {
        UUID anna = UUID.randomUUID();

        assertEquals(java.util.Set.of(), rundgang.gesehen(anna));

        // Umsetzungsfrei ist das nicht zu sehen: Der Vertrag prueft nur die leere Menge -
        // eine Fassung, die beim Lesen eine Zeile anlegte, waere dort gruen.
        assertEquals(0, zeilen(anna), "ein Lesen darf keine Zeile anlegen");
    }

    @Test
    @DisplayName("Zwei Stationen sind ZWEI Zeilen, dieselbe zweimal ist EINE")
    void eineZeileJeStation() throws SQLException {
        UUID anna = UUID.randomUUID();

        rundgang.merke(anna, BUERGERAMT, MITTAGS);
        rundgang.merke(anna, ZUNFTHAUS, SPAETER);
        rundgang.merke(anna, BUERGERAMT, SPAETER);

        // DER HAUPTSCHLUESSEL IST DER RIEGEL. Mit PRIMARY KEY (spieler) allein staende
        // hier eine Zeile, und der Rundgang waere mit der ersten Station fertig.
        assertEquals(2, zeilen(anna), "zwei Stationen, zwei Zeilen");
    }

    @Test
    @DisplayName("Der Zeitpunkt der ERSTEN Station bleibt stehen")
    void zeitpunktDerErstenBleibt() throws SQLException {
        UUID anna = UUID.randomUUID();

        rundgang.merke(anna, BUERGERAMT, MITTAGS);
        rundgang.merke(anna, BUERGERAMT, SPAETER);

        // INSERT IGNORE schreibt beim zweiten Mal gar nicht - anders als ein
        // ON DUPLICATE KEY UPDATE, das den Zeitpunkt juenger machte, als er ist.
        assertEquals("2026-11-21 12:00:00.000", alsText(anna, BUERGERAMT));
    }

    @Test
    @DisplayName("Der Zeitpunkt steht OHNE Zonenversatz in der Spalte")
    void zeitpunktOhneZonenversatz() throws SQLException {
        UUID anna = UUID.randomUUID();

        rundgang.merke(anna, BUERGERAMT, MITTAGS);

        // Die Zusage des ganzen Schemas: JEDE Zeitspalte in UTC. Ohne Kalender rechnete
        // der Treiber in die Zone der JVM - fuer sich stimmig, aber neben einer anderen
        // Tabelle gelesen um den Versatz daneben.
        assertEquals("2026-11-21 12:00:00.000", alsText(anna, BUERGERAMT));
    }

    @Test
    @DisplayName("Kein Fremdschluessel aufs Konto - der Rundgang geht ohne Kasse")
    void keinFremdschluesselAufsKonto() throws SQLException {
        UUID niemandMitKonto = UUID.randomUUID();

        assertTrue(rundgang.merke(niemandMitKonto, BUERGERAMT, MITTAGS));
        assertEquals(1, zeilen(niemandMitKonto));
    }

    @Test
    @DisplayName("leereAlles raeumt den Rundgang mit weg")
    void leereAllesRaeumtMitWeg() throws SQLException {
        UUID anna = UUID.randomUUID();
        rundgang.merke(anna, BUERGERAMT, MITTAGS);

        datenbank.leereAlles();

        // Ohne diese Zeile in leereAlles wanderten gesehene Stationen von einer Probe in
        // die naechste, und eine Probe ueber den ERSTEN Schritt waere gruen, ohne etwas
        // zu pruefen.
        assertEquals(0, zeilen(anna));
    }

    /** Wie viele Zeilen dieser Spieler hat - mit rohem SQL gezaehlt. */
    private static int zeilen(UUID spieler) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(
                "SELECT COUNT(*) FROM rundgang WHERE spieler = ?")) {
            anfrage.setString(1, spieler.toString());
            try (var ergebnis = anfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getInt(1);
            }
        }
    }

    /** Der Zeitpunkt, wie die Spalte ihn als TEXT traegt - ohne jede Umrechnung. */
    private static String alsText(UUID spieler, String station) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(
                "SELECT CAST(gesehen_am AS CHAR) FROM rundgang"
                        + " WHERE spieler = ? AND station = ?")) {
            anfrage.setString(1, spieler.toString());
            anfrage.setString(2, station);
            try (var ergebnis = anfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getString(1);
            }
        }
    }
}
