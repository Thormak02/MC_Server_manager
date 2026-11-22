package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import de.freimarken.kern.Anmeldung;
import org.bukkit.entity.Player;

/**
 * {@code /regeln} - was auf diesem Server gilt.
 *
 * <p>Plan 11.1 fuehrt den Befehl, Plan 10.1 nennt seinen Zweck: "Regeln und
 * Nutzungsbedingungen werden als Formular angenommen, nicht als Textwand weggeklickt."
 * Die ANNAHME kommt mit dem Buergeramt; hier steht das LESEN - und zwar schon in der
 * Form, in der die Annahme sie braucht.
 *
 * <p><b>Der TEXT steht in der Sprachdatei</b>, nicht in der {@code config.yml}: Es ist
 * Text, den ein Spieler liest, und dafuer hat dieses Plugin eine Datei, einen Leser und
 * einen Neuladeweg. Ein Server mit anderen Regeln aendert EINEN Satz
 * ({@code regeln.text}, mit Umbruchmarken fuer die Zeilen) und tippt
 * {@code /freimarken neuladen}. Eine Liste von Zeilen waere es nicht geworden: Jeder
 * Schluessel dieser Datei muss zu einem Satz des Programms gehoeren, und eine Liste
 * haette eine Zahl von Schluesseln, die der Betreiber bestimmt.
 *
 * <p><b>Zwei Formen, und beide sind noetig.</b> Ein Spieler bekommt das FENSTER - die
 * Form, die Plan 10.1 fuer offizielle Texte verlangt, und sie haelt das Spiel an,
 * waehrend er liest. Die Konsole bekommt die Zeilen im Chat, und ein Spieler bekommt sie
 * auch: {@code /regeln chat} - wer den Text kopieren will, braucht ihn dort.
 *
 * <p><b>Der Chatweg ist auch der RUECKFALL, aber nur fuer einen Fehler auf dem SERVER</b>
 * [21.11.2026]. Gibt {@link Formularhand#zeige} {@code false}, kommen dieselben Zeilen in
 * den Chat. Ein alter CLIENT loest das nicht aus: {@code showDialog} sendet und fragt
 * nicht, und ein verworfenes Paket merkt der Server nicht. Hier stand das Gegenteil
 * zugesagt. Sein Ausweg ist {@code /regeln chat}, und damit er ihn findet, nennt die
 * Beschreibung des Befehls ihn - die eine Zeile, die auf jedem Client in der Befehlsliste
 * steht.
 */
final class RegelnBefehle {

    /** Wer die Regeln lesen darf - jeder. Angemeldet mit {@code default: true}. */
    static final String RECHT = "freimarken.regeln";

    /**
     * Der Name des Befehls - er steht woertlich in den Saetzen der Sprachdatei.
     *
     * <p>Paketweit sichtbar, weil {@link Anmeldeformular} daraus den Befehl seines
     * Knopfes baut. Zwei getippte Zeichenketten liefen auseinander, und der Knopf endete
     * in Brigadiers englischer Meldung.
     */
    static final String ARGUMENT = "regeln";

    /** Der Zweig, der die Zeilen in den Chat holt statt in ein Fenster. */
    private static final String IM_CHAT = "chat";

    /**
     * Der Zweig, der die Regeln ANNIMMT - und der Weg ohne Fenster.
     *
     * <p>Paketweit sichtbar, aus demselben Grund wie {@link #ARGUMENT}: Der Knopf des
     * Formulars tippt ihn.
     */
    static final String ANNEHMEN = "annehmen";

    /**
     * Was ein Formular zeigt - die Naht, siehe {@link Formularhand}.
     *
     * <p>FERTIGE Components und keine Saetze: Das Holen bleibt hier, wo jede Probe es
     * sieht. Gibt die Naht {@code false} zurueck, gehen dieselben Zeilen in den Chat.
     */
    interface Formularzeiger {
        boolean zeige(Player wer, Component titel, List<Component> text, Component knopf);
    }

    private final Formularzeiger zeiger;
    private final Sprache sprache;
    private final Anmeldung anmeldung;
    private final Anmeldeformular formular;

    RegelnBefehle(Formularzeiger zeiger, Sprache sprache, Anmeldung anmeldung,
            Anmeldeformular formular) {
        if (zeiger == null || sprache == null || anmeldung == null || formular == null) {
            throw new IllegalArgumentException("/regeln braucht einen Zeiger, Sprache,"
                    + " die Anmeldung und ihr Formular");
        }
        this.zeiger = zeiger;
        this.sprache = sprache;
        this.anmeldung = anmeldung;
        this.formular = formular;
    }

