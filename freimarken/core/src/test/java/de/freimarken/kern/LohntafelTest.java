package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der dreiteilige Schluessel der Lohnliste - und warum jeder Teil noetig ist.
 *
 * <p>Die Tafel ist die groesste Tabelle dieses Projekts und die einzige, die ein Betreiber
 * im Betrieb einstellen soll. Darum ist hier jede Verwechslung festgenagelt, die eine
 * fehlende Ebene ermoeglichte.
 */
class LohntafelTest {

    private static Lohntafel tafel(Lohntafel.Lohn... loehne) {
        return Lohntafel.ausLoehnen(Arrays.asList(loehne));
    }

    private static Lohntafel.Lohn lohn(String beruf, Taetigkeit taetigkeit,
                                       String material, long pfennige) {
        return new Lohntafel.Lohn(beruf, taetigkeit, material, pfennige);
    }

    @Test
    @DisplayName("Eine leere Tafel ist erlaubt und heisst: nichts bringt etwas ein")
    void leereTafel() {
        Lohntafel leer = tafel();

        assertTrue(leer.leer());
        assertEquals(0, leer.anzahl());
        assertEquals(0L, leer.pfennige("bergmann", Taetigkeit.ABBAUEN, "stone"));
        assertEquals(0L, leer.hoechsterLohn(Set.of("bergmann"), Taetigkeit.ABBAUEN,
                "stone"));
    }

    @Test
    @DisplayName("Ohne Liste gibt es keine Tafel")
    void nullIstKeineLeereListe() {
        assertThrows(IllegalArgumentException.class, () -> Lohntafel.ausLoehnen(null));
    }

    @Test
    @DisplayName("Der BERUF trennt - derselbe Block bringt zwei Berufen Verschiedenes")
    void derBerufTrennt() {
        Lohntafel tafel = tafel(
                lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L),
                lohn("baumeister", Taetigkeit.ABBAUEN, "stone", 5L));

