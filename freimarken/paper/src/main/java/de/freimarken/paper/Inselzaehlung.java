package de.freimarken.paper;

import de.freimarken.kern.Punktebuch;
import de.freimarken.kern.Zaehlauftrag;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;

/**
 * Die Hand, die eine Insel ZAEHLT - Chunk fuer Chunk, Takt fuer Takt (Teil E).
 *
 * <p><b>Warum sie in Happen arbeitet:</b> Ein Wolkenfeld ist 128 Bloecke breit und reicht
 * ueber die ganze Welthoehe - 64 Chunks mit mehr als sechs Millionen Bloecken. In einem
 * Zug gezaehlt stuende der Server eine Sekunde oder laenger, und zwar fuer alle. Ein Chunk
 * je Takt ist unmerklich; die Groesse steht in {@code wolken.punkte.chunks-je-takt}.
 *
 * <p><b>Was gezaehlt wird, rechnet {@link Zaehlauftrag}; WAS in einem Chunk liegt, sagt
 * die {@link Chunkschau}.</b> Diese Teilung ist der Grund, warum der ganze Ablauf - die
 * Reihenfolge der Chunks, die Teilsummen, das Ende, der Abbruch - ohne laufenden Server
 * durch eine Probe laeuft.
 *
 * <p><b>Jeder Takt ist in {@code try} gefasst</b>, und das ist keine Vorsicht, sondern
 * Pflicht: Eine Ausnahme in einem getakteten Auftrag meldet Bukkit NICHT ab - sie kaeme
 * bei jedem Tick wieder, und das Protokoll waere in Minuten voll. Also: abmelden,
 * freigeben, EINMAL melden.
 */
final class Inselzaehlung {

    /**
     * Was in einem Chunk liegt - je Blockname die Zahl der Stuecke.
     *
     * <p>Die Naht zum Server, und sie ist hier besonders tief: Dahinter liegen
     * {@code World.getChunkAt} und {@code Material}, und beide brauchen einen laufenden
     * Server.
     */
    interface Chunkschau {

        Map<String, Long> zaehle(int chunkX, int chunkZ);
    }

    /** Der Zeitgeber - dieselbe Form wie bei {@code Blockwache.Taktquelle}. */
    interface Taktquelle {

        /**
         * Stellt den Zeitgeber.
         *
         * @param schlag       was jeden Takt zu tun ist
         * @param abstandTicks wie viele Ticks zwischen zwei Schlaegen liegen
         * @return was diesen Zeitgeber wieder abmeldet - nie {@code null}
         */
        Runnable stelle(Runnable schlag, long abstandTicks);
    }

    /** Ein Takt je Tick: 64 Chunks sind damit gut drei Sekunden. */
    private static final long ABSTAND_TICKS = 1L;

    private final Punktebuch buch;
    private final Chunkschau schau;
    private final Taktquelle taktquelle;
    private final Logger protokoll;

    Inselzaehlung(Punktebuch buch, Chunkschau schau, Taktquelle taktquelle,
                  Logger protokoll) {
        if (buch == null || schau == null || taktquelle == null) {
            throw new IllegalArgumentException("Ohne Punktebuch, Chunkschau und"
                    + " Taktquelle gibt es keine Zaehlung");
        }
        this.buch = buch;
        this.schau = schau;
        this.taktquelle = taktquelle;
        this.protokoll = protokoll;
    }

    /**
     * Zaehlt diesen Auftrag ab - und meldet am Ende die Punktzahl.
     *
     * <p>Der Auftrag kommt aus {@link Punktebuch#beginne}, und dort ist die Insel schon
     * als laufend vermerkt. Diese Methode MUSS ihn also zu Ende bringen oder freigeben -
     * beides tut sie, auch im Fehlerfall.
     *
     * @param fertig was mit der Punktzahl geschieht - im Spiel eine Zeile im Chat
     */
    void zaehle(Zaehlauftrag auftrag, LongConsumer fertig) {
        if (auftrag == null) {
            throw new IllegalArgumentException("Ohne Auftrag gibt es nichts zu zaehlen");
        }
        Lauf lauf = new Lauf(auftrag, fertig);
        lauf.abmelden = taktquelle.stelle(lauf, ABSTAND_TICKS);
    }

