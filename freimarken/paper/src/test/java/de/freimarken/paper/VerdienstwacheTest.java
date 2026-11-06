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
import de.freimarken.kern.Verdienstwerte;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
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

    private Verdienstbuch buch(long tagesdeckel) {
        return new Verdienstbuch(kasse, VerdienstbuchTest.stand(
                VerdienstbuchTest.werte(tagesdeckel)), uhr, protokoll);
    }

    private Verdienstwache wache(Verdienstbuch buch) {
        return wache(buch, berufe);
    }

    private Verdienstwache wache(Verdienstbuch buch, Berufsablage ablage) {
        return new Verdienstwache(takt, buch, marken, ablage,
                Set.of(MARKTSTADT, HOCHWALD), Sprachprobe.spracheAusDemJar(), protokoll);
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
                buch(Verdienstwerte.VORGABE_TAGESDECKEL), marken, berufe,
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
    @DisplayName("Genau ZWEI Ereigniszweige - Fischen und Toeten kommen spaeter")
    void genauZweiZweige() {
        // Eine Zahl, die mitwachsen MUSS: Wer hier einen Zweig hinzufuegt, ohne eine
        // Probe dafuer zu schreiben, laeuft hier auf. Das ist der Zweck - in diesem Zweig
        // entsteht Geld.
        List<String> zweige = new ArrayList<>();
        for (Method zweig : Verdienstwache.class.getDeclaredMethods()) {
            if (zweig.getAnnotation(EventHandler.class) != null) {
                zweige.add(zweig.getName());
            }
        }

        assertEquals(2, zweige.size(), zweige.toString());
        assertTrue(zweige.contains("beimAbbauen") && zweige.contains("beimAbgang"),
                zweige.toString());
    }

    @Test
    @DisplayName("Taktquelle, Sammler, Marken, Ablage, Sprache und Protokoll sind Pflicht")
    void pflichtenGelten() {
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var sprache = Sprachprobe.spracheAusDemJar();
        Set<String> welten = Set.of(MARKTSTADT);

        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(null, buch,
                marken, berufe, welten, sprache, protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, null,
                marken, berufe, welten, sprache, protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, buch,
                null, berufe, welten, sprache, protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, buch,
                marken, null, welten, sprache, protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, buch,
                marken, berufe, welten, null, protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, buch,
                marken, berufe, welten, sprache, null));
        // Und die Welten: Eine leere Menge ist ein Zweig, der nie etwas tut - niemand
        // verdiente etwas, und niemand merkte es, bis jemand nach einer Stunde fragt.
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, buch,
                marken, berufe, null, sprache, protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Verdienstwache(takt, buch,
                marken, berufe, Set.of(), sprache, protokoll));
    }

    @Test
    @DisplayName("Die Weltmenge wird KOPIERT - eine Aenderung von aussen wirkt nicht")
    void weltmengeWirdKopiert() {
        // Sonst koennte ein Aufrufer nach dem Bauen eine Welt hinzufuegen, in der nicht
        // markiert wird - und genau das ist die Gelddruckmaschine, gegen die die geteilte
        // Weltmenge gebaut ist.
        Set<String> veraenderlich = new java.util.HashSet<>(Set.of(MARKTSTADT));
        var buch = buch(Verdienstwerte.VORGABE_TAGESDECKEL);
        var wache = new Verdienstwache(takt, buch, marken, berufe, veraenderlich,
                Sprachprobe.spracheAusDemJar(), protokoll);
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
