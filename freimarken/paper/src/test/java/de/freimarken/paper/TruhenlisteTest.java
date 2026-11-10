package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockort;
import de.freimarken.kern.Ladeneinheit;
import de.freimarken.kern.Ladenverzeichnis;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Truhenwerte;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Freie Truhe aus der {@code config.yml} gelesen.
 *
 * <p>Dieselbe Bauart wie {@link MietlisteTest} und {@link LadenlisteTest}: Eine kaputte
 * Zeile wird beklagt und die uebrigen gelten weiter, und am Ende wird die MITGELIEFERTE
 * Datei selbst gelesen - sonst waere jede Zusage ueber sie eine Behauptung.
 *
 * <p><b>ZWEI Lesungen, eine Datei.</b> Die Zahlen sind neuladbar, die Stellen nicht;
 * geprueft werden beide hier, denn sie stehen im selben Abschnitt und ihre Klagen muessen
 * sich unterscheiden lassen.
 */
@DisplayName("Die Freie Truhe aus der config.yml")
class TruhenlisteTest {

    private static final String WELT = "freimarken:altfurt";

    /** Die Halle aus der ausgelieferten Datei - stand-1 liegt bei 60..64 / 71..75 / 6..10. */
    private static final Ladenverzeichnis HALLE = Ladenverzeichnis.ausEinheiten(List.of(
            new Ladeneinheit("stand-1", new Quader(60, 71, 6, 64, 75, 10), 62, 72, 8)));

    // ====================================================== die Zahlen

