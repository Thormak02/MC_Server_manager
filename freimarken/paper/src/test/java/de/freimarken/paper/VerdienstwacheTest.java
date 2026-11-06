package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Berufsablage;
import de.freimarken.kern.Berufsstand;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.SpeicherBerufe;
import de.freimarken.kern.SpeicherBlockmarken;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.Taetigkeit;
import de.freimarken.kern.Verdienstwerte;
import de.freimarken.kern.Wirtschaftsstand;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Verdienstwache DURCHLAUFEN - mit echten Abbauereignissen.
 *
 * <p>Dieselbe Entscheidung wie bei {@link SetzwacheTest}: Das Ereignis wird wirklich
 * gebaut. Hier aber nicht, weil etwas nur im Ereignis steht, sondern weil in diesem Zweig
 * GELD entsteht - und zwischen Ereignis und Lohn liegen vier Entscheidungen, von denen
 * jede einzelne eine Gelddruckmaschine waere, wenn sie falsch herum stuende: der
 * Weltfilter, die Marke, die Berufe und die Zahl der Wiederholungen.
 *
 * <p><b>Die Wache bekommt ihren Zeitgeber als Naht</b>
 * ({@link Verdienstwache.Taktquelle}) und nicht als {@code JavaPlugin}: Ein JavaPlugin
 * laesst sich ohne laufenden Server nicht bauen, und dann waere genau dieser Zweig der
 * einzige ungepruefte des Schrittes.
 */
@DisplayName("Verdienstwache")
class VerdienstwacheTest {

    private static final String MARKTSTADT = "freimarken:marktstadt";
    private static final String HOCHWALD = "freimarken:hochwald";
    private static final String FREMD = "freimarken:anderswo";


    /** Eine Welt, deren Blockart die Probe stellt - 40 Pfennige oder 1000. */
    private static final class EinBlock implements Weltgriff {

        private Material art = Material.STONE;

        @Override
        public int unten() {
            return -64;
        }

        @Override
        public int oben(int x, int z) {
            return 64;
        }

        @Override
        public Material bei(int x, int y, int z) {
            return art;
        }

        @Override
        public void setze(int x, int y, int z, Material block) {
            throw new UnsupportedOperationException("Diese Probe setzt keine Bloecke -"
                    + " sie prueft, was die Wache mit einem abgebauten tut.");
        }
    }

    /**
     * Eine Taktquelle, die nichts stellt und alles mitschreibt.
     *
     * <p>Sie gibt den Schlag HERAUS, statt ihn zu rufen: Ein Zeitgeber, der in der Probe
     * von selbst laeuft, machte jede Zusage ueber "wann wird gebucht" unpruefbar.
     */
    private static final class Probetakt implements Verdienstwache.Taktquelle {

        private Runnable schlag;
        private long abstand;
        private int gestellt;
        private int abgemeldet;

