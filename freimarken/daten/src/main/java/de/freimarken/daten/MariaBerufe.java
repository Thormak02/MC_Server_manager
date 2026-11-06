package de.freimarken.daten;

import de.freimarken.kern.Berufsablage;
import de.freimarken.kern.Berufsstand;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Die Berufe in MariaDB - die zweite Umsetzung desselben Vertrags.
 *
 * <p>Was sie halten muss, steht in {@link Berufsablage} und wird von
 * {@code BerufeVertrag} geprueft, denselben Proben wie {@code SpeicherBerufe}. Hier steht
 * nur, WIE sie es haelt.
 *
 * <p><b>Ein Verlassen loescht nicht, es setzt {@code zustand = 'abgelegt'}.</b> Die Zeile
 * bleibt stehen, weil Teil G die Geschichte will - und weil der Zeitpunkt des Ablegens die
 * Sperrfrist ist und mit der Zeile verschwaende.
 *
 * <p><b>Zwei Zeitspalten, nicht eine.</b> {@code geaendert_am} traegt die juengste WAHL und
 * gibt die Reihenfolge; {@code abgelegt_am} traegt das juengste ABLEGEN und ist die Frist.
 * Mit einer Spalte fuer beides sperrte die erste Wahl einen neuen Spieler einen Tag lang
 * aus seinem zweiten Grundberuf, und ein wiedergewaehlter Beruf loeschte die Frist, die er
 * ausgeloest hat. Die Begruendung in ganzer Laenge steht am Schemaschritt 10 in
 * {@link Datenbank}.
 *
 * <p><b>Geschrieben wird gegen einen ERWARTETEN Stand</b>, und zwar in einer Transaktion,
 * die den Stand mit {@code FOR UPDATE} liest, vergleicht und nur dann schreibt. Nach dem
 * Muster von {@code MariaKasse.inTransaktion}: {@code false} heisst "dieser Stand gilt
 * nicht mehr", und ein {@code false} nimmt alles zurueck. Ohne diese Sperre ergaeben zwei
 * {@code /beruf waehlen} in derselben Sekunde drei Grundberufe - beide lesen den leeren
 * Stand, beide rechnen einen Platz frei, beide schreiben.
 *
 * <p><b>Der Zeitpunkt kommt aus Java</b> und niemals aus {@code UTC_TIMESTAMP(3)}: Die
 * Regel im Kern urteilt auf derselben eingespeisten Uhr, und zwei Zeitquellen in einer
 * Frist sind eine Frist, die sich nicht nachrechnen laesst. Gelesen und geschrieben wird
 * mit {@code getObject(LocalDateTime.class)} und
 * {@code setObject(LocalDateTime.ofInstant(..., UTC))} - dieselbe Leseweise wie
 * {@code MariaLager}, keine dritte.
 *
 * <p><b>Kein Zwischenspeicher.</b> {@code /beruf} ist kein heisser Lesepfad; mit Teil E,
 * wenn jeder Verdienst den Beruf fragt, wird das neu bedacht - und dann als eigene
 * Schicht, wie {@code ZwischenspeicherLager}, nicht als Feld hier drin.
 */
public final class MariaBerufe implements Berufsablage {

    /**
     * Alle Zeilen eines Spielers - aktive und abgelegte.
     *
     * <p>EIN Satz fuer den ganzen Stand und nicht zwei (einer fuer die aktiven Berufe,
     * einer fuer das juengste Ablegen): Zwei Abfragen koennten zwei verschiedene Staende
     * sehen, und der Stand, auf dem die Regel urteilt, waere keiner, den es je gab.
     *
     * <p>Die Spalten namentlich und nie {@code SELECT *} - sonst bricht ein spaeterer
     * Schemaschritt diesen Leser stumm.
     */
    private static final String LESEN =
            "SELECT beruf, zustand, geaendert_am, abgelegt_am FROM berufe"
                    + " WHERE spieler = ? ORDER BY geaendert_am, beruf";

    /**
     * Derselbe Satz mit Sperre - buchstaeblich derselbe, nur mit Anhang.
     *
     * <p>Zweimal geschrieben wichen die beiden eines Tages ab, und dann sperrte der
     * schreibende Weg eine andere Zeilenmenge als die, die er vergleicht.
     *
     * <p>Fuer einen Spieler ohne jede Zeile sperrt {@code FOR UPDATE} keine Zeile, aber die
     * LUECKE im Schluessel: Ein zweites Einfuegen fuer denselben Spieler wartet, bis diese
     * Transaktion fertig ist. Genau das ist hier verlangt.
     */
    private static final String SPERREN = LESEN + " FOR UPDATE";

