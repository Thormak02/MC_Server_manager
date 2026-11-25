package de.freimarken.daten;

import de.freimarken.kern.Meldeablage;
import de.freimarken.kern.Meldestand;
import de.freimarken.kern.Meldung;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Die Meldungen in MariaDB (Plan 12c.3, Schritt 16a Teil C).
 *
 * <p>Haelt denselben {@code MeldeVertrag} wie die Fassung im Arbeitsspeicher.
 *
 * <p><b>Die NUMMER kommt aus {@code AUTO_INCREMENT} und wird mit
 * {@link Statement#getGeneratedKeys()} gelesen</b> - das ist die einzige Stelle dieses
 * Schemas, die so arbeitet. Der Grund ist die Zusage der Schnittstelle:
 * {@code lege} gibt die FERTIGE Meldung zurueck. Ohne den generierten Schluessel muesste
 * der Aufrufer danach suchen, und bei zwei Meldungen desselben Melders in derselben
 * Millisekunde griffe er zur falschen.
 *
 * <p><b>Der Riegel gegen zwei Moderatoren steht in der WHERE-Bedingung</b>, nicht in
 * Java: {@code UPDATE ... WHERE nummer = ? AND stand = ?}. Damit entscheidet die
 * Datenbank, wer zuerst war, und zwischen Lesen und Schreiben liegt kein Augenblick, in
 * dem ein zweiter durchkaeme. Eine Pruefung in Java waere genau die Luecke, die der
 * Vertrag abfragt - und sie fiele nur auf, wenn zwei Moderatoren gleichzeitig arbeiten,
 * also im Ernstfall.
 *
 * <p><b>Die Zeit kommt aus Java und niemals aus der Datenbank</b> - kein {@code NOW()}.
 * Dieselbe Begruendung wie bei {@link MariaTeam}: Alle Zeiten dieses Projekts sollen aus
 * EINER Uhr stammen, und die Sitzung steht auf UTC, waehrend die Regel auf der
 * eingespeisten Uhr geurteilt hat.
 *
 * <p>{@link Datenbank#utcKalender()} bei jeder Zeitspalte, wie ueberall in diesem Schema.
 */
public final class MariaMeldungen implements Meldeablage {

    /** Die Spalten in der Reihenfolge, in der {@link #ausZeile} sie liest. */
    private static final String SPALTEN =
            "nummer, melder, betroffener, grund, welt, x, y, z, gemeldet_am, stand,"
                    + " bearbeiter, vermerk, erledigt_am, gelesen";

    private static final String LEGEN =
            "INSERT INTO meldungen (melder, betroffener, grund, welt, x, y, z,"
                    + " gemeldet_am, stand, bearbeiter, vermerk, erledigt_am, gelesen)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, '', NULL, 0)";

    private static final String LESEN =
            "SELECT " + SPALTEN + " FROM meldungen WHERE nummer = ?";

    /**
     * Schreibt nur, wenn der Stand noch der erwartete ist - DER RIEGEL.
     *
     * <p>Die Bedingung auf {@code stand} ist die ganze Zusage: Zwei Moderatoren, die
     * dieselbe Meldung im selben Augenblick annehmen, lesen beide {@code OFFEN}; der
     * zweite {@code UPDATE} trifft dann keine Zeile und gibt 0 zurueck. Ohne sie
     * gewinnen beide, beide lesen "genommen", und beide sprechen denselben Spieler an.
     */
    private static final String SCHREIBEN =
            "UPDATE meldungen SET stand = ?, bearbeiter = ?, vermerk = ?,"
                    + " erledigt_am = ?, gelesen = ? WHERE nummer = ? AND stand = ?";

    /** Nur zur Unterscheidung "gibt es nicht" von "Stand passt nicht". */
    private static final String ZAEHLE_NUMMER =
            "SELECT COUNT(*) FROM meldungen WHERE nummer = ?";

    /**
     * Die unerledigten, die aeltesten zuerst.
     *
     * <p>{@code gemeldet_am} UND {@code nummer} im ORDER BY: Zwei Meldungen in derselben
     * Millisekunde gibt es, und ohne den zweiten Schluessel waere ihre Reihenfolge die
     * des Index - also zwischen zwei Fassungen verschieden. Der geteilte Vertrag
     * vergleicht beide.
     */
    private static final String OFFENE =
            "SELECT " + SPALTEN + " FROM meldungen WHERE stand IN ('OFFEN', 'IN_ARBEIT')"
                    + " ORDER BY gemeldet_am, nummer LIMIT ?";

    private static final String OFFENE_VON =
            "SELECT COUNT(*) FROM meldungen WHERE melder = ?"
                    + " AND stand IN ('OFFEN', 'IN_ARBEIT')";

    private static final String LETZTE_VON =
            "SELECT MAX(gemeldet_am) FROM meldungen WHERE melder = ?";

    private static final String UNGELESENE_VON =
            "SELECT " + SPALTEN + " FROM meldungen WHERE melder = ? AND gelesen = 0"
                    + " AND stand IN ('ABGESCHLOSSEN', 'ABGEWIESEN')"
                    + " ORDER BY gemeldet_am, nummer";

    private final Datenbank datenbank;

    public MariaMeldungen(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    @Override
    public Meldung lege(UUID melder, UUID betroffener, String grund, String welt,
                        int x, int y, int z, Instant jetzt) {
        // GEPRUEFT WIRD IM KERN, und zwar VOR dem Schreiben: Meldung.neu wirft bei
        // Selbstmeldung, zu kurzem Grund, Steuerzeichen und fehlender Welt. Die Prueffolge
        // laeuft hier ohne Nummer durch - 1L ist ein Platzhalter, den die Datenbank
        // gleich ersetzt. Ohne diesen Schritt kaeme ein zu langer Grund erst im strict
        // mode von MariaDB an, und die Klage waere englisch und ohne Zeilennummer.
        Meldung geprueft = Meldung.neu(1L, melder, betroffener, grund, welt, x, y, z,
                jetzt);
        try (PreparedStatement anfrage = datenbank.verbindung()
                .prepareStatement(LEGEN, Statement.RETURN_GENERATED_KEYS)) {
            anfrage.setString(1, geprueft.melder().toString());
            anfrage.setString(2, geprueft.betroffener().toString());
            anfrage.setString(3, geprueft.grund());
            anfrage.setString(4, geprueft.welt());
            anfrage.setInt(5, geprueft.x());
            anfrage.setInt(6, geprueft.y());
            anfrage.setInt(7, geprueft.z());
            anfrage.setTimestamp(8, Timestamp.from(geprueft.gemeldetAm()),
                    Datenbank.utcKalender());
            anfrage.setString(9, Meldestand.OFFEN.name());
            anfrage.executeUpdate();
            try (ResultSet schluessel = anfrage.getGeneratedKeys()) {
                if (!schluessel.next()) {
                    throw new IllegalStateException("Die Meldung wurde geschrieben, aber"
                            + " die Datenbank nennt ihre Nummer nicht. Ohne Nummer ist"
                            + " sie fuer /meldung unerreichbar - der Melder wartet dann"
                            + " auf eine Antwort, die niemand geben kann");
                }
                long nummer = schluessel.getLong(1);
                // NEU GEBAUT mit der echten Nummer und nicht nachgelesen: Ein zweiter
                // Lesezugriff waere bei zwei Meldungen in derselben Millisekunde der
                // Griff zur falschen Zeile, und genau dagegen gibt lege() die fertige
                // Meldung zurueck.
                return Meldung.neu(nummer, geprueft.melder(), geprueft.betroffener(),
                        geprueft.grund(), geprueft.welt(), geprueft.x(), geprueft.y(),
                        geprueft.z(), geprueft.gemeldetAm());
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Meldung nicht schreibbar: " + melder
                    + " meldet " + betroffener, fehler);
        }
    }

    @Override
    public Optional<Meldung> lies(long nummer) {
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN)) {
            anfrage.setLong(1, nummer);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                // Kein Sonderweg fuer "gibt es nicht": Ein Moderator vertippt sich in
                // der Nummer, und das ist eine Antwort, kein Absturz.
                return ergebnis.next() ? Optional.of(ausZeile(ergebnis))
                        : Optional.empty();
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Meldung nicht lesbar: " + nummer, fehler);
        }
    }

    @Override
    public boolean schreibe(Meldung geaendert, Meldestand erwartet) {
        if (geaendert == null) {
            throw new IllegalArgumentException("Nichts zu schreiben");
        }
        if (erwartet == null) {
            throw new IllegalArgumentException("Ohne erwarteten Stand gibt es kein"
                    + " Schreiben - sonst gewinnen zwei Moderatoren dieselbe Meldung");
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(SCHREIBEN)) {
            anfrage.setString(1, geaendert.stand().name());
            anfrage.setString(2, geaendert.wirdBearbeitetVon().map(UUID::toString)
                    .orElse(null));
            anfrage.setString(3, geaendert.vermerk());
            // setNull UND NICHT setTimestamp(i, null, kalender) - GEMESSEN am
            // 25.11.2026: Jener Aufruf wirft eine NullPointerException "date must not be
            // null", weil der Treiber den Kalender mit dem Wert fuettert
            // (Calendar.setTime(null)). Die drei-Argument-Form von setTimestamp vertraegt
            // kein null, die zwei-Argument-Form schon - und genau diese Stelle trifft
            // JEDE offene Meldung, denn eine offene hat keinen Erledigungszeitpunkt.
            // Aufgefallen ist es erst gegen die echte Datenbank; die Fassung im
            // Arbeitsspeicher kennt den Fall nicht.
            if (geaendert.erledigt().isPresent()) {
                anfrage.setTimestamp(4, Timestamp.from(geaendert.erledigt().get()),
                        Datenbank.utcKalender());
            } else {
                anfrage.setNull(4, java.sql.Types.TIMESTAMP);
            }
            anfrage.setBoolean(5, geaendert.gelesen());
            anfrage.setLong(6, geaendert.nummer());
            anfrage.setString(7, erwartet.name());
            if (anfrage.executeUpdate() > 0) {
                return true;
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Meldung nicht schreibbar: "
                    + geaendert.nummer(), fehler);
        }
        // KEINE Zeile getroffen - zwei Gruende, und sie sind verschieden zu beantworten:
        // Die Nummer gibt es nicht (Rechenfehler im Aufrufer, also ein Wurf), oder der
        // Stand hat sich geaendert (ein anderer war schneller, also false). Gezaehlt
        // wird erst JETZT, damit der haeufige Fall ohne zweite Abfrage auskommt.
        if (!gibtEs(geaendert.nummer())) {
            throw new IllegalArgumentException("Die Meldung " + geaendert.nummer()
                    + " gibt es nicht");
        }
        return false;
    }

    @Override
    public List<Meldung> offene(int hoechstens) {
        if (hoechstens < 1) {
            throw new IllegalArgumentException("Eine Liste von " + hoechstens
                    + " Zeilen ist keine Liste");
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(OFFENE)) {
            anfrage.setInt(1, hoechstens);
            return alleZeilen(anfrage);
        } catch (SQLException fehler) {
            throw new IllegalStateException("Die offenen Meldungen sind nicht lesbar",
                    fehler);
        }
    }

    @Override
    public int offeneVon(UUID melder) {
        pruefe(melder);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(OFFENE_VON)) {
            anfrage.setString(1, melder.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getInt(1);
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Die offenen Meldungen von " + melder
                    + " sind nicht zaehlbar", fehler);
        }
    }

    @Override
    public Optional<Instant> letzteVon(UUID melder) {
        pruefe(melder);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LETZTE_VON)) {
            anfrage.setString(1, melder.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                ergebnis.next();
                // MAX() ueber eine leere Menge gibt eine Zeile mit NULL - nicht keine
                // Zeile. Wer das verwechselt, liest hier eine Ausnahme statt "hat noch
                // nie gemeldet", und dann kann niemand seine erste Meldung abgeben.
                Timestamp juengste = ergebnis.getTimestamp(1, Datenbank.utcKalender());
                return Optional.ofNullable(juengste).map(Timestamp::toInstant);
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Die letzte Meldung von " + melder
                    + " ist nicht lesbar", fehler);
        }
    }

    @Override
    public List<Meldung> ungeleseneVon(UUID melder) {
        pruefe(melder);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(UNGELESENE_VON)) {
            anfrage.setString(1, melder.toString());
            return alleZeilen(anfrage);
        } catch (SQLException fehler) {
            throw new IllegalStateException("Die Antworten fuer " + melder
                    + " sind nicht lesbar", fehler);
        }
    }

    /** Ob es diese Nummer gibt - nur fuer die Unterscheidung in {@link #schreibe}. */
    private boolean gibtEs(long nummer) {
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(ZAEHLE_NUMMER)) {
            anfrage.setLong(1, nummer);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getInt(1) > 0;
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Die Meldung " + nummer
                    + " ist nicht nachzaehlbar", fehler);
        }
    }

    private static List<Meldung> alleZeilen(PreparedStatement anfrage)
            throws SQLException {
        try (ResultSet ergebnis = anfrage.executeQuery()) {
            List<Meldung> gefunden = new ArrayList<>();
            while (ergebnis.next()) {
                gefunden.add(ausZeile(ergebnis));
            }
            return List.copyOf(gefunden);
        }
    }

    /**
     * Eine Zeile - die EINE Stelle, die die Spalten kennt.
     *
     * <p>Der STAND kommt als Text und wird in die Aufzaehlung uebersetzt. Steht dort
     * etwas Unbekanntes - eine von Hand verdorbene Zeile oder ein zurueckgerolltes
     * Plugin -, wirft {@code valueOf} eine Meldung ohne jeden Hinweis darauf, WELCHE
     * Zeile gemeint ist. Darum wird sie hier gefangen und mit der Nummer neu geworfen:
     * Eine Liste, die mit "No enum constant" abbricht, laesst den Betreiber die ganze
     * Tabelle durchsuchen.
     */
    private static Meldung ausZeile(ResultSet ergebnis) throws SQLException {
        long nummer = ergebnis.getLong("nummer");
        String standtext = ergebnis.getString("stand");
        Meldestand stand;
        try {
            stand = Meldestand.valueOf(standtext);
        } catch (IllegalArgumentException unbekannt) {
            throw new IllegalStateException("Die Meldung " + nummer + " hat den Stand \""
                    + standtext + "\", und den kennt dieses Plugin nicht. Entweder ist"
                    + " die Zeile von Hand geaendert worden, oder das Plugin ist aelter"
                    + " als die Zeile. Erlaubt sind: OFFEN, IN_ARBEIT, ABGESCHLOSSEN,"
                    + " ABGEWIESEN.", unbekannt);
        }
        String bearbeiter = ergebnis.getString("bearbeiter");
        Timestamp erledigt = ergebnis.getTimestamp("erledigt_am",
                Datenbank.utcKalender());
        return new Meldung(nummer,
                UUID.fromString(ergebnis.getString("melder")),
                UUID.fromString(ergebnis.getString("betroffener")),
                ergebnis.getString("grund"),
                ergebnis.getString("welt"),
                ergebnis.getInt("x"),
                ergebnis.getInt("y"),
                ergebnis.getInt("z"),
                ergebnis.getTimestamp("gemeldet_am", Datenbank.utcKalender()).toInstant(),
                stand,
                bearbeiter == null ? null : UUID.fromString(bearbeiter),
                ergebnis.getString("vermerk"),
                erledigt == null ? null : erledigt.toInstant(),
                ergebnis.getBoolean("gelesen"));
    }

    private static void pruefe(UUID melder) {
        if (melder == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Meldungen");
        }
    }
}
