package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Wirtschaftsstand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.audience.Audience;
import org.bukkit.permissions.Permissible;

/**
 * Der Befehlsbaum von {@code /freimarken} - und die zwei Zweige, die ein Spieler liest.
 *
 * <p><b>Eine eigene Klasse seit dem 06.11.2026, und der Grund ist eine Probe.</b> Dieser
 * Baum war der letzte, der in {@link FreimarkenPlugin} selbst stand - alle sechs anderen
 * Befehlsgruppen haben laengst ihre eigene Klasse ({@code ParzellenBefehle},
 * {@code WarpBefehle}, {@code RueckwegBefehle}, {@code AnfragenBefehle},
 * {@code SpielzeitBefehle}). Solange er dort stand, liess er sich nicht TIPPEN: Ein
 * {@code JavaPlugin} prueft in seinem Konstruktor, ob es von einem
 * {@code ConfiguredPluginClassLoader} geladen wurde, und wirft sonst
 * {@code IllegalStateException} - im Bytecode nachgesehen, nicht vermutet. Damit war
 * {@code /freimarken} der einzige Befehl ohne Durchlaufprobe.
 *
 * <p><b>Was hier NICHT hereinzieht, ist der Betreibertext.</b> Die vier
 * Verwaltungszweige - {@code welt}, {@code vorgenerieren}, {@code neuladen} und
 * {@code parzelle} - lesen die laufende Welt, die Vorgenerierung und die
 * Einstellungsdatei; sie bleiben im Plugin und kommen als {@link Betreiberzweig}
 * beziehungsweise {@link Versetzung} herein. Diese Klasse kennt damit die WEICHE und die
 * zwei Spielerzweige, und sonst nichts. Dass der Betreibertext im Programm bleibt, ist
 * eine Entscheidung und steht so im Kopf der Sprachdatei.
 *
 * <p><b>Gebaut wird sie im Ereignis und nicht im {@code onEnable}:</b> Paper fragt den
 * Befehlsbaum erst im Lebenslauf-Ereignis {@code COMMANDS} ab, und bis dahin stehen alle
 * Felder des Plugins - auch {@code fluechtig}. Darum darf {@link Stand} ein Wert sein und
 * muss kein Lieferant sein.
 */
final class BundBefehle {

    /** Wer nachsehen und sich versetzen darf - nicht jeder Spieler. */
    static final String RECHT_VERWALTEN = "freimarken.verwalten";

    /**
     * Was die Standzeile nennt - vier einfache Werte, ohne Server und ohne Plugin.
     *
     * @param version     der Stand des Plugins
     * @param javaFassung die Hauptnummer der Java-Ausgabe
     * @param minecraft   die Fassung des Servers
     * @param fluechtig   ob die Ablage den Neustart nicht uebersteht
     */
    record Stand(String version, String javaFassung, String minecraft,
                 boolean fluechtig) { }

    /**
     * Ein Zweig, der dem BETREIBER antwortet - sein Text bleibt im Programm.
     *
     * <p>{@code wer} ist der Name des Absenders; nur {@code neuladen} schreibt ihn ins
     * Protokoll, die anderen zwei lassen ihn liegen. Eine zweite Schnittstelle fuer die
     * zwei waere ein Unterschied ohne Unterschied.
     */
    interface Betreiberzweig {
        void tue(Audience empfaenger, String wer);
    }

    /** Der Zweig mit zwei Zahlen: {@code /freimarken parzelle <x> <z>}. */
    interface Versetzung {
        void tue(Audience empfaenger, int parzelleX, int parzelleZ);
    }

    private final Sprache sprache;
    private final Stand stand;
    private final Wirtschaftsstand wirtschaft;
    private final LiteralArgumentBuilder<CommandSourceStack> spielzeit;
    private final Betreiberzweig welt;
    private final Betreiberzweig vorgenerieren;
    private final Betreiberzweig neuladen;
    private final Betreiberzweig messstelle;
    private final Versetzung versetzung;
    private final int von;
    private final int bis;

    /**
     * @param spielzeit der Zweig von {@code SpielzeitBefehle} als BAUER und nicht als
     *                  Knoten - so gibt jene Klasse ihn heraus; er haengt
     *                  hier und bekommt keinen eigenen Befehl (Plan 11.1)
     * @param von       kleinste Parzellennummer, die {@code parzelle} annimmt
     * @param bis       groesste - die Grenzen kommen aus der Stadt und nicht aus einer
     *                  geratenen Zahl
     */
    BundBefehle(Sprache sprache, Stand stand, Wirtschaftsstand wirtschaft,
                LiteralArgumentBuilder<CommandSourceStack> spielzeit, Betreiberzweig welt,
                Betreiberzweig vorgenerieren, Betreiberzweig neuladen,
                Betreiberzweig messstelle, Versetzung versetzung, int von, int bis) {
        this.sprache = sprache;
        this.stand = stand;
        this.wirtschaft = wirtschaft;
        this.spielzeit = spielzeit;
        this.welt = welt;
        this.vorgenerieren = vorgenerieren;
        this.neuladen = neuladen;
        this.messstelle = messstelle;
        this.versetzung = versetzung;
        this.von = von;
        this.bis = bis;
    }

