package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Quader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Das Torbuch - lesen, pruefen, schreiben.
 *
 * <p><b>Die wichtigste Probe dieser Datei ist 1</b>: Der Text, den das Plugin SCHREIBT,
 * muss zeichengleich der Datei sein, die es AUSLIEFERT. Ohne diese Zusage verliert das
 * erste {@code /portal setzen} den Kopf der Datei - und mit ihm die Anleitung, nach der
 * ein Betreiber ein Tor von Hand eintraegt. Genau dieser Verlust ist der Grund, aus dem
 * das Plugin die {@code config.yml} nicht selbst schreibt.
 */
class TorbuchTest {

    private static String ausgelieferte(String name) {
        try (var strom = TorbuchTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(strom, name + " muss im Jar liegen");
            return new String(strom.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException nichtLesbar) {
            throw new IllegalStateException(name + " nicht lesbar", nichtLesbar);
        }
    }

    /** Legt die ausgelieferte Datei in einen Ordner und liest sie von dort. */
    private static Torbuch ausDerRessource(Path ordner) throws IOException {
        File ort = ordner.resolve(Torbuch.DATEI).toFile();
        Files.writeString(ort.toPath(), ausgelieferte(Torbuch.DATEI),
                StandardCharsets.UTF_8);
        Torbuch buch = new Torbuch(ort);
        assertEquals(List.of(), buch.lade(), "die ausgelieferte Datei hat keine Klage");
        return buch;
    }

    @Test
    @DisplayName("1 - was das Plugin schreibt, ist zeichengleich das, was es ausliefert")
    void schriftIstDieAusgelieferteDatei(@TempDir Path ordner) throws IOException {
        Torbuch buch = ausDerRessource(ordner);

        // Lesen, schreiben, vergleichen - und zwar den GANZEN Text samt Kopf. Eine
        // Probe auf "enthaelt die acht Welten" wuerde gruen bleiben, waehrend der Kopf
        // verschwindet, und der Kopf ist die Anleitung.
        assertEquals(ausgelieferte(Torbuch.DATEI), Torbuch.schrift(buch.alle()));
    }

    @Test
    @DisplayName("1b - und das gilt auch nach einem Umweg ueber die Platte")
    void schreibenUndWiederLesen(@TempDir Path ordner) throws IOException {
        Torbuch buch = ausDerRessource(ordner);
        buch.schreibe();

        // Der zweite Zeuge: Nicht nur der TEXT ist gleich, auch der Leser findet
        // danach dasselbe. Ohne diese Probe koennte schrift() einen Text bauen, den
        // lade() anders versteht - etwa bei einem Namen mit Sonderzeichen.
        Torbuch wieder = new Torbuch(ordner.resolve(Torbuch.DATEI).toFile());
        assertEquals(List.of(), wieder.lade());
        assertEquals(buch.alle(), wieder.alle());
        assertEquals(ausgelieferte(Torbuch.DATEI),
                Files.readString(ordner.resolve(Torbuch.DATEI), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("2 - die acht ausgelieferten Tore stehen in Dateireihenfolge")
    void achtToreInReihenfolge(@TempDir Path ordner) throws IOException {
        Torbuch buch = ausDerRessource(ordner);

        assertEquals(List.of("marktstadt", "freistaedte", "hafenwerder", "wolkenwerder",
                        "hochwald", "kornmark", "nether", "end"), buch.namen(),
                "acht Tore in vier Torhaeusern, je zwei Nischen - und in der"
                        + " Reihenfolge, in der sie in der Datei stehen");
        assertEquals(new Quader(106, -60, -4, 108, -58, -4), buch.tor("marktstadt"));
        assertEquals(new Quader(-108, -60, 4, -106, -58, 4), buch.tor("end"));
        assertTrue(buch.alle().get("marktstadt").wo().contains("Osttorhaus"),
                "die Notiz zur Nische wandert mit: "
                        + buch.alle().get("marktstadt").wo());
    }

    @Test
    @DisplayName("3 - eine fehlende Datei ist kein Fehler")
    void fehlendeDateiOhneKlage(@TempDir Path ordner) {
        Torbuch buch = new Torbuch(ordner.resolve("gibtesnicht.yml").toFile());

        assertEquals(List.of(), buch.lade(), "dann gibt es keine Tore, und das ist alles");
        assertTrue(!buch.hatTore());
    }

    // -------------------------------------------------- Die vier Pruefungen

    private static Map<String, Torbuch.Tor> karte(Object... paare) {
        Map<String, Torbuch.Tor> tore = new LinkedHashMap<>();
        for (int i = 0; i < paare.length; i += 2) {
            tore.put((String) paare[i], new Torbuch.Tor("", (Quader) paare[i + 1]));
        }
        return tore;
    }

    private static final Set<String> WELTEN =
            Set.of("altfurt", "kornmark", "hochwald", "nether");

    /**
     * Alle Ziele der ausgelieferten Datei plus die Ankunftswelt und vogtei.
     *
     * <p>Vollstaendig und nicht nur die zwei, um die es gerade geht: Die vierte
     * Pruefung klagt ueber JEDES Tor in eine unbekannte Welt. Mit einer kurzen
     * Liste zaehlte eine Probe sieben Klagen, die nichts mit ihr zu tun haben -
     * und die eine, die sie meint, ginge darin unter.
     */
    private static final Set<String> ALLE_ZIELE = Set.of("altfurt",
            "marktstadt", "freistaedte", "hafenwerder", "wolkenwerder",
            "hochwald", "kornmark", "nether", "end", "vogtei");

    @Test
    @DisplayName("4 - ein Tor in eine Welt, die es nicht gibt, wird verworfen")
    void torInEineUnbekannteWelt() {
        var pruefung = Torbuch.pruefe(karte("vogtei", new Quader(74, 71, 0, 76, 73, 0)),
                "altfurt", WELTEN, 800);

        assertEquals(Set.of("vogtei"), pruefung.verworfen());
        assertTrue(pruefung.klagen().get(0).contains("eine Welt, die es nicht gibt"),
                pruefung.klagen().get(0));
    }

    @Test
    @DisplayName("5 - ein Tor in die Welt, in der es steht, wird verworfen")
    void torNachNebenan() {
        var pruefung = Torbuch.pruefe(karte("altfurt", new Quader(74, 71, 0, 76, 73, 0)),
                "altfurt", WELTEN, 800);

        assertEquals(Set.of("altfurt"), pruefung.verworfen());
        assertTrue(pruefung.klagen().get(0).contains("Tor nach nebenan"),
                pruefung.klagen().get(0));
    }

    @Test
    @DisplayName("6 - ein Tor ausserhalb der Grenze der Ankunftswelt wird verworfen")
    void torAusserhalbDerGrenze() {
        var pruefung = Torbuch.pruefe(
                karte("kornmark", new Quader(9000, 71, 0, 9002, 73, 0)),
                "altfurt", WELTEN, 800);

        assertEquals(Set.of("kornmark"), pruefung.verworfen());
        assertTrue(pruefung.klagen().get(0).contains("ausserhalb der eingetragenen Grenze"),
                pruefung.klagen().get(0));
    }

    @Test
    @DisplayName("6b - ohne eingetragene Kante gibt es keine Grenzpruefung")
    void ohneKanteKeineGrenze() {
        // Das Gegenbeispiel zu 6: Ohne Kante weiss niemand, wo die Grenze liegt - und
        // eine geratene Grenze wuerde jedes Tor verwerfen.
        var pruefung = Torbuch.pruefe(
                karte("kornmark", new Quader(9000, 71, 0, 9002, 73, 0)),
                "altfurt", WELTEN, null);

        assertEquals(Set.of(), pruefung.verworfen());
        assertTrue(pruefung.sauber());
    }

    @Test
    @DisplayName("7 - zwei Tore mit gemeinsamen Bloecken werden BEIDE verworfen")
    void zweiToreMitGemeinsamenBloecken() {
        // Beide und nicht eines: Welches von zwei vertippten Toren das gemeinte war,
        // weiss das Programm nicht.
        var pruefung = Torbuch.pruefe(karte(
                        "kornmark", new Quader(74, 71, 0, 76, 73, 0),
                        "hochwald", new Quader(76, 71, 0, 78, 73, 0)),
                "altfurt", WELTEN, 800);

        assertEquals(Set.of("kornmark", "hochwald"), pruefung.verworfen());
        assertEquals(1, pruefung.klagen().size(), pruefung.klagen().toString());
        assertTrue(pruefung.klagen().get(0).contains("Bloecke gemeinsam"),
                pruefung.klagen().get(0));
    }

    @Test
    @DisplayName("8 - ohne Ankunftswelt hat KEIN Tor einen Ort")
    void ohneAnkunftsweltKeinTor() {
        var pruefung = Torbuch.pruefe(karte(
                        "kornmark", new Quader(74, 71, 0, 76, 73, 0),
                        "hochwald", new Quader(90, 71, 0, 92, 73, 0)),
                "", WELTEN, 800);

        assertEquals(Set.of("kornmark", "hochwald"), pruefung.verworfen(),
                "der EINZIGE Fall, der alle Tore trifft");
        assertTrue(pruefung.klagen().get(0).contains("reisen.ankunftswelt ist leer"),
                pruefung.klagen().get(0));
    }

    @Test
    @DisplayName("8b - eine Ankunftswelt, die es nicht gibt, ebenso")
    void unbekannteAnkunftswelt() {
        var pruefung = Torbuch.pruefe(karte("kornmark", new Quader(74, 71, 0, 76, 73, 0)),
                "gibtesnicht", WELTEN, 800);

        assertEquals(Set.of("kornmark"), pruefung.verworfen());
        assertTrue(pruefung.klagen().get(0).contains("steht nicht in welten:"),
                pruefung.klagen().get(0));
    }

    @Test
    @DisplayName("9 - pruefeUndVerwirf nimmt das verworfene Tor WIRKLICH heraus")
    void verworfenesTorIstWegUndNichtNurBeklagt(@TempDir Path ordner)
            throws IOException {
        File ort = ordner.resolve(Torbuch.DATEI).toFile();
        Files.writeString(ort.toPath(), Torbuch.schrift(karte(
                "kornmark", new Quader(74, 71, 0, 76, 73, 0),
                "gibtesnicht", new Quader(90, 71, 0, 92, 73, 0))),
                StandardCharsets.UTF_8);
        Torbuch buch = new Torbuch(ort);
        assertEquals(List.of(), buch.lade());
        assertEquals(2, buch.anzahl());

        List<String> klagen = buch.pruefeUndVerwirf("altfurt", WELTEN, 800);

        // Beklagt UND heraus: Bliebe es nur beklagt, klagte das Protokoll und der
        // Durchgang wirkte trotzdem - das ist schlimmer als beides nicht zu tun.
        assertEquals(1, klagen.size(), klagen.toString());
        assertEquals(List.of("kornmark"), buch.namen());
        assertEquals(null, buch.tor("gibtesnicht"));
    }

    // -------------------------------------------------- Setzen und Entfernen

    @Test
    @DisplayName("10 - setzen schreibt die Datei, und der Kopf bleibt")
    void setzenSchreibtUndBehaeltDenKopf(@TempDir Path ordner) throws IOException {
        Torbuch buch = ausDerRessource(ordner);

        List<String> klagen = buch.setze("vogtei",
                new Torbuch.Tor("von Hand gesetzt", new Quader(0, -60, 20, 2, -58, 20)),
                "altfurt", ALLE_ZIELE, 2000);

        assertEquals(List.of(), klagen, klagen.toString());
        String geschrieben = Files.readString(ordner.resolve(Torbuch.DATEI),
                StandardCharsets.UTF_8);
        assertTrue(geschrieben.startsWith("# ====="), "der Kopf steht wieder oben");
        assertTrue(geschrieben.contains("DIE TORE AM BUNDESMARKT"), geschrieben);
        assertTrue(geschrieben.contains("    wo: \"von Hand gesetzt\""), geschrieben);
        assertEquals(9, buch.anzahl());
        assertEquals("vogtei", buch.namen().get(8), "das neue Tor kommt HINTEN dazu");
    }

    @Test
    @DisplayName("10b - setzen prueft VORHER und aendert bei einem Fund nichts")
    void setzenPrueftVorher(@TempDir Path ordner) throws IOException {
        Torbuch buch = ausDerRessource(ordner);
        String vorher = Files.readString(ordner.resolve(Torbuch.DATEI),
                StandardCharsets.UTF_8);

        // Derselbe Quader wie das Tor nach marktstadt - sie teilen sich jeden Block.
        List<String> klagen = buch.setze("vogtei",
                new Torbuch.Tor("", new Quader(106, -60, -4, 108, -58, -4)),
                "altfurt", ALLE_ZIELE, 2000);

        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("Bloecke gemeinsam"), klagen.get(0));
        assertEquals(8, buch.anzahl(), "im Speicher hat sich nichts geaendert");
        assertEquals(vorher, Files.readString(ordner.resolve(Torbuch.DATEI),
                StandardCharsets.UTF_8), "und auf der Platte auch nicht");
    }

    @Test
    @DisplayName("11 - entfernen nimmt genau eines heraus und schreibt")
    void entfernenSchreibt(@TempDir Path ordner) throws IOException {
        Torbuch buch = ausDerRessource(ordner);

        assertTrue(buch.entferne("kornmark"));
        assertEquals(7, buch.anzahl());
        assertEquals(null, buch.tor("kornmark"));
        assertTrue(!buch.entferne("kornmark"), "zweimal entfernen findet nichts mehr");

        Torbuch wieder = new Torbuch(ordner.resolve(Torbuch.DATEI).toFile());
        wieder.lade();
        assertEquals(7, wieder.anzahl(), "und die Platte weiss es auch");
    }

    // -------------------------------------------------- Die Form der Datei

    @Test
    @DisplayName("12 - ein halbes Tor gibt es nicht")
    void halbesTorWirdBeklagt(@TempDir Path ordner) throws IOException {
        File ort = ordner.resolve(Torbuch.DATEI).toFile();
        Files.writeString(ort.toPath(), "tore:\n  kornmark:\n    von: [1, 2, 3]\n",
                StandardCharsets.UTF_8);
        Torbuch buch = new Torbuch(ort);

        List<String> klagen = buch.lade();

        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("tore.kornmark.bis fehlt"), klagen.get(0));
        assertTrue(!buch.hatTore(), "und ein halbes Tor steht nicht in der Liste");
    }

    @Test
    @DisplayName("12b - eine Ecke mit Komma ist keine Ecke")
    void kommazahlIstKeineEcke(@TempDir Path ordner) throws IOException {
        File ort = ordner.resolve(Torbuch.DATEI).toFile();
        Files.writeString(ort.toPath(),
                "tore:\n  kornmark:\n    von: [1.5, 2, 3]\n    bis: [1, 2, 3]\n",
                StandardCharsets.UTF_8);

        List<String> klagen = new Torbuch(ort).lade();

        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("keine ganze Zahl"), klagen.get(0));
    }

    @Test
    @DisplayName("12c - jenseits der Weltkoordinaten wird beklagt, nicht gebaut")
    void jenseitsDerWeltkoordinaten(@TempDir Path ordner) throws IOException {
        // Paper stuerzt bei einem Teleport dorthin ab (PaperMC/Paper#13186), und ein
        // vertippter Quader reicht dafuer.
        File ort = ordner.resolve(Torbuch.DATEI).toFile();
        Files.writeString(ort.toPath(),
                "tore:\n  kornmark:\n    von: [40000000, 2, 3]\n    bis: [1, 2, 3]\n",
                StandardCharsets.UTF_8);

        List<String> klagen = new Torbuch(ort).lade();

        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("jenseits der Weltkoordinaten"),
                klagen.get(0));
    }

