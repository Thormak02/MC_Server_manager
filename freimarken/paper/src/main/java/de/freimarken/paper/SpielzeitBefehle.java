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
 *
 * <p><b>Kein Wort steht mehr hier.</b> Seit dem 04.11.2026 sind die drei Absagen dieses
 * Befehls reine Methoden, die eine Liste von {@link Component} aus {@link Sprache} holen;
 * ihr Wortlaut steht unter {@code spielzeit:} in {@code sprache-vorlage.yml} und ihr
 * Vertrag in {@link SpielzeitSatz}. Der Zweig schickt die Liste nur noch hinaus - derselbe
 * Griff wie bei {@code ParzellenBefehle} und {@code AnfragenBefehle}: So ist jeder Satz
 * ohne Server pruefbar, und eine Bildprobe haelt ihn zeichengenau fest.
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
     * Die Absage an die Konsole - erste der drei Absagen dieses Befehls.
     *
     * <p><b>Alle drei stehen hier beieinander</b> und nicht in den Verzweigungen, die sie
     * senden. Die ZEILEN der Anzeige stehen in {@link Spielzeittext}, weil in ihnen
     * gerechnet wird; hier stehen die Absagen, in denen nichts zu rechnen ist. Zusammen ist
     * das alles, was ein Spieler von diesem Befehl liest.
     *
     * <p>Die Konsole bekommt keine Absage, die wie ein unbekannter Befehl klingt, sondern
     * den Weg: Sie hat keine eigene Spielzeit, kann aber die eines Spielers nennen. Ohne
     * Einzug, weil sie nicht unter einer Ueberschrift steht, und grau, weil es kein Fehler
     * ist - beides steht jetzt im Satz und kann vom Betreiber geaendert werden.
     */
    static List<Component> keineKonsoleZeilen(Sprache sprache) {
        return sprache.bau(SpielzeitSatz.KEINE_KONSOLE, 1L, Einsatz.keine());
    }

    /**
     * Die Absage fuer einen Spieler, der nicht da ist - die zweite.
     *
     * <p><b>Die einzige ROTE Zeile dieses Befehls</b>, und ohne Einzug: Sie steht nicht
     * unter einer Ueberschrift, sondern allein, und der Spieler soll sie als Fehler lesen.
     * Nicht zu verwechseln mit {@link #nichtLesbarZeilen} - die ist grau wie die Anzeige,
     * weil sie an deren Stelle steht.
     */
    static List<Component> nichtDaZeilen(Sprache sprache, String name) {
        return sprache.bau(SpielzeitSatz.NICHT_DA, 1L, Einsatz.von("name", name));
    }

    /**
     * Die Absage, wenn die Ablage nicht antwortet - die dritte, MIT Einzug und grau.
     *
     * <p>{@link #zeilen} GIBT sie zurueck, statt sie einzeln zu senden - und eine Probe kann
     * sie darum gegen die ausgegebene Zeile halten. Eine einzeln gesendete Absage prueft
     * keine Probe: Sie faellt beim naechsten Umbau heraus, der Spieler liest nach der
     * Ueberschrift gar nichts mehr und haelt den Befehl fuer kaputt, und die Suite bleibt
     * gruen.
     *
     * <p>Sie sieht aus wie die vier Zeilen, weil sie an ihrer Stelle steht: Ein roter
     * Ausreisser mitten in einer grauen Liste sieht nach einem Absturz aus. Wer sie rot
     * will, aendert sie in der Sprachdatei - dafuer ist sie dort.
     */
    static List<Component> nichtLesbarZeilen(Sprache sprache) {
        return sprache.bau(SpielzeitSatz.NICHT_LESBAR, 1L, Einsatz.keine());
    }

    private final Spielzeitbuch buch;
    private final Spielzeitablage ablage;
    private final Wirtschaftsstand wirtschaft;
    private final Clock uhr;
    private final Logger protokoll;

    /**
     * Die Saetze dieses Plugins.
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
     *
     * <p>Der Argumentname {@code spieler} steht WOERTLICH in
     * {@code spielzeit.keine-konsole}: Wer ihn hier umbenennt, laesst den Satz in der
     * Sprachdatei still falsch stehen.
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
            keineKonsoleZeilen(sprache).forEach(quelle.getSender()::sendMessage);
            return;
        }
        zeige(quelle.getSender(), spieler, Spielzeittext.kopfSelbst(sprache));
    }

    private void zeigeFremde(CommandSourceStack quelle, String name) {
        Player gesucht = Bukkit.getPlayerExact(name);
        if (gesucht == null) {
            nichtDaZeilen(sprache, name).forEach(quelle.getSender()::sendMessage);
            return;
        }
        zeige(quelle.getSender(), gesucht,
                Spielzeittext.kopfFuer(sprache, gesucht.getName()));
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
    private void zeige(Audience empfaenger, Player spieler, Component kopf) {
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
     * <p><b>Berichtigt am 04.11.2026 - der alte Satz an dieser Stelle ist ueberholt.</b> Er
     * begruendete: "Eine Farbe je Zeile waere eine Verzweigung ueber den Inhalt ... Der
     * Einzug von zwei Zeichen haengt ebenfalls hier und nicht in den Saetzen: Er ist
     * Darstellung und kein Text." Es ist eine Entscheidung des NUTZERS, dass das nicht
     * gilt: In die Sprachdatei gehoeren Worte, Farben UND Aufbau - Einzug, Spaltenbreite
     * und Umbruch -, aehnlich zu einem MOTD-Editor. Diese Methode faerbt und zieht darum
     * nichts mehr ein; jede Zeile bringt beides selbst mit, und was hier bleibt, ist die
     * REIHENFOLGE.
     *
     * <p>Der Gewinn ist unmittelbar: Der Betreiber kann dem Hinweis zum Altbestand eine
     * eigene Farbe geben - in der Roadmap steht offen, ob er abgesetzt bleiben soll -, ohne
     * dass eine Zeile Programm geaendert wird. Eine Farbe JE ZEILE war vorher gar nicht
     * moeglich.
     */
    static void sende(Audience empfaenger, Component kopf, List<Component> zeilen) {
        empfaenger.sendMessage(kopf);
        zeilen.forEach(empfaenger::sendMessage);
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
    List<Component> zeilen(UUID wer, Duration untaetig, LocalDate heute, Zeitwerte zeit) {
        Spielzeitstand stand;
        try {
            stand = ablage.lies(wer, heute);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Spielzeit nicht lesbar fuer " + wer, fehler);
            return nichtLesbarZeilen(sprache);
        }
        // JEDE Zeile kommt aus Spielzeittext, auch der Hinweis zum Einbau. Der Stapel der
        // noch nicht geschriebenen Zuschlaege muss mit: Sonst zeigte die Anzeige waehrend
        // der Sperre eine zu kleine Zahl, obwohl die Sekunden gemessen sind. Und die
        // Sperre selbst muss mit, sonst waere die MENGE richtig und der genannte ABSTAND
        // falsch - waehrend der Sperre wird nicht alle takt-sekunden geschrieben.
        return Spielzeittext.werte(sprache, stand, buch.offeneSitzung(wer, untaetig),
                buch.wartende(wer), buch.gesperrt(), untaetig, zeit);
    }
}
