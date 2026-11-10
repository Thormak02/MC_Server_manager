package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die vier Schildzeilen - und die Preisform, an der ChestShop seinen Dupe hatte.
 *
 * <p>Die wichtigsten Proben dieser Datei sind die, die etwas ABWEISEN:
 * {@link #keinEInDerZahl} und {@link #keinPunktKeinSuffix}. ChestShops Preis-Regex
 * erlaubt {@code [.e]} bis heute, und das {@code E} in einem Preis war 2014 ein belegter
 * Dupe (#30).
 */
@DisplayName("Das Shopschild")
class ShopschildTest {

    private static final String BUND = "Bund";

    private static String[] schild(String eins, String zwei, String drei, String vier) {
        return new String[] {eins, zwei, drei, vier};
    }

    private static Shopschild.Gelesen gut(String zwei, String drei, String vier) {
        Shopschild.Ergebnis ergebnis =
                Shopschild.lies(schild("", zwei, drei, vier), BUND);
        assertTrue(ergebnis.gelungen(), "haette gelingen muessen: " + ergebnis.fehler()
                + " in Zeile " + ergebnis.zeile());
        return ergebnis.schild();
    }

    private static void schlecht(Shopschild.Fehler erwartet, int zeile, String zwei,
            String drei, String vier) {
        Shopschild.Ergebnis ergebnis =
                Shopschild.lies(schild("", zwei, drei, vier), BUND);
        assertFalse(ergebnis.gelungen(), "haette scheitern muessen");
        assertEquals(erwartet, ergebnis.fehler());
        // Die ZEILE wird mitgeprueft: Ein Spieler, der ein Schild falsch beschriftet,
        // soll lesen, wo er nachsehen muss. Eine Sammelabsage liesse ihn alle vier
        // durchprobieren.
        assertEquals(zeile, ergebnis.zeile(), "die genannte Zeile");
    }

    // ==================================================================== Der Normalfall

    @Test
    @DisplayName("Der Lumia-Normalfall: Menge, B:S, Fragezeichen")
    void derNormalfall() {
        // Genau das Beispiel aus der Lumia-Wikiseite vom 11.01.2016: erste Zeile leer,
        // Menge, Preise, und ein ? fuer die Ware aus der Kiste.
        Shopschild.Gelesen gelesen = gut("16", "B 100 : S 80", "?");

        assertEquals("", gelesen.besitzername(), "leer heisst: der Ersteller");
        assertFalse(gelesen.bundshop());
        assertEquals(16, gelesen.menge());
        assertEquals(100L, gelesen.kaufpreis());
        assertEquals(80L, gelesen.verkaufpreis());
        assertEquals(Shopschild.Warenangabe.AUS_KISTE, gelesen.warenangabe());
        assertEquals("", gelesen.material());
    }

    @Test
    @DisplayName("Ein getippter Materialname kommt als Name durch")
    void materialnameWirdGelesen() {
        Shopschild.Gelesen gelesen = gut("64", "B 10", "STONE");

        assertEquals(Shopschild.Warenangabe.NAME, gelesen.warenangabe());
        assertEquals("STONE", gelesen.material());
        assertEquals(0L, gelesen.verkaufpreis(), "ohne S wird nicht angekauft");
    }

    @Test
    @DisplayName("Leerzeichen und Gross-/Kleinschreibung sind gleich")
    void leerzeichenUndSchreibweise() {
        // EINE Form, aber tolerant im Weissraum: B100:S80 und b 100 : s 80 sind dasselbe.
        // ChestShop erlaubt stattdessen fuenf verschiedene Formen - das ist die Ursache
        // seiner eigenen Doku-Schwaeche.
        for (String form : new String[] {"B100:S80", "B 100 : S 80", "b100:s80",
                "  B 100:S 80  ", "B100 : S80"}) {
            Shopschild.Gelesen gelesen = gut("16", form, "?");
            assertEquals(100L, gelesen.kaufpreis(), form);
            assertEquals(80L, gelesen.verkaufpreis(), form);
        }
    }

    @Test
    @DisplayName("Die Reihenfolge S vor B geht auch")
    void reihenfolgeDerPreiseIstFrei() {
        // Welcher Buchstabe zuerst steht, ist gleich - er bestimmt die Bedeutung, nicht
        // die Stelle. Ein Spieler, der S vor B tippt, soll nicht raten muessen.
        Shopschild.Gelesen gelesen = gut("16", "S 80 : B 100", "?");

        assertEquals(100L, gelesen.kaufpreis());
        assertEquals(80L, gelesen.verkaufpreis());
    }

    @Test
    @DisplayName("Nur S ist ein Ankaufstand")
    void nurSIstEinAnkaufstand() {
        Shopschild.Gelesen gelesen = gut("1", "S 5000", "?");

        assertEquals(0L, gelesen.kaufpreis());
        assertEquals(5_000L, gelesen.verkaufpreis());
    }

    // ==================================================================== Die Preisform

    @Test
    @DisplayName("Ein E in der Zahl wird ABGEWIESEN - ChestShops Dupe von 2014")
    void keinEInDerZahl() {
        // Der belegte Fall: ChestShop #30, PR "Duplication glitch patch" - "This prevents
        // duplications by using the 'E' in a price of a sign." Die Preis-Regex dort
        // erlaubt [.e] bis heute.
        //
        // Und QuickShop #1902: 1E99 legte das Schild, aber keinen Shop dahinter - also
        // ein freies Schild zum Abbauen -, und sehr hohe Preise fielen stumm auf 10
        // zurueck, zu dem Spieler dann kauften.
        schlecht(Shopschild.Fehler.PREIS_KEINE_ZAHL, 3, "16", "B 1e3", "?");
        schlecht(Shopschild.Fehler.PREIS_KEINE_ZAHL, 3, "16", "B 1E99", "?");
        schlecht(Shopschild.Fehler.PREIS_KEINE_ZAHL, 3, "16", "B 100 : S 1e2", "?");
    }

    @Test
    @DisplayName("Punkt, Komma und die Suffixe K und M werden abgewiesen")
    void keinPunktKeinSuffix() {
        // Mark sind ganzzahlig. ChestShop kennt K = mal 1000 und M = mal 1000000; ein
        // Spieler, der "B 1K" meint und "B 1" tippt, verkauft fuer ein Tausendstel - und
        // einer, der "B 1,5" tippt, weiss nicht, was geschieht.
        schlecht(Shopschild.Fehler.PREIS_KEINE_ZAHL, 3, "16", "B 10.5", "?");
        schlecht(Shopschild.Fehler.PREIS_KEINE_ZAHL, 3, "16", "B 10,5", "?");
        schlecht(Shopschild.Fehler.PREIS_KEINE_ZAHL, 3, "16", "B 1K", "?");
        schlecht(Shopschild.Fehler.PREIS_KEINE_ZAHL, 3, "16", "B 1M", "?");
        schlecht(Shopschild.Fehler.PREIS_KEINE_ZAHL, 3, "16", "B -5", "?");
    }

    @Test
    @DisplayName("Die Zahl VOR dem Buchstaben wird abgewiesen")
    void zahlVorBuchstabeGehtNicht() {
        // ChestShop erlaubt "10 B" neben "B 10". Hier gilt eine Form, und die Absage
        // nennt sie - das ist weniger bequem und dafuer lesbar.
        schlecht(Shopschild.Fehler.PREIS_FORM, 3, "16", "100B", "?");
        schlecht(Shopschild.Fehler.PREIS_FORM, 3, "16", "100 B : 80 S", "?");
    }

    @Test
    @DisplayName("Das Wort free und jeder andere Buchstabe werden abgewiesen")
    void keinFreeUndKeinFremderBuchstabe() {
        // ChestShop kennt "free". Ein kostenloser Shop ist hier kein Shop: Beide Preise
        // 0 ist ein Schild, bei dem einem Kunden nichts geschieht.
        schlecht(Shopschild.Fehler.PREIS_FORM, 3, "16", "free", "?");
        schlecht(Shopschild.Fehler.PREIS_FORM, 3, "16", "P 100", "?");
    }

    @Test
    @DisplayName("Zweimal dieselbe Preisart ist keine Form")
    void zweimalDieselbeArt() {
        // "B 10 : B 20" bedeutet nichts. Ohne diese Pruefung gewinnt stillschweigend die
        // zweite Zahl - und der Spieler sieht auf dem Schild etwas anderes als im Shop.
        schlecht(Shopschild.Fehler.PREIS_FORM, 3, "16", "B 10 : B 20", "?");
        schlecht(Shopschild.Fehler.PREIS_FORM, 3, "16", "S 10 : S 20", "?");
    }

    @Test
    @DisplayName("Mehr als ein Doppelpunkt und eine leere Haelfte werden abgewiesen")
    void doppelpunktGenauEinmal() {
        schlecht(Shopschild.Fehler.PREIS_FORM, 3, "16", "B 10 : S 5 : B 1", "?");
        schlecht(Shopschild.Fehler.PREIS_FORM, 3, "16", "B 10 :", "?");
        schlecht(Shopschild.Fehler.PREIS_FORM, 3, "16", ": S 5", "?");
        schlecht(Shopschild.Fehler.PREIS_FORM, 3, "16", "", "?");
        schlecht(Shopschild.Fehler.PREIS_KEINE_ZAHL, 3, "16", "B", "?");
    }

    @Test
    @DisplayName("Ein Preis ueber der Obergrenze wird abgewiesen")
    void preisHatEineObergrenze() {
        schlecht(Shopschild.Fehler.PREIS_AUSSERHALB, 3, "16",
                "B " + (Kistenshop.HOECHSTER_PREIS + 1L), "?");
        // Der Rand geht durch.
        assertEquals(Kistenshop.HOECHSTER_PREIS,
                gut("16", "B " + Kistenshop.HOECHSTER_PREIS, "?").kaufpreis());
    }

    @Test
    @DisplayName("Eine zwanzigstellige Zahl faellt auf, BEVOR daraus eine Zahl wird")
    void zuLangeZiffernfolge() {
        // Long.parseLong wuerfe hier eine Ausnahme mitten im Lesen - und ein Wurf beim
        // Schildschreiben liesse den Spieler mit einem halben Schild stehen. Die
        // Laengengrenze steht darum VOR der Umwandlung.
        schlecht(Shopschild.Fehler.PREIS_KEINE_ZAHL, 3, "16",
                "B 99999999999999999999", "?");
        schlecht(Shopschild.Fehler.MENGE_KEINE_ZAHL, 2, "99999999999999999999",
                "B 10", "?");
    }

    @Test
    @DisplayName("Beide Preise 0 ist ein Schild, das nichts handelt")
    void beidePreiseNull() {
        schlecht(Shopschild.Fehler.PREIS_BEIDE_NULL, 3, "16", "B 0 : S 0", "?");
        schlecht(Shopschild.Fehler.PREIS_BEIDE_NULL, 3, "16", "B 0", "?");
    }

    @Test
    @DisplayName("Der Verkaufspreis ueber dem Kaufpreis wird am SCHILD abgewiesen")
    void verdrehtePreiseAmSchild() {
        // Dieselbe Schranke wie im Satz Kistenshop, und hier noch einmal: Ein Spieler
        // soll sie beim SCHREIBEN lesen und nicht erst, wenn das Schild schon haengt und
        // das Programm es stumm wieder abnimmt.
        schlecht(Shopschild.Fehler.PREIS_VERDREHT, 3, "16", "B 100 : S 101", "?");
        // Gleich ist erlaubt - ein Umtausch ist keine Quelle.
        assertEquals(100L, gut("16", "B 100 : S 100", "?").verkaufpreis());
    }

    // ==================================================================== Menge und Ware

    @Test
    @DisplayName("Die Menge reicht bis zur vollen Doppelkiste")
    void mengeHatGrenzen() {
        assertEquals(Kistenshop.GROESSTE_MENGE,
                gut(String.valueOf(Kistenshop.GROESSTE_MENGE), "B 10", "?").menge());
        schlecht(Shopschild.Fehler.MENGE_AUSSERHALB, 2,
                String.valueOf(Kistenshop.GROESSTE_MENGE + 1), "B 10", "?");
        schlecht(Shopschild.Fehler.MENGE_AUSSERHALB, 2, "0", "B 10", "?");
        schlecht(Shopschild.Fehler.MENGE_KEINE_ZAHL, 2, "viele", "B 10", "?");
        schlecht(Shopschild.Fehler.MENGE_KEINE_ZAHL, 2, "", "B 10", "?");
        schlecht(Shopschild.Fehler.MENGE_KEINE_ZAHL, 2, "-5", "B 10", "?");
    }

    @Test
    @DisplayName("Eine leere vierte Zeile ist keine Ware")
    void wareFehlt() {
        schlecht(Shopschild.Fehler.WARE_FEHLT, 4, "16", "B 10", "");
        schlecht(Shopschild.Fehler.WARE_FEHLT, 4, "16", "B 10", "   ");
    }

    @Test
    @DisplayName("Ein Schild ohne vier Zeilen ist kein Schild")
    void keinSchild() {
        assertEquals(Shopschild.Fehler.KEIN_SCHILD,
                Shopschild.lies(null, BUND).fehler());
        assertEquals(Shopschild.Fehler.KEIN_SCHILD,
                Shopschild.lies(new String[] {"", "16", "B 10"}, BUND).fehler());
    }

    @Test
    @DisplayName("Eine null-Zeile gilt als leer und wirft nicht")
    void nullZeileGiltAlsLeer() {
        // Paper gibt leere Schildzeilen als leeren Text, aber ein anderer Weg - eine
        // Probe, ein Fremdplugin - kann null liefern. Ein Wurf daraus waere ein
        // abgebrochenes Schildschreiben.
        Shopschild.Ergebnis ergebnis =
                Shopschild.lies(new String[] {null, "16", "B 10", "?"}, BUND);

        assertTrue(ergebnis.gelungen());
        assertEquals("", ergebnis.schild().besitzername());
    }

    // ==================================================================== Das Bundwort

    @Test
    @DisplayName("Das Bundwort wird ohne Leerzeichen und ohne Schreibweise erkannt")
    void bundwortWirdErkannt() {
        // Dieselbe Form wie ChestShops isAdminShop: Das Wort kann aus zwei Worten
        // bestehen, und ein Spieler, der eines mit zwei Leerzeichen tippt, soll dasselbe
        // Schild bekommen.
        for (String form : new String[] {"Bund", "bund", "BUND", " Bund ", "B und"}) {
            Shopschild.Ergebnis ergebnis =
                    Shopschild.lies(schild(form, "16", "B 10", "?"), BUND);
            assertTrue(ergebnis.gelungen(), form);
            assertTrue(ergebnis.schild().bundshop(), form);
        }
    }

    @Test
    @DisplayName("Ein Spielername in Zeile 1 ist KEIN Bundshop")
    void spielernameIstKeinBundshop() {
        Shopschild.Ergebnis ergebnis =
                Shopschild.lies(schild("Anna", "16", "B 10", "?"), BUND);

        assertTrue(ergebnis.gelungen());
        assertFalse(ergebnis.schild().bundshop());
        assertEquals("Anna", ergebnis.schild().besitzername());
    }

    @Test
    @DisplayName("Ohne Bundwort gibt es keinen Bundshop ueber das Schild")
    void ohneBundwortKeinBundshop() {
        // Eine zulaessige Einstellung: Ein Betreiber, der keine Bundshops will, laesst
        // das Wort leer. Dann ist "Bund" ein gewoehnlicher Name - und null darf er auch
        // uebergeben, ohne dass etwas wirft.
        for (String kein : new String[] {"", "  ", null}) {
            Shopschild.Ergebnis ergebnis =
                    Shopschild.lies(schild("Bund", "16", "B 10", "?"), kein);
            assertTrue(ergebnis.gelungen());
            assertFalse(ergebnis.schild().bundshop(), String.valueOf(kein));
        }
        assertFalse(Shopschild.istBundwort("Bund", null));
        assertFalse(Shopschild.istBundwort(null, "Bund"));
    }

    // ==================================================================== Zurueckschreiben

    @Test
    @DisplayName("Geschrieben und wieder gelesen ergibt denselben Shop")
    void hinUndZurueck() {
        // Die wichtigste Probe des Zurueckschreibens: Was das Programm auf das Schild
        // schreibt, muss es selbst wieder lesen koennen. Sonst steht nach dem Anlegen
        // ein Schild da, das beim naechsten Bearbeiten als falsch gilt.
        Kistenshop shop = new Kistenshop("freimarken:altfurt", 60, 71, 6, 61, 71, 6,
                UUID.randomUUID(), 16, 100L, 80L, new Warenprobe("Stein", "inhalt"), Set.of());

        String[] zeilen = Shopschild.schreibe("Anna", "Bund", shop);

        assertArrayEquals(new String[] {"Anna", "16", "B 100 : S 80", "Stein"}, zeilen);
        Shopschild.Ergebnis zurueck = Shopschild.lies(zeilen, BUND);
        assertTrue(zurueck.gelungen(), String.valueOf(zurueck.fehler()));
        assertEquals(16, zurueck.schild().menge());
        assertEquals(100L, zurueck.schild().kaufpreis());
        assertEquals(80L, zurueck.schild().verkaufpreis());
        assertEquals("Stein", zurueck.schild().material());
    }

    @Test
    @DisplayName("Ein BUNDSHOP bekommt das Bundwort in die erste Zeile, kein Spielername")
    void bundshopTraegtDasBundwort() {
        // BEFUND DER EINSPRITZUNG B20 vom 10.11.2026, und zugleich der Fund desselben
        // Tages: Die eine Aufrufstelle gab IMMER den Spielernamen, und ein frisch
        // aufgestellter Bundshop trug in der ersten Zeile den Namen dessen, der ihn
        // hingestellt hat. Er sah aus wie der Laden eines Spielers, waehrend er dem Bund
        // gehoert - gehandelt wurde richtig, denn die Zeile in der Datenbank ist die
        // Wahrheit und nicht das Holz, aber jeder Kunde las etwas Falsches.
        //
        // Gefangen hat es keine Probe: Das Zurueckschreiben war nur mit einem
        // SPIELERSHOP geprueft, und dort ist der Spielername richtig. Die zwei Faelle
        // stehen darum hier beieinander - der Unterschied zwischen ihnen IST die Regel.
        Kistenshop bundstand = new Kistenshop("freimarken:altfurt", 60, 71, 6, 61, 71, 6,
                null, 5, 0L, 1L, new Warenprobe("Stein", "inhalt"), Set.of());
        Kistenshop annas = new Kistenshop("freimarken:altfurt", 60, 71, 6, 61, 71, 6,
                UUID.randomUUID(), 16, 100L, 80L, new Warenprobe("Stein", "inhalt"),
                Set.of());

        assertEquals("Bund", Shopschild.schreibe("Anna", "Bund", bundstand)[0],
                "ein Shop OHNE Besitzer traegt das Wort aus der config.yml");
        assertEquals("Anna", Shopschild.schreibe("Anna", "Bund", annas)[0],
                "und jeder andere den Namen dessen, der ihn angelegt hat");
        // Und das geschriebene Schild ist wieder lesbar - als BUNDSHOP.
        Shopschild.Ergebnis zurueck = Shopschild.lies(
                Shopschild.schreibe("Anna", "Bund", bundstand), "Bund");
        assertTrue(zurueck.gelungen(), String.valueOf(zurueck.fehler()));
        assertTrue(zurueck.schild().bundshop(),
                "sonst entstuende aus dem eigenen Schild beim naechsten Lesen ein"
                        + " Spielerladen");
    }

    @Test
    @DisplayName("Ohne BUNDWORT gibt es fuer einen Bundshop keine Schildzeilen")
    void bundshopBrauchtDasWort() {
        // Die Gegenrichtung: Ein leeres Bundwort ist eine zulaessige Einstellung (dann
        // sind Bundshops ueber ein Schild nicht anzulegen), aber FUER EINEN BUNDSHOP ist
        // es keine - er haette eine leere erste Zeile, und die liest sich beim naechsten
        // Lesen als "der Ersteller". Erreichbar ist der Fall nur ueber einen
        // Programmfehler, und darum ist ein Wurf richtig.
        Kistenshop bundstand = new Kistenshop("freimarken:altfurt", 60, 71, 6, 61, 71, 6,
                null, 5, 0L, 1L, new Warenprobe("Stein", "inhalt"), Set.of());

        var fehler = assertThrows(IllegalArgumentException.class,
                () -> Shopschild.schreibe("Anna", "  ", bundstand));

        assertTrue(fehler.getMessage().contains("Bundwort"),
                "die Meldung sagt, WELCHER der zwei Namen fehlt: "
                        + fehler.getMessage());
        // Und ein Spielershop kommt ohne Bundwort aus - ein Server ohne Bundshops
        // braucht keines.
        Kistenshop annas = new Kistenshop("freimarken:altfurt", 60, 71, 6, 61, 71, 6,
                UUID.randomUUID(), 16, 100L, 80L, new Warenprobe("Stein", "inhalt"),
                Set.of());
        assertEquals("Anna", Shopschild.schreibe("Anna", "", annas)[0]);
    }

    @Test
    @DisplayName("Die Preiszeile laesst eine 0 weg, statt sie hinzuschreiben")
    void preistextLaesstNullWeg()
    {
        // "B 100 : S 0" waere eine Zeile, die einen Ankauf zu 0 Mark anzubieten scheint.
        assertEquals("B 100 : S 80", Shopschild.preistext(100L, 80L));
        assertEquals("B 100", Shopschild.preistext(100L, 0L));
        assertEquals("S 80", Shopschild.preistext(0L, 80L));
    }

    @Test
    @DisplayName("Ohne Namen und ohne Shop gibt es keine Schildzeilen")
    void schreibenBrauchtBeides() {
        Kistenshop shop = new Kistenshop("freimarken:altfurt", 60, 71, 6, 61, 71, 6,
                UUID.randomUUID(), 16, 100L, 80L, new Warenprobe("Stein", "inhalt"), Set.of());

        assertThrows(IllegalArgumentException.class,
                () -> Shopschild.schreibe(null, "Bund", shop));
        assertThrows(IllegalArgumentException.class,
                () -> Shopschild.schreibe("  ", "Bund", shop));
        assertThrows(IllegalArgumentException.class,
                () -> Shopschild.schreibe("Anna", "Bund", null));
    }

    @Test
    @DisplayName("Ein getippter Materialname wird gross und mit Unterstrichen")
    void materialnameWirdNormiert() {
        // Ein Spieler tippt "oak log" oder "Oak_Log"; das Spiel kennt OAK_LOG.
        assertEquals("OAK_LOG", Shopschild.alsMaterialname("oak log"));
        assertEquals("OAK_LOG", Shopschild.alsMaterialname(" Oak_Log "));
        assertEquals("STONE", Shopschild.alsMaterialname("stone"));
        assertEquals("", Shopschild.alsMaterialname(null));
    }
    // --- Die Vorfrage: ist ueberhaupt ein Shop gemeint? -----------------------

    @Test
    @DisplayName("Ein gewoehnliches Schild ist KEIN Shopversuch")
    void gewoehnlichesSchildIstKeinShopversuch() {
        // DIE FRAGE, OHNE DIE JEDES HAUSNUMMERNSCHILD EINE ABSAGE BEKAEME. lies
        // antwortet auf ein gewoehnliches Schild mit MENGE_KEINE_ZAHL - es hat ja keine
        // Menge -, und ein Spieler, der einen Wegweiser aufstellt, laese "Zeile 2 ist
        // keine Zahl". Darum wird VORHER gefragt, ob er ueberhaupt einen Shop gemeint
        // hat.
        assertFalse(Shopschild.istGemeint(new String[] {
            "Zum Hafen", "bitte", "hier entlang", "->"}));
        assertFalse(Shopschild.istGemeint(new String[] {"", "", "", ""}));
        assertFalse(Shopschild.istGemeint(new String[] {
            "Anna", "16", "Werkzeug", "Vorrat"}));
    }

    @Test
    @DisplayName("Ein Schild an einer Kiste ist nicht schon deshalb ein Shop")
    void vorratsschildIstKeinShop() {
        // Die eigentliche Entscheidung dieser Methode: Die PREISZEILE entscheidet, nicht
        // die Kiste daneben. Ein beschriftetes Schild an einer Vorratskiste ist das
        // Normalste der Welt - wer danach ginge, machte aus jedem davon einen
        // Shopversuch mit einer Absage.
        assertFalse(Shopschild.istGemeint(new String[] {
            "Lager", "Nord", "Eisen und Kohle", "nicht anfassen"}));
    }

    @Test
    @DisplayName("Ein B oder S mit etwas dahinter ist ein Shopversuch")
    void preiszeileIstEinShopversuch() {
        assertTrue(Shopschild.istGemeint(new String[] {"", "16", "B 100", "STONE"}));
        assertTrue(Shopschild.istGemeint(new String[] {"", "16", "S 80", "STONE"}));
        assertTrue(Shopschild.istGemeint(new String[] {
            "", "16", "B 100 : S 80", "STONE"}));
        // Kleinschreibung auch: Ein Spieler, der "b 100" tippt, hat einen Shop gemeint.
        assertTrue(Shopschild.istGemeint(new String[] {"", "16", "b 100", "STONE"}));
    }

    @Test
    @DisplayName("Ein VERTIPPTER Preis ist auch ein Shopversuch - und bekommt die Absage")
    void vertippterPreisIstEinShopversuch() {
        // DER SINN DER TRENNUNG, und die Zusage, die am meisten wert ist: Wer sich
        // vertippt hat, soll seine Absage mit der Hilfe lesen und nicht ein stumm
        // stehengelassenes Schild bekommen. Waere die ZAHL Teil dieser Frage,
        // verschwaende genau der Fall, fuer den es die acht Fehlermeldungen gibt.
        //
        // Beides zusammen ist die Zusicherung: gemeint JA, gelesen NEIN.
        for (String preiszeile : new String[] {"B abc", "B 1.50", "B 100k", "S -5",
                "B 0 : S 0", "B 80 : S 100"}) {
            String[] zeilen = {"", "16", preiszeile, "STONE"};
            assertTrue(Shopschild.istGemeint(zeilen),
                    preiszeile + " ist als Shop gemeint");
            assertFalse(Shopschild.lies(zeilen, "Bund").gelungen(),
                    preiszeile + " darf aber nicht durchgehen");
        }
    }

    @Test
    @DisplayName("Ein einzelnes B ist kein Shopversuch - es steht auf genug Schildern")
    void einzelnerBuchstabeIstKeinShopversuch() {
        // Zwei Zeichen heisst: Buchstabe UND etwas dahinter. Ein "B" allein ist ein
        // Buchstabe auf einem Schild, und davon gibt es viele - Hausnummern, Gleise,
        // Lagerreihen.
        assertFalse(Shopschild.istGemeint(new String[] {"", "16", "B", "STONE"}));
        assertFalse(Shopschild.istGemeint(new String[] {"", "16", " S ", "STONE"}));
    }

    @Test
    @DisplayName("Ohne vier Zeilen ist nichts gemeint - und das wirft nicht")
    void ohneVierZeilenNichtsGemeint() {
        assertFalse(Shopschild.istGemeint(null));
        assertFalse(Shopschild.istGemeint(new String[] {"", "16", "B 100"}));
    }

    @Test
    @DisplayName("Die zwei Preisbuchstaben stehen an EINER Stelle")
    void preisbuchstabenStehenAnEinerStelle() {
        // Gemessen am 08.11.2026: Dieselben zwei Zeichen standen VIERMAL als Literal da -
        // zweimal in liesPreise, zweimal in preistext. Mit istGemeint waere es ein
        // fuenftes Mal geworden, und dann entscheidet eine Stelle, ob ein Schild gemeint
        // ist, waehrend eine andere es liest.
        //
        // Diese Probe haelt die drei Stellen ZUSAMMEN: Sie schreibt mit KAUF und VERKAUF,
        // liest das Ergebnis und fragt, ob es gemeint ist. Wer einen der Buchstaben
        // aendert, aendert alle drei - oder diese Probe ist rot.
        String geschrieben = Shopschild.preistext(100L, 80L);
        assertEquals(Shopschild.KAUF, geschrieben.charAt(0));
        assertTrue(geschrieben.indexOf(Shopschild.VERKAUF) > 0);

        String[] zeilen = {"", "16", geschrieben, "STONE"};
        assertTrue(Shopschild.istGemeint(zeilen));
        assertTrue(Shopschild.lies(zeilen, "Bund").gelungen());
        assertEquals(100L, Shopschild.lies(zeilen, "Bund").schild().kaufpreis());
        assertEquals(80L, Shopschild.lies(zeilen, "Bund").schild().verkaufpreis());
    }

}
