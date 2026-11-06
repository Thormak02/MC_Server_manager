package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Berufsangabe;
import de.freimarken.kern.Berufsverzeichnis;
import de.freimarken.kern.Berufswerte;
import de.freimarken.kern.Lohntafel;
import de.freimarken.kern.Taetigkeit;
import de.freimarken.kern.Verdienstwerte;
import de.freimarken.kern.Wirtschaftswerte;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser des Abschnitts {@code verdienst:} - ohne Server.
 *
 * <p>Moeglich, weil {@code YamlConfiguration} reine Dateiarbeit ist und der Leser mit
 * {@link Material#getMaterial(String)} statt {@code matchMaterial} prueft: Jenes fragte die
 * Registry des Servers, und dann liefe hier nichts.
 *
 * <p><b>Die tragende Probe ist {@link #ausgelieferteDateiIstBrauchbar()}:</b> Die Lohnliste
 * ist der eine Wert dieses Plugins ohne Rueckfall im Programm. Ist sie kaputt, verdient
 * niemand etwas - und das faellt erst auf, wenn ein Spieler eine Stunde gegraben hat und
 * fragt, warum sein Konto leer ist.
 *
 * <p>Jede Klage dieses Lesers hat hier eine Probe, und die Proben vergleichen den PFAD und
 * nicht den Wortlaut: Ein umformulierter Satz soll nicht rot werden, ein verschwundener
 * schon.
 */
@DisplayName("Verdienst aus der config.yml")
class VerdienstlisteTest {

    /**
     * Ein vollstaendiger Abschnitt mit vier Lohnzeilen.
     *
     * <p>Genau die Vorgabezahlen bei Deckel und Minderung: So muss jede Probe, die etwas
     * anderes erwartet, ihren Unterschied selbst hineinschreiben, und eine Probe, die
     * nichts aendert, bekommt die Vorgabe.
     */
    private static final String VOLLSTAENDIG = """
            verdienst:
              tagesdeckel: 20000
              minderung:
                voll-bis: 200
                stufe: 200
                faktor-prozent: 50
                boden-prozent: 10
              loehne:
                bergmann:
                  abbauen:
                    stone: 40
                    coal_ore: 250
                  setzen:
                    stone_bricks: 10
                bauer:
                  abbauen:
                    wheat: 60
            """;

    /** Dieselben drei Berufe wie in {@link WirtschaftslisteTest} - zwei Grund, eine Spez. */
    private static final Berufsverzeichnis BERUFE = Berufsverzeichnis.ausAngaben(List.of(
            new Berufsangabe("bergmann", null),
            new Berufsangabe("bauer", null),
            new Berufsangabe("steinmetz", "bergmann")));

    private static Verdienstliste.Ergebnis lies(String yaml) {
        return Verdienstliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)), BERUFE);
    }

    /** Ob irgendeine Klage mit diesem Pfad beginnt. */
    private static boolean klagtUeber(Verdienstliste.Ergebnis ergebnis, String pfad) {
        return ergebnis.klagen().stream().anyMatch(klage -> klage.startsWith(pfad));
    }

    @Test
    @DisplayName("Eine vollstaendige Datei kommt ganz an")
    void vollstaendigKommtAn() {
        var ergebnis = lies(VOLLSTAENDIG);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        Verdienstwerte werte = ergebnis.werte();
        assertEquals(20_000L, werte.tagesdeckel());
        assertEquals(200, werte.vollBis());
        assertEquals(200, werte.stufe());
        assertEquals(50, werte.faktorProzent());
        assertEquals(10, werte.bodenProzent());
        assertEquals(4, werte.loehne().anzahl());
        assertEquals(40L, werte.loehne().pfennige("bergmann", Taetigkeit.ABBAUEN,
                "stone"));
        assertEquals(10L, werte.loehne().pfennige("bergmann", Taetigkeit.SETZEN,
                "stone_bricks"));
        assertEquals(60L, werte.loehne().pfennige("bauer", Taetigkeit.ABBAUEN, "wheat"));
    }

    @Test
    @DisplayName("Ohne Konfiguration gilt die Vorgabe - und die Lohnliste ist leer")
    void ohneKonfigurationDieVorgabe() {
        var ergebnis = Verdienstliste.ausKonfiguration(null, BERUFE);

        assertEquals(Verdienstwerte.VORGABE, ergebnis.werte());
        assertTrue(ergebnis.klagen().isEmpty());
        // Die Vorgabe traegt mit Absicht eine LEERE Tafel: Ohne Datei verdient niemand
        // etwas, und das ist die sichere Richtung - ein geratener Lohn macht Geld, das
        // keine Ruecksicherung zurueckholt.
        assertTrue(ergebnis.werte().loehne().leer());
    }

    @Test
    @DisplayName("Eine fehlende Lohnliste ist ein BERICHT und keine Klage")
    void fehlendeLohnlisteIstKeineKlage() {
        // Der Unterschied entscheidet, ob ein Server neu laden kann: Eine Klage
        // verhindert das ganze Neuladen, und eine vorhandene config.yml wird beim Update
        // NIE ueberschrieben. Auf einem Server ohne diesen Abschnitt koennte der
        // Betreiber sonst keinen Preis mehr neu laden, nur weil er keine Loehne hat.
        var ergebnis = lies("""
                verdienst:
                  tagesdeckel: 20000
                """);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertTrue(ergebnis.werte().loehne().leer());
        assertTrue(ergebnis.bericht().stream().anyMatch(zeile ->
                        zeile.contains(Verdienstwerte.PFAD_LOEHNE)
                                && zeile.contains("NIEMAND")),
                "der Betreiber muss erfahren, dass seine Berufe nichts einbringen: "
                        + ergebnis.bericht());
    }

    @Test
    @DisplayName("Eine leere Lohnliste wird genannt - mit Verweis auf die Zeilen davor")
    void leereLohnlisteWirdGenannt() {
        // Alle Zeilen kaputt heisst dasselbe wie keine Zeile: niemand verdient etwas.
        // Die Summenzeile ist auch hier ein Bericht, denn jede einzelne Zeile hat ihre
        // eigene Klage - zweimal dasselbe verhindern waere einmal zu viel.
        var ergebnis = lies(VOLLSTAENDIG.replace("stone: 40", "kein_block_dieser_art: 40")
                .replace("coal_ore: 250", "coal_ore: -1")
                .replace("stone_bricks: 10", "stone_bricks: 10.5")
                .replace("wheat: 60", "wheat: nein"));

        assertEquals(4, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.werte().loehne().leer());
        assertTrue(ergebnis.bericht().stream().anyMatch(zeile ->
                        zeile.contains(Verdienstwerte.PFAD_LOEHNE) && zeile.contains("leer")),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Ein Deckel von 0 gilt nicht - abschalten geht ueber die Lohnliste")
    void deckelNullGiltNicht() {
        // Eine 0 hiesse "niemand verdient etwas", und das stuende an der falschen Stelle:
        // Wer die Berufe abschalten will, laesst die Loehne weg. Ein Deckel von 0 sieht
        // dagegen aus wie ein Vertipper, und ein Vertipper darf nicht alles abschalten.
        for (String getippt : new String[] {"0", "-1", "-20000"}) {
            var ergebnis = lies(VOLLSTAENDIG.replace("tagesdeckel: 20000",
                    "tagesdeckel: " + getippt));

            assertTrue(klagtUeber(ergebnis, Verdienstwerte.PFAD_TAGESDECKEL),
                    getippt + " muss beklagt werden: " + ergebnis.klagen());
            assertEquals(Verdienstwerte.VORGABE_TAGESDECKEL,
                    ergebnis.werte().tagesdeckel());
            // Die Lohnliste bleibt stehen: Ein kaputter Deckel ist kein Grund, die
            // vierundachtzig Saetze wegzuwerfen.
            assertEquals(4, ergebnis.werte().loehne().anzahl());
        }
    }

    @Test
    @DisplayName("Ein Deckel um mehr als Faktor 2 braucht die zweite Bestaetigung")
    void grosserDeckelspringBrauchtBestaetigung() {
        // Derselbe Schutz wie bei jedem anderen Geldwert: Was ein Vertipper
        // zweihunderttausend macht, liegt binnen Minuten auf vielen Konten.
        var ohne = lies(VOLLSTAENDIG.replace("tagesdeckel: 20000",
                "tagesdeckel: 200000"));

        assertTrue(klagtUeber(ohne, Verdienstwerte.PFAD_TAGESDECKEL),
                ohne.klagen().toString());
        assertEquals(Verdienstwerte.VORGABE_TAGESDECKEL, ohne.werte().tagesdeckel());

        var mit = lies(VOLLSTAENDIG.replace("tagesdeckel: 20000",
                "tagesdeckel: 200000") + """
                wirtschaft:
                  sprung-bestaetigt:
                    tagesdeckel: "20000 -> 200000"
                """);

        assertTrue(mit.klagen().isEmpty(), mit.klagen().toString());
        assertEquals(200_000L, mit.werte().tagesdeckel());
    }

    @Test
    @DisplayName("Eine Stufenzahl von 0 gilt nicht - sie teilte die Rechnung durch null")
    void stufenzahlNullGiltNicht() {
        for (String pfad : new String[] {"voll-bis", "stufe"}) {
            var ergebnis = lies(VOLLSTAENDIG.replace(pfad + ": 200", pfad + ": 0"));

            assertTrue(ergebnis.klagen().stream().anyMatch(klage ->
                            klage.startsWith("verdienst.minderung." + pfad)),
                    pfad + " muss beklagt werden: " + ergebnis.klagen());
        }
        assertEquals(Verdienstwerte.VORGABE_VOLL_BIS,
                lies(VOLLSTAENDIG.replace("voll-bis: 200", "voll-bis: 0"))
                        .werte().vollBis());
        assertEquals(Verdienstwerte.VORGABE_STUFE,
                lies(VOLLSTAENDIG.replace("stufe: 200", "stufe: 0"))
                        .werte().stufe());
    }

    @Test
    @DisplayName("Der Faktor darf nicht 100 sein, der Boden schon")
    void faktorUndBodenHabenVerschiedeneGrenzen() {
        // Beim BODEN heisst 100 "die Minderung ist aus" - eine sinnvolle Einstellung.
        // Beim FAKTOR hiesse 100 dasselbe, nur an der falschen Zahl eingestellt: Die
        // Schleife liefe dann, bis der Boden greift, und mindert dabei nichts. Wer keine
        // Minderung will, setzt voll-bis hoch oder den Boden auf 100.
        var faktorHundert = lies(VOLLSTAENDIG.replace("faktor-prozent: 50",
                "faktor-prozent: 100"));
        assertTrue(klagtUeber(faktorHundert, Verdienstwerte.PFAD_FAKTOR),
                faktorHundert.klagen().toString());
        assertEquals(Verdienstwerte.VORGABE_FAKTOR_PROZENT,
                faktorHundert.werte().faktorProzent());

        var bodenHundert = lies(VOLLSTAENDIG.replace("boden-prozent: 10",
                "boden-prozent: 100"));
        assertTrue(bodenHundert.klagen().isEmpty(), bodenHundert.klagen().toString());
        assertEquals(100, bodenHundert.werte().bodenProzent());
    }

    @Test
    @DisplayName("Ein Boden von 0 gilt nicht - das waere eine unsichtbare Wand")
    void bodenNullGiltNicht() {
        // Bei 0 Prozent brachte Arbeit ab einer Zahl, die niemand kennt, gar nichts mehr
        // ein - und der Spieler sieht nur, dass sein Handbalken aufhoert.
        for (String getippt : new String[] {"0", "-10", "101"}) {
            var ergebnis = lies(VOLLSTAENDIG.replace("boden-prozent: 10",
                    "boden-prozent: " + getippt));

            assertTrue(klagtUeber(ergebnis, Verdienstwerte.PFAD_BODEN),
                    getippt + " muss beklagt werden: " + ergebnis.klagen());
            assertEquals(Verdienstwerte.VORGABE_BODEN_PROZENT,
                    ergebnis.werte().bodenProzent());
        }
    }

    @Test
    @DisplayName("Ein Lohn fuer einen unbekannten Beruf ist eine Zeile, die nie wirkt")
    void unbekannterBerufWirdBeklagt() {
        var ergebnis = lies(VOLLSTAENDIG.replace("bauer:",
                "holzfaeller:"));

        assertTrue(klagtUeber(ergebnis, Verdienstwerte.PFAD_LOEHNE + ".holzfaeller"),
                ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains(Berufswerte.PFAD_LISTE),
                "die Klage muss sagen, WO die Berufe stehen: " + ergebnis.klagen());
        // Der ganze Block ist uebersprungen, die drei Zeilen des Bergmanns stehen.
        assertEquals(3, ergebnis.werte().loehne().anzahl());
    }

    @Test
    @DisplayName("Eine Spezialisierung ist ein Beruf wie jeder andere")
    void spezialisierungDarfLohnHaben() {
        // Sonst waere die ganze Spezialisierung ohne Zweck: Sie soll sich an der Arbeit
        // zeigen und nicht nur im Namen.
        var ergebnis = lies("""
                verdienst:
                  loehne:
                    steinmetz:
                      setzen:
                        chiseled_stone_bricks: 30
                """);

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertEquals(30L, ergebnis.werte().loehne().pfennige("steinmetz",
                Taetigkeit.SETZEN, "chiseled_stone_bricks"));
    }

    @Test
    @DisplayName("Eine unbekannte Taetigkeit wird beklagt - mit den vorhandenen im Satz")
    void unbekannteTaetigkeitWirdBeklagt() {
        // Die Einrueckung ist die des FERTIGEN Textblocks und nicht die im Quelltext:
        // Java zieht den gemeinsamen Einzug ab, also stehen hier sechs und acht Leerzeichen.
        var ergebnis = lies(VOLLSTAENDIG.replace("      abbauen:\n        stone: 40",
                "      fischen:\n        stone: 40"));

        assertTrue(klagtUeber(ergebnis, Verdienstwerte.PFAD_LOEHNE + ".bergmann.fischen"),
                ergebnis.klagen().toString());
        // Die Klage nennt, was es gibt, und sagt, dass mehr kommt: Sonst suchte der
        // Betreiber den Fehler in seiner Schreibweise, obwohl die Taetigkeit noch fehlt.
        String klage = ergebnis.klagen().get(0);
        assertTrue(klage.contains("abbauen") && klage.contains("setzen"), klage);
        assertTrue(klage.contains("Bauschritt"), klage);
    }

    @Test
    @DisplayName("Ein unbekanntes Material wird mit seinem Namen beklagt")
    void unbekanntesMaterialWirdBeklagt() {
        var ergebnis = lies(VOLLSTAENDIG.replace("stone: 40", "stoen: 40"));

        assertTrue(klagtUeber(ergebnis,
                        Verdienstwerte.PFAD_LOEHNE + ".bergmann.abbauen.stoen"),
                ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("\"stoen\""),
                "der Name muss in der Klage stehen: " + ergebnis.klagen());
        assertEquals(3, ergebnis.werte().loehne().anzahl(),
                "eine kaputte Zeile laesst die uebrigen stehen");
    }

    @Test
    @DisplayName("Ein Name mit Bereichsvorsatz wird beklagt - und die Klage sagt, warum")
    void bereichsvorsatzWirdBeklagt() {
        // Das ist der PREIS dafuer, dass dieser Leser ohne Server pruefbar ist:
        // getMaterial kennt minecraft:stone nicht, matchMaterial schon - aber jenes
        // fragte die Registry. Die Klage muss den nackten Namen nennen, sonst sieht der
        // Betreiber nur, dass sein voellig richtig geschriebener Block nicht gilt.
        var ergebnis = lies(VOLLSTAENDIG.replace("stone: 40",
                "minecraft/stone: 40"));

        assertTrue(ergebnis.klagen().size() == 1, ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("ohne minecraft: davor"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Ein Lohn mit Nachkommastellen wird beklagt - Pfennige sind ganze Zahlen")
    void kommazahlWirdBeklagt() {
        // Der haeufigste Vertipper bei Geld in YAML: 1.000 ist dort KEINE Tausend,
        // sondern eine Eins. Die Klage muss die Einheit nennen, sonst traegt der
        // Betreiber denselben Wert noch einmal so ein.
        for (String getippt : new String[] {"10.5", "1.000", "nein", "true"}) {
            var ergebnis = lies(VOLLSTAENDIG.replace("stone: 40", "stone: " + getippt));

            assertTrue(klagtUeber(ergebnis,
                            Verdienstwerte.PFAD_LOEHNE + ".bergmann.abbauen.stone"),
                    getippt + " muss beklagt werden: " + ergebnis.klagen());
            assertTrue(ergebnis.klagen().get(0).contains("PFENNIGEN"),
                    ergebnis.klagen().get(0));
            assertEquals(3, ergebnis.werte().loehne().anzahl());
        }
    }

    @Test
    @DisplayName("Ein negativer Lohn waere eine Gebuehr fuers Arbeiten")
    void negativerLohnWirdBeklagt() {
        var ergebnis = lies(VOLLSTAENDIG.replace("stone: 40", "stone: -40"));

        assertTrue(klagtUeber(ergebnis,
                        Verdienstwerte.PFAD_LOEHNE + ".bergmann.abbauen.stone"),
                ergebnis.klagen().toString());
        assertEquals(3, ergebnis.werte().loehne().anzahl());
    }

    @Test
    @DisplayName("Ein Beruf oder eine Taetigkeit ohne Abschnitt wird beklagt")
    void keinAbschnittWirdBeklagt() {
        // Der Vertipper, den Bukkit sonst stumm schluckt: ein Lohn direkt unter dem
        // Beruf, ohne die Taetigkeit dazwischen.
        var berufFlach = lies("""
                verdienst:
                  loehne:
                    bergmann: 40
                """);
        assertTrue(klagtUeber(berufFlach, Verdienstwerte.PFAD_LOEHNE + ".bergmann"),
                berufFlach.klagen().toString());
        assertTrue(berufFlach.klagen().get(0).contains("Taetigkeiten"),
                berufFlach.klagen().get(0));

        var taetigkeitFlach = lies("""
                verdienst:
                  loehne:
                    bergmann:
                      abbauen: 40
                """);
        assertTrue(klagtUeber(taetigkeitFlach,
                        Verdienstwerte.PFAD_LOEHNE + ".bergmann.abbauen"),
                taetigkeitFlach.klagen().toString());
        assertTrue(taetigkeitFlach.klagen().get(0).contains("Materialien"),
                taetigkeitFlach.klagen().get(0));
    }

    @Test
    @DisplayName("Fehlende Verdienstzeilen werden gezaehlt und genannt")
    void fehlendeZeilenWerdenGenannt() {
        var ergebnis = lies("datenbank:\n  url: \"\"\n");

        assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
        assertTrue(ergebnis.bericht().stream().anyMatch(zeile ->
                        zeile.startsWith("6 von 6 Verdienstzeilen")),
                ergebnis.bericht().toString());
        // Jeder Schluessel muss NAMENTLICH dastehen - eine Zahl allein sagt dem
        // Betreiber nicht, welche Zeile er nachtragen soll.
        for (String pfad : new String[] {Verdienstwerte.PFAD_TAGESDECKEL,
                                         Verdienstwerte.PFAD_VOLL_BIS,
                                         Verdienstwerte.PFAD_STUFE,
                                         Verdienstwerte.PFAD_FAKTOR,
                                         Verdienstwerte.PFAD_BODEN,
                                         Verdienstwerte.PFAD_LOEHNE}) {
            assertTrue(ergebnis.bericht().toString().contains(pfad), pfad);
        }
    }

    @Test
    @DisplayName("Der Bericht nennt die Zahl der Saetze und beide Taetigkeiten")
    void berichtNenntDieZahlen() {
        // Damit der Betreiber nach einem Neuladen sieht, dass seine Aenderung angekommen
        // ist. Eine Zahl, die er nicht sieht, kann er nicht pruefen.
        var ergebnis = lies(VOLLSTAENDIG);

        assertTrue(ergebnis.bericht().stream().anyMatch(zeile ->
                        zeile.startsWith("Lohnliste: 4 Saetze")
                                && zeile.contains("3 Materialien beim Abbauen")
                                && zeile.contains("1 beim Setzen")),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Die MITGELIEFERTE config.yml traegt eine brauchbare Lohnliste")
    void ausgelieferteDateiIstBrauchbar() {
        // Die wichtigste Probe dieser Datei: Die Lohnliste ist der eine Wert ohne
        // Rueckfall im Programm. Ist sie kaputt, verdient auf einem frischen Server
        // NIEMAND etwas - und das faellt erst auf, wenn jemand eine Stunde gegraben hat.
        try (InputStream strom =
                     Verdienstliste.class.getResourceAsStream("/config.yml")) {
            assertNotNull(strom, "die config.yml muss im Jar liegen");
            var datei = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));
            // Gegen die Berufe DERSELBEN Datei und nicht gegen die drei erfundenen: Sonst
            // waere jeder der zwoelf Berufe eine Klage ueber einen Beruf, den es gibt.
            var berufe = Berufsliste.listeAus(
                    datei.getConfigurationSection(Berufswerte.PFAD_LISTE));
            assertTrue(berufe.klagen().isEmpty(), berufe.klagen().toString());

            var ergebnis = Verdienstliste.ausKonfiguration(datei, berufe.verzeichnis());

            assertTrue(ergebnis.klagen().isEmpty(), ergebnis.klagen().toString());
            assertTrue(ergebnis.bericht().stream().noneMatch(zeile ->
                            zeile.contains("stehen nicht in deiner config.yml")),
                    "die ausgelieferte Datei darf keine Verdienstzeile schulden: "
                            + ergebnis.bericht());
            Lohntafel tafel = ergebnis.werte().loehne();
            assertFalse(tafel.leer(),
                    "die ausgelieferte config.yml muss eine Lohnliste tragen");
            assertEquals(84, tafel.anzahl(),
                    "vierundachtzig Saetze - jede Zahl darin ist gegen 2.000 bis 5.000"
                            + " Mark je Spielstunde gerechnet");
            // Alles ANDERE muss die Vorgabe des Programms sein, sonst gilt bei einem
            // Erststart etwas anderes als beim Rueckfall.
            assertEquals(Verdienstwerte.VORGABE_TAGESDECKEL,
                    ergebnis.werte().tagesdeckel());
            assertEquals(Verdienstwerte.VORGABE_VOLL_BIS, ergebnis.werte().vollBis());
            assertEquals(Verdienstwerte.VORGABE_STUFE, ergebnis.werte().stufe());
            assertEquals(Verdienstwerte.VORGABE_FAKTOR_PROZENT,
                    ergebnis.werte().faktorProzent());
            assertEquals(Verdienstwerte.VORGABE_BODEN_PROZENT,
                    ergebnis.werte().bodenProzent());
        } catch (IOException fehler) {
            throw new AssertionError(fehler);
        }
    }

    @Test
    @DisplayName("JEDER Beruf der ausgelieferten Datei hat mindestens einen Lohn")
    void jederBerufVerdientEtwas() {
        // Ein Beruf in der Liste, der nirgends einen Lohn hat, ist eine Wahl, die nichts
        // bringt - und der Spieler, der ihn gewaehlt hat, merkt das erst nach einer
        // Stunde. Diese Probe faengt den Beruf, der beim Eintragen der Loehne vergessen
        // wurde, und zwar ohne dass irgendwo eine Zahl steht, die mitwachsen muss.
        try (InputStream strom =
                     Verdienstliste.class.getResourceAsStream("/config.yml")) {
            assertNotNull(strom, "die config.yml muss im Jar liegen");
            var datei = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(strom, StandardCharsets.UTF_8));
            var berufe = Berufsliste.listeAus(
                    datei.getConfigurationSection(Berufswerte.PFAD_LISTE));
            Lohntafel tafel = Verdienstliste.ausKonfiguration(datei,
                    berufe.verzeichnis()).werte().loehne();

            List<String> ohneLohn = new ArrayList<>();
            for (String beruf : berufe.verzeichnis().berufe()) {
                boolean verdient = false;
                for (Taetigkeit taetigkeit : Taetigkeit.values()) {
                    for (String material : tafel.materialien(taetigkeit)) {
                        if (tafel.pfennige(beruf, taetigkeit, material) > 0L) {
                            verdient = true;
                            break;
                        }
                    }
                }
                if (!verdient) {
                    ohneLohn.add(beruf);
                }
            }

            assertEquals(List.of(), ohneLohn,
                    "diese Berufe stehen in der Liste, bringen aber nichts ein");
        } catch (IOException fehler) {
            throw new AssertionError(fehler);
        }
    }

    @Test
    @DisplayName("Die Lohnliste steht NICHT in den Vorgaben des Programms")
    void keineZweiteAbschrift() {
        // Die Entscheidung dieses Schrittes, als Probe: Eine Tabelle mit
        // vierundachtzig Zeilen im Programm waere eine zweite Abschrift der
        // ausgelieferten Datei, und zwei Abschriften derselben Tabelle laufen
        // auseinander - genau wie beim Abschnitt welten:. Wer hier einen Rueckfall
        // einbaut, macht ausserdem Geld, das keine Ruecksicherung zurueckholt.
        assertTrue(Verdienstwerte.VORGABE.loehne().leer(),
                "die Vorgabe traegt mit Absicht keine Loehne");
        assertTrue(Wirtschaftswerte.VORGABE.verdienst().loehne().leer());
    }
}
