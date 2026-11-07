package de.freimarken.daten;

import de.freimarken.kern.Bonusablage;
import de.freimarken.kern.Bonusstand;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Die Serien des Tagesbonus in MariaDB - eine Zeile je Spieler.
 *
 * <p>Haelt denselben {@code BonusVertrag} wie die Fassung im Arbeitsspeicher.
 *
 * <p><b>Geschrieben wird SETZEND</b>, mit {@code ON DUPLICATE KEY UPDATE}, und damit
 * anders als bei der Spielzeit. Der Grund steht in {@link Bonusablage}: Die Serie ist ein
 * ZUSTAND und keine Summe. Zwei Server, die beide "plus eins" sagten, machten aus einem
 * Tag zwei; zwei, die denselben Zustand setzen, schreiben dasselbe.
 *
 * <p><b>Hier liegt kein Geld.</b> Ob heute schon gezahlt wurde, entscheidet allein das
 * Buchungsbuch ({@code MariaKasse}). Darum braucht diese Tabelle keine Transaktion um
 * einen einzelnen Schreibzug: Geht er verloren, ist eine Serie zu kurz und kein Geld
 * entstanden.
 *
 * <p><b>Der Tag kommt aus Java und niemals aus der Datenbank</b> - kein
 * {@code CURDATE()}. Dieselbe Begruendung wie bei {@code MariaSpielzeit}: Die Sitzung
 * steht fest auf UTC, also nennte die Datenbank zwischen 00:00 und 01:00 Ortszeit noch den
 * Vortag, und die eingestellte Tagesgrenze waere stumm wirkungslos.
 *
 * <p>{@code setObject} mit {@link LocalDate} und nicht {@code java.sql.Date.valueOf}: Ein
 * {@code java.sql.Date} ist ein Millisekundenwert um Mitternacht ORTSZEIT, den der Treiber
 * in die Sitzungszone UTC umrechnet - auf einem Rechner in Berlin waere das 23:00 des
 * Vortags, und in der Spalte stuende der falsche Tag.
 */
public final class MariaBonus implements Bonusablage {

    private static final String LESEN =
            "SELECT letzter_tag, serie, laengste_serie FROM tagesbonus WHERE spieler = ?";

    private static final String SCHREIBEN =
            "INSERT INTO tagesbonus (spieler, letzter_tag, serie, laengste_serie)"
                    + " VALUES (?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE letzter_tag = ?, serie = ?,"
                    + " laengste_serie = ?";

    private final Datenbank datenbank;

    public MariaBonus(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    @Override
    public Bonusstand lies(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN)) {
            anfrage.setString(1, spieler.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                if (!ergebnis.next()) {
                    // Kein Sonderweg und kein Fehler: Jeder faengt ohne Serie an, und der
                    // erste Bonus legt die Zeile an.
                    return Bonusstand.leer(spieler);
                }
                // getObject(LocalDate.class) und nicht getDate: Dieselbe Begruendung wie
                // beim Schreiben - getDate reist durch die Sitzungszone.
                LocalDate letzterTag = ergebnis.getObject("letzter_tag", LocalDate.class);
                return new Bonusstand(spieler, letzterTag, ergebnis.getInt("serie"),
                        ergebnis.getInt("laengste_serie"));
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Tagesbonus nicht lesbar: " + spieler,
                    fehler);
        }
    }

    @Override
    public void schreibe(Bonusstand stand) {
        if (stand == null) {
            throw new IllegalArgumentException("Nichts zu schreiben ist kein Stand");
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(SCHREIBEN)) {
            anfrage.setString(1, stand.spieler().toString());
            // Die drei Werte ZWEIMAL - Platzhalter 2 bis 4 fuer eine neue Zeile, 5 bis 7
            // fuer das Ueberschreiben einer vorhandenen. Dialektfrei, aus demselben Grund
            // wie bei MariaSpielzeit: MySQL 8 hat VALUES() abgekuendigt, und die
            // ersetzende Zeilen-Benennung kennt MariaDB nicht.
            anfrage.setObject(2, stand.letzterTag());
            anfrage.setInt(3, stand.serie());
            anfrage.setInt(4, stand.laengsteSerie());
            anfrage.setObject(5, stand.letzterTag());
            anfrage.setInt(6, stand.serie());
            anfrage.setInt(7, stand.laengsteSerie());
            anfrage.executeUpdate();
        } catch (SQLException fehler) {
            throw new IllegalStateException("Tagesbonus nicht schreibbar: "
                    + stand.spieler(), fehler);
        }
    }
}
