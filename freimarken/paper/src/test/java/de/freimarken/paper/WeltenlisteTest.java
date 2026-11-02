package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Quader;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das Lesen der Weltenliste - ohne Server.
 *
 * <p>Moeglich, weil {@code YamlConfiguration} reine Dateiarbeit ist und keinen laufenden
 * Server braucht. Geprueft wird damit genau das, was der Betreiber tippt, und nicht eine
 * nachgebaute Karte, die sich anders verhaelt als die echte Datei.
 */
class WeltenlisteTest {

    private static Weltenliste.Ergebnis lies(String yaml) {
        return Weltenliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml))
                        .getConfigurationSection("welten"), "marktstadt", "altfurt");
    }

    @Test
    @DisplayName("Eine vollstaendige Liste kommt vollstaendig an")
    void vollstaendigeListe() {
        var ergebnis = lies("""
                welten:
                  marktstadt:
                    art: raster
                    umgebung: normal
                    kante: 3000
                    sprung-bestaetigt: "2989 -> 8000"
                  altfurt:
                    art: gewoehnlich
                    kante: 800
                  nether:
                    art: gewoehnlich
                    umgebung: nether
                    kante: 2000
                """);

        assertTrue(ergebnis.klagen().isEmpty(), "unerwartete Klagen: " + ergebnis.klagen());
        assertEquals(3, ergebnis.welten().size());

        Weltangabe markt = ergebnis.welten().get(0);
        assertEquals("marktstadt", markt.name());
        assertEquals(Weltangabe.Art.RASTER, markt.art());
        assertEquals(World.Environment.NORMAL, markt.umgebung());
        assertEquals(3000, markt.kante());
        assertEquals("2989 -> 8000", markt.sprungBestaetigt());

        // Ohne Angabe ist es die Oberwelt - die haeufigste Antwort muss man nicht tippen.
        assertEquals(World.Environment.NORMAL, ergebnis.welten().get(1).umgebung());
        assertEquals(World.Environment.NETHER, ergebnis.welten().get(2).umgebung());
    }

    @Test
    @DisplayName("Die Reihenfolge aus der Datei bleibt erhalten")
    void reihenfolgeBleibt() {
        // Wichtig, weil die Marktstadt zuerst entstehen soll: An ihr haengt alles
        // Weitere, und ein Fehlschlag dort soll auffallen, bevor Stunden an
        // Weltenerzeugung vergangen sind.
        var ergebnis = lies("""
                welten:
                  eins:
                    art: gewoehnlich
                  zwei:
                    art: gewoehnlich
                  drei:
                    art: gewoehnlich
                """);

        assertEquals(java.util.List.of("eins", "zwei", "drei"),
                ergebnis.welten().stream().map(Weltangabe::name).toList());
    }

    @Test
    @DisplayName("Eine fehlende Art wird uebersprungen, nicht geraten")
    void fehlendeArtWirdUebersprungen() {
        // Der teuerste denkbare Fehler: "gewoehnlich" als Vorgabe wuerde aus der
        // Marktstadt beim naechsten Start eine Welt mit Huegeln und Baeumen machen -
        // mitten in den bezahlten Parzellen, und nicht zurueckzunehmen.
        var ergebnis = lies("""
                welten:
                  marktstadt:
                    kante: 3000
                """);

        assertTrue(ergebnis.welten().isEmpty(), "keine Welt ohne Art");
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("welten.marktstadt.art"),
                "die Klage muss die Stelle nennen: " + ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Eine unbekannte Art oder Umgebung wird benannt und uebersprungen")
    void unbekannteWoerter() {
        var ergebnis = lies("""
                welten:
                  eins:
                    art: huegelig
                  zwei:
                    art: gewoehnlich
                    umgebung: mond
                  drei:
                    art: gewoehnlich
                """);

        assertEquals(java.util.List.of("drei"),
                ergebnis.welten().stream().map(Weltangabe::name).toList(),
                "die gesunde Welt entsteht trotzdem");
        assertEquals(2, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("huegelig"));
        assertTrue(ergebnis.klagen().get(1).contains("mond"));
    }

    @Test
    @DisplayName("Ein unmoeglicher Weltname wird abgefangen, bevor Bukkit wirft")
    void unmoeglicherName() {
        // Bukkit wirft bei einem NamespacedKey mit Grossbuchstaben - und zwar mitten im
        // Start, wenn andere Welten schon stehen.
        var ergebnis = lies("""
                welten:
                  Marktstadt:
                    art: raster
                """);

        assertTrue(ergebnis.welten().isEmpty());
        assertTrue(ergebnis.klagen().get(0).contains("Kleinbuchstaben"));
    }

    @Test
    @DisplayName("Eine negative Kante kostet die Grenze, nicht die Welt")
    void negativeKante() {
        var ergebnis = lies("""
                welten:
                  altfurt:
                    art: gewoehnlich
                    kante: -800
                """);

        assertEquals(1, ergebnis.welten().size(), "die Welt bleibt");
        assertEquals(0, ergebnis.welten().get(0).kante());
        assertTrue(ergebnis.welten().get(0).hatGrenze() == false);
        assertEquals(1, ergebnis.klagen().size());
    }

    @Test
    @DisplayName("Ohne Kante gibt es keine Grenze - und das ist keine Klage")
    void ohneKanteKeineGrenze() {
        // Wolkenwerder hat keine feste Kante (Plan 4). Das ist eine Angabe und kein
        // Versehen, also auch keine Meldung.
        var ergebnis = lies("""
                welten:
                  wolkenwerder:
                    art: leer
                """);

        assertTrue(ergebnis.klagen().isEmpty());
        assertEquals(Weltangabe.Art.LEER, ergebnis.welten().get(0).art());
        assertTrue(!ergebnis.welten().get(0).hatGrenze());
    }

    @Test
    @DisplayName("Ein fehlender Abschnitt klagt, statt still nichts zu tun")
    void fehlenderAbschnitt() {
        var ergebnis = lies("parzellen:\n  hoechstzahl: 4\n");

        assertTrue(ergebnis.welten().isEmpty());
        assertEquals(1, ergebnis.klagen().size());
    }

    @Test
    @DisplayName("Die ausgelieferte config.yml ergibt eine Liste ohne Klagen")
    void dieAusgelieferteDatei() throws Exception {
        // Die Probe, die ein Tippfehler in der mitgelieferten Datei nicht ueberlebt. Ohne
        // sie faellt er erst beim Start eines echten Servers auf - und dann hat der
        // Betreiber eine Welt zu wenig oder eine zu viel.
        try (var strom = getClass().getResourceAsStream("/config.yml")) {
            assertTrue(strom != null, "config.yml liegt nicht im Klassenpfad");
            var datei = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                    strom, java.nio.charset.StandardCharsets.UTF_8));
            var ergebnis = Weltenliste.ausKonfiguration(
                    datei.getConfigurationSection("welten"), "marktstadt", "altfurt");

            assertTrue(ergebnis.klagen().isEmpty(),
                    "die ausgelieferte Datei klagt: " + ergebnis.klagen());
            assertEquals("marktstadt", ergebnis.welten().get(0).name(),
                    "die Marktstadt steht zuerst - an ihr haengt alles Weitere");
            assertEquals(Weltangabe.Art.RASTER, ergebnis.welten().get(0).art());
            // Jede weitere Welt ist gewoehnlich; eine zweite Rasterwelt gibt es nicht,
            // und sie waere ein Raster ohne Besitzbuch.
            assertEquals(1, ergebnis.welten().stream()
                    .filter(w -> w.art() == Weltangabe.Art.RASTER).count());
            // Die Namen und nicht nur ihre Zahl: Ein Weltname ist nach Plan 12a.6 nur
            // lesbar - an ihm haengen Sicherungen und spaeter Datensaetze. Ein Tippfehler
            // hier ist beim ersten Start dauerhaft, und dieser Vergleich ist die einzige
            // Stelle, an der er vorher auffaellt.
            assertEquals(java.util.List.of("marktstadt", "altfurt", "freistaedte",
                            "hafenwerder", "hochwald", "kornmark", "nether", "end"),
                    ergebnis.welten().stream().map(Weltangabe::name).toList(),
                    "die Weltnamen aus Plan Abschnitt 4");

            // Teil C: Altfurt ist Abenteuer, und jede Welt ausser Altfurt hat ein Tor.
            var altfurt = ergebnis.welten().stream()
                    .filter(w -> w.name().equals("altfurt")).findFirst().orElseThrow();
            assertEquals(GameMode.ADVENTURE, altfurt.spielmodus(),
                    "Plan Abschnitt 4 fuehrt Altfurt als Abenteuer");
            assertTrue(!altfurt.hatTor(), "in Altfurt stehen die Tore, keines fuehrt hinein");
            assertEquals(7, ergebnis.welten().stream().filter(Weltangabe::hatTor).count(),
                    "sieben Ziele, sieben Tore");
            ergebnis.welten().stream().filter(w -> !w.name().equals("altfurt"))
                    .forEach(w -> assertTrue(w.hatTor(),
                            "ohne Tor kaeme man nur mit /warp hin: " + w.name()));
        }
    }

    @Test
    @DisplayName("Die Marktstadt ohne art: raster kostet die Marktstadt, nicht das Raster")
    void marktstadtMussRasterSein() {
        // Der unumkehrbare Fall: Wer den Block kopiert oder eine config.yml von einem
        // anderen Server uebernimmt, hat schnell art: gewoehnlich bei der Marktstadt
        // stehen. Entstuende sie so, waeren Huegel und Baeume mitten in den bezahlten
        // Parzellen - und zwar bevor eine Pruefung sie wieder loswerden koennte. Also
        // entsteht sie gar nicht, und das Plugin bleibt aus.
        var ergebnis = lies("""
                welten:
                  marktstadt:
                    art: gewoehnlich
                    kante: 3000
                """);

        assertTrue(ergebnis.welten().isEmpty(), "die Welt wird nicht angelegt");
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("muss raster sein"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Eine zweite Rasterwelt gibt es nicht")
    void nurEineRasterwelt() {
        // Sie bekaeme Mauern und Wege vom selben Generator, aber kein Besitzbuch und
        // keine Schutzwache - beide sind auf die Marktstadt angemeldet. Das Ergebnis
        // waere eine Parzellenstadt, in der jeder ueberall baut.
        var ergebnis = lies("""
                welten:
                  marktstadt:
                    art: raster
                    kante: 3000
                  marktstadt2:
                    art: raster
                    kante: 3000
                """);

        assertEquals(java.util.List.of("marktstadt"),
                ergebnis.welten().stream().map(Weltangabe::name).toList());
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("allein welten.marktstadt"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Eine Kante, die keine Zahl ist, wird benannt - nicht stillschweigend 0")
    void kanteAlsText() {
        // getInt gibt hier 0 zurueck, ohne ein Wort. Die Welt liefe dann mit der
        // Vanille-Grenze von 59.999.968: Spieler laufen beliebig weit hinaus, es wird
        // endlos Gelaende erzeugt, und die Vorgenerierung lief gegen eine Grenze, die
        // nie gesetzt wurde.
        var ergebnis = lies("""
                welten:
                  altfurt:
                    art: gewoehnlich
                    kante: "800"
                """);

        assertEquals(0, ergebnis.welten().get(0).kante());
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("keine Zahl"), ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Ein Tausenderpunkt ist in YAML ein Komma")
    void kanteMitTausenderpunkt() {
        // Die Kommentare der ausgelieferten Datei schreiben Tausender selbst mit Punkt
        // ("Plan Abschnitt 4 nennt 3.000"). Wer das in den Wert uebernimmt, bekommt 6 -
        // und ohne diese Klage eine Welt mit einer Grenze von sechs Bloecken.
        var ergebnis = lies("""
                welten:
                  freistaedte:
                    art: gewoehnlich
                    kante: 6.000
                """);

        assertEquals(0, ergebnis.welten().get(0).kante());
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("keine ganze Zahl"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Eine Kante jenseits des Hoechstmasses reisst den Start nicht ab")
    void kanteZuGross() {
        // Beides fruehere Fehlerbilder in einem Test: 3000000000 passt in kein int und
        // wurde von getInt auf -1294967296 gekuerzt - die Klage nannte dann eine Zahl,
        // die nirgends in der Datei stand. Und ein Wert ueber dem Hoechstmass ging
        // ungeprueft an setSize, das wirft: mitten im Start, mit der Marktstadt geladen.
        var ergebnis = lies("""
                welten:
                  hochwald:
                    art: gewoehnlich
                    kante: 3000000000
                  kornmark:
                    art: gewoehnlich
                    kante: 60000000
                """);

        assertEquals(2, ergebnis.welten().size(), "die Welten bleiben");
        assertEquals(0, ergebnis.welten().get(0).kante());
        assertEquals(0, ergebnis.welten().get(1).kante());
        assertEquals(2, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("3000000000"),
                "die Klage nennt die Zahl, die in der Datei steht: "
                        + ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Eine Kante unter dem Mindestmass ist ein Kaefig")
    void kanteZuKlein() {
        var ergebnis = lies("""
                welten:
                  altfurt:
                    art: gewoehnlich
                    kante: 8
                """);

        assertEquals(0, ergebnis.welten().get(0).kante());
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("Kaefig"), ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Was das Plugin mitbringt und in der Datei fehlt, wird benannt")
    void fehlendeWeltNachDemUpdate() {
        // Der stille Fall nach einem Update: saveDefaultConfig() schreibt eine vorhandene
        // Datei nie um, und getKeys mischt die Vorgaben aus der Jar nicht ein. Die
        // Marktstadt entsteht - ihre art holt sich getString stumm aus der Jar -, die
        // uebrigen sieben Welten nicht, und im Protokoll stand darueber kein Wort.
        var eigene = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                welten:
                  marktstadt:
                    art: raster
                    kante: 3000
                """));
        var vorgabe = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                welten:
                  marktstadt:
                    art: raster
                    kante: 3000
                  kornmark:
                    art: gewoehnlich
                    kante: 2000
                """));
        eigene.setDefaults(vorgabe);

        var ergebnis = Weltenliste.ausKonfiguration(
                eigene.getConfigurationSection("welten"), "marktstadt", "altfurt");

        assertEquals(java.util.List.of("marktstadt"),
                ergebnis.welten().stream().map(Weltangabe::name).toList());
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("welten.kornmark"),
                ergebnis.klagen().get(0));
        assertTrue(ergebnis.klagen().get(0).contains("von Hand"), ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Der Spielmodus steht je Welt, Vorgabe ist Ueberleben")
    void spielmodusJeWelt() {
        var ergebnis = lies("""
                welten:
                  altfurt:
                    art: gewoehnlich
                    spielmodus: abenteuer
                  kornmark:
                    art: gewoehnlich
                """);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(GameMode.ADVENTURE, ergebnis.welten().get(0).spielmodus());
        assertEquals(GameMode.SURVIVAL, ergebnis.welten().get(1).spielmodus(),
                "ohne Angabe gilt Ueberleben - so fuehrt Plan 4 alle Welten ausser Altfurt");
    }

    @Test
    @DisplayName("Ein unbekannter Spielmodus klagt und kostet nicht die Welt")
    void unbekannterSpielmodus() {
        var ergebnis = lies("""
                welten:
                  kornmark:
                    art: gewoehnlich
                    spielmodus: hartkern
                """);

        assertEquals(1, ergebnis.welten().size(), "die Welt bleibt");
        assertEquals(GameMode.SURVIVAL, ergebnis.welten().get(0).spielmodus());
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("hartkern"), ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Ein Tor steht am Eintrag der Zielwelt")
    void torAmEintragDerZielwelt() {
        var ergebnis = lies("""
                welten:
                  altfurt:
                    art: gewoehnlich
                    kante: 800
                  kornmark:
                    art: gewoehnlich
                    tor:
                      von: [74, 71, 0]
                      bis: [76, 73, 0]
                """);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertTrue(!ergebnis.welten().get(0).hatTor(), "Altfurt selbst hat keines");
        assertEquals(new Quader(74, 71, 0, 76, 73, 0), ergebnis.welten().get(1).tor());
    }

    @Test
    @DisplayName("Ein halbes Tor gibt es nicht")
    void halbesTor() {
        // Mit einer geratenen zweiten Ecke staende irgendwo in Altfurt ein Tor, das
        // niemand dort haben wollte - und es wuerde ausloesen.
        var ergebnis = lies("""
                welten:
                  altfurt:
                    art: gewoehnlich
                    kante: 800
                  kornmark:
                    art: gewoehnlich
                    tor:
                      von: [74, 71, 0]
                """);

        assertEquals(2, ergebnis.welten().size(), "die Welten bleiben");
        assertTrue(!ergebnis.welten().get(1).hatTor());
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("tor.bis fehlt"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Eine Torecke braucht drei ganze Zahlen")
    void torEckeBrauchtDreiGanzeZahlen() {
        var ergebnis = lies("""
                welten:
                  altfurt:
                    art: gewoehnlich
                    kante: 800
                  kornmark:
                    art: gewoehnlich
                    tor:
                      von: [74, 71]
                      bis: [76, 73, 0]
                  nether:
                    art: gewoehnlich
                    umgebung: nether
                    tor:
                      von: [78, 71.5, 0]
                      bis: [80, 73, 0]
                """);

        assertTrue(!ergebnis.welten().get(1).hatTor());
        assertTrue(!ergebnis.welten().get(2).hatTor());
        assertEquals(2, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("genau drei Zahlen"),
                ergebnis.klagen().get(0));
        assertTrue(ergebnis.klagen().get(1).contains("keine ganze Zahl"),
                ergebnis.klagen().get(1));
    }

    @Test
    @DisplayName("Ein Tor in der Ankunftswelt selbst ist ein Tor nach nebenan")
    void torInDerAnkunftsweltSelbst() {
        var ergebnis = lies("""
                welten:
                  altfurt:
                    art: gewoehnlich
                    kante: 800
                    tor:
                      von: [58, 71, 0]
                      bis: [60, 73, 0]
                """);

        assertEquals(1, ergebnis.welten().size());
        assertTrue(!ergebnis.welten().get(0).hatTor());
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("nach nebenan"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Ein Tor hinter der Grenze der Ankunftswelt fehlt sonst stumm")
    void torAusserhalbDerGrenze() {
        // Altfurt hat die Kante 800 um 0/0, also Bloecke -400..399. Ein Tor bei 500
        // steht dort, wo niemand hinkommt - und ohne diese Klage merkt das niemand.
        var ergebnis = lies("""
                welten:
                  altfurt:
                    art: gewoehnlich
                    kante: 800
                  kornmark:
                    art: gewoehnlich
                    tor:
                      von: [500, 71, 0]
                      bis: [502, 73, 0]
                """);

        assertTrue(!ergebnis.welten().get(1).hatTor());
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("ausserhalb der eingetragenen Grenze"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Zwei Tore mit gemeinsamen Bloecken werden beide verworfen")
    void toreDuerfenSichNichtSchneiden() {
        // Sonst entscheidet die Reihenfolge der Eintraege, wohin ein Spieler reist. Beide
        // zu verwerfen ist richtig: Welches von zwei vertippten Toren das gemeinte war,
        // weiss das Programm nicht.
        var ergebnis = lies("""
                welten:
                  altfurt:
                    art: gewoehnlich
                    kante: 800
                  kornmark:
                    art: gewoehnlich
                    tor:
                      von: [74, 71, 0]
                      bis: [76, 73, 0]
                  hochwald:
                    art: gewoehnlich
                    tor:
                      von: [76, 71, 0]
                      bis: [78, 73, 0]
                """);

        assertEquals(3, ergebnis.welten().size(), "die Welten bleiben alle");
        assertTrue(!ergebnis.welten().get(1).hatTor());
        assertTrue(!ergebnis.welten().get(2).hatTor());
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("Bloecke gemeinsam"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Tore ohne Ankunftswelt haetten keinen Ort")
    void toreOhneAnkunftswelt() {
        var ergebnis = lies("""
                welten:
                  kornmark:
                    art: gewoehnlich
                    tor:
                      von: [74, 71, 0]
                      bis: [76, 73, 0]
                """);

        assertTrue(!ergebnis.welten().get(0).hatTor());
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("steht nicht in der Liste"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Ein Eintrag ohne Unterpunkte ist kein Abschnitt")
    void eintragOhneUnterpunkte() {
        var ergebnis = lies("welten:\n  altfurt: ja\n");

        assertTrue(ergebnis.welten().isEmpty());
        assertTrue(ergebnis.klagen().get(0).contains("kein Abschnitt"));
    }
}
