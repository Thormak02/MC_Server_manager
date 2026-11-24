package de.freimarken.paper;

import de.freimarken.kern.Richtung;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Liest {@code bauten.torhaeuser} und {@code bauten.dioramen} - wer hinter welchem Tor
 * liegt und wie es dahinter aussieht.
 *
 * <p><b>Die Torhaeuser haben KEINE Koordinaten in der Datei</b>, und das ist die
 * Entscheidung: Ihr Ort folgt aus der Stadt - je Seite eines im aeusseren Mauerring,
 * mittig auf der Achse. Was der Betreiber einstellt, ist, WELCHE WELT hinter welcher
 * Nische liegt. Vier Quader von Hand einzutragen waere eine zweite Wahrheit neben
 * {@code Torhausplan}, und bei jeder Aenderung der Stadtgroesse eine falsche.
 *
 * <p><b>Das Diorama ist eine MISCHUNG wie das Pflaster</b>: eine Liste von Bloecken, die
 * Gewichtung durch Wiederholung. Hinter dem Netherportal soll nicht eine Flaeche
 * Netherrack stehen, sondern Netherrack mit Magma und Glut darin - sonst sieht es aus wie
 * eine gestrichene Wand.
 *
 * <p><b>Eine Welt ohne Diorama bekommt Mauerwerk</b> und keine Klage: Nicht jeder
 * Betreiber will hinter jedem Portal eine Landschaft, und eine leere Wand ist kein
 * Fehler.
 */
