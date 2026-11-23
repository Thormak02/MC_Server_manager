package de.freimarken.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @DisplayName("Eine FAELLIGE Stadt warnt - auch bevor der erste Takt lief")
    void faelligeStadtWarntVorDemErstenTakt() {
        // GEMESSEN am 23.11.2026, und vorher ein Wurf: Der erste Spielzeittakt kommt
        // eine ganze Taktlaenge nach dem Start (ab Werk 60 s, einstellbar bis 600 s).
        // In diesem Fenster ist imTakt nie gelaufen, die Stadt steht also noch auf
        // FAELLIG - und beimBeitritt filtert nur AKTIV.
        //
        // KEIN lauf.imTakt() hier, und das ist der ganze Satz: Die Nachbarprobe
        // verfalleneStadtMeldetSich ruft es ausdruecklich und prueft damit den Zustand
        // DANACH. Genau dieser Aufruf hat den Fehler zweiundzwanzig Tage verdeckt.
        Parzelle stadt = gegruendet("annastadt", ANNA);
        lager.besuchSetzen(stadt.id(), heute().minusDays(400));
        Spielerprobe anna = new Spielerprobe("Anna", ANNA);

        lauf.beimBeitritt(beitritt(anna));

        assertEquals(2, anna.chatBild().size(),
                "die dringende Stufe mit ihren zwei Zeilen - vorher war es ein Wurf und"
                        + " der Spieler las gar nichts: " + anna.chatBild());
        assertTrue(anna.chatBild().get(0).contains("annastadt"),
                anna.chatBild().get(0));
        assertTrue(anna.chatBild().get(1).contains("Grundstueck"),
                "und der Ausweg: " + anna.chatBild().get(1));

        // DER WORTLAUT, NICHT NUR DER NAME [23.11.2026]: Bis hierher las FAELLIG die
        // dringende Stufe mit resttage = 0 - und ein gezaehlter Satz nimmt bei 0 die
        // MEHRZAHLform. Der Besitzer las "Deine Freistadt annastadt verfaellt in 0
        // Tagen!", und die drei Zusicherungen oben waren dabei alle erfuellt. Keine der
        // zwei ausgelieferten Formen von warnung-spaet meint diesen Fall; die Datei sagt
        // selbst, die Einzahlform stehe fuer den LETZTEN Tag.
        assertFalse(anna.chatBild().get(0).contains("0 Tagen"),
                "FAELLIG hat keine Resttage - eine Zahl im Satz ist hier immer falsch: "
                        + anna.chatBild().get(0));
        // Und der eigene Satz kommt wirklich an. Ohne diese Haelfte waere die Zeile
        // darueber auch erfuellt, wenn gar nichts mehr gesendet wuerde.
        assertTrue(anna.chatBild().get(0).contains("JETZT"),
                "der eigene Satz fuer FAELLIG kommt nicht an: " + anna.chatBild().get(0));

        // DIE GEGENRICHTUNG, auf derselben Stadt: Die dringende Stufe behaelt ihre Zahl.
        // Ohne sie waere die Probe auch gruen, wenn jemand {tage} ganz aus warnung-spaet
        // herausnimmt - dann waere "0 Tagen" nirgends mehr zu finden.
        lager.besuchSetzen(stadt.id(), heute().minusDays(360));
        Spielerprobe spaeter = new Spielerprobe("Anna", ANNA);
        lauf.beimBeitritt(beitritt(spaeter));

        assertTrue(spaeter.chatBild().get(0).contains("5 Tagen"),
                "die dringende Stufe muss ihre Resttage weiter nennen: "
                        + spaeter.chatBild().get(0));
    }

    @Test
    @DisplayName("Eine faellige Stadt verschluckt die Warnung der NAECHSTEN nicht")
    void eineFaelligeStadtVerschlucktDieNaechsteNicht() {
        // DIE GROESSERE HAELFTE DES SCHADENS, und eine Ein-Stadt-Probe findet sie nie:
        // Der Wurf verliess die Schleife ueber ALLE Staedte des Spielers. Wer drei
        // besass und eine faellige, verlor alle drei Warnungen - und im Protokoll stand
        // "liess sich nicht bauen", also suchte der Betreiber in der Sprachdatei.
        //
        // Die Namen sind mit Absicht so gewaehlt, dass die FAELLIGE zuerst kommt:
        // buch.staedte gibt nach Namen, und "aastadt" steht vor "bestadt". Mit der
        // umgekehrten Reihenfolge waere diese Probe auch vor der Behebung gruen gewesen.
        Parzelle faellig = gegruendet("aastadt", ANNA);
        Parzelle gewarnt = gegruendet("bestadt", ANNA);
        lager.besuchSetzen(faellig.id(), heute().minusDays(400));
        lager.besuchSetzen(gewarnt.id(), heute().minusDays(350));
        Spielerprobe anna = new Spielerprobe("Anna", ANNA);

        lauf.beimBeitritt(beitritt(anna));

        assertTrue(anna.chatBild().stream().anyMatch(z -> z.contains("aastadt")),
                "die faellige Stadt warnt: " + anna.chatBild());
        assertTrue(anna.chatBild().stream().anyMatch(z -> z.contains("bestadt")),
                "UND die naechste auch - vorher nahm der Wurf der ersten sie mit: "
                        + anna.chatBild());
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
