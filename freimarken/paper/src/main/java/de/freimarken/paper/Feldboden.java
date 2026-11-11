package de.freimarken.paper;

import de.freimarken.kern.Stadtfeldplan;
import org.bukkit.Material;

/**
 * Welcher Block auf welchem Feld der Freistaedte liegt - und auf welcher Hoehe.
 *
 * <p>Das Geschwister von {@link Stadtboden}, und aus demselben Grund eine eigene Stelle:
 * Heute liest nur der Weltgenerator diese Tabelle, aber sobald ein Grundstueck
 * zurueckgesetzt oder eine verfallene Stadt geraeumt wird, gibt es einen zweiten
 * Schreiber. Zwei Tabellen waeren dann das Fehlerbild, das niemand sucht.
 *
 * <p><b>Eine eigene Tabelle und keine Erweiterung von {@link Stadtboden}:</b> Jene
 * beantwortet Fragen ueber {@code Rasterplan.Feld} (Parzelle, Weg, Mauer), diese ueber
 * {@link Stadtfeldplan.Feld} (Stadtfeld, Barriere, Leere). Das sind zwei Welten mit zwei
 * Geometrien; eine gemeinsame Tabelle muesste beide Aufzaehlungen kennen und haette fuer
 * jede Haelfte Faelle, die nie vorkommen.
 *
 * <p>Die Hoehe ist DIESELBE Zahl wie in der Marktstadt, und das ist Absicht: Zwei gebaute
 * Welten, in denen der Boden verschieden hoch liegt, fuehlen sich verschieden an, ohne
 * dass es dafuer einen Grund gaebe. Geteilt wird die Zahl trotzdem nicht - sie gehoert
 * jeder Welt selbst, und eine davon darf sich eines Tages aendern.
 */
final class Feldboden {

    /** Hoehe der begehbaren Oberflaeche - wie in der Marktstadt. */
    static final int BODEN = 62;

    /** Unterste Erdschicht; darunter ist Stein. */
    static final int ERDE_AB = BODEN - 3;

    /**
     * Wie hoch die unsichtbare Wand UEBER DER LAUFFLAECHE steht.
     *
     * <p>Vier Bloecke, und mehr waere Vorsicht ohne Gewinn: Gegen Laufen genuegt einer,
     * gegen Springen zwei, gegen einen Sprung mit Trank und Schwung vier. Wer darueber
     * hinaus baut, um in die Leere zu kommen, will in die Leere - und dort faellt er.
     *
     * <p><b>Ueber der Laufflaeche, nicht ueber dem Bodenblock</b> [berichtigt 11.11.2026]:
     * Wer auf dem Gras bei {@link #BODEN} steht, hat die Fuesse einen Block darueber. Die
     * Wand von {@code BODEN} bis {@code BODEN + 4} liess ihm nur drei - gezaehlt wurde
     * vom falschen Block aus. Die Lesesuche hat es gefunden, keine Probe.
     *
     * <p>Bis zur Bauhoehe reicht sie nicht: Das waeren je Stadtfeld rund 400.000
     * Bloecke, und eine Stadt, aus der man nicht einmal fliegen kann, ist ein Kaefig.
     */
    static final int BARRIERE_HOCH = 4;

    /** Die oberste Hoehe der Wand - ausschliessend, wie {@code setRegion} es nimmt. */
    static int wandBis() {
        return BODEN + 1 + BARRIERE_HOCH;
    }

    private Feldboden() {
    }

    /** Der Block auf Hoehe {@link #BODEN}. */
    static Material oberflaeche(Stadtfeldplan.Feld feld) {
        return switch (feld) {
            case STADTFELD -> Material.GRASS_BLOCK;
            case BARRIERE -> Material.BARRIER;
            case LEERE -> Material.AIR;
        };
    }

    /**
     * Was unter der Oberflaeche liegt - und ueber der Leere liegt nichts.
     *
     * <p>Unter einem Stadtfeld: drei Schichten Erde, darunter Stein, auf der
     * Weltunterkante Grundgestein - dieselbe Spalte wie in der Marktstadt.
     *
     * <p><b>Unter der Barriere steht die Barriere weiter</b> [berichtigt 11.11.2026].
     * Vorher war dort Luft, mit der Begruendung, eine Saeule aus Stein waere ein Steg um
     * jede Stadt. Der Stein waere einer - die Barriere ist keiner: Auf ihr steht niemand,
     * sie ist einen Block breit und oben zu. Die Luft dagegen war ein LOCH: Wer am
     * Feldrand einen Block tief graebt und einen Schritt zur Seite geht, steht unter der
     * Wand in der Leere und faellt. Gefunden von der Lesesuche, nicht von einer Probe.
     *
     * @param unten die Weltunterkante, {@code welt.getMinHeight()}
     */
    static Material darunter(Stadtfeldplan.Feld feld, int y, int unten) {
        if (feld == Stadtfeldplan.Feld.BARRIERE) {
            return Material.BARRIER;
        }
        if (feld != Stadtfeldplan.Feld.STADTFELD) {
            return Material.AIR;
        }
        if (y <= unten) {
            return Material.BEDROCK;
        }
        return y >= ERDE_AB ? Material.DIRT : Material.STONE;
    }
}
