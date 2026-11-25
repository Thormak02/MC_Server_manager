package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Einsichtsstand;
import de.freimarken.kern.Einsichtswerte;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Das "anfassen nicht" der Einsicht (Schritt 16a Teil D).
 *
 * <p><b>Ohne diese Wache waere die Einsicht ein NEHMRECHT.</b> Die drei Naehte in
 * Schutz-, Shop- und Einrichtungswache oeffnen fremde Behaelter; wer eine offene Kiste
 * vor sich hat, nimmt daraus mit jedem Klick.
 *
 * <p>Geprueft werden {@code InventoryClickEvent} und {@code InventoryDragEvent} - die
 * zwei Ereignisse, in denen alle zehn Nehmwege liegen, die {@code Shopwache} aufzaehlt.
 * Beide lassen sich ohne Server bauen; das Vorbild dafuer ist {@link MenuewacheTest}.
 *
 * <p><b>Die Attrappen hier beantworten {@code getType} NICHT.</b> Das ist Absicht und
 * kein Mangel: Der Klassenaufbau von {@code InventoryType} geht ueber {@code MenuType}
 * an die Registry und wirft ohne Server (gemessen am 25.11.2026). Eine Wache, die die
 * ART des Inventars fragt, ist damit unpruefbar - sie fragt den HALTER. Fragt sie je
 * wieder die Art, faellt diese Probe mit {@code UnsupportedOperationException} um,
 * anstatt die Luecke durchzulassen.
 */
@DisplayName("Die Einsichtswache")
class EinsichtswacheTest {

    private static final Instant MITTAGS = Instant.parse("2026-11-25T12:00:00Z");

    /**
     * Eine fremde Kiste - ein Halter, der kein Mensch ist.
     *
     * <p>{@code InventoryHolder} hat genau eine Methode, darum geht ein Lambda. Was es
     * liefert, wird nie gefragt: Gefragt wird nur, WER haelt.
     */
    private static final InventoryHolder KISTE = () -> null;

    private Einsichtsstand stand;
    private Sprache sprache;
    private Spielerprobe moderator;

    @BeforeAll
    static void server() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void frisch() {
        stand = new Einsichtsstand(() -> Einsichtswerte.VORGABE);
        sprache = Sprachprobe.spracheAusDemJar();
        moderator = new Spielerprobe("Cara", Spielerprobe.kennungAus(881));
    }

    @Test
    @DisplayName("1 - WAEHREND der Einsicht ist der Klick in einer Kiste abgesagt")
    void klickInKisteAbgesagt() {
        stand.schalteEin(moderator.spieler().getUniqueId(), MITTAGS);
        InventoryClickEvent ereignis = klick(KISTE);

        wache().beimKlicken(ereignis);

        assertTrue(ereignis.isCancelled(),
                "ohne das waere die Einsicht ein Nehmrecht");
        assertEquals(1, moderator.chatBild().size(), moderator.chatBild().toString());
        assertTrue(moderator.chatBild().get(0).contains("nehmen"),
                "und er LIEST, warum nichts geschieht: " + moderator.chatBild());
    }

    @Test
    @DisplayName("2 - OHNE Einsicht wird nichts abgesagt")
    void ohneEinsichtKeineAbsage() {
        InventoryClickEvent ereignis = klick(KISTE);

        wache().beimKlicken(ereignis);

        // Die Gegenrichtung, und sie ist Pflicht: Eine Wache, die immer absagt, waere
        // mit der Probe darueber genauso gruen - und niemand koennte mehr eine Kiste
        // benutzen.
        assertFalse(ereignis.isCancelled());
        assertTrue(moderator.chatBild().isEmpty());
    }

    @Test
    @DisplayName("3 - NACH der Frist wird nichts mehr abgesagt")
    void nachDerFristKeineAbsage() {
        stand.schalteEin(moderator.spieler().getUniqueId(), MITTAGS);
        InventoryClickEvent ereignis = klick(KISTE);

        // Die Uhr ist eine NAHT: Diese Wache fragt sie, und die Probe gibt einen
        // festen Zeitpunkt herein. Ohne das waere diese Probe nur in einem Fenster am
        // Tag gruen - gemessen am 25.11.2026 an der Strafwache.
        new Einsichtswache(stand, sprache, () -> MITTAGS.plusSeconds(300L))
                .beimKlicken(ereignis);

        assertFalse(ereignis.isCancelled(), "die Einsicht ist abgelaufen");
    }

    @Test
    @DisplayName("4 - DER EIGENE Rucksack bleibt frei")
    void eigenerRucksackBleibtFrei() {
        // Sonst waere die Einsicht fuenf Minuten lang eine Handlungssperre, und
        // niemand schaltet sie dann noch ein. Sein eigenes Inventar haelt er selbst -
        // daran erkennt die Wache es.
        stand.schalteEin(moderator.spieler().getUniqueId(), MITTAGS);
        InventoryClickEvent ereignis = klick(moderator.spieler());

        wache().beimKlicken(ereignis);

        assertFalse(ereignis.isCancelled());
        assertTrue(moderator.chatBild().isEmpty());
    }

