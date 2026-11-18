package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Wolkenbuch;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * {@code /wolke} - die eigenen Skyblockinseln, eine neue nehmen, heimreisen, Mitbauer.
 *
 * <p>Geschnitten wie {@link WerderBefehle}: kein Wort im Programm (alles ueber
 * {@link Wolkentext} und {@link WolkeSatz}), keine Entscheidung im Programm (alles im
 * {@link Wolkenbuch}), und genau EINE private {@link #sende}-Stelle, durch die jeder Zweig
 * hinausgeht.
 *
 * <p><b>Ein eigener Baum und kein Zweig am {@code /werder}</b>: Jener hatte seit Schritt
 * 10 Platz fuer {@code wolke} gelassen, und der Nutzer hat am 18.11.2026 anders
 * entschieden. Der Grund ist die TIEFE: Diese Welt hat fuenf Zweige und bekommt mit den
 * Teilen D bis F weitere (Stufe, Herausforderungen, Biom, Zuruecksetzen). Als Unterbaum
 * hiesse das {@code /werder wolke herausforderung} - drei Worte, bevor etwas geschieht.
 *
 * <p><b>{@code /wolke nehmen} tut drei Dinge auf einmal</b>, und das ist der Unterschied
 * zu jeder anderen Besitzart im Projekt: Es sucht das Feld, setzt die INSEL darauf und
 * bringt den Spieler hin. Keines davon kann entfallen - in einer Welt aus Leere gibt es
 * nichts, worauf man vorher stehen koennte, und ohne Reise stuende der Spieler weiter auf
 * der Ankunftsplattform und wuesste nicht, wohin.
 */
final class WolkeBefehle {

    /**
     * Setzt die Startinsel in die Welt - die Bukkit-Haelfte des Nehmens, als NAHT.
     *
     * <p><b>Warum sie eine Naht ist</b>: Dahinter liegen {@code Weltgriff.AufDerWelt},
     * {@code Truhengriff.AufDerWelt} und ein {@code new ItemStack} - und jenes WIRFT ohne
     * laufenden Server. Baute dieser Befehl die Griffe selbst, waere der ganze Zweig
     * {@code /wolke nehmen} von keiner Probe durchlaufbar, und das ist der Zweig, der
     * Eigentum vergibt. Dieselbe Lage wie beim Stapelbau der {@link Angelwache}.
     *
     * @return ob die Bloecke stehen. {@code false} heisst: Das Feld ist vergeben, aber
     *         leer - dafuer gibt es {@code wolke.aufbau-gestoert}
     */
    interface Aufsetzer {

        boolean setze(Parzelle insel);
    }

    /**
     * Bringt den Spieler an eine Stelle - die REISE, als Naht.
     *
     * <p><b>Warum auch sie eine Naht ist</b> [gemessen am 18.11.2026]: Hinter
     * {@code Reise.fuehre} liegt {@code Ankunftspunkt.taugt}, und jenes fragt
     * {@code Material.isSolid()}. Das greift ueber {@code asBlockType} in Minecrafts
     * Verzeichnis und WIRFT ohne laufenden Server - "No RegistryAccess implementation
     * found". Ein erfolgreiches Reisen ist damit von keiner Probe durchlaufbar, und
     * {@code /wolke nehmen} reist IMMER.
     *
     * <p>Dahinter steht {@code Reise} und nichts anderes: Dort liegen der Rueckweg
     * ({@code /zurueck}), die Aufwaermzeit und die Hindernispruefung. Ein eigener
     * Teleport waere der zweite Weg zu derselben Tat - und der eine ohne Rueckweg.
     */
    interface Heimweg {

        void fuehre(Player spieler, Location ziel);
    }

    /** Der Name des Befehls. */
    static final String ARGUMENT = "wolke";

    /**
     * Die Auskunft - paketprivat, damit die Probe dasselbe Recht nimmt wie das Spiel.
     *
     * <p>Ein Spieler, der in der Probe ein anderes Recht bekommt als im Spiel, laeuft
     * durch einen Zweig, den es nicht gibt.
     */
    static final String RECHT = "freimarken.wolke";

    /**
     * Das Nehmen - ein eigenes Recht, obwohl die Insel gratis ist.
     *
     * <p>Gratis heisst nicht folgenlos: Jedes Nehmen belegt ein Rasterfeld auf Dauer und
     * setzt Bloecke in die Welt. Ein Betreiber, der die Welt erst oeffnen will, wenn sie
     * fertig eingerichtet ist, braucht genau diesen Schalter - und die Obergrenze
     * {@code wolken.hoechstzahl: 0} ist die andere Haelfte davon.
     */
    static final String RECHT_NEHMEN = "freimarken.wolke.nehmen";

    /**
     * Die Vorsilbe der Obergrenze: {@code freimarken.wolken.2} erlaubt zwei Inseln.
     *
     * <p>Dieselbe Bauart wie bei den Parzellen und den Meerinseln, und die Zahl steht in
     * der Rechteverwaltung und nicht im Programm - so bleibt sie aenderbar, wenn die
     * Rangstufen aus Schritt 12 ihre endgueltigen Zahlen bekommen.
     *
     * <p>Die Vorsilbe heisst nach der SACHE und nicht nach dem Befehl
     * ({@code wolken}, nicht {@code wolke}) - wie {@code freimarken.inseln} neben
     * {@code /werder}. Dass sie sich damit um einen Buchstaben vom Befehlsrecht
     * unterscheidet, ist der Preis; die Alternative waere ein Recht, das in der
     * plugin.yml neben seinem eigenen Zahlrecht steht und von Tafel 31al nicht zu
     * trennen ist.
     */
    private static final String RECHT_ANZAHL = "freimarken.wolken.";

    /** Der Zuschlag - eine Mitgliedschaft legt etwas auf den Rang. */
    private static final String RECHT_EXTRA_ANZAHL = "freimarken.extra.wolken.";

    private final Wolkenbuch buch;
    private final Kasse kasse;
    private final Sprache sprache;
    private final Aufsetzer aufsetzer;
    private final Heimweg heimweg;

    /**
     * Die Wolkenwelt - LEER, wenn es sie nicht gibt.
     *
     * <p>Dieselbe Entscheidung wie bei {@link WerderBefehle}: Der Befehl wird auch dann
     * angemeldet, und jeder Zweig sagt es dann. Ein Befehl, den es je nach
     * Einstellungsdatei gibt oder nicht, waere fuer einen Spieler ein Fehler ohne
     * Erklaerung.
     */
    private final String wolkenschluessel;

    /**
     * Die Obergrenze aus der Einstellungsdatei - als Abfrage und nicht als Zahl.
     *
     * <p>Sie steht in der Absage "du hast genug", und ein Neuladen muss sie erreichen.
     * Was ein RANG daraufsetzt, kommt getrennt aus {@link Rangzahl}.
     */
    private final IntSupplier hoechstzahl;

    WolkeBefehle(Wolkenbuch buch, Kasse kasse, Sprache sprache, Aufsetzer aufsetzer,
                 Heimweg heimweg, String wolkenschluessel, IntSupplier hoechstzahl) {
        this.buch = buch;
        this.kasse = kasse;
        this.sprache = sprache;
        this.aufsetzer = aufsetzer;
        this.heimweg = heimweg;
        this.wolkenschluessel = wolkenschluessel;
        this.hoechstzahl = hoechstzahl;
    }

    LiteralCommandNode<CommandSourceStack> wolke() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(this::zeigeListe)
                .then(Commands.literal("liste")
                        .executes(this::zeigeListe))
                .then(Commands.literal("nehmen")
                        .requires(quelle -> quelle.getSender()
                                .hasPermission(RECHT_NEHMEN))
                        .executes(this::nehme))
                .then(Commands.literal("heim")
                        .executes(this::heim))
                // JEDES Literal mit Untersaetzen traegt ein eigenes .executes, und das
                // ist keine Bequemlichkeit: Ein passendes Literal verdeckt in Brigadier
                // jedes Geschwister vollstaendig (CommandNode.getRelevantNodes) - ohne
                // eigenes .executes endete "/wolke mitbauer" in Brigadiers englischem
                // "Unknown or incomplete command". Dieselbe Falle wie bei
                // /werder hafen und /freistadt grundstueck.
                //
                // Und die Liste ist hier die richtige Antwort: In ihr stehen die
                // Mitbauer je Insel.
                .then(Commands.literal("mitbauer")
                        .executes(this::zeigeListe)
                        .then(Commands.literal("dazu")
                                .then(anwesendeNamen()
                                        .executes(z -> mitName(z,
                                                Wolkentext.Richtung.DAZU))))
                        .then(Commands.literal("weg")
                                .then(mitbauerNamen()
                                        .executes(z -> mitName(z,
                                                Wolkentext.Richtung.WEG)))))
                .build();
    }

    /**
     * Der nackte Befehl, {@code liste} und {@code mitbauer} tun DASSELBE.
     *
     * <p>Kein zweiter Weg zu einer zweiten Antwort - dieselbe Entscheidung wie bei
     * {@code /werder}. Die Liste geht ueberall und nicht nur in Wolkenwerder: Wer wissen
     * will, wo seine Insel liegt, steht gerade nicht darauf - er kann es gar nicht, wenn
     * er noch keine hat.
     */
    private int zeigeListe(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (keineWelt(spieler)) {
            return Command.SINGLE_SUCCESS;
        }
        List<Parzelle> meine;
        try {
            meine = buch.inselnVon(wolkenschluessel, spieler.getUniqueId());
        } catch (RuntimeException fehler) {
            sende(spieler, sprache.bau(WolkeSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        sende(spieler, Wolkentext.listenZeilen(sprache, meine, erlaubt(spieler),
                Spielername::von));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Eine Insel nehmen - suchen, aufsetzen, hinreisen.
     *
     * <p>Das Guthaben VOR dem Versuch, denn danach ist es abgebucht: Die Absage "zu wenig
     * Mark" nennt, was fehlt, und die Erfolgszeile, was bleibt - beides rechnet sich nur
     * gegen den Stand von vorher.
     *
     * <p><b>Das Aufsetzen ist der Schritt, der schiefgehen kann</b>, und darum hat er
     * seinen eigenen Satz: Ist die Welt in diesem Augenblick nicht geladen, gehoert dem
     * Spieler ein Feld aus Luft. Die Zeile sagt es ihm - sonst reist er hin und faellt.
     */
    private int nehme(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (keineWelt(spieler)) {
            return Command.SINGLE_SUCCESS;
        }
        int erlaubt = erlaubt(spieler);
        long guthaben = kasse.guthaben(spieler.getUniqueId());

        Wolkenbuch.Entscheid entscheid =
                buch.nehmen(wolkenschluessel, spieler.getUniqueId(), erlaubt);

        sende(spieler, Wolkentext.nehmenZeilen(sprache, entscheid, erlaubt, guthaben));
        if (!entscheid.erteilt()) {
            return Command.SINGLE_SUCCESS;
        }
        Parzelle insel = entscheid.insel().orElseThrow();
        if (!setzeUndFuehre(spieler, insel)) {
            sende(spieler, Wolkentext.aufbauGestoertZeilen(sprache, insel));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Zur eigenen Insel reisen.
     *
     * <p>Die ERSTE seiner Inseln, wenn er mehrere hat - und das ist Absicht: Sie ist die,
     * die er zuerst genommen hat, also die mit dem Haus darauf. Eine Auswahl waere ein
     * Argument, das in neun von zehn Faellen nur eine Moeglichkeit hat; wer die zweite
     * sucht, findet ihr Feld in {@code /wolke}.
     */
    private int heim(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (keineWelt(spieler)) {
            return Command.SINGLE_SUCCESS;
        }
        List<Parzelle> meine;
        try {
            meine = buch.inselnVon(wolkenschluessel, spieler.getUniqueId());
        } catch (RuntimeException fehler) {
            sende(spieler, sprache.bau(WolkeSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        if (meine.isEmpty()) {
            sende(spieler, Wolkentext.keineInselZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        fuehreZurInsel(spieler, meine.get(0));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Einen Mitbauer eintragen oder austragen - auf der Insel, auf der er STEHT.
     *
     * <p>Die drei Absagen, die vor dem Buch liegen, stehen hier: kein Name gefunden, man
     * selbst, keine Welt. Alles andere entscheidet das Buch - auch "gehoert einem
     * anderen", denn dazu muss man die Zeile gelesen haben.
     */
    private int mitName(CommandContext<CommandSourceStack> zusammenhang,
                        Wolkentext.Richtung richtung) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (keineWelt(spieler)) {
            return Command.SINGLE_SUCCESS;
        }
        String name = StringArgumentType.getString(zusammenhang, "spieler");
        Optional<UUID> wen = Spielername.kennung(name);
        if (wen.isEmpty()) {
            sende(spieler, Wolkentext.unbekannterSpielerZeilen(sprache, name));
            return Command.SINGLE_SUCCESS;
        }
        if (wen.get().equals(spieler.getUniqueId())) {
            sende(spieler, Wolkentext.selbstZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        Location ort = spieler.getLocation();
        Wolkenbuch.Mitentscheid entscheid = richtung == Wolkentext.Richtung.DAZU
                ? buch.mitbauerHinzufuegen(wolkenschluessel, spieler.getUniqueId(),
                        ort.getBlockX(), ort.getBlockZ(), wen.get())
                : buch.mitbauerEntfernen(wolkenschluessel, spieler.getUniqueId(),
                        ort.getBlockX(), ort.getBlockZ(), wen.get());

        sende(spieler, Wolkentext.mitbauerZeilen(sprache, entscheid, richtung, name,
                Spielername::von));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Setzt die Insel und bringt den Spieler hin - oder sagt, dass es nicht ging.
     *
     * <p>Das Setzen selbst liegt hinter {@link Aufsetzer}, und die Welt sucht JENER -
     * sie kann beim Start gefehlt haben und spaeter da sein. Ohne sie ist das Feld
     * vergeben, aber leer, und genau dafuer gibt es {@code wolke.aufbau-gestoert}.
     *
     * @return ob die Bloecke stehen
     */
    private boolean setzeUndFuehre(Player spieler, Parzelle insel) {
        if (!aufsetzer.setze(insel)) {
            return false;
        }
        fuehreZurInsel(spieler, insel);
        return true;
    }

    /**
     * Bringt den Spieler auf die Mitte dieser Insel.
     *
     * <p>Ueber {@link Heimweg} und damit ueber {@code Reise} - siehe dort, warum es
     * eine Naht ist.
     */
    private void fuehreZurInsel(Player spieler, Parzelle insel) {
        World welt = weltOderNull();
        if (welt == null) {
            sende(spieler, Wolkentext.keineWeltZeilen(sprache));
            return;
        }
        heimweg.fuehre(spieler, new Location(welt,
                buch.plan().feldMitte(insel.x()) + 0.5, Wolkenaufbau.stehhoehe(),
                buch.plan().feldMitte(insel.z()) + 0.5));
    }

    /**
     * Die Wolkenwelt, oder {@code null}.
     *
     * <p>JE AUFRUF gesucht und nicht im Konstruktor gemerkt: Eine Welt kann beim Start
     * gefehlt haben und spaeter da sein (der Betreiber traegt den Block nach und laedt
     * neu), und ein gemerkter Verweis auf eine entladene Welt waere ein Griff ins Leere.
     * Dieselbe Vorsicht wie bei {@code Chunkmarken}.
     */
    private World weltOderNull() {
        NamespacedKey schluessel = NamespacedKey.fromString(wolkenschluessel);
        return schluessel == null ? null : Bukkit.getWorld(schluessel);
    }

    /** Wie viele Inseln dieser Spieler halten darf - Einstellung plus Rang. */
    private int erlaubt(Player spieler) {
        return Rangzahl.ausRecht(spieler, RECHT_ANZAHL, RECHT_EXTRA_ANZAHL,
                hoechstzahl.getAsInt());
    }

    /** Sagt es, wenn es keine Wolkenwelt gibt - und gibt dann {@code true}. */
    private boolean keineWelt(Player spieler) {
        if (buch == null || wolkenschluessel.isEmpty()) {
            sende(spieler, Wolkentext.keineWeltZeilen(sprache));
            return true;
        }
        return false;
    }

    /** Zum Eintragen: wer gerade da ist. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> anwesendeNamen() {
        return Commands.argument("spieler", StringArgumentType.word())
                .suggests((zusammenhang, bauer) -> {
                    Bukkit.getOnlinePlayers().forEach(wer -> bauer.suggest(wer.getName()));
                    return bauer.buildFuture();
                });
    }

    /**
     * Zum Austragen: wer auf DIESER Insel eingetragen ist.
     *
     * <p>Ohne Meldung und ohne Weltpruefung: Ein Vorschlagsgeber laeuft bei jedem
     * Tastendruck. Eine Absage im Chat waere dort kein Hinweis, sondern Laerm.
     */
    private RequiredArgumentBuilder<CommandSourceStack, String> mitbauerNamen() {
        return Commands.argument("spieler", StringArgumentType.word())
                .suggests((zusammenhang, bauer) -> {
                    if (buch != null && !wolkenschluessel.isEmpty()
                            && zusammenhang.getSource().getExecutor()
                                    instanceof Player spieler) {
                        Location ort = spieler.getLocation();
                        buch.inselAn(wolkenschluessel, ort.getBlockX(), ort.getBlockZ())
                                .ifPresent(insel -> insel.mitbauer().stream()
                                        .map(Spielername::von)
                                        .sorted()
                                        .forEach(bauer::suggest));
                    }
                    return bauer.buildFuture();
                });
    }

    private static Player spielerAus(CommandContext<CommandSourceStack> zusammenhang) {
        // Spielerpflichtig wie /werder: Die Zeilen sind fuer ein Chatfenster gemacht,
        // und genommen wird fuer jemanden, der irgendwohin gebracht werden kann.
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
