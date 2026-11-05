package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /zurueck} - und {@code /back}, weil viele Spieler das tippen.
 *
 * <p>Fuehrt an den Ort, an dem der Spieler zuletzt war: vor einer Reise, vor einem
 * Teleport, oder an die Stelle, an der er gestorben ist.
 *
 * <p>Der Befehl prueft <b>nichts</b> selbst. Jede Absage - Welt nicht geladen, Boden
 * abgebaut, Lava gelaufen, zu Pferd, zu schnell hintereinander - kommt aus {@link Reise},
 * und das ist die Projektregel „alle Wege zur selben Tat". Eine zweite Fassung derselben
 * Pruefungen waere eine, die eines Tages nicht mitgepflegt wird.
 *
 * <p>Dass zweimal {@code /zurueck} wieder am Ausgangspunkt endet, steht nirgends als
 * Regel: Es folgt daraus, dass {@code Reise} vor jedem Teleport den jetzigen Ort merkt.
 */
final class RueckwegBefehle {

    /** Wer zurueck darf. Vorgabe: jeder. */
    static final String RECHT_ZURUECK = "freimarken.zurueck";

    private final Rueckweg rueckweg;
    private final Reise reise;

    /**
     * Die Saetze dieses Plugins - verdrahtet, bevor der erste Satz umzieht.
     *
     * <p>Warum alle elf Sender dieses Feld in EINEM Zug bekommen und nicht je
     * Bauschritt, steht dort, wo sie gebaut werden: {@code FreimarkenPlugin.onEnable}.
     */
    private final Sprache sprache;

    RueckwegBefehle(Rueckweg rueckweg, Reise reise, Sprache sprache) {
        this.sprache = sprache;
        this.rueckweg = rueckweg;
        this.reise = reise;
    }

    LiteralCommandNode<CommandSourceStack> zurueck() {
        return Commands.literal("zurueck")
                .requires(quelle -> quelle.getExecutor() instanceof Player
                        && quelle.getSender().hasPermission(RECHT_ZURUECK))
                .executes(z -> {
                    fuehreZurueck(z.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    /**
     * Der eine Zweig - sichtbar fuer die Probe, die ihn wirklich durchlaeuft.
     *
     * <p>Nicht {@code private}, und zwar aus demselben Grund, aus dem jeder Zweig von
     * {@link ParzellenBefehle} es nicht ist: Nur so kann {@code AufrufstelleTest} ihn mit
     * einem mitschreibenden Spieler aufrufen und belegen, dass der Tipper die Absage
     * WIRKLICH liest. Ohne Server geht das hier, weil der Zweig der Absage
     * {@link Reise} gar nicht anfasst - gebraucht wird allein der {@link Rueckweg}, und
     * der ist eine Karte im Speicher.
     */
    void fuehreZurueck(Object empfaenger) {
        if (!(empfaenger instanceof Player spieler)) {
            return;
        }
        var ziel = rueckweg.letzter(spieler.getUniqueId());
        if (ziel.isEmpty()) {
            keinOrtGemerktZeilen(sprache).forEach(spieler::sendMessage);
            return;
        }
        reise.fuehre(spieler, ziel.get(), letzterOrtWort(sprache));
    }

    // --- Die Naht: EIN Satz und EIN Wortstueck, beide ohne Player und ohne Bukkit ----

    /** Es ist nichts gemerkt - an den Tipper, und sonst an niemanden. */
    static List<Component> keinOrtGemerktZeilen(Sprache sprache) {
        return sprache.bau(ReiseSatz.ZURUECK_KEIN_ORT_GEMERKT, 1L, Einsatz.keine());
    }

    /**
     * Die Aufschrift des Reiseziels, die dieser Befehl durchgibt.
     *
     * <p>Kein gesendeter Satz, sondern ein Wortstueck, das in FREMDE Saetze geht - in die
     * vier unter {@code reise.}, die {@code {ziel}} einsetzen. Darum ein eigener Griff:
     * Ohne ihn waere nicht zu sehen, dass {@code /zurueck} an dieser Stelle eine WENDUNG
     * durchgibt, wo {@code /warp} einen Weltnamen und {@code /tpaccept} einen
     * Spielernamen durchgibt.
     */
    static String letzterOrtWort(Sprache sprache) {
        return sprache.wort(ReiseSatz.ZURUECK_LETZTER_ORT, Einsatz.keine());
    }
}
