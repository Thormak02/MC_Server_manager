package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * {@code /tpa}, {@code /tpaccept} und {@code /tpdeny} - Teleport zu einem Spieler, mit
 * seiner Zustimmung.
 *
 * <p>{@code /tpahere <spieler>} fragt in die andere Richtung: Dann kommt der Eingeladene,
 * und er ist auch der, der zustimmt. Darin liegt die Trennlinie zum Missbrauch - nicht in
 * der Richtung, sondern darin, dass <b>wer bewegt wird, zustimmt</b>. Was es nicht gibt,
 * ist {@code /tpaall}: ein Befehl, der alle zieht, wird von einem Einzigen benutzt und von
 * allen anderen erduldet.
 *
 * <p><b>Der Zielort wird erst beim Annehmen geholt</b>, nicht beim Fragen. Damit ist er
 * frisch: Der Angefragte kann in der Minute dazwischen weitergelaufen sein, und sein
 * Chunk ist geladen, weil er darin steht.
 *
 * <p>Geprueft wird nichts hier - jede Absage kommt aus {@link Reise}. Auch die
 * Abklingzeit: Sie gilt fuer Reisen, Rueckwege und Besuche gemeinsam, und das ist
 * richtig, weil es dieselbe Platte ist, die nachladen muss.
 */
final class AnfragenBefehle {

    /** Wer fragen und annehmen darf. Vorgabe: jeder - es gibt nichts zu rationieren. */
    static final String RECHT_TPA = "freimarken.tpa";

    private final Anfragen anfragen;
    private final Reise reise;

    /**
     * Die Saetze dieses Plugins - verdrahtet, bevor der erste Satz umzieht.
     *
     * <p>Warum alle elf Sender dieses Feld in EINEM Zug bekommen und nicht je
     * Bauschritt, steht dort, wo sie gebaut werden: {@code FreimarkenPlugin.onEnable}.
     */
    private final Sprache sprache;

    AnfragenBefehle(Anfragen anfragen, Reise reise, Sprache sprache) {
        this.sprache = sprache;
        this.anfragen = anfragen;
        this.reise = reise;
    }

    LiteralCommandNode<CommandSourceStack> tpa() {
        return frageBefehl("tpa", Anfragen.Richtung.ZU_DIR);
    }

    LiteralCommandNode<CommandSourceStack> tpahere() {
        return frageBefehl("tpahere", Anfragen.Richtung.HIERHER);
    }

