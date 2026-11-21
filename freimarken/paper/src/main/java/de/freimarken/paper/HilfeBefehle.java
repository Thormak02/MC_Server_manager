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

/**
 * {@code /hilfe} - alle Befehle, nach Bereichen geordnet und nach Rechten gefiltert.
 *
 * <p>Plan 11.1 fuehrt den Befehl, Plan 10 nennt den Grund: Papers eigenes {@code /help}
 * zeigt die Befehle JEDES Plugins in einer alphabetischen Wand, und ein Neuling findet
 * darin nichts. Diese Liste ist nach Bereichen geordnet und zeigt nur, was der Spieler
 * wirklich tippen darf.
 *
 * <p><b>Gefiltert wird mit dem Recht, das der Befehl selbst fragt</b> (siehe
 * {@link Hilfeplan}). Eine Liste, die etwas anbietet, das dann abgelehnt wird, waere
 * schlimmer als keine: Sie macht aus einer fehlenden Erlaubnis einen scheinbaren Fehler.
 * Eine Gruppe, von der nichts uebrig bleibt, verschwindet mit ihrer Ueberschrift.
 *
 * <p><b>Jede Zeile kommt ueber {@code sprache.bau} und nie ueber
 * {@code sprache.eineZeile}</b>, und das ist hier wichtiger als sonst: In jede Zeile wird
 * eine Befehlsbeschreibung EINGESETZT, und die gehoert dem Betreiber. Schreibt er eine
 * Umbruchmarke hinein, wird daraus eine zweite Zeile - und nicht ein Wurf mitten in der
 * Liste, der dem Spieler den Rest nimmt.
 */
final class HilfeBefehle {

    /** Wer die Hilfe lesen darf - jeder. Angemeldet mit {@code default: true}. */
    static final String RECHT = "freimarken.hilfe";

    /** Der Name des Befehls - er steht woertlich in den Saetzen der Sprachdatei. */
    private static final String ARGUMENT = "hilfe";

    private final Sprache sprache;

    HilfeBefehle(Sprache sprache) {
        if (sprache == null) {
            throw new IllegalArgumentException("/hilfe braucht Sprache");
        }
        this.sprache = sprache;
    }

    /** Der Baum: nur {@code /hilfe}, ohne Unterbefehl. */
    LiteralCommandNode<CommandSourceStack> hilfe() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(this::zeige)
                .build();
    }

    private int zeige(CommandContext<CommandSourceStack> zusammenhang) {
        var leser = zusammenhang.getSource().getSender();
        sende(leser, zeilen(leser::hasPermission));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die Liste fuer diesen Leser.
     *
     * <p>Paketweit sichtbar, damit die Probe sie fuer jede Rechtelage bauen kann - ohne
     * Server, ohne Spieler, ohne Brigadier.
     */
    List<Component> zeilen(Rechtefrage darf) {
        List<Component> zeilen = new ArrayList<>();
        zeilen.addAll(sprache.bau(HilfeSatz.KOPF, 1L, Einsatz.keine()));
        for (Hilfeplan.Gruppe gruppe : Hilfeplan.ALLE.gruppen()) {
            List<Component> eintraege = new ArrayList<>();
            for (Hilfeplan.Eintrag eintrag : gruppe.eintraege()) {
                if (!eintrag.fuerJeden() && !darf.hat(eintrag.recht())) {
                    continue;
                }
                eintraege.addAll(sprache.bau(HilfeSatz.ZEILE, 1L,
                        Einsatz.von("befehl", eintrag.befehl())
                                .mit("beschreibung", sprache.wort(eintrag.beschreibung(),
                                        Einsatz.keine()))));
            }
            if (eintraege.isEmpty()) {
                // Die Ueberschrift kommt NACH ihren Zeilen - erst wenn eine uebrig ist.
                continue;
            }
            zeilen.addAll(sprache.bau(gruppe.name(), 1L, Einsatz.keine()));
            zeilen.addAll(eintraege);
        }
        if (!sprache.leerGelassen(HilfeSatz.FUSS)) {
            zeilen.addAll(sprache.bau(HilfeSatz.FUSS, 1L, Einsatz.keine()));
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
