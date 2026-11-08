package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Mietregel;
import de.freimarken.kern.Mietwerte;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Ladenmiete aus der {@code config.yml} gelesen.
 *
 * <p>Dieselbe Bauart wie {@link BonuslisteTest} und {@link MarktlisteTest}: Eine kaputte
 * Zeile wird beklagt und die uebrigen gelten weiter, und am Ende wird die MITGELIEFERTE
 * Datei selbst gelesen - sonst waere jede Zusage ueber sie eine Behauptung.
 */
@DisplayName("Die Ladenmiete aus der config.yml")
class MietlisteTest {

    /**
     * Ein Satz, in dem KEINE Zahl die Vorgabe ist - und mit der Bestaetigung fuer den
     * einen Wert, der um mehr als Faktor 2 springt.
     *
     * <p>Der Sprungschutz von {@link Wirtschaftsliste#betrag} trifft jeden Geldwert
     * dieses Lesers. Ohne diese Bestaetigung las die Probe nicht die Datei, sondern die
     * Vorgabe - und waere gruen geblieben, ohne ihre Frage je gestellt zu haben.
     */
    private static final String VOLLSTAENDIG = """
            wirtschaft:
              sprung-bestaetigt:
                laden-lizenz: "50000 -> 20000"
            laeden:
              miete-je-tag: 300
              kaution: 9000
              lizenz: 20000
              mietdauer-tage: 14
            """;

