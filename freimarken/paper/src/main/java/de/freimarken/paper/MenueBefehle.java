package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /menu} - das Hauptmenue, der Einstieg fuer einen Neuling.
 *
 * <p>Plan 11.1 fuehrt den Befehl seit dem ersten Entwurf, und Plan 10 nennt den Grund:
 * "anfaengerfreundlich" ist eine Entwurfsregel, und ein Neuling, der siebzehn Befehle
 * lernen muss, bevor er seine erste Parzelle findet, ist kein Neuling mehr, wenn er sie
 * hat.
 *
 * <p>Dieselbe Bauart wie {@link RangBefehle}: Der Baum, die Rechtefrage und EINE
 * Sendestelle stehen hier, die Zeilen kommen aus der Sprachdatei, und WAS im Menue steht,
 * ist eine {@link Menuetafel} - reine Daten.
 *
 * <p><b>Das Oeffnen ist eine NAHT</b> und kein Aufruf: {@code Bukkit.createInventory} und
 * {@code new ItemStack} brauchen einen laufenden Server. Mit der Naht laeuft jeder Zweig
 * dieses Befehls durch eine Probe - ohne sie waere der Befehl die einzige Stelle des
 * Plugins, deren Zweige niemand durchlaeuft.
 */
final class MenueBefehle {

    /** Wer das Menue oeffnen darf - jeder. Angemeldet mit {@code default: true}. */
    static final String RECHT = "freimarken.menu";

    /** Der Name des Befehls - er steht woertlich in den Saetzen der Sprachdatei. */
    private static final String ARGUMENT = "menu";

    /**
     * Was ein Menue oeffnet.
     *
     * <p>EINE Methode, und sie gibt zurueck, ob es offen ist: Ein unbekannter Gegenstand
     * in der Tafel ist ein Baufehler, und der Spieler soll dann einen Satz lesen und
     * nicht in ein leeres Fenster sehen.
     */
    interface Menueoeffner {
        boolean oeffne(Player wer, Menuetafel tafel);
    }

    private final Menueoeffner oeffner;
    private final Sprache sprache;

    MenueBefehle(Menueoeffner oeffner, Sprache sprache) {
        if (oeffner == null || sprache == null) {
            throw new IllegalArgumentException("/menu braucht einen Oeffner und Sprache");
        }
        this.oeffner = oeffner;
        this.sprache = sprache;
    }

    /**
     * Der Baum: nur {@code /menu}, ohne Unterbefehl.
     *
     * <p>Das Recht steht im {@code requires} am Wurzelknoten und fragt
     * {@code quelle.getSender()} - nur so antwortet die Pruefung auch fuer einen
     * Stellvertreter in einer Probe.
     */
    LiteralCommandNode<CommandSourceStack> menu() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(this::zeige)
                .build();
    }

    private int zeige(CommandContext<CommandSourceStack> zusammenhang) {
        if (!(zusammenhang.getSource().getExecutor() instanceof Player wer)) {
            // Ein Menue ist ein Inventar, und die Konsole hat keines. Eine eigene Zeile
            // aus der Sprachdatei und nicht Brigadiers englische: Auch ein SPIELER kann
            // auf der Konsole tippen lassen.
            sende(zusammenhang.getSource().getSender(),
                    List.of(sprache.eineZeile(MenueSatz.KEINE_KONSOLE, Einsatz.keine())));
            return 0;
        }
        if (!oeffner.oeffne(wer, Menuetafel.HAUPT)) {
            sende(wer, List.of(sprache.eineZeile(MenueSatz.GESTOERT, Einsatz.keine())));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Die EINE Sendestelle dieser Klasse. */
    private void sende(Audience an, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            an.sendMessage(zeile);
        }
    }
}
