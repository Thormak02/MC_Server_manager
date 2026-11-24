package de.freimarken.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest {@code bauten.platzgruen} - woraus die Baeume und Brunnen der Platzkante sind.
 *
 * <p><b>Warum das in der Datei steht</b>: Welche Baumart auf einem hansischen Marktplatz
 * steht, ist Geschmack und keine Rechnung. Linden, Eichen, Birken - der Betreiber
 * entscheidet, und er entscheidet es ohne Neubau.
 *
 * <p><b>Stamm und Laub sind MISCHUNGEN</b> wie das Pflaster, die Gewichtung durch
 * Wiederholung. Ein Laub aus genau einem Block sieht aus wie eine gestrichene Kugel; mit
 * zwei Blattarten darin wirkt es wie ein Baum. Dieselbe Linie wie beim Pflaster und bei
 * den Dioramen - eine Form, die der Betreiber schon kennt.
 *
 * <p><b>Fehlt der Abschnitt, steht nichts auf der Kante</b> und es gibt keine Klage: Wer
 * einen steinernen Platz will, laesst ihn weg. Geklagt wird nur ueber einen Block, den es
 * nicht gibt - dann waere die Absicht da und das Ergebnis ein Loch.
 */
record Platzgruen(List<Material> stamm, List<Material> laub, List<Material> brunnenrand,
                  Material wasser, int baumhoehe) {

    /** Wo das Gruen der Platzkante steht. */
    static final String PFAD = "bauten.platzgruen";

    /** Die niedrigste Baumhoehe, die noch ein Baum ist. */
    static final int HOEHE_WENIGSTENS = 3;

    /**
     * Die hoechste Baumhoehe.
     *
     * <p>Zehn: Hoeher als die Flankenhaeuser des Marktes soll kein Baum auf dem Platz
     * stehen - der Platz ist von Bauten gerahmt, und ein Baum, der sie ueberragt,
     * verdeckt genau das, was er rahmen soll.
     */
    static final int HOEHE_HOECHSTENS = 10;

    Platzgruen {
        stamm = List.copyOf(stamm);
        laub = List.copyOf(laub);
        brunnenrand = List.copyOf(brunnenrand);
        if (baumhoehe < HOEHE_WENIGSTENS || baumhoehe > HOEHE_HOECHSTENS) {
            throw new IllegalArgumentException("Eine Baumhoehe von " + baumhoehe
                    + " Ebenen passt nicht: erlaubt sind " + HOEHE_WENIGSTENS + " bis "
                    + HOEHE_HOECHSTENS);
        }
    }

    /** Nichts auf der Kante - dann bleibt sie Pflaster. */
    static Platzgruen ohneGruen() {
        return new Platzgruen(List.of(), List.of(), List.of(), null,
                HOEHE_WENIGSTENS);
    }

    /** Die ausgelieferte Fassung: Eiche mit zwei Laubarten, Brunnen aus Bruchstein. */
    static Platzgruen abWerk() {
        return new Platzgruen(
                List.of(Material.OAK_LOG),
                List.of(Material.OAK_LEAVES, Material.OAK_LEAVES, Material.AZALEA_LEAVES),
                List.of(Material.COBBLESTONE, Material.COBBLESTONE,
                        Material.MOSSY_COBBLESTONE, Material.STONE_BRICKS),
                Material.WATER, 5);
    }

    /** Ob ueberhaupt etwas gesetzt werden kann. */
    boolean stehtEtwas() {
        return !stamm.isEmpty() && !laub.isEmpty();
    }

    /** Ob ein Brunnen gesetzt werden kann - ohne Rand waere es ein Wasserloch. */
    boolean brunnenMoeglich() {
        return !brunnenrand.isEmpty() && wasser != null;
    }

    /**
     * Liest den Abschnitt.
     *
     * <p>Ein unbekannter Blockname wird GEKLAGT und fehlt dann in der Mischung - dieselbe
     * Linie wie beim Pflaster. Eine Baumhoehe ausserhalb der Schranken wird geklagt, und
     * dann gilt die ausgelieferte: Ein Baum von null Ebenen ist kein Baum, und einer von
     * hundert durchstoesst die Welt.
     */
    static Platzgruen ausKonfiguration(ConfigurationSection wurzel, List<String> klagen) {
        if (wurzel == null) {
            return ohneGruen();
        }
        ConfigurationSection abschnitt = wurzel.getConfigurationSection(PFAD);
        if (abschnitt == null) {
            return ohneGruen();
        }
        List<Material> stamm = mischung(abschnitt, "stamm", klagen);
        List<Material> laub = mischung(abschnitt, "laub", klagen);
        List<Material> rand = mischung(abschnitt, "brunnenrand", klagen);
        List<Material> wasser = mischung(abschnitt, "brunnenwasser", klagen);

        int hoehe = abWerk().baumhoehe();
        if (abschnitt.contains("baumhoehe")) {
            if (!abschnitt.isInt("baumhoehe")) {
                klagen.add(PFAD + ".baumhoehe ist keine ganze Zahl. Es bleibt bei "
                        + hoehe + " Ebenen.");
            } else {
                int gelesen = abschnitt.getInt("baumhoehe");
                if (gelesen < HOEHE_WENIGSTENS || gelesen > HOEHE_HOECHSTENS) {
                    klagen.add(PFAD + ".baumhoehe ist " + gelesen + " - erlaubt sind "
                            + HOEHE_WENIGSTENS + " bis " + HOEHE_HOECHSTENS
                            + " Ebenen. Es bleibt bei " + hoehe + ".");
                } else {
                    hoehe = gelesen;
                }
            }
        }
        // DER STAMM BRAUCHT LAUB UND UMGEKEHRT: Ein Stamm ohne Laub ist ein Pfahl, Laub
        // ohne Stamm haengt in der Luft. Fehlt eines, wird das andere nicht gesetzt - und
        // das wird gesagt, denn der Betreiber hat offensichtlich Baeume gewollt.
        if (stamm.isEmpty() != laub.isEmpty()) {
            klagen.add(PFAD + ": " + (stamm.isEmpty() ? "stamm" : "laub") + " fehlt -"
                    + " ein Stamm ohne Laub ist ein Pfahl, Laub ohne Stamm haengt in der"
                    + " Luft. Es wachsen keine Baeume auf der Platzkante.");
            stamm = List.of();
            laub = List.of();
        }
        if (rand.isEmpty() != wasser.isEmpty()) {
            klagen.add(PFAD + ": " + (rand.isEmpty() ? "brunnenrand" : "brunnenwasser")
                    + " fehlt - ein Brunnen braucht beides. Es stehen keine Brunnen auf"
                    + " der Platzkante; an ihren Standorten wachsen Baeume.");
            rand = List.of();
            wasser = List.of();
        }
        return new Platzgruen(stamm, laub, rand,
                wasser.isEmpty() ? null : wasser.get(0), hoehe);
    }

    private static List<Material> mischung(ConfigurationSection abschnitt,
            String schluessel, List<String> klagen) {
        List<String> woerter = abschnitt.getStringList(schluessel);
        if (woerter.isEmpty()) {
            String einzeln = abschnitt.getString(schluessel);
            if (einzeln != null && !einzeln.isBlank()) {
                woerter = List.of(einzeln);
            }
        }
        List<Material> raus = new ArrayList<>();
        for (String wort : woerter) {
            // KEIN isBlock(): Das wirft ohne laufenden Server - gemessen und in
            // Blockbuchliste festgehalten.
            Material gefunden = Material.getMaterial(
                    wort.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
            if (gefunden == null) {
                klagen.add(PFAD + "." + schluessel + " nennt \"" + wort + "\" - diesen"
                        + " Block gibt es nicht. Er fehlt in der Mischung.");
                continue;
            }
            if (gefunden == Material.AIR) {
                klagen.add(PFAD + "." + schluessel + " nennt air. Luft ist keine"
                        + " Mischung; der Eintrag fehlt.");
                continue;
            }
            raus.add(gefunden);
        }
        return raus;
    }
}
