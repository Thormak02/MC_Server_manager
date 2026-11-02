package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * {@code /zurueck} - und {@code /back}, weil viele Spieler das tippen.
 *
 * <p>Fuehrt an den Ort, an dem der Spieler zuletzt war: vor einer Reise, vor einem
 * Teleport, oder an die Stelle, an der er gestorben ist.
 *
 * <p>Der Befehl prueft <b>nichts</b> selbst. Jede Absage - Welt nicht geladen, Boden
 * abgebaut, Lava gelaufen, zu Pferd, zu schnell hintereinander - kommt aus {@link Reise},
 * und das ist die Projektregel „alle Wege zur selben Tat". Eine zweite Fassung derselben
 * Pruefungen waere eine, die eines Tages nicht mitgepflegt wird.
 *
 * <p>Dass zweimal {@code /zurueck} wieder am Ausgangspunkt endet, steht nirgends als
 * Regel: Es folgt daraus, dass {@code Reise} vor jedem Teleport den jetzigen Ort merkt.
 */
final class RueckwegBefehle {

    /** Wer zurueck darf. Vorgabe: jeder. */
    static final String RECHT_ZURUECK = "freimarken.zurueck";

    private final Rueckweg rueckweg;
    private final Reise reise;

    RueckwegBefehle(Rueckweg rueckweg, Reise reise) {
        this.rueckweg = rueckweg;
        this.reise = reise;
    }

    LiteralCommandNode<CommandSourceStack> zurueck() {
        return Commands.literal("zurueck")
                .requires(quelle -> quelle.getExecutor() instanceof Player
                        && quelle.getSender().hasPermission(RECHT_ZURUECK))
                .executes(z -> {
                    fuehreZurueck(z.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private void fuehreZurueck(Object empfaenger) {
        if (!(empfaenger instanceof Player spieler)) {
            return;
        }
        var ziel = rueckweg.letzter(spieler.getUniqueId());
        if (ziel.isEmpty()) {
            spieler.sendMessage(Component.text("Es ist kein Ort gemerkt - du bist noch"
                    + " nirgends gewesen, wohin es zurueckgeht.", NamedTextColor.GRAY));
            return;
        }
        reise.fuehre(spieler, ziel.get(), "deinen letzten Ort");
    }
}
