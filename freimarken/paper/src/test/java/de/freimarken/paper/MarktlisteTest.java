package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Marktwerte;
import de.freimarken.kern.Verkaufsregel;
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
 * <p>Moeglich, weil {@code YamlConfiguration} reine Dateiarbeit ist und der Leser mit
 * {@link org.bukkit.Material#getMaterial(String)} statt {@code matchMaterial} prueft.
 *
 * <p><b>Die tragende Probe ist {@link #ausgelieferteDateiIstBrauchbar()}:</b> Das
 * Sortiment ist der eine Wert dieses Schrittes ohne Rueckfall im Programm. Ist es kaputt,
 * kauft der Bund nichts - und das faellt erst auf, wenn ein Spieler mit vollem Inventar
 * vor dem Markt steht.
 */
@DisplayName("Bundesmarkt aus der config.yml")
class MarktlisteTest {

    private static final String VOLLSTAENDIG = """
            bundesmarkt:
              tagesdeckel: 5000
              ankauf:
                cobblestone: 10
                stone: 20
                diamond: 1100
            """;

    private static Marktliste.Ergebnis lies(String yaml) {
        return Marktliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    /** Ob irgendeine Klage mit diesem Pfad beginnt. */
    private static boolean klagtUeber(Marktliste.Ergebnis ergebnis, String pfad) {
        return ergebnis.klagen().stream().anyMatch(klage -> klage.startsWith(pfad));
    }

    @Test
    @DisplayName("Eine vollstaendige Datei kommt ganz an")
    void vollstaendigKommtAn() {
        var ergebnis = lies(VOLLSTAENDIG);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(5_000L, ergebnis.werte().tagesdeckel());
        assertEquals(3, ergebnis.werte().ankauf().anzahl());
        assertEquals(20L, ergebnis.werte().ankauf().pfennige("stone"));
        // Die REIHENFOLGE der Datei - sie ist die Reihenfolge in /markt.
        assertEquals(java.util.List.of("cobblestone", "stone", "diamond"),
                ergebnis.werte().ankauf().materialien());
    }

    @Test
    @DisplayName("Die Zeile zum Sortiment ist ein GANZER Satz")
    void berichtzeileIstEinGanzerSatz() {
        var ergebnis = lies(VOLLSTAENDIG);

        // Der Wortlaut, nicht die Zahl der Zeilen: Diese Zeile hiess einmal
        // "... im Sortiment, Tagesdeckel je Spieler." - ein Satz, der seine Zahl nie
        // bekam, weil diese Stelle den Deckel nicht liest. Eine Zaehlung der
        // Berichtzeilen hat das nicht gesehen, ein Startlauf schon.
        assertTrue(ergebnis.bericht().contains(
                        "Bundesmarkt: 3 Materialien im Sortiment."),
                ergebnis.bericht().toString());
        for (String zeile : ergebnis.bericht()) {
            assertFalse(zeile.contains("Tagesdeckel")
                            && zeile.chars().noneMatch(Character::isDigit),
                    "nennt den Tagesdeckel ohne Zahl: " + zeile);
        }
    }

    @Test
    @DisplayName("Ohne Konfiguration gilt die Vorgabe - und das Sortiment ist leer")
    void ohneKonfiguration() {
        var ergebnis = Marktliste.ausKonfiguration(null);

        assertEquals(Marktwerte.VORGABE, ergebnis.werte());
        assertTrue(ergebnis.klagen().isEmpty());
        assertTrue(ergebnis.werte().ankauf().leer());
    }

    @Test
    @DisplayName("Ein fehlendes Sortiment ist ein BERICHT und keine Klage")
    void fehlendesSortimentIstKeineKlage() {
        // Der Unterschied entscheidet, ob ein Server neu laden kann: Eine Klage
        // verhindert das ganze Neuladen, und eine vorhandene config.yml wird beim Update
        // NIE ueberschrieben. Gefahrlos ist es, weil ohne Liste NICHTS gekauft wird.
        var ergebnis = lies("""
                bundesmarkt:
                  tagesdeckel: 5000
                """);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertTrue(ergebnis.werte().ankauf().leer());
        assertTrue(ergebnis.bericht().stream().anyMatch(zeile ->
                        zeile.contains(Marktwerte.PFAD_ANKAUF)
                                && zeile.contains("NICHTS")),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Ein Deckel von 0 gilt nicht - abschalten geht ueber das Sortiment")
    void deckelNullGiltNicht() {
        for (String getippt : new String[] {"0", "-1"}) {
            var ergebnis = lies(VOLLSTAENDIG.replace("tagesdeckel: 5000",
                    "tagesdeckel: " + getippt));

            assertTrue(klagtUeber(ergebnis, Marktwerte.PFAD_TAGESDECKEL),
                    getippt + " muss beklagt werden: " + ergebnis.klagen());
            assertEquals(Marktwerte.VORGABE_TAGESDECKEL, ergebnis.werte().tagesdeckel());
            // Das Sortiment bleibt stehen: Ein kaputter Deckel ist kein Grund, es
            // wegzuwerfen.
            assertEquals(3, ergebnis.werte().ankauf().anzahl());
        }
    }

    @Test
    @DisplayName("Ein Deckelsprung um mehr als Faktor 2 braucht die Bestaetigung")
    void grosserSprungBrauchtBestaetigung() {
        var ohne = lies(VOLLSTAENDIG.replace("tagesdeckel: 5000",
                "tagesdeckel: 50000"));
        assertTrue(klagtUeber(ohne, Marktwerte.PFAD_TAGESDECKEL),
                ohne.klagen().toString());
        assertEquals(Marktwerte.VORGABE_TAGESDECKEL, ohne.werte().tagesdeckel());

        var mit = lies(VOLLSTAENDIG.replace("tagesdeckel: 5000",
                "tagesdeckel: 50000") + """
                wirtschaft:
                  sprung-bestaetigt:
                    markt-tagesdeckel: "5000 -> 50000"
                """);
        assertTrue(mit.klagen().isEmpty(), mit.klagen().toString());
        assertEquals(50_000L, mit.werte().tagesdeckel());
    }

    @Test
    @DisplayName("Ein unbekanntes Material wird mit seinem Namen beklagt")
    void unbekanntesMaterial() {
        var ergebnis = lies(VOLLSTAENDIG.replace("stone: 20", "stoen: 20"));

        assertTrue(klagtUeber(ergebnis, Marktwerte.PFAD_ANKAUF + ".stoen"),
                ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("\"stoen\""),
                ergebnis.klagen().get(0));
        assertEquals(2, ergebnis.werte().ankauf().anzahl(),
                "eine kaputte Zeile laesst die uebrigen stehen");
    }

    @Test
    @DisplayName("Ein Preis mit Nachkommastellen wird beklagt")
    void kommazahlWirdBeklagt() {
        // Der haeufigste Vertipper bei Geld in YAML: 1.000 ist dort KEINE Tausend,
        // sondern eine Eins.
        for (String getippt : new String[] {"10.5", "1.000", "nein", "true"}) {
            var ergebnis = lies(VOLLSTAENDIG.replace("stone: 20", "stone: " + getippt));

            assertTrue(klagtUeber(ergebnis, Marktwerte.PFAD_ANKAUF + ".stone"),
                    getippt + " muss beklagt werden: " + ergebnis.klagen());
            assertTrue(ergebnis.klagen().get(0).contains("PFENNIGEN"),
                    ergebnis.klagen().get(0));
        }
    }

    @Test
    @DisplayName("Ein Preis unter einem Pfennig wird beklagt")
    void preisUnterEinemPfennig() {
        // Unter 1 ist kein Ankauf, sondern eine Annahme - und was der Bund nicht kaufen
        // soll, steht gar nicht in der Liste.
        for (String getippt : new String[] {"0", "-20"}) {
            var ergebnis = lies(VOLLSTAENDIG.replace("stone: 20", "stone: " + getippt));

            assertTrue(klagtUeber(ergebnis, Marktwerte.PFAD_ANKAUF + ".stone"),
                    getippt + " muss beklagt werden: " + ergebnis.klagen());
            assertEquals(2, ergebnis.werte().ankauf().anzahl());
        }
    }

    @Test
    @DisplayName("Ein Preis ueber der Ueberlaufschranke wird beklagt")
    void preisUeberDerSchranke() {
        // Die Schranke ist gerechnet und nicht gewaehlt: Bei einem vollen Inventar laeuft
        // die Rechnung darueber ueber, und ein uebergelaufener Betrag ist NEGATIV - also
        // eine Abbuchung auf dem Weg einer Einnahme.
        var ergebnis = lies(VOLLSTAENDIG.replace("stone: 20",
                "stone: " + (Verkaufsregel.HOECHSTER_PREIS + 1L)));

        assertTrue(klagtUeber(ergebnis, Marktwerte.PFAD_ANKAUF + ".stone"),
                ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("NEGATIV"),
                ergebnis.klagen().get(0));
        assertEquals(2, ergebnis.werte().ankauf().anzahl());
    }

    @Test
    @DisplayName("Fehlende Marktzeilen werden gezaehlt und genannt")
    void fehlendeZeilenWerdenGenannt() {
        var ergebnis = lies("datenbank:\n  url: \"\"\n");

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertTrue(ergebnis.bericht().stream().anyMatch(zeile ->
                        zeile.startsWith("2 von 2 Marktzeilen")),
                ergebnis.bericht().toString());
        for (String pfad : new String[] {Marktwerte.PFAD_TAGESDECKEL,
                                         Marktwerte.PFAD_ANKAUF}) {
            assertTrue(ergebnis.bericht().toString().contains(pfad), pfad);
        }
    }

    @Test
    @DisplayName("Die MITGELIEFERTE config.yml traegt ein brauchbares Sortiment")
    void ausgelieferteDateiIstBrauchbar() {
        try (InputStream strom =
                     Marktliste.class.getResourceAsStream("/config.yml")) {
            assertNotNull(strom, "die config.yml muss im Jar liegen");
            var datei = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));

            var ergebnis = Marktliste.ausKonfiguration(datei);

            assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
            assertFalse(ergebnis.werte().ankauf().leer(),
                    "ohne Sortiment kauft der Bund nichts");
            assertEquals(56, ergebnis.werte().ankauf().anzahl(),
                    "sechsundfuenfzig Materialien - jeder Preis liegt unter dem, was die"
                            + " Arbeit einbringt");
            assertEquals(Marktwerte.VORGABE_TAGESDECKEL,
                    ergebnis.werte().tagesdeckel());
            assertTrue(ergebnis.bericht().stream().noneMatch(zeile ->
                            zeile.contains("stehen nicht in deiner config.yml")),
                    "die ausgelieferte Datei darf keine Marktzeile schulden: "
                            + ergebnis.bericht());
        } catch (IOException fehler) {
            throw new AssertionError(fehler);
        }
    }

    @Test
    @DisplayName("Der Bund zahlt WENIGER als die Arbeit einbringt - sonst zahlt ein Block zweimal")
    void marktZahltWenigerAlsDieArbeit() {
        // Die tragende Kalibrierung dieses Schrittes, und sie ist mit einer Probe zu
        // halten: Der Markt ist die VERWERTUNG des Erarbeiteten. Zahlte er mehr als der
        // Lohn, waere jeder abgebaute Block zweimal Geld - einmal beim Abbauen, einmal
        // beim Verkaufen -, und die Spanne von 2.000 bis 5.000 Mark je Stunde waere
        // Papier.
        //
        // Geprueft an den Materialien, die in BEIDEN Listen stehen.
        try (InputStream strom =
                     Marktliste.class.getResourceAsStream("/config.yml")) {
            assertNotNull(strom);
            var datei = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));
            var markt = Marktliste.ausKonfiguration(datei).werte().ankauf();
            var berufe = Berufsliste.listeAus(datei.getConfigurationSection(
                    de.freimarken.kern.Berufswerte.PFAD_LISTE));
            var loehne = Verdienstliste.ausKonfiguration(datei, berufe.verzeichnis())
                    .werte().loehne();

            java.util.List<String> zuTeuer = new java.util.ArrayList<>();
            int verglichen = 0;
            for (String material : markt.materialien()) {
                long hoechsterLohn = 0L;
                for (de.freimarken.kern.Taetigkeit taetigkeit
                        : de.freimarken.kern.Taetigkeit.values()) {
                    for (String beruf : berufe.verzeichnis().berufe()) {
                        hoechsterLohn = Math.max(hoechsterLohn,
                                loehne.pfennige(beruf, taetigkeit, material));
                    }
                }
                if (hoechsterLohn == 0L) {
                    continue;
                }
                verglichen++;
                if (markt.pfennige(material) >= hoechsterLohn) {
                    zuTeuer.add(material + ": Markt " + markt.pfennige(material)
                            + " >= Lohn " + hoechsterLohn);
                }
            }

            assertTrue(verglichen > 0, "ohne gemeinsame Materialien prueft diese Probe"
                    + " nichts - dann ist sie selbst der Fehler");
            assertEquals(java.util.List.of(), zuTeuer,
                    "diese Materialien bringen am Markt mehr als die Arbeit");
        } catch (IOException fehler) {
            throw new AssertionError(fehler);
        }
    }
}
