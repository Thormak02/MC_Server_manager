package de.freimarken.daten;

import de.freimarken.kern.Mietablage;
import de.freimarken.kern.Mietvertrag;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Die Mietvertraege in MariaDB - eine Zeile je Ladeneinheit.
 *
 * <p>Haelt denselben {@code MietVertrag} wie die Fassung im Arbeitsspeicher.
 *
 * <p><b>KEIN {@code ON DUPLICATE KEY UPDATE}</b>, und das ist die eine Stelle, an der
 * diese Fassung von {@code MariaBonus} und {@code MariaSpielzeit} abweicht. Der Grund ist
 * der zweite eindeutige Schluessel: {@code ON DUPLICATE KEY} feuert bei JEDEM
 * eindeutigen Schluessel, nicht nur beim Hauptschluessel. Ein Versuch, dem Mieter eine
 * zweite Einheit zu geben, verletzt {@code uq_ladenmiete_mieter} - und waere damit kein
 * Fehler, sondern eine stille Aenderung an der Zeile seiner ERSTEN Einheit. Der Spieler
 * behielte den alten Laden mit dem neuen Ablauftag, und niemand bekaeme eine Meldung.
 *
 * <p>Darum schreibt {@link #schreibe} in einer Transaktion: erst fragen, wem was gehoert,
 * dann die alte Zeile dieser Einheit raeumen, dann einfuegen. Das {@code INSERT} ist ein
 * nacktes - laeuft trotz der Abfrage noch ein zweiter Faden hinein, scheitert es hart am
 * eindeutigen Schluessel, und die Transaktion nimmt alles zurueck. Die Abfrage ist also
 * nicht die Sicherung, sondern nur die gute Meldung; die Sicherung ist die Datenbank.
 *
 * <p><b>Die Tage kommen aus Java und niemals aus der Datenbank</b> - kein
 * {@code CURDATE()}. Sonst nennte die Datenbank zwischen 00:00 und 01:00 Ortszeit noch
 * den Vortag, weil die Sitzung fest auf UTC steht, und die eingestellte Tagesgrenze waere
 * stumm wirkungslos.
 *
 * <p>{@code setObject} mit {@link LocalDate} und {@code getObject(LocalDate.class)} statt
 * {@code java.sql.Date} - <b>aber nicht aus dem Grund, der bei einem Zeitpunkt gilt.</b>
 * Gemessen am 08.11.2026 mit einer Einspritzung: Bei einer DATE-Spalte heben sich
 * {@code getDate} und {@code toLocalDate} gegenseitig auf, und keine Probe wird rot. Die
 * Zonenreise ist die Gefahr von TIMESTAMP, nicht von DATE. Was hier bleibt, ist der
 * kuerzere Weg ohne Umweg ueber eine Millisekundenzahl - und dass in der SPALTE wirklich
 * der getippte Tag steht, haelt {@code MariaMieteTest.tageStehenAlsDate} am rohen SQL
 * fest.
 */
public final class MariaMiete implements Mietablage {

    private static final String SPALTEN =
            "einheit, mieter, beginn, laeuft_bis, miete, kaution, ausfaelle";

    private static final String LESEN_EINHEIT =
            "SELECT " + SPALTEN + " FROM ladenmiete WHERE einheit = ?";

    private static final String LESEN_MIETER =
            "SELECT " + SPALTEN + " FROM ladenmiete WHERE mieter = ?";

    /**
     * Nach dem Namen der Einheit geordnet - das dritte Versprechen aus Mietablage.
     *
     * <p>Das {@code ORDER BY} ist Guertel UND Hosentraeger, und das ist gemessen: Ohne es
     * liefert InnoDB einen Full Scan in der Ordnung des Hauptschluessels, und der IST die
     * Einheit - die Reihenfolge stimmt also zufaellig. Eine Einspritzung, die es
     * wegnimmt, bleibt darum gruen (08.11.2026). Es steht trotzdem da: Ein kuenftiger
     * Index oder eine Partitionierung kann die Scanordnung aendern, und der taegliche
     * Lauf aus Teil C bucht Geld in dieser Reihenfolge ab.
     */
    private static final String LESEN_ALLE =
            "SELECT " + SPALTEN + " FROM ladenmiete ORDER BY einheit";

    /**
     * Wem gehoert schon etwas - mit {@code FOR UPDATE}.
     *
     * <p>Die Sperre ist noetig, obwohl der eindeutige Schluessel das letzte Wort hat: Ohne
     * sie saehen zwei gleichzeitige Abschluesse beide "nichts gefunden", und einer von
     * beiden bekaeme statt der Meldung eine rohe Datenbankausnahme. Mit ihr wartet der
     * zweite und liest dann, was der erste geschrieben hat.
     */
    private static final String WEM_GEHOERT =
            "SELECT einheit FROM ladenmiete WHERE mieter = ? FOR UPDATE";

    private static final String RAEUMEN = "DELETE FROM ladenmiete WHERE einheit = ?";

    private static final String EINFUEGEN =
            "INSERT INTO ladenmiete (" + SPALTEN + ") VALUES (?, ?, ?, ?, ?, ?, ?)";

    private final Datenbank datenbank;

    public MariaMiete(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    @Override
    public Mietvertrag fuerEinheit(String einheit) {
        String name = schluessel(einheit);
        return einer(LESEN_EINHEIT, name, "Mietvertrag nicht lesbar: " + name);
    }

    @Override
    public Mietvertrag fuerMieter(UUID mieter) {
        if (mieter == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        return einer(LESEN_MIETER, mieter.toString(),
                "Mietvertrag nicht lesbar: " + mieter);
    }

    @Override
    public List<Mietvertrag> alle() {
        List<Mietvertrag> gefunden = new ArrayList<>();
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN_ALLE);
             ResultSet ergebnis = anfrage.executeQuery()) {
            while (ergebnis.next()) {
                gefunden.add(ausZeile(ergebnis));
            }
            return gefunden;
        } catch (SQLException fehler) {
            throw new IllegalStateException("Mietvertraege nicht lesbar", fehler);
        }
    }

    @Override
    public void schreibe(Mietvertrag vertrag) {
        if (vertrag == null) {
            throw new IllegalArgumentException("Nichts zu schreiben ist kein Vertrag");
        }
        try {
            Connection c = datenbank.verbindung();
            boolean vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                String fremde = wemGehoert(c, vertrag.mieter());
                if (fremde != null && !fremde.equals(vertrag.einheit())) {
                    // Zurueckgenommen wird im catch darunter - geworfen wird hier, damit
                    // der Aufrufer dieselbe Meldung bekommt wie von SpeicherMiete.
                    throw new IllegalStateException("Dem Mieter gehoert schon \""
                            + fremde + "\" - ein Laden je Spieler (Plan 7.1).");
                }
                raeumen(c, vertrag.einheit());
                einfuegen(c, vertrag);
                // Das commit() steht hier, WIRKT aber nicht allein: setAutoCommit(true)
                // im finally committet eine offene Transaktion ohnehin - gemessen am
                // 08.11.2026 mit einer Einspritzung, die diese Zeile wegnahm und keine
                // Probe rot machte.
                //
                // Daraus folgt, worauf es wirklich ankommt: Das rollback() im catch ist
                // UNVERZICHTBAR. Ohne es wuerde dasselbe setAutoCommit(true) eine halb
                // erledigte Arbeit festschreiben - die Zielzeile geraeumt, die neue nicht
                // da, und der Laden eines fremden Spielers stumm frei. Diese eine Stelle
                // ist nur durch Lesen gesichert: Ein Fehlschlag ZWISCHEN raeumen und
                // einfuegen ist von aussen nicht herstellbar, weil beide Anweisungen nur
                // an Zusagen scheitern, die wemGehoert vorher prueft.
                c.commit();
            } catch (SQLException | RuntimeException fehler) {
                // rollback laeuft ueber dieselbe, vielleicht gerade gestorbene Verbindung.
                // Wirft es, darf seine Ausnahme nicht die eigentliche ersetzen - sonst ist
                // der Grund des Fehlschlags weg und im Protokoll steht nur noch, dass das
                // Zuruecknehmen nicht ging.
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
                    // autoCommit=false. Weitergegeben wuerde jeder weitere Schreibvorgang
                    // in einer Transaktion landen, die niemand mehr committet - also
                    // lieber weg damit, der naechste Zugriff baut eine frische auf.
                    datenbank.verwerfen();
                }
            }
        } catch (SQLIntegrityConstraintViolationException schonDa) {
            // Der eindeutige Schluessel hat zugeschlagen - ein zweiter Faden war zwischen
            // Abfrage und Einfuegen schneller. Genau dieselbe Ausnahme wie bei
            // SpeicherMiete, damit der Vertrag haelt.
            throw new IllegalStateException("Dem Mieter gehoert schon ein anderer Laden -"
                    + " ein Laden je Spieler (Plan 7.1).", schonDa);
        } catch (SQLException fehler) {
            throw new IllegalStateException("Mietvertrag nicht schreibbar: "
                    + vertrag.einheit(), fehler);
        }
    }

    @Override
    public boolean loesche(String einheit) {
        String name = schluessel(einheit);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(RAEUMEN)) {
            anfrage.setString(1, name);
            return anfrage.executeUpdate() > 0;
        } catch (SQLException fehler) {
            throw new IllegalStateException("Mietvertrag nicht loeschbar: " + name,
                    fehler);
        }
    }

    private Mietvertrag einer(String satz, String wert, String klage) {
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(satz)) {
            anfrage.setString(1, wert);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                // Kein Sonderweg und kein Fehler: Eine freie Einheit und ein Spieler ohne
                // Laden sind der Normalfall am ersten Tag.
                return ergebnis.next() ? ausZeile(ergebnis) : null;
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException(klage, fehler);
        }
    }

    private String wemGehoert(Connection c, UUID mieter) throws SQLException {
        try (PreparedStatement anfrage = c.prepareStatement(WEM_GEHOERT)) {
            anfrage.setString(1, mieter.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? ergebnis.getString("einheit") : null;
            }
        }
    }

    private void raeumen(Connection c, String einheit) throws SQLException {
        try (PreparedStatement anfrage = c.prepareStatement(RAEUMEN)) {
            anfrage.setString(1, einheit);
            anfrage.executeUpdate();
        }
    }

    private void einfuegen(Connection c, Mietvertrag vertrag) throws SQLException {
        try (PreparedStatement anfrage = c.prepareStatement(EINFUEGEN)) {
            anfrage.setString(1, vertrag.einheit());
            anfrage.setString(2, vertrag.mieter().toString());
            // setObject(LocalDate) und nicht java.sql.Date - siehe den Kopf der Klasse.
            anfrage.setObject(3, vertrag.beginn());
            anfrage.setObject(4, vertrag.laeuftBis());
            anfrage.setLong(5, vertrag.miete());
            anfrage.setLong(6, vertrag.kaution());
            anfrage.setInt(7, vertrag.ausfaelle());
            anfrage.executeUpdate();
        }
    }

    private static Mietvertrag ausZeile(ResultSet zeile) throws SQLException {
        // getObject(LocalDate.class) und nicht getDate: Dieselbe Begruendung wie beim
        // Schreiben - getDate reist durch die Sitzungszone.
        return new Mietvertrag(zeile.getString("einheit"),
                UUID.fromString(zeile.getString("mieter")),
                zeile.getObject("beginn", LocalDate.class),
                zeile.getObject("laeuft_bis", LocalDate.class),
                zeile.getLong("miete"),
                zeile.getLong("kaution"),
                // Die Ausfaelle MUESSEN hier stehen, und das ist der eine Grund, aus dem
                // diese Zeile gefaehrlich ist: Es gibt einen kurzen Konstruktor ohne sie,
                // und er uebersetzt sich. Wer ihn nimmt, setzt die Zahl bei jedem Lesen
                // stumm auf 0 - dann zaehlt nichts jemals hoch, und kein Laden wird je
                // geraeumt. Der taegliche Lauf laeuft, warnt jeden Tag neu und bewirkt
                // nie etwas. Dagegen steht MietVertrag.ausfaelleUeberlebenDieAblage.
                zeile.getInt("ausfaelle"));
    }

    private static String schluessel(String einheit) {
        if (einheit == null || einheit.isBlank()) {
            throw new IllegalArgumentException("Ohne Ladeneinheit gibt es keinen Vertrag");
        }
        return einheit.trim().toLowerCase(Locale.ROOT);
    }
}
