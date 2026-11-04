package de.freimarken.daten;

import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Spielzeitstand;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Die gemessene Spielzeit in MariaDB - eine Zeile je Spieler und Tag.
 *
 * <p>Haelt denselben {@code SpielzeitVertrag} wie die Fassung im Arbeitsspeicher. Das ist
 * hier wichtiger als bei Besitz und Geld, denn Spielzeit hat keinen Zeugen: Anders als beim
 * Geld, wo die Summe aller Buchungen den Stand ergeben muss, widerspricht einer verlorenen
 * oder doppelten Minute nichts. Was sich nicht nachrechnen laesst, muss an einer festen
 * Zusage haengen - und die ist aufschlagen, nie setzen.
 *
 * <p><b>Geschrieben wird ADDITIV</b>, mit {@code ON DUPLICATE KEY UPDATE sekunden =
 * sekunden + ?}, nie gesetzt und nie lesen-rechnen-schreiben. Zwei Gruende: Nur so fuellen
 * zwei Backend-Server dieselbe Zeile widerspruchsfrei - ein Lesen in Java und
 * Zurueckschreiben verschluckt den Zuwachs des anderen -, und nur so steht in der Zeile
 * immer schon verstrichene Zeit und nie ein laufender Zeitpunkt.
 *
 * <p><b>Die Kehrseite: Ein Aufruf darf nicht wiederholt werden.</b> Additiv heisst, dass
 * eine Wiederholung doppelt zaehlt. Nach einem unklaren Fehler wird deshalb nicht erneut
 * geschrieben, sondern der Verlust protokolliert - zu viel Spielzeit gibt einen Rang zu
 * frueh, zu wenig nur zu spaet. Dieses Verhalten ist in
 * {@code MariaSpielzeitTest.derselbeTaktZweimalZaehltZweimal} festgehalten, damit niemand
 * es fuer einen Fehler haelt und eine Wiederholung einbaut.
 *
 * <p><b>Die ganze Liste in EINER Transaktion</b>, nach dem Muster von
 * {@code MariaKasse.inTransaktion}: Ein Takt, der die Haelfte seiner Spieler geschrieben
 * hat und dann abbricht, waere nicht nachvollziehbar.
 *
 * <p><b>Der Tag kommt aus Java und niemals aus der Datenbank</b> - kein {@code CURDATE()},
 * kein {@code UTC_DATE()}, kein {@code NOW()}. Die Begruendung steht am Schemaschritt 9 in
 * {@link Datenbank}: Die Sitzung steht fest auf UTC, also nennte die Datenbank zwischen
 * 00:00 und 01:00 Ortszeit noch den Vortag, und die eingestellte Tagesgrenze waere stumm
 * wirkungslos.
 *
 * <p>Zurueckgelesen wird kein Datum, sondern nur zwei Zahlen. Damit entsteht hier auch
 * keine dritte Leseweise fuer Zeiten neben {@code getObject(LocalDateTime.class)} in
 * {@code MariaLager} und {@code getTimestamp(utcKalender())} in {@code MariaKasse}.
 */
public final class MariaSpielzeit implements Spielzeitablage {

    /**
     * Der additive Schreibsatz.
     *
     * <p>Die Sekunden werden ZWEIMAL gebunden - Platzhalter 3 fuer die neue Zeile,
     * Platzhalter 4 fuer den Aufschlag auf eine vorhandene - statt {@code VALUES(sekunden)}
     * zu benutzen. Das ist dialektfrei: MySQL 8 hat die Funktion {@code VALUES()} fuer
     * genau diesen Zweck abgekuendigt und durch eine Zeilen-Benennung ersetzt, MariaDB
     * kennt die Benennung nicht. Ein zweites Mal denselben Wert zu binden kostet nichts
     * und laeuft auf beiden.
     */
    private static final String FORTSCHREIBEN =
            "INSERT INTO spielzeit (spieler, tag, sekunden) VALUES (?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE sekunden = sekunden + ?";

    /**
     * Gesamtzeit und Tageszeit in EINER Abfrage.
     *
     * <p>Nicht zwei Abfragen, und zwar nicht aus Sparsamkeit: Beide Zahlen muessen aus
     * DEMSELBEN Augenblick kommen. Liefe zwischen zwei Abfragen ein Takt durch, kaeme
     * {@code heute} aus der neueren und {@code gesamt} aus der aelteren Lesung - dann waere
     * heute groesser als insgesamt, und der Konstruktor von {@link Spielzeitstand}
     * verweigert genau das. Eine Anzeige, die manchmal mit einer Ausnahme abbricht, waere
     * der Preis fuer zwei Netzrunden statt einer, und das alles im Hauptfaden.
     *
     * <p>Beide Aufsummierungen laufen ueber {@code WHERE spieler = ?}, also ueber das linke
     * Glied des Primaerschluessels - die Tabelle braucht dafuer keinen zweiten Index.
     *
     * <p>{@code CASE WHEN} ohne {@code ELSE} liefert fuer die anderen Tage NULL, und NULL
     * uebergeht SUM; das aeussere COALESCE faengt den Spieler ohne jede Zeile.
     */
    private static final String LESEN =
            "SELECT COALESCE(SUM(CASE WHEN tag = ? THEN sekunden END), 0) AS heute,"
                    + " COALESCE(SUM(sekunden), 0) AS gesamt"
                    + " FROM spielzeit WHERE spieler = ?";

