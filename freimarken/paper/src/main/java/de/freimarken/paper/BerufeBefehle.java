package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Berufsablage;
import de.freimarken.kern.Berufsregel;
import de.freimarken.kern.Berufsstand;
import de.freimarken.kern.Berufsverzeichnis;
import de.freimarken.kern.Berufswerte;
import de.freimarken.kern.Buchungsgrund;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Wirtschaftsstand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Der Befehl {@code /beruf} - waehlen, aufgeben, ansehen.
 *
 * <p><b>Kein Wort steht hier.</b> Jede Zeile kommt aus {@link Beruftext}, ihr Wortlaut aus
 * {@code sprache-vorlage.yml} und ihr Vertrag aus {@link BerufSatz}. Diese Datei
 * entscheidet, WAS geschieht, und schickt die Zeilen nur hinaus - derselbe Griff wie bei
 * {@code SpielzeitBefehle} und {@code ParzellenBefehle}.
 *
 * <p><b>Jede Entscheidung faellt in {@link Berufsregel}</b>, also im Kern und ohne Bukkit.
 * Hier steht nur, was danach zu tun ist: buchen, schreiben, senden. Dadurch ist die
 * ganze Regel ohne Server pruefbar, und diese Datei hat keine einzige Bedingung ueber
 * Plaetze, Rollen oder Fristen.
 *
 * <p><b>Die kostenpflichtige Tat ist gesichert</b>, wie {@code /parzelle aufgeben}: Ein
 * {@code /beruf verlassen bergmann} zeigt erst die Rueckfrage, und nur
 * {@code /beruf verlassen bergmann bestaetigen} bucht. Kostet ein Wechsel nichts, faellt
 * die Rueckfrage weg - eine Sicherung vor einer Tat ohne Preis waere nur ein zweiter
 * Tastendruck. Die SPERRFRIST ist dabei kein Grund fuer eine Rueckfrage: Sie steht in der
 * Rueckfrage mit drin, aber ohne Gebuehr gibt es nichts zu bestaetigen - wer aufgibt, will
 * aufgeben.
 *
 * <p><b>Die Reihenfolge beim Aufgeben: erst buchen, dann schreiben, bei Fehlschlag
 * erstatten.</b> Dieselbe Reihenfolge wie beim Parzellenkauf und aus demselben Grund: Wer
 * zuerst schreibt und dann bucht, hat bei einer zerrissenen Verbindung einen Spieler ohne
 * Beruf UND ohne Abbuchung - er haette gratis gewechselt. Umgekehrt steht im schlimmsten
 * Fall eine Abbuchung mit Erstattung im Buch, und beide Zeilen sind lesbar.
 */
final class BerufeBefehle {

    /** Wer einen Beruf haben darf - jeder. */
    static final String RECHT = "freimarken.beruf";

    /**
     * Wer eine SPEZIALISIERUNG halten darf.
     *
     * <p>Ein Recht und keine Rangzahl, und das ist eine Entscheidung mit Grund: Raenge sind
     * auf diesem Server Rechte. Eine Rangzahl hier waere eine zweite Quelle fuer dieselbe
     * Frage, und zwei Quellen antworten eines Tages verschieden. Wer den Rang Buerger
     * vergibt, vergibt dieses Recht mit - und wer es anders staffeln will, kann es.
     */
    static final String RECHT_SPEZIALISIERUNG = "freimarken.beruf.spezialisierung";

    /** Das Wort, das die bezahlte Tat freigibt - wie bei {@code /parzelle aufgeben}. */
    private static final String BESTAETIGEN = "bestaetigen";

    /** Der Name des Argumentes - er steht WOERTLICH in den Saetzen der Sprachdatei. */
    private static final String ARGUMENT = "beruf";

    /**
     * Die Konsole hat keinen Beruf - erste der drei Absagen, die nicht aus dem Urteil
     * kommen.
     *
     * <p>Alle drei stehen hier beieinander und nicht in den Verzweigungen, die sie senden -
     * dieselbe Ordnung wie in {@code SpielzeitBefehle}. Die Absagen AUS DEM URTEIL stehen
     * dagegen in {@link Beruftext#absageZeilen}, wo auch die Zuordnung dazu steht.
     */
    static List<Component> keineKonsoleZeilen(Sprache sprache) {
        return sprache.bau(BerufSatz.ABSAGE_KEINE_KONSOLE, 1L, Einsatz.keine());
    }

