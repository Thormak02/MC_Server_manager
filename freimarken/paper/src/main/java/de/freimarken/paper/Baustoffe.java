package de.freimarken.paper;

import de.freimarken.kern.Bauplan;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Welcher Block zu welchem {@link Bauplan.Stueck} gehoert - die Tabelle im Plugin.
 *
 * <p>Dieselbe Trennung wie bei {@code Wolkenboden}: Die Geometrie steht im Kern und kennt
 * kein {@code Material}, die Tabelle steht hier. Dadurch ist die Regel ohne laufenden
 * Server zu pruefen.
 *
 * <p><b>Die drei Bloecke stehen in der {@code config.yml}</b> und nicht im Programm: Ein
 * Platzhalter soll aussehen, wie der Betreiber ihn will, und ein anderer Baustoff ist
 * keine Zeile Java wert.
 *
 * <p><b>TUER und LUFT sind beide {@link Material#AIR}</b>, und das ist kein Versehen: Der
 * {@code Weltgriff} setzt einen Block und keine Blockdaten - eine echte Tuer traegt eine
 * Richtung und eine Haelfte, und die kann diese Naht nicht schreiben. Die Oeffnung ist
 * darum ein Loch in der Wand. Dass sie im Plan ein eigenes Stueck ist und nicht einfach
 * Luft, hat seinen Grund: So steht im Bauplan, WO die Tuer ist, und wer spaeter eine
 * echte Tuer setzen will, muss sie nicht ein zweites Mal ausrechnen.
 *
 * <p>Gelesen wird mit {@link Material#getMaterial(String)} und NICHT mit
 * {@code matchMaterial} oder der Registry: Jene fragen den laufenden Server, und dieser
 * Leser laeuft auch in einer Probe.
 */
record Baustoffe(Material boden, Material wand, Material dach, Material tuer,
                 Material schild) {

    /** Der Abschnitt mit den drei Bloecken. */
    static final String PFAD = "bauten.baustoffe";

    /** Boden ab Werk - heller als die Wand, damit ein Innenraum als Raum zu sehen ist. */
    static final Material BODEN_AB_WERK = Material.SMOOTH_STONE;

    /** Wand ab Werk. */
    static final Material WAND_AB_WERK = Material.STONE_BRICKS;

    /** Dach ab Werk. */
    static final Material DACH_AB_WERK = Material.POLISHED_ANDESITE;

    /**
     * Die Tuer ab Werk.
     *
     * <p>Eichenholz und kein Eisen: Eine Eisentuer braucht einen Hebel, und ein
     * Platzhalter mit einer Tuer, die niemand aufbekommt, ist schlimmer als einer mit
     * einem Loch in der Wand.
     */
    static final Material TUER_AB_WERK = Material.OAK_DOOR;

    /** Das Namensschild ab Werk - ein WANDschild, es haengt an der Wand. */
    static final Material SCHILD_AB_WERK = Material.OAK_WALL_SIGN;

    Baustoffe {
        if (boden == null || wand == null || dach == null || tuer == null
                || schild == null) {
            throw new IllegalArgumentException("Ein Baustoff fehlt - ohne Block gibt es"
                    + " keinen Bau");
        }
    }

    /** Die Baustoffe ab Werk - gebraucht, wenn der Abschnitt fehlt. */
    static Baustoffe abWerk() {
        return new Baustoffe(BODEN_AB_WERK, WAND_AB_WERK, DACH_AB_WERK,
                TUER_AB_WERK, SCHILD_AB_WERK);
    }

    /**
     * Liest {@code bauten.baustoffe}. Ein unbekannter Block wird beklagt, und der Wert ab
     * Werk gilt - ein Platzhalter ohne Wand waere schlimmer als einer in der falschen
     * Farbe.
     */
    static Baustoffe ausKonfiguration(ConfigurationSection wurzel, List<String> klagen) {
        if (wurzel == null) {
            return abWerk();
        }
        ConfigurationSection abschnitt = wurzel.getConfigurationSection(PFAD);
        if (abschnitt == null) {
            return abWerk();
        }
        return new Baustoffe(
                block(abschnitt, "boden", BODEN_AB_WERK, klagen),
                block(abschnitt, "wand", WAND_AB_WERK, klagen),
                block(abschnitt, "dach", DACH_AB_WERK, klagen),
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

    /** Der Block fuer dieses Stueck. */
    Material block(Bauplan.Stueck stueck) {
        return switch (stueck) {
            case BODEN -> boden;
            case WAND -> wand;
            case DACH -> dach;
            case TUER, LUFT -> Material.AIR;
        };
    }

    /** Die Zeile fuer den Startbericht - der Betreiber sieht, womit gebaut wird. */
    String bericht() {
        List<String> worte = new ArrayList<>();
        worte.add("Boden " + boden.name().toLowerCase(Locale.ROOT));
        worte.add("Wand " + wand.name().toLowerCase(Locale.ROOT));
        worte.add("Dach " + dach.name().toLowerCase(Locale.ROOT));
        worte.add("Tuer " + tuer.name().toLowerCase(Locale.ROOT));
        worte.add("Schild " + schild.name().toLowerCase(Locale.ROOT));
        return String.join(", ", worte);
    }
}
