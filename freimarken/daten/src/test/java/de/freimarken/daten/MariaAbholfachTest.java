package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.AbholVertrag;
import de.freimarken.kern.Abholablage;
import de.freimarken.kern.Abholgut;
import de.freimarken.kern.Abholstueck;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
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
 * Derselbe Abholvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaMieteTest}.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann: dass der
 * Zeitpunkt ohne Zonenreise hin und zurueck kommt, dass die Spalte einen langen Inhalt
 * wirklich haelt statt ihn abzuschneiden, dass ein abgewiesener Schreibzug NICHTS
 * zurueckgelassen hat, und dass das Loeschen den Empfaenger wirklich in der Bedingung
 * hat.
 */
@DisplayName("Abholfach in MariaDB")
class MariaAbholfachTest extends AbholVertrag {

    private static Datenbank datenbank;
    private static MariaAbholfach fach;

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
        fach = new MariaAbholfach(datenbank);
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
    protected Abholablage ablage() {
        return fach;
    }

    @Test
    @DisplayName("Der Zeitpunkt kommt auf die Millisekunde zurueck")
    void zeitpunktOhneZonenreise() {
        UUID anna = UUID.randomUUID();
        // Eine Zeit mit Millisekunden UND nahe an einer Tagesgrenze: Eine Fassung, die
        // ueber eine Zone rechnet, verschiebt sie um Stunden; eine, die auf DATETIME ohne
        // Bruchteile schreibt, verliert die 123 - und dann haben zwei Stuecke desselben
        // Augenblicks keine Reihenfolge mehr.
        java.time.Instant genau = java.time.Instant.parse("2026-11-08T23:30:00.123Z");

        fach.lege(anna, "Laden halle-1", genau, List.of(gut("64x Stein")));

        assertEquals(genau, fach.fuerSpieler(anna).get(0).eingelegt());
    }

    @Test
    @DisplayName("Ein Inhalt mit 100.000 Zeichen kommt vollstaendig zurueck")
    void langerInhaltWirdNichtAbgeschnitten() {
        // Die Spalte ist MEDIUMTEXT und nicht TEXT, und das ist der Grund: Ein TEXT haelt
        // 64 KB, und MariaDB SCHNEIDET darueber hinaus ab, statt zu werfen. Ein
        // abgeschnittener Stapel ist ein Gegenstand, der nicht mehr zu lesen ist - und
        // niemand merkt es, bis der Spieler ihn holen will. 100.000 Zeichen liegen
        // ueber dem TEXT-Rand und unter Abholgut.LAENGSTER_INHALT.
        UUID anna = UUID.randomUUID();
        String inhalt = "AbCd+/=".repeat(15_000);
        assertTrue(inhalt.length() > 65_535, "muss ueber dem TEXT-Rand liegen: "
                + inhalt.length());

        fach.lege(anna, "Laden halle-1", WANN, List.of(new Abholgut("1x Kiste", inhalt)));

        assertEquals(inhalt, fach.fuerSpieler(anna).get(0).inhalt());
    }

    @Test
    @DisplayName("Der Empfaenger steht wirklich in der Loeschbedingung")
    void empfaengerStehtImSql() throws Exception {
        // Nicht die Wirkung, sondern der SATZ. Der Vertrag prueft schon, dass ein fremdes
        // Stueck liegen bleibt - aber er bleibt auch gruen, wenn das aus Zufall so ist,
        // etwa weil die Nummern in einer Probenreihe nie kollidieren. Was hier
        // festgehalten wird, ist die Bedingung selbst: Sie ist die einzige Schranke
        // dagegen, dass eine nach einem Neustart wiederverwendete Nummer fremde Ware
        // herausgibt.
        String satz = sqlFeld("NEHMEN");

        assertTrue(satz.contains("empfaenger = ?"), satz);
        assertTrue(satz.contains("id = ?"), satz);
    }

