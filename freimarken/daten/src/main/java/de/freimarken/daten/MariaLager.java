package de.freimarken.daten;

import de.freimarken.kern.Art;
import de.freimarken.kern.BesitzLager;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Tat;
import de.freimarken.kern.Zustand;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Besitz in MariaDB.
 *
 * <p>Haelt denselben Vertrag wie jedes andere Lager - geprueft wird das durch dieselben
 * Tests, die auch das Lager im Arbeitsspeicher bestehen muss.
 *
 * <p>Der wichtigste Teil steht nicht in diesem Code, sondern im Schema: Der eindeutige
 * Schluessel auf {@code (welt, x, z)} sorgt dafuer, dass zwei gleichzeitige Versuche,
 * dieselbe Parzelle zu nehmen, nicht beide durchgehen. Eine Pruefung im Programm allein
 * koennte das nicht - zwischen Nachsehen und Schreiben passt immer ein zweiter Spieler.
 */
public final class MariaLager implements BesitzLager {

    /**
     * Die Spalten, die eine Zeile zu einem Stueck Besitz machen - an einer Stelle.
     *
     * <p>Vorher stand diese Aufzaehlung dreimal da. Beim naechsten Feld haette eine der
     * drei sie nicht bekommen, und welche, zeigte sich erst im Betrieb.
     */
    private static final String SPALTEN =
            "id, welt, x, z, eigentuemer, preis, erworben_am, art, eltern_id, zustand,"
                    + " name";

    /**
     * Was auf dem Raster liegt - und damit alles, was als Parzelle zaehlt.
     *
     * <p>Gefragt wird nach der LAGE, nicht nach der art: Eine Gruppe hat keine eigene
     * Rasterzelle, ihr Bereich ergibt sich aus ihren Kindern. Kommt spaeter eine art dazu,
     * die sehr wohl auf dem Raster liegt, zaehlt sie von selbst mit - richtig, denn sie
     * belegt Flaeche - ohne dass diese Zeichenfolge wandern muesste.
     *
     * <p>Ohne diesen Filter zaehlte die Gruppenzeile mit, und das ist keine Kleinigkeit:
     * Nach dem Zusammenlegen zweier Felder koestete die dritte Parzelle 100.000 statt
     * 50.000 Mark, weil die Preisstaffel verdoppelt (25.000 << (bereits-1)), und eine
     * Rangobergrenze von vier waere schon nach drei Parzellen erreicht.
     */
    private static final String AUF_DEM_RASTER = " AND x IS NOT NULL";

    private final Datenbank datenbank;

