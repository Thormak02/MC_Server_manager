package de.freimarken.paper;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Wesen und Gegenstaende fuer die Proben - der Teil der Werkstatt, der nicht Server ist.
 *
 * <p>Gleiche Bauart und gleicher Grundsatz wie {@link Serverprobe} und
 * {@link Spielerprobe}: Beantwortet wird NUR, was gemessen gebraucht wird, und alles
 * andere wirft mit seinem eigenen Namen. Ein stilles {@code null} waere schlimmer - dann
 * liefe die Probe durch einen anderen Zweig und waere gruen, ohne etwas gesehen zu haben.
 *
 * <p><b>Eine eigene Datei und nicht in {@link Serverprobe}</b>: Jene ist der Server, und
 * ein Fisch am Haken gehoert nicht dorthin. Gebraucht wird das hier seit Teil E2, wo vier
 * neue Ereigniszweige Wesen und Gegenstaende lesen.
 *
 * <p><b>Der Gegenstandsstapel ist eine Unterklasse und kein Stellvertreter</b>, und das
 * ist gemessen und nicht gewaehlt: {@code ItemStack} ist eine KLASSE, also kann kein
 * Proxy darauf entstehen - und {@code new ItemStack(Material)} wirft ohne laufenden
 * Server, weil es ueber {@code Material.asItemType} in die Registry greift
 * ({@code ExceptionInInitializerError}). Was ohne Server geht, ist allein der
 * {@code protected ItemStack()} einer Unterklasse; die traegt dann kein Material, und
 * darum beantwortet diese hier {@code getType} selbst.
 */
final class Wesenprobe {

    private Wesenprobe() {
    }

    /**
     * Ein Gegenstandsstapel, der nur seine Art kennt.
     *
     * <p>Mehr braucht kein Zweig dieses Projekts: Der Fischzug liest die ART des Fangs,
     * und daran haengt der Lohnsatz.
     */
    static final class Probestapel extends ItemStack {

        private final Material art;

        Probestapel(Material art) {
            this.art = art;
        }

        @Override
        public Material getType() {
            return art;
        }

        @Override
        public String toString() {
            return "Probestapel " + art;
        }
    }

    /** Ein Gegenstand am Boden - das, was beim Angeln aus dem Wasser kommt. */
    static Item gegenstand(Material art) {
        ItemStack stapel = new Probestapel(art);
        return (Item) Proxy.newProxyInstance(Wesenprobe.class.getClassLoader(),
                new Class<?>[] {Item.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getItemStack" -> stapel;
                    case "getType" -> EntityType.ITEM;
                    case "toString" -> "Gegenstandsprobe " + art;
                    case "hashCode" -> art.hashCode();
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Wesenprobe beantwortet Item." + methode.getName()
                                    + " nicht. Der Zweig, den du gerade durchlaeufst,"
                                    + " braucht sie - also gehoert sie hierher, mit einer"
                                    + " Antwort, die die Probe selbst mitgibt.");
                });
    }

    /**
     * Ein Wesen am Haken - fuer den Fall, der KEIN Fang ist.
     *
     * <p>Gebraucht, weil {@code PlayerFishEvent.getCaught()} eine {@code Entity} liefert
     * und nicht einen Gegenstand: Ein gehakter Tintenfisch kommt genauso herein, und ein
     * Zweig, der darauf castet, wirft.
     */
    static Entity wesenAmHaken() {
        return (Entity) Proxy.newProxyInstance(Wesenprobe.class.getClassLoader(),
                new Class<?>[] {Entity.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getType" -> EntityType.SQUID;
                    case "toString" -> "Wesenprobe am Haken";
                    case "hashCode" -> 4711;
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Wesenprobe beantwortet Entity." + methode.getName()
                                    + " nicht");
                });
    }

    /**
     * Die Angel im Wasser.
     *
     * @param imOffenenWasser was {@code isInOpenWater} sagt - daran haengt, ob ein
     *                        Ein-Block-Loch zahlt
     */
    static FishHook angel(boolean imOffenenWasser) {
        return (FishHook) Proxy.newProxyInstance(Wesenprobe.class.getClassLoader(),
                new Class<?>[] {FishHook.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "isInOpenWater" -> imOffenenWasser;
                    case "getType" -> EntityType.FISHING_BOBBER;
                    case "toString" -> "Angelprobe offen=" + imOffenenWasser;
                    case "hashCode" -> Boolean.hashCode(imOffenenWasser);
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Wesenprobe beantwortet FishHook." + methode.getName()
                                    + " nicht");
                });
    }

    /**
     * Ein lebendes Wesen mit Art, Toeter und Entstehungsgrund.
     *
     * @param art    die Wesensart - sie wird zum Namen in der Lohnliste
     * @param toeter wer es erschlagen hat, oder {@code null} fuer Fall, Lava und
     *               Ersticken
     * @param grund  woraus es entstanden ist, oder {@code null} - daran haengt, ob es
     *               ueberhaupt zahlt
     */
    static LivingEntity wesen(EntityType art, Player toeter,
                              CreatureSpawnEvent.SpawnReason grund) {
        UUID kennung = UUID.randomUUID();
        return (LivingEntity) Proxy.newProxyInstance(Wesenprobe.class.getClassLoader(),
                new Class<?>[] {LivingEntity.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getType" -> art;
                    case "getKiller" -> toeter;
                    case "getEntitySpawnReason" -> grund;
                    case "getUniqueId" -> kennung;
                    case "getName" -> art.name();
                    case "toString" -> "Wesenprobe " + art;
                    case "hashCode" -> kennung.hashCode();
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "Wesenprobe beantwortet LivingEntity." + methode.getName()
                                    + " nicht. Der Zweig, den du gerade durchlaeufst,"
                                    + " braucht sie - also gehoert sie hierher.");
                });
    }
}
