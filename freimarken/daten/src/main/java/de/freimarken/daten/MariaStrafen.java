package de.freimarken.daten;

import de.freimarken.kern.Strafablage;
import de.freimarken.kern.Strafart;
import de.freimarken.kern.Strafdauer;
import de.freimarken.kern.Strafe;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Die Strafen in MariaDB (Plan 12c.3, Schritt 16a Teil B).
 *
 * <p>Haelt denselben {@code StrafVertrag} wie die Fassung im Arbeitsspeicher - und hier
 * ist die MariaDB-Fassung die einzige, die ein Server mit Team gebrauchen kann:
 * {@code SpeicherStrafen} vergisst beim Neustart alles, und das heisst, <b>jeder gebannte
 * Spieler ist am naechsten Morgen wieder da</b>.
 *
 * <p><b>Die NUMMER kommt aus {@code AUTO_INCREMENT}</b>, gelesen mit
 * {@link Statement#getGeneratedKeys()} - dieselbe Bauart wie {@link MariaMeldungen} und
 * aus demselben Grund: {@code lege} gibt die FERTIGE Strafe zurueck, damit der Aufrufer
 * nicht danach suchen muss.
 *
 * <p><b>Der Riegel gegen zwei Aufhebungen steht in der WHERE-Bedingung</b>, nicht in
 * Java: {@code UPDATE ... WHERE nummer = ? AND aufgehoben_am IS NULL}. Damit entscheidet
 * die Datenbank, wer zuerst war. Eine Pruefung in Java liesse den Augenblick zwischen
 * Lesen und Schreiben offen, und dann stehen zwei Aufhebungsgruende uebereinander - der
 * erste, der richtige, ist verloren.
 *
 * <p><b>Gespeichert wird ein ENDE und keine Dauer</b>, denn nur nach einem Ende laesst
 * sich fragen: {@code laeuft_ab_am IS NULL OR laeuft_ab_am > ?}. Mit einer Spanne muesste
 * jede Zeile gelesen und gerechnet werden, und die Abfrage ginge je Beitritt ueber die
 * ganze Tabelle.
 *
 * <p><b>Die Zeit kommt aus Java und niemals aus der Datenbank</b> - kein {@code NOW()}.
 * Das ist hier mehr als eine Gewohnheit: Ob ein Bann noch gilt, haengt an einem Vergleich
 * zweier Zeiten, und wenn die eine aus der Datenbankuhr und die andere aus der Serveruhr
 * kommt, entscheidet ueber die Freiheit eines Spielers ein Zeitversatz.
 *
 * <p>{@link Datenbank#utcKalender()} bei jeder Zeitspalte, wie ueberall in diesem Schema.
 */
public final class MariaStrafen implements Strafablage {

    /** Die Spalten in der Reihenfolge, in der {@link #ausZeile} sie liest. */
    private static final String SPALTEN =
            "nummer, betroffener, art, grund, von_wem, verhaengt_am, laeuft_ab_am,"
                    + " aufgehoben_am, aufgehoben_von, aufhebungsgrund";

    private static final String LEGEN =
            "INSERT INTO strafen (betroffener, art, grund, von_wem, verhaengt_am,"
                    + " laeuft_ab_am, aufgehoben_am, aufgehoben_von, aufhebungsgrund)"
                    + " VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, '')";

    private static final String LESEN =
            "SELECT " + SPALTEN + " FROM strafen WHERE nummer = ?";

    /**
     * Hebt auf - aber nur, wenn sie noch steht. DER RIEGEL.
     *
     * <p>{@code aufgehoben_am IS NULL} ist die ganze Zusage: Zwei Moderatoren, die
     * dieselbe Sperre im selben Augenblick aufheben, lesen beide eine stehende Strafe;
     * der zweite {@code UPDATE} trifft dann keine Zeile und gibt 0 zurueck.
     */
    private static final String AUFHEBEN =
            "UPDATE strafen SET aufgehoben_am = ?, aufgehoben_von = ?,"
                    + " aufhebungsgrund = ? WHERE nummer = ? AND aufgehoben_am IS NULL";

    /** Nur zur Unterscheidung "gibt es nicht" von "schon aufgehoben". */
    private static final String ZAEHLE_NUMMER =
            "SELECT COUNT(*) FROM strafen WHERE nummer = ?";

    /**
     * Die geltende Strafe dieser Art - die, die am LAENGSTEN gilt.
     *
     * <p><b>Die Sortierung ist eine Zusage und keine Bequemlichkeit.</b>
     * {@code (laeuft_ab_am IS NULL) DESC} setzt die unbegrenzte nach vorn - sie hat kein
     * Ende, mit dem sie sich vergleichen liesse. Dann das spaeteste Ende, dann die
     * hoehere Nummer, damit die Antwort bei gleichem Ende nicht vom Index abhaengt.
     *
     * <p>Gaebe diese Abfrage die ERSTE gefundene, liesse eine zweite, kuerzere Zeile
     * einen gebannten Spieler herein - und niemand sucht den Fehler in der Sortierung
     * einer Abfrage, deren Antwort ja eine Strafe ist.
     */
    private static final String GELTENDE =
            "SELECT " + SPALTEN + " FROM strafen WHERE betroffener = ? AND art = ?"
                    + " AND aufgehoben_am IS NULL"
                    + " AND (laeuft_ab_am IS NULL OR laeuft_ab_am > ?)"
                    + " ORDER BY (laeuft_ab_am IS NULL) DESC, laeuft_ab_am DESC,"
                    + " nummer DESC LIMIT 1";

    /**
     * Die Vorgeschichte - die NEUESTEN zuerst.
     *
     * <p>Anders als bei den Meldungen, und aus dem umgekehrten Grund: Bei einer Meldung
     * wartet die aelteste am laengsten; bei einer Vorgeschichte ist die letzte Tat die,
     * nach der gefragt wird. {@code verhaengt_am} UND {@code nummer} im ORDER BY, weil es
     * zwei Strafen in derselben Millisekunde gibt - der geteilte Vertrag vergleicht
     * beide Fassungen.
     */
    private static final String VON =
            "SELECT " + SPALTEN + " FROM strafen WHERE betroffener = ?"
                    + " ORDER BY verhaengt_am DESC, nummer DESC LIMIT ?";

    /** Alle geltenden dieser Art - fuer die oeffentliche Bannliste (Plan 12c.4). */
    private static final String GELTENDE_ALLE =
            "SELECT " + SPALTEN + " FROM strafen WHERE art = ?"
                    + " AND aufgehoben_am IS NULL"
                    + " AND (laeuft_ab_am IS NULL OR laeuft_ab_am > ?)"
                    + " ORDER BY verhaengt_am DESC, nummer DESC LIMIT ?";

    private static final String ZAEHLE =
            "SELECT COUNT(*) FROM strafen WHERE betroffener = ? AND art = ?";

    private final Datenbank datenbank;

    public MariaStrafen(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    @Override
    public Strafe lege(UUID betroffener, Strafart art, String grund, UUID vonWem,
                       Instant verhaengtAm, Strafdauer dauer) {
        // GEPRUEFT WIRD IM KERN, und zwar VOR dem Schreiben: Strafe.neu wirft bei
        // fehlendem Grund, Steuerzeichen und bei Art und Dauer, die nicht zusammenpassen
        // - in BEIDE Richtungen. Die Prueffolge laeuft hier ohne Nummer durch; 1L ist
        // ein Platzhalter, den die Datenbank gleich ersetzt. Ohne diesen Schritt kaeme
        // ein zu langer Grund erst im strict mode von MariaDB an, und die Klage waere
        // englisch und ohne Hinweis auf die Stelle.
        Strafe geprueft = Strafe.neu(1L, betroffener, art, grund, vonWem, verhaengtAm,
                dauer);
        try (PreparedStatement anfrage = datenbank.verbindung()
                .prepareStatement(LEGEN, Statement.RETURN_GENERATED_KEYS)) {
            anfrage.setString(1, geprueft.betroffener().toString());
            anfrage.setString(2, geprueft.art().name());
            anfrage.setString(3, geprueft.grund());
            anfrage.setString(4, geprueft.verhaengtVon().map(UUID::toString)
                    .orElse(null));
            anfrage.setTimestamp(5, Timestamp.from(geprueft.verhaengtAm()),
                    Datenbank.utcKalender());
            // setNull UND NICHT setTimestamp(i, null, kalender) - GEMESSEN am
            // 25.11.2026 an MariaMeldungen: Jener Aufruf wirft eine
            // NullPointerException "date must not be null", weil der Treiber den
            // Kalender mit dem Wert fuettert (Calendar.setTime(null)). Hier trifft es
            // JEDE unbegrenzte Strafe - also genau die, die am wichtigsten ist.
            setzeZeit(anfrage, 6, geprueft.endet().orElse(null));
            anfrage.executeUpdate();
            try (ResultSet schluessel = anfrage.getGeneratedKeys()) {
                if (!schluessel.next()) {
                    throw new IllegalStateException("Die Strafe wurde geschrieben, aber"
                            + " die Datenbank nennt ihre Nummer nicht. Ohne Nummer ist"
                            + " sie fuer /strafe aufheben unerreichbar - der Betroffene"
                            + " bleibt gesperrt, und niemand kann ihn freigeben");
                }
                long nummer = schluessel.getLong(1);
                // NEU GEBAUT mit der echten Nummer und nicht nachgelesen: Ein zweiter
                // Lesezugriff waere bei zwei Strafen in derselben Millisekunde der
                // Griff zur falschen Zeile - und genau dagegen gibt lege() die fertige
                // Strafe zurueck.
                return Strafe.neu(nummer, geprueft.betroffener(), geprueft.art(),
                        geprueft.grund(), geprueft.vonWem(), geprueft.verhaengtAm(),
                        dauer);
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Strafe nicht schreibbar: " + art + " gegen "
                    + betroffener, fehler);
        }
    }

    @Override
    public Optional<Strafe> lies(long nummer) {
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
            throw new IllegalStateException("Strafe nicht lesbar: " + nummer, fehler);
        }
    }

    @Override
    public boolean hebeAuf(Strafe geaendert) {
        if (geaendert == null) {
            throw new IllegalArgumentException("Nichts zu schreiben");
        }
        if (!geaendert.istAufgehoben()) {
            // Ohne diesen Riegel waere hebeAuf ein allgemeines Schreiben, mit dem sich
            // der GRUND einer stehenden Strafe nachtraeglich aendern liesse - und das
            // Protokoll waere eines, in dem man radieren kann.
            throw new IllegalArgumentException("Die Fassung der Strafe "
                    + geaendert.nummer() + " ist nicht aufgehoben - hebeAuf schreibt"
                    + " nur Aufhebungen");
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(AUFHEBEN)) {
            anfrage.setTimestamp(1,
                    Timestamp.from(geaendert.aufhebung().orElseThrow()),
                    Datenbank.utcKalender());
            anfrage.setString(2, geaendert.aufhebungDurch().map(UUID::toString)
                    .orElse(null));
            anfrage.setString(3, geaendert.aufhebungsgrund());
            anfrage.setLong(4, geaendert.nummer());
            if (anfrage.executeUpdate() > 0) {
                return true;
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Strafe nicht aufhebbar: "
                    + geaendert.nummer(), fehler);
        }
        // KEINE Zeile getroffen - zwei Gruende, und sie sind verschieden zu beantworten:
        // Die Nummer gibt es nicht (Rechenfehler im Aufrufer, also ein Wurf), oder sie
        // ist schon aufgehoben (ein anderer war schneller, also false). Gezaehlt wird
        // erst JETZT, damit der haeufige Fall ohne zweite Abfrage auskommt.
        if (!gibtEs(geaendert.nummer())) {
            throw new IllegalArgumentException("Die Strafe " + geaendert.nummer()
                    + " gibt es nicht");
        }
        return false;
    }

    @Override
    public Optional<Strafe> geltende(UUID betroffener, Strafart art, Instant jetzt) {
        pruefe(betroffener, art);
        if (jetzt == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt ist nicht zu sagen, was"
                    + " noch gilt");
        }
        if (!art.giltFort()) {
            // OHNE ABFRAGE, und das ist kein Sparen, sondern die Zusage der
            // Schnittstelle: Eine Art, die nicht fortwirkt, gilt nie noch. In der
            // Tabelle steht fuer einen Rauswurf das Ende gleich dem Anfang - wer mit
            // einem Zeitpunkt VOR der Tat fragte, bekaeme ihn sonst als geltend
            // zurueck, und die Fassung im Arbeitsspeicher antwortete anders.
            return Optional.empty();
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(GELTENDE)) {
            anfrage.setString(1, betroffener.toString());
            anfrage.setString(2, art.name());
            anfrage.setTimestamp(3, Timestamp.from(jetzt), Datenbank.utcKalender());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? Optional.of(ausZeile(ergebnis))
                        : Optional.empty();
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Die geltende Strafe (" + art + ") von "
                    + betroffener + " ist nicht lesbar", fehler);
        }
    }

    @Override
    public List<Strafe> von(UUID betroffener, int hoechstens) {
        if (betroffener == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Strafen");
        }
        pruefeZahl(hoechstens);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(VON)) {
            anfrage.setString(1, betroffener.toString());
            anfrage.setInt(2, hoechstens);
            return alleZeilen(anfrage);
        } catch (SQLException fehler) {
            throw new IllegalStateException("Die Strafen von " + betroffener
                    + " sind nicht lesbar", fehler);
        }
    }

    @Override
    public List<Strafe> geltendeAlle(Strafart art, int hoechstens, Instant jetzt) {
        if (art == null) {
            throw new IllegalArgumentException("Ohne Art gibt es keine Liste");
        }
        pruefeZahl(hoechstens);
        if (jetzt == null) {
            throw new IllegalArgumentException("Ohne Zeitpunkt ist nicht zu sagen, was"
                    + " noch gilt");
        }
        if (!art.giltFort()) {
            // Dieselbe Festlegung wie in geltende - siehe dort.
            return List.of();
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(GELTENDE_ALLE)) {
            anfrage.setString(1, art.name());
            anfrage.setTimestamp(2, Timestamp.from(jetzt), Datenbank.utcKalender());
            anfrage.setInt(3, hoechstens);
            return alleZeilen(anfrage);
        } catch (SQLException fehler) {
            throw new IllegalStateException("Die geltenden Strafen der Art " + art
                    + " sind nicht lesbar", fehler);
        }
    }

    @Override
    public int zaehle(UUID betroffener, Strafart art) {
        pruefe(betroffener, art);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(ZAEHLE)) {
            anfrage.setString(1, betroffener.toString());
            anfrage.setString(2, art.name());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getInt(1);
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Die Strafen der Art " + art + " von "
                    + betroffener + " sind nicht zaehlbar", fehler);
        }
    }

    /** Ob es diese Nummer gibt - nur fuer die Unterscheidung in {@link #hebeAuf}. */
    private boolean gibtEs(long nummer) {
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(ZAEHLE_NUMMER)) {
            anfrage.setLong(1, nummer);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                ergebnis.next();
                return ergebnis.getInt(1) > 0;
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Die Strafe " + nummer
                    + " ist nicht nachzaehlbar", fehler);
        }
    }

    /**
     * Eine Zeitspalte setzen, die fehlen darf.
     *
     * <p>Die EINE Stelle, die den Unterschied zwischen {@code setTimestamp} und
     * {@code setNull} kennt - siehe die Begruendung in {@link #lege}. Mit zwei Stellen
     * waere eine davon die, die eines Tages wieder {@code setTimestamp(i, null, cal)}
     * schreibt.
     */
    private static void setzeZeit(PreparedStatement anfrage, int stelle, Instant wann)
            throws SQLException {
        if (wann == null) {
            anfrage.setNull(stelle, Types.TIMESTAMP);
        } else {
            anfrage.setTimestamp(stelle, Timestamp.from(wann),
                    Datenbank.utcKalender());
        }
    }

    private static List<Strafe> alleZeilen(PreparedStatement anfrage)
            throws SQLException {
        try (ResultSet ergebnis = anfrage.executeQuery()) {
            List<Strafe> gefunden = new ArrayList<>();
            while (ergebnis.next()) {
                gefunden.add(ausZeile(ergebnis));
            }
            return List.copyOf(gefunden);
        }
    }

    /**
     * Eine Zeile - die EINE Stelle, die die Spalten kennt.
     *
     * <p>Die ART kommt als Text und wird in die Aufzaehlung uebersetzt. Steht dort etwas
     * Unbekanntes - eine von Hand verdorbene Zeile oder ein zurueckgerolltes Plugin -,
     * wirft {@code valueOf} eine Meldung ohne jeden Hinweis darauf, WELCHE Zeile gemeint
     * ist. Darum wird sie hier gefangen und mit der Nummer neu geworfen: Eine Bannliste,
     * die mit "No enum constant" abbricht, laesst den Betreiber die ganze Tabelle
     * durchsuchen - und bis dahin kommt niemand herein, dessen Zeile dahinter steht.
     */
    private static Strafe ausZeile(ResultSet ergebnis) throws SQLException {
        long nummer = ergebnis.getLong("nummer");
        String arttext = ergebnis.getString("art");
        Strafart art;
        try {
            art = Strafart.valueOf(arttext);
        } catch (IllegalArgumentException unbekannt) {
            throw new IllegalStateException("Die Strafe " + nummer + " hat die Art \""
                    + arttext + "\", und die kennt dieses Plugin nicht. Entweder ist"
                    + " die Zeile von Hand geaendert worden, oder das Plugin ist aelter"
                    + " als die Zeile. Erlaubt sind: STUMM, RAUSWURF, BANN.", unbekannt);
        }
        String vonWem = ergebnis.getString("von_wem");
        String aufheber = ergebnis.getString("aufgehoben_von");
        Timestamp ende = ergebnis.getTimestamp("laeuft_ab_am",
                Datenbank.utcKalender());
        Timestamp aufgehoben = ergebnis.getTimestamp("aufgehoben_am",
                Datenbank.utcKalender());
        try {
            return new Strafe(nummer,
                    UUID.fromString(ergebnis.getString("betroffener")),
                    art,
                    ergebnis.getString("grund"),
                    vonWem == null ? null : UUID.fromString(vonWem),
                    ergebnis.getTimestamp("verhaengt_am", Datenbank.utcKalender())
                            .toInstant(),
                    ende == null ? null : ende.toInstant(),
                    aufgehoben == null ? null : aufgehoben.toInstant(),
                    aufheber == null ? null : UUID.fromString(aufheber),
                    ergebnis.getString("aufhebungsgrund"));
        } catch (IllegalArgumentException unbrauchbar) {
            // DER GANZE KONSTRUKTOR und nicht nur die Art [behoben am 25.11.2026]:
            // Die Spalten erlauben mehr als er. Der Grund ist VARCHAR(256) NOT NULL,
            // seine MINDESTlaenge von drei Zeichen steht nur in Java; eine von Hand
            // eingetragene Zeile mit grund = "ok" liess ihn werfen, und zwar als
            // IllegalArgumentException. Die faengt niemand: geltendeAlle liest ALLE
            // geltenden Banne, also riss diese eine Zeile die ganze oeffentliche
            // Bannliste mit - und, schlimmer, den Wurf aus der Beitrittswache, die
            // nur IllegalStateException faengt.
            //
            // Von Hand eingetragen wird oefter, als man denkt: /strafe trifft nur
            // Spieler, die der Server schon einmal gesehen hat.
            //
            // Neu geworfen als IllegalStateException - das ist die Gattung, die die
            // Schnittstelle fuer "nicht lesbar" zusagt, und nur sie fangen die
            // Aufrufer. Mit der NUMMER darin, sonst durchsucht der Betreiber die
            // ganze Tabelle.
            throw new IllegalStateException("Die Strafe " + nummer + " laesst sich"
                    + " nicht lesen: " + unbrauchbar.getMessage() + ". Die Zeile ist"
                    + " wahrscheinlich von Hand eingetragen oder geaendert worden -"
                    + " die Spalten erlauben mehr als das Programm (ein Grund braucht"
                    + " mindestens " + Strafe.KUERZESTER_GRUND + " Zeichen, und ein"
                    + " Rauswurf darf kein Ende in der Zukunft haben).", unbrauchbar);
        }
    }

    private static void pruefe(UUID betroffener, Strafart art) {
        if (betroffener == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Strafen");
        }
        if (art == null) {
            throw new IllegalArgumentException("Ohne Art gibt es keine Frage");
        }
    }

    private static void pruefeZahl(int hoechstens) {
        if (hoechstens < 1) {
            throw new IllegalArgumentException("Eine Liste von " + hoechstens
                    + " Zeilen ist keine Liste");
        }
    }
}
