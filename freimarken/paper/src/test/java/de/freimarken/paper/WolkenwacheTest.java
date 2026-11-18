package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Lagerattrappe;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Tat;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Wolkenbuch;
import de.freimarken.kern.Wolkengrenze;
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
 * Der Wolkenschutz - wer auf einer Skyblockinsel bauen darf.
 *
 * <p>Dieselbe Bauart wie {@link InselwacheTest}: Was die Regel SAGT, steht im
 * {@code WolkenbuchTest} im Kern. Hier dazwischen liegt, was jener nicht sieht - ob die
 * Welt richtig erkannt wird, ob aus einer {@link Location} die richtigen Koordinaten
 * werden, und ob zu jeder Absage der passende Handbalken kommt.
 *
 * <p><b>Ohne diese Regel waere Wolkenwerder eine Welt ohne Eigentum</b>, und das waere
 * hier noch schwerer zu merken als in Hafenwerder: Dort kostet eine Insel 75.000 Mark,
 * und wer sie verliert, fragt nach. Hier kostet sie nichts - der fehlende Schutz faellt
 * erst auf, wenn jemand die Erde einer fremden Insel abgetragen hat, und Erde waechst
 * nicht nach.
 */
@DisplayName("Der Wolkenschutz")
class WolkenwacheTest {

    private static final Tat BAUEN = Tat.BAUEN;

    private static final String WOLKENWERDER = "freimarken:wolkenwerder";

    private static final String MARKTSTADT = "freimarken:marktstadt";

    private static final UUID ANNA = Spielerprobe.kennungAus(1);

    private static final UUID BERT = Spielerprobe.kennungAus(2);

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private SpeicherLager lager;
    private Wolkenbuch buch;
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
        buch = new Wolkenbuch(Wolkengrenze.wolkenwerder(), lager,
                new SpeicherKasse(10_000_000L),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
        anna = new Spielerprobe("Anna", ANNA);
        bert = new Spielerprobe("Bert", BERT);
    }

    // ============================================================ nicht zustaendig

    @Test
    @DisplayName("Eine andere WELT geht den Wolkenschutz nichts an")
    void fremdeWeltIstNichtZustaendig() {
        // Die billigste Frage und darum die erste: Diese Regel laeuft bei JEDEM
        // Blockereignis des Servers. DIESELBEN Koordinaten wie ein Wolkenfeld, nur in
        // der Marktstadt - faellt die Weltpruefung weg, saehe die Regel hier eine Insel.
        Wolkenwache wache = wache();

        assertEquals(Schutzwache.Ortsregel.Urteil.NICHT_ZUSTAENDIG,
                wache.pruefe(bert.spieler(), BAUEN, ort(MARKTSTADT, 63, 64, 63)));
        assertFalse(wache.zustaendig(ort(MARKTSTADT, 63, 64, 63)));
    }