    /** Zwei Befehle in derselben Sekunde - es ist nichts passiert. Die zweite. */
    static List<Component> gleichzeitigZeilen(Sprache sprache) {
        return sprache.bau(BerufSatz.ABSAGE_GLEICHZEITIG, 1L, Einsatz.keine());
    }

    /** Die Ablage antwortet nicht - die dritte. */
    static List<Component> nichtLesbarZeilen(Sprache sprache) {
        return sprache.bau(BerufSatz.ABSAGE_NICHT_LESBAR, 1L, Einsatz.keine());
    }

    private final Berufsablage ablage;
    private final Berufsverzeichnis verzeichnis;
    private final Wirtschaftsstand wirtschaft;
    private final Kasse kasse;
    private final Clock uhr;
    private final Logger protokoll;
    private final Sprache sprache;

    BerufeBefehle(Berufsablage ablage, Berufsverzeichnis verzeichnis,
                  Wirtschaftsstand wirtschaft, Kasse kasse, Clock uhr, Logger protokoll,
                  Sprache sprache) {
        this.sprache = sprache;
        this.ablage = ablage;
        this.verzeichnis = verzeichnis;
        this.wirtschaft = wirtschaft;
        this.kasse = kasse;
        this.uhr = uhr;
        this.protokoll = protokoll;
    }

