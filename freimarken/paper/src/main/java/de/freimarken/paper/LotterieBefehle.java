package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Lotteriebuch;
import de.freimarken.kern.Lotteriewerte;
import de.freimarken.kern.Vorrat;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /lotterie} - ein Los ziehen in Altfurt (Plan 14).
 *
 * <p><b>HIER liegt der Zufall</b>, und das ist die tragende Entscheidung dieser Klasse.
 * {@link de.freimarken.kern.Losregel} bekommt ihren Wurf als ZAHL herein - genau wie
 * {@code Fangregel} von der {@code Angelwache}. Ein Zufall im Kern waere eine Probe, die
 * manchmal gruen ist.
 *
 * <p><b>Vor dem Wurf steht ein Riegel</b>, und er ist nicht nur Vorsicht:
 * {@code Random.nextLong(0)} WIRFT. Eine leere oder ausgeschaltete Gewinntabelle hat
 * Gewichtsumme 0, und ohne diese Frage lase der Spieler eine rote englische Zeile fuer
 * eine Lage, die der Betreiber eingestellt hat. Dieselbe Zeile steht in {@code Erzwache}
 * aus demselben Grund.
 *
 * <p><b>Zwei Zweige, und beide haben ihr eigenes {@code executes}</b>: {@code /lotterie}
 * zeigt den Stand, {@code /lotterie ziehen} zieht. Ohne das zweite endet
 * {@code /lotterie ziehen} in Brigadiers englischem "Unknown or incomplete command".
 *
 * <p><b>Die Uhr kommt von aussen</b>, als {@link Instant}. Welcher TAG das ist,
 * entscheidet allein {@link Lotteriebuch#tagVon} aus der eingestellten Zone - diese
 * Klasse rechnet nichts mit Zeit.
 */
final class LotterieBefehle {

    /** Wer ein Los ziehen darf - jeder. Angemeldet mit {@code default: true}. */
    static final String RECHT = "freimarken.lotterie";

    /** Der Name des Befehls - er steht woertlich in den Saetzen der Sprachdatei. */
    static final String ARGUMENT = "lotterie";

    /** Der Zweig, der wirklich zieht. */
    static final String ZIEHEN = "ziehen";

    /** Wie viele Stufen der Stand vorrechnet. */
    private static final int STUFEN_IM_STAND = 5;

    /**
     * Die Uhr - als Naht, damit eine Probe einen festen Zeitpunkt hereingeben kann.
     *
     * <p>{@code Instant} und nicht {@code LocalDate}: Welcher Tag gilt, entscheidet die
     * eingestellte Zone, und diese Klasse soll darueber nichts wissen.
     */
    interface Uhr {
        Instant jetzt();
    }

    private final Lotteriebuch buch;
    private final Sprache sprache;
    private final Uhr uhr;
    private final Random wuerfel;
    private final java.util.function.Function<Player, Vorrat> rucksack;

    /**
     * @param rucksack die Naht zum Inventar - hereingegeben und nicht hier gerufen,
     *                 genau wie bei {@link ParzellenBefehle}. GEMESSEN am 22.11.2026:
     *                 {@code new ItemStack(Material)} braucht die Registry des Servers
     *                 und WIRFT auf dem Probenklassenpfad. Ohne diese Naht waere der
     *                 Gegenstandsweg - der haeufigste Gewinn der Tabelle - der eine
     *                 Zweig, den keine Probe durchlaufen kann.
     */
    LotterieBefehle(Lotteriebuch buch, Sprache sprache, Uhr uhr, Random wuerfel,
                    java.util.function.Function<Player, Vorrat> rucksack) {
        if (buch == null || sprache == null || uhr == null || wuerfel == null
                || rucksack == null) {
            throw new IllegalArgumentException("/lotterie braucht das Lotteriebuch,"
                    + " Sprache, eine Uhr, einen Wuerfel und die Vorratsnaht");
        }
        this.buch = buch;
        this.sprache = sprache;
        this.uhr = uhr;
        this.wuerfel = wuerfel;
        this.rucksack = rucksack;
    }

    /** Der Baum: {@code /lotterie} und {@code /lotterie ziehen}. */
    LiteralCommandNode<CommandSourceStack> lotterie() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(this::zeigeStand)
                .then(Commands.literal(ZIEHEN)
                        .executes(this::ziehe))
                .build();
    }

    /**
     * {@code /lotterie} - was ein Los jetzt kostet, ohne etwas zu tun.
     *
     * <p>Auch die KONSOLE darf das lesen: Ein Betreiber soll nachsehen koennen, was er
     * eingestellt hat, ohne sich einzuloggen. Gefragt wird dann nach dem Stand des
     * Senders, und die Konsole hat kein Konto - darum nennt der Zweig fuer sie nur die
     * Staffel.
     */
    private int zeigeStand(CommandContext<CommandSourceStack> zusammenhang) {
        Lotteriewerte werte = buch.werte();
        List<Component> zeilen = new ArrayList<>(
                sprache.bau(LotterieSatz.KOPF, 1L, Einsatz.keine()));
        if (!werte.greift()) {
            zeilen.addAll(sprache.bau(LotterieSatz.AUS, 1L, Einsatz.keine()));
            sende(zusammenhang.getSource().getSender(), zeilen);
            return Command.SINGLE_SUCCESS;
        }
        if (zusammenhang.getSource().getSender() instanceof Player wer) {
            Lotteriebuch.Auskunft auskunft = buch.frage(wer.getUniqueId(), uhr.jetzt());
            zeilen.addAll(standzeile(auskunft, werte));
        }
        zeilen.addAll(sprache.bau(LotterieSatz.STAND_STAFFEL, 1L,
                Einsatz.von("staffel", werte.staffelAlsText(STUFEN_IM_STAND))));
        zeilen.addAll(sprache.bau(LotterieSatz.STAND_HINWEIS, 1L, Einsatz.keine()));
        sende(zusammenhang.getSource().getSender(), zeilen);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die eine Zeile, die den Stand des Spielers nennt - gratis, mit Preis oder genug.
     *
     * <p>Drei Saetze und nicht einer mit einer 0: "kostet 0 Mark" schreibt so niemand,
     * und das gratis Los ist der Kern der ganzen Einrichtung.
     */
    private List<Component> standzeile(Lotteriebuch.Auskunft auskunft,
                                       Lotteriewerte werte) {
        long hoechste = werte.hoechsteLoseJeTag();
        if (!auskunft.nochEines()) {
            // 1L und nicht hoechste: STAND_GENUG ist Art.SATZ und hat keine
            // Einzahlform zu waehlen. Eine Anzahl hier behauptete eine Zaehlform, die
            // der Satz nicht hat - gemessen an BildGleichTest 11b.
            return sprache.bau(LotterieSatz.STAND_GENUG, 1L,
                    Einsatz.von("hoechste", hoechste));
        }
        Einsatz zahlen = Einsatz.von("gezogen", auskunft.loseHeute())
                .mit("hoechste", hoechste);
        if (auskunft.preis() == 0L) {
            return sprache.bau(LotterieSatz.STAND_GRATIS, 1L, zahlen);
        }
        return sprache.bau(LotterieSatz.STAND_PREIS, 1L,
                zahlen.mit("preis", sprache.betrag(auskunft.preis())));
    }

    /**
     * {@code /lotterie ziehen} - das Los.
     *
     * <p>Der WURF wird hier gezogen und dem Kern als Zahl gegeben. Vor ihm steht die
     * Frage nach der Gewichtsumme: {@code nextLong(0)} wirft, und eine leere Tabelle ist
     * eine erlaubte Einstellung.
     */
    private int ziehe(CommandContext<CommandSourceStack> zusammenhang) {
        if (!(zusammenhang.getSource().getSender() instanceof Player wer)) {
            // Gelesen und nicht verborgen, wie bei /regeln annehmen: Ein Betreiber, der
            // den Zweig nicht findet, weil sein Knoten verborgen ist, sucht an der
            // falschen Stelle.
            sende(zusammenhang.getSource().getSender(),
                    sprache.bau(LotterieSatz.NUR_SPIELER, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        Lotteriewerte werte = buch.werte();
        long summe = werte.gewichtsumme();
        if (!werte.greift() || summe <= 0L) {
            // DER RIEGEL VOR DEM WURF - siehe den Klassenkopf: nextLong(0) wirft.
            sende(wer, sprache.bau(LotterieSatz.AUS, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        long wurf = wuerfel.nextLong(summe);
        Vorrat vorrat = rucksack.apply(wer);

        Lotteriebuch.Entscheid entscheid =
                buch.ziehe(wer.getUniqueId(), uhr.jetzt(), wurf, vorrat);

        sende(wer, meldung(entscheid, werte, wer));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Was der Spieler liest - je Bescheid eine Antwort.
     *
     * <p>Ein Schalter ohne {@code default}: Wer dem {@link Lotteriebuch.Bescheid} einen
     * achten Wert hinzufuegt, wird vom Uebersetzer gefragt, was der Spieler dann liest.
     * Eine Vorgabe daneben waere ein Ausgang, der stumm bleibt.
     */
    private List<Component> meldung(Lotteriebuch.Entscheid entscheid,
                                    Lotteriewerte werte, Player wer) {
        return switch (entscheid.bescheid()) {
            case GEZOGEN -> gewinnzeile(entscheid);
            case AUS -> sprache.bau(LotterieSatz.AUS, 1L, Einsatz.keine());
            // 1L: auch HEUTE_GENUG ist Art.SATZ - siehe standzeile().
            case HEUTE_GENUG -> sprache.bau(LotterieSatz.HEUTE_GENUG, 1L,
                    Einsatz.von("hoechste", werte.hoechsteLoseJeTag()));
            case KEIN_PLATZ -> sprache.bau(LotterieSatz.KEIN_PLATZ,
                    entscheid.fehlenderPlatz(),
                    Einsatz.von("fehlt", entscheid.fehlenderPlatz()));
            // Der Stand wird hier NEU gefragt und nicht aus der Lesung vor dem Zug
            // mitgeschleppt: Bei ZU_WENIG_MARK ist nichts abgebucht, die Antwort ist
            // also dieselbe - und ein Feld dafuer waere bei zwei Spielern zugleich der
            // Stand des anderen.
            case ZU_WENIG_MARK -> sprache.bau(LotterieSatz.ZU_WENIG, 1L,
                    Einsatz.von("preis", sprache.betrag(entscheid.preis()))
                            .mit("guthaben", sprache.betrag(
                                    buch.frage(wer.getUniqueId(), uhr.jetzt())
                                            .guthaben())));
            case ERSTATTET -> sprache.bau(LotterieSatz.ERSTATTET, 1L,
                    Einsatz.von("preis", sprache.betrag(entscheid.preis())));
            case GESTOERT -> sprache.bau(LotterieSatz.GESTOERT, 1L, Einsatz.keine());
        };
    }

    /**
     * Die Gewinnzeile - je Art eine.
     *
     * <p>Ein Schalter ohne {@code default}, wie in {@code Lotteriebuch.zahleAus}: Wer der
     * {@link Lotteriewerte.Art} einen vierten Wert hinzufuegt, wird gefragt, wie er
     * gemeldet wird.
     */
    private List<Component> gewinnzeile(Lotteriebuch.Entscheid entscheid) {
        Lotteriewerte.Gewinn gewinn = entscheid.gewinn().orElseThrow();
        // DER PREIS ALS WORT und nicht als Zahl: Bei einem gratis Los stand hier
        // "(Los: 0)" - gemessen am 22.11.2026 an der Bildtafel. Ein Preis von 0 ist kein
        // Preis, sondern eine Gabe, und die hat ein Wort in der Sprachdatei.
        Einsatz werte = Einsatz.von("gewinn", gewinn.imSpiel())
                .mit("preis", entscheid.preis() == 0L
                        ? sprache.wort(LotterieSatz.GRATIS_WORT, Einsatz.keine())
                        : sprache.betrag(entscheid.preis()));
        // DIE EINHEIT JE ART, und zwar ueber die Methode, die nach der Waehrung heisst:
        // Beide Zahlen sind long, und eine verwechselte Zeile zeigte dem Spieler
        // "3 Mark", wo er 3 Goldmark hat - das Tausendfache daneben.
        return switch (gewinn.art()) {
            case MARK -> sprache.bau(LotterieSatz.GEZOGEN_MARK, 1L,
                    werte.mit("menge", sprache.betrag(gewinn.menge())));
            case GOLDMARK -> sprache.bau(LotterieSatz.GEZOGEN_GOLDMARK, gewinn.menge(),
                    werte.mit("menge", sprache.goldmark(gewinn.menge())));
            // Ein Gegenstand ist kein Betrag: Hier bleibt die nackte Stueckzahl, und die
            // Einzahlform des Satzes laesst sie weg.
            case GEGENSTAND -> sprache.bau(LotterieSatz.GEZOGEN_GEGENSTAND,
                    gewinn.menge(), werte.mit("menge", gewinn.menge()));
        };
    }

    /** Die EINE Sendestelle dieser Klasse. */
    private void sende(Audience an, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            an.sendMessage(zeile);
        }
    }
}