    @Test
    @DisplayName("In Wolkenwerder ist sie UEBERALL zustaendig - auch in der Leere")
    void inWolkenwerderImmerZustaendig() {
        // Der Grund steht in der Sache: Waere die Leere nicht zustaendig, fiele sie an
        // das Besitzbuch der Marktstadt durch - und das rechnete mit floorDiv(x, 71)
        // eine Parzellennummer aus einer Koordinate von Wolkenwerder. Schlimmer: Ein
        // Block in der Leere ist der erste Block einer Bruecke zum Nachbarn.
        Wolkenwache wache = wache();
        int inDerLeere = buch.plan().feldKante() + 5;

        assertTrue(wache.zustaendig(ort(WOLKENWERDER, inDerLeere, 64, inDerLeere)));
        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(anna.spieler(), BAUEN,
                        ort(WOLKENWERDER, inDerLeere, 64, mitte(0))));
    }

    @Test
    @DisplayName("Auch der Nachbar darf in der Leere nicht bauen")
    void auchDerNachbarNicht() {
        Parzelle insel = nimm(anna);
        Wolkenwache wache = wache();
        int gleichNebenan = buch.plan().feldAnfang(insel.x())
                + buch.plan().feldKante() + 1;

        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(anna.spieler(), BAUEN,
                        ort(WOLKENWERDER, gleichNebenan, 64, mitte(insel.z()))),
                "einen Block neben seiner eigenen Insel - und das ist Absicht");
    }

    // ============================================================ das Urteil

    @Test
    @DisplayName("Auf seiner Insel geht die Hand des Besitzers durch - durch die WACHE")
    void besitzerDarf() {
        Parzelle insel = nimm(anna);
        Wolkenwache wache = wache();

        assertEquals(Schutzwache.Ortsregel.Urteil.ERLAUBT,
                wache.pruefe(anna.spieler(), BAUEN, aufDerInsel(insel)));
        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(bert.spieler(), BAUEN, aufDerInsel(insel)),
                "und die des Nachbarn nicht");
    }

    @Test
    @DisplayName("Ein Mitbauer kommt durch dieselbe Wache")
    void mitbauerDarf() {
        Parzelle insel = nimm(anna);
        lager.mitbauerSetzen(insel.id(), Set.of(BERT));

        assertEquals(Schutzwache.Ortsregel.Urteil.ERLAUBT,
                wache().pruefe(bert.spieler(), BAUEN, aufDerInsel(insel)));
    }

    @Test
    @DisplayName("Auf dem Ankunftsfeld baut NIEMAND")
    void ankunftsfeldIstGesperrt() {
        // Es gehoert niemandem und wird nie vergeben. Ohne diese Zusage truege die eine
        // oeffentliche Plattform der Welt nach der ersten Nacht Loecher.
        int ankunft = buch.plan().ankunftsMitte();

        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache().pruefe(anna.spieler(), BAUEN,
                        ort(WOLKENWERDER, ankunft, 64, ankunft)));
    }

    @Test
    @DisplayName("Ohne Spieler ist die Antwort VERBOTEN - und sie wirft nicht")
    void ohneSpieler() {
        // Die Lage, die am 18.11.2026 in Hafenwerder eine NullPointerException war:
        // Set.of(...).contains(null) wirft, auch bei leerer Mitbauerliste. Die Wache
        // machte daraus eine Klage ueber eine stumme Datenbank und lehnte DANACH jedes
        // Bauen ab - mit einem Grund, der nicht stimmt.
        Parzelle insel = nimm(anna);

        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache().pruefe(null, BAUEN, aufDerInsel(insel)));
        assertTrue(klagen().isEmpty(), "und KEINE Klage: " + klagen());
    }

    // ============================================================ die Handbalken

    @Test
    @DisplayName("Jede Lage bekommt ihren eigenen Handbalken")
    void jedeLageIhrBalken() {
        Parzelle insel = nimm(anna);
        Wolkenwache wache = wache();
        int inDerLeere = buch.plan().feldKante() + 5;
        int ankunft = buch.plan().ankunftsMitte();
        var sprache = Sprachprobe.spracheAusDemJar();

        assertEquals(Schutztext.inselBalken(sprache, WacheSatz.SCHUTZ_WOLKE_LEERE),
                wache.grund(bert.spieler(), BAUEN,
                        ort(WOLKENWERDER, inDerLeere, 64, inDerLeere)));
        assertEquals(Schutztext.inselBalken(sprache, WacheSatz.SCHUTZ_WOLKE_ANKUNFT),
                wache.grund(bert.spieler(), BAUEN,
                        ort(WOLKENWERDER, ankunft, 64, ankunft)));
        assertEquals(Schutztext.inselBalken(sprache, WacheSatz.SCHUTZ_WOLKE_FREI),
                wache.grund(bert.spieler(), BAUEN,
                        ort(WOLKENWERDER, mitte(7), 64, mitte(7))),
                "Feld 7/7 ist nie vergeben worden");
        assertEquals(Schutztext.fremdeInselBalken(sprache, "Anna"),
                wache.grund(bert.spieler(), BAUEN, aufDerInsel(insel)),
                "DERSELBE Satz wie in Hafenwerder - er sagt dort genau dasselbe");
    }

    @Test
    @DisplayName("In einer fremden Welt gibt es den allgemeinen Satz")
    void fremdeWeltBekommtDenAllgemeinenSatz() {
        // Zwischen dem Urteil und dieser Frage kann der Spieler die Welt gewechselt
        // haben. Dann ist der allgemeine Satz die ehrlichere Auskunft - und kein Wurf.
        assertEquals(
                Schutztext.inselGestoertBalken(Sprachprobe.spracheAusDemJar()),
                wache().grund(bert.spieler(), BAUEN, ort(MARKTSTADT, 0, 64, 0)));
    }

    // ============================================================ der Ausfall

    @Test
    @DisplayName("Schweigt die Ablage, wird VERBOTEN - und einmal geklagt")
    void stummeAblage() {
        // Die sichere Richtung: Ein stilles Ja liesse jeden auf jeder fremden Insel
        // abbauen. Und die Klage ist GEDROSSELT - ohne Drossel schreibt ein
        // ausgefallener Datenbankrechner in Sekunden ein Protokoll voll.
        Wolkenbuch stumm = new Wolkenbuch(Wolkengrenze.wolkenwerder(),
                new WerfendesLager(), new SpeicherKasse(1L),
                new Wirtschaftsstand(Wirtschaftswerte.VORGABE));
        Wolkenwache wache = new Wolkenwache(stumm, WOLKENWERDER,
                Sprachprobe.spracheAusDemJar(), protokoll);

        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(anna.spieler(), BAUEN,
                        ort(WOLKENWERDER, mitte(1), 64, mitte(1))));
        assertEquals(Schutzwache.Ortsregel.Urteil.VERBOTEN,
                wache.pruefe(anna.spieler(), BAUEN,
                        ort(WOLKENWERDER, mitte(2), 64, mitte(2))));

        assertEquals(1, klagen().size(),
                "zweimal gefragt, EINMAL geklagt: " + klagen());
        assertTrue(klagen().get(0).contains("JEDES Bauen"), klagen().get(0));
    }

    @Test
    @DisplayName("Ohne Buch oder ohne Welt gibt es keine Wache")
    void riegel() {
        assertThrows(IllegalArgumentException.class,
                () -> new Wolkenwache(null, WOLKENWERDER,
                        Sprachprobe.spracheAusDemJar(), protokoll));
        assertThrows(IllegalArgumentException.class,
                () -> new Wolkenwache(buch, "", Sprachprobe.spracheAusDemJar(),
                        protokoll),
                "ohne Welt waere sie eine Regel, die in JEDER Welt nach Wolkenfeldern"
                        + " sucht");
    }

    // ============================================================ Werkzeug

    private Wolkenwache wache() {
        return new Wolkenwache(buch, WOLKENWERDER, Sprachprobe.spracheAusDemJar(),
                protokoll);
    }

    private int mitte(int feld) {
        return buch.plan().feldMitte(feld);
    }

    private Parzelle nimm(Spielerprobe wer) {
        return buch.nehmen(WOLKENWERDER, wer.kennung(), 9).insel().orElseThrow();
    }

    private Location aufDerInsel(Parzelle insel) {
        return ort(WOLKENWERDER, mitte(insel.x()), Wolkenboden.BODEN,
                mitte(insel.z()));
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
        Logger eigener = Logger.getLogger("WolkenwacheProbe-" + UUID.randomUUID());
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

    /** Ein Lager, das beim Nachsehen wirft - fuer den Ausfallzweig. */
    private static final class WerfendesLager extends Lagerattrappe {

        @Override
        public Optional<Parzelle> finde(String welt, int x, int z) {
            throw new IllegalStateException("Ablage stumm");
        }
    }
}