    private static Mietliste.Ergebnis lies(String yaml) {
        return Mietliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    private static boolean klagtUeber(Mietliste.Ergebnis ergebnis, String pfad) {
        return ergebnis.klagen().stream().anyMatch(klage -> klage.startsWith(pfad));
    }

    private static String rechenprobe(Mietliste.Ergebnis ergebnis) {
        return ergebnis.bericht().stream()
                .filter(zeile -> zeile.startsWith("Ladenmiete:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("keine Rechenprobe im Bericht: "
                        + ergebnis.bericht()));
    }

    @Test
    @DisplayName("Eine vollstaendige Datei kommt ganz an")
    void vollstaendigKommtAn() {
        var ergebnis = lies(VOLLSTAENDIG);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        // VIER verschiedene Zahlen, keine davon die Vorgabe: Mit der Vorgabe waere eine
        // Lesung, die gar nichts liest, auch gruen.
        assertEquals(300L, ergebnis.werte().miete());
        assertEquals(9_000L, ergebnis.werte().kaution());
        assertEquals(20_000L, ergebnis.werte().lizenz());
        assertEquals(14, ergebnis.werte().dauerTage());
    }

    @Test
    @DisplayName("Ohne Konfiguration gelten die Vorgaben und es wird nicht geklagt")
    void ohneKonfiguration() {
        var ergebnis = Mietliste.ausKonfiguration(null);

        assertEquals(Mietwerte.VORGABE, ergebnis.werte());
        assertTrue(ergebnis.klagen().isEmpty());
    }

    @Test
    @DisplayName("Ein fehlender Abschnitt ist ein BERICHT und keine Klage")
    void fehlenderAbschnitt() {
        var ergebnis = lies("wirtschaft:" + System.lineSeparator()
                + "  grundpreis: 25000");

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(Mietwerte.VORGABE, ergebnis.werte());
        assertTrue(ergebnis.bericht().stream().anyMatch(z -> z.contains("4 von 4")),
                "der Bericht muss sagen, dass alle vier Zeilen fehlen: "
                        + ergebnis.bericht());
    }

    @Test
    @DisplayName("Die Rechenprobe nennt den VOLLEN Mietzeitraum")
    void rechenprobeNenntDenZeitraum() {
        // Die einzige Schranke gegen den Wert, der im erlaubten Bereich liegt und
        // trotzdem falsch ist. Geprueft wird der TEXT und keine Zahl: Eine Zaehltafel
        // laesst sich hochdrehen, ein Satz muss dastehen. Gesehen am 07.11.2026, dass
        // genau hier eine Berichtszeile mitten im Satz abbrechen kann.
        String satz = rechenprobe(lies(VOLLSTAENDIG));

        assertTrue(satz.contains("300 Mark je Tag"), satz);
        assertTrue(satz.contains("14 Tage je Abschluss"), satz);
        assertTrue(satz.contains("4200 Mark voller Zeitraum"),
                "14 mal 300 - die Zahl, die den Vertipper zeigt: " + satz);
        assertTrue(satz.contains("Kaution 9000 Mark"), satz);
        assertTrue(satz.contains("Lizenz 20000 Mark einmalig"), satz);
        // Der GANZE Satz und nicht ein Stueck daraus: Eine Einspritzung, die nur die
        // Einleitung austauschte ("Nicht genannt: weniger als 14 Tage"), kam am
        // Teilstueck vorbei - gemessen am 08.11.2026.
        assertTrue(satz.contains("Verlaengert wird, sobald weniger als 14 Tage uebrig"
                        + " sind"),
                "die Verlaengerungsschranke steht im Bericht - sie hat keine eigene"
                        + " Zeile in der Datei: " + satz);
        // 27 und nicht 28: zwei Mietdauern MINUS den heutigen Tag. Die Zahl steht
        // hier ausgerechnet und nicht als Formel - sonst stuende in der Probe dasselbe
        // wie im Programm, und beide koennten gemeinsam falsch sein.
        assertTrue(satz.contains("27 Tage voraus liegt das Ende nie"),
                "und wie weit das Ende hoechstens vorausliegt: " + satz);
    }

    @Test
    @DisplayName("Ein einziger Miettag heisst Tag und nicht Tage")
    void einTagHeisstTag() {
        // Die Mehrzahl steht im Programm und nicht in der Sprachdatei, weil diese Zeile
        // an den BETREIBER geht. Dann muss sie aber auch stimmen: "1 Tage je Abschluss"
        // liest sich wie ein Fehler, und wer das sieht, sucht ihn an der falschen Stelle.
        String satz = rechenprobe(lies("""
                laeden:
                  mietdauer-tage: 1
                """));

        // DREI Stellen nennen in diesem Satz eine Anzahl Tage, und bei einer Dauer von
        // 1 sind alle drei die 1: die Mietdauer, die Verlaengerungsschranke und wie weit
        // das Ende hoechstens vorausliegt (2 x 1 - 1). Die Gegenprobe auf "1 Tage" faengt
        // jede einzelne davon - sie hat die dritte am 08.11.2026 wirklich gefangen.
        assertTrue(satz.contains("1 Tag je Abschluss"), satz);
        assertTrue(satz.contains("weniger als 1 Tag uebrig"), satz);
        assertTrue(satz.contains("weiter als 1 Tag voraus"), satz);
        assertFalse(satz.contains("1 Tage"), satz);
    }

    @Test
    @DisplayName("Ohne Kaution und ohne Lizenz sagt die Rechenprobe das")
    void ohneKautionUndLizenz() {
        // Zwei Nullen, zwei eigene Saetze. Ohne sie stuende "Kaution 0 Mark" im
        // Protokoll, und der Betreiber muesste raten, ob das wirkt.
        String satz = rechenprobe(lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    laden-kaution: "5000 -> 0"
                    laden-lizenz: "50000 -> 0"
                laeden:
                  kaution: 0
                  lizenz: 0
                """));

        assertTrue(satz.contains("Ohne Kaution"), satz);
        assertTrue(satz.contains("Ohne Lizenzgebuehr"), satz);
        assertFalse(satz.contains("Kaution 0"), satz);
    }

    @Test
    @DisplayName("Eine Kaution UNTER der Tagesmiete wird beklagt und auf sie geklemmt")
    void kautionUnterMiete() {
        // Das Paar, das jeder Wert FUER SICH erlaubt waere. Geklemmt wird auf die
        // Tagesmiete und NICHT auf die Vorgabe 5000: Wer eine kleine Kaution eingetragen
        // hat, wollte eine kleine - und bei einer Miete von 10.000 waere die Vorgabe
        // wieder unmoeglich.
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    laden-kaution: "5000 -> 200"
                laeden:
                  miete-je-tag: 300
                  kaution: 200
                """);

        assertTrue(klagtUeber(ergebnis, Mietwerte.PFAD_KAUTION),
                ergebnis.klagen().toString());
        assertEquals(300L, ergebnis.werte().miete(),
                "die Miete gilt weiter - eine Zahl verwirft nicht die andere Zeile");
        assertEquals(300L, ergebnis.werte().kaution());
    }

    @Test
    @DisplayName("Die Klemmung nimmt die MIETE und nicht die Vorgabe - der zweite Fall")
    void klemmungNimmtDieMiete() {
        // Die Gegenprobe: Bei einer Miete UEBER der Vorgabekaution waere ein
        // "es bleibt 5000" wieder unmoeglich, und der Leser wuerfe beim Bauen des
        // Satzes. Eine Fassung, die auf die Vorgabe klemmt, ist bei der Probe darueber
        // gruen und hier rot.
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    laden-miete: "500 -> 9000"
                    laden-kaution: "5000 -> 100"
                laeden:
                  miete-je-tag: 9000
                  kaution: 100
                """);

        assertTrue(klagtUeber(ergebnis, Mietwerte.PFAD_KAUTION),
                ergebnis.klagen().toString());
        assertEquals(9_000L, ergebnis.werte().kaution(),
                "geklemmt auf die Miete, nicht auf die Vorgabe 5000");
    }

    @Test
    @DisplayName("Gar keine Kaution geht durch, obwohl sie unter jeder Miete liegt")
    void keineKautionGehtDurch() {
        // Die 0 muss durch: "ohne Kaution" ist eine Entscheidung des Betreibers. Eine
        // Fassung, die nur kaution >= miete prueft, ist bei der Probe darueber gruen und
        // verhinderte hier einen Start mit kaution: 0.
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    laden-kaution: "5000 -> 0"
                laeden:
                  kaution: 0
                """);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(0L, ergebnis.werte().kaution());
    }

