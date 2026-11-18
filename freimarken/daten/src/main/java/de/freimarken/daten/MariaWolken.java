package de.freimarken.daten;

import de.freimarken.kern.Inselstand;
import de.freimarken.kern.Wolkenablage;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Der Zustand der Wolkeninseln in MariaDB - zwei Tabellen, eine Zeile je Insel.
 *
 * <p>Haelt denselben {@code WolkenVertrag} wie {@code SpeicherWolken}. Dass es diesen
 * Vertrag gibt, hat hier zwei handfeste Gruende: Das Erfuellen ist dort ein
 * {@code Set.add}, hier ein {@code INSERT IGNORE} mit Zeilenzaehlung - und das Lesen eines
 * Standes ist dort ein Kartengriff, hier eine Zusammenfuehrung aus ZWEI Tabellen.
 *
 * <p><b>Zwei Tabellen und nicht eine</b>: Die erfuellten Herausforderungen sind eine
 * MENGE, und fuer Mengen hat dieses Projekt dreimal eine eigene Tabelle gewaehlt
 * ({@code parzellen_mitbauer}, {@code freistadt_rechte}, {@code kistenshop_freigabe}). Eine
 * Liste in einer Spalte waere die vierte Bauart - und die erste, bei der jemand eine
 * falsche Annahme mitbringt.
 *
 * <p><b>Die Zeile entsteht beim ersten Schreiben</b> ({@code ON DUPLICATE KEY UPDATE}) und
 * nicht beim Nehmen der Insel: Ein Stand aus lauter Vorgabewerten sagt nichts, und eine
 * Tabelle mit 1.443 solchen Zeilen waere eine Tabelle, die nur Platz braucht.
 *
 * <p><b>Geschrieben wird SETZEND</b>, anders als bei {@code MariaTruhe}: Punkte und Biom
 * sind ein Zustand und keine Summe. Eine Insel hat nicht mehr Punkte, weil sie oefter
 * gezaehlt wurde.
 */
public final class MariaWolken implements Wolkenablage {

    /** Der Stand einer Insel - ohne ihre Herausforderungen, die kommen getrennt. */
    private static final String STAND =
            "SELECT biom, punkte, gezaehlt_am FROM wolkeninsel WHERE parzelle_id = ?";

    /** Die erfuellten Herausforderungen einer Insel. */
    private static final String ERFUELLT =
            "SELECT schluessel FROM wolke_herausforderung WHERE parzelle_id = ?";

    /** Jede Zeile beider Tabellen - fuer die Rangliste und die Stufen beim Start. */
    private static final String ALLE =
            "SELECT parzelle_id, biom, punkte, gezaehlt_am FROM wolkeninsel";

    private static final String ALLE_ERFUELLT =
            "SELECT parzelle_id, schluessel FROM wolke_herausforderung";

    private static final String BIOM_SETZEN =
            "INSERT INTO wolkeninsel (parzelle_id, biom) VALUES (?, ?)"
                    + " ON DUPLICATE KEY UPDATE biom = ?";

    private static final String PUNKTE_SETZEN =
            "INSERT INTO wolkeninsel (parzelle_id, punkte, gezaehlt_am)"
                    + " VALUES (?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE punkte = ?, gezaehlt_am = ?";

    /**
     * Die erfuellte Herausforderung - mit IGNORE und nicht mit einer Vorabfrage.
     *
     * <p>Der eindeutige Schluessel auf {@code (parzelle_id, schluessel)} ist der Riegel,
     * und die Zahl der geschriebenen Zeilen ist die Antwort: 1 heisst neu, 0 heisst
     * "stand schon drin". Eine Pruefung davor haette zwischen Fragen und Schreiben eine
     * Luecke - und zwei Abgaben im selben Augenblick zahlten die Belohnung zweimal.
     */
    private static final String ERFUELLEN =
            "INSERT IGNORE INTO wolke_herausforderung (parzelle_id, schluessel)"
                    + " VALUES (?, ?)";

    /**
     * Die Nachfrage im Null-Fall - und sie ist keine Vorsichtsmassnahme, sondern GEMESSEN.
     *
     * <p>{@code INSERT IGNORE} macht aus JEDEM Fehler eine Warnung, nicht nur aus dem
     * doppelten Schluessel: Eine Nummer, die es in {@code parzellen} nicht gibt, laeuft am
     * Fremdschluessel auf (Fehler 1452) - und gemeldet werden null Zeilen, genau wie bei
     * einer Herausforderung, die schon dasteht. Ohne diese Nachfrage haette eine Insel,
     * die es nicht gibt, lautlos "stand schon drin" bekommen, und die Belohnung waere
     * ausgeblieben, ohne dass irgendwo etwas aufgefallen waere.
     */
    private static final String STEHT_SCHON =
            "SELECT 1 FROM wolke_herausforderung WHERE parzelle_id = ? AND schluessel = ?";

