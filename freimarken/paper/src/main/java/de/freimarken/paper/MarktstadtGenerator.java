package de.freimarken.paper;

import de.freimarken.kern.Rasterplan;
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
 * Der Weltgenerator der Marktstadt: Flachland mit Parzellenraster, Mauern und Wegen.
 *
 * <p>Die Geometrie steht nicht hier, sondern im {@link Rasterplan} im Kern - derselbe Plan,
 * den spaeter auch die Besitzpruefung und das Zuruecksetzen einer Parzelle benutzen. Hier
 * werden nur Bloecke gesetzt.
 *
 * <p>Drei Dinge, die beim Schreiben eines Generators regelmaessig schiefgehen und hier
 * ausdruecklich beruecksichtigt sind:
 *
 * <ul>
 *   <li>Die {@code shouldGenerate*}-Schalter steuern das VANILLE-Gelaende, nicht die
 *       eigenen Methoden. Stehen sie auf true, liegt im ChunkData schon eine
 *       Rauschlandschaft, bevor der eigene Code laeuft.
 *   <li>{@code setRegion} ist oben AUSSCHLIESSEND. Eine Schicht zu viel oder zu wenig ist
 *       der haeufigste Fehler.
 *   <li>Die Spalte wird vollstaendig beschrieben, von Grundgestein bis Bauhoehe. Falls ein
 *       Schalter in einer kuenftigen Version doch wirkungslos wird, ueberschreibt der
 *       Generator das Vanille-Gelaende, statt Reste am Parzellenrand stehen zu lassen.
 * </ul>
 */
public final class MarktstadtGenerator extends ChunkGenerator {

    private final Rasterplan plan;
    private final BiomeProvider biome = new EinBiom();

    public MarktstadtGenerator(Rasterplan plan) {
        this.plan = plan;
    }

    public Rasterplan plan() {
        return plan;
    }

    @Override
    public void generateSurface(WorldInfo welt, Random zufall, int chunkX, int chunkZ,
                                ChunkData daten) {
        int unten = daten.getMinHeight();
        int oben = daten.getMaxHeight();

        daten.setRegion(0, unten, 0, 16, unten + 1, 16, Material.BEDROCK);
        daten.setRegion(0, unten + 1, 0, 16, Stadtboden.ERDE_AB, 16, Material.STONE);
        daten.setRegion(0, Stadtboden.ERDE_AB, 0, 16, Stadtboden.BODEN, 16, Material.DIRT);
        // Alles oberhalb der Mauerkrone leeren - siehe dritter Punkt oben.
        daten.setRegion(0, Stadtboden.BODEN + 2, 0, 16, oben, 16, Material.AIR);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                // Welcher Block wohin gehoert, steht in Stadtboden - in derselben Tabelle,
                // aus der das Auftrennen einer Gruppe den Weg zurueckbaut. Stuende die
                // Zuordnung hier, saehe ein zurueckgebauter Weg nach der ersten Aenderung
                // anders aus als der Rest der Stadt, und zwar fuer immer.
                Rasterplan.Feld feld = plan.feldBei(chunkX * 16 + lx, chunkZ * 16 + lz);
                daten.setBlock(lx, Stadtboden.BODEN, lz, Stadtboden.oberflaeche(feld));
                daten.setBlock(lx, Stadtboden.BODEN + 1, lz, Stadtboden.darueber(feld));
            }
        }
    }

    // --- Vanille-Gelaende vollstaendig abschalten -------------------------------------

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
        return false;
    }

    @Override
    public boolean shouldGenerateMobs(WorldInfo welt, Random zufall, int chunkX, int chunkZ) {
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
        // Mitte der Parzelle 0/0, einen Block ueber dem Boden.
        return new Location(welt, plan.mitteX(0) + 0.5, Stadtboden.BODEN + 1,
                plan.mitteZ(0) + 0.5);
    }

    /**
     * Ein Biom fuer die ganze Welt.
     *
     * <p>Wird aus mehreren Generierungs-Threads gerufen und hat deshalb keinen
     * veraenderlichen Zustand - kein Zwischenspeicher, keine Datenbankabfrage, nichts.
     */
    private static final class EinBiom extends BiomeProvider {

        @Override
        public Biome getBiome(WorldInfo welt, int x, int y, int z) {
            return Biome.PLAINS;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo welt) {
            // Wird genau einmal beim Laden der Welt gefragt; spaetere Aenderungen wirken
            // nicht mehr.
            return List.of(Biome.PLAINS);
        }
    }
}
