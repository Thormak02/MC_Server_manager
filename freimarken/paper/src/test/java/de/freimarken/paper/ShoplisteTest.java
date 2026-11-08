package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Shopregel;
import de.freimarken.kern.Shopwerte;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Handel aus der {@code config.yml} gelesen.
 *
 * <p>Dieselbe Bauart wie {@link MietlisteTest}: Eine kaputte Zeile wird beklagt und die
 * uebrigen gelten weiter, und am Ende wird die MITGELIEFERTE Datei selbst gelesen - sonst
 * waere jede Zusage ueber sie eine Behauptung.
 *
 * <p><b>Die Vorgaben dieses Lesers sind beide 0</b>, und das macht jede Probe besonders
 * anfaellig fuer die Falle aus der Kampagne vom 08.11.2026: Eine Probe, die zufaellig den
 * Vorgabewert einsetzt, prueft nichts - sie waere auch gruen, wenn der ganze Leseaufruf
 * durch die Vorgabe ersetzt waere. Darum steht in keiner dieser Proben eine 0, wo eine
 * Zahl aus der Datei kommen soll.
 */
@DisplayName("Der Handel aus der config.yml")
class ShoplisteTest {

    private static final String VOLLSTAENDIG = """
            handel:
              marktgebuehr-spieler-prozent: 7
              marktgebuehr-bund-prozent: 13
              bundshop-wort: Staat
            """;

    @Test
    @DisplayName("Alle drei Zeilen werden gelesen - und keine ist die Vorgabe")
    void alleDreiZeilen() {
        var ergebnis = lies(VOLLSTAENDIG);

        assertTrue(ergebnis.klagen().isEmpty(), "" + ergebnis.klagen());
        assertEquals(7, ergebnis.werte().spielerProzent());
        assertEquals(13, ergebnis.werte().bundProzent());
        assertEquals("Staat", ergebnis.werte().bundWort());
    }

    @Test
    @DisplayName("Die zwei Gebuehren werden NICHT vertauscht")
    void gebuehrenWerdenNichtVertauscht() {
        // Zwei Zahlen im selben Abschnitt, derselbe Typ, dieselbe Schranke - und beide
        // gehen durch DIESELBE Methode. Genau da ist ein Tausch der zwei Pfade nicht zu
        // sehen, solange eine Probe gleiche Zahlen einsetzt. 7 und 13 sind verschieden,
        // und keine von beiden ist die Vorgabe.
        var ergebnis = lies(VOLLSTAENDIG);

        assertEquals(7, ergebnis.werte().spielerProzent(),
                "7 steht unter " + Shopwerte.PFAD_SPIELER);
        assertEquals(13, ergebnis.werte().bundProzent(),
                "13 steht unter " + Shopwerte.PFAD_BUND);
    }

    @Test
    @DisplayName("Eine Gebuehr ueber der Haelfte wird verworfen, die andere gilt weiter")
    void zuHoheGebuehrWirdVerworfen() {
        var ergebnis = lies(String.join(System.lineSeparator(),
                "handel:",
                "  marktgebuehr-spieler-prozent: 51",
                "  marktgebuehr-bund-prozent: 13"));

        assertTrue(klagtUeber(ergebnis, Shopwerte.PFAD_SPIELER),
                "" + ergebnis.klagen());
        assertEquals(Shopwerte.VORGABE_SPIELER, ergebnis.werte().spielerProzent());
        assertEquals(13, ergebnis.werte().bundProzent(),
                "eine kaputte Zeile kostet nur sich selbst");
    }

    @Test
    @DisplayName("Eine negative Gebuehr wird verworfen")
    void negativeGebuehrWirdVerworfen() {
        var ergebnis = lies(String.join(System.lineSeparator(),
                "handel:",
                "  marktgebuehr-bund-prozent: -1"));

        assertTrue(klagtUeber(ergebnis, Shopwerte.PFAD_BUND));
    }

    @Test
    @DisplayName("Eine Zahl ueber dem int-Bereich wird verworfen und nicht verkuerzt")
    void zahlUeberIntWirdVerworfen() {
        // Dieselbe Falle wie bei der Mietdauer: Aus (int) 4294967296 wird 0, und das
        // waere ZUFAELLIG die Vorgabe - der Betreiber bekaeme keine Klage und haette eine
        // Gebuehr, die er nicht gesetzt hat. Geprueft wird darum der LONG.
        var ergebnis = lies(String.join(System.lineSeparator(),
                "handel:",
                "  marktgebuehr-spieler-prozent: 4294967296"));

        assertTrue(klagtUeber(ergebnis, Shopwerte.PFAD_SPIELER),
                "eine verkuerzte Zahl darf nicht stillschweigend die Vorgabe werden: "
                        + ergebnis.klagen());
    }

