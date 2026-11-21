package de.freimarken.paper;

import java.util.Set;

/**
 * Die Saetze, die das Programm als GENAU EINE Chatzeile holt - die eine Liste davon.
 *
 * <p><b>Warum es diese Liste gibt:</b> {@link Sprache#eineZeile} wirft, wenn ein Satz
 * mehr als eine Zeile ergibt. Die Bedingung ist richtig - der Zweig hat Platz fuer eine
 * Zeile und nicht fuer zwei -, aber der Augenblick ist der schlechteste denkbare: Sie
 * trifft, wenn der Zweig schon laeuft. Der Spieler liest dann GAR NICHTS statt eines zu
 * langen Satzes, und steht der Satz in einem Fangzweig, verlaesst der Wurf sogar den
 * Fang: Aus einer Stoerungsmeldung wird eine rote englische Serverzeile.
 *
 * <p>Ausloesen kann das jeder Betreiber, denn die Sprachdatei gehoert ihm. Fuer die
 * AUSGELIEFERTE Vorlage haelt {@code VorlageTest} 10h dieselbe Zusage; die
 * {@code sprache.yml} des Betreibers sieht keine Probe und kann keine sehen. Der Riegel
 * gehoert also dorthin, wo seine Datei GELESEN wird ({@link Sprachliste}) - und dafuer
 * muss der Satz von sich wissen, dass er eine Zeile ist.
 *
 * <p><b>Warum EINE Liste und nicht ein Merkmal je Konstante:</b> Die Zusage lebt an der
 * HOLSTELLE und nicht am Satz - es gibt keinen Unterschied im Satz selbst, der ihn
 * einzeilig machte. Ein zweiter Wahrheitswert in neunundachtzig Konstruktoraufrufen waere
 * neunundachtzig Gelegenheiten, ihn zu vergessen, und keine davon fiele auf. So steht sie
 * an einer Stelle, die sich in einem Blick lesen laesst, und {@code VorlageTest} haelt
 * sie in BEIDE Richtungen gegen die Holstellen des Quelltextes: kein geholter Satz ohne
 * Eintrag, kein Eintrag ohne geholten Satz.
 *
 * <p><b>Der Handbalken steht NICHT hier</b>, obwohl er genauso einzeilig ist: Fuer ihn
 * klagt {@link Sprachliste} seit Schritt 7 schon, und zwar mit einem anderen Grund
 * (sendActionBar nimmt ein Component und keine Liste). Zwei Pruefungen fuer denselben
 * Fall machen einander unpruefbar - die eine kann entfallen, ohne dass etwas rot wird.
 *
 * <p><b>Was diese Liste NICHT ist:</b> ein Verbot mehrzeiliger Saetze. Ein Satz darf
 * mehrzeilig sein, solange ihn niemand als eine Zeile holt; der Aufbau gehoert dem
 * Betreiber. Holt ein neuer Zweig einen Satz von hier ueber {@code bau} und will ihn
 * mehrzeilig, dann gehoert er aus dieser Liste heraus - und die Tafel sagt es.
 */
final class Einzeiler {