    public MariaLager(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    /** SQL-Fehler sind hier nicht behebbar - sie gehoeren nach oben, nicht ins Logbuch. */
    private static RuntimeException weiterreichen(String was, SQLException ursache) {
        return new IllegalStateException("Datenbankfehler beim " + was + ": "
                + ursache.getMessage(), ursache);
    }

    @Override
    public Optional<Parzelle> finde(String welt, int x, int z) {
        // Ohne AUF_DEM_RASTER und ohne art-Filter: x = ? wird gegen SQL NULL nie wahr,
        // der Filter waere also wirkungslos. Ein art-Filter dagegen waere gefaehrlich -
        // er meldete jeden Besitz, der eine echte Rasterzelle belegt und zufaellig eine
        // andere art hat, als "frei", und die Schutzwache liesse dort jeden bauen.
        // Was eine Rasterzelle belegt, wird gemeldet, gleich welcher Art.
        String sql = "SELECT " + SPALTEN + " FROM parzellen "
                + "WHERE welt = ? AND x = ? AND z = ?";
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, welt);
            anfrage.setInt(2, x);
            anfrage.setInt(3, z);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                if (!ergebnis.next()) {
                    return Optional.empty();
                }
                return Optional.of(lies(ergebnis));
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Suchen einer Parzelle", fehler);
        }
    }

    @Override
    public List<Parzelle> vonSpieler(String welt, UUID spieler) {
        String sql = "SELECT " + SPALTEN + " FROM parzellen "
                + "WHERE welt = ? AND eigentuemer = ?" + AUF_DEM_RASTER + " ORDER BY x, z";
        List<Parzelle> treffer = new ArrayList<>();
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, welt);
            anfrage.setString(2, spieler.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                while (ergebnis.next()) {
                    treffer.add(lies(ergebnis));
                }
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Lesen der Parzellen eines Spielers", fehler);
        }
        return List.copyOf(treffer);
    }

    @Override
    public int anzahlVon(String welt, UUID spieler) {
        String sql = "SELECT COUNT(*) FROM parzellen WHERE welt = ? AND eigentuemer = ?"
                + AUF_DEM_RASTER;
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, welt);
            anfrage.setString(2, spieler.toString());
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? ergebnis.getInt(1) : 0;
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Zaehlen der Parzellen", fehler);
        }
    }

    @Override
    public Parzelle anlegen(String welt, int x, int z, UUID eigentuemer, long preis) {
        // Auf Millisekunden kuerzen: Die Spalte ist DATETIME(3). Ohne das gaebe anlegen()
        // einen genaueren Zeitpunkt zurueck als der, der wirklich gespeichert wird - zwei
        // Wahrheiten fuer dieselbe Parzelle.
        Instant jetzt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        // Art, Elternteil und Zustand als Literale und nicht ueber den Vorgabewert der
        // Spalte: Was dieser Schreibweg erzeugt, soll hier stehen und nicht im Schema.
        // Die Platzhalter 1 bis 6 behalten dabei ihre Nummern.
        String sql = "INSERT INTO parzellen "
                + "(welt, x, z, eigentuemer, preis, erworben_am, art, eltern_id, zustand) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'parzelle', NULL, 'aktiv')";
        try (PreparedStatement anfrage = datenbank.verbindung()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            anfrage.setString(1, welt);
            anfrage.setInt(2, x);
            anfrage.setInt(3, z);
            anfrage.setString(4, eigentuemer.toString());
            anfrage.setLong(5, preis);
            // LocalDateTime statt Timestamp: Timestamp rechnet in die Zeitzone der JVM
            // um, und zwar beim Schreiben wie beim Lesen. Eine DATETIME-Spalte traegt
            // Wanduhrzeit ohne Zone - also schreiben wir UTC-Wanduhrzeit und lesen sie
            // genauso zurueck. Dann rechnet unterwegs niemand mehr.
            anfrage.setObject(6, LocalDateTime.ofInstant(jetzt, ZoneOffset.UTC));
            anfrage.executeUpdate();
            try (ResultSet schluessel = anfrage.getGeneratedKeys()) {
                schluessel.next();
                return new Parzelle(schluessel.getLong(1), welt, x, z, eigentuemer,
                        Set.of(), preis, jetzt,
                        Art.PARZELLE, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV);
            }
        } catch (SQLIntegrityConstraintViolationException schonDa) {
            // Der eindeutige Schluessel hat zugeschlagen - jemand war schneller. Genau
            // dieselbe Ausnahme wie beim Lager im Arbeitsspeicher, damit der Vertrag haelt.
            throw new IllegalStateException(
                    "Parzelle ist bereits vergeben: " + welt + "/" + x + "/" + z, schonDa);
        } catch (SQLException fehler) {
            throw weiterreichen("Anlegen einer Parzelle", fehler);
        }
    }

    @Override
    public Parzelle freistadtAnlegen(String welt, int feldX, int feldZ, UUID eigentuemer,
                                     long preis, String name) {
        // Auf Millisekunden kuerzen, derselbe Grund wie bei anlegen(): Die Spalte ist
        // DATETIME(3), und ein genauerer Rueckgabewert waere eine zweite Wahrheit.
        Instant jetzt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        // Art, Elternteil und Zustand wieder als Literale - dieser Schreibweg erzeugt
        // genau eine Sorte, und das soll hier stehen und nicht im Schema.
        //
        // KEIN "ON DUPLICATE KEY UPDATE": Das feuerte bei JEDEM eindeutigen Schluessel,
        // also auch beim Namen, und aenderte dann stillschweigend die fremde Zeile,
        // statt zu scheitern. Scheitern ist hier die richtige Antwort.
        String sql = "INSERT INTO parzellen "
                + "(welt, x, z, eigentuemer, preis, erworben_am, art, eltern_id, zustand,"
                + " name) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'freistadt', NULL, 'aktiv', ?)";
        try (PreparedStatement anfrage = datenbank.verbindung()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            anfrage.setString(1, welt);
            anfrage.setInt(2, feldX);
            anfrage.setInt(3, feldZ);
            anfrage.setString(4, eigentuemer.toString());
            anfrage.setLong(5, preis);
            anfrage.setObject(6, LocalDateTime.ofInstant(jetzt, ZoneOffset.UTC));
            anfrage.setString(7, name);
            anfrage.executeUpdate();
            try (ResultSet schluessel = anfrage.getGeneratedKeys()) {
                schluessel.next();
                return new Parzelle(schluessel.getLong(1), welt, feldX, feldZ,
                        eigentuemer, Set.of(), preis, jetzt,
                                Art.FREISTADT, Parzelle.KEIN_ELTERNTEIL, Zustand.AKTIV,
                        name, Parzelle.KEINE_RECHTE);
            }
        } catch (SQLIntegrityConstraintViolationException schonDa) {
            // ZWEI Schluessel koennen hier zuschlagen - das Feld und der Name -, und der
            // Treiber sagt in der Meldung, welcher. Die Ausnahme traegt beides, damit die
            // Absage am Ende dem Spieler sagen kann, WAS nicht ging; sie heisst genauso
            // wie bei der Speicherfassung, damit der gemeinsame Vertrag haelt.
            throw new IllegalStateException("Stadtfeld oder Name ist bereits vergeben: "
                    + welt + "/" + feldX + "/" + feldZ + " " + name, schonDa);
        } catch (SQLException fehler) {
            throw weiterreichen("Gruenden einer Freistadt", fehler);
        }
    }

    @Override
    public List<Parzelle> staedteIn(String welt) {
        // Nach art und nicht nach der Lage gefragt: Eine Freistadt liegt sehr wohl auf
        // einem Raster, nur auf einem anderen - AUF_DEM_RASTER traefe hier auch jede
        // Parzelle, falls je beide in derselben Welt staenden.
        //
        // ORDER BY name, nicht nach der Nummer: Die Reihenfolge ist zugesagt, und eine
        // Liste nach Gruendungsnummer waere fuer den Leser keine.
        String sql = "SELECT " + SPALTEN + " FROM parzellen"
                + " WHERE welt = ? AND art = 'freistadt' ORDER BY name";
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setString(1, welt);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                List<Parzelle> treffer = new ArrayList<>();
                while (ergebnis.next()) {
                    treffer.add(lies(ergebnis));
                }
                return List.copyOf(treffer);
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Lesen der Freistaedte", fehler);
        }
    }

    @Override
    public void mitbauerSetzen(long id, Set<UUID> mitbauer) {
        try {
            Connection c = datenbank.verbindung();
            boolean vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // schreibeMitbauerDurch nimmt die Gruppe UND alle ihre Kinder. Bei
                // einer Rasterzelle ohne Kinder ist das genau die eine Zeile - deshalb
                // braucht es hier keine Fallunterscheidung.
                schreibeMitbauerDurch(c, id, mitbauer);
                c.commit();
            } catch (SQLException fehler) {
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
                    // Jetzt steht die Verbindung womoeglich dauerhaft auf autoCommit=false.
                    // Weitergegeben wuerde jeder weitere Schreibvorgang in einer
                    // Transaktion landen, die niemand mehr committet - also lieber weg
                    // damit, der naechste Zugriff baut eine frische auf.
                    datenbank.verwerfen();
                }
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Setzen der Mitbauer", fehler);
        }
    }

    @Override
    public void buergerrechteSetzen(long id, Set<Tat> rechte) {
        try {
            Connection c = datenbank.verbindung();
            boolean vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // ERST nachsehen, OB es die Zeile gibt und was sie ist: Ohne das waere
                // ein Setzen auf eine unbekannte Nummer still erfolgreich - der
                // Fremdschluessel wuerde zwar beim Einfuegen klagen, aber nicht bei einer
                // LEEREN Menge, denn dann wird gar nichts eingefuegt. Und das
                // Speicherlager wirft dort. Zwei Umsetzungen hinter einer Schnittstelle
                // muessen dasselbe tun.
                String art = artVon(c, id);
                if (art == null) {
                    throw new IllegalStateException("Keine Parzelle mit der Nummer "
                            + id);
                }
                if (!Art.FREISTADT.schluessel().equals(art)) {
                    throw new IllegalStateException("Nummer " + id + " ist keine"
                            + " Freistadt, sondern " + art + " - Buergerrechte gelten"
                            + " nur fuer eine Stadt");
                }
                // ERSETZEND: erst raeumen, dann schreiben, beides in EINER Transaktion.
                // Zwei Zuege ohne Transaktion haetten einen Augenblick dazwischen, in
                // dem die Stadt gar kein Recht hat - und faellt dann die Verbindung aus,
                // bleibt es dabei.
                try (PreparedStatement raeumen = c.prepareStatement(
                        "DELETE FROM freistadt_rechte WHERE stadt_id = ?")) {
                    raeumen.setLong(1, id);
                    raeumen.executeUpdate();
                }
                if (!rechte.isEmpty()) {
                    try (PreparedStatement setzen = c.prepareStatement(
                            "INSERT INTO freistadt_rechte (stadt_id, recht)"
                                    + " VALUES (?, ?)")) {
                        for (Tat recht : rechte) {
                            setzen.setLong(1, id);
                            // Der SCHLUESSEL und nicht name(): In der Spalte steht
                            // "behaelter", nicht "BEHAELTER" - und eine Umbenennung der
                            // Konstante darf keine Zeile unlesbar machen.
                            setzen.setString(2, recht.schluessel());
                            setzen.addBatch();
                        }
                        setzen.executeBatch();
                    }
                }
                c.commit();
            } catch (SQLException fehler) {
                try {
                    c.rollback();
                } catch (SQLException beimZuruecknehmen) {
                    fehler.addSuppressed(beimZuruecknehmen);
                }
                throw fehler;
            } catch (IllegalStateException keineStadt) {
                // Auch der eigene Riegel muss zuruecknehmen: Das DELETE kann schon
                // gelaufen sein, wenn die Pruefung danach klagt - sie tut es hier nicht,
                // aber eine Umstellung der Reihenfolge waere sonst eine stille
                // Leerung.
                try {
                    c.rollback();
                } catch (SQLException beimZuruecknehmen) {
                    keineStadt.addSuppressed(beimZuruecknehmen);
                }
                throw keineStadt;
            } finally {
                try {
                    c.setAutoCommit(vorherAutomatisch);
                } catch (SQLException beimZuruecksetzen) {
                    datenbank.verwerfen();
                }
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Setzen der Buergerrechte", fehler);
        }
    }

    /** Die Art dieser Zeile als Schluessel - oder {@code null}, wenn es sie nicht gibt. */
    private String artVon(Connection c, long id) throws SQLException {
        try (PreparedStatement anfrage =
                     c.prepareStatement("SELECT art FROM parzellen WHERE id = ?")) {
            anfrage.setLong(1, id);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? ergebnis.getString("art") : null;
            }
        }
    }

    @Override
    public void entfernen(long id) {
        try (PreparedStatement anfrage = datenbank.verbindung()
                .prepareStatement("DELETE FROM parzellen WHERE id = ? AND art <> 'gruppe'")) {
            anfrage.setLong(1, id);
            // Die Mitbauer raeumt der Fremdschluessel mit ON DELETE CASCADE weg.
            if (anfrage.executeUpdate() == 0 && istGruppe(id)) {
                // Ohne diesen Riegel taete entfernen() bei einer Gruppe still gar nichts,
                // und der Aufrufer glaubte, sie sei weg. Nachgesehen wird nur im
                // Fehlerfall - und nur hier, denn eine unbekannte Nummer zu entfernen ist
                // laut Vertrag ausdruecklich KEIN Fehler.
                throw new IllegalStateException(
                        "Nummer " + id + " ist eine Gruppe - erst auftrennen");
            }
        } catch (SQLIntegrityConstraintViolationException hatKinder) {
            // fk_besitz_elternteil steht auf RESTRICT: Eine Zeile, auf die eltern_id
            // zeigt, laesst sich nicht loeschen - sonst naehme ein Aufgeben die Kinder
            // mit. Das hier ist Protokolltext und KEINE Spielerantwort; die kommt aus
            // Bescheid.TEIL_EINER_GRUPPE, bevor es ueberhaupt so weit kommt.
            throw new IllegalStateException("Besitz " + id
                    + " hat noch untergeordnete Eintraege - erst auftrennen", hatKinder);
        } catch (SQLException fehler) {
            throw weiterreichen("Entfernen einer Parzelle", fehler);
        }
    }

    @Override
    public List<Parzelle> kinderVon(long gruppeId) {
        // Bewusst ohne AUF_DEM_RASTER: Ein Kind ohne Rasterzelle waere ein Fehler, und
        // dann soll der Riegel in lies() laut werden, statt die Zeile still zu
        // verschweigen.
        String sql = "SELECT " + SPALTEN + " FROM parzellen WHERE eltern_id = ? "
                + "ORDER BY x, z";
        List<Parzelle> treffer = new ArrayList<>();
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setLong(1, gruppeId);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                while (ergebnis.next()) {
                    treffer.add(lies(ergebnis));
                }
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Lesen der Gruppenfelder", fehler);
        }
        return List.copyOf(treffer);
    }

    @Override
    public long gruppeSetzen(long gruppeId, String welt, UUID eigentuemer,
                             Set<UUID> mitbauer, long gebuehr, List<Long> felder) {
        if (felder.isEmpty()) {
            throw new IllegalStateException("Kein Feld zum Anhaengen angegeben");
        }
        boolean vorherAutomatisch = true;
        Connection c = null;
        try {
            c = datenbank.verbindung();
            vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                long id = gruppeId == Parzelle.KEIN_ELTERNTEIL
                        ? legeGruppeAn(c, welt, eigentuemer, gebuehr)
                        : erweitereGruppe(c, gruppeId, gebuehr);
                haengeFelderAn(c, id, welt, eigentuemer, felder);
                schreibeMitbauerDurch(c, id, mitbauer);
                c.commit();
                return id;
            } catch (SQLException | RuntimeException fehler) {
                zuruecknehmen(c, fehler);
                throw fehler;
            } finally {
                zurueckAufAutomatik(c, vorherAutomatisch);
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Anlegen oder Erweitern einer Gruppe", fehler);
        }
    }

    @Override
    public void ausGruppeLoesen(long feldId) {
        // Eine einzige Anweisung, also keine Transaktion: Die Gruppenzeile bleibt stehen,
        // und der Fremdschluessel auf sie ebenso. Die Bedingung art <> 'gruppe' verhindert,
        // dass jemand mit der Nummer einer Gruppe eine andere Gruppe aus ihrer Gruppe
        // loest - das waere eine Verschachtelung, die es nicht geben darf.
        // Die Verbindung gehoert NICHT in die Ressourcenklammer: Sie ist geteilt und
        // langlebig, und wer sie hier schliesst, zwingt den naechsten Zugriff - im
        // Normalfall die Besitzpruefung beim naechsten gesetzten Block, im Hauptfaden -
        // zum vollen Neuaufbau. Jede andere Methode dieser Klasse macht es deshalb so.
        try (PreparedStatement loesen = datenbank.verbindung().prepareStatement(
                "UPDATE parzellen SET eltern_id = NULL WHERE id = ?"
                        + " AND art <> 'gruppe' AND eltern_id IS NOT NULL")) {
            loesen.setLong(1, feldId);
            if (loesen.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Nummer " + feldId + " steckt in keiner Gruppe");
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Ein Feld aus seiner Gruppe loesen", fehler);
        }
    }

    @Override
    public void auftrennen(long gruppeId) {
        boolean vorherAutomatisch = true;
        Connection c = null;
        try {
            c = datenbank.verbindung();
            vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // Reihenfolge ist Pflicht: fk_besitz_elternteil steht auf RESTRICT.
                // Andersherum verweigert InnoDB die Zeile, weil noch Kinder zeigen.
                try (PreparedStatement loesen = c.prepareStatement(
                        "UPDATE parzellen SET eltern_id = NULL WHERE eltern_id = ?")) {
                    loesen.setLong(1, gruppeId);
                    loesen.executeUpdate();
                }
                try (PreparedStatement weg = c.prepareStatement(
                        "DELETE FROM parzellen WHERE id = ? AND art = 'gruppe'")) {
                    weg.setLong(1, gruppeId);
                    if (weg.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "Nummer " + gruppeId + " ist keine Gruppe");
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException fehler) {
                zuruecknehmen(c, fehler);
                throw fehler;
            } finally {
                zurueckAufAutomatik(c, vorherAutomatisch);
            }
        } catch (SQLException fehler) {
            throw weiterreichen("Auftrennen einer Gruppe", fehler);
        }
    }

    private long legeGruppeAn(Connection c, String welt, UUID eigentuemer, long gebuehr)
            throws SQLException {
        String sql = "INSERT INTO parzellen "
                + "(welt, x, z, eigentuemer, preis, erworben_am, art, eltern_id, zustand) "
                + "VALUES (?, NULL, NULL, ?, ?, ?, 'gruppe', NULL, 'aktiv')";
        try (PreparedStatement anfrage =
                     c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            anfrage.setString(1, welt);
            anfrage.setString(2, eigentuemer.toString());
            anfrage.setLong(3, gebuehr);
            anfrage.setObject(4, LocalDateTime.ofInstant(
                    Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC));
            anfrage.executeUpdate();
            try (ResultSet schluessel = anfrage.getGeneratedKeys()) {
                if (!schluessel.next()) {
                    throw new IllegalStateException("Die Gruppe bekam keine Nummer");
                }
                return schluessel.getLong(1);
            }
        }
    }

    private long erweitereGruppe(Connection c, long gruppeId, long gebuehr)
            throws SQLException {
        // FOR UPDATE sperrt die Zeile und reiht gleichzeitige Erweiterungen hintereinander
        // auf. Ohne sie koennten zwei Erweiterungen denselben Preis lesen und einer der
        // beiden Beitraege verschwaende.
        try (PreparedStatement pruefen = c.prepareStatement(
                "SELECT preis FROM parzellen WHERE id = ? AND art = 'gruppe' FOR UPDATE")) {
            pruefen.setLong(1, gruppeId);
            try (ResultSet ergebnis = pruefen.executeQuery()) {
                if (!ergebnis.next()) {
                    throw new IllegalStateException(
                            "Keine Gruppe mit der Nummer " + gruppeId);
                }
            }
        }
        // erworben_am bleibt: Die Gruppe entsteht einmal. Eine Gruppe, die bei jedem
        // Wachsen juenger wird als ihre Kinder, setzt jede kuenftige Verfallsrechnung
        // still zurueck.
        try (PreparedStatement teurer = c.prepareStatement(
                "UPDATE parzellen SET preis = preis + ? WHERE id = ?")) {
            teurer.setLong(1, gebuehr);
            teurer.setLong(2, gruppeId);
            teurer.executeUpdate();
        }
        return gruppeId;
    }

    private void haengeFelderAn(Connection c, long gruppeId, String welt, UUID eigentuemer,
                                List<Long> felder) throws SQLException {
        // Die vier Zusatzbedingungen sind der Wettlaufriegel. Sie machen drei Dinge
        // unmoeglich, die kein Programm allein verhindern kann, weil zwischen Nachsehen
        // und Schreiben immer ein zweiter Spieler passt: ein fremdes Feld anhaengen,
        // ein Feld zweimal vergeben (eltern_id IS NULL) und eine GRUPPE als Kind
        // anhaengen (x IS NOT NULL - eine Gruppe hat kein eltern_id und waere es sonst).
        // Dasselbe Prinzip wie der eindeutige Schluessel ort.
        String sql = "UPDATE parzellen SET eltern_id = ? WHERE id = ? AND welt = ? "
                + "AND eigentuemer = ? AND eltern_id IS NULL AND x IS NOT NULL";
        try (PreparedStatement anfrage = c.prepareStatement(sql)) {
            for (long feldId : felder) {
                anfrage.setLong(1, gruppeId);
                anfrage.setLong(2, feldId);
                anfrage.setString(3, welt);
                anfrage.setString(4, eigentuemer.toString());
                if (anfrage.executeUpdate() != 1) {
                    throw new IllegalStateException("Feld " + feldId + " laesst sich nicht "
                            + "anhaengen: Es fehlt, gehoert jemand anderem, liegt nicht auf "
                            + "dem Raster oder ist schon in einer Gruppe");
                }
            }
        }
    }

    private void schreibeMitbauerDurch(Connection c, long gruppeId, Set<UUID> mitbauer)
            throws SQLException {
        List<Long> zeilen = new ArrayList<>();
        zeilen.add(gruppeId);
        try (PreparedStatement kinder = c.prepareStatement(
                "SELECT id FROM parzellen WHERE eltern_id = ?")) {
            kinder.setLong(1, gruppeId);
            try (ResultSet ergebnis = kinder.executeQuery()) {
                while (ergebnis.next()) {
                    zeilen.add(ergebnis.getLong(1));
                }
            }
        }
        try (PreparedStatement weg = c.prepareStatement(
                "DELETE FROM parzellen_mitbauer WHERE parzelle_id = ?")) {
            for (long zeile : zeilen) {
                weg.setLong(1, zeile);
                weg.addBatch();
            }
            weg.executeBatch();
        }
        if (mitbauer.isEmpty()) {
            return;
        }
        try (PreparedStatement hin = c.prepareStatement(
                "INSERT INTO parzellen_mitbauer (parzelle_id, spieler) VALUES (?, ?)")) {
            for (long zeile : zeilen) {
                for (UUID spieler : mitbauer) {
                    hin.setLong(1, zeile);
                    hin.setString(2, spieler.toString());
                    hin.addBatch();
                }
            }
            hin.executeBatch();
        }
    }

    private void zuruecknehmen(Connection c, Exception ursache) {
        try {
            c.rollback();
        } catch (SQLException beimZuruecknehmen) {
            ursache.addSuppressed(beimZuruecknehmen);
        }
    }

    private void zurueckAufAutomatik(Connection c, boolean vorher) {
        try {
            c.setAutoCommit(vorher);
        } catch (SQLException beimZuruecksetzen) {
            // Weitergegeben landete jeder weitere Schreibvorgang in einer Transaktion,
            // die niemand mehr committet - also lieber weg damit.
            datenbank.verwerfen();
        }
    }

    private boolean istGruppe(long id) throws SQLException {
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(
                "SELECT 1 FROM parzellen WHERE id = ? AND art = 'gruppe'")) {
            anfrage.setLong(1, id);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next();
            }
        }
    }

    private Optional<Parzelle> nachNummer(long id) throws SQLException {
        // Bewusst ohne AUF_DEM_RASTER: Wer hier nach einer Nummer fragt, hat sie gerade
        // selbst geschrieben. Liegt sie nicht auf dem Raster, soll das laut auffallen -
        // siehe den Riegel in lies().
        String sql = "SELECT " + SPALTEN + " FROM parzellen WHERE id = ?";
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(sql)) {
            anfrage.setLong(1, id);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                return ergebnis.next() ? Optional.of(lies(ergebnis)) : Optional.empty();
            }
        }
    }

    private Parzelle lies(ResultSet ergebnis) throws SQLException {
        long id = ergebnis.getLong("id");
        Integer x = ergebnis.getObject("x", Integer.class);
        Integer z = ergebnis.getObject("z", Integer.class);
        if (x == null || z == null) {
            // getInt() gaebe hier lautlos 0 zurueck - also die Parzelle 0/0 mitten in der
            // Marktstadt, mit dem Eigentuemer dieser Zeile. 0/0 ist in der Spieldatenbank
            // vergeben. Wer Besitz ohne Rasterzelle lesen will, braucht einen eigenen
            // Leseweg, keine stille Notluege.
            throw new IllegalStateException("Besitz " + id + " der Art "
                    + ergebnis.getString("art") + " liegt nicht auf dem Raster");
        }
        return new Parzelle(
                id,
                ergebnis.getString("welt"),
                x,
                z,
                UUID.fromString(ergebnis.getString("eigentuemer")),
                mitbauerVon(id),
                ergebnis.getLong("preis"),
                ergebnis.getObject("erworben_am", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC),
                Art.ausSchluessel(ergebnis.getString("art")),
                // SQL NULL wird hier zu 0, und 0 ist KEIN_ELTERNTEIL. Die eine Stelle,
                // an der sich die Wahl der Null auszahlt.
                ergebnis.getLong("eltern_id"),
                Zustand.ausSchluessel(ergebnis.getString("zustand")),
                // getString gibt fuer SQL NULL ein echtes null zurueck - und genau das
                // ist Parzelle.KEIN_NAME. Keine Umwandlung noetig, und keine erlaubt:
                // Ein leerer Ersatz machte aus jeder Parzelle eine namenlose Freistadt,
                // und der Kompaktkonstruktor wiese sie ab.
                ergebnis.getString("name"),
                // Die Rechte aus der Nebentabelle, genau wie die Mitbauer darueber. Sie
                // zu vergessen waere der lautlose Fehler dieses Bauschritts: Geworfen
                // haette nichts, und jede Stadt haette nach einem Neustart keine
                // Buergerrechte mehr - eingetragen in der Datenbank, wirkungslos im
                // Spiel.
                rechteVon(id));
    }

    /**
     * Die Buergerrechte einer Stadt - aus {@code freistadt_rechte}.
     *
     * <p>Gelesen wird fuer JEDE Zeile, auch fuer Marktstadtparzellen: Eine Abfrage mehr
     * auf einen Hauptschluessel ist billiger als eine Bedingung, die eines Tages auf die
     * falsche Art trifft. Die Antwort ist dort leer, und der Kompaktkonstruktor von
     * {@link Parzelle} nimmt nur das hin.
     *
     * <p>{@code Tat.ausSchluessel} WIRFT bei einem unbekannten Eintrag. Das ist gewollt:
     * Eine Zeile, die ein neueres Freimarken geschrieben hat, stillschweigend zu
     * ueberspringen hiesse, ein Recht wegzulassen, das der Betreiber gegeben hat.
     */
    private Set<Tat> rechteVon(long id) throws SQLException {
        Set<Tat> rechte = new LinkedHashSet<>();
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(
                "SELECT recht FROM freistadt_rechte WHERE stadt_id = ? ORDER BY recht")) {
            anfrage.setLong(1, id);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                while (ergebnis.next()) {
                    rechte.add(Tat.ausSchluessel(ergebnis.getString("recht")));
                }
            }
        }
        return rechte;
    }

    private Set<UUID> mitbauerVon(long id) throws SQLException {
        Set<UUID> namen = new LinkedHashSet<>();
        try (PreparedStatement anfrage = datenbank.verbindung().prepareStatement(
                "SELECT spieler FROM parzellen_mitbauer WHERE parzelle_id = ? "
                        + "ORDER BY spieler")) {
            anfrage.setLong(1, id);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                while (ergebnis.next()) {
                    namen.add(UUID.fromString(ergebnis.getString("spieler")));
                }
            }
        }
        return namen;
    }
}
