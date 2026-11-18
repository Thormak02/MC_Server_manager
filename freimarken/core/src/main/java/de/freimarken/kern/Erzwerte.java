package de.freimarken.kern;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Was der Kobbelsteingenerator von Wolkenwerder ausspuckt (Plan 4.1, Schritt 11).
 *
 * <p><b>Der Reiz dieser Welt steht in dieser Tabelle.</b> In einer Welt aus Leere gibt es
 * kein Erz: keine Hoehle, kein Gestein, keine Ader. Was ein Spieler an Metall bekommt,
 * bekommt er aus Lava und Wasser - und darum ist der Generator hier nicht eine
 * Bequemlichkeit, sondern die Werkbank des ganzen Spiels.
 *
 * <p><b>Zwei Dinge steigen mit der Insel-Stufe</b>, und beide sind getrennt einstellbar:
 *
 * <ul>
 *   <li>WIE OFT ueberhaupt ein Erz kommt - {@link #prozentFuer}, ein Grundwert plus
 *       etwas je Stufe, gedeckelt. Dieselbe Form wie beim Tagesbonus
 *       ({@link Bonuswerte}), und aus demselben Grund: Ein Fortschritt, der nur aus
 *       einem Deckel besteht, ist keiner, und einer ohne Deckel ist irgendwann alles.
 *   <li>WELCHE Erze ueberhaupt in Frage kommen - jede Zeile traegt ihre
 *       {@link Erzzeile#abStufe}. Diamant ab Stufe fuenf heisst: Die ersten Stunden
 *       geben Kohle und Kupfer, und das ist der Unterschied zwischen einem Fortschritt
 *       und einem Geschenk.
 * </ul>
 *
 * <p><b>Was NICHT einstellbar ist: aus welchen Bloecken getauscht wird</b>
 * ({@link #AUSGANG}). Lava und Wasser bilden Kobbelstein und Stein, und sonst nichts -
 * aber dasselbe Bukkit-Ereignis bildet auch Schnee, Eis und Beton. Ein Diamant aus Schnee
 * waere ein Fehler, den niemand als Einstellung gemeint haben kann.
 *
 * @param an               ob die Tabelle ueberhaupt greift
 * @param grundProzent     wie oft ein Erz kommt, bei Stufe 0
 * @param jeStufeProzent   wie viel jede Stufe darauflegt
 * @param hoechstProzent   wo es aufhoert - auch bei hoher Stufe
 * @param marken           ob ein getauschter Block eine Blockmarke bekommt und sein Abbau
 *                         damit KEINEN Lohn zahlt
 * @param tabelle          je Gegenstand sein Gewicht und seine Mindeststufe
 */
public record Erzwerte(boolean an, int grundProzent, int jeStufeProzent,
                       int hoechstProzent, boolean marken,
                       Map<String, Erzwerte.Erzzeile> tabelle) {

    /**
     * Eine Zeile der Erztabelle.
     *
     * @param gewicht  wie schwer diese Zeile im Vergleich zu den anderen wiegt
     * @param abStufe  ab welcher Insel-Stufe sie ueberhaupt mitspielt
     */
    public record Erzzeile(int gewicht, int abStufe) {

        public Erzzeile {
            if (gewicht < 1 || gewicht > HOECHSTES_GEWICHT) {
                throw new IllegalArgumentException("Das Gewicht ist " + gewicht
                        + " und liegt nicht zwischen 1 und " + HOECHSTES_GEWICHT
                        + ". Wer eine Zeile abschalten will, nimmt sie heraus - ein"
                        + " Gewicht 0 waere eine Zeile, die nie kommt.");
            }
            if (abStufe < 0 || abStufe > HOECHSTE_STUFE) {
                throw new IllegalArgumentException("Die Mindeststufe ist " + abStufe
                        + " und liegt nicht zwischen 0 und " + HOECHSTE_STUFE + ".");
            }
        }
    }

    /** Der Schluessel des Schalters in der Einstellungsdatei. */
    public static final String PFAD_AN = "wolken.erze.an";

    /** Der Schluessel des Grundwertes. */
    public static final String PFAD_GRUND = "wolken.erze.grund-prozent";

    /** Der Schluessel des Zuschlags je Stufe. */
    public static final String PFAD_JE_STUFE = "wolken.erze.je-stufe-prozent";

    /** Der Schluessel des Deckels. */
    public static final String PFAD_HOECHST = "wolken.erze.hoechst-prozent";

    /** Der Schluessel des Markenschalters. */
    public static final String PFAD_MARKEN = "wolken.erze.marken";

    /** Der Schluessel der Tabelle. */
    public static final String PFAD_TABELLE = "wolken.erze.tabelle";

    /**
     * Der Unterschluessel des Gewichts, JE Zeile der Tabelle.
     *
     * <p>Hier und nicht nur im Leser: Die Einstellungsdatei schreibt Namen, die Laufzeit
     * braucht Schluessel - stehen sie an zwei Stellen, haelt eine Probe, die die fertige
     * Tabelle hereingibt, die Naht nie fest.
     */
    public static final String SCHLUESSEL_GEWICHT = "gewicht";

    /** Der Unterschluessel der Mindeststufe, je Zeile der Tabelle. */
    public static final String SCHLUESSEL_AB_STUFE = "ab-stufe";

    /** Die zwei Bloecke, die aus Lava und Wasser entstehen - siehe Klassenkopf. */
    public static final Set<String> AUSGANG = Set.of("cobblestone", "stone");

    /** Das hoechste Gewicht einer Zeile - gegen den Vertipper. */
    public static final int HOECHSTES_GEWICHT = 1_000_000;

    /**
     * Die hoechste Mindeststufe, die eine Zeile tragen darf.
     *
     * <p>Hundert, und das ist weit mehr als erreichbar: Die Stufe ist die Zahl der
     * erfuellten Herausforderungen, und so viele gibt es nicht. Eine Zeile oberhalb
     * waere eine, die nie kommt - und eine Einstellung, die nie wirkt, liest der
     * Betreiber als eine, die kaputt ist.
     */
    public static final int HOECHSTE_STUFE = 100;

    /** Ab Werk kommt bei Stufe 0 jeder siebte Kobbelstein als Erz. */
    public static final int VORGABE_GRUND = 15;

    /** Und jede Stufe legt zwei Prozentpunkte darauf. */
    public static final int VORGABE_JE_STUFE = 2;

    /**
     * Der Deckel - vierzig Prozent.
     *
     * <p>Mehr als jeder dritte Block waere kein Generator mehr, sondern eine Ader. Und
     * der Deckel ist der Grund, warum der Zuschlag je Stufe klein sein darf: Er greift
     * ab Stufe 13, und bis dahin ist der Fortschritt zu spueren.
     */
    public static final int VORGABE_HOECHST = 40;

    /**
     * Die ausgelieferte Erztabelle - neun Zeilen, und die Reihenfolge der Stufen IST der
     * Fortschritt.
     *
     * <p>Kohle und Kupfer von Anfang an: ohne Kohle kein Ofen, ohne Ofen kein Eisen. Eisen
     * ab Stufe 1, damit die erste Herausforderung wirklich etwas aufschliesst. Gold und
     * Redstone ab 2, Lapis ab 3 (er ist nur zum Verzaubern da), Smaragd ab 4 - er ist in
     * Vanilla das seltenste Erz und hier das Geld fuer den Bundesmarkt. Diamant ab 5, und
     * ANCIENT_DEBRIS ab 8: Netherit in einer Welt ohne Nether ist das eine Ziel, das weit
     * genug weg ist, um eines zu sein.
     */
    public static final Map<String, Erzzeile> VORGABE_TABELLE = Map.of(
            "coal_ore", new Erzzeile(50, 0),
            "copper_ore", new Erzzeile(30, 0),
            "iron_ore", new Erzzeile(25, 1),
            "gold_ore", new Erzzeile(12, 2),
            "redstone_ore", new Erzzeile(12, 2),
            "lapis_ore", new Erzzeile(8, 3),
            "emerald_ore", new Erzzeile(3, 4),
            "diamond_ore", new Erzzeile(3, 5),
            "ancient_debris", new Erzzeile(1, 8));

    /** Die Werte, die das Programm mitbringt. */
    public static final Erzwerte VORGABE = new Erzwerte(true, VORGABE_GRUND,
            VORGABE_JE_STUFE, VORGABE_HOECHST, true, VORGABE_TABELLE);

    public Erzwerte {
        if (!prozentBrauchbar(grundProzent) || !prozentBrauchbar(jeStufeProzent)
                || !prozentBrauchbar(hoechstProzent)) {
            throw new IllegalArgumentException("Grund, Zuschlag und Deckel sind "
                    + grundProzent + ", " + jeStufeProzent + " und " + hoechstProzent
                    + " - jeder davon muss zwischen 0 und 100 Prozent liegen.");
        }
        if (hoechstProzent < grundProzent) {
            // Sonst waere der Deckel unter dem Grundwert, und die Ausbeute SAENKE mit
            // der ersten Stufe. Das ist kein Fortschritt, sondern das Gegenteil - und im
            // Spiel haette es niemand gemeint.
            throw new IllegalArgumentException("Der Deckel " + hoechstProzent
                    + " liegt unter dem Grundwert " + grundProzent + " - dann saenke die"
                    + " Ausbeute mit der ersten Stufe.");
        }
        if (tabelle == null) {
            throw new IllegalArgumentException("Ohne Tabelle gibt es keine Erzwerte."
                    + " Eine LEERE Tabelle ist erlaubt und heisst: nur Kobbelstein.");
        }
        var abgelegt = new LinkedHashMap<String, Erzzeile>();
        for (var zeile : tabelle.entrySet()) {
            if (zeile.getKey() == null || zeile.getKey().isBlank()) {
                throw new IllegalArgumentException("Ein Erz ohne Namen gibt es nicht.");
            }
            if (zeile.getValue() == null) {
                throw new IllegalArgumentException("Die Zeile " + zeile.getKey()
                        + " hat kein Gewicht.");
            }
            abgelegt.put(zeile.getKey().trim().toLowerCase(Locale.ROOT),
                    zeile.getValue());
        }
        tabelle = Map.copyOf(abgelegt);
    }

    private static boolean prozentBrauchbar(int wert) {
        return wert >= 0 && wert <= 100;
    }

    /** Ob ueberhaupt getauscht wird. */
    public boolean greift() {
        return an && !tabelle.isEmpty() && hoechstProzent > 0;
    }

    /**
     * Wie oft bei dieser Stufe ein Erz kommt, in Prozent.
     *
     * <p>Gedeckelt, und der Deckel ist bindend: Bei Stufe 50 und zwei Prozentpunkten je
     * Stufe waeren es 115 Prozent, und das hiesse "immer" - der Kobbelstein, aus dem
     * alles Weitere gebaut wird, kaeme nie mehr.
     */
    public int prozentFuer(int stufe) {
        if (!greift() || stufe < 0) {
            return 0;
        }
        long gerechnet = (long) grundProzent + (long) jeStufeProzent * stufe;
        return (int) Math.min(hoechstProzent, gerechnet);
    }

    /**
     * Die Summe der Gewichte, die bei dieser Stufe mitspielen.
     *
     * <p>Null, wenn keine Zeile die Stufe erreicht - dann bleibt es bei Kobbelstein, auch
     * wenn der Prozentwurf getroffen hat. Das ist der Fall einer Tabelle, deren
     * niedrigste Zeile ab-stufe 3 traegt.
     */
    public long gewichtsumme(int stufe) {
        long summe = 0L;
        for (var zeile : tabelle.entrySet()) {
            if (zeile.getValue().abStufe() <= stufe) {
                summe += zeile.getValue().gewicht();
            }
        }
        return summe;
    }

    /** Ob dieser Block aus Lava und Wasser entsteht - und damit getauscht werden darf. */
    public static boolean istAusgang(String block) {
        return block != null && AUSGANG.contains(block.trim().toLowerCase(Locale.ROOT));
    }

    /** Dieselben Werte mit einem anderen Schalter. */
    public Erzwerte mitAn(boolean anders) {
        return new Erzwerte(anders, grundProzent, jeStufeProzent, hoechstProzent, marken,
                tabelle);
    }

    /** Dieselben Werte mit anderen Prozentzahlen. */
    public Erzwerte mitProzent(int grund, int jeStufe, int hoechst) {
        return new Erzwerte(an, grund, jeStufe, hoechst, marken, tabelle);
    }

    /** Dieselben Werte mit oder ohne Blockmarken. */
    public Erzwerte mitMarken(boolean anders) {
        return new Erzwerte(an, grundProzent, jeStufeProzent, hoechstProzent, anders,
                tabelle);
    }

    /** Dieselben Werte mit einer anderen Tabelle. */
    public Erzwerte mitTabelle(Map<String, Erzzeile> andere) {
        return new Erzwerte(an, grundProzent, jeStufeProzent, hoechstProzent, marken,
                andere);
    }
}
