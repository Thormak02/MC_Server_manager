package de.freimarken.paper;

import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Rasterplan;
import de.freimarken.kern.Tat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
// Liegt in .entity, nicht in .player - anders als sein Gegenstueck PlayerUnleashEntityEvent.
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Der Schutz der Parzellen.
 *
 * <p>Die Regel steht genau einmal da: {@link #darfHandeln}. Alles andere in dieser Klasse
 * sind Zugaenge zu dieser einen Regel - Bloecke setzen, Bloecke abbauen, Eimer, Feuer,
 * Bilderrahmen, Tuerklinken. Es gibt viele Wege, fremden Grund zu veraendern, und jeder
 * einzelne davon muss dieselbe Antwort bekommen. Haette jeder Weg seine eigene Pruefung,
 * waere einer davon irgendwann anders - und das waere die Luecke.
 *
 * <p>Noch NICHT abgedeckt, bewusst benannt statt stillschweigend offen gelassen: TNT und
 * andere Explosionen, Feuerausbreitung, Kolben, die ueber eine Grenze schieben, Wasser- und
 * Lavafluss von aussen, Enderman und Ziegen, Trichter, die in fremde Kisten greifen. Das
 * sind Weltregeln, keine Spielerhandlungen, und sie gehoeren zu einem eigenen Schritt.
 *
 * <p>Diese Liste hat einmal zu viel versprochen: Sie fuehrte nur Weltregeln auf und liess
 * damit den Eindruck entstehen, Spielerhandlungen seien vollstaendig gedeckt. Es fehlte
 * jedes Schadensereignis - ein Ruestungsstaender erbt von LivingEntity und stirbt durch
 * Schlagen, ein bestueckter Bilderrahmen gibt beim ersten Treffer seinen Inhalt her, ohne
 * dass der Rahmen selbst entfernt wird. Beides war mit der Faust zu holen. Daher jetzt
 * {@link #beimWesenSchaden} und {@link #beimFahrzeugSchaden}.
 *
 * <p><b>Seit dem 08.11.2026 beantwortet sie ZWEI Fragen</b>, und beide an derselben
 * Stelle: den Besitz einer Parzelle in ihrer Welt und - ueber {@link Ortsregel} - den
 * Ladenschutz in der Markthalle. Eine zweite Wache daneben waere der naheliegende Weg
 * und der falsche: Die fuenfzehn Zugaenge oben muessten dort alle noch einmal stehen,
 * und einer von ihnen waere irgendwann anders. Genau davor warnt der erste Absatz dieser
 * Doku.
 *
 * <p><b>Und eine dritte Frage in der Gegenrichtung: {@link Handelsklick}.</b> Handeln
 * darf jeder, immer und ueberall (entschieden 08.11.2026) - auch im Laden eines anderen,
 * sonst haette ein Kistenshop keine Kunden. Ein Schild ist aber eine {@link Tat}
 * (ueber {@code Tag.ALL_SIGNS}), damit niemand fremde Preise umschreibt. Beides zugleich
 * geht nur, wenn der HANDELSKLICK vor der Besitzfrage abbiegt - und genau das tut
 * {@link #beimKlicken}.
 *
 * <p><b>In BEIDEN Klickrichtungen</b> [08.11.2026]: Ein Kauf ist ein Rechtsklick, ein
 * Verkauf ein Linksklick, und die Tuer stand zuerst nur im Rechtsklickzweig. Damit war
 * der Verkauf in jedem fremden Laden und auf jeder fremden Parzelle abgewiesen - also
 * genau dort, wo Kistenshops stehen.
 *
 * <p>Gueltig nur in der eigenen Welt - was die Parzellen angeht. Die {@link Ortsregel}
 * bringt ihre eigene Welt mit; alles andere geht diese Wache nichts an.
 */
public final class Schutzwache implements Listener {

    /**
     * Eine zweite Frage zu einem Ort - heute der Ladenschutz [08.11.2026].
     *
     * <p>Sie wird VOR dem Besitzbuch gestellt und ist in ihrer Welt das letzte Wort. Das
     * ist kein Vorrang aus Bequemlichkeit: Steht die Markthalle in der Marktstadt - und
     * {@code laeden.welt} erlaubt das -, dann liegt eine Ladeneinheit auf einer Parzelle,
     * und zwei Regeln sagen etwas ueber dieselbe Stelle. Dann gilt die Einheit, denn der
     * Betreiber hat sie dort hingesetzt; die Parzelle regelt, was AUSSERHALB von ihr
     * liegt.
     *
     * <p>Eine Schnittstelle und keine Klasse, anders als bei den Mitfahrern der
     * {@link Spielzeitwache}: Diese Wache liegt im Besitzschutz und soll von der
     * Ladenmiete nichts wissen. Sie fragt, die Umsetzung antwortet.
     */
    public interface Ortsregel {

        /** Was eine Ortsregel ueber eine Stelle sagt. */
        enum Urteil {
            /** Erlaubt, und zwar ENDGUELTIG - das Besitzbuch wird nicht mehr gefragt. */
            ERLAUBT,
            /** Diese Stelle geht die Regel nichts an. Dann entscheidet der Besitz. */
            NICHT_ZUSTAENDIG,
            /** Verboten. Der Handbalken kommt dann aus {@link #grund}. */
            VERBOTEN
        }

        /**
         * Darf dieser Spieler hier DIESES tun?
         *
         * <p>Wirft NICHT. Was eine Umsetzung nicht beantworten kann - eine stumme
         * Datenbank etwa -, beantwortet sie mit {@link Urteil#VERBOTEN} und einer Zeile
         * im Protokoll. Die sichere Richtung: Im Zweifel bleibt fremder Besitz
         * unberuehrt.
         *
         * <p>Die {@link Tat} darf eine Umsetzung ignorieren, und der {@code Ladenschutz}
         * tut genau das: In einem fremden Laden ist auch die Tuer nicht fuer Besucher.
         * Wer sie beachtet, muss sie bei JEDER Tat beantworten - ein Fall, der
         * durchfaellt, ist eine Erlaubnis, die niemand gegeben hat.
         */
        Urteil pruefe(Player spieler, Tat tat, Location ort);

        /**
         * Der Handbalken zu einem {@link Urteil#VERBOTEN} - nur dann gerufen.
         *
         * <p>Die Regel liefert ihn SELBST und nicht die Wache. Sonst muesste die Wache
         * wissen, warum eine fremde Regel nein gesagt hat - und das ist genau das
         * Wissen, das sie nicht haben soll.
         *
         * <p><b>Mit Spieler und Tat seit dem 12.11.2026</b>, und beides aus demselben
         * Grund: Sobald an einem Ort fuer verschiedene Leute Verschiedenes gilt, ist "was
         * liegt hier" keine Begruendung mehr. Ein Stadtbuerger, dem eine Truhe
         * verschlossen bleibt, soll nicht lesen, dass die Stadt einem anderen gehoert -
         * sie gehoert ihm ja mit. {@link #zustaendig} bleibt OHNE beides: Jene Frage
         * trennt "geht mich das an" von "darf DIESER Spieler", und {@link #nacheinander}
         * findet die Regel damit auch dann wieder, wenn hier noch kein Urteil steht.
         */
        Component grund(Player spieler, Tat tat, Location ort);

        /**
         * Ob diese Regel ueber diese Stelle ueberhaupt etwas zu sagen hat.
         *
         * <p><b>Ohne Spieler, und das ist der ganze Zweck</b> [11.11.2026]: Sie trennt
         * die Frage "geht mich das an" von der Frage "darf DIESER Spieler". Noetig wurde
         * sie mit der zweiten Regel: {@link #nacheinander} muss in {@link #grund} die
         * zustaendige Regel wiederfinden, und dort gibt es keinen Spieler mehr. Ein
         * {@code pruefe(null, ort)} an dieser Stelle waere kein Ersatz - eine Umsetzung
         * darf einen fehlenden Spieler als Programmierfehler behandeln und klagen.
         *
         * <p>Muss mit {@link #pruefe} zusammenpassen: Wo das hier {@code false} sagt,
         * sagt jenes {@link Urteil#NICHT_ZUSTAENDIG}. Beides auseinanderlaufen zu lassen
         * hiesse, dass ein Spieler eine Absage ohne Begruendung liest - oder eine
         * Begruendung zu einer Absage, die eine andere Regel gegeben hat.
         */
        boolean zustaendig(Location ort);

        /**
         * Mehrere Regeln als EINE - die erste zustaendige entscheidet.
         *
         * <p>Gebraucht, seit es zwei gibt: den {@code Ladenschutz} in der Markthalle und
         * die {@code Stadtwache} in den Freistaedten. Die Reihenfolge ist hier ohne
         * Bedeutung, weil beide in verschiedenen WELTEN liegen und sich nie
         * ueberschneiden - sie ist trotzdem festgelegt und nicht dem Zufall einer
         * Menge ueberlassen, damit eine dritte Regel eines Tages an einer entschiedenen
         * Stelle eingefuegt wird.
         *
         * <p>Kein Zustand zwischen {@link #pruefe} und {@link #grund}: Beide suchen die
         * zustaendige Regel neu. Ein gemerkter Verweis waere bei zwei Spielern auf
         * zwei Faeden die falsche Regel.
         */
        static Ortsregel nacheinander(Ortsregel... regeln) {
            List<Ortsregel> kette = List.of(regeln);
            return new Ortsregel() {
                @Override
                public Urteil pruefe(Player spieler, Tat tat, Location ort) {
                    for (Ortsregel regel : kette) {
                        Urteil urteil = regel.pruefe(spieler, tat, ort);
                        if (urteil != Urteil.NICHT_ZUSTAENDIG) {
                            return urteil;
                        }
                    }
                    return Urteil.NICHT_ZUSTAENDIG;
                }

                @Override
                public Component grund(Player spieler, Tat tat, Location ort) {
                    for (Ortsregel regel : kette) {
                        if (regel.zustaendig(ort)) {
                            return regel.grund(spieler, tat, ort);
                        }
                    }
                    throw new IllegalStateException("Keine der " + kette.size()
                            + " Ortsregeln haelt sich fuer zustaendig, aber eine hat"
                            + " VERBOTEN gesagt - pruefe und zustaendig sind"
                            + " auseinandergelaufen");
                }

                @Override
                public boolean zustaendig(Location ort) {
                    return kette.stream().anyMatch(regel -> regel.zustaendig(ort));
                }
            };
        }

        /**
         * Eine Regel, die es beim Bauen der Wache noch nicht gibt.
         *
         * <p>Loest denselben Zirkel wie das spaet gelesene Feld beim
         * {@link Handelsklick}: Die {@code Stadtwache} braucht das Freistadtbuch, und
         * das entsteht achtzig Zeilen nach dieser Wache. Liefert der Lieferant
         * {@code null} - weil die Welt der Freistaedte fehlt oder der Start davor
         * abgebrochen ist -, ist die Regel schlicht nie zustaendig.
         */
        static Ortsregel spaet(Supplier<Ortsregel> lieferant) {
            return new Ortsregel() {
                @Override
                public Urteil pruefe(Player spieler, Tat tat, Location ort) {
                    Ortsregel echte = lieferant.get();
                    return echte == null
                            ? Urteil.NICHT_ZUSTAENDIG
                            : echte.pruefe(spieler, tat, ort);
                }

                @Override
                public Component grund(Player spieler, Tat tat, Location ort) {
                    Ortsregel echte = lieferant.get();
                    if (echte == null) {
                        throw new IllegalStateException("Es gibt keine Regel, die diesen"
                                + " Grund nennen koennte - zustaendig hat nein gesagt");
                    }
                    return echte.grund(spieler, tat, ort);
                }

                @Override
                public boolean zustaendig(Location ort) {
                    Ortsregel echte = lieferant.get();
                    return echte != null && echte.zustaendig(ort);
                }
            };
        }
    }

    /**
     * Ob ein Klick ein HANDEL ist - der darf immer und ueberall [08.11.2026].
     *
     * <p>Die Gegenrichtung zur {@link Ortsregel}, und sie steht hier, bevor es
     * Kistenshops gibt. Das ist Absicht: Ein Schild traegt eine {@link Tat}, damit niemand
     * fremde Preise umschreibt - also wuerde der Ladenschutz einem Kunden den Klick auf
     * das Kistenshop-Schild verbieten, und der Laden haette keine Kunden. Eine Luecke,
     * die erst mit den Kistenshops eingebaut wird, bricht den Schutz an einer Stelle auf,
     * die dann niemand mehr ueberblickt; eine, die von Anfang an dasteht, ist eine
     * benannte Tuer.
     *
     * <p>Heute antwortet sie ueberall {@link #KEIN_HANDEL} - es gibt noch keine
     * Kistenshops. Dass die Tuer trotzdem traegt, haelt
     * {@code SchutzwacheTest} fest.
     */
    public interface Handelsklick {

        /**
         * Ist dieser Klick ein Handel an einem Kistenshop?
         *
         * <p>JEDER Klick, nicht nur der Rechtsklick [08.11.2026]: Ein Kauf ist ein
         * Rechtsklick, ein Verkauf ein Linksklick, und beide Zweige fragen hier.
         *
         * <p>Gefragt wird VOR jeder Besitzfrage. Eine Umsetzung, die hier zu leicht ja
         * sagt, macht den ganzen Schutz auf - sie muss also sicher sein, dass an dieser
         * Stelle wirklich ein Shop steht, und zwar an seinem SCHILD und nicht an seiner
         * Kiste. Wirft nicht; im Zweifel nein.
         *
         * <p>Ueber die RICHTUNG entscheidet sie nicht: Ob dieser Shop in dieser
         * Richtung ueberhaupt handelt - ein Ankaufstand verkauft nichts -, sagt der
         * Handelszuhoerer dem Kunden selbst. Eine zweite Stelle, die die Richtung
         * kennt, waere eine zweite Wahrheit darueber, was ein Shop tut.
         */
        boolean istHandel(Player spieler, Block block);
    }

    /** Keine Ortsregel - fuer jeden Server ohne Markthalle und fuer die Proben. */
    public static final Ortsregel KEINE_ORTSREGEL = new Ortsregel() {
        @Override
        public Urteil pruefe(Player spieler, Tat tat, Location ort) {
            return Urteil.NICHT_ZUSTAENDIG;
        }

        @Override
        public Component grund(Player spieler, Tat tat, Location ort) {
            // Wird nie gerufen: pruefe sagt nie VERBOTEN. Ein Wurf statt eines leeren
            // Balkens, damit ein Aufrufer, der das doch tut, es sofort merkt.
            throw new IllegalStateException("KEINE_ORTSREGEL verbietet nichts - wer hier"
                    + " einen Grund will, hat die Reihenfolge in darfHandeln umgestellt");
        }

        @Override
        public boolean zustaendig(Location ort) {
            return false;
        }
    };

    /** Kein Handel - der heutige Stand, solange es keine Kistenshops gibt. */
    public static final Handelsklick KEIN_HANDEL = (spieler, block) -> false;

    /** Wer das hat, baut ueberall. Fuer das Team - die Regel selbst bleibt unangetastet. */
    public static final String UEBERALL = "freimarken.bauen.ueberall";

    /** Eine Absage je Spieler und Pause. Ohne das wird aus Abbauhalten ein Chatregen. */
    private static final long MELDEPAUSE_MS = 2_000L;

    /** So selten wird ein Ausfall der Ablage ins Protokoll geschrieben. */
    private static final long KLAGEPAUSE_MS = 30_000L;

    /**
     * Bloecke, deren Anklicken etwas veraendert, die aber weder Behaelter noch
     * {@link Openable} noch {@link Powerable} sind.
     *
     * <p>Das ist die Stelle, an der nachgetragen wird, wenn doch noch etwas durchrutscht.
     */
    private static final Set<Material> HEIKEL = Set.of(
            Material.COMPOSTER, Material.RESPAWN_ANCHOR, Material.DRAGON_EGG,
            Material.BEEHIVE, Material.BEE_NEST, Material.SWEET_BERRY_BUSH,
            Material.BELL, Material.CAKE, Material.NOTE_BLOCK, Material.JUKEBOX,
            Material.LECTERN, Material.COMPARATOR, Material.REPEATER,
            Material.DAYLIGHT_DETECTOR, Material.TNT, Material.FARMLAND,
            Material.TURTLE_EGG, Material.SNIFFER_EGG,
            // Nachgetragen: Keines davon ist Behaelter, Openable oder Powerable. Das
            // Leuchtfeuer liess seine Wirkung umstellen, auf dem Lagerfeuer liess sich
            // fremdes Fleisch braten - und ein nicht gewachstes Schild auf einer
            // Ladenparzelle liess jeder Besucher umschreiben, samt der Preise darauf.
            Material.BEACON, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.DECORATED_POT, Material.CHISELED_BOOKSHELF);

    /** Ganze Blockgruppen, damit nicht jede Topfpflanze einzeln aufgezaehlt werden muss. */
    private static final List<Tag<Material>> HEIKLE_GRUPPEN = List.of(
            Tag.FLOWER_POTS, Tag.CANDLES, Tag.CANDLE_CAKES, Tag.CAULDRONS, Tag.BEDS,
            // ALL_SIGNS deckt Steh- und Wandschilder samt Haengeschildern in allen
            // Holzarten ab; CAVE_VINES die Leuchtbeeren, die sonst jeder abernten konnte.
            Tag.ALL_SIGNS, Tag.CAVE_VINES);

    /**
     * Gegenstaende, die einen Block veraendern, ohne dass ein Setz- oder Abbauereignis
     * ausgeloest wird - Hacke, Schaufel, Axt, Feuerzeug, Knochenmehl und Verwandte.
     */
    private static final Set<Material> WERKZEUG = Set.of(
            Material.FLINT_AND_STEEL, Material.FIRE_CHARGE, Material.BONE_MEAL,
            Material.ARMOR_STAND, Material.END_CRYSTAL, Material.ITEM_FRAME,
            Material.GLOW_ITEM_FRAME, Material.PAINTING, Material.LEAD,
            Material.GLASS_BOTTLE, Material.SHEARS, Material.HONEYCOMB,
            Material.INK_SAC, Material.GLOW_INK_SAC);

    private final Besitzbuch buch;

    /**
     * Die Welt als Schluesseltext, etwa {@code freimarken:marktstadt}.
     *
     * <p>Bewusst als Text und nicht als {@code NamespacedKey}: {@code World.key()} liefert
     * ein {@code Key}, und {@code NamespacedKey.equals(Key)} ist immer falsch. Dieser
     * Vergleich wuerde uebersetzen und jede Welt fuer fremd halten - der Schutz waere
     * lautlos aus. Derselbe Text steht im Besitzbuch und in der Datenbank, also gibt es nur
     * eine Darstellung und nichts umzurechnen.
     */
    private final String welt;

    private final Map<UUID, Long> letzteMeldung = new HashMap<>();
    private long letzteKlage;

    /**
     * Die Saetze dieses Plugins - verdrahtet, bevor der erste Satz umzieht.
     *
     * <p>Warum alle elf Sender dieses Feld in EINEM Zug bekommen und nicht je
     * Bauschritt, steht dort, wo sie gebaut werden: {@code FreimarkenPlugin.onEnable}.
     */
    private final Sprache sprache;

    /**
     * Die zweite Frage - siehe {@link Ortsregel}. Nie {@code null}:
     * {@link #KEINE_ORTSREGEL} ist die Antwort fuer einen Server ohne Markthalle.
     */
    private final Ortsregel ortsregel;

    /** Die Gegenrichtung - siehe {@link Handelsklick}. Nie {@code null}. */
    private final Handelsklick handel;

    /**
     * Die alte Form mit zwei Fragen weniger - fuer Proben, die nur Parzellen messen.
     *
     * <p>Sie setzt {@link #KEINE_ORTSREGEL} und {@link #KEIN_HANDEL} ein und ist damit
     * genau die Wache von vor dem 08.11.2026. Es gibt sie, damit die Proben des
     * Parzellenschutzes unveraendert bleiben: Eine Probe, die mitgeaendert wird, waehrend
     * das Geprueefte sich aendert, sagt hinterher nicht mehr, was vorher galt.
     */
    public Schutzwache(Besitzbuch buch, String welt, Sprache sprache) {
        this(buch, welt, sprache, KEINE_ORTSREGEL, KEIN_HANDEL);
    }

    public Schutzwache(Besitzbuch buch, String welt, Sprache sprache, Ortsregel ortsregel,
                       Handelsklick handel) {
        if (ortsregel == null || handel == null) {
            throw new IllegalArgumentException("Ortsregel und Handelsklick sind Pflicht -"
                    + " wer keine hat, nimmt KEINE_ORTSREGEL und KEIN_HANDEL. Ein null"
                    + " waere eine Wache, die bei jedem Klick wirft.");
        }
        this.sprache = sprache;
        this.buch = buch;
        this.welt = welt;
        this.ortsregel = ortsregel;
        this.handel = handel;
    }

    // --- Die eine Regel ---------------------------------------------------------

    /**
     * Darf dieser Spieler an dieser Stelle DIESES tun?
     *
     * <p>Antwortet {@code true} fuer alles ausserhalb der eigenen Welt: Dort ist diese Wache
     * nicht zustaendig, und "nicht zustaendig" heisst nicht "verboten".
     *
     * <p><b>Die {@link Tat} geht nur an die {@link Ortsregel}</b> [12.11.2026]. Das
     * Besitzbuch der Marktstadt bekommt sie nicht, und das ist keine Luecke: Auf einer
     * fremden Parzelle ist alle drei dasselbe Nein, und ein Durchreichen waere eine
     * Abstufung, die dort niemand einstellen kann. Wer sie eines Tages auch bei den
     * Parzellen will, aendert EINE Zeile - und muss dann jede Tat beantworten.
     *
     * <p>Fehlt die Antwort, lautet sie nein. Wer nicht nachsehen kann, wem der Grund
     * gehoert, darf dort nicht bauen. Ohne diesen Fang flog eine Ausnahme aus der
     * Datenschicht - Netz weg, Zeitgrenze erreicht - aus dem Ereignishandler heraus; Bukkit
     * schreibt sie ins Protokoll und laesst das Ereignis UNABGEBROCHEN. Ein Aussetzer der
     * Datenbank waere damit eine Luecke im Besitzschutz gewesen, und zwar die unauffaellige
     * Sorte: Gebaut wird, der Eintrag im Protokoll liest sich wie ein technisches Problem.
     */
    public boolean darfHandeln(Player spieler, Tat tat, Location ort) {
        if (tat == null) {
            throw new IllegalArgumentException("Jeder Zugang benennt seine Tat - ein null"
                    + " waere eine Frage, die keine Ortsregel beantworten kann");
        }
        World gewaehlt = ort.getWorld();
        if (gewaehlt == null) {
            return true;
        }
        // Das Teamrecht VOR beiden Fragen: Es gilt ueberall, und "ueberall" heisst seit
        // dem 08.11.2026 auch in der Markthalle. Haenge es nur an den Parzellen, muesste
        // ein Betreiber den Laden eines Spielers kuendigen, um darin aufzuraeumen.
        if (spieler.hasPermission(UEBERALL)) {
            return true;
        }
        // Die Ortsregel ZUERST und in ihrer Welt endgueltig - die Begruendung steht an
        // Ortsregel selbst. NICHT_ZUSTAENDIG ist der Normalfall: Die meisten Orte des
        // Servers sind keine Ladeneinheit.
        Ortsregel.Urteil urteil = ortsregel.pruefe(spieler, tat, ort);
        if (urteil != Ortsregel.Urteil.NICHT_ZUSTAENDIG) {
            return urteil == Ortsregel.Urteil.ERLAUBT;
        }
        if (!welt.equals(gewaehlt.key().asString())) {
            return true;
        }
        try {
            return buch.darfBauen(welt, spieler.getUniqueId(), ort.getBlockX(), ort.getBlockZ());
        } catch (RuntimeException fehler) {
            klageUeberAblage(fehler);
            return false;
        }
    }

    /**
     * Meldet einen Ausfall der Ablage - hoechstens einmal je Pause.
     *
     * <p>Gedrosselt, weil der Fall bei jedem Blockereignis eintreten wuerde: Ohne Drossel
     * schreibt ein ausgefallener Datenbankrechner in Sekunden ein Protokoll voll und
     * verdeckt damit jede andere Meldung.
     */
    private void klageUeberAblage(RuntimeException fehler) {
        long jetzt = System.nanoTime();
        if (letzteKlage != 0L && (jetzt - letzteKlage) / 1_000_000L < KLAGEPAUSE_MS) {
            return;
        }
        letzteKlage = jetzt;
        Bukkit.getLogger().warning("[Freimarken] Besitz nicht nachsehbar, es wird abgelehnt: "
                + fehler.getMessage());
    }

    /**
     * Sagt dem Spieler, warum nicht - hoechstens einmal je Pause.
     *
     * <p>nanoTime und nicht currentTimeMillis: Die Wanduhr laeuft nicht monoton. Wird sie
     * zurueckgestellt - Zeitumstellung, Zeitabgleich - ist die Differenz negativ und damit
     * immer kleiner als die Pause. Die Begruendung bliebe dann dauerhaft stumm, und der
     * Spieler saehe nur, dass nichts passiert.
     */
    public void verweigere(Player spieler, Tat tat, Location ort) {
        long jetzt = System.nanoTime();
        Long zuletzt = letzteMeldung.get(spieler.getUniqueId());
        if (zuletzt != null && (jetzt - zuletzt) / 1_000_000L < MELDEPAUSE_MS) {
            return;
        }
        letzteMeldung.put(spieler.getUniqueId(), jetzt);
        spieler.sendActionBar(grund(spieler, tat, ort));
    }

    private Component grund(Player spieler, Tat tat, Location ort) {
        // Hat die ORTSREGEL verboten, liefert sie auch den Grund. Sonst stuende auf dem
        // Handbalken eines Ladenbesuchers "Diese Parzelle ist noch frei" - eine Auskunft
        // ueber eine Parzelle, die ihn nichts angeht, und zwar in einer Welt, in der es
        // gar keine gibt.
        //
        // Der SPIELER wird mitgegeben und die Frage noch einmal gestellt, statt die
        // Zustaendigkeit als zweite Methode zu fuehren: Zwei Methoden, die
        // uebereinstimmen MUESSEN, sind eine Stelle, an der eine Umsetzung sie eines
        // Tages verschieden beantwortet. Die zweite Frage kostet eine Abfrage, und sie
        // faellt nur bei einer Verweigerung an - die ohnehin auf eine je zwei Sekunden
        // und Spieler gedrosselt ist.
        if (ortsregel.pruefe(spieler, tat, ort) == Ortsregel.Urteil.VERBOTEN) {
            return ortsregel.grund(spieler, tat, ort);
        }
        // Derselbe Leseweg wie darfBauen. Mit parzelleAn hiess die Begruendung auf
        // einer gefuellten Wegflaeche "Weg und Mauer gehoeren allen" - waehrend
        // dieselbe Wache die Hand gerade abgewiesen hatte, weil sie jemandem gehoert.
        Optional<Parzelle> parzelle = buch.zustaendigerBesitz(welt, ort.getBlockX(),
                ort.getBlockZ());
        if (parzelle.isEmpty()) {
            int x = ort.getBlockX();
            int z = ort.getBlockZ();
            if (buch.plan().feldBei(x, z) != Rasterplan.Feld.PARZELLE) {
                return Schutztext.wegUndMauerBalken(sprache);
            }
            return buch.grenze().enthaelt(x, z)
                    ? Schutztext.parzelleFreiBalken(sprache)
                    : Schutztext.ausserhalbBalken(sprache);
        }
        return Schutztext.fremdeParzelleBalken(sprache,
                Spielername.von(parzelle.get().eigentuemer()));
    }

    /**
     * Pruefen und bei Bedarf absagen - was jeder Zugang unten tut.
     *
     * <p>Der Fang um {@link #verweigere} ist kein Schmuck: Die Absage steht in diesem
     * Augenblick schon fest, aber die Begruendung geht ueber {@link #grund} ein zweites
     * Mal zur Ablage. Ist die weg, floege von dort dieselbe Ausnahme, {@code return true}
     * wuerde nie erreicht, und Bukkit liesse das Ereignis unabgebrochen - der Block auf
     * fremdem Grund braeche, weil der Grund dafuer nicht zu beschaffen war.
     */
    private boolean unterbinden(Player spieler, Tat tat, Location ort) {
        if (darfHandeln(spieler, tat, ort)) {
            return false;
        }
        try {
            verweigere(spieler, tat, ort);
        } catch (RuntimeException besitzNichtNachsehbar) {
            klageUeberAblage(besitzNichtNachsehbar);
        }
        return true;
    }

    /** Wie {@link #unterbinden}, nur ohne Meldung - fuer alles, was der Spieler nicht wollte. */
    private boolean unterbindenLeise(Player spieler, Tat tat, Location ort) {
        return !darfHandeln(spieler, tat, ort);
    }

    /**
     * Raeumt die Meldepause eines Spielers weg, der gegangen ist.
     *
     * <p>Sonst waechst die Tabelle mit jedem Spieler, der je da war - langsam, aber
     * unbegrenzt, und niemand sieht es, weil es ja funktioniert.
     */
    @EventHandler
    public void beimVerlassen(PlayerQuitEvent ereignis) {
        letzteMeldung.remove(ereignis.getPlayer().getUniqueId());
    }

    // --- Die Zugaenge ----------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void beimAbbauen(BlockBreakEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), Tat.BAUEN,
                ereignis.getBlock().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /** Faengt auch {@code BlockMultiPlaceEvent} - Betten, Tueren, grosse Blumen. */
    @EventHandler(ignoreCancelled = true)
    public void beimSetzen(BlockPlaceEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), Tat.BAUEN,
                ereignis.getBlock().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void beimAusgiessen(PlayerBucketEmptyEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), Tat.BAUEN,
                ereignis.getBlock().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void beimAufschoepfen(PlayerBucketFillEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), Tat.BAUEN,
                ereignis.getBlock().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /** Zweiter Riegel gegen Feuerzeug und Feuerkugel, falls der Klickweg etwas durchlaesst. */
    @EventHandler(ignoreCancelled = true)
    public void beimAnzuenden(BlockIgniteEvent ereignis) {
        Player spieler = ereignis.getPlayer();
        if (spieler != null && unterbinden(spieler, Tat.BAUEN,
                ereignis.getBlock().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void beimBildAbnehmen(HangingBreakByEntityEvent ereignis) {
        if (ereignis.getRemover() instanceof Player spieler
                && unterbinden(spieler, Tat.BAUEN, ereignis.getEntity().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void beimBildAufhaengen(HangingPlaceEvent ereignis) {
        Player spieler = ereignis.getPlayer();
        if (spieler != null && unterbinden(spieler, Tat.BAUEN,
                ereignis.getEntity().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Ruestungsstaender und Bilderrahmen.
     *
     * <p>Beides sind Wesen, keine Bloecke - sie stehen aber auf fremdem Grund und gehoeren
     * zu dem, was dort gebaut wurde. {@code PlayerArmorStandManipulateEvent} kommt hier mit
     * durch, weil es von diesem Ereignis erbt.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimWesenAnklicken(PlayerInteractEntityEvent ereignis) {
        Entity wesen = ereignis.getRightClicked();
        if (gehoertZumGrundstueck(wesen)
                && unterbinden(ereignis.getPlayer(), tatAmWesen(wesen),
                        wesen.getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Welche Tat ein RECHTSKLICK auf dieses Wesen ist.
     *
     * <p>Dieselbe Trennung wie bei {@link #tatAn}, und aus demselben Merkmal: Eine
     * Kisten-Lore und ein Truhenboot sind {@link InventoryHolder}, also Behaelter. Ein
     * Ruestungsstaender, ein Bilderrahmen und ein Tier sind es nicht - sie werden
     * bestueckt, abgeraeumt oder gefuettert, und das veraendert, was dasteht.
     *
     * <p><b>Ein Spieler ist auch ein {@link InventoryHolder}</b>, und ein Dorfbewohner
     * ebenso - beide fallen hier also auf BEHAELTER. Das ist nicht hergeholt: Wo
     * {@link #gehoertZumGrundstueck} schon ja gesagt hat, ist der Klick auf einen
     * Dorfbewohner ein Blick in sein Angebot. Bis heute war er schlicht abgewiesen.
     *
     * <p>Paketprivat aus demselben Grund wie {@link #tatAn}.
     */
    static Tat tatAmWesen(Entity wesen) {
        return wesen instanceof InventoryHolder ? Tat.BEHAELTER : Tat.BAUEN;
    }

    /**
     * Ist dieses Wesen Teil dessen, was auf der Parzelle steht?
     *
     * <p>{@link InventoryHolder} ist der Grund, warum hier ueberhaupt etwas zu holen ist:
     * Eine Kisten-Lore und ein Truhenboot tragen genau dasselbe Merkmal, an dem
     * {@link #tatAn} einen Behaelterblock erkennt - nur sind sie Wesen und fielen
     * deshalb durch einen Filter, der nur Hanging und ArmorStand kannte. Wer sie als Lager
     * auf seine Parzelle stellt, stellte sie jedem offen.
     *
     * <p>Tiere sind mit dabei: Sie gehoeren zwar niemandem im Sinne des Besitzbuchs, stehen
     * aber im Gehege des Eigentuemers. Ein Besucher, der sie fortleint oder erschlaegt,
     * nimmt ihm etwas weg.
     */
    private static boolean gehoertZumGrundstueck(Entity wesen) {
        return wesen instanceof Hanging
                || wesen instanceof ArmorStand
                || wesen instanceof Vehicle
                || wesen instanceof InventoryHolder
                || wesen instanceof Animals;
    }

    /**
     * Ob ein Klick als HANDEL durchgeht - die Bedingung der benannten Tuer.
     *
     * <p>Eine eigene Methode und kein Zweig in {@link #beimKlicken}, und das ist ein
     * Befund der Einspritzungskampagne vom 08.11.2026: Im Zweig war die Bedingung nur
     * ueber ein {@code PlayerInteractEvent} mit einem Schildblock erreichbar, also blieb
     * jede Einspritzung darauf gruen. Hier ist sie ohne Server und ohne Ereignis zu
     * pruefen - und seit dem 08.11.2026 rufen sie ZWEI Zweige, also trifft eine
     * Einspritzung darauf beide.
     *
     * <p><b>Das {@code !werkzeugHeikel} ist hier WEG</b> [08.11.2026], und das ist die
     * wichtigste Aenderung an dieser Tuer. Es sollte heissen: Wer mit einer Axt auf ein
     * Schild klickt, will nicht kaufen. {@code aendertBlock} sagt aber bei JEDEM
     * Gegenstand ja, der ein Block ist - und wer Stein, Erde oder Holz verkaufen will,
     * haelt genau das in der Hand. Die Tuer waere also fuer die haeufigste Ware
     * ueberhaupt zu geblieben, und der Spielbericht hiesse "der Shop geht manchmal
     * nicht", weil die Absage am Inhalt der Hand hing und nicht am Shop.
     *
     * <p><b>Der Schutz liegt jetzt in der WIRKUNG und nicht in der Verweigerung:</b>
     * Der Handelszuhoerer setzt nach einem Shopklick beide Ergebnisse auf
     * {@code DENY} - der Handel findet statt, die Hand bleibt untaetig, und aus dem
     * Linksklick wird kein Abbau. Siehe {@code Shopklick}. Die halbe Zusage - mit
     * einer Axt in jedem fremden Laden arbeiten - haelt weiter, aber ueber
     * {@link Handelsklick#istHandel}: Die Tuer geht nur an einem SHOPSCHILD auf und
     * an keinem anderen Block.
     *
     * <p>Damit ist die Methode eine Durchreiche, und sie bleibt trotzdem: Sie ist die
     * benannte Stelle, an der zwei Zweige dieselbe Frage stellen.
     */
    static boolean alsHandelDurchlassen(Handelsklick handel, Player spieler,
            Block block) {
        return handel.istHandel(spieler, block);
    }

    /**
     * Schaden an dem, was auf der Parzelle steht.
     *
     * <p>Das war die groesste Luecke: Ein Ruestungsstaender erbt von LivingEntity, ist also
     * KEIN Hanging - er stirbt durch Schlagen und nicht durch einen Hanging-Break. Und ein
     * bestueckter Bilderrahmen gibt beim ersten Treffer seinen Inhalt her, ohne dass der
     * Rahmen selbst entfernt wird; auch dabei faellt kein HangingBreakByEntityEvent. Beides
     * war mit der Faust zu holen, ohne Meldung und ohne Protokolleintrag.
     *
     * <p>Der Schlaeger kann ein Pfeil oder ein Schneeball sein - dann steckt der Spieler im
     * Schuetzen, nicht im Angreifer.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimWesenSchaden(EntityDamageByEntityEvent ereignis) {
        if (!gehoertZumGrundstueck(ereignis.getEntity())) {
            return;
        }
        Player taeter = urheber(ereignis.getDamager());
        // BAUEN auch an einem Truhenboot: Ein Schlag nimmt etwas weg, und wer ein
        // Behaelterwesen zerschlaegt, oeffnet es nicht - er raeumt es ab.
        if (taeter != null && unterbinden(taeter, Tat.BAUEN,
                ereignis.getEntity().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /** Loren und Boote; faengt auch {@link VehicleDestroyEvent}, weil es davon erbt. */
    @EventHandler(ignoreCancelled = true)
    public void beimFahrzeugSchaden(VehicleDamageEvent ereignis) {
        Player taeter = urheber(ereignis.getAttacker());
        if (taeter != null && unterbinden(taeter, Tat.BAUEN,
                ereignis.getVehicle().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /** Tiere mit dem Eimer aus dem Teich schoepfen - Axolotl, Kaulquappe, Fisch. */
    @EventHandler(ignoreCancelled = true)
    public void beimWesenSchoepfen(PlayerBucketEntityEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), Tat.BAUEN,
                ereignis.getEntity().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /** Fremde Tiere fortleinen. */
    @EventHandler(ignoreCancelled = true)
    public void beimLeinen(PlayerLeashEntityEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), Tat.BAUEN,
                ereignis.getEntity().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /** Und fremde Tiere losmachen, damit sie weglaufen. */
    @EventHandler(ignoreCancelled = true)
    public void beimEntleinen(PlayerUnleashEntityEvent ereignis) {
        if (unterbinden(ereignis.getPlayer(), Tat.BAUEN,
                ereignis.getEntity().getLocation())) {
            ereignis.setCancelled(true);
        }
    }

    /**
     * Der Spieler hinter einem Schaden - direkt oder als Schuetze hinter einem Geschoss.
     *
     * @return {@code null}, wenn kein Spieler dahintersteht; dann ist es eine Weltregel und
     *         nicht Sache dieser Wache
     */
    private static Player urheber(Entity schlaeger) {
        if (schlaeger instanceof Player spieler) {
            return spieler;
        }
        if (schlaeger instanceof Projectile geschoss
                && geschoss.getShooter() instanceof Player schuetze) {
            return schuetze;
        }
        return null;
    }

    /**
     * Klicken auf Bloecke: Kisten, Tueren, Hebel - und Werkzeug, das den Block veraendert.
     *
     * <p>Absichtlich nicht jeder Rechtsklick: Wer auf fremdem Grund steht und Brot isst,
     * klickt auch auf einen Block. Unterbunden wird, was den Block oder seinen Inhalt
     * angeht, nicht was der Spieler mit sich selbst tut.
     */
    @EventHandler(ignoreCancelled = true)
    public void beimKlicken(PlayerInteractEvent ereignis) {
        Block block = ereignis.getClickedBlock();
        if (block == null) {
            return;
        }
        if (ereignis.getAction() == Action.PHYSICAL) {
            // Draufgetreten, nicht angeklickt - unterbinden, aber ohne Meldung. Sonst
            // bekommt ein Spieler, der ueber fremdes Ackerland laeuft, bei jedem Schritt
            // eine Absage fuer etwas, das er nicht vorhatte.
            // Die Tat kommt auch hier aus dem Block: Eine Druckplatte ist Powerable und
            // damit BENUTZEN, fremdes Ackerland steht in HEIKEL und ist BAUEN. Wo die
            // Listen nichts wissen, gilt BAUEN - geprueft wird dann wie bisher, nur mit
            // der strengeren der drei Fragen.
            Tat getreten = tatAn(block);
            if (unterbindenLeise(ereignis.getPlayer(),
                    getreten == null ? Tat.BAUEN : getreten, block.getLocation())) {
                ereignis.setCancelled(true);
            }
            return;
        }
        if (ereignis.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Der Linksklick war gar nicht geprueft. Fuer das Abbauen genuegt
            // BlockBreakEvent, aber nicht alles am Linksklick ist ein Abbau: Ein Drachenei
            // springt weg, ein Feuer wird ausgeschlagen, ein Gemaelde faellt. Nur bei
            // heiklen Bloecken eingreifen, sonst bliebe kein normales Abbauen moeglich.
            //
            // Die benannte Tuer steht hier GENAUSO wie im Rechtsklick [08.11.2026]:
            // Lumias Verkauf ist ein Linksklick, und ein Schild ist heikel. Ohne
            // diese Zeile wird jeder Verkauf auf fremdem Grund und in jeder
            // Markthalle vom Bauschutz abgewiesen - also genau dort, wo Kistenshops
            // stehen. Ein Werkzeugtestat gibt es hier nicht und im Rechtsklick auch
            // nicht mehr: siehe alsHandelDurchlassen.
            if (alsHandelDurchlassen(handel, ereignis.getPlayer(), block)) {
                return;
            }
            // BAUEN, obwohl der Block auch eine Tuer sein kann: Ein Linksklick nimmt
            // etwas weg - er schlaegt Feuer aus, loest ein Gemaelde, versetzt ein
            // Drachenei. Die Frage, OB hier ueberhaupt etwas zu schuetzen ist, bleibt
            // dieselbe wie vorher.
            if (tatAn(block) != null
                    && unterbinden(ereignis.getPlayer(), Tat.BAUEN, block.getLocation())) {
                ereignis.setCancelled(true);
            }
            return;
        }
        if (ereignis.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Tat amBlock = tatAn(block);
        boolean werkzeugHeikel = aendertBlock(ereignis.getItem());
        if (amBlock == null && !werkzeugHeikel) {
            return;
        }
        // Die benannte Tuer fuer den Handel [08.11.2026], und sie steht VOR der
        // Besitzfrage: Handeln darf jeder, immer und ueberall. Ein Schild ist heikel
        // (Tag.ALL_SIGNS), damit niemand fremde Preise umschreibt - ohne diese Zeile
        // verbietet derselbe Schutz dem Kunden den Klick auf das Kistenshop-Schild, und
        // der Laden hat keine Kunden.
        //
        // DIESELBE Zeile wie im Linksklickzweig und mit denselben Argumenten: Der
        // Kauf ist ein Rechtsklick, der Verkauf ein Linksklick, und was ein Handel
        // IST, darf nicht zweimal verschieden beantwortet werden.
        if (alsHandelDurchlassen(handel, ereignis.getPlayer(), block)) {
            return;
        }
        Player spieler = ereignis.getPlayer();
        Location ort = block.getLocation();
        // ZWEI FRAGEN UND NICHT EINE [12.11.2026]: Der Block und die Hand sind zwei
        // verschiedene Taten an derselben Stelle, und seit es Rechte je Tat gibt, koennen
        // sie verschieden ausgehen. Mit einer Frage - der strengeren von beiden - kaeme
        // ein Stadtbuerger mit Benutzenrecht durch keine Tuer mehr, solange er eine Axt in
        // der Hand haelt, und eine Axt in der Hand hat fast jeder.
        boolean blockVerboten = amBlock != null && !darfHandeln(spieler, amBlock, ort);
        boolean handVerboten = werkzeugHeikel && !darfHandeln(spieler, Tat.BAUEN, ort);
        if (!blockVerboten && !handVerboten) {
            return;
        }
        verweigere(spieler, blockVerboten ? amBlock : Tat.BAUEN, ort);
        // Getrennt abgesagt: die Hand nur, wenn sie etwas veraendert haette - und der
        // Block auch dann, wenn seine Tat UNBEKANNT ist. Das zweite ist der Stand von
        // vorher und deckt die Luecken der beiden Listen mit: Ein Block, der reagiert und
        // dort nicht steht, reagiert dann trotzdem nicht. Steht er drin, ist die Frage
        // beantwortet, und ein Ja soll auch wirken.
        if (blockVerboten || amBlock == null) {
            ereignis.setUseInteractedBlock(Event.Result.DENY);
        }
        if (handVerboten) {
            ereignis.setUseItemInHand(Event.Result.DENY);
        }
    }

    /**
     * Welche {@link Tat} ein Klick auf diesen Block ist - oder {@code null} fuer keine.
     *
     * <p><b>Aus {@code istHeikel} geworden</b> [12.11.2026] und nicht daneben gestellt:
     * Jene Frage und diese haetten dieselben vier Merkmale abgefragt, und zwei Stellen,
     * die {@link InventoryHolder} pruefen, sind zwei Wahrheiten darueber, was ein
     * Behaelter ist. Die Reihenfolge ist dieselbe wie vorher, also heisst "nicht
     * {@code null}" buchstabengleich dasselbe wie das alte "heikel".
     *
     * <p>{@code null} und kein vierter Fall in der Aufzaehlung: "keine Tat" ist keine
     * Tat, und eine NICHTS-Konstante wuerde eines Tages einer Ortsregel vorgelegt.
     *
     * <p><b>Paketprivat und nicht {@code private}</b>: An dieser Methode haengt, WELCHES
     * Recht ein Zugang braucht, und sie war als {@code istHeikel} von keiner
     * Verhaltensprobe gedeckt - der Grund stand in {@code GrenzeTest} 31x als
     * Einschraenkung. {@code TatTest} fragt sie jetzt unmittelbar.
     */
    static Tat tatAn(Block block) {
        // getState(false): ohne Abbild des Blockzustands. Diese Pruefung laeuft bei jedem
        // Rechtsklick, da ist eine Kopie des Kisteninhalts zu viel Aufwand fuer eine Frage,
        // die nur "ist das ein Behaelter" lautet.
        if (block.getState(false) instanceof InventoryHolder) {
            return Tat.BEHAELTER;
        }
        BlockData daten = block.getBlockData();
        if (daten instanceof Openable || daten instanceof Powerable) {
            return Tat.BENUTZEN;
        }
        // Alles Weitere veraendert den Block oder nimmt etwas aus ihm heraus - ein
        // Komposter, ein Drachenei, ein Bett, ein Schild mit fremden Preisen darauf.
        // BAUEN ist hier auch die sichere Richtung: Was die beiden Listen nicht
        // auseinanderhalten, bleibt gesperrt, bis jemand es benennt.
        if (HEIKEL.contains(block.getType())) {
            return Tat.BAUEN;
        }
        for (Tag<Material> gruppe : HEIKLE_GRUPPEN) {
            if (gruppe.isTagged(block.getType())) {
                return Tat.BAUEN;
            }
        }
        return null;
    }

    private static boolean aendertBlock(ItemStack gegenstand) {
        if (gegenstand == null) {
            return false;
        }
        Material art = gegenstand.getType();
        // Der einzige Eimer, der keinen Block anfasst. Ohne diese Zeile liess sich auf
        // fremdem Grund keine Milch trinken - eine Absage fuer etwas, das niemandem schadet.
        if (art == Material.MILK_BUCKET) {
            return false;
        }
        // DIE BILLIGEN MERKMALE ZUERST, und das ist eine Umstellung mit zwei Gruenden
        // [12.11.2026]: Diese Frage faellt bei jedem Rechtsklick, und eine Mengenabfrage
        // und ein Namensvergleich kosten nichts. {@code Material.isBlock} dagegen geht
        // ueber {@code asBlockType} in die Registry - und WIRFT ohne laufenden Server
        // (gemessen gegen paper-api 26.3). Damit war beimKlicken fuer eine Probe
        // unerreichbar; die Einspritzungsrunde zu Teil D hat gezeigt, was dadurch
        // ungedeckt blieb.
        //
        // Das ERGEBNIS aendert sich nicht: Die drei Bedingungen sind ODER-verknuepft,
        // und keine von ihnen hebt eine andere auf.
        if (WERKZEUG.contains(art)) {
            return true;
        }
        String name = art.name();
        if (name.endsWith("_HOE") || name.endsWith("_SHOVEL") || name.endsWith("_AXE")
                || name.endsWith("_BUCKET") || name.endsWith("_SPAWN_EGG")
                || name.endsWith("_DYE")) {
            return true;
        }
        return art.isBlock() && !art.isAir();
    }
}
