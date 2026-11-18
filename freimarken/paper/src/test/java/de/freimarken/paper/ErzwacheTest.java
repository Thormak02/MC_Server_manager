package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Blockmarken;
import de.freimarken.kern.Blockort;
import de.freimarken.kern.Erzwerte;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.SpeicherBlockmarken;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.SpeicherWolken;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Wolkenbuch;
import de.freimarken.kern.Wolkengrenze;
import de.freimarken.kern.Wolkenwerte;
import de.freimarken.kern.ZwischenspeicherWolken;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockFormEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Kobbelsteingenerator von Wolkenwerder - die Hand, die das Erz hineinlegt.
 *
 * <p>Geprueft wird am ECHTEN Ereignis, wie bei der {@code AngelwacheTest}:
 * {@code BlockFormEvent} laesst sich ohne Server bauen, und nur so ist zu sehen, ob die
 * Tore in der richtigen Reihenfolge stehen und ob das Erz wirklich im Block landet.
 *
 * <p><b>Der Zufall kommt als Wuerfel herein</b>, und diese Proben stellen ihn: Eine Probe
 * mit echtem Zufall ist eine, die manchmal gruen ist. Was die Regel aus den zwei Wuerfen
 * macht, prueft {@code ErzregelTest} im Kern; hier geht es um die Tore, die Insel, die
 * STUFE und die Blockmarke.
 */
@DisplayName("Der Kobbelsteingenerator")
class ErzwacheTest {

    private static final String WOLKENWERDER = "freimarken:wolkenwerder";

    private static final String ALTFURT = "freimarken:altfurt";

    /** Eine Tabelle mit EINER Zeile ab Stufe 0 - dann ist der zweite Wurf gleich. */
    private static final Erzwerte EINE_ZEILE = new Erzwerte(true, 100, 0, 100, true,
            Map.of("diamond_ore", new Erzwerte.Erzzeile(1, 0)));

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private final UUID anna = UUID.randomUUID();

    private Mapwelt boden;
    private SpeicherLager lager;
    private Wolkenbuch buch;
    private ZwischenspeicherWolken staende;
    private SpeicherBlockmarken marken;
    private Logger protokoll;
    private Parzelle insel;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
        boden = new Mapwelt();
        lager = new SpeicherLager();
        buch = new Wolkenbuch(Wolkengrenze.wolkenwerder(), lager,
                new SpeicherKasse(1_000_000L),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        staende = new ZwischenspeicherWolken(new SpeicherWolken());
        marken = new SpeicherBlockmarken();
        insel = buch.nehmen(WOLKENWERDER, anna, 1).insel().orElseThrow();
    }

    // ===================================================== der Tausch

