package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Ladenverzeichnis;
import de.freimarken.kern.Mietvertrag;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Der Befehl {@code /laden} - die Ladeneinheiten ansehen, mieten, verlaengern, kuendigen.
 *
 * <p><b>Kein Wort steht hier.</b> Jede Zeile kommt aus {@link Ladentext} oder
 * {@link Miettext}, ihr Wortlaut aus {@code sprache-vorlage.yml} und ihr Vertrag aus
 * {@link LadenSatz} beziehungsweise {@link MietSatz} - derselbe Griff wie bei
 * {@link MarktBefehle}.
 *
 * <p><b>Keine Entscheidung steht hier.</b> Was eine Tat kostet, ob sie erlaubt ist und was
 * sie bewirkt, entscheidet {@link Mietbuch}; welcher Satz zu welchem Bescheid gehoert,
 * entscheidet {@link Miettext}. Dieser Befehl liest ein Argument, prueft den ORT und
 * schickt Zeilen hinaus.
 *
 * <p><b>Nur das MIETEN haengt am Ort.</b> Wer einen Stand nimmt, soll in der Halle stehen
 * und ihn gesehen haben - dieselbe Linie wie beim Bundesmarkt, wo das Verkaufen am Ort
 * haengt und die Liste nicht. {@code verlaengern} und {@code kuendigen} gehen von
 * ueberall: Sie beruehren nur den eigenen Vertrag, und eine Reise nach Altfurt, um zu
 * kuendigen, waere Muehe ohne Zweck. Geprueft wird die WELT und nicht der Quader der
 * Einheit: Ein Spieler steht beim Tippen irgendwo in der Halle und nicht zwangslaeufig in
 * dem Stand, den er nehmen will.
 *
 * <p><b>Die Liste darf jeder sehen, auch von ueberall.</b> Sie ist eine Auskunft und keine
 * Tat: Wer wissen will, welche Staende es gibt, muss nicht erst nach Altfurt reisen.
 *
 * <p><b>Spielerpflichtig</b>, wie {@code /markt}: Die Zeilen tragen Farben und einen
 * Einzug fuer ein Chatfenster, und drei der vier Taten brauchen einen Spieler, dem Geld
 * gehoert. Die Konsole liest die Einheiten beim Start im Protokoll, und zwar
 * vollstaendiger - dort stehen auch die Bloecke.
 */
final class LadenBefehle {

    /** Der Name des Befehls - Plan 11.1 fuehrt {@code /laden}. */
    static final String ARGUMENT = "laden";

    /** Der Name des Arguments von {@code mieten}. */
    static final String ARGUMENT_NAME = "name";

    /**
     * Das Recht fuer {@code /laden} - die Liste.
     *
     * <p>Vorgabe fuer alle, wie bei {@code /markt}: Die Liste ist eine Auskunft.
     */
    static final String RECHT = "freimarken.laden";

    /**
     * Das Recht, einen Laden zu MIETEN - ein eigenes.
     *
     * <p>Ein Recht und keine Rangschwelle, und das ist eine Entscheidung dieses Teils:
     * Plan 9 haengt "Laden mieten" am Rang Siedler, und den Aufstieg bringt erst
     * Schritt 12. Bis dahin ist es ein Recht mit Vorgabe fuer alle - sonst waere Teil B
     * fertig und von niemandem zu benutzen. Wer schon heute staffeln will, nimmt das Recht
     * weg und gibt es einer Gruppe; Schritt 12 haengt dann den Rang daran und nicht eine
     * zweite Pruefung daneben.
     */
    static final String RECHT_MIETEN = "freimarken.laden.mieten";

    private final Ladenverzeichnis verzeichnis;
    private final Mietbuch mietbuch;
    private final Sprache sprache;
    private final String weltschluessel;
    private final IntSupplier dauerTage;

    /**
     * @param verzeichnis    die Einheiten - beim Start gelesen und danach unveraenderlich.
     *                       Darum hier als Wert und nicht als Zelle wie der
     *                       {@code Wirtschaftsstand}: Es gibt nichts, was ein Neuladen
     *                       daran aendern koennte
     * @param weltschluessel die Welt der Markthalle, aus {@code laeden.welt}. Nur beim
     *                       Start gelesen, wie bei {@code /markt}: Eine Welt ist zur
     *                       Laufzeit nicht zu tauschen
     * @param dauerTage      die eingestellte Mietdauer - beim GEBRAUCH gefragt und nicht
     *                       kopiert, damit ein Neuladen die Absage "noch zu frueh"
     *                       erreicht. Sie nennt die Dauer, und eine veraltete Zahl darin
     *                       waere eine Absage, die sich nicht nachrechnen laesst
     */
    LadenBefehle(Ladenverzeichnis verzeichnis, Mietbuch mietbuch, Sprache sprache,
                 String weltschluessel, IntSupplier dauerTage) {
        if (weltschluessel == null || weltschluessel.isBlank()) {
            throw new IllegalArgumentException("Ohne Welt ist die Markthalle kein Ort,"
                    + " sondern ein Menue - und von jeder Welt aus zu mieten");
        }
        this.verzeichnis = verzeichnis;
        this.mietbuch = mietbuch;
        this.sprache = sprache;
        this.weltschluessel = weltschluessel;
        this.dauerTage = dauerTage;
    }