    /**
     * Beide Fragebefehle, aus einer Hand.
     *
     * <p>{@code /tpa} und {@code /tpahere} unterscheiden sich in einem Wort - der
     * Richtung. Zwei Methoden daraus zu machen hiesse, dass eines Tages die eine einen
     * Vorschlagsgeber hat und die andere nicht.
     */
    private LiteralCommandNode<CommandSourceStack> frageBefehl(String befehlsname,
                                                               Anfragen.Richtung richtung) {
        return Commands.literal(befehlsname)
                .requires(diesesIstEinSpieler())
                .then(Commands.argument("spieler", StringArgumentType.word())
                        .suggests((zusammenhang, bauer) -> {
                            // Kein ArgumentTypes.player(): Der vanillanahe Typ nimmt
                            // Selektoren wie @a, verraet damit Unsichtbare und meldet
                            // englisch. Hier werden nur sichtbare Namen vorgeschlagen -
                            // und der eigene nie.
                            if (zusammenhang.getSource().getExecutor() instanceof Player ich) {
                                String angefangen = bauer.getRemainingLowerCase();
                                Bukkit.getOnlinePlayers().stream()
                                        .filter(anderer -> !anderer.equals(ich))
                                        .filter(ich::canSee)
                                        .map(Player::getName)
                                        .filter(name -> name.toLowerCase(java.util.Locale.ROOT)
                                                .startsWith(angefangen))
                                        .forEach(bauer::suggest);
                            }
                            return bauer.buildFuture();
                        })
                        .executes(z -> {
                            frage(z.getSource().getExecutor(),
                                    StringArgumentType.getString(z, "spieler"), richtung);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    LiteralCommandNode<CommandSourceStack> tpaccept() {
        return Commands.literal("tpaccept")
                .requires(diesesIstEinSpieler())
                .executes(z -> {
                    nimmAn(z.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    LiteralCommandNode<CommandSourceStack> tpdeny() {
        return Commands.literal("tpdeny")
                .requires(diesesIstEinSpieler())
                .executes(z -> {
                    lehneAb(z.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private static java.util.function.Predicate<CommandSourceStack> diesesIstEinSpieler() {
        return quelle -> quelle.getExecutor() instanceof Player
                && quelle.getSender().hasPermission(RECHT_TPA);
    }

    private void frage(Object fragenderRoh, String name, Anfragen.Richtung richtung) {
        if (!(fragenderRoh instanceof Player fragender)) {
            return;
        }
        // getPlayerExact und nicht getPlayer: Letzteres raet den naechstbesten Namen, und
        // dann fragt /tpa Max unversehens Maxime.
        Player angefragter = Bukkit.getPlayerExact(name);
        if (angefragter == null || !fragender.canSee(angefragter)) {
            nichtDaZeilen(sprache, name).forEach(fragender::sendMessage);
            return;
        }
        if (angefragter.equals(fragender)) {
            beiDirZeilen(sprache).forEach(fragender::sendMessage);
            return;
        }
        if (!anfragen.stellen(fragender.getUniqueId(), angefragter.getUniqueId(),
                richtung, System.nanoTime())) {
            stehtNochZeilen(sprache, angefragter.getName())
                    .forEach(fragender::sendMessage);
            return;
        }
        sendeStellen(sprache, fragender, angefragter, richtung);
    }

    /**
     * Die zwei Sendungen einer gestellten Frage - je Empfaenger eine eigene Liste.
     *
     * <p><b>Warum diese drei Sendemethoden herausgezogen sind</b> (hier, bei
     * {@link #sendeAnnahme} und bei {@link #sendeAblehnung}): Die Zweige darueber sind
     * ohne Server nicht aufrufbar - {@code Bukkit.getPlayerExact}, {@code Bukkit.getPlayer}
     * und {@link Reise} gibt es dort nicht. Sie bloss paketsichtbar zu machen haette also
     * nichts geholfen. Was hier steht, fasst nur noch zwei Spielerobjekte an und fragt sie
     * nach ihrem Namen - damit kann {@code AufrufstelleTest} die Stelle mit zwei
     * Stellvertretern aufrufen und JE SPIELER eine eigene Mitschrift halten. Dasselbe
     * Muster wie {@code SpielzeitBefehle.sende}, nur mit {@code Player} statt
     * {@code Audience}, weil der Name mit muss.
     *
     * <p>Gemessen war das die Luecke: Wurden hier die zwei NAMEN getauscht - also
     * {@code stellenFragerZeilen(sprache, fragender.getName())} und umgekehrt -, blieben
     * alle 418 Proben des Moduls gruen. Die Empfaenger blieben dabei richtig, und nur die
     * nagelt {@code GrenzeTest} 31e fest; WAS eingesetzt wird, sieht eine Tafel ueber den
     * Quelltext nicht.
     *
     * @param fragender   wer gefragt hat - er liest den Namen des ANGEFRAGTEN
     * @param angefragter wer entscheidet - er liest den Namen des FRAGENDEN
     * @param richtung    welche der zwei Fragen gestellt wurde
     */
    static void sendeStellen(Sprache sprache, Player fragender, Player angefragter,
                             Anfragen.Richtung richtung) {
        stellenFragerZeilen(sprache, angefragter.getName())
                .forEach(fragender::sendMessage);
        stellenAngefragtZeilen(sprache, fragender.getName(), richtung)
                .forEach(angefragter::sendMessage);
    }

    private void nimmAn(Object annehmerRoh) {
        if (!(annehmerRoh instanceof Player annehmer)) {
            return;
        }
        var anfrage = anfragen.annehmen(annehmer.getUniqueId(), System.nanoTime());
        if (anfrage.isEmpty()) {
            niemandFragtZeilen(sprache).forEach(annehmer::sendMessage);
            return;
        }
        Player fragender = Bukkit.getPlayer(anfrage.get().von());
        if (fragender == null) {
            fragenderWegZeilen(sprache).forEach(annehmer::sendMessage);
            return;
        }
        // Wer wandert und wer bleibt, steht in der Anfrage - nicht im Befehl. So muss
        // /tpaccept die Richtung nicht erraten, und es gibt nur eine Stelle, an der sie
        // entschieden wird.
        boolean annehmerWandert = anfrage.get().wandert().equals(annehmer.getUniqueId());
        Player wandert = annehmerWandert ? annehmer : fragender;
        Player bleibt = annehmerWandert ? fragender : annehmer;

        sendeAnnahme(sprache, annehmer, fragender, annehmerWandert,
                anfragen.offeneFuer(annehmer.getUniqueId(), System.nanoTime()));
        // Der Ort erst jetzt geholt: Der Bleibende kann in der Minute seit der Anfrage
        // weitergelaufen sein, und sein Chunk ist geladen, weil er darin steht.
        reise.fuehre(wandert, bleibt.getLocation(), bleibt.getName());
    }

    /**
     * Die zwei Sendungen einer angenommenen Anfrage, und der Hinweis auf die uebrigen.
     *
     * <p>Warum herausgezogen, steht bei {@link #sendeStellen}. Gemessen blieben auch hier
     * 418 Proben gruen, als die zwei Namen getauscht wurden: Gespielt las dann Bert nach
     * {@code /tpaccept} "Bert kommt zu dir." - seinen eigenen Namen - und Anna "Anna hat
     * angenommen - du kommst."
     *
     * <p><b>Die Wache {@code weitere > 0} steht hier</b> und nicht in
     * {@link #weitereZeilen}: Sie entscheidet, OB gesendet wird, und nicht, WAS gelesen
     * wird - {@code Art.GEZAEHLT} kennt nur eins und viele, und bei Null gibt es heute
     * gar keine Zeile. Vorher stand sie im Zweig und war damit von keiner Probe
     * beruehrbar: Auf {@code weitere > 1} geaendert erfuhr ein Spieler mit GENAU EINER
     * weiteren offenen Anfrage nichts mehr von ihr und liess sie ablaufen, und der Lauf
     * blieb gruen. Jetzt haelt {@code AufrufstelleTest} 34n die zwei Faelle Null und Eins
     * fest - mit der ZAHL der Zeilen je Spieler, denn das Bild der dritten Zeile allein
     * sagt nichts darueber, ob sie ueberhaupt kam.
     *
     * @param annehmer        wer {@code /tpaccept} getippt hat - er liest den Namen des
     *                        FRAGENDEN und, wenn noch etwas wartet, deren Zahl
     * @param fragender       wer gefragt hatte - er liest den Namen des ANNEHMERS
     * @param annehmerWandert ob der Annehmer selbst reist ({@code /tpahere})
     * @param weitere         wie viele Anfragen an den Annehmer NACH dieser noch offen
     *                        stehen
     */
    static void sendeAnnahme(Sprache sprache, Player annehmer, Player fragender,
                             boolean annehmerWandert, long weitere) {
        nimmAnAnnehmerZeilen(sprache, fragender.getName(), annehmerWandert)
                .forEach(annehmer::sendMessage);
        nimmAnFragerZeilen(sprache, annehmer.getName(), annehmerWandert)
                .forEach(fragender::sendMessage);
        if (weitere > 0) {
            weitereZeilen(sprache, weitere).forEach(annehmer::sendMessage);
        }
    }

    private void lehneAb(Object annehmerRoh) {
        if (!(annehmerRoh instanceof Player annehmer)) {
            return;
        }
        sendeAblehnung(sprache, annehmer,
                anfragen.ablehnenAlle(annehmer.getUniqueId(), System.nanoTime()));
    }

    /**
     * Die eine Sendung von {@code /tpdeny} - an den Ablehnenden und an niemanden sonst.
     *
     * <p>Ein Empfaenger und darum fast nichts zu entscheiden; herausgezogen aus demselben
     * Grund wie {@link #sendeStellen}, damit alle drei Zweige auf demselben Weg pruefbar
     * sind. Ohne sie bleibt dieser Sendeort von keiner Probe erreicht, und ein
     * weggefallenes {@code forEach} wuerde nur noch von der Zahlentafel in
     * {@code GrenzeTest} 31c gefangen - die sagt, wie oft die DATEI sendet, und nicht,
     * dass der Ablehnende wirklich etwas liest.
     *
     * @param weg wie viele Anfragen weggenommen wurden - Null ist der Fall "es war nichts
     *            da", und auch dann wird gesendet
     */
    static void sendeAblehnung(Sprache sprache, Player annehmer, long weg) {
        lehneAbZeilen(sprache, weg).forEach(annehmer::sendMessage);
    }

    // --- Die Naht: reine Methoden, die nur Zeilen bauen ------------------------
    //
    // Je Zweig und JE EMPFAENGER eine Methode, die aus einfachen Werten eine
    // List<Component> baut - ohne Player, ohne Bukkit. Dazwischen liegen die drei
    // Sendemethoden (sendeStellen, sendeAnnahme, sendeAblehnung): Sie waehlen die
    // Methode, setzen den Namen ein und schicken die Liste an den richtigen Spieler.
    // Das Muster ist das von ParzellenBefehle; der Grund steht dort und urspruenglich
    // im Javadoc von SpielzeitBefehle.sende.
    //
    // JE EMPFAENGER und nicht je Zweig, und das ist hier der Kern: frage() sendet EINE
    // Zeile an den Fragenden und ZWEI an den Angefragten, nimmAn() an beide. Eine
    // gemeinsame Liste waere die Vereinigung - und eine Probe darueber bliebe gruen,
    // wenn die zwei Empfaenger vertauscht werden. Gespielt liest dann der Fragende, er
    // moechte zu sich selbst kommen, und der Annehmer, er selbst habe angenommen.
    //
    // Zwei verschiedene Proben halten die zwei Haelften dieser Naht, und die eine
    // ersetzt die andere nicht. WELCHE Methode an WELCHE Empfaengervariable geht, nagelt
    // GrenzeTest mit seiner Tafel der Sendepaare fest - nicht SendewegTest, das die
    // Methoden unmittelbar aufruft und den Empfaenger gar nicht sieht. WAS ihr als Wert
    // uebergeben wird, nagelt keine Tafel ueber den Quelltext fest, sondern
    // AufrufstelleTest: Es ruft die drei Sendemethoden mit zwei verschieden benannten
    // Stellvertretern auf und haelt je Spieler eine eigene Mitschrift.
    //
    // WAS NICHT MITWANDERT: eine Wache, die entscheidet, OB ueberhaupt gesendet wird,
    // bleibt bei der Sendung. Das ist hier das "weitere > 0", jetzt in sendeAnnahme:
    // Art.GEZAEHLT kennt nur eins und viele, und bei Null gibt es heute gar keine Zeile.
    // Im Zweig selbst stand sie bis zum 05.11.2026 - dort war sie von keiner Probe
    // beruehrbar. Die Wache "weg == 0" in lehneAbZeilen steht dagegen in der reinen
    // Methode, weil in BEIDEN Faellen gesendet wird - sie entscheidet, WAS der Spieler
    // liest, und nicht, OB.
    //
    // Der Parameter "Sprache sprache" steht in jeder Signatur, auch wo die Methode ihn
    // noch nicht braucht: Beim Umzug der Saetze aendert sich dann nur der Rumpf und
    // keine Aufrufstelle.

    /**
     * Der Angefragte ist nicht da - oder unsichtbar.
     *
     * <p>{@code name} ist das GETIPPTE Wort und nicht {@code getName()}: Den gaebe es
     * hier gar nicht (der Spieler kann {@code null} sein), und bei einem unsichtbaren
     * Spieler waere der richtige Name ein Verrat. EIN Satz fuer beide Faelle, aus genau
     * diesem Grund - zwei Saetze verrieten den Unsichtbaren.
     */
    static List<Component> nichtDaZeilen(Sprache sprache, String name) {
        return sprache.bau(AnfragenSatz.FRAGE_NICHT_DA, 1L, Einsatz.von("name", name));
    }

    /** Er hat sich selbst gefragt. */
    static List<Component> beiDirZeilen(Sprache sprache) {
        return sprache.bau(AnfragenSatz.FRAGE_BEI_DIR, 1L, Einsatz.keine());
    }

    /** Dieselbe Anfrage steht schon offen - es ist nichts hinzugekommen. */
    static List<Component> stehtNochZeilen(Sprache sprache, String name) {
        return sprache.bau(AnfragenSatz.FRAGE_STEHT_NOCH, 1L, Einsatz.von("name", name));
    }

    /** Die Quittung an den FRAGENDEN - eine Zeile. */
    static List<Component> stellenFragerZeilen(Sprache sprache, String name) {
        return sprache.bau(AnfragenSatz.FRAGE_GESTELLT, 1L, Einsatz.von("name", name));
    }

    /**
     * Die zwei Zeilen an den ANGEFRAGTEN - die Frage und der Hinweis darunter.
     *
     * <p>Zwei Zeilen und nicht eine mit Umbruchmarke: Sie tragen zwei verschiedene
     * Farben, und ein Satz mit Umbruch sendet beide Zeilen in derselben.
     *
     * <p>Die Wahl zwischen den zwei Fragen steht HIER und nicht in
     * {@link Sprachschluessel} bei {@code grund} und {@code bescheid}: Eine dritte
     * mechanische Familie waere fuer {@code GrenzeTest} 30 selbstbescheinigend - die
     * zwei Schluessel stuenden dann nur noch in {@code Sprachschluessel}, und ihre Pfade
     * muessten in {@code familienDeckung} nachgetragen werden. {@code SendewegTest} 33x
     * nagelt diese Menge aber als GENAU die Vereinigung von Gruenden und Bescheiden
     * fest. Hier in der reinen Methode kann eine Probe die Verzweigung ohne Server
     * aufrufen - das war der Zweck der Hausregel, und er ist erfuellt.
     *
     * @param name     wie der FRAGENDE heisst - nicht der Angefragte
     * @param richtung welche der zwei Fragen gestellt wurde
     */
    static List<Component> stellenAngefragtZeilen(Sprache sprache, String name,
                                                  Anfragen.Richtung richtung) {
        List<Component> zeilen = new ArrayList<>(sprache.bau(
                richtung == Anfragen.Richtung.HIERHER
                        ? AnfragenSatz.FRAGE_EINGELADEN
                        : AnfragenSatz.FRAGE_MOECHTE_KOMMEN,
                1L, Einsatz.von("name", name)));
        zeilen.addAll(sprache.bau(AnfragenSatz.FRAGE_HINWEIS, 1L, Einsatz.keine()));
        return List.copyOf(zeilen);
    }

    /**
     * Es steht keine Anfrage offen - dieselbe Zeile fuer {@code /tpaccept} und
     * {@code /tpdeny}.
     *
     * <p>EINE Methode und ein Schluessel, von beiden Zweigen benutzt. Zwei waeren
     * derselbe Satz an zwei Orten: Der Betreiber aendert einen und wundert sich, warum
     * der andere Befehl weiter das Alte sagt.
     */
    static List<Component> niemandFragtZeilen(Sprache sprache) {
        return sprache.bau(AnfragenSatz.NIEMAND, 1L, Einsatz.keine());
    }

    /** Wer gefragt hatte, ist inzwischen abgemeldet. */
    static List<Component> fragenderWegZeilen(Sprache sprache) {
        return sprache.bau(AnfragenSatz.ANNEHMEN_FRAGENDER_WEG, 1L, Einsatz.keine());
    }

    /**
     * Die Zeile an den ANNEHMER - wer von beiden sich bewegt.
     *
     * @param fragender       wie der FRAGENDE heisst
     * @param annehmerWandert ob der Annehmer selbst reist ({@code /tpahere})
     */
    static List<Component> nimmAnAnnehmerZeilen(Sprache sprache, String fragender,
                                                boolean annehmerWandert) {
        return sprache.bau(annehmerWandert
                        ? AnfragenSatz.ANNEHMEN_DU_KOMMST
                        : AnfragenSatz.ANNEHMEN_ER_KOMMT,
                1L, Einsatz.von("name", fragender));
    }

    /**
     * Die Zeile an den FRAGENDEN - seine Anfrage wurde angenommen.
     *
     * <p>Die Bedingung ist verneint und der verneinte Fall steht zuerst, genau wie im
     * alten Quelltext: Wandert der Annehmer NICHT, geht der Fragende selbst; wandert er,
     * kommt er zum Fragenden. Wer die zwei Zweige nach Augenmass umhaengt, dreht die
     * Richtung.
     *
     * @param annehmer        wie der ANNEHMER heisst
     * @param annehmerWandert ob der Annehmer selbst reist ({@code /tpahere})
     */
    static List<Component> nimmAnFragerZeilen(Sprache sprache, String annehmer,
                                              boolean annehmerWandert) {
        if (!annehmerWandert) {
            return sprache.bau(AnfragenSatz.ANNEHMEN_QUITTUNG_DU_KOMMST, 1L,
                    Einsatz.von("name", annehmer));
        }
        return sprache.bau(AnfragenSatz.ANNEHMEN_QUITTUNG_ER_KOMMT, 1L,
                Einsatz.von("name", annehmer));
    }

    /**
     * Wie viele Anfragen noch warten - an den ANNEHMER.
     *
     * <p>Die Zahl zaehlt die NACH dem Wegnehmen noch offenen Anfragen; die angenommene
     * ist nicht mehr dabei. Ob ueberhaupt gesendet wird, entscheidet der Zweig: Bei Null
     * gibt es heute keine Zeile, und eine mit einer Null darin waere eine neue.
     *
     * <p>{@code String.valueOf} und nicht {@code Einsatz.mit(name, long)}: Die Zahl ist
     * NACKT, ohne Tausenderpunkt - so steht sie heute im Spiel, und nur Geldbetraege
     * tragen ihn. Der lange Weg machte aus 1000 ein "1.000".
     */
    static List<Component> weitereZeilen(Sprache sprache, long weitere) {
        return sprache.bau(AnfragenSatz.ANNEHMEN_WEITERE, weitere,
                Einsatz.von("anzahl", String.valueOf(weitere)));
    }

    /**
     * Die Antwort auf {@code /tpdeny} - weggenommen, oder es war nichts da.
     *
     * <p>Die Wache auf die Null steht hier und nicht im Zweig, weil in BEIDEN Faellen
     * gesendet wird: Sie entscheidet, WAS der Spieler liest, und nicht, OB. Der Nullfall
     * ist derselbe Satz wie bei {@code /tpaccept} ohne offene Anfrage.
     */
    static List<Component> lehneAbZeilen(Sprache sprache, long weg) {
        return weg == 0
                ? niemandFragtZeilen(sprache)
                : sprache.bau(AnfragenSatz.ABLEHNEN_ABGELEHNT, weg,
                        Einsatz.von("anzahl", String.valueOf(weg)));
    }
}
