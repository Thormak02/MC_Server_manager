package de.freimarken.kern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Vorschlaege zu einem angefangenen Wort - die EINE Stelle dieser Regel.
 *
 * <p><b>Zusammengelegt beim VIERTEN Leser</b> [23.11.2026]: Dieselben zehn Zeilen standen
 * in {@link Warpverzeichnis}, in {@link Berufsverzeichnis} und in {@link Stadtverzeichnis},
 * und mit den Bauten Altfurts waere es die vierte Kopie geworden. Vier Stellen, die
 * entscheiden, ob ein angefangenes Wort passt, sind vier Stellen, die auseinanderlaufen -
 * und die Vervollstaendigung ist das, was ein Spieler als "der Befehl kennt meinen Laden
 * nicht" erlebt.
 *
 * <p><b>{@link Stadtverzeichnis} bleibt bei seiner eigenen Fassung</b>, und das ist kein
 * Versehen: Es haelt seine Namen in einem SORTIERTEN Satz und bricht beim ersten Namen ab,
 * der den Anfang nicht mehr traegt - dahinter stehen nur noch groessere. Diese Fassung
 * hier laeuft ueber eine Liste in der Reihenfolge der Datei, und die kann nicht abbrechen.
 * Dort waere die gemeinsame Stelle also eine langsamere.
 *
 * <p>Ein leerer Anfang schlaegt ALLES vor: Wer die Tabulatortaste drueckt, ohne etwas
 * getippt zu haben, will sehen, was es gibt.
 */
public final class Namensvorschlag {

    private Namensvorschlag() {
    }

    /**
     * Die Namen, die mit diesem Anfang beginnen - in der Reihenfolge der Liste.
     *
     * <p>Kleingeschrieben verglichen, aber UNVERAENDERT zurueckgegeben: Was der Spieler
     * sieht, soll so aussehen wie in seiner Datei.
     */
    public static List<String> zu(List<String> namen, String angefangen) {
        if (namen == null) {
            return List.of();
        }
        String anfang = angefangen == null ? ""
                : angefangen.trim().toLowerCase(Locale.ROOT);
        List<String> treffer = new ArrayList<>();
        for (String name : namen) {
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(anfang)) {
                treffer.add(name);
            }
        }
        return List.copyOf(treffer);
    }
}
