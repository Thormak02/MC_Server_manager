package de.freimarken.daten;

import de.freimarken.kern.Teamablage;
import de.freimarken.kern.Teamstand;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Die Teamrollen in MariaDB - eine Zeile je Spieler und Rolle.
 *
 * <p>Haelt denselben {@code TeamVertrag} wie die Fassung im Arbeitsspeicher.
 *
 * <p><b>Geschrieben wird mit {@code INSERT IGNORE} und NICHT mit
 * {@code ON DUPLICATE KEY UPDATE}</b> - das ist die tragende Entscheidung dieser Klasse.
 * Ein {@code UPDATE vergeben_am = ?} gaebe denselben Rueckgabewert und zerstoerte doch
 * die Spur des ersten Gebens: Die Antwort auf "seit wann hat er die Rolle" waere fuer
 * immer die Zeit des letzten Versuchs. Im Spiel faellt das nie auf, weil die Rechte
 * dieselben bleiben; auffallen wuerde es erst, wenn jemand nachfragt - und dann ist die
 * Spur weg. {@code TeamVertrag.zweitesGebenAendertNichts} prueft darum nicht den
 * Rueckgabewert, sondern was danach dasteht.
 *
 * <p><b>Die Zeit kommt aus Java und niemals aus der Datenbank</b> - kein {@code NOW()}.
 * Dieselbe Begruendung wie bei {@link MariaRaenge}: Alle Zeiten dieses Projekts sollen
 * aus EINER Uhr stammen, und die Sitzung steht auf UTC, waehrend die Regel auf der
 * eingespeisten Uhr geurteilt hat.
 *
 * <p>{@link Datenbank#utcKalender()} bei jeder Zeitspalte, wie ueberall in diesem Schema:
 * Ohne Kalender rechnet der Treiber in die Zone der JVM, und dann ist jede Tabelle fuer
 * sich stimmig, aber wer zwei nebeneinander liest, liegt um den Zonenversatz daneben.
 *
 * <p><b>Der Rollenname wird hier kleingeschrieben</b> und nicht der Datenbank
 * ueberlassen. Die Spalte ist {@code utf8mb4} mit der Vorgabekollation, und die
 * vergleicht ohne Ruecksicht auf Gross und Klein - ein {@code Moderator} fiele also schon
 * am Hauptschluessel mit {@code moderator} zusammen, GESPEICHERT wuerde aber die
 * Schreibweise des ersten Schreibzugs. Gelesen bekaeme der Kern dann ein
 * {@code "Moderator"}, und {@link Teamstand.Zeile} klagt darueber - zu Recht, denn
 * {@code Teamwerte.rolle} faende die Rolle nicht wieder. Eine Kollation zu setzen waere
 * der zweite Weg zum selben Ziel und haette bei einer bestehenden Tabelle einen
 * Schemaschritt gebraucht.
 */
public final class MariaTeam implements Teamablage {

    private static final String LESEN =
            "SELECT rolle, vergeben_am, vergeber FROM team WHERE spieler = ?"
                    + " ORDER BY rolle";

    /**
     * Die Rolle anlegen, falls sie fehlt - und die bestehende Zeile NICHT anfassen.
     *
     * <p>{@code INSERT IGNORE} und nicht {@code ON DUPLICATE KEY UPDATE}: siehe den
     * Klassenkopf. {@code IGNORE} verschluckt dabei nur die Verletzung des
     * Hauptschluessels; ein zu langer Rollenname wird im strict mode trotzdem zum Fehler,
     * und das soll er auch - er kaeme aus einer Datei, deren Grenze
     * {@code Teamwerte.HOECHSTE_SCHLUESSELLAENGE} schon haelt.
     */
    private static final String GEBEN =
            "INSERT IGNORE INTO team (spieler, rolle, vergeben_am, vergeber)"
                    + " VALUES (?, ?, ?, ?)";

    private static final String NEHMEN =
            "DELETE FROM team WHERE spieler = ? AND rolle = ?";

    /**
     * Alle Zeilen, nach Spieler und darin nach Rolle.
     *
     * <p>Die Sortierung ist eine ZUSAGE der Schnittstelle und keine Bequemlichkeit: Ohne
     * sie gaebe die Karte im Arbeitsspeicher die Reihenfolge der Vergabe zurueck und
     * diese Fassung die ihres Index, und eine Probe, die beide vergleicht, waere mal
     * gruen und mal rot. Sortiert wird nach dem TEXT der UUID, weil der geteilte Vertrag
     * das so prueft - die Spalte ist {@code CHAR(36)}, also ist das dieselbe Ordnung.
     */
    private static final String ALLE =
            "SELECT spieler, rolle, vergeben_am, vergeber FROM team"
                    + " ORDER BY spieler, rolle";

    private final Datenbank datenbank;

    public MariaTeam(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    @Override
    public Teamstand lies(UUID spieler) {
        pruefe(spieler);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN)) {
            anfrage.setString(1, spieler.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                List<Teamstand.Zeile> zeilen = new ArrayList<>();
                while (ergebnis.next()) {
                    zeilen.add(zeile(ergebnis));
                }
                // Kein Sonderweg fuer "keine Zeile": Ein Spieler ohne Rolle ist nicht im
                // Team, und das ist der Normalfall bei fast jedem Beitritt.
                return new Teamstand(spieler, zeilen);
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Teamrollen nicht lesbar: " + spieler,
                    fehler);
        }
    }

    @Override
    public boolean gib(UUID spieler, String rolle, Instant wann, UUID vonWem) {
        pruefe(spieler);
        String klein = geprueft(rolle);
        if (wann == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt gibt es keine Vergabe -"
                    + " sie kommt von aussen und nicht aus der Datenbank");
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(GEBEN)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setString(2, klein);
            anfrage.setTimestamp(3, Timestamp.from(wann), Datenbank.utcKalender());
            // setString mit null und nicht setNull: Der Treiber setzt daraus ein SQL
            // NULL, und die Spalte erlaubt es ausdruecklich - die Konsole hat keine UUID.
            anfrage.setString(4, vonWem == null ? null : vonWem.toString());
            // Eine geschriebene Zeile heisst: Die Rolle war neu. Null heisst: Er hatte
            // sie schon, und die alte Zeile steht unveraendert da.
            return anfrage.executeUpdate() > 0;
        } catch (SQLException fehler) {
            throw new IllegalStateException("Teamrolle nicht vergebbar: " + spieler
                    + " als " + klein, fehler);
        }
    }

    @Override
    public boolean nimm(UUID spieler, String rolle) {
        pruefe(spieler);
        String klein = geprueft(rolle);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(NEHMEN)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setString(2, klein);
            return anfrage.executeUpdate() > 0;
        } catch (SQLException fehler) {
            throw new IllegalStateException("Teamrolle nicht entziehbar: " + spieler
                    + " als " + klein, fehler);
        }
    }

    @Override
    public List<Teamstand> alle() {
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(ALLE);
             ResultSet ergebnis = anfrage.executeQuery()) {
            // LinkedHashMap: Die Abfrage sortiert schon, und diese Karte haelt die
            // Reihenfolge. Ein TreeMap waere eine zweite Sortierung derselben Sache und
            // damit eine zweite Wahrheit darueber, was die Zusage der Schnittstelle ist.
            Map<UUID, List<Teamstand.Zeile>> jeSpieler = new LinkedHashMap<>();
            while (ergebnis.next()) {
                UUID spieler = UUID.fromString(ergebnis.getString("spieler"));
                jeSpieler.computeIfAbsent(spieler, wer -> new ArrayList<>())
                        .add(zeile(ergebnis));
            }
            List<Teamstand> staende = new ArrayList<>(jeSpieler.size());
            for (Map.Entry<UUID, List<Teamstand.Zeile>> eintrag : jeSpieler.entrySet()) {
                staende.add(new Teamstand(eintrag.getKey(), eintrag.getValue()));
            }
            return List.copyOf(staende);
        } catch (SQLException fehler) {
            throw new IllegalStateException("Teamliste nicht lesbar", fehler);
        }
    }

    /** Eine Zeile aus dem Ergebnis - die EINE Stelle, die die Spalten kennt. */
    private static Teamstand.Zeile zeile(ResultSet ergebnis) throws SQLException {
        String vergeber = ergebnis.getString("vergeber");
        return new Teamstand.Zeile(
                ergebnis.getString("rolle"),
                ergebnis.getTimestamp("vergeben_am", Datenbank.utcKalender()).toInstant(),
                vergeber == null ? null : UUID.fromString(vergeber));
    }

    private static void pruefe(UUID spieler) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Teamrollen");
        }
    }

    /**
     * Der Rollenname, kleingeschrieben - und eine Klage, wenn er fehlt.
     *
     * <p>Dieselbe Normalisierung wie in {@code SpeicherTeam}, und sie steht in beiden
     * Fassungen, weil beide sie BRAUCHEN: Die Kollation dieser Spalte vergleicht ohnehin
     * ohne Ruecksicht auf Gross und Klein, sie SPEICHERT aber die Schreibweise des ersten
     * Schreibzugs - und die bekaeme der Kern beim Lesen zurueck. Siehe den Klassenkopf.
     */
    private static String geprueft(String rolle) {
        if (rolle == null || rolle.isBlank()) {
            throw new IllegalArgumentException("Ohne Rolle gibt es nichts zu vergeben");
        }
        return rolle.trim().toLowerCase(Locale.ROOT);
    }
}
