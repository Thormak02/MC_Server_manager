package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Lebenszeichen;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Messwache DURCHLAUFEN - mit echten Ereignissen des Spiels.
 *
 * <p>Hier wird das Ereignis wirklich gebaut, wie bei {@link SetzwacheTest} und aus
 * demselben Grund: Die Entscheidung steckt IM Ereignis. Dass ein Linksklick nicht als
 * Benutzen zaehlt, haengt allein an {@link Action#isRightClick()} - ohne ein echtes
 * Ereignis waere das eine Behauptung, und an ihr haengt die halbe Messung.
 *
 * <p><b>Der Chatzweig fehlt hier.</b> Er bringt seine Meldung ueber den Zeitgeber des
 * Servers in den Hauptfaden, und ohne Server gibt es keinen. Was er entscheidet, ist
 * ausserdem nichts: Er meldet {@link Lebenszeichen#SPRECHEN}, wie der Befehlszweig, der
 * hier geprueft wird. Geprueft wird er im Spiel.
 */
@DisplayName("Messwache")
class MesswacheTest {

    private static final UUID ANNA = UUID.fromString("0000a11a-0000-0000-0000-000000000001");

    private World welt;
    private Spielerprobe anna;
    private Messstelle messstelle;
    private Messwache wache;

    @BeforeAll
    static void einmal() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        welt = Serverprobe.welt("freimarken:marktstadt");
        anna = new Spielerprobe("Anna", ANNA);
        // Die Messstelle ohne Ortsquelle: Dieser Lauf prueft, WAS gemeldet wird, und das
        // Bewegen wird nicht gemeldet, sondern gemessen. Eine Quelle, die Orte lieferte,
        // koennte hier aus Versehen ein zweites Zeichen beisteuern und jede Zusage
        // dieses Laufs weich machen.
        messstelle = new Messstelle(new Wirtschaftsstand(Wirtschaftswerte.VORGABE),
                wer -> null);
        wache = new Messwache(null, messstelle);
    }

    /** Welche Arten die Messstelle fuer Anna gesammelt hat - in fester Reihenfolge. */
    private List<String> gesammelt() {
        List<String> namen = new ArrayList<>();
        for (Lebenszeichen art : messstelle.bericht(ANNA)) {
            namen.add(art.schluessel());
        }
        return namen;
    }

    @Test
    @DisplayName("Ein abgebauter Block ist ein Lebenszeichen")
    void abbauen() {
        wache.beimAbbauen(new BlockBreakEvent(welt.getBlockAt(7, 71, 13), anna.spieler()));

        assertEquals(List.of("abbauen"), gesammelt());
    }

    @Test
    @DisplayName("Ein gesetzter Block ist ein Lebenszeichen")
    void setzen() {
        wache.beimSetzen(new BlockPlaceEvent(welt.getBlockAt(7, 71, 13), null,
                welt.getBlockAt(7, 70, 13), null, anna.spieler(), true,
                org.bukkit.inventory.EquipmentSlot.HAND));

        assertEquals(List.of("setzen"), gesammelt());
    }

    @Test
    @DisplayName("Ein RECHTSKLICK ist ein Benutzen")
    void rechtsklick() {
        wache.beimBenutzen(new PlayerInteractEvent(anna.spieler(),
                Action.RIGHT_CLICK_BLOCK, null, welt.getBlockAt(7, 71, 13), null));

        assertEquals(List.of("benutzen"), gesammelt());

        wache.beimBenutzen(new PlayerInteractEvent(anna.spieler(),
                Action.RIGHT_CLICK_AIR, null, null, null));

        assertEquals(List.of("benutzen"), gesammelt());
    }

    @Test
    @DisplayName("Ein LINKSKLICK ist KEIN Benutzen - daran haengt die halbe Messung")
    void linksklickZaehltNicht() {
        // Der tragende Fall: PlayerInteractEvent feuert bei JEDEM Schlag. Zaehlte das als
        // Benutzen, bekaeme ein Makro, das an einem Pflastersteingenerator die linke
        // Taste haelt, zwei Lebenszeichen - Graben UND Benutzen - und waere taetig. Es
        // gaebe genau das Muster, das diese Messstelle erkennen soll, und zwar stumm.
        wache.beimBenutzen(new PlayerInteractEvent(anna.spieler(),
                Action.LEFT_CLICK_BLOCK, null, welt.getBlockAt(7, 71, 13), null));
        wache.beimBenutzen(new PlayerInteractEvent(anna.spieler(),
                Action.LEFT_CLICK_AIR, null, null, null));

        assertEquals(List.of(), gesammelt(),
                "ein Schlag darf kein Benutzen sein");
    }

    @Test
    @DisplayName("Ein Makro am Generator bekommt nur EIN Zeichen - der Fall zusammen")
    void makroAmGeneratorBleibtBeiEinem() {
        // Die zwei Zweige zusammen, so wie das Spiel sie liefert: Wer die linke Taste
        // haelt, erzeugt abwechselnd einen Schlag und einen Blockbruch. Diese Probe ist
        // die, die der ganze Schritt meint - und sie prueft den Weg und nicht die Regel.
        for (int i = 0; i < 20; i++) {
            wache.beimBenutzen(new PlayerInteractEvent(anna.spieler(),
                    Action.LEFT_CLICK_BLOCK, null, welt.getBlockAt(7, 71, 13), null));
            wache.beimAbbauen(new BlockBreakEvent(welt.getBlockAt(7, 71, 13),
                    anna.spieler()));
        }

        assertEquals(List.of("abbauen"), gesammelt());
        assertEquals(1, messstelle.zaehlendeBisher(ANNA),
                "zwanzig Bloecke sind EIN Lebenszeichen - gezaehlt wird Vielfalt und"
                        + " nicht Menge");
    }

    @Test
    @DisplayName("Eine Druckplatte zaehlt nicht - darauf tritt man beim Gehen")
    void physischZaehltNicht() {
        wache.beimBenutzen(new PlayerInteractEvent(anna.spieler(), Action.PHYSICAL,
                null, welt.getBlockAt(7, 71, 13), null));

        assertEquals(List.of(), gesammelt());
    }

    @Test
    @DisplayName("Ein Befehl ist ein Sprechen")
    void befehl() {
        // Die DREISTELLIGE Form: Die zweistellige holt sich die Empfaenger ueber
        // player.getServer(), und das ist ohne Server nicht zu beantworten - gemessen am
        // 07.11.2026. Was die Wache liest, ist der Spieler, nicht die Empfaenger.
        wache.beimBefehl(new PlayerCommandPreprocessEvent(anna.spieler(), "/mark",
                Set.of()));

        assertEquals(List.of("sprechen"), gesammelt());
    }

    @Test
    @DisplayName("Eine geoeffnete Oberflaeche ist ein Lebenszeichen")
    void oeffnen() {
        wache.beimOeffnen(new InventoryOpenEvent(sichtAuf(anna.spieler())));

        assertEquals(List.of("oeffnen"), gesammelt());
    }

    @Test
    @DisplayName("Der ANGREIFER zaehlt, wenn er ein Spieler ist")
    void angreiferIstEinSpieler() {
        // Ueber den Griff und nicht ueber das Ereignis: EntityDamageByEntityEvent bricht
        // schon in seiner Klassenvorbereitung ab, wenn kein Server laeuft - gemessen am
        // 07.11.2026, ExceptionInInitializerError in seinem statischen Feld. Darum zieht
        // Messwache.angreifer die Entscheidung heraus; was der Zweig darum herum noch tut,
        // ist eine Zeile, und sie ist im Spiel zu pruefen.
        assertEquals(ANNA, Messwache.angreifer(anna.spieler()));
    }

    @Test
    @DisplayName("Wer SCHADEN NIMMT, hat nichts getan - der Fall der Mobfarm")
    void geschlagenWerdenZaehltNicht() {
        // Wer in einer Mobfarm steht und Schaden nimmt, tut nichts. Gefragt wird darum
        // nach dem Schlaeger; ein Wesen als Schlaeger gibt null, und die Messstelle nimmt
        // einen Aufruf ohne Spieler stillschweigend nicht an.
        Entity wesen = Wesenprobe.wesenAmHaken();

        assertNull(Messwache.angreifer(wesen));
        assertNull(Messwache.angreifer(null));

        messstelle.melde(Messwache.angreifer(wesen), Lebenszeichen.KAEMPFEN);

        assertEquals(List.of(), gesammelt(),
                "ein Schlag ohne Spieler darf nichts eintragen");
    }

    @Test
    @DisplayName("Mehrere verschiedene Zweige ergeben zusammen die Vielfalt")
    void verschiedeneZweigeZusammen() {
        wache.beimAbbauen(new BlockBreakEvent(welt.getBlockAt(7, 71, 13), anna.spieler()));
        wache.beimBefehl(new PlayerCommandPreprocessEvent(anna.spieler(), "/mark",
                Set.of()));

        assertEquals(List.of("abbauen", "sprechen"), gesammelt());
        assertEquals(2, messstelle.zaehlendeBisher(ANNA));
    }

    @Test
    @DisplayName("Jeder Zweig steht auf MONITOR und laesst Abgelehntes liegen")
    void prioritaetUndAbgelehntes() throws ReflectiveOperationException {
        // Die Blockzweige duerfen ein abgelehntes Ereignis nicht zaehlen: Was der
        // Besitzschutz verweigert hat, ist nicht geschehen - und Dauerschlagen auf eine
        // fremde Mauer waere sonst ein folgenloses Lebenszeichen, das beliebig oft geht.
        // Geprueft wird die ANMERKUNG und nicht ein abgelehntes Ereignis: Mit
        // ignoreCancelled entscheidet Bukkit, nicht dieser Code, und ein nachgebauter
        // Aufruf lief hier auch mit abgeschalteter Anmerkung durch.
        List<String> mitIgnoreCancelled = new ArrayList<>();
        for (var methode : Messwache.class.getDeclaredMethods()) {
            var anmerkung = methode.getAnnotation(org.bukkit.event.EventHandler.class);
            if (anmerkung == null) {
                continue;
            }
            assertEquals(org.bukkit.event.EventPriority.MONITOR, anmerkung.priority(),
                    methode.getName() + " muss auf MONITOR stehen - die Messung urteilt"
                            + " ueber das, was wirklich geschehen ist");
            if (anmerkung.ignoreCancelled()) {
                mitIgnoreCancelled.add(methode.getName());
            }
        }

        // Die NAMEN und nicht die Zahl: Eine Zaehlung von sechs waere auch gruen, wenn
        // jemand beimSchlagen ein ignoreCancelled gibt und es bei beimAbbauen wegnimmt -
        // und dann zaehlte das Schlagen auf eine geschuetzte Mauer.
        assertEquals(Set.of("beimAbbauen", "beimSetzen", "beimBenutzen", "beimOeffnen",
                        "beimBefehl", "beimSprechen"),
                new java.util.TreeSet<>(mitIgnoreCancelled),
                "beimSchlagen ist der EINE Zweig ohne ignoreCancelled: ein abgewehrter"
                        + " Schlag ist trotzdem ein Schlag, und es gibt keinen Weg,"
                        + " folgenlos beliebig oft zu schlagen");
    }

    /**
     * Eine Sicht, die nur ihren Spieler kennt - mehr fragt {@link InventoryOpenEvent}
     * nicht.
     *
     * <p>Ein Stellvertreter und kein echtes Inventar: {@code InventoryView} ist eine
     * Schnittstelle, und ohne Server gibt es keine Umsetzung davon. Was die Wache daraus
     * liest, ist allein {@code getPlayer()}.
     */
    private static InventoryView sichtAuf(HumanEntity spieler) {
        return (InventoryView) Proxy.newProxyInstance(
                MesswacheTest.class.getClassLoader(),
                new Class<?>[] {InventoryView.class},
                (stellvertreter, methode, argumente) -> {
                    if ("getPlayer".equals(methode.getName())) {
                        return spieler;
                    }
                    if ("toString".equals(methode.getName())) {
                        return "Sicht auf " + spieler.getName();
                    }
                    throw new UnsupportedOperationException("Die Wache darf nur den"
                            + " Spieler fragen, nicht " + methode.getName());
                });
    }

}
