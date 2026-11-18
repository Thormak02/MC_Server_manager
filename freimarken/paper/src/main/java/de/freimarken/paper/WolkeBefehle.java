package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Aufgabenbuch;
import de.freimarken.kern.Biombuch;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Punktebuch;
import de.freimarken.kern.Wolkenbuch;
import de.freimarken.kern.Zaehlauftrag;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
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

    /**
     * Die Hand, die eine Insel ZAEHLT - als Naht.
     *
     * <p>Dahinter liegt {@link Inselzaehlung} und darin ein Taktgeber und Minecrafts
     * Chunks. Der Befehl kennt davon nur zwei Dinge: Er gibt den Auftrag hinein und
     * bekommt am Ende eine Zahl - und genau deshalb ist sein Zweig von einer Probe
     * durchlaufbar.
     */
    interface Zaehlhand {

        void zaehle(Zaehlauftrag auftrag, LongConsumer fertig);
    }

    /**
     * Die Hand, die das BIOM eines Feldes umsetzt - als Naht.
     *
     * <p>Dahinter liegt {@link Biomhand} mit demselben Taktgeber. Dass der Befehl davon
     * nichts weiss, ist dieselbe Entscheidung wie beim Aufsetzen.
     */
    interface Biomwechsel {

        void setze(Parzelle insel, String biom, Runnable fertig);
    }

    /**
     * Die Buecher und Haende der Teile E und F - EIN Buendel statt vier Parameter.
     *
     * <p>Ein Record und keine vier Felder, und das ist mehr als Bequemlichkeit: Die vier
     * entstehen zusammen oder gar nicht (siehe {@code richteWolkenEin}), und ein Buendel
     * macht genau das sichtbar. Ein {@code null} heisst: In diesem Lauf gibt es keine
     * Punkte und keine Biomwahl - {@link #keinePflege} sagt es dann.
     */
    record Pflege(Punktebuch punkte, Zaehlhand zaehlhand, Biombuch biome,
                  Biomwechsel wechsel) {

        Pflege {
            if (punkte == null || zaehlhand == null || biome == null
                    || wechsel == null) {
                throw new IllegalArgumentException("Eine halbe Pflege gibt es nicht:"
                        + " Punktebuch, Zaehlhand, Biombuch und Biomwechsel gehoeren"
                        + " zusammen");
            }
        }
    }

    /** Der Name des Befehls. */
    static final String ARGUMENT = "wolke";

    /**
     * Wie viele Plaetze die Rangliste zeigt.
     *
     * <p>Zehn, und das ist eine Zahl fuer ein Chatfenster: Eine Welt hat bis zu 1.443
     * Inseln, und eine Liste darueber waere kein Ansporn, sondern ein Wall.
     */
    private static final int RANGLISTE_PLAETZE = 10;

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

    /**
     * Die Regel ueber die Herausforderungen - {@code null}, solange es die Welt nicht
     * gibt.
     *
     * <p>Darf null sein, und darum fragt {@link #keineAufgaben} danach: Das Buch entsteht
     * in {@code richteWolkenEin}, der Befehl wird aber IMMER angemeldet - ein Befehl, den
     * es je nach Einstellungsdatei gibt oder nicht, waere fuer einen Spieler ein Fehler
     * ohne Erklaerung.
     */
    private final Aufgabenbuch aufgabenbuch;

    /**
     * Was ein Spieler traegt - die Naht zu {@link Vorratshand}.
     *
     * <p>Je Aufruf gefragt und nicht gemerkt: Der Vorrat ist der Zustand eines
     * Inventars, und der aendert sich zwischen zwei Befehlen.
     */
    private final Function<Player, Aufgabenbuch.Vorrat> vorrat;

    /** Punkte und Biome - {@code null}, wenn es sie in diesem Lauf nicht gibt. */
    private final Pflege pflege;

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

    WolkeBefehle(Wolkenbuch buch, Aufgabenbuch aufgabenbuch,
                 Function<Player, Aufgabenbuch.Vorrat> vorrat, Pflege pflege,
                 Kasse kasse, Sprache sprache, Aufsetzer aufsetzer, Heimweg heimweg,
                 String wolkenschluessel, IntSupplier hoechstzahl) {
        this.buch = buch;
        this.aufgabenbuch = aufgabenbuch;
        this.vorrat = vorrat;
        this.pflege = pflege;
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
                .then(Commands.literal("aufgaben")
                        .executes(this::zeigeAufgaben))
                // Auch hier ein eigenes .executes am Literal: "/wolke abgeben" ohne Wort
                // endete sonst in Brigadiers englischem "Unknown or incomplete command".
                // Und die Tafel ist die richtige Antwort darauf - dort stehen die Namen,
                // die als naechstes Wort gefragt sind.
                .then(Commands.literal("abgeben")
                        .executes(this::zeigeAufgaben)
                        .then(aufgabenNamen()
                                .executes(this::abgeben)))
                .then(Commands.literal("punkte")
                        .executes(this::zaehlePunkte))
                .then(Commands.literal("rangliste")
                        .executes(this::zeigeRangliste))
                // Wieder ein eigenes .executes am Literal: "/wolke biom" ohne Namen gibt
                // die AUSKUNFT - welches Biom die Insel hat, was ein Wechsel kostet und
                // was zur Wahl steht. Ohne das waere es Brigadiers englischer Satz.
                .then(Commands.literal("biom")
                        .executes(this::zeigeBiom)
                        .then(biomnamen()
                                .executes(this::setzeBiom)))
                // UND die Rueckfrage als zweites Wort: Zurueckgesetzt wird die
                // Startinsel, und was an ihrer Stelle steht, ist danach weg. Dieselbe
                // Bauart wie beim Aufgeben einer Parzelle.
                .then(Commands.literal("zuruecksetzen")
                        .executes(z -> setzeZurueck(z, false))
                        .then(Commands.literal("bestaetigen")
                                .executes(z -> setzeZurueck(z, true))))
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
     * Die Tafel der Herausforderungen - und das Abgeben.
     *
     * <p>Zwei Zweige mit DEMSELBEN Recht wie die Liste: Wer die Welt betreten darf, soll
     * sehen, was dort zu tun ist. Ein eigenes Recht waere ein Schalter, der eine Insel
     * ohne Fortschritt hinterliesse - und die Insel hat ihn dann doch, nur sieht niemand
     * mehr, wie er zustande kam.
     */
    private int zeigeAufgaben(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (keineWelt(spieler) || keineAufgaben(spieler)) {
            return Command.SINGLE_SUCCESS;
        }
        Location ort = spieler.getLocation();
        sende(spieler, Wolkentext.aufgabenZeilen(sprache,
                aufgabenbuch.tafel(wolkenschluessel, ort.getBlockX(), ort.getBlockZ()),
                this::aufgabenwort));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Eine Herausforderung abgeben - auf der Insel, auf der er STEHT.
     *
     * <p>Die INSEL wird vor dem Buch geholt, und nur fuer die Absage "gehoert einem
     * anderen": Sie nennt den Eigentuemer, und dazu muss die Zeile gelesen sein. Die
     * ENTSCHEIDUNG trifft das Buch - diese Abfrage daneben waere die zweite Meinung
     * darueber, wo eine Insel liegt.
     *
     * <p>Der Vorrat kommt aus {@link #vorrat} und damit aus {@link Vorratshand}: Hinter
     * jenem liegen {@code getStorageContents} und {@code new ItemStack}, und letzteres
     * wirft ohne laufenden Server. Dieselbe Naht wie beim Aufsetzen.
     */
    private int abgeben(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (keineWelt(spieler) || keineAufgaben(spieler)) {
            return Command.SINGLE_SUCCESS;
        }
        String getippt = StringArgumentType.getString(zusammenhang, "aufgabe");
        Location ort = spieler.getLocation();
        Parzelle hier;
        try {
            hier = buch.inselAn(wolkenschluessel, ort.getBlockX(), ort.getBlockZ())
                    .orElse(null);
        } catch (RuntimeException fehler) {
            sende(spieler, sprache.bau(WolkeSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }

        Aufgabenbuch.Entscheid entscheid = aufgabenbuch.abgeben(wolkenschluessel,
                spieler.getUniqueId(), ort.getBlockX(), ort.getBlockZ(), getippt,
                vorrat.apply(spieler));

        sende(spieler, Wolkentext.abgebenZeilen(sprache, entscheid, getippt,
                this::aufgabenwort, Spielername::von, hier));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Sagt es, wenn es in dieser Welt keine Herausforderungen gibt.
     *
     * <p>Der Riegel steht hier und nicht erst im Buch, weil das Buch selbst fehlen kann:
     * Es entsteht in {@code richteWolkenEin}, und ohne Welt gibt es keines. Ein Griff ins
     * Leere waere eine Ausnahme im Chat eines Spielers.
     */
    private boolean keineAufgaben(Player spieler) {
        if (aufgabenbuch == null) {
            sende(spieler, sprache.bau(WolkeSatz.AUFGABEN_AUS, 1L, Einsatz.keine()));
            return true;
        }
        return false;
    }

    /**
     * Das Wort zu einem Aufgabenschluessel - aus der SPRACHDATEI.
     *
     * <p>Wie beim Berufsnamen: Was es gibt, steht in der {@code config.yml}, wie es
     * heisst, in {@code sprache.yml}. Fehlt die Zeile, antwortet {@link Sprache} mit dem
     * Pfad - und der Start hat es schon gemeldet.
     */
    private String aufgabenwort(String schluessel) {
        return sprache.wort(new Aufgabenname(schluessel), Einsatz.keine());
    }

    /**
     * Zum Abgeben: die OFFENEN Aufgaben dieser Insel.
     *
     * <p>Nur die offenen, und das ist der ganze Zweck: Eine Liste mit allen zehn liesse
     * einen Spieler eine abgeben, die schon steht - und die Antwort darauf waere eine
     * Absage. Steht er nicht auf einer Insel, kommen alle: Dann ist der Vorschlag die
     * einzige Stelle, an der er die Namen ueberhaupt lesen kann.
     *
     * <p>Ohne Meldung und ohne Weltpruefung, wie {@link #mitbauerNamen}: Ein
     * Vorschlagsgeber laeuft bei jedem Tastendruck.
     */
    private RequiredArgumentBuilder<CommandSourceStack, String> aufgabenNamen() {
        return Commands.argument("aufgabe", StringArgumentType.word())
                .suggests((zusammenhang, bauer) -> {
                    if (aufgabenbuch != null && !wolkenschluessel.isEmpty()
                            && zusammenhang.getSource().getExecutor()
                                    instanceof Player spieler) {
                        Location ort = spieler.getLocation();
                        Aufgabenbuch.Tafel tafel = aufgabenbuch.tafel(wolkenschluessel,
                                ort.getBlockX(), ort.getBlockZ());
                        for (Aufgabenbuch.Zeile zeile : tafel.zeilen()) {
                            if (!zeile.erfuellt()) {
                                bauer.suggest(zeile.aufgabe().schluessel());
                            }
                        }
                        if (tafel.zeilen().isEmpty()) {
                            aufgabenbuch.werte().schluessel().forEach(bauer::suggest);
                        }
                    }
                    return bauer.buildFuture();
                });
    }

    /**
     * Die Punktzahl zaehlen lassen - sie laeuft in Happen und meldet sich spaeter.
     *
     * <p>Die erste Zeile kommt sofort ("die Zaehlung laeuft"), die zweite, wenn sie durch
     * ist. Ohne die erste haelt ein Spieler den Befehl fuer wirkungslos und tippt ihn
     * wieder - und der zweite Versuch laeuft in "hier zaehlt schon jemand".
     */
    private int zaehlePunkte(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (keineWelt(spieler) || keinePflege(spieler, WolkeSatz.PUNKTE_AUS)) {
            return Command.SINGLE_SUCCESS;
        }
        Location ort = spieler.getLocation();
        Punktebuch.Entscheid entscheid = pflege.punkte().beginne(wolkenschluessel,
                ort.getBlockX(), ort.getBlockZ());

        sende(spieler, Wolkentext.punkteZeilen(sprache, entscheid, Spielername::von));
        entscheid.auftrag().ifPresent(auftrag -> pflege.zaehlhand().zaehle(auftrag,
                punkte -> sende(spieler,
                        Wolkentext.punkteFertigZeilen(sprache, punkte))));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die Rangliste - die Inseln mit den meisten Punkten.
     *
     * <p>Sie geht ueberall und nicht nur in Wolkenwerder: Wer wissen will, wie er steht,
     * steht gerade irgendwo. Und sie nennt die STUFE neben den Punkten - das ist die
     * andere Haelfte des Fortschritts.
     */
    private int zeigeRangliste(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (keineWelt(spieler) || keinePflege(spieler, WolkeSatz.PUNKTE_AUS)) {
            return Command.SINGLE_SUCCESS;
        }
        List<Punktebuch.Platz> plaetze;
        try {
            plaetze = pflege.punkte().rangliste(wolkenschluessel, RANGLISTE_PLAETZE);
        } catch (RuntimeException fehler) {
            sende(spieler, sprache.bau(WolkeSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        sende(spieler, Wolkentext.ranglistenZeilen(sprache, plaetze, Spielername::von));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Das Biom der Insel, auf der er steht - ohne Namen nur die Auskunft.
     *
     * <p>Zwei Zweige mit DEMSELBEN Literal, und das ist dieselbe Brigadier-Falle wie bei
     * {@code mitbauer}: Ohne eigenes {@code .executes} endete "/wolke biom" in "Unknown or
     * incomplete command".
     */
    private int zeigeBiom(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (keineWelt(spieler) || keinePflege(spieler, WolkeSatz.BIOM_KEINE_WAHL)) {
            return Command.SINGLE_SUCCESS;
        }
        Location ort = spieler.getLocation();
        try {
            Optional<Parzelle> hier = buch.inselAn(wolkenschluessel, ort.getBlockX(),
                    ort.getBlockZ());
            if (hier.isEmpty()) {
                sende(spieler, Wolkentext.keineInselZeilen(sprache));
                return Command.SINGLE_SUCCESS;
            }
            sende(spieler, Wolkentext.biomStandZeilen(sprache,
                    pflege.biome().biomVon(hier.get().id()), pflege.biome().preis(),
                    pflege.biome().angebot()));
        } catch (RuntimeException fehler) {
            sende(spieler, sprache.bau(WolkeSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine()));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Das Biom wechseln - es KOSTET, und zwar jedes Mal.
     *
     * <p>Auch auf dasselbe Biom, und das ist kein Versehen: Hinter dem Wechsel steht das
     * Umsetzen der Biomzellen eines ganzen Feldes, und das ist Arbeit, die der Server
     * jedes Mal leistet. Die Begruendung steht ausgeschrieben in {@link Biombuch}.
     *
     * <p>Das Guthaben VOR dem Versuch, denn danach ist abgebucht - dieselbe Rechnung wie
     * beim Nehmen.
     */
    private int setzeBiom(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (keineWelt(spieler) || keinePflege(spieler, WolkeSatz.BIOM_KEINE_WAHL)) {
            return Command.SINGLE_SUCCESS;
        }
        String gewuenscht = StringArgumentType.getString(zusammenhang, "biom");
        Location ort = spieler.getLocation();
        long guthaben = kasse.guthaben(spieler.getUniqueId());

        Biombuch.Entscheid entscheid = pflege.biome().setze(wolkenschluessel,
                spieler.getUniqueId(), ort.getBlockX(), ort.getBlockZ(), gewuenscht);

        sende(spieler, Wolkentext.biomZeilen(sprache, entscheid, guthaben,
                pflege.biome().angebot(), Spielername::von));
        if (entscheid.erteilt()) {
            // Die BLOECKE danach, und ohne weitere Zeile: Das Umsetzen laeuft in Happen
            // und ist Sache des Servers. Was ein Spieler wissen muss - dass er die Chunks
            // neu betreten muss -, steht im Erfolgssatz.
            pflege.wechsel().setze(entscheid.insel().orElseThrow(), entscheid.biom(),
                    null);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Die Startinsel wiederherstellen - mit Rueckfrage.
     *
     * <p>Zurueckgesetzt wird nur der Quader der Startinsel, nicht das ganze Feld: Was ein
     * Spieler daneben gebaut hat, bleibt stehen. Das ist genau der Griff, den
     * {@code wolke.aufbau-gestoert} nennt - und der zweite Zweck ist der haeufigere: eine
     * neue Starttruhe nach einem Fehlgriff mit der Lava.
     *
     * <p><b>Der Fortschritt bleibt.</b> Die erfuellten Herausforderungen und die Punkte
     * gehoeren der Insel, und die Gegenstaende dafuer sind weg - sie zu loeschen waere
     * eine Strafe fuer einen Griff, den das Spiel selbst anbietet.
     */
    private int setzeZurueck(CommandContext<CommandSourceStack> zusammenhang,
                             boolean bestaetigt) {
        Player spieler = spielerAus(zusammenhang);
        if (spieler == null) {
            return 0;
        }
        if (keineWelt(spieler)) {
            return Command.SINGLE_SUCCESS;
        }
        Location ort = spieler.getLocation();
        Optional<Parzelle> hier;
        try {
            hier = buch.inselAn(wolkenschluessel, ort.getBlockX(), ort.getBlockZ());
        } catch (RuntimeException fehler) {
            sende(spieler, sprache.bau(WolkeSatz.ABSAGE_GESTOERT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        if (hier.isEmpty()) {
            sende(spieler, Wolkentext.keineInselZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        Parzelle insel = hier.get();
        if (!insel.gehoert(spieler.getUniqueId())) {
            // NUR der Eigentuemer, wie beim Biom: Ein Mitbauer wuerde sonst die
            // Startinsel eines anderen ueberschreiben.
            sende(spieler, sprache.bau(WolkeSatz.BIOM_NICHT_DEINE, 1L,
                    Einsatz.von("eigentuemer",
                            Spielername.von(insel.eigentuemer()))));
            return Command.SINGLE_SUCCESS;
        }
        if (!bestaetigt) {
            sende(spieler, Wolkentext.zuruecksetzenWarnungZeilen(sprache));
            return Command.SINGLE_SUCCESS;
        }
        if (!aufsetzer.setze(insel)) {
            sende(spieler, Wolkentext.aufbauGestoertZeilen(sprache, insel));
            return Command.SINGLE_SUCCESS;
        }
        sende(spieler, Wolkentext.zuruecksetzenZeilen(sprache, insel));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Sagt es, wenn es die Pflege (Punkte, Biome) in diesem Lauf nicht gibt.
     *
     * <p>Sie fehlt genau dann, wenn die Welt beim Start fehlte oder die Inselstaende nicht
     * zu lesen waren - und im zweiten Fall ist das die sichere Richtung: Eine Rangliste
     * aus einem ungefuellten Zwischenspeicher zeigte jede Insel bei 0 Punkten.
     *
     * @param satz welcher Satz es sagt - fuer die Punkte und das Biom ein anderer
     */
    private boolean keinePflege(Player spieler, WolkeSatz satz) {
        if (pflege == null) {
            sende(spieler, sprache.bau(satz, 1L, Einsatz.keine()));
            return true;
        }
        return false;
    }

    /**
     * Zum Wechseln: die Biome, die diese Welt anbietet.
     *
     * <p>Ohne Meldung und ohne Weltpruefung, wie {@link #mitbauerNamen}: Ein
     * Vorschlagsgeber laeuft bei jedem Tastendruck.
     */
    private RequiredArgumentBuilder<CommandSourceStack, String> biomnamen() {
        return Commands.argument("biom", StringArgumentType.word())
                .suggests((zusammenhang, bauer) -> {
                    if (pflege != null) {
                        pflege.biome().angebot().forEach(bauer::suggest);
                    }
                    return bauer.buildFuture();
                });
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
