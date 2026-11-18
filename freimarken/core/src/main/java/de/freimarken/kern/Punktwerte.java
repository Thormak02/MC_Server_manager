package de.freimarken.kern;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Was eine Wolkeninsel WERT ist - die Punkte je Block (Plan 4.1, Schritt 11 Teil E).
 *
 * <p><b>Die Punktzahl ist die Rangliste, und sie ist NICHT die Stufe.</b> Die Stufe kommt
 * aus den Herausforderungen ({@link Aufgabenwerte}) und entscheidet, was der
 * Kobbelsteingenerator ausspuckt; die Punktzahl kommt aus den BLOECKEN und entscheidet
 * nichts - sie steht in der Liste. Das ist Absicht: Eine Rangliste, die die Ausbeute
 * aendert, waere ein Grund, Bloecke zu stapeln, statt zu spielen.
 *
 * <p><b>Gezaehlt wird, was in der Tabelle steht - und nur das.</b> Eine Insel aus einer
 * Million Kobbelsteinen ist keine Leistung: Der Generator gibt sie umsonst. Darum stehen
 * in der ausgelieferten Tabelle die Bloecke, die ARBEIT kosten - Erze, Metalle, Diamant,
 * und die Pflanzen, die es nur aus der Starttruhe gibt.
 *
 * <p><b>Die Zaehlung laeuft in HAPPEN</b>, und {@link #chunksJeTakt} ist ihre Groesse: Ein
 * Wolkenfeld ist 128 Bloecke breit und reicht ueber die ganze Welthoehe - das sind 64
 * Chunks mit zusammen mehr als sechs Millionen Bloecken. In einem Zug gezaehlt stuende der
 * Server eine Sekunde oder laenger; ein Chunk je Takt ist unmerklich.
 *
 * @param tabelle       je Blockname seine Punkte; leer heisst: keine Punkte fuer nichts
 * @param chunksJeTakt  wie viele Chunks ein Takt zaehlt
 * @param abklingSekunden wie lange ein Spieler nach einer Zaehlung warten muss
 */
public record Punktwerte(Map<String, Long> tabelle, int chunksJeTakt,
                         long abklingSekunden) {

    /** Der Schluessel der Tabelle in der Einstellungsdatei. */
    public static final String PFAD_TABELLE = "wolken.punkte.tabelle";

    /** Der Schluessel der Happengroesse. */
    public static final String PFAD_CHUNKS_JE_TAKT = "wolken.punkte.chunks-je-takt";

    /** Der Schluessel der Abklingzeit. */
    public static final String PFAD_ABKLINGZEIT = "wolken.punkte.abklingzeit-sekunden";

    /** Die hoechste Punktzahl, die ein einzelner Block tragen darf. */
    public static final long HOECHSTE_PUNKTE = 1_000_000L;

    /**
     * So viele Chunks darf ein Takt hoechstens zaehlen.
     *
     * <p>64 ist das ganze Feld in EINEM Takt - erlaubt, aber nur fuer eine Messung:
     * Dabei steht der Server, solange gezaehlt wird.
     */
    public static final int HOECHSTENS_JE_TAKT = 64;

    /** Ab Werk ein Chunk je Takt - 64 Takte fuer ein Feld, also etwa drei Sekunden. */
    public static final int VORGABE_CHUNKS_JE_TAKT = 1;

    /**
     * Ab Werk zwei Minuten Abklingzeit.
     *
     * <p>Nicht gegen Missbrauch, sondern gegen Last: Jede Zaehlung liest sechs Millionen
     * Bloecke. Zehn Spieler, die im Wechsel zaehlen, haetten den Server dauerhaft mit
     * einer Arbeit beschaeftigt, deren Ergebnis sich kaum aendert.
     */
    public static final long VORGABE_ABKLINGZEIT = 120L;

    /**
     * Die ausgelieferte Punktetabelle - was ARBEIT kostet.
     *
     * <p>Die Zahlen sind eine Rangfolge und keine Rechnung: Ein Diamantblock ist neun
     * Diamanten, also neunmal so viel; Netherit steht darueber, weil es in einer Welt
     * ohne Nether nur aus der letzten Erzstufe kommt. Kobbelstein und Stein stehen NICHT
     * darin - sie kommen aus dem Generator und kosten nur Zeit.
     */
    public static final Map<String, Long> VORGABE_TABELLE = Map.ofEntries(
            Map.entry("coal_block", 9L),
            Map.entry("copper_block", 9L),
            Map.entry("iron_block", 45L),
            Map.entry("gold_block", 90L),
            Map.entry("redstone_block", 45L),
            Map.entry("lapis_block", 72L),
            Map.entry("emerald_block", 450L),
            Map.entry("diamond_block", 900L),
            Map.entry("netherite_block", 3_600L),
            Map.entry("beacon", 2_000L),
            Map.entry("enchanting_table", 500L),
            Map.entry("anvil", 150L),
            Map.entry("hay_block", 9L),
            Map.entry("bookshelf", 30L),
            Map.entry("glass", 2L),
            Map.entry("bricks", 4L),
            Map.entry("oak_log", 2L),
            Map.entry("melon", 5L),
            Map.entry("pumpkin", 5L),
            Map.entry("cactus", 5L),
            Map.entry("sugar_cane", 3L),
            Map.entry("brown_mushroom_block", 4L),
            Map.entry("red_mushroom_block", 4L));

    /** Die Werte, die das Programm mitbringt. */
    public static final Punktwerte VORGABE = new Punktwerte(VORGABE_TABELLE,
            VORGABE_CHUNKS_JE_TAKT, VORGABE_ABKLINGZEIT);

    public Punktwerte {
        if (tabelle == null) {
            throw new IllegalArgumentException("Ohne Tabelle gibt es keine Punktwerte."
                    + " Eine LEERE Tabelle ist erlaubt und heisst: jede Insel hat 0"
                    + " Punkte.");
        }
        if (chunksJeTakt < 1 || chunksJeTakt > HOECHSTENS_JE_TAKT) {
            throw new IllegalArgumentException("Die Happengroesse ist " + chunksJeTakt
                    + " und liegt nicht zwischen 1 und " + HOECHSTENS_JE_TAKT
                    + ". Eine 0 waere eine Zaehlung, die nie weiterkommt.");
        }
        if (abklingSekunden < 0L) {
            throw new IllegalArgumentException("Eine negative Abklingzeit gibt es nicht,"
                    + " nicht " + abklingSekunden);
        }
        Map<String, Long> abgelegt = new LinkedHashMap<>();
        for (var zeile : tabelle.entrySet()) {
            if (zeile.getKey() == null || zeile.getKey().isBlank()) {
                throw new IllegalArgumentException("Ein Block ohne Namen in der"
                        + " Punktetabelle");
            }
            if (zeile.getValue() == null || zeile.getValue() < 1L
                    || zeile.getValue() > HOECHSTE_PUNKTE) {
                throw new IllegalArgumentException("Die Punkte von " + zeile.getKey()
                        + " sind " + zeile.getValue() + " und liegen nicht zwischen 1 und "
                        + HOECHSTE_PUNKTE + ". Wer einen Block nicht zaehlen will, nimmt"
                        + " ihn heraus - eine 0 waere eine Zeile, die nie wirkt.");
            }
            abgelegt.put(zeile.getKey().trim().toLowerCase(Locale.ROOT),
                    zeile.getValue());
        }
        tabelle = Map.copyOf(abgelegt);
    }

    /** Ob ueberhaupt gezaehlt wird. */
    public boolean greift() {
        return !tabelle.isEmpty();
    }

    /** Die Punkte dieses Blocks - 0, wenn er nicht in der Tabelle steht. */
    public long punkteFuer(String block) {
        if (block == null) {
            return 0L;
        }
        return tabelle.getOrDefault(block.trim().toLowerCase(Locale.ROOT), 0L);
    }

    /** Dieselben Werte mit einer anderen Tabelle. */
    public Punktwerte mitTabelle(Map<String, Long> andere) {
        return new Punktwerte(andere, chunksJeTakt, abklingSekunden);
    }

    /** Dieselben Werte mit einer anderen Happengroesse. */
    public Punktwerte mitChunksJeTakt(int andere) {
        return new Punktwerte(tabelle, andere, abklingSekunden);
    }

    /** Dieselben Werte mit einer anderen Abklingzeit. */
    public Punktwerte mitAbklingzeit(long andere) {
        return new Punktwerte(tabelle, chunksJeTakt, andere);
    }
}
