package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Buchung;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Handelsart;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherShops;
import de.freimarken.kern.Shopwerte;
import de.freimarken.kern.Warenprobe;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Handel am Kistenshop - Geld und Ware, in der einen Reihenfolge.
 *
 * <p><b>Diese Proben schreiben die SCHRITTFOLGE als Text auf</b> und zaehlen sie nicht.
 * Das ist die Lehre vom 08.11.2026: Eine Zaehltafel laesst sich hochdrehen und wird bei
 * einer Fluchtfolge blind. Hier steht, WAS in welcher Reihenfolge geschah - "abbuchen
 * KUNDE 100 SHOP_GEKAUFT -> true", dann "nehme KISTE 16" -, und eine vertauschte
 * Reihenfolge ist im Unterschied sofort zu lesen.
 *
 * <p>Die Kasse ist die ECHTE ({@link SpeicherKasse}), nur mitgeschrieben: So prueft jede
 * Probe nicht nur die Aufrufe, sondern auch den STAND danach - und die Zusage "die Summe
 * aller Buchungen eines Kontos ist sein Stand" gilt in der Probe wie im Betrieb.
 */
@DisplayName("Der Handel am Kistenshop")
class ShophandelTest {

    private static final String WELT = "freimarken:altfurt";
    private static final UUID KUNDE = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID BESITZER = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final long START = 1_000L;

    private final List<String> schritte = new ArrayList<>();
    private final Hand hand = new Hand(schritte);
    private final Shopablage ablage = new SpeicherShops();
    private final Kasse echteKasse = new SpeicherKasse(START);
    private final Kasse kasse = new Mitschrift(echteKasse, schritte);

    // ================================================== die Reihenfolge

    @Test
    @DisplayName("KAUF: Zahler, Ware weg, Ware hin, Empfaenger - in dieser Folge")
    void kaufGehtInDieserReihenfolge() {
        // DIE TRAGENDE PROBE DIESES BEREICHS. Die Hausregel steht im Kopf von Mietbuch:
        // "Die Reihenfolge der Schritte ist in JEDER Tat die, die kein Geld erzeugen
        // kann." Beim Mieten fliesst Geld nur in eine Richtung, hier in beide
        // gleichzeitig - also musste sie eine Fassung bekommen, die beide deckt.
        //
        // Der ZAHLER kommt zuerst, und zwar weil nur das Abbuchen die Deckung prueft und
        // ganz oder gar nicht geschieht. Faengt man mit der Ware an, hat der Kunde sie
        // einen Augenblick in der Hand, ohne gezahlt zu haben - und am Bundshop kommt
        // sie aus dem Nichts, dort waere es wirklich erzeugte Ware.
        var ausgang = handle(spielershop(), Handelsart.KUNDE_KAUFT, false);

        assertEquals(Shophandel.Bescheid.GEHANDELT, ausgang.bescheid());
        assertEquals(List.of(
                "abbuchen KUNDE 100 SHOP_GEKAUFT -> true",
                "nehme KISTE 16",
                "lege KUNDE 16",
                "gutschreiben BESITZER 100 SHOP_VERKAUFT"), schritte);
        assertEquals(START - 100L, echteKasse.guthaben(KUNDE));
        assertEquals(START + 100L, echteKasse.guthaben(BESITZER));
    }

    @Test
    @DisplayName("VERKAUF: dieselbe Folge, nur die Rollen getauscht")
    void verkaufGehtInDerselbenReihenfolge() {
        // Dass es EINE Abfolge ist und nicht zwei, ist der Grund fuer Shophandel.Seite:
        // Zwei Abfolgen waeren zwei Gelegenheiten, eine Zeile zu vergessen. Hier steht
        // der Beweis - dieselben vier Schritte, getauschte Rollen.
        var ausgang = handle(spielershop(), Handelsart.KUNDE_VERKAUFT, false);

        assertEquals(Shophandel.Bescheid.GEHANDELT, ausgang.bescheid());
        assertEquals(List.of(
                "abbuchen BESITZER 80 SHOP_GEKAUFT -> true",
                "nehme KUNDE 16",
                "lege KISTE 16",
                "gutschreiben KUNDE 80 SHOP_VERKAUFT"), schritte);
        assertEquals(START + 80L, echteKasse.guthaben(KUNDE));
        assertEquals(START - 80L, echteKasse.guthaben(BESITZER));
    }

    // ================================================== die drei Buchungen

    @Test
    @DisplayName("Die Gebuehr geht dem EMPFAENGER ab - der Preis bleibt voll")
    void gebuehrGehtVomEmpfaenger() {
        // DER BEFUND VOM 08.11.2026, der diesen Bereich am teuersten getroffen haette:
        // Der Javadoc von SHOP_VERKAUFT sagte "der Erloes, NACH Abzug der Gebuehr", der
        // von SHOP_GEBUEHR "eine EIGENE Zeile und kein stiller Abzug". Beides zusammen
        // zieht die Gebuehr ZWEIMAL ab - bei 100 Mark und 7 Prozent bekaeme der
        // Verkaeufer 93 gutgeschrieben und 7 abgebucht, also 86 statt 93.
        //
        // Die Summenprobe der Kasse faengt das NICHT: Sie gilt je Konto, und auf beiden
        // Konten stimmt die Summe. Nur diese Probe faengt es - darum prueft sie die
        // Zahlen UND den Stand.
        //
        // Haltbar ist nur eine Fassung: voller Preis gutschreiben, Gebuehr davon
        // abbuchen. Ein drittes Konto fuer den Staat gibt es nicht und soll es nicht
        // geben - die Ladenmiete bucht genauso ohne Empfaenger ab.
        var werte = Shopwerte.VORGABE.mitSpielerKauf(7);
        var ausgang = handle(spielershop(), Handelsart.KUNDE_KAUFT, false, werte);

        assertEquals(Shophandel.Bescheid.GEHANDELT, ausgang.bescheid());
        assertEquals(List.of(
                "abbuchen KUNDE 100 SHOP_GEKAUFT -> true",
                "nehme KISTE 16",
                "lege KUNDE 16",
                "gutschreiben BESITZER 100 SHOP_VERKAUFT",
                "abbuchen BESITZER 7 SHOP_GEBUEHR -> true"), schritte);
        // Netto bleibt der Erloes - und das ist die Zahl, die zaehlt.
        assertEquals(START + 93L, echteKasse.guthaben(BESITZER));
        assertEquals(100L, ausgang.handel().preis());
        assertEquals(7L, ausgang.handel().gebuehr());
        assertEquals(93L, ausgang.handel().erloes());
    }

