package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * <p><b>Die Vorgaben dieses Lesers sind alle 0</b>, und das macht jede Probe besonders
 * anfaellig fuer die Falle aus der Kampagne vom 08.11.2026: Eine Probe, die zufaellig den
 * Vorgabewert einsetzt, prueft nichts - sie waere auch gruen, wenn der ganze Leseaufruf
 * durch die Vorgabe ersetzt waere. Darum steht in keiner dieser Proben eine 0, wo eine
 * Zahl aus der Datei kommen soll.
 *
 * <p><b>DREI Gebuehrenzeilen und nicht zwei</b> [08.11.2026]: Auch WANN eine Gebuehr
 * faellt, ist eine Einstellung. Darum stehen hier drei VERSCHIEDENE Zahlen - 7, 13 und
 * 23 -, und keine ist die Vorgabe: Mit gleichen Zahlen waere ein Tausch zweier Pfade
 * nicht zu sehen, und alle drei gehen durch dieselbe Lesemethode.
 */
@DisplayName("Der Handel aus der config.yml")
class ShoplisteTest {

    private static final String VOLLSTAENDIG = """
            handel:
              marktgebuehr-spielershop-kauf-prozent: 7
              marktgebuehr-spielershop-verkauf-prozent: 13
              marktgebuehr-bundshop-verkauf-prozent: 23
              bundshop-wort: Staat
              zeilen-je-seite: 3
            """;

    @Test
    @DisplayName("Alle fuenf Zeilen werden gelesen - und keine ist die Vorgabe")
    void alleFuenfZeilen() {
        var ergebnis = lies(VOLLSTAENDIG);

        assertTrue(ergebnis.klagen().isEmpty(), "" + ergebnis.klagen());
        assertEquals(7, ergebnis.werte().spielershopKauf());
        assertEquals(13, ergebnis.werte().spielershopVerkauf());
        assertEquals(23, ergebnis.werte().bundshopVerkauf());
        assertEquals("Staat", ergebnis.werte().bundWort());
        assertEquals(3, ergebnis.werte().zeilenJeSeite(),
                "3 steht unter " + Shopwerte.PFAD_ZEILEN + " - und die Vorgabe ist "
                        + Shopwerte.VORGABE_ZEILEN);
    }

    @Test
    @DisplayName("Die drei Gebuehren werden NICHT vertauscht")
    void gebuehrenWerdenNichtVertauscht() {
        // Drei Zahlen im selben Abschnitt, derselbe Typ, dieselbe Schranke - und alle
        // drei gehen durch DIESELBE Methode. Genau da ist ein Tausch zweier Pfade nicht
        // zu sehen, solange eine Probe gleiche Zahlen einsetzt. 7, 13 und 23 sind
        // verschieden, und keine von ihnen ist die Vorgabe.
        var ergebnis = lies(VOLLSTAENDIG);

        assertEquals(7, ergebnis.werte().spielershopKauf(),
                "7 steht unter " + Shopwerte.PFAD_SPIELER_KAUF);
        assertEquals(13, ergebnis.werte().spielershopVerkauf(),
                "13 steht unter " + Shopwerte.PFAD_SPIELER_VERKAUF);
        assertEquals(23, ergebnis.werte().bundshopVerkauf(),
                "23 steht unter " + Shopwerte.PFAD_BUND_VERKAUF);
    }

    @Test
    @DisplayName("Eine Gebuehr ueber der Haelfte wird verworfen, die anderen gelten weiter")
    void zuHoheGebuehrWirdVerworfen() {
        var ergebnis = lies(String.join(System.lineSeparator(),
                "handel:",
                "  marktgebuehr-spielershop-kauf-prozent: 51",
                "  marktgebuehr-spielershop-verkauf-prozent: 13",
                "  marktgebuehr-bundshop-verkauf-prozent: 23"));

        assertTrue(klagtUeber(ergebnis, Shopwerte.PFAD_SPIELER_KAUF),
                "" + ergebnis.klagen());
        assertEquals(Shopwerte.VORGABE_SPIELER_KAUF,
                ergebnis.werte().spielershopKauf());
        assertEquals(13, ergebnis.werte().spielershopVerkauf(),
                "eine kaputte Zeile kostet nur sich selbst");
        assertEquals(23, ergebnis.werte().bundshopVerkauf());
    }

    @Test
    @DisplayName("Eine negative Gebuehr wird verworfen")
    void negativeGebuehrWirdVerworfen() {
        var ergebnis = lies(String.join(System.lineSeparator(),
                "handel:",
                "  marktgebuehr-bundshop-verkauf-prozent: -1"));

        assertTrue(klagtUeber(ergebnis, Shopwerte.PFAD_BUND_VERKAUF));
    }

