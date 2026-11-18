package de.freimarken.paper;

import de.freimarken.kern.Chunkfolge;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.block.Biome;

/**
 * Die Hand, die das BIOM eines Feldes umsetzt - Chunk fuer Chunk, Takt fuer Takt (Teil F).
 *
 * <p><b>Warum in Happen:</b> Minecraft legt Biome in Zellen von 4 x 4 x 4 Bloecken ab. Ein
 * Wolkenfeld ist 128 Bloecke breit und reicht ueber die ganze Welthoehe - das sind 32 x 32
 * x 96 Zellen, also fast hunderttausend Setzvorgaenge. In einem Zug stuende der Server
 * dafuer; je Chunk sind es 1.536, und das ist ein Takt.
 *
 * <p><b>Dieselbe Happenrechnung wie die Zaehlung</b> ({@link Chunkfolge}) und dieselbe
 * {@link Inselzaehlung.Taktquelle}: Zwei eigene waeren zwei Orte fuer dieselbe Falle.
 *
 * <p><b>Sichtbar wird der Wechsel erst, wenn ein Spieler die Chunks neu bekommt.</b> Das
 * ist eine Eigenschaft von Minecraft und keine dieser Klasse: Biome stehen im Chunk, und
 * der Client hat ihn schon. Der Satz zum Befehl sagt es - sonst haelt ein Spieler den
 * bezahlten Wechsel fuer wirkungslos und tippt ihn wieder.
 */
final class Biomhand {

    /**
     * Setzt das Biom in EINEM Chunk - die Naht zum Server.
     *
     * <p>Dahinter liegen {@code World.setBiome} und {@code Biome}, und beide brauchen
     * einen laufenden Server. Ohne diese Naht liefe keine Probe durch den Ablauf.
     */
    interface Biomschau {

        void setze(int chunkX, int chunkZ, String biom);
    }

    /** Die Kantenlaenge einer Biomzelle in Bloecken - Minecrafts Rasterung. */
    static final int ZELLE = 4;

    private final Inselzaehlung.Taktquelle taktquelle;
    private final Biomschau schau;
    private final Logger protokoll;

    Biomhand(Inselzaehlung.Taktquelle taktquelle, Biomschau schau, Logger protokoll) {
        if (taktquelle == null || schau == null) {
            throw new IllegalArgumentException("Ohne Taktquelle und Biomschau gibt es"
                    + " keinen Biomwechsel");
        }
        this.taktquelle = taktquelle;
        this.schau = schau;
        this.protokoll = protokoll;
    }

    /**
     * Setzt das Biom ueber das ganze Feld - getaktet.
     *
     * @param folge      die Chunks des Feldes
     * @param biom       der Name, kleingeschrieben wie in der Einstellungsdatei
     * @param chunksJeTakt wie viele Chunks ein Takt umsetzt
     * @param fertig     was danach geschieht; darf {@code null} sein
     */
    void setze(Chunkfolge folge, String biom, int chunksJeTakt, Runnable fertig) {
        if (folge == null || biom == null) {
            throw new IllegalArgumentException("Ohne Feld und Biom gibt es nichts zu"
                    + " setzen");
        }
        Lauf lauf = new Lauf(folge, biom, Math.max(1, chunksJeTakt), fertig);
        lauf.abmelden = taktquelle.stelle(lauf, 1L);
    }

    /** Ein laufender Wechsel - er haelt seine Abmeldung selbst. */
    private final class Lauf implements Runnable {

        private final Chunkfolge folge;
        private final String biom;
        private final int jeTakt;
        private final Runnable fertig;
        private Runnable abmelden;
        private boolean beendet;

        private Lauf(Chunkfolge folge, String biom, int jeTakt, Runnable fertig) {
            this.folge = folge;
            this.biom = biom;
            this.jeTakt = jeTakt;
            this.fertig = fertig;
        }

        @Override
        public void run() {
            if (beendet) {
                halt();
                return;
            }
            try {
                for (int[] chunk : folge.naechsterHappen(jeTakt)) {
                    schau.setze(chunk[0], chunk[1], biom);
                }
                if (folge.fertig()) {
                    beendet = true;
                    halt();
                    if (fertig != null) {
                        fertig.run();
                    }
                }
            } catch (RuntimeException fehler) {
                // EINMAL melden, dann Ruhe: Eine Ausnahme in einem getakteten Auftrag
                // meldet Bukkit NICHT ab - sie kaeme bei jedem Tick wieder.
                beendet = true;
                halt();
                if (protokoll != null) {
                    protokoll.log(Level.WARNING, "Der Biomwechsel auf " + biom + " ist"
                            + " bei " + folge.fortschrittProzent() + " Prozent"
                            + " abgebrochen. Das gewaehlte Biom steht in der Ablage und"
                            + " gilt fuer alles, was danach neu entsteht.", fehler);
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
     * Die Biomschau, wie der Server sie baut.
     *
     * <p>In Schritten von {@link #ZELLE}: Minecraft legt Biome in Zellen von 4 x 4 x 4 ab,
     * und ein {@code setBiome} je Block waere derselbe Vorgang 64-mal. Gesetzt wird von
     * der Weltuntergrenze bis zur Obergrenze - ein Biom nur in Bauhoehe waere eines, das
     * ueber dem Kopf und unter den Fuessen ein anderes ist, und genau daran haengt, was
     * erscheint.
     */
    static Biomschau ueberBukkit(World welt) {
        if (welt == null) {
            throw new IllegalArgumentException("Ohne Welt gibt es kein Biom zu setzen");
        }
        return (chunkX, chunkZ, biom) -> {
            Biome art = Biomtafel.zuBiom(biom);
            if (art == null) {
                // Der Leser der Einstellungsdatei hat jeden Namen geprueft, und die
                // Tafel deckt die ganze Liste (Tafel 31ay). Kommt hier doch etwas
                // anderes, wird NICHTS gesetzt - lieber das alte Biom als ein falsches.
                throw new IllegalStateException("Das Biom \"" + biom + "\" kennt die"
                        + " Biomtafel nicht");
            }
            int vonX = chunkX << 4;
            int vonZ = chunkZ << 4;
            for (int x = vonX; x < vonX + 16; x += ZELLE) {
                for (int z = vonZ; z < vonZ + 16; z += ZELLE) {
                    for (int y = welt.getMinHeight(); y < welt.getMaxHeight();
                            y += ZELLE) {
                        welt.setBiome(x, y, z, art);
                    }
                }
            }
        };
    }
}
