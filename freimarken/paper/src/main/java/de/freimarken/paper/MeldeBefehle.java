package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Meldebuch;
import de.freimarken.kern.Meldung;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /melden <spieler> <grund>} - eine Meldung abgeben (Plan 12c.3).
 *
 * <p><b>Fuer JEDEN</b>, und das ist der Sinn der Einrichtung: Wer einen Vorfall sieht,
 * soll ihn melden koennen, ohne jemanden zu suchen.
 *
 * <p><b>Der GRUND ist ein {@code greedyString}</b> und kein einzelnes Wort: Ein Melder
 * tippt einen Satz, keinen Begriff. Damit endet der Befehlsbaum hier - hinter einem
 * greedy kann kein weiteres Argument stehen, und das ist richtig, denn der Grund ist das
 * Letzte.
 *
 * <p><b>Nur ein SPIELER kann melden</b>, und zwar nicht aus Prinzip: Eine Meldung hat
 * einen ORT, weil der Moderator hinspringt. Die Konsole steht nirgends. Die Absage ist
 * gelesen und nicht verborgen - ein Betreiber, der den Befehl nicht findet, sucht an der
 * falschen Stelle.
 *
 * <p><b>Der TATORT ist der Ort des MELDERS</b>, nicht der des Betroffenen. Was er gesehen
 * hat, hat er von dort gesehen; wer eine Stunde spaeter zum Betroffenen springt, findet
 * den Spieler, aber nicht die Tat.
 *
 * <p><b>Die ANSAGE an anwesende Moderatoren</b> geht ueber eine Naht und nicht ueber
 * {@code Bukkit.getOnlinePlayers()}: So laeuft der Zweig ohne Server durch eine Probe,
 * und die Probe kann zaehlen, wer sie bekommen haette.
 */
final class MeldeBefehle {

    /**
     * Wer melden darf - jeder. Angemeldet mit {@code default: true}.
     *
     * <p>Das LITERAL steht hier und nicht im Kern: {@code GrenzeTest} 31al liest die
     * {@code "freimarken.*"}-Literale dieses Moduls und haelt sie gegen die
     * {@code plugin.yml}. Ein Name, der nur im Kern steht, gilt ihr als
     * angemeldet-aber-nie-gefragt.
     */
    static final String RECHT = "freimarken.melden";

    /** Der Name des Befehls - er steht woertlich in den Saetzen der Sprachdatei. */
    static final String ARGUMENT = "melden";

    /**
     * Woher die Kennung zu einem getippten Namen kommt.
     *
     * <p>Eine Naht und kein statischer Aufruf: {@code Bukkit.getOfflinePlayer} wirft ohne
     * Server. Gemeldet wird auch, wer gerade abgemeldet ist - ein Griefer verschwindet,
     * bevor man ihn meldet.
     */
    interface Namenssuche {
        UUID kennungVon(String name);
    }

    /**
     * Wo der Melder steht - die Naht zum Ort.
     *
     * <p>{@code Player.getLocation()} braucht einen echten Spieler, und der Weltname
     * kommt aus {@code World.getName()}. Hinter dieser Naht laeuft der ganze Zweig durch
     * eine Probe.
     *
     * @param welt der Bukkit-Weltname, ohne Namensraum
     */
    record Ort(String welt, int x, int y, int z) {
    }

    interface Ortsgeber {
        Ort ortVon(Player spieler);
    }

    /**
     * Wer die Ansage bekommt - alle anwesenden Moderatoren.
     *
     * <p>Eine Naht, damit eine Probe zaehlen kann, wer sie bekommen haette. Im Spiel
     * steht dahinter eine Schleife ueber die angemeldeten Spieler mit dem Recht.
     */
    interface Ansagekreis {
        List<Audience> moderatoren();
    }

    /** Die Uhr - als Naht, damit eine Probe einen festen Zeitpunkt hereingeben kann. */
    interface Uhr {
        Instant jetzt();
    }

    private final Meldebuch buch;
    private final Sprache sprache;
    private final Namenssuche suche;
    private final Ortsgeber orte;
    private final Ansagekreis kreis;
    private final Meldetext.Namensgeber namen;
    private final Uhr uhr;
    private final Logger protokoll;

