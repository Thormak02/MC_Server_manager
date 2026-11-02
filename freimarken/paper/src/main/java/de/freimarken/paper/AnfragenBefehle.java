package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * {@code /tpa}, {@code /tpaccept} und {@code /tpdeny} - Teleport zu einem Spieler, mit
 * seiner Zustimmung.
 *
 * <p>{@code /tpahere <spieler>} fragt in die andere Richtung: Dann kommt der Eingeladene,
 * und er ist auch der, der zustimmt. Darin liegt die Trennlinie zum Missbrauch - nicht in
 * der Richtung, sondern darin, dass <b>wer bewegt wird, zustimmt</b>. Was es nicht gibt,
 * ist {@code /tpaall}: ein Befehl, der alle zieht, wird von einem Einzigen benutzt und von
 * allen anderen erduldet.
 *
 * <p><b>Der Zielort wird erst beim Annehmen geholt</b>, nicht beim Fragen. Damit ist er
 * frisch: Der Angefragte kann in der Minute dazwischen weitergelaufen sein, und sein
 * Chunk ist geladen, weil er darin steht.
 *
 * <p>Geprueft wird nichts hier - jede Absage kommt aus {@link Reise}. Auch die
 * Abklingzeit: Sie gilt fuer Reisen, Rueckwege und Besuche gemeinsam, und das ist
 * richtig, weil es dieselbe Platte ist, die nachladen muss.
 */
final class AnfragenBefehle {

    /** Wer fragen und annehmen darf. Vorgabe: jeder - es gibt nichts zu rationieren. */
    static final String RECHT_TPA = "freimarken.tpa";

    private final Anfragen anfragen;
    private final Reise reise;

    AnfragenBefehle(Anfragen anfragen, Reise reise) {
        this.anfragen = anfragen;
        this.reise = reise;
    }

    LiteralCommandNode<CommandSourceStack> tpa() {
        return frageBefehl("tpa", Anfragen.Richtung.ZU_DIR);
    }

    LiteralCommandNode<CommandSourceStack> tpahere() {
        return frageBefehl("tpahere", Anfragen.Richtung.HIERHER);
    }

