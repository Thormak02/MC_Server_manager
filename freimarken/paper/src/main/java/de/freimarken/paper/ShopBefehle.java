package de.freimarken.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Shopablage;
import de.freimarken.kern.Wirtschaftsstand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * {@code /shop} - der SCHLUESSEL zu einer fremden Shopkiste.
 *
 * <p><b>Der erste Befehl des Shop-Bereichs ueberhaupt</b>, und das ist eine Entscheidung
 * aus Plan 7.4: Ein Kistenshop wird durch ein beschriebenes Schild angelegt und durch
 * einen Klick bedient - "Kein Befehl, kein Menue - man geht hin und klickt". Hier gibt es
 * aber nichts anzuklicken: Eine Freigabe ist eine Abmachung zwischen zwei Menschen, und
 * dafuer braucht es einen Namen, den man tippt.
 *
 * <p><b>Warum es den Befehl ueberhaupt gibt.</b> Seit dem 08.11.2026 ist das OEFFNEN
 * einer fremden Shopkiste ganz verwehrt, und zwar bewusst: "Das Nehmen hat zehn Wege
 * (Links-, Rechts-, Umschaltklick, Ziehen, Zahlentasten, zweite Hand, Doppelklick,
 * Wurf), und jede vergessene Art ist ein Loch." Der Preis steht im Plan daneben: "dass
 * ein Fremder einem Shop auch nichts schenken kann". Diesen Preis hebt der Befehl auf.
 *
 * <p><b>Drei Zweige, und jeder antwortet auf den Shop IM BLICK</b> - nicht auf einen
 * getippten Ort. Wer freigibt, steht vor seinem Laden; Koordinaten abzutippen waere der
 * Umweg, und bei zwei Schildern an einer Doppelkiste waere er auch noch mehrdeutig. Die
 * Naht dafuer ({@link Blickfang}) ist dieselbe Bauart wie bei {@link BlockBefehle} vom
 * Vortag.
 *
 * <p><b>Freigeben darf NUR der Besitzer</b> - nicht das Team, nicht ein schon
 * Freigegebener. Das Teamrecht oeffnet eine Kiste, damit ein verlassener Laden abgebaut
 * werden kann; es macht niemanden zum Hausherrn. Und waere der Schluessel selbst
 * weitergebbar, wuesste nach zwei Schritten niemand mehr, wer eigentlich Zutritt hat.
 */
final class ShopBefehle {

    /**
     * Das Recht, die eigenen Shops freizugeben.
     *
     * <p>{@code default: true} in der {@code plugin.yml}, anders als
     * {@code freimarken.blockbuch}: Hier verschenkt jemand Zutritt zu SEINEM Laden, und
     * das ist keine Verwaltungshandlung, sondern Teil des Handelns. Wer einen Shop
     * anlegen darf, darf ihn auch teilen.
     */
    static final String RECHT = "freimarken.shop";

    /**
     * Wie weit der Blick nach dem Schild reicht, in Bloecken.
     *
     * <p>Fest und nicht einstellbar, anders als beim Blockbuch: Dort sucht ein Moderator
     * einen Block, den ein anderer gesetzt hat, und steht oft nicht davor. Hier steht
     * einer vor seinem eigenen Laden - fuenf Bloecke sind die Reichweite, in der man
     * ohnehin baut.
     */
    static final int REICHWEITE = 5;

    /**
     * Woher der angesehene Block kommt - dieselbe Naht wie bei {@link BlockBefehle}.
     *
     * <p>Ein Fingerbreit Naht und kein Rahmenwerk: Ohne sie waere dieser Zweig nicht
     * pruefbar, denn der Spielerstellvertreter der Proben beantwortet fuenfzehn Methoden
     * und wirft bei jeder anderen - {@code getTargetBlockExact} gehoert nicht dazu.
     *
     * <p>EINE EIGENE Schnittstelle und nicht die von {@link BlockBefehle} geteilt: Zwei
     * Bereiche, die sich eine Naht teilen, teilen sich bald mehr, und dann kann man den
     * einen nicht ohne den anderen pruefen. Dieselbe Begruendung wie bei den zwei
     * Taktquellen.
     */
    interface Blickfang {

        /** Der Block, den dieser Spieler ansieht - oder {@code null}. */
        Block imBlick(Player wer, int reichweite);
    }

    /** Die Kennung zu einem getippten Namen - oder {@code null} fuer unbekannt. */
    interface Namenssuche {
        UUID kennungVon(String name);
    }

