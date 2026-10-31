package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Rasterplan;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
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

    private final Besitzbuch buch;

    /** Die Welt als Schluesseltext - aus demselben Grund wie in {@link Schutzwache}. */
    private final String welt;

    private final int standardHoechstzahl;

    ParzellenBefehle(Besitzbuch buch, String welt, int standardHoechstzahl) {
        this.buch = buch;
        this.welt = welt;
        this.standardHoechstzahl = standardHoechstzahl;
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
            spieler.sendMessage(Component.text("  frei · ", NamedTextColor.GRAY)
                    .append(Component.text(preis == 0L ? "gratis" : Betraege.mark(preis),
                            NamedTextColor.GREEN))
                    .append(Component.text(" · /parzelle nehmen", NamedTextColor.DARK_GRAY)));
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
            case GRENZE_ERREICHT -> spieler.sendMessage(Component.text(
                    "Du hast schon " + hoechstzahl
                            + (hoechstzahl == 1 ? " Parzelle" : " Parzellen")
                            + " - mehr erlaubt dein Rang nicht.", NamedTextColor.RED));
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
                            : " · " + parzelle.mitbauer().size() + " Mitbauer"),
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
        Optional<Parzelle> gefunden = buch.parzelleAn(welt, ort.getBlockX(), ort.getBlockZ());
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
            default -> spieler.sendMessage(absage(entscheid.bescheid()));
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
        Optional<Parzelle> gefunden = buch.parzelleAn(welt, ort.getBlockX(), ort.getBlockZ());
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
            default -> spieler.sendMessage(absage(entscheid.bescheid()));
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
        Optional<Parzelle> gefunden = buch.parzelleAn(welt, ort.getBlockX(), ort.getBlockZ());
        if (gefunden.isEmpty() || !gefunden.get().gehoert(spieler.getUniqueId())) {
            spieler.sendMessage(Component.text("Das ist nicht deine Parzelle.",
                    NamedTextColor.RED));
            return;
        }
        Parzelle parzelle = gefunden.get();
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
        int gemeintX = IntegerArgumentType.getInteger(zusammenhang, "x");
        int gemeintZ = IntegerArgumentType.getInteger(zusammenhang, "z");
        Rasterplan plan = buch.plan();
        if (plan.parzelleX(ort.getBlockX()) != gemeintX
                || plan.parzelleZ(ort.getBlockZ()) != gemeintZ) {
            spieler.sendMessage(Component.text("Du stehst nicht auf Parzelle "
                    + gemeintX + "/" + gemeintZ + ". Nichts aufgegeben.", NamedTextColor.RED));
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
        spieler.sendMessage(absage(entscheid.bescheid()));
        return Command.SINGLE_SUCCESS;
    }

    // --- Kleinteile ------------------------------------------------------------

    private Component absage(Besitzbuch.Bescheid bescheid) {
        return Component.text(switch (bescheid) {
            case KEINE_PARZELLE -> "Hier ist keine Parzelle.";
            case NICHT_EIGENTUEMER -> "Das darf nur der Eigentuemer.";
            case SCHON_VERGEBEN -> "Die gehoert schon jemandem.";
            case AUSSERHALB -> "Das liegt ausserhalb der Marktstadt.";
            case GRENZE_ERREICHT -> "Dein Rang erlaubt nicht mehr Parzellen.";
            case ZU_WENIG_MARK -> "Dein Guthaben reicht nicht.";
            case SCHON_EINGETRAGEN -> "Steht schon auf der Liste.";
            case NICHT_EINGETRAGEN -> "Stand gar nicht auf der Liste.";
            case ERTEILT -> "Erledigt.";
        }, NamedTextColor.RED);
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
        if (ort.getWorld() != null && welt.equals(ort.getWorld().key().asString())) {
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
        int hoechste = standardHoechstzahl;
        for (PermissionAttachmentInfo eintrag : spieler.getEffectivePermissions()) {
            if (!eintrag.getValue() || !eintrag.getPermission().startsWith(RECHT_ANZAHL)) {
                continue;
            }
            try {
                // long und dann gekappt: Integer.parseInt haette auch bei einer gueltigen,
                // nur sehr grossen Zahl geworfen - und die waere dann wie ein Tippfehler
                // behandelt worden, also stillschweigend als "keine Erhoehung".
                long gewuenscht = Long.parseLong(
                        eintrag.getPermission().substring(RECHT_ANZAHL.length()));
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
                        if (ort.getWorld() != null
                                && welt.equals(ort.getWorld().key().asString())) {
                            buch.parzelleAn(welt, ort.getBlockX(), ort.getBlockZ())
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