    MeldeBefehle(Meldebuch buch, Sprache sprache, Namenssuche suche, Ortsgeber orte,
                 Ansagekreis kreis, Meldetext.Namensgeber namen, Uhr uhr,
                 Logger protokoll) {
        if (buch == null || sprache == null || suche == null || orte == null
                || kreis == null || namen == null || uhr == null || protokoll == null) {
            throw new IllegalArgumentException("/melden braucht das Meldebuch, Sprache,"
                    + " Namenssuche, Ortsgeber, Ansagekreis, Namensgeber, eine Uhr und"
                    + " das Protokoll");
        }
        this.buch = buch;
        this.sprache = sprache;
        this.suche = suche;
        this.orte = orte;
        this.kreis = kreis;
        this.namen = namen;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /** Der Baum: {@code /melden} und {@code /melden <spieler> <grund>}. */
    LiteralCommandNode<CommandSourceStack> melden() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                // Ein eigenes executes auf dem Wort selbst UND auf dem Spielerargument:
                // Ohne sie endet /melden beziehungsweise /melden Anna in Brigadiers
                // englischem "Unknown or incomplete command" - eine rote Zeile fuer
                // jemanden, der gerade einen Vorfall gesehen hat.
                .executes(this::hilfe)
                .then(Commands.argument("spieler", StringArgumentType.word())
                        .executes(this::hilfe)
                        // greedyString: Der Grund ist ein Satz und kein Wort. Damit
                        // endet der Baum hier - hinter einem greedy kann nichts mehr
                        // stehen, und das ist richtig.
                        .then(Commands.argument("grund",
                                        StringArgumentType.greedyString())
                                .executes(this::melde)))
                .build();
    }

    /**
     * {@code /melden} ohne Angaben - der Hinweis, wie es geht.
     *
     * <p>Derselbe Satz wie bei einem zu kurzen Grund: Wer nichts getippt hat, hat auch
     * keinen brauchbaren Grund, und zwei Saetze fuer dieselbe Lage waeren zwei Orte fuer
     * dieselbe Erklaerung.
     */
    private int hilfe(CommandContext<CommandSourceStack> z) {
        sende(z.getSource().getSender(), sprache.bau(MeldeSatz.GRUND_ZU_KURZ,
                Meldung.KUERZESTER_GRUND,
                Einsatz.von("mindestens", Meldung.KUERZESTER_GRUND)));
        return Command.SINGLE_SUCCESS;
    }