    @Test
    @DisplayName("5 - JEDE Klickart wird abgesagt, ohne Ausnahme")
    void jedeKlickartAbgesagt() {
        // DIE REGEL DES PROJEKTS: "EIN Ereignis ohne Ausnahme ist dicht; zehn
        // Klickarten sind es irgendwann" (Shopwache). Ueber ClickType.values() und
        // nicht mit einer Liste - eine neue Klickart von Mojang ist damit von selbst
        // gesperrt.
        stand.schalteEin(moderator.spieler().getUniqueId(), MITTAGS);

        for (ClickType art : ClickType.values()) {
            InventoryClickEvent ereignis = klick(KISTE, art);
            wache().beimKlicken(ereignis);
            assertTrue(ereignis.isCancelled(), art + " ist nicht abgesagt");
        }
    }

    @Test
    @DisplayName("6 - JEDES Fach wird abgesagt, auch das im eigenen Rucksack")
    void jedesFachAbgesagt() {
        // Ein Umschaltklick im eigenen Rucksack legt etwas IN die offene Kiste - das
        // ist die Veraenderung, mit der man Beweise unterschiebt. Und ein Doppelklick
        // dort zieht gleiche Stapel aus der Kiste zusammen. Nach Fach zu unterscheiden
        // heisst, diese Wege einzeln zu kennen.
        stand.schalteEin(moderator.spieler().getUniqueId(), MITTAGS);

        for (int fach : new int[] {0, 26, 27, 53, -999}) {
            InventoryClickEvent ereignis = new InventoryClickEvent(
                    sicht(KISTE), SlotType.CONTAINER, fach, ClickType.SHIFT_LEFT,
                    InventoryAction.MOVE_TO_OTHER_INVENTORY);
            wache().beimKlicken(ereignis);
            assertTrue(ereignis.isCancelled(), "Fach " + fach + " ist nicht abgesagt");
        }
    }

    @Test
    @DisplayName("7 - DAS ZIEHEN wird abgesagt - der zehnte Weg")
    void ziehenAbgesagt() {
        // Der einzige Nehmweg, der NICHT durch den Klick laeuft: Maus halten und
        // ueber die Faecher ziehen. Derselbe Fund steht beim Shopschutz und bei der
        // Menuewache - dreimal dasselbe.
        stand.schalteEin(moderator.spieler().getUniqueId(), MITTAGS);
        InventoryDragEvent ereignis = new InventoryDragEvent(
                sicht(KISTE), null, null, false, new java.util.HashMap<>());

        wache().beimZiehen(ereignis);

        assertTrue(ereignis.isCancelled());
    }

    @Test
    @DisplayName("8 - die Absage wird GEDROSSELT - eine Zeile je zwei Sekunden")
    void absageWirdGedrosselt() {
        // Sonst wird aus einem gehaltenen Klick ein Chatregen, und beim zwanzigsten
        // Mal liest die Zeile niemand mehr. Dieselbe Drossel und dieselbe Zahl wie bei
        // der Schutzwache.
        stand.schalteEin(moderator.spieler().getUniqueId(), MITTAGS);
        Einsichtswache wache = wache();

        for (int lauf = 0; lauf < 5; lauf++) {
            InventoryClickEvent ereignis = klick(KISTE);
            wache.beimKlicken(ereignis);
            assertTrue(ereignis.isCancelled(), "abgesagt wird JEDES Mal");
        }

        assertEquals(1, moderator.chatBild().size(),
                "gesendet nur einmal: " + moderator.chatBild());
    }

    @Test
    @DisplayName("9 - ein schon ABGESAGTES Ereignis wird nicht noch einmal beantwortet")
    void abgesagtesWirdNichtWiederholt() {
        // Wer schon abgesagt hat, hat recht. Eine zweite Zeile daneben waere eine
        // Begruendung fuer eine Absage, die ein anderer ausgesprochen hat.
        stand.schalteEin(moderator.spieler().getUniqueId(), MITTAGS);
        InventoryClickEvent ereignis = klick(KISTE);
        ereignis.setCancelled(true);

        wache().beimKlicken(ereignis);

        assertTrue(moderator.chatBild().isEmpty(), moderator.chatBild().toString());
    }

    @Test
    @DisplayName("10 - das Verlassen VERGISST die Einsicht")
    void verlassenVergisst() {
        // Mehr als Aufraeumen: Wer wiederkommt, soll nicht weiter einsehen - ein
        // Modus, der eine Abwesenheit uebersteht, ist einer, den man beim Beitritt
        // nicht mehr erwartet.
        UUID wer = moderator.spieler().getUniqueId();
        stand.schalteEin(wer, MITTAGS);

        wache().beimVerlassen(new org.bukkit.event.player.PlayerQuitEvent(
                moderator.spieler(), net.kyori.adventure.text.Component.empty()));

        assertFalse(stand.schaut(wer, MITTAGS));
        assertEquals(0, stand.anzahl(MITTAGS), "und die Tabelle waechst nicht");
    }

