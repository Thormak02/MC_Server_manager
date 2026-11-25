package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Meldebuch;
import de.freimarken.kern.Meldestand;
import de.freimarken.kern.Meldung;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /meldung} - Meldungen ansehen, nehmen, erledigen und hinspringen (Plan 12c.3).
 *
 * <p><b>Sechs Zweige, und jeder hat sein eigenes {@code executes}</b>: {@code /meldung}
 * zeigt die Liste, {@code /meldung &lt;nummer&gt;} die Meldung, dazu {@code annehmen},
 * {@code abschliessen}, {@code abweisen} und {@code hin}. Ohne das eigene
 * {@code executes} endet jeder Zweig ohne sein Argument in Brigadiers englischem "Unknown
 * or incomplete command" - gemessen in diesem Projekt schon zweimal.
 *
 * <p><b>Der VERMERK ist ein {@code greedyString}</b>, wie der Grund bei {@code /melden}:
 * Ein Bearbeiter schreibt einen Satz. Er ist freiwillig - {@code abschliessen} hat ein
 * eigenes {@code executes} auf dem Literal, damit ein Moderator eine klare Sache ohne
 * Worte erledigen kann. Dann liest der Melder {@code OHNE_VERMERK_WORT}, und das ist
 * besser als ein Pflichtfeld, das zu "ok" fuehrt.
 *
 * <p><b>{@code hin} springt an den Ort des MELDERS</b>, nicht an den des Betroffenen. Was
 * er gesehen hat, hat er von dort gesehen. Gereist wird ueber dieselbe Naht wie bei
 * {@code /warp}, damit Wartezeit, Rueckweg und Hindernisse genauso gelten.
 *
 * <p><b>Die Rechte kommen als MENGE aus dem Absender</b> und gehen an das
 * {@link Meldebuch} - dieselbe Bauart wie bei {@link TeamBefehle}: Der Riegel liegt im
 * Kern und laeuft dort ohne Server durch eine Probe. Das {@code requires} hier ist der
 * erste, sichtbare Riegel; der im Kern ist der tragende.
 */
final class MeldungBefehle {

    /**
     * Wer Meldungen sehen und bearbeiten darf.
     *
     * <p>Das LITERAL steht hier und nicht im Kern - siehe {@link MeldeBefehle#RECHT}.
     * Es geht beim Bau in das {@code Meldebuch}, damit der Riegel dort bleibt, wo er
     * ohne Server pruefbar ist.
     */
    static final String RECHT = "freimarken.meldungen";

    /** Der Name des Befehls - er steht woertlich in den Saetzen der Sprachdatei. */
    static final String ARGUMENT = "meldung";

    static final String ANNEHMEN = "annehmen";

    static final String ABSCHLIESSEN = "abschliessen";

    static final String ABWEISEN = "abweisen";

    static final String HIN = "hin";

    /**
     * Wohin ein Moderator reist - die Naht zum Reisen.
     *
     * <p>Dieselbe Naht wie bei {@code /warp} und aus demselben Grund: Dahinter steht
     * {@code Reise.fuehre}, das Wartezeit, Rueckweg und Hindernisse kennt. Ein eigenes
     * {@code teleport} hier waere ein zweiter Reiseweg - und dann gilt die Wartezeit fuer
     * den einen und nicht fuer den anderen.
     *
     * @return {@code false}, wenn die Welt nicht da ist; dann sagt der Befehl das
     */
    interface Reisehand {
        boolean bringe(Player wer, String welt, int x, int y, int z, String ziel);
    }

    /** Die Uhr - als Naht, damit eine Probe einen festen Zeitpunkt hereingeben kann. */
    interface Uhr {
        Instant jetzt();
    }

    private final Meldebuch buch;
    private final Sprache sprache;
    private final Meldetext.Namensgeber namen;
    private final Reisehand reise;
    private final Uhr uhr;
    private final Logger protokoll;

