package de.freimarken.paper;

import de.freimarken.kern.Stadtplan;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Womit der Boden Altfurts belegt wird - je {@link Stadtplan.Lage} eine MISCHUNG.
 *
 * <p><b>Eine eigene Tafel und kein Teil von {@link Baustoffe}</b>: Jene beantwortet
 * "welcher Block ist dieses Stueck eines HAUSES", diese "welcher Block liegt auf diesem
 * Stueck BODEN". Zusammengelegt muesste jede Probe, die ein Haus baut, auch das Pflaster
 * mitfuehren - und jede Aenderung am Pflaster zoege die Hausproben mit.
 *
 * <p><b>Eine MISCHUNG und kein einzelner Block</b> [23.11.2026]: Eine Gasse aus einem
 * Block sieht aus wie eine Flaeche, nicht wie ein Weg - der Betreiber nannte die erste
 * Fassung "etwas unschoen" und verlangte "texturiert, also mit gemischten
 * zusammenpassenden bloecken". Welcher Block einer Mischung an einer Stelle liegt,
 * rechnet {@link Stadtplan#mischung} - fest, also nach einem Neustart dieselbe Stelle,
 * derselbe Block.
 *
 * <p><b>Die Gewichtung steckt in der Liste</b>: Wer {@code [cobblestone, cobblestone,
 * cobblestone, gravel]} eintraegt, bekommt dreimal so viel Kopfstein wie Kies. Keine
 * zweite Zahl je Block, keine Prozente - der Betreiber sieht in der Datei, was er meint.
 * Ein einzelner Block statt einer Liste gilt weiter und heisst "nur dieser".
 *
 * <p><b>Eine Lage ohne Stoff wird NICHT angefasst</b>, und darum steht dort eine LEERE
 * Liste und nicht {@code Material.AIR}: Luft ist ein Block. Wer sie auf den Boden einer
 * Ebene setzt, LOESCHT ihn - und wer dann darueber laeuft, faellt ins Leere.
 *
 * <p><b>{@link Stadtplan.Lage#AUSSEN} hat KEINEN Schluessel</b>: Das Pflastern geht nur
 * den {@link Stadtplan#umriss} ab, und dessen Weite IST der aeussere Ring -
 * {@code lageBei} gibt AUSSEN erst jenseits davon. Innerhalb des Umrisses kommt die Lage
 * also nie vor. Ein Schluessel {@code aussen:} wurde vorher gelesen, im Startbericht
 * genannt und von einer Probe bestaetigt - und setzte keinen einzigen Block. Ein
 * eingetragener Wert wird darum GEKLAGT, statt ins Leere zu laufen.
 *
 * <p>Gelesen wird mit {@link Material#getMaterial(String)} wie bei {@link Baustoffe} und
 * aus demselben Grund: {@code matchMaterial} fragt den laufenden Server.
 */
record Pflasterstoffe(Map<Stadtplan.Lage, List<Material>> boden) {

    /** Wo die Stoffe in der {@code config.yml} stehen. */
    static final String PFAD = "bauten.pflaster";

    Pflasterstoffe {
        if (boden == null) {
            throw new IllegalArgumentException("Ohne Tafel gibt es kein Pflaster");
        }
        Map<Stadtplan.Lage, List<Material>> fest = new EnumMap<>(Stadtplan.Lage.class);
        boden.forEach((lage, mischung) -> {
            if (lage == null) {
                throw new IllegalArgumentException("Eine Mischung ohne Lage");
            }
            if (mischung == null) {
                throw new IllegalArgumentException("Die Lage " + lage + " hat keine Liste"
                        + " - eine Lage ohne Stoff traegt eine LEERE Liste, damit sie"
                        + " nicht angefasst wird");
            }
            // EINZELN DURCHGEGANGEN und nicht mit contains(null) gefragt: Eine Liste
            // aus List.of wirft bei contains(null) selbst eine NullPointerException -
            // der Riegel haette jede Tafel unbrauchbar gemacht, auch die ab Werk.
            for (Material block : mischung) {
                if (block == null || block == Material.AIR) {
                    throw new IllegalArgumentException("Die Mischung von " + lage
                            + " enthaelt Luft oder nichts - Luft auf dem Boden einer"
                            + " Ebene LOESCHT ihn");
                }
            }
            fest.put(lage, List.copyOf(mischung));
        });
        boden = fest;
    }

    /**
     * Das Pflaster ab Werk - eine Hansestadt aus Stein, Kopfstein und getretener Erde.
     *
     * <p>Jede Mischung ist so gewaehlt, dass die Bloecke ZUSAMMENPASSEN: gleicher
     * Grauwert, nur andere Koernung. Eine Mischung aus Kopfstein und Sandstein waere
     * zwei Staedte in einer.
     *
     * <p>Der MARKT ist gesaegter Andesit mit etwas glattem Stein und Steinziegel: Ein
     * Marktplatz ist das einzige Stueck Boden einer alten Stadt, das jemand bezahlt und
     * gerichtet hat - und auch das wurde ueber Jahrhunderte geflickt.
     *
     * <p>Die GASSEN sind ueberwiegend Kopfstein mit Andesit und Kies dazwischen, der
     * ECKPLATZ moosig: Dort, wo keine Zeile hinreicht, geht niemand, und zwischen den
     * Steinen waechst etwas.
     *
     * <p>Die ZEILE ist karge Erde mit Kies und etwas Grobstein: Was hinter den Haeusern
     * liegt, ist Hof und nicht Strasse. Karg und nicht Gras, weil Gras sich ausbreitet
     * und in die Gassen kriecht.
     */
    static Pflasterstoffe abWerk() {
        Map<Stadtplan.Lage, List<Material>> tafel = new EnumMap<>(Stadtplan.Lage.class);
        tafel.put(Stadtplan.Lage.MARKT, List.of(
                Material.POLISHED_ANDESITE, Material.POLISHED_ANDESITE,
                Material.POLISHED_ANDESITE, Material.SMOOTH_STONE,
                Material.STONE_BRICKS, Material.ANDESITE));
        // DER KIRCHPLATZ ist der aelteste der drei: Steinziegel, von denen die Haelfte
        // bemoost ist. Ein Kirchhof wird nicht neu gepflastert.
        tafel.put(Stadtplan.Lage.KIRCHPLATZ, List.of(
                Material.STONE_BRICKS, Material.STONE_BRICKS,
                Material.MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
                Material.COBBLESTONE, Material.ANDESITE));
        // DER RATHAUSPLATZ ist der gerichtete: glatter Stein, gesaegter Andesit und
        // gemeisselte Ziegel. Hier zeigt die Stadt, was sie sich leisten kann.
        tafel.put(Stadtplan.Lage.RATHAUSPLATZ, List.of(
                Material.SMOOTH_STONE, Material.SMOOTH_STONE,
                Material.POLISHED_ANDESITE, Material.POLISHED_ANDESITE,
                Material.STONE_BRICKS, Material.CHISELED_STONE_BRICKS));
        // DIE PLATZKANTE ist der bepflanzte Streifen: Stein mit Erdinseln, in die
        // Baeume und Brunnen kommen.
        tafel.put(Stadtplan.Lage.PLATZKANTE, List.of(
                Material.COBBLESTONE, Material.COBBLESTONE, Material.ANDESITE,
                Material.PODZOL, Material.PODZOL, Material.COARSE_DIRT));
        tafel.put(Stadtplan.Lage.KERN, List.of(
                Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLESTONE,
                Material.ANDESITE, Material.STONE, Material.MOSSY_COBBLESTONE));
        tafel.put(Stadtplan.Lage.RINGGASSE, List.of(
                Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLESTONE,
                Material.COBBLESTONE, Material.ANDESITE, Material.STONE,
                Material.GRAVEL, Material.MOSSY_COBBLESTONE));
        tafel.put(Stadtplan.Lage.RADIALGASSE, List.of(
                Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLESTONE,
                Material.STONE, Material.ANDESITE, Material.GRAVEL));
        tafel.put(Stadtplan.Lage.ECKPLATZ, List.of(
                Material.MOSSY_COBBLESTONE, Material.MOSSY_COBBLESTONE,
                Material.COBBLESTONE, Material.GRAVEL, Material.COARSE_DIRT));
        tafel.put(Stadtplan.Lage.ZEILE, List.of(
                Material.COARSE_DIRT, Material.COARSE_DIRT, Material.COARSE_DIRT,
                Material.GRAVEL, Material.COBBLESTONE));
        tafel.put(Stadtplan.Lage.MAUER, List.of(
                Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICKS,
                Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICKS,
                Material.CRACKED_DEEPSLATE_BRICKS, Material.COBBLED_DEEPSLATE,
                Material.DEEPSLATE_TILES));
        // AUSSEN BEKOMMT KEINEN EINTRAG - siehe Klassenkopf: Dort wird nicht gepflastert.
        return new Pflasterstoffe(tafel);
    }

    /**
     * Liest {@code bauten.pflaster} - je Lage ein Block oder eine Liste von Bloecken.
     *
     * <p>Fehlt der Abschnitt, gilt das Werk. Fehlt eine ZEILE, gilt ihre Werkmischung:
     * Wer nur die Gasse umstellen will, soll nicht die ganze Tafel abschreiben muessen.
     *
     * <p>Ein unbekannter Blockname wird GEKLAGT und aus der Mischung GENOMMEN - dieselbe
     * Linie wie bei {@link Baustoffe}: Ein Tippfehler, der ein graues Pflaster macht,
     * sieht sonst aus wie eine Entscheidung. Bleibt davon nichts uebrig, gilt die
     * Werkmischung; eine leere Mischung waere eine Lage, die stillschweigend nicht
     * gepflastert wird.
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
        Map<Stadtplan.Lage, List<Material>> tafel = new EnumMap<>(Stadtplan.Lage.class);
        for (Stadtplan.Lage lage : Stadtplan.Lage.values()) {
            List<Material> abWerk = werk.mischung(lage);
            String schluessel = schluesselVon(lage);
            List<String> woerter = woerter(abschnitt, schluessel);
            if (lage == Stadtplan.Lage.AUSSEN) {
                // KEIN STOFF JENSEITS DER MAUER - siehe Klassenkopf: Das Pflaster
                // kommt dort nie hin, ein Eintrag waere ein Schluessel ohne Wirkung.
                if (!woerter.isEmpty()) {
                    klagen.add(PFAD + "." + schluessel + " ist \"" + woerter
                            + "\" - jenseits der Mauer wird nicht gepflastert, der"
                            + " Schluessel hat keine Wirkung. Das Pflaster geht nur"
                            + " den Umriss der Stadt ab.");
                }
                continue;
            }
            if (woerter.isEmpty()) {
                if (!abWerk.isEmpty()) {
                    tafel.put(lage, abWerk);
                }
                continue;
            }
            List<Material> gelesen = new ArrayList<>();
            for (String wort : woerter) {
                // KEIN isBlock(): Das schlaegt in der Registry nach und WIRFT ohne
                // laufenden Server - gemessen und in Blockbuchliste und Verdienstliste
                // festgehalten. Dieser Leser muss in jeder Probe laufen.
                Material gefunden = Material.getMaterial(
                        wort.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
                if (gefunden == null) {
                    klagen.add(PFAD + "." + schluessel + " nennt \"" + wort + "\" -"
                            + " diesen Block gibt es nicht. Er fehlt in der Mischung.");
                    continue;
                }
                if (gefunden == Material.AIR) {
                    klagen.add(PFAD + "." + schluessel + " nennt air - Luft ist ein"
                            + " Block und LOESCHT den Boden. Sie fehlt in der Mischung.");
                    continue;
                }
                gelesen.add(gefunden);
            }
            if (gelesen.isEmpty()) {
                klagen.add(PFAD + "." + schluessel + ": von der Mischung blieb nichts"
                        + " uebrig. Es gilt die Mischung ab Werk.");
                if (!abWerk.isEmpty()) {
                    tafel.put(lage, abWerk);
                }
                continue;
            }
            tafel.put(lage, gelesen);
        }
        return new Pflasterstoffe(tafel);
    }

    /**
     * Die Woerter eines Schluessels - ein einzelner Wert zaehlt als Liste mit einem.
     *
     * <p>Beides erlaubt, weil beides gemeint sein kann: {@code mauer: deepslate_bricks}
     * ist eine Mauer aus einem Stoff, {@code mauer: [a, b]} eine gemischte. Ein Leser,
     * der nur Listen nimmt, machte aus der ersten Form stillschweigend nichts.
     */
    private static List<String> woerter(ConfigurationSection abschnitt, String schluessel) {
        if (!abschnitt.contains(schluessel)) {
            return List.of();
        }
        List<String> liste = abschnitt.getStringList(schluessel);
        if (!liste.isEmpty()) {
            return liste;
        }
        String einzeln = abschnitt.getString(schluessel);
        return einzeln == null || einzeln.isBlank() ? List.of() : List.of(einzeln);
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

    /** Die Mischung dieser Lage - leer heisst: wird nicht angefasst. */
    List<Material> mischung(Stadtplan.Lage lage) {
        if (lage == null) {
            throw new IllegalArgumentException("Ohne Lage gibt es kein Pflaster");
        }
        return boden.getOrDefault(lage, List.of());
    }

    /**
     * Der Block dieser Lage an dieser Stelle, oder {@code null}, wenn dort nicht
     * gepflastert wird.
     *
     * <p>Die Stelle gehoert dazu, denn die Mischung haengt an ihr: {@code block(MARKT)}
     * ohne Ort koennte nur den ersten Block nennen, und der Markt waere wieder eine
     * Flaeche aus einem Stoff.
     */
    Material block(Stadtplan.Lage lage, int x, int z) {
        List<Material> mischung = mischung(lage);
        if (mischung.isEmpty()) {
            return null;
        }
        return mischung.get(Stadtplan.mischung(x, z, mischung.size()));
    }

    /** Die Zeile fuer den Startbericht - der Betreiber sieht, womit gepflastert wird. */
    String bericht() {
        StringBuilder raus = new StringBuilder("Pflaster: ");
        boolean erstes = true;
        for (Stadtplan.Lage lage : Stadtplan.Lage.values()) {
            List<Material> mischung = mischung(lage);
            if (mischung.isEmpty()) {
                continue;
            }
            if (!erstes) {
                raus.append("; ");
            }
            erstes = false;
            raus.append(schluesselVon(lage)).append(" ");
            for (int i = 0; i < mischung.size(); i++) {
                if (i > 0) {
                    raus.append("+");
                }
                raus.append(mischung.get(i).name().toLowerCase(Locale.ROOT));
            }
        }
        return raus.toString();
    }
}
