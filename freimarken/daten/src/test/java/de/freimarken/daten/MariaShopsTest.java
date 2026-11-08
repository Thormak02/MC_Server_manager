package de.freimarken.daten;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.ShopVertrag;
import de.freimarken.kern.Warenprobe;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derselbe Shopvertrag wie im Arbeitsspeicher - diesmal gegen die echte Datenbank.
 *
 * <p>Ohne Zugangsdaten werden die Tests uebersprungen; sind sie da und die Datenbank
 * antwortet nicht, scheitern sie. Gleiche Linie wie bei {@link MariaMieteTest}.
 *
 * <p>Die Proben hier oben drauf pruefen, was nur die Datenbank beweisen kann: dass der
 * Bundshop ein echtes SQL-NULL ist, dass der Hauptschluessel die Schildstelle traegt,
 * dass das Update genau eine Zeile trifft, und dass eine lange Warenprobe nicht
 * abgeschnitten wird.
 */
@DisplayName("Kistenshops in MariaDB")
class MariaShopsTest extends ShopVertrag {

    private static Datenbank datenbank;
    private static MariaShops shops;

    @BeforeAll
    static void verbinden() throws SQLException {
        String url = System.getProperty("freimarken.db.url", "");
        Assumptions.assumeFalse(url.isBlank(),
                "Keine Zugangsdaten - data/freimarken/datenbank.properties fehlt. "
                        + "Erst skripte/mariadb_einrichten.bat ausfuehren.");

        datenbank = new Datenbank(url,
                System.getProperty("freimarken.db.benutzer"),
                System.getProperty("freimarken.db.passwort"));
        datenbank.migriere();
        shops = new MariaShops(datenbank);
    }

    @AfterAll
    static void trennen() {
        if (datenbank != null) {
            datenbank.close();
        }
    }

    @BeforeEach
    void leeren() throws SQLException {
        datenbank.leereAlles();
    }

    @Override
    protected Shopablage ablage() {
        return shops;
    }

