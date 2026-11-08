package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Die vollstaendige Kopie der Kistenshops - und ihr Unterschied zu einem Cache.
 *
 * <p>Die wichtigste Probe dieser Datei ist {@link #keinVerfallKeinLoch}: Sie haelt den
 * belegten Fehler fest, gegen den diese Klasse gebaut ist. QuickShop #2165 - "The shop
 * cache was not invalidated when a new shop was added, causing cached null queries to
 * persist for up to three minutes", und in diesen drei Minuten zogen Trichter aus
 * Shopkisten ab.
 */
@DisplayName("Die Shopkarte im Arbeitsspeicher")
class ZwischenspeicherShopsTest {

    private SpeicherShops dahinter;
    private ZwischenspeicherShops karte;

    @BeforeEach
    void vorher() {
        dahinter = new SpeicherShops();
        karte = new ZwischenspeicherShops(dahinter);
        karte.fuellen();
    }

    @Nested
    @DisplayName("Haelt den Shopvertrag ein")
    class Vertrag extends ShopVertrag {
        @Override
        protected Shopablage ablage() {
            ZwischenspeicherShops frische =
                    new ZwischenspeicherShops(new SpeicherShops());
            frische.fuellen();
            return frische;
        }
    }

    // ==================================================================== kein Verfall

    @Test
    @DisplayName("Ein NEUER Shop ist SOFORT in der Karte - kein Verfall, kein Loch")
    void keinVerfallKeinLoch() {
        // Der belegte Fehler, gegen den diese Klasse gebaut ist: QuickShop #2165. Dort
        // hielt der Cache bis zu drei Minuten die Antwort "hier ist kein Shop" - und
        // Trichter, die VOR dem Anlegen lagen, zogen die ganze Zeit weiter ab.
        //
        // Hier gibt es keinen Zeitpunkt, an dem die Karte veraltet ist: Die Frage wird
        // ERST gestellt (und beantwortet mit "keiner"), DANN wird der Shop angelegt, und
        // die naechste Frage sieht ihn.
        assertTrue(karte.anDerKiste(ShopVertrag.WELT, 61, 71, 6).isEmpty(),
                "vorher ist dort keiner - genau diese Antwort hielt der alte Cache fest");

        karte.schreibe(ShopVertrag.shop(60, UUID.randomUUID(), "Stein"));

        assertEquals(1, karte.anDerKiste(ShopVertrag.WELT, 61, 71, 6).size(),
                "und danach sofort einer");
    }

    @Test
    @DisplayName("Ein GELOESCHTER Shop ist sofort weg - in beiden Karten")
    void loeschenWirktSofort() {
        // Die andere Richtung, und sie ist genauso wichtig: Ein Schild wird abgebaut, und
        // ab diesem Augenblick ist die Kiste eine gewoehnliche. Haelt die Karte den Shop
        // noch, bleibt eine Kiste gesperrt, an der nichts mehr haengt - und ihr
        // Besitzer kommt an seine eigene Ware nicht heran.
        UUID anna = UUID.randomUUID();
        karte.schreibe(ShopVertrag.shop(60, anna, "weg"));

        assertTrue(karte.loesche(ShopVertrag.WELT, 60, 71, 6));

        assertNull(karte.anDemSchild(ShopVertrag.WELT, 60, 71, 6));
        assertTrue(karte.anDerKiste(ShopVertrag.WELT, 61, 71, 6).isEmpty());
        assertTrue(karte.vonSpieler(anna).isEmpty());
        assertTrue(karte.alle().isEmpty());
        assertEquals(0, karte.anzahl());
    }

    @Test
    @DisplayName("Zieht ein Shop an eine andere Kiste, haengt er nur noch dort")
    void shopZiehtUm() {
        // Derselbe Fall wie in der Datenbankfassung, hier auf den ZWEITEN Index gemuenzt:
        // Ohne das Vergessen des alten Eintrags haengt der Shop an zwei Kisten - und an
        // der alten greift der Trichterschutz weiter, obwohl dort kein Shop ist.
        UUID anna = UUID.randomUUID();
        karte.schreibe(new Kistenshop(ShopVertrag.WELT, 60, 71, 6, 61, 71, 6, anna, 16,
                100L, 80L, ShopVertrag.ware("Stein")));

        karte.schreibe(new Kistenshop(ShopVertrag.WELT, 60, 71, 6, 60, 70, 6, anna, 16,
                100L, 80L, ShopVertrag.ware("Stein")));

        assertTrue(karte.anDerKiste(ShopVertrag.WELT, 61, 71, 6).isEmpty(),
                "an der ALTEN Kiste haengt nichts mehr");
        assertEquals(1, karte.anDerKiste(ShopVertrag.WELT, 60, 70, 6).size(),
                "an der neuen genau einer");
        assertEquals(1, karte.anzahl(), "und es ist DERSELBE Shop, kein zweiter");
    }

    @Test
    @DisplayName("Zwei Shops an einer Kiste - einen loeschen laesst den anderen stehen")
    void einenVonZweienLoeschen() {
        // Der Fall, an dem ein zweiter Index am leichtesten zerbricht: Die Liste je Kiste
        // muss den einen Eintrag verlieren und den anderen behalten.
        UUID anna = UUID.randomUUID();
        karte.schreibe(new Kistenshop(ShopVertrag.WELT, 60, 71, 6, 61, 71, 6, anna, 16,
                100L, 80L, ShopVertrag.ware("Stein")));
        karte.schreibe(new Kistenshop(ShopVertrag.WELT, 62, 71, 6, 61, 71, 6, anna, 8,
                200L, 150L, ShopVertrag.ware("Holz")));

        karte.loesche(ShopVertrag.WELT, 60, 71, 6);

        List<String> uebrig = new ArrayList<>();
        for (Kistenshop shop : karte.anDerKiste(ShopVertrag.WELT, 61, 71, 6)) {
            uebrig.add(shop.ware().name());
        }
        assertEquals(List.of("Holz"), uebrig);
    }

    // ==================================================================== Die Reihenfolge

    @Test
    @DisplayName("Zuerst die ABLAGE, dann die Karte - scheitert jene, bleibt diese leer")
    void erstDieAblageDannDieKarte() {
        // Die Reihenfolge ist Pflicht und nicht Geschmack: Stuende der Shop in der Karte
        // und nicht in der Ablage, handelte sein Schild bis zum naechsten Neustart - und
        // danach nie wieder. Ein Kunde, der heute kauft, bekommt morgen nichts.
        ZwischenspeicherShops gegenNichtSchreibbar =
                new ZwischenspeicherShops(new NichtSchreibbar());
        gegenNichtSchreibbar.fuellen();

        assertThrows(IllegalStateException.class, () -> gegenNichtSchreibbar
                .schreibe(ShopVertrag.shop(60, UUID.randomUUID(), "Stein")));

        assertEquals(0, gegenNichtSchreibbar.anzahl(),
                "die Karte darf den Shop NICHT halten");
        assertNull(gegenNichtSchreibbar.anDemSchild(ShopVertrag.WELT, 60, 71, 6));
    }

    @Test
    @DisplayName("Was in der Ablage steht, ist nach dem Fuellen in der Karte")
    void fuellenLiestAlles() {
        // Der Start: Die Ablage hat Zeilen, die Karte noch nicht. Nach fuellen() muessen
        // BEIDE Indizes stehen - ein gefuellter nachSchild und ein leerer nachKiste waere
        // genau das Loch, gegen das diese Klasse gebaut ist.
        UUID anna = UUID.randomUUID();
        dahinter.schreibe(ShopVertrag.shop(60, anna, "einer"));
        dahinter.schreibe(ShopVertrag.shop(70, anna, "zweiter"));
        ZwischenspeicherShops frische = new ZwischenspeicherShops(dahinter);

        assertEquals(2, frische.fuellen());

        assertEquals(2, frische.anzahl());
        assertEquals(1, frische.anDerKiste(ShopVertrag.WELT, 61, 71, 6).size());
        assertEquals(1, frische.anDerKiste(ShopVertrag.WELT, 71, 71, 6).size());
        assertEquals(2, frische.vonSpieler(anna).size());
    }

    @Test
    @DisplayName("Ein zweites Fuellen wirft nichts weg und verdoppelt nichts")
    void zweimalFuellen() {
        // Ein Neuladen im Betrieb ruft fuellen() erneut. Ohne das Leeren davor stuende
        // jeder Shop danach zweimal in nachKiste - und ein geloeschter Shop waere nach
        // dem Neuladen wieder da.
        dahinter.schreibe(ShopVertrag.shop(60, UUID.randomUUID(), "einer"));
        karte.fuellen();

        karte.fuellen();

        assertEquals(1, karte.anzahl());
        assertEquals(1, karte.anDerKiste(ShopVertrag.WELT, 61, 71, 6).size());
    }

    // ==================================================================== Ungefuellt

    @Test
    @DisplayName("Eine UNGEFUELLTE Karte beantwortet nichts - sie wirft")
    void ungefuelltWirft() {
        // Die tragende Entscheidung dieser Klasse: "Es gibt hier keinen Shop" und "ich
        // weiss es noch nicht" sind zwei verschiedene Dinge, und nur das erste darf ein
        // Trichter als Erlaubnis lesen. Ein stilles Nein waere eine Markthalle, die bis
        // zum ersten Schreibzug offen steht.
        ZwischenspeicherShops leere = new ZwischenspeicherShops(new SpeicherShops());

        assertFalse(leere.istGefuellt());
        assertThrows(IllegalStateException.class,
                () -> leere.anDerKiste(ShopVertrag.WELT, 61, 71, 6));
        assertThrows(IllegalStateException.class,
                () -> leere.anDemSchild(ShopVertrag.WELT, 60, 71, 6));
        assertThrows(IllegalStateException.class, () -> leere.alle());
        assertThrows(IllegalStateException.class,
                () -> leere.vonSpieler(UUID.randomUUID()));
        assertThrows(IllegalStateException.class,
                () -> leere.schreibe(ShopVertrag.shop(60, UUID.randomUUID(), "x")));
        assertThrows(IllegalStateException.class,
                () -> leere.loesche(ShopVertrag.WELT, 60, 71, 6));

        leere.fuellen();
        assertTrue(leere.istGefuellt());
        assertTrue(leere.anDerKiste(ShopVertrag.WELT, 61, 71, 6).isEmpty(),
                "jetzt ist die leere Antwort eine Antwort");
    }

    @Test
    @DisplayName("Ohne Ablage dahinter ist die Karte eine Behauptung")
    void ohneAblageKeineKarte() {
        assertThrows(IllegalArgumentException.class,
                () -> new ZwischenspeicherShops(null));
    }

    @Test
    @DisplayName("Die Listen sind EIGENE - der Aufrufer kann die Karte nicht aendern")
    void listenSindEigene() {
        // Gaebe die Karte ihre inneren Listen heraus, koennte ein Aufrufer einen Shop
        // hinzufuegen, der in keiner Zeile steht - und dann handelt ein Schild, das es
        // nicht gibt.
        UUID anna = UUID.randomUUID();
        karte.schreibe(ShopVertrag.shop(60, anna, "einer"));

        karte.alle().clear();
        karte.vonSpieler(anna).clear();
        assertThrows(UnsupportedOperationException.class,
                () -> karte.anDerKiste(ShopVertrag.WELT, 61, 71, 6).clear());

        assertEquals(1, karte.anzahl());
    }

    /** Eine Ablage, in die nichts hineingeht - fuer die Reihenfolgenprobe. */
    private static final class NichtSchreibbar implements Shopablage {
        @Override
        public Kistenshop anDemSchild(String welt, int x, int y, int z) {
            return null;
        }

        @Override
        public List<Kistenshop> anDerKiste(String welt, int x, int y, int z) {
            return List.of();
        }

        @Override
        public List<Kistenshop> vonSpieler(UUID besitzer) {
            return List.of();
        }

        @Override
        public List<Kistenshop> alle() {
            return List.of();
        }

        @Override
        public void schreibe(Kistenshop shop) {
            throw new IllegalStateException("nicht schreibbar (Probe)");
        }

        @Override
        public boolean loesche(String welt, int x, int y, int z) {
            throw new IllegalStateException("nicht loeschbar (Probe)");
        }
    }
}
