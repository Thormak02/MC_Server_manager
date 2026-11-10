package de.freimarken.paper;

import de.freimarken.kern.Handelsart;
import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopregel;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Die Zeilen der Kistenshops - reine Rechnung, kein Bukkit, kein Senden.
 *
 * <p><b>Das ist DIE NAHT dieses Bereichs</b>, wie {@link Miettext} fuer die Ladenmiete
 * und {@link Schutztext} fuer den Bauschutz: {@link Shopwache} holt sich hier ihre Zeilen
 * und schickt sie nur hinaus. Dadurch ist jedes Wort, jede Farbe, jede Verzweigung und
 * jede Zahl ohne Server pruefbar - und das ist bei diesem Bereich mehr als eine
 * Gewohnheit, denn die Wache selbst haengt an sieben Bukkit-Ereignissen und ist ohne
 * Server gar nicht zu starten.
 *
 * <p>Kein Wort steht hier: Der Wortlaut kommt aus {@code sprache-vorlage.yml} ueber
 * {@link ShopSatz}. Die WAHL des Satzes steht auch nicht hier, sondern in
 * {@link Sprachschluessel#shopAnlage} - derselbe Schnitt wie bei den Parzellenbescheiden,
 * und aus demselben Grund: Eine Probe soll die Zuordnung aufrufen koennen, ohne durch
 * einen Sendeort zu gehen.
 *
 * <p><b>Was hier bleibt, ist der AUFBAU der Antwort</b> - und der ist bei einem gelungenen
 * Shop mehr als eine Zeile: Was er handelt, dann je Richtung der Preis, und beim Bundshop
 * eine Zeile ueber den unbegrenzten Vorrat. Zwei bis vier Zeilen also, und welche, haengt
 * am Shop und nicht am Wortlaut.
 *
 * <p><b>Absagen gehen ueber {@link Sprache#bau}, Handbalken ueber
 * {@link Sprache#eineZeile}</b>, dieselbe Regel wie in {@link Miettext}: Eine Absage darf
 * einen Hinweis in einer zweiten Zeile tragen, und mehrere der Schildabsagen leben davon
 * ("schreibe eine Zahl von 1 bis 3456"). Ein Handbalken nimmt dagegen genau ein
 * Component, und eine zweite Zeile verschwaende sich stumm.
 */
final class Shoptext {

    private Shoptext() {
    }

    /**
     * Die Antwort auf ein beschriebenes Schild - zwei bis vier Zeilen oder eine Absage.
     *
     * @param getippteWare die vierte Schildzeile, wie der Spieler sie getippt hat. Nur
     *                     {@code SHOP_WARE_UNBEKANNT} setzt sie ein, und zwar absichtlich
     *                     unveraendert: Oft ist es ein Leerzeichen zu viel, und das sieht
     *                     nur, wer seine eigene Eingabe liest
     */
    static List<Component> anlageZeilen(Sprache sprache, Shopbuch.Anlage anlage,
            String getippteWare) {
        if (anlage.bescheid() == Shopbuch.Bescheid.ANGELEGT) {
            return zusageZeilen(sprache, anlage.shop());
        }
        // Die 1L ist die Anzahl fuer Art.GEZAEHLT, und keiner dieser Saetze ist
        // gezaehlt - Sprache.bau verlangt sie trotzdem, weil es die EINE Baustelle fuer
        // alle Arten ist. Dieselbe 1L steht in Miettext.absageZeilen.
        return sprache.bau(Sprachschluessel.shopAnlage(anlage.bescheid(),
                anlage.fehler()), 1L, absagewerte(anlage, getippteWare));
    }

    /**
     * Die Zeilen eines gelungenen Shops.
     *
     * <p>Die Preiszeile einer Richtung steht nur da, wenn es diese Richtung GIBT. Ein
     * gemeinsamer Satz mit beiden Zahlen muesste fuer den Nurverkaufsfall eine Null
     * nennen - und eine Null als Preis ist genau das, was {@code Shopschild} als
     * {@code PREIS_BEIDE_NULL} abweist. Sie darf nirgends als gueltiger Preis zu lesen
     * sein.
     *
     * <p>Die Bundzeile kommt ZULETZT und nicht statt des ersten Satzes: Ein Bundshop ist
     * ein gewoehnlicher Shop mit einer Eigenschaft mehr, und der Leser soll zuerst
     * dasselbe lesen wie bei jedem anderen.
     */
    private static List<Component> zusageZeilen(Sprache sprache, Kistenshop shop) {
        List<Component> zeilen = new ArrayList<>(4);
        zeilen.add(sprache.eineZeile(ShopSatz.SHOP_ANGELEGT, Einsatz
                .von("menge", shop.menge())
                .mit("ware", shop.ware().name())));
        // ZWEI BLICKWINKEL IN DREI ZEILEN, und genau hier war der Fehler: Die
        // Satznamen sind aus Kundensicht (SHOP_PREIS_KAUF heisst "Kunden kaufen"), die
        // Methoden aus Shopsicht (verkauftWare heisst "der Shop verkauft"). Also gehoert
        // verkauftWare zur KAUFzeile. Vertauscht bekam ein Shop, der nur ankauft, die
        // Zeile "Kunden kaufen fuer 0 Mark" - und eine 0 als Preis ist genau das, was
        // Shopschild als PREIS_BEIDE_NULL abweist. Gefunden hat es ShoptextTest; die
        // Bildtafel sah es nicht, denn beide Saetze sind fuer sich richtig.
        if (shop.verkauftWare()) {
            zeilen.add(sprache.eineZeile(ShopSatz.SHOP_PREIS_KAUF,
                    Einsatz.von("preis", sprache.betrag(shop.kaufpreis()))));
        }
        if (shop.kauftWare()) {
            zeilen.add(sprache.eineZeile(ShopSatz.SHOP_PREIS_VERKAUF,
                    Einsatz.von("preis", sprache.betrag(shop.verkaufpreis()))));
        }
        if (shop.demBund()) {
            zeilen.add(sprache.eineZeile(ShopSatz.SHOP_ANGELEGT_BUND, Einsatz.keine()));
        }
        return List.copyOf(zeilen);
    }

    /**
     * Alle vier moeglichen Werte einer Absage - auch die, die dieser Satz nicht braucht.
     *
     * <p><b>Ueberzaehlige Werte sind unschaedlich, fehlende sind eine sichtbare
     * Klammer:</b> {@code Sprache.ersetze} klagt nur ueber einen Platzhalter, fuer den
     * kein Wert da ist; einen Wert, den kein Platzhalter abholt, sieht es nie. Die
     * Alternative waere eine zweite Verzweigung ueber dieselben neunzehn Faelle, nur um
     * je zwei Werte zu sparen - und zwei Verzweigungen ueber dieselbe Frage laufen
     * irgendwann auseinander. Welche Werte ein Satz WIRKLICH einsetzen darf, steht in
     * {@code ShopSatz.platzhalter()} und wird beim Laden gegen die Vorlage geprueft.
     *
     * <p>Die beiden Grenzen kommen aus {@link Kistenshop} und nicht aus dem Wortlaut: Ein
     * Betreiber, der sie aendert, aendert eine Zahl im Programm und nicht zwei Worte in
     * einer Datei.
     */
    private static Einsatz absagewerte(Shopbuch.Anlage anlage, String getippteWare) {
        return Einsatz.von("zeile", anlage.zeile())
                .mit("hoechstmenge", Kistenshop.GROESSTE_MENGE)
                .mit("hoechstpreis", Kistenshop.HOECHSTER_PREIS)
                .mit("getippt", getippteWare == null ? "" : getippteWare)
                // Die kleinste Menge, mit der die Rechnung eines Ankaufstandes aufgeht -
                // 0 bei jeder anderen Absage, und dort setzt sie kein Satz ein.
                .mit("vorschlag", anlage.bundmenge());
    }

    /**
     * Die Antwort auf ein abgebautes Schild - eine Zeile, oder keine.
     *
     * <p><b>{@code KEIN_SHOP} schweigt</b>, und das ist der Grund, warum diese Methode
     * eine Liste gibt: Dieselbe Stelle wird von zwei Seiten gemeldet - vom Abbauen des
     * Schildes und vom Abbauen der Kiste -, und die zweite Meldung soll nicht sagen, dass
     * hier nichts war. Siehe {@code Shopbuch.baueAb}.
     */
    static List<Component> abbauZeilen(Sprache sprache, Shopbuch.Bescheid bescheid,
            Kistenshop shop) {
        return switch (bescheid) {
            case ABGEBAUT -> List.of(sprache.eineZeile(ShopSatz.SHOP_ABGEBAUT,
                    Einsatz.von("ware", shop == null ? "" : shop.ware().name())));
            case KEIN_SHOP -> List.of();
            case GESTOERT -> sprache.bau(ShopSatz.SHOP_GESTOERT, 1L,
                    Einsatz.keine());
            default -> throw new IllegalArgumentException(bescheid + " kommt nicht vom"
                    + " Abbauen - Shopbuch.baueAb gibt nur ABGEBAUT, KEIN_SHOP oder"
                    + " GESTOERT. Wer hier mehr erwartet, hat die Taten verwechselt.");
        };
    }

    /**
     * Der Handbalken zu einem geschuetzten Shopblock.
     *
     * <p><b>EINE Stelle fuer alle vier Absagen des Schutzes</b>, und die Wahl faellt an
     * zwei Fragen: Was wurde angefasst (Schild, Kiste, Traeger) und wem gehoert der Shop.
     * Getrennte Methoden wie in {@link Schutztext} waeren hier das falsche Maass - dort
     * entscheidet jede Absage etwas anderes, hier ist es dieselbe Entscheidung mit zwei
     * Achsen, und vier einzelne Methoden liessen den Aufrufer die Kreuzung bilden.
     *
     * <p><b>Der Name kommt FERTIG herein und wird hier nicht geholt</b>, und das ist
     * gemessen und keine Geschmacksfrage: Ein {@code Spielername.von} an dieser Stelle
     * griff nach {@code Bukkit.server}, und jede Probe dieser Naht starb im
     * {@code NullPointerException}, bevor sie ein Zeichen vergleichen konnte. Genau
     * dieselbe Begruendung steht am Kopf von {@link Schutztext} - die Naht ist nur ohne
     * Server pruefbar, solange sie keinen braucht.
     *
     * <p>Er geht als BLATT in den Satz: Ein Farbtag darin stuende dem Leser woertlich im
     * Handbalken. Dieselbe Begruendung wie bei
     * {@code Schutztext.fremdeParzelleBalken}.
     *
     * @param teil         was angefasst wurde
     * @param shop         der Shop, der schuetzt - bei {@link Teil#TRAEGER} ohne
     *                     Bedeutung, denn der Traegersatz nennt keinen Menschen.
     *                     {@code null} heisst "geschuetzt, aber niemand nennbar"
     *                     und gibt {@link ShopSatz#SHOP_SCHUTZ_UNLESBAR}; das ist
     *                     kein Versehen des Aufrufers, sondern die Lage bei stummer
     *                     Ablage
     * @param besitzername der Name, den der Satz einsetzt. Darf bei einem Bundshop und
     *                     beim Traeger beliebig sein - diese zwei Saetze nennen keinen
     */
    static Component schutzBalken(Sprache sprache, Teil teil, Kistenshop shop,
            String besitzername) {
        if (teil == Teil.TRAEGER) {
            return sprache.eineZeile(ShopSatz.SHOP_SCHUTZ_TRAEGER, Einsatz.keine());
        }
        if (shop == null) {
            // Geschuetzt, aber niemand nennbar - die Ablage hat geschwiegen. Siehe
            // Shopwache.AnDerKiste.gestoert; der Satz erklaert, warum er eigen ist.
            return sprache.eineZeile(ShopSatz.SHOP_SCHUTZ_UNLESBAR, Einsatz.keine());
        }
        if (shop.demBund()) {
            // Ein Bundshop hat keinen Besitzer, den man nennen koennte - darum ein
            // eigener Satz und kein eingesetztes Wort "der Bund". Siehe ShopSatz.
            return sprache.eineZeile(ShopSatz.SHOP_SCHUTZ_BUND, Einsatz.keine());
        }
        ShopSatz satz = teil == Teil.SCHILD
                ? ShopSatz.SHOP_SCHUTZ_SCHILD
                : ShopSatz.SHOP_SCHUTZ_KISTE;
        return sprache.eineZeile(satz, Einsatz.von("besitzer", besitzername));
    }

    /**
     * Der Handbalken zu einem Handelsklick - eine Zeile, oder keine.
     *
     * <p><b>Eine LISTE, obwohl ein Handbalken nur eine Zeile nimmt</b>, und zwar aus
     * demselben Grund wie bei {@link #abbauZeilen}: Ein Klick, an dem kein Shop
     * haengt, muss SCHWEIGEN - und das ist eine Entscheidung, die hier geprueft werden
     * soll und nicht im Ereigniszweig. Ein Spieler, der auf ein Wegweiserschild
     * klickt, bekaeme sonst eine Absage dafuer, dass dort kein Laden ist.
     *
     * <p>Alle Werte werden IMMER gesetzt, auch die, die dieser Satz nicht braucht -
     * dieselbe Entscheidung und derselbe Grund wie bei {@link #absagewerte}:
     * Ueberzaehlige Werte sieht {@code Sprache.ersetze} nie, fehlende stehen dem Leser
     * als Klammer im Satz. Welche ein Satz einsetzen DARF, steht in
     * {@code ShopSatz.platzhalter()} und wird beim Laden gegen die Vorlage geprueft.
     *
     * <p>{@code sprache.betrag} und nicht die nackte Zahl: Jede Geldangabe des
     * Projekts geht durch dieselbe Stelle, damit "1.000 Mark" ueberall gleich
     * aussieht.
     */
    static List<Component> handelBalken(Sprache sprache,
            Shophandel.Ausgang ausgang) {
        if (ausgang.bescheid() == Shophandel.Bescheid.KEIN_SHOP) {
            return List.of();
        }
        // Am eigenen Shop nur beim RECHTSklick ein Hinweis. Der Linksklick des
        // Besitzers ist sein Abbau - eine Meldung daneben waere eine zweite Antwort
        // auf eine Tat, die gerade stattfindet, und der Spieler las beides gleichzeitig.
        if (ausgang.bescheid() == Shophandel.Bescheid.EIGENER_SHOP
                && ausgang.art() == Handelsart.KUNDE_VERKAUFT) {
            return List.of();
        }
        Shopregel.Handel handel = ausgang.handel();
        boolean mitGebuehr = handel != null && handel.kostetGebuehr();
        return List.of(sprache.eineZeile(
                Sprachschluessel.shopHandel(ausgang.bescheid(), ausgang.art(),
                        mitGebuehr),
                handelswerte(sprache, ausgang, handel)));
    }

    /**
     * Die fuenf Werte eines Handelsbalkens.
     *
     * <p><b>{@code menge} ist nicht immer dasselbe</b>, und das ist der einzige Wert
     * hier, der verzweigt: Bei einem gelungenen Handel ist es, was WIRKLICH bewegt
     * wurde ({@code handel.stueck()}, beim Sneak-Klick ein ganzer Stapel); bei einer
     * Absage ist es, was der Shop je Handel VERLANGT ({@code shop.menge()}). Ein
     * Kunde, der "du brauchst 16x STONE" liest, soll die Zahl des Shops sehen und
     * nicht die, die er gerade nicht zusammenbekommen hat.
     *
     * <p>Die drei Geldzahlen stehen alle drei da, auch wenn nur der Verkaufssatz mit
     * Gebuehr sie alle nennt. Bei einer Absage ist {@code handel} {@code null}, und dann
     * ist der Preis der einer EINEN Stufe - das ist die Zahl, die der Satz
     * {@code kein-geld} braucht: Was dem Kunden fehlt, ist der Preis fuer einen
     * Handel, nicht fuer den Stapel, den er nicht bekommt.
     */
    private static Einsatz handelswerte(Sprache sprache, Shophandel.Ausgang ausgang,
            Shopregel.Handel handel) {
        Kistenshop shop = ausgang.shop();
        // DER PREIS KOMMT AUS DEM AUSGANG und wird hier nicht mehr aus dem Shop geholt
        // [10.11.2026]. An einem Ankaufstand des Bundes steht er in
        // bundesmarkt.ankauf und nicht in der Shopzeile - wer ihn hier aus dem Shop
        // holte, nennte dem Kunden die Zahl des Schildes, also moeglicherweise eine
        // alte. Die Entscheidung, WELCHE Zahl gilt, faellt an einer Stelle
        // (Shophandel), und diese Naht setzt sie nur ein.
        long preis = handel != null ? handel.preis() : ausgang.stufenpreis();
        return Einsatz
                .von("menge", handel != null ? handel.stueck() : shop.menge())
                .mit("ware", shop.ware().name())
                .mit("preis", sprache.betrag(preis))
                .mit("gebuehr", sprache.betrag(handel == null ? 0L
                        : handel.gebuehr()))
                .mit("erloes", sprache.betrag(handel == null ? 0L
                        : handel.erloes()));
    }

    /**
     * Welcher Teil eines Shops angefasst wurde.
     *
     * <p>Drei und nicht zwei: Der Traeger ist die ueberraschendste der Absagen, denn er
     * sieht nach nichts aus - ein Stein unter einem Stehschild. Ohne ihn faellt das
     * Schild, und dann steht ein Shop in der Datenbank, dessen Schild es nicht mehr gibt.
     */
    enum Teil {
        SCHILD,
        KISTE,
        TRAEGER
    }
}
