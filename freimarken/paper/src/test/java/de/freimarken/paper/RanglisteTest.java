package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Rangwerte;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Der Leser der Rangleiter - was der Betreiber tippt, und was daraus wird. */
@DisplayName("Der Leser der Rangleiter")
class RanglisteTest {

    private static Rangliste.Ergebnis lies(String yaml) {
        return Rangliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml)));
    }

    @Test
    @DisplayName("Eine vollstaendige Leiter kommt vollstaendig an")
    void vollstaendig() {
        var ergebnis = lies("""
                raenge:
                  an: true
                  leiter:
                    reisender:
                      zahlen:
                        parzellen: 1
                    siedler:
                      bedingungen:
                        taetige-stunden: 5
                      zahlen:
                        parzellen: 2
                      rechte:
                        - laden.mieten
                """);

        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertEquals(List.of("reisender", "siedler"), ergebnis.werte().schluessel(),
                "die Reihenfolge der Datei IST die Leiter");
        var siedler = ergebnis.werte().stufe("siedler").orElseThrow();
        assertEquals(5L, siedler.bedingungen().get(Bedingungsart.TAETIGE_STUNDEN));
        assertEquals(2, siedler.zahl("parzellen").orElseThrow());
        assertEquals(List.of("laden.mieten"), siedler.rechte());
    }

    @Test
    @DisplayName("Eine Stufe mit unbekannter Bedingung faellt GANZ weg")
    void unbekannteBedingung() {
        // Nicht nur die Zeile: Eine Stufe, der eine Bedingung fehlt, ist eine LEICHTERE -
        // dann bekaeme jemand Rechte, die er nicht verdient hat. Eine weggefallene Stufe
        // nimmt dagegen niemandem etwas, was er schon hat.
        var ergebnis = lies("""
                raenge:
                  leiter:
                    reisender:
                      zahlen:
                        parzellen: 1
                    siedler:
                      bedingungen:
                        taetige-stunden: 5
                        gebaute-bloecke: 100
                      zahlen:
                        parzellen: 2
                """);

        assertEquals(List.of("reisender"), ergebnis.werte().schluessel());
        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("gebaute-bloecke"),
                ergebnis.klagen().get(0));
        assertTrue(ergebnis.klagen().get(0).contains("leichter"),
                "die Klage muss sagen, WARUM die ganze Stufe faellt: "
                        + ergebnis.klagen().get(0));
        for (String bekannt : Bedingungsart.alleSchluessel()) {
            assertTrue(ergebnis.klagen().get(0).contains(bekannt),
                    "die Klage muss sagen, was es gibt: " + ergebnis.klagen().get(0));
        }
    }

    @Test
    @DisplayName("Eine Stufe mit unbekannter Grenze faellt GANZ weg")
    void unbekannteGrenze() {
        var ergebnis = lies("""
                raenge:
                  leiter:
                    reisender:
                      zahlen:
                        heimatpunkte: 1
                """);

        assertEquals(List.of(), ergebnis.werte().schluessel());
        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("heimatpunkte"),
                ergebnis.klagen().get(0));
        assertTrue(ergebnis.klagen().get(0).contains("nichts tut"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Eine Bedingung, die keine Zahl ist, kostet ihre Stufe")
    void bedingungKeineZahl() {
        var ergebnis = lies("""
                raenge:
                  leiter:
                    reisender: {}
                    siedler:
                      bedingungen:
                        taetige-stunden: viel
                """);

        assertEquals(List.of("reisender"), ergebnis.werte().schluessel());
        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("ganze Zahl von 1 bis"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Ein Recht MIT Vorsilbe wird beklagt und nennt die Loesung")
    void rechtMitVorsilbe() {
        var ergebnis = lies("""
                raenge:
                  leiter:
                    reisender: {}
                    siedler:
                      bedingungen:
                        parzellen: 1
                      rechte:
                        - freimarken.laden.mieten
                """);

        assertEquals(List.of("reisender"), ergebnis.werte().schluessel());
        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("Schreib laden.mieten"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Die unterste Stufe mit Bedingung kostet die GANZE Leiter")
    void untersteMitBedingung() {
        // Hier ist der Rueckfall richtig: Ein neuer Spieler stuende sonst auf KEINER
        // Stufe, also ohne jedes Recht, das die Leiter vergibt.
        var ergebnis = lies("""
                raenge:
                  leiter:
                    siedler:
                      bedingungen:
                        parzellen: 1
                """);

        assertEquals(Rangwerte.VORGABE_LEITER.size(), ergebnis.werte().leiter().size(),
                "es gilt die Leiter des Programms");
        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("ersten Beitritt"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Eine LEERE Leiter heisst etwas anderes als eine fehlende")
    void leereLeiter() {
        var mitAbschnitt = lies("raenge:\n  leiter: {}\n");
        assertEquals(0, mitAbschnitt.werte().leiter().size());
        assertFalse(mitAbschnitt.werte().greift());

        var ohneAbschnitt = lies("raenge:\n  an: true\n");
        assertEquals(Rangwerte.VORGABE_LEITER.size(),
                ohneAbschnitt.werte().leiter().size(),
                "wer nichts schreibt, bekommt die Leiter des Programms");
    }

    @Test
    @DisplayName("Ohne Konfiguration gilt die Vorgabe, und es gibt keine Klage")
    void ohneKonfiguration() {
        var ergebnis = Rangliste.ausKonfiguration(null);

        assertEquals(Rangwerte.VORGABE, ergebnis.werte());
        assertEquals(List.of(), ergebnis.klagen());
    }

    @Test
    @DisplayName("Ein Rueckschritt in der Leiter steht im BERICHT, nicht in den Klagen")
    void rueckschrittImBericht() {
        var ergebnis = lies("""
                raenge:
                  leiter:
                    reisender:
                      zahlen:
                        parzellen: 4
                    siedler:
                      bedingungen:
                        parzellen: 1
                      zahlen:
                        parzellen: 2
                """);

        assertEquals(List.of(), ergebnis.klagen(), "es ist erlaubt");
        assertTrue(ergebnis.bericht().stream()
                        .anyMatch(zeile -> zeile.contains("wirkungslos")),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Die Rechenprobe nennt die Leiter von unten nach oben")
    void rechenprobe() {
        assertEquals("Raenge: 3 Stufen - reisender (ohne Bedingung) -> siedler"
                        + " (5 Stunden taetiger Zeit, 1 eigene Parzellen) -> buerger"
                        + " (40 Stunden taetiger Zeit, 1 eigene Parzellen,"
                        + " 1 gewaehlter Beruf).",
                Rangliste.rechenprobe(Rangwerte.VORGABE));
        assertEquals("Raenge: keine - raenge.an ist aus. Es gelten die Zahlen aus der"
                        + " config.yml, fuer jeden dieselben.",
                Rangliste.rechenprobe(Rangwerte.VORGABE.mitAn(false)));
    }

    @Test
    @DisplayName("Eine Untergrenze, die so hoch steht wie die Leiter, macht sie wirkungslos")
    void wirkungslose() {
        // DIE SCHRANKE, DIE KEINE PROBE SONST FAENGT: Rangzahl nimmt die hoechste Zahl
        // aus Recht UND Datei. Steht in parzellen.hoechstzahl eine 4, bekommt sie jeder
        // Reisende - und der ganze Aufstieg auf dieser Grenze ist wirkungslos, ohne dass
        // irgendwo etwas rot wuerde. Genau so steht die ausgelieferte Datei bis Teil H.
        var saetze = Rangliste.wirkungslose(Rangwerte.VORGABE,
                Map.of("parzellen", 4, "gruppe", 4, "inseln", 1, "wolken", 1));

        assertEquals(4, saetze.size(), saetze.toString());
        assertTrue(saetze.get(0).contains("gruppe"), saetze.get(0));
        assertTrue(saetze.get(0).contains("wirkt auf diese Grenze nicht"), saetze.get(0));

        assertEquals(List.of(), Rangliste.wirkungslose(Rangwerte.VORGABE,
                        Map.of("parzellen", 1, "gruppe", 1, "inseln", 1, "wolken", 1))
                        .stream().filter(z -> z.contains("parzellen")).toList(),
                "mit der Reisendenzahl hebt die Leiter wieder");
    }

    @Test
    @DisplayName("Ein Recht, das es im Plugin nicht gibt, wird benannt")
    void unbekanntesRecht() {
        // Das Attachment setzt es klaglos, und es fragt es nie jemand. Nur ein laufender
        // Server weiss, was angemeldet ist - darum steht diese Frage neben dem Leser und
        // nicht in ihm.
        var werte = new Rangwerte(true, List.of(
                new Rangwerte.Stufe("reisender", Map.of(), Map.of(), List.of()),
                new Rangwerte.Stufe("siedler", Map.of(Bedingungsart.PARZELLEN, 1L),
                        Map.of(), List.of("laden.miete"))));

        var funde = Rangliste.unbekannteRechte(werte,
                Set.of("freimarken.laden.mieten", "freimarken.parzelle.nehmen"));

        assertEquals(1, funde.size(), funde.toString());
        assertTrue(funde.get(0).contains("freimarken.laden.miete"), funde.get(0));
        assertEquals(List.of(), Rangliste.unbekannteRechte(Rangwerte.VORGABE,
                        Set.of("freimarken.laden.mieten", "freimarken.freistadt.gruenden",
                                "freimarken.beruf.spezialisierung")),
                "was es gibt, wird nicht beklagt");
    }

    @Test
    @DisplayName("Die ausgelieferte config.yml bringt GENAU die Leiter des Programms")
    void ausgelieferteDatei() throws Exception {
        try (var strom = getClass().getResourceAsStream("/config.yml")) {
            assertTrue(strom != null, "config.yml liegt nicht im Klassenpfad");
            var datei = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                    strom, java.nio.charset.StandardCharsets.UTF_8));
            var ergebnis = Rangliste.ausKonfiguration(datei);

            assertEquals(List.of(), ergebnis.klagen(),
                    "die ausgelieferte Datei klagt: " + ergebnis.klagen());
            assertEquals(Rangwerte.VORGABE, ergebnis.werte());
        }
    }
}