    /** Ein laufender Auftrag - er haelt seine Abmeldung selbst. */
    private final class Lauf implements Runnable {

        private final Zaehlauftrag auftrag;
        private final LongConsumer fertig;
        private Runnable abmelden;

        /**
         * Ob schon geschrieben wurde.
         *
         * <p>Der Riegel gegen die doppelte Zeile: Wenn die Abmeldung noch nicht
         * zurueckgekommen ist (ein Taktgeber, der sofort schlaegt), kommt derselbe Lauf
         * ein zweites Mal - und ohne dieses Feld stuenden zwei Punktzahlen in der Ablage,
         * von denen die zweite die erste ueberschreibt.
         */
        private boolean geschrieben;

        private Lauf(Zaehlauftrag auftrag, LongConsumer fertig) {
            this.auftrag = auftrag;
            this.fertig = fertig;
        }

        @Override
        public void run() {
            if (geschrieben) {
                halt();
                return;
            }
            try {
                int jeTakt = buch.werte().chunksJeTakt();
                for (int[] chunk : auftrag.naechsterHappen(jeTakt)) {
                    auftrag.nimm(schau.zaehle(chunk[0], chunk[1]));
                }
                if (auftrag.fertig()) {
                    geschrieben = true;
                    halt();
                    long punkte = buch.fertig(auftrag);
                    if (fertig != null) {
                        fertig.accept(punkte);
                    }
                }
            } catch (RuntimeException fehler) {
                // EINMAL melden, dann Ruhe - siehe Klassenkopf.
                geschrieben = true;
                halt();
                buch.abbrechen(auftrag);
                if (protokoll != null) {
                    protokoll.log(Level.WARNING, "Die Blockzaehlung der Insel "
                            + auftrag.inselId() + " ist bei "
                            + auftrag.fortschrittProzent() + " Prozent abgebrochen."
                            + " Die Punktzahl bleibt, wie sie war.", fehler);
                }
            }
        }

        private void halt() {
            if (abmelden != null) {
                abmelden.run();
            }
        }
    }

    /**
     * Die Chunkschau, wie der Server sie baut.
     *
     * <p><b>Von unten bis zum HOECHSTEN Block und nicht bis zur Welthoehe</b>, und das ist
     * der ganze Unterschied in dieser Welt: Ueber einer Wolkeninsel ist alles Luft. Mit
     * {@code getHighestBlockYAt} liest ein leerer Chunk 256 Spalten und keinen Block;
     * ohne diese Grenze waeren es 98.304 Abfragen je Chunk, von denen 99 Prozent Luft
     * finden.
     *
     * <p><b>{@code getChunkAt} und nicht {@code isChunkLoaded}</b>: Gezaehlt wird das
     * ganze Feld, auch der Teil, den gerade niemand sieht. In einer Welt aus Leere ist das
     * Erzeugen eines Chunks billig - er enthaelt keinen Block.
     */
    static Chunkschau ueberBukkit(World welt) {
        if (welt == null) {
            throw new IllegalArgumentException("Ohne Welt gibt es nichts zu zaehlen");
        }
        return (chunkX, chunkZ) -> {
            // EIN SCHNAPPSCHUSS und keine Blockobjekte: Jener ist ein Feld von
            // Zustaenden, dieses waere ein Objekt je Block - sechs Millionen Stueck.
            ChunkSnapshot schnitt = welt.getChunkAt(chunkX, chunkZ)
                    .getChunkSnapshot(false, false, false);
            Map<String, Long> gefunden = new HashMap<>();
            int unten = welt.getMinHeight();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int oben = schnitt.getHighestBlockYAt(x, z);
                    for (int y = unten; y <= oben; y++) {
                        Material art = schnitt.getBlockType(x, y, z);
                        if (art == Material.AIR || art == Material.CAVE_AIR
                                || art == Material.VOID_AIR) {
                            continue;
                        }
                        gefunden.merge(art.name().toLowerCase(Locale.ROOT), 1L,
                                Long::sum);
                    }
                }
            }
            return gefunden;
        };
    }
}