    MeldungBefehle(Meldebuch buch, Sprache sprache, Meldetext.Namensgeber namen,
                   Reisehand reise, Uhr uhr, Logger protokoll) {
        if (buch == null || sprache == null || namen == null || reise == null
                || uhr == null || protokoll == null) {
            throw new IllegalArgumentException("/meldung braucht das Meldebuch, Sprache,"
                    + " Namensgeber, Reisehand, eine Uhr und das Protokoll");
        }
        this.buch = buch;
        this.sprache = sprache;
        this.namen = namen;
        this.reise = reise;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /** Der Baum. */
    LiteralCommandNode<CommandSourceStack> meldung() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(this::zeigeListe)
                .then(nummernargument()
                        .executes(this::zeigeEinzeln)
                        .then(Commands.literal(ANNEHMEN).executes(this::nimm))
                        .then(Commands.literal(ABSCHLIESSEN)
                                // Ein eigenes executes OHNE Vermerk: Eine klare Sache
                                // soll sich ohne Worte erledigen lassen. Ein Pflichtfeld
                                // fuehrt zu "ok" und nicht zu besseren Vermerken.
                                .executes(z -> erledige(z, Meldestand.ABGESCHLOSSEN, ""))
                                .then(Commands.argument("vermerk",
                                                StringArgumentType.greedyString())
                                        .executes(z -> erledige(z,
                                                Meldestand.ABGESCHLOSSEN,
                                                StringArgumentType.getString(z,
                                                        "vermerk")))))
                        .then(Commands.literal(ABWEISEN)
                                .executes(z -> erledige(z, Meldestand.ABGEWIESEN, ""))
                                .then(Commands.argument("vermerk",
                                                StringArgumentType.greedyString())
                                        .executes(z -> erledige(z,
                                                Meldestand.ABGEWIESEN,
                                                StringArgumentType.getString(z,
                                                        "vermerk")))))
                        .then(Commands.literal(HIN).executes(this::springHin)))
                .build();
    }

    /**
     * Das Nummernargument mit Vervollstaendigung der OFFENEN Nummern.
     *
     * <p>Vorgeschlagen werden die unerledigten: Das sind die, an denen etwas zu tun ist.
     * Eine Vervollstaendigung ueber alle Nummern waere nach einem Monat eine Liste von
     * Hunderten, in der die eine offene untergeht.
     *
     * <p><b>Der Zugriff auf die Ablage laeuft hier im Vervollstaendigungsfaden</b>, und
     * der ist nebenlaeufig zum Hauptfaden. Das ist vertretbar, weil es eine Abfrage auf
     * einen Index ist und nur laeuft, waehrend ein Moderator tippt - aber es ist der
     * Grund, aus dem ein Fehler hier GESCHLUCKT wird: Eine Ausnahme im
     * Vervollstaendigungsfaden erscheint im Protokoll als Ratsel ohne Zusammenhang, und
     * der Moderator kann die Nummer ohnehin tippen.
     */
    private com.mojang.brigadier.builder.RequiredArgumentBuilder
            <CommandSourceStack, Long> nummernargument() {
        return Commands.argument("nummer", LongArgumentType.longArg(1L))
                .suggests((z, bauer) -> {
                    try {
                        for (Meldung eine : buch.offene()) {
                            bauer.suggest(String.valueOf(eine.nummer()));
                        }
                    } catch (IllegalStateException gestoert) {
                        // Siehe den Methodenkopf: Der Moderator tippt die Nummer selbst.
                        protokoll.fine("Die offenen Meldungen sind fuer die"
                                + " Vervollstaendigung nicht lesbar: "
                                + gestoert.getMessage());
                    }
                    return bauer.buildFuture();
                });
    }

