package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Feldgrenze;
import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Lagerattrappe;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Stadtfeldplan;
import de.freimarken.kern.Warpverzeichnis;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Stadtschutz - wer in einem Stadtfeld bauen darf.
 *
 * <p><b>Die Probe zu dem Loch, das seit Teil A offenstand</b>: In der Welt der
 * Stadtfelder griff bis zum 11.11.2026 GAR KEINE Wache. {@code darfHandeln} stieg bei
 * fremder Welt aus, keine der siebzehn Zuhoererklassen las den Stadtfeldplan, und die
 * Welt laeuft im Ueberlebensmodus. Jeder konnte in jedem Feld jeder fremden Stadt bauen
 * und abbauen - 400.000 Mark fuer ein Feld, das niemandem gehoerte.
 *
 * <p>Geprueft wird die REGEL an ihrer Bukkit-Naht, nicht die fuenfzehn Ereignisarten der
 * {@link Schutzwache}: Dass jede von ihnen durch dieselbe Regel laeuft, ist die Zusage
 * jener Wache und steht in {@code SchutzwacheTest}. Was die Regel SAGT, steht im
 * {@code FreistadtbuchTest} im Kern. Hier dazwischen liegt, was keines von beiden sieht:
 * ob die Welt richtig erkannt wird, ob aus einer {@link Location} die richtigen
 * Koordinaten werden, und ob zu jeder Absage der passende Handbalken kommt.
 *
 * <p>Dieselbe Bauart wie {@code LadenschutzTest}, und aus demselben Grund: Beide sind
 * {@link Schutzwache.Ortsregel}n, und beide muessen bei stummer Ablage NEIN sagen.
 */
@DisplayName("Der Stadtschutz")
class StadtwacheTest {

    private static final String FREISTAEDTE = "freimarken:freistaedte";

    private static final String MARKTSTADT = "freimarken:marktstadt";

    private static final UUID ANNA = Spielerprobe.kennungAus(1);

