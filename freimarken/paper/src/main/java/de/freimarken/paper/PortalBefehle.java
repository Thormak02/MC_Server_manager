package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Quader;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /portal} - ein Tor am Bundesmarkt setzen, ohne den Server zu stoppen.
 *
 * <p><b>Warum es diesen Befehl gibt</b> [25.11.2026]: Ein Torquader entstand bis dahin
 * nur von Hand in der {@code config.yml} - drei Zahlen zweimal, abgelesen aus der
 * F3-Anzeige, und danach ein Neustart. Wer dabei eine Ziffer vertauscht, baut ein Tor in
 * eine Wand und merkt es erst, wenn jemand hineinlaeuft und nicht reist. Hier steht der
 * Betreiber IN der Nische und merkt zwei Ecken.
 *
 * <p><b>Derselbe Riegel wie beim Start, und zwar wirklich derselbe</b>: Vor dem
 * Schreiben laeuft {@code Torbuch.pruefe} auf dem GEDACHTEN Stand. Was der Start
 * verwerfen wuerde, kommt nicht in die Datei - und die Absage nennt die Klage des
 * Programms, nicht eine zweite, hier formulierte. Zwei Pruefungen fuer dieselbe Sache
 * waeren zwei Wahrheiten, und die eine davon wuerde beim naechsten Umbau vergessen.
 *
 * <p><b>Was dieser Befehl NICHT prueft</b>: ob an der Stelle auch ein Torhaus steht.
 * Das weiss der Start ({@code FreimarkenPlugin.pruefeNische} haelt jeden Quader gegen
 * den {@code Torhausplan}), und es braucht die geladene Welt. Ein Tor, das keiner Nische
 * entspricht, ist darum erlaubt und wird beim naechsten Start beklagt - das ist Absicht:
 * Ein Betreiber, der ein Torhaus erst noch bauen will, soll das Tor vorher setzen
 * duerfen.
 */
final class PortalBefehle {

    /**
     * Wer Tore setzen darf.
     *
     * <p>Das LITERAL steht hier im Plugin und nicht im Kern - {@code GrenzeTest} 31al
     * liest die {@code "freimarken.*"}-Literale dieses Moduls und haelt sie gegen die
     * {@code plugin.yml}. Dieselbe Begruendung wie bei {@link EinsichtBefehle#RECHT}.
     */
    static final String RECHT = "freimarken.portal";

    /** Der Name des Befehls - er steht woertlich in den Saetzen der Sprachdatei. */
    static final String ARGUMENT = "portal";

    static final String ECKE = "ecke";

    static final String SETZEN = "setzen";

    static final String LISTE = "liste";

    static final String ENTFERNEN = "entfernen";

    static final String NEULADEN = "neuladen";

    static final String WELT = "welt";

    /**
     * Was dieser Befehl ueber den Stand des Servers wissen muss - eine Naht.
     *
     * <p>Drei Fragen und kein {@code FreimarkenPlugin} im Rumpf: So laeuft jeder Zweig
     * in einer Probe durch, ohne Server und ohne geladene Welten. Die Antworten aendern
     * sich im Betrieb nicht - sie kommen aus der {@code config.yml} und stehen seit dem
     * Start fest.
     */
    interface Lage {

        /** Wo die Tore stehen, etwa {@code altfurt}. */
        String ankunftswelt();

        /** Alle Welten aus {@code welten:} - in eine andere fuehrt kein Tor. */
        Set<String> welten();

        /** Die eingetragene Kante der Ankunftswelt, oder {@code null} fuer keine. */
        Integer kanteDerAnkunftswelt();
    }

    private final Torbuch torbuch;
    private final Lage lage;
    private final Sprache sprache;
    private final Logger protokoll;

    /**
     * Die erste gemerkte Ecke je Spieler - drei Zahlen.
     *
     * <p>Im Speicher und nicht in einer Datei: Ein halb gemerkter Quader ist nichts, was
     * einen Neustart ueberleben muss. Nach dem zweiten {@code /portal ecke} steht der
     * Eintrag in {@link #fertig} und hier nicht mehr.
     */
    private final Map<UUID, int[]> erste = new HashMap<>();

    /** Der fertige Quader je Spieler, bis er gesetzt oder ueberschrieben wird. */
    private final Map<UUID, Quader> fertig = new HashMap<>();

