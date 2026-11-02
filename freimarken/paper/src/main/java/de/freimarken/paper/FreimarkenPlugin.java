package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.daten.Datenbank;
import de.freimarken.daten.MariaKasse;
import de.freimarken.daten.MariaLager;
import de.freimarken.kern.BesitzLager;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Chunkfeld;
import de.freimarken.kern.Grenzregel;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Rasterplan;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Stadtgrenze;
import de.freimarken.kern.Warpverzeichnis;
import de.freimarken.kern.ZwischenspeicherLager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.Set;
import java.util.TreeSet;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Einstiegspunkt des Freimarken-Plugins.
 *
 * <p>Stand: die Marktstadt wird beim Start erzeugt oder geladen, und zwar IMMER mit dem
 * eigenen Generator. Das ist kein Schoenheitsfehler, sondern Pflicht: Ein Plugin-Generator
 * wird nicht in der Weltdatei gespeichert. Wuerde die Welt einmal ohne ihn geladen, schriebe
 * der Server beim naechsten neuen Chunk Vanille-Gelaende an den Parzellenrand - und das ist
 * ohne Ruecksicherung nicht mehr zu reparieren.
 *
 * <p>Zur Ablage gibt es zwei Zustaende und keinen dritten: Mit Datenbank laeuft alles, ohne
 * Datenbank laeuft es fluechtig und sagt das laut. Ist eine Datenbank eingetragen, antwortet
 * aber nicht, startet das Plugin NICHT. Ein Server, der Parzellen vergibt und beim Neustart
 * vergisst, waere schlimmer als einer, der gar nicht hochkommt.
 */
public final class FreimarkenPlugin extends JavaPlugin {

    /**
     * Was ohne Eintrag in der Konfiguration unter einer Rangabsage steht.
     *
     * <p>Hier und nicht nur in der config.yml: {@code saveDefaultConfig()} schreibt die
     * Datei nur, wenn es sie noch nicht gibt. Auf einem Server, der schon laeuft, fehlt
     * der neue Eintrag also - und mit einer leeren Vorgabe bekaeme ausgerechnet der gar
     * keinen Hinweis. Der Satz sagt nur, was ohnehin wahr ist; wie es auf DIESEM Server
     * weitergeht, traegt der Betreiber in die config.yml ein.
     */
    private static final String WERBUNG_VORGABE = "Mehr erlaubt ein hoeherer Rang.";

    /** Der Name, unter dem die Marktstadt in der Weltenliste steht. */
    private static final String MARKTSTADT = "marktstadt";

    /**
     * Die Welt, in der die Tore stehen und ein neuer Spieler ankommt.
     *
     * <p>Rueckfall, wenn {@code reisen.ankunftswelt} fehlt. Plan Abschnitt 2:
     * „Hauptstadt (Welt mit Spawn) = Altfurt".
     */
    private static final String ANKUNFTSWELT_VORGABE = "altfurt";

    private final Rasterplan plan = Rasterplan.marktstadt();
    private final MarktstadtGenerator generator = new MarktstadtGenerator(plan);

    private World marktstadt;
    private Datenbank datenbank;
    private BesitzLager lager;
    private Kasse kasse;
    private Stadtgrenze grenze;
    private Besitzbuch buch;
    private ParzellenBefehle befehle;
    private WarpBefehle warpBefehle;
    private boolean fluechtig;
    /**
     * Alle gebauten Welten unter ihrem Namen.
     *
     * <p>Nicht nur die Marktstadt: Vorgenerierung und Reisen betreffen jede Welt, und ein
     * zweites Mal {@code getWorld} zu fragen hiesse, den Schluessel noch einmal
     * zusammenzusetzen - zwei Angaben, die auseinanderlaufen koennen.
     */
    private final Map<String, World> welten = new LinkedHashMap<>();