record Torhausliste(Map<Richtung, Torhausliste.Tor> tore,
                    Map<String, List<Material>> dioramen) {

    /**
     * Ein Torhaus: zwei Welten, links und rechts im Durchgang.
     *
     * @param links  die Welt hinter der Nische auf der Seite des kleineren Laengsmasses,
     *               oder {@code null}
     * @param rechts die Welt hinter der gegenueberliegenden Nische, oder {@code null}
     */
    record Tor(String links, String rechts) {
    }

    /** Wo die Torhaeuser stehen. */
    static final String PFAD = "bauten.torhaeuser";

    /** Wo die Dioramen stehen. */
    static final String PFAD_DIORAMEN = "bauten.dioramen";

    Torhausliste {
        tore = Map.copyOf(tore);
        Map<String, List<Material>> feste = new LinkedHashMap<>();
        dioramen.forEach((welt, mischung) -> feste.put(welt, List.copyOf(mischung)));
        dioramen = Map.copyOf(feste);
    }

    /** Keine Torhaeuser - dann wird keines gesetzt. */
    static Torhausliste ohneTore() {
        return new Torhausliste(Map.of(), Map.of());
    }

    /**
     * Liest beide Abschnitte.
     *
     * <p>Ein unbekannter Blockname im Diorama wird GEKLAGT und fehlt dann darin -
     * dieselbe Linie wie beim Pflaster. Luft wird ebenfalls geklagt: Ein Diorama aus Luft
     * waere ein Loch in der Stadtmauer.
     *
     * <p>Eine Seite, die kein {@code links} und kein {@code rechts} nennt, wird
     * uebersprungen - aber das Torhaus wird trotzdem gebaut: Es ist ein Bauwerk der
     * Mauer, auch ohne Portale darin.
     */
    static Torhausliste ausKonfiguration(ConfigurationSection wurzel,
            List<String> klagen) {
        if (wurzel == null) {
            return ohneTore();
        }
        Map<Richtung, Tor> tore = new EnumMap<>(Richtung.class);
        ConfigurationSection abschnitt = wurzel.getConfigurationSection(PFAD);
        if (abschnitt != null) {
            for (String schluessel : abschnitt.getKeys(false)) {
                Richtung seite = seiteVon(schluessel);
                if (seite == null) {
                    klagen.add(PFAD + "." + schluessel + ": \"" + schluessel + "\" ist"
                            + " keine Seite. Erlaubt sind nord, ost, sued und west - je"
                            + " Seite ein Torhaus.");
                    continue;
                }
                ConfigurationSection eines = abschnitt.getConfigurationSection(schluessel);
                if (eines == null) {
                    klagen.add(PFAD + "." + schluessel + " hat keine Eintraege - ein"
                            + " Torhaus braucht links und rechts je eine Welt.");
                    continue;
                }
                tore.put(seite, new Tor(welt(eines, "links"), welt(eines, "rechts")));
            }
        }
        Map<String, List<Material>> dioramen = new LinkedHashMap<>();
        ConfigurationSection bilder = wurzel.getConfigurationSection(PFAD_DIORAMEN);
        if (bilder != null) {
            for (String welt : bilder.getKeys(false)) {
                List<Material> mischung = mischung(bilder, welt, klagen);
                if (!mischung.isEmpty()) {
                    dioramen.put(welt, mischung);
                }
            }
        }
        return new Torhausliste(tore, dioramen);
    }

    private static String welt(ConfigurationSection eines, String schluessel) {
        String name = eines.getString(schluessel);
        return name == null || name.isBlank() ? null : name.trim();
    }

    private static List<Material> mischung(ConfigurationSection bilder, String welt,
            List<String> klagen) {
        List<String> woerter = bilder.getStringList(welt);
        if (woerter.isEmpty()) {
            String einzeln = bilder.getString(welt);
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
                klagen.add(PFAD_DIORAMEN + "." + welt + " nennt \"" + wort + "\" - diesen"
                        + " Block gibt es nicht. Er fehlt im Diorama.");
                continue;
            }
            if (gefunden == Material.AIR) {
                klagen.add(PFAD_DIORAMEN + "." + welt + " nennt air - ein Diorama aus"
                        + " Luft waere ein Loch in der Stadtmauer. Es fehlt darin.");
                continue;
            }
            raus.add(gefunden);
        }
        return raus;
    }

    /** Die Seite zu einem Schluessel der Datei, oder {@code null}. */
    static Richtung seiteVon(String wort) {
        if (wort == null) {
            return null;
        }
        for (Richtung richtung : Richtung.values()) {
            if (richtung.wort().equalsIgnoreCase(wort.trim())) {
                return richtung;
            }
        }
        return null;
    }

    /** Das Torhaus dieser Seite, oder {@code null}. */
    Tor tor(Richtung seite) {
        return tore.get(seite);
    }

    /** Die Mischung des Dioramas dieser Welt - leer heisst: Mauerwerk dahinter. */
    List<Material> diorama(String welt) {
        return welt == null ? List.of() : dioramen.getOrDefault(welt, List.of());
    }

    /** Ob es ueberhaupt Torhaeuser gibt. */
    boolean leer() {
        return tore.isEmpty();
    }

    /** Die Zeile fuer den Startbericht. */
    String bericht() {
        if (tore.isEmpty()) {
            return "Keine Torhaeuser eingetragen (" + PFAD + ") - die Stadtmauer bleibt"
                    + " ohne Tor, und die Weltportale liegen dort, wo welten: sie"
                    + " hinlegt.";
        }
        StringBuilder raus = new StringBuilder("Torhaeuser: ");
        boolean erstes = true;
        for (Map.Entry<Richtung, Tor> eintrag : tore.entrySet()) {
            if (!erstes) {
                raus.append("; ");
            }
            erstes = false;
            raus.append(eintrag.getKey().wort()).append(" links ")
                    .append(eintrag.getValue().links() == null ? "-"
                            : eintrag.getValue().links())
                    .append(", rechts ")
                    .append(eintrag.getValue().rechts() == null ? "-"
                            : eintrag.getValue().rechts());
        }
        raus.append(". Dioramen fuer: ").append(dioramen.isEmpty() ? "keine Welt"
                : String.join(", ", dioramen.keySet()));
        return raus.toString();
    }
}