    @Test
    @DisplayName("11 - ohne Bauteile gibt es keine Wache")
    void fehlendeBauteileWerfen() {
        assertThrows(IllegalArgumentException.class,
                () -> new Einsichtswache(null, sprache, () -> MITTAGS));
        assertThrows(IllegalArgumentException.class,
                () -> new Einsichtswache(stand, null, () -> MITTAGS));
        // Ohne die Uhr haengt jede Frage an der Tageszeit - und eine Probe, die von
        // ihr abhaengt, ist keine.
        assertThrows(IllegalArgumentException.class,
                () -> new Einsichtswache(stand, sprache, null));
    }

    @Test
    @DisplayName("12 - ein FREMDER Rucksack ist kein eigener")
    void fremderRucksackIstFremd() {
        // Die Wache fragt nicht "haelt es ein Mensch", sondern "haelt es DIESER
        // Mensch". Sonst waere das Inventar eines anderen Spielers - das ein Moderator
        // mit Einsicht oeffnen kann - der eine Behaelter, aus dem er nehmen darf.
        Spielerprobe anna = new Spielerprobe("Anna", Spielerprobe.kennungAus(882));
        stand.schalteEin(moderator.spieler().getUniqueId(), MITTAGS);
        InventoryClickEvent ereignis = klick(anna.spieler());

        wache().beimKlicken(ereignis);

        assertTrue(ereignis.isCancelled(),
                "fremd ist fremd, auch wenn es ein Mensch haelt");
    }

    @Test
    @DisplayName("13 - ein Inventar OHNE Halter gilt als fremd")
    void ohneHalterGiltAlsFremd() {
        // Die sichere Richtung, festgenagelt: Von zwei Fehlern der, der nichts
        // oeffnet. Ein Inventar, dessen Halter Bukkit nicht nennt, ist waehrend der
        // Einsicht gesperrt - eine laestige Sperre ist ein Aerger, eine offene fremde
        // Kiste ein Schaden.
        stand.schalteEin(moderator.spieler().getUniqueId(), MITTAGS);
        InventoryClickEvent ereignis = klick(null);

        wache().beimKlicken(ereignis);

        assertTrue(ereignis.isCancelled());
    }

    // ---------------------------------------------------------- Werkzeug

    private Einsichtswache wache() {
        return new Einsichtswache(stand, sprache, () -> MITTAGS);
    }

    private InventoryClickEvent klick(InventoryHolder halter) {
        return klick(halter, ClickType.LEFT);
    }

    private InventoryClickEvent klick(InventoryHolder halter, ClickType wie) {
        return new InventoryClickEvent(sicht(halter), SlotType.CONTAINER, 0, wie,
                InventoryAction.PICKUP_ALL);
    }

    /**
     * Eine Sicht, deren OBERE Haelfte dem gegebenen Halter gehoert.
     *
     * <p>Eigene Attrappe und nicht {@code Kistenprobe.sichtAuf}: Jene beantwortet nur
     * {@code getPlayer} und {@code getTopInventory}, und der Konstruktor von
     * {@code InventoryClickEvent} ruft {@code convertSlot}. Dieselbe Bauart wie in
     * {@link MenuewacheTest}, dort steht die lange Begruendung.
     *
     * <p>Die untere Haelfte haelt der Spieler selbst - so ist es im Spiel auch.
     */
    private InventoryView sicht(InventoryHolder halter) {
        Inventory oben = inventar(halter, 27);
        Inventory unten = inventar(moderator.spieler(), 36);
        return (InventoryView) Proxy.newProxyInstance(
                EinsichtswacheTest.class.getClassLoader(),
                new Class<?>[] {InventoryView.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getPlayer" -> moderator.spieler();
                    case "getTopInventory" -> oben;
                    case "getBottomInventory" -> unten;
                    case "convertSlot" -> {
                        int roh = (int) werte[0];
                        yield roh < oben.getSize() ? roh : roh - oben.getSize();
                    }
                    case "getInventory" -> {
                        int roh = (int) werte[0];
                        yield roh < 0 ? null : roh < oben.getSize() ? oben : unten;
                    }
                    case "getSlotType" -> SlotType.CONTAINER;
                    case "toString" -> "Sicht auf " + halter;
                    case "hashCode" -> System.identityHashCode(eigen);
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "EinsichtswacheTest beantwortet InventoryView."
                                    + methode.getName() + " nicht");
                });
    }

    private static Inventory inventar(InventoryHolder halter, int groesse) {
        return (Inventory) Proxy.newProxyInstance(
                EinsichtswacheTest.class.getClassLoader(),
                new Class<?>[] {Inventory.class},
                (eigen, methode, werte) -> switch (methode.getName()) {
                    case "getHolder" -> halter;
                    case "getSize" -> groesse;
                    case "toString" -> "Inventar von " + halter;
                    case "hashCode" -> System.identityHashCode(eigen);
                    case "equals" -> eigen == (werte == null ? null : werte[0]);
                    default -> throw new UnsupportedOperationException(
                            "EinsichtswacheTest beantwortet Inventory."
                                    + methode.getName() + " nicht");
                });
    }
}