    private int melde(CommandContext<CommandSourceStack> z) {
        if (!(z.getSource().getSender() instanceof Player wer)) {
            // Gelesen und nicht verborgen - siehe den Klassenkopf: Eine Meldung hat
            // einen Ort, und die Konsole steht nirgends.
            sende(z.getSource().getSender(),
                    sprache.bau(MeldeSatz.NUR_SPIELER, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        String getippt = StringArgumentType.getString(z, "spieler");
        String grund = StringArgumentType.getString(z, "grund");

        // ERST der Grund, dann der Name: Der Grund steht schon da, und wer ihn
        // nachbessern muss, soll das erfahren, bevor er den Namen nachschlaegt. Umgekehrt
        // als bei /team gib - dort ist der Name der Fehler, der eine Rolle an einen
        // Geisterspieler vergibt; hier gibt es keinen Geisterspieler, weil eine Meldung
        // nichts vergibt.
        if (!Meldebuch.brauchbarerGrund(grund)) {
            sende(wer, grundabsage(grund));
            return Command.SINGLE_SUCCESS;
        }
        UUID betroffener = suche.kennungVon(getippt);
        if (betroffener == null) {
            sende(wer, sprache.bau(MeldeSatz.UNBEKANNTER_SPIELER, 1L,
                    Einsatz.von("name", getippt)));
            return Command.SINGLE_SUCCESS;
        }
        Ort ort = orte.ortVon(wer);
        Meldebuch.Entscheid entscheid = buch.melde(wer.getUniqueId(), betroffener, grund,
                ort.welt(), ort.x(), ort.y(), ort.z(), uhr.jetzt());

        sende(wer, antwort(entscheid, getippt));
        if (entscheid.bescheid() == Meldebuch.Bescheid.ANGELEGT) {
            Meldung neue = entscheid.meldung().orElseThrow();
            protokoll.info(wer.getName() + " hat " + namen.nameVon(betroffener)
                    + " gemeldet (Meldung " + neue.nummer() + "): " + grund);
            sageAn(neue);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Welche der drei Grundabsagen gilt.
     *
     * <p>Drei Saetze fuer drei Faelle und nicht ein "Grund unbrauchbar": Der Spieler soll
     * wissen, was er aendern muss. Eine Absage, die das nicht sagt, fuehrt zum zweiten
     * Fehlversuch - und der dritte kommt nicht mehr.
     */
    private List<Component> grundabsage(String grund) {
        String rein = grund == null ? "" : grund.trim();
        if (rein.length() > Meldung.LAENGSTER_GRUND) {
            return sprache.bau(MeldeSatz.GRUND_ZU_LANG, Meldung.LAENGSTER_GRUND,
                    Einsatz.von("hoechstens", Meldung.LAENGSTER_GRUND));
        }
        if (rein.length() < Meldung.KUERZESTER_GRUND) {
            return sprache.bau(MeldeSatz.GRUND_ZU_KURZ, Meldung.KUERZESTER_GRUND,
                    Einsatz.von("mindestens", Meldung.KUERZESTER_GRUND));
        }
        // Bleibt nur das Zeichen. Der Satz nennt NICHT, welches - wer Farben faelschen
        // wollte, braucht keine Anleitung.
        return sprache.bau(MeldeSatz.GRUND_ZEICHEN, 1L, Einsatz.keine());
    }

    /**
     * Was der Melder liest - je Bescheid eine Antwort.
     *
     * <p>Ein Schalter ohne {@code default}: Wer dem {@link Meldebuch.Bescheid} einen
     * weiteren Wert hinzufuegt, wird vom Uebersetzer gefragt, was der Melder dann liest.
     * Die Bescheide der BEARBEITUNG koennen hier nicht ankommen - sie entstehen in
     * {@code /meldung} -, und ein Wurf sagt das, statt eine falsche Zeile zu senden.
     */
    private List<Component> antwort(Meldebuch.Entscheid entscheid, String getippt) {
        return switch (entscheid.bescheid()) {
            case ANGELEGT -> sprache.bau(MeldeSatz.ANGELEGT, 1L,
                    Einsatz.von("nummer", entscheid.meldung().orElseThrow().nummer())
                            .mit("spieler", getippt));
            case AUS -> sprache.bau(MeldeSatz.AUS, 1L, Einsatz.keine());
            case ABKLINGZEIT -> sprache.bau(MeldeSatz.ABKLINGZEIT,
                    entscheid.wartezeit(),
                    Einsatz.von("sekunden", entscheid.wartezeit()));
            case ZU_VIELE_OFFENE -> sprache.bau(MeldeSatz.ZU_VIELE_OFFENE,
                    entscheid.grenze(), Einsatz.von("grenze", entscheid.grenze()));
            case SELBST -> sprache.bau(MeldeSatz.SELBST, 1L, Einsatz.keine());
            case GRUND_UNBRAUCHBAR -> sprache.bau(MeldeSatz.GRUND_ZEICHEN, 1L,
                    Einsatz.keine());
            case GESTOERT -> sprache.bau(MeldeSatz.GESTOERT, 1L, Einsatz.keine());
            case GENOMMEN, ERLEDIGT, UNBEKANNT, SCHON_GENOMMEN, SCHON_ERLEDIGT,
                 OHNE_RECHT -> throw new IllegalStateException(
                    "Der Bescheid " + entscheid.bescheid() + " entsteht beim BEARBEITEN"
                            + " und kann von /melden nicht kommen. Wer ihn hier"
                            + " beantwortet, sendet dem Melder eine Zeile ueber einen"
                            + " Vorgang, den es nicht gab.");
        };
    }

    /**
     * Die Ansage an die anwesenden Moderatoren.
     *
     * <p><b>Leer heisst: keine Ansage.</b> Auf einem Server mit zehn Moderatoren und
     * vielen Meldungen ist eine Zeile je Meldung eine Belaestigung, und die Liste gibt es
     * ohnehin. Gefragt wird mit {@code leerGelassen} und nicht mit einem eigenen
     * Schalter in der {@code config.yml}: Der Wortlaut IST der Schalter - dieselbe
     * Entscheidung wie bei den Ansagen des Angelwettbewerbs.
     */
    private void sageAn(Meldung neue) {
        if (sprache.leerGelassen(MeldeSatz.ANSAGE)) {
            return;
        }
        List<Component> zeilen = sprache.bau(MeldeSatz.ANSAGE, 1L,
                Einsatz.von("nummer", neue.nummer())
                        .mit("melder", namen.nameVon(neue.melder()))
                        .mit("betroffener", namen.nameVon(neue.betroffener()))
                        .mit("grund", neue.grund()));
        for (Audience moderator : kreis.moderatoren()) {
            sende(moderator, zeilen);
        }
    }

    /** Die EINE Sendestelle dieser Klasse. */
    private void sende(Audience an, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            an.sendMessage(zeile);
        }
    }
}
