package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Rasterplan;
import de.freimarken.kern.Richtung;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * Die Befehle rund um Parzellen: nehmen, ansehen, Mitbauer, aufgeben.
 *
 * <p>Die Befehle entscheiden nichts. Sie lesen ab, wo der Spieler steht, fragen das
 * {@link Besitzbuch} und tragen die Antwort vor. Jede Regel - Preis, Obergrenze, wer was
 * darf - liegt im Kern und ist dort ohne Server geprueft.
 */
final class ParzellenBefehle {

    /** Mit diesem Recht und einer Zahl dahinter steigt die Obergrenze: {@code .4}, {@code .16}. */
    private static final String RECHT_ANZAHL = "freimarken.parzellen.";

    /** Dasselbe fuer die Felderzahl einer Gruppe: {@code freimarken.gruppe.4}. */
    private static final String RECHT_GRUPPE = "freimarken.gruppe.";

    private final Besitzbuch buch;

    /** Die Welt, in der Bloecke stehen - fuer das Fuellen und Zurueckbauen des Weges. */
    private final World marktstadt;

    /** Die Welt als Schluesseltext - aus demselben Grund wie in {@link Schutzwache}. */
    private final String welt;

    private final Wegbau wegbau;
    private final Logger protokoll;
    private final int standardHoechstzahl;
    private final int standardGruppenFelder;

    /**
     * Die eine Zeile, die angehaengt wird, wenn ein Rang die Grenze setzt - leer heisst:
     * keine.
     *
     * <p>Aus der Konfiguration und nicht fest im Programm: Was ein Spieler tun muss, um
     * mehr zu duerfen, weiss nur der Server. Ein eingebauter Hinweis auf einen Shop, den
     * es dort nicht gibt, waere schlechter als gar keiner.
     */
    private final String rangWerbung;

    ParzellenBefehle(Besitzbuch buch, World marktstadt, int standardHoechstzahl,
                     int standardGruppenFelder, String rangWerbung, Logger protokoll) {
        this.buch = buch;
        this.marktstadt = marktstadt;
        // Aus der Welt gelesen und nicht daneben uebergeben: Zwei Angaben fuer dieselbe
        // Welt koennen auseinanderlaufen, und der Fehler faellt erst auf, wenn ein Befehl
        // in der Ablage nichts findet, obwohl der Spieler richtig steht.
        this.welt = marktstadt.key().asString();
        this.wegbau = new Wegbau(buch.plan(), new Weltgriff.AufDerWelt(marktstadt));
        this.standardHoechstzahl = standardHoechstzahl;
        this.standardGruppenFelder = standardGruppenFelder;
        this.rangWerbung = rangWerbung == null ? "" : rangWerbung.trim();
        this.protokoll = protokoll;
    }

    // --- Anmeldung -------------------------------------------------------------