        @Override
        public Runnable stelle(Runnable was, long abstandTicks) {
            gestellt++;
            schlag = was;
            abstand = abstandTicks;
            return () -> abgemeldet++;
        }
    }

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private SpielzeitbuchTest.StellUhr uhr;
    private SpeicherKasse kasse;
    private SpeicherBlockmarken marken;
    private SpeicherBerufe berufe;
    private Probetakt takt;
    private EinBlock griff;
    private World marktstadt;
    private Logger protokoll;
    private Spielerprobe anna;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        griff = new EinBlock();
        marktstadt = Serverprobe.welt(MARKTSTADT, griff);
        uhr = new SpielzeitbuchTest.StellUhr(Instant.parse("2026-11-06T12:00:00Z"));
        kasse = new SpeicherKasse(0L, uhr);
        marken = new SpeicherBlockmarken();
        berufe = new SpeicherBerufe();
        takt = new Probetakt();
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
        anna = new Spielerprobe("Anna", Spielerprobe.kennungAus(1)).inWelt(marktstadt);
        // Bergmann, damit Stein und Erz etwas einbringen.
        berufe.waehle(new Berufsablage.Antrag(anna.kennung(), "bergmann", uhr.instant()),
                Berufsstand.leer(anna.kennung()));
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("VerdienstwacheProbe-" + UUID.randomUUID());
        eigener.setUseParentHandlers(false);
        eigener.addHandler(new Handler() {
            @Override
            public void publish(LogRecord satz) {
                protokollzeilen.add(satz);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return eigener;
    }

    /**
     * Derselbe Stand, den der Sammler bekommt - mit den Saetzen aus VerdienstbuchTest.
     *
     * <p>Die Wache braucht ihn seit E2 selbst: An ihm haengen die Weissliste der
     * Entstehungsgruende und die Frage, ob fuer ein Material ein Erntesatz vorliegt.
     * DERSELBE Stand und keine zweite Abschrift - sonst prueefte diese Probe eine andere
     * Wirtschaft als die, in der gebucht wird.
     */
    private Wirtschaftsstand stand() {
        return VerdienstbuchTest.stand(VerdienstbuchTest.werte(
                Verdienstwerte.VORGABE_TAGESDECKEL));
    }

    private Verdienstbuch buch(long tagesdeckel) {
        return new Verdienstbuch(kasse, VerdienstbuchTest.stand(
                VerdienstbuchTest.werte(tagesdeckel)), uhr, protokoll);
    }

    private Verdienstwache wache(Verdienstbuch buch) {
        return wache(buch, berufe);
    }

    private Verdienstwache wache(Verdienstbuch buch, Berufsablage ablage) {
        return new Verdienstwache(takt, buch, marken, ablage, stand(),
                Set.of(MARKTSTADT, HOCHWALD), Sprachprobe.spracheAusDemJar(), protokoll);
    }

    /**
     * Eine Berufsablage, in der Anna genau diese Berufe haelt.
     *
     * <p>Je Probe eine eigene: Welcher Beruf welche Taetigkeit bezahlt, ist der Kern
     * dessen, was hier geprueft wird - eine gemeinsame Ablage mit allen Berufen liesse
     * nicht sehen, ob der Lohn wirklich am richtigen Beruf haengt.
     */
    private Berufsablage berufe(String... welche) {
        SpeicherBerufe eigene = new SpeicherBerufe();
        for (String beruf : welche) {
            eigene.waehle(new Berufsablage.Antrag(anna.kennung(), beruf, uhr.instant()),
                    eigene.lies(anna.kennung()));
        }
        return eigene;
    }

    /** Ein Angelereignis - was die Wache nicht liest, ist null. */
    private PlayerFishEvent angeln(PlayerFishEvent.State zustand, boolean offenesWasser,
                                   org.bukkit.entity.Entity gefangen) {
        return new PlayerFishEvent(anna.spieler(), gefangen,
                Wesenprobe.angel(offenesWasser), zustand);
    }

    /**
     * Ein Todesereignis.
     *
     * <p>Die Schadensquelle ist {@code null}, und das ist kein Mangel: Diese Wache liest
     * sie NIE. {@code getDamageSource().getDamageType()} waere ohne laufenden Server
     * nicht zu lesen - die Konstanten von {@code DamageType} schlagen in der Registry
     * nach und werfen -, und dann liesse sich genau dieser Zweig nicht pruefen. Der
     * Toeter kommt aus {@code getEntity().getKiller()}.
     */
    private EntityDeathEvent sterben(EntityType art, org.bukkit.entity.Player toeter,
                                     CreatureSpawnEvent.SpawnReason grund) {
        return new EntityDeathEvent(Wesenprobe.wesen(art, toeter, grund), null,
                new ArrayList<>());
    }

    /**
     * Ein Zuchtereignis - mit VERSCHIEDENEN Arten fuer das Junge und die Eltern.
     *
     * <p>Verschieden, damit die Probe wirklich prueeft, welches Wesen den Namen gibt:
     * Waeren beide eine Kuh, blieben {@code getEntity()} und {@code getMother()}
     * ununterscheidbar - und eine Verwechslung im Zweig faellt nie auf. Pferd und Esel
     * ergeben wirklich ein Maultier, und das traegt seinen eigenen Satz.
     */
    private EntityBreedEvent zuechten(EntityType jung, EntityType eltern,
                                      org.bukkit.entity.Player zuechter) {
        return new EntityBreedEvent(Wesenprobe.wesen(jung, null,
                        CreatureSpawnEvent.SpawnReason.BREEDING),
                Wesenprobe.wesen(eltern, null, CreatureSpawnEvent.SpawnReason.NATURAL),
                Wesenprobe.wesen(eltern, null, CreatureSpawnEvent.SpawnReason.NATURAL),
                zuechter, null, 0);
    }

    /** Ein Abbauereignis an dieser Stelle - die Art kommt aus dem Weltgriff. */
    private BlockBreakEvent abbauen(int x, int y, int z) {
        return new BlockBreakEvent(marktstadt.getBlockAt(x, y, z), anna.spieler());
    }

    @Test
    @DisplayName("Ein abgebauter Block zahlt - und sagt es ueber dem Gegenstandsgurt")
    void abgebauterBlockZahlt() {
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch);

        wache.beimAbbauen(abbauen(7, 71, 13));

        assertEquals(40L, buch.rest(anna.kennung()), "vierzig Pfennige fuer einen Stein");
        assertEquals(List.of("&a+0,40 Mark"), anna.handbalkenBild());
    }

    @Test
    @DisplayName("Ein MARKIERTER Block zahlt beim Abbauen nichts")
    void markierterBlockZahltNicht() {
        // Die Zusage des vorigen Schrittes, hier eingeloest: "Ein Block zahlt nur
        // einmal." Ohne sie waere setzen-abbauen-setzen eine Schleife, die beide Saetze
        // addiert - und das ist die Gelddruckmaschine, die dieser ganze Zweig verhindern
        // soll.
        marken.markiere(new Blockort(MARKTSTADT, 7, 71, 13));
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch);

        wache.beimAbbauen(abbauen(7, 71, 13));

        assertEquals(0L, buch.rest(anna.kennung()));
        assertEquals(List.of(), anna.handbalkenBild(), "und kein Wort darueber");
        // Der Nachbarblock daneben zahlt weiter: Die Marke gilt FUER DIESE Position.
        wache.beimAbbauen(abbauen(8, 71, 13));
        assertEquals(40L, buch.rest(anna.kennung()));
    }

    @Test
    @DisplayName("In einer fremden Welt verdient niemand etwas")
    void fremdeWeltZahltNicht() {
        // Dieselbe Weltmenge wie bei der Setzwache, und das ist der Grund: Eine Welt, in
        // der verdient und NICHT markiert wird, waere die Gelddruckmaschine - dort zahlte
        // derselbe Block beim Setzen und beim Abbauen, beliebig oft.
        World fremde = Serverprobe.welt(FREMD, griff);
        Spielerprobe bert = new Spielerprobe("Bert", Spielerprobe.kennungAus(2))
                .inWelt(fremde);
        berufe.waehle(new Berufsablage.Antrag(bert.kennung(), "bergmann", uhr.instant()),
                Berufsstand.leer(bert.kennung()));
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch);

