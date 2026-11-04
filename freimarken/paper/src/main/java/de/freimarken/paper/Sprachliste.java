package de.freimarken.paper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.ParsingException;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

/**
 * Liest die Saetze aus der {@code sprache.yml} - der eine Leser.
 *
 * <p>Nach demselben Muster wie {@link Wirtschaftsliste}: reine Dateiarbeit, deshalb ohne
 * Server pruefbar, und mit gesammelten KLAGEN statt Ausnahmen. Dieselbe Trennung gilt und
 * sie entscheidet hier noch mehr als dort:
 * <ul>
 *   <li>KLAGE heisst: Was du getippt hast, gilt NICHT - und zwar gar nichts davon. Es
 *       bleiben die Saetze, die bisher galten. Eine Klage ist ein Mangel, den der
 *       Betreiber durch TIPPEN verursacht hat.
 *   <li>BERICHT heisst: Es ist nichts dagegen zu sagen, aber du sollst es gelesen haben.
 *       Ein Unterschied, den ein UPDATE verursacht hat, ist immer ein Bericht - und der
 *       Rueckfall gilt dann JE SCHLUESSEL und nicht fuer die ganze Datei. Sonst entwertet
 *       ein Update, bei dem der Betreiber nichts angefasst hat, seine ganze Arbeit.
 * </ul>
 *
 * <p><b>Gelesen wird SELBST und nicht ueber {@code ConfigurationSection}, und das ist eine
 * Abweichung vom Vorbild mit gemessenem Grund.</b> Ein {@code ConfigurationSection} kennt
 * keine Zeilennummern und hat die Dublette schon verschluckt, bevor man sie sehen koennte:
 * Gemessen an SnakeYAML 2.2 (die Fassung aus dem Gradle-Cache, also die, die Bukkit
 * benutzt) laesst ein zweimal getipptes {@code parzelle:} mit verschiedenen Kindern NUR
 * den zweiten Block uebrig - der erste verschwindet ohne eine Meldung, und kein Blattpfad
 * ist dabei doppelt. Der Betreiber haette gelesen "1 von 231 Saetzen kommt aus deiner
 * Datei", obwohl zwei darin stehen. Darum liest dieser Leser mit
 * {@code new Yaml().compose(reader)}: Das gibt einen Knotenbaum, in dem JEDER Schluessel
 * seine Zeile traegt und in dem eine Dublette auf JEDER Ebene noch als zwei Eintraege
 * nebeneinander steht. Ein Durchlauf liefert damit beides - Pfad auf Wert mit Zeile, und
 * jede Dublette mit BEIDEN Zeilennummern.
 *
 * <p>{@code org.yaml.snakeyaml} ist KEINE neue Abhaengigkeit: Das POM von
 * {@code paper-api} fuehrt {@code org.yaml:snakeyaml:2.2} mit {@code scope compile}, die
 * Jar liegt im Cache, und keine Gradle-Zeile aendert sich. Die eine Unsicherheit, die
 * keine Probe ausraeumen kann, ist Papers Klassenlader zur Laufzeit - dagegen steht die
 * Wache in {@code FreimarkenPlugin.lieseSprache}, die mit {@link #nichtsGelesen} auf die
 * ausgelieferten Saetze zurueckfaellt und den Grund nennt.
 */
final class Sprachliste {

    /** Die Datei des Betreibers - absichtlich leer ausgeliefert. */
    static final String DATEI = "sprache.yml";

    /** Die Abschrift aus der Jar - wird bei JEDEM Start neu geschrieben. */
    static final String VORLAGE = "sprache-vorlage.yml";

    /** Ein Wert in geschweiften Klammern: {@code {name}}, klein, mit Bindestrichen. */
    static final Pattern MARKE = Pattern.compile("[{]([a-z][a-z0-9-]*)[}]");

    /** Jede geschweifte Klammer ueberhaupt - fuer den Fall ohne Gegenstueck. */
    private static final Pattern KLAMMER = Pattern.compile("[{}]");

    /** Der Trenner, den die heutigen Saetze 18-mal benutzen: U+00B7. */
    private static final char TRENNER = (char) 0xb7;

    /**
     * Was in der Datei stand - und woran es gehapert hat.
     *
     * <p>{@code buch()} ist NULL, wenn {@code klagen()} nicht leer ist. Damit kann die
     * Verdrahtung nicht stumm falsch sein: {@code Sprachstand.uebernimm(null)} wirft.
     */
    record Ergebnis(Sprachbuch buch, List<String> klagen, List<String> bericht) {
    }