    @Test
    @DisplayName("Eine Zahl ueber dem int-Bereich wird verworfen und nicht verkuerzt")
    void zahlUeberIntWirdVerworfen() {
        // Dieselbe Falle wie bei der Mietdauer: Aus (int) 4294967296 wird 0, und das
        // waere ZUFAELLIG die Vorgabe - der Betreiber bekaeme keine Klage und haette eine
        // Gebuehr, die er nicht gesetzt hat. Geprueft wird darum der LONG.
        var ergebnis = lies(String.join(System.lineSeparator(),
                "handel:",
                "  marktgebuehr-spielershop-kauf-prozent: 4294967296"));

        assertTrue(klagtUeber(ergebnis, Shopwerte.PFAD_SPIELER_KAUF),
                "eine verkuerzte Zahl darf nicht stillschweigend die Vorgabe werden: "
                        + ergebnis.klagen());
    }

    @Test
    @DisplayName("Die ALTEN Zeilen werden beklagt und nicht uebergangen")
    void alteZeilenWerdenBeklagt() {
        // Bis zum 08.11.2026 hiessen die Zeilen marktgebuehr-spieler-prozent und
        // marktgebuehr-bund-prozent. Wer sie stehen laesst, hat eine Zahl in der Datei,
        // die niemand mehr liest - und das ist die schlimmste Art Einstellung: Der
        // Betreiber stellt sie ein, wartet auf ihre Wirkung und sucht den Fehler
        // ueberall sonst.
        //
        // Eine KLAGE und kein Bericht: Die Folge ist dieselbe wie bei einer verworfenen
        // Zahl - der Server tut etwas anderes, als in der Datei steht.
        var ergebnis = lies(String.join(System.lineSeparator(),
                "handel:",
                "  marktgebuehr-spieler-prozent: 7",
                "  marktgebuehr-bund-prozent: 13"));

        assertTrue(klagtUeber(ergebnis, "handel.marktgebuehr-spieler-prozent"),
                "" + ergebnis.klagen());
        assertTrue(klagtUeber(ergebnis, "handel.marktgebuehr-bund-prozent"),
                "" + ergebnis.klagen());
        // Und die Klage sagt, WOHIN die Zahl jetzt gehoert - sonst sucht er drei neue
        // Namen, die er nicht kennt.
        assertTrue(ergebnis.klagen().stream()
                        .anyMatch(k -> k.contains(Shopwerte.PFAD_SPIELER_KAUF)
                                && k.contains(Shopwerte.PFAD_SPIELER_VERKAUF)
                                && k.contains(Shopwerte.PFAD_BUND_VERKAUF)),
                "die Klage muss die drei neuen Zeilen nennen: " + ergebnis.klagen());
        // Die Zahlen von damals gelten NICHT weiter: Sie standen fuer eine Lage, die es
        // so nicht mehr gibt.
        assertEquals(Shopwerte.VORGABE, ergebnis.werte());
    }

    @Test
    @DisplayName("Die AUSGELIEFERTE Datei nennt keine alte Zeile mehr")
    void ausgelieferteDateiOhneAlteZeilen() {
        // Die Gegenprobe zur Probe darueber: Haette die mitgelieferte config.yml eine
        // der alten Zeilen behalten, klagte das Plugin bei JEDEM Start ueber sich
        // selbst - und ein Betreiber lernte, Klagen zu uebersehen.
        var ergebnis = Shopliste.ausKonfiguration(ausgelieferteDatei());

        assertTrue(ergebnis.klagen().isEmpty(), "" + ergebnis.klagen());
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
                + "  marktgebuehr-spielershop-kauf-prozent: 7");

