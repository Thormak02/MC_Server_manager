package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.freimarken.kern.Blockbuchwerte;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Rueckrollplan;
import de.freimarken.kern.Rueckweggrenzen;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /freimarken blockbuch zurueck} - nimmt zurueck, was in einem Kasten geschah.
 *
 * <p><b>Zweistufig, und die Stufen sind nicht Hoeflichkeit.</b> Der erste Befehl zeigt
 * Zahlen, der zweite fuehrt aus. Das ist der groesste Eingriff, den dieses Plugin in eine
 * fertige Welt macht - er setzt tausend Bloecke, ohne zu fragen, wem sie gehoeren -, und
 * ein Vertipper im Radius traefe statt eines gesprengten Hauses einen halben Stadtteil.
 *
 * <p><b>Der ORT steht im Bestaetigungsbefehl</b>, und damit steht die Sicherung an
 * derselben Stelle wie bei {@code /freimarken parzelle aufgeben}: Ohne ihn rollte die
 * Bestaetigung den Kasten um die Stelle zurueck, an der der Moderator JETZT steht - ein
 * Schritt waehrend des Lesens traefe den falschen Kasten, und zurueck gibt es dann nichts.
 * Dass der Befehl dadurch lang wird, ist der Preis; die Vorschau gibt ihn fertig zum
 * Abtippen vor.
 *
 * <p><b>Mit dem Ort im Befehl geht die Rueckrollung auch von der Konsole</b>, und das ist
 * kein Nebenprodukt, sondern ein Gewinn: Ein Betreiber kann einen Grief aus dem Manager
 * heraus zuruecknehmen, ohne sich einzuloggen. Spielerpflichtig bleibt nur die Vorschau am
 * EIGENEN Standort - wer keinen Koerper hat, steht nirgends -, und sie sagt das als
 * gelesene Absage und nicht als "unbekannter Befehl".
 *
 * <p><b>Ein eigenes Recht.</b> {@link BlockBefehle#RECHT} liest, dieses hier greift ein;
 * dieselbe Teilung und derselbe Grund wie bei {@code freimarken.team} und
 * {@code freimarken.team.vergeben}. Brigadier prueft beide - das Elternliteral
 * {@code blockbuch} traegt das Leserecht -, und das ist richtig: Wer zurueckrollen darf,
 * muss sehen koennen, was er zurueckrollt.
 *
 * <p><b>Keine eigene Klasse fuer den Zweig in {@link BlockBefehle}</b>, sondern diese
 * hier, und zwar aus einem handfesten Grund: Jene Klasse nimmt bereits acht Werte, und
 * dieser Zweig braucht vier weitere (die Rueckrollung, die Grenzen, eine Uhr und das
 * Protokoll). Eingehaengt wird mit {@code .then(...)} - dasselbe Muster, mit dem
 * {@link BlockBefehle#blockbuch()} selbst in den Baum von {@code /freimarken} kommt.
 */
final class RueckrollBefehle {

    /**
     * Das Recht, eine Rueckrollung auszufuehren.
     *
     * <p>{@code default: op} wie {@link BlockBefehle#RECHT}, aber ein eigenes: Das
     * Blockbuch zu LESEN ist eine Auskunft, es zurueckzurollen ist ein Eingriff in
     * fremdes Gebautes. Ein Moderator, der Griefs aufklaeren soll, braucht das erste;
     * das zweite ist eine zweite Entscheidung.
     */
    static final String RECHT = "freimarken.blockbuch.zurueck";

    /**
     * Das Wort fuer den Zweig.
     *
     * <p>Als Konstante, weil {@link Blocktext#bestaetigung} die Zeile daraus baut: Zwei
     * Stellen mit demselben Wort liefen beim Umbenennen auseinander, und die Vorschau
     * gaebe dann einen Befehl vor, den es nicht gibt.
     */
    static final String ZURUECK = "zurueck";

    /** Das Wort vor der Ortsangabe. */
    static final String BEI = "bei";

    /** Das Wort, das ausfuehrt. */
    static final String BESTAETIGEN = "bestaetigen";

    /**
     * Woher "jetzt" kommt.
     *
     * <p>Dieselbe Naht und derselbe Grund wie bei {@link LotterieBefehle.Uhr}: Ein
     * {@code Instant.now()} im Zweig machte jede Probe von der Tageszeit abhaengig, und
     * das Zeitfenster ist hier die halbe Entscheidung - eine Probe, die es nicht stellen
     * kann, prueft den Befehl nicht.
     */
    interface Uhr {
        Instant jetzt();
    }

    private final Rueckrollung rollung;
    private final Blockbuchwerte werte;
    private final Sprache sprache;
    private final Uhr uhr;
    private final Logger protokoll;

    RueckrollBefehle(Rueckrollung rollung, Blockbuchwerte werte, Sprache sprache,
                     Uhr uhr, Logger protokoll) {
        if (rollung == null || werte == null || sprache == null || uhr == null
                || protokoll == null) {
            throw new IllegalArgumentException("Rueckrollung, Werte, Sprache, Uhr und"
                    + " Protokoll sind Pflicht");
        }
        this.rollung = rollung;
        this.werte = werte;
        this.sprache = sprache;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Der Zweig, den {@link BlockBefehle#blockbuch()} einhaengt.
     *
     * <p>NICHT gebaut ({@code .build()} fehlt), damit der Baum ihn mit {@code .then(...)}
     * aufnehmen kann - dasselbe Muster wie bei {@code blockbuch()} selbst.
     *
     * <p><b>Die Zahlen kommen OHNE Brigadier-Grenzen herein</b>, und das ist kein
     * Versaeumnis: Ein {@code integer(0, 250)} liesse Brigadier bei einer 500 auf
     * ENGLISCH antworten, und zwar mit einer Meldung, die den eingestellten Wert nicht
     * kennt. Geprueft wird darum hier, gegen {@link Rueckweggrenzen} - und die Absage ist
     * ein Satz aus der Sprachdatei, der den Pfad nennt, an dem die Grenze steht.
     *
     * <p><b>Die Welt kommt als KURZER Name</b> - {@code altfurt}, nicht
     * {@code freimarken:altfurt} -, und das ist eine gemessene Grenze von Brigadier:
     * {@code StringArgumentType.word()} erlaubt nur {@code [0-9a-zA-Z_.+-]}, ein
     * Doppelpunkt bricht das Argument ab. Gedeckt ist es dadurch, dass DREI Stellen
     * dem Betreiber die kurze Form zeigen und keine die lange: die Vorschlaege, die
     * Absage bei unbekannter Welt und die vorgegebene Bestaetigungszeile. Die NAHT
     * nimmt trotzdem beide Formen, denn die Vorschau am eigenen Standort gibt ihr
     * {@code World.key().asString()} herein - also die lange.
     */
    LiteralArgumentBuilder<CommandSourceStack> zurueck() {
        return Commands.literal(ZURUECK)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .then(Commands.argument("radius", IntegerArgumentType.integer())
                        .then(Commands.argument("minuten", IntegerArgumentType.integer())
                                .executes(this::schauHier)
                                .then(Commands.literal(BEI)
                                        .then(Commands.argument("welt",
                                                        StringArgumentType.word())
                                                .suggests((zusammenhang, bauer) -> {
                                                    for (String name
                                                            : rollung.weltnamen()) {
                                                        bauer.suggest(name);
                                                    }
                                                    return bauer.buildFuture();
                                                })
                                                .then(Commands.argument("x",
                                                                IntegerArgumentType
                                                                        .integer())
                                                        .then(Commands.argument("y",
                                                                        IntegerArgumentType
                                                                                .integer())
                                                                .then(Commands.argument(
                                                                                "z",
                                                                                IntegerArgumentType
                                                                                        .integer())
                                                                        .executes(this::schauDort)
                                                                        .then(Commands
                                                                                .literal(BESTAETIGEN)
                                                                                .executes(this::rolle)))))))));
    }

    /**
     * Die Vorschau am eigenen Standort.
     *
     * <p>Spielerpflichtig, und die Absage ist eine GELESENE: Der Betreiber auf der
     * Konsole soll den Weg lesen, der ihm offensteht - den Zweig mit Welt und
     * Koordinaten -, und nicht "unbekannter Befehl". Dieselbe Entscheidung wie bei
     * {@link BlockBefehle} und aus demselben Grund.
     */
    private int schauHier(CommandContext<CommandSourceStack> zusammenhang) {
        if (!(zusammenhang.getSource().getExecutor() instanceof Player spieler)) {
            sende(zusammenhang, sprache.bau(BlockSatz.RUECKWEG_KEIN_ORT, 1L,
                    Einsatz.keine()));
            return 0;
        }
        Rueckrollung.Weltlage welt = rollung.welt(spieler.getWorld().key().asString());
        if (welt == null) {
            // Unerreichbar, solange ein Spieler in einer Welt steht, die der Server
            // kennt - und trotzdem gefragt: Die Naht gibt null fuer "gibt es nicht",
            // und ein ungefragtes null waere hier eine NullPointerException mitten im
            // Befehl. Die Absage nennt dieselben Welten wie beim getippten Namen.
            unbekannteWelt(zusammenhang, spieler.getWorld().key().asString());
            return 0;
        }
        return zeige(zusammenhang, welt, new Blockort(welt.schluessel(),
                spieler.getLocation().getBlockX(), spieler.getLocation().getBlockY(),
                spieler.getLocation().getBlockZ()));
    }

    /** Die Vorschau an einem genannten Ort - auch von der Konsole. */
    private int schauDort(CommandContext<CommandSourceStack> zusammenhang) {
        Rueckrollung.Weltlage welt = welt(zusammenhang);
        if (welt == null) {
            return 0;
        }
        return zeige(zusammenhang, welt, mitte(zusammenhang, welt));
    }

    /**
     * Die Ausfuehrung.
     *
     * <p><b>Es wird ERNEUT gelesen</b> und nicht mit dem Plan der Vorschau gearbeitet -
     * die Begruendung steht im Kopf von {@link Rueckrollung}: Zwischen Vorschau und
     * Bestaetigung kann weitergebaut worden sein. Was der Betreiber bestaetigt, ist der
     * Kasten und das Fenster, nicht eine Liste von Bloecken.
     *
     * <p>Alle Riegel laufen noch einmal, und zwar DIESELBEN. Eine Bestaetigung, die
     * ihre Pruefungen ueberspringt, weil "die Vorschau hat das schon geprueft", ist
     * genau die Hintertuer, durch die ein gesenkter Wert nicht wirkt - und hier
     * waere sie teuer: Zwischen Vorschau und Bestaetigung liegt eine beliebig lange
     * Zeit, in der sich die WELT geaendert haben kann (die Hoehe, die Auswahl der
     * Welt, die Zahl der Zeilen). Die GRENZEN selbst aendern sich nur bei einem
     * Neustart - sie stehen in {@code OHNE_NEULADEZEILE}, Tafel 31cw haelt das.
     */
    private int rolle(CommandContext<CommandSourceStack> zusammenhang) {
        Rueckrollung.Weltlage welt = welt(zusammenhang);
        if (welt == null) {
            return 0;
        }
        Blockort mitte = mitte(zusammenhang, welt);
        Rueckrollung.Schau schau = geprueft(zusammenhang, welt, mitte);
        if (schau == null) {
            return 0;
        }
        if (schau.zuViele() || schau.leer()) {
            // Dieselben Zeilen wie in der Vorschau. Ein eigener Satz "zwischen Vorschau
            // und Bestaetigung hat sich etwas geaendert" waere praeziser und zugleich
            // nutzlos: Was der Betreiber jetzt braucht, ist der Grund, warum nichts
            // geschieht - und den nennt die Vorschau schon.
            sende(zusammenhang, Blocktext.schauZeilen(sprache, mitte,
                    radius(zusammenhang), minuten(zusammenhang), schau,
                    Blocktext.bestaetigung(mitte, radius(zusammenhang),
                            minuten(zusammenhang))));
            return 0;
        }
        Rueckrollung.Lauf lauf;
        try {
            lauf = rollung.fuehre(schau, welt.schluessel(),
                    zusammenhang.getSource().getSender().getName());
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Die Rueckrollung ist gescheitert - es kann"
                    + " ein Teil gesetzt sein.", fehler);
            sende(zusammenhang, sprache.bau(BlockSatz.GESTOERT, 1L, Einsatz.keine()));
            return 0;
        }
        sende(zusammenhang, Blocktext.laufZeilen(sprache, lauf));
        return Command.SINGLE_SUCCESS;
    }

    /** Zeigt die Vorschau - oder die Absage, die an ihrer Stelle steht. */
    private int zeige(CommandContext<CommandSourceStack> zusammenhang,
                      Rueckrollung.Weltlage welt, Blockort mitte) {
        Rueckrollung.Schau schau = geprueft(zusammenhang, welt, mitte);
        if (schau == null) {
            return 0;
        }
        int radius = radius(zusammenhang);
        int minuten = minuten(zusammenhang);
        sende(zusammenhang, Blocktext.schauZeilen(sprache, mitte, radius, minuten,
                schau, Blocktext.bestaetigung(mitte, radius, minuten)));
        return schau.leer() || schau.zuViele() ? 0 : Command.SINGLE_SUCCESS;
    }

    /**
     * Alle Riegel und dann die Schau - oder {@code null}, wenn abgesagt wurde.
     *
     * <p><b>Die EINE Stelle, an der die Riegel stehen</b>, und darum liegt sie zwischen
     * Vorschau und Ausfuehrung: Zwei Ketten derselben Pruefungen liefen auseinander, und
     * zwar an der Bestaetigung - also genau dort, wo das Ueberspringen etwas kostet.
     *
     * <p>Die Reihenfolge ist nach Gewissheit geordnet, wie bei
     * {@link BlockBefehle}: zuerst die getippten Zahlen (sicher), dann die Hoehe
     * (sicher), dann die abgewaehlte Welt (eine Einstellung), und erst zuletzt die
     * Abfrage. So liest der Moderator nie "hier steht nichts", wenn der wahre Grund ein
     * anderer ist.
     */
    private Rueckrollung.Schau geprueft(CommandContext<CommandSourceStack> zusammenhang,
                                        Rueckrollung.Weltlage welt, Blockort mitte) {
        int radius = radius(zusammenhang);
        int minuten = minuten(zusammenhang);
        Rueckweggrenzen grenzen = rollung.grenzen();
        if (!grenzen.erlaubterRadius(radius)) {
            sende(zusammenhang, sprache.bau(BlockSatz.RUECKWEG_ZU_WEIT, 1L,
                    Einsatz.von("radius", radius)
                            .mit("hoechstens", grenzen.hoechsterRadius())));
            return null;
        }
        if (!grenzen.erlaubteMinuten(minuten)) {
            sende(zusammenhang, sprache.bau(BlockSatz.RUECKWEG_ZU_LANG, 1L,
                    Einsatz.von("minuten", minuten)
                            .mit("hoechstens", grenzen.hoechsteMinuten())));
            return null;
        }
        if (mitte.y() < welt.unten() || mitte.y() >= welt.oben()) {
            // Erreichbar, sobald jemand die Hoehe TIPPT - und in Altfurt liegt der Boden
            // bei y -61, die Unterkante bei -64. Ein Vertipper ist dort schnell
            // ausserhalb, und kastenUm wirft dann; eine geworfene Ausnahme im Befehl
            // waere eine rote Konsolenmeldung statt einer Auskunft.
            sende(zusammenhang, sprache.bau(BlockSatz.RUECKWEG_KEINE_HOEHE, 1L,
                    Einsatz.von("hoehe", mitte.y()).mit("unten", welt.unten())
                            .mit("oben", welt.oben() - 1)));
            return null;
        }
        if (werte.fuerWelt(welt.schluessel()).leer()) {
            // "Diese Welt schreibt nichts" ist NICHT "hier ist nichts geschehen", und
            // der Unterschied ist bei einer Rueckrollung noch wichtiger als beim Lesen:
            // Ein Moderator, der ihn verwechselt, haelt einen Grief fuer unmoeglich.
            sende(zusammenhang, sprache.bau(BlockSatz.AUS, 1L, Einsatz.keine()));
            return null;
        }
        Instant bis = uhr.jetzt();
        Instant von = bis.minus(Duration.ofMinutes(minuten));
        Quader kasten = Rueckrollplan.kastenUm(mitte, radius, welt.unten(),
                welt.oben());
        try {
            return rollung.schau(kasten, welt.schluessel(), von, bis);
        } catch (RuntimeException fehler) {
            // Eine Stoerung darf NICHT als "hier steht nichts" aussehen: Ein Netzfehler
            // liesse den Moderator sonst glauben, der Grief sei nicht aufgeschrieben.
            protokoll.log(Level.WARNING, "Das Blockbuch konnte fuer die Rueckrollung"
                    + " nicht gelesen werden - der Befehl hat abgesagt.", fehler);
            sende(zusammenhang, sprache.bau(BlockSatz.GESTOERT, 1L, Einsatz.keine()));
            return null;
        }
    }

    /** Die Welt aus dem Befehl - oder {@code null} samt Absage. */
    private Rueckrollung.Weltlage welt(CommandContext<CommandSourceStack> zusammenhang) {
        String getippt = StringArgumentType.getString(zusammenhang, "welt");
        Rueckrollung.Weltlage welt = rollung.welt(getippt);
        if (welt == null) {
            unbekannteWelt(zusammenhang, getippt);
            return null;
        }
        return welt;
    }

    /** Der Mittelpunkt aus dem Befehl, in der Welt des Befehls. */
    private static Blockort mitte(CommandContext<CommandSourceStack> zusammenhang,
                                  Rueckrollung.Weltlage welt) {
        return new Blockort(welt.schluessel(),
                IntegerArgumentType.getInteger(zusammenhang, "x"),
                IntegerArgumentType.getInteger(zusammenhang, "y"),
                IntegerArgumentType.getInteger(zusammenhang, "z"));
    }

    private static int radius(CommandContext<CommandSourceStack> zusammenhang) {
        return IntegerArgumentType.getInteger(zusammenhang, "radius");
    }

    private static int minuten(CommandContext<CommandSourceStack> zusammenhang) {
        return IntegerArgumentType.getInteger(zusammenhang, "minuten");
    }

    /**
     * Die Welt gibt es nicht - und die Absage nennt die, die es gibt.
     *
     * <p>Die Liste gehoert dazu, weil dieser Befehl auch von der Konsole getippt wird und
     * es dort keine Vorschlaege gibt. Ohne sie raet der Betreiber zwischen
     * {@code altfurt} und {@code freimarken:altfurt}.
     */
    private void unbekannteWelt(CommandContext<CommandSourceStack> zusammenhang,
                                String getippt) {
        sende(zusammenhang, sprache.bau(BlockSatz.RUECKWEG_UNBEKANNTE_WELT, 1L,
                Einsatz.von("welt", getippt)
                        .mit("welten", String.join(", ", rollung.weltnamen()))));
    }

    /** Geantwortet wird dem SENDER - dieselbe Teilung wie bei {@link BlockBefehle}. */
    private static void sende(CommandContext<CommandSourceStack> zusammenhang,
                              List<Component> zeilen) {
        zeilen.forEach(zusammenhang.getSource().getSender()::sendMessage);
    }
}
