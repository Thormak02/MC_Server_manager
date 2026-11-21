package de.freimarken.paper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

/**
 * Die Farbwelt: benannte ROLLEN statt Farbnamen - und die eine Stelle, an der aus einer
 * Zahl der Datei eine Farbe wird.
 *
 * <p><b>Warum Rollen und nicht die vier Markenfarben als Tags:</b> Plan 2.2 nennt vier
 * Farben und je Farbe eine Verwendung - Bundesrot fuer "Offizielles, Warnungen",
 * Markgold fuer "Waehrung, Preise", Forstgruen fuer "Bestaetigungen", Schiefer fuer
 * "Grundton, Fliesstext". Was ein Satz braucht, ist die VERWENDUNG: Eine Absage will
 * "warnung" heissen und nicht "bundesrot". Ein Satz mit {@code <warnung>} uebersteht
 * einen Palettenwechsel; einer mit {@code <bundesrot>} behauptet einen Wert und muesste
 * bei jeder Umfaerbung in achthundert Zeilen nachgezogen werden.
 *
 * <p><b>Warum die Werte nicht die Planwerte sind - jedenfalls nicht alle:</b> Die vier
 * Markenfarben sind fuer FLAECHEN gemacht (Schilder, Menuehintergruende, Bauwerke). Der
 * Chat hat einen dunklen Grund, und drei der vier Werte sind dort zu dunkel zum Lesen:
 * Schiefer {@code #333B40} als Fliesstext waere fast unsichtbar, Bundesrot
 * {@code #8C3431} und Forstgruen {@code #395948} dunkel und muede. Darum tragen die
 * Textrollen AUFGEHELLTE Werte derselben Farbtoene - gleicher Farbton, hoeherer Hellwert
 * -, und die reinen Markenwerte bleiben den Flaechen. Markgold ist hell genug und steht
 * unveraendert.
 *
 * <p><b>Alles davon ist einstellbar</b>, und zwar an EINER Stelle je Rolle: im Block
 * {@code farben:} der Sprachdatei, dort wo auch die Worte stehen. Der Betreiber, der sein
 * Rot dunkler will, aendert eine Zeile und nicht zweihundert Saetze.
 *
 * <p><b>Warum die Werte in der SPRACHDATEI stehen und nicht in der config.yml:</b> Es ist
 * Aussehen, und das Aussehen hat in diesem Plugin eine Datei, einen Leser, einen Halter
 * und einen gepruefte Neuladeweg - alles oder nichts. Ein zweiter Ort fuer dasselbe
 * waere ein zweiter Leseweg, ein zweiter Bericht und eine zweite Gelegenheit, die
 * Reihenfolge zu vergessen (die Farben muessen gelesen sein, bevor der erste Satz auf
 * seine Tags geprueft wird).
 */
final class Farbtafel {

    /** Gewoehnlicher Text - der Grundton jeder Zeile. Ersetzt das alte {@code <gray>}. */
    static final String FLIESSTEXT = "fliesstext";

    /**
     * Ein Name, eine Zahl, ein Schluesselwort INNERHALB einer Zeile. Alt: {@code <white>}.
     *
     * <p><b>Hiess bis zur ersten Probe "name"</b>, und das war ein Fehler mit Zeugen:
     * {@code <name>} ist in diesem Projekt die Schreibweise fuer einen ANGABEPLATZ in
     * einem Befehlshinweis ("/laden mieten \<name>"), fuenfmal in den Saetzen und
     * mehrfach im Quelltext. Tafel 31t hat es sofort gemeldet - ein Rollenname darf
     * nicht aussehen wie ein Platzhalter.
     */
    static final String BETONT = "betont";

    /** Die Ueberschrift oder Ansage, die obenan steht. Alt: {@code <gold>}. */
    static final String KOPF = "kopf";

    /** Ein Geldbetrag - Mark, Goldmark, Preise. Alt: {@code <gold>}. */
    static final String GELD = "geld";

    /** Absage, Verbot, Stoerung, Gefahr. Alt: {@code <red>}. */
    static final String WARNUNG = "warnung";

    /** Es ist geglueckt. Alt: {@code <green>}. */
    static final String BESTAETIGUNG = "bestaetigung";

    /** Ein Hinweis, eine Werbung, eine Rueckfrage. Alt: {@code <yellow>}. */
    static final String HINWEIS = "hinweis";