    /**
     * Gerechnet und nicht gesammelt: Jeder Eintrag steht an einer {@code eineZeile(}
     * -Stelle des Spielmoduls, und keiner steht hier ohne eine.
     */
    private static final Set<Satz> CHATZEILEN = Set.of(
            BlockSatz.KOPF_ORT, BlockSatz.KOPF_SPIELER, BlockSatz.LEER_ORT,
            BlockSatz.LEER_SPIELER, BlockSatz.ZEILE, BlockSatz.ZEILE_ABGEWIESEN,
            BlockSatz.ZEILE_MIT_ORT, BlockSatz.ZEILE_MIT_ORT_ABGEWIESEN,
            BonusSatz.GEZAHLT_ERSTER, BonusSatz.STAND_OFFEN, BonusSatz.STAND_SERIE,
            BonusSatz.STAND_WARTET,
            BundSatz.PREIS_AUSGEGEBEN, BundSatz.PREIS_GRATIS, BundSatz.PREIS_KOSTET,
            BundSatz.STAND_ABLAGE_FLUECHTIG, BundSatz.STAND_ABLAGE_MARIADB,
            BundSatz.STAND_BEFEHLE, BundSatz.STAND_BEFEHLE_VERWALTUNG, BundSatz.STAND_KOPF,
            BundSatz.STAND_VERSION,
            FreistadtSatz.KOPF, FreistadtSatz.LEER, FreistadtSatz.ZEILE,
            LadenSatz.KOPF, LadenSatz.LEER, LadenSatz.ZEILE,
            MarkSatz.VERLAUF_ZEILE,
            MarktSatz.DECKEL_UNLESBAR, MarktSatz.FREI, MarktSatz.GESTOERT,
            MarktSatz.KEINE_SEITE, MarktSatz.KOPF, MarktSatz.LEER, MarktSatz.OHNE_DECKEL,
            MarktSatz.WEITER, MarktSatz.ZEILE,
            MenueSatz.GESTOERT, MenueSatz.KEINE_KONSOLE, MenueSatz.KLICK,
            MietSatz.ABHOLFACH_BLEIBT, MietSatz.ABHOLFACH_GEHOLT,
            MietSatz.ABHOLFACH_KEIN_PLATZ, MietSatz.ABHOLFACH_LEER, MietSatz.ABHOLFACH_STAND,
            MietSatz.ABHOLFACH_UNLESBAR, MietSatz.GEKUENDIGT, MietSatz.GEKUENDIGT_KAUTION,
            MietSatz.GEMIETET_ERSTMALS, MietSatz.GEMIETET_KOPF, MietSatz.GEMIETET_KOSTEN,
            MietSatz.GEMIETET_TAEGLICH, MietSatz.MIETE_ABGELAUFEN, MietSatz.MIETE_GERAEUMT,
            MietSatz.MIETE_GEWARNT, MietSatz.MIETE_INS_FACH, MietSatz.MIETE_VERZUG,
            MietSatz.STAND, MietSatz.STAND_KEINER, MietSatz.VERLAENGERT,
            RangSatz.AUFGESTIEGEN, RangSatz.AUS, RangSatz.GESTOERT, RangSatz.HOECHSTE,
            RangSatz.KEINE_KONSOLE, RangSatz.LEITER_ERREICHT, RangSatz.LEITER_JETZT,
            RangSatz.LEITER_KOPF, RangSatz.LEITER_OFFEN, RangSatz.NAECHSTER,
            RangSatz.SCHAU_FUSS, RangSatz.SCHAU_JETZT, RangSatz.SCHAU_KOPF,
            RangSatz.SCHAU_NAECHSTE, RangSatz.SCHAU_STAND_ERFUELLT, RangSatz.SCHAU_STAND_OFFEN,
            RangSatz.SCHAU_UNBEKANNT,
            // Die Regeln [21.11.2026]: Kopf und Knopf gehen in den Titel und auf den
            // Knopf des Fensters, und beides nimmt EIN Component. Der TEXT steht
            // ausdruecklich nicht hier - er darf und soll mehrzeilig sein.
            RegelnSatz.KNOPF, RegelnSatz.KOPF,
            ShopSatz.SHOP_ABGEBAUT, ShopSatz.SHOP_ANGELEGT, ShopSatz.SHOP_ANGELEGT_BUND,
            ShopSatz.SHOP_FREIGABEN_KOPF, ShopSatz.SHOP_FREIGABEN_LEER,
            ShopSatz.SHOP_FREIGABEN_ZEILE, ShopSatz.SHOP_LISTE_KOPF,
            ShopSatz.SHOP_LISTE_WEITER, ShopSatz.SHOP_LISTE_ZEILE, ShopSatz.SHOP_PREIS_KAUF,
            ShopSatz.SHOP_PREIS_VERKAUF,
            SpielzeitSatz.GESAMT, SpielzeitSatz.HEUTE, SpielzeitSatz.HINWEIS,
            SpielzeitSatz.KOPF_FREMD, SpielzeitSatz.KOPF_SELBST, SpielzeitSatz.TAETIG,
            SpielzeitSatz.ZUSTAND_GEZAEHLT, SpielzeitSatz.ZUSTAND_UNTAETIG,
            TruheSatz.BEHALTEN, TruheSatz.BESETZT, TruheSatz.DECKEL, TruheSatz.GESTOERT,
            TruheSatz.OFFEN, TruheSatz.OFFEN_FREI, TruheSatz.VERBOTEN, TruheSatz.ZURUECK,
            WerderSatz.LEER, WerderSatz.ZEILE);