    PortalBefehle(Torbuch torbuch, Lage lage, Sprache sprache, Logger protokoll) {
        if (torbuch == null || lage == null || sprache == null || protokoll == null) {
            throw new IllegalArgumentException("/portal braucht das Torbuch, die Lage,"
                    + " Sprache und das Protokoll");
        }
        this.torbuch = torbuch;
        this.lage = lage;
        this.sprache = sprache;
        this.protokoll = protokoll;
    }

    /** Der Baum. */
    LiteralCommandNode<CommandSourceStack> portal() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                // Ein eigenes executes auf dem Wort selbst: Ohne es endet /portal in
                // Brigadiers englischem "Unknown or incomplete command". Der nackte
                // Befehl zeigt die Liste - das ist die Frage, mit der man anfaengt.
                .executes(this::zeigeListe)
                .then(Commands.literal(ECKE).executes(this::merkeEcke))
                .then(Commands.literal(LISTE).executes(this::zeigeListe))
                .then(Commands.literal(NEULADEN).executes(this::ladeNeu))
                .then(Commands.literal(SETZEN)
                        // Ein eigenes executes am Literal, sonst verdeckt es das
                        // Wortargument daneben vollstaendig und /portal setzen endet in
                        // Brigadiers englischer Meldung - gemessen am 21.11.2026 bei
                        // /warp freistadt.
                        .executes(this::setzenOhneWelt)
                        .then(Commands.argument(WELT, com.mojang.brigadier.arguments
                                        .StringArgumentType.word())
                                .suggests(this::schlageWeltenVor)
                                .executes(this::setze)))
                .then(Commands.literal(ENTFERNEN)
                        .executes(this::entfernenOhneWelt)
                        .then(Commands.argument(WELT, com.mojang.brigadier.arguments
                                        .StringArgumentType.word())
                                .suggests(this::schlageToreVor)
                                .executes(this::entferne)))
                .build();
    }

    // --- Die Ecken -------------------------------------------------------------

    /** {@code /portal ecke} - den Block merken, auf dem er steht. */
    private int merkeEcke(CommandContext<CommandSourceStack> z) {
        Player spieler = spielerVon(z);
        if (spieler == null) {
            return nurSpieler(z);
        }
        String welt = spieler.getWorld().key().value();
        if (!welt.equals(lage.ankunftswelt())) {
            // Der Riegel am ersten Klick und nicht am letzten: Die Tore stehen ALLE in
            // einer Welt, und wer in Kornmark eine Ecke merkt, hat sich verlaufen.
            sende(spieler, sprache.bau(PortalSatz.ECKE_FALSCHE_WELT, 1L,
                    Einsatz.von("welt", welt)
                            .mit("ankunftswelt", lage.ankunftswelt())));
            return Command.SINGLE_SUCCESS;
        }
        int[] hier = {spieler.getLocation().getBlockX(),
                spieler.getLocation().getBlockY(),
                spieler.getLocation().getBlockZ()};
        int[] vorher = erste.remove(spieler.getUniqueId());
        if (vorher == null) {
            erste.put(spieler.getUniqueId(), hier);
            sende(spieler, sprache.bau(PortalSatz.ERSTE_ECKE, 1L,
                    Einsatz.von("x", String.valueOf(hier[0]))
                            .mit("y", String.valueOf(hier[1]))
                            .mit("z", String.valueOf(hier[2]))));
            return Command.SINGLE_SUCCESS;
        }
        Quader quader = new Quader(vorher[0], vorher[1], vorher[2],
                hier[0], hier[1], hier[2]);
        fertig.put(spieler.getUniqueId(), quader);
        sende(spieler, sprache.bau(PortalSatz.ZWEITE_ECKE, 1L,
                Einsatz.von("von", ecke(quader.vonX(), quader.vonY(), quader.vonZ()))
                        .mit("bis", ecke(quader.bisX(), quader.bisY(), quader.bisZ()))
                        .mit("bloecke", quader.bloecke())));
        return Command.SINGLE_SUCCESS;
    }

    // --- Setzen ----------------------------------------------------------------

    private int setzenOhneWelt(CommandContext<CommandSourceStack> z) {
        return sagePortalSatz(z, PortalSatz.UNBEKANNTE_WELT, Einsatz.von("welt", "-"));
    }

    /** {@code /portal setzen <welt>} - der gemerkte Quader wird das Tor dorthin. */
    private int setze(CommandContext<CommandSourceStack> z) {
        Player spieler = spielerVon(z);
        if (spieler == null) {
            return nurSpieler(z);
        }
        String welt = z.getArgument(WELT, String.class);
        Quader quader = fertig.get(spieler.getUniqueId());
        if (quader == null) {
            sende(spieler, sprache.bau(PortalSatz.OHNE_ECKEN, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        if (!lage.welten().contains(welt)) {
            sende(spieler, sprache.bau(PortalSatz.UNBEKANNTE_WELT, 1L,
                    Einsatz.von("welt", welt)));
            return Command.SINGLE_SUCCESS;
        }
        List<String> klagen;
        try {
            klagen = torbuch.setze(welt,
                    new Torbuch.Tor("von Hand gesetzt mit /portal setzen", quader),
                    lage.ankunftswelt(), lage.welten(), lage.kanteDerAnkunftswelt());
        } catch (IOException nichtGeschrieben) {
            // Die Datei liess sich nicht schreiben. Ohne diesen Zweig waere das Tor
            // gesetzt und nach dem naechsten Start weg - und niemand wuesste, warum.
            protokoll.severe(Torbuch.DATEI + " liess sich nicht schreiben: "
                    + nichtGeschrieben.getMessage() + ". Das Tor nach " + welt
                    + " gilt bis zum naechsten Start und danach nicht mehr.");
            sende(spieler, sprache.bau(PortalSatz.NICHT_GESCHRIEBEN, 1L,
                    Einsatz.von("datei", Torbuch.DATEI)));
            return Command.SINGLE_SUCCESS;
        }
        if (!klagen.isEmpty()) {
            // Die Klage des PROGRAMMS und keine zweite, hier formulierte: Sie nennt
            // gemessene Zahlen - welches Tor sich mit welchem schneidet, wo die Grenze
            // liegt. Nur die erste; wer mehrere Fehler zugleich macht, behebt sie
            // ohnehin einen nach dem anderen.
            sende(spieler, sprache.bau(PortalSatz.ABGELEHNT, 1L,
                    Einsatz.von("grund", klagen.get(0))));
            return Command.SINGLE_SUCCESS;
        }
        fertig.remove(spieler.getUniqueId());
        protokoll.info(spieler.getName() + " hat das Tor nach " + welt + " auf "
                + quader.alsText() + " gesetzt (" + Torbuch.DATEI + ").");
        sende(spieler, sprache.bau(PortalSatz.GESETZT, 1L,
                Einsatz.von("welt", welt)
                        .mit("von", ecke(quader.vonX(), quader.vonY(), quader.vonZ()))
                        .mit("bis", ecke(quader.bisX(), quader.bisY(), quader.bisZ()))));
        return Command.SINGLE_SUCCESS;
    }

    // --- Entfernen -------------------------------------------------------------

    private int entfernenOhneWelt(CommandContext<CommandSourceStack> z) {
        return sagePortalSatz(z, PortalSatz.KEIN_TOR, Einsatz.von("welt", "-"));
    }

    /** {@code /portal entfernen <welt>}. */
    private int entferne(CommandContext<CommandSourceStack> z) {
        Player spieler = spielerVon(z);
        if (spieler == null) {
            return nurSpieler(z);
        }
        String welt = z.getArgument(WELT, String.class);
        boolean weg;
        try {
            weg = torbuch.entferne(welt);
        } catch (IOException nichtGeschrieben) {
            protokoll.severe(Torbuch.DATEI + " liess sich nicht schreiben: "
                    + nichtGeschrieben.getMessage() + ". Das Tor nach " + welt
                    + " ist bis zum naechsten Start weg und danach wieder da.");
            sende(spieler, sprache.bau(PortalSatz.NICHT_GESCHRIEBEN, 1L,
                    Einsatz.von("datei", Torbuch.DATEI)));
            return Command.SINGLE_SUCCESS;
        }
        if (!weg) {
            sende(spieler, sprache.bau(PortalSatz.KEIN_TOR, 1L,
                    Einsatz.von("welt", welt)));
            return Command.SINGLE_SUCCESS;
        }
        protokoll.info(spieler.getName() + " hat das Tor nach " + welt + " entfernt ("
                + Torbuch.DATEI + ").");
        sende(spieler, sprache.bau(PortalSatz.ENTFERNT, 1L, Einsatz.von("welt", welt)));
        return Command.SINGLE_SUCCESS;
    }

    // --- Liste und Neuladen ----------------------------------------------------

    /** {@code /portal} und {@code /portal liste}. */
    private int zeigeListe(CommandContext<CommandSourceStack> z) {
        Player spieler = spielerVon(z);
        if (spieler == null) {
            return nurSpieler(z);
        }
        if (!torbuch.hatTore()) {
            sende(spieler, sprache.bau(PortalSatz.LISTE_LEER, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        List<Component> zeilen = new ArrayList<>(sprache.bau(PortalSatz.LISTE_KOPF,
                torbuch.anzahl(), Einsatz.von("anzahl", torbuch.anzahl())));
        torbuch.alle().forEach((welt, tor) -> zeilen.addAll(
                sprache.bau(PortalSatz.LISTE_ZEILE, 1L, Einsatz.von("welt", welt)
                        .mit("von", ecke(tor.quader().vonX(), tor.quader().vonY(),
                                tor.quader().vonZ()))
                        .mit("bis", ecke(tor.quader().bisX(), tor.quader().bisY(),
                                tor.quader().bisZ()))
                        .mit("wo", tor.wo().isEmpty() ? "-" : tor.wo()))));
        sende(spieler, zeilen);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /portal neuladen} - die Datei erneut lesen.
     *
     * <p>Die Klagen gehen ins SERVERPROTOKOLL und nicht in den Chat: Sie nennen Pfade
     * und Zahlen und sind an den Betreiber am Protokoll gerichtet. Im Chat steht nur,
     * WIE VIELE es waren - das genuegt, um nachzusehen.
     */
    private int ladeNeu(CommandContext<CommandSourceStack> z) {
        Player spieler = spielerVon(z);
        if (spieler == null) {
            return nurSpieler(z);
        }
        List<String> klagen = new ArrayList<>(torbuch.lade());
        klagen.addAll(torbuch.pruefeUndVerwirf(lage.ankunftswelt(), lage.welten(),
                lage.kanteDerAnkunftswelt()));
        klagen.forEach(protokoll::severe);
        if (!klagen.isEmpty()) {
            sende(spieler, sprache.bau(PortalSatz.NEULADEN_KLAGT, klagen.size(),
                    Einsatz.von("anzahl", klagen.size())));
            return Command.SINGLE_SUCCESS;
        }
        sende(spieler, sprache.bau(PortalSatz.NEU_GELESEN, torbuch.anzahl(),
                Einsatz.von("anzahl", torbuch.anzahl())));
        return Command.SINGLE_SUCCESS;
    }

    // --- Vorschlaege -----------------------------------------------------------

    /**
     * Die Welten, in die ein Tor fuehren KANN.
     *
     * <p>Ohne die Ankunftswelt: Ein Tor dorthin waere ein Tor nach nebenan, und
     * {@code Torbuch.pruefe} verwirft es. Was abgelehnt wird, braucht nicht angeboten
     * zu werden.
     */
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            schlageWeltenVor(CommandContext<CommandSourceStack> z,
                             com.mojang.brigadier.suggestion.SuggestionsBuilder bauer) {
        String angefangen = bauer.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (String welt : lage.welten()) {
            if (!welt.equals(lage.ankunftswelt()) && welt.startsWith(angefangen)) {
                bauer.suggest(welt);
            }
        }
        return bauer.buildFuture();
    }

    /** Die Welten, in die ein Tor fuehrt - fuer {@code /portal entfernen}. */
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            schlageToreVor(CommandContext<CommandSourceStack> z,
                           com.mojang.brigadier.suggestion.SuggestionsBuilder bauer) {
        String angefangen = bauer.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (String welt : torbuch.namen()) {
            if (welt.startsWith(angefangen)) {
                bauer.suggest(welt);
            }
        }
        return bauer.buildFuture();
    }

    // --- Kleinzeug -------------------------------------------------------------

    /** Drei Zahlen als Wort - an EINER Stelle, damit sie ueberall gleich aussehen. */
    private static String ecke(int x, int y, int z) {
        return x + "/" + y + "/" + z;
    }

    private int sagePortalSatz(CommandContext<CommandSourceStack> z, PortalSatz satz,
                               Einsatz werte) {
        Player spieler = spielerVon(z);
        if (spieler == null) {
            return nurSpieler(z);
        }
        sende(spieler, sprache.bau(satz, 1L, werte));
        return Command.SINGLE_SUCCESS;
    }

    private int nurSpieler(CommandContext<CommandSourceStack> z) {
        for (Component zeile : sprache.bau(PortalSatz.NUR_SPIELER, 1L,
                Einsatz.keine())) {
            z.getSource().getSender().sendMessage(zeile);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static Player spielerVon(CommandContext<CommandSourceStack> z) {
        return z.getSource().getSender() instanceof Player spieler ? spieler : null;
    }

    /** Die EINE Sendestelle dieser Klasse. */
    private void sende(Player an, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            an.sendMessage(zeile);
        }
    }
}
