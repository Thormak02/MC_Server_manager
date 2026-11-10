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
 * diesem Bereich gibt es keine Ein-/Mehrzahl, kein eingesetztes Wortstueck und kein
 * Datum. Die Mengen- und Preiszeilen tragen Zahlen, aber keine, die den Satzbau
 * aendert - "3456" ist eine Grenze und "16x STONE" eine Angabe, keine Anzahl von
 * Dingen.
 *
 * <p><b>Der HANDEL redet im Handbalken und nicht im Chat</b> [08.11.2026], und das ist
 * dieselbe Begruendung wie beim Schutz, nur von der anderen Seite: Ein Spieler, der
 * zwanzig Stapel kauft, klickt zwanzigmal. Zwanzig Chatzeilen waeren ein Regen, und der
 * Handbalken ist genau die Stelle, auf die er dabei schaut. Was dauerhaft nachzulesen
 * sein muss, steht ohnehin woanders - jeder Handel ist eine Zeile unter
 * {@code /mark verlauf}.
 *
 * <p><b>Elf Absagen fuer den Handel</b>, aus demselben Grund wie die acht fuer das
 * Schild: Jede nennt, WAS fehlt. "Ausverkauft", "kein Platz" und "zu wenig Geld"
 * sind drei verschiedene Taten des Spielers, und wer statt dessen "das geht nicht"
 * liest, klickt noch dreimal und hoert dann auf.
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

    /**
     * An dieser Kiste haengt schon der Shop eines anderen.
     *
     * <p>Ein anderer Satz als {@link #SHOP_FREMDE_KISTE}: Dort gehoert der ORT einem
     * anderen ("sie steht auf fremdem Grund"), hier die KISTE. Draussen vor der
     * Stadt gehoert kein Ort jemandem, und dann ist das Shopschild die einzige
     * Besitzerklaerung, die eine Kiste tragen kann - ein Spieler, der dort "fremder
     * Grund" liest, sucht eine Parzelle, die es nicht gibt.
     *
     * <p>Der Satz NENNT KEINEN MENSCHEN, anders als der Schutzbalken: Wer hier
     * abgewiesen wird, steht direkt vor dem anderen Schild und liest den Namen darauf.
     * Ein Platzhalter mehr waere ein Platzhalter, der beim Bundshop leer bliebe.
     */
    SHOP_FREMDER_SHOP("shop.absage.fremder-shop", Art.SATZ),

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

    // --- Der Schutz: fuenf Handbalken -----------------------------------------

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
    SHOP_SCHUTZ_TRAEGER("shop.schutz.traeger", Art.HANDBALKEN),

    /**
     * Geschuetzt, aber niemand nennbar - die Ablage hat geschwiegen.
     *
     * <p>Der fuenfte Satz fuer den einen Fall, in dem die zwei Richtungen der Ablage
     * auseinandergehen: {@code Shopbuch.istShopkiste} gibt bei einer Stoerung
     * {@code true} ("schuetze sie"), {@code anDerKiste} eine leere Liste ("erfinde
     * keinen Besitzer"). Beides ist richtig, und zusammen heisst es: zu, aber ohne
     * Namen.
     *
     * <p>Darum ein eigener Satz und nicht der Kistensatz mit einem leeren Namen:
     * {@code Spielername.von(null)} gibt einen Bindestrich, und "Diese Kiste ist der
     * Shop von -" liest sich wie ein Programmfehler. Dieser Satz sagt, was wirklich
     * los ist, und nennt den Ausweg - denselben wie {@link #SHOP_GESTOERT}.
     */
    SHOP_SCHUTZ_UNLESBAR("shop.schutz.nicht-lesbar", Art.HANDBALKEN),

    // --- Der Handel ist gelungen ----------------------------------------------

    /**
     * Gekauft - was, wie viel und was es gekostet hat.
     *
     * <p>{@code {ware}} ist derselbe Kurzname wie auf der vierten Schildzeile, aus
     * demselben Grund wie bei {@link #SHOP_ANGELEGT}: Der Spieler soll die Meldung mit
     * dem Schild vor sich vergleichen koennen.
     *
     * <p><b>Keine Gebuehr in dieser Zeile.</b> Beim Kauf zahlt sie der SHOPBESITZER -
     * der Kunde zahlt den Preis, der auf dem Schild steht, und nichts darueber. Eine
     * Zeile, die ihm die Gebuehr eines anderen nennt, liest sich wie ein Abzug bei ihm.
     */
    SHOP_KAUF_GELUNGEN("shop.handel.gekauft", Art.HANDBALKEN, "menge", "ware",
            "preis"),

    /** Verkauft, ohne Gebuehr - der Kunde bekommt den ganzen Preis. */
    SHOP_VERKAUF_GELUNGEN("shop.handel.verkauft", Art.HANDBALKEN, "menge", "ware",
            "preis"),

    /**
     * Verkauft, und der Staat hat etwas genommen - ein EIGENER Satz.
     *
     * <p>Nicht derselbe mit einer Gebuehr von 0 darin: "fuer 100 Mark (100 minus 0
     * Gebuehr)" ist eine Zeile, die sich wie ein Fehler liest. Und nicht ein stiller
     * Abzug: Wer 100 auf dem Schild liest und 95 bekommt, sucht den Fehler beim Shop.
     *
     * <p>Alle drei Zahlen stehen darin, damit der Betreiber den Satz so bauen kann, wie
     * er will - und damit die Zeile nachrechenbar ist. Eine Zusage, die eine ihrer
     * Zahlen nicht nennt, traegt keine.
     */
    SHOP_VERKAUF_GEBUEHR("shop.handel.verkauft-gebuehr", Art.HANDBALKEN, "menge",
            "ware", "preis", "gebuehr", "erloes"),

    // --- Elf Absagen des Handels ----------------------------------------------

    /**
     * Rechtsklick an einem Shop, der nur ankauft.
     *
     * <p>Sagt auch, was stattdessen GEHT - der Linksklick. Ein Shop mit nur einem
     * Preis sieht aus wie jeder andere, und ohne diesen Hinweis probiert der Kunde die
     * andere Maustaste nicht.
     */
    SHOP_VERKAUFT_NICHT("shop.handel.verkauft-nicht", Art.HANDBALKEN),

    /** Linksklick an einem Shop, der nur verkauft - mit demselben Hinweis. */
    SHOP_KAUFT_NICHT_AN("shop.handel.kauft-nicht-an", Art.HANDBALKEN),

    /**
     * Der eigene Shop - hier wird nicht gehandelt [entschieden 08.11.2026].
     *
     * <p>Und der Satz sagt, was stattdessen geht: die Kiste aufmachen. Ohne diesen
     * Hinweis liest sich die Absage wie eine Stoerung, denn ein Besitzer, der sein
     * eigenes Schild anklickt, will meistens nur nachsehen, ob es geht.
     */
    SHOP_EIGENER_SHOP("shop.handel.eigener-shop", Art.HANDBALKEN),

    /**
     * Die Warenprobe des Shops ist nicht mehr zu deuten.
     *
     * <p>Der einzige Fall, in dem ein Spieler etwas liest, das NUR der Besitzer
     * beheben kann - darum sagt der Satz, dass er ihm das sagen soll. Entsteht, wenn ein
     * Spielupdate die Form der Gegenstaende so aendert, dass der gespeicherte Text nicht
     * mehr passt; die Ware steht dann noch in der Kiste, der Shop handelt sie aber nicht
     * mehr. Das Protokoll nennt den Warennamen, damit ein Betreiber sie ersetzen kann.
     */
    SHOP_WARE_UNLESBAR("shop.handel.ware-unlesbar", Art.HANDBALKEN),

    /** Die Shopkiste hat nicht genug - mit der Ware, damit der Kunde sie kennt. */
    SHOP_AUSVERKAUFT("shop.handel.ausverkauft", Art.HANDBALKEN, "ware"),

    /**
     * Der Kunde hat nicht genug von der Ware.
     *
     * <p>Mit der MENGE darin, und das ist der Unterschied zu "du hast die Ware nicht":
     * Ein Shop, der 16 Stueck je Handel nimmt, lehnt auch 15 ab. Ohne die Zahl sieht
     * der Kunde seine Ware im Rucksack und die Absage auf dem Schirm.
     */
    SHOP_KEINE_WARE("shop.handel.keine-ware", Art.HANDBALKEN, "menge", "ware"),

    /** Im Rucksack des Kunden ist kein Platz. */
    SHOP_KEIN_PLATZ("shop.handel.kein-platz", Art.HANDBALKEN),

    /**
     * Die Shopkiste ist voll.
     *
     * <p>Ein eigener Satz und nicht derselbe wie beim Rucksack: Der Kunde kann daran
     * nichts aendern, und ein Satz, der ihn zum Aufraeumen auffordert, schickt ihn ins
     * Leere. Er soll lesen, dass es am Shop liegt.
     */
    SHOP_KISTE_VOLL("shop.handel.kiste-voll", Art.HANDBALKEN),

    /** Der Kunde kann nicht zahlen - mit dem Preis, den er braucht. */
    SHOP_KEIN_GELD("shop.handel.kein-geld", Art.HANDBALKEN, "preis"),

    /**
     * Der Shopbesitzer kann nicht zahlen.
     *
     * <p>Ein Ankaufstand ohne Guthaben seines Besitzers - der haeufigste Fall, in dem
     * ein Shop dasteht und nicht handelt. Der Satz nennt den Besitzer NICHT: Wer wie
     * viel Geld hat, ist nicht die Sache eines Kunden, und der Shopname steht am
     * Schild.
     */
    SHOP_PLEITE("shop.handel.shop-pleite", Art.HANDBALKEN),

    /**
     * Etwas ist schiefgegangen - und zwar MITTEN im Handel.
     *
     * <p>Ein eigener Satz und nicht {@link #SHOP_GESTOERT}: Jener sagt "der Shop liess
     * sich nicht anlegen, das Schild bleibt stehen", und das hat mit einem Handelsklick
     * nichts zu tun. Dieselbe Regel wie bei den zwei Platzabsagen - ein Satz, der auf
     * zwei Lagen passt, passt auf keine.
     *
     * <p>Sagt ausdruecklich, dass kein Geld verloren ist. Das ist die Zusage, die der
     * Handel wirklich haelt: Was nach dem Zahlen scheitert, wird erstattet.
     */
    SHOP_HANDEL_GESTOERT("shop.handel.gestoert", Art.HANDBALKEN),

    // ============================================================ /shop freigeben
    //
    // Die Saetze des SCHLUESSELS (Plan 7.4, gebaut 09.11.2026). Sie sind die ersten
    // dieses Bereichs, die zu einem BEFEHL gehoeren - alles davor kommt von einem
    // beschriebenen Schild oder einer Hand an einem fremden Block. Darum Art.SATZ und
    // nicht HANDBALKEN: Wer einen Befehl tippt, schaut in den Chat, und die Antwort
    // soll stehen bleiben.

    /**
     * Die Freigabe ist eingetragen.
     *
     * <p>Nennt AUSDRUECKLICH, was sie erlaubt und was nicht - sonst muesste der
     * Besitzer raten, wie weit er gerade Vertrauen verschenkt hat. Das zweite Schild
     * bleibt verwehrt, und das ist keine Feinheit: Es ist der Weg, auf dem jemand
     * fremde Ware auf sein eigenes Konto verkaufen koennte.
     */
    SHOP_FREIGEGEBEN("shop.freigabe.erteilt", Art.SATZ, "spieler"),

    /** Die Freigabe ist zurueckgenommen. */
    SHOP_GESPERRT("shop.freigabe.gesperrt", Art.SATZ, "spieler"),

    /**
     * Der Genannte stand schon in der Liste.
     *
     * <p>Eine eigene Zeile und keine stille Wiederholung: Wer zweimal freigibt, soll
     * wissen, dass beim ersten Mal schon alles stimmte - sonst sucht er den Fehler.
     */
    SHOP_SCHON_FREI("shop.freigabe.schon-frei", Art.SATZ, "spieler"),

    /** Der Genannte stand gar nicht in der Liste. */
    SHOP_NICHT_FREI("shop.freigabe.nicht-frei", Art.SATZ, "spieler"),

    /** Die Ueberschrift von {@code /shop freigaben}. */
    SHOP_FREIGABEN_KOPF("shop.freigabe.kopf", Art.SATZ, "anzahl"),

    /** Eine Zeile der Freigabeliste. */
    SHOP_FREIGABEN_ZEILE("shop.freigabe.zeile", Art.SATZ, "spieler"),

    /**
     * Niemand ist freigegeben.
     *
     * <p>Eine eigene Zeile und nicht eine leere Liste: Wer nur die Ueberschrift sieht,
     * haelt es fuer einen Fehler - und hier ist die Auskunft "niemand" genau die, auf
     * die es ankommt.
     */
    SHOP_FREIGABEN_LEER("shop.freigabe.leer", Art.SATZ),

    /**
     * Der Spieler sieht kein Shopschild an.
     *
     * <p>Nennt die REICHWEITE, wie {@code blockbuch.kein-block}: Wer auf ein Schild
     * drei Meter weiter schaut, soll nicht raten, ob dort kein Shop ist oder der Blick
     * zu weit ging.
     */
    SHOP_KEIN_SCHILD("shop.freigabe.kein-schild", Art.SATZ, "reichweite"),

    /**
     * Der Shop im Blick gehoert einem anderen.
     *
     * <p>Freigeben darf NUR der Besitzer - nicht das Team und nicht ein schon
     * Freigegebener. Sonst waere der Schluessel selbst weitergebbar, und nach zwei
     * Schritten wuesste niemand mehr, wer eigentlich Zutritt hat.
     */
    SHOP_NICHT_DEINER("shop.freigabe.nicht-deiner", Art.SATZ),

    /**
     * Ein BUNDSHOP laesst sich nicht freigeben.
     *
     * <p>Er gehoert niemandem, also kann ihn auch niemand freigeben. Wer an einen
     * Bundshop darf, entscheidet allein {@code freimarken.shop.bund}.
     */
    SHOP_BUND_NICHT_FREIGEBBAR("shop.freigabe.bundshop", Art.SATZ),

    /** Sich selbst freizugeben ergibt nichts - der Besitzer darf ohnehin. */
    SHOP_SELBST("shop.freigabe.selbst", Art.SATZ),

    /** Den genannten Spieler kennt der Server nicht. */
    SHOP_UNBEKANNT("shop.freigabe.unbekannt", Art.SATZ, "name"),

    /** Die Ablage hat beim Freigeben geschwiegen. */
    SHOP_FREIGABE_GESTOERT("shop.freigabe.gestoert", Art.SATZ),

    // ===================================== die eigene SHOPLISTE [10.11.2026]

    /**
     * {@code /shop liste} hat nichts zu zeigen.
     *
     * <p>Eine eigene Zeile und keine leere Liste: "du hast keine" ist hier genau die
     * Auskunft, auf die es ankommt - und sie nennt gleich den Weg zum ersten Laden.
     */
    SHOP_LISTE_LEER("shop.liste.leer", Art.SATZ),

    /**
     * Die Ueberschrift: wie viele Laeden, und welche Seite davon.
     *
     * <p>Die Seite steht AUCH DANN da, wenn es nur eine gibt. Zwei Koepfe waeren zwei
     * Zeilen in der Vorlage, die auseinandergehen; und wer "Seite 1 von 1" liest, weiss
     * sofort, dass nichts fehlt.
     */
    SHOP_LISTE_KOPF("shop.liste.kopf", Art.SATZ, "anzahl", "seite", "seiten"),

    /**
     * Eine Zeile je Laden: Stelle, Menge, Ware und die beiden Preisstuecke.
     *
     * <p>Die STELLE steht vorn, denn das ist der Zweck dieser Liste - wer dreissig
     * Laeden hat, sucht den einen wieder. Welt UND Koordinaten, weil zwei Laeden
     * desselben Spielers in verschiedenen Welten sonst gleich aussehen.
     *
     * <p>{@code kauf} und {@code verkauf} sind die zwei Wortstuecke darunter ODER
     * NICHTS. Dasselbe Mittel wie {@code parzelle.liste.mitbauer}: Ein leerer Wert
     * verschwindet rueckstandslos, und darum stehen die Trenner in den Stuecken.
     */
    SHOP_LISTE_ZEILE("shop.liste.zeile", Art.SATZ, "welt", "x", "y", "z", "menge",
            "ware", "kauf", "verkauf"),

    /**
     * Das Wortstueck fuer den Preis, zu dem KUNDEN KAUFEN - ein Wortstueck, kein Satz.
     *
     * <p><b>Es haengt an {@code verkauftWare()}, und das ist kein Vertipper.</b> Die
     * Satznamen sind aus Kundensicht, die Methoden des Kerns aus Shopsicht: Wenn der
     * Shop verkauft, kauft der Kunde. Genau diese Verdrehung war am 08.11.2026 schon
     * einmal ein Fehler in {@link Shoptext} - vertauscht bekam ein Shop, der nur
     * ankauft, die Zeile "Kunden kaufen fuer 0 Mark", und eine 0 als Preis ist das,
     * was {@code Shopschild} als {@code PREIS_BEIDE_NULL} abweist.
     */
    SHOP_LISTE_KAUF("shop.liste.kauf", Art.WORT, "preis"),

    /** Das Wortstueck fuer den Preis, zu dem Kunden VERKAUFEN - an {@code kauftWare()}. */
    SHOP_LISTE_VERKAUF("shop.liste.verkauf", Art.WORT, "preis"),

    /**
     * Der Hinweis auf die naechste Seite - nur, wenn es eine gibt.
     *
     * <p>Er nennt die ZAHL der naechsten Seite und nicht bloss "es gibt mehr": Ein
     * Hinweis, der den Befehl nicht mitgibt, laesst den Spieler raten, wie man
     * blaettert.
     */
    SHOP_LISTE_WEITER("shop.liste.weiter", Art.SATZ, "naechste"),

    /**
     * Die gewuenschte Seite gibt es nicht.
     *
     * <p>Sie nennt, wie viele es GIBT. Eine Absage ohne diese Zahl schickt den Spieler
     * ins Probieren, und bei dreissig Laeden probiert er lange.
     */
    SHOP_LISTE_KEINE_SEITE("shop.liste.keine-seite", Art.SATZ, "seite", "seiten"),

    /**
     * Die Ablage hat geschwiegen.
     *
     * <p>Ein eigener Satz und nicht die leere Liste: Eine Stoerung, die wie "du hast
     * keine Shops" aussieht, laesst den Spieler glauben, seine Laeden seien weg.
     */
    SHOP_LISTE_GESTOERT("shop.liste.gestoert", Art.SATZ),

    // ================= der ANKAUF DES BUNDES am Schild [10.11.2026]
    //
    // SIEBEN Saetze standen hier am Mittag dieses Tages, und sechs sind am Nachmittag
    // wieder gegangen - mit der Tafel bundesmarkt.ankauf. Sie sagten, was an einer Tafel
    // schiefgehen kann: Ware nicht im Sortiment, Menge geht nicht glatt auf, Schild
    // traegt beide Preise. Ohne Tafel gibt es keinen dieser Faelle mehr: Der Preis steht
    // auf dem Schild, ein Schildpreis IST eine ganze Mark, und beide Preise haelt der
    // Satz Kistenshop selbst.
    //
    // Geblieben ist der eine, der nicht von der Tafel kam, sondern vom DECKEL - und der
    // ist der Grund des ganzen Schrittes: Ein Bundshop mit Verkaufspreis war eine
    // Geldquelle ohne Tagesdeckel (Plan 7.4, benannte Luecke).

    /**
     * Der Tagesdeckel des Bundesmarkts ist erreicht - morgen wieder.
     *
     * <p>Gelesen nur, wenn ueberhaupt gedeckelt wird: {@code bundesmarkt.tagesdeckel: 0}
     * heisst kein Deckel, und dann kommt dieser Satz nie.
     */
    SHOP_BUND_DECKEL("shop.bund.deckel", Art.SATZ);

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
