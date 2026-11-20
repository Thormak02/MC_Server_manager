package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Bedingungsart;
import de.freimarken.kern.Muenzwerte;
import de.freimarken.kern.Wechselwerte;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser des Muenzhauskatalogs - was er annimmt, was er verwirft, und was er SAGT.
 *
 * <p>Dieselbe Bauart wie {@code AufgabenlisteTest} und {@code WechsellisteTest}: Die Datei
 * kommt als Textblock herein, das Ergebnis geht heraus - ohne Server. Geprueft wird dabei
 * nicht nur, WAS gilt, sondern auch, dass jede Klage den Pfad und die FOLGE nennt: Ein
 * Betreiber muss wissen, ob sein Katalog noch steht.
 */
@DisplayName("Der Leser des Muenzhauses")
class MuenzlisteTest {

    private static Muenzliste.Ergebnis lies(String yaml) {
        return Muenzliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml)),
                Wechselwerte.VORGABE);
    }

    private static void klagtUeber(Muenzliste.Ergebnis ergebnis, String stueck) {
        assertTrue(ergebnis.klagen().stream().anyMatch(klage -> klage.contains(stueck)),
                "keine Klage ueber " + stueck + ": " + ergebnis.klagen());
    }

    private static boolean berichtetUeber(Muenzliste.Ergebnis ergebnis, String stueck) {
        return ergebnis.bericht().stream().anyMatch(zeile -> zeile.contains(stueck));
    }

    @Test
    @DisplayName("Ein vollstaendiger Abschnitt kommt vollstaendig an - in DIESER Folge")
    void vollstaendig() {
        var ergebnis = lies("""
                muenzhaus:
                  an: true
                  katalog:
                    zuletzt:
                      gegenstaende:
                        bell: 1
                      preis: 9
                    zuerst:
                      gegenstaende:
                        carved_pumpkin: 2
                        lilac: 4
                      preis: 1
                      bedingungen:
                        parzellen: 2
                """);

        assertEquals(List.of(), ergebnis.klagen());
        assertEquals(List.of("zuletzt", "zuerst"), ergebnis.werte().schluessel(),
                "die Reihenfolge der DATEI, nicht die alphabetische");
        var zuerst = ergebnis.werte().posten("zuerst").orElseThrow();
        assertEquals(1L, zuerst.preis());
        assertEquals(List.of("carved_pumpkin", "lilac"),
                List.copyOf(zuerst.gegenstaende().keySet()));
        assertEquals(2L, zuerst.bedingungen().get(Bedingungsart.PARZELLEN));
        assertTrue(ergebnis.werte().posten("zuletzt").orElseThrow().ohneBedingung());
    }

    @Test
    @DisplayName("Die ausgelieferte config.yml bringt GENAU den Katalog des Programms")
    void ausgelieferteDatei() throws Exception {
        // DIE NAHT zwischen Datei und Programm. Steht dort ein anderer Preis oder ein
        // anderer Gegenstand, sagt die ausgelieferte Datei etwas anderes als die Vorgabe
        // im Quelltext - und beim naechsten Umzug gilt das Falsche.
        try (var strom = getClass().getResourceAsStream("/config.yml")) {
            assertTrue(strom != null, "config.yml liegt nicht im Klassenpfad");
            var datei = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                    strom, java.nio.charset.StandardCharsets.UTF_8));
            var ergebnis = Muenzliste.ausKonfiguration(datei, Wechselwerte.VORGABE);

            assertEquals(List.of(), ergebnis.klagen(),
                    "die ausgelieferte Datei klagt: " + ergebnis.klagen());
            assertEquals(Muenzwerte.VORGABE, ergebnis.werte());
            assertTrue(berichtetUeber(ergebnis, "Muenzhaus: 4 Posten"),
                    "die Rechenprobe gehoert in den Bericht: " + ergebnis.bericht().toString());
        }
    }

    @Test
    @DisplayName("Ohne Datei gilt die Vorgabe, und niemand klagt")
    void ohneDatei() {
        var ergebnis = Muenzliste.ausKonfiguration(null, Wechselwerte.VORGABE);

        assertEquals(Muenzwerte.VORGABE, ergebnis.werte());
        assertEquals(List.of(), ergebnis.klagen());
        assertEquals(List.of(), ergebnis.bericht());
    }

    @Test
    @DisplayName("Ein fehlender Abschnitt gilt als Vorgabe und wird GEMELDET")
    void fehlenderAbschnitt() {
        var ergebnis = lies("wirtschaft:\n  grundpreis: 25000\n");

        assertEquals(Muenzwerte.VORGABE, ergebnis.werte());
        assertEquals(List.of(), ergebnis.klagen());
        assertTrue(berichtetUeber(ergebnis, "2 von 2 Muenzzeilen"), ergebnis.bericht().toString());
        assertTrue(berichtetUeber(ergebnis, "muenzhaus.katalog"), ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Ein LEERER Katalog gilt - wer alles herausnimmt, will nichts verkaufen")
    void leererKatalogGilt() {
        // Der Unterschied zum fehlenden Abschnitt: Dort ist nichts entschieden, hier
        // schon. Ein Rueckfall auf die Vorgabe brachte dem Betreiber die vier Posten
        // zurueck, die er gerade herausgenommen hat.
        var ergebnis = lies("""
                muenzhaus:
                  an: true
                  katalog: {}
                """);

        assertEquals(List.of(), ergebnis.klagen());
        assertEquals(List.of(), ergebnis.werte().katalog());
        assertFalse(ergebnis.werte().greift());
        assertTrue(berichtetUeber(ergebnis, "nichts zu kaufen"), ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Ein Katalog, der keine Liste ist, wird verworfen")
    void katalogIstKeineListe() {
        var ergebnis = lies("""
                muenzhaus:
                  an: true
                  katalog: "vier"
                """);

        klagtUeber(ergebnis, "keine Liste von Posten");
        klagtUeber(ergebnis, "\"vier\"");
        assertEquals(Muenzwerte.VORGABE_KATALOG, ergebnis.werte().katalog());
    }

    @Test
    @DisplayName("Ein kaputter Posten faellt GANZ weg, die uebrigen gelten - sechs Wege")
    void kaputterPosten() {
        // Je Weg eine Klage, und jede nennt den Pfad UND die Folge. Der ganze Posten
        // faellt, nicht nur seine Zeile: Ein Posten, dem ein Stueck fehlt, ist ein
        // ANDERER Posten - und zwar ein guenstigerer.
        String vorlage = """
                muenzhaus:
                  an: true
                  katalog:
                    gut:
                      gegenstaende:
                        bell: 1
                      preis: 5
                    schlecht:
                %s
                """;
        List<String> wege = List.of(
                // kein Abschnitt, sondern ein Wert
                "      \"zwei\"",
                // ohne Gegenstaende
                "      preis: 2",
                // Gegenstand, den Minecraft nicht kennt
                "      gegenstaende:\n        gibtesnicht: 1\n      preis: 2",
                // Stueckzahl ueber der Grenze
                "      gegenstaende:\n        bell: 99999\n      preis: 2",
                // Preis in Mark statt in Goldmark
                "      gegenstaende:\n        bell: 1\n      preis: 2000000",
                // unbekannte Bedingung
                "      gegenstaende:\n        bell: 1\n      preis: 2\n"
                        + "      bedingungen:\n        heimatpunkte: 1");

        for (String weg : wege) {
            var ergebnis = lies(String.format(vorlage, weg));

            assertEquals(List.of("gut"), ergebnis.werte().schluessel(),
                    "der gute Posten muss bleiben bei: " + weg);
            assertEquals(1, ergebnis.klagen().size(), "genau eine Klage bei: " + weg);
            klagtUeber(ergebnis, "muenzhaus.katalog.schlecht");
            klagtUeber(ergebnis, "die uebrigen gelten");
        }
    }

    @Test
    @DisplayName("Eine unbekannte Bedingung nennt, was es GIBT")
    void unbekannteBedingungNenntDieArten() {
        var ergebnis = lies("""
                muenzhaus:
                  an: true
                  katalog:
                    hut:
                      gegenstaende:
                        carved_pumpkin: 1
                      preis: 2
                      bedingungen:
                        heimatpunkte: 1
                """);

        klagtUeber(ergebnis, "heimatpunkte");
        for (String art : Bedingungsart.alleSchluessel()) {
            klagtUeber(ergebnis, art);
        }
    }

    @Test
    @DisplayName("Eine Bedingung von 0 wird verworfen - sie wirkte nie")
    void nullIstKeineBedingung() {
        var ergebnis = lies("""
                muenzhaus:
                  an: true
                  katalog:
                    hut:
                      gegenstaende:
                        carved_pumpkin: 1
                      preis: 2
                      bedingungen:
                        parzellen: 0
                """);

        klagtUeber(ergebnis, "Eine 0 ist keine Bedingung");
        assertEquals(List.of(), ergebnis.werte().katalog(),
                "faellt der einzige Posten weg, bleibt die Liste LEER - und NICHT die"
                        + " ausgelieferte: Der Abschnitt stand ja da");
        assertTrue(berichtetUeber(ergebnis, "kein einziger Posten war brauchbar"),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Faellt JEDER Posten weg, bleibt der Katalog leer - mit eigener Zeile")
    void jederPostenKaputt() {
        var ergebnis = lies("""
                muenzhaus:
                  an: true
                  katalog:
                    eins: "x"
                    zwei: "y"
                """);

        assertEquals(2, ergebnis.klagen().size());
        assertEquals(List.of(), ergebnis.werte().katalog());
        assertTrue(berichtetUeber(ergebnis, "kein einziger Posten war brauchbar"),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Ein Name, der nicht taugt, nimmt seinen Posten mit")
    void nameTaugtNicht() {
        var ergebnis = lies("""
                muenzhaus:
                  an: true
                  katalog:
                    "Mein Hut":
                      gegenstaende:
                        carved_pumpkin: 1
                      preis: 2
                """);

        klagtUeber(ergebnis, "der Name taugt nicht");
        klagtUeber(ergebnis, "sprache.yml");
    }

    @Test
    @DisplayName("Mehr Posten als erlaubt lassen den GANZEN Katalog fallen")
    void zuVielePosten() {
        // Der Fall, den nur der Konstruktor des records sehen kann: Jeder Posten fuer
        // sich ist brauchbar, und erst zusammen sind sie zu viele. Der Leser wirft nicht
        // - er klagt und nimmt die Vorgabe.
        StringBuilder datei = new StringBuilder("muenzhaus:\n  an: true\n  katalog:\n");
        for (int nummer = 0; nummer <= Muenzwerte.HOECHSTE_POSTENZAHL; nummer++) {
            datei.append("    posten-").append(nummer)
                    .append(":\n      gegenstaende:\n        bell: 1\n      preis: 1\n");
        }

        var ergebnis = lies(datei.toString());

        klagtUeber(ergebnis, "Der ganze Katalog ist verworfen");
        assertEquals(Muenzwerte.VORGABE, ergebnis.werte());
        assertTrue(berichtetUeber(ergebnis, "Muenzhaus: 4 Posten"),
                "auch im Rueckfall gehoert die Rechenprobe in den Bericht: "
                        + ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Der Schalter wird am TYP geprueft, nicht mit getBoolean")
    void schalterAmTyp() {
        var ergebnis = lies("""
                muenzhaus:
                  an: ja
                  katalog: {}
                """);

        klagtUeber(ergebnis, "kein Ja oder Nein");
        assertTrue(ergebnis.werte().an(), "es bleibt beim ausgelieferten true");
    }

    @Test
    @DisplayName("Der Schalter auf false wird gemeldet, nicht beklagt")
    void schalterAus() {
        var ergebnis = lies("""
                muenzhaus:
                  an: false
                  katalog:
                    hut:
                      gegenstaende:
                        carved_pumpkin: 1
                      preis: 2
                """);

        assertEquals(List.of(), ergebnis.klagen());
        assertFalse(ergebnis.werte().an());
        assertFalse(ergebnis.werte().greift());
        assertTrue(berichtetUeber(ergebnis, "muenzhaus.an ist aus"), ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Die Rechenprobe nennt jeden Preis in BEIDEN Waehrungen")
    void rechenprobeNenntBeideWaehrungen() {
        // DIE EINZIGE SCHRANKE gegen den Preis, der im Bereich liegt und trotzdem falsch
        // ist: Goldmark ist die grobe Einheit, und erst in Mark sieht ein Betreiber, ob
        // ein Hut eine Spielstunde oder einen Monat kostet. Gerechnet wird mit dem
        // HINWEGkurs - das ist, was die Goldmark gekostet hat.
        assertEquals("Muenzhaus: 4 Posten von 2 Goldmark (2000 Mark) bis 5 Goldmark"
                        + " (5000 Mark); der teuerste ist musikscheibe. Gewechselt wird"
                        + " zu 1000 Mark je Goldmark.",
                Muenzliste.rechenprobe(Muenzwerte.VORGABE, Wechselwerte.VORGABE));
    }

    @Test
    @DisplayName("Die Rechenprobe rechnet mit dem GELESENEN Kurs")
    void rechenprobeNimmtDenGelesenenKurs() {
        // Sonst stuende im Bericht ein Markbetrag, den dieser Server nicht verlangt -
        // und darum laeuft dieser Leser nach der Wechselstube.
        String satz = Muenzliste.rechenprobe(Muenzwerte.VORGABE,
                Wechselwerte.VORGABE.mitKursen(2_000L, 800L));

        assertTrue(satz.contains("(4000 Mark)"), satz);
        assertTrue(satz.contains("2000 Mark je Goldmark"), satz);
    }

    @Test
    @DisplayName("Ein Gratisposten steht als gratis da - gerechnet wird er nicht")
    void gratisWirdNichtGerechnet() {
        // kaufpreis(0) wirft (ein Tausch ueber null Goldmark ist keiner), und ein Wurf
        // hier riss den Startlauf mit - durch Wirtschaftsliste bis in onEnable.
        var werte = Muenzwerte.VORGABE.mitKatalog(List.of(
                new Muenzwerte.Posten("geschenk", java.util.Map.of("bell", 1), 0L,
                        java.util.Map.of())));

        String satz = Muenzliste.rechenprobe(werte, Wechselwerte.VORGABE);

        assertTrue(satz.contains("gratis"), satz);
    }

    @Test
    @DisplayName("Ohne Katalog sagt die Rechenprobe, WARUM nichts zu kaufen ist")
    void rechenprobeOhneKatalog() {
        assertTrue(Muenzliste.rechenprobe(Muenzwerte.VORGABE.mitAn(false),
                Wechselwerte.VORGABE).contains("muenzhaus.an ist aus"));
        assertTrue(Muenzliste.rechenprobe(Muenzwerte.VORGABE.mitKatalog(List.of()),
                Wechselwerte.VORGABE).contains("der Katalog ist leer"));
        assertTrue(Muenzliste.rechenprobe(Muenzwerte.VORGABE.mitAn(false),
                        Wechselwerte.VORGABE).contains("bleiben zu sehen"),
                "Kontostand und Verlauf bleiben - das gehoert in die Zeile");
    }

    @Test
    @DisplayName("Keine Zeile behauptet, ein Wert GELTE, wenn er nur beim Start gilt")
    void keineGeltungsbehauptung() {
        // Dieselbe Zusage wie in WirtschaftslisteTest: Keiner der Muenzschluessel steht
        // in NUR_BEIM_START, also darf jede Zeile "es gilt" sagen - und die Probe haelt
        // fest, dass sie es ueber fehltText tut und nicht selbst formuliert.
        var ergebnis = lies("wirtschaft:\n  grundpreis: 25000\n");

        for (String zeile : ergebnis.bericht()) {
            if (zeile.startsWith(Muenzwerte.PFAD_AN)) {
                assertTrue(zeile.contains("es gilt"), zeile);
            }
        }
    }
}
