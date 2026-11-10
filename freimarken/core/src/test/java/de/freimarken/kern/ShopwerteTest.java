package de.freimarken.kern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die drei Gebuehrensaetze des Kistenhandels - einer je Lage. */
@DisplayName("Die Handelsgebuehren")
class ShopwerteTest {

    private static final String WELT = "freimarken:altfurt";

    private static Kistenshop shop(UUID besitzer) {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, besitzer, 16, 100L, 80L,
                new Warenprobe("Stein", "inhalt"), Set.of());
    }

    @Test
    @DisplayName("Ab Werk sind ALLE DREI Saetze 0")
    void vorgabeIstNull() {
        // Entschieden am 08.11.2026: Der Spielermarkt soll zuerst laufen. Die Zahlen
        // stehen trotzdem in der Datei - eine, die erst eingebaut wird, wenn man sie
        // braucht, wird unter Druck eingebaut.
        assertEquals(0, Shopwerte.VORGABE.spielershopKauf());
        assertEquals(0, Shopwerte.VORGABE.spielershopVerkauf());
        assertEquals(0, Shopwerte.VORGABE.bundshopVerkauf());
        assertFalse(Shopwerte.VORGABE.erhebtGebuehr());
    }

    @Test
    @DisplayName("Die Pfade heissen wie in der config.yml")
    void pfade() {
        assertEquals("handel.marktgebuehr-spielershop-kauf-prozent",
                Shopwerte.PFAD_SPIELER_KAUF);
        assertEquals("handel.marktgebuehr-spielershop-verkauf-prozent",
                Shopwerte.PFAD_SPIELER_VERKAUF);
        assertEquals("handel.marktgebuehr-bundshop-verkauf-prozent",
                Shopwerte.PFAD_BUND_VERKAUF);
    }

    @Test
    @DisplayName("prozentFuer waehlt nach Shopart UND Richtung - alle vier Lagen")
    void prozentFuerKenntAlleVierLagen() {
        // DREI VERSCHIEDENE ZAHLEN, und das ist die Sache selbst: Eine Probe mit
        // gleichen Saetzen waere auch gruen, wenn zwei Lagen vertauscht sind - und das
        // faellt niemandem auf, solange alle 0 sind. So ist ein Tausch sofort zu sehen.
        //
        // Die Richtung ist seit dem 08.11.2026 eine EINSTELLUNG und keine Regel des
        // Programms: Auch WANN eine Gebuehr faellt, soll der Betreiber entscheiden.
        Shopwerte werte = new Shopwerte(7, 13, 23, "Bund", 7);
        Kistenshop spielershop = shop(UUID.randomUUID());
        Kistenshop bundshop = shop(null);

        assertEquals(7, werte.prozentFuer(spielershop, Handelsart.KUNDE_KAUFT),
                "kauft ein Kunde beim Spielershop, zahlt der BESITZER");
        assertEquals(13, werte.prozentFuer(spielershop, Handelsart.KUNDE_VERKAUFT),
                "verkauft ein Kunde an einen Spielershop, zahlt ER");
        assertEquals(23, werte.prozentFuer(bundshop, Handelsart.KUNDE_VERKAUFT),
                "verkauft ein Kunde an einen Bundshop, zahlt er den Bundsatz");
        // DIE VIERTE LAGE HAT KEINEN SATZ, und das ist die Zusage: Am Bundshop bekommt
        // beim Kauf niemand Geld, dem eine Gebuehr abzuziehen waere. Eine Zahl dafuer
        // waere eine Einstellung, die nie wirkt.
        assertEquals(0, werte.prozentFuer(bundshop, Handelsart.KUNDE_KAUFT),
                "am Bundshop kauft man ohne Gebuehr - es gibt keinen Zahler");
    }

    @Test
    @DisplayName("Ohne Shop oder ohne Richtung gibt es keine Gebuehr")
    void prozentFuerBrauchtBeides() {
        Shopwerte werte = new Shopwerte(7, 13, 23, "Bund", 7);

        assertThrows(IllegalArgumentException.class,
                () -> werte.prozentFuer(null, Handelsart.KUNDE_KAUFT));
        assertThrows(IllegalArgumentException.class,
                () -> werte.prozentFuer(shop(null), null));
    }

    @Test
    @DisplayName("Jeder Wither aendert GENAU seinen Wert")
    void witherAendernNurEinen() {
        // Drei Zusicherungen je Wither und nicht eine: Bei einem Wither, der die falsche
        // Stelle belegt, waere eine Probe, die nur den geaenderten Wert liest, gruen.
        Shopwerte kauf = Shopwerte.VORGABE.mitSpielerKauf(7);
        assertEquals(7, kauf.spielershopKauf());
        assertEquals(0, kauf.spielershopVerkauf());
        assertEquals(0, kauf.bundshopVerkauf());

        Shopwerte verkauf = Shopwerte.VORGABE.mitSpielerVerkauf(13);
        assertEquals(0, verkauf.spielershopKauf());
        assertEquals(13, verkauf.spielershopVerkauf());
        assertEquals(0, verkauf.bundshopVerkauf());

        Shopwerte bund = Shopwerte.VORGABE.mitBundVerkauf(23);
        assertEquals(0, bund.spielershopKauf());
        assertEquals(0, bund.spielershopVerkauf());
        assertEquals(23, bund.bundshopVerkauf());
    }

    @Test
    @DisplayName("erhebtGebuehr sieht ALLE DREI Saetze")
    void erhebtGebuehrSiehtAlle() {
        // Eine Fassung, die nur einen Satz prueft, liesse die Startzeile schweigen,
        // waehrend an einer der anderen Lagen eine Gebuehr erhoben wird.
        assertTrue(Shopwerte.VORGABE.mitSpielerKauf(1).erhebtGebuehr());
        assertTrue(Shopwerte.VORGABE.mitSpielerVerkauf(1).erhebtGebuehr());
        assertTrue(Shopwerte.VORGABE.mitBundVerkauf(1).erhebtGebuehr());
        assertFalse(Shopwerte.VORGABE.erhebtGebuehr());
    }

    @Test
    @DisplayName("Eine Gebuehr ueber der Haelfte wird abgewiesen - an jeder der drei")
    void gebuehrHatGrenzen() {
        assertThrows(IllegalArgumentException.class,
                () -> new Shopwerte(51, 0, 0, "Bund", 7));
        assertThrows(IllegalArgumentException.class,
                () -> new Shopwerte(0, 51, 0, "Bund", 7));
        assertThrows(IllegalArgumentException.class,
                () -> new Shopwerte(0, 0, 51, "Bund", 7));
        assertThrows(IllegalArgumentException.class,
                () -> new Shopwerte(-1, 0, 0, "Bund", 7));
        assertThrows(IllegalArgumentException.class,
                () -> new Shopwerte(0, -1, 0, "Bund", 7));
        assertThrows(IllegalArgumentException.class,
                () -> new Shopwerte(0, 0, -1, "Bund", 7));
        // Der Rand geht durch.
        assertEquals(50, new Shopwerte(50, 50, 50, "Bund", 7).spielershopKauf());
    }

    @Test
    @DisplayName("Jede Klage nennt, WELCHE der drei Lagen gemeint ist")
    void klageNenntDieLage() {
        // Drei Saetze mit derselben Schranke und derselben Klage - dann muss die Klage
        // sagen, welcher gemeint ist. Sonst sucht ein Betreiber mit einer 60 in der
        // Datei an drei Stellen.
        String kauf = assertThrows(IllegalArgumentException.class,
                () -> new Shopwerte(60, 0, 0, "Bund", 7)).getMessage();
        String verkauf = assertThrows(IllegalArgumentException.class,
                () -> new Shopwerte(0, 60, 0, "Bund", 7)).getMessage();
        String bund = assertThrows(IllegalArgumentException.class,
                () -> new Shopwerte(0, 0, 60, "Bund", 7)).getMessage();

        assertTrue(kauf.contains("Kauf"), kauf);
        assertTrue(verkauf.contains("Verkauf"), verkauf);
        assertTrue(bund.contains("Bundshop"), bund);
        // Und sie sind VERSCHIEDEN. Ohne diese Zeile waere eine Fassung gruen, in der
        // dreimal derselbe Satz steht und das Wort nur zufaellig passt.
        assertNotEqualsDrei(kauf, verkauf, bund);
    }

    private static void assertNotEqualsDrei(String a, String b, String c) {
        assertTrue(!a.equals(b) && !b.equals(c) && !a.equals(c),
                "die drei Klagen muessen verschieden sein: " + a + " / " + b + " / " + c);
    }

    @Test
    @DisplayName("Die Wirtschaftswerte tragen den Handel als dreizehnten Teil")
    void imWirtschaftssatz() {
        assertEquals(Shopwerte.VORGABE, Wirtschaftswerte.VORGABE.handel());

        Shopwerte andere = new Shopwerte(5, 3, 2, "Bund", 7);
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
    @DisplayName("Eine unbrauchbare Seitengroesse wird im SATZ abgewiesen")
    void unbrauchbareSeitengroesseWirdImSatzAbgewiesen() {
        // Dieselbe Lage wie beim Bundwort (siehe die Probe darunter): Der Leser
        // Shopliste hat seine EIGENE Pruefung und klagt statt zu werfen, weil er beim
        // Start laeuft. Haette nur er eine, kaeme eine 0 ueber jeden anderen Weg herein -
        // aus einer Probe, aus einem spaeteren Befehl, aus einer der mitX-Methoden.
        //
        // Eine 0 ist dabei das Schlimmste, was durchkommen koennte: Die Liste zeigte nie
        // etwas, und fuer den Spieler ist das von einem kaputten Server nicht zu
        // unterscheiden.
        assertThrows(IllegalArgumentException.class,
                () -> new Shopwerte(0, 0, 0, "Bund", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Shopwerte(0, 0, 0, "Bund", -1));
        assertThrows(IllegalArgumentException.class,
                () -> new Shopwerte(0, 0, 0, "Bund", Shopwerte.HOECHSTE_ZEILEN + 1));
        // Die Grenze SELBST geht durch, und das ist die andere Haelfte der Zusage: Wer
        // sie um eins verschiebt, faellt nur an dieser Zeile auf.
        assertEquals(Shopwerte.HOECHSTE_ZEILEN,
                new Shopwerte(0, 0, 0, "Bund", Shopwerte.HOECHSTE_ZEILEN)
                        .zeilenJeSeite());
        assertEquals(1, new Shopwerte(0, 0, 0, "Bund", 1).zeilenJeSeite(),
                "und eine einzige Zeile je Seite ist eine brauchbare Einstellung");
    }

    @Test
    @DisplayName("Jede mitX-Methode gibt die Seitengroesse DURCH")
    void mitMethodenGebenDieSeitengroesseDurch() {
        // Vier Methoden aendern je eine Komponente und geben die anderen durch. Eine,
        // die dabei auf die Vorgabe zurueckfaellt, ist nicht zu sehen: Die Vorgabe ist
        // 10, und zehn Zeilen sehen richtig aus. Darum eine Zahl, die NICHT die Vorgabe
        // ist - und alle vier Wege einzeln.
        Shopwerte drei = new Shopwerte(1, 2, 3, "Staat", 3);

        assertEquals(3, drei.mitWort("Reich").zeilenJeSeite(), "mitWort");
        assertEquals(3, drei.mitSpielerKauf(9).zeilenJeSeite(),
                "mitSpielerKauf");
        assertEquals(3, drei.mitSpielerVerkauf(9).zeilenJeSeite(),
                "mitSpielerVerkauf");
        assertEquals(3, drei.mitBundVerkauf(9).zeilenJeSeite(),
                "mitBundVerkauf");
    }

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
                () -> new Shopwerte(0, 0, 0, zuLang, 7));
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

        assertEquals(grenze, new Shopwerte(0, 0, 0, grenze, 7).bundWort());
    }

    @Test
    @DisplayName("Das Bundwort wird getrimmt, und null wird leer")
    void bundwortWirdNormalisiert() {
        // EINE Stelle normalisiert, und zwar der Konstruktor: null und ein leeres Wort
        // bedeuten dasselbe - Bundshops sind ueber ein Schild nicht anzulegen. Zwei
        // Schreibweisen fuer dieselbe Einstellung sind zwei Vergleiche, die irgendwann
        // auseinandergehen.
        assertEquals("", new Shopwerte(0, 0, 0, null, 7).bundWort());
        assertEquals("", new Shopwerte(0, 0, 0, "   ", 7).bundWort());
        assertEquals("Bund", new Shopwerte(0, 0, 0, "  Bund  ", 7).bundWort());
    }
}
