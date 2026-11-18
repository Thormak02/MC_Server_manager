package de.freimarken.kern;

import java.util.List;
import java.util.Locale;

/**
 * Welche Biome eine Wolkeninsel tragen kann - die Tafel hinter {@code /wolke biom}.
 *
 * <p><b>Warum die Namen im Programm stehen und nicht in der config.yml</b>: Ein Biom ist
 * nicht nur eine Farbe. Es entscheidet, WAS erscheint und WAS waechst - und damit, was auf
 * einer Skyblockinsel ueberhaupt zu bekommen ist. Jeder Name dieser Tafel ist darum eine
 * Entscheidung und kein Eintrag: Ein Sumpf gibt Schleim, ein Pilzland gibt KEINE Monster,
 * eine Wueste gibt keinen Regen. Einstellbar ist, welche davon der Betreiber ANBIETET
 * ({@code wolken.biome}) - erfinden kann er keines, denn zu einem Namen, den Minecraft
 * nicht kennt, gibt es kein Biom.
 *
 * <p><b>Die Namen sind Zeichenketten, und das ist der ganze Zweck dieser Klasse.</b>
 * Minecrafts Biome liegen in einem Verzeichnis, das ohne laufenden Server nicht zu lesen
 * ist - wie {@code new ItemStack}, das dort wirft. Hier steht darum nur, wie die Biome
 * HEISSEN; welches Biom daraus wird, weiss {@code Biomtafel} im Plugin. So ist jede Frage
 * nach einem Namen ohne Server beantwortbar, und die Liste steht nur einmal da.
 */
public final class Wolkenbiome {

    /** Das Biom, das eine Insel ohne Wahl traegt - und der Grundton der ganzen Welt. */
    public static final String VORGABE = "plains";

    /**
     * Jeder Name, den {@code /wolke biom} annehmen kann - in dieser Reihenfolge.
     *
     * <p>Zehn, und jedes traegt seinen eigenen Grund:
     *
     * <ul>
     *   <li><b>plains</b> - der Anfang. Gras waechst, es regnet, Bienen kommen zur Bluete.
     *   <li><b>forest</b> - dasselbe mit Wolf und Baer; der ruhige Wald.
     *   <li><b>desert</b> - kein Regen, dafuer Husks. Ein Kessel fuellt sich hier nie, und
     *       genau das macht es zur Aufgabe.
     *   <li><b>jungle</b> - Ozelot, Papagei, Panda; das lebendigste Biom des Spiels.
     *   <li><b>savanna</b> - trocken, Pferde, Akazienfarbe.
     *   <li><b>taiga</b> - Fuchs und Wolf, Suesse Beeren.
     *   <li><b>snowy_plains</b> - Schnee und Eis von selbst. Eis ist Wasser, und Wasser
     *       ist in einer leeren Welt der halbe Fortschritt.
     *   <li><b>swamp</b> - SCHLEIM. Ohne Sumpf gibt es auf einer Insel keinen Schleimball
     *       und damit keinen Kolben und kein Lasso.
     *   <li><b>mushroom_fields</b> - hier erscheint KEIN Monster. Die ruhigste Insel des
     *       Spiels, und darum eine Belohnung und kein Anfang.
     *   <li><b>cherry_grove</b> - rosa Blaetter; das Biom fuer die Augen.
     * </ul>
     */
    public static final List<String> NAMEN = List.of("plains", "forest", "desert",
            "jungle", "savanna", "taiga", "snowy_plains", "swamp", "mushroom_fields",
            "cherry_grove");

    private Wolkenbiome() {
    }

    /**
     * Wie ein Biomname abgelegt wird: beschnitten und klein.
     *
     * <p>Dieselbe Antwort wie bei jedem Namen in diesem Projekt und an EINER Stelle - zwei
     * Meinungen darueber, ob "Swamp" ein Sumpf ist, sind der Fehler, bei dem eine Zeile in
     * der einen Tabelle gilt und in der anderen nicht.
     */
    public static String wieAbgelegt(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** Ob dieser Name in der Tafel steht. */
    public static boolean gibtEs(String name) {
        return NAMEN.contains(wieAbgelegt(name));
    }
}
