package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Ladeneinheiten aus der {@code config.yml} gelesen.
 *
 * <p>Dieselbe Bauart wie {@link MarktlisteTest}, {@link MesslisteTest} und
 * {@link BonuslisteTest}: Eine kaputte Zeile wird beklagt und die uebrigen gelten weiter,
 * und am Ende wird die MITGELIEFERTE Datei selbst gelesen.
 *
 * <p>Die Kanten der Welten kommen hereingegeben, nicht gelesen - sie stehen im Abschnitt
 * {@code welten:}, den {@link Weltenliste} liest. Hier wird Altfurt mit seiner
 * ausgelieferten Kante 800 nachgestellt.
 */
@DisplayName("Die Ladeneinheiten aus der config.yml")
class LadenlisteTest {

    /** Altfurt mit der ausgelieferten Kante, und eine Welt ohne Grenze. */
    private static final Map<String, Integer> KANTEN =
            Map.of("altfurt", 800, "ohnegrenze", 0);

    private static final String VOLLSTAENDIG = """
            laeden:
              welt: altfurt
              einheiten:
                halle-1:
                  von: [60, 71, 4]
                  bis: [64, 75, 8]
                  truhe: [62, 71, 6]
                halle-2:
                  von: [66, 71, 4]
                  bis: [70, 75, 8]
                  truhe: [68, 71, 6]
            """;

