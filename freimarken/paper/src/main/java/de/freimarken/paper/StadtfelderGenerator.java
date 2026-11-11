package de.freimarken.paper;

import de.freimarken.kern.Stadtfeldplan;
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
 * Der Weltgenerator der Freistaedte: Stadtfelder in der Leere, mit unsichtbarem Rand.
 *
 * <p>Das Geschwister des {@link MarktstadtGenerator}, und wie dort steht die Geometrie
 * NICHT hier, sondern im {@link Stadtfeldplan} im Kern - derselbe Plan, den spaeter die
 * Besitzpruefung und der Warp benutzen. Hier werden nur Bloecke gesetzt, und welcher
 * wohin gehoert, steht in {@link Feldboden}.
 *
 * <p><b>Der Normalfall dieser Welt ist NICHTS.</b> Darum wird jeder Chunk zuerst ganz
 * geleert und danach nur dort gefuellt, wo ein Stadtfeld oder sein Rand liegt. Umgekehrt
 * - Boden legen und die Leere wieder herausschneiden - haette bei jedem vergessenen Fall
 * einen Steg zwischen zwei Staedten stehen lassen, und genau den soll es nicht geben.
 *
 * <p>Drei Dinge, die beim Schreiben eines Generators regelmaessig schiefgehen und hier
 * ausdruecklich beruecksichtigt sind - dieselben wie beim Vorbild:
 *
 * <ul>
 *   <li>Die {@code shouldGenerate*}-Schalter steuern das VANILLE-Gelaende, nicht die
 *       eigenen Methoden. Stehen sie auf true, liegt im ChunkData schon eine
 *       Rauschlandschaft, bevor der eigene Code laeuft - in einer Welt aus Leere faellt
 *       das besonders auf.
 *   <li>{@code setRegion} ist oben AUSSCHLIESSEND.
 *   <li>Die Spalte wird vollstaendig beschrieben, von Grundgestein bis Bauhoehe.
 * </ul>
 */
public final class StadtfelderGenerator extends ChunkGenerator {

    private final Stadtfeldplan plan;
    private final BiomeProvider biome;

    public StadtfelderGenerator(Stadtfeldplan plan) {
        this.plan = plan;
        this.biome = new FeldBiom(plan);
    }

    public Stadtfeldplan plan() {
        return plan;
    }

    @Override
    public void generateSurface(WorldInfo welt, Random zufall, int chunkX, int chunkZ,
                                ChunkData daten) {
        int unten = daten.getMinHeight();
        int oben = daten.getMaxHeight();
        // ERST LEEREN, dann fuellen - siehe den Kopf der Klasse.
        daten.setRegion(0, unten, 0, 16, oben, 16, Material.AIR);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                Stadtfeldplan.Feld feld =
                        plan.feldBei(chunkX * 16 + lx, chunkZ * 16 + lz);
                if (feld == Stadtfeldplan.Feld.LEERE) {
                    continue;
                }
                if (feld == Stadtfeldplan.Feld.BARRIERE) {
                    // VON DER WELTUNTERKANTE bis ueber den Kopf [berichtigt 11.11.2026].
                    // Vorher stand die Wand nur auf Bodenhoehe und vier Bloecke hoch;
                    // darunter war Luft. Wer am Feldrand einen Block tief graebt und
                    // einen Schritt zur Seite geht, stand damit unter der Wand in der
                    // Leere - und fiel. Eine Wand mit einem Loch darunter trennt nichts.
                    daten.setRegion(lx, unten, lz, lx + 1, Feldboden.wandBis(), lz + 1,
                            Feldboden.oberflaeche(feld));
                    continue;
                }
                spalte(daten, lx, lz, unten, feld);
            }
        }
    }

    /**
     * Die ganze Spalte eines Stadtfeldes, von der Weltunterkante bis zur Oberflaeche.
     *
     * <p>Vier Griffe statt siebzig: Die Schichten sind zusammenhaengend, also setzt jeder
     * Griff eine ganze Schicht. Das ist der Unterschied zwischen einem Generierlauf von
     * Minuten und einem von Stunden - diese Welt hat rund 140.000 Chunks.
     */
    private static void spalte(ChunkData daten, int lx, int lz, int unten,
                               Stadtfeldplan.Feld feld) {
        daten.setRegion(lx, unten, lz, lx + 1, unten + 1, lz + 1,
                Feldboden.darunter(feld, unten, unten));
        daten.setRegion(lx, unten + 1, lz, lx + 1, Feldboden.ERDE_AB, lz + 1,
                Feldboden.darunter(feld, unten + 1, unten));
        daten.setRegion(lx, Feldboden.ERDE_AB, lz, lx + 1, Feldboden.BODEN, lz + 1,
                Feldboden.darunter(feld, Feldboden.ERDE_AB, unten));
        daten.setBlock(lx, Feldboden.BODEN, lz, Feldboden.oberflaeche(feld));
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
        // Die Mitte des ANKUNFTSFELDES, einen Block ueber dem Boden. Es ist das einzige
        // Feld, das niemandem gehoert - jeder andere Erscheinungspunkt setzte einen
        // frisch Angekommenen in eine fremde Stadt.
        return new Location(welt, plan.ankunftsMitte() + 0.5, Feldboden.BODEN + 1,
                plan.ankunftsMitte() + 0.5);
    }

    /**
     * Gras auf den Feldern, Leere dazwischen.
     *
     * <p>Zwei Biome und nicht eines, aber <b>nicht wegen der Farbe</b> [berichtigt
     * 11.11.2026]: Hier stand, das Biom faerbe ueber der Leere den Himmel anders. Das
     * stimmt nicht - PLAINS und THE_VOID haben in der Oberwelt beinahe denselben Himmel
     * und denselben Nebel, und gefaerbt wird ohnehin nach dem Standort des Spielers und
     * nicht nach seiner Blickrichtung. Was ihn aufhaelt, ist allein die Barriere.
     *
     * <p>Getrennt wird trotzdem, und zwar aus einem Grund, der traegt: Das Biom
     * entscheidet, was WAECHST und was ERSCHEINT. Ein Feld soll eine Wiese sein, die
     * Leere nichts - und THE_VOID ist das Biom, das Minecraft selbst fuer "hier ist
     * nichts" benutzt.
     *
     * <p>Wird aus mehreren Generierungs-Threads gerufen und hat deshalb keinen
     * veraenderlichen Zustand - der Plan ist eine reine Rechnung.
     */
    private static final class FeldBiom extends BiomeProvider {

        private final Stadtfeldplan plan;

        private FeldBiom(Stadtfeldplan plan) {
            this.plan = plan;
        }

        @Override
        public Biome getBiome(WorldInfo welt, int x, int y, int z) {
            return plan.feldBei(x, z) == Stadtfeldplan.Feld.STADTFELD
                    ? Biome.PLAINS
                    : Biome.THE_VOID;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo welt) {
            // Wird genau einmal beim Laden der Welt gefragt; spaetere Aenderungen wirken
            // nicht mehr. Was hier fehlt, kann getBiome nie zurueckgeben.
            return List.of(Biome.PLAINS, Biome.THE_VOID);
        }
    }
}
