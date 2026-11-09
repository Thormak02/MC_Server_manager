package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.freimarken.kern.Blockbuchablage;
import de.freimarken.kern.Blockbuchwerte;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Blockzeile;
import de.freimarken.kern.Wirtschaftsstand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * {@code /freimarken blockbuch} - was an diesem Block geschah, und was dieser Spieler tat.
 *
 * <p>Zwei Zweige, und sie beantworten zwei verschiedene Fragen:
 * <ul>
 *   <li><b>Nackt: der Block im Blick.</b> Ein Moderator steht vor einem Loch und will
 *       wissen, wer es gegraben hat. Das war die Wahl des Betreibers - "Befehl im Spiel,
 *       an der Stelle" -, und sie ist die richtige: Koordinaten abzutippen ist in einem
 *       Spiel, in dem man vor der Sache steht, der Umweg.</li>
 *   <li><b>{@code spieler <name>}: die andere Richtung.</b> Nicht "wer war hier", sondern
 *       "wo war der". Ein Dutzend abgewiesener Versuche an verschiedenen Parzellen in zwei
 *       Minuten ist der Fund, den nur diese Richtung zeigt.</li>
 * </ul>
 *
 * <p><b>Der angesehene Block kommt ueber eine Naht</b>, und das ist die erste Stelle
 * dieses Projekts, die ueberhaupt nach ihm fragt - vorher gab es im ganzen Baum kein
 * {@code getTargetBlockExact}, kein {@code rayTrace} und keinen Blockiterator (gemessen
 * am 09.11.2026). Ein Aufruf unmittelbar am {@link Player} waere nicht pruefbar: Der
 * Spielerstellvertreter der Proben beantwortet genau fuenfzehn Methoden und wirft bei
 * jeder anderen. Mit der Naht gibt der Befehl im Betrieb {@code getTargetBlockExact} und
 * in der Probe einen gestellten Block - und der Zweig, der die Frage stellt, ist derselbe.
 *
 * <p><b>Rechte am SENDER, der Spieler am EXECUTOR.</b> Dieselbe Teilung wie bei
 * {@link BerufeBefehle}: Bei {@code /execute as <spieler> run freimarken blockbuch} ist
 * der Executor der Spieler und der Sender die Konsole. GEFRAGT wird fuer den Executor -
 * er schaut den Block an -, GELESEN wird beim Sender.
 *
 * <p><b>Der Ortszweig ist spielerpflichtig, der Spielerzweig nicht.</b> Wer keinen Koerper
 * hat, sieht keinen Block an; nach einem Spieler fragen kann die Konsole dagegen sehr
 * wohl, und fuer die Moderation ist das sogar der bequemere Weg. Darum steht die
 * Spielerpflicht als ABSAGE im Zweig und nicht als {@code requires}: Ein Betreiber, der
 * den Befehl auf der Konsole tippt, soll lesen, warum er nicht geht - ein
 * {@code requires} liesse ihn wortgleich wie einen Befehl antworten, den es nicht gibt.
 */
final class BlockBefehle {

    /**
     * Das Recht, das Blockbuch zu lesen.
     *
     * <p>{@code default: op} in der {@code plugin.yml}, wie {@code freimarken.verwalten}:
     * Dies ist eine Moderationsauskunft. Sie nennt, wer wann wo war - ein Spieler, der
     * sie frei lesen koennte, saehe damit auch, wann jemand online war und wo er baut.
     */
    static final String RECHT = "freimarken.blockbuch";

    /**
     * Woher der angesehene Block kommt.
     *
     * <p>Ein Fingerbreit Naht und kein Rahmenwerk, aus demselben Grund wie
     * {@link Verdienstwache.Taktquelle}: Ohne sie waere dieser Zweig der einzige
     * ungepruefte des Schrittes. Siehe den Kopf der Klasse.
     */
    interface Blickfang {

        /**
         * Der Block, den dieser Spieler ansieht - oder {@code null}.
         *
         * @param wer        wer schaut
         * @param reichweite wie weit, in Bloecken
         * @return der Block, oder {@code null}, wenn in Reichweite keiner liegt. Luft
         *         zaehlt nicht als Block: Ein Blick in den Himmel gibt {@code null}
         */
        Block imBlick(Player wer, int reichweite);
    }

    /**
     * Woher ein Spielername zu einem gesuchten Namen kommt.
     *
     * <p>Eine zweite Naht, und sie ist noetig: Der Zweig muss aus einem GETIPPTEN Namen
     * eine Kennung machen, und das geht nur ueber {@code Bukkit.getOfflinePlayer} - einen
     * statischen Aufruf, der ohne Server wirft. Ein Spieler, der nie auf diesem Server
     * war, hat keine Kennung, und dann soll der Befehl das sagen und nicht raten.
     */
    interface Namenssuche {

        /**
         * Die Kennung zu diesem Namen - oder {@code null}, wenn der Server ihn nicht
         * kennt.
         */
        UUID kennungVon(String name);
    }