    /**
     * Den Beruf annehmen - einfuegen oder eine abgelegte Zeile wiederbeleben.
     *
     * <p>{@code abgelegt_am} steht mit Absicht NICHT in der Fortschreibung: Die Frist, die
     * das Ablegen ausgeloest hat, darf die neue Wahl nicht loeschen. Sonst waere Aufgeben,
     * warten, wiederwaehlen der Weg um Gebuehr und Frist herum.
     *
     * <p>Der Zeitpunkt wird zweimal gebunden statt mit {@code VALUES(geaendert_am)}
     * gelesen - dialektfrei, dieselbe Entscheidung wie in {@code MariaSpielzeit}.
     */
    private static final String ANNEHMEN =
            "INSERT INTO berufe (spieler, beruf, zustand, geaendert_am)"
                    + " VALUES (?, ?, 'aktiv', ?)"
                    + " ON DUPLICATE KEY UPDATE zustand = 'aktiv', geaendert_am = ?";

    /**
     * Den Beruf ablegen - beide Zeitspalten bleiben erhalten, {@code abgelegt_am} kommt
     * hinzu.
     *
     * <p>Die Bedingung steht in der {@code WHERE}-Klausel und nicht in einem Java-{@code
     * if} zwischen zwei Abfragen: Dieselbe Entscheidung wie bei
     * {@code MariaKasse.abbuchen}, und aus demselben Grund - zwischen Pruefen und
     * Schreiben passiert sonst etwas.
     */
    private static final String ABLEGEN =
            "UPDATE berufe SET zustand = 'abgelegt', geaendert_am = ?, abgelegt_am = ?"
                    + " WHERE spieler = ? AND beruf = ? AND zustand = 'aktiv'";

    private final Datenbank datenbank;

    public MariaBerufe(Datenbank datenbank) {
        if (datenbank == null) {
            throw new IllegalArgumentException("Ohne Datenbank gibt es keine Ablage");
        }
        this.datenbank = datenbank;
    }

    @Override
    public Berufsstand lies(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(LESEN)) {
            anfrage.setString(1, spieler.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return standAus(ergebnis, spieler);
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Berufe nicht lesbar: " + spieler, fehler);
        }
    }

    @Override
    public boolean waehle(Antrag antrag, Berufsstand erwartet) {
        pflichten(antrag, erwartet);
        try {
            return inTransaktion(c -> {
                if (!giltNoch(c, antrag, erwartet)) {
                    return false;
                }
                // Haelt er ihn schon, ist nichts zu tun - und das ist KEIN Fehlschlag: Der
                // erwartete Stand gilt, die Ablage steht danach so, wie der Aufrufer es
                // wollte. Hier und nicht in ANNEHMEN, denn eine Fortschreibung schriebe
                // geaendert_am neu und verschoebe damit die Reihenfolge; die
                // Speicherfassung tut das mit putIfAbsent auch nicht.
                if (erwartet.haelt(antrag.beruf())) {
                    return true;
                }
                LocalDateTime jetzt = utc(antrag.jetzt());
                try (PreparedStatement anfrage = c.prepareStatement(ANNEHMEN)) {
                    anfrage.setString(1, antrag.spieler().toString());
                    anfrage.setString(2, antrag.beruf());
                    anfrage.setObject(3, jetzt);
                    anfrage.setObject(4, jetzt);
                    anfrage.executeUpdate();
                }
                return true;
            });
        } catch (SQLException fehler) {
            throw new IllegalStateException("Beruf nicht waehlbar: " + antrag.beruf()
                    + " fuer " + antrag.spieler(), fehler);
        }
    }

