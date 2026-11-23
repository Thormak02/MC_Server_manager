package de.freimarken.paper;

import de.freimarken.daten.Datenbank;
import de.freimarken.daten.MariaAbholfach;
import de.freimarken.daten.MariaAnmeldungen;
import de.freimarken.daten.MariaRundgang;
import de.freimarken.daten.MariaBerufe;
import de.freimarken.daten.Kassentabellen;
import de.freimarken.daten.MariaRaenge;
import de.freimarken.daten.MariaBlockbuch;
import de.freimarken.daten.MariaBonus;
import de.freimarken.daten.MariaLose;
import de.freimarken.daten.MariaWettbewerb;
import de.freimarken.daten.MariaGrundstuecke;
import de.freimarken.daten.MariaKasse;
import de.freimarken.daten.MariaLager;
import de.freimarken.daten.MariaMiete;
import de.freimarken.daten.MariaShops;
import de.freimarken.daten.MariaSpielzeit;
import de.freimarken.daten.MariaTruhe;
import de.freimarken.daten.MariaWolken;
import de.freimarken.kern.Abholablage;
import de.freimarken.kern.Anmeldeablage;
import de.freimarken.kern.Anmeldung;
import de.freimarken.kern.Rundgang;
import de.freimarken.kern.Rundgangsablage;
import de.freimarken.kern.Rundgangsverzeichnis;
import de.freimarken.kern.Stadtplan;
import de.freimarken.kern.Absteckbuch;
import de.freimarken.kern.Berufsablage;
import de.freimarken.kern.Berufsverzeichnis;
import de.freimarken.kern.Berufswerte;
import de.freimarken.kern.Bautenverzeichnis;
import de.freimarken.kern.BesitzLager;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Blockbuchablage;
import de.freimarken.kern.Blockbuchwerte;
import de.freimarken.kern.Aufgabenbuch;
import de.freimarken.kern.Biombuch;
import de.freimarken.kern.Blockmarken;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Bonusablage;
import de.freimarken.kern.Losablage;
import de.freimarken.kern.Lotteriebuch;
import de.freimarken.kern.Wettbewerbsablage;
import de.freimarken.kern.Wettbewerbsbuch;
import de.freimarken.kern.Bundankauf;
import de.freimarken.kern.Chunkfeld;
import de.freimarken.kern.Feldgrenze;
import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Grenzregel;
import de.freimarken.kern.Grundstuecksbuch;
import de.freimarken.kern.Inselbuch;
import de.freimarken.kern.Inselgrenze;
import de.freimarken.kern.Inselplan;
import de.freimarken.kern.Handelsart;
import de.freimarken.kern.Goldkasse;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Kistenshop;
import de.freimarken.kern.Ladeneinheit;
import de.freimarken.kern.Ladenverzeichnis;
import de.freimarken.kern.Muenzhaus;
import de.freimarken.kern.Muenzwerte;
import de.freimarken.kern.Messwerte;
import de.freimarken.kern.Mietablage;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.Punktebuch;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Platzhalterbau;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Rasterplan;
import de.freimarken.kern.SpeicherAbholfach;
import de.freimarken.kern.Rangablage;
import de.freimarken.kern.Rangschau;
import de.freimarken.kern.SpeicherBerufe;
import de.freimarken.kern.SpeicherBlockbuch;
import de.freimarken.kern.SpeicherAnmeldungen;
import de.freimarken.kern.SpeicherRundgang;
import de.freimarken.kern.SpeicherRaenge;
import de.freimarken.kern.SpeicherBonus;
import de.freimarken.kern.SpeicherLose;
import de.freimarken.kern.SpeicherWettbewerb;
import de.freimarken.kern.SpeicherGrundstuecke;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.SpeicherMiete;
import de.freimarken.kern.SpeicherShops;
import de.freimarken.kern.SpeicherSpielzeit;
import de.freimarken.kern.SpeicherTruhe;
import de.freimarken.kern.SpeicherWolken;
import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Stadtfeldplan;
import de.freimarken.kern.Stadtkasse;
import de.freimarken.kern.Stadtgrenze;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Truhenablage;
import de.freimarken.kern.Truhenverzeichnis;
import de.freimarken.kern.Warpverzeichnis;
import de.freimarken.kern.Wechselbuch;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wolkenbuch;
import de.freimarken.kern.Wolkengrenze;
import de.freimarken.kern.Wolkenplan;
import de.freimarken.kern.Zeitwerte;
import de.freimarken.kern.ZwischenspeicherBerufe;
import de.freimarken.kern.ZwischenspeicherGrundstuecke;
import de.freimarken.kern.ZwischenspeicherLager;
import de.freimarken.kern.ZwischenspeicherShops;
import de.freimarken.kern.ZwischenspeicherWolken;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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

    /** Der Name, unter dem die Marktstadt in der Weltenliste steht. */
    private static final String MARKTSTADT = "marktstadt";

    /**
     * Die Welt, in der die Tore stehen und ein neuer Spieler ankommt.
     *
     * <p>Rueckfall, wenn {@code reisen.ankunftswelt} fehlt. Plan Abschnitt 2:
     * „Hauptstadt (Welt mit Spawn) = Altfurt".
     */
    private static final String ANKUNFTSWELT_VORGABE = "altfurt";

    /**
     * Wie lange eine Teleportanfrage steht.
     *
     * <p>Eine Konstante und kein Einstellschluessel: Eine Minute ist der Wert, den die
     * verbreiteten Umsetzungen nehmen, und ein Schalter, den niemand dreht, ist eine
     * Zeile Anleitung mehr fuer nichts.
     */
    private static final java.time.Duration ANFRAGE_GUELTIG = java.time.Duration.ofMinutes(1);

    private final Rasterplan plan = Rasterplan.marktstadt();
    private final MarktstadtGenerator generator = new MarktstadtGenerator(plan);

    /**
     * Der Generator der Freistaedte - Stadtfelder in der Leere [11.11.2026].
     *
     * <p>Ein Feld, kein neues Exemplar je Welt: Bukkit fragt den Generator aus mehreren
     * Faeden, und er hat keinen veraenderlichen Zustand. Zwei Exemplare waeren zwei
     * Rechnungen fuer dieselbe Welt.
     */
    private final StadtfelderGenerator stadtfelder =
            new StadtfelderGenerator(Stadtfeldplan.freistaedte());

    /**
     * Der Generator von Hafenwerder - gleiche Inseln in gleichem Abstand [18.11.2026].
     *
     * <p>Ein Feld wie beim Nachbarn darueber, und aus demselben Grund: Bukkit fragt den
     * Generator aus mehreren Faeden, und er hat keinen veraenderlichen Zustand. Zwei
     * Exemplare waeren zwei Rechnungen fuer dieselbe Welt.
     */
    private final InselnGenerator inseln =
            new InselnGenerator(Inselplan.hafenwerder());

    /**
     * Der Generator von Wolkenwerder - Leere mit einem einzigen Boden [18.11.2026].
     *
     * <p>Ein Feld wie bei den drei Nachbarn darueber, aus demselben Grund. Er erzeugt nur
     * das Ankunftsfeld; jede Startinsel setzt das Plugin, wenn jemand sie nimmt.
     */
    private final WolkenGenerator wolken =
            new WolkenGenerator(Wolkenplan.wolkenwerder());

    /**
     * Die EINE Wanduhr des Plugins.
     *
     * <p>Fest UTC, und die Zone kommt getrennt aus {@link Zeitwerte} - kein
     * {@code Clock.withZone}. Jede Stelle, die einen Zeitpunkt braucht, bekommt diese Uhr
     * gereicht; so steht in keiner Rechnung DIESES Moduls ein {@code Instant.now()}, und die
     * Rechnung ist ohne Server pruefbar.
     *
     * <p>Fuer das ganze Programm gilt der Satz NICHT, und das muss hier stehen, weil der
     * naechste Leser sonst nach einer zweiten Zeitquelle nicht sucht: {@code MariaLager}
     * liest die Systemuhr an zwei Stellen selbst - beim Anlegen einer Parzelle
     * (MariaLager.java:136) und beim Anlegen einer Gruppe (:359), beides fuer die Spalte
     * {@code erworben_am} - und ist von dieser Uhr nicht erreichbar. Wer einem Zeitfehler
     * nachgeht, etwa einer Parzelle mit einem Kaufdatum aus der Zukunft nach einer
     * Zeitkorrektur, muss dort ebenfalls nachsehen. Die beiden Stellen umzustellen ist ein
     * eigener Schritt und war nicht Teil C.
     */
    private final Clock uhr = Clock.systemUTC();

    private World marktstadt;
    private Datenbank datenbank;
    private BesitzLager lager;
    private Kasse kasse;

    /**
     * Die Kasse der STAEDTE - eine zweite Kasse OHNE Startguthaben [Teil E1].
     *
     * <p>Zwei Kassen und nicht eine, und das ist der Kern der Sache: {@code guthaben}
     * gibt fuer ein unbekanntes Konto das Startguthaben zurueck, und jede Buchung legt
     * ein fehlendes Konto mit diesem Betrag an. Mit der Spielerkasse (ab Werk 50.000
     * Mark) haette die erste Gutschrift an eine Stadt ihr 50.000 Mark geschenkt - und
     * jede einzelne Buchung haette dabei richtig ausgesehen.
     *
     * <p>Mit Datenbank liegen beide Kassen auf DERSELBEN Tabelle; sie unterscheiden
     * sich nur darin, mit welchem Betrag sie ein neues Konto anlegen. Ohne Datenbank
     * sind es zwei getrennte Karten, und das ist kein Unterschied in der Sache: Eine
     * Stadtkennung und eine Spielerkennung sind nie dieselbe.
     *
     * <p>Dass hier wirklich eine Kasse ohne Startguthaben steht, prueft
     * {@link Stadtkasse} selbst - im Konstruktor, mit einer Frage, beim Start.
     */
    private Stadtkasse stadtkasse;

    /**
     * Die Kasse der GOLDMARK - die zweite Waehrung [Schritt 12 Teil E].
     *
     * <p>EIGENER TYP und nicht ein zweites Feld vom Typ {@link Kasse}: Zwei
     * Kassenfelder sehen an jeder Aufrufstelle gleich aus, und eines Tages buchte
     * jemand Mark in die Goldmark. Mit {@link Goldkasse} weist der Uebersetzer die
     * Verwechslung ab, bevor sie Geld bewegt.
     *
     * <p>Mit Datenbank liegt sie auf EIGENEN Tabellen ({@code goldkassen},
     * {@code goldbuchungen}) - anders als die Staedtekasse, die bewusst auf denen
     * der Spieler liegt. Der Grund ist die Waehrung: Zwei Waehrungen in einer
     * Tabelle addieren sich zu einem Stand, und die Summenprobe des Buchungsbuchs
     * wuerde ihn bestaetigen, obwohl er zwei Dinge zusammenwirft.
     *
     * <p>Startguthaben 0, und das ist keine Einstellung: Plan 8.1 nennt genau zwei
     * Quellen fuer Goldmark - Echtgeld und Tausch aus Mark. Dass hier wirklich eine
     * Kasse ohne Startguthaben steht, prueft {@link Goldkasse} selbst, im
     * Konstruktor, beim Start.
     *
     * <p><b>Zwei Verbraucher, eine Kasse:</b> die Wechselstube ({@code Wechselbuch},
     * Teil F) und das Muenzhaus ({@code Muenzhaus}, Teil G). Beide bekommen DIESES
     * Feld - zwei Goldkassen waeren zwei Konten in derselben Waehrung, und die Summe
     * waere auf keiner von beiden der Stand.
     */
    private Goldkasse goldkasse;
    private Spielzeitablage spielzeit;

    /**
     * Die Serien des Tagesbonus - ein Feld, weil der Takt und der Beitritt sie brauchen.
     *
     * <p>Hier liegt kein Geld: Ob heute schon gezahlt wurde, entscheidet das Buchungsbuch.
     * Siehe {@link de.freimarken.kern.Bonusablage}.
     */
    private Bonusablage bonusablage;

    /**
     * Wie viele Lose ein Spieler heute gezogen hat - die einzige Stelle, die es weiss.
     *
     * <p>Anders als beim Tagesbonus liegt hier GELD: Das erste Los am Tag ist gratis und
     * wird mit 0 Mark gebucht, aus dem Buchungsbuch ist die Zahl also nicht zu holen.
     * Siehe {@link de.freimarken.kern.Losablage}.
     */
    private Losablage losablage;

    /** Die Lotterie in Altfurt - sie haelt beide Kassen und die Losablage. */
    private Lotteriebuch lotteriebuch;

    /** {@code /lotterie} - haelt den Wuerfel, denn der gehoert nicht in den Kern. */
    private LotterieBefehle lotterieBefehle;

    /**
     * Anmeldungen und Punkte des Angelwettbewerbs.
     *
     * <p>Wie bei der Losablage liegt hier GELD: Aus den eingezahlten Startgeldern wird
     * der Topf gerechnet. Siehe {@link de.freimarken.kern.Wettbewerbsablage}.
     */
    private Wettbewerbsablage wettbewerbsablage;

    /** Der Angelwettbewerb - Fenster, Punkte, Abrechnung. */
    private Wettbewerbsbuch wettbewerbsbuch;

    /**
     * Die Wache, die Faenge zaehlt UND im Takt abrechnet.
     *
     * <p>Das Feld steht hier, weil die Spielzeitwache es als Mitfahrer braucht - und es
     * muss VOR {@code richteSpielzeitEin()} gesetzt sein. Ein null dort ist ERLAUBT, und
     * genau darum faellt ein vergessener Mitfahrer nicht auf: Er liefe nie, und kein
     * Wettbewerb wuerde je abgerechnet.
     */
    private Wettbewerbswache wettbewerbswache;

    /** {@code /wettbewerb} - Stand, Rangliste, anmelden. */
    private WettbewerbBefehle wettbewerbBefehle;
    private Mietablage mietablage;

    /**
     * Das Abholfach [Teil C] - wo Ware wartet, die ihrem Besitzer nicht zugestellt
     * werden konnte.
     *
     * <p>Hier liegt BESITZ, und zwar der einzige dauerhafte dieses Plugins neben den
     * Parzellen. Was Plan 7.1 ueber ihn sagt, kennt keine Ausnahme: Er wird nie
     * geloescht.
     */
    private Abholablage abholablage;

    /**
     * Das Blockbuch - wer wann wo was getan hat.
     *
     * <p><b>KEIN Zwischenspeicher darum</b>, und das ist die Umkehrung des Falls
     * {@link ZwischenspeicherShops}: Dort ist die Frage "haengt hier ein Shop" ein heisser
     * LESEpfad bei jeder Explosion. Hier ist jede Tat ein SCHREIBzug und wird nur auf
     * Befehl gelesen - ein Zwischenspeicher haette nichts zu sparen und waere eine zweite
     * Wahrheit ueber ein Protokoll. Das Sammeln besorgt {@link Blockbuch}, und zwar vor
     * der Ablage, nicht darin.
     */
    private Blockbuchablage blockbuchablage;
    /**
     * Wer heute wie viel aus der Freien Truhe behalten hat.
     *
     * <p>KEIN Zwischenspeicher darueber, aus demselben Grund wie bei der Spielzeit:
     * Geschrieben wird AUFSCHLAGEND, und ein zwischengespeicherter aufschlagender
     * Schreibzug ist entweder verloren oder doppelt. Gelesen wird zweimal je Griff - beim
     * Oeffnen und beim Schliessen -, und das sind zwei Abfragen je Kistenbesuch, nicht je
     * Tick.
     */
    private Truhenablage truhenablage;

    /**
     * Die Kistenshops - als {@link ZwischenspeicherShops} und nicht als
     * {@code Shopablage}.
     *
     * <p><b>Der Typ ist hier die Sache selbst.</b> Dieses Feld muss {@code fuellen()}
     * koennen, und das steht nur am Zwischenspeicher. Haette es den Schnittstellentyp,
     * braeuchte der Start einen Abwaertstest - und ein vergessenes {@code fuellen()}
     * waere kein Baufehler, sondern ein Wurf beim ersten Blockklick: Der
     * Zwischenspeicher antwortet nicht "ich kenne keinen Shop", sondern wirft, solange er
     * ungefuellt ist. Das ist dort Absicht, denn "hier ist kein Shop" und "ich weiss es
     * noch nicht" sind verschiedene Dinge.
     */
    private ZwischenspeicherShops shopablage;

    /**
     * Die Bauschutzwache - als FELD, seit es Kistenshops gibt.
     *
     * <p>Vorher wurde sie unmittelbar angemeldet und nie wieder gebraucht. Jetzt braucht
     * {@link Shopzugriff} sie: Ob ein Spieler an einer Kistenstelle darf, entscheidet
     * DIESELBE Wache, die ueber jeden Blockklick entscheidet - eine zweite Rechtefrage
     * daneben liefe auseinander.
     *
     * <p>Sie entsteht in einem FRUEHEREN Startschritt als die Shops, und das ist der
     * ganze Grund fuer das Feld: Zwischen den beiden Schritten gibt es keine lokale
     * Variable, die beide sehen.
     */
    private Schutzwache schutzwache;

    /**
     * Der Handelszuhoerer - und damit die Handelstuer der {@link Schutzwache}.
     *
     * <p><b>Ein Feld und nicht ein Konstruktorwert</b>, und das ist eine
     * Notwendigkeit und keine Bequemlichkeit: Die Wache entsteht in
     * {@code richteSchutzEin}, der Shopklick erst in {@link #richteShopsEin} - denn
     * dessen {@code Shopzugriff} braucht die schon fertige Wache. Jeder von beiden
     * braucht also den anderen. Geloest wie beim Tagesbonus zwanzig Zeilen weiter
     * oben: Die Wache bekommt einen Lambda, der das FELD beim Aufruf liest.
     *
     * <p>{@code null} heisst nein, und das deckt genau den Fall, den die
     * Startmeldung von {@code richteShopsEin} schon verspricht: Bei stummer
     * Datenbank gibt es keine Shops, also auch keinen Handel - und dann ist jedes
     * Schild wieder ein gewoehnlicher heikler Block.
     */
    private Shopklick shopklick;

    /**
     * Die Ladenmiete - entsteht erst, wenn Verzeichnis UND Ablage stehen.
     *
     * <p>Ein Feld und keine lokale Variable, weil zwei Stellen sie brauchen: der Befehl
     * {@code /laden} und - ab Teil C - der taegliche Lauf.
     */
    private Mietbuch mietbuch;

    /**
     * Das Abholfach als Naht - ein Feld, weil zwei Stellen es brauchen: der Befehl
     * {@code /laden abholfach} und die {@link Mietwache} beim Beitritt.
     */
    private Abholbuch abholbuch;

    /**
     * Der Mitfahrer im Spielzeittakt [Teil C] - ein Feld, weil der Takt ihn braucht und
     * er VOR der Spielzeitwache entstehen muss.
     */
    private Mietwache mietwache;

    /**
     * Der Tagesbonus - ein Feld, obwohl ihn nur zwei Stellen halten.
     *
     * <p>Der Grund ist die REIHENFOLGE: Die Befehle entstehen vor dem Spielzeittakt, und
     * der Bonus entsteht mit dem Takt. Die Auskunft fuer {@code /mark} liest darum das
     * Feld beim Aufruf und nicht beim Bauen - zu diesem Zeitpunkt steht es.
     */
    private Tagesbonus tagesbonus;

    /**
     * Die Ladeneinheiten der Markthalle - NUR BEIM START gelesen.
     *
     * <p>Ein Ort und kein Wert: Verschiebt ein Neuladen den Bereich einer Einheit, haette
     * ein Mieter von einem Augenblick auf den anderen einen anderen Raum. Dieselbe
     * Entscheidung wie bei den Welten und ihren Toren.
     */
    private Ladenverzeichnis ladenverzeichnis = Ladenverzeichnis.ohneEinheiten();

    /** In welcher Welt die Markthalle steht - ohne Namensraum, wie in der config.yml. */
    private String ladenwelt = Ladenliste.VORGABE_WELT;

    /** In welcher Welt die Stadtfelder liegen - ohne Namensraum, wie in der config.yml. */
    private String freistadtwelt = Freistadtliste.VORGABE_WELT;

    /** In welcher Welt die Inselfelder liegen - ohne Namensraum, wie in der config.yml. */
    private String inselwelt = Inselliste.VORGABE_WELT;

    /** In welcher Welt die Wolkenfelder liegen - ohne Namensraum, wie in der config.yml. */
    private String wolkenwelt = Wolkenliste.VORGABE_WELT;

    /**
     * Die Regel ueber die Meerinseln - {@code null}, solange die Welt fehlt.
     *
     * <p>Dieselbe Form wie beim Freistadtbuch: Ein fehlendes Buch kostet den Start
     * NICHT, {@code /werder} sagt es dann. Aus der Marktstadt kommen die
     * Parzellennummern, aus Hafenwerder kommt nichts, was der Rest des Spiels braucht.
     */
    private Inselbuch inselbuch;

    /**
     * Die Hand, die in Hafenwerder den Fang tauscht - {@code null} ohne Inselwelt.
     *
     * <p>Ein Feld und keine oertliche Groesse, aus demselben Grund wie bei den anderen
     * Wachen: Sie wird am Server angemeldet, und wer sie nur in einer Methode haelt,
     * kann sie beim Abschalten nicht mehr finden.
     */
    private Angelwache angelwache;

    /**
     * Der Schutz von Hafenwerder - {@code null}, solange es das Buch nicht gibt.
     *
     * <p>Wird von der Schutzwache SPAET gelesen, wie die Stadtwache: Sein Buch entsteht
     * erst in {@code richteInselnEin()}, und bis dahin ist die Regel nie zustaendig.
     */
    private Inselwache inselwache;

    /** Die Regel ueber die Wolkeninseln - {@code null}, solange die Welt fehlt. */
    private Wolkenbuch wolkenbuch;

    /** Der Schutz von Wolkenwerder - {@code null}, solange es das Buch nicht gibt. */
    private Wolkenwache wolkenwache;

    /**
     * Punkte, Rangliste und Biomwechsel - {@code null} unter denselben Bedingungen wie
     * {@link #aufgabenbuch}.
     *
     * <p>EIN Buendel und nicht vier Felder, weil die vier zusammen oder gar nicht
     * entstehen - siehe {@link WolkeBefehle.Pflege}.
     */
    private WolkeBefehle.Pflege wolkenpflege;

    /**
     * Die Regel ueber die Herausforderungen - {@code null}, solange die Welt fehlt ODER
     * die Inselstaende nicht zu lesen waren.
     *
     * <p><b>Die zweite Haelfte ist die wichtige</b>: Ein Aufgabenbuch auf einem
     * ungefuellten Zwischenspeicher saehe jede Insel bei Stufe 0 - die Tafel nennte
     * erfuellte Aufgaben als offen, und jeder Abgabeversuch liefe am Riegel der Ablage
     * auf. Lieber keine Tafel, laut gemeldet, als eine, die falsch ist.
     */
    private Aufgabenbuch aufgabenbuch;

    /**
     * Der Zustand der Wolkeninseln - als {@link ZwischenspeicherWolken}, nicht als
     * {@code Wolkenablage}.
     *
     * <p><b>Der Typ ist hier die Sache selbst</b>, wie bei {@link #shopablage}: Dieses
     * Feld muss {@code fuellen()} koennen, und das steht nur am Zwischenspeicher. Haette
     * es den Schnittstellentyp, braeuchte der Start einen Abwaertstest.
     *
     * <p>Und der Zwischenspeicher ist hier kein Sparen, sondern die Sache: Der
     * Kobbelsteingenerator fragt bei JEDEM gebildeten Block nach der Insel-Stufe. Zwei
     * Datenbankabfragen an dieser Stelle waeren derselbe Fehler, den
     * {@link ZwischenspeicherBerufe} schon einmal geloest hat.
     */
    private ZwischenspeicherWolken wolkenstaende;

    /**
     * Die Hand, die eine Startinsel setzt.
     *
     * <p>Ein Feld und kein Wert je Aufruf, weil sie die GEOMETRIE haelt - dieselbe, die
     * der Generator benutzt. Die Welt bekommt sie erst beim Setzen gereicht: Sie kann
     * beim Start gefehlt haben und spaeter da sein.
     */
    private final Wolkenaufbau wolkenaufbau = new Wolkenaufbau(wolken.plan());

    /**
     * Die Regel ueber das Gruenden - {@code null}, solange die Welt fehlt.
     *
     * <p>Ein fehlendes Buch kostet den Start NICHT, anders als bei der Marktstadt: Aus
     * jener kommen die Parzellennummern, ohne sie gibt es kein Parzellensystem. Eine
     * fehlende Freistaedtewelt macht nur {@code /freistadt} nutzlos, und das sagt der
     * Befehl dann selbst. Dasselbe Vorgehen wie bei der Ladenwelt.
     */
    private Freistadtbuch freistadtbuch;

    /**
     * Der Besuchsstempel der Freistaedte - {@code null}, solange es kein Buch gibt.
     *
     * <p>Ein Feld und keine lokale Grosse, aus demselben Grund wie bei stadtwache:
     * Gebaut wird er in richteFreistaedteEin, gebraucht erst in richteSpielzeitEin.
     */
    private Besuchslauf besuchslauf;

    /**
     * Die taegliche Ladengebuehr - {@code null} ohne Freistadtbuch ODER ohne Shops.
     *
     * <p>Gebaut in richteShopsEin, denn dort entsteht das Shopbuch - und gebraucht
     * eine Zeile spaeter im Spielzeittakt. Zwei Bedingungen fuer EIN Feld, und darum
     * steht die Pruefung dort und nicht hier.
     */
    private Ladengebuehrlauf ladengebuehrlauf;

    /** Der taegliche Verfall - {@code null}, solange es kein Freistadtbuch gibt. */
    private Verfallslauf verfallslauf;

    /**
     * Der Bauschutz der Freistaedte - {@code null}, solange es kein Freistadtbuch gibt.
     *
     * <p>Wird von der {@link Schutzwache} SPAET gelesen: Jene entsteht achtzig Zeilen
     * vor {@link #richteFreistaedteEin()}, und ohne Buch gibt es keine Geometrie, nach
     * der diese Regel urteilen koennte. Dieselbe Form wie beim Shopklick daneben, und
     * aus demselben Grund - ein Zirkel, den nur ein spaet gelesenes Feld aufloest.
     */
    private Stadtwache stadtwache;

    /**
     * Die Grundstuecke in den Staedten - mit Zwischenspeicher davor.
     *
     * <p>Wie beim Lager: {@code darfBauen} laeuft bei jedem gesetzten Block, und das
     * darf keine Abfrage ueber das Netz werden. Anders als dort ist dieser
     * Zwischenspeicher VOLLSTAENDIG und ohne Verfall - die Rechnung dazu steht an
     * {@link ZwischenspeicherGrundstuecke}.
     */
    private ZwischenspeicherGrundstuecke grundstuecksablage;

    /** Das Buch der Grundstuecke - {@code null}, wenn die Welt oder das Mass fehlt. */
    private Grundstuecksbuch grundstuecksbuch;

    /**
     * Die abgesteckten Ecken je Spieler - fluechtig.
     *
     * <p>Hier und nicht in den Befehlen: Beim Abmelden wird {@code vergiss} gerufen, und
     * das braucht einen Zuhoerer. Zwei Buecher waeren zwei Absteckungen, und der Spieler
     * saehe seine Ecken je nach Befehl.
     */
    private final Absteckbuch absteckbuch = new Absteckbuch();

    /**
     * Die Ziele von {@code /warp} - ein FELD, weil zwei Stellen sie brauchen.
     *
     * <p>{@code richteReisenEin} baut daraus den Warpbefehl, das Freistadtbuch fragt sie,
     * ob ein Stadtname einer Welt gehoert. Zwei Verzeichnisse waeren zwei Listen
     * derselben Welten, und die zweite bekaeme eine neue Welt nicht mit.
     */
    private Warpverzeichnis warpverzeichnis = Warpverzeichnis.ausWelten(List.of());

    /**
     * Der Weltschluessel zu einem NAMEN aus der {@code config.yml}.
     *
     * <p><b>Die Datei schreibt Namen, die Welt traegt Schluessel.</b> In der
     * {@code config.yml} steht {@code laeden.welt: altfurt} und
     * {@code reisen.ankunftswelt: altfurt}; {@code World.key().asString()} gibt
     * dagegen {@code freimarken:altfurt}. Wer das eine mit dem anderen vergleicht,
     * bekommt IMMER "ungleich" - und je nach Stelle heisst das: eine Absage, die
     * niemand versteht, oder ein Schutz, der schweigend nicht greift.
     *
     * <p><b>Gefunden am 09.11.2026 im Spiel</b>, nicht in einer Probe: {@code /laden
     * mieten} sagte "Die Markthalle steht in Altfurt - dort wird gemietet", mitten
     * in Altfurt. Dahinter steckten noch zwei stumme Fehler derselben Art - der
     * Ladenschutz hielt sich ueberall fuer nicht zustaendig, und {@code Ladenware}
     * fand die Ladenwelt nie, raeumte also keine Truhe. Die Proben sahen nichts: Sie
     * geben den Schluessel schon fertig herein, und damit pruefen sie eine andere
     * Eingabe als die Wirklichkeit.
     *
     * <p>Darum entsteht der Schluessel HIER und an keiner zweiten Stelle. Ein
     * handgeschriebenes {@code "freimarken:" + name} stand vorher bei
     * {@link MarktBefehle} und war richtig - aber genau diese Handarbeit ist es, die
     * an der naechsten Stelle vergessen wird.
     */
    private String weltschluessel(String name) {
        return new NamespacedKey(this, name).asString();
    }
    private Berufsablage berufsablage;

    /**
     * Auf welcher Stufe der Leiter ein Spieler steht - Schritt 12 Teil C.
     *
     * <p>KEIN Zwischenspeicher, anders als bei den Berufen: Gelesen wird einmal je
     * Beitritt und einmal je Taktschlag. Das sind bei zwanzig Spielern keine Zahlen,
     * fuer die sich eine zweite Wahrheit ueber den Rang lohnte.
     */
    private Rangablage rangablage;

    /**
     * Wo die Annahme der Regeln liegt - Plan 10.1 [Schritt 13 Teil C].
     *
     * <p>Kein Zwischenspeicher: Gelesen wird einmal je Beitritt und je Klick an einem
     * Bundstand, und das ist eine Abfrage auf den Hauptschluessel. Ein
     * zwischengespeicherter Wert waere eine zweite Wahrheit darueber, ob jemand
     * zugestimmt hat - und gerade die darf es nicht geben.
     */
    private Anmeldeablage anmeldeablage;

    /**
     * Die EINE Stelle, die sagt, ob ein Spieler noch annehmen muss.
     *
     * <p>EIN Feld, weil drei Wege sie fragen: die Wache beim Beitritt, {@code /regeln}
     * und der Handel am Bundesmarkt. Drei Fassungen derselben Frage wichen eines Tages
     * ab - und dann boete ein Weg etwas an, was ein anderer verweigert.
     */
    private Anmeldung anmeldung;

    /**
     * Wo der Rundgangsfortschritt liegt - Plan 10.2 [Schritt 13 Teil D].
     *
     * <p>Kein Zwischenspeicher: Gelesen wird bei jedem Schritt in einen Stationsbereich,
     * und das ist eine Abfrage auf den Hauptschluessel - aber nur, solange der Spieler
     * wirklich in einem Bereich steht. Ein Zwischenspeicher waere eine zweite Wahrheit
     * darueber, wo jemand war.
     */
    private Rundgangsablage rundgangsablage;

    /**
     * Der Rundgang - die EINE Stelle, die seinen Stand kennt.
     *
     * <p>EIN Feld, weil ihn zwei fragen: die Wache beim Schritt und der Aufstiegslauf als
     * fuenfte Messquelle. Zwei Fassungen derselben Rechnung wichen eines Tages ab - und
     * dann waere ein Spieler aufgestiegen, dem {@code /rang} noch eine fehlende Station
     * anzeigt.
     */
    private Rundgang rundgang;

    /**
     * Die Platzhalterbauten Altfurts - NUR BEIM START gelesen [Plan 2.1, 23.11.2026].
     *
     * <p>Leer heisst: Es wird nichts gesetzt. Das ist erlaubt - wer Altfurt von Hand
     * gebaut hat, traegt nichts ein.
     */
    private Bautenverzeichnis platzhalterbauten = Bautenverzeichnis.ohneBauten();

    /** Die Welt, in der die Platzhalterbauten stehen. */
    private String bautenwelt = "";

    /**
     * Die Hand, die sie setzt.
     *
     * <p>Erst im {@code onEnable} gebaut und nicht beim Laden der Klasse, anders als
     * {@code wolkenaufbau}: Sie braucht die Baustoffe, und die stehen in der
     * {@code config.yml}.
     */
    private Altfurtaufbau altfurtaufbau = new Altfurtaufbau(Baustoffe.abWerk());

    /**
     * Der Plan der gerechneten Stadt - NUR BEIM START gelesen, {@code null} wenn
     * {@code bauten.stadt.bauen: false}.
     *
     * <p>Der Plan RECHNET, er liest nicht: Zweihundert Haeuser von Hand in die
     * {@code config.yml} zu tragen waere nicht nur Arbeit, sondern eine
     * Fehlerquelle - der Betreiber muesste jeden Quader gegen jeden anderen auf
     * gemeinsame Bloecke pruefen. Was er einstellt, ist die GROESSE.
     */
    private Stadtplan stadtplan;

    /** Womit der Boden Altfurts belegt wird - NUR BEIM START gelesen. */
    private Pflasterstoffe pflasterstoffe = Pflasterstoffe.abWerk();

    /**
     * Die Quader, um die das Pflaster herumlaeuft: eingetragene Bauten,
     * Ladeneinheiten und Tore.
     *
     * <p><b>Beim Start gesammelt und nicht beim Pflastern geholt</b>: Dieselbe
     * Liste entscheidet schon, dass kein Bau ein Tor zumauert - sie zweimal
     * zusammenzustellen waeren zwei Wahrheiten darueber, was Handarbeit ist.
     */
    private List<Quader> unberuehrteQuader = List.of();

    /**
     * Das Verzeichnis der Stationen - NUR BEIM START gelesen.
     *
     * <p>Ab Werk leer: Die Gebaeude in Altfurt sind Handarbeit. Ein eigenes Feld, weil
     * die Wache die Welt dazu braucht und der Rundgang das Verzeichnis.
     */
    private Rundgangsverzeichnis rundgangsstationen = Rundgangsverzeichnis
            .ohneStationen();

    /** Die Welt, in der die Stationen liegen - leer, solange es keine gibt. */
    private String rundgangswelt = "";

    /**
     * Die Wache, die aus dem abgelegten Rang Rechte macht.
     *
     * <p>EIN Feld und kein Wert je Aufruf, weil Teil D sie rufen muss: Ein Aufstieg
     * zur Laufzeit wirkt erst, wenn jemand das Buendel neu vergibt. Ohne das waere
     * der Aufstieg eine Nachricht, der nichts folgt.
     */
    private Rangwache rangwache;

    /**
     * Der Lauf, der messen und heben kann - Schritt 12 Teil D.
     *
     * <p>EIN Feld und kein Wert je Aufruf, weil ihn zwei Stellen brauchen: die Rangwache
     * fuer den Takt und den Beitritt, und der Startbericht fuer die Nachstufung.
     */
    private Aufstiegslauf aufstiegslauf;
    /**
     * Welche Blockpositionen ein Spieler gesetzt hat - fuer Teil E.
     *
     * <p>Kein Halter wie {@link #wirtschaft}: Es gibt nichts zu tauschen. Die Marken
     * liegen im Chunk, also in der Weltdatei, und ein Neuladen der Einstellungen aendert
     * daran nichts.
     */
    private Blockmarken marken;
    /**
     * Welche Berufe es gibt - NUR BEIM START gelesen.
     *
     * <p>Kein Halter wie {@link #wirtschaft} und {@link #sprachstand}, und das ist kein
     * Versehen: An dieser Liste haengen die Vervollstaendigung von {@code /beruf}, die im
     * Befehlsbaum steckt und genau einmal angemeldet wird, und die ROLLE jedes Berufes,
     * mit der jede Zeile der Datenbank gelesen wird. Ein Neuladen koennte den Baum nicht
     * austauschen - also waere ein Halter ein Versprechen, das er nicht halten kann.
     * {@code /freimarken neuladen} sagt es in jeder Antwort.
     */
    private Berufsverzeichnis berufe;
    /** Zuhoerer UND Zeitgeber der Spielzeit. */
    private Spielzeitwache spielzeitwache;

    /**
     * Die Messstelle aus Teil G - ein Feld, weil der Bericht sie liest.
     *
     * <p>Anders als das {@link Spielzeitbuch}, das eine lokale Variable bleibt: Das Buch
     * halten die Wache und die Befehle, die Messstelle haelt ausserdem der Zweig
     * {@code /freimarken messstelle}, und der entsteht erst beim Anmelden der Befehle.
     */
    private Messstelle messstelle;
    /**
     * Zuhoerer UND Zeitgeber des Verdienens - der zweite wiederkehrende des Plugins.
     *
     * <p>Ein Feld, weil {@code onDisable} ihn abmelden und ein letztes Mal abrechnen
     * muss: Was im Arbeitsspeicher gesammelt ist, waere sonst verloren.
     */
    private Verdienstwache verdienstwache;
    /**
     * Zuhoerer UND Zeitgeber des Blockbuchs - der dritte wiederkehrende des Plugins.
     *
     * <p>Ein Feld aus demselben Grund wie {@link #verdienstwache}: {@code onDisable} muss
     * ihn abmelden und den Stapel ein letztes Mal wegschreiben. Der haelt bis zu eine
     * Sekunde Spielgeschehen, und gerade die letzte Sekunde ist die, nach der jemand
     * fragt, wenn ein Server unerwartet herunterfaehrt.
     */
    private Blockwache blockwache;
    /**
     * Das Aufraeumen des Blockbuchs - Mitfahrer im Spielzeittakt.
     *
     * <p>Ein Feld, weil {@link #richteSpielzeitEin} es braucht und
     * {@link #richteBlockbuchEin} es baut - in dieser Reihenfolge. Es entsteht AUCH, wenn
     * das Buch nichts schreibt: Alte Zeilen muessen auch dann noch weggehen, sonst waeren
     * sie unloeschbar.
     */
    private Blockraeumung blockraeumung;
    /**
     * Das taegliche Leeren der Freien Truhe - Mitfahrer im Spielzeittakt.
     *
     * <p>Ein Feld aus demselben Grund wie {@link #blockraeumung}:
     * {@link #richteSpielzeitEin} braucht es, {@link #richteTruheEin} baut es - in dieser
     * Reihenfolge. Es entsteht AUCH ohne eingetragene Stelle: Alte Tageszeilen muessen
     * auch dann noch weggehen, sonst waeren sie unloeschbar.
     */
    private Truhenlauf truhenlauf;
    /**
     * Die Stellen der Freien Truhen - gehalten fuer {@link #pruefeFreieTruhen}.
     *
     * <p>Ein Feld, weil die Pruefung erst laufen kann, wenn die Shops gelesen sind, und
     * das ist zwei Startschritte spaeter. {@code null} nur, wenn der Start davor
     * abgebrochen ist.
     */
    private Truhenverzeichnis truhenstellen;
    /**
     * Die gelesenen Blockbuchwerte - fuer den Befehlszweig.
     *
     * <p>Ein Feld, weil {@link #registriereBefehle} sie braucht und das in einem
     * SPAETEREN Ereignis laeuft als {@link #richteBlockbuchEin}. Nie {@code null}: Jene
     * Methode setzt sie als erstes, auch wenn das Buch nichts schreibt - der Befehl muss
     * dann "ist abgeschaltet" sagen koennen, und dafuer braucht er die Werte.
     */
    private Blockbuchwerte blockbuchwerte = Blockbuchwerte.VORGABE;
    private SpielzeitBefehle spielzeitBefehle;
    private Stadtgrenze grenze;
    /**
     * Was die Wirtschaft gerade kostet - die eine Zelle, die {@code /freimarken neuladen}
     * austauscht.
     *
     * <p>Jede Stelle, die mit Geld rechnet, haelt sie und fragt sie beim Gebrauch. Darin
     * liegt der ganze Trick: Ein neuer Wert erreicht jede Stelle, ohne dass ein Zuhoerer,
     * ein Zeitgeber, ein Befehl oder eine Verbindung neu gebaut wird.
     */
    private Wirtschaftsstand wirtschaft;
    /**
     * Was ein Spieler gerade liest - die zweite Zelle, die {@code /freimarken neuladen}
     * austauscht.
     *
     * <p>Dieselbe Form wie {@link #wirtschaft} und aus demselben Grund: Jede sendende
     * Stelle haelt {@link #sprache} als unveraenderliches Feld und fragt den Halter beim
     * Gebrauch. Ein neuer Wortlaut erreicht damit jede Stelle, ohne dass ein Zuhoerer, ein
     * Zeitgeber oder ein Befehl neu gebaut wird.
     */
    private Sprachstand sprachstand;
    /** Der eine Ausgang nach Adventure - haelt nur den Halter darueber. */
    private Sprache sprache;
    /**
     * Die Saetze, wie sie in der Jar stehen - der Rueckfall fuer jede Zeile, die der
     * Betreiber nicht geaendert hat.
     *
     * <p>Als Feld, weil {@code /freimarken neuladen} sie wieder braucht: Ohne sie waere
     * nach einem Neuladen nicht mehr zu sagen, welcher Satz ausgeliefert ist und welcher
     * aus der Datei kommt - und genau diese Unterscheidung traegt die Zusage, dass eine
     * Satzverbesserung aus einem Update einen laufenden Server erreicht.
     */
    private Sprachbuch ausgelieferteSaetze;
    private Besitzbuch buch;
    private ParzellenBefehle befehle;
    private WarpBefehle warpBefehle;
    private RueckwegBefehle rueckwegBefehle;
    private AnfragenBefehle anfragenBefehle;
    /** Die eine Stelle, durch die jeder Teleport laeuft - auch /freimarken parzelle. */
    private Reise reise;
    private Aufwaermzeit aufwaermzeit;
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

        // Die Saetze, bevor irgendetwas sendet. Zwei Dateien, und der Grund ist gemessen:
        // sprache-vorlage.yml wird bei JEDEM Start neu geschrieben und ist vollstaendig,
        // sprache.yml wird einmal LEER angelegt und enthaelt nur, was der Betreiber
        // geaendert hat. Ohne diese Teilung friert die erste vorhandene Datei alle Saetze
        // ein - saveResource ueberschreibt eine vorhandene Datei nie, und
        // Wirtschaftsliste.nenneFehlende hat am 02.11.2026 gemessen, dass auf dem
        // Probeserver 16 von 42 Schluesseln fehlten und trotzdem wirkten. Mit
        // vollstaendig ausgelieferter Datei erreichte eine Satzverbesserung einen
        // laufenden Server nie.
        Sprachliste.schreibeVorlage(this);
        Sprachliste.legeEigeneAn(this);
        try {
            ausgelieferteSaetze = Sprachliste.ausDemJar();
        } catch (RuntimeException bauunfall) {
            // Die EINE Stelle, an der ein Sprachfehler den Start verhindert. Ohne die
            // Vorlage gibt es keinen einzigen Satz, und ein Plugin, das stumm in den Chat
            // sendet, ist schlimmer als eines, das nicht laeuft. Das ist kein
            // Betriebsfall, sondern eine unvollstaendige Jar - dagegen steht VorlageTest,
            // die beim Bauen rot wird.
            getLogger().severe("Freimarken bleibt aus: " + bauunfall.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        var worte = lieseSprache();
        worte.klagen().forEach(getLogger()::severe);
        worte.bericht().forEach(getLogger()::info);
        // Eine Klage laesst buch() auf null - dann gelten die ausgelieferten Saetze, und
        // der Server laeuft. Anders als bei der config.yml, und aus derselben Ueberlegung
        // wie dort: Dort geht es um Geld, und zu billig verkaufte Flaeche liegt binnen
        // Minuten als Mark auf vielen Konten. Ein heimlich ausgeliefertes Wort kostet ein
        // Wort.
        sprachstand = new Sprachstand(
                worte.buch() != null ? worte.buch() : ausgelieferteSaetze);
        sprache = new Sprache(sprachstand);

        // Die DATEI, bevor ein einziger Pfad daraus gelesen wird. getConfig() meldet
        // einen Lesefehler nicht: Bei kaputtem YAML liefert es eine leere Konfiguration
        // mit den Vorgaben aus der Jar, und jeder Pfad liest dann brav den
        // ausgelieferten Wert. Ein Start mit heimlich ausgelieferten Preisen ist der
        // teuerste aller Fehlstarts, denn er macht Mark; ein Start, der ausbleibt,
        // kostet eine Pause.
        String lesefehler = Wirtschaftsliste.lesefehler(getDataFolder());
        if (lesefehler != null) {
            getLogger().severe("Freimarken bleibt aus: " + lesefehler);
            getLogger().severe("Sonst gelten lautlos die ausgelieferten Werte - mit"
                    + " 25000 statt eines eingetragenen hoeheren Grundpreises waere jede"
                    + " Parzelle zu billig, und zu billig verkaufte Flaeche liegt binnen"
                    + " Minuten als Mark auf vielen Konten. Lieber kein Parzellensystem"
                    + " als ein falsch bepreistes.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Die BERUFE vor den Wirtschaftswerten, und das ist keine Geschmacksfrage:
        // Deren Platzzahlen werden gegen die Liste geprueft - wer drei Grundberufe
        // erlaubt und zwei eintraegt, verspricht einen Platz, den niemand fuellen kann.
        // Geprueft wird gegen die LAUFENDE Liste, und das ist beim Start dieselbe.
        var berufsliste = Berufsliste.listeAus(
                getConfig().getConfigurationSection(Berufswerte.PFAD_LISTE));
        berufsliste.klagen().forEach(getLogger()::severe);
        berufe = berufsliste.verzeichnis();
        if (berufe.berufe().isEmpty()) {
            // Wie bei einer fehlenden Marktstadt: Ohne Berufe hat /beruf nichts zu tun,
            // und Teil E haette keine Grundlage. Lieber kein Berufssystem als eines, das
            // einen leeren Zunftsaal zeigt.
            getLogger().severe("Freimarken bleibt aus: Es gibt keinen einzigen brauchbaren"
                    + " Beruf in " + Berufswerte.PFAD_LISTE + ". Die Klagen darueber"
                    + " stehen in den Zeilen davor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        String ohneWort = ersterBerufOhneWort();
        if (ohneWort != null) {
            // BEIM START und nicht beim ersten /beruf: Sonst faellt es mitten im Spiel
            // auf, und zwar als Ausnahme im Chat eines Spielers.
            getLogger().severe("Freimarken bleibt aus: Zum Beruf \"" + ohneWort + "\" gibt"
                    + " es kein Wort. In der Sprachdatei fehlt die Zeile "
                    + new Berufsname(ohneWort).pfad() + " - sprache-vorlage.yml zeigt, wie"
                    + " sie aussieht. Lieber ein verweigerter Start als ein Zunfthaus, in"
                    + " dem \"" + ohneWort + "\" steht.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Berufe: " + berufe.anzahlDerRolle(false) + " Grundberufe und "
                + berufe.anzahlDerRolle(true) + " Spezialisierungen - "
                + String.join(", ", berufe.berufe()) + ". NUR BEIM START gelesen.");

        // Die Wirtschaftswerte vor der Ablage: Das Lesen ist reine Dateiarbeit und
        // braucht weder Datenbank noch Welt - und das Startguthaben steckt darin, das die
        // Kasse zum Bauen braucht. Die Reihenfolge "Ablage vor den Welten" bleibt davon
        // unberuehrt; die Begruendung unten gilt weiter.
        var werte = Wirtschaftsliste.ausKonfiguration(getConfig(), berufe);
        werte.klagen().forEach(getLogger()::severe);
        werte.bericht().forEach(getLogger()::info);
        wirtschaft = new Wirtschaftsstand(werte.werte());
        // Was GILT, sagt der Aufrufer - der Leser kennt nur die Datei. Beim Neuladen
        // uebernimmt uebernimm() diesen Wert nicht und sagt es dort selbst.
        getLogger().info("Startguthaben " + wirtschaft.werte().startguthaben()
                + " Mark - friert bei der Eroeffnung eines Kontos ein.");
        // Die einzige Schranke gegen eine Zone, die gueltig und trotzdem falsch ist: UTC
        // statt Europe/Berlin klagt nirgends, verschiebt die Tagesgrenze aber um eine
        // Stunde. Wer hier bei Nachmittagslicht "00:00 Ortszeit = 00:00 UTC" liest, sieht
        // den Fehler. Die Zeile steht HIER und nicht in Wirtschaftsliste.bericht(): Jener
        // Leser kennt nur die Datei, und die Zone ist nicht neuladbar - beim Neuladen
        // nennte er die Zone aus der Datei und nicht die, die gilt.
        getLogger().info(Tagesgrenze.tageswechselText(uhr.instant(),
                wirtschaft.werte().zeit().zone()));

        // Die Ablage ZUERST. Andersherum stand die Marktstadt schon geladen da, wenn
        // sich das Plugin wegen einer unerreichbaren Datenbank abschaltet - mit ihr eine
        // Welt voller Parzellen und ohne jede Schutzwache, denn die wird erst weiter unten
        // angemeldet. Wer zuletzt dort ausgeloggt war, erschiene beim Einloggen genau dort
        // und duerfte ueberall bauen. Jetzt entsteht im Fehlerfall gar keine Welt.
        if (!richteAblageEin(wirtschaft.werte().startguthaben())) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Welten aus der Liste, nicht aus dem Quelltext (Plan 4.2). Die Klagen zuerst
        // und einzeln: Wer eine Welt falsch eingetragen hat, soll genau lesen, welche und
        // warum - die uebrigen entstehen trotzdem.
        String ankunftswelt = getConfig()
                .getString("reisen.ankunftswelt", ANKUNFTSWELT_VORGABE).trim();
        // Der Name der Freistaedtewelt VOR dem Lesen der Welten: Weltenliste bindet
        // die Art an ihn, genau wie beim Raster an den Marktstadtnamen.
        freistadtwelt = Freistadtliste.ausKonfiguration(getConfig()).welt();
        // Derselbe Griff fuer die Inselwelt, und aus demselben Grund: Weltenliste bindet
        // art: inseln an diesen Namen.
        // NUR der Weltname, und Klagen bleiben hier stumm: Dieselbe Datei liest
        // Wirtschaftsliste noch einmal und beklagt dort, was nicht stimmt. Zweimal
        // dieselbe Klage im Startprotokoll sieht nach zwei Fehlern aus.
        inselwelt = Inselliste.ausKonfiguration(getConfig()).welt();
        // Und derselbe Griff fuer Wolkenwerder [Schritt 11].
        wolkenwelt = Wolkenliste.ausKonfiguration(getConfig()).welt();
        var liste = Weltenliste.ausKonfiguration(
                getConfig().getConfigurationSection("welten"), MARKTSTADT, ankunftswelt,
                freistadtwelt, inselwelt, wolkenwelt);
        liste.klagen().forEach(getLogger()::severe);
        var gebaut = new Weltenbau(this, generator, stadtfelder, inseln, wolken,
                getLogger()).baueAlle(liste.welten());
        welten.putAll(gebaut.welten());
        // Die Angaben mit, nicht nur die Welten: Spielmodus und Tor stehen dort, und ein
        // zweites Lesen der config.yml waere eine zweite Wahrheit.
        liste.welten().forEach(angabe -> angaben.put(angabe.name(), angabe));
        // Die Ladeneinheiten NACH den Welten: Sie werden gegen die Kante ihrer Welt
        // geprueft, und die steht in deren Angabe. Ein zweites Lesen des Abschnitts
        // welten: waere eine zweite Wahrheit.
        Map<String, Integer> kanten = new LinkedHashMap<>();
        liste.welten().forEach(angabe -> kanten.put(angabe.name(), angabe.kante()));
        Ladenliste.Ergebnis laeden = Ladenliste.ausKonfiguration(getConfig(), kanten);
        laeden.klagen().forEach(getLogger()::severe);
        laeden.bericht().forEach(getLogger()::info);
        ladenwelt = laeden.welt();
        ladenverzeichnis = laeden.verzeichnis();
        // DER RUNDGANG [Teil D]: hier und nicht an einer eigenen Stelle, weil er die
        // DIESELBEN Kanten braucht wie die Ladeneinheiten - ein Bereich hinter der
        // Weltgrenze ist einer, an den niemand kommt. Und er liegt in derselben Welt:
        // Altfurt. Ein zweites Lesen von welten: waere eine zweite Wahrheit.
        Rundgangsliste.Ergebnis stationen =
                Rundgangsliste.ausKonfiguration(getConfig(), kanten);
        stationen.klagen().forEach(getLogger()::severe);
        stationen.bericht().forEach(getLogger()::info);
        rundgangswelt = stationen.welt();
        rundgangsstationen = stationen.verzeichnis();
        // DIE PLATZHALTERBAUTEN [Plan 2.1]: hier und nicht an einer eigenen Stelle, aus
        // demselben Grund wie der Rundgang - dieselben Kanten, dieselbe Welt. Dazu
        // bekommt dieser Leser die Quader aller TORE: Ein Bau wird GESETZT, und einer
        // ueber einem Tor mauerte den Durchgang zu. Die Tore kommen aus derselben Liste,
        // aus der die Welten entstanden sind; ein zweites Lesen waere eine zweite
        // Wahrheit.
        List<Quader> torquader = new ArrayList<>();
        liste.welten().forEach(angabe -> {
            if (angabe.tor() != null) {
                torquader.add(angabe.tor());
            }
        });
        Bautenliste.Ergebnis bauten =
                Bautenliste.ausKonfiguration(getConfig(), kanten, torquader);
        bauten.klagen().forEach(getLogger()::severe);
        bauten.bericht().forEach(getLogger()::info);
        bautenwelt = bauten.welt();
        platzhalterbauten = bauten.verzeichnis();
        List<String> stoffklagen = new ArrayList<>();
        altfurtaufbau = new Altfurtaufbau(
                Baustoffe.ausKonfiguration(getConfig(), stoffklagen));
        pflasterstoffe = Pflasterstoffe.ausKonfiguration(getConfig(), stoffklagen);
        // DIE KANTE DER BAUTENWELT kommt herein - dieselbe Zahl, gegen die auch ein
        // eingetragener Bau geprueft wird. Eine Stadt jenseits der Weltgrenze koennte
        // niemand betreten.
        Stadtliste stadt = Stadtliste.ausKonfiguration(getConfig(),
                kanten.get(bautenwelt), stoffklagen);
        stadtplan = stadt.plan();
        getLogger().info(stadt.bericht());
        // DIE UNBERUEHRTEN QUADER: dieselben Tore, die schon den Bauten im Weg
        // stehen duerfen, dazu die eingetragenen Bauten und die Ladeneinheiten.
        // Ihr Boden gehoert ihnen - das Pflaster laeuft darum herum.
        List<Quader> unberuehrt = new ArrayList<>(torquader);
        platzhalterbauten.alle().forEach(bau -> unberuehrt.add(bau.bereich()));
        if (ladenwelt.equals(bautenwelt)) {
            ladenverzeichnis.alle().forEach(einheit -> unberuehrt.add(einheit.bereich()));
        }
        unberuehrteQuader = List.copyOf(unberuehrt);
        stoffklagen.forEach(getLogger()::severe);
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
        buch = new Besitzbuch(grenze, lager, kasse, wirtschaft);

        // Der Schluesseltext, nicht der NamespacedKey: World.key() gibt ein Key zurueck,
            // und ein Vergleich zwischen beiden ist immer falsch, ohne dass es auffaellt.
        String welt = marktstadt.key().asString();
        // Die WELT und nicht nur ihr Schluessel: Seit das Zusammenlegen den Weg fuellt,
        // setzen die Befehle Bloecke. Den Schluesseltext holt sich ParzellenBefehle aus
        // derselben Welt - zwei Angaben koennten auseinanderlaufen.
        // Die Sprache geht an ALLE elf Sender auf einmal, und zwar bevor der erste Satz
        // umzieht. Der Grund ist gemessen: Alle elf werden hier gebaut, sieben davon in
        // richteReisenEin in benachbarten Zeilen. Kaeme sie je Bauschritt herein, muesste
        // jeder Umzugsschritt diese Datei und teils dieselbe Methode anfassen -
        // benachbarte Zeilen, zwei Haende, ein verlorener Schreibzug. So fasst nach diesem
        // Schritt kein Umzug FreimarkenPlugin mehr an.
        // DIE WECHSELSTUBE [Teil F]: dieselbe Markkasse, die /mark zeigt, und die
        // Goldkasse aus richteAblageEin. Hier gebaut und nicht in einer eigenen
        // richte-Methode, weil sie nichts einrichtet - kein Zeitgeber, kein Zuhoerer,
        // keine Tabelle. Sie ist eine Regel mit zwei Kassen.
        //
        // Die Goldkasse steht an dieser Stelle schon: richteAblageEin lief weiter oben,
        // in BEIDEN Betriebsarten. Waere sie null, waere es kein stiller Fehler, sondern
        // ein Wurf im Konstruktor von Wechselbuch - beim Start und nicht beim ersten
        // Tausch.
        Wechselbuch wechselbuch = new Wechselbuch(buch.kasse(), goldkasse, wirtschaft,
                uhr);
        // DAS MUENZHAUS [Teil G]: dieselbe Goldkasse wie die Wechselstube - zwei waeren
        // zwei Konten in derselben Waehrung - und derselbe geteilte Wirtschaftsstand,
        // damit /freimarken neuladen den Katalog erreicht.
        //
        // Die MESSUNG als Lambda auf das Feld und nicht als Abhaengigkeit: Der
        // Aufstiegslauf entsteht erst in richteRaengeEin, und er ist die EINE Stelle, die
        // eine Bedingung messen darf. Gemessen wird beim Aufruf, also lange nach dem
        // Bauen. Ist das Feld null - der Start ist vorher abgebrochen -, gilt KEIN
        // Messwert; dann ist jeder Posten mit Bedingung gesperrt, und das ist die sichere
        // Richtung: Lieber kauft niemand, als dass jemand ohne Anspruch kauft.
        Muenzhaus muenzhaus = new Muenzhaus(goldkasse, wirtschaft,
                (wer, gebraucht) -> aufstiegslauf == null
                        ? java.util.Map.of()
                        : aufstiegslauf.messeFuer(wer, gebraucht));
        // DIE LOTTERIE [22.11.2026, Schritt 14]. Sie braucht beide Kassen: Das Los wird
        // in Mark bezahlt, ein Goldgewinn landet auf dem Goldkonto - und das muessen
        // zwei Kassen sein, weil pruefeWaehrung der Goldkasse fuer jeden Markgrund
        // wirft.
        //
        // DER WUERFEL LIEGT HIER und nicht im Kern: Losregel bekommt ihren Wurf als
        // Zahl, genau wie Fangregel von der Angelwache. Ein Zufall im Kern waere eine
        // Probe, die manchmal gruen ist.
        //
        // Die UHR als Lambda auf das Feld, nicht die Uhr selbst: Welcher TAG ein
        // Zeitpunkt ist, entscheidet Lotteriebuch.tagVon aus der eingestellten Zone -
        // der Befehl liefert nur den Augenblick.
        lotteriebuch = new Lotteriebuch(kasse, goldkasse, losablage, wirtschaft);
        lotterieBefehle = new LotterieBefehle(lotteriebuch, sprache, uhr::instant,
                new java.util.Random(), Vorratshand::fuer);
        // DER ANGELWETTBEWERB [22.11.2026, Schritt 14]. Nur die MARKkasse: Preise und
        // Startgeld sind Mark, und Goldmark hat hier nichts zu suchen - der Topf ist
        // eine Umverteilung unter Spielern.
        wettbewerbsbuch = new Wettbewerbsbuch(kasse, wettbewerbsablage, wirtschaft);
        // DIE WACHE WIRD HIER GEBAUT und nicht in richteSpielzeitEin: Sie ist der
        // Mitfahrer, den jener Takt braucht, und ein null dort ist ERLAUBT. Ein
        // vergessenes Feld liefe nie und rechnete keinen Wettbewerb ab - ohne dass eine
        // Probe oder eine Startmeldung es sagt.
        wettbewerbswache = new Wettbewerbswache(wettbewerbsbuch, uhr, sprache,
                Spielername::von, () -> getServer().getOnlinePlayers(),
                wer -> getServer().getPlayer(wer), getLogger());
        getServer().getPluginManager().registerEvents(wettbewerbswache, this);
        wettbewerbBefehle = new WettbewerbBefehle(wettbewerbsbuch, sprache,
                uhr::instant, Spielername::von);
        befehle = new ParzellenBefehle(buch, marktstadt, wirtschaft, getLogger(),
                sprache,
                // Das FELD wird beim Aufruf gelesen und nicht beim Bauen: Die Befehle
                // entstehen vor dem Spielzeittakt, mit dem der Bonus entsteht. Die
                // Pruefung auf null faengt den Fall, dass der Start davor abgebrochen
                // ist - dann zeigt /mark seine uebrigen Zeilen.
                wer -> tagesbonus == null ? null : tagesbonus.standZeile(wer, sprache),
                wechselbuch, muenzhaus, Vorratshand::fuer);
        // Der Ladenschutz als ZWEITE Frage derselben Wache [Teil C] - nicht als zweite
        // Wache. Jene deckt fuenfzehn Ereignisarten ab, dreizehn davon ueber dasselbe
        // unterbinden(); eine zweite Liste liefe auseinander, und genau davor warnt ihre
        // eigene Doku.
        //
        // Hier und nicht in richteAblageEin oder richteSpielzeitEin: Gebraucht werden das
        // Ladenverzeichnis aus der config.yml (steht seit dem Lesen der Einstellungen)
        // UND die Mietablage (steht seit richteAblageEin, das weiter oben lief). Dies ist
        // die erste Stelle, an der beide da sind.
        //
        // Die HANDELSTUER [08.11.2026]: Handeln darf jeder, immer und ueberall.
        //
        // Das FELD wird beim Aufruf gelesen und nicht beim Bauen - dieselbe Form wie
        // beim Tagesbonus zwanzig Zeilen weiter oben, und hier aus einem zwingenden
        // Grund: Der Shopklick entsteht erst in richteShopsEin, weil sein
        // Shopzugriff DIESE Wache braucht. Ein Zirkel, den nur ein spaet gelesenes
        // Feld aufloest.
        //
        // Der Lambda reicht NUR weiter und prueft nichts selbst: Was ein Handel ist,
        // entscheidet Shopklick.istHandelsschild an einer Stelle. Eine zweite Pruefung
        // hier waere die zweite Wahrheit, vor der Shopzugriff ausdruecklich warnt.
        //
        // DREI Ortsregeln seit dem 18.11.2026, und zwei davon schliessen dasselbe Loch:
        // In der Welt der Stadtfelder griff bis zum 11.11. GAR KEINE Wache, in
        // Hafenwerder bis heute auch nicht - jeder konnte in jedem Feld jeder fremden
        // Stadt und auf jeder fremden Insel bauen. Beide werden SPAET gelesen, weil
        // ihre Buecher erst in richteFreistaedteEin und richteInselnEin entstehen; bis
        // dahin ist keine von ihnen zustaendig.
        //
        // Die Reihenfolge ist ohne Bedeutung, weil alle drei in verschiedenen WELTEN
        // liegen - sie ist trotzdem festgelegt und nicht dem Zufall einer Menge
        // ueberlassen, damit eine vierte Regel an einer entschiedenen Stelle einzufuegen
        // ist.
        schutzwache = new Schutzwache(buch, welt, sprache,
                Schutzwache.Ortsregel.nacheinander(
                        new Ladenschutz(ladenverzeichnis, mietablage,
                                weltschluessel(ladenwelt), sprache,
                                getLogger()),
                        Schutzwache.Ortsregel.spaet(() -> stadtwache),
                        Schutzwache.Ortsregel.spaet(() -> inselwache),
                        Schutzwache.Ortsregel.spaet(() -> wolkenwache)),
                (spieler, block) -> shopklick != null
                        && shopklick.istHandel(spieler, block));
        getServer().getPluginManager().registerEvents(schutzwache, this);
        getLogger().info("Ladenschutz aktiv: " + ladenverzeichnis.anzahl()
                + " Einheiten in " + ladenwelt + " - darin baut, bearbeitet und oeffnet"
                + " Kisten nur der Mieter. Ein freier Stand ist fuer JEDEN gesperrt."
                + " Am Schild eines Kistenshops darf jeder handeln, auch hier.");
        // Die Markierung MUSS stehen, bevor die erste Mark aus Arbeit fliesst: Alles, was
        // vorher gesetzt wurde, gilt danach fuer immer als gewachsener Boden (Plan 9.1).
        // Heute zahlt noch nichts - genau darum steht sie hier und nicht erst mit Teil E.
        //
        // EIGENE Weltmenge und nicht die eine Welt der Schutzwache: Bergmann, Holzfaeller,
        // Bauer und Jaeger arbeiten gar nicht in der Marktstadt. Die Menge kommt aus den
        // Welten, die Freimarken selbst gebaut hat - eine zweite Liste in der config.yml
        // liefe auseinander, sobald jemand eine Welt hinzufuegt.
        marken = Chunkmarken.ueberBukkit();
        Set<String> markierteWelten = new TreeSet<>();
        welten.values().forEach(eine -> markierteWelten.add(eine.key().asString()));
        // Der Sammler und die Wache VOR der Setzwache: Jene braucht ihn als Zahlmeister.
        // Das Buch ist eine lokale Variable und kein Feld - gehalten wird es von der
        // Wache und sonst von niemandem, und als Feld blieb es beim Abschalten mit allen
        // gesammelten Pfennigen stehen (dieselbe Begruendung wie beim Spielzeitbuch).
        Verdienstbuch verdienstbuch = new Verdienstbuch(kasse, wirtschaft, uhr,
                getLogger());
        // Der Zeitgeber kommt als Naht herein und nicht als this: So ist der Zweig, in
        // dem Geld entsteht, ohne laufenden Server pruefbar. Gestellt wird er im
        // HAUPTFADEN und nicht nebenlaeufig - die Datenschicht haelt EINE Verbindung, und
        // MariaKasse schaltet darauf autoCommit um; ein zweiter Faden committete dort die
        // halbe Buchung des Hauptfadens mit.
        verdienstwache = new Verdienstwache((schlag, abstand) -> {
            BukkitTask aufgabe = getServer().getScheduler()
                    .runTaskTimer(this, schlag, abstand, abstand);
            return aufgabe::cancel;
        }, verdienstbuch, marken, berufsablage, wirtschaft, markierteWelten,
                weltschluessel(inselwelt), sprache,
                getLogger());
        getServer().getPluginManager().registerEvents(verdienstwache, this);
        verdienstwache.starte();
        // DIESELBE Weltmenge fuer Markieren und Zahlen: Zwei Listen liefen auseinander,
        // und dann waere eine Welt dabei, in der verdient und nicht markiert wird - das
        // ist die Gelddruckmaschine.
        getServer().getPluginManager().registerEvents(
                new Setzwache(marken, markierteWelten,
                        verdienstwache::zahleFuerSetzen,
                        // Bei JEDEM Setzen neu gefragt und nicht einmal gemerkt: So wirkt
                        // eine Aenderung an verdienst.saatgut sofort nach dem Neuladen.
                        art -> wirtschaft.werte().verdienst().istSaatgut(
                                art.name().toLowerCase(Locale.ROOT))),
                this);
        getLogger().info("Blockmarken und Verdienst aktiv in " + markierteWelten.size()
                + " Welten - ein gesetzter Block zahlt beim Abbauen nichts. Die Marken"
                + " liegen im Chunk, nicht in der Datenbank.");
        getLogger().info("Lohnliste: "
                + wirtschaft.werte().verdienst().loehne().anzahl() + " Saetze,"
                + " Tagesdeckel " + wirtschaft.werte().verdienst().tagesdeckel()
                + " Mark je Spieler. Gebucht wird gesammelt, nicht je Block.");
        // Diese Zeile ist am 10.11.2026 zweimal falsch geworden und zweimal berichtigt:
        // Erst stand hier "Verkauft wird nur in der Ankunftswelt" (die Weltpruefung war
        // mit /markt verkaufen gegangen), dann nannte sie die Zahl der Materialien im
        // Sortiment - und das Sortiment ist mit der Tafel gefallen. Geblieben ist der
        // DECKEL; wie viele Staende stehen, sagt die Zeile der Kistenshops weiter unten,
        // und die kann es erst, wenn die Shops gelesen sind.
        getLogger().info("Bundesmarkt: "
                + (wirtschaft.werte().markt().gedeckelt()
                        ? "Tagesdeckel " + wirtschaft.werte().markt().tagesdeckel()
                                + " Mark je Spieler und Tag."
                        : "OHNE Tagesdeckel - wer an einem Bundschild verkauft, kann"
                                + " beliebig viel am Tag bekommen.")
                + " Was der Bund handelt, entscheiden seine Schilder; /markt zeigt sie.");
        richteBlockbuchEin();
        richteTruheEin();
        // Das Verzeichnis der Warpziele zuerst: Das Freistadtbuch fragt es, ob ein
        // Stadtname einer Welt gehoert, und richteReisenEin baut daraus den Warpbefehl.
        // Ein Verzeichnis je Stelle waeren zwei Listen derselben Welten.
        warpverzeichnis = Warpverzeichnis.ausWelten(List.copyOf(welten.keySet()));
        // Dann das Buch, und ERST DANN die Reise: Der Warpbefehl traegt einen Zweig
        // /warp freistadt <name>, und der braucht das Buch.
        richteFreistaedteEin();
        // NACH den Freistaedten, und die Reihenfolge ist hier ohne Zwang: Die beiden
        // Buecher wissen nichts voneinander. Sie steht trotzdem fest, damit das
        // Startprotokoll immer dieselbe Gestalt hat.
        richteInselnEin();
        // NACH den Inseln, und wieder ohne Zwang: Die Buecher wissen nichts voneinander.
        // Die Reihenfolge steht fest, damit das Startprotokoll immer dieselbe Gestalt
        // hat - und sie steht VOR richteReisenEin, weil die Ankunftsplattform stehen
        // muss, bevor der Warpbefehl die Ankunftspunkte aller Welten prueft.
        // VOR den Wolken, und das ist kein Geschmack: Beide setzen Bloecke in eine Welt,
        // und wer das Protokoll liest, soll die zwei Haende beieinander finden.
        richteAltfurtEin();
        richteWolkenEin();
        richteReisenEin(ankunftswelt);
        richteSpielzeitEin();
        // NACH der Spielzeit, und das ist kein Geschmack: Die Bedingungen der Rangleiter
        // werden an ihr gemessen. Steht die Leiter vor ihr im Protokoll, liest der
        // Betreiber eine Huerde, deren Messstelle er noch nicht gesehen hat.
        nenneRaenge();
        nenneGoldmark();
        nennePostenOhneWort();
        nenneUnlieferbarePosten();
        registriereBefehle();

        getLogger().info("Freimarken " + getPluginMeta().getVersion()
                + " bereit - Java " + Runtime.version().feature()
                + ", Ablage " + (fluechtig ? "FLUECHTIG" : "MariaDB"));

        nenneNichtVorgenerierte();
        nenneSchlechteAnkunft();
    }

    /**
     * Richtet das Blockbuch ein - Leser, Sammler, Wache, Zeitgeber.
     *
     * <p><b>Die Welten kommen als NAME herein und werden hier zum SCHLUESSEL.</b> Das ist
     * genau die Umwandlung, deren Fehlen am 09.11.2026 den ganzen Ladenbereich tot legte:
     * Die {@code config.yml} schreibt {@code altfurt}, die Welt traegt
     * {@code freimarken:altfurt}. Uebergeben wird darum {@link #weltschluessel} als Naht
     * und nicht der rohe Name - so bleibt die Umwandlung an EINER Stelle, und der Leser
     * ist trotzdem ohne laufenden Server pruefbar.
     *
     * <p><b>Der Zeitgeber kommt als Naht herein und nicht als {@code this}</b>, wie bei
     * {@link Verdienstwache}: Gestellt wird er im HAUPTFADEN und nicht nebenlaeufig - die
     * Datenschicht haelt EINE Verbindung, und MariaKasse schaltet darauf
     * {@code autoCommit} um; ein zweiter Faden committete dort die halbe Buchung des
     * Hauptfadens mit.
     *
     * <p>Schreibt das Buch in keiner Welt etwas, wird die Wache GAR NICHT angemeldet: Ein
     * Hoerer an jedem Blockereignis, der nur vergleicht und verwirft, ist Arbeit fuer
     * nichts - und ein Zeitgeber, der jede Sekunde einen leeren Stapel abgibt, auch. Der
     * Leser hat diesen Zustand schon laut gesagt.
     */
    private void richteBlockbuchEin() {
        var gelesen = Blockbuchliste.ausKonfiguration(getConfig(), welten.keySet(),
                this::weltschluessel);
        gelesen.klagen().forEach(getLogger()::severe);
        gelesen.bericht().forEach(getLogger()::info);
        // DIE RAEUMUNG ZUERST, und zwar AUCH wenn nichts geschrieben wird. Wer das Buch
        // abschaltet, will seine alten Zeilen nicht fuer immer behalten - und sie waeren
        // sonst unloeschbar, weil niemand mehr aufraeumt. Sie haengt am Spielzeittakt und
        // nicht an der Wache darunter, genau aus diesem Grund.
        blockbuchwerte = gelesen.werte();
        blockraeumung = new Blockraeumung(blockbuchablage, gelesen.werte(), wirtschaft,
                uhr, getLogger());
        if (!gelesen.werte().schreibtIrgendwo()) {
            return;
        }
        // Das Buch ist eine lokale Variable und kein Feld - gehalten wird es von der
        // Wache und sonst von niemandem. Als Feld blieb es beim Abschalten mit allen
        // gesammelten Zeilen stehen (dieselbe Begruendung wie beim Verdienstbuch).
        Blockbuch buch = new Blockbuch(blockbuchablage, getLogger());
        blockwache = new Blockwache((schlag, abstand) -> {
            BukkitTask aufgabe = getServer().getScheduler()
                    .runTaskTimer(this, schlag, abstand, abstand);
            return aufgabe::cancel;
        }, buch, gelesen.werte(), uhr, getLogger());
        getServer().getPluginManager().registerEvents(blockwache, this);
        blockwache.starte();
        getLogger().info("Blockbuch aktiv - geschrieben wird gesammelt, nicht je Block."
                + " Abgewiesene Versuche kommen mit, wo sie eingetragen sind: Ein Dutzend"
                + " davon an einer fremden Parzelle ist der Fund, bei dem nichts"
                + " passiert ist.");
    }

    /**
     * Liest die {@code sprache.yml} - mit der einen Wache, die keine Probe ersetzen kann.
     *
     * <p>{@link Sprachliste} spricht {@code org.yaml.snakeyaml} unmittelbar an. Beim
     * Uebersetzen ist das belegt - das POM von {@code paper-api} fuehrt
     * {@code snakeyaml:2.2} mit {@code scope compile}, die Jar liegt im Cache, und die
     * Proben laufen durch dieselbe Methode. Was keine Probe im Gradle-Lauf zeigen kann,
     * ist Papers Klassenlader im laufenden Server. Faellt er aus, gelten die
     * ausgelieferten Saetze, der Server laeuft, und die Klage sagt den Grund - dieselbe
     * Linie wie bei jeder anderen Klage.
     *
     * <p>{@link Sprachliste#nichtsGelesen} gibt dafuer ein Ergebnis mit
     * {@code buch() == null} und genau dieser einen Klage. Damit geht es durch denselben
     * Weg wie jeder andere Mangel, und {@code uebernimm} wird nie mit {@code null}
     * gerufen.
     */
    private Sprachliste.Ergebnis lieseSprache() {
        String lesefehler = Sprachliste.lesefehler(getDataFolder());
        if (lesefehler != null) {
            return new Sprachliste.Ergebnis(ausgelieferteSaetze, List.of(),
                    List.of(lesefehler));
        }
        try {
            return Sprachliste.ausDatei(getDataFolder(), ausgelieferteSaetze);
        } catch (LinkageError nichtDa) {
            return Sprachliste.nichtsGelesen(Sprachliste.DATEI + " kann nicht geprueft"
                    + " werden (" + nichtDa + ") - es gelten die ausgelieferten Saetze."
                    + " Keine Zeile aus deiner Datei wird uebernommen, solange das so"
                    + " ist.");
        }
    }

    @Override
    public void onDisable() {
        // Geplante Spruenge zuerst: Eine Aufgabe, die nach dem Abschalten feuert, haette
        // keine Reise mehr, durch die sie laufen koennte.
        if (aufwaermzeit != null) {
            aufwaermzeit.brichAllesAb();
        }
        // Dann der Spielzeittakt, und zwar VOR datenbank.close(): Erst wird der Zeitgeber
        // abgemeldet, damit nicht mitten im Abrechnen noch ein Takt dazukommt, dann wird
        // jede offene Sitzung weggeschrieben - solange die Verbindung noch steht.
        // Andersherum waere die letzte Sitzung jedes anwesenden Spielers verloren, und eine
        // Ablage, die nach close() noch schreibt, baut eine neue Verbindung auf, die
        // niemand mehr schliesst.
        if (spielzeitwache != null) {
            spielzeitwache.beende();
            spielzeitwache = null;
        }
        // Derselbe Grund, dieselbe Reihenfolge: erst abmelden, dann das Gesammelte
        // buchen, und beides VOR datenbank.close() - sonst ist der letzte Verdienst jedes
        // anwesenden Spielers verloren.
        if (verdienstwache != null) {
            verdienstwache.beende();
            verdienstwache = null;
        }
        // Und das Blockbuch, aus genau demselben Grund und immer noch VOR
        // datenbank.close(): Der Stapel haelt bis zu eine Sekunde Spielgeschehen. Gerade
        // die letzte Sekunde ist die, nach der jemand fragt, wenn ein Server unerwartet
        // herunterfaehrt - und ein Buch, dem genau dort eine Zeile fehlt, beantwortet die
        // eine Frage nicht, fuer die es gebaut ist.
        if (blockwache != null) {
            blockwache.beende();
            blockwache = null;
        }
        if (datenbank != null) {
            datenbank.close();
            datenbank = null;
        }
    }

    /**
     * Richtet die Freie Truhe ein - Stellen lesen, Lauf bauen, Wache anmelden.
     *
     * <p><b>Nach {@link #richteBlockbuchEin} und VOR {@link #richteSpielzeitEin}</b>: Der
     * Lauf ist ein Mitfahrer im Spielzeittakt und muss stehen, bevor jener angelegt wird.
     *
     * <p><b>Die Stellen kommen aus dem Abschnitt {@code freie-truhe:}, die Welt aus
     * {@code laeden.welt}.</b> Der Weltschluessel entsteht ueber {@link #weltschluessel}
     * und nirgends sonst - genau diese Handarbeit hat am 09.11.2026 den ganzen
     * Ladenbereich tot gelegt. Das Ladenverzeichnis geht mit herein, damit eine Stelle
     * INNERHALB einer Ladeneinheit beklagt wird: Dort kaeme niemand ausser dem Mieter
     * heran, und der nimmt ohne Deckel.
     *
     * <p><b>Der LAUF entsteht immer, die WACHE nur mit Stellen.</b> Ein Hoerer an jedem
     * Inventarereignis, der nur vergleicht und verwirft, ist Arbeit fuer nichts; alte
     * Tageszeilen muessen trotzdem weggehen.
     */
    private void richteTruheEin() {
        var stellen = Truhenliste.stellen(getConfig(), weltschluessel(ladenwelt),
                ladenverzeichnis);
        truhenstellen = stellen.verzeichnis();
        stellen.klagen().forEach(getLogger()::severe);
        stellen.bericht().forEach(getLogger()::info);
        Truhenbuch buch = new Truhenbuch(stellen.verzeichnis(), truhenablage, wirtschaft,
                uhr, getLogger());
        truhenlauf = new Truhenlauf(buch,
                new Truhenleerung(getServer(), getLogger()), getLogger());
        if (!buch.gibtTruhen()) {
            return;
        }
        getServer().getPluginManager().registerEvents(new Truhenwache(buch, sprache),
                this);
        getLogger().info(Truhenliste.rechenprobe(wirtschaft.werte().truhe()));
    }

    /**
     * Stellt den Spielzeittakt und meldet seine Wache an.
     *
     * <p>Hier und nicht in {@code neulade()}: Ein in {@code neulade()} gestellter Zeitgeber
     * stuende nach zwei Neuladen dreimal da und schriebe dieselbe Spielzeit dreifach. Genau
     * deshalb sind {@code zeit.zone} und {@code zeit.takt-sekunden} nicht neuladbar - der
     * Satz aus Plan 8.7, dass ein Neuladen keinen Zeitgeber stellt, gilt ab jetzt nur noch
     * fuer das NEULADEN und nicht fuer den Start.
     *
     * <p>Nach dem Weltenbau und nach den Reisen: Der erste Takt schreibt in die Datenbank,
     * und das soll nicht mitten in das Anlegen einer Welt fallen.
     *
     * <p>Das Buch ist eine LOKALE Variable und kein Feld: Gehalten wird es von der Wache und
     * von den Befehlen, und sonst von niemandem. Als Feld blieb es beim Abschalten stehen -
     * die Wache wird dort auf {@code null} gesetzt, die Datenbank geschlossen -, und bei
     * einem Wiedereinschalten im Betrieb stuende das alte Buch mit seiner Markenkarte neben
     * dem neuen, ohne dass jemand es abgeraeumt hat.
     */
    private void richteSpielzeitEin() {
        // Die Messstelle VOR dem Buch: Das Buch haelt sie als seine Taetigkeitsquelle.
        // Ihre eine Frage ans Spiel ist der Ort eines Spielers; ein abgemeldeter hat
        // keinen, und ohne Ort zaehlt das Bewegen nicht - die sichere Richtung.
        messstelle = new Messstelle(wirtschaft, wer -> {
            Player spieler = getServer().getPlayer(wer);
            if (spieler == null) {
                return null;
            }
            Location ort = spieler.getLocation();
            return new Messstelle.Ort(ort.getWorld().getName(), ort.getX(), ort.getY(),
                    ort.getZ());
        });
        getServer().getPluginManager().registerEvents(
                new Messwache(this, messstelle), this);
        Spielzeitbuch buch = new Spielzeitbuch(wirtschaft, spielzeit, messstelle, uhr,
                getLogger());
        // Der Tagesbonus VOR der Spielzeitwache: Sie nimmt ihn in ihrem Takt mit.
        tagesbonus = new Tagesbonus(wirtschaft, bonusablage, kasse, spielzeit,
                uhr, getLogger());
        // Das Mietbuch hier und nicht in richteAblageEin: Es braucht das
        // Ladenverzeichnis aus der config.yml UND die Ablage, und die entstehen in zwei
        // verschiedenen Schritten des Starts.
        // EINE Ladenware fuer beide: Das Mietbuch leert die Truhe beim Vermieten, der
        // taegliche Lauf raeumt sie beim Ablaufen. Zwei Exemplare waeren zwei Zugaenge
        // zur selben Kiste - und eines Tages behandelt eines davon eine nicht geladene
        // Welt anders als das andere.
        Ladenware ladenware = new Ladenware(getServer(), ladenverzeichnis,
                weltschluessel(ladenwelt), getLogger());
        mietbuch = new Mietbuch(wirtschaft, ladenverzeichnis, mietablage, kasse,
                ladenware, uhr, getLogger());
        // Das Abholbuch und der taegliche Lauf [Teil C]. Beide HIER und nicht in
        // richteAblageEin: Sie brauchen das Ladenverzeichnis aus der config.yml, die
        // Ablagen und - beim Lauf - den Zugang zur Welt, und das sind drei verschiedene
        // Schritte des Starts.
        abholbuch = new Abholbuch(abholablage, uhr, getLogger());
        // Ladenware ist die einzige Stelle, die Bukkit und das Abholfach zusammenbringt.
        // Derselbe Weltschluessel wie bei LadenBefehle, aus demselben Feld: Zwei
        // Einstellungen daneben waeren eine Welt, in der gemietet, und eine andere, in
        // der geraeumt wird.
        Mietlauf mietlauf = new Mietlauf(wirtschaft, mietablage, abholablage, kasse,
                ladenware, uhr, getLogger());
        mietwache = new Mietwache(getServer(), mietlauf, mietbuch, abholbuch, wirtschaft,
                sprache);
        getServer().getPluginManager().registerEvents(mietwache, this);
        // DIE ANMELDUNG [Schritt 13 Teil C] - und sie steht VOR richteShopsEin, nicht
        // danach. GEMESSEN IM STARTLAUF vom 21.11.2026: Dahinter gesetzt, bekam
        // Shophandel ein null und Paper schaltete das ganze Plugin ab ("Error occurred
        // while enabling"). Keine Probe kann das sehen - sie baut den Handel selbst und
        // gibt ihm, was er braucht; die REIHENFOLGE im onEnable prueft nur ein Start.
        // Tafel 31dn haelt sie jetzt fest.
        //
        // Hier ist beides da: die Ablage aus richteAblageEin (laeuft im onEnable davor)
        // und der Halter aus Zeile 884. Die Fassung wird im AUGENBLICK gefragt und nicht
        // gemerkt - ein Neuladen kann sie aendern, und genau dann muessen alle neu
        // zustimmen.
        anmeldung = new Anmeldung(anmeldeablage, wirtschaft, uhr);
        richteShopsEin();
        Bonuswache bonuswache = new Bonuswache(tagesbonus, wirtschaft, sprache);
        getServer().getPluginManager().registerEvents(bonuswache, this);
        // Der Aufstiegslauf [Schritt 12 Teil D] - der erste Verbraucher von Rangregel.
        //
        // DER WELTSCHLUESSEL KOMMT AUS DEMSELBEN FELD wie der von ParzellenBefehle
        // (marktstadt.key().asString(), siehe dort): Die Zahl, an der ein Aufstieg
        // haengt, MUSS dieselbe sein, die die Obergrenze begrenzt. Zwei Zaehlungen fuer
        // "eigene Parzellen" waeren ein Rang, der an einer Zahl haengt, die der Spieler
        // nirgends sieht - und ein neu gerechneter Schluessel ist genau die Naht, an der
        // das auseinanderlaeuft.
        //
        // marktstadt ist hier nie null: Ohne sie bleibt Freimarken aus und onEnable ist
        // vorher zurueckgekehrt (siehe die severe-Zeile in ladeWelten).
        // DER RUNDGANG [Teil D] - gebaut VOR dem Aufstiegslauf, denn jener nimmt ihn als
        // fuenfte Messquelle. Das Verzeichnis steht seit ladeWelten, die Ablage seit
        // richteAblageEin.
        rundgang = new Rundgang(rundgangsablage, rundgangsstationen, uhr);
        aufstiegslauf = new Aufstiegslauf(wirtschaft, rangablage, spielzeit, lager,
                berufsablage, kasse, rundgang, marktstadt.key().asString(), uhr,
                getLogger());
        // DIE RUNDGANGSWACHE nur bei vorhandenen Stationen - dieselbe Entscheidung wie
        // bei der Torwache (if hatTore()) und der Anmeldewache. Ab Werk gibt es keine
        // Station; dann hoert hier niemand zu, und kein Schritt kostet etwas.
        //
        // OHNE DIESE ZEILEN ist der ganze Rundgang unsichtbar wirkungslos: Die Ablage
        // bliebe leer, niemand saehe eine Station, und eine Rangstufe, die ihn verlangt,
        // waere unerreichbar. Tafel 31dk haelt sie.
        Rundgangswache rundgangswache = new Rundgangswache(rundgang,
                getServer().getWorld(rundgangswelt), sprache);
        if (rundgangswache.hatStationen()) {
            getServer().getPluginManager().registerEvents(rundgangswache, this);
        }
        // Die Rangwache [Schritt 12 Teil C/D]. OHNE DIESE ZWEI ZEILEN ist die ganze
        // Leiter unsichtbar wirkungslos: Teil A rechnet, Teil B liest, Teil C legt
        // ab - und kein Spieler merkt davon etwas. Tafel 31cr haelt sie fest.
        // DIE MENUEWACHE [21.11.2026]: Sie sagt jeden Klick in einem Menue des
        // Plugins ab und fuehrt den Befehl aus, der am geklickten Fach steht. Ohne
        // Anmeldung waere jeder Gegenstand darin eine Ware - ein Hilfetext zum
        // Herausnehmen.
        getServer().getPluginManager().registerEvents(new Menuewache(getLogger()), this);
        rangwache = new Rangwache(rangablage, wirtschaft,
                new AnhangRechte(this), aufstiegslauf, sprache, getLogger());
        getServer().getPluginManager().registerEvents(rangwache, this);
        // DIE WACHE nur bei eingeschalteter Anmeldung - dieselbe Entscheidung wie bei
        // der Torwache, die ohne Tore nicht angemeldet wird. Aus heisst dann wirklich
        // aus: kein Zuhoerer am Beitritt, kein Lesen der Ablage.
        //
        // OHNE DIESE ZEILE ist die ganze Anmeldung unsichtbar wirkungslos: Die Ablage
        // bliebe leer, und weil der Bundesmarkt an genau dieser Ablage haengt, saehe
        // der Spieler nur eine Absage, die er nicht aufheben kann. Tafel 31di haelt sie.
        if (anmeldung.an()) {
            getServer().getPluginManager().registerEvents(new Anmeldewache(anmeldung,
                    new Anmeldeformular(new Formularhand(getLogger())::frage, sprache)),
                    this);
            getLogger().info("Anmeldung im Buergeramt: Fassung \"" + anmeldung.fassung()
                    + "\" - wer sie noch nicht angenommen hat, bekommt beim Beitritt das"
                    + " Formular und kann bis dahin am Bundesmarkt nicht handeln.");
        }
        // blockraeumung darf null sein - ohne Datenbank gibt es kein Blockbuch und
        // nichts zu raeumen. richteBlockbuchEin() laeuft VOR dieser Methode und hat es
        // dann gesetzt; die Reihenfolge sichert 31ai.
        spielzeitwache = new Spielzeitwache(this, buch, bonuswache, rangwache, mietwache,
                blockraeumung, truhenlauf, besuchslauf, verfallslauf,
                ladengebuehrlauf, wettbewerbswache,
                wirtschaft, getLogger());
        getServer().getPluginManager().registerEvents(spielzeitwache, this);
        spielzeitwache.starte();
        // DIE NACHSTUFUNG, und zwar NACH starte(): Jener Aufruf oeffnet die Zeitmarken
        // der Anwesenden, dieser liest Summen. Die Reihenfolge entscheidet nichts
        // Sachliches - die offenen Marken stehen erst beim naechsten Abrechnen in der
        // Tabelle -, aber sie macht den Startbericht lesbar: Erst laeuft die Zeit, dann
        // wird gestuft.
        nenneStationenOhneWort();
        nenneNachstufung();
        // Jeder Wert, den dieser Satz verspricht, steht auch darin. Eine halbe Zusage -
        // "verlangt sind mehrere Arten" ohne die Zahl - liest sich wie eine Angabe und
        // traegt keine; genau das ist der Lesezeile des Bundesmarktes passiert.
        getLogger().info(Messliste.startzeile(wirtschaft.werte().messung()));
        // spielzeitBefehle MUSS ein Feld bleiben: registriereBefehle() liest es.
        spielzeitBefehle = new SpielzeitBefehle(buch, spielzeit, wirtschaft, uhr,
                getLogger(), sprache);
    }

    /**
     * Die Kistenshops: Zwischenspeicher fuellen, Wache anmelden.
     *
     * <p><b>Das Fuellen ist die erste Zeile und muss es sein.</b> Der Zwischenspeicher
     * wirft, solange er ungefuellt ist - und zwar absichtlich, denn "hier ist kein Shop"
     * und "ich weiss es noch nicht" sind verschiedene Antworten. Eine Wache, die auf
     * einen ungefuellten Speicher angemeldet waere, haette beim ersten Blockklick
     * geworfen und den Schutz fuer alle Shops aufgehoben.
     *
     * <p>Schlaegt das Fuellen fehl, wird KEINE Wache angemeldet und der Fehler gemeldet.
     * Das ist die sichere Richtung: Ohne Wache laesst sich kein Shop anlegen und keiner
     * handeln - aber auch keiner abbauen, denn ohne die Wache greift nur der gewoehnliche
     * Bauschutz. Ein Shop in der Markthalle bleibt damit durch den {@link Ladenschutz}
     * gedeckt; einer in der Wildnis ist offen, und genau das steht in der Meldung.
     *
     * <p><b>Der {@link Shopwache.Nachtrag} ist der Taktgeber fuer das Wachsen.</b> Am
     * {@code SignChangeEvent} ist der Text noch nicht geschrieben; gewachst wird einen
     * Takt spaeter. {@code runTask} und nicht {@code runTaskLater} mit einem Takt: Der
     * naechste Takt ist genau das, was gebraucht wird, und eine Zahl waere eine Annahme
     * darueber, wie lange Bukkit mit dem Schreiben braucht.
     */
    private void richteShopsEin() {
        int gefunden;
        try {
            gefunden = shopablage.fuellen();
        } catch (RuntimeException fehler) {
            getLogger().log(java.util.logging.Level.SEVERE, "Die Kistenshops sind nicht"
                    + " zu lesen - es gibt in diesem Lauf KEINE Shops: kein Anlegen,"
                    + " kein Handeln, und kein eigener Shopschutz. Was in der Markthalle"
                    + " steht, deckt weiter der Ladenschutz; ein Shop in der Wildnis ist"
                    + " offen, bis dieser Server mit lesbarer Datenbank neu startet.",
                    fehler);
            return;
        }
        Shopware shopware = new Shopware(getServer(), getLogger());
        Shopbuch shopbuch = new Shopbuch(shopablage, shopware,
                // Die Zugriffsfrage geht an DIESELBE Wache, die ueber jeden Blockklick
                // entscheidet - siehe Shopzugriff.
                new Shopzugriff(getServer(), shopware, schutzwache),
                // Der HALTER und kein Schnappschuss: Hier stand bis zum 10.11.2026
                // wirtschaft.werte().handel().bundWort(), also das Wort beim Start - und
                // ein Neuladen wirkte nie. Seit dem Bundesmarkt am Schild braucht das
                // Buch den Stand ohnehin, denn die Ankauftafel gehoert dazu.
                wirtschaft,
                // DAS LADENRECHT DER FREISTAEDTE [Plan 16, Teil E3b]. Das Feld steht
                // hier fest: richteFreistaedteEin laeuft frueher im Start als dieser
                // Zweig. Es DARF null sein - dann gibt es in diesem Lauf keine
                // Freistaedte, und der Riegel laesst jeden Shop durch; gesagt hat das
                // der Start schon mit zwei eigenen Zeilen.
                freistadtbuch, getLogger());
        Shopwache shopwache = new Shopwache(shopbuch, shopware, sprache,
                tat -> getServer().getScheduler().runTask(this, tat), getLogger());
        getServer().getPluginManager().registerEvents(shopwache, this);
        // Der HANDEL, und er wird ZULETZT gesetzt: Das Feld ist die Tuer der
        // Schutzwache, und die steht schon. Solange es null war, galt an jedem
        // Shopschild der gewoehnliche Bauschutz - also genau der Zustand, den die
        // Startmeldung oben fuer eine stumme Datenbank verspricht.
        Shophandel shophandel = new Shophandel(shopbuch, shopware, kasse,
                // Derselbe Fund: Hier stand der Schnappschuss der Gebuehren. Und die
                // Uhr kommt dazu, weil der Tagesdeckel des Bundesmarktes den heutigen
                // Tag braucht.
                wirtschaft,
                // DIE ANMELDUNG [Teil C]: "Erst danach oeffnet sich der Bundesmarkt"
                // (Plan 10.1). Als HALTER und nicht als Antwort - gefragt wird im
                // Augenblick des Klicks, denn ein Neuladen kann die Fassung aendern.
                anmeldung, uhr, getLogger());
        // DIE TAEGLICHE LADENGEBUEHR [Teil E6]. Hier und nicht in
        // richteFreistaedteEin: Sie braucht das Shopbuch, und das entsteht erst in
        // dieser Methode. Ohne Freistadtbuch entsteht sie gar nicht - dann gibt es
        // keine Stadt, an die ein Laden zahlen koennte.
        if (freistadtbuch != null) {
            ladengebuehrlauf = new Ladengebuehrlauf(shopbuch, shopablage, freistadtbuch,
                    weltschluessel(freistadtwelt), uhr, wirtschaft, getServer(),
                    sprache, getLogger());
        }
        shopklick = new Shopklick(shophandel, sprache);
        getServer().getPluginManager().registerEvents(shopklick, this);
        getLogger().info("Kistenshops aktiv: " + gefunden + " im Speicher, "
                + Shopliste.startzeile(wirtschaft.werte().handel()));
        // Und die Ankaufstaende des Bundes - seit dem 10.11.2026 der einzige Weg, an
        // den Bund zu verkaufen. Gezaehlt wird mit der EINEN Frage danach
        // (Bundankauf.istAnkaufstand) und nicht mit einem eigenen Vergleich.
        int staende = 0;
        for (Kistenshop shop : shopablage.alle()) {
            if (Bundankauf.istAnkaufstand(shop, Handelsart.KUNDE_VERKAUFT)) {
                staende++;
            }
        }
        getLogger().info(Shopliste.bundzeile(staende, wirtschaft.werte().markt()));
        pruefeFreieTruhen(shopbuch);
        // DIE EINRICHTUNG DER STADT, und sie steht HIER am Ende des Starts: Die Wache
        // braucht die Truhenstellen (richteTruheEin, zwei Schritte vorher) UND die
        // Shops. Frueher gebaut waere sie eine Wache, die die Freie Truhe zusperrt und
        // jeden Kistenshop seinem Besitzer verschliesst.
        getServer().getPluginManager().registerEvents(new Einrichtungswache(
                weltschluessel(ladenwelt), truhenstellen, shopbuch::istShopkiste,
                sprache), this);
        getLogger().info("Einrichtungsschutz: Behaelter in "
                + weltschluessel(ladenwelt) + " bleiben zu - ausser der Freien Truhe,"
                + " den Kistenshops und den gemieteten Staenden. Der Abenteuermodus"
                + " verhindert dort das Bauen, nicht das Oeffnen; " + Schutzwache.UEBERALL
                + " oeffnet alles.");
    }

    /**
     * Prueft, ob an einer Freien Truhe ein SHOPSCHILD haengt.
     *
     * <p><b>Ein Bundschild an der Gemeinschaftstruhe waere ein Stand, der die Ware aller
     * verkauft</b>, ein Spielerschild einer, der sie auf ein eigenes Konto verkauft.
     * Beides ist kein Spielerloch - ein Schild stellt nur auf, wer dort bauen darf -,
     * aber es ist eine Falle fuer den Betreiber: In die Freie Truhe legt jeder hinein,
     * und was darin liegt, waere dann verkaeuflich.
     *
     * <p><b>HIER und nicht in {@link #richteTruheEin}</b>: Dort sind die Shops noch nicht
     * gelesen - sie entstehen in {@link #richteShopsEin} -, und eine Pruefung gegen einen
     * leeren Zwischenspeicher findet nie etwas. Sie waere eine Zeile, die immer schweigt,
     * und das ist schlimmer als keine.
     *
     * <p><b>Gefragt wird mit {@code anDerKiste} und NICHT mit {@code istShopkiste}</b>:
     * Jene gibt bei stummer Datenbank eine LEERE Liste ("erfinde keinen Besitzer"), diese
     * ein {@code true} ("schuetze die Kiste"). Mit der zweiten Frage klagte ein
     * Datenbankausfall ueber JEDE eingetragene Stelle - und eine Klage ueber etwas, das
     * nicht da ist, nimmt der Leser beim naechsten Mal nicht mehr ernst.
     *
     * <p>Eine Doppelkiste steht mit BEIDEN Haelften in {@code freie-truhe.truhen}, und
     * ein Shopschild haengt an genau einer davon - gefragt wird darum je Stelle und nicht
     * je Truhe.
     */
    private void pruefeFreieTruhen(Shopbuch shopbuch) {
        if (truhenstellen == null) {
            return;
        }
        for (Blockort stelle : truhenstellen.alle()) {
            for (Kistenshop shop : shopbuch.anDerKiste(stelle.welt(), stelle.x(),
                    stelle.y(), stelle.z())) {
                getLogger().severe("An der Freien Truhe bei " + stelle.welt() + " "
                        + stelle.x() + "/" + stelle.y() + "/" + stelle.z()
                        + " haengt ein " + (shop.demBund() ? "BUNDSCHILD" : "SHOPSCHILD")
                        + " bei " + shop.schildX() + "/" + shop.schildY() + "/"
                        + shop.schildZ() + ". Damit wird die Gemeinschaftsware verkauft:"
                        + " Jeder legt hinein, einer verkauft. Entweder das Schild"
                        + " abbauen oder die Stelle aus " + Truhenverzeichnis.PFAD_TRUHEN
                        + " nehmen.");
            }
        }
    }

    /**
     * Baut Lager und Kasse auf.
     *
     * @return {@code false}, wenn eine eingetragene Datenbank nicht erreichbar ist - dann
     *         soll das Plugin nicht starten
     */
    private boolean richteAblageEin(long startguthaben) {
        String url = getConfig().getString("datenbank.url", "").trim();

        if (url.isEmpty()) {
            fluechtig = true;
            lager = new SpeicherLager();
            kasse = new SpeicherKasse(startguthaben);
            // Die Staedtekasse mit 0 - siehe das Feld stadtkasse.
            stadtkasse = new Stadtkasse(new SpeicherKasse(0L));
            // Die Goldmark ebenfalls mit 0, und hier ohne eigene Tabellen: Im
            // Arbeitsspeicher ist jede Kasse ohnehin eine eigene Karte, und das ist
            // kein Unterschied in der Sache - getrennt sind sie so und so.
            goldkasse = new Goldkasse(new SpeicherKasse(0L));
            // Braucht keine Uhr: Der Tag kommt mit dem Zuschlag herein und bei lies() von
            // aussen. Eine Uhr hier waere eine zweite Quelle fuer die Tagesgrenze.
            spielzeit = new SpeicherSpielzeit();
            bonusablage = new SpeicherBonus();
            // Braucht keine Uhr: Der Tag kommt bei jedem Aufruf von aussen herein.
            // Dass diese Fassung beim Neustart alles vergisst, kostet hier GELD - jeder
            // Spieler hat danach wieder sein gratis Los. Die Warnung darunter nennt es
            // nicht eigens; fuer einen Server, auf dem Geld zaehlt, ist die
            // MariaDB-Fassung ohnehin die einzige richtige.
            losablage = new SpeicherLose();
            // Braucht keine Uhr: Der Lauf kommt bei jedem Aufruf von aussen herein.
            // Dass diese Fassung beim Neustart alles vergisst, kostet hier mehr als bei
            // den anderen: Ein Neustart MITTEN IM FENSTER loescht Punkte UND
            // Anmeldungen, und die Startgelder sind dann abgebucht und der Topf weg.
            wettbewerbsablage = new SpeicherWettbewerb();
            // Braucht keine Uhr: Beginn und Ende kommen mit dem Vertrag herein.
            mietablage = new SpeicherMiete();
            // Braucht ebenfalls keine Uhr: Der Zeitpunkt kommt beim Einlegen herein.
            // Dass diese Fassung beim Neustart alles vergisst, ist hier schwerer zu
            // verkraften als bei den anderen - ein vergessenes Abholfach ist verlorener
            // Besitz. Die Warnung darunter nennt es.
            abholablage = new SpeicherAbholfach();
            // Dass diese Fassung beim Neustart alles vergisst, ist beim Blockbuch leicht
            // zu verkraften: Es ist ein Protokoll und kein Besitz. Fuer einen Probelauf
            // ist es trotzdem die richtige Fassung - der Befehl und das Aufraeumen laufen
            // durch GENAU denselben Code wie mit Datenbank.
            blockbuchablage = new SpeicherBlockbuch();
            // Dass diese Fassung beim Neustart alles vergisst, heisst bei der Freien
            // Truhe: Nach einem Neustart darf jeder noch einmal seine Tagesmenge nehmen.
            // Fuer einen Probelauf ist das tragbar - verloren ist nichts, nur verschenkt.
            truhenablage = new SpeicherTruhe();
            // Derselbe Zwischenspeicher wie mit Datenbank, nur mit einer anderen
            // Wahrheit darunter: So laeuft der Betrieb durch GENAU denselben Code, ob
            // eine Datenbank da ist oder nicht. Eine Fassung ohne Zwischenspeicher waere
            // ein zweiter Weg zu der Frage, wo ein Shop haengt.
            shopablage = new ZwischenspeicherShops(new SpeicherShops());
            // Dieselbe Bauart wie bei den Shops, und aus demselben Grund: Der Betrieb
            // laeuft durch GENAU denselben Code, ob eine Datenbank da ist oder nicht.
            grundstuecksablage =
                    new ZwischenspeicherGrundstuecke(new SpeicherGrundstuecke());
            // Braucht ebenfalls keine Uhr: Der Zeitpunkt kommt im Antrag von aussen.
            berufsablage = new ZwischenspeicherBerufe(new SpeicherBerufe());
            // Braucht ebenfalls keine Uhr: Der Zeitpunkt kommt im Antrag von
            // aussen. Dass diese Fassung beim Neustart alles vergisst, ist beim
            // Rang leicht zu verkraften: Verloren ist nichts, was jemand gekauft
            // hat - er steigt wieder auf, sobald der Takt misst, denn die
            // Bedingungen liegen in der Spielzeit und im Besitz und nicht hier.
            rangablage = new SpeicherRaenge();
            // Die Annahme der Regeln - fluechtig. Das ist hier teurer als beim Rang:
            // Nach jedem Neustart muss jeder erneut zustimmen, und ein NACHWEIS der
            // Zustimmung ist weg. Die Warnung darunter sagt es; wer die Zustimmung als
            // Nachweis braucht, braucht eine Datenbank.
            anmeldeablage = new SpeicherAnmeldungen();
            // Der Rundgangsfortschritt - fluechtig. Leichter zu verkraften als die
            // Anmeldung: Verloren ist kein Nachweis, sondern ein Weg, den man noch
            // einmal gehen kann. Wer den Rundgang als Rangbedingung gesetzt hat, sollte
            // es trotzdem wissen - ohne Datenbank steigt dann niemand dauerhaft auf.
            rundgangsablage = new SpeicherRundgang();
            // Dieselbe Bauart wie bei den Shops: derselbe Zwischenspeicher, nur mit
            // einer anderen Wahrheit darunter.
            wolkenstaende = new ZwischenspeicherWolken(new SpeicherWolken());
            getLogger().warning("Keine Datenbank eingetragen: Parzellen, Guthaben und das"
                    + " Abholfach gelten nur bis zum naechsten Neustart.");
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
            // DIESELBE Tabelle, nur ohne Startguthaben - siehe das Feld stadtkasse.
            stadtkasse = new Stadtkasse(new MariaKasse(datenbank, 0L));
            // UND EIGENE TABELLEN fuer die Goldmark, anders als bei der
            // Staedtekasse: Es ist eine andere WAEHRUNG. Die Begruendung in ganzer
            // Laenge steht an Kassentabellen und am Schemaschritt 32.
            goldkasse = new Goldkasse(
                    new MariaKasse(datenbank, 0L, Kassentabellen.GOLDMARK));
            // KEIN Zwischenspeicher darum: Fortschreiben ist additiv, und ein
            // zwischengespeicherter additiver Schreibzug ist entweder verloren oder
            // doppelt. Gelesen wird nur auf Befehl, also auch nichts zu sparen.
            spielzeit = new MariaSpielzeit(datenbank);
            bonusablage = new MariaBonus(datenbank);
            // KEIN Zwischenspeicher: Gezaehlt wird AUFSCHLAGEND, und ein
            // zwischengespeicherter aufschlagender Schreibzug ist entweder verloren oder
            // doppelt - dieselbe Begruendung wie bei der Spielzeit. Gelesen wird nur auf
            // Befehl, also auch nichts zu sparen.
            losablage = new MariaLose(datenbank);
            // KEIN Zwischenspeicher: Die Punkte werden aufschlagend geschrieben, und ein
            // zwischengespeicherter aufschlagender Schreibzug ist entweder verloren oder
            // doppelt. Gelesen wird je Fang und einmal je Abrechnung - nichts zu sparen.
            wettbewerbsablage = new MariaWettbewerb(datenbank);
            // KEIN Zwischenspeicher: Gelesen wird auf Befehl und - ab Teil C - einmal je
            // Tageslauf. Ein Zwischenspeicher haette hier nichts zu sparen und waere ein
            // zweiter Ort fuer die Frage, wem ein Laden gehoert.
            mietablage = new MariaMiete(datenbank);
            // KEIN Zwischenspeicher: Gelesen wird auf Befehl und einmal je Beitritt, und
            // die Inhaltsspalte haelt bis zu ein Megabyte je Zeile. Ein Zwischenspeicher
            // darueber waere viel Arbeitsspeicher fuer nichts - und ein zweiter Ort fuer
            // Besitz.
            abholablage = new MariaAbholfach(datenbank);
            // KEIN Zwischenspeicher: Dies ist die groesste Tabelle des Projekts und ein
            // reiner Schreibpfad. Gelesen wird auf Befehl, also auch nichts zu sparen -
            // und ein Zwischenspeicher darueber waere eine zweite Wahrheit ueber ein
            // Protokoll. Gesammelt wird VOR der Ablage, in Blockbuch.
            blockbuchablage = new MariaBlockbuch(datenbank);
            // KEIN Zwischenspeicher: Gefragt wird zweimal je Truhengriff (beim Oeffnen
            // und beim Schliessen), und geschrieben wird AUFSCHLAGEND - ein
            // zwischengespeicherter aufschlagender Schreibzug ist entweder verloren oder
            // doppelt. Dieselbe Begruendung wie bei der Spielzeit.
            truhenablage = new MariaTruhe(datenbank);
            // ZWISCHENSPEICHER, und hier ist er keine Ersparnis, sondern die Sache:
            // betroffen() fragt bei JEDER Explosion fuer JEDEN Block und bei jedem
            // Trichtertakt, ob dort ein Shop haengt. Eine Datenbankabfrage an dieser
            // Stelle waere derselbe Fehler, den QuickShop #2165 mit einem Puffer von drei
            // Minuten Verfallszeit zu loesen versuchte - und dessen veraltete Antwort war
            // genau die Luecke. Dieser hier ist eine VOLLSTAENDIGE Kopie ohne
            // Verfallszeit: Es gibt keinen Augenblick, in dem er etwas Falsches sagt.
            shopablage = new ZwischenspeicherShops(new MariaShops(datenbank));
            // VOLLSTAENDIG und ohne Verfall, wie die Shops - und mit einer Obergrenze,
            // die aus der Weltgeometrie folgt: 399 Felder zu 256 Zellen. Die Rechnung
            // steht an ZwischenspeicherGrundstuecke.
            grundstuecksablage =
                    new ZwischenspeicherGrundstuecke(new MariaGrundstuecke(datenbank));
            // KEIN Zwischenspeicher: /beruf ist kein heisser Lesepfad. Mit Teil E, wenn
            // jeder Verdienst den Beruf fragt, wird das neu bedacht - und dann als eigene
            // Schicht wie ZwischenspeicherLager, nicht als Feld hier drin.
            // ZwischenspeicherBerufe darum: Seit Teil E fragt der Verdienst bei JEDEM
            // abgebauten und JEDEM gesetzten Block, welche Berufe ein Spieler haelt. Eine
            // Datenbankabfrage je Block waere derselbe Fehler, den der Zwischenspeicher
            // vor dem Lager schon geloest hat. Dass ein /beruf waehlen sofort wirkt,
            // besorgt dessen Leerung bei jedem Schreibzug - und weil /beruf durch
            // DIESELBE Ablage geht, kann das nicht auseinanderlaufen.
            berufsablage = new ZwischenspeicherBerufe(new MariaBerufe(datenbank));
            // KEIN Zwischenspeicher: Gelesen wird einmal je Beitritt und einmal je
            // Taktschlag - dafuer lohnt keine zweite Wahrheit ueber den Rang.
            // Geschrieben wird gegen einen erwarteten Stand, und ein
            // zwischengespeicherter Vergleichswert waere genau der veraltete Stand,
            // den dieser Riegel abweisen soll.
            rangablage = new MariaRaenge(datenbank);
            // KEIN Zwischenspeicher, aus demselben Grund wie beim Rang - und hier noch
            // strenger: Ein gemerktes "hat zugestimmt" waere eine zweite Wahrheit ueber
            // einen Nachweis.
            anmeldeablage = new MariaAnmeldungen(datenbank);
            // KEIN Zwischenspeicher: eine Abfrage auf den Hauptschluessel, und nur
            // solange der Spieler in einem Stationsbereich steht.
            rundgangsablage = new MariaRundgang(datenbank);
            // VOLLSTAENDIG und ohne Verfall, wie die Grundstuecke - und mit einer
            // Obergrenze aus der Weltgeometrie: hoechstens 1443 Inseln. Die Rechnung
            // steht an ZwischenspeicherWolken. Gefuellt wird in richteWolkenEin.
            wolkenstaende = new ZwischenspeicherWolken(new MariaWolken(datenbank));
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

    /**
     * Baut den Befehlsbaum von {@code /beruf} - im Ereignis, nicht im onEnable.
     *
     * <p>Dieselbe Form wie {@link #bundBefehle}: Der Baum wird genau einmal gebraucht, und
     * zwar in dem Ereignis, das ihn anmeldet. Ein Feld dafuer waere ein zweiter Ort, an dem
     * derselbe Baum stehen koennte - und bei zwei Anmeldungen haette der Server zwei
     * Baeume mit einem Namen.
     */
    private BerufeBefehle berufeBefehle() {
        return new BerufeBefehle(berufsablage, berufe, wirtschaft, kasse, uhr,
                getLogger(), sprache);
    }



    /**
     * Baut den Befehlsbaum von {@code /regeln} - im Ereignis, nicht im onEnable.
     *
     * <p>Dieselbe Form und derselbe Grund wie bei {@link #menueBefehle}. Die HAND steht
     * hier und nicht im Befehl: Die Dialog-API von Paper laeuft in keiner Probe, und was
     * nicht laeuft, gehoert hinter eine Naht. Der LOGGER geht mit hinein, weil die Hand
     * das einzige Stueck des Plugins ist, dessen Scheitern niemand sonst bemerkt - ein
     * Spieler sieht dann nur Chatzeilen statt eines Fensters.
     */
    private RegelnBefehle regelnBefehle() {
        Formularhand hand = new Formularhand(getLogger());
        // DIESELBE Hand fuer beide Fenster: Das Lesefenster hat einen Knopf, das
        // Anmeldeformular zwei. Zwei Haende meldeten denselben Ausfall zweimal, denn
        // jede haette ihren eigenen Zaehler.
        return new RegelnBefehle(hand::zeige, sprache, anmeldung,
                new Anmeldeformular(hand::frage, sprache));
    }

    /**
     * Baut den Befehlsbaum von {@code /menu} - im Ereignis, nicht im onEnable.
     *
     * <p>Dieselbe Form und derselbe Grund wie bei {@link #berufeBefehle}. Die HAND steht
     * hier und nicht im Befehl: {@code Bukkit.createInventory} und {@code new ItemStack}
     * brauchen einen laufenden Server, und hinter der Naht laeuft jeder Zweig des Befehls
     * durch eine Probe.
     */
    private MenueBefehle menueBefehle() {
        Menuehand hand = new Menuehand(sprache, getLogger());
        return new MenueBefehle(hand::oeffne, sprache);
    }

    /**
     * Baut den Befehlsbaum von {@code /rang} - im Ereignis, nicht im onEnable.
     *
     * <p>Dieselbe Form und derselbe Grund wie bei {@link #berufeBefehle}. Die MESSUNG ist
     * woertlich dieselbe Naht wie beim Muenzhaus: ein Lambda auf das Feld
     * {@code aufstiegslauf}, denn der entsteht erst in {@code richteRaengeEin} und ist
     * die EINE Stelle, die eine Bedingung messen darf. Ist das Feld null - der Start ist
     * vorher abgebrochen -, gilt KEIN Messwert; dann zeigt die Auskunft jede Bedingung
     * als offen, und das ist die sichere Richtung: Lieber zeigt sie zu wenig, als dass
     * sie einen Aufstieg verspricht, den der Aufstiegslauf nicht gibt.
     */
    private RangBefehle rangBefehle() {
        return new RangBefehle(new Rangschau(rangablage, wirtschaft,
                (wer, gebraucht) -> aufstiegslauf == null
                        ? java.util.Map.of()
                        : aufstiegslauf.messeFuer(wer, gebraucht)),
                sprache, getLogger());
    }

    /**
     * Baut den Befehlsbaum von {@code /markt} - im Ereignis, nicht im onEnable.
     *
     * <p>Dieselbe Form und derselbe Grund wie bei {@link #berufeBefehle}.
     *
     * <p><b>Die ANKUNFTSWELT braucht dieser Befehl seit dem 10.11.2026 nicht mehr.</b>
     * Sie war der Ort des Bundesmarktes und damit die Pruefung "bist du in Altfurt?" vor
     * jedem {@code /markt verkaufen}. Der Zweig ist weggefallen - verkauft wird am
     * Schild -, und ein Schild steht, wo es steht. Das ist eine schaerfere Ortsangabe,
     * als eine Weltpruefung je war.
     *
     * <p><b>Dafuer braucht er die SHOPABLAGE</b>: Seit die Tafel {@code bundesmarkt.ankauf}
     * gefallen ist, zeigt {@code /markt} die Staende selbst - und die stehen in der
     * Datenbank. Darum wird dieser Befehl erst gebaut, nachdem die Shops gelesen sind;
     * {@code richteShopsEin} laeuft vorher, und ohne lesbare Shops ist das Feld leer.
     */
    private MarktBefehle marktBefehle() {
        return new MarktBefehle(shopablage, wirtschaft, kasse, uhr, sprache,
                getLogger());
    }

    /**
     * Der erste Beruf der Einstellungsdatei, zu dem die Sprachdatei kein Wort hat.
     *
     * <p>In EINE Richtung geprueft und nicht in beide: Ein Wort ohne Beruf ist harmlos -
     * es ist eine Zeile, die nicht wirkt, und der Bericht von {@code /freimarken neuladen}
     * nennt sie. Ein Beruf ohne Wort dagegen laesst den ersten {@code /beruf} in einen
     * Wurf laufen, mitten im Spiel.
     *
     * @return der Schluessel, oder {@code null}, wenn jeder Beruf ein Wort hat
     */
    private String ersterBerufOhneWort() {
        for (String beruf : berufe.berufe()) {
            if (!sprachstand.buch().saetze().containsKey(new Berufsname(beruf).pfad())) {
                return beruf;
            }
        }
        return null;
    }

    private void registriereBefehle() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, ereignis -> {
            var verzeichnis = ereignis.registrar();
            Set<String> angemeldet = new TreeSet<>();
            // Die Beschreibungen kommen aus sprache-vorlage.yml, Abschnitt
            // befehl.beschreibung. Dass das zeitlich geht, ist belegt: Dieser Lambda
            // laeuft NACH onEnable (dasselbe sagt der Kommentar zu spielzeitBefehle), das
            // Feld sprache steht also. Dass eine Aenderung daran erst nach einem Neustart
            // wirkt, steht in Sprachstand.NUR_BEIM_START - und /freimarken neuladen sagt
            // es in jeder Antwort.
            angemeldet.addAll(verzeichnis.register(bundBefehle().freimarken(),
                    sprache.wort(BundSatz.BEFEHL_FREIMARKEN, Einsatz.keine()),
                    List.of("fm")));
            // /p und /konto stehen so im Plan (Abschnitt 11, [entschieden]); /pz bleibt
            // als zweite Kurzform, weil sie schon im Umlauf ist.
            //
            // Zweispurig deutsch und englisch [entschieden 02.11.2026]: Viele Spieler
            // kommen von englischsprachigen Servern und tippen /plot und /balance, bevor
            // sie /parzelle lesen. Ein zweiter Name kostet eine Zeile; eine Fehlmeldung
            // auf einen Befehl, den jeder kennt, kostet den ersten Eindruck. Die Sprache
            // der MELDUNGEN ist davon unberuehrt - sie haengt an der Sprachdatei aus Plan
            // Abschnitt 13 und wird spaeter je Spieler waehlbar.
            angemeldet.addAll(verzeichnis.register(befehle.parzelle(),
                    sprache.wort(BundSatz.BEFEHL_PARZELLE, Einsatz.keine()),
                    List.of("p", "pz", "plot")));
            angemeldet.addAll(verzeichnis.register(befehle.mark(),
                    sprache.wort(BundSatz.BEFEHL_MARK, Einsatz.keine()),
                    List.of("konto", "balance", "bal")));
            angemeldet.addAll(verzeichnis.register(warpBefehle.warp(),
                    sprache.wort(BundSatz.BEFEHL_WARP, Einsatz.keine())));
            angemeldet.addAll(verzeichnis.register(rueckwegBefehle.zurueck(),
                    sprache.wort(BundSatz.BEFEHL_ZURUECK, Einsatz.keine()),
                    List.of("back")));
            angemeldet.addAll(verzeichnis.register(anfragenBefehle.tpa(),
                    sprache.wort(BundSatz.BEFEHL_TPA, Einsatz.keine())));
            angemeldet.addAll(verzeichnis.register(anfragenBefehle.tpahere(),
                    sprache.wort(BundSatz.BEFEHL_TPAHERE, Einsatz.keine()),
                    List.of("tpaher", "einladen")));
            // Die Kurzformen sind die Schreibweisen aus dem Chat: Wer tpaaccept tippt,
            // soll nicht lesen, dass es den Befehl nicht gibt.
            angemeldet.addAll(verzeichnis.register(anfragenBefehle.tpaccept(),
                    sprache.wort(BundSatz.BEFEHL_TPACCEPT, Einsatz.keine()),
                    List.of("tpaaccept", "annehmen")));
            angemeldet.addAll(verzeichnis.register(anfragenBefehle.tpdeny(),
                    sprache.wort(BundSatz.BEFEHL_TPDENY, Einsatz.keine()),
                    List.of("tpdecline", "ablehnen")));
            // /job als zweiter Name, derselbe Grund wie bei /plot und /balance: Wer von
            // einem englischsprachigen Server kommt, tippt ihn, bevor er /beruf liest.
            angemeldet.addAll(verzeichnis.register(berufeBefehle().beruf(),
                    sprache.wort(BundSatz.BEFEHL_BERUF, Einsatz.keine()),
                    List.of("job")));
            // /menue als zweiter Name: Der Plan schreibt /menu, und wer deutsch tippt,
            // schreibt das e dahinter. Beide Namen kosten nichts und ersparen ein
            // "unbekannter Befehl" im ersten Augenblick eines Neulings.
            angemeldet.addAll(verzeichnis.register(menueBefehle().menu(),
                    sprache.wort(BundSatz.BEFEHL_MENU, Einsatz.keine()),
                    List.of("menue")));
            // /regeln und /hilfe [21.11.2026] - die zwei Befehle, auf die das
            // Hauptmenue schon zeigt. Beide stehen in Plan 11.1, und beide sind fuer
            // jeden: Wer die Regeln nicht lesen darf, kann sich nicht an sie halten.
            //
            // /rules als zweiter Name, dieselbe Linie wie plot, balance, job und rank.
            //
            // KEIN zweiter Name "help" fuer /hilfe, und das ist gemessen und nicht
            // vermutet: Den Namen hat der Server selbst. Ein zweiter Anbieter landet im
            // Namensraum des Plugins (freimarken:help), und wer /help tippt, bekaeme
            // weiter die alphabetische Wand von Paper - eine Verwechslung, die man nicht
            // sieht, weil beides etwas anzeigt.
            angemeldet.addAll(verzeichnis.register(regelnBefehle().regeln(),
                    sprache.wort(BundSatz.BEFEHL_REGELN, Einsatz.keine()),
                    List.of("rules")));
            angemeldet.addAll(verzeichnis.register(new HilfeBefehle(sprache).hilfe(),
                    sprache.wort(BundSatz.BEFEHL_HILFE, Einsatz.keine()),
                    List.of()));
            // /lotterie [22.11.2026, Schritt 14] mit /lottery als zweitem Namen -
            // dieselbe Linie wie plot, balance, job und rank: Wer von einem
            // englischsprachigen Server kommt, tippt ihn zuerst.
            //
            // Die Pruefung auf null faengt den Fall, dass der Start vorher abgebrochen
            // ist. Ohne sie wuerfe die Anmeldung, und dann fehlten AUCH die Befehle
            // darunter - ein Fehler in der Lotterie nahm die halbe Befehlsliste mit.
            if (lotterieBefehle != null) {
                angemeldet.addAll(verzeichnis.register(lotterieBefehle.lotterie(),
                        sprache.wort(BundSatz.BEFEHL_LOTTERIE, Einsatz.keine()),
                        List.of("lottery")));
            }
            // /wettbewerb [22.11.2026, Schritt 14] mit /contest als zweitem Namen -
            // dieselbe Linie wie plot, balance, job, rank und lottery.
            if (wettbewerbBefehle != null) {
                angemeldet.addAll(verzeichnis.register(wettbewerbBefehle.wettbewerb(),
                        sprache.wort(BundSatz.BEFEHL_WETTBEWERB, Einsatz.keine()),
                        List.of("contest")));
            }
            // /rank als zweiter Name, dieselbe Linie wie plot, balance und job: Wer von
            // einem englischsprachigen Server kommt, tippt ihn zuerst. Ein Spieler, der
            // "unbekannter Befehl" liest, sucht die Rangleiter nicht weiter.
            angemeldet.addAll(verzeichnis.register(rangBefehle().rang(),
                    sprache.wort(BundSatz.BEFEHL_RANG, Einsatz.keine()),
                    List.of("rank")));
            // /bundesmarkt als zweiter Name: Der Ort heisst so, und wer ihn sucht, tippt
            // seinen Namen und nicht die Abkuerzung.
            angemeldet.addAll(verzeichnis.register(marktBefehle().markt(),
                    sprache.wort(BundSatz.BEFEHL_MARKT, Einsatz.keine()),
                    List.of("bundesmarkt")));
            // /laden - die Einheiten ansehen, mieten, verlaengern, kuendigen. Kein
            // Zweitname: Plan 11.1 fuehrt genau einen Namen, und "markthalle" waere ein
            // zweiter Ort fuer dieselbe Liste.
            //
            // Die Mietdauer kommt als Abfrage und nicht als Zahl: Sie steht in der
            // Absage "noch zu frueh", und ein Neuladen muss sie erreichen.
            angemeldet.addAll(verzeichnis.register(
                    new LadenBefehle(ladenverzeichnis, mietbuch, abholbuch, sprache,
                            weltschluessel(ladenwelt),
                            () -> wirtschaft.werte().laeden().dauerTage()).laden(),
                    sprache.wort(BundSatz.BEFEHL_LADEN, Einsatz.keine()),
                    List.of()));
            // /freistadt - die Liste und das Gruenden. Das Buch kann null sein (die
            // Welt fehlt); der Befehl wird trotzdem angemeldet und sagt es dann. Ein
            // Befehl, den es je nach Einstellungsdatei gibt oder nicht, waere fuer einen
            // Spieler ein Fehler ohne Erklaerung.
            //
            // Die Hoechstzahl kommt als Abfrage und nicht als Zahl: Sie steht in der
            // Absage "du hast genug", und ein Neuladen muss sie erreichen.
            angemeldet.addAll(verzeichnis.register(
                    new FreistadtBefehle(freistadtbuch, kasse, sprache,
                            freistadtbuch == null ? "" : weltschluessel(freistadtwelt),
                            () -> wirtschaft.werte().freistaedte().hoechstzahl(),
                            () -> wirtschaft.werte().rangWerbung(),
                            // Die Zone als Abfrage, aus demselben Grund wie die
                            // Hoechstzahl und die Werbezeile: Sie steht in zeit.zone und
                            // ist neuladbar.
                            grundstuecksbuch, absteckbuch,
                            () -> wirtschaft.werte().zeit().zone(), getLogger())
                            .freistadt(),
                    sprache.wort(BundSatz.BEFEHL_FREISTADT, Einsatz.keine()),
                    List.of()));
            // /shop - der SCHLUESSEL zu einer fremden Shopkiste. Der erste Befehl des
            // Shop-Bereichs ueberhaupt: Ein Kistenshop wird sonst durch ein Schild
            // angelegt und durch einen Klick bedient. Eine Freigabe ist aber eine
            // Abmachung zwischen zwei Menschen, und dafuer braucht es einen Namen, den
            // man tippt.
            //
            // Kein Zweitname: "chestshop" waere der eines fremden Plugins, und
            // "kistenshop" tippt niemand, der /shop sucht.
            angemeldet.addAll(verzeichnis.register(
                    ShopBefehle.ueberBukkit(shopablage, sprache, wirtschaft,
                            getLogger()).shop(),
                    sprache.wort(BundSatz.BEFEHL_SHOP, Einsatz.keine()),
                    List.of()));
            // /werder - die eigenen Meerinseln und das Nehmen einer neuen [Schritt 10].
            // Das Buch kann null sein (die Welt fehlt); der Befehl wird trotzdem
            // angemeldet und sagt es dann - dieselbe Entscheidung wie bei /freistadt.
            //
            // Kein Zweitname: "insel" waere der zweite Ort fuer dieselbe Liste, und
            // Plan 2423 fuehrt genau einen Namen. Der Weltname geht MIT hinein, denn
            // die Absage "nur in Hafenwerder" nennt ihn - und zwar ohne Namensraum,
            // wie ein Spieler ihn tippt.
            //
            // Die Hoechstzahl kommt als Abfrage und nicht als Zahl: Sie steht in der
            // Absage "du hast genug", und ein Neuladen muss sie erreichen. Was ein RANG
            // daraufsetzt, holt der Befehl selbst aus den Rechten.
            angemeldet.addAll(verzeichnis.register(
                    new WerderBefehle(inselbuch, kasse, sprache,
                            inselbuch == null ? "" : weltschluessel(inselwelt),
                            inselwelt,
                            () -> wirtschaft.werte().inseln().hoechstzahl(),
                            () -> wirtschaft.werte().rangWerbung()).werder(),
                    sprache.wort(BundSatz.BEFEHL_WERDER, Einsatz.keine()),
                    List.of()));

            // /wolke - ein EIGENER Baum und kein Zweig am /werder [Schritt 11]. Der
            // Grund ist die Tiefe: Diese Welt hat fuenf Zweige und bekommt weitere
            // (Stufe, Herausforderungen, Biom, Zuruecksetzen). Als Unterbaum hiesse das
            // "/werder wolke herausforderung" - drei Worte, bevor etwas geschieht.
            //
            // Die REISE geht mit hinein, und das ist der Unterschied zu jedem anderen
            // Besitzbefehl: In einer Welt aus Leere steht man vor dem Nehmen nirgends
            // und nach dem Nehmen weiter auf der Ankunftsplattform. Ohne Reise waere
            // "deine Insel liegt auf Feld 3/-1" eine Auskunft ohne Weg.
            angemeldet.addAll(verzeichnis.register(
                    new WolkeBefehle(wolkenbuch, aufgabenbuch, Vorratshand::fuer,
                            wolkenpflege, kasse, sprache, this::setzeInsel,
                            (wer, ziel) -> reise.fuehre(wer, ziel,
                                    WolkeBefehle.ARGUMENT),
                            wolkenbuch == null ? "" : weltschluessel(wolkenwelt),
                            () -> wirtschaft.werte().wolken().hoechstzahl(),
                            () -> wirtschaft.werte().rangWerbung()).wolke(),
                    sprache.wort(BundSatz.BEFEHL_WOLKE, Einsatz.keine()),
                    List.of()));

            // Diese Zeile ist nicht Zierde. /parzelle und /mark sind spielerpflichtig, und
            // auf der Konsole antwortet ein spielerpflichtiger Befehl wortgleich wie ein
            // Befehl, den es nicht gibt. Ohne diese Ausgabe laesst sich von aussen nicht
            // feststellen, ob die Anmeldung geklappt hat - und sie passiert hier, in einem
            // spaeteren Ereignis, nicht in onEnable, also beweist die Bereitmeldung es auch
            // nicht.
            getLogger().info("Befehle angemeldet: " + String.join(", ", angemeldet));
        });
    }

    /**
     * Baut den Befehlsbaum von {@code /freimarken} - im Ereignis, nicht im onEnable.
     *
     * <p>Seit dem 06.11.2026 steht der Baum in {@link BundBefehle} und nicht mehr
     * hier, und der Grund ist eine Probe: Ein {@code JavaPlugin} laesst sich ohne
     * Paper-Klassenlader nicht bauen, also war {@code /freimarken} der einzige
     * Befehl, den keine Probe TIPPEN konnte. Alle sechs anderen Befehlsgruppen haben
     * laengst ihre eigene Klasse; dies war die letzte Ausnahme.
     *
     * <p><b>Was hierbleibt, sind die vier Verwaltungszweige.</b> Sie lesen die
     * laufende Welt, die Vorgenerierung und die Einstellungsdatei - also genau das,
     * was an diesem Plugin haengt - und gehen als Griffe hinein. Ihr Text ist
     * Betreibertext und bleibt im Programm; so sagt es auch der Kopf der
     * Sprachdatei.
     *
     * <p>Gerufen wird das erst im Lebenslauf-Ereignis {@code COMMANDS}, und darum
     * stehen {@code fluechtig}, {@code grenze} und {@code spielzeitBefehle} zu
     * diesem Zeitpunkt alle schon.
     */
    private BundBefehle bundBefehle() {
        // Eine EIGENE Anweisung und kein Argument mitten im Aufruf: So steht die
        // Reihenfolge der vier Angaben fuer sich, und GrenzeTest 31y kann sie
        // lesen, ohne an der ersten inneren Klammer haltzumachen.
        BundBefehle.Stand stand = new BundBefehle.Stand(
                getPluginMeta().getVersion(),
                String.valueOf(Runtime.version().feature()),
                getServer().getMinecraftVersion(), fluechtig);
        // Der Blockbuchzweig wird HIER gebaut und nicht in richteBlockbuchEin(): Jene
        // Methode laeuft in onEnable, dieser Aufruf im Befehlsereignis danach - und die
        // Werte, die der Zweig braucht, stehen im Feld blockbuchwerte. Ein Feld fuer den
        // fertigen Zweig waere ein zweiter Ort, an dem er entstehen koennte.
        return new BundBefehle(sprache, stand,
                wirtschaft, spielzeitBefehle.spielzeit(),
                BlockBefehle.ueberBukkit(blockbuchablage, blockbuchwerte, wirtschaft,
                        sprache, getLogger()).blockbuch(),
                (empfaenger, wer) -> zeigeWelt(empfaenger),
                (empfaenger, wer) -> zeigeVorgenerierung(empfaenger),
                this::neulade,
                (empfaenger, wer) -> zeigeMessstelle(empfaenger),
                this::bringeZurParzelle,
                geldzweig(),
                besitzzweig(),
                altfurtzweig(),
                grenze.von(), grenze.bis());
    }

    /**
     * Der Zweig von {@code /freimarken altfurt sichern} - Regel in {@link Altfurtaufbau},
     * Text hier.
     *
     * <p>Dieselbe Trennung wie bei {@link #geldzweig()} und {@link #besitzzweig()}: Was
     * gesichert wird und was ohne Bestaetigung unangetastet bleibt, entscheidet
     * {@code Altfurtaufbau.sichere} ueber die Naht - dort prueft es
     * {@code AltfurtaufbauTest} ohne Server. Hier steht nur, was der Betreiber liest.
     *
     * <p>Die Namen werden bei JEDER Vervollstaendigung neu gefragt: {@code neuladen}
     * tauscht das Verzeichnis aus, und eine beim Anmelden kopierte Liste zeigte danach
     * die alten Namen.
     */
    private BundBefehle.Altfurtzweig altfurtzweig() {
        return new BundBefehle.Altfurtzweig() {

            @Override
            public void sichere(Audience empfaenger, String wer, String bau,
                                boolean bestaetigt) {
                sichereAltfurt(empfaenger, wer, bau, bestaetigt);
            }

            @Override
            public List<String> namen(String angefangen) {
                return platzhalterbauten.vorschlaege(angefangen);
            }

            @Override
            public void hilfe(Audience empfaenger) {
                zeigeAltfurtHilfe(empfaenger);
            }
        };
    }

    /** Die Benutzung von {@code /freimarken altfurt} - fuer jede unvollstaendige Form. */
    private void zeigeAltfurtHilfe(Audience empfaenger) {
        empfaenger.sendMessage(Component.text("Altfurt sichern", NamedTextColor.GOLD));
        empfaenger.sendMessage(Component.text("/freimarken altfurt sichern",
                        NamedTextColor.YELLOW)
                .append(Component.text(" - schreibt jeden Quader, der noch KEINE Datei"
                        + " hat, nach plugins/Freimarken/" + Strukturgriff.ORDNER + "/."
                        + " Beim naechsten Start wird er gesetzt statt gerechnet.",
                        NamedTextColor.GRAY)));
        empfaenger.sendMessage(Component.text("/freimarken altfurt sichern <bau>",
                        NamedTextColor.YELLOW)
                .append(Component.text(" - nur diesen einen.", NamedTextColor.GRAY)));
        empfaenger.sendMessage(Component.text(
                        "/freimarken altfurt sichern <bau> bestaetigen",
                        NamedTextColor.YELLOW)
                .append(Component.text(" - ueberschreibt eine VORHANDENE Datei. Das ist"
                        + " der einzige Schritt hier, den nichts heilt: Ein gerechnetes"
                        + " Haus kann neu gerechnet werden, eine ersetzte Handarbeit"
                        + " nicht.", NamedTextColor.GRAY)));
        if (platzhalterbauten.leer()) {
            empfaenger.sendMessage(Component.text("In der config.yml steht kein"
                    + " Abschnitt bauten: - es gibt nichts zu sichern.",
                    NamedTextColor.RED));
            return;
        }
        empfaenger.sendMessage(Component.text("Bauten in \"" + bautenwelt + "\": ",
                        NamedTextColor.GRAY)
                .append(Component.text(String.join(", ", platzhalterbauten.namen()),
                        NamedTextColor.AQUA)));
    }

    /**
     * Schreibt die Quader Altfurts in Strukturdateien und antwortet dem Betreiber.
     *
     * <p>Jeder Versuch steht im Serverprotokoll, mit Absender: Sichern ERSETZT Dateien,
     * und wer eine Handarbeit verliert, soll nachlesen koennen, wer sie ersetzt hat.
     */
    private void sichereAltfurt(Audience empfaenger, String wer, String name,
            boolean bestaetigt) {
        if (platzhalterbauten.leer()) {
            empfaenger.sendMessage(Component.text("In der config.yml steht kein Abschnitt"
                    + " bauten: - es gibt nichts zu sichern.", NamedTextColor.RED));
            return;
        }
        World welt = welten.get(bautenwelt);
        if (welt == null) {
            empfaenger.sendMessage(Component.text("Die Welt \"" + bautenwelt + "\" ist"
                    + " nicht geladen - aus einer Welt, die nicht da ist, laesst sich"
                    + " nichts sichern. Steht sie im Abschnitt welten:?",
                    NamedTextColor.RED));
            return;
        }
        Strukturgriff strukturen = new Strukturgriff.AufDerWelt(welt,
                getServer().getStructureManager(),
                new java.io.File(getDataFolder(), Strukturgriff.ORDNER));
        Altfurtaufbau.Sicherung raus = altfurtaufbau.sichere(platzhalterbauten,
                strukturen, name, bestaetigt);
        getLogger().info("altfurt sichern von " + wer + ": gesichert "
                + raus.gesichert() + ", schon da " + raus.schonDa() + ", Klagen "
                + raus.klagen().size()
                + (raus.unbekannt() == null ? "" : ", unbekannt " + raus.unbekannt()));

        empfaenger.sendMessage(Component.text("Altfurt sichern", NamedTextColor.GOLD));
        if (raus.unbekannt() != null) {
            empfaenger.sendMessage(Component.text("\"" + raus.unbekannt() + "\" ist kein"
                    + " Bau dieser Datei. Es gibt: ", NamedTextColor.RED)
                    .append(Component.text(String.join(", ", platzhalterbauten.namen()),
                            NamedTextColor.AQUA)));
            return;
        }
        if (!raus.gesichert().isEmpty()) {
            empfaenger.sendMessage(Component.text(raus.gesichert().size()
                            + " gesichert: ", NamedTextColor.GREEN)
                    .append(Component.text(String.join(", ", raus.gesichert()),
                            NamedTextColor.AQUA))
                    .append(Component.text(" - nach plugins/Freimarken/"
                            + Strukturgriff.ORDNER + "/. Beim naechsten Start werden sie"
                            + " GESETZT statt gerechnet.", NamedTextColor.GRAY)));
        }
        for (String name2 : raus.schonDa()) {
            empfaenger.sendMessage(Component.text(name2, NamedTextColor.YELLOW)
                    .append(Component.text(" hat schon eine Datei und bleibt"
                            + " unangetastet. Ueberschreiben: ", NamedTextColor.GRAY))
                    .append(Component.text("/freimarken altfurt sichern " + name2
                            + " bestaetigen", NamedTextColor.YELLOW)));
        }
        for (String klage : raus.klagen()) {
            empfaenger.sendMessage(Component.text(klage, NamedTextColor.RED));
        }
        if (raus.gesichert().isEmpty() && raus.klagen().isEmpty()
                && raus.schonDa().isEmpty()) {
            empfaenger.sendMessage(Component.text("Nichts zu sichern.",
                    NamedTextColor.GRAY));
        }
    }

    /**
     * Der Zweig von {@code /freimarken besitz} - Regel in {@link Betreiberbesitz},
     * Text hier.
     *
     * <p>Dieselbe Trennung wie bei {@link #geldzweig()}: Die Entscheidung steht in einer
     * Klasse ohne Bukkit und ohne Text, hier steht nur, was der Betreiber liest.
     *
     * <p><b>Der bisherige Eigentuemer steht als KENNUNG in der Antwort und im
     * Protokoll</b>, nicht als Name. Einen Namen gaebe es nur ueber einen zweiten Gang
     * zum Spielerzwischenspeicher, und der kennt nur, wer sich auf diesem Server je
     * angemeldet hat - bei einem Besitz aus einem Umzug steht dort nichts. Eine
     * Protokollzeile mit einer leeren Stelle waere schlechter als eine mit der Kennung:
     * Die Kennung ist der Schluessel, unter dem der Besitz tatsaechlich gefuehrt wird.
     */
    private BundBefehle.Besitzzweig besitzzweig() {
        return new BundBefehle.Besitzzweig() {

            @Override
            public void uebertrage(Audience empfaenger, String wer, int parzelleX,
                                   int parzelleZ, String anName) {
                if (nichtBereit(empfaenger)) {
                    return;
                }
                Betreiberbesitz.Ergebnis ergebnis;
                try {
                    ergebnis = Betreiberbesitz.uebertrage(buch,
                            Spielername::kennungAuchAbwesend,
                            marktstadt.key().asString(), parzelleX, parzelleZ, anName);
                } catch (RuntimeException stoerung) {
                    gescheitert(empfaenger, wer, "uebertragen", parzelleX, parzelleZ,
                            stoerung);
                    return;
                }
                getLogger().info("Besitz von Hand: " + wer + " uebertragen "
                        + parzelleX + "/" + parzelleZ + " von "
                        + (ergebnis.vorher() == null ? "niemand" : ergebnis.vorher())
                        + " an " + anName + ", Ausgang " + ergebnis.ausgang() + ".");
                empfaenger.sendMessage(antwort(ergebnis));
            }

            @Override
            public void gibFrei(Audience empfaenger, String wer, int parzelleX,
                                int parzelleZ) {
                if (nichtBereit(empfaenger)) {
                    return;
                }
                Betreiberbesitz.Ergebnis ergebnis;
                try {
                    ergebnis = Betreiberbesitz.gibFrei(buch,
                            Spielername::kennungAuchAbwesend,
                            marktstadt.key().asString(), parzelleX, parzelleZ);
                } catch (RuntimeException stoerung) {
                    gescheitert(empfaenger, wer, "freigeben", parzelleX, parzelleZ,
                            stoerung);
                    return;
                }
                getLogger().info("Besitz von Hand: " + wer + " freigegeben "
                        + parzelleX + "/" + parzelleZ + ", bisher "
                        + (ergebnis.vorher() == null ? "niemand" : ergebnis.vorher())
                        + ", Ausgang " + ergebnis.ausgang() + ".");
                empfaenger.sendMessage(antwort(ergebnis));
            }

            @Override
            public void hilfe(Audience empfaenger) {
                empfaenger.sendMessage(Component.text(
                        "/freimarken besitz uebertragen <x> <z> <spieler> - ohne Geld"
                                + " und ohne Zustimmung; die Mitbauer des Vorbesitzers"
                                + " fallen weg, seine Kistenshops NICHT (sie haengen an"
                                + " der Schildkoordinate und sind danach fuer beide"
                                + " verschlossen).", NamedTextColor.GRAY));
                empfaenger.sendMessage(Component.text(
                        "/freimarken besitz freigeben <x> <z> bestaetigen - die Stelle"
                                + " wird wieder Bauland, ohne Rueckzahlung.",
                        NamedTextColor.GRAY));
            }

            /**
             * Ohne Marktstadt gibt es keine Parzelle - und keine Zeile ins Leere.
             *
             * <p>Der Zweig ist von der KONSOLE erreichbar, und dort ist er womoeglich
             * das erste, was nach einem Start passiert. {@code marktstadt} ist bis zum
             * Laden der Welt {@code null}, und ohne diese Frage waere die Antwort eine
             * NullPointerException im Serverprotokoll statt eines deutschen Satzes.
             */
            private boolean nichtBereit(Audience empfaenger) {
                if (marktstadt != null && buch != null) {
                    return false;
                }
                empfaenger.sendMessage(Component.text(
                        "Die Marktstadt ist noch nicht geladen - es ist nichts"
                                + " geaendert.", NamedTextColor.RED));
                return true;
            }

            private void gescheitert(Audience empfaenger, String wer, String was,
                                     int parzelleX, int parzelleZ,
                                     RuntimeException stoerung) {
                getLogger().log(Level.SEVERE, "Besitz von Hand GESCHEITERT: " + wer
                        + " wollte " + parzelleX + "/" + parzelleZ + " " + was
                        + " - die Ablage antwortet nicht.", stoerung);
                empfaenger.sendMessage(Component.text("Die Ablage antwortet nicht - es"
                        + " ist NICHTS geaendert. Der Grund steht im Serverprotokoll.",
                        NamedTextColor.RED));
            }

            private Component antwort(Betreiberbesitz.Ergebnis ergebnis) {
                String stelle = ergebnis.parzelleX() + "/" + ergebnis.parzelleZ();
                return switch (ergebnis.ausgang()) {
                    case UEBERTRAGEN -> Component.text("Parzelle " + stelle
                            + " gehoerte " + (ergebnis.vorher() == null ? "niemandem"
                                    : ergebnis.vorher())
                            + " und gehoert jetzt " + ergebnis.spieler() + " ("
                            + (ergebnis.haeltJetzt() == Betreiberbesitz.ZAHL_UNBEKANNT
                                    ? "wie viele er damit haelt, war nicht mehr"
                                            + " abzufragen"
                                    : "er haelt damit " + ergebnis.haeltJetzt()
                                            + " Parzellen")
                            + "). Gehoerte die Stelle zu einem Grundstueck, ist es ganz"
                            + " gewechselt. Die Rangobergrenze ist nicht geprueft"
                            + " worden, und Kistenshops wechseln NICHT mit - ein Shop"
                            + " des Vorbesitzers liegt danach auf fremdem Grund und ist"
                            + " fuer beide verschlossen.", NamedTextColor.GOLD);
                    case FREIGEGEBEN -> Component.text("Parzelle " + stelle + " ist"
                            + " wieder Bauland - bisher gehoerte sie "
                            + (ergebnis.vorher() == null ? "niemandem"
                                    : ergebnis.vorher()) + ".", NamedTextColor.GOLD);
                    case UNBEKANNTER_SPIELER -> Component.text("Den Namen "
                            + ergebnis.spieler() + " kennt dieser Server nicht - es ist"
                            + " nichts geaendert. Gross- und Kleinschreibung zaehlt.",
                            NamedTextColor.RED);
                    case KEIN_BESITZ -> Component.text("Bei " + stelle + " ist keine"
                            + " Parzelle - Weg, Mauer oder vor der Stadt.",
                            NamedTextColor.RED);
                    case NOCH_FREI -> Component.text("Parzelle " + stelle + " gehoert"
                            + " noch niemandem - es gibt nichts umzuschreiben.",
                            NamedTextColor.GRAY);
                    case TEIL_EINER_GRUPPE -> Component.text("Parzelle " + stelle
                            + " gehoert zu einem Grundstueck - freigeben laesst sich"
                            + " daraus kein einzelnes Feld, denn das loeschte das ganze"
                            + " Grundstueck. Uebertragen geht, dann wechselt es ganz.",
                            NamedTextColor.RED);
                    case SCHON_EIGENTUEMER -> Component.text(ergebnis.spieler()
                            + " ist schon Eigentuemer von " + stelle + " - es ist"
                            + " nichts geaendert.", NamedTextColor.GRAY);
                };
            }
        };
    }

    /**
     * Der Zweig von {@code /freimarken geld} - Regel in {@link Betreibergeld}, Text hier.
     *
     * <p>Dieselbe Trennung wie bei den vier Verwaltungszweigen: Die Entscheidung steht in
     * einer Klasse ohne Bukkit und ohne Text, damit jede Lage in einer Probe ohne Server
     * laeuft; hier steht nur, was der Betreiber liest, und das bleibt im Programm.
     *
     * <p>Die Antwort kommt aus einem Schalter-AUSDRUCK: Nur der muss vollstaendig sein.
     * Eine Anweisung ohne {@code default} liesse einen neuen Ausgang stumm durchfallen -
     * der Betreiber bekaeme dann gar keine Zeile und hielte die Buchung fuer erledigt.
     *
     * <p>Protokolliert wird JEDER Versuch, auch der abgewiesene: Bei Geld ist das keine
     * Formalie. Wer, wann, was, und was daraus wurde - das ist der Unterschied zwischen
     * "der Betreiber hat es korrigiert" und "mir fehlen 50.000 Mark".
     */
    private BundBefehle.Geldzweig geldzweig() {
        return new BundBefehle.Geldzweig() {

            @Override
            public void tue(Audience empfaenger, String wer, String spieler,
                            long betrag, Betreibergeld.Waehrung waehrung) {
                Betreibergeld.Ergebnis ergebnis;
                try {
                    // DIE EINZIGE Stelle im Haus, die beide Kassenfelder hat - darum
                    // waehlt sie hier und nicht in Betreibergeld. Ein Schalter-AUSDRUCK
                    // ohne default: Eine neue Waehrung wird vom Uebersetzer gefragt und
                    // faellt nicht stumm auf die Mark.
                    ergebnis = switch (waehrung) {
                        case MARK -> Betreibergeld.buche(kasse,
                                Spielername::kennungAuchAbwesend, spieler, betrag);
                        case GOLDMARK -> Betreibergeld.bucheGold(goldkasse,
                                Spielername::kennungAuchAbwesend, spieler, betrag);
                    };
                } catch (RuntimeException stoerung) {
                    getLogger().log(Level.SEVERE, "Geld von Hand GESCHEITERT: " + wer
                            + " wollte " + mitGeld(waehrung, betrag) + " fuer " + spieler
                            + " buchen - die Kasse antwortet nicht.", stoerung);
                    empfaenger.sendMessage(Component.text("Die Kasse antwortet nicht -"
                            + " es ist NICHTS gebucht. Der Grund steht im"
                            + " Serverprotokoll.", NamedTextColor.RED));
                    return;
                }
                getLogger().info("Geld von Hand: " + wer + " -> " + spieler + ", "
                        + mitGeld(waehrung, betrag) + ", Ausgang " + ergebnis.ausgang()
                        + ", Stand danach " + mitGeld(waehrung, ergebnis.stand()) + ".");
                empfaenger.sendMessage(switch (ergebnis.ausgang()) {
                    case GUTGESCHRIEBEN -> Component.text(ergebnis.spieler() + " hat "
                            + mitGeld(waehrung, ergebnis.betrag()) + " bekommen - Stand"
                            + " jetzt " + mitGeld(waehrung, ergebnis.stand()) + ".",
                            NamedTextColor.GOLD);
                    case ABGEZOGEN -> Component.text(ergebnis.spieler() + ": "
                            + mitGeld(waehrung, ergebnis.betrag()) + " abgezogen - Stand"
                            + " jetzt " + mitGeld(waehrung, ergebnis.stand()) + ".",
                            NamedTextColor.GOLD);
                    case UNBEKANNTER_SPIELER -> Component.text("Den Namen "
                            + ergebnis.spieler() + " kennt dieser Server nicht - es ist"
                            + " nichts gebucht. Gross- und Kleinschreibung zaehlt.",
                            NamedTextColor.RED);
                    case NICHTS_ZU_BUCHEN -> Component.text(mitGeld(waehrung, 0L)
                            + " ist keine Korrektur - nichts gebucht. Ein Minus zieht"
                            + " ab.", NamedTextColor.GRAY);
                    case NICHT_GEDECKT -> Component.text(ergebnis.spieler() + " hat"
                            + " keine " + mitGeld(waehrung, ergebnis.betrag())
                            + " (Stand " + mitGeld(waehrung, ergebnis.stand())
                            + ") - es ist nichts gebucht.", NamedTextColor.RED);
                });
            }

            @Override
            public void hilfe(Audience empfaenger, Betreibergeld.Waehrung waehrung) {
                empfaenger.sendMessage(Component.text(
                        "/freimarken " + waehrung.befehlswort() + " <spieler> <betrag>"
                                + " - ein Minus zieht ab, hoechstens "
                                + mitGeld(waehrung, waehrung.hoechstens())
                                + ". Die Buchung steht danach in "
                                + waehrung.verlaufsbefehl() + ".",
                        NamedTextColor.GRAY));
            }
        };
    }

    /**
     * Ein Geldbetrag fuer den BETREIBER - das Wort an EINER Stelle.
     *
     * <p>Was ein SPIELER liest, holt sein Geldwort aus {@code waehrung.betrag} in der
     * Sprachdatei; {@code VorlageTest} 10e haelt das fest und verlangt, dass jede Stelle
     * im Programm, die "Mark" selbst schreibt, dort aufgeschrieben ist. Die Zeilen von
     * {@code /freimarken geld} gehen an den Betreiber und bleiben darum im Programm -
     * aber sie brauchen das Wort elfmal. Ueber diesen Helfer steht es einmal da, und die
     * Tafel bleibt lesbar.
     */
    private static String mitGeld(Betreibergeld.Waehrung waehrung, long betrag) {
        return switch (waehrung) {
            case MARK -> betrag + " Mark";
            case GOLDMARK -> betrag + " Goldmark";
        };
    }

    /**
     * Liest die Einstellungsdatei neu und tauscht aus, was im Betrieb austauschbar ist.
     *
     * <p>ALLES ODER NICHTS: Ist nur eine Zeile unbrauchbar, wird gar nichts uebernommen.
     * Eine halb uebernommene Preisliste ist der schlechteste Zustand - dann gelten Zahlen
     * aus zwei Staenden gegeneinander, und niemand kann sagen, welcher Preis gerade gilt.
     *
     * <p>Es wird NICHTS neu gebaut: kein Zuhoerer, kein Zeitgeber, kein Befehl, keine
     * Verbindung - nur der Inhalt einer Zelle getauscht. Deshalb gibt es auch nichts
     * abzumelden, und nichts kann doppelt dastehen. Das ist der belegte Fehler eines
     * Neuladens, das Objekte neu baut.
     *
     * <p>Der Block "nicht angefasst" steht am Ende JEDER Antwort, auch wenn sich nichts
     * geaendert hat. Er ist das Gegenmittel gegen das haeufigste Fehlerbild eines
     * Neuladens: Der Befehl meldet Erfolg, und ein Teil wirkt erst nach dem Neustart.
     */
    private void neulade(Audience empfaenger, String wer) {
        // ZWEI Bloecke, jeder fuer sich alles-oder-nichts: Eine Klage in der Sprache darf
        // eine Preisaenderung nicht blockieren, und umgekehrt. Vorher kehrte diese Methode
        // bei einer unbrauchbaren Wirtschaftszeile zurueck - stuende der Sprachteil
        // darunter, waere eine getippte Sprachzeile von einem Preisvertipper mitgerissen
        // worden, der sie nicht betrifft.
        wirtschaftAntwort(empfaenger, wer);
        sprachAntwort(empfaenger, wer);
    }

    /**
     * Der Sprachteil der Antwort auf {@code /freimarken neuladen}.
     *
     * <p>Gebaut wird sie in {@link Sprachstand#antwort} - ohne {@code Player} und ohne
     * Server, damit eine Probe nachsehen kann, WAS in der Antwort steht, und nicht bloss,
     * dass etwas geschieht. Dasselbe Muster macht
     * {@code SpielzeitBefehle.sende(Audience, String, List)} (:194-200) schon vor.
     */
    private void sprachAntwort(Audience empfaenger, String wer) {
        var gelesen = lieseSprache();
        gelesen.klagen().forEach(klage ->
                getLogger().severe("neuladen von " + wer + " abgelehnt: " + klage));
        gelesen.bericht().forEach(zeile ->
                getLogger().info("neuladen von " + wer + ": " + zeile));
        sprachstand.antwort(gelesen).forEach(empfaenger::sendMessage);
    }

    /** Der Wirtschaftsteil - unveraendert, nur in eine eigene Methode gezogen. */
    private void wirtschaftAntwort(Audience empfaenger, String wer) {
        // reloadConfig legt ein NEUES FileConfiguration-Objekt von der Platte an. Genau
        // deshalb haelt dieses Plugin nirgends ein Config-Feld: Es wuerde danach auf die
        // verworfene Instanz zeigen. Angefasst wird die Datei an genau zwei Stellen -
        // hier und in onEnable.
        // Zuerst die Datei selbst: reloadConfig() verschluckt einen Parse-Fehler und
        // liefert danach die Vorgaben aus der Jar. Ohne diesen Blick waere ein Tabulator
        // in der Datei ein gruenes "Uebernommen: grundpreis 200000 -> 25000" - ohne eine
        // einzige Klage, weil jeder Pfad einen gueltigen Wert liest.
        String lesefehler = Wirtschaftsliste.lesefehler(getDataFolder());
        if (lesefehler != null) {
            lehneAb(empfaenger, wer, List.of(lesefehler));
            return;
        }
        reloadConfig();
        // Das LAUFENDE Verzeichnis und nicht das der Datei: Gegen die Datei geprueft
        // ginge hoechstens-grundberufe: 7 durch, sobald die Datei sieben Berufe nennt -
        // der laufende Server kennt sechs, und der siebte Platz ist nie fuellbar.
        var gelesen = Wirtschaftsliste.ausKonfiguration(getConfig(), berufe);

        if (!gelesen.klagen().isEmpty()) {
            lehneAb(empfaenger, wer, gelesen.klagen());
            return;
        }

        var uebernahme = wirtschaft.uebernimm(gelesen.werte());
        if (uebernahme.geaendert().isEmpty()) {
            empfaenger.sendMessage(Component.text(
                    "Gelesen - an den Wirtschaftswerten hat sich nichts geaendert.",
                    NamedTextColor.GRAY));
        } else {
            empfaenger.sendMessage(Component.text("Uebernommen:", NamedTextColor.GREEN));
            for (String zeile : uebernahme.geaendert()) {
                empfaenger.sendMessage(Component.text("  " + zeile, NamedTextColor.GREEN));
                getLogger().info("neuladen von " + wer + ": " + zeile);
            }
        }
        for (String zeile : gelesen.bericht()) {
            empfaenger.sendMessage(Component.text("  " + zeile, NamedTextColor.DARK_GRAY));
            getLogger().info("neuladen von " + wer + ": " + zeile);
        }
        nenneNichtAngefasst(empfaenger, uebernahme.nichtAngefasst());
    }

    /**
     * Die Antwort, wenn nichts uebernommen wird - fuer jeden Grund dieselbe.
     *
     * <p>Zwei Gruende fuehren hierher: eine unlesbare Datei und eine unbrauchbare Zeile.
     * Beide enden gleich, und genau deshalb steht die Antwort an einer Stelle: Sie nennt
     * den Grund, dann was unveraendert weiter gilt, dann was ein Neuladen ohnehin nicht
     * anfasst. Ein Betreiber, der einen Preis nicht wiederfindet, soll nicht raten
     * muessen, welcher Stand gerade gilt.
     */
    private void lehneAb(Audience empfaenger, String wer, List<String> gruende) {
        empfaenger.sendMessage(Component.text("Nichts uebernommen - " + gruende.size()
                + " Zeile(n) stimmen nicht:", NamedTextColor.RED));
        for (String grund : gruende) {
            empfaenger.sendMessage(Component.text("  " + grund, NamedTextColor.RED));
            getLogger().severe("neuladen von " + wer + " abgelehnt: " + grund);
        }
        empfaenger.sendMessage(Component.text("Es gilt unveraendert weiter: "
                + wirtschaft.werte().preise().alsText(VIER_STUFEN), NamedTextColor.GRAY));
        nenneNichtAngefasst(empfaenger, wirtschaft.nichtAngefasst(null));
    }

    /** Wie viele Preisstufen eine Antwort im Klartext nennt. */
    private static final int VIER_STUFEN = 4;

    /**
     * Was die Messstelle gerade sieht - der Bericht aus Teil G.
     *
     * <p>Die Haelfte von Teil G, die "Erkennung" heisst: Die andere Haelfte ist die zweite
     * Zahl in {@code /playtime}. Hier steht, WARUM sie so aussieht, wie sie aussieht - je
     * anwesender Spieler die Lebenszeichen des laufenden Taktschlages.
     *
     * <p>Diese Zeilen bleiben im PROGRAMM und nicht in der Sprachdatei, wie die anderen
     * Verwaltungszweige von {@code /freimarken}: Sie gehen an einen Betreiber mit dem
     * Recht und nie an einen Spieler.
     *
     * <p>Das BEWEGEN fehlt in jeder Spielerzeile, und der Satz sagt es: Es entsteht erst
     * beim Abrechnen des Schlages aus dem Abstand zweier Orte. Eine Zeile, die es stumm
     * wegliesse, behauptete "dieser Spieler hat sich nicht bewegt" - und das weiss diese
     * Stelle nicht.
     */
    private void zeigeMessstelle(Audience empfaenger) {
        Messwerte werte = wirtschaft.werte().messung();
        empfaenger.sendMessage(Component.text("Messstelle: " + werte.verlangteArten()
                + " von " + werte.arten().size() + " Arten machen einen Taktschlag"
                + " taetig, Bewegen ab " + werte.mindestStrecke() + " Bloecken.",
                NamedTextColor.GRAY));
        empfaenger.sendMessage(Component.text("  Es zaehlen: "
                + Messliste.nenne(werte.arten()) + ". Gemessen wird neben der"
                + " Anwesenheit und greift in nichts ein.", NamedTextColor.DARK_GRAY));
        if (messstelle == null) {
            // Erreichbar: Der Befehlsbaum steht, bevor richteSpielzeitEin gelaufen ist,
            // wenn eine eingetragene Datenbank fehlt und der Start abbricht.
            empfaenger.sendMessage(Component.text("  Die Messstelle laeuft nicht - der"
                    + " Start ist nicht bis zum Spielzeittakt gekommen.",
                    NamedTextColor.RED));
            return;
        }
        var anwesende = getServer().getOnlinePlayers();
        if (anwesende.isEmpty()) {
            empfaenger.sendMessage(Component.text("  Niemand ist anwesend.",
                    NamedTextColor.DARK_GRAY));
            return;
        }
        for (Player spieler : anwesende) {
            var gegeben = messstelle.bericht(spieler.getUniqueId());
            empfaenger.sendMessage(Component.text("  " + spieler.getName() + ": "
                    + messstelle.zaehlendeBisher(spieler.getUniqueId()) + " von "
                    + werte.verlangteArten() + " im laufenden Schlag"
                    + (gegeben.isEmpty() ? " - noch nichts"
                            : " - " + Messliste.nenne(gegeben)),
                    NamedTextColor.DARK_GRAY));
        }
        empfaenger.sendMessage(Component.text("  Das Bewegen steht hier nicht: Es"
                + " entsteht erst beim Abrechnen des Schlages aus dem Abstand zweier"
                + " Orte, kann also jede dieser Zahlen noch um eins erhoehen.",
                NamedTextColor.DARK_GRAY));
    }

    /**
     * Was ein Neuladen ausdruecklich NICHT anfasst - mit einem Grund je Gruppe.
     *
     * <p>Hier stehen die Gruppen, die das Plugin kennt und der {@link Wirtschaftsstand}
     * nicht: Zugangsdaten, Welten und die Zahlen, die im Programm bleiben. Jede Zeile
     * nennt, was GILT - nicht, was in der Datei steht. Genau dort sitzt das
     * gefaehrlichste Missverstaendnis: Ein Betreiber traegt die Datenbank nach und
     * erwartet, dass sie greift.
     */
    private void nenneNichtAngefasst(Audience empfaenger, List<String> ausDemStand) {
        empfaenger.sendMessage(Component.text("Nur beim Start gelesen - das aendert kein"
                + " Neuladen:", NamedTextColor.GRAY));
        for (String zeile : ausDemStand) {
            empfaenger.sendMessage(Component.text("  " + zeile, NamedTextColor.DARK_GRAY));
        }
        empfaenger.sendMessage(Component.text("  Ablage "
                + (fluechtig ? "FLUECHTIG - datenbank.url wird nur beim Start gelesen,"
                        + " eine nachgetragene Datenbank greift erst nach einem Neustart"
                        : "MariaDB - an der Verbindung haengen Lager, Kasse und Besitzbuch"),
                NamedTextColor.DARK_GRAY));
        empfaenger.sendMessage(Component.text("  welten: " + welten.size()
                + " geladen, Marktstadt " + (long) grenze.kante() + " Bloecke, Parzellen "
                + grenze.von() + " bis " + grenze.bis() + " - Welten entstehen einmal,"
                + " und die Zahlenbereiche von /freimarken parzelle stecken im"
                + " Befehlsbaum", NamedTextColor.DARK_GRAY));
        empfaenger.sendMessage(Component.text("  laeden: "
                + ladenverzeichnis.anzahl() + " Einheiten in " + ladenwelt
                + " - ein Neuladen verschiebt keinen Laden, sonst stuende die Truhe eines"
                + " Mieters ploetzlich beim Nachbarn", NamedTextColor.DARK_GRAY));
        empfaenger.sendMessage(Component.text("  reisen.ankunftswelt - setzt die"
                + " Respawn-Welt dauerhaft in der level.dat der Hauptwelt",
                NamedTextColor.DARK_GRAY));
        // Der Spielzeittakt ist der eine wiederkehrende Zeitgeber des Plugins. Die beiden
        // Zeilen zu zeit.zone und zeit.takt-sekunden kommen aus dem Wirtschaftsstand; was
        // der nicht weiss, ist, dass hier ein Zeitgeber steht. Ein Neuladen fasst ihn
        // ausdruecklich NICHT an - sonst liefen nach zwei Neuladen drei davon.
        empfaenger.sendMessage(Component.text("  Der Spielzeittakt steht seit dem Start -"
                + " ein Neuladen stellt ihn nicht neu, sonst schriebe dieselbe Spielzeit"
                + " mehrfach", NamedTextColor.DARK_GRAY));
        empfaenger.sendMessage(Component.text("  Im Programm, mit Begruendung:"
                + " Parzellengroesse " + plan.parzellenKante() + ", Wegbreite " + plan.wegBreite()
                + ", Bodenhoehe " + Stadtboden.BODEN + " (daran haengen der Generator und"
                + " die bestehenden Daten), Deckel der Verdopplung "
                + Preisstaffel.MAX_VERDOPPLUNGEN + " (Arithmetik gegen den Ueberlauf)",
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
        var rueckweg = new Rueckweg();
        var offeneAnfragen = new Anfragen(ANFRAGE_GUELTIG);
        aufwaermzeit = new Aufwaermzeit(this, sprache);
        getServer().getPluginManager().registerEvents(aufwaermzeit, this);
        reise = new Reise(getLogger(), rueckweg, aufwaermzeit, sprache);
        warpBefehle = new WarpBefehle(warpverzeichnis, welten, reise, sprache,
                freistadtbuch, welten.get(freistadtwelt));
        rueckwegBefehle = new RueckwegBefehle(rueckweg, reise, sprache);
        anfragenBefehle = new AnfragenBefehle(offeneAnfragen, reise, sprache);
        getServer().getPluginManager().registerEvents(
                new Rueckwegwache(rueckweg, offeneAnfragen), this);

        Set<String> eigene = welten.values().stream()
                .map(w -> w.key().asString())
                .collect(java.util.stream.Collectors.toSet());
        getServer().getPluginManager().registerEvents(
                new Portalwache(eigene, sprache), this);

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
                new Ankunftswache(ankunftswelt, nachSchluessel, kasse, getLogger(),
                        sprache), this);

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
        World ladenweltOder = welten.get(ladenwelt);
        if (ladenverzeichnis.leer()) {
            getLogger().info("Keine Ladeneinheiten eingetragen - die Markthalle wartet.");
        } else if (ladenweltOder == null) {
            getLogger().severe("Die Ladenwelt \"" + ladenwelt + "\" ist nicht geladen - die "
                    + ladenverzeichnis.anzahl() + " Ladeneinheiten sind nicht zu pruefen.");
        } else {
            pruefeLadeneinheiten(ladenweltOder);
        }
    }

    /**
     * Die Welt der Freistaedte und das Buch darueber.
     *
     * <p>Fehlt die Welt, bleibt das Buch {@code null} und {@code /freistadt} sagt es -
     * der Server laeuft weiter. Das ist die Form der Ladenwelt und nicht die der
     * Marktstadt: Jene kostet den Start, weil aus ihr die Parzellennummern kommen.
     */
    private void richteFreistaedteEin() {
        World welt = welten.get(freistadtwelt);
        if (welt == null) {
            getLogger().severe("freistaedte.welt ist " + freistadtwelt + ", und diese"
                    + " Welt steht nicht im Abschnitt welten: - /freistadt bleibt ohne"
                    + " Wirkung. Trag sie dort mit art: stadtfelder ein.");
            return;
        }
        Weltangabe angabe = angaben.get(freistadtwelt);
        if (angabe == null || angabe.art() != Weltangabe.Art.STADTFELDER) {
            getLogger().severe("freistaedte.welt ist " + freistadtwelt + ", aber dort"
                    + " steht nicht art: stadtfelder - /freistadt bleibt ohne Wirkung."
                    + " In einer Welt ohne Stadtfelder gaebe es keine Felder zu"
                    + " vergeben.");
            return;
        }
        // Die Grenze aus DERSELBEN Angabe, aus der die Welt entstand - und ueber
        // Feldgrenze und nicht ueber die Zahl aus der Datei: Jene ist ein WUNSCH, aus
        // 6000 werden 20 Felder je Achse. Wer das ueberspringt, vergibt eine Stadt,
        // deren Mitte vor der eigenen Weltgrenze liegt.
        Feldgrenze grenze = Feldgrenze.ausKante(stadtfelder.plan(),
                Feldgrenze.wirksameKante(stadtfelder.plan(), angabe.kante(),
                        welt.getWorldBorder().getSize(), false));
        freistadtbuch = new Freistadtbuch(grenze, lager, kasse, stadtkasse,
                warpverzeichnis, wirtschaft);
        // DER BESUCHSSTEMPEL [Plan 16, Teil E4], und er entsteht HIER und nicht in
        // richteSpielzeitEin: Dort gibt es das Freistadtbuch nicht, und ein zweites
        // waere eine zweite Rechnung darueber, wo ein Feld liegt. Der Minutentakt
        // entsteht spaeter (richteSpielzeitEin laeuft nach richteFreistaedteEin) und
        // bekommt ihn als Mitfahrer.
        besuchslauf = new Besuchslauf(freistadtbuch, uhr, wirtschaft, sprache,
                getLogger());
        // DER VERFALL [Plan 16, Teil E5]. Er ist Mitfahrer UND Zuhoerer: Der Lauf
        // haengt am Minutentakt (richteSpielzeitEin, spaeter), die WARNUNG am Beitritt -
        // und die muss dort haengen, weil ein abwesender Stadtbesitzer sonst nie
        // erfaehrt, dass seine Stadt ablaeuft.
        verfallslauf = new Verfallslauf(freistadtbuch, weltschluessel(freistadtwelt),
                uhr, wirtschaft, sprache, getLogger());
        getServer().getPluginManager().registerEvents(verfallslauf, this);
        // Das Verzeichnis der Stadtnamen fuellen, und die Zahl daraus in die
        // Startzeile: Der Vorschlagsgeber von /warp freistadt <name> liest es bei
        // jedem Tastendruck, und ein leeres Verzeichnis schluege nie etwas vor.
        // Die Zeile ist zugleich der Beleg, dass gefuellt wurde.
        //
        // GEFANGEN, nach dem Vorbild von richteShopsEin: Das ist der erste Griff in
        // die Ablage NACH dem Weltenbau, und der dauert bei einer neuen Welt Minuten.
        // Faellt die Datenbank in dieser Zeit aus, riss die Ausnahme bisher den ganzen
        // onEnable ab - also auch die Reisen, die Spielzeit und ALLE Befehle, und die
        // Marktstadt staende geladen und ohne Schutzwache da. Das Verzeichnis ist
        // nach seinem eigenen Javadoc das Harmloseste dieses Bereichs: Es traegt
        // Vorschlaege und keine Taten. Der Preis eines Fangs ist darum ein leeres
        // Vorschlagsfenster, waehrend /warp freistadt <name> weiter die Ablage fragt
        // und ankommt.
        int gegruendet;
        try {
            gegruendet = freistadtbuch.erneuereVerzeichnis(welt.key().asString());
        } catch (RuntimeException fehler) {
            getLogger().log(java.util.logging.Level.SEVERE, "Die Freistaedte sind nicht"
                    + " zu lesen - das Vorschlagsfenster von /warp freistadt bleibt in"
                    + " diesem Lauf LEER. Gegruendet und angereist wird weiter: Beides"
                    + " fragt die Ablage unmittelbar. Wer den Namen seiner Stadt tippt,"
                    + " kommt an.", fehler);
            gegruendet = -1;
        }
        // DIE GRUNDSTUECKE [Teil C2] - vor der Wache, denn die fragt sie mit.
        //
        // GEFANGEN wie das Verzeichnis darueber: Faellt die Grundstuecksablage aus,
        // laeuft der Server mit dem Schutz von Teil C1 weiter - jede Stadt ist dann als
        // Ganzes geschuetzt, nur die Grundstuecke darin nicht. Ein Abbruch waere hier
        // falsch: Ohne Grundstuecke ist eine Stadt weniger fein geschuetzt, OHNE WACHE
        // ist sie gar nicht geschuetzt.
        try {
            int zellen = grundstuecksablage.fuellen(welt.key().asString());
            grundstuecksbuch = new Grundstuecksbuch(grenze, lager, grundstuecksablage,
                    kasse, stadtkasse, wirtschaft);
            freistadtbuch.gibGrundstuecke(grundstuecksablage);
            getLogger().info("Grundstuecke: " + zellen + " in "
                    + grundstuecksablage.belegteZellen() + " Zellen, hoechstens "
                    + wirtschaft.werte().freistaedte().hoechstzellen()
                    + " Zellen je Grundstueck. In einem vergebenen Grundstueck baut nur"
                    + " sein Besitzer - auch der Stadtbesitzer nicht.");
        } catch (RuntimeException fehler) {
            getLogger().log(java.util.logging.Level.SEVERE, "Die Grundstuecke sind nicht"
                    + " zu lesen - in diesem Lauf gibt es KEINE: kein Zuschneiden, und"
                    + " in jeder Stadt baut wieder nur ihr Besitzer (auch dort, wo er"
                    + " Grundstuecke vergeben hat). Die Staedte selbst bleiben"
                    + " geschuetzt.", fehler);
            // UND DIE AUFLAGE FAELLT MIT, in BEIDE Riegel [Plan 16]. Diese Zeile sagt
            // das Freistadtbuch an zwei Stellen zu, und geschrieben war sie bis zum
            // 17.11.2026 nicht - eine Regel, die halb wirkt und davon schweigt, sieht
            // im Spiel aus wie eine kaputte. Eigenes severe und kein Anhaengsel an die
            // Zeile darueber: Wer "Grundstuecke" liest, denkt an Zuschnitte und nicht
            // an Teleporter.
            getLogger().severe("Und damit faellt die AUFLAGE aus - in beide Richtungen"
                    + " durchlassend: Buergerschaft ist aus den Grundstuecken"
                    + " abgeleitet, also kann sie in diesem Lauf keine Stadt erfuellen."
                    + " Jeder kommt per /warp freistadt in jede Stadt, und in jeder"
                    + " Stadt entstehen neue Kistenshops. Eine Wirtschaftsregel, die"
                    + " ausfaellt, darf nicht den Zugang kosten - aber sie soll es"
                    + " sagen.");
            grundstuecksbuch = null;
        }

        // Die Wache unmittelbar nach dem Buch, und aus DERSELBEN Instanz: Zwei Buecher
        // waeren zwei Rechnungen darueber, wo ein Feld liegt - und die eine entschiede,
        // wem es gehoert, die andere, wer darauf bauen darf.
        stadtwache = new Stadtwache(freistadtbuch, weltschluessel(freistadtwelt),
                sprache, getLogger());
        getLogger().info("Stadtschutz aktiv in " + welt.key().asString()
                + " - in einem Stadtfeld baut nur, wem die Stadt gehoert oder wer dort"
                + " Mitbauer ist. Zwischen den Feldern baut NIEMAND, auch der Nachbar"
                + " nicht: Ein Block in der Leere ist der erste Block einer Bruecke.");
        getLogger().info("Freistaedte: Welt " + welt.key().asString() + ", Felder "
                + grenze.von() + ".." + grenze.bis() + " je Achse, bis zu "
                + grenze.staedteZahl() + " Staedte, davon "
                + (gegruendet < 0 ? "UNBEKANNT VIELE (die Ablage antwortete nicht)"
                        : gegruendet + " gegruendet") + ".");
    }

    /**
     * Die Welt der Meerinseln und das Buch darueber [Schritt 10].
     *
     * <p>Dieselbe Form wie {@link #richteFreistaedteEin()}: Fehlt die Welt, bleibt das
     * Buch {@code null} und {@code /werder} sagt es - der Server laeuft weiter. Die
     * Marktstadt kostet den Start, weil aus ihr die Parzellennummern kommen; aus
     * Hafenwerder kommt nichts, was der Rest des Spiels braucht.
     *
     * <p>Zwei Klagen und nicht eine: Eine fehlende Welt ist ein anderer Fehler als eine
     * Welt mit falscher Art, und der Betreiber muss Verschiedenes tun.
     */
    private void richteInselnEin() {
        World welt = welten.get(inselwelt);
        if (welt == null) {
            getLogger().severe("inseln.welt ist " + inselwelt + ", und diese Welt steht"
                    + " nicht im Abschnitt welten: - /werder bleibt ohne Wirkung. Trag"
                    + " sie dort mit art: inseln ein.");
            return;
        }
        Weltangabe angabe = angaben.get(inselwelt);
        if (angabe == null || angabe.art() != Weltangabe.Art.INSELN) {
            getLogger().severe("inseln.welt ist " + inselwelt + ", aber dort steht nicht"
                    + " art: inseln - /werder bleibt ohne Wirkung. In einer Welt ohne"
                    + " Inselfelder gaebe es keine Insel zu nehmen.");
            return;
        }
        // Die Grenze aus DERSELBEN Angabe, aus der die Welt entstand - und ueber
        // Inselgrenze und nicht ueber die Zahl aus der Datei: Jene ist ein WUNSCH, aus
        // 5000 werden 18 Felder je Achse. Wer das ueberspringt, vergibt eine Insel,
        // deren Mitte vor der eigenen Weltgrenze liegt.
        Inselgrenze grenze = Inselgrenze.ausKante(inseln.plan(),
                Inselgrenze.wirksameKante(inseln.plan(), angabe.kante(),
                        welt.getWorldBorder().getSize(), false));
        inselbuch = new Inselbuch(grenze, lager, kasse, wirtschaft);
        // Die Wache unmittelbar nach dem Buch, und aus DERSELBEN Instanz: Zwei Buecher
        // waeren zwei Rechnungen darueber, wo ein Feld liegt - und die eine entschiede,
        // wem es gehoert, die andere, wer darauf bauen darf.
        inselwache = new Inselwache(inselbuch, weltschluessel(inselwelt), sprache,
                getLogger());
        getLogger().info("Meerinseln: Welt " + welt.key().asString() + ", Felder "
                + grenze.von() + ".." + grenze.bis() + " je Achse, bis zu "
                + grenze.inselnZahl() + " Inseln zu " + wirtschaft.werte().inseln()
                .preis() + " Mark. Feld 0/0 ist die Ankunftsinsel und bleibt frei.");
        getLogger().info("Inselschutz aktiv in " + welt.key().asString()
                + " - auf einer Insel baut nur, wem sie gehoert oder wer dort Mitbauer"
                + " ist. Im offenen Meer baut NIEMAND, auch der Nachbar nicht: Es ist"
                + " das oeffentliche Wasser des Archipels.");
        richteAngelnEin(welt);
    }

    /**
     * Setzt die Platzhalterbauten Altfurts, soweit sie fehlen [Plan 2.1, 23.11.2026].
     *
     * <p><b>Nur was FEHLT</b>: Gefragt wird je Bau EIN Block - die Wandecke eine Ebene
     * ueber dem Boden. Dieselbe Linie wie bei der Ankunftsplattform in Wolkenwerder, und
     * aus demselben Grund: Ein Start, der jedes Mal alles neu setzt, raeumte die
     * Einrichtung des Betreibers ab.
     *
     * <p>Berichtet wird in EINER Zeile und nicht je Haus: Neun Zeilen, von denen acht
     * "steht schon" sagen, sind acht Zeilen, die niemand liest. Nur wenn wirklich gesetzt
     * wurde, nennt die Meldung die Namen und die Blockzahl.
     *
     * <p>Fehlt die Welt, gibt es eine Klage und keinen Wurf: Ein Server, dessen Altfurt
     * nicht geladen ist, soll starten - er hat dann nur kein Altfurt.
     */
    private void richteAltfurtEin() {
        if (platzhalterbauten.leer() && stadtplan == null) {
            return;
        }
        World welt = welten.get(bautenwelt);
        if (welt == null) {
            getLogger().severe("Die Platzhalterbauten liegen in \"" + bautenwelt
                    + "\", und diese Welt ist nicht geladen - es wird nichts gesetzt."
                    + " Steht sie im Abschnitt welten:?");
            return;
        }
        Weltgriff griff = new Weltgriff.AufDerWelt(welt);
        Altfurtaufbau.Feingriff fein = new Altfurtaufbau.Feingriff.AufDerWelt(welt);
        Strukturgriff strukturen = new Strukturgriff.AufDerWelt(welt,
                getServer().getStructureManager(),
                new java.io.File(getDataFolder(), Strukturgriff.ORDNER));
        // DIE STADT ZUERST: Ihr Pflaster schreibt die Bodenebene, und ein Haus
        // steht darauf. Wer erst die Haeuser setzte, schnitte ihnen mit dem
        // Pflaster die unterste Ebene weg.
        baueStadt(griff, fein, strukturen);
        List<String> gesetzteNamen = new ArrayList<>();
        List<String> ausDatei = new ArrayList<>();
        long bloecke = 0L;
        for (Platzhalterbau bau : platzhalterbauten.alle()) {
            if (altfurtaufbau.stehtSchon(bau, griff)) {
                continue;
            }
            Altfurtaufbau.Ergebnis ergebnis = altfurtaufbau.baue(bau, griff, fein,
                    schildzeilen(bau), strukturen);
            if (ergebnis.klage() != null) {
                getLogger().severe(ergebnis.klage());
            }
            bloecke += ergebnis.bloecke();
            gesetzteNamen.add(bau.schluessel());
            if (ergebnis.weg() == Altfurtaufbau.Weg.STRUKTUR) {
                ausDatei.add(bau.schluessel());
            }
        }
        if (gesetzteNamen.isEmpty()) {
            getLogger().info("Altfurt steht: " + platzhalterbauten.anzahl()
                    + " Platzhalterbauten, nichts zu setzen.");
            return;
        }
        getLogger().warning("Altfurt gebaut: " + gesetzteNamen.size() + " von "
                + platzhalterbauten.anzahl() + " Platzhalterbauten gesetzt ("
                + String.join(", ", gesetzteNamen) + "), " + bloecke + " Bloecke - "
                + altfurtaufbau.stoffe().bericht() + ". Wer eines neu setzen will, nimmt"
                + " seine Wandecke eine Ebene ueber dem Boden weg."
                + (ausDatei.isEmpty()
                        ? " KEINES kam aus einer Datei - alle sind gerechnet. Wer eines"
                                + " von Hand baut, sichert es mit /freimarken altfurt"
                                + " sichern; danach wird es gesetzt statt gerechnet."
                        : " Aus einer Datei gesetzt: " + String.join(", ", ausDatei)
                                + "."));
    }

    /**
     * Pflastert Altfurt und setzt die gerechneten Zeilenhaeuser [23.11.2026].
     *
     * <p><b>Das ist die Stadt</b>, um die es dem Betreiber ging: "es soll eine ganze
     * stadt werden, mit strassen usw. also die haeuser auch verbinden, statt einfach
     * nur hinzustellen." Strassen sind Boden, verbundene Haeuser sind Zeilen ohne
     * Luecke - beides rechnet {@link Stadtplan}, beides setzt {@link Altfurtaufbau}.
     *
     * <p><b>Nur was FEHLT</b>, wie bei den eingetragenen Bauten: Das Pflaster wird an
     * EINEM Block erkannt (der Mitte des Marktes), jedes Haus an seiner Wandecke.
     * Ein Start, der jedes Mal zweihunderttausend Bloecke neu setzt, raeumte jede
     * Einrichtung ab - und brauchte jedes Mal die Zeit dafuer.
     *
     * <p>Berichtet wird in EINER Zeile, und sie nennt Zahlen statt Namen:
     * Zweihundert Hausnamen liest niemand.
     */
    private void baueStadt(Weltgriff griff, Altfurtaufbau.Feingriff fein,
            Strukturgriff strukturen) {
        if (stadtplan == null) {
            return;
        }
        // EIN GERECHNETES HAUS, DAS HANDARBEIT TRIFFT, WIRD NICHT GESETZT.
        // Stadtplan.KERN_RING haelt die Stadt von den neun Bauten fern, aber das ist
        // eine Zusage ueber die config.yml, die niemand prueft: Wer einen zehnten
        // Bau weiter draussen eintraegt oder eine Ladeneinheit dorthin legt, bekaeme
        // ein Haus darueber. Lieber ein Loch in der Zeile als ein uebermauerter
        // Laden - und die Meldung sagt es.
        List<Platzhalterbau> zuBauen = new ArrayList<>();
        List<String> imWeg = new ArrayList<>();
        for (Platzhalterbau bau : stadtplan.haeuser()) {
            boolean trifft = false;
            for (Quader fest : unberuehrteQuader) {
                if (bau.bereich().schneidet(fest)) {
                    trifft = true;
                    break;
                }
            }
            if (trifft) {
                imWeg.add(bau.schluessel());
            } else {
                zuBauen.add(bau);
            }
        }
        if (!imWeg.isEmpty()) {
            getLogger().severe(imWeg.size() + " gerechnete Zeilenhaeuser wurden NICHT"
                    + " gesetzt, weil dort Handarbeit steht (eingetragener Bau,"
                    + " Ladeneinheit oder Tor): " + String.join(", ", imWeg)
                    + ". Alles innerhalb von Ring " + Stadtplan.KERN_RING + " um "
                    + Stadtplan.MITTE_X + "/" + Stadtplan.MITTE_Z + " ist fuer"
                    + " Handarbeit frei - was weiter draussen liegt, kostet ein"
                    + " Haus.");
        }
        // DIE HAEUSER SCHUETZEN SICH SELBST GEGEN DAS PFLASTER: Das Pflaster schreibt
        // die Bodenebene, und die unterste Hausebene IST der Fussboden. Beim ersten
        // Bau faellt das nicht auf, weil die Haeuser danach kommen - wer aber neu
        // pflastern laesst, bekaeme Erde in jedem Zimmer, und stehtSchon baut die
        // Haeuser danach nicht neu.
        List<Quader> schutz = new ArrayList<>(unberuehrteQuader);
        zuBauen.forEach(bau -> schutz.add(bau.bereich()));
        long bloecke = 0L;
        String pflasterzeile = "schon gepflastert";
        if (!altfurtaufbau.istGepflastert(stadtplan, pflasterstoffe, griff)) {
            Altfurtaufbau.Pflasterung raus = altfurtaufbau.pflastere(stadtplan,
                    pflasterstoffe, griff, schutz);
            bloecke += raus.bloecke();
            pflasterzeile = raus.bloecke() + " Bloecke Pflaster und Mauer, "
                    + raus.uebersprungen() + " Bloecke um Haeuser und Handarbeit herum";
        }
        int gesetzt = 0;
        int ausDatei = 0;
        for (Platzhalterbau bau : zuBauen) {
            if (altfurtaufbau.stehtSchon(bau, griff)) {
                continue;
            }
            Altfurtaufbau.Ergebnis ergebnis = altfurtaufbau.baue(bau, griff, fein,
                    List.of(), strukturen);
            if (ergebnis.klage() != null) {
                getLogger().severe(ergebnis.klage());
            }
            bloecke += ergebnis.bloecke();
            gesetzt++;
            if (ergebnis.weg() == Altfurtaufbau.Weg.STRUKTUR) {
                ausDatei++;
            }
        }
        meldeAltereStadt(griff);
        if (gesetzt == 0 && bloecke == 0L) {
            getLogger().info("Die Stadt steht: " + zuBauen.size()
                    + " Zeilenhaeuser, nichts zu setzen.");
            return;
        }
        getLogger().warning("Altfurt gebaut: " + pflasterzeile + ", " + gesetzt
                + " von " + zuBauen.size() + " Zeilenhaeusern gesetzt"
                + (ausDatei == 0 ? " (alle gerechnet)"
                        : " (" + ausDatei + " aus einer Datei)")
                + ", zusammen " + bloecke + " Bloecke. "
                + pflasterstoffe.bericht() + ". Wer ein Haus neu setzen will, nimmt"
                + " seine Wandecke eine Ebene ueber dem Boden weg; wer neu pflastern"
                + " will, den Block in der Mitte des Marktes.");
    }

    /**
     * Meldet eine AELTERE, GROESSERE Stadt, deren Mauer noch mitten in der heutigen
     * steht [23.11.2026].
     *
     * <p><b>Eine Einstellung, die nur halb wirkt, muss es sagen</b>: Wer
     * {@code bauten.stadt.quartiere} verkleinert, bekommt die neuen Baender nicht
     * gepflastert (der Block in der Mitte des Marktes liegt schon) und die alte
     * Mauer bleibt stehen - mitten in der Stadt, quer durch die Gassen. Das Pflaster
     * koennte sie nicht abtragen: Es schreibt nur die Bodenebene, die Mauer steht
     * fuenf Ebenen darueber. Also wird es GEMELDET, mit dem Ring, auf dem sie steht.
     *
     * <p>Gefragt wird nicht die ganze Flaeche, sondern je moegliche Groesse EIN
     * Block: Die Mauer einer Stadt mit {@code q} Quartieren steht auf einem
     * gerechneten Ring, und davon gibt es hoechstens
     * {@link Stadtliste#HOECHSTENS_QUARTIERE} viele. Neun Blockabfragen statt
     * dreihunderttausend.
     */
    private void meldeAltereStadt(Weltgriff griff) {
        Material mauerstoff = pflasterstoffe.block(Stadtplan.Lage.MAUER);
        if (mauerstoff == null) {
            return;
        }
        List<String> gefunden = new ArrayList<>();
        for (int q = stadtplan.quartiere() + 1; q <= Stadtliste.HOECHSTENS_QUARTIERE;
                q++) {
            Stadtplan groesser = new Stadtplan(q);
            int ring = groesser.mauerVon();
            // NEBEN der Achse: auf ihr liegt das Stadttor, und dort steht nie Mauer.
            if (griff.bei(Stadtplan.MITTE_X + ring, Stadtplan.BODEN_Y + 2,
                    Stadtplan.MITTE_Z + 8) == mauerstoff) {
                gefunden.add(q + " Quartiere (Mauer auf Ring " + ring + ")");
            }
        }
        if (gefunden.isEmpty()) {
            return;
        }
        getLogger().severe("In Altfurt steht noch die Mauer einer GROESSEREN Stadt: "
                + String.join(", ", gefunden) + ". Heute sind "
                + stadtplan.quartiere() + " Quartiere eingestellt, die Mauer steht"
                + " damit mitten in der Stadt. Das Pflaster kann sie nicht abtragen -"
                + " es schreibt nur die Bodenebene. Trag die alte Zahl wieder ein"
                + " oder raeume die Mauer ab.");
    }

    /**
     * Was auf dem Namensschild eines Baus steht.
     *
     * <p><b>Derselbe Satz wie der Name der Rundgangsstation</b>
     * ({@code rundgang.station.<name>.name}) und keine fuenfte offene Satzfamilie: Die
     * Bauten tragen dieselben Namen wie die Stationen - der Betreiber traegt beides unter
     * demselben Schluessel ein. Zwei Satzfamilien fuer denselben Namen waeren zwei
     * Stellen, die auseinanderlaufen.
     *
     * <p>Fehlt die Zeile, steht auf dem Schild ihr PFAD - genau wie die Station es heute
     * tut, und aus demselben Grund: Ein haesslicher Name ist der kleinere Schaden.
     */
    private List<String> schildzeilen(Platzhalterbau bau) {
        return List.of(sprache.wort(
                new Stationssatz(bau.schluessel(), Stationssatz.Teil.NAME),
                Einsatz.keine()));
    }

    /**
     * Wolkenwerder: Grenze, Buch, Wache und die Ankunftsplattform [Schritt 11].
     *
     * <p>Zeile fuer Zeile wie {@link #richteInselnEin}, mit einem Schritt mehr: Die
     * Ankunftsplattform wird GEPRUEFT und notfalls gesetzt. In jeder anderen Welt macht
     * das der Weltgenerator einmal und fuer immer; hier gibt es keinen Block, den
     * jemand nicht gesetzt hat - und ohne festen Boden am Erscheinungspunkt fuehrt
     * dorthin GAR KEINE Reise.
     */
    private void richteWolkenEin() {
        World welt = welten.get(wolkenwelt);
        if (welt == null) {
            getLogger().severe("wolken.welt ist " + wolkenwelt + ", und diese Welt steht"
                    + " nicht im Abschnitt welten: - /wolke bleibt ohne Wirkung. Trag"
                    + " sie dort mit art: wolken ein.");
            return;
        }
        Weltangabe angabe = angaben.get(wolkenwelt);
        if (angabe == null || angabe.art() != Weltangabe.Art.WOLKEN) {
            getLogger().severe("wolken.welt ist " + wolkenwelt + ", aber dort steht nicht"
                    + " art: wolken - /wolke bleibt ohne Wirkung. In einer Welt ohne"
                    + " Wolkenfelder gaebe es keine Insel zu nehmen.");
            return;
        }
        // Die Grenze aus DERSELBEN Angabe, aus der die Welt entstand - und ueber
        // Wolkengrenze und nicht ueber die Zahl aus der Datei: Jene ist ein WUNSCH, aus
        // 20000 werden 38 Felder je Achse.
        Wolkengrenze grenze = Wolkengrenze.ausKante(wolken.plan(),
                Wolkengrenze.wirksameKante(wolken.plan(), angabe.kante(),
                        welt.getWorldBorder().getSize(), false));
        wolkenbuch = new Wolkenbuch(grenze, lager, kasse, wirtschaft);
        // Die Wache unmittelbar nach dem Buch, und aus DERSELBEN Instanz - siehe
        // richteInselnEin.
        wolkenwache = new Wolkenwache(wolkenbuch, weltschluessel(wolkenwelt), sprache,
                getLogger());
        getLogger().info("Wolkeninseln: Welt " + welt.key().asString() + ", Felder "
                + grenze.von() + ".." + grenze.bis() + " je Achse, bis zu "
                + grenze.inselnZahl() + " Inseln. "
                + Wolkenliste.rechenprobe(wirtschaft.werte().wolken()));
        getLogger().info("Wolkenschutz aktiv in " + welt.key().asString()
                + " - auf einer Insel baut nur, wem sie gehoert oder wer dort Mitbauer"
                + " ist. In der LEERE baut niemand, auch der Nachbar nicht: Ein Block"
                + " darin ist der erste Block einer Bruecke.");
        if (richteErzeEin(welt)) {
            // NUR wenn die Staende gelesen sind - siehe das Feld aufgabenbuch.
            aufgabenbuch = new Aufgabenbuch(wolkenbuch, wolkenstaende, kasse, wirtschaft);
            getLogger().info("Wolkenwerder: "
                    + Aufgabenliste.rechenprobe(wirtschaft.werte().wolken().aufgaben()));
            nenneAufgabenOhneWort();
            richtePflegeEin(welt);
        }
        setzeAnkunftsplattform(welt);
    }

    /**
     * Meldet jede Herausforderung, zu der es kein Wort gibt.
     *
     * <p>BEIM START und nicht beim ersten {@code /wolke aufgaben}: Sonst faellt es mitten
     * im Spiel auf, und zwar als Pfad in der Tafel eines Spielers.
     *
     * <p><b>Die Aufgabe bleibt trotzdem</b>, anders als bei einem Beruf ohne Wort (dort
     * bleibt Freimarken aus). Der Grund steht in {@link Aufgabenname}: Die Stufe einer
     * Insel ist die Zahl ihrer erfuellten Aufgaben, und eine weggefallene machte eine
     * Stufe dauerhaft unerreichbar - und damit ein Erz, das niemand mehr bekommt.
     */
    private void nenneAufgabenOhneWort() {
        List<String> ohneWort = new ArrayList<>();
        for (String schluessel : wirtschaft.werte().wolken().aufgaben().schluessel()) {
            if (!sprachstand.buch().saetze()
                    .containsKey(new Aufgabenname(schluessel).pfad())) {
                ohneWort.add(schluessel);
            }
        }
        if (ohneWort.isEmpty()) {
            return;
        }
        getLogger().severe("Zu " + ohneWort.size() + " Herausforderung(en) gibt es kein"
                + " Wort: " + String.join(", ", ohneWort) + ". In der Sprachdatei fehlt"
                + " je Zeile " + Aufgabenname.VORSILBE + "<name> -"
                + " sprache-vorlage.yml zeigt, wie sie aussieht. Abgeben laesst sich die"
                + " Aufgabe weiter; in der Tafel steht bis dahin ihr Pfad.");
    }

    /**
     * Punkte, Rangliste und Biomwechsel - die Haende der Teile E und F.
     *
     * <p>NACH dem Fuellen der Inselstaende und nur dann, aus demselben Grund wie beim
     * Aufgabenbuch: Eine Rangliste aus einem ungefuellten Zwischenspeicher zeigte jede
     * Insel bei 0 Punkten, und eine Abklingzeit, die den letzten Zaehlzeitpunkt nicht
     * kennt, liesse jede Insel sofort wieder zaehlen.
     *
     * <p>Die WELT geht in die zwei Haende hinein und wird dort gemerkt - anders als beim
     * Aufsetzen, das sie je Aufruf sucht. Das ist tragbar, weil die Haende mit der Welt
     * entstehen: Ist sie beim Start nicht da, gibt es sie nicht, und {@code /wolke punkte}
     * sagt es. Ein Neuladen baut sie neu.
     */
    private void richtePflegeEin(World welt) {
        Punktebuch punktebuch = new Punktebuch(wolkenbuch, wolkenstaende, wirtschaft,
                uhr);
        Inselzaehlung.Taktquelle takt = (schlag, abstand) -> {
            BukkitTask aufgabe = getServer().getScheduler()
                    .runTaskTimer(this, schlag, abstand, abstand);
            return aufgabe::cancel;
        };
        Inselzaehlung zaehlung = new Inselzaehlung(punktebuch,
                Inselzaehlung.ueberBukkit(welt), takt, getLogger());
        Biomhand biomhand = new Biomhand(takt, Biomhand.ueberBukkit(welt), getLogger());
        Biombuch biombuch = new Biombuch(wolkenbuch, wolkenstaende, kasse, wirtschaft);
        wolkenpflege = new WolkeBefehle.Pflege(punktebuch, zaehlung::zaehle, biombuch,
                (insel, biom, fertig) -> biomhand.setze(
                        new de.freimarken.kern.Chunkfolge(wolkenbuch.plan(), insel.x(),
                                insel.z()),
                        biom, wirtschaft.werte().wolken().punkte().chunksJeTakt(),
                        fertig));
        getLogger().info("Wolkenwerder: "
                + Punkteliste.rechenprobe(wirtschaft.werte().wolken().punkte())
                + " Biomwechsel: "
                + (wirtschaft.werte().wolken().biompreis() == 0L
                        ? "gratis"
                        : wirtschaft.werte().wolken().biompreis() + " Mark")
                + " je Wechsel, und zwar JEDES Mal.");
    }

    /**
     * Der Kobbelsteingenerator - Staende lesen und die Wache anmelden.
     *
     * <p>AUS {@code richteWolkenEin} gerufen und nicht aus {@code onEnable}, aus demselben
     * Grund, den {@link #richteAngelnEin} nennt: Die Tabelle gilt in Wolkenwerder, und ob
     * es die Welt gibt, ist genau dort entschieden.
     *
     * <p><b>Schlaegt das Fuellen fehl, wird KEINE Wache angemeldet</b>, und das ist die
     * sichere Richtung: Ein ungefuellter Zwischenspeicher nennt jede Insel Stufe 0. Dann
     * gaebe der Generator die Ausbeute des ersten Tages - auf Inseln, die ein Jahr alt
     * sind. Lieber reiner Kobbelstein, laut gemeldet, als eine Ausbeute, die stillschweigend
     * falsch ist.
     *
     * <p>Angemeldet wird die Wache auch bei einer LEEREN Tabelle: Was sie tut, haengt am
     * Wirtschaftsstand, und der ist neuladbar.
     *
     * @return ob die Inselstaende gelesen sind. {@code false} heisst: kein Generator und
     *         auch keine Herausforderungen - beide haengen an derselben Zahl
     */
    private boolean richteErzeEin(World welt) {
        try {
            wolkenstaende.fuellen();
        } catch (RuntimeException fehler) {
            getLogger().log(java.util.logging.Level.SEVERE, "Die Inselstaende sind nicht"
                    + " zu lesen - in diesem Lauf gibt der Kobbelsteingenerator NUR"
                    + " Kobbelstein, und keine erfuellte Herausforderung wirkt auf die"
                    + " Ausbeute. Die Inseln selbst, ihr Schutz und /wolke bleiben"
                    + " unberuehrt.", fehler);
            return false;
        }
        getServer().getPluginManager().registerEvents(new Erzwache(
                weltschluessel(wolkenwelt), wirtschaft, wolkenbuch, wolkenstaende,
                marken, new java.util.Random(), getLogger()), this);
        getLogger().info("Kobbelsteingenerator aktiv in " + welt.key().asString()
                + " - " + wolkenstaende.groesse() + " Inseln mit Fortschritt gelesen. "
                + Wolkenliste.erzprobe(wirtschaft.werte().wolken().erze()));
        return true;
    }

    /**
     * Setzt die Startinsel einer gerade genommenen Insel - die Hand hinter
     * {@code WolkeBefehle.Aufsetzer}.
     *
     * <p>HIER und nicht im Befehl, weil hier die Welten liegen und weil die Bukkit-Griffe
     * ohne laufenden Server werfen: {@code new ItemStack} greift in das
     * Gegenstandsverzeichnis. Der Befehl kennt davon nur das Ergebnis - ja oder nein -,
     * und genau deshalb ist sein Zweig von einer Probe durchlaufbar.
     *
     * <p>Die Welt wird JE AUFRUF gesucht: Sie kann beim Start gefehlt haben und spaeter
     * da sein. Faellt das Setzen um, ist das Feld vergeben und leer - der Spieler liest
     * es, und das Protokoll auch.
     */
    private boolean setzeInsel(Parzelle insel) {
        World welt = welten.get(wolkenwelt);
        if (welt == null) {
            getLogger().severe("Insel " + insel.x() + "/" + insel.z() + " ist vergeben,"
                    + " aber " + wolkenwelt + " ist nicht geladen - sie steht nicht."
                    + " Mit /wolke zuruecksetzen ist es zu beheben.");
            return false;
        }
        try {
            int bloecke = wolkenaufbau.setze(insel.x(), insel.z(),
                    Wolkenplan.Insel.START, wolkenbuch.starttruhe(),
                    new Weltgriff.AufDerWelt(welt),
                    new Wolkenaufbau.Truhengriff.AufDerWelt(welt));
            getLogger().info("Wolkeninsel " + insel.x() + "/" + insel.z()
                    + " aufgesetzt: " + bloecke + " Bloecke fuer "
                    + Spielername.von(insel.eigentuemer()) + ".");
            return true;
        } catch (RuntimeException fehler) {
            getLogger().log(java.util.logging.Level.SEVERE, "Insel " + insel.x() + "/"
                    + insel.z() + " liess sich nicht aufsetzen - das Feld ist vergeben"
                    + " und leer.", fehler);
            return false;
        }
    }

    /**
     * Setzt die Ankunftsplattform, wenn sie fehlt.
     *
     * <p><b>Der Generator backt sie</b>, und darum ist das hier kein zweiter Weg zu
     * derselben Tat, sondern eine Reparatur: Ein Chunk, der erzeugt wurde, bevor dieser
     * Generator die Plattform kannte - oder einer, in dem jemand sie abgebaut hat, bevor
     * die Wache stand -, laesst {@code Ankunftspunkt} in acht Ringen keinen festen Block
     * finden. Dann sagt {@code /warp wolkenwerder} ab, das Tor im Wolkenturm tut nichts,
     * und der Start klagt bei JEDEM Mal.
     *
     * <p>Gesetzt wird nur, wenn der Mittelblock FEHLT. Ohne diese Frage schriebe jeder
     * Start 810 Bloecke in dieselbe Stelle - und ein Spieler, der gerade dort steht,
     * saehe seine Welt bei jedem Neustart flackern.
     */
    private void setzeAnkunftsplattform(World welt) {
        Wolkenplan plan = wolken.plan();
        int mitte = plan.ankunftsMitte();
        if (welt.getBlockAt(mitte, Wolkenboden.BODEN, mitte).getType() != Material.AIR) {
            return;
        }
        int bloecke = wolkenaufbau.setze(Wolkenplan.ANKUNFTSFELD,
                Wolkenplan.ANKUNFTSFELD, Wolkenplan.Insel.ANKUNFT, Map.of(),
                new Weltgriff.AufDerWelt(welt),
                new Wolkenaufbau.Truhengriff.AufDerWelt(welt));
        getLogger().warning("Die Ankunftsplattform von " + welt.key().asString()
                + " fehlte und wurde neu gesetzt (" + bloecke + " Bloecke auf Feld 0/0,"
                + " Hoehe " + Wolkenboden.BODEN + "). Ohne festen Boden am"
                + " Erscheinungspunkt fuehrt dorthin keine Reise - weder /warp noch das"
                + " Tor im Wolkenturm.");
    }

    /**
     * Das eigene Angelgut von Hafenwerder [Plan 4.1, Schritt 10].
     *
     * <p>AUS {@code richteInselnEin} gerufen und nicht aus {@code onEnable}, und das ist
     * kein Zufall: Die Tabelle gilt in der Inselwelt, und ob es die gibt, ist genau dort
     * entschieden. Ein eigener Zweig im Start braeuchte dieselbe Pruefung ein zweites
     * Mal - und die zweite waere die, die eines Tages anders urteilt.
     *
     * <p>Angemeldet wird die Wache auch bei einer LEEREN Tabelle: Was sie tut, haengt am
     * Wirtschaftsstand, und der ist neuladbar. Eine Wache, die nur bei gefuellter Tabelle
     * entsteht, waere nach einem {@code /freimarken neuladen} mit neuen Zeilen immer noch
     * nicht da.
     */
    private void richteAngelnEin(World welt) {
        angelwache = Angelwache.ueberBukkit(weltschluessel(inselwelt), wirtschaft,
                new java.util.Random(), getLogger());
        getServer().getPluginManager().registerEvents(angelwache, this);
        getLogger().info("Angelgut aktiv in " + welt.key().asString() + " - "
                + Angelliste.rechenprobe(wirtschaft.werte().angeln())
                + " Der Tausch sitzt VOR der Zahlung, also bezahlt die Lohntafel den"
                + " getauschten Fang.");
    }

    /**
     * Prueft, ob jede Ladeneinheit ueberhaupt benutzbar ist.
     *
     * <p>Noetig aus demselben Grund wie {@link #pruefeTore}: Die Quader sind Zahlen in
     * einer Datei, und das Gelaende ist es nicht. {@code Ladenliste} prueft beim Lesen
     * Groesse, Ueberschneidung, Truhe-innerhalb und Weltgrenze - alles vier an den
     * ZAHLEN. Ob an der eingetragenen Stelle wirklich eine Truhe steht, sieht man nur in
     * der Welt, und bis zum 09.11.2026 sah es niemand: Die Klage kam erst beim taeglichen
     * Lauf, also fruehestens einen Tag nach dem ersten Mieter - und der hat dann Miete
     * gezahlt fuer einen Stand, in den er nichts legen kann.
     *
     * <p><b>Die Truhe ist die harte Bedingung</b>, denn ohne sie ist der Stand
     * unbenutzbar. Der leere Raum ist nur ein Hinweis: Eine Halle wird von Hand gebaut,
     * und was darin an Regalen und Mauern steht, ist Sache des Betreibers. Nur wenn GAR
     * NICHTS frei ist, liegt der Stand im Berg, und das ist wieder sicher falsch.
     *
     * <p>Gefragt wird ueber {@link Ladenware#inventarAn} - dieselbe Stelle, die der
     * taegliche Lauf fragt. Eine eigene Fassung hier meldete sonst einen Stand als in
     * Ordnung, den der Lauf nicht raeumen kann.
     */
    private void pruefeLadeneinheiten(World ladenweltOder) {
        for (Ladeneinheit einheit : ladenverzeichnis.alle()) {
            Block truhe = ladenweltOder.getBlockAt(einheit.truheX(), einheit.truheY(),
                    einheit.truheZ());
            if (Ladenware.inventarAn(truhe) == null) {
                getLogger().severe("Ladeneinheit \"" + einheit.name() + "\": bei "
                        + einheit.truheAlsText() + " steht keine Truhe (dort steht "
                        + truhe.getType() + "). Der Stand ist nicht benutzbar - ein"
                        + " Mieter zahlt Miete und kann nichts hineinlegen. Setz dort eine"
                        + " Kiste, ein Fass oder eine Redstonetruhe.");
                continue;
            }
            long frei = freieBloecke(ladenweltOder, einheit);
            if (frei == 0L) {
                getLogger().severe("Ladeneinheit \"" + einheit.name() + "\" ("
                        + einheit.bereich().alsText() + ") ist vollstaendig massiv - sie"
                        + " liegt im Berg, und niemand kommt hinein. Die Truhe steht, der"
                        + " Raum nicht.");
            } else {
                getLogger().info("Ladeneinheit \"" + einheit.name() + "\": Truhe bei "
                        + einheit.truheAlsText() + ", " + frei + " von "
                        + einheit.bereich().bloecke() + " Bloecken frei.");
            }
        }
    }

    /** Wie viele Bloecke dieses Quaders Luft sind - der Hinweis, nicht die Regel. */
    private long freieBloecke(World welt, Ladeneinheit einheit) {
        Quader bereich = einheit.bereich();
        long frei = 0L;
        for (int x = bereich.vonX(); x <= bereich.bisX(); x++) {
            for (int y = bereich.vonY(); y <= bereich.bisY(); y++) {
                for (int z = bereich.vonZ(); z <= bereich.bisZ(); z++) {
                    if (welt.getBlockAt(x, y, z).getType().isAir()) {
                        frei++;
                    }
                }
            }
        }
        return frei;
    }


    /**
     * Prueft, ob durch jedes Tor ueberhaupt jemand gehen kann.
     *
     * <p>Noetig, weil die Torquader Zahlen in einer Datei sind und das Gelaende nicht.
     * Die ausgelieferten Werte passen zu {@code art: flach} - dort liegt der Boden
     * ueberall auf y -61, und die Reihe steht auf y -60. Wer Altfurt gewachsen macht
     * oder die Quader verschiebt, hat eine Datei, die etwas anderes sagt als die Welt:
     * Die Tore liegen dann im Berg, in der Luft oder im Wasser. Am 18.11.2026 lagen
     * alle acht im Meer. Ohne diese Pruefung merkt man es erst, wenn jemand
     * durchgehen will und nichts passiert.
     *
     * <p>Was ein Tor begehbar macht, steht in {@link Ankunftspunkt#torklage} - dort und
     * nicht hier, damit es eine Probe dafuer geben KANN: FreimarkenPlugin ist ohne
     * laufenden Server nicht zu starten, der {@link Ankunftspunkt.Weltgriff} dagegen ist
     * eine Attrappe von drei Fragen. Hier bleibt, was nur das Plugin weiss - welche
     * Welten es gibt und wohin die Klage geht.
     */
    private void pruefeTore(World ankunftswelt) {
        var griff = Ankunftspunkt.griffAuf(ankunftswelt);
        for (Weltangabe angabe : angaben.values()) {
            if (!angabe.hatTor()) {
                continue;
            }
            String klage = Ankunftspunkt.torklage(griff, angabe.tor());
            if (klage != null) {
                getLogger().severe("Tor nach " + angabe.name() + " ("
                        + angabe.tor().alsText() + "): " + klage);
            }
        }
    }

    /**
     * Meldet jeden Katalogposten, der NICHT ANKOMMEN KANN [20.11.2026].
     *
     * <p>Zwei Faelle, und beide sind in der Pruefrunde zu Teil G gefunden worden:
     *
     * <p><b>Kein Gegenstand.</b> {@code potatoes} ist der Feldblock, {@code potato} der
     * Gegenstand - dasselbe bei {@code carrots}, {@code beetroots} und {@code water}. Der
     * Leser der {@code config.yml} kann das nicht fragen: {@code Material.isItem} greift
     * in Minecrafts Verzeichnis und wirft ohne laufenden Server, und die Proben des
     * Lesers laufen ohne. Die Lieferung sagt seit heute ab (KEIN PLATZ, vor der Zahlung,
     * siehe {@code Vorratshand.art}) - aber eine Absage, die der Betreiber nie liest,
     * laesst ihn den Fehler beim Spieler suchen.
     *
     * <p><b>Mehr Plaetze als ein Spieler hat.</b> Vierzig Musikscheiben sind vierzig
     * Lagerplaetze; die Stueckgrenze von {@link Muenzwerte#HOECHSTE_STUECKZAHL} laesst
     * sie durch, weil sie in Stueck rechnet. Ein solcher Posten stuende mit Preis in der
     * Tafel und antwortete auch bei leerem Rucksack "dir fehlt Platz".
     *
     * <p>Der Posten bleibt in beiden Faellen stehen, wie der ohne Wort: Wegfallen liesse
     * den Betreiber einen Katalog lesen, in dem seine Zeile einfach nicht ist.
     */
    private void nenneUnlieferbarePosten() {
        List<String> klagen = new ArrayList<>();
        for (Muenzwerte.Posten posten : wirtschaft.werte().muenzhaus().katalog()) {
            List<String> keinGegenstand = new ArrayList<>();
            int plaetze = 0;
            for (var zeile : posten.gegenstaende().entrySet()) {
                Material art = Material.matchMaterial(zeile.getKey());
                if (art == null || !art.isItem()) {
                    keinGegenstand.add(zeile.getKey());
                    continue;
                }
                int stapel = Math.max(1, art.getMaxStackSize());
                plaetze += (zeile.getValue() + stapel - 1) / stapel;
            }
            if (!keinGegenstand.isEmpty()) {
                klagen.add(posten.schluessel() + ": " + String.join(", ", keinGegenstand)
                        + " gibt es nur als Block und nicht als Gegenstand - der Posten"
                        + " sagt beim Kauf ab");
            }
            if (plaetze > Muenzwerte.LAGERPLAETZE) {
                klagen.add(posten.schluessel() + ": braucht " + plaetze
                        + " Lagerplaetze, ein Spieler hat " + Muenzwerte.LAGERPLAETZE
                        + " - der Posten ist auf Dauer unkaufbar");
            }
        }
        if (klagen.isEmpty()) {
            return;
        }
        getLogger().severe(klagen.size() + " Katalogposten koennen nicht ankommen: "
                + String.join(" | ", klagen) + ". Gepruefte Namen sind nicht genug -"
                + " entscheidend ist, ob ein Spieler den Gegenstand in die Hand nehmen"
                + " und tragen kann.");
    }

    /**
     * Meldet jeden Katalogposten, zu dem es kein Wort gibt [Teil G].
     *
     * <p>BEIM START und nicht beim ersten {@code /mark muenzhaus}: Sonst faellt es mitten
     * im Spiel auf, und zwar als Pfad in der Tafel eines Spielers.
     *
     * <p><b>Der Posten bleibt trotzdem kaufbar</b>, wie eine Herausforderung ohne Wort und
     * anders als ein Beruf ohne Wort (dort bleibt Freimarken aus). Der Grund steht in
     * {@link Muenzname}: Ein weggefallener Posten waere Kosmetik, die niemand mehr
     * bekommt, und der Spieler suchte den Fehler bei sich.
     *
     * <p>Hier und nicht in {@link Muenzliste}: Der Leser kennt die {@code config.yml}, und
     * ob es ein WORT gibt, weiss nur der Sprachstand.
     */
    private void nennePostenOhneWort() {
        List<String> ohneWort = new ArrayList<>();
        for (String schluessel : wirtschaft.werte().muenzhaus().schluessel()) {
            if (!sprachstand.buch().saetze()
                    .containsKey(new Muenzname(schluessel).pfad())) {
                ohneWort.add(schluessel);
            }
        }
        if (ohneWort.isEmpty()) {
            return;
        }
        getLogger().severe("Zu " + ohneWort.size() + " Katalogposten gibt es kein Wort: "
                + String.join(", ", ohneWort) + ". In der Sprachdatei fehlt je Zeile "
                + Muenzname.VORSILBE + "<name> - sprache-vorlage.yml zeigt, wie sie"
                + " aussieht. Kaufen laesst sich der Posten weiter; in der Tafel steht"
                + " bis dahin sein Pfad.");
    }

    /**
     * Meldet jede Rundgangsstation, zu der eine Sprachzeile fehlt [Teil D].
     *
     * <p>BEIM START und nicht beim ersten Schritt: Sonst faellt es mitten im Spiel auf,
     * und zwar als Pfad in der Zeile eines Spielers, der gerade ein Gebaeude betreten
     * hat.
     *
     * <p><b>Die Station bleibt trotzdem begehbar</b>, wie ein Katalogposten ohne Wort und
     * anders als ein Beruf ohne Wort (dort bleibt Freimarken aus). Der Grund steht in
     * {@link Stationssatz}: Eine weggefallene Station waere ein Rundgang, den niemand
     * abschliessen kann, und der Spieler suchte den Fehler bei sich.
     *
     * <p>Hier und nicht in {@link Rundgangsliste}: Der Leser kennt die
     * {@code config.yml}, und ob es einen SATZ gibt, weiss nur der Sprachstand. Drei
     * Stellen sagen diese Pruefung zu - {@code Stationssatz}, der Kommentar der
     * {@code config.yml} und die Rechenprobe des Lesers.
     */
    private void nenneStationenOhneWort() {
        List<String> ohneWort = new ArrayList<>();
        for (String station : rundgangsstationen.namen()) {
            for (Stationssatz.Teil teil : Stationssatz.Teil.values()) {
                String pfad = new Stationssatz(station, teil).pfad();
                if (!sprachstand.buch().saetze().containsKey(pfad)) {
                    ohneWort.add(pfad);
                }
            }
        }
        if (ohneWort.isEmpty()) {
            return;
        }
        getLogger().severe("Zu " + rundgangsstationen.anzahl() + " Rundgangsstationen"
                + " fehlen " + ohneWort.size() + " Sprachzeilen: "
                + String.join(", ", ohneWort) + ". Begehbar bleiben sie; bis dahin liest"
                + " ein Spieler dort den Pfad statt eines Satzes. sprache-vorlage.yml"
                + " zeigt im Abschnitt rundgang:, wie die Zeilen aussehen.");
    }

    /**
     * Nennt die Rangleiter - und was an ihr nicht wirkt.
     *
     * <p>Drei Saetze, und der zweite ist der wichtige: <b>Die Zahlen in der
     * {@code config.yml} sind eine Untergrenze fuer JEDEN.</b> {@code Rangzahl} nimmt die
     * hoechste Zahl aus Recht und Datei; solange dort die Buergerzahl steht, hebt die
     * Leiter nichts, und im Spiel sieht das genau aus wie ein kaputter Rang. Keine Probe
     * kann das fangen - es sind zwei Zeilen in zwei Abschnitten, und jede fuer sich ist
     * richtig.
     *
     * <p>Der dritte Satz faengt den Vertipper im Rechtenamen: Ein Rang, der
     * {@code laden.miete} vergibt, setzt es klaglos, und es fragt es nie jemand. Gefragt
     * wird die Rechteverwaltung des Servers, weil nur sie weiss, was angemeldet ist.
     */
    private void nenneRaenge() {
        var werte = wirtschaft.werte().raenge();
        getLogger().info(Rangliste.rechenprobe(werte));
        java.util.Map<String, Integer> untergrenzen = java.util.Map.of(
                "parzellen", wirtschaft.werte().hoechstzahl(),
                "gruppe", wirtschaft.werte().gruppenFelder(),
                "inseln", wirtschaft.werte().inseln().hoechstzahl(),
                "wolken", wirtschaft.werte().wolken().hoechstzahl(),
                "freistaedte", wirtschaft.werte().freistaedte().hoechstzahl());
        Rangliste.wirkungslose(werte, untergrenzen).forEach(getLogger()::warning);
        var angemeldet = new java.util.TreeSet<String>();
        for (var recht : getServer().getPluginManager().getPermissions()) {
            angemeldet.add(recht.getName());
        }
        Rangliste.unbekannteRechte(werte, angemeldet).forEach(getLogger()::severe);
    }

    /**
     * Stuft beim Start jeden nach, dem mehr zusteht - und sagt, wie viele es waren.
     *
     * <p><b>Warum das bei JEDEM Start laeuft und nicht einmal</b> [19.11.2026]: Der Lauf
     * ist folgenlos, wenn alles stimmt - {@code Rangregel.aufstieg} geht nur nach oben,
     * und wer auf seiner Stufe steht, bekommt keinen Schreibzug. Ein Merker "schon
     * erledigt" waere darum Aufwand ohne Gegenwert, und es gibt in diesem Projekt auch
     * keinen fachlichen Merker, der einen Neustart ueberlebt. Dass er jedes Mal laeuft,
     * ist sogar der Gewinn: Er faengt den Spieler, der waehrend seiner Abwesenheit
     * aufstiegsreif wurde, und er zieht nach, wenn der Betreiber die Leiter aendert.
     *
     * <p>Die Zeile nennt die ZAHL der geprueften Spieler und nicht nur die der gehobenen.
     * Eine Meldung "0 nachgestuft" allein waere nicht von "es wurde gar nicht gelaufen" zu
     * unterscheiden - und genau das ist die Verwechslung, an der dieses Projekt schon
     * zweimal eine stumme Luecke hatte.
     */
    private void nenneNachstufung() {
        Aufstiegslauf.Ergebnis ergebnis = aufstiegslauf.nachstufung();
        if (ergebnis.geprueft() == 0) {
            getLogger().info("Nachstufung: niemand zu pruefen - es hat noch nie jemand"
                    + " gespielt, oder raenge.an ist aus.");
            return;
        }
        getLogger().info("Nachstufung: " + ergebnis.geprueft() + " Spieler geprueft, "
                + ergebnis.aufstiege().size() + " nachgestuft.");
        for (Aufstiegslauf.Aufstieg aufstieg : ergebnis.aufstiege()) {
            // JEDER einzeln und nicht nur die Zahl: Ein Rang ist das, woran Grenzen
            // haengen, und wer beim naechsten Start nachlesen will, WER gehoben wurde,
            // findet es sonst nirgends - die Tabelle traegt nur den neuen Stand.
            getLogger().info("  " + aufstieg.spieler() + ": " + aufstieg.vorher()
                    + " -> " + aufstieg.nachher() + ".");
        }
    }

    /**
     * Sagt beim Start, dass die Goldmark steht - und was sie noch nicht kann.
     *
     * <p>Eine Waehrung ohne Verbraucher ist genau die Gestalt, die dieses Projekt
     * mehrfach getroffen hat: gebaut, gruen geprueft, und niemand merkt etwas. Mit Teil E
     * sagte diese Zeile, dass es ueberhaupt keinen Weg zu Goldmark gibt; seit Teil F
     * nennt sie den EINEN, der es gibt - den Tausch. Was weiter fehlt, ist das Haus, das
     * sie ausgibt, und auch das steht hier: Ohne diese Zeile waere der Unterschied
     * zwischen "noch kein Verbraucher" und "falsch verdrahtet" von aussen nicht zu
     * sehen.
     *
     * <p>Genannt wird auch, WOHER ein Guthaben kommen kann. Das ist die Frage, die ein
     * Betreiber als erste stellt - seit Teil F ist der Tausch der eine Weg, und seit dem
     * 23.11.2026 kommt die Buchung von Hand dazu ({@code /freimarken goldgeld}, Plan 8.1s
     * zweite Quelle: Echtgeld). Beide muessen hier stehen: Wer nur den Tausch liest,
     * laesst einen Spieler 40.000 Mark tauschen, statt ihm 40 Goldmark gutzuschreiben -
     * der Rueckweg kostet ihn zwanzig Prozent. Und wer das Goldbuch prueft, haelt eine
     * Zeile {@code betreiber_goldgutschrift} sonst fuer einen Einbruch.
     *
     * <p><b>Die ZAHLEN des Tausches stehen hier nicht</b>, und das ist gemessen
     * [20.11.2026]: Sie stehen in der Rechenprobe des Lesers ({@code Wechselliste}),
     * und eine zweite Zeile daneben war im Startprotokoll wortgleich dieselbe. Der
     * Bericht des Lesers ist der richtige Ort, weil er auch bei {@code /freimarken
     * neuladen} erscheint - und eine Zahl ist am ehesten falsch, kurz nachdem
     * jemand sie geaendert hat.
     */
    private void nenneGoldmark() {
        getLogger().info("Goldmark: eigene Kasse, Startguthaben 0 - "
                + (fluechtig ? "FLUECHTIG, vergisst beim Neustart"
                        : "eigene Tabellen " + Kassentabellen.GOLDMARK.konten() + " und "
                                + Kassentabellen.GOLDMARK.buch())
                + ". Zu bekommen ist sie durch Tausch aus Mark"
                + " (/mark tauschen) oder von Hand vom Betreiber"
                + " (/freimarken goldgeld), ausgegeben wird sie im Muenzhaus"
                + " (/mark muenzhaus).");
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
        for (Map.Entry<String, World> eintrag : welten.entrySet()) {
            World welt = eintrag.getValue();
            String schluessel = welt.key().asString();
            if (!vorzugenerieren(eintrag.getKey())) {
                empfaenger.sendMessage(Component.text(schluessel, NamedTextColor.YELLOW)
                        .append(Component.text(" - Leere, hier entsteht Gelaende erst,"
                                + " wenn jemand eine Insel nimmt. Nichts"
                                + " vorzugenerieren.", NamedTextColor.GRAY)));
                continue;
            }
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
     * Ob diese Welt ueberhaupt etwas zu erzeugen hat.
     *
     * <p>Die Frage steht in {@link Weltangabe#vorzugenerieren}, und hier wird sie zum
     * Weltnamen gestellt - beide Stellen, die ueber das Vorgenerieren reden, muessen
     * dieselbe Antwort bekommen. Eine Welt ohne Angabe gilt als vorzugenerierend: Das
     * kann nicht vorkommen (die Welten entstehen aus den Angaben), und wenn doch, ist
     * die Mahnung die mildere Folge gegenueber einer stillschweigend uebergangenen Welt.
     */
    private boolean vorzugenerieren(String name) {
        Weltangabe angabe = angaben.get(name);
        return angabe == null || angabe.vorzugenerieren();
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
        List<World> zuPruefen = welten.entrySet().stream()
                .filter(eintrag -> vorzugenerieren(eintrag.getKey()))
                .map(Map.Entry::getValue)
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
            // Durch die Reise und nicht mit einem eigenen teleportAsync: Das war bis zum
            // 02.11.2026 der einzige Teleport ohne Standprobe, ohne Abklingzeit und ohne
            // Auswertung des Ergebnisses - und seit es /zurueck gibt, muesste er sich
            // ausserdem gemerkt werden, sonst fuehrt /zurueck an die Stelle davor.
            reise.fuehre(spieler, ziel, "Parzelle " + parzelleX + "/" + parzelleZ);
        } else {
            empfaenger.sendMessage(Component.text(
                    "Parzelle " + parzelleX + "/" + parzelleZ + " liegt bei "
                            + ziel.getBlockX() + "/" + ziel.getBlockZ()
                            + " (Ursprung " + plan.ursprungX(parzelleX) + "/"
                            + plan.ursprungZ(parzelleZ) + ")", NamedTextColor.GRAY));
        }
    }
}