    @Test
    @DisplayName("Ein leeres Bundwort ist eine EINSTELLUNG und keine Klage")
    void leeresBundwortIstEineEinstellung() {
        // Ein Server ohne Bundshops braucht kein Wort dafuer. Das steht im Bericht, damit
        // ein Betreiber es nicht fuer einen Fehler haelt - aber es ist keine Klage.
        var ergebnis = lies(String.join(System.lineSeparator(),
                "handel:",
                "  bundshop-wort: \"\""));

        assertTrue(ergebnis.klagen().isEmpty(), "" + ergebnis.klagen());
        assertEquals("", ergebnis.werte().bundWort());
        assertTrue(ergebnis.bericht().stream()
                        .anyMatch(z -> z.startsWith(Shopwerte.PFAD_WORT)
                                && z.contains("nicht anzulegen")),
                "der Bericht muss die Folge nennen: " + ergebnis.bericht());
    }

    @Test
    @DisplayName("Ein zu langes Bundwort wird verworfen - es passt auf keine Schildzeile")
    void zuLangesBundwortWirdVerworfen() {
        // Eine Einstellung, die NIE wirkt, muss es sagen - das ist der Nutzergrundsatz
        // "halb umgezogen muss warnen". Sechzehn Zeichen liessen sich in die erste
        // Schildzeile gar nicht tippen, und der Betreiber wartete ewig auf seinen
        // Bundshop.
        // Die Laenge kommt aus der KONSTANTE und nicht aus einer abgezaehlten Kette:
        // Mein erster Versuch hatte genau 15 Zeichen und ging darum durch - eine
        // Zeichenkette von Hand gezaehlt ist eine Fehlerquelle, die die Probe nicht
        // haben muss.
        String zuLang = "x".repeat(Shopwerte.LAENGSTES_WORT + 1);
        var ergebnis = lies(String.join(System.lineSeparator(),
                "handel:",
                "  bundshop-wort: " + zuLang));

        assertTrue(klagtUeber(ergebnis, Shopwerte.PFAD_WORT), "" + ergebnis.klagen());
        assertEquals(Shopwerte.VORGABE_WORT, ergebnis.werte().bundWort());
    }

    @Test
    @DisplayName("Ein Bundwort, das kein Text ist, wird verworfen")
    void bundwortMussTextSein() {
        // Eine Liste kommt als etwas an, dessen toString auf dem Schild verglichen
        // wuerde - und dann heisst der Bundshop "[Bund, Staat]".
        var ergebnis = lies(String.join(System.lineSeparator(),
                "handel:",
                "  bundshop-wort:",
                "    - Bund",
                "    - Staat"));

        assertTrue(klagtUeber(ergebnis, Shopwerte.PFAD_WORT), "" + ergebnis.klagen());
        assertEquals(Shopwerte.VORGABE_WORT, ergebnis.werte().bundWort());
    }

    @Test
    @DisplayName("Fehlende Zeilen werden gezaehlt genannt")
    void fehlendeZeilenWerdenGenannt() {
        var ergebnis = lies("handel:" + System.lineSeparator()
                + "  marktgebuehr-spieler-prozent: 7");

        assertTrue(ergebnis.bericht().stream()
                        .anyMatch(z -> z.startsWith("2 von 3") && z.contains("Handels")),
                "" + ergebnis.bericht());
    }

    @Test
    @DisplayName("Ohne Abschnitt gelten die Vorgaben, ohne Klage")
    void ohneAbschnittGeltenVorgaben() {
        var ergebnis = Shopliste.ausKonfiguration(null);

        assertEquals(Shopwerte.VORGABE, ergebnis.werte());
        assertTrue(ergebnis.klagen().isEmpty());
    }

    @Test
    @DisplayName("Die Rechenprobe rechnet die Gebuehr an einem Beispiel vor")
    void rechenprobeRechnetVor() {
        // 0 Prozent ist eine Zahl, die niemandem etwas sagt; "von 1.000 bleiben 1.000"
        // schon. Geprueft wird mit 7 und 13, damit die Zeile wirklich RECHNET - mit zwei
        // Nullen waere eine Rechnung, die immer 1.000 ergibt, nicht von einer
        // abgeschriebenen Zahl zu unterscheiden.
        String probe = rechenprobe(lies(VOLLSTAENDIG));

        assertTrue(probe.contains("930"), "1.000 minus 7 Prozent = 930: " + probe);
        assertTrue(probe.contains("870"), "1.000 minus 13 Prozent = 870: " + probe);
        assertTrue(probe.contains("Staat"), "und das Bundwort: " + probe);
    }

    @Test
    @DisplayName("Die Rechenprobe nennt bei leerem Wort, dass es keine Bundshops gibt")
    void rechenprobeNenntFehlendesWort() {
        String probe = rechenprobe(lies(String.join(System.lineSeparator(),
                "handel:",
                "  bundshop-wort: \"\"")));

        assertTrue(probe.contains("nicht anzulegen"), probe);
    }

