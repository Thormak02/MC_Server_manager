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

/** Eine Verlaufszeile - ohne Server und ohne Zufall. */
class VerlaufszeileTest {

    private static final UUID ANNA = UUID.nameUUIDFromBytes("anna".getBytes());
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** 02.11.2026, 13:37 UTC - in Berlin ist das 14:37 (Winterzeit). */
    private static final Instant MITTAG = Instant.parse("2026-11-02T13:37:00Z");

    private static Buchung buchung(Buchungsgrund grund, long betrag, long nachher) {
        return new Buchung(1L, MITTAG, ANNA, grund, betrag, nachher);
    }

    @Test
    @DisplayName("Eine Ausgabe steht mit Minus, der Stand dahinter")
    void ausgabe() {
        String text = Verlaufszeile.text(
                buchung(Buchungsgrund.PARZELLE_GEKAUFT, -25_000L, 25_000L), BERLIN);

        assertEquals("02.11. 14:37  -25.000 Mark  Parzelle genommen  (Stand 25.000 Mark)",
                text);
    }

    @Test
    @DisplayName("Eine Einnahme steht mit Plus")
    void einnahme() {
        // Das Plus steht ausdruecklich da: Ohne es muesste ein Spieler aus dem Grund
        // erraten, in welche Richtung das Geld floss - und bei den Erstattungen heissen
        // Hin und Zurueck fast gleich.
        String text = Verlaufszeile.text(
                buchung(Buchungsgrund.PARZELLE_ERSTATTET, 25_000L, 50_000L), BERLIN);

        assertTrue(text.contains("+25.000 Mark"), text);
    }

    @Test
    @DisplayName("Null bekommt kein Vorzeichen - die erste Parzelle ist gratis")
    void nullOhneVorzeichen() {
        String text = Verlaufszeile.text(
                buchung(Buchungsgrund.PARZELLE_GEKAUFT, 0L, 50_000L), BERLIN);

        assertTrue(text.contains("  0 Mark  "), text);
        assertTrue(!text.contains("+0") && !text.contains("-0"), text);
    }

    @Test
    @DisplayName("Die Zeit wird in die Zone des Lesers gerechnet, nicht in UTC gezeigt")
    void zoneWirdGerechnet() {
        // Der Zeitpunkt steht in UTC - so wie jeder Zeitpunkt in dieser Ablage. Ein
        // Spieler liest Ortszeit. Ohne Umrechnung waere jede Buchung eine Stunde zu
        // frueh, im Sommer zwei.
        var zeile = buchung(Buchungsgrund.EROEFFNUNG, 50_000L, 50_000L);

        assertTrue(Verlaufszeile.text(zeile, BERLIN).startsWith("02.11. 14:37"),
                Verlaufszeile.text(zeile, BERLIN));
        assertTrue(Verlaufszeile.text(zeile, ZoneId.of("UTC")).startsWith("02.11. 13:37"));
        assertTrue(Verlaufszeile.text(zeile, ZoneId.of("Pacific/Auckland"))
                        .startsWith("03.11."),
                "jenseits der Datumsgrenze ist es schon der naechste Tag");
    }

    @Test
    @DisplayName("Jeder Grund hat ein deutsches Wort")
    void jederGrundHatEinWort() {
        // Die Woerter stehen hier und nicht in der Datenbank - dort liegt der
        // ASCII-Schluessel. Wer die Wortwahl in die Spalte schriebe, koennte sie spaeter
        // nicht mehr aendern, ohne alte Zeilen unlesbar zu machen.
        for (Buchungsgrund grund : Buchungsgrund.values()) {
            String wort = Verlaufszeile.wort(grund);
            assertTrue(wort != null && !wort.isBlank(), "ohne Wort: " + grund);
            assertTrue(!wort.equals(grund.schluessel()),
                    "der Schluessel ist kein Wort fuer Spieler: " + grund);
        }
    }
}
