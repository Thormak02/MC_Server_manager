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
 * Der Ankauf des Bundes am Schild - welcher Klick es ist, und der Deckel.
 *
 * <p>Diese Datei war am 10.11.2026 mittags dreimal so lang: Sie hielt die Umrechnung von
 * PFENNIGEN in ganze Mark, die kleinste passende Menge und die Pruefung, dass Menge mal
 * Preis glatt aufgeht. All das ist mit der Tafel {@code bundesmarkt.ankauf} gefallen -
 * der Preis steht auf dem Schild, und ein Schildpreis ist immer eine ganze Mark. Was
 * bleibt, ist der Unterschied zwischen einem Bundstand und einem Spielerladen.
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
        // die dritte: Ein Spielershop, an den ein Kunde verkauft, sieht genauso aus - nur
        // zahlt dort ein Mensch aus seinem Konto, und dafuer gibt es keinen Tagesdeckel.
        assertTrue(Bundankauf.istAnkaufstand(bundstand(), Handelsart.KUNDE_VERKAUFT));
        assertFalse(Bundankauf.istAnkaufstand(bundstand(), Handelsart.KUNDE_KAUFT),
                "am Bundshop KAUFEN ist eine Senke und braucht keinen Deckel");
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

    // ================================================== der Tagesdeckel

    @Test
    @DisplayName("Der Deckel laesst nur so viele Stufen, wie noch frei sind")
    void deckelLaesstNurDasFreie() {
        // Die benannte Luecke aus Plan 7.4: Ein Bundshop mit Verkaufspreis war eine
        // Geldquelle OHNE Tagesdeckel. Gerechnet wird ueber Deckelregel - dieselbe
        // Rechnung wie beim Verdienstdeckel und wie beim alten /markt verkaufen.
        Marktwerte hundert = Marktwerte.VORGABE.mitDeckel(100L);

        assertEquals(10, Bundankauf.stufenImDeckel(hundert, 0L, 1L, 10),
                "zehn gewuenschte Stufen zu einer Mark, Deckel 100 - alle gehen");
        assertEquals(2, Bundankauf.stufenImDeckel(hundert, 98L, 1L, 10),
                "98 von 100 verbraucht: nur noch zwei");
        assertEquals(0, Bundankauf.stufenImDeckel(hundert, 100L, 1L, 10), "am Deckel");
        assertEquals(0, Bundankauf.stufenImDeckel(hundert, 120L, 1L, 10),
                "darueber - kann durch eine Deckelsenkung entstehen");
    }

    @Test
    @DisplayName("OHNE Deckel kommt das Gewuenschte unveraendert zurueck")
    void ohneDeckelAllesDurch() {
        // ENTSCHIEDEN AM 10.11.2026: Eine 0 heisst kein Deckel, und die Entscheidung
        // steht in dieser Rechnung und nicht bei ihren Aufrufern. Haetten die sie zu
        // treffen, fragte einer eines Tages > 0 und ein anderer >= 0 - und dann deckelt
        // der Handel, was die Auskunft fuer unbegrenzt erklaert.
        Marktwerte ohne = Marktwerte.VORGABE.mitDeckel(Marktwerte.KEIN_DECKEL);

        assertEquals(12, Bundankauf.stufenImDeckel(ohne, 0L, 1L, 12));
        assertEquals(12, Bundankauf.stufenImDeckel(ohne, 1_000_000L, 1L, 12),
                "auch wer heute schon eine Million hat, verkauft weiter");
        assertEquals(3, Bundankauf.stufenImDeckel(ohne, 0L, 999_999L, 3),
                "und der Preis je Stufe spielt dann keine Rolle");
    }

    @Test
    @DisplayName("Teure Stufen zaehlen mehr - gedeckelt wird das GELD, nicht das Stueck")
    void deckelRechnetInMark() {
        // Fuenf Stufen zu je 30 Mark sind 150 Mark, und bei 100 Mark Deckel gehen drei.
        // Mit einer Zaehlung in Stufen gingen fuenf, und der Deckel waere je Preis ein
        // anderer.
        Marktwerte hundert = Marktwerte.VORGABE.mitDeckel(100L);

        assertEquals(3, Bundankauf.stufenImDeckel(hundert, 0L, 30L, 5));
        assertEquals(1, Bundankauf.stufenImDeckel(hundert, 70L, 30L, 5));
        assertEquals(0, Bundankauf.stufenImDeckel(hundert, 80L, 30L, 5),
                "20 Mark frei, eine Stufe kostet 30 - also keine");
    }

    @Test
    @DisplayName("Nie mehr als gewuenscht, und ohne Wunsch keine Stufe")
    void nieMehrAlsGewuenscht() {
        Marktwerte weit = Marktwerte.VORGABE.mitDeckel(1_000L);

        assertEquals(3, Bundankauf.stufenImDeckel(weit, 0L, 1L, 3),
                "ein weiter Deckel macht aus drei Stufen nicht mehr");
        assertEquals(0, Bundankauf.stufenImDeckel(weit, 0L, 1L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Bundankauf.stufenImDeckel(weit, 0L, 0L, 3));
        assertThrows(IllegalArgumentException.class,
                () -> Bundankauf.stufenImDeckel(weit, 0L, 1L, -1));
        assertThrows(IllegalArgumentException.class,
                () -> Bundankauf.stufenImDeckel(null, 0L, 1L, 3));
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
