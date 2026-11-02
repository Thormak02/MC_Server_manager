package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Art;
import de.freimarken.kern.BesitzLager;
import de.freimarken.kern.BesitzLagerVertrag;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Zustand;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Vertrag wie beim Lager im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Das ist der Punkt, an dem sich der Aufwand mit {@link BesitzLagerVertrag} auszahlt:
 * Der Umstieg auf MariaDB ist kein Vertrauensakt, sondern ein Testlauf. Weicht die
 * Datenbankfassung in irgendeinem Punkt ab, faellt es hier auf und nicht im Betrieb.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen - sind sie da, aber die Datenbank
 * antwortet nicht, scheitern sie. Stillschweigend durchwinken waere das Schlimmste: eine
 * gruene Suite, die nie eine Datenbank gesehen hat.
 */
@DisplayName("Lager in MariaDB")
class MariaLagerTest extends BesitzLagerVertrag {

    private static Datenbank datenbank;
    private static MariaLager lager;

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
        lager = new MariaLager(datenbank);
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
    protected BesitzLager lager() {
        return lager;
    }

    // --- Was nur die Datenbank leisten kann -------------------------------------

    @Test
    @DisplayName("Der eindeutige Schluessel haelt, nicht nur die Pruefung im Programm")
    void schluesselHaelt() {
        UUID anna = UUID.randomUUID();
        lager.anlegen(WELT, 42, 42, anna, 0L);

        // Zwischen Nachsehen und Schreiben passt immer ein zweiter Spieler. Nur die
        // Datenbank kann das wirklich verhindern - und sie tut es hier.
        try {
            lager.anlegen(WELT, 42, 42, UUID.randomUUID(), 0L);
            throw new AssertionError("Der eindeutige Schluessel hat nicht gegriffen");
        } catch (IllegalStateException erwartet) {
            assertTrue(erwartet.getMessage().contains("bereits vergeben"));
        }
        assertEquals(anna, lager.finde(WELT, 42, 42).orElseThrow().eigentuemer());
    }

    @Test
    @DisplayName("Alles uebersteht einen Verbindungsabbruch - die Datenbank ist die Wahrheit")
    void ueberstehtNeustart() throws SQLException {
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        Parzelle parzelle = lager.anlegen(WELT, 7, 11, anna, 25_000L);
        lager.mitbauerSetzen(parzelle.id(), Set.of(bert));

        // Verbindung wegwerfen und neu aufbauen - so, wie es ein Serverneustart taete.
        datenbank.close();

        Parzelle danach = lager.finde(WELT, 7, 11).orElseThrow();
        assertEquals(anna, danach.eigentuemer());
        assertEquals(25_000L, danach.preis());
        assertEquals(Set.of(bert), danach.mitbauer());
        assertEquals(parzelle.id(), danach.id());
    }

    @Test
    @DisplayName("Ein Feld herausloesen laesst die geteilte Verbindung offen")
    void herausloesenLaesstDieVerbindungOffen() throws SQLException {
        // Die Verbindung ist geteilt und langlebig. Wer sie in eine Ressourcenklammer
        // setzt, schliesst sie nach JEDEM Herausloesen - und der naechste Zugriff ist die
        // Besitzpruefung beim naechsten gesetzten Block, im Hauptfaden des Servers. Der
        // Vertrag im Arbeitsspeicher kann das nicht bemerken: dort gibt es keine
        // Verbindung. Deshalb steht die Probe hier.
        UUID anna = UUID.randomUUID();
        Parzelle eins = lager.anlegen(WELT, 60, 60, anna, 0L);
        Parzelle zwei = lager.anlegen(WELT, 61, 60, anna, 0L);
        Parzelle drei = lager.anlegen(WELT, 62, 60, anna, 0L);
        lager.gruppeSetzen(Parzelle.KEIN_ELTERNTEIL, WELT, anna, Set.of(), 30_000L,
                List.of(eins.id(), zwei.id(), drei.id()));
        java.sql.Connection geteilt = datenbank.verbindung();

        lager.ausGruppeLoesen(drei.id());

        assertFalse(geteilt.isClosed(),
                "die geteilte Verbindung wurde geschlossen - der naechste Zugriff muesste "
                        + "sie neu aufbauen");
        assertFalse(lager.finde(WELT, 62, 60).orElseThrow().hatElternteil());
    }