    @Override
    public boolean verlasse(Antrag antrag, Berufsstand erwartet) {
        pflichten(antrag, erwartet);
        try {
            return inTransaktion(c -> {
                if (!giltNoch(c, antrag, erwartet)) {
                    return false;
                }
                // Erst nach dem Vergleich: Ein veralteter Stand haelt den Beruf vielleicht
                // nicht mehr, und dann ist false die richtige Antwort und nicht der Wurf.
                Berufsablage.pruefeAbzulegen(antrag, erwartet);
                LocalDateTime jetzt = utc(antrag.jetzt());
                try (PreparedStatement anfrage = c.prepareStatement(ABLEGEN)) {
                    anfrage.setObject(1, jetzt);
                    anfrage.setObject(2, jetzt);
                    anfrage.setString(3, antrag.spieler().toString());
                    anfrage.setString(4, antrag.beruf());
                    if (anfrage.executeUpdate() != 1) {
                        // Unmoeglich, solange die Sperre haelt: Der Stand wurde in dieser
                        // Transaktion mit FOR UPDATE gelesen und haelt den Beruf als
                        // aktiv. Ein false waere hier eine Luege ("der Stand ist
                        // veraltet"), und der Aufrufer versuchte es endlos erneut.
                        throw new IllegalStateException("Die Zeile " + antrag.beruf()
                                + " von " + antrag.spieler() + " war in derselben"
                                + " Transaktion noch aktiv und liess sich doch nicht"
                                + " ablegen");
                    }
                }
                return true;
            });
        } catch (SQLException fehler) {
            throw new IllegalStateException("Beruf nicht ablegbar: " + antrag.beruf()
                    + " fuer " + antrag.spieler(), fehler);
        }
    }

    /**
     * Ob der Stand, auf dem geurteilt wurde, noch gilt - gelesen MIT Sperre.
     *
     * <p>Verglichen wird der GANZE Stand und nicht nur die Berufe: Ein Ablegen zwischen
     * Urteil und Schreiben aendert nur {@link Berufsstand#letztesAblegen}, und genau dann
     * darf die Sperrfrist nicht uebersprungen werden.
     */
    private boolean giltNoch(Connection c, Antrag antrag, Berufsstand erwartet)
            throws SQLException {
        try (PreparedStatement anfrage = c.prepareStatement(SPERREN)) {
            anfrage.setString(1, antrag.spieler().toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return standAus(ergebnis, antrag.spieler()).equals(erwartet);
            }
        }
    }

    /**
     * Aus den Zeilen eines Spielers seinen Stand - die EINE Leseweise.
     *
     * <p>Die aktiven Berufe in der Reihenfolge der Abfrage, und von allen Zeilen das
     * juengste {@code abgelegt_am}. Dass die Datenbank das Maximum nicht selbst bildet,
     * ist Absicht: Ein {@code MAX()} brauchte eine zweite Abfrage, und zwei Abfragen fuer
     * einen Stand koennten zwei verschiedene Staende sehen.
     */
    private static Berufsstand standAus(ResultSet ergebnis, UUID spieler)
            throws SQLException {
        List<String> aktive = new ArrayList<>();
        LocalDateTime zuletzt = null;
        while (ergebnis.next()) {
            if ("aktiv".equals(ergebnis.getString("zustand"))) {
                aktive.add(ergebnis.getString("beruf"));
            }
            LocalDateTime abgelegt = ergebnis.getObject("abgelegt_am",
                    LocalDateTime.class);
            if (abgelegt != null && (zuletzt == null || abgelegt.isAfter(zuletzt))) {
                zuletzt = abgelegt;
            }
        }
        return new Berufsstand(spieler, aktive,
                zuletzt == null ? null : zuletzt.toInstant(ZoneOffset.UTC));
    }

    /** Der Zeitpunkt, wie die Spalte ihn traegt: UTC und ohne Zone. */
    private static LocalDateTime utc(Instant jetzt) {
        return LocalDateTime.ofInstant(jetzt, ZoneOffset.UTC);
    }

    private static void pflichten(Antrag antrag, Berufsstand erwartet) {
        if (antrag == null) {
            throw new IllegalArgumentException("Ohne Antrag gibt es nichts zu schreiben");
        }
        if (erwartet == null) {
            throw new IllegalArgumentException("Ohne erwarteten Stand gaebe es nur"
                    + " lesen-rechnen-schreiben, und das verliert bei zwei gleichzeitigen"
                    + " Zuegen einen");
        }
        if (!erwartet.spieler().equals(antrag.spieler())) {
            throw new IllegalArgumentException("Der erwartete Stand gehoert "
                    + erwartet.spieler() + ", der Antrag " + antrag.spieler());
        }
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
