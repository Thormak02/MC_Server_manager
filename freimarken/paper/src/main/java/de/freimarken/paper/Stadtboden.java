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

    /**
     * Was unter der Oberflaeche liegt: drei Schichten Erde, darunter Stein - und auf der
     * Weltunterkante Grundgestein.
     *
     * <p>Das Grundgestein gehoert hierher und nicht nur in den Generator. Der Zurueckbau
     * stellt eine abgegrabene Spalte wieder her und laeuft dabei bis zur Unterkante; mit
     * zwei Tabellen stuende dort danach Stein, und die Stadt haette eine Stelle, an der man
     * aus der Welt fallen kann.
     *
     * @param unten die Weltunterkante, {@code welt.getMinHeight()}
     */
    static Material darunter(int y, int unten) {
        if (y <= unten) {
            return Material.BEDROCK;
        }
        return y >= ERDE_AB ? Material.DIRT : Material.STONE;
    }

    /**
     * Der Block, der an dieser Hoehe auf diesem Feld liegt - die ganze Spalte in einer
     * Tabelle.
     *
     * @param unten die Weltunterkante, {@code welt.getMinHeight()}
     */
    static Material soll(int y, Rasterplan.Feld feld, int unten) {
        if (y > BODEN + 1) {
            return Material.AIR;
        }
        if (y == BODEN + 1) {
            return darueber(feld);
        }
        if (y == BODEN) {
            return oberflaeche(feld);
        }
        return darunter(y, unten);
    }

    /**
     * Ob dieser Block das ist, was hier liegen soll - oder was daraus von selbst wird.
     *
     * <p>Gras verrottet zu Erde, sobald etwas Lichtdichtes darauf steht, und ein Schaf
     * frisst es ebenso weg. Beides ist keine Tat des Spielers. Ohne diese Ausnahme meldet
     * eine frisch zusammengelegte, voellig unberuehrte Flaeche nach einer Weile "dabei
     * verschwinden 448 Bloecke" - es genuegt ein Schaf.
     */
    static boolean istOderWird(Material block, Material soll) {
        return block == soll || (soll == Material.GRASS_BLOCK && block == Material.DIRT);
    }

    /** Was mit einer Stelle geschieht, wenn der Weg zurueckkommt. */
    enum Wertung {

        /** Hier aendert sich nichts, oder nur das, was die Stadt selbst gelegt hat. */
        UNVERAENDERT,

        /** Hier steht etwas, das danach weg ist - der Verlust, vor dem zu warnen ist. */
        VERSCHWINDET,

        /** Hier fehlt etwas, das der Weg wieder zumacht - kein Verlust. */
        WIRD_VERFUELLT
    }

    /**
     * Wie eine Stelle der Fuge zu werten ist, wenn der Weg zurueckkommt.
     *
     * <p>Die Regel, bei der zwei Dinge nicht zu verwechseln sind. Erstens: Was die Stadt
     * selbst gelegt hat, ist kein Verlust - sonst warnt das Plugin vor dem Verschwinden
     * seines eigenen Werks. Zweitens: Was schon so aussieht, wie der Zurueckbau es
     * hinschreiben wuerde, fasst er gar nicht an - es zu nennen waere eine Warnung vor
     * einer Tat, die nicht stattfindet.
     *
     * <p>Und drittens die Richtung: Eine Fehlstelle verschwindet nicht, sie wird gefuellt.
     * Beides in einer Zahl hiesse "es verschwinden 4.928 Bloecke" ueber einem leeren
     * Becken, und das Gegenteil waere wahr.
     *
     * @param ist      was dort jetzt liegt
     * @param gefuellt was dort liegt, solange die Gruppe besteht
     * @param zurueck  was der Zurueckbau dort hinschreibt
     */
    static Wertung wertung(Material ist, Material gefuellt, Material zurueck) {
        if (ist == zurueck || istOderWird(ist, gefuellt)) {
            return Wertung.UNVERAENDERT;
        }
        return istLuft(ist) ? Wertung.WIRD_VERFUELLT : Wertung.VERSCHWINDET;
    }

    /**
     * Ob hier nichts ist.
     *
     * <p>Und nicht {@code Material.isAir()}: Das geht ueber die Blockregistrierung und
     * braucht einen laufenden Server. Diese Frage muss ohne einen zu beantworten sein,
     * sonst ist die Wertung einer Stelle nur im Spiel pruefbar - und das war schon einmal
     * der Grund, warum zwei falsche Zahlen bis in die Welt durchkamen.
     */
    static boolean istLuft(Material block) {
        return block == Material.AIR || block == Material.CAVE_AIR
                || block == Material.VOID_AIR;
    }
}
