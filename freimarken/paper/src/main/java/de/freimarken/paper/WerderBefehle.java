package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Inselbuch;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Parzelle;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * {@code /werder} - die eigenen Meerinseln und das Nehmen einer neuen.
 *
 * <p>Geschnitten wie {@link FreistadtBefehle}: kein Wort im Programm (alles ueber
 * {@link Werdertext} und {@link WerderSatz}), keine Entscheidung im Programm (alles im
 * {@link Inselbuch}), und genau EINE private {@link #sende}-Stelle, durch die jeder Zweig
 * hinausgeht.
 *
 * <p><b>Der Baum folgt Plan 2423</b>: {@code /werder hafen nehmen}, und daneben ist Platz
 * fuer {@code wolke nehmen} aus Schritt 11. Das Wort {@code hafen} ist darum kein
 * Schmuck - es unterscheidet die beiden Werderwelten, und ohne es muesste der Befehl mit
 * Schritt 11 umgebaut werden.
 *
 * <p><b>Keine zwei Stufen, anders als beim Gruenden einer Freistadt:</b> Dort kostet ein
 * Vertipper im NAMEN 400.000 Mark, und darum gibt es eine Rueckfrage. Hier gibt es nichts
 * zu tippen - genommen wird die Insel, auf der der Spieler STEHT, und was sie kostet,
 * steht im Handbalken und in {@code /werder hafen}, bevor er es tut.
 */
final class WerderBefehle {

    /** Der Name des Befehls. */
    static final String ARGUMENT = "werder";

    /** Das Wort fuer Hafenwerder - daneben kommt {@code wolke} mit Schritt 11. */
    static final String ARGUMENT_HAFEN = "hafen";

    /**
     * Die Auskunft: die eigenen Inseln und was an einer Stelle liegt.
     *
     * <p>Paketprivat wie bei {@link FreistadtBefehle}, damit die Probe denselben Text
     * nimmt: Ein Spieler, der in der Probe ein anderes Recht bekommt als im Spiel,
     * laeuft durch einen Zweig, den es nicht gibt.
     */
    static final String RECHT = "freimarken.werder";

    /** Das Nehmen - ein eigenes Recht, denn es bewegt 75.000 Mark. */
    static final String RECHT_NEHMEN = "freimarken.werder.nehmen";

    /**
     * Die Vorsilbe der Obergrenze: {@code freimarken.inseln.2} erlaubt zwei Inseln.
     *
     * <p>Dieselbe Bauart wie bei den Parzellen, und die Zahl steht in der
     * Rechteverwaltung und nicht im Programm - so bleibt sie aenderbar, wenn die
     * Rangstufen aus Schritt 12 ihre endgueltigen Zahlen bekommen.
     */
    private static final String RECHT_ANZAHL = "freimarken.inseln.";

    /** Der Zuschlag - eine Mitgliedschaft legt etwas auf den Rang. */
    private static final String RECHT_EXTRA_ANZAHL = "freimarken.extra.inseln.";

    /**
     * Die Werbezeile aus {@code hinweise.rang-werbung} - als ABFRAGE.
     *
     * <p>Aus demselben Grund wie die Hoechstzahl: Sie ist neuladbar. Beim Bauen kopiert
     * waere sie nach {@code /freimarken neuladen} die alte, und ein Hinweis, der eine
     * Stufe bewirbt, die es nicht mehr gibt, ist schlimmer als keiner.
     */
    private final Supplier<String> rangWerbung;

    private final Inselbuch buch;
    private final Kasse kasse;
    private final Sprache sprache;

    /**
     * Die Inselwelt - LEER, wenn es sie nicht gibt.
     *
     * <p>Dieselbe Entscheidung wie bei {@link FreistadtBefehle}: Der Befehl wird auch dann
     * angemeldet, und jeder Zweig sagt es dann. Ein Befehl, den es je nach
     * Einstellungsdatei gibt oder nicht, waere fuer einen Spieler ein Fehler ohne
     * Erklaerung.
     */
    private final String weltschluessel;

    /** Derselbe Name OHNE Namensraum - er steht im Weg "/warp hafenwerder". */
    private final String weltname;

    /**
     * Die Obergrenze aus der Einstellungsdatei - als Abfrage und nicht als Zahl.
     *
     * <p>Sie steht in der Absage "du hast genug", und ein Neuladen muss sie erreichen.
     * Was ein RANG daraufsetzt, kommt getrennt aus {@link Rangzahl}.
     */
    private final IntSupplier hoechstzahl;

    WerderBefehle(Inselbuch buch, Kasse kasse, Sprache sprache, String weltschluessel,
                  String weltname, IntSupplier hoechstzahl,
                  Supplier<String> rangWerbung) {
        this.buch = buch;
        this.kasse = kasse;
        this.sprache = sprache;
        this.weltschluessel = weltschluessel;
        this.weltname = weltname;
        this.hoechstzahl = hoechstzahl;
        this.rangWerbung = rangWerbung;
    }

    LiteralCommandNode<CommandSourceStack> werder() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(this::zeigeListe)
                .then(Commands.literal("liste")
                        .executes(this::zeigeListe))
                // HAFENWERDER. Das Literal traegt ein eigenes .executes, und das ist
                // keine Bequemlichkeit: Ein passendes Literal verdeckt in Brigadier
                // jedes Geschwister vollstaendig (CommandNode.getRelevantNodes) - ohne
                // eigenes .executes endete "/werder hafen" in Brigadiers englischem
                // "Unknown or incomplete command". Dieselbe Falle wie bei
                // /freistadt grundstueck.
                //
                // Und es zeigt, WAS HIER LIEGT: Wer nehmen will, braucht als erstes die
                // Auskunft, ob hier ueberhaupt etwas zu nehmen ist und was es kostet.
                .then(Commands.literal(ARGUMENT_HAFEN)
                        .executes(this::zeigeHier)
                        .then(Commands.literal("nehmen")
                                .requires(quelle -> quelle.getSender()
                                        .hasPermission(RECHT_NEHMEN))
                                .executes(this::nehme)))
                .build();
    }

    /**
     * Der nackte Befehl und {@code liste} tun DASSELBE.
     *
     * <p>Kein zweiter Weg zu einer zweiten Antwort - dieselbe Entscheidung wie bei
     * {@code /freistadt}. Der Zweitname steht da, damit ihn findet, wer ihn sucht.
     *
     * <p><b>Die Liste geht ueberall</b>, nicht nur in Hafenwerder: Wer wissen will, wo
     * seine Insel liegt, steht gerade nicht darauf.
     */
    private int zeigeListe(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (buch == null || weltschluessel.isEmpty()) {
            sende(spieler, Werdertext.keineWeltZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        List<Parzelle> meine;
        long preis;
        try {
            meine = buch.inselnVon(weltschluessel, spieler.getUniqueId());
            preis = buch.preis();
        } catch (RuntimeException fehler) {
            sende(spieler, sprache.bau(WerderSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        sende(spieler, Werdertext.listenZeilen(sprache, meine, preis));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Was an dieser Stelle liegt - {@code /werder hafen}.
     *
     * <p>Ohne Rechtepruefung ueber {@link #RECHT} hinaus: Wer in Hafenwerder steht, darf
     * lesen, wem der Boden unter ihm gehoert. Dieselbe Entscheidung wie bei
     * {@code /freistadt kasse}.
     */
    private int zeigeHier(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        Location ort = ortInHafenwerder(spieler);
        if (ort == null) {
            return Command.SINGLE_SUCCESS;
        }
        try {
            sende(spieler, Werdertext.hierZeilen(sprache, buch.plan(),
                    ort.getBlockX(), ort.getBlockZ(),
                    buch.inselAn(weltschluessel, ort.getBlockX(), ort.getBlockZ()),
                    spieler.getUniqueId(), buch.preis(), Spielername::von));
        } catch (RuntimeException fehler) {
            sende(spieler, sprache.bau(WerderSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine()));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die Insel nehmen, auf der der Spieler steht.
     *
     * <p>Das Guthaben VOR dem Versuch, denn danach ist es abgebucht: Die Absage "zu wenig
     * Mark" nennt, was fehlt, und die Erfolgszeile, was bleibt - beides rechnet sich nur
     * gegen den Stand von vorher.
     */
    private int nehme(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        Location ort = ortInHafenwerder(spieler);
        if (ort == null) {
            return Command.SINGLE_SUCCESS;
        }
        int erlaubt = Rangzahl.ausRecht(spieler, RECHT_ANZAHL, RECHT_EXTRA_ANZAHL,
                hoechstzahl.getAsInt());
        long guthaben = kasse.guthaben(spieler.getUniqueId());

        Inselbuch.Entscheid entscheid = buch.nehmen(weltschluessel,
                spieler.getUniqueId(), ort.getBlockX(), ort.getBlockZ(), erlaubt);

        sende(spieler, Werdertext.nehmenZeilen(sprache, entscheid, erlaubt, guthaben,
                rangWerbung.get(), Spielername::von));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Der Standort, wenn der Spieler in Hafenwerder steht - sonst {@code null} und eine
     * Zeile ist schon unterwegs.
     *
     * <p>Die Absage geht VON HIER aus und nicht aus den zwei Zweigen: Zwei Stellen, die
     * dieselbe Lage beschreiben, beschreiben sie eines Tages verschieden. Und sie nennt
     * den WELTNAMEN aus der Einstellungsdatei, nicht den Schluessel - was ein Spieler
     * tippt, ist {@code /warp hafenwerder} und nicht {@code /warp freimarken:hafenwerder}.
     */
    private Location ortInHafenwerder(Player spieler) {
        if (buch == null || weltschluessel.isEmpty()) {
            sende(spieler, Werdertext.keineWeltZeilen(sprache));
            return null;
        }
        Location ort = spieler.getLocation();
        if (ort.getWorld() == null
                || !weltschluessel.equals(ort.getWorld().key().asString())) {
            sende(spieler, Werdertext.falscheWeltZeilen(sprache, weltname));
            return null;
        }
        return ort;
    }

    private static Player spielerAus(CommandContext<CommandSourceStack> zusammenhang) {
        // Spielerpflichtig wie /freistadt: Die Zeilen sind fuer ein Chatfenster gemacht,
        // und genommen wird fuer jemanden, dem Geld gehoert und der irgendwo STEHT.
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