    /** Ein Blatt des Knotenbaums: Pfad, Wert und die Zeile, in der der Schluessel steht. */
    private record Fund(String pfad, String wert, int zeile, String tag) {
    }

    private Sprachliste() {
    }

    // --- Der Weg aus der Jar ---------------------------------------------------

    /**
     * Der ausgelieferte Wortlaut - wirft, wenn die Ressource fehlt oder unvollstaendig ist.
     *
     * <p>Die eine Stelle, an der nicht geklagt, sondern geworfen wird. Ohne die Vorlage
     * gibt es keinen einzigen Satz, und ein Plugin, das stumm in den Chat sendet, ist
     * schlimmer als eines, das nicht laeuft. Das ist ein Bauunfall und kein Betriebsfall -
     * dagegen steht {@code VorlageTest}, die beim BAUEN rot wird.
     */
    static Sprachbuch ausDemJar() {
        String text = ressource(VORLAGE);
        Node wurzel;
        try {
            wurzel = new Yaml().compose(new StringReader(text));
        } catch (RuntimeException kaputt) {
            throw new IllegalStateException(VORLAGE + " in der Jar ist kein brauchbares"
                    + " YAML: " + einzeilig(kaputt.getMessage()), kaputt);
        }
        List<String> maengel = new ArrayList<>(dublettenklagen(wurzel));
        Map<String, Fund> gefunden = new LinkedHashMap<>();
        blaetter("", wurzel, gefunden, maengel);

        Map<String, String> saetze = new LinkedHashMap<>();
        Map<String, DateTimeFormatter> muster = new LinkedHashMap<>();
        for (Satz satz : Sprachschluessel.alle()) {
            for (String pfad : satz.pfade()) {
                Fund fund = gefunden.get(pfad);
                if (fund == null) {
                    maengel.add(pfad + " fehlt in " + VORLAGE + " - jeder Satz, den das"
                            + " Plugin kennt, muss dort stehen.");
                    continue;
                }
                maengel.addAll(maengelAmWert(satz, pfad, fund));
                saetze.put(pfad, fund.wert());
                if (satz.art() == Satz.Art.MUSTER) {
                    DateTimeFormatter form = zeitform(fund.wert());
                    if (form == null) {
                        maengel.add(pfad + " in " + VORLAGE + " ist kein brauchbares"
                                + " Zeitmuster: \"" + fund.wert() + "\"");
                    } else {
                        muster.put(satz.pfad(), form);
                    }
                }
            }
        }
        for (String pfad : gefunden.keySet()) {
            if (Sprachschluessel.zuPfad(pfad) == null) {
                maengel.add(VORLAGE + ", Zeile " + gefunden.get(pfad).zeile() + ": " + pfad
                        + " gehoert zu keinem Satz dieses Plugins - in der ausgelieferten"
                        + " Datei ist das ein Tippfehler beim Abschreiben.");
            }
        }
        if (!maengel.isEmpty()) {
            throw new IllegalStateException("Die ausgelieferte " + VORLAGE + " ist"
                    + " unvollstaendig oder falsch (" + maengel.size() + "): "
                    + String.join(" | ", maengel));
        }
        return new Sprachbuch(saetze, Set.of(), muster);
    }

    /** Der Text einer Ressource aus der Jar - UTF-8 mit Wurf, wie bei der Datei. */
    static String ressource(String name) {
        try (InputStream strom = Sprachliste.class.getResourceAsStream("/" + name)) {
            if (strom == null) {
                throw new IllegalStateException(name + " liegt nicht in der Jar. Ohne"
                        + " diese Ressource gibt es keinen einzigen Satz.");
            }
            return streng(strom.readAllBytes());
        } catch (IOException fehler) {
            throw new IllegalStateException(name + " in der Jar ist nicht lesbar: "
                    + fehler, fehler);
        }
    }

    // --- Der Weg aus der Datei des Betreibers ---------------------------------

    /**
     * Ob die Datei ueberhaupt lesbar ist - {@code null} heisst lesbar.
     *
     * <p>Form von {@code Wirtschaftsliste.lesefehler}, aber mit einem anderen Ausgang:
     * Dort bleibt der Start aus, hier laeuft der Server weiter. Dort geht es um Geld - "zu
     * billig verkaufte Flaeche liegt binnen Minuten als Mark auf vielen Konten". Ein
     * heimlich ausgeliefertes Wort kostet ein Wort.
     */
    static String lesefehler(File ordner) {
        File datei = new File(ordner, DATEI);
        if (!datei.isFile()) {
            return DATEI + " liegt nicht in " + ordner.getPath() + " - es gelten die"
                    + " ausgelieferten Saetze. Sie entsteht beim naechsten Start neu;"
                    + " " + VORLAGE + " zeigt, was darin stehen kann.";
        }
        return null;
    }

