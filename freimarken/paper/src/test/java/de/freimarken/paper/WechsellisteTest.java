package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Wechselwerte;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser des Abschnitts {@code wechsel:} - ohne Server.
 *
 * <p>Dieselbe Bauart wie {@link MarktlisteTest}, mit einer Frage mehr: Dieser Leser prueft
 * seine beiden Kurse GEGENEINANDER. Jeder fuer sich kann im erlaubten Bereich liegen, und
 * zusammen machen sie aus jedem Rundlauf einen Gewinn.
 */
@DisplayName("Wechselstube aus der config.yml")
class WechsellisteTest {

    private static final String VOLLSTAENDIG = """
            wechsel:
              mark-je-goldmark: 1000
              mark-zurueck: 800
              tagesdeckel: 10000
            """;

    private static Wechselliste.Ergebnis lies(String yaml) {
        return Wechselliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    /** Ob irgendeine Klage mit diesem Pfad beginnt. */
    private static boolean klagtUeber(Wechselliste.Ergebnis ergebnis, String pfad) {
        return ergebnis.klagen().stream().anyMatch(klage -> klage.startsWith(pfad));
    }

    private static boolean berichtetUeber(Wechselliste.Ergebnis ergebnis, String stueck) {
        return ergebnis.bericht().stream().anyMatch(zeile -> zeile.contains(stueck));
    }

    @Test
    @DisplayName("Eine vollstaendige Datei kommt ganz an")
    void vollstaendigKommtAn() {
        var ergebnis = lies(VOLLSTAENDIG);

        assertEquals(1_000L, ergebnis.werte().markJeGoldmark());
        assertEquals(800L, ergebnis.werte().markZurueck());
        assertEquals(10_000L, ergebnis.werte().tagesdeckel());
        assertTrue(ergebnis.werte().rueckwegOffen());
        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        // UND DIE RECHENPROBE STEHT IM BERICHT. Sie ist der einzige Ort, an dem die drei
        // Zahlen im Zusammenhang erscheinen - beim Start und bei jedem Neuladen. Ohne
        // diese Zeile waere die Methode gebaut, geprueft und von niemandem gerufen.
        assertTrue(berichtetUeber(ergebnis, "Wechselstube: 1 Goldmark kostet"),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Ein Rueckweg ueber dem Hinweg verwirft BEIDE Kurse")
    void rundlaufVerwirftBeide() {
        // DER WICHTIGSTE FALL DIESES LESERS, und er ist in keiner einzelnen Zahl zu
        // sehen: 1.000 hinein, 1.200 heraus - beliebig oft, jede Buchung richtig. Dass
        // BEIDE verworfen werden, ist Absicht: Nur den Rueckweg zurueckzunehmen machte
        // das Paar wieder brauchbar und waere trotzdem nicht, was der Betreiber getippt
        // hat.
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    wechsel-zurueck: "800 -> 1200"
                wechsel:
                  mark-je-goldmark: 1000
                  mark-zurueck: 1200
                  tagesdeckel: 10000
                """);

        assertEquals(Wechselwerte.VORGABE_HIN, ergebnis.werte().markJeGoldmark());
        assertEquals(Wechselwerte.VORGABE_ZURUECK, ergebnis.werte().markZurueck());
        assertTrue(klagtUeber(ergebnis, Wechselwerte.PFAD_ZURUECK),
                ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("Rundlauf"),
                "die Klage muss sagen, WAS daran schlimm ist: "
                        + ergebnis.klagen().get(0));
        assertTrue(ergebnis.klagen().get(0).contains("200"),
                "und wie gross der Gewinn je Goldmark waere: "
                        + ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Derselbe Kurs in beide Richtungen kommt durch")
    void gleicherKursKommtDurch() {
        // Die Gegenrichtung, auf der Grenze: Ein Riegel auf >= verboete eine Einstellung,
        // die nur verlustfrei ist und kein Geld erzeugt - und diese Probe waere mit der
        // darueber genauso gruen.
        var ergebnis = lies("""
                wechsel:
                  mark-je-goldmark: 1000
                  mark-zurueck: 1000
                  tagesdeckel: 10000
                """);

        assertEquals(1_000L, ergebnis.werte().markZurueck());
        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
    }

    @Test
    @DisplayName("Ein Kurs von 0 wird verworfen - und die Klage nennt den richtigen Weg")
    void kursNullWirdVerworfen() {
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    wechsel-zurueck: "800 -> 0"
                wechsel:
                  mark-je-goldmark: 1000
                  mark-zurueck: 0
                  tagesdeckel: 10000
                """);

        assertEquals(Wechselwerte.VORGABE_ZURUECK, ergebnis.werte().markZurueck());
        assertTrue(klagtUeber(ergebnis, Wechselwerte.PFAD_ZURUECK),
                ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains(Wechselwerte.PFAD_DECKEL),
                "wer den Rueckweg schliessen will, soll lesen WIE: "
                        + ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Eine 0 beim Deckel schliesst den Rueckweg - mit Bestaetigung")
    void deckelNullSchliesstDenRueckweg() {
        // DER SPRUNGSCHUTZ GREIFT, und das ist angebracht: 10.000 auf 0 ist mehr als
        // Faktor 2, und es ist die Aenderung, die jeder Spieler mit Goldmark sofort
        // merkt. Diese Probe zeigt beide Haelften - ohne Bestaetigung bleibt die Vorgabe.
        var ohneBestaetigung = lies("""
                wechsel:
                  mark-je-goldmark: 1000
                  mark-zurueck: 800
                  tagesdeckel: 0
                """);
        assertEquals(Wechselwerte.VORGABE_DECKEL, ohneBestaetigung.werte().tagesdeckel(),
                "ohne Sprungbestaetigung bleibt es bei der Vorgabe");
        assertTrue(klagtUeber(ohneBestaetigung, Wechselwerte.PFAD_DECKEL),
                ohneBestaetigung.klagen().toString());

        var mitBestaetigung = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    wechsel-tagesdeckel: "10000 -> 0"
                wechsel:
                  mark-je-goldmark: 1000
                  mark-zurueck: 800
                  tagesdeckel: 0
                """);
        assertEquals(0L, mitBestaetigung.werte().tagesdeckel());
        assertFalse(mitBestaetigung.werte().rueckwegOffen());
        assertEquals(List.of(), mitBestaetigung.klagen(),
                mitBestaetigung.klagen().toString());
        assertTrue(berichtetUeber(mitBestaetigung, "RUECKWEG IST ZU"),
                mitBestaetigung.bericht().toString());
        // UND DER UNTERSCHIED ZUM ANDEREN DECKEL STEHT IM BERICHT: Bei
        // bundesmarkt.tagesdeckel heisst dieselbe 0 das Gegenteil. Wer das nicht liest,
        // stellt eines Tages den falschen Deckel auf 0.
        assertTrue(berichtetUeber(mitBestaetigung, "bundesmarkt.tagesdeckel"),
                mitBestaetigung.bericht().toString());
    }

    @Test
    @DisplayName("Eine negative Zahl wird verworfen - die Vorgabe bleibt")
    void negativWirdVerworfen() {
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    wechsel-tagesdeckel: "10000 -> -100"
                wechsel:
                  mark-je-goldmark: 1000
                  mark-zurueck: 800
                  tagesdeckel: -100
                """);

        assertEquals(Wechselwerte.VORGABE_DECKEL, ergebnis.werte().tagesdeckel());
        assertTrue(klagtUeber(ergebnis, Wechselwerte.PFAD_DECKEL),
                ergebnis.klagen().toString());
    }

    @Test
    @DisplayName("Ein Punkt in der Zahl wird verworfen und nicht als Tausender gelesen")
    void punktWirdVerworfen() {
        // YAML trennt mit einem Punkt keine Tausender, sondern die Nachkommastellen:
        // 1.000 ist dort die Zahl Eins. Gelesen wird darum ROH - sonst kostete eine
        // Goldmark eine Mark, und das Geld dahinter liegt binnen Minuten auf vielen
        // Konten.
        var ergebnis = lies("""
                wechsel:
                  mark-je-goldmark: 1.000
                  mark-zurueck: 800
                  tagesdeckel: 10000
                """);

        assertEquals(Wechselwerte.VORGABE_HIN, ergebnis.werte().markJeGoldmark());
        assertTrue(klagtUeber(ergebnis, Wechselwerte.PFAD_HIN),
                ergebnis.klagen().toString());
    }

    @Test
    @DisplayName("Eine fehlende Zeile wird berichtet, nicht beklagt")
    void fehlendeZeileWirdBerichtet() {
        var ergebnis = lies("""
                wechsel:
                  irgendwas: 1
                """);

        assertEquals(Wechselwerte.VORGABE, ergebnis.werte());
        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertTrue(berichtetUeber(ergebnis, Wechselwerte.PFAD_HIN),
                ergebnis.bericht().toString());
        assertTrue(berichtetUeber(ergebnis, "3 von 3"),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Kein Abschnitt heisst: die Vorgaben, und keine Klage")
    void ohneAbschnitt() {
        assertEquals(Wechselwerte.VORGABE, Wechselliste.ausKonfiguration(null).werte());
        assertEquals(List.of(), Wechselliste.ausKonfiguration(null).klagen());
    }

    @Test
    @DisplayName("Die Rechenprobe nennt beide Kurse, den Deckel und die Zusage des Plans")
    void rechenprobeNenntAlles() {
        // DIE EINZIGE SCHRANKE gegen Zahlen, die im erlaubten Bereich liegen und trotzdem
        // falsch sind - dasselbe Mittel wie Tagesgrenze.tageswechselText: Erkennung statt
        // Verhinderung. Geprueft wird der WORTLAUT und nicht, dass irgendetwas berichtet
        // wird: Eine Zeile, die die Zahlen nicht nennt, erkennt nichts.
        String zeile = Wechselliste.rechenprobe(Wechselwerte.VORGABE);

        assertEquals("Wechselstube: 1 Goldmark kostet 1000 Mark und bringt 800 Mark"
                + " zurueck - hoechstens 12 Goldmark am Tag (10000 Mark). Eine Freistadt"
                + " zu 400000 Mark braucht auf diesem Weg 40 Tage; der Plan nennt 40.",
                zeile);
    }

    @Test
    @DisplayName("Die Rechenprobe zeigt einen zu grossen Deckel an den TAGEN")
    void rechenprobeZeigtDenHebel() {
        // Die Zahl, die die Zusage des Plans traegt: Wer den Deckel verzehnfacht, liest
        // hier VIER Tage statt vierzig - und sieht, dass eine gekaufte Freistadt jetzt
        // ein Wochenendprojekt ist.
        String zeile = Wechselliste.rechenprobe(
                Wechselwerte.VORGABE.mitDeckel(100_000L));

        assertTrue(zeile.contains("4 Tage; der Plan nennt 40"), zeile);
    }

    @Test
    @DisplayName("Bei geschlossenem Rueckweg sagt die Rechenprobe genau das")
    void rechenprobeBeiZu() {
        String zeile = Wechselliste.rechenprobe(
                Wechselwerte.VORGABE.mitDeckel(Wechselwerte.RUECKWEG_ZU));

        assertTrue(zeile.contains("Rueckweg ist ZU"), zeile);
        assertFalse(zeile.contains("Tage"),
                "ohne Rueckweg gibt es keine Tagesrechnung - sie waere eine Division"
                        + " durch 0: " + zeile);
    }

    @Test
    @DisplayName("Die AUSGELIEFERTE Datei nennt alle drei Zahlen und ist lesbar")
    void ausgelieferteDateiIstBrauchbar() {
        // Die Datei, die das Plugin mitbringt, muss durch ihren eigenen Leser gehen -
        // sonst ist jede Zusage ueber sie eine Behauptung.
        try (InputStream strom = Wechselliste.class.getResourceAsStream("/config.yml")) {
            assertNotNull(strom, "config.yml muss im Jar liegen");
            var ergebnis = Wechselliste.ausKonfiguration(
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(strom, StandardCharsets.UTF_8)));

            assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
            assertEquals(Wechselwerte.VORGABE, ergebnis.werte(),
                    "die ausgelieferten Zahlen und die Vorgabe des Programms sind"
                            + " dieselben - zwei verschiedene waeren zwei Wahrheiten");
            assertFalse(ergebnis.bericht().stream()
                            .anyMatch(zeile -> zeile.contains("stehen nicht in deiner")),
                    "in der ausgelieferten Datei darf keine Zeile fehlen: "
                            + ergebnis.bericht());
        } catch (IOException fehler) {
            throw new AssertionError(fehler);
        }
    }
}
