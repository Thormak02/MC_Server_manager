package de.freimarken.daten;

import de.freimarken.kern.Abholablage;
import de.freimarken.kern.Abholgut;
import de.freimarken.kern.Abholstueck;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Das Abholfach in MariaDB - eine Zeile je Stapel.
 *
 * <p>Haelt denselben {@code AbholVertrag} wie die Fassung im Arbeitsspeicher.
 *
 * <p><b>Die Nummern kommen von {@code AUTO_INCREMENT}</b>, und das ist der eine Punkt, an
 * dem diese Fassung weniger zusagt als die im Arbeitsspeicher: Der Zaehler von InnoDB
 * ueberlebt in MariaDB einen Neustart nicht zuverlaessig - er beginnt dann bei
 * {@code MAX(id) + 1}. Wurde die hoechste Zeile vorher abgeholt, wird ihre Nummer ein
 * zweites Mal vergeben. Darum fragt {@link #nimm} nach {@code empfaenger} UND {@code id};
 * ohne den Empfaenger holte ein alt gewordenes Fenster die Ware eines fremden Spielers.
 * Genau deshalb steht "eine Nummer wird nie wiederverwendet" NICHT im geteilten Vertrag,
 * sondern nur bei {@code SpeicherAbholfach}.
 *
 * <p><b>{@link #lege} schreibt in EINER Transaktion</b> - das zweite Versprechen der
 * Ablage. Eine halb eingelegte Truhe waere schlimmer als eine nicht eingelegte: Beim
 * zweiten Versuch stuenden die ersten Stapel doppelt im Fach, und ein verdoppelter
 * Diamantblock ist in einer Wirtschaft mit Bundesmarkt sofort Geld. Dieselbe Bauart wie
 * {@link MariaMiete#schreibe}, samt der Erkenntnis von dort: Das {@code rollback()} im
 * {@code catch} ist die tragende Zeile, nicht das {@code commit()} - denn
 * {@code setAutoCommit(true)} im {@code finally} committet eine offene Transaktion
 * ohnehin.
 *
 * <p><b>Diese Stelle ist allein durch LESEN gesichert, und das ist gemessen</b>
 * (08.11.2026): Eine Einspritzung, die das {@code rollback()} wegnimmt, bleibt gruen.
 * Hier stand einmal, der Teilfehlschlag sei anders als bei {@code MariaMiete}
 * herstellbar - ein Stapel, der die Spalte sprengt, lasse {@code executeBatch} mitten in
 * der Reihe scheitern. Das ist WIDERLEGT: {@link Abholgut} weist alles ueber 1 MB vorher
 * ab, die Spalte haelt 16 MB, die Pruefschleife faengt jede zu lange Herkunft, und eine
 * andere Zusage hat die Tabelle nicht (kein {@code UNIQUE}, kein Fremdschluessel). Es
 * gibt also keinen Weg, mitten in der Reihe zu scheitern - und darum auch keine Probe.
 * Das {@code rollback()} bleibt: Die naechste Spalte mit einer Zusage macht den Weg auf,
 * und dann waere die halb geschriebene Ladung genau die Verdopplung, gegen die die
 * Transaktion da ist.
 *
 * <p><b>Der Zeitpunkt kommt aus Java und niemals aus der Datenbank</b> - kein
 * {@code UTC_TIMESTAMP(3)}. Dieselbe Regel wie bei {@code MariaBerufe}: Zwei Zeitquellen
 * in einer Reihenfolge sind eine Reihenfolge, die sich nicht nachrechnen laesst.
 *
 * <p><b>{@link Timestamp} und nicht {@code setObject(Instant)}</b>, anders als bei
 * {@link MariaMiete}: Dort geht es um ein DATE, hier um einen Zeitpunkt. Ein
 * {@code Instant} hat keine Zone, eine {@code DATETIME}-Spalte auch nicht, und zwischen
 * beiden steht die Sitzungszone - die fest auf UTC gestellt ist. {@code Timestamp.from}
 * und {@code toInstant} rechnen ueber dieselbe Zone hin und zurueck, also kommt heraus,
 * was hineinging. Das ist der Weg, den {@code MariaBerufe} und {@code MariaKasse} schon
 * gehen; ein zweiter Weg daneben waere eine zweite Zonenrechnung.
 */
public final class MariaAbholfach implements Abholablage {

    private static final String SPALTEN =
            "id, empfaenger, herkunft, eingelegt, beschriftung, inhalt";

    /**
     * Das Fach eines Spielers, aelteste Zeile zuerst.
     *
     * <p>Das {@code ORDER BY id} steht da, obwohl {@code ix_abholfach_empfaenger} die id
     * als zweites Glied traegt und MariaDB die Zeilen deshalb schon in dieser Reihenfolge
     * liest. <b>Gemessen am 08.11.2026: Eine Einspritzung, die es wegnimmt, bleibt
     * gruen</b> - auch die Probe, in der zwei Spieler sich abwechseln. Es bleibt
     * trotzdem stehen, denn eine Reihenfolge, die nur aus dem Ausfuehrungsplan folgt, ist
     * keine Zusage: Der naechste Index oder eine Partitionierung kann sie aendern, und
     * dann leert ein Spieler sein Fach in mehreren Griffen und bekommt jedes Mal eine
     * andere Anzeige. Dasselbe gilt und dasselbe wurde gemessen bei
     * {@code MariaMiete.LESEN_ALLE}.
     */
    private static final String LESEN =
            "SELECT " + SPALTEN + " FROM abholfach WHERE empfaenger = ? ORDER BY id";

    /**
     * Zaehlen statt lesen.
     *
     * <p>Eine eigene Abfrage und nicht {@code fuerSpieler().size()}, weil sie beim
     * Beitritt jedes Spielers gestellt wird: Dabei soll nicht der ganze Inhalt - samt der
     * MEDIUMTEXT-Spalte, in der ein Megabyte stehen darf - durch die Leitung kommen, nur
     * um ihn zu zaehlen.
     */
    private static final String ZAEHLEN =
            "SELECT COUNT(*) FROM abholfach WHERE empfaenger = ?";

    private static final String EINFUEGEN = "INSERT INTO abholfach (empfaenger,"
            + " herkunft, eingelegt, beschriftung, inhalt) VALUES (?, ?, ?, ?, ?)";

    /**
     * Ein Stueck nehmen - mit dem Empfaenger in der Bedingung.
     *
     * <p>Der {@code empfaenger} ist keine Hoeflichkeit, sondern die Schranke gegen die
     * wiederverwendete Nummer (siehe den Kopf der Klasse) und gegen die geratene Zahl.
     */
    private static final String NEHMEN =
            "DELETE FROM abholfach WHERE id = ? AND empfaenger = ?";

    private final Datenbank datenbank;

    public MariaAbholfach(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    @Override
    public List<Abholstueck> fuerSpieler(UUID empfaenger) {
        pruefe(empfaenger);
        List<Abholstueck> gefunden = new ArrayList<>();
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN)) {
            anfrage.setString(1, empfaenger.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                while (ergebnis.next()) {
                    gefunden.add(ausZeile(ergebnis));
                }
            }
            return gefunden;
        } catch (SQLException fehler) {
            throw new IllegalStateException("Abholfach nicht lesbar: " + empfaenger,
                    fehler);
        }
    }

    @Override
    public int anzahlFuer(UUID empfaenger) {
        pruefe(empfaenger);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(ZAEHLEN)) {
            anfrage.setString(1, empfaenger.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                // COUNT(*) liefert immer genau eine Zeile - die Pruefung steht trotzdem
                // da, weil ein getInt auf einem leeren Ergebnis wirft und die Meldung
                // dann nicht sagt, worum es ging.
                return ergebnis.next() ? ergebnis.getInt(1) : 0;
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Abholfach nicht zaehlbar: " + empfaenger,
                    fehler);
        }
    }

    @Override
    public void lege(UUID empfaenger, String herkunft, Instant wann,
            List<Abholgut> stapel) {
        pruefe(empfaenger);
        if (stapel == null) {
            throw new IllegalArgumentException("Nichts ist keine Liste von Stapeln -"
                    + " eine LEERE Liste ist erlaubt und tut nichts");
        }
        // ZUERST alle Stuecke bauen und damit pruefen, dann schreiben. Ein Stapel, dessen
        // Beschriftung zu lang ist, soll auffallen, BEVOR die Transaktion offen ist -
        // sonst waere der haeufigste Grund fuer ein Zuruecknehmen ein Fehler, der sich
        // ohne Datenbank sehen laesst. Die Nummer 1 ist dabei ein Platzhalter: Die echte
        // kommt von AUTO_INCREMENT, und Abholstueck verlangt eine, die eine ist.
        for (Abholgut gut : stapel) {
            if (gut == null) {
                throw new IllegalArgumentException("Ein Stapel, der nichts ist, gehoert"
                        + " nicht ins Fach");
            }
            new Abholstueck(1L, empfaenger, herkunft, wann, gut);
        }
        if (stapel.isEmpty()) {
            return;
        }
        try {
            Connection c = datenbank.verbindung();
            boolean vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement anfrage = c.prepareStatement(EINFUEGEN)) {
                    for (Abholgut gut : stapel) {
                        anfrage.setString(1, empfaenger.toString());
                        anfrage.setString(2, herkunft.trim());
                        // MIT KALENDER: Die Spalte haelt UTC wie jede Zeitspalte
                        // dieses Schemas - siehe Datenbank.utcKalender().
                        anfrage.setTimestamp(3, Timestamp.from(wann),
                                Datenbank.utcKalender());
                        anfrage.setString(4, gut.beschriftung().trim());
                        anfrage.setString(5, gut.inhalt());
                        anfrage.addBatch();
                    }
                    anfrage.executeBatch();
                }
                // Wie bei MariaMiete: Diese Zeile ist nicht die tragende -
                // setAutoCommit(true) im finally committet ohnehin. Tragend ist das
                // rollback() im catch, und hier ist es NICHT nur durch Lesen gesichert:
                // Ein Stapel, dessen Inhalt die Spalte sprengt, laesst executeBatch
                // mitten in der Reihe scheitern, und ohne Zuruecknehmen stuenden die
                // ersten Stapel doppelt im Fach, sobald die Raeumung es erneut versucht.
                c.commit();
            } catch (SQLException | RuntimeException fehler) {
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
                    // Jetzt steht die Verbindung womoeglich dauerhaft auf
                    // autoCommit=false, und jeder weitere Schreibvorgang landete in einer
                    // Transaktion, die niemand committet - lieber weg damit.
                    datenbank.verwerfen();
                }
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Abholfach nicht schreibbar: " + empfaenger
                    + " (" + stapel.size() + " Stapel aus " + herkunft + ")", fehler);
        }
    }

    @Override
    public int nimm(UUID empfaenger, List<Long> ids) {
        pruefe(empfaenger);
        if (ids == null) {
            throw new IllegalArgumentException("Nichts ist keine Liste von Nummern");
        }
        for (Long id : ids) {
            if (id == null || id < 1L) {
                throw new IllegalArgumentException("\"" + id + "\" ist keine Nummer eines"
                        + " Abholstuecks - sie beginnen bei 1");
            }
        }
        if (ids.isEmpty()) {
            return 0;
        }
        // KEINE Transaktion und kein Batch, und das ist eine Entscheidung gegen das
        // Muster von lege(). Der Aufrufer gibt genau heraus, was hier geloescht wurde:
        // Bricht es nach der dritten von fuenf Zeilen ab, sind drei Stuecke weg und
        // werden drei gegeben - richtig. Ein Zuruecknehmen machte daraus "nichts
        // geloescht", waehrend der Aufrufer schon drei Nummern als erledigt gezaehlt
        // hat; ein Batch verwischt, WELCHE Zeilen weg sind. Einzeln gezaehlt ist die
        // Zahl immer die Wahrheit, und nur darauf baut die Ausgabe.
        int weg = 0;
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(NEHMEN)) {
            anfrage.setString(2, empfaenger.toString());
            for (Long id : ids) {
                anfrage.setLong(1, id);
                weg += anfrage.executeUpdate();
            }
            return weg;
        } catch (SQLException fehler) {
            throw new IllegalStateException("Abholstueck nicht loeschbar fuer "
                    + empfaenger + " (" + weg + " von " + ids.size()
                    + " waren schon weg)", fehler);
        }
    }

    private static Abholstueck ausZeile(ResultSet zeile) throws SQLException {
        return new Abholstueck(zeile.getLong("id"),
                UUID.fromString(zeile.getString("empfaenger")),
                zeile.getString("herkunft"),
                zeile.getTimestamp("eingelegt", Datenbank.utcKalender())
                        .toInstant(),
                new Abholgut(zeile.getString("beschriftung"),
                        zeile.getString("inhalt")));
    }

    private static void pruefe(UUID empfaenger) {
        if (empfaenger == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es kein Abholfach");
        }
    }
}