    /**
     * Die Datei lesen - oder klagen.
     *
     * @param vorlage das ausgelieferte Buch; es gilt fuer jeden Satz, den die Datei nicht
     *                aendert, und darum kann kein Satz verstummen
     */
    static Ergebnis ausDatei(File ordner, Sprachbuch vorlage) {
        File datei = new File(ordner, DATEI);
        if (!datei.isFile()) {
            List<String> bericht = new ArrayList<>();
            bericht.add("Sprache: " + DATEI + " gibt es nicht - alle "
                    + vorlage.saetze().size() + " Saetze kommen aus dem Plugin.");
            return new Ergebnis(vorlage, List.of(), bericht);
        }
        String text;
        try {
            text = streng(Files.readAllBytes(datei.toPath()));
        } catch (CharacterCodingException keinUtf8) {
            return nichtsGelesen(DATEI + " ist nicht UTF-8 kodiert"
                    + wohl(datei) + ". Ein Editor, der als Windows-1252 speichert, macht"
                    + " aus jedem Sonderzeichen ein Fragezeichen beim Spieler - darum wird"
                    + " hier abgewiesen und nicht ersetzt. Speichere die Datei als UTF-8.");
        } catch (IOException nichtLesbar) {
            return nichtsGelesen(DATEI + " ist nicht lesbar: " + nichtLesbar
                    + " - solange das so ist, kennt der Server deine Saetze nicht.");
        }
        return ausQuelle(text, vorlage);
    }

    /** Der pruefbare Kern - ohne Datei, ohne Bukkit, ohne Server: nur Text hinein. */
    static Ergebnis ausQuelle(String text, Sprachbuch vorlage) {
        return ausQuelle(text, vorlage, Sprachschluessel.alle());
    }

