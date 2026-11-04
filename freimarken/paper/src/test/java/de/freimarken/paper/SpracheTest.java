package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der eine Ausgang nach Adventure.
 *
 * <p>Die drei Zusagen, die hier haengen: Ein eingesetzter Wert wird von keinem zweiten Lauf
 * mehr angesehen; ein vergessener Wert ist LAUT und bricht nichts ab; und die Zahlform ist
 * nebenlaeufig haltbar, weil nichts sie teilt.
 */
class SpracheTest {

    private static Satz satzZu(String pfad, String... platzhalter) {
        return Sprachprobe.Probesatz.von(pfad, Satz.Art.SATZ, platzhalter);
    }

    @Test
    @DisplayName("26 - ein Wert kommt zeichengleich an, mit Klammern und Tags darin")
    void werteInEinemDurchgang() {
        // Gemessen an Adventure 5.2.0: MiniMessage liest den WORTLAUT, replaceText ersetzt
        // danach in EINEM Baumdurchlauf durch BLAETTER. Waere der Wert erst gespleisst und
        // dann geparst, kaeme ein <red> aus einer Spielereingabe als wirklich rot beim
        // Spieler an - eine Textfaelschung aus einer fremden Meldung.
        Satz satz = satzZu("probe.wert", "meldung");
        Sprache sprache = new Sprache(new Sprachstand(Sprachprobe.buch(List.of(satz),
                "probe.wert", "<gray>Lesefehler: {meldung}</gray>")));
        String fremd = "expected <block end>, but found '}' - dazu {betrag}, <red>rot</red>"
                + " und ein Backslash \\";

        var zeilen = sprache.bau(satz, 1L, Einsatz.von("meldung", fremd));

        assertEquals(1, zeilen.size());
        assertEquals("Lesefehler: " + fremd, Sprachprobe.nurText(zeilen.get(0)));
        // Und als Bild: die Farbe des SATZES gilt, die aus dem Wert nicht.
        assertEquals("&7Lesefehler: " + fremd, Sprachprobe.bild(zeilen.get(0)));
    }

    @Test
    @DisplayName("27 - ein vergessener Wert ist laut und sendet den Satz mit Klammern")
    void vergessenerWertIstLautUndSendetDenAusgelieferten() {
        Satz satz = satzZu("probe.vergessen", "zeit", "betrag");
        Sprache sprache = new Sprache(new Sprachstand(Sprachprobe.buch(List.of(satz),
                "probe.vergessen", "<gray>{zeit} und {betrag}</gray>")));

        var zeilen = sprache.bau(satz, 1L, Einsatz.von("zeit", "14:37"));

        // Nicht abbrechen: Der Spieler sieht {betrag} und weiss, dass etwas kaputt ist;
        // bei einem abgebrochenen Befehl wuesste er nur, dass nichts geschieht.
        assertEquals(1, zeilen.size());
        String text = Sprachprobe.nurText(zeilen.get(0));
        assertTrue(text.contains("{betrag}"), text);
        assertFalse(text.isBlank(), "eine leere Chatzeile waere der eine Fehler, den"
                + " niemand mehr zuordnet");
    }

    @Test
    @DisplayName("28 - eine Umbruchmarke wird zu mehreren Zeilen, mit Einzug und Farbe")
    void umbruchWirdZuMehrerenZeilen() {
        // Der Server kennt die Chatbreite des Clients nicht, kann also nicht selbst
        // brechen. Mit der Umbruchmarke bleibt die Zeilenstruktur eine Entscheidung des
        // Betreibers - und die Farbe gilt fuer alle Zeilen, weil das Tag den ganzen Wert
        // umschliesst.
        Satz satz = satzZu("probe.drei");
        Sprache sprache = new Sprache(new Sprachstand(Sprachprobe.buch(List.of(satz),
                "probe.drei", "<gray>  Der Weg wird dein Bauland.\n  Beim Auftrennen"
                        + " kommt er zurueck.</gray>")));

        var zeilen = sprache.bau(satz, 1L, Einsatz.keine());

        assertEquals(2, zeilen.size(), "bekommen: " + Sprachprobe.bild(zeilen));
        assertEquals("&7  Der Weg wird dein Bauland.", Sprachprobe.bild(zeilen.get(0)));
        assertEquals("&7  Beim Auftrennen kommt er zurueck.",
                Sprachprobe.bild(zeilen.get(1)), "der Einzug der Folgezeile steht in der"
                        + " Datei, hinter der Umbruchmarke");
    }

