package de.freimarken.daten;

import de.freimarken.kern.Blockbuchablage;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Blocktat;
import de.freimarken.kern.Blockzeile;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Das Blockbuch in MariaDB - eine Zeile je Tat.
 *
 * <p>Haelt denselben {@code BlockbuchVertrag} wie {@code SpeicherBlockbuch}.
 *
 * <p><b>{@link #schreibe} fuehrt KEINE Transaktion</b>, und das ist die eine bewusste
 * Abweichung von {@link MariaAbholfach#lege} und {@link MariaMiete#schreibe}. Dort geht es
 * um Gegenstaende und Vertraege: Eine halb geschriebene Ladung waere beim zweiten Versuch
 * eine Verdopplung, und ein verdoppelter Diamantblock ist sofort Geld. Hier geht es um ein
 * Protokoll, das NIEMAND wiederholt - der Sammler verwirft seinen Stapel und meldet es.
 * Eine Transaktion, die bei der dreihundertsten von fuenfhundert Zeilen scheitert, wuerfe
 * 299 gute Zeilen weg; ohne sie bleiben sie stehen. Ein Buch soll so viel behalten wie
 * moeglich, und es hat keine Zusage, die ein Teilschreibzug verletzen koennte: kein
 * {@code UNIQUE}, kein Fremdschluessel, keine Summe, die aufgehen muss.
 *
 * <p><b>Jede Lesemethode traegt ihr {@code LIMIT} als Parameter</b>, nicht als Zahl im
 * Text. Dies ist die groesste Tabelle des Projekts, und eine Abfrage ohne Grenze holte
 * Zehntausende Zeilen in den Hauptfaden des Spielservers - dort, wo der Befehl laeuft, der
 * sie anzeigt.
 *
 * <p><b>{@code ORDER BY zeitpunkt DESC, id DESC} - und die id ist kein Beiwerk.</b> Die
 * Spalte ist {@code DATETIME(3)}, also millisekundengenau, und zwanzig Bloecke in einem
 * Tick sind ein normaler Abbau mit Effizienz V. Ohne den Tiebreaker waere die Reihenfolge
 * gleichzeitiger Zeilen beliebig, und eine Abfrage mit Grenze schnitte zweimal
 * verschieden ab. Die beiden Lese-Indizes tragen beide Spalten in dieser Richtung, die
 * Sortierung kostet also nichts.
 *
 * <p><b>Diese Stelle ist allein durch LESEN gesichert, und das ist gemessen</b>
 * (Einspritzung B7 am 09.11.2026): Nimmt man das {@code , id DESC} aus beiden Abfragen,
 * bleibt alles gruen - auch die Probe, die drei Zeilen mit derselben Millisekunde
 * schreibt. Der Grund ist, dass {@code ix_blockbuch_ort} die id als letztes Glied traegt
 * und MariaDB die Zeilen deshalb schon in dieser Reihenfolge liest. Es bleibt trotzdem
 * stehen, denn eine Reihenfolge, die nur aus dem Ausfuehrungsplan folgt, ist keine
 * Zusage: Der naechste Index oder eine Partitionierung kann sie aendern, und dann schnitte
 * dieselbe Abfrage zweimal verschieden ab. Dasselbe gilt und dasselbe wurde gemessen bei
 * {@link MariaAbholfach#fuerSpieler} und {@code MariaMiete.LESEN_ALLE}.
 *
 * <p><b>Das Aufraeumen loescht die AELTESTEN zuerst</b> ({@code ORDER BY zeitpunkt}), und
 * das ist mehr als Ordnung: Reicht der Happen dauerhaft nicht, bliebe sonst gerade der
 * aelteste Bestand fuer immer liegen - also genau das, was die Frist loeschen soll.
 *
 * <p>Auch diese Zeile ist allein durch Lesen gesichert (Einspritzung B10, dasselbe
 * Datum): Ohne sie bleibt jede Probe gruen, weil MariaDB die Zeilen faktisch in der
 * Reihenfolge des Index trifft - und das ist hier die der Einfuegung. Die Begruendung,
 * sie trotzdem zu behalten, ist dieselbe wie oben, und der Schaden waere der groessere:
 * Eine Frist, die bei knappem Happen gerade die aeltesten Zeilen liegen laesst, raeumt
 * nie auf.
 *
 * <p><b>Der Zeitpunkt kommt aus Java und niemals aus der Datenbank</b> - kein
 * {@code UTC_TIMESTAMP(3)} beim Schreiben und kein {@code NOW() - INTERVAL} beim
 * Aufraeumen. Dieselbe Regel wie bei {@link MariaAbholfach}, und beim Aufraeumen waere der
 * Bruch besonders still: Die Frist fragte dann die Uhr und die Zone des
 * Datenbankrechners, und die eingestellte Zone waere stumm wirkungslos - sichtbar erst an
 * Zeilen, die einen Tag zu frueh oder zu spaet verschwinden.
 */
public final class MariaBlockbuch implements Blockbuchablage {

    private static final String SPALTEN =
            "id, zeitpunkt, spieler, welt, x, y, z, tat, gelungen, block";

    private static final String EINFUEGEN = "INSERT INTO blockbuch (zeitpunkt, spieler,"
            + " welt, x, y, z, tat, gelungen, block) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String LESEN_ORT = "SELECT " + SPALTEN + " FROM blockbuch"
            + " WHERE welt = ? AND x = ? AND y = ? AND z = ?"
            + " ORDER BY zeitpunkt DESC, id DESC LIMIT ?";

    private static final String LESEN_SPIELER = "SELECT " + SPALTEN + " FROM blockbuch"
            + " WHERE spieler = ? ORDER BY zeitpunkt DESC, id DESC LIMIT ?";

    private static final String AUFRAEUMEN = "DELETE FROM blockbuch WHERE zeitpunkt < ?"
            + " ORDER BY zeitpunkt LIMIT ?";

    private final Datenbank datenbank;

    public MariaBlockbuch(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    @Override
    public int schreibe(List<Blockzeile> zeilen) {
        if (zeilen == null) {
            throw new IllegalArgumentException("Nichts ist keine Liste von Zeilen - eine"
                    + " LEERE Liste ist erlaubt und tut nichts");
        }
        for (Blockzeile zeile : zeilen) {
            if (zeile == null) {
                throw new IllegalArgumentException("Eine Zeile, die nichts ist, gehoert"
                        + " nicht ins Blockbuch");
            }
        }
        if (zeilen.isEmpty()) {
            // Der haeufigste Fall: Der Takt schlaegt jede Sekunde, und auf einem ruhigen
            // Server ist der Stapel leer. Ohne diese Zeile ginge je Sekunde eine
            // Verbindungspruefung ueber das Netz, fuer nichts.
            return 0;
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(EINFUEGEN)) {
            for (Blockzeile zeile : zeilen) {
                anfrage.setTimestamp(1, Timestamp.from(zeile.zeitpunkt()));
                anfrage.setString(2, zeile.spieler().toString());
                anfrage.setString(3, zeile.ort().welt());
                anfrage.setInt(4, zeile.ort().x());
                anfrage.setInt(5, zeile.ort().y());
                anfrage.setInt(6, zeile.ort().z());
                anfrage.setString(7, zeile.tat().schluessel());
                anfrage.setBoolean(8, zeile.gelungen());
                anfrage.setString(9, zeile.block());
                anfrage.addBatch();
            }
            int[] je = anfrage.executeBatch();
            return gezaehlt(je);
        } catch (SQLException fehler) {
            throw new IllegalStateException("Blockbuch nicht schreibbar ("
                    + zeilen.size() + " Zeilen)", fehler);
        }
    }

    @Override
    public List<Blockzeile> amOrt(Blockort ort, int hoechstens) {
        if (ort == null) {
            throw new IllegalArgumentException("Ohne Ort gibt es keine Frage an das"
                    + " Blockbuch");
        }
        pruefeGrenze(hoechstens);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN_ORT)) {
            anfrage.setString(1, ort.welt());
            anfrage.setInt(2, ort.x());
            anfrage.setInt(3, ort.y());
            anfrage.setInt(4, ort.z());
            anfrage.setInt(5, hoechstens);
            return gelesen(anfrage);
        } catch (SQLException fehler) {
            throw new IllegalStateException("Blockbuch nicht lesbar am Ort " + ort,
                    fehler);
        }
    }

    @Override
    public List<Blockzeile> vonSpieler(UUID spieler, int hoechstens) {
        if (spieler == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es keine Frage an das"
                    + " Blockbuch");
        }
        pruefeGrenze(hoechstens);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN_SPIELER)) {
            anfrage.setString(1, spieler.toString());
            anfrage.setInt(2, hoechstens);
            return gelesen(anfrage);
        } catch (SQLException fehler) {
            throw new IllegalStateException("Blockbuch nicht lesbar fuer Spieler "
                    + spieler, fehler);
        }
    }

    @Override
    public int raeumeAuf(Instant aelterAls, int hoechstens) {
        if (aelterAls == null) {
            throw new IllegalArgumentException("Ohne Grenze wuerde alles geloescht - der"
                    + " Zeitpunkt kommt herein und wird nie hier gerechnet");
        }
        pruefeGrenze(hoechstens);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(AUFRAEUMEN)) {
            anfrage.setTimestamp(1, Timestamp.from(aelterAls));
            anfrage.setInt(2, hoechstens);
            return anfrage.executeUpdate();
        } catch (SQLException fehler) {
            throw new IllegalStateException("Blockbuch nicht aufraeumbar (aelter als "
                    + aelterAls + ", hoechstens " + hoechstens + " Zeilen)", fehler);
        }
    }

    private static List<Blockzeile> gelesen(PreparedStatement anfrage)
            throws SQLException {
        List<Blockzeile> gefunden = new ArrayList<>();
        try (ResultSet ergebnis = anfrage.executeQuery()) {
            while (ergebnis.next()) {
                gefunden.add(ausZeile(ergebnis));
            }
        }
        return gefunden;
    }

    /**
     * Was die Zeile sagt - und ein unbekannter Tatname wirft.
     *
     * <p>Der Wurf ist hier richtig, obwohl er sonst in diesem Projekt die unsichere
     * Richtung waere: In der Spalte steht nur, was {@link Blocktat#schluessel()}
     * geschrieben hat. Ein Wort, das keine Tat ist, kann also nur von Hand hineingekommen
     * sein oder aus einem Jar, das eine Tat kannte, die dieses nicht mehr kennt - und
     * dann ist eine laute Meldung besser als eine Zeile, die stumm aus der Anzeige
     * faellt. Gefangen wird er im Befehl, nicht hier.
     */
    private static Blockzeile ausZeile(ResultSet zeile) throws SQLException {
        String tatname = zeile.getString("tat");
        Blocktat tat = Blocktat.ausSchluessel(tatname);
        if (tat == null) {
            throw new SQLException("Die Zeile " + zeile.getLong("id") + " traegt die Tat"
                    + " \"" + tatname + "\", und die kennt dieses Jar nicht. Erlaubt"
                    + " sind " + Blocktat.erlaubteNamen() + ".");
        }
        return new Blockzeile(zeile.getLong("id"),
                zeile.getTimestamp("zeitpunkt").toInstant(),
                UUID.fromString(zeile.getString("spieler")),
                new Blockort(zeile.getString("welt"), zeile.getInt("x"),
                        zeile.getInt("y"), zeile.getInt("z")),
                tat,
                zeile.getBoolean("gelungen"),
                zeile.getString("block"));
    }

    /**
     * Wie viele Zeilen der Stapel wirklich geschrieben hat.
     *
     * <p>Nicht {@code zeilen.size()}: Ein Treiber darf je Anweisung
     * {@link java.sql.Statement#SUCCESS_NO_INFO} zurueckgeben, und ein
     * {@link java.sql.Statement#EXECUTE_FAILED} ist das, was diese Methode von einer
     * Zusage unterscheidet - ohne Transaktion kann eine einzelne Zeile scheitern,
     * waehrend die uebrigen stehen. Dann soll die Zahl die Wahrheit sagen.
     */
    private static int gezaehlt(int[] je) {
        int geschrieben = 0;
        for (int ergebnis : je) {
            if (ergebnis >= 0 || ergebnis == java.sql.Statement.SUCCESS_NO_INFO) {
                geschrieben++;
            }
        }
        return geschrieben;
    }

    private static void pruefeGrenze(int hoechstens) {
        if (hoechstens < 1) {
            throw new IllegalArgumentException("Eine Grenze von " + hoechstens
                    + " Zeilen ist keine - erwartet wird mindestens 1. Die Grenze ist"
                    + " Pflicht, weil dies die groesste Tabelle des Projekts ist.");
        }
    }
}
