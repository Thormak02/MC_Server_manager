package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Lotteriewerte;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser der Lotterie - was der Betreiber tippt, und was daraus wird.
 *
 * <p>Gelesen wird eine echte {@code YamlConfiguration} und keine nachgebaute Karte:
 * {@code YamlConfiguration} ist reine Dateiarbeit und braucht keinen Server, und damit
 * prueft diese Probe genau das, was im Spiel in der Datei steht.
 *
 * <p><b>Die tragende Zusage ist "eine kaputte ZEILE kostet nur sich selbst".</b> Ein
 * Tippfehler in einem Gegenstandsnamen darf nicht die ganze Lotterie kosten.
 *
 * <p><b>Die ZWEITE tragende Zusage sind die beiden ABHAENGIGKEITEN</b>, die der
 * Konstruktor von {@link Lotteriewerte} wirft: Hoechstpreis unter Grundpreis, und
 * Tagesgrenze unter der Zahl der gratis Lose. Beide Zahlen stehen in verschiedenen Zeilen
 * der Datei und sind einzeln erlaubt - ein Wurf daraus waere ein abgebrochener
 * Serverstart wegen zweier Zeilen, die fuer sich in Ordnung sind.
 *
 * <p><b>Geldwerte brauchen hier {@code sprung-bestaetigt}</b>, wo sie um mehr als Faktor 2
 * von der Vorgabe abweichen - sonst verwirft der Sprungschutz sie, und die Probe prueft
 * am Ende die VORGABE statt das, was sie eingetippt hat.
 */
@DisplayName("Der Leser der Lotterie")
class LotterielisteTest {

