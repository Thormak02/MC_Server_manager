package de.freimarken.paper;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import de.freimarken.kern.Preisstaffel;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Einstiegspunkt des Freimarken-Plugins.
 *
 * <p>Dieser erste Stand kann bewusst fast nichts. Er beweist die Kette: Java 25, Paper
 * 26.3, Gradle, der Kern als eigenes Modul in der Jar, und ein Brigadier-Befehl mit
 * echtem Argumenttyp und Vervollstaendigung.
 *
 * <p>Die Befehle laufen ueber die Lifecycle-API, nicht ueber {@code commands:} in der
 * plugin.yml. Das geht auch mit der klassischen plugin.yml - es braucht dafuer kein
 * paper-plugin.yml, das laut Paper noch als experimentell gefuehrt wird.
 */
public final class FreimarkenPlugin extends JavaPlugin {

    /** Deutsche Tausenderpunkte: 400000 wird zu 400.000. */
    private static final NumberFormat BETRAG = NumberFormat.getIntegerInstance(Locale.GERMANY);

    @Override
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, ereignis -> {
            ereignis.registrar().register(
                    Commands.literal("freimarken")
                            .executes(zusammenhang -> {
                                zeigeStand(zusammenhang.getSource().getSender());
                                return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.literal("preis")
                                    .then(Commands.argument("besessen",
                                                    IntegerArgumentType.integer(0, 64))
                                            .executes(zusammenhang -> {
                                                int besessen = IntegerArgumentType.getInteger(
                                                        zusammenhang, "besessen");
                                                zeigePreis(zusammenhang.getSource().getSender(),
                                                        besessen);
                                                return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                            })))
                            .build(),
                    "Stand und Preise des Freimarkenbunds",
                    List.of("fm"));
        });

        getLogger().info("Freimarken " + getPluginMeta().getVersion()
                + " bereit - Java " + Runtime.version().feature());
    }

    private void zeigeStand(net.kyori.adventure.audience.Audience empfaenger) {
        empfaenger.sendMessage(Component.text("Der Freimarkenbund", NamedTextColor.GOLD));
        empfaenger.sendMessage(Component.text(
                "Stand " + getPluginMeta().getVersion()
                        + " · Java " + Runtime.version().feature()
                        + " · " + getServer().getMinecraftVersion(),
                NamedTextColor.GRAY));
        empfaenger.sendMessage(Component.text(
                "Erste Parzelle gratis, jede weitere verdoppelt. /freimarken preis <anzahl>",
                NamedTextColor.GRAY));
    }

    private void zeigePreis(net.kyori.adventure.audience.Audience empfaenger, int besessen) {
        long preis = Preisstaffel.preisFuerNaechste(besessen);
        Component satz = preis == 0L
                ? Component.text("Die erste Parzelle ist gratis.", NamedTextColor.GREEN)
                : Component.text("Parzelle Nr. " + (besessen + 1) + " kostet ",
                        NamedTextColor.GRAY)
                        .append(Component.text(BETRAG.format(preis) + " Mark",
                                NamedTextColor.GOLD))
                        .append(Component.text(".", NamedTextColor.GRAY));
        empfaenger.sendMessage(satz);
        empfaenger.sendMessage(Component.text(
                "Bisher ausgegeben: " + BETRAG.format(Preisstaffel.gesamtpreisFuer(besessen))
                        + " Mark",
                NamedTextColor.DARK_GRAY));
    }
}