    /** Der Name zu einer Kennung - nie {@code null}. */
    interface Namensgeber {
        String von(UUID wer);
    }

    private final Shopablage ablage;
    private final Sprache sprache;
    private final Blickfang blickfang;
    private final Namenssuche namenssuche;
    private final Namensgeber namen;

    /**
     * Der STAND und nicht sein Schnappschuss.
     *
     * <p>{@code Shophandel} bekommt beim Anmelden {@code wirtschaft.werte().handel()},
     * also die Zahlen EINES Augenblicks - seine Gebuehren wirken damit erst nach einem
     * Neustart. Das wird hier nicht fortgeschrieben: Wer die Seitengroesse aendert und
     * {@code /freimarken neuladen} tippt, soll die Wirkung sehen. Sonst steht in der
     * {@code config.yml} eine Zahl, die im Betrieb nicht umschaltet, und das waere
     * genau die Art halb wirkender Einstellung, die diese Datei sonst ueberall
     * ausdruecklich meldet.
     */
    private final Wirtschaftsstand wirtschaft;

    private final Logger protokoll;

    ShopBefehle(Shopablage ablage, Sprache sprache, Blickfang blickfang,
                Namenssuche namenssuche, Namensgeber namen,
                Wirtschaftsstand wirtschaft, Logger protokoll) {
        if (ablage == null || sprache == null || blickfang == null
                || namenssuche == null || namen == null || wirtschaft == null
                || protokoll == null) {
            throw new IllegalArgumentException("Ablage, Sprache, Blickfang,"
                    + " Namenssuche, Namensgeber, Wirtschaftsstand und Protokoll sind"
                    + " Pflicht");
        }
        this.ablage = ablage;
        this.sprache = sprache;
        this.blickfang = blickfang;
        this.namenssuche = namenssuche;
        this.namen = namen;
        this.wirtschaft = wirtschaft;
        this.protokoll = protokoll;
    }