    @Test
    @DisplayName("Eine Miete von 0 wird beklagt - sie waere ein unraeumbarer Laden")
    void mieteNull() {
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    laden-miete: "500 -> 0"
                laeden:
                  miete-je-tag: 0
                """);

        assertTrue(klagtUeber(ergebnis, Mietwerte.PFAD_MIETE),
                ergebnis.klagen().toString());
        assertEquals(Mietwerte.VORGABE_MIETE, ergebnis.werte().miete());
        assertTrue(ergebnis.klagen().stream().anyMatch(k -> k.contains("nie beruehrt")),
                "die Klage muss sagen, WARUM eine 0 schlimm ist: " + ergebnis.klagen());
    }

    @Test
    @DisplayName("Eine Lizenz von 0 wird NICHT beklagt")
    void lizenzNull() {
        // Der Unterschied zur Miete ist die Wirkung: Eine Lizenz von 0 heisst nur, dass
        // das Recht genuegt. Ohne diese Probe waere eine Fassung gruen, die beide Nullen
        // gleich behandelt - und dann kaeme ein Betreiber ohne Lizenzgebuehr nicht aus.
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    laden-lizenz: "50000 -> 0"
                laeden:
                  lizenz: 0
                """);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(0L, ergebnis.werte().lizenz());
    }

    @Test
    @DisplayName("Eine Mietdauer von 0 und eine ueber einem Jahr werden beklagt")
    void dauergrenzen() {
        var null0 = lies("laeden:" + System.lineSeparator() + "  mietdauer-tage: 0");
        assertTrue(klagtUeber(null0, Mietwerte.PFAD_DAUER), null0.klagen().toString());
        assertEquals(Mietwerte.VORGABE_DAUER, null0.werte().dauerTage());

        var zuViel = lies("laeden:" + System.lineSeparator() + "  mietdauer-tage: 400");
        assertTrue(klagtUeber(zuViel, Mietwerte.PFAD_DAUER), zuViel.klagen().toString());
        assertEquals(Mietwerte.VORGABE_DAUER, zuViel.werte().dauerTage());

        // Und der erlaubte Rand geht durch.
        assertEquals(Mietregel.HOECHSTE_DAUER, lies("laeden:" + System.lineSeparator()
                + "  mietdauer-tage: " + Mietregel.HOECHSTE_DAUER).werte().dauerTage());
    }

    @Test
    @DisplayName("Eine Dauer, die als int zur VORGABE verkuerzt, wird beklagt")
    void dauerVerkuerztNichtStill() {
        // 4294967326 wird als int genau 30 - und das ist zufaellig die Vorgabe. Eine
        // Fassung, die den verkuerzten int prueft, bekaeme hier keinen Anlass zu klagen,
        // und der Betreiber erfuehre nie, dass seine Zahl nicht gilt.
        var ergebnis = lies("laeden:" + System.lineSeparator()
                + "  mietdauer-tage: 4294967326");

        assertTrue(klagtUeber(ergebnis, Mietwerte.PFAD_DAUER),
                ergebnis.klagen().toString());
        assertEquals(Mietwerte.VORGABE_DAUER, ergebnis.werte().dauerTage());
    }

    @Test
    @DisplayName("Eine Zeile, die keine Zahl ist, wird beklagt und die uebrigen gelten")
    void keineZahl() {
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    laden-lizenz: "50000 -> 20000"
                laeden:
                  miete-je-tag: viel
                  lizenz: 20000
                """);

        assertTrue(klagtUeber(ergebnis, Mietwerte.PFAD_MIETE),
                ergebnis.klagen().toString());
        assertEquals(Mietwerte.VORGABE_MIETE, ergebnis.werte().miete());
        assertEquals(20_000L, ergebnis.werte().lizenz(),
                "eine kaputte Zeile kostet nur sich selbst");
    }

    @Test
    @DisplayName("Die Einheiten im selben Abschnitt gehen diesen Leser nichts an")
    void einheitenStoerenNicht() {
        // Zwei Leser in einem Abschnitt: Ladenliste liest welt und einheiten, dieser die
        // vier Zahlen. Ohne diese Probe koennte einer dem anderen in die Zeilen greifen -
        // und das faellt erst auf, wenn eine echte Halle in der Datei steht.
        var ergebnis = lies("""
                laeden:
                  welt: altfurt
                  miete-je-tag: 300
                  einheiten:
                    halle-1:
                      von: [60, 71, 4]
                      bis: [64, 75, 8]
                      truhe: [62, 71, 6]
                """);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(300L, ergebnis.werte().miete());
        assertEquals(Mietwerte.VORGABE_KAUTION, ergebnis.werte().kaution());
    }

    @Test
    @DisplayName("Der Sprungschutz trifft auch die Mietzahlen - ohne Bestaetigung nichts")
    void sprungschutzTrifftAuchDieMiete() {
        // Der Befund der ersten Probenrunde als eigene Zusage: Ein Geldwert, der um mehr
        // als Faktor 2 springt, braucht die Bestaetigung - sonst gilt die Vorgabe. Ohne
        // diese Probe stuende das nur in Kommentaren, und eine Probenreihe, die ihre
        // Bestaetigungen eines Tages verliert, waere wieder stumm gruen.
        var ohne = lies("laeden:" + System.lineSeparator() + "  lizenz: 20000");
        assertEquals(Mietwerte.VORGABE_LIZENZ, ohne.werte().lizenz(),
                "ohne Bestaetigung bleibt die ausgelieferte Zahl");
        assertTrue(klagtUeber(ohne, Mietwerte.PFAD_LIZENZ), ohne.klagen().toString());

        var mit = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    laden-lizenz: "50000 -> 20000"
                laeden:
                  lizenz: 20000
                """);
        assertEquals(20_000L, mit.werte().lizenz(), "mit Bestaetigung gilt sie");
        assertTrue(mit.klagen().isEmpty(), mit.klagen().toString());
    }

    @Test
    @DisplayName("Die AUSGELIEFERTE config.yml ist brauchbar und nennt die Planzahlen")
    void ausgelieferteDateiIstBrauchbar() {
        // Ohne diese Probe waere jede Zusage ueber die ausgelieferte Datei eine
        // Behauptung: Ein YAML-Einzug an der falschen Stelle beendet den Abschnitt
        // darueber, und der Leser sieht die Zeilen nie.
        var ergebnis = Mietliste.ausKonfiguration(ausgelieferteDatei());

        assertTrue(ergebnis.klagen().isEmpty(),
                "die mitgelieferte Datei darf nicht klagen: " + ergebnis.klagen());
        assertEquals(Mietwerte.VORGABE, ergebnis.werte(),
                "Datei und Programm muessen dieselben Zahlen nennen");
        assertTrue(ergebnis.bericht().stream().noneMatch(z -> z.contains("fehlen")),
                "und keine Zeile darf fehlen: " + ergebnis.bericht());
    }

    @Test
    @DisplayName("Die ausgelieferte Miete ist die staerkste Senke und bleibt tragbar")
    void ausgelieferteMieteIstTragbar() {
        // Die Rechenprobe des Entwurfs, gegen die DATEI und nicht gegen das Programm:
        // Plan 7.1 fuehrt die Ladenmiete als staerkste Geldsenke des Servers, und Plan
        // 7.1 nennt die Kaution "bei ordentlicher Rueckgabe zurueck".
        var werte = Mietliste.ausKonfiguration(ausgelieferteDatei()).werte();

        assertTrue(werte.kaution() >= werte.miete() * 5,
                "die Kaution muss das Weglaufen sichtbar teuer machen - unter fuenf"
                        + " Miettagen ist sie keine Bremse");
        assertTrue(werte.lizenz() > werte.vollerZeitraum(),
                "die Lizenz ist die EINMALIGE Huerde und soll ueber einem ganzen"
                        + " Mietzeitraum liegen - sonst ist sie nur eine zweite Miete");
        assertTrue(werte.dauerTage() >= 7,
                "unter einer Woche muesste ein Spieler dauernd verlaengern");
        assertTrue(werte.dauerTage() <= 90,
                "ueber einem Vierteljahr blockiert ein verlassener Laden zu lange");
    }

    private static YamlConfiguration ausgelieferteDatei() {
        try (InputStream strom = MietlisteTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertNotNull(strom, "config.yml muss im Jar liegen");
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));
        } catch (java.io.IOException fehler) {
            throw new IllegalStateException("config.yml nicht lesbar", fehler);
        }
    }
}
