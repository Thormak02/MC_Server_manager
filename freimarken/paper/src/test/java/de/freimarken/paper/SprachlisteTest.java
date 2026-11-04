package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Die Pruefmaschine - je eine Probe je Fehlerfall, und jede kann rot werden.
 *
 * <p>Die Unterscheidung, um die es in jeder dieser Proben geht, steht in einem Satz: Ein
 * Mangel, den der Betreiber durch TIPPEN verursacht hat, ist eine KLAGE und blockiert den
 * ganzen Tausch. Ein Unterschied, den ein UPDATE verursacht hat, ist ein BERICHT und faellt
 * JE SCHLUESSEL zurueck. Ohne diese Regel macht ein gewoehnliches Update aus der Datei des
 * Betreibers einen Totalausfall, bei dem er nichts angefasst hat.
 *
 * <p>Fuer die Faelle, deren Schluessel erst ein spaeterer Bauschritt anlegt, laeuft die
 * Probe ueber eine EIGENE Schluesselliste
 * ({@link Sprachliste#ausQuelle(String, Sprachbuch, List)}). Sonst blieben die
 * Fehlerklassen "Umbruchmarke im Handbalken", "halbe Mehrzahl" und "fremder Name, den ein
 * ANDERER Satz kennt" bis zum letzten Bauschritt ungeprueft - und eine gruene Probe, die
 * nichts geprueft hat, ist schlimmer als eine rote.
 */
class SprachlisteTest {

    private static Sprachbuch vorlage() {
        return Sprachprobe.ausDemJar();
    }

    private static String quelle(String... zeilen) {
        return String.join("\n", zeilen) + "\n";
    }

    @Test
    @DisplayName("12 - ein fehlender Schluessel ist ein Bericht und nennt die Zahl")
    void fehlenderSchluesselIstBericht() {
        Sprachbuch vorlage = vorlage();
        var gelesen = Sprachliste.ausQuelle(quelle(
                "mark:",
                "  verlauf:",
                "    grund:",
                "      eroeffnung: \"Anfangsguthaben\""), vorlage);

        assertEquals(List.of(), gelesen.klagen(), "ein fehlender Satz ist der NORMALFALL -"
                + " sprache.yml wird absichtlich leer ausgeliefert");
        assertNotNull(gelesen.buch());
        assertTrue(gelesen.bericht().get(0).contains("1 von "
                        + vorlage.saetze().size() + " Saetzen"),
                "der Bericht muss die Zahl nennen, bekommen: " + gelesen.bericht());
        assertEquals("Anfangsguthaben",
                gelesen.buch().wortlaut(MarkSatz.VERLAUF_GRUND_EROEFFNUNG));
        // Jeder andere Satz kommt weiter aus dem Plugin - darum kann keiner verstummen.
        assertEquals(vorlage.wortlaut(MarkSatz.VERLAUF_ZEILE),
                gelesen.buch().wortlaut(MarkSatz.VERLAUF_ZEILE));
    }

    @Test
    @DisplayName("13 - ein ueberzaehliger Schluessel wird genannt, mit aehnlichstem Pfad")
    void ueberzaehligerWirdGenanntMitAehnlichstem() {
        var gelesen = Sprachliste.ausQuelle(quelle(
                "mark:",
                "  verlauf:",
                "    zeil: \"<gray>vertippt</gray>\""), vorlage());

        // KEINE Klage, und das ist gegen die scharfe Fassung bewusst entschieden: Laesst
        // ein naechstes Jar einen Satz weg, den der Betreiber vor Monaten geaendert hat,
        // wuerde sonst keine seiner Zeilen mehr uebernommen - ausgeloest vom Update.
        assertEquals(List.of(), gelesen.klagen());
        assertTrue(gelesen.bericht().stream().anyMatch(zeile
                        -> zeile.contains("Zeile 3")
                        && zeile.contains("mark.verlauf.zeil")
                        && zeile.contains("Meintest du mark.verlauf.zeile?")),
                "der aehnlichste Pfad fehlt, bekommen: " + gelesen.bericht());
    }

    @Test
    @DisplayName("14 - ein doppeltes Blatt ist eine Klage mit BEIDEN Zeilennummern")
    void dubletteImBlattIstKlageMitBeidenZeilennummern() {
        var gelesen = Sprachliste.ausQuelle(quelle(
                "mark:",
                "  verlauf:",
                "    zeile: \"<gray>{zeit} erste</gray>\"",
                "    zeile: \"<gray>{zeit} zweite</gray>\""), vorlage());

        assertNull(gelesen.buch(), "ein Tippfehler des Betreibers tauscht gar nichts");
        assertTrue(gelesen.klagen().stream().anyMatch(klage
                        -> klage.contains("mark.verlauf.zeile steht zweimal")
                        && klage.contains("Zeile 3") && klage.contains("Zeile 4")),
                "beide Zeilennummern fehlen, bekommen: " + gelesen.klagen());
    }

    @Test
    @DisplayName("14b - ein doppelter ELTERNKNOTEN ist eine Klage mit beiden Zeilen")
    void dubletteImElternknotenIstKlageMitBeidenZeilennummern() {
        // Der Fall, den ein Leser ueber ConfigurationSection verloren haette, und er ist
        // gemessen: SnakeYAML 2.2 behaelt nur den ZWEITEN Block, kein Blattpfad ist
        // doppelt, und die Pfade des ersten gehen als "fehlt" durch - also als Normalfall.
        // Der Betreiber haette gelesen "1 von 18 Saetzen kommt aus deiner sprache.yml",
        // obwohl zwei darin stehen, und nichts haette ihm den Grund gesagt.
        var gelesen = Sprachliste.ausQuelle(quelle(
                "mark:",
                "  verlauf:",
                "    zeile: \"<gray>{zeit} erste</gray>\"",
                "mark:",
                "  verlauf:",
                "    grund:",
                "      eroeffnung: \"Anfangsguthaben\""), vorlage());

        assertNull(gelesen.buch());
        assertTrue(gelesen.klagen().stream().anyMatch(klage
                        -> klage.contains("mark steht zweimal")
                        && klage.contains("Zeile 1") && klage.contains("Zeile 4")
                        && klage.contains("vom ersten Block gilt NICHTS")),
                "bekommen: " + gelesen.klagen());
    }

    @Test
    @DisplayName("15 - eine Datei, die nicht UTF-8 ist, ist eine Klage und kein Fragezeichen")
    void nichtUtf8IstKlage(@TempDir Path ordner) throws IOException {
        // Windows-1252 schreibt einen Umlaut als EIN Byte - allein ist das kein gueltiges
        // UTF-8. Ohne Wurf stuende beim Spieler ein Ersatzzeichen, und niemand wuesste,
        // woher es kommt.
        ByteArrayOutputStream rohe = new ByteArrayOutputStream();
        rohe.writeBytes("mark:\n  verlauf:\n    grund:\n      eroeffnung: \"Gr"
                .getBytes(StandardCharsets.US_ASCII));
        rohe.write(0xfc);
        rohe.writeBytes("ndung\"\n".getBytes(StandardCharsets.US_ASCII));
        var gelesen = Sprachliste.ausDatei(
                Sprachprobe.mitBytes(ordner, rohe.toByteArray()), vorlage());

        assertNull(gelesen.buch());
        assertTrue(gelesen.klagen().stream().anyMatch(klage
                        -> klage.contains("nicht UTF-8") && klage.contains("Zeile 4")),
                "bekommen: " + gelesen.klagen());
    }

    @Test
    @DisplayName("16 - ein leerer Wert ist eine Klage")
    void leererWertIstKlage() {
        var leer = Sprachliste.ausQuelle(quelle(
                "mark:",
                "  verlauf:",
                "    grund:",
                "      eroeffnung: \"\""), vorlage());
        assertNull(leer.buch());
        assertTrue(leer.klagen().stream().anyMatch(klage
                        -> klage.contains("mark.verlauf.grund.eroeffnung")
                        && klage.contains("ist leer")),
                "bekommen: " + leer.klagen());

        // In die andere Richtung: ein Wert, der nur so aussieht, geht durch.
        var voll = Sprachliste.ausQuelle(quelle(
                "mark:",
                "  verlauf:",
                "    grund:",
                "      eroeffnung: \" Startguthaben \""), vorlage());
        assertEquals(List.of(), voll.klagen());
    }

    @Test
    @DisplayName("17 - ein kaputtes Tag ist eine Klage mit Zeile UND Spalte")
    void kaputtesTagIstKlageMitZeileUndSpalte() {
        var unbekannt = Sprachliste.ausQuelle(quelle(
                "mark:",
                "  verlauf:",
                "    zeile: \"<gol>{zeit} {betrag} {grund} {stand}</gol>\""), vorlage());
        assertNull(unbekannt.buch());
        assertTrue(unbekannt.klagen().stream().anyMatch(klage
                        -> klage.contains("Zeile 3") && klage.contains("Spalte 0")
                        && klage.contains("<gol>")
                        && klage.contains("Meintest du <gold>?")),
                "bekommen: " + unbekannt.klagen());

        var offen = Sprachliste.ausQuelle(quelle(
                "mark:",
                "  verlauf:",
                "    zeile: \"<gray>{zeit} {betrag} {grund} {stand}\""), vorlage());
        assertNull(offen.buch());
        assertTrue(offen.klagen().stream().anyMatch(klage
                        -> klage.contains("Zeile 3") && klage.contains("Spalte")
                        && klage.contains("geschlossen")),
                "bekommen: " + offen.klagen());
    }

    @Test
    @DisplayName("17b - jedes Tag, das der Leser VERSTEHT, wird auch angenommen")
    void jederTagDenDerLeserVerstehtWirdAngenommen() {
        // Zwei Tore mit zwei Wortschaetzen sind ein Fehler fuer sich: Eine Handliste mit
        // zehn Farben haette <dark_blue> abgewiesen - eine Farbe, die das Plugin kennt -,
        // und der Betreiber haette dabei alle seine anderen Aenderungen verloren.
        for (String tag : List.of("dark_blue", "light_purple", "#ffaa00", "b", "st")) {
            assertTrue(Sprache.VORRAT.has(tag), "der Vorrat kennt <" + tag + "> nicht");
            var gelesen = Sprachliste.ausQuelle(quelle(
                    "mark:",
                    "  verlauf:",
                    "    zeile: \"<" + tag + ">{zeit} {betrag} {grund} {stand}</"
                            + tag + ">\""), vorlage());
            assertEquals(List.of(), gelesen.klagen(), "<" + tag + "> wurde abgewiesen");
        }
        for (String tag : List.of("click:run_command:'/op du'", "hover", "br", "reset",
                "goldd")) {
            var gelesen = Sprachliste.ausQuelle(quelle(
                    "mark:",
                    "  verlauf:",
                    "    zeile: \"<" + tag + ">{zeit} {betrag} {grund} {stand}\""),
                    vorlage());
            assertFalse(gelesen.klagen().isEmpty(), "<" + tag + "> kam durch - mit dem"
                    + " vollen Vorrat erzeugt run_command einen Satz, der im NAMEN DES"
                    + " SPIELERS laeuft");
        }
    }

    @Test
    @DisplayName("18 - ein fremder Name ist Rueckfall JE SCHLUESSEL, nicht fuer alles")
    void fremderNameNirgendsBekanntIstRueckfallJeSchluessel() {
        Sprachbuch vorlage = vorlage();
        var gelesen = Sprachliste.ausQuelle(quelle(
                "mark:",
                "  verlauf:",
                "    zeile: \"<gray>{zeit} {quatsch}</gray>\"",
                "    grund:",
                "      eroeffnung: \"Anfangsguthaben\""), vorlage);

        assertEquals(List.of(), gelesen.klagen(), "alle Saetze blockiert - damit entwertet"
                + " ein Update, bei dem er nichts angefasst hat, seine ganze Datei");
        assertNotNull(gelesen.buch());
        assertEquals(vorlage.wortlaut(MarkSatz.VERLAUF_ZEILE),
                gelesen.buch().wortlaut(MarkSatz.VERLAUF_ZEILE),
                "fuer DIESEN Satz gilt wieder der ausgelieferte");
        // Und die anderen wurden uebernommen - das ist die zweite Haelfte der Zusage.
        assertEquals("Anfangsguthaben",
                gelesen.buch().wortlaut(MarkSatz.VERLAUF_GRUND_EROEFFNUNG));
        assertTrue(gelesen.buch().ausDerDatei()
                .contains(MarkSatz.VERLAUF_GRUND_EROEFFNUNG.pfad()));
        assertFalse(gelesen.buch().ausDerDatei().contains(MarkSatz.VERLAUF_ZEILE.pfad()));
        assertTrue(gelesen.bericht().stream().anyMatch(zeile
                        -> zeile.contains("{quatsch}")
                        && zeile.contains("fuer diesen EINEN Satz")),
                "bekommen: " + gelesen.bericht());
    }

    @Test
    @DisplayName("18b - ein fremder Name AUS EINEM ANDEREN Satz ist ebenfalls Rueckfall")
    void fremderNameAusEinemAnderenSatzIstEbenfallsRueckfall() {
        // Der Update-Fall: Gibt ein naechstes Jar dem einen Satz sein {max} nicht mehr,
        // waehrend {max} im anderen weiterlebt, waere die Zeile des Betreibers ploetzlich
        // ein Tippfehler - und keiner seiner Saetze gaelte noch, ausgeloest von einem
        // Update, bei dem er nichts angefasst hat. Der Unterscheider "Tippfehler oder Rest
        // aus der vorigen Fassung" ist ausserdem nicht herstellbar.
        Satz mitMax = Sprachprobe.Probesatz.von("probe.mit-max", Satz.Art.SATZ, "max");
        Satz ohneMax = Sprachprobe.Probesatz.von("probe.ohne-max", Satz.Art.SATZ, "anzahl");
        List<Satz> schluessel = List.of(mitMax, ohneMax);
        Sprachbuch vorlage = Sprachprobe.buch(schluessel,
                "probe.mit-max", "<red>erlaubt sind {max}</red>",
                "probe.ohne-max", "<red>es sind {anzahl}</red>");

        var gelesen = Sprachliste.ausQuelle(quelle(
                "probe:",
                "  mit-max: \"<red>dein Rang erlaubt {max}</red>\"",
                "  ohne-max: \"<red>es sind {max}</red>\""), vorlage, schluessel);

        assertEquals(List.of(), gelesen.klagen(), "bekommen: " + gelesen.klagen());
        assertNotNull(gelesen.buch());
        assertEquals("<red>dein Rang erlaubt {max}</red>",
                gelesen.buch().wortlaut(mitMax), "dieser Satz war in Ordnung");
        assertEquals("<red>es sind {anzahl}</red>", gelesen.buch().wortlaut(ohneMax),
                "nur DIESER eine faellt zurueck");
    }

    @Test
    @DisplayName("19 - ein weggelassener Platzhalter wird uebernommen, mit Bericht")
    void fehlenderPlatzhalterWirdUebernommen() {
        var gelesen = Sprachliste.ausQuelle(quelle(
                "mark:",
                "  verlauf:",
                "    zeile: \"<gray>{zeit}</gray>\""), vorlage());

        assertEquals(List.of(), gelesen.klagen(), "eine Teilmenge ist erlaubt - er"
                + " verzichtet auf einen Wert, und das ist seine Sache");
        assertEquals("<gray>{zeit}</gray>", gelesen.buch().wortlaut(MarkSatz.VERLAUF_ZEILE));
        assertTrue(gelesen.bericht().stream().anyMatch(zeile
                        -> zeile.contains("{betrag}") && zeile.contains("nennt")),
                "bekommen: " + gelesen.bericht());
    }

    @Test
    @DisplayName("20 - eine Klage tauscht GAR NICHTS")
    void eineKlageTauschtGarNichts() {
        var gelesen = Sprachliste.ausQuelle(quelle(
                "mark:",
                "  verlauf:",
                "    grund:",
                "      eroeffnung: \"\""), vorlage());

        assertFalse(gelesen.klagen().isEmpty());
        // Die Sperre, die die Verdrahtung nicht umgehen kann: uebernimm(null) wirft.
        assertNull(gelesen.buch());
    }

    @Test
    @DisplayName("21 - eine unlesbare Datei ist eine Klage und kein Wurf")
    void unlesbareDateiIstKlageUndKeinWurf() {
        var gelesen = assertDoesNotThrow(() -> Sprachliste.ausQuelle(
                "mark:\n\tverlauf: \"x\"\n", vorlage()));

        assertNull(gelesen.buch());
        assertFalse(gelesen.klagen().isEmpty());
        assertTrue(gelesen.klagen().get(0).contains("line 2")
                        || gelesen.klagen().get(0).contains("Zeile"),
                "die Zeilennummer aus der YAML-Meldung ist das Brauchbare daran,"
                        + " bekommen: " + gelesen.klagen());
        assertFalse(gelesen.klagen().get(0).contains("'reader'"),
                "SnakeYAML nennt die Quelle 'reader' - wer das liest, sucht nach einer"
                        + " Datei dieses Namens");
    }

    @Test
    @DisplayName("21b - eine Umbruchmarke im Handbalken ist eine Klage")
    void handbalkenMitUmbruchIstKlage() {
        // sendActionBar nimmt ein Component und keine Liste (Schutzwache:210) - die zweite
        // Zeile verschwindet stumm oder erscheint als Zeichen.
        Satz balken = Sprachprobe.Probesatz.von("probe.balken", Satz.Art.HANDBALKEN);
        List<Satz> schluessel = List.of(balken);
        Sprachbuch vorlage = Sprachprobe.buch(schluessel,
                "probe.balken", "<red>Hier darfst du nicht bauen</red>");

        var gelesen = Sprachliste.ausQuelle(
                "probe:\n  balken: \"<red>Erste\\nZweite</red>\"\n", vorlage, schluessel);

        assertNull(gelesen.buch());
        assertTrue(gelesen.klagen().stream().anyMatch(klage
                        -> klage.contains("probe.balken")
                        && klage.contains("Umbruchmarke")),
                "bekommen: " + gelesen.klagen());

        // In die andere Richtung: ohne Umbruch geht derselbe Satz durch.
        var heil = Sprachliste.ausQuelle(
                "probe:\n  balken: \"<red>Nicht hier</red>\"\n", vorlage, schluessel);
        assertEquals(List.of(), heil.klagen());
    }
}
