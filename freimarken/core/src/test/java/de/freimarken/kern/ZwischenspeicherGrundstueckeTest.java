package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Der Zwischenspeicher der Grundstuecke - dieselben Zusagen, und ein paar eigene.
 *
 * <p>Der Vertrag laeuft hier zum dritten Mal: Eine Karte vor der Ablage darf an keiner
 * Stelle etwas anderes antworten als die Ablage selbst. Was NUR fuer sie gilt - kein
 * Verfall, eine gedeckte Welt, der Wurf vor dem Fuellen - steht darunter.
 */
@DisplayName("Grundstuecke im Zwischenspeicher")
class ZwischenspeicherGrundstueckeTest {

    private static final String WELT = "freimarken:freistaedte";

    /** Derselbe Vertrag wie bei Speicher- und Datenbankfassung. */
    @Nested
    @DisplayName("Vertrag")
    class Vertrag extends GrundstuecksablageVertrag {

        private long naechste = 100L;

        @Override
        protected Grundstuecksablage ablage() {
            ZwischenspeicherGrundstuecke karte =
                    new ZwischenspeicherGrundstuecke(new SpeicherGrundstuecke());
            karte.fuellen(WELT);
            return karte;
        }

        @Override
        protected long stadtAnlegen(String welt, int feldX, int feldZ) {
            return naechste++;
        }
    }

    @Test
    @DisplayName("Ein ZWEITES fuellen laesst keine Geisterbuerger stehen")
    void zweitesFuellenRaeumtAlleKarten() {
        // FUND DER EINSPRITZUNGSRUNDE ZU TEIL D [12.11.2026]: fuellen leert die
        // Zellkarte und die Nummernkarte - die DRITTE, nachStadt, war dort nicht
        // genannt, und nichts hat es gefangen. Im Spiel laeuft fuellen ein zweites Mal
        // nach /freimarken neuladen: Dann waere jeder, dem je ein Grundstueck gehoerte,
        // weiter Buerger - mit allen Rechten, die seine Stadt hergibt, auf einer Flaeche,
        // die ihm nicht mehr gehoert.
        SpeicherGrundstuecke wahrheit = new SpeicherGrundstuecke();
        ZwischenspeicherGrundstuecke karte = new ZwischenspeicherGrundstuecke(wahrheit);
        long stadt = 1L;
        UUID anna = UUID.randomUUID();
        Grundstueck annas = wahrheit.anlegen(WELT, stadt, Zellquader.eineZelle(1, 1),
                anna, 0L);
        karte.fuellen(WELT);
        assertTrue(karte.besitztIn(WELT, stadt, anna), "vorher ist sie Buergerin");

        // An der KARTE vorbei entfernt - so, wie es nach einem Neustart aussieht, wenn
        // ein anderer Server oder ein Betreiber die Zeile weggenommen hat.
        wahrheit.entfernen(annas.id());
        karte.fuellen(WELT);

        assertFalse(karte.besitztIn(WELT, stadt, anna),
                "nach dem zweiten fuellen ist sie keine Buergerin mehr - eine Karte,"
                        + " die nicht geleert wird, haelt sie fuer immer");
    }

    @Test
    @DisplayName("Ohne Fuellen wirft die Frage - eine leere Karte hiesse 'gibt es nicht'")
    void ohneFuellenWirftDieFrage() {
        ZwischenspeicherGrundstuecke karte =
                new ZwischenspeicherGrundstuecke(new SpeicherGrundstuecke());

        // Der laute Fehler beim Start ist besser als ein stilles Nein auf dem heissen
        // Pfad: Das hiesse, dass jeder auf jedem verkauften Grundstueck bauen darf.
        assertThrows(IllegalStateException.class, () -> karte.anZelle(WELT, 0, 0));
    }

    @Test
    @DisplayName("Eine Zelle wird aus der KARTE beantwortet - ohne Gang zur Ablage")
    void zelleKommtAusDerKarte() {
        Grundstuecksattrappe zaehler = new Grundstuecksattrappe();
        zaehler.anlegen(WELT, 1L, new Zellquader(0, 0, 3, 3), UUID.randomUUID(),
                0L);
        ZwischenspeicherGrundstuecke karte = new ZwischenspeicherGrundstuecke(zaehler);
        karte.fuellen(WELT);
        zaehler.zaehlerZuruecksetzen();

        // Hundert Fragen auf dem heissen Pfad - und kein einziger Gang zur Ablage.
        for (int lauf = 0; lauf < 100; lauf++) {
            assertTrue(karte.anZelle(WELT, 1, 1).isPresent());
            assertTrue(karte.anZelle(WELT, 9, 9).isEmpty());
        }

        assertEquals(0, zaehler.gezaehlt(), "die Karte antwortet selbst - sonst laege bei"
                + " jedem gesetzten Block ein Gang zur Datenbank");
    }

    @Test
    @DisplayName("Ein neues Grundstueck steht SOFORT in der Karte - kein Verfall")
    void neuesStehtSofortDrin() {
        ZwischenspeicherGrundstuecke karte =
                new ZwischenspeicherGrundstuecke(new SpeicherGrundstuecke());
        karte.fuellen(WELT);

        karte.anlegen(WELT, 1L, new Zellquader(2, 2, 4, 4), UUID.randomUUID(), 0L);

        // Die Lehre aus QuickShop #2165, die im Javadoc von ZwischenspeicherShops
        // steht: Zwischen dem Anlegen und dem Ablauf eines Eintrags "hier ist nichts"
        // duerfte sonst jeder darauf bauen.
        assertTrue(karte.anZelle(WELT, 3, 3).isPresent());
        assertEquals(9, karte.belegteZellen(), "3 x 3 Zellen sind EINGETRAGEN");
    }