    private static final UUID BERT = Spielerprobe.kennungAus(2);

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private SpeicherLager lager;
    private Freistadtbuch buch;
    private Spielerprobe anna;
    private Spielerprobe bert;
    private Logger protokoll;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
        Serverprobe.nenne(ANNA, "Anna");
        Serverprobe.nenne(BERT, "Bert");
    }

    @BeforeEach
    void vorher() {
        Serverprobe.keineWeltenMehr();
        lager = new SpeicherLager();
        buch = new Freistadtbuch(Feldgrenze.freistaedte(), lager,
                new SpeicherKasse(10_000_000L),
                Warpverzeichnis.ausWelten(List.of("freistaedte")),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
        anna = new Spielerprobe("Anna", ANNA);
        bert = new Spielerprobe("Bert", BERT);
    }

    // ============================================================ nicht zustaendig

    @Test
    @DisplayName("Eine andere WELT geht den Stadtschutz nichts an")
    void fremdeWeltIstNichtZustaendig() {
        // Die billigste Frage und darum die erste: Diese Regel laeuft bei JEDEM
        // Blockereignis des Servers, und in jeder anderen Welt soll sie nach einem
        // Zeichenkettenvergleich fertig sein - ohne Plan und ohne Datenbank.
        //
        // DIESELBEN Koordinaten wie ein Stadtfeld, nur in der Marktstadt: Faellt die
        // Weltpruefung weg, saehe die Regel hier ein Stadtfeld.
        Stadtwache wache = wache();

        assertEquals(Schutzwache.Ortsregel.Urteil.NICHT_ZUSTAENDIG,
                wache.pruefe(bert.spieler(), ort(MARKTSTADT, 100, 64, 100)));
        assertTrue(!wache.zustaendig(ort(MARKTSTADT, 100, 64, 100)),
                "und sie haelt sich dort auch nicht fuer zustaendig");
    }

    @Test
    @DisplayName("In der Stadtfelderwelt ist sie UEBERALL zustaendig - auch in der Leere")
    void inDerStadtweltImmerZustaendig() {
        // Der Unterschied zum Ladenschutz, und er ist die Sache selbst: Dort entscheidet
        // die EINHEIT, hier die WELT. Waere die Leere nicht zustaendig, fiele sie an das
        // Besitzbuch der Marktstadt durch - und das rechnete mit floorDiv(x, 71) eine
        // Parzellennummer aus einer Koordinate der Freistaedte.
        Stadtwache wache = wache();
        Stadtfeldplan plan = buch.plan();
        int inDerLeere = plan.feldAnfang(1) + Stadtfeldplan.FELD_KANTE;

        assertTrue(wache.zustaendig(ort(FREISTAEDTE, inDerLeere, 64, inDerLeere)));
        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(anna.spieler(), ort(FREISTAEDTE, inDerLeere, 64,
                        plan.feldMitte(1))));
    }

    // ============================================================ die sechs Lagen

    @Test
    @DisplayName("Der Stadtbesitzer darf in seiner Stadt, ein anderer nicht")
    void besitzerDarf() {
        buch.gruende(FREISTAEDTE, ANNA, "annastadt", 9);
        Stadtwache wache = wache();
        Location mitten = aufFeldVon("annastadt");

        assertEquals(Schutzwache.Ortsregel.Urteil.ERLAUBT,
                wache.pruefe(anna.spieler(), mitten));
        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(bert.spieler(), mitten));
    }

    @Test
    @DisplayName("Die LEERE ist auch fuer den Nachbarn verboten")
    void leereIstFuerJedenVerboten() {
        // Die Zusage, auf der die zweiunddreissig Bloecke Leere ruhen: Ein Block dort
        // ist der erste Block einer Bruecke. Vor dem 11.11.2026 stand diese Zusage im
        // Javadoc von Stadtfeldplan und war durch nichts gedeckt - die Barriere ist vier
        // Bloecke hoch, und wer auf ihre Oberkante steigt, baut von dort aus weiter.
        buch.gruende(FREISTAEDTE, ANNA, "annastadt", 9);
        Stadtwache wache = wache();
        var stadt = buch.staedte(FREISTAEDTE).get(0);
        int hinterDemFeld = buch.plan().feldAnfang(stadt.x()) + Stadtfeldplan.FELD_KANTE;

        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(anna.spieler(), ort(FREISTAEDTE, hinterDemFeld, 64,
                        buch.plan().feldMitte(stadt.z()))),
                "auch die Eigentuemerin des Feldes daneben darf hier nicht");
    }

    @Test
    @DisplayName("Ein Mitbauer der Stadt darf")
    void mitbauerDarf() {
        buch.gruende(FREISTAEDTE, ANNA, "annastadt", 9);
        lager.mitbauerSetzen(buch.staedte(FREISTAEDTE).get(0).id(), Set.of(BERT));
        Stadtwache wache = wache();

        assertEquals(Schutzwache.Ortsregel.Urteil.ERLAUBT,
                wache.pruefe(bert.spieler(), aufFeldVon("annastadt")));
    }

    // ============================================================ die Handbalken

    @Test
    @DisplayName("Jede Lage bekommt ihren EIGENEN Handbalken")
    void jedeLageEigenerBalken() {
        buch.gruende(FREISTAEDTE, ANNA, "annastadt", 9);
        Stadtwache wache = wache();
        Stadtfeldplan plan = buch.plan();
        var stadt = buch.staedte(FREISTAEDTE).get(0);
        int freiesFeld = stadt.x() == 1 ? 2 : 1;

        String fremd = Sprachprobe.bild(wache.grund(aufFeldVon("annastadt")));
        String leere = Sprachprobe.bild(wache.grund(ort(FREISTAEDTE,
                plan.feldAnfang(stadt.x()) + Stadtfeldplan.FELD_KANTE, 64,
                plan.feldMitte(stadt.z()))));
        String frei = Sprachprobe.bild(wache.grund(ort(FREISTAEDTE,
                plan.feldMitte(freiesFeld), 64, plan.feldMitte(1))));
        String ankunft = Sprachprobe.bild(wache.grund(ort(FREISTAEDTE,
                plan.feldMitte(0), 64, plan.feldMitte(0))));

        // Die STADT wird beim Namen genannt - mit ihm findet ein Besucher den Weg
        // zurueck ueber /warp freistadt annastadt. Das ist der Unterschied zur
        // Parzelle, die nur eine Nummer hat.
        assertTrue(fremd.contains("annastadt"), fremd);
        assertTrue(fremd.contains("Anna"), fremd);
        assertTrue(frei.contains("/freistadt gruenden"), frei);
        // Das Ankunftsfeld nennt KEINEN Ausweg fuer sich selbst - es ist nicht zu
        // gruenden. Stuende dort "frei - gruende hier", waere das eine Luege.
        assertTrue(ankunft.contains("gehoert allen"), ankunft);

        // Und alle vier unterscheiden sich wirklich: Vier gleiche Balken waeren vier
        // Lagen, die der Spieler nicht auseinanderhalten kann.
        List<String> alle = List.of(fremd, leere, frei, ankunft);
        assertEquals(alle.size(), Set.copyOf(alle).size(),
                "vier Lagen, vier verschiedene Saetze: " + alle);
    }

    @Test
    @DisplayName("Der Grund fragt OHNE Spieler - sonst saehe der Besitzer nie seinen Balken")
    void grundOhneSpieler() {
        buch.gruende(FREISTAEDTE, ANNA, "annastadt", 9);
        Stadtwache wache = wache();

        // Annas eigene Stadt. Wuerde grund() mit dem Spieler fragen, kaeme ERLAUBT
        // heraus - und dafuer gibt es keinen Balken, also wuerfe stadtBalken.
        String balken = Sprachprobe.bild(wache.grund(aufFeldVon("annastadt")));

        assertTrue(balken.contains("annastadt"), balken);
    }

    // ============================================================ stumme Ablage

    @Test
    @DisplayName("Eine stumme Ablage heisst NEIN - und klagt genau einmal")
    void stummeAblageHeisstNein() {
        Stadtwache wache = new Stadtwache(
                new Freistadtbuch(Feldgrenze.freistaedte(), new Lagerattrappe.Stumm(),
                        new SpeicherKasse(0L),
                        Warpverzeichnis.ausWelten(List.of("freistaedte")),
                        new Wirtschaftsstand(Wirtschaftswerte.VORGABE)),
                FREISTAEDTE, Sprachprobe.spracheAusDemJar(), protokoll);
        Location aufDemFeld = ort(FREISTAEDTE, buch.plan().feldMitte(1), 64,
                buch.plan().feldMitte(1));

        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(anna.spieler(), aufDemFeld),
                "im Zweifel bleibt fremder Besitz unberuehrt");
        // Und der Handbalken sagt es, statt eine Lage zu erfinden.
        assertTrue(Sprachprobe.bild(wache.grund(aufDemFeld)).contains("nicht nachsehbar"),
                Sprachprobe.bild(wache.grund(aufDemFeld)));
        assertEquals(1, protokollzeilen.size(),
                "gedrosselt: ein ausgefallener Datenbankrechner schriebe sonst in"
                        + " Sekunden das Protokoll voll - " + protokollzeilen.size());
    }

    // ============================================================ die Verdrahtung

    @Test
    @DisplayName("Ohne Welt und ohne Buch gibt es diese Regel gar nicht")
    void ohneWeltKeineRegel() {
        // Eine Regel ohne Welt suchte in JEDER Welt nach Stadtfeldern - und eine ohne
        // Buch haette keine Geometrie, nach der sie urteilen koennte. Beides faellt
        // sofort auf, statt still zu wirken.
        assertThrows(IllegalArgumentException.class,
                () -> new Stadtwache(buch, "", Sprachprobe.spracheAusDemJar(), protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Stadtwache(null, FREISTAEDTE, Sprachprobe.spracheAusDemJar(),
                        protokoll));
    }

    @Test
    @DisplayName("Zwei Ortsregeln nacheinander: jede in ihrer Welt, und keine stoert die andere")
    void zweiRegelnNacheinander() {
        buch.gruende(FREISTAEDTE, ANNA, "annastadt", 9);
        Schutzwache.Ortsregel kette = Schutzwache.Ortsregel.nacheinander(
                Schutzwache.KEINE_ORTSREGEL, wache());

        assertEquals(Schutzwache.Ortsregel.Urteil.ERLAUBT,
                kette.pruefe(anna.spieler(), aufFeldVon("annastadt")));
        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                kette.pruefe(bert.spieler(), aufFeldVon("annastadt")));
        // In einer dritten Welt sagt KEINE der beiden etwas - dann entscheidet das
        // Besitzbuch, wie vorher.
        assertEquals(Schutzwache.Ortsregel.Urteil.NICHT_ZUSTAENDIG,
                kette.pruefe(anna.spieler(), ort(MARKTSTADT, 100, 64, 100)));
        assertTrue(!kette.zustaendig(ort(MARKTSTADT, 100, 64, 100)));
    }

    @Test
    @DisplayName("Eine SPAETE Regel, die es noch nicht gibt, ist nie zustaendig")
    void spaeteRegelOhneLieferung() {
        // Der Zirkel aus FreimarkenPlugin: Die Schutzwache entsteht achtzig Zeilen vor
        // der Stadtwache. Solange das Feld null ist, darf nichts werfen und nichts
        // verbieten - sonst waere der Server zwischen diesen zwei Zeilen dicht.
        Schutzwache.Ortsregel spaet = Schutzwache.Ortsregel.spaet(() -> null);

        assertEquals(Schutzwache.Ortsregel.Urteil.NICHT_ZUSTAENDIG,
                spaet.pruefe(anna.spieler(), ort(FREISTAEDTE, 0, 64, 0)));
        assertTrue(!spaet.zustaendig(ort(FREISTAEDTE, 0, 64, 0)));
    }

    @Test
    @DisplayName("Eine SPAETE Regel wirkt, sobald sie da ist")
    void spaeteRegelWirktSpaeter() {
        buch.gruende(FREISTAEDTE, ANNA, "annastadt", 9);
        Stadtwache[] feld = new Stadtwache[1];
        Schutzwache.Ortsregel spaet = Schutzwache.Ortsregel.spaet(() -> feld[0]);
        Location mitten = aufFeldVon("annastadt");

        assertEquals(Schutzwache.Ortsregel.Urteil.NICHT_ZUSTAENDIG,
                spaet.pruefe(bert.spieler(), mitten), "vorher");

        feld[0] = wache();

        // DASSELBE Lambda, dieselbe Stelle, anderes Urteil: Das Feld wird beim AUFRUF
        // gelesen und nicht beim Bauen. Waere es beim Bauen gelesen, bliebe die Regel
        // fuer immer stumm, und kein Probenlauf meldete es.
        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                spaet.pruefe(bert.spieler(), mitten), "nachher");
        assertNotEquals("", Sprachprobe.bild(spaet.grund(mitten)));
    }

    // ------------------------------------------------------------------ Werkzeug

    private Stadtwache wache() {
        return new Stadtwache(buch, FREISTAEDTE, Sprachprobe.spracheAusDemJar(),
                protokoll);
    }

    /** Mitten auf dem Feld der Stadt mit diesem Namen. */
    private Location aufFeldVon(String name) {
        var stadt = buch.staedte(FREISTAEDTE).stream()
                .filter(eine -> name.equals(eine.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("keine Stadt " + name));
        return ort(FREISTAEDTE, buch.plan().feldMitte(stadt.x()), 64,
                buch.plan().feldMitte(stadt.z()));
    }

    private static Location ort(String weltschluessel, int x, int y, int z) {
        return new Location(Serverprobe.welt(weltschluessel), x, y, z);
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("StadtwacheProbe-" + UUID.randomUUID());
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
        eigener.setLevel(Level.ALL);
        return eigener;
    }
}