        assertEquals(40L, tafel.pfennige("bergmann", Taetigkeit.ABBAUEN, "stone"));
        assertEquals(5L, tafel.pfennige("baumeister", Taetigkeit.ABBAUEN, "stone"));
        assertEquals(2, tafel.anzahl());
    }

    @Test
    @DisplayName("Die TAETIGKEIT trennt - der Holzfaeller faellt und forstet auf")
    void dieTaetigkeitTrennt() {
        // Ohne diese Ebene bekaeme ein Baumeister, der Stein ABBAUT, den Lohn fuers
        // Setzen - und Setzen-Abbauen waere zweimal bezahlt.
        Lohntafel tafel = tafel(
                lohn("holzfaeller", Taetigkeit.ABBAUEN, "oak_log", 60L),
                lohn("holzfaeller", Taetigkeit.SETZEN, "oak_sapling", 30L));

        assertEquals(60L, tafel.pfennige("holzfaeller", Taetigkeit.ABBAUEN, "oak_log"));
        assertEquals(0L, tafel.pfennige("holzfaeller", Taetigkeit.SETZEN, "oak_log"));
        assertEquals(30L, tafel.pfennige("holzfaeller", Taetigkeit.SETZEN,
                "oak_sapling"));
        assertEquals(0L, tafel.pfennige("holzfaeller", Taetigkeit.ABBAUEN,
                "oak_sapling"));
    }

    @Test
    @DisplayName("Beruf und Material werden kleingeschrieben - an EINER Stelle")
    void allesKleingeschrieben() {
        // Material heisst in Bukkit STONE, in der Einstellungsdatei steht stone. Zwei
        // Stellen, die denselben Namen zurechtbiegen, biegen ihn eines Tages verschieden.
        Lohntafel tafel = tafel(lohn("  Bergmann ", Taetigkeit.ABBAUEN, " STONE ", 40L));

        assertEquals(40L, tafel.pfennige("bergmann", Taetigkeit.ABBAUEN, "stone"));
        assertEquals(40L, tafel.pfennige("BERGMANN", Taetigkeit.ABBAUEN, "STONE"),
                "auch die Frage wird kleingeschrieben");
    }

    @Test
    @DisplayName("Die erste Zeile zu einem Schluessel gilt")
    void dieErsteGilt() {
        Lohntafel tafel = tafel(
                lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L),
                lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 999L));

        assertEquals(40L, tafel.pfennige("bergmann", Taetigkeit.ABBAUEN, "stone"));
        assertEquals(1, tafel.anzahl());
    }

    @Test
    @DisplayName("Eine fehlende Zeile bringt nichts zum Stehen")
    void nullZeilenWerdenUebersprungen() {
        Lohntafel tafel = tafel(lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L),
                null, lohn("bauer", Taetigkeit.ABBAUEN, "wheat", 20L));

        assertEquals(2, tafel.anzahl());
    }

    @Test
    @DisplayName("Der HOECHSTE Satz unter den gehaltenen Berufen gilt")
    void hoechsterLohn() {
        Lohntafel tafel = tafel(
                lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L),
                lohn("steinmetz", Taetigkeit.ABBAUEN, "stone", 70L),
                lohn("bauer", Taetigkeit.ABBAUEN, "wheat", 20L));

        assertEquals(70L, tafel.hoechsterLohn(Set.of("bergmann", "steinmetz"),
                Taetigkeit.ABBAUEN, "stone"), "nicht 110 - ein Block zahlt einmal");
        assertEquals(40L, tafel.hoechsterLohn(Set.of("bergmann", "bauer"),
                Taetigkeit.ABBAUEN, "stone"));
        assertEquals(0L, tafel.hoechsterLohn(Set.of("fischer"), Taetigkeit.ABBAUEN,
                "stone"));
        assertEquals(0L, tafel.hoechsterLohn(Set.of(), Taetigkeit.ABBAUEN, "stone"));
    }

    @Test
    @DisplayName("Ohne Berufsmenge gibt es keine Antwort")
    void ohneBerufeWirft() {
        assertThrows(IllegalArgumentException.class,
                () -> tafel().hoechsterLohn(null, Taetigkeit.ABBAUEN, "stone"));
    }

    @Test
    @DisplayName("Eine fehlende Frage ist 0 und kein Wurf")
    void fehlendeFrageIstNull() {
        // Gefragt wird in einem Blockereignis. Ein Wurf dort waere eine Ausnahme je
        // Block; 0 heisst "bringt nichts" und ist der Normalfall.
        Lohntafel tafel = tafel(lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L));

        assertEquals(0L, tafel.pfennige(null, Taetigkeit.ABBAUEN, "stone"));
        assertEquals(0L, tafel.pfennige("bergmann", null, "stone"));
        assertEquals(0L, tafel.pfennige("bergmann", Taetigkeit.ABBAUEN, null));
    }

    @Test
    @DisplayName("Ein Lohn braucht Beruf, Taetigkeit und Material - und darf nicht negativ sein")
    void lohnPflichten() {
        assertThrows(IllegalArgumentException.class,
                () -> lohn(null, Taetigkeit.ABBAUEN, "stone", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> lohn("bergmann", null, "stone", 1L));
        assertThrows(IllegalArgumentException.class,
                () -> lohn("bergmann", Taetigkeit.ABBAUEN, "  ", 1L));
        // Ein negativer Lohn waere eine Gebuehr fuers Arbeiten, und die gehoert zu den
        // Senken - nicht in die Lohnliste.
        assertThrows(IllegalArgumentException.class,
                () -> lohn("bergmann", Taetigkeit.ABBAUEN, "stone", -1L));
        assertEquals(0L, lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 0L).pfennige(),
                "eine 0 ist erlaubt: ein Material, das ausdruecklich nichts bringt");
    }

    @Test
    @DisplayName("Die Materialien einer Taetigkeit - fuer den Bericht beim Start")
    void materialienEinerTaetigkeit() {
        Lohntafel tafel = tafel(
                lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L),
                lohn("bergmann", Taetigkeit.ABBAUEN, "coal_ore", 150L),
                lohn("baumeister", Taetigkeit.SETZEN, "stone", 25L));

        assertEquals(List.of("stone", "coal_ore"),
                tafel.materialien(Taetigkeit.ABBAUEN));
        assertEquals(List.of("stone"), tafel.materialien(Taetigkeit.SETZEN));
        assertEquals(List.of(), tafel.materialien(null));
    }

    @Test
    @DisplayName("Zwei Tafeln mit denselben Zeilen sind GLEICH")
    void tafelnSindWerte() {
        // Noetig, weil Wirtschaftswerte und Verdienstwerte Saetze sind und ihre Teile
        // vergleichen: Ohne equals waere die ausgelieferte Datei nie gleich der Vorgabe
        // des Programms, obwohl Zeile fuer Zeile dasselbe darin steht.
        assertEquals(tafel(lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L)),
                tafel(lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L)));
        assertEquals(tafel().hashCode(), tafel().hashCode());
        assertNotEquals(tafel(lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L)),
                tafel(lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 41L)));
        assertNotEquals(tafel(lohn("bergmann", Taetigkeit.ABBAUEN, "stone", 40L)),
                tafel(lohn("bergmann", Taetigkeit.SETZEN, "stone", 40L)));
        assertFalse(tafel().equals("keine Tafel"));
    }
}
