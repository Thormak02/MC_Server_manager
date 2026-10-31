package de.freimarken.daten;

import de.freimarken.kern.BesitzLager;
import de.freimarken.kern.Parzelle;
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
        String sql = "SELECT id, welt, x, z, eigentuemer, preis, erworben_am "
                + "FROM parzellen WHERE welt = ? AND x = ? AND z = ?";
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
        String sql = "SELECT id, welt, x, z, eigentuemer, preis, erworben_am "
                + "FROM parzellen WHERE welt = ? AND eigentuemer = ? ORDER BY x, z";
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
        String sql = "SELECT COUNT(*) FROM parzellen WHERE welt = ? AND eigentuemer = ?";
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
        String sql = "INSERT INTO parzellen (welt, x, z, eigentuemer, preis, erworben_am) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
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
                        Set.of(), preis, jetzt);
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
    public Parzelle mitbauerSetzen(long id, Set<UUID> mitbauer) {
        try {
            Connection c = datenbank.verbindung();
            boolean vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement weg = c.prepareStatement(
                        "DELETE FROM parzellen_mitbauer WHERE parzelle_id = ?")) {
                    weg.setLong(1, id);
                    weg.executeUpdate();
                }
                if (!mitbauer.isEmpty()) {
                    try (PreparedStatement rein = c.prepareStatement(
                            "INSERT INTO parzellen_mitbauer (parzelle_id, spieler) "
                                    + "VALUES (?, ?)")) {
                        for (UUID spieler : mitbauer) {
                            rein.setLong(1, id);
                            rein.setString(2, spieler.toString());
                            rein.addBatch();
                        }
                        rein.executeBatch();
                    }
                }
                c.commit();
            } catch (SQLException fehler) {
                c.rollback();
                throw fehler;
            } finally {
                c.setAutoCommit(vorherAutomatisch);
            }
            return nachNummer(id).orElseThrow(() ->
                    new IllegalStateException("Keine Parzelle mit der Nummer " + id));
        } catch (SQLException fehler) {
            throw weiterreichen("Setzen der Mitbauer", fehler);
        }
    }

    @Override
    public void entfernen(long id) {
        try (PreparedStatement anfrage = datenbank.verbindung()
                .prepareStatement("DELETE FROM parzellen WHERE id = ?")) {
            anfrage.setLong(1, id);
            anfrage.executeUpdate();
            // Die Mitbauer raeumt der Fremdschluessel mit ON DELETE CASCADE weg.
        } catch (SQLException fehler) {
            throw weiterreichen("Entfernen einer Parzelle", fehler);
        }
    }

    private Optional<Parzelle> nachNummer(long id) throws SQLException {
        String sql = "SELECT id, welt, x, z, eigentuemer, preis, erworben_am "
                + "FROM parzellen WHERE id = ?";
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setLong(1, id);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? Optional.of(lies(ergebnis)) : Optional.empty();
            }
        }
    }

    private Parzelle lies(ResultSet ergebnis) throws SQLException {
        long id = ergebnis.getLong("id");
        return new Parzelle(
                id,
                ergebnis.getString("welt"),
                ergebnis.getInt("x"),
                ergebnis.getInt("z"),
                UUID.fromString(ergebnis.getString("eigentuemer")),
                mitbauerVon(id),
                ergebnis.getLong("preis"),
                ergebnis.getObject("erworben_am", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC));
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
