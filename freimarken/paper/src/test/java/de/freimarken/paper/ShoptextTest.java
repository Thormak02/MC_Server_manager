package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Handelsart;
import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopregel;
import de.freimarken.kern.Shopschild;
import de.freimarken.kern.Warenprobe;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Zeilen der Kistenshops - durch die ECHTE ausgelieferte Vorlage.
 *
 * <p><b>Das ist die Nahtprobe, und {@code BildGleichTest} ist sie nicht.</b> Jene Tafel
 * ruft die Saetze selbst auf, mit Werten, die sie sich selbst gibt - sie haelt also das
 * BILD eines Satzes fest und nichts darueber, ob der richtige Satz gewaehlt und der
 * richtige Wert eingesetzt wird. Genau das steht hier: Welcher Bescheid welchen Satz
 * bekommt, in welcher Reihenfolge die Zeilen kommen und welche Zahl in welcher Luecke
 * landet.
 *
 * <p>Dieselbe Linie wie {@code MiettextTest}. Der Unterschied zu dort ist die ZEILENZAHL:
 * Eine gelungene Anlage ergibt zwei bis vier Zeilen, und welche, haengt am Shop.
 */
@DisplayName("Die Zeilen der Kistenshops")
class ShoptextTest {

    private static final String WELT = "freimarken:altfurt";
    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000a111");
    private static final Warenprobe STEIN = new Warenprobe("STONE", "c3RlaW4=");

    private Sprache sprache;

    @BeforeEach
    void laden() {
        sprache = Sprachprobe.spracheAusDemJar();
    }

    // --- Die gelungene Anlage: zwei bis vier Zeilen ---------------------------

    @Test
    @DisplayName("Ein Shop mit beiden Preisen ergibt DREI Zeilen - Kauf vor Verkauf")
    void beidePreiseDreiZeilen() {
        List<Component> zeilen = anlageZeilen(shop(ANNA, 16, 100L, 80L));

        assertEquals(3, zeilen.size());
        assertEquals("&aShop angelegt: 16x STONE", bild(zeilen.get(0)));
        // DIE REIHENFOLGE ist die Zusage: Kauf vor Verkauf, weil das Schild sie so nennt
        // (B vor S). Vertauscht liest ein Spieler den falschen Preis als den, den er
        // bekommt - und die Bildtafel saehe das nicht, denn beide Saetze sind fuer sich
        // richtig.
        assertEquals("&7Kunden kaufen fuer &f100 Mark&7.", bild(zeilen.get(1)));
        assertEquals("&7Kunden verkaufen fuer &f80 Mark&7.", bild(zeilen.get(2)));
    }

    @Test
    @DisplayName("Ein Shop, der nur verkauft, hat KEINE Kaufzeile")
    void nurVerkaufZweiZeilen() {
        // Die Null darf nirgends als gueltiger Preis zu lesen sein - sie ist genau das,
        // was Shopschild als PREIS_BEIDE_NULL abweist.
        List<Component> zeilen = anlageZeilen(shop(ANNA, 64, 0L, 5L));

        assertEquals(2, zeilen.size());
        assertEquals("&7Kunden verkaufen fuer &f5 Mark&7.", bild(zeilen.get(1)));
        assertTrue(zeilen.stream().noneMatch(z -> bild(z).contains("kaufen fuer &f0")),
                "eine 0 als Preis steht in keiner Zeile");
    }

    @Test
    @DisplayName("Ein Shop, der nur kauft, hat KEINE Verkaufszeile")
    void nurKaufZweiZeilen() {
        List<Component> zeilen = anlageZeilen(shop(ANNA, 1, 5_000L, 0L));

        assertEquals(2, zeilen.size());
        assertEquals("&7Kunden kaufen fuer &f5.000 Mark&7.", bild(zeilen.get(1)));
    }

    @Test
    @DisplayName("Die Bundzeile kommt ZULETZT und ersetzt keine andere")
    void bundzeileKommtZuletzt() {
        // Ein Bundshop ist ein gewoehnlicher Shop mit einer Eigenschaft mehr, und der
        // Leser soll zuerst dasselbe lesen wie bei jedem anderen. Stuende die Bundzeile
        // VORNE oder ANSTELLE der ersten, waere die Menge nicht zu sehen.
        List<Component> zeilen = anlageZeilen(shop(null, 16, 100L, 80L));

        assertEquals(4, zeilen.size());
        assertEquals("&aShop angelegt: 16x STONE", bild(zeilen.get(0)));
        assertEquals("&6Bundshop: unbegrenzter Vorrat, unbegrenzter Platz,"
                + " unbegrenztes Geld.", bild(zeilen.get(3)));
    }