    @Test
    @DisplayName("Ein Bundshop steht als echtes NULL in der Spalte")
    void bundshopIstEchtesNull() throws SQLException {
        // Nicht ein leerer Text und nicht eine Null-UUID: Beides waere ein Besitzer, den
        // es nicht gibt - und vonSpieler faende ihn. Geprueft wird am rohen SQL, weil nur
        // dort der Unterschied zwischen NULL und "" zu sehen ist.
        shops.schreibe(shop(60, null, "Bundes"));

        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis = anweisung.executeQuery(
                     "SELECT besitzer FROM kistenshop WHERE schild_x = 60")) {
            assertTrue(ergebnis.next(), "die Zeile muss da sein");
            assertNull(ergebnis.getString("besitzer"));
        }
        assertTrue(shops.anDemSchild(WELT, 60, 71, 6).demBund());
    }

    @Test
    @DisplayName("Der Hauptschluessel ist die SCHILDSTELLE - das Update trifft eine Zeile")
    void schluesselIstDieSchildstelle() throws SQLException {
        // ON DUPLICATE KEY UPDATE ist hier richtig, anders als bei MariaMiete: Diese
        // Tabelle hat nur den Hauptschluessel, also trifft das Update genau die Zeile,
        // die gemeint ist. Geprueft wird die ZEILENZAHL am rohen SQL - ueber die
        // Shopablage waere eine zweite Zeile mit demselben Schild nicht zu sehen.
        UUID anna = UUID.randomUUID();
        shops.schreibe(shop(60, anna, "alt"));
        shops.schreibe(shop(60, anna, "neu"));

        assertEquals(1, zeilen(), "eine Zeile, nicht zwei");
        assertEquals("neu", shops.anDemSchild(WELT, 60, 71, 6).ware().name());
    }

    @Test
    @DisplayName("Das Update zieht die KISTE mit, wenn sie sich geaendert hat")
    void updateZiehtDieKisteMit() {
        // Der Fall, in dem die Spalten der Stelle und die der Kiste auseinandergehen:
        // Dasselbe Schild, eine andere Kiste. Steht kiste_x nicht im UPDATE, haengt der
        // Shop danach an der alten - und an der neuen greift kein Trichterschutz, obwohl
        // dort die Ware liegt.
        UUID anna = UUID.randomUUID();
        shops.schreibe(new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, anna, 16, 100L, 80L,
                ware("Stein")));

        shops.schreibe(new Kistenshop(WELT, 60, 71, 6, 60, 70, 6, anna, 16, 100L, 80L,
                ware("Stein")));

        assertEquals(60, shops.anDemSchild(WELT, 60, 71, 6).kisteX());
        assertEquals(70, shops.anDemSchild(WELT, 60, 71, 6).kisteY(),
                "die Kiste liegt jetzt UNTER dem Schild");
        assertTrue(shops.anDerKiste(WELT, 61, 71, 6).isEmpty(),
                "und an der alten Stelle haengt nichts mehr");
    }

    @Test
    @DisplayName("Eine Warenprobe mit 100.000 Zeichen kommt vollstaendig zurueck")
    void langeWarenprobeWirdNichtAbgeschnitten() {
        // Die Spalte ist MEDIUMTEXT und nicht TEXT, und das ist die Folge der
        // Entscheidung vom 08.11.2026: Verzauberte Ausruestung soll handelbar sein. Ein
        // TEXT haelt 64 KB und MariaDB SCHNEIDET darueber ab, statt zu werfen - und eine
        // abgeschnittene Warenprobe ist eine Ware, die kein Kunde bekommt, weil der
        // Vergleich mit dem Kisteninhalt immer fehlschlaegt.
        String inhalt = "AbCd+/=".repeat(15_000);
        assertTrue(inhalt.length() > 65_535, "muss ueber dem TEXT-Rand liegen: "
                + inhalt.length());
        shops.schreibe(new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, UUID.randomUUID(), 1,
                5_000L, 0L, new Warenprobe("Spitzhacke", inhalt)));

        assertEquals(inhalt, shops.anDemSchild(WELT, 60, 71, 6).ware().inhalt());
    }

    @Test
    @DisplayName("Zwei Welten mit denselben Koordinaten sind zwei Zeilen")
    void weltGehoertZumSchluessel() throws SQLException {
        // Die Welt steht im Hauptschluessel. Ohne sie waere ein Shop in Altfurt auch
        // einer in der Marktstadt - und ein Trichter dort zoege aus einer Kiste, die es
        // nicht gibt.
        UUID anna = UUID.randomUUID();
        shops.schreibe(shop(60, anna, "altfurt"));
        shops.schreibe(new Kistenshop("freimarken:marktstadt", 60, 71, 6, 61, 71, 6,
                anna, 16, 100L, 80L, ware("marktstadt")));

        assertEquals(2, zeilen());
        assertEquals("altfurt", shops.anDemSchild(WELT, 60, 71, 6).ware().name());
        assertEquals("marktstadt",
                shops.anDemSchild("freimarken:marktstadt", 60, 71, 6).ware().name());
    }

    @Test
    @DisplayName("Negative Koordinaten gehen durch - die halbe Welt liegt dort")
    void negativeKoordinaten() {
        // Eine Selbstverstaendlichkeit, die eine Probe braucht: Ein Shop in der
        // Marktstadt liegt mit hoher Wahrscheinlichkeit bei negativen Koordinaten, und
        // eine Spalte oder ein Schluessel, der nur mit positiven umgeht, faellt erst
        // dort auf.
        UUID anna = UUID.randomUUID();
        shops.schreibe(new Kistenshop(WELT, -1_200, 64, -980, -1_200, 63, -980, anna, 16,
                100L, 80L, ware("tief")));

        Kistenshop zurueck = shops.anDemSchild(WELT, -1_200, 64, -980);
        assertNotNull(zurueck);
        assertEquals(-980, zurueck.schildZ());
        assertEquals(63, zurueck.kisteY());
        assertEquals(1, shops.anDerKiste(WELT, -1_200, 63, -980).size());
    }

    @Test
    @DisplayName("leereAlles raeumt die Shops mit weg")
    void leereAllesRaeumtAuf() throws SQLException {
        // Ohne diese Zeile in leereAlles wanderten Shops von einem Test in den naechsten,
        // und weil die Schildstelle der Hauptschluessel ist, waere die Folge ein harter
        // Wurf beim naechsten Anlegen an derselben Stelle.
        shops.schreibe(shop(60, UUID.randomUUID(), "weg"));

        datenbank.leereAlles();

        assertEquals(0, zeilen());
        assertNull(shops.anDemSchild(WELT, 60, 71, 6));
    }

    private static int zeilen() throws SQLException {
        try (Statement anweisung = datenbank.verbindung().createStatement();
             ResultSet ergebnis =
                     anweisung.executeQuery("SELECT COUNT(*) FROM kistenshop")) {
            ergebnis.next();
            return ergebnis.getInt(1);
        }
    }
}
