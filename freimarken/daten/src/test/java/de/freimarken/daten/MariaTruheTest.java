package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.freimarken.kern.Truhenablage;
import de.freimarken.kern.TruhenVertrag;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Truhenvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Proben uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaSpielzeitTest}: Eine
 * gruene Suite, die nie eine Datenbank gesehen hat, waere das Schlimmste.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann: dass
 * {@code ON DUPLICATE KEY UPDATE} wirklich aufschlaegt statt zu ersetzen, dass dabei EINE
 * Zeile entsteht, dass das Ortsdatum unverschoben in der Spalte landet, dass das Raeumen
 * ueber den Tag und nicht ueber den Spieler geht und dass kein Fremdschluessel eine
 * Entnahme an ein Konto bindet.
 */
@DisplayName("Entnahmen der Freien Truhe in MariaDB")
class MariaTruheTest extends TruhenVertrag {

    private static Datenbank datenbank;
    private static MariaTruhe truhe;

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
        truhe = new MariaTruhe(datenbank);
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
    protected Truhenablage ablage() {
        // Immer dieselbe Ablage, aber vor jeder Probe eine leere Tabelle - leereAlles()
        // laeuft in @BeforeEach. Dasselbe Vorgehen wie bei MariaSpielzeitTest.
        return truhe;
    }

    @Test
    @DisplayName("Zwei Griffe am selben Tag ergeben EINE Zeile, nicht zwei")
    void zweiGriffeEineZeile() throws SQLException {
        UUID anna = UUID.randomUUID();

        truhe.merke(anna, HEUTE, 5);
        truhe.merke(anna, HEUTE, 3);

        assertEquals(1, zeilenVon(anna),
                "ON DUPLICATE KEY UPDATE schlaegt auf die vorhandene Zeile auf; zwei"
                        + " Zeilen hiessen, dass der Hauptschluessel (spieler, tag) nicht"
                        + " greift - und die Abfrage ohne SUM saehe nur eine davon");
        assertEquals(8, stueckIn(anna, HEUTE), "und zwar aufgeschlagen, nicht ersetzt");
    }

    @Test
    @DisplayName("Derselbe Griff zweimal geschrieben zaehlt zweimal")
    void derselbeGriffZweimalZaehltZweimal() {
        // Das ist KEIN Fehler, sondern die Kehrseite des aufschlagenden Schreibens - und
        // der Grund, warum nach einem fehlgeschlagenen Schreibvorgang NICHT wiederholt
        // wird. Zu viel angerechnet heisst: Jemand nimmt nichts mehr aus einer Truhe, in
        // die er gerade erst hineingesehen hat. Zu wenig kostet hoechstens einen offenen
        // Deckel. Wer hier einmal eine Wiederholung einbaut, soll an dieser Probe
        // vorbeimuessen.
        UUID anna = UUID.randomUUID();

        truhe.merke(anna, HEUTE, 4);
        truhe.merke(anna, HEUTE, 4);

        assertEquals(8, truhe.behalten(anna, HEUTE));
    }

    @Test
    @DisplayName("Das Ortsdatum steht unverschoben in der Spalte")
    void ortsdatumStehtUnverschoben() throws SQLException {
        // Die eigentliche Probe auf setObject(LocalDate). Mit java.sql.Date.valueOf stuende
        // hier auf einem Rechner oestlich von Greenwich der VORTAG - und dann zaehlte der
        // Deckel die Griffe von gestern. Die beiden Umstellungstage stehen mit dabei, weil
        // ein 23- und ein 25-Stunden-Tag jede Rechnung ueber Millisekunden auffliegen
        // lassen.
        List<LocalDate> tage = List.of(
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 10, 25),
                LocalDate.of(2027, 1, 1));

        for (LocalDate tag : tage) {
            UUID wer = UUID.randomUUID();
            truhe.merke(wer, tag, 7);

            assertEquals(tag.toString(), tagAlsTextVon(wer),
                    "der Tag muss genau so in der Spalte stehen, wie der Kern ihn"
                            + " gerechnet hat - sonst entscheidet die Zeitzone der"
                            + " Verbindung, welcher Tag gedeckelt ist");
            assertEquals(7, truhe.behalten(wer, tag),
                    "und mit demselben Tag muss er wiederzufinden sein");
        }
    }

    @Test
    @DisplayName("Geraeumt wird ueber den TAG und nicht ueber den Spieler")
    void geraeumtWirdUeberDenTag() throws SQLException {
        // Der taegliche Lauf kennt keine Spielerliste - er loescht, was vor heute liegt,
        // fuer alle. Eine Bedingung auf den Spieler liesse die Tabelle mit jedem Spieler
        // und jedem Tag weiterwachsen, und gefragt wird immer nur nach HEUTE.
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        truhe.merke(anna, GESTERN, 9);
        truhe.merke(bert, GESTERN, 2);
        truhe.merke(bert, HEUTE, 3);

        assertEquals(2, truhe.raeume(HEUTE), "beide Zeilen von gestern");
        assertEquals(0, zeilenVon(anna));
        assertEquals(1, zeilenVon(bert), "und nur die heutige bleibt");
    }

    @Test
    @DisplayName("Eine Truhenzeile braucht kein Konto")
    void keinKontoNoetig() throws SQLException {
        // Mit Absicht kein Fremdschluessel auf kassen: In der Truhe liegt kein Geld,
        // sondern eine Zaehlung von Stueck. Ein RESTRICT liesse den Griff eines Spielers
        // scheitern, der noch nie etwas verdient hat - und der nimmt aus einer freien
        // Truhe am ehesten.
        UUID ohneKonto = UUID.randomUUID();

        truhe.merke(ohneKonto, HEUTE, 16);

        assertEquals(16, stueckIn(ohneKonto, HEUTE));
        assertEquals(0, kassenzeilenVon(ohneKonto), "und es entsteht dabei auch keines");
    }

    @Test
    @DisplayName("Leeren raeumt auch die Entnahmen")
    void leerenRaeumtDieEntnahmen() throws SQLException {
        // Fehlte die Tabelle in leereAlles(), wanderten Tagesstaende von einer Probe in
        // die naechste, und die Datenproben waeren voneinander abhaengig.
        UUID anna = UUID.randomUUID();
        truhe.merke(anna, HEUTE, 16);

        datenbank.leereAlles();

        assertEquals(0, zeilenVon(anna));
        assertEquals(Datenbank.hoechsteFassung(), schemastand(),
                "der Schemastand darf dabei nicht fallen");
    }

    private int stueckIn(UUID spieler, LocalDate tag) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(
                "SELECT stueck FROM freie_truhe WHERE spieler = ? AND tag = ?")) {
            anfrage.setString(1, spieler.toString());
            anfrage.setObject(2, tag);
            try (var ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? ergebnis.getInt(1) : -1;
            }
        }
    }

    /** Der Tag als Text, wie die Datenbank ihn selbst formatiert - ohne Java dazwischen. */
    private String tagAlsTextVon(UUID spieler) throws SQLException {
        try (var anfrage = datenbank.verbindung().prepareStatement(
                "SELECT CAST(tag AS CHAR) FROM freie_truhe WHERE spieler = ?")) {
            anfrage.setString(1, spieler.toString());
            try (var ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? ergebnis.getString(1) : "";
            }
        }
    }

    private int zeilenVon(UUID spieler) throws SQLException {
        return zaehle("SELECT COUNT(*) FROM freie_truhe WHERE spieler = ?", spieler);
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
