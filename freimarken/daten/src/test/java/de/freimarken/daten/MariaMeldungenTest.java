package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.MeldeVertrag;
import de.freimarken.kern.Meldeablage;
import de.freimarken.kern.Meldestand;
import de.freimarken.kern.Meldung;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Meldevertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaTeamTest}: Eine gruene
 * Suite, die nie eine Datenbank gesehen hat, waere das Schlimmste.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann: dass die
 * Nummer aus {@code AUTO_INCREMENT} kommt, dass der Riegel in der WHERE-Bedingung sitzt
 * und dass {@code MAX()} ueber eine leere Menge eine Zeile mit NULL gibt und nicht keine
 * Zeile - jede dieser drei Sachen laesst sich umsetzungsfrei nicht sagen.
 */
@DisplayName("Meldungen in MariaDB")
class MariaMeldungenTest extends MeldeVertrag {

    private static Datenbank datenbank;
    private static MariaMeldungen meldungen;

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
        meldungen = new MariaMeldungen(datenbank);
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
    protected Meldeablage ablage() {
        return meldungen;
    }

    @Test
    @DisplayName("Die Nummer kommt aus AUTO_INCREMENT und steigt")
    void nummerKommtAusDerDatenbank() {
        UUID anna = UUID.randomUUID();

        long erste = meldungen.lege(anna, UUID.randomUUID(), "erster Vorfall", "altfurt",
                0, -60, 0, MITTAGS).nummer();
        long zweite = meldungen.lege(anna, UUID.randomUUID(), "zweiter Vorfall",
                "altfurt", 0, -60, 0, SPAETER).nummer();

        // Geprueft wird NICHT auf eine bestimmte Zahl: leereAlles() benutzt DELETE und
        // nicht TRUNCATE, der Zaehler laeuft also ueber die Probenreihe weiter. Was
        // zaehlt, ist dass die Datenbank sie vergibt und dass sie steigt - ohne
        // getGeneratedKeys waere beides 0.
        assertTrue(erste > 0L, "die Datenbank nennt die Nummer: " + erste);
        assertEquals(erste + 1L, zweite);
    }

