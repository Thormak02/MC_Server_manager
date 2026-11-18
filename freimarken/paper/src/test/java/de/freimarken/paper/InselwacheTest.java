package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Inselbuch;
import de.freimarken.kern.Inselgrenze;
import de.freimarken.kern.Inselplan;
import de.freimarken.kern.Lagerattrappe;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Tat;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * Der Inselschutz - wer auf einer Meerinsel bauen darf.
 *
 * <p><b>Die Probe zu dem Loch, das diese Regel schliesst</b>: In Hafenwerder griff bis
 * zum 18.11.2026 GAR KEINE Wache. {@code darfHandeln} stieg bei fremder Welt aus, keine
 * Zuhoererklasse las den Inselplan, und die Welt laeuft im Ueberlebensmodus. Jeder konnte
 * auf jeder fremden Insel bauen und abbauen - 75.000 Mark fuer etwas, das niemandem
 * gehoerte.
 *
 * <p>Dieselbe Bauart wie {@code StadtwacheTest}: Geprueft wird die REGEL an ihrer
 * Bukkit-Naht. Dass jede der fuenfzehn Ereignisarten durch dieselbe Regel laeuft, ist die
 * Zusage der {@link Schutzwache} und steht in {@code SchutzwacheTest}. Was die Regel SAGT,
 * steht im {@code InselbuchTest} im Kern. Hier dazwischen liegt, was keines von beiden
 * sieht: ob die Welt richtig erkannt wird, ob aus einer {@link Location} die richtigen
 * Koordinaten werden, und ob zu jeder Absage der passende Handbalken kommt.
 */
@DisplayName("Der Inselschutz")
class InselwacheTest {

    private static final Tat BAUEN = Tat.BAUEN;

    private static final String HAFENWERDER = "freimarken:hafenwerder";

    private static final String MARKTSTADT = "freimarken:marktstadt";

    private static final UUID ANNA = Spielerprobe.kennungAus(1);

    private static final UUID BERT = Spielerprobe.kennungAus(2);

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private SpeicherLager lager;
    private Inselbuch buch;
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
        buch = new Inselbuch(Inselgrenze.hafenwerder(), lager,
                new SpeicherKasse(10_000_000L),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
        anna = new Spielerprobe("Anna", ANNA);
        bert = new Spielerprobe("Bert", BERT);
    }

    // ============================================================ nicht zustaendig

    @Test
    @DisplayName("Eine andere WELT geht den Inselschutz nichts an")
    void fremdeWeltIstNichtZustaendig() {
        // Die billigste Frage und darum die erste: Diese Regel laeuft bei JEDEM
        // Blockereignis des Servers, und in jeder anderen Welt soll sie nach einem
        // Zeichenkettenvergleich fertig sein - ohne Plan und ohne Datenbank.
        //
        // DIESELBEN Koordinaten wie ein Inselfeld, nur in der Marktstadt: Faellt die
        // Weltpruefung weg, saehe die Regel hier eine Insel.
        Inselwache wache = wache();

        assertEquals(Schutzwache.Ortsregel.Urteil.NICHT_ZUSTAENDIG,
                wache.pruefe(bert.spieler(), BAUEN, ort(MARKTSTADT, 319, 64, 319)));
        assertTrue(!wache.zustaendig(ort(MARKTSTADT, 319, 64, 319)),
                "und sie haelt sich dort auch nicht fuer zustaendig");
    }

