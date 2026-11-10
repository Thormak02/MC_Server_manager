package de.freimarken.paper;

import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopweg;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.Shopschild;
import de.freimarken.kern.Warenprobe;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die Kistenshops - die eine Stelle, die anlegt und abbaut.
 *
 * <p>Kein Bukkit, derselbe Schnitt wie bei {@link Mietbuch} und {@link Mietlauf}. Das
 * Spiel kommt ueber zwei Schnittstellen herein: {@link Warenleser} loest eine Ware auf,
 * {@link Zugriff} sagt, ob ein Spieler an einer Stelle darf. Alles andere - welcher
 * Bescheid, welche Reihenfolge, welche Absage - ist ohne Server pruefbar.
 *
 * <p><b>Die Kistenstelle wird eigens geprueft, und das ist eine Luecke der Vorbilder.</b>
 * Ein Schild haengt auf eigenem Grund, die Kiste steht einen Block weiter auf fremdem:
 * Dann verkaufte dieser Shop die Ware eines anderen. ChestShop deckt das nur ueber
 * Fremdplugins ab ({@code CHECK_ACCESS_FOR_SHOP_USE}, LWC, WorldGuard, Residence);
 * QuickShop #2539 nennt denselben Fall von der anderen Seite - "they can place a hopper
 * underneath it to obtain the items". Hier ist die Frage Teil des Anlegens: Wer an der
 * KISTE nicht handeln darf, bekommt dort keinen Shop.
 *
 * <p><b>Nichts hiervon wirft nach aussen.</b> Jede Tat antwortet mit einem Bescheid, auch
 * wenn die Datenbank schweigt. Ein Wurf aus dem Schildschreiben liesse den Spieler mit
 * einem halben Schild stehen.
 */
final class Shopbuch {

    /** Was aus einer Tat geworden ist. */
    enum Bescheid {
        /** Der Shop steht. */
        ANGELEGT,
        /** Der Shop ist weg. */
        ABGEBAUT,
        /** Das Schild ist falsch beschriftet - {@link Anlage#fehler} nennt, wie. */
        SCHILD_FEHLERHAFT,
        /** Neben dem Schild steht keine Kiste. */
        KEINE_KISTE,
        /** An der Kiste darf dieser Spieler nicht handeln - sie gehoert einem anderen. */
        FREMDE_KISTE,
        /**
         * An dieser Kiste haengt schon der Shop eines anderen.
         *
         * <p>Ein anderer Fall als {@link #FREMDE_KISTE}: Dort gehoert der ORT einem
         * anderen, hier die KISTE. Draussen vor der Stadt gehoert kein Ort jemandem,
         * und dann ist das Shopschild die einzige Besitzerklaerung, die eine Kiste
         * tragen kann.
         */
        FREMDER_SHOP,
        /** An diesem Schild haengt schon ein Shop. */
        SCHON_EIN_SHOP,
        /** Das Fragezeichen, aber die Kiste ist leer oder haelt mehrere Arten. */
        WARE_UNKLAR,
        /** Den getippten Gegenstand gibt es nicht. */
        WARE_UNBEKANNT,
        /** Ein Bundshop, aber der Spieler hat das Recht dafuer nicht. */
        KEIN_BUNDRECHT,
        /** Hier haengt gar kein Shop - beim Abbauen. */
        KEIN_SHOP,
        /** Die Datenbank hat geschwiegen. Steht im Protokoll. */
        GESTOERT
    }

    /**
     * Das Ergebnis von {@link #anlege}.
     *
     * @param shop   bei {@link Bescheid#ANGELEGT} der neue Shop, sonst {@code null}
     * @param fehler bei {@link Bescheid#SCHILD_FEHLERHAFT} der Grund, sonst {@code null}
     * @param zeile  bei {@link Bescheid#SCHILD_FEHLERHAFT} die Zeilennummer 1 bis 4,
     *               sonst 0. Ein Spieler soll lesen, WO er nachsehen muss
     */
    record Anlage(Bescheid bescheid, Kistenshop shop, Shopschild.Fehler fehler,
                  int zeile) {
    }

