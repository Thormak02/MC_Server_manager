package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was JEDE Umsetzung von {@link Shopablage} halten muss.
 *
 * <p>Geteilt zwischen der Fassung im Arbeitsspeicher und der auf MariaDB, aus demselben
 * Grund wie beim {@code MietVertrag}: Die eine durchsucht eine Karte, die andere schickt
 * ein {@code WHERE} - und genau an so einer Stelle laufen zwei Fassungen auseinander,
 * ohne dass eine Probe rot wird.
 *
 * <p><b>Das erste Versprechen ist der Kern dieses Vertrags</b>: Der Schluessel ist die
 * SCHILDSTELLE. Dafuer stehen {@link #zweiSchilderAnEinerKiste} und
 * {@link #dasSchildIstDerSchluessel} - sie sagen zusammen, dass eine Doppelkiste zwei
 * Waren handeln kann und dass ein zweites Schreiben dieselbe Zeile trifft.
 */
public abstract class ShopVertrag {

    protected static final String WELT = "freimarken:altfurt";

    /** Eine frische, leere Ablage. */
    protected abstract Shopablage ablage();

    /** Eine Warenprobe mit unterscheidbarem Inhalt. */
    protected static Warenprobe ware(String name) {
        return new Warenprobe(name, "inhalt-von-" + name);
    }

    /**
     * Ein Shop: Schild bei x/71/6, Kiste einen Block daneben bei (x+1)/71/6.
     *
     * <p>Die Kiste liegt in POSITIVER x-Richtung, und das ist nicht beliebig: So liegen
     * zwei Shops mit x und x+2 an VERSCHIEDENEN Kisten, und zwei mit demselben x an
     * derselben. Beides braucht dieser Vertrag.
     */
    protected static Kistenshop shop(int x, UUID besitzer, String warenname) {
        return new Kistenshop(WELT, x, 71, 6, x + 1, 71, 6, besitzer, 16, 100L, 80L,
                ware(warenname));
    }

    @Test
    @DisplayName("Eine leere Ablage antwortet mit null und leeren Listen")
    void leerIstKeinFehler() {
        Shopablage ablage = ablage();

        assertNull(ablage.anDemSchild(WELT, 60, 71, 6));
        assertTrue(ablage.anDerKiste(WELT, 61, 71, 6).isEmpty());
        assertTrue(ablage.vonSpieler(UUID.randomUUID()).isEmpty());
        assertTrue(ablage.alle().isEmpty());
    }

    @Test
    @DisplayName("Was geschrieben wurde, kommt zurueck - alle zwoelf Werte")
    void hinUndZurueck() {
        Shopablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        // Zwoelf verschiedene Werte: Schild und Kiste an verschiedenen Stellen, Menge,
        // Kaufpreis und Verkaufspreis verschieden und keiner eine uebliche Vorgabe. Bei
        // gleichen Werten waere ein Vertauschen zweier Spalten nicht zu sehen - und
        // kaufpreis und verkaufpreis liegen in der Zeile nebeneinander.
        ablage.schreibe(new Kistenshop(WELT, 60, 71, 6, 60, 70, 6, anna, 7, 777L, 333L,
                new Warenprobe("7x Stein", "besonderer-inhalt")));

        Kistenshop zurueck = ablage.anDemSchild(WELT, 60, 71, 6);
        assertNotNull(zurueck);
        assertEquals(WELT, zurueck.schildWelt());
        assertEquals(60, zurueck.schildX());
        assertEquals(71, zurueck.schildY());
        assertEquals(6, zurueck.schildZ());
        assertEquals(60, zurueck.kisteX());
        assertEquals(70, zurueck.kisteY(), "die Kiste liegt UNTER dem Schild");
        assertEquals(6, zurueck.kisteZ());
        assertEquals(anna, zurueck.besitzer());
        assertEquals(7, zurueck.menge());
        assertEquals(777L, zurueck.kaufpreis());
        assertEquals(333L, zurueck.verkaufpreis());
        assertEquals("7x Stein", zurueck.ware().name());
        assertEquals("besonderer-inhalt", zurueck.ware().inhalt());
    }

    @Test
    @DisplayName("Das SCHILD ist der Schluessel - zweimal schreiben trifft eine Zeile")
    void dasSchildIstDerSchluessel() {
        Shopablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.schreibe(shop(60, anna, "alt"));

        ablage.schreibe(shop(60, anna, "neu"));

        assertEquals(1, ablage.alle().size(), "eine Zeile, nicht zwei");
        assertEquals("neu", ablage.anDemSchild(WELT, 60, 71, 6).ware().name());
    }

    @Test
    @DisplayName("ZWEI Schilder an EINER Kiste - eine Doppelkiste mit zwei Waren")
    void zweiSchilderAnEinerKiste() {
        // Der Grund, warum das Schild der Schluessel ist und nicht die Kiste: Mit der
        // Kiste als Schluessel waere das unmoeglich, und ein Laden mit einer Ware je
        // Kiste braeuchte doppelt so viel Platz.
        Shopablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        // Zwei Schilder, die beide auf die Kiste bei 61/71/6 zeigen: eines von links
        // (60), eines von rechts (62).
        ablage.schreibe(new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, anna, 16, 100L, 80L,
                ware("Stein")));
        ablage.schreibe(new Kistenshop(WELT, 62, 71, 6, 61, 71, 6, anna, 8, 200L, 150L,
                ware("Holz")));

        List<Kistenshop> dort = ablage.anDerKiste(WELT, 61, 71, 6);

        assertEquals(2, dort.size(), dort.toString());
        assertEquals(List.of("Stein", "Holz"), namen(dort));
    }

    @Test
    @DisplayName("anDerKiste findet NUR die an dieser Stelle")
    void anDerKisteTrenntDieStellen() {
        Shopablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.schreibe(shop(60, anna, "hier"));
        ablage.schreibe(shop(70, anna, "dort"));

        assertEquals(List.of("hier"), namen(ablage.anDerKiste(WELT, 61, 71, 6)));
        assertEquals(List.of("dort"), namen(ablage.anDerKiste(WELT, 71, 71, 6)));
        assertTrue(ablage.anDerKiste(WELT, 65, 71, 6).isEmpty(), "dazwischen nichts");
    }

    @Test
    @DisplayName("Eine andere WELT ist eine andere Stelle")
    void dieWeltGehoertZurStelle() {
        // Zwei Server-Welten koennen dieselben Koordinaten haben. Ohne die Welt im
        // Schluessel waere ein Shop in Altfurt auch einer in der Marktstadt - und ein
        // Trichter dort zoege aus einer Kiste, die es nicht gibt.
        Shopablage ablage = ablage();
        ablage.schreibe(shop(60, UUID.randomUUID(), "altfurt"));

        assertNull(ablage.anDemSchild("freimarken:marktstadt", 60, 71, 6));
        assertTrue(ablage.anDerKiste("freimarken:marktstadt", 61, 71, 6).isEmpty());
    }

    @Test
    @DisplayName("vonSpieler zeigt NUR seine Shops - und keinen Bundshop")
    void vonSpielerTrenntDieBesitzer() {
        Shopablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        UUID bert = UUID.randomUUID();
        ablage.schreibe(shop(60, anna, "Annas"));
        ablage.schreibe(shop(70, bert, "Berts"));
        // Ein Bundshop gehoert NIEMANDEM. Er darf in keiner Spielerliste stehen - sonst
        // sieht ein Spieler fremde Staende unter "meine".
        ablage.schreibe(shop(80, null, "Bundes"));

        assertEquals(List.of("Annas"), namen(ablage.vonSpieler(anna)));
        assertEquals(List.of("Berts"), namen(ablage.vonSpieler(bert)));
        assertEquals(3, ablage.alle().size(), "alle drei sind aber da");
    }

    @Test
    @DisplayName("Ein BUNDSHOP hat keinen Besitzer und weiss das von sich")
    void bundshopOhneBesitzer() {
        Shopablage ablage = ablage();
        ablage.schreibe(shop(60, null, "Bundes"));

        Kistenshop zurueck = ablage.anDemSchild(WELT, 60, 71, 6);
        assertNull(zurueck.besitzer());
        assertTrue(zurueck.demBund());
    }

    @Test
    @DisplayName("Die Reihenfolge von alle() haelt - zweimal hintereinander gleich")
    void reihenfolgeIstStabil() {
        // Die Suche nach einer Ware zeigt die ersten Treffer. Eine wechselnde
        // Reihenfolge zeigte bei derselben Frage verschiedene Laeden.
        Shopablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.schreibe(shop(70, anna, "zweiter"));
        ablage.schreibe(shop(60, anna, "erster"));
        ablage.schreibe(shop(80, anna, "dritter"));

        List<String> einmal = namen(ablage.alle());
        assertEquals(einmal, namen(ablage.alle()));
        assertEquals(3, einmal.size());
    }

    @Test
    @DisplayName("Loeschen nimmt genau einen Shop - das zweite Mal ist kein Fehler")
    void loeschenIstWiederholbar() {
        // Ein Schild wird von zwei Seiten gemeldet: vom Abbau und vom Herabfallen. Das
        // zweite Mal darf nicht werfen.
        Shopablage ablage = ablage();
        UUID anna = UUID.randomUUID();
        ablage.schreibe(shop(60, anna, "weg"));
        ablage.schreibe(shop(70, anna, "bleibt"));

        assertTrue(ablage.loesche(WELT, 60, 71, 6));
        assertEquals(false, ablage.loesche(WELT, 60, 71, 6));
        assertEquals(List.of("bleibt"), namen(ablage.alle()));
    }

    @Test
    @DisplayName("Ohne Welt, ohne Spieler und ohne Shop geht nichts")
    void pflichtangaben() {
        Shopablage ablage = ablage();

        assertThrows(IllegalArgumentException.class,
                () -> ablage.anDemSchild(null, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.anDemSchild("  ", 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.anDerKiste(null, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ablage.vonSpieler(null));
        assertThrows(IllegalArgumentException.class, () -> ablage.schreibe(null));
        assertThrows(IllegalArgumentException.class,
                () -> ablage.loesche(null, 0, 0, 0));
    }

    @Test
    @DisplayName("Eine Ware mit langem Inhalt kommt Zeichen fuer Zeichen zurueck")
    void langerInhaltBleibtGleich() {
        // Eine voll verzauberte Spitzhacke wird zu einigen hundert Zeichen Text. Eine
        // Umsetzung, die ihn trimmt, Anfuehrungszeichen verschluckt oder bei 255 Zeichen
        // abschneidet, macht daraus eine Ware, die kein Kunde mehr bekommt - und der
        // Vergleich mit dem Kisteninhalt schlaegt dann immer fehl.
        Shopablage ablage = ablage();
        StringBuilder lang = new StringBuilder("  'x' \"y\" \\z% _ ");
        while (lang.length() < 600) {
            lang.append("AbCd+/=0123456789");
        }
        String inhalt = lang.toString();
        ablage.schreibe(new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, UUID.randomUUID(), 1,
                5_000L, 0L, new Warenprobe("Spitzhacke", inhalt)));

        assertEquals(inhalt, ablage.anDemSchild(WELT, 60, 71, 6).ware().inhalt());
    }

    private static List<String> namen(List<Kistenshop> shops) {
        List<String> namen = new ArrayList<>();
        for (Kistenshop shop : shops) {
            namen.add(shop.ware().name());
        }
        return namen;
    }
}
