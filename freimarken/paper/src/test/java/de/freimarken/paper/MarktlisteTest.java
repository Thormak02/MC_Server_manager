package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Marktwerte;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser des Abschnitts {@code bundesmarkt:} - ohne Server.
 *
 * <p><b>Diese Datei war am Mittag des 10.11.2026 dreimal so lang.</b> Sie hielt das
 * SORTIMENT: je Material eine Zeile, eine Klage je unbrauchbarem Preis, die Pruefung
 * gegen {@code Material.getMaterial} - und eine Probe, die jeden Ankaufpreis der
 * ausgelieferten Datei gegen den hoechsten LOHN fuer dasselbe Material stellte ("der
 * Markt darf nie mehr zahlen als die Arbeit, sonst ist jeder Block zweimal Geld").
 *
 * <p>All das ist mit der Tafel gefallen: "es kann ja nur ge- und verkauft werden, wofuer
 * es ein Schild gibt." Der Preis steht auf dem Schild, und was dieser Leser noch liest,
 * ist eine Zahl.
 *
 * <p><b>Die verlorene Probe ist aufgeschrieben und nicht vergessen</b> (TODO): Ohne
 * zentrale Liste prueft niemand mehr, ob ein Bundschild mehr zahlt als die Arbeit
 * einbringt. Der Riegel waere beim ANLEGEN eines Schildes zu bauen, nicht hier.
 */
@DisplayName("Bundesmarkt aus der config.yml")
class MarktlisteTest {

    private static final String VOLLSTAENDIG = """
            bundesmarkt:
              tagesdeckel: 5000
            """;

    private static Marktliste.Ergebnis lies(String yaml) {
        return Marktliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    /** Ob irgendeine Klage mit diesem Pfad beginnt. */
    private static boolean klagtUeber(Marktliste.Ergebnis ergebnis, String pfad) {
        return ergebnis.klagen().stream().anyMatch(klage -> klage.startsWith(pfad));
    }

    private static boolean berichtetUeber(Marktliste.Ergebnis ergebnis, String stueck) {
        return ergebnis.bericht().stream().anyMatch(zeile -> zeile.contains(stueck));
    }

    @Test
    @DisplayName("Eine vollstaendige Datei kommt ganz an")
    void vollstaendigKommtAn() {
        var ergebnis = lies(VOLLSTAENDIG);

        assertEquals(5_000L, ergebnis.werte().tagesdeckel());
        assertTrue(ergebnis.werte().gedeckelt());
        assertEquals(java.util.List.of(), ergebnis.klagen(),
                ergebnis.klagen().toString());
    }

    @Test
    @DisplayName("Eine 0 heisst KEIN Deckel - und wird berichtet, nicht beklagt")
    void nullHeisstKeinDeckel() {
        // ENTSCHIEDEN AM 10.11.2026. Bis dahin wurde die 0 VERWORFEN, mit einer Klage,
        // die auf die Ankaufliste verwies ("wer den Ankauf abschalten will, laesst die
        // Liste leer"). Die Liste gibt es nicht mehr.
        //
        // Ein BERICHT und keine Klage: Es ist eine Einstellung. Aber eine, die man lesen
        // soll - der Ankauf des Bundes ist damit eine Geldquelle ohne Grenze, und das
        // ist eine Entscheidung und kein Zustand, in den man rutscht.
        //
        // Die BESTAETIGUNG gehoert dazu: 5.000 auf 0 ist mehr als Faktor 2, und das ist
        // ein Geldwert. Ohne sie bleibt es bei der Vorgabe - diese Probe zeigt beides.
        var ohneBestaetigung = lies("""
                bundesmarkt:
                  tagesdeckel: 0
                """);
        assertEquals(5_000L, ohneBestaetigung.werte().tagesdeckel(),
                "ohne Sprungbestaetigung bleibt es bei der Vorgabe");
        assertTrue(klagtUeber(ohneBestaetigung, Marktwerte.PFAD_TAGESDECKEL),
                ohneBestaetigung.klagen().toString());

        var mitBestaetigung = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    markt-tagesdeckel: "5000 -> 0"
                bundesmarkt:
                  tagesdeckel: 0
                """);
        assertEquals(0L, mitBestaetigung.werte().tagesdeckel());
        assertFalse(mitBestaetigung.werte().gedeckelt(), "0 heisst: kein Deckel");
        assertEquals(java.util.List.of(), mitBestaetigung.klagen(),
                mitBestaetigung.klagen().toString());
        assertTrue(berichtetUeber(mitBestaetigung, "KEIN Tagesdeckel"),
                mitBestaetigung.bericht().toString());
    }

    @Test
    @DisplayName("Eine negative Zahl wird verworfen - die Vorgabe bleibt")
    void negativWirdVerworfen() {
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    markt-tagesdeckel: "5000 -> -100"
                bundesmarkt:
                  tagesdeckel: -100
                """);

        assertEquals(5_000L, ergebnis.werte().tagesdeckel());
        assertTrue(klagtUeber(ergebnis, Marktwerte.PFAD_TAGESDECKEL),
                ergebnis.klagen().toString());
    }

    @Test
    @DisplayName("Eine fehlende Zeile wird berichtet, nicht beklagt")
    void fehlendeZeileWirdBerichtet() {
        // Eine vorhandene config.yml wird beim Update NIE ueberschrieben: Wer von einer
        // aelteren Fassung kommt, hat die Zeile nicht - und dann gilt die Vorgabe, und
        // der Bericht sagt es. Eine Klage wuerde das ganze Neuladen anhalten.
        var ergebnis = lies("""
                bundesmarkt:
                  irgendwas: 1
                """);

        assertEquals(5_000L, ergebnis.werte().tagesdeckel());
        assertEquals(java.util.List.of(), ergebnis.klagen(),
                ergebnis.klagen().toString());
        assertTrue(berichtetUeber(ergebnis, Marktwerte.PFAD_TAGESDECKEL),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Kein Abschnitt heisst: die Vorgaben, und keine Klage")
    void ohneAbschnitt() {
        assertEquals(Marktwerte.VORGABE, Marktliste.ausKonfiguration(null).werte());
        assertEquals(java.util.List.of(),
                Marktliste.ausKonfiguration(null).klagen());
    }

    @Test
    @DisplayName("Die AUSGELIEFERTE Datei nennt den Deckel und ist lesbar")
    void ausgelieferteDateiIstBrauchbar() {
        // Die Datei, die das Plugin mitbringt, muss durch ihren eigenen Leser gehen -
        // sonst ist jede Zusage ueber sie eine Behauptung. Dass das SORTIMENT hier nicht
        // mehr geprueft wird, ist kein Vergessen: Es steht nicht mehr in der Datei.
        try (InputStream strom =
                     Marktliste.class.getResourceAsStream("/config.yml")) {
            assertNotNull(strom, "config.yml muss im Jar liegen");
            var ergebnis = Marktliste.ausKonfiguration(
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(strom, StandardCharsets.UTF_8)));

            assertEquals(java.util.List.of(), ergebnis.klagen(),
                    ergebnis.klagen().toString());
            assertEquals(Marktwerte.VORGABE_TAGESDECKEL,
                    ergebnis.werte().tagesdeckel(),
                    "die ausgelieferte Zahl und die Vorgabe des Programms sind dieselbe"
                            + " - zwei verschiedene waeren zwei Wahrheiten");
            assertTrue(ergebnis.werte().gedeckelt());
        } catch (IOException fehler) {
            throw new AssertionError(fehler);
        }
    }

    @Test
    @DisplayName("Eine ALTE Ankauftafel in der Datei wird beklagt, nicht uebergangen")
    void alteTafelWirdBeklagt() {
        // Eine vorhandene config.yml wird beim Update NIE ueberschrieben: Wer von heute
        // Mittag kommt, hat bundesmarkt.ankauf mit sechsundfuenfzig Zeilen in seiner
        // Datei. Ohne diese Klage sagt ihm niemand, dass sie nicht mehr gelesen wird -
        // er stellt dort Preise ein, wartet auf ihre Wirkung und sucht den Fehler
        // ueberall sonst.
        //
        // Eine KLAGE und kein Bericht: Die Folge ist dieselbe wie bei einer verworfenen
        // Zahl - der Server tut etwas anderes, als in der Datei steht.
        var ergebnis = lies("""
                bundesmarkt:
                  tagesdeckel: 5000
                  ankauf:
                    stone: 20
                """);

        assertEquals(5_000L, ergebnis.werte().tagesdeckel(),
                "die uebrigen Zeilen gelten weiter");
        assertTrue(klagtUeber(ergebnis, "bundesmarkt.ankauf"),
                ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("Schild"),
                "die Klage sagt, WO der Preis jetzt steht: "
                        + ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Die ausgelieferte Datei hat KEIN Sortiment mehr")
    void ausgelieferteDateiOhneSortiment() {
        // Die Gegenrichtung, und sie ist die eigentliche Probe dieses Umbaus: Eine
        // Tafel, die noch in der Datei stuende, wuerde von niemandem mehr gelesen - und
        // ein Betreiber traegt dort Preise ein, die nie wirken. Dasselbe Muster wie
        // Shopliste.nenneAlte: Was es nicht mehr gibt, darf auch nicht mehr dastehen.
        try (InputStream strom =
                     Marktliste.class.getResourceAsStream("/config.yml")) {
            assertNotNull(strom);
            var datei = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));

            assertFalse(datei.contains("bundesmarkt.ankauf", true),
                    "bundesmarkt.ankauf ist am 10.11.2026 gefallen - wer es wieder"
                            + " eintraegt, traegt eine Liste ein, die niemand liest");
        } catch (IOException fehler) {
            throw new AssertionError(fehler);
        }
    }
}
