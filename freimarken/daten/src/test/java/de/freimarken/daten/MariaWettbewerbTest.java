package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Wettbewerbsablage;
import de.freimarken.kern.WettbewerbVertrag;
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
 * Derselbe Wettbewerbsvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaBonusTest}.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann: dass
 * {@code INSERT IGNORE} wirklich die Zeilenzahl 0 gibt, wenn die Zeile stand, dass der
 * Topf im SELBEN Zug gerechnet wird, und dass in der {@code lauf}-Spalte der
 * hereingegebene Tag steht und nicht der einer Zone.
 */
@DisplayName("Angelwettbewerb in MariaDB")
class MariaWettbewerbTest extends WettbewerbVertrag {

    private static Datenbank datenbank;
    private static MariaWettbewerb wettbewerb;

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
        wettbewerb = new MariaWettbewerb(datenbank);
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
    protected Wettbewerbsablage ablage() {
        return wettbewerb;
    }

    @Test
    @DisplayName("Zwei Anmeldungen ergeben EINE Zeile mit der ERSTEN Einzahlung")
    void zweiAnmeldungenEineZeile() throws SQLException {
        UUID anna = UUID.randomUUID();

        wettbewerb.meldeAn(anna, LAUF, 250L);
        wettbewerb.meldeAn(anna, LAUF, 9_999L);

        assertEquals(1, zeilenVon(anna),
                "der Hauptschluessel ist Spieler UND Lauf");
        assertEquals(250L, eingezahltVon(anna),
                "INSERT IGNORE darf die Einzahlung NICHT ueberschreiben - sonst waere"
                        + " der Topf der zweiten Zahl, die niemand gezahlt hat");
    }

    @Test
    @DisplayName("Der Abschluss rechnet Topf und Teilnehmer im SELBEN Zug")
    void abschlussRechnetMit() throws SQLException {
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        wettbewerb.meldeAn(anna, LAUF, 250L);
        wettbewerb.meldeAn(bert, LAUF, 300L);

        assertTrue(wettbewerb.merkeAbgeschlossen(LAUF, JETZT));

        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT topf, teilnehmer FROM wettbewerb_lauf WHERE lauf = '"
                             + LAUF + "'")) {
            assertTrue(ergebnis.next(), "die Zeile muss da sein");
            assertEquals(550L, ergebnis.getLong("topf"),
                    "die Summe der Einzahlungen - gerechnet in derselben Anweisung,"
                            + " damit die Zahl nicht zu einem anderen Stand gehoert");
            assertEquals(2, ergebnis.getInt("teilnehmer"));
        }
    }

    @Test
    @DisplayName("Der LAUF steht als DIESER Tag in der Spalte, nicht als der einer Zone")
    void laufStehtRichtigInDerSpalte() throws SQLException {
        // Dieselbe Falle wie bei MariaLose: Ein Treiber, der beim Schreiben um eine Zone
        // verschiebt und beim Lesen zurueck, ist in sich stimmig - auffallen wuerde es
        // erst, wenn das Dashboard des Managers die Spalte liest. Der Monatsanfang ist
        // der harte Fall: Ein Versatz um einen Tag dreht dort auch den Monat.
        UUID anna = UUID.randomUUID();
        java.time.LocalDate monatsanfang = java.time.LocalDate.of(2026, 3, 1);

        wettbewerb.meldeAn(anna, monatsanfang, 250L);
        wettbewerb.merkeAbgeschlossen(monatsanfang, JETZT);

        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT CAST(p.lauf AS CHAR) AS punkttag,"
                             + " CAST(l.lauf AS CHAR) AS lauftag"
                             + " FROM wettbewerb_punkte p JOIN wettbewerb_lauf l"
                             + " ON p.lauf = l.lauf WHERE p.spieler = '" + anna + "'")) {
            assertTrue(ergebnis.next(), "beide Zeilen muessen da sein und zusammenpassen");
            assertEquals("2026-03-01", ergebnis.getString("punkttag"));
            assertEquals("2026-03-01", ergebnis.getString("lauftag"));
        }
    }

    @Test
    @DisplayName("Der Abschluss schreibt genau EINE Zeile, auch bei drei Versuchen")
    void abschlussEineZeile() throws SQLException {
        UUID anna = UUID.randomUUID();
        wettbewerb.meldeAn(anna, LAUF, 250L);

        wettbewerb.merkeAbgeschlossen(LAUF, JETZT);
        assertFalse(wettbewerb.merkeAbgeschlossen(LAUF, JETZT.plusSeconds(60)));
        assertFalse(wettbewerb.merkeAbgeschlossen(LAUF, JETZT.plusSeconds(120)));

        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT COUNT(*) FROM wettbewerb_lauf")) {
            ergebnis.next();
            assertEquals(1, ergebnis.getInt(1),
                    "EINE Zeile - sie ist die Erlaubnis zum Auszahlen, und zwei davon"
                            + " waeren zwei Erlaubnisse");
        }
    }

    @Test
    @DisplayName("Punkte am Rand des INT laufen nicht ueber")
    void punkteLaufenNichtUeber() {
        // LEAST(punkte + ?, 2147483647) im SQL: Ein INT, der ueberlaeuft, ist in MariaDB
        // je nach Modus ein Fehler oder eine stumme Kappung - und ein NEGATIVER
        // Punktestand macht aus dem Letzten den Ersten.
        UUID anna = UUID.randomUUID();
        wettbewerb.meldeAn(anna, LAUF, 0L);
        wettbewerb.zaehlePunkte(anna, LAUF, Integer.MAX_VALUE - 1);

        assertEquals(java.util.Optional.of(Integer.MAX_VALUE),
                wettbewerb.zaehlePunkte(anna, LAUF, 100),
                "geklemmt bei der Hoechstzahl, nicht negativ");
    }

    private static int zeilenVon(UUID spieler) throws SQLException {
        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT COUNT(*) FROM wettbewerb_punkte WHERE spieler = '"
                             + spieler + "'")) {
            ergebnis.next();
            return ergebnis.getInt(1);
        }
    }

    private static long eingezahltVon(UUID spieler) throws SQLException {
        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT eingezahlt FROM wettbewerb_punkte WHERE spieler = '"
                             + spieler + "'")) {
            ergebnis.next();
            return ergebnis.getLong(1);
        }
    }
}
