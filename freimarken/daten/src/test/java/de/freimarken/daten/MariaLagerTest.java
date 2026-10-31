package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.BesitzLager;
import de.freimarken.kern.BesitzLagerVertrag;
import de.freimarken.kern.Parzelle;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Vertrag wie beim Lager im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Das ist der Punkt, an dem sich der Aufwand mit {@link BesitzLagerVertrag} auszahlt:
 * Der Umstieg auf MariaDB ist kein Vertrauensakt, sondern ein Testlauf. Weicht die
 * Datenbankfassung in irgendeinem Punkt ab, faellt es hier auf und nicht im Betrieb.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen - sind sie da, aber die Datenbank
 * antwortet nicht, scheitern sie. Stillschweigend durchwinken waere das Schlimmste: eine
 * gruene Suite, die nie eine Datenbank gesehen hat.
 */
@DisplayName("Lager in MariaDB")
class MariaLagerTest extends BesitzLagerVertrag {

    private static Datenbank datenbank;
    private static MariaLager lager;

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
        lager = new MariaLager(datenbank);
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
    protected BesitzLager lager() {
        return lager;
    }

    // --- Was nur die Datenbank leisten kann -------------------------------------

    @Test
    @DisplayName("Der eindeutige Schluessel haelt, nicht nur die Pruefung im Programm")
    void schluesselHaelt() {
        UUID anna = UUID.randomUUID();
        lager.anlegen(WELT, 42, 42, anna, 0L);

        // Zwischen Nachsehen und Schreiben passt immer ein zweiter Spieler. Nur die
        // Datenbank kann das wirklich verhindern - und sie tut es hier.
        try {
            lager.anlegen(WELT, 42, 42, UUID.randomUUID(), 0L);
            throw new AssertionError("Der eindeutige Schluessel hat nicht gegriffen");
        } catch (IllegalStateException erwartet) {
            assertTrue(erwartet.getMessage().contains("bereits vergeben"));
        }
        assertEquals(anna, lager.finde(WELT, 42, 42).orElseThrow().eigentuemer());
    }

    @Test
    @DisplayName("Alles uebersteht einen Verbindungsabbruch - die Datenbank ist die Wahrheit")
    void ueberstehtNeustart() throws SQLException {
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        Parzelle parzelle = lager.anlegen(WELT, 7, 11, anna, 25_000L);
        lager.mitbauerSetzen(parzelle.id(), Set.of(bert));

        // Verbindung wegwerfen und neu aufbauen - so, wie es ein Serverneustart taete.
        datenbank.close();

        Parzelle danach = lager.finde(WELT, 7, 11).orElseThrow();
        assertEquals(anna, danach.eigentuemer());
        assertEquals(25_000L, danach.preis());
        assertEquals(Set.of(bert), danach.mitbauer());
        assertEquals(parzelle.id(), danach.id());
    }

    @Test
    @DisplayName("Der Zeitpunkt kommt in UTC zurueck, nicht in der Zeitzone der Sitzung")
    void zeitBleibtUtc() {
        Parzelle vorher = lager.anlegen(WELT, 3, 4, UUID.randomUUID(), 0L);
        Parzelle gelesen = lager.finde(WELT, 3, 4).orElseThrow();

        long abweichung = Math.abs(
                gelesen.erworbenAm().toEpochMilli() - vorher.erworbenAm().toEpochMilli());
        assertTrue(abweichung < 1000,
                "Geschrieben " + vorher.erworbenAm() + ", gelesen " + gelesen.erworbenAm()
                        + " - das ist eine Zeitzonenverschiebung, keine Rundung");
    }

    @Test
    @DisplayName("Mitbauer verschwinden mit der Parzelle - der Fremdschluessel raeumt mit")
    void mitbauerVerschwindenMit() throws SQLException {
        Parzelle parzelle = lager.anlegen(WELT, 12, 12, UUID.randomUUID(), 0L);
        lager.mitbauerSetzen(parzelle.id(), Set.of(UUID.randomUUID(), UUID.randomUUID()));

        lager.entfernen(parzelle.id());

        try (var anweisung = datenbank.verbindung().createStatement();
             var ergebnis = anweisung.executeQuery(
                     "SELECT COUNT(*) FROM parzellen_mitbauer WHERE parzelle_id = "
                             + parzelle.id())) {
            ergebnis.next();
            assertEquals(0, ergebnis.getInt(1), "verwaiste Mitbauer bleiben liegen");
        }
    }

    @Test
    @DisplayName("Migration laeuft mehrfach, ohne etwas kaputtzumachen")
    void migrationIstWiederholbar() throws SQLException {
        Parzelle parzelle = lager.anlegen(WELT, 20, 20, UUID.randomUUID(), 0L);

        datenbank.migriere();
        datenbank.migriere();

        assertEquals(parzelle.id(), lager.finde(WELT, 20, 20).orElseThrow().id());
    }
}
