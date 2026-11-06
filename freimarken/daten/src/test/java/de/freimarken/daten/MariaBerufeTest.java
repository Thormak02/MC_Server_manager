package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Berufsablage;
import de.freimarken.kern.BerufeVertrag;
import de.freimarken.kern.Berufsstand;
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
 * Derselbe Berufsvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaSpielzeitTest}: Eine
 * gruene Suite, die nie eine Datenbank gesehen hat, waere das Schlimmste.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann - und jede
 * einzelne haengt an der Entscheidung, die dieser Schritt getroffen hat: <b>eine abgelegte
 * Zeile bleibt stehen, und sie behaelt ihr {@code abgelegt_am} auch dann, wenn der Beruf
 * spaeter wieder gewaehlt wird.</b> Umsetzungsfrei laesst sich das nicht pruefen: Dass die
 * Frist nach einem Wiederwaehlen noch gilt, sieht man im Vertrag, dass dafuer eine ZEILE
 * mit zwei Zeitspalten verantwortlich ist, nur hier.
 */
@DisplayName("Berufe in MariaDB")
class MariaBerufeTest extends BerufeVertrag {

    private static Datenbank datenbank;
    private static MariaBerufe berufe;

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
        berufe = new MariaBerufe(datenbank);
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
    protected Berufsablage ablage() {
        // Immer dieselbe Ablage, aber vor jedem Test eine leere Tabelle - leereAlles()
        // laeuft in @BeforeEach. Dasselbe Vorgehen wie bei MariaSpielzeitTest.
        return berufe;
    }

    @Test
    @DisplayName("Ein Verlassen laesst die Zeile stehen und setzt nur den Zustand")
    void verlassenLoeschtNicht() throws SQLException {
        UUID anna = UUID.randomUUID();
        waehle(anna, "bergmann", MITTAGS);

        berufe.verlasse(new Berufsablage.Antrag(anna, "bergmann", SPAETER),
                berufe.lies(anna));

        // Ein DELETE waere im Vertrag nicht aufgefallen: Die Liste der aktiven Berufe ist
        // in beiden Faellen leer. Nur hier ist zu sehen, dass die Zeile noch da ist - und
        // damit die Geschichte, die Teil G lesen will.
        assertEquals(1, zeilenVon(anna), "die Zeile bleibt stehen");
        assertEquals("abgelegt", zustandVon(anna, "bergmann"));
    }

    @Test
    @DisplayName("Ein wiedergewaehlter Beruf behaelt sein abgelegt_am")
    void abgelegtAmUeberlebtDasWiederwaehlen() throws SQLException {
        UUID anna = UUID.randomUUID();
        waehle(anna, "bergmann", MITTAGS);
        berufe.verlasse(new Berufsablage.Antrag(anna, "bergmann", MITTAGS),
                berufe.lies(anna));
        waehle(anna, "bergmann", SPAETER);

        // DAS ist der Grund fuer zwei Zeitspalten. Mit einer einzigen schriebe das
        // Wiederwaehlen den Zeitpunkt des Ablegens um, und Aufgeben-warten-wiederwaehlen
        // waere der Weg um Gebuehr UND Frist herum.
        assertEquals("aktiv", zustandVon(anna, "bergmann"));
        assertEquals(1, zeilenVon(anna), "und es ist noch EINE Zeile, keine zweite");
        assertNotNull(berufe.lies(anna).letztesAblegen(), "die Frist steht noch");
        assertEquals(MITTAGS, berufe.lies(anna).letztesAblegen(),
                "und zwar auf dem Zeitpunkt des Ablegens, nicht dem der neuen Wahl");
    }

    @Test
    @DisplayName("Der Zeitpunkt landet unverschoben in der Spalte")
    void zeitpunktOhneVersatz() throws SQLException {
        UUID anna = UUID.randomUUID();
        waehle(anna, "bergmann", MITTAGS);
        berufe.verlasse(new Berufsablage.Antrag(anna, "bergmann", MITTAGS),
                berufe.lies(anna));

        // Als TEXT gelesen, wie die Datenbank ihn selbst formatiert - ohne Java
        // dazwischen. Mit setTimestamp ohne Kalender stuende hier die Ortszeit, und die
        // Sperrfrist waere eine Stunde zu kurz oder zu lang; im Sommer anders als im
        // Winter, und niemand bringt das mit einer Zeitzone zusammen.
        String text = spalteAlsText(anna, "abgelegt_am");
        assertTrue(text.startsWith("2026-11-06 12:00:00"),
                "UTC muss unveraendert in der Spalte stehen, hier steht: " + text);
    }

    @Test
    @DisplayName("Ein Beruf braucht kein Konto")
    void keinFremdschluesselAufsKonto() throws SQLException {
        UUID anna = UUID.randomUUID();

        waehle(anna, "bergmann", MITTAGS);

        // Ein RESTRICT brachte das Waehlen zum Scheitern, sobald eine Kontoeroeffnung
        // einmal fehlgeschlagen ist; ein CASCADE loeschte Berufe stumm mit dem Konto.
        assertEquals(1, zeilenVon(anna));
        assertEquals(0, kassenzeilenVon(anna), "es entsteht kein Konto dabei");
    }

    @Test
    @DisplayName("leereAlles raeumt die Berufe mit weg")
    void leereAllesRaeumtDieBerufe() throws SQLException {
        UUID anna = UUID.randomUUID();
        waehle(anna, "bergmann", MITTAGS);

        datenbank.leereAlles();

        // Ohne diese Zeile in leereAlles wandern gewaehlte Berufe von einem Test in den
        // naechsten, und der geteilte Vertrag wird sporadisch rot - mit einem Grund, den
        // niemand findet.
        assertEquals(0, zeilenVon(anna));
        assertEquals(Datenbank.hoechsteFassung(), schemastand(),
                "der Schemastand darf dabei nicht fallen");
    }

    private void waehle(UUID wer, String beruf, Instant jetzt) {
        Berufsstand vorher = berufe.lies(wer);
        assertTrue(berufe.waehle(new Berufsablage.Antrag(wer, beruf, jetzt), vorher),
                "die Vorbereitung muss durchgehen: " + beruf);
    }

    private String zustandVon(UUID spieler, String beruf) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(
                "SELECT zustand FROM berufe WHERE spieler = ? AND beruf = ?")) {
            anfrage.setString(1, spieler.toString());
            anfrage.setString(2, beruf);
            try (var ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? ergebnis.getString(1) : "";
            }
        }
    }

    /** Eine Zeitspalte als Text, wie die Datenbank sie selbst formatiert. */
    private String spalteAlsText(UUID spieler, String spalte) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(
                "SELECT CAST(" + spalte + " AS CHAR) FROM berufe WHERE spieler = ?")) {
            anfrage.setString(1, spieler.toString());
            try (var ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? ergebnis.getString(1) : "";
            }
        }
    }

    private int zeilenVon(UUID spieler) throws SQLException {
        return zaehle("SELECT COUNT(*) FROM berufe WHERE spieler = ?", spieler);
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
