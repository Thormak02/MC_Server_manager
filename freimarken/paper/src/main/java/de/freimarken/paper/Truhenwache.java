package de.freimarken.paper;

import de.freimarken.kern.Blockort;
import de.freimarken.kern.Truhenwerte;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Die Freie Truhe im Spiel: Oeffnen, Schliessen, Trichter - Plan 7.2.
 *
 * <p><b>Gezaehlt wird die DIFFERENZ und nicht der Klick.</b> Aus einer offenen Kiste nimmt
 * man mit Linksklick, Rechtsklick, Shift-Klick, Doppelklick auf einen Stapel, mit der
 * Zahlentaste, durch Ziehen ueber mehrere Felder und mit {@code Q} aus dem offenen Fenster
 * heraus. Das sind sieben Wege zur selben Tat, und ein Programm, das sechs davon kennt,
 * hat keinen Deckel. Darum wird beim Oeffnen der Inhalt gezaehlt, beim Schliessen wieder,
 * und was FEHLT, ist die Entnahme.
 *
 * <p>Zwei Zusagen tragen diese Zaehlung, und beide gibt diese Wache:
 * <ul>
 *   <li><b>EINER zur Zeit - aber nur, WENN gezaehlt wird.</b> Bei zwei Zuschauern waere
 *       die Differenz nicht die Entnahme eines Spielers, sondern die von zweien -
 *       angerechnet bekaeme sie der, der zuletzt schliesst. Ohne Deckel und ohne Verbote
 *       zaehlt niemand etwas ({@link Truhenwerte#zaehlt()}), und dann duerfen alle
 *       gemeinsam hineinsehen [entschieden 10.11.2026].</li>
 *   <li><b>KEIN Trichter.</b> Eine Maschine, die zieht, waere eine Entnahme ohne
 *       Spieler - und damit eine Truhe, die sich ohne Deckel leeren laesst. Diese Zusage
 *       gilt IMMER: Sie haengt nicht an der Zaehlung, sondern daran, dass die Truhe
 *       Spielern gehoert und nicht Maschinen.</li>
 * </ul>
 *
 * <p><b>Die Naht ist {@link Truhenhand}</b>, dieselbe Bauart wie
 * {@code Shophandel.Warenhand}: Jeder Griff in eine Kiste geht durch sie, und darum
 * laufen {@link #oeffne} und {@link #schliesse} in einer Probe ohne Server. Was hier an
 * Bukkit haengt, sind die drei Ereignisse und die Umrechnung von Stapeln in Zahlen.
 *
 * <p><b>Verbotene Gegenstaende kommen beim SCHLIESSEN zurueck</b> und werden nicht beim
 * Hineinlegen abgewiesen - aus demselben Grund wie die Zaehlung: In eine offene Kiste legt
 * man auf ebenso vielen Wegen, wie man herausnimmt. Zurueck geht nur, was DIESER Spieler
 * neu hineingelegt hat; was schon vorher darin lag, gehoert dem taeglichen Lauf.
 */
final class Truhenwache implements Listener {

    /**
     * Jeder Griff in eine Kiste - die Naht zum Spiel.
     *
     * <p>Alle fuenf Methoden nennen, was WIRKLICH bewegt wurde, und niemals, was bewegt
     * werden sollte: Eine volle Kiste nimmt nicht alles, ein voller Rucksack gibt nicht
     * alles her. Wer die Absicht zurueckgibt, rechnet den Tagesdeckel gegen Gegenstaende,
     * die nirgends liegen.
     */
    interface Truhenhand {

        /** Was in der Truhe liegt, je Materialname und in Stueck. */
        Map<String, Integer> inhalt();

        /** Nimmt Stueck aus der TRUHE weg. */
        int ausDerTruhe(String material, int stueck);

        /** Legt Stueck in die TRUHE. */
        int inDieTruhe(String material, int stueck);

        /** Nimmt Stueck aus dem Rucksack des Spielers. */
        int vomSpieler(String material, int stueck);

        /**
         * Gibt Stueck in den Rucksack des Spielers.
         *
         * <p>Was nicht hineinpasst, faellt vor seine Fuesse und gilt als gegeben: Es ist
         * seins, und ein stilles Verschwinden waere ein Verlust, den niemand nachweisen
         * kann.
         */
        int zumSpieler(String material, int stueck);
    }

    /** Was beim Oeffnen geschieht: ob sie aufgeht und was der Spieler liest. */
    record Antwort(boolean geoeffnet, List<Component> zeilen) {
    }

    private final Truhenbuch buch;
    private final Sprache sprache;

    /**
     * Der Inhalt beim Oeffnen, je Spieler - die ganze Zaehlung haengt daran.
     *
     * <p>Je SPIELER und nicht je Truhe: Es kann mehrere Truhen geben, und ein Spieler
     * steht an genau einer. Ein Eintrag entsteht beim Oeffnen und verschwindet beim
     * Schliessen; bleibt einer liegen, weil der Server mitten im Fenster abstuerzt, ist
     * er beim naechsten Start weg - und das ist die richtige Richtung, denn dann hat
     * niemand etwas genommen.
     */
    private final Map<UUID, Map<String, Integer>> beimOeffnen = new HashMap<>();

    Truhenwache(Truhenbuch buch, Sprache sprache) {
        if (buch == null || sprache == null) {
            throw new IllegalArgumentException("Buch und Sprache sind Pflicht");
        }
        this.buch = buch;
        this.sprache = sprache;
    }

    // ============================================================ ohne Bukkit

    /**
     * Darf er hineinsehen - und was liest er dabei?
     *
     * <p>Merkt sich den Inhalt, WENN gezaehlt wird. Der Schnappschuss wird NUR hier
     * gesetzt: Ohne ihn zaehlt {@link #schliesse} gar nichts, und genau das ist richtig -
     * wenn die Truhe nie aufgegangen ist, und auch, wenn es nichts zu zaehlen gibt.
     *
     * <p><b>Besetzt abgewiesen wird nur eine zaehlende Truhe</b>
     * ({@link Truhenwerte#zaehlt()}). Ohne Deckel und ohne Verbote sehen alle gemeinsam
     * hinein: Es gibt keinen Unterschied, der jemandem gehoeren muesste.
     *
     * @param besetzt ob ein ANDERER gerade hineinsieht
     */
    Antwort oeffne(UUID wer, boolean besetzt, Truhenhand hand) {
        Truhenwerte werte = buch.werte();
        if (besetzt && werte.zaehlt()) {
            return new Antwort(false, List.of(Truhentext.besetzt(sprache)));
        }
        var zutritt = buch.zutritt(wer);
        return switch (zutritt.bescheid()) {
            case DECKEL -> new Antwort(false,
                    List.of(Truhentext.amDeckel(sprache, werte)));
            case GESTOERT -> new Antwort(false, List.of(Truhentext.gestoert(sprache)));
            case OFFEN -> {
                if (werte.zaehlt()) {
                    beimOeffnen.put(wer, hand.inhalt());
                }
                yield new Antwort(true,
                        List.of(Truhentext.beimOeffnen(sprache, zutritt.nochFrei())));
            }
        };
    }

    /**
     * Die Abrechnung beim Schliessen - die EINE Stelle, an der der Deckel wirkt.
     *
     * <p>Die Reihenfolge ist Absicht und nicht Geschmack:
     * <ol>
     *   <li><b>Erst das Verbotene zurueck.</b> Es ist seins und war nie eine Entnahme;
     *       waere es danach, raeumte das Zuruecklegen womoeglich schon seine Plaetze.</li>
     *   <li><b>Dann die Entnahme rechnen</b> - aus den Unterschieden je Material, nicht
     *       aus der Gesamtzahl: Wer zehn Stein nimmt und zehn Erde hineinlegt, hat zehn
     *       Stein genommen, und die Gesamtzahl waere 0.</li>
     *   <li><b>Dann der Ueberschuss zurueck in die Truhe</b>, und gezaehlt wird, was
     *       WIRKLICH zurueckgeht.</li>
     *   <li><b>Zuletzt schreiben</b>, und zwar die Zahl, die nach allem bei ihm bleibt.
     *       Wer seine Ware vorher wegwirft, hat sie der Truhe trotzdem entnommen.</li>
     * </ol>
     */
    List<Component> schliesse(UUID wer, Truhenhand hand) {
        Map<String, Integer> vorher = beimOeffnen.remove(wer);
        Truhenwerte werte = buch.werte();
        if (vorher == null) {
            // Nie geoeffnet oder schon abgerechnet: Dann gibt es keine Differenz, und
            // eine erfundene waere das Schlimmste - sie zaehlte dem Spieler eine
            // Entnahme an, die ein anderer gemacht hat.
            //
            // HIER STAND KURZ EIN ZWEITER RIEGEL (|| !werte.zaehlt()) gegen das
            // Wegschalten der Zaehlung mitten im offenen Fenster. Er war unpruefbar und
            // ist darum wieder heraus [Einspritzungsrunde 10.11.2026, Fall G11]: Wer
            // nicht zaehlt, hat auch keinen Deckel - also liest und schreibt
            // {@link Truhenbuch} keinen Tagesstand, legt nichts zurueck und sagt nichts -
            // und seine Verbotsliste ist leer, also kommt auch nichts zurueck. Die
            // Rechnung laeuft dann durch und kostet niemanden etwas.
            return List.of();
        }
        List<Component> zeilen = new ArrayList<>();
        Map<String, Integer> jetzt = hand.inhalt();
        zeilen.addAll(gibVerbotenesZurueck(vorher, jetzt, hand));

        Map<String, Integer> genommenJe = entnahmeJeMaterial(vorher, jetzt);
        int genommen = genommenJe.values().stream().mapToInt(Integer::intValue).sum();
        if (genommen < 1) {
            // Er hat nichts genommen (oder etwas hineingelegt). Kein Schreiben, keine
            // Zeile - das Schenken ist der halbe Zweck dieser Truhe und kein Vorgang,
            // der eine Meldung braucht.
            return zeilen;
        }
        var bilanz = buch.beimSchliessen(wer, genommen);
        int zurueck = lageZurueck(genommenJe, bilanz.zurueck(), hand);
        int behalten = genommen - zurueck;
        buch.merke(wer, behalten);
        if (bilanz.gestoert()) {
            zeilen.add(Truhentext.gestoert(sprache));
        }
        zeilen.addAll(Truhentext.beimSchliessen(sprache, werte, behalten, zurueck,
                buch.nochFrei(bilanz.schonVorher(), behalten)));
        return zeilen;
    }

    /**
     * Was von jedem Material FEHLT - geordnet, damit zwei Laeufe gleich ausgehen.
     *
     * <p>Je Material und nicht in einer Summe: Wer zehn Stein nimmt und zehn Erde
     * hineinlegt, hat zehn Stein genommen. Die Gesamtzahl waere 0, und damit waere das
     * Hineinlegen von Schutt ein Weg, den Deckel zu umgehen.
     *
     * <p>Ein {@link TreeMap} und kein {@link HashMap}: Reicht der Rest nicht fuer alle
     * Materialien, entscheidet die Reihenfolge, WELCHES zurueckgeht - und eine Probe, die
     * das prueft, braucht sie fest.
     */
    private static Map<String, Integer> entnahmeJeMaterial(Map<String, Integer> vorher,
            Map<String, Integer> jetzt) {
        Map<String, Integer> fehlt = new TreeMap<>();
        vorher.forEach((material, hatte) -> {
            int weg = hatte - jetzt.getOrDefault(material, 0);
            if (weg > 0) {
                fehlt.put(material, weg);
            }
        });
        return fehlt;
    }

    /**
     * Legt so viel zurueck, wie ueber dem Deckel liegt - und meldet, was ankam.
     *
     * <p>Genommen wird aus dem Rucksack und nur von den Materialien, die er AUS DIESER
     * TRUHE hat. Alles andere waere ein Zugriff auf seinen Besitz: Wer Stein aus der
     * Truhe nimmt, soll nicht seinen eigenen Stein verlieren, weil er zufaellig
     * dasselbe Material trug - unterscheiden lassen sich die beiden nicht, aber die MENGE
     * ist begrenzt auf das, was hier fehlt.
     *
     * <p>Was der Rucksack nicht hergibt (weggeworfen, verbaut, weitergegeben), kann nicht
     * zurueck. Es bleibt angerechnet - sonst waere das Wegwerfen vor dem Schliessen der
     * Weg um jeden Deckel.
     */
    private static int lageZurueck(Map<String, Integer> genommenJe, int zurueck,
            Truhenhand hand) {
        int rest = zurueck;
        int angekommen = 0;
        for (Map.Entry<String, Integer> eintrag : genommenJe.entrySet()) {
            if (rest < 1) {
                break;
            }
            int wunsch = Math.min(rest, eintrag.getValue());
            int vomSpieler = hand.vomSpieler(eintrag.getKey(), wunsch);
            if (vomSpieler < 1) {
                continue;
            }
            int rein = hand.inDieTruhe(eintrag.getKey(), vomSpieler);
            if (rein < vomSpieler) {
                // Die Truhe nimmt nicht alles - das kann nur sein, wenn dazwischen etwas
                // hineingelaufen ist. Was draussen bleibt, gehoert nicht dem Server: Es
                // geht an den Spieler zurueck und gilt weiter als entnommen.
                hand.zumSpieler(eintrag.getKey(), vomSpieler - rein);
            }
            rest -= rein;
            angekommen += rein;
        }
        return angekommen;
    }

    /**
     * Gibt zurueck, was dieser Spieler NEU hineingelegt hat und was dort nicht hingehoert.
     *
     * <p><b>Nur was NEU dazugekommen ist</b>, und das ist eine Entscheidung ueber einen
     * stillen Fehler: Setzt der Betreiber morgen ein Material auf die Verbotsliste, laege
     * es womoeglich schon lange in der Truhe - und dann bekaeme der naechste Spieler, der
     * nur hineinsieht, fremde Ware in die Hand. Was schon vorher darin lag, raeumt der
     * taegliche Lauf.
     */
    private List<Component> gibVerbotenesZurueck(Map<String, Integer> vorher,
            Map<String, Integer> jetzt, Truhenhand hand) {
        List<Component> zeilen = new ArrayList<>();
        Map<String, Integer> dazu = new TreeMap<>();
        jetzt.forEach((material, hat) -> {
            int mehr = hat - vorher.getOrDefault(material, 0);
            if (mehr > 0 && buch.istVerboten(material)) {
                dazu.put(material, mehr);
            }
        });
        for (Map.Entry<String, Integer> eintrag : dazu.entrySet()) {
            int heraus = hand.ausDerTruhe(eintrag.getKey(), eintrag.getValue());
            if (heraus < 1) {
                continue;
            }
            hand.zumSpieler(eintrag.getKey(), heraus);
            zeilen.add(Truhentext.verboten(sprache, eintrag.getKey()));
        }
        return zeilen;
    }

    // ============================================================ mit Bukkit

    /**
     * Beim Oeffnen: abweisen oder den Inhalt merken.
     *
     * <p>{@link EventPriority#HIGH} und nicht MONITOR: Dieses Ereignis wird ABGEBROCHEN,
     * wenn die Truhe zubleibt, und ein MONITOR-Zweig darf nichts abbrechen.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimOeffnen(InventoryOpenEvent ereignis) {
        if (!(ereignis.getPlayer() instanceof Player spieler)) {
            return;
        }
        Inventory inventar = ereignis.getInventory();
        if (!istFreieTruhe(inventar)) {
            return;
        }
        Antwort antwort = oeffne(spieler.getUniqueId(), fremderSiehtHinein(inventar,
                spieler), new BukkitHand(inventar, spieler));
        if (!antwort.geoeffnet()) {
            ereignis.setCancelled(true);
        }
        sende(spieler, antwort.zeilen());
    }

    /** Beim Schliessen: abrechnen. Das Ereignis ist nicht abbrechbar - es wird gerechnet. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void beimSchliessen(InventoryCloseEvent ereignis) {
        if (!(ereignis.getPlayer() instanceof Player spieler)) {
            return;
        }
        Inventory inventar = ereignis.getInventory();
        if (!istFreieTruhe(inventar)) {
            return;
        }
        sende(spieler, schliesse(spieler.getUniqueId(),
                new BukkitHand(inventar, spieler)));
    }

    /**
     * Kein Trichter zieht aus einer Freien Truhe - und keiner schiebt hinein.
     *
     * <p>Beide Richtungen, und die zweite ist nicht Vorsicht: Ein Trichter, der hineinlegt,
     * fuellte die Truhe mit dem, was eine Maschine auswirft - und der taegliche Lauf
     * loeschte es jede Nacht. Das ist keine Gemeinschaftstruhe, sondern ein Muellschlucker
     * mit Zwischenlager.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void beimTrichter(InventoryMoveItemEvent ereignis) {
        if (istFreieTruhe(ereignis.getSource())
                || istFreieTruhe(ereignis.getDestination())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Ob dieses Inventar zu einer Freien Truhe gehoert.
     *
     * <p>Ueber {@link Shopware#bloeckeVon} und nicht ueber
     * {@code Inventory.getLocation()}: Jenes liefert bei einer Doppelkiste die MITTE
     * zwischen den Haelften, also eine Stelle mit halben Koordinaten, an der kein Block
     * steht. Dieselbe Falle, dieselbe Antwort und darum dieselbe Methode wie beim
     * Trichterschutz der Kistenshops.
     *
     * <p>EINE Haelfte genuegt: Beide teilen dasselbe Inventar, und welche angeklickt
     * wird, entscheidet der Spieler.
     */
    private boolean istFreieTruhe(Inventory inventar) {
        for (Block block : Shopware.bloeckeVon(inventar)) {
            if (buch.istFreieTruhe(new Blockort(block.getWorld().getKey().asString(),
                    block.getX(), block.getY(), block.getZ()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ob ein ANDERER gerade hineinsieht.
     *
     * <p>Beim Oeffnen steht der Spieler selbst schon in der Zuschauerliste - gefragt wird
     * darum nach einem zweiten und nicht nach einem ersten.
     */
    private static boolean fremderSiehtHinein(Inventory inventar, Player spieler) {
        for (HumanEntity zuschauer : inventar.getViewers()) {
            if (!zuschauer.getUniqueId().equals(spieler.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private static void sende(Player spieler, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            spieler.sendMessage(zeile);
        }
    }

    /**
     * Die Naht zum echten Spiel - Stapel rein, Zahlen raus.
     *
     * <p>Gezaehlt wird in STUECK und nicht in Stapeln: Ein Stapel ist je Material etwas
     * anderes (Enderperlen zu 16, Werkzeug zu 1), und ein Deckel "16 Stapel" waere je
     * nach Ware ein anderer Deckel.
     *
     * <p>Der Materialname ist der Schluessel ({@code Material.name()}), weil er ohne
     * Server derselbe ist. Ein VERZAUBERTES Stueck faellt damit mit seinem gewoehnlichen
     * zusammen, und das ist hier richtig: Die Truhe zaehlt Stueck, nicht Werte - und wer
     * eine verzauberte Spitzhacke verschenken will, legt sie hinein, ohne dass die
     * Zaehlung sie von einer stumpfen unterscheiden muss.
     */
    private static final class BukkitHand implements Truhenhand {

        private final Inventory truhe;
        private final Player spieler;

        /**
         * Die Gestalt jedes Materials, das durch diese Hand gegangen ist.
         *
         * <p><b>Gemerkt wird, BEVOR etwas herausgenommen wird</b>, und daran hing ein
         * Verlust [gemessen im Spieltest am 10.11.2026]: Der Rueckweg leitet seinen
         * Stapel aus einem VORHANDENEN ab und suchte in beiden Inventaren. Beim
         * verbotenen Gegenstand ist der vorhandene aber genau der, der gerade
         * herausgenommen wurde - das einzige geschriebene Buch lag in der Truhe, der
         * Spieler hatte keines. Dann fand {@link #vorlageFuer} nichts, {@link #lege} gab
         * 0 zurueck, und das Buch war geloescht.
         *
         * <p>Derselbe Weg traegt den Ueberschuss ueber dem Tagesdeckel, und dort war es
         * schlimmer: Wer die Truhe RESTLOS leert und genau den Ueberschuss noch im
         * Rucksack hat, verlor ihn - angerechnet wurde ihm trotzdem alles.
         *
         * <p>Ein frischer {@code new ItemStack(Material)} waere kein Ausweg: Er schlaegt
         * in der Registry nach und wirft ohne laufenden Server - und er verloere Namen
         * und Verzauberung, also genau das, was den Verlust schmerzhaft macht.
         */
        private final Map<String, ItemStack> gegangen = new HashMap<>();

        private BukkitHand(Inventory truhe, Player spieler) {
            this.truhe = truhe;
            this.spieler = spieler;
        }

        @Override
        public Map<String, Integer> inhalt() {
            Map<String, Integer> gezaehlt = new LinkedHashMap<>();
            for (ItemStack stapel : truhe.getContents()) {
                if (stapel == null || stapel.getAmount() < 1) {
                    continue;
                }
                gezaehlt.merge(stapel.getType().name(), stapel.getAmount(),
                        Integer::sum);
            }
            return gezaehlt;
        }

        @Override
        public int ausDerTruhe(String material, int stueck) {
            return nimm(truhe, material, stueck);
        }

        @Override
        public int inDieTruhe(String material, int stueck) {
            return lege(truhe, material, stueck, false);
        }

        @Override
        public int vomSpieler(String material, int stueck) {
            return nimm(spieler.getInventory(), material, stueck);
        }

        @Override
        public int zumSpieler(String material, int stueck) {
            return lege(spieler.getInventory(), material, stueck, true);
        }

        /**
         * Nimmt bis zu {@code stueck} dieses Materials aus diesem Inventar.
         *
         * <p>Von Hand ueber die Plaetze und nicht mit {@code removeItem}: Jenes braucht
         * einen {@link ItemStack}, und dessen Bauplan schlaegt in der Registry nach - was
         * ohne laufenden Server wirft. Die Plaetze selbst sind gewoehnliche Felder.
         */
        private int nimm(Inventory woraus, String material, int stueck) {
            int offen = stueck;
            ItemStack[] plaetze = woraus.getContents();
            for (int i = 0; i < plaetze.length && offen > 0; i++) {
                ItemStack stapel = plaetze[i];
                if (stapel == null || !stapel.getType().name().equals(material)) {
                    continue;
                }
                merke(stapel);
                int weg = Math.min(offen, stapel.getAmount());
                if (weg >= stapel.getAmount()) {
                    woraus.setItem(i, null);
                } else {
                    stapel.setAmount(stapel.getAmount() - weg);
                    woraus.setItem(i, stapel);
                }
                offen -= weg;
            }
            return stueck - offen;
        }

        /**
         * Bewahrt ein Exemplar dieses Stapels fuer den Rueckweg.
         *
         * <p>Behalten wird der ZULETZT angefasste: Liegen mehrere Gestalten desselben
         * Materials nebeneinander - zwei verschiedene geschriebene Buecher -, kommt eine
         * davon zweimal zurueck. Das ist die alte Vereinfachung dieser Naht, die auch
         * {@link #vorlageFuer} nennt, und sie ist jetzt der schlimmste Fall; vorher war
         * der schlimmste Fall ein geloeschter Gegenstand.
         */
        private void merke(ItemStack stapel) {
            ItemStack eines = stapel.clone();
            eines.setAmount(1);
            gegangen.put(stapel.getType().name(), eines);
        }

        /**
         * Legt bis zu {@code stueck} dieses Materials in dieses Inventar.
         *
         * <p>Der Stapel wird aus einem VORHANDENEN abgeleitet, wo es einen gibt: Nur so
         * bleiben Namen und Verzauberungen erhalten. Gibt es keinen mehr - er ist gerade
         * ganz herausgenommen worden -, bleibt nur ein frischer, und dann ist es ein
         * gewoehnliches Stueck. Das kostet im schlimmsten Fall die Verzauberung eines
         * Stueckes, das ueber dem Tagesdeckel lag; der andere Weg waere, den Stapel beim
         * Nehmen aufzubewahren, und das verschoebe die ganze Rechnung in die Naht.
         */
        private int lege(Inventory hinein, String material, int stueck, boolean fallen) {
            ItemStack vorlage = vorlageFuer(material);
            if (vorlage == null) {
                return 0;
            }
            int offen = stueck;
            while (offen > 0) {
                // Stapelweise, weil addItem einen Stapel bekommt und nicht eine Zahl.
                // Was es nicht unterbringt, gibt es zurueck - und sobald etwas
                // zurueckkommt, ist das Inventar voll und ein weiterer Versuch sinnlos.
                int diesmal = Math.min(offen, vorlage.getMaxStackSize());
                ItemStack teil = vorlage.clone();
                teil.setAmount(diesmal);
                int blieben = hinein.addItem(teil).values().stream()
                        .mapToInt(ItemStack::getAmount).sum();
                offen -= diesmal - blieben;
                if (blieben > 0) {
                    break;
                }
            }
            if (offen > 0 && fallen) {
                // Vor seine Fuesse und nicht ins Nichts: Es ist seins, und ein stilles
                // Verschwinden waere ein Verlust, den niemand nachweisen kann.
                ItemStack rest = vorlage.clone();
                rest.setAmount(Math.min(offen, vorlage.getMaxStackSize()));
                spieler.getWorld().dropItemNaturally(spieler.getLocation(), rest);
                return stueck;
            }
            return stueck - offen;
        }

        /**
         * Ein Exemplar dieses Materials, aus einem Stapel, der schon da ist.
         *
         * <p>Gesucht wird in BEIDEN Inventaren und zuletzt in {@link #gegangen}: Beim
         * Zuruecklegen liegt das Stueck noch beim Spieler, beim Zurueckgeben noch in der
         * Truhe - und wenn in keinem von beiden mehr eines liegt, dann weil dieselbe Hand
         * es eben herausgenommen hat. Genau dafuer merkt sie sich, was durch sie
         * hindurchgegangen ist; ohne diesen letzten Griff war der Gegenstand geloescht.
         *
         * <p>Findet sich auch dort nichts, gibt es wirklich nichts zu legen: Dann hat
         * diese Hand dieses Material nie angefasst.
         */
        private ItemStack vorlageFuer(String material) {
            for (Inventory woraus : List.of(spieler.getInventory(), truhe)) {
                for (ItemStack stapel : woraus.getContents()) {
                    if (stapel != null && stapel.getType().name().equals(material)) {
                        ItemStack eines = stapel.clone();
                        eines.setAmount(1);
                        return eines;
                    }
                }
            }
            ItemStack gemerkt = gegangen.get(material);
            return gemerkt == null ? null : gemerkt.clone();
        }
    }
}