    private static Ladenliste.Ergebnis lies(String yaml) {
        return Ladenliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)), KANTEN);
    }

    @Test
    @DisplayName("Eine vollstaendige Datei kommt ganz an")
    void vollstaendigKommtAn() {
        var ergebnis = lies(VOLLSTAENDIG);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals("altfurt", ergebnis.welt());
        assertEquals(2, ergebnis.verzeichnis().anzahl());
        // Die REIHENFOLGE der Datei - sie ist die Reihenfolge in /laden liste.
        assertEquals(List.of("halle-1", "halle-2"), ergebnis.verzeichnis().namen());
        assertEquals("62/71/6",
                ergebnis.verzeichnis().mitNamen("halle-1").truheAlsText());
    }

    @Test
    @DisplayName("Ohne Konfiguration gibt es keine Einheiten und keine Klage")
    void ohneKonfiguration() {
        var ergebnis = Ladenliste.ausKonfiguration(null, KANTEN);

        assertEquals(0, ergebnis.verzeichnis().anzahl());
        assertTrue(ergebnis.klagen().isEmpty());
        assertEquals(Ladenliste.VORGABE_WELT, ergebnis.welt());
    }

    @Test
    @DisplayName("Ein fehlender Abschnitt ist ein BERICHT und keine Klage")
    void fehlenderAbschnitt() {
        // Die Markthalle ist von Hand zu bauen. Bis dahin ist eine leere Liste der
        // richtige Zustand - keine Fehleingabe.
        var ergebnis = lies("wirtschaft:" + System.lineSeparator() + "  grundpreis: 25000");

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(0, ergebnis.verzeichnis().anzahl());
        assertTrue(ergebnis.bericht().stream()
                        .anyMatch(zeile -> zeile.contains("von Hand zu bauen")),
                "der Bericht muss sagen, WARUM es keine gibt: " + ergebnis.bericht());
    }

    @Test
    @DisplayName("Zwei Einheiten, die sich Bloecke teilen, werden BEIDE verworfen")
    void ueberschneidungVerwirftBeide() {
        // Die tragende Zusage: Welche gilt, entschiede sonst die Reihenfolge der Datei -
        // und dann koennte ein Mieter in der Truhe seines Nachbarn stehen und waere dort
        // geschuetzt. Dieselbe Antwort wie bei den Toren.
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten:
                    halle-1:
                      von: [60, 71, 4]
                      bis: [64, 75, 8]
                      truhe: [62, 71, 6]
                    halle-2:
                      von: [63, 71, 4]
                      bis: [67, 75, 8]
                      truhe: [65, 71, 6]
                """);

        assertTrue(klageNennt(ergebnis, "Bloecke gemeinsam"),
                ergebnis.klagen().toString());
        assertEquals(0, ergebnis.verzeichnis().anzahl(),
                "BEIDE weg - nicht die zweite, denn welche die richtige ist, weiss"
                        + " niemand");
    }

    @Test
    @DisplayName("Eine dritte, saubere Einheit ueberlebt die Ueberschneidung der anderen")
    void dritteUeberlebt() {
        // Eine kaputte Einheit kostet nur sich selbst. Ohne diese Probe koennte die
        // Ueberschneidungspruefung alles wegwerfen und bliebe gruen.
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten:
                    halle-1:
                      von: [60, 71, 4]
                      bis: [64, 75, 8]
                      truhe: [62, 71, 6]
                    halle-2:
                      von: [63, 71, 4]
                      bis: [67, 75, 8]
                      truhe: [65, 71, 6]
                    halle-3:
                      von: [80, 71, 4]
                      bis: [84, 75, 8]
                      truhe: [82, 71, 6]
                """);

        assertEquals(List.of("halle-3"), ergebnis.verzeichnis().namen());
    }

    @Test
    @DisplayName("Eine Einheit hinter der Weltgrenze wird verworfen")
    void hinterDerGrenze() {
        // Altfurt hat die Kante 800 um 0/0, also von -400 bis 400. Dort kaeme niemand
        // hin - und der Stand fehlte stumm.
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten:
                    weitweg:
                      von: [5000, 71, 4]
                      bis: [5004, 75, 8]
                      truhe: [5002, 71, 6]
                """);

        assertTrue(klageNennt(ergebnis, "ausserhalb der Grenze"),
                ergebnis.klagen().toString());
        assertEquals(0, ergebnis.verzeichnis().anzahl());
    }

    @Test
    @DisplayName("In einer Welt OHNE Grenze wird die Lage nicht geprueft")
    void ohneGrenzeKeinePruefung() {
        // Eine Welt ohne eingetragene Kante hat keine Grenze, an der etwas liegen
        // koennte. Eine Pruefung dort waere eine gegen nichts.
        var ergebnis = lies("""
                laeden:
                  welt: ohnegrenze
                  einheiten:
                    weitweg:
                      von: [5000, 71, 4]
                      bis: [5004, 75, 8]
                      truhe: [5002, 71, 6]
                """);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(1, ergebnis.verzeichnis().anzahl());
    }

    @Test
    @DisplayName("Eine Welt, die nicht in welten: steht, laesst KEINE Einheit gelten")
    void unbekannteWelt() {
        // Die Einheiten haetten keinen Ort. Besser gar keine als zwanzig in einer Welt,
        // die es nicht gibt - dort waere auch der Truhenschutz nirgends.
        var ergebnis = lies("""
                laeden:
                  welt: wolkenwerder
                  einheiten:
                    halle-1:
                      von: [60, 71, 4]
                      bis: [64, 75, 8]
                      truhe: [62, 71, 6]
                """);

        assertTrue(klageNennt(ergebnis, "steht nicht in welten"),
                ergebnis.klagen().toString());
        assertEquals(0, ergebnis.verzeichnis().anzahl());
    }

    @Test
    @DisplayName("Eine Truhe ausserhalb ihrer Einheit wird beklagt, der Rest gilt")
    void truheDaneben() {
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten:
                    kaputt:
                      von: [60, 71, 4]
                      bis: [64, 75, 8]
                      truhe: [90, 71, 6]
                    gut:
                      von: [20, 71, 4]
                      bis: [24, 75, 8]
                      truhe: [22, 71, 6]
                """);

        assertTrue(klageNennt(ergebnis, "ungeschuetzt"), ergebnis.klagen().toString());
        assertEquals(List.of("gut"), ergebnis.verzeichnis().namen(),
                "eine kaputte Einheit kostet nur sich selbst");
    }

    @Test
    @DisplayName("Eine zu grosse Einheit wird beklagt")
    void zuGross() {
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten:
                    riesig:
                      von: [0, 0, 0]
                      bis: [100, 100, 100]
                      truhe: [50, 50, 50]
                """);

        assertTrue(klageNennt(ergebnis, "Stadtteil"), ergebnis.klagen().toString());
        assertEquals(0, ergebnis.verzeichnis().anzahl());
    }

    @Test
    @DisplayName("Eine fehlende Zeile nennt ihren Namen - und nimmt nur ihre Einheit mit")
    void fehlendeZeile() {
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten:
                    ohne-truhe:
                      von: [60, 71, 4]
                      bis: [64, 75, 8]
                    gut:
                      von: [20, 71, 4]
                      bis: [24, 75, 8]
                      truhe: [22, 71, 6]
                """);

        assertTrue(klageNennt(ergebnis, "ohne-truhe.truhe fehlt"),
                ergebnis.klagen().toString());
        assertEquals(List.of("gut"), ergebnis.verzeichnis().namen());
    }

    @Test
    @DisplayName("Zwei statt drei Zahlen werden beklagt")
    void zuWenigZahlen() {
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten:
                    halbe-ecke:
                      von: [60, 71]
                      bis: [64, 75, 8]
                      truhe: [62, 71, 6]
                """);

        assertTrue(klageNennt(ergebnis, "DREI Zahlen"), ergebnis.klagen().toString());
        assertEquals(0, ergebnis.verzeichnis().anzahl());
    }

    @Test
    @DisplayName("Eine Kommazahl in einer Ecke wird beklagt - mit der STELLE")
    void kommazahlInDerEcke() {
        // YAML macht aus 1.000 eine 1.0, und die ist ganzzahlig - gemeint waren 1000.
        // Dass die Klage die STELLE nennt, erspart dem Betreiber das Zaehlen.
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten:
                    schief:
                      von: [60, 71.5, 4]
                      bis: [64, 75, 8]
                      truhe: [62, 71, 6]
                """);

        assertTrue(klageNennt(ergebnis, "an Stelle 2"), ergebnis.klagen().toString());
        assertEquals(0, ergebnis.verzeichnis().anzahl());
    }

    @Test
    @DisplayName("Ein Name mit Leerzeichen wird beklagt - er stuende auch im Befehl")
    void nameMitLeerzeichen() {
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten:
                    "halle 1":
                      von: [60, 71, 4]
                      bis: [64, 75, 8]
                      truhe: [62, 71, 6]
                """);

        assertTrue(klageNennt(ergebnis, "/laden mieten"),
                "die Klage muss sagen, WARUM ein Leerzeichen nicht geht: "
                        + ergebnis.klagen());
        assertEquals(0, ergebnis.verzeichnis().anzahl());
    }

    @Test
    @DisplayName("Ein Eintrag, der kein Block ist, wird beklagt")
    void keinBlock() {
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten:
                    halle-1: 7
                """);

        assertTrue(klageNennt(ergebnis, "kein Block mit von, bis und truhe"),
                ergebnis.klagen().toString());
        assertEquals(0, ergebnis.verzeichnis().anzahl());
    }

    @Test
    @DisplayName("Ein Weltname, der kein Text ist, wird beklagt")
    void weltKeinText() {
        var ergebnis = lies("""
                laeden:
                  welt: 7
                  einheiten: {}
                """);

        assertTrue(klageNennt(ergebnis, "kein Weltname"), ergebnis.klagen().toString());
        assertEquals(Ladenliste.VORGABE_WELT, ergebnis.welt());
    }

    @Test
    @DisplayName("Ein leerer Abschnitt einheiten ist ein BERICHT und keine Klage")
    void leererAbschnitt() {
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten: {}
                """);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(0, ergebnis.verzeichnis().anzahl());
        assertTrue(ergebnis.bericht().stream().anyMatch(z -> z.contains("ist leer")),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Der Bericht nennt die NAMEN und nicht nur ihre Zahl")
    void berichtNenntDieNamen() {
        // Eine Zaehlung "2 Einheiten" saehe nicht, dass ein Stand verschoben wurde. Und
        // sie sagt dem Betreiber nicht, WELCHER weggefallen ist.
        String bericht = String.join(" ", lies(VOLLSTAENDIG).bericht());

        assertTrue(bericht.contains("halle-1") && bericht.contains("halle-2"), bericht);
        assertTrue(bericht.contains("250 Bloecke"),
                "die Summe gehoert dazu - daran sieht man eine vertippte Ecke: "
                        + bericht);
        assertTrue(bericht.contains("NUR BEIM START"),
                "und dass ein Neuladen daran nichts aendert: " + bericht);
    }

    @Test
    @DisplayName("Ein leerer Abschnitt OHNE Klagen verweist nicht auf Gruende")
    void leererAbschnittOhneKlagen() {
        // Befund aus dem gelesenen Startlauf vom 08.11.2026: Hier stand immer "die
        // Gruende stehen in den Zeilen davor" - auch bei dem ausgelieferten
        // einheiten: {}, wo es gar keine Zeilen davor gibt. Ein Satz, der auf nichts
        // verweist, schickt den Betreiber suchen.
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten: {}
                """);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        String zeile = leerzeile(ergebnis);
        assertFalse(zeile.contains("Zeilen davor"),
                "ohne Klagen gibt es keine Zeilen davor: " + zeile);
        assertTrue(zeile.contains("von Hand zu bauen"),
                "stattdessen der Hinweis, was zu tun ist: " + zeile);
    }

    @Test
    @DisplayName("Ein leerer Abschnitt MIT Klagen verweist sehr wohl auf sie")
    void leererAbschnittMitKlagen() {
        // Die andere Richtung, und sie braucht ihre eigene Probe: Eine Fassung, die
        // immer den Hinweis "von Hand zu bauen" nennt, waere bei der Probe darueber
        // gruen - und verschwiege hier, dass die Einheit wegen eines FEHLERS fehlt.
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten:
                    halle-1:
                      von: [60, 71, 4]
                      bis: [64, 75, 8]
                      truhe: [999, 71, 6]
                """);

        assertFalse(ergebnis.klagen().isEmpty(), "die Truhe liegt ausserhalb");
        String zeile = leerzeile(ergebnis);
        assertTrue(zeile.contains("Zeilen davor"),
                "mit Klagen muss der Satz auf sie verweisen: " + zeile);
    }

    /** Die Zeile ueber den leeren Abschnitt - oder ein Abbruch, wenn sie fehlt. */
    private static String leerzeile(Ladenliste.Ergebnis ergebnis) {
        return ergebnis.bericht().stream()
                .filter(zeile -> zeile.contains("ist leer"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("keine Leer-Zeile im Bericht: "
                        + ergebnis.bericht()));
    }

    @Test
    @DisplayName("Die MITGELIEFERTE config.yml traegt drei gueltige Probestaende")
    void ausgelieferteDateiTraegtDieProbestaende() {
        // Bis zum 09.11.2026 hielt diese Probe das Gegenteil: dass die Datei KEINE
        // Einheit traegt, weil eine erfundene Koordinate ein Laden mitten im Wasser
        // waere. Seit der Entscheidung vom 08.11.2026 stehen drei Probestaende drin -
        // eingemessen und nicht erfunden -, und damit haelt die Probe MEHR als
        // vorher: Sie laeuft den Leser mit wirklichen Zahlen durch.
        //
        // Was keine Probe sehen kann, ist die Lage im Gelaende. Die config.yml
        // schreibt daneben, woher die Zahlen kommen und wann sie wertlos werden
        // (Altfurt hat keinen festen Startwert). Hier wird geprueft, was der LESER
        // zusagt - und das Tragende ist die Zahl DREI neben der leeren Klagenliste:
        // Eine Ueberschneidung oder eine Truhe ausserhalb kostet ihre Einheit, und
        // zwar OHNE dass die Datei dafuer unlesbar wird. Mit zwei Staenden und einer
        // leeren Klagenliste sieht ein verworfener Stand genau wie Absicht aus.
        var ergebnis = Ladenliste.ausKonfiguration(ausgelieferteDatei(), KANTEN);

        assertTrue(ergebnis.klagen().isEmpty(),
                "die mitgelieferte Datei darf nicht klagen: " + ergebnis.klagen());
        assertEquals(3, ergebnis.verzeichnis().anzahl(),
                "drei Staende, und keiner davon verworfen: "
                        + ergebnis.bericht());
        assertEquals("altfurt", ergebnis.welt(),
                "Plan 2.1 setzt die Markthalle in die Hauptstadt");
    }

    @Test
    @DisplayName("Die ausgelieferte Welt steht auch im Abschnitt welten")
    void weltStehtInWelten() {
        // Sonst gaelte keine Einheit, sobald der Betreiber eine eintraegt - und die
        // Klage kaeme erst dann, Wochen nach dem Tippfehler.
        YamlConfiguration datei = ausgelieferteDatei();
        String welt = Ladenliste.ausKonfiguration(datei, KANTEN).welt();

        assertNotNull(datei.getConfigurationSection("welten." + welt),
                "laeden.welt ist \"" + welt + "\", aber diese Welt steht nicht in"
                        + " welten: der ausgelieferten Datei");
    }

    @Test
    @DisplayName("Ein zweiter Eintrag, der sich erst kleingeschrieben gleicht, wird beklagt")
    void doppelterNameNachKleinschreiben() {
        // YAML wirft einen woertlich doppelten Schluessel schon beim Laden weg. Dieser
        // Fall ist nur so erreichbar - und ohne die Klage sucht der Betreiber einen
        // Stand, den er eingetragen hat.
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten:
                    halle-1:
                      von: [60, 71, 4]
                      bis: [64, 75, 8]
                      truhe: [62, 71, 6]
                    Halle-1:
                      von: [20, 71, 4]
                      bis: [24, 75, 8]
                      truhe: [22, 71, 6]
                """);

        assertEquals(1, ergebnis.verzeichnis().anzahl());
        assertTrue(klageNennt(ergebnis, "denselben Namen"), ergebnis.klagen().toString());
        assertEquals("62/71/6",
                ergebnis.verzeichnis().mitNamen("halle-1").truheAlsText(),
                "der ERSTE gewinnt");
    }

    @Test
    @DisplayName("Vertauschte Ecken sind kein Fehler - der Quader sortiert sie")
    void vertauschteEcken() {
        // Ein Betreiber, der von und bis vertauscht, hat sich vertippt und keinen leeren
        // Raum gemeint. Der Quader sortiert beim Anlegen; eine Klage hier waere eine
        // ueber einen Fehler, der schon behoben ist.
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  einheiten:
                    rueckwaerts:
                      von: [64, 75, 8]
                      bis: [60, 71, 4]
                      truhe: [62, 71, 6]
                """);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(1, ergebnis.verzeichnis().anzahl());
        assertNotNull(ergebnis.verzeichnis().anDerStelle(62, 71, 6));
        assertNull(ergebnis.verzeichnis().anDerStelle(59, 71, 6));
    }

    private static boolean klageNennt(Ladenliste.Ergebnis ergebnis, String stueck) {
        return ergebnis.klagen().stream().anyMatch(klage -> klage.contains(stueck));
    }

    private static YamlConfiguration ausgelieferteDatei() {
        try (InputStream strom = LadenlisteTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertNotNull(strom, "config.yml muss im Jar liegen");
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));
        } catch (java.io.IOException fehler) {
            throw new IllegalStateException("config.yml nicht lesbar", fehler);
        }
    }
}
