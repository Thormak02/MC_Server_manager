package de.freimarken.kern;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Was eine Wolkeninsel kostet, wie viele einer halten darf und welche Biome sie tragen
 * kann.
 *
 * <p>Das Geschwister von {@link Inselwerte}, und der Unterschied ist der Preis: Plan 6.2
 * nennt die Skyblockinsel neben der ersten Parzelle als einen der <b>zwei gratis
 * Einstiege</b> - "wer nur bauen will, zahlt nie etwas". Die Null steht trotzdem in der
 * Datei und nicht im Programm, denn ein Preis ist ein Preis: Wer eines Tages fuer die
 * ZWEITE Insel etwas nehmen will, soll eine Zahl aendern und nicht eine Klasse.
 *
 * <p><b>Was dabei zu wissen ist</b>: Bei einem ausgelieferten Preis von 0 ist JEDE andere
 * Zahl ein Sprung ueber das Doppelte ({@link Sprungregel}), und der braucht die
 * Bestaetigung unter {@code wirtschaft.sprung-bestaetigt}. Das ist hier nicht im Weg,
 * sondern richtig: Aus gratis etwas Kostenpflichtiges zu machen ist die Aenderung, die
 * jeder Spieler sofort merkt.
 *
 * @param preis       was eine Insel kostet, in Mark; 0 heisst gratis
 * @param hoechstzahl wie viele Inseln ein Spieler ohne besonderes Recht haelt
 * @param biome       welche Biome {@code /wolke biom} anbietet; leer heisst: keine Wahl
 * @param starttruhe  was in der Truhe auf einer frischen Insel liegt, je Gegenstand eine
 *                    Anzahl; leer heisst: eine leere Truhe
 */
