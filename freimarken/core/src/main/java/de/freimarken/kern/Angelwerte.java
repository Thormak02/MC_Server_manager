package de.freimarken.kern;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Das eigene Angelgut von Hafenwerder - was dort an den Haken geht (Plan 4.1).
 *
 * <p><b>Die erste Beutetabelle dieses Projekts.</b> Bis zum 18.11.2026 gab es nirgends
 * eine Stelle, die bestimmt, WAS man faengt: {@code PlayerFishEvent} wurde nur gelesen,
 * um den Fang zu BEZAHLEN. Was im Wasser hing, war vollstaendig Vanilla.
 *
 * <p><b>Die Tabelle ERSETZT den Fang nur manchmal</b>, und das ist die Entscheidung
 * dieses Teils: {@link #haeufigkeitProzent} sagt, wie oft. Alles andere bleibt Vanilla -
 * ein Angler soll weiter Fische, Schrott und Schaetze bekommen, und darauf stehen
 * sowohl die Lohntafel als auch die Erwartung jedes Spielers, der Minecraft kennt. Eine
 * Tabelle, die IMMER greift, waere keine Besonderheit, sondern eine andere Welt.
 *
 * <p>Was in der Tabelle steht, ist eine Entscheidung des Betreibers und keine des
 * Programms: Die ausgelieferten Zeilen sind Dinge, die es auf einer Insel sonst NICHT
 * gibt - Hafenwerder hat keine Hoehlen und keine Bauwerke, also kein Erz, kein
 * Prismarin, keinen Schwamm. Wer die Tabelle leer laesst, hat wieder Vanilla.
 *
 * @param an                 ob die Tabelle ueberhaupt greift
 * @param haeufigkeitProzent wie oft sie den Fang ersetzt, 0 bis 100
 * @param gewichte           Gegenstandsname auf Gewicht; je hoeher, je oefter
 */
public record Angelwerte(boolean an, int haeufigkeitProzent,
                         Map<String, Integer> gewichte) {

    /** Der Schluessel des Schalters in der Einstellungsdatei. */
    public static final String PFAD_AN = "angeln.an";

    /** Der Schluessel der Haeufigkeit. */
    public static final String PFAD_HAEUFIGKEIT = "angeln.haeufigkeit-prozent";

    /** Der Schluessel der Tabelle selbst. */
    public static final String PFAD_FAENGE = "angeln.faenge";

    /** Das hoechste Gewicht einer Zeile - gegen den Vertipper, nicht gegen den Willen. */
    public static final int HOECHSTES_GEWICHT = 1_000_000;

    /**
     * Wie oft die Tabelle ab Werk greift - jeder fuenfte Fang.
     *
     * <p>Zwanzig, und die Zahl hat zwei Seiten: Weniger, und ein Angler merkt es nicht;
     * mehr, und die Fische aus der Lohntafel werden selten - dann verdient der Fischer
     * in Hafenwerder weniger als ueberall sonst, und genau das ist die Umkehrung von
     * Plan 9.1.
     */
    public static final int VORGABE_HAEUFIGKEIT = 20;

    /**
     * Die ausgelieferte Tabelle - alles, was es auf einer Insel sonst nicht gibt.
     *
     * <p>Hafenwerder hat keine Hoehlen und keine Bauwerke ({@code shouldGenerateCaves}
     * und {@code shouldGenerateStructures} stehen auf false): kein Erz, kein Prismarin,
     * kein Schwamm, keine Korallen ausser denen, die der Bewuchs setzt. Was hier steht,
     * ist darum kein Zusatzverdienst, sondern der einzige Weg zu diesen Dingen - und ein
     * Grund, nach Hafenwerder zu fahren.
     *
     * <p>Das MEERESHERZ steht mit Gewicht 1 darin, und das ist der Reiz der ganzen
     * Tabelle: Mit ihm und acht Schneckenhaeusern wird ein Aquisator gebaut. Das ist ein
     * Inselziel fuer Wochen, und es gibt es in Vanilla durch Angeln nirgends.
     */
    public static final Map<String, Integer> VORGABE_FAENGE = Map.ofEntries(
            Map.entry("tropical_fish", 30),
            Map.entry("nautilus_shell", 12),
            Map.entry("prismarine_shard", 10),
            Map.entry("sea_pickle", 8),
            Map.entry("prismarine_crystals", 6),
            Map.entry("brain_coral_block", 6),
            Map.entry("horn_coral_block", 6),
            Map.entry("sponge", 4),
            Map.entry("heart_of_the_sea", 1));

    /** Die Werte, die das Programm mitbringt. */
    public static final Angelwerte VORGABE =
            new Angelwerte(true, VORGABE_HAEUFIGKEIT, VORGABE_FAENGE);

    public Angelwerte {
        if (haeufigkeitProzent < 0 || haeufigkeitProzent > 100) {
            throw new IllegalArgumentException("Die Haeufigkeit ist "
                    + haeufigkeitProzent + " und liegt nicht zwischen 0 und 100 Prozent.");
        }
        if (gewichte == null) {
            throw new IllegalArgumentException("Ohne Tabelle gibt es kein Angelgut -"
                    + " eine LEERE Tabelle ist erlaubt und heisst: alles bleibt Vanilla");
        }
        Map<String, Integer> kopie = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> zeile : gewichte.entrySet()) {
            if (zeile.getKey() == null || zeile.getKey().isBlank()) {
                throw new IllegalArgumentException("Ein Fang ohne Namen steht in der"
                        + " Tabelle - der Name ist der Gegenstand.");
            }
            if (zeile.getValue() == null || zeile.getValue() < 1
                    || zeile.getValue() > HOECHSTES_GEWICHT) {
                throw new IllegalArgumentException("Das Gewicht von " + zeile.getKey()
                        + " ist " + zeile.getValue() + " und liegt nicht zwischen 1 und "
                        + HOECHSTES_GEWICHT + ". Ein Gewicht von 0 ist eine Zeile, die"
                        + " nie greift - die gehoert aus der Tabelle heraus.");
            }
            kopie.put(zeile.getKey(), zeile.getValue());
        }
        // Die eigene Abbildung und nicht die hereingegebene: Eine veraenderliche Tabelle
        // in einem unveraenderlichen Satz waere eine Hintertuer in den Stand, der bei
        // jedem Fang gelesen wird - und zwar aus dem Hauptfaden.
        gewichte = Map.copyOf(kopie);
    }

    /** Die Summe aller Gewichte - der Nenner jeder Wahl. */
    public long gewichtsumme() {
        long summe = 0L;
        for (int gewicht : gewichte.values()) {
            summe += gewicht;
        }
        return summe;
    }

    /** Ob diese Tabelle ueberhaupt etwas tut. */
    public boolean greift() {
        return an && haeufigkeitProzent > 0 && !gewichte.isEmpty();
    }

    /** Dieselben Werte mit einem anderen Schalter. */
    public Angelwerte mitAn(boolean anders) {
        return new Angelwerte(anders, haeufigkeitProzent, gewichte);
    }

    /** Dieselben Werte mit einer anderen Haeufigkeit. */
    public Angelwerte mitHaeufigkeit(int andere) {
        return new Angelwerte(an, andere, gewichte);
    }

    /** Dieselben Werte mit einer anderen Tabelle. */
    public Angelwerte mitFaengen(Map<String, Integer> andere) {
        return new Angelwerte(an, haeufigkeitProzent, andere);
    }
}
