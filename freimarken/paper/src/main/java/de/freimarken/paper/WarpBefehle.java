package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
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

    WarpBefehle(Warpverzeichnis verzeichnis, Map<String, World> welten, Reise reise,
                Sprache sprache) {
        this.sprache = sprache;
        this.verzeichnis = verzeichnis;
        this.welten = welten;
        this.reise = reise;
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
}
