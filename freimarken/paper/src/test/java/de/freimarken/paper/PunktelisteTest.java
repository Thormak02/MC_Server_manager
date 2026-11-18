package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Punktwerte;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Der Leser der Punktetabelle - was der Betreiber tippt, und was daraus wird. */
@DisplayName("Der Leser der Inselpunkte")
class PunktelisteTest {

    private static Punkteliste.Ergebnis lies(String yaml) {
        return Punkteliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml)));
    }

    @Test
    @DisplayName("Ein vollstaendiger Abschnitt kommt vollstaendig an")
    void vollstaendig() {
        var ergebnis = lies("""
                wolken:
                  punkte:
                    chunks-je-takt: 4
                    abklingzeit-sekunden: 30
                    tabelle:
                      DIAMOND_BLOCK: 900
                      glass: 2
                """);

        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertEquals(4, ergebnis.werte().chunksJeTakt());
        assertEquals(30L, ergebnis.werte().abklingSekunden());
        assertEquals(900L, ergebnis.werte().punkteFuer("diamond_block"),
                "gross geschrieben in der Datei, klein in der Tabelle");
        assertEquals(2, ergebnis.werte().tabelle().size());
    }

    @Test
    @DisplayName("Eine kaputte Zeile kostet NUR ihre Zeile")
    void kaputteZeile() {
        var ergebnis = lies("""
                wolken:
                  punkte:
                    tabelle:
                      glass: 2
                      gibt_es_nicht: 5
                      diamond_block: 0
                      iron_block: viel
                """);

        assertEquals(1, ergebnis.werte().tabelle().size(),
                "nur das Glas bleibt: " + ergebnis.werte().tabelle());
        assertEquals(3, ergebnis.klagen().size(), ergebnis.klagen().toString());
        for (String klage : ergebnis.klagen()) {
            assertTrue(klage.contains("die uebrigen gelten"), klage);
        }
    }

    @Test
    @DisplayName("Ein Happen ausserhalb von 1 bis 64 wird beklagt")
    void happenAusserhalb() {
        var zuGross = lies("wolken:\n  punkte:\n    chunks-je-takt: 100\n");
        assertEquals(1, zuGross.klagen().size(), zuGross.klagen().toString());
        assertEquals(Punktwerte.VORGABE_CHUNKS_JE_TAKT,
                zuGross.werte().chunksJeTakt());

        var null0 = lies("wolken:\n  punkte:\n    chunks-je-takt: 0\n");
        assertEquals(1, null0.klagen().size(), null0.klagen().toString());
        assertEquals(Punktwerte.VORGABE_CHUNKS_JE_TAKT, null0.werte().chunksJeTakt());
    }

    @Test
    @DisplayName("Eine LEERE Tabelle heisst etwas anderes als eine fehlende")
    void leereTabelle() {
        var mitAbschnitt = lies("wolken:\n  punkte:\n    tabelle: {}\n");
        assertEquals(0, mitAbschnitt.werte().tabelle().size());
        assertFalse(mitAbschnitt.werte().greift());

        var ohneAbschnitt = lies("wolken:\n  preis: 0\n");
        assertEquals(Punktwerte.VORGABE_TABELLE.size(),
                ohneAbschnitt.werte().tabelle().size());
    }

    @Test
    @DisplayName("Ohne Konfiguration gilt die Vorgabe, und es gibt keine Klage")
    void ohneKonfiguration() {
        var ergebnis = Punkteliste.ausKonfiguration(null);

        assertEquals(Punktwerte.VORGABE, ergebnis.werte());
        assertEquals(List.of(), ergebnis.klagen());
    }

    @Test
    @DisplayName("Die ausgelieferte config.yml bringt GENAU die Werte des Programms")
    void ausgelieferteDatei() throws Exception {
        try (var strom = getClass().getResourceAsStream("/config.yml")) {
            assertTrue(strom != null, "config.yml liegt nicht im Klassenpfad");
            var datei = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                    strom, java.nio.charset.StandardCharsets.UTF_8));
            var ergebnis = Punkteliste.ausKonfiguration(datei);

            assertEquals(List.of(), ergebnis.klagen(),
                    "die ausgelieferte Datei klagt: " + ergebnis.klagen());
            assertEquals(Punktwerte.VORGABE, ergebnis.werte());
        }
    }

    @Test
    @DisplayName("Die Rechenprobe rechnet die Dauer einer Zaehlung vor")
    void rechenprobe() {
        // Die Schranke gegen den einen teuren Vertipper: Wer chunks-je-takt: 64 setzt,
        // zaehlt ein ganzes Feld in EINEM Takt - und der Server steht, solange das laeuft.
        assertEquals("Punkte: 23 Bloecke zaehlen, 1 Chunk(s) je Takt - eine Zaehlung"
                        + " braucht 64 Takte (etwa 4 Sekunden), danach 120 Sekunden Pause"
                        + " je Insel.",
                Punkteliste.rechenprobe(Punktwerte.VORGABE));
        assertEquals("Punkte: 23 Bloecke zaehlen, 64 Chunk(s) je Takt - eine Zaehlung"
                        + " braucht 1 Takte (etwa 1 Sekunden), danach 120 Sekunden Pause"
                        + " je Insel.",
                Punkteliste.rechenprobe(Punktwerte.VORGABE.mitChunksJeTakt(64)));
        assertEquals("Punkte: keine Tabelle - jede Insel hat 0 Punkte, und die Rangliste"
                        + " zeigt sie in der Reihenfolge ihres Alters.",
                Punkteliste.rechenprobe(Punktwerte.VORGABE.mitTabelle(
                        java.util.Map.of())));
    }
}
