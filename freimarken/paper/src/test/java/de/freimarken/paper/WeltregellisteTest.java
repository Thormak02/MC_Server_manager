package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Weltregel;
import de.freimarken.kern.Weltregelwerte;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Abschnitt {@code weltregeln:} aus der config.yml.
 *
 * <p>Moeglich ohne laufenden Server, weil {@code YamlConfiguration} reine Dateiarbeit ist
 * - dieselbe Bauart wie {@link BlockbuchlisteTest}. Der Weltschluessel kommt aus einer
 * Naht und nicht aus {@code NamespacedKey}: genau darum ist dieser Zweig pruefbar.
 *
 * <p><b>Die Richtung dieses Lesers ist die OFFENE</b>, anders als beim Blockbuch, und das
 * schlaegt auf fast jede Probe hier durch: Fehlt eine Zeile, gilt KEIN Schutz. Ein
 * Betreiber, der glaubt, etwas eingetragen zu haben, findet sonst eines Tages einen
 * Krater - darum muss jede Luecke im Bericht STEHEN.
 */
@DisplayName("Die Weltregelliste")
class WeltregellisteTest {

    /** Die Welten, die es laut config.yml gibt. */
    private static final Set<String> WELTEN =
            Set.of("marktstadt", "altfurt", "kornmark");

    /** Dieselbe Umwandlung, die das Plugin mit NamespacedKey macht. */
    private static final Blockbuchliste.Schluesselmacher MACHER =
            name -> "freimarken:" + name;

    /**
     * Die SCHLUESSEL der Welten, in denen gebaut wird.
     *
     * <p>Nur dort kann eine Grenzregel wirken - und das sind seit dem 27.11.2026
     * alle Bauwelten und nicht mehr nur die Marktstadt. Altfurt steht mit darin,
     * obwohl es keine Besitzeinheiten hat: Eine ganz gebaute Welt verbietet schlicht.
     *
     * <p>SCHLUESSEL und nicht Namen, weil der Leser erst umwandelt und dann prueft.
     */
    private static final Set<String> BAUWELTEN =
            Set.of("freimarken:marktstadt", "freimarken:altfurt");

