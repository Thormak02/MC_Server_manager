package de.freimarken.daten;

import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.Warenprobe;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** Die Freigaben EINES Shops - fuer den Einzelschluessel. */
    private static final String LESEN_FREIGABEN = "SELECT spieler"
            + " FROM kistenshop_freigabe"
            + " WHERE welt = ? AND schild_x = ? AND schild_y = ? AND schild_z = ?"
            + " ORDER BY spieler";

    /**
     * ALLE Freigaben auf einmal - fuer jede Abfrage, die eine LISTE von Shops liefert.
     *
     * <p><b>Eine Abfrage statt einer je Shop</b>, und das ist der Grund, warum es sie
     * gibt: Die naheliegende Fassung - je gelesener Shop ein {@code SELECT} auf seine
     * Freigaben - ist das klassische N+1. Bei hundert Shops waeren das hunderteins
     * Netzrunden beim Fuellen des {@code ZwischenspeicherShops}, und der wird im
     * Hauptfaden gefuellt.
     *
     * <p><b>Warum ohne Einschraenkung auf die gesuchten Shops.</b> Ein {@code JOIN} auf
     * dieselbe Bedingung waere sparsamer, braeuchte aber je Leseart eine eigene Fassung -
     * vier Abschriften derselben Frage, die auseinanderlaufen koennen. Diese Tabelle
     * bleibt klein: eine Zeile je freigegebenem Spieler je Shop, und jede ist eine
     * bewusste Handlung eines Besitzers. Waechst sie je ueber ein paar Tausend Zeilen,
     * gehoert hier der JOIN her - dann aber fuer alle vier Lesearten.
     */
    private static final String LESEN_FREIGABEN_ALLE = "SELECT welt, schild_x, schild_y,"
            + " schild_z, spieler FROM kistenshop_freigabe ORDER BY spieler";

    private static final String FREIGABEN_LOESCHEN = "DELETE FROM kistenshop_freigabe"
            + " WHERE welt = ? AND schild_x = ? AND schild_y = ? AND schild_z = ?";

    private static final String FREIGABE_SCHREIBEN = "INSERT INTO kistenshop_freigabe"
            + " (welt, schild_x, schild_y, schild_z, spieler) VALUES (?, ?, ?, ?, ?)";

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
                if (!ergebnis.next()) {
                    return null;
                }
                // Die Freigaben GEZIELT und nicht ueber die Gesamtabfrage: Hier geht es
                // um genau eine Stelle, und der Hauptschluessel trifft sie unmittelbar.
                return ausZeile(ergebnis, freigabenEines(name, x, y, z));
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
        // EINE Transaktion fuer Shop UND Freigaben, und die Reihenfolge ist Pflicht:
        // Der Fremdschluessel fk_freigabe_shop verlangt die Shopzeile, bevor eine
        // Freigabe darauf zeigen darf. Und zusammen deshalb, weil ein Shop ohne seine
        // Freigaben eine Kiste waere, die den Mitbenutzer nicht mehr kennt - der merkt
        // es erst, wenn er davorsteht.
        //
        // Dieselbe Bauart wie MariaLager.mitbauerSetzen und MariaAbholfach.lege, samt
        // der Erkenntnis von dort: Das rollback() im catch ist die tragende Zeile, nicht
        // das commit() - denn setAutoCommit(true) im finally committet eine offene
        // Transaktion ohnehin.
        try {
            Connection c = datenbank.verbindung();
            boolean vorherAutomatisch = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement anfrage = c.prepareStatement(SCHREIBEN)) {
                    anfrage.setString(1, shop.schildWelt());
                    anfrage.setInt(2, shop.schildX());
                    anfrage.setInt(3, shop.schildY());
                    anfrage.setInt(4, shop.schildZ());
                    anfrage.setInt(5, shop.kisteX());
                    anfrage.setInt(6, shop.kisteY());
                    anfrage.setInt(7, shop.kisteZ());
                    // NULL beim Bundshop - setString nimmt null und schreibt echtes
                    // SQL-NULL.
                    anfrage.setString(8, shop.besitzer() == null ? null
                            : shop.besitzer().toString());
                    anfrage.setInt(9, shop.menge());
                    anfrage.setLong(10, shop.kaufpreis());
                    anfrage.setLong(11, shop.verkaufpreis());
                    anfrage.setString(12, shop.ware().name());
                    anfrage.setString(13, shop.ware().inhalt());
                    anfrage.executeUpdate();
                }
                schreibeFreigabenDurch(c, shop);
                c.commit();
            } catch (SQLException | RuntimeException fehler) {
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
                    // Jetzt steht die Verbindung womoeglich dauerhaft auf
                    // autoCommit=false, und jeder weitere Schreibvorgang landete in
                    // einer Transaktion, die niemand committet - lieber weg damit.
                    datenbank.verwerfen();
                }
            }
        } catch (SQLException fehler) {
            throw new IllegalStateException("Kistenshop nicht schreibbar: "
                    + shop.schluessel(), fehler);
        }
    }

    /**
     * Die Freigaben eines Shops - SETZEND, nicht ergaenzend.
     *
     * <p>Erst alles weg, dann die neuen hinein: Dasselbe Muster wie
     * {@code MariaLager.schreibeMitbauerDurch}, und derselbe Grund - eine ergaenzende
     * Fassung koennte eine Freigabe nie wieder zuruecknehmen. {@code /shop sperren}
     * schreibt darum einfach die kuerzere Liste.
     *
     * <p>Laeuft INNERHALB der Transaktion des Aufrufers und oeffnet keine eigene: Zwei
     * Transaktionen auf einer Verbindung gibt es nicht, und die zweite committete die
     * erste stillschweigend mit.
     */
    private static void schreibeFreigabenDurch(Connection c, Kistenshop shop)
            throws SQLException {
        try (PreparedStatement weg = c.prepareStatement(FREIGABEN_LOESCHEN)) {
            weg.setString(1, shop.schildWelt());
            weg.setInt(2, shop.schildX());
            weg.setInt(3, shop.schildY());
            weg.setInt(4, shop.schildZ());
            weg.executeUpdate();
        }
        if (shop.freigegeben().isEmpty()) {
            return;
        }
        try (PreparedStatement hinein = c.prepareStatement(FREIGABE_SCHREIBEN)) {
            for (UUID wer : shop.freigegeben()) {
                hinein.setString(1, shop.schildWelt());
                hinein.setInt(2, shop.schildX());
                hinein.setInt(3, shop.schildY());
                hinein.setInt(4, shop.schildZ());
                hinein.setString(5, wer.toString());
                hinein.addBatch();
            }
            hinein.executeBatch();
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

    /**
     * Eine Liste von Shops, jeder mit seinen Freigaben.
     *
     * <p>ZWEI Abfragen und nicht eine je Shop - siehe {@link #LESEN_FREIGABEN_ALLE}. Die
     * Reihenfolge ist dabei egal: Die Freigaben werden ueber den Schluessel zugeordnet,
     * nicht ueber die Zeilenfolge.
     */
    private List<Kistenshop> liste(PreparedStatement anfrage) throws SQLException {
        List<Kistenshop> gefunden = new ArrayList<>();
        Map<String, Set<UUID>> freigaben = alleFreigaben();
        try (ResultSet ergebnis = anfrage.executeQuery()) {
            while (ergebnis.next()) {
                String schluessel = Kistenshop.schluesselVon(ergebnis.getString("welt"),
                        ergebnis.getInt("schild_x"), ergebnis.getInt("schild_y"),
                        ergebnis.getInt("schild_z"));
                gefunden.add(ausZeile(ergebnis,
                        freigaben.getOrDefault(schluessel, Set.of())));
            }
        }
        return gefunden;
    }

    /**
     * Alle Freigaben, nach Shopschluessel geordnet.
     *
     * <p>Der Schluessel ist derselbe, den {@link Kistenshop#schluesselVon} baut - und
     * das ist Absicht: Zwei Fassungen desselben Schluessels liefen auseinander, und dann
     * bekaeme ein Shop die Freigaben eines anderen oder gar keine.
     */
    private Map<String, Set<UUID>> alleFreigaben() throws SQLException {
        Map<String, Set<UUID>> karte = new HashMap<>();
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN_FREIGABEN_ALLE);
             ResultSet ergebnis = anfrage.executeQuery()) {
            while (ergebnis.next()) {
                String schluessel = Kistenshop.schluesselVon(ergebnis.getString("welt"),
                        ergebnis.getInt("schild_x"), ergebnis.getInt("schild_y"),
                        ergebnis.getInt("schild_z"));
                karte.computeIfAbsent(schluessel, egal -> new LinkedHashSet<>())
                        .add(UUID.fromString(ergebnis.getString("spieler")));
            }
        }
        return karte;
    }

    /** Die Freigaben genau EINER Stelle. */
    private Set<UUID> freigabenEines(String welt, int x, int y, int z)
            throws SQLException {
        Set<UUID> gefunden = new LinkedHashSet<>();
        try (PreparedStatement anfrage =
                     datenbank.verbindung().prepareStatement(LESEN_FREIGABEN)) {
            setzeStelle(anfrage, welt, x, y, z);
            try (ResultSet ergebnis = anfrage.executeQuery()) {
                while (ergebnis.next()) {
                    gefunden.add(UUID.fromString(ergebnis.getString("spieler")));
                }
            }
        }
        return gefunden;
    }

    private static Kistenshop ausZeile(ResultSet zeile, Set<UUID> freigegeben)
            throws SQLException {
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
                new Warenprobe(zeile.getString("warenname"), zeile.getString("ware")),
                freigegeben);
    }

    private static String weltname(String welt) {
        if (welt == null || welt.isBlank()) {
            throw new IllegalArgumentException("Ohne Welt gibt es keine Stelle");
        }
        return welt.trim();
    }
}
