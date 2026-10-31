package de.freimarken.daten;

import de.freimarken.kern.Kasse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Die Kasse in MariaDB.
 *
 * <p>Gerechnet wird in der Datenbank, nicht in Java: Jede Buchung ist ein {@code UPDATE}
 * mit relativem Betrag und - beim Abbuchen - mit der Deckungspruefung in der
 * {@code WHERE}-Klausel. Dadurch ist eine Buchung auch dann richtig, wenn sie sich mit
 * einer anderen ueberschneidet. Wer stattdessen liest, in Java rechnet und zurueckschreibt,
 * verliert bei zwei gleichzeitigen Buchungen eine davon - heute noch nicht, weil nur ein
 * Server laeuft, aber genau dann, wenn der zweite dazukommt.
 *
 * <p>Ein Spieler ohne Zeile hat das Startguthaben. Die Zeile entsteht bei der ersten
 * Buchung, nicht beim ersten Blick - sonst legte jede Abfrage Daten an.
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
    public boolean abbuchen(UUID spieler, long betrag) {
        Kasse.pruefeBetrag(betrag);
        if (betrag == 0L) {
            // Muss immer gelingen, auch bei leerem Konto. Bewusst ohne UPDATE: Ein UPDATE,
            // das nichts aendert, meldet je nach Treibereinstellung null oder eine Zeile -
            // darauf soll sich dieses Versprechen nicht stuetzen.
            return true;
        }
        try {
            legeZeileAn(spieler);
            String sql = "UPDATE kassen SET mark = mark - ? WHERE spieler = ? AND mark >= ?";
            try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
                anfrage.setLong(1, betrag);
                anfrage.setString(2, spieler.toString());
                anfrage.setLong(3, betrag);
                return anfrage.executeUpdate() == 1;
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Abbuchen fehlgeschlagen: " + spieler, fehler);
        }
    }

    @Override
    public void gutschreiben(UUID spieler, long betrag) {
        Kasse.pruefeBetrag(betrag);
        if (betrag == 0L) {
            return;
        }
        try {
            legeZeileAn(spieler);
            String sql = "UPDATE kassen SET mark = mark + ? WHERE spieler = ?";
            try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
                anfrage.setLong(1, betrag);
                anfrage.setString(2, spieler.toString());
                anfrage.executeUpdate();
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Gutschreiben fehlgeschlagen: " + spieler, fehler);
        }
    }

    /**
     * Sorgt dafuer, dass es eine Zeile mit dem Startguthaben gibt.
     *
     * <p>{@code INSERT IGNORE} und nicht erst lesen: Zwischen Pruefen und Anlegen koennte
     * ein anderer Vorgang dieselbe Zeile anlegen. So entscheidet der Primaerschluessel.
     */
    private void legeZeileAn(UUID spieler) throws SQLException {
        String sql = "INSERT IGNORE INTO kassen (spieler, mark) VALUES (?, ?)";
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setLong(2, startguthaben);
            anfrage.executeUpdate();
        }
    }
}