    @Test
    @DisplayName("Auf einer Insel wird aus Kobbelstein ein Erz")
    void aufDerInselKommtErz() {
        wache(EINE_ZEILE).beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));

        assertEquals(Material.DIAMOND_ORE, boden.bei(inselX(), HOEHE, inselZ()));
    }

    @Test
    @DisplayName("Auch STEIN ist ein Ausgang - Lava ueber Wasser")
    void auchStein() {
        // Der zweite Block, den Lava und Wasser bilden. Eine Probe nur mit Kobbelstein
        // waere gruen, auch wenn die Wache nur diesen einen Namen kennt - und ein
        // Generator mit Wasser VON OBEN gaebe nie Erz.
        wache(EINE_ZEILE).beimBilden(bildet(Material.STONE, aufDerInsel()));

        assertEquals(Material.DIAMOND_ORE, boden.bei(inselX(), HOEHE, inselZ()));
    }

    @Test
    @DisplayName("Schnee und Eis gehen durch DASSELBE Ereignis - und werden nie getauscht")
    void schneeBleibtSchnee() {
        // Das Tor, das zuerst geprueft wird, und zwar aus zwei Gruenden: Ein Diamant aus
        // Schnee waere ein Fehler, den niemand als Einstellung gemeint hat - und in einem
        // Biom wie snowy_plains faellt dieses Ereignis bei einem Schneesturm fuer jeden
        // freien Block. Eine Inselabfrage davor waere die teuerste Zeile des Servers.
        wache(EINE_ZEILE).beimBilden(bildet(Material.SNOW, aufDerInsel()));
        assertEquals(Material.SNOW, boden.bei(inselX(), HOEHE, inselZ()));

        wache(EINE_ZEILE).beimBilden(bildet(Material.ICE, aufDerInsel()));
        assertEquals(Material.ICE, boden.bei(inselX(), HOEHE, inselZ()));
    }

    @Test
    @DisplayName("In der LEERE zwischen den Inseln bleibt es Kobbelstein")
    void inDerLeereNichts() {
        // Dort gehoert niemandem etwas, und es gibt keine Stufe, nach der gerechnet
        // werden koennte. Das ist auch der Riegel gegen den Automaten, der im Niemandsland
        // zwischen zwei Feldern steht.
        int x = inselX() + IN_DIE_LEERE;

        wache(EINE_ZEILE).beimBilden(bildet(Material.COBBLESTONE,
                new int[] {x, HOEHE, inselZ()}));

        assertEquals(Material.COBBLESTONE, boden.bei(x, HOEHE, inselZ()));
    }

    @Test
    @DisplayName("In einer anderen Welt bleibt es Kobbelstein")
    void andereWeltNichts() {
        BlockFormEvent ereignis = bildet(Material.COBBLESTONE, aufDerInsel(),
                Serverprobe.welt(ALTFURT, boden));

        wache(EINE_ZEILE).beimBilden(ereignis);

        assertEquals(Material.COBBLESTONE, boden.bei(inselX(), HOEHE, inselZ()));
    }

    @Test
    @DisplayName("Eine ausgeschaltete Tabelle laesst alles, wie es ist")
    void ausgeschaltet() {
        wache(EINE_ZEILE.mitAn(false))
                .beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));

        assertEquals(Material.COBBLESTONE, boden.bei(inselX(), HOEHE, inselZ()));
    }

    @Test
    @DisplayName("Liegt der Wurf ueber der Haeufigkeit, bleibt es Kobbelstein")
    void ueberDerHaeufigkeit() {
        // Der haeufigste Fall im Spiel - fuenf von sechs Bloecken bei der Vorgabe -, und
        // er muss den Block unberuehrt lassen.
        Erzwache wache = wache(EINE_ZEILE.mitProzent(20, 0, 20), 20, 0L);

        wache.beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));

        assertEquals(Material.COBBLESTONE, boden.bei(inselX(), HOEHE, inselZ()));
    }

    // ===================================================== die Stufe

    @Test
    @DisplayName("Die STUFE schliesst ein Erz auf - dieselbe Tabelle, derselbe Wurf")
    void stufeSchliesstAuf() {
        // DIE TRAGENDE PROBE DIESER WACHE. Die Stufe ist die Zahl der erfuellten
        // Herausforderungen, und daran haengt der ganze Fortschritt dieser Welt: Eisen
        // ab Stufe 1 heisst, dass derselbe Generator vor der ersten Aufgabe KEIN Eisen
        // gibt. Ohne diese Probe waere die Mindeststufe eine Zahl, die nur im Kern wirkt.
        Erzwerte abStufeEins = new Erzwerte(true, 100, 0, 100, true,
                Map.of("iron_ore", new Erzwerte.Erzzeile(1, 1)));
        Erzwache wache = wache(abStufeEins);

        wache.beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));
        assertEquals(Material.COBBLESTONE, boden.bei(inselX(), HOEHE, inselZ()),
                "bei Stufe 0 erreicht keine Zeile die Stufe - es bleibt Kobbelstein,"
                        + " obwohl der Prozentwurf trifft");

        staende.erfuellen(insel.id(), "erster_stein");
        wache.beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));

        assertEquals(Material.IRON_ORE, boden.bei(inselX(), HOEHE, inselZ()),
                "eine erfuellte Herausforderung, und dasselbe Loch gibt Eisen");
    }

    @Test
    @DisplayName("Die Stufe der NACHBARINSEL zaehlt nicht")
    void stufeDerNachbarinsel() {
        // Der Fehler, den eine Probe mit nur einer Insel nie findet: Wer die Staende
        // ueber die WELT statt ueber die Inselnummer fragt, gibt jedem die Ausbeute des
        // Besten.
        Parzelle andere = buch.nehmen(WOLKENWERDER, UUID.randomUUID(), 1).insel()
                .orElseThrow();
        staende.erfuellen(andere.id(), "erster_stein");
        Erzwerte abStufeEins = new Erzwerte(true, 100, 0, 100, true,
                Map.of("iron_ore", new Erzwerte.Erzzeile(1, 1)));

        wache(abStufeEins).beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));

        assertEquals(Material.COBBLESTONE, boden.bei(inselX(), HOEHE, inselZ()));
    }

    // ===================================================== die Marke

    @Test
    @DisplayName("Ein getauschter Block bekommt seine BLOCKMARKE")
    void getauschtesErzIstMarkiert() {
        // Eine Geldentscheidung: Ohne Marke zahlt verdienst.loehne fuer jedes Erz aus dem
        // Generator, und ein Automat laeuft Tag und Nacht. Der Spieler behaelt das Erz -
        // er bekommt nur kein Mark fuer das Abbauen.
        wache(EINE_ZEILE).beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));

        assertTrue(marken.istMarkiert(
                new Blockort(WOLKENWERDER, inselX(), HOEHE, inselZ())));
        assertEquals(1, marken.anzahl());
    }

    @Test
    @DisplayName("Ohne wolken.erze.marken wird nicht markiert")
    void ohneMarkenNichtMarkiert() {
        wache(EINE_ZEILE.mitMarken(false))
                .beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));

        assertEquals(Material.DIAMOND_ORE, boden.bei(inselX(), HOEHE, inselZ()),
                "getauscht wird trotzdem");
        assertEquals(0, marken.anzahl());
    }

    @Test
    @DisplayName("Ein NICHT getauschter Block bekommt keine Marke")
    void kobbelsteinOhneMarke() {
        // Sonst traegt jeder Kobbelstein eines Generators eine Marke, und der Abbau
        // zahlte in Wolkenwerder nie etwas - auch nicht fuer den Block, den Vanilla
        // genauso in Altfurt bildet.
        wache(EINE_ZEILE.mitProzent(0, 0, 100), 50, 0L)
                .beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));

        assertEquals(0, marken.anzahl());
    }

    // ===================================================== Datei und Protokoll

    @Test
    @DisplayName("Ein Neuladen wirkt - die Wache haelt den STAND, keine Kopie")
    void neuladenWirkt() {
        // Der Grund, aus dem die Wache den Wirtschaftsstand bekommt und nicht die
        // Erzwerte: Eine kopierte Tabelle waere nach dem ersten Neuladen taub, und
        // niemand saehe es.
        Wirtschaftsstand stand = stand(EINE_ZEILE);
        Erzwache wache = new Erzwache(WOLKENWERDER, stand, buch, staende, marken,
                gestellterWuerfel(0, 0L), protokoll);

        wache.beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));
        assertEquals(Material.DIAMOND_ORE, boden.bei(inselX(), HOEHE, inselZ()));

        stand.uebernimm(Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitErzen(new Erzwerte(true, 100, 0, 100, true,
                        Map.of("emerald_ore", new Erzwerte.Erzzeile(1, 0))))));

        wache.beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));

        assertEquals(Material.EMERALD_ORE, boden.bei(inselX(), HOEHE, inselZ()));
    }

    @Test
    @DisplayName("Ein Erz, das es nicht gibt, bleibt Kobbelstein - und klagt EINMAL")
    void unbekanntesErzKlagtEinmal() {
        // Der Leser der Einstellungsdatei prueft jeden Namen, also kommt dieser Fall nur
        // aus einer Tabelle, die auf einem anderen Weg hereinkam. Und die Drossel ist
        // noetig: Ohne sie schreibt ein Generator in Sekunden ein Protokoll voll.
        Erzwache wache = wache(EINE_ZEILE.mitTabelle(
                Map.of("gibt_es_nicht", new Erzwerte.Erzzeile(1, 0))));

        wache.beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));
        wache.beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));

        assertEquals(Material.COBBLESTONE, boden.bei(inselX(), HOEHE, inselZ()));
        assertEquals(1, klagen().size(), klagen().toString());
        assertTrue(klagen().get(0).contains("gibt_es_nicht"), klagen().get(0));
        assertTrue(klagen().get(0).contains("wolken.erze.tabelle"), klagen().get(0));
    }

    @Test
    @DisplayName("Ohne Wolkenwelt tut die Wache nichts")
    void ohneWelt() {
        new Erzwache("", stand(EINE_ZEILE), buch, staende, marken,
                gestellterWuerfel(0, 0L), protokoll)
                .beimBilden(bildet(Material.COBBLESTONE, aufDerInsel()));

        assertEquals(Material.COBBLESTONE, boden.bei(inselX(), HOEHE, inselZ()));
    }

    @Test
    @DisplayName("Ohne Stand, Buch, Staende, Marken oder Wuerfel gibt es keine Wache")
    void riegel() {
        Wirtschaftsstand stand = stand(EINE_ZEILE);
        Random wuerfel = new Random();
        Blockmarken echte = marken;

        assertThrows(IllegalArgumentException.class, () -> new Erzwache(WOLKENWERDER,
                null, buch, staende, echte, wuerfel, protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Erzwache(WOLKENWERDER,
                stand, null, staende, echte, wuerfel, protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Erzwache(WOLKENWERDER,
                stand, buch, null, echte, wuerfel, protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Erzwache(WOLKENWERDER,
                stand, buch, staende, null, wuerfel, protokoll));
        assertThrows(IllegalArgumentException.class, () -> new Erzwache(WOLKENWERDER,
                stand, buch, staende, echte, null, protokoll));
    }

    // ===================================================== Werkzeug

    /** Die Hoehe, in der die Proben ihren Generator betreiben. */
    private static final int HOEHE = Wolkenboden.BODEN + 1;

    /**
     * So weit neben die Inselmitte, dass es sicher in der LEERE liegt.
     *
     * <p>Ein Wolkenfeld ist {@code WOLKENFELD_KANTE} = 128 Bloecke breit, die Mitte liegt
     * also 64 von seinem Rand entfernt. 70 ist draussen - und der Abstand zum naechsten
     * Feld ist 384, also trifft es auch nicht das Nachbarfeld.
     */
    private static final int IN_DIE_LEERE = 70;

    private int inselX() {
        return buch.plan().feldMitte(insel.x());
    }

    private int inselZ() {
        return buch.plan().feldMitte(insel.z());
    }

    private int[] aufDerInsel() {
        return new int[] {inselX(), HOEHE, inselZ()};
    }

    private Wirtschaftsstand stand(Erzwerte werte) {
        return new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitWolken(
                Wolkenwerte.VORGABE.mitErzen(werte)));
    }

    private Erzwache wache(Erzwerte werte) {
        return wache(werte, 0, 0L);
    }

    private Erzwache wache(Erzwerte werte, int prozentWurf, long gewichtsWurf) {
        return new Erzwache(WOLKENWERDER, stand(werte), buch, staende, marken,
                gestellterWuerfel(prozentWurf, gewichtsWurf), protokoll);
    }

    /** Das Ereignis, wie Bukkit es baut: ein Block wird zu dieser Art. */
    private BlockFormEvent bildet(Material entsteht, int[] ort) {
        return bildet(entsteht, ort, Serverprobe.welt(WOLKENWERDER, boden));
    }

    private BlockFormEvent bildet(Material entsteht, int[] ort, World welt) {
        // Die Art steht VOR dem Ereignis im Boden: Der neue Zustand der Serverprobe liest
        // seine Art aus demselben Griff, in den die Wache schreibt. Auf dem laufenden
        // Server ist der Block bis zum Ende des Ereignisses noch Lava - was zaehlt, ist
        // die Art, die der Zweig in den ZUSTAND schreibt, und genau die liest die Probe
        // danach ab.
        boden.setze(ort[0], ort[1], ort[2], entsteht);
        BlockState neu = Serverprobe.zustand(welt, boden, ort[0], ort[1], ort[2]);
        return new BlockFormEvent(welt.getBlockAt(ort[0], ort[1], ort[2]), neu);
    }

    /** Ein Wuerfel, der SAGT, was er wirft - wie bei der Angelwache. */
    private static Random gestellterWuerfel(int prozentWurf, long gewichtsWurf) {
        return new Random() {
            @Override
            public int nextInt(int grenze) {
                return prozentWurf;
            }

            @Override
            public long nextLong(long grenze) {
                return gewichtsWurf;
            }
        };
    }

    private List<String> klagen() {
        return protokollzeilen.stream()
                .filter(satz -> satz.getLevel().intValue() >= Level.WARNING.intValue())
                .map(LogRecord::getMessage)
                .toList();
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("ErzwacheProbe-" + UUID.randomUUID());
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

    /** Ein Weltgriff, der schreibt und wiedergibt - mehr braucht diese Probe nicht. */
    private static final class Mapwelt implements Weltgriff {

        private final Map<String, Material> gesetzt = new HashMap<>();

        @Override
        public int unten() {
            return -64;
        }

        @Override
        public int oben(int x, int z) {
            return Wolkenboden.BODEN;
        }

        @Override
        public Material bei(int x, int y, int z) {
            return gesetzt.getOrDefault(x + "/" + y + "/" + z, Material.AIR);
        }

        @Override
        public void setze(int x, int y, int z, Material block) {
            gesetzt.put(x + "/" + y + "/" + z, block);
        }
    }

}