    // --- Die Absagen: welcher Satz, welcher Wert ------------------------------

    @Test
    @DisplayName("Jeder Schildfehler bekommt SEINEN Satz, mit SEINER Zeilennummer")
    void jederSchildfehlerSeinSatz() {
        // Die Zuordnung, die kein Bild festhaelt: Acht Fehler, acht verschiedene Hilfen.
        // Eine vertauschte Zuordnung ist zweimal ein richtiger Satz an der falschen
        // Stelle - und ein Spieler, der bei einem Preisfehler "schreib eine Zahl von 1
        // bis 3456" liest, aendert die falsche Zeile.
        assertEquals("&cZeile 2 ist keine Zahl.",
                ersteZeile(Shopschild.Fehler.MENGE_KEINE_ZAHL, 2));
        assertEquals("&cZeile 2: die Menge geht von 1 bis 3.456.",
                ersteZeile(Shopschild.Fehler.MENGE_AUSSERHALB, 2));
        assertEquals("&cZeile 3 ist keine Preiszeile.",
                ersteZeile(Shopschild.Fehler.PREIS_FORM, 3));
        assertEquals("&cZeile 3: der Preis ist keine ganze Zahl.",
                ersteZeile(Shopschild.Fehler.PREIS_KEINE_ZAHL, 3));
        assertEquals("&cZeile 3: der Preis geht von 0 bis 10.000.000.",
                ersteZeile(Shopschild.Fehler.PREIS_AUSSERHALB, 3));
        assertEquals("&cZeile 3: beide Preise sind 0.",
                ersteZeile(Shopschild.Fehler.PREIS_BEIDE_NULL, 3));
        assertEquals("&cZeile 3: du kaufst teurer an, als du verkaufst.",
                ersteZeile(Shopschild.Fehler.PREIS_VERDREHT, 3));
        assertEquals("&cZeile 4 ist leer.",
                ersteZeile(Shopschild.Fehler.WARE_FEHLT, 4));
    }

    @Test
    @DisplayName("KEIN_SCHILD bekommt die Stoerungsmeldung und keinen eigenen Satz")
    void keinSchildIstEineStoerung() {
        // Dieser Fehler heisst "es wurden weniger als vier Zeilen uebergeben", und am
        // SignChangeEvent sind immer vier da. Er ist also kein Fall des Spielers, sondern
        // ein Fehler des Programms - und ein eigener Wortlaut dafuer waere ein Satz, den
        // nie jemand liest und den der Betreiber trotzdem uebersetzen muesste.
        assertEquals("&cDer Shop liess sich nicht anlegen.",
                ersteZeile(Shopschild.Fehler.KEIN_SCHILD, 0));
    }

    @Test
    @DisplayName("Die ZWEITE Zeile jeder Schildabsage ist die Hilfe - sie fehlt nie")
    void jedeSchildabsageHatIhreHilfe() {
        // Die zweite Zeile ist der Grund, warum es acht Saetze gibt. Faellt sie weg,
        // bleibt ein Spieler am Schild stehen und weiss nicht, was er tippen soll -
        // genau der Zustand, in dem die Vorbilder ihre Nutzer lassen.
        for (Shopschild.Fehler fehler : Shopschild.Fehler.values()) {
            List<Component> zeilen = Shoptext.anlageZeilen(sprache,
                    absage(fehler, 3), "STEIN");
            assertEquals(2, zeilen.size(), fehler + " muss zwei Zeilen haben:"
                    + " die Absage und die Hilfe");
            assertTrue(bild(zeilen.get(1)).startsWith("&7"),
                    fehler + ": die Hilfe ist grau, die Absage rot");
        }
    }

    @Test
    @DisplayName("WARE_UNBEKANNT gibt dem Spieler SEINE EIGENE Eingabe zurueck")
    void wareUnbekanntZeigtDieEingabe() {
        // Oft ist es ein Leerzeichen zu viel, und das sieht nur, wer liest, was das
        // Programm gelesen hat. Der Wert kommt darum UNVERAENDERT aus der vierten
        // Schildzeile und nicht aus einer Liste moeglicher Namen.
        List<Component> zeilen = Shoptext.anlageZeilen(sprache,
                bescheid(Shopbuch.Bescheid.WARE_UNBEKANNT), "STEIN ");

        assertEquals("&cDen Gegenstand &fSTEIN &c gibt es nicht.", bild(zeilen.get(0)));
    }

