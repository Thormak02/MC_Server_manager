package de.freimarken.paper;

import de.freimarken.kern.Inselplan;
import org.bukkit.Material;

/**
 * Welcher Block auf welcher Hoehe von Hafenwerder liegt.
 *
 * <p>Das Geschwister von {@link Feldboden} und {@link Stadtboden}, und aus demselben Grund
 * eine eigene Stelle: Heute liest nur der Weltgenerator diese Tabelle, aber sobald eine
 * aufgegebene Insel zurueckgesetzt wird, gibt es einen zweiten Schreiber. Zwei Tabellen
 * waeren dann das Fehlerbild, das niemand sucht.
 *
 * <p><b>Der Boden liegt hier einen Block HOEHER als in den anderen Welten</b> - 63 statt
 * 62 -, und das ist die einzige Zahl, die aus der Reihe faellt. Der Grund ist das Meer:
 * Die Wasserlinie liegt auf 62, wie in jeder gewoehnlichen Minecraftwelt, und eine Insel,
 * deren Gras auf derselben Hoehe liegt, ist keine Insel, sondern eine Untiefe. Wer die
 * Zahl aendert, verschiebt die ganze Welt mit - Wasserlinie, Hang und Meeresgrund haengen
 * alle an ihr.
 *
 * <p>Die ganze Spalte, von oben gelesen:
 *
 * <pre>
 *       64 ..    LUFT
 *          63    BODEN         Gras (Inselinneres) oder Sand (Strand)
 *    56 .. 62    WASSER        bis zur Wasserlinie - oder Hang, wo die Insel steht
 *          55    MEERESGRUND   Sand, acht Bloecke unter der Wasserlinie
 *    -63 .. 54   Erde und Stein
 *         -64    Grundgestein
 * </pre>
 */
final class Inselboden {

    /** Die Hoehe der Inseloberflaeche - ein Block ueber der Wasserlinie. */
    static final int BODEN = 63;

    /** Der oberste Wasserblock. Vanilles Mass, damit das Meer wie ein Meer aussieht. */
    static final int MEERESSPIEGEL = BODEN - 1;

    /** Wie viele Schichten Erde unter jedem Oberflaechenblock liegen; darunter Stein. */
    static final int ERDE_TIEF = 3;

    private Inselboden() {
    }

    /**
     * Die Hoehe des Meeresgrundes - abgeleitet und keine eigene Zahl.
     *
     * <p>{@link Inselplan#meerTiefe} folgt aus Feldkante und Kuestenradius: Der Hang der
     * Insel faellt um einen Block, sooft er einen Block nach aussen geht, und wo er den
     * Rand des eigenen Feldes erreicht, ist der Grund. Eine zweite Zahl daneben waere
     * entweder eine senkrechte Riffwand oder ein Fuss, der ins fremde Wasser laeuft.
     */
    static int meeresgrund(Inselplan plan) {
        return BODEN - plan.meerTiefe();
    }

    /**
     * Der oberste Block einer Spalte.
     *
     * <p>Gras nur im Inselinneren und nur UEBER Wasser. Alles andere ist Sand: der
     * Strand, der Hang darunter und der Meeresgrund - drei Lagen, die ineinander
     * uebergehen, und ein Sandstrand, der unter Wasser ploetzlich Stein waere, saehe aus
     * wie eine Baugrube.
     *
     * @param gestalt was {@link Inselplan#gestaltBei} an dieser Stelle sagt
     * @param tiefe   die Tiefe des Gipfels, {@link Inselplan#tiefeDerOberflaeche}
     */
    static Material oberflaeche(Inselplan.Gestalt gestalt, int tiefe) {
        if (tiefe > 0) {
            return Material.SAND;
        }
        return gestalt == Inselplan.Gestalt.LAND ? Material.GRASS_BLOCK : Material.SAND;
    }

    /**
     * Was unter dem obersten Block liegt.
     *
     * <p>Drei Schichten Erde, darunter Stein, auf der Weltunterkante Grundgestein -
     * dieselbe Spalte wie in der Marktstadt und auf einem Stadtfeld. Dass die Erde unter
     * Wasser weitergeht, ist richtig und nicht nachlaessig: Ein Meeresgrund aus Erde ist
     * das, worauf Seegras waechst.
     *
     * @param y      die Hoehe, nach der gefragt wird
     * @param gipfel der oberste Block dieser Spalte
     * @param unten  die Weltunterkante, {@code welt.getMinHeight()}
     */
    static Material darunter(int y, int gipfel, int unten) {
        if (y <= unten) {
            return Material.BEDROCK;
        }
        return y >= gipfel - ERDE_TIEF ? Material.DIRT : Material.STONE;
    }
}