    @Test
    @DisplayName("Der Zeitpunkt kommt in UTC zurueck, nicht in der Zeitzone der Sitzung")
    void zeitBleibtUtc() {
        Parzelle vorher = lager.anlegen(WELT, 3, 4, UUID.randomUUID(), 0L);
        Parzelle gelesen = lager.finde(WELT, 3, 4).orElseThrow();

        long abweichung = Math.abs(
                gelesen.erworbenAm().toEpochMilli() - vorher.erworbenAm().toEpochMilli());
        assertTrue(abweichung < 1000,
                "Geschrieben " + vorher.erworbenAm() + ", gelesen " + gelesen.erworbenAm()
                        + " - das ist eine Zeitzonenverschiebung, keine Rundung");
    }

    @Test
    @DisplayName("Mitbauer verschwinden mit der Parzelle - der Fremdschluessel raeumt mit")
    void mitbauerVerschwindenMit() throws SQLException {
        Parzelle parzelle = lager.anlegen(WELT, 12, 12, UUID.randomUUID(), 0L);
        lager.mitbauerSetzen(parzelle.id(), Set.of(UUID.randomUUID(), UUID.randomUUID()));

        lager.entfernen(parzelle.id());

        try (var anweisung = datenbank.verbindung().createStatement();
             var ergebnis = anweisung.executeQuery(
                     "SELECT COUNT(*) FROM parzellen_mitbauer WHERE parzelle_id = "
                             + parzelle.id())) {
            ergebnis.next();
            assertEquals(0, ergebnis.getInt(1), "verwaiste Mitbauer bleiben liegen");
        }
    }

    @Test
    @DisplayName("Migration laeuft mehrfach, ohne etwas kaputtzumachen")
    void migrationIstWiederholbar() throws SQLException {
        Parzelle parzelle = lager.anlegen(WELT, 20, 20, UUID.randomUUID(), 0L);

        datenbank.migriere();
        datenbank.migriere();

        assertEquals(parzelle.id(), lager.finde(WELT, 20, 20).orElseThrow().id());
    }

    // --- Das Modell: art, eltern_id, zustand ------------------------------------

    @Test
    @DisplayName("Art, Elternteil und Zustand ueberstehen den Weg durch die Datenbank")
    void modellfelderUeberlebenDenWeg() {
        Parzelle neu = lager.anlegen(WELT, 30, 30, UUID.randomUUID(), 0L);
        Parzelle gelesen = lager.finde(WELT, 30, 30).orElseThrow();

        assertEquals(Art.PARZELLE, gelesen.art());
        assertEquals(Zustand.AKTIV, gelesen.zustand());
        assertEquals(Parzelle.KEIN_ELTERNTEIL, gelesen.elternId());
        assertEquals(neu.art(), gelesen.art());
        assertEquals(neu.elternId(), gelesen.elternId());
        assertEquals(neu.zustand(), gelesen.zustand());
    }

    @Test
    @DisplayName("Die Migration verliert keine Zeile und macht aus ihr eine aktive Parzelle")
    void migrationVerliertKeineZeile() throws SQLException {
        // So sieht eine Zeile aus, die vor dem Schemaschritt geschrieben wurde: nur die
        // sechs alten Spalten. Genau drei solche Zeilen stehen in der Spieldatenbank.
        UUID wem = UUID.randomUUID();
        try (var anweisung = datenbank.verbindung().createStatement()) {
            anweisung.execute("INSERT INTO parzellen "
                    + "(welt, x, z, eigentuemer, preis, erworben_am) VALUES ('"
                    + WELT + "', 40, 40, '" + wem + "', 25000, UTC_TIMESTAMP(3))");
        }

        datenbank.migriere();

        Parzelle danach = lager.finde(WELT, 40, 40).orElseThrow();
        assertEquals(wem, danach.eigentuemer());
        assertEquals(25_000L, danach.preis());
        assertEquals(Art.PARZELLE, danach.art(), "Vorgabewert der neuen Spalte");
        assertEquals(Zustand.AKTIV, danach.zustand());
        assertEquals(Parzelle.KEIN_ELTERNTEIL, danach.elternId());
    }