    private final Blockbuchablage ablage;
    private final Blockbuchwerte werte;
    private final Wirtschaftsstand wirtschaft;
    private final Sprache sprache;
    private final Blickfang blickfang;
    private final Namenssuche namenssuche;
    private final Blocktext.Namensgeber namen;
    private final Logger protokoll;

    BlockBefehle(Blockbuchablage ablage, Blockbuchwerte werte,
                 Wirtschaftsstand wirtschaft, Sprache sprache, Blickfang blickfang,
                 Namenssuche namenssuche, Blocktext.Namensgeber namen,
                 Logger protokoll) {
        if (ablage == null || werte == null || wirtschaft == null || sprache == null
                || blickfang == null || namenssuche == null || namen == null
                || protokoll == null) {
            throw new IllegalArgumentException("Ablage, Werte, Wirtschaftsstand,"
                    + " Sprache, Blickfang, Namenssuche, Namensgeber und Protokoll sind"
                    + " Pflicht");
        }
        this.ablage = ablage;
        this.werte = werte;
        this.wirtschaft = wirtschaft;
        this.sprache = sprache;
        this.blickfang = blickfang;
        this.namenssuche = namenssuche;
        this.namen = namen;
        this.protokoll = protokoll;
    }

    /**
     * Der Zweig, den {@link BundBefehle} einhaengt.
     *
     * <p>NICHT gebaut ({@code .build()} fehlt), damit der Baum von {@code /freimarken}
     * ihn mit {@code .then(...)} aufnehmen kann - dasselbe Muster wie
     * {@link SpielzeitBefehle#spielzeit()}.
     */
    LiteralArgumentBuilder<CommandSourceStack> blockbuch() {
        return Commands.literal("blockbuch")
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(this::zeigeOrt)
                .then(Commands.literal("spieler")
                        .then(Commands.argument("name", StringArgumentType.word())
                                // Vorschlaege nur aus den ANWESENDEN, wie ueberall im
                                // Projekt: Eine Liste aller je gesehenen Spieler waere
                                // auf einem alten Server tausende Namen lang, und der
                                // Zweig nimmt ohnehin jeden getippten Namen an.
                                .suggests((zusammenhang, bauer) -> {
                                    for (Player spieler : Bukkit.getOnlinePlayers()) {
                                        bauer.suggest(spieler.getName());
                                    }
                                    return bauer.buildFuture();
                                })
                                .executes(this::zeigeSpieler)));
    }

    /**
     * Was an dem Block geschah, den der Spieler ansieht.
     *
     * <p>Die Reihenfolge der Absagen ist nach Gewissheit geordnet, und das ist Absicht:
     * zuerst "du bist keiner" (sicher), dann "du siehst nichts an" (sicher), dann "das
     * Buch ist aus" (eine Einstellung), und erst zuletzt die Abfrage. So liest der
     * Moderator nie "hier steht nichts", wenn der wahre Grund ein anderer ist - der
     * Unterschied zwischen "nichts geschehen" und "nichts aufgeschrieben" ist genau der,
     * den er wissen muss, bevor er jemanden freispricht.
     */
    private int zeigeOrt(CommandContext<CommandSourceStack> zusammenhang) {
        if (!(zusammenhang.getSource().getExecutor() instanceof Player spieler)) {
            // Keine Absage per requires, sondern eine gelesene: Der Betreiber auf der
            // Konsole soll den GRUND lesen und nicht "unbekannter Befehl". Dass er nach
            // einem SPIELER fragen kann, sagt ihm der Satz.
            sende(zusammenhang, sprache.bau(BlockSatz.KEIN_BLOCK, 1L,
                    Einsatz.von("reichweite", werte.reichweite())));
            return 0;
        }
        Block block = blickfang.imBlick(spieler, werte.reichweite());
        if (block == null) {
            sende(zusammenhang, sprache.bau(BlockSatz.KEIN_BLOCK, 1L,
                    Einsatz.von("reichweite", werte.reichweite())));
            return 0;
        }
        Blockort ort = new Blockort(block.getWorld().key().asString(), block.getX(),
                block.getY(), block.getZ());
        if (!werte.fuerWelt(ort.welt()).leer()) {
            List<Blockzeile> zeilen = gelesen(() -> ablage.amOrt(ort,
                    werte.zeilenJeAbfrage()), zusammenhang);
            if (zeilen == null) {
                return 0;
            }
            sende(zusammenhang, Blocktext.amOrtZeilen(sprache, ort, zeilen, zone(),
                    namen));
            return Command.SINGLE_SUCCESS;
        }
        // Die Welt schreibt nichts - und das ist NICHT dasselbe wie "hier steht nichts".
        // Ein Moderator, der das verwechselt, spricht jemanden frei, ueber den nie etwas
        // aufgeschrieben wurde.
        sende(zusammenhang, sprache.bau(BlockSatz.AUS, 1L, Einsatz.keine()));
        return 0;
    }