    LiteralCommandNode<CommandSourceStack> freimarken() {
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
                // Ein Zweig und kein eigener Befehl: Plan 11.1 fuehrt sechzehn Befehle, und
                // /spielzeit steht nicht darunter. Das eigene Recht steckt im Zweig.
                .then(spielzeit)
                .then(Commands.literal("welt")
                        .requires(darfVerwalten())
                        .executes(z -> rufe(z, welt)))
                .then(Commands.literal("vorgenerieren")
                        .requires(darfVerwalten())
                        .executes(z -> rufe(z, vorgenerieren)))
                // Kein eigenes Recht: Plan 1544-1547 begruendet, warum es keinen zweiten
                // Ort fuer Rechte geben soll. Der Befehl kann Werte austauschen, keine
                // Buchung aendern - und wer die Datei nicht aendern kann, aendert damit
                // auch nichts.
                .then(Commands.literal("neuladen")
                        .requires(darfVerwalten())
                        .executes(z -> rufe(z, neuladen)))
                // Die Messstelle aus Teil G: Sie ZEIGT und greift nicht ein, also ist der
                // Zweig eine Auskunft wie "welt". Beim Verwalter und nicht bei allen: Er
                // nennt, was jeder anwesende Spieler gerade tut, und das ist nichts, was
                // ein Mitspieler lesen soll.
                .then(Commands.literal("messstelle")
                        .requires(darfVerwalten())
                        .executes(z -> rufe(z, messstelle)))
                .then(Commands.literal("parzelle")
                        .requires(darfVerwalten())
                        // Die Grenzen kommen aus der Stadt, nicht aus einer geratenen
                        // Zahl: mitteX(64) laege 3.085 Bloecke jenseits der Weltgrenze.
                        .then(Commands.argument("x",
                                        IntegerArgumentType.integer(von, bis))
                                .then(Commands.argument("z",
                                                IntegerArgumentType.integer(von, bis))
                                        .executes(z -> {
                                            versetzung.tue(z.getSource().getSender(),
                                                    IntegerArgumentType.getInteger(z, "x"),
                                                    IntegerArgumentType.getInteger(z, "z"));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build();
    }

    /**
     * Die Wache der drei Verwaltungszweige - EINE Stelle und keine drei Abschriften.
     *
     * <p>Dreimal dieselbe Bedingung hingeschrieben waere dreimal die Gelegenheit, sie
     * beim naechsten Mal zu vergessen. {@code getSender} und nicht {@code getExecutor}:
     * Diese Zweige antworten dem Betreiber, und der sitzt auch auf der Konsole.
     */
    private static java.util.function.Predicate<CommandSourceStack> darfVerwalten() {
        return quelle -> quelle.getSender().hasPermission(RECHT_VERWALTEN);
    }

    /** Ruft einen Betreiberzweig mit Empfaenger und Namen - die drei tun dasselbe. */
    private static int rufe(com.mojang.brigadier.context.CommandContext<CommandSourceStack> z,
                            Betreiberzweig zweig) {
        zweig.tue(z.getSource().getSender(), z.getSource().getSender().getName());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die Antwort auf das nackte {@code /freimarken} - gebaut in {@link Bundtext}.
     *
     * <p>Seit Bauschritt 7 stehen die Worte unter {@code stand} in
     * {@code sprache-vorlage.yml}, und hier wird nur noch gesendet. WELCHE der zwei
     * Ablagezeilen kommt und ob die Verwaltungszeile dazugehoert, entscheidet die Naht
     * an den zwei Wahrheitswerten - und dort prueft {@code SendewegTest} jede Lage als
     * Bild, ohne Server.
     */
    private void zeigeStand(Audience empfaenger) {
        Bundtext.standZeilen(sprache, stand.version(), stand.javaFassung(),
                        stand.minecraft(), stand.fluechtig(),
                        empfaenger instanceof Permissible darf
                                && darf.hasPermission(RECHT_VERWALTEN))
                .forEach(empfaenger::sendMessage);
    }

    /**
     * Was die naechste Parzelle kostet - gebaut in {@link Bundtext}.
     *
     * <p>Die Staffel kommt bei JEDEM Aufruf frisch aus den Wirtschaftswerten und nicht
     * aus einem Feld: {@code /freimarken neuladen} tauscht sie aus, und ein gemerkter
     * Stand nannte danach die alten Preise. Die zwei Zahlen gehen als nackte
     * {@code long} in die Naht, und das Geldwort holt sie bei {@link Sprache#betrag}.
     */
    private void zeigePreis(Audience empfaenger, int besessen) {
        Preisstaffel preise = wirtschaft.werte().preise();
        Bundtext.preisZeilen(sprache, besessen, preise.preisFuerNaechste(besessen),
                        preise.gesamtpreisFuer(besessen))
                .forEach(empfaenger::sendMessage);
    }
}