    /**
     * Das Loeschen des Standes.
     *
     * <p>Die Herausforderungen gehen mit, und zwar ueber den Fremdschluessel
     * {@code ON DELETE CASCADE} - siehe Schemaschritt 30. Ein zweites DELETE hier waere
     * die zweite Stelle, an der diese Regel steht.
     */
    private static final String ENTFERNEN = "DELETE FROM wolkeninsel WHERE parzelle_id = ?";

    /**
     * Und das Loeschen der Herausforderungen, falls es keine Standzeile gibt.
     *
     * <p>Der Fall ist echt: Eine Insel, auf der jemand Herausforderungen erfuellt hat und
     * sonst nichts, hat Zeilen in der zweiten Tabelle - aber keine in der ersten, denn
     * das Erfuellen schreibt nur dort. Ohne diese Anweisung ueberlebte ihr Fortschritt
     * das Freigeben, und der naechste Spieler auf diesem Feld erbte die Erzausbeute eines
     * Fremden.
     */
    private static final String ENTFERNEN_ERFUELLT =
            "DELETE FROM wolke_herausforderung WHERE parzelle_id = ?";

    private final Datenbank datenbank;

    public MariaWolken(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    @Override
    public Inselstand stand(long inselId) {
        pruefe(inselId);
        try (PreparedStatement anfrage =
                datenbank.verbindung().prepareStatement(STAND)) {
            anfrage.setLong(1, inselId);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                Set<String> erfuellt = erfuellt(inselId);
                if (!ergebnis.next()) {
                    // Keine Standzeile, aber vielleicht Herausforderungen: Das Erfuellen
                    // schreibt nur in die zweite Tabelle. Ein leerer Stand mit Stufe 3
                    // ist damit ein gueltiger Zustand.
                    return new Inselstand(inselId, Inselstand.KEIN_BIOM, 0L,
                            Inselstand.NIE_GEZAEHLT, erfuellt);
                }
                return new Inselstand(inselId, ergebnis.getString("biom"),
                        ergebnis.getLong("punkte"),
                        zeitpunkt(ergebnis.getObject("gezaehlt_am",
                                LocalDateTime.class)),
                        erfuellt);
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Inselstand nicht lesbar: " + inselId,
                    fehler);
        }
    }