    /** Fusszeile, Nebensatz, der Weg zum naechsten Befehl. Alt: {@code <dark_gray>}. */
    static final String GEDAEMPFT = "gedaempft";

    /**
     * Die Rollen in der Reihenfolge, in der sie in der Datei stehen.
     *
     * <p>Die EINE Liste davon: Der Leser prueft gegen sie, die Vorgabe wird aus ihr
     * gebaut, der Vorrat traegt genau diese Namen, und die Tafel in {@code GrenzeTest}
     * haelt sie gegen die Tags, die in den Saetzen wirklich vorkommen.
     */
    static final List<String> ROLLEN = List.of(FLIESSTEXT, BETONT, KOPF, GELD, WARNUNG,
            BESTAETIGUNG, HINWEIS, GEDAEMPFT);

    /** Der Schluessel des Blocks in der Sprachdatei. */
    static final String PFAD = "farben";

    /** Die groesste Zahl, die eine Farbe sein kann - drei Bytes. */
    static final int HOECHSTENS = 0xffffff;

    /**
     * Die ausgelieferte Palette.
     *
     * <p>Je Rolle der Wert und seine Herkunft:
     * <ul>
     *   <li>{@code fliesstext #a5b4bf} - Schiefer {@code #333B40}, aufgehellt. Derselbe
     *       kuehle Grauton, aber lesbar auf dunklem Grund.</li>
     *   <li>{@code betont #edf1f3} - Schiefer, fast weiss. Hebt EIN Wort heraus, ohne eine
     *       zweite Farbe in die Zeile zu bringen.</li>
     *   <li>{@code kopf #c6a15b} - Markgold, unveraendert.</li>
     *   <li>{@code geld #c6a15b} - Markgold, unveraendert. Ab Werk derselbe Wert wie
     *       {@code kopf}, aber eine eigene Rolle: Wer seine Ueberschriften schiefern
     *       will, aendert nur {@code kopf}.</li>
     *   <li>{@code warnung #d9564d} - Bundesrot {@code #8C3431}, aufgehellt.</li>
     *   <li>{@code bestaetigung #73bf96} - Forstgruen {@code #395948}, aufgehellt.</li>
     *   <li>{@code hinweis #e3c57e} - Markgold, aufgehellt. Steht neben {@code geld} und
     *       darf nicht damit verwechselt werden: Ein Hinweis ist kein Betrag.</li>
     *   <li>{@code gedaempft #6e7a82} - Schiefer, mittel. Leiser als der Fliesstext und
     *       noch lesbar; das alte {@code <dark_gray>} war an der Grenze.</li>
     * </ul>
     */
    static final Farbtafel VORGABE = new Farbtafel(vorgabewerte());

    private final Map<String, Integer> rollen;
    private final MiniMessage leser;

    /**
     * @param rollen je Rolle ihr Wert als {@code 0xRRGGBB}; VOLLSTAENDIG, nie eine
     *               Teilmenge. Was der Betreiber nicht gesetzt hat, traegt der Leser aus
     *               {@link #VORGABE} nach - so kann keine Rolle fehlen und kein Satz
     *               ohne Farbe dastehen
     */
    Farbtafel(Map<String, Integer> rollen) {
        if (rollen == null) {
            throw new IllegalArgumentException("Ohne Werte gibt es keine Farbtafel");
        }
        Map<String, Integer> eigene = new LinkedHashMap<>();
        for (String rolle : ROLLEN) {
            Integer wert = rollen.get(rolle);
            if (wert == null) {
                throw new IllegalArgumentException("Die Farbtafel ist unvollstaendig: "
                        + rolle + " fehlt. Der Leser traegt Fehlendes nach; eine halbe"
                        + " Tafel hier waere ein Satz ohne Farbe im Chat.");
            }
            if (wert < 0 || wert > HOECHSTENS) {
                throw new IllegalArgumentException(rolle + " ist keine Farbe: " + wert);
            }
            eigene.put(rolle, wert);
        }
        if (eigene.size() != rollen.size()) {
            throw new IllegalArgumentException("Die Farbtafel kennt eine Rolle nicht: "
                    + rollen.keySet() + " gegen " + ROLLEN);
        }
        this.rollen = Map.copyOf(eigene);
        this.leser = MiniMessage.builder().strict(true).tags(vorrat(this.rollen)).build();
    }

