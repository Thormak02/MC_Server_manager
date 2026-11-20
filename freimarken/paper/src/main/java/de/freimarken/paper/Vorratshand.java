package de.freimarken.paper;

import de.freimarken.kern.Aufgabenbuch;
import de.freimarken.kern.Vorrat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Was ein Spieler traegt - die Bukkit-Haelfte von {@link Vorrat}.
 *
 * <p>Diese Klasse ist die NAHT: {@code new ItemStack} greift in Minecrafts
 * Gegenstandsverzeichnis und wirft ohne laufenden Server, und
 * {@code Inventory.getStorageContents} braucht einen Spieler. Dahinter liegt das
 * {@link Aufgabenbuch} und rechnet mit drei Zahlen - zaehlen, nehmen, zurueckgeben -, und
 * genau deshalb ist jeder seiner Zweige ohne Server durchlaufbar, auch der teuerste: das
 * Zurueckgeben nach einem halben Nehmen.
 *
 * <p><b>{@code getStorageContents} und nicht {@code getContents}</b>, und das ist
 * dieselbe Lehre, die in {@code Shopware.bestand} ausgeschrieben steht: Beim Spieler
 * enthaelt jenes auch vier Ruestungsplaetze und die zweite Hand. Gezaehlt waere ein Stueck
 * dort VORHANDEN und, wenn {@code removeItem} es nicht anfasst, nicht ENTNEHMBAR - und das
 * waere eine Aufgabe, die gezaehlt gelingt und genommen scheitert.
 *
 * <p><b>Nur die ART wird verglichen, nicht der Gegenstand.</b> Eine verzauberte
 * Spitzhacke, ein umbenanntes Brot und ein beschaedigtes Schwert zaehlen mit. Fuer die
 * Herausforderungen ist das richtig: Verlangt werden Rohstoffe und Mengen, und wer sein
 * Eisen einem Stapel mit Namen entnimmt, hat dieselbe Arbeit gehabt. (Der Kistenshop
 * urteilt strenger, und dort ist das auch richtig - dort wird gehandelt.)
 *
 * <p><b>Und seit Teil G gilt das auch fuer das Muenzhaus</b> [20.11.2026]: Das ist
 * ebenfalls Handel, und darum holt es NIE etwas zurueck - es fragt vorher nach dem
 * {@link Vorrat#fehlenderPlatz} und erstattet im Fehlerfall das GELD. Ein nimm dort
 * koennte das verzauberte Exemplar des Spielers treffen und ihm das gelieferte blanke
 * lassen.
 */
final class Vorratshand {

    private Vorratshand() {
    }

    /**
     * Der Vorrat dieses Spielers.
     *
     * <p>Ein Gegenstandsname, den dieser Server nicht kennt, zaehlt als NICHT VORHANDEN
     * und laesst sich nicht nehmen. Das ist die sichere Richtung: Der Leser der
     * Einstellungsdatei hat jeden Namen geprueft, also kann das nur eine Liste sein, die
     * auf einem anderen Weg hereinkam - und dann soll die Aufgabe unerfuellbar sein und
     * nicht gratis.
     */
    static Vorrat fuer(Player spieler) {
        Inventory inventar = spieler.getInventory();
        return new Vorrat() {

            @Override
            public int zaehle(String gegenstand) {
                Material art = art(gegenstand);
                if (art == null) {
                    return 0;
                }
                int summe = 0;
                for (ItemStack platz : inventar.getStorageContents()) {
                    if (platz != null && platz.getType() == art) {
                        summe += platz.getAmount();
                    }
                }
                return summe;
            }

            @Override
            public int nimm(String gegenstand, int stueck) {
                return bewege(gegenstand, stueck, true);
            }

            @Override
            public int lege(String gegenstand, int stueck) {
                return bewege(gegenstand, stueck, false);
            }

            /**
             * Was von dieser Lieferung NICHT hineinpasst - gerechnet, nicht versucht.
             *
             * <p><b>Auf einer KOPIE der Lagerplaetze</b>, und zwar nur Art und Anzahl je
             * Platz: Bukkit hat keinen Probelauf fuer {@code addItem}, und ein wirklicher
             * Versuch mit anschliessendem Zurueckholen waere genau der Rueckweg, den das
             * Muenzhaus nicht haben darf - {@code nimm} vergleicht nur die ART und naehme
             * womoeglich das bessere Exemplar des Spielers.
             *
             * <p><b>Die Reihenfolge ist die von {@code addItem}</b>: erst die angefangenen
             * Stapel derselben Art auffuellen, dann freie Plaetze belegen. Andersherum
             * gerechnet meldete ein fast voller Rucksack zu wenig Platz, obwohl die
             * Lieferung in die Luecken der eigenen Stapel passt.
             *
             * <p>Ein Gegenstand, den dieser Server nicht kennt oder der nur als BLOCK
             * existiert, zaehlt vollstaendig als fehlender Platz - dieselbe sichere
             * Richtung wie bei {@code zaehle}: Was nicht ankommen kann, soll den Kauf
             * verhindern und nicht gratis gelingen.
             *
             * <p>Gerechnet wird in {@link Vorratshand#nichtUnterzubringen}; hier steht
             * nur, was dafuer aus Bukkit zu holen ist - drei Zahlen je Platz und die
             * Stapelgroesse je Zeile.
             */
            @Override
            public int fehlenderPlatz(Map<String, Integer> gegenstaende) {
                if (gegenstaende == null || gegenstaende.isEmpty()) {
                    return 0;
                }
                ItemStack[] plaetze = inventar.getStorageContents();
                Material[] arten = new Material[plaetze.length];
                int[] mengen = new int[plaetze.length];
                boolean[] aufstockbar = new boolean[plaetze.length];
                for (int platz = 0; platz < plaetze.length; platz++) {
                    // Gegen Material.AIR verglichen und nicht mit isAir(): Jene Methode
                    // greift in Minecrafts Verzeichnis und wirft ohne laufenden Server.
                    if (plaetze[platz] != null
                            && plaetze[platz].getType() != Material.AIR) {
                        arten[platz] = plaetze[platz].getType();
                        mengen[platz] = plaetze[platz].getAmount();
                        // DIE DRITTE ZAHL JE PLATZ, nachgetragen am 20.11.2026: Ein
                        // Stapel mit eigener Gestalt - umbenannt, verzaubert, beschrieben
                        // - ist fuer addItem KEIN Aufstockziel, denn Bukkits firstPartial
                        // fragt isSimilar und vergleicht die Komponenten. Er belegt
                        // seinen Platz und nimmt nichts an.
                        aufstockbar[platz] = !plaetze[platz].hasItemMeta();
                    }
                }
                int fehlt = 0;
                for (var zeile : gegenstaende.entrySet()) {
                    Material art = art(zeile.getKey());
                    fehlt += nichtUnterzubringen(arten, mengen, aufstockbar, art,
                            zeile.getValue() == null ? 0 : zeile.getValue(),
                            art == null ? 0 : art.getMaxStackSize());
                }
                return fehlt;
            }

            /**
             * Nehmen und Legen in EINER Methode - der Unterschied ist ein Aufruf.
             *
             * <p>Beide Bukkit-Methoden melden den REST und nicht den Erfolg, beide fuegen
             * teilweise ein, und beide veraendern das uebergebene Exemplar waehrend der
             * Arbeit. Darum wird nur die zurueckgegebene Karte gelesen - dieselbe
             * Vorsicht wie in {@code Shopware.bewege}.
             */
            private int bewege(String gegenstand, int stueck, boolean nehmen) {
                Material art = art(gegenstand);
                if (art == null || stueck < 1) {
                    return 0;
                }
                int rest = 0;
                for (ItemStack portion : portionen(art, stueck)) {
                    var uebrig = nehmen
                            ? inventar.removeItem(portion)
                            : inventar.addItem(portion);
                    for (ItemStack liegengeblieben : uebrig.values()) {
                        rest += liegengeblieben.getAmount();
                    }
                }
                return stueck - rest;
            }
        };
    }

    /**
     * Die Menge in Stapel zerlegt - je Portion ein eigenes Exemplar.
     *
     * <p>Wie in {@code Shopware.portionen}: Ein Exemplar ueber seiner Stapelgroesse ist
     * kein Zustand, den Minecraft kennt, und was die Inventarmethoden damit tun, ist
     * nicht zugesagt. Eine Aufgabe darf 3.456 Stueck verlangen - das sind 54 Stapel.
     */
    private static List<ItemStack> portionen(Material art, int stueck) {
        List<ItemStack> teile = new ArrayList<>();
        int offen = stueck;
        int stapel = Math.max(1, art.getMaxStackSize());
        while (offen > 0) {
            int diesmal = Math.min(offen, stapel);
            teile.add(new ItemStack(art, diesmal));
            offen -= diesmal;
        }
        return teile;
    }

    /**
     * Legt EINE Zeile in die Kopie der Lagerplaetze und meldet, was uebrig bleibt.
     *
     * <p><b>Eine eigene statische Methode</b>, und das ist der eigentliche Nachtrag vom
     * 20.11.2026: Vorher stand diese Schleife in der anonymen Klasse, also hinter einem
     * {@code Player} und hinter {@code getMaxStackSize} - und darum konnte KEINE der
     * 4453 Proben sie durchlaufen. Der Fehler darin (der umbenannte Stapel als
     * Aufstockziel) war nicht schwer zu sehen; er war nur nicht zu sehen. Die
     * Stapelgroesse kommt jetzt als Zahl herein, und damit rechnet eine Probe sie nach.
     *
     * <p><b>Die Reihenfolge ist die von {@code addItem}</b>: erst die angefangenen Stapel
     * derselben Art auffuellen, dann freie Plaetze belegen. Andersherum gerechnet meldete
     * ein fast voller Rucksack zu wenig Platz, obwohl die Lieferung in die Luecken der
     * eigenen Stapel passt.
     *
     * <p><b>Ein Stapel ohne eigene Gestalt und eine Stapelgroesse von 0</b> sind die zwei
     * Wege in die PESSIMISTISCHE Richtung: Was die Rechnung nicht sicher unterbringen
     * kann, zaehlt als fehlender Platz. Diese Richtung ist Absicht - sie sagt den Kauf
     * ab, bevor sich etwas bewegt. Die optimistische Richtung kostet dagegen echte
     * Goldmark: Sie bucht ab, liefert halb und erstattet den ganzen Preis.
     *
     * @param arten       je Platz die Art oder {@code null} fuer leer; wird VERAENDERT
     * @param mengen      je Platz die Anzahl; wird VERAENDERT
     * @param aufstockbar je Platz, ob ein weiteres Stueck derselben Art dazu darf
     * @param art         was geliefert wird, oder {@code null} fuer unbekannt
     * @param stueck      wie viele Stueck
     * @param stapel      die Stapelgroesse dieser Art - 0 heisst: passt nirgends hin
     */
    static int nichtUnterzubringen(Material[] arten, int[] mengen, boolean[] aufstockbar,
                                   Material art, int stueck, int stapel) {
        int offen = Math.max(0, stueck);
        if (art == null || stapel < 1) {
            return offen;
        }
        for (int platz = 0; platz < arten.length && offen > 0; platz++) {
            if (arten[platz] == art && aufstockbar[platz] && mengen[platz] < stapel) {
                int passt = Math.min(offen, stapel - mengen[platz]);
                mengen[platz] += passt;
                offen -= passt;
            }
        }
        for (int platz = 0; platz < arten.length && offen > 0; platz++) {
            if (arten[platz] == null) {
                arten[platz] = art;
                mengen[platz] = Math.min(offen, stapel);
                // Ein eben belegter Platz ist aufstockbar - das Exemplar, das dort
                // landet, kommt aus der Lieferung und hat keine eigene Gestalt.
                aufstockbar[platz] = true;
                offen -= mengen[platz];
            }
        }
        return offen;
    }

    /**
     * Aus dem Namen der Einstellungsdatei die Art - oder {@code null}.
     *
     * <p><b>Nur ein GEGENSTAND zaehlt</b>, nachgetragen am 20.11.2026 und wortgleich mit
     * {@code Shopware.ausNamen}: {@code potatoes} ist der Feldblock, {@code potato} der
     * Gegenstand; dasselbe gilt fuer {@code carrots}, {@code beetroots}, {@code water}
     * und das Kopfstueck einer Tuer. Der Leser der Einstellungsdatei kann diese Frage
     * nicht stellen - {@code isItem} greift in Minecrafts Verzeichnis und wirft ohne
     * laufenden Server -, also faellt sie hier, an der Naht. Ein Posten auf einem reinen
     * Block sagt damit ab (KEIN PLATZ, vor der Zahlung) statt leer zu liefern, und der
     * Startbericht nennt ihn beim Namen.
     *
     * <p>{@code isItem} deckt die Luft mit ab: Weder {@code AIR} noch {@code CAVE_AIR}
     * ist ein Gegenstand.
     */
    private static Material art(String gegenstand) {
        if (gegenstand == null) {
            return null;
        }
        Material art = Material.getMaterial(gegenstand.trim().toUpperCase(Locale.ROOT));
        return art == null || !art.isItem() ? null : art;
    }
}