    /**
     * Derselbe Kern mit einer eigenen Schluesselliste - fuer die Proben.
     *
     * <p>Nur so sind die Fehlerfaelle schon heute rot zu bekommen, deren Schluessel erst
     * ein spaeterer Bauschritt anlegt: ein {@code Art.HANDBALKEN} mit Umbruchmarke, eine
     * fehlende {@code .viele}-Form, ein fremder Platzhaltername, den ein ANDERER Satz
     * kennt. Ohne diese Naht waere die Pruefmaschine bis zum letzten Bauschritt
     * ungeprueft - und eine gruene Probe, die nichts geprueft hat, ist schlimmer als eine
     * rote.
     */
    static Ergebnis ausQuelle(String text, Sprachbuch vorlage, List<Satz> schluessel) {
        List<String> klagen = new ArrayList<>();
        List<String> bericht = new ArrayList<>();
        Node wurzel;
        try {
            wurzel = new Yaml().compose(new StringReader(text));
        } catch (MarkedYAMLException kaputt) {
            // SnakeYAML nennt die Quelle 'reader' - wer das liest, sucht nach einer Datei
            // dieses Namens. Die Zeilennummer darin ist das Brauchbare an der Meldung.
            return nichtsGelesen(DATEI + " ist kein brauchbares YAML: "
                    + einzeilig(kaputt.getMessage()).replace("'reader'", DATEI));
        } catch (RuntimeException kaputt) {
            return nichtsGelesen(DATEI + " ist kein brauchbares YAML: "
                    + einzeilig(String.valueOf(kaputt.getMessage())));
        }
        if (wurzel == null) {
            bericht.add("Sprache: " + DATEI + " ist leer - alle "
                    + vorlage.saetze().size() + " Saetze kommen aus dem Plugin.");
            return new Ergebnis(vorlage, List.of(), bericht);
        }

        klagen.addAll(dublettenklagen(wurzel));
        Map<String, Fund> gefunden = new LinkedHashMap<>();
        blaetter("", wurzel, gefunden, klagen);

        Map<String, Satz> verzeichnis = Sprachschluessel.verzeichnisVon(schluessel);
        Map<String, String> eigene = new LinkedHashMap<>();
        Set<String> zurueckgefallen = new LinkedHashSet<>();

        for (Map.Entry<String, Fund> eintrag : gefunden.entrySet()) {
            String pfad = eintrag.getKey();
            Fund fund = eintrag.getValue();
            Satz satz = verzeichnis.get(pfad);
            if (satz == null) {
                String aehnlich = Sprachschluessel.aehnlichster(pfad);
                bericht.add(DATEI + ", Zeile " + fund.zeile() + ": " + pfad + " gehoert zu"
                        + " keinem Satz dieses Plugins - diese Zeile wirkt nicht."
                        + (aehnlich == null ? "" : " Meintest du " + aehnlich + "?"));
                continue;
            }
            List<String> maengel = maengelAmWert(satz, pfad, fund);
            if (!maengel.isEmpty()) {
                klagen.addAll(maengel);
                continue;
            }
            if (satz.art() == Satz.Art.MUSTER && zeitform(fund.wert()) == null) {
                klagen.add(DATEI + ", Zeile " + fund.zeile() + ", " + pfad + ": \""
                        + fund.wert() + "\" ist kein brauchbares Zeitmuster. Erlaubt ist,"
                        + " was DateTimeFormatter kennt - zum Beispiel dd.MM. HH:mm.");
                continue;
            }
            // Obermenge: ein Name, den DIESER Satz nicht kennt. IMMER Rueckfall je
            // Schluessel und niemals eine Klage - sonst entwertet ein Update, das einem
            // Satz einen Platzhalter nimmt, die ganze Datei des Betreibers, obwohl er
            // nichts angefasst hat. Der Unterscheider "Tippfehler oder Rest aus der
            // vorigen Fassung" ist ausserdem nicht herstellbar.
            Set<String> fremd = fremdeNamen(satz, fund.wert());
            if (!fremd.isEmpty()) {
                zurueckgefallen.add(pfad);
                bericht.add(DATEI + ", Zeile " + fund.zeile() + ", " + pfad + ": dein Satz"
                        + " nennt " + marken(fremd) + ", den es in diesem Satz nicht gibt"
                        + " - fuer diesen EINEN Satz gilt wieder der ausgelieferte. Alles"
                        + " andere wurde uebernommen. Erlaubt sind hier: "
                        + (satz.platzhalter().isEmpty() ? "keine Werte"
                                : marken(new TreeSet<>(satz.platzhalter()))) + ".");
                continue;
            }
            Set<String> fehlend = fehlendeNamen(satz, fund.wert());
            if (!fehlend.isEmpty()) {
                bericht.add(DATEI + ", Zeile " + fund.zeile() + ", " + pfad + ": dein Satz"
                        + " nennt " + marken(fehlend) + " nicht - das Plugin bietet diesen"
                        + " Wert an. " + VORLAGE + " zeigt, wie er gemeint ist. Deine"
                        + " Zeile gilt trotzdem.");
            }
            eigene.put(pfad, fund.wert());
        }

        // Beide Formen oder keine: Welche Saetze zahlabhaengig sind, entscheidet der
        // Vertrag und nicht die Datei. Eine halbe Mehrzahl waere der Fall, in dem genau
        // eine von zwei Chatzeilen anders klingt als die andere.
        for (Satz satz : schluessel) {
            if (satz.art() != Satz.Art.GEZAEHLT) {
                continue;
            }
            boolean eins = eigene.containsKey(satz.pfad() + ".eins");
            boolean viele = eigene.containsKey(satz.pfad() + ".viele");
            if (eins != viele) {
                klagen.add(DATEI + ": " + satz.pfad() + " hat nur die Form "
                        + (eins ? "eins" : "viele") + ". Ein gezaehlter Satz braucht BEIDE"
                        + " - sonst klingt genau eine von zwei Chatzeilen anders als die"
                        + " andere, und zwar die, die seltener vorkommt.");
            }
        }

        if (!klagen.isEmpty()) {
            return new Ergebnis(null, List.copyOf(klagen), List.copyOf(bericht));
        }

        Map<String, String> saetze = new LinkedHashMap<>(vorlage.saetze());
        saetze.putAll(eigene);
        Map<String, DateTimeFormatter> muster = new LinkedHashMap<>(vorlage.muster());
        for (Satz satz : schluessel) {
            if (satz.art() == Satz.Art.MUSTER && eigene.containsKey(satz.pfad())) {
                muster.put(satz.pfad(), zeitform(eigene.get(satz.pfad())));
            }
        }
        bericht.add(0, "Sprache: " + eigene.size() + " von " + saetze.size()
                + " Saetzen kommen aus deiner " + DATEI + ", "
                + (saetze.size() - eigene.size()) + " aus dem Plugin."
                + (zurueckgefallen.isEmpty() ? ""
                        : " " + zurueckgefallen.size() + " Zeile(n) sind dabei"
                                + " zurueckgefallen: " + String.join(", ", zurueckgefallen)));
        if (!eigene.isEmpty()) {
            bericht.add(1, "  deine: " + String.join(", ", eigene.keySet()));
        }
        return new Ergebnis(new Sprachbuch(saetze, eigene.keySet(), muster),
                List.of(), List.copyOf(bericht));
    }

