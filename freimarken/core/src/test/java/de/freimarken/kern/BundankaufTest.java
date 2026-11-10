package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Ankauf des Bundes AM SCHILD - die Rechnung ohne Server.
 *
 * <p>Zwei Zahlen treffen hier aufeinander, die vorher nichts miteinander zu tun hatten:
 * der Ankaufpreis in PFENNIGEN je Stueck ({@code bundesmarkt.ankauf}) und die
 * Handelsmenge eines Kistenshops. Aus beiden wird ein Stufenpreis in GANZEN MARK - und
 * genau an dieser Stelle verschwindet sonst ein Pfennigrest.
 */
@DisplayName("Der Ankauf des Bundes am Schild")
class BundankaufTest {

    private static final UUID ANNA = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");

    // ================================================== welcher Klick ein Ankauf ist

    @Test
    @DisplayName("Ein Ankaufstand ist dem Bund UND wird vom Kunden beliefert")
    void ankaufstandIstBeides() {
        // Vier Lagen, und nur eine ist der Bundesmarkt. Die wichtigste Verwechslung ist
        // die dritte: Ein Spielershop, an den ein Kunde verkauft, sieht genauso aus -
        // nur zahlt dort ein Mensch aus seinem Konto, und dafuer gibt es keinen
        // Tagesdeckel und keine Tafel.
        assertTrue(Bundankauf.istAnkaufstand(bundstand(), Handelsart.KUNDE_VERKAUFT));
        assertFalse(Bundankauf.istAnkaufstand(bundstand(), Handelsart.KUNDE_KAUFT),
                "am Bundshop KAUFEN ist eine Senke und braucht keine Tafel");
        assertFalse(Bundankauf.istAnkaufstand(spielershop(),
                Handelsart.KUNDE_VERKAUFT), "ein Spielershop zahlt aus einem Konto");
        assertFalse(Bundankauf.istAnkaufstand(spielershop(), Handelsart.KUNDE_KAUFT));
    }

    @Test
    @DisplayName("Ohne Shop oder Handelsart gibt es die Frage nicht")
    void ohneShopKeineFrage() {
        assertThrows(IllegalArgumentException.class,
                () -> Bundankauf.istAnkaufstand(null, Handelsart.KUNDE_VERKAUFT));
        assertThrows(IllegalArgumentException.class,
                () -> Bundankauf.istAnkaufstand(bundstand(), null));
    }

    // ================================================== ganze Mark, oder gar nicht

    @Test
    @DisplayName("Die Menge mal Pfennige muss durch hundert teilbar sein")
    void nurGanzeMark() {
        // 16 Stein zu 20 Pfennigen sind 3,20 Mark - an einem Schild mit FESTER Menge
        // verschwaenden also 20 Pfennige bei JEDEM Handel, unsichtbar. Verkaufsregel
        // konnte dafuer Stuecke liegenlassen; ein Schild kann das nicht.
        assertFalse(Bundankauf.gehtGlattAuf(20L, 16));
        assertTrue(Bundankauf.gehtGlattAuf(20L, 5), "5 mal 20 sind genau eine Mark");
        assertTrue(Bundankauf.gehtGlattAuf(20L, 10));
        assertTrue(Bundankauf.gehtGlattAuf(100L, 1), "ein glatter Markpreis geht immer");
        assertTrue(Bundankauf.gehtGlattAuf(1_100L, 1), "11 Mark je Diamant");
        assertFalse(Bundankauf.gehtGlattAuf(1_150L, 1), "11,50 Mark je Stueck nicht");
        assertTrue(Bundankauf.gehtGlattAuf(1_150L, 2));
    }

    @Test
    @DisplayName("Die kleinste passende Menge ist hundert durch den groessten Teiler")
    void kleinsteMengeIstGerechnet() {
        // Die Zahl steht in der Absage, damit ein Betreiber nicht Mengen durchprobiert -
        // und jedes Vielfache davon geht ebenfalls.
        assertEquals(5, Bundankauf.kleinsteMenge(20L));
        assertEquals(1, Bundankauf.kleinsteMenge(100L), "glatte Mark: eine genuegt");
        assertEquals(1, Bundankauf.kleinsteMenge(1_100L));
        assertEquals(2, Bundankauf.kleinsteMenge(1_150L), "50 Pfennige: zwei Stueck");
        assertEquals(2, Bundankauf.kleinsteMenge(250L));
        assertEquals(100, Bundankauf.kleinsteMenge(1L), "ein Pfennig: hundert Stueck");
        assertEquals(4, Bundankauf.kleinsteMenge(25L));
        assertEquals(20, Bundankauf.kleinsteMenge(5L));
    }