    @Test
    @DisplayName("Ein abgewiesener Schreibzug hinterlaesst NICHTS")
    void abgewiesenHinterlaesstNichts() throws SQLException {
        // Das zweite Versprechen gegen die echte Datenbank: Der dritte Stapel ist null,
        // also wirft das Pruefen - und zwar BEVOR die Transaktion offen ist. Gezaehlt
        // wird am rohen SQL und nicht ueber anzahlFuer: Eine Fassung, die die ersten zwei
        // geschrieben und dann zurueckgenommen hat, waere ueber anzahlFuer auch gruen,
        // aber die Nummern waeren verbraucht.
        UUID anna = UUID.randomUUID();
        List<Abholgut> mitFehler = java.util.Arrays.asList(gut("eins"), gut("zwei"),
                null);

        assertThrows(RuntimeException.class,
                () -> fach.lege(anna, "Laden halle-1", WANN, mitFehler));

        assertEquals(0, zeilenImFach(), "keine Zeile darf stehen geblieben sein");
    }

    @Test
    @DisplayName("Eine zu lange Herkunft faellt dem PROGRAMM auf, nicht der Spalte")
    void zuLangeHerkunftFaelltVorherAuf() {
        // Befund der Einspritzungskampagne vom 08.11.2026: Die Pruefschleife VOR der
        // Transaktion liess sich wegnehmen, ohne dass eine Probe rot wurde. Sie ist aber
        // noetig - die Spalte haelt VARCHAR(48), und MariaDB schneidet darueber hinaus
        // stumm ab (oder wirft, je nach Modus). Eine abgeschnittene Herkunft ist eine
        // Zeile, bei der der Spieler raten muss, welcher Laden das war.
        //
        // Geprueft wird die Ausnahmeart: IllegalArgumentException kommt aus Abholstueck,
        // IllegalStateException aus der Datenbank. Nur die erste sagt, dass das Programm
        // es gefangen hat.
        UUID anna = UUID.randomUUID();
        String zuLang = "L".repeat(Abholstueck.LAENGSTE_HERKUNFT + 1);

        assertThrows(IllegalArgumentException.class,
                () -> fach.lege(anna, zuLang, WANN, List.of(gut("64x STONE"))));

        assertEquals(0, fach.anzahlFuer(anna), "nichts darf geschrieben sein");
    }

    @Test
    @DisplayName("Die Nummern steigen und sind je Stapel eigene")
    void nummernSteigen() {
        // AUTO_INCREMENT, und das ist die Zusage, die der Vertrag braucht: Zwei Stapel
        // aus EINEM Einlegen bekommen zwei Nummern. Eine Fassung mit einem Batch, die
        // sich den Schluessel selbst rechnet, gibt hier zweimal dieselbe - und dann
        // loescht das Holen des einen auch den anderen.
        UUID anna = UUID.randomUUID();

        fach.lege(anna, "Laden halle-1", WANN, List.of(gut("eins"), gut("zwei")));

        List<Abholstueck> seine = fach.fuerSpieler(anna);
        assertEquals(2, seine.size());
        assertNotEquals(seine.get(0).id(), seine.get(1).id());
        assertTrue(seine.get(0).id() < seine.get(1).id(),
                "die aeltere Nummer ist die kleinere: " + seine.get(0).id() + " / "
                        + seine.get(1).id());
    }

