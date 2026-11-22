package de.freimarken.daten;

import de.freimarken.kern.Losablage;
import de.freimarken.kern.Losstand;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Die Lose der Lotterie in MariaDB - eine Zeile je Spieler und Tag.
 *
 * <p>Haelt denselben {@code LosVertrag} wie die Fassung im Arbeitsspeicher.
 *
 * <p><b>Geschrieben wird AUFSCHLAGEND</b>, mit {@code ON DUPLICATE KEY UPDATE anzahl =
 * anzahl + 1}, und damit anders als bei {@link MariaBonus}. Der Grund steht in
 * {@link Losablage}: Die Zahl ist eine ZAEHLUNG und kein Zustand. Zwei Server, die beide
 * ein Los melden, meinen zwei Lose - und es sind zwei. Setzend verschluckte eines davon,
 * und ein verschlucktes Los verbilligt das naechste.
 *
 * <p><b>Hier liegt Geld</b> - auch das anders als beim Bonus, wo das Buchungsbuch die
 * Wahrheit haelt. Diese Zahl ist die einzige Wahrheit darueber, was das naechste Los
 * kostet; darum zaehlt {@code Lotteriebuch} zuerst und zahlt danach.
 *
 * <p><b>Der Tag kommt aus Java und niemals aus der Datenbank</b> - kein {@code CURDATE()}
 * und kein {@code UTC_DATE()}. Dieselbe Begruendung wie bei {@link MariaBonus}: Die
 * Sitzung steht fest auf UTC, also nennte die Datenbank zwischen 00:00 und 01:00 Ortszeit
 * noch den Vortag, und die eingestellte Tagesgrenze waere stumm wirkungslos.
 *
 * <p>{@code setObject} mit {@link LocalDate} und nicht {@code java.sql.Date.valueOf}: Ein
 * {@code java.sql.Date} ist ein Millisekundenwert um Mitternacht ORTSZEIT, den der Treiber
 * in die Sitzungszone UTC umrechnet - auf einem Rechner in Berlin waere das 23:00 des
 * Vortags, und in der Spalte stuende der falsche Tag.
 */
public final class MariaLose implements Losablage {

    private static final String LESEN =
            "SELECT anzahl FROM lotterie_lose WHERE spieler = ? AND tag = ?";

    private static final String ZAEHLEN =
            "INSERT INTO lotterie_lose (spieler, tag, anzahl) VALUES (?, ?, 1)"
                    + " ON DUPLICATE KEY UPDATE anzahl = anzahl + 1";

    private final Datenbank datenbank;

    public MariaLose(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    @Override
    public Losstand lies(UUID spieler, LocalDate tag) {
        pruefe(spieler, tag);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setObject(2, tag);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                if (!ergebnis.next()) {
                    // Kein Sonderweg und kein Fehler: Jeder faengt jeden Tag bei null
                    // an, und das erste Los legt die Zeile an.
                    return Losstand.leer(spieler, tag);
                }
                return new Losstand(spieler, tag, ergebnis.getInt("anzahl"));
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Lose nicht lesbar: " + spieler + " am " + tag,
                    fehler);
        }
    }

    @Override
    public int zaehleEins(UUID spieler, LocalDate tag) {
        pruefe(spieler, tag);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(ZAEHLEN)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setObject(2, tag);
            anfrage.executeUpdate();
        } catch (SQLException fehler) {
            throw new IllegalStateException("Los nicht zaehlbar: " + spieler + " am " + tag,
                    fehler);
        }
        // ZWEI Anweisungen und keine Transaktion um sie, und das ist eine benannte
        // Ungenauigkeit: Zaehlen zwei Server im selben Augenblick, kann die hier
        // gelesene Zahl die des anderen mit enthalten. Die Folge ist ein Preis, der zu
        // HOCH ist, nie einer, der zu niedrig ist - und das ist die Richtung, in der
        // nichts kaputtgeht.
        //
        // Eine Transaktion waere hier kein Gewinn, sondern ein Risiko: Die Datenschicht
        // haelt EINE Verbindung, auf der MariaKasse und MariaLager autoCommit
        // umschalten. Ein weiteres Umschalten mitten in der Zugkette der Lotterie
        // committete die halbe Geldbuchung des Nachbarn.
        //
        // Der Trick mit LAST_INSERT_ID(anzahl + 1) gaebe die Zahl exakt, aber nur beim
        // UPDATE-Zweig; beim INSERT bliebe sie ungesetzt, und dann stuende hier eine
        // Zahl aus einer fremden Anweisung.
        return lies(spieler, tag).loseHeute();
    }

    private static void pruefe(UUID spieler, LocalDate tag) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Lose");
        }
        if (tag == null) {
            throw new IllegalArgumentException("Ohne Tag gibt es keine Lose - welcher Tag"
                    + " gilt, sagt Tagesgrenze.tagVon und nicht die Datenbank");
        }
    }
}