    @Test
    @DisplayName("Jeder Weltbescheid bekommt seinen eigenen Satz")
    void jederWeltbescheidSeinenSatz() {
        assertEquals("&cNeben dem Schild steht keine Kiste.",
                ersteZeileVon(Shopbuch.Bescheid.KEINE_KISTE));
        assertEquals("&cAn dieser Kiste darfst du nicht.",
                ersteZeileVon(Shopbuch.Bescheid.FREMDE_KISTE));
        assertEquals("&cHier haengt schon ein Shop.",
                ersteZeileVon(Shopbuch.Bescheid.SCHON_EIN_SHOP));
        assertEquals("&cDie Kiste sagt nicht, was du handeln willst.",
                ersteZeileVon(Shopbuch.Bescheid.WARE_UNKLAR));
        assertEquals("&cBundshops darfst du nicht anlegen.",
                ersteZeileVon(Shopbuch.Bescheid.KEIN_BUNDRECHT));
        assertEquals("&cDer Shop liess sich nicht anlegen.",
                ersteZeileVon(Shopbuch.Bescheid.GESTOERT));
    }

    @Test
    @DisplayName("WARE_UNKLAR und WARE_UNBEKANNT sind VERSCHIEDENE Saetze")
    void dieZweiWarenabsagenSindVerschieden() {
        // Der eine Spieler muss in die Kiste sehen, der andere auf sein Schild. Mit
        // einem gemeinsamen Satz suchte einer von beiden immer am falschen Ort - und
        // weil beide Saetze fuer sich richtig sind, saehe die Bildtafel das nicht.
        assertNotEqualsBild(ersteZeileVon(Shopbuch.Bescheid.WARE_UNKLAR),
                ersteZeileVon(Shopbuch.Bescheid.WARE_UNBEKANNT));
    }

    // --- Das Abbauen ----------------------------------------------------------

    @Test
    @DisplayName("Abgebaut nennt die Ware UND dass sie in der Kiste bleibt")
    void abgebautNenntDieWare() {
        List<Component> zeilen = Shoptext.abbauZeilen(sprache,
                Shopbuch.Bescheid.ABGEBAUT, shop(ANNA, 16, 100L, 80L));

        assertEquals(1, zeilen.size());
        assertEquals("&eShop abgebaut: STONE&7 - die Ware bleibt in der Kiste.",
                bild(zeilen.get(0)));
    }

    @Test
    @DisplayName("KEIN_SHOP sagt GAR NICHTS - die zweite Meldung derselben Stelle")
    void keinShopSchweigt() {
        // Dieselbe Stelle wird von zwei Seiten gemeldet: vom Abbauen des Schildes und vom
        // Abbauen der Kiste. Die zweite soll nicht sagen, dass hier nichts war - und
        // genau darum gibt diese Methode eine LISTE und keine einzelne Zeile.
        assertTrue(Shoptext.abbauZeilen(sprache, Shopbuch.Bescheid.KEIN_SHOP, null)
                .isEmpty());
    }

    @Test
    @DisplayName("Ein Anlagebescheid beim Abbauen wirft - die Taten sind nicht zu tauschen")
    void anlagebescheidBeimAbbauenWirft() {
        assertThrows(IllegalArgumentException.class, () -> Shoptext.abbauZeilen(sprache,
                Shopbuch.Bescheid.ANGELEGT, null));
    }

    @Test
    @DisplayName("ABGEBAUT und KEIN_SHOP werfen in der Gegenrichtung")
    void abbaubescheidBeimAnlegenWirft() {
        assertThrows(IllegalArgumentException.class, () -> Sprachschluessel.shopAnlage(
                Shopbuch.Bescheid.ABGEBAUT, null));
        assertThrows(IllegalArgumentException.class, () -> Sprachschluessel.shopAnlage(
                Shopbuch.Bescheid.KEIN_SHOP, null));
    }