    /**
     * Der Baum: {@code /regeln} und {@code /regeln chat}.
     *
     * <p>Beide Knoten haben ihr eigenes {@code executes} - ohne das endet
     * {@code /regeln chat} in Brigadiers englischem "Unknown or incomplete command".
     */
    LiteralCommandNode<CommandSourceStack> regeln() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(zusammenhang -> zeige(zusammenhang, false))
                .then(Commands.literal(IM_CHAT)
                        .executes(zusammenhang -> zeige(zusammenhang, true)))
                .then(Commands.literal(ANNEHMEN)
                        .executes(this::nimmAn))
                .build();
    }

    /**
     * {@code /regeln annehmen} - die Zustimmung, getippt oder geklickt.
     *
     * <p><b>Kein eigenes Recht und keine Spielerpflicht als {@code requires}</b>: Der
     * Zweig haengt am Recht der Wurzel ({@code freimarken.regeln}, fuer jeden), und dass
     * die Konsole nicht zustimmen kann, ist eine gelesene Absage und keine verborgene
     * Tuer. Ein Betreiber, der den Zweig nicht findet, weil sein Knoten verborgen ist,
     * sucht an der falschen Stelle.
     *
     * <p><b>Angenommen wird die GELTENDE Fassung</b>, und das entscheidet
     * {@link Anmeldung#nimmAn} - nicht dieser Zweig. Ein Parameter dafuer waere eine
     * Tuer, durch die jemand einer alten Fassung zustimmen koennte.
     */
    private int nimmAn(CommandContext<CommandSourceStack> zusammenhang) {
        if (!(zusammenhang.getSource().getSender() instanceof Player wer)) {
            // Die Konsole stimmt nichts zu: Eine Zustimmung ist die Erklaerung eines
            // Menschen, und der Betreiber ist nicht jeder Spieler. Gelesen und nicht
            // verborgen - siehe oben.
            sende(zusammenhang.getSource().getSender(),
                    sprache.bau(AnmeldeSatz.NUR_SPIELER, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        if (!anmeldung.an()) {
            // AUS heisst: Es gibt nichts anzunehmen. Ohne diese Zeile wuerfe
            // Anmeldung.nimmAn - ein Wurf mitten im Befehl, und der Spieler lase eine
            // rote englische Zeile fuer eine Lage, die der Betreiber eingestellt hat.
            sende(wer, sprache.bau(AnmeldeSatz.NICHT_VERLANGT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        if (anmeldung.nimmAn(wer.getUniqueId())) {
            sende(wer, sprache.bau(AnmeldeSatz.ANGENOMMEN, 1L, Einsatz.keine()));
        } else {
            // Er hatte genau diese Fassung schon. Ein eigener Satz und nicht derselbe:
            // Eine zweite Begruessung sieht aus, als haette die erste nicht gewirkt.
            sende(wer, sprache.bau(AnmeldeSatz.SCHON_ANGENOMMEN, 1L, Einsatz.keine()));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Das Fenster, sonst der Chat - und beides an DENSELBEN Leser.
     *
     * <p>Gefragt wird {@code getSender()} und nicht {@code getExecutor()} [21.11.2026].
     * Bei {@code /execute as <spieler> run regeln} sind das zwei verschiedene: Das
     * Fenster ginge an den Gespielten, der Rueckfall an den Tippenden, und je nachdem, ob
     * das Fenster aufgeht, lase ein anderer. Es ist dieselbe Hausregel wie beim
     * Blockbuch - lesen will es der, der getippt hat.
     */
    private int zeige(CommandContext<CommandSourceStack> zusammenhang, boolean imChat) {
        // WER NOCH NICHT ANGENOMMEN HAT, bekommt das Anmeldeformular statt des
        // Lesefensters - dasselbe, das die Wache beim Beitritt zeigt, mit zwei Knoepfen.
        // Die Entscheidung faellt an EINER Stelle (Anmeldung.verlangt), und zwar
        // derselben, die auch der Bundesmarkt fragt. Auch im Chatzweig: Dort sind es
        // dieselben Zeilen plus der Hinweis, wie man ohne Fenster zustimmt.
        if (zusammenhang.getSource().getSender() instanceof Player wer
                && anmeldung.verlangt(wer.getUniqueId())) {
            if (imChat) {
                sende(wer, formular.zeilen());
            } else {
                formular.zeige(wer);
            }
            return Command.SINGLE_SUCCESS;
        }
        if (!imChat
                && zusammenhang.getSource().getSender() instanceof Player wer
                && zeiger.zeige(wer,
                        sprache.eineZeile(RegelnSatz.KOPF, Einsatz.keine()),
                        sprache.bau(RegelnSatz.TEXT, 1L, Einsatz.keine()),
                        sprache.eineZeile(RegelnSatz.KNOPF, Einsatz.keine()))) {
            return Command.SINGLE_SUCCESS;
        }
        sende(zusammenhang.getSource().getSender(), zeilen());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die Regeln als Chatzeilen - Kopf, der Text mit seinen Umbruechen, der Fuss.
     *
     * <p>Jede Zeile ueber {@code bau} und nicht ueber {@code eineZeile}: Der Text IST
     * mehrzeilig, und der Fuss darf es werden, ohne dass der Befehl abbricht.
     *
     * <p>Der KOPF darf es NICHT [21.11.2026]. Er ist der Fenstertitel und wird oben mit
     * {@code eineZeile} geholt; darum steht er in {@link Einzeiler#CHATZEILEN}, und
     * {@code Sprachliste} macht aus einer Umbruchmarke darin eine Klage - die ganze
     * {@code sprache.yml} wird abgewiesen, es gelten die bisherigen Saetze. Der Riegel
     * liegt also beim LADEN und nicht hier; die ausgelieferte Datei sagt es dem Betreiber
     * an dieser Zeile ("Darum GENAU EINE Zeile").
     */
    private List<Component> zeilen() {
        List<Component> zeilen = new ArrayList<>();
        zeilen.addAll(sprache.bau(RegelnSatz.KOPF, 1L, Einsatz.keine()));
        zeilen.addAll(sprache.bau(RegelnSatz.TEXT, 1L, Einsatz.keine()));
        if (!sprache.leerGelassen(RegelnSatz.FUSS)) {
            zeilen.addAll(sprache.bau(RegelnSatz.FUSS, 1L, Einsatz.keine()));
        }
        return List.copyOf(zeilen);
    }

    /** Die EINE Sendestelle dieser Klasse. */
    private void sende(Audience an, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            an.sendMessage(zeile);
        }
    }
}
