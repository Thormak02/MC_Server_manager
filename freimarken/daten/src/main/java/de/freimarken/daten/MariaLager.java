package de.freimarken.daten;

import de.freimarken.kern.Art;
import de.freimarken.kern.BesitzLager;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Zustand;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Besitz in MariaDB.
 *
 * <p>Haelt denselben Vertrag wie jedes andere Lager - geprueft wird das durch dieselben
 * Tests, die auch das Lager im Arbeitsspeicher bestehen muss.
 *
 * <p>Der wichtigste Teil steht nicht in diesem Code, sondern im Schema: Der eindeutige
 * Schluessel auf {@code (welt, x, z)} sorgt dafuer, dass zwei gleichzeitige Versuche,
 * dieselbe Parzelle zu nehmen, nicht beide durchgehen. Eine Pruefung im Programm allein
 * koennte das nicht - zwischen Nachsehen und Schreiben passt immer ein zweiter Spieler.
 */
public final class MariaLager implements BesitzLager {

    /**
     * Die Spalten, die eine Zeile zu einem Stueck Besitz machen - an einer Stelle.
     *
     * <p>Vorher stand diese Aufzaehlung dreimal da. Beim naechsten Feld haette eine der
     * drei sie nicht bekommen, und welche, zeigte sich erst im Betrieb.
     */
    private static final String SPALTEN =
            "id, welt, x, z, eigentuemer, preis, erworben_am, art, eltern_id, zustand";

    /**
     * Was auf dem Raster liegt - und damit alles, was als Parzelle zaehlt.
     *
     * <p>Gefragt wird nach der LAGE, nicht nach der art: Eine Gruppe hat keine eigene
     * Rasterzelle, ihr Bereich ergibt sich aus ihren Kindern. Kommt spaeter eine art dazu,
     * die sehr wohl auf dem Raster liegt, zaehlt sie von selbst mit - richtig, denn sie
     * belegt Flaeche - ohne dass diese Zeichenfolge wandern muesste.
     *
     * <p>Ohne diesen Filter zaehlte die Gruppenzeile mit, und das ist keine Kleinigkeit:
     * Nach dem Zusammenlegen zweier Felder koestete die dritte Parzelle 100.000 statt
     * 50.000 Mark, weil die Preisstaffel verdoppelt (25.000 << (bereits-1)), und eine
     * Rangobergrenze von vier waere schon nach drei Parzellen erreicht.
     */
    private static final String AUF_DEM_RASTER = " AND x IS NOT NULL";

    private final Datenbank datenbank;

