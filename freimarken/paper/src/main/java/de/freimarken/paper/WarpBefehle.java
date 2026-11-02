package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Warpverzeichnis;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

    WarpBefehle(Warpverzeichnis verzeichnis, Map<String, World> welten, Reise reise) {
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

    private void zeigeZiele(Object empfaenger) {
        if (!(empfaenger instanceof Player spieler)) {
            return;
        }
        spieler.sendMessage(Component.text("Reiseziele", NamedTextColor.GOLD));
        spieler.sendMessage(Component.text(String.join("  ", verzeichnis.welten()),
                NamedTextColor.AQUA));
        spieler.sendMessage(Component.text("/warp <ziel> - oder durch ein Tor am"
                + " Bundesmarkt.", NamedTextColor.GRAY));
    }

    private void reiseAn(Object empfaenger, String eingabe) {
        if (!(empfaenger instanceof Player spieler)) {
            return;
        }
        String name = verzeichnis.aufgeloest(eingabe).orElse(null);
        if (name == null) {
            spieler.sendMessage(Component.text("Dorthin geht keine Reise: " + eingabe,
                    NamedTextColor.RED));
            spieler.sendMessage(Component.text("Moeglich sind: "
                    + String.join(", ", verzeichnis.welten()), NamedTextColor.GRAY));
            return;
        }
        World ziel = welten.get(name);
        if (ziel == null) {
            // Der Name steht in der Datei, die Welt ist aber nicht entstanden - das hat
            // beim Start geklagt. Hier nur die Absage, nicht noch eine Erklaerung.
            spieler.sendMessage(Component.text("Die Welt " + name + " gibt es gerade"
                    + " nicht.", NamedTextColor.RED));
            return;
        }
        reise.fuehre(spieler, ziel, name);
    }
}
