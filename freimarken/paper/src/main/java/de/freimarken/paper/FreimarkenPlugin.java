package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.daten.Datenbank;
import de.freimarken.daten.MariaKasse;
import de.freimarken.daten.MariaLager;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Rasterplan;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.ZwischenspeicherLager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Einstiegspunkt des Freimarken-Plugins.
 *
 * <p>Stand: die Marktstadt wird beim Start erzeugt oder geladen, und zwar IMMER mit dem
 * eigenen Generator. Das ist kein Schoenheitsfehler, sondern Pflicht: Ein Plugin-Generator
 * wird nicht in der Weltdatei gespeichert. Wuerde die Welt einmal ohne ihn geladen, schriebe
 * der Server beim naechsten neuen Chunk Vanille-Gelaende an den Parzellenrand - und das ist
 * ohne Ruecksicherung nicht mehr zu reparieren.
 *
 * <p>Zur Ablage gibt es zwei Zustaende und keinen dritten: Mit Datenbank laeuft alles, ohne
 * Datenbank laeuft es fluechtig und sagt das laut. Ist eine Datenbank eingetragen, antwortet
 * aber nicht, startet das Plugin NICHT. Ein Server, der Parzellen vergibt und beim Neustart
 * vergisst, waere schlimmer als einer, der gar nicht hochkommt.
 */
public final class FreimarkenPlugin extends JavaPlugin {

    private final Rasterplan plan = Rasterplan.marktstadt();
    private final MarktstadtGenerator generator = new MarktstadtGenerator(plan);

