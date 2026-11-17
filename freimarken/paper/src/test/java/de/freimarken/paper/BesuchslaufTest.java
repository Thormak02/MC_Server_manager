package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Feldgrenze;
import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Lagerattrappe;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.SpeicherGrundstuecke;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.Stadtkasse;
import de.freimarken.kern.Warpverzeichnis;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
 * Der Besuchsstempel der Freistaedte - ohne Server [Plan 16, Teil E4].
 *
 * <p>Die Kernzusage dieser Datei ist eine Zahl, und zwar die GESCHRIEBENE: hoechstens
 * EIN Stempel je Stadt und Tag. Der Minutentakt schlaegt 1.440 Mal am Tag, und eine
 * bewohnte Stadt bekaeme sonst 1.440 Schreibvorgaenge - jeder davon leert den ganzen
 * Zwischenspeicher der Parzellen.
 *
 * <p><b>Die Zone ist NICHT UTC, und das ist der Grund, warum diese Proben etwas
 * messen.</b> Mit Uhr=UTC und Zone=UTC waere jede Tagesrechnung gruen, auch wenn die
 * Tagesgrenze ganz fehlte. Hier steht die Uhr auf 22:30 UTC und die Zone auf
 * {@code Pacific/Auckland} - dort ist schon der naechste Tag. Dasselbe Vorbild wie
 * {@code TruhenbuchTest}.
 */
@DisplayName("Der Besuchsstempel der Freistaedte")
class BesuchslaufTest {

    private static final String STADTWELT = "freimarken:freistaedte";
    private static final String ALTFURT = "freimarken:altfurt";

    /** 22:30 UTC - unter der Zone unten ist es der 18. um 11:30 Uhr. */
    private static final Instant START = Instant.parse("2026-11-17T22:30:00Z");

    /** Auckland, damit der Tag NICHT der UTC-Tag ist. */
    private static final ZoneId ZONE = ZoneId.of("Pacific/Auckland");

    private static final UUID ANNA = Spielerprobe.kennungAus(1);
    private static final UUID BERT = Spielerprobe.kennungAus(2);

