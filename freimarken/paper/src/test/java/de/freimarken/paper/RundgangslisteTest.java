package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser der Rundgangsstationen - gegen getippte Dateien.
 *
 * <p>Dieselbe Bauart wie {@code LadenlisteTest}: Die Datei wird hier getippt, und zwar
 * so, wie ein Betreiber sie tippt - mit Tippfehlern. Was dabei herauskommen MUSS, ist
 * nicht nur ein Verzeichnis, sondern eine KLAGE, die den Schluessel nennt.
 *
 * <p>Die wichtigste Probe ist die erste: <b>Eine leere Liste ist der ausgelieferte
 * Zustand und keine Klage.</b> Ein Server, dessen Altfurt noch nicht steht, ist nicht
 * kaputt - und wer ihn startet, soll keine rote Zeile lesen, sondern einen Hinweis.
 */
@DisplayName("Die Rundgangsliste")
class RundgangslisteTest {

    /** Altfurt mit einer Kante, die alles aufnimmt, was diese Proben tippen. */
    private static final Map<String, Integer> KANTEN = Map.of("altfurt", 800);

    private static YamlConfiguration datei(String... zeilen) {
        var inhalt = new StringBuilder();
        for (String zeile : zeilen) {
            inhalt.append(zeile).append('\n');
        }
        var gelesen = new YamlConfiguration();
        try {
            gelesen.loadFromString(inhalt.toString());
        } catch (org.bukkit.configuration.InvalidConfigurationException kaputt) {
            throw new IllegalStateException("Die Probe tippt kein gueltiges YAML",
                    kaputt);
        }
        return gelesen;
    }

    @Test
    @DisplayName("1 - eine leere Liste ist der ausgelieferte Zustand und KEINE Klage")
    void leereListeIstKeineKlage() {
        var ergebnis = Rundgangsliste.ausKonfiguration(datei(
                "rundgang:",
                "  welt: altfurt",
                "  stationen: {}"), KANTEN);

        assertEquals(List.of(), ergebnis.klagen(),
                "ein Server ohne gebautes Altfurt ist nicht kaputt");
        assertTrue(ergebnis.verzeichnis().leer());
        assertEquals(1, ergebnis.bericht().size(), ergebnis.bericht().toString());
        assertTrue(ergebnis.bericht().get(0).contains("Kein Rundgang"),
                ergebnis.bericht().get(0));
    }

    @Test
    @DisplayName("2 - zwei Stationen stehen in der Reihenfolge der Datei")
    void zweiStationenInDerReihenfolge() {
        var ergebnis = Rundgangsliste.ausKonfiguration(datei(
                "rundgang:",
                "  welt: altfurt",
                "  stationen:",
                "    stadttor:",
                "      von: [40, -60, 0]",
                "      bis: [42, -58, 2]",
                "    buergeramt:",
                "      von: [10, -60, 10]",
                "      bis: [20, -55, 20]"), KANTEN);

        assertEquals(List.of(), ergebnis.klagen());
        // DIE REIHENFOLGE IST DER WEG - und hier beginnt er am Ausgang. Das ist erlaubt,
        // und genau darum nennt die Berichtzeile sie.
        assertEquals(List.of("stadttor", "buergeramt"), ergebnis.verzeichnis().namen());
        assertEquals("altfurt", ergebnis.welt());
        assertTrue(ergebnis.bericht().get(0).contains("stadttor, buergeramt"),
                ergebnis.bericht().get(0));
    }