    @Test
    @DisplayName("Erst gutschreiben, dann die Gebuehr - die andere Folge scheitert")
    void erstGutschreibenDannGebuehr() {
        // Die Reihenfolge ist zwingend und nicht Geschmack: Ein Verkaeufer OHNE Guthaben
        // bekaeme die Gebuehr nicht abgebucht, und abbuchen antwortet dann mit false -
        // er behielte den ganzen Preis. Nach der Gutschrift kann es nicht scheitern,
        // denn die Gebuehr ist nie groesser als der Preis.
        //
        // Geprueft mit einem Besitzer, dessen Konto LEER ist: Steht die Gebuehr vor der
        // Gutschrift, ist das zweite Schrittpaar vertauscht und diese Probe rot.
        Kasse leer = new SpeicherKasse(0L);
        var werte = Shopwerte.VORGABE.mitSpielerKauf(7);
        var handel = new Shophandel(buch(), hand,
                new Mitschrift(leer, schritte), werte, Logger.getAnonymousLogger());
        Kistenshop shop = spielershop();
        ablage.schreibe(shop);
        leer.eroeffne(KUNDE);
        leer.gutschreiben(KUNDE, 1_000L, Buchungsgrund.EROEFFNUNG);
        leer.eroeffne(BESITZER);
        hand.setzeBestand(999, 999, 999, 999);

        var ausgang = handel.handle(KUNDE, WELT, 60, 71, 6, Handelsart.KUNDE_KAUFT,
                false);

        assertEquals(Shophandel.Bescheid.GEHANDELT, ausgang.bescheid());
        assertEquals("gutschreiben BESITZER 100 SHOP_VERKAUFT", schritte.get(3));
        assertEquals("abbuchen BESITZER 7 SHOP_GEBUEHR -> true", schritte.get(4),
                "die Gebuehr muss NACH der Gutschrift kommen - sonst ist sie ungedeckt");
        assertEquals(93L, leer.guthaben(BESITZER));
    }

    @Test
    @DisplayName("Eine gerechnete Gebuehr von 0 bekommt KEINE Zeile")
    void keineZeileOhneGebuehr() {
        // Gefragt wird die GERECHNETE Gebuehr und nicht der Satz: Bei 5 Prozent auf 19
        // Mark ist sie abgerundet 0, und eine Zeile ueber 0 Mark waere eine Zeile ohne
        // Geldbewegung - genau das verbietet die Kasse. Mit einem Shop zu 19 Mark und 5
        // Prozent ist die Lage erreichbar, OBWOHL ein Satz eingestellt ist.
        var werte = Shopwerte.VORGABE.mitSpielerKauf(5);
        Kistenshop billig = new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, BESITZER, 1,
                19L, 0L, ware(), Set.of());
        var ausgang = handle(billig, Handelsart.KUNDE_KAUFT, false, werte);