        assertTrue(ergebnis.bericht().stream()
                        .anyMatch(z -> z.startsWith("4 von 5") && z.contains("Handels")),
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
    @DisplayName("Die Rechenprobe rechnet jede der drei Gebuehren an einem Beispiel vor")
    void rechenprobeRechnetVor() {
        // 0 Prozent ist eine Zahl, die niemandem etwas sagt; "von 1.000 bleiben 1.000"
        // schon. Geprueft wird mit 7, 13 und 23, damit die Zeile wirklich RECHNET - mit
        // Nullen waere eine Rechnung, die immer 1.000 ergibt, nicht von einer
        // abgeschriebenen Zahl zu unterscheiden.
        String probe = rechenprobe(lies(VOLLSTAENDIG));

        assertTrue(probe.contains("930"), "1.000 minus 7 Prozent = 930: " + probe);
        assertTrue(probe.contains("870"), "1.000 minus 13 Prozent = 870: " + probe);
        assertTrue(probe.contains("770"), "1.000 minus 23 Prozent = 770: " + probe);
        assertTrue(probe.contains("Staat"), "und das Bundwort: " + probe);
    }

    @Test
    @DisplayName("Die Rechenprobe sagt, WER die Gebuehr zahlt")
    void rechenprobeSagtWerZahlt() {
        // Das ist der Unterschied der drei Lagen, und eine Zeile, die nur Prozente
        // nennt, liest sich wie eine Zahl und nicht wie eine Entscheidung: Beim Kauf
        // zahlt der BESITZER, beim Verkauf der KUNDE.
        String probe = rechenprobe(lies(VOLLSTAENDIG));

        assertTrue(probe.contains("Besitzer"), probe);
        assertTrue(probe.contains("Kunde"), probe);
        // Und sie nennt die vierte Lage, die KEINEN Schluessel hat - sonst sucht ein
        // Betreiber ewig nach der Zeile fuer den Kauf am Bundshop.
        assertTrue(probe.contains("Bundshop faellt keine Gebuehr"), probe);
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
        // ohne die Zahl - liest sich wie eine Angabe und traegt keine. Mit drei Saetzen
        // gilt das dreifach.
        String zeile = Shopliste.startzeile(new Shopwerte(7, 13, 23, "Staat", 7));

        assertTrue(zeile.contains("7"), zeile);
        assertTrue(zeile.contains("13"), zeile);
        assertTrue(zeile.contains("23"), zeile);
        assertTrue(zeile.contains("Staat"), zeile);
    }

    @Test
    @DisplayName("Ohne Gebuehr sagt die Startzeile WELCHE Zeilen auf 0 stehen")
    void startzeileOhneGebuehr() {
        // Der ausgelieferte Zustand, also die Zeile, die ein Betreiber am haeufigsten
        // liest. Sie muss die Pfade nennen: Sonst weiss er nicht, wo er die Gebuehr
        // einschaltet - und bei drei Zeilen erst recht.
        String zeile = Shopliste.startzeile(Shopwerte.VORGABE);

        assertTrue(zeile.contains("ohne Marktgebuehr"), zeile);
        assertTrue(zeile.contains(Shopwerte.PFAD_SPIELER_KAUF), zeile);
        assertTrue(zeile.contains(Shopwerte.PFAD_SPIELER_VERKAUF), zeile);
        assertTrue(zeile.contains(Shopwerte.PFAD_BUND_VERKAUF), zeile);
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
                + "  marktgebuehr-spielershop-kauf-prozent: " + hoechste);
        var darueber = lies("handel:" + System.lineSeparator()
                + "  marktgebuehr-spielershop-kauf-prozent: " + (hoechste + 1));

        assertTrue(gerade.klagen().isEmpty(), "" + gerade.klagen());
        assertEquals(hoechste, gerade.werte().spielershopKauf());
        assertTrue(klagtUeber(darueber, Shopwerte.PFAD_SPIELER_KAUF));
    }

