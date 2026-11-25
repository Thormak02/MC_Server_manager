package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.StrafVertrag;
import de.freimarken.kern.Strafablage;
import de.freimarken.kern.Strafart;
import de.freimarken.kern.Strafdauer;
import de.freimarken.kern.Strafe;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Strafvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaMeldungenTest}: Eine
 * gruene Suite, die nie eine Datenbank gesehen hat, waere das Schlimmste.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann: dass
 * {@code NULL} in {@code laeuft_ab_am} wirklich unbegrenzt heisst, dass der Riegel in der
 * WHERE-Bedingung sitzt und dass die Sortierung "die laengste zuerst" aus der Abfrage
 * kommt und nicht aus Java. <b>Die wichtigste ist
 * {@link #unbegrenzteSchreibtNullUndKeinenText()}</b>: Ein Text {@code "null"} in dieser
 * Spalte waere ein Wurf mitten in der Beitrittswache - und dann kommt niemand mehr
 * herein.
 */
@DisplayName("Strafen in MariaDB")
class MariaStrafenTest extends StrafVertrag {

    private static Datenbank datenbank;
    private static MariaStrafen strafen;

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
        strafen = new MariaStrafen(datenbank);
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
    protected Strafablage ablage() {
        return strafen;
    }

    @Test
    @DisplayName("Die Nummer kommt aus AUTO_INCREMENT und steigt")
    void nummerKommtAusDerDatenbank() {
        UUID bert = UUID.randomUUID();

        long erste = strafen.lege(bert, Strafart.RAUSWURF, "erster Vorfall", null,
                MITTAGS, null).nummer();
        long zweite = strafen.lege(bert, Strafart.RAUSWURF, "zweiter Vorfall", null,
                SPAETER, null).nummer();

        // Geprueft wird NICHT auf eine bestimmte Zahl: leereAlles() benutzt DELETE und
        // nicht TRUNCATE, der Zaehler laeuft also ueber die Probenreihe weiter.
        assertTrue(erste > 0L, "die Datenbank nennt die Nummer: " + erste);
        assertEquals(erste + 1L, zweite);
    }

    @Test
    @DisplayName("Eine UNBEGRENZTE Strafe schreibt NULL und keinen Text")
    void unbegrenzteSchreibtNullUndKeinenText() throws SQLException {
        UUID bert = UUID.randomUUID();

        Strafe ohne = strafen.lege(bert, Strafart.BANN, "dauerhaft", null, MITTAGS,
                Strafdauer.UNBEGRENZT);

        // DIE PROBE DIESER KLASSE. Die Spalte kommt beim Lesen durch
        // Timestamp.toInstant, und ein Text "null" waere dort ein Wurf - mitten in der
        // Beitrittswache, die bei JEDEM Spieler laeuft. Dann kommt niemand mehr herein.
        //
        // Dazu: setTimestamp(i, null, kalender) wirft eine NullPointerException, weil
        // der Treiber den Kalender mit dem Wert fuettert. Gemessen am 25.11.2026 an
        // MariaMeldungen; hier trifft es jede unbegrenzte Strafe.
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "SELECT laeuft_ab_am, von_wem, aufgehoben_am, aufgehoben_von,"
                        + " aufhebungsgrund FROM strafen WHERE nummer = ?")) {
            anweisung.setLong(1, ohne.nummer());
            try (var ergebnis = anweisung.executeQuery()) {
                assertTrue(ergebnis.next(), "die Zeile muss da sein");
                assertNull(ergebnis.getTimestamp("laeuft_ab_am"));
                assertNull(ergebnis.getString("von_wem"),
                        "null heisst die Konsole - ein erfundener Platzhalter saehe in"
                                + " der Liste wie ein Spieler aus");
                assertNull(ergebnis.getTimestamp("aufgehoben_am"));
                assertNull(ergebnis.getString("aufgehoben_von"));
                assertEquals("", ergebnis.getString("aufhebungsgrund"));
            }
        }
        assertTrue(strafen.lies(ohne.nummer()).orElseThrow().unbegrenzt(),
                "und beim Lesen ist sie wieder unbegrenzt");
    }

    @Test
    @DisplayName("DER RIEGEL gegen zwei Aufhebungen steht in der WHERE-Bedingung")
    void riegelStehtInDerAbfrage() throws SQLException {
        UUID bert = UUID.randomUUID();
        UUID anna = UUID.randomUUID();
        UUID clara = UUID.randomUUID();
        Strafe steht = strafen.lege(bert, Strafart.BANN, "Grief", anna, MITTAGS,
                EINE_STUNDE);

        assertTrue(strafen.hebeAuf(steht.aufgehoben(anna, "erster Grund", SPAETER)),
                "der erste gewinnt");
        assertEquals(false, strafen.hebeAuf(steht.aufgehoben(clara, "zweiter Grund",
                NOCH_SPAETER)), "der zweite verliert");

        // Mit rohem SQL gegengelesen: Die Spalten halten den ERSTEN Aufheber. Eine
        // Fassung, die in Java prueft, haette hier den zweiten - und zwar nur, wenn
        // zwei Moderatoren gleichzeitig arbeiten, also im Ernstfall.
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "SELECT aufgehoben_von, aufhebungsgrund FROM strafen"
                        + " WHERE nummer = ?")) {
            anweisung.setLong(1, steht.nummer());
            try (var ergebnis = anweisung.executeQuery()) {
                assertTrue(ergebnis.next(), "die Zeile muss da sein");
                assertEquals(anna.toString(), ergebnis.getString("aufgehoben_von"));
                assertEquals("erster Grund", ergebnis.getString("aufhebungsgrund"));
            }
        }
    }

    @Test
    @DisplayName("Die Sortierung 'die laengste zuerst' kommt aus der ABFRAGE")
    void sortierungKommtAusDerAbfrage() {
        UUID bert = UUID.randomUUID();

        // Drei geltende Banne in EINER Millisekunde, die kuerzeste zuerst eingefuegt.
        // Die Abfrage holt genau eine Zeile (LIMIT 1), also entscheidet allein das
        // ORDER BY - in Java wird hier nichts mehr verglichen. Gaebe die Abfrage die
        // erste gefundene, liesse die kuerzeste den Spieler nach zehn Minuten herein,
        // obwohl ein dauerhafter Bann gegen ihn steht.
        strafen.lege(bert, Strafart.BANN, "zehn Minuten", null, MITTAGS, ZEHN_MINUTEN);
        strafen.lege(bert, Strafart.BANN, "eine Stunde", null, MITTAGS, EINE_STUNDE);
        strafen.lege(bert, Strafart.BANN, "ohne Ende", null, MITTAGS,
                Strafdauer.UNBEGRENZT);

        assertEquals("ohne Ende",
                strafen.geltende(bert, Strafart.BANN, SPAETER).orElseThrow().grund(),
                "(laeuft_ab_am IS NULL) DESC setzt die unbegrenzte nach vorn");
    }

    @Test
    @DisplayName("Die Grenze des Ablaufs liegt in der Abfrage, nicht in Java")
    void ablaufgrenzeLiegtInDerAbfrage() {
        UUID bert = UUID.randomUUID();
        strafen.lege(bert, Strafart.STUMM, "zehn Minuten", null, MITTAGS, ZEHN_MINUTEN);

        // laeuft_ab_am > ? und nicht >= : Eine Strafe, die in diesem Augenblick
        // ablaeuft, ist abgelaufen. Der Grenzfall entscheidet zugunsten des
        // Betroffenen - er hat sie abgesessen. Dieselbe Richtung wie Strafe.giltNoch;
        // waeren beide verschieden, haette die Wache eine andere Meinung als die Liste.
        assertTrue(strafen.geltende(bert, Strafart.STUMM,
                        MITTAGS.plusSeconds(599L)).isPresent(),
                "eine Sekunde vorher gilt sie");
        assertTrue(strafen.geltende(bert, Strafart.STUMM,
                        MITTAGS.plusSeconds(600L)).isEmpty(),
                "im Augenblick des Ablaufs nicht mehr");
    }

    @Test
    @DisplayName("Ein RAUSWURF schreibt Ende gleich Anfang")
    void rauswurfSchreibtEndeGleichAnfang() throws SQLException {
        UUID bert = UUID.randomUUID();

        Strafe eine = strafen.lege(bert, Strafart.RAUSWURF, "Spam", null, MITTAGS, null);

        // Und nicht NULL: Ein NULL hiesse unbegrenzt, und ein unbegrenzter Rauswurf
        // waere ein Bann mit anderem Namen. Die Spalte haelt damit die Zusage aus
        // Strafart.RAUSWURF - gelesen wird sie durch den Konstruktor, der genau das
        // prueft.
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "SELECT verhaengt_am, laeuft_ab_am FROM strafen WHERE nummer = ?")) {
            anweisung.setLong(1, eine.nummer());
            try (var ergebnis = anweisung.executeQuery()) {
                assertTrue(ergebnis.next(), "die Zeile muss da sein");
                assertEquals(ergebnis.getTimestamp("verhaengt_am",
                                Datenbank.utcKalender()),
                        ergebnis.getTimestamp("laeuft_ab_am",
                                Datenbank.utcKalender()));
            }
        }
    }

    @Test
    @DisplayName("Die Zeit kommt in UTC zurueck, wie sie hineingegeben wurde")
    void zeitKommtInUtcZurueck() {
        UUID bert = UUID.randomUUID();

        Strafe gelegt = strafen.lege(bert, Strafart.BANN, "Grief", null, MITTAGS,
                EINE_STUNDE);

        // Ohne den UTC-Kalender rechnet der Treiber in die Zone der JVM. Hier haengt
        // daran mehr als eine Anzeige: Ob ein Bann noch gilt, ist ein Vergleich zweier
        // Zeiten - und bei einem Zonenversatz von einer Stunde kommt ein Gesperrter
        // eine Stunde zu frueh oder zu spaet wieder herein.
        Strafe gelesen = strafen.lies(gelegt.nummer()).orElseThrow();
        assertEquals(MITTAGS, gelesen.verhaengtAm());
        assertEquals(MITTAGS.plusSeconds(3600L), gelesen.endet().orElseThrow());
    }

    @Test
    @DisplayName("Die Zeit der AUFHEBUNG kommt auch in UTC zurueck")
    void aufhebungszeitInUtc() {
        UUID bert = UUID.randomUUID();
        UUID anna = UUID.randomUUID();
        Strafe steht = strafen.lege(bert, Strafart.BANN, "Grief", anna, MITTAGS,
                EINE_STUNDE);

        strafen.hebeAuf(steht.aufgehoben(anna, "Einspruch angenommen", SPAETER));

        assertEquals(SPAETER,
                strafen.lies(steht.nummer()).orElseThrow().aufhebung().orElseThrow());
    }

    @Test
    @DisplayName("Ein zu langer Grund wird im KERN abgewiesen, nicht erst von MariaDB")
    void zuLangerGrundWirdVorherAbgewiesen() {
        UUID bert = UUID.randomUUID();
        String lang = "x".repeat(Strafe.LAENGSTER_GRUND + 1);

        // Ein IllegalArgumentException und keine SQLException: Die Spalte ist
        // VARCHAR(256), und ohne die Pruefung im Kern kaeme der strict mode von MariaDB
        // mit einer englischen Klage. Der Moderator glaubte dann, der Spieler sei
        // gesperrt - und der spielt weiter.
        assertThrows(IllegalArgumentException.class,
                () -> strafen.lege(bert, Strafart.BANN, lang, null, MITTAGS,
                        EINE_STUNDE));
        assertEquals(0, strafen.zaehle(bert, Strafart.BANN),
                "und nichts ist geschrieben");
    }

    @Test
    @DisplayName("Eine unbekannte ART in der Spalte nennt die NUMMER")
    void unbekannteArtNenntDieNummer() throws SQLException {
        UUID bert = UUID.randomUUID();
        Strafe eine = strafen.lege(bert, Strafart.BANN, "Grief", null, MITTAGS,
                EINE_STUNDE);
        // Von Hand verdorben - so, wie es ein zurueckgerolltes Plugin oder ein Eingriff
        // in die Datenbank hinterlaesst.
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "UPDATE strafen SET art = 'VERWARNUNG' WHERE nummer = ?")) {
            anweisung.setLong(1, eine.nummer());
            assertEquals(1, anweisung.executeUpdate(), "die Vorbereitung");
        }

        IllegalStateException klage = assertThrows(IllegalStateException.class,
                () -> strafen.lies(eine.nummer()));

        // Javas eigene Meldung waere "No enum constant ... VERWARNUNG" - ohne jeden
        // Hinweis darauf, WELCHE Zeile gemeint ist. Dann durchsucht der Betreiber die
        // ganze Tabelle, und bis dahin bricht die Bannliste ab.
        assertTrue(klage.getMessage().contains(String.valueOf(eine.nummer())),
                "die Klage nennt die Nummer: " + klage.getMessage());
        assertTrue(klage.getMessage().contains("VERWARNUNG"), klage.getMessage());
        assertTrue(klage.getMessage().contains("BANN"),
                "und zaehlt die erlaubten auf: " + klage.getMessage());
    }

    @Test
    @DisplayName("Eine UNLESBARE Zeile nennt die Nummer und reisst keine Liste mit")
    void unlesbareZeileNenntDieNummer() throws SQLException {
        UUID bert = UUID.randomUUID();
        Strafe eine = strafen.lege(bert, Strafart.BANN, "Grief in Altfurt", null,
                MITTAGS, EINE_STUNDE);
        // Von Hand verdorben, und zwar auf eine Weise, die die SPALTE erlaubt: Der
        // Grund ist VARCHAR(256) NOT NULL, seine MINDESTlaenge von drei Zeichen steht
        // nur in Java. Von Hand eingetragen wird oefter, als man denkt - /strafe
        // trifft nur Spieler, die der Server schon einmal gesehen hat.
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "UPDATE strafen SET grund = 'ok' WHERE nummer = ?")) {
            anweisung.setLong(1, eine.nummer());
            assertEquals(1, anweisung.executeUpdate(), "die Vorbereitung");
        }

        // DER FUND VOM 25.11.2026: ausZeile fing nur die unbekannte ART. Die Klage des
        // Konstruktors ist eine IllegalArgumentException, und die faengt NIEMAND - die
        // Aufrufer fangen IllegalStateException. Folge: Diese eine Zeile riss die ganze
        // oeffentliche Bannliste mit UND den Wurf aus der Beitrittswache.
        IllegalStateException klage = assertThrows(IllegalStateException.class,
                () -> strafen.lies(eine.nummer()));
        assertTrue(klage.getMessage().contains(String.valueOf(eine.nummer())),
                "die Klage nennt die Nummer: " + klage.getMessage());

        // UND die Gattung muss stimmen, damit die Aufrufer sie fangen - auch in den
        // Listen, die ueber mehrere Zeilen gehen.
        assertThrows(IllegalStateException.class,
                () -> strafen.geltendeAlle(Strafart.BANN, 10, SPAETER));
        assertThrows(IllegalStateException.class,
                () -> strafen.von(bert, 10));
        assertThrows(IllegalStateException.class,
                () -> strafen.geltende(bert, Strafart.BANN, SPAETER));
    }

    @Test
    @DisplayName("Eine Nummer, die es nicht gibt, wirft - und zwar NACH dem Versuch")
    void unbekannteNummerWirftBeimAufheben() {
        Strafe erfunden = new Strafe(999_999L, UUID.randomUUID(), Strafart.BANN,
                "nie gelegt", null, MITTAGS, MITTAGS.plusSeconds(3600L), SPAETER, null,
                "Grund der Aufhebung");

        // Zwei Gruende fuer "keine Zeile getroffen", und sie sind verschieden zu
        // beantworten: Die Nummer gibt es nicht (Wurf) oder sie ist schon aufgehoben
        // (false). Gezaehlt wird erst nach dem fehlgeschlagenen UPDATE, damit der
        // haeufige Fall ohne zweite Abfrage auskommt.
        assertThrows(IllegalArgumentException.class, () -> strafen.hebeAuf(erfunden));
    }

    @Test
    @DisplayName("Zehn Jahre Bann lassen sich schreiben und wieder lesen")
    void zehnJahreGehenDurchDieSpalte() {
        UUID bert = UUID.randomUUID();

        // Die Grenze aus Strafdauer.LAENGSTE_TAGE gegen die Spalte gehalten: DATETIME
        // reicht bis 9999, der Wert passt also. Waere er zu gross, brach der strict
        // mode - und zwar nur bei der laengsten erlaubten Dauer, also selten und
        // ausgerechnet im schwersten Fall.
        Strafe lang = strafen.lege(bert, Strafart.BANN, "zehn Jahre", null, MITTAGS,
                new Strafdauer(Duration.ofDays(Strafdauer.LAENGSTE_TAGE)));

        assertEquals(MITTAGS.plus(Duration.ofDays(Strafdauer.LAENGSTE_TAGE)),
                strafen.lies(lang.nummer()).orElseThrow().endet().orElseThrow());
    }

    @Test
    @DisplayName("Die Liste der geltenden Banne liest die Arten getrennt")
    void bannlisteTrenntDieArten() throws SQLException {
        UUID bert = UUID.randomUUID();
        UUID clara = UUID.randomUUID();
        strafen.lege(bert, Strafart.BANN, "gebannt", null, MITTAGS, EINE_STUNDE);
        strafen.lege(clara, Strafart.STUMM, "stumm", null, MITTAGS, EINE_STUNDE);

        // In der Abfrage und nicht in Java: Ohne die Bedingung auf art stuenden die
        // Stummgeschalteten in der oeffentlichen Bannliste - ein Pranger fuer eine
        // Strafe, die in zehn Minuten vorbei ist.
        assertEquals(1, strafen.geltendeAlle(Strafart.BANN, 10, SPAETER).size());
        assertEquals("gebannt",
                strafen.geltendeAlle(Strafart.BANN, 10, SPAETER).get(0).grund());
        try (var anweisung = datenbank.verbindung().prepareStatement(
                "SELECT COUNT(*) FROM strafen WHERE art = 'STUMM'")) {
            try (var ergebnis = anweisung.executeQuery()) {
                assertTrue(ergebnis.next());
                assertEquals(1, ergebnis.getInt(1),
                        "die Stummschaltung steht in derselben Tabelle");
            }
        }
    }
}
