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

    // ================================================== Stufung und Stapel

    @Test
    @DisplayName("Eine Stufung braucht eine ECHTE Stapelgroesse - geraten wird nicht")
    void stufungBrauchtEchteZahlen() {
        // Die Stapelgroesse kommt gemessen vom Gegenstand und nicht als Vorgabe: Eine
        // 64 fuer alles ist bei Enderperlen (16) und bei jedem Werkzeug (1) falsch, und
        // verzauberte Ausruestung ist seit dem 08.11.2026 der Normalfall.
        //
        // Geworfen wird, statt auf 64 zurueckzufallen, und das ist die SICHERE Richtung:
        // Ein Rueckfall auf 64 machte aus einem Gegenstand, dessen Groesse niemand
        // messen konnte, den groesstmoeglichen Handel.
        assertThrows(IllegalArgumentException.class, () -> new Shopregel.Stufung(16, 0));
        assertThrows(IllegalArgumentException.class, () -> new Shopregel.Stufung(16, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new Shopregel.Stufung(16, Shopregel.GROESSTER_STAPEL + 1));
        assertThrows(IllegalArgumentException.class, () -> new Shopregel.Stufung(0, 64));
        // Die Raender gehen durch.
        assertEquals(1, new Shopregel.Stufung(1, 1).stapelgroesse());
        assertEquals(Shopregel.GROESSTER_STAPEL,
                new Shopregel.Stufung(1, Shopregel.GROESSTER_STAPEL).stapelgroesse());
    }

    // ================================================== der Sneak-Klick

    @Test
    @DisplayName("Der Sneak-Klick nimmt die KLEINSTE der drei Grenzen")
    void sneakNimmtDieKleinsteGrenze() {
        // Lumia handelte beim Sneak-Klick "einen ganzen Stack". Was das ist, haengt an
        // drei Grenzen, und die kleinste gilt. Jede wird einzeln gefahren, damit keine
        // von ihnen stumm uebersehen werden kann.
        //
        // Shopmenge 1, Stapel 64, Preis 10: Ein Stapel waere 64 Stufen.
        Shopregel.Stufung einzeln = new Shopregel.Stufung(1, 64);

        assertEquals(5, Shopregel.passendeStufen(10L, 50L, 999, 999, einzeln),
                "das GELD reicht nur fuer 5");
        assertEquals(7, Shopregel.passendeStufen(10L, 9_999L, 7, 999, einzeln),
                "der VORRAT reicht nur fuer 7");
        assertEquals(3, Shopregel.passendeStufen(10L, 9_999L, 999, 3, einzeln),
                "der PLATZ reicht nur fuer 3");
    }

    @Test
    @DisplayName("Nie mehr als ein Stapel je Klick - und ein Stapel ist WARENABHAENGIG")
    void nichtMehrAlsEinStapel() {
        // Ohne diese Grenze raeumte ein Sneak-Klick eine volle Doppelkiste in einem Zug -
        // und der Kunde haette 54 Stapel, von denen 18 in sein Inventar passen und der
        // Rest auf dem Boden laege. Genau dort entstehen die Verluste, die niemand
        // zurueckbekommt.
        assertEquals(64, Shopregel.passendeStufen(1L, 999_999L, 99_999, 99_999,
                new Shopregel.Stufung(1, 64)),
                "Stapel 64, Shopmenge 1: 64 Stufen sind ein Stapel");
        assertEquals(4, Shopregel.passendeStufen(1L, 999_999L, 99_999, 99_999,
                new Shopregel.Stufung(16, 64)),
                "Stapel 64, Shopmenge 16: 4 Stufen sind ein Stapel");
    }

    @Test
    @DisplayName("Eine Ware, die zu 16 stapelt, gibt auch nur 16 Stufen")
    void kleinereStapelgroesseBegrenzt() {
        // BEFUND vom 08.11.2026: Hier stand eine feste 64, und sie ist nur die Groesse
        // der GEWOEHNLICHEN Ware. Enderperle und Schneeball stapeln zu 16, Werkzeug und
        // Ruestung zu 1 - und verzauberte Ausruestung ist der Normalfall, seit es das
        // Fragezeichen auf dem Schild gibt.
        //
        // Mit der festen 64 forderte ein Sneak-Klick an einem Spitzhacken-Shop mit
        // Menge 1 vierundsechzig Stufen an. Beim Kauf haette der Platz gebremst; beim
        // ANKAUF verkaufte ein Spieler mit einem Klick 54 Spitzhacken in eine
        // Doppelkiste, obwohl er einen Stapel gemeint hat - und die Zusage "nie mehr als
        // ein Stapel je Klick" waere eine Zeile Doku ohne Deckung gewesen.
        assertEquals(16, Shopregel.passendeStufen(1L, 999_999L, 99_999, 99_999,
                new Shopregel.Stufung(1, 16)), "Enderperlen stapeln zu 16");
        assertEquals(1, Shopregel.passendeStufen(1L, 999_999L, 99_999, 99_999,
                new Shopregel.Stufung(1, 1)),
                "eine Spitzhacke ist ein Stapel - der Sneak-Klick handelt genau eine");
        assertEquals(2, Shopregel.passendeStufen(1L, 999_999L, 99_999, 99_999,
                new Shopregel.Stufung(8, 16)), "Stapel 16, Shopmenge 8: 2 Stufen");
    }

    @Test
    @DisplayName("Eine Shopmenge ueber einem Stapel ergibt GENAU eine Stufe")
    void grosseShopmengeGibtEineStufe() {
        // Bei Shopmenge 100 und Stapel 64 ist eine Stufe schon mehr als ein Stapel. Dann
        // ist die Antwort 1 und nicht 0: Ein Sneak-Klick soll nie WENIGER handeln als ein
        // einfacher.
        assertEquals(1, Shopregel.passendeStufen(1L, 999_999L, 99_999, 99_999,
                new Shopregel.Stufung(100, 64)));
    }

    @Test
    @DisplayName("Reicht es nicht fuer eine Stufe, sind es 0 Stufen")
    void nullStufenIstEineAntwort() {
        // Der Aufrufer macht daraus eine Absage, die sagt, WAS fehlt. Diese Rechnung
        // sagt nur, wie viel geht - und 0 ist eine ehrliche Antwort und kein Fehler.
        Shopregel.Stufung einzeln = new Shopregel.Stufung(1, 64);
        Shopregel.Stufung sechzehn = new Shopregel.Stufung(16, 64);

        assertEquals(0, Shopregel.passendeStufen(100L, 99L, 999, 999, einzeln),
                "kein Geld");
        assertEquals(0, Shopregel.passendeStufen(10L, 9_999L, 15, 999, sechzehn),
                "kein voller Vorrat");
        assertEquals(0, Shopregel.passendeStufen(10L, 9_999L, 999, 0, einzeln),
                "kein Platz");
    }

    @Test
    @DisplayName("Negativer Vorrat und negativer Platz gelten als nichts")
    void negativesGiltAlsNichts() {
        // Kommt aus einem Programmierfehler der Schicht darueber. Eine 0 statt einer
        // negativen Zahl ist die sichere Richtung: Lieber kein Handel als einer, dessen
        // Stufenzahl aus einem Vorzeichenfehler stammt.
        Shopregel.Stufung einzeln = new Shopregel.Stufung(1, 64);

        assertEquals(0, Shopregel.passendeStufen(10L, 9_999L, -5, 999, einzeln));
        assertEquals(0, Shopregel.passendeStufen(10L, 9_999L, 999, -5, einzeln));
    }

    @Test
    @DisplayName("Ohne Stufenpreis und ohne Stufung gibt es keine Stufen")
    void sneakBrauchtSeineZahlen() {
        assertThrows(IllegalArgumentException.class,
                () -> Shopregel.passendeStufen(0L, 100L, 10, 10,
                        new Shopregel.Stufung(1, 64)));
        assertThrows(IllegalArgumentException.class,
                () -> Shopregel.passendeStufen(10L, 100L, 10, 10, null));
    }

    // ================================================== Vorrat und Platz in STUECK

    @Test
    @DisplayName("Platz in Stueck: freie Faecher mal Stapel, plus die angefangenen")
    void platzRechnetFaecherUndReste() {
        // Die Einheit ist STUECK und nicht Plaetze, und das ist der Unterschied, an dem
        // ein Shop mit Menge 64 "kein Platz" gemeldet haette, immer: 36 Plaetze geteilt
        // durch 64 ist 0.
        assertEquals(128, Shopregel.platzInStueck(2, 64, null),
                "zwei leere Faecher, Stapel 64");
        assertEquals(128, Shopregel.platzInStueck(2, 64, new int[0]),
                "ein leeres Feld ist dasselbe wie keines");
        assertEquals(0, Shopregel.platzInStueck(0, 64, null), "nichts frei");
        // Angefangene Stapel: was noch darauf passt.
        assertEquals(32, Shopregel.platzInStueck(0, 64, new int[] {32}));
        assertEquals(96, Shopregel.platzInStueck(1, 64, new int[] {32}));
        assertEquals(0, Shopregel.platzInStueck(0, 64, new int[] {64}),
                "ein voller Stapel nimmt nichts mehr auf");
    }

    @Test
    @DisplayName("Der haeufigste Kauffall: 35 volle Stapel und ein halber")
    void derHaeufigsteKauffall() {
        // BEFUND vom 08.11.2026 und der Grund, warum diese Rechnung nicht die des
        // Abholfachs ist. Dort zaehlt freiePlaetze nur GANZ freie Plaetze und
        // unterschaetzt absichtlich: Jedes Abholgut ist ein beliebiger ganzer Stapel
        // unbekannter Art, und worauf er sich legen liesse, ist nicht vorherzusagen.
        //
        // Beim Shop ist die Ware EINE bekannte Art - die genaue Zahl ist berechenbar,
        // und eine Unterschaetzung waere keine Vorsicht, sondern eine falsche Absage.
        // Und zwar im haeufigsten Kauffall ueberhaupt: Wer Erde kauft, traegt meistens
        // schon Erde.
        int[] fuenfunddreissigVolle = new int[35];
        java.util.Arrays.fill(fuenfunddreissigVolle, 64);
        int[] mitHalbem = new int[36];
        java.util.Arrays.fill(mitHalbem, 64);
        mitHalbem[35] = 32;

        assertEquals(0, Shopregel.platzInStueck(0, 64, fuenfunddreissigVolle),
                "35 volle Stapel und ein freies Feld gibt es hier nicht - 0 freie");
        assertEquals(32, Shopregel.platzInStueck(0, 64, mitHalbem),
                "32 Stueck gehen hinein, obwohl kein Feld frei ist");
    }

    @Test
    @DisplayName("Ein Stapel ueber der Groesse nimmt nichts auf - und wirft nicht")
    void zuGrosserStapelGiltAlsVoll() {
        // Entsteht nur nach einem Spielupdate, das die Stapelgroesse einer Ware GESENKT
        // hat. Dann ist "kein Platz" die sichere Antwort - eine negative Zahl zoege von
        // den freien Faechern ab und machte aus einem leeren Rucksack einen vollen.
        assertEquals(64, Shopregel.platzInStueck(1, 64, new int[] {99}));
    }

    @Test
    @DisplayName("Vorrat in Stueck ist die Summe der Mengen")
    void vorratSummiert() {
        assertEquals(0, Shopregel.vorratInStueck(null));
        assertEquals(0, Shopregel.vorratInStueck(new int[0]));
        assertEquals(96, Shopregel.vorratInStueck(new int[] {64, 32}));
    }

    @Test
    @DisplayName("Leere Plaetze gehoeren nicht in die Mengen")
    void leerePlaetzeWerdenAbgewiesen() {
        // Eine 0 in der Liste waere ein leerer Platz, und der zaehlt in freieFaecher.
        // Beides gleichzeitig waere derselbe Platz zweimal - beim Platz zu viel, beim
        // Vorrat harmlos. Darum wirft es an beiden Stellen.
        assertThrows(IllegalArgumentException.class,
                () -> Shopregel.platzInStueck(1, 64, new int[] {0}));
        assertThrows(IllegalArgumentException.class,
                () -> Shopregel.vorratInStueck(new int[] {0}));
        assertThrows(IllegalArgumentException.class,
                () -> Shopregel.platzInStueck(-1, 64, null));
        assertThrows(IllegalArgumentException.class,
                () -> Shopregel.platzInStueck(1, 0, null));
    }

    @Test
    @DisplayName("Beide Zahlen sind auf eine Doppelkiste gedeckelt")
    void beideZahlenSindGedeckelt() {
        // Gedeckelt und nicht geworfen: Die Zahlen sind MESSUNGEN und keine
        // Einstellungen, und ein voller Rucksack samt voller Doppelkiste liegt schon in
        // der Groessenordnung. Der Deckel ist die sichere Richtung - er kann einen Handel
        // kleiner machen, nie groesser.
        int[] vieleVolle = new int[200];
        java.util.Arrays.fill(vieleVolle, 99);

        assertEquals(Kistenshop.GROESSTE_MENGE, Shopregel.vorratInStueck(vieleVolle));
        assertEquals(Kistenshop.GROESSTE_MENGE,
                Shopregel.platzInStueck(1_000, 99, null));
    }

    // ================================================== die Gebuehrzeile

    @Test
    @DisplayName("Eine Gebuehr von 0 bekommt KEINE Zeile im Buch")
    void nullgebuehrBekommtKeineZeile() {
        // Gefragt wird die GERECHNETE Gebuehr und nicht der Satz, und das ist der
        // Unterschied, an dem eine Zeile ueber 0 Mark entsteht: Bei 5 Prozent auf 19
        // Mark ist die Gebuehr abgerundet 0. Wer an erhebtGebuehr() haengt, schreibt
        // dann eine Zeile ohne Geldbewegung - und genau das verbietet die Kasse.
        assertFalse(Shopregel.rechne(1, 19L, 1, 5).kostetGebuehr(),
                "5 Prozent von 19 Mark sind abgerundet 0");
        assertFalse(Shopregel.rechne(1, 1_000L, 1, 0).kostetGebuehr(),
                "ohne Satz keine Gebuehr");
        assertTrue(Shopregel.rechne(1, 1_000L, 1, 7).kostetGebuehr(),
                "7 Prozent von 1.000 Mark sind 70");
        // Und die Zahl stimmt: Die Frage darf nicht von der Hoehe abweichen.
        assertEquals(70L, Shopregel.rechne(1, 1_000L, 1, 7).gebuehr());
        assertEquals(0L, Shopregel.rechne(1, 19L, 1, 5).gebuehr());
    }
}
