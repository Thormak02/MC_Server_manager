package de.freimarken.paper;

import de.freimarken.kern.Gruppenflaeche;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Rasterplan;
import java.util.List;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Der Boden einer Gruppe: den Weg fuellen und den Weg zurueckbauen.
 *
 * <p>Hier wird nichts entschieden und nichts gerechnet. Welche Koordinaten dazugehoeren,
 * sagt die {@link Gruppenflaeche} im Kern; welcher Block wohin gehoert, sagt
 * {@link Stadtboden}. Diese Klasse setzt Bloecke und zaehlt, wie viele es waren.
 *
 * <p>Die Ablage fuehrt, die Welt folgt - deshalb laeuft der Fuellauftrag immer NACH dem
 * Besitzbuch. Gefuellte Bloecke ohne Recht darauf waeren das Fehlerbild, das wie ein
 * Rechtefehler aussieht und keiner ist.
 */
final class Wegbau {

    private final Rasterplan plan;
    private final World welt;

    Wegbau(Rasterplan plan, World welt) {
        this.plan = plan;
        this.welt = welt;
    }

    /**
     * Macht die Fugen der Gruppe zu Bauland.
     *
     * <p>Angefasst werden genau die zwei Schichten, die der Generator je Feldart
     * verschieden setzt: die Oberflaeche und der Block darueber. Alles andere bleibt, wie
     * es ist - unter einem Weg liegt dieselbe Erde wie unter einer Parzelle, und was
     * jemand darueber gebaut hat, ist nicht im Weg.
     *
     * @return wie viele Bloecke sich geaendert haben
     */
    int fuelle(List<Parzelle> felder) {
        int[] geaendert = {0};
        Gruppenflaeche.aus(felder).ueberDieFugen(plan, (x, z, feld) -> {
            // Bewusst PARZELLE und nicht feld: Aus Weg und Mauer wird Bauland - das ist
            // der ganze Zweck. feld sagt, was hier OHNE die Gruppe liegen wuerde.
            geaendert[0] += setze(x, Stadtboden.BODEN, z,
                    Stadtboden.oberflaeche(Rasterplan.Feld.PARZELLE));
            geaendert[0] += setze(x, Stadtboden.BODEN + 1, z,
                    Stadtboden.darueber(Rasterplan.Feld.PARZELLE));
        });
        return geaendert[0];
    }

    /**
     * Wie viele Bloecke auf der gefuellten Wegflaeche stehen.
     *
     * <p>Das ist die Zahl fuer die Warnung vor dem Auftrennen, und sie ist absichtlich
     * nicht "wie viele Bloecke aendert der Zurueckbau": Der Weg selbst kommt ja zurueck,
     * das sind bei zwei Feldern immer dieselben 576 Bloecke. Gezaehlt wird nur, was der
     * Spieler dort hingestellt hat - direkt nach dem Zusammenlegen also null.
     *
     * <p>Gezaehlt wird ab {@link Stadtboden#BODEN}+1: Auf der Oberflaeche selbst liegt das
     * Gras, das das Fuellen hingelegt hat, und das ist nichts, was jemand verliert.
     */
    int bautenAufDemWeg(List<Parzelle> felder) {
        int[] bauten = {0};
        Gruppenflaeche.aus(felder).ueberDieFugen(plan, (x, z, feld) -> {
            // WORLD_SURFACE und nicht die Vorgabe: Die zaehlt nur Bloecke, an die man
            // stoesst, und ein Schild oder eine Blume waere dann nicht vorhanden - sie
            // verschwaende aber trotzdem.
            int oben = welt.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
            for (int y = Stadtboden.BODEN + 1; y <= oben; y++) {
                if (!welt.getBlockAt(x, y, z).getType().isAir()) {
                    bauten[0]++;
                }
            }
        });
        return bauten[0];
    }

    /**
     * Baut den Weg zurueck - und raeumt dafuer, was auf ihm steht.
     *
     * <p>Das Raeumen ist der Preis des Auftrennens, nicht ein Nebenschaden: Ohne ihn
     * stuende eine Mauer quer ueber einer oeffentlichen Strasse, die niemand mehr
     * entfernen darf. Die Warnung nennt die Zahl vorher, und bestaetigen muss der Spieler
     * mit einem zweiten Befehl.
     *
     * <p>Die Spalte wird von oben nach unten vollstaendig hergestellt, wie im Generator.
     * Nach unten laeuft die Schleife nur, solange sie Luft oder Fluessigkeit findet: Im
     * Normalfall ist das ein einziger Blick, bei einem Schacht genau so viele Bloecke, wie
     * der Schacht tief ist.
     *
     * @return wie viele Bloecke sich geaendert haben
     */
    int baueZurueck(List<Parzelle> felder) {
        int[] geaendert = {0};
        Gruppenflaeche.aus(felder).ueberDieFugen(plan, (x, z, feld) -> {
            int oben = welt.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
            for (int y = oben; y > Stadtboden.BODEN + 1; y--) {
                geaendert[0] += setze(x, y, z, Material.AIR);
            }
            geaendert[0] += setze(x, Stadtboden.BODEN + 1, z, Stadtboden.darueber(feld));
            geaendert[0] += setze(x, Stadtboden.BODEN, z, Stadtboden.oberflaeche(feld));
            for (int y = Stadtboden.BODEN - 1; y >= welt.getMinHeight(); y--) {
                Block block = welt.getBlockAt(x, y, z);
                if (!block.getType().isAir() && !block.isLiquid()) {
                    break;
                }
                geaendert[0] += setze(x, y, z, Stadtboden.darunter(y));
            }
        });
        return geaendert[0];
    }

    /**
     * Setzt einen Block, falls dort nicht schon der richtige steht.
     *
     * @return 1, wenn sich etwas geaendert hat, sonst 0
     */
    private int setze(int x, int y, int z, Material soll) {
        Block block = welt.getBlockAt(x, y, z);
        if (block.getType() == soll) {
            return 0;
        }
        // Ohne Physik: Bei vier Feldern im Quadrat sind das ueber 2.000 Bloecke, und wenn
        // jeder davon seine Nachbarn benachrichtigt, steht der Server.
        block.setType(soll, false);
        return 1;
    }
}
