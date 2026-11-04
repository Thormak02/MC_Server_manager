package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Spielzeitstand;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Zeitwerte;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Die Anzeige der gemessenen Spielzeit: {@code /freimarken spielzeit [<spieler>]}.
 *
 * <p>Ein Zweig von {@code /freimarken} und kein eigener Befehl - Plan 11.1 fuehrt sechzehn
 * Befehle, und {@code /spielzeit} steht nicht darunter. Sobald der Haupteintrag dort
 * nachgetragen ist, wird aus diesem Zweig ein eigener Befehl mit dem Zweitnamen
 * {@code /playtime}; der Inhalt bleibt derselbe.
 *
 * <p>Die Klasse entscheidet nichts. Sie liest ab, fragt die Ablage und das
 * {@link Spielzeitbuch} und laesst {@link Spielzeittext} die Saetze bauen.
 *
 * <p>Gezeigt werden nur ANWESENDE Spieler. Zwei der vier Zeilen - die offene Sitzung und
 * die Untaetigkeit - gibt es fuer einen abgemeldeten Spieler nicht, und ein Namensabgleich
 * ueber abgemeldete Spieler holt im Zweifel bei Mojang nach und haelt dabei den ganzen
 * Server an. Wer in alte Staende sehen will, nimmt den Manager (Plan 12a).
 */
final class SpielzeitBefehle {

    /** Wer seine eigene Spielzeit sehen darf - jeder. */
    static final String RECHT = "freimarken.spielzeit";

    /**
     * Wer die Spielzeit anderer sehen darf.
     *
     * <p>Ein zweites Recht und nicht {@code freimarken.verwalten}: Die Spielzeit ist eine
     * Messung und keine Verwaltung, und ein Moderator soll sie lesen koennen, ohne die
     * Einstellungen neu laden zu duerfen.
     */
    static final String RECHT_ANDERE = "freimarken.spielzeit.andere";

    /**
     * Die Absage an die Konsole - erste der drei Saetze dieses Befehls.
     *
     * <p><b>Alle Saetze, die dieser Befehl selbst sendet, stehen hier beieinander</b> und
     * nicht in den Verzweigungen, die sie senden. Die ZEILEN der Anzeige stehen in
     * {@link Spielzeittext}, weil in ihnen gerechnet wird; hier stehen die drei Absagen, in
     * denen nichts zu rechnen ist. Zusammen ist das alles, was ein Spieler von diesem Befehl
     * liest - der Umzug in die Sprachdatei aus Plan Abschnitt 13 ist damit an beiden Stellen
     * ein Verschieben und keine Suche.
     *
     * <p>Die Konsole bekommt keine Absage, die wie ein unbekannter Befehl klingt, sondern
     * den Weg: Sie hat keine eigene Spielzeit, kann aber die eines Spielers nennen.
     */
    static final String KEINE_KONSOLE = "Die Konsole spielt nicht. /freimarken spielzeit"
            + " <spieler> nennt die Zeit eines anwesenden Spielers.";

    /**
     * Die Absage, wenn die Ablage nicht antwortet.
     *
     * <p>Steht als Konstante da, weil {@link #zeilen} sie ZURUECKGIBT, statt sie einzeln zu
     * senden - und eine Probe sie darum gegen die ausgegebene Zeile halten kann. Eine
     * einzeln gesendete Absage prueft keine Probe: Sie faellt beim naechsten Umbau heraus,
     * der Spieler liest nach der Ueberschrift gar nichts mehr und haelt den Befehl fuer
     * kaputt, und die Suite bleibt gruen.
     */
    static final String NICHT_LESBAR = "Die Spielzeit ist gerade nicht lesbar - gezaehlt"
            + " wird trotzdem. Sag einem Operator Bescheid.";

    /**
     * Die Absage fuer einen Spieler, der nicht da ist - der dritte Satz dieses Befehls.
     *
     * <p>Eine Methode und keine Konstante, weil der Name davorsteht; der Satz selbst bleibt
     * damit bei den anderen zwei.
     */
    static String nichtDa(String name) {
        return name + " ist nicht da. Gezeigt werden kann nur die Spielzeit eines"
                + " anwesenden Spielers.";
    }