    /**
     * Ein Ergebnis mit genau einer Klage und ohne Buch.
     *
     * <p>Damit geht jeder Grund denselben Weg: {@code buch()} ist {@code null}, also kann
     * {@code uebernimm} nie mit {@code null} gerufen werden, ohne zu werfen.
     */
    static Ergebnis nichtsGelesen(String klage) {
        return new Ergebnis(null, List.of(klage), List.of());
    }

    // --- Die Pruefstuecke, einzeln pruefbar ------------------------------------

    /** Alle Maengel an EINEM Wert, die eine Klage sind - leer, wenn er heil ist. */
    private static List<String> maengelAmWert(Satz satz, String pfad, Fund fund) {
        List<String> maengel = new ArrayList<>();
        if (fund.wert() == null) {
            maengel.add(ort(pfad, fund.zeile()) + " ist kein Text (" + fund.tag() + ")."
                    + " Erwartet wird ein Satz in doppelten Anfuehrungszeichen; eine"
                    + " Liste oder ein weiterer Block an dieser Stelle kommt beim Spieler"
                    + " als Programmgekritzel an.");
            return maengel;
        }
        if (fund.wert().isBlank() && !satz.darfLeerSein()) {
            maengel.add(ort(pfad, fund.zeile()) + " ist leer. Ein leerer Satz sendet eine"
                    + " leere Chatzeile - der Spieler sieht, dass etwas geschieht, und"
                    + " nicht was. Soll nichts stehen, bleibt die Zeile ganz weg; dann"
                    + " gilt der ausgelieferte Satz.");
        }
        String zeichen = zeichenklage(pfad, fund.zeile(), fund.wert());
        if (zeichen != null) {
            maengel.add(zeichen);
        }
        String klammer = klammerklage(pfad, fund.zeile(), fund.wert());
        if (klammer != null) {
            maengel.add(klammer);
        }
        if (satz.art() == Satz.Art.WORT || satz.art() == Satz.Art.MUSTER) {
            if (fund.wert().indexOf('<') >= 0) {
                maengel.add(ort(pfad, fund.zeile()) + " traegt eine spitze Klammer. Dieser"
                        + " Wert wird in einen ANDEREN Satz eingesetzt und ist dort ein"
                        + " Blatt - ein Farbtag darin stuende dem Spieler woertlich im"
                        + " Chat. Faerbe ihn im Satz, der ihn einsetzt.");
            }
        } else {
            String tag = tagklage(pfad, fund.zeile(), fund.wert());
            if (tag != null) {
                maengel.add(tag);
            }
        }
        if (satz.art() == Satz.Art.HANDBALKEN && fund.wert().indexOf('\n') >= 0) {
            maengel.add(ort(pfad, fund.zeile()) + " traegt eine Umbruchmarke. Der Handbalken"
                    + " ueber dem Gegenstandsgurt nimmt genau EINE Zeile (sendActionBar"
                    + " nimmt ein Component und keine Liste) - die zweite verschwindet"
                    + " stumm. Kuerze den Satz.");
        }
        return maengel;
    }

    /**
     * Die Zeichenwache: ASCII 0x20..0x7E, U+00B7 und genau ein Steuerzeichen.
     *
     * <p>U+00B7 ist ausdruecklich erlaubt und gemessen: Der Trenner kommt 18-mal in
     * {@code FreimarkenPlugin} und {@code ParzellenBefehle} vor. Alles andere faellt - jeder
     * Umlaut, jedes scharfe s, jedes Paragrafenzeichen und jeder alte {@code &}-Code. Ein
     * {@code &}-Code wuerde als Zeichen beim Spieler ankommen, und das ist genau die
     * Gattung Fehler, die niemand mehr zuordnet.
     */
    static String zeichenklage(String pfad, int zeile, String wert) {
        for (int i = 0; i < wert.length(); i++) {
            char zeichen = wert.charAt(i);
            if (zeichen == '\n' || zeichen == TRENNER) {
                continue;
            }
            if (zeichen >= 0x20 && zeichen <= 0x7e) {
                if (zeichen != '&') {
                    continue;
                }
                return ort(pfad, zeile) + " traegt ein kaufmaennisches Und an Stelle " + i
                        + ". Die alten Farbcodes (&7, &6) wirken hier nicht - Farbe steht"
                        + " in spitzen Klammern: <gray>, <gold>. Soll das Zeichen selbst"
                        + " stehen, gehoert es trotzdem nicht in diese Datei.";
            }
            return ort(pfad, zeile) + " traegt an Stelle " + i + " das Zeichen "
                    + benennung(zeichen) + ". Erlaubt sind ASCII von Leerzeichen bis Tilde,"
                    + " der Trenner " + TRENNER + " und die Umbruchmarke. Keine Umlaute,"
                    + " kein scharfes s, kein Paragrafenzeichen - ae, oe, ue, ss"
                    + " schreiben.";
        }
        return null;
    }

