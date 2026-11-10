package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.Wirtschaftsstand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Der Befehl {@code /markt} - die Staende des Bundes ansehen.
 *
 * <p><b>Kein Wort steht hier.</b> Jede Zeile kommt aus {@link Markttext}, ihr Wortlaut aus
 * {@code sprache-vorlage.yml} und ihr Vertrag aus {@link MarktSatz} - derselbe Griff wie
 * bei {@code BerufeBefehle} und {@code ParzellenBefehle}.
 *
 * <p><b>Dieser Befehl ist am 10.11.2026 zweimal kleiner geworden und hat dabei seinen
 * Gegenstand gewechselt.</b>
 *
 * <ol>
 *   <li><b>{@code verkaufen} fiel weg</b> [entschieden 08.11.2026]: Verkauft wird am
 *       SCHILD. Ein Markt, den man von ueberall bedienen kann, ist ein Menue - wer
 *       einmal weiss, dass der Befehl geht, geht nie wieder hin, und das gebaute
 *       Gebaeude ist Zierde.</li>
 *   <li><b>Das SORTIMENT fiel weg</b> [entschieden 10.11.2026], mit einem Satz des
 *       Betreibers: "es kann ja nur ge- und verkauft werden, wofuer es ein Schild gibt."
 *       Die Tafel {@code bundesmarkt.ankauf} war ein zweites Tor hinter dem Schild und
 *       verbot genau das, was er wollte - alles freizugeben.</li>
 * </ol>
 *
 * <p>Was {@code /markt} seither zeigt, ist darum nicht mehr eine Liste aus der Datei,
 * sondern <b>die Staende selbst</b>: wo sie stehen, was sie handeln, zu welchem Preis.
 * Das ist dieselbe Auskunft, die {@code /shop liste} fuer die eigenen Laeden gibt - und
 * sie ist gebaut wie jene, samt Seiten und derselben Seitengroesse
 * ({@code handel.zeilen-je-seite}). Die Rechnung dahinter teilen beide
 * ({@code Seitenregel}): Vier der zwoelf Einspritzungen gegen {@code /shop liste} sassen
 * in genau diesen drei Zeilen, und die zweite Liste soll sie nicht neu einsammeln.
 *
 * <p><b>Der Bundesmarkt ist damit erst wirklich ein ORT.</b> Vorher stand hier eine
 * Weltpruefung, die das nachbilden sollte; sie ist mit {@code verkaufen} gegangen. Ein
 * Schild steht, wo es steht - und diese Liste sagt, wo.
 *
 * <p>Die LISTE darf man ueberall ansehen: Sie nennt Orte und Preise, und wer wissen will,
 * ob sich der Weg lohnt, soll nicht erst hingehen muessen.
 */
final class MarktBefehle {

    /** Wer die Staende des Bundesmarktes ansehen darf - jeder. */
    static final String RECHT = "freimarken.markt";

    /** Das Wort, unter dem der Befehl steht. */
    static final String ARGUMENT = "markt";

    private final Shopablage shops;
    private final Wirtschaftsstand wirtschaft;
    private final Kasse kasse;
    private final Clock uhr;
    private final Sprache sprache;
    private final Logger protokoll;

    /**
     * @param shops die Shopablage - der Bundesmarkt steht darin, nicht in der Datei.
     *              Dahinter liegt der Zwischenspeicher, also der Arbeitsspeicher und
     *              nicht das Netz
     */
    MarktBefehle(Shopablage shops, Wirtschaftsstand wirtschaft, Kasse kasse, Clock uhr,
                 Sprache sprache, Logger protokoll) {
        if (shops == null || wirtschaft == null || kasse == null || uhr == null
                || sprache == null || protokoll == null) {
            throw new IllegalArgumentException("Shopablage, Wirtschaftsstand, Kasse,"
                    + " Uhr, Sprache und Protokoll sind Pflicht");
        }
        this.shops = shops;
        this.wirtschaft = wirtschaft;
        this.kasse = kasse;
        this.uhr = uhr;
        this.sprache = sprache;
        this.protokoll = protokoll;
    }

