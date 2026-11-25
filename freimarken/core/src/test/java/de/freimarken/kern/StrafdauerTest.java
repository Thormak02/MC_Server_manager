package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Dauer einer Strafe - und die Luecke, gegen die sie gebaut ist.
 *
 * <p>Die wichtigste Probe hier ist {@link #zuLangeIstKeinWort()}: Ein Moderator ohne das
 * Recht zum dauerhaften Bannen tippt {@code 36500d}. Das ist formal befristet und
 * praktisch endgueltig - und wenn das Wort durchkaeme, waere die Unterscheidung aus Plan
 * 12c.2 zwischen Moderator und Verwalter eine Zeile Papier.
 */
class StrafdauerTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");

    @Test
    @DisplayName("Sekunden, Minuten, Stunden und Tage werden gelesen")
    void vierEinheiten() {
        assertEquals(Duration.ofSeconds(30L),
                Strafdauer.ausWort("30s").orElseThrow().spanne());
        assertEquals(Duration.ofMinutes(10L),
                Strafdauer.ausWort("10m").orElseThrow().spanne());
        assertEquals(Duration.ofHours(12L),
                Strafdauer.ausWort("12h").orElseThrow().spanne());
        assertEquals(Duration.ofDays(7L),
                Strafdauer.ausWort("7d").orElseThrow().spanne());
    }

    @Test
    @DisplayName("dauerhaft ist unbegrenzt - und hat kein Ende")
    void dauerhaftIstUnbegrenzt() {
        Strafdauer ohne = Strafdauer.ausWort("dauerhaft").orElseThrow();

        assertTrue(ohne.unbegrenzt());
        assertTrue(ohne.endeAb(MITTAGS).isEmpty(),
                "leer heisst hier: kein Ende, nicht unbekannt");
        assertTrue(ohne.laengerAls(Long.MAX_VALUE),
                "eine Strafe ohne Ende ist laenger als jede Zahl");
    }

    @Test
    @DisplayName("Die Schreibweise ist gleichgueltig, der Rand wird abgeschnitten")
    void schreibweiseUndRand() {
        assertEquals(Duration.ofHours(1L),
                Strafdauer.ausWort("  1H  ").orElseThrow().spanne());
        assertTrue(Strafdauer.ausWort(" DAUERHAFT ").orElseThrow().unbegrenzt());
    }

    @Test
    @DisplayName("Eine Zahl OHNE Einheit wird abgewiesen und nicht geraten")
    void zahlOhneEinheit() {
        // Zehn Minuten oder zehn Tage? Der Unterschied ist der zwischen einer
        // Abkuehlung und einem verlorenen Monat. Geraten wird nicht.
        assertTrue(Strafdauer.ausWort("10").isEmpty());
        assertTrue(Strafdauer.ausWort("0").isEmpty());
    }

    @Test
    @DisplayName("Eine unbekannte Einheit wird abgewiesen")
    void unbekannteEinheit() {
        assertTrue(Strafdauer.ausWort("2w").isEmpty(), "Wochen gibt es nicht");
        assertTrue(Strafdauer.ausWort("3jahre").isEmpty());
        assertTrue(Strafdauer.ausWort("1d12h").isEmpty(),
                "mehrteilige Angaben gibt es nicht - wer anderthalb Tage will,"
                        + " tippt 36h");
    }

    @Test
    @DisplayName("Null und ein Minus sind keine Dauer")
    void nullUndMinus() {
        assertTrue(Strafdauer.ausWort("0m").isEmpty(),
                "eine Strafe von null Minuten ist keine");
        // parseLong nimmt ein Vorzeichen an - ohne die eigene Ziffernpruefung kaeme
        // "-10m" bis zum Konstruktor und wuerfe dort. Dieselbe Falle wie bei den
        // Marktnummern.
        assertTrue(Strafdauer.ausWort("-10m").isEmpty());
        assertTrue(Strafdauer.ausWort("+10m").isEmpty());
    }

    @Test
    @DisplayName("Leer, null und ein blosser Buchstabe sind keine Dauer")
    void leerUndUnsinn() {
        assertTrue(Strafdauer.ausWort(null).isEmpty());
        assertTrue(Strafdauer.ausWort("").isEmpty());
        assertTrue(Strafdauer.ausWort("   ").isEmpty());
        assertTrue(Strafdauer.ausWort("m").isEmpty());
        assertTrue(Strafdauer.ausWort("d").isEmpty());
    }

    @Test
    @DisplayName("Mehr als zehn Jahre ist kein brauchbares Wort")
    void zuLangeIstKeinWort() {
        // DIE PROBE DIESER KLASSE. Ohne diese Grenze tippt ein Moderator ohne das
        // Recht fuer dauerhafte Strafen eine Zahl, die seine Grenze formal einhaelt
        // und praktisch ueberschreitet.
        assertTrue(Strafdauer.ausWort("3650d").isPresent(), "zehn Jahre gehen noch");
        assertTrue(Strafdauer.ausWort("3651d").isEmpty());
        assertTrue(Strafdauer.ausWort("36500d").isEmpty());
        assertTrue(Strafdauer.ausWort("99999999h").isEmpty());
        assertTrue(Strafdauer.ausWort("99999999999999999999d").isEmpty(),
                "mehr Stellen, als in einen long passen");
    }

    @Test
    @DisplayName("Ein Ueberlauf gibt leer und WIRFT nicht")
    void ueberlaufGibtLeer() {
        // DER FUND VOM 25.11.2026: Der Fang umschloss nur parseLong, der switch mit
        // Duration.ofDays stand ausserhalb. Duration.ofDays rechnet mit
        // Math.multiplyExact und wirft eine ArithmeticException ("long overflow"),
        // BEVOR die Grenze darunter greift - der Wurf lief durch den Befehl hinaus zu
        // Brigadier, und der Moderator las eine rote englische Zeile statt der Absage
        // mit den Beispielen.
        //
        // Je EINHEIT geprueft: Nur "s" war gefahrlos, weil Duration.ofSeconds nicht
        // multipliziert. Eine Probe mit nur einer Einheit haette den Fund nicht
        // gehalten.
        for (String wort : new String[] {
                "999999999999999d", "9999999999999999h", "999999999999999999m",
                "9223372036854775807d", "9223372036854775807h",
                "9223372036854775807m", "9223372036854775807s"}) {
            assertTrue(Strafdauer.ausWort(wort).isEmpty(),
                    wort + " ist kein brauchbares Wort - und darf nicht werfen");
        }
    }

    @Test
    @DisplayName("Der Konstruktor weist null, Minus und zu lange Dauern ab")
    void konstruktorWeistAb() {
        assertThrows(IllegalArgumentException.class,
                () -> new Strafdauer(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new Strafdauer(Duration.ofMinutes(-10L)));
        assertThrows(IllegalArgumentException.class,
                () -> new Strafdauer(Duration.ofDays(3651L)));
    }

    @Test
    @DisplayName("Das Ende wird aus dem Verhaengungszeitpunkt gerechnet")
    void endeAusZeitpunkt() {
        Strafdauer stunde = Strafdauer.ausWort("1h").orElseThrow();

        assertEquals(Optional.of(MITTAGS.plusSeconds(3600L)), stunde.endeAb(MITTAGS));
        assertFalse(stunde.laengerAls(1L), "eine Stunde ist nicht laenger"
                + " als eine Stunde");
        assertTrue(stunde.laengerAls(0L));
        assertFalse(stunde.unbegrenzt());
        assertThrows(IllegalArgumentException.class, () -> stunde.endeAb(null));
    }

    @Test
    @DisplayName("Die Stunden runden ab - 59 Minuten sind null Stunden")
    void stundenRundenAb() {
        // Das ist fuer den Riegel die SICHERE Richtung: Wer 59 Minuten tippt, liegt
        // unter jeder Hoechstdauer. Aufrunden wuerde eine erlaubte Dauer verbieten.
        // DER BEHOBENE FEHLER [25.11.2026]: Vorher gab es stunden(), und das war
        // abschneidend - ofMinutes(59).toHours() ist 0. Der Riegel fragte
        // "stunden() > grenze", und bei der Grenze 0 war damit jede Dauer unter einer
        // Stunde erlaubt, obwohl die Datei zusagt, dass 0 alles sperrt.
        assertTrue(Strafdauer.ausWort("59m").orElseThrow().laengerAls(0L),
                "59 Minuten sind laenger als null Stunden - mit toHours() waren sie es"
                        + " nicht");
        assertFalse(Strafdauer.ausWort("59m").orElseThrow().laengerAls(1L));
        assertFalse(Strafdauer.ausWort("60m").orElseThrow().laengerAls(1L));
        assertTrue(Strafdauer.ausWort("61m").orElseThrow().laengerAls(1L));
        assertFalse(Strafdauer.ausWort("1d").orElseThrow().laengerAls(24L));
    }

    @Test
    @DisplayName("zwischen rechnet aus zwei Zeitpunkten eine Dauer")
    void zwischenRechnet() {
        assertEquals(Duration.ofHours(2L),
                Strafdauer.zwischen(MITTAGS, MITTAGS.plusSeconds(7200L)).spanne());
        assertTrue(Strafdauer.zwischen(MITTAGS, null).unbegrenzt(),
                "ohne Ende heisst unbegrenzt");
    }

    @Test
    @DisplayName("zwischen macht aus Anfang gleich Ende eine Sekunde")
    void zwischenBeiGleichstand() {
        // So wird ein RAUSWURF gespeichert: Ende gleich Anfang. Der Konstruktor darf
        // keine Dauer von null halten, und "0 Sekunden" liest sich wie ein Fehler.
        assertEquals(Duration.ofSeconds(1L),
                Strafdauer.zwischen(MITTAGS, MITTAGS).spanne());
        assertEquals(Duration.ofSeconds(1L),
                Strafdauer.zwischen(MITTAGS, MITTAGS.minusSeconds(60L)).spanne());
    }

    @Test
    @DisplayName("zwischen deckelt bei zehn Jahren statt zu werfen")
    void zwischenDeckelt() {
        // Eine Zeile aus der Datenbank, die laenger laeuft als die Grenze - etwa von
        // Hand geschrieben. Ein Wurf hier liesse die ganze Liste scheitern, und eine
        // Liste, die an einer Zeile zerbricht, ist schlimmer als eine ungenaue Zahl.
        assertEquals(Duration.ofDays(3650L),
                Strafdauer.zwischen(MITTAGS, MITTAGS.plus(Duration.ofDays(9000L)))
                        .spanne());
    }

    @Test
    @DisplayName("Die Vorschlaege sind brauchbare Woerter")
    void vorschlaegeSindBrauchbar() {
        // Eine Vervollstaendigung, die ein Wort anbietet, das der Befehl dann abweist,
        // ist schlimmer als keine: Der Moderator tippt sie im Vorfall und liest eine
        // Absage.
        for (String wort : Strafdauer.VORSCHLAEGE) {
            assertTrue(Strafdauer.ausWort(wort).isPresent(),
                    "der Vorschlag \"" + wort + "\" ist keine brauchbare Dauer");
        }
        assertTrue(Strafdauer.VORSCHLAEGE.contains(Strafdauer.WORT_UNBEGRENZT),
                "dauerhaft gehoert in die Liste - es ist das Wort, das man sonst nicht"
                        + " findet");
    }
}
