package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Wolkenbiome;
import de.freimarken.kern.Wolkenwerte;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser des Abschnitts {@code wolken:} - was der Betreiber tippt, und was daraus wird.
 *
 * <p>Gelesen wird eine echte {@code YamlConfiguration} und keine nachgebaute Karte:
 * {@code YamlConfiguration} ist reine Dateiarbeit und braucht keinen Server, und damit
 * prueft diese Probe genau das, was im Spiel in der Datei steht.
 *
 * <p><b>Die tragende Zusage ist, dass dieser Leser NIE wirft.</b> Ein Tippfehler darf
 * keinen Serverstart mitreissen - er kostet seine Zeile und bekommt eine Klage.
 * {@link Wolkenwerte} wirft sehr wohl; was hier durchkaeme, waere also ein Wurf mitten im
 * Lesen.
 */
@DisplayName("Der Leser der Wolkeninseln")
class WolkenlisteTest {

    private static Wolkenliste.Ergebnis lies(String yaml) {
        return Wolkenliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml)));
    }

    @Test
    @DisplayName("Ein vollstaendiger Abschnitt kommt vollstaendig an")
    void vollstaendig() {
        // MIT BESTAETIGUNG, und das ist keine Umstaendlichkeit der Probe: Der ausgelieferte
        // Preis ist 0, also ist JEDE Zahl ein Sprung ueber das Doppelte. Genau so steht
        // es in der config.yml - aus gratis etwas Kostenpflichtiges zu machen ist die
        // Aenderung, die jeder Spieler sofort merkt.
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    wolken-kauf: "0 -> 5000"
                wolken:
                  welt: himmelsinseln
                  preis: 5000
                  hoechstzahl: 3
                  biome: [swamp, taiga]
                """);

        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertEquals("himmelsinseln", ergebnis.welt());
        assertEquals(5000L, ergebnis.werte().preis());
        assertEquals(3, ergebnis.werte().hoechstzahl());
        assertEquals(List.of("swamp", "taiga"), ergebnis.werte().biome());
    }

    @Test
    @DisplayName("Ohne Bestaetigung bleibt die Insel gratis - und die Klage sagt es")
    void ohneBestaetigungBleibtEsGratis() {
        // Die andere Richtung derselben Regel, und die wichtigere: Wer eine Zahl eintraegt
        // und die Bestaetigung nicht liest, hat KEINEN Preis gesetzt - und darf das nicht
        // fuer eine kaputte Einstellung halten. Die Klage nennt den Schluessel samt
        // fertiger Zeile.
        var ergebnis = lies("wolken:\n  preis: 5000\n");

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("wolken-kauf"),
                "die Klage nennt den Schluessel, mit dem der Sprung freigegeben wird: "
                        + ergebnis.klagen().get(0));
        assertEquals(0L, ergebnis.werte().preis(),
                "bis dahin bleibt es gratis - und nicht etwas dazwischen");
    }

    @Test
    @DisplayName("Ein fehlender Abschnitt ergibt genau die Vorgaben des Programms")
    void fehlenderAbschnitt() {
        var ergebnis = lies("parzellen:\n  hoechstzahl: 4\n");

        assertEquals(List.of(), ergebnis.klagen(), "ein fehlender Abschnitt ist keine"
                + " Klage - das Programm hat seinen Rueckfall");
        assertEquals(Wolkenwerte.VORGABE, ergebnis.werte());
        assertEquals(Wolkenliste.VORGABE_WELT, ergebnis.welt());
        assertFalse(ergebnis.bericht().isEmpty(),
                "aber in den Bericht gehoert er - auf einem Server, der schon lief,"
                        + " schreibt saveDefaultConfig die Datei nie um");
    }

    @Test
    @DisplayName("Eine LEERE Biomliste heisst etwas anderes als eine fehlende")
    void leereListeIstEineAussage() {
        // Wer alle Zeilen herausnimmt, will keine Biomwahl - und soll sie nicht aus der
        // Vorgabe zurueckbekommen. Dieselbe Unterscheidung wie bei der Angeltabelle.
        var leer = lies("wolken:\n  biome: []\n");

        assertEquals(List.of(), leer.klagen(), leer.klagen().toString());
        assertEquals(List.of(), leer.werte().biome());
        assertFalse(leer.werte().biomwahl());

        var fehlt = lies("wolken:\n  preis: 0\n");

        assertEquals(Wolkenbiome.NAMEN, fehlt.werte().biome(),
                "eine FEHLENDE Liste gibt die zehn aus dem Programm");
    }

    @Test
    @DisplayName("Ein Biom, das es nicht gibt, kostet nur SEINE Zeile")
    void kaputteZeileKostetNurSichSelbst() {
        var ergebnis = lies("""
                wolken:
                  biome: [swamp, mondwueste, taiga]
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("mondwueste"),
                ergebnis.klagen().get(0));
        assertTrue(ergebnis.klagen().get(0).contains("swamp"),
                "die Klage nennt, was erlaubt ist: " + ergebnis.klagen().get(0));
        assertEquals(List.of("swamp", "taiga"), ergebnis.werte().biome(),
                "die uebrigen gelten weiter");
    }

    @Test
    @DisplayName("Ein doppeltes Biom wird einmal gezaehlt und NICHT beklagt")
    void doppeltesBiom() {
        // Hier und nicht im Satz: Wolkenwerte WIRFT bei einem doppelten Namen, und ein
        // Wurf mitten im Lesen riss schon einmal einen Serverstart mit. Eine Klage ist es
        // nicht - es ist nichts kaputt, nur zweimal dasselbe gemeint.
        var ergebnis = lies("""
                wolken:
                  biome: [swamp, SWAMP, taiga]
                """);

        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertEquals(List.of("swamp", "taiga"), ergebnis.werte().biome());
        assertTrue(ergebnis.bericht().stream().anyMatch(z -> z.contains("zweimal")),
                "aber der Bericht sagt es: " + ergebnis.bericht());
    }

    @Test
    @DisplayName("Eine Zahl statt einer Liste wird beklagt, nicht geworfen")
    void keineListe() {
        var ergebnis = lies("wolken:\n  biome: 7\n");

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertEquals(Wolkenbiome.NAMEN, ergebnis.werte().biome(), "es bleiben die zehn");
    }

    @Test
    @DisplayName("Eine Hoechstzahl ueber der Zahl der Felder wird abgewiesen")
    void hoechstzahlUeberDerWelt() {
        var ergebnis = lies("wolken:\n  hoechstzahl: 99999\n");

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains(
                        String.valueOf(Wolkenwerte.HOECHSTE_ZAHL)),
                "die Klage nennt die Obergrenze: " + ergebnis.klagen().get(0));
        assertEquals(Wolkenwerte.VORGABE_HOECHSTZAHL, ergebnis.werte().hoechstzahl());
    }

    @Test
    @DisplayName("Eine 0 bei der Hoechstzahl ist erlaubt - so wird die Welt zugemacht")
    void nullIstErlaubt() {
        var ergebnis = lies("wolken:\n  hoechstzahl: 0\n");

        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertEquals(0, ergebnis.werte().hoechstzahl());
        assertTrue(ergebnis.bericht().stream().anyMatch(z -> z.contains("AUS")),
                "und die Rechenprobe sagt es laut: " + ergebnis.bericht());
    }

    @Test
    @DisplayName("Ein Weltname, der keiner ist, wird beklagt")
    void kaputterWeltname() {
        var ergebnis = lies("wolken:\n  welt: 7\n");

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertEquals(Wolkenliste.VORGABE_WELT, ergebnis.welt());
    }

    @Test
    @DisplayName("Die ausgelieferte config.yml ergibt genau die Vorgaben des Programms")
    void dieAusgelieferteDatei() throws Exception {
        // Die Probe, die ein Tippfehler in der mitgelieferten Datei nicht ueberlebt.
        // Ohne sie faellt er erst beim Start eines echten Servers auf - und dann hat ein
        // frischer Server andere Zahlen als einer, dem die Zeilen fehlen.
        try (var strom = getClass().getResourceAsStream("/config.yml")) {
            assertTrue(strom != null, "config.yml liegt nicht im Klassenpfad");
            var datei = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                    strom, java.nio.charset.StandardCharsets.UTF_8));
            var ergebnis = Wolkenliste.ausKonfiguration(datei);

            assertEquals(List.of(), ergebnis.klagen(),
                    "die ausgelieferte Datei klagt: " + ergebnis.klagen());
            assertEquals(Wolkenwerte.VORGABE, ergebnis.werte(),
                    "die Datei und das Programm bringen DIESELBEN Werte");
            assertEquals(Wolkenliste.VORGABE_WELT, ergebnis.welt());
            assertEquals(0L, ergebnis.werte().preis(),
                    "GRATIS, und das ist Plan 6.2 - der zweite der zwei gratis"
                            + " Einstiege");
        }
    }

    @Test
    @DisplayName("Jede gelesene Zeile steht in der ausgelieferten Datei")
    void jedeGeleseneZeileStehtInDerDatei() throws Exception {
        // Die Gegenrichtung: Ein Schluessel, den das Programm liest und die Datei nicht
        // nennt, ist eine Einstellung, von der niemand weiss. Gefunden wird das sonst
        // erst, wenn ein Betreiber danach fragt.
        try (var strom = getClass().getResourceAsStream("/config.yml")) {
            var datei = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                    strom, java.nio.charset.StandardCharsets.UTF_8));

            for (String pfad : List.of(Wolkenliste.PFAD_WELT, Wolkenwerte.PFAD_PREIS,
                    Wolkenwerte.PFAD_HOECHSTZAHL, Wolkenwerte.PFAD_BIOME)) {
                assertTrue(datei.contains(pfad, true),
                        pfad + " wird gelesen, steht aber nicht in der Datei");
            }
            assertTrue(ergebnisOhneFehlende(datei),
                    "und darum darf der Bericht keine fehlende Zeile nennen");
        }
    }

    private static boolean ergebnisOhneFehlende(YamlConfiguration datei) {
        return Wolkenliste.ausKonfiguration(datei).bericht().stream()
                .noneMatch(zeile -> zeile.contains("stehen nicht in deiner config.yml"));
    }

    @Test
    @DisplayName("Die Rechenprobe sagt GRATIS aus, solange der Preis 0 ist")
    void rechenprobe() {
        // Die einzige Schranke gegen den Wert, der im erlaubten Bereich liegt und
        // trotzdem falsch ist: Wer dort versehentlich eine Zahl eintraegt, hat den
        // zweiten gratis Einstieg des Spiels zugemacht und soll es beim Start lesen.
        assertEquals("Wolkeninseln: GRATIS, eine Insel je Spieler, 10 Biome zur Wahl,"
                        + " Starttruhe mit 10 Gegenstaenden.",
                Wolkenliste.rechenprobe(Wolkenwerte.VORGABE));
        assertEquals("Wolkeninseln: 5000 Mark je Insel, 3 Inseln je Spieler,"
                        + " nur das Biom swamp, Starttruhe mit 1 Gegenstaenden.",
                Wolkenliste.rechenprobe(new Wolkenwerte(5000L, 3, List.of("swamp"),
                        java.util.Map.of("lava_bucket", 1))));
        assertEquals("Wolkeninseln: GRATIS, eine Insel je Spieler, keine Biomwahl,"
                        + " Starttruhe mit 10 Gegenstaenden.",
                Wolkenliste.rechenprobe(Wolkenwerte.VORGABE.mitBiomen(List.of())));
        assertEquals("Wolkeninseln: AUS - hoechstzahl ist 0, es wird keine vergeben.",
                Wolkenliste.rechenprobe(Wolkenwerte.VORGABE.mitHoechstzahl(0)));
        // OHNE Lava, und die Rechenprobe sagt es laut: Das ist der Vertipper mit der
        // groessten Folge - ohne Lava gibt es keinen Generator und damit kein Spiel.
        assertEquals("Wolkeninseln: GRATIS, eine Insel je Spieler, 10 Biome zur Wahl,"
                        + " Starttruhe mit 1 Gegenstaenden (OHNE Lava).",
                Wolkenliste.rechenprobe(Wolkenwerte.VORGABE.mitStarttruhe(
                        java.util.Map.of("ice", 2))));
    }
}