    @Test
    @DisplayName("3 - eine fehlende Ecke laesst die Station GANZ wegfallen")
    void fehlendeEckeLaesstSieWegfallen() {
        var ergebnis = Rundgangsliste.ausKonfiguration(datei(
                "rundgang:",
                "  welt: altfurt",
                "  stationen:",
                "    halb:",
                "      von: [10, -60, 10]",
                "    ganz:",
                "      von: [30, -60, 10]",
                "      bis: [32, -58, 12]"), KANTEN);

        assertEquals(List.of("ganz"), ergebnis.verzeichnis().namen(),
                "die halbe Station faellt weg, die ganze gilt weiter");
        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("rundgang.stationen.halb.bis"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("4 - eine Kommazahl in einer Ecke ist eine Klage")
    void kommazahlIstEineKlage() {
        // YAML macht aus 1.000 eine 1.0 - gemeint waren 1000. Dieselbe Falle wie bei den
        // Ladeneinheiten, und darum dieselbe Pruefung am TYP.
        var ergebnis = Rundgangsliste.ausKonfiguration(datei(
                "rundgang:",
                "  welt: altfurt",
                "  stationen:",
                "    schief:",
                "      von: [10.5, -60, 10]",
                "      bis: [20, -55, 20]"), KANTEN);

        assertTrue(ergebnis.verzeichnis().leer());
        assertTrue(ergebnis.klagen().get(0).contains("keine ganze Zahl"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("5 - zwei Stationen mit gemeinsamen Bloecken werden BEIDE verworfen")
    void ueberschneidungVerwirftBeide() {
        // Welche zaehlt, entschiede sonst die Reihenfolge der Datei - und beide Eintraege
        // sehen fuer sich richtig aus. Ein Spieler bekaeme je nach Sortierung eine andere
        // Station gezaehlt.
        var ergebnis = Rundgangsliste.ausKonfiguration(datei(
                "rundgang:",
                "  welt: altfurt",
                "  stationen:",
                "    eine:",
                "      von: [10, -60, 10]",
                "      bis: [20, -55, 20]",
                "    andere:",
                "      von: [15, -60, 15]",
                "      bis: [25, -55, 25]",
                "    heil:",
                "      von: [40, -60, 40]",
                "      bis: [42, -58, 42]"), KANTEN);

        assertEquals(List.of("heil"), ergebnis.verzeichnis().namen());
        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("BEIDE werden verworfen"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("6 - eine Station hinter der Weltgrenze wird verworfen")
    void hinterDerGrenzeWirdVerworfen() {
        var ergebnis = Rundgangsliste.ausKonfiguration(datei(
                "rundgang:",
                "  welt: altfurt",
                "  stationen:",
                "    weitDraussen:",
                "      von: [100000, -60, 100000]",
                "      bis: [100002, -58, 100002]"), KANTEN);

        assertTrue(ergebnis.verzeichnis().leer());
        assertTrue(ergebnis.klagen().get(0).contains("ausserhalb der Weltgrenze"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("7 - eine Welt, die nicht in welten: steht, laesst keine Station gelten")
    void unbekannteWeltLaesstKeineGelten() {
        var ergebnis = Rundgangsliste.ausKonfiguration(datei(
                "rundgang:",
                "  welt: gibtsnicht",
                "  stationen:",
                "    eine:",
                "      von: [10, -60, 10]",
                "      bis: [12, -58, 12]"), KANTEN);

        assertTrue(ergebnis.verzeichnis().leer());
        assertTrue(ergebnis.klagen().get(0).contains("steht nicht in welten"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("8 - ein unbrauchbarer Stationsname ist eine Klage")
    void unbrauchbarerNameIstEineKlage() {
        // Der Name ist der Schluessel in der Sprachdatei UND in der Ablage. Was an einer
        // der drei Stellen nicht wiederzuerkennen ist, ist ein Fehler, den niemand
        // findet.
        var ergebnis = Rundgangsliste.ausKonfiguration(datei(
                "rundgang:",
                "  welt: altfurt",
                "  stationen:",
                "    \"1stelle\":",
                "      von: [10, -60, 10]",
                "      bis: [12, -58, 12]"), KANTEN);

        assertTrue(ergebnis.verzeichnis().leer());
        assertTrue(ergebnis.klagen().get(0).contains("taugt nicht"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("9 - eine zu grosse Station ist eine Klage")
    void zuGrosseStationIstEineKlage() {
        // Eine Station ist ein Gebaeude, kein Stadtteil: Ein Bereich von der Groesse
        // Altfurts waere mit dem ersten Schritt gesehen.
        var ergebnis = Rundgangsliste.ausKonfiguration(datei(
                "rundgang:",
                "  welt: altfurt",
                "  stationen:",
                "    halbaltfurt:",
                "      von: [-200, -61, -200]",
                "      bis: [200, 50, 200]"), KANTEN);

        assertTrue(ergebnis.verzeichnis().leer());
        assertTrue(ergebnis.klagen().get(0).contains("Bloecke"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("10 - ohne Abschnitt gibt es kein Verzeichnis und keine Klage")
    void ohneAbschnittKeineKlage() {
        var ergebnis = Rundgangsliste.ausKonfiguration(datei("andere: 1"), KANTEN);

        assertEquals(List.of(), ergebnis.klagen());
        assertTrue(ergebnis.verzeichnis().leer());
        assertEquals("", ergebnis.welt(),
                "ohne Stationen braucht es keine Welt - und keine zweite Klage darueber");
    }
}
