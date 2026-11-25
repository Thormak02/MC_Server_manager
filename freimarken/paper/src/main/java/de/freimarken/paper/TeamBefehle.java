package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Teambuch;
import de.freimarken.kern.Teamstand;
import de.freimarken.kern.Teamwerte;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /team} - Teamrollen vergeben, entziehen und nachsehen (Plan 12c.2).
 *
 * <p><b>Fuenf Zweige, und jeder hat sein eigenes {@code executes}</b>: {@code /team}
 * zeigt die Liste, {@code /team rollen} die Rollen der Datei, {@code /team zeigen
 * &lt;spieler&gt;} die Rollen eines Menschen, {@code /team gib} und {@code /team nimm}
 * aendern etwas. Ohne das eigene {@code executes} endet ein Zweig in Brigadiers
 * englischem "Unknown or incomplete command" - gemessen in diesem Projekt schon zweimal,
 * zuletzt bei {@code /lotterie ziehen}.
 *
 * <p><b>Die RECHTE DES VERGEBENDEN werden hier geholt und dem Kern gegeben</b> - das ist
 * die tragende Naht dieser Klasse. {@code Teambuch} entscheidet mit einer Rechtemenge,
 * nicht mit einem Spieler, und darum laeuft jede Rechtelage ohne Server durch eine Probe.
 * {@code null} statt der Menge heisst DIE KONSOLE und darf alles; der Grund steht in
 * {@code Teambuch.gib}, und er ist nicht Bequemlichkeit: Wer an der Konsole steht, hat
 * Dateizugriff.
 *
 * <p><b>Vergeben wird auch an ABWESENDE</b>, und das ist der Normalfall - niemand wird
 * zum Moderator gemacht, waehrend er zusieht. Darum
 * {@link Spielername#kennungAuchAbwesend} hinter der Naht und nicht
 * {@link Spielername#kennung}: Jene findet nur, wer gerade da ist.
 *
 * <p><b>Jede Aenderung steht im Protokoll</b>, mit Absender, Betroffenem und Rolle (Plan
 * 12c.3: "alles protokolliert, mit Grund und Bearbeiter"). Eine Rollenvergabe ist der
 * Eingriff, bei dem die Frage "wer hat das gemacht" am haeufigsten gestellt wird - und
 * die Antwort steht ausserdem in der Datenbank, in der Spalte {@code vergeber}.
 */
final class TeamBefehle {

    /** Wer die Teamliste und die Rollen sehen darf. */
    static final String RECHT = "freimarken.team";

    /** Wer Rollen geben und nehmen darf - zusaetzlich zum Rechteriegel im Kern. */
    static final String RECHT_VERGEBEN = "freimarken.team.vergeben";

    /** Der Name des Befehls - er steht woertlich in den Saetzen der Sprachdatei. */
    static final String ARGUMENT = "team";

    static final String ROLLEN = "rollen";

    static final String ZEIGEN = "zeigen";

    static final String GIB = "gib";

    static final String NIMM = "nimm";

    /**
     * Woher die Kennung zu einem getippten Namen kommt.
     *
     * <p>Eine Naht und kein statischer Aufruf, wie bei {@link BlockBefehle.Namenssuche}:
     * {@code Bukkit.getOfflinePlayer} wirft ohne Server, und dann liefe keine Probe.
     */
    interface Namenssuche {

        /** Die Kennung, oder {@code null}, wenn der Server den Namen nicht kennt. */
        UUID kennungVon(String name);
    }

    /**
     * Wie aus einer Kennung wieder ein Name wird - fuer die Liste und die Auskunft.
     *
     * <p>Zweite Naht aus demselben Grund. Im Spiel steht dahinter
     * {@link Spielername#von}, das auf die ersten Zeichen der Kennung zurueckfaellt, wenn
     * der Server den Namen nicht mehr kennt.
     */
    interface Namensgeber {
        String nameVon(UUID wer);
    }

    /**
     * Die Rechte eines Spielers neu setzen - sofort nach einer Rollenaenderung.
     *
     * <p><b>Ohne diese Naht wirkt eine Rollenaenderung erst beim naechsten
     * Spielzeittakt</b> [behoben am 25.11.2026]: Gesetzt wird das Anhaengsel allein in
     * {@code Rangwache.vergibNeu}, und das laeuft beim Beitritt und im Takt - ab Werk
     * alle 60 Sekunden, erlaubt sind 600. Ein Verwalter entzog einem missbrauchenden
     * Moderator die Rolle, las GENOMMEN und hielt ihn fuer entwaffnet, waehrend der in
     * diesem Fenster weiter bannen konnte. Die zwei Seiten derselben Aenderung wirkten
     * zu verschiedenen Zeiten.
     *
     * <p>Sie gilt fuer BEIDE Richtungen: Wer eine Rolle BEKOMMT, soll sie auch sofort
     * haben - sonst tippt er den Befehl, liest eine Absage und meldet einen Fehler.
     *
     * <p>Ist der Spieler nicht angemeldet, geschieht nichts, und das ist kein Fehler:
     * Beim Beitritt setzt die {@code Rangwache} seine Rechte ohnehin neu.
     */
    interface Rechtehand {
        void setzeNeu(UUID wer);
    }

    /** Die Uhr - als Naht, damit eine Probe einen festen Zeitpunkt hereingeben kann. */
    interface Uhr {
        Instant jetzt();
    }

    private final Teambuch buch;
    private final Sprache sprache;
    private final Namenssuche suche;
    private final Namensgeber namen;
    private final Uhr uhr;

    /**
     * Die Zone fuer die Vergabezeit - als Lieferant und nicht als Wert.
     *
     * <p>Sie kommt aus {@code zeit.zone} und ist nicht neuladbar, aber ein hier
     * festgehaltenes Exemplar waere eine zweite Quelle fuer dieselbe Frage. Dasselbe
     * Vorgehen wie bei {@code BlockBefehle}.
     */
    private final java.util.function.Supplier<ZoneId> zone;

    private final Rechtehand rechtehand;

    private final Logger protokoll;

    TeamBefehle(Teambuch buch, Sprache sprache, Namenssuche suche, Namensgeber namen,
                Uhr uhr, java.util.function.Supplier<ZoneId> zone,
                Rechtehand rechtehand, Logger protokoll) {
        if (buch == null || sprache == null || suche == null || namen == null
                || uhr == null || zone == null || rechtehand == null
                || protokoll == null) {
            throw new IllegalArgumentException("/team braucht das Teambuch, Sprache,"
                    + " Namenssuche, Namensgeber, eine Uhr, die Zone, die Rechtehand"
                    + " und das Protokoll");
        }
        this.buch = buch;
        this.sprache = sprache;
        this.suche = suche;
        this.namen = namen;
        this.uhr = uhr;
        this.zone = zone;
        this.rechtehand = rechtehand;
        this.protokoll = protokoll;
    }

    /** Der Baum. */
    LiteralCommandNode<CommandSourceStack> team() {
        return Commands.literal(ARGUMENT)
                .requires(quelle -> quelle.getSender().hasPermission(RECHT))
                .executes(this::zeigeListe)
                .then(Commands.literal(ROLLEN)
                        .executes(this::zeigeRollen))
                .then(Commands.literal(ZEIGEN)
                        // Ein eigenes executes AUF DEM LITERAL, nicht nur auf dem
                        // Argument darunter: Sonst endet /team zeigen ohne Namen in
                        // Brigadiers englischem "Unknown or incomplete command", und der
                        // Betreiber liest eine rote Zeile statt eines Hinweises.
                        .executes(this::zeigeListe)
                        .then(Commands.argument("spieler", StringArgumentType.word())
                                .executes(this::zeigeSpieler)))
                .then(Commands.literal(GIB)
                        .requires(quelle -> quelle.getSender()
                                .hasPermission(RECHT_VERGEBEN))
                        .executes(this::zeigeRollen)
                        .then(Commands.argument("spieler", StringArgumentType.word())
                                .executes(this::zeigeRollen)
                                .then(rollenargument().executes(z -> gib(z, true)))))
                .then(Commands.literal(NIMM)
                        .requires(quelle -> quelle.getSender()
                                .hasPermission(RECHT_VERGEBEN))
                        .executes(this::zeigeListe)
                        .then(Commands.argument("spieler", StringArgumentType.word())
                                .executes(this::zeigeListe)
                                .then(rollenargument().executes(z -> gib(z, false)))))
                .build();
    }

    /**
     * Das Rollenargument mit Vervollstaendigung - EINE Stelle fuer gib und nimm.
     *
     * <p>Zweimal hingeschrieben waere zweimal die Gelegenheit, die Vervollstaendigung
     * beim naechsten Mal zu vergessen - und eine fehlende faellt nicht auf, sie macht nur
     * das Tippen muehsam.
     *
     * <p><b>Vervollstaendigt werden die Rollen der DATEI</b>, auch bei {@code nimm}. Das
     * ist bewusst nicht die Liste dessen, was der Spieler hat: Diese Liste braeuchte
     * einen Datenbankzugriff im Vervollstaendigungsfaden, und der laeuft nebenlaeufig zum
     * Hauptfaden. Eine weggefallene Rolle laesst sich dadurch nicht
     * vervollstaendigen - sie laesst sich aber eingeben, und genau das sagt die Antwort
     * von {@code Teambuch.nimm}.
     */
    private com.mojang.brigadier.builder.RequiredArgumentBuilder
            <CommandSourceStack, String> rollenargument() {
        return Commands.argument("rolle", StringArgumentType.word())
                .suggests((z, bauer) -> {
                    for (String rolle : buch.werte().schluessel()) {
                        bauer.suggest(rolle);
                    }
                    return bauer.buildFuture();
                });
    }

    /** {@code /team} - wer im Team ist. */
    private int zeigeListe(CommandContext<CommandSourceStack> z) {
        CommandSender wer = z.getSource().getSender();
        List<Component> zeilen = new ArrayList<>(
                sprache.bau(TeamSatz.KOPF, 1L, Einsatz.keine()));
        List<Teamstand> alle;
        try {
            alle = buch.alle();
        } catch (IllegalStateException gestoert) {
            protokoll.warning("Die Teamliste ist nicht lesbar: " + gestoert.getMessage());
            sende(wer, sprache.bau(TeamSatz.GESTOERT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        if (alle.isEmpty()) {
            zeilen.addAll(sprache.bau(TeamSatz.LEER, 1L, Einsatz.keine()));
            sende(wer, zeilen);
            return Command.SINGLE_SUCCESS;
        }
        for (Teamstand stand : alle) {
            zeilen.addAll(sprache.bau(TeamSatz.ZEILE, 1L,
                    Einsatz.von("spieler", namen.nameVon(stand.spieler()))
                            .mit("rollen", aufzaehlung(stand.rollen()))));
        }
        sende(wer, zeilen);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /team rollen} - was die Datei kennt. */
    private int zeigeRollen(CommandContext<CommandSourceStack> z) {
        Teamwerte werte = buch.werte();
        List<Component> zeilen = new ArrayList<>(
                sprache.bau(TeamSatz.ROLLEN_KOPF, 1L, Einsatz.keine()));
        if (!werte.greift()) {
            zeilen.addAll(sprache.bau(TeamSatz.ROLLEN_LEER, 1L, Einsatz.keine()));
            sende(z.getSource().getSender(), zeilen);
            return Command.SINGLE_SUCCESS;
        }
        for (Teamwerte.Rolle rolle : werte.rollen()) {
            zeilen.addAll(sprache.bau(TeamSatz.ROLLEN_ZEILE, rolle.rechte().size(),
                    Einsatz.von("rolle", rollenname(rolle.schluessel()))
                            .mit("rechte", rolle.rechte().size())));
        }
        sende(z.getSource().getSender(), zeilen);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /team zeigen <spieler>} - seine Rollen mit Zeit und Vergeber. */
    private int zeigeSpieler(CommandContext<CommandSourceStack> z) {
        CommandSender wer = z.getSource().getSender();
        String getippt = StringArgumentType.getString(z, "spieler");
        UUID kennung = suche.kennungVon(getippt);
        if (kennung == null) {
            sende(wer, sprache.bau(TeamSatz.UNBEKANNTER_SPIELER, 1L,
                    Einsatz.von("name", getippt)));
            return Command.SINGLE_SUCCESS;
        }
        Teamstand stand;
        try {
            stand = buch.stand(kennung);
        } catch (IllegalStateException gestoert) {
            protokoll.warning("Die Teamrollen von " + getippt + " sind nicht lesbar: "
                    + gestoert.getMessage());
            sende(wer, sprache.bau(TeamSatz.GESTOERT, 1L, Einsatz.keine()));
            return Command.SINGLE_SUCCESS;
        }
        String name = namen.nameVon(kennung);
        if (!stand.imTeam()) {
            sende(wer, sprache.bau(TeamSatz.ZEIGEN_LEER, 1L,
                    Einsatz.von("spieler", name)));
            return Command.SINGLE_SUCCESS;
        }
        Teamwerte werte = buch.werte();
        List<Component> zeilen = new ArrayList<>(sprache.bau(TeamSatz.ZEIGEN_KOPF, 1L,
                Einsatz.von("spieler", name)));
        for (Teamstand.Zeile zeile : stand.zeilen()) {
            zeilen.addAll(sprache.bau(TeamSatz.ZEIGEN_ZEILE, 1L,
                    Einsatz.von("rolle", rollenname(zeile.rolle()))
                            .mit("seit", sprache.muster(TeamSatz.ZEIT_MUSTER)
                                    .format(zeile.seit().atZone(zone.get())))
                            .mit("von", zeile.vergeber().map(namen::nameVon)
                                    .orElseGet(() -> sprache.wort(
                                            TeamSatz.VON_KONSOLE_WORT,
                                            Einsatz.keine())))));
            if (!werte.kennt(zeile.rolle())) {
                // DIE ZEILE, DIE DEN BEFEHL RECHTFERTIGT: Ein Moderator, der nichts
                // darf, und eine Auskunft, die seine Rolle brav auflistet, ist das
                // Fehlerbild, gegen das dieser Satz gebaut ist.
                zeilen.addAll(sprache.bau(TeamSatz.ZEIGEN_UNBEKANNT, 1L,
                        Einsatz.von("rolle", zeile.rolle())));
            }
        }
        sende(wer, zeilen);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /team gib} und {@code /team nimm} - EINE Methode fuer beide Richtungen.
     *
     * <p>Zusammen und nicht getrennt, weil alles davor und danach gleich ist: derselbe
     * Namensweg, dieselbe Rechtemenge, dasselbe Protokoll. Verschieden ist genau ein
     * Aufruf, und der steht in der Mitte. Zwei Methoden waeren zwei Orte, an denen man
     * das Protokoll vergisst.
     *
     * @param vergeben {@code true} fuer gib, {@code false} fuer nimm
     */
    private int gib(CommandContext<CommandSourceStack> z, boolean vergeben) {
        CommandSender wer = z.getSource().getSender();
        String getippt = StringArgumentType.getString(z, "spieler");
        String rolle = StringArgumentType.getString(z, "rolle");
        UUID kennung = suche.kennungVon(getippt);
        if (kennung == null) {
            // ERST der Name, dann die Rolle - dieselbe Reihenfolge wie bei
            // Betreibergeld.buche, und aus demselben Grund: Wer sich im Namen vertippt
            // UND in der Rolle, soll den Namen lesen. Jener ist der Fehler, der sonst
            // eine Rolle an einen Geisterspieler vergibt.
            sende(wer, sprache.bau(TeamSatz.UNBEKANNTER_SPIELER, 1L,
                    Einsatz.von("name", getippt)));
            return Command.SINGLE_SUCCESS;
        }
        Set<String> rechte = rechteVon(wer);
        Teambuch.Entscheid entscheid = vergeben
                ? buch.gib(kennung, rolle, uhr.jetzt(), kennungVon(wer), rechte)
                : buch.nimm(kennung, rolle, rechte);
        String name = namen.nameVon(kennung);
        if (entscheid.geaendert()) {
            protokoll.info(wer.getName() + " hat " + name + " die Teamrolle \"" + rolle
                    + "\" " + (vergeben ? "gegeben" : "genommen") + ".");
            // SOFORT und nicht erst beim naechsten Takt [behoben am 25.11.2026]: Die
            // Antwort darunter sagt GENOMMEN, und der Betreiber haelt den Moderator
            // fuer entwaffnet - vorher behielt der seine Rechte bis zum naechsten
            // Taktschlag, ab Werk eine Minute und erlaubt bis zehn. In diesem Fenster
            // konnte er weiter bannen.
            rechtehand.setzeNeu(kennung);
        }
        sende(wer, meldung(entscheid, name, rolle));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Was der Betreiber liest - je Bescheid eine Antwort.
     *
     * <p>Ein Schalter ohne {@code default}: Wer dem {@link Teambuch.Bescheid} einen
     * neunten Wert hinzufuegt, wird vom Uebersetzer gefragt, was der Betreiber dann
     * liest. Eine Vorgabe daneben waere ein Ausgang, der stumm bleibt.
     */
    private List<Component> meldung(Teambuch.Entscheid entscheid, String name,
                                    String getippt) {
        String rolle = entscheid.rolle().map(gefunden
                -> rollenname(gefunden.schluessel())).orElse(getippt);
        Einsatz beide = Einsatz.von("spieler", name).mit("rolle", rolle);
        return switch (entscheid.bescheid()) {
            case VERGEBEN -> sprache.bau(TeamSatz.VERGEBEN, 1L, beide);
            case HATTE_SCHON -> sprache.bau(TeamSatz.HATTE_SCHON, 1L, beide);
            // Eine Rolle, die die Datei kennt, und eine weggefallene sind dasselbe
            // Ergebnis aus zwei Gruenden. Der Betreiber soll lesen, dass er einen
            // ALTBESTAND aufgeraeumt hat - sonst sucht er die Rolle in seiner Datei.
            case GENOMMEN -> sprache.bau(entscheid.rolle().isPresent()
                    ? TeamSatz.GENOMMEN : TeamSatz.GENOMMEN_UNBEKANNT, 1L, beide);
            case HATTE_NICHT -> sprache.bau(TeamSatz.HATTE_NICHT, 1L, beide);
            case UNBEKANNTE_ROLLE -> sprache.bau(TeamSatz.UNBEKANNTE_ROLLE, 1L,
                    Einsatz.von("rolle", getippt)
                            .mit("rollen", aufzaehlung(buch.werte().schluessel())));
            case ZU_WENIG_RECHTE -> sprache.bau(TeamSatz.ZU_WENIG_RECHTE, 1L,
                    Einsatz.von("rolle", rolle)
                            .mit("fehlende", String.join(", ", entscheid.fehlende())));
            case KEIN_TEAM -> sprache.bau(TeamSatz.KEIN_TEAM, 1L, Einsatz.keine());
            case GESTOERT -> sprache.bau(TeamSatz.GESTOERT, 1L, Einsatz.keine());
        };
    }

    /**
     * Die Rechte des Absenders - oder {@code null} fuer die Konsole.
     *
     * <p><b>Gelesen wird, was Bukkit WIRKLICH haelt</b>, und nicht, was ein Rang oder
     * eine Rolle geben wuerde: {@code getEffectivePermissions} nennt auch die Rechte aus
     * der Rechteverwaltung und die eines Operators. Eine eigene Rechnung hier waere eine
     * zweite Wahrheit darueber, was jemand darf - und zwar genau an der Stelle, an der
     * entschieden wird, was er weitergeben darf.
     *
     * <p>Nur die ZUGESPROCHENEN kommen in die Menge. Ein {@code PermissionAttachmentInfo}
     * kann ein Recht auch auf {@code false} setzen, und ein so verweigertes zaehlte sonst
     * als vorhanden - der Riegel liesse dann eine Rolle durch, deren Recht der Vergebende
     * ausdruecklich nicht hat.
     */
    private static Set<String> rechteVon(CommandSender wer) {
        if (!(wer instanceof Player spieler)) {
            // null und nicht die leere Menge: Das ist DIE KONSOLE, und sie darf alles.
            // Eine leere Menge waere ein Spieler ohne jedes Recht - der Unterschied ist
            // der ganze Riegel, siehe Teambuch.
            return null;
        }
        Set<String> rechte = new LinkedHashSet<>();
        spieler.getEffectivePermissions().forEach(angabe -> {
            if (angabe.getValue()) {
                rechte.add(angabe.getPermission());
            }
        });
        return rechte;
    }

    /** Die Kennung des Absenders - {@code null} fuer die Konsole. */
    private static UUID kennungVon(CommandSender wer) {
        return wer instanceof Player spieler ? spieler.getUniqueId() : null;
    }

    /**
     * Der Name einer Rolle aus der Sprachdatei.
     *
     * <p>Fehlt die Zeile, gibt {@code Sprachbuch} den PFAD zurueck statt zu werfen - das
     * tut es fuer jede offene Satzfamilie, und es ist der Grund, aus dem ein fehlender
     * Rollenname den Start nicht verweigert (siehe {@link Teamrolle}). Eine
     * Verwaltungsliste, in der {@code team.rolle.moderator} steht, ist haesslich und
     * vollstaendig verstaendlich; ein Server, der deswegen nicht startet, ist keines von
     * beidem.
     */
    private String rollenname(String schluessel) {
        return sprache.wort(new Teamrolle(schluessel), Einsatz.keine());
    }

    /** Rollennamen als EINE Aufzaehlung, mit dem Trenner aus der Sprachdatei. */
    private String aufzaehlung(List<String> schluessel) {
        List<String> namen = new ArrayList<>(schluessel.size());
        for (String eines : schluessel) {
            namen.add(rollenname(eines));
        }
        return String.join(sprache.wort(TeamSatz.TRENNER_WORT, Einsatz.keine()), namen);
    }

    /** Die EINE Sendestelle dieser Klasse. */
    private void sende(Audience an, List<Component> zeilen) {
        for (Component zeile : zeilen) {
            an.sendMessage(zeile);
        }
    }
}
