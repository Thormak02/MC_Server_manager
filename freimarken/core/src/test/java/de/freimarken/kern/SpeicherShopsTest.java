package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die fluechtige Fassung haelt die Kistenshops - und zwei Zusagen darueber hinaus.
 *
 * <p>Jede Zusage, die umsetzungsfrei zu formulieren ist, steht im {@link ShopVertrag} und
 * nicht hier: Sonst erbt die naechste Fassung sie nicht.
 */
@DisplayName("Kistenshops im Arbeitsspeicher")
class SpeicherShopsTest {

    @Nested
    @DisplayName("Haelt den Shopvertrag ein")
    class Vertrag extends ShopVertrag {
        @Override
        protected Shopablage ablage() {
            return new SpeicherShops();
        }
    }

    @Test
    @DisplayName("Zwei Ablagen teilen nichts")
    void zweiAblagenTeilenNichts() {
        // Eine statische Karte waere hier der naheliegende Fehler, und er faellt in einer
        // Probenreihe nie auf, weil jede Probe ihre eigenen Koordinaten nimmt. Im Betrieb
        // waere er ein Shop, der nach dem Wiedereinschalten des Plugins noch dasteht.
        SpeicherShops erste = new SpeicherShops();
        erste.schreibe(new Kistenshop(ShopVertrag.WELT, 60, 71, 6, 61, 71, 6,
                UUID.randomUUID(), 16, 100L, 80L,
                new Warenprobe("Stein", "inhalt"), Set.of()));

        assertNull(new SpeicherShops().anDemSchild(ShopVertrag.WELT, 60, 71, 6));
        assertTrue(new SpeicherShops().alle().isEmpty());
    }

    @Test
    @DisplayName("Die Reihenfolge ist die der SCHLUESSEL, nicht die des Schreibens")
    void reihenfolgeIstDieDerSchluessel() {
        // Eine TreeMap und keine LinkedHashMap, und das ist der Unterschied: Nach einem
        // Neustart liest die Datenbankfassung ihre Zeilen in einer Reihenfolge, die sie
        // selbst bestimmt - nicht in der, in der sie geschrieben wurden. Eine Probe, die
        // sich auf die Schreibreihenfolge verlaesst, waere hier gruen und dort rot.
        SpeicherShops shops = new SpeicherShops();
        UUID anna = UUID.randomUUID();
        shops.schreibe(ShopVertrag.shop(80, anna, "dritter"));
        shops.schreibe(ShopVertrag.shop(60, anna, "erster"));
        shops.schreibe(ShopVertrag.shop(70, anna, "zweiter"));

        // Die Schluessel lauten "welt 60 71 6", "welt 70 71 6", "welt 80 71 6" - als
        // Text geordnet steht 60 vor 70 vor 80.
        assertEquals("erster", shops.alle().get(0).ware().name());
        assertEquals("zweiter", shops.alle().get(1).ware().name());
        assertEquals("dritter", shops.alle().get(2).ware().name());
    }

    @Test
    @DisplayName("Ein geloeschter Shop verschwindet aus ALLEN vier Antworten")
    void loeschenWirktUeberall() {
        // Vier Wege fuehren zu einem Shop, und ein Loeschen muss alle vier treffen. Eine
        // Fassung mit einem zweiten Index - etwa einer Karte je Kiste - haette genau hier
        // ihre Luecke: Der Shop ist aus der einen Karte weg und steht in der anderen noch.
        SpeicherShops shops = new SpeicherShops();
        UUID anna = UUID.randomUUID();
        shops.schreibe(ShopVertrag.shop(60, anna, "weg"));

        assertTrue(shops.loesche(ShopVertrag.WELT, 60, 71, 6));

        assertNull(shops.anDemSchild(ShopVertrag.WELT, 60, 71, 6));
        assertTrue(shops.anDerKiste(ShopVertrag.WELT, 61, 71, 6).isEmpty());
        assertTrue(shops.vonSpieler(anna).isEmpty());
        assertTrue(shops.alle().isEmpty());
    }

    @Test
    @DisplayName("Die Listen sind EIGENE - der Aufrufer kann die Ablage nicht aendern")
    void listenSindEigene() {
        // Gaebe die Ablage ihre innere Sammlung heraus, koennte ein Aufrufer einen Shop
        // hinzufuegen, ohne durch schreibe() zu gehen - und dann waere die
        // Schluesselordnung weg und ein Shop ohne Zeile da.
        SpeicherShops shops = new SpeicherShops();
        UUID anna = UUID.randomUUID();
        shops.schreibe(ShopVertrag.shop(60, anna, "einer"));

        shops.alle().clear();
        shops.vonSpieler(anna).clear();
        shops.anDerKiste(ShopVertrag.WELT, 61, 71, 6).clear();

        assertEquals(1, shops.alle().size(), "die Ablage haelt ihren Shop");
    }

    @Test
    @DisplayName("Ohne Welt gibt es keine Stelle - auch nicht beim Loeschen")
    void pflichtangabenAuchBeimLoeschen() {
        SpeicherShops shops = new SpeicherShops();

        assertThrows(IllegalArgumentException.class,
                () -> shops.loesche("  ", 0, 0, 0));
        assertFalse(shops.loesche(ShopVertrag.WELT, 0, 0, 0),
                "eine leere Stelle zu loeschen ist kein Fehler");
    }
}
