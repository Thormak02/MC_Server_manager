package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Erzwerte;
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
                    Wolkenwerte.PFAD_HOECHSTZAHL, Wolkenwerte.PFAD_BIOME,
                    Wolkenwerte.PFAD_STARTTRUHE, Erzwerte.PFAD_AN, Erzwerte.PFAD_GRUND,
                    Erzwerte.PFAD_JE_STUFE, Erzwerte.PFAD_HOECHST, Erzwerte.PFAD_MARKEN,
                    Erzwerte.PFAD_TABELLE)) {
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
        assertEquals("Wolkeninseln: GRATIS, eine Insel je Spieler, 10 Biome zur Wahl"
                        + " (Wechsel 2000 Mark), Starttruhe mit 10 Gegenstaenden.",
                Wolkenliste.rechenprobe(Wolkenwerte.VORGABE));
        assertEquals("Wolkeninseln: 5000 Mark je Insel, 3 Inseln je Spieler,"
                        + " nur das Biom swamp (Wechsel gratis), Starttruhe mit 1"
                        + " Gegenstaenden.",
                Wolkenliste.rechenprobe(new Wolkenwerte(5000L, 3, List.of("swamp"),
                        java.util.Map.of("lava_bucket", 1), Erzwerte.VORGABE,
                        de.freimarken.kern.Aufgabenwerte.VORGABE,
                        de.freimarken.kern.Punktwerte.VORGABE, 0L)));
        assertEquals("Wolkeninseln: GRATIS, eine Insel je Spieler, keine Biomwahl,"
                        + " Starttruhe mit 10 Gegenstaenden.",
                Wolkenliste.rechenprobe(Wolkenwerte.VORGABE.mitBiomen(List.of())));
        assertEquals("Wolkeninseln: AUS - hoechstzahl ist 0, es wird keine vergeben.",
                Wolkenliste.rechenprobe(Wolkenwerte.VORGABE.mitHoechstzahl(0)));
        // OHNE Lava, und die Rechenprobe sagt es laut: Das ist der Vertipper mit der
        // groessten Folge - ohne Lava gibt es keinen Generator und damit kein Spiel.
        assertEquals("Wolkeninseln: GRATIS, eine Insel je Spieler, 10 Biome zur Wahl"
                        + " (Wechsel 2000 Mark), Starttruhe mit 1 Gegenstaenden"
                        + " (OHNE Lava).",
                Wolkenliste.rechenprobe(Wolkenwerte.VORGABE.mitStarttruhe(
                        java.util.Map.of("ice", 2))));
    }

    @Test
    @DisplayName("Die Erztabelle kommt mit Gewicht und Mindeststufe an")
    void erztabelle() {
        var ergebnis = lies("""
                wolken:
                  erze:
                    an: true
                    grund-prozent: 25
                    je-stufe-prozent: 5
                    hoechst-prozent: 60
                    marken: false
                    tabelle:
                      coal_ore:
                        gewicht: 40
                        ab-stufe: 0
                      DIAMOND_ORE:
                        gewicht: 2
                        ab-stufe: 7
                      iron_ore:
                        gewicht: 10
                """);

        Erzwerte erze = ergebnis.werte().erze();
        assertEquals(List.of(), ergebnis.klagen(), "keine Klage erwartet");
        assertEquals(25, erze.grundProzent());
        assertEquals(5, erze.jeStufeProzent());
        assertEquals(60, erze.hoechstProzent());
        assertFalse(erze.marken());
        assertEquals(3, erze.tabelle().size());
        assertEquals(40, erze.tabelle().get("coal_ore").gewicht());
        assertEquals(7, erze.tabelle().get("diamond_ore").abStufe(),
                "gross geschrieben in der Datei, klein in der Tabelle");
        assertEquals(0, erze.tabelle().get("iron_ore").abStufe(),
                "eine fehlende ab-stufe heisst 0 - also von Anfang an");
    }

    @Test
    @DisplayName("Eine kaputte Zeile kostet NUR ihre Zeile")
    void kaputteErzzeileKostetNurSichSelbst() {
        // Die tragende Linie dieses Lesers, und hier gibt es fuenf Wege, eine Zeile zu
        // verlieren: ein Name, den Minecraft nicht kennt; der Block, AUS dem getauscht
        // wird; eine Zeile ohne Unterschluessel; ein Gewicht von 0; eine Stufe, die keine
        // Zahl ist. Keiner davon darf die uebrigen mitnehmen.
        var ergebnis = lies("""
                wolken:
                  erze:
                    tabelle:
                      coal_ore:
                        gewicht: 40
                      gibt_es_nicht:
                        gewicht: 5
                      cobblestone:
                        gewicht: 5
                      iron_ore: 7
                      gold_ore:
                        gewicht: 0
                      lapis_ore:
                        gewicht: 3
                        ab-stufe: viele
                """);

        assertEquals(1, ergebnis.werte().erze().tabelle().size(),
                "nur die Kohle ist brauchbar: " + ergebnis.werte().erze().tabelle());
        assertEquals(40, ergebnis.werte().erze().tabelle().get("coal_ore").gewicht());
        assertEquals(5, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().stream()
                        .anyMatch(zeile -> zeile.contains("cobblestone")
                                && zeile.contains("AUS dem getauscht wird")),
                "Kobbelstein in der Tabelle waere ein Tausch gegen sich selbst: "
                        + ergebnis.klagen());
        assertTrue(ergebnis.klagen().stream()
                        .anyMatch(zeile -> zeile.contains("iron_ore")
                                && zeile.contains("gewicht")),
                "eine Zeile ohne Unterschluessel nennt, was erwartet wird: "
                        + ergebnis.klagen());
        for (String klage : ergebnis.klagen()) {
            assertTrue(klage.contains("die uebrigen gelten"), klage);
        }
    }

    @Test
    @DisplayName("Eine LEERE Tabelle heisst etwas anderes als eine fehlende")
    void leereTabelle() {
        // Wer alle Zeilen herausnimmt, will reinen Kobbelstein und soll ihn bekommen,
        // ohne den Schalter zu finden. Mit der Vorgabe als Rueckfall kaemen genau die
        // Erze zurueck, die er gerade gestrichen hat.
        var mitAbschnitt = lies("""
                wolken:
                  erze:
                    tabelle: {}
                """);
        assertEquals(0, mitAbschnitt.werte().erze().tabelle().size());
        assertFalse(mitAbschnitt.werte().erze().greift());

        var ohneAbschnitt = lies("wolken:\n  preis: 0\n");
        assertEquals(Erzwerte.VORGABE_TABELLE.size(),
                ohneAbschnitt.werte().erze().tabelle().size(),
                "fehlt der Abschnitt, gilt die Tabelle des Programms");
    }

    @Test
    @DisplayName("Jede Zeile kaputt: der Bericht sagt, dass es nur Kobbelstein gibt")
    void jedeZeileKaputt() {
        var ergebnis = lies("""
                wolken:
                  erze:
                    tabelle:
                      gibt_es_nicht:
                        gewicht: 5
                """);

        assertEquals(0, ergebnis.werte().erze().tabelle().size());
        assertTrue(ergebnis.bericht().stream()
                        .anyMatch(zeile -> zeile.contains("keine einzige Zeile war"
                                + " brauchbar")),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Ein Deckel UNTER dem Grundwert klagt und nimmt die ganze Vorgabe")
    void deckelUnterGrund() {
        // Alles oder nichts, wie bei den Verfallsfristen: Die drei Zahlen haengen
        // voneinander ab, und ein einzeln zurechtgebogener Wert waere eine Mischung aus
        // Datei und Vorgabe, die der Betreiber nirgends nachlesen kann. Der SCHALTER
        // bleibt aber stehen - wer abschaltet, bekommt keine eingeschaltete Tabelle
        // zurueck.
        var ergebnis = lies("""
                wolken:
                  erze:
                    an: false
                    grund-prozent: 50
                    hoechst-prozent: 10
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("saenke"),
                ergebnis.klagen().get(0));
        assertEquals(Erzwerte.VORGABE_GRUND, ergebnis.werte().erze().grundProzent());
        assertEquals(Erzwerte.VORGABE_HOECHST, ergebnis.werte().erze().hoechstProzent());
        assertFalse(ergebnis.werte().erze().an(), "der Schalter bleibt stehen");
    }

    @Test
    @DisplayName("Eine Prozentzahl ueber 100 und ein Ja ohne Ja werden beklagt")
    void unbrauchbareZahlen() {
        var ergebnis = lies("""
                wolken:
                  erze:
                    an: vielleicht
                    grund-prozent: 300
                    je-stufe-prozent: -1
                """);

        assertEquals(3, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.werte().erze().an(), "es bleibt beim ausgelieferten Ja");
        assertEquals(Erzwerte.VORGABE_GRUND, ergebnis.werte().erze().grundProzent());
        assertEquals(Erzwerte.VORGABE_JE_STUFE,
                ergebnis.werte().erze().jeStufeProzent());
    }

    @Test
    @DisplayName("Die Erz-Rechenprobe nennt Deckelstufe, Zahl der Erze und die Marke")
    void erzprobe() {
        // Die Zahlen, bei denen ein Vertipper am teuersten ist: Wer je-stufe-prozent: 20
        // tippt, soll beim Start lesen, dass der Deckel schon bei Stufe 2 erreicht ist -
        // und nicht erst merken, dass seine Herausforderungen ab der dritten nichts mehr
        // bringen.
        assertEquals("Kobbelsteingenerator: 15 Prozent Erz bei Stufe 0, je Stufe 2 mehr"
                        + " bis 40 Prozent ab Stufe 13; 9 Erze, davon 2 von Anfang an.",
                Wolkenliste.erzprobe(Erzwerte.VORGABE));
        assertEquals("Kobbelsteingenerator: 20 Prozent Erz bei Stufe 0, je Stufe 20 mehr"
                        + " bis 40 Prozent ab Stufe 1; 9 Erze, davon 2 von Anfang an.",
                Wolkenliste.erzprobe(Erzwerte.VORGABE.mitProzent(20, 20, 40)));
        assertEquals("Kobbelsteingenerator: 15 Prozent Erz bei Stufe 0, keine Steigerung"
                        + " je Stufe; 9 Erze, davon 2 von Anfang an.",
                Wolkenliste.erzprobe(Erzwerte.VORGABE.mitProzent(15, 0, 40)));
        assertEquals("Kobbelsteingenerator: 15 Prozent Erz bei Stufe 0, je Stufe 2 mehr"
                        + " bis 40 Prozent ab Stufe 13; 9 Erze, davon 2 von Anfang an;"
                        + " OHNE Blockmarke, der Abbau zahlt Lohn.",
                Wolkenliste.erzprobe(Erzwerte.VORGABE.mitMarken(false)));
        assertEquals("Kobbelsteingenerator: nur Kobbelstein - wolken.erze.an ist aus.",
                Wolkenliste.erzprobe(Erzwerte.VORGABE.mitAn(false)));
        assertEquals("Kobbelsteingenerator: nur Kobbelstein - die Erztabelle ist leer.",
                Wolkenliste.erzprobe(Erzwerte.VORGABE.mitTabelle(java.util.Map.of())));
    }
}
