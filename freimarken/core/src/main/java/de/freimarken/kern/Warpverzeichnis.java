package de.freimarken.kern;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Welche Namen eine Reise meint.
 *
 * <p>Plan 4.3 entscheidet: Oeffentliche Warps werden <b>abgeleitet</b>, nicht gepflegt. Sie
 * kommen aus der Weltenliste der {@code config.yml} und nicht aus einer zweiten Liste -
 * sonst ist eine neue Welt nicht mehr „ein Eintrag plus Generator", sondern ein Eintrag
 * plus ein vergessener Warp.
 *
 * <p>Genau diese Ableitung ist reine Zeichenkettenarbeit, und deshalb steht sie hier und
 * nicht im Befehl: Was das Plugin anbietet und in welcher Reihenfolge, ist damit belegt und
 * nicht eine Eigenschaft von Brigadier.
 *
 * <p>Die Reihenfolge der Datei bleibt die Reihenfolge der Vorschlaege. Die Marktstadt steht
 * oben, und wer {@code /warp m} tippt, meint fast immer sie.
 */
public final class Warpverzeichnis {

    private final List<String> welten;

    private Warpverzeichnis(List<String> welten) {
        this.welten = welten;
    }

    /**
     * Das Verzeichnis zu einer Liste von Weltnamen.
     *
     * <p>Doppelte Namen fallen weg, die Reihenfolge bleibt. Leere Namen werden
     * uebersprungen statt zu werfen: Das Lesen der Weltenliste hat sie schon beklagt, und
     * ein Verzeichnis, das deswegen den Serverstart abbricht, waere die zweite Strafe fuer
     * denselben Tippfehler.
     */
    public static Warpverzeichnis ausWelten(List<String> weltnamen) {
        LinkedHashSet<String> sauber = new LinkedHashSet<>();
        for (String name : weltnamen) {
            if (name == null) {
                continue;
            }
            String klein = name.trim().toLowerCase(Locale.ROOT);
            if (!klein.isEmpty()) {
                sauber.add(klein);
            }
        }
        return new Warpverzeichnis(List.copyOf(sauber));
    }

    /** Die Welten in der Reihenfolge der Datei. */
    public List<String> welten() {
        return welten;
    }

    /**
     * Welche Welt diese Eingabe meint - Gross- und Kleinschreibung egal.
     *
     * <p>Egal, weil ein Spieler {@code /warp Kornmark} tippt, wenn er das Wort aus einem
     * Schild abliest. Eine Absage waere dort richtig und trotzdem aergerlich.
     */
    public Optional<String> aufgeloest(String eingabe) {
        if (eingabe == null) {
            return Optional.empty();
        }
        String klein = eingabe.trim().toLowerCase(Locale.ROOT);
        return welten.contains(klein) ? Optional.of(klein) : Optional.empty();
    }

    /**
     * Ob dieser Name einer Welt gehoert.
     *
     * <p>Heute dasselbe wie {@link #aufgeloest}, und mit Absicht eine eigene Frage: Sobald
     * es spielereigene Warps gibt (Rangleiter, Plan 11.1), ist das die Stelle, an der ein
     * eigener Warp namens {@code kornmark} abgelehnt wird. Dann aendert sich der Aufrufer,
     * nicht die Regel.
     */
    public boolean reserviert(String name) {
        return aufgeloest(name).isPresent();
    }

    /**
     * Die Vorschlaege zu einem angefangenen Wort.
     *
     * <p>Kleingeschrieben verglichen, in der Reihenfolge der Datei zurueckgegeben. Ein
     * leerer Anfang schlaegt alles vor.
     */
    public List<String> vorschlaege(String angefangen) {
        String klein = angefangen == null ? "" : angefangen.trim().toLowerCase(Locale.ROOT);
        List<String> treffer = new ArrayList<>();
        for (String welt : welten) {
            if (welt.startsWith(klein)) {
                treffer.add(welt);
            }
        }
        return treffer;
    }
}