    /**
     * Der Befehlsbaum - ein Zweig mit einer Seitenzahl.
     *
     * <p>{@code liste} steht zweimal da, als Wort und als blosses {@code /markt}: Das
     * Wort gehoert in die Vervollstaendigung, damit sichtbar ist, was es gibt, und
     * {@code /markt} allein soll nicht mit einer Hilfe antworten, wenn es nur eine
     * Antwort gibt.
     *
     * <p><b>Zwei {@code executes} am Seitenzweig</b>, und das ist keine Doppelung:
     * Brigadier kennt keine optionalen Argumente - ohne das erste waere {@code /markt
     * liste} ohne Zahl ein unbekannter Befehl. Dieselbe Form wie bei
     * {@code /shop liste}.
     */
    LiteralCommandNode<CommandSourceStack> markt() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(zusammenhang -> zeigeListe(zusammenhang, 1))
                .then(Commands.literal("liste")
                        .executes(zusammenhang -> zeigeListe(zusammenhang, 1))
                        .then(Commands.argument("seite",
                                        IntegerArgumentType.integer(1))
                                .executes(zusammenhang -> zeigeListe(zusammenhang,
                                        IntegerArgumentType.getInteger(zusammenhang,
                                                "seite")))))
                .build();
    }

    /**
     * Zeigt eine Seite der Staende - und was heute noch zu holen ist.
     *
     * <p>Der SPIELER ist Pflicht, obwohl die Staende nichts von ihm wissen: Die letzte
     * Zeile nennt seinen heutigen Rest, und ein Rest ohne Spieler ist keine Zahl. Eine
     * eigene uebersetzte Zeile dafuer gibt es nicht - wer auf der Konsole tippt, ist der
     * Betreiber.
     */
    private int zeigeListe(CommandContext<CommandSourceStack> zusammenhang, int seite) {
        if (!(zusammenhang.getSource().getSender() instanceof Player wer)) {
            zusammenhang.getSource().getSender().sendMessage(
                    Component.text("Der Bundesmarkt braucht einen Spieler."));
            return Command.SINGLE_SUCCESS;
        }
        List<Kistenshop> staende;
        long heuteSchon;
        try {
            staende = staende();
            heuteSchon = Markttag.heuteSchon(kasse, wirtschaft, uhr, wer.getUniqueId());
        } catch (RuntimeException stoerung) {
            // Ein eigener Satz und nicht die leere Liste: Eine Stoerung, die wie "es
            // steht kein Schild" aussieht, laesst einen Spieler auf einen Markt warten,
            // der dasteht. Dieselbe Entscheidung wie bei /shop liste.
            protokoll.log(Level.WARNING, "Die Staende des Bundes sind nicht lesbar -"
                    + " " + wer.getName() + " bekommt eine Absage.", stoerung);
            sende(wer, List.of(sprache.eineZeile(MarktSatz.GESTOERT, Einsatz.keine())));
            return 0;
        }
        sende(wer, Markttext.listeZeilen(sprache, staende, seite,
                wirtschaft.werte().handel().zeilenJeSeite(),
                wirtschaft.werte().markt(), heuteSchon));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Alle Staende des Bundes - in der Ordnung der Ablage.
     *
     * <p>Gefiltert wird mit {@link Kistenshop#demBund()}, der benannten Frage des Kerns,
     * und nicht mit einem eigenen {@code besitzer() == null}: Ein Bundshop hat keinen
     * Besitzer, und wer das an zwei Stellen selbst nachbaut, bekommt eines Tages zwei
     * Antworten. <b>Beide Richtungen stehen in der Liste</b> - ein Stand, an dem der
     * Bund verkauft, gehoert so gut zum Markt wie einer, an dem er ankauft; was er tut,
     * sagen seine zwei Preisstuecke in der Zeile.
     *
     * <p>Die Reihenfolge kommt aus {@code Shopablage.alle()} und damit aus
     * {@code Kistenshop.NACH_STELLE} - Welt, dann x, y, z, numerisch. Bei einer Liste
     * auf Seiten entscheidet die Ordnung, WAS auf Seite eins landet, und sie soll fuer
     * jeden dieselbe sein.
     */
    private List<Kistenshop> staende() {
        List<Kistenshop> gefunden = new ArrayList<>();
        for (Kistenshop shop : shops.alle()) {
            if (shop.demBund()) {
                gefunden.add(shop);
            }
        }
        return gefunden;
    }

    private void sende(Player wer, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            wer.sendMessage(zeile);
        }
    }
}