    /**
     * Die Saetze, die das Programm nur ueber eine VARIABLE holt - und auch sie muessen
     * genau eine Zeile sein.
     *
     * <p><b>Warum sie nicht in {@link #CHATZEILEN} stehen koennen:</b> Jene Liste wird
     * gegen die Holstellen des Quelltextes gehalten ({@code VorlageTest} 10j), und eine
     * Holstelle wie {@code sprache.eineZeile(eintrag.name(), ...)} nennt keinen Satz -
     * sie nennt ein Feld. Ein Eintrag dort waere fuer jene Tafel ein Eintrag ohne
     * Holstelle.
     *
     * <p><b>Warum die Menge trotzdem genau bekannt ist:</b> Durch jene Variablen laufen
     * ausschliesslich die Saetze einer {@link Menuetafel}, und eine Tafel traegt nur
     * {@link MenueSatz}-Konstanten. Was hier stehen MUSS, ist damit abzaehlbar: Titel,
     * Name und Erklaerung jedes Eintrags. Tafel 10j prueft beide Richtungen - kein
     * MenueSatz dieser Art ohne Eintrag, kein Eintrag, der kein MenueSatz ist.
     *
     * <p>Der Riegel selbst ist derselbe: Eine Umbruchmarke in einem dieser Saetze ist
     * beim LADEN eine Klage. Im Menue waere sie besonders teuer - ein Wurf beim Bauen des
     * Gegenstands liesse das Fenster zu, und der Spieler las nur eine rote Zeile.
     */
    private static final Set<Satz> UEBER_VARIABLE = Set.of(
            MenueSatz.TITEL,
            MenueSatz.PARZELLE_NAME, MenueSatz.PARZELLE_ERKLAERUNG,
            MenueSatz.MARK_NAME, MenueSatz.MARK_ERKLAERUNG,
            MenueSatz.RANG_NAME, MenueSatz.RANG_ERKLAERUNG,
            MenueSatz.BERUF_NAME, MenueSatz.BERUF_ERKLAERUNG,
            MenueSatz.MARKT_NAME, MenueSatz.MARKT_ERKLAERUNG,
            MenueSatz.LADEN_NAME, MenueSatz.LADEN_ERKLAERUNG,
            MenueSatz.FREISTADT_NAME, MenueSatz.FREISTADT_ERKLAERUNG,
            MenueSatz.WERDER_NAME, MenueSatz.WERDER_ERKLAERUNG,
            MenueSatz.WOLKE_NAME, MenueSatz.WOLKE_ERKLAERUNG,
            MenueSatz.HILFE_NAME, MenueSatz.HILFE_ERKLAERUNG,
            MenueSatz.REGELN_NAME, MenueSatz.REGELN_ERKLAERUNG);

    private Einzeiler() {
    }

    /**
     * Holt das Programm diesen Satz als genau eine Chatzeile?
     *
     * <p>Gefragt wird beim LESEN der Sprachdatei: Eine Umbruchmarke in so einem Satz ist
     * eine Klage und damit ein Grund, die ganze Datei abzuweisen - alles oder nichts, wie
     * bei jedem anderen Mangel. Besser eine Datei, die nicht geladen wird, als ein
     * Befehl, der in der Hand des Spielers verschwindet.
     */
    static boolean nurEineZeile(Satz schluessel) {
        return CHATZEILEN.contains(schluessel) || UEBER_VARIABLE.contains(schluessel);
    }

    /** Die Liste selbst - fuer die Tafel, die sie gegen den Quelltext haelt. */
    static Set<Satz> chatzeilen() {
        return CHATZEILEN;
    }

    /** Die Saetze, die nur ueber eine Variable geholt werden - fuer dieselbe Tafel. */
    static Set<Satz> ueberVariable() {
        return UEBER_VARIABLE;
    }
}
