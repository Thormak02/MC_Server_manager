package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Rasterplan;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Einstiegspunkt des Freimarken-Plugins.
 *
 * <p>Stand: die Marktstadt wird beim Start erzeugt oder geladen, und zwar IMMER mit dem
 * eigenen Generator. Das ist kein Schoenheitsfehler, sondern Pflicht: Ein Plugin-Generator
 * wird nicht in der Weltdatei gespeichert. Wuerde die Welt einmal ohne ihn geladen, schriebe
 * der Server beim naechsten neuen Chunk Vanille-Gelaende an den Parzellenrand - und das ist
 * ohne Ruecksicherung nicht mehr zu reparieren.
 */
public final class FreimarkenPlugin extends JavaPlugin {

    /** Deutsche Tausenderpunkte: 400000 wird zu 400.000. */
    private static final NumberFormat BETRAG = NumberFormat.getIntegerInstance(Locale.GERMANY);

    private final Rasterplan plan = Rasterplan.marktstadt();
    private final MarktstadtGenerator generator = new MarktstadtGenerator(plan);
    private World marktstadt;

    @Override
    public void onEnable() {
        marktstadt = ladeMarktstadt();
        registriereBefehle();

        getLogger().info("Freimarken " + getPluginMeta().getVersion()
                + " bereit - Java " + Runtime.version().feature()
                + ", Marktstadt " + (marktstadt != null ? "bereit" : "FEHLT"));
    }

