package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze der Kistenshops - der dreizehnte Bereich.
 *
 * <p>Wie in den zwoelf Bereichen vor diesem: Pfad, Art und die Namen der Werte stehen
 * hier, der WORTLAUT nur in {@code sprache-vorlage.yml}. Deshalb ist die
 * Platzhalterprobe nicht selbstbezueglich - wer beim Abschreiben einem Satz sein
 * {@code {ware}} wegnimmt, bekommt eine rote Probe und keinen stummen Fehler.
 *
 * <p><b>Zwei Arten, und die Trennung folgt dem Ereignis.</b> Was beim ANLEGEN gesagt wird,
 * ist {@link Art#SATZ} und geht in den Chat: Der Spieler hat ein Schild beschrieben, er
 * soll die Antwort lesen und auch noch finden, wenn er den Kopf gedreht hat. Was der
 * SCHUTZ sagt, ist {@link Art#HANDBALKEN} wie in {@link WacheSatz}: Dort hat jemand eine
 * Hand gehoben, und eine Chatzeile je Schlag waere ein Regen.
 *
 * <p><b>Acht Saetze fuer die Schildfehler und nicht einer mit einem Wortstueck.</b> Das
 * ist die teuerste Entscheidung dieses Bereichs und die wichtigste: Ein Rahmensatz mit
 * einem eingesetzten Grund koennte die HILFE nicht mitwaehlen. "Die Menge ist keine
 * Zahl" braucht als naechsten Satz "schreibe eine Zahl von 1 bis 3456", "die Preise sind
 * verdreht" braucht "der Ankaufspreis darf nicht ueber dem Verkaufspreis liegen" - und
 * ein Spieler, der am Schild steht und nicht weiss, was er falsch getippt hat, bricht
 * nach dem zweiten Versuch ab. Die Vorbilder haben hier genau einen Satz
 * ("You don't have permission" bei ChestShop deckt sieben verschiedene Faelle) und dafuer
 * ein Forum voller Fragen.
 *
 * <p><b>{@code {zeile}} traegt eine Zahl, die NICHT im Wortlaut steht.</b> Welche Zeile
 * falsch ist, weiss {@code Shopschild.lies} und sonst niemand - sie gibt sie als
 * {@code Ergebnis.zeile()} heraus. Stuende die 2 im Wort ("Zeile 2 ist falsch"), waere
 * sie beim naechsten Umbau des Schildformats falsch, ohne dass eine Probe das merkt.
 *
 * <p><b>Die drei Zahlen der Grenzen kommen aus dem Kern</b>, nicht aus dem Wort:
 * {@code {hoechstmenge}} ist {@code Kistenshop.GROESSTE_MENGE},
 * {@code {hoechstpreis}} ist {@code Kistenshop.HOECHSTER_PREIS}. Ein Betreiber, der die
 * Grenze aendert, aendert eine Zahl im Programm und nicht zwei Worte in einer Datei.
 *
 * <p><b>Kein {@link Art#GEZAEHLT}, kein {@link Art#WORT}, kein {@link Art#MUSTER}:</b> In
 * diesem Zug gibt es keine Ein-/Mehrzahl, kein eingesetztes Wortstueck und kein Datum. Die
 * Mengen- und Preiszeilen tragen Zahlen, aber keine, die den Satzbau aendert - "3456" ist
 * eine Grenze und keine Anzahl von Dingen.
 */
enum ShopSatz implements Satz {

    // --- Das Anlegen ist gelungen ---------------------------------------------

    /**
     * Der Shop steht - mit Menge und Ware.
     *
     * <p>{@code {ware}} ist der Kurzname aus {@code Shopware.kurzname}, also dasselbe
     * Wort, das auf der vierten Schildzeile steht. Das ist Absicht: Der Spieler soll die
     * Meldung mit dem Schild vor sich vergleichen koennen. Ein ausfuehrlicherer Name hier
     * waere schoener zu lesen und liesse ihn raten, ob das Schild dieselbe Ware meint.
     */
    SHOP_ANGELEGT("shop.angelegt", Art.SATZ, "menge", "ware"),

    /**
     * Die ZUSATZZEILE fuer einen Bundshop - kein Ersatz fuer {@link #SHOP_ANGELEGT}.
     *
     * <p>Ein eigener Satz statt eines zweiten Hauptsatzes, und das ist der Unterschied zu
     * {@code ParzellenSatz.BESCHEID_NOCH_FREI}: Dort schliessen sich die zwei Saetze aus,
     * hier kommt dieser HINTER den anderen. Ein Bundshop ist ein gewoehnlicher Shop mit
     * einer Eigenschaft mehr - unbegrenzter Vorrat -, und genau diese eine Eigenschaft
     * steht in dieser einen Zeile.
     */
    SHOP_ANGELEGT_BUND("shop.angelegt-bund", Art.SATZ),

    /**
     * Was ein Kunde zahlt, wenn er kauft - nur gesendet, wenn es einen Kaufpreis gibt.
     *
     * <p>Zwei Saetze fuer die zwei Richtungen und nicht einer mit beiden Zahlen: Ein Shop
     * kann nur kaufen, nur verkaufen oder beides, und ein gemeinsamer Satz muesste fuer
     * den Nurverkaufsfall eine Null nennen. Eine Null als Preis ist genau das, was
     * {@code Shopschild} als {@code PREIS_BEIDE_NULL} abweist - sie darf nirgends als
     * gueltiger Preis zu lesen sein.
     */
    SHOP_PREIS_KAUF("shop.preis-kauf", Art.SATZ, "preis"),

    /** Was ein Kunde bekommt, wenn er verkauft - nur bei einem Verkaufspreis. */
    SHOP_PREIS_VERKAUF("shop.preis-verkauf", Art.SATZ, "preis"),

    /**
     * Das Schild ist weg, der Shop damit auch.
     *
     * <p>Nennt die Ware, damit der Besitzer beim Abbauen mehrerer Schilder lesen kann,
     * welches er gerade erwischt hat. Und er soll lesen, dass die WARE IN DER KISTE
     * BLEIBT - ein abgebauter Shop ist kein Verlust, aber das weiss nur, wer es gesagt
     * bekommt.
     */
    SHOP_ABGEBAUT("shop.abgebaut", Art.SATZ, "ware"),

    // --- Das Schild ist falsch beschriftet: acht Absagen, acht Hilfen ---------

    /** In der Mengenzeile steht keine Zahl. */
    SHOP_MENGE_KEINE_ZAHL("shop.absage.menge-keine-zahl", Art.SATZ, "zeile"),

    /** Die Menge ist eine Zahl, aber keine moegliche - die Grenze kommt aus dem Kern. */
    SHOP_MENGE_AUSSERHALB("shop.absage.menge-ausserhalb", Art.SATZ, "zeile",
            "hoechstmenge"),

    /**
     * Die Preiszeile hat nicht die eine Form.
     *
     * <p>Der Satz, der am meisten erklaeren muss, denn die Form ist nicht zu erraten.
     * EINE Form und nicht mehrere, und das ist der Befund aus Plan 7.4: ChestShop nimmt
     * Punkte, Kommas, ein {@code E} als Exponenten und freie Reihenfolge an und hat dafuer
     * zwei Fehler im Gepaeck (PR #522 zum Nullpreis, #30 zum {@code E}).
     */
    SHOP_PREIS_FORM("shop.absage.preis-form", Art.SATZ, "zeile"),

    /** Die Form stimmt, aber hinter dem Buchstaben steht keine Zahl. */
    SHOP_PREIS_KEINE_ZAHL("shop.absage.preis-keine-zahl", Art.SATZ, "zeile"),

    /** Der Preis ist eine Zahl, aber keine moegliche. */
    SHOP_PREIS_AUSSERHALB("shop.absage.preis-ausserhalb", Art.SATZ, "zeile",
            "hoechstpreis"),

    /**
     * Beide Preise sind null - das ist kein Shop, sondern ein Geschenk.
     *
     * <p>Eine eigene Absage und nicht {@link #SHOP_PREIS_AUSSERHALB}: Null ist eine
     * erlaubte Zahl fuer EINE der beiden Richtungen (ein Shop, der nur verkauft, hat
     * keinen Ankaufspreis). Nur beide zusammen sind der Fehler, und der Spieler soll
     * lesen, dass EINER der beiden genuegt.
     */
    SHOP_PREIS_BEIDE_NULL("shop.absage.preis-beide-null", Art.SATZ, "zeile"),

    /**
     * Der Ankaufspreis liegt ueber dem Verkaufspreis - eine Gelddruckmaschine.
     *
     * <p>Die Absage, die am meisten Geld rettet: Wer fuer 100 verkauft und fuer 120
     * ankauft, baut eine Maschine, die jedem Kunden bei jedem Durchlauf 20 Mark schenkt -
     * und zwar aus dem Guthaben des Shopbesitzers, bis es leer ist. Das ist keine Regel
     * des Spiels, sondern eine Rechnung, und {@code Kistenshop} weist sie schon im Satz
     * ab. Dieser Satz muss erklaeren, WARUM: Ein Spieler, der nur "nicht erlaubt" liest,
     * haelt es fuer Willkuer.
     */
    SHOP_PREIS_VERDREHT("shop.absage.preis-verdreht", Art.SATZ, "zeile"),

    /** Die vierte Zeile ist leer - kein Name und kein Fragezeichen. */
    SHOP_WARE_FEHLT("shop.absage.ware-fehlt", Art.SATZ, "zeile"),

    // --- Das Schild stimmt, aber die Welt sagt nein ---------------------------

    /**
     * Neben dem Schild steht keine Kiste, in der ein Shop sein darf.
     *
     * <p>Nennt die drei erlaubten Behaelter, denn das ist die haeufigste stille
     * Ueberraschung: Ein Ofen, ein Spender und eine Shulkerkiste sehen aus wie Behaelter
     * und sind als Shop ausgeschlossen - jeder aus einem eigenen Grund, der in
     * {@code Shopware.ERLAUBTE_KISTEN} steht.
     */
    SHOP_KEINE_KISTE("shop.absage.keine-kiste", Art.SATZ),

    /**
     * An der Kiste darf dieser Spieler nicht - sie steht auf fremdem Grund.
     *
     * <p>Die Absage zu der Luecke, die kein Vorbild selbst schliesst: Das Schild haengt
     * auf eigenem Grund, die Kiste steht einen Block weiter auf fremdem. Siehe den Kopf
     * von {@link Shopbuch}.
     */
    SHOP_FREMDE_KISTE("shop.absage.fremde-kiste", Art.SATZ),

    /** An diesem Schild haengt schon ein Shop. */
    SHOP_SCHON_EIN_SHOP("shop.absage.schon-ein-shop", Art.SATZ),

    /**
     * Das Fragezeichen, aber die Kiste sagt nicht, was gemeint ist.
     *
     * <p>Muss BEIDE Faelle nennen - leer und mehrere Arten -, weil der Spieler von aussen
     * nicht sieht, welcher vorliegt. Und er muss lesen, dass eine verzauberte und eine
     * gewoehnliche Spitzhacke ZWEI Arten sind: Das ist die Folge der Entscheidung vom
     * 08.11.2026 und von aussen nicht zu erraten.
     */
    SHOP_WARE_UNKLAR("shop.absage.ware-unklar", Art.SATZ),

    /**
     * Den getippten Gegenstand gibt es nicht - und der Spieler liest, was er getippt hat.
     *
     * <p>{@code {getippt}} kommt aus der vierten Schildzeile, nicht aus einer Liste
     * moeglicher Namen: Ein Vorschlag waere schoener und hier falsch, denn das Schild ist
     * beim Lesen dieser Zeile schon beschrieben - der Spieler muss nur sehen, welches
     * Wort das Programm gelesen hat. Oft ist es ein Leerzeichen zu viel.
     */
    SHOP_WARE_UNBEKANNT("shop.absage.ware-unbekannt", Art.SATZ, "getippt"),

    /** Ein Bundshop, aber das Recht dafuer fehlt. */
    SHOP_KEIN_BUNDRECHT("shop.absage.kein-bundrecht", Art.SATZ),

    /**
     * Die Datenbank hat geschwiegen.
     *
     * <p>Sagt ausdruecklich "noch einmal versuchen", wie
     * {@code WacheSatz.SCHUTZ_LADEN_GESTOERT}: Ein Spieler, der eine Absage ohne Ausweg
     * liest, baut sein Schild ab und kommt nicht wieder.
     */
    SHOP_GESTOERT("shop.absage.gestoert", Art.SATZ),

    // --- Der Schutz: vier Handbalken ------------------------------------------

    /**
     * Das Schild eines anderen - der Handbalken des Shopschutzes.
     *
     * <p><b>Dieser Schutz gilt UEBERALL</b>, auch in der Wildnis, wo keine Parzelle und
     * keine Ladeneinheit greift. Das ist der Unterschied zum Bauschutz: Dort entscheidet
     * der ORT, hier der Shop. Ein Shop in der Wildnis waere sonst von jedem abbaubar, und
     * mit dem Schild faellt der Schutz der Kiste.
     */
    SHOP_SCHUTZ_SCHILD("shop.schutz.fremdes-schild", Art.HANDBALKEN, "besitzer"),

    /**
     * Die Kiste eines fremden Shops.
     *
     * <p>Ein eigener Satz und nicht derselbe wie beim Schild: Der Spieler soll lesen,
     * WARUM eine gewoehnlich aussehende Kiste geschuetzt ist - naemlich weil ein Schild
     * daran haengt. Sonst sucht er den Fehler bei sich.
     */
    SHOP_SCHUTZ_KISTE("shop.schutz.fremde-kiste", Art.HANDBALKEN, "besitzer"),

    /**
     * Ein Bundshop - der hat keinen Besitzer, den man nennen koennte.
     *
     * <p>Darum ein dritter Satz ohne {@code {besitzer}} und nicht derselbe mit einem
     * eingesetzten Wort "der Bund": Ein Platzhalter, in dem manchmal ein Mensch und
     * manchmal eine Einrichtung steht, zwingt den Betreiber zu einem Satz, der fuer beide
     * passt - und solche Saetze lesen sich wie eine Fehlermeldung.
     */
    SHOP_SCHUTZ_BUND("shop.schutz.bundshop", Art.HANDBALKEN),

    /**
     * Der Block, der ein fremdes Shopschild TRAEGT.
     *
     * <p>Die ueberraschendste der vier Absagen, denn der Block sieht nach nichts aus - er
     * ist ein Stein unter einem Stehschild. Darum muss der Satz den Grund nennen: Ohne
     * ihn faellt das Schild, und dann steht ein Shop in der Datenbank, dessen Schild es
     * nicht mehr gibt. ChestShop faengt diesen Fall mit einem eigenen
     * {@code handlePhysicsBreak} NACH dem Fall; hier faellt nichts.
     */
    SHOP_SCHUTZ_TRAEGER("shop.schutz.traeger", Art.HANDBALKEN);

    private final String pfad;
    private final Art art;
    private final Set<String> platzhalter;

    ShopSatz(String pfad, Art art, String... platzhalter) {
        this.pfad = pfad;
        this.art = art;
        this.platzhalter = Set.of(platzhalter);
    }

    @Override
    public String pfad() {
        return pfad;
    }

    @Override
    public Art art() {
        return art;
    }

    @Override
    public Set<String> platzhalter() {
        return platzhalter;
    }

    /**
     * Keiner dieser Saetze darf leer sein.
     *
     * <p>Dieselbe Hausregel wie in {@link WacheSatz}, und hier mit einem eigenen Gewicht:
     * Jeder dieser Saetze ist die EINZIGE Antwort auf eine Tat. Ein leerer
     * Schildfehlersatz liesse einen Spieler am Schild stehen, dessen Shop nicht entsteht
     * und der nicht erfaehrt, warum - das Schild bleibt beschrieben, und von aussen sieht
     * es aus wie ein Shop.
     */
    @Override
    public boolean darfLeerSein() {
        return false;
    }
}