    @Test
    @DisplayName("Jede ausgerechnete Menge geht wirklich auf - und jedes Vielfache")
    void dieGerechneteMengeGehtAuf() {
        // DIE PROBE, DIE DIE RECHNUNG PRUEFT UND NICHT IHRE ABSCHRIFT. Eine Tafel aus
        // Paaren wie oben haelt die acht Faelle, die jemand aufgeschrieben hat; dieser
        // Durchlauf haelt die ZUSAGE: Was kleinsteMenge nennt, geht auf - und keine
        // kleinere. Genau davon lebt die Absage.
        for (long pfennige = 1L; pfennige <= 400L; pfennige++) {
            int kleinste = Bundankauf.kleinsteMenge(pfennige);
            assertTrue(Bundankauf.gehtGlattAuf(pfennige, kleinste),
                    pfennige + " Pfennige, " + kleinste + " Stueck");
            assertTrue(Bundankauf.gehtGlattAuf(pfennige, kleinste * 3),
                    "jedes Vielfache: " + pfennige + " Pfennige, " + kleinste * 3);
            for (int menge = 1; menge < kleinste; menge++) {
                assertFalse(Bundankauf.gehtGlattAuf(pfennige, menge),
                        "keine KLEINERE darf aufgehen: " + pfennige + " Pfennige, "
                                + menge + " Stueck");
            }
        }
    }

    @Test
    @DisplayName("Der Stufenpreis sind Menge mal Pfennige durch hundert")
    void stufenpreisIstDieRechnung() {
        assertEquals(1L, Bundankauf.stufenpreis(20L, 5));
        assertEquals(4L, Bundankauf.stufenpreis(20L, 20));
        assertEquals(11L, Bundankauf.stufenpreis(1_100L, 1));
        assertEquals(23L, Bundankauf.stufenpreis(1_150L, 2));
    }

    @Test
    @DisplayName("Eine unebene Menge WIRFT - und die Meldung nennt die passende")
    void unebeneMengeWirft() {
        // Kein stiller Rueckfall auf eine abgerundete Zahl: Das waere der verschwundene
        // Pfennigrest, gegen den die ganze Klasse steht. Die zwei Stellen, die rufen,
        // fragen vorher - darum darf es hier werfen.
        var fehler = assertThrows(IllegalArgumentException.class,
                () -> Bundankauf.stufenpreis(20L, 16));

        assertTrue(fehler.getMessage().contains("5"),
                "die Meldung nennt die kleinste passende Menge: "
                        + fehler.getMessage());
    }

    @Test
    @DisplayName("Ein Stufenpreis ueber der Preisgrenze wirft - keine Shopzeile traegt ihn")
    void zuGrosserStufenpreisWirft() {
        // Erreichbar nur mit grossen Zahlen auf beiden Seiten: 3.456 Stueck zu 3.000
        // Mark je Stueck sind 10,3 Millionen Mark je Handel, und die Shopzeile traegt
        // hoechstens 10 Millionen. Ein Wurf ist hier richtig - die Stelle, die ihn
        // faengt, gibt dem Betreiber eine Absage statt eines halben Standes.
        assertFalse(Bundankauf.brauchbarerStufenpreis(300_000L, 3_456));
        assertThrows(IllegalArgumentException.class,
                () -> Bundankauf.stufenpreis(300_000L, 3_456));
        // Keine Zahl ist auch keine: Diese zwei Fragen darf ein Aufrufer auch ohne
        // gehtGlattAuf stellen, und sie sind erreichbar.
        assertFalse(Bundankauf.brauchbarerStufenpreis(0L, 5));
        assertFalse(Bundankauf.brauchbarerStufenpreis(20L, 0));
        assertTrue(Bundankauf.brauchbarerStufenpreis(20L, 5));
        // UND DIE UNTERE KANTE STEHT HIER NICHT MEHR - Befund der Einspritzung vom
        // 10.11.2026. Ein Stufenpreis von 0 ist nach einem Ja von gehtGlattAuf
        // unmoeglich (menge mal pfennige ist dann mindestens 100), also war der Riegel
        // preis >= 1 von keiner Einspritzung zu treffen. Was hier gilt, ist die Zusage
        // selbst: Eine Menge, die aufgeht, ergibt immer mindestens eine Mark.
        assertTrue(Bundankauf.gehtGlattAuf(100L, 1));
        assertEquals(1L, Bundankauf.stufenpreis(100L, 1), "die kleinste moegliche Stufe");
        assertFalse(Bundankauf.gehtGlattAuf(99L, 1),
                "99 Pfennige ergeben bei einem Stueck keine ganze Mark - und darum"
                        + " kommt die Rechnung nie bis zu einem Stufenpreis von 0");
    }

