package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Lebenszeichen;
import de.freimarken.kern.Messwerte;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Messstelle aus der {@code config.yml} gelesen.
 *
 * <p>Dieselbe Bauart wie {@link MarktlisteTest}: Eine kaputte Zeile wird beklagt und die
 * uebrigen gelten weiter, und am Ende wird die MITGELIEFERTE Datei selbst gelesen - sonst
 * waere jede Zusage ueber sie eine Behauptung.
 */
@DisplayName("Die Messstelle aus der config.yml")
class MesslisteTest {

    private static final String VOLLSTAENDIG = """
            messstelle:
              verlangte-arten: 2
              mindest-strecke: 8
              arten:
                - bewegen
                - abbauen
                - setzen
            """;

    private static Messliste.Ergebnis lies(String yaml) {
        return Messliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    /** Ob irgendeine Klage mit diesem Pfad beginnt. */
    private static boolean klagtUeber(Messliste.Ergebnis ergebnis, String pfad) {
        return ergebnis.klagen().stream().anyMatch(klage -> klage.startsWith(pfad));
    }

    private static List<String> namen(Messliste.Ergebnis ergebnis) {
        List<String> namen = new ArrayList<>();
        for (Lebenszeichen art : ergebnis.werte().arten()) {
            namen.add(art.schluessel());
        }
        return namen;
    }

    @Test
    @DisplayName("Eine vollstaendige Datei kommt ganz an")
    void vollstaendigKommtAn() {
        var ergebnis = lies(VOLLSTAENDIG);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(2, ergebnis.werte().verlangteArten());
        assertEquals(8, ergebnis.werte().mindestStrecke());
        // Die REIHENFOLGE der Datei - sie ist die Reihenfolge im Bericht.
        assertEquals(List.of("bewegen", "abbauen", "setzen"), namen(ergebnis));
    }

    @Test
    @DisplayName("Ohne Konfiguration gelten die Vorgaben und es wird nicht geklagt")
    void ohneKonfiguration() {
        var ergebnis = Messliste.ausKonfiguration(null);

        assertEquals(Messwerte.VORGABE, ergebnis.werte());
        assertTrue(ergebnis.klagen().isEmpty());
    }

    @Test
    @DisplayName("Ein fehlender Abschnitt ist ein BERICHT und keine Klage")
    void fehlenderAbschnitt() {
        var ergebnis = lies("wirtschaft:" + System.lineSeparator() + "  grundpreis: 25000");

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(Messwerte.VORGABE, ergebnis.werte());
        assertTrue(ergebnis.bericht().stream()
                        .anyMatch(zeile -> zeile.contains("3 von 3")),
                "alle drei Zeilen fehlen, und das soll dastehen: " + ergebnis.bericht());
    }

    @Test
    @DisplayName("Mehr verlangte Arten als die Liste hergibt wird beklagt und geklemmt")
    void zuVieleVerlangteArten() {
        // Der Fall, den eine Pruefung je Wert durchlaesst: 3 ist eine gueltige Zahl,
        // zwei Arten sind eine gueltige Liste. Zusammen waere kein Schlag jemals taetig.
        var ergebnis = lies("""
                messstelle:
                  verlangte-arten: 3
                  arten:
                    - abbauen
                    - bewegen
                """);

        assertTrue(klagtUeber(ergebnis, Messwerte.PFAD_VERLANGTE_ARTEN),
                ergebnis.klagen().toString());
        assertEquals(2, ergebnis.werte().verlangteArten(),
                "geklemmt auf das Strengste, was seine Liste hergibt - und nicht auf die"
                        + " Vorgabe des Programms, die bei einer Art wieder nicht passt");
        assertEquals(2, ergebnis.werte().arten().size(),
                "und die LISTE gilt weiter - eine Zahl verwirft nicht die andere Zeile");
    }

    @Test
    @DisplayName("Bei einer Liste mit EINER Art wird auf 1 geklemmt - nicht auf die Vorgabe")
    void geklemmtAufEins() {
        // Hier unterscheiden sich die zwei moeglichen Rueckfaelle: Die Vorgabe des
        // Programms ist 2, und die passt zu einer Liste mit einer Art nicht. Eine Probe
        // nur mit zwei Arten waere auch gruen, wenn hier die Vorgabe zurueckkaeme.
        var ergebnis = lies("""
                messstelle:
                  verlangte-arten: 5
                  arten:
                    - abbauen
                """);

        assertEquals(1, ergebnis.werte().verlangteArten());
        assertEquals(1, ergebnis.werte().arten().size());
    }

    @Test
    @DisplayName("Eine 0 bei verlangte-arten wird beklagt - das waere immer taetig")
    void nullVerlangteArten() {
        var ergebnis = lies("""
                messstelle:
                  verlangte-arten: 0
                """);

        assertTrue(klagtUeber(ergebnis, Messwerte.PFAD_VERLANGTE_ARTEN),
                ergebnis.klagen().toString());
        assertEquals(Messwerte.VORGABE_VERLANGTE_ARTEN,
                ergebnis.werte().verlangteArten());
    }

    @Test
    @DisplayName("Eine Kommazahl und ein Text sind keine Zahl")
    void keineZahl() {
        assertTrue(klagtUeber(lies("""
                messstelle:
                  verlangte-arten: 2.5
                """), Messwerte.PFAD_VERLANGTE_ARTEN));
        assertTrue(klagtUeber(lies("""
                messstelle:
                  mindest-strecke: "acht"
                """), Messwerte.PFAD_STRECKE));
    }

    @Test
    @DisplayName("Eine Mindeststrecke von 0 wird beklagt - Stillstehen ist keine Bewegung")
    void streckeNull() {
        var ergebnis = lies("""
                messstelle:
                  mindest-strecke: 0
                """);

        assertTrue(klagtUeber(ergebnis, Messwerte.PFAD_STRECKE),
                ergebnis.klagen().toString());
        assertEquals(Messwerte.VORGABE_STRECKE, ergebnis.werte().mindestStrecke());
    }

    @Test
    @DisplayName("Eine Mindeststrecke ueber der Schranke wird beklagt")
    void streckeZuGross() {
        var ergebnis = lies("""
                messstelle:
                  mindest-strecke: 5000
                """);

        assertTrue(klagtUeber(ergebnis, Messwerte.PFAD_STRECKE),
                ergebnis.klagen().toString());
        assertEquals(Messwerte.VORGABE_STRECKE, ergebnis.werte().mindestStrecke());
    }

    @Test
    @DisplayName("Ein unbekannter Name wird mit seinem Namen beklagt, der Rest gilt")
    void unbekannteArt() {
        var ergebnis = lies("""
                messstelle:
                  verlangte-arten: 1
                  arten:
                    - abbauen
                    - graben
                    - setzen
                """);

        assertTrue(ergebnis.klagen().stream()
                        .anyMatch(klage -> klage.contains("graben")),
                "der Name muss in der Klage stehen, sonst sucht der Betreiber ihn: "
                        + ergebnis.klagen());
        assertEquals(List.of("abbauen", "setzen"), namen(ergebnis),
                "die uebrigen zwei gelten weiter");
    }

    @Test
    @DisplayName("Die Klage nennt alle Arten, die es gibt")
    void klageNenntAlleArten() {
        var ergebnis = lies("""
                messstelle:
                  verlangte-arten: 1
                  arten:
                    - graben
                """);

        String klage = String.join(" ", ergebnis.klagen());
        for (Lebenszeichen art : Lebenszeichen.values()) {
            assertTrue(klage.contains(art.schluessel()),
                    "ohne " + art.schluessel() + " muss der Betreiber raten: " + klage);
        }
    }

    @Test
    @DisplayName("Eine leere Liste wird verworfen - das waere der stumme Ausschalter")
    void leereListe() {
        var ergebnis = lies("""
                messstelle:
                  arten: []
                """);

        assertTrue(klagtUeber(ergebnis, Messwerte.PFAD_ARTEN),
                ergebnis.klagen().toString());
        assertEquals(Messwerte.VORGABE_ARTEN, ergebnis.werte().arten());
        assertTrue(String.join(" ", ergebnis.klagen())
                        .contains(Messwerte.PFAD_VERLANGTE_ARTEN),
                "die Klage muss den Hebel nennen, mit dem man MILDE messen kann - sonst"
                        + " sucht der Betreiber weiter nach dem Ausschalter");
    }

    @Test
    @DisplayName("Eine Liste, in der nur unbekannte Namen stehen, wird auch verworfen")
    void nurUnbekannteNamen() {
        var ergebnis = lies("""
                messstelle:
                  arten:
                    - graben
                    - reden
                """);

        assertEquals(Messwerte.VORGABE_ARTEN, ergebnis.werte().arten(),
                "sonst waere eine Datei mit zwei Tippfehlern eine Messstelle, die nie"
                        + " etwas zaehlt");
    }

    @Test
    @DisplayName("Was keine Liste ist, wird beklagt")
    void keineListe() {
        var ergebnis = lies("""
                messstelle:
                  arten: abbauen
                """);

        assertTrue(klagtUeber(ergebnis, Messwerte.PFAD_ARTEN),
                ergebnis.klagen().toString());
        assertEquals(Messwerte.VORGABE_ARTEN, ergebnis.werte().arten());
    }

    @Test
    @DisplayName("Ein Eintrag, der kein Name ist, wird uebersprungen")
    void eintragOhneNamen() {
        var ergebnis = lies("""
                messstelle:
                  verlangte-arten: 1
                  arten:
                    - abbauen
                    - 7
                """);

        assertFalse(ergebnis.klagen().isEmpty());
        assertEquals(List.of("abbauen"), namen(ergebnis));
    }

    @Test
    @DisplayName("Der Bericht nennt die Arten mit NAMEN und nicht nur ihre Zahl")
    void berichtNenntDieNamen() {
        // Eine Zaehlung "3 Arten" waere auch gruen, wenn jemand benutzen hineinnimmt und
        // kaempfen herausnimmt - und dann gaebe ein Setzmakro zwei Zeichen aus einer
        // Taste. Der Bericht muss darum die Namen tragen.
        var ergebnis = lies(VOLLSTAENDIG);

        String bericht = String.join(" ", ergebnis.bericht());
        assertTrue(bericht.contains("bewegen") && bericht.contains("abbauen")
                        && bericht.contains("setzen"),
                "jeder Name muss im Bericht stehen: " + ergebnis.bericht());
        assertTrue(bericht.contains("3 von 7"),
                "und wie viele von allen es sind: " + ergebnis.bericht());
    }

    @Test
    @DisplayName("Die MITGELIEFERTE config.yml traegt eine brauchbare Messstelle")
    void ausgelieferteDateiIstBrauchbar() {
        // Ohne diese Probe waere jede Zusage ueber die ausgelieferte Datei eine
        // Behauptung: Ein YAML-Einzug an der falschen Stelle beendet den Abschnitt
        // darueber, und der Leser sieht die Zeilen nie. Genau das ist bei
        // verdienst.spawngruende passiert.
        YamlConfiguration datei = ausgelieferteDatei();
        var ergebnis = Messliste.ausKonfiguration(datei);

        assertTrue(ergebnis.klagen().isEmpty(),
                "die mitgelieferte Datei darf nicht klagen: " + ergebnis.klagen());
        assertEquals(Messwerte.VORGABE_VERLANGTE_ARTEN,
                ergebnis.werte().verlangteArten(),
                "die Datei und das Programm muessen dieselbe Zahl nennen");
        assertEquals(Messwerte.VORGABE_STRECKE, ergebnis.werte().mindestStrecke());
        assertEquals(Messwerte.VORGABE_ARTEN, ergebnis.werte().arten(),
                "und dieselbe Liste - sonst messen Datei und Programm verschieden");
        assertTrue(ergebnis.bericht().stream().noneMatch(zeile -> zeile.contains("fehlen")),
                "und keine Zeile darf fehlen: " + ergebnis.bericht());
    }

    @Test
    @DisplayName("benutzen steht in der mitgelieferten Datei NICHT in der Liste")
    void benutzenFehltInDerDatei() {
        // Der WORTLAUT und nicht die Laenge: Die Art ist die halbe Art zum Setzen, und
        // wer sie ab Werk mitzaehlt, macht ein Setzmakro taetig.
        var ergebnis = Messliste.ausKonfiguration(ausgelieferteDatei());

        assertFalse(ergebnis.werte().zaehlt(Lebenszeichen.BENUTZEN));
        assertTrue(ergebnis.werte().zaehlt(Lebenszeichen.BEWEGEN));
    }

    @Test
    @DisplayName("Die Startzeile nennt JEDEN Wert, den sie verspricht")
    void startzeileNenntAlles() {
        // Die Lehre vom 07.11.2026: Die Lesezeile des Bundesmarktes versprach einen
        // Tagesdeckel und nannte seine Zahl nie, weil die bauende Stelle sie nicht las.
        // Eine halbe Zusage liest sich wie eine Angabe und traegt keine.
        String zeile = Messliste.startzeile(Messwerte.VORGABE);

        assertTrue(zeile.contains("2 von 6"),
                "beide Zahlen und nicht nur eine: " + zeile);
        assertTrue(zeile.contains("8 Bloecken"), "die Strecke mit ihrer Zahl: " + zeile);
        for (Lebenszeichen art : Messwerte.VORGABE_ARTEN) {
            assertTrue(zeile.contains(art.schluessel()),
                    art.schluessel() + " fehlt: " + zeile);
        }
        assertFalse(zeile.contains("benutzen"),
                "und was NICHT zaehlt, steht auch nicht da: " + zeile);
    }

    @Test
    @DisplayName("Die Startzeile sagt, dass die Messstelle in nichts eingreift")
    void startzeileSagtDieZusage() {
        // Die tragende Zusage dieses Schrittes, und sie gehoert in die Zeile, die der
        // Betreiber beim Start liest - nicht nur in die config.yml, die er vielleicht
        // nie oeffnet.
        assertTrue(Messliste.startzeile(Messwerte.VORGABE).contains("greift in nichts ein"),
                Messliste.startzeile(Messwerte.VORGABE));
    }

    @Test
    @DisplayName("Die Startzeile folgt den Werten - sie ist nicht abgeschrieben")
    void startzeileFolgtDenWerten() {
        // Die Gegenprobe zu einem festen Satz: Mit anderen Werten muessen andere Zahlen
        // darin stehen. Ohne sie waere eine Zeile mit eingetippten Zahlen auch gruen.
        Messwerte andere = new Messwerte(1, 40,
                new java.util.LinkedHashSet<>(List.of(Lebenszeichen.ABBAUEN,
                        Lebenszeichen.BENUTZEN)));

        String zeile = Messliste.startzeile(andere);

        assertTrue(zeile.contains("1 von 2"), zeile);
        assertTrue(zeile.contains("40 Bloecken"), zeile);
        assertTrue(zeile.contains("benutzen"),
                "jetzt zaehlt es und steht darum da: " + zeile);
        assertFalse(zeile.contains("sprechen"), zeile);
    }

    private static YamlConfiguration ausgelieferteDatei() {
        try (InputStream strom = MesslisteTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertNotNull(strom, "config.yml muss im Jar liegen");
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));
        } catch (java.io.IOException fehler) {
            throw new IllegalStateException("config.yml nicht lesbar", fehler);
        }
    }
}