    @Test
    @DisplayName("Die AUSGELIEFERTE config.yml ist brauchbar und nennt die Planzahlen")
    void ausgelieferteDateiIstBrauchbar() {
        // Ohne diese Probe waere jede Zusage ueber die ausgelieferte Datei eine
        // Behauptung: Ein YAML-Einzug an der falschen Stelle beendet den Abschnitt
        // darueber, und der Leser sieht die Zeilen nie. Genau das faengt sie - und bei
        // diesem Abschnitt noch mehr als bei anderen, denn alle Vorgaben sind 0, und
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
    @DisplayName("Eine 0 Zeilen je Seite wird verworfen und NICHT als Abschaltung gelesen")
    void nullZeilenWirdVerworfen() {
        // Eine 0 saehe wie eine Abschaltung aus, waere aber eine Liste, die nie etwas
        // zeigt - fuer den Spieler nicht von einem kaputten Server zu unterscheiden.
        // Darum Klage UND Vorgabe, nicht das eine ohne das andere.
        var ergebnis = lies("handel:" + System.lineSeparator()
                + "  zeilen-je-seite: 0");

        assertEquals(Shopwerte.VORGABE_ZEILEN, ergebnis.werte().zeilenJeSeite(),
                "es bleibt bei der Vorgabe");
        assertTrue(ergebnis.klagen().stream()
                        .anyMatch(z -> z.contains(Shopwerte.PFAD_ZEILEN)),
                "und der Betreiber liest, dass seine Zahl verworfen wurde: "
                        + ergebnis.klagen());
    }

    @Test
    @DisplayName("Die Grenze selbst gilt, eine darueber nicht")
    void grenzeDerZeilenGilt() {
        // Die Grenze SELBST muss durchgehen: Wer sie im Leser um eins verschiebt, faellt
        // nur hier auf. Eine Probe, die nur 101 prueft, bliebe bei <= statt < gruen.
        var genau = lies("handel:" + System.lineSeparator()
                + "  zeilen-je-seite: " + Shopwerte.HOECHSTE_ZEILEN);
        var darueber = lies("handel:" + System.lineSeparator()
                + "  zeilen-je-seite: " + (Shopwerte.HOECHSTE_ZEILEN + 1));

        assertEquals(Shopwerte.HOECHSTE_ZEILEN, genau.werte().zeilenJeSeite(),
                "die Grenze selbst ist erlaubt: " + genau.klagen());
        assertTrue(genau.klagen().isEmpty(), "" + genau.klagen());
        assertEquals(Shopwerte.VORGABE_ZEILEN, darueber.werte().zeilenJeSeite(),
                "eine darueber nicht");
        assertFalse(darueber.klagen().isEmpty(), "und das wird gesagt");
    }

    @Test
    @DisplayName("Der Startbericht nennt die Seitengroesse")
    void startzeileNenntDieSeitengroesse() {
        // Eine Einstellung, die der Startbericht nicht nennt, wirkt fuer den Betreiber
        // wie keine: Er stellt sie ein, liest keine Bestaetigung und sucht den Fehler.
        String zeile = Shopliste.startzeile(new Shopwerte(7, 13, 23, "Staat", 3));

        assertTrue(zeile.contains("3"), zeile);
        assertTrue(zeile.contains("/shop liste"), zeile);
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

    // --- Die Startzeile ueber die Ankaufstaende des Bundes [10.11.2026] -------

    @Test
    @DisplayName("Ohne Stand UND mit Sortiment ist die Startzeile eine WARNUNG")
    void ohneStandMitSortiment() {
        // DIE EIGENTLICHE ARBEIT DIESER ZEILE. Seit dem 10.11.2026 ist ein Bundschild
        // mit S-Preis der einzige Weg, an den Bund zu verkaufen - /markt verkaufen gibt
        // es nicht mehr. Ein Betreiber, der sein Sortiment eingestellt und kein Schild
        // gestellt hat, hat also einen Markt, der nichts kauft, und nirgends fehlt
        // etwas: Die Tafel ist da, die Zahlen stimmen, der Befehl zeigt sie an.
        String zeile = Shopliste.bundzeile(0, markt(5_000L, 7));

        assertTrue(zeile.contains("KEINER"), zeile);
        assertTrue(zeile.contains("7 Materialien"), zeile);
        assertTrue(zeile.contains("/markt verkaufen"),
                "die Zeile muss sagen, dass der alte Weg weg ist: " + zeile);
    }

    @Test
    @DisplayName("Ohne Stand und ohne Sortiment ist es eine Einstellung, keine Warnung")
    void ohneStandOhneSortiment() {
        // Wer die Liste leer laesst, schaltet den Ankauf ab - eine zulaessige
        // Einstellung, die Marktwerte ausdruecklich erlaubt. Eine Warnung waere hier
        // falsch, und zwei gleich klingende Zeilen fuer zwei verschiedene Lagen waeren
        // schlimmer als keine.
        String zeile = Shopliste.bundzeile(0, markt(5_000L, 0));

        assertFalse(zeile.contains("KEINER"), zeile);
        assertTrue(zeile.contains("ist leer"), zeile);
        assertTrue(zeile.contains("zulaessige Einstellung"), zeile);
    }

    @Test
    @DisplayName("Mit Staenden nennt die Zeile ihre Zahl, den Deckel und die Tafel")
    void mitStaenden() {
        // Die DREI Zahlen, auf die es ankommt: wie viele Staende stehen, wie viel ein
        // Spieler am Tag bekommt, und woher die Preise kommen. Die letzte ist keine
        // Zahl, sondern der Pfad - ein Betreiber, der ein Schild mit einer anderen Zahl
        // sieht, soll wissen, wo die geltende steht.
        String zeile = Shopliste.bundzeile(3, markt(1_234L, 7));

        assertTrue(zeile.contains("3 am Schild"), zeile);
        assertTrue(zeile.contains("1.234") || zeile.contains("1234"), zeile);
        assertTrue(zeile.contains("bundesmarkt.ankauf"), zeile);
    }

    /** Marktzahlen mit so vielen Materialien im Sortiment. */
    private static de.freimarken.kern.Marktwerte markt(long deckel, int materialien) {
        java.util.List<de.freimarken.kern.Ankaufliste.Ankauf> ankaeufe =
                new java.util.ArrayList<>();
        for (int i = 0; i < materialien; i++) {
            ankaeufe.add(new de.freimarken.kern.Ankaufliste.Ankauf("ware_" + i, 20L));
        }
        return de.freimarken.kern.Marktwerte.VORGABE.mitDeckel(deckel)
                .mitAnkauf(de.freimarken.kern.Ankaufliste.ausAnkaeufen(ankaeufe));
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