    /**
     * Loest eine Ware auf - die eine Bukkit-Naht fuer Gegenstaende.
     *
     * <p>Zwei Wege, und beide kommen von Lumia: ein getippter Name oder ein Fragezeichen.
     * Der zweite ist seit der Entscheidung vom 08.11.2026 der Normalweg, denn verzauberte
     * Ausruestung passt auf kein Schild.
     */
    interface Warenleser {

        /**
         * Die Ware aus einem getippten Namen - oder {@code null}, wenn es sie nicht gibt.
         *
         * <p>Die Probe ist ein UNVERAENDERTES Exemplar: Wer {@code DIAMOND_PICKAXE}
         * tippt, handelt eine gewoehnliche Spitzhacke und keine verzauberte. Wer eine
         * verzauberte handeln will, legt sie in die Kiste und tippt ein Fragezeichen -
         * das ist der ganze Grund, warum es diesen zweiten Weg gibt.
         */
        Warenprobe ausNamen(String getippt);

        /**
         * Die EINE Warenart in dieser Kiste - oder {@code null}.
         *
         * <p>{@code null} heisst: leer, oder mehr als eine Art darin. Das ist Lumias
         * Regel, woertlich - "funktioniert jedoch nur, wenn von diesem Item nur ein
         * Gegenstand in der Kiste enthalten ist". Mit zwei Arten waere nicht zu
         * entscheiden, welche gemeint ist, und ein geratener Shop verkauft die falsche
         * Ware.
         */
        Warenprobe ausKiste(String welt, int x, int y, int z);

        /**
         * Ob an dieser Stelle ein Behaelter steht, der ein Shop sein darf.
         *
         * <p>Kiste, Redstonetruhe und Fass - nicht Ofen und Spender (QuickShops eigene
         * Warnliste nennt sie: mehrere Slots, Redstone-Zugriff) und ausdruecklich keine
         * Shulkerkiste: QuickShop #1889 liess mit einer Shulkerkiste als Shopblock
         * Shulker in Shulker, gegen das Vanilla-Verhalten.
         */
        boolean taugtAlsShopkiste(String welt, int x, int y, int z);
    }

    /**
     * Ob ein Spieler an einer Stelle handeln darf - die zweite Bukkit-Naht.
     *
     * <p>Dahinter liegt {@code Schutzwache.darfHandeln}, also derselbe Entscheid, der
     * ueber jeden Blockklick faellt. Eine zweite Rechtefrage daneben liefe auseinander -
     * und dann duerfte jemand einen Shop anlegen, wo er nicht bauen darf.
     */
    interface Zugriff {
        boolean darf(UUID wer, String welt, int x, int y, int z);
    }

    private final Shopablage ablage;
    private final Warenleser waren;
    private final Zugriff zugriff;
    private final Logger protokoll;

    Shopbuch(Shopablage ablage, Warenleser waren, Zugriff zugriff, Logger protokoll) {
        this.ablage = ablage;
        this.waren = waren;
        this.zugriff = zugriff;
        this.protokoll = protokoll;
    }