    @Test
    @DisplayName("Zaehlen und Lesen sagen dasselbe")
    void zaehlenUndLesenStimmenUeberein() {
        // Zwei verschiedene Abfragen auf dieselbe Frage - genau die Stelle, an der zwei
        // Fassungen auseinanderlaufen. Das COUNT(*) gibt es nur, damit beim Beitritt
        // nicht die MEDIUMTEXT-Spalte durch die Leitung kommt; wenn es dabei eine andere
        // Zahl nennt, ist die Ersparnis ein Fehler.
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        fach.lege(anna, "Laden halle-1", WANN, List.of(gut("eins"), gut("zwei")));
        fach.lege(bert, "Laden halle-2", WANN, List.of(gut("drei")));

        assertEquals(2, fach.anzahlFuer(anna));
        assertEquals(fach.fuerSpieler(anna).size(), fach.anzahlFuer(anna));
        assertEquals(1, fach.anzahlFuer(bert));
        assertEquals(0, fach.anzahlFuer(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Die Reihenfolge haelt auch, wenn zwei Spieler sich abwechseln")
    void reihenfolgeTrotzFremderZeilenDazwischen() {
        // Annas Zeilen liegen in der Tabelle nicht beieinander, und genau das ist der
        // Fall, in dem ein fehlendes ORDER BY auffallen KANN: Der Index traegt
        // (empfaenger, id), ein Full Scan dagegen die reine Einfuegereihenfolge.
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        fach.lege(anna, "Laden halle-1", WANN, List.of(gut("Anna zuerst")));
        fach.lege(bert, "Laden halle-2", WANN, List.of(gut("Bert")));
        fach.lege(anna, "Laden halle-1", WANN, List.of(gut("Anna zuletzt")));

        List<String> seine = new ArrayList<>();
        for (Abholstueck stueck : fach.fuerSpieler(anna)) {
            seine.add(stueck.beschriftung());
        }
        assertEquals(List.of("Anna zuerst", "Anna zuletzt"), seine);
    }

    /** Wie viele Zeilen ueberhaupt im Fach stehen - am rohen SQL gezaehlt. */
    private static int zeilenImFach() throws SQLException {
        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis =
                     anweisung.executeQuery("SELECT COUNT(*) FROM abholfach")) {
            ergebnis.next();
            return ergebnis.getInt(1);
        }
    }

    /**
     * Der Wortlaut eines SQL-Satzes - ueber Spiegelung.
     *
     * <p>Dasselbe Mittel wie in {@code MariaMieteTest.einfuegenOhneOnDuplicate}: Der Satz
     * ist privat und soll es bleiben, aber eine Zusage ueber seine Bauart braucht ihn.
     */
    private static String sqlFeld(String name) throws Exception {
        java.lang.reflect.Field feld = MariaAbholfach.class.getDeclaredField(name);
        feld.setAccessible(true);
        return (String) feld.get(null);
    }

    @Test
    @DisplayName("Die Spalte haelt UTC - gelesen am ROHEN Text, nicht ueber den Treiber")
    void spalteHaeltUtc() throws SQLException {
        // Der Schemaschritt sagt es seit jeher ("eingelegt ist DATETIME(3) und UTC, wie
        // jede Zeitspalte dieses Schemas") - der Quelltext hielt es bis zum 11.11.2026
        // nicht. Eine Zusage in einem Kommentar, die keine Probe traegt, ist eine
        // Behauptung.
        Instant wann = Instant.parse("2026-11-09T00:00:00.123Z");

        fach.lege(UUID.randomUUID(), "halle-1", wann,
                List.of(new Abholgut("64 x Stein", "inhalt")));

        assertEquals("2026-11-09 00:00:00.123",
                rohesDatum(datenbank, "eingelegt", "abholfach"),
                "in der Spalte steht UTC - wie es der Schemaschritt verspricht");
    }
    /**
     * Liest den ROHEN Text einer Zeitspalte - ohne den Treiber dazwischen.
     *
     * <p>{@code getTimestamp} rechnet, und genau das soll hier nicht geschehen: Ein Hin
     * und Zurueck durch denselben Treiber beweist nichts ueber den Inhalt der Spalte.
     */
    private static String rohesDatum(Datenbank bank, String spalte, String tabelle)
            throws SQLException {
        try (var anfrage = bank.verbindung().prepareStatement(
                "SELECT CAST(" + spalte + " AS CHAR) FROM " + tabelle + " LIMIT 1");
                var ergebnis = anfrage.executeQuery()) {
            assertTrue(ergebnis.next(), "die Zeile muss da sein");
            return ergebnis.getString(1);
        }
    }

}