        assertEquals(Shophandel.Bescheid.GEHANDELT, ausgang.bescheid());
        assertEquals(2, schritteMit("SHOP_").size(),
                "genau ZWEI Geldzeilen, keine dritte ueber 0 Mark: " + schritte);
        assertFalse(ausgang.handel().kostetGebuehr());
        // Und im Buch des Verkaeufers steht genau eine Zeile.
        assertEquals(List.of(Buchungsgrund.SHOP_VERKAUFT),
                gruende(echteKasse, BESITZER));
    }

    // ================================================== der Bundshop

    @Test
    @DisplayName("Am Bundshop wird die KISTE nicht angefasst")
    void bundshopFasstDieKisteNichtAn() {
        // Ein Bundshop hat unbegrenzten Vorrat und unbegrenzten Platz. Haetten wir dort
        // gezaehlt und entnommen, deckelte die Kiste den Bundshop - und ein Betreiber
        // muesste sie nachfuellen wie jeder Spieler.
        //
        // Die Entscheidung steht in Shophandel und nicht in der Bukkit-Naht: Dort waere
        // sie eine Regel in der einen Klasse, die ohne Server nicht pruefbar ist.
        hand.setzeBestand(0, 0, 999, 999);

        var ausgang = handle(bundshop(), Handelsart.KUNDE_KAUFT, false);

        assertEquals(Shophandel.Bescheid.GEHANDELT, ausgang.bescheid(),
                "eine LEERE Bundkiste verkauft trotzdem: " + schritte);
        assertEquals(List.of(
                "abbuchen KUNDE 100 SHOP_GEKAUFT -> true",
                "lege KUNDE 16"), schritte,
                "kein nehme KISTE und keine Gegenbuchung");
    }

    @Test
    @DisplayName("Am Bundshop faellt keine Gegenbuchung - das Geld verschwindet")
    void bundshopHatKeineGegenbuchung() {
        var ausgang = handle(bundshop(), Handelsart.KUNDE_KAUFT, false);

        assertEquals(Shophandel.Bescheid.GEHANDELT, ausgang.bescheid());
        assertEquals(START - 100L, echteKasse.guthaben(KUNDE));
        // Die staerkste Senke des Servers, und sie hat keine Zeile auf einem zweiten
        // Konto - hier so wenig wie bei der Ladenmiete. Der Staat hat kein Konto.
        assertEquals(List.of(
                "abbuchen KUNDE 100 SHOP_GEKAUFT -> true",
                "lege KUNDE 16"), schritte,
                "keine zweite Geldzeile - die Ware kommt trotzdem. Und die Kasse"
                        + " wird fuer den Bund gar nicht GERUFEN: Ein"
                        + " gutschreiben(null, ...) wuerfe, bezahle faenge es und"
                        + " schriebe SEVERE - der Handel sieht gelungen aus, und"
                        + " der Betreiber liest eine Zeile ueber Geld, das nie"
                        + " jemandem gehoerte.");
    }

    @Test
    @DisplayName("Verkauf an den Bundshop: niemand zahlt, der Kunde bekommt")
    void verkaufAnBundshopZahltNiemand() {
        // Die Gegenrichtung: Der Zahler ist der Bund und hat kein Konto, also faellt der
        // erste Schritt weg. Das Geld kommt aus dem Nichts - die staerkste Quelle.
        var ausgang = handle(bundshop(), Handelsart.KUNDE_VERKAUFT, false);

        assertEquals(Shophandel.Bescheid.GEHANDELT, ausgang.bescheid());
        assertEquals(List.of(
                "nehme KUNDE 16",
                "gutschreiben KUNDE 80 SHOP_VERKAUFT"), schritte,
                "kein abbuchen und kein lege KISTE");
        assertEquals(START + 80L, echteKasse.guthaben(KUNDE));
    }

    @Test
    @DisplayName("Am Bundshop faellt beim KAUF keine Gebuehr, beim Verkauf schon")
    void bundshopGebuehrNurBeimVerkauf() {
        // Die vierte Lage hat keinen Schluessel, und das ist eine Tatsache und keine
        // Einstellung: Beim Kauf am Bundshop bekommt niemand Geld, dem eine Gebuehr
        // abzuziehen waere. Geprueft mit BEIDEN Saetzen hochgedreht - dann ist eine
        // Fassung, die den Spielersatz auch am Bundshop nimmt, sofort rot.
        var werte = Shopwerte.VORGABE.mitSpielerKauf(7).mitSpielerVerkauf(11)
                .mitBundVerkauf(23);

        var kauf = handle(bundshop(), Handelsart.KUNDE_KAUFT, false, werte);
        assertEquals(0L, kauf.handel().gebuehr(), "am Bundshop kauft man ohne Gebuehr");

        schritte.clear();
        neuerStand();
        var verkauf = handle(bundshop(), Handelsart.KUNDE_VERKAUFT, false, werte);
        assertEquals(18L, verkauf.handel().gebuehr(), "23 Prozent von 80 Mark sind 18");
        assertEquals(List.of(
                "nehme KUNDE 16",
                "gutschreiben KUNDE 80 SHOP_VERKAUFT",
                "abbuchen KUNDE 18 SHOP_GEBUEHR -> true"), schritte);
    }

    // ================================================== die Absagen

    @Test
    @DisplayName("Am EIGENEN Shop wird nicht gehandelt - und nichts bewegt")
    void amEigenenShopWirdNichtGehandelt() {
        // Entschieden am 08.11.2026. Der wichtigere Grund ist nicht, dass Geld an sich
        // selbst sinnlos waere, sondern der Linksklick: Er ist in Minecraft der ANFANG
        // eines Abbaus, und am eigenen Schild verbietet der Schutz nichts. Haetten wir
        // ihn zum Verkauf gemacht, muesste der Schutz des eigenen Schildes umgebaut
        // werden - und ein unzerbrechliches Schild ist schlimmer als ein Shop, an dem man
        // nicht selbst kauft.
        Kistenshop shop = spielershop();
        ablage.schreibe(shop);
        hand.setzeBestand(999, 999, 999, 999);

        var ausgang = handel(Shopwerte.VORGABE).handle(BESITZER, WELT, 60, 71, 6,
                Handelsart.KUNDE_KAUFT, false);

        assertEquals(Shophandel.Bescheid.EIGENER_SHOP, ausgang.bescheid());
        assertEquals(List.of(), schritte, "nichts darf geschehen sein");
        assertNull(ausgang.handel());
    }

    @Test
    @DisplayName("Die falsche Richtung sagt, WELCHE Richtung es gibt")
    void falscheRichtungSagtWelche() {
        // Zwei Bescheide und nicht einer: Ein Shop mit nur einem Preis sieht aus wie
        // jeder andere, und der Kunde soll lesen, welche Maustaste geht.
        Kistenshop nurVerkauf = new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, BESITZER, 16,
                100L, 0L, ware(), Set.of());
        Kistenshop nurAnkauf = new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, BESITZER, 16,
                0L, 80L, ware(), Set.of());

        assertEquals(Shophandel.Bescheid.KAUFT_NICHT_AN,
                handle(nurVerkauf, Handelsart.KUNDE_VERKAUFT, false).bescheid());
        neuerStand();
        assertEquals(Shophandel.Bescheid.VERKAUFT_NICHT,
                handle(nurAnkauf, Handelsart.KUNDE_KAUFT, false).bescheid());
        assertEquals(List.of(), schritte);
    }

    @Test
    @DisplayName("Eine unlesbare Ware sagt es und handelt nicht")
    void unlesbareWareSagtEs() {
        // Ohne diese Frage VOR dem Zaehlen verwandelt eine unlesbare Ware sich
        // stillschweigend in Vorrat 0 - und der Shop meldete fuer immer "ausverkauft",
        // obwohl die Kiste voll ist. Der Besitzer erfuehre keinen Grund.
        hand.lesbar = false;

        var ausgang = handle(spielershop(), Handelsart.KUNDE_KAUFT, false);

        assertEquals(Shophandel.Bescheid.WARE_UNLESBAR, ausgang.bescheid());
        assertEquals(List.of(), schritte);
    }

    @Test
    @DisplayName("Jeder Mangel bekommt seinen eigenen Bescheid - acht Lagen")
    void jederMangelSeinBescheid() {
        // "Das geht nicht" laesst einen Kunden dreimal klicken und dann aufhoeren -
        // genau daran haben die Vorbilder ihre Forenfragen. Die Reihenfolge der Pruefung
        // ist die der Haeufigkeit: erst der Vorrat, dann der Platz, dann das Geld.
        //
        // Alle acht in EINER Probe, weil sie eine Tafel sind: Der SELBE Mangel heisst
        // auf den zwei Seiten verschieden, und eine Fassung, die beide Seiten
        // zusammenlegt, schickt den Kunden zum Aufraeumen eines Rucksacks, der nicht das
        // Problem ist.
        pruefe(Handelsart.KUNDE_KAUFT, 0, 999, 999, 999, START,
                Shophandel.Bescheid.AUSVERKAUFT);
        pruefe(Handelsart.KUNDE_KAUFT, 999, 999, 999, 0, START,
                Shophandel.Bescheid.KEIN_PLATZ);
        pruefe(Handelsart.KUNDE_KAUFT, 999, 999, 999, 999, 99L,
                Shophandel.Bescheid.KEIN_GELD);
        pruefe(Handelsart.KUNDE_VERKAUFT, 999, 999, 0, 999, START,
                Shophandel.Bescheid.KEINE_WARE);
        pruefe(Handelsart.KUNDE_VERKAUFT, 999, 0, 999, 999, START,
                Shophandel.Bescheid.KISTE_VOLL);
        pruefe(Handelsart.KUNDE_VERKAUFT, 999, 999, 999, 999, 79L,
                Shophandel.Bescheid.SHOP_PLEITE);
    }

    @Test
    @DisplayName("Fehlen ZWEI Dinge, wird der Vorrat genannt - nicht der Platz")
    void beiZweiMaengelnZaehltDerVorrat() {
        // BEFUND DER EINSPRITZUNG vom 08.11.2026: Die Reihenfolge der drei Pruefungen
        // liess sich vertauschen, ohne dass eine Probe rot wurde - denn die Tafel oben
        // fuehrt jede Lage EINZELN, und einzeln ist jede Reihenfolge richtig.
        //
        // Gemeint ist die Reihenfolge aber als Rangfolge der Haeufigkeit: "Ausverkauft"
        // ist der haeufigste Fall ueberhaupt, und er sagt dem Kunden, dass er
        // wiederkommen soll. "Kein Platz" schickt ihn zum Aufraeumen - und wenn die
        // Kiste ohnehin leer ist, raeumt er umsonst auf und klickt wieder.
        //
        // Darum stehen hier ALLE DREI Maengel gleichzeitig: Genannt wird der erste der
        // Rangfolge, und eine vertauschte Reihenfolge ist sofort zu lesen.
        pruefe(Handelsart.KUNDE_KAUFT, 0, 999, 999, 0, 0L,
                Shophandel.Bescheid.AUSVERKAUFT);
        // Und wenn nur Platz und Geld fehlen, ist es der PLATZ: Daran kann der Kunde
        // etwas aendern, am Guthaben eines Augenblicks nicht.
        pruefe(Handelsart.KUNDE_KAUFT, 999, 999, 999, 0, 0L,
                Shophandel.Bescheid.KEIN_PLATZ);
        // Dieselbe Rangfolge in der anderen Richtung - mit den Saetzen der anderen
        // Seite.
        pruefe(Handelsart.KUNDE_VERKAUFT, 999, 0, 0, 999, 0L,
                Shophandel.Bescheid.KEINE_WARE);
        pruefe(Handelsart.KUNDE_VERKAUFT, 999, 0, 999, 999, 0L,
                Shophandel.Bescheid.KISTE_VOLL);
    }

    @Test
    @DisplayName("Eine Absage bewegt nichts - auch kein Geld")
    void absageBewegtNichts() {
        hand.setzeBestand(0, 999, 999, 999);

        handle(spielershop(), Handelsart.KUNDE_KAUFT, false);

        assertEquals(List.of(), schritte);
        assertEquals(START, echteKasse.guthaben(KUNDE));
    }

    @Test
    @DisplayName("Ohne Shop am Schild wird GESCHWIEGEN")
    void ohneShopWirdGeschwiegen() {
        // Die Ablage bleibt leer: An diesem Schild haengt kein Shop. Darauf gibt es
        // keine Absage - ein Spieler, der auf ein Wegweiserschild klickt, bekaeme sonst
        // eine Meldung dafuer, dass dort kein Laden ist.
        var ausgang = handel(Shopwerte.VORGABE).handle(KUNDE, WELT, 60, 71, 6,
                Handelsart.KUNDE_KAUFT, false);

        assertEquals(Shophandel.Bescheid.KEIN_SHOP, ausgang.bescheid());
        assertNull(ausgang.shop());
        assertEquals(List.of(), schritte);
    }

    // ================================================== Stufen

    @Test
    @DisplayName("Der Sneak-Klick nimmt so viel, wie geht - der einfache eine Stufe")
    void sneakNimmtMehr() {
        // Lumias Regel. Dass BEIDE durch dieselbe Schranke gehen, ist der Punkt: Ein
        // eigenes "reicht der Vorrat?" fuer den haeufigeren Weg waere die zweite
        // Wahrheit, und dann verkaufte der einfache Klick, was der Sneak-Klick abgelehnt
        // haette.
        Kistenshop shop = new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, BESITZER, 16, 10L,
                0L, ware(), Set.of());

        var einfach = handle(shop, Handelsart.KUNDE_KAUFT, false);
        assertEquals(16, einfach.handel().stueck(), "ein Klick ist eine Stufe");
        assertEquals(10L, einfach.handel().preis());

        schritte.clear();
        neuerStand();
        var sneak = handle(shop, Handelsart.KUNDE_KAUFT, true);
        assertEquals(64, sneak.handel().stueck(),
                "Stapel 64, Shopmenge 16: vier Stufen sind ein Stapel");
        assertEquals(40L, sneak.handel().preis());
    }

    @Test
    @DisplayName("Die Stapelgroesse der WARE begrenzt den Sneak-Klick")
    void stapelgroesseBegrenztDenSneak() {
        // Die Zahl kommt gemessen vom Gegenstand und nicht als 64: Werkzeug und jedes
        // verzauberte Stueck stapeln zu 1, und verzauberte Ausruestung ist seit dem
        // 08.11.2026 der Normalfall. Mit einer festen 64 forderte ein Sneak-Klick an
        // einem Spitzhacken-Shop mit Menge 1 vierundsechzig Stufen an.
        hand.stapel = 1;
        Kistenshop spitzhacken = new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, BESITZER, 1,
                10L, 0L, ware(), Set.of());

        var sneak = handle(spitzhacken, Handelsart.KUNDE_KAUFT, true);

        assertEquals(1, sneak.handel().stueck(),
                "eine Spitzhacke IST ein Stapel - der Sneak-Klick handelt genau eine");
    }

    // ================================================== die Stoerfaelle

    @Test
    @DisplayName("Kommt die Ware nicht aus der Kiste, wird ERSTATTET")
    void wareKommtNichtAusDerKisteWirdErstattet() {
        // Der Zahler ist zuerst abgebucht - das ist die Reihenfolge, die kein Geld
        // erzeugen kann. Wenn danach die Ware nicht kommt, hat er gezahlt und nichts
        // bekommen, und genau dafuer gibt es SHOP_ERSTATTET: dieselbe Antwort wie bei
        // der Parzelle, dem Beruf und dem Laden.
        //
        // Nach einer genauen Platzrechnung ist der Fall nicht erreichbar. Dass er
        // trotzdem gebaut und geprueft ist, ist der Unterschied zwischen "kann nicht" und
        // "darf nicht" - "nicht erreichbar" ist kein Zustand, auf den man Geld setzt.
        hand.nimmtHoechstens = 0;

        var ausgang = handle(spielershop(), Handelsart.KUNDE_KAUFT, false);

        assertEquals(Shophandel.Bescheid.GESTOERT, ausgang.bescheid());
        assertEquals(List.of(
                "abbuchen KUNDE 100 SHOP_GEKAUFT -> true",
                "nehme KISTE 16",
                "gutschreiben KUNDE 100 SHOP_ERSTATTET"), schritte);
        // Und das Entscheidende: Der Kunde steht wieder, wo er war.
        assertEquals(START, echteKasse.guthaben(KUNDE));
        assertEquals(START, echteKasse.guthaben(BESITZER));
    }

    @Test
    @DisplayName("Kommt die Ware nicht an, geht sie zurueck UND das Geld")
    void wareKommtNichtAnGehtZurueck() {
        // Der zweite Stoerfall, und er braucht beides: Die Ware ist schon aus der Kiste,
        // also muss sie zurueck - die Plaetze sind gerade frei geworden, im selben Takt
        // laufen sie niemandem zu. Und das Geld ist schon weg, also wird erstattet.
        //
        // KEIN Teilhandel: Was angekommen ist, wird wieder genommen. Ein halber Handel
        // mit halbem Preis ist genau die Luecke aus ChestShop PR #522.
        hand.legtHoechstens = 0;

        var ausgang = handle(spielershop(), Handelsart.KUNDE_KAUFT, false);

        assertEquals(Shophandel.Bescheid.GESTOERT, ausgang.bescheid());
        assertEquals(List.of(
                "abbuchen KUNDE 100 SHOP_GEKAUFT -> true",
                "nehme KISTE 16",
                "lege KUNDE 16",
                "nehme KUNDE 0",
                "lege KISTE 16",
                "gutschreiben KUNDE 100 SHOP_ERSTATTET"), schritte);
        assertEquals(START, echteKasse.guthaben(KUNDE));
    }

    @Test
    @DisplayName("Ein TEILWEISE angekommener Stapel wird GANZ zurueckgenommen")
    void teilweiseAngekommenWirdGanzZurueck() {
        // Die haertere Haelfte derselben Zusage: addItem fuegt bei zu wenig Platz
        // teilweise ein. Wuerde nur der REST zurueckgelegt, laege die Haelfte beim
        // Kunden und die Haelfte in der Kiste - und gebucht waere der ganze Preis, dann
        // erstattet. Der Kunde hiesse acht Stueck geschenkt.
        hand.legtHoechstens = 8;

        handle(spielershop(), Handelsart.KUNDE_KAUFT, false);

        assertEquals(List.of(
                "abbuchen KUNDE 100 SHOP_GEKAUFT -> true",
                "nehme KISTE 16",
                "lege KUNDE 16",
                "nehme KUNDE 8",
                "lege KISTE 16",
                "gutschreiben KUNDE 100 SHOP_ERSTATTET"), schritte,
                "die ANGEKOMMENEN acht werden wieder genommen, und alle 16 gehen"
                        + " zurueck in die Kiste");
    }

    @Test
    @DisplayName("Erstattet wird der PREIS, nicht der Erloes - mit Gebuehr gemessen")
    void erstattetWirdDerPreis() {
        // BEFUND DER NACHSPRITZUNG N1 vom 08.11.2026: handel.erloes() statt
        // handel.preis() in der Erstattung liess sich einsetzen, ohne dass EINE Probe
        // rot wurde. Der Grund ist nicht, dass die Erstattung ungeprueft war - sie ist
        // es dreifach -, sondern dass alle diese Proben mit Gebuehr 0 laufen, und dort
        // ist erloes == preis. Die zwei Zahlen unterschieden sich nur in der Sache,
        // die KEINE Probe fuhr.
        //
        // Genau der Nutzergrundsatz "der Unterschied muss der geprueefte sein": Wo zwei
        // Werte verglichen werden, duerfen sie sich nur in dem unterscheiden, worum es
        // geht. Darum stehen hier 7 Prozent.
        //
        // Und die Sache selbst waegt: Gezahlt hat der Kunde den PREIS. Bekaeme er den
        // Erloes zurueck, fehlte ihm die Gebuehr eines Handels, der nie stattgefunden
        // hat - und zwar lautlos, denn beide Zeilen stehen im Buch und die Summe stimmt.
        hand.nimmtHoechstens = 0;
        var werte = Shopwerte.VORGABE.mitSpielerKauf(7);

        var ausgang = handle(spielershop(), Handelsart.KUNDE_KAUFT, false, werte);

        assertEquals(Shophandel.Bescheid.GESTOERT, ausgang.bescheid());
        assertEquals(List.of(
                "abbuchen KUNDE 100 SHOP_GEKAUFT -> true",
                "nehme KISTE 16",
                "gutschreiben KUNDE 100 SHOP_ERSTATTET"), schritte,
                "zurueck kommt der PREIS (100) und nicht der Erloes (93)");
        assertEquals(START, echteKasse.guthaben(KUNDE),
                "der Kunde steht GENAU wieder, wo er war - auf die Mark");
    }

    @Test
    @DisplayName("Erstattet wird auch beim VERKAUF - dort ist der Zahler der Besitzer")
    void erstattungAuchBeimVerkauf() {
        // Die andere Richtung, und sie war gar nicht gefahren: Beim Verkauf zahlt der
        // SHOPBESITZER, also muss ihm erstattet werden. Eine Fassung, die nur den Kunden
        // kennt, liesse den Besitzer gezahlt und ohne Ware zurueck.
        //
        // Auch hier mit Gebuehr, und zwar mit der des VERKAUFS: Die traegt der Kunde als
        // Empfaenger, der Besitzer zahlt den vollen Preis - und genau den bekommt er
        // zurueck.
        hand.nimmtHoechstens = 0;
        var werte = Shopwerte.VORGABE.mitSpielerVerkauf(10);

        var ausgang = handle(spielershop(), Handelsart.KUNDE_VERKAUFT, false, werte);

        assertEquals(Shophandel.Bescheid.GESTOERT, ausgang.bescheid());
        assertEquals(List.of(
                "abbuchen BESITZER 80 SHOP_GEKAUFT -> true",
                "nehme KUNDE 16",
                "gutschreiben BESITZER 80 SHOP_ERSTATTET"), schritte);
        assertEquals(START, echteKasse.guthaben(BESITZER));
        assertEquals(START, echteKasse.guthaben(KUNDE));
    }

    @Test
    @DisplayName("Am Bundshop gibt es nichts zu erstatten - niemand hat gezahlt")
    void bundshopVerkaufOhneErstattung() {
        // Beim Verkauf an einen Bundshop ist der Zahler der Bund und hat kein Konto.
        // Scheitert die Ware, gibt es also keine Zahlung zurueckzudrehen - und eine
        // Erstattungszeile auf irgendeinem Konto waere Geld aus nichts.
        hand.nimmtHoechstens = 0;

        var ausgang = handle(bundshop(), Handelsart.KUNDE_VERKAUFT, false);

        assertEquals(Shophandel.Bescheid.GESTOERT, ausgang.bescheid());
        assertEquals(List.of("nehme KUNDE 16"), schritte);
        assertEquals(START, echteKasse.guthaben(KUNDE));
    }

    // ================================================== der Klick selbst

    @Test
    @DisplayName("Verbraucht ist der Klick nur an einem FREMDEN Shop")
    void verbrauchtNurAmFremdenShop() {
        // Die Frage entscheidet, ob der Ereigniszweig beide Ergebnisse auf DENY setzt -
        // und daran haengt viel: Ein Linksklick ist der ANFANG eines Abbaus, ein
        // Rechtsklick mit Blockware in der Hand SETZT einen Block.
        //
        // Zwei Faelle sind NICHT verbraucht, und sie sind es aus demselben Grund: Dort
        // wurde gar nicht gehandelt. Ein Wegweiserschild soll sich weiter abbauen
        // lassen, und das eigene Schild auch - verbrauchten wir den Klick des Besitzers,
        // waere sein Shopschild unzerbrechlich.
        for (Shophandel.Bescheid bescheid : Shophandel.Bescheid.values()) {
            boolean erwartet = bescheid != Shophandel.Bescheid.KEIN_SHOP
                    && bescheid != Shophandel.Bescheid.EIGENER_SHOP;
            var ausgang = new Shophandel.Ausgang(bescheid, Handelsart.KUNDE_KAUFT, null,
                    null);
            assertEquals(erwartet, ausgang.verbraucht(), bescheid.toString());
        }
    }

    @Test
    @DisplayName("amSchild geht durch dieselbe Stelle wie der Handel")
    void amSchildGehtDurchDieselbeStelle() {
        // Die Handelstuer fragt, OB hier ein Shop steht, der Handel braucht WELCHER -
        // dieselbe Frage mit zwei Antwortformen. Zwei Wege dahin liefen auseinander, und
        // dann oeffnete die Tuer an einer Stelle, an der nicht gehandelt wird.
        Kistenshop shop = spielershop();
        ablage.schreibe(shop);
        var handel = handel(Shopwerte.VORGABE);

        assertEquals(shop, handel.amSchild(WELT, 60, 71, 6));
        assertNull(handel.amSchild(WELT, 99, 99, 99));
    }

    // --- Werkzeug -------------------------------------------------------------

    private Kistenshop spielershop() {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, BESITZER, 16, 100L, 80L,
                ware(), Set.of());
    }

    private Kistenshop bundshop() {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, null, 16, 100L, 80L, ware(), Set.of());
    }

    private static Warenprobe ware() {
        return new Warenprobe("STONE", "inhalt");
    }

    private Shopbuch buch() {
        return new Shopbuch(ablage, new Leser(), (wer, welt, x, y, z) -> true,
                Logger.getAnonymousLogger());
    }

    private Shophandel handel(Shopwerte werte) {
        return new Shophandel(buch(), hand, kasse, werte, Logger.getAnonymousLogger());
    }

    private Shophandel.Ausgang handle(Kistenshop shop, Handelsart art,
            boolean ganzerStapel) {
        return handle(shop, art, ganzerStapel, Shopwerte.VORGABE);
    }

    private Shophandel.Ausgang handle(Kistenshop shop, Handelsart art,
            boolean ganzerStapel, Shopwerte werte) {
        ablage.schreibe(shop);
        neuerStand();
        return handel(werte).handle(KUNDE, WELT, 60, 71, 6, art, ganzerStapel);
    }

    /** Beide Konten eroeffnet, beide Seiten voll - der gewoehnliche Fall. */
    private void neuerStand() {
        echteKasse.eroeffne(KUNDE);
        echteKasse.eroeffne(BESITZER);
        if (hand.kistenVorrat == 0 && hand.kundenVorrat == 0) {
            hand.setzeBestand(999, 999, 999, 999);
        }
    }

    /** Ein Mangel, eine Lage, ein Bescheid - fuer die Tafel der acht Absagen. */
    private void pruefe(Handelsart art, int kistenVorrat, int kistenPlatz,
            int kundenVorrat, int kundenPlatz, long guthaben,
            Shophandel.Bescheid erwartet) {
        List<String> eigene = new ArrayList<>();
        Hand eigeneHand = new Hand(eigene);
        eigeneHand.setzeBestand(kistenVorrat, kistenPlatz, kundenVorrat, kundenPlatz);
        Shopablage eigeneAblage = new SpeicherShops();
        eigeneAblage.schreibe(spielershop());
        Kasse eigeneKasse = new SpeicherKasse(guthaben);
        eigeneKasse.eroeffne(KUNDE);
        eigeneKasse.eroeffne(BESITZER);
        var handel = new Shophandel(
                new Shopbuch(eigeneAblage, new Leser(),
                        (wer, welt, x, y, z) -> true, Logger.getAnonymousLogger()),
                eigeneHand, eigeneKasse, Shopwerte.VORGABE,
                Logger.getAnonymousLogger());

        var ausgang = handel.handle(KUNDE, WELT, 60, 71, 6, art, false);

        assertEquals(erwartet, ausgang.bescheid(),
                art + " mit Kiste " + kistenVorrat + "/" + kistenPlatz + ", Kunde "
                        + kundenVorrat + "/" + kundenPlatz + ", Geld " + guthaben);
        assertEquals(List.of(), eigene, "eine Absage bewegt nichts: " + eigene);
    }

    private List<String> schritteMit(String stueck) {
        return schritte.stream().filter(z -> z.contains(stueck)).toList();
    }

    private static List<Buchungsgrund> gruende(Kasse kasse, UUID wer) {
        List<Buchungsgrund> gruende = new ArrayList<>();
        for (Buchung buchung : kasse.verlauf(wer, 20)) {
            if (buchung.grund() != Buchungsgrund.EROEFFNUNG) {
                gruende.add(buchung.grund());
            }
        }
        return gruende;
    }

    private static String name(UUID wer) {
        if (wer == null) {
            return "BUND";
        }
        return wer.equals(KUNDE) ? "KUNDE" : "BESITZER";
    }

    /**
     * Die Kasse, nur mitgeschrieben - die ECHTE rechnet.
     *
     * <p><b>Darum ist der STAND in jeder Probe mitgeprueft.</b> Eine Attrappe, die nur
     * mitschreibt, saehe eine vertauschte Reihenfolge, aber nicht, dass am Ende die
     * falsche Zahl auf dem Konto liegt - und genau das war der Befund zu den drei
     * Buchungen: Beide Fassungen rufen dieselben Methoden, nur mit anderen Betraegen.
     */
    private record Mitschrift(Kasse echt, List<String> schritte) implements Kasse {

        @Override
        public long guthaben(UUID spieler) {
            return echt.guthaben(spieler);
        }

        @Override
        public boolean eroeffne(UUID spieler) {
            return echt.eroeffne(spieler);
        }

        @Override
        public boolean abbuchen(UUID spieler, long betrag, Buchungsgrund grund) {
            String zeile = "abbuchen " + name(spieler) + " " + betrag + " "
                    + grund;
            try {
                boolean ergebnis = echt.abbuchen(spieler, betrag, grund);
                schritte.add(zeile + " -> " + ergebnis);
                return ergebnis;
            } catch (RuntimeException ausnahme) {
                schritte.add(zeile + " -> WIRFT");
                throw ausnahme;
            }
        }

        @Override
        public void gutschreiben(UUID spieler, long betrag, Buchungsgrund grund) {
            // ZUERST mitschreiben, dann rufen - BEFUND DER NACHSPRITZUNG N3 vom
            // 08.11.2026. Vorher stand es umgekehrt, und damit war ein Aufruf, der
            // WIRFT, im Protokoll unsichtbar: Nimmt man dem Handel die Wache gegen
            // einen Empfaenger ohne Konto (den Bund), ruft er
            // gutschreiben(null, ...), die Kasse wirft, bezahle faengt es und
            // schreibt SEVERE - und die Reihenfolgeprobe sah genau nichts.
            //
            // Ein Aufruf ist auch dann ein Aufruf, wenn er scheitert. Das ist
            // dieselbe Einsicht wie bei einem Zaehler, der nur Erfolge zaehlt.
            schritte.add("gutschreiben " + name(spieler) + " " + betrag + " " + grund);
            echt.gutschreiben(spieler, betrag, grund);
        }

        @Override
        public List<Buchung> verlauf(UUID spieler, int hoechstens) {
            return echt.verlauf(spieler, hoechstens);
        }

        @Override
        public long summeSeit(UUID spieler, Instant ab, Buchungsgrund grund) {
            return echt.summeSeit(spieler, ab, grund);
        }
    }

    /**
     * Die Warenhand, die mitschreibt und auf Befehl weniger bewegt.
     *
     * <p>{@code nimmtHoechstens} und {@code legtHoechstens} sind die zwei Stoerfaelle:
     * Beide sind nach einer genauen Platzrechnung nicht erreichbar, und genau darum
     * muessen sie erzwingbar sein. Was nicht erzwingbar ist, ist nicht geprueft.
     */
    private static final class Hand implements Shophandel.Warenhand {

        private final List<String> schritte;
        private int kistenVorrat = 999;
        private int kistenPlatz = 999;
        private int kundenVorrat = 999;
        private int kundenPlatz = 999;
        private int stapel = 64;
        private boolean lesbar = true;
        private int nimmtHoechstens = Integer.MAX_VALUE;
        private int legtHoechstens = Integer.MAX_VALUE;

        Hand(List<String> schritte) {
            this.schritte = schritte;
        }

        void setzeBestand(int kistenVorrat, int kistenPlatz, int kundenVorrat,
                int kundenPlatz) {
            this.kistenVorrat = kistenVorrat;
            this.kistenPlatz = kistenPlatz;
            this.kundenVorrat = kundenVorrat;
            this.kundenPlatz = kundenPlatz;
        }

        @Override
        public boolean lesbar(Kistenshop shop) {
            return lesbar;
        }

        @Override
        public int stapelgroesse(Kistenshop shop) {
            return stapel;
        }

        @Override
        public Shophandel.Bestand bestand(Shophandel.Seite seite, UUID kunde,
                Kistenshop shop) {
            return seite == Shophandel.Seite.KISTE
                    ? new Shophandel.Bestand(kistenVorrat, kistenPlatz)
                    : new Shophandel.Bestand(kundenVorrat, kundenPlatz);
        }

        @Override
        public int nehme(Shophandel.Seite seite, UUID kunde, Kistenshop shop,
                int stueck) {
            // Protokolliert wird die ANFORDERUNG und nicht das Erreichte, in
            // beide Richtungen gleich: Sonst waere "nehme KISTE 0" nicht von
            // "nehme wurde nicht gerufen" zu unterscheiden, und genau das ist der
            // Unterschied, den eine Reihenfolgeprobe lesen muss.
            schritte.add("nehme " + seite + " " + stueck);
            return Math.min(stueck, nimmtHoechstens);
        }

        @Override
        public int lege(Shophandel.Seite seite, UUID kunde, Kistenshop shop,
                int stueck) {
            schritte.add("lege " + seite + " " + stueck);
            return Math.min(stueck, legtHoechstens);
        }
    }

    /** Ein Warenleser, der nichts kann - der Handel fragt ihn nicht. */
    private static final class Leser implements Shopbuch.Warenleser {

        @Override
        public Warenprobe ausNamen(String getippt) {
            throw new AssertionError("der Handel fragt den Warenleser nicht");
        }

        @Override
        public Warenprobe ausKiste(String welt, int x, int y, int z) {
            throw new AssertionError("der Handel fragt den Warenleser nicht");
        }

        @Override
        public boolean taugtAlsShopkiste(String welt, int x, int y, int z) {
            throw new AssertionError("der Handel fragt den Warenleser nicht");
        }
    }
}
