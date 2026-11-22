package de.freimarken.daten;

import de.freimarken.kern.Punktzeile;
import de.freimarken.kern.Wettbewerbsablage;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Der Angelwettbewerb in MariaDB - Punkte je Spieler und Lauf, Abschluss je Lauf.
 *
 * <p>Haelt denselben {@code WettbewerbVertrag} wie die Fassung im Arbeitsspeicher.
 *
 * <p><b>ZWEI RIEGEL, und beide liegen in einem {@code INSERT IGNORE}</b>, dessen
 * Zeilenzahl die Antwort ist - dasselbe Muster wie bei {@code MariaWolken} und
 * {@code MariaRundgang}:
 * <ul>
 *   <li>{@link #meldeAn} gibt {@code false}, wenn die Zeile schon stand. Erst danach
 *       bucht der Aufrufer ab - zwei Klicks kosten also nur einmal Startgeld.</li>
 *   <li>{@link #merkeAbgeschlossen} gibt {@code false}, wenn der Lauf schon abgerechnet
 *       war. Ohne ihn zahlte jeder weitere Takt denselben Topf erneut aus.</li>
 * </ul>
 * Beide sind EIN Zug und keine Lesung mit anschliessendem Schreiben: Zwei Faeden kaemen
 * sonst beide durch, und genau das ist der Fall, gegen den die Riegel stehen.
 *
 * <p><b>Der Lauf kommt aus Java und niemals aus der Datenbank</b> - kein
 * {@code CURDATE()}. Welcher Lauf gilt, entscheidet
 * {@code Wettbewerbswerte.laufAm} aus Uhrzeit, Dauer und eingestellter Zone; die
 * Datenbanksitzung steht fest auf UTC und wuesste nichts davon.
 *
 * <p>{@code setObject} mit {@link LocalDate} und nicht {@code java.sql.Date.valueOf} -
 * dieselbe Begruendung wie bei {@link MariaBonus} und {@link MariaLose}.
 */
public final class MariaWettbewerb implements Wettbewerbsablage {

    private static final String ANMELDEN =
            "INSERT IGNORE INTO wettbewerb_punkte (spieler, lauf, punkte, eingezahlt)"
                    + " VALUES (?, ?, 0, ?)";

    private static final String ZAEHLEN =
            "UPDATE wettbewerb_punkte SET punkte = LEAST(punkte + ?, 2147483647)"
                    + " WHERE spieler = ? AND lauf = ?";

    private static final String EINE_ZEILE =
            "SELECT punkte, eingezahlt FROM wettbewerb_punkte"
                    + " WHERE spieler = ? AND lauf = ?";

    private static final String ALLE_ZEILEN =
            "SELECT spieler, punkte, eingezahlt FROM wettbewerb_punkte WHERE lauf = ?";

    private static final String ABSCHLIESSEN =
            "INSERT IGNORE INTO wettbewerb_lauf (lauf, abgerechnet_am, topf, teilnehmer)"
                    + " SELECT ?, ?, COALESCE(SUM(eingezahlt), 0), COUNT(*)"
                    + " FROM wettbewerb_punkte WHERE lauf = ?";

    private static final String IST_ABGESCHLOSSEN =
            "SELECT 1 FROM wettbewerb_lauf WHERE lauf = ?";

    private final Datenbank datenbank;

    public MariaWettbewerb(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    @Override
    public boolean meldeAn(UUID spieler, LocalDate lauf, long startgeld) {
        pruefe(spieler, lauf);
        if (startgeld < 0L) {
            throw new IllegalArgumentException("Ein Startgeld kann nicht negativ sein: "
                    + startgeld);
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(ANMELDEN)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setObject(2, lauf);
            anfrage.setLong(3, startgeld);
            // DIE ZEILENZAHL IST DIE ANTWORT - siehe den Klassenkopf. 0 heisst "stand
            // schon da", und daran haengt, ob der Aufrufer abbucht.
            return anfrage.executeUpdate() > 0;
        } catch (SQLException fehler) {
            throw new IllegalStateException("Anmeldung nicht schreibbar: " + spieler
                    + " am " + lauf, fehler);
        }
    }

    @Override
    public Optional<Integer> zaehlePunkte(UUID spieler, LocalDate lauf, int punkte) {
        pruefe(spieler, lauf);
        if (punkte < 0) {
            throw new IllegalArgumentException("Ein Fang bringt keine negativen Punkte: "
                    + punkte);
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(ZAEHLEN)) {
            anfrage.setInt(1, punkte);
            anfrage.setString(2, spieler.toString());
            anfrage.setObject(3, lauf);
            // EIN UPDATE und kein INSERT: Wer keine Zeile hat, ist nicht angemeldet und
            // soll keine bekommen. Die Zeilenzahl 0 ist genau diese Antwort.
            //
            // LEAST(..., 2147483647) deckelt im SQL und nicht in Java: Ein INT, der
            // ueberlaeuft, ist in MariaDB ein Fehler oder eine stumme Kappung je nach
            // Modus - und ein negativer Punktestand macht aus dem Letzten den Ersten.
            if (anfrage.executeUpdate() == 0) {
                return Optional.empty();
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Punkte nicht schreibbar: " + spieler + " am "
                    + lauf, fehler);
        }
        return zeile(spieler, lauf).map(Punktzeile::punkte);
    }

    @Override
    public List<Punktzeile> zeilen(LocalDate lauf) {
        if (lauf == null) {
            throw new IllegalArgumentException("Ohne Lauf gibt es keine Zeilen");
        }
        List<Punktzeile> gefunden = new ArrayList<>();
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(ALLE_ZEILEN)) {
            anfrage.setObject(1, lauf);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                while (ergebnis.next()) {
                    gefunden.add(new Punktzeile(
                            UUID.fromString(ergebnis.getString("spieler")), lauf,
                            ergebnis.getInt("punkte"), ergebnis.getLong("eingezahlt")));
                }
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Zeilen nicht lesbar: " + lauf, fehler);
        }
        // KEIN ORDER BY, und das ist Absicht: Sortiert wird in
        // Wettbewerbsregel.rangliste, und zwar dort allein. Ein ORDER BY hier waere eine
        // zweite Stelle, an der ueber den Sieger entschieden wird - und bei
        // Punktgleichheit entschiede sie anders als die Regel.
        return List.copyOf(gefunden);
    }

    @Override
    public Optional<Punktzeile> zeile(UUID spieler, LocalDate lauf) {
        pruefe(spieler, lauf);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(EINE_ZEILE)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setObject(2, lauf);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                if (!ergebnis.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Punktzeile(spieler, lauf,
                        ergebnis.getInt("punkte"), ergebnis.getLong("eingezahlt")));
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Zeile nicht lesbar: " + spieler + " am "
                    + lauf, fehler);
        }
    }

    @Override
    public boolean merkeAbgeschlossen(LocalDate lauf, java.time.Instant jetzt) {
        if (lauf == null || jetzt == null) {
            throw new IllegalArgumentException("Ohne Lauf und Zeitpunkt gibt es nichts"
                    + " abzuschliessen");
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(ABSCHLIESSEN)) {
            anfrage.setObject(1, lauf);
            // UTC, wie jede Zeitspalte dieses Schemas ausser den TAGesspalten. Der
            // Zeitpunkt kommt von aussen - eine Uhr hier waere die zweite im Haus.
            anfrage.setObject(2, java.time.LocalDateTime.ofInstant(
                    jetzt, java.time.ZoneOffset.UTC));
            anfrage.setObject(3, lauf);
            // Topf und Teilnehmerzahl rechnet die Datenbank im SELBEN Zug aus - sie sind
            // nur eine Spur fuer spaeter, und zwei Anweisungen dafuer waeren zwei
            // Gelegenheiten, dass die Zahl nicht zum Lauf passt.
            return anfrage.executeUpdate() > 0;
        } catch (SQLException fehler) {
            throw new IllegalStateException("Abschluss nicht schreibbar: " + lauf,
                    fehler);
        }
    }

    @Override
    public boolean istAbgeschlossen(LocalDate lauf) {
        if (lauf == null) {
            throw new IllegalArgumentException("Ohne Lauf gibt es keine Antwort");
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(IST_ABGESCHLOSSEN)) {
            anfrage.setObject(1, lauf);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next();
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Abschluss nicht lesbar: " + lauf, fehler);
        }
    }

    private static void pruefe(UUID spieler, LocalDate lauf) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Punkte");
        }
        if (lauf == null) {
            throw new IllegalArgumentException("Ohne Lauf gibt es keine Punkte - welcher"
                    + " Lauf gilt, sagt Wettbewerbswerte.laufAm und nicht die Datenbank");
        }
    }
}
