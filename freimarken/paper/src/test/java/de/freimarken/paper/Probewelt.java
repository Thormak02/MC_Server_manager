package de.freimarken.paper;

import de.freimarken.kern.Rasterplan;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;

/**
 * Die Marktstadt in einer Map - der Weltgriff fuer die Proben.
 *
 * <p>Was nie gesetzt wurde, liefert dieselbe Tabelle, aus der auch der Weltgenerator
 * schreibt. Die Stadt ist damit genau so, wie sie nach dem Erzeugen daliegt, und jede
 * Abweichung ist etwas, das der Test oder der {@link Wegbau} hingelegt hat.
 */
final class Probewelt implements Weltgriff {

    /** Die Bauhoehe der Marktstadt - so hoch sucht {@link #oben} nach einem Block. */
    private static final int HIMMEL = 320;

    /** Eine Stelle in der Welt. */
    record Ort(int x, int y, int z) { }

    private final Rasterplan plan;
    private final int unten;
    private final Map<Ort, Material> gesetzt = new HashMap<>();

    Probewelt(Rasterplan plan, int unten) {
        this.plan = plan;
        this.unten = unten;
    }

    /** Was der Generator an dieser Stelle hinlegt. */
    private Material generiert(int x, int y, int z) {
        return Stadtboden.soll(y, plan.feldBei(x, z), unten);
    }

    @Override
    public int unten() {
        return unten;
    }

    @Override
    public int oben(int x, int z) {
        for (int y = HIMMEL; y > unten; y--) {
            if (!Stadtboden.istLuft(bei(x, y, z))) {
                return y;
            }
        }
        return unten;
    }

    @Override
    public Material bei(int x, int y, int z) {
        Ort ort = new Ort(x, y, z);
        Material abweichend = gesetzt.get(ort);
        return abweichend != null ? abweichend : generiert(x, y, z);
    }

    @Override
    public void setze(int x, int y, int z, Material block) {
        gesetzt.put(new Ort(x, y, z), block);
    }

    /**
     * Alle Stellen, die von der erzeugten Stadt abweichen.
     *
     * <p>Das Mass fuer die Rundreise: Was gefuellt und wieder zurueckgebaut wurde, muss
     * hier danach leer sein. Eine Stelle zu viel oder zu wenig faellt sofort auf, und zwar
     * mit Koordinate - anders als bei einer blossen Blockzahl, die sich ausgleichen kann.
     */
    Map<Ort, Material> abweichungen() {
        Map<Ort, Material> anders = new HashMap<>();
        gesetzt.forEach((ort, block) -> {
            if (block != generiert(ort.x(), ort.y(), ort.z())) {
                anders.put(ort, block);
            }
        });
        return anders;
    }
}
