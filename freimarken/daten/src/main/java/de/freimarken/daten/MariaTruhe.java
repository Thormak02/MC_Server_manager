package de.freimarken.daten;

import de.freimarken.kern.Truhenablage;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Die Entnahmen aus der Freien Truhe in MariaDB - eine Zeile je Spieler und Tag.
 *
 * <p>Haelt denselben {@code TruhenVertrag} wie {@code SpeicherTruhe}. Dass es diesen
 * Vertrag gibt, hat hier einen handfesten Grund: Die eine Fassung ruft
 * {@code Map.merge(.., Integer::sum)}, diese schreibt {@code ON DUPLICATE KEY UPDATE
 * stueck = stueck + ?} - und genau an so einer Stelle laufen zwei Fassungen auseinander,
 * ohne dass eine Probe rot wird.
 *
 * <p><b>Geschrieben wird AUFSCHLAGEND</b>, nie gesetzt und nie lesen-rechnen-schreiben,
 * aus denselben zwei Gruenden wie bei {@code MariaSpielzeit}: Wer dreimal am Tag an die
 * Truhe geht, soll dreimal zaehlen, und zwei Backend-Server fuellen dieselbe Zeile nur so
 * widerspruchsfrei. Ein setzendes Schreiben verloere die vorigen Griffe, und der Deckel
 * waere so oft offen, wie man die Truhe schliesst.
 *
 * <p><b>Die Kehrseite ist dieselbe:</b> Ein Aufruf darf nicht wiederholt werden, denn
 * aufschlagend heisst, dass eine Wiederholung doppelt zaehlt. Nach einem unklaren Fehler
 * wird darum nicht erneut geschrieben - die Ware ist dann schon beim Spieler, und der
 * verlorene Zaehler kostet hoechstens einen offenen Deckel. Zu viel angerechnet waere
 * schlimmer: Dann nimmt jemand nichts mehr aus einer Truhe, in die er gerade erst
 * hineingesehen hat.
 *
 * <p><b>Ein einzelner Satz je Aufruf, also keine Transaktion.</b> Anders als beim
 * Spielzeittakt, der eine ganze Liste schreibt: Hier ist jeder Aufruf ein Griff eines
 * Spielers, und ein halber Griff gibt es nicht. Eine Transaktion um eine Anweisung waere
 * zwei Netzrunden mehr im Hauptfaden des Spielservers, und zwar an einer Stelle, die beim
 * Schliessen jeder Truhe durchlaufen wird.
 *
 * <p><b>Der Tag kommt aus Java und niemals aus der Datenbank</b> - kein {@code CURDATE()}.
 * Die Begruendung steht am Schemaschritt 9 in {@link Datenbank}: Die Sitzung steht fest
 * auf UTC, also nennte die Datenbank zwischen 00:00 und 01:00 Ortszeit noch den Vortag,
 * und die eingestellte Tagesgrenze waere stumm wirkungslos.
 */
public final class MariaTruhe implements Truhenablage {

    /**
     * Der aufschlagende Schreibsatz.
     *
     * <p>Die Stueck werden ZWEIMAL gebunden - Platzhalter 3 fuer die neue Zeile,
     * Platzhalter 4 fuer den Aufschlag auf eine vorhandene - statt {@code VALUES(stueck)}
     * zu benutzen. Dieselbe Begruendung wie bei {@code MariaSpielzeit}: MySQL 8 hat
     * {@code VALUES()} abgekuendigt und durch eine Zeilen-Benennung ersetzt, MariaDB kennt
     * die Benennung nicht. Ein zweites Mal denselben Wert zu binden kostet nichts und
     * laeuft auf beiden.
     */
    private static final String AUFSCHLAGEN =
            "INSERT INTO freie_truhe (spieler, tag, stueck) VALUES (?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE stueck = stueck + ?";

