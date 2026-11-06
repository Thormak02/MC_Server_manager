package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Berufsangabe;
import de.freimarken.kern.Berufsverzeichnis;
import de.freimarken.kern.Berufswerte;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser der Berufe - reine Dateiarbeit, darum ohne Server pruefbar.
 *
 * <p>Gleiche Bauart wie {@link WeltenlisteTest} und {@link WirtschaftslisteTest}: Eine
 * erfundene Datei hinein, Klagen und Werte heraus. Jede Probe hier faengt einen Eintrag,
 * der im Betrieb nichts kaputtmacht, aber auch nicht wirkt - und das ist die schlimmere
 * Sorte: Der Betreiber hat etwas eingetragen und sieht nicht, dass es nicht gilt.
 *
 * <p><b>Die wichtigste Probe ist {@link #ausgelieferteDateiIstBrauchbar()}.</b> Sie faengt
 * den Fall, dass die mitgelieferte {@code config.yml} gegen die eigenen Schranken
 * verstoesst - also einen Erststart, der mit einer severe-Zeile beginnt, bevor der
 * Betreiber ein Zeichen getippt hat.
 */
class BerufslisteTest {

    /** Eine Liste, die genau die Vorgabezahlen hergibt. */
    private static final String ZWEI_UND_EINE = """
            berufe:
              liste:
                bergmann:
                  spezialisierungen: [steinmetz]
                bauer:
                  spezialisierungen: []
            """;

    private static Berufsliste.Liste liste(String yaml) {
        return Berufsliste.listeAus(YamlConfiguration
                .loadConfiguration(new StringReader(yaml))
                .getConfigurationSection(Berufswerte.PFAD_LISTE));
    }

    private static Berufsliste.Werte werte(String yaml, Berufsverzeichnis gilt) {
        return Berufsliste.werteAus(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)), gilt);
    }

    /** Das Verzeichnis zu einer erfundenen Datei - gelesen vom Leser selbst. */
    private static Berufsverzeichnis verzeichnisAus(String yaml) {
        Berufsliste.Liste gelesen = liste(yaml);
        assertTrue(gelesen.klagen().isEmpty(), gelesen.klagen().toString());
        return gelesen.verzeichnis();
    }

    // --- Die Liste -------------------------------------------------------------

    @Test
    @DisplayName("Ein Eintrag je Grundberuf, die Spezialisierungen daran")
    void dasDateiformat() {
        Berufsliste.Liste gelesen = liste(ZWEI_UND_EINE);

        assertTrue(gelesen.klagen().isEmpty(), gelesen.klagen().toString());
        // Die Reihenfolge ist die der DATEI: zuerst der Grundberuf, dann seine
        // Spezialisierungen, dann der naechste Grundberuf.
        assertEquals(List.of("bergmann", "steinmetz", "bauer"),
                gelesen.verzeichnis().berufe());
        assertEquals(List.of("bergmann", "bauer"), gelesen.verzeichnis().grundberufe());
        assertEquals(List.of("steinmetz"), gelesen.verzeichnis().spezialisierungen());
        assertEquals("bergmann",
                gelesen.verzeichnis().grundberufVon("steinmetz").orElseThrow());
    }

    @Test
    @DisplayName("Leere Klammern heissen: keine Spezialisierung")
    void leereKlammern() {
        Berufsliste.Liste gelesen = liste("""
                berufe:
                  liste:
                    bergmann:
                      spezialisierungen: []
                """);

        assertTrue(gelesen.klagen().isEmpty(), gelesen.klagen().toString());
        assertEquals(List.of("bergmann"), gelesen.verzeichnis().berufe());
    }

    @Test
    @DisplayName("Ein Eintrag OHNE jeden Inhalt kommt beim Leser nie an - gemessen")
    void eintragOhneInhaltVerschwindetInBukkit() {
        // GEMESSEN und nicht gewuenscht: Bukkit wirft eine Zeile ohne Wert beim Laden
        // weg, der Schluessel existiert danach nicht mehr. Der Leser kann darueber also
        // nicht klagen - er sieht nichts.
        //
        // Diese Probe steht hier, damit niemand die Warnung in der config.yml wieder
        // herausnimmt. Dort stand "der Eintrag darf dann auch ganz leer bleiben", und das
        // war falsch: Der Beruf verschwand stumm. Faengt ein spaeteres Bukkit die Zeile
        // doch, wird diese Probe rot - und dann gehoert der Fall in die Klage.
        Berufsliste.Liste gelesen = liste("""
                berufe:
                  liste:
                    bergmann:
                    bauer:
                      spezialisierungen: []
                """);

        assertEquals(List.of("bauer"), gelesen.verzeichnis().berufe(),
                "bergmann ist verschwunden, bevor der Leser ihn sehen konnte");
        assertTrue(gelesen.klagen().isEmpty(),
                "und es gibt nichts zu klagen - das ist der ganze Grund fuer die Warnung"
                        + " in der config.yml");
    }

    @Test
    @DisplayName("Ohne Abschnitt gibt es keine Berufe - und eine Klage")
    void ohneAbschnitt() {
        Berufsliste.Liste gelesen = liste("wirtschaft:\n  parzelle:\n    grundpreis: 1\n");

        assertEquals(List.of(), gelesen.verzeichnis().berufe());
        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.klagen().getFirst().contains(Berufswerte.PFAD_LISTE),
                gelesen.klagen().getFirst());
    }

    @Test
    @DisplayName("Ein Eintrag, der kein Abschnitt ist, wird uebersprungen")
    void keinAbschnitt() {
        // bergmann: steinmetz sieht aus wie "die Spezialisierung heisst steinmetz" und
        // ist doch nur ein Text. Ohne Klage waere bergmann ein Grundberuf ohne
        // Spezialisierung, und der Betreiber suchte seinen steinmetz.
        Berufsliste.Liste gelesen = liste("""
                berufe:
                  liste:
                    bergmann: steinmetz
                    bauer:
                      spezialisierungen: []
                """);

        assertEquals(List.of("bauer"), gelesen.verzeichnis().berufe());
        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.klagen().getFirst().contains("steinmetz"),
                "die Klage muss zeigen, was dastand: " + gelesen.klagen().getFirst());
    }

    @Test
    @DisplayName("Eine vergessene Klammer wird uebersprungen und nicht leer gelesen")
    void keineListe() {
        // getStringList machte daraus stumm eine leere Liste - der Betreiber suchte
        // seinen steinmetz, und nichts klagte.
        Berufsliste.Liste gelesen = liste("""
                berufe:
                  liste:
                    bergmann:
                      spezialisierungen: steinmetz
                """);

        assertEquals(List.of("bergmann"), gelesen.verzeichnis().berufe());
        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.klagen().getFirst().contains("[steinmetz]"),
                "die Klage soll die richtige Schreibweise zeigen: "
                        + gelesen.klagen().getFirst());
    }

    @Test
    @DisplayName("Ein Schluessel mit 33 Zeichen wird abgewiesen, einer mit 32 nicht")
    void laengeGilt() {
        String grenze = "a".repeat(Berufsangabe.HOECHSTENS_ZEICHEN);
        String rumpf = "berufe:\n  liste:\n    ";
        String leer = ":\n      spezialisierungen: []\n";
        Berufsliste.Liste gerade = liste(rumpf + grenze + leer);
        assertTrue(gerade.klagen().isEmpty(), gerade.klagen().toString());
        assertEquals(List.of(grenze), gerade.verzeichnis().berufe());

        // Ohne diese Schranke schnitte MariaDB den Schluessel stumm ab: Zwei Berufe
        // fielen zusammen, und ein Spieler verlore einen.
        Berufsliste.Liste zuLang = liste(rumpf + grenze + "a" + leer);
        assertEquals(List.of(), zuLang.verzeichnis().berufe());
        assertEquals(1, zuLang.klagen().size(), zuLang.klagen().toString());
        assertTrue(zuLang.klagen().getFirst().contains("33"),
                "die Klage muss die Zahl nennen: " + zuLang.klagen().getFirst());
        assertTrue(zuLang.klagen().getFirst().contains("32"),
                zuLang.klagen().getFirst());
    }

    @Test
    @DisplayName("Ein Punkt im Schluessel wird abgewiesen - er ist der Trenner der Sprachdatei")
    void punktImSchluessel() {
        // beruf.name.berg.mann waere in der sprache.yml eine Ebene tiefer statt ein Name.
        Berufsliste.Liste gelesen = liste("""
                berufe:
                  liste:
                    bergmann:
                      spezialisierungen: ["berg.mann"]
                """);

        assertEquals(List.of("bergmann"), gelesen.verzeichnis().berufe());
        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.klagen().getFirst().contains("Punkt"),
                gelesen.klagen().getFirst());
    }

    @Test
    @DisplayName("Derselbe Name zweimal - ALLE Nennungen fallen weg")
    void doppelterNameFaelltGanzWeg() {
        // Nicht "die erste gilt": Steht steinmetz unter zwei Grundberufen, entschiede die
        // Reihenfolge der Eintraege, welchen er voraussetzt - und eine Datei umzusortieren
        // aenderte stumm die Spielregeln.
        Berufsliste.Liste gelesen = liste("""
                berufe:
                  liste:
                    bergmann:
                      spezialisierungen: [steinmetz]
                    bauer:
                      spezialisierungen: [steinmetz]
                """);

        assertEquals(List.of("bergmann", "bauer"), gelesen.verzeichnis().berufe());
        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.klagen().getFirst().contains("2 mal"),
                gelesen.klagen().getFirst());
        assertTrue(gelesen.klagen().getFirst().contains("bergmann")
                        && gelesen.klagen().getFirst().contains("bauer"),
                "die Klage muss BEIDE Stellen nennen: " + gelesen.klagen().getFirst());
    }

    @Test
    @DisplayName("Ein Grundberuf, der auch Spezialisierung ist, faellt mit seinen Kindern")
    void grundberufUndSpezialisierungZugleich() {
        // steinmetz ist Grundberuf UND Spezialisierung. Die Platzzaehlung kennt zwei
        // Toepfe, und er zaehlte in beiden. Seine eigene Spezialisierung bildhauer faellt
        // mit: Sie haenge sonst an einem Beruf, den es nicht mehr gibt.
        Berufsliste.Liste gelesen = liste("""
                berufe:
                  liste:
                    bergmann:
                      spezialisierungen: [steinmetz]
                    steinmetz:
                      spezialisierungen: [bildhauer]
                """);

        assertEquals(List.of("bergmann"), gelesen.verzeichnis().berufe());
        assertFalse(gelesen.klagen().isEmpty());
    }

    @Test
    @DisplayName("Gross geschrieben ist derselbe Beruf - und keiner baut auf sich selbst auf")
    void grossUndKleinIstDerselbe() {
        // Zwei Dinge in einer Probe, weil es dieselbe Falle ist: Bergmann und BERGMANN
        // sind derselbe Beruf, also baute die zweite Zeile eine Spezialisierung, die auf
        // sich selbst aufbaut. Der Kompaktkonstruktor von Berufsangabe wirft dabei, und
        // ein Leser der config.yml darf nicht werfen - genau das hat diese Probe gefunden.
        Berufsliste.Liste gelesen = liste("""
                berufe:
                  liste:
                    Bergmann:
                      spezialisierungen: [BERGMANN]
                """);

        assertEquals(List.of("bergmann"), gelesen.verzeichnis().berufe(),
                "der Grundberuf bleibt, kleingeschrieben");
        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.klagen().getFirst().contains("Grundberuf selbst"),
                gelesen.klagen().getFirst());
    }

    // --- Die vier Zahlen -------------------------------------------------------

    @Test
    @DisplayName("Die Vorgaben gelten, wenn die Datei nichts sagt")
    void vorgabenGelten() {
        Berufsverzeichnis gilt = verzeichnisAus(ZWEI_UND_EINE);
        Berufsliste.Werte gelesen = werte(ZWEI_UND_EINE, gilt);

        assertTrue(gelesen.klagen().isEmpty(), gelesen.klagen().toString());
        assertEquals(Berufswerte.VORGABE, gelesen.werte());
        // Und der Bericht sagt, dass vier Zeilen fehlen - sonst sucht der Betreiber sie
        // in einer Datei, in der sie nicht stehen.
        assertTrue(gelesen.bericht().stream().anyMatch(z -> z.contains("4 von 5")),
                gelesen.bericht().toString());
    }

    @Test
    @DisplayName("Die vier Zahlen werden gelesen")
    void zahlenWerdenGelesen() {
        Berufsverzeichnis gilt = verzeichnisAus("""
                berufe:
                  liste:
                    bergmann:
                      spezialisierungen: [steinmetz, juwelier]
                    bauer:
                      spezialisierungen: [mueller]
                    fischer:
                      spezialisierungen: []
                """);
        Berufsliste.Werte gelesen = werte("""
                berufe:
                  wechselgebuehr: 7500
                  sperrfrist-stunden: 48
                  hoechstens-grundberufe: 3
                  hoechstens-spezialisierungen: 2
                """, gilt);

        assertTrue(gelesen.klagen().isEmpty(), gelesen.klagen().toString());
        assertEquals(7_500L, gelesen.werte().wechselgebuehr());
        assertEquals(Duration.ofHours(48L), gelesen.werte().sperrfrist());
        assertEquals(3, gelesen.werte().grundberufe());
        assertEquals(2, gelesen.werte().spezialisierungen());
    }

    @Test
    @DisplayName("Mehr Plaetze als die laufende Liste hergibt - verworfen, mit beiden Zahlen")
    void mehrPlaetzeAlsBerufe() {
        Berufsverzeichnis gilt = verzeichnisAus(ZWEI_UND_EINE);
        Berufsliste.Werte gelesen = werte("""
                berufe:
                  hoechstens-grundberufe: 7
                """, gilt);

        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertTrue(gelesen.klagen().getFirst().contains("7")
                        && gelesen.klagen().getFirst().contains("2"),
                "die Klage muss beide Zahlen nennen: " + gelesen.klagen().getFirst());
        assertEquals(Berufswerte.VORGABE_GRUNDBERUFE, gelesen.werte().grundberufe());
    }

    @Test
    @DisplayName("Eine kurze Liste kuerzt die VORGABE - als Bericht, nicht als Klage")
    void kurzeListeKuerztDieVorgabe() {
        // Der Unterschied zur Probe davor ist die HAERTE, und sie haengt daran, ob der
        // Betreiber etwas getippt hat: Eine Klage verhinderte jedes Neuladen, solange die
        // Liste kurz ist - abgewiesen wird aber nichts, was er geschrieben hat.
        Berufsverzeichnis gilt = verzeichnisAus("""
                berufe:
                  liste:
                    bergmann:
                      spezialisierungen: []
                """);
        Berufsliste.Werte gelesen = werte("berufe:\n  wechselgebuehr: 5000\n", gilt);

        assertTrue(gelesen.klagen().isEmpty(), gelesen.klagen().toString());
        assertEquals(1, gelesen.werte().grundberufe(), "gekuerzt auf die Liste");
        assertEquals(0, gelesen.werte().spezialisierungen());
        assertTrue(gelesen.bericht().stream().anyMatch(z -> z.contains("Es gelten 1")),
                gelesen.bericht().toString());
    }

    @Test
    @DisplayName("Eine 0 bei den Grundberufen wird abgewiesen, bei den Spezialisierungen nicht")
    void nullPlaetze() {
        Berufsverzeichnis gilt = verzeichnisAus(ZWEI_UND_EINE);

        Berufsliste.Werte keinGrundberuf = werte(
                "berufe:\n  hoechstens-grundberufe: 0\n", gilt);
        assertEquals(1, keinGrundberuf.klagen().size(),
                keinGrundberuf.klagen().toString());
        assertEquals(Berufswerte.VORGABE_GRUNDBERUFE,
                keinGrundberuf.werte().grundberufe());

        // Die 0 bei den Spezialisierungen ist ein echter Betriebsfall: Ein Server ohne
        // Spezialisierungen braucht das Recht dafuer nicht zu verteilen.
        Berufsliste.Werte keineSpezialisierung = werte(
                "berufe:\n  hoechstens-spezialisierungen: 0\n", gilt);
        assertTrue(keineSpezialisierung.klagen().isEmpty(),
                keineSpezialisierung.klagen().toString());
        assertEquals(0, keineSpezialisierung.werte().spezialisierungen());
    }

    @Test
    @DisplayName("Eine zu lange Sperrfrist wird abgewiesen, 0 nicht")
    void sperrfristgrenzen() {
        Berufsverzeichnis gilt = verzeichnisAus(ZWEI_UND_EINE);

        Berufsliste.Werte zuLang = werte("berufe:\n  sperrfrist-stunden: 721\n", gilt);
        assertEquals(1, zuLang.klagen().size(), zuLang.klagen().toString());
        assertTrue(zuLang.klagen().getFirst().contains("720"),
                zuLang.klagen().getFirst());
        assertEquals(Duration.ofHours(Berufswerte.VORGABE_SPERRFRIST_STUNDEN),
                zuLang.werte().sperrfrist());

        Berufsliste.Werte aus = werte("berufe:\n  sperrfrist-stunden: 0\n", gilt);
        assertTrue(aus.klagen().isEmpty(), aus.klagen().toString());
        assertFalse(aus.werte().sperrfristGilt());
        assertTrue(aus.bericht().stream().anyMatch(z -> z.contains("keine Sperrfrist")),
                aus.bericht().toString());
    }

    @Test
    @DisplayName("Eine Gebuehr mit Punkt ist keine ganze Zahl")
    void punktIstKeinTausendertrenner() {
        // In YAML trennt der Punkt die Nachkommastellen: aus 5.000 wird 5. Ohne diese
        // Klage kostete ein Wechsel fuenf Mark statt fuenftausend.
        Berufsverzeichnis gilt = verzeichnisAus(ZWEI_UND_EINE);
        Berufsliste.Werte gelesen = werte("berufe:\n  wechselgebuehr: 5.000\n", gilt);

        assertEquals(1, gelesen.klagen().size(), gelesen.klagen().toString());
        assertEquals(Berufswerte.VORGABE_WECHSELGEBUEHR,
                gelesen.werte().wechselgebuehr());
    }

    @Test
    @DisplayName("Ein grosser Sprung der Gebuehr braucht die Bestaetigung")
    void grosserSprungBrauchtBestaetigung() {
        // Dieselbe Sicherung wie bei grundpreis und startguthaben, und aus demselben
        // Grund: Es ist ein Geldwert. Der Helfer dafuer ist geteilt und nicht
        // abgeschrieben.
        Berufsverzeichnis gilt = verzeichnisAus(ZWEI_UND_EINE);
        Berufsliste.Werte ohne = werte("berufe:\n  wechselgebuehr: 50000\n", gilt);

        assertEquals(1, ohne.klagen().size(), ohne.klagen().toString());
        assertTrue(ohne.klagen().getFirst().contains("wechselgebuehr"),
                ohne.klagen().getFirst());
        assertEquals(Berufswerte.VORGABE_WECHSELGEBUEHR, ohne.werte().wechselgebuehr());

        Berufsliste.Werte mit = werte("""
                berufe:
                  wechselgebuehr: 50000
                wirtschaft:
                  sprung-bestaetigt:
                    wechselgebuehr: "5000 -> 50000"
                """, gilt);
        assertTrue(mit.klagen().isEmpty(), mit.klagen().toString());
        assertEquals(50_000L, mit.werte().wechselgebuehr());
    }

    // --- Die ausgelieferte Datei ----------------------------------------------

    @Test
    @DisplayName("Die MITGELIEFERTE config.yml verstoesst nicht gegen die eigenen Schranken")
    void ausgelieferteDateiIstBrauchbar() throws IOException {
        try (InputStream strom = Berufsliste.class.getResourceAsStream("/config.yml")) {
            assertNotNull(strom, "die config.yml muss im Jar liegen");
            var datei = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));

            Berufsliste.Liste gelesen = Berufsliste.listeAus(
                    datei.getConfigurationSection(Berufswerte.PFAD_LISTE));
            assertTrue(gelesen.klagen().isEmpty(), gelesen.klagen().toString());
            assertEquals(6, gelesen.verzeichnis().anzahlDerRolle(false),
                    "sechs Grundberufe: " + gelesen.verzeichnis().grundberufe());
            assertEquals(6, gelesen.verzeichnis().anzahlDerRolle(true),
                    "je einer dazu: " + gelesen.verzeichnis().spezialisierungen());

            Berufsliste.Werte zahlen = Berufsliste.werteAus(datei,
                    gelesen.verzeichnis());
            assertTrue(zahlen.klagen().isEmpty(), zahlen.klagen().toString());
            // Die ausgelieferte Datei muss genau die Vorgaben des Programms tragen -
            // sonst gilt bei einem Erststart etwas anderes als beim Rueckfall.
            assertEquals(Berufswerte.VORGABE, zahlen.werte());
            assertTrue(zahlen.bericht().stream().noneMatch(z -> z.contains("stehen nicht")),
                    "keine Zeile darf fehlen: " + zahlen.bericht());
        }
    }

    @Test
    @DisplayName("Zu JEDEM Beruf der ausgelieferten Datei steht ein Wort in der Vorlage")
    void jederAusgelieferteBerufHatSeinWort() throws IOException {
        // Die beidseitige Probe zur Startpruefung: Ohne sie liefert das Plugin eine Datei
        // aus, mit der es selbst nicht startet - und zwar nicht beim Bauen, sondern beim
        // ersten Start eines Betreibers.
        try (InputStream strom = Berufsliste.class.getResourceAsStream("/config.yml")) {
            assertNotNull(strom);
            var datei = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));
            Berufsverzeichnis verzeichnis = Berufsliste.listeAus(
                    datei.getConfigurationSection(Berufswerte.PFAD_LISTE)).verzeichnis();
            var buch = Sprachprobe.ausDemJar();

            for (String beruf : verzeichnis.berufe()) {
                String pfad = new Berufsname(beruf).pfad();
                assertTrue(buch.saetze().containsKey(pfad),
                        "in sprache-vorlage.yml fehlt " + pfad);
                assertFalse(buch.saetze().get(pfad).isBlank(),
                        pfad + " ist leer - dann liest ein Spieler eine Luecke");
            }
            // Und die andere Richtung: ein Wort ohne Beruf ist eine Zeile, die nicht
            // wirkt. Sie bringt nichts zum Stehen, soll aber in der ausgelieferten Datei
            // nicht vorkommen.
            for (String pfad : buch.saetze().keySet()) {
                if (pfad.startsWith(Berufsname.VORSILBE)) {
                    String beruf = pfad.substring(Berufsname.VORSILBE.length());
                    assertTrue(verzeichnis.berufe().contains(beruf),
                            pfad + " steht in der Sprachdatei, aber " + beruf
                                    + " in keiner Liste der config.yml");
                }
            }
        }
    }
}
