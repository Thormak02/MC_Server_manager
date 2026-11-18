package de.freimarken.paper;

import de.freimarken.kern.Wolkenbiome;
import de.freimarken.kern.Wolkenplan;
import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * Der Weltgenerator von Wolkenwerder: Leere, und darin ein einziges Feld mit Boden.
 *
 * <p>Das Geschwister des {@link InselnGenerator}s - und sein Gegenteil. Dort backt der
 * Generator 1.443 Inseln in das Gelaende; hier backt er EINE, das Ankunftsfeld, und sonst
 * nichts. Jede Startinsel setzt das Plugin beim Nehmen, und warum, steht im
 * {@link Wolkenplan}: Eine Truhe mit Inhalt kann kein Generator setzen, und was gesetzt
 * werden kann, kann auch zurueckgesetzt werden.
 *
 * <p><b>Das Ankunftsfeld muss er aber backen</b> [18.11.2026], und das ist keine
 * Bequemlichkeit: {@code Ankunftspunkt} sucht am Erscheinungspunkt den hoechsten FESTEN
 * Block und braucht zwei freie darueber. Findet er in acht Ringen keinen, sagt
 * {@code /warp wolkenwerder} ab, das Tor im Wolkenturm tut nichts, und der Start klagt bei
 * JEDEM Mal "kein Platz zum Stehen. Dorthin fuehrt keine Reise." In einer Welt aus Leere
 * ist das der Normalfall - deshalb liegt in Feld 0/0 eine Steinplattform mit Gelaender.
 *
 * <p>Die Geometrie steht NICHT hier, sondern im {@link Wolkenplan} im Kern - derselbe
 * Plan, den die Besitzpruefung und das Aufsetzen benutzen. Welcher Block wohin gehoert,
 * steht in {@link Wolkenboden}.
 */
public final class WolkenGenerator extends ChunkGenerator {

    private final Wolkenplan plan;
    private final BiomeProvider biome;

    public WolkenGenerator(Wolkenplan plan) {
        this.plan = plan;
        this.biome = new Wolkenbiom();
    }

    public Wolkenplan plan() {
        return plan;
    }