    /** Was dieser Spieler getan hat - auch von der Konsole aus zu fragen. */
    private int zeigeSpieler(CommandContext<CommandSourceStack> zusammenhang) {
        String gesucht = StringArgumentType.getString(zusammenhang, "name");
        UUID kennung = namenssuche.kennungVon(gesucht);
        if (kennung == null) {
            sende(zusammenhang, sprache.bau(BlockSatz.UNBEKANNT, 1L,
                    Einsatz.von("name", gesucht)));
            return 0;
        }
        List<Blockzeile> zeilen = gelesen(() -> ablage.vonSpieler(kennung,
                werte.zeilenJeAbfrage()), zusammenhang);
        if (zeilen == null) {
            return 0;
        }
        // Der Name kommt aus dem Namensgeber und nicht aus der Eingabe: Wer "anna" tippt,
        // soll "Anna" lesen - und bei einem Spieler, den der Server nur als Kennung
        // kennt, die Kennung. Der getippte Name stuende sonst falsch geschrieben in der
        // Ueberschrift einer Auskunft, die jemanden belastet.
        sende(zusammenhang, Blocktext.vonSpielerZeilen(sprache, namen.von(kennung),
                zeilen, zone()));
        return Command.SINGLE_SUCCESS;
    }

    /** Was eine Abfrage liefert - oder {@code null}, wenn sie gestoert war. */
    private interface Abfrage {
        List<Blockzeile> hole();
    }

    /**
     * Fuehrt eine Abfrage aus und sagt bei einer Stoerung ab.
     *
     * <p>Eine Stoerung darf NICHT als "hier steht nichts" aussehen: Ein Netzfehler wuerde
     * dann zu einem Freispruch. Darum ein eigener Satz und ein {@code null} statt einer
     * leeren Liste - der Aufrufer kann die beiden Faelle nicht verwechseln.
     */
    private List<Blockzeile> gelesen(Abfrage abfrage,
                                     CommandContext<CommandSourceStack> zusammenhang) {
        try {
            return abfrage.hole();
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Das Blockbuch konnte nicht gelesen werden -"
                    + " der Befehl hat abgesagt.", fehler);
            sende(zusammenhang, sprache.bau(BlockSatz.GESTOERT, 1L, Einsatz.keine()));
            return null;
        }
    }

    /**
     * Geantwortet wird dem SENDER und nicht dem Executor.
     *
     * <p>Bei {@code /execute as <spieler> run freimarken blockbuch} schaut der Spieler
     * den Block an, aber lesen will es der, der getippt hat. Dieselbe Teilung wie bei
     * den Rechten.
     */
    private static void sende(CommandContext<CommandSourceStack> zusammenhang,
                              List<Component> zeilen) {
        zeilen.forEach(zusammenhang.getSource().getSender()::sendMessage);
    }

    /** Die eingestellte Zone - von aussen, nie als Feld. */
    private java.time.ZoneId zone() {
        return wirtschaft.werte().zeit().zone();
    }

    /**
     * Die Fassung fuers Spiel: Blick, Namenssuche und Namensgeber ueber Bukkit.
     *
     * <p>An EINER Stelle gebaut und nicht im Plugin verstreut, damit die drei Nahtstuecke
     * beisammen stehen - sie beantworten dieselbe Frage "wer und was ist da draussen".
     *
     * <p>{@code getTargetBlockExact(reichweite)} und nicht {@code getTargetBlock}: Jenes
     * ist veraltet, dieses gibt {@code null} statt eines Luftblocks, wenn der Blick ins
     * Leere geht - genau die Unterscheidung, die {@link Blickfang} zusagt.
     */
    static BlockBefehle ueberBukkit(Blockbuchablage ablage, Blockbuchwerte werte,
                                    Wirtschaftsstand wirtschaft, Sprache sprache,
                                    Logger protokoll) {
        return new BlockBefehle(ablage, werte, wirtschaft, sprache,
                (wer, reichweite) -> wer.getTargetBlockExact(reichweite),
                BlockBefehle::kennungUeberBukkit, Spielername::von, protokoll);
    }

    /**
     * Die Kennung zu einem getippten Namen - oder {@code null}.
     *
     * <p>{@code hasPlayedBefore()} ist die Schranke: {@code getOfflinePlayer(String)}
     * gibt NIEMALS null zurueck, sondern erfindet bei einem unbekannten Namen ein
     * Exemplar mit einer frisch gewuerfelten Kennung. Ohne diese Frage wuerde ein
     * Tippfehler zu einer Abfrage auf eine Kennung, die es nie gab - und die Antwort
     * waere "zu diesem Spieler steht nichts im Buch", also ein Freispruch fuer einen
     * Namen, den niemand traegt.
     *
     * <p>Ein anwesender Spieler wird zuerst gefragt: Er hat sicher eine Kennung, und
     * {@code hasPlayedBefore()} ist auf manchen Servern fuer den gerade beigetretenen
     * Spieler noch falsch.
     */
    private static UUID kennungUeberBukkit(String name) {
        Player anwesend = Bukkit.getPlayerExact(name);
        if (anwesend != null) {
            return anwesend.getUniqueId();
        }
        OfflinePlayer bekannt = Bukkit.getOfflinePlayer(name);
        return bekannt.hasPlayedBefore() ? bekannt.getUniqueId() : null;
    }
}