    /**
     * Der Generator gilt auch, wenn die Welt ueber die bukkit.yml geladen wird.
     *
     * <p>Zweiter Riegel gegen denselben Unfall: Wer die Welt dort eintraegt, bekommt
     * trotzdem das Raster und nicht Vanille-Gelaende.
     */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String weltName, String kennung) {
        return generator;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(String weltName, String kennung) {
        return generator.getDefaultBiomeProvider(null);
    }

    private World ladeMarktstadt() {
        NamespacedKey schluessel = new NamespacedKey(this, "marktstadt");
        World vorhanden = getServer().getWorld(schluessel);
        if (vorhanden != null) {
            return vorhanden;
        }
        long begonnen = System.currentTimeMillis();
        World welt = WorldCreator.ofKey(schluessel)
                .generator(generator)
                .biomeProvider(generator.getDefaultBiomeProvider(null))
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                .createWorld();
        if (welt == null) {
            getLogger().severe("Die Marktstadt konnte nicht angelegt werden.");
            return null;
        }
        welt.setSpawnLocation(plan.mitteX(0), MarktstadtGenerator.BODEN + 1, plan.mitteZ(0));
        getLogger().info("Marktstadt bereit in " + (System.currentTimeMillis() - begonnen)
                + " ms, Raster " + plan.parzellenKante() + " + " + plan.wegBreite());
        return welt;
    }

    private void registriereBefehle() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, ereignis ->
                ereignis.registrar().register(
                        Commands.literal("freimarken")
                                .executes(z -> {
                                    zeigeStand(z.getSource().getSender());
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.literal("preis")
                                        .then(Commands.argument("besessen",
                                                        IntegerArgumentType.integer(0, 64))
                                                .executes(z -> {
                                                    zeigePreis(z.getSource().getSender(),
                                                            IntegerArgumentType.getInteger(
                                                                    z, "besessen"));
                                                    return Command.SINGLE_SUCCESS;
                                                })))
                                .then(Commands.literal("welt")
                                        .executes(z -> {
                                            zeigeWelt(z.getSource().getSender());
                                            return Command.SINGLE_SUCCESS;
                                        }))
                                .then(Commands.literal("parzelle")
                                        .then(Commands.argument("x",
                                                        IntegerArgumentType.integer(-64, 64))
                                                .then(Commands.argument("z",
                                                                IntegerArgumentType.integer(-64, 64))
                                                        .executes(z -> {
                                                            bringeZurParzelle(
                                                                    z.getSource().getSender(),
                                                                    IntegerArgumentType.getInteger(z, "x"),
                                                                    IntegerArgumentType.getInteger(z, "z"));
                                                            return Command.SINGLE_SUCCESS;
                                                        }))))
                                .build(),
                        "Stand, Preise und Welt des Freimarkenbunds",
                        List.of("fm")));
    }

    private void zeigeStand(Audience empfaenger) {
        empfaenger.sendMessage(Component.text("Der Freimarkenbund", NamedTextColor.GOLD));
        empfaenger.sendMessage(Component.text(
                "Stand " + getPluginMeta().getVersion()
                        + " · Java " + Runtime.version().feature()
                        + " · " + getServer().getMinecraftVersion(),
                NamedTextColor.GRAY));
        empfaenger.sendMessage(Component.text(
                "/freimarken preis <anzahl> · welt · parzelle <x> <z>", NamedTextColor.GRAY));
    }

    private void zeigePreis(Audience empfaenger, int besessen) {
        long preis = Preisstaffel.preisFuerNaechste(besessen);
        empfaenger.sendMessage(preis == 0L
                ? Component.text("Die erste Parzelle ist gratis.", NamedTextColor.GREEN)
                : Component.text("Parzelle Nr. " + (besessen + 1) + " kostet ", NamedTextColor.GRAY)
                        .append(Component.text(BETRAG.format(preis) + " Mark", NamedTextColor.GOLD))
                        .append(Component.text(".", NamedTextColor.GRAY)));
        empfaenger.sendMessage(Component.text(
                "Bisher ausgegeben: " + BETRAG.format(Preisstaffel.gesamtpreisFuer(besessen))
                        + " Mark", NamedTextColor.DARK_GRAY));
    }

    /**
     * Zeigt den Zustand der Welt - und liest dafuer echte Bloecke aus.
     *
     * <p>Absichtlich keine Berechnung: Der Rasterplan wuerde dieselbe Antwort liefern, ohne
     * dass je ein Chunk erzeugt wurde. Gefragt ist hier aber, was WIRKLICH in der Welt
     * steht.
     */
    private void zeigeWelt(Audience empfaenger) {
        if (marktstadt == null) {
            empfaenger.sendMessage(Component.text("Die Marktstadt fehlt.", NamedTextColor.RED));
            return;
        }
        empfaenger.sendMessage(Component.text("Marktstadt", NamedTextColor.GOLD));
        empfaenger.sendMessage(Component.text(
                "Schluessel " + marktstadt.key().asString()
                        + " · Raster " + plan.parzellenKante() + "+" + plan.wegBreite()
                        + " · Boden y=" + MarktstadtGenerator.BODEN,
                NamedTextColor.GRAY));

        int y = MarktstadtGenerator.BODEN;
        probe(empfaenger, "Parzellenmitte", plan.mitteX(0), y, plan.mitteZ(0));
        probe(empfaenger, "Parzellenrand ", plan.parzellenKante() - 1, y, 10);
        probe(empfaenger, "Mauer         ", plan.parzellenKante(), y, 10);
        probe(empfaenger, "Weg           ", plan.parzellenKante() + 3, y, 10);
        probe(empfaenger, "Mauerkrone    ", plan.parzellenKante(), y + 1, 10);
    }

    private void probe(Audience empfaenger, String was, int x, int y, int z) {
        String block = marktstadt.getBlockAt(x, y, z).getType().name();
        empfaenger.sendMessage(Component.text("  " + was + " (" + x + "/" + y + "/" + z + "): ",
                        NamedTextColor.DARK_GRAY)
                .append(Component.text(block, NamedTextColor.AQUA)));
    }

    private void bringeZurParzelle(Audience empfaenger, int parzelleX, int parzelleZ) {
        if (marktstadt == null) {
            empfaenger.sendMessage(Component.text("Die Marktstadt fehlt.", NamedTextColor.RED));
            return;
        }
        Location ziel = new Location(marktstadt,
                plan.mitteX(parzelleX) + 0.5, MarktstadtGenerator.BODEN + 1,
                plan.mitteZ(parzelleZ) + 0.5);
        if (empfaenger instanceof Player spieler) {
            spieler.teleportAsync(ziel);
            empfaenger.sendMessage(Component.text(
                    "Unterwegs zu Parzelle " + parzelleX + "/" + parzelleZ, NamedTextColor.GREEN));
        } else {
            empfaenger.sendMessage(Component.text(
                    "Parzelle " + parzelleX + "/" + parzelleZ + " liegt bei "
                            + ziel.getBlockX() + "/" + ziel.getBlockZ()
                            + " (Ursprung " + plan.ursprungX(parzelleX) + "/"
                            + plan.ursprungZ(parzelleZ) + ")", NamedTextColor.GRAY));
        }
    }
}
