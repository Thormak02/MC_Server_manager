package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.KasseVertrag;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DERSELBE Kassenvertrag ein zweites Mal - auf den Tabellen der GOLDMARK.
 *
 * <p><b>Das ist der Grund, aus dem die Goldmark keine eigene Umsetzung bekommt.</b>
 * {@link MariaKasse} bleibt EINE Klasse und bekommt die Tabellennamen als
 * {@link Kassentabellen} herein; dass die zweite Tabellenmenge wirklich traegt, prueft
 * darum derselbe geteilte Vertrag noch einmal - alle vierundzwanzig Zusagen, bis hin zu
 * der tragenden: "fuer ein eroeffnetes Konto ist die Summe aller Buchungen sein Stand".
 * Eine Kopie der Klasse haette eine zweite Fassung der Transaktionsfuehrung ergeben, und
 * zwei Fassungen derselben Sache weichen eines Tages ab.
 *
 * <p><b>Hier mit {@code START} als Startguthaben, obwohl die Goldmark im Betrieb KEINES
 * hat.</b> Das ist Absicht und kein Widerspruch: Der Vertrag prueft die Mechanik der
 * Tabellen, und er verlangt dafuer eine Kasse mit Startguthaben (seine erste Zusage ist
 * "ein unbekannter Spieler hat das Startguthaben"). Dass im Betrieb 0 steht, prueft
 * {@code GoldkasseTest} am Riegel - und zwar an der Stelle, an der die Entscheidung
 * sitzt.
 */
@DisplayName("Goldkasse in MariaDB")
class MariaGoldkasseTest extends KasseVertrag {

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
        kasse = new MariaKasse(datenbank, START, Kassentabellen.GOLDMARK);
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
    @DisplayName("Die Goldmark liegt in GOLDKASSEN und nicht in kassen")
    void eigeneTabelle() throws SQLException {
        UUID anna = UUID.randomUUID();

        kasse.gutschreiben(anna, 7L, Buchungsgrund.BESTAND);

        // DIE PROBE, DIE DEN GANZEN BAUSCHRITT RECHTFERTIGT. Lagen beide Waehrungen in
        // kassen, addierten sie sich zu einem Stand - und die Summenprobe des Vertrags
        // wuerde ihn BESTAETIGEN, obwohl er zwei Dinge zusammenwirft. Sie waere gruen und
        // das Geld falsch.
        assertEquals(START + 7L, zahl("SELECT goldmark FROM goldkassen WHERE spieler = ?",
                anna));
        assertEquals(0, zeilen("SELECT COUNT(*) FROM kassen WHERE spieler = ?", anna),
                "in der Markkasse darf dabei keine Zeile entstehen");
        assertEquals(0, zeilen("SELECT COUNT(*) FROM buchungen WHERE spieler = ?", anna),
                "und im Markbuch keine Zeile");
    }

    @Test
    @DisplayName("Beide Waehrungen desselben Spielers stehen sich nicht im Weg")
    void beideWaehrungenGetrennt() throws SQLException {
        UUID anna = UUID.randomUUID();
        MariaKasse markkasse = new MariaKasse(datenbank, START);

        markkasse.gutschreiben(anna, 1_000L, Buchungsgrund.BESTAND);
        kasse.gutschreiben(anna, 3L, Buchungsgrund.BESTAND);

        // Zwei Staende, zwei Buecher, derselbe Spieler. Mit einer gemeinsamen Tabelle
        // haette er jetzt einen Stand von START + 1003 in einer Einheit, die es nicht
        // gibt.
        assertEquals(START + 1_000L, markkasse.guthaben(anna));
        assertEquals(START + 3L, kasse.guthaben(anna));
        assertEquals(2, zeilen("SELECT COUNT(*) FROM buchungen WHERE spieler = ?", anna),
                "Markbuch: Eroeffnung und Gutschrift");
        assertEquals(2, zeilen("SELECT COUNT(*) FROM goldbuchungen WHERE spieler = ?",
                anna), "Goldbuch: Eroeffnung und Gutschrift");
    }

    @Test
    @DisplayName("Das Goldbuch haengt an den GOLDkonten, nicht an den Markkonten")
    void fremdschluesselZeigtRichtig() throws SQLException {
        UUID anna = UUID.randomUUID();
        kasse.gutschreiben(anna, 5L, Buchungsgrund.BESTAND);

        // Ein Buch, das auf die falsche Kasse zeigt, haelt seine Zusage fuer die andere
        // Waehrung: Ohne Goldkonto liesse sich dann eine Goldbuchung schreiben, solange
        // nur ein MARKkonto da ist. Gefragt wird das Schema selbst - der Quelltext des
        // Schrittes sagt es auch, aber nur diese Abfrage sagt, was in der Datenbank steht.
        assertEquals("goldkassen", text(
                "SELECT referenced_table_name FROM information_schema.key_column_usage"
                        + " WHERE table_schema = DATABASE()"
                        + " AND constraint_name = 'fk_goldbuchung_kasse'"));
        assertTrue(zeilen("SELECT COUNT(*) FROM goldkassen WHERE spieler = ?", anna) > 0);
    }

    @Test
    @DisplayName("leereAlles raeumt beide Goldmarktabellen mit weg")
    void leereAllesRaeumtDieGoldmark() throws SQLException {
        UUID anna = UUID.randomUUID();
        kasse.gutschreiben(anna, 9L, Buchungsgrund.BESTAND);

        datenbank.leereAlles();

        // ZWEI Pflichtstellen, und die Reihenfolge ist Pflicht: Das Buch VOR den Konten,
        // sonst scheitert jedes leereAlles am Fremdschluessel - und damit JEDE
        // Datenbankprobe, auch die, die mit Geld nichts zu tun hat.
        assertEquals(0, zeilen("SELECT COUNT(*) FROM goldbuchungen WHERE spieler = ?",
                anna));
        assertEquals(0, zeilen("SELECT COUNT(*) FROM goldkassen WHERE spieler = ?",
                anna));
        assertEquals(Datenbank.hoechsteFassung(), schemastand(),
                "der Schemastand darf dabei nicht fallen");
    }

    private long zahl(String sql, UUID spieler) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, spieler.toString());
            try (var ergebnis = anfrage.executeQuery()) {
                assertTrue(ergebnis.next(), "keine Zeile fuer " + spieler);
                return ergebnis.getLong(1);
            }
        }
    }

    private int zeilen(String sql, UUID spieler) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, spieler.toString());
            try (var ergebnis = anfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getInt(1);
            }
        }
    }

    private String text(String sql) throws SQLException {
        try (var anweisung = datenbank.verbindung().createStatement();
             var ergebnis = anweisung.executeQuery(sql)) {
            return ergebnis.next() ? ergebnis.getString(1) : "";
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
