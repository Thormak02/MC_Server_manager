package de.freimarken.daten;

import de.freimarken.kern.Grundstueck;
import de.freimarken.kern.Grundstuecksablage;
import de.freimarken.kern.Zellquader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Die Grundstuecke in MariaDB - Schemaschritte 20 und 21.
 *
 * <p><b>Die Ueberschneidung wird mit einer SPERRE gehalten und nicht mit einem
 * Schluessel</b>, und das ist der Unterschied zum Namen einer Freistadt: Dort macht
 * {@code uq_besitz_name} wirklich dicht, weil zwei gleiche Namen gleich SIND. Zwei
 * Quader koennen sich aber ueberschneiden, ohne gleich zu sein - dagegen gibt es keinen
 * eindeutigen Schluessel. Darum liest {@link #anlegen} die Grundstuecke der Stadt mit
 * {@code FOR UPDATE} und schreibt in DERSELBEN Transaktion. Zwei Spieler, die gleichzeitig
 * dieselbe Flaeche abstecken, laufen damit hintereinander durch: Der zweite sieht den
 * ersten und bekommt eine Absage.
 *
 * <p><b>Gesperrt wird je STADT und nicht je Welt.</b> Eine Weltsperre haette alle 399
 * Staedte in eine Reihe gestellt - zwei Stadtbesitzer, die nichts miteinander zu tun
 * haben, warteten aufeinander. Die Stadt ist die kleinste Einheit, in der eine
 * Ueberschneidung ueberhaupt moeglich ist: Grundstuecke zweier Staedte liegen auf
 * verschiedenen Feldern, und dazwischen ist Leere.
 *
 * <p><b>Die Mitbauer kommen in EINER zweiten Abfrage</b> und nicht je Zeile: Wer alle
 * Grundstuecke einer Welt liest (einmal beim Start, fuer den Zwischenspeicher), holte
 * sonst je Zeile eine eigene Runde. Bei einer Stadt mit 256 Grundstuecken waeren das 256
 * Rundlaeufe statt zwei.
 *
 * <p>Zeiten gehen ueber {@link Datenbank#utcKalender()} - die Regel dieses Schemas kennt
 * keine Ausnahme.
 */
public final class MariaGrundstuecke implements Grundstuecksablage {

    private static final String SPALTEN =
            "id, welt, stadt_id, eigentuemer, von_x, von_z, bis_x, bis_z, preis,"
                    + " erworben_am";

    private static final String EINFUEGEN =
            "INSERT INTO grundstuecke (welt, stadt_id, eigentuemer, von_x, von_z,"
                    + " bis_x, bis_z, preis, erworben_am)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** Die Grundstuecke einer Stadt - mit Sperre, fuer die Ueberschneidungspruefung. */
    private static final String LESEN_STADT_GESPERRT =
            "SELECT " + SPALTEN + " FROM grundstuecke WHERE stadt_id = ? FOR UPDATE";

    private static final String LESEN_STADT =
            "SELECT " + SPALTEN + " FROM grundstuecke WHERE stadt_id = ? ORDER BY id";

    private static final String LESEN_WELT =
            "SELECT " + SPALTEN + " FROM grundstuecke WHERE welt = ? ORDER BY id";

    private static final String LESEN_SPIELER =
            "SELECT " + SPALTEN + " FROM grundstuecke WHERE welt = ? AND eigentuemer = ?"
                    + " ORDER BY id";

    private static final String LESEN_ZELLE =
            "SELECT " + SPALTEN + " FROM grundstuecke WHERE welt = ? AND von_x <= ?"
                    + " AND bis_x >= ? AND von_z <= ? AND bis_z >= ? LIMIT 1";

    private static final String LOESCHEN = "DELETE FROM grundstuecke WHERE id = ?";

    /**
     * Die Mitbauer zu MEHREREN Grundstuecken - der Anfang, die Fragezeichen folgen.
     *
     * <p>Zusammengesetzt und nicht fest, weil die Zahl der Nummern wechselt. Gebaut wird
     * ausschliesslich aus der ANZAHL der gelesenen Zeilen, nie aus einem Wert - es geht
     * kein Zeichen aus der Datenbank und keines von einem Spieler in diese Zeichenkette
     * ein. Jeder Wert kommt weiter als Fragezeichen.
     */
    private static final String MITBAUER_VIELE =
            "SELECT grundstueck_id, spieler FROM grundstuecke_mitbauer"
                    + " WHERE grundstueck_id IN (";

    private static final String MITBAUER_EINER =
            "SELECT spieler FROM grundstuecke_mitbauer WHERE grundstueck_id = ?";

    private static final String MITBAUER_RAEUMEN =
            "DELETE FROM grundstuecke_mitbauer WHERE grundstueck_id = ?";

    private static final String MITBAUER_EINFUEGEN =
            "INSERT INTO grundstuecke_mitbauer (grundstueck_id, spieler) VALUES (?, ?)";

    private final Datenbank datenbank;

    public MariaGrundstuecke(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    /** SQL-Fehler sind hier nicht behebbar - sie gehoeren nach oben, nicht ins Logbuch. */
    private static RuntimeException weiterreichen(String was, SQLException ursache) {
        return new IllegalStateException("Datenbankfehler beim " + was + ": "
                + ursache.getMessage(), ursache);
    }

    @Override
    public Grundstueck anlegen(String welt, long stadtId, Zellquader flaeche,
                               UUID eigentuemer, long preis) {
        if (flaeche == null || eigentuemer == null) {
            throw new IllegalArgumentException("Ohne Flaeche oder ohne Eigentuemer gibt"
                    + " es kein Grundstueck");
        }
        try {
            Connection c = datenbank.verbindung();
            boolean vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // Lesen MIT Sperre und Schreiben in DERSELBEN Transaktion - siehe
                // Javadoc der Klasse. Ohne das liegt zwischen Pruefen und Schreiben ein
                // Augenblick, in dem ein zweiter Spieler dieselbe Flaeche absteckt.
                for (Zellquader vorhanden : flaechenDerStadt(c, stadtId)) {
                    if (vorhanden.schneidet(flaeche)) {
                        // Geworfen und im catch darunter zurueckgenommen - damit der
                        // Aufrufer dieselbe Meldung bekommt wie von
                        // SpeicherGrundstuecke.
                        throw new IllegalStateException("Die Flaeche "
                                + flaeche.alsText() + " ueberschneidet sich mit "
                                + vorhanden.alsText());
                    }
                }
                long nummer = einfuegen(c, welt, stadtId, flaeche, eigentuemer, preis);
                c.commit();
                // Erst NACH dem commit gelesen: Vorher stuende die Zeile nur in dieser
                // Transaktion, und der Zeitpunkt aus der Datenbank waere ein anderer als
                // der, den ein zweiter Leser sieht.
                return einesOder(c, nummer);
            } catch (SQLException | RuntimeException fehler) {
                // Das rollback ist die tragende Zeile, nicht das commit darueber:
                // setAutoCommit(true) im finally committet eine offene Transaktion.
                try {
                    c.rollback();
                } catch (SQLException beimZuruecknehmen) {
                    fehler.addSuppressed(beimZuruecknehmen);
                }
                throw fehler;
            } finally {
                c.setAutoCommit(vorherAutomatisch);
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Anlegen eines Grundstuecks", fehler);
        }
    }

    @Override
    public Optional<Grundstueck> anZelle(String welt, int zelleX, int zelleZ) {
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN_ZELLE)) {
            anfrage.setString(1, welt);
            anfrage.setInt(2, zelleX);
            anfrage.setInt(3, zelleX);
            anfrage.setInt(4, zelleZ);
            anfrage.setInt(5, zelleZ);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                if (!ergebnis.next()) {
                    return Optional.empty();
                }
                long nummer = ergebnis.getLong("id");
                return Optional.of(ausZeile(ergebnis,
                        mitbauerEines(datenbank.verbindung(), nummer)));
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Lesen eines Grundstuecks", fehler);
        }
    }

    @Override
    public List<Grundstueck> inStadt(long stadtId) {
        return liste(LESEN_STADT, anfrage -> anfrage.setLong(1, stadtId),
                "Lesen der Grundstuecke einer Stadt");
    }

    @Override
    public List<Grundstueck> alle(String welt) {
        return liste(LESEN_WELT, anfrage -> anfrage.setString(1, welt),
                "Lesen aller Grundstuecke");
    }

    @Override
    public List<Grundstueck> vonSpieler(String welt, UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        return liste(LESEN_SPIELER, anfrage -> {
            anfrage.setString(1, welt);
            anfrage.setString(2, spieler.toString());
        }, "Lesen der Grundstuecke eines Spielers");
    }

    @Override
    public void mitbauerSetzen(long id, Set<UUID> mitbauer) {
        try {
            Connection c = datenbank.verbindung();
            boolean vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // Erst nachsehen, ob es die Zeile GIBT: Ohne das waere ein Setzen auf
                // eine unbekannte Nummer still erfolgreich, und SpeicherGrundstuecke
                // wirft dort. Zwei Umsetzungen hinter einer Schnittstelle muessen
                // dasselbe tun.
                if (!gibtEs(c, id)) {
                    throw new IllegalStateException("Kein Grundstueck mit der Nummer "
                            + id);
                }
                try (PreparedStatement raeumen =
                             c.prepareStatement(MITBAUER_RAEUMEN)) {
                    raeumen.setLong(1, id);
                    raeumen.executeUpdate();
                }
                if (mitbauer != null && !mitbauer.isEmpty()) {
                    try (PreparedStatement einfuegen =
                                 c.prepareStatement(MITBAUER_EINFUEGEN)) {
                        for (UUID wer : mitbauer) {
                            einfuegen.setLong(1, id);
                            einfuegen.setString(2, wer.toString());
                            einfuegen.addBatch();
                        }
                        einfuegen.executeBatch();
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException fehler) {
                try {
                    c.rollback();
                } catch (SQLException beimZuruecknehmen) {
                    fehler.addSuppressed(beimZuruecknehmen);
                }
                throw fehler;
            } finally {
                c.setAutoCommit(vorherAutomatisch);
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Setzen der Mitbauer", fehler);
        }
    }

    @Override
    public void entfernen(long id) {
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LOESCHEN)) {
            anfrage.setLong(1, id);
            if (anfrage.executeUpdate() == 0) {
                throw new IllegalStateException("Kein Grundstueck mit der Nummer " + id);
            }
            // Die Mitbauer gehen ueber ON DELETE CASCADE mit - siehe Schemaschritt 21.
        } catch (SQLException fehler) {
            throw weiterreichen("Aufloesen eines Grundstuecks", fehler);
        }
    }

    // ------------------------------------------------------------------ innen

    /** Was ein Vorbereiter mit der Anfrage tut - fuer {@link #liste}. */
    private interface Besetzer {
        void besetze(PreparedStatement anfrage) throws SQLException;
    }

    /**
     * Eine Liste lesen, samt Mitbauern in EINER zweiten Abfrage.
     *
     * <p>Die Mitbauer je Zeile einzeln zu holen waere bei 256 Grundstuecken einer Stadt
     * 256 Rundlaeufe. Hier sind es zwei - und die zweite laeuft nur, wenn es ueberhaupt
     * Zeilen gibt.
     */
    private List<Grundstueck> liste(String sql, Besetzer besetzer, String was) {
        List<Grundstueck> ohneMitbauer = new ArrayList<>();
        java.util.Map<Long, Set<UUID>> mitbauer = new java.util.HashMap<>();
        try {
            Connection c = datenbank.verbindung();
            try (PreparedStatement anfrage = c.prepareStatement(sql)) {
                besetzer.besetze(anfrage);
                try (ResultSet ergebnis = anfrage.executeQuery()) {
                    while (ergebnis.next()) {
                        ohneMitbauer.add(ausZeile(ergebnis, Set.of()));
                    }
                }
            }
            if (ohneMitbauer.isEmpty()) {
                return List.of();
            }
            mitbauer.putAll(mitbauerVieler(c, ohneMitbauer));
        } catch (SQLException fehler) {
            throw weiterreichen(was, fehler);
        }
        List<Grundstueck> fertig = new ArrayList<>(ohneMitbauer.size());
        for (Grundstueck eines : ohneMitbauer) {
            fertig.add(new Grundstueck(eines.id(), eines.welt(), eines.stadtId(),
                    eines.eigentuemer(), mitbauer.get(eines.id()), eines.flaeche(),
                    eines.preis(), eines.erworbenAm()));
        }
        return List.copyOf(fertig);
    }

    private List<Zellquader> flaechenDerStadt(Connection c, long stadtId)
            throws SQLException {
        List<Zellquader> gefunden = new ArrayList<>();
        try (PreparedStatement anfrage = c.prepareStatement(LESEN_STADT_GESPERRT)) {
            anfrage.setLong(1, stadtId);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                while (ergebnis.next()) {
                    gefunden.add(new Zellquader(ergebnis.getInt("von_x"),
                            ergebnis.getInt("von_z"), ergebnis.getInt("bis_x"),
                            ergebnis.getInt("bis_z")));
                }
            }
        }
        return gefunden;
    }

    private long einfuegen(Connection c, String welt, long stadtId, Zellquader flaeche,
                           UUID eigentuemer, long preis) throws SQLException {
        try (PreparedStatement anfrage =
                     c.prepareStatement(EINFUEGEN, Statement.RETURN_GENERATED_KEYS)) {
            anfrage.setString(1, welt);
            anfrage.setLong(2, stadtId);
            anfrage.setString(3, eigentuemer.toString());
            anfrage.setInt(4, flaeche.vonX());
            anfrage.setInt(5, flaeche.vonZ());
            anfrage.setInt(6, flaeche.bisX());
            anfrage.setInt(7, flaeche.bisZ());
            anfrage.setLong(8, preis);
            anfrage.setTimestamp(9,
                    new java.sql.Timestamp(System.currentTimeMillis()),
                    Datenbank.utcKalender());
            anfrage.executeUpdate();
            try (ResultSet schluessel = anfrage.getGeneratedKeys()) {
                if (!schluessel.next()) {
                    throw new IllegalStateException("Das Grundstueck wurde geschrieben,"
                            + " aber die Datenbank nennt seine Nummer nicht");
                }
                return schluessel.getLong(1);
            }
        }
    }

    private Grundstueck einesOder(Connection c, long nummer) throws SQLException {
        try (PreparedStatement anfrage = c.prepareStatement(
                "SELECT " + SPALTEN + " FROM grundstuecke WHERE id = ?")) {
            anfrage.setLong(1, nummer);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                if (!ergebnis.next()) {
                    throw new IllegalStateException("Grundstueck " + nummer
                            + " ist nach dem Schreiben nicht mehr da");
                }
                return ausZeile(ergebnis, Set.of());
            }
        }
    }

    private boolean gibtEs(Connection c, long id) throws SQLException {
        try (PreparedStatement anfrage =
                     c.prepareStatement("SELECT 1 FROM grundstuecke WHERE id = ?")) {
            anfrage.setLong(1, id);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next();
            }
        }
    }

    /**
     * Die Mitbauer zu einer ganzen Liste - in EINER Abfrage.
     *
     * <p>Je Zeile einzeln zu fragen waere bei einer Stadt mit 256 Grundstuecken 256
     * Rundlaeufe statt einem. Das faellt beim Fuellen des Zwischenspeichers ins Gewicht,
     * also genau einmal je Serverstart - und dort steht der Server noch nicht fuer
     * Spieler offen, sondern haelt sie auf.
     *
     * <p>Grundstuecke OHNE Mitbauer kommen in der Antwort nicht vor; sie bekommen hier
     * eine leere Menge, damit der Aufrufer nicht zwischen "keine" und "nicht gefragt"
     * unterscheiden muss.
     */
    private java.util.Map<Long, Set<UUID>> mitbauerVieler(Connection c,
                                                          List<Grundstueck> welche)
            throws SQLException {
        java.util.Map<Long, Set<UUID>> gefunden = new java.util.HashMap<>();
        for (Grundstueck eines : welche) {
            gefunden.put(eines.id(), new HashSet<>());
        }
        StringBuilder sql = new StringBuilder(MITBAUER_VIELE);
        sql.append("?,".repeat(welche.size() - 1)).append("?)");
        try (PreparedStatement anfrage = c.prepareStatement(sql.toString())) {
            int stelle = 1;
            for (Grundstueck eines : welche) {
                anfrage.setLong(stelle++, eines.id());
            }
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                while (ergebnis.next()) {
                    gefunden.get(ergebnis.getLong("grundstueck_id"))
                            .add(UUID.fromString(ergebnis.getString("spieler")));
                }
            }
        }
        return gefunden;
    }

    private Set<UUID> mitbauerEines(Connection c, long id) throws SQLException {
        Set<UUID> gefunden = new HashSet<>();
        try (PreparedStatement anfrage = c.prepareStatement(MITBAUER_EINER)) {
            anfrage.setLong(1, id);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                while (ergebnis.next()) {
                    gefunden.add(UUID.fromString(ergebnis.getString("spieler")));
                }
            }
        }
        return gefunden;
    }

    private static Grundstueck ausZeile(ResultSet ergebnis, Set<UUID> mitbauer)
            throws SQLException {
        return new Grundstueck(ergebnis.getLong("id"), ergebnis.getString("welt"),
                ergebnis.getLong("stadt_id"),
                UUID.fromString(ergebnis.getString("eigentuemer")), mitbauer,
                new Zellquader(ergebnis.getInt("von_x"), ergebnis.getInt("von_z"),
                        ergebnis.getInt("bis_x"), ergebnis.getInt("bis_z")),
                ergebnis.getLong("preis"),
                ergebnis.getTimestamp("erworben_am", Datenbank.utcKalender())
                        .toInstant());
    }
}