    private static Lotterieliste.Ergebnis lies(String yaml) {
        return Lotterieliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml)));
    }

    @Test
    @DisplayName("Eine vollstaendige Lotterie kommt vollstaendig an")
    void vollstaendig() {
        var ergebnis = lies("""
                lotterie:
                  an: true
                  freie-lose-je-tag: 2
                  hoechste-lose-je-tag: 9
                  grundpreis: 700
                  steigerung-prozent: 150
                  hoechstpreis: 300000
                  gewinne:
                    trost:
                      art: mark
                      gewicht: 80
                      menge: 25
                    goldstueck:
                      art: goldmark
                      gewicht: 5
                      menge: 2
                    hut:
                      art: gegenstand
                      gewicht: 15
                      menge: 1
                      gegenstand: leather_helmet
                      name: Fischerhut
                """);

        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        Lotteriewerte werte = ergebnis.werte();
        assertTrue(werte.an());
        assertEquals(2, werte.freieLoseJeTag());
        assertEquals(9, werte.hoechsteLoseJeTag());
        assertEquals(700L, werte.grundpreis());
        assertEquals(150, werte.steigerungProzent());
        assertEquals(300_000L, werte.hoechstpreis());
        assertEquals(3, werte.gewinne().size());
        assertEquals(100L, werte.gewichtsumme());

        var hut = werte.gewinn("hut").orElseThrow();
        assertEquals(Lotteriewerte.Art.GEGENSTAND, hut.art());
        assertEquals("leather_helmet", hut.gegenstand());
        assertEquals("Fischerhut", hut.anzeigename());
        assertEquals("Fischerhut", hut.imSpiel());
    }

    @Test
    @DisplayName("Ein Gegenstand, den der Server nicht kennt, kostet NUR SEINE ZEILE")
    void kaputteZeileKostetNurSichSelbst() {
        var ergebnis = lies("""
                lotterie:
                  an: true
                  gewinne:
                    gut:
                      art: mark
                      gewicht: 10
                      menge: 50
                    kaputt:
                      art: gegenstand
                      gewicht: 5
                      menge: 1
                      gegenstand: gibtsnicht
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("kaputt"),
                ergebnis.klagen().get(0));
        assertEquals(1, ergebnis.werte().gewinne().size(),
                "die brauchbare Zeile muss gelten");
        assertTrue(ergebnis.werte().gewinn("gut").isPresent());
    }

    @Test
    @DisplayName("Alle Fehler EINER Zeile stehen in EINER Klage")
    void alleFehlerEinerZeile() {
        // Wer nach dem ersten Fehler aufhoert, nennt dem Betreiber einen von drei
        // Fehlern derselben Zeile und schickt ihn dreimal in die Datei.
        var ergebnis = lies("""
                lotterie:
                  an: true
                  gewinne:
                    gut:
                      art: mark
                      gewicht: 10
                      menge: 50
                    dreifach:
                      art: silbermark
                      gewicht: 0
                      menge: 0
                """);

        assertEquals(1, ergebnis.klagen().size(), "EINE Klage fuer die eine Zeile");
        String klage = ergebnis.klagen().get(0);
        assertTrue(klage.contains("art"), klage);
        assertTrue(klage.contains("gewicht"), klage);
        assertTrue(klage.contains("menge"), klage);
    }

    @Test
    @DisplayName("Ein Gegenstandsgewinn ohne Gegenstand wird beklagt")
    void gegenstandOhneNamen() {
        var ergebnis = lies("""
                lotterie:
                  an: true
                  gewinne:
                    gut:
                      art: mark
                      gewicht: 10
                      menge: 50
                    leer:
                      art: gegenstand
                      gewicht: 5
                      menge: 1
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("gegenstand"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Ein Geldgewinn MIT Gegenstand wird beklagt - eine Angabe waere stumm")
    void geldMitGegenstand() {
        var ergebnis = lies("""
                lotterie:
                  an: true
                  gewinne:
                    gut:
                      art: mark
                      gewicht: 10
                      menge: 50
                    schief:
                      art: mark
                      gewicht: 5
                      menge: 100
                      gegenstand: diamond
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("stumm ignoriert"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Ein Hoechstpreis unter dem Grundpreis wird beklagt, nicht geworfen")
    void deckelUnterGrundpreis() {
        // Beide Zahlen sind EINZELN erlaubt. Ein Wurf aus dem Konstruktor waere hier ein
        // abgebrochener Serverstart wegen zweier Zeilen, die fuer sich in Ordnung sind.
        //
        // sprung-bestaetigt fuer beide: 100 statt 500 und 200 statt 250.000 sind weit
        // mehr als Faktor 2. Ohne diese Zeilen verwirft der Sprungschutz sie, und die
        // Probe prueft am Ende die Vorgabe.
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    lotterie-grundpreis: "500 -> 100"
                    lotterie-hoechstpreis: "250000 -> 200"
                lotterie:
                  an: true
                  grundpreis: 100
                  hoechstpreis: 200
                  gewinne:
                    gut:
                      art: mark
                      gewicht: 10
                      menge: 50
                """);

        // Hier ist der Deckel UEBER dem Grundpreis - also noch keine Klage. Der Satz
        // darunter dreht genau das um, und nur das.
        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertEquals(100L, ergebnis.werte().grundpreis());
        assertEquals(200L, ergebnis.werte().hoechstpreis());

        var verdreht = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    lotterie-grundpreis: "500 -> 200"
                    lotterie-hoechstpreis: "250000 -> 100"
                lotterie:
                  an: true
                  grundpreis: 200
                  hoechstpreis: 100
                  gewinne:
                    gut:
                      art: mark
                      gewicht: 10
                      menge: 50
                """);

        assertEquals(1, verdreht.klagen().size(), verdreht.klagen().toString());
        assertTrue(verdreht.klagen().get(0).contains("hoechstpreis"),
                verdreht.klagen().get(0));
        assertEquals(200L, verdreht.werte().grundpreis());
        assertEquals(200L, verdreht.werte().hoechstpreis(),
                "geheilt wird nach OBEN - der Grundpreis gilt dann als Deckel");
    }

    @Test
    @DisplayName("Eine Tagesgrenze unter den gratis Losen wird beklagt, nicht geworfen")
    void grenzeUnterGratis() {
        var ergebnis = lies("""
                lotterie:
                  an: true
                  freie-lose-je-tag: 5
                  hoechste-lose-je-tag: 3
                  gewinne:
                    gut:
                      art: mark
                      gewicht: 10
                      menge: 50
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("nie zu ziehen"),
                ergebnis.klagen().get(0));
        assertEquals(3, ergebnis.werte().freieLoseJeTag(),
                "geheilt wird nach UNTEN - sonst waere ein gratis Los unerreichbar");
        assertEquals(3, ergebnis.werte().hoechsteLoseJeTag());
    }

    @Test
    @DisplayName("Eine Steigerung unter 100 Prozent wird beklagt")
    void steigerungUnterHundert() {
        var ergebnis = lies("""
                lotterie:
                  an: true
                  steigerung-prozent: 80
                  gewinne:
                    gut:
                      art: mark
                      gewicht: 10
                      menge: 50
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertEquals(Lotteriewerte.VORGABE_STEIGERUNG,
                ergebnis.werte().steigerungProzent(), "es bleibt bei der Vorgabe");
    }

    @Test
    @DisplayName("Ein fehlender Abschnitt ist keine Klage, aber eine Berichtzeile")
    void abschnittFehlt() {
        // Auf einem Server, der schon lief, schreibt saveDefaultConfig die Datei nie um -
        // die Zeilen fehlen dort einfach, und es gilt die Lotterie des Programms.
        var ergebnis = lies("parzellen:\n  hoechstzahl: 1\n");

        assertEquals(List.of(), ergebnis.klagen());
        assertEquals(Lotteriewerte.VORGABE, ergebnis.werte());
        assertTrue(ergebnis.bericht().stream().anyMatch(z -> z.contains("fehlt")),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Eine LEERE Gewinnliste heisst: es wird nicht gezogen")
    void leereListe() {
        // Etwas anderes als ein fehlender Abschnitt: Wer alle Gewinne herausnimmt, will
        // keine Lotterie und soll sie nicht bekommen, ohne den Schalter zu finden.
        var ergebnis = lies("""
                lotterie:
                  an: true
                  gewinne: {}
                """);

        assertFalse(ergebnis.werte().greift(), "ohne Gewinne wird nicht gezogen");
        assertEquals(0, ergebnis.werte().gewinne().size());
    }

    @Test
    @DisplayName("Jede Zeile kaputt: die Vorgabe gilt NICHT, aber der Bericht sagt es")
    void jedeZeileKaputt() {
        // Die Vorgabe waere hier falsch: Der Betreiber hat eine eigene Tabelle gewollt,
        // und mit der Vorgabe verteilte die Lotterie Gewinne, die er gerade
        // herausgenommen hat.
        var ergebnis = lies("""
                lotterie:
                  an: true
                  gewinne:
                    eins:
                      art: unbekannt
                      gewicht: 5
                      menge: 1
                    zwei:
                      art: gegenstand
                      gewicht: 5
                      menge: 1
                      gegenstand: gibtsnichtauch
                """);

        assertEquals(2, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertEquals(0, ergebnis.werte().gewinne().size());
        assertTrue(ergebnis.bericht().stream()
                        .anyMatch(z -> z.contains("keine einzige Zeile")),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Ein Schalter, der kein ja/nein ist, wird beklagt")
    void schalterIstKeinJaNein() {
        var ergebnis = lies("""
                lotterie:
                  an: vielleicht
                  gewinne:
                    gut:
                      art: mark
                      gewicht: 10
                      menge: 50
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("ja/nein"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Eine Zeile, die gar kein Abschnitt ist, wird beklagt")
    void zeileIstKeinAbschnitt() {
        // Der haeufigste Tippfehler: "trost: 80" statt eines Abschnitts mit art,
        // gewicht und menge.
        var ergebnis = lies("""
                lotterie:
                  an: true
                  gewinne:
                    trost: 80
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("kein Abschnitt"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Die Rechenprobe nennt die Staffel UND den mittleren Gewinn")
    void rechenprobe() {
        // Die einzige Schranke gegen 50 statt 500 - keine Zahlengrenze faengt das.
        // Und der mittlere Gewinn ist die einzige Zahl, an der ein Betreiber sieht, ob
        // seine Lotterie eine Senke ist.
        String text = Lotterieliste.rechenprobe(Lotteriewerte.VORGABE);

        assertTrue(text.contains("1. gratis"), text);
        assertTrue(text.contains("Im Mittel"), text);
        assertTrue(text.contains("Promille"), text);
        assertTrue(text.contains("meeresherz"), text);
    }

    @Test
    @DisplayName("Die Rechenprobe einer ausgeschalteten Lotterie sagt AUS")
    void rechenprobeAus() {
        assertTrue(Lotterieliste.rechenprobe(Lotteriewerte.VORGABE.mitAn(false))
                .contains("AUS"));
    }

    @Test
    @DisplayName("Ein Anzeigename bei einem Geldgewinn wird beklagt")
    void namenBeiGeld() {
        var ergebnis = lies("""
                lotterie:
                  an: true
                  gewinne:
                    gut:
                      art: mark
                      gewicht: 10
                      menge: 50
                    schief:
                      art: goldmark
                      gewicht: 5
                      menge: 2
                      name: Goldschatz
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("keinen Namen"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Mehr Stueck als ein Rucksack fasst wird beklagt")
    void zuVieleStueck() {
        var ergebnis = lies("""
                lotterie:
                  an: true
                  gewinne:
                    gut:
                      art: mark
                      gewicht: 10
                      menge: 50
                    zuviel:
                      art: gegenstand
                      gewicht: 5
                      menge: 99999
                      gegenstand: diamond
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("Rucksack"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Ein Gewinnname mit grossen Buchstaben wird kleingeschrieben gelesen")
    void gegenstandWirdKleingeschrieben() {
        // Der Betreiber tippt DIAMOND, das Spiel kennt diamond. Dieselbe Milde wie bei
        // der Angeltabelle - der SCHLUESSEL der Zeile bleibt dagegen, wie er dasteht:
        // Er ist der Name im Protokoll und in der Sprachdatei.
        var ergebnis = lies("""
                lotterie:
                  an: true
                  gewinne:
                    Edelstein:
                      art: gegenstand
                      gewicht: 5
                      menge: 1
                      gegenstand: DIAMOND
                """);

        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertEquals("diamond",
                ergebnis.werte().gewinn("Edelstein").orElseThrow().gegenstand());
    }
}