    @Test
    @DisplayName("Ein zweiter Anlauf nach verlorenem Schemastand wirft nicht")
    void schemaschritteSindWiederholbar() throws SQLException {
        // migriere() fuehrt erst das DDL aus und schreibt danach getrennt den Stand fort.
        // Bricht es dazwischen ab, ist die Aenderung da und der Stand zurueck. Das
        // vorhandene migrationIstWiederholbar prueft das NICHT: Bei vollem Stand laeuft
        // die Schleife gar nicht erst.
        try (var anweisung = datenbank.verbindung().createStatement()) {
            anweisung.execute("DELETE FROM schema_stand WHERE schritt IN (4, 5)");
        }

        datenbank.migriere();

        Parzelle neu = lager.anlegen(WELT, 41, 41, UUID.randomUUID(), 0L);
        assertEquals(Art.PARZELLE, lager.finde(WELT, 41, 41).orElseThrow().art());
        assertEquals(neu.id(), lager.finde(WELT, 41, 41).orElseThrow().id());
    }

    @Test
    @DisplayName("Eine Gruppenzeile ist fuer Suchen, Zaehlen und Auflisten unsichtbar")
    void gruppeZaehltNichtAlsParzelle() throws SQLException {
        UUID wem = UUID.randomUUID();
        lager.anlegen(WELT, 50, 50, wem, 0L);
        gruppeAnlegen(wem);

        // Die Gruppe hat keine Rasterzelle, belegt also keine Flaeche. Zaehlte sie mit,
        // koestete die naechste Parzelle das Doppelte und die Rangobergrenze waere frueher
        // erreicht - beides, ohne dass der Spieler mehr Land haette.
        assertEquals(1, lager.anzahlVon(WELT, wem));
        assertEquals(1, lager.vonSpieler(WELT, wem).size());
        assertEquals(lager.vonSpieler(WELT, wem).size(), lager.anzahlVon(WELT, wem));
    }

    @Test
    @DisplayName("Besitz ohne Rasterzelle wird laut abgewiesen, nicht als Parzelle 0/0 gelesen")
    void rasterloserBesitzFaelltAuf() throws SQLException {
        UUID wem = UUID.randomUUID();
        long gruppe = gruppeAnlegen(wem);
        // Eine Zeile, die behauptet, eine Parzelle zu sein, aber keine Rasterzelle hat.
        // So etwas kann kein Schreibweg des Programms erzeugen - wohl aber eine Hand an
        // der Datenbank. Ohne den Riegel in lies() gaebe getInt("x") hier lautlos 0
        // zurueck, also die Parzelle 0/0 mitten in der Marktstadt, mit dem Eigentuemer
        // dieser Zeile. 0/0 ist in der Spieldatenbank vergeben.
        try (var anweisung = datenbank.verbindung().createStatement()) {
            anweisung.execute("INSERT INTO parzellen "
                    + "(welt, x, z, eigentuemer, preis, erworben_am, art, eltern_id, zustand) "
                    + "VALUES ('" + WELT + "', NULL, NULL, '" + wem
                    + "', 0, UTC_TIMESTAMP(3), 'parzelle', " + gruppe + ", 'aktiv')");
        }

        var fehler = assertThrows(IllegalStateException.class,
                () -> lager.kinderVon(gruppe));
        assertTrue(fehler.getMessage().contains("nicht auf dem Raster"), fehler.getMessage());
    }

