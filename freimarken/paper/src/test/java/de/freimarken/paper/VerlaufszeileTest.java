package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Eine Verlaufszeile - ohne Server und ohne Zufall.
 *
 * <p>Verglichen wird seit dem 04.11.2026 das BILD und nicht die Zeichenkette: Die Zeile ist
 * ein {@code Component}, weil Einzug und Farbe in den Satz {@code mark.verlauf.zeile}
 * gewandert sind. Die Zusicherungen sind woertlich dieselben geblieben - die ausgelieferte
 * Datei traegt die heutigen Saetze woertlich, und genau das ist hier zu zeigen.
 */
class VerlaufszeileTest {

    private static final UUID ANNA = UUID.nameUUIDFromBytes("anna".getBytes());
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** 02.11.2026, 13:37 UTC - in Berlin ist das 14:37 (Winterzeit). */
    private static final Instant MITTAG = Instant.parse("2026-11-02T13:37:00Z");

    private static Buchung buchung(Buchungsgrund grund, long betrag, long nachher) {
        return new Buchung(1L, MITTAG, ANNA, grund, betrag, nachher);
    }

    private static String bild(Buchung buchung, ZoneId zone) {
        return Sprachprobe.bild(
                Verlaufszeile.zeile(Sprachprobe.spracheAusDemJar(), buchung, zone));
    }

    @Test
    @DisplayName("Eine Ausgabe steht mit Minus, der Stand dahinter")
    void ausgabe() {
        String bild = bild(buchung(Buchungsgrund.PARZELLE_GEKAUFT, -25_000L, 25_000L),
                BERLIN);

        // Der Einzug von zwei Leerzeichen und das Grau stehen jetzt im Satz und nicht am
        // Sendeort - darum sind sie Teil dieser Zusicherung.
        assertEquals("&7  02.11. 14:37  -25.000 Mark  Parzelle genommen"
                + "  (Stand 25.000 Mark)", bild);
    }

    @Test
    @DisplayName("Eine Einnahme steht mit Plus")
    void einnahme() {
        // Das Plus steht ausdruecklich da: Ohne es muesste ein Spieler aus dem Grund
        // erraten, in welche Richtung das Geld floss - und bei den Erstattungen heissen
        // Hin und Zurueck fast gleich.
        String bild = bild(buchung(Buchungsgrund.PARZELLE_ERSTATTET, 25_000L, 50_000L),
                BERLIN);

        assertTrue(bild.contains("+25.000 Mark"), bild);
    }

    @Test
    @DisplayName("Null bekommt kein Vorzeichen - die erste Parzelle ist gratis")
    void nullOhneVorzeichen() {
        String bild = bild(buchung(Buchungsgrund.PARZELLE_GEKAUFT, 0L, 50_000L), BERLIN);

        assertTrue(bild.contains("  0 Mark  "), bild);
        assertTrue(!bild.contains("+0") && !bild.contains("-0"), bild);
    }

    @Test
    @DisplayName("Die Zeit wird in die Zone des Lesers gerechnet, nicht in UTC gezeigt")
    void zoneWirdGerechnet() {
        // Der Zeitpunkt steht in UTC - so wie jeder Zeitpunkt in dieser Ablage. Ein
        // Spieler liest Ortszeit. Ohne Umrechnung waere jede Buchung eine Stunde zu
        // frueh, im Sommer zwei.
        var zeile = buchung(Buchungsgrund.EROEFFNUNG, 50_000L, 50_000L);

        assertTrue(bild(zeile, BERLIN).startsWith("&7  02.11. 14:37"),
                bild(zeile, BERLIN));
        assertTrue(bild(zeile, ZoneId.of("UTC")).startsWith("&7  02.11. 13:37"));
        assertTrue(bild(zeile, ZoneId.of("Pacific/Auckland")).startsWith("&7  03.11."),
                "jenseits der Datumsgrenze ist es schon der naechste Tag");
    }

    @Test
    @DisplayName("Jeder Grund hat ein deutsches Wort")
    void jederGrundHatEinWort() {
        // Die Woerter stehen jetzt in der Sprachdatei und nicht in der Datenbank - dort
        // liegt der ASCII-Schluessel. Wer die Wortwahl in die Spalte schriebe, koennte sie
        // spaeter nicht mehr aendern, ohne alte Zeilen unlesbar zu machen. Der PFAD in der
        // Datei kommt deshalb aus dem Aufzaehlungsnamen und nicht aus dem Schluessel.
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        for (Buchungsgrund grund : Buchungsgrund.values()) {
            String wort = sprache.wort(Sprachschluessel.grund(grund), Einsatz.keine());
            assertTrue(wort != null && !wort.isBlank(), "ohne Wort: " + grund);
            assertTrue(!wort.equals(grund.schluessel()),
                    "der Schluessel ist kein Wort fuer Spieler: " + grund);
        }
    }
}