    @Override
    public void generateSurface(WorldInfo welt, Random zufall, int chunkX, int chunkZ,
                                ChunkData daten) {
        int unten = daten.getMinHeight();
        int oben = daten.getMaxHeight();
        // ERST LEEREN, dann fuellen - wie bei den Geschwistern. Jeder Block, der danach
        // steht, ist einer, den dieser Code gesetzt hat.
        daten.setRegion(0, unten, 0, 16, oben, 16, Material.AIR);
        if (!beruehrtAnkunftsfeld(chunkX, chunkZ)) {
            // Der Normalfall dieser Welt, und zwar fuer alle Chunks ausser vier: Luft.
            return;
        }
        int mitte = plan.ankunftsMitte();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                spalte(daten, lx, lz, chunkX * 16 + lx - mitte, chunkZ * 16 + lz - mitte);
            }
        }
    }

    /**
     * Eine Spalte des Ankunftsfeldes, von der tiefsten bis zur hoechsten Hoehe.
     *
     * <p>Nur die Spanne, die eine Insel ueberhaupt beruehrt - zehn Bloecke von 62 bis 71
     * und nicht 384. Was {@link Wolkenplan#stueckBei} nicht nennt, bleibt die Luft, die
     * das Leeren hinterlassen hat.
     *
     * @param dx Bloecke oestlich des Plattformmittelpunkts
     * @param dz Bloecke suedlich des Plattformmittelpunkts
     */
    private void spalte(ChunkData daten, int lx, int lz, int dx, int dz) {
        for (int y = Wolkenboden.untersteHoehe(plan); y <= Wolkenboden.obersteHoehe(plan);
                y++) {
            Wolkenplan.Stueck stueck = plan.stueckBei(Wolkenplan.Insel.ANKUNFT, dx,
                    y - Wolkenboden.BODEN, dz);
            Material block = Wolkenboden.block(Wolkenplan.Insel.ANKUNFT, stueck);
            if (block != Material.AIR) {
                daten.setBlock(lx, y, lz, block);
            }
        }
    }

    /**
     * Ob dieser Chunk ueberhaupt etwas von der Ankunftsplattform traegt.
     *
     * <p>Der billige Vortest vor der Schleife, und er entscheidet ueber die Laufzeit
     * dieser Welt: Die Plattform liegt in vier Chunks, die Welt hat 390.000. Ohne ihn
     * fragte jeder einzelne 2.560-mal den Plan nach einem Stueck, das es dort nicht gibt.
     */
    private boolean beruehrtAnkunftsfeld(int chunkX, int chunkZ) {
        return beruehrt(chunkX) && beruehrt(chunkZ);
    }

    private boolean beruehrt(int chunkAchse) {
        int von = chunkAchse * 16;
        int mitte = plan.ankunftsMitte();
        return von + 15 >= mitte - plan.plattformHalb()
                && von <= mitte + plan.plattformHalb();
    }

    // --- Vanille GANZ abschalten ------------------------------------------------------

    @Override
    public boolean shouldGenerateNoise(WorldInfo welt, Random zufall, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface(WorldInfo welt, Random zufall, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves(WorldInfo welt, Random zufall, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations(WorldInfo welt, Random zufall, int chunkX,
                                             int chunkZ) {
        // AUS, anders als bei den Inseln: Gras und Blumen auf einer Steinplattform gibt es
        // nicht, und in der Leere ist nichts zu bewachsen.
        return false;
    }

    @Override
    public boolean shouldGenerateMobs(WorldInfo welt, Random zufall, int chunkX, int chunkZ) {
        // AUS, und hier ist es kein Verzicht, sondern eine Feststellung: Wenn ein Chunk
        // dieser Welt entsteht, liegt darin kein einziger Block - die Startinsel kommt
        // erst danach, aus der Hand des Plugins. Der Durchgang fuer friedliche Tiere
        // faende also nichts, worauf eine Kuh stehen koennte, und zwar auch dann, wenn er
        // angeschaltet waere.
        //
        // Dass es in Wolkenwerder keine Kuehe und keine Schafe gibt, ist damit die Welt
        // selbst und keine Einstellung - und es ist genau die Aufgabe: Tiere muss man sich
        // holen. Monster erscheinen davon unberuehrt, die kommen im Dunkeln und zur
        // Laufzeit.
        return false;
    }

    @Override
    public boolean shouldGenerateStructures(WorldInfo welt, Random zufall, int chunkX,
                                            int chunkZ) {
        return false;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo welt) {
        return biome;
    }

    @Override
    public Location getFixedSpawnLocation(World welt, Random zufall) {
        // Die Mitte des ANKUNFTSFELDES, einen Block ueber der Plattform. Es ist das
        // einzige Feld, das niemandem gehoert - jeder andere Erscheinungspunkt setzte
        // einen frisch Angekommenen auf fremdes Eigentum oder in die Leere.
        return new Location(welt, plan.ankunftsMitte() + 0.5, Wolkenboden.BODEN + 1,
                plan.ankunftsMitte() + 0.5);
    }

    /**
     * Der Grundton der Welt - und die Farbtafel, aus der spaeter gewaehlt wird.
     *
     * <p>{@link #getBiome} gibt immer dasselbe: Beim Erzeugen eines Chunks ist noch nicht
     * entschieden, welches Biom die Insel darin eines Tages tragen wird - sie ist noch
     * nicht einmal vergeben. Das gewaehlte Biom setzt {@code /wolke biom} zur Laufzeit.
     *
     * <p>{@link #getBiomes} nennt dagegen die GANZE Tafel ({@link Wolkenbiome}), und das
     * ist der Grund, warum jene vollstaendig sein muss, bevor die Welt entsteht: Diese
     * Frage wird genau einmal beim Laden gestellt, und was hier fehlt, laesst sich in
     * dieser Welt nie setzen.
     *
     * <p>Wird aus mehreren Generierungs-Threads gerufen und hat deshalb keinen
     * veraenderlichen Zustand.
     */
    private static final class Wolkenbiom extends BiomeProvider {

        @Override
        public Biome getBiome(WorldInfo welt, int x, int y, int z) {
            return Biomtafel.zuBiom(Wolkenbiome.VORGABE);
        }

        @Override
        public List<Biome> getBiomes(WorldInfo welt) {
            return Biomtafel.alle();
        }
    }
}
