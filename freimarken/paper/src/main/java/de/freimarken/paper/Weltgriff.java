package de.freimarken.paper;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Die Welt, soweit der {@link Wegbau} sie braucht.
 *
 * <p>Vier Methoden, und genau deshalb gibt es dieses Zwischenstueck: Die Spaltenlogik ist
 * die Stelle, an der sich dreimal hintereinander ein Fehler versteckt hat - die Warnung
 * zaehlte das eigene Gras mit, sie nannte Bloecke, die der Zurueckbau nicht anfasst, und
 * sie meldete Fehlstellen als Verlust. Gefunden wurde keiner davon durch einen Test,
 * sondern durch Nachrechnen, weil mit {@link World} nur ein laufender Server die Antwort
 * geben konnte.
 *
 * <p>Mit vier Methoden statt eines Weltobjekts laesst sich eine Stadt in einer Map
 * nachstellen, und dann sind Blockzahlen vorrechenbar und pruefbar.
 */
interface Weltgriff {

    /** Die Weltunterkante. */
    int unten();

    /** Die Hoehe des hoechsten Blocks, der nicht Luft ist. */
    int oben(int x, int z);

    /** Was an dieser Stelle liegt. */
    Material bei(int x, int y, int z);

    /** Setzt einen Block, ohne die Nachbarn zu benachrichtigen. */
    void setze(int x, int y, int z, Material block);

    /** Der Griff auf die echte Welt. */
    record AufDerWelt(World welt) implements Weltgriff {

        @Override
        public int unten() {
            return welt.getMinHeight();
        }

        @Override
        public int oben(int x, int z) {
            // WORLD_SURFACE und nicht die Vorgabe: Die zaehlt nur Bloecke, an die man
            // stoesst, und ein Schild oder eine Blume waere dann nicht vorhanden - sie
            // verschwaende aber trotzdem.
            return welt.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        }

        @Override
        public Material bei(int x, int y, int z) {
            return welt.getBlockAt(x, y, z).getType();
        }

        @Override
        public void setze(int x, int y, int z, Material block) {
            // Ohne Physik: Bei vier Feldern im Quadrat sind das ueber 2.000 Bloecke, und
            // wenn jeder davon seine Nachbarn benachrichtigt, steht der Server.
            welt.getBlockAt(x, y, z).setType(block, false);
        }
    }
}