    @Test
    @DisplayName("In Hafenwerder ist sie UEBERALL zustaendig - auch im offenen Meer")
    void inHafenwerderImmerZustaendig() {
        // Der Unterschied zum Ladenschutz, und er ist die Sache selbst: Dort entscheidet
        // die EINHEIT, hier die WELT. Waere das Meer nicht zustaendig, fiele es an das
        // Besitzbuch der Marktstadt durch - und das rechnete mit floorDiv(x, 71) eine
        // Parzellennummer aus einer Koordinate von Hafenwerder.
        Inselwache wache = wache();
        int imMeer = buch.plan().feldAnfang(1) + Inselplan.INSEL_KANTE + 5;

        assertTrue(wache.zustaendig(ort(HAFENWERDER, imMeer, 62, imMeer)));
        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(anna.spieler(), BAUEN,
                        ort(HAFENWERDER, imMeer, 62, mitte(1))));
    }

    // ============================================================ das Urteil

    @Test
    @DisplayName("Auf seiner Insel geht die Hand des Besitzers durch - durch die WACHE")
    void besitzerDarf() {
        nimm(anna, 1, 1);
        Inselwache wache = wache();

        assertEquals(Schutzwache.Ortsregel.Urteil.ERLAUBT,
                wache.pruefe(anna.spieler(), BAUEN,
                        ort(HAFENWERDER, mitte(1), 64, mitte(1))));
        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(bert.spieler(), BAUEN,
                        ort(HAFENWERDER, mitte(1), 64, mitte(1))),
                "und die des Nachbarn nicht");
    }

    @Test
    @DisplayName("Ein Mitbauer kommt durch dieselbe Wache")
    void mitbauerDarf() {
        Parzelle insel = nimm(anna, 1, 1);
        lager.mitbauerSetzen(insel.id(), Set.of(BERT));

        assertEquals(Schutzwache.Ortsregel.Urteil.ERLAUBT,
                wache().pruefe(bert.spieler(), BAUEN,
                        ort(HAFENWERDER, mitte(1), 64, mitte(1))));
    }

    @Test
    @DisplayName("Auf einer freien Insel und auf der Ankunftsinsel baut niemand")
    void freieInselUndAnkunft() {
        Inselwache wache = wache();

        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(anna.spieler(), BAUEN,
                        ort(HAFENWERDER, mitte(4), 64, mitte(4))));
        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(anna.spieler(), BAUEN,
                        ort(HAFENWERDER, mitte(0), 64, mitte(0))),
                "auch auf der Ankunftsinsel - sie gehoert niemandem und bleibt es");
    }

    @Test
    @DisplayName("Ein Ereignis OHNE Spieler wird abgelehnt - und klagt NICHT")
    void ohneSpielerKeineKlage() {
        // GEFUNDEN BEIM BAUEN [18.11.2026]: Parzelle.darfBauen(null) warf, weil
        // Set.of(...).contains(null) eine NullPointerException ist. Die Wache faengt
        // jeden Wurf und macht daraus eine Klage ueber eine stumme Datenbank - der
        // Betreiber haette im Protokoll gelesen, seine Ablage sei kaputt, und zwar bei
        // jedem Ereignis ohne Spieler.
        nimm(anna, 1, 1);
        Inselwache wache = wache();

        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(null, BAUEN, ort(HAFENWERDER, mitte(1), 64, mitte(1))));
        assertEquals(List.of(), klagen(),
                "und zwar OHNE eine Zeile im Protokoll: Es ist kein Fehler, dass ein"
                        + " Ereignis keinen Spieler hat");
    }

    @Test
    @DisplayName("Eine stumme Ablage heisst NEIN - und klagt genau einmal")
    void stummeAblageSagtNein() {
        Inselwache wache = new Inselwache(new Inselbuch(Inselgrenze.hafenwerder(),
                new WerfendesLager(), new SpeicherKasse(0L),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE)),
                HAFENWERDER, Sprachprobe.spracheAusDemJar(), protokoll);
        Location ort = ort(HAFENWERDER, mitte(1), 64, mitte(1));

        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(anna.spieler(), BAUEN, ort),
                "die sichere Richtung: Ein stilles Ja liesse jeden auf jeder fremden"
                        + " Insel bauen");
        wache.pruefe(anna.spieler(), BAUEN, ort);
        wache.pruefe(bert.spieler(), BAUEN, ort);

        assertEquals(1, klagen().size(),
                "gedrosselt: Ohne Drossel schreibt ein ausgefallener Datenbankrechner"
                        + " in Sekunden ein Protokoll voll - " + klagen());
        assertTrue(klagen().get(0).contains("nicht nachsehbar"), klagen().get(0));
    }

    // ============================================================ der Handbalken

    @Test
    @DisplayName("Jede Lage hat ihren EIGENEN Handbalken")
    void jedeLageIhrBalken() {
        nimm(anna, 1, 1);
        Inselwache wache = wache();
        int imMeer = buch.plan().feldAnfang(1) + Inselplan.INSEL_KANTE + 5;

        String meer = Sprachprobe.bild(wache.grund(bert.spieler(), BAUEN,
                ort(HAFENWERDER, imMeer, 62, mitte(1))));
        String ankunft = Sprachprobe.bild(wache.grund(bert.spieler(), BAUEN,
                ort(HAFENWERDER, mitte(0), 64, mitte(0))));
        String frei = Sprachprobe.bild(wache.grund(bert.spieler(), BAUEN,
                ort(HAFENWERDER, mitte(4), 64, mitte(4))));
        String fremd = Sprachprobe.bild(wache.grund(bert.spieler(), BAUEN,
                ort(HAFENWERDER, mitte(1), 64, mitte(1))));

        assertTrue(meer.contains("Offenes Meer"), meer);
        assertTrue(ankunft.contains("Ankunftsinsel"), ankunft);
        assertTrue(frei.contains("frei"), frei);
        assertTrue(fremd.contains("Anna"),
                "die fremde Insel nennt ihren Besitzer - ohne ihn weiss der Fragende"
                        + " nicht, wen er ansprechen muss: " + fremd);
        assertEquals(4, Set.of(meer, ankunft, frei, fremd).size(),
                "vier Lagen, vier Saetze - laufen zwei auf denselben, ist der Spieler"
                        + " im Zweig verloren");
    }

    @Test
    @DisplayName("Die Reihenfolge der Fragen: das MEER gewinnt gegen die Feldnummer")
    void meerGewinntGegenDieFeldnummer() {
        // Mitten im Meer nennt feldNummer das Feld DAVOR. Wer die Ablage zuerst fragt,
        // bekommt dort den Balken der fremden Insel - und ein Spieler, der zehn Bloecke
        // vor Annas Strand im Wasser steht, liest, das Wasser gehoere Anna.
        nimm(anna, 1, 1);
        int imMeer = buch.plan().feldAnfang(1) + Inselplan.INSEL_KANTE + 5;

        String balken = Sprachprobe.bild(wache().grund(bert.spieler(), BAUEN,
                ort(HAFENWERDER, imMeer, 62, mitte(1))));

        assertTrue(balken.contains("Offenes Meer"), balken);
        assertEquals(1, buch.plan().feldNummer(imMeer),
                "und zwar OBWOHL die Feldnummer dort 1 nennt - also Annas Feld. Diese"
                        + " Zeile ist der Grund, aus dem die Probe traegt: Ohne den"
                        + " Riegel kaeme hier der Balken der fremden Insel");
    }

    @Test
    @DisplayName("Der Grund nach einem Weltwechsel ist der allgemeine Satz")
    void grundNachWeltwechsel() {
        // Zwischen dem Urteil und dieser Frage kann der Spieler die Welt gewechselt
        // haben. Dann ist der allgemeine Satz die ehrlichere Auskunft - eine Antwort
        // ueber ein Inselfeld in der Marktstadt waere eine Erfindung.
        String balken = Sprachprobe.bild(wache().grund(anna.spieler(), BAUEN,
                ort(MARKTSTADT, 319, 64, 319)));

        assertTrue(balken.contains("nicht nachsehbar"), balken);
    }

    // ============================================================ der Riegel

    @Test
    @DisplayName("Ohne Buch und ohne Welt gibt es diese Wache nicht")
    void riegel() {
        assertThrows(IllegalArgumentException.class,
                () -> new Inselwache(null, HAFENWERDER,
                        Sprachprobe.spracheAusDemJar(), protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Inselwache(buch, "", Sprachprobe.spracheAusDemJar(),
                        protokoll),
                "ohne Welt waere sie eine Regel, die in JEDER Welt nach Inselfeldern"
                        + " sucht");
    }

    // ============================================================ Werkzeug

    private Inselwache wache() {
        return new Inselwache(buch, HAFENWERDER, Sprachprobe.spracheAusDemJar(),
                protokoll);
    }

    private int mitte(int feld) {
        return buch.plan().feldMitte(feld);
    }

    private Parzelle nimm(Spielerprobe wer, int feldX, int feldZ) {
        return buch.nehmen(HAFENWERDER, wer.kennung(), mitte(feldX), mitte(feldZ), 9)
                .insel().orElseThrow();
    }

    private static Location ort(String welt, int x, int y, int z) {
        return new Location(Serverprobe.welt(welt), x, y, z);
    }

    private List<String> klagen() {
        return protokollzeilen.stream()
                .filter(satz -> satz.getLevel().intValue() >= Level.WARNING.intValue())
                .map(LogRecord::getMessage)
                .toList();
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("InselwacheProbe-" + UUID.randomUUID());
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

    /** Ein Lager, das beim Nachsehen wirft - fuer die stumme Ablage. */
    private static final class WerfendesLager extends Lagerattrappe {

        @Override
        public Optional<Parzelle> finde(String welt, int x, int z) {
            throw new IllegalStateException("Die Datenbank antwortet nicht");
        }
    }
}
