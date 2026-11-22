package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Anmeldeablage;
import de.freimarken.kern.AnmeldeVertrag;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Anmeldevertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaRaengeTest}: Eine
 * gruene Suite, die nie eine Datenbank gesehen hat, waere das Schlimmste.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann - und jede
 * haengt an einer Entscheidung dieses Schrittes: <b>eine Zeile je Spieler, sie entsteht
 * erst bei der Annahme, und der Zeitpunkt der ersten Zustimmung bleibt stehen.</b> Gerade
 * das Letzte ist umsetzungsfrei nur an der Wirkung zu sehen; dass die Tabelle wirklich
 * eine Zeile hat und keine zweite, sieht man nur hier.
 */
@DisplayName("Anmeldungen in MariaDB")
class MariaAnmeldungenTest extends AnmeldeVertrag {

    private static Datenbank datenbank;
    private static MariaAnmeldungen anmeldungen;

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
        anmeldungen = new MariaAnmeldungen(datenbank);
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
    protected Anmeldeablage ablage() {
        // Immer dieselbe Ablage, aber vor jedem Test eine leere Tabelle - leereAlles()
        // laeuft in @BeforeEach. Dasselbe Vorgehen wie bei MariaRaengeTest.
        return anmeldungen;
    }

    @Test
    @DisplayName("Wer nie angenommen hat, hat KEINE Zeile")
    void keineAnnahmeKeineZeile() throws SQLException {
        UUID anna = UUID.randomUUID();

        assertNull(anmeldungen.lies(anna).fassung());

        // Umsetzungsfrei ist das nicht zu sehen: Der Vertrag prueft nur, dass der Stand
        // leer ist - eine Fassung, die beim Lesen eine Zeile anlegte, waere dort gruen.
        assertEquals(0, zeilen(anna), "ein Lesen darf keine Zeile anlegen");
    }

    @Test
    @DisplayName("Eine zweite Annahme schreibt dieselbe Zeile fort")
    void zweiteAnnahmeSchreibtDieselbeZeile() throws SQLException {
        UUID anna = UUID.randomUUID();

        anmeldungen.nimmAn(new Anmeldeablage.Antrag(anna, ERSTE, MITTAGS));
        anmeldungen.nimmAn(new Anmeldeablage.Antrag(anna, ZWEITE, SPAETER));

        // DIE DRITTE ZUSAGE in der Tabelle: eine Zeile je Spieler, kein Verlauf. Mit
        // PRIMARY KEY (spieler, fassung) staenden hier zwei, und die Frage "hat er die
        // heutigen Regeln angenommen" haette zwei Antworten.
        assertEquals(1, zeilen(anna), "es darf genau EINE Zeile geben");
        assertEquals(ZWEITE, anmeldungen.lies(anna).fassung());
    }

    @Test
    @DisplayName("Dieselbe Fassung zweimal laesst den Zeitpunkt der ERSTEN stehen")
    void zeitpunktDerErstenBleibt() throws SQLException {
        UUID anna = UUID.randomUUID();

        anmeldungen.nimmAn(new Anmeldeablage.Antrag(anna, ERSTE, MITTAGS));
        anmeldungen.nimmAn(new Anmeldeablage.Antrag(anna, ERSTE, SPAETER));

        // IN DER SPALTE nachgesehen und nicht nur am Stand: Ein blindes
        // ON DUPLICATE KEY UPDATE machte den Nachweis bei jedem Beitritt juenger, als er
        // ist - und ein Nachweis mit falschem Datum ist keiner.
        assertEquals(MITTAGS, gelesenerZeitpunkt(anna));
    }

    @Test
    @DisplayName("Der Zeitpunkt steht OHNE Zonenversatz in der Spalte")
    void zeitpunktOhneZonenversatz() throws SQLException {
        UUID anna = UUID.randomUUID();

        anmeldungen.nimmAn(new Anmeldeablage.Antrag(anna, ERSTE, MITTAGS));

        // Die Zusage des ganzen Schemas: JEDE Zeitspalte in UTC. Haette diese Klasse
        // keinen Kalender, rechnete der Treiber in die Zone der JVM - fuer sich stimmig,
        // aber neben raenge gelesen um den Versatz daneben. Genau das ist am 10.11.2026
        // im Blockbuch aufgefallen.
        assertEquals(MITTAGS, gelesenerZeitpunkt(anna));
        assertEquals("2026-11-21 12:00:00.000", alsText(anna),
                "in der Spalte muss die UTC-Zeit stehen und nicht die Ortszeit");
    }

    @Test
    @DisplayName("Kein Fremdschluessel aufs Konto - die Annahme geht ohne Kasse")
    void keinFremdschluesselAufsKonto() throws SQLException {
        UUID niemandMitKonto = UUID.randomUUID();

        // Ein RESTRICT auf kassen brachte die Annahme zum Scheitern, sobald eine
        // Kontoeroeffnung einmal fehlgeschlagen ist. Angenommen hat ein Mensch, nicht
        // ein Guthaben.
        assertTrue(anmeldungen.nimmAn(
                new Anmeldeablage.Antrag(niemandMitKonto, ERSTE, MITTAGS)));
        assertEquals(1, zeilen(niemandMitKonto));
    }

    @Test
    @DisplayName("leereAlles raeumt die Annahmen mit weg")
    void leereAllesRaeumtMitWeg() throws SQLException {
        UUID anna = UUID.randomUUID();
        anmeldungen.nimmAn(new Anmeldeablage.Antrag(anna, ERSTE, MITTAGS));

        datenbank.leereAlles();

        // Ohne diese Zeile in leereAlles wanderte eine Zustimmung von einer Probe in die
        // naechste, und eine Probe ueber den ERSTEN Beitritt faende ihn schon
        // angemeldet - das Formular kaeme nie, und die Probe waere gruen.
        assertEquals(0, zeilen(anna));
    }

    /** Wie viele Zeilen dieser Spieler in der Tabelle hat - mit rohem SQL gezaehlt. */
    private static int zeilen(UUID spieler) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(
                "SELECT COUNT(*) FROM anmeldungen WHERE spieler = ?")) {
            anfrage.setString(1, spieler.toString());
            try (var ergebnis = anfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getInt(1);
            }
        }
    }

    /** Der abgelegte Zeitpunkt, als Instant gelesen. */
    private static Instant gelesenerZeitpunkt(UUID spieler) {
        return anmeldungen.lies(spieler).angenommenAm();
    }

    /** Der Zeitpunkt, wie die Spalte ihn als TEXT traegt - ohne jede Umrechnung. */
    private static String alsText(UUID spieler) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(
                "SELECT CAST(angenommen_am AS CHAR) FROM anmeldungen WHERE spieler = ?")) {
            anfrage.setString(1, spieler.toString());
            try (var ergebnis = anfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getString(1);
            }
        }
    }
}
