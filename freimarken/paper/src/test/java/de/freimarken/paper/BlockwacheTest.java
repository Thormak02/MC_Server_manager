package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockbuchwerte;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Blocktat;
import de.freimarken.kern.Blockzeile;
import de.freimarken.kern.SpeicherBlockbuch;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die drei Haken des Blockbuchs - und die Tore, die sonst niemand bewacht.
 *
 * <p>Zwei Zusagen dieser Wache liegen ALLEIN hier, und beide sind unsichtbar, wenn sie
 * brechen:
 * <ul>
 *   <li><b>{@link EventPriority#MONITOR} OHNE {@code ignoreCancelled}.</b> Mit
 *       {@code ignoreCancelled = true} verschwinden genau die abgewiesenen Versuche aus
 *       dem Buch - der Fund, nach dem die Moderation sucht. Alles andere laeuft weiter,
 *       das Buch ist voll, und niemand merkt, dass die eine Sorte Zeile fehlt.
 *       <p><b>Gesichert wird das ALLEIN von
 *       {@link #jederZweigAufMonitorOhneIgnoreCancelled}, und das ist gemessen</b>
 *       (Einspritzung B5 am 09.11.2026): Jene Probe wurde rot,
 *       {@link #abgewiesenesSetzenKommtInsBuch} blieb gruen. Der Grund ist der Bau jeder
 *       Verhaltensprobe hier: Sie ruft {@code wache.beimSetzen(...)} UNMITTELBAR auf, und
 *       dabei liest niemand die Anmerkung - {@code ignoreCancelled} wertet Bukkit beim
 *       Verteilen aus, nicht die Methode. Eine Verhaltensprobe kann diese Zusage also
 *       grundsaetzlich nicht halten; nur der Blick auf die Anmerkung selbst kann es.</li>
 *   <li><b>Nur die Haupthand.</b> {@link PlayerInteractEvent} feuert fuer BEIDE Haende.
 *       Ohne das Tor stuende jede Truhe zweimal im Buch, auf die Millisekunde gleich -
 *       Zeilen, die echt aussehen, und eine Zaehlung, die das Doppelte ergibt
 *       ({@link #nebenhandSchreibtNichts}, gemessen gefangen).</li>
 * </ul>
 */
@DisplayName("Blockwache")
class BlockwacheTest {

    private static final String ALTFURT = "freimarken:altfurt";
    private static final String NETHER = "freimarken:nether";

    /** Ein Nachmittag - feste Uhr, damit der Zeitpunkt vergleichbar ist. */
    private static final Instant WANN = Instant.parse("2026-11-09T14:30:00Z");

    /** Was an welcher Stelle steht - alles ohne Eintrag ist Stein. */
    private final Map<String, Material> boden = new HashMap<>();

    private World altfurt;
    private World nether;
    private SpeicherBlockbuch buch;
    private Blockwache wache;

    /** Was die Taktquelle gestellt hat - die Probe schlaegt selbst. */
    private Runnable schlag;

    /** Wie oft der Zeitgeber abgemeldet wurde. */
    private int abgemeldet;

    private final Weltgriff griff = new Weltgriff() {
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
            return boden.getOrDefault(x + "/" + y + "/" + z, Material.STONE);
        }

        @Override
        public void setze(int x, int y, int z, Material block) {
            boden.put(x + "/" + y + "/" + z, block);
        }
    };

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        boden.clear();
        altfurt = Serverprobe.welt(ALTFURT, griff);
        nether = Serverprobe.welt(NETHER, griff);
        buch = new SpeicherBlockbuch();
        schlag = null;
        abgemeldet = 0;
        wache = baue(werte(EnumSet.of(Blocktat.SETZEN, Blocktat.ABBAUEN,
                Blocktat.BENUTZEN), true, Set.of("chest")));
    }

    /** Werte, in denen altfurt die genannten Taten schreibt und der Nether nichts. */
    private static Blockbuchwerte werte(Set<Blocktat> taten, boolean auchAbgewiesen,
                                        Set<String> benutzbar) {
        return new Blockbuchwerte(30, 500,
                new Blockbuchwerte.Auswahl(taten, auchAbgewiesen),
                Map.of(NETHER, new Blockbuchwerte.Auswahl(Set.of(), false)),
                benutzbar, Blockbuchwerte.VORGABE_REICHWEITE,
                Blockbuchwerte.VORGABE_ZEILEN);
    }

    private Blockwache baue(Blockbuchwerte werte) {
        return new Blockwache((was, abstand) -> {
            schlag = was;
            return () -> abgemeldet++;
        }, new Blockbuch(buch, Logger.getLogger("Probe")), werte,
                Clock.fixed(WANN, ZoneOffset.UTC), Logger.getLogger("Probe"));
    }

    private Spielerprobe cara() {
        return new Spielerprobe("Cara", Spielerprobe.kennungAus(1));
    }

    /**
     * Ein Setzereignis - was die Wache nicht liest, ist null.
     *
     * <p>Mit {@link EquipmentSlot} am Ende, und das ist kein Beiwerk: Die Fassung ohne
     * Hand ist in Bukkit zum ENTFERNEN markiert, und dieses Projekt uebersetzt mit
     * {@code -Xlint:all} ohne eine Warnung und ohne ein einziges
     * {@code SuppressWarnings}.
     */
    private BlockPlaceEvent setzen(World welt, int x, int y, int z) {
        // DER ZWEITE WERT IST DER UEBERBAUTE ZUSTAND, und er stand hier bis zum
        // 26.11.2026 auf null. Das Blockbuch liest ihn seit dem Rueckweg: Er
        // ist, was ein zurueckgenommenes Setzen wiederherstellt. Mit null lief
        // die Wache in eine NullPointerException - auf MONITOR, also mitten im
        // Ereignis.
        return new BlockPlaceEvent(welt.getBlockAt(x, y, z),
                welt.getBlockAt(x, y, z).getState(),
                welt.getBlockAt(x, y - 1, z), null, cara().spieler(), true,
                EquipmentSlot.HAND);
    }

    private BlockBreakEvent abbauen(World welt, int x, int y, int z) {
        return new BlockBreakEvent(welt.getBlockAt(x, y, z), cara().spieler());
    }

    private PlayerInteractEvent benutzen(World welt, int x, int y, int z,
                                         EquipmentSlot hand) {
        return new PlayerInteractEvent(cara().spieler(), Action.RIGHT_CLICK_BLOCK, null,
                welt.getBlockAt(x, y, z), null, hand);
    }

    /** Was im Buch steht - erst nach einem Takt, denn geschrieben wird gesammelt. */
    private List<String> zeilen(Blockort ort) {
        if (schlag != null) {
            schlag.run();
        }
        List<String> gelesen = new ArrayList<>();
        for (Blockzeile zeile : buch.amOrt(ort, 50)) {
            gelesen.add(zeile.tat().schluessel() + " " + zeile.block()
                    + (zeile.gelungen() ? " ja" : " NEIN"));
        }
        return gelesen;
    }

    private static Blockort ort(String welt, int x, int y, int z) {
        return new Blockort(welt, x, y, z);
    }

    @Test
    @DisplayName("Ein gesetzter Block kommt mit allen Werten ins Buch")
    void gesetzterBlockKommtInsBuch() {
        wache.starte();

        wache.beimSetzen(setzen(altfurt, 7, -60, 13));

        schlag.run();
        List<Blockzeile> gelesen = buch.amOrt(ort(ALTFURT, 7, -60, 13), 20);
        assertEquals(1, gelesen.size());
        Blockzeile zeile = gelesen.get(0);
        assertEquals(WANN, zeile.zeitpunkt(), "die Uhr kommt von aussen");
        assertEquals(cara().spieler().getUniqueId(), zeile.spieler());
        assertEquals(ort(ALTFURT, 7, -60, 13), zeile.ort());
        assertEquals(Blocktat.SETZEN, zeile.tat());
        assertTrue(zeile.gelungen());
        assertEquals("stone", zeile.block(), "kleingeschrieben, wie in der Lohnliste");
    }

    @Test
    @DisplayName("Im Ereignis wird NICHT geschrieben - erst der Takt schreibt")
    void imEreignisWirdNichtGeschrieben() {
        // Die Regel, die sich dieses Projekt selbst gegeben hat: keine Netzrunde im
        // Ereignis. Zwanzig Spieler im Tagebau sind mehrere hundert Bloecke je Sekunde,
        // und jeder einzelne waere ein INSERT auf der einen Verbindung, im Hauptfaden.
        wache.starte();

        wache.beimSetzen(setzen(altfurt, 7, -60, 13));

        assertEquals(0, buch.anzahl(), "noch nichts in der Ablage");
        assertEquals(1, wache.imStapel(), "aber auf dem Stapel");

        schlag.run();

        assertEquals(1, buch.anzahl());
        assertEquals(0, wache.imStapel(), "und der Stapel ist leer");
    }

    @Test
    @DisplayName("Ein ABGEWIESENES Setzen kommt ins Buch - der Fund, bei dem nichts geschah")
    void abgewiesenesSetzenKommtInsBuch() {
        // DIE ZUSAGE, DIE ALLEIN HIER STEHT: MONITOR ohne ignoreCancelled. Mit
        // ignoreCancelled = true verschwinden genau diese Zeilen - und zwar lautlos. Das
        // Buch bleibt voll, jede andere Probe bleibt gruen, und die Moderation sucht
        // vergeblich nach dem Dutzend Fehlversuche an einer fremden Parzelle.
        wache.starte();
        BlockPlaceEvent ereignis = setzen(altfurt, 7, -60, 13);
        ereignis.setCancelled(true);

        wache.beimSetzen(ereignis);

        assertEquals(List.of("setzen stone NEIN"), zeilen(ort(ALTFURT, 7, -60, 13)));
    }

    @Test
    @DisplayName("Ein abgewiesener Abbau kommt ebenso ins Buch")
    void abgewiesenerAbbauKommtInsBuch() {
        wache.starte();
        BlockBreakEvent ereignis = abbauen(altfurt, 7, -60, 13);
        ereignis.setCancelled(true);

        wache.beimAbbauen(ereignis);

        assertEquals(List.of("abbauen stone NEIN"), zeilen(ort(ALTFURT, 7, -60, 13)));
    }

    @Test
    @DisplayName("Ohne abgewiesen bleibt der abgebrochene Versuch draussen")
    void ohneAbgewiesenKeineAbgelehnten() {
        wache = baue(werte(EnumSet.of(Blocktat.SETZEN), false, Set.of("chest")));
        wache.starte();
        BlockPlaceEvent abgewiesen = setzen(altfurt, 7, -60, 13);
        abgewiesen.setCancelled(true);

        wache.beimSetzen(abgewiesen);
        wache.beimSetzen(setzen(altfurt, 8, -60, 13));

        assertEquals(List.of(), zeilen(ort(ALTFURT, 7, -60, 13)),
                "derselbe Zweig, NUR der Ausgang ist anders - und die Einstellung sagt"
                        + " nein");
        assertEquals(List.of("setzen stone ja"), zeilen(ort(ALTFURT, 8, -60, 13)),
                "das gelungene Setzen steht aber da");
    }

    @Test
    @DisplayName("In einer Welt ohne Blockbuch wird nichts aufgeschrieben")
    void weltOhneBlockbuchSchreibtNichts() {
        wache.starte();

        wache.beimSetzen(setzen(nether, 7, -60, 13));
        wache.beimAbbauen(abbauen(nether, 7, -60, 13));

        assertEquals(0, wache.imStapel(),
                "es entsteht nicht einmal ein Stapeleintrag");
        assertEquals(List.of(), zeilen(ort(NETHER, 7, -60, 13)));
    }

    @Test
    @DisplayName("Eine abgewaehlte Tat wird nicht aufgeschrieben")
    void abgewaehlteTatSchreibtNichts() {
        wache = baue(werte(EnumSet.of(Blocktat.ABBAUEN), true, Set.of("chest")));
        wache.starte();

        wache.beimSetzen(setzen(altfurt, 7, -60, 13));
        wache.beimAbbauen(abbauen(altfurt, 8, -60, 13));

        assertEquals(List.of(), zeilen(ort(ALTFURT, 7, -60, 13)), "setzen fehlt");
        assertEquals(List.of("abbauen stone ja"), zeilen(ort(ALTFURT, 8, -60, 13)));
    }

    @Test
    @DisplayName("Benutzen schreibt nur Bloecke aus der Liste")
    void benutzenNurAusDerListe() {
        boden.put("7/-60/13", Material.CHEST);
        wache.starte();

        wache.beimBenutzen(benutzen(altfurt, 7, -60, 13, EquipmentSlot.HAND));
        wache.beimBenutzen(benutzen(altfurt, 8, -60, 13, EquipmentSlot.HAND));

        assertEquals(List.of("benutzen chest ja"), zeilen(ort(ALTFURT, 7, -60, 13)));
        assertEquals(List.of(), zeilen(ort(ALTFURT, 8, -60, 13)),
                "Stein steht nicht in der Liste - sonst waere jeder Rechtsklick auf Erde"
                        + " eine Zeile, und wer mit einem Block in der Hand baut,"
                        + " erzeugte zwei Zeilen fuer eine Tat");
    }

    @Test
    @DisplayName("Die NEBENHAND schreibt nichts - sonst stuende jede Truhe doppelt")
    void nebenhandSchreibtNichts() {
        // DIE ZWEITE ZUSAGE, DIE ALLEIN HIER STEHT. PlayerInteractEvent feuert fuer
        // BEIDE Haende - genau so, wie es der Server tut, wenn ein Spieler eine Truhe
        // oeffnet. Ohne das Tor stuende sie zweimal im Buch, auf die Millisekunde gleich.
        // Die Zeilen sehen echt aus, nichts bricht, und wer zaehlt, kommt auf das
        // Doppelte.
        boden.put("7/-60/13", Material.CHEST);
        wache.starte();

        wache.beimBenutzen(benutzen(altfurt, 7, -60, 13, EquipmentSlot.HAND));
        wache.beimBenutzen(benutzen(altfurt, 7, -60, 13, EquipmentSlot.OFF_HAND));

        assertEquals(List.of("benutzen chest ja"), zeilen(ort(ALTFURT, 7, -60, 13)),
                "EIN Klick, EINE Zeile - obwohl das Ereignis zweimal kam");
    }

    @Test
    @DisplayName("Ein Linksklick ist kein Benutzen - das waere der Abbau zweimal")
    void linksklickIstKeinBenutzen() {
        boden.put("7/-60/13", Material.CHEST);
        wache.starte();
        var links = new PlayerInteractEvent(cara().spieler(),
                Action.LEFT_CLICK_BLOCK, null, altfurt.getBlockAt(7, -60, 13), null,
                EquipmentSlot.HAND);

        wache.beimBenutzen(links);

        assertEquals(List.of(), zeilen(ort(ALTFURT, 7, -60, 13)),
                "der Linksklick ist der Beginn eines Abbaus, und der hat seinen eigenen"
                        + " Haken");
    }

    @Test
    @DisplayName("Ein verweigerter Block ist abgewiesen - ein verweigerter GEGENSTAND nicht")
    void verweigerterBlockGegenVerweigertenGegenstand() {
        // Dieses Ereignis traegt ZWEI Ergebnisse: eines fuer den Block, eines fuer den
        // Gegenstand in der Hand. Gefragt wird das BLOCKergebnis - ein Spieler, der mit
        // einem unerlaubten Werkzeug eine erlaubte Truhe oeffnet, hat die Truhe
        // geoeffnet, und so steht es im Buch.
        //
        // DIESE PROBE FAENGT DEN TAUSCH GEGEN !isCancelled() NICHT, und das ist gemessen
        // (Einspritzung B13, 09.11.2026): Alle 2.516 Proben blieben gruen, weil die
        // beiden Ausdruecke in Bukkit dasselbe sind - isCancelled() liest genau dieses
        // Blockergebnis. Der naheliegende Gedanke "isCancelled() ist schon wahr, wenn
        // nur der Gegenstand verweigert wurde" ist FALSCH; er stand hier einmal als
        // Begruendung. Was bleibt, ist ein anderer Grund fuer useInteractedBlock(), und
        // er ist handfest: isCancelled() ist bei diesem Ereignis VERALTET.
        boden.put("7/-60/13", Material.CHEST);
        boden.put("8/-60/13", Material.CHEST);
        wache.starte();

        PlayerInteractEvent blockVerweigert =
                benutzen(altfurt, 7, -60, 13, EquipmentSlot.HAND);
        blockVerweigert.setUseInteractedBlock(Event.Result.DENY);
        wache.beimBenutzen(blockVerweigert);

        PlayerInteractEvent nurGegenstand =
                benutzen(altfurt, 8, -60, 13, EquipmentSlot.HAND);
        nurGegenstand.setUseItemInHand(Event.Result.DENY);
        wache.beimBenutzen(nurGegenstand);

        assertEquals(List.of("benutzen chest NEIN"), zeilen(ort(ALTFURT, 7, -60, 13)));
        assertEquals(List.of("benutzen chest ja"), zeilen(ort(ALTFURT, 8, -60, 13)),
                "nur der Gegenstand war verweigert, die Truhe ging auf");
    }

    @Test
    @DisplayName("beende meldet den Zeitgeber ab UND schreibt den Rest")
    void beendeSchreibtDenRest() {
        // Beides, und in dieser Reihenfolge: Erst abmelden, damit nicht mitten im letzten
        // Schreibzug noch ein Schlag dazukommt - dann schreiben, solange die Verbindung
        // noch steht.
        wache.starte();
        wache.beimSetzen(setzen(altfurt, 7, -60, 13));
        assertEquals(1, wache.imStapel());

        wache.beende();

        assertEquals(1, abgemeldet, "der Zeitgeber ist abgemeldet");
        assertEquals(1, buch.anzahl(), "und der Stapel ist geschrieben");
        assertEquals(0, wache.imStapel());
    }

    @Test
    @DisplayName("Ein zweiter Zeitgeber wird abgewiesen")
    void zweiterZeitgeberWirdAbgewiesen() {
        // Ein zweiter schriebe denselben Stapel zweimal - und weil das Blockbuch keinen
        // eindeutigen Schluessel hat, stuende dann jede Tat doppelt darin, ohne dass
        // etwas davor warnt.
        wache.starte();

        assertThrows(IllegalStateException.class, () -> wache.starte());
    }

    @Test
    @DisplayName("Eine Taktquelle ohne Zeitgeber wird abgewiesen")
    void taktquelleOhneZeitgeberWirdAbgewiesen() {
        // Ein stilles null waere der schlechtere Ausgang: Die Wache hoerte weiter auf
        // Bloecke, sammelte Zeilen - und bei abmelden == null liesse sich ein zweiter
        // Zeitgeber stellen.
        Blockwache ohne = new Blockwache((was, abstand) -> null,
                new Blockbuch(buch, Logger.getLogger("Probe")),
                werte(EnumSet.of(Blocktat.SETZEN), true, Set.of()),
                Clock.fixed(WANN, ZoneOffset.UTC), Logger.getLogger("Probe"));

        assertThrows(IllegalStateException.class, ohne::starte);
    }

    @Test
    @DisplayName("Ohne Pflichtangabe wird die Wache nicht gebaut")
    void ohnePflichtangabeWirdAbgewiesen() {
        Blockbuch echtesBuch = new Blockbuch(buch, Logger.getLogger("Probe"));
        Blockbuchwerte echteWerte = werte(EnumSet.of(Blocktat.SETZEN), true, Set.of());
        Clock uhr = Clock.fixed(WANN, ZoneOffset.UTC);

        assertThrows(IllegalArgumentException.class, () -> new Blockwache(null,
                echtesBuch, echteWerte, uhr, Logger.getLogger("Probe")));
        assertThrows(IllegalArgumentException.class, () -> new Blockwache(
                (was, abstand) -> () -> { }, null, echteWerte, uhr,
                Logger.getLogger("Probe")));
        assertThrows(IllegalArgumentException.class, () -> new Blockwache(
                (was, abstand) -> () -> { }, echtesBuch, null, uhr,
                Logger.getLogger("Probe")));
        assertThrows(IllegalArgumentException.class, () -> new Blockwache(
                (was, abstand) -> () -> { }, echtesBuch, echteWerte, null,
                Logger.getLogger("Probe")));
    }

    @Test
    @DisplayName("Jeder zahlende Zweig laeuft auf MONITOR und OHNE ignoreCancelled")
    void jederZweigAufMonitorOhneIgnoreCancelled() throws Exception {
        // DIE QUELLTEXTZUSAGE ZU DEN BEIDEN PROBEN DARUEBER, und sie sichert den Fall,
        // den jene nicht sehen koennen: einen NEUEN Zweig, der die Regel nicht einhaelt.
        // Eine Verhaltensprobe prueft nur die Zweige, die sie kennt; diese hier prueft
        // jeden, den es gibt.
        //
        // Gelesen aus der Anmerkung und nicht aus dem Quelltext: Was zaehlt, ist, was
        // Bukkit sieht.
        int gefunden = 0;
        var ereignisse = new java.util.TreeSet<String>();
        for (var methode : Blockwache.class.getDeclaredMethods()) {
            EventHandler anmerkung = methode.getAnnotation(EventHandler.class);
            if (anmerkung == null) {
                continue;
            }
            gefunden++;
            assertEquals(1, methode.getParameterCount(),
                    methode.getName() + " nimmt nicht genau ein Ereignis");
            ereignisse.add(methode.getParameterTypes()[0].getSimpleName());
            assertEquals(EventPriority.MONITOR, anmerkung.priority(),
                    methode.getName() + " laeuft nicht auf MONITOR - dann steht der"
                            + " Ausgang noch nicht fest, und ein Schutz, der NACH"
                            + " dieser Wache abbricht, stuende als gelungen im Buch");
            assertFalse(anmerkung.ignoreCancelled(),
                    methode.getName() + " hat ignoreCancelled = true - damit"
                            + " verschwinden genau die ABGEWIESENEN Versuche aus dem"
                            + " Buch, und das ist der Fund, nach dem die Moderation"
                            + " sucht. Nichts bricht, nichts wird rot; es fehlt nur eine"
                            + " Sorte Zeile.");
        }
        assertEquals(11, gefunden,
                "erwartet werden ELF Haken. Weniger heisst, dass diese Probe einen"
                        + " Zweig nicht sieht; mehr heisst, dass ein neuer"
                        + " dazugekommen ist und hier mitgezaehlt werden muss."
                        + " Gefunden: " + ereignisse);

        // UND DIE EREIGNISARTEN, nicht nur ihre Zahl: Zwei Zuhoerer auf dasselbe
        // Ereignis waeren ebenfalls elf, und ein vergessenes Ereignis liesse sich mit
        // einem zweiten Haken auf ein schon gedecktes ausgleichen.
        //
        // Die sechs unteren kamen am 27.11.2026 dazu [Betreiber: "ueberall mindestens
        // aufzeichnen"]: Vorher schrieb nur die Sprengung mit, und Feuer, Kolben,
        // Fluessigkeit und Wesen hatten gar keinen Schluessel, in dem eine Zeile
        // stehen koennte.
        assertEquals(java.util.Set.of(
                        "BlockBreakEvent",
                        "BlockBurnEvent",
                        "BlockExplodeEvent",
                        "BlockFromToEvent",
                        "BlockPistonExtendEvent",
                        "BlockPistonRetractEvent",
                        "BlockPlaceEvent",
                        "BlockSpreadEvent",
                        "EntityChangeBlockEvent",
                        "EntityExplodeEvent",
                        "PlayerInteractEvent"),
                ereignisse,
                "Diese Ereignisse schreibt das Blockbuch mit - jedes genau einmal."
                        + " Fehlt eines, steht die Tat im Programm und in der"
                        + " Sprachdatei, aber keine Zeile im Buch; steht eines zu viel"
                        + " darin, gehoert es in diese Tafel.");
        assertNotNull(wache, "die Wache aus dem Aufbau wird hier mitgeprueft");
    }

    @Test
    @DisplayName("Jeder Zweig schreibt seine Tat - und der Sandfall ist ausgenommen")
    void jederZweigSchreibtSeineTat() {
        // DIE TAFEL, DIE DIE UNERREICHBAREN ZWEIGE DECKT. Sieben der elf Zuhoerer
        // nehmen ein Bukkit-Ereignis, das sich ohne laufenden Server nicht bauen
        // laesst. Am 27.11.2026 sind fuenf Einspritzungen genau hier entwischt: Die
        // Aufzeichnung war gebaut, gruen und von keiner Probe gehalten.
        // Map.ofEntries und nicht Map.of: Jenes nimmt hoechstens zehn Paare, und es
        // sind elf Zuhoerer. Der Uebersetzer hat es selbst gesagt.
        Map<String, String> erwartet = Map.ofEntries(
                Map.entry("beimSetzen", "Blocktat.SETZEN"),
                Map.entry("beimAbbauen", "Blocktat.ABBAUEN"),
                Map.entry("beimBenutzen", "Blocktat.BENUTZEN"),
                Map.entry("beimSprengen", "halteSprengungFest("),
                Map.entry("beimBlocksprengen", "halteSprengungFest("),
                Map.entry("beimAbbrennen", "Blocktat.FEUER"),
                Map.entry("beimAusbreiten", "Blocktat.FEUER"),
                Map.entry("beimWesenblock", "Blocktat.WESEN"),
                Map.entry("beimKolbenSchieben", "halteBewegungFest("),
                Map.entry("beimKolbenZiehen", "halteBewegungFest("),
                Map.entry("beimFliessen", "Blocktat.FLUESSIGKEIT"));

        String quelle = Quellen.text(Quellen.alle().stream()
                .filter(d -> d.getFileName().toString().equals("Blockwache.java"))
                .findFirst().orElseThrow());
        String[] stuecke = quelle.split("@EventHandler");
        assertEquals(12, stuecke.length,
                "elf Anmerkungen ergeben zwoelf Stuecke - gefunden: "
                        + (stuecke.length - 1));

        var gesehen = new java.util.TreeSet<String>();
        for (int nummer = 1; nummer < stuecke.length; nummer++) {
            int ende = stuecke[nummer].indexOf("\n    }");
            assertTrue(ende > 0, "Zweig " + nummer + " endet nicht auf der Ebene"
                    + " einer Methode");
            String rumpf = stuecke[nummer].substring(0, ende);
            String name = null;
            for (String kandidat : erwartet.keySet()) {
                if (rumpf.contains(" " + kandidat + "(")) {
                    name = kandidat;
                }
            }
            assertNotNull(name, "Dieser Zuhoerer steht in keiner Zeile der Tafel -"
                    + " seine Tat ist damit unbeaufsichtigt:\n" + rumpf);
            gesehen.add(name);
            assertTrue(rumpf.contains(erwartet.get(name)),
                    name + " schreibt nicht " + erwartet.get(name)
                            + " - entweder die falsche Tat oder gar keine:\n"
                            + rumpf);
        }
        assertEquals(erwartet.keySet(), gesehen,
                "jede Zeile der Tafel braucht ihren Zuhoerer im Quelltext");

        // DER SANDFALL, und er haengt an DERSELBEN Frage wie der Schutz: Ohne diese
        // Zeile stuende in jeder Welt eine Zeile fuer jeden gesetzten Sandblock -
        // und zwar fuer einen Block, den ein SPIELER gesetzt hat.
        int wesen = -1;
        for (int nummer = 1; nummer < stuecke.length; nummer++) {
            if (stuecke[nummer].contains(" beimWesenblock(")) {
                wesen = nummer;
            }
        }
        assertTrue(wesen > 0, "beimWesenblock nicht gefunden");
        assertTrue(stuecke[wesen].contains("Weltwache.wesenZaehlt("),
                "beimWesenblock fragt nicht Weltwache.wesenZaehlt - damit landet"
                        + " jeder landende Sandblock als Wesen im Buch, und eine"
                        + " zweite Liste hier liefe von der des Schutzes weg:\n"
                        + stuecke[wesen].substring(0,
                                Math.min(400, stuecke[wesen].length())));
    }
}
