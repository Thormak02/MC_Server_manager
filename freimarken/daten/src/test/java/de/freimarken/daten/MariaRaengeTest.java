package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Rangablage;
import de.freimarken.kern.RangVertrag;
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
 * Derselbe Rangvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaBerufeTest}: Eine
 * gruene Suite, die nie eine Datenbank gesehen hat, waere das Schlimmste.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann - und jede
 * einzelne haengt an einer Entscheidung dieses Schrittes: <b>eine Zeile je Spieler, und
 * sie entsteht erst beim ersten Aufstieg.</b> Umsetzungsfrei laesst sich das nicht
 * pruefen: Dass ein zweiter Aufstieg dieselbe Zeile fortschreibt und keine zweite anlegt,
 * sieht man nur hier.
 */
@DisplayName("Raenge in MariaDB")
class MariaRaengeTest extends RangVertrag {

    private static Datenbank datenbank;
    private static MariaRaenge raenge;

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
        raenge = new MariaRaenge(datenbank);
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
    protected Rangablage ablage() {
        // Immer dieselbe Ablage, aber vor jedem Test eine leere Tabelle - leereAlles()
        // laeuft in @BeforeEach. Dasselbe Vorgehen wie bei MariaBerufeTest.
        return raenge;
    }

    @Test
    @DisplayName("Wer nie aufgestiegen ist, hat KEINE Zeile")
    void keinAufstiegKeineZeile() throws SQLException {
        UUID anna = UUID.randomUUID();

        assertNull(raenge.lies(anna).rang());

        // Im Vertrag ist das nicht zu sehen: Eine Zeile mit rang='reisender' gaebe
        // dieselbe Antwort, sobald der Leser sie auf die unterste Stufe abbildet. Nur
        // hier faellt auf, dass das blosse LESEN keine Zeile anlegt - und genau das ist
        // der Unterschied zwischen einer Tabelle mit zwanzig Zeilen und einer mit jedem
        // Besucher, der je vorbeikam.
        assertEquals(0, zeilenVon(anna));
    }

    @Test
    @DisplayName("Der zweite Aufstieg schreibt dieselbe Zeile fort")
    void zweiterAufstiegSchreibtFort() throws SQLException {
        UUID anna = UUID.randomUUID();
        setze(anna, "siedler", MITTAGS);

        setze(anna, "buerger", SPAETER);

        // DAS ist der Grund fuer PRIMARY KEY (spieler). Mit (spieler, rang) stuenden hier
        // zwei Zeilen, und "welchen Rang hat er" haette zwei Antworten - die zweite waere
        // nach einer umsortierten Leiter die falsche.
        assertEquals(1, zeilenVon(anna), "EINE Zeile je Spieler");
        assertEquals("buerger", rangVon(anna));
    }

    @Test
    @DisplayName("Der Zeitpunkt landet unverschoben in der Spalte")
    void zeitpunktOhneVersatz() throws SQLException {
        UUID anna = UUID.randomUUID();

        setze(anna, "siedler", MITTAGS);

        // Als TEXT gelesen, wie die Datenbank ihn selbst formatiert - ohne Java
        // dazwischen. Mit setTimestamp ohne Kalender stuende hier die Ortszeit, und die
        // Angabe "seit wann bist du Siedler" waere eine Stunde daneben; im Sommer anders
        // als im Winter, und niemand bringt das mit einer Zeitzone zusammen.
        String text = spalteAlsText(anna, "erreicht_am");
        assertTrue(text.startsWith("2026-11-06 12:00:00"),
                "UTC muss unveraendert in der Spalte stehen, hier steht: " + text);
    }

    @Test
    @DisplayName("Ein Rang braucht kein Konto")
    void keinFremdschluesselAufsKonto() throws SQLException {
        UUID anna = UUID.randomUUID();

        setze(anna, "siedler", MITTAGS);

        // Ein RESTRICT brachte den Aufstieg zum Scheitern, sobald eine Kontoeroeffnung
        // einmal fehlgeschlagen ist; ein CASCADE loeschte Raenge stumm mit dem Konto.
        assertEquals(1, zeilenVon(anna));
        assertEquals(0, kassenzeilenVon(anna), "es entsteht kein Konto dabei");
    }

    @Test
    @DisplayName("leereAlles raeumt die Raenge mit weg")
    void leereAllesRaeumtDieRaenge() throws SQLException {
        UUID anna = UUID.randomUUID();
        setze(anna, "buerger", MITTAGS);

        datenbank.leereAlles();

        // Ohne diese Zeile in leereAlles wandern erreichte Raenge von einem Test in den
        // naechsten - und der geteilte Vertrag faende bei "ein unbekannter Spieler hat
        // keinen Rang" einen vorgefundenen vor.
        assertEquals(0, zeilenVon(anna));
        assertEquals(Datenbank.hoechsteFassung(), schemastand(),
                "der Schemastand darf dabei nicht fallen");
    }

    private void setze(UUID wer, String rang, Instant jetzt) {
        assertTrue(raenge.setze(new Rangablage.Antrag(wer, rang, jetzt),
                raenge.lies(wer)), "die Vorbereitung muss durchgehen: " + rang);
    }

    private String rangVon(UUID spieler) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(
                "SELECT rang FROM raenge WHERE spieler = ?")) {
            anfrage.setString(1, spieler.toString());
            try (var ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? ergebnis.getString(1) : "";
            }
        }
    }

    /** Eine Zeitspalte als Text, wie die Datenbank sie selbst formatiert. */
    private String spalteAlsText(UUID spieler, String spalte) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(
                "SELECT CAST(" + spalte + " AS CHAR) FROM raenge WHERE spieler = ?")) {
            anfrage.setString(1, spieler.toString());
            try (var ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? ergebnis.getString(1) : "";
            }
        }
    }

    private int zeilenVon(UUID spieler) throws SQLException {
        return zaehle("SELECT COUNT(*) FROM raenge WHERE spieler = ?", spieler);
    }

    private int kassenzeilenVon(UUID spieler) throws SQLException {
        return zaehle("SELECT COUNT(*) FROM kassen WHERE spieler = ?", spieler);
    }

    private int zaehle(String sql, UUID spieler) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, spieler.toString());
            try (var ergebnis = anfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getInt(1);
            }
        }
    }

    private int schemastand() throws SQLException {
        try (var anweisung = datenbank.verbindung().createStatement();
             var ergebnis = anweisung.executeQuery(
                     "SELECT COALESCE(MAX(schritt), 0) FROM schema_stand")) {
            ergebnis.next();
            return ergebnis.getInt(1);
        }
    }
}