    @Test
    @DisplayName("Ein aufgeloestes verschwindet aus JEDER seiner Zellen")
    void aufgeloestesVerschwindetGanz() {
        ZwischenspeicherGrundstuecke karte =
                new ZwischenspeicherGrundstuecke(new SpeicherGrundstuecke());
        karte.fuellen(WELT);
        Grundstueck neu = karte.anlegen(WELT, 1L, new Zellquader(0, 0, 2, 2),
                UUID.randomUUID(), 0L);
        assertEquals(9, karte.belegteZellen());

        karte.entfernen(neu.id());

        assertEquals(0, karte.belegteZellen(), "alle neun Eintraege sind weg");
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                assertTrue(karte.anZelle(WELT, x, z).isEmpty(), x + "/" + z);
            }
        }
    }

    @Test
    @DisplayName("Mitbauer aendern lassen die FLAECHE in der Karte stehen")
    void mitbauerAendernLaesstDieFlaecheStehen() {
        // Der Weg geht ueber vergiss() und merke(): Beide laufen ueber alle Zellen, und
        // ein Fehler darin naehme das Grundstueck aus der Karte, ohne dass die Ablage
        // etwas davon weiss. Danach duerfte dort jeder bauen.
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        ZwischenspeicherGrundstuecke karte =
                new ZwischenspeicherGrundstuecke(new SpeicherGrundstuecke());
        karte.fuellen(WELT);
        Grundstueck neu = karte.anlegen(WELT, 1L, new Zellquader(0, 0, 2, 2), anna, 0L);

        karte.mitbauerSetzen(neu.id(), Set.of(bert));

        assertEquals(9, karte.belegteZellen(), "die Flaeche steht noch");
        Grundstueck danach = karte.anZelle(WELT, 1, 1).orElseThrow();
        assertEquals(Set.of(bert), danach.mitbauer());
        assertTrue(danach.darfBauen(bert));
        assertTrue(danach.darfBauen(anna), "und die Eigentuemerin weiter auch");
    }

    @Test
    @DisplayName("Eine FREMDE Welt geht ungepuffert durch - die Karte deckt eine")
    void fremdeWeltGehtDurch() {
        Grundstuecksattrappe zaehler = new Grundstuecksattrappe();
        ZwischenspeicherGrundstuecke karte = new ZwischenspeicherGrundstuecke(zaehler);
        karte.fuellen(WELT);
        zaehler.zaehlerZuruecksetzen();

        karte.anZelle("freimarken:andere", 0, 0);

        assertEquals(1, zaehler.gezaehlt(), "fuer eine Welt, die sie nicht deckt, fragt"
                + " die Karte die Ablage - sonst antwortete sie 'gibt es nicht' ueber"
                + " eine Welt, von der sie nichts weiss");
    }

    @Test
    @DisplayName("Die Obergrenze folgt aus der GEOMETRIE: ein Feld belegt 256 Zellen")
    void obergrenzeFolgtAusDerGeometrie() {
        // Die Rechnung aus dem Javadoc, nachgerechnet: Ein ganzes Feld als EIN
        // Grundstueck belegt 16 x 16 Eintraege. Bei 399 Feldern sind das hoechstens
        // 102.144 - eine Grenze aus der Weltgeometrie und keine Annahme ueber Spieler.
        Stadtfeldplan plan = Stadtfeldplan.freistaedte();
        ZwischenspeicherGrundstuecke karte =
                new ZwischenspeicherGrundstuecke(new SpeicherGrundstuecke());
        karte.fuellen(WELT);

        karte.anlegen(WELT, 1L, new Zellquader(plan.ersteZelle(1), plan.ersteZelle(1),
                plan.letzteZelle(1), plan.letzteZelle(1)), UUID.randomUUID(), 0L);

        assertEquals(256, karte.belegteZellen());
        assertEquals(256, plan.zellenJeKante() * plan.zellenJeKante());
        assertEquals(102_144, Feldgrenze.freistaedte().staedteZahl() * 256L,
                "399 Felder zu 256 Zellen - die Obergrenze der ganzen Karte");
    }

    @Test
    @DisplayName("Gefuellt wird aus der Ablage - was dort steht, steht danach drin")
    void gefuelltWirdAusDerAblage() {
        SpeicherGrundstuecke ablage = new SpeicherGrundstuecke();
        ablage.anlegen(WELT, 1L, new Zellquader(0, 0, 1, 1), UUID.randomUUID(), 0L);
        ablage.anlegen(WELT, 1L, new Zellquader(5, 5, 5, 5), UUID.randomUUID(), 0L);
        ZwischenspeicherGrundstuecke karte = new ZwischenspeicherGrundstuecke(ablage);

        assertEquals(2, karte.fuellen(WELT), "zwei Grundstuecke");

        assertEquals(5, karte.belegteZellen(), "4 Zellen plus 1");
        assertTrue(karte.anZelle(WELT, 0, 0).isPresent());
        assertTrue(karte.anZelle(WELT, 5, 5).isPresent());
        assertTrue(karte.anZelle(WELT, 3, 3).isEmpty());
    }
}