    /** Eine geschweifte Klammer ohne Gegenstueck, oder ein Name, der keiner sein kann. */
    private static String klammerklage(String pfad, int zeile, String wert) {
        String ohneMarken = MARKE.matcher(wert).replaceAll("");
        Matcher rest = KLAMMER.matcher(ohneMarken);
        if (!rest.find()) {
            return null;
        }
        return ort(pfad, zeile) + " traegt eine geschweifte Klammer, die kein Wert ist."
                + " Ein Wert heisst {name} - klein, mit Bindestrichen, ohne Leerzeichen."
                + " Gefunden wurde \"" + ohneMarken.substring(Math.max(0, rest.start() - 8),
                        Math.min(ohneMarken.length(), rest.start() + 9)) + "\". So"
                + " geschrieben kann kein Satz dieses Plugins je einen Wert nennen, also"
                + " ist es ein Tippfehler und keine Absicht.";
    }

    /**
     * Ein Tag, das der Leser nicht versteht - oder eines, das offen bleibt.
     *
     * <p><b>Gefragt wird der Vorrat SELBST und nicht eine Handliste.</b> Zwei Tore mit zwei
     * Wortschaetzen sind ein Fehler fuer sich: Eine Handliste mit zehn Farben haette
     * {@code <dark_blue>} abgewiesen - eine Farbe, die das Plugin kennt -, und der
     * Betreiber haette dabei alle seine anderen Aenderungen verloren. Gemessen an Adventure
     * 5.2.0 sagt {@code TagResolver.has} genau das Richtige: {@code gold}, {@code dark_blue},
     * {@code light_purple}, {@code #ffaa00}, {@code b}, {@code st} sind bekannt;
     * {@code goldd}, {@code click}, {@code hover}, {@code br}, {@code reset} nicht.
     *
     * <p>Die eigene Pruefung ist noetig, weil MiniMessage ein FALSCH GESCHRIEBENES Tag
     * nicht beklagt - auch nicht in strict mode. Gemessen:
     * {@code deserialize("<goldd>x</goldd>")} geht durch, und der Spieler liest
     * {@code <goldd>}. Das ist genau der stille Fehler, den diese ganze Maschine
     * verhindern soll.
     */
    static String tagklage(String pfad, int zeile, String wert) {
        for (int i = 0; i < wert.length(); i++) {
            if (wert.charAt(i) != '<') {
                continue;
            }
            if (i > 0 && wert.charAt(i - 1) == '\\') {
                // Entkommen - eine spitze Klammer als Zeichen. In der Datei stehen dafuer
                // ZWEI Backslashes; nach YAML ist genau einer davon uebrig.
                continue;
            }
            int zu = wert.indexOf('>', i);
            if (zu < 0) {
                return ort(pfad, zeile) + ", Spalte " + i + ": eine spitze Klammer ohne"
                        + " schliessende. Soll sie ein Zeichen sein, schreibe ZWEI"
                        + " Backslashes davor: \\\\<";
            }
            String marke = wert.substring(i + 1, zu);
            String name = marke.startsWith("/") ? marke.substring(1) : marke;
            int doppelpunkt = name.indexOf(':');
            if (doppelpunkt >= 0) {
                name = name.substring(0, doppelpunkt);
            }
            if (!Sprache.VORRAT.has(name.toLowerCase(Locale.ROOT))) {
                return ort(pfad, zeile) + ", Spalte " + i + ": <" + marke + "> versteht das"
                        + " Plugin nicht. Erlaubt ist jede Farbe und jede Schriftform, die"
                        + " MiniMessage kennt - zum Beispiel <gray> <dark_gray> <white>"
                        + " <yellow> <gold> <green> <red> <aqua> <bold> <italic>, dazu"
                        + " <dark_blue>, <light_purple> und <#ffaa00>. Nicht erlaubt sind"
                        + " <click> und <hover> - sie koennten einen Befehl im Namen des"
                        + " Spielers ausloesen." + vorschlag(name)
                        + " Soll eine spitze Klammer ein Zeichen sein, schreibe \\\\<";
            }
            i = zu;
        }
        try {
            Sprache.MINIMESSAGE.deserialize(wert);
        } catch (ParsingException offen) {
            return ort(pfad, zeile) + ", Spalte " + offen.startIndex() + ": "
                    + einzeilig(offen.detailMessage()) + " Jedes geoeffnete Tag muss"
                    + " geschlossen werden: <gold>...</gold>.";
        }
        return null;
    }

