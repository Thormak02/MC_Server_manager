package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Einsichtsstand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /einsehen} - die Einsicht ein- und ausschalten (Plan 12c.3, Schritt 16a
 * Teil D).
 *
 * <p><b>Drei Zweige und kein Umschalter</b>: {@code /einsehen} zeigt den STAND,
 * {@code /einsehen an} und {@code /einsehen aus} schalten. Ein Schalter, der beim
 * zweiten Tippen umkippt, ist in einem RECHT tueckisch: Wer nicht weiss, ob die
 * Einsicht an war, weiss nach dem Tippen auch nicht, ob sie es jetzt ist - und oeffnet
 * dann eine fremde Kiste, die er nur ansehen wollte. Darum sagt jeder Zweig, was er tut.
 *
 * <p><b>{@code an} verlaengert</b>, wenn die Einsicht schon laeuft. Das ist der Weg fuer
 * eine Untersuchung, die laenger dauert als die Frist - ein Tastendruck und eine Zeile
 * im Protokoll. Die Antwort nennt den neuen Zeitpunkt.
 *
 * <p><b>Jedes Einschalten geht ins Protokoll.</b> Einsicht ist ein Zugriff auf fremdes
 * Eigentum, und Plan 12c.3 verlangt <i>"alles protokolliert, mit Grund und
 * Bearbeiter"</i>. Einen GRUND verlangt dieser Befehl nicht - er wuerde zu "x" fuehren,
 * und die Tat ist das Hinsehen, nicht das Eingreifen. Was im Protokoll steht, ist WER
 * und WANN; der Grund steht in der Meldung, der er nachgeht.
 */
final class EinsichtBefehle {

    /**
     * Wer einsehen darf.
     *
     * <p>Das LITERAL steht hier im Plugin und nicht im Kern - {@code GrenzeTest} 31al
     * liest die {@code "freimarken.*"}-Literale des Plugin-Moduls und haelt sie gegen
     * die {@code plugin.yml}. Dieselbe Begruendung wie bei {@link MeldungBefehle#RECHT}
     * und {@link StrafBefehle#RECHTE}.
     */
    static final String RECHT = "freimarken.einsehen";

    /** Der Name des Befehls - er steht woertlich in den Saetzen der Sprachdatei. */
    static final String ARGUMENT = "einsehen";

    static final String AN = "an";

    static final String AUS = "aus";

    /** Die Uhr - als Naht, damit eine Probe einen festen Zeitpunkt hereingeben kann. */
    interface Uhr {
        Instant jetzt();
    }

    private final Einsichtsstand stand;
    private final Sprache sprache;
    private final Uhr uhr;
    private final Supplier<ZoneId> zone;
    private final Logger protokoll;

    EinsichtBefehle(Einsichtsstand stand, Sprache sprache, Uhr uhr,
                    Supplier<ZoneId> zone, Logger protokoll) {
        if (stand == null || sprache == null || uhr == null || zone == null
                || protokoll == null) {
            throw new IllegalArgumentException("/einsehen braucht den Einsichtsstand,"
                    + " Sprache, eine Uhr, die Zone und das Protokoll");
        }
        this.stand = stand;
        this.sprache = sprache;
        this.uhr = uhr;
        this.zone = zone;
        this.protokoll = protokoll;
    }

