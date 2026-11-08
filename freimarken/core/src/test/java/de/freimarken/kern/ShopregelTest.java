package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Rechnung des Kistenhandels - die drei Zahlenraender der Vorbilder.
 *
 * <p>Jede Probe hier steht gegen einen belegten Fehler von ChestShop oder QuickShop
 * (Plan 7.4). Die wichtigste ist {@link #keinPreisWirdNullGerundet}: Dort hatte ChestShop
 * sein kostenloses Kaufen.
 */
@DisplayName("Die Rechnung des Kistenhandels")
class ShopregelTest {

    @Test
    @DisplayName("Ein einfacher Handel kostet den Stufenpreis")
    void einfacherHandel() {
        Shopregel.Handel handel = Shopregel.rechne(16, 100L, 1, 0);

        assertEquals(100L, handel.preis());
        assertEquals(0L, handel.gebuehr());
        assertEquals(100L, handel.erloes());
        assertEquals(16, handel.stueck());
    }

    @Test
    @DisplayName("Mehrere Stufen vervielfachen Preis UND Stueckzahl")
    void mehrereStufen() {
        Shopregel.Handel handel = Shopregel.rechne(16, 100L, 4, 0);

        assertEquals(400L, handel.preis());
        assertEquals(64, handel.stueck());
    }

    @Test
    @DisplayName("KEIN Preis wird auf 0 gerundet - es gibt keinen Teilhandel")
    void keinPreisWirdNullGerundet() {
        // Der Fehler, gegen den die ganze Bauart steht: ChestShop PR #522 "Fix zero
        // partial price exploit" - bei Teilhandel wurde der skalierte Preis auf zwei
        // Stellen gerundet und konnte 0 werden. Kostenlos kaufen.
        //
        // Hier gibt es keinen skalierten Preis: Ein Handel ist eine GANZE Zahl von
        // Stufen, und eine Stufe kostet, was auf dem Schild steht. Die Probe faehrt den
        // kleinstmoeglichen Preis mit der hoechsten Gebuehr - genau die Lage, in der ein
        // Teilpreis 0 geworden waere.
        Shopregel.Handel kleinst = Shopregel.rechne(1, 1L, 1, 50);

        assertEquals(1L, kleinst.preis(), "der Kunde zahlt immer mindestens 1");
        assertEquals(0L, kleinst.gebuehr(), "50 Prozent von 1 Mark sind abgerundet 0");
        assertEquals(1L, kleinst.erloes(), "und der Verkaeufer bekommt seine 1 Mark");
    }

    @Test
    @DisplayName("Die Gebuehr wird ABGERUNDET - zugunsten des Verkaeufers")
    void gebuehrWirdAbgerundet() {
        // Die sichere Richtung: Bei 5 Prozent von 99 Mark sind es 4 und nicht 5. Die
        // andere Rundung koennte bei kleinen Betraegen den ganzen Erloes verschlucken -
        // 5 Prozent von 1 Mark aufgerundet waere 1, und der Verkaeufer bekaeme 0 fuer
        // seine Ware. Dieselbe Form, in der ChestShops Rundung zum Nulltarif wurde, nur
        // in der anderen Richtung.
        Shopregel.Handel handel = Shopregel.rechne(1, 99L, 1, 5);

        assertEquals(99L, handel.preis());
        assertEquals(4L, handel.gebuehr(), "4,95 abgerundet");
        assertEquals(95L, handel.erloes());
    }

    @Test
    @DisplayName("Erst vervielfachen, DANN die Gebuehr - nicht je Stufe")
    void gebuehrAufDieGanzeSumme() {
        // Je Stufe gerundet verlore ein Verkaeufer bei 20 Stufen bis zu 20 Mark: 5
        // Prozent von 19 sind abgerundet 0, zwanzigmal 0 ist 0. Auf die ganze Summe
        // gerechnet sind es 19.
        Shopregel.Handel handel = Shopregel.rechne(1, 19L, 20, 5);

        assertEquals(380L, handel.preis());
        assertEquals(19L, handel.gebuehr(), "5 Prozent von 380, nicht 20 mal 0");
        assertEquals(361L, handel.erloes());
    }

    @Test
    @DisplayName("Die Gebuehr nimmt NIE den ganzen Erloes")
    void gebuehrNimmtNichtAlles() {
        // Bei der hoechsten Gebuehr von 50 Prozent bleibt immer die Haelfte. Geprueft
        // wird die Rechnung und die Zusage des Satzes zusammen: Ein Erloes, der nicht
        // Preis minus Gebuehr ist, wirft.
        Shopregel.Handel handel = Shopregel.rechne(1, 1_000L, 1, 50);

        assertEquals(500L, handel.gebuehr());
        assertEquals(500L, handel.erloes());
    }

    @Test
    @DisplayName("Eine ueberlaufende STUECKZAHL wirft, statt negativ zu werden")
    void ueberlaufWirft() {
        // multiplyExact und nicht *: Ein uebergelaufener int ist NEGATIV, und eine
        // negative Stueckzahl wirft erst im Satz Handel - mit einer Meldung, die vom
        // Ueberlauf nichts sagt. Lieber hier, wo er entsteht.
        //
        // Geprueft wird die STUECKZAHL und nicht der Preis, und das ist beim Schreiben
        // dieser Probe aufgefallen: Der PREIS kann gar nicht ueberlaufen. Bei hoechstens
        // 10 Millionen je Stufe und hoechstens 2,1 Milliarden Stufen sind es 2,1 mal
        // 10^16 - ein long haelt 9,2 mal 10^18. Die Grenzen schliessen den Fall also
        // schon aus; multiplyExact bleibt dort als Guertel und ist unerreichbar.
        assertThrows(ArithmeticException.class,
                () -> Shopregel.rechne(Kistenshop.GROESSTE_MENGE, 1L, Integer.MAX_VALUE,
                        0));
    }

    @Test
    @DisplayName("Jede Zahl, die keine ist, wird abgewiesen")
    void zahlenGrenzen() {
        assertThrows(IllegalArgumentException.class, () -> Shopregel.rechne(0, 100L, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Shopregel.rechne(Kistenshop.GROESSTE_MENGE + 1, 100L, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> Shopregel.rechne(1, 0L, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> Shopregel.rechne(1, -1L, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> Shopregel.rechne(1, 100L, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Shopregel.rechne(1, 100L, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> Shopregel.rechne(1, 100L, 1,
                Shopregel.HOECHSTE_GEBUEHR_PROZENT + 1));
    }

    @Test
    @DisplayName("Die hoechste Gebuehr ist die Haelfte, nicht alles")
    void gebuehrHatEineGrenze() {
        // Eine Gebuehr, die den ganzen Erloes nimmt, ist keine Gebuehr, sondern eine
        // Enteignung - und ein Betreiber, der sie aus Versehen einstellt, merkt es erst,
        // wenn die ersten Spieler aufhoeren zu handeln.
        assertEquals(50, Shopregel.HOECHSTE_GEBUEHR_PROZENT);
        assertTrue(Shopregel.brauchbareGebuehr(0));
        assertTrue(Shopregel.brauchbareGebuehr(50));
        assertFalse(Shopregel.brauchbareGebuehr(51));
        assertFalse(Shopregel.brauchbareGebuehr(-1));
    }

    // ================================================== der Sneak-Klick

    @Test
    @DisplayName("Der Sneak-Klick nimmt die KLEINSTE der drei Grenzen")
    void sneakNimmtDieKleinsteGrenze() {
        // Lumia handelte beim Sneak-Klick "einen ganzen Stack". Was das ist, haengt an
        // drei Grenzen, und die kleinste gilt. Jede wird einzeln gefahren, damit keine
        // von ihnen stumm uebersehen werden kann.
        //
        // Shopmenge 1, Preis 10: Ein Stapel waere 64 Stufen.
        assertEquals(5, Shopregel.passendeStufen(10L, 50L, 999, 999, 1),
                "das GELD reicht nur fuer 5");
        assertEquals(7, Shopregel.passendeStufen(10L, 9_999L, 7, 999, 1),
                "der VORRAT reicht nur fuer 7");
        assertEquals(3, Shopregel.passendeStufen(10L, 9_999L, 999, 3, 1),
                "der PLATZ reicht nur fuer 3");
    }

    @Test
    @DisplayName("Nie mehr als ein Stapel je Klick")
    void nichtMehrAlsEinStapel() {
        // Ohne diese Grenze raeumte ein Sneak-Klick eine volle Doppelkiste in einem Zug -
        // und der Kunde haette 54 Stapel, von denen 18 in sein Inventar passen und der
        // Rest auf dem Boden laege. Genau dort entstehen die Verluste, die niemand
        // zurueckbekommt.
        assertEquals(64, Shopregel.passendeStufen(1L, 999_999L, 99_999, 99_999, 1),
                "bei Shopmenge 1 sind 64 Stufen ein Stapel");
        assertEquals(4, Shopregel.passendeStufen(1L, 999_999L, 99_999, 99_999, 16),
                "bei Shopmenge 16 sind 4 Stufen ein Stapel");
    }

    @Test
    @DisplayName("Eine Shopmenge ueber einem Stapel ergibt GENAU eine Stufe")
    void grosseShopmengeGibtEineStufe() {
        // Bei Shopmenge 100 ist eine Stufe schon mehr als ein Stapel. Dann ist die
        // Antwort 1 und nicht 0: Ein Sneak-Klick soll nie WENIGER handeln als ein
        // einfacher.
        assertEquals(1, Shopregel.passendeStufen(1L, 999_999L, 99_999, 99_999, 100));
    }

    @Test
    @DisplayName("Reicht es nicht fuer eine Stufe, sind es 0 Stufen")
    void nullStufenIstEineAntwort() {
        // Der Aufrufer macht daraus eine Absage, die sagt, WAS fehlt. Diese Rechnung
        // sagt nur, wie viel geht - und 0 ist eine ehrliche Antwort und kein Fehler.
        assertEquals(0, Shopregel.passendeStufen(100L, 99L, 999, 999, 1), "kein Geld");
        assertEquals(0, Shopregel.passendeStufen(10L, 9_999L, 15, 999, 16),
                "kein voller Vorrat");
        assertEquals(0, Shopregel.passendeStufen(10L, 9_999L, 999, 0, 1), "kein Platz");
    }

    @Test
    @DisplayName("Negativer Vorrat und negativer Platz gelten als nichts")
    void negativesGiltAlsNichts() {
        // Kommt aus einem Programmierfehler der Schicht darueber. Eine 0 statt einer
        // negativen Zahl ist die sichere Richtung: Lieber kein Handel als einer, dessen
        // Stufenzahl aus einem Vorzeichenfehler stammt.
        assertEquals(0, Shopregel.passendeStufen(10L, 9_999L, -5, 999, 1));
        assertEquals(0, Shopregel.passendeStufen(10L, 9_999L, 999, -5, 1));
    }

    @Test
    @DisplayName("Ohne Stufenpreis und Shopmenge gibt es keine Stufen")
    void sneakBrauchtSeineZahlen() {
        assertThrows(IllegalArgumentException.class,
                () -> Shopregel.passendeStufen(0L, 100L, 10, 10, 1));
        assertThrows(IllegalArgumentException.class,
                () -> Shopregel.passendeStufen(10L, 100L, 10, 10, 0));
    }
}
