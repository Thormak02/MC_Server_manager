package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Wettbewerbswerte;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Leser des Angelwettbewerbs - und die Uhrzeit, die YAML nicht als Text liest.
 *
 * <p><b>Die UHRZEIT ist das Besondere an dieser Datei.</b> Ein unquotiertes
 * {@code 18:00} ist in YAML kein Text, sondern der Anfang einer Abbildung - der Leser
 * bekommt dann eine {@code Map} und keine Zeichenkette. Die ausgelieferte Datei schreibt
 * die Zeit darum in Anfuehrungszeichen, und die Klage nennt genau diesen Grund. Hier
 * stehen beide Faelle.
 */
@DisplayName("Der Leser des Angelwettbewerbs")
class WettbewerbslisteTest {

    private static Wettbewerbsliste.Ergebnis lies(String yaml) {
        return Wettbewerbsliste.ausKonfiguration(
                YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml)));
    }

    @Test
    @DisplayName("Ein vollstaendiger Abschnitt kommt vollstaendig an")
    void vollstaendig() {
        // MIT sprung-bestaetigt fuer BEIDE Geldwerte: 100 statt 250 und 500 statt 0
        // sind mehr als Faktor 2, und ohne die Bestaetigung verwirft der Sprungschutz
        // sie - dann pruefte diese Probe am Ende die VORGABE statt das, was sie
        // eingetippt hat. Beim Zuschuss ist es besonders scharf: Ab Werk ist er 0, und
        // von 0 aus ist JEDE Zahl ein Sprung ueber das Doppelte.
        var ergebnis = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    wettbewerb-startgeld: "250 -> 100"
                    wettbewerb-zuschuss: "0 -> 500"
                wettbewerb:
                  an: true
                  beginn: "07:30"
                  dauer-minuten: 90
                  tage: [freitag, samstag]
                  startgeld: 100
                  zuschuss: 500
                  verteilung-prozent: [60, 40]
                  punkte-je-fang: 2
                  punkte:
                    cod: 5
                    salmon: 7
                """);

        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        Wettbewerbswerte werte = ergebnis.werte();
        assertTrue(werte.an());
        assertEquals(LocalTime.of(7, 30), werte.beginn());
        assertEquals(Duration.ofMinutes(90), werte.dauer());
        assertEquals(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), werte.tage());
        assertEquals(100L, werte.startgeld());
        assertEquals(500L, werte.zuschuss());
        assertEquals(List.of(60, 40), werte.verteilungProzent());
        assertEquals(2, werte.punkteJeFang());
        assertEquals(5, werte.punkteFuer("cod"));
        assertEquals(7, werte.punkteFuer("salmon"));
    }

    @Test
    @DisplayName("Eine Uhrzeit OHNE Anfuehrungszeichen wird beklagt - mit dem GRUND")
    void uhrzeitOhneAnfuehrungszeichen() {
        // DIE Falle dieser Datei: YAML macht aus 18:00 eine Abbildung. Der Betreiber
        // sieht dann eine Klage ueber seine Uhrzeit und sucht den Fehler in der Zahl -
        // er steht aber in den Zeichen darum. Darum nennt die Klage sie.
        var ergebnis = lies("""
                wettbewerb:
                  an: true
                  beginn: 18:00
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        String klage = ergebnis.klagen().get(0);
        assertTrue(klage.contains("Anfuehrungszeichen"), klage);
        assertTrue(klage.contains("Doppelpunkt"), klage);
        assertEquals(Wettbewerbswerte.VORGABE_BEGINN, ergebnis.werte().beginn());
    }

    @Test
    @DisplayName("Eine Uhrzeit, die keine ist, wird beklagt")
    void keineUhrzeit() {
        var ergebnis = lies("""
                wettbewerb:
                  an: true
                  beginn: "abends"
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("keine Uhrzeit"),
                ergebnis.klagen().get(0));
        assertEquals(Wettbewerbswerte.VORGABE_BEGINN, ergebnis.werte().beginn());
    }

    @Test
    @DisplayName("Eine Dauer ausserhalb der Grenzen wird beklagt")
    void dauerAusserhalb() {
        var zuLang = lies("""
                wettbewerb:
                  an: true
                  dauer-minuten: 1441
                """);
        assertEquals(1, zuLang.klagen().size(), zuLang.klagen().toString());
        assertEquals(Wettbewerbswerte.VORGABE_DAUER, zuLang.werte().dauer());

        var zuKurz = lies("""
                wettbewerb:
                  an: true
                  dauer-minuten: 0
                """);
        assertEquals(1, zuKurz.klagen().size(), zuKurz.klagen().toString());
        assertEquals(Wettbewerbswerte.VORGABE_DAUER, zuKurz.werte().dauer());
    }

    @Test
    @DisplayName("Ein unbekannter Wochentag kostet NUR SEINEN Eintrag")
    void unbekannterTag() {
        var ergebnis = lies("""
                wettbewerb:
                  an: true
                  tage: [freitag, donnerstagabend, samstag]
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("donnerstagabend"),
                ergebnis.klagen().get(0));
        assertEquals(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
                ergebnis.werte().tage(), "die beiden guten gelten");
    }

    @Test
    @DisplayName("Wochentage werden auch GROSS geschrieben gelesen")
    void tageGrossGeschrieben() {
        var ergebnis = lies("""
                wettbewerb:
                  an: true
                  tage: [Freitag, SAMSTAG]
                """);

        assertEquals(List.of(), ergebnis.klagen(), ergebnis.klagen().toString());
        assertEquals(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
                ergebnis.werte().tage());
    }

    @Test
    @DisplayName("Eine LEERE Tagesliste heisst: kein Wettbewerb")
    void leereTage() {
        // Etwas anderes als ein fehlender Schluessel (dann gelten alle sieben): Wer die
        // Liste leert, will den Wettbewerb anhalten, ohne den Schalter zu finden.
        var ergebnis = lies("""
                wettbewerb:
                  an: true
                  tage: []
                """);

        assertEquals(Set.of(), ergebnis.werte().tage());
        assertFalse(ergebnis.werte().greift());
    }

    @Test
    @DisplayName("Eine kaputte Zahl kostet die GANZE Verteilung")
    void kaputteVerteilung() {
        // Anders als bei den Punkten: Die Plaetze haengen voneinander ab. Faellt der
        // zweite weg, bekaeme der Dritte den Preis des Zweiten, und niemand hat es
        // gelesen.
        var ergebnis = lies("""
                wettbewerb:
                  an: true
                  verteilung-prozent: [50, viel, 20]
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("GANZE"),
                ergebnis.klagen().get(0));
        assertEquals(Wettbewerbswerte.VORGABE_VERTEILUNG,
                ergebnis.werte().verteilungProzent());
    }

    @Test
    @DisplayName("Eine Verteilung ueber 100 Prozent wird beklagt")
    void verteilungUeberHundert() {
        var ergebnis = lies("""
                wettbewerb:
                  an: true
                  verteilung-prozent: [70, 50]
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("mehr als den Topf"),
                ergebnis.klagen().get(0));
        assertEquals(Wettbewerbswerte.VORGABE_VERTEILUNG,
                ergebnis.werte().verteilungProzent());
    }

    @Test
    @DisplayName("Ein Gegenstand, den der Server nicht kennt, kostet NUR SEINE Zeile")
    void kaputtePunktzeile() {
        var ergebnis = lies("""
                wettbewerb:
                  an: true
                  punkte:
                    cod: 5
                    gibtsnicht: 3
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertEquals(5, ergebnis.werte().punkteFuer("cod"),
                "die brauchbare Zeile gilt");
        assertEquals(1, ergebnis.werte().punkte().size());
    }

    @Test
    @DisplayName("Punkte je Fang 0 und leere Tabelle: eine Berichtzeile sagt es")
    void keinFangZaehlt() {
        // Kein Tippfehler, den man erraten kann - aber eine Lage, die der Betreiber
        // kennen soll: Sein Fenster geht auf, und jeder Lauf endet ohne Punkte.
        var ergebnis = lies("""
                wettbewerb:
                  an: true
                  punkte-je-fang: 0
                  punkte: {}
                """);

        assertEquals(List.of(), ergebnis.klagen());
        assertTrue(ergebnis.bericht().stream()
                        .anyMatch(z -> z.contains("zaehlt kein Fang")),
                ergebnis.bericht().toString());
        assertFalse(ergebnis.werte().greift());
    }

    @Test
    @DisplayName("Ein fehlender Abschnitt ist keine Klage, aber eine Berichtzeile")
    void abschnittFehlt() {
        var ergebnis = lies("parzellen:\n  hoechstzahl: 1\n");

        assertEquals(List.of(), ergebnis.klagen());
        assertEquals(Wettbewerbswerte.VORGABE, ergebnis.werte());
        assertTrue(ergebnis.bericht().stream().anyMatch(z -> z.contains("fehlt")),
                ergebnis.bericht().toString());
    }

    @Test
    @DisplayName("Die Rechenprobe nennt das ENDE des Fensters")
    void rechenprobeNenntDasEnde() {
        // Aus "18:00" und "600 Minuten" das Ende zu rechnen macht niemand im Kopf - und
        // ein Fenster, das bis 4:00 des naechsten Tages reicht, sieht in der Datei aus
        // wie eines von einer Stunde.
        var ergebnis = lies("""
                wettbewerb:
                  an: true
                  beginn: "18:00"
                  dauer-minuten: 600
                """);

        String text = Wettbewerbsliste.rechenprobe(ergebnis.werte());

        assertTrue(text.contains("18:00"), text);
        assertTrue(text.contains("04:00"), text);
        assertTrue(text.contains("des naechsten Tages"), text);
    }

    @Test
    @DisplayName("Die Rechenprobe nennt die Tage - und 'jedem Tag', wenn es alle sind")
    void rechenprobeNenntDieTage() {
        assertTrue(Wettbewerbsliste.rechenprobe(Wettbewerbswerte.VORGABE)
                .contains("jedem Tag"));

        var ergebnis = lies("""
                wettbewerb:
                  an: true
                  tage: [freitag, sonntag]
                """);
        String text = Wettbewerbsliste.rechenprobe(ergebnis.werte());
        assertTrue(text.contains("friday/sunday"), text);
    }

    @Test
    @DisplayName("Die Rechenprobe eines ausgeschalteten Wettbewerbs sagt AUS")
    void rechenprobeAus() {
        assertTrue(Wettbewerbsliste.rechenprobe(Wettbewerbswerte.VORGABE.mitAn(false))
                .contains("AUS"));
    }

    @Test
    @DisplayName("Ein Schalter, der kein ja/nein ist, wird beklagt")
    void schalterIstKeinJaNein() {
        var ergebnis = lies("""
                wettbewerb:
                  an: manchmal
                  beginn: "18:00"
                """);

        assertEquals(1, ergebnis.klagen().size(), ergebnis.klagen().toString());
        assertTrue(ergebnis.klagen().get(0).contains("ja/nein"),
                ergebnis.klagen().get(0));
    }

    @Test
    @DisplayName("Ein Zuschuss ueber Faktor 2 braucht sprung-bestaetigt")
    void zuschussBrauchtBestaetigung() {
        // Ab Werk ist der Zuschuss 0 - von 0 aus ist JEDE Zahl ein Sprung ueber das
        // Doppelte. Das ist nicht im Weg, sondern richtig: Ein Zuschuss ist neues Geld,
        // und das soll bewusst entstehen.
        var ohne = lies("""
                wettbewerb:
                  an: true
                  zuschuss: 5000
                """);
        assertEquals(0L, ohne.werte().zuschuss(),
                "ohne Bestaetigung verwirft der Sprungschutz ihn");

        var mit = lies("""
                wirtschaft:
                  sprung-bestaetigt:
                    wettbewerb-zuschuss: "0 -> 5000"
                wettbewerb:
                  an: true
                  zuschuss: 5000
                """);
        assertEquals(5_000L, mit.werte().zuschuss(), "mit Bestaetigung gilt er");
    }
}
