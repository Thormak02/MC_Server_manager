package de.freimarken.paper;

import de.freimarken.kern.Abholgut;
import de.freimarken.kern.Ladeneinheit;
import de.freimarken.kern.Ladenverzeichnis;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Die Gegenstaende der Markthalle - die EINZIGE Stelle, die Bukkit und das Abholfach
 * zusammenbringt.
 *
 * <p>Alles hier haengt am laufenden Server und ist darum ohne ihn nicht pruefbar
 * ({@code ItemStack.serializeAsBytes} greift auf die Registry zu, {@code new ItemStack}
 * wirft ohne Server - gemessen). Genau deshalb ist diese Klasse so DUENN wie moeglich:
 * Sie wandelt um, liest eine Truhe und leert sie. Jede Entscheidung - wann geraeumt wird,
 * was ins Fach wandert, was der Spieler liest - steht in {@link Mietlauf} und
 * {@link Abholbuch}, und die sind ohne Server pruefbar.
 *
 * <p><b>Base64 ueber {@code serializeAsBytes} und nicht YAML.</b> Das ist der von Paper
 * vorgesehene Weg, und er traegt ueber Spielversionen: Die Bytes gehen durch den
 * Datenfixer, wenn ein Update die Gegenstandsform aendert. Ein
 * {@code BukkitObjectOutputStream} schriebe dagegen serialisierte Java-Objekte, und die
 * brechen bei einer Umbenennung in Bukkit selbst.
 *
 * <p><b>Nichts hier wirft.</b> Ein Gegenstand, der sich nicht umwandeln laesst, ist
 * {@code null} oder wird uebergangen - und steht im Protokoll. Ein Wurf aus
 * {@link #lese} liesse einen Laden fuer immer unraeumbar; ein Wurf aus
 * {@link Zusteller#lesbar} liesse ein Fach fuer immer unholbar.
 */
final class Ladenware implements Mietlauf.Truhe {

    private final Server server;
    private final Ladenverzeichnis verzeichnis;
    private final String weltschluessel;
    private final Logger protokoll;

    /**
     * @param weltschluessel die Welt der Markthalle, aus {@code laeden.welt} - dieselbe
     *                       Zeichenfolge wie bei {@link LadenBefehle}. Eine zweite
     *                       Einstellung daneben waere eine Welt, in der gemietet und eine
     *                       andere, in der geraeumt wird
     */
    Ladenware(Server server, Ladenverzeichnis verzeichnis, String weltschluessel,
              Logger protokoll) {
        this.server = server;
        this.verzeichnis = verzeichnis;
        this.weltschluessel = weltschluessel;
        this.protokoll = protokoll;
    }

    @Override
    public List<Abholgut> lese(String einheit) {
        Inventory inventar = inventarVon(einheit);
        if (inventar == null) {
            return List.of();
        }
        List<Abholgut> inhalt = new ArrayList<>();
        for (ItemStack stapel : inventar.getContents()) {
            if (stapel == null || stapel.getType().isAir()) {
                continue;
            }
            try {
                inhalt.add(new Abholgut(beschriftung(stapel), alsText(stapel)));
            } catch (RuntimeException fehler) {
                // UEBERGANGEN und nicht geworfen: Sonst verhinderte ein einziger
                // unmoeglicher Gegenstand - einer, dessen Text ueber die Grenze von
                // Abholgut laeuft - die Raeumung der ganzen Einheit, und der Laden stuende
                // fuer immer da. Der Stapel bleibt in der Truhe liegen; die Truhe wird
                // zwar danach geleert, aber dieser eine Fall ist genannt statt
                // verschwiegen.
                protokoll.log(Level.WARNING, "Ein Stapel aus der Truhe von \"" + einheit
                        + "\" (" + stapel.getType() + " x" + stapel.getAmount()
                        + ") liess sich nicht ins Abholfach fassen und wird uebergangen.",
                        fehler);
            }
        }
        return inhalt;
    }

    @Override
    public void leere(String einheit) {
        Inventory inventar = inventarVon(einheit);
        if (inventar != null) {
            inventar.clear();
        }
    }

    /**
     * Der Zusteller fuer das Inventar DIESES Spielers.
     *
     * <p>Je Griff neu gebaut und nicht gehalten: Er zeigt auf ein Inventar, und das
     * wechselt mit jedem Beitritt.
     */
    static Abholbuch.Zusteller fuer(Player spieler) {
        return new Abholbuch.Zusteller() {
            @Override
            public boolean lesbar(String inhalt) {
                return ausText(inhalt) != null;
            }

            @Override
            public boolean gib(String inhalt) {
                ItemStack stapel = ausText(inhalt);
                if (stapel == null) {
                    return false;
                }
                // addItem gibt zurueck, was NICHT gepasst hat. Leer heisst: alles drin.
                // Das ist die Zusage, auf die sich Abholbuch verlaesst - ein "false"
                // fuehrt dort zum Zuruecklegen, und ein halb eingefuegter Stapel waere
                // dann halb verdoppelt. Darum wird nur ein VOLLSTAENDIG eingefuegter
                // Stapel als zugestellt gemeldet.
                return spieler.getInventory().addItem(stapel).isEmpty();
            }
        };
    }

    /**
     * Wie viele Stapel das Inventar dieses Spielers sicher aufnimmt.
     *
     * <p>Nur GANZ freie Plaetze, obwohl sich ein Stapel manchmal auf einen halb gefuellten
     * legen liesse. Die Unterschaetzung ist die sichere Richtung: Zu viel geholt heisst
     * verloren, zu wenig geholt heisst noch einmal tippen.
     *
     * <p>{@code getStorageContents} und nicht {@code getContents}: Jenes enthaelt auch
     * Ruestung und die zweite Hand, und ein freier Helmplatz ist kein Platz fuer einen
     * Stapel Holz.
     */
    static int freiePlaetze(Player spieler) {
        int frei = 0;
        for (ItemStack platz : spieler.getInventory().getStorageContents()) {
            if (platz == null || platz.getType().isAir()) {
                frei++;
            }
        }
        return frei;
    }

    /** Ein Stapel als Text - Base64 der Paper-Bytefolge. */
    static String alsText(ItemStack stapel) {
        return Base64.getEncoder().encodeToString(stapel.serializeAsBytes());
    }

    /**
     * Ein Text zurueck in einen Stapel - oder {@code null}, wenn das nicht geht.
     *
     * <p>{@code null} und kein Wurf: Der Aufrufer ({@link Abholbuch}) entscheidet dann,
     * den Stapel LIEGEN zu lassen und zu protokollieren. Ein Wurf liesse das ganze Fach
     * unholbar, und zwar wegen eines einzigen Stapels.
     */
    static ItemStack ausText(String inhalt) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(inhalt));
        } catch (RuntimeException fehler) {
            return null;
        }
    }

    /**
     * Was drin ist, in Worten - fuer das Protokoll.
     *
     * <p>Die Materialbezeichnung und nicht der Anzeigename: Ein Betreiber, der einen
     * verlorenen Stapel ersetzen soll, braucht das Wort, das er in {@code /give} tippt.
     * Ein uebersetzter Name waere schoener zu lesen und nicht zu gebrauchen.
     */
    static String beschriftung(ItemStack stapel) {
        return stapel.getAmount() + "x " + stapel.getType().name();
    }

    /**
     * Das Inventar der Ladentruhe dieser Einheit - oder {@code null}.
     *
     * <p>Jeder der drei Fehlfaelle klagt und gibt {@code null}: Die Welt ist nicht
     * geladen, die Einheit steht nicht mehr in der {@code config.yml}, oder an der
     * eingetragenen Stelle steht keine Truhe. Alle drei sind Fehler des Betreibers oder
     * der Welt, keine des Mieters - und der Lauf muss weiterlaufen. Eine leere Liste
     * bedeutet dann "keine Ware", und die Raeumung geht durch: Ein Laden, der wegen einer
     * fehlenden Truhe fuer immer stehen bliebe, waere das schlechtere Ergebnis.
     */
    private Inventory inventarVon(String einheit) {
        Ladeneinheit eine = verzeichnis.mitNamen(einheit);
        if (eine == null) {
            protokoll.warning("Die Ladeneinheit \"" + einheit + "\" steht nicht mehr in"
                    + " laeden.einheiten - ihre Truhe ist nicht zu finden.");
            return null;
        }
        World welt = null;
        for (World eineWelt : server.getWorlds()) {
            if (eineWelt.key().asString().equals(weltschluessel)) {
                welt = eineWelt;
                break;
            }
        }
        if (welt == null) {
            protokoll.warning("Die Ladenwelt " + weltschluessel + " ist nicht geladen -"
                    + " die Truhe von \"" + einheit + "\" ist nicht zu erreichen.");
            return null;
        }
        // getBlockAt laedt den Chunk, wenn er nicht geladen ist. Das ist gewollt: Der
        // Lauf faehrt im Hauptfaden, und eine Markthalle, deren Chunks gerade nicht
        // geladen sind, soll trotzdem geraeumt werden koennen.
        Block block = welt.getBlockAt(eine.truheX(), eine.truheY(), eine.truheZ());
        Inventory inventar = inventarAn(block);
        if (inventar == null) {
            protokoll.warning("Bei " + eine.truheAlsText() + " steht keine Truhe, obwohl"
                    + " laeden.einheiten sie fuer \"" + einheit + "\" dort eintraegt"
                    + " (dort steht " + block.getType() + ").");
        }
        return inventar;
    }

    /**
     * Das Inventar der Ladentruhe an dieser Stelle - oder {@code null}.
     *
     * <p><b>Die EINE Stelle, die "ist das eine Ladentruhe" beantwortet</b>, und darum
     * statisch und paketweit sichtbar: Gefragt wird sie beim taeglichen Lauf (zum Lesen
     * und Leeren) UND beim Start, wo {@code FreimarkenPlugin.pruefeLadeneinheiten} wissen
     * will, ob der Stand ueberhaupt benutzbar ist. Zwei Fassungen derselben Frage liefen
     * auseinander, und dann meldete der Start einen Stand als in Ordnung, den der Lauf
     * nicht raeumen kann.
     *
     * <p><b>{@code getState(false)} und nicht {@code getState()}.</b> Jenes baut einen
     * Schnappschuss des Behaelters samt Inhalt - eine Kopie, die hier verschwendet ist.
     * {@code getInventory()} gibt auf beiden das LEBENDE Inventar (nur
     * {@code getSnapshotInventory()} waere das kopierte), das Leeren wirkt also so oder
     * so; es kostet bloss nichts mehr. Dieselbe Entscheidung und derselbe Grund wie bei
     * {@code Shopware.inventarAn}, wo derselbe Pfad heiss ist.
     */
    static Inventory inventarAn(Block block) {
        return block.getState(false) instanceof Container behaelter
                ? behaelter.getInventory()
                : null;
    }
}
