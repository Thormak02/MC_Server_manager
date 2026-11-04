package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.SpielzeitVertrag;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Spielzeitvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaKasseTest} und
 * {@link MariaLagerTest}: Eine gruene Suite, die nie eine Datenbank gesehen hat, waere das
 * Schlimmste.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann: dass
 * {@code ON DUPLICATE KEY UPDATE} wirklich addiert statt zu ersetzen, dass dabei EINE Zeile
 * entsteht, dass das Ortsdatum unverschoben in der Spalte landet und dass kein
 * Fremdschluessel eine Messung an ein Konto bindet.
 *
 * <p>Was NICHT mehr hier steht: "ein Aufruf mit einem fehlenden Zuschlag hinterlaesst nichts".
 * Die Probe war umsetzungsfrei und stand zweimal da - einmal hier, einmal bei der
 * Speicherfassung. Sie ist in {@link SpielzeitVertrag#allesOderNichts()} gewandert, zusammen
 * mit der Ueberlaufprobe, die den gemeinsamen Ausnahmetyp festlegt
 * ({@link SpielzeitVertrag#ueberlaufBrichtAb()}). Eine dritte Umsetzung erbt damit auch diese
 * zwei Zusagen.
 */
@DisplayName("Spielzeit in MariaDB")
class MariaSpielzeitTest extends SpielzeitVertrag {

    private static Datenbank datenbank;
    private static MariaSpielzeit spielzeit;

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
        spielzeit = new MariaSpielzeit(datenbank);
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
    protected Spielzeitablage ablage() {
        // Immer dieselbe Ablage, aber vor jedem Test eine leere Tabelle - leereAlles()
        // laeuft in @BeforeEach. Dasselbe Vorgehen wie bei MariaKasseTest.
        return spielzeit;
    }

    @Test
    @DisplayName("Zwei Zuschlaege am selben Tag ergeben EINE Zeile, nicht zwei")
    void zweiZuschlaegeEineZeile() throws SQLException {
        UUID anna = UUID.randomUUID();

        spielzeit.schreibeFort(List.of(new Spielzeitablage.Zuschlag(anna, HEUTE, 60L)));
        spielzeit.schreibeFort(List.of(new Spielzeitablage.Zuschlag(anna, HEUTE, 30L)));

        assertEquals(1, zeilenVon(anna),
                "ON DUPLICATE KEY UPDATE schlaegt auf die vorhandene Zeile auf; zwei Zeilen"
                        + " hiessen, dass der Primaerschluessel (spieler, tag) nicht greift");
        assertEquals(90L, sekundenIn(anna, HEUTE), "und zwar addiert, nicht ersetzt");
    }

    @Test
    @DisplayName("Derselbe Takt zweimal geschrieben zaehlt zweimal")
    void derselbeTaktZweimalZaehltZweimal() throws SQLException {
        // Das ist KEIN Fehler, sondern die Kehrseite des additiven Schreibens - und der
        // Grund, warum nach einem fehlgeschlagenen Schreibvorgang NICHT wiederholt wird.
        // Ein Wiederholen nach einem unklaren Netzfehler koennte die Minute doppelt
        // buchen, und zu viel Spielzeit gibt einen Rang zu frueh, zu wenig nur zu spaet.
        // Wer hier einmal eine Wiederholung einbaut, soll an dieser Probe vorbeimuessen.
        UUID anna = UUID.randomUUID();
        List<Spielzeitablage.Zuschlag> einTakt =
                List.of(new Spielzeitablage.Zuschlag(anna, HEUTE, 60L));

        spielzeit.schreibeFort(einTakt);
        spielzeit.schreibeFort(einTakt);

        assertEquals(120L, spielzeit.lies(anna, HEUTE).sekundenHeute(),
                "additiv heisst: eine Wiederholung ist eine zweite Minute");
    }

    @Test
    @DisplayName("Das Ortsdatum steht unverschoben in der Spalte")
    void ortsdatumStehtUnverschoben() throws SQLException {
        // Die eigentliche Probe auf setObject(LocalDate). Mit java.sql.Date.valueOf stuende
        // hier auf einem Rechner oestlich von Greenwich der VORTAG: Ein java.sql.Date ist
        // ein Millisekundenwert um Mitternacht Ortszeit, und der Treiber rechnet ihn in die
        // Sitzungszone UTC um. Die beiden Umstellungstage stehen mit dabei, weil ein
        // 23- und ein 25-Stunden-Tag jede Rechnung mit 86.400 Sekunden auffliegen lassen.
        List<LocalDate> tage = List.of(
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 10, 25),
                LocalDate.of(2027, 1, 1));

        for (LocalDate tag : tage) {
            UUID wer = UUID.randomUUID();
            spielzeit.schreibeFort(List.of(new Spielzeitablage.Zuschlag(wer, tag, 42L)));

            assertEquals(tag.toString(), tagAlsTextVon(wer),
                    "der Tag muss genau so in der Spalte stehen, wie der Kern ihn gerechnet"
                            + " hat - sonst entscheidet die Zeitzone der Verbindung");
            assertEquals(42L, spielzeit.lies(wer, tag).sekundenHeute(),
                    "und mit demselben Tag muss er wiederzufinden sein");
        }
    }

    @Test
    @DisplayName("Eine Spielzeitzeile braucht kein Konto")
    void keinKontoNoetig() throws SQLException {
        // Mit Absicht kein Fremdschluessel auf kassen: Ein RESTRICT brachte die EINE
        // Transaktion des Takts zum Scheitern - also die Minute ALLER anwesenden Spieler -,
        // sobald ein einziger von ihnen noch keine Kassenzeile hat.
        UUID ohneKonto = UUID.randomUUID();

        spielzeit.schreibeFort(List.of(new Spielzeitablage.Zuschlag(ohneKonto, HEUTE, 60L)));

        assertEquals(60L, sekundenIn(ohneKonto, HEUTE));
        assertEquals(0, kassenzeilenVon(ohneKonto), "und es entsteht dabei auch keines");
    }

    @Test
    @DisplayName("Die Gesamtsumme laeuft ueber viele Tage")
    void gesamtsummeUeberVieleTage() {
        // Ein Jahr und ein Vierteljahr in einem Zug - die Abfrage summiert ueber den
        // Primaerschluessel, und die Zahl der Zeilen je Spieler ist die, die wirklich
        // entsteht: eine je gespieltem Tag.
        UUID anna = UUID.randomUUID();
        LocalDate erster = HEUTE.minusDays(399);
        List<Spielzeitablage.Zuschlag> alles = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            alles.add(new Spielzeitablage.Zuschlag(anna, erster.plusDays(i), 3_600L));
        }

        spielzeit.schreibeFort(alles);

        assertEquals(400L * 3_600L, spielzeit.lies(anna, HEUTE).sekundenGesamt(),
                "400 Tage mit je einer Stunde");
        assertEquals(3_600L, spielzeit.lies(anna, HEUTE).sekundenHeute(),
                "und heute ist nur einer davon");
    }

    @Test
    @DisplayName("Leeren raeumt auch die Spielzeit")
    void leerenRaeumtDieSpielzeit() throws SQLException {
        // Fehlte die Tabelle in leereAlles(), wanderten gemessene Sekunden von einem Test
        // in den naechsten, und die Datentests waeren voneinander abhaengig.
        UUID anna = UUID.randomUUID();
        spielzeit.schreibeFort(List.of(new Spielzeitablage.Zuschlag(anna, HEUTE, 600L)));

        datenbank.leereAlles();

        assertEquals(0, zeilenVon(anna));
        assertEquals(Datenbank.hoechsteFassung(), schemastand(),
                "der Schemastand darf dabei nicht fallen");
    }

    private long sekundenIn(UUID spieler, LocalDate tag) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(
                "SELECT sekunden FROM spielzeit WHERE spieler = ? AND tag = ?")) {
            anfrage.setString(1, spieler.toString());
            anfrage.setObject(2, tag);
            try (var ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? ergebnis.getLong(1) : -1L;
            }
        }
    }

    /** Der Tag als Text, wie die Datenbank ihn selbst formatiert - ohne Java dazwischen. */
    private String tagAlsTextVon(UUID spieler) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(
                "SELECT CAST(tag AS CHAR) FROM spielzeit WHERE spieler = ?")) {
            anfrage.setString(1, spieler.toString());
            try (var ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? ergebnis.getString(1) : "";
            }
        }
    }

    private int zeilenVon(UUID spieler) throws SQLException {
        return zaehle("SELECT COUNT(*) FROM spielzeit WHERE spieler = ?", spieler);
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
