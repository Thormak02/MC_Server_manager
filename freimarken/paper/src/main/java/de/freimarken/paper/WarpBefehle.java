package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Warpverzeichnis;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * {@code /warp} - der zweite der beiden Reisewege aus Plan 4.3.
 *
 * <p>Die Ziele kommen aus der Weltenliste und nicht aus einer eigenen Liste; das Rechnen
 * dazu steht in {@link Warpverzeichnis} im Kern und ist dort geprueft. Hier bleibt der
 * Befehl.
 *
 * <p>Spielereigene Warps ({@code /warp setzen}) gehoeren zur Rangleiter (Plan 11.1) und
 * stehen hier noch nicht. Die Regel, dass ein eigener Warp dann nicht {@code kornmark}
 * heissen darf, steht als {@code Warpverzeichnis#reserviert} schon bereit.
 */
final class WarpBefehle {

    /** Wer reisen darf. Vorgabe: jeder - die Tore stehen ja offen. */
    static final String RECHT_REISEN = "freimarken.reisen";

    private final Warpverzeichnis verzeichnis;
    private final Map<String, World> welten;
    private final Reise reise;

    /**
     * Die Saetze dieses Plugins - verdrahtet, bevor der erste Satz umzieht.
     *
     * <p>Warum alle elf Sender dieses Feld in EINEM Zug bekommen und nicht je
     * Bauschritt, steht dort, wo sie gebaut werden: {@code FreimarkenPlugin.onEnable}.
     */
    private final Sprache sprache;

    /**
     * Die Regel ueber die Freistaedte - {@code null}, solange die Welt fehlt.
     *
     * <p>Aus ihr kommen die Staedte UND der Stadtfeldplan, aus dem die Mitte eines Feldes
     * folgt. Zwei Quellen dafuer waeren zwei Geometrien.
     */
    private final Freistadtbuch freistadtbuch;

    /** Die Welt der Stadtfelder - {@code null}, wenn sie fehlt. */
    private final World freistadtwelt;

    WarpBefehle(Warpverzeichnis verzeichnis, Map<String, World> welten, Reise reise,
                Sprache sprache, Freistadtbuch freistadtbuch, World freistadtwelt) {
        this.sprache = sprache;
        this.verzeichnis = verzeichnis;
        this.welten = welten;
        this.reise = reise;
        this.freistadtbuch = freistadtbuch;
        this.freistadtwelt = freistadtwelt;
    }

    LiteralCommandNode<CommandSourceStack> warp() {
        return Commands.literal("warp")
                // Spielerpflichtig an der Wurzel: Die Konsole hat keinen Ort, in den sie
                // reisen koennte. Die Pruefung laeuft beim Einlesen gegen die
                // ursprueungliche Quelle - deshalb scheitert auch
                // "execute as <spieler> run warp ..." und das ist richtig so.
                .requires(quelle -> quelle.getExecutor() instanceof Player
                        && quelle.getSender().hasPermission(RECHT_REISEN))
                .executes(z -> {
                    zeigeZiele(z.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                // Das Literal VOR dem Wortargument, und mit eigenem .executes.
                // Brigadier gibt bei einem passenden Literal nur dieses zurueck
                // (CommandNode.getRelevantNodes) - die Argumentknoten werden dann GAR
                // NICHT mehr betrachtet. Ohne eigenes .executes faellt "/warp freistadt"
                // ohne Namen nicht auf den Zweig darunter durch, sondern endet in
                // Brigadiers englischem "Unknown or incomplete command".
                //
                // Dass dieses Literal jede Welt namens "freistadt" verschattet, ist der
                // Preis - und er ist bezahlbar: Die Welt der Stadtfelder heisst
                // "freistaedte", und Warpverzeichnis.reserviert ist die Stelle, an der
                // ein Stadtname gegen einen Weltnamen abgewiesen wird.
                .then(Commands.literal("freistadt")
                        .executes(z -> {
                            zeigeStaedte(z.getSource().getExecutor());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("stadt", StringArgumentType.word())
                                .suggests((zusammenhang, bauer) -> {
                                    // Aus dem Stadtverzeichnis und NICHT aus der
                                    // Ablage: Ein Vorschlagsgeber laeuft bei jedem
                                    // Tastendruck, und bis zu 399 Staedte stehen in
                                    // der Datenbank. Ohne Buch gibt es nichts zu
                                    // schlagen - dann faellt das Fenster leer aus,
                                    // und die Absage kommt beim Tippen.
                                    if (freistadtbuch != null) {
                                        freistadtbuch.verzeichnis()
                                                .vorschlaege(bauer.getRemainingLowerCase())
                                                .forEach(bauer::suggest);
                                    }
                                    return bauer.buildFuture();
                                })
                                .executes(z -> {
                                    reiseInStadt(z.getSource().getExecutor(),
                                            StringArgumentType.getString(z, "stadt"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.argument("ziel", StringArgumentType.word())
                        .suggests((zusammenhang, bauer) -> {
                            // Ein Vorschlagsgeber laeuft bei jedem Tastendruck: keine
                            // Meldung, keine Abklingzeit, kein Blick in die Welt.
                            verzeichnis.vorschlaege(bauer.getRemainingLowerCase())
                                    .forEach(bauer::suggest);
                            return bauer.buildFuture();
                        })
                        .executes(z -> {
                            reiseAn(z.getSource().getExecutor(),
                                    StringArgumentType.getString(z, "ziel"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    /**
     * {@code /warp freistadt} ohne Namen - zeigt, wo die Namen stehen.
     *
     * <p>Nicht {@code private}, wie jeder Zweig: Nur so ruft eine Probe ihn mit einem
     * mitschreibenden Spieler auf. {@link Reise} bleibt dabei unberuehrt.
     */
    void zeigeStaedte(Object empfaenger) {
        if (!(empfaenger instanceof Player spieler)) {
            return;
        }
        (freistadtbuch == null
                ? keineStadtweltZeilen(sprache)
                : welcheStadtZeilen(sprache)).forEach(spieler::sendMessage);
    }

    /**
     * {@code /warp freistadt <name>} - und seine zwei Absagen.
     *
     * <p>BEIDE Absagen lassen {@link Reise} unberuehrt; erst die letzte Zeile braucht
     * sie. Nur so kommt eine Probe ohne Server bis hierher.
     *
     * <p>Das Ziel ist ein ORT und keine Welt: Es gibt 399 Staedte in EINER Welt, und
     * {@code fuehre(spieler, World, ...)} landete bei allen am selben Punkt.
     */
    void reiseInStadt(Object empfaenger, String eingabe) {
        if (!(empfaenger instanceof Player spieler)) {
            return;
        }
        if (freistadtbuch == null || freistadtwelt == null) {
            keineStadtweltZeilen(sprache).forEach(spieler::sendMessage);
            return;
        }
        String gesucht = eingabe.trim().toLowerCase(java.util.Locale.ROOT);
        Parzelle stadt = null;
        for (Parzelle vorhandene
                : freistadtbuch.staedte(freistadtwelt.key().asString())) {
            if (vorhandene.name().equals(gesucht)) {
                stadt = vorhandene;
                break;
            }
        }
        if (stadt == null) {
            keineStadtZeilen(sprache, eingabe).forEach(spieler::sendMessage);
            return;
        }
        // Mittig auf dem Block und auf Fusshoehe ueber dem Gras, wie der
        // Erscheinungspunkt der Welt. Die Mitte kommt aus dem Stadtfeldplan des Buchs -
        // nicht aus einer zweiten Rechnung hier.
        var plan = freistadtbuch.plan();
        reise.fuehre(spieler, new org.bukkit.Location(freistadtwelt,
                        plan.feldMitte(stadt.x()) + 0.5, Feldboden.BODEN + 1,
                        plan.feldMitte(stadt.z()) + 0.5),
                sprache.wort(ReiseSatz.WARP_STADT_AUFSCHRIFT,
                        Einsatz.von("name", stadt.name())));
    }

    /**
     * Das nackte {@code /warp} - sichtbar fuer die Probe, die den Zweig durchlaeuft.
     *
     * <p>Nicht {@code private}, wie jeder Zweig von {@link ParzellenBefehle}: Nur so ruft
     * {@code AufrufstelleTest} ihn mit einem mitschreibenden Spieler auf. Ohne Server geht
     * das, weil dieser Zweig {@link Reise} nicht anfasst - er fragt allein das
     * {@link Warpverzeichnis}, und das ist reine Zeichenkettenarbeit im Kern.
     */
    void zeigeZiele(Object empfaenger) {
        if (!(empfaenger instanceof Player spieler)) {
            return;
        }
        zieleZeilen(sprache, verzeichnis.welten()).forEach(spieler::sendMessage);
    }

    /**
     * {@code /warp <ziel>} - und seine zwei Absagen.
     *
     * <p>Nicht {@code private}, aus demselben Grund wie {@link #zeigeZiele}. Auch hier
     * bleibt {@link Reise} in BEIDEN Absagen unberuehrt; erst die letzte Zeile braucht
     * sie, und bis dorthin kommt eine Probe ohne Server nicht.
     */
    void reiseAn(Object empfaenger, String eingabe) {
        if (!(empfaenger instanceof Player spieler)) {
            return;
        }
        String name = verzeichnis.aufgeloest(eingabe).orElse(null);
        if (name == null) {
            keinZielZeilen(sprache, eingabe, verzeichnis.welten())
                    .forEach(spieler::sendMessage);
            return;
        }
        World ziel = welten.get(name);
        if (ziel == null) {
            // Der Name steht in der Datei, die Welt ist aber nicht entstanden - das hat
            // beim Start geklagt. Hier nur die Absage, nicht noch eine Erklaerung.
            weltFehltZeilen(sprache, name).forEach(spieler::sendMessage);
            return;
        }
        reise.fuehre(spieler, ziel, name);
    }

    // --- Die Naht: drei Antworten, ohne Player und ohne Bukkit -----------------

    /**
     * Die Auskunft auf das nackte {@code /warp} - DREI Chatzeilen aus EINEM Griff.
     *
     * <p>Ueberschrift, Liste, Hinweis: Sie gehen immer zusammen hinaus, nie einzeln, und
     * darum ist der Zeilenbau hier ein Stueck des Satzes und nicht eine Folge von drei
     * Sendeaufrufen. Die Reihenfolge der Welten kommt aus der {@code config.yml} und
     * bleibt dort entschieden ({@link Warpverzeichnis}); nur der TRENNER zwischen ihnen
     * ist Darstellung.
     */
    static List<Component> zieleZeilen(Sprache sprache, List<String> welten) {
        return sprache.bau(ReiseSatz.WARP_ZIELE, 1L, Einsatz.von("liste",
                String.join(sprache.wort(ReiseSatz.WARP_ZIELE_TRENNER, Einsatz.keine()),
                        welten)));
    }

    /**
     * Die Absage auf einen Namen, den es nicht gibt - ZWEI Chatzeilen aus EINEM Griff.
     *
     * <p>{@code eingabe} ist der GETIPPTE Name und nicht der aufgeloeste: In diesem Zweig
     * hat {@code aufgeloest} nichts gefunden, es gibt also keinen aufgeloesten. Die zweite
     * Zeile nennt dieselbe Liste wie {@link #zieleZeilen}, aber mit einem anderen Trenner
     * und in Grau - zwei Lagen, zwei Saetze.
     */
    static List<Component> keinZielZeilen(Sprache sprache, String eingabe,
                                          List<String> welten) {
        return sprache.bau(ReiseSatz.WARP_KEIN_ZIEL, 1L, Einsatz
                .von("eingabe", eingabe)
                .mit("liste", String.join(sprache.wort(
                        ReiseSatz.WARP_KEIN_ZIEL_TRENNER, Einsatz.keine()), welten)));
    }

    /**
     * Der Name steht in der {@code config.yml}, die Welt ist nicht entstanden.
     *
     * <p>{@code welt} ist der AUFGELOESTE Name und nicht die Eingabe - immer klein, immer
     * aus der Datei. Das ist der Unterschied, an dem ein Betreiber erkennt, ob er in die
     * {@code config.yml} oder in den laufenden Server schauen muss, und darum traegt
     * dieser Satz einen anderen Wertnamen als {@link #keinZielZeilen}.
     */
    static List<Component> weltFehltZeilen(Sprache sprache, String welt) {
        return sprache.bau(ReiseSatz.WARP_WELT_FEHLT, 1L, Einsatz.von("welt", welt));
    }

    /**
     * Die Welt der Freistaedte fehlt - fuer BEIDE Zweige, die sie brauchen.
     *
     * <p>Eine Methode fuer zwei Aufrufer und nicht zwei gleichlautende: Es ist DIESELBE
     * Lage, und zwei Stellen fuer eine Lage laufen beim naechsten Satzwechsel
     * auseinander.
     */
    static List<Component> keineStadtweltZeilen(Sprache sprache) {
        return sprache.bau(ReiseSatz.WARP_STADT_KEINE_WELT, 1L, Einsatz.keine());
    }

    /** {@code /warp freistadt} ohne Namen - der Hinweis, wo die Namen stehen. */
    static List<Component> welcheStadtZeilen(Sprache sprache) {
        return sprache.bau(ReiseSatz.WARP_STADT_OHNE_NAMEN, 1L, Einsatz.keine());
    }

    /**
     * Die Absage auf einen Stadtnamen, den es nicht gibt - ZWEI Chatzeilen.
     *
     * <p><b>Diese drei Methoden sind ein Nachzug, und der Grund gehoert aufgeschrieben:
     * </b> Die drei Zweige in die Freistaedte sendeten ihre Antwort anfangs mit
     * {@code sprache.eineZeile} unmittelbar aus dem Zweig heraus - als einzige in dieser
     * Datei, wo alles andere seit Schritt 5 ueber die Naht geht. Und {@code eineZeile}
     * nimmt genau EINE Zeile: {@code warp.stadt-unbekannt} traegt aber eine Umbruchmarke,
     * also WARF der Zweig, und der Spieler las eine rote Brigadier-Zeile statt seiner
     * Absage. Keine Bildtafel sah das - sie ruft die Saetze selbst und kommt am Zweig
     * vorbei. Gefunden hat es die Probe, die den Befehl tippt.
     *
     * <p>{@code eingabe} ist der GETIPPTE Name und nicht der genormte: In diesem Zweig
     * gibt es keine Stadt, deren Name zu nennen waere. Wer sich vertippt, soll seinen
     * eigenen Vertipper lesen.
     */
    static List<Component> keineStadtZeilen(Sprache sprache, String eingabe) {
        return sprache.bau(ReiseSatz.WARP_STADT_UNBEKANNT, 1L,
                Einsatz.von("name", eingabe));
    }
}