    @Override
    public List<Inselstand> alle() {
        // ZWEI Abfragen und keine Zusammenfuehrung in SQL: Ein LEFT JOIN gaebe je Insel
        // so viele Zeilen, wie sie Herausforderungen hat, und Biom und Punkte kaemen in
        // jeder davon mit. Zusammengesetzt wird in Java - bei hoechstens 1.443 Inseln ist
        // das eine Handvoll Kilobytes, und die Abfrage laeuft selten.
        Map<Long, Set<String>> erfuellt = alleErfuellt();
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(ALLE);
                ResultSet ergebnis = anfrage.executeQuery()) {
            Map<Long, Inselstand> staende = new LinkedHashMap<>();
            while (ergebnis.next()) {
                long id = ergebnis.getLong("parzelle_id");
                staende.put(id, new Inselstand(id, ergebnis.getString("biom"),
                        ergebnis.getLong("punkte"),
                        zeitpunkt(ergebnis.getObject("gezaehlt_am",
                                LocalDateTime.class)),
                        erfuellt.getOrDefault(id, Set.of())));
            }
            // Und die Inseln, die NUR Herausforderungen haben: Sie haben eine Stufe,
            // aber keine Standzeile - ohne sie faehlte beim Start genau die Stufe, an der
            // die Erzausbeute haengt.
            for (var zeile : erfuellt.entrySet()) {
                staende.computeIfAbsent(zeile.getKey(),
                        id -> new Inselstand(id, Inselstand.KEIN_BIOM, 0L,
                                Inselstand.NIE_GEZAEHLT, zeile.getValue()));
            }
            return List.copyOf(new ArrayList<>(staende.values()));
        } catch (SQLException fehler) {
            throw new IllegalStateException("Inselstaende nicht lesbar", fehler);
        }
    }

    @Override
    public void biomSetzen(long inselId, String biom) {
        pruefe(inselId);
        try (PreparedStatement anfrage =
                datenbank.verbindung().prepareStatement(BIOM_SETZEN)) {
            anfrage.setLong(1, inselId);
            anfrage.setString(2, biom);
            anfrage.setString(3, biom);
            anfrage.executeUpdate();
        } catch (SQLException fehler) {
            throw new IllegalStateException("Biom nicht schreibbar: " + inselId, fehler);
        }
    }

    @Override
    public void punkteSetzen(long inselId, long punkte, Instant wann) {
        pruefe(inselId);
        if (punkte < 0L) {
            throw new IllegalArgumentException("Negative Punkte gibt es nicht, nicht "
                    + punkte);
        }
        LocalDateTime zeit = wann == null ? null
                : LocalDateTime.ofInstant(wann.truncatedTo(ChronoUnit.MILLIS),
                        ZoneOffset.UTC);
        try (PreparedStatement anfrage =
                datenbank.verbindung().prepareStatement(PUNKTE_SETZEN)) {
            anfrage.setLong(1, inselId);
            anfrage.setLong(2, punkte);
            anfrage.setObject(3, zeit);
            anfrage.setLong(4, punkte);
            anfrage.setObject(5, zeit);
            anfrage.executeUpdate();
        } catch (SQLException fehler) {
            throw new IllegalStateException("Punkte nicht schreibbar: " + inselId,
                    fehler);
        }
    }

    @Override
    public boolean erfuellen(long inselId, String schluessel) {
        pruefe(inselId);
        if (schluessel == null || schluessel.isBlank()) {
            throw new IllegalArgumentException("Eine Herausforderung ohne Schluessel gibt"
                    + " es nicht");
        }
        try (PreparedStatement anfrage =
                datenbank.verbindung().prepareStatement(ERFUELLEN)) {
            anfrage.setLong(1, inselId);
            anfrage.setString(2, schluessel);
            if (anfrage.executeUpdate() == 1) {
                // Eine geschriebene Zeile heisst neu - und nur das ist eindeutig.
                return true;
            }
            if (!stehtSchon(inselId, schluessel)) {
                // Null Zeilen und die Zeile ist trotzdem nicht da: Dann hat IGNORE einen
                // anderen Fehler verschluckt - siehe STEHT_SCHON.
                throw new IllegalStateException("Herausforderung nicht schreibbar: "
                        + inselId + "/" + schluessel
                        + " - gibt es die Insel?");
            }
            return false;
        } catch (SQLException fehler) {
            throw new IllegalStateException("Herausforderung nicht schreibbar: "
                    + inselId + "/" + schluessel, fehler);
        }
    }

    @Override
    public void entfernen(long inselId) {
        pruefe(inselId);
        try (PreparedStatement erst =
                        datenbank.verbindung().prepareStatement(ENTFERNEN_ERFUELLT);
                PreparedStatement dann =
                        datenbank.verbindung().prepareStatement(ENTFERNEN)) {
            erst.setLong(1, inselId);
            erst.executeUpdate();
            dann.setLong(1, inselId);
            dann.executeUpdate();
        } catch (SQLException fehler) {
            throw new IllegalStateException("Inselstand nicht loeschbar: " + inselId,
                    fehler);
        }
    }

    /** Steht diese Herausforderung wirklich da? Siehe {@link #STEHT_SCHON}. */
    private boolean stehtSchon(long inselId, String schluessel) throws SQLException {
        try (PreparedStatement anfrage =
                datenbank.verbindung().prepareStatement(STEHT_SCHON)) {
            anfrage.setLong(1, inselId);
            anfrage.setString(2, schluessel);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next();
            }
        }
    }

    /** Die erfuellten Herausforderungen einer Insel. */
    private Set<String> erfuellt(long inselId) throws SQLException {
        try (PreparedStatement anfrage =
                datenbank.verbindung().prepareStatement(ERFUELLT)) {
            anfrage.setLong(1, inselId);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                Set<String> alle = new LinkedHashSet<>();
                while (ergebnis.next()) {
                    alle.add(ergebnis.getString(1));
                }
                return alle;
            }
        }
    }

    /** Alle erfuellten Herausforderungen, nach Insel. */
    private Map<Long, Set<String>> alleErfuellt() {
        try (PreparedStatement anfrage =
                        datenbank.verbindung().prepareStatement(ALLE_ERFUELLT);
                ResultSet ergebnis = anfrage.executeQuery()) {
            Map<Long, Set<String>> nachInsel = new LinkedHashMap<>();
            while (ergebnis.next()) {
                nachInsel.computeIfAbsent(ergebnis.getLong(1),
                        id -> new LinkedHashSet<>()).add(ergebnis.getString(2));
            }
            return nachInsel;
        } catch (SQLException fehler) {
            throw new IllegalStateException("Herausforderungen nicht lesbar", fehler);
        }
    }

    /**
     * Aus der Spalte wird ein Zeitpunkt - oder {@code null}.
     *
     * <p>UTC, ohne Ausnahme: Die Spalte steht in UTC (siehe den Kopf von
     * {@link Datenbank}), und ein {@code atZone(systemDefault())} waere ein Zeitpunkt,
     * der auf jedem Rechner ein anderer ist.
     */
    private static Instant zeitpunkt(LocalDateTime gelesen) {
        return gelesen == null ? Inselstand.NIE_GEZAEHLT
                : gelesen.toInstant(ZoneOffset.UTC);
    }

    private static void pruefe(long inselId) {
        if (inselId <= 0L) {
            throw new IllegalArgumentException("Eine Insel ohne Nummer gibt es nicht,"
                    + " nicht " + inselId);
        }
    }
}
