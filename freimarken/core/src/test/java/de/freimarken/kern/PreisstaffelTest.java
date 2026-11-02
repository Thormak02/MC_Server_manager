package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der erste Test des Projekts - und er laeuft ohne Server, ohne Attrappe, ohne Bukkit.
 *
 * <p>Genau dafuer gibt es den Kern als eigenes Modul: Was wirklich kaputtgehen kann, ist
 * reine Rechnung und damit so pruefbar wie jeder Python-Test im Manager.
 *
 * <p>Die festen Betraege unten pruefen ausdruecklich die AUSGELIEFERTE Staffel
 * ({@link Preisstaffel#VORGABE}). Seit die Zahlen aus der Einstellungsdatei kommen, waere
 * es leicht, sie hier still gegen andere zu tauschen - dann blieben die Proben gruen und
 * prueften nichts mehr. Dass die Rechnung auch mit anderen Zahlen stimmt, pruefen die
 * Proben am Ende mit einer zweiten Staffel.
 */
class PreisstaffelTest {

    private static final Preisstaffel VORGABE = Preisstaffel.VORGABE;

    @Test
    @DisplayName("Die erste Parzelle ist gratis - wer nur bauen will, zahlt nie etwas")
    void ersteIstGratis() {
        assertEquals(0L, VORGABE.preisFuerNaechste(0));
    }

    @Test
    @DisplayName("Ab der zweiten wird verdoppelt - die ausgelieferte Staffel")
    void verdopplung() {
        assertEquals(25_000L, VORGABE.preisFuerNaechste(1));
        assertEquals(50_000L, VORGABE.preisFuerNaechste(2));
        assertEquals(100_000L, VORGABE.preisFuerNaechste(3));
        assertEquals(200_000L, VORGABE.preisFuerNaechste(4));
    }

    @Test
    @DisplayName("Bei der sechsten Parzelle kippt es: Flaeche kostet so viel wie eine Stadt")
    void bremseGegenFlaechenhorten() {
        // Eine Freistadt kostet 400.000 Mark. Die sechste Parzelle kostet GENAU so viel -
        // ein sauberer Kipppunkt. Ab der siebten ist Horten teurer als Gruenden, und
        // genau das soll die Staffel bewirken.
        assertEquals(400_000L, VORGABE.preisFuerNaechste(5),
                "Die sechste Parzelle sollte genau eine Freistadt kosten");
        assertTrue(VORGABE.preisFuerNaechste(6) > 400_000L,
                "Ab der siebten muss Flaeche teurer sein als eine eigene Stadt");
    }

    @Test
    @DisplayName("Kein Ueberlauf, auch bei unsinnig vielen Parzellen")
    void keinUeberlauf() {
        // Ohne Deckel waere 25.000 << 199 laengst negativ. Der Deckel ist Arithmetik,
        // nicht Balance - aber ein negativer Preis waere ein Geldautomat.
        long preis = VORGABE.preisFuerNaechste(200);
        assertTrue(preis > 0L, "Preis wurde negativ: " + preis);
        assertEquals(VORGABE.preisFuerNaechste(Preisstaffel.MAX_VERDOPPLUNGEN + 1),
                preis, "Oberhalb des Deckels darf sich nichts mehr aendern");
    }

    @Test
    @DisplayName("Negative Anzahl ist ein Programmierfehler, kein Sonderfall")
    void negativWirftAus() {
        assertThrows(IllegalArgumentException.class, () -> VORGABE.preisFuerNaechste(-1));
        assertThrows(IllegalArgumentException.class, () -> VORGABE.gesamtpreisFuer(-1));
    }

    @Test
    @DisplayName("Zusammenlegen kostet je Feld und braucht mindestens zwei")
    void zusammenlegung() {
        assertEquals(20_000L, VORGABE.zusammenlegungsGebuehr(2));
        assertEquals(90_000L, VORGABE.zusammenlegungsGebuehr(9));
        assertThrows(IllegalArgumentException.class,
                () -> VORGABE.zusammenlegungsGebuehr(1));
    }

    @Test
    @DisplayName("Gesamtpreis summiert die Staffel, erste Parzelle gratis")
    void gesamtpreis() {
        assertEquals(0L, VORGABE.gesamtpreisFuer(0));
        assertEquals(0L, VORGABE.gesamtpreisFuer(1));
        assertEquals(25_000L, VORGABE.gesamtpreisFuer(2));
        assertEquals(75_000L, VORGABE.gesamtpreisFuer(3));
        assertEquals(175_000L, VORGABE.gesamtpreisFuer(4));
    }

    // --- Eine andere Staffel ----------------------------------------------------

    @Test
    @DisplayName("Mit einem anderen Grundpreis rechnet dieselbe Staffel richtig")
    void andereZahlen() {
        Preisstaffel teuer = new Preisstaffel(40_000L, 15_000L);
        assertEquals(0L, teuer.preisFuerNaechste(0), "gratis bleibt gratis");
        assertEquals(40_000L, teuer.preisFuerNaechste(1));
        assertEquals(80_000L, teuer.preisFuerNaechste(2));
        assertEquals(30_000L, teuer.zusammenlegungsGebuehr(2));
        assertEquals(120_000L, teuer.gesamtpreisFuer(3), "40.000 + 80.000");
    }

    @Test
    @DisplayName("Grundpreis 0 heisst: alles gratis - erlaubt, aber nicht beilaeufig")
    void allesGratis() {
        // Eine denkbare Serverform und kein Tippfehler. Dass sie trotzdem eine
        // Bestaetigung in der Datei braucht, ist Sache der Sprungregel.
        Preisstaffel gratis = new Preisstaffel(0L, 0L);
        assertEquals(0L, gratis.preisFuerNaechste(7));
        assertEquals(0L, gratis.zusammenlegungsGebuehr(9));
        assertTrue(Preisstaffel.brauchbarerGrundpreis(0L));
        assertTrue(Preisstaffel.brauchbareGebuehr(0L));
    }

    @Test
    @DisplayName("Der Ueberlaufdeckel greift genau da, wo der Leser die Grenze zieht")
    void obergrenzeIstGerechnet() {
        // Nicht eine getippte runde Zahl: Der Preis der letzten Stufe ist
        // grundpreis << MAX_VERDOPPLUNGEN, und der muss positiv bleiben. Ein
        // uebergelaufener Preis ist negativ - und ein negativer Preis ist eine
        // Gutschrift beim Kauf.
        Preisstaffel ganzOben = new Preisstaffel(Preisstaffel.HOECHSTER_GRUNDPREIS, 0L);
        assertTrue(ganzOben.preisFuerNaechste(Preisstaffel.MAX_VERDOPPLUNGEN + 1) > 0L,
                "beim hoechsten erlaubten Grundpreis darf die letzte Stufe nicht kippen");

        assertFalse(Preisstaffel.brauchbarerGrundpreis(Preisstaffel.HOECHSTER_GRUNDPREIS + 1));
        assertFalse(Preisstaffel.brauchbarerGrundpreis(-1L));
        assertThrows(IllegalArgumentException.class,
                () -> new Preisstaffel(Preisstaffel.HOECHSTER_GRUNDPREIS + 1, 0L));
    }

    @Test
    @DisplayName("Die Gebuehr bleibt unter dem Ueberlauf, auch bei der groessten Gruppe")
    void gebuehrGrenze() {
        // Die Felderzahl kappt mitZuschlag bei Integer.MAX_VALUE; die hoechste erlaubte
        // Gebuehr je Feld muss mit genau dieser Zahl noch in einen long passen.
        Preisstaffel ganzOben = new Preisstaffel(0L, Preisstaffel.HOECHSTE_GEBUEHR);
        assertTrue(ganzOben.zusammenlegungsGebuehr(Integer.MAX_VALUE) > 0L,
                "die groesste denkbare Gruppe darf die Gebuehr nicht kippen");

        assertFalse(Preisstaffel.brauchbareGebuehr(Preisstaffel.HOECHSTE_GEBUEHR + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Preisstaffel(0L, -1L));
    }

    @Test
    @DisplayName("Die Staffel im Klartext nennt die Stufen - gegen den plausiblen Vertipper")
    void klartext() {
        // Die einzige Schranke gegen 25 statt 25.000: Beide Zahlen liegen im erlaubten
        // Bereich, und nur wer die Stufen liest, sieht den Unterschied.
        assertEquals("1. Parzelle gratis - 2. 25000 - 3. 50000 - 4. 100000 - 5. 200000",
                VORGABE.alsText(4));
        assertEquals("1. Parzelle gratis - 2. 25", new Preisstaffel(25L, 1L).alsText(1));
    }

    @Test
    @DisplayName("Die Summe laeuft nicht ueber - ein negativer Betrag waere eine Gutschrift")
    void gesamtpreisLaeuftNichtUeber() {
        // Gemessen am 02.11.2026: Mit 349.526 Mark Grundpreis - erlaubt, denn der Deckel
        // liegt bei 8.388.607 - ergab gesamtpreisFuer(64) exakt
        // -9.223.354.444.669.080.918. Der Befehl /freimarken preis nimmt bis 64 und steht
        // jedem Spieler offen; auf dem Bildschirm stand also eine Gutschrift in
        // Milliardenhoehe. Der gerechnete Deckel deckt die einzelne Stufe, nicht die Summe.
        var teuer = new Preisstaffel(349_526L, Preisstaffel.VORGABE_ZUSAMMENLEGUNG_JE_FELD);
        assertEquals(Long.MAX_VALUE, teuer.gesamtpreisFuer(64));
        assertTrue(teuer.gesamtpreisFuer(63) > 0L, "63 passt noch");

        var hoechste = new Preisstaffel(Preisstaffel.HOECHSTER_GRUNDPREIS, 0L);
        for (int anzahl = 0; anzahl <= 80; anzahl++) {
            assertTrue(hoechste.gesamtpreisFuer(anzahl) >= 0L,
                    "nie negativ, auch nicht bei " + anzahl + " Parzellen");
            if (anzahl > 0) {
                assertTrue(hoechste.gesamtpreisFuer(anzahl)
                                >= hoechste.gesamtpreisFuer(anzahl - 1),
                        "und nie kleiner als vorher, bei " + anzahl);
            }
        }
    }
}
