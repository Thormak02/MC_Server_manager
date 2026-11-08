package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Was im Abholfach liegt - der Stapel und seine Zeile. */
@DisplayName("Abholstueck und Abholgut")
class AbholstueckTest {

    private static final UUID ANNA =
            UUID.fromString("0000a44a-0000-0000-0000-000000000001");

    private static final Instant WANN = Instant.parse("2026-11-08T14:30:00Z");

    @Test
    @DisplayName("Ein Stueck traegt alle fuenf Werte und reicht zwei durch")
    void alleWerte() {
        Abholstueck stueck = new Abholstueck(7L, ANNA, "Laden halle-1", WANN,
                new Abholgut("64x Stein", "AAAA"));

        assertEquals(7L, stueck.id());
        assertEquals(ANNA, stueck.empfaenger());
        assertEquals("Laden halle-1", stueck.herkunft());
        assertEquals(WANN, stueck.eingelegt());
        // Die zwei kurzen Wege an gut() vorbei: Wer sie vertauscht, zeigt dem Spieler
        // Base64 statt "64x Stein" - und protokolliert "64x Stein" statt des Inhalts.
        assertEquals("64x Stein", stueck.beschriftung());
        assertEquals("AAAA", stueck.inhalt());
    }

    @Test
    @DisplayName("gehoert trennt den Empfaenger von jedem anderen")
    void gehoert() {
        Abholstueck stueck = new Abholstueck(1L, ANNA, "Laden halle-1", WANN,
                new Abholgut("64x Stein", "AAAA"));

        assertTrue(stueck.gehoert(ANNA));
        assertFalse(stueck.gehoert(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Die Nummer 0 ist keine")
    void nummerNullGehtNicht() {
        // Hart, weil eine Ablage, die beim Schreiben die Nummer vergisst, sonst ein Fach
        // voller Nullen liefert - und dann trifft jede Abfrage nach der 0 alle Stuecke.
        IllegalArgumentException fehler = assertThrows(IllegalArgumentException.class,
                () -> new Abholstueck(0L, ANNA, "Laden halle-1", WANN,
                        new Abholgut("64x Stein", "AAAA")));

        assertTrue(fehler.getMessage().contains("beginnt bei 1"), fehler.getMessage());
    }

    @Test
    @DisplayName("Ohne Empfaenger, Herkunft, Zeitpunkt oder Stapel geht es nicht")
    void pflichtwerte() {
        Abholgut gut = new Abholgut("64x Stein", "AAAA");

        assertThrows(IllegalArgumentException.class,
                () -> new Abholstueck(1L, null, "Laden halle-1", WANN, gut));
        assertThrows(IllegalArgumentException.class,
                () -> new Abholstueck(1L, ANNA, " ", WANN, gut));
        assertThrows(IllegalArgumentException.class,
                () -> new Abholstueck(1L, ANNA, "Laden halle-1", null, gut));
        assertThrows(IllegalArgumentException.class,
                () -> new Abholstueck(1L, ANNA, "Laden halle-1", WANN, null));
    }

    @Test
    @DisplayName("Eine zu lange Herkunft wird abgewiesen, die Grenze selbst nicht")
    void herkunftHatEineGrenze() {
        String grenze = "x".repeat(Abholstueck.LAENGSTE_HERKUNFT);
        assertEquals(grenze, new Abholstueck(1L, ANNA, grenze, WANN,
                new Abholgut("64x Stein", "AAAA")).herkunft());

        assertThrows(IllegalArgumentException.class,
                () -> new Abholstueck(1L, ANNA, grenze + "x", WANN,
                        new Abholgut("64x Stein", "AAAA")));
    }

    @Test
    @DisplayName("Die Herkunft wird getrimmt, der INHALT nicht")
    void trimmen() {
        Abholstueck stueck = new Abholstueck(1L, ANNA, "  Laden halle-1  ", WANN,
                new Abholgut("  64x Stein  ", "  AAAA  "));

        assertEquals("Laden halle-1", stueck.herkunft());
        assertEquals("64x Stein", stueck.beschriftung());
        // Der Inhalt NICHT: Was die Schicht darueber geschrieben hat, kommt Zeichen fuer
        // Zeichen zurueck. Ein Trimmen waere eine stille Aenderung an Daten, die der Kern
        // nicht versteht - und ein Gegenstand, der danach nicht mehr zu lesen ist.
        assertEquals("  AAAA  ", stueck.inhalt());
    }

    @Test
    @DisplayName("Ein Stapel ohne Beschriftung oder ohne Inhalt ist keiner")
    void gutBrauchtBeides() {
        assertThrows(IllegalArgumentException.class, () -> new Abholgut(null, "AAAA"));
        assertThrows(IllegalArgumentException.class, () -> new Abholgut(" ", "AAAA"));
        assertThrows(IllegalArgumentException.class,
                () -> new Abholgut("64x Stein", null));
        // Ein LEERER Inhalt und nicht bloss null: Eine Zeile im Fach, die keinen
        // Gegenstand ergibt, ist einer, den der Spieler nie bekommt - und die der
        // Aufrufer trotzdem als geholt loescht.
        assertThrows(IllegalArgumentException.class, () -> new Abholgut("64x Stein", ""));
    }

    @Test
    @DisplayName("Eine zu lange Beschriftung und ein zu langer Inhalt werden abgewiesen")
    void gutHatGrenzen() {
        String beschriftung = "x".repeat(Abholgut.LAENGSTE_BESCHRIFTUNG);
        assertEquals(beschriftung,
                new Abholgut(beschriftung, "AAAA").beschriftung());
        assertThrows(IllegalArgumentException.class,
                () -> new Abholgut(beschriftung + "x", "AAAA"));

        // Die Inhaltsgrenze liegt UNTER dem, was die Spalte haelt. Faellt ein Stapel
        // hier auf, wird er liegen gelassen; faellt er erst der Datenbank auf, scheitert
        // die ganze Raeumung.
        String inhalt = "A".repeat(Abholgut.LAENGSTER_INHALT);
        assertEquals(inhalt, new Abholgut("1x Kiste", inhalt).inhalt());
        assertThrows(IllegalArgumentException.class,
                () -> new Abholgut("1x Kiste", inhalt + "A"));
    }
}