    @Test
    @DisplayName("Der Fremdschluessel verweigert das Loeschen einer Gruppe mit Kindern")
    void gruppeMitKindernBleibtStehen() throws SQLException {
        UUID wem = UUID.randomUUID();
        long gruppe = gruppeAnlegen(wem);
        Parzelle kind = lager.anlegen(WELT, 60, 60, wem, 0L);
        try (var anweisung = datenbank.verbindung().createStatement()) {
            anweisung.execute("UPDATE parzellen SET eltern_id = " + gruppe
                    + " WHERE id = " + kind.id());
        }

        var fehler = assertThrows(IllegalStateException.class, () -> lager.entfernen(gruppe));
        assertTrue(fehler.getMessage().contains("auftrennen"), fehler.getMessage());
        assertTrue(lager.kinderVon(gruppe).size() == 1, "Das Kind haengt noch an der Gruppe");
        assertTrue(lager.finde(WELT, 60, 60).isPresent(), "Das Kind muss stehen bleiben");

        // Erst auftrennen, dann ist die Gruppe weg und das Feld wieder eigenstaendig -
        // das ist Plan 6.3 als Schemapruefung, bevor es einen Befehl dafuer gibt.
        lager.auftrennen(gruppe);
        assertTrue(lager.kinderVon(gruppe).isEmpty());
        assertTrue(lager.finde(WELT, 60, 60).isPresent(), "Das Feld bleibt bestehen");
        assertFalse(lager.finde(WELT, 60, 60).orElseThrow().hatElternteil(),
                "und ist wieder eigenstaendig");
    }

    @Test
    @DisplayName("Eine unbekannte Elternnummer laesst sich nicht eintragen")
    void unbekannterElternteilWirdAbgewiesen() throws SQLException {
        Parzelle kind = lager.anlegen(WELT, 61, 61, UUID.randomUUID(), 0L);
        try (var anweisung = datenbank.verbindung().createStatement()) {
            assertThrows(SQLException.class, () -> anweisung.execute(
                    "UPDATE parzellen SET eltern_id = 999999999 WHERE id = " + kind.id()));
        }
    }

    @Test
    @DisplayName("Leeren raeumt auch eine Gruppe mit Kindern")
    void leerenRaeumtDieKette() throws SQLException {
        UUID wem = UUID.randomUUID();
        long gruppe = gruppeAnlegen(wem);
        Parzelle kind = lager.anlegen(WELT, 62, 62, wem, 0L);
        try (var anweisung = datenbank.verbindung().createStatement()) {
            anweisung.execute("UPDATE parzellen SET eltern_id = " + gruppe
                    + " WHERE id = " + kind.id());
        }

        // Daran haengen alle anderen Datentests: leereAlles() laeuft vor jedem einzelnen.
        // Ohne das Loesen der Kette scheiterte es am Fremdschluessel, und zwar erst, wenn
        // zum ersten Mal eine Gruppe in der Tabelle steht.
        datenbank.leereAlles();

        try (var anweisung = datenbank.verbindung().createStatement();
             var ergebnis = anweisung.executeQuery("SELECT COUNT(*) FROM parzellen")) {
            ergebnis.next();
            assertEquals(0, ergebnis.getInt(1));
        }
        try (var anweisung = datenbank.verbindung().createStatement();
             var ergebnis = anweisung.executeQuery(
                     "SELECT COALESCE(MAX(schritt), 0) FROM schema_stand")) {
            ergebnis.next();
            assertEquals(8, ergebnis.getInt(1), "Der Schemastand darf dabei nicht fallen");
        }
    }

    /** Legt eine Gruppenzeile an - ohne Rasterlage, wie Plan 6.3 sie verlangt. */
    private long gruppeAnlegen(UUID eigentuemer) throws SQLException {
        try (var anweisung = datenbank.verbindung().createStatement()) {
            anweisung.execute("INSERT INTO parzellen "
                    + "(welt, x, z, eigentuemer, preis, erworben_am, art, eltern_id, zustand) "
                    + "VALUES ('" + WELT + "', NULL, NULL, '" + eigentuemer
                    + "', 30000, UTC_TIMESTAMP(3), 'gruppe', NULL, 'aktiv')");
        }
        try (var anweisung = datenbank.verbindung().createStatement();
             var ergebnis = anweisung.executeQuery("SELECT MAX(id) FROM parzellen")) {
            ergebnis.next();
            return ergebnis.getLong(1);
        }
    }
}
