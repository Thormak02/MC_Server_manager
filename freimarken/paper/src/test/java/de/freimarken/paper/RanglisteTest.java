package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Freistadtwerte;
import de.freimarken.kern.Inselwerte;
import de.freimarken.kern.Rangwerte;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Wolkenwerte;
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

    /**
     * Die fuenf Untergrenzen aus der {@code config.yml}, wie der Startbericht sie reicht.
     *
     * <p>Als Methode und nicht als Tafel je Probe: Kommt eine sechste Grenze dazu, ist
     * hier EINE Zeile zu aendern - und die Probe darunter faellt nicht aus, sondern
     * prueft die neue Grenze mit.
     */
    private static Map<String, Integer> untergrenzen(int parzellen, int gruppe,
                                                     int inseln, int wolken,
                                                     int freistaedte) {
        return Map.of("parzellen", parzellen, "gruppe", gruppe, "inseln", inseln,
                "wolken", wolken, "freistaedte", freistaedte);
    }

    @Test
    @DisplayName("Eine Untergrenze, die so hoch steht wie die Leiter, macht sie wirkungslos")
    void wirkungslose() {
        // DIE SCHRANKE, DIE KEINE PROBE SONST FAENGT: Rangzahl nimmt die hoechste Zahl
        // aus Recht UND Datei. Steht in parzellen.hoechstzahl eine 4, bekommt sie jeder
        // Reisende - und der ganze Aufstieg auf dieser Grenze ist wirkungslos, ohne dass
        // irgendwo etwas rot wuerde. Genau so stand die ausgelieferte Datei bis Teil H.
        var saetze = Rangliste.wirkungslose(Rangwerte.VORGABE,
                untergrenzen(4, 4, 1, 1, 1));

        // ZWEI Zeilen und nicht fuenf: Die Leiter staffelt nur die Parzellen und die
        // Gruppenfelder. Inseln, Wolkeninseln und Freistaedte stehen auf ALLEN Stufen
        // bei 1, und die Datei nennt genau diese 1 - daran ist nichts zu beklagen. Eine
        // Warnung dort waere die Haelfte aller Zeilen, und ein Startbericht, dessen
        // Warnungen man ueberliest, warnt nicht mehr.
        assertEquals(2, saetze.size(), saetze.toString());
        assertTrue(saetze.get(0).contains("gruppe"), saetze.get(0));
        assertTrue(saetze.get(0).contains("wirkt auf diese Grenze nicht"), saetze.get(0));
        assertTrue(saetze.get(1).contains("parzellen: 4 an JEDEN"), saetze.get(1));
        assertTrue(saetze.get(1).contains("(buerger)"),
                "die Zeile muss die Stufe nennen, die es gewollt haette: "
                        + saetze.get(1));
    }

    @Test
    @DisplayName("Die ausgelieferte Datei und die ausgelieferte Leiter passen zusammen")
    void ausgelieferteWerteSchweigen() {
        // DAS IST TEIL H IN EINER ZEILE: Die Zahlen der config.yml sind die der
        // UNTERSTEN Stufe. Waere hier je wieder eine hoeher, wuerde diese Probe rot -
        // und zwar genau so lange, bis die Leiter dazu passt.
        assertEquals(List.of(), Rangliste.wirkungslose(Rangwerte.VORGABE,
                untergrenzen(Wirtschaftswerte.VORGABE_HOECHSTZAHL,
                        Wirtschaftswerte.VORGABE_GRUPPEN_FELDER,
                        Inselwerte.VORGABE_HOECHSTZAHL,
                        Wolkenwerte.VORGABE_HOECHSTZAHL,
                        Freistadtwerte.VORGABE_HOECHSTZAHL)),
                "die ausgelieferten Zahlen duerfen den Startbericht nicht zum Reden"
                        + " bringen - sonst steht in jedem Protokoll eine Warnung, die"
                        + " niemand beheben kann");
    }

    @Test
    @DisplayName("Die AUSGELIEFERTE config.yml bringt den Startbericht nicht zum Reden")
    void ausgelieferteDateiSchweigt() throws java.io.IOException {
        // DIE PROBE DARUEBER VERGLEICHT DIE VORGABEN DES PROGRAMMS - diese liest die
        // DATEI. Das ist nicht dasselbe: saveDefaultConfig() schreibt eine vorhandene
        // Datei nie um, aber ein NEUER Server bekommt die ausgelieferte, und die kann von
        // den Vorgaben abweichen, ohne dass irgendwo etwas rot wird. Bis zum 20.11.2026
        // las keine einzige Probe die Zahl parzellen.hoechstzahl aus der echten Datei.
        //
        // Gefahren wird der ECHTE Weg: derselbe Leser und dieselbe Warnung wie im
        // Startlauf. Eine Probe, die die Zahlen nur vergleicht, saehe eine Leiter nicht,
        // die ihre Staffel verloren hat.
        YamlConfiguration datei;
        try (var strom = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(strom, "config.yml muss im Jar liegen");
            datei = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                    strom, java.nio.charset.StandardCharsets.UTF_8));
        }

        // Erst die fuenf Zahlen gegen die Vorgaben - sonst stuende unten eine Warnung,
        // deren Grund man in der Datei suchen muesste.
        assertEquals(Wirtschaftswerte.VORGABE_HOECHSTZAHL,
                datei.getInt(Wirtschaftswerte.PFAD_HOECHSTZAHL), "Datei gegen Vorgabe: "
                        + Wirtschaftswerte.PFAD_HOECHSTZAHL);
        assertEquals(Wirtschaftswerte.VORGABE_GRUPPEN_FELDER,
                datei.getInt(Wirtschaftswerte.PFAD_GRUPPEN_FELDER),
                "Datei gegen Vorgabe: " + Wirtschaftswerte.PFAD_GRUPPEN_FELDER);
        assertEquals(Inselwerte.VORGABE_HOECHSTZAHL,
                datei.getInt(Inselwerte.PFAD_HOECHSTZAHL), "Datei gegen Vorgabe: "
                        + Inselwerte.PFAD_HOECHSTZAHL);
        assertEquals(Wolkenwerte.VORGABE_HOECHSTZAHL,
                datei.getInt(Wolkenwerte.PFAD_HOECHSTZAHL), "Datei gegen Vorgabe: "
                        + Wolkenwerte.PFAD_HOECHSTZAHL);
        assertEquals(Freistadtwerte.VORGABE_HOECHSTZAHL,
                datei.getInt(Freistadtwerte.PFAD_HOECHSTZAHL), "Datei gegen Vorgabe: "
                        + Freistadtwerte.PFAD_HOECHSTZAHL);

        var gelesen = Rangliste.ausKonfiguration(datei);
        assertTrue(gelesen.klagen().isEmpty(),
                "die ausgelieferte Leiter muss ohne Klage durchkommen: "
                        + gelesen.klagen());
        assertEquals(List.of(), Rangliste.wirkungslose(gelesen.werte(), untergrenzen(
                        datei.getInt(Wirtschaftswerte.PFAD_HOECHSTZAHL),
                        datei.getInt(Wirtschaftswerte.PFAD_GRUPPEN_FELDER),
                        datei.getInt(Inselwerte.PFAD_HOECHSTZAHL),
                        datei.getInt(Wolkenwerte.PFAD_HOECHSTZAHL),
                        datei.getInt(Freistadtwerte.PFAD_HOECHSTZAHL))),
                "die ausgelieferte Datei und die ausgelieferte Leiter widersprechen"
                        + " sich - ein neuer Server bekaeme beim ersten Start eine"
                        + " Warnung, die er nicht verursacht hat");
    }

    @Test
    @DisplayName("Steht die Datei ueber der UNTERSTEN Stufe, sind die unteren Sprossen tot")
    void untereStufenTot() {
        // DIE LAGE, DIE DIE ALTE BEDINGUNG UEBERSAH: Die Leiter gibt 1/2/4, die Datei
        // gibt 2. Der Aufstieg zum Siedler bringt auf dieser Grenze nichts - und die
        // alte Frage (unten >= hoechste) war 2 >= 4 und damit falsch. Es wurde
        // geschwiegen.
        var saetze = Rangliste.wirkungslose(Rangwerte.VORGABE,
                untergrenzen(2, 1, 1, 1, 1));

        assertEquals(1, saetze.size(),
                "erwartet wird eine Zeile ueber die toten unteren Stufen: " + saetze);
        assertTrue(saetze.get(0).contains("parzellen: 2 an JEDEN"), saetze.get(0));
        assertTrue(saetze.get(0).contains("reisender, siedler"),
                "die Zeile muss BEIDE toten Stufen nennen: " + saetze.get(0));
        assertFalse(saetze.get(0).contains("wirkt auf diese Grenze nicht"),
                "der Aufstieg zum Buerger wirkt noch - diese Zeile darf nicht die"
                        + " schaerfere sein: " + saetze.get(0));
        assertTrue(saetze.get(0).contains("reisender: 1"),
                "und sie muss sagen, welche Zahl dorthin gehoert: " + saetze.get(0));
    }

    @Test
    @DisplayName("Eine 0 in der Datei schaltet eine Grenze NICHT ab, wenn ein Rang sie vergibt")
    void nullSchliesstNicht() {
        // ZWEI ZEILEN IN ZWEI ABSCHNITTEN, jede fuer sich richtig: Die ausgelieferte
        // config.yml verspricht bei den Wolkeninseln, eine 0 sei "der Weg, Wolkenwerder
        // zuzumachen, ohne das Recht anzufassen". Die ausgelieferte Leiter vergibt
        // freimarken.wolken.1 - und es gilt die hoechste Zahl. Wer die 0 eintraegt,
        // startet neu und findet seine Welt offen.
        var saetze = Rangliste.wirkungslose(Rangwerte.VORGABE,
                untergrenzen(1, 1, 1, 0, 1));

        // DIE ZAHL ALLEIN WAERE EINE SCHWACHE ZUSICHERUNG: Steht hier nur "eine Zeile",
        // sagt die rote Probe spaeter "[] erwartet 1, war 0" - und wer sie liest, weiss
        // nicht, WAS fehlt. Darum nennt die Meldung die Zeile, die hier stehen muss.
        assertEquals(1, saetze.size(),
                "erwartet wird eine Zeile ueber die 0, die nicht schliesst: " + saetze);
        assertTrue(saetze.get(0).contains("wolken: 0"), saetze.get(0));
        assertTrue(saetze.get(0).contains("schaltet diese Grenze NICHT ab"),
                saetze.get(0));
        assertTrue(saetze.get(0).contains("freimarken.wolken.1"),
                "die Zeile muss das Recht nennen, das die 0 schlaegt: " + saetze.get(0));
        assertTrue(saetze.get(0).contains("reisender"),
                "und die UNTERSTE Stufe, denn die hat jeder: " + saetze.get(0));
    }

    @Test
    @DisplayName("Eine Grenze ohne Zahl im Startbericht sagt, dass sie unbeobachtet ist")
    void grenzeOhneZahl() {
        // DER STILLE FALL: Wer eine Grenze in BEKANNTE_ZAHLEN eintraegt und die Karte im
        // Startbericht vergisst, hat eine Grenze, ueber die nie wieder jemand warnt.
        // Genau das war beim Bau von Teil H der erste Lauf.
        var saetze = Rangliste.wirkungslose(Rangwerte.VORGABE,
                Map.of("parzellen", 1, "gruppe", 1, "inseln", 1, "wolken", 1));

        assertEquals(1, saetze.size(),
                "erwartet wird eine Zeile ueber die unbeobachtete Grenze: " + saetze);
        assertTrue(saetze.get(0).contains("freistaedte"), saetze.get(0));
        assertTrue(saetze.get(0).contains("keine Zahl aus der config.yml"),
                saetze.get(0));
    }

    @Test
    @DisplayName("Eine Grenze, die keine Stufe vergibt, wird nicht beklagt")
    void grenzeOhneStufe() {
        // Die Leiter nennt die Wolkeninseln nirgends. Dann ist die Zahl in der Datei die
        // GANZE Regel - auch eine hohe -, und eine Warnung waere eine Aufforderung, eine
        // Leiter zu bauen, die der Betreiber nicht will.
        var ohneWolken = new Rangwerte(true, List.of(
                new Rangwerte.Stufe("reisender", Map.of(), Map.of("parzellen", 1),
                        List.of()),
                new Rangwerte.Stufe("buerger", Map.of(Bedingungsart.PARZELLEN, 1L),
                        Map.of("parzellen", 4), List.of())));

        assertEquals(List.of(), Rangliste.wirkungslose(ohneWolken,
                untergrenzen(1, 9, 9, 9, 9)));
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