    /**
     * Der ganze Baum von {@code /beruf} - fertig gebaut, weil er unmittelbar angemeldet
     * wird.
     *
     * <p>Das nackte {@code /beruf} zeigt den Stand und nicht die Liste: Wer den Befehl
     * ohne Zusatz tippt, will meistens wissen, was er selbst ist.
     */
    LiteralCommandNode<CommandSourceStack> beruf() {
        return Commands.literal(ARGUMENT)
                // Ueber getSender() und nicht ueber die Quelle selbst: Nur so antwortet
                // die Pruefung auch fuer einen Stellvertreter in einer Probe.
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(zusammenhang -> rufe(zusammenhang, this::zeigeStand))
                .then(Commands.literal("stand")
                        .executes(zusammenhang -> rufe(zusammenhang, this::zeigeStand)))
                // Die LISTE geht auch an die Konsole: Sie haengt an keinem Spieler,
                // sondern an der Einstellungsdatei. Darum laeuft dieser Zweig nicht durch
                // rufe().
                .then(Commands.literal("liste")
                        .executes(zusammenhang -> {
                            sende(zusammenhang.getSource().getSender(),
                                    Beruftext.listeZeilen(sprache, verzeichnis));
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("waehlen")
                        .then(berufsargument()
                                .executes(zusammenhang -> rufe(zusammenhang,
                                        auftrag -> waehle(auftrag,
                                                getippt(zusammenhang))))))
                .then(Commands.literal("verlassen")
                        .then(berufsargument()
                                .executes(zusammenhang -> rufe(zusammenhang,
                                        auftrag -> verlasse(auftrag,
                                                getippt(zusammenhang), false)))
                                .then(Commands.literal(BESTAETIGEN)
                                        .executes(zusammenhang -> rufe(zusammenhang,
                                                auftrag -> verlasse(auftrag,
                                                        getippt(zusammenhang), true))))))
                .build();
    }

    /**
     * Das Argument mit seiner Vervollstaendigung.
     *
     * <p>Vorgeschlagen werden ALLE Berufe der Einstellungsdatei, auch beim Aufgeben - und
     * das ist eine bewusste Abweichung von "ein Vorschlag, der in einer Absage endet, ist
     * Laerm": Nur die gehaltenen vorzuschlagen heisst, den Stand des Spielers zu lesen,
     * und Brigadier fragt die Vorschlaege bei JEDEM Tastendruck neu. Die Ablage hat keinen
     * Zwischenspeicher - es waere ein Datenbankzugriff je Buchstabe. Mit Teil E bekommt
     * der Beruf einen Zwischenspeicher, und dann ist das hier zu verbessern.
     */
    private RequiredArgumentBuilder<CommandSourceStack, String> berufsargument() {
        return Commands.argument(ARGUMENT, StringArgumentType.word())
                .suggests((zusammenhang, bauer) -> {
                    verzeichnis.vorschlaege(bauer.getRemaining()).forEach(bauer::suggest);
                    return bauer.buildFuture();
                });
    }

    private static String getippt(CommandContext<CommandSourceStack> zusammenhang) {
        return StringArgumentType.getString(zusammenhang, ARGUMENT);
    }

    /**
     * Wer gehandelt hat, wer liest, und was er darf - alles, was ein Zweig braucht.
     *
     * <p>Das Recht steht HIER und wird nicht spaeter nachgefragt: Gefragt werden kann nur
     * ein Sender, und den kennt allein {@link #rufe}. Eine Methode, die es sich aus einer
     * {@link Audience} holt, muesste raten, ob diese ueberhaupt ein Rechteinhaber ist -
     * im Betrieb ginge das gut, und in der ersten Probe mit einem Stellvertreter faellt
     * es um.
     *
     * @param wer               der Spieler, fuer den gehandelt wird (der Executor)
     * @param an                wer die Antwort liest (der Sender) - bei
     *                          {@code /execute as} sind das zwei verschiedene
     * @param darfSpezialisieren ob der SENDER das Recht fuer Spezialisierungen hat. Der
     *                          Sender und nicht der Executor: Wer {@code /execute as}
     *                          benutzt, handelt mit seinen eigenen Rechten
     */
    private record Auftrag(UUID wer, Audience an, boolean darfSpezialisieren) {
    }

    /** Was ein Zweig dieses Befehls tut, wenn ein Spieler ihn getippt hat. */
    private interface Spielerzweig {
        void tue(Auftrag auftrag);
    }

    /**
     * Der eine Weg von einem Zweig zu einer Tat - mit der Pruefung auf die Konsole.
     *
     * <p>Alle fuenf Zweige laufen hier durch. Die Pruefung an fuenf Stellen abzuschreiben
     * waere vier Gelegenheiten, sie zu vergessen - und ein vergessenes Exemplar waere eine
     * Ausnahme in der Konsole statt einer Absage.
     *
     * <p>{@code getExecutor()} und nicht {@code getSender()}: Bei
     * {@code /execute as <spieler> run beruf stand} ist der Executor der Spieler und der
     * Sender die Konsole. Gehandelt wird fuer den Executor, GELESEN wird beim Sender -
     * sonst stuende die Antwort in keinem Fenster.
     */
    private int rufe(CommandContext<CommandSourceStack> zusammenhang, Spielerzweig zweig) {
        CommandSourceStack quelle = zusammenhang.getSource();
        if (!(quelle.getExecutor() instanceof Player spieler)) {
            sende(quelle.getSender(), keineKonsoleZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        zweig.tue(new Auftrag(spieler.getUniqueId(), quelle.getSender(),
                quelle.getSender().hasPermission(RECHT_SPEZIALISIERUNG)));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /beruf} und {@code /beruf stand}. */
    private void zeigeStand(Auftrag auftrag) {
        UUID wer = auftrag.wer();
        Audience an = auftrag.an();
        Berufsstand stand = gelesen(wer, an);
        if (stand == null) {
            return;
        }
        Berufswerte werte = wirtschaft.werte().berufe();
        sende(an, Beruftext.standZeilen(sprache, verzeichnis, werte, stand,
                Berufsregel.restfrist(werte, stand, uhr.instant())));
    }

    /**
     * {@code /beruf waehlen <beruf>} - kostenlos, darum ohne Rueckfrage.
     *
     * <p>Das Recht fuer Spezialisierungen wird HIER gefragt und nicht in {@code requires}:
     * Ein {@code requires} am Argument verstecke den ganzen Zweig, und wer das Recht nicht
     * hat, laese "unbekannter Befehl" statt des Grundes. Die Regel bekommt die Antwort als
     * Wahrheitswert und entscheidet, an welcher Stelle der Reihenfolge sie zaehlt.
     */
    private void waehle(Auftrag auftrag, String getippt) {
        UUID wer = auftrag.wer();
        Audience an = auftrag.an();
        Berufsstand stand = gelesen(wer, an);
        if (stand == null) {
            return;
        }
        Berufswerte werte = wirtschaft.werte().berufe();
        Berufsregel.Urteil urteil = Berufsregel.pruefeWahl(verzeichnis, werte, stand,
                getippt, auftrag.darfSpezialisieren(), uhr.instant());
        if (!urteil.angenommen()) {
            sende(an, Beruftext.absageZeilen(sprache, urteil, kasse.guthaben(wer)));
            return;
        }
        boolean geschrieben;
        try {
            geschrieben = ablage.waehle(
                    new Berufsablage.Antrag(wer, urteil.beruf(), uhr.instant()), stand);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Beruf nicht waehlbar fuer " + wer, fehler);
            sende(an, nichtLesbarZeilen(sprache));
            return;
        }
        sende(an, geschrieben
                ? Beruftext.gewaehltZeilen(sprache, urteil.beruf())
                : gleichzeitigZeilen(sprache));
    }

    /**
     * {@code /beruf verlassen <beruf> [bestaetigen]} - die einzige Tat, die Geld kostet.
     *
     * <p>Geurteilt wird VOR der Rueckfrage, mit dem Guthaben: So liest ein Spieler, dem das
     * Geld fehlt, das sofort und nicht erst nach dem zweiten Befehl.
     */
    private void verlasse(Auftrag auftrag, String getippt, boolean bestaetigt) {
        UUID wer = auftrag.wer();
        Audience an = auftrag.an();
        Berufsstand stand = gelesen(wer, an);
        if (stand == null) {
            return;
        }
        Berufswerte werte = wirtschaft.werte().berufe();
        long guthaben = kasse.guthaben(wer);
        Berufsregel.Urteil urteil = Berufsregel.pruefeVerlassen(verzeichnis, werte, stand,
                getippt, guthaben, uhr.instant());
        if (!urteil.angenommen()) {
            sende(an, Beruftext.absageZeilen(sprache, urteil, guthaben));
            return;
        }
        if (urteil.kostenpflichtig() && !bestaetigt) {
            sende(an, Beruftext.warnungZeilen(sprache, urteil.beruf(), urteil.kosten(),
                    werte.sperrfrist()));
            return;
        }
        buchen(wer, an, stand, urteil);
    }

    /**
     * Buchen, schreiben, und bei einem Fehlschlag erstatten.
     *
     * <p>Die Erstattung ist keine Hoeflichkeit, sondern die Gegenbuchung zu einer Tat, die
     * nicht stattgefunden hat. Sie steht als eigene Zeile im Kontobuch und nicht als
     * stilles Zurueckdrehen - zwei lesbare Zeilen sind nachvollziehbar, ein verschwundener
     * Betrag nicht.
     */
    private void buchen(UUID wer, Audience an, Berufsstand stand,
                        Berufsregel.Urteil urteil) {
        if (urteil.kostenpflichtig()
                && !kasse.abbuchen(wer, urteil.kosten(), Buchungsgrund.BERUF_VERLASSEN)) {
            // Zwischen dem Urteil und hier hat etwas anderes gebucht. Dieselbe Absage wie
            // aus dem Urteil, damit der Spieler denselben Satz liest wie beim ersten
            // Versuch - der Grund ist ja derselbe.
            sende(an, Beruftext.absageZeilen(sprache, new Berufsregel.Urteil(
                    Berufsregel.Ausgang.GELD_FEHLT, urteil.beruf(), urteil.kosten(),
                    Duration.ZERO, 0), kasse.guthaben(wer)));
            return;
        }
        boolean geschrieben;
        try {
            geschrieben = ablage.verlasse(
                    new Berufsablage.Antrag(wer, urteil.beruf(), uhr.instant()), stand);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Beruf nicht ablegbar fuer " + wer, fehler);
            erstatte(wer, urteil);
            sende(an, nichtLesbarZeilen(sprache));
            return;
        }
        if (!geschrieben) {
            erstatte(wer, urteil);
            sende(an, gleichzeitigZeilen(sprache));
            return;
        }
        sende(an, Beruftext.verlassenZeilen(sprache, urteil.beruf(), urteil.kosten()));
    }

    private void erstatte(UUID wer, Berufsregel.Urteil urteil) {
        if (urteil.kostenpflichtig()) {
            kasse.gutschreiben(wer, urteil.kosten(), Buchungsgrund.BERUF_ERSTATTET);
        }
    }

    /**
     * Den Stand lesen - oder die Absage senden und {@code null} zurueckgeben.
     *
     * <p>Alle vier Zweige brauchen ihn, und alle vier muessen denselben Ausgang nehmen,
     * wenn die Ablage nicht antwortet. Vier Abschriften waeren drei Gelegenheiten, den
     * Fang zu vergessen - und dann liest ein Spieler eine Ausnahme im Chat.
     */
    private Berufsstand gelesen(UUID wer, Audience an) {
        try {
            return ablage.lies(wer);
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE, "Berufe nicht lesbar fuer " + wer, fehler);
            sende(an, nichtLesbarZeilen(sprache));
            return null;
        }
    }

    /** Der eine Sendeweg - damit eine Probe ihn mitschreiben kann. */
    private static void sende(Audience an, List<Component> zeilen) {
        zeilen.forEach(an::sendMessage);
    }
}