    private World marktstadt;
    private Datenbank datenbank;
    private Besitzbuch buch;
    private ParzellenBefehle befehle;
    private boolean fluechtig;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Die Ablage ZUERST. Andersherum stand die Marktstadt schon geladen da, wenn
        // sich das Plugin wegen einer unerreichbaren Datenbank abschaltet - mit ihr eine
        // Welt voller Parzellen und ohne jede Schutzwache, denn die wird erst weiter unten
        // angemeldet. Wer zuletzt dort ausgeloggt war, erschiene beim Einloggen genau dort
        // und duerfte ueberall bauen. Jetzt entsteht im Fehlerfall gar keine Welt.
        if (!richteAblageEin()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        marktstadt = ladeMarktstadt();
        if (marktstadt == null) {
            getLogger().severe("Ohne Marktstadt gibt es nichts zu verwalten - Freimarken bleibt aus.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Der Schluesseltext, nicht der NamespacedKey: World.key() gibt ein Key zurueck,
            // und ein Vergleich zwischen beiden ist immer falsch, ohne dass es auffaellt.
        String welt = marktstadt.key().asString();
        befehle = new ParzellenBefehle(buch, welt,
                getConfig().getInt("parzellen.hoechstzahl", 4));
        getServer().getPluginManager().registerEvents(new Schutzwache(buch, welt), this);
        registriereBefehle();

        getLogger().info("Freimarken " + getPluginMeta().getVersion()
                + " bereit - Java " + Runtime.version().feature()
                + ", Ablage " + (fluechtig ? "FLUECHTIG" : "MariaDB"));
    }

    @Override
    public void onDisable() {
        if (datenbank != null) {
            datenbank.close();
            datenbank = null;
        }
    }

    /**
     * Baut Lager und Kasse auf.
     *
     * @return {@code false}, wenn eine eingetragene Datenbank nicht erreichbar ist - dann
     *         soll das Plugin nicht starten
     */
    private boolean richteAblageEin() {
        String url = getConfig().getString("datenbank.url", "").trim();
        long startguthaben = getConfig().getLong("kasse.startguthaben", 50_000L);

        if (url.isEmpty()) {
            fluechtig = true;
            buch = new Besitzbuch(plan, new SpeicherLager(), new SpeicherKasse(startguthaben));
            getLogger().warning("Keine Datenbank eingetragen: Parzellen und Guthaben gelten "
                    + "nur bis zum naechsten Neustart.");
            getLogger().warning("Zum Festhalten datenbank.url in plugins/"
                    + getName() + "/config.yml ausfuellen.");
            return true;
        }

        try {
            datenbank = new Datenbank(url,
                    getConfig().getString("datenbank.benutzer", ""),
                    getConfig().getString("datenbank.passwort", ""));
            datenbank.migriere();
            // ZwischenspeicherLager um das MariaLager: darfBauen laeuft bei jedem gesetzten
            // und abgebauten Block, und das darf keine Abfrage ueber das Netz werden.
            buch = new Besitzbuch(plan,
                    new ZwischenspeicherLager(new MariaLager(datenbank)),
                    new MariaKasse(datenbank, startguthaben));
            getLogger().info("Datenbank bereit: " + ohneGeheimnis(url));
            return true;
        } catch (SQLException | RuntimeException fehler) {
            getLogger().severe("Die Datenbank ist eingetragen, antwortet aber nicht: "
                    + fehler.getMessage());
            getLogger().severe("Freimarken startet nicht. Lieber kein Server als einer, der "
                    + "Parzellen vergibt und beim Neustart vergisst.");
            if (datenbank != null) {
                datenbank.close();
                datenbank = null;
            }
            return false;
        }
    }

    /**
     * Die Adresse ohne alles hinter dem Fragezeichen.
     *
     * <p>Dort koennen Benutzer und Passwort stehen, und das Protokoll liest spaeter jemand,
     * der es nicht lesen soll.
     */
    private static String ohneGeheimnis(String url) {
        int fragezeichen = url.indexOf('?');
        return fragezeichen < 0 ? url : url.substring(0, fragezeichen);
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
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, ereignis -> {
            var verzeichnis = ereignis.registrar();
            Set<String> angemeldet = new TreeSet<>();
            angemeldet.addAll(verzeichnis.register(freimarken(),
                    "Stand, Preise und Welt des Freimarkenbunds", List.of("fm")));
            // /p und /konto stehen so im Plan (Abschnitt 11, [entschieden]); /pz bleibt
            // als zweite Kurzform, weil sie schon im Umlauf ist.
            angemeldet.addAll(verzeichnis.register(befehle.parzelle(),
                    "Parzellen nehmen, ansehen und verwalten", List.of("p", "pz")));
            angemeldet.addAll(verzeichnis.register(befehle.mark(),
                    "Dein Guthaben in Mark", List.of("konto")));

            // Diese Zeile ist nicht Zierde. /parzelle und /mark sind spielerpflichtig, und
            // auf der Konsole antwortet ein spielerpflichtiger Befehl wortgleich wie ein
            // Befehl, den es nicht gibt. Ohne diese Ausgabe laesst sich von aussen nicht
            // feststellen, ob die Anmeldung geklappt hat - und sie passiert hier, in einem
            // spaeteren Ereignis, nicht in onEnable, also beweist die Bereitmeldung es auch
            // nicht.
            getLogger().info("Befehle angemeldet: " + String.join(", ", angemeldet));
        });
    }

    /** Wer nachsehen und sich versetzen darf - nicht jeder Spieler. */
    private static final String RECHT_VERWALTEN = "freimarken.verwalten";

    private LiteralCommandNode<CommandSourceStack> freimarken() {
        return Commands.literal("freimarken")
                .executes(z -> {
                    zeigeStand(z.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("preis")
                        .then(Commands.argument("besessen",
                                        IntegerArgumentType.integer(0, 64))
                                .executes(z -> {
                                    zeigePreis(z.getSource().getSender(),
                                            IntegerArgumentType.getInteger(z, "besessen"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                // Rechte je Zweig: Stand und Preise sind fuer alle, Nachsehen und
                // Versetzen nicht. Ohne das hatte jeder Spieler einen kostenlosen Teleport
                // quer durch die Marktstadt - und das unterlaeuft die Rangleiter, an der
                // Heimatpunkte und Warps spaeter haengen sollen, bevor sie gebaut ist.
                .then(Commands.literal("welt")
                        .requires(quelle -> quelle.getSender().hasPermission(RECHT_VERWALTEN))
                        .executes(z -> {
                            zeigeWelt(z.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("parzelle")
                        .requires(quelle -> quelle.getSender().hasPermission(RECHT_VERWALTEN))
                        .then(Commands.argument("x", IntegerArgumentType.integer(-64, 64))
                                .then(Commands.argument("z",
                                                IntegerArgumentType.integer(-64, 64))
                                        .executes(z -> {
                                            bringeZurParzelle(z.getSource().getSender(),
                                                    IntegerArgumentType.getInteger(z, "x"),
                                                    IntegerArgumentType.getInteger(z, "z"));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build();
    }

    private void zeigeStand(Audience empfaenger) {
        empfaenger.sendMessage(Component.text("Der Freimarkenbund", NamedTextColor.GOLD));
        empfaenger.sendMessage(Component.text(
                "Stand " + getPluginMeta().getVersion()
                        + " · Java " + Runtime.version().feature()
                        + " · " + getServer().getMinecraftVersion(),
                NamedTextColor.GRAY));
        empfaenger.sendMessage(fluechtig
                ? Component.text("Ablage fluechtig - nichts ueberlebt den Neustart",
                        NamedTextColor.RED)
                : Component.text("Ablage MariaDB", NamedTextColor.DARK_GRAY));
        empfaenger.sendMessage(Component.text(
                "/parzelle · /mark · /freimarken preis <anzahl>", NamedTextColor.GRAY));
        if (empfaenger instanceof Permissible darf
                && darf.hasPermission(RECHT_VERWALTEN)) {
            empfaenger.sendMessage(Component.text(
                    "/freimarken welt · parzelle <x> <z>", NamedTextColor.DARK_GRAY));
        }
    }

    private void zeigePreis(Audience empfaenger, int besessen) {
        long preis = Preisstaffel.preisFuerNaechste(besessen);
        empfaenger.sendMessage(preis == 0L
                ? Component.text("Die erste Parzelle ist gratis.", NamedTextColor.GREEN)
                : Component.text("Parzelle Nr. " + (besessen + 1) + " kostet ", NamedTextColor.GRAY)
                        .append(Component.text(Betraege.mark(preis), NamedTextColor.GOLD))
                        .append(Component.text(".", NamedTextColor.GRAY)));
        empfaenger.sendMessage(Component.text(
                "Bisher ausgegeben: " + Betraege.mark(Preisstaffel.gesamtpreisFuer(besessen)),
                NamedTextColor.DARK_GRAY));
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
