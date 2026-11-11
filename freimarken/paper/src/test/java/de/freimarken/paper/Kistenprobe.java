package de.freimarken.paper;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Eine Kiste als Attrappe - Plaetze, Zuschauer, Halter und die Sicht darauf.
 *
 * <p><b>Eine eigene Datei</b> [11.11.2026]: Seit der Einrichtungsschutz dazugekommen ist,
 * pruefen ZWEI Wachen am {@code InventoryOpenEvent} - die Truhenwache und die
 * Einrichtungswache -, und beide brauchen dieselbe Kiste. Zwei Abschriften derselben
 * Attrappe laufen auseinander, und dann prueft die eine Probe einen Griff, den die andere
 * nie sieht.
 *
 * <p><b>Warum eine Attrappe und kein echtes Inventar:</b> {@code Bukkit.createInventory}
 * braucht einen laufenden Server. Was hier beantwortet wird, ist genau das, was die
 * gepruefen Zweige fragen; alles andere wirft mit seinem eigenen Namen - ein stilles
 * {@code null} liesse eine Probe durch einen anderen Zweig laufen und gruen melden, ohne
 * etwas gesehen zu haben.
 */
final class Kistenprobe {

    private Kistenprobe() {
    }

    /**
     * Das Inventar einer Kiste an dieser Stelle - Plaetze, Zuschauer, Halter.
     *
     * <p>{@code getContents} gibt die LEBENDE Liste der Plaetze und nicht eine Kopie, wie
     * echtes Bukkit es tut. Der Unterschied ist hier keiner: Gelesen wird daraus, und
     * geschrieben wird ueber {@code setItem} - genau wie im gepruefen Zweig.
     */
    static Inventory kiste(World welt, int x, int y, int z, ItemStack[] plaetze,
            List<HumanEntity> zuschauer) {
        InventoryHolder haelt = halter(welt, x, y, z);
        return (Inventory) Proxy.newProxyInstance(
                Kistenprobe.class.getClassLoader(),
                new Class<?>[] {Inventory.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    // getHolder() und getHolder(false) heissen gleich - der
                    // Stellvertreter sieht den Unterschied nicht und braucht ihn nicht.
                    case "getHolder" -> haelt;
                    case "getContents" -> plaetze;
                    case "getViewers" -> zuschauer;
                    case "getSize" -> plaetze.length;
                    case "setItem" -> {
                        plaetze[(Integer) werte[0]] = (ItemStack) werte[1];
                        yield null;
                    }
                    // Gebraucht vom Zuruecklegen ueber dem Deckel: Bukkit nimmt dort
                    // einen Stapel und keine Zahl, also geht der Weg durch addItem.
                    case "addItem" -> hinein((ItemStack[]) werte[0], plaetze);
                    case "toString" -> "Kistenprobe " + x + "/" + y + "/" + z;
                    case "hashCode" -> (x * 31 + y) * 31 + z;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Die Kistenprobe beantwortet " + methode.getName()
                                    + " nicht - gefragt werden darf nach Halter,"
                                    + " Plaetzen und Zuschauern");
                });
    }

    /**
     * Eine Kistenhaelfte: {@code BlockState} UND {@code InventoryHolder}.
     *
     * <p>Der Block kommt aus {@link Serverprobe} - die eine Wahrheit darueber, was ein
     * Block beantwortet. Die Huelle darum traegt die zweite Schnittstelle, die echtes
     * Bukkit mitbringt: Ohne sie wirft {@code getHolder} beim Umwandeln auf
     * {@code InventoryHolder}.
     *
     * <p>Eine DOPPELKISTE laesst sich so nicht nachbauen: {@code DoubleChest} ist in
     * Bukkit eine Klasse und keine Schnittstelle, und ein Stellvertreter kann nur
     * Schnittstellen tragen. Was bei zwei Haelften gilt, prueft darum keine Probe dieser
     * Art - es steht als Satz an den Wachen, die {@code Shopware.bloeckeVon} rufen.
     */
    static InventoryHolder halter(World welt, int x, int y, int z) {
        Block block = Serverprobe.zustand(welt, null, x, y, z).getBlock();
        return (InventoryHolder) Proxy.newProxyInstance(
                Kistenprobe.class.getClassLoader(),
                new Class<?>[] {BlockState.class, InventoryHolder.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getBlock" -> block;
                    case "toString" -> "Halterprobe " + x + "/" + y + "/" + z;
                    case "hashCode" -> (x * 31 + y) * 31 + z;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Die Halterprobe beantwortet " + methode.getName()
                                    + " nicht - gefragt werden darf nur, WO sie steht");
                });
    }

    /**
     * Ein Inventar OHNE Kiste darunter - der Rucksack, eine Werkbank, das Endlager.
     *
     * <p>{@code Shopware.bloeckeVon} gibt dafuer eine leere Liste, und jede Wache am
     * {@code InventoryOpenEvent} muss das aushalten: Ohne diesen Fall waere in der
     * Ladenwelt jedes Inventar zu - auch das eigene.
     */
    static Inventory ohneBlock() {
        return (Inventory) Proxy.newProxyInstance(
                Kistenprobe.class.getClassLoader(),
                new Class<?>[] {Inventory.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getHolder" -> null;
                    case "getContents" -> new ItemStack[36];
                    case "getSize" -> 36;
                    case "toString" -> "Inventarprobe ohne Kiste";
                    case "hashCode" -> 41;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Dieses Inventar hat keine Kiste - gefragt werden darf nach"
                                    + " Halter, Plaetzen und Groesse. Gefragt wurde "
                                    + methode.getName());
                });
    }

    /** Die Sicht auf ein Inventar - mehr fragen die Ereignisse nicht. */
    static InventoryView sichtAuf(HumanEntity wer, Inventory oben) {
        return (InventoryView) Proxy.newProxyInstance(
                Kistenprobe.class.getClassLoader(),
                new Class<?>[] {InventoryView.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getPlayer" -> wer;
                    case "getTopInventory" -> oben;
                    case "toString" -> "Sicht von " + wer;
                    case "hashCode" -> 31;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Die Sichtprobe beantwortet " + methode.getName()
                                    + " nicht - gefragt werden darf, WER oeffnet und WAS");
                });
    }

    /** Ein Wesen, das kein Spieler ist - nur dafuer da, keiner zu sein. */
    static HumanEntity keinSpieler() {
        return (HumanEntity) Proxy.newProxyInstance(
                Kistenprobe.class.getClassLoader(),
                new Class<?>[] {HumanEntity.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "toString" -> "Wesen ohne Spieler";
                    case "hashCode" -> 37;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Dieses Wesen soll GAR NICHTS beantworten - es ist nur dafuer"
                                    + " da, kein Spieler zu sein. Gefragt wurde "
                                    + methode.getName());
                });
    }

    /**
     * Legt Stapel in die Kiste und gibt zurueck, was nicht hineinpasste - die Form, in
     * der Bukkit antwortet. Jeder Stapel bekommt einen eigenen Platz.
     */
    static Map<Integer, ItemStack> hinein(ItemStack[] stapel, ItemStack[] plaetze) {
        Map<Integer, ItemStack> blieben = new HashMap<>();
        for (int i = 0; i < stapel.length; i++) {
            int frei = -1;
            for (int platz = 0; platz < plaetze.length && frei < 0; platz++) {
                if (plaetze[platz] == null) {
                    frei = platz;
                }
            }
            if (frei < 0) {
                blieben.put(i, stapel[i]);
                continue;
            }
            plaetze[frei] = stapel[i];
        }
        return blieben;
    }
}