    /** {@code /meldung} - was anliegt. */
    private int zeigeListe(CommandContext<CommandSourceStack> z) {
        Audience wer = z.getSource().getSender();
        List<Meldung> offene;
        try {
            offene = buch.offene();
        } catch (IllegalStateException gestoert) {
            protokoll.warning("Die offenen Meldungen sind nicht lesbar: "
                    + gestoert.getMessage());
            sende(wer, sprache.bau(MeldeSatz.MODERATION_GESTOERT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        if (offene.isEmpty()) {
            sende(wer, sprache.bau(MeldeSatz.LISTE_LEER, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        Instant jetzt = uhr.jetzt();
        List<Component> zeilen = new ArrayList<>(sprache.bau(MeldeSatz.LISTE_KOPF,
                offene.size(), Einsatz.von("anzahl", offene.size())));
        for (Meldung eine : offene) {
            zeilen.add(Meldetext.listenzeile(sprache, eine, jetzt, namen));
        }
        zeilen.addAll(sprache.bau(MeldeSatz.LISTE_HINWEIS, 1L, Einsatz.keine()));
        sende(wer, zeilen);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /meldung <nummer>} - eine Meldung vollstaendig. */
    private int zeigeEinzeln(CommandContext<CommandSourceStack> z) {
        Audience wer = z.getSource().getSender();
        long nummer = LongArgumentType.getLong(z, "nummer");
        Optional<Meldung> gefunden;
        try {
            gefunden = buch.lies(nummer);
        } catch (IllegalStateException gestoert) {
            protokoll.warning("Die Meldung " + nummer + " ist nicht lesbar: "
                    + gestoert.getMessage());
            sende(wer, sprache.bau(MeldeSatz.MODERATION_GESTOERT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        if (gefunden.isEmpty()) {
            sende(wer, sprache.bau(MeldeSatz.UNBEKANNT, 1L,
                    Einsatz.von("nummer", nummer)));
            return Command.SINGLE_SUCCESS;
        }
        sende(wer, Meldetext.einzeln(sprache, gefunden.get(), uhr.jetzt(), namen));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /meldung <nummer> annehmen}. */
    private int nimm(CommandContext<CommandSourceStack> z) {
        Audience wer = z.getSource().getSender();
        long nummer = LongArgumentType.getLong(z, "nummer");
        UUID bearbeiter = kennungVon(z);
        if (bearbeiter == null) {
            // Die Konsole kann eine Meldung nicht NEHMEN: "in Arbeit bei der Konsole"
            // ist keine Auskunft, und niemand kann sie ansprechen. Erledigen darf sie -
            // dort steht ein Vermerk, und der traegt die Auskunft.
            sende(wer, sprache.bau(MeldeSatz.NUR_SPIELER_MODERATION, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        Meldebuch.Entscheid entscheid = buch.nimm(nummer, bearbeiter, uhr.jetzt(),
                rechteVon(z));
        if (entscheid.geaendert()) {
            protokoll.info(nameVon(z) + " hat die Meldung " + nummer + " angenommen.");
        }
        sende(wer, antwort(entscheid, nummer));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /meldung <nummer> abschliessen|abweisen [vermerk]} - EINE Methode fuer
     * beide.
     *
     * <p>Zusammen und nicht getrennt, weil alles davor und danach gleich ist: dieselbe
     * Rechtemenge, dasselbe Protokoll, dieselbe Antwort an den Melder. Verschieden ist
     * genau ein Wert, und der steht im Aufruf.
     */
    private int erledige(CommandContext<CommandSourceStack> z, Meldestand ziel,
                         String vermerk) {
        Audience wer = z.getSource().getSender();
        long nummer = LongArgumentType.getLong(z, "nummer");
        // Die Konsole DARF erledigen - anders als beim Nehmen. Sie bekommt dafuer die
        // Null-Kennung nicht: Meldung verlangt einen Bearbeiter, sobald der Stand nicht
        // OFFEN ist. Darum wird die Kennung des Absenders gebraucht, und fuer die
        // Konsole gibt es keine. Gebaut ist es so, dass die Konsole den Zweig gar nicht
        // erreicht - das requires auf dem Befehl laesst sie durch, der Riegel hier nicht.
        UUID bearbeiter = kennungVon(z);
        if (bearbeiter == null) {
            sende(wer, sprache.bau(MeldeSatz.NUR_SPIELER_MODERATION, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        Meldebuch.Entscheid entscheid = buch.erledige(nummer, ziel, bearbeiter, vermerk,
                uhr.jetzt(), rechteVon(z));
        if (entscheid.geaendert()) {
            protokoll.info(nameVon(z) + " hat die Meldung " + nummer + " "
                    + (ziel == Meldestand.ABGESCHLOSSEN ? "abgeschlossen" : "abgewiesen")
                    + (vermerk.isBlank() ? " (ohne Vermerk)." : ": " + vermerk));
        }
        sende(wer, antwort(entscheid, nummer));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /meldung <nummer> hin} - zum Tatort. */
    private int springHin(CommandContext<CommandSourceStack> z) {
        Audience wer = z.getSource().getSender();
        long nummer = LongArgumentType.getLong(z, "nummer");
        if (!(z.getSource().getSender() instanceof Player spieler)) {
            sende(wer, sprache.bau(MeldeSatz.NUR_SPIELER_MODERATION, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        Optional<Meldung> gefunden;
        try {
            gefunden = buch.lies(nummer);
        } catch (IllegalStateException gestoert) {
            protokoll.warning("Die Meldung " + nummer + " ist nicht lesbar: "
                    + gestoert.getMessage());
            sende(wer, sprache.bau(MeldeSatz.MODERATION_GESTOERT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        if (gefunden.isEmpty()) {
            sende(wer, sprache.bau(MeldeSatz.UNBEKANNT, 1L,
                    Einsatz.von("nummer", nummer)));
            return Command.SINGLE_SUCCESS;
        }
        Meldung eine = gefunden.get();
        if (!reise.bringe(spieler, eine.welt(), eine.x(), eine.y(), eine.z(),
                "Meldung " + nummer)) {
            // Die Welt gibt es nicht mehr. Das kommt vor: Eine Meldung aus Kornmark
            // ueberlebt den Reset der Welt, und eine aus einer abgeschalteten Welt
            // ueberlebt die Aenderung der config.yml.
            sende(wer, sprache.bau(MeldeSatz.HIN_WELT_FEHLT, 1L,
                    Einsatz.von("welt", eine.welt())));
            return Command.SINGLE_SUCCESS;
        }
        sende(wer, sprache.bau(MeldeSatz.HIN_UNTERWEGS, 1L,
                Einsatz.von("nummer", nummer).mit("welt", eine.welt())));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Was der Moderator liest - je Bescheid eine Antwort.
     *
     * <p>Ein Schalter ohne {@code default}: Wer dem {@link Meldebuch.Bescheid} einen
     * weiteren Wert hinzufuegt, wird gefragt, was hier zu lesen ist. Die Bescheide des
     * MELDENS koennen hier nicht ankommen, und ein Wurf sagt das.
     */
    private List<Component> antwort(Meldebuch.Entscheid entscheid, long nummer) {
        return switch (entscheid.bescheid()) {
            case GENOMMEN -> sprache.bau(MeldeSatz.GENOMMEN, 1L,
                    Einsatz.von("nummer", nummer));
            case ERLEDIGT -> erledigtzeile(entscheid, nummer);
            case SCHON_GENOMMEN -> sprache.bau(MeldeSatz.SCHON_GENOMMEN, 1L,
                    Einsatz.von("nummer", nummer)
                            .mit("bearbeiter", entscheid.meldung()
                                    .flatMap(Meldung::wirdBearbeitetVon)
                                    .map(namen::nameVon)
                                    .orElse("?")));
            case SCHON_ERLEDIGT -> sprache.bau(MeldeSatz.SCHON_ERLEDIGT, 1L,
                    Einsatz.von("nummer", nummer)
                            .mit("stand", entscheid.meldung()
                                    .map(m -> Meldetext.standwort(sprache, m.stand()))
                                    .orElse("?")));
            case UNBEKANNT -> sprache.bau(MeldeSatz.UNBEKANNT, 1L,
                    Einsatz.von("nummer", nummer));
            case GRUND_UNBRAUCHBAR -> sprache.bau(MeldeSatz.VERMERK_ZU_LANG,
                    Meldung.LAENGSTER_VERMERK,
                    Einsatz.von("hoechstens", Meldung.LAENGSTER_VERMERK));
            case GESTOERT -> sprache.bau(MeldeSatz.MODERATION_GESTOERT, 1L,
                    Einsatz.keine());
            // OHNE_RECHT kann hier nicht ankommen, weil requires den Zweig schon
            // verbirgt - aber der Kern gibt den Bescheid, und eine Antwort darauf ist
            // besser als ein stummer Zweig. Dasselbe Wort wie bei einer unbekannten
            // Nummer waere falsch: Der Betreiber muss wissen, dass es am Recht liegt.
            case OHNE_RECHT -> sprache.bau(MeldeSatz.MODERATION_GESTOERT, 1L,
                    Einsatz.keine());
            case ANGELEGT, AUS, ABKLINGZEIT, ZU_VIELE_OFFENE, SELBST ->
                    throw new IllegalStateException("Der Bescheid "
                            + entscheid.bescheid() + " entsteht beim MELDEN und kann von"
                            + " /meldung nicht kommen.");
        };
    }

    /** Abgeschlossen oder abgewiesen - mit dem Namen des Melders, der die Antwort liest. */
    private List<Component> erledigtzeile(Meldebuch.Entscheid entscheid, long nummer) {
        Meldung eine = entscheid.meldung().orElseThrow();
        Einsatz werte = Einsatz.von("nummer", nummer)
                .mit("melder", namen.nameVon(eine.melder()));
        return eine.stand() == Meldestand.ABGESCHLOSSEN
                ? sprache.bau(MeldeSatz.ABGESCHLOSSEN, 1L, werte)
                : sprache.bau(MeldeSatz.ABGEWIESEN, 1L, werte);
    }

    /**
     * Die Rechte des Absenders - oder {@code null} fuer die Konsole.
     *
     * <p>Woertlich dieselbe Naht wie in {@link TeamBefehle}: Gelesen wird, was Bukkit
     * WIRKLICH haelt, und nur die zugesprochenen. Ein auf {@code false} gesetztes Recht
     * zaehlte sonst als vorhanden.
     */
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

    /** Die Kennung des Absenders - {@code null} fuer die Konsole. */
    private static UUID kennungVon(CommandContext<CommandSourceStack> z) {
        return z.getSource().getSender() instanceof Player spieler
                ? spieler.getUniqueId() : null;
    }

    /** Der Name des Absenders fuer das Protokoll. */
    private static String nameVon(CommandContext<CommandSourceStack> z) {
        return z.getSource().getSender().getName();
    }

    /** Die EINE Sendestelle dieser Klasse. */
    private void sende(Audience an, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            an.sendMessage(zeile);
        }
    }
}
