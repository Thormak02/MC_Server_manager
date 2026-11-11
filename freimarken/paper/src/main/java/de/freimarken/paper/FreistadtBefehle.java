package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Kasse;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.function.IntSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /freistadt} - die Liste der Staedte und das Gruenden einer eigenen.
 *
 * <p>Geschnitten wie {@link LadenBefehle}: kein Wort im Programm (alles ueber
 * {@link Freistadttext} und {@link FreistadtSatz}), keine Entscheidung im Programm (alles
 * im {@link Freistadtbuch}), und genau EINE private {@link #sende} Stelle, durch die
 * jeder Zweig hinausgeht.
 *
 * <p><b>Warum das Gruenden hier liegt und nicht unter {@code /warp}:</b> Es ist eine
 * Verwaltungshandlung ueber 400.000 Mark und kein Reiseweg. Der Weg IN eine Stadt heisst
 * dagegen {@code /warp freistadt <name>} - so steht es an vier Stellen im Quelltext und
 * in der ausgelieferten {@code config.yml}, und ein eigener Reisebefehl machte aus vier
 * Zusagen vier Luegen.
 *
 * <p><b>Zwei Stufen beim Gruenden</b>, wie bei {@code /laden mieten}: Erst sagt der
 * Befehl, was es kostet und was danach uebrig bleibt, dann gruendet
 * {@code bestaetigen}. Eine Stadt ist das teuerste, was ein Spieler kaufen kann; ein
 * Vertipper im Namen darf nicht 400.000 Mark kosten.
 */
final class FreistadtBefehle {

    /** Der Name des Befehls. */
    static final String ARGUMENT = "freistadt";

    /** Der Name des Arguments von {@code gruenden}. */
    static final String ARGUMENT_NAME = "name";

    /**
     * Das Recht fuer {@code /freistadt} - die Liste.
     *
     * <p>Vorgabe fuer alle, wie bei {@code /laden}: Die Liste ist eine Auskunft. Wer
     * wissen will, welche Staedte es gibt, soll das koennen, bevor er spart.
     */
    static final String RECHT = "freimarken.freistadt";

    /**
     * Das Recht, eine Stadt zu GRUENDEN - ein eigenes.
     *
     * <p>Dieselbe Entscheidung wie bei {@code freimarken.laden.mieten}: Die Liste ist
     * eine Auskunft, das Gruenden bewegt Geld. Vorgabe fuer alle, bis Schritt 12 den Rang
     * Buerger daran haengt - und zwar DARAN und nicht als zweite Pruefung daneben.
     */
    static final String RECHT_GRUENDEN = "freimarken.freistadt.gruenden";

    private final Freistadtbuch buch;
    private final Kasse kasse;
    private final Sprache sprache;
    private final String weltschluessel;
    private final IntSupplier hoechstzahl;

    /**
     * @param weltschluessel die Welt der Freistaedte, aus {@code freistaedte.welt}, schon
     *                       umgewandelt. Leer heisst: Die Welt fehlt, und dann sagt jeder
     *                       Zweig das - statt in einer namenlosen Welt zu gruenden
     * @param hoechstzahl    wie viele Staedte erlaubt sind - beim GEBRAUCH gefragt und
     *                       nicht kopiert, damit ein Neuladen die Zahl erreicht. Sie
     *                       steht in der Absage, und eine veraltete Zahl darin waere eine
     *                       Absage, die sich nicht nachrechnen laesst
     */
    FreistadtBefehle(Freistadtbuch buch, Kasse kasse, Sprache sprache,
                     String weltschluessel, IntSupplier hoechstzahl) {
        this.buch = buch;
        this.kasse = kasse;
        this.sprache = sprache;
        this.weltschluessel = weltschluessel == null ? "" : weltschluessel;
        this.hoechstzahl = hoechstzahl;
    }

    LiteralCommandNode<CommandSourceStack> freistadt() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(this::zeigeListe)
                .then(Commands.literal("liste")
                        .executes(this::zeigeListe))
                .then(Commands.literal("gruenden")
                        .requires(quelle -> quelle.getSender()
                                .hasPermission(RECHT_GRUENDEN))
                        .then(Commands.argument(ARGUMENT_NAME, StringArgumentType.word())
                                // KEIN Vorschlagsgeber: Der Name ist frei erfunden. Ein
                                // Vorschlag waere hier eine Behauptung - und die
                                // vergebenen Namen vorzuschlagen hiesse, bei jedem
                                // Tastendruck in die Ablage zu sehen.
                                .executes(this::frageNach)
                                .then(Commands.literal("bestaetigen")
                                        .executes(this::gruende))))
                .build();
    }

    /**
     * Der nackte Befehl und {@code liste} tun DASSELBE.
     *
     * <p>Kein zweiter Weg zu einer zweiten Antwort - dieselbe Entscheidung wie bei
     * {@code /laden}. Der Zweitname steht da, damit ihn findet, wer ihn sucht.
     */
    private int zeigeListe(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (weltschluessel.isEmpty()) {
            sende(spieler, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        sende(spieler, Freistadttext.listeZeilen(sprache, buch.staedte(weltschluessel),
                Spielername::von));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die erste Stufe: fragen, was es kostet.
     *
     * <p>Hier wird NICHTS geprueft, was das Buch prueft - kein zweiter Ort fuer die
     * Namensregel. Wer sich vertippt, liest die Absage erst nach {@code bestaetigen},
     * und das ist richtig: Eine Rueckfrage, die schon absagt, ist keine Rueckfrage.
     */
    private int frageNach(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (weltschluessel.isEmpty()) {
            sende(spieler, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        String getippt = StringArgumentType.getString(zusammenhang, ARGUMENT_NAME);
        sende(spieler, Freistadttext.frageZeilen(sprache, getippt, buch.preis(),
                kasse.guthaben(spieler.getUniqueId())));
        return Command.SINGLE_SUCCESS;
    }

    /** Die zweite Stufe: gruenden. Alles Weitere entscheidet das Buch. */
    private int gruende(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (weltschluessel.isEmpty()) {
            sende(spieler, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        String getippt = StringArgumentType.getString(zusammenhang, ARGUMENT_NAME);
        int erlaubt = hoechstzahl.getAsInt();
        // Das Guthaben VOR dem Versuch, denn danach ist es abgebucht: Die Absage
        // "zu wenig Mark" nennt, was fehlt, und das rechnet sich nur gegen den Stand
        // von vorher.
        long guthaben = kasse.guthaben(spieler.getUniqueId());
        Freistadtbuch.Entscheid entscheid =
                buch.gruende(weltschluessel, spieler.getUniqueId(), getippt, erlaubt);
        sende(spieler, Freistadttext.antwortZeilen(sprache, entscheid, getippt,
                erlaubt, guthaben));
        return Command.SINGLE_SUCCESS;
    }

    private static Player spielerAus(CommandContext<CommandSourceStack> zusammenhang) {
        // Spielerpflichtig wie /laden: Die Zeilen sind fuer ein Chatfenster gemacht, und
        // gegruendet wird fuer jemanden, dem Geld gehoert.
        return zusammenhang.getSource().getExecutor() instanceof Player spieler
                ? spieler
                : null;
    }

    private static void sende(Player wer, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            wer.sendMessage(zeile);
        }
    }
}
