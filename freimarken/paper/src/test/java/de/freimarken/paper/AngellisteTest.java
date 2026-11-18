package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Angelwerte;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser der Angeltabelle - was der Betreiber tippt, und was daraus wird.
 *
 * <p>Gelesen wird eine echte {@code YamlConfiguration} und keine nachgebaute Karte:
 * {@code YamlConfiguration} ist reine Dateiarbeit und braucht keinen Server, und damit
 * prueft diese Probe genau das, was im Spiel in der Datei steht.
 *
 * <p><b>Die tragende Zusage ist "eine kaputte ZEILE kostet nur sich selbst".</b> Ein
 * Tippfehler in einem Fangnamen darf nicht die ganze Tabelle kosten - sonst angelt eine
 * Welt wieder Vanilla, und im Protokoll steht eine Zeile, die niemand liest.
 */
@DisplayName("Der Leser der Angeltabelle")
class AngellisteTest {

    private static Angelliste.Ergebnis lies(String yaml) {
        return Angelliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml)));
    }

    @Test
    @DisplayName("Eine vollstaendige Tabelle kommt vollstaendig an")
    void vollstaendig() {
        var ergebnis = lies("""
                angeln:
                  an: true
                  haeufigkeit-prozent: 35
                  faenge:
                    cod: 7
                    sponge: 2
                """);

        assertEquals(java.util.List.of(), ergebnis.klagen(),
                ergebnis.klagen().toString());
        assertTrue(ergebnis.werte().an());
        assertEquals(35, ergebnis.werte().haeufigkeitProzent());
        assertEquals(java.util.Map.of("cod", 7, "sponge", 2),
                ergebnis.werte().gewichte());
    }

    @Test
    @DisplayName("Ein Name, den der Server nicht kennt, kostet NUR SEINE ZEILE")
    void kaputteZeileKostetNurSichSelbst() {
        var ergebnis = lies("""
                angeln:
                  an: true
                  haeufigkeit-prozent: 20
                  faenge:
                    cod: 5
                    gibtsnicht: 3
                    sponge: 2
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("gibtsnicht"),
                ergebnis.klagen().get(0));
        assertEquals(java.util.Map.of("cod", 5, "sponge", 2),
                ergebnis.werte().gewichte(),
                "die uebrigen gelten - sonst angelt die Welt wegen eines Tippfehlers"
                        + " wieder Vanilla");
    }

    @Test
    @DisplayName("Ein Name mit minecraft: davor wird beklagt - und die Klage sagt es")
    void mitNamensraumBeklagt() {
        // Dieselbe Pruefung wie bei der Lohntafel, und dieselbe Grenze: Geprueft wird
        // mit Material.getMaterial und bewusst nicht mit matchMaterial, damit der Leser
        // ohne laufenden Server pruefbar bleibt. Der Preis ist, dass "minecraft:cod"
        // nicht erkannt wird - und die Klage sagt genau das.
        var ergebnis = lies("""
                angeln:
                  faenge:
                    minecraft:cod: 5
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("ohne minecraft: davor"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Ein Gewicht von 0 oder keiner Zahl wird beklagt")
    void kaputtesGewicht() {
        var ergebnis = lies("""
                angeln:
                  faenge:
                    cod: 0
                    sponge: viel
                    tropical_fish: 3
                """);

        assertEquals(2, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertEquals(java.util.Map.of("tropical_fish", 3), ergebnis.werte().gewichte());
    }

    @Test
    @DisplayName("Eine Haeufigkeit ausserhalb 0 bis 100 wird verworfen")
    void haeufigkeitAusserhalb() {
        var ergebnis = lies("""
                angeln:
                  haeufigkeit-prozent: 140
                  faenge:
                    cod: 1
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertEquals(Angelwerte.VORGABE_HAEUFIGKEIT,
                ergebnis.werte().haeufigkeitProzent());
    }

    @Test
    @DisplayName("Ein ja/nein, das keines ist, wird verworfen")
    void schalterKaputt() {
        var ergebnis = lies("""
                angeln:
                  an: vielleicht
                  faenge:
                    cod: 1
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertEquals(Angelwerte.VORGABE.an(), ergebnis.werte().an());
    }

    @Test
    @DisplayName("Eine LEERE Liste ist erlaubt und bedeutet etwas")
    void leereListeIstErlaubt() {
        // Der Weg zurueck zu Vanilla, ohne den Schalter zu finden: Wer alle Zeilen
        // herausnimmt, will Vanilla und soll es bekommen - und zwar ohne Klage.
        var ergebnis = lies("""
                angeln:
                  an: true
                  haeufigkeit-prozent: 20
                  faenge: {}
                """);

        assertEquals(java.util.List.of(), ergebnis.klagen(),
                ergebnis.klagen().toString());
        assertTrue(ergebnis.werte().gewichte().isEmpty());
        assertFalse(ergebnis.werte().greift());
    }

    @Test
    @DisplayName("Fehlt der ganze Abschnitt, gilt die Tabelle des Programms")
    void abschnittFehlt() {
        // Auf einem Server, der schon lief, schreibt saveDefaultConfig die Datei nie um -
        // die Zeilen fehlen dort einfach. Das ist keine Klage, aber eine Berichtzeile.
        var ergebnis = lies("wirtschaft:\n  startguthaben: 1000\n");

        assertEquals(java.util.List.of(), ergebnis.klagen());
        assertEquals(Angelwerte.VORGABE, ergebnis.werte());
        assertEquals(1, ergebnis.bericht().size(), ergebnis.bericht().toString());
        assertTrue(ergebnis.bericht().get(0).contains("fehlt"),
                ergebnis.bericht().get(0));
    }

    @Test
    @DisplayName("Die ausgelieferte config.yml ergibt eine Tabelle ohne Klagen")
    void dieAusgelieferteDatei() throws Exception {
        // Die Probe, die ein Tippfehler in der mitgelieferten Datei nicht ueberlebt.
        // Ohne sie faellt er erst beim Start eines echten Servers auf - und dann angelt
        // Hafenwerder Vanilla, mit einer Klage, die niemand gelesen hat.
        try (var strom = getClass().getResourceAsStream("/config.yml")) {
            assertTrue(strom != null, "config.yml liegt nicht im Klassenpfad");
            var datei = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                    strom, java.nio.charset.StandardCharsets.UTF_8));
            var ergebnis = Angelliste.ausKonfiguration(datei);

            assertEquals(java.util.List.of(), ergebnis.klagen(),
                    "die ausgelieferte Datei klagt: " + ergebnis.klagen());
            assertEquals(Angelwerte.VORGABE.gewichte(), ergebnis.werte().gewichte(),
                    "die Datei und das Programm bringen DIESELBE Tabelle - laufen sie"
                            + " auseinander, faengt ein frischer Server etwas anderes"
                            + " als einer, dem die Zeilen fehlen");
            assertEquals(Angelwerte.VORGABE_HAEUFIGKEIT,
                    ergebnis.werte().haeufigkeitProzent());
        }
    }

    @Test
    @DisplayName("Die Rechenprobe nennt jeden Fang mit seinem Anteil an ALLEM")
    void rechenprobe() {
        // Die einzige Schranke gegen den Wert, der im erlaubten Bereich liegt und
        // trotzdem falsch ist: Wer "heart_of_the_sea 120 Promille" liest, sieht die
        // verschobene Null. Keine Zahlengrenze sieht sie.
        String satz = Angelliste.rechenprobe(Angelwerte.VORGABE);

        assertTrue(satz.contains("9 Faenge"), satz);
        assertTrue(satz.contains("jeder 20. Fang"), satz);
        assertTrue(satz.contains("heart_of_the_sea 2 Promille"),
                "1 von 83 bei jedem fuenften Fang - das sind zwei von tausend: " + satz);
        assertTrue(Angelliste.rechenprobe(Angelwerte.VORGABE.mitAn(false))
                        .contains("AUS"),
                "und ausgeschaltet sagt sie das in einem Wort");
    }
}
