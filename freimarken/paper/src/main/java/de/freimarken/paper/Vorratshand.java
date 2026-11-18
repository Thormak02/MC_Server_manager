package de.freimarken.paper;

import de.freimarken.kern.Aufgabenbuch;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Was ein Spieler traegt - die Bukkit-Haelfte von {@link Aufgabenbuch.Vorrat}.
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
    static Aufgabenbuch.Vorrat fuer(Player spieler) {
        Inventory inventar = spieler.getInventory();
        return new Aufgabenbuch.Vorrat() {

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

    /** Aus dem Namen der Einstellungsdatei die Art - oder {@code null}. */
    private static Material art(String gegenstand) {
        return gegenstand == null
                ? null
                : Material.getMaterial(gegenstand.trim().toUpperCase(Locale.ROOT));
    }
}