    public MariaLager(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    /** SQL-Fehler sind hier nicht behebbar - sie gehoeren nach oben, nicht ins Logbuch. */
    private static RuntimeException weiterreichen(String was, SQLException ursache) {
        return new IllegalStateException("Datenbankfehler beim " + was + ": "
                + ursache.getMessage(), ursache);
    }

    @Override
    public Optional<Parzelle> finde(String welt, int x, int z) {
        // Ohne AUF_DEM_RASTER und ohne art-Filter: x = ? wird gegen SQL NULL nie wahr,
        // der Filter waere also wirkungslos. Ein art-Filter dagegen waere gefaehrlich -
        // er meldete jeden Besitz, der eine echte Rasterzelle belegt und zufaellig eine
        // andere art hat, als "frei", und die Schutzwache liesse dort jeden bauen.
        // Was eine Rasterzelle belegt, wird gemeldet, gleich welcher Art.
        String sql = "SELECT " + SPALTEN + " FROM parzellen "
                + "WHERE welt = ? AND x = ? AND z = ?";
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, welt);
            anfrage.setInt(2, x);
            anfrage.setInt(3, z);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                if (!ergebnis.next()) {
                    return Optional.empty();
                }
                return Optional.of(lies(ergebnis));
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Suchen einer Parzelle", fehler);
        }
    }

    @Override
    public List<Parzelle> vonSpieler(String welt, UUID spieler) {
        String sql = "SELECT " + SPALTEN + " FROM parzellen "
                + "WHERE welt = ? AND eigentuemer = ?" + AUF_DEM_RASTER + " ORDER BY x, z";
        List<Parzelle> treffer = new ArrayList<>();
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, welt);
            anfrage.setString(2, spieler.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                while (ergebnis.next()) {
                    treffer.add(lies(ergebnis));
                }
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Lesen der Parzellen eines Spielers", fehler);
        }
        return List.copyOf(treffer);
    }

    @Override
    public int anzahlVon(String welt, UUID spieler) {
        String sql = "SELECT COUNT(*) FROM parzellen WHERE welt = ? AND eigentuemer = ?"
                + AUF_DEM_RASTER;
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, welt);
            anfrage.setString(2, spieler.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? ergebnis.getInt(1) : 0;
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Zaehlen der Parzellen", fehler);
        }
    }

    @Override
    public Parzelle anlegen(String welt, int x, int z, UUID eigentuemer, long preis) {
        // Auf Millisekunden kuerzen: Die Spalte ist DATETIME(3). Ohne das gaebe anlegen()
        // einen genaueren Zeitpunkt zurueck als der, der wirklich gespeichert wird - zwei
        // Wahrheiten fuer dieselbe Parzelle.
        Instant jetzt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        // Art, Elternteil und Zustand als Literale und nicht ueber den Vorgabewert der
        // Spalte: Was dieser Schreibweg erzeugt, soll hier stehen und nicht im Schema.
        // Die Platzhalter 1 bis 6 behalten dabei ihre Nummern.
        String sql = "INSERT INTO parzellen "
                + "(welt, x, z, eigentuemer, preis, erworben_am, art, eltern_id, zustand) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'parzelle', NULL, 'aktiv')";
        try (PreparedStatement anfrage = datenbank.verbindung()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            anfrage.setString(1, welt);
            anfrage.setInt(2, x);
            anfrage.setInt(3, z);
            anfrage.setString(4, eigentuemer.toString());
            anfrage.setLong(5, preis);
            // LocalDateTime statt Timestamp: Timestamp rechnet in die Zeitzone der JVM
            // um, und zwar beim Schreiben wie beim Lesen. Eine DATETIME-Spalte traegt
            // Wanduhrzeit ohne Zone - also schreiben wir UTC-Wanduhrzeit und lesen sie
            // genauso zurueck. Dann rechnet unterwegs niemand mehr.
            anfrage.setObject(6, LocalDateTime.ofInstant(jetzt, ZoneOffset.UTC));
            anfrage.executeUpdate();
            try (ResultSet schluessel = anfrage.getGeneratedKeys()) {
                schluessel.next();
                return new Parzelle(schluessel.getLong(1), welt, x, z, eigentuemer,
                        Set.of(), preis, jetzt,
                        Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV);
            }
        } catch (SQLIntegrityConstraintViolationException schonDa) {
            // Der eindeutige Schluessel hat zugeschlagen - jemand war schneller. Genau
            // dieselbe Ausnahme wie beim Lager im Arbeitsspeicher, damit der Vertrag haelt.
            throw new IllegalStateException(
                    "Parzelle ist bereits vergeben: " + welt + "/" + x + "/" + z, schonDa);
        } catch (SQLException fehler) {
            throw weiterreichen("Anlegen einer Parzelle", fehler);
        }
    }

    @Override
    public void mitbauerSetzen(long id, Set<UUID> mitbauer) {
        try {
            Connection c = datenbank.verbindung();
            boolean vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // schreibeMitbauerDurch nimmt die Gruppe UND alle ihre Kinder. Bei
                // einer Rasterzelle ohne Kinder ist das genau die eine Zeile - deshalb
                // braucht es hier keine Fallunterscheidung.
                schreibeMitbauerDurch(c, id, mitbauer);
                c.commit();
            } catch (SQLException fehler) {
                // rollback laeuft ueber dieselbe, vielleicht gerade gestorbene Verbindung.
                // Wirft es, darf seine Ausnahme nicht die eigentliche ersetzen - sonst ist
                // der Grund des Fehlschlags weg und im Protokoll steht nur noch, dass das
                // Zuruecknehmen nicht ging.
                try {
                    c.rollback();
                } catch (SQLException beimZuruecknehmen) {
                    fehler.addSuppressed(beimZuruecknehmen);
                }
                throw fehler;
            } finally {
                try {
                    c.setAutoCommit(vorherAutomatisch);
                } catch (SQLException beimZuruecksetzen) {
                    // Jetzt steht die Verbindung womoeglich dauerhaft auf autoCommit=false.
                    // Weitergegeben wuerde jeder weitere Schreibvorgang in einer
                    // Transaktion landen, die niemand mehr committet - also lieber weg
                    // damit, der naechste Zugriff baut eine frische auf.
                    datenbank.verwerfen();
                }
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Setzen der Mitbauer", fehler);
        }
    }

    @Override
    public void entfernen(long id) {
        try (PreparedStatement anfrage = datenbank.verbindung()
                .prepareStatement("DELETE FROM parzellen WHERE id = ? AND art <> 'gruppe'")) {
            anfrage.setLong(1, id);
            // Die Mitbauer raeumt der Fremdschluessel mit ON DELETE CASCADE weg.
            if (anfrage.executeUpdate() == 0 && istGruppe(id)) {
                // Ohne diesen Riegel taete entfernen() bei einer Gruppe still gar nichts,
                // und der Aufrufer glaubte, sie sei weg. Nachgesehen wird nur im
                // Fehlerfall - und nur hier, denn eine unbekannte Nummer zu entfernen ist
                // laut Vertrag ausdruecklich KEIN Fehler.
                throw new IllegalStateException(
                        "Nummer " + id + " ist eine Gruppe - erst auftrennen");
            }
        } catch (SQLIntegrityConstraintViolationException hatKinder) {
            // fk_besitz_elternteil steht auf RESTRICT: Eine Zeile, auf die eltern_id
            // zeigt, laesst sich nicht loeschen - sonst naehme ein Aufgeben die Kinder
            // mit. Das hier ist Protokolltext und KEINE Spielerantwort; die kommt aus
            // Bescheid.TEIL_EINER_GRUPPE, bevor es ueberhaupt so weit kommt.
            throw new IllegalStateException("Besitz " + id
                    + " hat noch untergeordnete Eintraege - erst auftrennen", hatKinder);
        } catch (SQLException fehler) {
            throw weiterreichen("Entfernen einer Parzelle", fehler);
        }
    }

    @Override
    public List<Parzelle> kinderVon(long gruppeId) {
        // Bewusst ohne AUF_DEM_RASTER: Ein Kind ohne Rasterzelle waere ein Fehler, und
        // dann soll der Riegel in lies() laut werden, statt die Zeile still zu
        // verschweigen.
        String sql = "SELECT " + SPALTEN + " FROM parzellen WHERE eltern_id = ? "
                + "ORDER BY x, z";
        List<Parzelle> treffer = new ArrayList<>();
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setLong(1, gruppeId);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                while (ergebnis.next()) {
                    treffer.add(lies(ergebnis));
                }
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Lesen der Gruppenfelder", fehler);
        }
        return List.copyOf(treffer);
    }

    @Override
    public long gruppeSetzen(long gruppeId, String welt, UUID eigentuemer,
                             Set<UUID> mitbauer, long gebuehr, List<Long> felder) {
        if (felder.isEmpty()) {
            throw new IllegalStateException("Kein Feld zum Anhaengen angegeben");
        }
        boolean vorherAutomatisch = true;
        Connection c = null;
        try {
            c = datenbank.verbindung();
            vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                long id = gruppeId == Parzelle.KEIN_ELTERNTEIL
                        ? legeGruppeAn(c, welt, eigentuemer, gebuehr)
                        : erweitereGruppe(c, gruppeId, gebuehr);
                haengeFelderAn(c, id, welt, eigentuemer, felder);
                schreibeMitbauerDurch(c, id, mitbauer);
                c.commit();
                return id;
            } catch (SQLException | RuntimeException fehler) {
                zuruecknehmen(c, fehler);
                throw fehler;
            } finally {
                zurueckAufAutomatik(c, vorherAutomatisch);
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Anlegen oder Erweitern einer Gruppe", fehler);
        }
    }

    @Override
    public void auftrennen(long gruppeId) {
        boolean vorherAutomatisch = true;
        Connection c = null;
        try {
            c = datenbank.verbindung();
            vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // Reihenfolge ist Pflicht: fk_besitz_elternteil steht auf RESTRICT.
                // Andersherum verweigert InnoDB die Zeile, weil noch Kinder zeigen.
                try (PreparedStatement loesen = c.prepareStatement(
                        "UPDATE parzellen SET eltern_id = NULL WHERE eltern_id = ?")) {
                    loesen.setLong(1, gruppeId);
                    loesen.executeUpdate();
                }
                try (PreparedStatement weg = c.prepareStatement(
                        "DELETE FROM parzellen WHERE id = ? AND art = 'gruppe'")) {
                    weg.setLong(1, gruppeId);
                    if (weg.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "Nummer " + gruppeId + " ist keine Gruppe");
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException fehler) {
                zuruecknehmen(c, fehler);
                throw fehler;
            } finally {
                zurueckAufAutomatik(c, vorherAutomatisch);
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Auftrennen einer Gruppe", fehler);
        }
    }

    private long legeGruppeAn(Connection c, String welt, UUID eigentuemer, long gebuehr)
            throws SQLException {
        String sql = "INSERT INTO parzellen "
                + "(welt, x, z, eigentuemer, preis, erworben_am, art, eltern_id, zustand) "
                + "VALUES (?, NULL, NULL, ?, ?, ?, 'gruppe', NULL, 'aktiv')";
        try (PreparedStatement anfrage =
                     c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            anfrage.setString(1, welt);
            anfrage.setString(2, eigentuemer.toString());
            anfrage.setLong(3, gebuehr);
            anfrage.setObject(4, LocalDateTime.ofInstant(
                    Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC));
            anfrage.executeUpdate();
            try (ResultSet schluessel = anfrage.getGeneratedKeys()) {
                if (!schluessel.next()) {
                    throw new IllegalStateException("Die Gruppe bekam keine Nummer");
                }
                return schluessel.getLong(1);
            }
        }
    }

    private long erweitereGruppe(Connection c, long gruppeId, long gebuehr)
            throws SQLException {
        // FOR UPDATE sperrt die Zeile und reiht gleichzeitige Erweiterungen hintereinander
        // auf. Ohne sie koennten zwei Erweiterungen denselben Preis lesen und einer der
        // beiden Beitraege verschwaende.
        try (PreparedStatement pruefen = c.prepareStatement(
                "SELECT preis FROM parzellen WHERE id = ? AND art = 'gruppe' FOR UPDATE")) {
            pruefen.setLong(1, gruppeId);
            try (ResultSet ergebnis = pruefen.executeQuery()) {
                if (!ergebnis.next()) {
                    throw new IllegalStateException(
                            "Keine Gruppe mit der Nummer " + gruppeId);
                }
            }
        }
        // erworben_am bleibt: Die Gruppe entsteht einmal. Eine Gruppe, die bei jedem
        // Wachsen juenger wird als ihre Kinder, setzt jede kuenftige Verfallsrechnung
        // still zurueck.
        try (PreparedStatement teurer = c.prepareStatement(
                "UPDATE parzellen SET preis = preis + ? WHERE id = ?")) {
            teurer.setLong(1, gebuehr);
            teurer.setLong(2, gruppeId);
            teurer.executeUpdate();
        }
        return gruppeId;
    }

    private void haengeFelderAn(Connection c, long gruppeId, String welt, UUID eigentuemer,
                                List<Long> felder) throws SQLException {
        // Die vier Zusatzbedingungen sind der Wettlaufriegel. Sie machen drei Dinge
        // unmoeglich, die kein Programm allein verhindern kann, weil zwischen Nachsehen
        // und Schreiben immer ein zweiter Spieler passt: ein fremdes Feld anhaengen,
        // ein Feld zweimal vergeben (eltern_id IS NULL) und eine GRUPPE als Kind
        // anhaengen (x IS NOT NULL - eine Gruppe hat kein eltern_id und waere es sonst).
        // Dasselbe Prinzip wie der eindeutige Schluessel ort.
        String sql = "UPDATE parzellen SET eltern_id = ? WHERE id = ? AND welt = ? "
                + "AND eigentuemer = ? AND eltern_id IS NULL AND x IS NOT NULL";
        try (PreparedStatement anfrage = c.prepareStatement(sql)) {
            for (long feldId : felder) {
                anfrage.setLong(1, gruppeId);
                anfrage.setLong(2, feldId);
                anfrage.setString(3, welt);
                anfrage.setString(4, eigentuemer.toString());
                if (anfrage.executeUpdate() != 1) {
                    throw new IllegalStateException("Feld " + feldId + " laesst sich nicht "
                            + "anhaengen: Es fehlt, gehoert jemand anderem, liegt nicht auf "
                            + "dem Raster oder ist schon in einer Gruppe");
                }
            }
        }
    }

    private void schreibeMitbauerDurch(Connection c, long gruppeId, Set<UUID> mitbauer)
            throws SQLException {
        List<Long> zeilen = new ArrayList<>();
        zeilen.add(gruppeId);
        try (PreparedStatement kinder = c.prepareStatement(
                "SELECT id FROM parzellen WHERE eltern_id = ?")) {
            kinder.setLong(1, gruppeId);
            try (ResultSet ergebnis = kinder.executeQuery()) {
                while (ergebnis.next()) {
                    zeilen.add(ergebnis.getLong(1));
                }
            }
        }
        try (PreparedStatement weg = c.prepareStatement(
                "DELETE FROM parzellen_mitbauer WHERE parzelle_id = ?")) {
            for (long zeile : zeilen) {
                weg.setLong(1, zeile);
                weg.addBatch();
            }
            weg.executeBatch();
        }
        if (mitbauer.isEmpty()) {
            return;
        }
        try (PreparedStatement hin = c.prepareStatement(
                "INSERT INTO parzellen_mitbauer (parzelle_id, spieler) VALUES (?, ?)")) {
            for (long zeile : zeilen) {
                for (UUID spieler : mitbauer) {
                    hin.setLong(1, zeile);
                    hin.setString(2, spieler.toString());
                    hin.addBatch();
                }
            }
            hin.executeBatch();
        }
    }

    private void zuruecknehmen(Connection c, Exception ursache) {
        try {
            c.rollback();
        } catch (SQLException beimZuruecknehmen) {
            ursache.addSuppressed(beimZuruecknehmen);
        }
    }

    private void zurueckAufAutomatik(Connection c, boolean vorher) {
        try {
            c.setAutoCommit(vorher);
        } catch (SQLException beimZuruecksetzen) {
            // Weitergegeben landete jeder weitere Schreibvorgang in einer Transaktion,
            // die niemand mehr committet - also lieber weg damit.
            datenbank.verwerfen();
        }
    }

    private boolean istGruppe(long id) throws SQLException {
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(
                "SELECT 1 FROM parzellen WHERE id = ? AND art = 'gruppe'")) {
            anfrage.setLong(1, id);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next();
            }
        }
    }

    private Optional<Parzelle> nachNummer(long id) throws SQLException {
        // Bewusst ohne AUF_DEM_RASTER: Wer hier nach einer Nummer fragt, hat sie gerade
        // selbst geschrieben. Liegt sie nicht auf dem Raster, soll das laut auffallen -
        // siehe den Riegel in lies().
        String sql = "SELECT " + SPALTEN + " FROM parzellen WHERE id = ?";
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setLong(1, id);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? Optional.of(lies(ergebnis)) : Optional.empty();
            }
        }
    }

    private Parzelle lies(ResultSet ergebnis) throws SQLException {
        long id = ergebnis.getLong("id");
        Integer x = ergebnis.getObject("x", Integer.class);
        Integer z = ergebnis.getObject("z", Integer.class);
        if (x == null || z == null) {
            // getInt() gaebe hier lautlos 0 zurueck - also die Parzelle 0/0 mitten in der
            // Marktstadt, mit dem Eigentuemer dieser Zeile. 0/0 ist in der Spieldatenbank
            // vergeben. Wer Besitz ohne Rasterzelle lesen will, braucht einen eigenen
            // Leseweg, keine stille Notluege.
            throw new IllegalStateException("Besitz " + id + " der Art "
                    + ergebnis.getString("art") + " liegt nicht auf dem Raster");
        }
        return new Parzelle(
                id,
                ergebnis.getString("welt"),
                x,
                z,
                UUID.fromString(ergebnis.getString("eigentuemer")),
                mitbauerVon(id),
                ergebnis.getLong("preis"),
                ergebnis.getObject("erworben_am", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC),
                Art.ausSchluessel(ergebnis.getString("art")),
                // SQL NULL wird hier zu 0, und 0 ist KEIN_ELTERNTEIL. Die eine Stelle,
                // an der sich die Wahl der Null auszahlt.
                ergebnis.getLong("eltern_id"),
                Zustand.ausSchluessel(ergebnis.getString("zustand")));
    }

    private Set<UUID> mitbauerVon(long id) throws SQLException {
        Set<UUID> namen = new LinkedHashSet<>();
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(
                "SELECT spieler FROM parzellen_mitbauer WHERE parzelle_id = ? "
                        + "ORDER BY spieler")) {
            anfrage.setLong(1, id);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                while (ergebnis.next()) {
                    namen.add(UUID.fromString(ergebnis.getString("spieler")));
                }
            }
        }
        return namen;
    }
}
