package de.freimarken.daten;

import de.freimarken.kern.Rundgangsablage;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Der Rundgangsfortschritt in MariaDB - die zweite Umsetzung desselben Vertrags.
 *
 * <p>Was sie halten muss, steht in {@link Rundgangsablage} und wird von
 * {@code RundgangVertrag} geprueft, denselben Proben wie {@code SpeicherRundgang}. Hier
 * steht nur, WIE sie es haelt.
 *
 * <p><b>Eine Zeile je Spieler UND Station</b>, also {@code PRIMARY KEY (spieler,
 * station)}. Dasselbe Muster wie bei den erfuellten Herausforderungen von Wolkenwerder -
 * und aus demselben Grund: Der Hauptschluessel IST der Riegel gegen die doppelte Zeile,
 * und die Zahl der geschriebenen Zeilen ist die Antwort auf "war es neu".
 *
 * <p><b>{@code INSERT IGNORE} und keine Abfrage davor.</b> Ein Lesen und dann Schreiben
 * verlore bei zwei gleichzeitigen Schritten in dieselbe Station einen: Beide lasen "nicht
 * da", beide schrieben, und beide meldeten eine neue Station - der Spieler lase den
 * Stationstext zweimal. Das {@code IGNORE} schluckt genau einen Fehler, den
 * Hauptschluesselstoss, und das ist hier die gesuchte Antwort.
 *
 * <p><b>Null geschriebene Zeilen werden nachgeprueft.</b> {@code IGNORE} verschluckt auch
 * ANDERE Fehler - eine zu lange Zeichenkette etwa. Steht die Zeile danach nicht da, war
 * es kein Hauptschluesselstoss, sondern ein echter Fehler, und der darf nicht als "war
 * schon da" durchgehen. Dieselbe Vorsicht wie in {@code MariaWolken.erfuellen}.
 *
 * <p><b>Der Zeitpunkt kommt aus Java</b> und niemals aus {@code UTC_TIMESTAMP(3)}: Die Uhr
 * steht an einer Stelle. Gelesen wird er heute von niemandem - er steht in der Tabelle,
 * weil eine Zeile ohne Zeitpunkt dort nichts kostet und ein Betreiber eines Tages
 * nachsehen will, wann ein Spieler wo war.
 *
 * <p><b>Kein Zwischenspeicher.</b> Gelesen wird bei jedem Schritt in einen Stationsbereich
 * - das klingt nach viel, ist aber eine Abfrage auf den Hauptschluessel, und die Wache
 * fragt nur, wenn der Spieler wirklich in einem Bereich steht. Ein Zwischenspeicher waere
 * eine zweite Wahrheit darueber, wo jemand war.
 */
public final class MariaRundgang implements Rundgangsablage {

    /**
     * Die Stationen eines Spielers.
     *
     * <p>Die Spalten namentlich und nie {@code SELECT *} - sonst bricht ein spaeterer
     * Schemaschritt diesen Leser stumm.
     */
    private static final String LESEN =
            "SELECT station FROM rundgang WHERE spieler = ?";

    /** Eine gesehene Station eintragen - der Hauptschluessel ist der Riegel. */
    private static final String MERKEN =
            "INSERT IGNORE INTO rundgang (spieler, station, gesehen_am)"
                    + " VALUES (?, ?, ?)";

    /**
     * Ob die Zeile schon dasteht - nur fuer den Fall "null Zeilen geschrieben".
     *
     * <p>{@code IGNORE} verschluckt mehr als den Hauptschluesselstoss. Ohne diese Frage
     * ginge ein echter Fehler als "war schon da" durch, und der Spieler haette eine
     * Station gesehen, die nie abgelegt wurde.
     */
    private static final String STEHT_SCHON =
            "SELECT 1 FROM rundgang WHERE spieler = ? AND station = ?";

    private final Datenbank datenbank;

    public MariaRundgang(Datenbank datenbank) {
        if (datenbank == null) {
            throw new IllegalArgumentException("Ohne Datenbank gibt es keine Ablage");
        }
        this.datenbank = datenbank;
    }

    @Override
    public Set<String> gesehen(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(LESEN)) {
            anfrage.setString(1, spieler.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                Set<String> namen = new LinkedHashSet<>();
                while (ergebnis.next()) {
                    namen.add(ergebnis.getString("station"));
                }
                // Eine KOPIE heraus, wie bei der Speicherfassung: Wer die Menge
                // veraenderte, aenderte den Stand, ohne durch merke zu gehen.
                return Set.copyOf(namen);
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Rundgang nicht lesbar: " + spieler, fehler);
        }
    }

    @Override
    public boolean merke(UUID spieler, String station, Instant jetzt) {
        String name = Rundgangsablage.pruefeUndNormalisiere(spieler, station, jetzt);
        try (PreparedStatement anfrage =
                datenbank.verbindung().prepareStatement(MERKEN)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setString(2, name);
            anfrage.setObject(3, LocalDateTime.ofInstant(jetzt, ZoneOffset.UTC));
            if (anfrage.executeUpdate() == 1) {
                // Eine geschriebene Zeile heisst neu - und nur das ist eindeutig.
                return true;
            }
            if (!stehtSchon(spieler, name)) {
                throw new IllegalStateException("Station nicht schreibbar: " + spieler
                        + "/" + name + " - null Zeilen geschrieben, und die Zeile steht"
                        + " trotzdem nicht da. Dann hat IGNORE einen anderen Fehler"
                        + " verschluckt als den Hauptschluesselstoss.");
            }
            return false;
        } catch (SQLException fehler) {
            throw new IllegalStateException("Station nicht schreibbar: " + spieler + "/"
                    + name, fehler);
        }
    }

    private boolean stehtSchon(UUID spieler, String station) throws SQLException {
        try (PreparedStatement anfrage =
                datenbank.verbindung().prepareStatement(STEHT_SCHON)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setString(2, station);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next();
            }
        }
    }
}