    @Test
    @DisplayName("Die Startzeile nennt JEDE Zahl, die sie verspricht")
    void startzeileNenntJedeZahl() {
        // Der Befund vom Bundesmarkt: Eine halbe Zusage - "es wird eine Gebuehr erhoben"
        // ohne die Zahl - liest sich wie eine Angabe und traegt keine.
        String zeile = Shopliste.startzeile(new Shopwerte(7, 13, "Staat"));

        assertTrue(zeile.contains("7 Prozent"), zeile);
        assertTrue(zeile.contains("13 Prozent"), zeile);
        assertTrue(zeile.contains("Staat"), zeile);
    }

    @Test
    @DisplayName("Ohne Gebuehr sagt die Startzeile WELCHE Zeilen auf 0 stehen")
    void startzeileOhneGebuehr() {
        // Der ausgelieferte Zustand, also die Zeile, die ein Betreiber am haeufigsten
        // liest. Sie muss die Pfade nennen: Sonst weiss er nicht, wo er die Gebuehr
        // einschaltet.
        String zeile = Shopliste.startzeile(Shopwerte.VORGABE);

        assertTrue(zeile.contains("ohne Marktgebuehr"), zeile);
        assertTrue(zeile.contains(Shopwerte.PFAD_SPIELER), zeile);
        assertTrue(zeile.contains(Shopwerte.PFAD_BUND), zeile);
    }

    @Test
    @DisplayName("Die Schranke des Lesers ist die des Kerns - nicht eine zweite Zahl")
    void schrankeKommtAusDemKern() {
        // Die 50 steht in Shopregel und nirgends sonst. Diese Probe haelt die zwei Seiten
        // zusammen: Was der Kern erlaubt, nimmt der Leser an - und was er abweist, weist
        // der Leser ab. Zwei Zahlen daneben liefen auseinander, und dann klagte der Leser
        // ueber einen Wert, den der Satz annimmt (oder schlimmer: umgekehrt, und der
        // Konstruktor wuerfe beim Start).
        int hoechste = Shopregel.HOECHSTE_GEBUEHR_PROZENT;
        var gerade = lies("handel:" + System.lineSeparator()
                + "  marktgebuehr-spieler-prozent: " + hoechste);
        var darueber = lies("handel:" + System.lineSeparator()
                + "  marktgebuehr-spieler-prozent: " + (hoechste + 1));

        assertTrue(gerade.klagen().isEmpty(), "" + gerade.klagen());
        assertEquals(hoechste, gerade.werte().spielerProzent());
        assertTrue(klagtUeber(darueber, Shopwerte.PFAD_SPIELER));
    }

    @Test
    @DisplayName("Die AUSGELIEFERTE config.yml ist brauchbar und nennt die Planzahlen")
    void ausgelieferteDateiIstBrauchbar() {
        // Ohne diese Probe waere jede Zusage ueber die ausgelieferte Datei eine
        // Behauptung: Ein YAML-Einzug an der falschen Stelle beendet den Abschnitt
        // darueber, und der Leser sieht die Zeilen nie. Genau das faengt sie - und bei
        // diesem Abschnitt noch mehr als bei anderen, denn beide Vorgaben sind 0, und
        // eine nie gelesene Zeile waere nicht von einer gelesenen zu unterscheiden.
        var ergebnis = Shopliste.ausKonfiguration(ausgelieferteDatei());

        assertTrue(ergebnis.klagen().isEmpty(),
                "die mitgelieferte Datei darf nicht klagen: " + ergebnis.klagen());
        assertEquals(Shopwerte.VORGABE, ergebnis.werte(),
                "Datei und Programm muessen dieselben Zahlen nennen");
        assertTrue(ergebnis.bericht().stream().noneMatch(z -> z.contains("fehlen")),
                "und keine Zeile darf fehlen: " + ergebnis.bericht());
    }

    @Test
    @DisplayName("Das Bundwort wird WIRKLICH aus der Datei gelesen")
    void bundwortKommtAusDerDatei() {
        // Die Gegenprobe zur Vorgabenfalle: "Staat" steht nirgends im Programm. Waere der
        // Leseaufruf durch die Vorgabe ersetzt, kaeme "Bund" heraus - und jede Probe, die
        // nur "Bund" prueft, blieb gruen.
        assertEquals("Staat", lies(VOLLSTAENDIG).werte().bundWort());
    }

    // --- Werkzeug -------------------------------------------------------------

    private static Shopliste.Ergebnis lies(String yaml) {
        return Shopliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    private static boolean klagtUeber(Shopliste.Ergebnis ergebnis, String pfad) {
        return ergebnis.klagen().stream().anyMatch(klage -> klage.startsWith(pfad));
    }

    private static String rechenprobe(Shopliste.Ergebnis ergebnis) {
        return ergebnis.bericht().stream()
                .filter(zeile -> zeile.startsWith("Handel:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("keine Rechenprobe im Bericht: "
                        + ergebnis.bericht()));
    }

    private static YamlConfiguration ausgelieferteDatei() {
        try (InputStream strom = ShoplisteTest.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertNotNull(strom, "config.yml muss im Jar liegen");
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));
        } catch (java.io.IOException fehler) {
            throw new IllegalStateException("config.yml nicht lesbar", fehler);
        }
    }
}
