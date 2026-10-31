package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der erste Test des Projekts - und er laeuft ohne Server, ohne Attrappe, ohne Bukkit.
 *
 * <p>Genau dafuer gibt es den Kern als eigenes Modul: Was wirklich kaputtgehen kann, ist
 * reine Rechnung und damit so pruefbar wie jeder Python-Test im Manager.
 */
class PreisstaffelTest {

    @Test
    @DisplayName("Die erste Parzelle ist gratis - wer nur bauen will, zahlt nie etwas")
    void ersteIstGratis() {
        assertEquals(0L, Preisstaffel.preisFuerNaechste(0));
    }

    @Test
    @DisplayName("Ab der zweiten wird verdoppelt")
    void verdopplung() {
        assertEquals(25_000L, Preisstaffel.preisFuerNaechste(1));
        assertEquals(50_000L, Preisstaffel.preisFuerNaechste(2));
        assertEquals(100_000L, Preisstaffel.preisFuerNaechste(3));
        assertEquals(200_000L, Preisstaffel.preisFuerNaechste(4));
    }

    @Test
    @DisplayName("Bei der sechsten Parzelle kippt es: Flaeche kostet so viel wie eine Stadt")
    void bremseGegenFlaechenhorten() {
        // Eine Freistadt kostet 400.000 Mark. Die sechste Parzelle kostet GENAU so viel -
        // ein sauberer Kipppunkt. Ab der siebten ist Horten teurer als Gruenden, und
        // genau das soll die Staffel bewirken.
        assertEquals(400_000L, Preisstaffel.preisFuerNaechste(5),
                "Die sechste Parzelle sollte genau eine Freistadt kosten");
        assertTrue(Preisstaffel.preisFuerNaechste(6) > 400_000L,
                "Ab der siebten muss Flaeche teurer sein als eine eigene Stadt");
    }

    @Test
    @DisplayName("Kein Ueberlauf, auch bei unsinnig vielen Parzellen")
    void keinUeberlauf() {
        // Ohne Deckel waere 25.000 << 199 laengst negativ. Der Deckel ist Arithmetik,
        // nicht Balance - aber ein negativer Preis waere ein Geldautomat.
        long preis = Preisstaffel.preisFuerNaechste(200);
        assertTrue(preis > 0L, "Preis wurde negativ: " + preis);
        assertEquals(Preisstaffel.preisFuerNaechste(Preisstaffel.MAX_VERDOPPLUNGEN + 1),
                preis, "Oberhalb des Deckels darf sich nichts mehr aendern");
    }

    @Test
    @DisplayName("Negative Anzahl ist ein Programmierfehler, kein Sonderfall")
    void negativWirftAus() {
        assertThrows(IllegalArgumentException.class,
                () -> Preisstaffel.preisFuerNaechste(-1));
        assertThrows(IllegalArgumentException.class,
                () -> Preisstaffel.gesamtpreisFuer(-1));
    }

    @Test
    @DisplayName("Zusammenlegen kostet je Feld und braucht mindestens zwei")
    void zusammenlegung() {
        assertEquals(20_000L, Preisstaffel.zusammenlegungsGebuehr(2));
        assertEquals(90_000L, Preisstaffel.zusammenlegungsGebuehr(9));
        assertThrows(IllegalArgumentException.class,
                () -> Preisstaffel.zusammenlegungsGebuehr(1));
    }

    @Test
    @DisplayName("Gesamtpreis summiert die Staffel, erste Parzelle gratis")
    void gesamtpreis() {
        assertEquals(0L, Preisstaffel.gesamtpreisFuer(0));
        assertEquals(0L, Preisstaffel.gesamtpreisFuer(1));
        assertEquals(25_000L, Preisstaffel.gesamtpreisFuer(2));
        assertEquals(75_000L, Preisstaffel.gesamtpreisFuer(3));
        assertEquals(175_000L, Preisstaffel.gesamtpreisFuer(4));
    }
}
