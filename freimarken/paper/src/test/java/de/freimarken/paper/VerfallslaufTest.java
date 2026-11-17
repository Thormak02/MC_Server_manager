package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.freimarken.kern.Feldgrenze;
import de.freimarken.kern.Freistadtbuch;
import de.freimarken.kern.Parzelle;
import de.freimarken.kern.SpeicherGrundstuecke;
import de.freimarken.kern.SpeicherKasse;
import de.freimarken.kern.SpeicherLager;
import de.freimarken.kern.Stadtkasse;
import de.freimarken.kern.Verfallswerte;
import de.freimarken.kern.Warpverzeichnis;
import de.freimarken.kern.Wirtschaftsstand;
import de.freimarken.kern.Wirtschaftswerte;
import de.freimarken.kern.Zeitwerte;
import de.freimarken.kern.Zellquader;
import de.freimarken.kern.Zustand;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der taegliche Verfall und die zwei Warnungen - ohne Server [Plan 16, Teil E5].
 *
 * <p>Die Uhr steht auf einem festen Tag; was sich bewegt, ist der BESUCHSTAG der Stadt.
 * Das ist der kuerzere Weg zu derselben Aussage: Eine Stadt, die vor 400 Tagen besucht
 * wurde, ist heute faellig - und eine Probe muss dafuer nicht 400 Tage warten.
 */
@DisplayName("Der Verfall der Geisterstaedte")
class VerfallslaufTest {

