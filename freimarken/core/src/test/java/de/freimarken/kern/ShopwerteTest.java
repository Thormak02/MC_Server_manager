package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die zwei Gebuehrensaetze des Kistenhandels. */
@DisplayName("Die Handelsgebuehren")
class ShopwerteTest {

    private static final String WELT = "freimarken:altfurt";

    private static Kistenshop shop(UUID besitzer) {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, besitzer, 16, 100L, 80L,
                new Warenprobe("Stein", "inhalt"));
    }

    @Test
    @DisplayName("Ab Werk sind BEIDE Saetze 0")
    void vorgabeIstNull() {
        // Entschieden am 08.11.2026: Der Spielermarkt soll zuerst laufen. Die Zahlen
        // stehen trotzdem in der Datei - eine, die erst eingebaut wird, wenn man sie
        // braucht, wird unter Druck eingebaut.
        assertEquals(0, Shopwerte.VORGABE.spielerProzent());
        assertEquals(0, Shopwerte.VORGABE.bundProzent());
        assertFalse(Shopwerte.VORGABE.erhebtGebuehr());
    }

    @Test
    @DisplayName("Die Pfade heissen wie in der config.yml")
    void pfade() {
        assertEquals("handel.marktgebuehr-spieler-prozent", Shopwerte.PFAD_SPIELER);
        assertEquals("handel.marktgebuehr-bund-prozent", Shopwerte.PFAD_BUND);
    }

    @Test
    @DisplayName("prozentFuer waehlt den Satz nach dem BESITZER - die eine Stelle")
    void prozentFuerWaehltDenSatz() {
        // Zwei Stellen waeren zwei Gelegenheiten, die Saetze zu vertauschen - und das
        // faellt niemandem auf, solange beide 0 sind. Darum stehen hier VERSCHIEDENE
        // Zahlen: Ein Tausch ist sofort zu sehen.
        Shopwerte werte = new Shopwerte(7, 13, "Bund");

        assertEquals(7, werte.prozentFuer(shop(UUID.randomUUID())),
                "ein Spielershop zahlt den Spielersatz");
        assertEquals(13, werte.prozentFuer(shop(null)),
                "ein Bundshop zahlt den Bundsatz");
        assertThrows(IllegalArgumentException.class, () -> werte.prozentFuer(null));
    }

    @Test
    @DisplayName("Jeder Wither aendert GENAU seinen Wert")
    void witherAendernNurEinen() {
        // Vier Zusicherungen und nicht zwei: Bei einem Wither, der die falsche Stelle
        // belegt, waere eine Probe, die nur den geaenderten Wert liest, gruen.
        Shopwerte spieler = Shopwerte.VORGABE.mitSpieler(7);
        assertEquals(7, spieler.spielerProzent());
        assertEquals(0, spieler.bundProzent());

        Shopwerte bund = Shopwerte.VORGABE.mitBund(13);
        assertEquals(0, bund.spielerProzent());
        assertEquals(13, bund.bundProzent());
    }

    @Test
    @DisplayName("erhebtGebuehr sieht BEIDE Saetze")
    void erhebtGebuehrSiehtBeide() {
        // Eine Fassung, die nur den Spielersatz prueft, liesse die Startzeile schweigen,
        // waehrend am Bundesmarkt eine Gebuehr erhoben wird.
        assertTrue(Shopwerte.VORGABE.mitSpieler(1).erhebtGebuehr());
        assertTrue(Shopwerte.VORGABE.mitBund(1).erhebtGebuehr());
        assertFalse(Shopwerte.VORGABE.erhebtGebuehr());
    }

    @Test
    @DisplayName("Eine Gebuehr ueber der Haelfte wird abgewiesen")
    void gebuehrHatGrenzen() {
        assertThrows(IllegalArgumentException.class, () -> new Shopwerte(51, 0, "Bund"));
        assertThrows(IllegalArgumentException.class, () -> new Shopwerte(0, 51, "Bund"));
        assertThrows(IllegalArgumentException.class, () -> new Shopwerte(-1, 0, "Bund"));
        assertThrows(IllegalArgumentException.class, () -> new Shopwerte(0, -1, "Bund"));
        // Der Rand geht durch.
        assertEquals(50, new Shopwerte(50, 50, "Bund").spielerProzent());
    }

    @Test
    @DisplayName("Die Wirtschaftswerte tragen den Handel als dreizehnten Teil")
    void imWirtschaftssatz() {
        assertEquals(Shopwerte.VORGABE, Wirtschaftswerte.VORGABE.handel());

        Shopwerte andere = new Shopwerte(5, 3, "Bund");
        Wirtschaftswerte mit = Wirtschaftswerte.VORGABE.mitHandel(andere);
        assertEquals(andere, mit.handel());
        // Der Wither fasst GENAU seinen Wert an - geprueft an drei Nachbarn.
        assertEquals(Mietwerte.VORGABE, mit.laeden());
        assertEquals(Bonuswerte.VORGABE, mit.bonus());
        assertEquals(Wirtschaftswerte.VORGABE_STARTGUTHABEN, mit.startguthaben());
    }

    @Test
    @DisplayName("Ohne Handelsgebuehren gibt es keinen Wirtschaftssatz")
    void handelIstPflicht() {
        assertThrows(IllegalArgumentException.class,
                () -> Wirtschaftswerte.VORGABE.mitHandel(null));
    }
    // --- Das Bundwort: der Satz prueft es, nicht nur der Leser ---------------

    @Test
    @DisplayName("Ein zu langes Bundwort wird im SATZ abgewiesen, nicht nur im Leser")
    void zuLangesBundwortWirdImSatzAbgewiesen() {
        // BEFUND DER EINSPRITZUNG N3 vom 08.11.2026: Die Laengenpruefung im Konstruktor
        // liess sich wegnehmen, ohne dass eine einzige Probe rot wurde. Geprueft hatte
        // das bisher nur Shopliste - und der hat seine EIGENE Pruefung. Der Satz war
        // damit ohne Zeugen, und ein zu langes Wort haette ueber jeden anderen Weg
        // hereingekommen: aus einer Probe, aus einem spaeteren Befehl, aus mitWort.
        //
        // Ein Wort ueber der Schildzeile ist eine Einstellung, die NIE wirkt - der
        // Betreiber wartete ewig auf seinen Bundshop. Genau das ist der Nutzergrundsatz
        // "halb umgezogen muss warnen".
        String zuLang = "x".repeat(Shopwerte.LAENGSTES_WORT + 1);

        assertThrows(IllegalArgumentException.class,
                () -> new Shopwerte(0, 0, zuLang));
        // Und auf dem zweiten Weg hinein, denn mitWort prueft selbst nichts: Es
        // verlaesst sich auf den Konstruktor, und genau darum muss der es tun.
        assertThrows(IllegalArgumentException.class,
                () -> Shopwerte.VORGABE.mitWort(zuLang));
    }

    @Test
    @DisplayName("Genau die Laenge einer Schildzeile geht durch")
    void genauDieSchildzeileGehtDurch() {
        // Die Gegenprobe: Ohne sie waere die Pruefung auch mit einem Abstand von eins
        // gruen - und dann passte das laengste erlaubte Wort nicht mehr.
        String grenze = "x".repeat(Shopwerte.LAENGSTES_WORT);

        assertEquals(grenze, new Shopwerte(0, 0, grenze).bundWort());
    }

    @Test
    @DisplayName("Das Bundwort wird getrimmt, und null wird leer")
    void bundwortWirdNormalisiert() {
        // EINE Stelle normalisiert, und zwar der Konstruktor: null und ein leeres Wort
        // bedeuten dasselbe - Bundshops sind ueber ein Schild nicht anzulegen. Zwei
        // Schreibweisen fuer dieselbe Einstellung sind zwei Vergleiche, die irgendwann
        // auseinandergehen.
        assertEquals("", new Shopwerte(0, 0, null).bundWort());
        assertEquals("", new Shopwerte(0, 0, "   ").bundWort());
        assertEquals("Bund", new Shopwerte(0, 0, "  Bund  ").bundWort());
    }

}
