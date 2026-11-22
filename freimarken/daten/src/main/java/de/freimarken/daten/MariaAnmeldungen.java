package de.freimarken.daten;

import de.freimarken.kern.Anmeldeablage;
import de.freimarken.kern.Anmeldestand;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Die Annahmen in MariaDB - die zweite Umsetzung desselben Vertrags.
 *
 * <p>Was sie halten muss, steht in {@link Anmeldeablage} und wird von
 * {@code AnmeldeVertrag} geprueft, denselben Proben wie {@code SpeicherAnmeldungen}. Hier
 * steht nur, WIE sie es haelt.
 *
 * <p><b>Keine Zeile ist der Normalfall</b>, nicht die Ausnahme: Geschrieben wird erst bei
 * der Annahme. Ein {@code lies} auf einen Spieler ohne Zeile gibt
 * {@link Anmeldestand#leer} und nicht {@code null} - jeder, der zum ersten Mal ankommt,
 * ist dieser Fall.
 *
 * <p><b>Geschrieben wird in einer Transaktion, die die Zeile mit {@code FOR UPDATE}
 * liest.</b> Nicht gegen einen erwarteten Stand wie beim Rang - den gibt es hier nicht
 * (vierter Absatz im Klassenkopf des Vertrags) -, sondern um die ANTWORT richtig zu
 * bilden: {@code nimmAn} sagt, ob sich etwas geaendert hat, und diese Antwort entsteht
 * aus dem Vergleich mit dem Vorherigen. Ohne Sperre meldeten zwei gleichzeitige Annahmen
 * beide eine Aenderung, und der Spieler lase die Begruessung zweimal.
 *
 * <p><b>Der Zeitpunkt der ERSTEN Zustimmung bleibt stehen</b>, solange die Fassung
 * dieselbe ist. Darum traegt das {@code ON DUPLICATE KEY UPDATE} eine Bedingung: Es
 * schreibt nur, wenn die Fassung sich unterscheidet. Ein blindes Fortschreiben machte den
 * Nachweis bei jedem Beitritt juenger, als er ist - und ein Nachweis mit falschem Datum
 * ist keiner.
 *
 * <p><b>Der Zeitpunkt kommt aus Java</b> und niemals aus {@code UTC_TIMESTAMP(3)}: Die
 * Uhr steht an einer Stelle, und zwei Zeitquellen in einer Angabe "wann hat er
 * zugestimmt" sind eine Angabe, die sich nicht nachrechnen laesst. Gelesen und geschrieben
 * wird mit {@code getObject(LocalDateTime.class)} und
 * {@code setObject(LocalDateTime.ofInstant(..., UTC))} - dieselbe Leseweise wie
 * {@code MariaRaenge}, keine dritte.
 *
 * <p><b>Kein Zwischenspeicher.</b> Gelesen wird einmal je Beitritt - und je Klick an
 * einem Bundstand, denn dort haengt der Riegel daran. Das ist mehr als beim Rang, aber
 * immer noch eine Abfrage auf den Hauptschluessel; ein zwischengespeicherter Wert waere
 * eine zweite Wahrheit darueber, ob jemand zugestimmt hat.
 */
public final class MariaAnmeldungen implements Anmeldeablage {

    /**
     * Die eine Zeile eines Spielers.
     *
     * <p>Die Spalten namentlich und nie {@code SELECT *} - sonst bricht ein spaeterer
     * Schemaschritt diesen Leser stumm.
     */
    private static final String LESEN =
            "SELECT fassung, angenommen_am FROM anmeldungen WHERE spieler = ?";

    /**
     * Derselbe Satz mit Sperre - buchstaeblich derselbe, nur mit Anhang.
     *
     * <p>Zweimal geschrieben wichen die beiden eines Tages ab, und dann sperrte der
     * schreibende Weg eine andere Zeile als die, die er vergleicht.
     */
    private static final String SPERREN = LESEN + " FOR UPDATE";

    /**
     * Die Annahme eintragen - einfuegen oder die vorhandene Zeile fortschreiben.
     *
     * <p>Der Zeitpunkt wird zweimal gebunden statt mit {@code VALUES(angenommen_am)}
     * gelesen - dialektfrei, dieselbe Entscheidung wie in {@code MariaRaenge}.
     */
    private static final String EINTRAGEN =
            "INSERT INTO anmeldungen (spieler, fassung, angenommen_am) VALUES (?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE fassung = ?, angenommen_am = ?";

    private final Datenbank datenbank;

    public MariaAnmeldungen(Datenbank datenbank) {
        if (datenbank == null) {
            throw new IllegalArgumentException("Ohne Datenbank gibt es keine Ablage");
        }
        this.datenbank = datenbank;
    }

    @Override
    public Anmeldestand lies(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(LESEN)) {
            anfrage.setString(1, spieler.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return standAus(ergebnis, spieler);
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Anmeldung nicht lesbar: " + spieler, fehler);
        }
    }

    @Override
    public boolean nimmAn(Antrag antrag) {
        Anmeldeablage.pruefePflichten(antrag);
        try {
            return inTransaktion(c -> {
                if (schonAngenommen(c, antrag)) {
                    // Genau diese Fassung stand schon da. NICHT schreiben: Der Zeitpunkt
                    // der ersten Zustimmung bleibt stehen.
                    return false;
                }
                LocalDateTime jetzt = utc(antrag.jetzt());
                try (PreparedStatement anfrage = c.prepareStatement(EINTRAGEN)) {
                    anfrage.setString(1, antrag.spieler().toString());
                    anfrage.setString(2, antrag.fassung());
                    anfrage.setObject(3, jetzt);
                    anfrage.setString(4, antrag.fassung());
                    anfrage.setObject(5, jetzt);
                    anfrage.executeUpdate();
                }
                return true;
            });
        } catch (SQLException fehler) {
            throw new IllegalStateException("Anmeldung nicht eintragbar: "
                    + antrag.fassung() + " fuer " + antrag.spieler(), fehler);
        }
    }

    /**
     * Ob genau diese Fassung schon dasteht - gelesen MIT Sperre.
     *
     * <p>Fuer einen Spieler ohne Zeile sperrt {@code FOR UPDATE} keine Zeile, aber die
     * LUECKE im Schluessel: Ein zweites Einfuegen fuer denselben Spieler wartet, bis diese
     * Transaktion fertig ist. Genau das ist hier verlangt - die erste Annahme ist der
     * Fall, in dem zwei Wege gleichzeitig schreiben wollen (das Formular und ein zweiter
     * Klick darauf).
     */
    private boolean schonAngenommen(Connection c, Antrag antrag) throws SQLException {
        try (PreparedStatement anfrage = c.prepareStatement(SPERREN)) {
            anfrage.setString(1, antrag.spieler().toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return standAus(ergebnis, antrag.spieler())
                        .hatAngenommen(antrag.fassung());
            }
        }
    }

    /** Aus der Zeile eines Spielers sein Stand - die EINE Leseweise. */
    private static Anmeldestand standAus(ResultSet ergebnis, UUID spieler)
            throws SQLException {
        if (!ergebnis.next()) {
            return Anmeldestand.leer(spieler);
        }
        LocalDateTime wann = ergebnis.getObject("angenommen_am", LocalDateTime.class);
        return new Anmeldestand(spieler, ergebnis.getString("fassung"),
                wann == null ? null : wann.toInstant(ZoneOffset.UTC));
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
     * <p>Anders als dort nimmt ein {@code false} hier NICHT alles zurueck, sondern wird
     * ebenso bestaetigt: "diese Fassung stand schon da" ist kein Scheitern, und es gibt
     * nichts zurueckzunehmen - geschrieben wurde in diesem Zweig nichts. Ein
     * {@code rollback} waere dasselbe Ergebnis mit einer Zeile mehr; bestaetigt wird, um
     * die Lesesperre nicht laenger zu halten als nötig.
     */
    private boolean inTransaktion(Arbeit arbeit) throws SQLException {
        Connection c = datenbank.verbindung();
        boolean vorherAutomatisch = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            boolean ergebnis = arbeit.tue(c);
            c.commit();
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