    private static Map<String, Integer> vorgabewerte() {
        Map<String, Integer> werte = new LinkedHashMap<>();
        werte.put(FLIESSTEXT, 0xa5b4bf);
        werte.put(BETONT, 0xedf1f3);
        werte.put(KOPF, 0xc6a15b);
        werte.put(GELD, 0xc6a15b);
        werte.put(WARNUNG, 0xd9564d);
        werte.put(BESTAETIGUNG, 0x73bf96);
        werte.put(HINWEIS, 0xe3c57e);
        werte.put(GEDAEMPFT, 0x6e7a82);
        return werte;
    }

    /**
     * Der Wortvorrat zu diesen Werten - die Standardtags und die acht Rollen.
     *
     * <p>Die Rollen stehen NACH den Standardtags im Bauer: Ein eigener Tag gewinnt damit
     * gegen einen gleichnamigen Standardtag. Keiner der acht Namen ist heute ein
     * Minecraftname - aber {@code name} ist kurz und allgemein, und eine kuenftige
     * Adventure-Fassung soll ihn nicht stillschweigend uebernehmen koennen.
     */
    static TagResolver vorrat(Map<String, Integer> werte) {
        TagResolver.Builder bauer = TagResolver.builder()
                .resolver(StandardTags.color())
                .resolver(StandardTags.decorations());
        for (String rolle : ROLLEN) {
            bauer.tag(rolle, Tag.styling(TextColor.color(werte.get(rolle))));
        }
        return bauer.build();
    }

    /**
     * Der Leser dieser Palette - einmal gebaut und unveraenderlich.
     *
     * <p>Hier, nicht in {@code Sprache}: Der Leser traegt die WERTE, und die sind
     * neuladbar. {@code Sprache} fragt ihn beim Gebrauch am Halter - dieselbe Regel wie
     * fuer jeden Wortlaut. Ein {@code static final} Leser koennte die Farben der Datei
     * gar nicht kennen.
     */
    MiniMessage leser() {
        return leser;
    }

    /** Der Wert einer Rolle als {@code 0xRRGGBB}. */
    int wert(String rolle) {
        Integer wert = rollen.get(rolle);
        if (wert == null) {
            throw new IllegalArgumentException(rolle + " ist keine Rolle der Farbwelt."
                    + " Es gibt " + ROLLEN);
        }
        return wert;
    }

    /** Alle Werte - fuer den Bericht und die Proben. */
    Map<String, Integer> rollen() {
        return rollen;
    }

    /** Der Wert einer Rolle, wie er in der Datei steht: {@code #rrggbb}, kleingeschrieben. */
    static String wieInDerDatei(int wert) {
        return String.format(Locale.ROOT, "#%06x", wert);
    }

    /**
     * Welche Rollen sich gegenueber einer anderen Tafel unterscheiden - in der
     * Reihenfolge von {@link #ROLLEN}.
     *
     * <p>Fuer die Antwort auf {@code /freimarken neuladen}: Der Betreiber hat EINE Farbe
     * geaendert und will ihren Namen lesen. "8 von 8 Zeilen (geaendert)" waere wahr und
     * nutzlos - die Laenge dieser Tafel ist immer dieselbe.
     */
    List<String> unterschiede(Farbtafel andere) {
        if (andere == null) {
            return ROLLEN;
        }
        List<String> anders = new ArrayList<>();
        for (String rolle : ROLLEN) {
            if (wert(rolle) != andere.wert(rolle)) {
                anders.add(rolle + ": " + wieInDerDatei(andere.wert(rolle)) + " -> "
                        + wieInDerDatei(wert(rolle)));
            }
        }
        return List.copyOf(anders);
    }

    @Override
    public boolean equals(Object andere) {
        return andere instanceof Farbtafel tafel && rollen.equals(tafel.rollen);
    }

    @Override
    public int hashCode() {
        return rollen.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("Farbtafel[");
        for (String rolle : ROLLEN) {
            if (text.length() > "Farbtafel[".length()) {
                text.append(", ");
            }
            text.append(rolle).append('=').append(wieInDerDatei(wert(rolle)));
        }
        return text.append(']').toString();
    }
}
