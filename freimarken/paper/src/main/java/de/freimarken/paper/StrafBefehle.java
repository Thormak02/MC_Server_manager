package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Strafart;
import de.freimarken.kern.Strafbuch;
import de.freimarken.kern.Strafdauer;
import de.freimarken.kern.Strafe;
import de.freimarken.kern.Strafrechte;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /strafe} und {@code /bannliste} (Plan 12c.3 und 12c.4, Schritt 16a Teil B).
 *
 * <p><b>Die Zweige je Art werden GERECHNET und nicht aufgezaehlt</b>
 * ({@link #zweigFuer}). Eine vierte {@link Strafart} bringt damit ihren Zweig, ihr
 * Recht, ihre Vervollstaendigung und ihre Dauerfrage von selbst mit. Drei von Hand
 * geschriebene Zweige waeren drei Stellen, an denen die vierte fehlt - und eine Art ohne
 * Zweig ist eine Strafe, die niemand verhaengen kann, waehrend sie im Programm dasteht.
 *
 * <p><b>Ein Befehl mit Zweigen und nicht drei Befehle.</b> Anders als bei {@code /melden}
 * und {@code /meldung} (Teil C) ist hier die Leserschaft DIESELBE: ein Moderator, im
 * Vorfall. Drei Wurzelbefehle waeren drei Namen, die er sich merken muss, und zwei davon
 * kollidieren mit Vanilla ({@code /ban}, {@code /kick}) - dann gewinnt je nach
 * Registrierungsreihenfolge der falsche, und der Moderator liest eine englische Zeile,
 * waehrend nichts geschieht.
 *
 * <p><b>Der Grund ist ein {@code greedyString} und steht ZULETZT</b>, wie bei
 * {@code /melden}. Er ist Pflicht - Plan 12c.3: <i>"Eine Strafe, die niemand erklaert,
 * erzeugt nur Wiederkehr."</i> Darum hat kein Zweig ein {@code executes} ohne ihn: Ein
 * freiwilliger Grund waere einer, der im Eifer wegfaellt.
 *
 * <p><b>Die Rechte kommen als MENGE aus dem Absender</b> und gehen an das
 * {@link Strafbuch} - dieselbe Bauart wie bei {@link TeamBefehle} und
 * {@link MeldungBefehle}: Der Riegel liegt im Kern und laeuft dort ohne Server durch eine
 * Probe. Das {@code requires} hier ist der erste, sichtbare Riegel; der im Kern ist der
 * tragende. <b>Und nur der Kern kennt den Riegel gegen die zu lange Dauer</b> - ein
 * {@code requires} kann ihn nicht halten, denn die Dauer steht erst im Argument.
 */
final class StrafBefehle {

    /**
     * Wie die Rechte heissen - die LITERALE stehen hier im Plugin.
     *
     * <p>Gemessen am 25.11.2026 in Teil C: {@code GrenzeTest} 31al liest die
     * {@code "freimarken.*"}-Literale des <i>Plugin</i>-Moduls und haelt sie gegen die
     * {@code plugin.yml}. Ein Literal im Kern sieht sie NICHT - und dann steht in der
     * plugin.yml ein angemeldetes Recht, das nach ihrer Rechnung kein Zweig fragt.
     *
     * <p>Sieben Namen und keine Zusammensetzung aus Wurzel und Kuerzel: Ein berechneter
     * Name steht nirgends als Literal, und die Tafel waere blind. Siehe
     * {@link Strafrechte}.
     */
    static final Strafrechte RECHTE = new Strafrechte(
            "freimarken.strafen.stumm",
            "freimarken.strafen.rauswurf",
            "freimarken.strafen.bann",
            "freimarken.strafen.dauerhaft",
            "freimarken.strafen.aufheben",
            "freimarken.strafen.team");

    /**
     * Wer die oeffentliche Bannliste lesen darf.
     *
     * <p>{@code default: true} in der {@code plugin.yml} - Plan 12c.4 macht die Liste
     * ausdruecklich oeffentlich. Ob sie es wirklich ist, entscheidet zusaetzlich
     * {@code moderation.strafen.bannliste-oeffentlich}: Das RECHT ist der sichtbare
     * Riegel, die Einstellung der inhaltliche. Zwei Riegel sind hier kein Widerspruch,
     * denn sie beantworten verschiedene Fragen - "darf dieser Leser" und "ist die Liste
     * oeffentlich".
     */
    static final String RECHT_BANNLISTE = "freimarken.bannliste";

    /** Der Name des Befehls - er steht woertlich in den Saetzen der Sprachdatei. */
    static final String ARGUMENT = "strafe";

    /** Der Name der Bannliste - desgleichen. */
    static final String ARGUMENT_BANNLISTE = "bannliste";

    static final String AUFHEBEN = "aufheben";

    static final String VERLAUF = "verlauf";

    /**
     * Woher die Kennung zu einem getippten Namen kommt.
     *
     * <p>Dieselbe Naht wie bei {@link MeldeBefehle#suche} und aus demselben Grund:
     * {@code Bukkit.getOfflinePlayer} wirft ohne Server. Gebannt wird auch, wer gerade
     * abgemeldet ist - ein Griefer verschwindet, bevor man ihn sperrt.
     */
    interface Namenssuche {
        UUID kennungVon(String name);
    }

    /**
     * Ob dieser Spieler selbst im Team ist.
     *
     * <p><b>Die Frage geht an die TEAMABLAGE und nicht an Bukkit</b>, und das ist die
     * tragende Festlegung dieser Naht: Der Betroffene kann offline sein, und offline hat
     * niemand Rechte. Mit einer Rechtemenge als Grundlage waere jeder Abwesende
     * ungeschuetzt - und das ist genau der Augenblick, in dem man einen Kollegen bannt.
     *
     * <p>Eine Stoerung der Ablage gibt {@code true} und nicht {@code false}: Wer nicht
     * weiss, ob der Betroffene im Team ist, soll die Strafe NICHT verhaengen. Von zwei
     * Fehlern der, der sich zuruecknehmen laesst.
     */
    interface Teamfrage {
        boolean imTeam(UUID wer);
    }

    /**
     * Wer eine Ansage bekommt - ALLE Anwesenden.
     *
     * <p>Anders als bei {@code /melden}, wo nur die Moderatoren die Ansage lesen: Eine
     * Strafe ist oeffentlich (Plan 12c.4 macht sogar die Bannliste oeffentlich). Wer
     * zusieht, wie jemand grieft, und nichts geschieht, meldet beim naechsten Mal nicht.
     */
    interface Ansagekreis {
        List<Audience> alle();
    }

    /**
     * Was mit einem anwesenden Spieler geschieht - hinauswerfen oder ansprechen.
     *
     * <p><b>Ohne {@link #wirfHinaus} wirkt ein Bann erst beim naechsten Beitritt</b>, und
     * das ist die Luecke, die ihn im Ernstfall wertlos macht: Ein Moderator sperrt einen
     * Griefer, liest "gesperrt bis morgen" - und der Griefer baut weiter ab. Darum
     * gehoert das Hinauswerfen in dieselbe Tat wie das Verhaengen.
     *
     * @return {@code false}, wenn der Spieler nicht angemeldet ist; dann ist nichts zu
     *         tun, und das ist kein Fehler
     */
    interface Spielerhand {
        boolean wirfHinaus(UUID wer, Component zeile);

        boolean sage(UUID wer, Component zeile);
    }

    /** Die Uhr - als Naht, damit eine Probe einen festen Zeitpunkt hereingeben kann. */
    interface Uhr {
        Instant jetzt();
    }

    private final Strafbuch buch;
    private final Sprache sprache;
    private final Namenssuche suche;
    private final Teamfrage team;
    private final Ansagekreis kreis;
    private final Spielerhand hand;
    private final Meldetext.Namensgeber namen;
    private final Uhr uhr;
    private final Supplier<ZoneId> zone;
    private final Logger protokoll;

    StrafBefehle(Strafbuch buch, Sprache sprache, Namenssuche suche, Teamfrage team,
                 Ansagekreis kreis, Spielerhand hand, Meldetext.Namensgeber namen,
                 Uhr uhr, Supplier<ZoneId> zone, Logger protokoll) {
        if (buch == null || sprache == null || suche == null || team == null
                || kreis == null || hand == null || namen == null || uhr == null
                || zone == null || protokoll == null) {
            throw new IllegalArgumentException("/strafe braucht das Strafbuch, Sprache,"
                    + " Namenssuche, Teamfrage, Ansagekreis, Spielerhand, Namensgeber,"
                    + " eine Uhr, die Zone und das Protokoll");
        }
        this.buch = buch;
        this.sprache = sprache;
        this.suche = suche;
        this.team = team;
        this.kreis = kreis;
        this.hand = hand;
        this.namen = namen;
        this.uhr = uhr;
        this.zone = zone;
        this.protokoll = protokoll;
    }

    /**
     * Der Baum von {@code /strafe}.
     *
     * <p>Das {@code requires} auf dem Wort selbst fragt, ob der Leser UEBERHAUPT strafen
     * darf - sonst sieht ein gewoehnlicher Spieler einen Befehl, dessen jeder Zweig ihm
     * verborgen ist, und das liest sich wie ein Fehler. Je Zweig steht danach das Recht
     * fuer seine Art.
     */
    LiteralCommandNode<CommandSourceStack> strafe() {
        LiteralArgumentBuilder<CommandSourceStack> baum = Commands.literal(ARGUMENT)
                .requires(quelle -> darfUeberhaupt(quelle.getSender()))
                // Ein eigenes executes auf dem Wort selbst: Ohne es endet /strafe in
                // Brigadiers englischem "Unknown or incomplete command".
                .executes(this::zeigeHinweis);
        // GERECHNET und nicht aufgezaehlt - siehe den Klassenkopf.
        for (Strafart art : Strafart.values()) {
            baum = baum.then(zweigFuer(art));
        }
        return baum
                .then(Commands.literal(AUFHEBEN)
                        .requires(quelle -> quelle.getSender()
                                .hasPermission(RECHTE.aufheben()))
                        .executes(this::zeigeHinweis)
                        .then(Commands.argument("nummer", LongArgumentType.longArg(1L))
                                .executes(this::zeigeHinweis)
                                .then(Commands.argument("grund",
                                                StringArgumentType.greedyString())
                                        .executes(this::hebeAuf))))
                .then(Commands.literal(VERLAUF)
                        .executes(this::zeigeHinweis)
                        .then(Commands.argument("spieler",
                                        StringArgumentType.word())
                                .suggests(this::spielervorschlaege)
                                .executes(this::zeigeVerlauf)))
                .build();
    }

    /**
     * Ein Zweig je Art - mit oder ohne Dauerargument.
     *
     * <p><b>Die Dauer steht VOR dem Grund</b>, weil der Grund ein {@code greedyString}
     * ist und alles Weitere verschlingt. Eine Art ohne Dauer hat das Argument nicht: Ein
     * leeres Dauerargument fuer einen Rauswurf waere eine Stelle, an der ein Moderator
     * {@code 10m} tippt und nichts davon wirkt.
     *
     * <p>Jede Stufe bekommt ein {@code executes}, das den HINWEIS zeigt - gemessen in
     * diesem Projekt: Ein Literal neben einem Wortargument ohne eigenes {@code executes}
     * endet in Brigadiers englischer Fehlermeldung.
     */
    private LiteralArgumentBuilder<CommandSourceStack> zweigFuer(Strafart art) {
        var spieler = Commands.argument("spieler", StringArgumentType.word())
                .suggests(this::spielervorschlaege)
                .executes(this::zeigeHinweis);
        if (art.brauchtDauer()) {
            spieler = spieler.then(Commands.argument("dauer",
                            StringArgumentType.word())
                    .suggests((z, bauer) -> {
                        for (String wort : Strafdauer.VORSCHLAEGE) {
                            bauer.suggest(wort);
                        }
                        return bauer.buildFuture();
                    })
                    .executes(this::zeigeHinweis)
                    .then(Commands.argument("grund", StringArgumentType.greedyString())
                            .executes(z -> verhaenge(z, art))));
        } else {
            spieler = spieler.then(Commands.argument("grund",
                            StringArgumentType.greedyString())
                    .executes(z -> verhaenge(z, art)));
        }
        return Commands.literal(art.kuerzel())
                .requires(quelle -> quelle.getSender().hasPermission(RECHTE.fuer(art)))
                .executes(this::zeigeHinweis)
                .then(spieler);
    }

    /** Der Baum von {@code /bannliste} - fuer jeden. */
    LiteralCommandNode<CommandSourceStack> bannliste() {
        return Commands.literal(ARGUMENT_BANNLISTE)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT_BANNLISTE))
                .executes(this::zeigeBannliste)
                .build();
    }

    /** {@code /strafe} - was es gibt. */
    private int zeigeHinweis(CommandContext<CommandSourceStack> z) {
        StringJoiner arten = new StringJoiner(", ");
        for (Strafart art : Strafart.values()) {
            arten.add(art.kuerzel());
        }
        sende(z.getSource().getSender(), sprache.bau(StrafSatz.HINWEIS, 1L,
                Einsatz.von("arten", arten.toString())));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /strafe <art> <spieler> [dauer] <grund>}. */
    private int verhaenge(CommandContext<CommandSourceStack> z, Strafart art) {
        Audience wer = z.getSource().getSender();
        String name = StringArgumentType.getString(z, "spieler");
        UUID betroffener = suche.kennungVon(name);
        if (betroffener == null) {
            sende(wer, sprache.bau(StrafSatz.UNBEKANNTER_SPIELER, 1L,
                    Einsatz.von("name", name)));
            return Command.SINGLE_SUCCESS;
        }
        Strafdauer dauer = null;
        if (art.brauchtDauer()) {
            String wort = StringArgumentType.getString(z, "dauer");
            Optional<Strafdauer> gelesen = Strafdauer.ausWort(wort);
            if (gelesen.isEmpty()) {
                // Mit BEISPIELEN: Eine Absage "unbrauchbare Dauer" sagt nicht, dass die
                // Einheit fehlt. Wer 10 getippt hat, braucht 10m.
                sende(wer, sprache.bau(StrafSatz.UNBEKANNTE_DAUER, 1L,
                        Einsatz.von("wort", wort)
                                .mit("beispiele",
                                        String.join(", ", Strafdauer.VORSCHLAEGE))));
                return Command.SINGLE_SUCCESS;
            }
            dauer = gelesen.get();
        }
        String grund = StringArgumentType.getString(z, "grund");
        // Die TEAMFRAGE stellt das BUCH und nicht dieser Zweig [behoben am
        // 25.11.2026]: Sie wurde ihm beim Bau mitgegeben, denn hebeAuf braucht sie
        // genauso - und dort ist sie als Parameter nicht zu stellen, weil der
        // Betroffene erst nach dem Lesen bekannt ist. Genau darum fehlte der Riegel
        // beim Aufheben ganz.
        Strafbuch.Entscheid entscheid = buch.verhaenge(betroffener, art, grund,
                dauer, kennungVon(z), rechteVon(z), uhr.jetzt());
        if (entscheid.geaendert()) {
            Strafe eine = entscheid.strafe().orElseThrow();
            protokoll.info(nameVon(z) + " hat " + name + " bestraft: " + art + " #"
                    + eine.nummer() + ", Grund: " + grund + ", bis: "
                    + eine.endet().map(Object::toString).orElse("dauerhaft"));
            wirke(eine, name);
            sageAn(eine, name);
        }
        sende(wer, antwort(entscheid, art, name, 0L, grund));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die Strafe am anwesenden Spieler wirksam machen.
     *
     * <p><b>Ohne diese Methode ist die Wirkung um einen Beitritt verschoben</b>, und beim
     * Rauswurf gaebe es sie gar nicht: Ein Kick, der erst beim naechsten Anmelden
     * geschieht, ist ein Bann von einer Sekunde. Darum gehoert das Hinauswerfen in
     * dieselbe Tat wie das Verhaengen.
     *
     * <p>Beim STUMM wird nur gesprochen. Die Sperre selbst liegt bei der
     * {@link Strafwache}, die jede Chatzeile fragt - hier den Chat zu sperren waere ein
     * zweiter Riegel, und zwei Riegel fuer denselben Fall machen sich gegenseitig
     * unpruefbar.
     *
     * <p>Ist der Spieler nicht angemeldet, geschieht nichts, und das ist kein Fehler: Die
     * Zeile liest er an der Tuer, und die Wache hat sie.
     */
    private void wirke(Strafe eine, String name) {
        List<Component> zeilen = Straftext.getroffen(sprache, eine, uhr.jetzt(),
                zone.get(), werwort(eine));
        // Hinausgeworfen wird bei allem, was die Tuer sperrt, UND beim Rauswurf
        // selbst. Ein Kick nimmt nur EIN Component, darum alsEine; der Chat bekommt
        // die Zeilen einzeln, denn dort ist jede eine eigene Nachricht.
        boolean erreicht = eine.art().sperrtBeitritt() || !eine.art().giltFort()
                ? hand.wirfHinaus(eine.betroffener(), Straftext.alsEine(zeilen))
                : sageAlle(eine.betroffener(), zeilen);
        if (!erreicht) {
            protokoll.fine(name + " war nicht angemeldet - die Zeile zur Strafe #"
                    + eine.nummer() + " liest er an der Tuer beziehungsweise beim"
                    + " naechsten Versuch.");
        }
    }

    /**
     * Jede Zeile an den Betroffenen - und ob er ueberhaupt erreicht wurde.
     *
     * <p>Die ERSTE Zeile entscheidet: Ist der Spieler nicht angemeldet, sind es alle
     * nicht, und die Antwort ist {@code false}. Ohne diese Unterscheidung stuende im
     * Protokoll, er sei erreicht worden, weil die Schleife nicht geworfen hat.
     */
    private boolean sageAlle(UUID wer, List<Component> zeilen) {
        boolean erreicht = false;
        for (Component zeile : zeilen) {
            erreicht = hand.sage(wer, zeile);
            if (!erreicht) {
                return false;
            }
        }
        return erreicht;
    }

    /**
     * Die oeffentliche Ansage - nur, wenn sie eingestellt UND nicht leer ist.
     *
     * <p><b>ZWEI Wege, sie abzuschalten, und beide muessen hier gefragt werden</b>
     * [behoben am 25.11.2026]: der Schalter {@code moderation.strafen.ansagen} und der
     * geleerte Wortlaut. Die {@code config.yml} und die Sprachvorlage nennen das Leeren
     * ausdruecklich als zweiten Weg - gefragt wurde es nicht, und von da an bekam bei
     * JEDER Strafe jeder Anwesende eine LEERE Chatzeile statt keiner. {@code eineZeile}
     * wirft dabei nicht: Ein leerer Wortlaut ergibt genau ein leeres Component.
     *
     * <p>{@code MeldeBefehle.sageAn} fragt es seit Teil C richtig - dieselbe Zeile, und
     * sie fehlte hier.
     */
    private void sageAn(Strafe eine, String name) {
        if (!buch.werte().ansagen()) {
            return;
        }
        StrafSatz welcher = eine.art().brauchtDauer()
                ? StrafSatz.ANSAGE : StrafSatz.ANSAGE_OHNE_DAUER;
        if (sprache.leerGelassen(welcher)) {
            return;
        }
        Einsatz werte = Einsatz.von("spieler", name)
                .mit("art", Straftext.artwort(sprache, eine.art()))
                .mit("grund", eine.grund());
        // Dieselbe Verzweigung wie in antwort() und aus demselben Grund: Ein Rauswurf
        // hat keine Dauer, und "fuer 0 Sek." ist keine.
        Component zeile = eine.art().brauchtDauer()
                ? sprache.eineZeile(StrafSatz.ANSAGE, werte
                        .mit("dauer", Straftext.restwort(sprache, eine, uhr.jetzt())))
                : sprache.eineZeile(StrafSatz.ANSAGE_OHNE_DAUER, werte);
        for (Audience hoerer : kreis.alle()) {
            hoerer.sendMessage(zeile);
        }
    }

    /** {@code /strafe aufheben <nummer> <grund>}. */
    private int hebeAuf(CommandContext<CommandSourceStack> z) {
        Audience wer = z.getSource().getSender();
        long nummer = LongArgumentType.getLong(z, "nummer");
        String grund = StringArgumentType.getString(z, "grund");
        Strafbuch.Entscheid entscheid = buch.hebeAuf(nummer, kennungVon(z), grund,
                uhr.jetzt(), rechteVon(z));
        if (entscheid.geaendert()) {
            Strafe eine = entscheid.strafe().orElseThrow();
            String name = namen.nameVon(eine.betroffener());
            protokoll.info(nameVon(z) + " hat die Strafe #" + nummer + " gegen " + name
                    + " aufgehoben. Grund: " + grund);
            if (buch.werte().ansagen()
                    && !sprache.leerGelassen(StrafSatz.ANSAGE_AUFHEBUNG)) {
                Component zeile = sprache.eineZeile(StrafSatz.ANSAGE_AUFHEBUNG,
                        Einsatz.von("spieler", name)
                                .mit("art", Straftext.artwort(sprache, eine.art()))
                                .mit("grund", grund));
                for (Audience hoerer : kreis.alle()) {
                    hoerer.sendMessage(zeile);
                }
            }
        }
        sende(wer, antwort(entscheid, null, "", nummer, grund));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /strafe verlauf <spieler>}. */
    private int zeigeVerlauf(CommandContext<CommandSourceStack> z) {
        Audience wer = z.getSource().getSender();
        String name = StringArgumentType.getString(z, "spieler");
        UUID betroffener = suche.kennungVon(name);
        if (betroffener == null) {
            sende(wer, sprache.bau(StrafSatz.UNBEKANNTER_SPIELER, 1L,
                    Einsatz.von("name", name)));
            return Command.SINGLE_SUCCESS;
        }
        List<Strafe> verlauf;
        try {
            verlauf = buch.vorgeschichte(betroffener);
        } catch (IllegalStateException gestoert) {
            protokoll.warning("Die Vorgeschichte von " + name + " ist nicht lesbar: "
                    + gestoert.getMessage());
            sende(wer, sprache.bau(StrafSatz.GESTOERT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        sende(wer, Straftext.verlauf(sprache, verlauf, name, uhr.jetzt(), zone.get()));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /bannliste} - die oeffentliche Liste.
     *
     * <p>Ist sie nicht oeffentlich eingestellt, liest sie nur, wer selbst bannen darf.
     * Die Absage sagt das - eine leere Liste waere die falsche Antwort: Sie behauptet,
     * niemand sei gesperrt.
     */
    private int zeigeBannliste(CommandContext<CommandSourceStack> z) {
        Audience wer = z.getSource().getSender();
        if (!buch.werte().bannlisteOeffentlich()
                && !z.getSource().getSender().hasPermission(RECHTE.bann())) {
            sende(wer, sprache.bau(StrafSatz.BANNLISTE_OHNE_RECHT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        List<Strafe> banne;
        Instant jetzt = uhr.jetzt();
        try {
            banne = buch.bannliste(jetzt);
        } catch (IllegalStateException gestoert) {
            protokoll.warning("Die Bannliste ist nicht lesbar: "
                    + gestoert.getMessage());
            sende(wer, sprache.bau(StrafSatz.BANNLISTE_GESTOERT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        sende(wer, Straftext.bannliste(sprache, banne, jetzt, zone.get(), namen));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die Antwort an den Absender - je Bescheid eine andere.
     *
     * <p>Ein Schalter ohne {@code default}: Ein neuer Bescheid im Kern wird hier zum
     * Uebersetzungsfehler und nicht zu einer stummen Zeile. Dass einige Bescheide
     * wegen des {@code requires} nicht ankommen koennen, aendert nichts - der Kern gibt
     * sie, und eine Antwort darauf ist besser als ein stummer Zweig.
     *
     * @param art    bei einer verhaengten Strafe die Art, sonst {@code null}
     * @param name   der Name des Betroffenen, oder leer
     * @param nummer bei einer Aufhebung die Nummer, sonst 0
     * @param grund  was getippt wurde - fuer {@link #grundabsage}. Als Parameter und
     *               NICHT als Feld: Ein Feld, das nur ein Zweig setzt, ist beim
     *               anderen null, und dann liest der Moderator die falsche der drei
     *               Absagen
     */
    private List<Component> antwort(Strafbuch.Entscheid entscheid, Strafart art,
                                    String name, long nummer, String grund) {
        return switch (entscheid.bescheid()) {
            case VERHAENGT -> {
                Strafe eine = entscheid.strafe().orElseThrow();
                Einsatz werte = Einsatz.von("nummer", eine.nummer())
                        .mit("spieler", name)
                        .mit("art", Straftext.artwort(sprache, eine.art()))
                        .mit("grund", eine.grund());
                // JE NACH ART ein anderer Satz: Fuer einen Rauswurf stand hier die
                // Restzeit, und die ist null - "Rauswurf fuer 0 Sek." (gemessen im
                // Startlauf am 25.11.2026). Eine Dauer von null ist keine Angabe.
                yield eine.art().brauchtDauer()
                        ? sprache.bau(StrafSatz.VERHAENGT, 1L, werte
                                .mit("dauer", Straftext.restwort(sprache, eine,
                                        uhr.jetzt())))
                        : sprache.bau(StrafSatz.VERHAENGT_OHNE_DAUER, 1L, werte);
            }
            case AUFGEHOBEN -> {
                Strafe eine = entscheid.strafe().orElseThrow();
                yield sprache.bau(StrafSatz.AUFGEHOBEN, 1L,
                        Einsatz.von("nummer", eine.nummer())
                                .mit("spieler", namen.nameVon(eine.betroffener()))
                                .mit("art", Straftext.artwort(sprache, eine.art())));
            }
            // ART == NULL heisst: der AUFHEBUNGS-Zweig. Dort fehlt nicht das Recht
            // fuer eine Art, sondern das zum Zuruecknehmen - und eine Art zu nennen,
            // die es hier nicht gibt, waere eine Absage ueber die falsche Sache.
            case OHNE_RECHT -> art == null
                    ? sprache.bau(StrafSatz.OHNE_RECHT_AUFHEBEN, 1L, Einsatz.keine())
                    : sprache.bau(StrafSatz.OHNE_RECHT, 1L,
                            Einsatz.von("art", Straftext.artwort(sprache, art)));
            case OHNE_RECHT_DAUERHAFT -> art == null
                    ? sprache.bau(StrafSatz.OHNE_RECHT_DAUERHAFT_AUFHEBEN, 1L,
                            Einsatz.von("nummer", nummer))
                    : sprache.bau(StrafSatz.OHNE_RECHT_DAUERHAFT, 1L,
                            Einsatz.von("art", Straftext.artwort(sprache, art))
                                    .mit("hoechstens", Straftext.dauerwort(sprache,
                                            grenzeAls(entscheid))));
            case OHNE_RECHT_TEAM -> art == null
                    ? sprache.bau(StrafSatz.OHNE_RECHT_TEAM_AUFHEBEN, 1L,
                            Einsatz.von("nummer", nummer))
                    : sprache.bau(StrafSatz.OHNE_RECHT_TEAM, 1L,
                            Einsatz.von("spieler", name));
            // ART == NULL heisst wieder: der AUFHEBUNGS-Zweig. "Dich selbst kannst
            // du nicht bestrafen" waere dort falsch - er wollte aufheben.
            case SELBST -> art == null
                    ? sprache.bau(StrafSatz.SELBST_AUFHEBEN, 1L, Einsatz.keine())
                    : sprache.bau(StrafSatz.SELBST, 1L, Einsatz.keine());
            case GRUND_UNBRAUCHBAR -> grundabsage(grund);
            case SCHON_GESTRAFT -> {
                Strafe eine = entscheid.strafe().orElseThrow();
                yield sprache.bau(StrafSatz.SCHON_GESTRAFT, 1L,
                        Einsatz.von("spieler", name)
                                .mit("art", Straftext.artwort(sprache, eine.art()))
                                .mit("nummer", eine.nummer())
                                .mit("bis", Straftext.bisWort(sprache, eine,
                                        zone.get())));
            }
            case SCHON_AUFGEHOBEN -> sprache.bau(StrafSatz.SCHON_AUFGEHOBEN, 1L,
                    Einsatz.von("nummer", nummer)
                            .mit("wer", entscheid.strafe()
                                    .map(s -> Straftext.werwort(sprache,
                                            s.aufhebungDurch(), namen))
                                    .orElse("?")));
            case NICHT_FORTWIRKEND -> sprache.bau(StrafSatz.NICHT_FORTWIRKEND, 1L,
                    Einsatz.von("nummer", nummer)
                            .mit("art", entscheid.strafe()
                                    .map(s -> Straftext.artwort(sprache, s.art()))
                                    .orElse("?")));
            case UNBEKANNT -> sprache.bau(StrafSatz.UNBEKANNT, 1L,
                    Einsatz.von("nummer", nummer));
            case GESTOERT -> sprache.bau(StrafSatz.GESTOERT, 1L, Einsatz.keine());
        };
    }

    /**
     * Die Grenze aus dem Entscheid als Dauer.
     *
     * <p>0 Stunden heisst: jede Dauer braucht das Recht. Dann steht {@code dauerhaft}
     * da, und das ist genau richtig - der Moderator darf GAR NICHTS dieser Art, und ein
     * "hoechstens 0 Sek." waere eine Zahl, aus der niemand das schliesst.
     */
    private static Strafdauer grenzeAls(Strafbuch.Entscheid entscheid) {
        long stunden = entscheid.grenzeStunden();
        return stunden <= 0L ? Strafdauer.UNBEGRENZT
                : new Strafdauer(java.time.Duration.ofHours(stunden));
    }

    /**
     * Welche der drei Grundabsagen gilt.
     *
     * <p>Drei Saetze fuer drei Faelle und nicht ein "Grund unbrauchbar": Der Moderator
     * soll wissen, was er aendern muss. Gerechnet wird aus dem, was der Kern geprueft
     * hat - dieselben Grenzen, also nur eine Wahrheit.
     */
    private List<Component> grundabsage(String grund) {
        // Welcher der drei Faelle vorliegt, steht nicht im Bescheid - er sagt nur, dass
        // der Grund unbrauchbar ist. Der getippte Text liegt im Zweig noch vor, also
        // wird er dort hereingegeben; ein vierter Bescheid je Fall waere ein Kern, der
        // die Wortwahl des Plugins kennt.
        if (grund == null) {
            return sprache.bau(StrafSatz.GRUND_ZEICHEN, 1L, Einsatz.keine());
        }
        String rein = grund.trim();
        if (rein.length() < Strafe.KUERZESTER_GRUND) {
            return sprache.bau(StrafSatz.GRUND_ZU_KURZ, Strafe.KUERZESTER_GRUND,
                    Einsatz.von("mindestens", Strafe.KUERZESTER_GRUND));
        }
        if (rein.length() > Strafe.LAENGSTER_GRUND) {
            return sprache.bau(StrafSatz.GRUND_ZU_LANG, Strafe.LAENGSTER_GRUND,
                    Einsatz.von("hoechstens", Strafe.LAENGSTER_GRUND));
        }
        return sprache.bau(StrafSatz.GRUND_ZEICHEN, 1L, Einsatz.keine());
    }

    /** Die Rechte des Absenders - oder {@code null} fuer die Konsole. */
    private static Set<String> rechteVon(CommandContext<CommandSourceStack> z) {
        if (!(z.getSource().getSender() instanceof Player spieler)) {
            return null;
        }
        Set<String> rechte = new LinkedHashSet<>();
        spieler.getEffectivePermissions().forEach(angabe -> {
            if (angabe.getValue()) {
                rechte.add(angabe.getPermission());
            }
        });
        return rechte;
    }

    /** Ob dieser Absender ueberhaupt strafen darf - fuer das {@code requires}. */
    private static boolean darfUeberhaupt(
            org.bukkit.command.CommandSender absender) {
        for (Strafart art : Strafart.values()) {
            if (absender.hasPermission(RECHTE.fuer(art))) {
                return true;
            }
        }
        return absender.hasPermission(RECHTE.aufheben());
    }

    /** Die Kennung des Absenders - {@code null} fuer die Konsole. */
    private static UUID kennungVon(CommandContext<CommandSourceStack> z) {
        return z.getSource().getSender() instanceof Player spieler
                ? spieler.getUniqueId() : null;
    }

    /** Der Name des Absenders fuer das Protokoll. */
    private static String nameVon(CommandContext<CommandSourceStack> z) {
        return z.getSource().getSender().getName();
    }

    /** Wer eine Strafe verhaengt hat, als Wort. */
    private String werwort(Strafe eine) {
        return Straftext.werwort(sprache, eine.verhaengtVon(), namen);
    }

    /**
     * Die Namen der angemeldeten Spieler als Vervollstaendigung.
     *
     * <p>Nur die angemeldeten, obwohl sich auch ein Abwesender bestrafen laesst: Eine
     * Liste aller je gesehenen Namen waere nach einem Jahr unbrauchbar. Wer einen
     * Abwesenden sperren will, tippt den Namen - deshalb ist es ein {@code word} und
     * kein Spielerargument.
     */
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion
            .Suggestions> spielervorschlaege(CommandContext<CommandSourceStack> z,
            com.mojang.brigadier.suggestion.SuggestionsBuilder bauer) {
        for (Audience hoerer : kreis.alle()) {
            if (hoerer instanceof Player spieler) {
                bauer.suggest(spieler.getName());
            }
        }
        return bauer.buildFuture();
    }

    /** Die EINE Sendestelle dieser Klasse. */
    private void sende(Audience an, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            an.sendMessage(zeile);
        }
    }
}
