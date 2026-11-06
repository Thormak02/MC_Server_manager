package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockort;
import de.freimarken.kern.SpeicherBlockmarken;
import java.util.List;
import java.util.Set;
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

    private SpeicherBlockmarken marken;
    private Setzwache wache;
    private World marktstadt;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        marktstadt = Serverprobe.welt(MARKTSTADT);
        marken = new SpeicherBlockmarken();
        wache = new Setzwache(marken, Set.of(MARKTSTADT, HOCHWALD));
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
    }

    @Test
    @DisplayName("Eine Tuer sind ZWEI Marken - aus den ersetzten Zustaenden")
    void tuerIstZweiMarken() {
        // Das Ereignis, das es nur hier gibt. Ohne getReplacedBlockStates waere die obere
        // Haelfte unmarkiert, und sie brachte beim Abbauen Geld: ein halber Block aus
        // jeder Tuer, aus jedem Bett, aus jeder grossen Blume.
        BlockState unten = Serverprobe.zustand(marktstadt, null, 7, 71, 13);
        BlockState oben = Serverprobe.zustand(marktstadt, null, 7, 72, 13);
        Spielerprobe cara = new Spielerprobe("Cara", Spielerprobe.kennungAus(1));
        var ereignis = new BlockMultiPlaceEvent(List.of(unten, oben),
                marktstadt.getBlockAt(7, 70, 13), null, cara.spieler(), true);

        wache.beimSetzen(ereignis);

        assertTrue(marken.istMarkiert(ort(MARKTSTADT, 7, 71, 13)), "untere Haelfte");
        assertTrue(marken.istMarkiert(ort(MARKTSTADT, 7, 72, 13)), "obere Haelfte");
        assertEquals(2, marken.anzahl());
    }

    @Test
    @DisplayName("Zweimal dasselbe Setzen legt keine zweite Marke an")
    void zweimalIstEineMarke() {
        wache.beimSetzen(setzen(7, 71, 13));
        wache.beimSetzen(setzen(7, 71, 13));

        assertEquals(1, marken.anzahl());
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
                () -> new Setzwache(null, Set.of(MARKTSTADT)));
        assertThrows(IllegalArgumentException.class,
                () -> new Setzwache(marken, null));
        // Eine leere Menge waere ein Zweig, der nie etwas tut - und das faellt erst mit
        // Teil E auf: Dann zahlt jeder Block jedes Mal.
        assertThrows(IllegalArgumentException.class,
                () -> new Setzwache(marken, Set.of()));
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
