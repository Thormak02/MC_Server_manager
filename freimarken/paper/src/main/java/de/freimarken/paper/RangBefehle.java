package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Rangschau;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /rang} - der eigene Rang, die naechste Stufe und die ganze Leiter.
 *
 * <p><b>Warum es diesen Befehl erst jetzt gibt:</b> Schritt 12 hat die Rangleiter gebaut,
 * den Aufstieg, die Rechte und fuenf Grenzen daran - aber keinen Weg, das nachzusehen.
 * Ein Spieler sah seinen Rang im Augenblick des Aufstiegs und beim Beitritt; was die
 * naechste Stufe verlangt, erfuhr er nie. Die gelbe Zeile unter jeder Absage ("Mehr
 * erlaubt ein hoeherer Rang.") verwies damit auf etwas, das er nirgends finden konnte.
 *
 * <p>Dieselbe Bauart wie {@link BerufeBefehle}: Die Zusammenstellung kommt aus dem Kern
 * ({@link Rangschau}), die Zeilen aus {@link Rangtext}, und hier steht nur der Baum, die
 * Rechtefrage und EINE Sendestelle.
 *
 * <p><b>Gemessen wird nicht hier</b>, sondern an der einen Stelle, die es darf
 * ({@code Aufstiegslauf.messeFuer}, hereingereicht als {@code Bedingungsmessung}). Ein
 * eigener Messer waere der Fehler, gegen den jene Naht gebaut ist: Dann urteilte eine
 * Auskunft anders als eine Befoerderung.
 */
final class RangBefehle {

    /**
     * Wer seinen Rang nachsehen darf - jeder.
     *
     * <p>Angemeldet in der {@code plugin.yml} mit {@code default: true}. Ohne Anmeldung
     * antwortet Bukkit mit {@code DEFAULT_PERMISSION}, und die ist OP - der Befehl waere
     * fuer jeden gewoehnlichen Spieler verborgen UND verwehrt, waehrend im Programm
     * "jeder" steht.
     */
    static final String RECHT = "freimarken.rang";

    /** Der Name des Befehls - er steht woertlich in den Saetzen der Sprachdatei. */
    private static final String ARGUMENT = "rang";

    private final Rangschau schau;
    private final Sprache sprache;
    private final Logger protokoll;

    RangBefehle(Rangschau schau, Sprache sprache, Logger protokoll) {
        if (schau == null || sprache == null || protokoll == null) {
            throw new IllegalArgumentException("/rang braucht Rangschau, Sprache und"
                    + " Protokoll");
        }
        this.schau = schau;
        this.sprache = sprache;
        this.protokoll = protokoll;
    }

    /**
     * Der Baum: {@code /rang} und {@code /rang leiter}.
     *
     * <p><b>Ein eigenes {@code executes} am Literal {@code leiter}</b> - und das nackte
     * {@code /rang} hat sein eigenes: Ein passendes Literal verdeckt in Brigadier jedes
     * Geschwister vollstaendig, und ohne eigenes {@code executes} endet der Zweig in
     * Brigadiers englischem "Unknown or incomplete command".
     *
     * <p>Das Recht steht im {@code requires} am Wurzelknoten und fragt
     * {@code quelle.getSender()}: Nur so antwortet die Pruefung auch fuer einen
     * Stellvertreter in einer Probe.
     */
    LiteralCommandNode<CommandSourceStack> rang() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(zusammenhang -> zeige(zusammenhang, false))
                .then(Commands.literal("leiter")
                        .executes(zusammenhang -> zeige(zusammenhang, true)))
                .build();
    }

    /**
     * Beide Zweige gehen durch DIESE Methode - die Unterschiede sind ein Wahrheitswert.
     *
     * <p>Der Spieler ist Pflicht: Ein Rang gehoert einem Spieler, und die Konsole hat
     * keinen. Sie bekommt dafuer eine eigene Zeile aus der Sprachdatei - anders als bei
     * {@code /markt}, dessen Konsolenzeile im Programm steht, weil dort der BETREIBER
     * gemeint ist. Hier kann auch ein Spieler auf die Konsole schreiben lassen.
     */
    private int zeige(CommandContext<CommandSourceStack> zusammenhang, boolean leiter) {
        if (!(zusammenhang.getSource().getExecutor() instanceof Player wer)) {
            sende(zusammenhang.getSource().getSender(),
                    Rangtext.keineKonsoleZeilen(sprache));
            return 0;
        }
        Optional<Rangschau.Tafel> tafel;
        try {
            tafel = schau.tafel(wer.getUniqueId());
        } catch (RuntimeException stoerung) {
            // Ein eigener Satz und kein Rueckfall auf die unterste Stufe: Der saehe aus
            // wie eine Auskunft und waere eine Vermutung.
            protokoll.log(Level.WARNING, "Der Rang von " + wer.getName() + " ist nicht"
                    + " lesbar - er bekommt eine Absage.", stoerung);
            sende(wer, Rangtext.gestoertZeilen(sprache));
            return 0;
        }
        if (tafel.isEmpty()) {
            sende(wer, Rangtext.ausZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        sende(wer, leiter ? Rangtext.leiterZeilen(sprache, tafel.get())
                : Rangtext.schauZeilen(sprache, tafel.get()));
        return Command.SINGLE_SUCCESS;
    }

    /** Die EINE Sendestelle dieser Klasse - jeder Zweig und jede Absage geht hier durch. */
    private void sende(Audience an, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            an.sendMessage(zeile);
        }
    }
}