    @Test
    @DisplayName("DER RIEGEL steht in der WHERE-Bedingung, nicht in Java")
    void riegelStehtInDerAbfrage() throws SQLException {
        UUID anna = UUID.randomUUID();
        UUID moderator = UUID.randomUUID();
        UUID zweiter = UUID.randomUUID();
        Meldung offen = meldungen.lege(anna, UUID.randomUUID(), "Griefing", "altfurt",
                0, -60, 0, MITTAGS);

        assertTrue(meldungen.schreibe(offen.inArbeitVon(moderator, SPAETER),
                Meldestand.OFFEN), "der erste gewinnt");
        assertFalse(meldungen.schreibe(offen.inArbeitVon(zweiter, NOCH_SPAETER),
                Meldestand.OFFEN), "der zweite verliert");

        // Mit rohem SQL gegengelesen: Die Spalte haelt den ERSTEN Bearbeiter. Eine
        // Fassung, die in Java prueft, haette hier den zweiten - und zwar nur, wenn
        // zwei Moderatoren gleichzeitig arbeiten, also im Ernstfall.
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "SELECT bearbeiter, stand FROM meldungen WHERE nummer = ?")) {
            anweisung.setLong(1, offen.nummer());
            try (var ergebnis = anweisung.executeQuery()) {
                assertTrue(ergebnis.next(), "die Zeile muss da sein");
                assertEquals(moderator.toString(), ergebnis.getString("bearbeiter"));
                assertEquals("IN_ARBEIT", ergebnis.getString("stand"));
            }
        }
    }

    @Test
    @DisplayName("Eine offene Meldung schreibt NULL in Bearbeiter und Erledigungszeit")
    void offeneSchreibtNull() throws SQLException {
        UUID anna = UUID.randomUUID();
        Meldung offen = meldungen.lege(anna, UUID.randomUUID(), "Vorfall", "altfurt",
                0, -60, 0, MITTAGS);

        // Und keinen Text "null": Beide Spalten kommen beim Lesen durch
        // UUID.fromString beziehungsweise Timestamp.toInstant, und ein Text waere dort
        // ein Wurf mitten in der Liste des Moderators.
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "SELECT bearbeiter, erledigt_am, vermerk, gelesen FROM meldungen"
                        + " WHERE nummer = ?")) {
            anweisung.setLong(1, offen.nummer());
            try (var ergebnis = anweisung.executeQuery()) {
                assertTrue(ergebnis.next(), "die Zeile muss da sein");
                assertNull(ergebnis.getString("bearbeiter"));
                assertNull(ergebnis.getTimestamp("erledigt_am"));
                assertEquals("", ergebnis.getString("vermerk"));
                assertFalse(ergebnis.getBoolean("gelesen"));
            }
        }
    }

    @Test
    @DisplayName("MAX() ueber keine Meldung gibt eine Zeile mit NULL - und kein leer")
    void maxUeberLeereMenge() {
        // Der Fall, der in SQL anders aussieht als er klingt: MAX() ueber eine leere
        // Menge gibt EINE Zeile mit NULL, nicht keine Zeile. Wer das verwechselt, liest
        // hier eine Ausnahme statt "hat noch nie gemeldet" - und dann kann niemand seine
        // erste Meldung abgeben, weil die Abklingzeit nicht zu rechnen ist.
        assertTrue(meldungen.letzteVon(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("Die Zeit kommt in UTC zurueck, wie sie hineingegeben wurde")
    void zeitKommtInUtcZurueck() {
        UUID anna = UUID.randomUUID();

        Meldung gelegt = meldungen.lege(anna, UUID.randomUUID(), "Vorfall", "altfurt",
                0, -60, 0, MITTAGS);

        // Ohne den UTC-Kalender rechnet der Treiber in die Zone der JVM: Die Tabelle
        // waere fuer sich stimmig (hin und zurueck dieselbe Zeit), aber neben dem
        // Blockbuch gelesen laege sie um den Zonenversatz daneben. Genau das ist am
        // 10.11.2026 aufgefallen.
        assertEquals(MITTAGS, meldungen.lies(gelegt.nummer()).orElseThrow().gemeldetAm());
        assertEquals(MITTAGS, meldungen.letzteVon(anna).orElseThrow());
    }

    @Test
    @DisplayName("Ein zu langer Grund wird im KERN abgewiesen, nicht erst von MariaDB")
    void zuLangerGrundWirdVorherAbgewiesen() {
        UUID anna = UUID.randomUUID();
        String lang = "x".repeat(Meldung.LAENGSTER_GRUND + 1);

        // Ein IllegalArgumentException und keine SQLException: Die Spalte ist
        // VARCHAR(256), und ohne die Pruefung im Kern kaeme der strict mode von MariaDB
        // mit einer englischen Klage ohne Zeilennummer. Der Melder glaubte dann, es sei
        // nichts passiert.
        assertThrows(IllegalArgumentException.class,
                () -> meldungen.lege(anna, UUID.randomUUID(), lang, "altfurt", 0, -60, 0,
                        MITTAGS));
        assertEquals(0, meldungen.offeneVon(anna), "und nichts ist geschrieben");
    }

    @Test
    @DisplayName("Ein unbekannter Stand in der Spalte nennt die NUMMER")
    void unbekannterStandNenntDieNummer() throws SQLException {
        UUID anna = UUID.randomUUID();
        Meldung eine = meldungen.lege(anna, UUID.randomUUID(), "Vorfall", "altfurt",
                0, -60, 0, MITTAGS);
        // Von Hand verdorben - so, wie es ein zurueckgerolltes Plugin oder ein Eingriff
        // in die Datenbank hinterlaesst.
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "UPDATE meldungen SET stand = 'ERFUNDEN' WHERE nummer = ?")) {
            anweisung.setLong(1, eine.nummer());
            assertEquals(1, anweisung.executeUpdate(), "die Vorbereitung");
        }

        IllegalStateException klage = assertThrows(IllegalStateException.class,
                () -> meldungen.lies(eine.nummer()));

        // Javas eigene Meldung waere "No enum constant ... ERFUNDEN" - ohne jeden
        // Hinweis darauf, WELCHE Zeile gemeint ist. Dann durchsucht der Betreiber die
        // ganze Tabelle.
        assertTrue(klage.getMessage().contains(String.valueOf(eine.nummer())),
                "die Klage nennt die Nummer: " + klage.getMessage());
        assertTrue(klage.getMessage().contains("ERFUNDEN"), klage.getMessage());
        assertTrue(klage.getMessage().contains("OFFEN"),
                "und zaehlt die erlaubten auf: " + klage.getMessage());
    }

    @Test
    @DisplayName("Eine Nummer, die es nicht gibt, wirft - und zwar NACH dem Versuch")
    void unbekannteNummerWirftBeimSchreiben() {
        Meldung erfunden = Meldung.neu(999_999L, UUID.randomUUID(), UUID.randomUUID(),
                "nie gelegt", "altfurt", 0, -60, 0, MITTAGS);

        // Zwei Gruende fuer "keine Zeile getroffen", und sie sind verschieden zu
        // beantworten: Die Nummer gibt es nicht (Wurf) oder der Stand hat sich geaendert
        // (false). Gezaehlt wird erst nach dem fehlgeschlagenen UPDATE, damit der
        // haeufige Fall ohne zweite Abfrage auskommt.
        assertThrows(IllegalArgumentException.class,
                () -> meldungen.schreibe(erfunden, Meldestand.OFFEN));
    }

    @Test
    @DisplayName("Der Weltname landet kleingeschrieben in der Spalte")
    void weltnameWirdKlein() throws SQLException {
        UUID anna = UUID.randomUUID();

        Meldung eine = meldungen.lege(anna, UUID.randomUUID(), "Vorfall", "ALTFURT",
                0, -60, 0, MITTAGS);

        // Meldung normalisiert, nicht die Datenbank: Sonst stuende in der Spalte, was
        // der Server gerade zurueckgab, und ein Vergleich mit dem Weltnamen aus der
        // config.yml ginge einmal gut und einmal nicht.
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "SELECT welt FROM meldungen WHERE nummer = ?")) {
            anweisung.setLong(1, eine.nummer());
            try (var ergebnis = anweisung.executeQuery()) {
                assertTrue(ergebnis.next(), "die Zeile muss da sein");
                assertEquals("altfurt", ergebnis.getString("welt"));
            }
        }
    }
}
