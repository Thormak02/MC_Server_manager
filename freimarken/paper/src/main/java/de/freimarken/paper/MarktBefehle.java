package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Wirtschaftsstand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Clock;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Der Befehl {@code /markt} - das Sortiment ansehen.
 *
 * <p><b>Kein Wort steht hier.</b> Jede Zeile kommt aus {@link Markttext}, ihr Wortlaut aus
 * {@code sprache-vorlage.yml} und ihr Vertrag aus {@link MarktSatz} - derselbe Griff wie
 * bei {@code BerufeBefehle} und {@code ParzellenBefehle}.
 *
 * <p><b>{@code verkaufen} ist am 10.11.2026 weggefallen</b> [entschieden 08.11.2026].
 * Verkauft wird am SCHILD: Ein Bundshop, der ankauft, IST der Bundesmarkt - mit
 * demselben Tagesdeckel, derselben Tafel und derselben Buchung wie dieser Befehl sie
 * hatte ({@code Bundankauf}). Der Grund ist keiner der Technik und steht im Plan (7.1):
 * Ein Markt, den man von ueberall bedienen kann, ist ein Menue. Wer einmal weiss, dass
 * der Befehl geht, geht nie wieder hin - und damit ist das gebaute Gebaeude Zierde, und
 * niemand trifft dort jemanden.
 *
 * <p><b>Damit ist der Bundesmarkt erst wirklich ein ORT.</b> Vorher stand hier eine
 * WELTPRUEFUNG, die das nachbilden sollte: Wer nicht in Altfurt war, bekam eine Absage.
 * Sie ist mit dem Zweig gegangen, und zwar nicht aus Nachlaessigkeit - ein Schild steht,
 * wo es steht, und das ist eine schaerfere Ortsangabe als jede Weltpruefung. Das Wort
 * {@code reisen.ankunftswelt} braucht dieser Befehl seither nicht mehr.
 *
 * <p><b>Die LISTE darf man ueberall ansehen</b>, und das war schon vorher so: Sie nennt
 * nur Preise, und wer wissen will, ob sich der Weg lohnt, soll nicht erst hingehen
 * muessen. Dazu sagt sie, was heute noch zu holen ist - dieselbe Zahl, mit der ein
 * Ankaufstand rechnet, und darum aus derselben Stelle ({@link Markttag}).
 */
final class MarktBefehle {

    /** Wer das Sortiment des Bundesmarktes ansehen darf - jeder. */
    static final String RECHT = "freimarken.markt";

    /** Das Wort, unter dem der Befehl steht. */
    static final String ARGUMENT = "markt";

    private final Wirtschaftsstand wirtschaft;
    private final Kasse kasse;
    private final Clock uhr;
    private final Sprache sprache;

    /**
     * <b>Kein Weltschluessel und kein Protokoll mehr</b> [10.11.2026]. Jener stand hier
     * fuer die Pruefung "bist du in Altfurt?" und ist mit {@code verkaufen} gegangen:
     * Der Bundesmarkt ist jetzt ein Ort, weil seine Schilder dort stehen - siehe den
     * Klassenkopf. Und das Protokoll, weil dieser Befehl nichts mehr bucht, was
     * scheitern koennte; ein Feld, das niemand mehr liest, waere eine Zusage, die
     * niemand haelt.
     */
    MarktBefehle(Wirtschaftsstand wirtschaft, Kasse kasse, Clock uhr, Sprache sprache) {
        if (wirtschaft == null || kasse == null || uhr == null || sprache == null) {
            throw new IllegalArgumentException("Wirtschaftsstand, Kasse, Uhr und Sprache"
                    + " sind Pflicht");
        }
        this.wirtschaft = wirtschaft;
        this.kasse = kasse;
        this.uhr = uhr;
        this.sprache = sprache;
    }

    /**
     * Der Befehlsbaum - EIN Zweig, und er braucht einen Spieler.
     *
     * <p>{@code liste} steht zweimal da, als Wort und als blosses {@code /markt}: Das
     * Wort gehoert in die Vervollstaendigung, damit sichtbar ist, was es gibt, und
     * {@code /markt} allein soll nicht mit einer Hilfe antworten, wenn es nur eine
     * Antwort gibt.
     */
    LiteralCommandNode<CommandSourceStack> markt() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(this::zeigeListe)
                .then(Commands.literal("liste")
                        .executes(this::zeigeListe))
                .build();
    }

    /**
     * Zeigt das Sortiment - und was heute noch zu holen ist.
     *
     * <p>Der SPIELER ist Pflicht, obwohl die Liste nichts von ihm braucht: Die zweite
     * Zeile nennt seinen heutigen Rest, und ein Rest ohne Spieler ist keine Zahl. Eine
     * eigene uebersetzte Zeile dafuer gibt es nicht - wer auf der Konsole tippt, ist der
     * Betreiber.
     */
    private int zeigeListe(CommandContext<CommandSourceStack> zusammenhang) {
        if (!(zusammenhang.getSource().getSender() instanceof Player wer)) {
            zusammenhang.getSource().getSender().sendMessage(
                    Component.text("Der Bundesmarkt braucht einen Spieler."));
            return Command.SINGLE_SUCCESS;
        }
        sende(wer, Markttext.listeZeilen(sprache, wirtschaft.werte().markt(),
                heuteSchon(wer)));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Was dieser Spieler heute schon aus Verkaeufen an den Bund bekommen hat.
     *
     * <p>Durch {@link Markttag} und nicht selbst gerechnet - seit dem 10.11.2026
     * brauchen ZWEI Stellen diese Zahl: Diese Liste verspricht sie dem Spieler, und der
     * Ankaufstand am Schild entscheidet damit, wie viel er noch nimmt. Zwei Abschriften
     * derselben Abfrage liefen auseinander, und dann verspricht die Liste etwas, das der
     * Stand nicht hergibt.
     */
    private long heuteSchon(Player wer) {
        return Markttag.heuteSchon(kasse, wirtschaft, uhr, wer.getUniqueId());
    }

    private void sende(Player wer, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            wer.sendMessage(zeile);
        }
    }
}