    @Test
    @DisplayName("Ein fehlerhaftes Schild OHNE Fehler wirft")
    void fehlerhaftOhneFehlerWirft() {
        // Shopschild.lies liefert Fehler und Zeile zusammen oder keines von beiden. Ein
        // SCHILD_FEHLERHAFT ohne Fehler ist darum ein Fehler des Programms, und ein Wurf
        // sagt das - eine stille Ersatzmeldung verdeckte ihn.
        assertThrows(IllegalArgumentException.class, () -> Sprachschluessel.shopAnlage(
                Shopbuch.Bescheid.SCHILD_FEHLERHAFT, null));
    }

    // --- Der Schutz: vier Handbalken, zwei Achsen -----------------------------

    @Test
    @DisplayName("Der Handbalken waehlt nach TEIL und nach BESITZER")
    void handbalkenWaehltNachTeilUndBesitzer() {
        Kistenshop meiner = shop(ANNA, 16, 100L, 80L);
        Kistenshop bundes = shop(null, 16, 100L, 80L);

        // Die zwei Achsen: Schild und Kiste tragen den Namen, der Bund hat keinen, und
        // der Traeger nennt niemanden. Vier Saetze aus zwei Fragen - und wer sie
        // vertauscht, nennt an einer Kiste "dieser Shop gehoert" oder an einem Bundshop
        // einen Menschen, den es nicht gibt.
        assertTrue(bild(Shoptext.schutzBalken(sprache, Shoptext.Teil.SCHILD, meiner, "Anna"))
                .startsWith("&cDieser Shop gehoert &f"));
        assertTrue(bild(Shoptext.schutzBalken(sprache, Shoptext.Teil.KISTE, meiner, "Anna"))
                .startsWith("&cDiese Kiste ist der Shop von &f"));
        assertEquals("&cDas ist ein Shop des Bundes",
                bild(Shoptext.schutzBalken(sprache, Shoptext.Teil.SCHILD, bundes, "Anna")));
        assertEquals("&cDas ist ein Shop des Bundes",
                bild(Shoptext.schutzBalken(sprache, Shoptext.Teil.KISTE, bundes, "Anna")));
        assertEquals("&cDaran haengt ein fremdes Shopschild",
                bild(Shoptext.schutzBalken(sprache, Shoptext.Teil.TRAEGER, meiner, "Anna")));
    }

    @Test
    @DisplayName("Der Traegersatz braucht keinen Shop - und wirft darum auch ohne einen nicht")
    void traegerBrauchtKeinenShop() {
        // Gerufen wird er fuer jeden Block, der ein Shopschild haelt; welcher Shop daran
        // haengt, ist fuer diesen Satz ohne Bedeutung. Ein Wurf hier liesse eine Wache
        // beim Schuetzen sterben, und dann braeche der Block.
        assertEquals("&cDaran haengt ein fremdes Shopschild",
                bild(Shoptext.schutzBalken(sprache, Shoptext.Teil.TRAEGER, null, "Anna")));
    }

    // --- Werkzeug -------------------------------------------------------------

    private List<Component> anlageZeilen(Kistenshop shop) {
        return Shoptext.anlageZeilen(sprache,
                new Shopbuch.Anlage(Shopbuch.Bescheid.ANGELEGT, shop, null, 0), "");
    }

    private String ersteZeile(Shopschild.Fehler fehler, int zeile) {
        return bild(Shoptext.anlageZeilen(sprache, absage(fehler, zeile), "STEIN")
                .get(0));
    }

    private String ersteZeileVon(Shopbuch.Bescheid bescheid) {
        return bild(Shoptext.anlageZeilen(sprache, bescheid(bescheid), "STEIN").get(0));
    }

    private static Shopbuch.Anlage absage(Shopschild.Fehler fehler, int zeile) {
        return new Shopbuch.Anlage(Shopbuch.Bescheid.SCHILD_FEHLERHAFT, null, fehler,
                zeile);
    }

    private static Shopbuch.Anlage bescheid(Shopbuch.Bescheid bescheid) {
        return new Shopbuch.Anlage(bescheid, null, null, 0);
    }

    private static Kistenshop shop(UUID besitzer, int menge, long kauf, long verkauf) {
        return new Kistenshop(WELT, 10, 64, 10, 11, 64, 10, besitzer, menge, kauf,
                verkauf, STEIN);
    }

    /**
     * Eine Preisform, wie sie WEISS in der Hilfe steht - samt der Farbe dahinter.
     *
     * <p>Das ist die Einfassung, die aus einem Teilstueck eine Zusage macht: "&fB 100"
     * allein steht auch in "&fB 100 : S 80", "&fB 100&7" nicht.
     */
    private static String eingefasst(String form) {
        return "&f" + form + "&7";
    }

