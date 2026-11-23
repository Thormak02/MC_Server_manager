package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
     * Wer Geld von Hand buchen darf - ein EIGENES Recht.
     *
     * <p>Nicht {@link #RECHT_VERWALTEN}, und der Grund steht im Kommentar an
     * {@code neuladen}: Jene Zweige zeigen etwas oder tauschen Einstellwerte aus - "der
     * Befehl kann Werte austauschen, keine Buchung aendern". Dieser aendert eine Buchung,
     * und das ist der Unterschied, auf den sich jene Begruendung stuetzt. Wer Welten
     * anlegen soll, soll nicht zugleich Konten fuellen koennen.
     */
    static final String RECHT_GELD = "freimarken.geld";

    /**
     * Besitz umschreiben - ein eigenes Recht neben {@link #RECHT_GELD}.
     *
     * <p>Getrennt, weil es zwei verschiedene Schaeden sind: Eine falsche Buchung laesst
     * sich mit einer Gegenbuchung heilen, ein freigegebener Besitz nicht - die Stelle
     * ist wieder Bauland, und der Naechste nimmt sie.
     */
    static final String RECHT_BESITZ = "freimarken.besitz";

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

    /**
     * Der Zweig, der Geld bucht: {@code /freimarken geld <spieler> <betrag>}.
     *
     * <p>ZWEI Methoden und kein Sonderwert fuer "Angaben fehlen": Ein {@code null} als
     * Name waere ein Zeichen, das der Aufgerufene erraten muss, und beim naechsten Zweig
     * erraet es jemand falsch.
     *
     * <p>{@code wer} ist der Name des Absenders. Er gehoert in das Protokoll und nicht in
     * die Antwort: Wer, wann, was - bei Geld ist das keine Formalie, sondern der
     * Unterschied zwischen "der Betreiber hat es korrigiert" und "mir fehlen 50.000
     * Mark".
     */
    interface Geldzweig {

        void tue(Audience empfaenger, String wer, String spieler, long betrag,
                 Betreibergeld.Waehrung waehrung);

        /** Die Benutzung - fuer jede unvollstaendige Form des Zweigs. */
        void hilfe(Audience empfaenger, Betreibergeld.Waehrung waehrung);
    }

    /**
     * Der Zweig, der Besitz umschreibt: {@code /freimarken besitz ...}.
     *
     * <p>DREI Methoden und kein Sonderwert fuer "Angaben fehlen" - dieselbe Bauart wie
     * {@link Geldzweig}, und aus demselben Grund: Ein {@code null} als Name waere ein
     * Zeichen, das der Aufgerufene erraten muss.
     *
     * <p>{@code wer} ist der Name des Absenders und gehoert in das Protokoll, nicht in
     * die Antwort. Bei Besitz ist das keine Formalie: Eine Parzelle, die ihren
     * Eigentuemer ohne Zeile im Protokoll wechselt, ist von einem Diebstahl nicht zu
     * unterscheiden.
     */
    interface Besitzzweig {

        void uebertrage(Audience empfaenger, String wer, int parzelleX, int parzelleZ,
                        String anName);

        void gibFrei(Audience empfaenger, String wer, int parzelleX, int parzelleZ);

        /** Die Benutzung - fuer jede unvollstaendige Form des Zweigs. */
        void hilfe(Audience empfaenger);
    }

    private final Sprache sprache;
    private final Stand stand;
    private final Wirtschaftsstand wirtschaft;
    private final LiteralArgumentBuilder<CommandSourceStack> spielzeit;

    /**
     * Der Zweig des Blockbuchs - ebenfalls als BAUER, dieselbe Bauart wie
     * {@link #spielzeit}.
     *
     * <p>Ein Zweig und kein eigener Befehl, aus demselben Grund: Plan 11.1 fuehrt eine
     * feste Liste von Befehlen, und {@code /blockbuch} steht nicht darunter.
     *
     * <p>Sein eigenes Recht steckt im Zweig, und es ist ausdruecklich NICHT
     * {@link #darfVerwalten()}: Das Blockbuch zu lesen ist eine Moderationsaufgabe, keine
     * Verwaltung. Wer es bekommen soll, soll nicht gleichzeitig Welten anlegen und
     * Spieler quer durch die Stadt versetzen koennen.
     */
    private final LiteralArgumentBuilder<CommandSourceStack> blockbuch;

    private final Betreiberzweig welt;
    private final Betreiberzweig vorgenerieren;
    private final Betreiberzweig neuladen;
    private final Betreiberzweig messstelle;
    private final Versetzung versetzung;
    private final Geldzweig geld;
    private final Besitzzweig besitz;
    private final int von;
    private final int bis;

    /**
     * @param spielzeit der Zweig von {@code SpielzeitBefehle} als BAUER und nicht als
     *                  Knoten - so gibt jene Klasse ihn heraus; er haengt
     *                  hier und bekommt keinen eigenen Befehl (Plan 11.1)
     * @param blockbuch der Zweig von {@code BlockBefehle}, ebenso als Bauer
     * @param von       kleinste Parzellennummer, die {@code parzelle} annimmt
     * @param bis       groesste - die Grenzen kommen aus der Stadt und nicht aus einer
     *                  geratenen Zahl
     */
    BundBefehle(Sprache sprache, Stand stand, Wirtschaftsstand wirtschaft,
                LiteralArgumentBuilder<CommandSourceStack> spielzeit,
                LiteralArgumentBuilder<CommandSourceStack> blockbuch,
                Betreiberzweig welt,
                Betreiberzweig vorgenerieren, Betreiberzweig neuladen,
                Betreiberzweig messstelle, Versetzung versetzung, Geldzweig geld,
                Besitzzweig besitz, int von, int bis) {
        this.blockbuch = blockbuch;
        this.sprache = sprache;
        this.stand = stand;
        this.wirtschaft = wirtschaft;
        this.spielzeit = spielzeit;
        this.welt = welt;
        this.vorgenerieren = vorgenerieren;
        this.neuladen = neuladen;
        this.messstelle = messstelle;
        this.versetzung = versetzung;
        this.geld = geld;
        this.besitz = besitz;
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
                // Das Blockbuch, mit eigenem Recht im Zweig - siehe das Feld. Es steht
                // hier und nicht bei den Verwaltungszweigen darunter, weil es eine
                // AUSKUNFT ist und nichts aendert: dieselbe Art Zweig wie /spielzeit,
                // nur fuer eine andere Frage.
                .then(blockbuch)
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
                // GELD VON HAND, und mit einem EIGENEN Recht: Die vier Zweige darueber
                // zeigen etwas oder tauschen Einstellwerte aus; dieser aendert eine
                // Buchung. Drei .executes, eines je unvollstaendiger Form - sonst endet
                // "/freimarken geld" in Brigadiers englischem "Unknown or incomplete
                // command", und der Betreiber sucht den Fehler bei sich.
                .then(geldbaum(Betreibergeld.Waehrung.MARK, geld))
                // EIN EIGENES LITERAL und kein Waehrungswort neben <spieler>: Ein Literal
                // neben einem Wortargument konkurriert mit ihm und braucht eigene
                // .executes, sonst antwortet Brigadier englisch - dieselbe Falle, die der
                // Kommentar darueber schon nennt. So bleiben ausserdem der heutige
                // Befehl, der Manager-Pfad und das Muskelgedaechtnis unveraendert.
                .then(geldbaum(Betreibergeld.Waehrung.GOLDMARK, geld))
                .then(Commands.literal("besitz")
                        // EIGENES Recht, nicht freimarken.geld und nicht
                        // freimarken.verwalten: Welten anlegen, Konten fuellen und
                        // Besitz umschreiben sind drei Vollmachten. Wer eine braucht,
                        // soll nicht die anderen zwei dazubekommen.
                        .requires(quelle -> quelle.getSender()
                                .hasPermission(RECHT_BESITZ))
                        .executes(z -> {
                            besitz.hilfe(z.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("uebertragen")
                                .executes(z -> {
                                    besitz.hilfe(z.getSource().getSender());
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("x",
                                                IntegerArgumentType.integer(von, bis))
                                        .executes(z -> {
                                            besitz.hilfe(z.getSource().getSender());
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(Commands.argument("z",
                                                        IntegerArgumentType.integer(von,
                                                                bis))
                                                .executes(z -> {
                                                    besitz.hilfe(
                                                            z.getSource().getSender());
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                                .then(Commands.argument("spieler",
                                                                StringArgumentType.word())
                                                        .executes(z -> {
                                                            besitz.uebertrage(
                                                                    z.getSource()
                                                                            .getSender(),
                                                                    z.getSource()
                                                                            .getSender()
                                                                            .getName(),
                                                                    IntegerArgumentType
                                                                            .getInteger(z,
                                                                                    "x"),
                                                                    IntegerArgumentType
                                                                            .getInteger(z,
                                                                                    "z"),
                                                                    StringArgumentType
                                                                            .getString(z,
                                                                                    "spieler"));
                                                            return Command.SINGLE_SUCCESS;
                                                        })))))
                        // Freigeben verlangt BESTAETIGEN: Es loescht Besitz ohne
                        // Rueckzahlung, und /parzelle aufgeben verlangt es aus demselben
                        // Grund. Ein Tippfehler in der Nummer traefe sonst die Parzelle
                        // eines Unbeteiligten.
                        .then(Commands.literal("freigeben")
                                .executes(z -> {
                                    besitz.hilfe(z.getSource().getSender());
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("x",
                                                IntegerArgumentType.integer(von, bis))
                                        .executes(z -> {
                                            besitz.hilfe(z.getSource().getSender());
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(Commands.argument("z",
                                                        IntegerArgumentType.integer(von,
                                                                bis))
                                                .executes(z -> {
                                                    besitz.hilfe(
                                                            z.getSource().getSender());
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                                .then(Commands.literal("bestaetigen")
                                                        .executes(z -> {
                                                            besitz.gibFrei(
                                                                    z.getSource()
                                                                            .getSender(),
                                                                    z.getSource()
                                                                            .getSender()
                                                                            .getName(),
                                                                    IntegerArgumentType
                                                                            .getInteger(z,
                                                                                    "x"),
                                                                    IntegerArgumentType
                                                                            .getInteger(z,
                                                                                    "z"));
                                                            return Command.SINGLE_SUCCESS;
                                                        }))))))
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
     * Der Geldzweig fuer eine Waehrung - EINMAL gebaut, zweimal angehaengt [23.11.2026].
     *
     * <p>Das Befehlswort und die Schranke kommen aus {@link Betreibergeld.Waehrung}: Es
     * ist ein Paar, und wer es an zwei Stellen fuehrt, laesst es auseinanderlaufen.
     *
     * <p>Dasselbe Recht fuer beide Waehrungen: {@code RECHT_GELD} ist die Vollmacht
     * "Konten fuellen". Die drei Rechte dieses Baums trennen Welten, Konten und Besitz -
     * nicht Waehrungen. Ein zweites Recht fuer dieselbe Vollmacht waere ein zweiter Ort
     * fuer dieselbe Regel.
     *
     * <p>Diese Methode SENDET nichts - sie baut nur den Baum. Was der Betreiber liest,
     * kommt aus dem {@link Geldzweig}, und darum bleibt die Zahl der Sendestellen in
     * dieser Klasse unveraendert.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> geldbaum(
            Betreibergeld.Waehrung waehrung, Geldzweig geld) {
        return Commands.literal(waehrung.befehlswort())
                .requires(quelle -> quelle.getSender()
                        .hasPermission(RECHT_GELD))
                .executes(z -> {
                    geld.hilfe(z.getSource().getSender(), waehrung);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("spieler", StringArgumentType.word())
                        .executes(z -> {
                            geld.hilfe(z.getSource().getSender(), waehrung);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("betrag",
                                        LongArgumentType.longArg(
                                                -waehrung.hoechstens(),
                                                waehrung.hoechstens()))
                                .executes(z -> {
                                    geld.tue(z.getSource().getSender(),
                                            z.getSource().getSender().getName(),
                                            StringArgumentType.getString(z, "spieler"),
                                            LongArgumentType.getLong(z, "betrag"),
                                            waehrung);
                                    return Command.SINGLE_SUCCESS;
                                })));
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
