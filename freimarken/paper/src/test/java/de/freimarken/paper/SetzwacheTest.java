package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockort;
import de.freimarken.kern.SpeicherBlockmarken;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Setzwache DURCHLAUFEN - mit echten Setzereignissen.
 *
 * <p><b>Hier wird das Ereignis wirklich gebaut</b>, anders als bei
 * {@link SchutzwacheTest}, und das ist kein Stilbruch, sondern derselbe Grundsatz mit
 * anderem Ergebnis. Jene Probe geht durch den gemeinsamen Hals, weil ein nachgebautes
 * Ereignis dort dieselbe Entscheidung ein zweites Mal prueefte. Hier steckt die
 * Entscheidung IM Ereignis: Die zweite Haelfte einer Tuer gibt es nur in
 * {@link BlockMultiPlaceEvent#getReplacedBlockStates}, und ohne ein echtes Ereignis waere
 * deren Marke eine Behauptung - ein halber Block aus jeder Tuer, der beim Abbauen zahlt.
 *
 * <p>Der Hals {@code merke} wird daneben mit einfachen Werten geprueft: Weltfilter und
 * Zaehlung brauchen kein Ereignis.
 */
@DisplayName("Setzwache")
class SetzwacheTest {

    private static final String MARKTSTADT = "freimarken:marktstadt";
    private static final String HOCHWALD = "freimarken:hochwald";
    private static final String FREMD = "freimarken:anderswo";

    /**
     * Eine Welt, in der ueberall Stein liegt.
     *
     * <p>Mehr braucht diese Probe nicht: Die Wache liest die ART des gesetzten Blocks und
     * gibt sie dem Zahlmeister weiter. Ein ganzer Rasterplan waere hier Beiwerk - was
     * geprueft wird, ist die Weitergabe und nicht der Boden.
     */
    private static final Weltgriff EIN_STEIN = new Weltgriff() {
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
            return Material.STONE;
        }

        @Override
        public void setze(int x, int y, int z, Material block) {
            throw new UnsupportedOperationException("Diese Probe setzt keine Bloecke -"
                    + " sie prueft, was die Wache mit einem gesetzten tut.");
        }
    };

    private SpeicherBlockmarken marken;
    private Setzwache wache;
    private World marktstadt;

    /**
     * Was der Zahlmeister zu sehen bekam - "<art> x<neueMarken>".
     *
     * <p>Mitgeschrieben und nicht weggeworfen: Die Zahl der NEUEN Marken ist das, was
     * Teil E bezahlt. Ein Zahlmeister, der nichts bekommt, waere eine Marke ohne Lohn -
     * und eine Marke, die zweimal gezahlt wird, waere die Gelddruckmaschine.
     */
    private final List<String> gezahlt = new ArrayList<>();

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        // MIT Bloecken, seit die Wache die Blockart an den Zahlmeister weitergibt: Ohne
        // Griff klagt der Blockstellvertreter - und das ist richtig so, denn ein Zweig,
        // der den Boden liest, soll es gesagt bekommen.
        marktstadt = Serverprobe.welt(MARKTSTADT, EIN_STEIN);
        marken = new SpeicherBlockmarken();
        gezahlt.clear();
        wache = new Setzwache(marken, Set.of(MARKTSTADT, HOCHWALD), this::merkeLohn);
    }

    private void merkeLohn(org.bukkit.entity.Player wer, org.bukkit.Material art,
                           int neueMarken) {
        gezahlt.add(art + " x" + neueMarken);
    }

    private static Blockort ort(String welt, int x, int y, int z) {
        return new Blockort(welt, x, y, z);
    }

    /** Ein Setzereignis an dieser Stelle - was die Wache nicht liest, ist null. */
    private BlockPlaceEvent setzen(int x, int y, int z) {
        Spielerprobe cara = new Spielerprobe("Cara", Spielerprobe.kennungAus(1));
        return new BlockPlaceEvent(marktstadt.getBlockAt(x, y, z), null,
                marktstadt.getBlockAt(x, y - 1, z), null, cara.spieler(), true);
    }

    @Test
    @DisplayName("Ein gesetzter Block ist danach markiert")
    void gesetzterBlockIstMarkiert() {
        wache.beimSetzen(setzen(7, 71, 13));

        assertTrue(marken.istMarkiert(ort(MARKTSTADT, 7, 71, 13)));
        assertEquals(1, marken.anzahl(), "und nur der eine");
        // Und der Zahlmeister hat es erfahren: EINE neue Marke, Art STONE. Ohne diese
        // Zeile waere die Marke gesetzt und das erste Setzen unbezahlt.
        assertEquals(List.of("STONE x1"), gezahlt);
    }

    @Test
    @DisplayName("Eine Tuer sind ZWEI Marken - aus den ersetzten Zustaenden")
    void tuerIstZweiMarken() {
        // Das Ereignis, das es nur hier gibt. Ohne getReplacedBlockStates waere die obere
        // Haelfte unmarkiert, und sie brachte beim Abbauen Geld: ein halber Block aus
        // jeder Tuer, aus jedem Bett, aus jeder grossen Blume.
        // MIT Griff: Der Konstruktor von BlockMultiPlaceEvent holt sich den gesetzten
        // Block aus dem ersten Zustand, und die Wache fragt danach seine ART.
        BlockState unten = Serverprobe.zustand(marktstadt, EIN_STEIN, 7, 71, 13);
        BlockState oben = Serverprobe.zustand(marktstadt, EIN_STEIN, 7, 72, 13);
        Spielerprobe cara = new Spielerprobe("Cara", Spielerprobe.kennungAus(1));
        var ereignis = new BlockMultiPlaceEvent(List.of(unten, oben),
                marktstadt.getBlockAt(7, 70, 13), null, cara.spieler(), true);

        wache.beimSetzen(ereignis);

        assertTrue(marken.istMarkiert(ort(MARKTSTADT, 7, 71, 13)), "untere Haelfte");
        assertTrue(marken.istMarkiert(ort(MARKTSTADT, 7, 72, 13)), "obere Haelfte");
        assertEquals(2, marken.anzahl());
        // ZWEI neue Marken in EINEM Aufruf: Eine Tuer sind zwei Bloecke, und sie zahlt
        // zweimal. Zwei Aufrufe mit je einer waeren dasselbe Geld, aber der Zahlmeister
        // koennte die Minderung dann nicht in einem Zug rechnen.
        assertEquals(List.of("STONE x2"), gezahlt);
    }

    @Test
    @DisplayName("Zweimal dasselbe Setzen legt keine zweite Marke an")
    void zweimalIstEineMarke() {
        wache.beimSetzen(setzen(7, 71, 13));
        wache.beimSetzen(setzen(7, 71, 13));

        assertEquals(1, marken.anzahl());
        // Und bezahlt wurde genau EINMAL: Das zweite Setzen bringt keine neue Marke, also
        // auch keinen Lohn. Sonst waere Setzen-Abbauen-Setzen die Gelddruckmaschine.
        assertEquals(List.of("STONE x1"), gezahlt);
    }

    // --- Der Hals, mit einfachen Werten ---------------------------------------

    @Test
    @DisplayName("Markiert wird in JEDER eigenen Welt, nicht nur in der Marktstadt")
    void mehrereWelten() {
        // Der Unterschied zur Schutzwache, und er ist der Grund fuer die eigene Klasse:
        // Bergmann, Holzfaeller, Bauer und Jaeger arbeiten gar nicht in der Marktstadt.
        // Eine Wache, die nur sie sieht, markierte genau dort nicht, wo gearbeitet wird.
        assertEquals(1, wache.merke(List.of(ort(HOCHWALD, 7, 71, 13))));

        assertTrue(marken.istMarkiert(ort(HOCHWALD, 7, 71, 13)));
    }

    @Test
    @DisplayName("Eine fremde Welt wird nicht markiert")
    void fremdeWeltBleibtAus() {
        assertEquals(0, wache.merke(List.of(ort(FREMD, 7, 71, 13))));

        assertEquals(0, marken.anzahl());
    }

    @Test
    @DisplayName("Aus einer gemischten Liste wird nur die eigene Welt markiert")
    void gemischteListe() {
        int neue = wache.merke(List.of(ort(MARKTSTADT, 7, 71, 13),
                ort(FREMD, 7, 71, 13), ort(HOCHWALD, 7, 71, 13)));

        assertEquals(2, neue);
        assertEquals(2, marken.anzahl());
        assertFalse(marken.istMarkiert(ort(FREMD, 7, 71, 13)));
    }

    @Test
    @DisplayName("Gezaehlt werden nur die NEUEN Marken")
    void nurDieNeuen() {
        // Daran haengt mit Teil E die Auszahlung: Eine Tuer, von der eine Haelfte schon
        // steht, zahlt einmal und nicht zweimal.
        wache.merke(List.of(ort(MARKTSTADT, 7, 71, 13)));

        assertEquals(1, wache.merke(List.of(ort(MARKTSTADT, 7, 71, 13),
                ort(MARKTSTADT, 7, 72, 13))));
        assertEquals(0, wache.merke(List.of(ort(MARKTSTADT, 7, 71, 13))));
    }

    @Test
    @DisplayName("Eine leere Liste ist erlaubt und tut nichts")
    void leereListe() {
        assertEquals(0, wache.merke(List.of()));
        assertEquals(0, marken.anzahl());
    }

    @Test
    @DisplayName("Ohne Marken und ohne Welten gibt es keine Wache")
    void pflichtenGelten() {
        assertThrows(IllegalArgumentException.class,
                () -> new Setzwache(null, Set.of(MARKTSTADT), this::merkeLohn));
        assertThrows(IllegalArgumentException.class,
                () -> new Setzwache(marken, null, this::merkeLohn));
        // Eine leere Menge waere ein Zweig, der nie etwas tut - und das faellt erst mit
        // Teil E auf: Dann zahlt jeder Block jedes Mal.
        assertThrows(IllegalArgumentException.class,
                () -> new Setzwache(marken, Set.of(), this::merkeLohn));
        // Und ohne Zahlmeister waere die Marke nur halb: Sie entwertete die Position,
        // ohne das erste Setzen zu bezahlen.
        assertThrows(IllegalArgumentException.class,
                () -> new Setzwache(marken, Set.of(MARKTSTADT), null));
    }

    // --- Die Verdrahtung, aus der Annotation gelesen -------------------------

    @Test
    @DisplayName("Der Zweig laeuft auf MONITOR und laesst Abgebrochenes aus")
    void monitorUndIgnoreCancelled() throws ReflectiveOperationException {
        // Gelesen wird die ANNOTATION und nicht der Quelltext: Bukkit liest sie genauso,
        // also prueft diese Probe dasselbe, was der Server sieht.
        //
        // Warum es zaehlt: Die Schutzwache laeuft auf NORMAL, und nach ihr kann ein
        // anderer Hoerer das Setzen noch abbrechen. Auf NORMAL markierte diese Wache dann
        // Positionen, an denen nie ein Block entstand - der Spieler saehe nichts, und die
        // Position waere fuer immer entwertet. Keine Verhaltensprobe faengt das: Sie ruft
        // den Zweig selbst auf, und dabei gibt es keine Prioritaet.
        EventHandler haken = Setzwache.class
                .getMethod("beimSetzen", BlockPlaceEvent.class)
                .getAnnotation(EventHandler.class);

        assertNotNull(haken, "ohne @EventHandler hoert die Wache gar nichts");
        assertEquals(EventPriority.MONITOR, haken.priority());
        assertTrue(haken.ignoreCancelled(), "ein abgebrochenes Setzen markiert nichts");
    }

    @Test
    @DisplayName("Die Setzwache hoert GENAU EIN Ereignis")
    void nurEinEreignis() {
        // Drei weitere Wege wurden geprueft und bewusst ausgelassen: das Abbauen, der
        // Eimer und der Klickweg. Jeder Grund steht im Kopf der Klasse. Kommt ein zweiter
        // Haken dazu, soll diese Probe rot werden - nicht, weil es verboten waere,
        // sondern weil dann eine Begruendung dazugehoert.
        long haken = java.util.Arrays.stream(Setzwache.class.getMethods())
                .filter(methode -> methode.getAnnotation(EventHandler.class) != null)
                .count();

        assertEquals(1L, haken, "ein Haken - siehe die Begruendung im Klassenkopf");
    }
}