    private static String bild(Component zeile) {
        return Sprachprobe.bild(List.of(zeile));
    }

    private static void assertNotEqualsBild(String a, String b) {
        assertTrue(!a.equals(b), "diese zwei Saetze muessen verschieden sein: " + a);
    }
    // --- Zwei Befunde der Einspritzung vom 08.11.2026 -----------------------

    @Test
    @DisplayName("Die Zuordnung ist auch fuer ANGELEGT richtig - und war ohne Zeugen")
    void zuordnungAuchFuerAngelegt() {
        // BEFUND N5: Der Zweig case ANGELEGT liess sich auf den Stoerungssatz umbiegen,
        // ohne dass eine Probe rot wurde. Der Grund ist der Weg: Shoptext verzweigt bei
        // ANGELEGT selbst und ruft shopAnlage gar nicht.
        //
        // Der Zweig bleibt trotzdem stehen, und das ist eine Entscheidung: shopAnlage IST
        // das Verzeichnis, und ein Verzeichnis soll vollstaendig sein, gleich wer es
        // gerade fragt. Was fehlte, war der Zeuge dafuer.
        assertEquals(ShopSatz.SHOP_ANGELEGT,
                Sprachschluessel.shopAnlage(Shopbuch.Bescheid.ANGELEGT, null));
    }

    @Test
    @DisplayName("Die Hilfe nennt die Preisform, die das Programm WIRKLICH annimmt")
    void hilfeNenntDieEchteForm() {
        // BEFUND N6: Die zwei Preisbuchstaben stehen im Programm an einer Stelle
        // (Shopschild.KAUF und VERKAUF), in der Sprachdatei aber HART - "So geht es:
        // B 100, S 80 oder B 100 : S 80". Eine Einspritzung auf K machte 34 Proben rot,
        // also faellt sie auf; die Datei blieb davon aber unberuehrt, und ein Betreiber,
        // der die Hilfe umschreibt, kann dort eine Form hinterlassen, die nicht geht.
        //
        // Diese Probe bindet beide Seiten: Was in der Hilfe steht, ERZEUGT das Programm -
        // und was es erzeugt, liest es auch. Damit ist die Hilfe in beide Richtungen
        // gedeckt.
        String hilfe = bild(Shoptext.anlageZeilen(sprache,
                absage(Shopschild.Fehler.PREIS_FORM, 3), "").get(1));

        String beide = Shopschild.preistext(100L, 80L);
        // EINGESCHLOSSEN in ihre Farbmarken und nicht bloss "enthalten", und das ist
        // ein Befund der Nachspritzung vom 08.11.2026: Mit einem reinen contains war
        // die Probe GRUEN, obwohl die Hilfe auf "K 100" umgeschrieben war - denn
        // "B 100" steht als Teilstueck auch in "B 100 : S 80". Eine Zusage, die ihre
        // eigene Verletzung nicht sieht, ist keine.
        //
        // Mit dem &7 dahinter geht das nicht mehr: "&fB 100&7" steht in
        // "&fB 100 : S 80&7" nicht.
        assertTrue(hilfe.contains(eingefasst(beide)),
                "die Hilfe muss \"" + beide + "\" nennen - das ist die Form, die"
                        + " preistext schreibt und lies annimmt: " + hilfe);
        assertTrue(hilfe.contains(eingefasst(Shopschild.preistext(100L, 0L))),
                "und die Form fuer nur-kaufen: " + hilfe);
        assertTrue(hilfe.contains(eingefasst(Shopschild.preistext(0L, 80L))),
                "und die fuer nur-verkaufen: " + hilfe);

        // Die Gegenrichtung: Jede dieser drei Formen geht WIRKLICH durch lies. Ohne das
        // koennte die Hilfe eine Form nennen, die preistext schreibt und lies ablehnt -
        // die zwei Richtungen sind getrennte Zusagen.
        for (long[] preise : new long[][] {{100L, 80L}, {100L, 0L}, {0L, 80L}}) {
            String[] zeilen = {"", "16", Shopschild.preistext(preise[0], preise[1]),
                "STONE"};
            assertTrue(Shopschild.lies(zeilen, "Bund").gelungen(),
                    "lies muss \"" + zeilen[2] + "\" annehmen");
        }
    }

