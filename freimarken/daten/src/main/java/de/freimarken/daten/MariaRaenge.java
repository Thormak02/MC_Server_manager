package de.freimarken.daten;

import de.freimarken.kern.Rangablage;
import de.freimarken.kern.Rangstand;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Die Raenge in MariaDB - die zweite Umsetzung desselben Vertrags.
 *
 * <p>Was sie halten muss, steht in {@link Rangablage} und wird von {@code RangVertrag}
 * geprueft, denselben Proben wie {@code SpeicherRaenge}. Hier steht nur, WIE sie es haelt.
 *
 * <p><b>Keine Zeile ist der Normalfall</b>, nicht die Ausnahme: Geschrieben wird erst beim
 * ersten Aufstieg. Ein {@code lies} auf einen Spieler ohne Zeile gibt
 * {@link Rangstand#leer} und nicht {@code null} - und was dann gilt, entscheidet nicht
 * diese Klasse, sondern {@link Rangstand#stufeIn} an der einen Stelle, die die Leiter
 * kennt.
 *
 * <p><b>Geschrieben wird gegen einen ERWARTETEN Stand</b>, und zwar in einer Transaktion,
 * die den Stand mit {@code FOR UPDATE} liest, vergleicht und nur dann schreibt. Nach dem
 * Muster von {@code MariaKasse.inTransaktion} und {@code MariaBerufe}: {@code false}
 * heisst "dieser Stand gilt nicht mehr", und ein {@code false} nimmt alles zurueck. Ohne
 * diese Sperre schrieben der Tageslauf und ein Befehl im selben Augenblick beide - und
 * der zweite ueberschriebe den ersten mit einem Rang, der nie zustand.
 *
 * <p><b>Ein {@code INSERT ... ON DUPLICATE KEY UPDATE} und kein zweiter Weg fuer den
 * ersten Aufstieg.</b> Ob eine Zeile da ist, ist in dieser Tabelle genau der Unterschied
 * zwischen "noch nie aufgestiegen" und "steht schon auf einer Stufe" - und beide Faelle
 * enden an derselben Stelle mit derselben Zeile. Zwei Anweisungen dafuer waeren zwei
 * Stellen, an denen der Zeitpunkt vergessen werden kann.
 *
 * <p><b>Der Zeitpunkt kommt aus Java</b> und niemals aus {@code UTC_TIMESTAMP(3)}: Die
 * Regel im Kern urteilt auf derselben eingespeisten Uhr, und zwei Zeitquellen in einer
 * Angabe "seit wann bist du Buerger" sind eine Angabe, die sich nicht nachrechnen laesst.
 * Gelesen und geschrieben wird mit {@code getObject(LocalDateTime.class)} und
 * {@code setObject(LocalDateTime.ofInstant(..., UTC))} - dieselbe Leseweise wie
 * {@code MariaBerufe}, keine dritte.
 *
 * <p><b>Kein Zwischenspeicher.</b> Gelesen wird einmal je Beitritt und einmal je
 * Taktschlag, und das sind bei zwanzig Spielern keine Zahlen, die eine zweite Wahrheit
 * ueber den Rang rechtfertigen. Anders als bei den Berufen, die seit Teil E an JEDEM
 * Block haengen.
 */
public final class MariaRaenge implements Rangablage {

    /**
     * Die eine Zeile eines Spielers.
     *
     * <p>Die Spalten namentlich und nie {@code SELECT *} - sonst bricht ein spaeterer
     * Schemaschritt diesen Leser stumm.
     */
    private static final String LESEN =
            "SELECT rang, erreicht_am FROM raenge WHERE spieler = ?";

    /**
     * Derselbe Satz mit Sperre - buchstaeblich derselbe, nur mit Anhang.
     *
     * <p>Zweimal geschrieben wichen die beiden eines Tages ab, und dann sperrte der
     * schreibende Weg eine andere Zeile als die, die er vergleicht.
     *
     * <p>Fuer einen Spieler ohne Zeile sperrt {@code FOR UPDATE} keine Zeile, aber die
     * LUECKE im Schluessel: Ein zweites Einfuegen fuer denselben Spieler wartet, bis diese
     * Transaktion fertig ist. Genau das ist hier verlangt - der erste Aufstieg ist der
     * Fall, in dem zwei Wege gleichzeitig schreiben wollen.
     */
    private static final String SPERREN = LESEN + " FOR UPDATE";

    /**
     * Den Rang eintragen - einfuegen oder die vorhandene Zeile fortschreiben.
     *
     * <p>Der Zeitpunkt wird zweimal gebunden statt mit {@code VALUES(erreicht_am)}
     * gelesen - dialektfrei, dieselbe Entscheidung wie in {@code MariaSpielzeit} und
     * {@code MariaBerufe}.
     */
    private static final String EINTRAGEN =
            "INSERT INTO raenge (spieler, rang, erreicht_am) VALUES (?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE rang = ?, erreicht_am = ?";

    private final Datenbank datenbank;

    public MariaRaenge(Datenbank datenbank) {
        if (datenbank == null) {
            throw new IllegalArgumentException("Ohne Datenbank gibt es keine Ablage");
        }
        this.datenbank = datenbank;
    }

    @Override
    public Rangstand lies(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(LESEN)) {
            anfrage.setString(1, spieler.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return standAus(ergebnis, spieler);
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Rang nicht lesbar: " + spieler, fehler);
        }
    }

    @Override
    public boolean setze(Antrag antrag, Rangstand erwartet) {
        Rangablage.pruefePflichten(antrag, erwartet);
        try {
            return inTransaktion(c -> {
                if (!giltNoch(c, antrag, erwartet)) {
                    return false;
                }
                LocalDateTime jetzt = utc(antrag.jetzt());
                try (PreparedStatement anfrage = c.prepareStatement(EINTRAGEN)) {
                    anfrage.setString(1, antrag.spieler().toString());
                    anfrage.setString(2, antrag.rang());
                    anfrage.setObject(3, jetzt);
                    anfrage.setString(4, antrag.rang());
                    anfrage.setObject(5, jetzt);
                    anfrage.executeUpdate();
                }
                return true;
            });
        } catch (SQLException fehler) {
            throw new IllegalStateException("Rang nicht eintragbar: " + antrag.rang()
                    + " fuer " + antrag.spieler(), fehler);
        }
    }

    /**
     * Ob der Stand, auf dem geurteilt wurde, noch gilt - gelesen MIT Sperre.
     *
     * <p>Verglichen wird der GANZE Stand und nicht nur der Rang: Stuende dort derselbe
     * Rang mit einem anderen Zeitpunkt, waere er zwischen Urteil und Schreiben neu
     * vergeben worden - und ein zweites Schreiben verschoebe die Angabe "seit wann".
     */
    private boolean giltNoch(Connection c, Antrag antrag, Rangstand erwartet)
            throws SQLException {
        try (PreparedStatement anfrage = c.prepareStatement(SPERREN)) {
            anfrage.setString(1, antrag.spieler().toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return standAus(ergebnis, antrag.spieler()).equals(erwartet);
            }
        }
    }

    /** Aus der Zeile eines Spielers sein Stand - die EINE Leseweise. */
    private static Rangstand standAus(ResultSet ergebnis, UUID spieler)
            throws SQLException {
        if (!ergebnis.next()) {
            return Rangstand.leer(spieler);
        }
        LocalDateTime erreicht = ergebnis.getObject("erreicht_am", LocalDateTime.class);
        return new Rangstand(spieler, ergebnis.getString("rang"),
                erreicht == null ? null : erreicht.toInstant(ZoneOffset.UTC));
    }

    /** Der Zeitpunkt, wie die Spalte ihn traegt: UTC und ohne Zone. */
    private static LocalDateTime utc(Instant jetzt) {
        return LocalDateTime.ofInstant(jetzt, ZoneOffset.UTC);
    }

    /** Was in einer Transaktion zu tun ist. */
    private interface Arbeit {
        boolean tue(Connection c) throws SQLException;
    }

    /**
     * Fuehrt die Arbeit in einer Transaktion aus - nach dem Muster aus
     * {@code MariaKasse.inTransaktion}.
     *
     * <p>{@code false} nimmt alles zurueck. Das ist hier genau die gebrauchte Bedeutung:
     * "der Stand passt nicht, also ist nichts passiert".
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