    LiteralCommandNode<CommandSourceStack> laden() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(this::zeigeListe)
                .then(Commands.literal("liste")
                        .executes(this::zeigeListe))
                .then(Commands.literal("mieten")
                        .requires(quelle -> quelle.getSender().hasPermission(RECHT_MIETEN))
                        .then(Commands.argument(ARGUMENT_NAME, StringArgumentType.word())
                                .suggests((zusammenhang, bauer) -> {
                                    vorschlaege(bauer.getRemaining())
                                            .forEach(bauer::suggest);
                                    return bauer.buildFuture();
                                })
                                .executes(this::miete)))
                .then(Commands.literal("verlaengern")
                        .requires(quelle -> quelle.getSender().hasPermission(RECHT_MIETEN))
                        .executes(this::verlaengere))
                .then(Commands.literal("kuendigen")
                        .requires(quelle -> quelle.getSender().hasPermission(RECHT_MIETEN))
                        .executes(this::kuendige))
                .build();
    }

    /**
     * Der nackte Befehl und {@code liste} tun DASSELBE.
     *
     * <p>Kein zweiter Weg zu einer zweiten Antwort: Dieselbe Entscheidung wie bei
     * {@code /markt}, wo der nackte Befehl die Liste zeigt. Der Zweitname steht da, damit
     * ihn findet, wer ihn sucht - nicht, damit er etwas anderes tut.
     */
    private int zeigeListe(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        // Die Standzeile wird MITGEGEBEN und nicht angehaengt: So steht das ganze Bild in
        // einer Naht, und eine Probe sieht die Reihenfolge, die der Spieler sieht.
        sende(spieler, Ladentext.listeZeilen(sprache, verzeichnis, standZeile(spieler)));
        return Command.SINGLE_SUCCESS;
    }

    private int miete(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (!spieler.getWorld().key().asString().equals(weltschluessel)) {
            sende(spieler, Miettext.falscheWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        String getippt = StringArgumentType.getString(zusammenhang, ARGUMENT_NAME);
        sende(spieler, Miettext.mietenZeilen(sprache,
                mietbuch.mieten(spieler.getUniqueId(), getippt), getippt));
        return Command.SINGLE_SUCCESS;
    }

    private int verlaengere(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        sende(spieler, Miettext.verlaengernZeilen(sprache,
                mietbuch.verlaengern(spieler.getUniqueId()), dauerTage.getAsInt()));
        return Command.SINGLE_SUCCESS;
    }

    private int kuendige(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        sende(spieler, Miettext.kuendigenZeilen(sprache,
                mietbuch.kuendigen(spieler.getUniqueId())));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die eigene Mietzeile - oder der Hinweis, dass noch kein Laden da ist.
     *
     * <p>Antwortet die Ablage nicht, steht hier die Zeile "noch kein Laden". Das ist eine
     * bewusste Unwahrheit im Stoerfall und die mildeste von drei Moeglichkeiten: Die Liste
     * bricht nicht ab, der Spieler sieht keine rote Zeile, und was wirklich war, steht im
     * Protokoll - dafuer sorgt {@link Mietbuch#vertragVon}.
     */
    private Component standZeile(Player spieler) {
        Mietvertrag vertrag = mietbuch.vertragVon(spieler.getUniqueId());
        return Miettext.standZeile(sprache, vertrag,
                vertrag == null ? 0L : mietbuch.restTage(vertrag));
    }

    /**
     * Die Vorschlaege zu {@code /laden mieten}.
     *
     * <p>Vorgeschlagen werden ALLE Einheiten der Einstellungsdatei, auch die vermieteten -
     * dieselbe bewusste Abweichung von "ein Vorschlag, der in einer Absage endet, ist
     * Laerm" wie bei den Berufen, und aus demselben Grund: Nur die freien vorzuschlagen
     * heisst, die Mietvertraege zu lesen, und Brigadier fragt die Vorschlaege bei JEDEM
     * Tastendruck neu. Das waere ein Datenbankzugriff je Buchstabe.
     */
    private List<String> vorschlaege(String angefangen) {
        String klein = angefangen.toLowerCase(Locale.ROOT);
        return verzeichnis.namen().stream()
                .filter(name -> name.startsWith(klein))
                .toList();
    }

    private static Player spielerAus(CommandContext<CommandSourceStack> zusammenhang) {
        // Spielerpflichtig, wie /markt und /mark: Die Zeilen sind fuer ein Chatfenster
        // gemacht, und drei der vier Taten brauchen einen Spieler, dem Geld gehoert.
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
