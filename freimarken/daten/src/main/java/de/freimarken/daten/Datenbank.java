package de.freimarken.daten;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

/**
 * Verbindung zur Datenbank und das Schema darin.
 *
 * <p>Bewusst klein gehalten: eine Verbindung, synchronisiert, mit Wiederaufbau. Ein
 * Verbindungspool waere bei zwanzig Spielern Ziererei - und jede weitere Bibliothek muss
 * spaeter mit in die Jar und dort gegen Signaturreste und Dienstdateien verteidigt werden.
 * Kommt der zweite Backend-Server, ist das die Stelle, an der ein Pool nachgeruestet wird.
 *
 * <p>Zeiten werden als {@code DATETIME(3)} abgelegt, nicht als {@code TIMESTAMP}: MariaDB
 * rechnet TIMESTAMP-Spalten in die Zeitzone der Sitzung um. Wer dann den Server umstellt
 * oder von einem anderen Rechner zugreift, liest andere Zeiten als er geschrieben hat.
 * Geschrieben wird immer UTC.
 */
public final class Datenbank implements AutoCloseable {

    /**
     * Die Schema-Schritte, in Reihenfolge. Angehaengt wird unten, nie dazwischen -
     * die Nummer eines Schrittes ist das, woran eine bestehende Datenbank erkennt, was
     * sie schon hat.
     */
    private static final List<String> SCHRITTE = List.of(
            """
            CREATE TABLE parzellen (
              id           BIGINT       NOT NULL AUTO_INCREMENT,
              welt         VARCHAR(128) NOT NULL,
              x            INT          NOT NULL,
              z            INT          NOT NULL,
              eigentuemer  CHAR(36)     NOT NULL,
              preis        BIGINT       NOT NULL,
              erworben_am  DATETIME(3)  NOT NULL,
              PRIMARY KEY (id),
              UNIQUE KEY ort (welt, x, z),
              KEY nach_eigentuemer (welt, eigentuemer)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE parzellen_mitbauer (
              parzelle_id BIGINT   NOT NULL,
              spieler     CHAR(36) NOT NULL,
              PRIMARY KEY (parzelle_id, spieler),
              CONSTRAINT fk_mitbauer_parzelle FOREIGN KEY (parzelle_id)
                REFERENCES parzellen (id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE kassen (
              spieler CHAR(36) NOT NULL,
              mark    BIGINT   NOT NULL,
              PRIMARY KEY (spieler)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

    /**
     * Der Treiber, direkt gehalten statt ueber DriverManager gesucht.
     *
     * <p>Notwendig fuer den Betrieb als Plugin: DriverManager sammelt seine Treiber einmalig
     * ueber den ServiceLoader, mit dem Klassenlader, der gerade dran ist. In Paper hat jedes
     * Plugin seinen eigenen Lader, und hat ein anderes Plugin den DriverManager vor uns
     * angefasst, ist die Sammlung schon abgeschlossen - unser mitgelieferter Treiber steht
     * dann nicht darin. Das Ergebnis waere "No suitable driver", und zwar nur auf dem echten
     * Server, nie im Test.
     */
    private static final Driver TREIBER = new org.mariadb.jdbc.Driver();

    private final String url;
    private final String benutzer;
    private final String passwort;
    private Connection verbindung;

    public Datenbank(String url, String benutzer, String passwort) {
        this.url = url;
        this.benutzer = benutzer;
        this.passwort = passwort;
    }

    /**
     * Eine brauchbare Verbindung - baut sie bei Bedarf neu auf.
     *
     * <p>Notwendig, weil MariaDB untaetige Verbindungen nach einigen Stunden schliesst.
     * Ein Server, der nachts schlaeft und morgens weiterarbeitet, trifft genau das.
     */
    public synchronized Connection verbindung() throws SQLException {
        if (verbindung == null || verbindung.isClosed() || !verbindung.isValid(2)) {
            schliesseLeise();
            Properties angaben = new Properties();
            angaben.setProperty("user", benutzer);
            angaben.setProperty("password", passwort);
            // Vorsorge fuer kuenftige TIMESTAMP-Spalten. Die Zeitrichtigkeit der
            // DATETIME-Spalten haengt NICHT daran, sondern daran, dass ueberall
            // LocalDateTime in UTC geschrieben und gelesen wird - Timestamp rechnet
            // sonst in die Zeitzone der JVM um, hin wie zurueck.
            angaben.setProperty("connectionTimeZone", "UTC");
            angaben.setProperty("forceConnectionTimeZoneToSession", "true");
            verbindung = TREIBER.connect(url, angaben);
            if (verbindung == null) {
                // connect() gibt null zurueck, wenn der Treiber die Adresse nicht annimmt.
                throw new SQLException("Keine MariaDB-Adresse: " + url);
            }
        }
        return verbindung;
    }

    /** Legt fehlende Tabellen an. Mehrfach aufrufbar. */
    public synchronized void migriere() throws SQLException {
        Connection c = verbindung();
        try (Statement anweisung = c.createStatement()) {
            anweisung.execute("""
                    CREATE TABLE IF NOT EXISTS schema_stand (
                      schritt   INT         NOT NULL,
                      erledigt  DATETIME(3) NOT NULL,
                      PRIMARY KEY (schritt)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }

        int stand = 0;
        try (Statement anweisung = c.createStatement();
             var ergebnis = anweisung.executeQuery(
                     "SELECT COALESCE(MAX(schritt), 0) FROM schema_stand")) {
            if (ergebnis.next()) {
                stand = ergebnis.getInt(1);
            }
        }

        for (int nummer = stand + 1; nummer <= SCHRITTE.size(); nummer++) {
            try (Statement anweisung = c.createStatement()) {
                anweisung.execute(SCHRITTE.get(nummer - 1));
                anweisung.execute("INSERT INTO schema_stand (schritt, erledigt) VALUES ("
                        + nummer + ", UTC_TIMESTAMP(3))");
            }
        }
    }

    /**
     * Alle Tabellen leeren - nur fuer Tests.
     *
     * <p>Bewusst {@code DELETE} und kein {@code DROP}: So bleibt das Schema stehen und ein
     * Testlauf prueft weiterhin gegen die echten Schluessel und Fremdschluessel.
     */
    public synchronized void leereAlles() throws SQLException {
        try (Statement anweisung = verbindung().createStatement()) {
            anweisung.execute("DELETE FROM parzellen_mitbauer");
            anweisung.execute("DELETE FROM parzellen");
            anweisung.execute("DELETE FROM kassen");
        }
    }

    private void schliesseLeise() {
        if (verbindung != null) {
            try {
                verbindung.close();
            } catch (SQLException ignoriert) {
                // Eine Verbindung, die sich nicht schliessen laesst, ist ohnehin hin.
            }
            verbindung = null;
        }
    }

    @Override
    public synchronized void close() {
        schliesseLeise();
    }
}
