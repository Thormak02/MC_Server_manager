package de.freimarken.paper;

import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Bundankauf;
import de.freimarken.kern.Handelsart;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopregel;
import de.freimarken.kern.Wirtschaftsstand;
import java.time.Clock;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Der Handel am Kistenshop - Geld und Ware wechseln die Seiten.
 *
 * <p>Kein Bukkit, derselbe Schnitt wie bei {@link Shopbuch} und {@link Mietbuch}. Das
 * Spiel kommt ueber EINE Schnittstelle herein: {@link Warenhand} zaehlt und bewegt
 * Gegenstaende und entscheidet nichts. Jede Entscheidung - welcher Bescheid, welche
 * Reihenfolge, welche Buchung, was bei einem Rest - steht hier und ist ohne Server
 * pruefbar.
 *
 * <h2>Die Reihenfolge, und zwar in BEIDEN Richtungen dieselbe</h2>
 *
 * <p>Die Hausregel steht im Kopf von {@link Mietbuch}: "Die Reihenfolge der Schritte ist
 * in JEDER Tat die, die kein Geld erzeugen kann." Beim Mieten fliesst Geld nur in eine
 * Richtung, hier in beide gleichzeitig - also braucht sie eine Fassung, die beide deckt:
 *
 * <ol>
 *   <li><b>Der ZAHLER wird abgebucht</b> ({@link Buchungsgrund#SHOP_GEKAUFT}). Ein
 *       {@code false} ist die saubere Absage: Dann ist nichts geschehen, auch keine Zeile
 *       im Buch.</li>
 *   <li><b>Die Ware wird von der Geberseite GENOMMEN.</b></li>
 *   <li><b>Die Ware wird auf die Nehmerseite GELEGT.</b></li>
 *   <li><b>Der EMPFAENGER bekommt den vollen Preis</b>
 *       ({@link Buchungsgrund#SHOP_VERKAUFT}).</li>
 *   <li><b>Der Staat nimmt seinen Teil davon</b> ({@link Buchungsgrund#SHOP_GEBUEHR}) -
 *       nur, wenn die gerechnete Gebuehr ueber 0 liegt.</li>
 * </ol>
 *
 * <p>Beim KAUF ist der Zahler der Kunde und die Geberseite die Kiste, beim VERKAUF genau
 * umgekehrt. Dass es trotzdem EINE Abfolge ist und nicht zwei, ist der Grund fuer
 * {@link Seite}: Zwei Abfolgen waeren zwei Gelegenheiten, eine Zeile zu vergessen.
 *
 * <p><b>Warum der Zahler zuerst kommt und nicht die Ware:</b> Weil nur das Abbuchen die
 * Deckung prueft, und weil es ganz oder gar nicht geschieht. Faengt man mit der Ware an,
 * hat der Kunde sie einen Augenblick in der Hand, ohne gezahlt zu haben - und am Bundshop
 * kommt sie aus dem Nichts, dort waere es wirklich erzeugte Ware. Umgekehrt ist das
 * schlimmste Fenster eine Zahlung ohne Gegenleistung, und die hat eine Buchung, die sie
 * zurueckdreht: {@link Buchungsgrund#SHOP_ERSTATTET}.
 *
 * <p><b>Die Schritte 2 und 3 koennen nach einer genauen Platzrechnung nicht scheitern</b>
 * - siehe {@code Shopregel.platzInStueck}, das angefangene Stapel mitzaehlt und die
 * wirkliche Stapelgroesse der Ware benutzt. Dass sie trotzdem geprueft werden, ist der
 * Unterschied zwischen "kann nicht" und "darf nicht": Was hier schiefgeht, ist ein
 * Programm- oder Weltfehler, und dann wird zurueckgelegt, erstattet und protokolliert -
 * nie ein halber Handel. Einen Teilhandel gibt es nicht ({@link Shopregel}: ChestShop PR
 * #522).
 *
 * <p><b>Alles in EINEM Ereignis, kein spaeterer Takt.</b> Zwischen dem Zaehlen und dem
 * Bewegen darf nichts liegen - sonst bestehen zwei Kunden am selben Schild beide die
 * Vorratspruefung, und der Shop verkauft zweimal, was einmal da war. Das Zurueckgeben in
 * Schritt 2 und 3 traegt aus demselben Grund: Die Plaetze, die eben frei wurden, laufen
 * im selben Takt niemandem zu.
 *
 * <h2>Der ANKAUF DES BUNDES ist seit dem 10.11.2026 derselbe Weg</h2>
 *
 * <p>Ein Bundshop, an den ein Kunde verkauft, IST der Bundesmarkt - und damit faellt
 * {@code /markt verkaufen} weg (Plan 7.1, entschieden 08.11.2026). ZWEI Dinge sind an
 * diesem Stand anders, und beide stehen in {@link Bundankauf} begruendet:
 *
 * <ul>
 *   <li><b>Der Tagesdeckel des Bundesmarkts gilt</b>, gezaehlt aus dem Buchungsbuch
 *       ({@link Markttag}). Das schliesst die benannte Luecke aus Plan 7.4: Ein Bundshop
 *       mit Verkaufspreis war eine Geldquelle ohne Deckel. OB gedeckelt wird,
 *       entscheidet der Betreiber - {@code bundesmarkt.tagesdeckel: 0} heisst
 *       nein.</li>
 *   <li><b>Gebucht wird {@link Buchungsgrund#BUNDESMARKT_VERKAUF}</b> und nicht
 *       {@code SHOP_VERKAUFT} - sonst zaehlt der Deckel seine eigenen Zeilen nicht
 *       mit, und er waere nach dem ersten Stand wieder offen.</li>
 * </ul>
 *
 * <p><b>Ein DRITTES stand hier einen halben Tag lang und ist wieder gegangen:</b> Der
 * Preis kam aus einer Tafel {@code bundesmarkt.ankauf}, und was dort nicht stand, kaufte
 * der Bund nicht. Die Tafel ist gefallen [entschieden 10.11.2026], weil sie ein zweites
 * Tor war: "es kann ja nur ge- und verkauft werden, wofuer es ein Schild gibt." Der Preis
 * steht damit wieder in der Shopzeile, wie bei jedem anderen Laden - und mit der Tafel
 * sind die Absagen "das kauft der Bund nicht", "die Menge geht nicht glatt auf" und "ein
 * Bundschild traegt eine Richtung" gegangen.
 *
 * <p>Die andere Richtung - ein Kunde KAUFT am Bundshop - war schon vorher unberuehrt:
 * Sie ist eine Senke und braucht keinen Deckel.
 *
 * <p><b>Der STAND und kein Schnappschuss</b> [Fund vom 10.11.2026]. Diese Klasse hielt
 * bis dahin ein {@code Shopwerte} als Feld, also die Zahlen des Starts. Ein
 * {@code /freimarken neuladen} baut absichtlich kein Objekt neu
 * ({@link Wirtschaftsstand}) - also wirkte eine geaenderte Marktgebuehr nie, waehrend der
 * Befehl "uebernommen" meldete. Jetzt haengt hier der Halter, und gefragt wird im
 * Augenblick des Handels.
 *
 * <p><b>Nichts hiervon wirft nach aussen.</b> Jede Tat antwortet mit einem Bescheid, auch
 * wenn die Datenbank schweigt - derselbe Grundsatz wie bei {@link Shopbuch}. Ein Wurf aus
 * einem Klick traefe jeden Spieler auf dem Server.
 */
final class Shophandel {

    /** Was aus einem Handelsklick geworden ist. */
    enum Bescheid {
        /** Gehandelt - {@link Ausgang#handel} nennt die Zahlen. */
        GEHANDELT,
        /** Hier haengt gar kein Shop. Darauf wird GESCHWIEGEN. */
        KEIN_SHOP,
        /** Dieser Shop verkauft nichts - er kauft nur an. */
        VERKAUFT_NICHT,
        /** Dieser Shop kauft nichts an - er verkauft nur. */
        KAUFT_NICHT_AN,
        /** Am eigenen Shop wird nicht gehandelt [entschieden 08.11.2026]. */
        EIGENER_SHOP,
        /** Die Warenprobe des Shops ist nicht mehr lesbar. Steht im Protokoll. */
        WARE_UNLESBAR,
        /** Die Shopkiste hat nicht genug - ausverkauft. */
        AUSVERKAUFT,
        /** Der Kunde hat nicht genug von der Ware. */
        KEINE_WARE,
        /** Im Rucksack des Kunden ist kein Platz. */
        KEIN_PLATZ,
        /** In der Shopkiste ist kein Platz. */
        KISTE_VOLL,
        /** Der Kunde kann nicht zahlen. */
        KEIN_GELD,
        /** Der Shopbesitzer kann nicht zahlen. */
        SHOP_PLEITE,
        /**
         * Dieser Ankaufstand steht auf einer Ware, die der Bund nicht mehr kauft.
         *
         * <p>Ein eigener Bescheid und nicht {@code KAUFT_NICHT_AN}: Jener heisst "dieses
         * Schild hat keinen Ankaufpreis", und der Kunde soll es mit der anderen
         * Maustaste versuchen. Hier steht ein Ankaufstand, der nicht mehr ankauft, weil
         * die Ware aus {@code bundesmarkt.ankauf} genommen wurde - mit der anderen
         * Maustaste wird es nicht besser.
         */
        BUND_DECKEL,
        // DREI WEITERE BESCHEIDE STANDEN HIER einen halben Tag lang:
        // BUND_KAUFT_NICHT, BUND_ZWEI_RICHTUNGEN und BUND_UNEBEN. Sie gehoerten zur
        // Tafel bundesmarkt.ankauf, und die ist am 10.11.2026 gefallen - der Preis steht
        // auf dem Schild. Ein Stand kann damit nicht mehr "aus dem Sortiment genommen"
        // werden, seine Menge geht immer glatt auf (ein Schildpreis IST eine ganze
        // Mark), und ein Rundlauf ist unmoeglich, weil der Satz Kistenshop beide Preise
        // selbst haelt.
        /** Etwas ist schiefgegangen. Steht im Protokoll. */
        GESTOERT
    }

    /**
     * Welche der zwei Seiten eines Handels.
     *
     * <p>Damit es EINE Abfolge gibt und nicht zwei: Beim Kauf gibt die Kiste und nimmt
     * der Kunde, beim Verkauf umgekehrt. Ohne diesen Typ stuende die Abfolge zweimal da,
     * und in einer der zwei Fassungen fehlte irgendwann eine Buchung.
     */
    enum Seite {
        /** Die Shopkiste. Bei einem Bundshop wird sie nie gefragt und nie bewegt. */
        KISTE,
        /** Der Rucksack des Kunden, der geklickt hat. */
        KUNDE
    }

    /**
     * Was eine Seite halten kann - FRISCH gezaehlt, nie gerechnet.
     *
     * @param vorrat wie viele STUECK der Ware dort liegen
     * @param platz  wie viele STUECK noch hineinpassen - angefangene Stapel mitgezaehlt,
     *               siehe {@code Shopregel.platzInStueck}
     */
    record Bestand(int vorrat, int platz) {
    }

    /**
     * Das Ergebnis eines Handelsklicks.
     *
     * <p>Die {@link Handelsart} steht MIT darin, obwohl der Aufrufer sie gerade
     * uebergeben hat. Der Grund ist die Antwort an den Spieler: Welcher Satz er liest,
     * haengt an der Richtung - "ausverkauft" und "du hast die Ware nicht" sind der
     * gleiche Mangel auf verschiedenen Seiten -, und die Zuordnung soll an EINER Stelle
     * stehen, nicht halb hier und halb beim Aufrufer.
     *
     * @param shop   der Shop am Schild, oder {@code null} bei {@link Bescheid#KEIN_SHOP}
     * @param handel bei {@link Bescheid#GEHANDELT} die Zahlen, sonst {@code null}
     * @param stufenpreis was EINE Stufe hier kostet oder einbringt - die Zahl, die eine
     *               Absage nennen muss ("dir fehlen 100 Mark"). Sie steht MIT darin, weil
     *               sie nicht mehr immer in der Shopzeile steht: An einem Ankaufstand des
     *               Bundes kommt sie aus {@code bundesmarkt.ankauf}
     *               ({@link Bundankauf}). Haette der Satzbau sie weiter selbst aus dem
     *               Shop geholt, nennte er am Stand die Zahl des Schildes - also die
     *               alte - und der Spieler bekaeme eine andere
     */
    record Ausgang(Bescheid bescheid, Handelsart art, Kistenshop shop,
                   Shopregel.Handel handel, long stufenpreis) {

        /**
         * Ob dieser Klick VERBRAUCHT ist - dann darf er den Block nicht mehr treffen.
         *
         * <p>Eine Frage am Ergebnis und kein Vergleich im Ereigniszweig, damit sie ohne
         * Server pruefbar ist. Was sie entscheidet, ist viel: Ein Linksklick ist in
         * Minecraft der ANFANG eines Abbaus, und ein Rechtsklick mit Blockware in der
         * Hand SETZT einen Block. Wo gehandelt oder abgesagt wurde, soll beides nicht
         * geschehen.
         *
         * <p><b>Zwei Faelle sind NICHT verbraucht, und sie sind es aus demselben
         * Grund:</b> Dort wurde gar nicht gehandelt, also soll der Klick bleiben, was
         * er war. Bei {@link Bescheid#KEIN_SHOP} haengt hier kein Laden - ein
         * Wegweiserschild soll sich weiter abbauen lassen. Bei
         * {@link Bescheid#EIGENER_SHOP} ist es der Besitzer, und sein Linksklick ist
         * sein Abbau: Verbrauchten wir ihn, waere sein eigenes Schild unzerbrechlich,
         * und das ist schlimmer als ein Shop, an dem man nicht selbst kauft.
         */
        boolean verbraucht() {
            return bescheid != Bescheid.KEIN_SHOP
                    && bescheid != Bescheid.EIGENER_SHOP;
        }
    }

    /**
     * Zaehlt und bewegt Gegenstaende - die eine Bukkit-Naht des Handels.
     *
     * <p><b>Nur Stellen, Kennungen und Zahlen</b>, wie bei {@code Shopbuch.Warenleser}
     * und {@code Abholbuch.Zusteller}: kein {@code ItemStack}, kein {@code Inventory},
     * kein {@code Player}. Das ist nicht Formsache - ein {@code ItemStack} laesst sich
     * ohne laufenden Server nicht einmal bauen, und dann waere der ganze Handel nur von
     * einer Quelltexttafel bewacht.
     *
     * <p><b>Keine Methode hier entscheidet etwas.</b> Sie zaehlen, sie bewegen, sie
     * melden, wie viel wirklich bewegt wurde. Was das bedeutet, steht in
     * {@link Shophandel}.
     */
    interface Warenhand {

        /**
         * Ob die Ware dieses Shops heute noch lesbar ist.
         *
         * <p>Eine eigene Frage VOR dem Zaehlen, und das ist dieselbe Form wie
         * {@code Abholbuch.Zusteller.lesbar}: Eine unlesbare Ware zaehlt sonst als
         * Vorrat 0, und der Shop meldete fuer immer "ausverkauft", obwohl die Kiste voll
         * ist. Der Besitzer erfuehre keinen Grund.
         *
         * <p>Unlesbar wird eine Ware, wenn ein Spielupdate die Form der Gegenstaende so
         * aendert, dass der gespeicherte Text nicht mehr zu deuten ist.
         */
        boolean lesbar(Kistenshop shop);

        /**
         * Wie viele Stueck dieser Ware auf EINEN Platz gehen - 1 bis 99.
         *
         * <p>Gemessen am Gegenstand und nicht geraten: Enderperle und Schneeball stapeln
         * zu 16, Werkzeug und jedes verzauberte Stueck zu 1, und seit 1.20.5 kann jeder
         * Gegenstand seine eigene Groesse tragen. Eine feste 64 machte aus einem
         * Sneak-Klick an einem Spitzhacken-Shop vierundsechzig Stufen.
         */
        int stapelgroesse(Kistenshop shop);

        /** Was auf dieser Seite liegt und hineinpasst - frisch gezaehlt. */
        Bestand bestand(Seite seite, UUID kunde, Kistenshop shop);

        /**
         * Nimmt Stueck dieser Ware von dieser Seite weg.
         *
         * @return wie viele WIRKLICH weggenommen wurden - weniger heisst, dass die Seite
         *         nicht so viel hatte, wie {@link #bestand} gemeldet hat
         */
        int nehme(Seite seite, UUID kunde, Kistenshop shop, int stueck);

        /**
         * Legt Stueck dieser Ware auf diese Seite.
         *
         * @return wie viele WIRKLICH angekommen sind
         */
        int lege(Seite seite, UUID kunde, Kistenshop shop, int stueck);
    }

    /** Ein Bundshop hat unbegrenzten Vorrat und unbegrenzten Platz. */
    private static final Bestand UNBEGRENZT =
            new Bestand(Kistenshop.GROESSTE_MENGE, Kistenshop.GROESSTE_MENGE);

    private final Shopbuch buch;
    private final Warenhand waren;
    private final Kasse kasse;
    private final Wirtschaftsstand wirtschaft;
    private final Clock uhr;
    private final Logger protokoll;

    Shophandel(Shopbuch buch, Warenhand waren, Kasse kasse, Wirtschaftsstand wirtschaft,
            Clock uhr, Logger protokoll) {
        if (wirtschaft == null || uhr == null) {
            throw new IllegalArgumentException("Ohne Wirtschaftsstand und Uhr gibt es"
                    + " keinen Handel: Die Gebuehren, die Ankauftafel und der"
                    + " Tagesdeckel werden im AUGENBLICK des Handels gefragt, und der"
                    + " Deckel braucht dazu den heutigen Tag.");
        }
        this.buch = buch;
        this.waren = waren;
        this.kasse = kasse;
        this.wirtschaft = wirtschaft;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Der Shop an diesem Schild, oder {@code null} - die Frage der Handelstuer.
     *
     * <p>Dieselbe Frage, die der Klick danach beantwortet bekommt, und darum dieselbe
     * Stelle: Die Tuer in {@code Schutzwache} fragt, OB hier ein Shop steht, der Handel
     * braucht WELCHER. Zwei Wege zu dieser Antwort liefen auseinander, und dann oeffnete
     * die Tuer an einer Stelle, an der nicht gehandelt wird.
     */
    Kistenshop amSchild(String welt, int x, int y, int z) {
        return buch.anDemSchild(welt, x, y, z);
    }

    /**
     * Handelt - die eine Tat dieses Bereichs.
     *
     * @param kunde        wer geklickt hat
     * @param art          Rechtsklick kauft, Linksklick verkauft
     * @param ganzerStapel ob geschlichen wurde - dann so viele Stufen wie moeglich
     */
    Ausgang handle(UUID kunde, String welt, int x, int y, int z, Handelsart art,
            boolean ganzerStapel) {
        Kistenshop shop = buch.anDemSchild(welt, x, y, z);
        if (shop == null) {
            // Auch bei einer Stoerung: anDemSchild gibt dann null und klagt selbst. Ein
            // Klick, der nichts bewirkt, ist die mildeste Antwort - siehe Shopbuch.
            return new Ausgang(Bescheid.KEIN_SHOP, art, null, null, 0L);
        }
        // Am EIGENEN Shop wird nicht gehandelt [entschieden 08.11.2026]. Der Besitzer
        // nimmt direkt aus der Kiste, und Geld an sich selbst waere ohnehin nur die
        // Gebuehr. Der wichtigere Grund: Sein Linksklick bleibt damit ein Abbau wie
        // heute - haetten wir ihn zum Verkauf gemacht, muesste der Schutz des eigenen
        // Schildes umgebaut werden, und ein unzerbrechliches Schild ist schlimmer als
        // ein Shop, an dem man nicht selbst kaufen kann.
        if (shop.gehoert(kunde)) {
            return absage(Bescheid.EIGENER_SHOP, art, shop);
        }
        if (!art.moeglichAn(shop)) {
            return absage(art == Handelsart.KUNDE_KAUFT
                    ? Bescheid.VERKAUFT_NICHT
                    : Bescheid.KAUFT_NICHT_AN, art, shop);
        }
        try {
            return pruefeUndHandle(kunde, shop, art, ganzerStapel);
        } catch (RuntimeException fehler) {
            // Was hier ankommt, kam aus Bukkit oder aus der Kasse und ist VOR der ersten
            // Veraenderung geworfen - die Schritte danach fangen selbst. Ein Wurf aus
            // einem Klick traefe sonst jeden Spieler auf dem Server.
            protokoll.log(Level.WARNING, "Handel an " + shop.schluessel()
                    + " liess sich nicht pruefen", fehler);
            return absage(Bescheid.GESTOERT, art, shop);
        }
    }

    private Ausgang pruefeUndHandle(UUID kunde, Kistenshop shop, Handelsart art,
            boolean ganzerStapel) {
        if (!waren.lesbar(shop)) {
            // Der Shop hoert nicht still auf zu handeln, sondern sagt es - und das
            // Protokoll nennt die Ware, damit ein Betreiber sie ersetzen kann. Genau
            // dafuer steht der Warenname eigens in der Tabelle.
            protokoll.warning("Die Ware des Kistenshops " + shop.schluessel()
                    + " (" + shop.ware().name() + ") ist nicht mehr lesbar - dieser Shop"
                    + " handelt nicht, bis sie neu eingelegt wird.");
            return absage(Bescheid.WARE_UNLESBAR, art, shop);
        }
        // Die vier Rollen dieser Tat. Nur hier wird die Richtung ausgelegt; danach ist
        // die Abfolge fuer Kauf und Verkauf dieselbe.
        Seite gibt = art == Handelsart.KUNDE_KAUFT ? Seite.KISTE : Seite.KUNDE;
        Seite nimmt = art == Handelsart.KUNDE_KAUFT ? Seite.KUNDE : Seite.KISTE;
        UUID zahler = art == Handelsart.KUNDE_KAUFT ? kunde : shop.besitzer();
        UUID empfaenger = art == Handelsart.KUNDE_KAUFT ? shop.besitzer() : kunde;

        // DER ANKAUFSTAND DES BUNDES - der einzige Unterschied ist der Tagesdeckel
        // weiter unten. Der Preis kommt bei JEDEM Shop aus seiner Zeile.
        boolean bundankauf = Bundankauf.istAnkaufstand(shop, art);
        long stufenpreis = art.stufenpreis(shop);
        // Der Bund hat unbegrenztes Geld - und hoechstens so viel, dass die Rechnung
        // nicht ueberlaeuft. Die Stapelgrenze deckelt die Stufen ohnehin.
        long guthaben = zahler == null ? Long.MAX_VALUE : kasse.guthaben(zahler);
        Bestand beimGeber = bestandVon(gibt, kunde, shop);
        Bestand beimNehmer = bestandVon(nimmt, kunde, shop);
        Shopregel.Stufung stufung =
                new Shopregel.Stufung(shop.menge(), waren.stapelgroesse(shop));

        int moeglich = Shopregel.passendeStufen(stufenpreis, guthaben,
                beimGeber.vorrat(), beimNehmer.platz(), stufung);
        if (moeglich < 1) {
            // Erst der gewoehnliche Mangel: Wer gar keine Ware hat, soll das lesen und
            // nicht "heute ist genug verkauft". Was fehlt, ist die Ware.
            return absage(warumNicht(art, shop, beimGeber, beimNehmer, guthaben,
                    stufenpreis), art, shop, stufenpreis);
        }
        if (bundankauf) {
            // UND JETZT DER DECKEL. Er steht NACH der Stufenrechnung, weil er deren
            // Ergebnis braucht: Der Wunsch ist, was dieser Klick sonst buchen wuerde,
            // und nur dann sieht der Deckel genau die Summe, die auf dem Konto landet.
            int imDeckel = Bundankauf.stufenImDeckel(wirtschaft.werte().markt(),
                    Markttag.heuteSchon(kasse, wirtschaft, uhr, kunde), stufenpreis,
                    moeglich);
            if (imDeckel < 1) {
                return absage(Bescheid.BUND_DECKEL, art, shop, stufenpreis);
            }
            moeglich = imDeckel;
        }
        // Der EINFACHE Klick geht durch dieselbe Schranke und wird danach auf eine Stufe
        // begrenzt. Eine eigene Pruefung fuer den haeufigeren Weg waere die zweite
        // Wahrheit - und dann verkaufte der einfache Klick, was der Sneak-Klick
        // abgelehnt haette.
        int stufen = ganzerStapel ? moeglich : 1;
        Shopregel.Handel handel = Shopregel.rechne(shop.menge(), stufenpreis, stufen,
                wirtschaft.werte().handel().prozentFuer(shop, art));
        return vollziehe(kunde, shop, art, handel, gibt, nimmt, zahler, empfaenger,
                bundankauf ? Buchungsgrund.BUNDESMARKT_VERKAUF
                        : Buchungsgrund.SHOP_VERKAUFT, stufenpreis);
    }

    /**
     * Die fuenf Schritte - siehe den Klassenkopf.
     *
     * <p>Ab hier wird veraendert. Jeder Schritt, der scheitern kann, nimmt zurueck, was
     * die Schritte davor getan haben; nur die letzten zwei koennen das nicht mehr, und
     * sie schreiben darum eine SEVERE-Zeile. Das ist dieselbe Entscheidung wie in
     * {@code Mietbuch.kuendigen}: Wo kein Rueckweg ist, muss der Fall LAUT sein.
     */
    private Ausgang vollziehe(UUID kunde, Kistenshop shop, Handelsart art,
            Shopregel.Handel handel, Seite gibt, Seite nimmt, UUID zahler,
            UUID empfaenger, Buchungsgrund erloesgrund, long stufenpreis) {
        // --- 1. Der ZAHLER ---------------------------------------------------
        if (zahler != null) {
            boolean gezahlt;
            try {
                gezahlt = kasse.abbuchen(zahler, handel.preis(),
                        Buchungsgrund.SHOP_GEKAUFT);
            } catch (RuntimeException fehler) {
                protokoll.log(Level.WARNING, "Handel an " + shop.schluessel()
                        + ": das Abbuchen von " + handel.preis() + " Mark bei "
                        + Spielername.von(zahler) + " ist gescheitert. Es ist nichts"
                        + " geschehen.", fehler);
                return absage(Bescheid.GESTOERT, art, shop);
            }
            if (!gezahlt) {
                // Die saubere Absage: abbuchen bucht ganz oder gar nicht, also ist
                // nichts geschehen - auch keine Zeile im Buch.
                return absage(art == Handelsart.KUNDE_KAUFT
                        ? Bescheid.KEIN_GELD
                        : Bescheid.SHOP_PLEITE, art, shop);
            }
        }
        // --- 2. Die Ware von der Geberseite ---------------------------------
        int stueck = handel.stueck();
        int genommen = bewege(true, gibt, kunde, shop, stueck);
        if (genommen != stueck) {
            // Zurueck, woher es kam - die Plaetze sind gerade frei geworden.
            zurueck(gibt, kunde, shop, genommen, "nach dem Entnehmen");
            erstatte(zahler, shop, handel, "die Ware liess sich nicht entnehmen ("
                    + genommen + " von " + stueck + ")");
            return absage(Bescheid.GESTOERT, art, shop);
        }
        // --- 3. Die Ware auf die Nehmerseite --------------------------------
        int gegeben = bewege(false, nimmt, kunde, shop, stueck);
        if (gegeben != stueck) {
            // Kein halber Handel: Was angekommen ist, wird wieder genommen, und ALLES
            // geht dorthin zurueck, wo es herkam.
            int wiederGeholt = waren.nehme(nimmt, kunde, shop, gegeben);
            zurueck(gibt, kunde, shop, stueck - gegeben + wiederGeholt,
                    "nach dem Uebergeben");
            erstatte(zahler, shop, handel, "die Ware kam nicht an (" + gegeben + " von "
                    + stueck + ")");
            return absage(Bescheid.GESTOERT, art, shop);
        }
        // --- 4. und 5. Der EMPFAENGER und der Staat -------------------------
        // Ab hier ist der Handel geschehen: Die Ware liegt beim Nehmenden, und das ist
        // nicht mehr zurueckzunehmen, ohne sie ihm wieder wegzunehmen. Was jetzt noch
        // scheitert, wird darum NICHT gegengebucht, sondern laut: Was fehlt, ist der
        // Erloes, und der wird nachgebucht - nicht erstattet.
        bezahle(empfaenger, shop, handel, erloesgrund);
        return new Ausgang(Bescheid.GEHANDELT, art, shop, handel, stufenpreis);
    }

    /**
     * Schreibt dem Empfaenger den PREIS gut und bucht die Gebuehr davon ab.
     *
     * <p><b>Der volle Preis und nicht der Erloes</b>, und dann die Gebuehr als eigene
     * Zeile auf DEMSELBEN Konto - siehe {@link Buchungsgrund#SHOP_VERKAUFT}. Der Erloes
     * gutzuschreiben UND die Gebuehr abzubuchen zoege sie zweimal ab; die Gebuehr still
     * abzuziehen machte sie unsichtbar. So bleibt netto der Erloes, die Gebuehr steht im
     * Verlauf, und die Summe aller Buchungen eines Kontos ist weiterhin sein Stand.
     *
     * <p>Die Reihenfolge ist zwingend: erst gutschreiben, dann abbuchen. Umgekehrt
     * scheiterte das Abbuchen bei einem Empfaenger ohne Guthaben, und er behielte den
     * ganzen Preis. Nach der Gutschrift kann es nicht scheitern, denn die Gebuehr ist
     * nie groesser als der Preis - das haelt {@code Shopregel.Handel} selbst.
     *
     * <p>Bei einem Empfaenger, den es nicht gibt - dem Bund -, faellt beides weg. Sein
     * Geld verschwindet, und das ist die staerkste Senke des Servers; eine Gegenzeile
     * dafuer gibt es nicht, hier so wenig wie bei der Ladenmiete.
     */
    private void bezahle(UUID empfaenger, Kistenshop shop, Shopregel.Handel handel,
            Buchungsgrund erloesgrund) {
        if (empfaenger == null) {
            return;
        }
        try {
            // DER GRUND KOMMT HEREIN und steht nicht hier: An einem Ankaufstand des
            // Bundes ist der Erloes eine Einnahme aus dem BUNDESMARKT, und genau daran
            // zaehlt sein Tagesdeckel. Stuende hier fest SHOP_VERKAUFT, zaehlte der
            // Deckel seine eigenen Zeilen nicht mit - das ist die benannte Luecke aus
            // Plan 7.4, und sie laege wieder offen.
            kasse.gutschreiben(empfaenger, handel.preis(), erloesgrund);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Handel an " + shop.schluessel()
                    + " ist vollzogen, aber " + Spielername.von(empfaenger)
                    + " hat seinen Erloes von " + handel.preis() + " Mark NICHT"
                    + " bekommen. Von Hand gutzuschreiben - die Ware ist schon"
                    + " uebergeben, ein Rueckgaengig gibt es nicht.", fehler);
            return;
        }
        // Nur, wenn die GERECHNETE Gebuehr ueber 0 liegt - nicht schon, wenn ein Satz
        // ueber 0 steht. Bei 5 Prozent auf 19 Mark ist sie abgerundet 0, und eine Zeile
        // ueber 0 Mark waere eine Zeile ohne Geldbewegung.
        if (!handel.kostetGebuehr()) {
            return;
        }
        try {
            if (!kasse.abbuchen(empfaenger, handel.gebuehr(),
                    Buchungsgrund.SHOP_GEBUEHR)) {
                protokoll.severe("Handel an " + shop.schluessel() + ": die Gebuehr von "
                        + handel.gebuehr() + " Mark liess sich bei "
                        + Spielername.von(empfaenger) + " nicht abbuchen, obwohl er"
                        + " gerade " + handel.preis() + " Mark bekommen hat. Das kann"
                        + " nicht sein und ist von Hand nachzuholen.");
            }
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Handel an " + shop.schluessel()
                    + ": die Gebuehr von " + handel.gebuehr() + " Mark fehlt im Buch."
                    + " Von Hand abzubuchen.", fehler);
        }
    }

    /**
     * Dreht die Zahlung zurueck - {@link Buchungsgrund#SHOP_ERSTATTET}.
     *
     * <p>Gerufen, wenn die Ware nach dem Abbuchen nicht kam. Die Erstattung laeuft durch
     * dieselbe Verbindung, die gerade geschwiegen haben kann, also kann sie mitscheitern
     * - darum steht sie in einem Fang und schreibt dann SEVERE. Das ist eine Chance plus
     * eine lesbare Zeile und keine Zusicherung; dieselbe Form wie
     * {@code Mietbuch.erstatte}.
     */
    private void erstatte(UUID zahler, Kistenshop shop, Shopregel.Handel handel,
            String warum) {
        if (zahler == null) {
            return;
        }
        try {
            kasse.gutschreiben(zahler, handel.preis(), Buchungsgrund.SHOP_ERSTATTET);
            protokoll.warning("Handel an " + shop.schluessel() + " abgebrochen, weil "
                    + warum + ". " + Spielername.von(zahler) + " hat seine "
                    + handel.preis() + " Mark zurueck.");
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Handel an " + shop.schluessel()
                    + " abgebrochen, weil " + warum + " - und die Erstattung von "
                    + handel.preis() + " Mark an " + Spielername.von(zahler)
                    + " ist AUCH gescheitert. Von Hand gutzuschreiben.", fehler);
        }
    }

    /** Nimmt oder legt - und faengt, was Bukkit wirft. */
    private int bewege(boolean nehmen, Seite seite, UUID kunde, Kistenshop shop,
            int stueck) {
        // Bei einem Bundshop wird die Kiste nicht angefasst: Die Ware kommt aus dem
        // Nichts und verschwindet dorthin. Haetten wir dort gezaehlt und entnommen,
        // deckelte die Kiste den Bundshop.
        if (seite == Seite.KISTE && shop.demBund()) {
            return stueck;
        }
        try {
            return nehmen
                    ? waren.nehme(seite, kunde, shop, stueck)
                    : waren.lege(seite, kunde, shop, stueck);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Handel an " + shop.schluessel() + ": "
                    + stueck + " Stueck " + (nehmen ? "aus " : "in ") + seite
                    + (nehmen ? " zu nehmen" : " zu legen") + " ist gescheitert",
                    fehler);
            return 0;
        }
    }

    /**
     * Legt zurueck, was schon bewegt war - der letzte Riegel gegen verlorene Ware.
     *
     * <p>Scheitert auch das, ist die Ware wirklich weg, und dann muss es LAUT sein:
     * dieselbe Entscheidung wie in {@code Abholbuch.zurueck}. Ein stiller Verlust ist
     * das einzige, was schlimmer waere.
     */
    private void zurueck(Seite seite, UUID kunde, Kistenshop shop, int stueck,
            String wann) {
        if (stueck < 1) {
            return;
        }
        int angekommen = bewege(false, seite, kunde, shop, stueck);
        if (angekommen != stueck) {
            protokoll.severe("Handel an " + shop.schluessel() + ": beim Zuruecklegen "
                    + wann + " sind " + (stueck - angekommen) + " von " + stueck
                    + " Stueck " + shop.ware().name() + " nicht in " + seite
                    + " zurueckgekommen. Diese Ware ist verloren.");
        }
    }

    /**
     * Was auf dieser Seite liegt - beim Bundshop ohne zu fragen.
     *
     * <p>Die Entscheidung "der Bund ist unbegrenzt" steht HIER und nicht in der
     * Bukkit-Naht. Dort waere sie eine Regel in der einen Klasse, die ohne Server nicht
     * pruefbar ist.
     */
    private Bestand bestandVon(Seite seite, UUID kunde, Kistenshop shop) {
        if (seite == Seite.KISTE && shop.demBund()) {
            return UNBEGRENZT;
        }
        return waren.bestand(seite, kunde, shop);
    }

    /**
     * WAS gefehlt hat - die Reihenfolge ist die der Haeufigkeit.
     *
     * <p>Erst der Vorrat ("ausverkauft" ist der haeufigste Fall), dann der Platz, dann
     * das Geld. Eine Absage muss sagen, was fehlt; "es geht nicht" ist die Absage, die
     * ChestShop ein Forum voller Fragen eingebracht hat.
     *
     * <p>Der letzte Fall kann nicht eintreten - wenn Vorrat, Platz und Geld fuer eine
     * Stufe reichen, ist {@code passendeStufen} mindestens 1, denn der Stapeldeckel ist
     * nie kleiner. Dass er trotzdem dasteht und protokolliert, ist Absicht: Ein
     * unmoeglicher Fall, der in eine falsche Absage gefaltet wird, ist nicht mehr zu
     * finden.
     *
     * <p><b>Der Tagesdeckel des Bundes steht hier NICHT</b>, und das ist eine
     * Reihenfolge und kein Vergessen: Er wird erst gefragt, wenn eine Stufe ueberhaupt
     * moeglich ist. Wer gar keine Ware hat, soll "du hast die Ware nicht" lesen und
     * nicht "heute ist genug verkauft" - die zweite Zeile schickt ihn nach Hause,
     * obwohl ihm nur die Ware fehlt.
     */
    private Bescheid warumNicht(Handelsart art, Kistenshop shop, Bestand beimGeber,
            Bestand beimNehmer, long guthaben, long stufenpreis) {
        boolean kauf = art == Handelsart.KUNDE_KAUFT;
        if (beimGeber.vorrat() < shop.menge()) {
            return kauf ? Bescheid.AUSVERKAUFT : Bescheid.KEINE_WARE;
        }
        if (beimNehmer.platz() < shop.menge()) {
            return kauf ? Bescheid.KEIN_PLATZ : Bescheid.KISTE_VOLL;
        }
        if (guthaben < stufenpreis) {
            return kauf ? Bescheid.KEIN_GELD : Bescheid.SHOP_PLEITE;
        }
        protokoll.severe("Handel an " + shop.schluessel() + ": keine Stufe moeglich,"
                + " obwohl Vorrat (" + beimGeber.vorrat() + "), Platz ("
                + beimNehmer.platz() + ") und Guthaben (" + guthaben + " bei Stufenpreis "
                + stufenpreis + ") fuer " + shop.menge() + " Stueck reichen. Das ist ein"
                + " Fehler in der Stufenrechnung.");
        return Bescheid.GESTOERT;
    }

    /**
     * Eine Absage mit dem Preis AUS DER SHOPZEILE - der gewoehnliche Fall.
     *
     * <p>Zwei Fassungen, und der Unterschied ist genau die Zahl, die der Spieler liest:
     * An einem Ankaufstand des Bundes steht der Preis in der Tafel und nicht in der
     * Zeile, und dort ruft {@link #pruefeUndHandle} die andere Fassung. Eine einzige
     * Fassung, die den Preis selbst aus dem Shop holte, nennte am Stand die Zahl des
     * Schildes - also die alte.
     */
    private static Ausgang absage(Bescheid bescheid, Handelsart art,
            Kistenshop shop) {
        return absage(bescheid, art, shop, art.stufenpreis(shop));
    }

    /** Dieselbe Absage mit einem Preis, der nicht in der Shopzeile steht. */
    private static Ausgang absage(Bescheid bescheid, Handelsart art, Kistenshop shop,
            long stufenpreis) {
        return new Ausgang(bescheid, art, shop, null, stufenpreis);
    }
}