    // ================================================== der Handel

    @Test
    @DisplayName("Der gelungene Kauf nennt Menge, Ware und Preis - keine Gebuehr")
    void kaufzeile() {
        // Beim Kauf zahlt die Gebuehr der SHOPBESITZER. Der Kunde zahlt den Preis vom
        // Schild und nichts darueber - eine Zeile, die ihm den Abzug eines anderen
        // nennt, liest sich wie ein Abzug bei ihm.
        var zeilen = Shoptext.handelBalken(sprache, gehandelt(Handelsart.KUNDE_KAUFT,
                16, 100L, 0L));

        assertEquals(1, zeilen.size(), "ein Handbalken ist EINE Zeile");
        assertEquals("&aGekauft: 16x STONE&7 fuer &f100 Mark", bild(zeilen.get(0)));
    }

    @Test
    @DisplayName("Der gelungene Verkauf ohne Gebuehr nennt den ganzen Preis")
    void verkaufzeileOhneGebuehr() {
        var zeilen = Shoptext.handelBalken(sprache,
                gehandelt(Handelsart.KUNDE_VERKAUFT, 16, 80L, 0L));

        assertEquals("&aVerkauft: 16x STONE&7 fuer &f80 Mark", bild(zeilen.get(0)));
    }

    @Test
    @DisplayName("Mit Gebuehr steht die Rechnung in der Zeile - alle drei Zahlen")
    void verkaufzeileMitGebuehr() {
        // EIN EIGENER SATZ und nicht derselbe mit einer 0 darin: "fuer 100 Mark (100
        // minus 0 Gebuehr)" liest sich wie ein Fehler. Und kein stiller Abzug: Wer 100
        // am Schild liest und 93 bekommt, sucht den Fehler beim Shop.
        //
        // Alle drei Zahlen, damit die Zeile NACHRECHENBAR ist - eine Zusage, die eine
        // ihrer Zahlen nicht nennt, traegt keine.
        var zeilen = Shoptext.handelBalken(sprache,
                gehandelt(Handelsart.KUNDE_VERKAUFT, 16, 100L, 7L));

        assertEquals("&aVerkauft: 16x STONE&7 fuer &f93 Mark&7 von 100 Mark, 7 Mark"
                + " Marktgebuehr", bild(zeilen.get(0)));
    }

    @Test
    @DisplayName("Ohne Shop am Schild wird GESCHWIEGEN")
    void keinShopSchweigtBeimHandel() {
        // Ein Spieler, der auf ein Wegweiserschild klickt, bekaeme sonst eine Meldung
        // dafuer, dass dort kein Laden ist. Die Entscheidung steht in der Naht und nicht
        // im Ereigniszweig, damit sie hier zu lesen ist.
        assertEquals(List.of(), Shoptext.handelBalken(sprache,
                new Shophandel.Ausgang(Shophandel.Bescheid.KEIN_SHOP,
                        Handelsart.KUNDE_KAUFT, null, null)));
    }

    @Test
    @DisplayName("Am eigenen Shop redet nur der RECHTSklick")
    void eigenerShopNurRechts() {
        // Beim Linksklick baut der Besitzer sein Schild ab, und zwei Antworten auf eine
        // Tat liest niemand als zwei. Beim Rechtsklick geschieht nichts, da ist die
        // Auskunft das einzige, was er bekommt - und sie sagt, was stattdessen geht.
        var rechts = Shoptext.handelBalken(sprache, absage(
                Shophandel.Bescheid.EIGENER_SHOP, Handelsart.KUNDE_KAUFT));
        var links = Shoptext.handelBalken(sprache, absage(
                Shophandel.Bescheid.EIGENER_SHOP, Handelsart.KUNDE_VERKAUFT));

        assertEquals("&7Das ist dein eigener Shop - nimm die Ware aus der Kiste.",
                bild(rechts.get(0)));
        assertEquals(List.of(), links, "beim Linksklick baut er ab - kein Wort dazu");
    }