    /**
     * Der Baum von {@code /shop} - gebaut, denn er ist ein eigener Befehl.
     *
     * <p>Anders als {@code SpielzeitBefehle.spielzeit()}, das einen BAUER herausgibt:
     * Jener Zweig haengt sich an {@code /freimarken}, dieser steht fuer sich. Plan 11.1
     * fuehrt zwar eine feste Befehlsliste ohne {@code /shop} - aber dieselbe Liste
     * verlangt auch, dass ein Befehl dort steht, wo der Spieler ihn sucht, und wer einen
     * Shop teilen will, tippt {@code /shop}.
     *
     * <p>SPIELERPFLICHTIG an der Wurzel ({@code getExecutor() instanceof Player}): Jeder
     * Zweig braucht einen eigenen Spieler - drei fragen den Shop IM BLICK, und wer
     * keinen Koerper hat, sieht keinen; {@code liste} fragt nach den EIGENEN Laeden, und
     * die Konsole hat keine. Als {@code requires} und nicht als gelesene Absage - anders
     * als beim Blockbuch, wo die Konsole einen sinnvollen zweiten Weg hat. Hier gibt es
     * keinen: Ein Betreiber, der eine fremde Freigabe setzen oder die Laeden eines
     * Spielers sehen will, fragt die Datenbank.
     */
    LiteralCommandNode<CommandSourceStack> shop() {
        return Commands.literal("shop")
                .requires(quelle -> quelle.getExecutor() instanceof Player
                        && quelle.getSender().hasPermission(RECHT))
                .then(Commands.literal("freigeben")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((zusammenhang, bauer) -> {
                                    for (Player spieler : Bukkit.getOnlinePlayers()) {
                                        bauer.suggest(spieler.getName());
                                    }
                                    return bauer.buildFuture();
                                })
                                .executes(this::freigeben)))
                .then(Commands.literal("sperren")
                        .then(Commands.argument("name", StringArgumentType.word())
                                // Die Vorschlaege sind hier die FREIGEGEBENEN und nicht
                                // die Anwesenden: Wer sperrt, meint jemanden aus seiner
                                // Liste, und der ist meist gerade nicht da.
                                .suggests(this::schlageFreigegebeneVor)
                                .executes(this::sperren)))
                .then(Commands.literal("freigaben")
                        .executes(this::zeigeFreigaben))
                // Der EINZIGE Zweig ohne Blick auf ein Schild - und genau darum gibt es
                // ihn: Wer seine Laeden sucht, steht nicht vor ihnen.
                //
                // Zwei Einstiege und kein Argument mit Vorgabewert: Brigadier kennt
                // keine optionalen Argumente, ein nicht angegebenes waere beim Lesen ein
                // Wurf. Die Untergrenze 1 weist Brigadier selbst ab, also muss der Zweig
                // keine 0 und keine negative Seite bedenken.
                .then(Commands.literal("liste")
                        .executes(zusammenhang -> zeigeListe(zusammenhang, 1))
                        .then(Commands.argument("seite",
                                        IntegerArgumentType.integer(1))
                                .executes(zusammenhang -> zeigeListe(zusammenhang,
                                        IntegerArgumentType.getInteger(zusammenhang,
                                                "seite")))))
                .build();
    }

    /** {@code /shop freigeben <name>}. */
    private int freigeben(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = (Player) zusammenhang.getSource().getExecutor();
        Kistenshop shop = meinShopImBlick(zusammenhang, spieler);
        if (shop == null) {
            return 0;
        }
        String gesucht = StringArgumentType.getString(zusammenhang, "name");
        UUID wen = namenssuche.kennungVon(gesucht);
        if (wen == null) {
            sende(zusammenhang, ShopSatz.SHOP_UNBEKANNT,
                    Einsatz.von("name", gesucht));
            return 0;
        }
        if (wen.equals(spieler.getUniqueId())) {
            // Sich selbst freizugeben ergibt nichts - der Besitzer darf ohnehin. Eine
            // stille Annahme waere schlechter: Der Spieler saehe eine Bestaetigung und
            // glaubte, etwas eingerichtet zu haben.
            sende(zusammenhang, ShopSatz.SHOP_SELBST, Einsatz.keine());
            return 0;
        }
        if (shop.istFreigegeben(wen)) {
            sende(zusammenhang, ShopSatz.SHOP_SCHON_FREI,
                    Einsatz.von("spieler", namen.von(wen)));
            return 0;
        }
        Set<UUID> neue = new LinkedHashSet<>(shop.freigegeben());
        neue.add(wen);
        if (!schreibe(zusammenhang, shop, neue)) {
            return 0;
        }
        sende(zusammenhang, ShopSatz.SHOP_FREIGEGEBEN,
                Einsatz.von("spieler", namen.von(wen)));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /shop sperren <name>}. */
    private int sperren(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = (Player) zusammenhang.getSource().getExecutor();
        Kistenshop shop = meinShopImBlick(zusammenhang, spieler);
        if (shop == null) {
            return 0;
        }
        String gesucht = StringArgumentType.getString(zusammenhang, "name");
        UUID wen = namenssuche.kennungVon(gesucht);
        if (wen == null) {
            sende(zusammenhang, ShopSatz.SHOP_UNBEKANNT,
                    Einsatz.von("name", gesucht));
            return 0;
        }
        if (!shop.istFreigegeben(wen)) {
            sende(zusammenhang, ShopSatz.SHOP_NICHT_FREI,
                    Einsatz.von("spieler", namen.von(wen)));
            return 0;
        }
        Set<UUID> neue = new LinkedHashSet<>(shop.freigegeben());
        neue.remove(wen);
        if (!schreibe(zusammenhang, shop, neue)) {
            return 0;
        }
        sende(zusammenhang, ShopSatz.SHOP_GESPERRT,
                Einsatz.von("spieler", namen.von(wen)));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /shop freigaben} - wer darf an diesen Shop. */
    private int zeigeFreigaben(CommandContext<CommandSourceStack> zusammenhang) {
        Player spieler = (Player) zusammenhang.getSource().getExecutor();
        Kistenshop shop = meinShopImBlick(zusammenhang, spieler);
        if (shop == null) {
            return 0;
        }
        List<Component> zeilen = new ArrayList<>();
        if (shop.freigegeben().isEmpty()) {
            // EINE Zeile und nicht eine leere Liste: "niemand" ist hier genau die
            // Auskunft, auf die es ankommt.
            zeilen.add(sprache.eineZeile(ShopSatz.SHOP_FREIGABEN_LEER, Einsatz.keine()));
        } else {
            zeilen.add(sprache.eineZeile(ShopSatz.SHOP_FREIGABEN_KOPF,
                    Einsatz.von("anzahl", shop.freigegeben().size())));
            // Nach NAMEN geordnet und nicht nach Kennung: Der Spieler liest Namen, und
            // eine Liste in der Reihenfolge zufaelliger UUIDs sieht aus wie Unordnung.
            List<String> namensliste = new ArrayList<>();
            for (UUID wer : shop.freigegeben()) {
                namensliste.add(namen.von(wer));
            }
            namensliste.sort(String.CASE_INSENSITIVE_ORDER);
            for (String name : namensliste) {
                zeilen.add(sprache.eineZeile(ShopSatz.SHOP_FREIGABEN_ZEILE,
                        Einsatz.von("spieler", name)));
            }
        }
        sendeZeilen(zusammenhang, zeilen);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /shop liste [seite]} - die eigenen Laeden, auf Seiten.
     *
     * <p><b>Seiten und kein Schnitt.</b> Ein Schnitt bei zehn Zeilen verfehlte genau den
     * Zweck: Wer dreissig Laeden hat, findet sie nicht wieder - und zwanzig davon waeren
     * ueber diesen Befehl gar nicht erreichbar.
     *
     * <p><b>Die Reihenfolge kommt aus der Ablage</b> und wird hier NICHT noch einmal
     * hergestellt. {@link Shopablage#vonSpieler} ordnet nach
     * {@link Kistenshop#NACH_STELLE}, und der geteilte Vertrag haelt das fuer jede
     * Ablage - mit Stellen, deren Text- und Zahlenordnung auseinanderfallen. Ein
     * zweites Sortieren hier waere ein Riegel, der den ersten unpruefbar macht.
     *
     * <p>Bundshops stehen in keiner dieser Listen: Sie gehoeren niemandem, also
     * niemandes Liste.
     */
    private int zeigeListe(CommandContext<CommandSourceStack> zusammenhang, int seite) {
        Player spieler = (Player) zusammenhang.getSource().getExecutor();
        List<Kistenshop> meine;
        try {
            meine = ablage.vonSpieler(spieler.getUniqueId());
        } catch (RuntimeException stoerung) {
            // Dieselbe Behandlung wie in meinShopImBlick: Eine Stoerung der Ablage ist
            // ein Fall fuer das Protokoll und eine gelesene Absage - kein Brigadier-
            // Fehler im Chat, der wie ein kaputter Server aussieht.
            protokoll.log(Level.WARNING, "Die Shopliste von " + spieler.getName()
                    + " liess sich nicht lesen", stoerung);
            sende(zusammenhang, ShopSatz.SHOP_LISTE_GESTOERT, Einsatz.keine());
            return 0;
        }
        if (meine.isEmpty()) {
            sende(zusammenhang, ShopSatz.SHOP_LISTE_LEER, Einsatz.keine());
            return Command.SINGLE_SUCCESS;
        }
        int jeSeite = wirtschaft.werte().handel().zeilenJeSeite();
        // Aufgerundet, ohne Gleitkomma: 11 Laeden auf 10 je Seite sind zwei Seiten.
        int seiten = (meine.size() + jeSeite - 1) / jeSeite;
        if (seite > seiten) {
            sende(zusammenhang, ShopSatz.SHOP_LISTE_KEINE_SEITE,
                    Einsatz.von("seite", seite).mit("seiten", seiten));
            return 0;
        }
        List<Component> zeilen = new ArrayList<>();
        zeilen.add(sprache.eineZeile(ShopSatz.SHOP_LISTE_KOPF,
                Einsatz.von("anzahl", meine.size()).mit("seite", seite)
                        .mit("seiten", seiten)));
        int erste = (seite - 1) * jeSeite;
        for (Kistenshop shop : meine.subList(erste,
                Math.min(erste + jeSeite, meine.size()))) {
            zeilen.add(zeileFuer(shop));
        }
        if (seite < seiten) {
            zeilen.add(sprache.eineZeile(ShopSatz.SHOP_LISTE_WEITER,
                    Einsatz.von("naechste", seite + 1)));
        }
        sendeZeilen(zusammenhang, zeilen);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Eine Zeile der Shopliste - Stelle, Ware und die zwei Preisstuecke.
     *
     * <p><b>verkauftWare gehoert zum KAUFstueck</b>, und das ist kein Vertipper: Die
     * Satznamen sind aus Kundensicht, die Methoden des Kerns aus Shopsicht. Wenn der
     * Shop verkauft, kauft der Kunde. Genau diese Verdrehung war am 08.11.2026 ein
     * Fehler in {@link Shoptext} - vertauscht bekam ein Shop, der nur ankauft, "Kauf
     * 0 Mark" zu lesen, und eine 0 als Preis ist das, was {@code Shopschild} als
     * {@code PREIS_BEIDE_NULL} abweist.
     *
     * <p>Ein Shop handelt mindestens in EINE Richtung - beide Preise 0 weist das Schild
     * ab. Eine Zeile ganz ohne Preisstueck gibt es also nicht.
     */
    private Component zeileFuer(Kistenshop shop) {
        return sprache.eineZeile(ShopSatz.SHOP_LISTE_ZEILE,
                Einsatz.von("welt", shop.schildWelt())
                        .mit("x", shop.schildX())
                        .mit("y", shop.schildY())
                        .mit("z", shop.schildZ())
                        .mit("menge", shop.menge())
                        .mit("ware", shop.ware().name())
                        .mit("kauf", preisstueck(ShopSatz.SHOP_LISTE_KAUF,
                                shop.verkauftWare(), shop.kaufpreis()))
                        .mit("verkauf", preisstueck(ShopSatz.SHOP_LISTE_VERKAUF,
                                shop.kauftWare(), shop.verkaufpreis())));
    }

    /** Ein Preisstueck, oder ein leerer Text - der verschwindet rueckstandslos. */
    private String preisstueck(ShopSatz satz, boolean handelt, long preis) {
        return handelt
                ? sprache.wort(satz, Einsatz.von("preis", sprache.betrag(preis)))
                : "";
    }

    /**
     * Der Shop im Blick - wenn er diesem Spieler gehoert.
     *
     * <p>Gibt {@code null} und hat dann schon abgesagt. Die Reihenfolge der Absagen ist
     * nach Gewissheit geordnet: kein Block, kein Shop, Bundshop, fremder Shop. So liest
     * der Spieler nie "gehoert dir nicht", wenn er in Wahrheit in die Luft schaut.
     */
    private Kistenshop meinShopImBlick(CommandContext<CommandSourceStack> zusammenhang,
                                       Player spieler) {
        Block block = blickfang.imBlick(spieler, REICHWEITE);
        if (block == null) {
            sende(zusammenhang, ShopSatz.SHOP_KEIN_SCHILD,
                    Einsatz.von("reichweite", REICHWEITE));
            return null;
        }
        Kistenshop shop;
        try {
            shop = ablage.anDemSchild(block.getWorld().key().asString(), block.getX(),
                    block.getY(), block.getZ());
        } catch (RuntimeException fehler) {
            protokoll.log(Level.WARNING, "Shop am Schild nicht lesbar - der Befehl hat"
                    + " abgesagt.", fehler);
            sende(zusammenhang, ShopSatz.SHOP_FREIGABE_GESTOERT, Einsatz.keine());
            return null;
        }
        if (shop == null) {
            sende(zusammenhang, ShopSatz.SHOP_KEIN_SCHILD,
                    Einsatz.von("reichweite", REICHWEITE));
            return null;
        }
        if (shop.demBund()) {
            sende(zusammenhang, ShopSatz.SHOP_BUND_NICHT_FREIGEBBAR,
                    Einsatz.keine());
            return null;
        }
        if (!shop.gehoert(spieler.getUniqueId())) {
            // NUR der Besitzer, und ausdruecklich nicht ueber offenFuer: Das Teamrecht
            // oeffnet eine Kiste, damit ein verlassener Laden abgebaut werden kann - es
            // macht niemanden zum Hausherrn. Und ein schon Freigegebener darf den
            // Schluessel nicht weitergeben.
            sende(zusammenhang, ShopSatz.SHOP_NICHT_DEINER, Einsatz.keine());
            return null;
        }
        return shop;
    }

    /** Schreibt die neue Liste - und sagt ab, wenn die Ablage schweigt. */
    private boolean schreibe(CommandContext<CommandSourceStack> zusammenhang,
                             Kistenshop shop, Set<UUID> neue) {
        try {
            ablage.schreibe(shop.mitFreigaben(neue));
            return true;
        } catch (RuntimeException fehler) {
            // Eine Stoerung darf nicht als Erfolg aussehen: Der Besitzer glaubte sonst,
            // jemanden eingetragen zu haben, und der steht spaeter vor einer
            // verschlossenen Kiste - oder, schlimmer, beim Sperren: Er glaubte, jemanden
            // ausgesperrt zu haben, und der kommt weiter hinein.
            protokoll.log(Level.WARNING, "Freigaben von " + shop.schluessel()
                    + " nicht schreibbar - nichts geaendert.", fehler);
            sende(zusammenhang, ShopSatz.SHOP_FREIGABE_GESTOERT, Einsatz.keine());
            return false;
        }
    }

    /** Vorschlaege fuer {@code /shop sperren} - die, die wirklich eingetragen sind. */
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion
            .Suggestions> schlageFreigegebeneVor(
                    CommandContext<CommandSourceStack> zusammenhang,
                    com.mojang.brigadier.suggestion.SuggestionsBuilder bauer) {
        if (zusammenhang.getSource().getExecutor() instanceof Player spieler) {
            Block block = blickfang.imBlick(spieler, REICHWEITE);
            if (block != null) {
                try {
                    Kistenshop shop = ablage.anDemSchild(
                            block.getWorld().key().asString(), block.getX(),
                            block.getY(), block.getZ());
                    if (shop != null) {
                        for (UUID wer : shop.freigegeben()) {
                            bauer.suggest(namen.von(wer));
                        }
                    }
                } catch (RuntimeException fehler) {
                    // Ein Vorschlag ist eine Bequemlichkeit, keine Auskunft: Faellt die
                    // Ablage aus, kommt eben keiner. Eine Meldung ins Protokoll waere
                    // hier Laerm - der Zweig selbst sagt gleich ab.
                    return bauer.buildFuture();
                }
            }
        }
        return bauer.buildFuture();
    }

    /**
     * Geantwortet wird dem SENDER und nicht dem Executor.
     *
     * <p>Bei {@code /execute as <spieler> run shop freigaben} schaut der Spieler das
     * Schild an, lesen will es der, der getippt hat. Dieselbe Teilung wie bei den
     * Rechten und dieselbe wie in {@link BlockBefehle}.
     */
    private void sende(CommandContext<CommandSourceStack> zusammenhang,
                       ShopSatz satz, Einsatz werte) {
        sendeZeilen(zusammenhang, sprache.bau(satz, 1L, werte));
    }

    /**
     * Die EINE Stelle, an der dieser Befehl etwas abschickt.
     *
     * <p>Alle vier Zweige und jede ihrer Absagen laufen hier durch - dieselbe
     * Hausregel wie bei {@link LadenBefehle}. Haette die Liste von
     * {@code /shop freigaben} ihren eigenen Weg, waere sie die erste, deren Zeilen keine
     * Bildtafel haelt, und eine weggefallene Sendung bliebe unsichtbar.
     */
    private static void sendeZeilen(CommandContext<CommandSourceStack> zusammenhang,
                                    List<Component> zeilen) {
        zeilen.forEach(zusammenhang.getSource().getSender()::sendMessage);
    }

    /**
     * Die Fassung fuers Spiel: Blick, Namenssuche und Namensgeber ueber Bukkit.
     *
     * <p>An EINER Stelle gebaut, damit die drei Nahtstuecke beisammen stehen - sie
     * beantworten dieselbe Frage "wer und was ist da draussen".
     */
    static ShopBefehle ueberBukkit(Shopablage ablage, Sprache sprache,
                                   Wirtschaftsstand wirtschaft, Logger protokoll) {
        return new ShopBefehle(ablage, sprache,
                (wer, reichweite) -> wer.getTargetBlockExact(reichweite),
                ShopBefehle::kennungUeberBukkit, Spielername::von, wirtschaft,
                protokoll);
    }

    /**
     * Die Kennung zu einem getippten Namen - oder {@code null}.
     *
     * <p>{@code hasPlayedBefore()} ist die Schranke: {@code getOfflinePlayer(String)}
     * gibt NIEMALS null, sondern erfindet bei einem unbekannten Namen ein Exemplar mit
     * frisch gewuerfelter Kennung. Ohne diese Frage traege ein Tippfehler eine Freigabe
     * fuer einen Spieler ein, den es nicht gibt - und die stuende fuer immer in der
     * Liste, ohne dass jemand sie je einloesen koennte.
     */
    private static UUID kennungUeberBukkit(String name) {
        Player anwesend = Bukkit.getPlayerExact(name);
        if (anwesend != null) {
            return anwesend.getUniqueId();
        }
        OfflinePlayer bekannt = Bukkit.getOfflinePlayer(name);
        return bekannt.hasPlayedBefore() ? bekannt.getUniqueId() : null;
    }
}
