package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Klick in einem Menue - durch das echte Ereignis.
 *
 * <p>Bis zum 21.11.2026 lief diese Wache in keiner Probe. Dass sie es jetzt tut, liegt an
 * derselben Messung wie bei den anderen Wachen: Ein {@code InventoryClickEvent} braucht
 * keinen Server, nur eine Sicht - und die ist ein Stellvertreter, der genau das
 * beantwortet, was gebraucht wird.
 *
 * <p><b>Die wichtigste Probe ist die dritte</b>, und sie wurde erst moeglich, als der
 * doppelte Riegel in der Wache weg war: Ein Klick in den RUCKSACK bei offenem Menue hat
 * ein rohes Fach jenseits der Tafel, aber ein umgerechnetes INNERHALB - mit
 * {@code getSlot} fuehrte der Spieler einen Befehl aus, den er nicht angeklickt hat.
 */
@DisplayName("Die Menuewache")
class MenuewacheTest {

    private static final UUID GERD = Spielerprobe.kennungAus(216);

    /** Drei Zeilen Menue - so viele hat das Hauptmenue. */
    private static final int FAECHER = 27;

    private Menuewache wache;
    private Spielerprobe gerd;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        wache = new Menuewache(Logger.getLogger(MenuewacheTest.class.getName()));
        gerd = new Spielerprobe("Gerd", GERD);
    }

    /** Ein Inventar, das seinen Halter und seine Groesse kennt - mehr wird nicht gefragt. */
    private static Inventory inventar(Object halter, int groesse) {
        return (Inventory) Proxy.newProxyInstance(
                MenuewacheTest.class.getClassLoader(),
                new Class<?>[] {Inventory.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getHolder" -> halter;
                    case "getSize" -> groesse;
                    case "getType" -> InventoryType.CHEST;
                    case "toString" -> "Inventar von " + halter;
                    case "hashCode" -> System.identityHashCode(eigen);
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "MenuewacheTest beantwortet Inventory." + methode.getName()
                                    + " nicht");
                });
    }

    /**
     * Die Sicht auf ein offenes Menue - oben das Menue, unten der Rucksack.
     *
     * <p>{@code convertSlot} rechnet wie Bukkit: Ein rohes Fach jenseits des oberen
     * Inventars wird zu einem Fach des unteren - und genau diese Rechnung ist der Grund
     * fuer {@code getRawSlot} in der Wache.
     */
    private static InventoryView sicht(HumanEntity wer, Inventory oben, Inventory unten) {
        return (InventoryView) Proxy.newProxyInstance(
                MenuewacheTest.class.getClassLoader(),
                new Class<?>[] {InventoryView.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getPlayer" -> wer;
                    case "getTopInventory" -> oben;
                    case "getBottomInventory" -> unten;
                    case "getType" -> InventoryType.CHEST;
                    case "convertSlot" -> {
                        int roh = (int) werte[0];
                        yield roh < oben.getSize() ? roh : roh - oben.getSize();
                    }
                    case "getInventory" -> {
                        int roh = (int) werte[0];
                        yield roh < 0 ? null : roh < oben.getSize() ? oben : unten;
                    }
                    case "getSlotType" -> org.bukkit.event.inventory.InventoryType
                            .SlotType.CONTAINER;
                    case "toString" -> "Sicht von " + wer.getName();
                    case "hashCode" -> System.identityHashCode(eigen);
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "MenuewacheTest beantwortet InventoryView."
                                    + methode.getName() + " nicht");
                });
    }

    private InventoryClickEvent klick(int rohesFach, Object halter) {
        Inventory oben = inventar(halter, FAECHER);
        Inventory unten = inventar(null, 36);
        return new InventoryClickEvent(sicht(gerd.spieler(), oben, unten),
                InventoryType.SlotType.CONTAINER, rohesFach, ClickType.LEFT,
                InventoryAction.PICKUP_ALL);
    }

    @Test
    @DisplayName("1 - ein Klick auf einen Eintrag schliesst ERST und tippt DANN")
    void klickSchliesstErstUndTipptDann() {
        // DIE REIHENFOLGE IST DIE ZUSAGE: Ein Befehl, der selbst ein Menue oeffnet,
        // liefe sonst gegen ein offenes - und Bukkit schickt dafuer ein
        // Schliessereignis mitten in die Ausfuehrung. Eine Mitschrift, die nur ZAEHLT,
        // saehe den Tausch nicht.
        var ereignis = klick(10, new Menuehalter(Menuetafel.HAUPT));

        wache.beimKlicken(ereignis);

        assertTrue(ereignis.isCancelled(), "ein Knopf ist keine Ware");
        assertEquals(List.of(Spielerprobe.INVENTAR_ZU, "/parzelle"), gerd.gefuehrt());
    }

    @Test
    @DisplayName("2 - ein Klick auf ein leeres Fach des Menues tut nichts")
    void klickAufLeeresFachTutNichts() {
        var ereignis = klick(0, new Menuehalter(Menuetafel.HAUPT));

        wache.beimKlicken(ereignis);

        assertTrue(ereignis.isCancelled(), "auch das leere Fach ist keine Ware");
        assertEquals(List.of(), gerd.gefuehrt());
    }

    @Test
    @DisplayName("3 - ein Klick in den RUCKSACK fuehrt keinen Befehl aus")
    void klickInDenRucksackFuehrtKeinenBefehlAus() {
        // DER FALL, UM DEN ES GEHT: Rohes Fach 37 liegt im Rucksack, umgerechnet ist es
        // Fach 10 - und auf Fach 10 des MENUES steht /parzelle. Wer hier getSlot fragt,
        // laesst den Spieler einen Befehl ausfuehren, den er nicht angeklickt hat.
        var ereignis = klick(FAECHER + 10, new Menuehalter(Menuetafel.HAUPT));

        assertEquals(10, ereignis.getSlot(), "die Rechnung der Sicht muss stimmen,"
                + " sonst prueft diese Probe nichts");

        wache.beimKlicken(ereignis);

        assertEquals(List.of(), gerd.gefuehrt());
    }

    @Test
    @DisplayName("4 - ein Klick in einer fremden Truhe bleibt unberuehrt")
    void klickInFremderTruheBleibtUnberuehrt() {
        // KEINE Absage: Jede andere Truhe gehoert anderen Wachen (Shop, Laden, Freie
        // Truhe). Eine Wache, die jeden Klick absagt, machte das Spiel unbenutzbar.
        var ereignis = klick(10, null);

        wache.beimKlicken(ereignis);

        assertFalse(ereignis.isCancelled());
        assertEquals(List.of(), gerd.gefuehrt());
    }

    @Test
    @DisplayName("4b - zwei Klicks hintereinander, und vergessen raeumt wirklich alles")
    void zweiKlickeHintereinander() {
        // DIE LAGE, DIE DIE MITSCHRIFT PRUEFT [21.11.2026]. vergessen() loeschte nur
        // Handbalken und Chat, nicht die Liste gefuehrt - und trug trotzdem die Zusage
        // "vergisst alles Angekommene". Die erste Probe, die einen Spieler ueber zwei
        // Faelle wiederverwendet, bekam die Eintraege des ERSTEN Falls mitgeliefert und
        // waere gruen geblieben, auch wenn der zweite Klick gar nichts mehr tut.
        wache.beimKlicken(klick(10, new Menuehalter(Menuetafel.HAUPT)));
        assertEquals(List.of(Spielerprobe.INVENTAR_ZU, "/parzelle"), gerd.gefuehrt());

        gerd.vergessen();
        assertEquals(List.of(), gerd.gefuehrt(), "vergessen raeumt gefuehrt nicht mit -"
                + " dann traegt jede folgende Probe die Eintraege des ersten Falls");

        // UND DER ZWEITE KLICK steht danach allein da, mit seiner eigenen Reihenfolge.
        wache.beimKlicken(klick(11, new Menuehalter(Menuetafel.HAUPT)));
        assertEquals(List.of(Spielerprobe.INVENTAR_ZU, "/mark"), gerd.gefuehrt());
    }

    @Test
    @DisplayName("5 - ein Eintrag ohne Befehl wird abgesagt und tut nichts")
    void eintragOhneBefehlTutNichts() {
        var zierde = new Menuetafel(MenueSatz.TITEL, 3,
                List.of(new Menuetafel.Eintrag(10, MenueSatz.PARZELLE_NAME,
                        MenueSatz.PARZELLE_ERKLAERUNG, "stone", "")));
        var ereignis = klick(10, new Menuehalter(zierde));

        wache.beimKlicken(ereignis);

        assertTrue(ereignis.isCancelled());
        assertEquals(List.of(), gerd.gefuehrt());
    }

    @Test
    @DisplayName("6 - ein ZIEHEN ueber die Faecher wird abgesagt")
    void ziehenWirdAbgesagt() {
        // DER HAEUFIGSTE WEG, einen Knopf aus einem Menue zu holen: Maus halten und
        // ziehen. Ein eigenes Ereignis, darum ein eigener Empfaenger - derselbe Fund
        // steht beim Shopschutz.
        var ereignis = ziehen(new Menuehalter(Menuetafel.HAUPT));

        wache.beimZiehen(ereignis);

        assertTrue(ereignis.isCancelled());
    }

    @Test
    @DisplayName("7 - ein ZIEHEN in einer fremden Truhe bleibt unberuehrt")
    void ziehenInFremderTruheBleibtUnberuehrt() {
        var ereignis = ziehen(null);

        wache.beimZiehen(ereignis);

        assertFalse(ereignis.isCancelled());
    }

    private InventoryDragEvent ziehen(Object halter) {
        Inventory oben = inventar(halter, FAECHER);
        Inventory unten = inventar(null, 36);
        return new InventoryDragEvent(sicht(gerd.spieler(), oben, unten), null, null,
                false, new java.util.HashMap<>());
    }
}