    /** ALLE Dubletten, auf jeder Ebene, je mit beiden Zeilennummern. */
    static List<String> dublettenklagen(Node wurzel) {
        List<String> klagen = new ArrayList<>();
        dubletten("", wurzel, klagen);
        return klagen;
    }

    private static void dubletten(String pfad, Node knoten, List<String> klagen) {
        if (!(knoten instanceof MappingNode abbildung)) {
            return;
        }
        Map<String, Integer> gesehen = new LinkedHashMap<>();
        for (NodeTuple paar : abbildung.getValue()) {
            if (!(paar.getKeyNode() instanceof ScalarNode name)) {
                continue;
            }
            String voll = pfad.isEmpty() ? name.getValue() : pfad + "." + name.getValue();
            int zeile = name.getStartMark().getLine() + 1;
            Integer vorher = gesehen.put(name.getValue(), zeile);
            if (vorher != null) {
                boolean blatt = paar.getValueNode() instanceof ScalarNode;
                klagen.add(DATEI + ": " + voll + " steht zweimal, in Zeile " + vorher
                        + " und in Zeile " + zeile + " - " + (blatt
                                ? "die zweite gilt und die erste nicht. Entferne eine,"
                                        + " dann wird nichts geraten."
                                : "vom ersten Block gilt NICHTS, auch nicht die Saetze"
                                        + " darin. Schiebe die Zeilen in den einen Block."));
            }
            dubletten(voll, paar.getValueNode(), klagen);
        }
    }

    /** Jedes Blatt des Baums mit Pfad, Wert und Zeile - und eine Klage je Nichtblatt. */
    private static void blaetter(String pfad, Node knoten, Map<String, Fund> hinein,
                                 List<String> klagen) {
        if (knoten instanceof MappingNode abbildung) {
            for (NodeTuple paar : abbildung.getValue()) {
                if (!(paar.getKeyNode() instanceof ScalarNode name)) {
                    klagen.add(DATEI + ", Zeile "
                            + (paar.getKeyNode().getStartMark().getLine() + 1)
                            + ": dort steht kein Schluessel, sondern ein ganzer Block."
                            + " Ein Schluessel ist ein Wort mit einem Doppelpunkt"
                            + " dahinter.");
                    continue;
                }
                String voll = pfad.isEmpty() ? name.getValue()
                        : pfad + "." + name.getValue();
                blaetter(voll, paar.getValueNode(), hinein, klagen);
            }
            return;
        }
        if (pfad.isEmpty()) {
            klagen.add(DATEI + " ist keine Liste von Schluesseln, sondern ein einzelner"
                    + " Wert. Erwartet werden Zeilen der Form schluessel: \"Satz\".");
            return;
        }
        if (knoten instanceof ScalarNode wert) {
            // Der Tag entscheidet, ob es Text ist. Gemessen: zahl: 42 traegt
            // tag:yaml.org,2002:int, wahr: true traegt bool, und ein Satz in doppelten
            // Anfuehrungszeichen traegt str. Die Klage nennt darum den Tag, den SnakeYAML
            // erkannt hat, und nicht "irgendwie kein Text".
            String tag = String.valueOf(wert.getTag());
            boolean text = tag.endsWith(":str");
            hinein.put(pfad, new Fund(pfad, text ? wert.getValue() : null,
                    wert.getStartMark().getLine() + 1, tag));
            return;
        }
        hinein.put(pfad, new Fund(pfad, null, knoten.getStartMark().getLine() + 1,
                String.valueOf(knoten.getNodeId())));
    }

    /** Namen im Wortlaut, die dieser Satz nicht kennt - die Obermenge. */
    private static Set<String> fremdeNamen(Satz satz, String wert) {
        Set<String> fremd = new TreeSet<>();
        Matcher treffer = MARKE.matcher(wert);
        while (treffer.find()) {
            if (!satz.platzhalter().contains(treffer.group(1))) {
                fremd.add(treffer.group(1));
            }
        }
        return fremd;
    }

