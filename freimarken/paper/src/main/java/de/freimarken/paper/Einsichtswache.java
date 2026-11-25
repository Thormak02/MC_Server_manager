package de.freimarken.paper;

import de.freimarken.kern.Einsichtsstand;
import java.time.Duration;
import java.time.Instant;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/**
 * Das "anfassen nicht" der Einsicht (Plan 12c.3, Schritt 16a Teil D).
 *
 * <p>Plan 12c.3: <i>"Einsehen eines Grundstuecks ohne Baurecht: hinsehen duerfen,
 * anfassen nicht."</i> Die eine Haelfte - dass die Kiste AUFGEHT - entscheidet
 * {@code Schutzwache.darfHandeln} ueber die Naht {@link Schutzwache.Einsicht}. Diese
 * Wache ist die andere Haelfte, und ohne sie waere die Einsicht ein NEHMRECHT: Wer eine
 * fremde Kiste offen hat, nimmt daraus mit jedem Klick.
 *
 * <p><b>Abgesagt wird JEDER Klick, ohne eine einzige Ausnahme</b>, solange ein fremder
 * Behaelter offen ist - auch der in den eigenen Rucksack. Das ist die Regel des
 * Projekts, und sie steht an drei Stellen begruendet ({@code Shopwache}: <i>"EIN
 * Ereignis ohne Ausnahme ist dicht; zehn Klickarten sind es irgendwann"</i>,
 * {@code Truhenwache}: <i>"sieben Wege zur selben Tat, und ein Programm, das sechs
 * davon kennt, hat keinen Deckel"</i>, {@code Menuewache}: <i>"Abgesagt wird JEDER
 * Klick in einem Menue, auch der auf ein leeres Fach"</i>).
 *
 * <p><b>Warum auch der Klick in den eigenen Rucksack:</b> Ein Umschaltklick dort legt
 * etwas IN die offene Kiste - das ist eine Veraenderung, und zwar die, mit der man
 * Beweise unterschiebt. Und ein Doppelklick im Rucksack zieht gleiche Stapel aus der
 * Kiste zusammen. Nach Fach zu unterscheiden heisst, diese Wege einzeln zu kennen; die
 * Erfahrung dieses Projekts ist, dass man dann einen vergisst.
 *
 * <p><b>Was NICHT abgesagt wird:</b> Klicks, wenn gar kein Behaelter offen ist (der
 * Moderator sortiert seinen eigenen Rucksack) - sonst waere die Einsicht fuenf Minuten
 * lang eine Handlungssperre, und niemand schaltet sie dann noch ein. Die Unterscheidung
 * steht in {@link #fremderBehaelter}.
 *
 * <p><b>{@link EventPriority#LOWEST}</b> wie die {@code Strafwache}: Hier wird
 * ABGEBROCHEN, und eine Wache auf {@code MONITOR} kann das nicht mehr. Dazu
 * {@code ignoreCancelled} NICHT - wer schon abgesagt hat, hat recht, aber diese Wache
 * will auch dann die Zeile senden, damit der Moderator weiss, warum nichts geschieht.
 * Darum fragt sie {@code isCancelled} selbst.
 */
final class Einsichtswache implements Listener {

    /**
     * Eine Absage je Spieler und Pause.
     *
     * <p>Dieselbe Begruendung und dieselbe Zahl wie bei {@code Schutzwache}: Ohne das
     * wird aus einem gehaltenen Klick ein Chatregen - und ein Moderator, der die Zeile
     * zwanzigmal liest, liest sie beim naechsten Mal nicht.
     */
    private static final Duration MELDEPAUSE = Duration.ofSeconds(2L);

    private final Einsichtsstand stand;
    private final Sprache sprache;
    private final Einsichtswache.Uhr uhr;
    private final Abklingzeit drossel = new Abklingzeit(MELDEPAUSE);

    /** Die Uhr - als Naht, damit eine Probe einen festen Zeitpunkt hereingeben kann. */
    interface Uhr {
        Instant jetzt();
    }

    Einsichtswache(Einsichtsstand stand, Sprache sprache, Uhr uhr) {
        if (stand == null || sprache == null || uhr == null) {
            throw new IllegalArgumentException("Die Einsichtswache braucht den Stand,"
                    + " Sprache und eine Uhr");
        }
        this.stand = stand;
        this.sprache = sprache;
        this.uhr = uhr;
    }