    @Test
    @DisplayName("12d - eine Datei ohne Abschnitt tore: wird beklagt")
    void dateiOhneAbschnitt(@TempDir Path ordner) throws IOException {
        File ort = ordner.resolve(Torbuch.DATEI).toFile();
        Files.writeString(ort.toPath(), "portale:\n  kornmark: ja\n",
                StandardCharsets.UTF_8);

        List<String> klagen = new Torbuch(ort).lade();

        assertEquals(1, klagen.size(), klagen.toString());
        assertTrue(klagen.get(0).contains("keinen Abschnitt tore:"), klagen.get(0));
    }
    // -------------------------------------------------- Gegen die Torhaeuser

    /** Die ausgelieferte config.yml - fuer die Torhaeuser und die Stadt. */
    private static org.bukkit.configuration.file.YamlConfiguration ausgelieferteKonfiguration()
            throws IOException {
        try (var strom = TorbuchTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(strom, "config.yml liegt nicht im Klassenpfad");
            return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(strom, StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("13 - jedes Tor der ausgelieferten Datei liegt in der Nische seines Torhauses")
    void dieToreLiegenInDenNischen(@TempDir Path ordner) throws IOException {
        // WARUM ES DIESE TAFEL GIBT [18.11.2026, neu gefasst 23.11. und 25.11.2026].
        // Mit Wolkenwerder kam ein achtes Tor in die ausgelieferte Datei, und es fiel
        // aus der Reihe: Die Wand am Bundesmarkt war 32 Bloecke breit, acht
        // Durchgaenge brauchen aber 33. Keine Probe hat das gesehen - die Proben
        // darueber pruefen, dass die Datei klaglos liest und acht Tore traegt. Das ist
        // alles wahr und sagt ueber den ORT der Tore nichts.
        //
        // JEDES TOR MUSS IN DER NISCHE LIEGEN, DIE SEIN TORHAUS BAUT. Der Quader steht
        // in tore.yml, die Nische rechnet Torhausplan aus bauten.torhaeuser. Weichen
        // sie ab, steht das Portal in einer Wand - ein Torhaus mit einer Nische, in
        // der nichts passiert. Das faellt im Spiel erst auf, wenn jemand hineinlaeuft
        // und nicht reist; hier faellt es beim Bauen auf.
        //
        // Diese Tafel stand bis zum 25.11.2026 in WeltenlisteTest. Sie ist mit den
        // Quadern hierher gezogen, nicht weggefallen.
        Torbuch buch = ausDerRessource(ordner);
        var datei = ausgelieferteKonfiguration();
        var klagen = new java.util.ArrayList<String>();
        var torhaeuser = Torhausliste.ausKonfiguration(datei, klagen);
        assertEquals(List.of(), klagen,
                "die Torhaeuser der ausgelieferten Datei muessen sauber lesen: " + klagen);
        var stadt = Stadtliste.ausKonfiguration(datei, 800, klagen).plan();
        assertNotNull(stadt, "die ausgelieferte Datei baut eine Stadt");
        assertEquals(List.of(), klagen, "und auch sie liest sauber: " + klagen);

        int geprueft = 0;
        for (var seite : de.freimarken.kern.Richtung.values()) {
            var haus = torhaeuser.tor(seite);
            assertNotNull(haus, "die ausgelieferte Datei hat je Seite ein Torhaus - "
                    + seite.wort() + " fehlt");
            var plan = de.freimarken.kern.Torhausplan.inDerMauer(stadt, seite);
            for (boolean links : new boolean[] {true, false}) {
                String name = links ? haus.links() : haus.rechts();
                assertNotNull(name, seite.wort() + ": die Nische "
                        + (links ? "links" : "rechts") + " hat keine Welt");
                assertEquals(plan.nische(links), buch.tor(name),
                        "das Tor nach " + name + " muss in der Nische des "
                                + seite.wort() + "-Torhauses liegen");
                geprueft++;
            }
        }
        // GEGEN DIE ZAHL UND NICHT GEGEN "NICHT LEER": Vier Torhaeuser mit je zwei
        // Nischen sind acht Tore, und acht traegt die Datei. Ein Filter, der aus
        // Versehen alles wegwirft, liesse die Schleife ins Leere laufen.
        assertEquals(8, geprueft, "acht Tore in vier Torhaeusern");
        assertEquals(geprueft, buch.anzahl(),
                "und kein Tor steht ausserhalb eines Torhauses");
    }

    @Test
    @DisplayName("13b - jedes Tor ist eine TUER: drei hoch, drei breit, einen Block dick")
    void jedesTorIstEineTuer(@TempDir Path ordner) throws IOException {
        // Welche Achse die dicke ist, haengt an der Seite: An Nord und Sued steht die
        // Nische in einer Wand, die laengs x laeuft, an Ost und West quer dazu.
        Torbuch buch = ausDerRessource(ordner);

        int gemessen = 0;
        for (var eintrag : buch.alle().entrySet()) {
            var q = eintrag.getValue().quader();
            int kx = q.bisX() - q.vonX() + 1;
            int ky = q.bisY() - q.vonY() + 1;
            int kz = q.bisZ() - q.vonZ() + 1;
            assertEquals(3, ky, "Tuerhoehe 3 - das Tor nach " + eintrag.getKey());
            assertTrue((kx == 1 && kz == 3) || (kx == 3 && kz == 1),
                    "ein Tor ist eine Tuer und kein Gang: das Tor nach "
                            + eintrag.getKey() + " ist " + kx + " x " + kz
                            + " in der Ebene");
            assertEquals(de.freimarken.kern.Stadtplan.BODEN_Y + 1, q.vonY(),
                    "es beginnt auf der Lauf-Ebene - wer hineingeht, springt nicht: "
                            + eintrag.getKey());
            gemessen++;
        }
        assertEquals(8, gemessen, "jeder Quader ist angesehen");
    }

    @Test
    @DisplayName("13c - und kein Tor teilt einen Block mit einem anderen")
    void keinTorTeiltEinenBlock(@TempDir Path ordner) throws IOException {
        // Hier wird nichts geglaubt: Jedes Paar wird gegeneinander gehalten, und die
        // Zahl der Paare ist nachgerechnet - sonst liesse ein Filter, der alles
        // wegwirft, die Schleife ins Leere laufen.
        Torbuch buch = ausDerRessource(ordner);
        var namen = buch.namen();

        int paare = 0;
        for (int i = 0; i < namen.size(); i++) {
            for (int j = i + 1; j < namen.size(); j++) {
                paare++;
                assertTrue(!buch.tor(namen.get(i)).schneidet(buch.tor(namen.get(j))),
                        "die Tore nach " + namen.get(i) + " ("
                                + buch.tor(namen.get(i)).alsText() + ") und "
                                + namen.get(j) + " ("
                                + buch.tor(namen.get(j)).alsText()
                                + ") haben Bloecke gemeinsam");
            }
        }
        assertEquals(namen.size() * (namen.size() - 1) / 2, paare,
                "jedes Paar ist gegeneinander gehalten");
    }

    @Test
    @DisplayName("13d - welche Welt ein Tor hat und welche nicht")
    void welcheWeltEinTorHat(@TempDir Path ordner) throws IOException {
        // DREI Welten ohne Tor, ausdruecklich und nicht gerechnet: In Altfurt STEHEN
        // die Tore. Vogtei und Stollenberg haben keines, weil alle acht Nischen der
        // vier Torhaeuser belegt sind (config.yml bauten.torhaeuser, vier Seiten mal
        // links/rechts). Bis ein fuenftes Torhaus steht, fuehrt /warp hin.
        //
        // Kommt eine Nische dazu, wird DIESE Zeile rot, und das ist der Zweck: Eine
        // gerechnete Ausnahme waere fuer jede weitere Welt still.
        Torbuch buch = ausDerRessource(ordner);
        var welten = Weltenliste.ausKonfiguration(
                ausgelieferteKonfiguration().getConfigurationSection("welten"),
                "marktstadt", "freistaedte", "hafenwerder", "wolkenwerder");
        assertEquals(List.of(), welten.klagen(),
                "die ausgelieferten Welten muessen sauber lesen: " + welten.klagen());

        var ohneTor = List.of("altfurt", "vogtei", "stollenberg");
        for (var welt : welten.welten()) {
            if (ohneTor.contains(welt.name())) {
                assertEquals(null, buch.tor(welt.name()),
                        "diese Welt hat ein Tor bekommen, ohne dass die Tafel es"
                                + " weiss: " + welt.name());
            } else {
                assertNotNull(buch.tor(welt.name()),
                        "ohne Tor kaeme man nur mit /warp hin: " + welt.name());
            }
        }
        assertEquals(welten.welten().size() - ohneTor.size(), buch.anzahl(),
                "und es gibt kein Tor in eine Welt, die es nicht gibt");
    }
}