        wache.beimAbbauen(new BlockBreakEvent(fremde.getBlockAt(7, 71, 13),
                bert.spieler()));

        assertEquals(0L, buch.rest(bert.kennung()));
        assertEquals(List.of(), bert.handbalkenBild());
        // Und die Marke wird dabei gar nicht gelesen: Der Weltfilter steht VOR ihr, damit
        // ein Block in einer fremden Welt genau einen Vergleich kostet.
        assertEquals(0, marken.anzahl());
    }

    @Test
    @DisplayName("Ohne Beruf zahlt kein Block")
    void ohneBerufZahltNichts() {
        Spielerprobe ohne = new Spielerprobe("Cara", Spielerprobe.kennungAus(3))
                .inWelt(marktstadt);
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch);

        wache.beimAbbauen(new BlockBreakEvent(marktstadt.getBlockAt(7, 71, 13),
                ohne.spieler()));

        assertEquals(0L, buch.rest(ohne.kennung()));
        assertEquals(List.of(), ohne.handbalkenBild());
    }

    @Test
    @DisplayName("Ein Ausfall der Berufsablage zahlt nichts - und reisst nichts mit")
    void kaputteAblageZahltNichts() {
        // Der Zweig laeuft auf MONITOR: Ein Wurf landete als Stapelspur im
        // Serverprotokoll, je Block erneut - bei einem Spieler mit Flaechenwerkzeug
        // hundertfach in einer Sekunde. Eine leere Menge heisst "kein Lohn", und das ist
        // die sichere Richtung.
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, new KaputteAblage());

        wache.beimAbbauen(abbauen(7, 71, 13));

        assertEquals(0L, buch.rest(anna.kennung()));
        assertEquals(List.of(), anna.handbalkenBild());
    }

    @Test
    @DisplayName("Das Setzen zahlt je NEUE Marke - eine Tuer zweimal")
    void setzenZahltJeNeueMarke() {
        // Bei einer Tuer sind es zwei Bloecke, also zweimal den Satz. Bei einer Tuer, von
        // der eine Haelfte schon stand, nur einmal - die Setzwache zaehlt das, und diese
        // Zahl kommt hier an.
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch);

        wache.zahleFuerSetzen(anna.spieler(), Material.STONE, 2);

        assertEquals(20L, buch.rest(anna.kennung()), "zwei mal zehn Pfennige");
        assertEquals(List.of("&a+0,20 Mark"), anna.handbalkenBild(),
                "und EIN Handbalken fuer beide, nicht zwei");
    }

    @Test
    @DisplayName("Ohne neue Marke zahlt das Setzen nichts")
    void ohneNeueMarkeKeinLohn() {
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch);

        wache.zahleFuerSetzen(anna.spieler(), Material.STONE, 0);
        wache.zahleFuerSetzen(anna.spieler(), Material.STONE, -1);

        assertEquals(0L, buch.rest(anna.kennung()));
        assertEquals(List.of(), anna.handbalkenBild());
    }

    @Test
    @DisplayName("Der Handbalken kommt gedrosselt - aber die Pfennige zaehlen alle")
    void handbalkenGedrosseltAberNichtsVerloren() {
        // Zwanzig Nachrichten je Sekunde waeren ein flackernder Streifen. Was in der
        // Pause verdient wurde, ist nicht weg: Die naechste Zeile nennt die SUMME.
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch);

        wache.beimAbbauen(abbauen(7, 71, 13));
        wache.beimAbbauen(abbauen(8, 71, 13));
        wache.beimAbbauen(abbauen(9, 71, 13));

        assertEquals(List.of("&a+0,40 Mark"), anna.handbalkenBild(),
                "nur der erste kommt durch");
        // Hundert und nicht hundertzwanzig: Der dritte Stein ist der erste gemminderte
        // (voll-bis 2 in dieser Probe), also 40 + 40 + 20. Die Minderung laeuft WEITER,
        // auch wenn der Handbalken schweigt - sonst waere die Drossel ein Rabatt.
        assertEquals(100L, buch.rest(anna.kennung()), "gezaehlt sind alle drei");

        uhr.weiter(Verdienstbuch.ANZEIGEPAUSE);
        wache.beimAbbauen(abbauen(10, 71, 13));

        assertEquals(List.of("&a+0,40 Mark", "&a+0,80 Mark"), anna.handbalkenBild(),
                "und dann die Summe der drei, die in der Pause lagen: 40 + 20 + 20");
    }

    @Test
    @DisplayName("Am Tagesdeckel kommt der ANDERE Satz")
    void amDeckelKommtDerAndereSatz() {
        // Sonst arbeitete ein Spieler weiter fuer nichts, ohne es zu erfahren - und der
        // Deckel waere eine stille Wand. Dass der Rest dabei verfaellt, prueft der
        // Sammler; hier geht es um das Wort.
        griff.art = Material.DIAMOND_ORE;
        var buch = buch(1L);
        var wache = wache(buch);
        wache.beimAbbauen(abbauen(7, 71, 13));
        assertEquals(List.of("&a+10,00 Mark"), anna.handbalkenBild());

        assertEquals(1L, buch.rechneAb(10), "der Deckel laesst eine Mark zu");
        uhr.weiter(Verdienstbuch.ANZEIGEPAUSE);
        wache.beimAbbauen(abbauen(8, 71, 13));

        assertEquals(List.of("&a+10,00 Mark",
                        "&eHeute ist genug verdient - morgen zahlt die Arbeit wieder."),
                anna.handbalkenBild());
    }

    // --- ERNTEN: dieselbe Marke, aber eine eigene Taetigkeit ----------------

    @Test
    @DisplayName("Eine REIFE Pflanze zahlt als ERNTE und nicht als Abbau")
    void reifePflanzeZahltAlsErnte() {
        // Welche der beiden Taetigkeiten gilt, entscheidet die LOHNLISTE: Steht fuer
        // dieses Material ein Erntesatz darin, ist es eine Ernte. So haengt die
        // Entscheidung an der Datei, die der Betreiber pflegt, und nicht an einer
        // Abschrift daneben.
        griff.art = Material.WHEAT;
        Serverprobe.alter(MARKTSTADT, 7, 71, 13, 7, 7);
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("bauer"));

        wache.beimAbbauen(abbauen(7, 71, 13));

        assertEquals(150L, buch.rest(anna.kennung()), "der Erntesatz, nicht der Abbau");
        // Und das Fenster traegt die TAETIGKEIT im Schluessel - daran haengt, dass
        // Ernten und Abbauen nicht denselben Zaehler teilen.
        assertEquals(List.of("ernten/" + VerdienstbuchTest.WEIZEN),
                buch.gezaehlteArten(anna.kennung()));
    }

    @Test
    @DisplayName("Eine UNREIFE Pflanze bringt nichts - auch nicht als Abbau")
    void unreifePflanzeZahltNichts() {
        // Wer gesaeten Weizen umschlaegt, hat nichts geerntet. Und er hat auch nichts
        // abgebaut: Ein Rueckfall auf den Abbausatz waere die Maschine, gegen die die
        // Reifepruefung gebaut ist - saeen, umschlagen, saeen.
        griff.art = Material.WHEAT;
        Serverprobe.alter(MARKTSTADT, 7, 71, 13, 3, 7);
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("bauer"));

        wache.beimAbbauen(abbauen(7, 71, 13));

        assertEquals(0L, buch.rest(anna.kennung()));
        assertEquals(List.of(), anna.handbalkenBild());
        assertEquals(List.of(), buch.gezaehlteArten(anna.kennung()),
                "und es entsteht kein Zaehler fuer etwas, das nichts einbringt");
    }

    @Test
    @DisplayName("Eine MARKIERTE Erntepflanze zahlt nichts - der Kuerbis im Loch")
    void markierteErnteZahltNichts() {
        // Die wichtigste Probe dieses Schrittes. Der naheliegende Entwurf - "Ernten
        // fragt die Marke nicht, weil Saat gesetzt wird" - macht einen einzigen Kuerbis
        // in einem Ein-Block-Loch zur Maschine: setzen (zahlt einmal), abbauen (die
        // Ernte zahlt), wieder setzen (Marke steht, zahlt nichts), wieder abbauen (die
        // Ernte zahlt erneut) - unbegrenzt, ohne Farm, ohne Werkzeug, ohne Weg.
        //
        // Dass ein von Hand gesaetes Feld trotzdem zahlt, besorgt die andere Seite:
        // Die Setzwache markiert Saatgut nicht. Geprueft wird das dort.
        griff.art = Material.WHEAT;
        Serverprobe.alter(MARKTSTADT, 7, 71, 13, 7, 7);
        marken.markiere(new Blockort(MARKTSTADT, 7, 71, 13));
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("bauer"));

        wache.beimAbbauen(abbauen(7, 71, 13));

        assertEquals(0L, buch.rest(anna.kennung()));
        assertEquals(List.of(), anna.handbalkenBild());
    }

    @Test
    @DisplayName("Bei einer Hoehlenranke entscheiden die BEEREN, nicht das Alter")
    void beerenVorAlter() {
        // CaveVines ist BEIDES - Ageable und CaveVinesPlant -, und sein Alter ist die
        // LAENGE der Ranke, nicht die Reife. Wer das Alter zuerst fragt, zahlt fuer eine
        // Ranke ohne Gluehbeeren. Die Reihenfolge im Zweig ist darum keine Geschmacksfrage.
        griff.art = Material.CAVE_VINES;
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("bauer"));

        // Volles Alter, aber KEINE Beeren: Eine Alterspruefung allein wuerde hier zahlen.
        Serverprobe.beeren(MARKTSTADT, 7, 71, 13, false, 25, 25);
        wache.beimAbbauen(abbauen(7, 71, 13));
        assertEquals(0L, buch.rest(anna.kennung()), "ohne Beeren ist nichts zu ernten");

        // Beeren bei KLEINEM Alter: Eine Alterspruefung allein wuerde hier nicht zahlen.
        Serverprobe.beeren(MARKTSTADT, 8, 71, 13, true, 3, 25);
        wache.beimAbbauen(abbauen(8, 71, 13));
        assertEquals(180L, buch.rest(anna.kennung()), "mit Beeren zahlt sie");
    }

    @Test
    @DisplayName("Ein Block OHNE Alter ist reif - dort ist die Marke die Bremse")
    void ohneAlterIstReif() {
        // Kuerbis, Melone, Zuckerrohr, Bambus, Kaktus und Seetang haben keinen
        // Reifezustand. Eine Pruefung, die dort "unreif" annahm, liesse sie nie zahlen -
        // und der halbe Acker waere stumm wertlos.
        griff.art = Material.STONE;
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch);

        wache.beimAbbauen(abbauen(7, 71, 13));

        assertEquals(40L, buch.rest(anna.kennung()),
                "kein Alter heisst nicht unreif - hier zahlt der Abbausatz");
    }

    // --- FISCHEN: drei Tore, und jedes schliesst etwas anderes ---------------

    @Test
    @DisplayName("Ein Fang im offenen Wasser zahlt")
    void fangZahlt() {
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("fischer"));

        wache.beimFischen(angeln(PlayerFishEvent.State.CAUGHT_FISH, true,
                Wesenprobe.gegenstand(Material.COD)));

        assertEquals(800L, buch.rest(anna.kennung()));
        assertEquals(List.of("&a+8,00 Mark"), anna.handbalkenBild());
    }

    @Test
    @DisplayName("Im Ein-Block-Loch zahlt kein Fang")
    void nichtImOffenenWasser() {
        // Das Tor gegen den Klickautomaten am Wasserloch: Ein 1x1-Loch ist kein offenes
        // Wasser. Dass ein Automat auch im Meer angeln kann, faengt die Stundenmenge -
        // eine Bremse allein reicht hier nicht.
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("fischer"));

        wache.beimFischen(angeln(PlayerFishEvent.State.CAUGHT_FISH, false,
                Wesenprobe.gegenstand(Material.COD)));

        assertEquals(0L, buch.rest(anna.kennung()));
    }

    @Test
    @DisplayName("Die sieben anderen Angelzustaende zahlen nichts - und werfen nicht")
    void andereAngelzustaende() {
        // In sechs der acht Zustaende ist getCaught() null, und bei CAUGHT_ENTITY ist es
        // ein Wesen. Ein Zweig, der ohne Pruefung castet, wirft aus einem MONITOR-Zweig -
        // je Angelwurf eine Stapelspur im Serverprotokoll, von aussen unsichtbar.
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("fischer"));

        for (PlayerFishEvent.State zustand : PlayerFishEvent.State.values()) {
            if (zustand == PlayerFishEvent.State.CAUGHT_FISH) {
                continue;
            }
            wache.beimFischen(angeln(zustand, true, null));
        }

        assertEquals(0L, buch.rest(anna.kennung()));

        // UND der Fall, den NUR das Zustandstor faengt: ein richtiger Gegenstand in
        // einem anderen Zustand. Ohne diese Zeile war das Tor ungeprueft - gegen ein
        // null schuetzt schon das instanceof darunter, und eine Einspritzung, die das
        // Tor entfernte, blieb gruen. Gefunden hat das die Einspritzung E6.
        for (PlayerFishEvent.State zustand : PlayerFishEvent.State.values()) {
            if (zustand == PlayerFishEvent.State.CAUGHT_FISH) {
                continue;
            }
            wache.beimFischen(angeln(zustand, true,
                    Wesenprobe.gegenstand(Material.COD)));
        }

        assertEquals(0L, buch.rest(anna.kennung()),
                "nur CAUGHT_FISH ist ein Fang - alles andere zahlt nichts, auch wenn ein"
                        + " Gegenstand daran haengt");
    }

    @Test
    @DisplayName("Ein gehakter Tintenfisch ist kein Fang")
    void tintenfischIstKeinFang() {
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("fischer"));

        wache.beimFischen(angeln(PlayerFishEvent.State.CAUGHT_FISH, true,
                Wesenprobe.wesenAmHaken()));

        assertEquals(0L, buch.rest(anna.kennung()));
    }

    // --- TOETEN: der Entstehungsgrund ist die Marke fuer Wesen ---------------

    @Test
    @DisplayName("Ein natuerliches Monster zahlt")
    void natuerlichesMonsterZahlt() {
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("jaeger"));

        wache.beimToeten(sterben(EntityType.ZOMBIE, anna.spieler(),
                CreatureSpawnEvent.SpawnReason.NATURAL));

        assertEquals(2000L, buch.rest(anna.kennung()));
        assertEquals(List.of("toeten/" + VerdienstbuchTest.MONSTER),
                buch.gezaehlteArten(anna.kennung()));
    }

    @Test
    @DisplayName("Ohne Toeter zahlt niemand - Fall, Lava, Kaktus")
    void ohneToeterNichts() {
        // getKiller() ist null, wenn kein Spieler zugeschrieben wird. Das ist dieselbe
        // Zuschreibung, nach der das Spiel selbst Beute und Erfahrung verteilt.
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("jaeger"));

        wache.beimToeten(sterben(EntityType.ZOMBIE, null,
                CreatureSpawnEvent.SpawnReason.NATURAL));

        assertEquals(0L, buch.rest(anna.kennung()));
    }

    @Test
    @DisplayName("Eine SPAWNERFARM zahlt nichts")
    void spawnerfarmZahltNichts() {
        // Die Marke fuer Wesen. Ohne sie waere jede Spawnerfarm eine Gelddruckmaschine:
        // Die Stufen daempfen nur, und ihr Boden zahlt unbegrenzt weiter.
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("jaeger"));

        for (CreatureSpawnEvent.SpawnReason grund
                : new CreatureSpawnEvent.SpawnReason[] {
                        CreatureSpawnEvent.SpawnReason.SPAWNER,
                        CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER,
                        CreatureSpawnEvent.SpawnReason.SPAWNER_EGG}) {
            wache.beimToeten(sterben(EntityType.ZOMBIE, anna.spieler(), grund));
        }

        assertEquals(0L, buch.rest(anna.kennung()));
    }

    @Test
    @DisplayName("Ein GEZUECHTETES Tier zahlt beim Tod nichts - es ist schon bezahlt")
    void gezuechtetesTierZahltNichtZweimal() {
        // Die Zuchtschleife, und sie ist die lukrativste der gefundenen: Zuechten zahlt
        // bei der Geburt, Toeten beim Tod DESSELBEN Tieres - zwei Loehne fuer eine
        // Handlung, zwoelf Mal je Stunde und Paar. Der Grund breeding steht nicht in der
        // Weissliste, und damit ist sie zu.
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("jaeger", "bauer"));

        wache.beimToeten(sterben(EntityType.COW, anna.spieler(),
                CreatureSpawnEvent.SpawnReason.BREEDING));

        assertEquals(0L, buch.rest(anna.kennung()));
    }

    @Test
    @DisplayName("Ein unbekannter Entstehungsgrund zahlt nichts")
    void ohneGrundNichts() {
        // Die sichere Richtung: Wer den Grund nicht kennt, kann nicht wissen, ob das
        // Wesen schon bezahlt wurde.
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("jaeger"));

        wache.beimToeten(sterben(EntityType.ZOMBIE, anna.spieler(), null));

        assertEquals(0L, buch.rest(anna.kennung()));
    }

    // --- ZUECHTEN -----------------------------------------------------------

    @Test
    @DisplayName("Eine Zucht zahlt - und der Name ist das JUNGE")
    void zuchtZahlt() {
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("bauer"));

        // Das JUNGE ist die Kuh, die Eltern sind Pferde. Nur die Kuh steht in der
        // Lohnliste dieser Probe - ein Zweig, der ein Elternteil liest, bekommt hier
        // also NULL und nicht denselben Betrag.
        wache.beimZuechten(zuechten(EntityType.COW, EntityType.HORSE, anna.spieler()));

        assertEquals(3500L, buch.rest(anna.kennung()));
        assertEquals(List.of("zuechten/" + VerdienstbuchTest.KALB),
                buch.gezaehlteArten(anna.kennung()));
    }

    @Test
    @DisplayName("Eine Zucht ohne Spieler zahlt nichts - Dorfbewohner vermehren sich selbst")
    void zuchtOhneSpieler() {
        // getBreeder() ist dann null, und der Rueckgabetyp ist nur ein LivingEntity -
        // ein Cast auf Player waere eine ClassCastException mit Ansage.
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch, berufe("bauer"));

        wache.beimZuechten(zuechten(EntityType.COW, EntityType.HORSE, null));

        assertEquals(0L, buch.rest(anna.kennung()));
    }

    @Test
    @DisplayName("Ein Abgang rechnet sofort ab")
    void abgangRechnetAb() {
        griff.art = Material.DIAMOND_ORE;
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch);
        wache.beimAbbauen(abbauen(7, 71, 13));

        wache.beimAbgang(new PlayerQuitEvent(anna.spieler(), (Component) null));

        assertEquals(10L, kasse.guthaben(anna.kennung()),
                "sofort und nicht erst im naechsten Takt");
        assertEquals(0L, buch.rest(anna.kennung()));
        assertEquals(List.of(), buch.gezaehlteArten(anna.kennung()));
    }

    @Test
    @DisplayName("Der Zeitgeber wird einmal gestellt, und ein zweiter nicht")
    void zeitgeberStehtGenauEinmal() {
        // Ein zweiter Zeitgeber buchte denselben Verdienst doppelt - zweimal dieselbe
        // Summe, und die Zusage "Summe aller Buchungen gleich Kontostand" waere mit Geld
        // gebrochen, das niemand erarbeitet hat.
        var wache = wache(buch(Verdienstwerte.VORGABE_TAGESDECKEL));

        wache.starte();

        assertEquals(1, takt.gestellt);
        assertEquals(Verdienstwache.TAKT_TICKS, takt.abstand, "jede Sekunde");
        assertNotNull(takt.schlag);
        assertThrows(IllegalStateException.class, wache::starte);
        assertEquals(1, takt.gestellt);
    }

    @Test
    @DisplayName("Der Schlag des Zeitgebers bucht - hoechstens JE_SCHLAG Spieler")
    void derSchlagBucht() {
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch);
        wache.starte();
        griff.art = Material.DIAMOND_ORE;
        List<Spielerprobe> vier = new ArrayList<>();
        for (int wer = 10; wer < 14; wer++) {
            Spielerprobe einer = new Spielerprobe("Nr" + wer,
                    Spielerprobe.kennungAus(wer)).inWelt(marktstadt);
            vier.add(einer);
            berufe.waehle(new Berufsablage.Antrag(einer.kennung(), "bergmann",
                    uhr.instant()), Berufsstand.leer(einer.kennung()));
            wache.beimAbbauen(new BlockBreakEvent(
                    marktstadt.getBlockAt(wer, 71, 13), einer.spieler()));
        }

        takt.schlag.run();

        long gebucht = 0L;
        for (Spielerprobe einer : vier) {
            gebucht += kasse.guthaben(einer.kennung());
        }
        assertEquals(10L * Verdienstwache.JE_SCHLAG, gebucht,
                "ein Schlag bucht nicht mehr als JE_SCHLAG Spieler");

        takt.schlag.run();
        gebucht = 0L;
        for (Spielerprobe einer : vier) {
            gebucht += kasse.guthaben(einer.kennung());
        }
        assertEquals(40L, gebucht, "was ein Schlag nicht bucht, bucht der naechste");
    }

    @Test
    @DisplayName("Beim Beenden wird abgemeldet UND alles gebucht")
    void beendenMeldetAbUndBucht() {
        // Erst abmelden, dann buchen: Sonst kaeme mitten im letzten Abrechnen noch ein
        // Schlag dazu. Und ohne das Buchen waere der letzte Verdienst jedes anwesenden
        // Spielers verloren - bei einem Neustart zur vollen Stunde jeden Tag.
        griff.art = Material.DIAMOND_ORE;
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = wache(buch);
        wache.starte();
        wache.beimAbbauen(abbauen(7, 71, 13));

        wache.beende();

        assertEquals(1, takt.abgemeldet);
        assertEquals(10L, kasse.guthaben(anna.kennung()));
        assertTrue(protokollzeilen.stream().anyMatch(satz ->
                        satz.getMessage() != null
                                && satz.getMessage().contains("Mark Verdienst")),
                protokollzeilen.stream().map(LogRecord::getMessage).toList().toString());
        // Zweimal beenden meldet nicht zweimal ab - onDisable kann nach einem Fehlschlag
        // beim Start durchlaufen, ohne dass starte() je gerufen wurde.
        wache.beende();
        assertEquals(1, takt.abgemeldet);
    }

    @Test
    @DisplayName("Beenden ohne Start wirft nicht")
    void beendenOhneStart() {
        var wache = wache(buch(Verdienstwerte.VORGABE_TAGESDECKEL));

        wache.beende();

        assertEquals(0, takt.abgemeldet);
    }

    @Test
    @DisplayName("Eine Taktquelle ohne Zeitgeber ist ein Fehler und kein stilles Nichts")
    void taktquelleOhneZeitgeber() {
        // Ein stilles null hiesse: Die Wache hoert weiter auf Bloecke, sammelt Pfennige -
        // und bucht nie. Der Spieler sieht seinen Handbalken und ein leeres Konto.
        var wache = new Verdienstwache((schlag, abstand) -> null,
                buch(Verdienstwerte.VORGABE_TAGESDECKEL), marken, berufe, stand(),
                Set.of(MARKTSTADT), Sprachprobe.spracheAusDemJar(), protokoll);

        assertThrows(IllegalStateException.class, wache::starte);
    }

    @Test
    @DisplayName("Der Abbauzweig laeuft auf MONITOR mit ignoreCancelled")
    void abbauzweigLaeuftAufMonitor() throws Exception {
        // Dieselbe Pruefung wie bei der Setzwache, und derselbe Grund: Die Schutzwache
        // laeuft auf NORMAL und bricht ab, und nach ihr kann ein anderer Hoerer noch
        // abbrechen. Auf NORMAL bezahlte diese Wache Arbeit, die gar nicht stattfand -
        // ein Spieler bekaeme Geld fuer jeden Versuch, in einer fremden Parzelle zu
        // graben.
        Method zweig = Verdienstwache.class.getMethod("beimAbbauen",
                BlockBreakEvent.class);
        EventHandler haken = zweig.getAnnotation(EventHandler.class);

        assertNotNull(haken, "beimAbbauen braucht ein @EventHandler");
        assertEquals(EventPriority.MONITOR, haken.priority());
        assertTrue(haken.ignoreCancelled(), "ein abgebrochener Abbau darf nichts zahlen");
    }

    @Test
    @DisplayName("Der Abgangszweig hoert OHNE ignoreCancelled - ein Abgang bricht nicht ab")
    void abgangOhneIgnoreCancelled() {
        // Benannt statt stillschweigend: PlayerQuitEvent ist nicht abbrechbar.
        // ignoreCancelled daran waere eine Behauptung ueber ein Ereignis, das es so nicht
        // gibt - und beim naechsten Leser eine Frage.
        EventHandler haken = null;
        for (Method zweig : Verdienstwache.class.getDeclaredMethods()) {
            if (zweig.getName().equals("beimAbgang")) {
                haken = zweig.getAnnotation(EventHandler.class);
            }
        }

        assertNotNull(haken);
        assertEquals(EventPriority.NORMAL, haken.priority());
        assertFalse(haken.ignoreCancelled());
    }

    @Test
    @DisplayName("Genau FUENF Ereigniszweige, und jeder ist benannt")
    void genauFuenfZweige() {
        // Eine Zahl, die mitwachsen MUSS: Wer hier einen Zweig hinzufuegt, ohne eine
        // Probe dafuer zu schreiben, laeuft hier auf. Das ist der Zweck - in diesen
        // Zweigen entsteht Geld.
        List<String> zweige = new ArrayList<>();
        for (Method zweig : Verdienstwache.class.getDeclaredMethods()) {
            if (zweig.getAnnotation(EventHandler.class) != null) {
                zweige.add(zweig.getName());
            }
        }

        assertEquals(5, zweige.size(), zweige.toString());
        for (String name : new String[] {"beimAbbauen", "beimFischen", "beimToeten",
                                         "beimZuechten", "beimAbgang"}) {
            assertTrue(zweige.contains(name), name + " fehlt: " + zweige);
        }
    }

    @Test
    @DisplayName("Jeder ZAHLENDE Zweig laeuft auf MONITOR mit ignoreCancelled")
    void jederZahlendeZweigAufMonitor() {
        // Nicht nur der Abbauzweig: Die Schutzwache laeuft auf NORMAL und bricht ab, und
        // nach ihr kann ein anderer Hoerer noch abbrechen. Auf NORMAL bezahlte diese
        // Wache Arbeit, die gar nicht stattfand - und zwar in JEDEM der vier zahlenden
        // Zweige. Der Abgang ist ausgenommen: Er laesst sich nicht abbrechen.
        for (Method zweig : Verdienstwache.class.getDeclaredMethods()) {
            EventHandler haken = zweig.getAnnotation(EventHandler.class);
            if (haken == null || zweig.getName().equals("beimAbgang")) {
                continue;
            }
            assertEquals(EventPriority.MONITOR, haken.priority(), zweig.getName());
            assertTrue(haken.ignoreCancelled(), zweig.getName()
                    + " muss ignoreCancelled tragen - ein abgebrochenes Ereignis darf"
                    + " nichts zahlen");
        }
    }

    @Test
    @DisplayName("JEDE Taetigkeit hat ihr Wort in der AUSGELIEFERTEN Vorlage")
    void jedeTaetigkeitHatEinWort() {
        // Die Deckung fuer die Wortfamilie beruf.taetigkeit: Sie wird von keinem Zweig
        // namentlich geholt, sondern ueber BerufSatz.taetigkeitswort - und darum steht
        // sie in Sprachschluessel.familienDeckung(), also auf der Ausnahmeliste von
        // GrenzeTest 30. Diese Probe ist der Gegenwert dafuer: Sie holt JEDES Wort aus
        // der echten Datei. Ohne sie koennte eine Zeile dort fehlen, und der Handbalken
        // an der Stundenmenge bliebe stumm - bei genau der Taetigkeit, die neu ist.
        Sprache sprache = Sprachprobe.spracheAusDemJar();
        for (Taetigkeit taetigkeit : Taetigkeit.values()) {
            BerufSatz satz = BerufSatz.taetigkeitswort(taetigkeit);
            assertEquals("beruf.taetigkeit." + taetigkeit.schluessel(), satz.pfad());
            String wort = sprache.wort(satz, Einsatz.keine());
            assertFalse(wort.isBlank(), taetigkeit.schluessel()
                    + " hat kein Wort in der Vorlage");
        }
    }

    @Test
    @DisplayName("Taktquelle, Sammler, Marken, Ablage, Sprache und Protokoll sind Pflicht")
    void pflichtenGelten() {
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var sprache = Sprachprobe.spracheAusDemJar();
        Set<String> welten = Set.of(MARKTSTADT);

        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(null, buch,
                marken, berufe, stand(), welten, sprache,
                protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, null,
                marken, berufe, stand(), welten, sprache,
                protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, buch,
                null, berufe, stand(), welten, sprache,
                protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, buch,
                marken, null, stand(), welten, sprache,
                protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, buch,
                marken, berufe, stand(), welten, null,
                protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, buch,
                marken, berufe, stand(), welten, sprache,
                null));
        // Und die Welten: Eine leere Menge ist ein Zweig, der nie etwas tut - niemand
        // verdiente etwas, und niemand merkte es, bis jemand nach einer Stunde fragt.
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, buch,
                marken, berufe, stand(), null, sprache,
                protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, buch,
                marken, berufe, stand(), Set.of(), sprache,
                protokoll));
    }

    @Test
    @DisplayName("Die Weltmenge wird KOPIERT - eine Aenderung von aussen wirkt nicht")
    void weltmengeWirdKopiert() {
        // Sonst koennte ein Aufrufer nach dem Bauen eine Welt hinzufuegen, in der nicht
        // markiert wird - und genau das ist die Gelddruckmaschine, gegen die die geteilte
        // Weltmenge gebaut ist.
        Set<String> veraenderlich = new java.util.HashSet<>(Set.of(MARKTSTADT));
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = new Verdienstwache(takt, buch, marken, berufe, stand(),
                veraenderlich, Sprachprobe.spracheAusDemJar(), protokoll);
        World fremde = Serverprobe.welt(FREMD, griff);
        veraenderlich.add(FREMD);

        Spielerprobe bert = new Spielerprobe("Bert", Spielerprobe.kennungAus(2))
                .inWelt(fremde);
        berufe.waehle(new Berufsablage.Antrag(bert.kennung(), "bergmann", uhr.instant()),
                Berufsstand.leer(bert.kennung()));
        wache.beimAbbauen(new BlockBreakEvent(fremde.getBlockAt(7, 71, 13),
                bert.spieler()));

        assertEquals(0L, buch.rest(bert.kennung()));
    }

    /** Eine Berufsablage, die bei jedem Lesen wirft. */
    private static final class KaputteAblage implements Berufsablage {

        @Override
        public Berufsstand lies(UUID spieler) {
            throw new IllegalStateException("die Datenbank ist weg");
        }

        @Override
        public boolean waehle(Antrag antrag, Berufsstand erwartet) {
            throw new IllegalStateException("die Datenbank ist weg");
        }

        @Override
        public boolean verlasse(Antrag antrag, Berufsstand erwartet) {
            throw new IllegalStateException("die Datenbank ist weg");
        }
    }
}
