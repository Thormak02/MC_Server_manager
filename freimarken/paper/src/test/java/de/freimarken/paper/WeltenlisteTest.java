package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                        .getConfigurationSection("welten"), "marktstadt", "altfurt",
                "freistaedte");
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
    @DisplayName("Die vierte Art ist flach - und ihr Startwert wird nicht verglichen")
    void flacheWeltKommtAn() {
        // Fuer Welten, in denen das Gelaende STOERT: Jeder von Hand eingemessene Quader
        // braucht ebenen Boden, und gewoehnliches Gelaende gibt ihn nur mit Glueck - ein
        // gewuerfelter Startwert gab am 02.11.2026 eine rote Wueste mit 2 Bloecken
        // Spanne, ein anderer am 09.11. Bergland mit 29.
        //
        // Der Startwert wirkt bei FLACH so wenig wie beim Raster, darum darf er auch
        // nicht verglichen werden: Zwei flache Welten mit derselben Zahl sind kein
        // Fehler, sie waeren auch mit verschiedenen Zahlen gleich.
        var ergebnis = lies("""
                welten:
                  marktstadt:
                    art: raster
                  probewelt:
                    art: flach
                    startwert: 42
                  zweite:
                    art: flach
                    startwert: 42
                """);

        assertTrue(ergebnis.klagen().isEmpty(), "unerwartete Klagen: " + ergebnis.klagen());
        assertEquals(Weltangabe.Art.FLACH, ergebnis.welten().get(1).art());
        assertEquals(3, ergebnis.welten().size());
        // Der Startwert STEHT in der Zeile und WIRKT NICHT. Beides muss stimmen:
        // Dass er gelesen wird, soll keine Klage geben (er stoert ja nicht), und
        // dass er nicht wirkt, darf nicht verschwiegen werden - sonst setzt
        // Weltenbau ihn doch, oder die Abweichungsklage kommt fuer einen
        // Unterschied, der Absicht ist. Genau das ist am 09.11.2026 im Startlauf
        // passiert, als die Ausnahme nur an EINER der zwei Stellen stand.
        assertEquals(42L, ergebnis.welten().get(1).startwert(),
                "gelesen wird er trotzdem");
        assertFalse(ergebnis.welten().get(1).startwertWirkt(),
                "eine Ebene ist eine Ebene, gleich welche Zahl darueber steht");
        assertFalse(ergebnis.welten().get(1).wiederholbar(),
                "und darum ist sie auch nicht \"wiederholbar\" im Sinne des"
                        + " Startwerts - Weltenbau setzt ihn dann nicht und die"
                        + " Abweichungsklage schweigt");
    }

    @Test
    @DisplayName("Eine unbekannte Art nennt in der Klage alle vier")
    void unbekannteArtNenntAlleVier() {
        // Die Klage ist das einzige, was der Betreiber von der Liste zu sehen bekommt -
        // sie muss darum vollstaendig sein. Eine vergessene Art in dieser Zeile ist eine,
        // die niemand findet.
        var ergebnis = lies("""
                welten:
                  marktstadt:
                    art: raster
                  sumpf:
                    art: huegelig
                """);

        assertTrue(ergebnis.klagen().stream()
                        .anyMatch(k -> k.contains("raster") && k.contains("gewoehnlich")
                                && k.contains("leer") && k.contains("flach")),
                "die Klage muss alle vier Arten nennen: " + ergebnis.klagen());
    }

    // --- Der Startwert [09.11.2026] -------------------------------------------

    @Test
    @DisplayName("Ein Startwert kommt an - auch 0 und auch negativ")
    void startwertKommtAn() {
        // NULL IST EIN GUELTIGER STARTWERT, und darum ist das Feld ein Long: Bei der
        // Kante heisst 0 "keine Grenze", hier waere dasselbe ein Gelaende, das niemand
        // mehr einstellen kann. Negativ geht auch - Minecraft nimmt den ganzen
        // Zahlenbereich.
        var ergebnis = lies("""
                welten:
                  marktstadt:
                    art: raster
                  altfurt:
                    art: gewoehnlich
                    startwert: 110926001
                  hochwald:
                    art: gewoehnlich
                    startwert: 0
                  kornmark:
                    art: gewoehnlich
                    startwert: -7
                """);

        assertTrue(ergebnis.klagen().isEmpty(), "unerwartete Klagen: " + ergebnis.klagen());
        assertEquals(110926001L, ergebnis.welten().get(1).startwert());
        assertEquals(0L, ergebnis.welten().get(2).startwert(),
                "0 ist ein Startwert wie jeder andere und nicht \"keiner\"");
        assertEquals(-7L, ergebnis.welten().get(3).startwert());
        assertTrue(ergebnis.welten().get(2).wiederholbar(),
                "auch mit 0 ist das Gelaende wiederholbar");
    }

    @Test
    @DisplayName("Ohne Startwert ist das Gelaende NICHT wiederholbar")
    void ohneStartwertNichtWiederholbar() {
        // Die Lage, in der jeder von Hand eingemessene Quader an genau einer zufaellig
        // erzeugten Welt haengt: Wird sie geloescht, sind die Zahlen wertlos. Am
        // 09.11.2026 war das der Fall - darum gibt es diese Zeile ueberhaupt.
        var ergebnis = lies("""
                welten:
                  marktstadt:
                    art: raster
                  altfurt:
                    art: gewoehnlich
                """);

        assertNull(ergebnis.welten().get(1).startwert());
        assertFalse(ergebnis.welten().get(1).wiederholbar());
    }

    @Test
    @DisplayName("Ein Startwert als WORT wird beklagt und nicht umgerechnet")
    void startwertAlsWortWirdBeklagt() {
        // Minecraft nimmt im Spielmenue auch Woerter und rechnet sie selbst in eine Zahl
        // um. Das hier nachzubauen waere eine Abschrift des Spiels - weicht sie eines
        // Tages ab, entsteht aus demselben Wort ein anderes Gelaende, und JEDER
        // eingemessene Quader liegt daneben. Darum: Klage, keine Umrechnung.
        var ergebnis = lies("""
                welten:
                  marktstadt:
                    art: raster
                  altfurt:
                    art: gewoehnlich
                    startwert: freimarken
                """);

        assertNull(ergebnis.welten().get(1).startwert(),
                "ein Wort darf nicht stillschweigend zu einer Zahl werden");
        assertTrue(ergebnis.klagen().stream()
                        .anyMatch(k -> k.contains("welten.altfurt.startwert")
                                && k.contains("keine Zahl")),
                "die Klage muss den Pfad nennen: " + ergebnis.klagen());
    }

    @Test
    @DisplayName("Ein Punkt im Startwert wird beklagt - YAML liest 1.000 als 1.0")
    void startwertMitPunktWirdBeklagt() {
        // Dieselbe Falle wie bei der Kante, und sie ist hier schlimmer: Eine falsche
        // Kante ist eine fehlende Grenze, ein falscher Startwert ist ein ANDERES
        // Gelaende - und das merkt niemand, denn eine Welt sieht immer nach etwas aus.
        var ergebnis = lies("""
                welten:
                  marktstadt:
                    art: raster
                  altfurt:
                    art: gewoehnlich
                    startwert: 1.000
                """);

        assertNull(ergebnis.welten().get(1).startwert());
        assertTrue(ergebnis.klagen().stream()
                        .anyMatch(k -> k.contains("keine ganze Zahl")),
                "die Klage muss den Punkt erklaeren: " + ergebnis.klagen());
    }

    @Test
    @DisplayName("Zwei Welten mit demselben Startwert und derselben Umgebung werden"
            + " beklagt")
    void gleicherStartwertWirdBeklagt() {
        // Gleicher Startwert UND gleiche Umgebung heisst in Minecraft dasselbe Gelaende,
        // Block fuer Block. Hochwald und Kornmark waeren zwei Namen fuer eine
        // Landschaft - ein Spieler, der von einer in die andere reist, stuende in
        // derselben Gegend, und ohne diese Klage sagte keine Zeile warum.
        var ergebnis = lies("""
                welten:
                  marktstadt:
                    art: raster
                  hochwald:
                    art: gewoehnlich
                    startwert: 42
                  kornmark:
                    art: gewoehnlich
                    startwert: 42
                """);

        assertTrue(ergebnis.klagen().stream()
                        .anyMatch(k -> k.contains("hochwald") && k.contains("kornmark")
                                && k.contains("DASSELBE Gelaende")),
                "die Klage muss BEIDE Welten nennen: " + ergebnis.klagen());
        // Nur eine Klage, kein Verwerfen: Zweimal dasselbe Gelaende macht nichts kaputt,
        // es ist nur mit Sicherheit nicht gewollt.
        assertEquals(42L, ergebnis.welten().get(2).startwert(),
                "die Welt entsteht trotzdem, mit ihrem Startwert");
    }

    @Test
    @DisplayName("Derselbe Startwert in einer ANDEREN Umgebung ist keine Klage")
    void gleicherStartwertAndereUmgebung() {
        // Oberwelt und Nether aus derselben Zahl sind zwei verschiedene Gelaende - in
        // Vanilla ist das sogar der Normalfall. Wer hier pauschal auf den Startwert
        // pruefte, klagte ueber den haeufigsten richtigen Fall.
        var ergebnis = lies("""
                welten:
                  marktstadt:
                    art: raster
                  altfurt:
                    art: gewoehnlich
                    startwert: 42
                  nether:
                    art: gewoehnlich
                    umgebung: nether
                    startwert: 42
                """);

        assertTrue(ergebnis.klagen().isEmpty(), "unerwartete Klagen: " + ergebnis.klagen());
    }

    @Test
    @DisplayName("Die Rasterwelt wird vom Startwertvergleich ausgenommen")
    void rasterweltOhneStartwertvergleich() {
        // Ihr Gelaende kommt aus dem MarktstadtGenerator und nicht aus einem Startwert.
        // Eine Klage ueber "dasselbe Gelaende" waere dort falsch - das Raster ist
        // dasselbe, ganz gleich welche Zahl darueber steht.
        var ergebnis = lies("""
                welten:
                  marktstadt:
                    art: raster
                    startwert: 42
                  altfurt:
                    art: gewoehnlich
                    startwert: 42
                """);

        assertTrue(ergebnis.klagen().isEmpty(), "unerwartete Klagen: " + ergebnis.klagen());
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
                    datei.getConfigurationSection("welten"), "marktstadt", "altfurt",
                "freistaedte");

            assertTrue(ergebnis.klagen().isEmpty(),
                    "die ausgelieferte Datei klagt: " + ergebnis.klagen());
            // JEDE gewoehnliche Welt hat einen Startwert, und zwar einen EIGENEN.
            // Ohne diese Zeile faellt ein versehentlich kopierter Wert nicht auf:
            // Die Liste klagt darueber, aber die Klage traegt nur, wenn jemand sie
            // liest - und ein Startbericht mit sieben Welten ist lang.
            // GEFRAGT WIRD, OB DER STARTWERT WIRKT - und nicht, ob die Art zufaellig
            // RASTER heisst [geaendert 11.11.2026]: Mit den Stadtfeldern sind es vier
            // Arten, deren Gelaende aus einer Rechnung kommt und nicht aus einer Zahl.
            // Eine handgeschriebene Ausnahmeliste bliebe beim naechsten Zuwachs stehen,
            // und dann verlangte diese Probe einen Startwert fuer eine Welt, die keinen
            // haben kann.
            var gewoehnliche = ergebnis.welten().stream()
                    .filter(Weltangabe::startwertWirkt)
                    .toList();
            assertTrue(gewoehnliche.stream().allMatch(Weltangabe::wiederholbar),
                    "ohne Startwert haengt jeder eingemessene Quader an einer"
                            + " Welt, die niemand wiederherstellen kann: "
                            + gewoehnliche.stream().filter(w -> !w.wiederholbar())
                                    .map(Weltangabe::name).toList());
            assertEquals(gewoehnliche.size(),
                    gewoehnliche.stream().map(Weltangabe::startwert).distinct()
                            .count(),
                    "je Welt ein ANDERER Startwert - gleicher Wert und gleiche"
                            + " Umgebung heisst dasselbe Gelaende, Block fuer"
                            + " Block: " + gewoehnliche.stream()
                                    .map(w -> w.name() + "=" + w.startwert())
                                    .toList());
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
    @DisplayName("Eine zweite Stadtfelderwelt gibt es nicht")
    void nurEineStadtfelderwelt() {
        // Sie bekaeme Stadtfelder und eine Weltgrenze vom selben Generator, aber KEIN
        // Freistadtbuch - Felder, die niemand gruenden und niemand schuetzen kann, und
        // ein /warp freistadt, der sie nie findet. Der offene Punkt aus Teil A.
        var ergebnis = lies("""
                welten:
                  freistaedte:
                    art: stadtfelder
                    kante: 6000
                  freistaedte2:
                    art: stadtfelder
                    kante: 6000
                """);

        assertEquals(java.util.List.of("freistaedte"),
                ergebnis.welten().stream().map(Weltangabe::name).toList());
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("allein welten.freistaedte"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Die Freistaedtewelt MUSS stadtfelder tragen - die andere Richtung")
    void freistadtweltMussStadtfelderSein() {
        // Die Bindung gilt in BEIDE Richtungen. Ohne diese Haelfte entstuende die Welt,
        // auf die freistaedte.welt zeigt, als gewoehnliche Welt mit Huegeln und Baeumen
        // - und das Gruendungsbuch vergaebe darin Felder, die es gar nicht gibt.
        var ergebnis = lies("""
                welten:
                  freistaedte:
                    art: gewoehnlich
                    kante: 6000
                """);

        assertTrue(ergebnis.welten().isEmpty(), ergebnis.welten().toString());
        assertEquals(1, ergebnis.klagen().size());
        assertTrue(ergebnis.klagen().get(0).contains("muss stadtfelder sein"),
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
        // Eine Welt, die NICHT freistaedte heisst: Jener Name ist seit dem 11.11.2026
        // an art: stadtfelder gebunden, und die Bindung klagt vor dem Tausenderpunkt.
        // Diese Probe fragt nach dem Punkt und nicht nach der Bindung.
        var ergebnis = lies("""
                welten:
                  hochwald:
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
                eigene.getConfigurationSection("welten"), "marktstadt", "altfurt",
                "freistaedte");

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