    @Test
    @DisplayName("29 - zehn Faeden formatieren zeichengleich")
    void zweiFaedenFormatierenGleich() throws InterruptedException {
        // Verglichen wird das EXAKTE Ergebnis und nicht ein Muster: Ein Muster wie
        // ^[0-9.]+ Mark$ erfuellt auch der Schrott eines geteilten DecimalFormat
        // ("1.0001.000 Mark" fuer 1000). Jetzt ist die Probe nicht wahrscheinlich, sondern
        // bestimmt rot.
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        List<Long> zahlen = List.of(0L, 1L, 1000L, 25_000L, 400_000L, 1_234_567_890L);
        List<String> erwartet = List.of("0 Mark", "1 Mark", "1.000 Mark", "25.000 Mark",
                "400.000 Mark", "1.234.567.890 Mark");

        List<String> fehler = new CopyOnWriteArrayList<>();
        CountDownLatch los = new CountDownLatch(1);
        CountDownLatch fertig = new CountDownLatch(10);
        List<Thread> faeden = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Thread faden = new Thread(() -> {
                try {
                    los.await();
                    for (int lauf = 0; lauf < 200; lauf++) {
                        for (int n = 0; n < zahlen.size(); n++) {
                            String gebaut = sprache.betrag(zahlen.get(n));
                            if (!erwartet.get(n).equals(gebaut)) {
                                fehler.add(zahlen.get(n) + " wurde \"" + gebaut
                                        + "\", erwartet \"" + erwartet.get(n) + "\"");
                            }
                        }
                    }
                } catch (InterruptedException unterbrochen) {
                    Thread.currentThread().interrupt();
                } finally {
                    fertig.countDown();
                }
            });
            faeden.add(faden);
            faden.start();
        }
        los.countDown();
        assertTrue(fertig.await(60L, TimeUnit.SECONDS), "die Faeden sind nicht fertig"
                + " geworden");
        for (Thread faden : faeden) {
            faden.join();
        }
        assertEquals(List.of(), fehler);
    }

    @Test
    @DisplayName("29b - kein Zwischenspeicher fuer Zahlen, und Sprache hat ein Feld")
    void keinZwischenspeicherFuerZahlen() throws IOException {
        // Die Strukturzusage, ohne die "es gibt kein Feld, das sie teilen koennten" eine
        // Behauptung bleibt. Sie wird rot, sobald jemand wieder zwischenspeichert - so wie
        // Betraege es bis zum 04.11.2026 tat.
        List<String> gefunden = new ArrayList<>();
        for (Path quelle : Quellen.alle()) {
            int nummer = 0;
            for (String zeile : Files.readAllLines(quelle)) {
                nummer++;
                // Genau vier Leerzeichen Einzug: In diesem Quelltext stehen Felder auf
                // dieser Ebene und oertliche Veraenderliche auf acht oder mehr.
                if (zeile.matches("^ {4}(private |protected |public |static |final |)*"
                        + "(NumberFormat|DecimalFormat)\\s+\\w+.*")) {
                    gefunden.add(quelle.getFileName() + ":" + nummer + " " + zeile.trim());
                }
            }
        }
        assertEquals(List.of(), gefunden, "ein geteiltes NumberFormat liefert"
                + " nebenlaeufig keinen falschen Betrag, sondern Schrott");

        List<String> felder = new ArrayList<>();
        for (Field feld : Sprache.class.getDeclaredFields()) {
            if (!Modifier.isStatic(feld.getModifiers())) {
                felder.add(Modifier.toString(feld.getModifiers()) + " "
                        + feld.getType().getSimpleName() + " " + feld.getName());
            }
        }
        assertEquals(List.of("private final Sprachstand stand"), felder,
                "Sprache hat genau EIN Feld, und das ist der Halter - was neuladbar ist,"
                        + " wird beim Gebrauch gefragt und nie beim Bauen"
                        + " festgeschrieben");
    }
}
