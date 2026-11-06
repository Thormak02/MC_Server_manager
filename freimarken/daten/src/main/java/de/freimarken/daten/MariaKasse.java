package de.freimarken.daten;

import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Die Kasse in MariaDB - mit dem Buchungsbuch.
 *
 * <p>Gerechnet wird in der Datenbank, nicht in Java: Jede Buchung ist ein {@code UPDATE}
 * mit relativem Betrag und - beim Abbuchen - mit der Deckungspruefung in der
 * {@code WHERE}-Klausel. Dadurch ist eine Buchung auch dann richtig, wenn sie sich mit
 * einer anderen ueberschneidet. Wer stattdessen liest, in Java rechnet und zurueckschreibt,
 * verliert bei zwei gleichzeitigen Buchungen eine davon - heute noch nicht, weil nur ein
 * Server laeuft, aber genau dann, wenn der zweite dazukommt.
 *
 * <p><b>Geldbewegung und Buchungszeile liegen in einer Transaktion.</b> Das ist der
 * eigentliche Grund, warum das Buch hier und nicht in einer Schicht darueber gefuehrt
 * wird: Nur an der Stelle, an der geschrieben wird, lassen sich beide zusammenhalten.
 * Scheitert das {@code UPDATE} an der Deckung, wird zurueckgerollt - und dann gibt es auch
 * keine Zeile. „Nichts ist passiert" heisst damit wirklich nichts.
 *
 * <p>Der Stand nach der Buchung wird in derselben Anweisung aus der schon gesperrten
 * Kassenzeile gelesen ({@code INSERT ... SELECT}). Kein zweiter Gang nach Java, kein
 * Wettlauf zwischen Buchung und Protokoll.
 *
 * <p>Ein Spieler ohne Zeile hat das Startguthaben. Die Zeile entsteht beim ersten
 * Kontakt - durch {@link #eroeffne} beim Beitritt oder durch die erste Buchung - und
 * zusammen mit ihr die Eroeffnungsbuchung. Das ist seit dem 02.11.2026 der Punkt: Solange
 * es keine Zeile gab, kam das Guthaben bei <em>jedem Blick</em> neu aus der
 * Einstellungsdatei, und ein geaenderter Wert dort aenderte rueckwirkend das Guthaben
 * jedes Spielers, der noch nichts gekauft hatte. Mit der Eroeffnungsbuchung friert der
 * Betrag in dem Augenblick ein, in dem das Konto entsteht.
 */
public final class MariaKasse implements Kasse {

    private final Datenbank datenbank;
    private final long startguthaben;

    public MariaKasse(Datenbank datenbank, long startguthaben) {
        if (startguthaben < 0) {
            throw new IllegalArgumentException("Startguthaben darf nicht negativ sein");
        }
        this.datenbank = datenbank;
        this.startguthaben = startguthaben;
    }

    @Override
    public long guthaben(UUID spieler) {
        String sql = "SELECT mark FROM kassen WHERE spieler = ?";
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, spieler.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? ergebnis.getLong("mark") : startguthaben;
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Guthaben nicht lesbar: " + spieler, fehler);
        }
    }

    @Override
    public boolean eroeffne(UUID spieler) {
        try {
            return inTransaktion(c -> stelleKontoSicher(c, spieler));
        } catch (SQLException fehler) {
            throw new IllegalStateException("Eroeffnen fehlgeschlagen: " + spieler, fehler);
        }
    }

    /**
     * Sorgt dafuer, dass es eine Kassenzeile gibt - und ihre erste Buchung.
     *
     * <p>Wird von jeder Buchung zuerst gerufen, nicht nur von {@link #eroeffne}. Sonst
     * waere die Zusage „ein unbekannter Spieler hat das Startguthaben" wahr und
     * unbrauchbar: Man saehe das Geld und koennte es nicht ausgeben. Und die
     * Summenprobe braeche, weil der Stand das Startguthaben enthielte und das Buch nicht.
     *
     * <p>{@code INSERT IGNORE} und nicht erst lesen: Zwischen Pruefen und Anlegen koennte
     * ein anderer Vorgang dieselbe Zeile anlegen. So entscheidet der Primaerschluessel.
     *
     * @return {@code true}, wenn die Zeile jetzt entstanden ist
     */
    private boolean stelleKontoSicher(Connection c, UUID spieler) throws SQLException {
        String sql = "INSERT IGNORE INTO kassen (spieler, mark) VALUES (?, ?)";
        try (PreparedStatement anfrage = c.prepareStatement(sql)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setLong(2, startguthaben);
            if (anfrage.executeUpdate() == 0) {
                // Die Zeile gab es schon - dann gibt es auch ihre Geschichte, und eine
                // zweite Eroeffnungsbuchung waere erfundenes Geld.
                return false;
            }
        }
        schreibeBuchung(c, spieler, startguthaben, Buchungsgrund.EROEFFNUNG);
        return true;
    }

    @Override
    public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
        Kasse.pruefeBetrag(betrag);
        try {
            return inTransaktion(c -> {
                stelleKontoSicher(c, spieler);
                if (betrag > 0L) {
                    String sql = "UPDATE kassen SET mark = mark - ?"
                            + " WHERE spieler = ? AND mark >= ?";
                    try (PreparedStatement anfrage = c.prepareStatement(sql)) {
                        anfrage.setLong(1, betrag);
                        anfrage.setString(2, spieler.toString());
                        anfrage.setLong(3, betrag);
                        if (anfrage.executeUpdate() != 1) {
                            // Nicht gedeckt - oder es gibt die Zeile nicht. Beides heisst:
                            // nichts ist passiert, also auch keine Buchungszeile.
                            return false;
                        }
                    }
                }
                // Betrag 0 ueberspringt nur das UPDATE, nicht die Zeile: Die erste Parzelle
                // ist gratis, und eine Ausgabe ueber 0 Mark gehoert ins Buch. Bewusst ohne
                // Blick auf die Zahl geaenderter Zeilen - ein UPDATE, das nichts aendert,
                // meldet je nach Treibereinstellung null oder eine.
                schreibeBuchung(c, spieler, -betrag, grund);
                return true;
            });
        } catch (SQLException fehler) {
            throw new IllegalStateException("Abbuchen fehlgeschlagen: " + spieler, fehler);
        }
    }

    @Override
    public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
        Kasse.pruefeBetrag(betrag);
        try {
            inTransaktion(c -> {
                stelleKontoSicher(c, spieler);
                if (betrag > 0L) {
                    String sql = "UPDATE kassen SET mark = mark + ? WHERE spieler = ?";
                    try (PreparedStatement anfrage = c.prepareStatement(sql)) {
                        anfrage.setLong(1, betrag);
                        anfrage.setString(2, spieler.toString());
                        anfrage.executeUpdate();
                    }
                }
                schreibeBuchung(c, spieler, betrag, grund);
                return true;
            });
        } catch (SQLException fehler) {
            throw new IllegalStateException("Gutschreiben fehlgeschlagen: " + spieler, fehler);
        }
    }

    @Override
    public long verdientSeit(UUID spieler, Instant ab) {
        if (spieler == null || ab == null) {
            throw new IllegalArgumentException("Ohne Spieler und Zeitpunkt gibt es keine"
                    + " Tagessumme");
        }
        // Dieselbe Spaltenfolge wie der Verlauf: (spieler, zeitpunkt, id). Die Abfrage
        // laeuft damit ueber denselben Index, und der Grund wird erst danach gefiltert -
        // ein eigener Index fuer eine Abfrage, die einmal je Sitzung laeuft, waere derselbe
        // Fehler wie ein Einstellschluessel fuer einen Wert, den es nicht gibt.
        String sql = "SELECT COALESCE(SUM(betrag), 0) FROM buchungen"
                + " WHERE spieler = ? AND zeitpunkt >= ? AND grund = ?";
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, spieler.toString());
            // Mit Kalender in UTC: Die Spalte haelt UTC, und ohne Zone legte der Treiber
            // die der JVM darueber - dann begaenne der Tag um den Ortsversatz verschoben,
            // und der Deckel griffe eine Stunde zu frueh oder zu spaet.
            anfrage.setTimestamp(2, Timestamp.from(ab), utcKalender());
            anfrage.setString(3, Buchungsgrund.BERUF_VERDIENST.schluessel());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getLong(1);
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Tagessumme nicht lesbar: " + spieler,
                    fehler);
        }
    }

    @Override
    public List<Buchung> verlauf(UUID spieler, int hoechstens) {
        Kasse.pruefeAnzahl(hoechstens);
        // Die Sortierung folgt dem Index (spieler, zeitpunkt, id). Die id als zweites
        // Merkmal ist noetig, weil zwei Buchungen im selben Millisekundenschritt sonst in
        // beliebiger Reihenfolge kaemen - und der Verlauf waere bei jedem Blick ein anderer.
        String sql = "SELECT id, zeitpunkt, grund, betrag, stand_nachher FROM buchungen"
                + " WHERE spieler = ? ORDER BY zeitpunkt DESC, id DESC LIMIT ?";
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setInt(2, hoechstens);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                List<Buchung> zeilen = new ArrayList<>();
                while (ergebnis.next()) {
                    zeilen.add(new Buchung(
                            ergebnis.getLong("id"),
                            // Mit Kalender in UTC lesen: Die Spalte haelt UTC, und ohne
                            // Zone legte der Treiber die der JVM darueber - dann waere
                            // jeder Zeitpunkt um den Ortsversatz verschoben.
                            ergebnis.getTimestamp("zeitpunkt", utcKalender()).toInstant(),
                            spieler,
                            Buchungsgrund.ausSchluessel(ergebnis.getString("grund")),
                            ergebnis.getLong("betrag"),
                            ergebnis.getLong("stand_nachher")));
                }
                return List.copyOf(zeilen);
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Verlauf nicht lesbar: " + spieler, fehler);
        }
    }

    private static Calendar utcKalender() {
        return Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
    }

    /**
     * Schreibt die Buchungszeile und holt den Stand aus der schon gesperrten Kassenzeile.
     *
     * <p>{@code INSERT ... SELECT} und nicht erst lesen und dann schreiben: So steht in
     * {@code stand_nachher} genau der Wert, den dieselbe Transaktion gerade gesetzt hat.
     * Ein zweiter Gang nach Java waere ein Fenster, in dem eine andere Buchung dazwischen
     * kaeme.
     */
    private void schreibeBuchung(Connection c, UUID spieler, long betrag,
                                 Buchungsgrund grund) throws SQLException {
        String sql = "INSERT INTO buchungen (spieler, zeitpunkt, grund, betrag,"
                + " stand_nachher) SELECT ?, UTC_TIMESTAMP(3), ?, ?, mark FROM kassen"
                + " WHERE spieler = ?";
        try (PreparedStatement anfrage = c.prepareStatement(sql)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setString(2, grund.schluessel());
            anfrage.setLong(3, betrag);
            anfrage.setString(4, spieler.toString());
            if (anfrage.executeUpdate() != 1) {
                // Keine Kassenzeile - dann gibt es nichts, wozu diese Buchung gehoeren
                // koennte. Lauter Fehlschlag statt einer Zeile ohne Konto.
                throw new SQLException("Keine Kassenzeile fuer " + spieler
                        + " - das Konto muss vor der ersten Buchung eroeffnet sein.");
            }
        }
    }

    /** Was in einer Transaktion zu tun ist. */
    private interface Arbeit {
        boolean tue(Connection c) throws SQLException;
    }

    /**
     * Fuehrt die Arbeit in einer Transaktion aus - nach dem Muster aus {@code MariaLager}.
     *
     * <p>Ein {@code false} der Arbeit heisst „nichts ist passiert" und wird
     * zurueckgerollt. Das ist der Punkt, an dem eine ungedeckte Buchung auch keine Zeile
     * hinterlaesst.
     *
     * <p>Die Sorgfalt im {@code finally} ist kein Zierwerk: Bliebe {@code autoCommit} auf
     * {@code false} stehen, landete jeder weitere Schreibvorgang in einer Transaktion, die
     * niemand mehr committet - und verschwaende lautlos.
     */
    private boolean inTransaktion(Arbeit arbeit) throws SQLException {
        Connection c = datenbank.verbindung();
        boolean vorherAutomatisch = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            boolean ergebnis = arbeit.tue(c);
            if (ergebnis) {
                c.commit();
            } else {
                c.rollback();
            }
            return ergebnis;
        } catch (SQLException | RuntimeException fehler) {
            try {
                c.rollback();
            } catch (SQLException beimZuruecknehmen) {
                // Darf die eigentliche Ausnahme nicht ersetzen - sonst steht im Protokoll
                // nur noch, dass das Zuruecknehmen nicht ging, und nicht warum.
                fehler.addSuppressed(beimZuruecknehmen);
            }
            throw fehler;
        } finally {
            try {
                c.setAutoCommit(vorherAutomatisch);
            } catch (SQLException beimZuruecksetzen) {
                datenbank.verwerfen();
            }
        }
    }
}
