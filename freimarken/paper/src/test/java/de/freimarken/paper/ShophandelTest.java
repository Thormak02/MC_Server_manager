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
import de.freimarken.kern.Marktwerte;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherShops;
import de.freimarken.kern.Shopwerte;
import de.freimarken.kern.Warenprobe;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
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

    /**
     * Die Uhr, die KASSE und HANDEL teilen.
     *
     * <p>Dieselbe, und das ist keine Sparsamkeit - dieselbe Lehre wie in
     * {@code MarktBefehlTest}: Der Tagesdeckel des Bundes fragt die Kasse nach den
     * Buchungen SEIT dem Tagesbeginn. Haette die Kasse die Systemuhr und der Handel eine
     * gestellte, laege eine vorgebuchte Zeile je nach Tageszeit vor oder nach jener
     * Grenze - und die Probe waere nachts rot und mittags gruen.
     */
    private static final Clock UHR = Clock.fixed(
            Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);

    private final List<String> schritte = new ArrayList<>();
    private final List<String> protokollzeilen = new ArrayList<>();
    private final Hand hand = new Hand(schritte);
    private final Shopablage ablage = new SpeicherShops();
    private final Kasse echteKasse = new SpeicherKasse(START, UHR);
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
        Wirtschaftsstand eigenerStand = stand(werte);
        var handel = new Shophandel(buch(eigenerStand), hand,
                new Mitschrift(leer, schritte), eigenerStand, UHR,
                Logger.getAnonymousLogger());
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

        var ausgang = handle(bundladen(), Handelsart.KUNDE_KAUFT, false);

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
        var ausgang = handle(bundladen(), Handelsart.KUNDE_KAUFT, false);

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
        //
        // UND DARUM STEHT HIER BUNDESMARKT_VERKAUF: Das ist die benannte Luecke aus Plan
        // 7.4, Wort fuer Wort - "ein Bundshop mit Verkaufspreis ist eine Geldquelle ohne
        // Tagesdeckel, denn der Deckel rechnet ueber bundesmarkt_verkauf und der Shop
        // bucht shop_verkauft". Mit SHOP_VERKAUFT zaehlt der Deckel seine eigenen Zeilen
        // nicht mit und ist nach dem ersten Stand wieder offen.
        var ausgang = handle(bundstand(), Handelsart.KUNDE_VERKAUFT, false);

        assertEquals(Shophandel.Bescheid.GEHANDELT, ausgang.bescheid());
        assertEquals(List.of(
                "nehme KUNDE 5",
                "gutschreiben KUNDE 1 BUNDESMARKT_VERKAUF"), schritte,
                "kein abbuchen, kein lege KISTE - und der Grund des BUNDESMARKTES");
        assertEquals(START + 1L, echteKasse.guthaben(KUNDE));
    }

    @Test
    @DisplayName("Der Tagesdeckel beschneidet den Stapel - und zahlt genau bis an ihn")
    void deckelBeschneidetDenStapel() {
        // DIE ZUSAGE, DIE VOM BEFEHL HERUEBERKOMMT. /markt verkaufen hatte sie
        // ("Kurz vor dem Deckel wird nur das Freie gekauft"), und mit dem Befehl waere
        // sie verschwunden. Am Schild heisst sie: Der Sneak-Klick nimmt so viele Stufen,
        // wie noch frei sind - nicht mehr und nicht weniger.
        echteKasse.eroeffne(KUNDE);
        echteKasse.gutschreiben(KUNDE, 8L, Buchungsgrund.BUNDESMARKT_VERKAUF);
        ablage.schreibe(bundstand());
        hand.setzeBestand(999, 999, 999, 999);

        var ausgang = handel(stand(Shopwerte.VORGABE, 10L))
                .handle(KUNDE, WELT, 60, 71, 6, Handelsart.KUNDE_VERKAUFT, true);

        assertEquals(Shophandel.Bescheid.GEHANDELT, ausgang.bescheid());
        assertEquals(2L, ausgang.handel().preis(),
                "acht von zehn Mark sind verbraucht - also zwei Stufen zu einer Mark");
        assertEquals(10, ausgang.handel().stueck(), "zwei Stufen sind zehn Stueck");
    }

    @Test
    @DisplayName("OHNE WARE sagt der Stand das - und nicht, der Deckel sei erreicht")
    void ohneWareNichtDerDeckel() {
        // BEFUND DER EINSPRITZUNG B10 vom 10.11.2026: Die REIHENFOLGE von Deckel und
        // gewoehnlichem Mangel hielt keine Probe. Vertauscht liest ein Spieler ohne Ware
        // "heute ist genug verkauft" - und geht nach Hause, obwohl ihm nur die Ware
        // fehlt. Der Deckel ist dabei gar nicht angetastet.
        //
        // Erreichbar ist der Unterschied nur so: Der Kunde hat NICHTS, und der Deckel
        // ist OFFEN. Dann faengt stufenImDeckel(..., 0) ohnehin 0 Stufen, und wer die
        // Reihenfolge tauscht, bekommt den Deckelbescheid fuer einen leeren Rucksack.
        hand.setzeBestand(999, 999, 0, 999);
        ablage.schreibe(bundstand());
        echteKasse.eroeffne(KUNDE);

        var ausgang = handel(stand(Shopwerte.VORGABE, 10L))
                .handle(KUNDE, WELT, 60, 71, 6, Handelsart.KUNDE_VERKAUFT, false);

        assertEquals(Shophandel.Bescheid.KEINE_WARE, ausgang.bescheid(),
                "was fehlt, ist die WARE - der Deckel hat damit nichts zu tun");
        assertEquals(List.of(), schritte);
    }

    @Test
    @DisplayName("Am Deckel wird nicht gehandelt - und nichts bewegt")
    void amDeckelWirdNichtGehandelt() {
        echteKasse.eroeffne(KUNDE);
        echteKasse.gutschreiben(KUNDE, 10L, Buchungsgrund.BUNDESMARKT_VERKAUF);
        ablage.schreibe(bundstand());
        hand.setzeBestand(999, 999, 999, 999);
        schritte.clear();

        var ausgang = handel(stand(Shopwerte.VORGABE, 10L))
                .handle(KUNDE, WELT, 60, 71, 6, Handelsart.KUNDE_VERKAUFT, false);

        assertEquals(Shophandel.Bescheid.BUND_DECKEL, ausgang.bescheid());
        assertEquals(List.of(), schritte, "eine Absage bewegt nichts");
    }

    @Test
    @DisplayName("Am Deckel KAUFT der Kunde weiter - gedeckelt ist nur der Ankauf")
    void amDeckelWirdWeiterGekauft() {
        // BEFUND DER EINSPRITZUNG T14 vom 10.11.2026: Setzt man in Shophandel
        // "bundankauf = shop.demBund()" statt Bundankauf.istAnkaufstand(shop, art), wird
        // auch der VERKAUFENDE Bundstand gedeckelt - und keine Probe wurde rot.
        // BundankaufTest prueft die Frage selbst (eine Senke braucht keinen Deckel), aber
        // niemand prueefte, dass der Handel sie auch so STELLT. Die Folge im Spiel: Wer
        // vormittags seine Tagesmenge an den Bund verkauft hat, kann nachmittags am
        // Bundladen nichts mehr KAUFEN - obwohl dort Geld zum Bund fliesst und nicht von
        // ihm weg.
        echteKasse.eroeffne(KUNDE);
        echteKasse.gutschreiben(KUNDE, 10L, Buchungsgrund.BUNDESMARKT_VERKAUF);
        ablage.schreibe(bundladen());
        hand.setzeBestand(999, 999, 999, 999);

        var ausgang = handel(stand(Shopwerte.VORGABE, 10L))
                .handle(KUNDE, WELT, 60, 71, 6, Handelsart.KUNDE_KAUFT, false);

        assertEquals(Shophandel.Bescheid.GEHANDELT, ausgang.bescheid(),
                "der Tagesdeckel des Bundesmarkts gilt nur, wenn der Kunde VERKAUFT");
        assertEquals(100L, ausgang.handel().preis(), "der Schildpreis, ungedeckelt");
    }

    @Test
    @DisplayName("Der Deckel zaehlt NUR Verkaeufe - Arbeit sperrt den Markt nicht")
    void deckelZaehltNurVerkaeufe() {
        // Die zweite Zusage, die vom Befehl herueberkommt. Mit einem gemeinsamen Grund
        // waere jede der zwei Quellen durch die andere zu verschliessen: Wer vormittags
        // arbeitet, koennte nachmittags nicht verkaufen.
        echteKasse.eroeffne(KUNDE);
        echteKasse.gutschreiben(KUNDE, 10_000L, Buchungsgrund.BERUF_VERDIENST);
        ablage.schreibe(bundstand());
        hand.setzeBestand(999, 999, 999, 999);

        var ausgang = handel(stand(Shopwerte.VORGABE, 10L))
                .handle(KUNDE, WELT, 60, 71, 6, Handelsart.KUNDE_VERKAUFT, false);

        assertEquals(Shophandel.Bescheid.GEHANDELT, ausgang.bescheid());
    }

    @Test
    @DisplayName("Ein SPIELERSHOP bleibt unberuehrt - er zahlt seinen Schildpreis")
    void spielershopBleibtUnberuehrt() {
        // Die Gegenprobe zur Tafel, und sie ist mehr als Vorsicht: Stein steht im
        // Sortiment des Bundes, und Annas Laden handelt Stein. Wuerde die Tafel auch
        // hier gelten, zahlte ein Spielershop ploetzlich den Ankaufpreis des Bundes -
        // und sein Besitzer haette einen Preis, den er nie eingestellt hat.
        var ausgang = handle(spielershop(), Handelsart.KUNDE_VERKAUFT, false);

        assertEquals(Shophandel.Bescheid.GEHANDELT, ausgang.bescheid());
        assertEquals(List.of(
                "abbuchen BESITZER 80 SHOP_GEKAUFT -> true",
                "nehme KUNDE 16",
                "lege KISTE 16",
                "gutschreiben KUNDE 80 SHOP_VERKAUFT"), schritte,
                "die 80 vom Schild, und SHOP_VERKAUFT - kein Deckel, keine Tafel");
    }

    @Test
    @DisplayName("Eine geaenderte Gebuehr wirkt ohne Neustart - der Halter, kein Abbild")
    void gebuehrWirktNachDemNeuladen() {
        // DER FUND VOM 10.11.2026, als eigene Probe. Shophandel hielt ein Shopwerte als
        // FELD - also die Zahlen des Serverstarts. Ein /freimarken neuladen baut
        // absichtlich kein Objekt neu, und darum wirkte eine geaenderte Marktgebuehr
        // nie, waehrend der Befehl "uebernommen" meldete.
        Wirtschaftsstand halter = stand(Shopwerte.VORGABE, 20L);
        ablage.schreibe(spielershop());
        neuerStand();

        handel(halter).handle(KUNDE, WELT, 60, 71, 6, Handelsart.KUNDE_KAUFT, false);
        assertEquals(List.of(), schritteMit("SHOP_GEBUEHR"), "ab Werk keine Gebuehr");

        halter.uebernimm(halter.werte().mitHandel(
                halter.werte().handel().mitSpielerKauf(10)));
        schritte.clear();
        handel(halter).handle(KUNDE, WELT, 60, 71, 6, Handelsart.KUNDE_KAUFT, false);

        assertEquals(List.of("abbuchen BESITZER 10 SHOP_GEBUEHR -> true"),
                schritteMit("SHOP_GEBUEHR"),
                "zehn Prozent von 100 Mark - ohne Neustart, am selben Handel");
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

        var kauf = handle(bundladen(), Handelsart.KUNDE_KAUFT, false, werte);
        assertEquals(0L, kauf.handel().gebuehr(), "am Bundshop kauft man ohne Gebuehr");

        // Und der Ankauf mit einem TEUREN Stand: 100 Mark je Handel, und 23 Prozent
        // davon sind 23. An einem Stand zu einer Mark waere die Gebuehr abgerundet 0 -
        // und diese Probe sagte nichts ueber die Lage, die sie prueft.
        schritte.clear();
        ablage.schreibe(bundstand(100L));
        neuerStand();
        var verkauf = handel(stand(werte)).handle(KUNDE, WELT, 60, 71, 6,
                Handelsart.KUNDE_VERKAUFT, false);
        assertEquals(23L, verkauf.handel().gebuehr(), "23 Prozent von 100 Mark sind 23");
        assertEquals(List.of(
                "nehme KUNDE 5",
                "gutschreiben KUNDE 100 BUNDESMARKT_VERKAUF",
                "abbuchen KUNDE 23 SHOP_GEBUEHR -> true"), schritte,
                "die Gebuehr bleibt SHOP_GEBUEHR - sie ist eine Ausgabe des Kunden und"
                        + " darum keine zweite Quelle. Gedeckelt wird, was gutgeschrieben"
                        + " wird.");
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

        var ausgang = handle(bundstand(), Handelsart.KUNDE_VERKAUFT, false);

        assertEquals(Shophandel.Bescheid.GESTOERT, ausgang.bescheid());
        assertEquals(List.of("nehme KUNDE 5"), schritte);
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
                    null, 0L);
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

    /**
     * Ein BUNDLADEN: Der Bund verkauft, der Kunde kauft. Nur ein B-Preis.
     *
     * <p><b>Zwei Bundshops und nicht einer</b> - seit dem 10.11.2026 traegt ein Schild
     * des Bundes genau EINE Richtung. Vorher stand hier ein einziger mit beiden Preisen,
     * und das ist genau die Gestalt, die es nicht mehr geben darf: Was der Bund ANKAUFT,
     * steht in der Tafel, was er VERKAUFT, auf dem Schild - und sobald die Tafel ueber
     * den Schildpreis steigt, waere so ein Stand ein Rundlauf.
     */
    private Kistenshop bundladen() {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, null, 16, 100L, 0L, ware(),
                Set.of());
    }

    /**
     * Ein ANKAUFSTAND des Bundes: der Bundesmarkt am Schild. Nur ein S-Preis.
     *
     * <p>Menge FUENF, und das ist gerechnet: Das Sortiment dieser Proben nennt Stein mit
     * 20 Pfennigen, und fuenf Stueck sind damit genau eine Mark. Bei 16 - der Menge der
     * uebrigen Shops hier - waeren es 3,20 Mark, und ein solcher Stand entsteht gar
     * nicht: Bei FESTER Menge verschwaende jeder Handel dieselben 20 Pfennige.
     *
     * <p>Der Preis in der Zeile ist dieselbe 1 - er MUSS es nicht sein, denn gehandelt
     * wird zur Tafel. Dass er stimmt, ist der Normalfall nach dem Anlegen; die Proben
     * zur Abweichung setzen ihn ausdruecklich anders.
     */
    private Kistenshop bundstand() {
        return bundstand(1L);
    }

    /**
     * Ein Ankaufstand mit diesem Preis je Handel.
     *
     * <p>Seit dem 10.11.2026 ist dieser Preis die WAHRHEIT und nicht eine Anzeige: Die
     * Tafel {@code bundesmarkt.ankauf} ist gefallen, und ein Stand des Bundes zahlt, was
     * auf seinem Schild steht - wie jeder Laden.
     */
    private Kistenshop bundstand(long preis) {
        return new Kistenshop(WELT, 60, 71, 6, 61, 71, 6, null, 5, 0L, preis,
                ware(), Set.of());
    }

    private static Warenprobe ware() {
        return new Warenprobe("STONE", "inhalt");
    }

    private Shopbuch buch() {
        return buch(stand(Shopwerte.VORGABE));
    }

    private Shopbuch buch(Wirtschaftsstand wirtschaft) {
        return new Shopbuch(ablage, new Leser(), (wer, welt, x, y, z) -> true,
                wirtschaft, Logger.getAnonymousLogger());
    }

    /** Ein Halter mit diesen Handelszahlen und dem Tagesdeckel ab Werk. */
    private Wirtschaftsstand stand(Shopwerte werte) {
        return stand(werte, Marktwerte.VORGABE_TAGESDECKEL);
    }

    /**
     * Derselbe Halter mit eigenem Tagesdeckel.
     *
     * <p>Der Deckel ab Werk ist 5.000 Mark, und ein Stand zu einer Mark je Handel kaeme
     * nie in seine Naehe: Ein Sneak-Klick bewegt hoechstens einen Stapel. Die
     * Deckelproben setzen ihn darum selbst auf zehn - die Probe setzt ihre Zahlen, nicht
     * die Vorgabe.
     *
     * <p><b>Ein SORTIMENT setzt hier nichts mehr</b>: Die Tafel {@code bundesmarkt.ankauf}
     * ist am 10.11.2026 gefallen, und der Preis eines Standes steht in seiner Zeile.
     */
    private Wirtschaftsstand stand(Shopwerte werte, long deckel) {
        return new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitHandel(werte)
                .mitMarkt(Marktwerte.VORGABE.mitDeckel(deckel)));
    }

    private Shophandel handel(Shopwerte werte) {
        return handel(stand(werte));
    }

    private Shophandel handel(Wirtschaftsstand wirtschaft) {
        return new Shophandel(buch(wirtschaft), hand, kasse, wirtschaft, UHR,
                protokollMit(protokollzeilen));
    }

    /** Ein Protokoll, das in eine Liste schreibt - fuer die Zeilen des Betreibers. */
    private static Logger protokollMit(List<String> hinein) {
        Logger eigener = Logger.getLogger("Shophandelprobe-" + UUID.randomUUID());
        eigener.setUseParentHandlers(false);
        eigener.addHandler(new Handler() {
            @Override
            public void publish(LogRecord satz) {
                hinein.add(satz.getLevel() + ": " + satz.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        eigener.setLevel(Level.ALL);
        return eigener;
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
        Wirtschaftsstand eigenerStand = stand(Shopwerte.VORGABE);
        var handel = new Shophandel(
                new Shopbuch(eigeneAblage, new Leser(),
                        (wer, welt, x, y, z) -> true, eigenerStand,
                        Logger.getAnonymousLogger()),
                eigeneHand, eigeneKasse, eigenerStand, UHR,
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
