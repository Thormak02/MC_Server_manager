package de.freimarken.paper;

import de.freimarken.kern.Rasterplan;
import org.bukkit.Material;

/**
 * Welcher Block auf welchem Feld liegt - und auf welcher Hoehe.
 *
 * <p>Die eine Stelle, an der das steht. Vorher wusste es nur der Weltgenerator, und das
 * reichte, solange niemand sonst Bloecke setzte. Seit das Auftrennen einer Gruppe den Weg
 * zurueckbauen muss, gibt es einen zweiten Schreiber - und zwei Tabellen waeren das
 * Fehlerbild, das niemand sucht: Wer den Weg hier auf Pflaster umstellt, bekommt nach
 * jedem Auftrennen einen Streifen in der alten Farbe mitten in der Stadt, fuer immer.
 *
 * <p>Die Hoehe gehoert aus demselben Grund dazu. Sie stand im Generator, wurde aber von
 * vier weiteren Stellen gelesen.
 */
final class Stadtboden {

    /** Hoehe der begehbaren Oberflaeche. */
    static final int BODEN = 62;

    /** Unterste Erdschicht; darunter ist Stein. */
    static final int ERDE_AB = BODEN - 3;

    private Stadtboden() {
    }

    /** Der Block auf Hoehe {@link #BODEN}. */
    static Material oberflaeche(Rasterplan.Feld feld) {
        return switch (feld) {
            case PARZELLE -> Material.GRASS_BLOCK;
            case WEG -> Material.SMOOTH_STONE;
            case MAUER -> Material.STONE_BRICKS;
        };
    }

    /**
     * Der Block einen ueber der Oberflaeche.
     *
     * <p>Auf der Mauer eine Halbstufe statt eines vollen Blocks: sichtbare Grenze, aber man
     * kommt darueber, ohne springen zu muessen.
     */
    static Material darueber(Rasterplan.Feld feld) {
        return feld == Rasterplan.Feld.MAUER ? Material.STONE_BRICK_SLAB : Material.AIR;
    }

    /** Was unter der Oberflaeche liegt: drei Schichten Erde, darunter Stein. */
    static Material darunter(int y) {
        return y >= ERDE_AB ? Material.DIRT : Material.STONE;
    }
}
