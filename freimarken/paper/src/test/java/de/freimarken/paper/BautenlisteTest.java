package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Quader;
import de.freimarken.kern.Richtung;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser der Platzhalterbauten - gegen getippte Dateien.
 *
 * <p>Dieselbe Bauart wie {@code RundgangslisteTest}: Die Datei wird hier getippt, und
 * zwar so, wie ein Betreiber sie tippt - mit Tippfehlern. Was dabei herauskommen MUSS,
 * ist nicht nur ein Verzeichnis, sondern eine KLAGE, die den Schluessel nennt.
 *
 * <p>Die letzte Probe ist die wichtigste: Sie liest die AUSGELIEFERTE
 * {@code config.yml}. Die neun Quader darin sind gegen jeden belegten Ort Altfurts
 * geprueft - acht Tore, Torwand, drei Ladeneinheiten, Mittelachse - und ohne diese Probe
 * waere das eine Messung von einem Tag und kein Riegel.
 */
@DisplayName("Die Bautenliste")
class BautenlisteTest {

    /** Altfurt mit seiner wirklichen Kante. */
    private static final Map<String, Integer> KANTEN = Map.of("altfurt", 800);

    /** Das Tor nach Hafenwerder, wie es in der ausgelieferten Datei steht. */
    private static final List<Quader> TORE =
            List.of(new Quader(66, -60, 0, 68, -58, 0));

    private static YamlConfiguration datei(String... zeilen) {
        var inhalt = new StringBuilder();
        for (String zeile : zeilen) {
            inhalt.append(zeile).append('\n');
        }
        var gelesen = new YamlConfiguration();
        try {
            gelesen.loadFromString(inhalt.toString());
        } catch (org.bukkit.configuration.InvalidConfigurationException kaputt) {
            throw new IllegalStateException("Die Probe tippt kein gueltiges YAML",
                    kaputt);
        }
        return gelesen;
    }

    /** Die ausgelieferte Datei aus dem Jar - wie {@code GrenzeTest} sie holt. */
    private static String ausgelieferteDatei() {
        try (var strom = BautenlisteTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertNotNull(strom, "config.yml muss im Jar liegen");
            return new String(strom.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException nichtLesbar) {
            throw new IllegalStateException("config.yml nicht lesbar", nichtLesbar);
        }
    }

    private static Bautenliste.Ergebnis lies(String... zeilen) {
        return Bautenliste.ausKonfiguration(datei(zeilen), KANTEN, TORE);
    }

    @Test
    @DisplayName("1 - ein Bau mit drei Zeilen wird gelesen")
    void einBauWirdGelesen() {
        var ergebnis = lies(
                "bauten:",
                "  welt: altfurt",
                "  haeuser:",
                "    buergeramt:",
                "      von: [50, -61, 2]",
                "      bis: [58, -53, 10]",
                "      tuer: ost",
                "      stil: backstein",
                "      dach: ziegel");

        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertEquals("altfurt", ergebnis.welt());
        assertEquals(1, ergebnis.verzeichnis().anzahl());
        var bau = ergebnis.verzeichnis().mitNamen("buergeramt");
        assertNotNull(bau);
        assertEquals(50, bau.bereich().vonX());
        assertEquals(-61, bau.bereich().vonY());
        assertEquals(Richtung.OST, bau.tuer());
        assertEquals(1, ergebnis.bericht().size(), ergebnis.bericht().toString());
        assertTrue(ergebnis.bericht().get(0).contains("NUR BEIM START"),
                ergebnis.bericht().get(0));
    }

    @Test
    @DisplayName("2 - eine leere Liste ist KEINE Klage")
    void leereListe() {
        var ergebnis = lies("bauten:", "  welt: altfurt", "  haeuser: {}");

        assertEquals(List.of(), ergebnis.klagen());
        assertTrue(ergebnis.verzeichnis().leer());
        assertTrue(ergebnis.bericht().get(0).contains("Keine Platzhalterbauten"),
                ergebnis.bericht().get(0));
    }

    @Test
    @DisplayName("3 - eine fehlende Tuerseite wird beklagt und der Bau uebersprungen")
    void fehlendeTuerseite() {
        var ergebnis = lies(
                "bauten:", "  welt: altfurt", "  haeuser:",
                "    haus:",
                "      von: [50, -61, 2]",
                "      bis: [58, -53, 10]",
                "      stil: backstein", "      dach: ziegel");

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("bauten.haeuser.haus.tuer fehlt"),
                ergebnis.klagen().get(0));
        // Die erlaubten Worte stehen in der Klage - und zwar ALLE VIER, aus der
        // Aufzaehlung und nicht abgeschrieben.
        for (Richtung richtung : Richtung.values()) {
            assertTrue(ergebnis.klagen().get(0).contains(richtung.wort()),
                    richtung.wort() + " fehlt in: " + ergebnis.klagen().get(0));
        }
        assertTrue(ergebnis.verzeichnis().leer());
    }