    private static Weltregelliste.Ergebnis lies(String yaml) {
        return Weltregelliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)),
                WELTEN, MACHER, BAUWELTEN);
    }

    @Test
    @DisplayName("Eine Liste von Namen wird gelesen")
    void vorgabeWirdGelesen() {
        var gelesen = lies("""
                weltregeln:
                  regeln: [explosion, feuer]
                """);

        assertEquals(List.of(), gelesen.klagen(), "diese Datei ist in Ordnung");
        assertEquals("[explosion, feuer]",
                Weltregelwerte.wieEingetragen(gelesen.werte().vorgabe()));
        assertTrue(gelesen.werte().greift("freimarken:kornmark", Weltregel.EXPLOSION),
                "eine Welt ohne eigene Zeile folgt der Vorgabe");
    }

    @Test
    @DisplayName("Je Welt eine eigene Liste - und sie wird zum SCHLUESSEL")
    void jeWeltZumSchluessel() {
        // DIESELBE FALLE WIE IM BLOCKBUCHLESER, und hier ist sie teurer: Die config.yml
        // schreibt "marktstadt", die Welt traegt "freimarken:marktstadt". Bliebe die
        // Karte nach dem NAMEN geschluesselt, fragte die Wache fuer immer ins Leere -
        // und zwar stumm. Beim Blockbuch bliebe ein Buch leer; hier bleibt eine Welt
        // UNGESCHUETZT, obwohl sie eingetragen ist.
        var gelesen = lies("""
                weltregeln:
                  regeln: []
                  welten:
                    marktstadt: [explosion, feuer, wesen, kolben, fluessigkeit]
                    kornmark: []
                """);

        assertEquals(List.of(), gelesen.klagen());
        assertTrue(gelesen.werte().jeWelt().containsKey("freimarken:marktstadt"),
                "nach dem SCHLUESSEL geschluesselt, nicht nach dem Namen: "
                        + gelesen.werte().jeWelt().keySet());
        assertFalse(gelesen.werte().jeWelt().containsKey("marktstadt"));
        for (Weltregel regel : Weltregel.values()) {
            assertTrue(gelesen.werte().greift("freimarken:marktstadt", regel),
                    "die Marktstadt bekommt alles - " + regel.schluessel());
        }
    }

    @Test
    @DisplayName("Eine LEERE Liste heisst \"hier nicht\" und faellt nicht zurueck")
    void leereListeIstKeinRueckfall() {
        // Der Unterschied, auf den es ankommt: Mit einem Rueckfall auf die Vorgabe waere
        // in der Kornmark TNT verboten - und dort ist TNT das Werkzeug, mit dem
        // gearbeitet wird.
        var gelesen = lies("""
                weltregeln:
                  regeln: [explosion]
                  welten:
                    kornmark: []
                """);

        assertEquals(List.of(), gelesen.klagen());
        assertTrue(gelesen.werte().greift("freimarken:altfurt", Weltregel.EXPLOSION),
                "altfurt hat keine eigene Zeile und erbt die Vorgabe");
        assertFalse(gelesen.werte().greift("freimarken:kornmark", Weltregel.EXPLOSION),
                "die leere Liste nimmt die Kornmark aus");
    }

    @Test
    @DisplayName("Ein unbekannter Regelname ist eine Klage MIT dem Namen")
    void unbekannterRegelnameWirdBeklagt() {
        var gelesen = lies("""
                weltregeln:
                  regeln: [explosion, zerstoerung]
                """);

        assertEquals(1, gelesen.klagen().size(), "" + gelesen.klagen());
        String klage = gelesen.klagen().get(0);
        assertTrue(klage.contains("zerstoerung"),
                "die Klage muss den Tippfehler nennen: " + klage);
        for (String erlaubt : Weltregel.erlaubteNamen()) {
            assertTrue(klage.contains(erlaubt),
                    "und JEDEN erlaubten Namen zeigen - " + erlaubt + " fehlt: " + klage);
        }
        assertTrue(gelesen.werte().greift("freimarken:marktstadt", Weltregel.EXPLOSION),
                "die uebrigen Namen bleiben stehen - eine kaputte Zeile darf nicht die"
                        + " ganze Liste verwerfen");
    }

    @Test
    @DisplayName("Ein true wird BEKLAGT und nicht stumm als false gelesen")
    void wahrheitswertWirdBeklagt() {
        // DER GEMESSENE GRUND FUER DIE WEISSLISTE: Bukkit liest jedes Wort, das nicht
        // "true" ist, stumm als false. Stuende hier ein Wahrheitswert, waere ein
        // Tippfehler ein ausgeschalteter Schutz, den niemand sieht.
        var gelesen = lies("""
                weltregeln:
                  regeln: true
                """);

        assertEquals(1, gelesen.klagen().size(), "" + gelesen.klagen());
        assertTrue(gelesen.klagen().get(0).contains("keine Liste"),
                gelesen.klagen().get(0));
        assertTrue(gelesen.klagen().get(0).contains("[explosion, feuer]"),
                "die Klage muss zeigen, wie es richtig aussieht: "
                        + gelesen.klagen().get(0));
        assertFalse(gelesen.werte().greiftIrgendwo(),
                "verworfen heisst: es gilt die Vorgabe des Programms, und die ist leer");
    }

    @Test
    @DisplayName("Eine Zahl in der Liste ist kein Name - Klage, Rest bleibt")
    void zahlInDerListeWirdBeklagt() {
        var gelesen = lies("""
                weltregeln:
                  regeln: [explosion, 7]
                """);

        assertEquals(1, gelesen.klagen().size(), "" + gelesen.klagen());
        assertTrue(gelesen.klagen().get(0).contains("kein Name"),
                gelesen.klagen().get(0));
        assertTrue(gelesen.werte().greift("freimarken:marktstadt", Weltregel.EXPLOSION));
    }

    @Test
    @DisplayName("Ohne Abschnitt sagt der Bericht, dass NICHTS schuetzt")
    void ohneAbschnittSagtDerBerichtDassNichtsSchuetzt() {
        // DIE WICHTIGSTE BERICHTZEILE DIESES LESERS. Beim Blockbuch war die Vorgabe die
        // sichere Richtung, hier ist sie die offene: Ohne Abschnitt greift keine Regel.
        // Eine Klage waere falsch - sie verhinderte das Neuladen auf jedem Server, dessen
        // Datei den Abschnitt noch nicht hat, und eine vorhandene config.yml wird beim
        // Update NIE ueberschrieben. Aber verschweigen darf man es auch nicht.
        var gelesen = lies("""
                welten:
                  marktstadt:
                    art: stadt
                """);

        assertEquals(List.of(), gelesen.klagen(), "keine Klage - nur ein Bericht");
        assertFalse(gelesen.werte().greiftIrgendwo());
        String alles = String.join(" | ", gelesen.bericht());
        assertTrue(alles.contains("TNT"),
                "der Bericht muss sagen, WAS dann passiert: " + alles);
        assertTrue(alles.contains(Weltregelwerte.PFAD_REGELN),
                "und wo man es aendert: " + alles);
    }

    @Test
    @DisplayName("Jede fehlende Zeile wird gezaehlt und genannt")
    void fehlendeZeilenWerdenGezaehlt() {
        var gelesen = lies("""
                weltregeln:
                  regeln: [explosion]
                """);

        String alles = String.join(" | ", gelesen.bericht());
        assertTrue(alles.contains("1 von 2"),
                "eine von zwei Zeilen fehlt (welten:): " + alles);
        assertTrue(alles.contains(Weltregelwerte.PFAD_WELTEN), alles);
    }

    @Test
    @DisplayName("Eine Welt, die es nicht gibt, wird beklagt")
    void unbekannteWeltWirdBeklagt() {
        // Eine Zeile, die nie wirkt, sieht man an der Datei nicht - und bei einem Schutz
        // ist das der Fall, in dem jemand sich sicher glaubt.
        var gelesen = lies("""
                weltregeln:
                  regeln: []
                  welten:
                    marktstadt: [explosion]
                    seeland: [explosion]
                """);

        assertEquals(1, gelesen.klagen().size(), "" + gelesen.klagen());
        assertTrue(gelesen.klagen().get(0).contains("seeland"),
                gelesen.klagen().get(0));
        assertTrue(gelesen.werte().jeWelt().containsKey("freimarken:marktstadt"),
                "die uebrigen Welten bleiben - ein Server, der wegen eines Tippfehlers"
                        + " in einer von vier Welten nicht startet, ist schlimmer");
        assertFalse(gelesen.werte().jeWelt().containsKey("freimarken:seeland"));
    }

    @Test
    @DisplayName("Ein Weltschluessel OHNE Namensraum ist eine Klage ueber das PLUGIN")
    void weltnameOhneNamensraumWirdBeklagt() {
        // DER WALL. Gibt der Schluesselmacher einen NAMEN zurueck, ist das kein Fehler in
        // der Datei des Betreibers, sondern eine falsch verdrahtete Naht - und die Klage
        // sagt das auch, damit niemand in seiner config.yml nach etwas sucht, das dort
        // in Ordnung ist. Uebersprungen wird die Welt trotzdem: Sonst lage sie unter
        // einem Schluessel in der Karte, nach dem die Wache nie fragt.
        var gelesen = Weltregelliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader("""
                        weltregeln:
                          regeln: []
                          welten:
                            marktstadt: [explosion]
                        """)),
                WELTEN, name -> name, BAUWELTEN);

        assertEquals(1, gelesen.klagen().size(), "" + gelesen.klagen());
        assertTrue(gelesen.klagen().get(0).contains("KEIN FEHLER IN DEINER DATEI"),
                gelesen.klagen().get(0));
        assertTrue(gelesen.klagen().get(0).contains("namensraum:name"),
                gelesen.klagen().get(0));
        assertTrue(gelesen.werte().jeWelt().isEmpty(),
                "nichts eingetragen ist besser als unter dem falschen Schluessel");
    }

    @Test
    @DisplayName("Ohne Schluesselmacher wirft es - und nennt den Grund")
    void ohneMacherWirft() {
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> Weltregelliste.ausKonfiguration(
                        YamlConfiguration.loadConfiguration(
                                new StringReader("weltregeln:\n  regeln: []\n")),
                        WELTEN, null, BAUWELTEN));

        assertTrue(fehler.getMessage().contains("SCHLUESSEL"), fehler.getMessage());
    }

    @Test
    @DisplayName("Ohne Konfiguration die Vorgabe - und sie ist leer")
    void ohneWurzelDieVorgabe() {
        var gelesen = Weltregelliste.ausKonfiguration(null, WELTEN, MACHER,
                BAUWELTEN);

        assertEquals(List.of(), gelesen.klagen());
        assertFalse(gelesen.werte().greiftIrgendwo());
    }

    @Test
    @DisplayName("Der Bericht nennt, was WIRKLICH gilt - je Welt")
    void berichtNenntWasWirklichGilt() {
        // Damit im Protokoll steht, was gilt, und nicht nur, was der Betreiber glaubt
        // eingetragen zu haben. Und die Teilung gehoert dazu: Wer kolben eintraegt und
        // nur weltweite Wirkung erwartet, sucht den Fehler sonst in der Wache.
        var gelesen = lies("""
                weltregeln:
                  regeln: [explosion]
                  welten:
                    kornmark: []
                """);

        String alles = String.join(" | ", gelesen.bericht());
        assertTrue(alles.contains("Vorgabe [explosion]"), alles);
        assertTrue(alles.contains("freimarken:kornmark []"), alles);
        assertTrue(alles.contains("WELTWEIT"),
                "die zwei Sorten muessen im Bericht stehen: " + alles);
        assertTrue(alles.contains("neuladen"), alles);
    }

    @Test
    @DisplayName("Die ausgelieferte config.yml ergibt Regeln ohne eine einzige Klage")
    void dieAusgelieferteDatei() throws Exception {
        // DIE PROBE, DIE EIN TIPPFEHLER IN DER MITGELIEFERTEN DATEI NICHT UEBERLEBT.
        // Ohne sie faellt er erst beim Start eines echten Servers auf - mit einer Klage,
        // die niemand gelesen hat, und einer Welt, die ungeschuetzt bleibt.
        //
        // Die Weltnamen kommen aus DEMSELBEN Abschnitt welten:, den das Plugin liest,
        // und nicht aus einer Liste hier: Sonst prueefte diese Probe ihre eigene
        // Abschrift, und eine Welt, die in welten: fehlt, faende sie nie.
        try (var strom = getClass().getResourceAsStream("/config.yml")) {
            assertTrue(strom != null, "config.yml liegt nicht im Klassenpfad");
            var datei = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));
            var welten = datei.getConfigurationSection("welten");
            assertTrue(welten != null, "der Abschnitt welten: fehlt");

            var gelesen = Weltregelliste.ausKonfiguration(datei,
                    welten.getKeys(false), MACHER, bauweltenAus(welten));

            assertEquals(List.of(), gelesen.klagen(),
                    "die ausgelieferte Datei klagt: " + gelesen.klagen());
            assertEquals(welten.getKeys(false).size(), gelesen.werte().jeWelt().size(),
                    "JEDE Welt aus welten: braucht ihre Zeile in weltregeln.welten -"
                            + " eine ohne folgt der leeren Vorgabe und ist ungeschuetzt,"
                            + " ohne dass die Datei das zeigt. Eingetragen: "
                            + gelesen.werte().jeWelt().keySet());
            for (Weltregel regel : Weltregel.values()) {
                assertTrue(gelesen.werte().greift("freimarken:marktstadt", regel),
                        "die Marktstadt ist die Stadt mit den Parzellen und den Laeden -"
                                + " dort muss JEDE Regel greifen, auch "
                                + regel.schluessel());
            }

            // JEDE BAUWELT BEKOMMT ALLE FUENF [Betreiber 27.11.2026: "soll ueberall
            // wo gebaut wird wirken"]. Zweimal berichtigt, und beide Male war die
            // Zusage hier der eigentliche Fund: Zuerst stand "Altfurt muss JEDE
            // Regel greifen", obwohl kolben dort nichts tat; dann "die Grenzregeln
            // koennen es nicht", obwohl sie es seit der Umstellung koennen.
            //
            // In Altfurt wirken sie ANDERS - es gibt dort keine Einheiten, also
            // verbieten sie schlicht -, aber sie wirken. Was der Leser liefert, ist
            // in beiden Faellen dasselbe: die Regel steht in der Auswahl.
            for (String bauwelt : List.of("freimarken:altfurt",
                    "freimarken:freistaedte", "freimarken:hafenwerder",
                    "freimarken:wolkenwerder")) {
                for (Weltregel regel : Weltregel.values()) {
                    assertTrue(gelesen.werte().greift(bauwelt, regel),
                            bauwelt + " ist eine Bauwelt - dort muss JEDE Regel"
                                    + " greifen, auch " + regel.schluessel());
                }
            }
            assertFalse(gelesen.werte().greift("freimarken:kornmark",
                            Weltregel.EXPLOSION),
                    "die Farmwelten bleiben frei - dort ist TNT das Werkzeug");
            assertTrue(gelesen.werte().vorgabe().isEmpty(),
                    "die VORGABE bleibt leer: Eine neu angelegte Welt soll nicht"
                            + " ungefragt unter einem Schutz stehen, von dem niemand"
                            + " weiss");
        }
    }

    @Test
    @DisplayName("Eine Grenzregel in einer Welt OHNE Bauland wird beklagt")
    void grenzregelOhneRasterWirdBeklagt() {
        // DIE LUECKE, DIE DAS GEGENLESEN GEFUNDEN HAT. Bei einer UNBEKANNTEN Welt
        // beklagte dieser Leser schon "eine Zeile, die nie wirkt, und an der Datei
        // sieht man das nicht" - bei einer BEKANNTEN Welt ging genau dieselbe
        // Gestalt durch. In der ausgelieferten Datei stand sie bei Altfurt.
        //
        // HERAUSGENOMMEN und nicht bloss beklagt: Bliebe die Regel in den Werten,
        // behauptete greift einen Schutz, den es nicht gibt.
        var gelesen = lies("""
                weltregeln:
                  regeln: []
                  welten:
                    marktstadt: [explosion, kolben]
                    kornmark: [explosion, kolben, fluessigkeit]
                """);

        assertEquals(1, gelesen.klagen().size(), "" + gelesen.klagen());
        String klage = gelesen.klagen().get(0);
        assertTrue(klage.contains("kornmark"), klage);
        assertTrue(klage.contains("kolben") && klage.contains("fluessigkeit"),
                "die Klage muss BEIDE wirkungslosen Regeln nennen: " + klage);
        assertTrue(klage.contains("marktstadt"),
                "und sagen, wo gebaut wird: " + klage);
        assertTrue(klage.contains("[explosion]"),
                "und was bleibt: " + klage);

        assertTrue(gelesen.werte().greift("freimarken:kornmark", Weltregel.EXPLOSION),
                "die weltweite Regel bleibt stehen - eine kaputte Angabe darf nicht"
                        + " die ganze Zeile verwerfen");
        assertFalse(gelesen.werte().greift("freimarken:kornmark", Weltregel.KOLBEN));
        assertFalse(gelesen.werte().greift("freimarken:kornmark",
                Weltregel.FLUESSIGKEIT));

        // DIE GEGENRICHTUNG: In einer Bauwelt bleibt kolben unangetastet.
        // Ohne diesen Satz waere die Probe auch gruen, wenn der Riegel JEDE
        // Grenzregel ueberall herausnaehme - und dann wirkte kolben nirgends.
        assertTrue(gelesen.werte().greift("freimarken:marktstadt", Weltregel.KOLBEN),
                "in der Marktstadt wird gebaut, dort gehoert kolben hin");
    }

    @Test
    @DisplayName("Ohne bekannte Parzellenwelt wird NICHTS herausgenommen")
    void ohneParzellenweltKeinEingriff() {
        // Ein Leser soll keine Parzellenwelt erfinden. Weiss der Aufrufer sie
        // nicht, bleibt die Datei, wie sie ist - lieber eine wirkungslose Zeile als
        // eine stumm entfernte Regel in der richtigen Welt.
        var gelesen = Weltregelliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader("""
                        weltregeln:
                          regeln: []
                          welten:
                            kornmark: [kolben]
                        """)),
                WELTEN, MACHER, Set.of());

        assertEquals(List.of(), gelesen.klagen());
        assertTrue(gelesen.werte().greift("freimarken:kornmark", Weltregel.KOLBEN));
    }

    /**
     * Die Bauwelt-Schluessel aus dem Abschnitt {@code welten:} der echten Datei.
     *
     * <p>Gelesen und nicht abgeschrieben, mit DERSELBEN Entscheidung, die das Plugin
     * trifft ({@code Weltangabe.Art.hatBauland}): Eine Liste von Hand hier waere eine
     * zweite Wahrheit, und die Probe prueefte dann ihre eigene Abschrift.
     */
    private static Set<String> bauweltenAus(
            org.bukkit.configuration.ConfigurationSection welten) {
        var schluessel = new java.util.LinkedHashSet<String>();
        for (String name : welten.getKeys(false)) {
            var eintrag = welten.getConfigurationSection(name);
            var art = Weltenliste.art(eintrag == null ? null
                    : eintrag.getString("art"));
            if (art != null && art.hatBauland()) {
                schluessel.add(MACHER.fuer(name));
            }
        }
        return schluessel;
    }
}
