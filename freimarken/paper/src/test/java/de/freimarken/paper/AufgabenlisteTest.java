package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Aufgabenwerte;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser der Herausforderungen - was der Betreiber tippt, und was daraus wird.
 *
 * <p><b>Die tragende Zusage ist, dass dieser Leser NIE wirft.</b> Ein Tippfehler darf
 * keinen Serverstart mitreissen - er kostet seine Aufgabe und bekommt eine Klage.
 * {@link Aufgabenwerte} wirft sehr wohl; was hier durchkaeme, waere also ein Wurf mitten
 * im Lesen.
 */
@DisplayName("Der Leser der Herausforderungen")
class AufgabenlisteTest {

    private static Aufgabenliste.Ergebnis lies(String yaml) {
        return Aufgabenliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml)));
    }

    @Test
    @DisplayName("Ein vollstaendiger Abschnitt kommt vollstaendig an - in DIESER Folge")
    void vollstaendig() {
        var ergebnis = lies("""
                wolken:
                  aufgaben:
                    an: true
                    liste:
                      zuletzt:
                        verlangt:
                          diamond: 4
                        belohnung: 2000
                      zuerst:
                        verlangt:
                          cobblestone: 64
                          oak_log: 32
                        belohnung: 50
                """);

        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertEquals(List.of("zuletzt", "zuerst"), ergebnis.werte().schluessel(),
                "die Reihenfolge der DATEI, nicht die des Alphabets");
        var erste = ergebnis.werte().aufgabe("zuletzt").orElseThrow();
        assertEquals(Map.of("diamond", 4), erste.verlangt());
        assertEquals(2_000L, erste.belohnung());
        assertEquals(2, ergebnis.werte().aufgabe("zuerst").orElseThrow()
                .verlangt().size());
    }

    @Test
    @DisplayName("Eine fehlende Belohnung heisst 0 - eine Aufgabe nur fuer die Stufe")
    void belohnungDarfFehlen() {
        var ergebnis = lies("""
                wolken:
                  aufgaben:
                    liste:
                      ohne-lohn:
                        verlangt:
                          stone: 1
                """);

        assertEquals(List.of(), ergebnis.klagen());
        assertEquals(0L, ergebnis.werte().aufgabe("ohne-lohn").orElseThrow().belohnung());
    }

    @Test
    @DisplayName("Eine kaputte Aufgabe kostet NUR sich selbst")
    void kaputteAufgabe() {
        // Fuenf Wege, eine Aufgabe zu verlieren - und keiner davon darf die uebrigen
        // mitnehmen.
        var ergebnis = lies("""
                wolken:
                  aufgaben:
                    liste:
                      gut:
                        verlangt:
                          stone: 1
                        belohnung: 10
                      kein-abschnitt: 7
                      ohne-verlangt:
                        belohnung: 10
                      leeres-verlangt:
                        verlangt: {}
                      kaputter-lohn:
                        verlangt:
                          stone: 1
                        belohnung: viel
                      falsche.form:
                        verlangt:
                          stone: 1
                """);

        assertEquals(List.of("gut"), ergebnis.werte().schluessel(),
                "nur die gute bleibt: " + ergebnis.werte().schluessel());
        assertEquals(5, ergebnis.klagen().size(), ergebnis.klagen().toString());
        for (String klage : ergebnis.klagen()) {
            assertTrue(klage.contains("die uebrigen gelten"), klage);
        }
    }

    @Test
    @DisplayName("Ein Gegenstand, den es nicht gibt, nimmt die GANZE Aufgabe mit")
    void unbekannterGegenstand() {
        // Anders als bei der Erztabelle, und das ist der Grund: Eine Aufgabe, der ein
        // verlangtes Stueck fehlt, ist eine ANDERE Aufgabe - und zwar eine leichtere. Wer
        // sich in einem Namen vertippt, soll sie vermissen und nicht halb erfuellt
        // bekommen.
        var ergebnis = lies("""
                wolken:
                  aufgaben:
                    liste:
                      zwei-teile:
                        verlangt:
                          stone: 64
                          gibt_es_nicht: 1
                        belohnung: 10
                """);

        assertEquals(List.of(), ergebnis.werte().schluessel());
        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("ganze Herausforderung"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Eine Anzahl von 0 oder ueber der Obergrenze nimmt die Aufgabe mit")
    void unbrauchbareAnzahl() {
        var ergebnis = lies("""
                wolken:
                  aufgaben:
                    liste:
                      nullmenge:
                        verlangt:
                          stone: 0
                      zuviel:
                        verlangt:
                          stone: 100000
                """);

        assertEquals(List.of(), ergebnis.werte().schluessel());
        assertEquals(2, ergebnis.klagen().size(), ergebnis.klagen().toString());
    }

    @Test
    @DisplayName("Eine LEERE Liste heisst etwas anderes als eine fehlende")
    void leereListe() {
        var mitAbschnitt = lies("""
                wolken:
                  aufgaben:
                    liste: {}
                """);
        assertEquals(0, mitAbschnitt.werte().liste().size());
        assertFalse(mitAbschnitt.werte().greift());

        var ohneAbschnitt = lies("wolken:\n  preis: 0\n");
        assertEquals(Aufgabenwerte.VORGABE_LISTE.size(),
                ohneAbschnitt.werte().liste().size(),
                "fehlt der Abschnitt, gilt die Liste des Programms");
        assertTrue(ohneAbschnitt.bericht().stream()
                .anyMatch(zeile -> zeile.contains(Aufgabenwerte.PFAD_LISTE)));
    }

    @Test
    @DisplayName("Jede Aufgabe kaputt: der Bericht sagt, dass jede Insel auf 0 bleibt")
    void jedeKaputt() {
        var ergebnis = lies("""
                wolken:
                  aufgaben:
                    liste:
                      kaputt: 7
                """);

        assertEquals(0, ergebnis.werte().liste().size());
        assertTrue(ergebnis.bericht().stream()
                        .anyMatch(zeile -> zeile.contains("Stufe 0")),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Der Schalter wird gelesen und gemeldet")
    void schalter() {
        var aus = lies("""
                wolken:
                  aufgaben:
                    an: false
                """);

        assertFalse(aus.werte().an());
        assertFalse(aus.werte().greift(), "ausgeschaltet greift die Liste nicht");
        assertTrue(aus.bericht().stream()
                        .anyMatch(zeile -> zeile.contains(Aufgabenwerte.PFAD_AN)),
                aus.bericht().toString());

        var unsinn = lies("""
                wolken:
                  aufgaben:
                    an: vielleicht
                """);
        assertEquals(1, unsinn.klagen().size(), unsinn.klagen().toString());
        assertTrue(unsinn.werte().an(), "es bleibt beim ausgelieferten Ja");
    }

    @Test
    @DisplayName("Ohne Konfiguration gilt die Vorgabe, und es gibt keine Klage")
    void ohneKonfiguration() {
        var ergebnis = Aufgabenliste.ausKonfiguration(null);

        assertEquals(Aufgabenwerte.VORGABE, ergebnis.werte());
        assertEquals(List.of(), ergebnis.klagen());
    }

    @Test
    @DisplayName("Die ausgelieferte config.yml bringt GENAU die Liste des Programms")
    void ausgelieferteDatei() throws Exception {
        // Die Naht zwischen Datei und Programm: Stehen dort andere Mengen oder andere
        // Namen, sagt die Dokumentation dieses Projekts etwas anderes als das Programm -
        // und der Betreiber liest die falsche Zahl.
        try (var strom = getClass().getResourceAsStream("/config.yml")) {
            assertTrue(strom != null, "config.yml liegt nicht im Klassenpfad");
            var datei = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                    strom, java.nio.charset.StandardCharsets.UTF_8));
            var ergebnis = Aufgabenliste.ausKonfiguration(datei);

            assertEquals(List.of(), ergebnis.klagen(),
                    "die ausgelieferte Datei klagt: " + ergebnis.klagen());
            assertEquals(Aufgabenwerte.VORGABE, ergebnis.werte());
        }
    }

    @Test
    @DisplayName("Die Rechenprobe nennt Zahl, hoechste Stufe und die Summe")
    void rechenprobe() {
        // Die Schranke gegen den haeufigsten Fehler dieser Liste: Wer vier Aufgaben
        // eintraegt und in wolken.erze.tabelle den Diamanten ab Stufe fuenf stehen
        // laesst, hat ein Erz, das in dieser Welt niemand je bekommt.
        assertEquals("Herausforderungen: 10 Stueck, hoechste Stufe 10, zusammen 5450"
                        + " Mark zu verdienen - je Insel EINMAL.",
                Aufgabenliste.rechenprobe(Aufgabenwerte.VORGABE));
        assertEquals("Herausforderungen: keine - wolken.aufgaben.an ist aus. Jede Insel"
                        + " bleibt auf Stufe 0, und der Generator gibt nur, was dort"
                        + " steht.",
                Aufgabenliste.rechenprobe(Aufgabenwerte.VORGABE.mitAn(false)));
        assertEquals("Herausforderungen: keine - die Liste ist leer. Jede Insel bleibt"
                        + " auf Stufe 0, und der Generator gibt nur, was dort steht.",
                Aufgabenliste.rechenprobe(Aufgabenwerte.VORGABE.mitListe(List.of())));
    }
}
