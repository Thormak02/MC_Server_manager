package de.freimarken.paper;

import de.freimarken.kern.Stadtplan;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Womit der Boden Altfurts belegt wird - je {@link Stadtplan.Lage} ein Block.
 *
 * <p><b>Eine eigene Tafel und kein Teil von {@link Baustoffe}</b>: Jene beantwortet
 * "welcher Block ist dieses Stueck eines HAUSES", diese "welcher Block liegt auf diesem
 * Stueck BODEN". Zusammengelegt muesste jede Probe, die ein Haus baut, auch das Pflaster
 * mitfuehren - und jede Aenderung am Pflaster zoege die Hausproben mit.
 *
 * <p><b>Eine Lage ohne Stoff wird NICHT angefasst</b>, und darum steht dort
 * {@code null} und nicht {@code Material.AIR}: Luft ist ein Block. Wer sie auf den Boden
 * einer Ebene setzt, LOESCHT ihn - und wer dann darueber laeuft, faellt ins Leere.
 *
 * <p><b>{@link Stadtplan.Lage#AUSSEN} hat KEINEN Schluessel</b> [berichtigt 23.11.2026]:
 * Das Pflastern geht nur den {@link Stadtplan#umriss} ab, und dessen Weite IST der
 * aeussere Ring - {@code lageBei} gibt AUSSEN erst jenseits davon. Innerhalb des
 * Umrisses kommt die Lage also nie vor. Ein Schluessel {@code aussen:} wurde vorher
 * gelesen, im Startbericht genannt und von einer Probe bestaetigt - und setzte keinen
 * einzigen Block. Ein eingetragener Wert wird darum GEKLAGT, statt ins Leere zu
 * laufen.
 *
 * <p>Gelesen wird mit {@link Material#getMaterial(String)} wie bei {@link Baustoffe} und
 * aus demselben Grund: {@code matchMaterial} fragt den laufenden Server.
 */
record Pflasterstoffe(Map<Stadtplan.Lage, Material> boden) {

    /** Wo die Stoffe in der {@code config.yml} stehen. */
    static final String PFAD = "bauten.pflaster";

    Pflasterstoffe {
        if (boden == null) {
            throw new IllegalArgumentException("Ohne Tafel gibt es kein Pflaster");
        }
        boden = Map.copyOf(boden);
    }

    /**
     * Das Pflaster ab Werk - eine Hansestadt aus Stein, Kopfstein und getretener Erde.
     *
     * <p>Der MARKT ist gesaegter Andesit: Ein Marktplatz ist das einzige Stueck Boden
     * einer alten Stadt, das jemand bezahlt und gerichtet hat.
     *
     * <p>Die GASSEN sind Kopfstein, der ECKPLATZ moosiger Kopfstein - dort, wo keine
     * Zeile hinreicht, geht niemand, und zwischen den Steinen waechst etwas.
     *
     * <p>Die ZEILE ist karge Erde: Was hinter den Haeusern liegt, ist Hof und nicht
     * Strasse. Karg und nicht Gras, weil Gras sich ausbreitet und in die Gassen kriecht.
     */
    static Pflasterstoffe abWerk() {
        Map<Stadtplan.Lage, Material> tafel = new EnumMap<>(Stadtplan.Lage.class);
        tafel.put(Stadtplan.Lage.MARKT, Material.POLISHED_ANDESITE);
        tafel.put(Stadtplan.Lage.KERN, Material.COBBLESTONE);
        tafel.put(Stadtplan.Lage.RINGGASSE, Material.COBBLESTONE);
        tafel.put(Stadtplan.Lage.RADIALGASSE, Material.COBBLESTONE);
        tafel.put(Stadtplan.Lage.ECKPLATZ, Material.MOSSY_COBBLESTONE);
        tafel.put(Stadtplan.Lage.ZEILE, Material.COARSE_DIRT);
        tafel.put(Stadtplan.Lage.MAUER, Material.DEEPSLATE_BRICKS);
        // AUSSEN BEKOMMT KEINEN EINTRAG - siehe Klassenkopf: Dort wird nicht gepflastert.
        return new Pflasterstoffe(tafel);
    }

    /**
     * Liest {@code bauten.pflaster} - je Lage ein Schluessel in Kleinbuchstaben.
     *
     * <p>Fehlt der Abschnitt, gilt das Werk. Fehlt eine ZEILE, gilt ihr Werkstoff: Wer
     * nur die Gasse umstellen will, soll nicht die ganze Tafel abschreiben muessen.
     *
     * <p>Ein unbekannter Blockname wird GEKLAGT und nicht stillschweigend uebergangen -
     * dieselbe Linie wie bei {@link Baustoffe}: Ein Tippfehler, der ein graues Pflaster
     * macht, sieht aus wie eine Entscheidung.
     */
    static Pflasterstoffe ausKonfiguration(ConfigurationSection wurzel,
            List<String> klagen) {
        Pflasterstoffe werk = abWerk();
        if (wurzel == null) {
            return werk;
        }
        ConfigurationSection abschnitt = wurzel.getConfigurationSection(PFAD);
        if (abschnitt == null) {
            return werk;
        }
        Map<Stadtplan.Lage, Material> tafel = new EnumMap<>(Stadtplan.Lage.class);
        for (Stadtplan.Lage lage : Stadtplan.Lage.values()) {
            Material abWerk = werk.boden().get(lage);
            String schluessel = schluesselVon(lage);
            String wort = abschnitt.getString(schluessel);
            if (lage == Stadtplan.Lage.AUSSEN) {
                // KEIN STOFF JENSEITS DER MAUER - siehe Klassenkopf: Das Pflaster
                // kommt dort nie hin, ein Eintrag waere ein Schluessel ohne Wirkung.
                if (wort != null && !wort.isBlank()) {
                    klagen.add(PFAD + "." + schluessel + " ist \"" + wort
                            + "\" - jenseits der Mauer wird nicht gepflastert, der"
                            + " Schluessel hat keine Wirkung. Das Pflaster geht nur"
                            + " den Umriss der Stadt ab.");
                }
                continue;
            }
            if (wort == null || wort.isBlank()) {
                if (abWerk != null) {
                    tafel.put(lage, abWerk);
                }
                continue;
            }
            // KEIN isBlock(): Das schlaegt in der Registry nach und WIRFT ohne laufenden
            // Server - gemessen und in Blockbuchliste und Verdienstliste festgehalten.
            // Dieser Leser muss in jeder Probe laufen, also bleibt es beim Namen.
            Material gefunden = Material.getMaterial(
                    wort.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
            if (gefunden == null) {
                klagen.add(PFAD + "." + schluessel + " ist \"" + wort + "\" - diesen"
                        + " Block gibt es nicht. Es bleibt bei "
                        + (abWerk == null ? "unberuehrt"
                                : abWerk.name().toLowerCase(Locale.ROOT)) + ".");
                if (abWerk != null) {
                    tafel.put(lage, abWerk);
                }
                continue;
            }
            tafel.put(lage, gefunden);
        }
        return new Pflasterstoffe(tafel);
    }

    /**
     * Der Schluessel einer Lage in der {@code config.yml} - der Name in Kleinbuchstaben.
     *
     * <p>Mechanisch aus dem Namen und nicht als zweite Liste: Eine Liste von Woertern
     * neben der Aufzaehlung waere eine Naht zwischen Datei und Laufzeit, die keine Probe
     * sieht - eine neue Lage haette dann stillschweigend keinen Schluessel.
     */
    static String schluesselVon(Stadtplan.Lage lage) {
        return lage.name().toLowerCase(Locale.ROOT);
    }

    /** Der Block dieser Lage, oder {@code null}, wenn dort nicht gepflastert wird. */
    Material block(Stadtplan.Lage lage) {
        if (lage == null) {
            throw new IllegalArgumentException("Ohne Lage gibt es kein Pflaster");
        }
        return boden.get(lage);
    }

    /** Die Zeile fuer den Startbericht - der Betreiber sieht, womit gepflastert wird. */
    String bericht() {
        StringBuilder raus = new StringBuilder("Pflaster: ");
        boolean erstes = true;
        for (Stadtplan.Lage lage : Stadtplan.Lage.values()) {
            Material block = boden.get(lage);
            if (block == null) {
                continue;
            }
            if (!erstes) {
                raus.append(", ");
            }
            erstes = false;
            raus.append(schluesselVon(lage)).append(" ")
                    .append(block.name().toLowerCase(Locale.ROOT));
        }
        return raus.toString();
    }
}