    /** Der Baum. */
    LiteralCommandNode<CommandSourceStack> einsehen() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                // Ein eigenes executes auf dem Wort selbst: Ohne es endet /einsehen in
                // Brigadiers englischem "Unknown or incomplete command" - und hier ist
                // der nackte Befehl der haeufigste, denn er beantwortet die Frage
                // "sehe ich noch ein?".
                .executes(this::zeigeStand)
                .then(Commands.literal(AN).executes(this::schalteEin))
                .then(Commands.literal(AUS).executes(this::schalteAus))
                .build();
    }

    /** {@code /einsehen} - sehe ich noch ein? */
    private int zeigeStand(CommandContext<CommandSourceStack> z) {
        Player spieler = spielerVon(z);
        if (spieler == null) {
            return nurSpieler(z);
        }
        Instant jetzt = uhr.jetzt();
        Optional<Instant> ende = stand.endeFuer(spieler.getUniqueId(), jetzt);
        sende(spieler, ende.isPresent()
                ? sprache.bau(EinsichtSatz.STAND_AN, 1L, werte(ende.get(), jetzt))
                : sprache.bau(EinsichtSatz.STAND_AUS, 1L, Einsatz.keine()));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /einsehen an} - einschalten oder verlaengern. */
    private int schalteEin(CommandContext<CommandSourceStack> z) {
        Player spieler = spielerVon(z);
        if (spieler == null) {
            return nurSpieler(z);
        }
        Instant jetzt = uhr.jetzt();
        Instant ende = stand.schalteEin(spieler.getUniqueId(), jetzt);
        // INS PROTOKOLL, und zwar jedes Einschalten: Einsicht ist ein Zugriff auf
        // fremdes Eigentum. Ohne diese Zeile gibt es keine Spur davon, dass jemand in
        // eine Kiste gesehen hat - und genau die braucht man, wenn ein Spieler fragt,
        // wer in seiner Truhe war.
        protokoll.info(spieler.getName() + " sieht ein bis " + ende
                + " (" + stand.werte().dauerSekunden() + " Sekunden).");
        sende(spieler, sprache.bau(EinsichtSatz.AN, 1L, werte(ende, jetzt)));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /einsehen aus} - ausschalten. */
    private int schalteAus(CommandContext<CommandSourceStack> z) {
        Player spieler = spielerVon(z);
        if (spieler == null) {
            return nurSpieler(z);
        }
        boolean warAn = stand.schalteAus(spieler.getUniqueId());
        if (warAn) {
            protokoll.info(spieler.getName() + " sieht nicht mehr ein.");
        }
        // ZWEI Saetze: Wer "ist jetzt aus" liest auf etwas, das nie an war, glaubt, er
        // habe eben noch eingesehen.
        sende(spieler, warAn
                ? sprache.bau(EinsichtSatz.AUS, 1L, Einsatz.keine())
                : sprache.bau(EinsichtSatz.WAR_AUS, 1L, Einsatz.keine()));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die Werte fuer die zwei Saetze mit Zeitangabe.
     *
     * <p>BEIDES: der Zeitpunkt (den merkt man sich) und die Restdauer (die sagt, ob es
     * noch reicht). An EINER Stelle gerechnet, damit sie nie auseinanderlaufen.
     */
    private Einsatz werte(Instant ende, Instant jetzt) {
        return Einsatz.von("bis", sprache.muster(EinsichtSatz.ZEIT_MUSTER)
                        .format(ende.atZone(zone.get())))
                .mit("dauer", restwort(ende, jetzt));
    }

    /**
     * Die Restzeit als Wort - ueber {@link Straftext}, damit es EINE Schreibweise gibt.
     *
     * <p>Die Dauerworte stehen in {@code StrafSatz}, und das ist richtig: Eine zweite
     * Reihe "Min./Std." in {@code EinsichtSatz} waere eine zweite Schreibweise fuer
     * dieselbe Sache, und eine davon weicht irgendwann ab. Teil B und Teil D gehoeren
     * zum selben Bereich der Moderation.
     */
    private String restwort(Instant ende, Instant jetzt) {
        Duration rest = Duration.between(jetzt, ende);
        if (rest.isNegative() || rest.isZero()) {
            rest = Duration.ofSeconds(1L);
        }
        return Straftext.dauerwort(sprache,
                new de.freimarken.kern.Strafdauer(rest));
    }

    private int nurSpieler(CommandContext<CommandSourceStack> z) {
        for (Component zeile : sprache.bau(EinsichtSatz.NUR_SPIELER, 1L,
                Einsatz.keine())) {
            z.getSource().getSender().sendMessage(zeile);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static Player spielerVon(CommandContext<CommandSourceStack> z) {
        return z.getSource().getSender() instanceof Player spieler ? spieler : null;
    }

    /** Die EINE Sendestelle dieser Klasse. */
    private void sende(Player an, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            an.sendMessage(zeile);
        }
    }
}
