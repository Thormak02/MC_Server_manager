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

    /**
     * Wie lange eine einzelne Anweisung hoechstens auf Antwort wartet.
     *
     * <p>Muss gesetzt sein: Der Treiber nimmt ohne Angabe 0, also unbegrenzt. Schlaeft der
     * Datenbankrechner ein oder verwirft eine Firewall den bestehenden Fluss, wartet eine
     * Abfrage dann, bis der TCP-Stack von selbst aufgibt - Minuten. Das laeuft im
     * Hauptfaden des Spielservers, weil der Besitzschutz bei jedem Blockereignis dort
     * fragt. Fuenf Sekunden sind fuer ein lokales Netz reichlich und fuer einen stehenden
     * Server kurz genug, dass daraus ein Fehler wird statt eines Stillstands.
     */
    private static final String ANTWORTFRIST_MS = "5000";

    /** Wie lange der Aufbau einer neuen Verbindung hoechstens dauern darf. */
    private static final String AUFBAUFRIST_MS = "3000";

    /**
     * Wie lange eine einmal gepruefte Verbindung als brauchbar gilt.
     *
     * <p>{@code isValid} ist kein lokaler Test, sondern ein Ping ueber das Netz. Bei jedem
     * Statement geprueft verdoppelt das die Netzrunden: Fuer einen abgebauten Block auf
     * einer vergebenen Parzelle waren es vier statt zwei. Eine Sekunde Vertrauen kostet
     * nichts, denn eine Anweisung an einer inzwischen toten Verbindung scheitert mit einer
     * SQLException - und darauf wird ohnehin neu aufgebaut.
     */
    private static final long PRUEFABSTAND_MS = 1_000L;

    private final String url;
    private final String benutzer;
    private final String passwort;
    private Connection verbindung;
    private long zuletztGeprueft;

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
        if (verbindung != null && !brauchbar()) {
            schliesseLeise();
        }
        if (verbindung == null) {
            Properties angaben = new Properties();
            angaben.setProperty("user", benutzer);
            angaben.setProperty("password", passwort);
            // Vorsorge fuer kuenftige TIMESTAMP-Spalten. Die Zeitrichtigkeit der
            // DATETIME-Spalten haengt NICHT daran, sondern daran, dass ueberall
            // LocalDateTime in UTC geschrieben und gelesen wird - Timestamp rechnet
            // sonst in die Zeitzone der JVM um, hin wie zurueck.
            angaben.setProperty("connectionTimeZone", "UTC");
            angaben.setProperty("forceConnectionTimeZoneToSession", "true");
            angaben.setProperty("socketTimeout", ANTWORTFRIST_MS);
            angaben.setProperty("connectTimeout", AUFBAUFRIST_MS);
            verbindung = TREIBER.connect(url, angaben);
            if (verbindung == null) {
                // connect() gibt null zurueck, wenn der Treiber die Adresse nicht annimmt.
                throw new SQLException("Keine MariaDB-Adresse: " + url);
            }
            zuletztGeprueft = System.nanoTime();
        }
        return verbindung;
    }

    /**
     * Ist die gehaltene Verbindung noch zu gebrauchen?
     *
     * <p>Gedrosselt: Wurde sie vor weniger als {@link #PRUEFABSTAND_MS} schon geprueft,
     * gilt sie als gut, ohne dass dafuer ein Ping ueber das Netz geht. nanoTime und nicht
     * currentTimeMillis, weil nur nanoTime monoton laeuft - eine zurueckgestellte Uhr
     * wuerde den Abstand sonst negativ machen.
     */
    private boolean brauchbar() throws SQLException {
        if (verbindung.isClosed()) {
            return false;
        }
        long seit = (System.nanoTime() - zuletztGeprueft) / 1_000_000L;
        if (seit < PRUEFABSTAND_MS) {
            return true;
        }
        boolean gut = verbindung.isValid(2);
        if (gut) {
            zuletztGeprueft = System.nanoTime();
        }
        return gut;
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
     *
     * <p>Verweigert jedes Schema, dessen Name nicht auf {@code _test} endet. Die Sperre
     * sitzt hier und nicht in der Gradle-Datei oder in den Testklassen, weil sie dort von
     * jedem kuenftigen Testlauf und jeder kuenftigen Testklasse neu eingehalten werden
     * muesste - und eine davon wuerde es vergessen. Hier fragt die Methode die Datenbank
     * selbst, wo sie steckt, und kann nicht umgangen werden.
     *
     * @throws IllegalStateException wenn die Verbindung nicht in einem Testschema haengt
     */
    public synchronized void leereAlles() throws SQLException {
        String schema = schemaName();
        if (!schema.endsWith("_test")) {
            throw new IllegalStateException(
                    "leereAlles() verweigert das Schema \"" + schema + "\": Es loescht ALLE "
                            + "Parzellen, Mitbauer und Guthaben und ist nur fuer Tests "
                            + "gedacht. Erlaubt sind nur Schemata, deren Name auf _test "
                            + "endet. In data/freimarken/datenbank.properties gehoert dafuer "
                            + "url.test, angelegt von skripte/mariadb_einrichten.bat.");
        }
        try (Statement anweisung = verbindung().createStatement()) {
            anweisung.execute("DELETE FROM parzellen_mitbauer");
            anweisung.execute("DELETE FROM parzellen");
            anweisung.execute("DELETE FROM kassen");
        }
    }

    /**
     * Die gehaltene Verbindung wegwerfen; der naechste Zugriff baut eine neue auf.
     *
     * <p>Fuer den Fall, dass ein Aufrufer die Verbindung in einem Zustand zurueckliesse,
     * den er nicht aufraeumen konnte - etwa mit abgeschaltetem autoCommit, weil schon das
     * Zurueckstellen gescheitert ist. Eine solche Verbindung weiterzugeben waere der
     * schlimmste Fall: Schreibvorgaenge landeten in einer Transaktion, die niemand mehr
     * committet, und verschwinden lautlos.
     */
    public synchronized void verwerfen() {
        schliesseLeise();
    }

    /** In welchem Schema die Verbindung gerade haengt. Leer, wenn keines gewaehlt ist. */
    public synchronized String schemaName() throws SQLException {
        try (Statement anweisung = verbindung().createStatement();
             var ergebnis = anweisung.executeQuery("SELECT DATABASE()")) {
            if (!ergebnis.next()) {
                return "";
            }
            String name = ergebnis.getString(1);
            return name != null ? name : "";
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