    /**
     * Was dieser Spieler an diesem Tag behalten hat.
     *
     * <p>Ueber den ganzen Hauptschluessel, also hoechstens eine Zeile - und darum ohne
     * SUM: Eine Aufsummierung verdeckte, wenn der Schluessel je zerbricht, und eine
     * zweite Zeile zum selben Tag waere ein Deckel, der doppelt so weit offen steht.
     */
    private static final String BEHALTEN =
            "SELECT stueck FROM freie_truhe WHERE spieler = ? AND tag = ?";

    /**
     * Die Zeilen von vor diesem Tag.
     *
     * <p>{@code <} und nicht {@code <=}: Die Grenze ist AUSSCHLIESSEND. Ein
     * einschliessendes Loeschen raeumte den laufenden Tag weg und oeffnete jedem den
     * Deckel genau in dem Augenblick, in dem der taegliche Lauf die Truhe wieder fuellt.
     *
     * <p>Laeuft ueber den Index {@code ix_freie_truhe_tag}; ohne ihn liefe jeder
     * Tageswechsel ueber die ganze Tabelle, im Hauptfaden, auf der einen Verbindung.
     */
    private static final String RAEUMEN = "DELETE FROM freie_truhe WHERE tag < ?";

    private final Datenbank datenbank;

    public MariaTruhe(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    @Override
    public int behalten(UUID spieler, LocalDate tag) {
        pruefe(spieler, tag);
        try (PreparedStatement anfrage =
                datenbank.verbindung().prepareStatement(BEHALTEN)) {
            anfrage.setString(1, spieler.toString());
            // setObject mit LocalDate und nicht java.sql.Date.valueOf: Ein java.sql.Date
            // ist ein Millisekundenwert um Mitternacht ORTSZEIT, den der Treiber in die
            // Sitzungszone UTC umrechnet - auf einem Rechner in Berlin waere das 23:00 des
            // Vortags, und in der Spalte stuende der falsche Tag. Dieselbe Begruendung wie
            // in MariaSpielzeit.
            anfrage.setObject(2, tag);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                // Keine Zeile heisst 0 und nicht "unbekannt": Jeder faengt mit leerem Tag
                // an, und der erste Griff legt die Zeile an.
                return ergebnis.next() ? ergebnis.getInt(1) : 0;
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Entnahmen nicht lesbar: " + spieler + " am "
                    + tag, fehler);
        }
    }

    @Override
    public void merke(UUID spieler, LocalDate tag, int stueck) {
        pruefe(spieler, tag);
        if (stueck < 1) {
            throw new IllegalArgumentException("Ein Aufschlag von " + stueck
                    + " Stueck ist keiner - angerechnet wird nur, was er BEHAELT, und das"
                    + " ist mindestens eines");
        }
        try (PreparedStatement anfrage =
                datenbank.verbindung().prepareStatement(AUFSCHLAGEN)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setObject(2, tag);
            anfrage.setInt(3, stueck);
            anfrage.setInt(4, stueck);
            anfrage.executeUpdate();
        } catch (SQLException fehler) {
            throw new IllegalStateException("Entnahme nicht schreibbar: " + stueck
                    + " Stueck fuer " + spieler + " am " + tag, fehler);
        }
    }

    @Override
    public int raeume(LocalDate vorTag) {
        if (vorTag == null) {
            throw new IllegalArgumentException("Ohne Tag gibt es keine Grenze");
        }
        try (PreparedStatement anfrage =
                datenbank.verbindung().prepareStatement(RAEUMEN)) {
            anfrage.setObject(1, vorTag);
            return anfrage.executeUpdate();
        } catch (SQLException fehler) {
            throw new IllegalStateException("Entnahmen vor " + vorTag
                    + " nicht raeumbar", fehler);
        }
    }

    private static void pruefe(UUID spieler, LocalDate tag) {
        if (spieler == null || tag == null) {
            throw new IllegalArgumentException("Ohne Spieler und Tag gibt es keine Zeile");
        }
    }
}
