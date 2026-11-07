package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Bonusregel;
import de.freimarken.kern.Bonuswerte;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Tagesbonus aus der {@code config.yml} gelesen.
 *
 * <p>Dieselbe Bauart wie {@link MarktlisteTest} und {@link MesslisteTest}: Eine kaputte
 * Zeile wird beklagt und die uebrigen gelten weiter, und am Ende wird die MITGELIEFERTE
 * Datei selbst gelesen - sonst waere jede Zusage ueber sie eine Behauptung.
 */
@DisplayName("Der Tagesbonus aus der config.yml")
class BonuslisteTest {

    private static final String VOLLSTAENDIG = """
            tagesbonus:
              grundbetrag: 400
              je-serientag: 200
              hoechstbetrag: 1600
              serie-reisst-ab-luecke: 3
              mindest-minuten: 5
            """;

    private static Bonusliste.Ergebnis lies(String yaml) {
        return Bonusliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    private static boolean klagtUeber(Bonusliste.Ergebnis ergebnis, String pfad) {
        return ergebnis.klagen().stream().anyMatch(klage -> klage.startsWith(pfad));
    }

    @Test
    @DisplayName("Eine vollstaendige Datei kommt ganz an")
    void vollstaendigKommtAn() {
        var ergebnis = lies(VOLLSTAENDIG);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        // FUENF verschiedene Zahlen, keine davon die Vorgabe: Mit der Vorgabe waere eine
        // Lesung, die gar nichts liest, auch gruen.
        assertEquals(400L, ergebnis.werte().grundbetrag());
        assertEquals(200L, ergebnis.werte().jeSerientag());
        assertEquals(1_600L, ergebnis.werte().hoechstbetrag());
        assertEquals(3, ergebnis.werte().reisstAbLuecke());
        assertEquals(5L, ergebnis.werte().mindestMinuten());
    }

    @Test
    @DisplayName("Ohne Konfiguration gelten die Vorgaben und es wird nicht geklagt")
    void ohneKonfiguration() {
        var ergebnis = Bonusliste.ausKonfiguration(null);

        assertEquals(Bonuswerte.VORGABE, ergebnis.werte());
        assertTrue(ergebnis.klagen().isEmpty());
    }

    @Test
    @DisplayName("Ein fehlender Abschnitt ist ein BERICHT und keine Klage")
    void fehlenderAbschnitt() {
        var ergebnis = lies("wirtschaft:" + System.lineSeparator() + "  grundpreis: 25000");

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(Bonuswerte.VORGABE, ergebnis.werte());
        assertTrue(ergebnis.bericht().stream().anyMatch(z -> z.contains("5 von 5")),
                "alle fuenf Zeilen fehlen, und das soll dastehen: " + ergebnis.bericht());
    }

    @Test
    @DisplayName("Ein Grundbetrag von 0 wird beklagt - das waere eine Nachricht ueber nichts")
    void grundbetragNull() {
        var ergebnis = lies("""
                tagesbonus:
                  grundbetrag: 0
                """);

        assertTrue(klagtUeber(ergebnis, Bonuswerte.PFAD_GRUNDBETRAG),
                ergebnis.klagen().toString());
        assertEquals(Bonuswerte.VORGABE_GRUNDBETRAG, ergebnis.werte().grundbetrag());
    }

    @Test
    @DisplayName("Ein Hoechstbetrag unter dem Grundbetrag wird beklagt und auf ihn geklemmt")
    void hoechstbetragUnterDemGrund() {
        // Der Fall, den eine Pruefung je Wert durchlaesst: 5.000 ist ein gueltiger
        // Grundbetrag, 800 ein gueltiger Hoechstbetrag. Zusammen gaelte der Grundbetrag
        // nie, und seine Zeile in der Datei waere eine Angabe ohne Wirkung.
        //
        // BEIDE Sprungbestaetigungen muessen dabeistehen, und das ist ein Befund:
        // Gemessen am 07.11.2026 greift der Sprungschutz von Wirtschaftsliste.betrag in
        // BEIDE Richtungen - 2000 auf 800 ist mehr als Faktor 2 nach unten und wird
        // ebenso abgewiesen wie 500 auf 5000 nach oben. Ohne die zwei Zeilen bliebe es
        // bei 500 und 2000, das Paar passte wieder, und diese Probe haette den
        // Geldschutz geprueft statt die Paarregel.
        //
        // Daraus folgt: In EINEM Schritt ist das unmoegliche Paar ueber den Leser gar
        // nicht erreichbar - die Vorgaben 500 und 2000 liegen zu weit auseinander. Ueber
        // zwei Neuladen oder mit Bestaetigung ist es erreichbar, und dann faengt es die
        // Paarregel.
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    bonus-grundbetrag: "500 -> 5000"
                    bonus-hoechstbetrag: "2000 -> 800"
                tagesbonus:
                  grundbetrag: 5000
                  hoechstbetrag: 800
                """);

        assertTrue(klagtUeber(ergebnis, Bonuswerte.PFAD_HOECHSTBETRAG),
                ergebnis.klagen().toString());
        assertEquals(5_000L, ergebnis.werte().grundbetrag(),
                "der Grundbetrag gilt weiter - eine Zahl verwirft nicht die andere Zeile");
        assertEquals(5_000L, ergebnis.werte().hoechstbetrag(),
                "geklemmt auf den Grundbetrag und NICHT auf die Vorgabe 2000 - die waere"
                        + " bei diesem Grundbetrag wieder unmoeglich");
    }

    @Test
    @DisplayName("Der Sprungschutz greift in BEIDE Richtungen - gemessen")
    void sprungschutzInBeideRichtungen() {
        // Der Befund von oben als eigene Probe, damit er nicht nur im Kommentar steht:
        // Eine Halbierung und mehr braucht dieselbe Bestaetigung wie eine Verdopplung.
        // Wer das eines Tages aendert, bekommt hier eine rote Probe und nicht eine
        // andere Probe, die aus einem unerwarteten Grund gruen bleibt.
        var ergebnis = lies("""
                tagesbonus:
                  hoechstbetrag: 800
                """);

        assertTrue(klagtUeber(ergebnis, Bonuswerte.PFAD_HOECHSTBETRAG),
                ergebnis.klagen().toString());
        assertEquals(Bonuswerte.VORGABE_HOECHSTBETRAG, ergebnis.werte().hoechstbetrag());
        // Und mit Bestaetigung geht es durch.
        assertEquals(800L, lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    bonus-hoechstbetrag: "2000 -> 800"
                tagesbonus:
                  hoechstbetrag: 800
                """).werte().hoechstbetrag());
    }

    @Test
    @DisplayName("Eine Luecke von 1 wird beklagt - sie liesse die Serie fuer jeden reissen")
    void lueckeEins() {
        var ergebnis = lies("""
                tagesbonus:
                  serie-reisst-ab-luecke: 1
                """);

        assertTrue(klagtUeber(ergebnis, Bonuswerte.PFAD_LUECKE),
                ergebnis.klagen().toString());
        assertEquals(Bonuswerte.VORGABE_LUECKE, ergebnis.werte().reisstAbLuecke());
        assertTrue(String.join(" ", ergebnis.klagen()).contains("gestern"),
                "die Klage muss sagen, WARUM eine 1 nicht geht: " + ergebnis.klagen());
    }

    @Test
    @DisplayName("Eine zu grosse Luecke wird beklagt")
    void lueckeZuGross() {
        var ergebnis = lies("""
                tagesbonus:
                  serie-reisst-ab-luecke: 1000
                """);

        assertTrue(klagtUeber(ergebnis, Bonuswerte.PFAD_LUECKE),
                ergebnis.klagen().toString());
        assertEquals(Bonuswerte.VORGABE_LUECKE, ergebnis.werte().reisstAbLuecke());
    }

    @Test
    @DisplayName("Eine Luecke, die als int ueberlaeuft, wird nicht zur Vorgabe verkuerzt")
    void lueckeUeberlaeuft() {
        // Die Zahl ist mit Bedacht gewaehlt, und das ist ein Befund aus einer
        // Einspritzung: 4294967298 ist 2^32 + 2, und (int) davon ist GENAU 2 - zufaellig
        // die Vorgabe. Wer den long nicht prueft, sondern den verkuerzten int, laesst sie
        // durch; der Betreiber bekaeme keine Klage und glaubte, seine Zahl gelte.
        //
        // Die naheliegende 2147483650 taugt dafuer NICHT: Ihre Verkuerzung ist
        // -2147483646, also negativ, und die faengt auch eine Pruefung auf dem int. Eine
        // Probe mit dieser Zahl waere gruen geblieben, ohne die Zusage zu halten -
        // gemessen am 07.11.2026.
        var ergebnis = lies("""
                tagesbonus:
                  serie-reisst-ab-luecke: 4294967298
                """);

        assertTrue(klagtUeber(ergebnis, Bonuswerte.PFAD_LUECKE),
                ergebnis.klagen().toString());
        assertEquals(Bonuswerte.VORGABE_LUECKE, ergebnis.werte().reisstAbLuecke());
    }

    @Test
    @DisplayName("Eine zu lange Mindestzeit wird beklagt")
    void mindestzeitZuLang() {
        var ergebnis = lies("""
                tagesbonus:
                  mindest-minuten: 2000
                """);

        assertTrue(klagtUeber(ergebnis, Bonuswerte.PFAD_MINDEST_MINUTEN),
                ergebnis.klagen().toString());
        assertEquals(Bonuswerte.VORGABE_MINDEST_MINUTEN,
                ergebnis.werte().mindestMinuten());
    }

    @Test
    @DisplayName("Eine Kommazahl und ein Text sind keine Zahl")
    void keineZahl() {
        assertTrue(klagtUeber(lies("""
                tagesbonus:
                  grundbetrag: 500.5
                """), Bonuswerte.PFAD_GRUNDBETRAG));
        assertTrue(klagtUeber(lies("""
                tagesbonus:
                  mindest-minuten: "zehn"
                """), Bonuswerte.PFAD_MINDEST_MINUTEN));
    }

    @Test
    @DisplayName("Die Rechenprobe nennt Tag 1, Tag 2 und den Tag des Hoechstbetrags")
    void rechenprobeImBericht() {
        // Die einzige Schranke gegen den Wert, der im erlaubten Bereich liegt und
        // trotzdem falsch ist - dieselbe Antwort wie bei der Preisstaffel. Wer hier
        // "Tag 1 gibt 500 Mark, Tag 2 500 Mark" liest, sieht den vergessenen Zuschlag.
        String bericht = String.join(" ", lies(VOLLSTAENDIG).bericht());

        assertTrue(bericht.contains("Tag 1 gibt 400 Mark"), bericht);
        assertTrue(bericht.contains("Tag 2 600 Mark"), bericht);
        assertTrue(bericht.contains("ab Tag 7 dann 1600 Mark"), bericht);
        // Und der Satz, der sagt, WARUM es keinen eigenen Tagesdeckel braucht. Er steht
        // in DIESER Zeile und nicht in einer zweiten Startmeldung: Der Bericht des Lesers
        // wird beim Start UND bei /freimarken neuladen gedruckt, eine Zeile im Plugin
        // nur beim Start - und zwei fast gleiche Zeilen liest niemand zweimal.
        assertTrue(bericht.contains("hoechstens einmal am Tag"), bericht);
        assertTrue(bericht.contains("Buchungsbuch"), bericht);
    }

    @Test
    @DisplayName("Die Rechenprobe sagt, nach wie vielen verpassten Tagen die Serie reisst")
    void rechenprobeNenntDieLuecke() {
        assertTrue(Bonusliste.staffelText(Bonuswerte.VORGABE).contains("1 Tag verpasst"),
                Bonusliste.staffelText(Bonuswerte.VORGABE));
        assertTrue(Bonusliste.staffelText(Bonuswerte.VORGABE.mitLuecke(4))
                        .contains("3 Tage verpasst"),
                Bonusliste.staffelText(Bonuswerte.VORGABE.mitLuecke(4)));
    }

    @Test
    @DisplayName("Die Rechenprobe sagt, WANN gezahlt wird")
    void rechenprobeNenntDenZeitpunkt() {
        assertTrue(Bonusliste.staffelText(Bonuswerte.VORGABE)
                        .contains("beim Einloggen"),
                Bonusliste.staffelText(Bonuswerte.VORGABE));
        assertTrue(Bonusliste.staffelText(Bonuswerte.VORGABE.mitMindestMinuten(10L))
                        .contains("10 taetigen Minuten"),
                Bonusliste.staffelText(Bonuswerte.VORGABE.mitMindestMinuten(10L)));
    }

    @Test
    @DisplayName("Ohne Zuschlag sagt die Rechenprobe, dass nichts waechst")
    void rechenprobeOhneWachstum() {
        // Sonst stuende dort "ab Tag 1 dann dasselbe" - eine Zeile, die sich selbst
        // widerspricht.
        String text = Bonusliste.staffelText(Bonuswerte.VORGABE.mitJeSerientag(0L));

        assertTrue(text.contains("jeder weitere Tag dasselbe"), text);
        assertTrue(text.contains(Bonuswerte.PFAD_JE_SERIENTAG),
                "und sie nennt den Schluessel, mit dem man es aendert: " + text);
        assertFalse(text.contains("ab Tag"), text);
    }

    @Test
    @DisplayName("Die MITGELIEFERTE config.yml traegt einen brauchbaren Tagesbonus")
    void ausgelieferteDateiIstBrauchbar() {
        // Ohne diese Probe waere jede Zusage ueber die ausgelieferte Datei eine
        // Behauptung: Ein YAML-Einzug an der falschen Stelle beendet den Abschnitt
        // darueber, und der Leser sieht die Zeilen nie.
        var ergebnis = Bonusliste.ausKonfiguration(ausgelieferteDatei());

        assertTrue(ergebnis.klagen().isEmpty(),
                "die mitgelieferte Datei darf nicht klagen: " + ergebnis.klagen());
        assertEquals(Bonuswerte.VORGABE, ergebnis.werte(),
                "Datei und Programm muessen dieselben Zahlen nennen");
        assertTrue(ergebnis.bericht().stream().noneMatch(z -> z.contains("fehlen")),
                "und keine Zeile darf fehlen: " + ergebnis.bericht());
    }

    @Test
    @DisplayName("Der Bonus der ausgelieferten Datei ersetzt keine Arbeit")
    void ausgelieferterBonusErsetztKeineArbeit() {
        // Die Rechenprobe des Entwurfs, gegen die DATEI und nicht gegen das Programm:
        // Plan 8.2 fuehrt den Bonus als kleinste der drei Quellen.
        var werte = Bonusliste.ausKonfiguration(ausgelieferteDatei()).werte();

        assertTrue(Bonusregel.betrag(1, werte) < werte.hoechstbetrag(),
                "der erste Tag muss unter dem Hoechstbetrag liegen, sonst waechst nichts");
        assertTrue(Bonusregel.tagDesHoechstbetrags(werte) >= 7,
                "die Serie soll sich wie ein Ziel anfuehlen - unter einer Woche waere sie"
                        + " sofort erreicht");
        assertTrue(Bonusregel.tagDesHoechstbetrags(werte) <= 60,
                "und erlebbar bleiben - zwei Monate sieht niemand");
    }

    private static YamlConfiguration ausgelieferteDatei() {
        try (InputStream strom = BonuslisteTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertNotNull(strom, "config.yml muss im Jar liegen");
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));
        } catch (java.io.IOException fehler) {
            throw new IllegalStateException("config.yml nicht lesbar", fehler);
        }
    }
}