    private static final String STADTWELT = "freimarken:freistaedte";
    private static final Instant JETZT = Instant.parse("2026-11-17T12:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final UUID ANNA = Spielerprobe.kennungAus(1);
    private static final UUID BERT = Spielerprobe.kennungAus(2);

    private StellUhr uhr;
    private Wirtschaftsstand wirtschaft;
    private SpeicherLager lager;
    private SpeicherGrundstuecke grundstuecke;
    private Freistadtbuch buch;
    private Verfallslauf lauf;

    @BeforeAll
    static void serverStellen() {
        Serverprobe.einmalSetzen();
    }

    @BeforeEach
    void vorher() {
        Serverprobe.niemandMehrDa();
        uhr = new StellUhr(JETZT);
        wirtschaft = new Wirtschaftsstand(Wirtschaftswerte.VORGABE.mitZeit(
                new Zeitwerte(ZONE, Zeitwerte.VORGABE.takt(),
                        Zeitwerte.VORGABE.untaetigNach())));
        lager = new SpeicherLager();
        grundstuecke = new SpeicherGrundstuecke();
        buch = new Freistadtbuch(Feldgrenze.freistaedte(), lager,
                new SpeicherKasse(10_000_000L), new Stadtkasse(new SpeicherKasse(0L)),
                Warpverzeichnis.ausWelten(List.of("altfurt", "freistaedte")),
                wirtschaft);
        buch.gibGrundstuecke(grundstuecke);
        lauf = new Verfallslauf(buch, STADTWELT, uhr, wirtschaft,
                Sprachprobe.spracheAusDemJar(), java.util.logging.Logger
                        .getAnonymousLogger());
    }

    @Test
    @DisplayName("Eine laengst unbesuchte Stadt ohne Buerger VERFAELLT")
    void faelligeStadtVerfaellt() {
        Parzelle stadt = gegruendet("annastadt", ANNA);
        lager.besuchSetzen(stadt.id(), LocalDate.of(2025, 1, 1));

        assertEquals(1, lauf.imTakt());

        assertEquals(Zustand.VERFALLEN,
                lager.finde(STADTWELT, stadt.x(), stadt.z()).orElseThrow().zustand());
    }

    @Test
    @DisplayName("Zweimal am selben Tag laeuft NICHT zweimal")
    void nurEinmalJeTag() {
        // Der billige Vortest. Der Takt schlaegt 1.440 Mal am Tag, und der Lauf geht
        // ueber ALLE Staedte einer Welt - ohne ihn waere das 1.440 Mal eine Liste.
        Parzelle stadt = gegruendet("annastadt", ANNA);
        lager.besuchSetzen(stadt.id(), LocalDate.of(2025, 1, 1));
        assertEquals(1, lauf.imTakt());

        // Zweite Stadt, derselbe Tag: Der Lauf sieht sie heute nicht mehr.
        Parzelle zweite = gegruendet("bertstadt", BERT);
        lager.besuchSetzen(zweite.id(), LocalDate.of(2025, 1, 1));

        assertEquals(0, lauf.imTakt(), "heute ist er durch");
        assertEquals(Zustand.AKTIV,
                lager.finde(STADTWELT, zweite.x(), zweite.z()).orElseThrow().zustand());

        // Und morgen holt er sie.
        uhr.weiter(java.time.Duration.ofDays(1));
        assertEquals(1, lauf.imTakt());
    }

    @Test
    @DisplayName("Eine Stadt MIT Stadtbuerger verfaellt nie - auch unbesucht")
    void stadtMitBuergerBleibt() {
        Parzelle stadt = gegruendet("annastadt", ANNA);
        lager.besuchSetzen(stadt.id(), LocalDate.of(2020, 1, 1));
        // BERT und nicht ANNA: Der Zuschnitt der Besitzerin macht keinen Buerger.
        grundstuecke.anlegen(STADTWELT, stadt.id(), Zellquader.eineZelle(
                buch.plan().ersteZelle(stadt.x()), buch.plan().ersteZelle(stadt.z())),
                BERT, 5_000L);

        assertEquals(0, lauf.imTakt());
        assertEquals(Zustand.AKTIV,
                lager.finde(STADTWELT, stadt.x(), stadt.z()).orElseThrow().zustand());
    }

    @Test
    @DisplayName("Mit abgeschaltetem Verfall laeuft nichts - und zwar SOFORT")
    void abgeschalteterVerfall() {
        // Aus dem HALTER und nicht aus einem Schnappschuss: Hier wird umgelegt, nachdem
        // der Lauf schon steht - genau wie bei einem /freimarken neuladen.
        Parzelle stadt = gegruendet("annastadt", ANNA);
        lager.besuchSetzen(stadt.id(), LocalDate.of(2020, 1, 1));
        wirtschaft.uebernimm(wirtschaft.werte().mitFreistaedten(
                wirtschaft.werte().freistaedte().mitVerfall(
                        Verfallswerte.VORGABE.mitAn(false))));

        assertEquals(0, lauf.imTakt());
        assertEquals(Zustand.AKTIV,
                lager.finde(STADTWELT, stadt.x(), stadt.z()).orElseThrow().zustand());
    }

    @Test
    @DisplayName("Eine schon verfallene Stadt wird nicht noch einmal geschrieben")
    void verfalleneStadtNichtErneut() {
        Parzelle stadt = gegruendet("annastadt", ANNA);
        lager.besuchSetzen(stadt.id(), LocalDate.of(2020, 1, 1));
        assertEquals(1, lauf.imTakt());

        uhr.weiter(java.time.Duration.ofDays(1));

        assertEquals(0, lauf.imTakt(),
                "VERFALLEN ist ein eigener Stand und nicht wieder FAELLIG - sonst"
                        + " leerte der Lauf taeglich den Zwischenspeicher fuer nichts");
    }

    @Test
    @DisplayName("Der Besitzer liest beim Beitritt die Warnung - und nur fuer SEINE Stadt")
    void warnungBeimBeitritt() {
        Parzelle meine = gegruendet("annastadt", ANNA);
        Parzelle fremde = gegruendet("bertstadt", BERT);
        // 310 Tage ohne Besuch: 55 bis zum Verfall, also in der ersten Warnstufe (60).
        lager.besuchSetzen(meine.id(), heute().minusDays(310));
        lager.besuchSetzen(fremde.id(), heute().minusDays(310));
        Spielerprobe anna = new Spielerprobe("Anna", ANNA);

        lauf.beimBeitritt(new org.bukkit.event.player.PlayerJoinEvent(anna.spieler(),
                net.kyori.adventure.text.Component.empty()));

        assertEquals(1, anna.chatBild().size(), anna.chatBild().toString());
        assertTrue(anna.chatBild().get(0).contains("annastadt"),
                anna.chatBild().get(0));
        assertTrue(anna.chatBild().get(0).contains("55"),
                "und die Resttage stehen drin: " + anna.chatBild().get(0));
        assertTrue(!anna.chatBild().get(0).contains("bertstadt"),
                "die Stadt eines anderen nicht");
    }

    @Test
    @DisplayName("Die zweite Stufe klingt ANDERS als die erste")
    void zweiteStufeKlingtAnders() {
        // Dass es zwei sind, ist der ganze Sinn von zwei Stufen: Wer zwei Wochen vorher
        // denselben Ton liest wie zwei Monate davor, liest ihn nicht mehr.
        Parzelle stadt = gegruendet("annastadt", ANNA);
        lager.besuchSetzen(stadt.id(), heute().minusDays(310));
        Spielerprobe frueh = new Spielerprobe("Anna", ANNA);
        lauf.beimBeitritt(beitritt(frueh));

        lager.besuchSetzen(stadt.id(), heute().minusDays(360));
        Spielerprobe spaet = new Spielerprobe("Anna", ANNA);
        lauf.beimBeitritt(beitritt(spaet));

        assertEquals(1, frueh.chatBild().size());
        assertEquals(2, spaet.chatBild().size(),
                "die dringende Stufe traegt eine zweite, graue Zeile mit dem Ausweg: "
                        + spaet.chatBild());
        assertTrue(!frueh.chatBild().get(0).equals(spaet.chatBild().get(0)),
                "und die erste Zeile ist eine andere");
    }

    @Test
    @DisplayName("Eine VERFALLENE Stadt meldet sich beim Beitritt - mit dem Ausweg")
    void verfalleneStadtMeldetSich() {
        // Ohne diese Zeile hielte ein Besitzer seine 400.000 Mark fuer verbrannt und
        // suchte den Betreiber.
        Parzelle stadt = gegruendet("annastadt", ANNA);
        lager.besuchSetzen(stadt.id(), LocalDate.of(2020, 1, 1));
        lauf.imTakt();
        Spielerprobe anna = new Spielerprobe("Anna", ANNA);

        lauf.beimBeitritt(beitritt(anna));

        assertEquals(2, anna.chatBild().size(), anna.chatBild().toString());
        assertTrue(anna.chatBild().get(0).contains("verfallen"),
                anna.chatBild().get(0));
        assertTrue(anna.chatBild().get(1).contains("Guthaben"),
                "und dass nichts verloren ist: " + anna.chatBild().get(1));
    }

    @Test
    @DisplayName("Eine gesunde Stadt bekommt KEINE Zeile")
    void gesundeStadtSchweigt() {
        gegruendet("annastadt", ANNA);
        Spielerprobe anna = new Spielerprobe("Anna", ANNA);

        lauf.beimBeitritt(beitritt(anna));

        assertEquals(List.of(), anna.chatBild());
    }

    @Test
    @DisplayName("Ohne Buch und ohne Weltschluessel entsteht der Lauf nicht")
    void unbrauchbarerLaufWirft() {
        // Ein leerer Weltschluessel faende keine Staedte, und dann verfiele lautlos
        // nie eine - gebaut, gruen geprueft und im Spiel wirkungslos.
        assertThrows(IllegalArgumentException.class, () -> new Verfallslauf(null,
                STADTWELT, uhr, wirtschaft, Sprachprobe.spracheAusDemJar(),
                java.util.logging.Logger.getAnonymousLogger()));
        assertThrows(IllegalArgumentException.class, () -> new Verfallslauf(buch, "  ",
                uhr, wirtschaft, Sprachprobe.spracheAusDemJar(),
                java.util.logging.Logger.getAnonymousLogger()));
    }

    // --- Werkzeug -------------------------------------------------------------

    private org.bukkit.event.player.PlayerJoinEvent beitritt(Spielerprobe wer) {
        return new org.bukkit.event.player.PlayerJoinEvent(wer.spieler(),
                net.kyori.adventure.text.Component.empty());
    }

    private LocalDate heute() {
        return de.freimarken.kern.Tagesgrenze.tagVon(uhr.instant(), ZONE);
    }

    private Parzelle gegruendet(String name, UUID wer) {
        return buch.gruende(STADTWELT, wer, name, 99).stadt().orElseThrow();
    }

    /** Eine Uhr, die sich stellen laesst - dieselbe Bauart wie in {@code MietlaufTest}. */
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
