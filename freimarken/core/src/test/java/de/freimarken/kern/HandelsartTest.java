package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die zwei Taten am Kistenshop - und die Verdrehung, die hier an EINER Stelle sitzt.
 *
 * <p>Am 08.11.2026 waren die beiden Preisrichtungen in {@code Shoptext} vertauscht:
 * {@code verkauftWare()} heisst "der Shop verkauft", also KAUFT der Kunde - und der Satz,
 * der daran hing, sagte "Kunden kaufen". Gefunden hat es eine Probe und nicht die
 * Bildtafel, denn beide Saetze waren fuer sich richtig.
 *
 * <p>Diese Proben sind das Netz darunter: Die Verdrehung steht jetzt in
 * {@link Handelsart} und nirgends sonst, also kann sie nur noch einmal falsch sein.
 */
@DisplayName("Die Handelsart")
class HandelsartTest {

    private static final String WELT = "freimarken:altfurt";

    private static Kistenshop shop(long kaufpreis, long verkaufpreis) {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, UUID.randomUUID(), 16,
                kaufpreis, verkaufpreis, new Warenprobe("STONE", "inhalt"));
    }

    @Test
    @DisplayName("Der Kunde kauft, WENN der Shop verkauft - und umgekehrt")
    void dieVerdrehungSitztHier() {
        // DIE EINE STELLE. Ein Shop mit Kaufpreis 100 und Verkaufpreis 0 VERKAUFT nur:
        // Dort kann ein Kunde kaufen und nichts verkaufen. Vertauscht bekommt ein
        // Ankaufstand die Zeile "Kunden kaufen fuer 0 Mark" - und eine 0 als Preis ist
        // genau das, was Shopschild als PREIS_BEIDE_NULL abweist.
        Kistenshop nurVerkauf = shop(100L, 0L);
        Kistenshop nurAnkauf = shop(0L, 80L);
        Kistenshop beides = shop(100L, 80L);

        assertTrue(Handelsart.KUNDE_KAUFT.moeglichAn(nurVerkauf),
                "der Shop verkauft, also kann der Kunde kaufen");
        assertFalse(Handelsart.KUNDE_VERKAUFT.moeglichAn(nurVerkauf),
                "der Shop kauft nichts an, also kann der Kunde nicht verkaufen");

        assertFalse(Handelsart.KUNDE_KAUFT.moeglichAn(nurAnkauf));
        assertTrue(Handelsart.KUNDE_VERKAUFT.moeglichAn(nurAnkauf));

        assertTrue(Handelsart.KUNDE_KAUFT.moeglichAn(beides));
        assertTrue(Handelsart.KUNDE_VERKAUFT.moeglichAn(beides));
    }

    @Test
    @DisplayName("Jede Art nimmt IHREN Preis")
    void jedeArtIhrPreis() {
        // Zwei verschiedene Zahlen, und keine ist 0: Mit gleichen Preisen waere ein
        // Tausch nicht zu sehen, und mit einer 0 waere er von "diese Richtung gibt es
        // nicht" nicht zu unterscheiden.
        Kistenshop beides = shop(100L, 80L);

        assertEquals(100L, Handelsart.KUNDE_KAUFT.stufenpreis(beides),
                "wer kauft, zahlt den Kaufpreis");
        assertEquals(80L, Handelsart.KUNDE_VERKAUFT.stufenpreis(beides),
                "wer verkauft, bekommt den Verkaufpreis");
    }

    @Test
    @DisplayName("Ohne Shop gibt es weder Richtung noch Preis")
    void ohneShopNichts() {
        assertThrows(IllegalArgumentException.class,
                () -> Handelsart.KUNDE_KAUFT.moeglichAn(null));
        assertThrows(IllegalArgumentException.class,
                () -> Handelsart.KUNDE_VERKAUFT.stufenpreis(null));
    }

    @Test
    @DisplayName("Die Namen nennen die SICHT - sonst waere die Falle wieder offen")
    void dieNamenNennenDieSicht() {
        // Ein Wert KAUF haette dieselbe Falle wieder aufgestellt: Jeder Handel IST ein
        // Kauf und ein Verkauf zugleich - nur nicht fuer denselben Menschen. Diese Probe
        // nagelt die Benennung fest, damit sie niemand "kuerzt".
        assertEquals("KUNDE_KAUFT", Handelsart.KUNDE_KAUFT.name());
        assertEquals("KUNDE_VERKAUFT", Handelsart.KUNDE_VERKAUFT.name());
        assertEquals(2, Handelsart.values().length,
                "zwei Taten und keine dritte - Rechtsklick und Linksklick");
    }
}
