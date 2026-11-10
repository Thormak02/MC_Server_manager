package de.freimarken.paper;

import de.freimarken.kern.Blockort;
import de.freimarken.kern.Truhenverzeichnis;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Die einzige Stelle, die Bukkit und das taegliche Leeren der Freien Truhe zusammenbringt.
 *
 * <p>Dieselbe Rolle wie {@link Ladenware} fuer die Ladentruhen: Der {@link Truhenlauf}
 * entscheidet WANN, diese Klasse weiss WIE. Darum ist der Tageswechsel ohne Server
 * pruefbar und diese Klasse eine Handvoll Zeilen, die nichts entscheiden.
 *
 * <p><b>Eine Truhe mit Zuschauer wird NICHT geleert</b>, sondern auf den naechsten Takt
 * verschoben ({@link Truhenlauf#SPAETER}). Das ist die Zusage, an der die ganze Zaehlung
 * haengt: Die {@link Truhenwache} rechnet die Entnahme als Unterschied zwischen Oeffnen
 * und Schliessen, und ein Leeren dazwischen machte den ganzen Inhalt zur Entnahme dieses
 * Spielers.
 *
 * <p><b>Eine fehlende Kiste ist keine Stoerung, sondern eine 0.</b> Wer eine Stelle in die
 * {@code config.yml} schreibt, an der keine Kiste steht, hat einen Fehler gemacht - aber
 * kein Grund, den Lauf jede Minute neu zu versuchen. Gesagt wird es beim ersten Mal und
 * dann nicht mehr: Der Lauf laeuft einmal am Tag, und eine Zeile am Tag ueber dieselbe
 * fehlende Kiste ist ein Protokoll, das niemand mehr liest.
 */
final class Truhenleerung implements Truhenlauf.Leerung {

    private final Server server;
    private final Logger protokoll;

    /**
     * Welche Stelle schon beklagt wurde - damit es nicht jeden Tag dieselbe Zeile gibt.
     *
     * <p>Als Text und nicht als {@link Blockort}: Verglichen wird genau die Zeile, die im
     * Protokoll stuende, und die traegt die Welt mit. Nur im Arbeitsspeicher - nach einem
     * Neustart wird die Klage einmal wiederholt, und das ist die richtige Richtung: Wer
     * den Server neu startet, hat womoeglich gerade die {@code config.yml} angefasst.
     */
    private final Set<String> schonBeklagt = new HashSet<>();

    Truhenleerung(Server server, Logger protokoll) {
        if (server == null || protokoll == null) {
            throw new IllegalArgumentException("Server und Protokoll sind Pflicht");
        }
        this.server = server;
        this.protokoll = protokoll;
    }

    @Override
    public int leere(Blockort ort) {
        World welt = weltMit(ort.welt());
        if (welt == null) {
            // Nicht geladen heisst SPAETER und nicht 0: Eine Welt kann im Betrieb
            // nachgeladen werden, und dann soll der naechste Takt die Truhe finden.
            klageEinmal(ort, "die Welt " + ort.welt() + " ist nicht geladen");
            return Truhenlauf.SPAETER;
        }
        // getBlockAt laedt den Chunk, wenn er nicht geladen ist. Das ist gewollt und
        // dieselbe Entscheidung wie bei Ladenware: Der Lauf faehrt im Hauptfaden, und
        // eine Markthalle, deren Chunks gerade nicht geladen sind, soll trotzdem
        // geleert werden.
        Block block = welt.getBlockAt(ort.x(), ort.y(), ort.z());
        Inventory inventar = Ladenware.inventarAn(block);
        if (inventar == null) {
            klageEinmal(ort, "dort steht keine Kiste, sondern " + block.getType());
            return 0;
        }
        if (!inventar.getViewers().isEmpty()) {
            return Truhenlauf.SPAETER;
        }
        int stueck = 0;
        for (ItemStack stapel : inventar.getContents()) {
            if (stapel != null) {
                stueck += stapel.getAmount();
            }
        }
        inventar.clear();
        return stueck;
    }

    private World weltMit(String schluessel) {
        for (World welt : server.getWorlds()) {
            if (welt.key().asString().equals(schluessel)) {
                return welt;
            }
        }
        return null;
    }

    private void klageEinmal(Blockort ort, String warum) {
        String zeile = "Die Freie Truhe bei " + ort.welt() + " " + ort.x() + "/"
                + ort.y() + "/" + ort.z() + " laesst sich nicht leeren: " + warum
                + ". Die Stelle steht in " + Truhenverzeichnis.PFAD_TRUHEN + ".";
        if (schonBeklagt.add(zeile)) {
            protokoll.warning(zeile);
        }
    }
}