    private final List<LogRecord> protokollzeilen = new ArrayList<>();
    private StellUhr uhr;
    private Wirtschaftsstand wirtschaft;
    private ZaehlLager lager;
    private Freistadtbuch buch;
    private Besuchslauf lauf;
    private Logger protokoll;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.niemandMehrDa();
        Serverprobe.keineWeltenMehr();
        protokollzeilen.clear();
        protokoll = eigenesProtokoll();
        uhr = new StellUhr(START);
        wirtschaft = new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitZeit(
                new Zeitwerte(ZONE, Zeitwerte.VORGABE.takt(),
                        Zeitwerte.VORGABE.untaetigNach())));
        lager = new ZaehlLager();
        buch = new Freistadtbuch(Feldgrenze.freistaedte(), lager,
                new SpeicherKasse(10_000_000L), new Stadtkasse(new SpeicherKasse(0L)),
                Warpverzeichnis.ausWelten(List.of("altfurt", "freistaedte")),
                wirtschaft);
        buch.gibGrundstuecke(new SpeicherGrundstuecke());
        lauf = new Besuchslauf(buch, uhr, wirtschaft,
                Sprachprobe.spracheAusDemJar(), protokoll);
    }

    @Test
    @DisplayName("Wer in einer Stadt steht, setzt ihren Stempel auf HEUTE")
    void stempelWirdGesetzt() {
        // Der Tag ist der 18., nicht der 17.: Die Uhr steht auf 22:30 UTC, und in
        // Auckland ist es dann schon der naechste Tag. Ohne die Tagesgrenze stuende
        // hier der 17., und die Probe waere rot - genau dafuer ist sie da.
        Parzelle stadt = gegruendet("annastadt");

        lauf.imTakt(spielerIn(stadt, ANNA, "Anna"));

        assertEquals(1, lager.gesetzt.size(), lager.gesetzt.toString());
        assertEquals(LocalDate.of(2026, 11, 18), lager.gesetzt.get(0),
                "der Tag der eingestellten ZONE und nicht der von UTC");
        assertEquals(LocalDate.of(2026, 11, 18),
                lager.finde(STADTWELT, stadt.x(), stadt.z()).orElseThrow().besucht());
    }

    @Test
    @DisplayName("Derselbe Takt noch neunmal schreibt NICHT noch neunmal")
    void nurEinmalJeTag() {
        // DIE KERNZUSAGE. Der Takt schlaegt jede Minute; ohne den Merker im
        // Arbeitsspeicher bekaeme eine bewohnte Stadt 1.440 Schreibvorgaenge am Tag,
        // und jeder davon leert den ganzen Zwischenspeicher der Parzellen.
        Parzelle stadt = gegruendet("annastadt");
        org.bukkit.entity.Player anna = spielerIn(stadt, ANNA, "Anna");

        for (int schlag = 0; schlag < 10; schlag++) {
            lauf.imTakt(anna);
            uhr.weiter(java.time.Duration.ofMinutes(1));
        }

        assertEquals(1, lager.gesetzt.size(),
                "zehn Takte, EIN Stempel: " + lager.gesetzt);
    }

    @Test
    @DisplayName("ZWEI Spieler in DERSELBEN Stadt sind EIN Stempel")
    void zweiSpielerEineStadt() {
        // Der Merker haengt an der STADT und nicht am Spieler. Haenge er am Spieler,
        // bekaeme eine Stadt mit zwanzig Besuchern zwanzig Schreibvorgaenge.
        Parzelle stadt = gegruendet("annastadt");

        lauf.imTakt(spielerIn(stadt, ANNA, "Anna"));
        lauf.imTakt(spielerIn(stadt, BERT, "Bert"));

        assertEquals(1, lager.gesetzt.size(), lager.gesetzt.toString());
    }

    @Test
    @DisplayName("ZWEI Spieler in ZWEI Staedten sind ZWEI Stempel")
    void zweiSpielerZweiStaedte() {
        // Die andere Richtung, und ohne sie waere die Probe darueber auch mit einem
        // EINZIGEN Merker fuer alle Staedte gruen - dann bekaeme am Tag genau eine
        // Stadt ihren Stempel und alle anderen keinen.
        Parzelle eine = gegruendet("annastadt");
        Parzelle andere = gegruendet("bertstadt");

        lauf.imTakt(spielerIn(eine, ANNA, "Anna"));
        lauf.imTakt(spielerIn(andere, BERT, "Bert"));

        assertEquals(2, lager.gesetzt.size(), lager.gesetzt.toString());
    }

    @Test
    @DisplayName("Nach Mitternacht - der ZONE - wird wieder gestempelt")
    void nachMitternachtWiederEinmal() {
        // Die Menge der heute Gestempelten gehoert zu einem TAG, und der erste Schlag
        // des neuen Tages leert sie. Ohne das brauchte es jemanden, der sie um
        // Mitternacht leert - und das waere ein zweiter Zeitgeber.
        Parzelle stadt = gegruendet("annastadt");
        org.bukkit.entity.Player anna = spielerIn(stadt, ANNA, "Anna");
        lauf.imTakt(anna);
        assertEquals(1, lager.gesetzt.size());

        // Ueber Mitternacht in Auckland: Von 11:30 Uhr Ortszeit sind es gut zwoelf
        // Stunden bis zum naechsten Tag.
        uhr.weiter(java.time.Duration.ofHours(13));
        lauf.imTakt(anna);

        assertEquals(2, lager.gesetzt.size(), lager.gesetzt.toString());
        assertEquals(LocalDate.of(2026, 11, 19), lager.gesetzt.get(1));
    }

    @Test
    @DisplayName("Wer auf einem freien Feld steht, stempelt nichts")
    void freiesFeldStempeltNicht() {
        gegruendet("annastadt");
        int frei = buch.plan().feldMitte(40);

        lauf.imTakt(new Spielerprobe("Anna", ANNA)
                .anOrt(new Location(Serverprobe.welt(STADTWELT), frei, 64, frei))
                .spieler());

        assertEquals(0, lager.gesetzt.size(), lager.gesetzt.toString());
    }

    @Test
    @DisplayName("Eine RASTERPARZELLE in Altfurt ist keine Stadt - kein Stempel")
    void rasterparzelleStempeltNicht() {
        // DIE FALLE AUS E3b, von der anderen Seite: Das Lager filtert nicht nach Art,
        // und die Feldnummern der zwei Geometrien ueberschneiden sich - Block 150/150
        // in Altfurt liegt in Stadtfeld 0/0. Ohne den Filter in stadtAn bekaeme dieser
        // Lauf dort eine gewoehnliche Parzelle und riefe stempleBesuch darauf, das
        // dafuer wirft; die Klage stuende dann bei JEDEM Takt im Protokoll.
        lager.anlegen(ALTFURT, 0, 0, ANNA, 5_000L);

        lauf.imTakt(new Spielerprobe("Anna", ANNA)
                .anOrt(new Location(Serverprobe.welt(ALTFURT), 150, 64, 150))
                .spieler());

        assertEquals(0, lager.gesetzt.size(), lager.gesetzt.toString());
        assertTrue(klagen().isEmpty(), "und keine Klage: " + klagen());
    }

    @Test
    @DisplayName("Eine Stoerung wird GEKLAGT und wirft nicht - der Takt laeuft weiter")
    void stoerungKlagtNur() {
        // Der Aufrufer ist der Minutentakt, und seine Ausnahme kostet die Spielzeit
        // aller Spieler bis zum naechsten Neustart. Ein verlorener Stempel kostet
        // nichts - der naechste Takt holt ihn nach.
        Parzelle stadt = gegruendet("annastadt");
        lager.stolpert = true;

        lauf.imTakt(spielerIn(stadt, ANNA, "Anna"));

        assertEquals(1, klagen().size(), klagen().toString());
        assertTrue(klagen().get(0).contains("Besuchsstempel"), klagen().get(0));
    }

    @Test
    @DisplayName("Nach einer Stoerung versucht der naechste Takt es ERNEUT")
    void nachStoerungErneut() {
        // Der Tag wird NACH dem Schreiben gemerkt. Davor gemerkt, waere ein einzelner
        // Datenbankausfall ein ganzer Tag ohne Stempel - obwohl jemand dort stand.
        Parzelle stadt = gegruendet("annastadt");
        org.bukkit.entity.Player anna = spielerIn(stadt, ANNA, "Anna");
        lager.stolpert = true;
        lauf.imTakt(anna);
        assertEquals(0, lager.gesetzt.size());

        lager.stolpert = false;
        lauf.imTakt(anna);

        assertEquals(1, lager.gesetzt.size(), lager.gesetzt.toString());
    }

    @Test
    @DisplayName("Der BESITZER weckt seine verfallene Stadt - und liest es")
    void besitzerWecktSeineStadt() {
        // Die zweite Folge eines Besuchs [Teil E5]. Ohne die Zeile im Chat waere sie
        // unsichtbar: Er steht dort, darf ploetzlich bauen und weiss nicht, warum.
        Parzelle stadt = gegruendet("annastadt");
        buch.lasseVerfallen(stadt);
        Spielerprobe anna = new Spielerprobe("Anna", ANNA).anOrt(new Location(
                Serverprobe.welt(STADTWELT), buch.plan().feldMitte(stadt.x()), 64,
                buch.plan().feldMitte(stadt.z())));

        lauf.imTakt(anna.spieler());

        assertEquals(de.freimarken.kern.Zustand.AKTIV, lager
                .finde(STADTWELT, stadt.x(), stadt.z()).orElseThrow().zustand());
        assertEquals(1, anna.chatBild().size(), anna.chatBild().toString());
        assertTrue(anna.chatBild().get(0).contains("annastadt"),
                anna.chatBild().get(0));
    }

    @Test
    @DisplayName("Ein FREMDER weckt sie nicht - er stempelt nur")
    void fremderWecktSieNicht() {
        // Sonst hielte ein Durchgangsverkehr jede Geisterstadt am Leben. Der STEMPEL
        // faellt trotzdem - das ist Teil E4 und eine andere Frage.
        Parzelle stadt = gegruendet("annastadt");
        buch.lasseVerfallen(stadt);
        Spielerprobe bert = new Spielerprobe("Bert", BERT).anOrt(new Location(
                Serverprobe.welt(STADTWELT), buch.plan().feldMitte(stadt.x()), 64,
                buch.plan().feldMitte(stadt.z())));

        lauf.imTakt(bert.spieler());

        assertEquals(de.freimarken.kern.Zustand.VERFALLEN, lager
                .finde(STADTWELT, stadt.x(), stadt.z()).orElseThrow().zustand());
        assertEquals(1, lager.gesetzt.size(), "gestempelt hat er aber");
        assertEquals(java.util.List.of(), bert.chatBild());
    }

    // --- Werkzeug -------------------------------------------------------------

    private Parzelle gegruendet(String name) {
        return buch.gruende(STADTWELT, name.startsWith("anna") ? ANNA : BERT, name, 99)
                .stadt().orElseThrow();
    }

    /** Ein Spieler in der Mitte dieser Stadt. */
    private org.bukkit.entity.Player spielerIn(Parzelle stadt, UUID kennung,
            String name) {
        return new Spielerprobe(name, kennung).anOrt(new Location(
                Serverprobe.welt(STADTWELT), buch.plan().feldMitte(stadt.x()), 64,
                buch.plan().feldMitte(stadt.z()))).spieler();
    }

    private List<String> klagen() {
        return protokollzeilen.stream()
                .filter(satz -> satz.getLevel().intValue() >= Level.WARNING.intValue())
                .map(LogRecord::getMessage)
                .toList();
    }

    private Logger eigenesProtokoll() {
        Logger eigener = Logger.getLogger("BesuchslaufProbe-" + UUID.randomUUID());
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
     * Ein Lager, das jeden gesetzten Tag mitschreibt und auf Befehl stolpert.
     *
     * <p>Erst auf Befehl, weil die Stadt vorher GEGRUENDET werden muss: Ein Lager, das
     * von Anfang an wirft, laesst keine entstehen - und die Probe maesse dann "keine
     * Stadt da" statt "Stadt nicht schreibbar".
     */
    private static final class ZaehlLager extends Lagerattrappe {

        private final List<LocalDate> gesetzt = new ArrayList<>();
        private boolean stolpert;

        @Override
        public void besuchSetzen(long id, LocalDate tag) {
            if (stolpert) {
                throw new IllegalStateException("Die Datenbank antwortet nicht");
            }
            gesetzt.add(tag);
            super.besuchSetzen(id, tag);
        }
    }

    /**
     * Eine Uhr, die sich stellen laesst - dieselbe Bauart wie in {@code MietlaufTest}.
     *
     * <p>{@code withZone} wirft absichtlich: Die Zone kommt aus den Zeitwerten, und
     * eine Uhr mit angeklebter Zone waere die zweite Quelle fuer dieselbe Frage.
     */
    private static final class StellUhr extends Clock {

        private Instant jetzt;

        StellUhr(Instant anfang) {
            this.jetzt = anfang;
        }

        void weiter(java.time.Duration wie) {
            jetzt = jetzt.plus(wie);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException(
                    "Die Zone kommt aus den Zeitwerten");
        }

        @Override
        public Instant instant() {
            return jetzt;
        }
    }
}