    @Test
    @DisplayName("Derselbe Mangel heisst auf den zwei Seiten verschieden")
    void derselbeMangelZweiSaetze() {
        // Die Kiste ist "ausverkauft", der Spieler "hat die Ware nicht" - und wer beides
        // mit einem Satz beantwortet, schickt den Kunden zum Aufraeumen eines Rucksacks,
        // der nicht das Problem ist. Dasselbe bei Platz und Geld.
        assertEquals("&cAusverkauft: STONE", bild(Shoptext.handelBalken(sprache,
                absage(Shophandel.Bescheid.AUSVERKAUFT,
                        Handelsart.KUNDE_KAUFT)).get(0)));
        assertEquals("&cDu brauchst 16x STONE", bild(Shoptext.handelBalken(sprache,
                absage(Shophandel.Bescheid.KEINE_WARE,
                        Handelsart.KUNDE_VERKAUFT)).get(0)));
        assertEquals("&cIn deinem Rucksack ist kein Platz",
                bild(Shoptext.handelBalken(sprache, absage(
                        Shophandel.Bescheid.KEIN_PLATZ,
                        Handelsart.KUNDE_KAUFT)).get(0)));
        assertEquals("&cDie Kiste dieses Shops ist voll",
                bild(Shoptext.handelBalken(sprache, absage(
                        Shophandel.Bescheid.KISTE_VOLL,
                        Handelsart.KUNDE_VERKAUFT)).get(0)));
    }

    @Test
    @DisplayName("Die Geldabsage nennt den Preis EINER Stufe")
    void geldabsageNenntDenStufenpreis() {
        // Was dem Kunden fehlt, ist der Preis fuer EINEN Handel - nicht fuer den Stapel,
        // den er nicht bekommt. Bei einer Absage gibt es keinen gerechneten Handel, also
        // kommt die Zahl aus dem Shop: 100 beim Kauf, 80 beim Verkauf. Zwei
        // verschiedene Zahlen, damit ein Tausch zu sehen ist.
        assertEquals("&cDafuer brauchst du 100 Mark",
                bild(Shoptext.handelBalken(sprache, absage(
                        Shophandel.Bescheid.KEIN_GELD,
                        Handelsart.KUNDE_KAUFT)).get(0)));
        // Und in der anderen Richtung ist es der Verkaufpreis - die Zahl, die der Shop
        // nicht zahlen kann.
        assertEquals("&cDieser Shop kann das nicht zahlen",
                bild(Shoptext.handelBalken(sprache, absage(
                        Shophandel.Bescheid.SHOP_PLEITE,
                        Handelsart.KUNDE_VERKAUFT)).get(0)));
    }

    @Test
    @DisplayName("Die Stoerung sagt, dass kein Geld weg ist")
    void stoerungSagtDassGeldDaIst() {
        // Das ist die Zusage, die der Handel wirklich haelt: Was nach dem Zahlen
        // scheitert, wird erstattet. Ein eigener Satz und nicht absage.gestoert - jener
        // sagt "der Shop liess sich nicht anlegen, das Schild bleibt stehen".
        String zeile = bild(Shoptext.handelBalken(sprache, absage(
                Shophandel.Bescheid.GESTOERT, Handelsart.KUNDE_KAUFT)).get(0));

        assertEquals("&cDer Handel ist nicht zustande gekommen&7 - dein Geld ist nicht"
                + " weg.", zeile);
    }

    @Test
    @DisplayName("JEDER Bescheid bekommt eine Zeile - ausser KEIN_SHOP")
    void jederBescheidEineZeile() {
        // Die Vollstaendigkeitsprobe. Ohne sie fehlte einem neuen Bescheid der Satz, und
        // der Spieler bekaeme einen stummen Klick - genau die Lage, von der die acht
        // Schildabsagen handeln.
        for (Shophandel.Bescheid bescheid : Shophandel.Bescheid.values()) {
            for (Handelsart art : Handelsart.values()) {
                var zeilen = Shoptext.handelBalken(sprache,
                        bescheid == Shophandel.Bescheid.GEHANDELT
                                ? gehandelt(art, 16, 100L, 0L)
                                : absage(bescheid, art));
                boolean stumm = bescheid == Shophandel.Bescheid.KEIN_SHOP
                        || (bescheid == Shophandel.Bescheid.EIGENER_SHOP
                                && art == Handelsart.KUNDE_VERKAUFT);
                assertEquals(stumm ? 0 : 1, zeilen.size(), bescheid + " / " + art);
                if (!stumm) {
                    assertTrue(bild(zeilen.get(0)).length() > 5,
                            bescheid + " / " + art + " darf nicht leer sein: "
                                    + bild(zeilen.get(0)));
                }
            }
        }
    }