    @Test
    @DisplayName("Ohne Abschnitt gelten die Vorgaben - und es wird gesagt")
    void ohneAbschnittDieVorgabe() {
        var ergebnis = Truhenliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader("kasse: {}")));

        assertEquals(Truhenwerte.VORGABE_ENTNAHME, ergebnis.werte().entnahmeJeTag());
        assertEquals(Truhenwerte.VORGABE_VERBOTEN, ergebnis.werte().verboten());
        assertEquals(List.of(), ergebnis.klagen());
        assertTrue(ergebnis.bericht().stream()
                        .anyMatch(zeile -> zeile.contains("2 von 2 Zeilen")),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Eine 0 ist KEIN Deckel - berichtet und nicht beklagt")
    void nullIstKeinDeckel() {
        // Die Zahl, die das Gegenteil der uebrigen bewirkt, muss im Bericht stehen -
        // dieselbe Form wie beim Tagesdeckel des Bundesmarkts. Eine Klage waere falsch:
        // Es ist eine Entscheidung des Betreibers.
        var ergebnis = lies("""
                freie-truhe:
                  entnahme-je-tag: 0
                """);

        assertEquals(0, ergebnis.werte().entnahmeJeTag());
        assertFalse(ergebnis.werte().gedeckelt());
        assertEquals(List.of(), ergebnis.klagen());
        assertTrue(ergebnis.bericht().stream()
                        .anyMatch(zeile -> zeile.contains("KEIN Deckel")),
                ergebnis.bericht().toString());
        assertTrue(rechenprobe(ergebnis).contains("OHNE Deckel"), rechenprobe(ergebnis));
    }

    @Test
    @DisplayName("Eine negative, eine zu grosse und eine gebrochene Zahl werden beklagt")
    void unbrauchbareZahlen() {
        // Die beiden letzten sind die Einspritzungsproben, die jeder Geschwisterleser
        // hat (Mietliste, Shopliste, Bonusliste): Eine Zahl, die als long durchkommt und
        // als int etwas ANDERES bedeutet. 4294967312 wird verkuerzt zu 16 - genau der
        // Vorgabe, die Klage waere also die einzige Spur. 4294967296 wird zu 0, und das
        // heisst hier KEIN DECKEL: Aus einer zu grossen Zahl wuerde stumm die
        // unbegrenzte Truhe.
        for (String wert : List.of("-1", "3457", "1.5", "\"sechzehn\"",
                "4294967312", "4294967296")) {
            var ergebnis = lies("""
                    freie-truhe:
                      entnahme-je-tag: %s
                    """.formatted(wert));

            assertTrue(klagtUeber(ergebnis, Truhenwerte.PFAD_ENTNAHME),
                    "nicht beklagt: " + wert + " -> " + ergebnis.klagen());
            assertEquals(Truhenwerte.VORGABE_ENTNAHME, ergebnis.werte().entnahmeJeTag(),
                    "und es bleibt bei der Vorgabe");
        }
    }

    @Test
    @DisplayName("Die Rechenprobe nennt die Zahl im Klartext")
    void rechenprobeNenntDieZahl() {
        // Die einzige Schranke gegen den Wert, der im erlaubten Bereich liegt und
        // trotzdem falsch ist. Wer "576 Stueck je Spieler und Tag" liest, sieht eine
        // Doppelkiste am Tag - keine Zahlengrenze sieht sie, denn genau dort liegt die
        // Grenze (Kistenshop.GROESSTE_MENGE).
        var ergebnis = lies("""
                freie-truhe:
                  entnahme-je-tag: 3456
                  verbotene-gegenstaende:
                    - ELYTRA
                    - DIRT
                """);

        assertEquals(3456, ergebnis.werte().entnahmeJeTag(),
                "die Grenze selbst ist erlaubt - eine volle Doppelkiste");
        assertTrue(rechenprobe(ergebnis).contains("3456 Stueck je Spieler und Tag"),
                rechenprobe(ergebnis));
        assertTrue(rechenprobe(ergebnis).contains("FEHLT"),
                "und sagt, WIE gezaehlt wird: " + rechenprobe(ergebnis));
        // BEIDE Haelften der Rechenprobe: Die Zahl der verbotenen Gegenstaende ist die
        // zweite, und sie muss die WIRKLICH geltende sein. Wer seine Liste kuerzt und
        // weiter "18 Gegenstaende" liest, haelt die Vorgabe des Programms fuer seine
        // eigene Einstellung - und die Rechenprobe ist genau dagegen gebaut.
        assertTrue(rechenprobe(ergebnis).contains("2 Gegenstaende duerfen nicht heraus"),
                "die Zahl der eigenen Liste, nicht die der Vorgabe: "
                        + rechenprobe(ergebnis));
    }

    @Test
    @DisplayName("Eine eigene Verbotsliste gilt - ein unbekannter Name wird beklagt")
    void eigeneVerbotsliste() {
        var ergebnis = lies("""
                freie-truhe:
                  verbotene-gegenstaende:
                    - ELYTRA
                    - gibtesnicht
                """);

        assertTrue(ergebnis.werte().istVerboten("elytra"), "gross eingetragen, klein gefragt");
        assertTrue(klagtUeber(ergebnis, Truhenwerte.PFAD_VERBOTEN),
                ergebnis.klagen().toString());
        // GENAU DIESER NAME UND KEIN ZWEITER. Vorher stand hier
        // assertFalse(istVerboten("written_book")) als Zusicherung "getauscht und nicht
        // ergaenzt" - seit die Vorgabe LEER ist, kann diese Zeile nicht mehr rot werden
        // [gefunden von der Lesesuche am 10.11.2026]. Gegen eine leere Vorgabe ist
        // "ersetzt" von "ergaenzt" ueberhaupt nicht zu unterscheiden; dass die Liste
        // getauscht und nicht gemischt wird, haelt WirtschaftsstandTest an einem Stand,
        // der mit einem WIRKLICHEN Verbot anfaengt.
        assertEquals(Set.of("elytra"), ergebnis.werte().verboten(),
                "die kaputte Zeile fiel weg, die gute blieb");
    }

    @Test
    @DisplayName("Eine LEERE Verbotsliste heisst: alles darf hinein")
    void leereVerbotsliste() {
        // Kein Rueckfall auf die Vorgabe: Wer eine leere Liste eintraegt, meint nicht die
        // Vorgabe. Dieselbe Entscheidung wie beim Saatgut und bei blockbuch.benutzbar.
        var ergebnis = lies("""
                freie-truhe:
                  verbotene-gegenstaende: []
                """);

        assertEquals(0, ergebnis.werte().verboten().size());
        assertEquals(List.of(), ergebnis.klagen());
    }

    @Test
    @DisplayName("Zwei Schreibweisen desselben Namens sind EIN Gegenstand - im Bericht auch")
    void zweiSchreibweisenEinGegenstand() {
        // Kleingeschrieben wird an EINER Stelle, naemlich in Truhenwerte. Diese Probe
        // haelt fest, dass der Leser nicht ein zweites Mal glattzieht und danach eine
        // andere Zahl berichtet als die Truhe kennt: Der Bericht ist das einzige, was der
        // Betreiber von dieser Liste ueberhaupt zu sehen bekommt.
        var ergebnis = lies("""
                freie-truhe:
                  verbotene-gegenstaende:
                    - STONE
                    - stone
                    - Dirt
                """);

        assertEquals(2, ergebnis.werte().verboten().size(),
                "STONE und stone sind EIN Gegenstand: " + ergebnis.werte().verboten());
        assertTrue(ergebnis.werte().istVerboten("stone"));
        assertTrue(ergebnis.werte().istVerboten("dirt"));
        assertTrue(ergebnis.bericht().stream().anyMatch(zeile ->
                        zeile.equals(Truhenwerte.PFAD_VERBOTEN + ": 2 Gegenstaende.")),
                "der Bericht nennt die Zahl, die wirklich gilt: " + ergebnis.bericht());
        assertEquals(List.of(), ergebnis.klagen());
    }

    @Test
    @DisplayName("Eine Verbotsliste, die keine Liste ist, wird beklagt")
    void verbotslisteIstKeineListe() {
        var ergebnis = lies("""
                freie-truhe:
                  verbotene-gegenstaende: "written_book"
                """);

        assertTrue(klagtUeber(ergebnis, Truhenwerte.PFAD_VERBOTEN),
                ergebnis.klagen().toString());
        assertEquals(Truhenwerte.VORGABE_VERBOTEN, ergebnis.werte().verboten());
    }

    // ====================================================== die Stellen

    @Test
    @DisplayName("Ohne Stellenzeile gibt es keine Freie Truhe - und es wird gesagt")
    void ohneStellen() {
        var stellen = Truhenliste.stellen(
                YamlConfiguration.loadConfiguration(new StringReader("kasse: {}")),
                WELT, HALLE);

        assertFalse(stellen.verzeichnis().gibtTruhen());
        assertEquals(List.of(), stellen.klagen());
        assertTrue(stellen.bericht().stream()
                        .anyMatch(zeile -> zeile.contains("keine Freie Truhe")),
                stellen.bericht().toString());
    }

    @Test
    @DisplayName("Eine Stelle wird zum Blockort MIT Weltschluessel")
    void stelleWirdBlockort() {
        // Der Weltschluessel kommt FERTIG herein und wird hier nicht gebaut: Genau diese
        // Handarbeit hat am 09.11.2026 den ganzen Ladenbereich tot gelegt, weil die
        // config.yml "altfurt" schreibt und die Welt "freimarken:altfurt" traegt.
        var stellen = stellen("""
                freie-truhe:
                  truhen:
                    - [72, 72, 20]
                    - [80, 72, 20]
                """);

        assertEquals(List.of(new Blockort(WELT, 72, 72, 20),
                        new Blockort(WELT, 80, 72, 20)),
                stellen.verzeichnis().alle());
        assertEquals(List.of(), stellen.klagen());
    }

    @Test
    @DisplayName("Der Bericht nennt JEDE Stelle - und dass sie nur fuer diese Welt gilt")
    void berichtNenntJedeStelle() {
        // Diese Stellen gelten nur fuer die VORHANDENE Welt. Wer Altfurt neu anlegt, hat
        // anderes Gelaende - dann steht die Truhe im Boden, und die einzige Spur davon
        // ist diese Zeile.
        var stellen = stellen("""
                freie-truhe:
                  truhen:
                    - [72, 72, 20]
                """);

        String zeile = stellen.bericht().stream()
                .filter(eine -> eine.startsWith("Truhenstellen:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("keine Stellenzeile: "
                        + stellen.bericht()));
        assertTrue(zeile.contains("72/72/20"), zeile);
        assertTrue(zeile.contains(WELT), zeile);
        assertTrue(zeile.contains("vorhandene Welt"), zeile);
    }

    @Test
    @DisplayName("Dieselbe Stelle zweimal wird beklagt und zaehlt einmal")
    void doppelteStelle() {
        // Sonst stuende in der Startmeldung "2 Freie Truhen", und der Betreiber suchte
        // die zweite.
        var stellen = stellen("""
                freie-truhe:
                  truhen:
                    - [72, 72, 20]
                    - [72, 72, 20]
                """);

        assertEquals(1, stellen.verzeichnis().anzahl());
        assertTrue(stellen.klagen().stream()
                        .anyMatch(klage -> klage.contains("Eintrag 2")),
                stellen.klagen().toString());
    }

    @Test
    @DisplayName("Eine ganzzahlige Kommazahl ist keine Stelle - 1.000 ist nicht 1000")
    void ganzzahligeKommazahl() {
        // Der Fall, den die gebrochene Zahl NICHT prueft: In YAML trennt der Punkt keine
        // Tausender, 1.000 ist die Kommazahl 1,0 - und die ist ganzzahlig. Ohne die Frage
        // nach Double und Float kaeme sie durch, und die bei x=1000 von Hand eingemessene
        // Kiste waere als Stelle 1/72/20 angemeldet: Die wirkliche Truhe waere eine
        // gewoehnliche Kiste, und irgendwo in der Wand steckte eine Freie Truhe, die
        // niemand sieht.
        var stellen = stellen("""
                freie-truhe:
                  truhen:
                    - [1.000, 72, 20]
                """);

        assertFalse(stellen.verzeichnis().gibtTruhen(),
                "1.000 ist keine ganze Zahl: " + stellen.verzeichnis().alle());
        assertTrue(stellen.klagen().stream()
                        .anyMatch(klage -> klage.contains("an Stelle 1")),
                stellen.klagen().toString());
    }

    @Test
    @DisplayName("Eine Stelle IN einer Ladeneinheit wird abgewiesen")
    void stelleInEinerLadeneinheit() {
        // Dort kaeme niemand ausser dem Mieter heran, und der nimmt ohne Deckel - seine
        // Kiste fragt keine Truhenregel. Genau diese Verwechslung passiert beim Einmessen
        // von Hand: Beide Angaben stehen in derselben Datei und nennen Koordinaten
        // derselben Halle.
        var stellen = stellen("""
                freie-truhe:
                  truhen:
                    - [62, 72, 8]
                """);

        assertFalse(stellen.verzeichnis().gibtTruhen());
        assertTrue(stellen.klagen().stream()
                        .anyMatch(klage -> klage.contains("stand-1")),
                stellen.klagen().toString());
    }

    @Test
    @DisplayName("Kaputte Eintraege werden beklagt, die guten bleiben stehen")
    void kaputteEintraege() {
        var stellen = stellen("""
                freie-truhe:
                  truhen:
                    - [72, 72]
                    - [72, 72.5, 20]
                    - "72 72 20"
                    - [100, 72, 20]
                """);

        assertEquals(List.of(new Blockort(WELT, 100, 72, 20)),
                stellen.verzeichnis().alle());
        assertEquals(3, stellen.klagen().size(), stellen.klagen().toString());
        assertTrue(stellen.klagen().stream()
                        .anyMatch(klage -> klage.contains("Stelle 2")),
                "die gebrochene Zahl wird mit ihrer Stelle genannt: " + stellen.klagen());
    }

    @Test
    @DisplayName("Eine Stellenzeile, die keine Liste ist, wird beklagt")
    void stellenSindKeineListe() {
        var stellen = stellen("""
                freie-truhe:
                  truhen: 72
                """);

        assertFalse(stellen.verzeichnis().gibtTruhen());
        assertTrue(stellen.klagen().stream().anyMatch(klage ->
                klage.startsWith("freie-truhe.truhen")), stellen.klagen().toString());
    }

    @Test
    @DisplayName("Eine LEERE Stellenliste ist erlaubt und heisst: keine Truhe")
    void leereStellenliste() {
        var stellen = stellen("""
                freie-truhe:
                  truhen: []
                """);

        assertFalse(stellen.verzeichnis().gibtTruhen());
        assertEquals(List.of(), stellen.klagen());
        assertTrue(stellen.bericht().stream().anyMatch(zeile -> zeile.contains("leer")),
                stellen.bericht().toString());
    }

    // ====================================================== die echte Datei

    @Test
    @DisplayName("Die Rechenprobe sagt, ob gezaehlt wird - und damit, wer hineinsehen"
            + " darf")
    void rechenprobeNenntDieBetriebsart() {
        // Die EINE Zeile, die der Betreiber beim Start und nach jedem Neuladen liest.
        // Sie muss die Betriebsart nennen, denn ein Neuladen kann sie umschalten: Wer
        // einen Deckel eintraegt, sperrt damit den zweiten Zuschauer aus, und das darf
        // er nicht aus dem Spiel erfahren muessen.
        String ohne = Truhenliste.rechenprobe(
                new Truhenwerte(Truhenwerte.KEIN_DECKEL, Set.of()));
        String mitDeckel = Truhenliste.rechenprobe(new Truhenwerte(16, Set.of()));
        String nurVerbote = Truhenliste.rechenprobe(
                new Truhenwerte(Truhenwerte.KEIN_DECKEL, Set.of("written_book")));

        assertTrue(ohne.contains("GAR NICHTS"), ohne);
        assertTrue(ohne.contains("gemeinsam hineinsehen"), ohne);
        assertTrue(mitDeckel.contains("nur EINER hinein"), mitDeckel);
        assertTrue(nurVerbote.contains("nur EINER hinein"),
                "auch ein Verbot ohne Deckel zaehlt: " + nurVerbote);
    }

    @Test
    @DisplayName("Die AUSGELIEFERTE Datei traegt die Vorgaben des Programms")
    void ausgelieferteDateiStimmt() {
        // Sonst waere die Vorgabe im Programm eine Behauptung ueber eine Datei, die etwas
        // anderes sagt - und beim ersten Start galte die Datei.
        var ergebnis = Truhenliste.ausKonfiguration(ausgelieferteDatei());

        assertEquals(Truhenwerte.VORGABE, ergebnis.werte(),
                "config.yml und Truhenwerte.VORGABE muessen dasselbe sagen");
        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
    }

    @Test
    @DisplayName("Die AUSGELIEFERTE Datei traegt KEINE Stelle - und darf es nicht")
    void ausgelieferteDateiOhneStelle() {
        // Eine geratene Koordinate zeigte in unerschlossenes Gelaende, und dort stuende
        // dann eine Kiste, aus der jeder nehmen darf. Wer eine Freie Truhe will, misst
        // sie von Hand ein - wie die Ladeneinheiten und die Torquader.
        var stellen = Truhenliste.stellen(ausgelieferteDatei(), WELT, HALLE);

        assertFalse(stellen.verzeichnis().gibtTruhen());
        assertEquals(List.of(), stellen.klagen(), stellen.klagen().toString());
    }

    private static Truhenliste.Ergebnis lies(String yaml) {
        return Truhenliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    private static Truhenliste.Stellen stellen(String yaml) {
        return Truhenliste.stellen(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)), WELT, HALLE);
    }

    private static boolean klagtUeber(Truhenliste.Ergebnis ergebnis, String pfad) {
        return ergebnis.klagen().stream().anyMatch(klage -> klage.startsWith(pfad));
    }

    private static String rechenprobe(Truhenliste.Ergebnis ergebnis) {
        return ergebnis.bericht().stream()
                .filter(zeile -> zeile.startsWith("Freie Truhe:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("keine Rechenprobe im Bericht: "
                        + ergebnis.bericht()));
    }

    private static YamlConfiguration ausgelieferteDatei() {
        try (InputStream strom = TruhenlisteTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertNotNull(strom, "config.yml muss im Jar liegen");
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));
        } catch (java.io.IOException fehler) {
            throw new IllegalStateException("config.yml nicht lesbar", fehler);
        }
    }
}