    /**
     * Legt einen Shop an - aus einem beschriebenen Schild und der Kiste daneben.
     *
     * <p><b>Die Reihenfolge der Absagen ist die der Kosten</b>, wie beim Mieten: erst das
     * Schild selbst (keine Abfrage), dann die Kiste in der Welt, dann das Recht, dann die
     * Ablage. Ein Spieler, der sich vertippt hat, bekommt seine Absage, ohne dass die
     * Datenbank gefragt wurde.
     *
     * <p><b>Eine Ausnahme davon ist die WARE, und sie steht zuletzt</b> [08.11.2026]:
     * Die zwei Ablagefragen sind eine Map im Arbeitsspeicher, das Lesen der Kiste ist ein
     * Griff in die Welt - und viel wichtiger ist, welche Absage der Spieler LIEST. Haengt
     * an der Kiste schon ein fremder Shop, soll das dastehen und nicht "die Kiste sagt
     * nicht, was du handeln willst", bloss weil der fremde Shop gerade ausverkauft ist.
     *
     * @param wer        wer das Schild beschrieben hat
     * @param welt       die Welt des Schildes
     * @param kisteX     wo die Kiste steht - von der Wache gefunden, weil nur sie die
     *                   sechs Nachbarn des Schildes ansehen kann
     * @param darfBund   ob dieser Spieler Bundshops anlegen darf
     * @param darfUeberall ob er {@code freimarken.bauen.ueberall} hat. Das Teamrecht
     *                   darf ein zweites Schild an eine fremde Shopkiste haengen,
     *                   damit ein verlassener Shop erreichbar bleibt. Hereingegeben
     *                   und nicht hier erfragt: Diese Klasse sieht keinen
     *                   {@code Player}, und genau deshalb ist sie ohne Server
     *                   pruefbar
     * @param bundWort   das Wort fuer einen Bundshop aus der {@code config.yml}
     */
    Anlage anlege(UUID wer, String welt, int schildX, int schildY, int schildZ,
            int kisteX, int kisteY, int kisteZ, String[] zeilen, boolean darfBund,
            boolean darfUeberall, String bundWort) {
        Shopschild.Ergebnis gelesen = Shopschild.lies(zeilen, bundWort);
        if (!gelesen.gelungen()) {
            return new Anlage(Bescheid.SCHILD_FEHLERHAFT, null, gelesen.fehler(),
                    gelesen.zeile());
        }
        Shopschild.Gelesen schild = gelesen.schild();
        if (schild.bundshop() && !darfBund) {
            return absage(Bescheid.KEIN_BUNDRECHT);
        }
        // Die Kiste in der WELT, bevor die Ablage gefragt wird: Ein Schild ohne Kiste
        // daneben ist der haeufigste Fehlversuch, und er kostet keine Abfrage.
        if (!waren.taugtAlsShopkiste(welt, kisteX, kisteY, kisteZ)) {
            return absage(Bescheid.KEINE_KISTE);
        }
        // Die KISTENSTELLE und nicht die Schildstelle - siehe den Klassenkopf. Dass er am
        // Schild bauen durfte, hat die Schutzwache schon entschieden, als er es setzte;
        // die Kiste kann einem anderen gehoeren.
        if (!schild.bundshop() && !zugriff.darf(wer, welt, kisteX, kisteY, kisteZ)) {
            return absage(Bescheid.FREMDE_KISTE);
        }
        // DIE ZWEI ABLAGEFRAGEN - und sie stehen beide VOR der Warenpruefung.
        //
        // Die zweite ist neu [08.11.2026] und die andere Haelfte des Kistenschutzes:
        // Wer die Kiste nicht OEFFNEN kann, haengt sonst einfach ein eigenes Schild
        // daneben und verkauft die fremde Ware auf sein Konto; mit dem Fragezeichen
        // sogar automatisch die, die drin liegt. Die Pruefung darueber sieht das
        // nicht: zugriff.darf fragt den ORT, und draussen vor der Stadt gehoert kein
        // Ort jemandem - das Shopschild ist dort die einzige Besitzerklaerung, die
        // eine Kiste tragen kann.
        //
        // DIE REIHENFOLGE IST DIE DER LAGE DES SPIELERS, nicht die der Kosten (beide
        // Fragen sind eine Map im Arbeitsspeicher - dahinter liegt
        // ZwischenspeicherShops). Wer sein EIGENES Schild ueberschreibt, soll "hier
        // haengt schon ein Shop" lesen und nicht "da haengt ein fremder": Das Schild
        // ist das, worauf er gerade tippt.
        //
        // UND BEIDE VOR DER WARE, aus demselben Grund: Sonst liest das Fragezeichen
        // die fremde Kiste, und wenn Annas Shop gerade ausverkauft ist, bekaeme Bert
        // "die Kiste sagt nicht, was du handeln willst" zu lesen. Dann wartet er auf
        // Ware, wo kein Warten hilft.
        //
        // Direkt an der Ablage und mit eigenem Fang: Ein Wurf gibt GESTOERT, und dann
        // entsteht kein Shop. Das ist hier die sichere Richtung - ein Schild tippt er
        // neu, eine gekaperte Kiste bekommt niemand zurueck. Die milderen Wege
        // anDemSchild und anDerKiste dieser Klasse passen dafuer NICHT: Jener gibt
        // bei einer Stoerung null und dieser eine leere Liste, und beides heisst hier
        // "nur zu".
        try {
            if (ablage.anDemSchild(welt, schildX, schildY, schildZ) != null) {
                return absage(Bescheid.SCHON_EIN_SHOP);
            }
            // ZWEITES_SCHILD, und das ist der eine Weg, den eine FREIGABE nicht
            // oeffnet: Wer an einer fremden Kiste ein eigenes Schild anhaengen duerfte,
            // verkaufte fremde Ware auf sein Konto - genau das Loch vom 08.11.2026, und
            // der Besitzer saehe nur, dass seine Kiste leerer wird. Die Begruendung
            // steht bei Shopweg.ZWEITES_SCHILD.
            if (Kistenshop.ersterFremder(
                    ablage.anDerKiste(welt, kisteX, kisteY, kisteZ), wer,
                    Shopweg.ZWEITES_SCHILD, darfUeberall, darfBund) != null) {
                return absage(Bescheid.FREMDER_SHOP);
            }
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Shops an " + welt + " " + kisteX + "/"
                    + kisteY + "/" + kisteZ + " nicht lesbar - es entsteht kein"
                    + " Shop", fehler);
            return absage(Bescheid.GESTOERT);
        }
        Warenprobe ware = schild.warenangabe() == Shopschild.Warenangabe.AUS_KISTE
                ? waren.ausKiste(welt, kisteX, kisteY, kisteZ)
                : waren.ausNamen(schild.material());
        if (ware == null) {
            return absage(schild.warenangabe() == Shopschild.Warenangabe.AUS_KISTE
                    ? Bescheid.WARE_UNKLAR
                    : Bescheid.WARE_UNBEKANNT);
        }
        try {
            // Ein neuer Shop hat KEINE Freigaben - niemand ausser dem Besitzer darf an
            // die Kiste, bis er jemanden ausdruecklich eintraegt. Die leere Menge ist
            // hier die sichere Richtung und zugleich die einzige, die ein BUNDSHOP haben
            // darf: Er gehoert niemandem, also kann ihn auch niemand freigeben.
            Kistenshop shop = new Kistenshop(welt, schildX, schildY, schildZ, kisteX,
                    kisteY, kisteZ, schild.bundshop() ? null : wer, schild.menge(),
                    schild.kaufpreis(), schild.verkaufpreis(), ware, Set.of());
            ablage.schreibe(shop);
            protokoll.info("Kistenshop angelegt: " + shop.schluessel() + " - "
                    + shop.menge() + "x " + ware.name() + ", "
                    + Shopschild.preistext(shop.kaufpreis(), shop.verkaufpreis())
                    + (shop.demBund() ? " (Bund)" : " von " + wer));
            return new Anlage(Bescheid.ANGELEGT, shop, null, 0);
        } catch (RuntimeException fehler) {
            // Auch ein IllegalArgumentException aus dem Satz Kistenshop landet hier, und
            // das ist gewollt: Shopschild prueft dasselbe schon und laesst nichts durch,
            // was der Satz abweist. Bleibt doch etwas, ist es ein Fehler des Programms -
            // und dann soll der Spieler eine Absage lesen und keinen halben Shop haben.
            protokoll.log(Level.WARNING, "Kistenshop an " + welt + " " + schildX + "/"
                    + schildY + "/" + schildZ + " liess sich nicht anlegen", fehler);
            return absage(Bescheid.GESTOERT);
        }
    }

    /**
     * Baut den Shop an diesem Schild ab.
     *
     * <p>Gerufen vom Abbauen des Schildes UND vom Herabfallen, wenn der Block darunter
     * verschwindet. Darum ist {@link Bescheid#KEIN_SHOP} kein Fehler: Dieselbe Stelle
     * wird von zwei Seiten gemeldet, und das zweite Mal soll nicht klagen.
     *
     * <p><b>Die Ware bleibt in der Kiste.</b> Ein abgebauter Shop ist kein Verlust - das
     * Schild ist weg, die Kiste steht da, der Besitzer raeumt sie aus. Das ist der
     * Unterschied zur Raeumung eines Ladens, bei der die Ware ins Abholfach wandert: Dort
     * verliert der Spieler seinen Zugang, hier nicht.
     */
    Bescheid baueAb(String welt, int schildX, int schildY, int schildZ) {
        try {
            Kistenshop shop = ablage.anDemSchild(welt, schildX, schildY, schildZ);
            if (shop == null) {
                return Bescheid.KEIN_SHOP;
            }
            ablage.loesche(welt, schildX, schildY, schildZ);
            protokoll.info("Kistenshop abgebaut: " + shop.schluessel() + " - "
                    + shop.ware().name() + " bleibt in der Kiste.");
            return Bescheid.ABGEBAUT;
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Kistenshop an " + welt + " " + schildX + "/"
                    + schildY + "/" + schildZ + " liess sich nicht abbauen", fehler);
            return Bescheid.GESTOERT;
        }
    }

    /**
     * Der Shop an diesem Schild, oder {@code null} - fuer die Wache.
     *
     * <p>Gibt bei einer Stoerung {@code null} und klagt. Das ist die mildeste von drei
     * Moeglichkeiten: Der Klick bewirkt nichts, und was wirklich war, steht im Protokoll.
     */
    Kistenshop anDemSchild(String welt, int x, int y, int z) {
        try {
            return ablage.anDemSchild(welt, x, y, z);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Kistenshop an " + welt + " " + x + "/" + y + "/"
                    + z + " nicht lesbar", fehler);
            return null;
        }
    }

    /**
     * Ob an dieser Stelle eine SHOPKISTE steht - der heisse Pfad.
     *
     * <p>Die Frage, die bei jedem Trichtertakt, jeder Explosion und jedem Kolben gestellt
     * wird. Dahinter liegt {@code ZwischenspeicherShops}, also der Arbeitsspeicher und
     * nicht das Netz.
     *
     * <p><b>Bei einer Stoerung TRUE.</b> Das ist die umgekehrte Richtung als bei
     * {@link #anDemSchild}, und sie ist Absicht: Hier heisst die Antwort "schuetze diese
     * Kiste". Ein stilles Nein bei stummer Ablage waere eine Markthalle, aus der jeder
     * Trichter abzieht, solange die Datenbank schweigt - und eine leergezogene Kiste
     * bekommt niemand zurueck. Dass der Zwischenspeicher gar nicht fragen muss, macht
     * den Fall selten; selten ist nicht nie.
     */
    boolean istShopkiste(String welt, int x, int y, int z) {
        try {
            return !ablage.anDerKiste(welt, x, y, z).isEmpty();
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Shopkisten an " + welt + " " + x + "/" + y + "/"
                    + z + " nicht lesbar - die Kiste wird geschuetzt", fehler);
            return true;
        }
    }

    /**
     * Alle Shops an dieser Kiste - fuer den Kistenschutz.
     *
     * <p>Gibt bei einer Stoerung eine leere Liste. Wer die Kiste SCHUETZEN will, fragt
     * {@link #istShopkiste}; diese Antwort ist fuer die Meldung, wem sie gehoert, und da
     * ist eine leere Liste ehrlicher als eine erfundene.
     */
    List<Kistenshop> anDerKiste(String welt, int x, int y, int z) {
        try {
            return ablage.anDerKiste(welt, x, y, z);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Shopkisten an " + welt + " " + x + "/" + y + "/"
                    + z + " nicht lesbar", fehler);
            return List.of();
        }
    }

    private static Anlage absage(Bescheid bescheid) {
        return new Anlage(bescheid, null, null, 0);
    }
}
