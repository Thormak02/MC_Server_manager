package de.freimarken.paper;

import de.freimarken.kern.Inselplan;
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
 * Der Weltgenerator von Hafenwerder: gleiche Inseln in gleichem Abstand, dazwischen Meer.
 *
 * <p>Das Geschwister des {@link StadtfelderGenerator}, und wie dort steht die Geometrie
 * NICHT hier, sondern im {@link Inselplan} im Kern - derselbe Plan, den spaeter die
 * Besitzpruefung und der Hafen benutzen. Hier werden nur Bloecke gesetzt, und welcher
 * wohin gehoert, steht in {@link Inselboden}.
 *
 * <p><b>Der Normalfall dieser Welt ist MEER</b>, nicht Nichts. Darum ist jede Spalte
 * dieselbe Folge - Grundgestein, Stein, Erde, ein Oberflaechenblock, darueber Wasser bis
 * zur Wasserlinie - und nur ihre Hoehe aendert sich: Inselinneres auf 63, der Hang
 * darunter, Meeresgrund auf 55. Eine Spalte ohne jeden Block gibt es nicht; wo nichts
 * steht, steht Wasser.
 *
 * <p>Drei Dinge, die beim Schreiben eines Generators regelmaessig schiefgehen und hier
 * ausdruecklich beruecksichtigt sind - dieselben wie beim Vorbild:
 *
 * <ul>
 *   <li>Die {@code shouldGenerate*}-Schalter steuern das VANILLE-Gelaende, nicht die
 *       eigenen Methoden. Stehen Rauschen und Oberflaeche auf true, liegt im ChunkData
 *       schon eine Landschaft, bevor der eigene Code laeuft.
 *   <li>{@code setRegion} ist oben AUSSCHLIESSEND.
 *   <li>Die Spalte wird vollstaendig beschrieben, von Grundgestein bis zur Wasserlinie.
 * </ul>
 *
 * <p><b>Was hier anders als bei den Stadtfeldern angeschaltet BLEIBT</b> [18.11.2026]:
 * Bewuchs und Tiere. Ein Stadtfeld soll sauberes Bauland sein - ein Baum darauf ist im
 * Weg. Eine Insel ist das Gegenteil: Sie soll nach Insel aussehen, und das heisst Gras,
 * Blumen, ein paar Baeume, Seegras und Korallen im flachen Wasser, Fische und Tintenfische
 * im warmen Meer. Die Tiere sind dabei kein Beiwerk - ohne den Durchgang beim Erzeugen
 * kommen Kuehe und Schafe spaeter von selbst nie dazu. BAUWERKE bleiben aus: Ein
 * Ozeanmonument mit Wachen mitten auf einer bezahlten Insel ist genau das, was niemand
 * bestellt hat.
 */
public final class InselnGenerator extends ChunkGenerator {

    private final Inselplan plan;
    private final BiomeProvider biome;

    public InselnGenerator(Inselplan plan) {
        this.plan = plan;
        this.biome = new Inselbiom(plan);
    }

    public Inselplan plan() {
        return plan;
    }