    @Test
    @DisplayName("4 - eine erfundene Tuerseite wird beklagt")
    void erfundeneTuerseite() {
        var ergebnis = lies(
                "bauten:", "  welt: altfurt", "  haeuser:",
                "    haus:",
                "      von: [50, -61, 2]",
                "      bis: [58, -53, 10]",
                "      tuer: nordwest",
                "      stil: backstein", "      dach: ziegel");

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("nordwest"),
                ergebnis.klagen().get(0));
        assertTrue(ergebnis.verzeichnis().leer());
    }

    @Test
    @DisplayName("5 - Zahlen, die keine sind: fehlt, nicht drei, Nachkomma, jenseits")
    void kaputteZahlen() {
        // Die vier Maengel, die Zahlenzeile unterscheidet - je mit dem Satz, der dem
        // Betreiber sagt, was er getippt hat.
        var fehlt = lies("bauten:", "  welt: altfurt", "  haeuser:",
                "    haus:", "      bis: [58, -53, 10]", "      tuer: ost",
                "      stil: backstein", "      dach: ziegel");
        assertTrue(fehlt.klagen().get(0).contains("von fehlt"), fehlt.klagen().toString());
        assertTrue(fehlt.klagen().get(0).contains("y=-61"),
                "das Beispiel nennt die Hoehen Altfurts: " + fehlt.klagen().get(0));

        var zwei = lies("bauten:", "  welt: altfurt", "  haeuser:",
                "    haus:", "      von: [50, -61]", "      bis: [58, -53, 10]",
                "      tuer: ost",
                "      stil: backstein",
                "      dach: ziegel");
        assertTrue(zwei.klagen().get(0).contains("DREI Zahlen"), zwei.klagen().toString());

        // Ein Punkt trennt in YAML keine Tausender, sondern die Nachkommastellen.
        var komma = lies("bauten:", "  welt: altfurt", "  haeuser:",
                "    haus:", "      von: [50.5, -61, 2]", "      bis: [58, -53, 10]",
                "      tuer: ost",
                "      stil: backstein",
                "      dach: ziegel");
        assertTrue(komma.klagen().get(0).contains("keine ganze Zahl"),
                komma.klagen().toString());
        assertTrue(komma.klagen().get(0).contains("Stelle 1"), komma.klagen().get(0));

        // JENSEITS DER WELTKOORDINATEN: Paper stuerzt dort bei einem Teleport ab. Diese
        // Pruefung stand bis zum 23.11.2026 nur bei den Toren.
        var weit = lies("bauten:", "  welt: altfurt", "  haeuser:",
                "    haus:", "      von: [50, -61, 2]",
                "      bis: [99999999, -53, 10]", "      tuer: ost",
                "      stil: backstein", "      dach: ziegel");
        assertTrue(weit.klagen().get(0).contains("jenseits der Weltkoordinaten"),
                weit.klagen().toString());
    }

    @Test
    @DisplayName("6 - zwei Bauten mit gemeinsamen Bloecken werden BEIDE verworfen")
    void ueberschneidungVerwirftBeide() {
        // Der zweite risse ein Loch in die Wand des ersten, und welcher gewinnt,
        // entschiede die Reihenfolge der Datei.
        var ergebnis = lies(
                "bauten:", "  welt: altfurt", "  haeuser:",
                "    eins:",
                "      von: [50, -61, 2]", "      bis: [58, -53, 10]", "      tuer: ost",
                "      stil: backstein", "      dach: ziegel",
                "    zwei:",
                "      von: [56, -61, 8]", "      bis: [64, -53, 16]", "      tuer: ost",
                "      stil: backstein", "      dach: ziegel");

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("BEIDE"), ergebnis.klagen().get(0));
        assertTrue(ergebnis.verzeichnis().leer(), "beide, nicht einer");
    }

    @Test
    @DisplayName("7 - ein Bau auf einem TOR wird verworfen: er mauerte den Durchgang zu")
    void bauAufEinemTorWirdVerworfen() {
        // Diese Pruefung gibt es bei den Stationen nicht, und der Unterschied ist der
        // Grund: Eine Station liest nur, ein Bau SETZT.
        var ergebnis = lies(
                "bauten:", "  welt: altfurt", "  haeuser:",
                "    torhaus:",
                "      von: [64, -61, -2]", "      bis: [70, -54, 2]", "      tuer: sued",
                "      stil: backstein", "      dach: ziegel");

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("mauerte den Durchgang zu"),
                ergebnis.klagen().get(0));
        assertTrue(ergebnis.verzeichnis().leer());

        // DIE GEGENRICHTUNG: VOR der Wand ist erlaubt - genau so stehen die vier
        // Torhaeuser der ausgelieferten Datei. Ohne sie waere die Pruefung auch gruen,
        // wenn sie jeden Bau in der Naehe eines Tores verwirft.
        var davor = lies(
                "bauten:", "  welt: altfurt", "  haeuser:",
                "    torhaus:",
                "      von: [66, -61, -5]", "      bis: [68, -54, -1]",
                "      tuer: sued",
                "      stil: backstein",
                "      dach: ziegel");
        assertEquals(List.of(), davor.klagen(), davor.klagen().toString());
        assertEquals(1, davor.verzeichnis().anzahl());
    }

    @Test
    @DisplayName("8 - ein Bau hinter der Weltgrenze wird verworfen")
    void hinterDerGrenze() {
        var ergebnis = lies(
                "bauten:", "  welt: altfurt", "  haeuser:",
                "    weit:",
                "      von: [5000, -61, 5000]", "      bis: [5008, -53, 5008]",
                "      tuer: ost",
                "      stil: backstein",
                "      dach: ziegel");

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("Weltgrenze"),
                ergebnis.klagen().get(0));
        assertTrue(ergebnis.verzeichnis().leer());
    }

    @Test
    @DisplayName("9 - eine Welt, die nicht in welten: steht, laesst nichts gelten")
    void unbekannteWelt() {
        var ergebnis = lies(
                "bauten:", "  welt: nirgendwo", "  haeuser:",
                "    haus:",
                "      von: [50, -61, 2]", "      bis: [58, -53, 10]", "      tuer: ost",
                "      stil: backstein", "      dach: ziegel");

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("steht nicht in welten:"),
                ergebnis.klagen().get(0));
        assertTrue(ergebnis.verzeichnis().leer());
    }

    @Test
    @DisplayName("10 - ein zu kleiner Bau wird mit dem Satz des Kerns beklagt")
    void zuKleinerBau() {
        var ergebnis = lies(
                "bauten:", "  welt: altfurt", "  haeuser:",
                "    klotz:",
                "      von: [50, -61, 2]", "      bis: [51, -60, 3]", "      tuer: ost",
                "      stil: backstein", "      dach: ziegel");

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("kein Inneres"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("11 - die AUSGELIEFERTE Datei: sechs Bauten, keine Klage, nichts kollidiert")
    void dieAusgelieferteDatei() {
        // DIE WICHTIGSTE PROBE DIESER DATEI. Die neun Quader sind am 23.11.2026 gegen
        // jeden belegten Ort Altfurts geprueft worden - acht Tore, die Torwand, die drei
        // Ladeneinheiten, die Mittelachse des Ankunftswegs und die Ladenschutz-Luecke.
        // Ohne diese Probe waere das eine Messung von einem Tag und kein Riegel: Wer
        // morgen einen Quader verschiebt, bekaeme keine Antwort.
        var ausgeliefert = new YamlConfiguration();
        try {
            ausgeliefert.loadFromString(ausgelieferteDatei());
        } catch (org.bukkit.configuration.InvalidConfigurationException kaputt) {
            throw new IllegalStateException("Die ausgelieferte config.yml ist kein YAML",
                    kaputt);
        }

        // Die Tore und die Kanten kommen aus DERSELBEN Datei und nicht aus einer
        // Abschrift - sonst prueft diese Probe gegen Tore, die es nicht mehr gibt. Die
        // fuenf Weltnamen stehen so, wie das Plugin sie im onEnable einsetzt.
        var welten = Weltenliste.ausKonfiguration(
                ausgeliefert.getConfigurationSection("welten"), "marktstadt", "altfurt",
                "freistaedte", "hafenwerder", "wolkenwerder");
        assertEquals(List.of(), welten.klagen(),
                "die ausgelieferten Welten selbst muessen sauber sein: "
                        + welten.klagen());
        var kanten = new java.util.LinkedHashMap<String, Integer>();
        var tore = new java.util.ArrayList<Quader>();
        welten.welten().forEach(angabe -> {
            kanten.put(angabe.name(), angabe.kante());
            if (angabe.tor() != null) {
                tore.add(angabe.tor());
            }
        });
        assertEquals(8, tore.size(), "acht Tore am Bundesmarkt");

        var ergebnis = Bautenliste.ausKonfiguration(ausgeliefert, kanten, tore);

        assertEquals(List.of(), ergebnis.klagen(),
                "die ausgelieferte Datei darf keine einzige Klage erzeugen: "
                        + ergebnis.klagen());
        assertEquals("altfurt", ergebnis.welt());
        assertEquals(6, ergebnis.verzeichnis().anzahl(),
                "sechs Bauten - die vier Tortuerme sind am 23.11.2026 zu TORHAEUSERN"
                        + " in der Stadtmauer geworden und stehen nicht mehr in dieser"
                        + " Liste: " + ergebnis.verzeichnis().namen());
        assertEquals(List.of("buergeramt", "zunfthaus", "muenzhaus", "kaufmannskontor",
                        "bundesratshaus", "stadtkirche"),
                ergebnis.verzeichnis().namen(),
                "in der Reihenfolge der Datei - West und Ost, dann Rathaus und Kirche");
        // UND JEDER RAHMT DEN PLATZ: Er liegt im Kernband, also zwischen dem Rand
        // der Plaetze und der ersten Ringgasse. Ein Bau weiter innen stuende MITTEN
        // auf dem Markt, einer weiter draussen kostete ein gerechnetes Zeilenhaus.
        for (var bau : ergebnis.verzeichnis().alle()) {
            int innen = Integer.MAX_VALUE;
            int aussen = 0;
            for (int x = bau.bereich().vonX(); x <= bau.bereich().bisX(); x++) {
                for (int z = bau.bereich().vonZ(); z <= bau.bereich().bisZ(); z++) {
                    innen = Math.min(innen, de.freimarken.kern.Stadtplan.ring(x, z));
                    aussen = Math.max(aussen,
                            de.freimarken.kern.Stadtplan.ring(x, z));
                }
            }
            assertTrue(innen > de.freimarken.kern.Stadtplan.MARKT_RING,
                    bau.schluessel() + " reicht bis Ring " + innen + " und steht damit"
                            + " auf dem Platz");
            assertTrue(aussen <= de.freimarken.kern.Stadtplan.KERN_RING,
                    bau.schluessel() + " reicht bis Ring " + aussen + " und damit aus"
                            + " dem Rahmen heraus");
        }

        // UND GEGEN DIE LADENEINHEITEN: Sie stehen in einem anderen Abschnitt, also
        // prueft der Leser sie nicht - diese Probe tut es. Eine Markthalle, in die ein
        // Platzhalter hineinragt, waere ein Laden mit einer Wand mitten darin.
        var laeden = Ladenliste.ausKonfiguration(ausgeliefert, kanten);
        assertEquals(3, laeden.verzeichnis().anzahl(), "drei Ladeneinheiten");
        for (var bau : ergebnis.verzeichnis().alle()) {
            for (var einheit : laeden.verzeichnis().alle()) {
                assertFalse(bau.bereich().schneidet(einheit.bereich()),
                        bau.schluessel() + " " + bau.bereich().alsText() + " schneidet "
                                + einheit.name() + " " + einheit.bereich().alsText());
            }
        }

        // UND DIE MITTELACHSE bleibt frei: x 65..79 zwischen Torreihe und Spawn - dort
        // laeuft jeder Ankommende durch (config.yml nennt den Grund).
        var achse = new Quader(65, -61, 1, 79, -40, 22);
        // (Der Ankunftsweg liegt heute mitten auf dem Marktplatz - die Bauten
        // rahmen ihn, also bleibt er frei.)
        for (var bau : ergebnis.verzeichnis().alle()) {
            assertFalse(bau.bereich().schneidet(achse),
                    bau.schluessel() + " steht im Ankunftsweg: "
                            + bau.bereich().alsText());
        }

        // Und jeder steht auf dem BODEN der Ebene - ein Haus, das schwebt, ist keines.
        for (var bau : ergebnis.verzeichnis().alle()) {
            assertEquals(-61, bau.bereich().vonY(),
                    bau.schluessel() + " steht nicht auf y=-61");
        }
    }
}