public record Wolkenwerte(long preis, int hoechstzahl, List<String> biome,
                          Map<String, Integer> starttruhe) {

    /** Der Schluessel des Preises in der Einstellungsdatei. */
    public static final String PFAD_PREIS = "wolken.preis";

    /** Der Schluessel der Obergrenze in der Einstellungsdatei. */
    public static final String PFAD_HOECHSTZAHL = "wolken.hoechstzahl";

    /** Der Schluessel der angebotenen Biome in der Einstellungsdatei. */
    public static final String PFAD_BIOME = "wolken.biome";

    /** Der Schluessel der Starttruhe in der Einstellungsdatei. */
    public static final String PFAD_STARTTRUHE = "wolken.starttruhe";

    /**
     * Was eine Wolkeninsel ab Werk kostet - nichts.
     *
     * <p>Plan 6.2: "Skyblock-Insel | Wolkenwerder | gratis", und Plan 4.1 nennt die Welt
     * "eigener Spielmodus, eigener Einstieg". Ein Einstieg, der 75.000 Mark kostet, ist
     * keiner - wer neu ist, hat sie nicht.
     */
    public static final long VORGABE_PREIS = 0L;

    /**
     * Wie viele Wolkeninseln ein Spieler ab Werk haelt - eine.
     *
     * <p>Eine, und zwar genau weil sie gratis ist: Wer zwei nehmen darf, nimmt die zweite
     * mit und laesst sie leer liegen, und ein Rasterfeld gibt es nur einmal. Mehr gibt es
     * ueber das RECHT {@code freimarken.wolken.<n>}, also je Rang; dieselbe Bauart wie bei
     * den Parzellen und den Meerinseln, damit die Rangzahlen in der Datei bleiben und
     * nicht im Programm.
     */
    public static final int VORGABE_HOECHSTZAHL = 1;

    /**
     * Welche Biome ab Werk angeboten werden - alle, die es gibt.
     *
     * <p>Die Tafel selbst steht in {@link Wolkenbiome}, samt dem Grund fuer jedes
     * einzelne. Hier steht nur, dass ab Werk keines zurueckgehalten wird: Die Biomwahl ist
     * einer der vier Reize dieser Welt (Plan 4.1), und ein Reiz, von dem der Betreiber
     * erst neun Zeilen eintragen muss, ist keiner.
     */
    public static final List<String> VORGABE_BIOME = Wolkenbiome.NAMEN;

    /**
     * Die Obergrenze fuer den Preis - dieselbe wie bei der Freistadt und der Meerinsel.
     *
     * <p>Gegen den Vertipper und nicht gegen den Betreiber.
     */
    public static final long HOECHSTER_BETRAG = Freistadtwerte.HOECHSTER_BETRAG;

    /**
     * Die Obergrenze fuer die Zahl - so viele Inseln hat die Welt.
     *
     * <p>Eintausendvierhundertdreiundvierzig, und das ist {@code Wolkengrenze
     * .wolkenwerder().inselnZahl()}: 38 Felder je Achse, das Ankunftsfeld abgezogen. Eine
     * Zahl darueber waere eine Obergrenze, die nie greift - und eine Einstellung, die nie
     * wirkt, liest der Betreiber als eine, die kaputt ist.
     *
     * <p>Als Zahl und nicht als Aufruf: {@link Wolkengrenze} haengt an der Weltkante aus
     * der Datei, und die kann der Betreiber vergroessern. Eine Obergrenze, die mit der
     * Weltkante waechst, waere keine. Die Probe haelt beide gegeneinander.
     */
    public static final int HOECHSTE_ZAHL = 1443;

    /**
     * Die hoechste Anzahl eines Gegenstands in der Starttruhe - ein voller Stapel.
     *
     * <p>Mehr passt in keinen Platz, und eine 65 waere ein Stapel, den Minecraft beim
     * ersten Anfassen teilt. Gegen den Vertipper, nicht gegen den Betreiber: Wer eine
     * Insel reicher ausstatten will, nimmt mehr ZEILEN.
     */
    public static final int HOECHSTE_ANZAHL = 64;

    /**
     * Was ab Werk in der Starttruhe liegt - der Anfang des ganzen Spiels.
     *
     * <p>Jede Zeile ist ein Weg, der ohne sie fuer immer zu waere. In einer Welt aus
     * Leere gibt es nichts, was nicht aus dieser Truhe oder dem Kobbelsteingenerator
     * kommt:
     *
     * <ul>
     *   <li><b>lava_bucket</b> - das Herz des Generators. Ohne Lava kein Kobbelstein,
     *       ohne Kobbelstein kein Stein, kein Ofen, keine Spitzhacke.
     *   <li><b>ice</b> - Wasser. Es schmilzt im Tageslicht, und erst daraus und der Lava
     *       entsteht der Generator. Zwei Stuecke, damit ein Fehlgriff nicht das Spiel
     *       beendet.
     *   <li><b>bone_meal</b> - damit wachsen Gras und Blumen auf der Wiese, und aus dem
     *       Gras kommen die ersten Samen. Ohne Knochenmehl gibt es kein Brot.
     *   <li><b>melon_slice, pumpkin_seeds, sugar_cane, brown_mushroom, red_mushroom,
     *       cactus</b> - Pflanzen, die nirgendwo sonst zu bekommen sind.
     *   <li><b>sand</b> - zwei Bloecke, und sie sind der Boden fuer den Kaktus. Sand
     *       entsteht in dieser Welt sonst nicht.
     * </ul>
     *
     * <p>Das ist die Stelle, an der der Betreiber den Schwierigkeitsgrad einstellt - eine
     * Zeile weniger ist eine Aufgabe mehr.
     */
    public static final Map<String, Integer> VORGABE_STARTTRUHE = Map.of(
            "lava_bucket", 1,
            "ice", 2,
            "bone_meal", 5,
            "melon_slice", 1,
            "pumpkin_seeds", 1,
            "sugar_cane", 1,
            "brown_mushroom", 1,
            "red_mushroom", 1,
            "cactus", 1,
            "sand", 2);

    /** Die Werte, die das Programm mitbringt. */
    public static final Wolkenwerte VORGABE = new Wolkenwerte(VORGABE_PREIS,
            VORGABE_HOECHSTZAHL, VORGABE_BIOME, VORGABE_STARTTRUHE);

    public Wolkenwerte {
        if (preis < 0L || preis > HOECHSTER_BETRAG) {
            throw new IllegalArgumentException("Der Preis einer Wolkeninsel ist " + preis
                    + " und liegt nicht zwischen 0 und " + HOECHSTER_BETRAG
                    + ". Eine 0 ist der ausgelieferte Wert und heisst: gratis.");
        }
        if (hoechstzahl < 0 || hoechstzahl > HOECHSTE_ZAHL) {
            throw new IllegalArgumentException("Die Hoechstzahl der Wolkeninseln ist "
                    + hoechstzahl + " und liegt nicht zwischen 0 und " + HOECHSTE_ZAHL
                    + ". Eine 0 ist erlaubt und heisst: Es wird keine Insel vergeben.");
        }
        if (biome == null) {
            throw new IllegalArgumentException("Ohne Biomliste gibt es keine Wolkenwerte."
                    + " Eine LEERE Liste ist erlaubt und heisst: keine Biomwahl.");
        }
        // Jede Zeile einzeln geprueft, wie bei der Angeltabelle: Was hier durchkaeme,
        // waere ein Biom, das der Befehl anbietet und das Plugin nicht setzen kann.
        var sauber = new LinkedHashSet<String>();
        for (String name : biome) {
            if (!Wolkenbiome.gibtEs(name)) {
                throw new IllegalArgumentException("Das Biom " + name + " gibt es nicht."
                        + " Erlaubt sind " + String.join(", ", Wolkenbiome.NAMEN) + ".");
            }
            if (!sauber.add(Wolkenbiome.wieAbgelegt(name))) {
                throw new IllegalArgumentException("Das Biom " + name + " steht zweimal in"
                        + " der Liste. Ein Biom zweimal anzubieten macht es nicht"
                        + " waehlbarer, aber die Auswahl laenger.");
            }
        }
        biome = List.copyOf(sauber);
        if (starttruhe == null) {
            throw new IllegalArgumentException("Ohne Starttruhe gibt es keine"
                    + " Wolkenwerte. Eine LEERE Truhe ist erlaubt und heisst: Der"
                    + " Spieler faengt mit nichts an.");
        }
        // Zeile fuer Zeile geprueft, wie bei der Angeltabelle. Der NAME wird hier NICHT
        // gegen Minecraft geprueft - das kann der Kern nicht, sein Gegenstandsverzeichnis
        // braucht einen laufenden Server. Das tut der Leser (Wolkenliste) an derselben
        // Stelle, an der er jeden anderen Gegenstandsnamen prueft.
        var abgelegt = new LinkedHashMap<String, Integer>();
        for (var zeile : starttruhe.entrySet()) {
            if (zeile.getKey() == null || zeile.getKey().isBlank()) {
                throw new IllegalArgumentException("Ein Gegenstand ohne Namen passt in"
                        + " keine Truhe.");
            }
            if (zeile.getValue() == null || zeile.getValue() < 1
                    || zeile.getValue() > HOECHSTE_ANZAHL) {
                throw new IllegalArgumentException("Die Anzahl von " + zeile.getKey()
                        + " ist " + zeile.getValue() + " und liegt nicht zwischen 1 und "
                        + HOECHSTE_ANZAHL + ". Wer einen Gegenstand nicht mitgeben will,"
                        + " nimmt die Zeile heraus - eine 0 waere ein Platz, auf dem"
                        + " nichts liegt.");
            }
            abgelegt.put(zeile.getKey().trim().toLowerCase(Locale.ROOT),
                    zeile.getValue());
        }
        starttruhe = Map.copyOf(abgelegt);
    }

    /** Ob dieses Biom angeboten wird - gefragt wird kleingeschrieben. */
    public boolean bietetBiom(String name) {
        return biome.contains(Wolkenbiome.wieAbgelegt(name));
    }

    /** Ob es ueberhaupt etwas zu waehlen gibt. */
    public boolean biomwahl() {
        return !biome.isEmpty();
    }

    /** Dieselben Werte mit einem anderen Preis. */
    public Wolkenwerte mitPreis(long anderer) {
        return new Wolkenwerte(anderer, hoechstzahl, biome, starttruhe);
    }

    /** Dieselben Werte mit einer anderen Obergrenze. */
    public Wolkenwerte mitHoechstzahl(int andere) {
        return new Wolkenwerte(preis, andere, biome, starttruhe);
    }

    /** Dieselben Werte mit anderen angebotenen Biomen. */
    public Wolkenwerte mitBiomen(List<String> andere) {
        return new Wolkenwerte(preis, hoechstzahl, andere, starttruhe);
    }

    /** Dieselben Werte mit einer anderen Starttruhe. */
    public Wolkenwerte mitStarttruhe(Map<String, Integer> andere) {
        return new Wolkenwerte(preis, hoechstzahl, biome, andere);
    }
}
