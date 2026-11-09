package de.freimarken.paper;

import de.freimarken.daten.Datenbank;
import de.freimarken.daten.MariaBonus;
import de.freimarken.daten.MariaAbholfach;
import de.freimarken.daten.MariaBlockbuch;
import de.freimarken.daten.MariaMiete;
import de.freimarken.daten.MariaShops;
import de.freimarken.daten.MariaKasse;
import de.freimarken.daten.MariaLager;
import de.freimarken.daten.MariaBerufe;
import de.freimarken.daten.MariaSpielzeit;
import de.freimarken.kern.Bonusablage;
import de.freimarken.kern.SpeicherBonus;
import de.freimarken.kern.Abholablage;
import de.freimarken.kern.Blockbuchablage;
import de.freimarken.kern.Blockbuchwerte;
import de.freimarken.kern.SpeicherAbholfach;
import de.freimarken.kern.SpeicherBlockbuch;
import de.freimarken.kern.SpeicherMiete;
import de.freimarken.kern.Berufsablage;
import de.freimarken.kern.Berufsverzeichnis;
import de.freimarken.kern.Blockmarken;
import de.freimarken.kern.Berufswerte;
import de.freimarken.kern.BesitzLager;
import de.freimarken.kern.Besitzbuch;
import de.freimarken.kern.Chunkfeld;
import de.freimarken.kern.Grenzregel;
import de.freimarken.kern.Kasse;
import de.freimarken.kern.Ladenverzeichnis;
import de.freimarken.kern.Mietablage;
import de.freimarken.kern.Messwerte;
import de.freimarken.kern.Preisstaffel;
import de.freimarken.kern.Ladeneinheit;
import de.freimarken.kern.Quader;
import de.freimarken.kern.Rasterplan;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherBerufe;
import de.freimarken.kern.SpeicherShops;
import de.freimarken.kern.ZwischenspeicherBerufe;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.SpeicherSpielzeit;
import de.freimarken.kern.Spielzeitablage;
import de.freimarken.kern.Stadtgrenze;
import de.freimarken.kern.Tagesgrenze;
import de.freimarken.kern.Warpverzeichnis;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Zeitwerte;
import de.freimarken.kern.ZwischenspeicherLager;
import de.freimarken.kern.ZwischenspeicherShops;
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
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
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
    private Spielzeitablage spielzeit;

    /**
     * Die Serien des Tagesbonus - ein Feld, weil der Takt und der Beitritt sie brauchen.
     *
     * <p>Hier liegt kein Geld: Ob heute schon gezahlt wurde, entscheidet das Buchungsbuch.
     * Siehe {@link de.freimarken.kern.Bonusablage}.
     */
    private Bonusablage bonusablage;
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
        var liste = Weltenliste.ausKonfiguration(
                getConfig().getConfigurationSection("welten"), MARKTSTADT, ankunftswelt);
        liste.klagen().forEach(getLogger()::severe);
        var gebaut = new Weltenbau(this, generator, getLogger()).baueAlle(liste.welten());
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
        befehle = new ParzellenBefehle(buch, marktstadt, wirtschaft, getLogger(),
                sprache,
                // Das FELD wird beim Aufruf gelesen und nicht beim Bauen: Die Befehle
                // entstehen vor dem Spielzeittakt, mit dem der Bonus entsteht. Die
                // Pruefung auf null faengt den Fall, dass der Start davor abgebrochen
                // ist - dann zeigt /mark seine uebrigen Zeilen.
                wer -> tagesbonus == null ? null : tagesbonus.standZeile(wer, sprache));
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
        schutzwache = new Schutzwache(buch, welt, sprache,
                new Ladenschutz(ladenverzeichnis, mietablage,
                        weltschluessel(ladenwelt), sprache,
                        getLogger()),
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
        }, verdienstbuch, marken, berufsablage, wirtschaft, markierteWelten, sprache,
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
        getLogger().info("Bundesmarkt: "
                + wirtschaft.werte().markt().ankauf().anzahl() + " Materialien im"
                + " Sortiment, Tagesdeckel " + wirtschaft.werte().markt().tagesdeckel()
                + " Mark je Spieler. Verkauft wird nur in der Ankunftswelt.");
        richteBlockbuchEin();
        richteReisenEin(ankunftswelt);
        richteSpielzeitEin();
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
        mietbuch = new Mietbuch(wirtschaft, ladenverzeichnis, mietablage, kasse, uhr,
                getLogger());
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
                new Ladenware(getServer(), ladenverzeichnis,
                        weltschluessel(ladenwelt), getLogger()),
                uhr, getLogger());
        mietwache = new Mietwache(getServer(), mietlauf, mietbuch, abholbuch, wirtschaft,
                sprache);
        getServer().getPluginManager().registerEvents(mietwache, this);
        richteShopsEin();
        Bonuswache bonuswache = new Bonuswache(tagesbonus, wirtschaft, sprache);
        getServer().getPluginManager().registerEvents(bonuswache, this);
        // blockraeumung darf null sein - ohne Datenbank gibt es kein Blockbuch und
        // nichts zu raeumen. richteBlockbuchEin() laeuft VOR dieser Methode und hat es
        // dann gesetzt; die Reihenfolge sichert 31ai.
        spielzeitwache = new Spielzeitwache(this, buch, bonuswache, mietwache,
                blockraeumung, wirtschaft, getLogger());
        getServer().getPluginManager().registerEvents(spielzeitwache, this);
        spielzeitwache.starte();
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
                getLogger());
        Shopwache shopwache = new Shopwache(shopbuch, shopware, sprache,
                wirtschaft.werte().handel().bundWort(),
                tat -> getServer().getScheduler().runTask(this, tat), getLogger());
        getServer().getPluginManager().registerEvents(shopwache, this);
        // Der HANDEL, und er wird ZULETZT gesetzt: Das Feld ist die Tuer der
        // Schutzwache, und die steht schon. Solange es null war, galt an jedem
        // Shopschild der gewoehnliche Bauschutz - also genau der Zustand, den die
        // Startmeldung oben fuer eine stumme Datenbank verspricht.
        Shophandel shophandel = new Shophandel(shopbuch, shopware, kasse,
                wirtschaft.werte().handel(), getLogger());
        shopklick = new Shopklick(shophandel, sprache);
        getServer().getPluginManager().registerEvents(shopklick, this);
        getLogger().info("Kistenshops aktiv: " + gefunden + " im Speicher, "
                + Shopliste.startzeile(wirtschaft.werte().handel()));
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
            // Braucht keine Uhr: Der Tag kommt mit dem Zuschlag herein und bei lies() von
            // aussen. Eine Uhr hier waere eine zweite Quelle fuer die Tagesgrenze.
            spielzeit = new SpeicherSpielzeit();
            bonusablage = new SpeicherBonus();
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
            // Derselbe Zwischenspeicher wie mit Datenbank, nur mit einer anderen
            // Wahrheit darunter: So laeuft der Betrieb durch GENAU denselben Code, ob
            // eine Datenbank da ist oder nicht. Eine Fassung ohne Zwischenspeicher waere
            // ein zweiter Weg zu der Frage, wo ein Shop haengt.
            shopablage = new ZwischenspeicherShops(new SpeicherShops());
            // Braucht ebenfalls keine Uhr: Der Zeitpunkt kommt im Antrag von aussen.
            berufsablage = new ZwischenspeicherBerufe(new SpeicherBerufe());
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
            // KEIN Zwischenspeicher darum: Fortschreiben ist additiv, und ein
            // zwischengespeicherter additiver Schreibzug ist entweder verloren oder
            // doppelt. Gelesen wird nur auf Befehl, also auch nichts zu sparen.
            spielzeit = new MariaSpielzeit(datenbank);
            bonusablage = new MariaBonus(datenbank);
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
            // ZWISCHENSPEICHER, und hier ist er keine Ersparnis, sondern die Sache:
            // betroffen() fragt bei JEDER Explosion fuer JEDEN Block und bei jedem
            // Trichtertakt, ob dort ein Shop haengt. Eine Datenbankabfrage an dieser
            // Stelle waere derselbe Fehler, den QuickShop #2165 mit einem Puffer von drei
            // Minuten Verfallszeit zu loesen versuchte - und dessen veraltete Antwort war
            // genau die Luecke. Dieser hier ist eine VOLLSTAENDIGE Kopie ohne
            // Verfallszeit: Es gibt keinen Augenblick, in dem er etwas Falsches sagt.
            shopablage = new ZwischenspeicherShops(new MariaShops(datenbank));
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
     * Baut den Befehlsbaum von {@code /markt} - im Ereignis, nicht im onEnable.
     *
     * <p>Dieselbe Form und derselbe Grund wie bei {@link #berufeBefehle}.
     *
     * <p>Die ANKUNFTSWELT ist der Ort des Bundesmarktes: Dort steht er laut Plan 2.1,
     * und dort kommt jeder Spieler zuerst an. Sie wird nur beim Start gelesen, also
     * haengt der Befehl an derselben Entscheidung wie die Ankunft und nicht an einer
     * zweiten Abschrift davon.
     */
    private MarktBefehle marktBefehle() {
        String ankunftswelt = getConfig()
                .getString("reisen.ankunftswelt", ANKUNFTSWELT_VORGABE).trim();
        return new MarktBefehle(wirtschaft, kasse, uhr, sprache,
                weltschluessel(ankunftswelt), getLogger());
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
                grenze.von(), grenze.bis());
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
        var verzeichnis = Warpverzeichnis.ausWelten(List.copyOf(welten.keySet()));
        warpBefehle = new WarpBefehle(verzeichnis, welten, reise, sprache);
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