    private final Datenbank datenbank;

    public MariaSpielzeit(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    @Override
    public void schreibeFort(List<Zuschlag> zuschlaege) {
        if (zuschlaege == null) {
            throw new IllegalArgumentException("Nichts zu schreiben heisst eine leere Liste,"
                    + " nicht null");
        }
        // Erst die ganze Liste pruefen, dann die Verbindung anfassen: "alles oder nichts"
        // soll nicht am Zuruecknehmen haengen, sondern daran, dass ein fehlerhafter Aufruf
        // die Datenbank gar nicht erreicht. Dieselbe Reihenfolge wie in SpeicherSpielzeit,
        // damit beide Fassungen an derselben Stelle abbrechen.
        for (Zuschlag zuschlag : zuschlaege) {
            if (zuschlag == null) {
                throw new IllegalArgumentException("Ein Zuschlag in der Liste ist null");
            }
        }
        if (zuschlaege.isEmpty()) {
            // Ein Takt, in dem niemand anwesend oder jeder untaetig war. Keine Netzrunde
            // und keine leere Transaktion - das laeuft im Hauptfaden des Spielservers.
            return;
        }
        try {
            Connection c = datenbank.verbindung();
            boolean vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement anfrage = c.prepareStatement(FORTSCHREIBEN)) {
                    for (Zuschlag zuschlag : zuschlaege) {
                        anfrage.setString(1, zuschlag.spieler().toString());
                        // setObject mit LocalDate und nicht java.sql.Date.valueOf: Ein
                        // java.sql.Date ist ein Millisekundenwert um Mitternacht ORTSZEIT,
                        // den der Treiber danach in die Sitzungszone UTC umrechnet. Auf
                        // einem Rechner in Berlin waere das 23:00 des Vortags - und in der
                        // Spalte stuende der falsche Tag. Ein LocalDate reist als Jahr,
                        // Monat, Tag und ohne jede Zone. Dieselbe Begruendung wie fuer
                        // LocalDateTime statt Timestamp in MariaLager.
                        anfrage.setObject(2, zuschlag.tag());
                        anfrage.setLong(3, zuschlag.sekunden());
                        anfrage.setLong(4, zuschlag.sekunden());
                        // Ein Stapel und eine Anweisung: Kommt derselbe Spieler und Tag
                        // zweimal in der Liste vor - ein Takt ueber Mitternacht mit dem
                        // Nachtrag eines abgemeldeten Spielers -, addiert das zweite
                        // ON DUPLICATE KEY auf das erste. Genau so ist es gewollt.
                        anfrage.addBatch();
                    }
                    anfrage.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException fehler) {
                try {
                    c.rollback();
                } catch (SQLException beimZuruecknehmen) {
                    // Darf die eigentliche Ausnahme nicht ersetzen - sonst steht im
                    // Protokoll nur noch, dass das Zuruecknehmen nicht ging, nicht warum.
                    fehler.addSuppressed(beimZuruecknehmen);
                }
                throw fehler;
            } finally {
                try {
                    c.setAutoCommit(vorherAutomatisch);
                } catch (SQLException beimZuruecksetzen) {
                    // Bliebe autoCommit auf false stehen, landete jeder weitere
                    // Schreibvorgang in einer Transaktion, die niemand mehr committet -
                    // also lieber weg damit, der naechste Zugriff baut eine frische auf.
                    datenbank.verwerfen();
                }
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Spielzeit nicht fortschreibbar ("
                    + zuschlaege.size() + " Zuschlaege, nicht geschrieben)", fehler);
        }
    }

    @Override
    public Spielzeitstand lies(UUID spieler, LocalDate heute) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        if (heute == null) {
            throw new IllegalArgumentException("Ohne Tag ist 'heute' nicht zu beantworten");
        }
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(LESEN)) {
            anfrage.setObject(1, heute);
            anfrage.setString(2, spieler.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                // Eine Aufsummierung ohne GROUP BY liefert immer genau eine Zeile, auch
                // ohne einen einzigen Treffer. Der unbekannte Spieler braucht deshalb
                // keinen Sonderweg: COALESCE macht aus seinem NULL eine 0, und daraus wird
                // derselbe Wert, den Spielzeitstand.leer liefert. Jeder faengt bei 0 an.
                ergebnis.next();
                return new Spielzeitstand(spieler, heute,
                        ergebnis.getLong("gesamt"), ergebnis.getLong("heute"));
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Spielzeit nicht lesbar: " + spieler, fehler);
        }
    }
}