    LiteralCommandNode<CommandSourceStack> parzelle() {
        return Commands.literal("parzelle")
                .requires(quelle -> quelle.getExecutor() instanceof Player)
                .executes(fuerSpieler(this::zeigeInfo))
                .then(Commands.literal("info").executes(fuerSpieler(this::zeigeInfo)))
                .then(Commands.literal("nehmen").executes(fuerSpieler(this::nehmen)))
                .then(Commands.literal("liste").executes(fuerSpieler(this::zeigeListe)))
                .then(Commands.literal("mitbauer")
                        .executes(fuerSpieler(this::zeigeMitbauer))
                        .then(Commands.literal("hinzu")
                                .then(anwesendeNamen()
                                        .executes(z -> mitOrtUndName(z, this::mitbauerHinzu))))
                        // Eigener Vorschlagsgeber: Zum Austragen sind die Namen auf der
                        // Parzelle gefragt, nicht die aller Anwesenden.
                        .then(Commands.literal("weg")
                                .then(mitbauerNamen()
                                        .executes(z -> mitOrtUndName(z, this::mitbauerWeg)))))
                .then(Commands.literal("aufgeben")
                        .executes(fuerSpieler(this::warneVorAufgeben))
                        // Das zweite Wort ist die Sicherung. Aufgeben zahlt nichts zurueck,
                        // und ein Vertipper darf keine bezahlte Parzelle kosten.
                        //
                        // Die Nummer gehoert mit in den Befehl: Ohne sie gab "bestaetigen"
                        // die Parzelle auf, auf der der Spieler JETZT steht - ein Schritt
                        // ueber den Weg zwischen Warnung und Bestaetigung kostete die
                        // falsche, und zurueck gibt es nichts. Die Warnung schreibt die
                        // Nummer vor, und hier muss sie wieder auftauchen.
                        .then(Commands.literal("bestaetigen")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z",
                                                        IntegerArgumentType.integer())
                                                .executes(this::aufgeben)))))
                .then(zusammenlegenZweig())
                .then(Commands.literal("auftrennen")
                        // Ohne weiteres Wort loest sich das Feld heraus, auf dem der
                        // Spieler steht. Das ist der haeufige Fall: Wer vier Felder
                        // verbunden hat und eines abgeben will, will nicht alle Wege
                        // dazwischen verlieren und dreimal neu zusammenlegen.
                        .executes(fuerSpieler(this::warneVorHerausloesen))
                        // Zweites Wort und Nummer wie beim Aufgeben: Auftrennen raeumt den
                        // Weg, und was dort stand, ist danach weg.
                        .then(Commands.literal("bestaetigen")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z",
                                                        IntegerArgumentType.integer())
                                                .executes(this::herausloesen))))
                        .then(Commands.literal("alles")
                                .executes(fuerSpieler(this::warneVorAuftrennen))
                                .then(Commands.literal("bestaetigen")
                                        .then(Commands.argument("x",
                                                        IntegerArgumentType.integer())
                                                .then(Commands.argument("z",
                                                                IntegerArgumentType.integer())
                                                        .executes(this::auftrennen))))))
                .build();
    }

    LiteralCommandNode<CommandSourceStack> mark() {
        return Commands.literal("mark")
                .requires(quelle -> quelle.getExecutor() instanceof Player)
                .executes(fuerSpieler(spieler -> {
                    spieler.sendMessage(Component.text("Du hast ", NamedTextColor.GRAY)
                            .append(Component.text(
                                    Betraege.mark(buch.kasse().guthaben(spieler.getUniqueId())),
                                    NamedTextColor.GOLD))
                            .append(Component.text(".", NamedTextColor.GRAY)));
                }))
                .build();
    }

    // --- Die einzelnen Befehle -------------------------------------------------

    private void zeigeInfo(Player spieler) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        Rasterplan plan = buch.plan();
        int x = ort.getBlockX();
        int z = ort.getBlockZ();
        Rasterplan.Feld feld = plan.feldBei(x, z);

        if (feld != Rasterplan.Feld.PARZELLE) {
            // Erst nachsehen, ob hier eine Gruppe liegt: Nach einem Zusammenlegen ist
            // diese Stelle Bauland, auch wenn der Rasterplan weiter "Weg" sagt. Ohne
            // diesen Zweig hiesse es hier "gehoert allen", waehrend die Schutzwache
            // nebenan fremde Haende abweist - zwei Antworten auf dieselbe Frage.
            Optional<Parzelle> gruppenboden = buch.zustaendigerBesitz(welt, x, z);
            if (gruppenboden.isPresent()) {
                spieler.sendMessage(Component.text("Gruppenboden - war einmal Weg",
                        NamedTextColor.GOLD));
                spieler.sendMessage(Component.text("  Eigentuemer: ", NamedTextColor.GRAY)
                        .append(Component.text(namen(gruppenboden.get().eigentuemer()),
                                NamedTextColor.WHITE)));
                return;
            }
            spieler.sendMessage(Component.text(
                    feld == Rasterplan.Feld.WEG
                            ? "Du stehst auf einem Weg - der gehoert allen."
                            : "Du stehst auf einer Mauer - die gehoert zur Strasse.",
                    NamedTextColor.GRAY));
            return;
        }

        int px = plan.parzelleX(x);
        int pz = plan.parzelleZ(z);
        spieler.sendMessage(Component.text("Parzelle " + px + "/" + pz, NamedTextColor.GOLD));

        Optional<Parzelle> gefunden = buch.parzelleAn(welt, x, z);
        if (gefunden.isEmpty()) {
            if (!buch.grenze().enthaeltParzelle(px, pz)) {
                spieler.sendMessage(Component.text(
                        "  ausserhalb der Marktstadt - wird nicht vergeben",
                        NamedTextColor.GRAY));
                return;
            }
            long preis = Preisstaffel.preisFuerNaechste(anzahl(spieler));
            boolean amLimit = anzahl(spieler) >= hoechstzahl(spieler);
            // Der Preis bleibt stehen, die Einladung nicht. Wer vier von vier Parzellen
            // hat, soll ruhig sehen, was die fuenfte kosten WUERDE - aber "/parzelle
            // nehmen" darunter ist eine Aufforderung, die an derselben Stelle mit einer
            // Absage endet. Genau das ist der Unterschied zwischen Werbung und Irrefuehrung.
            spieler.sendMessage(Component.text("  frei · ", NamedTextColor.GRAY)
                    .append(Component.text(preis == 0L ? "gratis" : Betraege.mark(preis),
                            amLimit ? NamedTextColor.GRAY : NamedTextColor.GREEN))
                    .append(amLimit
                            ? Component.empty()
                            : Component.text(" · /parzelle nehmen", NamedTextColor.DARK_GRAY)));
            if (amLimit) {
                spieler.sendMessage(Component.text("  Dein Rang erlaubt "
                        + hoechstzahl(spieler)
                        + (hoechstzahl(spieler) == 1 ? " Parzelle" : " Parzellen")
                        + ", und so viele hast du schon.", NamedTextColor.RED));
                werbung(spieler);
            }
            return;
        }

        Parzelle parzelle = gefunden.get();
        spieler.sendMessage(Component.text("  Eigentuemer: ", NamedTextColor.GRAY)
                .append(Component.text(namen(parzelle.eigentuemer()), NamedTextColor.WHITE)));
        spieler.sendMessage(Component.text("  Gekauft fuer: ", NamedTextColor.GRAY)
                .append(Component.text(parzelle.preis() == 0L
                        ? "gratis (die erste)" : Betraege.mark(parzelle.preis()),
                        NamedTextColor.WHITE)));
        if (!parzelle.mitbauer().isEmpty()) {
            spieler.sendMessage(Component.text("  Mitbauer: ", NamedTextColor.GRAY)
                    .append(Component.text(namenListe(parzelle.mitbauer()), NamedTextColor.WHITE)));
        }
        boolean meine = parzelle.gehoert(spieler.getUniqueId());
        if (parzelle.hatElternteil()) {
            spieler.sendMessage(Component.text("  Teil einer Gruppe aus "
                    + buch.gruppenFelder(welt, x, z).size() + " Feldern",
                    NamedTextColor.WHITE));
            if (meine) {
                spieler.sendMessage(Component.text(
                        "  /parzelle auftrennen        dieses Feld herausloesen",
                        NamedTextColor.DARK_GRAY));
            }
        } else if (meine) {
            spieler.sendMessage(Component.text(
                    "  /parzelle zusammenlegen <nord|ost|sued|west>", NamedTextColor.DARK_GRAY));
        }
    }

    private void nehmen(Player spieler) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        int hoechstzahl = hoechstzahl(spieler);
        var entscheid = buch.nehmen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ(), hoechstzahl);

        switch (entscheid.bescheid()) {
            case ERTEILT -> {
                Parzelle neu = entscheid.parzelle().orElseThrow();
                spieler.sendMessage(Component.text("Parzelle " + neu.x() + "/" + neu.z()
                        + " gehoert jetzt dir", NamedTextColor.GREEN));
                spieler.sendMessage(Component.text(entscheid.kosten() == 0L
                        ? "  Die erste ist gratis."
                        : "  Bezahlt: " + Betraege.mark(entscheid.kosten())
                                + " · Rest: "
                                + Betraege.mark(buch.kasse().guthaben(spieler.getUniqueId())),
                        NamedTextColor.GRAY));
            }
            case KEINE_PARZELLE -> spieler.sendMessage(Component.text(
                    "Hier ist kein Bauland - stell dich auf eine Parzelle.",
                    NamedTextColor.RED));
            case SCHON_VERGEBEN -> spieler.sendMessage(Component.text(
                            "Die gehoert schon ", NamedTextColor.RED)
                    .append(Component.text(namen(entscheid.parzelle().orElseThrow().eigentuemer()),
                            NamedTextColor.WHITE)));
            case GRENZE_ERREICHT -> {
                spieler.sendMessage(Component.text(
                        "Du hast schon " + hoechstzahl
                                + (hoechstzahl == 1 ? " Parzelle" : " Parzellen")
                                + " - mehr erlaubt dein Rang nicht.", NamedTextColor.RED));
                werbung(spieler);
            }
            case ZU_WENIG_MARK -> spieler.sendMessage(Component.text("Die kostet ",
                            NamedTextColor.RED)
                    .append(Component.text(Betraege.mark(entscheid.kosten()), NamedTextColor.GOLD))
                    .append(Component.text(", du hast "
                            + Betraege.mark(buch.kasse().guthaben(spieler.getUniqueId())) + ".",
                            NamedTextColor.RED)));
            case AUSSERHALB -> spieler.sendMessage(Component.text(
                    "Hier endet die Marktstadt. Parzellen gibt es von "
                            + buch.grenze().von() + "/" + buch.grenze().von() + " bis "
                            + buch.grenze().bis() + "/" + buch.grenze().bis() + ".",
                    NamedTextColor.RED));
            default -> spieler.sendMessage(Component.text(
                    "Das ging nicht: " + entscheid.bescheid(), NamedTextColor.RED));
        }
    }

    private void zeigeListe(Player spieler) {
        List<Parzelle> meine = buch.parzellenVon(welt, spieler.getUniqueId());
        if (meine.isEmpty()) {
            spieler.sendMessage(Component.text(
                    "Du hast noch keine Parzelle. Die erste ist gratis - /parzelle nehmen",
                    NamedTextColor.YELLOW));
            return;
        }
        int hoechstzahl = hoechstzahl(spieler);
        spieler.sendMessage(Component.text("Deine Parzellen (" + meine.size() + " von "
                + hoechstzahl + ")", NamedTextColor.GOLD));
        for (Parzelle parzelle : meine) {
            spieler.sendMessage(Component.text("  " + parzelle.x() + "/" + parzelle.z()
                    + " · " + (parzelle.preis() == 0L ? "gratis"
                            : Betraege.mark(parzelle.preis()))
                    + (parzelle.mitbauer().isEmpty() ? ""
                            : " · " + parzelle.mitbauer().size() + " Mitbauer")
                    // Sonst saehe eine Gruppe aus zwei Feldern in der Liste genauso aus
                    // wie zwei einzelne Parzellen - und der Spieler wuesste nicht, warum
                    // /parzelle aufgeben bei ihnen nicht geht.
                    + (parzelle.hatElternteil() ? " · Gruppe" : ""),
                    NamedTextColor.GRAY));
        }
        if (meine.size() < hoechstzahl) {
            spieler.sendMessage(Component.text("Die naechste kostet "
                    + Betraege.mark(Preisstaffel.preisFuerNaechste(meine.size())),
                    NamedTextColor.DARK_GRAY));
        }
    }

    private void zeigeMitbauer(Player spieler) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        Optional<Parzelle> gefunden = buch.zustaendigerBesitz(welt, ort.getBlockX(),
                ort.getBlockZ());
        if (gefunden.isEmpty()) {
            spieler.sendMessage(Component.text("Hier ist keine vergebene Parzelle.",
                    NamedTextColor.RED));
            return;
        }
        Parzelle parzelle = gefunden.get();
        spieler.sendMessage(Component.text("Mitbauer auf " + parzelle.x() + "/" + parzelle.z()
                + ": ", NamedTextColor.GOLD)
                .append(Component.text(parzelle.mitbauer().isEmpty()
                        ? "niemand" : namenListe(parzelle.mitbauer()), NamedTextColor.WHITE)));
        if (parzelle.gehoert(spieler.getUniqueId())) {
            spieler.sendMessage(Component.text(
                    "/parzelle mitbauer hinzu <name> · weg <name>", NamedTextColor.DARK_GRAY));
        }
    }

    private void mitbauerHinzu(Player spieler, Location ort, String name) {
        Optional<UUID> betroffener = findeSpieler(name);
        if (betroffener.isEmpty()) {
            spieler.sendMessage(Component.text(
                    "Den kenne ich nicht. Er muss einmal auf dem Server gewesen sein.",
                    NamedTextColor.RED));
            return;
        }
        var entscheid = buch.mitbauerHinzufuegen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ(), betroffener.get());
        switch (entscheid.bescheid()) {
            case ERTEILT -> spieler.sendMessage(Component.text(name + " darf hier jetzt bauen.",
                    NamedTextColor.GREEN));
            case SCHON_EINGETRAGEN -> spieler.sendMessage(Component.text(
                    name + " darf hier ohnehin bauen.", NamedTextColor.YELLOW));
            default -> absage(spieler, entscheid.bescheid());
        }
    }

    /**
     * Traegt einen Mitbauer aus - und sucht ihn zuerst auf der Parzelle selbst.
     *
     * <p>Das ist der Unterschied zum Eintragen: Dort muss ein Spieler erst gefunden werden,
     * hier steht er schon auf einer Liste. Vorher ging auch das Austragen ueber die
     * Spielerhistorie, und damit war genau der Eintrag unentfernbar, den die Anzeige als
     * UUID-Anfang zeigt - weil es zu ihm keinen Namen mehr gibt, den jemand nachschlagen
     * koennte. Gesucht wird gegen dieselbe Zeichenfolge, die die Anzeige ausgibt.
     */
    private void mitbauerWeg(Player spieler, Location ort, String name) {
        Optional<Parzelle> gefunden = buch.zustaendigerBesitz(welt, ort.getBlockX(),
                ort.getBlockZ());
        Optional<UUID> betroffener = gefunden
                .flatMap(parzelle -> ausDerListe(parzelle, name))
                .or(() -> findeSpieler(name));
        if (betroffener.isEmpty()) {
            spieler.sendMessage(Component.text(
                    "Steht hier niemand mit dem Namen - /parzelle mitbauer zeigt die Liste.",
                    NamedTextColor.RED));
            return;
        }
        var entscheid = buch.mitbauerEntfernen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ(), betroffener.get());
        switch (entscheid.bescheid()) {
            case ERTEILT -> spieler.sendMessage(Component.text(
                    name + " darf hier nicht mehr bauen.", NamedTextColor.GREEN));
            case NICHT_EINGETRAGEN -> spieler.sendMessage(Component.text(
                    name + " stand gar nicht auf der Liste.", NamedTextColor.YELLOW));
            default -> absage(spieler, entscheid.bescheid());
        }
    }

    /** Einen Eintrag der Mitbauerliste an der Zeichenfolge erkennen, die angezeigt wird. */
    private static Optional<UUID> ausDerListe(Parzelle parzelle, String eingabe) {
        for (UUID kandidat : parzelle.mitbauer()) {
            if (namen(kandidat).equalsIgnoreCase(eingabe)
                    || kandidat.toString().equalsIgnoreCase(eingabe)) {
                return Optional.of(kandidat);
            }
        }
        return Optional.empty();
    }

    private void warneVorAufgeben(Player spieler) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        Optional<Parzelle> gefunden = buch.zustaendigerBesitz(welt, ort.getBlockX(),
                ort.getBlockZ());
        if (gefunden.isEmpty() || !gefunden.get().gehoert(spieler.getUniqueId())) {
            spieler.sendMessage(Component.text("Das ist nicht deine Parzelle.",
                    NamedTextColor.RED));
            return;
        }
        Parzelle parzelle = gefunden.get();
        if (parzelle.hatElternteil()) {
            // Aufgeben verweigert auf einem Gruppenkind. Das sagt sich hier besser als
            // erst nach der Bestaetigung, mit dem allgemeinen Satz aus absage().
            spieler.sendMessage(Component.text(
                    "Die Parzelle gehoert zu einer Gruppe - erst /parzelle auftrennen.",
                    NamedTextColor.RED));
            return;
        }
        spieler.sendMessage(Component.text("Parzelle " + parzelle.x() + "/" + parzelle.z()
                + " aufgeben?", NamedTextColor.YELLOW));
        spieler.sendMessage(Component.text(
                "  Es gibt kein Geld zurueck, und gebaut bleibt stehen.", NamedTextColor.GRAY));
        spieler.sendMessage(Component.text("  /parzelle aufgeben bestaetigen "
                + parzelle.x() + " " + parzelle.z(), NamedTextColor.RED));
    }

    /**
     * Gibt die Parzelle auf, die im Befehl steht - nicht die, auf der der Spieler steht.
     *
     * <p>Der Unterschied ist der ganze Punkt: Vorher las dieser Zweig den Ort neu ein, und
     * ein Schritt ueber den Weg zwischen Warnung und Bestaetigung gab die Nachbarparzelle
     * auf. Jetzt muss der Spieler immer noch auf der Parzelle stehen, die er aufgibt, aber
     * es muss auch dieselbe sein, die im Befehl steht.
     */
    private int aufgeben(CommandContext<CommandSourceStack> zusammenhang) {
        if (!(zusammenhang.getSource().getExecutor() instanceof Player spieler)) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        Location ort = hier.get();
        if (!stehtAuf(spieler, ort, IntegerArgumentType.getInteger(zusammenhang, "x"),
                IntegerArgumentType.getInteger(zusammenhang, "z"), "Nichts aufgegeben.")) {
            return Command.SINGLE_SUCCESS;
        }
        var entscheid = buch.aufgeben(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ());
        if (entscheid.erteilt()) {
            Parzelle weg = entscheid.parzelle().orElseThrow();
            spieler.sendMessage(Component.text("Parzelle " + weg.x() + "/" + weg.z()
                    + " ist wieder frei.", NamedTextColor.GREEN));
            return Command.SINGLE_SUCCESS;
        }
        absage(spieler, entscheid.bescheid());
        return Command.SINGLE_SUCCESS;
    }

    // --- Zusammenlegen und Auftrennen ------------------------------------------

    /**
     * Der Zweig {@code /parzelle zusammenlegen <richtung>} - vier Literale, kein Textfeld.
     *
     * <p>Brigadier vergleicht die Literale selbst und schlaegt sie vor. Ein Textfeld
     * muesste den Namen zurueck in eine {@link Richtung} uebersetzen, und dabei liegt die
     * Falle mit dem Gebietsschema: {@code toUpperCase()} macht aus "sued" auf einem
     * tuerkischen Rechner "SUED" mit Punkt auf dem I, und der Befehl scheitert auf genau
     * einem Rechner der Welt. Diese Falle wird hier gar nicht gebaut.
     */
    private LiteralArgumentBuilder<CommandSourceStack> zusammenlegenZweig() {
        LiteralArgumentBuilder<CommandSourceStack> zweig = Commands.literal("zusammenlegen");
        for (Richtung richtung : Richtung.values()) {
            zweig.then(Commands.literal(richtung.wort())
                    .executes(fuerSpieler(spieler -> warneVorZusammenlegen(spieler, richtung)))
                    // Die Nummer gehoert in die Bestaetigung, genau wie beim Aufgeben: Ohne
                    // sie legt ein Schritt ueber den Weg zwischen Warnung und Bestaetigung
                    // ein anderes Paar zusammen - zu einem anderen Preis, und bezahlt ist
                    // es dann.
                    .then(Commands.literal("bestaetigen")
                            .then(Commands.argument("x", IntegerArgumentType.integer())
                                    .then(Commands.argument("z",
                                                    IntegerArgumentType.integer())
                                            .executes(z -> zusammenlegen(z, richtung))))));
        }
        return zweig;
    }

    /**
     * Was ein Zusammenlegen kosten wuerde - und was es mit dem Weg macht.
     *
     * <p>Gerechnet wird mit {@link Besitzbuch#pruefeZusammenlegen}, also mit derselben
     * Formel, die anschliessend abbucht. Eine eigene Rechnung hier waere der zweite Weg zur
     * selben Tat, und einer von beiden nennt irgendwann einen anderen Betrag.
     */
    private void warneVorZusammenlegen(Player spieler, Richtung richtung) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        int felder = gruppenFelder(spieler);
        var entscheid = buch.pruefeZusammenlegen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ(), richtung, felder);
        if (entscheid.bescheid() == Besitzbuch.Bescheid.GRUPPE_ZU_GROSS) {
            // Mit Zahl, nicht mit dem allgemeinen Satz: "dein Rang erlaubt nicht mehr"
            // laesst offen, was er erlaubt.
            spieler.sendMessage(Component.text("Dein Rang erlaubt hoechstens " + felder
                    + (felder == 1 ? " Feld" : " Felder") + " je Gruppe.", NamedTextColor.RED));
            werbung(spieler);
            return;
        }
        if (!entscheid.erteilt()) {
            absage(spieler, entscheid.bescheid());
            return;
        }

        Rasterplan plan = buch.plan();
        int px = plan.parzelleX(ort.getBlockX());
        int pz = plan.parzelleZ(ort.getBlockZ());
        long guthaben = buch.kasse().guthaben(spieler.getUniqueId());

        spieler.sendMessage(Component.text("Parzelle " + px + "/" + pz + " mit "
                + (px + richtung.dx()) + "/" + (pz + richtung.dz()) + " zusammenlegen?",
                NamedTextColor.YELLOW));
        spieler.sendMessage(Component.text("  Gebuehr: ", NamedTextColor.GRAY)
                .append(Component.text(Betraege.mark(entscheid.kosten()), NamedTextColor.GOLD))
                .append(Component.text(" · du hast " + Betraege.mark(guthaben),
                        NamedTextColor.GRAY)));
        spieler.sendMessage(Component.text(
                "  Der Weg dazwischen wird dein Bauland.", NamedTextColor.GRAY));
        spieler.sendMessage(Component.text(
                "  Beim Auftrennen kommt er zurueck - und raeumt alles weg, was dann",
                NamedTextColor.GRAY));
        spieler.sendMessage(Component.text("  darauf steht.", NamedTextColor.GRAY));
        if (guthaben < entscheid.kosten()) {
            // Das Geld prueft pruefeZusammenlegen absichtlich nicht - es ist die einzige
            // Pruefung, die bei Erfolg schreibt. Hier wird nur verglichen.
            spieler.sendMessage(Component.text("  Dein Guthaben reicht nicht.",
                    NamedTextColor.RED));
            return;
        }
        spieler.sendMessage(Component.text("  /parzelle zusammenlegen " + richtung.wort()
                + " bestaetigen " + px + " " + pz, NamedTextColor.YELLOW));
    }

    private int zusammenlegen(CommandContext<CommandSourceStack> zusammenhang,
                              Richtung richtung) {
        if (!(zusammenhang.getSource().getExecutor() instanceof Player spieler)) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        Location ort = hier.get();
        if (!stehtAuf(spieler, ort, IntegerArgumentType.getInteger(zusammenhang, "x"),
                IntegerArgumentType.getInteger(zusammenhang, "z"), "Nichts zusammengelegt.")) {
            return Command.SINGLE_SUCCESS;
        }
        // Was VOR dieser Zusammenlegung schon Gruppe war, und zwar auf beiden Seiten. Auf
        // einer alten Fuge steht inzwischen ein Haus; sie noch einmal zu fuellen wuerde das
        // Erdgeschoss auf Gras zuruecksetzen. Die Felder beider Seiten einzeln holen und
        // nicht zusammen: Vereinigt waeren sie genau die neue Gruppe, und dann gaebe es
        // nichts zu fuellen.
        Rasterplan plan = buch.plan();
        int px = plan.parzelleX(ort.getBlockX());
        int pz = plan.parzelleZ(ort.getBlockZ());
        List<List<Parzelle>> schonGelegt = List.of(
                buch.gruppenFelder(welt, ort.getBlockX(), ort.getBlockZ()),
                buch.gruppenFelder(welt, plan.ursprungX(px + richtung.dx()),
                        plan.ursprungZ(pz + richtung.dz())));
        var entscheid = buch.zusammenlegen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ(), richtung, gruppenFelder(spieler));
        if (!entscheid.erteilt()) {
            absage(spieler, entscheid.bescheid());
            return Command.SINGLE_SUCCESS;
        }
        List<Parzelle> gruppe = buch.gruppenFelder(welt, ort.getBlockX(), ort.getBlockZ());
        spieler.sendMessage(Component.text("Ein Grundstueck aus " + gruppe.size()
                + " Feldern.", NamedTextColor.GREEN));
        spieler.sendMessage(Component.text("  Bezahlt: "
                + Betraege.mark(entscheid.kosten()) + " · Rest: "
                + Betraege.mark(buch.kasse().guthaben(spieler.getUniqueId())),
                NamedTextColor.GRAY));
        andereDenBoden(spieler, () -> wegbau.fuelle(gruppe, schonGelegt),
                "Der Weg dazwischen ist Bauland");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Warnt vor dem Auftrennen - und nennt vorher, wie viele Bloecke dabei verschwinden.
     *
     * <p>Das ist der Grund, warum es diese Warnung ueberhaupt gibt. Nach dem Zusammenlegen
     * ist die ehemalige Wegflaeche Bauland, und der Spieler baut dort. Beim Auftrennen muss
     * der Weg zurueck, sonst stuende eine Mauer quer ueber einer oeffentlichen Strasse, die
     * niemand mehr entfernen darf. Also wird geraeumt - aber nicht, ohne es vorher zu sagen.
     */
    private void warneVorAuftrennen(Player spieler) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        var entscheid = buch.pruefeAuftrennen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ());
        if (!entscheid.erteilt()) {
            absage(spieler, entscheid.bescheid());
            return;
        }
        Parzelle anker = entscheid.parzelle().orElseThrow();
        List<Parzelle> gruppe = buch.gruppenFelder(welt, ort.getBlockX(), ort.getBlockZ());

        spieler.sendMessage(Component.text("Grundstueck aus " + gruppe.size()
                + " Feldern ganz auftrennen?", NamedTextColor.YELLOW));
        spieler.sendMessage(Component.text(
                "  ALLE Wege zwischen den Feldern kommen zurueck. Geld gibt es keines"
                        + " zurueck.", NamedTextColor.GRAY));
        spieler.sendMessage(Component.text(
                "  Fuer ein einzelnes Feld: stell dich darauf und nimm /parzelle auftrennen"
                        + " ohne \"alles\".", NamedTextColor.GRAY));
        nenneAufwand(spieler, wegbau.aufwandBeimAuftrennen(gruppe, List.of()));
        spieler.sendMessage(Component.text("  /parzelle auftrennen alles bestaetigen "
                + anker.x() + " " + anker.z(), NamedTextColor.RED));
    }

    /**
     * Warnt vor dem Herausloesen des Feldes, auf dem der Spieler steht.
     *
     * <p>Zurueck kommt nur der Weg zu diesem einen Feld. Die Wege im Rest des
     * Grundstuecks bleiben Bauland - sonst muesste der Spieler sie neu bezahlen, nur weil
     * er ein Feld abgeben wollte.
     */
    private void warneVorHerausloesen(Player spieler) {
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return;
        }
        Location ort = hier.get();
        var entscheid = buch.pruefeHerausloesen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ());
        if (!entscheid.erteilt()) {
            absage(spieler, entscheid.bescheid());
            return;
        }
        Parzelle feld = entscheid.parzelle().orElseThrow();
        List<Parzelle> gruppe = buch.gruppenFelder(welt, ort.getBlockX(), ort.getBlockZ());
        List<Parzelle> bleibend = ohneDasFeld(gruppe, feld);

        if (bleibend.size() < 2) {
            spieler.sendMessage(Component.text("Grundstueck aus " + gruppe.size()
                    + " Feldern auftrennen?", NamedTextColor.YELLOW));
            spieler.sendMessage(Component.text(
                    "  Es hat nur zwei Felder - eines allein geht nicht heraus, also wird",
                    NamedTextColor.GRAY));
            spieler.sendMessage(Component.text("  es ganz aufgetrennt.",
                    NamedTextColor.GRAY));
        } else {
            spieler.sendMessage(Component.text("Das Feld " + feld.x() + "/" + feld.z()
                    + " aus dem Grundstueck herausloesen?", NamedTextColor.YELLOW));
            spieler.sendMessage(Component.text("  Die anderen " + bleibend.size()
                    + " Felder bleiben ein Grundstueck.", NamedTextColor.GRAY));
        }
        spieler.sendMessage(Component.text(
                "  Der Weg dorthin kommt zurueck. Geld gibt es keines zurueck.",
                NamedTextColor.GRAY));
        nenneAufwand(spieler, wegbau.aufwandBeimAuftrennen(gruppe, bleibt(bleibend)));
        spieler.sendMessage(Component.text("  /parzelle auftrennen bestaetigen "
                + feld.x() + " " + feld.z(), NamedTextColor.RED));
    }

    /**
     * Nennt, was die Weltarbeit kostet - Verlust in Rot, Fuellung in Grau.
     *
     * <p>Dieselben Saetze fuer das Herausloesen und fuer das ganze Auftrennen. Zwei
     * Fassungen wuerden irgendwann verschiedene Zahlen verschieden benennen.
     */
    private void nenneAufwand(Player spieler, Wegbau.Aufwand aufwand) {
        // "von der Flaeche" und nicht "die auf der Wegflaeche stehen": Gezaehlt wird die
        // ganze Spalte, also auch ein Fremdblock im Schacht darunter. Wer den Satz woertlich
        // nimmt, sucht auf der Oberflaeche und findet nichts - und haelt die rote Zahl fuer
        // einen Fehler.
        if (aufwand.verschwindet() == 1) {
            spieler.sendMessage(Component.text("  Dabei verschwindet 1 Block von der "
                    + "Flaeche.", NamedTextColor.RED));
        } else if (aufwand.verschwindet() > 1) {
            spieler.sendMessage(Component.text("  Dabei verschwinden "
                    + aufwand.verschwindet() + " Bloecke von der Flaeche.",
                    NamedTextColor.RED));
        }
        // Getrennt von den Verlusten und in Grau: Hier geht nichts weg, hier kommt etwas
        // hinzu. In einer Zahl zusammengefasst stuende ueber einem leeren Becken "es
        // verschwinden 4.928 Bloecke" - und das Gegenteil waere wahr. "Fehlstelle" und
        // nicht "unter der Flaeche", weil die Oberflaeche selbst dazugehoert.
        if (aufwand.verfuellt() == 1) {
            spieler.sendMessage(Component.text("  1 Fehlstelle wird wieder gefuellt.",
                    NamedTextColor.GRAY));
        } else if (aufwand.verfuellt() > 1) {
            spieler.sendMessage(Component.text("  " + aufwand.verfuellt()
                    + " Fehlstellen werden wieder gefuellt.", NamedTextColor.GRAY));
        }
        if (aufwand.istNichts()) {
            spieler.sendMessage(Component.text(
                    "  Auf der Wegflaeche steht nichts - es geht nichts verloren.",
                    NamedTextColor.GRAY));
        }
    }

    /** Die Felder der Gruppe ohne dieses eine. */
    private static List<Parzelle> ohneDasFeld(List<Parzelle> gruppe, Parzelle feld) {
        return gruppe.stream().filter(anderes -> anderes.id() != feld.id()).toList();
    }

    /**
     * Was nach dem Schritt noch Gruppe ist - fuer die Weltarbeit.
     *
     * <p>Unter zwei Feldern ist es keine Gruppe mehr, dann kommt der ganze Weg zurueck.
     */
    private static List<List<Parzelle>> bleibt(List<Parzelle> bleibend) {
        return bleibend.size() < 2 ? List.of() : List.of(bleibend);
    }

    /**
     * Loest das Feld heraus, auf dem der Spieler steht.
     *
     * <p>Bei einer Gruppe aus zwei Feldern trennt es ganz auf: Was uebrig bliebe, waere ein
     * einzelnes Feld, und eine Gruppe aus einem Feld gibt es nicht.
     */
    private int herausloesen(CommandContext<CommandSourceStack> zusammenhang) {
        if (!(zusammenhang.getSource().getExecutor() instanceof Player spieler)) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        Location ort = hier.get();
        if (!stehtAuf(spieler, ort, IntegerArgumentType.getInteger(zusammenhang, "x"),
                IntegerArgumentType.getInteger(zusammenhang, "z"), "Nichts herausgeloest.")) {
            return Command.SINGLE_SUCCESS;
        }
        // Die Felder VOR dem Loesen holen, wie beim ganzen Auftrennen: Danach sagt die
        // Ablage nicht mehr, welche Flaeche zurueckzubauen waere.
        List<Parzelle> gruppe = buch.gruppenFelder(welt, ort.getBlockX(), ort.getBlockZ());
        var entscheid = buch.herausloesen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ());
        if (!entscheid.erteilt()) {
            absage(spieler, entscheid.bescheid());
            return Command.SINGLE_SUCCESS;
        }
        Parzelle feld = entscheid.parzelle().orElseThrow();
        List<Parzelle> bleibend = ohneDasFeld(gruppe, feld);
        if (bleibend.size() < 2) {
            spieler.sendMessage(Component.text("Aufgetrennt - " + gruppe.size()
                    + " eigenstaendige Parzellen.", NamedTextColor.GREEN));
        } else {
            spieler.sendMessage(Component.text("Feld " + feld.x() + "/" + feld.z()
                    + " herausgeloest - das Grundstueck hat noch " + bleibend.size()
                    + " Felder.", NamedTextColor.GREEN));
        }
        andereDenBoden(spieler, () -> wegbau.baueZurueck(gruppe, bleibt(bleibend)),
                "Der Weg ist zurueck");
        return Command.SINGLE_SUCCESS;
    }

    private int auftrennen(CommandContext<CommandSourceStack> zusammenhang) {
        if (!(zusammenhang.getSource().getExecutor() instanceof Player spieler)) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        Location ort = hier.get();
        if (!stehtAuf(spieler, ort, IntegerArgumentType.getInteger(zusammenhang, "x"),
                IntegerArgumentType.getInteger(zusammenhang, "z"), "Nichts aufgetrennt.")) {
            return Command.SINGLE_SUCCESS;
        }
        // Die Felder VOR dem Auftrennen holen. Danach gehoert keines mehr zu einer Gruppe,
        // und welche Flaeche zurueckzubauen waere, liesse sich nicht mehr feststellen.
        List<Parzelle> gruppe = buch.gruppenFelder(welt, ort.getBlockX(), ort.getBlockZ());
        var entscheid = buch.auftrennen(welt, spieler.getUniqueId(),
                ort.getBlockX(), ort.getBlockZ());
        if (!entscheid.erteilt()) {
            absage(spieler, entscheid.bescheid());
            return Command.SINGLE_SUCCESS;
        }
        spieler.sendMessage(Component.text("Aufgetrennt - " + gruppe.size()
                + " eigenstaendige Parzellen.", NamedTextColor.GREEN));
        andereDenBoden(spieler, () -> wegbau.baueZurueck(gruppe, List.of()),
                "Der Weg ist zurueck");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Fuehrt die Bodenarbeit aus und sagt, was daraus geworden ist.
     *
     * <p>Die Ablage ist an dieser Stelle schon geaendert, und beides zusammen laesst sich
     * nicht sichern - eine Welt kennt keine Transaktion. Scheitert das Setzen, gilt
     * trotzdem die Ablage: Der Spieler darf dort bauen, es sieht nur noch nicht danach aus.
     * Das ist die harmlose Haelfte des Fehlers, und sie gehoert ins Protokoll, nicht in
     * einen Abbruch - andersherum waere das Geld weg und das Grundstueck nicht da.
     */
    private void andereDenBoden(Player spieler, IntSupplier arbeit, String gelungen) {
        try {
            // "geaendert" und nicht nur die Zahl: Darin stecken der Weg selbst und das,
            // was geraeumt wurde. Ohne das Wort liest sich "Der Weg ist zurueck (623
            // Bloecke)", als haette der Weg 623 Bloecke.
            spieler.sendMessage(Component.text("  " + gelungen + " (" + arbeit.getAsInt()
                    + " Bloecke geaendert).", NamedTextColor.GRAY));
        } catch (RuntimeException fehler) {
            protokoll.log(Level.SEVERE,
                    "Der Boden der Gruppe liess sich nicht aendern - die Ablage stimmt, "
                            + "die Welt nicht", fehler);
            spieler.sendMessage(Component.text(
                    "  Der Boden liess sich nicht aendern. Die Parzellen stimmen - "
                            + "sag einem Operator Bescheid.", NamedTextColor.RED));
        }
    }

    // --- Kleinteile ------------------------------------------------------------

    /**
     * Ob der Spieler auf der Rasterzelle steht, die im Befehl steht.
     *
     * <p>Die Sicherung gegen einen Schritt zwischen Warnung und Bestaetigung, fuer alle
     * drei Befehle, die etwas Endgueltiges tun. Sie steht an einer Stelle, weil der dritte
     * sie sonst irgendwann nicht bekommt.
     *
     * <p>Gefragt wird nach der Rasterzelle und nicht nach dem zustaendigen Besitz: Beide
     * nennen dieselbe Nummer - {@code zustaendigerBesitz} sucht die Zeile ja bei
     * {@code parzelleX/parzelleZ} -, aber die Rasterzelle braucht keine Abfrage.
     */
    private boolean stehtAuf(Player spieler, Location ort, int gemeintX, int gemeintZ,
                             String nichtsPassiert) {
        Rasterplan plan = buch.plan();
        if (plan.parzelleX(ort.getBlockX()) == gemeintX
                && plan.parzelleZ(ort.getBlockZ()) == gemeintZ) {
            return true;
        }
        spieler.sendMessage(Component.text("Du stehst nicht auf Parzelle " + gemeintX + "/"
                + gemeintZ + ". " + nichtsPassiert, NamedTextColor.RED));
        return false;
    }

    /** Ob dieser Ort in der Marktstadt liegt - der eine Vergleich dafuer. */
    private boolean inDerMarktstadt(Location ort) {
        return marktstadt.equals(ort.getWorld());
    }

    /**
     * Die Absage an den Spieler: der Satz - und wo ein RANG die Grenze setzt, die Werbung
     * dahinter.
     *
     * <p>Beides hier und nicht an den zehn Aufrufstellen. Ein Hinweis, den neun von zehn
     * Wegen kennen, wirkt auf den zehnten wie ein Zufall, und der Spieler sucht beim
     * naechsten Mal vergeblich danach.
     *
     * <p>Angehaengt wird nur, wo der Rang wirklich der Grund ist. Wer zu wenig Geld hat
     * oder auf dem Weg steht, bekommt keine Werbung - sie wuerde ihm etwas verkaufen
     * wollen, das sein Problem nicht loest.
     */
    private void absage(Player spieler, Besitzbuch.Bescheid bescheid) {
        boolean amLimit = anzahl(spieler) >= hoechstzahl(spieler);
        spieler.sendMessage(Component.text(switch (bescheid) {
            case KEINE_PARZELLE -> "Hier ist keine Parzelle.";
            case NOCH_FREI -> amLimit
                    ? "Diese Parzelle gehoert noch niemandem - aber dein Rang erlaubt "
                            + hoechstzahl(spieler)
                            + (hoechstzahl(spieler) == 1 ? " Parzelle" : " Parzellen")
                            + ", und so viele hast du schon."
                    : "Diese Parzelle gehoert noch niemandem - nimm sie mit "
                            + "/parzelle nehmen.";
            case NICHT_EIGENTUEMER -> "Das darf nur der Eigentuemer.";
            case SCHON_VERGEBEN -> "Die gehoert schon jemandem.";
            case AUSSERHALB -> "Das liegt ausserhalb der Marktstadt.";
            // Mit der Zahl: "nicht mehr" laesst offen, wie viele es denn sind.
            case GRENZE_ERREICHT -> "Dein Rang erlaubt " + hoechstzahl(spieler)
                    + (hoechstzahl(spieler) == 1 ? " Parzelle" : " Parzellen")
                    + ", und so viele hast du schon.";
            case ZU_WENIG_MARK -> "Dein Guthaben reicht nicht.";
            case SCHON_EINGETRAGEN -> "Steht schon auf der Liste.";
            case NICHT_EINGETRAGEN -> "Stand gar nicht auf der Liste.";
            case TEIL_EINER_GRUPPE -> "Die Parzelle gehoert zu einer Gruppe - "
                    + "das geht nur ueber die ganze Gruppe.";
            case NACHBAR_FREI -> "Dort drueben ist keine Parzelle vergeben.";
            case NACHBAR_FREMD -> "Die Nachbarparzelle gehoert jemand anderem.";
            case SCHON_IN_GRUPPE -> "Die beiden gehoeren zu zwei verschiedenen "
                    + "Grundstuecken. Trenne eines auf und haenge die Felder einzeln an.";
            case SCHON_ZUSAMMEN -> "Die beiden sind schon dasselbe Grundstueck.";
            case GRUPPE_ZU_GROSS -> "Dein Rang erlaubt hoechstens " + gruppenFelder(spieler)
                    + (gruppenFelder(spieler) == 1 ? " Feld" : " Felder") + " je Gruppe.";
            case KEINE_GRUPPE -> "Hier ist keine Gruppe.";
            case AUF_DEM_WEG -> "Stell dich auf das Feld, das du meinst - nicht auf den Weg.";
            case HAELT_ZUSAMMEN -> "Dieses Feld haelt dein Grundstueck zusammen. Loese erst eines am Rand heraus.";
            case ERTEILT -> "Erledigt.";
        }, NamedTextColor.RED));
        if (bescheid == Besitzbuch.Bescheid.GRENZE_ERREICHT
                || bescheid == Besitzbuch.Bescheid.GRUPPE_ZU_GROSS
                || (bescheid == Besitzbuch.Bescheid.NOCH_FREI && amLimit)) {
            werbung(spieler);
        }
    }

    /**
     * Die Zeile aus der Konfiguration - einmal, wenn sie gesetzt ist.
     *
     * <p>In Gelb und nicht in Rot: Die Absage darueber ist die Nachricht, das hier ist
     * das Angebot. Zwei rote Zeilen hintereinander liest niemand als zwei Dinge.
     */
    private void werbung(Player spieler) {
        if (!rangWerbung.isEmpty()) {
            spieler.sendMessage(Component.text(rangWerbung, NamedTextColor.YELLOW));
        }
    }

    /**
     * Wo der Spieler steht - aber nur, wenn er in der Marktstadt steht.
     *
     * <p>Das ist bewusst die EINZIGE Stelle, an der ein Befehl an Koordinaten kommt. Vorher
     * war die Weltpruefung eine freiwillige Zeile am Anfang jeder Methode, und zwei von
     * sieben hatten sie nicht: Wer in einer anderen Welt stand, veraenderte damit die
     * Marktstadt-Parzelle an genau diesen Koordinaten - fremden Besitz, aus einer Welt
     * heraus, in der es keine Parzellen gibt. Jetzt gibt es ohne diese Pruefung keinen Ort,
     * mit dem sich etwas anstellen liesse.
     */
    private Optional<Location> ortInMarktstadt(Player spieler) {
        Location ort = spieler.getLocation();
        if (inDerMarktstadt(ort)) {
            return Optional.of(ort);
        }
        spieler.sendMessage(Component.text(
                "Parzellen gibt es nur in der Marktstadt.", NamedTextColor.RED));
        return Optional.empty();
    }

    private int anzahl(Player spieler) {
        return buch.parzellenVon(welt, spieler.getUniqueId()).size();
    }

    /**
     * Wie viele Parzellen dieser Spieler haben darf.
     *
     * <p>Ueber Rechte und nicht ueber eine Rangtabelle im Plugin: So legt die
     * Rechteverwaltung die Raenge fest - dieselbe, die auch alles andere regelt - und
     * Freimarken muss von Raengen nichts wissen.
     *
     * <p>Ein Operator bekommt dadurch NICHT von sich aus mehr Parzellen: Gelesen werden die
     * gesetzten Rechte, und {@code freimarken.parzellen.8} ist bei niemandem gesetzt, nur
     * weil er Operator ist. Wer mehr haben soll, bekommt das Recht eingetragen. So gewollt -
     * die Obergrenze ist eine Spielregel und keine Verwaltungsbefugnis.
     */
    private int hoechstzahl(Player spieler) {
        return hoechsteAus(spieler, RECHT_ANZAHL, standardHoechstzahl);
    }

    /**
     * Wie viele Felder eine Gruppe dieses Spielers haben darf.
     *
     * <p>Eine eigene Grenze und bewusst nicht dieselbe wie {@link #hoechstzahl}: Wie viele
     * Parzellen jemand besitzt, bremst das Flaechenhorten; wie viele davon er zu EINEM
     * Grundstueck verbinden darf, entscheidet, wie gross ein einzelnes Bauwerk werden kann.
     */
    private int gruppenFelder(Player spieler) {
        return hoechsteAus(spieler, RECHT_GRUPPE, standardGruppenFelder);
    }

    /**
     * Die hoechste Zahl, die ein gesetztes Recht mit diesem Anfang nennt - mindestens die
     * Vorgabe.
     *
     * <p>Eine Auswertung fuer beide Grenzen. Zwei Abschriften derselben Schleife waeren
     * zwei Orte fuer dieselbe Falle, und die zweite bekaeme den Hinweis unten nicht mit.
     */
    private static int hoechsteAus(Player spieler, String recht, int vorgabe) {
        int hoechste = vorgabe;
        for (PermissionAttachmentInfo eintrag : spieler.getEffectivePermissions()) {
            if (!eintrag.getValue() || !eintrag.getPermission().startsWith(recht)) {
                continue;
            }
            try {
                // long und dann gekappt: Integer.parseInt haette auch bei einer gueltigen,
                // nur sehr grossen Zahl geworfen - und die waere dann wie ein Tippfehler
                // behandelt worden, also stillschweigend als "keine Erhoehung".
                long gewuenscht = Long.parseLong(
                        eintrag.getPermission().substring(recht.length()));
                hoechste = (int) Math.max(hoechste, Math.min(gewuenscht, Integer.MAX_VALUE));
            } catch (NumberFormatException keineZahl) {
                // "freimarken.parzellen.alle" oder ein Tippfehler - kein Grund fuer einen
                // Abbruch, nur kein Anlass, die Obergrenze zu heben.
            }
        }
        return hoechste;
    }

    private static String namen(UUID spieler) {
        String name = Bukkit.getOfflinePlayer(spieler).getName();
        return name != null ? name : spieler.toString().substring(0, 8);
    }

    private static String namenListe(Collection<UUID> spieler) {
        return spieler.stream().map(ParzellenBefehle::namen).sorted().reduce(
                (a, b) -> a + ", " + b).orElse("niemand");
    }

    /**
     * Sucht einen Spieler am Namen - ohne ins Netz und ohne durch die Historie zu laufen.
     *
     * <p>Absichtlich NICHT {@code Bukkit.getOfflinePlayer(String)}: Das fragt im Zweifel bei
     * Mojang nach und haelt dabei den ganzen Server an.
     *
     * <p>Und absichtlich nicht mehr {@code Bukkit.getOfflinePlayers()}: Das baute die Liste
     * aller je angemeldeten Spieler bei JEDEM Aufruf neu auf, mit einem {@code getName()}
     * je Eintrag. Ein Spieler, der diesen Befehl mit Zufallsnamen wiederholt, haelt damit
     * den ganzen Server an - ohne Recht, ohne eigene Parzelle, aus jeder Welt heraus.
     * {@code getOfflinePlayerIfCached} schaut nur in den Zwischenspeicher und geht nie ins
     * Netz; der Preis ist, dass ein lange abwesender Spieler nicht gefunden wird, solange
     * ihn niemand nachgeschlagen hat.
     */
    private static Optional<UUID> findeSpieler(String name) {
        Player anwesend = Bukkit.getPlayerExact(name);
        if (anwesend != null) {
            return Optional.of(anwesend.getUniqueId());
        }
        OfflinePlayer bekannt = Bukkit.getOfflinePlayerIfCached(name);
        return bekannt != null ? Optional.of(bekannt.getUniqueId()) : Optional.empty();
    }

    /** Zum Eintragen: wer gerade da ist. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> anwesendeNamen() {
        return Commands.argument("spieler", StringArgumentType.word())
                .suggests((zusammenhang, bauer) -> {
                    Bukkit.getOnlinePlayers().forEach(p -> bauer.suggest(p.getName()));
                    return bauer.buildFuture();
                });
    }

    /**
     * Zum Austragen: wer auf dieser Parzelle steht.
     *
     * <p>Ohne Meldung und ohne {@link #ortInMarktstadt}: Ein Vorschlagsgeber laeuft bei
     * jedem Tastendruck. Eine Absage im Chat waere dort kein Hinweis, sondern Laerm.
     */
    private RequiredArgumentBuilder<CommandSourceStack, String> mitbauerNamen() {
        return Commands.argument("spieler", StringArgumentType.word())
                .suggests((zusammenhang, bauer) -> {
                    if (zusammenhang.getSource().getExecutor() instanceof Player spieler) {
                        Location ort = spieler.getLocation();
                        if (inDerMarktstadt(ort)) {
                            buch.zustaendigerBesitz(welt, ort.getBlockX(), ort.getBlockZ())
                                    .ifPresent(parzelle -> parzelle.mitbauer().stream()
                                            .map(ParzellenBefehle::namen)
                                            .sorted()
                                            .forEach(bauer::suggest));
                        }
                    }
                    return bauer.buildFuture();
                });
    }

    /** Hebt den Spieler aus der Befehlsquelle - alle Zweige hier sind spielerpflichtig. */
    private Command<CommandSourceStack> fuerSpieler(Consumer<Player> tat) {
        return zusammenhang -> {
            if (zusammenhang.getSource().getExecutor() instanceof Player spieler) {
                tat.accept(spieler);
            }
            return Command.SINGLE_SUCCESS;
        };
    }

    /**
     * Wie {@link #fuerSpieler}, zusaetzlich mit Ort und dem eingegebenen Namen.
     *
     * <p>Die Welt wird hier geprueft, VOR allem anderen. Vorher lief die Namensauflösung
     * zuerst - und damit auch dann, wenn der Spieler in einer Welt stand, in der es keine
     * Parzellen gibt. Der Name wird absichtlich unaufgeloest weitergegeben: Eintragen und
     * Austragen suchen an verschiedenen Stellen.
     */
    private int mitOrtUndName(CommandContext<CommandSourceStack> zusammenhang, OrtUndName tat) {
        if (!(zusammenhang.getSource().getExecutor() instanceof Player spieler)) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Location> hier = ortInMarktstadt(spieler);
        if (hier.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        tat.fuehreAus(spieler, hier.get(),
                StringArgumentType.getString(zusammenhang, "spieler"));
        return Command.SINGLE_SUCCESS;
    }

    @FunctionalInterface
    private interface OrtUndName {
        void fuehreAus(Player spieler, Location ort, String name);
    }
}