    @Test
    @DisplayName("Ohne Preis gibt es keine Rechnung")
    void ohnePreisKeineRechnung() {
        assertThrows(IllegalArgumentException.class,
                () -> Bundankauf.gehtGlattAuf(0L, 5));
        assertThrows(IllegalArgumentException.class,
                () -> Bundankauf.gehtGlattAuf(-1L, 5));
        assertThrows(IllegalArgumentException.class,
                () -> Bundankauf.gehtGlattAuf(20L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Bundankauf.kleinsteMenge(0L));
    }

    // ================================================== der Tagesdeckel

    @Test
    @DisplayName("Der Deckel laesst nur so viele Stufen, wie noch frei sind")
    void deckelLaesstNurDasFreie() {
        // Die benannte Luecke aus Plan 7.4: Ein Bundshop mit Verkaufspreis war eine
        // Geldquelle OHNE Tagesdeckel. Gerechnet wird ueber Deckelregel - dieselbe
        // Rechnung wie beim Verdienstdeckel und wie beim alten /markt verkaufen.
        assertEquals(10, Bundankauf.stufenImDeckel(100L, 0L, 1L, 10),
                "zehn gewuenschte Stufen zu einer Mark, Deckel 100 - alle gehen");
        assertEquals(2, Bundankauf.stufenImDeckel(100L, 98L, 1L, 10),
                "98 von 100 verbraucht: nur noch zwei");
        assertEquals(0, Bundankauf.stufenImDeckel(100L, 100L, 1L, 10), "am Deckel");
        assertEquals(0, Bundankauf.stufenImDeckel(100L, 120L, 1L, 10),
                "darueber - kann durch eine Deckelsenkung entstehen");
    }

    @Test
    @DisplayName("Teure Stufen zaehlen mehr - gedeckelt wird das GELD, nicht das Stueck")
    void deckelRechnetInMark() {
        // Fuenf Stufen zu je 30 Mark sind 150 Mark, und bei 100 Mark Deckel gehen drei.
        // Mit einer Zaehlung in Stufen gingen fuenf, und der Deckel waere je Preis ein
        // anderer.
        assertEquals(3, Bundankauf.stufenImDeckel(100L, 0L, 30L, 5));
        assertEquals(1, Bundankauf.stufenImDeckel(100L, 70L, 30L, 5));
        assertEquals(0, Bundankauf.stufenImDeckel(100L, 80L, 30L, 5),
                "20 Mark frei, eine Stufe kostet 30 - also keine");
    }

    @Test
    @DisplayName("Nie mehr als gewuenscht, und ohne Wunsch keine Stufe")
    void nieMehrAlsGewuenscht() {
        assertEquals(3, Bundankauf.stufenImDeckel(1_000L, 0L, 1L, 3),
                "ein weiter Deckel macht aus drei Stufen nicht mehr");
        assertEquals(0, Bundankauf.stufenImDeckel(1_000L, 0L, 1L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Bundankauf.stufenImDeckel(1_000L, 0L, 0L, 3));
        assertThrows(IllegalArgumentException.class,
                () -> Bundankauf.stufenImDeckel(1_000L, 0L, 1L, -1));
    }

    // --- Werkzeug -------------------------------------------------------------

    /** Ein Stand des Bundes: kein Besitzer, nur ein Ankaufpreis. */
    private static Kistenshop bundstand() {
        return new Kistenshop("freimarken:altfurt", 60, 71, 6, 61, 71, 6, null, 5, 0L,
                1L, new Warenprobe("STONE", "inhalt"), Set.of());
    }

    private static Kistenshop spielershop() {
        return new Kistenshop("freimarken:altfurt", 60, 71, 6, 61, 71, 6, ANNA, 16, 100L,
                80L, new Warenprobe("STONE", "inhalt"), Set.of());
    }
}
