package de.freimarken.daten;

import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.Warenprobe;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Die Kistenshops in MariaDB - eine Zeile je Schild.
 *
 * <p>Haelt denselben {@code ShopVertrag} wie die Fassung im Arbeitsspeicher.
 *
 * <p><b>Im Betrieb wird diese Fassung fast nie gefragt.</b> Davor liegt
 * {@code ZwischenspeicherShops}, eine vollstaendige Kopie im Arbeitsspeicher, und die
 * beantwortet jede Frage. Diese Klasse wird beim Start einmal fuer {@code alle()}
 * gebraucht und danach nur noch zum Schreiben und Loeschen.
 *
 * <p>Das ist kein Ersatz fuer Sorgfalt an dieser Stelle, sondern ihr Grund: Was hier
 * falsch geschrieben wird, steht nach dem naechsten Neustart in der Karte - und dann
 * handelt ein Schild etwas anderes, als es anzeigt.
 *
 * <p><b>{@code ON DUPLICATE KEY UPDATE} ist hier richtig</b>, anders als bei
 * {@code MariaMiete}. Dort gibt es einen ZWEITEN eindeutigen Schluessel
 * ({@code uq_ladenmiete_mieter}), und {@code ON DUPLICATE KEY} feuert bei jedem von
 * ihnen - ein zweiter Laden desselben Mieters haette dort stillschweigend die Zeile
 * seines ersten geaendert. Diese Tabelle hat nur den Hauptschluessel, also trifft das
 * Update genau die Zeile, die gemeint ist: dasselbe Schild.
 *
 * <p><b>Der Besitzer darf NULL sein</b> - das ist der Bundshop. {@code setString(null)}
 * schreibt ein echtes SQL-NULL, und {@code getString} gibt {@code null} zurueck; die
 * Pruefung darauf ist in {@link #ausZeile} die einzige Stelle, an der diese Fassung mehr
 * tun muss als Werte durchreichen.
 */
public final class MariaShops implements Shopablage {

    private static final String SPALTEN = "welt, schild_x, schild_y, schild_z,"
            + " kiste_x, kiste_y, kiste_z, besitzer, menge, kaufpreis, verkaufpreis,"
            + " warenname, ware";

    private static final String LESEN_SCHILD = "SELECT " + SPALTEN + " FROM kistenshop"
            + " WHERE welt = ? AND schild_x = ? AND schild_y = ? AND schild_z = ?";

    /**
     * Alle Shops an einer Kiste - die Gegenrichtung zum Hauptschluessel.
     *
     * <p>Das {@code ORDER BY} macht die Reihenfolge zur Zusage und nicht zum Ergebnis
     * eines Ausfuehrungsplans. Dass es gemessen wirkungslos sein kann - der Index
     * {@code ix_kistenshop_kiste} liefert die Zeilen ohnehin geordnet -, ist bei
     * {@code MariaMiete.LESEN_ALLE} und {@code MariaAbholfach.LESEN} schon aufgeschrieben
     * und gilt hier genauso.
     */
    private static final String LESEN_KISTE = "SELECT " + SPALTEN + " FROM kistenshop"
            + " WHERE welt = ? AND kiste_x = ? AND kiste_y = ? AND kiste_z = ?"
            + " ORDER BY schild_x, schild_y, schild_z";

    private static final String LESEN_BESITZER = "SELECT " + SPALTEN + " FROM kistenshop"
            + " WHERE besitzer = ? ORDER BY welt, schild_x, schild_y, schild_z";

    private static final String LESEN_ALLE = "SELECT " + SPALTEN + " FROM kistenshop"
            + " ORDER BY welt, schild_x, schild_y, schild_z";

    /**
     * Setzend, mit dem Hauptschluessel - siehe den Kopf der Klasse.
     *
     * <p>Die Spalten der Stelle stehen im UPDATE NICHT, und das ist kein Versehen: Sie
     * sind der Schluessel, also kann ein Update sie nicht aendern. Was sich aendern darf,
     * ist alles andere - ein Spieler, der sein Schild neu beschriftet, bekommt dieselbe
     * Zeile mit neuen Preisen.
     */
    private static final String SCHREIBEN = "INSERT INTO kistenshop (" + SPALTEN + ")"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            + " ON DUPLICATE KEY UPDATE kiste_x = VALUES(kiste_x),"
            + " kiste_y = VALUES(kiste_y), kiste_z = VALUES(kiste_z),"
            + " besitzer = VALUES(besitzer), menge = VALUES(menge),"
            + " kaufpreis = VALUES(kaufpreis), verkaufpreis = VALUES(verkaufpreis),"
            + " warenname = VALUES(warenname), ware = VALUES(ware)";

    private static final String LOESCHEN = "DELETE FROM kistenshop"
            + " WHERE welt = ? AND schild_x = ? AND schild_y = ? AND schild_z = ?";

    private final Datenbank datenbank;

    public MariaShops(Datenbank datenbank) {
        this.datenbank = datenbank;
    }

    @Override
    public Kistenshop anDemSchild(String welt, int x, int y, int z) {
        String name = weltname(welt);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN_SCHILD)) {
            setzeStelle(anfrage, name, x, y, z);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                // Kein Sonderweg und kein Fehler: Die allermeisten Schilder des Servers
                // sind keine Shops.
                return ergebnis.next() ? ausZeile(ergebnis) : null;
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Kistenshop nicht lesbar: " + name + " " + x
                    + "/" + y + "/" + z, fehler);
        }
    }

    @Override
    public List<Kistenshop> anDerKiste(String welt, int x, int y, int z) {
        String name = weltname(welt);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN_KISTE)) {
            setzeStelle(anfrage, name, x, y, z);
            return liste(anfrage);
        } catch (SQLException fehler) {
            throw new IllegalStateException("Kistenshops an der Kiste nicht lesbar: "
                    + name + " " + x + "/" + y + "/" + z, fehler);
        }
    }

    @Override
    public List<Kistenshop> vonSpieler(UUID besitzer) {
        if (besitzer == null) {
            throw new IllegalArgumentException("Ohne Spieler gibt es nichts zu lesen");
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN_BESITZER)) {
            anfrage.setString(1, besitzer.toString());
            return liste(anfrage);
        } catch (SQLException fehler) {
            throw new IllegalStateException("Kistenshops von " + besitzer
                    + " nicht lesbar", fehler);
        }
    }

    @Override
    public List<Kistenshop> alle() {
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN_ALLE)) {
            return liste(anfrage);
        } catch (SQLException fehler) {
            throw new IllegalStateException("Kistenshops nicht lesbar", fehler);
        }
    }

    @Override
    public void schreibe(Kistenshop shop) {
        if (shop == null) {
            throw new IllegalArgumentException("Nichts zu schreiben ist kein Shop");
        }
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(SCHREIBEN)) {
            anfrage.setString(1, shop.schildWelt());
            anfrage.setInt(2, shop.schildX());
            anfrage.setInt(3, shop.schildY());
            anfrage.setInt(4, shop.schildZ());
            anfrage.setInt(5, shop.kisteX());
            anfrage.setInt(6, shop.kisteY());
            anfrage.setInt(7, shop.kisteZ());
            // NULL beim Bundshop - setString nimmt null und schreibt echtes SQL-NULL.
            anfrage.setString(8, shop.besitzer() == null ? null
                    : shop.besitzer().toString());
            anfrage.setInt(9, shop.menge());
            anfrage.setLong(10, shop.kaufpreis());
            anfrage.setLong(11, shop.verkaufpreis());
            anfrage.setString(12, shop.ware().name());
            anfrage.setString(13, shop.ware().inhalt());
            anfrage.executeUpdate();
        } catch (SQLException fehler) {
            throw new IllegalStateException("Kistenshop nicht schreibbar: "
                    + shop.schluessel(), fehler);
        }
    }

    @Override
    public boolean loesche(String welt, int x, int y, int z) {
        String name = weltname(welt);
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LOESCHEN)) {
            setzeStelle(anfrage, name, x, y, z);
            return anfrage.executeUpdate() > 0;
        } catch (SQLException fehler) {
            throw new IllegalStateException("Kistenshop nicht loeschbar: " + name + " "
                    + x + "/" + y + "/" + z, fehler);
        }
    }

    /** Die vier Werte einer Stelle - an EINER Stelle gesetzt, damit keine verrutscht. */
    private static void setzeStelle(PreparedStatement anfrage, String welt, int x, int y,
            int z) throws SQLException {
        anfrage.setString(1, welt);
        anfrage.setInt(2, x);
        anfrage.setInt(3, y);
        anfrage.setInt(4, z);
    }

    private static List<Kistenshop> liste(PreparedStatement anfrage) throws SQLException {
        List<Kistenshop> gefunden = new ArrayList<>();
        try (ResultSet ergebnis = anfrage.executeQuery()) {
            while (ergebnis.next()) {
                gefunden.add(ausZeile(ergebnis));
            }
        }
        return gefunden;
    }

    private static Kistenshop ausZeile(ResultSet zeile) throws SQLException {
        String besitzer = zeile.getString("besitzer");
        return new Kistenshop(zeile.getString("welt"),
                zeile.getInt("schild_x"), zeile.getInt("schild_y"),
                zeile.getInt("schild_z"),
                zeile.getInt("kiste_x"), zeile.getInt("kiste_y"),
                zeile.getInt("kiste_z"),
                // Die EINE Stelle, an der diese Fassung mehr tut als durchreichen: Ein
                // NULL ist der Bundshop und kein fehlender Wert.
                besitzer == null ? null : UUID.fromString(besitzer),
                zeile.getInt("menge"), zeile.getLong("kaufpreis"),
                zeile.getLong("verkaufpreis"),
                new Warenprobe(zeile.getString("warenname"), zeile.getString("ware")));
    }

    private static String weltname(String welt) {
        if (welt == null || welt.isBlank()) {
            throw new IllegalArgumentException("Ohne Welt gibt es keine Stelle");
        }
        return welt.trim();
    }
}
