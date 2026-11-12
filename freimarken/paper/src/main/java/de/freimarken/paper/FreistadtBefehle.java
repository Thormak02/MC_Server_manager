package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Absteckbuch;
import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Grundstuecksbuch;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Tat;
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
     * Der Name des Preisarguments von {@code grundstueck anbieten}.
     *
     * <p>Als {@code longArg(0L)} und nicht als Wort: Brigadier weist damit alles ab, was
     * keine Zahl ist, und zwar mit seiner eigenen Fehlermarkierung UNTER dem falschen
     * Zeichen - besser als jede Absage, die wir danach schreiben koennten. Die untere
     * Grenze 0 ist keine Spielregel, sondern dieselbe Zahl, die auch das Buch prueft:
     * Ein Grundstueck, fuer dessen Abnahme der Verkaeufer zahlt, waere eine Geldquelle.
     */
    static final String ARGUMENT_PREIS = "preis";

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

    /**
     * Das Recht, Grundstuecke zuzuschneiden [Teil C2].
     *
     * <p>Ein DRITTES Recht und nicht {@link #RECHT_GRUENDEN}: Wer gruendet, bewegt
     * 400.000 Mark; wer zuschneidet, bewegt nichts und arbeitet nur auf seinem eigenen
     * Feld. Dass das Buch ohnehin pruefen muss, ob ihm die Stadt gehoert, macht das
     * Recht nicht ueberfluessig - es ist die Stelle, an der ein Betreiber den ganzen
     * Zweig abschalten kann, ohne die Staedte anzutasten.
     *
     * <p>Vorgabe fuer alle, wie die beiden anderen: Der eigentliche Riegel ist der
     * Besitz der Stadt.
     */
    static final String RECHT_GRUNDSTUECK = "freimarken.freistadt.grundstueck";

    /**
     * Das Recht, ein angebotenes Grundstueck zu KAUFEN [Teil C3].
     *
     * <p>Ein viertes Recht, und es trennt die andere Seite ab: {@link #RECHT_GRUNDSTUECK}
     * gehoert dem Stadtbesitzer, der sein Feld teilt und Preise setzt. Kaufen tut ein
     * ANDERER - jemand, der (noch) keine Stadt hat. Und das ist genau der Weg, auf dem
     * man STADTBUERGER wird.
     *
     * <p><b>BERICHTIGT [12.11.2026, Teil D5]</b>: Hier stand, Schritt 12 haenge daran
     * den Rang Buerger. Das war eine Zusage, die der Plan nicht gibt - Plan 2318 nennt
     * als Rangvorteil nur "Freistadt gruenden", nicht das Kaufen. Der Satz war in sich
     * gedreht: Er machte den RANG Buerger zur Voraussetzung fuer den Weg, auf dem man
     * STADTBUERGER wird. Mit dem Gatter koennte ein Siedler kein Grundstueck kaufen, und
     * das verschenkte Grundstueck fuer angeworbene Buerger (Teil C3) haette keinen
     * Adressaten mehr: Wer vierzig Stunden gespielt hat, nimmt eher die gratis erste
     * Parzelle in der Marktstadt.
     *
     * <p>Zwei Buerger, zwei Dinge: Der RANG kommt aus Spielzeit (Schritt 12), der
     * STADTBUERGER aus Grundbesitz (Teil D). Sie haben nichts miteinander zu tun, und
     * darum traegt das Neue ueberall seinen eigenen Namen.
     *
     * <p>Vorgabe fuer alle: Der eigentliche Riegel ist das Geld.
     */
    static final String RECHT_KAUFEN = "freimarken.freistadt.kaufen";

    /**
     * Darf ein Stadtbesitzer die Buergerrechte seiner Stadt einstellen.
     *
     * <p><b>Ein fuenftes Recht</b>, und es trennt eine neue Sache ab: Die drei bisherigen
     * gehen um Geld und um Flaechen, dieses um die REGELN eines Gebiets. Ein Betreiber,
     * der die Staedte ohne Buergerrechte laufen lassen will - alles bleibt beim
     * Stadtbesitzer -, nimmt es weg, und der ganze Zweig verschwindet; die Staedte
     * selbst bleiben unberuehrt.
     *
     * <p>Das ANZEIGEN haengt NICHT daran: Was in einer Stadt gilt, darf jeder lesen, der
     * dort steht. Ein Buerger muss wissen, was er darf, ohne den Besitzer zu fragen -
     * und ein Recht darauf waere eine Auskunft, die man sich verdienen muss.
     */
    static final String RECHT_RECHTE = "freimarken.freistadt.rechte";

    /** Das Argument des Rechtezweigs - der Name eines Buergerrechts. */
    private static final String ARGUMENT_RECHT = "recht";

    private final Freistadtbuch buch;
    private final Kasse kasse;
    private final Sprache sprache;
    private final String weltschluessel;
    private final IntSupplier hoechstzahl;

    /**
     * Das Buch der Grundstuecke - {@code null}, solange es keine gibt.
     *
     * <p>Null-faehig wie das Freistadtbuch in {@link WarpBefehle}: Faellt die
     * Grundstuecksablage beim Start aus, soll {@code /freistadt} weiter die Liste zeigen
     * und gruenden lassen. Dann sagt der Grundstueckszweig, dass er nicht kann - statt
     * stumm nichts zu tun.
     */
    private final Grundstuecksbuch grundstuecksbuch;

    /** Die abgesteckten Ecken je Spieler - fluechtig, siehe {@link Absteckbuch}. */
    private final Absteckbuch absteckbuch;

    /**
     * Das Serverprotokoll - gebraucht fuer GENAU EINEN Fall.
     *
     * <p>Den verlorenen Erloes eines Grundstuecksverkaufs: Das Geld ist abgebucht, das
     * Grundstueck uebertragen, und die Gutschrift an den Verkaeufer ist gescheitert.
     * Dass das nur hier protokolliert wird und nicht im Kern, ist Absicht - WAS eine
     * verlorene Gutschrift bedeutet, entscheidet der Betreiber.
     */
    private final java.util.logging.Logger protokoll;

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
                     String weltschluessel, IntSupplier hoechstzahl,
                     Grundstuecksbuch grundstuecksbuch, Absteckbuch absteckbuch,
                     java.util.logging.Logger protokoll) {
        this.buch = buch;
        this.kasse = kasse;
        this.sprache = sprache;
        this.weltschluessel = weltschluessel == null ? "" : weltschluessel;
        this.hoechstzahl = hoechstzahl;
        this.grundstuecksbuch = grundstuecksbuch;
        this.absteckbuch = absteckbuch;
        this.protokoll = protokoll;
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
                // DIE GRUNDSTUECKE [Teil C2]. Das Literal traegt ein eigenes
                // .executes, und das ist keine Bequemlichkeit: Ein passendes Literal
                // verdeckt in Brigadier jedes Geschwister vollstaendig
                // (CommandNode.getRelevantNodes) - ohne eigenes .executes endete
                // "/freistadt grundstueck" in Brigadiers englischem "Unknown or
                // incomplete command". Dieselbe Falle wie bei /warp freistadt.
                .then(Commands.literal("grundstueck")
                        .requires(quelle -> quelle.getSender()
                                .hasPermission(RECHT_GRUNDSTUECK))
                        .executes(this::zeigeGrundstuecke)
                        .then(Commands.literal("ecke")
                                .executes(this::steckeEcke))
                        .then(Commands.literal("anlegen")
                                .executes(this::schneideZu))
                        .then(Commands.literal("aufloesen")
                                .executes(this::loeseAuf))
                        // --- Teil C3 ---------------------------------------
                        .then(Commands.literal("anbieten")
                                .then(Commands.argument(ARGUMENT_PREIS,
                                                LongArgumentType.longArg(0L))
                                        .executes(this::biete)))
                        .then(Commands.literal("zurueckziehen")
                                .executes(this::zieheZurueck))
                        .then(Commands.literal("kaufen")
                                .requires(quelle -> quelle.getSender()
                                        .hasPermission(RECHT_KAUFEN))
                                .executes(this::kaufe)))
                // Die Angebote stehen NEBEN dem Grundstueckszweig und nicht darin: Sie
                // sind eine Auskunft fuer jeden, der etwas sucht - und dafuer soll
                // niemand das Recht brauchen, das dem Stadtbesitzer gehoert.
                .then(Commands.literal("angebote")
                        .executes(this::zeigeAngebote))
                // DIE BUERGERRECHTE [Teil D3]. Der Zweig steht NEBEN grundstueck und
                // nicht darin: Er geht nicht um eine Flaeche, sondern um die Regeln der
                // ganzen Stadt - und das ANZEIGEN soll jeder duerfen, der dort steht.
                //
                // Eigenes .executes am Literal, aus demselben Grund wie bei
                // grundstueck: Ein passendes Literal verdeckt in Brigadier jedes
                // Geschwister vollstaendig.
                //
                // "an <recht>" und nicht "<recht> an": So liegen die zwei Literale
                // unmittelbar unter rechte, und die Vorschlagsliste der drei Rechte
                // kommt erst, wenn die Richtung klar ist. Umgekehrt haette ein Spieler,
                // der "an" tippt, es als Wert des Rechte-Arguments gesetzt - und bekaeme
                // "an ist kein Buergerrecht".
                // DIE STADTBUERGERLISTE [Teil D5]. NEBEN rechte und nicht darin: Die
                // eine Liste sagt, WER hier wohnt, die andere, WAS sie duerfen - zwei
                // Fragen, und beide duerfen alle lesen, die in der Stadt stehen.
                .then(Commands.literal("stadtbuerger")
                        .executes(this::zeigeStadtbuerger))
                .then(Commands.literal("rechte")
                        .executes(this::zeigeRechte)
                        .then(Commands.literal("an")
                                .requires(quelle -> quelle.getSender()
                                        .hasPermission(RECHT_RECHTE))
                                .then(Commands.argument(ARGUMENT_RECHT,
                                                StringArgumentType.word())
                                        .suggests(FreistadtBefehle::schlageRechteVor)
                                        .executes(zusammenhang ->
                                                setzeRecht(zusammenhang, true))))
                        .then(Commands.literal("aus")
                                .requires(quelle -> quelle.getSender()
                                        .hasPermission(RECHT_RECHTE))
                                .then(Commands.argument(ARGUMENT_RECHT,
                                                StringArgumentType.word())
                                        .suggests(FreistadtBefehle::schlageRechteVor)
                                        .executes(zusammenhang ->
                                                setzeRecht(zusammenhang, false)))))
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

    // ------------------------------------ Angebot und Verkauf [Teil C3]

    /** Das Grundstueck unter den Fuessen zum Verkauf stellen. */
    private int biete(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (grundstuecksbuch == null) {
            sende(spieler, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        long preis = LongArgumentType.getLong(zusammenhang, ARGUMENT_PREIS);
        antworteAufZuschnitt(spieler, grundstuecksbuch.biete(weltschluessel,
                spieler.getUniqueId(), spieler.getLocation().getBlockX(),
                spieler.getLocation().getBlockZ(), preis));
        return Command.SINGLE_SUCCESS;
    }

    /** Das Angebot zuruecknehmen. */
    private int zieheZurueck(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (grundstuecksbuch == null) {
            sende(spieler, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        antworteAufZuschnitt(spieler, grundstuecksbuch.biete(weltschluessel,
                spieler.getUniqueId(), spieler.getLocation().getBlockX(),
                spieler.getLocation().getBlockZ(),
                de.freimarken.kern.Grundstueck.KEIN_ANGEBOT));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Das angebotene Grundstueck unter den Fuessen kaufen - das Abnahmekriterium.
     *
     * <p><b>Beide Seiten erfahren es</b>, und der Verkaeufer nur, wenn er online ist:
     * Eine Nachricht an einen abgemeldeten Spieler gibt es in diesem Projekt nicht (das
     * Abholfach ist fuer WARE, nicht fuer Worte). Wer offline verkauft, sieht es in
     * {@code /mark verlauf} - und darum tragen die drei Buchungsgruende eigene Worte.
     *
     * <p>Der VERLORENE ERLOES wird hier protokolliert und nicht im Kern: Was eine
     * gescheiterte Gutschrift bedeutet, entscheidet der Betreiber. Die Zeile nennt
     * beide Namen und den Betrag, damit sie von Hand zu heilen ist.
     */
    private int kaufe(CommandContext<CommandSourceStack> zusammenhang) {
        Player kaeufer = spielerAus(zusammenhang);
        if (kaeufer == null) {
            return 0;
        }
        if (grundstuecksbuch == null) {
            sende(kaeufer, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        // Der Eigentuemer VOR dem Kauf: Danach ist es der Kaeufer selbst, und dann
        // waere nicht mehr zu sagen, wem die Meldung gilt.
        var vorher = grundstuecksbuch.anStelle(weltschluessel,
                kaeufer.getLocation().getBlockX(), kaeufer.getLocation().getBlockZ());
        long guthaben = kasse.guthaben(kaeufer.getUniqueId());
        Grundstuecksbuch.Entscheid entscheid = grundstuecksbuch.kaufe(weltschluessel,
                kaeufer.getUniqueId(), kaeufer.getLocation().getBlockX(),
                kaeufer.getLocation().getBlockZ(),
                (wer, betrag, fehler) -> protokoll.log(java.util.logging.Level.SEVERE,
                        "Ein Grundstueck ist verkauft, aber " + Spielername.von(wer)
                                + " hat seine " + betrag + " Mark NICHT bekommen. Von"
                                + " Hand gutzuschreiben - der Besitzerwechsel ist"
                                + " vollzogen, und ein Zuruecknehmen waere schlimmer:"
                                + " Es koennte Geld aus dem Nichts schaffen.", fehler));

        java.util.UUID verkaeufer = vorher.map(
                de.freimarken.kern.Grundstueck::eigentuemer).orElse(null);
        sende(kaeufer, Freistadttext.kaufZeilen(sprache, entscheid,
                verkaeufer == null ? "?" : Spielername.von(verkaeufer), guthaben));

        // NACH DEM BESCHEID gefragt und nicht nach erfolgreich(): Jenes Wort meint
        // ERTEILT, also den Zuschnitt - ein Kauf traegt GEKAUFT. Mit erfolgreich()
        // stand hier ein Zweig, der nie lief, und der Verkaeufer erfuhr nichts.
        if (entscheid.bescheid() == Grundstuecksbuch.Bescheid.GEKAUFT
                && verkaeufer != null) {
            // Ueber Bukkit und nicht ueber kaeufer.getServer(): Jeder andere Zweig
            // dieses Programms sucht so (AnfragenBefehle, BlockBefehle, ShopBefehle),
            // und nur FreimarkenPlugin fragt getServer - weil es selbst das Plugin
            // ist. Zwei Wege zu derselben Tat heisst hier auch: Der Spielerstellvertreter
            // der Proben kennt den zweiten nicht, und dieser Zweig war darum nicht
            // zu durchlaufen.
            Player derAndere = org.bukkit.Bukkit.getPlayer(verkaeufer);
            if (derAndere != null) {
                sende(derAndere, Freistadttext.verkaufZeilen(sprache, entscheid,
                        kaeufer.getName(),
                        grundstuecksbuch.verkaufsgebuehrProzent()));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Alle Angebote dieser Welt - ueber alle Staedte. */
    private int zeigeAngebote(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (grundstuecksbuch == null || weltschluessel.isEmpty()) {
            sende(spieler, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        sende(spieler, Freistadttext.angeboteZeilen(sprache,
                grundstuecksbuch.angebote(weltschluessel), Spielername::von));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die EINE Antwort auf einen Entscheid, der kein Kauf ist.
     *
     * <p>Zuschneiden, Aufloesen, Anbieten und Zuruecknehmen gehen alle hier hindurch:
     * Vier Zweige mit vier eigenen Sendefolgen waeren vier Stellen, an denen das
     * Guthaben vergessen wird - und die Absage "zu wenig Mark" nennt daraus den
     * Fehlbetrag.
     */
    private void antworteAufZuschnitt(Player spieler,
                                      Grundstuecksbuch.Entscheid entscheid) {
        sende(spieler, Freistadttext.zuschnittZeilen(sprache, entscheid,
                grundstuecksbuch.hoechstzellen(),
                kasse.guthaben(spieler.getUniqueId())));
    }

    // ----------------------------------------- Die Grundstuecke [Teil C2]

    /**
     * Eine Ecke abstecken - an der Stelle, an der der Spieler steht.
     *
     * <p>Kein Werkzeug und kein Klick, sondern ein Befehl an der eigenen Stelle: Ein
     * Klickwerkzeug braeuchte ein eigenes Bukkit-Ereignis und einen Gegenstand, den
     * jemand verlieren kann. So steht der ganze Zweig in diesem Baum.
     *
     * <p>Geprueft wird hier NICHTS - nicht die Welt, nicht die Stadt, nicht das Feld.
     * Eine Ecke ist eine Marke und noch keine Handlung; wer sie an einer unmoeglichen
     * Stelle setzt, erfaehrt es beim Anlegen. Umgekehrt muesste dieser Zweig jede Regel
     * des Buchs ein zweites Mal kennen.
     */
    private int steckeEcke(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (grundstuecksbuch == null) {
            sende(spieler, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        Absteckbuch.Stand stand = absteckbuch.stecke(spieler.getUniqueId(),
                spieler.getLocation().getBlockX(), spieler.getLocation().getBlockZ());
        sende(spieler, Freistadttext.eckeZeilen(sprache, stand,
                grundstuecksbuch.plan()));
        return Command.SINGLE_SUCCESS;
    }

    /** Aus den zwei abgesteckten Ecken ein Grundstueck machen. */
    private int schneideZu(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (grundstuecksbuch == null) {
            sende(spieler, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        var fertige = absteckbuch.fertige(spieler.getUniqueId());
        if (fertige.isEmpty()) {
            sende(spieler, Freistadttext.fehlendeEckenZeilen(sprache,
                    absteckbuch.zahl(spieler.getUniqueId())));
            return Command.SINGLE_SUCCESS;
        }
        Grundstuecksbuch.Entscheid entscheid = grundstuecksbuch.schneideZu(
                weltschluessel, spieler.getUniqueId(), fertige.get().eine(),
                fertige.get().andere());
        if (entscheid.erfolgreich()) {
            // VERGESSEN nach dem Erfolg: Blieben die Ecken stehen, versuchte das
            // naechste anlegen dasselbe Grundstueck noch einmal - und die Absage
            // "hier liegt schon ein Grundstueck" waere die eigene.
            absteckbuch.vergiss(spieler.getUniqueId());
        }
        antworteAufZuschnitt(spieler, entscheid);
        return Command.SINGLE_SUCCESS;
    }

    /** Das Grundstueck aufloesen, auf dem der Spieler steht. */
    private int loeseAuf(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (grundstuecksbuch == null) {
            sende(spieler, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        Grundstuecksbuch.Entscheid entscheid = grundstuecksbuch.loeseAuf(weltschluessel,
                spieler.getUniqueId(), spieler.getLocation().getBlockX(),
                spieler.getLocation().getBlockZ());
        sende(spieler, Freistadttext.aufgeloestZeilen(sprache, entscheid,
                grundstuecksbuch.hoechstzellen(),
                kasse.guthaben(spieler.getUniqueId())));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die Grundstuecke der Stadt, in der der Spieler steht.
     *
     * <p>Die Stadt kommt aus dem ORT und nicht aus einem Namen: Wer zuschneidet, steht
     * in seiner Stadt - und ein Name waere ein zweiter Weg, dieselbe Stadt zu nennen.
     */
    private int zeigeGrundstuecke(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (grundstuecksbuch == null || weltschluessel.isEmpty()) {
            sende(spieler, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        var stadt = buch.stadtAn(weltschluessel, spieler.getLocation().getBlockX(),
                spieler.getLocation().getBlockZ());
        if (stadt.isEmpty()) {
            antworteAufZuschnitt(spieler, new Grundstuecksbuch.Entscheid(
                    Grundstuecksbuch.Bescheid.KEINE_STADT, java.util.Optional.empty(),
                    0, 0L, 0L));
            return Command.SINGLE_SUCCESS;
        }
        sende(spieler, Freistadttext.grundstueckZeilen(sprache, stadt.get().name(),
                grundstuecksbuch.inStadt(stadt.get().id()), Spielername::von));
        return Command.SINGLE_SUCCESS;
    }

    // --- Teil D5: die Stadtbuergerliste ----------------------------------------

    /**
     * Zeigt, wer in dieser Stadt Buerger ist.
     *
     * <p>Ohne Rechtepruefung und ohne Besitzfrage, wie {@code zeigeRechte} und
     * {@code zeigeGrundstuecke}: Wer in einer Stadt steht, darf lesen, wer dort wohnt.
     *
     * <p><b>Die Liste wird GERECHNET und nicht gelesen</b>: Es gibt keine Tabelle
     * stadt_buerger. {@code inStadt} gibt die Grundstuecke, und
     * {@code Stadtbuergerschaft} macht daraus die Buerger - dieselbe Regel, nach der
     * auch die Bauwache urteilt.
     */
    private int zeigeStadtbuerger(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (grundstuecksbuch == null || weltschluessel.isEmpty()) {
            sende(spieler, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        var stadt = buch.stadtAn(weltschluessel, spieler.getLocation().getBlockX(),
                spieler.getLocation().getBlockZ());
        if (stadt.isEmpty()) {
            sende(spieler, Freistadttext.rechteAntwortZeilen(sprache,
                    new Freistadtbuch.Rechteentscheid(
                            Freistadtbuch.Rechtebescheid.KEINE_STADT,
                            java.util.Optional.empty(), java.util.Set.of()),
                    Tat.BAUEN, true, Spielername::von));
            return Command.SINGLE_SUCCESS;
        }
        sende(spieler, Freistadttext.stadtbuergerZeilen(sprache, stadt.get().name(),
                stadt.get().eigentuemer(),
                de.freimarken.kern.Stadtbuergerschaft.aus(stadt.get(),
                        grundstuecksbuch.inStadt(stadt.get().id())),
                Spielername::von));
        return Command.SINGLE_SUCCESS;
    }

    // --- Teil D3: die Buergerrechte --------------------------------------------

    /**
     * Zeigt, was die Stadtbuerger hier duerfen.
     *
     * <p>Ohne Rechtepruefung und ohne Besitzfrage: Wer in einer Stadt steht, darf lesen,
     * was dort gilt. Dieselbe Entscheidung wie bei {@code zeigeGrundstuecke}.
     */
    private int zeigeRechte(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (weltschluessel.isEmpty()) {
            sende(spieler, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        var stadt = buch.stadtAn(weltschluessel, spieler.getLocation().getBlockX(),
                spieler.getLocation().getBlockZ());
        if (stadt.isEmpty()) {
            sende(spieler, Freistadttext.rechteAntwortZeilen(sprache,
                    new Freistadtbuch.Rechteentscheid(
                            Freistadtbuch.Rechtebescheid.KEINE_STADT,
                            java.util.Optional.empty(), java.util.Set.of()),
                    Tat.BAUEN, true, Spielername::von));
            return Command.SINGLE_SUCCESS;
        }
        sende(spieler, Freistadttext.rechteZeilen(sprache, stadt.get().name(),
                stadt.get().buergerrechte()));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Schaltet ein Buergerrecht an oder aus.
     *
     * <p>Das WORT wird hier gepruefet und nicht im Kern: {@code setzeRecht} nimmt eine
     * {@link Tat}, und ein unbekanntes Wort ist eine Sache des Befehls - der Spieler hat
     * sich vertippt, und die Vorschlagsliste nennt die drei.
     */
    private int setzeRecht(CommandContext<CommandSourceStack> zusammenhang, boolean an) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (weltschluessel.isEmpty()) {
            sende(spieler, Freistadttext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        String wort = StringArgumentType.getString(zusammenhang, ARGUMENT_RECHT);
        Tat recht;
        try {
            recht = Tat.ausSchluessel(wort.toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unbekannt) {
            sende(spieler, Freistadttext.unbekanntesRechtZeilen(sprache, wort));
            return Command.SINGLE_SUCCESS;
        }
        Freistadtbuch.Rechteentscheid entscheid = buch.setzeRecht(weltschluessel,
                spieler.getUniqueId(), spieler.getLocation().getBlockX(),
                spieler.getLocation().getBlockZ(), recht, an);
        sende(spieler, Freistadttext.rechteAntwortZeilen(sprache, entscheid, recht, an,
                Spielername::von));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die drei Rechte als Vorschlag.
     *
     * <p>Aus {@code Tat.values()} und nicht aus einer Liste hier: Ein viertes Recht
     * steht dann ohne Zutun in der Vorschlagsliste. Die SCHLUESSEL werden
     * vorgeschlagen, nicht die Worte aus der Sprachdatei - getippt wird der Schluessel,
     * und ein Vorschlag, den der Befehl nicht annimmt, ist schlimmer als keiner.
     */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier
            .suggestion.Suggestions> schlageRechteVor(
            CommandContext<CommandSourceStack> zusammenhang,
            com.mojang.brigadier.suggestion.SuggestionsBuilder bauer) {
        String angefangen = bauer.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (Tat recht : Tat.values()) {
            if (recht.schluessel().startsWith(angefangen)) {
                bauer.suggest(recht.schluessel());
            }
        }
        return bauer.buildFuture();
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