    @Test
    @DisplayName("Keine Handelszeile traegt eine offene Klammer")
    void keineOffeneKlammer() {
        // Die Gegenprobe zu "alle Werte werden IMMER gesetzt": Ueberzaehlige Werte sieht
        // Sprache.ersetze nie, fehlende stehen dem Leser als Klammer im Satz. Diese
        // Probe sucht sie - durch JEDEN Bescheid und JEDE Richtung.
        for (Shophandel.Bescheid bescheid : Shophandel.Bescheid.values()) {
            for (Handelsart art : Handelsart.values()) {
                for (Component zeile : Shoptext.handelBalken(sprache,
                        bescheid == Shophandel.Bescheid.GEHANDELT
                                ? gehandelt(art, 16, 100L, 7L)
                                : absage(bescheid, art))) {
                    assertTrue(!bild(zeile).contains("{") && !bild(zeile).contains("}"),
                            bescheid + " / " + art + ": " + bild(zeile));
                }
            }
        }
    }

    @Test
    @DisplayName("shopHandel wirft fuer KEIN_SHOP und ohne Richtung")
    void shopHandelWirftWoEsNichtsZuWaehlenGibt() {
        // Dieselbe Entscheidung wie bei shopAnlage fuer ABGEBAUT: Auf einen Klick ohne
        // Shop wird geschwiegen, dafuer gibt es keinen Satz - und wer hier danach fragt,
        // hat die Pruefung auf KEIN_SHOP vergessen. Ein Satz, den nie jemand liest,
        // muesste der Betreiber trotzdem uebersetzen.
        assertThrows(IllegalArgumentException.class,
                () -> Sprachschluessel.shopHandel(Shophandel.Bescheid.KEIN_SHOP,
                        Handelsart.KUNDE_KAUFT, false));
        assertThrows(IllegalArgumentException.class,
                () -> Sprachschluessel.shopHandel(Shophandel.Bescheid.GEHANDELT, null,
                        false));
    }

    @Test
    @DisplayName("Die Zuordnung kennt alle drei Erfolgssaetze - auch ohne Shoptext")
    void zuordnungKenntDieDreiErfolgssaetze() {
        // Derselbe Befund wie bei shopAnlage(ANGELEGT) am 08.11.2026: Eine Zuordnung,
        // die der Aufrufer teilweise selbst verzweigt, bleibt ohne Zeugen. Hier wird sie
        // unmittelbar gefragt - und sie IST das Verzeichnis, also soll sie vollstaendig
        // sein, gleich wer sie fragt.
        assertEquals(ShopSatz.SHOP_KAUF_GELUNGEN,
                Sprachschluessel.shopHandel(Shophandel.Bescheid.GEHANDELT,
                        Handelsart.KUNDE_KAUFT, false));
        assertEquals(ShopSatz.SHOP_KAUF_GELUNGEN,
                Sprachschluessel.shopHandel(Shophandel.Bescheid.GEHANDELT,
                        Handelsart.KUNDE_KAUFT, true),
                "beim KAUF gibt es keine Gebuehrzeile - der Besitzer zahlt sie");
        assertEquals(ShopSatz.SHOP_VERKAUF_GELUNGEN,
                Sprachschluessel.shopHandel(Shophandel.Bescheid.GEHANDELT,
                        Handelsart.KUNDE_VERKAUFT, false));
        assertEquals(ShopSatz.SHOP_VERKAUF_GEBUEHR,
                Sprachschluessel.shopHandel(Shophandel.Bescheid.GEHANDELT,
                        Handelsart.KUNDE_VERKAUFT, true));
    }

    // --- Werkzeug fuer den Handel ---------------------------------------------

    /** Ein gelungener Handel mit diesen Zahlen. */
    private static Shophandel.Ausgang gehandelt(Handelsart art, int stueck, long preis,
            long gebuehr) {
        return new Shophandel.Ausgang(Shophandel.Bescheid.GEHANDELT, art, shop(),
                new Shopregel.Handel(preis, gebuehr, preis - gebuehr, stueck));
    }

    /** Eine Absage - ohne gerechneten Handel, wie im Betrieb. */
    private static Shophandel.Ausgang absage(Shophandel.Bescheid bescheid,
            Handelsart art) {
        return new Shophandel.Ausgang(bescheid, art, shop(), null);
    }

    private static Kistenshop shop() {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, ANNA, 16, 100L, 80L, STEIN);
    }
}