    /** Namen, die der Satz anbietet und der Wortlaut nicht nennt - die Teilmenge. */
    private static Set<String> fehlendeNamen(Satz satz, String wert) {
        Set<String> fehlend = new TreeSet<>(satz.platzhalter());
        Matcher treffer = MARKE.matcher(wert);
        while (treffer.find()) {
            fehlend.remove(treffer.group(1));
        }
        return fehlend;
    }

    // --- Die Vorlage schreiben -------------------------------------------------

    /**
     * Schreibt {@link #VORLAGE} bei JEDEM Start neu.
     *
     * <p>Das ist die eine Haelfte der Zweidateiform, und ihr Grund ist gemessen:
     * {@code saveResource} ueberschreibt eine vorhandene Datei NIE, und
     * {@code Wirtschaftsliste.nenneFehlende} hat am 02.11.2026 gemessen, dass auf dem
     * Probeserver 16 von 42 Schluesseln fehlten und trotzdem wirkten. Waere die Datei des
     * Betreibers vollstaendig ausgeliefert, erreichte eine Satzverbesserung einen
     * laufenden Server nie.
     */
    static void schreibeVorlage(JavaPlugin plugin) {
        plugin.saveResource(VORLAGE, true);
    }

    /**
     * Legt {@link #DATEI} leer an - genau einmal, und danach nie wieder.
     *
     * <p>Die Existenz wird selbst geprueft und nicht {@code saveResource(DATEI, false)}
     * ueberlassen: Jenes schreibt bei vorhandener Datei eine Warnung ins Protokoll, und
     * eine Warnung, die bei JEDEM Start kommt und nichts bedeutet, macht die Warnungen
     * daneben unlesbar.
     */
    static void legeEigeneAn(JavaPlugin plugin) {
        if (!new File(plugin.getDataFolder(), DATEI).isFile()) {
            plugin.saveResource(DATEI, false);
        }
    }

    // --- Kleinzeug -------------------------------------------------------------

    /** UTF-8 mit WURF statt Ersatzzeichen - und mit Zeilennummer. */
    private static String streng(byte[] rohe) throws CharacterCodingException {
        CharsetDecoder leser = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return leser.decode(ByteBuffer.wrap(rohe)).toString();
    }

    /** Welche Zeile das erste unlesbare Byte traegt - fuer die Klage. */
    private static String wohl(File datei) {
        try {
            String ersetzt = new String(Files.readAllBytes(datei.toPath()),
                    StandardCharsets.UTF_8);
            // U+FFFD, das Ersatzzeichen - als Zahl, damit diese Quelldatei reines
            // ASCII bleibt.
            int stelle = ersetzt.indexOf(0xfffd);
            if (stelle < 0) {
                return "";
            }
            long zeile = ersetzt.substring(0, stelle).chars()
                    .filter(zeichen -> zeichen == '\n').count() + 1L;
            return " (zuerst in Zeile " + zeile + ")";
        } catch (IOException nichtLesbar) {
            return "";
        }
    }

    private static DateTimeFormatter zeitform(String muster) {
        try {
            return DateTimeFormatter.ofPattern(muster, Locale.GERMANY);
        } catch (IllegalArgumentException unbrauchbar) {
            return null;
        }
    }

    private static String ort(String pfad, int zeile) {
        return DATEI + ", Zeile " + zeile + ", " + pfad;
    }

    private static String marken(Set<String> namen) {
        List<String> mit = new ArrayList<>();
        namen.forEach(name -> mit.add("{" + name + "}"));
        return String.join(", ", mit);
    }

    private static String vorschlag(String name) {
        for (String bekannt : List.of("gold", "gray", "dark_gray", "white", "yellow",
                "green", "red", "aqua", "bold", "italic", "dark_blue", "light_purple")) {
            if (bekannt.startsWith(name) || name.startsWith(bekannt)) {
                return " Meintest du <" + bekannt + ">?";
            }
        }
        return "";
    }

    private static String einzeilig(String meldung) {
        return String.join(" ", String.valueOf(meldung).lines().map(String::trim).toList());
    }

    private static String benennung(char zeichen) {
        return "U+" + String.format(Locale.ROOT, "%04X", (int) zeichen)
                + (Character.isLetterOrDigit(zeichen) ? " (" + zeichen + ")" : "");
    }
}