    private final Spielzeitbuch buch;
    private final Spielzeitablage ablage;
    private final Wirtschaftsstand wirtschaft;
    private final Clock uhr;
    private final Logger protokoll;

    /**
     * Die Saetze dieses Plugins - verdrahtet, bevor der erste Satz umzieht.
     *
     * <p>Warum alle elf Sender dieses Feld in EINEM Zug bekommen und nicht je
     * Bauschritt, steht dort, wo sie gebaut werden: {@code FreimarkenPlugin.onEnable}.
     */
    private final Sprache sprache;

    SpielzeitBefehle(Spielzeitbuch buch, Spielzeitablage ablage, Wirtschaftsstand wirtschaft,
                     Clock uhr, Logger protokoll, Sprache sprache) {
        this.sprache = sprache;
        this.buch = buch;
        this.ablage = ablage;
        this.wirtschaft = wirtschaft;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Der Zweig, wie {@code ParzellenBefehle.zusammenlegenZweig()} - noch nicht gebaut,
     * damit der Aufrufer ihn einhaengen kann.
     */
    LiteralArgumentBuilder<CommandSourceStack> spielzeit() {
        return Commands.literal("spielzeit")
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(zusammenhang -> {
                    zeigeEigene(zusammenhang.getSource());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("spieler", StringArgumentType.word())
                        .requires(quelle ->
                                quelle.getSender().hasPermission(RECHT_ANDERE))
                        // Vorgeschlagen werden die Anwesenden, weil nur sie gezeigt werden
                        // koennen - ein Vorschlag, der in einer Absage endet, ist Laerm.
                        .suggests((zusammenhang, bauer) -> {
                            Bukkit.getOnlinePlayers().forEach(p -> bauer.suggest(p.getName()));
                            return bauer.buildFuture();
                        })
                        .executes(zusammenhang -> {
                            zeigeFremde(zusammenhang.getSource(),
                                    StringArgumentType.getString(zusammenhang, "spieler"));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /** Die eigene Spielzeit. */
    private void zeigeEigene(CommandSourceStack quelle) {
        if (!(quelle.getExecutor() instanceof Player spieler)) {
            quelle.getSender().sendMessage(Component.text(KEINE_KONSOLE,
                    NamedTextColor.GRAY));
            return;
        }
        zeige(quelle.getSender(), spieler, Spielzeittext.kopfSelbst());
    }

    private void zeigeFremde(CommandSourceStack quelle, String name) {
        Player gesucht = Bukkit.getPlayerExact(name);
        if (gesucht == null) {
            quelle.getSender().sendMessage(Component.text(nichtDa(name),
                    NamedTextColor.RED));
            return;
        }
        zeige(quelle.getSender(), gesucht, Spielzeittext.kopfFuer(gesucht.getName()));
    }

    /**
     * Holt die Zahlen und traegt sie vor.
     *
     * <p>Genau EIN Sendeweg fuer alles unter der Ueberschrift: {@link #zeilen} entscheidet,
     * WAS dort steht - die vier Zeilen der Anzeige oder die Absage, wenn die Ablage nicht
     * antwortet. Darum gibt es hier keine einzeln gesendete Zeile mehr, die beim naechsten
     * Umbau herausfallen koennte, ohne dass eine Probe rot wird.
     *
     * <p>Was dieser Methode bleibt, ist das Spielerobjekt: UUID, Untaetigkeit und Tag. Das
     * SENDEN steht in {@link #sende} und nicht hier, damit es ohne Server pruefbar ist -
     * sonst waere der eine Weg, auf dem die Zeilen den Spieler ueberhaupt erreichen, von
     * keiner Probe beruehrt, und seine Schleife koennte ganz herausfallen, ohne dass etwas
     * rot wird.
     */
    private void zeige(Audience empfaenger, Player spieler, String kopf) {
        Zeitwerte zeit = wirtschaft.werte().zeit();
        LocalDate heute = Tagesgrenze.tagVon(uhr.instant(), zeit.zone());
        Duration untaetig = Spielzeitwache.untaetigkeitVon(spieler);
        sende(empfaenger, kopf, zeilen(spieler.getUniqueId(), untaetig, heute, zeit));
    }

    /**
     * Ueberschrift und darunter jede Zeile - der einzige Weg, auf dem dieser Befehl etwas
     * ausgibt.
     *
     * <p>Ohne Spielerobjekt und ohne Bukkit, damit eine Probe eine {@link Audience}
     * mitschreiben lassen kann: Geprueft ist damit nicht nur, WAS in den Zeilen steht,
     * sondern auch, DASS sie gesendet werden. Der Befehl soll in einem spaeteren Schritt
     * einen Zweitnamen bekommen ({@code /playtime}) - bei diesem Umbau ist die Schleife hier
     * das erste, was herausfallen kann, und dann liest der Spieler die goldene Ueberschrift
     * und darunter nichts.
     *
     * <p>Der Preis steht in der Farbe: Die Absage ist grau wie die Anzeige und nicht mehr
     * rot. Eine Farbe je Zeile waere eine Verzweigung ueber den Inhalt - dieselbe Abwaegung
     * wie beim Hinweis zum Einbau. Der Einzug von zwei Zeichen haengt ebenfalls hier und
     * nicht in den Saetzen: Er ist Darstellung und kein Text.
     */
    static void sende(Audience empfaenger, String kopf, List<String> zeilen) {
        empfaenger.sendMessage(Component.text(kopf, NamedTextColor.GOLD));
        for (String zeile : zeilen) {
            empfaenger.sendMessage(Component.text("  " + zeile, NamedTextColor.GRAY));
        }
    }

    /**
     * Was unter der Ueberschrift steht - ohne Spielerobjekt und ohne Senden, damit es ohne
     * Server pruefbar ist. Dasselbe Muster wie {@code ParzellenBefehle.mitZuschlag}.
     *
     * <p>Der TAG kommt vom Aufrufer und wird nicht in der Ablage gerechnet: {@code lies}
     * darf nicht wissen, was heute ist - sonst braeuchte es eine zweite Zone oder ein
     * {@code CURDATE()}, und die eingestellte Tagesgrenze waere stumm wirkungslos, weil die
     * Datenbanksitzung auf UTC steht.
     *
     * <p>Antwortet die Ablage nicht, ist die Antwort die Absage und nicht eine leere Liste:
     * Dieselbe Linie wie beim Boden, der sich nicht aendern liess - die Zahl fehlt, die
     * Messung laeuft weiter, und der Spieler soll nicht raten, warum nichts kommt. Im
     * Protokoll steht die UUID und nicht der Name, wie in {@link Spielzeitbuch}: Diese
     * Methode kennt bewusst kein Spielerobjekt.
     */
    List<String> zeilen(UUID wer, Duration untaetig, LocalDate heute, Zeitwerte zeit) {
        Spielzeitstand stand;
        try {
            stand = ablage.lies(wer, heute);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Spielzeit nicht lesbar fuer " + wer, fehler);
            return List.of(NICHT_LESBAR);
        }
        // JEDE Zeile kommt aus Spielzeittext, auch der Hinweis zum Einbau. Der Stapel der
        // noch nicht geschriebenen Zuschlaege muss mit: Sonst zeigte die Anzeige waehrend
        // der Sperre eine zu kleine Zahl, obwohl die Sekunden gemessen sind. Und die
        // Sperre selbst muss mit, sonst waere die MENGE richtig und der genannte ABSTAND
        // falsch - waehrend der Sperre wird nicht alle takt-sekunden geschrieben.
        return Spielzeittext.werte(stand, buch.offeneSitzung(wer, untaetig),
                buch.wartende(wer), buch.gesperrt(), untaetig, zeit);
    }
}