    /**
     * Beide Fragebefehle, aus einer Hand.
     *
     * <p>{@code /tpa} und {@code /tpahere} unterscheiden sich in einem Wort - der
     * Richtung. Zwei Methoden daraus zu machen hiesse, dass eines Tages die eine einen
     * Vorschlagsgeber hat und die andere nicht.
     */
    private LiteralCommandNode<CommandSourceStack> frageBefehl(String befehlsname,
                                                               Anfragen.Richtung richtung) {
        return Commands.literal(befehlsname)
                .requires(diesesIstEinSpieler())
                .then(Commands.argument("spieler", StringArgumentType.word())
                        .suggests((zusammenhang, bauer) -> {
                            // Kein ArgumentTypes.player(): Der vanillanahe Typ nimmt
                            // Selektoren wie @a, verraet damit Unsichtbare und meldet
                            // englisch. Hier werden nur sichtbare Namen vorgeschlagen -
                            // und der eigene nie.
                            if (zusammenhang.getSource().getExecutor() instanceof Player ich) {
                                String angefangen = bauer.getRemainingLowerCase();
                                Bukkit.getOnlinePlayers().stream()
                                        .filter(anderer -> !anderer.equals(ich))
                                        .filter(ich::canSee)
                                        .map(Player::getName)
                                        .filter(name -> name.toLowerCase(java.util.Locale.ROOT)
                                                .startsWith(angefangen))
                                        .forEach(bauer::suggest);
                            }
                            return bauer.buildFuture();
                        })
                        .executes(z -> {
                            frage(z.getSource().getExecutor(),
                                    StringArgumentType.getString(z, "spieler"), richtung);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    LiteralCommandNode<CommandSourceStack> tpaccept() {
        return Commands.literal("tpaccept")
                .requires(diesesIstEinSpieler())
                .executes(z -> {
                    nimmAn(z.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    LiteralCommandNode<CommandSourceStack> tpdeny() {
        return Commands.literal("tpdeny")
                .requires(diesesIstEinSpieler())
                .executes(z -> {
                    lehneAb(z.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private static java.util.function.Predicate<CommandSourceStack> diesesIstEinSpieler() {
        return quelle -> quelle.getExecutor() instanceof Player
                && quelle.getSender().hasPermission(RECHT_TPA);
    }

    private void frage(Object fragenderRoh, String name, Anfragen.Richtung richtung) {
        if (!(fragenderRoh instanceof Player fragender)) {
            return;
        }
        // getPlayerExact und nicht getPlayer: Letzteres raet den naechstbesten Namen, und
        // dann fragt /tpa Max unversehens Maxime.
        Player angefragter = Bukkit.getPlayerExact(name);
        if (angefragter == null || !fragender.canSee(angefragter)) {
            fragender.sendMessage(Component.text(name + " ist nicht da.",
                    NamedTextColor.RED));
            return;
        }
        if (angefragter.equals(fragender)) {
            fragender.sendMessage(Component.text("Du bist schon bei dir.",
                    NamedTextColor.GRAY));
            return;
        }
        if (!anfragen.stellen(fragender.getUniqueId(), angefragter.getUniqueId(),
                richtung, System.nanoTime())) {
            fragender.sendMessage(Component.text("Diese Anfrage an "
                    + angefragter.getName() + " steht noch.", NamedTextColor.GRAY));
            return;
        }
        fragender.sendMessage(Component.text("Angefragt bei " + angefragter.getName()
                + " - er oder sie muss zustimmen.", NamedTextColor.GRAY));
        angefragter.sendMessage(Component.text(fragender.getName()
                + (richtung == Anfragen.Richtung.HIERHER
                        ? " laedt dich ein zu kommen."
                        : " moechte zu dir kommen."), NamedTextColor.GOLD));
        angefragter.sendMessage(Component.text("/tpaccept zum Annehmen, /tpdeny zum"
                + " Ablehnen. Die Anfrage laeuft nach einer Minute ab.",
                NamedTextColor.GRAY));
    }

    private void nimmAn(Object annehmerRoh) {
        if (!(annehmerRoh instanceof Player annehmer)) {
            return;
        }
        var anfrage = anfragen.annehmen(annehmer.getUniqueId(), System.nanoTime());
        if (anfrage.isEmpty()) {
            annehmer.sendMessage(Component.text("Es fragt niemand.", NamedTextColor.GRAY));
            return;
        }
        Player fragender = Bukkit.getPlayer(anfrage.get().von());
        if (fragender == null) {
            annehmer.sendMessage(Component.text("Wer gefragt hatte, ist nicht mehr da.",
                    NamedTextColor.RED));
            return;
        }
        // Wer wandert und wer bleibt, steht in der Anfrage - nicht im Befehl. So muss
        // /tpaccept die Richtung nicht erraten, und es gibt nur eine Stelle, an der sie
        // entschieden wird.
        boolean annehmerWandert = anfrage.get().wandert().equals(annehmer.getUniqueId());
        Player wandert = annehmerWandert ? annehmer : fragender;
        Player bleibt = annehmerWandert ? fragender : annehmer;

        annehmer.sendMessage(Component.text(annehmerWandert
                        ? "Du kommst zu " + fragender.getName() + "."
                        : fragender.getName() + " kommt zu dir.",
                NamedTextColor.GREEN));
        if (!annehmerWandert) {
            fragender.sendMessage(Component.text(annehmer.getName()
                    + " hat angenommen - du kommst.", NamedTextColor.GREEN));
        } else {
            fragender.sendMessage(Component.text(annehmer.getName()
                    + " hat angenommen und kommt zu dir.", NamedTextColor.GREEN));
        }
        long weitere = anfragen.offeneFuer(annehmer.getUniqueId(), System.nanoTime());
        if (weitere > 0) {
            annehmer.sendMessage(Component.text("Es warten noch " + weitere
                    + (weitere == 1 ? " Anfrage." : " Anfragen."), NamedTextColor.GRAY));
        }
        // Der Ort erst jetzt geholt: Der Bleibende kann in der Minute seit der Anfrage
        // weitergelaufen sein, und sein Chunk ist geladen, weil er darin steht.
        reise.fuehre(wandert, bleibt.getLocation(), bleibt.getName());
    }

    private void lehneAb(Object annehmerRoh) {
        if (!(annehmerRoh instanceof Player annehmer)) {
            return;
        }
        long weg = anfragen.ablehnenAlle(annehmer.getUniqueId(), System.nanoTime());
        annehmer.sendMessage(weg == 0
                ? Component.text("Es fragt niemand.", NamedTextColor.GRAY)
                : Component.text(weg + (weg == 1 ? " Anfrage" : " Anfragen")
                        + " abgelehnt.", NamedTextColor.GRAY));
    }
}