    @Override
    public void generateSurface(WorldInfo welt, Random zufall, int chunkX, int chunkZ,
                                ChunkData daten) {
        int unten = daten.getMinHeight();
        int oben = daten.getMaxHeight();
        // ERST LEEREN, dann fuellen - wie beim Vorbild. Jeder Block, der danach steht,
        // ist einer, den dieser Code gesetzt hat.
        daten.setRegion(0, unten, 0, 16, oben, 16, Material.AIR);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = chunkX * 16 + lx;
                int z = chunkZ * 16 + lz;
                spalte(daten, lx, lz, unten, plan.tiefeDerOberflaeche(x, z),
                        plan.gestaltBei(x, z));
            }
        }
    }

    /**
     * Eine ganze Spalte, von der Weltunterkante bis zur Wasserlinie.
     *
     * <p>Fuenf Griffe statt zweihundert: Die Schichten sind zusammenhaengend, also setzt
     * jeder Griff eine ganze Schicht. Das ist der Unterschied zwischen einem Generierlauf
     * von Minuten und einem von Stunden - diese Welt hat rund 90.000 Chunks.
     *
     * @param tiefe wie tief der Gipfel dieser Spalte unter der Wasserlinie liegt
     */
    private static void spalte(ChunkData daten, int lx, int lz, int unten, int tiefe,
                               Inselplan.Gestalt gestalt) {
        int gipfel = Inselboden.BODEN - tiefe;
        daten.setRegion(lx, unten, lz, lx + 1, unten + 1, lz + 1,
                Inselboden.darunter(unten, gipfel, unten));
        daten.setRegion(lx, unten + 1, lz, lx + 1, gipfel - Inselboden.ERDE_TIEF, lz + 1,
                Inselboden.darunter(unten + 1, gipfel, unten));
        daten.setRegion(lx, gipfel - Inselboden.ERDE_TIEF, lz, lx + 1, gipfel, lz + 1,
                Inselboden.darunter(gipfel - Inselboden.ERDE_TIEF, gipfel, unten));
        daten.setBlock(lx, gipfel, lz, Inselboden.oberflaeche(gestalt, tiefe));
        if (gipfel < Inselboden.MEERESSPIEGEL) {
            // Der Riegel ist kein Schmuck: Auf dem Inselinneren liegt der Gipfel UEBER
            // der Wasserlinie, und eine Spanne von 64 bis 63 waere eine, die oben unter
            // unten liegt. Was setRegion damit tut, ist nirgends zugesagt.
            daten.setRegion(lx, gipfel + 1, lz, lx + 1, Inselboden.MEERESSPIEGEL + 1, lz + 1,
                    Material.WATER);
        }
    }

    // --- Vanille-GELAENDE abschalten, Bewuchs und Tiere behalten ----------------------

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
        // ANGESCHALTET, anders als bei den Stadtfeldern: Gras, Blumen und ein paar Baeume
        // auf der Insel, Seegras und Korallen im flachen Wasser. Eine Insel aus nacktem
        // Gras ist eine Baustelle, und der Reiz dieser Welt ist, dass sie keine ist.
        return true;
    }

    @Override
    public boolean shouldGenerateMobs(WorldInfo welt, Random zufall, int chunkX, int chunkZ) {
        // ANGESCHALTET, und das ist der Durchgang, der sich nicht nachholen laesst:
        // Friedliche Tiere entstehen in Minecraft beim ERZEUGEN eines Chunks und spaeter
        // so gut wie nie. Ohne ihn blieben die Inseln fuer immer ohne Kuehe und Schafe.
        return true;
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
        // Die Mitte der ANKUNFTSINSEL, einen Block ueber dem Boden. Sie ist die einzige
        // Insel, die niemandem gehoert - jeder andere Erscheinungspunkt setzte einen
        // frisch Angekommenen auf fremdes Eigentum oder ins Wasser.
        return new Location(welt, plan.ankunftsMitte() + 0.5, Inselboden.BODEN + 1,
                plan.ankunftsMitte() + 0.5);
    }

    /**
     * Wiese auf der Insel, Sand am Strand, warmes Meer dazwischen.
     *
     * <p>Drei Biome, und jedes traegt eine Entscheidung: Das Biom bestimmt, was WAECHST
     * und was ERSCHEINT. Auf der Wiese Blumen und Tiere, am Strand nichts von beidem -
     * ein Strand voller Blumen waere keiner -, und im warmen Meer Seegras, Korallen und
     * tropische Fische. Genau diese Fische sind der Grund, warum es das warme und nicht
     * das gewoehnliche Meer ist: Hafenwerder soll nach Fang aussehen (Plan 4.1).
     *
     * <p>Wird aus mehreren Generierungs-Threads gerufen und hat deshalb keinen
     * veraenderlichen Zustand - der Plan ist eine reine Rechnung.
     */
    private static final class Inselbiom extends BiomeProvider {

        private final Inselplan plan;

        private Inselbiom(Inselplan plan) {
            this.plan = plan;
        }

        @Override
        public Biome getBiome(WorldInfo welt, int x, int y, int z) {
            return switch (plan.gestaltBei(x, z)) {
                case LAND -> Biome.PLAINS;
                case STRAND -> Biome.BEACH;
                case WASSER -> Biome.WARM_OCEAN;
            };
        }

        @Override
        public List<Biome> getBiomes(WorldInfo welt) {
            // Wird genau einmal beim Laden der Welt gefragt; spaetere Aenderungen wirken
            // nicht mehr. Was hier fehlt, kann getBiome nie zurueckgeben.
            return List.of(Biome.PLAINS, Biome.BEACH, Biome.WARM_OCEAN);
        }
    }
}
