package de.freimarken.paper;

import de.freimarken.kern.Baustil;
import de.freimarken.kern.Bauplan;
import de.freimarken.kern.Dachart;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Welcher Block zu welchem {@link Bauplan.Stueck} gehoert - die Tabelle im Plugin.
 *
 * <p>Dieselbe Trennung wie bei {@code Wolkenboden}: Die Geometrie steht im Kern und kennt
 * kein {@code Material}, die Tabelle steht hier. Dadurch ist die Form ohne laufenden
 * Server zu pruefen.
 *
 * <p><b>Fuenf Paletten, und ihre Farben sind entschieden</b>: Abschnitt 2.2 des Plans
 * nennt vier Farben und sagt ausdruecklich, sie wiederholten sich "in Schildern, Menues,
 * Rangfarben und offiziellen Gebaeuden". Bundesrot ist der Backstein, Markgold der
 * Sandstein mit Goldakzent, Forstgruen der Gruenspan, Schiefer der Tiefenschiefer. Das
 * Fachwerk ist die fuenfte und traegt keine Markenfarbe - es ist die Handwerkergasse.
 *
 * <p><b>Der Blick kommt vom alten Lumia</b>, und zwar von seinen Bildern
 * ({@code data/lumia-archiv/bilder}): Backsteinwand mit dunklen Balken, Glasfenster in
 * Gruppen, dunkles Dach mit Ueberstand, Sockel und Kaimauer aus Steinziegel, Laternen an
 * der Gasse. Modernisiert heisst: dieselbe Sprache mit den Bloecken von 26.3 - entrindete
 * Staemme als Balken, Tiefenschiefer und Tuff als Sockel, Schlammziegel als warme Flaeche,
 * oxidiertes Kupfer als Dach, Kupfergitter als Fenstergitter.
 *
 * <p><b>TUER und LUFT sind Luft</b>: Der {@code Weltgriff} setzt einen Block und keine
 * Blockdaten - die echte Tuer setzt der {@code Feingriff} danach in dieselbe Oeffnung.
 * Dass die Tuerstelle im Plan ein eigenes Stueck ist und nicht einfach Luft, hat seinen
 * Grund: So steht im Bauplan, WO sie ist.
 *
 * <p>Gelesen wird mit {@link Material#getMaterial(String)} und NICHT mit
 * {@code matchMaterial} oder der Registry: Jene fragen den laufenden Server, und dieser
 * Leser laeuft auch in einer Probe.
 */
record Baustoffe(Map<Baustil, Map<Bauplan.Stueck, Material>> paletten,
                 Map<Dachart, Dachstoff> daecher, Material tuer, Material schild) {

    /**
     * Die zwei Bloecke einer Dachart - die schraege Treppe und der volle Block.
     *
     * <p><b>Zwei und nicht einer</b> [23.11.2026]: Ein steiles Dach steigt zwei Ebenen,
     * bevor es sich einen Block einzieht. Zwei Treppen uebereinander liessen aussen die
     * oberen Haelften offen - von der Gasse aus ein Zickzack statt einer Flaeche. Die
     * untere Ebene jeder Stufe ist darum ein voller Block
     * ({@link Bauplan.Stueck#DACHFUSS}), und der First ist es auch: Eine Treppe als
     * First zeigte in eine der zwei Richtungen und saehe von der anderen Seite falsch
     * aus.
     */
    record Dachstoff(Material treppe, Material voll) {

        Dachstoff {
            if (treppe == null || voll == null) {
                throw new IllegalArgumentException("Eine Dachart braucht beide Bloecke -"
                        + " die Treppe und den vollen");
            }
        }
    }

    /** Der Abschnitt, in dem der Betreiber einzelne Bloecke austauschen kann. */
    static final String PFAD = "bauten.baustoffe";

    /**
     * Die Tuer ab Werk.
     *
     * <p>Eichenholz und kein Eisen: Eine Eisentuer braucht einen Hebel, und ein Haus mit
     * einer Tuer, die niemand aufbekommt, ist schlimmer als eines mit einem Loch.
     */
    static final Material TUER_AB_WERK = Material.OAK_DOOR;

    /** Das Namensschild ab Werk - ein WANDschild, es haengt an der Wand. */
    static final Material SCHILD_AB_WERK = Material.OAK_WALL_SIGN;

    Baustoffe {
        if (paletten == null || daecher == null || tuer == null || schild == null) {
            throw new IllegalArgumentException("Ein Baustoff fehlt - ohne Block gibt es"
                    + " keinen Bau");
        }
        for (Baustil stil : Baustil.values()) {
            Map<Bauplan.Stueck, Material> palette = paletten.get(stil);
            if (palette == null) {
                throw new IllegalArgumentException("Dem Stil " + stil.wort() + " fehlt"
                        + " seine Palette");
            }
            for (Bauplan.Stueck stueck : Bauplan.Stueck.values()) {
                if (!palette.containsKey(stueck)) {
                    throw new IllegalArgumentException("Dem Stil " + stil.wort()
                            + " fehlt der Block fuer " + stueck + " - ein Stueck ohne"
                            + " Block waere ein Loch in jeder Wand");
                }
            }
        }
        for (Dachart art : Dachart.values()) {
            if (!daecher.containsKey(art)) {
                throw new IllegalArgumentException("Der Dachart " + art.wort()
                        + " fehlt ihr Block");
            }
        }
    }

    /** Die Tabelle ab Werk - alle fuenf Paletten und beide Daecher. */
    static Baustoffe abWerk() {
        Map<Baustil, Map<Bauplan.Stueck, Material>> paletten =
                new EnumMap<>(Baustil.class);
        // BUNDESROT: Backstein mit dunklen Balken - der Blick des alten Lumia.
        paletten.put(Baustil.BACKSTEIN, palette(
                Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_BRICKS,
                Material.STRIPPED_DARK_OAK_WOOD, Material.BRICKS, Material.GLASS_PANE));
        // FACHWERK: helles Gefach zwischen dunklen Balken - die Handwerkergasse.
        paletten.put(Baustil.FACHWERK, palette(
                Material.COBBLESTONE, Material.STONE_BRICKS,
                Material.STRIPPED_SPRUCE_WOOD, Material.CALCITE, Material.GLASS_PANE));
        // MARKGOLD: warmer Sandstein mit Goldakzent, fuer das Muenzhaus.
        paletten.put(Baustil.GOLD, palette(
                Material.CUT_SANDSTONE, Material.SMOOTH_SANDSTONE,
                Material.STRIPPED_DARK_OAK_WOOD, Material.SANDSTONE,
                Material.GLASS_PANE));
        // FORSTGRUEN: Schlammziegel und Gruenspan, fuer die Tore in die Natur.
        paletten.put(Baustil.FORST, palette(
                Material.POLISHED_TUFF, Material.TUFF_BRICKS,
                Material.STRIPPED_SPRUCE_WOOD, Material.MUD_BRICKS,
                Material.GLASS_PANE));
        // SCHIEFER: Tiefenschiefer und Tuff - der Grundton der Stadt.
        paletten.put(Baustil.SCHIEFER, palette(
                Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES,
                Material.STRIPPED_DARK_OAK_WOOD, Material.POLISHED_DEEPSLATE,
                Material.GLASS_PANE));

        Map<Dachart, Dachstoff> daecher = new EnumMap<>(Dachart.class);
        daecher.put(Dachart.ZIEGEL, new Dachstoff(Material.DARK_OAK_STAIRS,
                Material.DARK_OAK_PLANKS));
        // KEIN Grünspan-Treppenblock: OXIDIZED_COPPER_STAIRS gibt es nicht - im
        // paper-api nachgesehen [23.11.2026], Kupfer hat nur Falltuer, Gitter und
        // Stab. Die Treppe kommt darum vom GESCHNITTENEN Kupfer, das eine hat; der
        // volle Block bleibt der glatte, damit die Flaeche nicht gemustert wirkt.
        daecher.put(Dachart.KUPFER, new Dachstoff(Material.OXIDIZED_CUT_COPPER_STAIRS,
                Material.OXIDIZED_COPPER));
        return new Baustoffe(paletten, daecher, TUER_AB_WERK, SCHILD_AB_WERK);
    }

    /**
     * Eine Palette aus fuenf Bloecken - die uebrigen Stuecke folgen daraus.
     *
     * <p>Fuenf und nicht elf: Boden und Sockel sind derselbe Stein, Giebelstufe und First
     * sind dasselbe Mauerwerk wie die Wand, Laterne ist immer eine Laterne, Tuer und Luft
     * sind Luft. Elf Zeilen je Stil waeren sechs Gelegenheiten, zwei davon verschieden zu
     * machen, ohne es zu merken.
     */
    private static Map<Bauplan.Stueck, Material> palette(Material sockel, Material mauer,
            Material balken, Material gefach, Material fenster) {
        Map<Bauplan.Stueck, Material> palette = new EnumMap<>(Bauplan.Stueck.class);
        palette.put(Bauplan.Stueck.BODEN, sockel);
        palette.put(Bauplan.Stueck.SOCKEL, sockel);
        palette.put(Bauplan.Stueck.BALKEN, balken);
        palette.put(Bauplan.Stueck.GEFACH, gefach);
        palette.put(Bauplan.Stueck.FENSTER, fenster);
        palette.put(Bauplan.Stueck.LATERNE, Material.LANTERN);
        palette.put(Bauplan.Stueck.GIEBELSTUFE, mauer);
        palette.put(Bauplan.Stueck.FIRST, mauer);
        // DACH, DACHFUSS und FIRST kommen aus der Dachart und nicht aus der Palette -
        // hier steht der Rueckfall, falls je ein Stil ohne Dachart gefragt wird.
        palette.put(Bauplan.Stueck.DACH, mauer);
        palette.put(Bauplan.Stueck.DACHFUSS, mauer);
        palette.put(Bauplan.Stueck.TUER, Material.AIR);
        palette.put(Bauplan.Stueck.LUFT, Material.AIR);
        return palette;
    }

    /**
     * Liest {@code bauten.baustoffe} - nur Tuer und Schild sind einstellbar.
     *
     * <p>Die fuenf Paletten stehen NICHT in der Datei: Das waeren fuenf mal elf Zeilen,
     * also fuenfundfuenfzig Gelegenheiten, eine Stadt uneinheitlich zu machen - und
     * Abschnitt 2.2 verlangt das Gegenteil. Wer eine Palette aendern will, aendert sie
     * hier, und dann aendert sie sich fuer jedes Haus desselben Stils.
     */
    static Baustoffe ausKonfiguration(ConfigurationSection wurzel, List<String> klagen) {
        Baustoffe werk = abWerk();
        if (wurzel == null) {
            return werk;
        }
        ConfigurationSection abschnitt = wurzel.getConfigurationSection(PFAD);
        if (abschnitt == null) {
            return werk;
        }
        return new Baustoffe(werk.paletten(), werk.daecher(),
                block(abschnitt, "tuer", TUER_AB_WERK, klagen),
                block(abschnitt, "schild", SCHILD_AB_WERK, klagen));
    }

    private static Material block(ConfigurationSection abschnitt, String schluessel,
            Material abWerk, List<String> klagen) {
        String gelesen = abschnitt.getString(schluessel);
        if (gelesen == null || gelesen.isBlank()) {
            return abWerk;
        }
        Material gefunden = Material.getMaterial(
                gelesen.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
        if (gefunden == null) {
            klagen.add(PFAD + "." + schluessel + " ist \"" + gelesen + "\" - diesen Block"
                    + " gibt es nicht. Es gilt " + abWerk.name().toLowerCase(Locale.ROOT)
                    + ".");
            return abWerk;
        }
        return gefunden;
    }

    /**
     * Der Block fuer dieses Stueck in diesem Stil, mit dieser Dachart.
     *
     * <p>Die Dachart gewinnt fuer DACH, DACHFUSS und FIRST: Der First ist die Krone des
     * Dachs und nicht das oberste Mauerwerk.
     *
     * <p><b>Dass der First dazugehoert, ist am laufenden Server gefunden worden</b>
     * [23.11.2026]: Ein Bau mit einer Front von DREI Bloecken hat genau eine Giebelstufe,
     * und die ist sofort der First - er hat also gar keine Dachflaeche. Solange der First
     * aus der Palette kam, war {@code dach:} bei allen vier Tortuermen Altfurts eine
     * Einstellung OHNE WIRKUNG, und keine Probe sah es: Alle prueften an einem Haus mit
     * sieben Bloecken Front.
     */
    Material block(Baustil stil, Dachart dach, Bauplan.Stueck stueck) {
        Dachstoff stoff = daecher.get(dach);
        if (stueck == Bauplan.Stueck.DACH) {
            return stoff.treppe();
        }
        // DACHFUSS und FIRST sind die VOLLEN Bloecke derselben Dachart: Der Fuss jeder
        // Dachstufe traegt die Treppe darueber, und der First ist die eine Reihe, in der
        // beide Dachseiten sich treffen - eine Treppe dort zeigte in eine Richtung und
        // saehe von der anderen Seite falsch aus.
        if (stueck == Bauplan.Stueck.DACHFUSS || stueck == Bauplan.Stueck.FIRST) {
            return stoff.voll();
        }
        return paletten.get(stil).get(stueck);
    }

    /** Die Zeile fuer den Startbericht - der Betreiber sieht, womit gebaut wird. */
    String bericht() {
        StringBuilder raus = new StringBuilder();
        for (Baustil stil : Baustil.values()) {
            if (raus.length() > 0) {
                raus.append(", ");
            }
            raus.append(stil.wort()).append(" ")
                    .append(paletten.get(stil).get(Bauplan.Stueck.GEFACH)
                            .name().toLowerCase(Locale.ROOT));
        }
        for (Dachart art : Dachart.values()) {
            Dachstoff stoff = daecher.get(art);
            raus.append(", Dach ").append(art.wort()).append(" ")
                    .append(stoff.treppe().name().toLowerCase(Locale.ROOT))
                    .append(" auf ")
                    .append(stoff.voll().name().toLowerCase(Locale.ROOT));
        }
        return raus.toString();
    }
}