    /** Die Eintraege aus der Datei, unter demselben Namen wie {@link #welten}. */
    private final Map<String, Weltangabe> angaben = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Die Ablage ZUERST. Andersherum stand die Marktstadt schon geladen da, wenn
        // sich das Plugin wegen einer unerreichbaren Datenbank abschaltet - mit ihr eine
        // Welt voller Parzellen und ohne jede Schutzwache, denn die wird erst weiter unten
        // angemeldet. Wer zuletzt dort ausgeloggt war, erschiene beim Einloggen genau dort
        // und duerfte ueberall bauen. Jetzt entsteht im Fehlerfall gar keine Welt.
        if (!richteAblageEin()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Welten aus der Liste, nicht aus dem Quelltext (Plan 4.2). Die Klagen zuerst
        // und einzeln: Wer eine Welt falsch eingetragen hat, soll genau lesen, welche und
        // warum - die uebrigen entstehen trotzdem.
        String ankunftswelt = getConfig()
                .getString("reisen.ankunftswelt", ANKUNFTSWELT_VORGABE).trim();
        var liste = Weltenliste.ausKonfiguration(
                getConfig().getConfigurationSection("welten"), MARKTSTADT, ankunftswelt);
        liste.klagen().forEach(getLogger()::severe);
        var gebaut = new Weltenbau(this, generator, getLogger()).baueAlle(liste.welten());
        welten.putAll(gebaut.welten());
        // Die Angaben mit, nicht nur die Welten: Spielmodus und Tor stehen dort, und ein
        // zweites Lesen der config.yml waere eine zweite Wahrheit.
        liste.welten().forEach(angabe -> angaben.put(angabe.name(), angabe));
        marktstadt = gebaut.welten().get(MARKTSTADT);
        // Dieselbe Angabe, aus der die Welt entstand, entscheidet auch ueber ihre Grenze.
        // Vorher las richteWeltgrenzeEin die Einstellung ein zweites Mal und ungefiltert
        // aus der config.yml - mit einem anderen Rueckfall als Weltenliste. Bei einer
        // vertippten Kante standen dann zwei severe-Zeilen desselben Starts untereinander,
        // die sich widersprachen: "entsteht ohne Grenze" und "Weltgrenze 2989".
        Weltangabe stadtangabe = liste.welten().stream()
                .filter(angabe -> angabe.name().equals(MARKTSTADT))
                .findFirst()
                .orElse(null);
        if (marktstadt == null || stadtangabe == null) {
            getLogger().severe("Ohne Marktstadt gibt es nichts zu verwalten - Freimarken"
                    + " bleibt aus. Erwartet wird ein Eintrag welten." + MARKTSTADT
                    + " mit art: raster.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (gebaut.neu().contains(MARKTSTADT)) {
            // Nur bei einer frisch angelegten Welt: Sonst ueberschriebe jeder Start, was
            // der Betreiber mit /setworldspawn eingestellt hat.
            marktstadt.setSpawnLocation(plan.mitteX(0), Stadtboden.BODEN + 1, plan.mitteZ(0));
            getLogger().info("Erscheinungspunkt der Marktstadt gesetzt: " + plan.mitteX(0)
                    + "/" + (Stadtboden.BODEN + 1) + "/" + plan.mitteZ(0)
                    + " - die Mitte von Parzelle 0/0.");
        }

        // Erst die Grenze, dann das Besitzbuch: Die Grenze entscheidet, welche
        // Parzellennummern es ueberhaupt gibt, und sie muss stehen, bevor der erste Befehl
        // angemeldet wird - die Brigadier-Grenzen von /freimarken parzelle kommen aus ihr.
        // Der Aufruf steht hier und nicht in ladeMarktstadt(), weil jene Methode vorzeitig
        // zurueckkehrt, wenn die Welt schon geladen ist; dann stuende die Grenze auf dem
        // Vorgabewert einer frischen Welt und waere faktisch keine.
        grenze = richteWeltgrenzeEin(marktstadt, stadtangabe);
        buch = new Besitzbuch(grenze, lager, kasse);

        // Der Schluesseltext, nicht der NamespacedKey: World.key() gibt ein Key zurueck,
            // und ein Vergleich zwischen beiden ist immer falsch, ohne dass es auffaellt.
        String welt = marktstadt.key().asString();
        // Die WELT und nicht nur ihr Schluessel: Seit das Zusammenlegen den Weg fuellt,
        // setzen die Befehle Bloecke. Den Schluesseltext holt sich ParzellenBefehle aus
        // derselben Welt - zwei Angaben koennten auseinanderlaufen.
        befehle = new ParzellenBefehle(buch, marktstadt,
                getConfig().getInt("parzellen.hoechstzahl", 4),
                getConfig().getInt("parzellen.gruppe-hoechstzahl", 4),
                getConfig().getString("hinweise.rang-werbung", WERBUNG_VORGABE),
                getLogger());
        getServer().getPluginManager().registerEvents(new Schutzwache(buch, welt), this);
        richteReisenEin(ankunftswelt);
        registriereBefehle();

        getLogger().info("Freimarken " + getPluginMeta().getVersion()
                + " bereit - Java " + Runtime.version().feature()
                + ", Ablage " + (fluechtig ? "FLUECHTIG" : "MariaDB"));

        nenneNichtVorgenerierte();
        nenneSchlechteAnkunft();
    }

    @Override
    public void onDisable() {
        if (datenbank != null) {
            datenbank.close();
            datenbank = null;
        }
    }

    /**
     * Baut Lager und Kasse auf.
     *
     * @return {@code false}, wenn eine eingetragene Datenbank nicht erreichbar ist - dann
     *         soll das Plugin nicht starten
     */
    private boolean richteAblageEin() {
        String url = getConfig().getString("datenbank.url", "").trim();
        long startguthaben = getConfig().getLong("kasse.startguthaben", 50_000L);

        if (url.isEmpty()) {
            fluechtig = true;
            lager = new SpeicherLager();
            kasse = new SpeicherKasse(startguthaben);
            getLogger().warning("Keine Datenbank eingetragen: Parzellen und Guthaben gelten "
                    + "nur bis zum naechsten Neustart.");
            getLogger().warning("Zum Festhalten datenbank.url in plugins/"
                    + getName() + "/config.yml ausfuellen.");
            return true;
        }

        try {
            datenbank = new Datenbank(url,
                    getConfig().getString("datenbank.benutzer", ""),
                    getConfig().getString("datenbank.passwort", ""));
            datenbank.migriere();
            // ZwischenspeicherLager um das MariaLager: darfBauen laeuft bei jedem gesetzten
            // und abgebauten Block, und das darf keine Abfrage ueber das Netz werden.
            lager = new ZwischenspeicherLager(new MariaLager(datenbank));
            kasse = new MariaKasse(datenbank, startguthaben);
            getLogger().info("Datenbank bereit: " + ohneGeheimnis(url));
            return true;
        } catch (SQLException | RuntimeException fehler) {
            getLogger().severe("Die Datenbank ist eingetragen, antwortet aber nicht: "
                    + fehler.getMessage());
            getLogger().severe("Freimarken startet nicht. Lieber kein Server als einer, der "
                    + "Parzellen vergibt und beim Neustart vergisst.");
            if (datenbank != null) {
                datenbank.close();
                datenbank = null;
            }
            return false;
        }
    }

    /**
     * Die Adresse ohne alles hinter dem Fragezeichen.
     *
     * <p>Dort koennen Benutzer und Passwort stehen, und das Protokoll liest spaeter jemand,
     * der es nicht lesen soll.
     */
    private static String ohneGeheimnis(String url) {
        int fragezeichen = url.indexOf('?');
        return fragezeichen < 0 ? url : url.substring(0, fragezeichen);
    }

    /**
     * Der Generator gilt auch, wenn die Welt ueber die bukkit.yml geladen wird.
     *
     * <p>Zweiter Riegel gegen denselben Unfall: Wer die Welt dort eintraegt, bekommt
     * trotzdem das Raster und nicht Vanille-Gelaende.
     */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String weltName, String kennung) {
        // Nur fuer die Marktstadt. Frueher gab diese Methode das Raster fuer JEDEN Namen
        // zurueck - solange es eine einzige Welt gab, fiel das nicht auf. Seit es mehrere
        // gibt, haette eine Welt, die dieses Plugin in der bukkit.yml als Generator nennt,
        // Parzellenmauern statt Gelaende bekommen.
        return gehoertZurMarktstadt(weltName) ? generator : null;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(String weltName, String kennung) {
        return gehoertZurMarktstadt(weltName) ? generator.getDefaultBiomeProvider(null) : null;
    }

    /**
     * Ob dieser Weltname die Marktstadt meint.
     *
     * <p>Mit {@code endsWith} und nicht mit {@code equals}: Bukkit reicht hier je nach Weg
     * den blossen Namen oder den Ordnerpfad herein, und seit Paper 26.1 liegt eine zur
     * Laufzeit erzeugte Welt unter {@code dimensions/freimarken/marktstadt}.
     */
    private static boolean gehoertZurMarktstadt(String weltName) {
        if (weltName == null) {
            return false;
        }
        String klein = weltName.toLowerCase(java.util.Locale.ROOT);
        // Kein blosses endsWith: Eine Welt namens "nebenmarktstadt" bekaeme sonst das
        // Parzellenraster - und zwar ueber die bukkit.yml, an Weltenliste vorbei. Also
        // entweder genau der Name oder der letzte Teil eines Pfades.
        return klein.equals(MARKTSTADT) || klein.endsWith("/" + MARKTSTADT)
                || klein.endsWith("\\" + MARKTSTADT);
    }

    private void registriereBefehle() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, ereignis -> {
            var verzeichnis = ereignis.registrar();
            Set<String> angemeldet = new TreeSet<>();
            angemeldet.addAll(verzeichnis.register(freimarken(),
                    "Stand, Preise und Welt des Freimarkenbunds", List.of("fm")));
            // /p und /konto stehen so im Plan (Abschnitt 11, [entschieden]); /pz bleibt
            // als zweite Kurzform, weil sie schon im Umlauf ist.
            angemeldet.addAll(verzeichnis.register(befehle.parzelle(),
                    "Parzellen nehmen, ansehen und verwalten", List.of("p", "pz")));
            angemeldet.addAll(verzeichnis.register(befehle.mark(),
                    "Dein Guthaben in Mark", List.of("konto")));
            angemeldet.addAll(verzeichnis.register(warpBefehle.warp(),
                    "Reisen zwischen den Welten des Freimarkenbunds"));

            // Diese Zeile ist nicht Zierde. /parzelle und /mark sind spielerpflichtig, und
            // auf der Konsole antwortet ein spielerpflichtiger Befehl wortgleich wie ein
            // Befehl, den es nicht gibt. Ohne diese Ausgabe laesst sich von aussen nicht
            // feststellen, ob die Anmeldung geklappt hat - und sie passiert hier, in einem
            // spaeteren Ereignis, nicht in onEnable, also beweist die Bereitmeldung es auch
            // nicht.
            getLogger().info("Befehle angemeldet: " + String.join(", ", angemeldet));
        });
    }

    /** Wer nachsehen und sich versetzen darf - nicht jeder Spieler. */
    private static final String RECHT_VERWALTEN = "freimarken.verwalten";

    private LiteralCommandNode<CommandSourceStack> freimarken() {
        return Commands.literal("freimarken")
                .executes(z -> {
                    zeigeStand(z.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("preis")
                        .then(Commands.argument("besessen",
                                        IntegerArgumentType.integer(0, 64))
                                .executes(z -> {
                                    zeigePreis(z.getSource().getSender(),
                                            IntegerArgumentType.getInteger(z, "besessen"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                // Rechte je Zweig: Stand und Preise sind fuer alle, Nachsehen und
                // Versetzen nicht. Ohne das hatte jeder Spieler einen kostenlosen Teleport
                // quer durch die Marktstadt - und das unterlaeuft die Rangleiter, an der
                // Heimatpunkte und Warps spaeter haengen sollen, bevor sie gebaut ist.
                .then(Commands.literal("welt")
                        .requires(quelle -> quelle.getSender().hasPermission(RECHT_VERWALTEN))
                        .executes(z -> {
                            zeigeWelt(z.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("vorgenerieren")
                        .requires(quelle -> quelle.getSender().hasPermission(RECHT_VERWALTEN))
                        .executes(z -> {
                            zeigeVorgenerierung(z.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("parzelle")
                        .requires(quelle -> quelle.getSender().hasPermission(RECHT_VERWALTEN))
                        // Die Grenzen kommen aus der Stadt, nicht aus einer geratenen
                        // Zahl: mitteX(64) laege 3.085 Bloecke jenseits der Weltgrenze.
                        .then(Commands.argument("x",
                                        IntegerArgumentType.integer(grenze.von(), grenze.bis()))
                                .then(Commands.argument("z",
                                                IntegerArgumentType.integer(grenze.von(),
                                                        grenze.bis()))
                                        .executes(z -> {
                                            bringeZurParzelle(z.getSource().getSender(),
                                                    IntegerArgumentType.getInteger(z, "x"),
                                                    IntegerArgumentType.getInteger(z, "z"));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build();
    }

    private void zeigeStand(Audience empfaenger) {
        empfaenger.sendMessage(Component.text("Der Freimarkenbund", NamedTextColor.GOLD));
        empfaenger.sendMessage(Component.text(
                "Stand " + getPluginMeta().getVersion()
                        + " · Java " + Runtime.version().feature()
                        + " · " + getServer().getMinecraftVersion(),
                NamedTextColor.GRAY));
        empfaenger.sendMessage(fluechtig
                ? Component.text("Ablage fluechtig - nichts ueberlebt den Neustart",
                        NamedTextColor.RED)
                : Component.text("Ablage MariaDB", NamedTextColor.DARK_GRAY));
        empfaenger.sendMessage(Component.text(
                "/parzelle · /mark · /freimarken preis <anzahl>", NamedTextColor.GRAY));
        if (empfaenger instanceof Permissible darf
                && darf.hasPermission(RECHT_VERWALTEN)) {
            empfaenger.sendMessage(Component.text(
                    "/freimarken welt · parzelle <x> <z>", NamedTextColor.DARK_GRAY));
        }
    }

    private void zeigePreis(Audience empfaenger, int besessen) {
        long preis = Preisstaffel.preisFuerNaechste(besessen);
        empfaenger.sendMessage(preis == 0L
                ? Component.text("Die erste Parzelle ist gratis.", NamedTextColor.GREEN)
                : Component.text("Parzelle Nr. " + (besessen + 1) + " kostet ", NamedTextColor.GRAY)
                        .append(Component.text(Betraege.mark(preis), NamedTextColor.GOLD))
                        .append(Component.text(".", NamedTextColor.GRAY)));
        empfaenger.sendMessage(Component.text(
                "Bisher ausgegeben: " + Betraege.mark(Preisstaffel.gesamtpreisFuer(besessen)),
                NamedTextColor.DARK_GRAY));
    }

    /**
     * Setzt die Weltgrenze und gibt zurueck, welche Stadt ab jetzt gilt.
     *
     * <p>Laeuft auf jedem Weg, auf dem die Welt in den Server kommt, und vor jeder
     * Vorgenerierung - "erst Grenze setzen, dann vorgenerieren" (Plan Abschnitt 4).
     *
     * <p>Die Grenze wird bei jedem Start neu gesetzt. Das heilt auch den Fall, dass die
     * Datei verloren geht, in der sie steht: Sie liegt nicht in der level.dat, sondern
     * unter dimensions/freimarken/marktstadt/data/minecraft/world_border.dat - ein
     * Sicherungsmuster, das sie dort nicht sucht, findet sie nicht.
     */
    private Stadtgrenze richteWeltgrenzeEin(World welt, Weltangabe angabe) {
        int ausEinstellung = angabe.kante();
        if (!angabe.hatGrenze()) {
            // Die Marktstadt ist die eine Welt, die ohne Grenze nicht auskommt: Aus ihr
            // kommen die Parzellennummern. Ohne Eintrag gilt deshalb der Plan, nicht die
            // Grenzenlosigkeit - und das steht hier, damit es im Protokoll steht.
            ausEinstellung = Stadtgrenze.MARKTSTADT_KANTE;
            getLogger().info("welten." + MARKTSTADT + ".kante gilt nicht - die Marktstadt"
                    + " braucht eine Grenze, es gilt der Planwert " + ausEinstellung + ".");
        }
        WorldBorder rand = welt.getWorldBorder();
        double inDerWelt = rand.getSize();
        boolean bestaetigt = Stadtgrenze.sprungText(ausEinstellung, inDerWelt)
                .equals(angabe.sprungBestaetigt());

        int wirksam = Stadtgrenze.wirksameKante(plan, ausEinstellung, inDerWelt, bestaetigt);
        Stadtgrenze neu = Stadtgrenze.ausKante(plan, wirksam);
        String klage = Grenzregel.klage("welten." + MARKTSTADT, ausEinstellung, inDerWelt,
                wirksam, Stadtgrenze.mindestKante(plan));
        if (klage != null) {
            getLogger().severe(klage);
        }
        rand.setCenter(neu.mitte(), neu.mitte());
        rand.setSize(neu.kante());
        // Genau der aeussere Mauer-Weg-Ring wird rot umrandet, nicht bebaubares Land.
        rand.setWarningDistance(plan.wegBreite());
        // Eine Mauer, keine Falle: Wer heute weit draussen ausloggt, soll beim naechsten
        // Einloggen nicht am Grenzschaden sterben.
        rand.setDamageAmount(0.0);
        getLogger().info("Weltgrenze " + (long) neu.kante() + " um " + neu.mitte() + "/"
                + neu.mitte() + " - Parzellen " + neu.von() + ".." + neu.bis()
                + " je Achse, " + neu.parzellenZahl() + " Stueck");
        return neu;
    }

    /**
     * Zeigt den Zustand der Welt - und liest dafuer echte Bloecke aus.
     *
     * <p>Absichtlich keine Berechnung: Der Rasterplan wuerde dieselbe Antwort liefern, ohne
     * dass je ein Chunk erzeugt wurde. Gefragt ist hier aber, was WIRKLICH in der Welt
     * steht.
     */
    private void zeigeWelt(Audience empfaenger) {
        if (marktstadt == null) {
            empfaenger.sendMessage(Component.text("Die Marktstadt fehlt.", NamedTextColor.RED));
            return;
        }
        empfaenger.sendMessage(Component.text("Marktstadt", NamedTextColor.GOLD));
        empfaenger.sendMessage(Component.text(
                "Schluessel " + marktstadt.key().asString()
                        + " · Raster " + plan.parzellenKante() + "+" + plan.wegBreite()
                        + " · Boden y=" + Stadtboden.BODEN,
                NamedTextColor.GRAY));

        WorldBorder rand = marktstadt.getWorldBorder();
        empfaenger.sendMessage(Component.text(
                "Grenze gemessen: Kante " + (long) rand.getSize() + " um "
                        + rand.getCenter().getX() + "/" + rand.getCenter().getZ(),
                NamedTextColor.GRAY));
        empfaenger.sendMessage(Component.text(
                "Grenze gerechnet: Parzellen " + grenze.von() + ".." + grenze.bis()
                        + " je Achse \u00b7 " + grenze.parzellenZahl() + " Stueck",
                NamedTextColor.GRAY));

        int y = Stadtboden.BODEN;
        probe(empfaenger, "Parzellenmitte", plan.mitteX(0), y, plan.mitteZ(0));
        probe(empfaenger, "Parzellenrand ", plan.parzellenKante() - 1, y, 10);
        probe(empfaenger, "Mauer         ", plan.parzellenKante(), y, 10);
        probe(empfaenger, "Weg           ", plan.parzellenKante() + 3, y, 10);
        probe(empfaenger, "Mauerkrone    ", plan.parzellenKante(), y + 1, 10);
    }

    /**
     * Richtet das Reisen ein - beide Wege aus Plan 4.3 und die Sperre fuer den dritten.
     *
     * <p>Die Respawn-Welt wird HIER gesetzt und nicht weiter oben: {@code setRespawnWorld}
     * sucht die Dimension, und vor dem Weltenbau faellt diese Suche auf die Ueberwelt
     * zurueck. Der Aufruf ist ausserdem dauerhaft - er schreibt in die level.dat der
     * Hauptwelt. Deshalb steht die vorige Welt im Protokoll: Ohne sie waere nicht mehr
     * festzustellen, worauf man zurueckstellen muesste.
     */
    private void richteReisenEin(String ankunftsweltName) {
        Reise reise = new Reise(getLogger());
        var verzeichnis = Warpverzeichnis.ausWelten(List.copyOf(welten.keySet()));
        warpBefehle = new WarpBefehle(verzeichnis, welten, reise);

        Set<String> eigene = welten.values().stream()
                .map(w -> w.key().asString())
                .collect(java.util.stream.Collectors.toSet());
        getServer().getPluginManager().registerEvents(new Portalwache(eigene), this);

        World ankunftswelt = welten.get(ankunftsweltName);
        if (ankunftswelt == null) {
            if (!ankunftsweltName.isEmpty()) {
                getLogger().severe("reisen.ankunftswelt nennt " + ankunftsweltName
                        + ", aber diese Welt gibt es nicht. Es gibt keine Tore, und ein"
                        + " neuer Spieler erscheint da, wo der Server ihn hinsetzt.");
            }
            return;
        }
        if (!ankunftswelt.equals(getServer().getRespawnWorld())) {
            World vorher = getServer().getRespawnWorld();
            getServer().setRespawnWorld(ankunftswelt);
            getLogger().info("Respawn-Welt war "
                    + (vorher == null ? "nicht gesetzt" : vorher.key().asString())
                    + ", ist jetzt " + ankunftswelt.key().asString()
                    + " - hier kommt an, wer neu ist oder ohne Bett stirbt. Die Angabe"
                    + " bleibt in der Weltdatei stehen; zum Zuruecknehmen die vorige Welt"
                    + " in reisen.ankunftswelt eintragen.");
        }
        // Nach dem Schluesseltext und nicht nach dem Namen aus der Datei: Eine Hauptwelt
        // des Servers, die zufaellig "altfurt" heisst, traegt sonst Altfurts Spielmodus.
        Map<String, Weltangabe> nachSchluessel = new LinkedHashMap<>();
        welten.forEach((name, w) -> {
            Weltangabe angabe = angaben.get(name);
            if (angabe != null) {
                nachSchluessel.put(w.key().asString(), angabe);
            }
        });
        getServer().getPluginManager().registerEvents(
                new Ankunftswache(ankunftswelt, nachSchluessel, getLogger()), this);

        var torwache = new Torwache(ankunftswelt, welten,
                List.copyOf(angaben.values()), reise);
        if (torwache.hatTore()) {
            getServer().getPluginManager().registerEvents(torwache, this);
            long tore = angaben.values().stream().filter(Weltangabe::hatTor).count();
            getLogger().info(tore + " Tore in " + ankunftswelt.key().asString() + ".");
            pruefeTore(ankunftswelt);
        } else {
            getLogger().info("Keine Tore eingetragen - gereist wird nur mit /warp.");
        }
    }

    /**
     * Prueft, ob durch jedes Tor ueberhaupt jemand gehen kann.
     *
     * <p>Noetig, weil die Torquader Zahlen in einer Datei sind und das Gelaende nicht.
     * Die mitgelieferten Werte sind am 02.11.2026 an EINEM Altfurt eingemessen; ein
     * anderer Startwert, ein anderes Gelaende, und die Tore liegen im Berg oder in der
     * Luft. Beides merkt man ohne diese Pruefung erst, wenn jemand durchgehen will und
     * nichts passiert.
     *
     * <p>Ein Tor ist ein <b>Loch in einer Wand</b>, und das ist der Grund, warum hier
     * nicht die Standprobe aus {@link Ankunftspunkt} laeuft: Die sucht den hoechsten
     * festen Block einer Saeule und findet damit den STURZ ueber der Oeffnung, nicht den
     * Boden darin. Am 02.11.2026 hat sie deshalb alle sieben Tore einer gerade gebauten
     * Wand fuer unbegehbar erklaert.
     *
     * <p>Gefragt ist also zweierlei, und zwar fuer jede Saeule der Oeffnung: traegt der
     * Block unter dem Tor, und ist das Tor selbst in Fuss- und Kopfhoehe frei? Liegt es
     * daneben, sucht die Meldung den Boden und nennt die passenden Zahlen zum Abtippen.
     */
    private void pruefeTore(World ankunftswelt) {
        var griff = Ankunftspunkt.griffAuf(ankunftswelt);
        for (Weltangabe angabe : angaben.values()) {
            if (!angabe.hatTor()) {
                continue;
            }
            String klage = torklage(griff, angabe.tor());
            if (klage != null) {
                getLogger().severe("Tor nach " + angabe.name() + " ("
                        + angabe.tor().alsText() + "): " + klage);
            }
        }
    }

    /** Was an diesem Tor nicht stimmt - oder {@code null}, wenn es begehbar ist. */
    private String torklage(Ankunftspunkt.Weltgriff griff, Quader tor) {
        for (int x = tor.vonX(); x <= tor.bisX(); x++) {
            for (int z = tor.vonZ(); z <= tor.bisZ(); z++) {
                if (!griff.fest(x, tor.vonY() - 1, z)) {
                    return "unter " + x + "/" + tor.vonY() + "/" + z + " ist kein Boden -"
                            + " wer hier durchgeht, faellt. " + vorschlag(griff, tor, x, z);
                }
                for (int hoehe = 0; hoehe <= 1 && tor.vonY() + hoehe <= tor.bisY(); hoehe++) {
                    int y = tor.vonY() + hoehe;
                    if (griff.fest(x, y, z)) {
                        // Hier KEIN Vorschlag mit Koordinaten: Der Boden darunter traegt
                        // ja, also nennt die Suche dieselbe Hoehe wieder - ein Rat, der
                        // ins Leere fuehrt. Zu tun ist etwas anderes.
                        return "bei " + x + "/" + y + "/" + z + " steht "
                                + griff.bei(x, y, z) + " im Tor. Die Oeffnung muss frei"
                                + " sein - entweder die Bloecke entfernen oder den Quader"
                                + " dorthin legen, wo der Durchgang ist.";
                    }
                    if (Ankunftspunkt.schaedlich(griff.bei(x, y, z))) {
                        return "bei " + x + "/" + y + "/" + z + " steht "
                                + griff.bei(x, y, z) + " im Tor.";
                    }
                }
            }
        }
        return null;
    }

    /**
     * Welche Hoehe das Tor haette haben sollen - gesucht von oben nach unten.
     *
     * <p>Von oben, damit eine Wand ueber dem Tor nicht als Boden durchgeht: Gesucht ist
     * der erste feste Block UNTER der eingetragenen Fusshoehe.
     */
    private String vorschlag(Ankunftspunkt.Weltgriff griff, Quader tor, int x, int z) {
        for (int y = tor.vonY(); y > ankunftsTiefe(); y--) {
            if (griff.fest(x, y - 1, z)) {
                return "Passend waere: von [" + tor.vonX() + ", " + y + ", " + tor.vonZ()
                        + "] bis [" + tor.bisX() + ", " + (y + 2) + ", " + tor.bisZ() + "]";
            }
        }
        return "Unter dem Tor ist bis zum Weltboden nichts.";
    }

    /** Wie weit die Suche nach unten geht - tiefer gibt es keine Welt. */
    private static int ankunftsTiefe() {
        return -64;
    }

    /**
     * Nennt die Welten, in deren Erscheinungspunkt niemand stehen kann.
     *
     * <p>Und aendert ihn NICHT. Der Erscheinungspunkt ist die Entscheidung des Betreibers
     * und nach Plan 4.3 die einzige Wahrheit ueber den Ankunftsort; ein Programm, das ihn
     * verschiebt, verschiebt bei jedem Start, wo alle ankommen - unumkehrbar, weil der
     * alte Punkt danach nirgends mehr steht. Die Schranke ist hier die Hand des
     * Betreibers: eine Zeile zum Kopieren.
     */
    private void nenneSchlechteAnkunft() {
        for (World welt : welten.values()) {
            Location spawn = welt.getSpawnLocation();
            var platz = Ankunftspunkt.suche(Ankunftspunkt.griffAuf(welt),
                    spawn.getBlockX(), spawn.getBlockZ());
            if (platz.isEmpty()) {
                getLogger().severe("In " + welt.key().asString() + " ist am"
                        + " Erscheinungspunkt " + spawn.getBlockX() + "/"
                        + spawn.getBlockZ() + " und " + Ankunftspunkt.RINGE
                        + " Ringe darum kein Platz zum Stehen. Dorthin fuehrt keine"
                        + " Reise.");
                continue;
            }
            var stand = platz.get();
            if (stand.x() != spawn.getBlockX() || stand.z() != spawn.getBlockZ()) {
                getLogger().warning("In " + welt.key().asString() + " taugt der"
                        + " Erscheinungspunkt " + spawn.getBlockX() + "/"
                        + spawn.getBlockY() + "/" + spawn.getBlockZ() + " nicht zum"
                        + " Stehen; Platz ist bei " + stand.x() + "/" + stand.y() + "/"
                        + stand.z() + ". Zum Uebernehmen: execute in "
                        + welt.key().asString() + " run setworldspawn " + stand.x() + " "
                        + stand.y() + " " + stand.z());
            }
        }
    }

    /**
     * Was jede Welt zum Vorgenerieren braucht - und wie weit sie ist.
     *
     * <p>Vorgeneriert wird mit Chunky, einem fremden Plugin. Dieser Befehl erzeugt keinen
     * Chunk; er rechnet die Zahlen aus, die Chunky braucht, und liest nach, was angekommen
     * ist. Der Grund steht in {@link Vorgenerierung}: Mitte und Radius sieht man nicht an,
     * und die Marktstadt liegt um -3,5/-3,5 statt um den Spawn.
     */
    private void zeigeVorgenerierung(Audience empfaenger) {
        empfaenger.sendMessage(Component.text("Vorgenerierung", NamedTextColor.GOLD));
        if (welten.isEmpty()) {
            empfaenger.sendMessage(Component.text("Keine Welt gebaut.", NamedTextColor.RED));
            return;
        }
        for (World welt : welten.values()) {
            String schluessel = welt.key().asString();
            if (!Vorgenerierung.hatGrenze(welt)) {
                empfaenger.sendMessage(Component.text(schluessel, NamedTextColor.YELLOW)
                        .append(Component.text(" - keine Weltgrenze, also nichts"
                                + " Abgeschlossenes vorzugenerieren.", NamedTextColor.GRAY)));
                continue;
            }
            Chunkfeld feld = Vorgenerierung.feldVon(welt);
            var rand = welt.getWorldBorder();
            empfaenger.sendMessage(Component.text(schluessel, NamedTextColor.AQUA)
                    .append(Component.text(
                            "  " + feld.chunks() + " Chunks, " + feld.regionenImFeld()
                                    + " Regionen im Feld, Chunks " + feld.chunkVonX() + ".."
                                    + feld.chunkBisX() + " je Achse",
                            NamedTextColor.GRAY)));
            String name = Vorgenerierung.chunkyName(welt, getServer());
            if (name == null) {
                // Keine Zeile zum Kopieren ist besser als eine, die ins Leere laeuft oder
                // die falsche Welt trifft.
                empfaenger.sendMessage(Component.text(
                        "  Chunky findet diese Welt nicht ueber ihren Namen (" + welt.getName()
                                + ") - es gibt keinen Befehl dafuer.", NamedTextColor.RED));
            } else {
                empfaenger.sendMessage(Component.text("  " + Vorgenerierung.chunkyBefehl(
                        name, feld, rand.getCenter().getX(), rand.getCenter().getZ()),
                        NamedTextColor.WHITE));
            }
            // Die Stichprobe kommt von der Platte und damit nicht sofort. Sie wird
            // nachgeschoben, statt den Hauptfaden warten zu lassen.
            Vorgenerierung.stand(welt).thenAccept(stand -> getServer().getScheduler()
                    .runTask(this, () -> meldeStand(empfaenger, stand)));
        }
    }

    private void meldeStand(Audience empfaenger, Vorgenerierung.Stand stand) {
        String kopf = "  Stichprobe " + stand.welt() + ": " + stand.fertig() + " von 9";
        if (stand.siehtVollstaendigAus()) {
            empfaenger.sendMessage(Component.text(kopf + " erzeugt - sieht vorgeneriert aus"
                    + (stand.veraltet() ? ", aber veraltet" : ""),
                    stand.veraltet() ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
            return;
        }
        empfaenger.sendMessage(Component.text(kopf + " erzeugt - NICHT vorgeneriert",
                NamedTextColor.RED));
        for (String luecke : stand.luecken()) {
            empfaenger.sendMessage(Component.text("    " + luecke, NamedTextColor.DARK_GRAY));
        }
    }

    /**
     * Nennt beim Start die Welten, die nicht vorgeneriert sind.
     *
     * <p>Angestossen wird die Vorgenerierung nur auf Befehl [entschieden 02.11.2026] - und
     * damit braucht es diese Meldung, sonst ist der Befehl eine Moeglichkeit, auf die
     * niemand kommt. Plan Abschnitt 13 verlangt die Vorgenerierung vor dem ersten
     * Spielertag.
     *
     * <p>Eine Zeile und nicht acht: Acht Warnungen bei jedem Start liest nach drei Wochen
     * niemand mehr, und dann ist auch die neunte unsichtbar, die wirklich neu ist.
     *
     * <p>Veraltete Chunks bekommen ihre eigene Zeile. Sie sind das maschinell lesbare
     * Zeichen fuer einen Minecraft-Versionswechsel - und damit fuer den Kornmark-Reset,
     * den Plan Abschnitt 13 dann verlangt. Ohne diese Zeile muesste der Betreiber sich
     * erinnern.
     */
    private void nenneNichtVorgenerierte() {
        List<World> zuPruefen = welten.values().stream()
                .filter(Vorgenerierung::hatGrenze)
                .toList();
        if (zuPruefen.isEmpty()) {
            return;
        }
        // Erst alle Stichproben, dann EINE Meldung. Die Antworten kommen von der Platte
        // und damit in beliebiger Reihenfolge; nacheinander zu protokollieren ergaebe
        // wieder acht Zeilen, nur in zufaelliger Folge.
        var stichproben = zuPruefen.stream().map(Vorgenerierung::stand).toList();
        CompletableFuture.allOf(stichproben.toArray(CompletableFuture[]::new))
                .thenAccept(nichts -> {
                    List<String> fehlt = new ArrayList<>();
                    List<String> veraltet = new ArrayList<>();
                    for (var frage : stichproben) {
                        var stand = frage.join();
                        String kurz = stand.welt().replace(getName().toLowerCase(Locale.ROOT)
                                + ":", "");
                        if (!stand.siehtVollstaendigAus()) {
                            fehlt.add(kurz + " (" + stand.fertig() + "/9)");
                        } else if (stand.veraltet()) {
                            veraltet.add(kurz);
                        }
                    }
                    if (!fehlt.isEmpty()) {
                        getLogger().warning("Nicht vorgeneriert: " + String.join(", ", fehlt)
                                + " - /freimarken vorgenerieren nennt die Befehle dazu.");
                    }
                    if (!veraltet.isEmpty()) {
                        getLogger().warning("Veraltete Chunks: " + String.join(", ", veraltet)
                                + " - erzeugt mit einer aelteren Minecraft-Fassung. Fuer"
                                + " Kornmark ist damit das Zuruecksetzen faellig"
                                + " (Plan Abschnitt 13).");
                    }
                });
    }

    private void probe(Audience empfaenger, String was, int x, int y, int z) {
        String block = marktstadt.getBlockAt(x, y, z).getType().name();
        empfaenger.sendMessage(Component.text("  " + was + " (" + x + "/" + y + "/" + z + "): ",
                        NamedTextColor.DARK_GRAY)
                .append(Component.text(block, NamedTextColor.AQUA)));
    }

    private void bringeZurParzelle(Audience empfaenger, int parzelleX, int parzelleZ) {
        if (marktstadt == null) {
            empfaenger.sendMessage(Component.text("Die Marktstadt fehlt.", NamedTextColor.RED));
            return;
        }
        if (!grenze.enthaeltParzelle(parzelleX, parzelleZ)) {
            empfaenger.sendMessage(Component.text(
                    "Parzellen gibt es nur von " + grenze.von() + "/" + grenze.von()
                            + " bis " + grenze.bis() + "/" + grenze.bis() + ".",
                    NamedTextColor.RED));
            return;
        }
        Location ziel = new Location(marktstadt,
                plan.mitteX(parzelleX) + 0.5, Stadtboden.BODEN + 1,
                plan.mitteZ(parzelleZ) + 0.5);
        if (empfaenger instanceof Player spieler) {
            spieler.teleportAsync(ziel);
            empfaenger.sendMessage(Component.text(
                    "Unterwegs zu Parzelle " + parzelleX + "/" + parzelleZ, NamedTextColor.GREEN));
        } else {
            empfaenger.sendMessage(Component.text(
                    "Parzelle " + parzelleX + "/" + parzelleZ + " liegt bei "
                            + ziel.getBlockX() + "/" + ziel.getBlockZ()
                            + " (Ursprung " + plan.ursprungX(parzelleX) + "/"
                            + plan.ursprungZ(parzelleZ) + ")", NamedTextColor.GRAY));
        }
    }
}