    /** Ein Klick - der Weg, auf dem neun von zehn Nehmarten laufen. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void beimKlicken(InventoryClickEvent ereignis) {
        if (ereignis.isCancelled() || !greift(ereignis.getWhoClicked(),
                ereignis.getView())) {
            return;
        }
        ereignis.setCancelled(true);
        melde((Player) ereignis.getWhoClicked());
    }

    /**
     * Ein Ziehen - der zehnte Weg, und der einzige, der NICHT durch den Klick laeuft.
     *
     * <p>Ohne diesen Zweig bliebe der haeufigste Weg offen, etwas aus einem Inventar zu
     * holen: Maus halten und ueber die Faecher ziehen. Derselbe Fund steht beim
     * Shopschutz und bei der Menuewache - dreimal dasselbe, und das ist der Grund, es
     * hier gleich mitzubauen.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void beimZiehen(InventoryDragEvent ereignis) {
        if (ereignis.isCancelled() || !greift(ereignis.getWhoClicked(),
                ereignis.getView())) {
            return;
        }
        ereignis.setCancelled(true);
        melde((Player) ereignis.getWhoClicked());
    }

    /**
     * Beim Verlassen wird die Einsicht vergessen.
     *
     * <p>Dieselbe Begruendung wie bei {@code Schutzwache.beimVerlassen}: Sonst waechst
     * die Tabelle mit jedem Spieler, der je eingesehen hat, und niemand sieht es.
     *
     * <p><b>Und es ist mehr als Aufraeumen:</b> Wer den Server verlaesst und
     * wiederkommt, soll nicht weiter einsehen - ein Modus, der eine Abwesenheit
     * uebersteht, ist einer, den man beim Beitritt nicht mehr erwartet.
     *
     * <p>{@link EventPriority#MONITOR}: Das Aufraeumen ist eine FOLGE des Verlassens und
     * keine Bedingung dafuer.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void beimVerlassen(PlayerQuitEvent ereignis) {
        stand.vergiss(ereignis.getPlayer().getUniqueId());
    }

    /**
     * Ob diese Wache hier eingreift.
     *
     * <p>Drei Fragen, und alle drei muessen hier stehen: Ist es ein Spieler, sieht er
     * ein, und ist ueberhaupt ein fremder Behaelter offen. Die dritte ist die, die
     * verhindert, dass die Einsicht fuenf Minuten lang eine Handlungssperre ist.
     */
    private boolean greift(HumanEntity wer, InventoryView sicht) {
        if (!(wer instanceof Player spieler)) {
            return false;
        }
        if (!stand.schaut(spieler.getUniqueId(), uhr.jetzt())) {
            return false;
        }
        return fremderBehaelter(sicht, spieler);
    }

    /**
     * Ob die obere Haelfte dieser Sicht etwas FREMDES ist und nicht der eigene Rucksack.
     *
     * <p>Gefragt wird der HALTER und nicht die ART. Die Art zu fragen waere der
     * naheliegende Weg - und er ist in keiner Probe pruefbar: Der Klassenaufbau von
     * {@code InventoryType} geht ueber {@code MenuType} an die Registry und wirft ohne
     * Server "No RegistryAccess implementation found" (gemessen am 25.11.2026). Eine
     * Wache, deren einzige Entscheidung keine Probe halten kann, ist eine Zusage ohne
     * Nachweis. Die Menuewache erkennt ihr Menue aus demselben Grund am Halter.
     *
     * <p>Gehoert das obere Inventar einem MENSCHEN, ist es fremd, sobald es nicht er
     * selbst ist - damit ist auch der Rucksack eines anderen Spielers gesperrt, nicht
     * nur die Kiste. Sein eigenes Werkfeld und sein eigener Rucksack gehoeren ihm, dort
     * soll er weiter sortieren koennen.
     *
     * <p><b>Ein Inventar ohne menschlichen Halter gilt als FREMD</b> - Kiste, Ofen,
     * Menue, unbekannt. Das ist die sichere Richtung: Von zwei Fehlern der, der nichts
     * oeffnet. Eine laestige Sperre auf dem eigenen Rucksack ist ein Aerger, eine offene
     * fremde Kiste ein Schaden. Und es ist die Richtung, die eine neue Behaelterart von
     * Mojang von selbst sperrt, statt auf einen Nachtrag zu warten.
     */
    private static boolean fremderBehaelter(InventoryView sicht, HumanEntity wer) {
        if (sicht == null) {
            return false;
        }
        Inventory oben = sicht.getTopInventory();
        if (oben == null) {
            return false;
        }
        if (oben.getHolder() instanceof HumanEntity mensch) {
            return !mensch.getUniqueId().equals(wer.getUniqueId());
        }
        return true;
    }

    /** Die EINE Sendestelle dieser Klasse - mit Drossel. */
    private void melde(Player spieler) {
        if (drossel.restNs(spieler.getUniqueId(), System.nanoTime()) > 0L) {
            return;
        }
        for (var zeile : sprache.bau(EinsichtSatz.NUR_SEHEN, 1L, Einsatz.keine())) {
            spieler.sendMessage(zeile);
        }
    }
}
